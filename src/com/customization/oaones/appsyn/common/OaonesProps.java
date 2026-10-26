package com.customization.oaones.appsyn.common;

import weaver.general.BaseBean;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * OAones 模块配置。
 *
 * <p>配置项存放于 ecology/WEB-INF/prop/oaones.properties。
 * 业务表都是 E9 建模表，<b>配置里只登记表名</b>（{@code oaones_xxx_table}），
 * 不再登记模块ID：需要模块ID的地方（写入数据行 {@code formmodeid}、按建模共享规则
 * 重构单条数据权限）由 {@link WeaverUtil} 按
 * “workflow_bill.tablename → modeinfo.formid → modeinfo.id”反查，
 * 表不是建模表时为空/0，调用方跳过建模相关动作，不影响绑定/同步本身。</p>
 *
 * <p>表名没配或配成空时兜底用下面的默认表名（与 sql/oaones_tables.sql 一致），保证模块可用。</p>
 */
public final class OaonesProps {

    private static final String PROP_FILE = "oaones";
    /** 本模块配置文件（oaones.properties）的编码：GBK，与平台其它 properties 同一口径。 */
    private static final Charset CHARSET_GBK = Charset.forName("GBK");
    /** 已提示过“重解码还原”的配置项（同一 JVM 只提示一次，避免每次打开页面都刷日志）。 */
    private static final java.util.Set<String> CHARSET_REPAIR_LOGGED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    /** 未配置表名时的兜底表名（与 sql/oaones_tables.sql 建表脚本一致）。 */
    private static final String DEF_CFG_TABLE = "uf_oaones_qywx_config";
    private static final String DEF_DEPT_BIND_TABLE = "uf_oaones_dept_bind";
    private static final String DEF_SUB_TENANT_MAP_TABLE = "uf_oaones_sub_tenant_map";
    private static final String DEF_USER_BIND_TABLE = "uf_oaones_user_bind";
    private static final String DEF_USER_RULE_TABLE = "uf_oaones_user_rule";
    private static final String DEF_SYNC_LOG_TABLE = "uf_oaones_sync_log";
    private static final String DEF_SYNC_LOG_ITEM_TABLE = "uf_oaones_sync_log_item";
    private static final String DEF_SYNC_PLAN_TABLE = "uf_oaones_sync_plan";
    private static final String DEF_DEP_TMP_TABLE = "uf_oaones_dep_tmp";
    private static final String DEF_USER_TMP_TABLE = "uf_oaones_user_tmp";
    private static final String DEF_APP_BIND_TABLE = "uf_oaones_app_bind";
    private static final String DEF_APP_TABLE = "uf_oaones_app";
    private static final String DEF_MTG_SYNC_TABLE = "uf_oaones_mtg_sync";
    private static final String DEF_MTG_SYNC_LOG_TABLE = "uf_oaones_mtg_sync_log";
    private static final String DEF_MTG_SYNC_USER_TABLE = "uf_oaones_mtg_sync_user";
    private static final String DEF_MTG_LINK_TABLE = "uf_oaones_mtg_link";
    /** 会议 / 日程同步「计划任务」表（单行：白天 / 夜间两套 cron）。 */
    private static final String DEF_MTG_CRON_TABLE = "uf_oaones_mtg_cron";
    /** 会议 / 日程「计划任务」时间段明细表（没有配置项时用的兜底表名）。 */
    private static final String DEF_MTG_PLAN_ITEM_TABLE = "uf_oaones_mtg_plan_item";
    /** 考勤同步设置表（一行 = 平台 + 租户 + 方向，可多条；另含“租户级开关”行）。 */
    private static final String DEF_ATT_CFG_TABLE = "uf_oaones_att_cfg";
    /** 考勤同步「计划任务」时间段明细表（一行一个时间段，挂在设置行上）。 */
    private static final String DEF_ATT_PLAN_ITEM_TABLE = "uf_oaones_att_plan_item";
    /** 考勤同步批次表（一次执行一行）。 */
    private static final String DEF_ATT_LOG_TABLE = "uf_oaones_att_log";
    /** 考勤同步打卡明细表（一条打卡一次动作一行）。 */
    private static final String DEF_ATT_LOG_ITEM_TABLE = "uf_oaones_att_log_item";
    /** 考勤同步「平台打卡记录 ⇄ OA 打卡记录」映射表。 */
    private static final String DEF_ATT_LINK_TABLE = "uf_oaones_att_link";
    /** E9 标准考勤打卡表（正向同步的写入目标；表名可配，换版本只改配置）。 */
    private static final String DEF_ATT_SIGN_TABLE = "HrmScheduleSign";
    /** OA 会议 / 日程来源表（E9 标准表，只读；会议取 Meeting，日程取 WorkPlan，可配）。 */
    private static final String DEF_MTG_MEETING_TABLE = "Meeting";
    private static final String DEF_MTG_MEETING_MEMBER_TABLE = "Meeting_Member2";
    private static final String DEF_MTG_SCHEDULE_TABLE = "WorkPlan";
    private static final String DEF_MTG_SCHEDULE_MEMBER_TABLE = "workplanMember";
    private static final String DEF_SCAN_LOGIN_TABLE = "uf_oaones_scan_login";
    private static final String DEF_LOGIN_CONFIG_TABLE = "uf_oaones_login_config";
    private static final String DEF_LOGIN_SMS_TABLE = "uf_oaones_login_sms";
    private static final String DEF_PERSONAL_WX_CONFIG_TABLE = "uf_oaones_personal_wx_config";
    private static final String DEF_PERSONAL_WX_BIND_TABLE = "uf_oaones_personal_wx_bind";
    /** 接口日志表（oaones.properties#oaones_api_log_table）默认表名。 */
    private static final String DEF_API_LOG_TABLE = "uf_oaones_api_log";
    /** 操作监控日志表（oaones.properties#oaones_op_log_table）默认表名。 */
    private static final String DEF_OP_LOG_TABLE = "uf_oaones_op_log";
    /** 日志保留设置表（oaones.properties#oaones_log_retention_table）默认表名。 */
    private static final String DEF_LOG_RETENTION_TABLE = "uf_oaones_log_retention";
    /** OA 分权（选择 OA 组织架构时按登录人范围过滤）三类候选各自对应的 E9 分权权限点。 */
    private static final String DEF_SEC_RIGHT_SUB = "Subcompanys:decentralization";
    private static final String DEF_SEC_RIGHT_DEPT = "Departments:decentralization";
    private static final String DEF_SEC_RIGHT_USER = "Resources:decentralization";

    private OaonesProps() {
    }

    private static String prop(String key, String def) {
        try {
            String value = new BaseBean().getPropValue(PROP_FILE, key);
            if (value == null || value.trim().length() == 0) {
                return def;
            }
            return decodePropValue(key, value.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    /**
     * 把平台按 ISO-8859-1 读进来的属性值按 GBK 还原成中文（读每一个配置项都会过这一层）。
     *
     * <p>为什么要还原：平台的 {@code weaver.file.Prop#loadTemplateProp} 用的是
     * {@code Properties.load(InputStream)}，即按 <b>ISO-8859-1</b> 解码
     * （见 srcbak/com/weaver/file/Prop.java），所以 GBK 文件里的中文值到了内存就是
     * 一串 Latin-1 乱码（典型现象：登录页标题显示成 “OA ¿¼ÇÚÍ¬²½”），
     * 而**注释里的中文不受影响**——因为注释根本不进内存。</p>
     *
     * <p>还原口径：先把 Latin-1 串还原成原始字节，再**严格**按 GBK 解一次；
     * 判定要同时满足“解码成功、没有替换符、字符数变少”
     * （GBK 汉字在 Latin-1 下是一个字节占一个字符，解码后字符数必然变少），
     * 所以纯 ASCII 值、单字节的 Latin-1 文本、以及本来就不是 GBK 的值都原样返回，不会误伤
     * （只有“连着的两个 Latin-1 字符恰好构成一个合法 GBK 汉字”这种极端写法才可能被改写，
     * 本模块的配置里不存在这种值）。</p>
     *
     * <p>本模块的 oaones.properties 统一按 <b>GBK</b> 保存、值里直接写中文
     * （与平台其它 properties 同一口径，用 GBK 编辑器打开也正常显示）。
     * 注意：这里只认 GBK——把文件改存成 UTF-8 会让值里的中文读出乱码。</p>
     */
    private static String decodePropValue(String key, String raw) {
        if (raw == null || raw.length() == 0) {
            return raw;
        }
        boolean asciiOnly = true;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) > 0x7f) {
                asciiOnly = false;
                break;
            }
        }
        if (asciiOnly) {
            return raw;
        }
        byte[] bytes = raw.getBytes(StandardCharsets.ISO_8859_1);
        try {
            String decoded = CHARSET_GBK.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (decoded.length() > 0 && decoded.length() < raw.length()
                    && decoded.indexOf('\uFFFD') < 0) {
                if (CHARSET_REPAIR_LOGGED.add(key)) {
                    OaonesLog.step("配置项 " + key + " 按 GBK 重解码还原（平台 properties 读法为 ISO-8859-1）");
                }
                return decoded;
            }
        } catch (CharacterCodingException e) {
            // 不是 GBK 字节：保持原值（例如值本身就是 Latin-1 文本）
        }
        return raw;
    }

    /** 读整数配置；未配置或配了非数字时用 def 兜底（不抛异常，避免一个错配置把整页打挂）。 */
    private static int propInt(String key, int def) {
        try {
            String value = prop(key, "");
            if (value.length() == 0) {
                return def;
            }
            return Integer.parseInt(value);
        } catch (Throwable t) {
            return def;
        }
    }

