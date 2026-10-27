package com.customization.oaones.appsyn.common;

import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.Util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E9 建模表通用查询工具：按物理表名反查建模模块ID。
 *
 * <p>OAones 的业务表都是 E9 建模引擎的 uf_ 表。<b>配置里只登记表名</b>
 * （{@code oaones.appsyn.properties#oaones_xxx_table}），不再登记模块ID：
 * 需要模块ID的地方只有两处——写入数据行的 {@code formmodeid}、按建模共享规则重构单条数据权限
 * ——由表名反查得到：{@code workflow_bill.tablename → workflow_bill.id → modeinfo.id}
 * （与 {@code com.customization.ghedu.utils.WokflowAndModeDataUtil} 的取表口径一致，
 * 但本类属于 OAones 项目，独立实现，不引用 ghedu 项目的类，两个项目各自维护）。</p>
 *
 * <p>反查不到时返回 0：表还没建成建模表、或本来就是普通表时，调用方据此跳过
 * {@code formmodeid} 与权限重构，**不影响**绑定/同步/登录等本身的读写。</p>
 *
 * <p>反查结果按表名缓存，重复调用不会重复查库；表名为空、建模模块不存在、库不可用等
 * 取不到模块ID的情况返回 0，失败结果只缓存 1 分钟，避免配置错误时反复查库与刷日志，
 * 也保证建模模块补齐后不用重启。</p>
 */
public final class WeaverUtil {

    /** 反查失败后的重试间隔（毫秒）：失败结果只按此短周期缓存。 */
    private static final long FAIL_RETRY_MS = 60000L;

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<String, Cached>();

    /** 缓存项：成功项长期有效，失败项按 retryAt 之后重试。 */
    private static final class Cached {
        private final String value;
        private final long retryAt;

        Cached(String value, long retryAt) {
            this.value = value;
            this.retryAt = retryAt;
        }
    }

    private WeaverUtil() {
    }

    /**
     * 按物理表名反查建模模块ID（modeinfo.id，即各业务表里的 formmodeid）。
     *
     * <p>同一张表被多个建模模块引用时取最小的模块ID，保证同一张表每次反查结果一致。
     * 表名带 schema 前缀（如 {@code dbo.uf_xxx}）或大小写不一致都能命中。</p>
     *
     * @param table 物理表名（properties 里配置的表名）
     * @return 建模模块ID；表名无效或该表不是建模表时返回 0
     */
    public static int getModeIdByTableName(String table) {
        String name = normalizeTable(table);
        if (name.length() == 0) {
            return 0;
        }
        String value = resolve("modeinfo:table:" + name,
                "select min(m.id) as modeid from workflow_bill b, modeinfo m"
                        + " where m.formid = b.id and b.tablename = ?",
                "modeid", name);
        return Util.getIntValue(value, 0);
    }

    /** 表名归一：去空白、去库名/schema 前缀、统一小写（反查缓存键与比对都用它）。 */
    private static String normalizeTable(String table) {
        String name = Util.null2String(table).trim();
        if (name.length() == 0) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1).trim();
        }
        if (name.startsWith("[") && name.endsWith("]") && name.length() > 2) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name.toLowerCase();
    }

    /** 清空反查缓存（运维排查用；正常无需调用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static String resolve(String key, String sql, String column, Object... params) {
        long now = System.currentTimeMillis();
        Cached cached = CACHE.get(key);
        if (cached != null && (cached.value.length() > 0 || now < cached.retryAt)) {
            return cached.value;
        }
        String value = query(sql, column, params);
        if (value.length() > 0) {
            CACHE.put(key, new Cached(value, Long.MAX_VALUE));
        } else {
            CACHE.put(key, new Cached("", now + FAIL_RETRY_MS));
            new BaseBean().writeLog("oaones WeaverUtil 未能反查建模模块ID（调用方将跳过 formmodeid 与权限重构，"
                    + "失败结果 1 分钟内不重复查库），请检查建模模块与库连接：" + sql);
        }
        return value;
    }

    private static String query(String sql, String column, Object... params) {
        try {
            RecordSet rs = new RecordSet();
            if (rs.executeQuery(sql, params) && rs.next()) {
                return Util.null2String(rs.getString(column)).trim();
            }
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones WeaverUtil 反查建模模块ID异常：" + sql, t);
        }
        return "";
    }
}
