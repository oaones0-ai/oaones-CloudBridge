package com.customization.oaones.appsyn.common;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * OAones Mapper 的 SQL 日志插件。
 *
 * <p>替换原 {@code OaonesRecordSet} 的 SQL 打印能力：
 * 在 MyBatis 执行前把最终 SQL 与参数值写入 mylog.log，
 * 只处理 {@code com.customization.oaones.appsyn.dao} 命名空间下的语句。</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class})
})
public class OaonesSqlLogInterceptor implements Interceptor {

    /** 仅记录本模块的 Mapper 语句。 */
    private static final String NAMESPACE = "com.customization.oaones.appsyn.dao.";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (args != null && args.length >= 2 && args[0] instanceof MappedStatement) {
            MappedStatement ms = (MappedStatement) args[0];
            if (ms.getId() != null && ms.getId().startsWith(NAMESPACE)) {
                try {
                    logStatement(ms, args[1]);
                } catch (Throwable t) {
                    OaonesLog.error("记录 OAones SQL 日志失败 statement=" + ms.getId(), t);
                }
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无自定义属性
    }

    private void logStatement(MappedStatement ms, Object parameterObject) {
        BoundSql boundSql = ms.getBoundSql(parameterObject);
        String sql = boundSql.getSql();
        List<Object> values = parameterValues(ms, boundSql, parameterObject);
        OaonesLog.sql(sql, values.toArray());
    }

    /** 按占位符顺序取出参数值，逻辑对齐 MyBatis 默认的参数处理器。 */
    private List<Object> parameterValues(MappedStatement ms, BoundSql boundSql, Object parameterObject) {
        List<Object> values = new ArrayList<Object>();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings == null || mappings.isEmpty()) {
            return values;
        }
        Configuration configuration = ms.getConfiguration();
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
        for (ParameterMapping mapping : mappings) {
            if (ParameterMode.OUT.equals(mapping.getMode())) {
                continue;
            }
            String property = mapping.getProperty();
            Object value = null;
            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            } else if (parameterObject == null) {
                value = null;
            } else if (registry.hasTypeHandler(parameterObject.getClass())) {
                value = parameterObject;
            } else if (metaObject != null && metaObject.hasGetter(property)) {
                value = metaObject.getValue(property);
            }
            values.add(value);
        }
        return values;
    }
}
