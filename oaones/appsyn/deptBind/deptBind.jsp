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
    // 从“租户详情”进入时带 cfg_id：作为当前租户的初值（仍会校验是否在本人可见租户里，取不到则回退第一个）
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
    <%-- 平台决定接口入口（AJAX 统一入口）与页面文案：三个平台共用本页，只换 platform 参数 --%>
    <script>window.OAONES_API_URL = "<%= oaonesPBApiUrl %>";
    window.OAONES_CFG_ALL_LABEL = "全部<%= oaonesPBFull %>租户";
    window.OAONES_CFG_TYPE = <%= oaonesPBType %>;
    var P_FULL = "<%= oaonesPBFull %>", P_SHORT = "<%= oaonesPBShort %>";
    var P_PLATFORM = "<%= oaonesPBPlatform %>";
    var P_KeyLabel = "<%= oaonesPBKeyLabel %>", P_AppIdLabel = "<%= oaonesPBAppIdLabel %>";</script>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>部门绑定 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };
    window.OAONES_CFG_ID = "<%=oaonesCfgId%>";</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<jsp:include page="<%= oaonesPBNavbarPath %>">
    <jsp:param name="active" value="deptBind"/>
</jsp:include>
<div class="page">
    <div class="card">
        <div class="toolbar">
            <label><%= oaonesPBFull %>租户</label>
            <select id="cfgFilter" style="min-width:200px;"></select>
            <%-- 带 cfg_id 从某个平台租户点进来时租户锁定：这里只读展示当前租户，不提供切换（见 skill.md 第一节第 15 条） --%>
            <span class="cfg-locked" id="cfgLocked" style="display:none;"
                  title="当前租户由“<%= oaonesPBShort %>租户”列表点开决定，本页不能切换租户"></span>
            <span class="help-tip" id="cfgHelpTip" data-help="cfgHelpText" role="button" tabindex="0"
                  title="查看页面说明" aria-label="查看页面说明">?</span>
            <span style="flex:1;"></span>
            <button class="btn secondary" id="refreshTmpBtn"
                    title="从<%= oaonesPBFull %>拉取全部部门及其主要数据，写入 uf_oaones_appsyn_dep_tmp，供本页绑定选择使用">更新<%= oaonesPBShort %>部门快照</button>
            <button class="btn secondary" id="refreshBtn">刷新</button>
        </div>
        <div class="help-source" id="cfgHelpText"><span class="rule">绑定只允许选择 uf_oaones_appsyn_dep_tmp 中状态正常(type=0)的<%= oaonesPBShort %>部门；
            已删除/已封存或已标记“免绑定”的<%= oaonesPBShort %>部门不能绑定，需先在“<%= oaonesPBShort %>数据基准”页取消免绑定；
            已绑定数据状态异常时会给出提示。
            “OA数据基准”页的“智能匹配”可按部门编号或名称自动为 OA 部门匹配<%= oaonesPBShort %>部门并自动绑定，
            <span class="rule">匹配到的行会带底色标记</span>（保留到重新搜索/重载数据），便于逐行核对；
            <span class="rule">选择即落库</span>：两个二级页都在选择弹框里单击选中、双击该行（或点“确定”）
            立即写入数据库，页面上没有“保存/批量绑定”这类第二步；
            同步时不会自动创建<%= oaonesPBShort %>部门。
            “<%= oaonesPBShort %>数据基准”页按“已绑定/未绑定/免绑定”勾选筛选<%= oaonesPBShort %>部门快照，
            已绑定行按“一行一条绑定关系”列出绑定的全部 OA 部门（多对一）并逐条提供“解绑”；
            该页的勾选仅用于“批量免绑定”（只有“未绑定”部门可勾选）：表头复选框只全选当前页，
            要一次选中当前筛选条件下的全部部门（含未翻到的页），勾选表头上方（与表头复选框对齐）的
            “跨页全选”，再点统计行末尾的“仅显示已选择数据”只看已勾选的行核对后执行“批量免绑定”。
            <b>从“<%= oaonesPBShort %>租户”列表点开某个租户后进入本页时，当前租户锁定为该租户</b>：
            工具栏的“<%= oaonesPBFull %>租户”只读展示、不能切换租户（要换租户请回“<%= oaonesPBShort %>租户”列表重新点开另一个租户）。</span></div>
        <p class="notice" id="cfgPermTip" style="display:none;"></p>
        <div class="sub-tabs">
            <button id="tabManage" class="active">OA数据基准</button>
            <span class="help-tip" id="manageHelpTip" data-help="manageHelpText" role="button" tabindex="0"
                  title="查看 OA 数据基准说明" aria-label="查看 OA 数据基准说明">?</span>
            <button id="tabWxUnbound"><%= oaonesPBShort %>数据基准</button>
            <span class="help-tip" id="wxUnboundHelpTip" data-help="wxUnboundHelpText" role="button" tabindex="0"
                  title="查看<%= oaonesPBShort %>数据基准说明" aria-label="查看<%= oaonesPBShort %>数据基准说明">?</span>
            <button id="tabTree">树状映射</button>
            <span class="help-tip" id="deptTreeHelpTip" data-help="deptTreeHelpText" role="button" tabindex="0"
                  title="查看树状映射说明" aria-label="查看树状映射说明">?</span>
        </div>

