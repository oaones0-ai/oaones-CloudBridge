package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesSso;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.QrLoginPlatform;
import com.customization.oaones.appsyn.common.ScanLoginState;
import com.customization.oaones.appsyn.service.PersonalWxService;
import com.customization.oaones.appsyn.service.impl.PersonalWxServiceImpl;
import com.customization.oaones.appsyn.service.impl.wecom.LoginConfigServiceImpl;
import com.customization.oaones.appsyn.service.impl.wecom.LoginServiceImpl;
import com.customization.oaones.appsyn.service.impl.wecom.LoginSmsServiceImpl;
import com.customization.oaones.appsyn.service.impl.wecom.ScanLoginServiceImpl;
import com.customization.oaones.appsyn.service.wecom.LoginConfigService;
import com.customization.oaones.appsyn.service.wecom.LoginSmsService;
import com.customization.oaones.appsyn.service.wecom.ScanLoginService;
import weaver.general.Util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 统一登录页控制器（action 前缀 {@code login.}）。
 *
 * <p><b>一个页面、四种登录方式</b>：统一登录页
 * {@code /oaones/appsyn/login/login.jsp} 同时提供
 * <b>账号密码登录</b> / <b>验证登录</b>（页签内可切手机号 / 邮箱两种验证方式）/
 * <b>企业应用扫码</b> / <b>个人微信登录</b>；企业微信 / 钉钉 / 飞书客户端共用一张本站二维码，
 * 个人微信使用独立的微信官方二维码，但四者共用同一套票据、轮询与一次性登录令牌
 * （见 {@link QrLoginPlatform}）。</p>
 *
 * <p>与模块内其它控制器最大的不同：<b>登录页、扫码落地页、PC 轮询都没有 OA 登录会话</b>，
 * 因此不能走 {@code /oaones/appsyn/{WeCom,DingTalk,Feishu}/api.jsp}（那里会先做登录校验、未登录直接返回 401），
 * 而是由 {@code /oaones/appsyn/login/loginAction.jsp}（PC 页面的 AJAX 入口）与
 * {@code /oaones/appsyn/login/scan.jsp}（手机扫码落地/确认页）直接调用本类。</p>
 *
 * <p>分层口径：</p>
 * <ul>
 *   <li><b>PC 侧</b>（登录设置 / 手机验证码 / 票据生成与轮询）是三个连接器**共用**的一件事
 *       （一行共享配置 + 一张共享票据表 {@code uf_oaones_appsyn_scan_login}），
 *       因此沿用企微连接器的实现（与 {@code /oaones/appsyn/{WeCom,DingTalk,Feishu}/api.jsp}、{@code loginConfig/config.jsp} 同一口径），
 *       不在本页另写一套；</li>
 *   <li><b>手机侧</b>（换成员身份）必须由**扫码客户端对应的连接器**来做：三个连接器的租户范围与
 *       应用凭证是分开的，跨连接器换身份会把 A 平台的 Secret 用到 B 平台接口上。
 *       本类只负责“按客户端分派”，不复制各连接器的换取身份逻辑。</li>
 * </ul>
 */
public class LoginController extends BaseController {

    private LoginConfigService loginConfigService() {
        return new LoginConfigServiceImpl();
    }

    private ScanLoginService scanLoginService() {
        return new ScanLoginServiceImpl();
    }

    private LoginSmsService loginSmsService() {
        return new LoginSmsServiceImpl();
    }

    private PersonalWxService personalWxService() {
        return new PersonalWxServiceImpl();
    }

