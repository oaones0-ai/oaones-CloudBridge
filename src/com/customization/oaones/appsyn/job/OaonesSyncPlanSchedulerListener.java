package com.customization.oaones.appsyn.job;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * （可选）让同步中心的<b>内置调度器</b>在 E9 webapp 启动时就跑起来。
 *
 * <p>默认不用配：任何 OAones 页面被访问、或任何 OAones 接口被调用时都会幂等启动调度器
 * （见 {@link OaonesSyncPlanScheduler}）。若希望“没人访问也能按 cron 跑”
 * （例如长假期间），在 {@code ecology/WEB-INF/web.xml} 里追加一行监听器即可：</p>
 *
 * <pre>
 * &lt;listener&gt;
 *     &lt;listener-class&gt;com.customization.oaones.appsyn.job.OaonesSyncPlanSchedulerListener&lt;/listener-class&gt;
 * &lt;/listener&gt;
 * </pre>
 *
 * <p>注意：E9 升级若覆盖 web.xml，这行需要重新追加；不配也不影响正常使用。</p>
 */
public class OaonesSyncPlanSchedulerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        OaonesSyncPlanScheduler.ensureStarted();
        // 同时拉起「会议 / 日程同步」的调度器：它按页面上的「计划任务」（白天 / 夜间两套频率）
        // 跑一轮同步（OA→第三方 推送 + 第三方→OA 拉取，两个方向同一频率）
        OaonesMtgSyncScheduler.ensureStarted();
        // 以及「考勤同步」的调度器：按**每条设置自己的计划任务**跑一轮
        // （第三方→OA 导入打卡 + OA→第三方 推送，两个方向各自独立配置与执行）
        OaonesAttSyncScheduler.ensureStarted();
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        OaonesAttSyncScheduler.shutdown();
        OaonesMtgSyncScheduler.shutdown();
        OaonesSyncPlanScheduler.shutdown();
    }
}
