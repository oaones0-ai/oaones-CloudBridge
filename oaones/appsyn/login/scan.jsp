<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.LoginController" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesRoutes" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesApiLog" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.common.QrLoginPlatform" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.wecom.LoginConfigServiceImpl" %>
<%@ page import="weaver.general.Util" %>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<%
    // 统一扫码落地页（统一登录页上的**那一张**二维码内容指向这里）——**手机端页面**，不校验 OA 登录会话。
    //
    // 三种进入方式，同一个地址：
    //   ① 扫码方（企业微信 / 钉钉 / 飞书）打开 /oaones/appsyn/login/scan.jsp?t=票据：
    //      按 UA 判断平台 → 把请求交给**对应连接器**的落地逻辑（各连接器只用自己的租户与凭证换身份）；
    //      只配了一家可用企业就直接 302 到该企业的网页授权地址（静默授权）；
    //      配了多家时先让扫码人在本页选自己所在的企业（跨企业授权在客户端会报错，
    //      也不能拿同一个 code 逐家试探，所以这里明确选一次）；
    //   ② 平台授权回跳 /oaones/appsyn/login/scan.jsp?code=..&state=..：
    //      state 里带回了票据/平台/第几家企业 → 换成员身份 → 按人员绑定关系定位 OA 账号；
    //      不是这家企业的成员就自动换下一家继续探测（这就是“一张二维码覆盖多企业”的关键）；
    //   ③ 身份核实完成后票据直接进入“已登录”，电脑上的登录页轮询到之后，
    //      由**电脑自己的请求**写登录会话并直接进入工作台。
    //
    // 扫码登录不再要求手机端二次点击确认。若碰到切换配置前遗留的“待确认”票据，
    // 本页会自动提交一次确认（不渲染确认按钮），避免员工卡在中间页。
    //
    // 本页只做“平台分派 + 渲染”，不判断“是哪家平台 / 怎么换身份”：
    // 分派在 LoginController#scanStep/#scanConfirm，换取身份在各连接器自己的实现里。
    // 平台授权回跳用的 redirect_uri 由各连接器配置（企微默认也指向本页；钉钉 / 飞书的旧地址
    // login/DingTalk/scan.jsp、login/Feishu/scan.jsp 现在只做 302 跳到本页，老配置不用改）。
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    String oaonesAct = OaonesUtil.s(request.getParameter("act"));
    JSONObject oaonesResult = oaonesAct.length() > 0
            ? new LoginController().scanConfirm(request)
            : new LoginController().scanStep(request);
    JSONObject oaonesData = oaonesResult == null ? null : oaonesResult.getJSONObject("data");
    if (oaonesData == null) {
        oaonesData = new JSONObject();
        oaonesData.put("mode", "failed");
        oaonesData.put("msg", "扫码登录失败，请在电脑上刷新二维码后重试");
    }
    // 接口日志中心：记录这一次“扫码落地页”的入口调用（第三方平台→OA 的入口）。
    // 平台按已识别出的平台名映射；识别不出（如首次扫码尚未判定平台）按企业微信用 0 计。
    String oaonesLogPlatformName = OaonesUtil.s(oaonesData.getString("platform")).toLowerCase();
    int oaonesLogPlatform = QrLoginPlatform.PERSONAL_WX.equals(oaonesLogPlatformName)
            ? OaonesApiLog.PLATFORM_PERSONAL_WX
            : ("dingtalk".equals(oaonesLogPlatformName) ? OaonesConst.TYPE_DINGTALK
            : ("feishu".equals(oaonesLogPlatformName) ? OaonesConst.TYPE_FEISHU : OaonesConst.TYPE_WECHAT));
    OaonesApiLog.inbound(oaonesLogPlatform,
            OaonesUtil.i(OaonesUtil.s(oaonesData.getString("cfg_id")), 0),
            OaonesUtil.s(oaonesData.getString("cfg_name")),
            OaonesApiLog.TYPE_LOGIN, "/oaones/appsyn/login/scan.jsp",
            OaonesApiLog.requestSummary(request),
            OaonesUtil.s(oaonesData.getString("mode")) + ":" + OaonesUtil.s(oaonesData.getString("msg")),
            oaonesResult != null && oaonesResult.getIntValue("code") == 0, "",
            "failed".equals(OaonesUtil.s(oaonesData.getString("mode")))
                    ? OaonesUtil.s(oaonesData.getString("msg")) : "", "");
    String oaonesMode = OaonesUtil.s(oaonesData.getString("mode"));
    if ("oauth".equals(oaonesMode)) {
        // 去平台网页授权换取身份：保持直接 302（由企业微信 / 钉钉 / 飞书客户端接管授权，页面不渲染任何内容）
        response.sendRedirect(OaonesUtil.s(oaonesData.getString("auth_url")));
        return;
    }
    String oaonesMsg = OaonesUtil.s(oaonesData.getString("msg"));
    String oaonesPlatform = OaonesUtil.s(oaonesData.getString("platform"));
    String oaonesTicket = OaonesUtil.s(oaonesData.getString("ticket"));
    if (oaonesTicket.length() == 0) {
        oaonesTicket = OaonesUtil.s(request.getParameter("t"));
    }
    String oaonesState = OaonesUtil.s(request.getParameter("state"));
    // 提示文案里的客户端名单：**只列“登录设置”里已开启扫码的平台**（后台没启用飞书就不提飞书），
    // 与“平台被关掉时扫码请求直接被服务端拒绝”（LoginController#requirePlatformEnabled）同一口径。
    JSONObject oaonesScanSettings = new LoginConfigServiceImpl().publicSettings();
    java.util.List<String> oaonesScanLabels = QrLoginPlatform.enabledLabels(oaonesScanSettings);
    String oaonesScanText = QrLoginPlatform.enabledLabelText(oaonesScanSettings);
    boolean oaonesPersonalWxOn = oaonesScanSettings.getBooleanValue("personal_wx_available");
    boolean oaonesScanOn = oaonesScanLabels.size() > 0 || oaonesPersonalWxOn;
    boolean oaonesScanMulti = oaonesScanLabels.size() > 1;
    if (oaonesPersonalWxOn) {
        oaonesScanText = oaonesScanText.length() == 0 ? "个人微信"
                : oaonesScanText + " / 个人微信";
    }
    // 页面标题与图标按状态区分：自动完成页、成功页、失败页、不支持客户端页
    boolean oaonesConfirm = "confirm".equals(oaonesMode);
    boolean oaonesDone = "done".equals(oaonesMode);
    boolean oaonesChoose = "choose".equals(oaonesMode);
    boolean oaonesUnsupported = "unsupported".equals(oaonesMode);
    // 认不出客户端（例如用普通相机 / 浏览器打开）时的“该用哪些客户端”提示：
    // 只写后台已开启的平台；企业应用与个人微信都不可用时直接说扫码登录已关闭。
    String oaonesScanClientTip = oaonesScanOn
            ? ("请用" + oaonesScanText + "扫一扫") : "扫码登录已关闭";
    String oaonesTitle = oaonesConfirm ? "正在完成登录"
            : (oaonesDone ? "扫码成功"
            : (oaonesChoose ? "选择你所在的企业"
            : (oaonesUnsupported ? oaonesScanClientTip : "无法扫码登录")));
    com.alibaba.fastjson.JSONArray oaonesTenants = oaonesData.getJSONArray("tenants");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%=Util.toHtml(oaonesTitle)%> - OAones</title>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