    /**
     * 业务表物理名：properties 里配置的就是表名（表名的唯一来源）。
     *
     * <p>配置项留空时用 def 兜底，保证页面不至于因为一个空配置整块打不开。</p>
     */
    private static String tableOf(String key, String def) {
        return prop(key, def);
    }

    /** 多企业微信租户配置表（E9 建模表，配置项 oaones_qywx_config_table）。 */
    public static String cfgTable() {
        return tableOf("oaones_qywx_config_table", DEF_CFG_TABLE);
    }

    /** OA部门 - 企业微信部门 绑定表（E9 建模表，配置项 oaones_dept_bind_table）。 */
    public static String deptBindTable() {
        return tableOf("oaones_dept_bind_table", DEF_DEPT_BIND_TABLE);
    }

    /**
     * OA 分部 - 平台租户一对一映射表（配置项 oaones_sub_tenant_map_table）。
     *
     * <p>本表只服务独立的分部映射页面，不替代部门绑定表。</p>
     */
    public static String subTenantMapTable() {
        return tableOf("oaones_sub_tenant_map_table", DEF_SUB_TENANT_MAP_TABLE);
    }

    /** OA人员 - 企业微信成员 绑定表（E9 建模表，配置项 oaones_user_bind_table）。 */
    public static String userBindTable() {
        return tableOf("oaones_user_bind_table", DEF_USER_BIND_TABLE);
    }

    /** 默认人员绑定规则配置表（E9 建模表，配置项 oaones_user_rule_table）。 */
    public static String userRuleTable() {
        return tableOf("oaones_user_rule_table", DEF_USER_RULE_TABLE);
    }

    /** 同步日志表（E9 建模表，配置项 oaones_sync_log_table）。 */
    public static String syncLogTable() {
        return tableOf("oaones_sync_log_table", DEF_SYNC_LOG_TABLE);
    }

    /**
     * 同步日志明细表（E9 建模表，配置项 oaones_sync_log_item_table）。
     *
     * <p>一行一个同步对象（单条部门 / 单条人员），记录该对象本次同步的动作与过程说明。</p>
     */
    public static String syncLogItemTable() {
        return tableOf("oaones_sync_log_item_table", DEF_SYNC_LOG_ITEM_TABLE);
    }

    /**
     * 同步中心“同步设置”表（E9 建模表，配置项 oaones_sync_plan_table）。
     *
     * <p>一行 = 一个“平台 + 租户 + 同步方向”（唯一键 type + cfgid + direction）：
     * 存该方向的计划任务 cron、启停开关，以及“成员没有找到部门”时的处理口径。</p>
     */
    public static String syncPlanTable() {
        return tableOf("oaones_sync_plan_table", DEF_SYNC_PLAN_TABLE);
    }

    /** 部门快照表（E9 建模表，配置项 oaones_dep_tmp_table；corpid + wx_dept_id 唯一）。 */
    public static String deptTmpTable() {
        return tableOf("oaones_dep_tmp_table", DEF_DEP_TMP_TABLE);
    }

    /** 人员快照表（E9 建模表，配置项 oaones_user_tmp_table；corpid + wx_user_id 唯一）。 */
    public static String userTmpTable() {
        return tableOf("oaones_user_tmp_table", DEF_USER_TMP_TABLE);
    }

    /** 企微工作台应用 - OA 页面绑定表（E9 建模表，配置项 oaones_app_bind_table）。 */
    public static String appBindTable() {
        return tableOf("oaones_app_bind_table", DEF_APP_BIND_TABLE);
    }

    /**
     * 应用中心表（E9 建模表，配置项 oaones_app_table）。
     *
     * <p>一行一个第三方应用：应用名称 / 默认访问地址 / 单点登录实现类（实现
     * {@code appsso.AppSsoHandler} 的类全限定名）/ 图标 / 排序 / 启停。
     * 与“应用绑定”表（{@link #appBindTable()}）不同：本表是<b>没有租户维度</b>的独立应用清单。</p>
     */
    public static String appTable() {
        return tableOf("oaones_app_table", DEF_APP_TABLE);
    }

    /**
     * 会议 / 日程同步设置表（E9 建模表，配置项 {@code oaones_mtg_sync_table}）。
     *
     * <p>一行 = 一个「业务（biz 0 会议 / 1 日程）+ 平台（type）+ 租户（cfgid）」，
     * 存该租户该业务的同步开关（{@code enabled}）与同步范围（{@code oa_sub_ids}，多选 OA 分部，含下级）。
     * 会议同步与日程同步是两条独立的一级菜单，但共用这一张设置表，用 {@code biz} 区分。</p>
     */
    public static String mtgSyncTable() {
        return tableOf("oaones_mtg_sync_table", DEF_MTG_SYNC_TABLE);
    }

    /** 会议 / 日程同步记录表（E9 建模表，配置项 {@code oaones_mtg_sync_log_table}）。 */
    public static String mtgSyncLogTable() {
        return tableOf("oaones_mtg_sync_log_table", DEF_MTG_SYNC_LOG_TABLE);
    }

    /**
     * 会议 / 日程同步「个人同步开关」表（E9 建模表，配置项 {@code oaones_mtg_sync_user_table}）。
     *
     * <p>一行 = 一个「业务（biz 0 会议 / 1 日程）+ 平台（type）+ 租户（cfgid）+ OA 人员（oa_user_id）」，
     * 存本人是否愿意把自己相关的会议 / 日程同步到第三方平台（{@code enabled} 1 允许 / 0 不同步）；
     * 没有行 = 默认允许（租户开启同步后默认所有人同步），见 {@code entity.MtgSyncUser}。</p>
     */
    public static String mtgSyncUserTable() {
        return tableOf("oaones_mtg_sync_user_table", DEF_MTG_SYNC_USER_TABLE);
    }

    /**
     * 会议 / 日程同步「对象映射」表（E9 建模表，配置项 {@code oaones_mtg_link_table}）。
     *
     * <p>一行 = 一个「业务 + 平台 + 租户」下的 OA 对象ID 与第三方平台对象ID 的映射
     * （{@code source} 0 = OA→第三方推送建立 / 1 = 第三方→OA 拉取建立），
     * 供拉取判重与推送跳过用，见 {@code entity.MtgSyncLink}。</p>
     */
    public static String mtgLinkTable() {
        return tableOf("oaones_mtg_link_table", DEF_MTG_LINK_TABLE);
    }

    /** 会议 / 日程「第三方→OA」拉取计划表（单行 id = 1，配置项 {@code oaones_mtg_cron_table}）。 */
    public static String mtgCronTable() {
        return tableOf("oaones_mtg_cron_table", DEF_MTG_CRON_TABLE);
    }

    /**
     * 会议 / 日程「计划任务」的**时间段明细**表（配置项 {@code oaones_mtg_plan_item_table}）。
     *
     * <p>计划任务选了“按时间段”（{@link OaonesConst#MTG_PLAN_MODE_ITEM}）时，一行 = 一个时间段
     * （适用日 + 起止时间 + 轮询间隔），可配多条；同一适用日下的时间段不允许重叠。</p>
     */
    public static String mtgPlanItemTable() {
        return tableOf("oaones_mtg_plan_item_table", DEF_MTG_PLAN_ITEM_TABLE);
    }

    /** OA 会议来源表（配置项 {@code mtg.meeting.table}，默认 E9 标准表 {@code Meeting}）。 */
    public static String mtgMeetingTable() {
        return prop("mtg.meeting.table", DEF_MTG_MEETING_TABLE);
    }

    /** OA 会议参与人表（配置项 {@code mtg.meeting.member.table}，默认 {@code Meeting_Member2}）。 */
    public static String mtgMeetingMemberTable() {
        return prop("mtg.meeting.member.table", DEF_MTG_MEETING_MEMBER_TABLE);
    }

    /** OA 日程来源表（配置项 {@code mtg.schedule.table}，默认 E9 标准表 {@code WorkPlan}）。 */
    public static String mtgScheduleTable() {
        return prop("mtg.schedule.table", DEF_MTG_SCHEDULE_TABLE);
    }

    /** OA 日程参与人表（配置项 {@code mtg.schedule.member.table}，默认 {@code workplanMember}）。 */
    public static String mtgScheduleMemberTable() {
        return prop("mtg.schedule.member.table", DEF_MTG_SCHEDULE_MEMBER_TABLE);
    }

    /** 会议 / 日程同步内置调度器是否启用（{@code mtg.sync.enable}，默认 1 = 启用）。 */
    public static boolean mtgSyncSchedulerEnabled() {
        return propInt("mtg.sync.enable", 1) == 1;
    }

    /**
     * 会议 / 日程同步内置调度器的**检查间隔**（秒，{@code mtg.sync.tick.seconds}，默认 60）。
     *
     * <p>调度器每次醒来只做一件事：看页面上配的「计划任务」（白天 / 夜间两套频率，
     * 表 {@code uf_oaones_mtg_cron}）是否到点，到点就跑一轮同步（OA→第三方 推送 +
     * 第三方→OA 拉取）。因此本值只是“看一眼”的粒度，不是同步频率：
     * 计划任务频率下限 5 分钟，这里收敛到 [30, 180] 秒，最多晚 3 分钟发现“到点了”。</p>
     */
    public static int mtgSyncTickSeconds() {
        int seconds = propInt("mtg.sync.tick.seconds", 60);
        if (seconds < 30) {
            return 30;
        }
        return seconds > 180 ? 180 : seconds;
    }

    /** 会议 / 日程同步一次扫描最多处理多少个租户设置（{@code mtg.sync.batch}，默认 50）。 */
    public static int mtgSyncBatch() {
        int batch = propInt("mtg.sync.batch", 50);
        return batch < 1 ? 1 : batch;
    }

