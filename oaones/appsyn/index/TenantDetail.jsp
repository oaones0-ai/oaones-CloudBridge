<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    /* 本页三个平台共用：平台由地址决定（?platform=wecom|dingtalk|feishu，不传按企微）；
       接口入口、平台类型、导航片段与文案都由下面变量派生，页面逻辑完全一致。 */
    String oaonesPBPlatform = request.getParameter("platform");
    if (oaonesPBPlatform == null || oaonesPBPlatform.trim().length() == 0) {
        oaonesPBPlatform = (String) request.getAttribute("oaonesPBPlatform");
    }
    if (oaonesPBPlatform == null) {
        oaonesPBPlatform = "";
    }
    oaonesPBPlatform = oaonesPBPlatform.trim().toLowerCase();
    boolean oaonesPBDd = "dingtalk".equals(oaonesPBPlatform);
    boolean oaonesPBFs = "feishu".equals(oaonesPBPlatform);
    String oaonesPBFull = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企业微信");
    String oaonesPBShort = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企微");
    int oaonesPBType = oaonesPBFs ? 2 : (oaonesPBDd ? 1 : 0);
    String oaonesPBApiUrl = oaonesPBFs ? "/oaones/appsyn/Feishu/api.jsp"
            : (oaonesPBDd ? "/oaones/appsyn/DingTalk/api.jsp" : "/oaones/appsyn/WeCom/api.jsp");
    String oaonesPBNavbarPath = oaonesPBFs ? "/oaones/appsyn/Feishu/navbar.jsp"
            : (oaonesPBDd ? "/oaones/appsyn/DingTalk/navbar.jsp" : "/oaones/appsyn/WeCom/navbar.jsp");
    request.setAttribute("oaonesPBPlatform", oaonesPBFs ? "feishu" : (oaonesPBDd ? "dingtalk" : "wecom"));
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
    boolean oaonesPBHasAgent = !oaonesPBFs;                       // 飞书租户没有 AgentId
    String oaonesPBRootDeptDefault = oaonesPBFs ? "0" : "";       // 飞书根部门取 0
    String oaonesPBIdLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "企业ID");
    String oaonesPBFieldList = oaonesPBFs ? "App ID"
            : (oaonesPBDd ? "AppKey / AgentId" : "企业ID / AgentId");
    String oaonesPBTestLoadingTip = oaonesPBDd ? "连通测试中，数据较多，请不要关闭页面"
            : "连通测试中，请不要关闭页面";
%>

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
    // 当前租户：从首页租户块进入时带 cfg_id，用于拼各功能菜单地址并在导航里保持同一租户
    String oaonesCfgId = request.getParameter("cfg_id");
    if (oaonesCfgId == null) { oaonesCfgId = ""; }
    oaonesCfgId = oaonesCfgId.trim();
    if (!oaonesCfgId.matches("\\d+")) { oaonesCfgId = ""; }
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <%-- 平台决定接口入口（AJAX 统一入口）与页面文案：三个平台共用本页，只换 platform 参数 --%>
    <script>window.OAONES_API_URL = "<%= oaonesPBApiUrl %>";
    window.OAONES_CFG_ALL_LABEL = "全部<%= oaonesPBFull %>租户";
    window.OAONES_CFG_TYPE = <%= oaonesPBType %>;
    var P_FULL = "<%= oaonesPBFull %>", P_SHORT = "<%= oaonesPBShort %>";
    var P_PLATFORM = "<%= oaonesPBPlatform %>";
    var P_KeyLabel = "<%= oaonesPBKeyLabel %>", P_AppIdLabel = "<%= oaonesPBAppIdLabel %>";
    var P_IdLabel = "<%= oaonesPBIdLabel %>";
    var P_RootDeptDefault = "<%= oaonesPBRootDeptDefault %>";
    var HAS_AGENT = <%= oaonesPBHasAgent %>;</script>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>租户设置 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 同一套两级标题：一级=模块标题 + 当前租户名/设置，二级=该租户的功能菜单（页签）。
     本页是租户“设置”：二级标题里不放平台租户主信息以外的功能内容，主信息就在下面的卡片里 --%>
