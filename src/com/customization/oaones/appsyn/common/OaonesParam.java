package com.customization.oaones.appsyn.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper 入参工厂。
 *
 * <p>本模块九张业务表的物理名就是 properties 里配置的表名（{@link OaonesProps}）；
 * 所有 Mapper 语句中的表名统一使用
 * {@code ${...}} 占位，由本类把这九张表的物理名一次性放入入参 Map，
 * 便于解决多表关联查询时的表名取值问题。</p>
 */
public final class OaonesParam {

    /** 企微租户配置表物理名。 */
    public static final String CFG_TABLE = "cfgTable";
    /** 部门绑定表物理名。 */
    public static final String DEPT_BIND_TABLE = "deptBindTable";
    /** OA 分部 - 平台租户一对一映射表物理名。 */
    public static final String SUB_TENANT_MAP_TABLE = "subTenantMapTable";
    /** 人员绑定表物理名。 */
    public static final String USER_BIND_TABLE = "userBindTable";
    /** 默认人员绑定规则表物理名。 */
    public static final String USER_RULE_TABLE = "userRuleTable";
    /** 同步日志表物理名。 */
    public static final String SYNC_LOG_TABLE = "syncLogTable";
    /** 同步日志明细表物理名。 */
    public static final String SYNC_LOG_ITEM_TABLE = "syncLogItemTable";
    /** 部门快照表物理名。 */
    public static final String DEPT_TMP_TABLE = "deptTmpTable";
    /** 人员快照表物理名。 */
    public static final String USER_TMP_TABLE = "userTmpTable";
    /** 操作监控日志表物理名（登录 / 退出 / 菜单访问留痕）。 */
    public static final String OP_LOG_TABLE = "opLogTable";
    /** 企微应用绑定表物理名。 */
    public static final String APP_BIND_TABLE = "appBindTable";
    /** 应用中心表（第三方应用列表：默认地址 + 单点登录实现类）物理名。 */
    public static final String APP_TABLE = "appTable";

    /** 会议 / 日程同步设置表物理名（uf_oaones_mtg_sync，biz 区分会议 / 日程）。 */
    public static final String MTG_SYNC_TABLE = "mtgSyncTable";
    /** 会议 / 日程同步记录表物理名（uf_oaones_mtg_sync_log）。 */
    public static final String MTG_SYNC_LOG_TABLE = "mtgSyncLogTable";
    /** 会议 / 日程同步「个人同步开关」表物理名（uf_oaones_mtg_sync_user）。 */
    public static final String MTG_SYNC_USER_TABLE = "mtgSyncUserTable";
    /** 会议 / 日程同步「对象映射」表物理名（uf_oaones_mtg_link）。 */
    public static final String MTG_LINK_TABLE = "mtgLinkTable";
    /** OA 会议来源表物理名（E9 Meeting，可配）。 */
    public static final String MTG_MEETING_TABLE = "mtgMeetingTable";
    /** OA 会议参与人表物理名（E9 Meeting_Member2，可配）。 */
    public static final String MTG_MEETING_MEMBER_TABLE = "mtgMeetingMemberTable";
    /** OA 日程来源表物理名（E9 WorkPlan，可配）。 */
    public static final String MTG_SCHEDULE_TABLE = "mtgScheduleTable";
    /** OA 日程参与人表物理名（E9 workplanMember，可配）。 */
    public static final String MTG_SCHEDULE_MEMBER_TABLE = "mtgScheduleMemberTable";

