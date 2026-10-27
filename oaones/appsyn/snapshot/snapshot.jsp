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
    // 从“企业微信租户”列表点开某个租户（租户二级菜单的“快照列表”页签）进入时带 cfg_id：
    // 作为当前租户的初值（仍会校验是否在本人可见租户里，取不到则回退第一个）
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
    <title>快照列表（<%= oaonesPBShort %>） - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
        <script>window.OAONES_API_URL = "<%= oaonesPBApiUrl %>";
    window.OAONES_CFG_ALL_LABEL = "全部<%= oaonesPBFull %>租户";
    window.OAONES_CFG_TYPE = <%= oaonesPBType %>;
    var P_FULL = "<%= oaonesPBFull %>", P_SHORT = "<%= oaonesPBShort %>";
    var P_PLATFORM = "<%= oaonesPBPlatform %>";</script>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };
    window.OAONES_CFG_ID = "<%=oaonesCfgId%>";</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/excel.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 同一套两级标题：一级=模块标题 + 当前租户名/设置，二级=该租户的功能菜单（页签）。
     本页是租户二级菜单“快照列表”：只读展示该租户的平台组织架构与人员快照，
     右上角“更新快照”按当前租户重新从平台拉取部门与人员并写回快照表 --%>
<jsp:include page="<%= oaonesPBNavbarPath %>">
    <jsp:param name="active" value="snapshot"/>
</jsp:include>
<div class="page">
    <div class="card">
        <div class="toolbar">
            <label><%= oaonesPBFull %>租户</label>
            <select id="cfgFilter" style="min-width:200px;"></select>
            <%-- 带 cfg_id 从某个平台租户点进来时租户锁定：这里只读展示当前租户，不提供切换（见 skill.md 第一节第 15 条） --%>
            <span class="cfg-locked" id="cfgLocked" style="display:none;"
                  title="当前租户由“<%= oaonesPBFull %>租户”列表点开决定，本页不能切换租户"></span>
            <span class="help-tip" id="cfgHelpTip" data-help="cfgHelpText" role="button" tabindex="0"
                  title="查看页面说明" aria-label="查看页面说明">?</span>
            <span style="flex:1;"></span>
            <button class="btn secondary" id="exportExcelBtn"
                    title="把当前快照导出为 Excel 文件（.xlsx）：树状导出“部门 → 成员”明细，列表导出成员明细；导出范围与页面当前筛选条件一致">导出Excel</button>
            <button class="btn" id="snapshotBtn"
                    title="按当前<%= oaonesPBFull %>租户重新从<%= oaonesPBFull %>拉取全部部门与全部成员，写回快照表 uf_oaones_appsyn_dep_tmp / uf_oaones_appsyn_user_tmp（数据较多时耗时较长，期间请不要关闭页面）">更新快照</button>
            <button class="btn secondary" id="refreshBtn">刷新</button>
        </div>
        <%-- 页面说明按登录人角色分两套：系统管理员（全部租户）与维护人/普通用户（只看本人维护的租户） --%>
        <div class="help-source" id="cfgHelpText">本页是当前<%= oaonesPBFull %>租户的<b>快照列表</b>：展示的是<b><%= oaonesPBShort %>快照表</b>
            （<span class="mono">uf_oaones_appsyn_dep_tmp</span> / <span class="mono">uf_oaones_appsyn_user_tmp</span>）里状态正常的
            <%= oaonesPBShort %>部门与<%= oaonesPBShort %>成员，<span class="rule">只查看、不修改</span>（免绑定、绑定、同步都在“部门绑定 / 人员绑定 / 同步中心”页维护）。
            左列按<%= oaonesPBShort %>组织架构逐级展示部门：点部门行左侧箭头（或部门行）可收起 / 展开，部门名右侧的徽标是该部门
            <span class="rule">含下级部门在内</span>的成员数；每个部门下面<span class="rule">只挂本部门的直属成员</span>
            （不含下级部门成员，下级部门单独成行），成员行显示“姓名（成员ID）”。右列是部门 ID / 免绑定标记，
            以及成员的职位、手机号、邮箱、座机与<span class="rule">该成员快照的最近同步时间</span>。
            “快照筛选”支持按<%= oaonesPBShort %>部门名称 / 部门ID、成员姓名 / 成员ID / 职位 / 手机号 / 邮箱过滤：
            <span class="rule">部门命中时保留该部门的全部直属成员</span>，成员命中时保留其上级部门路径，
            点“重置筛选”清空条件。右侧“树状 / 列表”切换展示方式：树状按组织架构看，列表按成员逐行看
            （带分页与列宽拖拽，列宽可拖动表头右边缘调整）；“展开全部 / 收起全部”只改变树的收起状态，
            不影响筛选与导出（部门与成员合计超过 300 条时默认收起，先看整体）。右上角“导出 Excel”
            按当前展示方式导出（.xlsx）：树状导出“部门 → 成员”明细，列表导出成员明细，导出范围与当前筛选条件一致。
            <span class="rule">快照不是<%= oaonesPBShort %>实时数据</span>：需要更新时点右上角<b>“更新快照”</b>，
            会按当前租户依次从<%= oaonesPBFull %>拉取<b>全部部门</b>与<b>全部成员</b>并写回快照表
            （数据较多、耗时较长，期间请不要关闭页面），完成后本页自动重新加载；
            定时更新由计划任务 <span class="mono">OaonesDeptTmpCronJob</span> /
            <span class="mono">OaonesUserTmpCronJob</span> 负责，“部门绑定 / 人员绑定”页也各自带
            “更新<%= oaonesPBShort %>部门快照 / 更新<%= oaonesPBShort %>人员快照”按钮（与“更新快照”写同一份快照）。
            已删除 / 已封存的部门与成员不在本页列出；快照为空时请先点“更新快照”。
            <% if (OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))) { %>你是<b>系统管理员</b>（user.id=1）或超管角色成员，可查看与管理全部<%= oaonesPBShort %>租户：
            未指定维护人的租户也只对系统管理员开放；从“<%= oaonesPBFull %>租户”列表点开某个租户后进入本页时，
            当前租户<span class="rule">锁定为该租户</span>，工具栏的“<%= oaonesPBFull %>租户”只读展示、不能切换租户
            （要换租户请回“<%= oaonesPBFull %>租户”列表重新点开另一个租户）。
        <% } else { %>本页只列出你被指定为维护人的<%= oaonesPBFull %>租户；从“<%= oaonesPBFull %>租户”列表点开某个租户后进入本页时，
            当前租户<span class="rule">锁定为该租户</span>，工具栏的“<%= oaonesPBFull %>租户”只读展示、不能切换租户
            （要换租户请回“<%= oaonesPBFull %>租户”列表重新点开另一个租户）；
            未指定维护人的租户不对外可见，也不会出现在你的列表中。
        <% } %></div>
        <p class="notice" id="cfgPermTip" style="display:none;"></p>

