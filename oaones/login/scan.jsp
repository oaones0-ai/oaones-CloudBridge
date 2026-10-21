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
<%@ include file="/oaones/assets.jsp" %>
<%
    // 统一扫码落地页（统一登录页上的**那一张**二维码内容指向这里）——**手机端页面**，不校验 OA 登录会话。
    //
    // 三种进入方式，同一个地址：
    //   ① 扫码方（企业微信 / 钉钉 / 飞书）打开 /oaones/login/scan.jsp?t=票据：
    //      按 UA 判断平台 → 把请求交给**对应连接器**的落地逻辑（各连接器只用自己的租户与凭证换身份）；
    //      只配了一家可用企业就直接 302 到该企业的网页授权地址（静默授权）；
    //      配了多家时先让扫码人在本页选自己所在的企业（跨企业授权在客户端会报错，
    //      也不能拿同一个 code 逐家试探，所以这里明确选一次）；
    //   ② 平台授权回跳 /oaones/login/scan.jsp?code=..&state=..：
    //      state 里带回了票据/平台/第几家企业 → 换成员身份 → 按人员绑定关系定位 OA 账号；
    //      不是这家企业的成员就自动换下一家继续探测（这就是“一张二维码覆盖多企业”的关键）；
    //   ③ 扫码人点“确认登录”（POST 回本页 act=confirm）：把票据推进到“已确认”，
    //      电脑上的登录页轮询到之后，由**电脑自己的请求**写登录会话并进入 OA。
    //
    // 确认动作放在手机端是防误扫/防二维码被别人扫走：电脑页面上会显示“谁、哪家企业”在请求登录。
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
    int oaonesLogPlatform = "dingtalk".equals(oaonesLogPlatformName) ? OaonesConst.TYPE_DINGTALK
            : ("feishu".equals(oaonesLogPlatformName) ? OaonesConst.TYPE_FEISHU : OaonesConst.TYPE_WECHAT);
    OaonesApiLog.inbound(oaonesLogPlatform,
            OaonesUtil.i(OaonesUtil.s(oaonesData.getString("cfg_id")), 0),
            OaonesUtil.s(oaonesData.getString("cfg_name")),
            OaonesApiLog.TYPE_LOGIN, "/oaones/login/scan.jsp",
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
    String oaonesPlatformLabel = OaonesUtil.s(oaonesData.getString("platform_label"));
    String oaonesPlatform = OaonesUtil.s(oaonesData.getString("platform"));
    String oaonesCfgName = OaonesUtil.s(oaonesData.getString("cfg_name"));
    String oaonesOutName = OaonesUtil.s(oaonesData.getString("out_user_name"));
    String oaonesOutId = OaonesUtil.s(oaonesData.getString("out_user_id"));
    String oaonesOaName = OaonesUtil.s(oaonesData.getString("oa_name"));
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
    boolean oaonesScanOn = oaonesScanLabels.size() > 0;
    boolean oaonesScanMulti = oaonesScanLabels.size() > 1;
    // 页面标题与图标按状态区分：确认页、成功页、失败页、不支持客户端页
    boolean oaonesConfirm = "confirm".equals(oaonesMode);
    boolean oaonesDone = "done".equals(oaonesMode);
    boolean oaonesChoose = "choose".equals(oaonesMode);
    boolean oaonesUnsupported = "unsupported".equals(oaonesMode);
    // 认不出客户端（例如用普通相机 / 浏览器打开）时的“该用哪些客户端”提示：
    // 只写后台已开启的平台；三个平台全被关掉时直接说扫码登录已关闭。
    String oaonesScanClientTip = oaonesScanOn
            ? ("请用" + oaonesScanText + "扫一扫") : "扫码登录已关闭";
    String oaonesTitle = oaonesConfirm ? "确认登录 OA"
            : (oaonesDone ? "已确认登录"
            : (oaonesChoose ? "选择你所在的企业"
            : (oaonesUnsupported ? oaonesScanClientTip : "无法扫码登录")));
    com.alibaba.fastjson.JSONArray oaonesTenants = oaonesData.getJSONArray("tenants");
    // 分派不出平台（既不认识 UA 也没带 state）时页面上只写通用文案
    String oaonesPlatformText = oaonesPlatformLabel.length() == 0
            ? (oaonesScanOn ? oaonesScanText : "办公客户端") : oaonesPlatformLabel;
    String oaonesWho = (oaonesOutName.length() > 0 ? oaonesOutName : oaonesOutId);
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%=Util.toHtml(oaonesTitle)%> - OAones</title>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
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
           href="/oaones/login/scan.jsp?t=<%=Util.toHtml(oaonesTicket)%>&amp;c=<%=Util.toHtml(oaonesTenantId)%>&amp;p=<%=Util.toHtml(oaonesPlatform)%>">
            <%=Util.toHtml(oaonesTenantName.length() == 0 ? ("企业ID " + oaonesTenantId) : oaonesTenantName)%>
        </a>
    </p>
    <% } %>
