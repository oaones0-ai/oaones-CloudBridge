package com.customization.oaones.appsyn.msg.feishu.entity;

/**
 * 推送用飞书应用视图（**不是独立表**，读的是“应用绑定”表 {@code uf_oaones_app_bind}
 * 关联飞书租户配置 {@code uf_oaones_qywx_config} 的结果）。
 *
 * <p>一个飞书自建应用只维护一次：应用名、App ID、应用 Secret、所属租户、启停都在
 * “应用绑定”页维护；消息中心只引用同一行，并补充租户侧的企业ID与 Secret 兜底，
 * 因此不再有 {@code uf_oaones_msg_app} 表。</p>
 */
public class MsgApp {

    /** uf_oaones_app_bind.id（消息类型 / 日志 / 卡片任务里的 app_id 即本ID）。 */
    private Integer id;
    private Integer cfgId;
    /** 租户名称（uf_oaones_qywx_config.corp_name）。 */
    private String cfgName;
    /** 租户企业ID（uf_oaones_qywx_config.corp_id）。 */
    private String corpId;
    /** 租户App Secret（应用 Secret 留空时的兜底）。 */
    private String corpSecret;
    /** 租户同步开关（仅展示）。 */
    private Integer cfgQy;
    /** 应用名称（应用绑定页维护）。 */
    private String appName;
    /** 应用 App ID。 */
    private String agentId;
    /** 应用自己的 Secret（可空，留空回退租户 Secret）。 */
    private String appSecret;
    /** 工作台入口页面（应用绑定页维护，消息中心只展示是否已配）。 */
    private String oaPageUrl;
    /** 0 有效 / 1 停用（应用绑定表 yx 口径）。 */
    private Integer yx;
    private String remark;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    /** 实际用于调用飞书的消息 Secret：优先应用 Secret，其次租户 Secret。 */
    public String getEffectiveSecret() {
        if (appSecret != null && appSecret.trim().length() > 0) {
            return appSecret.trim();
        }
        return corpSecret == null ? "" : corpSecret.trim();
    }

    /** 密钥是否来自租户配置兜底（页面展示用）。 */
    public boolean isSecretFromCfg() {
        return (appSecret == null || appSecret.trim().length() == 0)
                && corpSecret != null && corpSecret.trim().length() > 0;
    }

    /** 是否已配置工作台入口页面（同一行同时服务“应用绑定”与“消息推送”）。 */
    public boolean isEntryConfigured() {
        return oaPageUrl != null && oaPageUrl.trim().length() > 0;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getCorpSecret() { return corpSecret; }
    public void setCorpSecret(String corpSecret) { this.corpSecret = corpSecret; }

    public Integer getCfgQy() { return cfgQy; }
    public void setCfgQy(Integer cfgQy) { this.cfgQy = cfgQy; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getOaPageUrl() { return oaPageUrl; }
    public void setOaPageUrl(String oaPageUrl) { this.oaPageUrl = oaPageUrl; }

    public Integer getYx() { return yx; }
    public void setYx(Integer yx) { this.yx = yx; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
