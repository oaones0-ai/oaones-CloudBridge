package com.customization.oaones.appsyn.job;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesPlatformGate;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.impl.MtgSyncServiceImpl;
import weaver.general.BaseBean;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 「会议同步 / 日程同步」的内置调度器：按页面上的**计划任务**跑一轮同步。
 *
 * <p>计划任务有两种同步方式（见 {@code uf_oaones_appsyn_mtg_cron.plan_mode}）：<b>按时间段</b>
 * （明细表 {@code uf_oaones_appsyn_mtg_plan_item}，页面上自由配多个时段 + 每段一个轮询间隔，
 * 按工作日 / 非工作日区分）与<b>高级（cron 表达式）</b>；工作日口径取泛微标准考勤设置。
 * 到点判断在 {@link MtgSyncServiceImpl#planDue(java.util.Date, java.util.Date)}，本类只负责看表。</p>
 *
 * <p>一轮 = 先推 OA→第三方（新增 / 修改 / 删除），再拉 第三方→OA（新增 / 修改 / 删除）——
 * 会议 / 日程是双向同步且共用同一套频率，因此两个方向在一个调度器里跑，不再各起一个。</p>
 *
 * <p>做法与同步中心的内置调度器（{@link OaonesSyncPlanScheduler}）一致：本模块自己在 E9 webapp 里
 * 起一个守护线程，每 {@code mtg.sync.tick.seconds}（默认 60 秒，收敛到 [30, 180]）**看一眼到点没有**；
 * 计划任务与到点判断都在服务端（表 {@code uf_oaones_appsyn_mtg_cron}），本类只保留“上次跑了没 / 结果”的运行状态。</p>
 *
 * <p>多实例部署时“到点各跑一轮”可能重复：同步链路口径是“按映射快照比对，变了才调接口”，
 * 重复执行最多是同一轮里多查一次，不会重复创建对象。</p>
 *
 * <p>启动时机（幂等）：任何 OAones 页面 / 接口被访问时；可选在 {@code WEB-INF/web.xml} 注册
 * {@link OaonesSyncPlanSchedulerListener} 让 E9 一启动就跑（该监听器已同时拉起本调度器）。</p>
 *
 * <p>开关与频率：总开关 {@code mtg.sync.enable}，检查间隔 {@code mtg.sync.tick.seconds}，
 * 同步频率（时间段明细 / cron）在页面上维护（{@code uf_oaones_appsyn_mtg_cron} +
 * {@code uf_oaones_appsyn_mtg_plan_item}）。</p>
 */
public final class OaonesMtgSyncScheduler {

    private static final String THREAD_NAME = "oaones-mtg-sync";
    /** 首次等待秒数：先让平台跑一会儿，真正能否开始执行由 {@link OaonesPlatformGate} 判定。 */
    private static final int INITIAL_DELAY_SECONDS = 15;

    private static volatile boolean started;
    private static volatile ScheduledExecutorService executor;
    private static final AtomicBoolean TICKING = new AtomicBoolean(false);
    private static volatile String lastTick = "";
    private static volatile String lastMessage = "";
    /** 上一次真正跑一轮的时间（null = 还没跑过；首次启动立即跑一轮）。 */
    private static volatile Date lastRunAt;

    private OaonesMtgSyncScheduler() {
    }

    /** 幂等启动（多次调用只起一个线程）；{@code mtg.sync.enable=0} 时不启动。 */
    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        if (!OaonesProps.mtgSyncSchedulerEnabled()) {
            return;
        }
        try {
            final int tickSeconds = OaonesProps.mtgSyncTickSeconds();
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
            OaonesLog.step("OAones 会议/日程同步内置调度器已启动：每 " + tickSeconds
                    + " 秒检查一次计划任务是否到点");
        } catch (Throwable t) {
            OaonesLog.error("OAones 会议/日程同步内置调度器启动失败", t);
        }
    }

    /** 停止调度器（webapp 停止时由可选的监听器调用）。 */
    public static synchronized void shutdown() {
        ScheduledExecutorService local = executor;
        executor = null;
        started = false;
        if (local != null) {
            local.shutdownNow();
            OaonesLog.step("OAones 会议/日程同步内置调度器已停止");
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
            MtgSyncServiceImpl service = new MtgSyncServiceImpl();
            Date now = new Date();
            if (!service.planDue(lastRunAt, now)) {
                return;   // 还没到计划任务的下一次执行时间
            }
            lastRunAt = now;
            JSONObject summary = service.syncRound();
            lastTick = OaonesUtil.now();
            lastMessage = OaonesUtil.s(summary.getString("message"));
        } catch (Throwable t) {
            lastTick = OaonesUtil.now();
            lastMessage = "同步失败：" + (t.getMessage() == null ? t.getClass().getName() : t.getMessage());
            OaonesLog.error("OAones 会议/日程同步内置调度器执行失败", t);
            new BaseBean().writeLog("oaones mtg sync scheduler tick error: " + t);
        } finally {
            TICKING.set(false);
        }
    }

    /**
     * 调度器状态（页面 action {@code mtgsync.scheduler} 直接用）：
     * 计划任务配置 + 规则文案 + 下次 / 上次执行时间 + 最近一轮的结果。
     */
    public static JSONObject status() {
        JSONObject json = new JSONObject();
        json.put("enabled", OaonesProps.mtgSyncSchedulerEnabled());
        json.put("started", started);
        json.put("running", TICKING.get());
        json.put("tick_seconds", OaonesProps.mtgSyncTickSeconds());
        json.put("batch", OaonesProps.mtgSyncBatch());
        json.put("last_tick", lastTick);
        json.put("last_message", lastMessage);
        json.put("last_run", lastRunAt == null ? "" : OaonesUtil.formatDateTime(lastRunAt.getTime()));
        try {
            JSONObject plan = new MtgSyncServiceImpl().syncPlan();
            json.putAll(plan);
            json.put("plan_enabled", plan.getIntValue("enabled"));
            json.put("next_run", plan.getString("next_run"));
            json.put("rule_text", plan.getString("rule_text"));
        } catch (Throwable t) {
            // 计划任务读不出来（比如表还没升级）：状态里给出原因，页面照样能打开
            json.put("plan_error", t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        }
        return json;
    }
}