    /** 考勤同步设置表物理名（uf_oaones_att_cfg，一行 = 平台 + 租户 + 方向，可多条）。 */
    public static final String ATT_CFG_TABLE = "attCfgTable";
    /** 考勤同步「计划任务」时间段明细表物理名（uf_oaones_att_plan_item）。 */
    public static final String ATT_PLAN_ITEM_TABLE = "attPlanItemTable";
    /** 考勤同步批次表物理名（uf_oaones_att_log）。 */
    public static final String ATT_LOG_TABLE = "attLogTable";
    /** 考勤同步打卡明细表物理名（uf_oaones_att_log_item）。 */
    public static final String ATT_LOG_ITEM_TABLE = "attLogItemTable";
    /** 考勤同步映射表物理名（uf_oaones_att_link）。 */
    public static final String ATT_LINK_TABLE = "attLinkTable";
    /** E9 标准考勤打卡表物理名（HrmScheduleSign，可配）：正向同步的写入目标。 */
    public static final String ATT_SIGN_TABLE = "attSignTable";

    /** 个人微信授权密钥表物理名。 */
    public static final String PERSONAL_WX_CONFIG_TABLE = "personalWxConfigTable";
    /** 个人微信绑定表物理名。 */
    public static final String PERSONAL_WX_BIND_TABLE = "personalWxBindTable";

    private OaonesParam() {
    }

    /** 新建一个已包含全部业务表物理名的入参 Map。 */
    public static Map<String, Object> of() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put(CFG_TABLE, OaonesProps.cfgTable());
        params.put(DEPT_BIND_TABLE, OaonesProps.deptBindTable());
        params.put(SUB_TENANT_MAP_TABLE, OaonesProps.subTenantMapTable());
        params.put(USER_BIND_TABLE, OaonesProps.userBindTable());
        params.put(USER_RULE_TABLE, OaonesProps.userRuleTable());
        params.put(SYNC_LOG_TABLE, OaonesProps.syncLogTable());
        params.put(SYNC_LOG_ITEM_TABLE, OaonesProps.syncLogItemTable());
        params.put(DEPT_TMP_TABLE, OaonesProps.deptTmpTable());
        params.put(USER_TMP_TABLE, OaonesProps.userTmpTable());
        params.put(OP_LOG_TABLE, OaonesProps.opLogTable());
        params.put(APP_BIND_TABLE, OaonesProps.appBindTable());
        params.put(APP_TABLE, OaonesProps.appTable());
        params.put(MTG_SYNC_TABLE, OaonesProps.mtgSyncTable());
        params.put(MTG_SYNC_LOG_TABLE, OaonesProps.mtgSyncLogTable());
        params.put(MTG_SYNC_USER_TABLE, OaonesProps.mtgSyncUserTable());
        params.put(MTG_LINK_TABLE, OaonesProps.mtgLinkTable());
        params.put(MTG_MEETING_TABLE, OaonesProps.mtgMeetingTable());
        params.put(MTG_MEETING_MEMBER_TABLE, OaonesProps.mtgMeetingMemberTable());
        params.put(MTG_SCHEDULE_TABLE, OaonesProps.mtgScheduleTable());
        params.put(MTG_SCHEDULE_MEMBER_TABLE, OaonesProps.mtgScheduleMemberTable());
        params.put(ATT_CFG_TABLE, OaonesProps.attCfgTable());
        params.put(ATT_PLAN_ITEM_TABLE, OaonesProps.attPlanItemTable());
        params.put(ATT_LOG_TABLE, OaonesProps.attLogTable());
        params.put(ATT_LOG_ITEM_TABLE, OaonesProps.attLogItemTable());
        params.put(ATT_LINK_TABLE, OaonesProps.attLinkTable());
        params.put(ATT_SIGN_TABLE, OaonesProps.attSignTable());
        params.put(PERSONAL_WX_CONFIG_TABLE, OaonesProps.personalWxConfigTable());
        params.put(PERSONAL_WX_BIND_TABLE, OaonesProps.personalWxBindTable());
        return params;
    }

    /** 链式写入一个入参。 */
    public static Map<String, Object> of(String key, Object value) {
        Map<String, Object> params = of();
        params.put(key, value);
        return params;
    }

    /** 在已有入参 Map 上链式写入一个入参。 */
    public static Map<String, Object> with(Map<String, Object> params, String key, Object value) {
        params.put(key, value);
        return params;
    }
}
