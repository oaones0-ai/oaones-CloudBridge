package com.customization.oaones.appsyn.msg.feishu.common;

import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.WeaverUtil;
import weaver.general.BaseBean;

/**
 * 消息中心缺省参数与专有表名（ecology/WEB-INF/prop/oaones.appsyn.properties）。
 *
 * <p><b>properties 里只配表名，不配模块ID</b>：四张消息表（配置 / 消息类型 / 推送日志 / 卡片任务）
 * 各配一个 {@code oaones_msg_xxx_table}；需要建模模块ID时才按表名反查
 * （{@link WeaverUtil}，“workflow_bill.tablename → modeinfo.formid → modeinfo.id”）。</p>
 *
 * <p>表名没配或配成空时兜底用下面的默认物理表名，保证模块可用；兜底只影响取表名，
 * 不影响其它业务逻辑。</p>
 */
public final class FeishuMsgProps {

    private static final String PROP_FILE = "oaones.appsyn";

    /** 未配置表名时的兜底表名（与 sql/create_oaones_msg_center.sql 建表脚本一致）。 */
    private static final String DEF_CONFIG_TABLE = "uf_oaones_appsyn_msg_config";
    private static final String DEF_TYPE_TABLE = "uf_oaones_appsyn_msg_type";
    private static final String DEF_TYPE_APP_TABLE = "uf_oaones_appsyn_msg_type_app";
    private static final String DEF_LOG_TABLE = "uf_oaones_appsyn_msg_log";
    private static final String DEF_TASK_TABLE = "uf_oaones_appsyn_msg_task";

    private FeishuMsgProps() {
    }

    private static String prop(String key, String def) {
        try {
            String value = new BaseBean().getPropValue(PROP_FILE, key);
            if (value == null || value.trim().length() == 0) {
                return def;
            }
            return value.trim();
        } catch (Throwable t) {
            return def;
        }
    }

    private static int propInt(String key, int def) {
        String value = prop(key, "");
        if (value.length() == 0) {
            return def;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return def;
        }
    }

    /** 业务表物理名：properties 里配置的就是表名（表名的唯一来源），留空用默认表名兜底。 */
    private static String tableOf(String key, String def) {
        return prop(key, def);
    }

    /** 系统参数表（配置项 oaones_msg_config_table）。 */
    public static String configTable() {
        return tableOf("oaones_msg_config_table", DEF_CONFIG_TABLE);
    }

    /**
     * 飞书应用表：**与“应用绑定”共用同一张表**（uf_oaones_appsyn_app_bind）。
     *
     * <p>一个飞书自建应用只维护一次：应用绑定页维护工作台入口页面，
     * 消息中心引用同一行的应用名称 / App ID / 应用 Secret / 所属租户 / 启停，
     * 不再另建消息专用应用表；表名与组织同步同一处取值（oaones_app_bind_table）。</p>
     */
    public static String appBindTable() {
        return OaonesProps.appBindTable();
    }

    /** 消息类型映射表（配置项 oaones_msg_type_table）。 */
    public static String typeTable() {
        return tableOf("oaones_msg_type_table", DEF_TYPE_TABLE);
    }

    /**
     * 消息类型「推送目标应用」明细表（配置项 {@code oaones_msg_type_app_table}）。
     *
     * <p>一行 = 一个消息类型 + 一个租户 + 一个应用（页面按租户逐行设置目标应用）。</p>
     */
    public static String typeAppTable() {
        return tableOf("oaones_msg_type_app_table", DEF_TYPE_APP_TABLE);
    }

    /** 推送日志表（配置项 oaones_msg_log_table）。 */
    public static String logTable() {
        return tableOf("oaones_msg_log_table", DEF_LOG_TABLE);
    }

    /** 卡片任务表（配置项 oaones_msg_task_table）。 */
    public static String taskTable() {
        return tableOf("oaones_msg_task_table", DEF_TASK_TABLE);
    }

    // ===== 建模模块ID：不配置，按上面对应的表名反查（见 WeaverUtil#getModeIdByTableName） =====

    /** 系统参数表建模模块ID（按 {@link #configTable()} 反查）。 */
    public static int configModeId() {
        return WeaverUtil.getModeIdByTableName(configTable());
    }

    /** 消息类型映射表建模模块ID（按 {@link #typeTable()} 反查）。 */
    public static int typeModeId() {
        return WeaverUtil.getModeIdByTableName(typeTable());
    }