<%-- 快照列表主体（组织架构 + 成员快照，只读） --%>
<jsp:include page="snapshotPanel.jsp"/>

    </div>
</div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
var state = { cfgs: [], cfgId: String(window.OAONES_CFG_ID || ""), keyword: "", view: "tree" };
/** 企微部门快照（dept.wxlist：id / name / parentid / status / no_bind） */
var snapDepts = [];
/** 企微成员快照（user.wxlist：wx_user_id / wx_name / wx_dept_id / wx_dept_name / position / mobile / email …） */
var snapUsers = [];
/** 组织架构树：{ nodes, order, roots, orphan }，由 buildSnapTree() 生成 */
var snapTree = null;
/** 树收起状态：节点ID → true 表示收起（默认展开）；只影响树状展示 */
var snapCollapsed = {};
/** 树状视图当前铺平的行（部门行 / 成员行），供渲染与导出使用 */
var snapRows = [];
/** 当前筛选下可见的成员（整棵树，与收起/展开无关），供列表视图与导出使用 */
var snapVisibleUsers = [];
/** 首次取数后按数据量决定默认收起 / 展开的阈值（部门 + 成员条数） */
var SNAP_AUTO_EXPAND_MAX = 300;
/** 列表视图的分页器（树状视图不套分页） */
var snapPager = null;
/** 取数序号：租户切换 / 多次刷新时丢弃过期回调 */
var loadSeq = 0;
/** 快照更新中标记：避免重复点击 */
var snapshotBusy = false;

function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
function snapKey(v) { return String(v === null || v === undefined ? "" : v).trim(); }
/** 快照里的“免绑定”标记：部门与成员快照表都是写 0（正常态为空） */
function snapIsNoBind(v) { return snapKey(v) === "0"; }

/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/appsyn/js/toast.js 弹出。
 */
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

/* ==================== 一、当前租户（与其它租户功能页同一口径） ==================== */

/**
 * 当前租户：租户上下文（地址带 cfg_id）时锁定为该租户；不带 cfg_id 时用页内下拉选的租户，
 * 未选时回退第一个可见租户（无可见租户时为空）。
 */
function resolveCfgId(cfgs, wanted) {
    if (Oaones.cfgLocked()) { return Oaones.lockedCfgId(); }
    var id = snapKey(wanted);
    if (id) {
        for (var i = 0; i < cfgs.length; i++) {
            if (String(cfgs[i].id) === id) { return id; }
        }
    }
    return cfgs.length ? cfgs[0].id : "";
}

function loadCfgs() {
    Oaones.api("cfg.list", {}, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        state.cfgs = res.data || [];
        state.cfgId = resolveCfgId(state.cfgs, state.cfgId);
        renderCfgSelect();
        applyCfgPermTip(state.cfgs);
        loadData();
    });
}

