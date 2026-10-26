<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String oaonesActive = request.getParameter("active");
    if (oaonesActive == null) {
        oaonesActive = "";
    }
    // 当前租户：从“钉钉租户”首页点开某个租户后，地址上带 cfg_id（页面也可用 <jsp:param> 显式传入），
    // 带 cfg_id = 已进入某个租户的上下文：一级标题右侧显示“当前租户名 + 设置”，
    // 二级标题显示该租户的功能菜单（页签），各页签地址也带上它，切换功能时保持同一租户。
    String oaonesCfgId = request.getParameter("cfg_id");
    if (oaonesCfgId == null) {
        oaonesCfgId = "";
    }
    oaonesCfgId = oaonesCfgId.trim();
    if (!oaonesCfgId.matches("\\d+")) {
        oaonesCfgId = "";
    }
    String oaonesCfgQuery = oaonesCfgId.length() == 0 ? "" : ("?cfg_id=" + oaonesCfgId);
    boolean oaonesTenantCtx = oaonesCfgId.length() > 0;
    // 二级标题（租户菜单）当前项：“默认绑定规则 / 规则列表”同属“人员绑定”
    boolean oaonesTenantCtxUser = "userBind".equals(oaonesActive) || "userRule".equals(oaonesActive);
%>
<div class="navbar">
    <h1><a href="/oaones/index/TenantIndex.jsp?platform=dingtalk" title="返回钉钉租户列表">OA ⇄ 钉钉</a></h1>
<% if (!oaonesTenantCtx) { %>
    <div class="nav">
        <%-- 一级标题右侧只保留本菜单自己的入口（“钉钉租户设置”）：
             “消息设置 / 登录设置”是**另外两条独立的一级菜单**（各有 URL），
             这里不再互链，避免从一个菜单串到另一个菜单 —— 见 README「四个独立一级菜单」一节 --%>
        <a class="<%= "orgMap".equals(oaonesActive) ? "" : "active" %>"
           href="/oaones/index/TenantIndex.jsp?platform=dingtalk">钉钉租户设置</a>
        <a class="<%= "orgMap".equals(oaonesActive) ? "active" : "" %>"
           href="/oaones/orgMap/orgMap.jsp?platform=dingtalk">分部映射</a>
    </div>
<% } else { %>
    <%-- 一级标题右侧：当前租户名 + 设置（点开租户后不再出现“钉钉租户”菜单，回列表点标题） --%>
    <span class="navbar-tenant">
        <span class="navbar-tenant-label">当前租户</span>
        <b id="navTenantName" data-cfg-id="<%=oaonesCfgId%>" title="当前钉钉租户">#<%=oaonesCfgId%></b>
        <a class="btn small<%= "tenantSetting".equals(oaonesActive) ? "" : " secondary" %>"
           href="/oaones/index/TenantDetail.jsp?platform=dingtalk<%=oaonesCfgQuery.length() > 0 ? "&cfg_id=" + oaonesCfgId : ""%>" title="查看 / 修改该租户配置">设置</a>
    </span>
<% } %>
</div>
<% if (oaonesTenantCtx) { %>
<%-- 二级标题：当前租户的功能菜单（页签），点页签进入对应功能页并保持同一租户；
     顺序固定为 同步中心 / 部门绑定 / 人员绑定 / 快照列表 / 应用绑定（见 skill.md 第一节第 15 条） --%>
<div class="tenant-menus">
    <%-- 同步中心：与「部门绑定 / 人员绑定」等页签**完全同一种打开方式**——
         直接整页跳转（不是弹层，也不是页内 iframe 块），内容就落在二级菜单（页签）下方；
         落地页是三个平台通用的 /oaones/syncCenter/syncCenter.jsp?platform=...&cfg_id=<当前租户>，
         该页带 cfg_id 时会引本导航片段（一级标题 + 二级菜单），外观与其它页签一致。 --%>
    <a class="tenant-menu<% if ("syncCenter".equals(oaonesActive)) { %> active<% } %>"
       href="/oaones/syncCenter/syncCenter.jsp?platform=dingtalk<%=oaonesCfgQuery.length() > 0 ? "&cfg_id=" + oaonesCfgId : ""%>">同步中心</a>
    <a class="tenant-menu<% if ("syncResult".equals(oaonesActive)) { %> active<% } %>"
       href="/oaones/syncCenter/syncResult.jsp?platform=dingtalk<%=oaonesCfgQuery.length() > 0 ? "&cfg_id=" + oaonesCfgId : ""%>">同步结果</a>
    <a class="tenant-menu<% if ("deptBind".equals(oaonesActive)) { %> active<% } %>" href="/oaones/deptBind/deptBind.jsp?platform=dingtalk<%=oaonesCfgQuery.length() > 0 ? "&cfg_id=" + oaonesCfgId : ""%>">部门绑定</a>
    <a class="tenant-menu<% if ("orgMap".equals(oaonesActive)) { %> active<% } %>"
       href="/oaones/orgMap/orgMap.jsp?platform=dingtalk<%=oaonesCfgQuery.length() > 0 ? "&cfg_id=" + oaonesCfgId : ""%>">分部映射</a>
    <a class="tenant-menu<% if (oaonesTenantCtxUser) { %> active<% } %>" href="/oaones/userBind/userBind.jsp?platform=dingtalk<%=oaonesCfgQuery.length() > 0 ? "&cfg_id=" + oaonesCfgId : ""%>">人员绑定</a>
    <%-- 快照列表：只读展示该租户的钉钉组织架构与人员快照，右上角可“更新快照”重新从钉钉同步 --%>
    <a class="tenant-menu<% if ("snapshot".equals(oaonesActive)) { %> active<% } %>" href="/oaones/snapshot/snapshot.jsp?platform=dingtalk<%=oaonesCfgQuery.length() > 0 ? "&cfg_id=" + oaonesCfgId : ""%>">快照列表</a>
    <%-- 应用绑定（钉钉工作台应用 → OA 页面、免登录）是租户级功能，与绑定/同步并列；
         统一消息中心（“消息设置”）不在这里，从一级标题右侧的模块菜单进入 --%>
    <a class="tenant-menu<% if ("appBind".equals(oaonesActive)) { %> active<% } %>" href="/oaones/appBind/DingTalk/appBind.jsp<%=oaonesCfgQuery%>">应用绑定</a>
</div>
<script>
/**
 * 一级标题右侧的“当前租户名”：租户名称要等接口返回，这里按地址上的 cfg_id
 * 从 cfg.list（已按维护人过滤）里取名称回填；取不到时保持显示 #租户ID。
 * 本脚本在页中执行，接口脚本 api.js 在页尾执行，故放到 DOMContentLoaded 之后再取值。
 */
(function () {
    function fillNavTenantName() {
        var el = document.getElementById("navTenantName");
        if (!el || !window.Oaones) { return; }
        var cfgId = el.getAttribute("data-cfg-id");
        if (!cfgId) { return; }
        Oaones.api("cfg.list", {}, function (res) {
            if (!res || res.code !== 0) { return; }
            var rows = res.data || [];
            for (var i = 0; i < rows.length; i++) {
                if (String(rows[i].id) === String(cfgId)) {
                    el.textContent = rows[i].corp_name || ("#" + cfgId);
                    el.title = rows[i].corp_name || ("#" + cfgId);
                    return;
                }
            }
        });
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", fillNavTenantName);
    } else {
        fillNavTenantName();
    }
})();
</script>

<% } %>