<%-- 二级菜单：OA数据基准（部门绑定 - OA 部门侧） --%>
<jsp:include page="deptBindManagePanel.jsp"/>

<%-- 二级菜单：平台数据基准（部门绑定 - 平台部门侧） --%>
<jsp:include page="deptBindUnboundPanel.jsp"/>

<%-- 二级菜单：树状映射（部门绑定 - OA 部门 ⇄ 平台部门 对照树） --%>
<jsp:include page="deptTreePanel.jsp"/>

    </div>
</div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
var state = { cfgs: [], cfgId: String(window.OAONES_CFG_ID || ""), view: "manage", oaListVisible: false, oaFbIds: [], oaDeptIds: [], oaMatchOnlyIds: [], wxDeptIds: [], wxStates: ["bound", "unbound", "nobind"], wxOnlyChecked: false, saving: false };
var oaDepts = [];
var oaSubOptions = [];
var oaDeptOptions = [];
var wxDepts = [];
var binds = [];
var boundByOa = {};
var boundByWx = {};
/** 多对一：同一企微部门 → 全部绑定的 OA 部门（用于页面展示“已绑定的 OA 部门”列表） */
var boundListByWx = {};
var wxPickOaId = "";
var wxPickId = "";
var wxPickValue = "";
var wxPickLabel = "";
var oaPickType = "";
var oaPickSelected = [];
var smartMatchData = [];
var smartSuggestions = {};
var loadSeq = 0;

function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/appsyn/js/toast.js 弹出。
 */
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

function syncWxStateFilterInputs() {
    byId("wxStateBound").checked = state.wxStates.indexOf("bound") >= 0;
    byId("wxStateUnbound").checked = state.wxStates.indexOf("unbound") >= 0;
    byId("wxStateNoBind").checked = state.wxStates.indexOf("nobind") >= 0;
    var labels = byId("wxStateFilters").querySelectorAll(".wx-state-filter");
    for (var i = 0; i < labels.length; i++) {
        var input = labels[i].querySelector("input");
        labels[i].classList.toggle("checked", input && input.checked);
    }
}

/**
 * 当前租户：
 * <ul>
 *   <li>租户上下文（地址带 cfg_id，从“企微租户”列表点开某个租户 / 租户工作区页签进入）：锁定为这个租户，
 *       页面上只读展示、不能切换租户（见 skill.md 第一节第 15 条）；</li>
 *   <li>不带 cfg_id 直接按地址访问：用本页下拉选的租户，未选时回退到第一个可见租户（无可见租户时为空）。</li>
 * </ul>
 */
