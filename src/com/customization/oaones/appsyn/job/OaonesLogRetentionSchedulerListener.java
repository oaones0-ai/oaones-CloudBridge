package com.customization.oaones.appsyn.job;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * E9 启动时拉起日志保留自动清理线程。
 *
 * <p>即使之后没有任何 OAones 页面或接口访问，也会按每天一次的频率清理过期日志。
 * 首次执行前仍会经过平台就绪闸门，避免在数据库连接池初始化前触发 MyBatis。</p>
 */
public class OaonesLogRetentionSchedulerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        OaonesLogRetentionScheduler.ensureStarted();
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        OaonesLogRetentionScheduler.shutdown();
    }
}
