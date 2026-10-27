package com.customization.oaones.appsyn.entity.wecom;

/**
 * 统一登录设置实体（对应业务表：表名取 oaones.appsyn.properties#oaones_login_config_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行 = 统一登录页的一套设置：开哪些登录方式（账号密码 / 验证登录 / 扫码登录）、
 * 扫码二维码有效期。其中“验证登录”有两种验证方式（手机号 / 邮箱），
 * 分别落在 {@code mobile_enable} 与 {@code email_enable} 两列上。
 * 企业微信 / 钉钉 / 飞书共用一个页面里的一张本站二维码；
 * 个人微信使用微信开放平台官方二维码并有独立页签（见 {@code QrLoginPlatform}）。
 * 另有三个可选的**外观**项：{@code logo}（登录页右侧登录框上方的图片）、
 * {@code bgImage}（整页背景图，前端按窗口自动缩放）、
 * {@code brandHtml}（登录页**左侧**的富文本，设置什么显示什么；都为空时登录页用系统默认样式）。
 * 登录框还可自定义位置与宽高（{@code loginBoxLeft/Top/Width/Height}，
 * 四列要么都有值、要么都为 NULL = 默认居中布局）；{@code loginLayoutMode} 是登录框与左侧文字
 * 共用的定位方式（0 = 视口左上角，1 = 区域相对视口中心的偏移），两者始终使用同一个基准；
 * {@code loginBoxOpacity} 控制登录框背景的透明度（0 = 不透明，100 = 完全透明，NULL = 0）；
 * 左侧文字同样可自定义位置与宽高（{@code brandLeft/Top/Width/Height}，
 * 四列要么都有值、要么都为 NULL = 跟随系统默认版式）；
 * {@code brandOpacity} 控制左侧文字区白色底板的透明度（0 = 不透明，100 = 完全透明，NULL = 100）。
 * 桌面启动台自动跳转秒数落在 {@code gopageSeconds}。</p>
 */
public class LoginConfig {

    private Integer id;
    /** 账号密码登录：0 关 / 1 开。 */
    private Integer passwordEnable;
    /** 验证登录·手机号（验证码由本模块自管、短信走平台标准能力）：0 关 / 1 开。 */
    private Integer mobileEnable;
    /** 验证登录·邮箱（验证码由本模块自管、邮件走平台标准能力）：0 关 / 1 开。 */
    private Integer emailEnable;
    /** 企业微信扫码登录（本系统自己生成的二维码）：0 关 / 1 开。 */
    private Integer wecomQrEnable;
    /** 钉钉扫码登录：0 关 / 1 开（仅保留配置项，尚未接入）。 */
    private Integer dingtalkQrEnable;
    /** 飞书扫码登录：0 关 / 1 开（对应列 {@code feishu_qr_enable}）。 */
    private Integer feishuQrEnable;
    /** 个人微信登录：0 关 / 1 开（使用微信开放平台官方二维码）。 */
    private Integer personalWxEnable;
    /** 扫码二维码有效期（秒）；空/0 时用 oaones.appsyn.properties 的 login.qr.ttl。 */
    private Integer qrTtlSeconds;
    /**
     * 登录页 logo 的站内地址（页面上传后落 {@code /oaones/appsyn/img/loginlogo_xxx.png}）；空 = 登录页不显示 logo。
     */
    private String logo;
    /**
     * 登录页**整页背景图**的站内地址（页面上传后落 {@code /oaones/appsyn/img/loginbg_xxx.png}）；
     * 空 = 用系统默认底色（{@code --oa-g-50}）。
     */
    private String bgImage;
    /**
     * 登录页**左侧**的富文本（HTML）：非空时左侧就按它显示（设置什么显示什么），
     * 空 = 用系统默认的左侧说明（标题 + 已开启登录方式的清单）。
     */
    private String brandHtml;
    /**
     * 登录框与左侧文字共用的定位方式：0 = 视口左上角（历史默认），1 = 相对视口中心的偏移。
     * 中心模式下左 / 上字段分别表示区域中心相对视口中心的 X / Y 偏移，允许负数。
     */
    private Integer loginLayoutMode;
    /**
     * 登录框 X（CSS 像素）：左上角模式下为左边缘，中心模式下为区域中心相对视口中心的水平偏移；
     * 四列都为空 = 默认居中布局。
     * 坐标允许 0，因此“未设置”统一用 {@code null} 表示，不能用 0 代替。
     */
    private Integer loginBoxLeft;
    /** 登录框 Y（CSS 像素）：左上角模式下为上边缘，中心模式下为区域中心相对视口中心的垂直偏移。 */
    private Integer loginBoxTop;
    /** 登录框宽度（px）；空 = 默认宽度。 */
    private Integer loginBoxWidth;
    /** 登录框高度（px，内容超出时在登录框内滚动）；空 = 高度随内容。 */
    private Integer loginBoxHeight;
    /**
     * 登录框背景透明度（0~100，越大越透明）：只影响登录框的白色底色，
     * 文字、输入框与按钮保持不透明；NULL = 0（完全不透明，与历史配置一致）。
     */
    private Integer loginBoxOpacity;
    /**
     * 登录页左侧文字（{@code .login-brand}）X（CSS 像素）：与登录框共用 {@link #loginLayoutMode}；
     * 左上角模式表示左边缘，中心模式表示区域中心相对视口中心的水平偏移。
     * 四列都为空 = 跟随系统默认版式。坐标允许 0，因此“未设置”统一用 {@code null}。
     */
    private Integer brandLeft;
    /** 登录页左侧文字 Y（CSS 像素）：左上角模式表示上边缘，中心模式表示中心点的垂直偏移。 */
    private Integer brandTop;
    /** 登录页左侧文字宽度（px）；空 = 跟随系统默认版式。 */
    private Integer brandWidth;
    /** 登录页左侧文字高度（px，内容超出时在文字区内滚动）；空 = 跟随系统默认版式。 */
    private Integer brandHeight;
    /**
     * 登录页左侧文字背景透明度（0~100，越大越透明）：只影响文字区的白色底板，
     * 文字保持不透明；NULL = 100（历史无底板外观）。
     */
    private Integer brandOpacity;
    /**
     * 桌面启动台（/oaones/appsyn/desktop）带 gopage 时自动跳转的秒数：
     * 0 = 不自动跳转；空 = 用 oaones.appsyn.properties#login.landing.gopage.seconds（默认 5 秒）。
     */
    private Integer gopageSeconds;
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

