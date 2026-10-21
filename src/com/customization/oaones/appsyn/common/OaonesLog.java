package com.customization.oaones.appsyn.common;

import org.apache.log4j.Logger;

/**
 * OAones 专用日志工具。
 *
 * <p>使用 E9 log4j 中已配置的 mylog appender，输出到
 * ecology/log/mylog/mylog.log；用于记录 OAones 每次 SQL 与关键业务步骤。
 * SQL 由 {@link OaonesSqlLogInterceptor} 在 MyBatis 执行前调用本类输出。</p>
 */
public final class OaonesLog {

    private static final Logger LOG = Logger.getLogger("mylog");

    private OaonesLog() {
    }

    public static void sql(String sql, Object... params) {
        if (sql == null || sql.length() == 0) {
            return;
        }
        LOG.info("[oaones][SQL] " + format(sql, params));
    }

    public static void step(String message) {
        if (message == null || message.length() == 0) {
            return;
        }
        LOG.info("[oaones][STEP] " + message);
    }

    public static void error(String message, Throwable t) {
        LOG.error("[oaones][ERROR] " + (message == null ? "" : message), t);
    }

    /** 把预处理占位符替换成实际参数值，便于日志里直接看到执行语句。 */
    private static String format(String sql, Object[] params) {
        String s = sql.replaceAll("\\s+", " ").trim();
        if (params == null || params.length == 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int pi = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '?' && pi < params.length) {
                sb.append(literal(params[pi++]));
            } else {
                sb.append(ch);
            }
        }
        if (pi < params.length) {
            sb.append("  -- 附加参数: ");
            for (int i = pi; i < params.length; i++) {
                if (i > pi) {
                    sb.append(", ");
                }
                sb.append(literal(params[i]));
            }
        }
        return sb.toString();
    }

    private static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }
}