    /**
     * PC 页面 AJAX 统一入口（由 {@code loginAction.jsp} 调用）。
     *
     * <p>需要写登录会话的 {@code login.qrLogin} 也走这里，因为它是 PC 发起的请求——
     * Set-Cookie 只有写在发起登录的浏览器请求上才有效。</p>
     */
    public JSONObject dispatch(HttpServletRequest request, HttpServletResponse response) {
        String action = req(request, "action");
        try {
            if ("login.tenants".equals(action)) {
                return tenantsData();
            }
            if ("login.settings".equals(action)) {
                // 登录页按统一登录设置决定显示哪些登录方式（未登录也能取，只回公开部分）
                return OaonesUtil.ok(loginConfigService().publicSettings());
            }
            if ("login.smsSend".equals(action)) {
                // 手机号登录第 1 步：发送手机验证码（本模块自管的验证码 + 平台标准短信发送）
                requireMobileEnabled();
                return OaonesUtil.ok(loginSmsService().sendCode(req(request, "mobile"),
                        Util.getIpAddr(request), OaonesUtil.s(request.getHeader("User-Agent"))));
            }
            if ("login.smsLogin".equals(action)) {
                // 手机号登录第 2 步：校验验证码后在本请求里写登录会话
                requireMobileEnabled();
                return smsLogin(request, response);
            }
            if ("login.emailSend".equals(action)) {
                // 邮箱验证登录第 1 步：发送邮箱验证码（本模块自管的验证码 + 平台标准邮件发送）
                requireEmailEnabled();
                return OaonesUtil.ok(loginSmsService().sendEmailCode(req(request, "email"),
                        Util.getIpAddr(request), OaonesUtil.s(request.getHeader("User-Agent"))));
            }
            if ("login.emailLogin".equals(action)) {
                // 邮箱验证登录第 2 步：校验验证码后在本请求里写登录会话
                requireEmailEnabled();
                return emailLogin(request, response);
            }
            if ("login.qrCreate".equals(action)) {
                requireEnterpriseQrEnabled();
                // 二维码内容 = 站点地址 + login.scan.path（统一扫码落地页）+ 票据；
                // 与平台无关，所以一张码三个客户端都能扫
                JSONObject data = scanLoginService().create(
                        req(request, "browser_key"),
                        OaonesSso.siteBase(request),
                        landingUrl(req(request, "gopage")),
                        Util.getIpAddr(request),
                        OaonesUtil.s(request.getHeader("User-Agent")));
                return OaonesUtil.ok(data);
            }
            if ("login.personalwxQrCreate".equals(action)) {
                requirePersonalWxEnabled();
                // 个人微信必须使用微信官方 qrconnect 二维码；票据仍复用统一扫码登录表，
                // 因此轮询、确认、一次性令牌与 PC 写会话都和三个企业应用平台一致。
                JSONObject ticketData = scanLoginService().create(
                        req(request, "browser_key"),
                        OaonesSso.siteBase(request),
                        landingUrl(req(request, "gopage")),
                        Util.getIpAddr(request),
                        OaonesUtil.s(request.getHeader("User-Agent")));
                String ticket = OaonesUtil.s(ticketData.getString("ticket"));
                JSONObject wx = personalWxService().loginQrStart(
                        ticket, OaonesSso.siteBase(request));
                ticketData.put("auth_url", OaonesUtil.s(wx.getString("auth_url")));
                return OaonesUtil.ok(ticketData);
            }
            if ("login.qrStatus".equals(action)) {
                requireScanEnabled();
                return OaonesUtil.ok(scanLoginService().status(req(request, "ticket"),
                        req(request, "browser_key")));
            }
            if ("login.qrLogin".equals(action)) {
                requireScanEnabled();
                // PC 用自己的请求带一次性令牌换登录会话（不能用手机端请求写会话）
                JSONObject data = scanLoginService().take(req(request, "ticket"),
                        req(request, "browser_key"), req(request, "token"));
                int oaUserId = OaonesUtil.i(OaonesUtil.nullToEmpty(data.getString("oa_user_id")), 0);
                String platformLabel = OaonesUtil.s(data.getString("platform_label"));
                String platform = OaonesUtil.s(data.getString("platform"));
                String cfgId = OaonesUtil.s(data.getString("cfg_id"));
                String source = "扫码登录："
                        + (platformLabel.length() == 0 ? "" : platformLabel)
                        + "成员「" + OaonesUtil.s(data.getString("out_user_name"))
                        + (OaonesUtil.s(data.getString("out_user_name")).length() == 0
                            ? OaonesUtil.s(data.getString("out_user_id")) : "")
                        + "」";
                if (!QrLoginPlatform.PERSONAL_WX.equals(platform) && cfgId.length() > 0) {
                    source += "(租户 " + cfgId + ")";
                }
                OaonesSso.login(request, response, oaUserId, source);
                return OaonesUtil.ok(data);
            }
            if ("login.qrCancel".equals(action)) {
                return OaonesUtil.ok(scanLoginService().cancel(req(request, "ticket"),
                        req(request, "browser_key")));
            }
            return OaonesUtil.fail("未知 action: " + action);
        } catch (Exception e) {
            OaonesLog.error("统一登录页 action 处理失败 action=" + action, e);
            return OaonesUtil.fail(e.getMessage() == null ? "处理失败" : e.getMessage());
        }
    }

