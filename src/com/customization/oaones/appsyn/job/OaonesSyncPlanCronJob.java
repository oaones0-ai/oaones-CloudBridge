package com.customization.oaones.appsyn.job;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesCronExpr;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.SyncPlanService;
import com.customization.oaones.appsyn.service.impl.SyncPlanServiceImpl;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 同步中心计划任务（按页面上配置的 cron 表达式触发）。
 *
 * <p>在 E9 后台“计划任务”里配置本类全限定名：
 * {@code com.customization.oaones.appsyn.job.OaonesSyncPlanCronJob}，
 * 建议<b>每分钟执行一次</b>（如 cron：{@code 0 * * * * ?}）。</p>
 *
 * <p>本类只做“到点判断 + 触发”：读 `uf_oaones_appsyn_sync_plan` 里 enabled=1 且
 * next_run_time 已到点的设置（一个“平台 + 租户 + 同步方向”一行），
 * 交给该平台连接器的同步服务执行，执行完按 cron 重算下次执行时间
 * （从当前时刻往后算，避免停机后“补跑”一堆历史任务）。</p>
 *
 * <p>三个平台共用这一个计划任务类；不需要再为企微/钉钉/飞书分别配任务
 * （各家原有的 {@code OaonesCronJob} 仍按 oaones.appsyn.properties 的口径跑全量同步，两者互不影响）。</p>
 */
public class OaonesSyncPlanCronJob extends BaseCronJob {

    /** 一次执行最多处理几条到点设置（按 next_run_time 升序，剩下的等下一次）。 */
    private static final int MAX_PER_RUN = 20;

    @Override
    public void execute() {
        BaseBean log = new BaseBean();
        try {
            SyncPlanService service = new SyncPlanServiceImpl();
            // 与模块内置调度器共用同一套“认领 + 执行”逻辑：
            // 两边都配了也不会重复跑（认领是条件更新，谁先改成功谁执行）
            int executed = service.runDuePlans(MAX_PER_RUN);
            if (executed > 0) {
                OaonesLog.step("同步设置计划任务（E9 后台）本次执行 " + executed + " 条");
            }
        } catch (Throwable t) {
            OaonesLog.error("同步设置计划任务异常", t);
            log.writeLog("oaones sync plan cron error: " + t);
        }
    }

    /** cron 表达式工具入口（供现场排查时复用同一口径）。 */
    public static String nextRunTime(String cron) {
        return OaonesCronExpr.nextAfter(cron, new java.util.Date());
    }
}
