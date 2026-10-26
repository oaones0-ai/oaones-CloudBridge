package com.customization.oaones.appsyn.common;

import com.customization.oaones.appsyn.msg.common.MsgProps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OAones 日志保留项清单。
 *
 * <p>页面、配置表与自动清理任务共用这里的一份清单，避免出现“页面能配、任务不清理”
 * 或“代码清理了、页面看不到”的两套口径。物理表名仍从 {@link OaonesProps}
 * 与 {@link MsgProps} 读取，支持现场按 properties 调整表名。</p>
 */
public final class LogRetentionCatalog {

    public static final String API_LOG = "api_log";
    public static final String OP_LOG = "op_log";
    public static final String MSG_LOG = "msg_log";
    public static final String ATT_LOG = "att_log";
    public static final String ATT_LOG_ITEM = "att_log_item";
    public static final String SYNC_LOG = "sync_log";
    public static final String SYNC_LOG_ITEM = "sync_log_item";
    public static final String MTG_SYNC_LOG = "mtg_sync_log";

    private static final List<Definition> DEFINITIONS;

    static {
        List<Definition> list = new ArrayList<Definition>();
        list.add(new Definition(API_LOG, "接口日志", "OAones 与第三方平台之间的接口调用记录"));
        list.add(new Definition(OP_LOG, "操作日志", "登录、退出与菜单访问记录"));
        list.add(new Definition(MSG_LOG, "推送日志", "统一消息中心的推送状态记录"));
        list.add(new Definition(ATT_LOG, "考勤同步批次", "考勤同步每次执行一条汇总记录"));
        list.add(new Definition(ATT_LOG_ITEM, "考勤同步明细", "考勤同步每条打卡动作一行"));
        list.add(new Definition(SYNC_LOG, "组织同步日志", "组织同步每次执行一条汇总记录"));
        list.add(new Definition(SYNC_LOG_ITEM, "组织同步明细", "组织同步每个部门或人员一行"));
        list.add(new Definition(MTG_SYNC_LOG, "会议/日程同步记录", "会议或日程每次同步动作一行"));
        DEFINITIONS = Collections.unmodifiableList(list);
    }

    private LogRetentionCatalog() {
    }

    /** 按页面展示顺序返回全部日志项。 */
    public static List<Definition> all() {
        return DEFINITIONS;
    }

    /** 按日志编码查找；未知编码返回 null。 */
    public static Definition find(String code) {
        String target = OaonesUtil.s(code).trim();
        for (Definition item : DEFINITIONS) {
            if (item.getCode().equals(target)) {
                return item;
            }
        }
        return null;
    }

    /** 日志项的编码、名称、说明与实际物理表名。 */
    public static final class Definition {
        private final String code;
        private final String name;
        private final String description;

        private Definition(String code, String name, String description) {
            this.code = code;
            this.name = name;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        /** 当前配置下的物理表名。 */
        public String getTableName() {
            if (API_LOG.equals(code)) {
                return OaonesProps.apiLogTable();
            }
            if (OP_LOG.equals(code)) {
                return OaonesProps.opLogTable();
            }
            if (MSG_LOG.equals(code)) {
                return MsgProps.logTable();
            }
            if (ATT_LOG.equals(code)) {
                return OaonesProps.attLogTable();
            }
            if (ATT_LOG_ITEM.equals(code)) {
                return OaonesProps.attLogItemTable();
            }
            if (SYNC_LOG.equals(code)) {
                return OaonesProps.syncLogTable();
            }
            if (SYNC_LOG_ITEM.equals(code)) {
                return OaonesProps.syncLogItemTable();
            }
            if (MTG_SYNC_LOG.equals(code)) {
                return OaonesProps.mtgSyncLogTable();
            }
            return "";
        }
    }
}
