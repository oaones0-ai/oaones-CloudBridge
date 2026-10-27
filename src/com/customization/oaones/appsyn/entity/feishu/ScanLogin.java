package com.customization.oaones.appsyn.entity.feishu;

/**
 * 扫码登录票据实体（对应业务表：表名取 oaones.appsyn.properties#oaones_scan_login_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行 = 登录页上的一次“自己生成的二维码”：
 * PC 生成 ticket 与二维码 → 扫码方（飞书客户端）打开二维码里的本模块落地页 →
 * OA 侧换出平台成员身份并按人员绑定关系找到 OA 账号 → 扫码人确认 →
 * PC 轮询命中后用一次性 login_token 换登录会话。</p>
 *
 * <p>状态口径见 {@link com.customization.oaones.appsyn.service.feishu.ScanLoginService} 的常量：
 * 0 待扫码 / 1 已扫码待确认 / 2 已确认 / 3 失败 / 4 已过期 / 5 已使用。</p>
 */
public class ScanLogin {

    private Integer id;
    /** 二维码票据（PC 生成，全局唯一；二维码内容与轮询都用它）。 */
    private String ticket;
    /** 生成二维码的浏览器标识：PC 完成登录时必须带上同一个值，避免二维码被拿到别的浏览器上用。 */
    private String browserKey;
    /** 二维码内容（指向本模块扫码落地页 scan.jsp?t=...）。 */
    private String qrText;
    /** 扫码平台：wecom / dingtalk / feishu（为空表示还没识别出扫码客户端）。 */
    private String platform;
    /** 命中的平台租户配置ID（uf_oaones_appsyn_qywx_config.id，飞书租户 type=2）。 */
    private Integer cfgId;
    /** 扫码人平台成员ID（飞书 user_id；应用没申请 user_id 权限时为 open_id）。 */
    private String outUserId;
    /** 扫码人平台成员名称（取人员快照，页面展示用）。 */
    private String outUserName;
    /** 命中的 OA 人员ID（按平台成员 + 人员绑定关系查出）。 */
    private Integer oaUserId;
    /** 确认后生成的一次性登录令牌（PC 换登录会话用，用过即失效）。 */
    private String loginToken;
    /** 状态：0 待扫码 / 1 已扫码待确认 / 2 已确认 / 3 失败 / 4 已过期 / 5 已使用。 */
    private Integer state;
    /** 失败原因或页面提示文案。 */
    private String msg;
    /** 登录成功后的落地页（站内地址，如 /wui/index.html#/main）。 */
    private String landingUrl;
    /** 过期时间（毫秒时间戳）。 */
    private Long expireTime;
    /** 扫码人确认时间（毫秒时间戳）。 */
    private Long confirmTime;
    /** 完成登录时间（毫秒时间戳）。 */
    private Long usedTime;
    private String createIp;
    private String createUa;
    private String scanIp;
    private String scanUa;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTicket() { return ticket; }
    public void setTicket(String ticket) { this.ticket = ticket; }

    public String getBrowserKey() { return browserKey; }
    public void setBrowserKey(String browserKey) { this.browserKey = browserKey; }

    public String getQrText() { return qrText; }
    public void setQrText(String qrText) { this.qrText = qrText; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getOutUserId() { return outUserId; }
    public void setOutUserId(String outUserId) { this.outUserId = outUserId; }

    public String getOutUserName() { return outUserName; }
    public void setOutUserName(String outUserName) { this.outUserName = outUserName; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getLoginToken() { return loginToken; }
    public void setLoginToken(String loginToken) { this.loginToken = loginToken; }

    public Integer getState() { return state; }
    public void setState(Integer state) { this.state = state; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public String getLandingUrl() { return landingUrl; }
    public void setLandingUrl(String landingUrl) { this.landingUrl = landingUrl; }

    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }

    public Long getConfirmTime() { return confirmTime; }
    public void setConfirmTime(Long confirmTime) { this.confirmTime = confirmTime; }

    public Long getUsedTime() { return usedTime; }
    public void setUsedTime(Long usedTime) { this.usedTime = usedTime; }

    public String getCreateIp() { return createIp; }
    public void setCreateIp(String createIp) { this.createIp = createIp; }

    public String getCreateUa() { return createUa; }
    public void setCreateUa(String createUa) { this.createUa = createUa; }

    public String getScanIp() { return scanIp; }
    public void setScanIp(String scanIp) { this.scanIp = scanIp; }

    public String getScanUa() { return scanUa; }
    public void setScanUa(String scanUa) { this.scanUa = scanUa; }

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
