<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    // “应用绑定”是租户二级菜单（二级标题里的租户功能菜单）的一项：
    // 从租户工作区各页点“应用绑定”进来时地址带 cfg_id，作为当前租户的初值
    // （仍会校验是否在本人可维护租户里，取不到则回退第一个可维护租户；只影响默认选中哪个租户，不放宽权限）。
    // 不带 cfg_id 直接访问（历史书签）同样可用：一级标题右侧显示模块菜单，页内租户选择框默认第一个租户。
    String oaonesCfgId = request.getParameter("cfg_id");
    if (oaonesCfgId == null) {
        oaonesCfgId = "";
    }
    oaonesCfgId = oaonesCfgId.trim();
    if (!oaonesCfgId.matches("\\d+")) {
        oaonesCfgId = "";
    }
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>应用绑定 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin）；
         当前租户由地址参数 cfg_id 决定（见 skill.md 第一节第 14 / 15 条） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };
    window.OAONES_CFG_ID = "<%=oaonesCfgId%>";</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <%-- 密码 / 密钥框的掩码口径：编辑时只显示 **** --%>
    <script src="/oaones/appsyn/js/secret.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 两级标题由 navbar.jsp 统一渲染：一级标题 = 模块标题 + （带租户时）当前租户名与“设置”，
     二级标题 = 该租户的功能菜单（同步中心 / 部门绑定 / 人员绑定 / 快照列表 / 应用绑定），
     本页是二级标题“应用绑定”的落地页。 --%>
<jsp:include page="/oaones/appsyn/WeCom/navbar.jsp">
    <jsp:param name="active" value="appBind"/>
</jsp:include>
<div class="page">
    <div class="card">
        <%-- 版式与租户工作区其它功能页（人员绑定 / 部门绑定）一致：卡片里不放模块标题行，
             卡片内第一行工具栏（企业微信租户选择 + 页面说明 + 新建应用绑定 + 刷新）与
             应用绑定列表、新建/编辑弹框都在片段 appBindPanel.jsp 里；
             页面说明正文按登录人角色两套（第 4 条），所以把是否系统管理员传给片段 --%>
        <jsp:include page="appBindPanel.jsp">
            <jsp:param name="sysAdmin" value="<%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%>"/>
        </jsp:include>
    </div>
</div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
/* 页壳只做初始化：片段脚本只声明函数、不在解析期访问 DOM（见 skill.md 第一节第 7 条）。 */
(function () {
    function init() {
        if (window.AppBindPanel && typeof window.AppBindPanel.load === "function") {
            window.AppBindPanel.load();
        }
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
