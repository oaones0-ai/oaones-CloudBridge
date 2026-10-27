<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSON" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesApiLog" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.controller.PersonalWxController" %>
<%@ page import="weaver.general.Util" %>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<%
    request.setCharacterEncoding("UTF-8");
    /*
     * 个人微信扫码授权回调页（redirect_uri）。
     *
     * 与企微侧的 WeComCallback.jsp 同口径：
     *  ① 不依赖 OA 登录会话 —— 发起人身份取自带签名且未过期的 state（见 PersonalWxService.scanConfirm，
     *     签名密钥就是微信 AppSecret），因此手机上扫码、换设备打开都能完成绑定；
     *  ② 页面与调用方同源时，把结果通知父窗口 / 打开者（window.OaonesWxScanDone）刷新列表；
     *  ③ 绑定成功后本页只停留 3 秒即自动返回：管理员在「微信设置」页代绑（?from=admin）回微信设置，
     *     本人在个人账号中心扫码（?from=self / 缺省）回“个人账号中心 → 个人微信”；
     *     绑定失败不跳转（失败原因与重新扫码入口必须留在页面上）。
     */
    String oaonesPwCode = OaonesUtil.s(request.getParameter("code"));
    String oaonesPwState = OaonesUtil.s(request.getParameter("state"));
    String oaonesPwFrom = OaonesUtil.s(request.getParameter("from")).trim().toLowerCase();
    boolean oaonesPwAsAdmin = "admin".equals(oaonesPwFrom);
    JSONObject oaonesPwResult;
    if (oaonesPwCode.length() == 0 || oaonesPwState.length() == 0) {
        oaonesPwResult = new JSONObject();
        oaonesPwResult.put("code", -1);
        oaonesPwResult.put("msg", "扫码回调缺少 code/state 参数，无法完成绑定，请回到页面重新扫码");
    } else {
        oaonesPwResult = new PersonalWxController().handle("pw.scanConfirm", request, "");
    }
    if (oaonesPwResult == null) {
        oaonesPwResult = new JSONObject();
        oaonesPwResult.put("code", -1);
        oaonesPwResult.put("msg", "扫码绑定失败，请回到页面重新扫码");
    }
    boolean oaonesPwOk = oaonesPwResult.getIntValue("code") == 0;
    // 接口日志中心：记录这一次“个人微信扫码回调”的入口调用（第三方平台→OA 的入口）
    OaonesApiLog.inbound(OaonesApiLog.PLATFORM_PERSONAL_WX, 0, "",
            OaonesApiLog.TYPE_LOGIN, "/oaones/appsyn/personalWx/PersonalWxCallback.jsp",
            OaonesApiLog.requestSummary(request),
            oaonesPwResult.toJSONString(), oaonesPwOk,
            String.valueOf(oaonesPwResult.getIntValue("code")),
            OaonesUtil.s(oaonesPwResult.getString("msg")), "");
    JSONObject oaonesPwData = oaonesPwResult.getJSONObject("data");
    String oaonesPwNick = oaonesPwData == null ? "" : OaonesUtil.s(oaonesPwData.getString("nickname"));
    String oaonesPwName = oaonesPwData == null ? "" : OaonesUtil.s(oaonesPwData.getString("oa_user_name"));
    String oaonesPwMsg;
    if (oaonesPwOk) {
        oaonesPwMsg = "已把" + (oaonesPwName.length() == 0 ? "该 OA 账号" : " OA 账号「" + oaonesPwName + "」")
                + "绑定到微信"
                + (oaonesPwNick.length() == 0 ? "" : "「" + oaonesPwNick + "」") + "。";
    } else {
        oaonesPwMsg = OaonesUtil.s(oaonesPwResult.getString("msg"));
    }
    String oaonesPwMsgJson = JSON.toJSONString(oaonesPwMsg).replace("<", "\\u003c");
    String oaonesPwStateJson = JSON.toJSONString(oaonesPwState).replace("<", "\\u003c");
    String oaonesPwBackUrl = oaonesPwAsAdmin
            ? "/oaones/appsyn/personalWx/config.jsp" : "/oaones/appsyn/accountCenter/MyAccount.jsp?tab=personalwx";
    String oaonesPwBackLabel = oaonesPwAsAdmin ? "返回「微信设置」" : "打开“个人账号中心”";
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>个人微信扫码绑定 - OAones</title>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
</head>
<body class="wx-callback-page">
<div class="wx-callback-icon"><%=oaonesPwOk ? "&#10004;" : "&#33;"%></div>
<div class="wx-callback-title <%=oaonesPwOk ? "ok" : "fail"%>"><%=oaonesPwOk ? "绑定成功" : "绑定失败"%></div>
<div class="wx-callback-text"><%=Util.toHtml(oaonesPwMsg)%></div>
<div class="wx-callback-text wx-callback-auto" id="pwCallbackAutoTip" style="display:none;"></div>
<a class="wx-callback-link" href="<%=oaonesPwBackUrl%>" target="_blank"><%=oaonesPwBackLabel%></a>
<script>
(function () {
    var done = { ok: <%=oaonesPwOk%>, msg: <%=oaonesPwMsgJson%> };
    var state = <%=oaonesPwStateJson%>;
    // 通知调用方刷新列表：回调页与主页面同源，两种打开方式都要覆盖——
    // ① 内嵌二维码：回调页在弹框 iframe 里，通知 window.parent；
    // ② 新窗口二维码（浏览器拦截 iframe 第三方 Cookie 时的兜底）：通知 window.opener。
    function notify(win) {
        try {
            if (win && win !== window && typeof win.OaonesWxScanDone === "function") {
                win.OaonesWxScanDone(state, done);
                return true;
            }
        } catch (e) {
            // 跨域/已关闭：忽略
        }
        return false;
    }
    if (!notify(window.parent)) { notify(window.opener); }

    if (<%=oaonesPwOk%>) {
        var backUrl = "<%=oaonesPwBackUrl%>";
        var seconds = 3;
        var tip = document.getElementById("pwCallbackAutoTip");
        var left = seconds;
        var tick = function () {
            tip.textContent = left > 0 ? "将在 " + left + " 秒后自动返回…" : "正在返回…";
        };
        tip.style.display = "";
        tick();
        var timer = setInterval(function () {
            left--;
            tick();
            if (left <= 0) { clearInterval(timer); }
        }, 1000);
        setTimeout(function () {
            try {
                // iframe 内跳顶层页面；单独/新窗口打开时跳本窗口
                var target = (window.parent && window.parent !== window) ? window.top : window;
                target.location.replace(backUrl);
            } catch (e) {
                try { location.replace(backUrl); } catch (e2) { /* 跨域时静默忽略 */ }
            }
        }, seconds * 1000);
    }
})();
</script>
</body>
</html>