</head>
<body class="wx-callback-page">
<div class="wx-callback-icon"><%=oaonesConfirm ? "&#63;" : (oaonesDone ? "&#10004;" : "&#33;")%></div>
<div class="wx-callback-title <%=oaonesDone ? "ok" : (oaonesConfirm ? "" : "fail")%>">
    <%=Util.toHtml(oaonesTitle)%>
</div>
<% if (oaonesChoose) { %>
<div class="wx-callback-text">
    <p><%=Util.toHtml(oaonesMsg.length() == 0 ? "请选择你所在的企业：" : oaonesMsg)%></p>
</div>
<div style="margin-top:14px;">
    <% for (int i = 0; oaonesTenants != null && i < oaonesTenants.size(); i++) {
        JSONObject oaonesTenant = oaonesTenants.getJSONObject(i);
        String oaonesTenantId = OaonesUtil.nullToEmpty(oaonesTenant.getString("cfg_id"));
        String oaonesTenantName = OaonesUtil.s(oaonesTenant.getString("cfg_name"));
    %>
    <p>
        <%-- 选完企业回到同一个落地页：客户端 UA 与 p 参数都能让后端继续分派到同一家连接器 --%>
        <a class="btn" style="display:inline-block;min-width:220px;text-decoration:none;"
           href="/oaones/appsyn/login/scan.jsp?t=<%=Util.toHtml(oaonesTicket)%>&amp;c=<%=Util.toHtml(oaonesTenantId)%>&amp;p=<%=Util.toHtml(oaonesPlatform)%>">
            <%=Util.toHtml(oaonesTenantName.length() == 0 ? ("企业ID " + oaonesTenantId) : oaonesTenantName)%>
        </a>
    </p>
    <% } %>
</div>
<div class="wx-callback-text">选错企业时重新扫一次二维码即可。</div>
<% } else if (oaonesConfirm) { %>
<div class="wx-callback-text">
    <p>身份已核实，正在进入工作台，请稍候…</p>
</div>
<form id="oaonesLegacyConfirmForm" method="post" action="/oaones/appsyn/login/scan.jsp" style="display:none;">
    <input type="hidden" name="t" value="<%=Util.toHtml(oaonesTicket)%>">
    <input type="hidden" name="state" value="<%=Util.toHtml(oaonesState)%>">
    <input type="hidden" name="p" value="<%=Util.toHtml(oaonesPlatform)%>">
    <input type="hidden" name="act" value="confirm">
</form>
<script>document.getElementById("oaonesLegacyConfirmForm").submit();</script>
<% } else { %>
<div class="wx-callback-text"><%=Util.toHtml(oaonesMsg.length() == 0
        ? (oaonesDone ? "扫码成功，电脑端正在进入工作台" : "扫码登录未完成，请在电脑上刷新二维码后重试")
        : oaonesMsg)%></div>
<% if (oaonesDone) { %>
<div class="wx-callback-text">电脑上的登录页会自动进入工作台；如果电脑上没有反应，请回到电脑上刷新登录页。</div>
<%-- “快捷登录”由 window.open 打开，扫码成功页自动关闭；普通手机扫码页没有 opener，不执行关闭。 --%>
<script>
setTimeout(function () {
    if (window.opener && window.opener !== window) {
        try { window.close(); } catch (e) { /* 浏览器拒绝关闭时由电脑端收口 */ }
    }
}, 300);
</script>
<% } else if (oaonesUnsupported) { %>
<% if (oaonesScanOn) { %>
<div class="wx-callback-text">请在手机上打开本人的<b><%=Util.toHtml(oaonesScanText)%></b> → 右上角“+” → 扫一扫，
    扫描电脑登录页上的二维码<%=oaonesScanMulti ? "（这几个客户端扫的是同一张二维码）" : ""%>。</div>
<% } %>
<% } else { %>
<%-- 失败后用同一张二维码重试：只要二维码还没过期，后端会把票据退回“待扫码”重新走一遍 --%>
<% if (oaonesTicket.length() > 0) { %>
<a class="wx-callback-link"
   href="/oaones/appsyn/login/scan.jsp?t=<%=Util.toHtml(oaonesTicket)%>&amp;p=<%=Util.toHtml(oaonesPlatform)%>">
    重新扫码 / 重新选择企业
</a>
<br>
<% } %>
<a class="wx-callback-link" href="<%=OaonesRoutes.publicUrl("/oaones/appsyn/login/login.jsp")%>">打开 OA 登录页</a>
<% } %>
<% } %>
</body>
</html>