    public Integer getFeishuQrEnable() { return feishuQrEnable; }
    public void setFeishuQrEnable(Integer feishuQrEnable) { this.feishuQrEnable = feishuQrEnable; }

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

    public Integer getLoginLayoutMode() { return loginLayoutMode; }
    public void setLoginLayoutMode(Integer loginLayoutMode) { this.loginLayoutMode = loginLayoutMode; }

    public Integer getLoginBoxLeft() { return loginBoxLeft; }
    public void setLoginBoxLeft(Integer loginBoxLeft) { this.loginBoxLeft = loginBoxLeft; }

    public Integer getLoginBoxTop() { return loginBoxTop; }
    public void setLoginBoxTop(Integer loginBoxTop) { this.loginBoxTop = loginBoxTop; }

    public Integer getLoginBoxWidth() { return loginBoxWidth; }
    public void setLoginBoxWidth(Integer loginBoxWidth) { this.loginBoxWidth = loginBoxWidth; }

    public Integer getLoginBoxHeight() { return loginBoxHeight; }
    public void setLoginBoxHeight(Integer loginBoxHeight) { this.loginBoxHeight = loginBoxHeight; }

    public Integer getLoginBoxOpacity() { return loginBoxOpacity; }
    public void setLoginBoxOpacity(Integer loginBoxOpacity) { this.loginBoxOpacity = loginBoxOpacity; }

    public Integer getBrandLeft() { return brandLeft; }
    public void setBrandLeft(Integer brandLeft) { this.brandLeft = brandLeft; }

    public Integer getBrandTop() { return brandTop; }
    public void setBrandTop(Integer brandTop) { this.brandTop = brandTop; }

    public Integer getBrandWidth() { return brandWidth; }
    public void setBrandWidth(Integer brandWidth) { this.brandWidth = brandWidth; }

    public Integer getBrandHeight() { return brandHeight; }
    public void setBrandHeight(Integer brandHeight) { this.brandHeight = brandHeight; }

    public Integer getBrandOpacity() { return brandOpacity; }
    public void setBrandOpacity(Integer brandOpacity) { this.brandOpacity = brandOpacity; }

    public Integer getGopageSeconds() { return gopageSeconds; }
    public void setGopageSeconds(Integer gopageSeconds) { this.gopageSeconds = gopageSeconds; }

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
