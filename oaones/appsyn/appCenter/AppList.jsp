<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.AppCenterServiceImpl" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    /*
     * 应用中心 —— 应用列表（管理页，/oaones/appsyn/appCenter/AppList.jsp）
     *
     * 一条记录 = 一个应用（第三方系统 / 外部页面）：
     *   应用名称 + 默认访问地址 + 单点登录实现类（指向实现“对方身份认证”的具体类，留空 = 不做单点登录）。
     * 页面形态与「微信设置」一致：**独立页面、自带标题头**，不引模块导航（navbar.jsp）；
     * 入口由 E9 后台「菜单管理」配本页 URL（或直接访问本页地址）。
     *
     * 与「桌面启动台」（appCenter/Desktop.jsp）**互不链接、各管各的**：
     * 本页不设任何菜单关联（页头只有本页一项，不放“桌面启动台”页签、不放“预览桌面”按钮），
     * 两个页面各自单独打开、单独显示。
     * 页面主体（工具栏 / 列表 / 新建编辑弹框 + 脚本）在片段 appListPanel.jsp 里。
     * 页面顶部另有一块「启动台自动跳转」（从登录设置移过来）：只给系统管理员 / appc.admin.ids，
     * 通过 appc.gopage / appc.gopageSave 读写，只改 gopage_seconds 一列。
     *
     * 权限（两级）：
     *   页面准入：系统管理员（user.id=1）、oaones.appsyn.properties#appc.admin.ids 中列出的人员，
     *             以及**被指定为某个应用创建人 / 维护人**的人员可进本页（能进本页就能新建应用）；
     *             其他登录人打开本页只看得到“无权限”提示。
     *   应用级：应用由**创建人 + 维护人**维护（见 appListPanel.jsp 的「维护人」字段）——
     *           列表只列出自己创建或自己维护的应用，编辑 / 启停 / 删除也只对它们生效；
     *           系统管理员可查看与维护全部应用。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    boolean oaonesAppcCanMaintain = new AppCenterServiceImpl().canMaintain(String.valueOf(user.getUID()));
    boolean oaonesAppcGopageCanMaintain = new AppCenterServiceImpl().canMaintainGopage(String.valueOf(user.getUID()));
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>应用列表 - 应用中心 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 一级标题只属于本页（“应用中心”）：只列本页一项，
     不与租户设置 / 消息设置 / 登录设置等菜单互链，也不与「桌面启动台」互链（两个页面各自单独显示） --%>
<div class="navbar">
    <h1>应用中心</h1>
    <div class="nav">
        <a class="active" href="/oaones/appsyn/appCenter/AppList.jsp">应用列表</a>
    </div>
</div>
<div class="page">
    <div class="card">
<% if (oaonesAppcCanMaintain) { %>
        <%-- 主体：工具栏 + 列表 + 新建/编辑弹框（片段内含该菜单的标记与其脚本） --%>
        <jsp:include page="appListPanel.jsp">
            <jsp:param name="sysAdmin" value="<%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%>"/>
            <jsp:param name="gopageCanMaintain" value="<%=oaonesAppcGopageCanMaintain%>"/>
        </jsp:include>
<% } else { %>
        <p class="notice" style="margin:0 0 10px;">你不是「应用中心」的维护人，本页不展示应用清单。
            需要新增应用时，请联系管理员把你加入 oaones.appsyn.properties#appc.admin.ids，
            或让他把某个应用指定给你维护（被指定为创建人 / 维护人后即可进入本页维护该应用；
            已有应用只能由它的创建人 / 维护人维护，系统管理员可维护全部应用）。
            本轮登录人：<b><%=weaver.general.Util.toHtml(user.getLastname())%></b>（ID <%=user.getUID()%>）。</p>
<% } %>
    </div>
</div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
/* 页壳只做初始化：片段脚本只声明函数、不在解析期访问 DOM（见 skill.md 第一节第 7 条）。 */
(function () {
    function init() {
        if (window.AppListPanel && typeof window.AppListPanel.load === "function") {
            window.AppListPanel.load();
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