/** 本页没有任何可查看租户时，按登录人角色提示原因（超管=还没建租户；其他=不是任何租户的维护人）。 */
function applyCfgPermTip(cfgs) {
    var tip = byId("cfgPermTip");
    // 租户上下文（锁定租户）但该租户不在本人可见范围内：只给提示，本页不能切换租户
    if (Oaones.cfgLocked() && !lockedCfgLabel()) {
        tip.textContent = "当前租户（#" + Oaones.lockedCfgId() + "）不在你可以查看的" + P_FULL + "租户范围内，本页不能切换租户；"
            + "请联系管理员把你加入该租户的维护人，或回“" + P_FULL + "租户”列表重新点开另一个租户。";
        tip.style.display = "";
        return;
    }
    if ((cfgs || []).length) {
        tip.style.display = "none";
        tip.textContent = "";
        return;
    }
    tip.textContent = OaonesTips.pick(
        "当前还没有任何" + P_FULL + "租户，请先到“" + P_SHORT + "租户配置”页新建租户，再回到本页查看快照。",
        "当前登录人不是任何" + P_FULL + "租户的维护人，暂无可查看的租户；"
            + "请联系管理员在“" + P_SHORT + "租户配置”页把本人加入该租户的维护人。");
    tip.style.display = "";
}

function renderCfgSelect() {
    var locked = Oaones.cfgLocked();
    // 租户上下文：隐藏下拉，改为只读展示（不能切换租户）
    byId("cfgFilter").style.display = locked ? "none" : "";
    byId("cfgLocked").style.display = locked ? "" : "none";
    if (locked) {
        byId("cfgLocked").innerHTML = "<b>" + esc(Oaones.lockedCfgText(lockedCfgLabel())) + "</b>";
    }
    var html = '<option value="">请选择" + P_FULL + "租户</option>';
    state.cfgs.forEach(function (c) {
        html += '<option value="' + esc(c.id) + '">' + esc(c.corp_name)
            + (c.sync_enable === "1" ? "" : "（停用）") + "</option>";
    });
    byId("cfgFilter").innerHTML = html;
    byId("cfgFilter").value = state.cfgId;
}

/** 当前锁定租户的名称（取不到时为空，展示时退化为 #租户ID） */
function lockedCfgLabel() {
    for (var i = 0; i < state.cfgs.length; i++) {
        if (String(state.cfgs[i].id) === String(state.cfgId)) { return state.cfgs[i].corp_name || ""; }
    }
    return "";
}

/* ==================== 二、取数：部门快照 + 成员快照 ==================== */

function loadData() {
    if (!state.cfgId) {
        snapDepts = [];
        snapUsers = [];
        buildSnapTree();
        render();
        return;
    }
    loadSeq++;
    var seq = loadSeq;
    var deptDone = false;
    var userDone = false;
    function finish() {
        if (!deptDone || !userDone || seq !== loadSeq) { return; }
        buildSnapTree();
        applyDefaultCollapse();
        render();
    }
    // 与“部门绑定 / 人员绑定”页取的是同一份快照（dept.wxlist / user.wxlist，只返回状态正常的数据）；
    // 两个回调都先比 loadSeq：租户切换 / 连续刷新时，过期回调直接丢弃，避免旧租户数据覆盖新租户
    Oaones.api("dept.wxlist", { cfg_id: state.cfgId }, function (res) {
        if (seq !== loadSeq) { return; }
        if (res.code !== 0) { showToast(res.msg, 2); snapDepts = []; }
        else { snapDepts = res.data || []; }
        deptDone = true;
        finish();
    });
    Oaones.api("user.wxlist", { cfg_id: state.cfgId }, function (res) {
        if (seq !== loadSeq) { return; }
        if (res.code !== 0) { showToast(res.msg, 2); snapUsers = []; }
        else { snapUsers = res.data || []; }
        userDone = true;
        finish();
    });
}

/** 快照更新：先刷新部门快照（dept.tmpRefresh），成功后再刷新人员快照（user.tmpRefresh），最后重新取数。 */
function updateSnapshot() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    if (snapshotBusy) { return; }
    snapshotBusy = true;
    var btn = byId("snapshotBtn");
    btn.disabled = true;
    btn.textContent = "快照更新中…";
    OaonesToast.loading("快照更新中，数据较多，请不要关闭页面");
    Oaones.api("dept.tmpRefresh", { cfg_id: state.cfgId }, function (res) {
        if (res.code !== 0) {
            finishSnapshot("部门快照更新失败: " + res.msg);
            return;
        }
        var deptCount = (res.data && res.data.department_count) || 0;
        Oaones.api("user.tmpRefresh", { cfg_id: state.cfgId }, function (res2) {
            if (res2.code !== 0) {
                finishSnapshot("人员快照更新失败: " + res2.msg);
                return;
            }
            var userCount = (res2.data && res2.data.user_count) || 0;
            finishSnapshot("", "快照更新成功：" + P_SHORT + "部门 " + deptCount + " 个、" + P_SHORT + "成员 " + userCount + " 人");
        });
    });
}

