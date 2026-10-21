package com.customization.oaones.appsyn.msg.feishu.dto;

/**
 * 消息接收人（已完成 OA 账号 -> 飞书 userid 转换与租户/应用路由）。
 */
public class MsgReceiver {

    /** OA 人员ID；直接传飞书 userid 时为 0。 */
    private int oaUserId;
    private String oaName;
    private String oaCode;
    /** 接收人的 OA 语言ID（hrmresource.systemlanguage，7 简体 / 8 英文 / 9 繁体…）；未知为 0。 */
    private int language;
    private String wxUserid;
    /** 组织同步租户ID（uf_oaones_qywx_config.id）；未知为 0。 */
    private int cfgId;
    /** 飞书应用ID（uf_oaones_app_bind.id，与应用绑定共用同一行）；未匹配到为 0。 */
    private int appId;

    public int getOaUserId() { return oaUserId; }
    public void setOaUserId(int oaUserId) { this.oaUserId = oaUserId; }

    public String getOaName() { return oaName; }
    public void setOaName(String oaName) { this.oaName = oaName; }

    public String getOaCode() { return oaCode; }
    public void setOaCode(String oaCode) { this.oaCode = oaCode; }

    public int getLanguage() { return language; }
    public void setLanguage(int language) { this.language = language; }

    public String getWxUserid() { return wxUserid; }
    public void setWxUserid(String wxUserid) { this.wxUserid = wxUserid; }

    public int getCfgId() { return cfgId; }
    public void setCfgId(int cfgId) { this.cfgId = cfgId; }

    public int getAppId() { return appId; }
    public void setAppId(int appId) { this.appId = appId; }
}
