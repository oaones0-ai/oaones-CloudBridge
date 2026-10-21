package com.customization.oaones.appsyn.controller.wecom;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesSso;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.impl.wecom.AppBindServiceImpl;
import com.customization.oaones.appsyn.service.wecom.AppBindService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 应用绑定控制器（action 前缀 app.）。
 *
 * <p>本模块维护“企业微信自建应用（企微工作台应用）→ OA 页面”的绑定：
 * 一条记录一个应用入口，支持同一租户绑定多个应用（多应用绑定），
 * 每个应用可分别设置“在企微工作台打开的 OA 页面地址”和“OA 主页地址”。</p>
 *
 * <p>除 AJAX action 外，本类还提供 {@link #entry(HttpServletRequest, HttpServletResponse)}
 * —— 企微工作台应用主页地址的落地入口：由 {@code appEntry.jsp} 直接调用
 * （<b>不</b>经 api.jsp，也<b>不</b>要求 OA 登录会话，因为用户是从企业微信里点开应用的）。
 * 身份由“企业微信网页授权 code + 该应用/租户的 Secret”核实，
 * 绑定关系取人员绑定表中的已绑定记录，核实通过后再由 {@link OaonesSso} 写入登录会话。</p>
 */
public class AppBindController extends BaseController {

    private AppBindService appBindService() {
        return new AppBindServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        // 租户分权：本模块的绑定都挂在某个企微租户下，带 cfg_id 的请求必须由维护人或系统管理员发起
        requireCfgRight(request, operatorId);
        if ("app.list".equals(action)) {
            String cfgId = req(request, "cfg_id");
            if (OaonesUtil.i(cfgId, 0) <= 0) {
                throw new RuntimeException("请先选择企业微信租户");
            }
            JSONObject data = new JSONObject();
            data.put("binds", appBindService().list(cfgId, req(request, "keyword")));
            data.put("entry_base", entryBase(request));
            return OaonesUtil.ok(data);
        }
        if ("app.save".equals(action)) {
            JSONObject params = paramObject(request);
            // 改绑到另一个租户时，原租户也必须有权维护（避免把别人维护的绑定改走）
            assertCfgRight(OaonesUtil.nullToEmpty(appBindService().cfgIdOf(params.getString("id"))),
                    operatorId);
            return OaonesUtil.ok(appBindService().save(params, operatorId));
        }
        if ("app.state".equals(action)) {
            String id = req(request, "id");
            assertCfgRight(OaonesUtil.nullToEmpty(appBindService().cfgIdOf(id)), operatorId);
            return OaonesUtil.ok(appBindService().setState(id, req(request, "bind_state"), operatorId));
        }
        if ("app.delete".equals(action)) {
            String id = req(request, "id");
            assertCfgRight(OaonesUtil.nullToEmpty(appBindService().cfgIdOf(id)), operatorId);
            appBindService().delete(id);
            return OaonesUtil.ok();
        }
        return null;
    }

    /**
     * 企微工作台应用主页地址的免登录入口（由 {@code appEntry.jsp} 直接调用，不经 api.jsp）。
     *
     * <p>流程：定位启用中的绑定 → 企业微信 code 换成员身份 → 人员绑定关系找 OA 账号
     * → 校验 OA 账号 → 写入 OA 登录会话 → 返回落地页地址（由页面 302 跳转）。</p>
     */
    public JSONObject entry(HttpServletRequest request, HttpServletResponse response) {
        String bindId = req(request, "b");
        String cfgId = req(request, "cfg_id");
        String agentId = req(request, "agent_id");
        String code = req(request, "code");
        String state = req(request, "state");
        // 客户端来源：UA 里带 wxwork 就是企业微信客户端；手机端/桌面端由平台标识区分
        String userAgent = request == null ? "" : OaonesUtil.s(request.getHeader("User-Agent"));
        String device = req(request, "device");
        try {
            OaonesLog.step("企微工作台应用免登录入口 b=" + bindId + " cfg_id=" + cfgId
                    + " agent_id=" + agentId + " 有code=" + (OaonesUtil.s(code).length() > 0));
            // 企业微信工作台打开应用时通常会直接带上 code；没带上（或不是从工作台打开）时，
            // 先跳到企业微信网页授权地址换取授权码，再带 code 回到本入口（oauth=1 防循环）。
            if (code.length() == 0 && !"1".equals(req(request, "oauth"))) {
                String redirect = entryUrl(request, bindId, cfgId, agentId, device, true);
                String oauthUrl = appBindService().oauthAuthorizeUrl(bindId, cfgId, agentId, redirect, state);
                JSONObject jump = new JSONObject();
                jump.put("oauth", true);
                jump.put("redirect_url", oauthUrl);
                return OaonesUtil.ok(jump);
            }
            JSONObject data = appBindService().workbenchLogin(bindId, cfgId, agentId, code, state,
                    userAgent, device);
            int oaUserId = OaonesUtil.i(OaonesUtil.nullToEmpty(data.getString("oa_user_id")), 0);
            String source = "企微工作台应用免登录：应用「" + OaonesUtil.s(data.getString("app_name"))
                    + "」(agentid " + OaonesUtil.s(data.getString("agent_id"))
                    + "，企微租户 " + OaonesUtil.s(data.getString("cfg_name")) + ")";
            OaonesSso.login(request, response, oaUserId, source);
            return OaonesUtil.ok(data);
        } catch (Exception e) {
            OaonesLog.error("企微工作台应用免登录失败 b=" + bindId + " cfg_id=" + cfgId
                    + " agent_id=" + agentId, e);
            return OaonesUtil.fail(e.getMessage() == null ? "免登录失败" : e.getMessage());
        }
    }

    /** 应用主页地址前缀（协议+域名+端口 + 入口页路径）；页面据此拼每个应用的主页地址。 */
    private String entryBase(HttpServletRequest request) {
        return OaonesSso.siteBase(request) + OaonesProps.appEntryPath();
    }

    /**
     * 入口页的完整地址（用于企业微信网页授权回跳）。
     *
     * @param markOauthTried 是否带上 {@code oauth=1} 防循环标记：授权回跳时带上，
     *                       若企业微信仍未返回 code，入口页就直接给提示而不是再次跳转
     */
    private String entryUrl(HttpServletRequest request, String bindId, String cfgId, String agentId,
                            String device, boolean markOauthTried) {
        StringBuilder sb = new StringBuilder(entryBase(request)).append('?');
        appendParam(sb, "cfg_id", cfgId);
        appendParam(sb, "agent_id", agentId);
        if (OaonesUtil.s(bindId).length() > 0) {
            appendParam(sb, "b", bindId);
        }
        // 显式指定的客户端类型要带到授权回跳地址上，否则授权回来后只剩 UA 兜底
        if (OaonesUtil.s(device).length() > 0) {
            appendParam(sb, "device", device);
        }
        if (markOauthTried) {
            appendParam(sb, "oauth", "1");
        }
        return sb.toString();
    }

    /** 追加 URL 查询参数（键值都做 URL 编码）。 */
    private void appendParam(StringBuilder sb, String key, String value) {
        try {
            if (sb.charAt(sb.length() - 1) != '?') {
                sb.append('&');
            }
            sb.append(OaonesUtil.urlEncode(key)).append('=').append(OaonesUtil.urlEncode(value));
        } catch (Exception e) {
            OaonesLog.error("拼接免登录入口地址失败 key=" + key, e);
        }
    }
}
