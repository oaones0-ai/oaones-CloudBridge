package com.customization.oaones.appsyn.common;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import weaver.conn.mybatis.MyBatisFactory;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MyBatis 会话统一入口。
 *
 * <p>底层复用 E9 平台自带的 {@link MyBatisFactory}（配置文件
 * WEB-INF/config/MyBatis.xml，Mapper 目录 WEB-INF/config/mapper），
 * 因此数据源、事务与连接池与 E9 其余模块保持一致；
 * 本类只负责：</p>
 * <ul>
 *   <li>懒加载 SqlSessionFactory，并在首次使用时挂载 OAones 专用的 SQL 日志插件；</li>
 *   <li>为 DAO 层提供 {@code execute} / {@code call} 模板，统一开关会话（自动提交，与历史行为一致）。</li>
 * </ul>
 */
public final class OaonesMyBatis {

    private static volatile SqlSessionFactory sqlSessionFactory;
    private static volatile boolean sqlLoggerInstalled;
    private static volatile boolean factoryMissingLogged;

    private OaonesMyBatis() {
    }

    public static SqlSessionFactory factory() {
        SqlSessionFactory local = sqlSessionFactory;
        if (local == null) {
            synchronized (OaonesMyBatis.class) {
                local = sqlSessionFactory;
                if (local == null) {
                    local = MyBatisFactory.sqlSessionFactory;
                    if (local == null) {
                        noteFactoryMissing();
                        throw new IllegalStateException("MyBatis 初始化失败，请检查 "
                                + "WEB-INF/config/MyBatis.xml 与 WEB-INF/config/mapper 下的 Mapper 配置");
                    }
                    installSqlLogger(local);
                    sqlSessionFactory = local;
                }
            }
        }
        return local;
    }

    /** 打开自动提交会话（与模块历史“逐条提交”的行为保持一致）。 */
    public static SqlSession openSession() {
        return factory().openSession(true);
    }

    /** 模板方法：在自动提交会话中执行一次 Mapper 调用并返回结果。 */
    public static <M, R> R call(Class<M> mapperType, Function<M, R> work) {
        SqlSession session = openSession();
        try {
            return work.apply(session.getMapper(mapperType));
        } finally {
            session.close();
        }
    }

    /** 模板方法：在自动提交会话中执行一次无返回值的 Mapper 调用。 */
    public static <M> void run(Class<M> mapperType, Consumer<M> work) {
        SqlSession session = openSession();
        try {
            work.accept(session.getMapper(mapperType));
        } finally {
            session.close();
        }
    }

    /**
     * 平台 MyBatis 工厂为空时的诊断日志（每个 JVM 只记一条，避免每次查询都刷屏）。
     *
     * <p>这个状态没有自愈可能：平台的 {@code MyBatisFactory.sqlSessionFactory} 是
     * {@code static final}，只在类初始化时解析一次配置、失败后不会重试，
     * 因此本 JVM 内所有 MyBatis 调用都会一直失败，只能重启 E9。</p>
     */
    private static void noteFactoryMissing() {
        if (factoryMissingLogged) {
            return;
        }
        factoryMissingLogged = true;
        OaonesLog.error("平台 MyBatis 未初始化成功（MyBatisFactory.sqlSessionFactory 为 null），"
                + "本 JVM 内 OAones 所有数据库操作都不可用，需重启 E9 才能恢复。"
                + "常见原因是 Webapp 启动早期（平台连接池 / InitServer 尚未就绪）就有线程先触发了 MyBatis 初始化；"
                + "请在启动日志里搜『读取MyBatis配置失败』（其前一行通常是 weaver.conn.ConnectionPool 的 poolname==null）。"
                + "调度器侧已由 common/OaonesPlatformGate 拦在 MyBatis 之前，"
                + "若仍出现本条日志，请把该 JVM 的启动日志发出来核查。", null);
    }

    /**
     * 把 OAones 的 SQL 日志插件挂到平台共享的 MyBatis 配置上。
     * 插件内部只记录 {@code com.customization.oaones.appsyn.dao} 命名空间，
     * 不影响 E9 其它模块的日志。
     */
    private static void installSqlLogger(SqlSessionFactory factory) {
        if (sqlLoggerInstalled) {
            return;
        }
        try {
            factory.getConfiguration().addInterceptor(new OaonesSqlLogInterceptor());
            sqlLoggerInstalled = true;
        } catch (Throwable t) {
            OaonesLog.error("挂载 OAones SQL 日志插件失败（不影响业务执行）", t);
        }
    }
}
