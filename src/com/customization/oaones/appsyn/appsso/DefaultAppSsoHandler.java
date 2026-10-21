package com.customization.oaones.appsyn.appsso;

/**
 * 默认（直连）单点登录实现类：不做任何身份认证，直接返回应用默认地址。
 *
 * <p>作用是给应用列表留一个「可填、可跑通」的兜底：维护人在
 * {@code sso_class} 里填本类名时，桌面点击该应用的表现与“留空”完全一致
 * （直接打开 {@code app_url}），便于先用默认实现验证链路，
 * 之后再换成客户自己的实现类。</p>
 */
public class DefaultAppSsoHandler implements AppSsoHandler {

    @Override
    public String loginUrl(AppSsoContext ctx) throws Exception {
        return ctx == null ? "" : ctx.getAppUrl();
    }

    @Override
    public String describe() {
        return "默认直连：不做身份认证，直接打开应用默认地址";
    }
}
