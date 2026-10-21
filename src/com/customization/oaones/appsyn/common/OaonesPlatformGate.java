package com.customization.oaones.appsyn.common;

import weaver.conn.ConnectionPool;
import weaver.general.GCONST;

/**
 * 「E9 平台就绪」闸门：内置调度器在碰 MyBatis 之前必须先过这里。
 *
 * <p><b>为什么需要它</b>：E9 的 {@code weaver.conn.mybatis.MyBatisFactory} 只有一个
 * {@code static final SqlSessionFactory sqlSessionFactory}，类初始化时解析一次
 * {@code WEB-INF/config/MyBatis.xml}，<b>失败就返回 null 且终生不重试</b>。
 * 解析过程中 MyBatis 会实例化平台的数据源工厂
 * {@code weaver.conn.mybatis.MyBatisDataSourceFactory}，它的构造函数是
 * {@code ConnectionPool.getInstance().getPool(null).getPooled()}；
 * 而 E9 自己的连接池要等自身 InitServer 初始化阶段才建好，服务器繁忙时可能启动 1 分钟后
 * 才就绪（日志里的脚印是 {@code weaver.conn.ConnectionPool} 打出的 {@code poolname==null}）。</p>
 *
 * <p>如果 Webapp 启动早期（例如本模块内置调度器的首次 tick）先触发了 MyBatis 初始化，
 * 上面这行就会抛 {@code NullPointerException}，被 {@code MyBatisFactory} 记成
 * 「读取MyBatis配置失败」后返回 null，此后<b>整个 JVM 内所有 MyBatis 调用都不可用</b>
 * （页面表现为「MyBatis 初始化失败，请检查 WEB-INF/config/MyBatis.xml 与
 * WEB-INF/config/mapper 下的 Mapper 配置」），只能重启 E9 才能恢复。</p>
 *
 * <p>因此本模块的调度器改成：每一轮先问这里「平台就绪了吗」，没就绪就跳过本轮、
 * <b>不碰任何 MyBatis 代码</b>，等下一轮（{@code *.tick.seconds} 之后）再试。
 * 这样 MyBatis 的首次初始化一定发生在平台连接池建成之后——也就是平台自身请求能正常工作的时机。</p>
 */
public final class OaonesPlatformGate {

    /** 「还在等平台就绪」提示的节流间隔：首次立即记，之后最多每 10 分钟记一条，避免每轮 tick 刷屏。 */
    private static final long WAIT_LOG_INTERVAL_MS = 10 * 60 * 1000L;

    private static volatile long lastWaitLogAt;
    /** 「平台已就绪」只记一次，且只在真的等待过之后才记。 */
    private static volatile boolean readyLogged;

    private OaonesPlatformGate() {
    }

    /**
     * 平台是否已经初始化到「可以安全地触发 MyBatis 初始化」的程度。
     *
     * <p>判断口径与平台 {@code MyBatisDataSourceFactory} 取池的方式一致：先要拿到本地服务器名
     * （{@link GCONST#getServerName()}，平台建池用的就是它），再看该名字下的连接池是否已经建好。
     * 任一步拿不到都算「没就绪」；平台没初始化完时 {@code ConnectionPool} 会抛空指针，
     * 这类异常一律按未就绪处理，绝不外抛。</p>
     *
     * @return true = 可以碰 MyBatis；false = 本轮请直接跳过，不要触发任何 MyBatis 调用
     */
    public static boolean myBatisReady() {
        if (probe()) {
            if (lastWaitLogAt != 0 && !readyLogged) {
                readyLogged = true;
                OaonesLog.step("E9 平台已初始化完成，OAones 内置调度器开始正常执行");
            }
            return true;
        }
        noteWaiting();
        return false;
    }

    /** 真正探测：拿不到服务器名就直接返回 false，连 ConnectionPool 都不去碰（它此时会 NPE）。 */
    private static boolean probe() {
        try {
            String serverName = GCONST.getServerName();
            if (serverName == null || serverName.length() == 0) {
                return false;
            }
            return ConnectionPool.getInstance().getPool(serverName) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void noteWaiting() {
        long now = System.currentTimeMillis();
        if (lastWaitLogAt != 0 && now - lastWaitLogAt < WAIT_LOG_INTERVAL_MS) {
            return;
        }
        lastWaitLogAt = now;
        OaonesLog.step("E9 平台还在初始化（连接池尚未就绪），本轮 OAones 调度先跳过、等下一轮再试；"
                + "这样可避免在平台就绪前触发 MyBatis 初始化，导致本 JVM 的 MyBatis 永久不可用");
    }
}