/** 快照更新的收尾：恢复按钮、关掉加载框；msg 非空时用模式 2 提示失败。 */
function finishSnapshot(failMsg, okMsg) {
    snapshotBusy = false;
    OaonesToast.hideLoading();
    var btn = byId("snapshotBtn");
    btn.disabled = false;
    btn.textContent = "更新快照";
    if (failMsg) {
        showToast(failMsg + "（可稍后重试，或到“部门绑定 / 人员绑定”页分别更新快照）", 2);
        // 部门快照可能已经写成功，重新取一次数，页面状态与实际快照保持一致
        loadData();
        return;
    }
    showToast(okMsg || "快照更新成功", 0, 5000);
    loadData();
}

/* ==================== 三、组织架构树（部门 → 本部门直属成员） ==================== */

function snapDeptText(name, id) { return snapKey(name) + " " + snapKey(id); }

function snapUserText(u) {
    return [u.wx_name, u.wx_user_id, u.wx_dept_name, u.position, u.mobile, u.email, u.biz_mail]
        .map(function (v) { return String(v === null || v === undefined ? "" : v); }).join(" ");
}

function snapHit(text, keyword) {
    return String(text === null || text === undefined ? "" : text).toLowerCase().indexOf(keyword) >= 0;
}

/**
 * 建树：企微部门按 parentid 组层级，成员挂到自己的主部门（wx_dept_id）下。
 * 主部门不在部门快照里的成员挂到末尾的虚拟节点“未归属部门…”，避免这些人看不到。
 * 同时算出各节点在当前筛选下的可见性、可见成员数与含下级引用，渲染与提示语都从这里取。
 */
function buildSnapTree() {
    var nodes = {};
    var order = [];
    function ensureDept(d) {
        var id = snapKey(d.id);
        if (nodes[id]) { return nodes[id]; }
        var node = {
            id: id, name: snapKey(d.name) || (P_SHORT + "部门 " + id), parentId: snapKey(d.parentid),
            noBind: snapIsNoBind(d.no_bind), virtual: false,
            users: [], children: [], visibleUsers: []
        };
        nodes[id] = node;
        order.push(node);
        return node;
    }
    (snapDepts || []).forEach(function (d) { ensureDept(d); });
    // 虚拟节点：主部门不在部门快照里的成员（部门快照未刷新时也会出现）
    var orphan = {
        id: "", name: "未归属部门（主部门不在部门快照中）", parentId: "", noBind: false, virtual: true,
        users: [], children: [], visibleUsers: []
    };
    // 挂父子：父部门不在快照里、父部门是自己时按顶层处理；
    // 企微侧数据异常出现环形父子（A 的父是 B、B 的父是 A）时同样按顶层处理，
    // 否则渲染 / 统计递归会绕不出去（父子关系一旦成环，页面就直接栈溢出）。
    var parentOf = {};
    order.forEach(function (node) {
        var parent = node.parentId ? nodes[node.parentId] : null;
        if (!parent || parent === node) { return; }
        var cur = parent;
        var guard = 0;
        while (cur && guard++ <= order.length) {
            if (cur === node) { return; }
            cur = parentOf[cur.id] ? nodes[parentOf[cur.id]] : null;
        }
        parent.children.push(node);
        parentOf[node.id] = parent.id;
    });
    (snapUsers || []).forEach(function (u) {
        var dept = nodes[snapKey(u.wx_dept_id)];
        if (dept) { dept.users.push(u); } else { orphan.users.push(u); }
    });
    if (orphan.users.length) { order.push(orphan); nodes.__orphan = orphan; }

    // 顶层部门 = 没有挂上父部门的节点（父部门缺失 / 自己父自己 / 环形父子被拆掉的那一侧）+ 未归属成员的虚拟节点
    var roots = order.filter(function (node) { return node.virtual || !parentOf[node.id]; });
    snapTree = { nodes: nodes, order: order, roots: roots, orphan: orphan };
}

/**
 * 按当前关键字标记可见性（对整棵树一次性算好）：
 *   部门命中 → 该部门本身 + 它的全部直属成员都保留；
 *   成员命中 → 保留该成员及其上级部门路径；
 *   下级命中 → 上级部门作为路径保留（否则看不出层级）。
 */
