<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    /*
     * 考勤同步（独立一级菜单：/oaones/attSync/attSync.jsp）
     *
     * 把第三方平台（企业微信 / 钉钉 / 飞书）的考勤打卡数据写进 OA，并支持反向（OA 的打卡写到第三方平台）。
     * 参与同步的人只限「已绑定人员」；没绑定的人在同步时跳过并留下固定文案的原因。
     * 两个方向各自独立配置、独立执行、独立记日志（需求《考勤同步需求规格.md》3.0）。
     *
     * 版式参考同模块的「统一消息中心」页壳：顶部一级标题 + 平台切换，下面一张卡片里两行页签
     * ——**先选租户、再看功能**（2026-10-15 现场反馈第二轮）：
     * 第一行是**租户页签**（页面级，本文件里的 #attTenantTabs）：一个租户一个页签、一屏只看一个租户，
     * 第二行才是「同步设置 / 同步日志 / 打卡明细」，**三个都跟着当前选中的租户走**；
     * 页面不放模块标题行，也不放工具条那一行（登录人角色提示 / 问号说明 / 刷新按钮都没有）。
     *   同步设置：选中租户的卡片 = 租户级开关 + 考勤应用（与租户绑定，选完立刻保存并校验）
     *             + 它的同步设置行（一行一条规则：方向 / 范围 / 数据类型 / 时间口径 / 计划任务 /
     *             最近执行 / 告警）；没有「整批执行」按钮（2026-10-15 现场反馈）；
     *   同步日志：该租户的批次列表（一次执行一行），点“明细”看这一批的打卡明细，失败 / 跳过可重推；
     *   打卡明细：该租户全部明细的平铺列表（可按方向 / 结果 / 打卡时间 / 关键字筛），失败 / 跳过可重推。
     * 页壳与面板、脚本固定为 attSync.jsp + attSyncPanel.jsp + js/attSync.js（三个平台共用）。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    String oaonesPlatform = request.getParameter("platform");
    if (oaonesPlatform == null) { oaonesPlatform = ""; }
    oaonesPlatform = oaonesPlatform.trim().toLowerCase();
    if (!"dingtalk".equals(oaonesPlatform) && !"feishu".equals(oaonesPlatform)) {
        oaonesPlatform = "wecom";
    }
    boolean oaonesDd = "dingtalk".equals(oaonesPlatform);
    boolean oaonesFs = "feishu".equals(oaonesPlatform);
    int oaonesType = oaonesFs ? 2 : (oaonesDd ? 1 : 0);
    String oaonesPlatformLabel = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企业微信");
    String oaonesPlatformShort = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企微");
    String oaonesApiUrl = oaonesFs ? "/oaones/Feishu/api.jsp"
            : (oaonesDd ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    String oaonesCfgAllLabel = oaonesFs ? "全部飞书租户" : (oaonesDd ? "全部钉钉租户" : "全部企微租户");
    // 页签：?tab=setting|log|item（默认“同步设置”）
    String oaonesTab = request.getParameter("tab");
    if (!"log".equals(oaonesTab) && !"item".equals(oaonesTab)) { oaonesTab = "setting"; }
    boolean oaonesSysAdmin = OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()));
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <script>var P_FULL = "<%=oaonesPlatformLabel%>", P_SHORT = "<%=oaonesPlatformShort%>";
    var P_PLATFORM = "<%=oaonesPlatform%>";</script>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>考勤同步 - OAones</title>
    <script>window.OAONES_API_URL = "<%=oaonesApiUrl%>";
    window.OAONES_CFG_ALL_LABEL = "<%=oaonesCfgAllLabel%>";
    window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=oaonesSysAdmin%> };
    window.OAONES_ATT_TYPE = <%=oaonesType%>;
    window.OAONES_ATT_TAB = "<%=oaonesTab%>";</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/datetime.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 一级标题只属于本菜单（“考勤同步”）：不链到租户设置，也不链到会议同步 / 同步中心 --%>
<div class="navbar">
    <h1>考勤同步</h1>
    <div class="nav">
        <span class="view-switch" title="切换平台：三个平台的考勤接口与权限点不同，分别维护">
            <a class="view-btn<%= oaonesDd || oaonesFs ? "" : " active" %>"
               href="/oaones/attSync/attSync.jsp?platform=wecom&tab=<%=oaonesTab%>">企业微信</a>
            <a class="view-btn<%= oaonesDd ? " active" : "" %>"
               href="/oaones/attSync/attSync.jsp?platform=dingtalk&tab=<%=oaonesTab%>">钉钉</a>
            <a class="view-btn<%= oaonesFs ? " active" : "" %>"
               href="/oaones/attSync/attSync.jsp?platform=feishu&tab=<%=oaonesTab%>">飞书</a>
        </span>
    </div>
</div>
<div class="page">
    <div class="card">
        <%-- 卡片里不放工具条那一行（2026-10-15 现场反馈：顶部的问号说明整行去掉，
             隐藏的 .help-source 说明正文一并删掉），与「统一消息中心」页壳同一口径。
             卡片里两行页签，**租户在上、功能在下**（2026-10-15 现场反馈第二轮）：
             先选租户，再在它下面看「同步设置 / 同步日志 / 打卡明细」。
             本页口径见 oaones/skill.md 第一节第 32 条与 oaones/考勤同步需求规格.md --%>
        <%-- 租户页签（第一行，页面级）：三个功能页签都只看这一个租户的数据 --%>
        <div class="sub-tabs" id="attTenantTabs" style="flex-wrap:wrap;"></div>
        <%-- 功能页签（第二行）：第二层页签所以不强出底线，与上面那排租户页签区分开 --%>
        <div class="sub-tabs" id="attTabs" style="border:none;margin:0 0 14px;">
            <button type="button" data-tab="setting" class="active">同步设置</button>
            <button type="button" data-tab="log">同步日志</button>
            <button type="button" data-tab="item">打卡明细</button>
        </div>
        <%-- 考勤同步面板（标记 + 脚本）：三个平台共用一份 --%>
        <jsp:include page="attSyncPanel.jsp"/>
    </div>
</div>
<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/js/attSync.js?v=<%=oaonesAssetV%>"></script>
<script>
/* 页签切换：与会议同步 / 消息中心同一套口径（切到哪个载哪个，重复载幂等） */
(function () {
    "use strict";
    function switchTab(tab) {
        if (tab !== "setting" && tab !== "log" && tab !== "item") { return; }
        var tabs = document.querySelectorAll("#attTabs button");
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].classList.toggle("active", tabs[i].getAttribute("data-tab") === tab);
        }
        var panels = document.querySelectorAll("[data-att-panel]");
        for (var j = 0; j < panels.length; j++) {
            panels[j].classList.toggle("active", panels[j].getAttribute("data-att-panel") === tab);
        }
        if (window.OaonesAttSync) { window.OaonesAttSync.switchTo(tab); }
    }
    function init() {
        var tabs = document.querySelectorAll("#attTabs button");
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].addEventListener("click", function () {
                switchTab(this.getAttribute("data-tab"));
            });
        }
        if (window.OaonesAttSync) { window.OaonesAttSync.init(); }
        var initial = window.OAONES_ATT_TAB;
        if (initial !== "log" && initial !== "item") { initial = "setting"; }
        switchTab(initial);
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
</script>
</body>
</html>
