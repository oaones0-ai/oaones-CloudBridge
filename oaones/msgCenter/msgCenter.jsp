<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    /*
     * 消息设置（独立一级菜单：/oaones/msgCenter/msgCenter.jsp）
     *
     * 与「企微租户设置 / 钉钉租户设置 / 登录设置」并列的第四条独立菜单：
     * 本页只做消息相关的事，页面里不出现租户维护入口、也不出现登录设置入口。
     *
     * 页签：推送配置 / 租户开关 / 消息类型 / 推送日志 / 数据看板
     *   * 推送配置 / 消息类型 / 推送日志 / 数据看板：按“平台”维护，
     *     用页面右上角的平台切换在企微 / 钉钉 / 飞书之间切换（三边的消息形态与接口不同，
     *     但**映射对所有租户全局生效**，不按租户各配一套）；
     *   * 租户开关：只列出**当前平台**（企微 / 钉钉 / 飞书三选一，由 ?platform= 决定）的租户，
     *     逐个开/关“消息通知”（要改另一侧的租户，用右上角的平台切换切过去看）；
     *     关掉的租户不再推送，见 sql/alter_oaones_tenant_msg_enable.sql。
     *
     * 实现口径：本页是**页壳**，页签内容直接复用各连接器已有的面板片段
     * （msgCenter/{msgConfigPanel,msgTypePanel,msgLogPanel,msgBoardPanel}.jsp，三个平台共用），
     * 因此不重复实现表格/选择框/日志/看板；
     * “平台切换”通过 ?platform=wecom|dingtalk|feishu 决定本请求包含哪一侧的面板，
     * 同时决定页面用哪个接口入口（WeCom/api.jsp / DingTalk/api.jsp / Feishu/api.jsp），
     * 各平台面板因此不会同时出现在 DOM 里（不会有 id 冲突）。
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
    String oaonesPlatformLabel = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企业微信");
    // 初始页签：?tab=config|tenant|type|log|board（默认第一个页签“推送配置”）
    String oaonesMsgTab = request.getParameter("tab");
    if (oaonesMsgTab == null) { oaonesMsgTab = ""; }
    oaonesMsgTab = oaonesMsgTab.trim().toLowerCase();
    if (!"tenant".equals(oaonesMsgTab) && !"type".equals(oaonesMsgTab)
            && !"log".equals(oaonesMsgTab) && !"board".equals(oaonesMsgTab)) {
        oaonesMsgTab = "config";
    }
    // 复用同一套面板片段：按平台选一侧（面板文件里的文案与取值口径各自对应本平台）
    // 面板整页路径在这里先拼好，下面 <jsp:include> 的 page 只用“纯表达式”。
    // 注意：page 属性不要写成“表达式 + 字面量”混写（表达式后面直接跟面板文件名），
    // Resin 4.0.58 的 JSP 生成器处理不了这种写法，生成的 servlet 会在
    // pageContext.include( 处被截断，javac 报“解析时已到达文件结尾”，整页 500。
    String oaonesPanelConfigPath = "/oaones/msgCenter/msgConfigPanel.jsp";
    String oaonesPanelTypePath = "/oaones/msgCenter/msgTypePanel.jsp";
    String oaonesPanelLogPath = "/oaones/msgCenter/msgLogPanel.jsp";
    String oaonesPanelBoardPath = "/oaones/msgCenter/msgBoardPanel.jsp";
    String oaonesApiUrl = oaonesFs ? "/oaones/Feishu/api.jsp"
            : (oaonesDd ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    String oaonesCfgAllLabel = oaonesFs ? "全部飞书租户" : (oaonesDd ? "全部钉钉租户" : "全部企微租户");
    // 合并后的通用面板用的平台文案与标识标签（企微 corpid/AgentId、钉钉 AppKey/AgentId、飞书 App ID）
    String oaonesPlatformShort = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企微");
    String oaonesPlatformKeyLabel = oaonesFs ? "App ID" : (oaonesDd ? "AppKey" : "corpid");
    String oaonesPlatformAppIdLabel = oaonesFs ? "App ID" : "AgentId";
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <%-- 合并后的通用面板（推送配置/日志/看板）用这些变量拼平台文案 --%>
    <script>var P_FULL = "<%= oaonesPlatformLabel %>", P_SHORT = "<%= oaonesPlatformShort %>";
    var P_PLATFORM = "<%= oaonesPlatform %>";
    var P_KeyLabel = "<%= oaonesPlatformKeyLabel %>", P_AppIdLabel = "<%= oaonesPlatformAppIdLabel %>";</script>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>消息设置 - OAones</title>
    <%-- 平台决定接口入口：企微 → /oaones/WeCom/api.jsp，钉钉 → /oaones/DingTalk/api.jsp，飞书 → /oaones/Feishu/api.jsp --%>
    <script>window.OAONES_API_URL = "<%= oaonesApiUrl %>";
    window.OAONES_CFG_ALL_LABEL = "<%= oaonesCfgAllLabel %>";
    window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };
    window.OAONES_MSG_TAB = "<%=oaonesMsgTab%>";
    window.OAONES_MSG_PLATFORM = "<%=oaonesPlatform%>";</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/datetime.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 一级标题只属于本菜单（“消息设置”）：不链到租户设置、也不链到登录设置 --%>
<div class="navbar">
    <h1>消息设置</h1>
    <div class="nav">
        <span class="view-switch" title="切换平台：企微 / 钉钉 / 飞书的消息形态与接口不同，分别维护">
            <a class="view-btn<%= oaonesDd || oaonesFs ? "" : " active" %>"
               href="/oaones/msgCenter/msgCenter.jsp?platform=wecom&tab=<%=oaonesMsgTab%>">企业微信</a>
            <a class="view-btn<%= oaonesDd ? " active" : "" %>"
               href="/oaones/msgCenter/msgCenter.jsp?platform=dingtalk&tab=<%=oaonesMsgTab%>">钉钉</a>
            <a class="view-btn<%= oaonesFs ? " active" : "" %>"
               href="/oaones/msgCenter/msgCenter.jsp?platform=feishu&tab=<%=oaonesMsgTab%>">飞书</a>
        </span>
    </div>
</div>
<div class="page">
    <div class="card">
        <%-- 顶部工具条整行已去掉（2026-10-13 现场反馈）：登录人角色提示 / 页面说明叹号 / “刷新”按钮都不要了——
             重新打开本 URL 或再次进入本菜单都会重新拉数据，所以页面顶部不再放“刷新”按钮；
             页签之间仍是“切到哪个载哪个”（切回来不重复请求，要看最新数据刷新整个页面即可）。
             各页签面板自己的“刷新”按钮保留（只重载本页签，见各 xxxPanel.jsp）。 --%>
        <div class="sub-tabs" id="msgTabs">
            <button type="button" data-tab="config" class="active">推送配置</button>
            <button type="button" data-tab="tenant">租户开关</button>
            <button type="button" data-tab="type">消息类型</button>
            <button type="button" data-tab="log">推送日志</button>
            <button type="button" data-tab="board">数据看板</button>
        </div>

        <div class="tab-panel active" data-tab-panel="config">
            <jsp:include page="<%=oaonesPanelConfigPath%>"/>
        </div>
        <div class="tab-panel" data-tab-panel="tenant">
            <%-- 租户开关：只列当前平台的租户（随右上角的平台切换变化） --%>
            <jsp:include page="/oaones/msgCenter/tenantSwitchPanel.jsp"/>
        </div>
        <div class="tab-panel" data-tab-panel="type">
            <jsp:include page="<%=oaonesPanelTypePath%>"/>
        </div>
        <div class="tab-panel" data-tab-panel="log">
            <jsp:include page="<%=oaonesPanelLogPath%>"/>
        </div>
        <div class="tab-panel" data-tab-panel="board">
            <jsp:include page="<%=oaonesPanelBoardPath%>"/>
        </div>
    </div>
</div>
<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
/* 页签切换（与企微消息中心同一套口径：面板片段各自暴露 load()，切到哪个载哪个） */
(function () {
    "use strict";
    var MODULES = {
        config: function () { return window.MsgConfigPanel; },
        tenant: function () { return window.MsgTenantSwitchPanel; },
        type: function () { return window.MsgTypePanel; },
        log: function () { return window.MsgLogPanel; },
        board: function () { return window.MsgBoardPanel; }
    };
    var loaded = {};
    function showToast(msg, mode, duration) {
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode, duration: duration }); }
    }
    window.MsgCenterToast = showToast;
    function loadTab(tab) {
        var getter = MODULES[tab];
        var panel = getter ? getter() : null;
        if (!panel || typeof panel.load !== "function") { return; }
        if (loaded[tab]) { return; }
        loaded[tab] = true;
        panel.load();
    }
    function switchTab(tab) {
        if (!MODULES[tab]) { return; }
        var tabs = document.querySelectorAll("#msgTabs button");
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].classList.toggle("active", tabs[i].getAttribute("data-tab") === tab);
        }
        var panels = document.querySelectorAll("[data-tab-panel]");
        for (var j = 0; j < panels.length; j++) {
            panels[j].classList.toggle("active", panels[j].getAttribute("data-tab-panel") === tab);
        }
        loadTab(tab);
    }
    window.MsgCenterTabs = { switchTo: function (tab) { switchTab(tab); } };

    function init() {
        var tabs = document.querySelectorAll("#msgTabs button");
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].addEventListener("click", function () {
                switchTab(this.getAttribute("data-tab"));
            });
        }
        var start = String(window.OAONES_MSG_TAB || "");
        switchTab(MODULES[start] ? start : "config");
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