    /**
     * 同步时间窗：只同步“开始时间在 [今天 - 前 N 天, 今天 + 后 M 天]”内的会议 / 日程
     * （{@code mtg.sync.window.before.days} / {@code mtg.sync.window.after.days}，默认前 1 天 / 后 30 天），
     * 避免一次把历史全部对象都推一遍。
     */
    public static int mtgSyncWindowBeforeDays() {
        return propInt("mtg.sync.window.before.days", 1);
    }

    /** 同步时间窗向后天数（见 {@link #mtgSyncWindowBeforeDays()}）。 */
    public static int mtgSyncWindowAfterDays() {
        return propInt("mtg.sync.window.after.days", 30);
    }

    /** 一次同步最多处理多少条会议 / 日程（{@code mtg.sync.item.limit}，默认 500）。 */
    public static int mtgSyncItemLimit() {
        int limit = propInt("mtg.sync.item.limit", 500);
        return limit < 1 ? 1 : limit;
    }

    // ---------------- 会议 / 日程同步：第三方向拉取（平台 → OA） ----------------

    /** 第三方向拉取（平台 → OA）是否启用（{@code mtg.pull.enable}，默认 1 = 启用）。 */
    public static boolean mtgPullEnabled() {
        return propInt("mtg.pull.enable", 1) == 1;
    }

    /** 拉取"高频时段"的起始小时（含，{@code mtg.pull.hour.from}，默认 9）。 */
    public static int mtgPullHourFrom() {
        int h = propInt("mtg.pull.hour.from", 9);
        return (h < 0 || h > 23) ? 9 : h;
    }

    /** 拉取"高频时段"的结束小时（不含，{@code mtg.pull.hour.to}，默认 18）。 */
    public static int mtgPullHourTo() {
        int h = propInt("mtg.pull.hour.to", 18);
        return (h < 0 || h > 24) ? 18 : h;
    }

    /**
     * 白天时段（默认 9:00–18:00）的**默认**同步频率分钟数
     * （{@code mtg.pull.hourly.minutes}，默认 60）。
     *
     * <p>实际频率由页面上的「计划任务」决定（存 {@code uf_oaones_mtg_cron}，白天 / 夜间两套 cron）；
     * 本值只在“计划任务还没配过”（表里没有行）时作为白天那一档的默认值。</p>
     */
    public static int mtgPullHourlyMinutes() {
        int m = propInt("mtg.pull.hourly.minutes", 60);
        return m < 5 ? 5 : m;
    }

    /**
     * 夜间时段（其余时段）的**默认**同步频率分钟数
     * （{@code mtg.pull.offhour.minutes}，默认 120）；口径同 {@link #mtgPullHourlyMinutes()}。
     */
    public static int mtgPullOffhourMinutes() {
        int m = propInt("mtg.pull.offhour.minutes", 120);
        return m < 5 ? 5 : m;
    }

    /** 拉取时间窗向后天数（{@code mtg.pull.window.before.days}，默认 1）。 */
    public static int mtgPullWindowBeforeDays() {
        return propInt("mtg.pull.window.before.days", 1);
    }

    /** 拉取时间窗向前天数（{@code mtg.pull.window.after.days}，默认 30）。 */
    public static int mtgPullWindowAfterDays() {
        return propInt("mtg.pull.window.after.days", 30);
    }

    /** 一个租户一次拉取最多处理多少条平台事件（{@code mtg.pull.item.limit}，默认 500）。 */
    public static int mtgPullItemLimit() {
        int limit = propInt("mtg.pull.item.limit", 500);
        return limit < 1 ? 1 : limit;
    }

    /**
     * 一个租户一次拉取最多遍历多少个成员日历（{@code mtg.pull.member.limit}，默认 0 = 不限制）。
     *
     * <p>平台（企微 / 钉钉 / 飞书）都只能“按日历取日程”，因此需要按该租户已绑定的平台成员逐个日历读；
     * 成员很多时可以把这个值调小做限流（超出本轮不拉，下一轮继续），默认不限制以保证“平台新建的日程都能拉回来”。</p>
     */
    public static int mtgPullMemberLimit() {
        int limit = propInt("mtg.pull.member.limit", 0);
        return limit < 0 ? 0 : limit;
    }

    /**
     * 会议 / 日程同步频率说明的**默认**文案（计划任务还没配过时用；配过则由服务端按计划任务生成）。
     *
     * <p>口径：白天（{@code mtg.pull.hour.from}–{@code mtg.pull.hour.to}，默认 9:00–18:00）
     * 用 {@code mtg.pull.hourly.minutes}（默认 60），其余时段用 {@code mtg.pull.offhour.minutes}
     * （默认 120）。同一频率同时作用于“OA→第三方 推送”与“第三方→OA 拉取”两条链路。</p>
     *
     * <p>注意：**页面展示的不是这句话**。2026-10-12 起「计划任务」有两种同步方式
     * （按时间段明细 / 高级 cron）且支持工作日与非工作日，展示口径由
     * {@code MtgSyncServiceImpl#planRuleText}（个人账号中心经 {@code mtgsync.userStates} 的
     * {@code pull_rule_text} 取用）按当前配置实时生成；本方法只在“计划任务读不到 / 还没配过”
     * 时用于兜底说明。</p>
     */
    public static String mtgPullRuleText() {
        int from = mtgPullHourFrom();
        int to = mtgPullHourTo();
        return "同步频率（OA ⇄ 第三方平台）：白天 " + from + ":00–" + to + ":00 "
                + humanMinutes(mtgPullHourlyMinutes()) + "，其余时段 " + humanMinutes(mtgPullOffhourMinutes()) + "。";
    }