function applySnapFilter() {
    var keyword = snapKey(state.keyword).toLowerCase();
    snapVisibleUsers = [];
    function mark(node) {
        var deptHit = keyword.length === 0 || snapHit(snapDeptText(node.name, node.id), keyword);
        node.visibleUsers = [];
        node.users.forEach(function (u) {
            var hit = keyword.length === 0 || snapHit(snapUserText(u), keyword);
            if (hit || (deptHit && node.users.length)) {
                node.visibleUsers.push(u);
                snapVisibleUsers.push(u);
            }
        });
        var childShown = 0;
        node.children.forEach(function (c) { if (mark(c)) { childShown++; } });
        node.visible = deptHit || node.visibleUsers.length > 0 || childShown > 0;
        // 徽标与提示语里的“部门数 / 成员数”都按“可见的子树”统计：被筛掉的分支不计入
        node.shownUsers = node.visibleUsers.length;
        node.shownSubtreeUsers = node.visible ? node.shownUsers : 0;
        node.shownSubtreeDepts = node.visible ? (node.virtual ? 0 : 1) : 0;
        node.children.forEach(function (c) {
            node.shownSubtreeUsers += c.shownSubtreeUsers;
            node.shownSubtreeDepts += c.shownSubtreeDepts;
        });
        return node.visible;
    }
    snapTree.roots.forEach(mark);
}

/** 首次取数后按数据量决定默认状态：条数多时先收起看整体，少时直接展开（与“绑定总览”页同一口径）。 */
function applyDefaultCollapse() {
    snapCollapsed = {};
    if ((snapDepts || []).length + (snapUsers || []).length <= SNAP_AUTO_EXPAND_MAX) { return; }
    snapTree.order.forEach(function (node) { snapCollapsed[node.id || "__orphan"] = true; });
}

/** 树状视图：把可见节点按层级铺平成行（部门行 + 其直属成员行 + 下级部门行），收起的分支不再往下走。 */
function flattenSnapRows() {
    var rows = [];
    function pushNode(node, depth) {
        rows.push({ kind: "dept", node: node, depth: depth });
        if (snapCollapsed[node.id || "__orphan"]) { return; }
        node.visibleUsers.forEach(function (u) { rows.push({ kind: "user", user: u, node: node, depth: depth + 1 }); });
        node.children.forEach(function (c) { if (c.visible) { pushNode(c, depth + 1); } });
    }
    snapTree.roots.forEach(function (root) { if (root.visible) { pushNode(root, 0); } });
    snapRows = rows;
}

/** 部门行左侧单元格：收起箭头 + 部门名（企微侧对象保持纯文本）+ 含下级的成员数徽标。 */
function snapDeptCellHtml(row) {
    var node = row.node;
    var hasKids = node.visibleUsers.length > 0 || node.children.some(function (c) { return c.visible; });
    var open = !snapCollapsed[node.id || "__orphan"];
    var caret = hasKids
        ? "<button type='button' class='tree-caret' data-node='" + esc(node.id || "__orphan") + "' title='"
            + (open ? "收起下级" : "展开下级") + "'>" + (open ? "▾" : "▸") + "</button>"
        : "<span class='tree-caret leaf'>•</span>";
    var badgeTip = "本部门直属成员 " + node.shownUsers + " 人，含下级部门共 " + node.shownSubtreeUsers + " 人";
    var badge = "<span class='map-count" + (node.shownSubtreeUsers ? "" : " zero") + "'"
        + " title='" + esc(badgeTip) + "'>" + node.shownSubtreeUsers + "</span>";
    return "<div class='map-col map-col-oa' style='padding-left:" + (8 + row.depth * 20) + "px;'>"
        + caret
        + "<span class='map-dept-name'>" + esc(node.name) + "</span>"
        + badge
        + "</div>";
}

/** 部门行右侧单元格：企微部门ID 与免绑定标记（虚拟节点给“—”）。 */
function snapDeptInfoHtml(node) {
    if (node.virtual) { return "<span class='map-empty-cell'>—</span>"; }
    var html = "<span class='map-wx-acct'><span class='map-wx-sub'>" + P_SHORT + "部门ID</span>"
        + "<span class='map-user-name'>" + esc(node.id) + "</span></span>";
    if (node.noBind) { html += "<span class='tag nobind'>免绑定</span>"; }
    return html;
}

function snapDeptRowHtml(row) {
    return "<div class='map-row map-row-dept' data-node='" + esc(row.node.id || "__orphan") + "'>"
        + snapDeptCellHtml(row)
        + "<div class='map-col map-col-wx'>" + snapDeptInfoHtml(row.node) + "</div>"
        + "</div>";
}

/** 成员行右侧单元格：职位 / 手机号 / 邮箱 / 座机 + 该成员快照的最近同步时间。 */
function snapUserInfoHtml(u) {
    var parts = [];
    if (snapKey(u.position)) { parts.push("职位 " + snapKey(u.position)); }
    if (snapKey(u.mobile)) { parts.push("手机 " + snapKey(u.mobile)); }
    if (snapKey(u.telephone)) { parts.push("座机 " + snapKey(u.telephone)); }
    if (snapKey(u.email)) { parts.push("邮箱 " + snapKey(u.email)); }
    if (snapKey(u.biz_mail)) { parts.push("企业邮箱 " + snapKey(u.biz_mail)); }
    var html = parts.length
        ? "<span class='map-wx-sub'>" + esc(parts.join(" · ")) + "</span>"
        : "<span class='map-empty-cell'>—</span>";
    if (snapKey(u.last_sync_time)) {
        html += "<span class='map-wx-sub'>快照 " + esc(snapKey(u.last_sync_time)) + "</span>";
    }
    return html;
}