<jsp:include page="<%= oaonesPBNavbarPath %>">
    <jsp:param name="active" value="tenantSetting"/>
    <jsp:param name="cfg_id" value="<%=oaonesCfgId%>"/>
</jsp:include>
<div class="page">
    <div class="card" id="permCard" style="display:none;">
        <p class="notice" id="permTip"></p>
        <button class="btn secondary" onclick="location.href='/oaones/appsyn/index/TenantIndex.jsp?platform=<%= oaonesPBPlatform %>'">返回<%= oaonesPBShort %>租户</button>
    </div>

    <div class="card" id="tenantCard" style="display:none;">
        <div class="toolbar" style="justify-content:space-between;">
            <div class="toolbar-left">
                <h3 style="margin:2px 0;"><span id="tenantName">—</span></h3>
                <span id="tenantState"></span>
                <span class="help-tip" id="detailHelpTip" data-help="detailHelpText" role="button" tabindex="0"
                      title="查看页面说明" aria-label="查看页面说明">?</span>
            </div>
            <div>
                <button class="btn secondary" onclick="location.href='/oaones/appsyn/index/TenantIndex.jsp?platform=<%= oaonesPBPlatform %>'">返回<%= oaonesPBShort %>租户</button>
                <button class="btn secondary" id="testBtn">连通测试</button>
                <button class="btn secondary" id="editBtn">编辑租户配置</button>
                <button class="btn ghost-danger" id="delBtn">删除租户</button>
            </div>
        </div>
        <%-- 本页是该租户的“设置”页：一级标题右侧的“设置”入口进来的租户主信息与租户级操作 --%>
        <div class="help-source" id="detailHelpText">本页是单个<%= oaonesPBShort %>租户的<b>设置</b>：展示该租户的主信息
            （<%= oaonesPBFieldList %> / 根部门ID / 维护人 / 同步开关 / 更新时间 / 备注）与租户级操作
            （<b>连通测试</b> / <b>编辑租户配置</b> / <b>删除租户</b>）。
            同步中心 / 部门绑定 / 人员绑定 / 快照列表 / 应用绑定是<b>二级标题</b>（页签），点页签进入对应功能页
            （进入功能页后“<%= oaonesPBFull %>租户”<b>锁定为该租户、不能切换租户</b>）；
            <b>统一消息中心</b>不在二级标题里，从一级标题右侧模块菜单的<b>“消息设置”</b>进入
            （<%= oaonesPBShort %>工作台应用 → OA 页面、免登录在二级标题的<b>“应用绑定”</b>页维护）；
            功能页里不再展示本页的租户主信息；
            点左上角标题可回到“<%= oaonesPBShort %>租户”租户列表。
            租户的新增与删除也可以在首页（<%= oaonesPBShort %>租户）完成；本页只针对这一个租户。</div>
        <div id="tenantFields"></div>
    </div>
</div>
<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

/** 当前租户ID（首页租户块带入） */
var cfgId = "<%=oaonesCfgId%>";
var cfgRow = null;

function maintainerCellHtml(r) {
    var list = (r && r.maintainers) || [];
    var rows = [];
    list.forEach(function (m) {
        if (!m || m.id === null || m.id === undefined) { return; }
        rows.push({ id: String(m.id), name: m.name ? String(m.name) : "" });
    });
    if (!rows.length) { return "<span class='muted'>未设置（仅系统管理员可查看与维护）</span>"; }
    var html = "<span class='mt-tag-list'>";
    rows.forEach(function (m) {
        html += "<span class='mt-chip-static' title='OA 人员ID " + esc(m.id) + "'>"
            + OaonesHrm.user(m.id, m.name ? m.name : ("ID " + m.id)) + "</span>";
    });
    html += "</span>";
    if (r && r.can_maintain) { html += " <span class='tag manual'>我维护</span>"; }
    return html;
}

function fieldRow(label, valueHtml) {
    return "<div class='cfg-item-row'><span>" + esc(label) + "</span><b>" + valueHtml + "</b></div>";
}

