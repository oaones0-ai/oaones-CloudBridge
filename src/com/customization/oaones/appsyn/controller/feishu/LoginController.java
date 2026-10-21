package com.customization.oaones.appsyn.controller.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesSso;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.impl.feishu.LoginServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.LoginConfigServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.LoginSmsServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.ScanLoginServiceImpl;
import com.customization.oaones.appsyn.service.feishu.LoginConfigService;
import com.customization.oaones.appsyn.service.feishu.LoginService;
import com.customization.oaones.appsyn.service.feishu.LoginSmsService;
import com.customization.oaones.appsyn.service.feishu.ScanLoginService;
import weaver.general.Util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 自定义登录页控制器（action 前缀 {@code login.}）。
 *
 * <p>与模块内其它控制器最大的不同：<b>登录页、扫码落地页、PC 轮询都没有 OA 登录会话</b>，
 * 因此不能走 {@code /oaones/Feishu/api.jsp}（那里会先做登录校验、未登录直接返回 401），
 * 而是由统一登录页自己的入口 {@code /oaones/login/loginAction.jsp}（PC 页面的 AJAX 入口，
 * 见 {@link com.customization.oaones.appsyn.controller.LoginController}）与
 * {@code /oaones/login/scan.jsp}（统一扫码落地/确认页）直接调用。</p>
 *
 * <p><b>扫码登录已经合并</b>：登录页只有一个页签、一张二维码（三个平台共用），
 * PC 侧接口统一由公共的
 * {@link com.customization.oaones.appsyn.controller.LoginController} 提供；
 * 本类保留的 {@link #dispatch} 等 PC 侧能力只为兼容 {@code /oaones/Feishu/api.jsp} 里
 * {@code login.*} 的老路由，**手机侧**的 {@link #scanStep} / {@link #scanConfirm}
 * 才是本连接器自己的换身份逻辑（统一落地页识别到飞书客户端时分派到这里；
 * 旧的 {@code /oaones/login/Feishu/scan.jsp} 现在只做 302 跳到统一落地页）。</p>
 *
 * <p><b>二维码是本系统自己生成的</b>（见 {@link ScanLoginService}）：
 * 二维码内容只是本站的扫码落地页 + 票据，扫码方（飞书客户端）打开后
 * 由 OA 侧判断平台、依次用它对接的租户配置换成员身份，因此一张二维码覆盖多个飞书租户；
 * 登录会话只在 <b>PC 的请求</b>里写入（{@code login.qrLogin}），
 * 手机端只负责“确认”，保证 Cookie 一定落在发起登录的电脑上。</p>
 */
public class LoginController extends BaseController {

    private LoginService loginService() {
        return new LoginServiceImpl();
    }

    private ScanLoginService scanLoginService() {
        return new ScanLoginServiceImpl();
    }

    private LoginConfigService loginConfigService() {
        return new LoginConfigServiceImpl();
    }

    private LoginSmsService loginSmsService() {
        return new LoginSmsServiceImpl();
    }

    /**
     * PC 页面 AJAX 统一入口（由 {@code loginAction.jsp} 调用）。
     *
     * <p>需要写登录会话的 {@code login.qrLogin} 也走这里，因为它是 PC 发起的请求——
     * Set-Cookie 只有写在发起登录的浏览器请求上才有效。</p>
     */
    public JSONObject dispatch(HttpServletRequest request, HttpServletResponse response) {
        String action = request.getParameter("action") == null ? "" : request.getParameter("action").trim();
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
                requireQrEnabled();
                JSONObject data = scanLoginService().create(
                        req(request, "browser_key"),
                        OaonesSso.siteBase(request),
                        landingUrl(req(request, "gopage")),
                        Util.getIpAddr(request),
                        OaonesUtil.s(request.getHeader("User-Agent")));
                return OaonesUtil.ok(data);
            }
            if ("login.qrStatus".equals(action)) {
                requireQrEnabled();
                return OaonesUtil.ok(scanLoginService().status(req(request, "ticket"),
                        req(request, "browser_key")));
            }
            if ("login.qrLogin".equals(action)) {
                requireQrEnabled();
                // PC 用自己的请求带一次性令牌换登录会话（不能用手机端请求写会话）
                JSONObject data = scanLoginService().take(req(request, "ticket"),
                        req(request, "browser_key"), req(request, "token"));
                int oaUserId = OaonesUtil.i(OaonesUtil.nullToEmpty(data.getString("oa_user_id")), 0);
                String source = "扫码登录：" + OaonesUtil.s(data.getString("platform_label"))
                        + "成员「" + OaonesUtil.s(data.getString("out_user_name"))
                        + (OaonesUtil.s(data.getString("out_user_name")).length() == 0
                            ? OaonesUtil.s(data.getString("out_user_id")) : "")
                        + "」(飞书租户 " + OaonesUtil.s(data.getString("cfg_id")) + ")";
                OaonesSso.login(request, response, oaUserId, source);
                return OaonesUtil.ok(data);
            }
            if ("login.qrCancel".equals(action)) {
                return OaonesUtil.ok(scanLoginService().cancel(req(request, "ticket"),
                        req(request, "browser_key")));
            }
            return OaonesUtil.fail("未知 action: " + action);
        } catch (Exception e) {
            OaonesLog.error("登录页 action 处理失败 action=" + action, e);
            return OaonesUtil.fail(e.getMessage() == null ? "处理失败" : e.getMessage());
        }
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

    /** 手机号登录开关关闭时拒绝发码/登录（页面已隐藏入口，这里是服务端兜底）。 */
    private void requireMobileEnabled() {
        if (loginConfigService().publicSettings().getIntValue("mobile_enable") != 1) {
            throw new RuntimeException("手机号登录已在“统一登录设置”里关闭，无法使用");
        }
    }

    /** 邮箱验证登录开关关闭时拒绝发码/登录（页面已隐藏该验证方式，这里是服务端兜底）。 */
    private void requireEmailEnabled() {
        if (loginConfigService().publicSettings().getIntValue("email_enable") != 1) {
            throw new RuntimeException("邮箱验证登录已在“统一登录设置”里关闭，无法使用");
        }
    }

    /** 扫码登录开关关闭时拒绝生成/查询二维码（页面已隐藏入口，这里是服务端兜底）。 */
    private void requireQrEnabled() {
        // 飞书登录页读的是“飞书扫码登录”开关（feishu_qr_enable）
        if (loginConfigService().publicSettings().getIntValue("feishu_qr_enable") != 1) {
            throw new RuntimeException("飞书扫码登录已在“登录设置”里关闭，无法使用");
        }
    }

    /**
     * 模块统一入口（{@code OaonesController} 路由 {@code login.} 前缀时调用）。
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
     * <p>返回的 {@code mode} 供页面渲染：{@code oauth} 跳授权 / {@code confirm} 确认页 /
     * {@code done} 已完成 / {@code failed} 失败 / {@code unsupported} 客户端不支持 /
     * {@code closed} 票据已过期或已用。</p>
     */
    public JSONObject scanStep(HttpServletRequest request) {
        try {
            // 飞书回跳时把上下文放在 state 里（票据/平台/第几家企业），
            // 页面参数作为兜底，两者取先有值的那个
            JSONObject state = ScanLoginServiceImpl.parseScanState(req(request, "state"));
            String ticket = firstNonEmpty(state.getString("ticket"), req(request, "t"));
            String platform = firstNonEmpty(state.getString("platform"), req(request, "p"));
            String cfgId = firstNonEmpty(state.getString("cfg_id"), req(request, "c"));
            // 飞书网页授权回跳带回 code（个别入口给的是 authCode），两个都收
            String authCode = firstNonEmpty(req(request, "code"), req(request, "authCode"));
            JSONObject data = scanLoginService().scanStep(ticket,
                    platform, cfgId, authCode,
                    OaonesSso.siteBase(request), Util.getIpAddr(request),
                    OaonesUtil.s(request.getHeader("User-Agent")));
            return OaonesUtil.ok(data);
        } catch (Exception e) {
            OaonesLog.error("扫码登录落地处理失败", e);
            JSONObject data = new JSONObject();
            data.put("mode", "failed");
            data.put("msg", e.getMessage() == null ? "扫码登录失败，请回到电脑上刷新二维码重试" : e.getMessage());
            return OaonesUtil.ok(data);
        }
    }

    /** 手机端确认/取消（由 {@code scan.jsp} 的按钮调用）。 */
    public JSONObject scanConfirm(HttpServletRequest request) {
        try {
            JSONObject state = ScanLoginServiceImpl.parseScanState(req(request, "state"));
            String ticket = firstNonEmpty(state.getString("ticket"), req(request, "t"));
            JSONObject data = scanLoginService().scanConfirm(ticket,
                    firstNonEmpty(req(request, "act"), "confirm"));
            return OaonesUtil.ok(data);
        } catch (Exception e) {
            OaonesLog.error("扫码登录确认失败", e);
            JSONObject data = new JSONObject();
            data.put("mode", "failed");
            data.put("msg", e.getMessage() == null ? "确认失败，请重新扫码" : e.getMessage());
            return OaonesUtil.ok(data);
        }
    }

    /** 登录页“有没有可用扫码租户”的查询（页面据此决定显示二维码还是提示）。 */
    private JSONObject tenantsData() {
        JSONArray tenants = loginService().tenants();
        JSONObject data = new JSONObject();
        data.put("tenants", tenants);
        data.put("default_cfg_id", defaultCfgId(tenants));
        data.put("configured_cfg_id", OaonesUtil.s(OaonesProps.fsLoginCfgId()));
        data.put("landing_url", landingUrl(""));
        data.put("need_confirm", OaonesProps.loginQrConfirm() == 1);
        return OaonesUtil.ok(data);
    }

    /** 默认租户：properties 里指定的优先，其次第一个可扫码的租户。 */
    private String defaultCfgId(JSONArray tenants) {
        String configured = OaonesUtil.s(OaonesProps.fsLoginCfgId()).trim();
        String firstUsable = "";
        for (int i = 0; i < tenants.size(); i++) {
            JSONObject item = tenants.getJSONObject(i);
            String cfgId = OaonesUtil.nullToEmpty(item.getString("cfg_id"));
            if (configured.length() > 0 && configured.equals(cfgId)) {
                return cfgId;
            }
            if (firstUsable.length() == 0 && item.getBooleanValue("usable")) {
                firstUsable = cfgId;
            }
        }
        return firstUsable;
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

    private String firstNonEmpty(String first, String second) {
        String a = OaonesUtil.s(first).trim();
        return a.length() > 0 ? a : OaonesUtil.s(second).trim();
    }
}