function snapUserRowHtml(row) {
    var u = row.user;
    var pad = 8 + row.depth * 20 + 20;
    var name = snapKey(u.wx_name) || (P_SHORT + "成员 " + snapKey(u.wx_user_id));
    return "<div class='map-row map-row-user'>"
        + "<div class='map-col map-col-oa' style='padding-left:" + pad + "px;'>"
        + "<span class='tree-caret leaf'>•</span>"
        + "<span class='map-user-name'>" + esc(name) + "</span>"
        + "<span class='map-wx-sub'>（" + esc(snapKey(u.wx_user_id)) + "）</span>"
        + (snapIsNoBind(u.no_bind) ? "<span class='tag nobind'>免绑定</span>" : "")
        + "</div>"
        + "<div class='map-col map-col-wx'>" + snapUserInfoHtml(u) + "</div>"
        + "</div>";
}

function renderTree() {
    flattenSnapRows();
    if (!snapRows.length) { byId("snapTree").innerHTML = ""; return; }
    var html = "";
    for (var i = 0; i < snapRows.length; i++) {
        html += snapRows[i].kind === "dept" ? snapDeptRowHtml(snapRows[i]) : snapUserRowHtml(snapRows[i]);
    }
    byId("snapTree").innerHTML = html;
}

/* ==================== 四、列表视图（成员逐行 + 分页 + 列宽拖拽） ==================== */

/** 成员主部门：快照里的部门名，取不到时按部门ID回查部门快照，再退回“企微部门 + ID”。 */
function snapUserDeptName(u) {
    var name = snapKey(u.wx_dept_name);
    if (name) { return name; }
    var deptId = snapKey(u.wx_dept_id);
    if (!deptId) { return "-"; }
    if (snapTree && snapTree.nodes[deptId]) { return snapTree.nodes[deptId].name; }
    return P_SHORT + "部门 " + deptId;
}

function snapUserStateHtml(u) {
    return snapIsNoBind(u.no_bind) ? "<span class='tag nobind'>免绑定</span>" : "正常";
}

function renderList() {
    var rows = snapVisibleUsers.slice(0);
    var pageRows = rows;
    if (snapPager) {
        snapPager.resetIfChanged(state.cfgId + "|" + state.keyword);
        pageRows = snapPager.apply(rows);
    }
    var body = "";
    pageRows.forEach(function (u) {
        body += "<tr>"
            + "<td><b>" + esc(snapKey(u.wx_name) || (P_SHORT + "成员 " + snapKey(u.wx_user_id))) + "</b></td>"
            + "<td>" + esc(snapKey(u.wx_user_id)) + "</td>"
            + "<td>" + esc(snapUserDeptName(u)) + "</td>"
            + "<td>" + esc(snapKey(u.position) || "-") + "</td>"
            + "<td>" + esc(snapKey(u.mobile) || "-") + "</td>"
            + "<td>" + esc(snapKey(u.email) || snapKey(u.biz_mail) || "-") + "</td>"
            + "<td>" + snapUserStateHtml(u) + "</td>"
            + "<td>" + esc(snapKey(u.last_sync_time) || "-") + "</td>"
            + "</tr>";
    });
    byId("snapBody").innerHTML = body;
    if (window.OaonesTable) {
        OaonesTable.resizable(byId("snapTable"), { key: "wecomSnapshot.snapTable" });
    }
    // 分页条只在列表方式下出现（树状方式展示全部命中行，不套分页）
    if (snapPager) {
        if (state.view === "list") { snapPager.show(); } else { snapPager.hide(); }
        snapPager.render();
    }
    byId("snapListEmpty").style.display = rows.length ? "none" : "block";
    if (!rows.length) {
        byId("snapListEmpty").textContent = snapEmptyText();
    }
}

/* ==================== 五、渲染入口：统计、提示、树状 / 列表 ==================== */

function renderStats() {
    var noBindDepts = 0;
    snapDepts.forEach(function (d) { if (snapIsNoBind(d.no_bind)) { noBindDepts++; } });
    var noBindUsers = 0;
    var lastSync = "";
    snapUsers.forEach(function (u) {
        if (snapIsNoBind(u.no_bind)) { noBindUsers++; }
        var t = snapKey(u.last_sync_time);
        if (t && t > lastSync) { lastSync = t; }
    });
    var el = byId("snapStats");
    el.style.display = "";
    el.innerHTML = ""
        + "<span class='bind-stat'><span>" + P_SHORT + "部门</span><b>" + snapDepts.length + "</b> 个</span>"
        + "<span class='bind-stat'><span>" + P_SHORT + "成员</span><b>" + snapUsers.length + "</b> 人</span>"
        + "<span class='bind-stat'><span>免绑定部门</span><b>" + noBindDepts + "</b> 个</span>"
        + "<span class='bind-stat'><span>免绑定成员</span><b>" + noBindUsers + "</b> 人</span>"
        + "<span class='bind-stat'><span>当前显示</span><b>" + snapVisibleUsers.length + "</b> 人</span>"
        + "<span class='bind-stat'><span>快照时间</span><b>" + esc(lastSync || "-") + "</b></span>";
}