    /** 卡片任务表建模模块ID（按 {@link #taskTable()} 反查）。 */
    public static int taskModeId() {
        return WeaverUtil.getModeIdByTableName(taskTable());
    }

    /**
     * 推送日志表的建模模块ID（按 {@link #logTable()} 反查；写入日志行的 formmodeid，
     * 便于在建模引擎里查询日志）。
     *
     * <p>日志表**不做权限重构**：只记录模块ID，不调用 ModeRightInfo；
     * 反查不到（表不是建模表）时不写值，不影响推送与日志页面。</p>
     */
    public static int logModeId() {
        return WeaverUtil.getModeIdByTableName(logTable());
    }

    /** 组织同步的飞书租户配置表（消息中心按接收人定位应用时复用，表名与组织同步同一处取值）。 */
    public static String cfgTable() {
        return OaonesProps.cfgTable();
    }

    /** OA 人员 - 飞书成员绑定表（OA 账号 -> 飞书 userid，表名与组织同步同一处取值）。 */
    public static String userBindTable() {
        return OaonesProps.userBindTable();
    }

    /**
     * 飞书统一消息接口调用密钥（{@code /oaones/appsyn/Feishu/msgapi.jsp}），留空关闭该入口。
     *
     * <p>与企微的 {@code msg.api.key} 分开维护：留空时**不回退**到企微密钥，
     * 避免误用企业微信的密钥往飞书推消息。</p>
     */
    public static String apiKey() {
        return prop("msg.feishu.api.key", "");
    }

    /**
     * 跳转地址是否包一层【飞书工作台免登录入口】（`msg.oa.login.entry`，默认 1=包）。
     *
     * <p>飞书里点开卡片时浏览器没有 OA 会话，直接打开站内页面会提示“登录超时”；
     * 默认把站内地址换成 {@code /oaones/appsyn/appBind/Feishu/appEntry.jsp?cfg_id=..&agent_id=..&state=..}
     * （飞书网页授权静默换身份 → 写入 OA 会话 → 跳到业务页）。
     * 环境里已经用别的 SSO（如 E9 自带飞书免登录）覆盖站内地址时，配 0 关闭本包装。</p>
     */
    public static int loginEntry() {
        return propInt("msg.oa.login.entry", 1);
    }

    public static int pushEnable() {
        return propInt("msg.push.enable", 1);
    }

    public static int retryTimes() {
        return propInt("msg.retry.times", 3);
    }

    public static int retryInterval() {
        return propInt("msg.retry.interval", 60);
    }

    public static int shardSize() {
        return propInt("msg.shard.size", 1000);
    }

    public static int timeout() {
        return propInt("msg.timeout", 30000);
    }

    public static int tokenAdvanceSec() {
        return propInt("msg.token.advance.sec", 300);
    }

    public static int dedupWindow() {
        return propInt("msg.dedup.window", 600);
    }

    public static int logKeepDays() {
        return propInt("msg.log.keep.days", 0);
    }

    public static int taskKeepDays() {
        return propInt("msg.task.keep.days", 30);
    }

    /**
     * 未配置映射的标准消息类型：推送时是否按默认口径自动登记一行（配置项
     * {@code msg.type.auto.register}，默认 1=开）。
     *
     * <p><b>只有“本模块从未配置过”的类型才自动登记</b>（登记行备注写明来源）：
     * 已配置但被<b>停用</b>的类型不会因此恢复推送，避免绕开人工“暂停推送”的意图；
     * 关掉本开关后，未配置的类型照旧丢弃（不推送）。</p>
     */
    public static int autoRegisterTypes() {
        return propInt("msg.type.auto.register", 1);
    }

    /**
     * 自动登记要排除的标准消息类型 typecode（配置项 {@code msg.type.auto.register.exclude}，
     * 逗号分隔；空 = 不排除）。
     *
     * <p>用于把“不想推给飞书”的类型挡在门外（如系统内部提示类消息）；
     * 只影响自动登记，页面手工配置的行不受影响。</p>
     */
    public static boolean autoRegisterExcluded(int stdType) {
        String value = prop("msg.type.auto.register.exclude", "");
        if (value.length() == 0) {
            return false;
        }
        String target = String.valueOf(stdType);
        for (String item : value.split(",")) {
            if (item != null && item.trim().equals(target)) {
                return true;
            }
        }
        return false;
    }
}
