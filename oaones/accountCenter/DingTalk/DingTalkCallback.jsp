<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSON" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.controller.dingtalk.OaonesController" %>
<%@ page import="weaver.general.Util" %>
<%@ include file="/oaones/assets.jsp" %>
<%
    request.setCharacterEncoding("UTF-8");
    // 钉钉扫码授权回调页：只接收 code + state。
    // ① 不依赖 OA 登录会话 —— 发起人身份取自带签名且未过期的 state（见 AccountService.scanConfirm）；
    // ② 页面与“个人账号中心”同源，成功/失败结果直接回调父窗口 OaonesWxScanDone 刷新列表，
    //    用户单独打开本页（非 iframe）时也能看到结果；
    // ③ 绑定成功后本页只停留 3 秒即自动返回“个人账号中心”（失败时保留本页与失败原因）。
    // 钉钉网页授权回跳带的授权码：新版 OAuth2 授权（login.dingtalk.com/oauth2/auth）回传 authCode，
    // 旧口径回传 code；两个都收，避免因为参数名不同停在“缺少 code”上。
    String oaonesCode = OaonesUtil.s(request.getParameter("code"));
    if (oaonesCode.length() == 0) {
        oaonesCode = OaonesUtil.s(request.getParameter("authCode"));
    }
    String oaonesState = OaonesUtil.s(request.getParameter("state"));
    JSONObject oaonesResult;
    if (oaonesCode.length() == 0 || oaonesState.length() == 0) {
        oaonesResult = new JSONObject();
        oaonesResult.put("code", -1);
        oaonesResult.put("msg", "扫码回调缺少 code/state 参数，无法完成绑定，请回到“个人账号中心”重新扫码");
    } else {
        oaonesResult = new OaonesController().handle("account.wxScanConfirm", request, "");
    }
    if (oaonesResult == null) {
        oaonesResult = new JSONObject();
        oaonesResult.put("code", -1);
        oaonesResult.put("msg", "扫码绑定失败，请回到“个人账号中心”重新扫码");
    }
    boolean oaonesOk = oaonesResult.getIntValue("code") == 0;
    JSONObject oaonesData = oaonesResult.getJSONObject("data");
    String oaonesWxName = oaonesData == null ? "" : OaonesUtil.s(oaonesData.getString("wx_name"));
    String oaonesCfgName = oaonesData == null ? "" : OaonesUtil.s(oaonesData.getString("cfg_name"));
    String oaonesMsg;
    if (oaonesOk) {
        oaonesMsg = "已把本 OA 账号绑定到钉钉成员"
                + (oaonesWxName.length() == 0 ? "" : "「" + oaonesWxName + "」")
                + (oaonesCfgName.length() == 0 ? "" : "（" + oaonesCfgName + "）")
                + "。";
    } else {
        oaonesMsg = OaonesUtil.s(oaonesResult.getString("msg"));
    }
    String oaonesMsgJson = JSON.toJSONString(oaonesMsg).replace("<", "\\u003c");
    String oaonesStateJson = JSON.toJSONString(oaonesState).replace("<", "\\u003c");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>钉钉扫码绑定 - OAones</title>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
</head>
<body class="wx-callback-page">
<div class="wx-callback-icon"><%=oaonesOk ? "&#10004;" : "&#33;"%></div>
<div class="wx-callback-title <%=oaonesOk ? "ok" : "fail"%>"><%=oaonesOk ? "绑定成功" : "绑定失败"%></div>
<div class="wx-callback-text"><%=Util.toHtml(oaonesMsg)%></div>
<%-- 绑定成功页只停留 3 秒：倒计时提示由下方脚本填充，3 秒后自动跳回“个人账号中心” --%>
<div class="wx-callback-text wx-callback-auto" id="wxCallbackAutoTip" style="display:none;"></div>
<a class="wx-callback-link" href="/oaones/accountCenter/MyAccount.jsp?tab=dingtalk" target="_blank">打开“个人账号中心”</a>
<script>
(function () {
    var done = { ok: <%=oaonesOk%>, msg: <%=oaonesMsgJson%> };
    var state = <%=oaonesStateJson%>;
    // 通知“个人账号中心”刷新绑定列表：回调页与主页面同源，两种打开方式都要覆盖——
    // ① 内嵌二维码：回调页在弹框 iframe 里，通知 window.parent；
    // ② 新窗口二维码（浏览器拦截 iframe 第三方 Cookie 时的兜底）：通知 window.opener。
    // 都不适用（单独打开/跨域）时静默跳过，页面上已有结果与本页链接可继续操作。
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

    // 绑定成功后本页只停留 3 秒，然后自动跳回“个人账号中心”（用户要求的口径）。
    //  ① 内嵌二维码（本页在弹框 iframe 里）：父页收到通知后会关闭弹框并刷新列表，
    //     这里再对顶层页面做一次跳转兜底——父页没接住通知时也不会停在回调页；
    //  ② 新窗口二维码 / 手机单独打开：本窗口自己跳回账号中心（同一账号中心页面）。
    //  绑定失败不跳转：失败原因与“重新扫码”入口必须留在页面上给用户看。
    if (<%=oaonesOk%>) {
        var oaonesBackUrl = "/oaones/accountCenter/MyAccount.jsp?tab=dingtalk";
        var oaonesBackSeconds = 3;
        var oaonesTip = document.getElementById("wxCallbackAutoTip");
        var oaonesLeft = oaonesBackSeconds;
        var oaonesTick = function () {
            oaonesTip.textContent = oaonesLeft > 0
                ? "将在 " + oaonesLeft + " 秒后自动返回“个人账号中心”…" : "正在返回“个人账号中心”…";
        };
        oaonesTip.style.display = "";
        oaonesTick();
        var oaonesTimer = setInterval(function () {
            oaonesLeft--;
            oaonesTick();
            if (oaonesLeft <= 0) { clearInterval(oaonesTimer); }
        }, 1000);
        setTimeout(function () {
            try {
                // iframe 内跳顶层页面（回调页与账号中心同源）；单独/新窗口打开时跳本窗口
                var target = (window.parent && window.parent !== window) ? window.top : window;
                target.location.replace(oaonesBackUrl);
            } catch (e) {
                try { location.replace(oaonesBackUrl); } catch (e2) { /* 跨域时静默忽略 */ }
            }
        }, oaonesBackSeconds * 1000);
    }
})();
</script>
</body>
</html>
