<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesSso" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.wecom.LoginConfigServiceImpl" %>
<%!
    /** HTML 属性转义（先转 &，避免已有实体被二次转义）。 */
    private String oaonesAttr(String value) {
        if (value == null) { return ""; }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
%>
<%
    /*
     * 应用中心 —— 桌面启动台（展示页，/oaones/appsyn/appCenter/Desktop.jsp）
     *
     * 把“应用列表”里启用中的应用以 **macOS 桌面样式**展示（布局见 css/desktop.css）：
     *   顶部菜单栏（只有“返回 OA”与时钟）+ 中间安全区里的桌面图标区（**没有底部 Dock**）；
     *   **应用图标只出现在中间区域**：图标区左右各留白 20%、上下各留白 10%，图标在该区域内居中换行；
     *   桌面图标 144px（原来 72px 的 2 倍），鼠标移上去放大，
     *   点击图标先由后端按该应用的“单点登录实现类”生成最终地址，再跳转到具体页面
     *   （没配实现类时直接跳转应用默认地址）。
     *
     * 本页只读：应用的新增/编辑/停用在 AppList.jsp（本页**不提供**该页入口——
     * 两个页面不设任何菜单关联、互不链接，各自单独打开、单独显示），
     * 因此这里不判断维护人身份：页面按登录人只读渲染，维护权限由后端接口把关；
     * 任何登录人都能打开本页（取 appc.desktop，只下发启用中的应用）。
     *
     * 先展示桌面，进入 N 秒后自动跳转：带 gopage 时跳到该站内地址，
     * 不带 gopage 时跳到站点根路径 /（N 取「应用中心 → 应用列表 → 启动台自动跳转」，
     * 未设置时回退 login.landing.gopage.seconds，默认 5 秒；0 = 不自动跳转）；
     * 用户点了应用图标或“返回 OA”则取消自动跳转。
     */
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    // 登录页带上来的 gopage：接受站内路径或完整的 http/https 网址；
    // 页面进入 N 秒后自动跳转（N 可配置）。#hash 里的页面参数整段保留。
    String oaonesGopage = OaonesSso.safeRedirectUrl(
            OaonesUtil.s(request.getParameter("gopage")), "");
    String oaonesGopageLower = oaonesGopage.toLowerCase();
    boolean oaonesGopageExternal = oaonesGopageLower.startsWith("http://")
            || oaonesGopageLower.startsWith("https://");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        // 未登录直接回统一登录页；带上 gopage，登录后仍先回桌面，再由桌面倒计时后跳目标页
        String oaonesLogin = "/oaones/appsyn/login";
        if (oaonesGopage.length() > 0) {
            oaonesLogin += "?gopage=" + java.net.URLEncoder.encode(oaonesGopage, "UTF-8");
        }
        oaonesRedirect(request, response, oaonesLogin);
        return;
    }
    // 秒数由「应用中心 → 应用列表」维护（可视化的滑块 / 秒数输入），留空时回退 properties 默认值；
    // 0 = 不自动跳转。读配置失败不会阻断桌面页（Service 内部已兜底）。
    int oaonesGopageSeconds = new LoginConfigServiceImpl().gopageSeconds();
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>桌面启动台 - 应用中心 - OAones</title>
    <%-- 桌面页不按角色显示提示语，但仍注入登录人，避免公共脚本按角色取值时报错 --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <%-- 本页用两套样式：style.css 提供提示框（toast）等公共样式，desktop.css 是桌面与图标的专用样式 --%>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <link rel="stylesheet" href="/oaones/appsyn/css/desktop.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
</head>
<body class="mac-body">

<%-- 自动跳转目标与秒数：有 gopage 时跳 gopage，否则由页面脚本回退到站点根路径 / --%>
<div id="oaonesDesktopMeta" data-gopage="<%=oaonesAttr(oaonesGopage)%>"
     data-seconds="<%=oaonesGopageSeconds%>" hidden></div>

<%-- 自动跳转倒计时：秒数 > 0 时显示 --%>
<div class="mac-countdown" id="macCountdown" hidden>
    <b id="macCountdownSeconds">0</b>秒后，跳转到<%=oaonesGopageExternal ? "目标网址" : "OA默认首页"%>
</div>

<%-- 顶部菜单栏：只保留右侧的“返回 OA”与时钟（js/appDesktop.js 每 10 秒刷新），
     **不放**苹果标与“应用中心”标题（按需求去掉）。
     本页不设任何菜单关联：不放“桌面启动台 / 管理应用”等页签或入口，
     也不与「应用列表」互链（两个页面各自单独显示）。 --%>
<div class="mac-menubar">
    <span class="mac-menubar-right">
        <a href="/wui/index.html#/main">返回 OA</a>
        <span class="mac-clock" id="macClock"></span>
    </span>
</div>

<%-- 桌面图标区（js/appDesktop.js 渲染）：只占中间的“安全区”，四周留白不出现图标 --%>
<div class="mac-desktop" id="macDesktop"></div>

<%-- 空状态：还没有启用中的应用时提示去哪里添加 --%>
<div class="mac-empty" id="macEmpty" style="display:none;"></div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/appsyn/js/appDesktop.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
/* 页壳初始化：自动跳转目标从上方隐藏节点读取；应用列表初始化放在 DOMContentLoaded。 */
(function () {
    var meta = document.getElementById("oaonesDesktopMeta");
    var target = meta ? String(meta.getAttribute("data-gopage") || "").trim() : "";
    var effectiveTarget = target || "/";
    var targetPath = effectiveTarget.split("#")[0].split("?")[0];
    var seconds = meta ? parseInt(meta.getAttribute("data-seconds") || "0", 10) : 0;
    if (!isFinite(seconds) || seconds < 0) { seconds = 0; }
    // 指回桌面/登录页时不再自动跳，避免倒计时循环
    var skip = seconds <= 0
        || targetPath === "/oaones/appsyn/desktop" || targetPath === "/oaones/appsyn/desktop/"
        || targetPath === "/oaones/appsyn/appCenter/Desktop.jsp"
        || targetPath === "/oaones/appsyn/login" || targetPath === "/oaones/appsyn/login/"
        || targetPath === "/oaones/appsyn/login/login.jsp";
    if (!skip) {
        var countdownEl = document.getElementById("macCountdown");
        var countdownSecondsEl = document.getElementById("macCountdownSeconds");
        var remaining = seconds;
        function paintCountdown() {
            if (countdownSecondsEl) { countdownSecondsEl.textContent = String(remaining); }
            if (countdownEl) { countdownEl.hidden = false; }
        }
        function goTarget() {
            try { window.location.replace(effectiveTarget); }
            catch (e) { window.location.href = effectiveTarget; }
        }
        paintCountdown();
        var autoJump = window.setInterval(function () {
            remaining -= 1;
            if (remaining <= 0) {
                window.clearInterval(autoJump);
                if (countdownEl) { countdownEl.hidden = true; }
                goTarget();
                return;
            }
            paintCountdown();
        }, 1000);
        // 用户已经点了应用图标或“返回 OA”，取消自动跳转，避免覆盖用户操作
        document.addEventListener("click", function (e) {
            var a = e.target && e.target.closest ? e.target.closest("a") : null;
            if (a) {
                window.clearInterval(autoJump);
                if (countdownEl) { countdownEl.hidden = true; }
            }
        }, true);
    }
    function init() {
        if (window.MacDesktop && typeof window.MacDesktop.load === "function") {
            window.MacDesktop.load();
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