</div>
<div class="wx-callback-text">选错企业时重新扫一次二维码即可。</div>
<% } else if (oaonesConfirm) { %>
<div class="wx-callback-text">
    <p>即将用下面的账号登录 OA（电脑端）：</p>
    <p>
        <b><%=Util.toHtml(oaonesPlatformText)%>成员</b>：<%=Util.toHtml(oaonesWho)%>
        <% if (oaonesCfgName.length() > 0) { %>
        <br><b>所属企业</b>：<%=Util.toHtml(oaonesCfgName)%>
        <% } %>
        <% if (oaonesOaName.length() > 0) { %>
        <br><b>OA 账号</b>：<%=Util.toHtml(oaonesOaName)%>
        <% } %>
    </p>
    <p>确认后电脑上的登录页会自动进入 OA；不是你本人在登录请点“取消”。</p>
</div>
<form method="post" action="/oaones/login/scan.jsp" style="margin-top:18px;">
    <input type="hidden" name="t" value="<%=Util.toHtml(oaonesTicket)%>">
    <input type="hidden" name="state" value="<%=Util.toHtml(oaonesState)%>">
    <input type="hidden" name="p" value="<%=Util.toHtml(oaonesPlatform)%>">
    <button type="submit" class="btn" name="act" value="confirm">确认登录</button>
    <button type="submit" class="btn ghost-danger" name="act" value="cancel"
            style="margin-left:10px;">取消</button>
</form>
<% } else { %>
<div class="wx-callback-text"><%=Util.toHtml(oaonesMsg.length() == 0
        ? (oaonesDone ? "已确认登录，请回到电脑上继续" : "扫码登录未完成，请在电脑上刷新二维码后重试")
        : oaonesMsg)%></div>
<% if (oaonesDone) { %>
<div class="wx-callback-text">电脑上的登录页会自动进入 OA；如果电脑上没有反应，请回到电脑上刷新登录页。</div>
<% } else if (oaonesUnsupported) { %>
<% if (oaonesScanOn) { %>
<div class="wx-callback-text">请在手机上打开本人的<b><%=Util.toHtml(oaonesScanText)%></b> → 右上角“+” → 扫一扫，
    扫描电脑登录页上的二维码<%=oaonesScanMulti ? "（这几个客户端扫的是同一张二维码）" : ""%>。</div>
<% } %>
<% } else { %>
<%-- 失败后用同一张二维码重试：只要二维码还没过期，后端会把票据退回“待扫码”重新走一遍 --%>
<% if (oaonesTicket.length() > 0) { %>
<a class="wx-callback-link"
   href="/oaones/login/scan.jsp?t=<%=Util.toHtml(oaonesTicket)%>&amp;p=<%=Util.toHtml(oaonesPlatform)%>">
    重新扫码 / 重新选择企业
</a>
<br>
<% } %>
<a class="wx-callback-link" href="<%=OaonesRoutes.publicUrl("/oaones/login/login.jsp")%>">打开 OA 登录页</a>
<% } %>
<% } %>
</body>
</html>
