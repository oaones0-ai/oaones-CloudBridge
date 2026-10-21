package com.customization.oaones.appsyn.common;

import com.customization.oaones.appsyn.dao.KqMapper;
import com.customization.oaones.appsyn.entity.KqFixedSchedule;
import com.customization.oaones.appsyn.entity.KqGroup;
import com.customization.oaones.appsyn.entity.KqHoliday;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「工作日 / 非工作日」判定（会议 / 日程同步的「计划任务」用）。
 *
 * <p>口径**直接取泛微标准考勤设置**（只读，不改标准表）：</p>
 * <ol>
 *   <li><b>节假日优先</b>：某天在 {@code kq_HolidaySet} 里有记录时按它算——
 *       {@code changeType = 2}（调配工作日，例如国庆前的周六补班）算<b>工作日</b>；
 *       {@code 1}（公众假日）/ {@code 3}（调配休息日）算<b>非工作日</b>。</li>
 *   <li>没有节假日记录时按该考勤组的**每周固定工作日**算：
 *       优先用 {@code kq_group.weekday}（老版本 E9 的“工作日”勾选），
 *       没有就用 {@code kq_fixedschedulce}（固定排班：星期几排了班次就算上班，
 *       {@code serialid = -1} / 空 = 休息），
 *       两者都没有（新安装、还没配考勤）时**兜底按周一至周五**。</li>
 * </ol>
 *
 * <p>{@code groupId &lt;= 0}（页面选“不指定考勤组”）时只看兜底口径（周一至周五），
 * 因为没有考勤组就没有节假日日历可查。</p>
 *
 * <p>性能：调度器每 {@code mtg.sync.tick.seconds}（默认 60 秒）算一次“下一次执行时间”，
 * 一次最多向后看 60 天，所以这里把考勤组配置与节假日按“组 + 年份”缓存：
 * 组配置 {@link #CFG_TTL_MS} 刷新一次，节假日按年惰性加载并常驻，
 * 正常运行时一整天总共就几条 SQL。</p>
 */
public final class OaonesWorkday {

    /** 考勤组配置（工作日勾选 / 固定排班 / 考勤组名称）的缓存时长：5 分钟。 */
    private static final long CFG_TTL_MS = 5 * 60 * 1000L;

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 组ID -> 配置（含按年缓存的节假日）。 */
    private static final Map<Integer, Cfg> CFG = new ConcurrentHashMap<Integer, Cfg>();
    /** 上次刷新组配置的时间（超过 TTL 就整体重载一次）。 */
    private static volatile long cfgLoadAt;
    /** 考勤组下拉（页面用）。 */
    private static volatile List<KqGroup> groups;
    private static volatile long groupsLoadAt;

    private OaonesWorkday() {
    }

    /** 清缓存（页面保存计划任务后调用，让“换了考勤组 / 改了节假日”立即生效）。 */
    public static void clearCache() {
        CFG.clear();
        cfgLoadAt = 0L;
        groups = null;
        groupsLoadAt = 0L;
    }

    /** 考勤组下拉（未删除的组；读不到标准表时返回空列表，页面按“不指定考勤组”处理）。 */
    public static List<KqGroup> groups() {
        long now = System.currentTimeMillis();
        List<KqGroup> local = groups;
        if (local != null && now - groupsLoadAt < CFG_TTL_MS) {
            return local;
        }
        try {
            local = OaonesMyBatis.call(KqMapper.class, KqMapper::listGroups);
        } catch (Throwable t) {
            OaonesLog.error("[mtgSync] 读取考勤组（kq_group）失败，计划任务的“工作日口径”只能按周一至周五算", t);
            local = null;
        }
        if (local == null) {
            local = new ArrayList<KqGroup>();
        }
        groups = local;
        groupsLoadAt = now;
        return local;
    }

    /**
     * 某一天算不算工作日。
     *
     * @param day     待判定的日期（只看日期部分）
     * @param groupId 考勤组ID（{@code <= 0} = 不指定，按周一至周五）
     */
    public static boolean isWorkday(Date day, int groupId) {
        Date at = day == null ? new Date() : day;
        int week = weekIndex(at);
        if (groupId <= 0) {
            return week <= 4;   // 不指定考勤组：没有节假日日历可查，按周一至周五
        }
        Cfg cfg = cfg(groupId);
        if (cfg == null) {
            return week <= 4;   // 考勤组查不到（被删了 / 标准表读不到）：同样按周一至周五
        }
        Integer changeType = cfg.holiday(text(at));
        if (changeType != null) {
            return changeType.intValue() == 2;   // 2 = 调配工作日；1 公众假日 / 3 调配休息日 = 非工作日
        }
        return cfg.workdays.contains(Integer.valueOf(week));
    }

    /**
     * 工作日口径的说明文案（页面 / 日志用）：
     * 形如“工作日：按考勤组「光华集团考勤组」的固定排班 + 节假日 / 调休（未指定考勤组时按周一至周五）”。
     */
    public static String ruleText(int groupId) {
        String base = "工作日：按泛微标准考勤（考勤组的固定排班 / 工作日勾选 + 节假日与调配工作日）；";
        if (groupId <= 0) {
            return base + "当前未指定考勤组，按周一至周五算。";
        }
        Cfg cfg = cfg(groupId);
        String name = cfg == null ? ("#" + groupId) : cfg.name;
        if (cfg == null) {
            return base + "指定的考勤组（" + name + "）读不到，按周一至周五算。";
        }
        return base + "当前用考勤组「" + name + "」：" + weeksText(cfg.workdays)
                + (cfg.defaulted ? "（该组没配排班，按周一至周五兜底）" : "") + "。";
    }

    /** 适用日文案：0 每天 / 1 工作日 / 2 非工作日 / 其它 = 每天。 */
    public static String dayTypeText(int dayType) {
        if (dayType == OaonesConst.MTG_PLAN_DAY_WORK) {
            return "工作日";
        }
        if (dayType == OaonesConst.MTG_PLAN_DAY_OFF) {
            return "非工作日";
        }
        return "每天";
    }

    /** 简短的周几文案（页面上的“工作时间段”提示用），如“周一、周二、周三、周四、周五”。 */
    public static String weeksText(Set<Integer> weeks) {
        String[] names = new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (weeks != null && weeks.contains(Integer.valueOf(i))) {
                if (sb.length() > 0) {
                    sb.append("、");
                }
                sb.append(names[i]);
            }
        }
        return sb.length() == 0 ? "（没有任何工作日）" : sb.toString();
    }

    /* ------------------------------------------------------------------ */

    /** 星期几序号：0 = 周一 … 6 = 周日（与 {@code kq_fixedschedulce.weekday} 同一口径）。 */
    private static int weekIndex(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        // Calendar：SUNDAY = 1 … SATURDAY = 7；换算成 周一 = 0 … 周日 = 6
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    private static String text(Date at) {
        return new SimpleDateFormat(DATE_PATTERN).format(at);
    }

    /** 取（并在需要时加载）某个考勤组的配置。 */
    private static Cfg cfg(int groupId) {
        long now = System.currentTimeMillis();
        if (now - cfgLoadAt >= CFG_TTL_MS) {
            synchronized (OaonesWorkday.class) {
                if (now - cfgLoadAt >= CFG_TTL_MS) {
                    CFG.clear();
                    cfgLoadAt = now;
                }
            }
        }
        Integer key = Integer.valueOf(groupId);
        Cfg cached = CFG.get(key);
        if (cached != null) {
            return cached;
        }
        Cfg cfg = load(groupId);
        if (cfg != null) {
            CFG.put(key, cfg);
        }
        return cfg;
    }

    /** 组装一个考勤组的工作日口径（每周固定工作日 + 按年缓存的节假日）。 */
    private static Cfg load(int groupId) {
        KqGroup group = null;
        List<KqGroup> all = groups();
        for (KqGroup one : all) {
            if (one.getId() != null && one.getId().intValue() == groupId) {
                group = one;
                break;
            }
        }
        if (group == null) {
            return null;
        }
        Cfg cfg = new Cfg();
        cfg.groupId = groupId;
        cfg.name = OaonesUtil.s(group.getName());
        Set<Integer> weeks = parseWeeks(group.getWeekday());
        if (!weeks.isEmpty()) {
            cfg.workdays = weeks;
            return cfg;
        }
        List<KqFixedSchedule> fixed;
        try {
            final int gid = groupId;
            fixed = OaonesMyBatis.call(KqMapper.class, m -> m.listFixedSchedule(gid));
        } catch (Throwable t) {
            OaonesLog.error("[mtgSync] 读取考勤固定排班（kq_fixedschedulce）失败，groupId=" + groupId, t);
            fixed = null;
        }
        if (fixed == null || fixed.isEmpty()) {
            cfg.workdays = defaultWeeks();
            cfg.defaulted = true;
            return cfg;
        }
        Set<Integer> set = new LinkedHashSet<Integer>();
        for (KqFixedSchedule row : fixed) {
            if (row.getWeekday() == null) {
                continue;
            }
            Integer serialid = row.getSerialid();
            if (serialid != null && serialid.intValue() != -1) {
                set.add(row.getWeekday());
            }
        }
        cfg.workdays = set;
        return cfg;
    }

    /** 解析 {@code kq_group.weekday}（老版本的“工作日”勾选）：容忍 `,` / `;` / `|` 分隔与 1~7 口径。 */
    private static Set<Integer> parseWeeks(String text) {
        Set<Integer> raw = new LinkedHashSet<Integer>();
        String s = OaonesUtil.s(text);
        if (s.length() == 0) {
            return raw;
        }
        boolean has7 = false;
        boolean has0 = false;
        for (String part : s.split("[^0-9]+")) {
            if (part.length() == 0) {
                continue;
            }
            int v;
            try {
                v = Integer.parseInt(part);
            } catch (Throwable t) {
                continue;
            }
            raw.add(Integer.valueOf(v));
            has7 = has7 || v == 7;
            has0 = has0 || v == 0;
        }
        Set<Integer> weeks = new LinkedHashSet<Integer>();
        for (Integer one : raw) {
            int v = one.intValue();
            if (has7 && !has0) {
                // 1 = 周一 … 7 = 周日 口径
                v = v - 1;
            }
            if (v >= 0 && v <= 6) {
                weeks.add(Integer.valueOf(v));
            }
        }
        return weeks;
    }

    /** 兜底工作日：周一至周五。 */
    private static Set<Integer> defaultWeeks() {
        Set<Integer> set = new LinkedHashSet<Integer>();
        for (int i = 0; i <= 4; i++) {
            set.add(Integer.valueOf(i));
        }
        return set;
    }

    /** 一个考勤组的工作日口径 + 按年缓存的节假日。 */
    private static final class Cfg {
        /** 对应考勤组ID（按年加载节假日时要用）。 */
        private int groupId;
        /** 考勤组名称（文案用）。 */
        private String name = "";
        /** 每周的固定工作日（0 = 周一 … 6 = 周日）。 */
        private Set<Integer> workdays = new HashSet<Integer>();
        /** 该组没配排班、按周一至周五兜底（文案里标一下）。 */
        private boolean defaulted;
        /** 年份 -> （yyyy-MM-dd -> changeType）；按需惰性加载后常驻。 */
        private final Map<String, Map<String, Integer>> holidays =
                new ConcurrentHashMap<String, Map<String, Integer>>();

        /** 取某一天的节假日类型（没有记录返回 null）。 */
        private Integer holiday(String date) {
            String year = date.length() >= 4 ? date.substring(0, 4) : "";
            if (year.length() == 0) {
                return null;
            }
            Map<String, Integer> yearMap = holidays.get(year);
            if (yearMap == null) {
                yearMap = loadYear(groupId, year);
                holidays.put(year, yearMap);
            }
            return yearMap.get(date);
        }
    }

    /** 加载某个考勤组某一年的节假日（读不到就返回空表并按空表用）。 */
    private static Map<String, Integer> loadYear(int groupId, String year) {
        Map<String, Integer> map = new ConcurrentHashMap<String, Integer>();
        if (year.length() == 0) {
            return map;
        }
        for (KqHoliday row : listHolidays(groupId, year + "-01-01", year + "-12-31")) {
            if (row.getHolidayDate() != null && row.getChangeType() != null) {
                map.put(OaonesUtil.s(row.getHolidayDate()).trim(), row.getChangeType());
            }
        }
        return map;
    }

    /** 读某个考勤组一个日期区间内的节假日（按年整年预取，调用方是 {@link Cfg#holiday(String)}）。 */
    private static List<KqHoliday> listHolidays(int groupId, String from, String to) {
        try {
            return OaonesMyBatis.call(KqMapper.class, m -> m.listHolidays(groupId, from, to));
        } catch (Throwable t) {
            OaonesLog.error("[mtgSync] 读取节假日设置（kq_HolidaySet）失败，groupId=" + groupId, t);
            return Collections.emptyList();
        }
    }
}
