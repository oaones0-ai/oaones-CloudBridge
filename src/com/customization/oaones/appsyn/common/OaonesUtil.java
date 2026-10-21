package com.customization.oaones.appsyn.common;

import com.alibaba.fastjson.JSONObject;
import weaver.general.Util;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * 模块内通用工具（与数据库无关的纯工具方法）。
 */
public final class OaonesUtil {

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    private static final ThreadLocal<SimpleDateFormat> DATE_ONLY_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
    private static final ThreadLocal<SimpleDateFormat> TIME_ONLY_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss"));

    /**
     * {@link #parseDateTime(String)} 的候选格式，**按“信息量从多到少”排列**（先精确到秒，
     * 再退到只到分 / 只到小时 / 只到日期），命中即返回：
     * 这样 {@code 2026-10-10 09:00}（E9 的 begintime 常见写法，没有秒）能按 09:00:00 解析，
     * 而不是整条解析失败变成 0。
     */
    private static final String[] DATE_TIME_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH",
            "yyyy-MM-dd",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm",
            "yyyyMMdd"
    };

    private OaonesUtil() {
    }

    public static String now() {
        return DATE_FORMAT.get().format(new Date());
    }

    /** E9 建模表标准字段 modedatacreatedate：yyyy-MM-dd。 */
    public static String nowDate() {
        return DATE_ONLY_FORMAT.get().format(new Date());
    }

    /** E9 建模表标准字段 modedatacreatetime：HH:mm:ss。 */
    public static String nowTime() {
        return TIME_ONLY_FORMAT.get().format(new Date());
    }

    /** 页面值转非空字符串。 */
    public static String s(String value) {
        return Util.null2String(value);
    }

    public static int i(String value, int def) {
        return Util.getIntValue(value, def);
    }

    /** 字符串转 Integer；空值返回 null，便于 Mapper 用 {@code is null} 判断。 */
    public static Integer integer(String value) {
        String v = s(value);
        if (v.length() == 0) {
            return null;
        }
        return Util.getIntValue(v, 0);
    }

    /**
     * 把 OA / 平台给的“日期时间”文本解析成毫秒时间戳。
     *
     * <p>会议 / 日程同步推送平台时要的是时间戳，OA 侧来源（Meeting.beginDateTime /
     * WorkPlan.begindate + begintime）都是字符串，统一在这里换算；解析不了返回 0，
     * 由调用方按“时间缺失”处理。</p>
     *
     * <p><b>写法要宽容</b>：E9 里同是“开始时间”，不同版本 / 不同来源的写法并不统一，
     * 常见的有 {@code 2026-10-10 09:00}（<b>没有秒</b>，Meeting/WorkPlan 的 begintime 就是这种）、
     * {@code 2026-10-10}（只有日期）、{@code 2026/10/10 09:00:00}、
     * {@code 2026-10-10T09:00:00}、{@code 2026-10-10 09:00:00.0}（带毫秒 / JDBC 读 datetime 的写法）、
     * {@code 2026年10月10日 9时0分0秒}、{@code 20261010090000}。
     * 以前只认 {@code yyyy-MM-dd HH:mm:ss}，遇到“没有秒”的写法会直接返回 0，
     * 于是报文中开始 / 结束时间变成 0（见 {@link #normalizeDateTimeText} / {@link #DATE_TIME_PATTERNS}）。</p>
     */
    public static long parseDateTime(String value) {
        String v = normalizeDateTimeText(value);
        if (v.length() == 0) {
            return 0L;
        }
        for (int i = 0; i < DATE_TIME_PATTERNS.length; i++) {
            try {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(DATE_TIME_PATTERNS[i]);
                format.setLenient(true);
                java.util.Date parsed = format.parse(v);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignore) {
                // 换下一种写法继续试
            }
        }
        return 0L;
    }

    /**
     * {@link #parseDateTime(String)} 的「按 Asia/Shanghai（东八区）挂钟时间解析」版。
     *
     * <p><b>什么时候必须用它</b>：要把 OA 侧的时间串换成**平台接口要的 Unix 时间戳**时。
     * {@link #parseDateTime(String)} 用的是 {@code SimpleDateFormat} 的默认时区（= 运行环境时区），
     * 只要 JVM 默认时区不是东八区，算出来的“秒”就差 8 小时——平台侧按真正的 Unix 秒比较，
     * 于是查询窗口整体偏移（2026-10-15 现场：企微打卡数据窗口被写成
     * “前一天 16:00 ~ 当天 15:59:59”，当天下班卡取不回来）。
     * 这里显式指定时区，结果与运行环境时区无关。</p>
     *
     * <p>写法宽容度与 {@link #parseDateTime(String)} 完全一致（同一份
     * {@link #normalizeDateTimeText(String)} + {@link #DATE_TIME_PATTERNS}），
     * 解析不了同样返回 0，由调用方按“时间缺失”处理。</p>
     *
     * <p>本模块其它地方（OA 侧展示与比较用的纯文本时间串）继续用
     * {@link #parseDateTime(String)} / {@link #formatDateTime(long)}——那两处口径一致，
     * 只要运行环境是东八区就互相抵消，不需要改；只有“给平台的时间戳”这一类必须用本方法。</p>
     */
    public static long parseDateTimeTgz(String value) {
        String v = normalizeDateTimeText(value);
        if (v.length() == 0) {
            return 0L;
        }
        for (int i = 0; i < DATE_TIME_PATTERNS.length; i++) {
            try {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(DATE_TIME_PATTERNS[i]);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                format.setLenient(true);
                java.util.Date parsed = format.parse(v);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignore) {
                // 换下一种写法继续试
            }
        }
        return 0L;
    }

    /**
     * {@link #parseDateTime(String)} 的日期对象版：解析不了返回 {@code null}。
     *
     * <p>考勤同步的计划任务「下次执行时间」与设置行健康度用它——那些地方
     * “没有值”与“1970 年”是完全不同的语义，用 0 兜底容易把计划任务算成“早就到点了”。</p>
     */
    public static Date parseDateTimeSafe(String value) {
        long millis = parseDateTime(value);
        return millis <= 0L ? null : new Date(millis);
    }

    /**
     * 时间文本归一化：把中文分隔符与各种分隔符统一成 {@code yyyy-MM-dd HH:mm:ss} 的好解析形状。
     *
     * <p>下面这个方法 {@link #parseDateTimeSafe(String)} 是 {@link #parseDateTime(String)} 的
     * “日期对象版”：解析不了返回 {@code null} 而不是 0，方便计划任务 / 时间窗这类
     * 「没有值就是没有值」的场景（考勤同步的计划任务与健康度展示用它）。</p>
     *
     * <pre>
     *   2026年10月10日 9时0分0秒  → 2026-10-10 9:0:0
     *   2026/10/10 09:00          → 2026-10-10 09:00
     *   2026-10-10T09:00:00.000   → 2026-10-10 09:00:00-000（毫秒被当成后缀忽略）
     *   2026-10-10  09:00:00      → 2026-10-10 09:00:00（多空格合并）
     * </pre>
     *
     * <p>只做“写法归一”，不补缺失的分 / 秒——补位交给 {@link #DATE_TIME_PATTERNS} 里
     * 由长到短的候选格式。</p>
     */
    private static String normalizeDateTimeText(String value) {
        String v = s(value).trim();
        if (v.length() == 0) {
            return "";
        }
        v = v.replace('年', '-').replace('月', '-').replace('日', ' ')
                .replace('时', ':').replace('分', ':').replace('秒', ' ')
                .replace('/', '-').replace('.', '-').replace('T', ' ');
        v = v.replaceAll("\\s+", " ").trim();
        return v;
    }

    /** 在 {@code yyyy-MM-dd} 上加减天数（同步时间窗用；解析不了原样返回）。 */
    public static String shiftDate(String date, int days) {
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(DATE_ONLY_FORMAT.get().parse(s(date).trim()));
            c.add(java.util.Calendar.DAY_OF_MONTH, days);
            return DATE_ONLY_FORMAT.get().format(c.getTime());
        } catch (Exception e) {
            return s(date);
        }
    }

    /**
     * 毫秒时间戳 → {@code yyyy-MM-dd HH:mm:ss}（与 {@link #parseDateTime(String)} 同一口径）。
     *
     * <p>平台日程接口给的是时间戳（企微是秒、钉钉/飞书是毫秒），拉取（第三方→OA）时
     * 统一换算成 OA 侧的时间串。</p>
     */
    public static String formatDateTime(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return DATE_FORMAT.get().format(new java.util.Date(millis));
    }

    /** 秒级时间戳 → {@code yyyy-MM-dd HH:mm:ss}（企微日程接口口径）。 */
    public static String formatDateTimeSeconds(long seconds) {
        return seconds <= 0L ? "" : formatDateTime(seconds * 1000L);
    }

    /**
     * 把“日期时间”检索值拆成 [日期, 时间] 两段——对应建模标准字段
     * {@code modedatacreatedate}（varchar(10)）/ {@code modedatacreatetime}（varchar(8)）。
     *
     * <p>页面上的“时间”检索框统一是日期时间框（{@code yyyy-MM-dd HH:mm:ss}，精确到秒），
     * 而库里是两列，所以过滤前在这里拆开；<b>只填日期</b>（{@code yyyy-MM-dd}）时时间按
     * {@code defaultTime} 补齐（起点 {@code 00:00:00}、终点 {@code 23:59:59}，正好覆盖整天）；
     * 空值返回 {@code [null, null]}，表示该侧不限制。</p>
     *
     * <p>与消息中心推送日志的 {@code MsgLogService#splitDateTime} 同一口径
     * （那边已改为委托本方法），全模块只保留这一份实现。</p>
     */
    public static String[] splitDateTime(String value, String defaultTime) {
        String text = s(value).trim();
        if (text.length() == 0) {
            return new String[]{null, null};
        }
        String date = text;
        String time = defaultTime;
        int space = text.indexOf(' ');
        if (space < 0) {
            space = text.indexOf('T');
        }
        if (space > 0) {
            date = text.substring(0, space).trim();
            String rest = text.substring(space + 1).trim();
            if (rest.length() > 0) {
                time = rest.length() >= 8 ? rest.substring(0, 8) : rest;
            }
        }
        return new String[]{date, time == null || time.length() == 0 ? defaultTime : time};
    }

    public static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    public static String buildQuery(Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder();
        if (params == null || params.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey()))
                    .append('=')
                    .append(urlEncode(e.getValue()));
        }
        return sb.toString();
    }

    public static JSONObject ok() {
        JSONObject obj = new JSONObject();
        obj.put("code", 0);
        obj.put("msg", "success");
        return obj;
    }

    public static JSONObject ok(Object data) {
        JSONObject obj = ok();
        obj.put("data", data);
        return obj;
    }

    public static JSONObject fail(String message) {
        JSONObject obj = new JSONObject();
        obj.put("code", -1);
        obj.put("msg", message == null ? "操作失败" : message);
        return obj;
    }

    public static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
