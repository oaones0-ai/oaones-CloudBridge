package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesSso;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.PersonalWxService;
import com.customization.oaones.appsyn.service.impl.PersonalWxServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 个人微信控制器（action 前缀 {@code pw.}）。
 *
 * <p>两条入口共用本控制器：</p>
 * <ul>
 *   <li><b>个人账号中心 → 个人微信</b>（`/oaones/appsyn/accountCenter/MyAccount.jsp?tab=personalwx`）：
 *       {@code pw.mine} / {@code pw.qrStart}（不带 oa_user_id = 本人）/ {@code pw.unbind}（同上），
 *       本人自助，所有读写都限定在登录会话本人的那一行；</li>
 *   <li><b>微信设置</b>（`/oaones/appsyn/personalWx/config.jsp`，独立一级菜单）：
 *       {@code pw.config} / {@code pw.save} 维护微信授权密钥，
 *       {@code pw.list} 取“一行一个 OA 有效账号（含超管账号）”的绑定列表，
 *       {@code pw.qrStart} / {@code pw.unbind} 带 {@code oa_user_id} 时为员工绑定 / 解绑
 *       （服务端再校验一次维护人权限）。</li>
 * </ul>
 *
 * <p>{@code pw.scanConfirm} 由回调页 {@code /oaones/appsyn/personalWx/PersonalWxCallback.jsp} 直接调用
 * （不经 api.jsp）：扫码回调可能发生在没有登录会话的场景，发起人身份取自带签名且未过期的 state。</p>
 */
public class PersonalWxController extends BaseController {

    private PersonalWxService service() {
        return new PersonalWxServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        // 页面地址上的 showSetting=true（显示“微信设置”这一部分）由页面转成 show_setting 传进来：
        // 不带它时接口只回“密钥是否已配置”，**不下发 AppID / 密钥掩码 / 备注等设置参数**
        boolean showSetting = flag(req(request, "showSetting")) || flag(req(request, "show_setting"));
        if ("pw.config".equals(action)) {
            return OaonesUtil.ok(service().getConfig(operatorId, showSetting));
        }
        if ("pw.save".equals(action)) {
            return OaonesUtil.ok(service().saveConfig(paramObject(request), operatorId, showSetting));
        }
        if ("pw.list".equals(action)) {
            return OaonesUtil.ok(service().list(req(request, "keyword"), req(request, "state"), operatorId));
        }
        if ("pw.mine".equals(action)) {
            return OaonesUtil.ok(service().mine(operatorId));
        }
        if ("pw.orgOptions".equals(action)) {
            // 绑定列表“选择人员”弹框的分部 / 部门候选（与人员绑定页 user.orgOptions 同口径）
            return OaonesUtil.ok(service().orgOptions(operatorId));
        }
        if ("pw.qrStart".equals(action)) {
            return OaonesUtil.ok(service().qrStart(operatorId, req(request, "oa_user_id"),
                    callbackBase(request)));
        }
        if ("pw.scanConfirm".equals(action)) {
            // 由回调页直接调用（不经 api.jsp）：身份来自签名 state，因此不传 operatorId
            return OaonesUtil.ok(service().scanConfirm(req(request, "code"), req(request, "state")));
        }
        if ("pw.unbind".equals(action)) {
            return OaonesUtil.ok(service().unbind(operatorId, req(request, "oa_user_id")));
        }
        return null;
    }

    /** 布尔开关参数：1 / true / on 视为真（与登录设置的开关口径一致）。 */
    private boolean flag(String value) {
        String v = OaonesUtil.s(value).trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    /**
     * 扫码回调地址前缀：协议 + 域名 + 端口（不含路径）。
     *
     * <p>redirect_uri 必须与微信开放平台该应用登记的回调域一致；反向代理 / 多域名部署时
     * 用 oaones.appsyn.properties 的 {@code pw.bind.callback.base} 显式指定，未配置时按
     * 系统设置里的 OA 访问地址 → 代理透传头 → 浏览器访问地址依次兜底。</p>
     */
    private String callbackBase(HttpServletRequest request) {
        String configured = OaonesUtil.s(OaonesProps.pwCallbackBase()).trim();
        while (configured.endsWith("/")) {
            configured = configured.substring(0, configured.length() - 1);
        }
        if (configured.length() > 0) {
            return configured;
        }
        return OaonesSso.siteBase(request);
    }
}
