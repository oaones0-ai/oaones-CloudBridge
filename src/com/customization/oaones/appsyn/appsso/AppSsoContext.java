package com.customization.oaones.appsyn.appsso;

/**
 * 调用单点登录实现类时传入的上下文（当前 OA 登录人 + 应用配置）。
 *
 * <p>实现类（{@link AppSsoHandler}）从这里取“谁在访问、要打开哪个应用”，
 * 再按对方系统的要求生成身份凭据（票据 / 签名 / 一次性令牌等）。</p>
 */
public class AppSsoContext {

    /** 应用记录ID（uf_oaones_appsyn_app.id）。 */
    private int appId;
    private String appName;
    /** 应用默认访问地址（实现类可在此基础上追加参数）。 */
    private String appUrl;
    /** 单点登录实现类全限定名（即实现类自身，便于日志）。 */
    private String ssoClass;
    /** 页面维护的“方法说明”（实现类可忽略）。 */
    private String ssoMethod;
    private String remark;

    /** 当前登录的 OA 人员ID。 */
    private int oaUserId;
    /** 当前登录人的 OA 登录名（多数第三方系统的身份匹配字段）。 */
    private String oaLoginId;
    /** 当前登录人姓名。 */
    private String oaUserName;

    /** 站点地址前缀（协议 + 域名 + 端口，不含路径），实现类需要回调地址时用它拼。 */
    private String siteBase;

    public int getAppId() { return appId; }
    public void setAppId(int appId) { this.appId = appId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getAppUrl() { return appUrl; }
    public void setAppUrl(String appUrl) { this.appUrl = appUrl; }

    public String getSsoClass() { return ssoClass; }
    public void setSsoClass(String ssoClass) { this.ssoClass = ssoClass; }

    public String getSsoMethod() { return ssoMethod; }
    public void setSsoMethod(String ssoMethod) { this.ssoMethod = ssoMethod; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public int getOaUserId() { return oaUserId; }
    public void setOaUserId(int oaUserId) { this.oaUserId = oaUserId; }

    public String getOaLoginId() { return oaLoginId; }
    public void setOaLoginId(String oaLoginId) { this.oaLoginId = oaLoginId; }

    public String getOaUserName() { return oaUserName; }
    public void setOaUserName(String oaUserName) { this.oaUserName = oaUserName; }

    public String getSiteBase() { return siteBase; }
    public void setSiteBase(String siteBase) { this.siteBase = siteBase; }
}
