package com.customization.oaones.appsyn.dto.wecom;

/**
 * 企微应用绑定列表行（绑定表 + 企微租户配置 + 创建人 关联结果）。
 */
public class AppBindRow {

    private Integer id;
    private Integer cfgId;
    private String cfgName;
    private String cfgCorpId;
    private Integer cfgSyncEnable;

    private String agentId;
    private String appSecret;
    private String appName;
    private String oaPageUrl;
    /** 手机端 OA 页面地址（留空表示与 PC 端相同）。 */
    private String oaPageUrlMobile;

    /** 绑定状态（物理列 yx）：0 有效 / 1 无效。 */
    private Integer bindState;
    private String remark;
    private Integer formmodeid;

    private Integer modedatacreater;
    private String creatorName;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public String getCfgCorpId() { return cfgCorpId; }
    public void setCfgCorpId(String cfgCorpId) { this.cfgCorpId = cfgCorpId; }

    public Integer getCfgSyncEnable() { return cfgSyncEnable; }
    public void setCfgSyncEnable(Integer cfgSyncEnable) { this.cfgSyncEnable = cfgSyncEnable; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getOaPageUrl() { return oaPageUrl; }
    public void setOaPageUrl(String oaPageUrl) { this.oaPageUrl = oaPageUrl; }

    public String getOaPageUrlMobile() { return oaPageUrlMobile; }
    public void setOaPageUrlMobile(String oaPageUrlMobile) { this.oaPageUrlMobile = oaPageUrlMobile; }

    public Integer getBindState() { return bindState; }
    public void setBindState(Integer bindState) { this.bindState = bindState; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getFormmodeid() { return formmodeid; }
    public void setFormmodeid(Integer formmodeid) { this.formmodeid = formmodeid; }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) {
        this.modedatacreatedate = modedatacreatedate;
    }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) {
        this.modedatacreatetime = modedatacreatetime;
    }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
