<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    /*
     * 同步中心：**一个通用页面**（一级菜单与租户工作区都用这一个地址）。
     *
     *   /oaones/syncCenter/syncCenter.jsp?platform=wecom|dingtalk|feishu[&cfg_id=<租户ID>]
     *
     * * platform 决定平台：接口入口（WeCom / DingTalk / Feishu 的 api.jsp）、页面文案、
     *   同步设置与计划任务的 type（0 企微 / 1 钉钉 / 2 飞书）；不传默认企微。
     *   右上角“平台切换”就是同一个地址换 platform（只有**不带 cfg_id** 的独立菜单口径才显示，
     *   带 cfg_id 进来时当前租户锁定，不显示平台标识与平台切换）。
     * * cfg_id 决定租户：从租户工作区点“同步中心”进来时自动带上（该租户锁定、页内不能切）；
     *   不带 cfg_id 时按本人可维护的全部租户展示。
     *
     * 本页只做“页壳”，页面正文由三个平台共用的 syncPlanPanel.jsp 提供，从上到下是：
     *   1) 上下文（租户 / 配置范围 / 内置调度器状态）；
     *   2) 编辑同步设置：同步方向 + 同步范围（OA 分部/部门 ⇄ 平台部门）+ 计划任务等，
     *      计划任务有两种方式（**轮询间隔（分钟）** 或 **高级模式（cron 表达式）**），
     *      保存后由内置调度器按这条映射关系与这个频率同步；
     *   3) 同步设置列表：**就在“编辑同步设置”表单下方**（同一张卡片，中间没有单独的板块与说明文字），
     *      一行一条“同步规则”（**首列是租户名称**，然后是方向 + 同步范围 + 计划任务 + 口径），
     *      独立一级菜单里列出本人可维护租户的设置，同一“租户 + 方向”可以有多条规则，
     *      计划任务按每条规则自己的方式分别执行。
     * 页面里**不铺说明文字**：页面说明挂在一级标题“同步中心”后的 ! 图标上（#cfgHelpText）。
     * 交互脚本是共用的 js/syncPlan.js；因此页面本身与平台无关，不需要为平台各写一个页壳。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    // 租户：带 cfg_id 时作为当前租户初值（仍会按维护人校验，取不到则退回第一个可维护租户）
    String oaonesCfgId = request.getParameter("cfg_id");
    if (oaonesCfgId == null) {
        oaonesCfgId = "";
    }
    oaonesCfgId = oaonesCfgId.trim();
    if (!oaonesCfgId.matches("\\d+")) {
        oaonesCfgId = "";
    }
    // 平台：企微 / 钉钉 / 飞书（不传或传错都按企微）
    String oaonesPlatform = request.getParameter("platform");
    if (oaonesPlatform == null) {
        oaonesPlatform = "";
    }
    oaonesPlatform = oaonesPlatform.trim().toLowerCase();
    if (!"dingtalk".equals(oaonesPlatform) && !"feishu".equals(oaonesPlatform)) {
        oaonesPlatform = "wecom";
    }
    boolean oaonesDd = "dingtalk".equals(oaonesPlatform);
    boolean oaonesFs = "feishu".equals(oaonesPlatform);
    String oaonesSyncLabel = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企业微信");
    String oaonesSyncShort = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企微");
    int oaonesSyncType = oaonesFs ? 2 : (oaonesDd ? 1 : 0);
    String oaonesApiUrl = oaonesFs ? "/oaones/Feishu/api.jsp"
            : (oaonesDd ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    // 当前平台的导航片段（一级标题 + 二级菜单）：从租户工作区（带 cfg_id）进来时引它，
    // 让“同步中心”与「分部绑定 / 部门绑定 / 人员绑定」等页签长得完全一样，
    // 内容就落在二级菜单（页签）下方，不再是弹层 / 页内 iframe 块。
    String oaonesNavbarPath = oaonesFs ? "/oaones/Feishu/navbar.jsp"
            : (oaonesDd ? "/oaones/DingTalk/navbar.jsp" : "/oaones/WeCom/navbar.jsp");
    String oaonesCfgAllLabel = oaonesFs ? "全部飞书租户" : (oaonesDd ? "全部钉钉租户" : "全部企微租户");
    // 同步设置面板（三个平台共用一份）按平台取文案：用请求属性传，避免 jsp:param 里再嵌表达式
    request.setAttribute("oaonesSyncPlatform", oaonesPlatform);
    // 平台切换链接要保留当前租户
    String oaonesCfgQuery = oaonesCfgId.length() == 0 ? "" : ("&cfg_id=" + oaonesCfgId);
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>同步中心（<%=oaonesSyncLabel%>） - OAones</title>
    <%-- 平台决定接口入口与计划任务的 type：企微 → WeCom/api.jsp，钉钉 → DingTalk/api.jsp，飞书 → Feishu/api.jsp --%>
    <script>window.OAONES_API_URL = "<%=oaonesApiUrl%>";
    window.OAONES_CFG_ALL_LABEL = "<%=oaonesCfgAllLabel%>";
    window.OAONES_SYNC_TYPE = <%=oaonesSyncType%>;
    window.OAONES_SYNC_SHORT = "<%=oaonesSyncShort%>";
    window.OAONES_SYNC_LABEL = "<%=oaonesSyncLabel%>";
    window.OAONES_SYNC_PLATFORM = "<%=oaonesPlatform%>";</script>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };
    window.OAONES_CFG_ID = "<%=oaonesCfgId%>";</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/datetime.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 同步中心既是**独立的一级菜单**，也是租户工作区二级菜单里的“同步中心”（带 cfg_id）：
     独立一级菜单口径（不在某个租户的工作区里）出标题栏 + 右上角按平台切换；
     页面说明（! 图标）挂在一级标题上，页面里不单列说明行。
     租户工作区口径（地址带 cfg_id）**引当前平台的导航片段**：一级标题 + 二级菜单（页签）
     与其它功能页完全一致，当前页签“同步中心”高亮，页面正文就是下面的两块卡片——
     和其它页签（分部绑定 / 部门绑定 / 人员绑定…）一样是**整页跳转、内容落在菜单下方**，
     不是弹层、也不是页内 iframe 块。 --%>
<% if (oaonesCfgId.length() == 0) { %>
<div class="navbar">
    <h1>同步中心<span class="help-tip inline" id="cfgHelpTip" data-help="cfgHelpText" role="button" tabindex="0"
              title="查看页面说明" aria-label="查看页面说明">?</span></h1>
    <div class="nav">
        <span class="view-switch" title="切换平台：企微 / 钉钉 / 飞书各有一套租户与同步设置，页面与脚本是同一份">
            <a class="view-btn<%= oaonesDd || oaonesFs ? "" : " active" %>"
               href="/oaones/syncCenter/syncCenter.jsp?platform=wecom<%=oaonesCfgQuery%>">企业微信</a>
            <a class="view-btn<%= oaonesDd ? " active" : "" %>"
               href="/oaones/syncCenter/syncCenter.jsp?platform=dingtalk<%=oaonesCfgQuery%>">钉钉</a>
            <a class="view-btn<%= oaonesFs ? " active" : "" %>"
               href="/oaones/syncCenter/syncCenter.jsp?platform=feishu<%=oaonesCfgQuery%>">飞书</a>
        </span>
    </div>
</div>
<% } else { %>
<jsp:include page="<%= oaonesNavbarPath %>">
    <jsp:param name="active" value="syncCenter"/>
    <jsp:param name="cfg_id" value="<%= oaonesCfgId %>"/>
</jsp:include>
<% } %>
<div class="page">
    <%-- 页面说明按登录人角色分两套：系统管理员（全部租户）与维护人/普通用户（只能选本人维护的租户） --%>
    <div class="help-source" id="cfgHelpText"><% if (OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))) { %>租户分权：你是<b>系统管理员</b>（user.id=1）或超管角色成员，可以选择并同步全部<%=oaonesSyncShort%>租户（含未指定维护人的租户）。
            维护人在“<%=oaonesSyncShort%>租户配置”页设置（支持多个 OA 员工）；
            同步设置同样只展示你可维护租户的设置。
        <% } else { %>租户分权：你只能选择并同步本人被指定为维护人的<%=oaonesSyncShort%>租户，未指定维护人的租户不会出现在这里。
            维护人在“<%=oaonesSyncShort%>租户配置”页设置（支持多个 OA 员工）；
            同步设置同样只展示你可维护租户的设置。
        <% } %><b>从租户工作区点“同步中心”进来时，当前租户锁定为该租户</b>：
            页内的租户只读展示、不能切换租户（要换租户请回租户列表重新点开另一个租户）。
        <b>上方的“租户”下拉必选一个具体租户</b>（没有“全部可维护租户”一档），
            它决定「编辑同步设置」里的改动保存到哪个租户；<b>保存后立刻显示在表单下方紧跟着的
            “同步设置列表”里</b>（同一张卡片、中间没有单独的板块，也不铺说明文字）——
            一行一条规则、<b>首列显示租户名称</b>（列出你可维护租户的设置，从租户工作区进来时只列当前租户）；
            <b>同步范围在列表里拆成「源组织 / 目标组织」两栏</b>：
            <b>源组织一行一个组织</b>（多个时按行显示，超过 3 行折叠、移到鼠标上看全部），
            <b>目标组织只有一个落点</b>（A = 所选<%=oaonesSyncShort%>部门，未选 = 该租户 root 节点；
            B = 「落点：分部「…」› 部门「…」」，只选落点分部时新建部门作为该分部的顶级部门）；
            两栏的“含下级 / 全量同步”口径见列表下方说明。
        <b>「计划任务」有两种方式</b>（“同步方式”单选切换，两种取值一起保存、来回切不丢值）：
            <b>① 轮询间隔（分钟，默认）</b>——填“每 N 分钟同步一次”（5 ~ 1440 分钟之间），
            下一次执行 = 上一次执行时刻 + N 分钟，现场不需要懂 cron；
            <b>② 高级模式（cron）</b>——直接写 cron 表达式（Quartz 口径：秒 分 时 日 月 周，
            例 <span class="mono">0 0 2 * * ?</span> = 每天 02:00、<span class="mono">0 0/30 * * * ?</span>
            = 每 30 分钟）。点“预测后 10 次”可按当前方式核对<b>实际会执行的时间</b>（显示在表单右侧栏）。
        “配置范围”只有<b>当前租户配置</b>一档，不再提供“全局默认配置”
            （早期遗留的全局默认行仍会在列表里列出并在首列标注，可“覆盖为当前租户配置”）。
        <% if (oaonesCfgId.length() == 0) { %><b>本页是三个平台共用的一个页面</b>：右上角切换平台（企微 / 钉钉 / 飞书），
            页面上的同步设置、同步范围与计划任务都按当前平台展示。
        <% } else { %><b>当前租户已锁定</b>：页面上不显示平台标识与平台切换（同步设置只按该租户所属平台展示）。
        <% } %></div>
    <p class="notice" id="cfgPermTip" style="display:none;color:var(--oa-warning);"></p>

<%-- 同步设置列表 + 编辑同步设置：三个平台共用同一份片段 --%>
<jsp:include page="/oaones/syncCenter/syncPlanPanel.jsp"/>
</div>

<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/js/syncPlan.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
