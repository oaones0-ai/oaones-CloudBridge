package com.customization.oaones.appsyn.job;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesPlatformGate;
import com.customization.oaones.appsyn.service.impl.LogRetentionServiceImpl;
import com.customization.oaones.appsyn.common.OaonesUtil;
import weaver.general.BaseBean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志保留自动清理调度器。
 *
 * <p>每天检查一次各日志项配置；只有保留天数大于 0 的日志项才会执行删除。
 * 清理按建模标准时间列精确比较，不受服务重启影响。多个节点同时执行也只会重复删除
 * 同一批已过期数据，不会误删保留期内的数据。</p>
 */
public final class OaonesLogRetentionScheduler {

    private static final String THREAD_NAME = "oaones-log-retention";
    private static final int INITIAL_DELAY_SECONDS = 60;
    private static final int TICK_SECONDS = 24 * 60 * 60;
    /** 平台尚未就绪时 5 分钟后再试，避免首次检查恰好错过连接池初始化后要等一天。 */
    private static final int RETRY_SECONDS = 5 * 60;

    private static volatile boolean started;
    private static volatile ScheduledExecutorService executor;
    private static final AtomicBoolean TICKING = new AtomicBoolean(false);
    private static volatile String lastRun = "";
    private static volatile String lastMessage = "";
    private static volatile int lastDeleted;

    private OaonesLogRetentionScheduler() {
    }

    /** 幂等启动；启动失败不影响业务页面。 */
    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        try {
            executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, THREAD_NAME);
                    thread.setDaemon(true);
                    return thread;
                }
            });
            executor.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            }, INITIAL_DELAY_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
            started = true;
            OaonesLog.step("OAones 日志保留清理调度器已启动：每天检查一次");
        } catch (Throwable t) {
            OaonesLog.error("OAones 日志保留清理调度器启动失败", t);
        }
    }

    /** 停止调度器。 */
    public static synchronized void shutdown() {
        ScheduledExecutorService local = executor;
        executor = null;
        started = false;
        if (local != null) {
            local.shutdownNow();
            OaonesLog.step("OAones 日志保留清理调度器已停止");
        }
    }

    private static void tick() {
        if (!TICKING.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!OaonesPlatformGate.myBatisReady()) {
                retrySoon();
                return;
            }
            JSONObject result = new LogRetentionServiceImpl().cleanExpiredAll();
            lastRun = OaonesUtil.now();
            lastDeleted = result.getIntValue("deleted");
            lastMessage = OaonesUtil.s(result.getString("message"));
            if (result.getIntValue("failed") > 0 || lastDeleted > 0) {
                OaonesLog.step("[logRetention] 自动清理：" + lastMessage);
            }
        } catch (Throwable t) {
            lastRun = OaonesUtil.now();
            lastDeleted = 0;
            lastMessage = "自动清理失败：" + (t.getMessage() == null ? t.getClass().getName() : t.getMessage());
            OaonesLog.error("OAones 日志保留自动清理失败", t);
            new BaseBean().writeLog("oaones log retention scheduler error: " + t);
        } finally {
            TICKING.set(false);
        }
    }

    private static void retrySoon() {
        ScheduledExecutorService local = executor;
        if (local == null || local.isShutdown()) {
            return;
        }
        try {
            local.schedule(new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            }, RETRY_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable ignore) {
            // webapp 正在停止时提交失败不影响下一次正常启动
        }
    }

    /** 页面展示的调度状态。 */
    public static JSONObject status() {
        JSONObject json = new JSONObject();
        json.put("started", started);
        json.put("running", TICKING.get());
        json.put("tickSeconds", Integer.valueOf(TICK_SECONDS));
        json.put("lastRun", lastRun);
        json.put("lastDeleted", Integer.valueOf(lastDeleted));
        json.put("lastMessage", lastMessage);
        return json;
    }
}
