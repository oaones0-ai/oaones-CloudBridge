package com.customization.oaones.appsyn.entity;

/**
 * 个人微信授权密钥实体（对应业务表：表名取
 * oaones.appsyn.properties#oaones_personal_wx_config_table，建模模块ID按表名反查，配置里不出现）。
 *
 * <p><b>本表只保留一行</b>（取 id 最小的一条）：个人微信没有“企业 / 租户”概念，
 * 一套部署只用一份微信授权密钥（AppID + AppSecret），用它把扫码授权换回的 code
 * 换成扫码人的 openid / unionid 与昵称头像。
 * 维护页面：{@code /oaones/appsyn/personalWx/config.jsp}（独立一级菜单「微信设置」）。</p>
 */
public class PersonalWxConfig {

    private Integer id;
    /** 微信应用 AppID（微信开放平台网站应用 / 公众号的 AppID）。 */
    private String appId;
    /** 微信应用 AppSecret（换取 openid / 用户信息必需）。 */
    private String appSecret;
    private String remark;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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
