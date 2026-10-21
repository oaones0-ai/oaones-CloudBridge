package com.customization.oaones.appsyn.entity.dingtalk;

/**
 * 钉钉工作台应用 - OA 页面 绑定实体（对应业务表：表名取 oaones.properties#oaones_app_bind_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一条记录 = 钉钉某个自建应用（AgentId）在钉钉工作台里的一个入口：
 * 用户从钉钉工作台打开该应用时，系统按钉钉网页授权拿到的成员身份
 * 找到其绑定的 OA 账号，免登录进入本记录配置的 OA 页面。</p>
 *
 * <p>同一租户可绑定多个应用（多应用绑定）；同一租户下同一个 AgentId 只允许一条记录
 * （`(cfg_id, agent_id)` 唯一索引兜底）。</p>
 */
public class AppBind {

    private Integer id;
    private Integer cfgId;
    /** 钉钉自建应用 AgentId（钉钉工作台应用的唯一标识）。 */
    private String agentId;
    /**
     * 该自建应用的 Secret：钉钉网页授权只能用它（或租户AppSecret）换成员身份。
     *
     * <p>留空时回退用租户配置里的AppSecret（`uf_oaones_qywx_config.corp_secret`），
     * 便于“租户配置里维护的自建应用就是工作台应用”这种单应用场景。</p>
     */
    private String appSecret;
    /** 钉钉工作台应用名称（页面展示用，钉钉后台叫什么这里就填什么）。 */
    private String appName;
    /** 免登录后打开的 OA 指定页面（PC/桌面端；站内地址，如 /wui/index.html#/main/...）。 */
    private String oaPageUrl;
    /**
     * 免登录后打开的 OA 指定页面（手机端；站内地址，留空表示与 PC 端用同一个地址）。
     *
     * <p>从钉钉手机端打开时用本字段，从桌面端（钉钉电脑版/浏览器）打开时用 {@link #oaPageUrl}。</p>
     */
    private String oaPageUrlMobile;
    /** 绑定状态（物理列 yx）：0 有效 / 1 无效，见 {@code OaonesConst.APP_BIND_STATE_VALID}。 */
    private Integer yx;
    private String remark;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

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

    public Integer getYx() { return yx; }
    public void setYx(Integer yx) { this.yx = yx; }

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
    public void setModedatacreatedate(String modedatacreatedate) {
        this.modedatacreatedate = modedatacreatedate;
    }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) {
        this.modedatacreatetime = modedatacreatetime;
    }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) {
        this.modedatamodifier = modedatamodifier;
    }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