    /** 分钟数 → 人话（整小时说“每 N 小时一次”，否则“每 N 分钟一次”）。 */
    public static String humanMinutes(int minutes) {
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1 ? "每小时一次" : ("每 " + hours + " 小时一次");
        }
        return "每 " + minutes + " 分钟一次";
    }

    /**
     * 扫码登录票据表（E9 建模表，配置项 oaones_scan_login_table）。
     *
     * <p>登录页自己生成的二维码、扫码身份命中结果、一次性登录令牌都落在这张表里
     * （见 {@code sql/create_oaones_scan_login.sql}）。</p>
     */
    public static String scanLoginTable() {
        return tableOf("oaones_scan_login_table", DEF_SCAN_LOGIN_TABLE);
    }

    /** 统一登录设置表（E9 建模表，配置项 oaones_login_config_table；只保留一行）。 */
    public static String loginConfigTable() {
        return tableOf("oaones_login_config_table", DEF_LOGIN_CONFIG_TABLE);
    }

    /** 登录短信验证码表（E9 建模表，配置项 oaones_login_sms_table）。 */
    public static String loginSmsTable() {
        return tableOf("oaones_login_sms_table", DEF_LOGIN_SMS_TABLE);
    }

    /** 个人微信授权密钥表（E9 建模表，配置项 oaones_personal_wx_config_table；只保留一行）。 */
    public static String personalWxConfigTable() {
        return tableOf("oaones_personal_wx_config_table", DEF_PERSONAL_WX_CONFIG_TABLE);
    }

    /** 个人微信绑定表（E9 建模表，配置项 oaones_personal_wx_bind_table；一个 OA 人员最多一行）。 */
    public static String personalWxBindTable() {
        return tableOf("oaones_personal_wx_bind_table", DEF_PERSONAL_WX_BIND_TABLE);
    }

    /**
     * 接口日志表（E9 建模表，配置项 {@code oaones_api_log_table}）。
     *
     * <p>一行 = 一次接口调用（OA→第三方平台 的请求 / 第三方平台→OA 的入口调用），
     * 供「接口日志中心」（一级菜单）在一个列表里按 平台 / 租户 / 接口类型 过滤查看。</p>
     */
    public static String apiLogTable() {
        return tableOf("oaones_api_log_table", DEF_API_LOG_TABLE);
    }

    /** 是否开启接口日志（oaones.properties#api.log.enable，默认 1 = 开启）。 */
    public static boolean apiLogEnabled() {
        return propInt("api.log.enable", 1) == 1;
    }

    /**
     * 旧接口日志保留天数配置（oaones.properties#api.log.keep.days，默认 0；0 = 永久保留）。
     *
     * <p>现由「系统设置 → 日志保留」统一维护；本方法仅为旧部署兼容保留。</p>
     */
    public static int apiLogKeepDays() {
        return propInt("api.log.keep.days", 0);
    }

    /**
     * 「接口日志中心」的维护人（逗号分隔的 OA 人员ID）。
     *
     * <p>除系统管理员（user.id=1）外，只有这里列出的人员能查看 / 清理全部租户的日志；
     * 普通登录人只看得到本人可维护租户的日志（与同步日志同一分权口径）。留空表示
     * 仅系统管理员可查看全部（普通登录人仍可查看本人维护租户的日志）。</p>
     */
    public static String apiLogAdminIds() {
        return prop("apilog.admin.ids", "");
    }

    /**
     * 操作监控日志表（E9 建模表，配置项 {@code oaones_op_log_table}）。
     *
     * <p>一行 = 一次“登录 / 退出 / 菜单访问”，供「操作监控平台」（一级菜单）
     * 回答“某某人什么时间登录、访问了哪个菜单”。</p>
     */
    public static String opLogTable() {
        return tableOf("oaones_op_log_table", DEF_OP_LOG_TABLE);
    }

    /** 日志保留设置表（配置项 {@code oaones_log_retention_table}）。 */
    public static String logRetentionTable() {
        return tableOf("oaones_log_retention_table", DEF_LOG_RETENTION_TABLE);
    }

    /**
     * 是否采集操作监控（oaones.properties#op.log.enable，默认 1 = 开启）。
     *
     * <p>关闭后过滤器不再写任何操作日志（已落库的数据不受影响），
     * 页面顶部会给出提示；改动最多 1 分钟生效（采集端按 60 秒缓存）。</p>
     */
    public static boolean opLogEnabled() {
        return propInt("op.log.enable", 1) == 1;
    }

    /**
     * 旧操作监控日志保留天数配置（oaones.properties#op.log.keep.days，默认 0；0 = 永久保留）。
     *
     * <p>现由「系统设置 → 日志保留」统一维护；本方法仅为旧部署兼容保留。</p>
     */
    public static int opLogKeepDays() {
        return propInt("op.log.keep.days", 0);
    }

    /**
     * 菜单访问去重窗口（oaones.properties#op.log.dedupe.seconds，默认 60 秒之一行；0 = 不去重）。
     *
     * <p>同一会话在窗口内重复打开同一个菜单只记一条：页面里的 iframe、刷新、
     * 重定向都会在很短时间内重复请求同一个菜单地址，不做去重会产生大量重复记录。</p>
     */
    public static int opLogDedupeSeconds() {
        int seconds = propInt("op.log.dedupe.seconds", 1);
        return seconds < 0 ? 0 : seconds;
    }

    /**
     * 「操作监控平台」的查看人（逗号分隔的 OA 人员ID，oaones.properties#opmonitor.admin.ids）。
     *
     * <p>本页是“全站的操作留痕”，只对系统管理员（user.id=1）与这里列出的人员开放；
     * 留空表示仅系统管理员可查看。</p>
     */
    public static String opMonitorAdminIds() {
        return prop("opmonitor.admin.ids", "");
    }

    /** 企业微信开放接口网关。 */
    public static String wxApiBase() {
        return prop("wx.api.base", "https://qyapi.weixin.qq.com");
    }

    /** 企业微信扫码授权（网页授权登录）网关：内嵌二维码页面所在域名。 */
    public static String wxQrBase() {
        return prop("wx.qr.base", "https://open.work.weixin.qq.com");
    }

    /**
     * 扫码授权的页面路径。
     *
     * <p>默认是自建应用经典的扫码登录地址；企业微信后续推出的
     * `login.work.weixin.qq.com/wwlogin/sso/login` 只需改这里（并配 wx.qr.extra_params），
     * 不需要改 Java 代码。</p>
     */
    public static String wxQrPath() {
        return prop("wx.qr.path", "/wwopen/sso/qrConnect");
    }

    /**
     * 扫码授权地址的附加查询串（形如 {@code login_type=CorpApp}，多个用 &amp; 连接）。
     *
     * <p>按原样追加在 appid/agentid/redirect_uri/state 之后，不做二次编码，
     * 因此配置时请自行保证已正确编码；默认留空。</p>
     */
    public static String wxQrExtraParams() {
        return prop("wx.qr.extra_params", "");
    }

    /**
     * 个人账号中心扫码绑定的回调地址前缀（redirect_uri 的协议 + 域名 + 端口部分，不含路径）。
     *
     * <p>留空表示按浏览器当前访问的域名自动拼接；OA 部署在反向代理/多域名之后时，
     * 在这里显式配置企业微信后台“可信域名”对应的地址，避免回调落到代理内部地址上。</p>
     */
    public static String accountCallbackBase() {
        return prop("account.callback.base", "");
    }

    /** 个人账号中心扫码 state 令牌有效期（毫秒），默认 15 分钟。 */
    public static int accountScanStateTtl() {
        return Integer.parseInt(prop("account.qr.state.ttl", "900000"));
    }


    /* ---------------- 个人微信（与企微 / 钉钉 / 飞书并列的第四个平台，但没有“租户”概念） ----------------
       个人微信绑定只用**一份**微信授权密钥（AppID + AppSecret，维护在「微信设置」页 /
       uf_oaones_personal_wx_config），因此下面这些参数都是全局单份，不按 cfg_id 区分。 */

    /**
     * 个人微信“换授权令牌 / 读用户信息”的接口网关。
     *
     * <p>默认微信开放平台的接口域名；扫码授权换回来的 code 在这里换成
     * {@code openid} / {@code unionid} 与昵称头像。换网关只改配置，不改 Java。</p>
     */
    public static String pwApiBase() {
        return prop("pw.api.base", "https://api.weixin.qq.com");
    }

    /** 个人微信扫码授权的网关（内嵌二维码页面所在域名）。 */
    public static String pwOauthBase() {
        return prop("pw.oauth.base", "https://open.weixin.qq.com");
    }

    /**
     * 个人微信扫码授权的页面路径。
     *
     * <p>默认微信开放平台“网站应用”的扫码登录地址（{@code scope=snsapi_login}，可用个人微信扫码）；
     * 换公众号网页授权等其它入口时只改这里与 {@link #pwOauthScope()}，不改 Java 代码。</p>
     */
    public static String pwOauthPath() {
        return prop("pw.oauth.path", "/connect/qrconnect");
    }

    /** 个人微信扫码授权申请的 scope（默认 {@code snsapi_login}）。 */
    public static String pwOauthScope() {
        return prop("pw.oauth.scope", "snsapi_login");
    }

    /**
     * 个人微信扫码授权地址的附加查询串（形如 {@code lang=zh_CN}，多个用 &amp; 连接）。
     *
     * <p>按原样追加在 appid/redirect_uri/response_type/scope/state 之后，不做二次编码，
     * 因此配置时请自行保证已正确编码；默认留空。</p>
     */
    public static String pwOauthExtraParams() {
        return prop("pw.oauth.extra_params", "");
    }

    /**
     * 个人微信扫码绑定回调地址前缀（协议 + 域名 + 端口，不含路径）。
     *
     * <p>留空表示按浏览器当前访问的域名自动拼接；反向代理 / 多域名部署时必须写成
     * 微信开放平台该应用登记过的“授权回调域”对应的地址。</p>
     */
    public static String pwCallbackBase() {
        return prop("pw.bind.callback.base", "");
    }

    /** 个人微信扫码绑定回调页的站内路径（redirect_uri 的路径部分）。 */
    public static String pwCallbackPath() {
        return prop("pw.bind.callback.path", "/oaones/personalWx/PersonalWxCallback.jsp");
    }

    /** 个人微信扫码 state 令牌有效期（毫秒），默认 10 分钟。 */
    public static int pwScanStateTtl() {
        return propInt("pw.bind.state.ttl", 600000);
    }

    /**
     * 个人微信设置的**维护人**（逗号分隔的 OA 人员ID）。
     *
     * <p>除系统管理员（user.id=1）外，只有这里列出的人员能查看 / 修改微信授权密钥、
     * 能解绑他人的个人微信绑定（与登录设置 `login.admin.ids`、消息中心 `msg.admin.ids` 同一口径）；
     * 留空表示仅系统管理员可维护。</p>
     */
    public static String pwAdminIds() {
        return prop("pw.admin.ids", "");
    }

    /**
     * 「应用中心」的维护人：除系统管理员（user.id=1）外，只有这里列出的人员（逗号分隔的 OA 人员ID）
     * 能查看/修改应用列表；留空表示仅系统管理员可维护。
     */
    public static String appcAdminIds() {
        return prop("appc.admin.ids", "");
    }

    /**
     * 全局超级管理员的**角色ID**（oaones.properties#total.admin.roleid，单个 E9 角色ID）。
     *
     * <p>该角色内的人员与系统管理员（user.id=1）权限完全一致：OAones 全部页面的全部管理权限
     * （所有平台 / 所有租户、含未指定维护人的租户）。留空或 0 = 不启用（只有 user.id=1 是超管）。</p>
     *
     * <p>角色成员的判定不在这里做（配置项只管“是哪个角色”），见
     * {@link OaonesAdmin#isTotalAdmin(String)}。</p>
     */
    public static int totalAdminRoleId() {
        return propInt("total.admin.roleid", 0);
    }

    /**
     * OA 分权：「分部」候选对应的 E9 分权权限点（默认 {@code Subcompanys:decentralization}）。
     *
     * <p>OA 开了分权时，OAones 里“选择 OA 分部 / 部门 / 人员”的候选按登录人的分权范围过滤，
     * 与 OA 平台自己的分权选择框同一口径；三类候选各用哪个权限点在这里配置，
     * 平台权限点改名/换口径时只改配置、不改 Java（见 {@link OaonesHrmScope}）。</p>
     */
    public static String secRightSub() {
        return prop("oa.sec.right.sub", DEF_SEC_RIGHT_SUB);
    }

    /** OA 分权：「部门」候选对应的 E9 分权权限点（默认 {@code Departments:decentralization}）。 */
    public static String secRightDept() {
        return prop("oa.sec.right.dept", DEF_SEC_RIGHT_DEPT);
    }

    /** OA 分权：「人员」候选对应的 E9 分权权限点（默认 {@code Resources:decentralization}）。 */
    public static String secRightUser() {
        return prop("oa.sec.right.user", DEF_SEC_RIGHT_USER);
    }

    /**
     * OA 分权开关：{@code 1}=强制按分权过滤 / {@code 0}=强制不过滤 / 留空或其它=跟随 OA 系统设置
     * （{@code SystemSet.detachable} 或权限点自身的分权开关）。
     */
    public static String secForce() {
        return prop("oa.sec.force", "");
    }

    /**
     * 企微工作台应用免登录入口的地址前缀（协议 + 域名 + 端口，不含路径）。
     *
     * <p>留空表示按浏览器当前访问的域名自动拼接；此地址要填进企业微信后台
     * 该自建应用的“应用主页”，因此必须与企业微信后台配置的“可信域名”一致
     * （反向代理/多域名部署时请在properties里显式指定）。</p>
     */
    public static String appEntryBase() {
        return prop("app.entry.base", "");
    }

    /** 企微工作台应用免登录入口的页面路径。 */
    public static String appEntryPath() {
        return prop("app.entry.path", "/oaones/appBind/WeCom/appEntry.jsp");
    }

    /** 企业微信网页授权（OAuth2）网关：入口缺少 code 时跳转到这里换取授权码。 */
    public static String appOauthBase() {
        return prop("app.oauth.base", "https://open.weixin.qq.com");
    }

    /** 企业微信网页授权（OAuth2）授权页路径。 */
    public static String appOauthPath() {
        return prop("app.oauth.path", "/connect/oauth2/authorize");
    }

    /* ------------------------------------------------------------------
     * 钉钉连接器（DingTalk）：与企微同名的一套配置项，前缀用 dd.
     * 钉钉与企微共用同一批业务表（见 docs/oaones_model_tables.md 的“平台类型”），
     * 因此这里只有**接口地址/入口地址**这类真正随平台变化的东西。
     * ------------------------------------------------------------------ */

    /** 钉钉服务端接口网关（gettoken / topapi 等老接口）。 */
    public static String ddApiBase() {
        return prop("dd.api.base", "https://oapi.dingtalk.com");
    }

    /** 钉钉新版开放接口网关（OAuth2 用户令牌、contact/users/me 等）。 */
    public static String ddOpenApiBase() {
        return prop("dd.open.api.base", "https://api.dingtalk.com");
    }

    /**
     * 钉钉扫码登录/网页授权的授权网关。
     *
     * <p>自建应用扫码登录走 {@code login.dingtalk.com/oauth2/auth}（等同钉钉文档里的
     * “获取登录用户的访问凭证”第一步）；换网关只改这里与 {@link #ddLoginAuthPath()}，不改 Java。</p>
     */
    public static String ddLoginBase() {
        return prop("dd.login.base", "https://login.dingtalk.com");
    }

    /** 钉钉扫码登录/网页授权的授权页路径。 */
    public static String ddLoginAuthPath() {
        return prop("dd.login.auth.path", "/oauth2/auth");
    }

    /** 钉钉扫码登录授权申请的 scope（钉钉固定 openid，可配成 openid corpid 之类）。 */
    public static String ddLoginScope() {
        return prop("dd.login.scope", "openid");
    }

    /**
     * 钉钉扫码的授权回跳地址（钉钉网页授权 redirect_uri；须在钉钉后台登记的回调域名下）。
     *
     * <p>二维码内容统一指向 {@link #loginScanPath()}（统一扫码落地页），
     * 本路径只用于“钉钉授权完成后回到本站”，页面本体只做 302 跳回统一落地页。</p>
     */
    public static String ddLoginScanPath() {
        return prop("dd.login.scan.path", "/oaones/login/DingTalk/scan.jsp");
    }

    /** 钉钉自定义登录页默认使用的租户配置ID（留空时按可用租户顺序探测/让员工选）。 */
    public static String ddLoginCfgId() {
        return prop("dd.login.cfg_id", "");
    }

    /**
     * 钉钉工作台应用免登录入口的地址前缀（协议 + 域名 + 端口，不含路径）。
     *
     * <p>留空时按浏览器当前访问地址自动拼接；该地址要填进钉钉后台“应用主页”，
     * 钉钉会在后面追加 {@code code}。</p>
     */
    public static String ddAppEntryBase() {
        return prop("dd.app.entry.base", "");
    }

    /** 钉钉工作台应用免登录入口的页面路径。 */
    public static String ddAppEntryPath() {
        return prop("dd.app.entry.path", "/oaones/appBind/DingTalk/appEntry.jsp");
    }

    /** 钉钉个人账号中心扫码绑定回调地址前缀（协议+域名+端口，不含路径）。 */
    public static String ddAccountCallbackBase() {
        return prop("dd.account.callback.base", "");
    }

    /* ------------------------------------------------------------------
     * 飞书连接器（Feishu / 海外版 Lark）：与企微、钉钉同名的一套配置项，前缀用 fs.
     *
     * 飞书与企微、钉钉共用同一批业务表（见 docs/oaones_model_tables.md 的“平台类型”），
     * 因此这里只有**接口地址/入口地址/清单ID**这类真正随平台变化的东西：
     * 飞书开放平台只有一个网关（open.feishu.cn；海外版 open.larksuite.com），
     * 通讯录、网页授权、消息推送都在它下面，所以不像钉钉那样分“老接口/新接口”两个网关。
     * ------------------------------------------------------------------ */

    /** 飞书开放平台网关（tenant_access_token/internal、contact/v3、authen/v1、im/v1、task/v2 都在它下面）。 */
    public static String fsApiBase() {
        return prop("fs.api.base", "https://open.feishu.cn");
    }

    /**
     * 飞书网页授权网关。

     * <p>扫码登录 / 工作台免登 / 个人账号中心扫码绑定都走
     * {@code {fs.login.base}{fs.login.auth.path}}（飞书“网页应用登录授权”）；
     * 海外版 Lark 换 {@code https://open.larksuite.com} 只改这一项。</p>
     */
    public static String fsLoginBase() {
        return prop("fs.login.base", "https://open.feishu.cn");
    }

    /** 飞书网页授权（授权页）路径；自建应用的“重定向URL”必须在飞书后台登记该地址。 */
    public static String fsLoginAuthPath() {
        return prop("fs.login.auth.path", "/open-apis/authen/v1/authorize");
    }

    /**
     * 飞书扫码的授权回跳地址（飞书网页授权 redirect_uri，飞书要求与后台登记的“重定向URL”精确匹配）。
     *
     * <p>二维码内容统一指向 {@link #loginScanPath()}（统一扫码落地页），
     * 本路径只用于“飞书授权完成后回到本站”，页面本体只做 302 跳回统一落地页——
     * 这样飞书后台已登记的旧地址不用改。</p>
     */
    public static String fsLoginScanPath() {
        return prop("fs.login.scan.path", "/oaones/login/Feishu/scan.jsp");
    }

    /** 飞书自定义登录页默认使用的租户配置ID（留空时按可用租户顺序探测/让员工选）。 */
    public static String fsLoginCfgId() {
        return prop("fs.login.cfg_id", "");
    }

    /**
     * 飞书工作台应用免登录入口的地址前缀（协议 + 域名 + 端口，不含路径）。
     *
     * <p>留空时按浏览器当前访问地址自动拼接；该地址要填进飞书后台该应用的“网页应用主页”，
     * 飞书客户端打开时由 OA 侧跳一次飞书网页授权（静默）换回成员身份。</p>
     */
    public static String fsAppEntryBase() {
        return prop("fs.app.entry.base", "");
    }

    /** 飞书工作台应用免登录入口的页面路径。 */
    public static String fsAppEntryPath() {
        return prop("fs.app.entry.path", "/oaones/appBind/Feishu/appEntry.jsp");
    }

    /** 飞书个人账号中心扫码绑定回调地址前缀（协议+域名+端口，不含路径）。 */
    public static String fsAccountCallbackBase() {
        return prop("fs.account.callback.base", "");
    }

    /**
     * 飞书「待办」挂靠的任务清单GUID（可选）。
     *
     * <p>消息形态 {@code task_card} 推送时，飞书待办走任务（Task）接口：先建任务、
     * 再把接收人加成任务成员；配了本项就把任务同时挂到该清单下，未配只建任务。</p>
     */
    public static String fsTaskListGuid() {
        return prop("fs.task.tasklist.guid", "");
    }

    /* ---------------- 飞书连接器的计划任务参数 ----------------
       与企微、钉钉分开配置：三个连接器的计划任务各自跑各自的租户，
       避免共用一个“指定租户ID”时互相把对方过滤掉（租户ID在各平台不通用）。 */

    /** 飞书计划任务默认同步方向：0=双向，1=OA->飞书，2=飞书->OA；未配置时沿用 cron.direction。 */
    public static int fsCronDirection() {
        return propInt("fs.cron.direction", cronDirection());
    }

    /** 飞书计划任务指定租户配置ID，0 表示全部启用的飞书租户。 */
    public static int fsCronCfgId() {
        return propInt("fs.cron.cfg_id", 0);
    }

    /**
     * 自定义登录页扫码登录默认使用的企微租户配置ID。
     *
     * <p>留空时：只有一个可用租户就用它，多个可用租户则由员工在登录页上自己选
     * （登录页没有登录人，无法按维护人过滤，见 {@code LoginService#tenants}）。</p>
     */
    public static String loginQywxCfgId() {
        return prop("login.qywx.cfg_id", "");
    }

    /**
     * 自建扫码登录二维码的有效期（毫秒），默认 2 分钟（120 秒）。
     *
     * <p>票据存 {@code uf_oaones_scan_login}：PC 轮询用它判断过期，
     * 过期后必须刷新二维码（登录二维码比绑定的二维码更敏感，有效期取更短）。
     * 本项只是“登录设置”页有效期留空时的默认值：设置页按秒维护、最长 180 秒，优先于这里。</p>
     */
    public static int loginQrTtl() {
        return propInt("login.qr.ttl", 120000);
    }

    /** PC 轮询间隔（毫秒），默认 1.5 秒；页面按它 setInterval。 */
    public static int loginQrPollMs() {
        return propInt("login.qr.poll.ms", 1500);
    }

    /**
     * 扫码后是否需要扫码人在手机上点“确认登录”：1=需要，0=扫码即登录（默认）。
     *
     * <p>统一登录页不再做手机端二次确认：扫码授权成功即签发一次性登录令牌，
     * 电脑端轮询到结果后直接进入工作台。保留该开关只为兼容旧配置与历史调用。</p>
     */
    public static int loginQrConfirm() {
        return propInt("login.qr.confirm", 0);
    }

    /** 过期票据保留小时数（新建二维码时顺手清理更早的记录，默认 24 小时）。 */
    public static int loginQrKeepHours() {
        return propInt("login.qr.keep.hours", 24);
    }

    /**
     * 统一扫码落地页路径（二维码内容 = 站点地址 + 本路径 + ?t=票据；企业微信的网页授权回调地址也用这一项）。
     *
     * <p>钉钉 / 飞书的回跳地址仍取 {@link #ddLoginScanPath()} / {@link #fsLoginScanPath()}
     * （两边都要在平台后台登记对应地址），那两个页面只做 302 跳到本路径，
     * 因此三个平台的二维码都是同一张、落地页也是同一个。</p>
     */
    public static String loginScanPath() {
        return prop("login.scan.path", "/oaones/login/scan.jsp");
    }

    /**
     * 登录成功后的默认落地页（未指定 gopage 时用，`#` 之后的页面参数要整段保留，
     * 见 {@link OaonesSso#internalPath}）。
     *
     * <p>默认落工作台对外地址 `/oaones/desktop`（路由到应用中心桌面启动台，不再进系统首页）；
     * 想回系统首页把 {@code login.landing.url} 配成 `/wui/index.html#/main` 即可。</p>
     */
    public static String loginLandingUrl() {
        return prop("login.landing.url", "/oaones/desktop");
    }

    /**
     * 桌面启动台带 gopage 时，进入页面后自动跳转的秒数。
     *
     * <p>配置项 {@code login.landing.gopage.seconds}，默认 5 秒；
     * 配 0 表示不自动跳转（桌面页只展示启动台）。</p>
     */
    public static int loginLandingGopageSeconds() {
        return Math.max(0, propInt("login.landing.gopage.seconds", 5));
    }

    /* ---------- 手机号登录（本模块自管的短信验证码；短信发送走平台标准能力） ---------- */

    /** 验证码有效期（秒），默认 300（5 分钟）。 */
    public static int loginSmsTtl() {
        return propInt("login.sms.ttl", 300);
    }

    /** 同一手机号两次发送的最小间隔（秒），默认 60。 */
    public static int loginSmsCooldown() {
        return propInt("login.sms.cooldown", 60);
    }

    /** 同一手机号每天最多发送次数，默认 10。 */
    public static int loginSmsDailyMax() {
        return propInt("login.sms.daily.max", 10);
    }

    /** 同一验证码最多允许输错次数，默认 5。 */
    public static int loginSmsMaxWrong() {
        return propInt("login.sms.max.wrong", 5);
    }

    /** 验证码位数（4~8），默认 6。 */
    public static int loginSmsLength() {
        return propInt("login.sms.length", 6);
    }

    /** 过期验证码记录保留小时数（发送新验证码时顺手清理），默认 24。 */
    public static int loginSmsKeepHours() {
        return propInt("login.sms.keep.hours", 24);
    }

    /**
     * 短信正文模板（`{code}` 替换成验证码、`{minutes}` 替换成有效分钟数）。
     *
     * <p>这是**值里带中文**的配置：文件按 GBK 存，平台 properties 读法是 ISO-8859-1，
     * {@link #prop} 会按 GBK 把值还原回来（见 #decodePropValue），所以值里直接写中文即可。
     * 平台会按后台“短信设置”自动加签名。</p>
     */
    public static String loginSmsTemplate() {
        return prop("login.sms.template", "您的登录验证码为 {code}，{minutes} 分钟内有效，请勿转发他人。");
    }

    /**
     * 登录验证码邮件的**主题**（`login.email.subject`）。
     *
     * <p>值里带中文：本文件按 GBK 存、{@link #prop} 会按 GBK 还原（见 #decodePropValue）。</p>
     */
    public static String loginEmailSubject() {
        return prop("login.email.subject", "登录验证码");
    }

    /**
     * 登录验证码邮件的**正文模板**（`login.email.template`，`{code}` 验证码、`{minutes}` 有效分钟数）。
     *
     * <p>与短信模板同一口径：值里带中文，文件按 GBK 存、{@link #prop} 按 GBK 还原；
     * 邮件按平台“邮件提醒”同一条通道发出（发件账号取后台“邮件设置”的群发邮箱）。</p>
     */
    public static String loginEmailTemplate() {
        return prop("login.email.template",
                "您的登录验证码为 {code}，{minutes} 分钟内有效，请勿转发他人。");
    }

    /**
     * 兜底用的“短信服务类”全限定名（可留空）。
     *
     * <p>默认走平台标准能力 {@code weaver.sms.SMSManager}（后台“短信设置”里的通道）；
     * 当本环境没有启用平台短信服务、但仍有用自定义通道在发短信时，
     * 在这里填一个**实现了平台接口 {@code weaver.sms.SmsService}** 的类，
     * 由本模块反射调用（只依赖平台接口，编译期不引用任何其它定制包）。</p>
     */
    public static String loginSmsServiceClass() {
        return prop("login.sms.service.class", "");
    }

    /** 登录页标题（左侧品牌区与浏览器标题；换单位名/项目名只改这一项）。 */
    public static String loginPageTitle() {
        // 值里带中文：本文件按 GBK 存，prop() 会按 GBK 还原（见 decodePropValue 的说明），
        // 否则登录页的浏览器标题与页面主标题会显示成乱码
        return prop("login.page.title", "OA 协同办公系统");
    }

    /**
     * 统一登录设置的**维护人**（逗号分隔的 OA 人员ID）。
     *
     * <p>除系统管理员（user.id=1）外，只有这里列出的人员能查看/修改登录设置
     * （与消息中心的 `msg.admin.ids` 同一口径）；留空表示仅系统管理员可维护。</p>
     */
    public static String loginAdminIds() {
        return prop("login.admin.ids", "");
    }


    /** 一次批量拉取的超时毫秒数。 */
    public static int connectTimeout() {
        return Integer.parseInt(prop("http.timeout", "30000"));
    }

    /** 后台计划任务默认同步方向：0=双向，1=OA->企微，2=企微->OA。 */
    public static int cronDirection() {
        return Integer.parseInt(prop("cron.direction", "0"));
    }

    /** 后台计划任务指定企微配置ID，0 表示全部启用租户。 */
    public static int cronCorpId() {
        return Integer.parseInt(prop("cron.corpid", "0"));
    }

    /* ---------------- 钉钉连接器的计划任务参数 ----------------
       与企微分开配置：两个连接器的计划任务各自跑各自的租户，
       避免共用一个“指定租户ID”时互相把对方过滤掉（租户ID在两个平台不通用）。 */

    /** 钉钉计划任务默认同步方向：0=双向，1=OA->钉钉，2=钉钉->OA；未配置时沿用 cron.direction。 */
    public static int ddCronDirection() {
        return propInt("dd.cron.direction", cronDirection());
    }

    /** 钉钉计划任务指定租户配置ID，0 表示全部启用的钉钉租户。 */
    public static int ddCronCfgId() {
        return propInt("dd.cron.cfg_id", 0);
    }

    // ===== 建模模块ID：不配置，按上面对应的表名反查（见 WeaverUtil#getModeIdByTableName） =====
    //
    // 用途只有两处：写入数据行的 formmodeid、按建模共享规则重构单条数据权限（OaonesModeRight）。
    // 反查不到（表还没建成建模表 / 库不可用）返回 0，调用方跳过这两件事，
    // 绑定、同步、登录等本身照常进行。

    /** 企微租户配置建模模块ID（按 {@link #cfgTable()} 反查）。 */
    public static int cfgModeId() {
        return WeaverUtil.getModeIdByTableName(cfgTable());
    }

    /** 部门绑定建模模块ID（按 {@link #deptBindTable()} 反查）。 */
    public static int deptBindModeId() {
        return WeaverUtil.getModeIdByTableName(deptBindTable());
    }

    /** OA 分部 - 平台租户映射建模模块ID（按 {@link #subTenantMapTable()} 反查）。 */
    public static int subTenantMapModeId() {
        return WeaverUtil.getModeIdByTableName(subTenantMapTable());
    }

    /** 人员绑定建模模块ID（按 {@link #userBindTable()} 反查）。 */
    public static int userBindModeId() {
        return WeaverUtil.getModeIdByTableName(userBindTable());
    }

    /** 同步日志建模模块ID（按 {@link #syncLogTable()} 反查）。 */
    public static int syncLogModeId() {
        return WeaverUtil.getModeIdByTableName(syncLogTable());
    }

    /** 同步日志明细建模模块ID（按 {@link #syncLogItemTable()} 反查）。 */
    public static int syncLogItemModeId() {
        return WeaverUtil.getModeIdByTableName(syncLogItemTable());
    }

    /** 内置同步调度器是否启用（oaones.properties#sync.plan.enable，默认 1 = 启用）。 */
    public static boolean syncPlanSchedulerEnabled() {
        return propInt("sync.plan.enable", 1) == 1;
    }

    /** 内置同步调度器的扫描间隔（秒，oaones.properties#sync.plan.tick.seconds，默认 30，最小 10）。 */
    public static int syncPlanTickSeconds() {
        int seconds = propInt("sync.plan.tick.seconds", 30);
        return seconds < 10 ? 10 : seconds;
    }

    /** 内置同步调度器一次扫描最多执行的设置条数（oaones.properties#sync.plan.batch，默认 20）。 */
    public static int syncPlanBatch() {
        int batch = propInt("sync.plan.batch", 20);
        return batch < 1 ? 1 : batch;
    }

    /** 同步设置建模模块ID（按 {@link syncPlanTable()} 反查）。 */
    public static int syncPlanModeId() {
        return WeaverUtil.getModeIdByTableName(syncPlanTable());
    }

    /** 部门快照建模模块ID（按 {@link #deptTmpTable()} 反查）。 */
    public static int deptTmpModeId() {
        return WeaverUtil.getModeIdByTableName(deptTmpTable());
    }

    /** 默认人员绑定规则建模模块ID（按 {@link #userRuleTable()} 反查）。 */
    public static int userRuleModeId() {
        return WeaverUtil.getModeIdByTableName(userRuleTable());
    }

    /** 人员快照建模模块ID（按 {@link #userTmpTable()} 反查）。 */
    public static int userTmpModeId() {
        return WeaverUtil.getModeIdByTableName(userTmpTable());
    }

    /** 企微应用绑定建模模块ID（按 {@link #appBindTable()} 反查）。 */
    public static int appBindModeId() {
        return WeaverUtil.getModeIdByTableName(appBindTable());
    }

    /** 应用中心建模模块ID（按 {@link #appTable()} 反查）。 */
    public static int appModeId() {
        return WeaverUtil.getModeIdByTableName(appTable());
    }

    /** 接口日志建模模块ID（按 {@link #apiLogTable()} 反查）。 */
    public static int apiLogModeId() {
        return WeaverUtil.getModeIdByTableName(apiLogTable());
    }

    /** 操作监控日志建模模块ID（按 {@link #opLogTable()} 反查）。 */
    public static int opLogModeId() {
        return WeaverUtil.getModeIdByTableName(opLogTable());
    }

    /** 会议 / 日程同步设置建模模块ID（按 {@link #mtgSyncTable()} 反查）。 */
    public static int mtgSyncModeId() {
        return WeaverUtil.getModeIdByTableName(mtgSyncTable());
    }

    /** 会议 / 日程同步记录建模模块ID（按 {@link #mtgSyncLogTable()} 反查）。 */
    public static int mtgSyncLogModeId() {
        return WeaverUtil.getModeIdByTableName(mtgSyncLogTable());
    }

    /** 会议 / 日程同步「个人同步开关」建模模块ID（按 {@link #mtgSyncUserTable()} 反查）。 */
    public static int mtgSyncUserModeId() {
        return WeaverUtil.getModeIdByTableName(mtgSyncUserTable());
    }

    /** 会议 / 日程同步「对象映射」建模模块ID（按 {@link #mtgLinkTable()} 反查）。 */
    public static int mtgLinkModeId() {
        return WeaverUtil.getModeIdByTableName(mtgLinkTable());
    }

    /** 扫码登录票据建模模块ID（按 {@link #scanLoginTable()} 反查）。 */
    public static int scanLoginModeId() {
        return WeaverUtil.getModeIdByTableName(scanLoginTable());
    }

    /** 统一登录设置建模模块ID（按 {@link #loginConfigTable()} 反查）。 */
    public static int loginConfigModeId() {
        return WeaverUtil.getModeIdByTableName(loginConfigTable());
    }

    /** 登录短信验证码建模模块ID（按 {@link #loginSmsTable()} 反查）。 */
    public static int loginSmsModeId() {
        return WeaverUtil.getModeIdByTableName(loginSmsTable());
    }

    /** 个人微信授权密钥建模模块ID（按 {@link #personalWxConfigTable()} 反查）。 */
    public static int personalWxConfigModeId() {
        return WeaverUtil.getModeIdByTableName(personalWxConfigTable());
    }

    /** 个人微信绑定建模模块ID（按 {@link #personalWxBindTable()} 反查）。 */
    public static int personalWxBindModeId() {
        return WeaverUtil.getModeIdByTableName(personalWxBindTable());
    }

    // ==================================================================
    // 考勤同步（独立一级菜单「考勤同步」，页面 /oaones/attSync/attSync.jsp）
    // ==================================================================

    /**
     * 考勤同步设置表（E9 建模表，配置项 {@code oaones_att_cfg_table}）。
     *
     * <p>一行 = 一个「平台（type）+ 租户（cfgid）+ 方向（dir）」（同一平台租户同一方向**可以有多条**，
     * 例如不同范围 / 不同数据类型 / 不同频率），另有一类 {@code row_type = 1} 的行作为
     * “平台 + 租户”整级的启停开关（需求 3.10.3）。</p>
     */
    public static String attCfgTable() {
        return tableOf("oaones_att_cfg_table", DEF_ATT_CFG_TABLE);
    }

    /** 考勤同步「计划任务」时间段明细表（配置项 {@code oaones_att_plan_item_table}）。 */
    public static String attPlanItemTable() {
        return tableOf("oaones_att_plan_item_table", DEF_ATT_PLAN_ITEM_TABLE);
    }

    /** 考勤同步批次表（配置项 {@code oaones_att_log_table}）。 */
    public static String attLogTable() {
        return tableOf("oaones_att_log_table", DEF_ATT_LOG_TABLE);
    }

    /** 考勤同步打卡明细表（配置项 {@code oaones_att_log_item_table}）。 */
    public static String attLogItemTable() {
        return tableOf("oaones_att_log_item_table", DEF_ATT_LOG_ITEM_TABLE);
    }

    /** 考勤同步「平台记录 ⇄ OA 打卡记录」映射表（配置项 {@code oaones_att_link_table}）。 */
    public static String attLinkTable() {
        return tableOf("oaones_att_link_table", DEF_ATT_LINK_TABLE);
    }

    /** 正向同步的写入目标表（配置项 {@code att.sign.table}，默认 E9 标准表 {@code HrmScheduleSign}）。 */
    public static String attSignTable() {
        return prop("att.sign.table", DEF_ATT_SIGN_TABLE);
    }

    /** 考勤同步功能**总开关**（{@code att.sync.enable}，默认 1）：关闭后不自动同步，手工执行也禁用。 */
    public static boolean attSyncEnabled() {
        return propInt("att.sync.enable", 1) == 1;
    }

    /**
     * 考勤同步内置调度器的检查间隔（秒，{@code att.sync.tick.seconds}，默认 60，收敛到 [30, 180]）。
     *
     * <p>与会议 / 日程同步同一口径：调度器每次醒来只看“有没有设置的计划任务到点”，
     * 到点跑一轮；频率本身由每条设置自己的计划任务决定（下限 5 分钟）。</p>
     */
    public static int attSyncTickSeconds() {
        int seconds = propInt("att.sync.tick.seconds", 60);
        if (seconds < 30) {
            return 30;
        }
        return seconds > 180 ? 180 : seconds;
    }

    /** 一轮调度最多处理多少条启用中的设置（{@code att.sync.batch}，默认 20）。 */
    public static int attSyncBatch() {
        int batch = propInt("att.sync.batch", 20);
        return batch < 1 ? 1 : batch;
    }

    /** 单次执行**最多处理多少条打卡**（{@code att.sync.item.limit}，默认 2000；超限记日志、下一轮接着跑）。 */
    public static int attSyncItemLimit() {
        int limit = propInt("att.sync.item.limit", 2000);
        return limit < 1 ? 1 : limit;
    }

    /** 单次执行**最多调用多少次平台接口**（{@code att.sync.api.limit}，默认 200）。 */
    public static int attSyncApiLimit() {
        int limit = propInt("att.sync.api.limit", 200);
        return limit < 1 ? 1 : limit;
    }

    /** 平台接口**单次人数上限**（{@code att.sync.user.batch}，默认 50）：超限自动分批。 */
    public static int attSyncUserBatch() {
        int batch = propInt("att.sync.user.batch", 50);
        return batch < 1 ? 1 : batch;
    }

    /** 平台接口**单次时间跨度上限（天）**（{@code att.sync.day.batch}，默认 7）：超限自动按天切分。 */
    public static int attSyncDayBatch() {
        int days = propInt("att.sync.day.batch", 7);
        return days < 1 ? 1 : days;
    }

    /** 批次之间的间隔（毫秒，{@code att.sync.request.interval.ms}，默认 200）。 */
    public static int attSyncRequestIntervalMs() {
        int ms = propInt("att.sync.request.interval.ms", 200);
        return ms < 0 ? 0 : ms;
    }

    /** 命中平台限流时的退避重试次数（{@code att.sync.retry.times}，默认 3；仍失败整批记失败）。 */
    public static int attSyncRetryTimes() {
        int times = propInt("att.sync.retry.times", 3);
        return times < 0 ? 0 : times;
    }

    /** 旧考勤同步日志保留天数配置；现由「系统设置 → 日志保留」统一维护，默认 0。 */
    public static int attLogKeepDays() {
        return propInt("att.log.keep.days", 0);
    }

    /** 「最近 N 天」口径的默认 N（{@code att.win.last.days.default}，默认 2）。 */
    public static int attWinLastDaysDefault() {
        int days = propInt("att.win.last.days.default", 2);
        return days < 1 ? 1 : days;
    }

    /** 连续失败到几次给出显式告警（{@code att.fail.alert.threshold}，默认 3）。 */
    public static int attFailAlertThreshold() {
        int times = propInt("att.fail.alert.threshold", 3);
        return times < 1 ? 1 : times;
    }

    /**
     * 打卡来源标识（写入 E9 标准打卡表的 {@code signfrom}）：
     * 企微 {@code att.sign.source.wecom}（默认 {@code Wechat}，E9 标准口径里已有）、
     * 钉钉 {@code att.sign.source.dingtalk}（默认 {@code DingTalk}）、
     * 飞书 {@code att.sign.source.feishu}（默认 {@code Feishu}）。
     */
    public static String attSignSource(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return prop("att.sign.source.dingtalk", "DingTalk");
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return prop("att.sign.source.feishu", "Feishu");
        }
        return prop("att.sign.source.wecom", "Wechat");
    }

    /**
     * 补签卡（补卡）的来源标识前缀（{@code att.sign.source.remedy.prefix}，默认 {@code card:}）。
     *
     * <p>E9 标准补卡流程写进打卡表的来源是 {@code card:|wfid|…|requestid|…}，
     * 标准考勤报表就是按“来源以 card 开头”把它当补卡识别的，所以这里用同一口径，
     * 后面再拼上平台与原始类型，便于追溯（例：{@code card:|oaones|wecom|remedy}）。</p>
     */
    public static String attSignRemedyPrefix() {
        return prop("att.sign.source.remedy.prefix", "card:");
    }

    /**
     * 反向同步（OA → 第三方）**要排除**的 OA 打卡来源（{@code att.oa.sign.exclude.sources}）。
     *
     * <p>默认排除“本功能从第三方平台导入回来的记录”与 E9 自带的导入来源，
     * 避免把平台来的打卡又搬回平台（需求 3.9.1 / Q9）。留空表示只按映射表排除。</p>
     */
    public static String attOaSignExcludeSources() {
        String def = attSignSource(OaonesConst.TYPE_WECHAT) + ","
                + attSignSource(OaonesConst.TYPE_DINGTALK) + ","
                + attSignSource(OaonesConst.TYPE_FEISHU)
                + ",importExcel,OutDataSourceSyn,EMSyn,EMSyn_out";
        return prop("att.oa.sign.exclude.sources", def);
    }

    /**
     * 反向写入方式（需求 3.9 第 4 条的三级方案）：
     * {@code direct} 直接写入打卡 / {@code card} 走平台的补卡（申诉）审批接口 /
     * {@code none} 显式标记为不支持。
     *
     * <p><b>正式口径（2026-10-15 按官方接口文档核对）</b>：企微打卡已开放
     * 「添加打卡记录」{@code /cgi-bin/checkin/add_checkin_record}（= {@code direct}）与
     * 「为打卡人员补卡」{@code /cgi-bin/checkin/punch_correction}（= {@code card}），
     * 因此企微配置为 {@code card}（写成补卡、限 600 次/分钟；direct 需另配打卡地点 / 设备固定值，
     * 且接口限 60 次/分钟）；钉钉 / 飞书不开放打卡写入，只能走补卡审批，留 {@code none}
     * 直到现场把审批模板 / 审批人配好（不做“看起来在同步、其实没写成”的假成功）。</p>
     */
    public static String attPushMode(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return prop("att.push.mode.dingtalk", OaonesConst.ATT_PUSH_MODE_NONE);
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return prop("att.push.mode.feishu", OaonesConst.ATT_PUSH_MODE_NONE);
        }
        return prop("att.push.mode.wecom", OaonesConst.ATT_PUSH_MODE_NONE);
    }

    /** 钉钉“补卡审批”用的审批模板编码（{@code att.push.dingtalk.process.code}，留空 = 未配置）。 */
    public static String attPushDingTalkProcessCode() {
        return prop("att.push.dingtalk.process.code", "");
    }

    /** 飞书“补卡审批”用的审批定义编码（{@code att.push.feishu.approval.code}，留空 = 未配置）。 */
    public static String attPushFeishuApprovalCode() {
        return prop("att.push.feishu.approval.code", "");
    }

    /** 企微反向写入 direct（「添加打卡记录」）的接口路径（{@code att.wecom.checkin.add.path}）。 */
    public static String attWeComAddRecordPath() {
        return prop("att.wecom.checkin.add.path", "/cgi-bin/checkin/add_checkin_record");
    }

    /** 企微反向写入 card（「为打卡人员补卡」）的接口路径（{@code att.wecom.checkin.correct.path}）。 */
    public static String attWeComPunchCorrectionPath() {
        return prop("att.wecom.checkin.correct.path", "/cgi-bin/checkin/punch_correction");
    }

    /**
     * 企微 direct 写入时的“打卡地点标题”（{@code att.push.wecom.location.title}）。
     * 平台把该字段列为必填，且平台侧没有“接口写入”这一档，所以按配置写固定值。
     */
    public static String attPushWeComLocationTitle() {
        return prop("att.push.wecom.location.title", "OA考勤同步");
    }

    /** 企微 direct 写入时的“打卡地点详情”（{@code att.push.wecom.location.detail}，必填）。 */
    public static String attPushWeComLocationDetail() {
        return prop("att.push.wecom.location.detail", "由 OA 考勤同步写入");
    }

    /** 企微 direct 写入时的“打卡设备类型”（{@code att.push.wecom.device.type}，1 门禁 / 2 考勤机 / 3 其他）。 */
    public static int attPushWeComDeviceType() {
        return propInt("att.push.wecom.device.type", 3);
    }

    /** 企微 direct 写入时的“打卡设备品牌”（{@code att.push.wecom.device.detail}，必填，≤40 字符）。 */
    public static String attPushWeComDeviceDetail() {
        return prop("att.push.wecom.device.detail", "OA考勤同步");
    }

    /** 企微 direct 写入时的打卡备注（{@code att.push.wecom.notes}，留空 = 不下发该字段）。 */
    public static String attPushWeComNotes() {
        return prop("att.push.wecom.notes", "由 OA 考勤同步写入");
    }

    /**
     * 企微“按成员读考勤组 / 打卡规则”接口路径
     * （{@code att.wecom.checkin.group.path}，默认 {@code /cgi-bin/checkin/getcheckinoption}）。
     *
     * <p>请求体的 {@code datetime} 与 {@code useridlist} 都是必填（字段名就是 {@code useridlist}），
     * 所以「校验」在零绑定成员的租户上不会走这条路，见 {@link #attWeComCorpGroupPath()}。</p>
     */
    public static String attWeComGroupPath() {
        return prop("att.wecom.checkin.group.path", "/cgi-bin/checkin/getcheckinoption");
    }

    /**
     * 企微“企业所有打卡规则”接口路径
     * （{@code att.wecom.checkin.corppath}，默认 {@code /cgi-bin/checkin/getcorpcheckinoption}）。
     *
     * <p>请求体为空，拿全企业的打卡规则——「校验」在租户还没有已绑定成员时用它，
     * 这条路不需要 useridlist，因此零绑定的租户也能校验到考勤数据读取权限。</p>
     */
    public static String attWeComCorpGroupPath() {
        return prop("att.wecom.checkin.corppath", "/cgi-bin/checkin/getcorpcheckinoption");
    }

    /** 企微打卡数据接口路径（{@code att.wecom.checkin.data.path}，默认 {@code /cgi-bin/checkin/getcheckindata}）。 */
    public static String attWeComDataPath() {
        return prop("att.wecom.checkin.data.path", "/cgi-bin/checkin/getcheckindata");
    }

    /** 钉钉考勤组接口路径（{@code att.dingtalk.group.path}，默认 {@code /topapi/attendance/getsimplegroups}）。 */
    public static String attDingTalkGroupPath() {
        return prop("att.dingtalk.group.path", "/topapi/attendance/getsimplegroups");
    }

    /** 钉钉打卡结果接口路径（{@code att.dingtalk.list.path}，默认 {@code /topapi/attendance/list}）。 */
    public static String attDingTalkListPath() {
        return prop("att.dingtalk.list.path", "/topapi/attendance/list");
    }

    /** 钉钉补卡结果接口路径（{@code att.dingtalk.remedy.path}，默认 {@code /topapi/attendance/listrecord}）。 */
    public static String attDingTalkRemedyPath() {
        return prop("att.dingtalk.remedy.path", "/topapi/attendance/listrecord");
    }

    /** 飞书考勤组接口路径（{@code att.feishu.group.path}，默认 {@code /open-apis/attendance/v1/groups}）。 */
    public static String attFeishuGroupPath() {
        return prop("att.feishu.group.path", "/open-apis/attendance/v1/groups");
    }

    /** 飞书打卡流水接口路径（{@code att.feishu.flow.path}，默认 {@code /open-apis/attendance/v1/user_flows/query}）。 */
    public static String attFeishuFlowPath() {
        return prop("att.feishu.flow.path", "/open-apis/attendance/v1/user_flows/query");
    }

    /** 飞书打卡结果接口路径（{@code att.feishu.task.path}，默认 {@code /open-apis/attendance/v1/user_tasks/query}）。 */
    public static String attFeishuTaskPath() {
        return prop("att.feishu.task.path", "/open-apis/attendance/v1/user_tasks/query");
    }

    /** 飞书补卡记录接口路径（{@code att.feishu.remedy.path}，默认 {@code /open-apis/attendance/v1/user_task_remedys/query}）。 */
    public static String attFeishuRemedyPath() {
        return prop("att.feishu.remedy.path", "/open-apis/attendance/v1/user_task_remedys/query");
    }

    /** 钉钉补卡审批的审批人（{@code att.push.dingtalk.approvers}，逗号分隔 userId，可留空）。 */
    public static String attPushDingTalkApprovers() {
        return prop("att.push.dingtalk.approvers", "");
    }

    /** 飞书补卡审批的审批人（{@code att.push.feishu.approvers}，逗号分隔 user_id，可留空）。 */
    public static String attPushFeishuApprovers() {
        return prop("att.push.feishu.approvers", "");
    }

    /** 考勤同步设置建模模块ID（按 {@link #attCfgTable()} 反查）。 */
    public static int attCfgModeId() {
        return WeaverUtil.getModeIdByTableName(attCfgTable());
    }

    /** 考勤同步批次建模模块ID（按 {@link #attLogTable()} 反查）。 */
    public static int attLogModeId() {
        return WeaverUtil.getModeIdByTableName(attLogTable());
    }

    /** 考勤同步明细分建模模块ID（按 {@link #attLogItemTable()} 反查）。 */
    public static int attLogItemModeId() {
        return WeaverUtil.getModeIdByTableName(attLogItemTable());
    }

    /** 考勤同步映射建模模块ID（按 {@link #attLinkTable()} 反查）。 */
    public static int attLinkModeId() {
        return WeaverUtil.getModeIdByTableName(attLinkTable());
    }
}