    /**
     * 模块统一入口（经 {@code /oaones/appsyn/{WeCom,DingTalk,Feishu}/api.jsp} 调用时的老口径）。
     *
     * <p>这里没有 response，{@code login.qrLogin}（要写会话）在模块内调用时不受支持，
     * 其余 action 与 {@link #dispatch} 同一口径。</p>
     */
    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId) {
        if ("login.qrLogin".equals(action)) {
            return OaonesUtil.fail("扫码登录完成动作只能由登录页自己的入口调用（需要写登录会话）");
        }
        return dispatch(request, null);
    }

    /**
     * 手机扫码落地/授权回跳（由 {@code scan.jsp} 调用）。
     *
     * <p>按扫码客户端把请求交给**对应连接器**的落地逻辑：企业微信 → 企微连接器、
     * 钉钉 → 钉钉连接器、飞书 → 飞书连接器（各连接器只用自己的租户与应用凭证换身份）。
     * 客户端识别不出来时给出统一提示，不猜平台。</p>
     */
    public JSONObject scanStep(HttpServletRequest request) {
        String platform = scanPlatform(request);
        if (platform.length() == 0) {
            // 文案里的客户端名单只列“登录设置”里**已开启扫码**的平台（后台没启用飞书就不提飞书）；
            // 企业应用与个人微信都不可用时扫码登录本身是关闭的，直接说明，而不是列出用不了的客户端。
            // 页面用 Util.toHtml 转义后再显示，这里只写纯文本（不要带 HTML 标签）
            String clients = QrLoginPlatform.enabledLabelText(loginConfigService().publicSettings());
            return OaonesUtil.ok(mode("unsupported", clients.length() == 0
                    ? "扫码登录已在“登录设置”里关闭，请回到电脑上改用账号密码 / 手机号验证登录。"
                    : "请用本人的" + clients + "扫一扫打开本页；"
                    + "普通相机、微信扫一扫无法完成登录。"));
        }
        try {
            requirePlatformEnabled(platform);
        } catch (Exception e) {
            OaonesLog.step("统一扫码落地被拒（平台开关已关闭） platform=" + platform
                    + " msg=" + OaonesUtil.s(e.getMessage()));
            return OaonesUtil.ok(mode("failed", e.getMessage()));
        }
        if (QrLoginPlatform.DINGTALK.equals(platform)) {
            return new com.customization.oaones.appsyn.controller.dingtalk.LoginController().scanStep(request);
        }
        if (QrLoginPlatform.FEISHU.equals(platform)) {
            return new com.customization.oaones.appsyn.controller.feishu.LoginController().scanStep(request);
        }
        if (QrLoginPlatform.PERSONAL_WX.equals(platform)) {
            JSONObject state = ScanLoginState.parse(req(request, "state"));
            String ticket = OaonesUtil.s(state.getString("ticket")).trim();
            if (ticket.length() == 0) {
                ticket = req(request, "t");
            }
            try {
                return OaonesUtil.ok(personalWxService().loginScanStep(ticket,
                        req(request, "code"), OaonesSso.siteBase(request),
                        Util.getIpAddr(request),
                        OaonesUtil.s(request.getHeader("User-Agent"))));
            } catch (Exception e) {
                OaonesLog.error("个人微信扫码登录落地处理失败", e);
                return OaonesUtil.ok(mode("failed",
                        e.getMessage() == null ? "个人微信登录失败，请重新扫码" : e.getMessage()));
            }
        }
        return new com.customization.oaones.appsyn.controller.wecom.LoginController().scanStep(request);
    }

    /** 手机端确认/取消（由 {@code scan.jsp} 的按钮调用）：与落地页同一套平台分派。 */
    public JSONObject scanConfirm(HttpServletRequest request) {
        String platform = scanPlatform(request);
        if (platform.length() == 0) {
            return OaonesUtil.ok(mode("failed", "无法识别扫码客户端，请重新扫码"));
        }
        if (QrLoginPlatform.DINGTALK.equals(platform)) {
            return new com.customization.oaones.appsyn.controller.dingtalk.LoginController().scanConfirm(request);
        }
        if (QrLoginPlatform.FEISHU.equals(platform)) {
            return new com.customization.oaones.appsyn.controller.feishu.LoginController().scanConfirm(request);
        }
        return new com.customization.oaones.appsyn.controller.wecom.LoginController().scanConfirm(request);
    }

    /**
     * 本次请求的扫码平台：优先按 User-Agent 判断（扫码方一定是平台客户端），
     * 认不出来时用授权回调的 {@code state} / 页面参数 {@code p} 兜底。
     */
    private String scanPlatform(HttpServletRequest request) {
        String platform = OaonesUtil.s(QrLoginPlatform.detect(request.getHeader("User-Agent")));
        if (QrLoginPlatform.known(platform)) {
            return platform;
        }
        JSONObject state = ScanLoginState.parse(req(request, "state"));
        platform = OaonesUtil.s(state.getString("platform"));
        if (!QrLoginPlatform.known(platform)) {
            platform = OaonesUtil.s(req(request, "p"));
        }
        return QrLoginPlatform.known(platform) ? platform : "";
    }

    /**
     * 手机号登录：校验手机验证码 → 手机号换唯一 OA 账号 → 写入登录会话。
     *
     * <p>会话必须在**发起登录的这台电脑**的请求里写（手机浏览器上不写会话），
     * 与扫码登录同一口径；验证码的发送与校验都复用 OA 既有能力（含冷却与错误次数限制）。</p>
     */
    private JSONObject smsLogin(HttpServletRequest request, HttpServletResponse response) {
        String mobile = req(request, "mobile");
        JSONObject checked = loginSmsService().checkCode(mobile, req(request, "code"),
                Util.getIpAddr(request));
        if (!"0".equals(OaonesUtil.s(checked.getString("status")))) {
            // 既有能力的状态口径：-1 验证失败、-2 超过尝试次数/已超时
            return OaonesUtil.fail(OaonesUtil.s(checked.getString("info")).length() == 0
                    ? "手机验证码校验未通过" : OaonesUtil.s(checked.getString("info")));
        }
        int oaUserId = loginSmsService().requireOaUserByMobile(mobile);
        OaonesSso.login(request, response, oaUserId,
                "手机号登录：手机验证码校验通过（" + mobileTail(mobile) + "）");
        JSONObject data = new JSONObject();
        data.put("oa_user_id", String.valueOf(oaUserId));
        data.put("landing_url", landingUrl(req(request, "gopage")));
        return OaonesUtil.ok(data);
    }

    /** 手机号只留后 4 位（日志与登录来源说明里不落全号）。 */
    private String mobileTail(String mobile) {
        String value = OaonesUtil.s(mobile).trim();
        return value.length() <= 4 ? value : "****" + value.substring(value.length() - 4);
    }

    /**
     * 邮箱验证登录：校验邮箱验证码 → 邮箱换唯一 OA 账号 → 写入登录会话。
     *
     * <p>与手机号登录完全同一口径（会话只在发起登录的电脑的请求里写），
     * 只是第一步的信道换成邮箱、第二步按 {@code hrmresource.email} 定位账号。</p>
     */
    private JSONObject emailLogin(HttpServletRequest request, HttpServletResponse response) {
        String email = req(request, "email");
        JSONObject checked = loginSmsService().checkEmailCode(email, req(request, "code"),
                Util.getIpAddr(request));
        if (!"0".equals(OaonesUtil.s(checked.getString("status")))) {
            // 既有能力的状态口径：-1 验证失败、-2 超过尝试次数/已超时
            return OaonesUtil.fail(OaonesUtil.s(checked.getString("info")).length() == 0
                    ? "邮箱验证码校验未通过" : OaonesUtil.s(checked.getString("info")));
        }
        int oaUserId = loginSmsService().requireOaUserByEmail(email);
        OaonesSso.login(request, response, oaUserId,
                "邮箱登录：邮箱验证码校验通过（" + emailTail(email) + "）");
        JSONObject data = new JSONObject();
        data.put("oa_user_id", String.valueOf(oaUserId));
        data.put("landing_url", landingUrl(req(request, "gopage")));
        return OaonesUtil.ok(data);
    }

    /** 邮箱脱敏：只留首字符与域名（日志与登录来源说明里不落完整地址）。 */
    private String emailTail(String email) {
        String value = OaonesUtil.s(email).trim();
        int at = value.indexOf('@');
        if (at <= 0) {
            return value;
        }
        String name = value.substring(0, at);
        return (name.length() <= 1 ? name : name.substring(0, 1) + "***") + value.substring(at);
    }

    /**
     * 登录页“扫码登录当前能支持哪些平台 / 各有多少家可用租户”的查询。
     *
     * <p>统一的扫码页签只在“至少有一个平台可用”时展示二维码；这里把每个平台的
     * 开关状态与可用租户数一起回给页面，页面据此给出可执行的提示（哪家没配好、该找谁）。</p>
     */
    private JSONObject tenantsData() {
        JSONObject settings = loginConfigService().publicSettings();
        JSONArray platforms = new JSONArray();
        int enabledCount = 0;
        int usableTotal = 0;
        for (String platform : QrLoginPlatform.all()) {
            String label = QrLoginPlatform.label(platform);
            boolean enabled = settings.getIntValue(QrLoginPlatform.enableKey(platform)) == 1;
            JSONArray tenants = enabled ? tenantsOf(platform) : new JSONArray();
            JSONArray names = new JSONArray();
            int usable = 0;
            String reason = "";
            for (int i = 0; i < tenants.size(); i++) {
                JSONObject tenant = tenants.getJSONObject(i);
                if (tenant.getBooleanValue("usable")) {
                    usable++;
                    names.add(OaonesUtil.s(tenant.getString("cfg_name")));
                } else if (reason.length() == 0) {
                    reason = OaonesUtil.s(tenant.getString("reason"));
                }
            }
            if (usable == 0 && reason.length() == 0) {
                reason = "当前没有启用中的" + label + "租户";
            }
            JSONObject item = new JSONObject();
            item.put("platform", platform);
            item.put("label", label);
            item.put("enabled", enabled);
            item.put("usable_count", usable);
            item.put("names", names);
            item.put("reason", enabled && usable == 0 ? reason : "");
            platforms.add(item);
            if (enabled) {
                enabledCount++;
            }
            usableTotal += usable;
        }
        JSONObject data = new JSONObject();
        data.put("platforms", platforms);
        data.put("scan_enable", enabledCount > 0);
        data.put("usable_total", usableTotal);
        data.put("landing_url", landingUrl(""));
        data.put("need_confirm", OaonesProps.loginQrConfirm() == 1);
        return OaonesUtil.ok(data);
    }

    /** 某平台可用于扫码登录的租户（各连接器自己的租户表；只读、不含 Secret）。 */
    private JSONArray tenantsOf(String platform) {
        if (QrLoginPlatform.DINGTALK.equals(platform)) {
            return new com.customization.oaones.appsyn.service.impl.dingtalk.LoginServiceImpl().tenants();
        }
        if (QrLoginPlatform.FEISHU.equals(platform)) {
            return new com.customization.oaones.appsyn.service.impl.feishu.LoginServiceImpl().tenants();
        }
        return new LoginServiceImpl().tenants();
    }

    /** 手机号登录开关关闭时拒绝发码/登录（页面已隐藏入口，这里是服务端兜底）。 */
    private void requireMobileEnabled() {
        if (loginConfigService().publicSettings().getIntValue("mobile_enable") != 1) {
            throw new RuntimeException("手机号登录已在“登录设置”里关闭，无法使用");
        }
    }

    /** 邮箱验证登录开关关闭时拒绝发码/登录（页面已隐藏该验证方式，这里是服务端兜底）。 */
    private void requireEmailEnabled() {
        if (loginConfigService().publicSettings().getIntValue("email_enable") != 1) {
            throw new RuntimeException("邮箱验证登录已在“登录设置”里关闭，无法使用");
        }
    }

    /** 扫码登录（三个平台都不允许）时拒绝生成/查询二维码（页面已隐藏页签，这里是服务端兜底）。 */
    private void requireScanEnabled() {
        if (!loginConfigService().publicSettings().getBooleanValue("qr_enable")) {
            throw new RuntimeException("扫码登录已在“登录设置”里关闭，无法使用");
        }
    }

    /** 本站企业应用二维码生成：个人微信单开时不能走这里（它使用微信官方二维码）。 */
    private void requireEnterpriseQrEnabled() {
        if (!loginConfigService().publicSettings().getBooleanValue("enterprise_qr_enable")) {
            throw new RuntimeException("企业微信 / 钉钉 / 飞书扫码登录已在“登录设置”里关闭");
        }
    }

    /** 个人微信登录开关：页面已隐藏入口，这里是服务端兜底。 */
    private void requirePersonalWxEnabled() {
        if (!loginConfigService().publicSettings().getBooleanValue("personal_wx_available")) {
            throw new RuntimeException("个人微信登录未开启或尚未维护微信授权密钥（AppID / AppSecret）");
        }
    }

    /**
     * 某个平台被管理员关掉时，扫码的这台手机直接给出原因，
     * 不把请求交给该连接器（与“关闭的方式直接调接口也会被服务端拒绝”同一口径）。
     */
    private void requirePlatformEnabled(String platform) {
        if (loginConfigService().publicSettings()
                .getIntValue(QrLoginPlatform.enableKey(platform)) != 1) {
            throw new RuntimeException("「" + QrLoginPlatform.label(platform)
                    + "」扫码登录已在“登录设置”里关闭，无法用该客户端扫码；"
                    + "请改用其它已开启的登录方式，或联系管理员开启");
        }
    }

    /** 落地地址：优先页面传入的站内路径 / http(s) 网址，其次配置项，最后平台默认主页。 */
    private String landingUrl(String gopage) {
        String target = OaonesSso.safeRedirectUrl(gopage, "");
        if (target.length() > 0) {
            return target;
        }
        String configured = OaonesUtil.s(OaonesProps.loginLandingUrl()).trim();
        if (configured.length() == 0) {
            configured = OaonesConst.DEFAULT_OA_HOME_URL;
        }
        String safe = OaonesSso.safeInternalUrl(configured, "");
        return safe.length() == 0 ? OaonesConst.DEFAULT_OA_HOME_URL : safe;
    }

    /** 手机落地页的状态视图（与各连接器的 mode 口径一致：oauth/choose/confirm/done/failed/unsupported）。 */
    private JSONObject mode(String mode, String msg) {
        JSONObject data = new JSONObject();
        data.put("mode", mode);
        data.put("msg", OaonesUtil.s(msg));
        return data;
    }
}
