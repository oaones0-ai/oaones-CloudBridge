<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    String oaonesPlatform = request.getParameter("platform");
    if (!"dingtalk".equalsIgnoreCase(oaonesPlatform) && !"feishu".equalsIgnoreCase(oaonesPlatform)) {
        oaonesPlatform = "wecom";
    }
    oaonesPlatform = oaonesPlatform.toLowerCase();
    String oaonesNavbar = "dingtalk".equals(oaonesPlatform) ? "/oaones/DingTalk/navbar.jsp"
            : ("feishu".equals(oaonesPlatform) ? "/oaones/Feishu/navbar.jsp" : "/oaones/WeCom/navbar.jsp");
    String oaonesCfgId = request.getParameter("cfg_id");
    if (oaonesCfgId == null) {
        oaonesCfgId = "";
    }
    oaonesCfgId = oaonesCfgId.trim();
    if (!oaonesCfgId.matches("\\d+")) {
        oaonesCfgId = "";
    }
%>

<%@ include file="/oaones/redirect.inc.jsp" %>
<%
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>OA 分部 - 租户映射 - OAones</title>
    <script>
        window.OAONES_USER = {
            id: "<%=user.getUID()%>",
            sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%>
        };
        window.OAONES_ORGMAP = {
            initialCfgId: "<%=oaonesCfgId%>",
            apiUrl: "/oaones/orgMap/api.jsp"
        };
    </script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<jsp:include page="<%=oaonesNavbar%>">
    <jsp:param name="active" value="orgMap"/>
    <jsp:param name="cfg_id" value="<%=oaonesCfgId%>"/>
</jsp:include>
<div class="page">
    <div class="card">
        <div class="orgmap-head">
            <div>
                <h2 class="orgmap-title">OA 分部 - 租户映射</h2>
                <p class="orgmap-subtitle">一张表同时维护企业微信、钉钉、飞书三个平台的分部租户映射；只保存映射，不执行同步。</p>
            </div>
            <span class="help-tip" id="orgMapHelpTip" data-help="orgMapHelpText"
                  role="button" tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span>
        </div>
        <div class="help-source" id="orgMapHelpText">
            每行是一个 OA 分部，表格横向列出企业微信、钉钉、飞书三个平台的租户映射。
            OA 分部支持“树状 / 列表式”切换：树状按上级分部逐级缩进，可展开或收起；
            列表式显示“父分部 / 子分部”的完整路径，并按分部层级排列，同一父分部的子分部集中显示且紧跟父分部下方。
            同一平台内严格一对一：同一个 OA 分部只能映射一个租户，同一个租户也只能映射一个 OA 分部；
            三个平台之间互不影响，因此同一分部可以分别映射三个平台的租户。
            每个平台列直接显示租户选择框：未映射时选择租户即保存，已映射时直接选择其他租户即更换；
            已被其他 OA 分部占用的租户不可选。点击“解绑”只删除该平台的这一条映射。
            状态筛选按 OA 分部判断：“有绑定”表示至少一个平台已有映射，“无绑定”表示三个平台均未映射；
            状态条件可与分部、租户搜索同时使用。
            本页只维护映射关系，不创建部门、不修改人员绑定，也不触发任何同步。
        </div>

        <div class="bind-stats orgmap-stats" id="orgMapStats"></div>

        <div class="filter-bar">
            <input type="text" id="orgMapKeyword" class="orgmap-keyword" autocomplete="off"
                   placeholder="搜索 OA 分部、租户名称或 ID">
            <label for="orgMapStatus" class="filter-label">状态</label>
            <select id="orgMapStatus" class="orgmap-status-filter">
                <option value="">全部状态</option>
                <option value="bound">有绑定</option>
                <option value="unbound">无绑定</option>
            </select>
            <div class="view-switch mini orgmap-view-switch" role="group" aria-label="OA 分部显示方式">
                <button type="button" class="view-btn orgmap-view-btn active" data-orgmap-view="tree"
                        aria-pressed="true">树状</button>
                <button type="button" class="view-btn orgmap-view-btn" data-orgmap-view="list"
                        aria-pressed="false">列表式</button>
            </div>
            <button type="button" class="btn small secondary" id="orgMapRefreshBtn">刷新</button>
            <span class="orgmap-filter-tip">绑定状态按三个平台综合判断；列表按父分部路径排列</span>
        </div>

        <div class="table-wrap orgmap-table-wrap">
            <table class="grid orgmap-grid orgmap-all-platforms">
                <thead>
                <tr>
                    <th class="orgmap-col-org">OA 分部</th>
                    <th class="orgmap-col-map">企业微信租户</th>
                    <th class="orgmap-col-map">钉钉租户</th>
                    <th class="orgmap-col-map">飞书租户</th>
                </tr>
                </thead>
                <tbody id="orgMapBody"></tbody>
            </table>
            <div class="empty" id="orgMapEmpty" style="display:none;">暂无可展示的 OA 分部</div>
        </div>
    </div>
</div>

<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/js/orgMap.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
