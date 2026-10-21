package com.customization.oaones.appsyn.job;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesPlatformGate;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.SyncPlanService;
import com.customization.oaones.appsyn.service.impl.SyncPlanServiceImpl;
import weaver.general.BaseBean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同步中心“计划任务”的<b>内置调度器</b>：页面配置的 cron 直接生效，**不需要 E9 后台计划任务**。
 *
 * <p>做法：本模块自己在 E9 webapp 里起一个守护线程，按固定间隔（默认 30 秒）扫描
 * {@code uf_oaones_sync_plan}，把 {@code next_run_time} 已到点的设置交给
 * {@link SyncPlanService#runDuePlans(int)} 执行；每条设置在执行前先“认领”
 * （条件更新 {@code next_run_time}），因此<b>多实例部署不会重复跑</b>，
 * 与 E9 后台计划任务（可选保留的 {@link OaonesSyncPlanCronJob}）同时存在也不会重复。</p>
 *
 * <p>启动时机（幂等，只起一个线程）：</p>
 * <ul>
 *   <li>任何 OAones 页面被访问时（{@code oaones/redirect.inc.jsp}）；</li>
 *   <li>任何 OAones 接口被调用时（三个连接器的 {@code OaonesController});</li>
 *   <li>可选：在 {@code WEB-INF/web.xml} 里注册
 *       {@link OaonesSyncPlanSchedulerListener}，则 E9 一启动就跑起来（不依赖有人访问）。</li>
 * </ul>
 *
 * <p>开关与频率在 {@code oaones.properties}：{@code sync.plan.enable} / {@code sync.plan.tick.seconds} /
 * {@code sync.plan.batch}（见 {@link OaonesProps}）。</p>
 */
public final class OaonesSyncPlanScheduler {

    /** 调度线程名（线程转储里一眼能认出来）。 */
    private static final String THREAD_NAME = "oaones-sync-plan";

    /** 启动后的首次等待秒数：先避开启动高峰，真正能否开始执行由 {@link OaonesPlatformGate} 判定。 */
    private static final int INITIAL_DELAY_SECONDS = 20;

    private static volatile boolean started;
    private static volatile ScheduledExecutorService executor;
    /** 防止重入：一次扫描里的同步可能跑很久，扫描间隔到点时直接跳过这一次。 */
    private static final AtomicBoolean TICKING = new AtomicBoolean(false);
    private static volatile String lastTick = "";
    private static volatile String lastMessage = "";
    private static volatile int lastRunCount;

    private OaonesSyncPlanScheduler() {
    }

    /** 幂等启动（多次调用只起一个线程）；{@code sync.plan.enable=0} 时不启动。 */
    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        if (!OaonesProps.syncPlanSchedulerEnabled()) {
            return;
        }
        try {
            final int tickSeconds = OaonesProps.syncPlanTickSeconds();
            executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, THREAD_NAME);
                    // 守护线程：E9 停止时不会拖住 JVM；没配监听器也能随 webapp 一起退出
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
            OaonesLog.step("OAones 同步中心内置调度器已启动：每 " + tickSeconds
                    + " 秒扫描一次到点设置（页面 cron 直接生效，无需 E9 后台计划任务）");
        } catch (Throwable t) {
            OaonesLog.error("OAones 同步中心内置调度器启动失败", t);
        }
    }

    /** 停止调度器（webapp 停止时由可选的监听器调用）。 */
    public static synchronized void shutdown() {
        ScheduledExecutorService local = executor;
        executor = null;
        started = false;
        if (local != null) {
            local.shutdownNow();
            OaonesLog.step("OAones 同步中心内置调度器已停止");
        }
    }

    /** 一次扫描：到点设置由服务层逐条认领后执行；异常只记日志，绝不抛出影响线程存活。 */
    private static void tick() {
        if (!TICKING.compareAndSet(false, true)) {
            OaonesLog.step("同步中心内置调度器：上一次扫描还没结束（同步耗时较长），本次跳过");
            return;
        }
        try {
            // 平台（连接池）没就绪前绝不能碰 MyBatis：E9 的 MyBatisFactory 一生只初始化一次，
            // 抢在平台建池之前触发会让整个 JVM 的 MyBatis 永久为 null（见 OaonesPlatformGate）
            if (!OaonesPlatformGate.myBatisReady()) {
                return;
            }
            SyncPlanService service = new SyncPlanServiceImpl();
            int executed = service.runDuePlans(OaonesProps.syncPlanBatch());
            lastTick = OaonesUtil.now();
            lastRunCount = executed;
            lastMessage = executed > 0 ? ("本次执行 " + executed + " 个同步设置") : "无到点任务";
            if (executed > 0) {
                OaonesLog.step("同步中心内置调度器：" + lastMessage);
            }
        } catch (Throwable t) {
            lastTick = OaonesUtil.now();
            lastRunCount = 0;
            lastMessage = "扫描失败：" + (t.getMessage() == null ? t.getClass().getName() : t.getMessage());
            OaonesLog.error("同步中心内置调度器扫描失败", t);
            new BaseBean().writeLog("oaones sync plan scheduler tick error: " + t);
        } finally {
            TICKING.set(false);
        }
    }

    /** 调度器状态（页面 action {@code syncplan.scheduler} 直接用）。 */
    public static JSONObject status() {
        JSONObject json = new JSONObject();
        json.put("enabled", OaonesProps.syncPlanSchedulerEnabled());
        json.put("started", started);
        json.put("running", TICKING.get());
        json.put("tick_seconds", OaonesProps.syncPlanTickSeconds());
        json.put("batch", OaonesProps.syncPlanBatch());
        json.put("last_tick", lastTick);
        json.put("last_run_count", lastRunCount);
        json.put("last_message", lastMessage);
        return json;
    }
}
