package com.customization.oaones.appsyn.job;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.LogRetentionCatalog;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesPlatformGate;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.impl.AttSyncServiceImpl;
import weaver.general.BaseBean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 「考勤同步」的内置调度器：按**每条设置自己的计划任务**跑一轮同步。
 *
 * <p>与会议 / 日程同步的内置调度器同一做法（需求 3.3.5：不需要在 E9 后台另配计划任务）：
 * 随任意 OAones 页面 / 接口访问幂等启动，每 {@code att.sync.tick.seconds}
 * （默认 60 秒，程序收敛到 [30, 180]）**看一眼有没有设置到点**，到点的那条跑一轮
 * （按它自己的范围、数据类型、时间口径执行；多条设置互不影响，一条失败不阻塞其它）。</p>
 *
 * <p>“到点”的口径在 {@link AttSyncServiceImpl#planDue}——与页面上的「预测后 10 次」
 * 共用同一份判断，所以页面看到的执行时间就是实际会跑的时间。</p>
 */
public final class OaonesAttSyncScheduler {

    private static final String THREAD_NAME = "oaones-att-sync";
    /** 首次等待秒数：先让平台跑一会儿，真正能否开始执行由 {@link OaonesPlatformGate} 判定。 */
    private static final int INITIAL_DELAY_SECONDS = 20;

    private static volatile boolean started;
    private static volatile ScheduledExecutorService executor;
    private static final AtomicBoolean TICKING = new AtomicBoolean(false);
    private static volatile String lastTick = "";
    private static volatile String lastMessage = "";
    private static volatile String lastRun = "";

    private OaonesAttSyncScheduler() {
    }

    /** 幂等启动（多次调用只起一个线程）。 */
    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        try {
            final int tickSeconds = OaonesProps.attSyncTickSeconds();
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
            }, INITIAL_DELAY_SECONDS, tickSeconds, TimeUnit.SECONDS);
            started = true;
            OaonesLog.step("OAones 考勤同步内置调度器已启动：每 " + tickSeconds
                    + " 秒检查一次各条设置的计划任务是否到点");
        } catch (Throwable t) {
            OaonesLog.error("OAones 考勤同步内置调度器启动失败", t);
        }
    }

    /** 停止调度器（webapp 停止时由可选的监听器调用）。 */
    public static synchronized void shutdown() {
        ScheduledExecutorService local = executor;
        executor = null;
        started = false;
        if (local != null) {
            local.shutdownNow();
            OaonesLog.step("OAones 考勤同步内置调度器已停止");
        }
    }

    private static void tick() {
        if (!TICKING.compareAndSet(false, true)) {
            return;
        }
        try {
            // 平台（连接池）没就绪前绝不能碰 MyBatis：E9 的 MyBatisFactory 一生只初始化一次，
            // 抢在平台建池之前触发会让整个 JVM 的 MyBatis 永久为 null（见 OaonesPlatformGate）
            if (!OaonesPlatformGate.myBatisReady()) {
                return;
            }
            if (!OaonesProps.attSyncEnabled()) {
                return;   // 功能总开关关闭时不执行任何自动同步
            }
            JSONObject summary = new AttSyncServiceImpl().syncRound();
            lastTick = OaonesUtil.now();
            lastMessage = OaonesUtil.s(summary.getString("message"));
            if (summary.getIntValue("count") > 0) {
                lastRun = lastTick;
            }
        } catch (Throwable t) {
            lastTick = OaonesUtil.now();
            lastMessage = "同步失败：" + (t.getMessage() == null ? t.getClass().getName() : t.getMessage());
            OaonesLog.error("OAones 考勤同步内置调度器执行失败", t);
            new BaseBean().writeLog("oaones att sync scheduler tick error: " + t);
        } finally {
            TICKING.set(false);
        }
    }

    /** 调度器状态（页面 action {@code attsync.scheduler} 用）。 */
    public static JSONObject status() {
        JSONObject json = new JSONObject();
        json.put("enabled", OaonesProps.attSyncEnabled());
        json.put("started", started);
        json.put("running", TICKING.get());
        json.put("tick_seconds", OaonesProps.attSyncTickSeconds());
        json.put("batch", OaonesProps.attSyncBatch());
        json.put("last_tick", lastTick);
        json.put("last_run", lastRun);
        json.put("last_message", lastMessage);
        json.put("item_limit", OaonesProps.attSyncItemLimit());
        json.put("user_batch", OaonesProps.attSyncUserBatch());
        json.put("day_batch", OaonesProps.attSyncDayBatch());
        com.customization.oaones.appsyn.service.impl.LogRetentionServiceImpl retention =
                new com.customization.oaones.appsyn.service.impl.LogRetentionServiceImpl();
        json.put("log_keep_days", Integer.valueOf(retention.keepDays(LogRetentionCatalog.ATT_LOG)));
        json.put("log_item_keep_days", Integer.valueOf(retention.keepDays(LogRetentionCatalog.ATT_LOG_ITEM)));
        return json;
    }
}
