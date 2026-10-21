package com.customization.oaones.appsyn.entity.dingtalk;

/**
 * 统一登录设置实体（对应业务表：表名取 oaones.properties#oaones_login_config_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行 = 统一登录页的一套设置：开哪些登录方式（账号密码 / 验证登录 / 扫码登录）、
 * 扫码二维码有效期。其中“验证登录”有两种验证方式（手机号 / 邮箱），
 * 分别落在 {@code mobile_enable} 与 {@code email_enable} 两列上
 * （统一登录页只有一个扫码页签、一张二维码，见公共实现
 * {@code service.impl.wecom.LoginConfigServiceImpl}）。
 * 另有三个可选的**外观**项：{@code logo}（登录页右侧登录框上方的图片）、
 * {@code bgImage}（整页背景图，前端按窗口自动缩放）、
 * {@code brandHtml}（登录页**左侧**的富文本，设置什么显示什么；都为空时登录页用系统默认样式）。</p>
 */
public class LoginConfig {

    private Integer id;
    /** 账号密码登录：0 关 / 1 开。 */
    private Integer passwordEnable;
    /** 验证登录·手机号（验证码由本模块自管、短信走平台标准能力）：0 关 / 1 开。 */
    private Integer mobileEnable;
    /** 验证登录·邮箱（验证码由本模块自管、邮件走平台标准能力）：0 关 / 1 开。 */
    private Integer emailEnable;
    /** 钉钉扫码登录（本系统自己生成的二维码）：0 关 / 1 开。 */
    private Integer wecomQrEnable;
    /** 钉钉扫码登录：0 关 / 1 开（仅保留配置项，尚未接入）。 */
    private Integer dingtalkQrEnable;
    /** 个人微信登录：0 关 / 1 开（仅保留配置项，尚未接入）。 */
    private Integer personalWxEnable;
    /** 扫码二维码有效期（秒）；空/0 时用 oaones.properties 的 login.qr.ttl。 */
    private Integer qrTtlSeconds;
    /**
     * 登录页 logo 的站内地址（页面上传后落 {@code /oaones/img/loginlogo_xxx.png}）；空 = 登录页不显示 logo。
     */
    private String logo;
    /**
     * 登录页**整页背景图**的站内地址（页面上传后落 {@code /oaones/img/loginbg_xxx.png}）；
     * 空 = 用系统默认底色（{@code --oa-g-50}）。
     */
    private String bgImage;
    /**
     * 登录页**左侧**的富文本（HTML）：非空时左侧就按它显示（设置什么显示什么），
     * 空 = 用系统默认的左侧说明（标题 + 已开启登录方式的清单）。
     */
    private String brandHtml;
    private String remark;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPasswordEnable() { return passwordEnable; }
    public void setPasswordEnable(Integer passwordEnable) { this.passwordEnable = passwordEnable; }

    public Integer getMobileEnable() { return mobileEnable; }
    public void setMobileEnable(Integer mobileEnable) { this.mobileEnable = mobileEnable; }

    public Integer getEmailEnable() { return emailEnable; }
    public void setEmailEnable(Integer emailEnable) { this.emailEnable = emailEnable; }

    public Integer getWecomQrEnable() { return wecomQrEnable; }
    public void setWecomQrEnable(Integer wecomQrEnable) { this.wecomQrEnable = wecomQrEnable; }

    public Integer getDingtalkQrEnable() { return dingtalkQrEnable; }
    public void setDingtalkQrEnable(Integer dingtalkQrEnable) { this.dingtalkQrEnable = dingtalkQrEnable; }

    public Integer getPersonalWxEnable() { return personalWxEnable; }
    public void setPersonalWxEnable(Integer personalWxEnable) { this.personalWxEnable = personalWxEnable; }

    public Integer getQrTtlSeconds() { return qrTtlSeconds; }
    public void setQrTtlSeconds(Integer qrTtlSeconds) { this.qrTtlSeconds = qrTtlSeconds; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getBgImage() { return bgImage; }
    public void setBgImage(String bgImage) { this.bgImage = bgImage; }

    public String getBrandHtml() { return brandHtml; }
    public void setBrandHtml(String brandHtml) { this.brandHtml = brandHtml; }

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
