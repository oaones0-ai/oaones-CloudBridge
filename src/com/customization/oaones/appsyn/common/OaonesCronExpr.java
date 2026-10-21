package com.customization.oaones.appsyn.common;

import org.quartz.CronExpression;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * cron 表达式工具（Quartz 口径，与 E9 后台“计划任务”同一套语法）。
 *
 * <p>同步中心（一级菜单）的“同步设置”允许为每个“租户 + 方向”配一条 cron 表达式：
 * 页面保存前用 {@link #valid(String)} 校验、并把它交给页面展示
 * {@link #nextTimes(String, Date, int)} 预测出的后 10 次执行时间（所见即所得）；
 * 计划任务类 {@code job.OaonesSyncPlanCronJob} 用 {@link #nextAfter(String, Date)}
 * 维护 {@code uf_oaones_sync_plan.next_run_time}，到点即触发该租户该方向的同步。</p>
 *
 * <p>Quartz 的 cron 是 <b>6 个字段（秒 分 时 日 月 周）</b>或 7 个字段（多一个年），
 * 与“5 字段的 Linux crontab”不同：页面提示语与报错都按这个口径写，避免现场少写秒字段。</p>
 */
public final class OaonesCronExpr {

    /** 执行时间展示格式（与同步日志的 starttime / endtime 一致）。 */
    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** cron 表达式最大长度（与 uf_oaones_sync_plan.cron 列宽一致）。 */
    public static final int MAX_LENGTH = 100;

    private OaonesCronExpr() {
    }

    /** 表达式是否合法（空、超长、Quartz 解析不了都算不合法）。 */
    public static boolean valid(String cron) {
        String text = normalize(cron);
        if (text.length() == 0 || text.length() > MAX_LENGTH) {
            return false;
        }
        try {
            new CronExpression(text);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 规范化：去掉首尾空白、把连续空白压成一个空格（Quartz 自身对大小写不敏感）。 */
    public static String normalize(String cron) {
        String text = OaonesUtil.s(cron).trim();
        return text.replaceAll("\\s+", " ");
    }

    /**
     * 从 after（含 after 之后）起预测 count 次执行时间。
     *
     * @param after 起算时间；为空表示从现在开始
     * @return 形如 {@code 2026-09-28 02:00:00} 的时间串；表达式非法时返回空列表
     */
    public static List<String> nextTimes(String cron, Date after, int count) {
        List<String> times = new ArrayList<String>();
        String text = normalize(cron);
        if (count <= 0 || !valid(text)) {
            return times;
        }
        try {
            CronExpression expr = new CronExpression(text);
            SimpleDateFormat fmt = new SimpleDateFormat(PATTERN);
            Date base = after == null ? new Date() : after;
            Date next = expr.getNextValidTimeAfter(base);
            while (next != null && times.size() < count) {
                times.add(fmt.format(next));
                next = expr.getNextValidTimeAfter(next);
            }
        } catch (Throwable t) {
            OaonesLog.error("cron 预测失败: " + cron, t);
        }
        return times;
    }

    /** 下一次执行时间（yyyy-MM-dd HH:mm:ss）；表达式非法时返回空串。 */
    public static String nextAfter(String cron, Date after) {
        List<String> times = nextTimes(cron, after, 1);
        return times.isEmpty() ? "" : times.get(0);
    }
}