function resolveCfgId(cfgs, wanted) {
    if (Oaones.cfgLocked()) { return Oaones.lockedCfgId(); }
    var id = String(wanted == null ? "" : wanted);
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

/** 本页没有任何可操作租户时，按登录人角色提示原因（超管=还没建租户；其他=不是任何租户的维护人）。 */
function applyCfgPermTip(cfgs) {
    var tip = byId("cfgPermTip");
    // 租户上下文（锁定租户）但该租户不在本人可见范围内：只给提示，本页不能切换租户
    if (Oaones.cfgLocked() && !lockedCfgLabel()) {
        tip.textContent = "当前租户（#" + Oaones.lockedCfgId() + "）不在你可以维护的" + P_SHORT + "租户范围内，本页不能切换租户；"
            + "请联系管理员把你加入该租户的维护人，或回“" + P_SHORT + "租户”列表重新点开另一个租户。";
        tip.style.display = "";
        return;
    }
    if ((cfgs || []).length) {
        tip.style.display = "none";
        tip.textContent = "";
        return;
    }
    tip.textContent = OaonesTips.pick(
        "当前还没有任何" + P_SHORT + "租户，请先到“" + P_SHORT + "租户配置”页新建租户，再回到本页进行部门绑定。",
        "当前登录人不是任何" + P_SHORT + "租户的维护人，暂无可操作的租户；"
            + "请联系管理员在“" + P_SHORT + "租户配置”页把本人加入该租户的维护人。");
    tip.style.display = "";
}

function renderCfgSelect() {
    var locked = Oaones.cfgLocked();
    // 租户上下文：隐藏下拉，改为只读展示（不能切换租户）
    byId("cfgFilter").style.display = locked ? "none" : "";
    byId("cfgLocked").style.display = locked ? "" : "none";
    if (locked) {
        var name = lockedCfgLabel();
        byId("cfgLocked").innerHTML = "<b>" + esc(Oaones.lockedCfgText(name)) + "</b>";
    }
    var html = '<option value="">请选择" + P_SHORT + "租户</option>';
    state.cfgs.forEach(function (c) {
        html += '<option value="' + esc(c.id) + '">' + esc(c.corp_name) + (c.sync_enable === "1" ? "" : "（停用）") + "</option>";
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

function loadData() {
    smartSuggestions = {};
    // 数据重新加载后不再保留上一次“智能匹配结果”的视图与自动绑定状态（避免误按自动绑定提交）
    state.oaMatchOnlyIds = [];
    resetPendingOaPicks();
    // 企微侧勾选集合随数据重新加载清空（批量免绑定成功后这些行已不再是候选）
    if (typeof wxCheckedIds !== "undefined") { wxCheckedIds = {}; }
    if (!state.cfgId) {
        oaDepts = []; oaSubOptions = []; oaDeptOptions = [];
        wxDepts = []; binds = [];
        boundByOa = {}; boundByWx = {}; boundListByWx = {};
        render();
        return;
    }
    loadSeq++;
    var seq = loadSeq;
    var oaDone = false;
    var wxDone = false;
    function finish() {
        if (oaDone && wxDone && seq === loadSeq) { render(); }
    }
    Oaones.api("dept.list", { cfg_id: state.cfgId, keyword: "" }, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        oaDepts = res.data.oa_departments || [];
        oaSubOptions = res.data.oa_subcompanies || [];
        oaDeptOptions = res.data.oa_dept_options || [];
        binds = res.data.binds || [];
        buildMaps();
        oaDone = true;
        finish();
    });
    Oaones.api("dept.wxlist", { cfg_id: state.cfgId }, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); wxDepts = []; }
        else { wxDepts = res.data || []; }
        wxDone = true;
        finish();
    });
}

function buildMaps() {
    boundByOa = {};
    boundByWx = {};
    boundListByWx = {};
    binds.forEach(function (b) {
        boundByOa[String(b.oa_dept_id)] = b;
        if (b.wx_dept_id !== null && b.wx_dept_id !== undefined && b.wx_dept_id !== "") {
            var wxDeptId = String(b.wx_dept_id);
            boundByWx[wxDeptId] = b;
            // 多对一：同一企微部门可被多个 OA 部门绑定，保留全部绑定记录供页面展示
            if (!boundListByWx[wxDeptId]) { boundListByWx[wxDeptId] = []; }
            boundListByWx[wxDeptId].push(b);
        }
    });
}

