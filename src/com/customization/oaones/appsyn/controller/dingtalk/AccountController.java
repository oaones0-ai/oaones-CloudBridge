package com.customization.oaones.appsyn.controller.dingtalk;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesSso;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.dingtalk.AccountService;
import com.customization.oaones.appsyn.service.impl.dingtalk.AccountServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 个人账号中心控制器（action 前缀 account.）。
 *
 * <p>本页是“本人自助”入口：所有读写都由 Service 按登录人ID（operatorId）限定在本人数据上，
 * 因此<b>不做</b>租户维护人分权校验（任何登录人都应能把本人的 OA 账号与钉钉账号绑定）；
 * 唯一的例外是扫码回调 {@code account.wxScanConfirm}，它由回调页在无登录会话的场景下调用，
 * 发起人身份取自带签名且未过期的 state（见 OaonesScanToken），失败即拒绝。</p>
 */
public class AccountController extends BaseController {

    private AccountService accountService() {
        return new AccountServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("account.info".equals(action)) {
            return OaonesUtil.ok(accountService().info(operatorId));
        }
        if ("account.wxList".equals(action)) {
            return OaonesUtil.ok(accountService().wxList(operatorId));
        }
        if ("account.wxQrStart".equals(action)) {
            return OaonesUtil.ok(accountService().qrStart(
                    req(request, "cfg_id"), operatorId, callbackBase(request)));
        }
        if ("account.wxScanConfirm".equals(action)) {
            // 由回调页直接调用（不经 api.jsp）：身份来自签名 state，因此不传 operatorId
            return OaonesUtil.ok(accountService().scanConfirm(
                    req(request, "code"), req(request, "state")));
        }
        if ("account.wxUnbind".equals(action)) {
            return OaonesUtil.ok(accountService().unbind(req(request, "cfg_id"), operatorId));
        }
        return null;
    }

    /**
     * 扫码回调地址前缀：协议 + 域名 + 端口（不含路径与上下文）。
     *
     * <p>redirect_uri 必须是钉钉后台“可信域名”里的绝对地址；
     * 反向代理/多域名部署时用 oaones.properties 的 account.callback.base 显式指定，
     * 未配置时按浏览器当前访问的地址自动拼（默认端口不带端口号）。</p>
     */
    private String callbackBase(HttpServletRequest request) {
        // 与免登录入口同一口径：properties 里配了就用配的，否则按代理透传头/浏览器访问地址拼
        // （反向代理终止 TLS 时 request.getScheme() 是 http，会拼出钉钉不接受的回调地址）
        String configured = OaonesUtil.s(OaonesProps.ddAccountCallbackBase()).trim();
        while (configured.endsWith("/")) {
            configured = configured.substring(0, configured.length() - 1);
        }
        if (configured.length() > 0) {
            return configured;
        }
        return OaonesSso.siteBase(request);
    }
}