function snapEmptyText() {
    if (!state.cfgId) { return "请先选择" + P_FULL + "租户"; }
    if (snapKey(state.keyword)) { return "当前筛选条件下没有匹配的" + P_SHORT + "部门 / 成员，点“重置筛选”可查看全部快照"; }
    return "当前租户还没有" + P_SHORT + "快照数据，请点击右上角“更新快照”从" + P_SHORT + "同步（部门 + 人员）";
}

function renderTip() {
    var deptCount = 0;
    var userCount = snapVisibleUsers.length;
    snapTree.roots.forEach(function (r) { if (r.visible) { deptCount += r.shownSubtreeDepts; } });
    var text = "当前筛选下共 " + deptCount + " 个" + P_SHORT + "部门、" + userCount + " 名" + P_SHORT + "成员";
    if (snapKey(state.keyword)) { text += "（已按关键字“" + state.keyword + "”过滤）"; }
    text += "；快照数据取自 uf_oaones_appsyn_dep_tmp / uf_oaones_appsyn_user_tmp（只含状态正常的部门与成员），本页只查看、不修改。";
    if ((snapDepts.length + snapUsers.length) > SNAP_AUTO_EXPAND_MAX) {
        text += "部门与成员较多（超过 " + SNAP_AUTO_EXPAND_MAX + " 条），树状默认收起，可点“展开全部”或逐级展开。";
    }
    byId("snapTip").textContent = text;
}

/** 展示方式：tree = 树状（默认，组织架构 + 成员）/ list = 列表（成员逐行 + 分页）。 */
function setSnapView(mode, silent) {
    state.view = mode === "list" ? "list" : "tree";
    byId("snapTreeView").style.display = state.view === "tree" ? "" : "none";
    byId("snapListView").style.display = state.view === "list" ? "" : "none";
    byId("snapViewTreeBtn").classList.toggle("active", state.view === "tree");
    byId("snapViewListBtn").classList.toggle("active", state.view === "list");
    if (!silent) {
        try { localStorage.setItem("oaones.wecomSnapshot.view", state.view); } catch (e) { /* 隐私模式忽略 */ }
    }
    if (state.view === "tree" && snapPager) { snapPager.hide(); }
}

function render() {
    // 首次取数前（或取数中）用户就可能点搜索：树模型为空时先建一棵空树，保证渲染不报错
    if (!snapTree) { buildSnapTree(); }
    applySnapFilter();
    renderStats();
    renderTip();
    renderTree();
    renderList();
    var treeEmpty = !snapRows.length;
    byId("snapTreeEmpty").style.display = treeEmpty ? "block" : "none";
    if (treeEmpty) { byId("snapTreeEmpty").textContent = snapEmptyText(); }
}

/* ==================== 六、导出 Excel（与当前筛选条件一致） ==================== */

/** 树状导出：按层级顺序铺开全部可见部门与成员（不受收起/展开影响）。 */
function exportTreeRows() {
    var rows = [];
    function walk(node, depth, path) {
        var selfPath = path ? (path + " / " + node.name) : node.name;
        rows.push([
            depth + 1, node.virtual ? "未归属人员" : "部门", node.name,
            node.virtual ? "" : node.id, selfPath, "", "", "", "",
            node.virtual ? "" : (snapIsNoBind(node.no_bind) ? "免绑定" : "正常")
        ]);
        node.visibleUsers.forEach(function (u) {
            rows.push([
                depth + 2, "成员", snapKey(u.wx_name), snapKey(u.wx_user_id), selfPath,
                snapKey(u.position), snapKey(u.mobile), snapKey(u.email) || snapKey(u.biz_mail),
                snapKey(u.last_sync_time), snapIsNoBind(u.no_bind) ? "免绑定" : "正常"
            ]);
        });
        node.children.forEach(function (c) { if (c.visible) { walk(c, depth + 1, selfPath); } });
    }
    snapTree.roots.forEach(function (r) { if (r.visible) { walk(r, 0, ""); } });
    return rows;
}