/** 部门所属分部ID：OA 部门缓存优先，绑定记录兜底（部门已从缓存里消失时仍能判断） */
function oaDeptSubId(row, bind) {
    var id = row && row.subcompanyid1 !== null && row.subcompanyid1 !== undefined
        ? String(row.subcompanyid1) : "";
    if ((id === "" || id === "0") && bind) {
        id = String(bind.oa_subcompany_id == null ? "" : bind.oa_subcompany_id);
    }
    return id === "0" ? "" : id;
}

function oaRows() {
    var rows = oaDepts.slice();
    // OA 已封存/已删除但仍有绑定的记录也要展示，便于识别“绑定异常”并解绑
    binds.forEach(function (b) {
        var id = String(b.oa_dept_id);
        var exists = rows.some(function (r) { return String(r.id) === id; });
        if (!exists) {
            rows.push({
                id: b.oa_dept_id,
                name: b.oa_dept_name || "OA部门 ID " + b.oa_dept_id,
                canceled: "1",
                parent: b.oa_dept_parent || "",
                subcompanyid1: b.oa_subcompany_id || "",
                subcompany_name: b.oa_subcompany_name || ""
            });
        }
    });
    return rows;
}

function filterByKeyword(rows, keys, keyword) {
    var k = (keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (r) {
        var text = keys.map(function (key) { return String(r[key] == null ? "" : r[key]); }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
}

/* ------------------------------------------------------------------
 * 部门选择框：列表式 / 树状 两种显示模式。
 * 拖动、列表/树状切换、树状渲染（默认收起分支）统一由 /oaones/appsyn/js/picker.js 提供，
 * 这里仅保留与“部门选择框”相关的薄封装，供各面板继续调用。
 * ------------------------------------------------------------------ */
function deptViewMode(key) { return OaonesPicker.mode(key); }
function renderDeptTreeHtml(key, rows, opt) { return OaonesPicker.renderTree(key, rows, opt); }
function partialSelectedMap(ids, childGetter) { return OaonesPicker.partialSelected(ids, childGetter); }

// 各选择框的显示模式切换/树节点展开收起后，回调对应渲染函数重绘
OaonesPicker.register("oaPick", function () { renderOaPickList(); });
OaonesPicker.register("wxPick", function () { renderWxPickList(); });
OaonesPicker.register("oaBind", function () { renderOaBindList(); });
// “企微数据基准”页筛选栏的“选择企微部门”弹框（列表式/树状切换后重绘）
OaonesPicker.register("wxDeptPick", function () { renderWxDeptPickList(); });

function resetPendingOaPicks() {
    // 行内“已选但未保存”的预览值：数据重新加载（含绑定成功后）一律清空，避免残留
    if (typeof wxPickValues !== "undefined") { wxPickValues = {}; }
    if (typeof wxOaPick !== "undefined") { wxOaPick = {}; }
}

function oaSubLabel(id) {
    id = String(id == null ? "" : id);
    if (!id) { return ""; }
    for (var i = 0; i < oaSubOptions.length; i++) {
        if (String(oaSubOptions[i].id) === id) {
            return oaSubOptions[i].name || oaSubOptions[i].fullname || "";
        }
    }
    return "";
}

function oaDeptLabel(id) {
    id = String(id == null ? "" : id);
    if (!id) { return ""; }
    for (var i = 0; i < oaDeptOptions.length; i++) {
        if (String(oaDeptOptions[i].id) === id) {
            var d = oaDeptOptions[i];
            return d.name || d.fullname || "";
        }
    }
    return "";
}

function renderOaFilterInputs() {
    var fbIds = state.oaFbIds || [];
    var deptIds = state.oaDeptIds || [];
    byId("oaFbInput").value = fbIds.length === 1
        ? oaSubLabel(fbIds[0]) + "（ID " + fbIds[0] + "）"
        : (fbIds.length > 1 ? "已选 " + fbIds.length + " 个分部" : "");
    byId("oaDeptInput").value = deptIds.length === 1
        ? oaDeptLabel(deptIds[0]) + "（ID " + deptIds[0] + "）"
        : (deptIds.length > 1 ? "已选 " + deptIds.length + " 个部门" : "");
    byId("oaFbPicker").classList.toggle("has-value", fbIds.length > 0);
    byId("oaDeptPicker").classList.toggle("has-value", deptIds.length > 0);
}

function render() {
    // 筛选栏的“选择企微部门”按当前数据（部门快照）刷新显示
    renderWxDeptFilterInput();
    if (state.view === "manage") { renderManage(); }
    else if (state.view === "tree") { renderDeptTree(); }
    else { renderWxUnbound(); }
}

/**
 * 保存“OA 部门 → 企微部门”绑定：OA数据基准页的选择弹框里双击或点“确定”时立即调用
 * （页面上不再有保存/批量绑定按钮）。
 *
 * @param value 企微部门ID__父部门ID（弹框选项的 data-value）
 */
function saveDeptBind(oaDeptId, value) {
    oaDeptId = String(oaDeptId == null ? "" : oaDeptId);
    value = String(value == null ? "" : value);
    if (!oaDeptId || !value) { return; }
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    if (state.saving) { return; }
    var parts = value.split("__");
    state.saving = true;
    var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + oaDeptId + "']");
    if (picker) { picker.classList.add("wx-picker-busy"); }
    Oaones.api("dept.save", {
        cfg_id: state.cfgId,
        oa_dept_id: oaDeptId,
        wx_dept_id: parts[0],
        wx_parent_dept_id: parts[1] || "0",
        remark: ""
    }, function (res) {
        state.saving = false;
        if (picker) { picker.classList.remove("wx-picker-busy"); }
        delete wxPickValues[oaDeptId];
        if (res.code === 0) {
            showToast("已绑定" + P_SHORT + "部门 " + (wxName(parts[0]) || ("ID " + parts[0])));
            loadData();
        } else {
            showToast("绑定失败：" + res.msg, 2);
            // 保存失败：行内回到已保存的绑定，不残留看起来已绑定的值
            updateWxPickerDom(oaDeptId);
        }
    });
}

/**
 * 保存“企微部门 → OA 部门”绑定（“企微数据基准”页）：选择弹框里双击或点“确定”时立即调用。
 */
function saveWxOaBind(wxDeptId, oaDeptId) {
    wxDeptId = String(wxDeptId == null ? "" : wxDeptId);
    oaDeptId = String(oaDeptId == null ? "" : oaDeptId);
    if (!wxDeptId || !oaDeptId) { return; }
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    if (state.saving) { return; }
    state.saving = true;
    var picker = document.querySelector("#wxBody .wx-picker[data-kind='oabind'][data-wx='" + wxDeptId + "']");
    if (picker) { picker.classList.add("wx-picker-busy"); }
    Oaones.api("dept.save", {
        cfg_id: state.cfgId,
        oa_dept_id: oaDeptId,
        wx_dept_id: wxDeptId,
        wx_parent_dept_id: wxParentOf(wxDeptId),
        remark: ""
    }, function (res) {
        state.saving = false;
        if (picker) { picker.classList.remove("wx-picker-busy"); }
        delete wxOaPick[wxDeptId];
        if (res.code === 0) {
            showToast("已绑定 OA 部门 " + oaBindLabel(oaDeptId));
            loadData();
        } else {
            showToast("绑定失败：" + res.msg, 2);
            // 保存失败：行内选择框回到“未选择”，不残留看起来已绑定的值
            updateOaBindPickerDom(wxDeptId);
        }
    });
}

/** 企微部门在快照中的父部门ID（绑定接口需要带上） */
function wxParentOf(wxDeptId) {
    wxDeptId = String(wxDeptId == null ? "" : wxDeptId);
    for (var i = 0; i < wxDepts.length; i++) {
        if (String(wxDepts[i].id) === wxDeptId) {
            return String(wxDepts[i].parentid == null ? "0" : wxDepts[i].parentid);
        }
    }
    return "0";
}

/** 立即自动绑定：把“智能匹配”的结果一次性写入 */
function doAutoBind(matches) {
    matches = matches || [];
    if (!matches.length) { return; }
    var oaIds = [];
    var wxIds = [];
    var wxParents = [];
    matches.forEach(function (m) {
        oaIds.push(String(m.oa_id));
        wxIds.push(String(m.wx_id));
        wxParents.push(String(m.parentid == null ? "0" : m.parentid));
    });
    byId("oaSmartMatchBtn").disabled = true;
    Oaones.api("dept.autoBatchSave", {
        cfg_id: state.cfgId,
        oa_ids: oaIds.join(","),
        wx_ids: wxIds.join(","),
        wx_parent_ids: wxParents.join(",")
    }, function (res) {
        byId("oaSmartMatchBtn").disabled = false;
        if (res.code === 0) {
            showToast("自动绑定成功，共 " + (res.data && res.data.success) + " 条");
            loadData();
        } else {
            showToast("自动绑定失败：" + res.msg, 2);
        }
    });
}

function delBind(id) {
    OaonesToast.confirm("确认删除该部门绑定？", function () {
        Oaones.api("dept.delete", { id: id }, function (res) {
            if (res.code === 0) { loadData(); }
            else { showToast(res.msg, 2); }
        });
    }, { danger: true });
}

function updateTmp() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    byId("refreshTmpBtn").disabled = true;
    byId("refreshTmpBtn").textContent = "快照更新中…";
    Oaones.api("dept.tmpRefresh", { cfg_id: state.cfgId }, function (res) {
        byId("refreshTmpBtn").disabled = false;
        byId("refreshTmpBtn").textContent = "更新" + P_SHORT + "部门快照";
        if (res.code !== 0) { showToast("快照更新失败: " + res.msg, 2); return; }
        showToast("快照更新成功，" + P_SHORT + "部门 " + (res.data && res.data.department_count) + " 个");
        loadData();
    });
}

byId("manageBody").addEventListener("click", function (e) {
    var t = e.target;
    if (t.classList.contains("unbind-btn")) {
        delBind(t.getAttribute("data-id"));
    }
});
byId("wxBody").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("no-bind-btn")) {
        toggleWxNoBind(t, true);
    } else if (t.classList.contains("cancel-nobind-btn")) {
        toggleWxNoBind(t, false);
    } else if (t.classList.contains("unbind-btn")) {
        // 已绑定行按“一行一条绑定关系”列出全部绑定的 OA 部门，行尾的“解绑”只解除该条绑定
        delBind(t.getAttribute("data-id"));
    }
});
document.addEventListener("click", function (e) {
    if (!e.target || !e.target.closest) { return; }
    // 列表式 / 树状 切换与树节点展开收起由 /oaones/appsyn/js/picker.js 统一处理
    var option = e.target.closest(".wx-pick-option");
    if (option) {
        if (option.hasAttribute("data-group")) { return; }
        // “树状映射”页的分部/部门筛选弹框：只改选中态，双击或“确定”才写回筛选条件
        if (option.closest("#deptTreePickList")) { deptTreeTogglePick(option); return; }
        // “企微数据基准”页的“企微部门”筛选弹框：只改选中态（连带下级），双击或“确定”才写回筛选条件
        if (option.closest("#wxDeptPickList")) { toggleWxDeptPick(option); return; }
        if (oaBindPickWxId) { pickOaBindOption(option); }
        else if (oaPickType) { toggleOaPick(option); }
        else { pickWxOption(option); }
        return;
    }
    var picker = e.target.closest(".wx-picker");
    if (picker && !picker.classList.contains("disabled")) {
        if (picker.getAttribute("data-kind") === "oabind") { showOaBindPick(picker.getAttribute("data-wx")); }
        else { showWxPickModal(picker.getAttribute("data-oa")); }
    }
});
// 关闭按钮 / 点遮罩 / 取消：都放弃弹框内本次选择，行内显示回退到打开前状态（不落库）
byId("wxPickCloseBtn").addEventListener("click", function () { cancelWxPick(); });
byId("wxPickMask").addEventListener("click", function (e) {
    if (e.target === this) { cancelWxPick(); }
});
byId("wxPickSearch").addEventListener("input", function () {
    if (wxPickOaId) { renderWxPickList(); }
});
// 企微部门弹框：单击选中，双击该行即落库；底部“清空选择/取消/确定”同理（确定=保存绑定）
byId("wxPickOkBtn").addEventListener("click", confirmWxPick);
byId("wxPickClearBtn").addEventListener("click", clearWxPickSelection);
byId("wxPickCancelBtn").addEventListener("click", cancelWxPick);
byId("wxPickList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group")) { return; }
    if (option.classList.contains("disabled")) { return; }
    // 双击前已收到两次单击，这里确保该行处于选中态后再落库
    pickWxOption(option);
    confirmWxPick();
});
byId("oaPickCloseBtn").addEventListener("click", hideOaPick);
byId("oaPickMask").addEventListener("click", function (e) {
    if (e.target === this) { hideOaPick(); }
});
byId("oaPickSearch").addEventListener("input", function () {
    if (oaPickType) { renderOaPickList(); }
});
// 双击选项：多选（分部/部门）也立即确定选择并退出弹框
byId("oaPickList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group") || !oaPickType) { return; }
    // 双击前已收到两次单击（选中→取消），这里确保该行处于选中态后再确定
    if (oaPickSelected.indexOf(option.getAttribute("data-id")) < 0) { toggleOaPick(option); }
    applyOaPick();
});
byId("oaPickClearBtn").addEventListener("click", clearOaPickSelection);
byId("oaPickCancelBtn").addEventListener("click", hideOaPick);
byId("oaPickOkBtn").addEventListener("click", applyOaPick);
// 企微侧 OA 部门弹框：单击选中，双击该行即落库（确定=保存绑定）
byId("oaBindCloseBtn").addEventListener("click", cancelOaBindPick);
byId("oaBindMask").addEventListener("click", function (e) {
    if (e.target === this) { cancelOaBindPick(); }
});
byId("oaBindCancelBtn").addEventListener("click", cancelOaBindPick);
byId("oaBindClearBtn").addEventListener("click", clearOaBindPick);
byId("oaBindOkBtn").addEventListener("click", confirmOaBindPick);
byId("oaBindList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group")) { return; }
    if (option.classList.contains("disabled")) { return; }
    pickOaBindOption(option);
    confirmOaBindPick();
});
byId("oaBindSearch").addEventListener("input", function () {
    if (oaBindPickWxId) { renderOaBindList(); }
});
byId("oaFbPicker").addEventListener("click", function () { openOaPick("fb"); });
byId("oaDeptPicker").addEventListener("click", function () { openOaPick("dept"); });
byId("oaFbClear").addEventListener("click", function (e) {
    e.stopPropagation();
    clearOaFbFilter();
});
byId("oaDeptClear").addEventListener("click", function (e) {
    e.stopPropagation();
    clearOaDeptFilter();
});
byId("oaSearchBtn").addEventListener("click", doOaSearch);
byId("oaFilterReset").addEventListener("click", resetOaFilters);
byId("oaSmartMatchBtn").addEventListener("click", doSmartMatch);
byId("smartApplyBtn").addEventListener("click", smartApplyNow);
byId("smartShowBtn").addEventListener("click", smartShowOnly);
byId("smartCloseBtn").addEventListener("click", smartCloseOnly);
byId("smartMask").addEventListener("click", function (e) {
    if (e.target === this) { smartCloseOnly(); }
});
// 企微数据基准页的“批量免绑定”仍需勾选（只有未绑定部门可勾选）：表头复选框只全选当页
byId("checkAllWx").addEventListener("change", function () {
    // 当页全选：按 deptBindWeComUnboundPanel.jsp 的 wxPageSelectableIds 只选当前页可绑定部门
    toggleWxUnboundPageChecks(this.checked);
});
byId("cfgFilter").addEventListener("change", function () {
    // 租户上下文里下拉是隐藏的（租户锁定），这里再兜一层，避免脚本/键盘误触发切换租户
    if (Oaones.cfgLocked()) { this.value = state.cfgId; return; }
    state.cfgId = this.value;
    state.oaListVisible = false;
    state.oaFbIds = [];
    state.oaDeptIds = [];
    state.oaMatchOnlyIds = [];
    state.wxDeptIds = [];
    state.wxStates = ["bound", "unbound", "nobind"];
    state.wxOnlyChecked = false;
    renderWxDeptFilterInput();
    syncWxStateFilterInputs();
    loadData();
});
byId("tabManage").addEventListener("click", function () {
    state.view = "manage";
    byId("tabManage").classList.add("active");
    byId("tabWxUnbound").classList.remove("active");
    byId("tabTree").classList.remove("active");
    byId("managePanel").style.display = "";
    byId("wxUnboundPanel").style.display = "none";
    byId("deptTreePanel").style.display = "none";
    render();
});
byId("tabWxUnbound").addEventListener("click", function () {
    state.view = "wxunbound";
    byId("tabWxUnbound").classList.add("active");
    byId("tabManage").classList.remove("active");
    byId("tabTree").classList.remove("active");
    byId("managePanel").style.display = "none";
    byId("wxUnboundPanel").style.display = "";
    byId("deptTreePanel").style.display = "none";
    render();
});
byId("tabTree").addEventListener("click", function () {
    state.view = "tree";
    byId("tabTree").classList.add("active");
    byId("tabManage").classList.remove("active");
    byId("tabWxUnbound").classList.remove("active");
    byId("managePanel").style.display = "none";
    byId("wxUnboundPanel").style.display = "none";
    byId("deptTreePanel").style.display = "";
    render();
});
// 企微数据基准页的“企微部门”筛选弹框（多选 + 连带下级）：筛选栏的选择框点击打开弹框
byId("wxDeptFilterPicker").addEventListener("click", openWxDeptPick);
byId("wxDeptFilterClear").addEventListener("click", function (e) {
    e.stopPropagation();
    clearWxDeptFilter();
});
byId("wxDeptPickCloseBtn").addEventListener("click", hideWxDeptPick);
byId("wxDeptPickMask").addEventListener("click", function (e) {
    if (e.target === this) { hideWxDeptPick(); }
});
byId("wxDeptPickSearch").addEventListener("input", renderWxDeptPickList);
byId("wxDeptPickClearBtn").addEventListener("click", clearWxDeptPickSelection);
byId("wxDeptPickCancelBtn").addEventListener("click", hideWxDeptPick);
byId("wxDeptPickOkBtn").addEventListener("click", applyWxDeptPick);
// 双击选项：多选也立即确定选择并退出弹框
byId("wxDeptPickList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group")) { return; }
    // 双击前已收到两次单击（选中→取消），这里确保该行处于选中态后再确定
    if (wxDeptPickSelected.indexOf(option.getAttribute("data-id")) < 0) { toggleWxDeptPick(option); }
    applyWxDeptPick();
});
byId("wxStateUnbound").addEventListener("change", function () {
    applyWxStateFilter("unbound", this.checked);
    syncWxStateFilterInputs();
});
byId("wxStateBound").addEventListener("change", function () {
    applyWxStateFilter("bound", this.checked);
    syncWxStateFilterInputs();
});
byId("wxStateNoBind").addEventListener("change", function () {
    applyWxStateFilter("nobind", this.checked);
    syncWxStateFilterInputs();
});
byId("batchNoBindWxBtn").addEventListener("click", batchNoBindFromWx);
byId("refreshBtn").addEventListener("click", loadData);
byId("refreshTmpBtn").addEventListener("click", updateTmp);

OaonesPicker.syncSwitches();
loadCfgs();
</script>
</body>
</html>