/** 二级标题（租户功能菜单页签）由 navbar.jsp 统一输出；租户不可见时连同它一起隐藏。 */
function setTenantMenusVisible(visible) {
    var el = document.querySelector(".tenant-menus");
    if (el) { el.style.display = visible ? "" : "none"; }
}

function showPerm(msg) {
    byId("permTip").textContent = msg;
    byId("permCard").style.display = "";
    byId("tenantCard").style.display = "none";
    setTenantMenusVisible(false);
}

function render(c) {
    cfgRow = c;
    byId("tenantName").textContent = c.corp_name || (P_SHORT + "租户 #" + c.id);
    byId("tenantState").innerHTML = c.sync_enable === "1"
        ? "<span class='tag bound'>启用</span>" : "<span class='tag off'>停用</span>";
    byId("tenantFields").innerHTML = ""
        + fieldRow("租户ID", esc(c.id))
        + fieldRow(P_IdLabel, esc(c.corp_id))
        + (HAS_AGENT ? fieldRow(P_AppIdLabel, esc(c.agent_id || "-")) : "")
        + fieldRow("根部门ID", esc(c.root_dept_id || P_RootDeptDefault))
        + fieldRow("维护人", maintainerCellHtml(c))
        + fieldRow("同步开关", c.sync_enable === "1" ? "启用" : "停用")
        + fieldRow("更新时间", esc(Oaones.fmtTime(c.update_time)))
        + fieldRow("备注", esc(c.remark || "-"));
    byId("permCard").style.display = "none";
    byId("tenantCard").style.display = "";
    setTenantMenusVisible(true);
}

function loadTenant() {
    Oaones.api("cfg.list", {}, function (res) {
        if (res.code !== 0) { showPerm(res.msg || "租户信息加载失败"); return; }
        var rows = res.data || [];
        if (!cfgId) {
            showPerm("未指定" + P_SHORT + "租户，请从首页“" + P_SHORT + "租户”里点开某个租户。");
            return;
        }
        var found = null;
        rows.forEach(function (r) { if (String(r.id) === String(cfgId)) { found = r; } });
        if (!found) {
            showPerm(OaonesTips.pick(
                "租户不存在（可能已被删除），请返回首页重新选择。",
                "租户不存在或你不是该租户的维护人，请返回首页重新选择。"));
            return;
        }
        render(found);
    });
}

/** 连通测试：只验证“能通即可”，结果只报连通与否（不展示部门数/成员数/token）。
 *  测试期间用加载框挡住页面（避免重复点击），与后端“只验连通”的口径一致。 */
var testing = false;
byId("testBtn").addEventListener("click", function () {
    if (testing) { return; }
    if (!cfgRow) {
        showToast("租户配置还没加载完，请稍后再试。", 1);
        return;
    }
    testing = true;
    OaonesToast.loading("<%= oaonesPBTestLoadingTip %>");
    Oaones.api("cfg.test", { id: cfgRow.id }, function (res) {
        testing = false;
        OaonesToast.hideLoading();
        if (res.code !== 0) { showToast("测试失败: " + res.msg, 2); return; }
        showToast("连通成功：" + P_SHORT + "租户“" + (res.data.corp_name || ("#" + cfgRow.id)) + "”可以连通", 0, 5000);
    });
});
byId("editBtn").addEventListener("click", function () {
    if (!cfgRow) { return; }
    location.href = "/oaones/appsyn/index/TenantIndex.jsp?platform=" + P_PLATFORM
        + "&edit=" + encodeURIComponent(cfgRow.id);
});
byId("delBtn").addEventListener("click", function () {
    if (!cfgRow) { return; }
    var name = cfgRow.corp_name || ("#" + cfgRow.id);
    OaonesToast.confirm("确认删除" + P_SHORT + "租户“" + name + "”？存在绑定关系时不允许删除。", function () {
        Oaones.api("cfg.delete", { id: cfgRow.id }, function (res) {
            if (res.code === 0) {
                showToast("已删除，返回租户列表");
                location.href = "/oaones/appsyn/index/TenantIndex.jsp?platform=" + P_PLATFORM;
            } else {
                showToast(res.msg, 2);
            }
        });
    }, { danger: true });
});
loadTenant();
</script>
</body>
</html>