function exportSnapExcel() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    if (!snapDepts.length && !snapUsers.length) {
        showToast("当前快照没有可导出的数据，请先点击右上角“更新快照”从" + P_SHORT + "同步", 1);
        return;
    }
    var label = lockedCfgLabel() || ("#" + state.cfgId);
    var rows;
    var columns;
    if (state.view === "list") {
        columns = [
            { title: P_SHORT + "成员", width: 18 }, { title: "成员ID", width: 16 }, { title: "主部门", width: 20 },
            { title: "职位", width: 16 }, { title: "手机号", width: 16 }, { title: "邮箱", width: 24 },
            { title: "状态", width: 10 }, { title: "快照时间", width: 20 }
        ];
        rows = snapVisibleUsers.map(function (u) {
            return [
                snapKey(u.wx_name), snapKey(u.wx_user_id), snapUserDeptName(u), snapKey(u.position),
                snapKey(u.mobile), snapKey(u.email) || snapKey(u.biz_mail),
                snapIsNoBind(u.no_bind) ? "免绑定" : "正常", snapKey(u.last_sync_time)
            ];
        });
    } else {
        columns = [
            { title: "层级", width: 8 }, { title: "类型", width: 10 }, { title: "名称", width: 20 },
            { title: P_SHORT + "ID", width: 16 }, { title: "层级路径", width: 34 }, { title: "职位", width: 16 },
            { title: "手机号", width: 16 }, { title: "邮箱", width: 24 }, { title: "快照时间", width: 20 },
            { title: "状态", width: 10 }
        ];
        rows = exportTreeRows();
    }
    if (!rows.length) { showToast("当前筛选条件下没有可导出的数据", 1); return; }
    OaonesExcel.export({
        filename: P_SHORT + "快照_" + label + "_" + OaonesExcel.stamp() + ".xlsx",
        sheet: P_SHORT + "快照",
        columns: columns,
        rows: rows
    });
}

/* ==================== 七、树状视图：展开 / 收起 ==================== */

function toggleSnapNode(key) {
    if (!key) { return; }
    if (snapCollapsed[key]) { delete snapCollapsed[key]; } else { snapCollapsed[key] = true; }
    renderTree();
}

byId("snapTree").addEventListener("click", function (e) {
    var caret = e.target.closest ? e.target.closest(".tree-caret") : null;
    // 占位箭头（.tree-caret.leaf，叶子部门/成员行）不是展开箭头，落到下面的“点行展开”逻辑
    if (caret && !caret.classList.contains("leaf")) {
        toggleSnapNode(caret.getAttribute("data-node"));
        return;
    }
    // 点部门行（部门名、徽标、行内空白）同样收起/展开，与“树状映射”页口径一致
    var row = e.target.closest ? e.target.closest(".map-row-dept") : null;
    if (!row) { return; }
    var rowCaret = row.querySelector ? row.querySelector(".tree-caret") : null;
    if (rowCaret && !rowCaret.classList.contains("leaf")) {
        toggleSnapNode(row.getAttribute("data-node"));
    }
});

function snapSetAllCollapsed(collapsed) {
    snapCollapsed = {};
    if (collapsed) {
        snapTree.order.forEach(function (node) { snapCollapsed[node.id || "__orphan"] = true; });
    }
    renderTree();
}

/* ==================== 八、事件注册与初始化 ==================== */

byId("cfgFilter").addEventListener("change", function () {
    if (Oaones.cfgLocked()) { this.value = state.cfgId; return; }
    state.cfgId = this.value || "";
    state.keyword = "";
    byId("snapKeyword").value = "";
    snapCollapsed = {};
    loadData();
});
byId("snapKeyword").addEventListener("keydown", function (e) {
    if (e.key === "Enter") { state.keyword = byId("snapKeyword").value || ""; render(); }
});
byId("snapSearchBtn").addEventListener("click", function () {
    state.keyword = byId("snapKeyword").value || "";
    render();
});
byId("snapResetBtn").addEventListener("click", function () {
    state.keyword = "";
    byId("snapKeyword").value = "";
    render();
});
byId("snapViewTreeBtn").addEventListener("click", function () {
    setSnapView("tree");
    render();
});
byId("snapViewListBtn").addEventListener("click", function () {
    setSnapView("list");
    render();
});
byId("snapExpandBtn").addEventListener("click", function () {
    setSnapView("tree");
    snapSetAllCollapsed(false);
});
byId("snapCollapseBtn").addEventListener("click", function () {
    setSnapView("tree");
    snapSetAllCollapsed(true);
});
byId("refreshBtn").addEventListener("click", loadData);
byId("snapshotBtn").addEventListener("click", updateSnapshot);
byId("exportExcelBtn").addEventListener("click", exportSnapExcel);

/* 列表视图分页器（树状视图不套分页，切到树状时隐藏） */
if (window.OaonesTable) {
    snapPager = OaonesTable.pager({
        key: "wecomSnapshot.list",
        el: byId("snapPager"),
        onChange: function () { renderList(); }
    });
}
/* 展示方式：默认树状；上次选过列表就用列表（记在浏览器本地） */
var snapSavedView = "tree";
try { snapSavedView = localStorage.getItem("oaones.wecomSnapshot.view") || "tree"; } catch (e) { snapSavedView = "tree"; }
setSnapView(snapSavedView, true);

loadCfgs();
</script>
</body>
</html>
