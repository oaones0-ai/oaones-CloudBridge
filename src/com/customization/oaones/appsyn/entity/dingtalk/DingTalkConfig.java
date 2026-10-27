package com.customization.oaones.appsyn.entity.dingtalk;

/**
 * 钉钉租户配置实体（对应业务表：表名取 oaones.appsyn.properties#oaones_qywx_config_table，
 * 建模模块ID按表名反查，配置里不出现）。
 */
public class DingTalkConfig {

    private Integer id;
    private String corpName;
    private String corpId;
    private String corpSecret;
    private String agentId;
    private Integer rootDeptId;
    /** 同步开关：1 启用 / 0 停用（物理列 qy）。 */
    private Integer qy;
    /** 消息通知开关：1 开启（默认）/ 0 关闭；在独立菜单“消息设置 → 租户开关”里维护。 */
    private Integer msgEnable;
    private String remark;
    /** 维护人（OA 人员ID，逗号分隔；为空表示公共租户，所有用户可同步）。 */
    private String maintainerIds;
    /**
     * 平台类型：0 钉钉 / 1 钉钉（物理列 type，见 docs/oaones_model_tables.md）。
     *
     * <p>钉钉连接器与钉钉连接器**共用同一张租户表**（设置类表结构可复用时不新建表），
     * 用本列区分租户属于哪个平台：本连接器只读写 {@code type = 1} 的行，
     * 钉钉侧只读写 {@code type = 0} 的行。</p>
     */
    private Integer type;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCorpName() { return corpName; }
    public void setCorpName(String corpName) { this.corpName = corpName; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getCorpSecret() { return corpSecret; }
    public void setCorpSecret(String corpSecret) { this.corpSecret = corpSecret; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public Integer getRootDeptId() { return rootDeptId; }
    public void setRootDeptId(Integer rootDeptId) { this.rootDeptId = rootDeptId; }

    public Integer getQy() { return qy; }
    public void setQy(Integer qy) { this.qy = qy; }

    public Integer getMsgEnable() { return msgEnable; }
    public void setMsgEnable(Integer msgEnable) { this.msgEnable = msgEnable; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getMaintainerIds() { return maintainerIds; }
    public void setMaintainerIds(String maintainerIds) { this.maintainerIds = maintainerIds; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

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
    public void setModedatamodifydatetime(String modedatamodifydatetime) { this.modedatamodifydatetime = modedatamodifydatetime; }
}
