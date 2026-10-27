package com.customization.oaones.appsyn.entity.dingtalk;

/**
 * 同步日志明细实体（对应业务表：表名取 oaones.appsyn.properties#oaones_sync_log_item_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行一个同步对象：单条部门、单条人员各一行，记录该对象本次同步的过程。</p>
 */
public class SyncLogItem {

    private Integer id;
    /** 所属同步日志ID（uf_oaones_appsyn_sync_log.id）。 */
    private Integer logId;
    private Integer cfgid;
    /** 同步方向：0 OA->钉钉 / 1 钉钉->OA。 */
    private Integer direction;
    /** 对象类型：0 部门 / 1 人员。 */
    private Integer itemType;
    /** 动作：0 新增 / 1 更新 / 2 跳过 / 3 失败。 */
    private Integer op;
    private String oaId;
    private String oaName;
    private String wxId;
    private String wxName;
    /** 目标侧父节点（部门同步时的挂靠位置）。 */
    private String parentId;
    private String parentName;
    /** 本次同步内的处理顺序。 */
    private Integer seq;
    private String message;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getLogId() { return logId; }
    public void setLogId(Integer logId) { this.logId = logId; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public Integer getDirection() { return direction; }
    public void setDirection(Integer direction) { this.direction = direction; }

    public Integer getItemType() { return itemType; }
    public void setItemType(Integer itemType) { this.itemType = itemType; }

    public Integer getOp() { return op; }
    public void setOp(Integer op) { this.op = op; }

    public String getOaId() { return oaId; }
    public void setOaId(String oaId) { this.oaId = oaId; }

    public String getOaName() { return oaName; }
    public void setOaName(String oaName) { this.oaName = oaName; }

    public String getWxId() { return wxId; }
    public void setWxId(String wxId) { this.wxId = wxId; }

    public String getWxName() { return wxName; }
    public void setWxName(String wxName) { this.wxName = wxName; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
