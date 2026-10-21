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
    String oaonesPBApiUrl = oaonesPBFs ? "/oaones/Feishu/api.jsp"
            : (oaonesPBDd ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    String oaonesPBNavbarPath = oaonesPBFs ? "/oaones/Feishu/navbar.jsp"
            : (oaonesPBDd ? "/oaones/DingTalk/navbar.jsp" : "/oaones/WeCom/navbar.jsp");
    request.setAttribute("oaonesPBPlatform", oaonesPBFs ? "feishu" : (oaonesPBDd ? "dingtalk" : "wecom"));
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
%>

<%@ include file="/oaones/redirect.inc.jsp" %>
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
<%@ include file="/oaones/assets.jsp" %>
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
    <title>绑定详情 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };
    window.OAONES_CFG_ID = "<%=oaonesCfgId%>";</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/excel.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<jsp:include page="<%= oaonesPBNavbarPath %>">
    <jsp:param name="active" value="userBind"/>
</jsp:include>
<div class="page">
    <jsp:include page="userSwitch.jsp">
        <jsp:param name="active" value="detail"/>
    </jsp:include>
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
            <button class="btn secondary" id="exportExcelBtn"
                    title="把当前二级菜单的数据导出为 Excel 文件（.xlsx）：OA 数据基准 / <%= oaonesPBShort %>数据基准导出当前列表，树状映射导出“分部-部门-人员”对照；导出范围与页面当前筛选条件一致">导出Excel</button>
            <button class="btn secondary" id="refreshTmpBtn"
                    title="从<%= oaonesPBFull %>拉取全部成员及其主要数据，写入 uf_oaones_user_tmp，供本页绑定选择与默认规则匹配使用">更新<%= oaonesPBShort %>人员快照</button>
            <button class="btn secondary" id="refreshBtn">刷新</button>
        </div>
        <div class="help-source" id="cfgHelpText"><span class="rule">页面分为“OA 数据基准”（A 页，OA 人员侧）与“<%= oaonesPBShort %>数据基准”（B 页，<%= oaonesPBShort %>成员侧），
            两者均展示两侧部门的绑定情况：若某人员所在部门已建立部门绑定、
            但该人员尚未绑定，行内会出现“同步人员”按钮，可按部门绑定关系把人员同步到对侧
            （A 页 OA 人员 → <%= oaonesPBShort %>，B 页<%= oaonesPBShort %>人员 → OA），并自动建立人员绑定。
            同步中心仅处理已绑定人员；OA 部门未绑定<%= oaonesPBShort %>部门时不自动创建<%= oaonesPBShort %>部门。
            右上角“导出 Excel”按当前二级菜单导出（.xlsx）：“OA 数据基准”“<%= oaonesPBShort %>数据基准”导出当前筛选后的列表，
            “树状映射”导出“分部 → 部门 → 人员”的对照明细；导出范围是当前筛选命中的全部行，
            不受分页与节点收起/展开影响，多对一按“一行一条关系”展开。
            绑定只允许选择 uf_oaones_user_tmp(type=0、status=0)中的<%= oaonesPBShort %>成员，已标记“免绑定”的成员不能绑定，
            需先在“<%= oaonesPBShort %>数据基准”页取消免绑定；快照为空或成员状态异常时请先“更新<%= oaonesPBShort %>人员快照”。
            OA 到<%= oaonesPBShort %>的人员绑定支持“多对一”：多个 OA 账户可对应<%= oaonesPBShort %>同一个账户，
            同一<%= oaonesPBShort %>成员可被多个 OA 人员绑定，因此已被其他 OA 人员绑定的成员仍会出现在选择框里
            （选项上标注“已被 N 个 OA 人员绑定”），核对后点击即可继续建立绑定。
            “OA 数据基准”页点开行内选择框、在弹框里点选<%= oaonesPBShort %>成员后立即保存绑定，行内不再放“绑定”按钮。
            “OA 数据基准”页的“<%= oaonesPBShort %>成员”列在选择框后面带“免绑定”按钮：不需要绑定<%= oaonesPBShort %>成员的 OA 人员
            （如系统账号、外部人员）可标记为免绑定，标记后不参与默认批量绑定与同步人员，
            也不会出现在“<%= oaonesPBShort %>数据基准”页的 OA 人员候选里；状态显示“免绑定”，同一列可“取消免绑定”；
            也可勾选多行后点筛选栏“批量免绑定”一次标记。
            “OA 数据基准”页的行内按钮统一放在“<%= oaonesPBShort %>成员”列（解除绑定/取消免绑定/免绑定/同步人员，绑定由选择框点选成员即保存）；
            “<%= oaonesPBShort %>数据基准”页的行内按钮统一放在“OA 人员”列（解绑/取消免绑定/免绑定/同步人员），
            两个列表都不再单独设“操作”列；表头只写字段名，操作说明看本说明或按钮悬浮提示。
            “OA 数据基准”页每行行首带复选框（仅未绑定人员可勾选），勾选后点击筛选栏“默认批量绑定”右侧的“同步人员”，
            会按部门绑定关系把这些人员批量同步到<%= oaonesPBShort %>并建立人员绑定；同步完成后用弹框列表给出成功统计与失败人员原因
            （例如“所在部门没有进行绑定，请手动创建人员或先进行部门绑定”），失败人员保持勾选，处理后可直接重试。
            两个列表的表头复选框都只全选当前页；要一次选中当前筛选条件下的全部人员（含未翻到的页），
            勾选表头上方（与表头复选框对齐）的“跨页全选”，再点统计行末尾的“仅显示已选择数据”
            只看已勾选的行核对无误后执行批量操作。
            “<%= oaonesPBShort %>数据基准”页的“OA 人员”列对已绑定成员按“一行一个 OA 人员”列出全部绑定关系：
            一个<%= oaonesPBShort %>成员对应多个 OA 人员时分行显示，每行后面直接带该条绑定的“解绑”按钮，解绑后可重新绑定。
            未绑定成员则在同一列放选择框，选择框下方跟“免绑定”按钮（列表不再单独放“绑定OA人员”按钮）；
            点击选择框弹出 OA 人员列表（弹框内默认按该行<%= oaonesPBShort %>成员姓名搜索），双击或点击“确定”即保存绑定。
            “<%= oaonesPBShort %>数据基准”页同样支持勾选批量同步（<%= oaonesPBShort %> → OA）：行首复选框只对“未绑定”成员可用（已绑定与免绑定成员不可勾选），
            勾选后点击筛选栏“同步人员”即按部门绑定关系批量在 OA 建号并建立人员绑定，结果弹框同样逐条列出失败人员与原因。
            勾选后也可点击筛选栏“批量免绑定”，一次把勾选的<%= oaonesPBShort %>成员标记为免绑定（免绑定成员不参与绑定选择，可取消免绑定后再次绑定）。
            <b>从“<%= oaonesPBShort %>租户”列表点开某个租户后进入本页时，当前租户锁定为该租户</b>：
            工具栏的“<%= oaonesPBFull %>租户”只读展示、不能切换租户（要换租户请回“<%= oaonesPBShort %>租户”列表重新点开另一个租户）。</span></div>
        <p class="notice" id="cfgPermTip" style="display:none;"></p>
        <div class="sub-tabs">
            <button id="tabManage" class="active">OA数据基准</button>
            <span class="help-tip" id="oaHelpTip" data-help="oaHelpText" role="button" tabindex="0"
                  title="查看 OA 数据基准说明" aria-label="查看 OA 数据基准说明">?</span>
            <button id="tabWxUnbound"><%= oaonesPBShort %>数据基准</button>
            <span class="help-tip" id="wxHelpTip" data-help="wxHelpText" role="button" tabindex="0"
                  title="查看<%= oaonesPBShort %>数据基准说明" aria-label="查看<%= oaonesPBShort %>数据基准说明">?</span>
            <button id="tabTree">树状映射</button>
            <span class="help-tip" id="treeHelpTip" data-help="treeHelpText" role="button" tabindex="0"
                  title="查看树状映射说明" aria-label="查看树状映射说明">?</span>
        </div>

<%-- 二级菜单：OA数据基准（A 页，人员绑定 - OA 人员侧） --%>
<jsp:include page="userBindOaPanel.jsp"/>

<%-- 二级菜单：平台数据基准（B 页，人员绑定 - 平台成员侧） --%>
<jsp:include page="userBindPanel.jsp"/>

<%-- 二级菜单：树状映射（人员绑定 - OA 部门/成员 与 平台部门/成员 两列对照树） --%>
<jsp:include page="userTreePanel.jsp"/>

    </div>
</div>

<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
var state = { cfgs: [], cfgId: String(window.OAONES_CFG_ID || ""), view: "manage", oaListVisible: false, oaKeyword: "", oaFbIds: [], oaDeptIds: [], oaMatchOnlyIds: [], oaOnlyChecked: false, wxKeyword: "", wxStates: ["bound", "unbound", "nobind"], wxDeptIds: [], wxOnlyChecked: false };
var oaUsers = [];
var oaSubOptions = [];
var oaDeptOptions = [];
var wxUsers = [];
var binds = [];
var boundByOa = {};
var boundByWx = {};
/** OA 人员侧“免绑定”标记（A 页 OA 数据基准）：oa_user_id → true */
var noBindByOa = {};
/** 多对一：同一企微成员 → 全部绑定的 OA 人员（用于页面展示“已绑定的 OA 人员”列表） */
var boundListByWx = {};
var deptBinds = [];
var deptBindByOa = {};
var deptBindByWx = {};
var oaPickType = "";
var oaPickSelected = [];
var wxPickOaId = "";
var wxPickId = "";
var wxPickValue = "";
var loadSeq = 0;
var defaultMatchData = [];
var defaultMatchRules = [];

function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
/** 该 OA 人员是否已标记免绑定（A 页 OA 数据基准）。 */
function isOaNoBind(oaId) { return Boolean(noBindByOa[String(oaId == null ? "" : oaId)]); }
/**
 * 绑定方式（uf_oaones_user_bind.bind_source）：0 手动绑定 / 1 系统绑定 / 2 自行绑定（本人扫码）。
 * 自行绑定的记录被更换或解绑时，必须二次确认（见 SELF_BIND_CONFIRM_MSG）。
 */
function bindSourceText(value) {
    var v = String(value == null ? "" : value);
    if (v === "1") { return "系统绑定"; }
    if (v === "2") { return "自行绑定"; }
    return "手动绑定";
}
/** 该绑定是否为“自行绑定”（本人扫码）。 */
function isSelfBind(bind) {
    return !!bind && String(bind.bind_source == null ? "" : bind.bind_source) === "2";
}
/** 自行绑定的记录在更换/解绑时的二次确认文案。 */
var SELF_BIND_CONFIRM_MSG = "该数据为用户自行绑定，确定是否继续操作？";
/** 按绑定记录ID取绑定行（binds 为当前租户的全部有效绑定）。 */
function findBindById(id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < binds.length; i++) {
        if (String(binds[i].id) === key) { return binds[i]; }
    }
    return null;
}
/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert，统一由 /oaones/js/toast.js 弹出。
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
        labels[i].classList.toggle("checked", Boolean(input && input.checked));
    }
}

/**
 * 当前租户初值：地址带入的 cfg_id（从“租户详情”进入）在本人可见租户里就用它，
 * 否则回退到第一个租户（无可见租户时为空）。
 */
function resolveCfgId(cfgs, wanted) {
    // 租户上下文（带 cfg_id 从“企微租户”列表点开进入）：锁定该租户，不切换、也不回退到其它租户
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
        loadOrgOptions();
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
        "当前还没有任何" + P_SHORT + "租户，请先到“" + P_SHORT + "租户配置”页新建租户，再回到本页进行绑定。",
        "当前登录人不是任何" + P_SHORT + "租户的维护人，暂无可操作的租户；"
            + "请联系管理员在“" + P_SHORT + "租户配置”页把本人加入该租户的维护人。");
    tip.style.display = "";
}

function loadOrgOptions() {
    Oaones.api("user.orgOptions", {}, function (res) {
        if (res.code !== 0) {
            oaSubOptions = [];
            oaDeptOptions = [];
            return;
        }
        oaSubOptions = res.data.oa_subcompanies || [];
        oaDeptOptions = res.data.oa_departments || [];
        renderOaFilterInputs();
    });
}

function renderCfgSelect() {
    var locked = Oaones.cfgLocked();
    // 租户上下文：隐藏下拉，改为只读展示（不能切换租户）
    byId("cfgFilter").style.display = locked ? "none" : "";
    byId("cfgLocked").style.display = locked ? "" : "none";
    if (locked) {
        byId("cfgLocked").innerHTML = "<b>" + esc(Oaones.lockedCfgText(lockedCfgLabel())) + "</b>";
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
    resetOaPersonPick();
    if (!state.cfgId) {
        oaUsers = []; wxUsers = []; binds = [];
        boundByOa = {}; boundByWx = {}; boundListByWx = {}; noBindByOa = {};
        deptBinds = []; deptBindByOa = {}; deptBindByWx = {};
        wxDepts = [];
        render();
        return;
    }
    loadSeq++;
    var seq = loadSeq;
    var oaDone = false;
    var bindDone = false;
    var wxDone = false;
    var deptDone = false;
    var noBindDone = false;
    var wxDeptDone = false;
    function finish() {
        if (oaDone && bindDone && wxDone && deptDone && noBindDone && wxDeptDone && seq === loadSeq) { render(); }
    }
    // 企微部门快照（“树状映射”页右列显示已绑定的企微部门名称）：
    // 没有部门快照的历史环境返回非 0 时按“无快照”处理，只影响企微部门名称显示
    Oaones.api("dept.wxlist", { cfg_id: state.cfgId }, function (res) {
        wxDepts = (res.code === 0 && res.data) ? res.data : [];
        wxDeptDone = true;
        finish();
    });
    // OA 人员侧“免绑定”标记（A 页 OA 数据基准）：没有该接口的历史环境返回非 0 时按“无免绑定”处理
    Oaones.api("user.oaNoBindList", { cfg_id: state.cfgId }, function (res) {
        noBindByOa = {};
        if (res.code === 0) {
            (res.data || []).forEach(function (id) {
                if (id != null && String(id) !== "") { noBindByOa[String(id)] = true; }
            });
        }
        noBindDone = true;
        finish();
    });
    Oaones.api("user.oaUsers", { keyword: "" }, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); oaUsers = []; }
        else { oaUsers = res.data || []; }
        oaDone = true;
        finish();
    });
    Oaones.api("user.list", { cfg_id: state.cfgId, keyword: "" }, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); binds = []; }
        else { binds = res.data || []; }
        buildMaps();
        bindDone = true;
        finish();
    });
    Oaones.api("user.wxlist", { cfg_id: state.cfgId }, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); wxUsers = []; }
        else { wxUsers = res.data || []; }
        wxDone = true;
        finish();
    });
    Oaones.api("user.deptBinds", { cfg_id: state.cfgId }, function (res) {
        if (res.code !== 0) { deptBinds = []; }
        else { deptBinds = res.data || []; }
        buildDeptMaps();
        deptDone = true;
        finish();
    });
}

function buildMaps() {
    boundByOa = {};
    boundByWx = {};
    boundListByWx = {};
    binds.forEach(function (b) {
        boundByOa[String(b.oa_user_id)] = b;
        var wxId = String(b.wx_user_id);
        boundByWx[wxId] = b;
        // 多对一：同一企微成员可被多个 OA 人员绑定，保留全部绑定记录供页面展示
        if (!boundListByWx[wxId]) { boundListByWx[wxId] = []; }
        boundListByWx[wxId].push(b);
    });
}

function buildDeptMaps() {
    deptBindByOa = {};
    deptBindByWx = {};
    (deptBinds || []).forEach(function (b) {
        var oaDeptId = String(b.oa_dept_id == null ? "" : b.oa_dept_id);
        var wxDeptId = String(b.wx_dept_id == null ? "" : b.wx_dept_id);
        if (oaDeptId && oaDeptId !== "0") { deptBindByOa[oaDeptId] = b; }
        if (wxDeptId && wxDeptId !== "0") { deptBindByWx[wxDeptId] = b; }
    });
}

/** A 页按 OA 部门取部门绑定；无部门/未绑定返回 null。 */
function deptBindOfOa(oaDeptId) {
    var id = String(oaDeptId == null ? "" : oaDeptId);
    if (!id || id === "0") { return null; }
    return deptBindByOa[id] || null;
}

/** B 页按企微部门取部门绑定；无部门/未绑定返回 null。 */
function deptBindOfWx(wxDeptId) {
    var id = String(wxDeptId == null ? "" : wxDeptId);
    if (!id || id === "0") { return null; }
    return deptBindByWx[id] || null;
}

/**
 * “部门绑定”列单元格：显示两侧部门是否已绑定。
 * 同一企微部门可被多个 OA 部门绑定（一对多），此时把全部 OA 部门一并列出。
 * OA 部门名称统一走 OaonesHrm.dept 输出超链接（点击打开泛微标准部门显示页面），
 * 企微部门在泛微没有标准页面，只按普通文本展示。
 */
function deptBindCellHtml(bind, noteText) {
    if (bind) {
        var wxDeptId = String(bind.wx_dept_id == null ? "" : bind.wx_dept_id);
        var list = (boundListByWx && boundListByWx[wxDeptId]) ? boundListByWx[wxDeptId] : [bind];
        if (list.length > 1) {
            var oaTexts = list.map(function (b) {
                var id = String(b.oa_dept_id);
                return OaonesHrm.dept(id, oaDeptLabel(id));
            }).join("、");
            return "<span class='tag bound'>部门已绑定</span>"
                + "<div class='user-mini'>" + P_SHORT + "部门 " + esc(wxDeptId) + " ⇄ OA 部门 " + oaTexts
                + "（" + list.length + " 个）</div>";
        }
        return "<span class='tag bound'>部门已绑定</span>"
            + "<div class='user-mini'>OA 部门 "
            + OaonesHrm.dept(bind.oa_dept_id, oaDeptLabel(bind.oa_dept_id))
            + " ⇄ " + P_SHORT + "部门 " + esc(bind.wx_dept_id) + "</div>";
    }
    return "<span class='tag unbound'>部门未绑定</span>"
        + (noteText ? "<div class='user-mini'>" + esc(noteText) + "</div>" : "");
}

function wxLabel(u) {
    return (u.wx_name || "") + "（" + (u.wx_user_id || "") + "）" + (u.wx_dept_name ? " / " + u.wx_dept_name : "");
}

function filterByKeyword(rows, keys, keyword) {
    var k = (keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (r) {
        var text = keys.map(function (key) { return String(r[key] == null ? "" : r[key]); }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
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
    if (state.view === "manage") { renderManage(); }
    else if (state.view === "tree") { renderTree(); }
    else { renderWxUnbound(); }
}

/**
 * 解绑（A 页与 B 页行内“解除绑定/解绑”共用）：
 * 自行绑定（bind_source=2，本人在个人账号中心扫码建立）的记录，解绑前用专用文案二次确认。
 */
function delBind(id) {
    var bind = findBindById(id);
    var msg = isSelfBind(bind) ? SELF_BIND_CONFIRM_MSG : "确认解除该人员绑定？";
    OaonesToast.confirm(msg, function () {
        Oaones.api("user.delete", { id: id }, function (res) {
            if (res.code === 0) { loadData(); }
            else { showToast(res.msg, 2); }
        });
    }, { danger: true });
}

function updateTmp() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var btn = byId("refreshTmpBtn");
    btn.disabled = true;
    btn.textContent = "快照更新中…";
    Oaones.api("user.tmpRefresh", { cfg_id: state.cfgId }, function (res) {
        btn.disabled = false;
        btn.textContent = "更新" + P_SHORT + "人员快照";
        if (res.code !== 0) { showToast("快照更新失败: " + res.msg, 2); return; }
        showToast("快照更新成功，" + P_SHORT + "成员 " + (res.data && res.data.user_count) + " 人");
        loadData();
    });
}

byId("manageBody").addEventListener("click", function (e) {
    var t = e.target;
    // 行内不再有“绑定”按钮：绑定由“企微成员”列的选择弹框点选成员时立即保存
    if (t.classList.contains("sync-btn") && t.hasAttribute("data-oa")) {
        syncOaPerson(t.getAttribute("data-oa"));
    } else if (t.classList.contains("oa-no-bind-btn")) {
        toggleOaNoBind(t, true);
    } else if (t.classList.contains("cancel-oa-nobind-btn")) {
        toggleOaNoBind(t, false);
    } else if (t.classList.contains("unbind-btn")) {
        delBind(t.getAttribute("data-id"));
    }
});

byId("wxBody").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    // B 页行内不再有“绑定OA人员”（弹框双击/“确定”即保存绑定），这里只处理同步/免绑定/解绑
    if (t.classList.contains("sync-btn") && t.hasAttribute("data-wx")) {
        syncWxPerson(t.getAttribute("data-wx"));
    } else if (t.classList.contains("no-bind-btn")) {
        toggleWxNoBind(t, true);
    } else if (t.classList.contains("cancel-nobind-btn")) {
        toggleWxNoBind(t, false);
    } else if (t.classList.contains("unbind-btn")) {
        delBind(t.getAttribute("data-id"));
    }
});

byId("cfgFilter").addEventListener("change", function () {
    // 租户上下文里下拉是隐藏的（租户锁定），这里再兜一层，避免脚本/键盘误触发切换租户
    if (Oaones.cfgLocked()) { this.value = state.cfgId; return; }
    state.cfgId = this.value;
    state.oaListVisible = false;
    state.oaKeyword = "";
    state.oaFbIds = [];
    state.oaDeptIds = [];
    state.oaMatchOnlyIds = [];
    state.oaOnlyChecked = false;
    state.wxKeyword = "";
    state.wxStates = ["bound", "unbound", "nobind"];
    state.wxDeptIds = [];
    state.wxOnlyChecked = false;
    defaultMatchData = [];
    defaultMatchRules = [];
    byId("oaKeyword").value = "";
    byId("wxKeyword").value = "";
    renderWxDeptFilterInput();
    syncWxStateFilterInputs();
    renderOaFilterInputs();
    clearOaChecked();
    clearWxChecked();
    loadData();
});
byId("tabManage").addEventListener("click", function () {
    state.view = "manage";
    byId("tabManage").classList.add("active");
    byId("tabWxUnbound").classList.remove("active");
    byId("tabTree").classList.remove("active");
    byId("managePanel").style.display = "";
    byId("wxUnboundPanel").style.display = "none";
    byId("treePanel").style.display = "none";
    render();
});
byId("tabWxUnbound").addEventListener("click", function () {
    state.view = "wxunbound";
    byId("tabWxUnbound").classList.add("active");
    byId("tabManage").classList.remove("active");
    byId("tabTree").classList.remove("active");
    byId("managePanel").style.display = "none";
    byId("wxUnboundPanel").style.display = "";
    byId("treePanel").style.display = "none";
    render();
});
byId("tabTree").addEventListener("click", function () {
    state.view = "tree";
    byId("tabTree").classList.add("active");
    byId("tabManage").classList.remove("active");
    byId("tabWxUnbound").classList.remove("active");
    byId("managePanel").style.display = "none";
    byId("wxUnboundPanel").style.display = "none";
    byId("treePanel").style.display = "";
    render();
});
byId("oaKeyword").addEventListener("input", function () {
    state.oaKeyword = this.value;
    if (state.view === "manage" && state.oaListVisible) { renderManage(); }
});
byId("oaKeyword").addEventListener("keydown", function (e) {
    if (e.key === "Enter") { doOaSearch(); }
});
byId("wxKeyword").addEventListener("input", function () {
    state.wxKeyword = this.value;
    if (state.view === "wxunbound") { renderWxUnbound(); }
});
byId("wxStateBound").addEventListener("change", function () {
    applyWxStateFilter("bound", this.checked);
    syncWxStateFilterInputs();
});
byId("wxStateUnbound").addEventListener("change", function () {
    applyWxStateFilter("unbound", this.checked);
    syncWxStateFilterInputs();
});
byId("wxStateNoBind").addEventListener("change", function () {
    applyWxStateFilter("nobind", this.checked);
    syncWxStateFilterInputs();
});
byId("oaSearchBtn").addEventListener("click", doOaSearch);
byId("oaResetBtn").addEventListener("click", resetOaFilter);
byId("defaultBindBtn").addEventListener("click", doDefaultBind);
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
byId("oaPickCloseBtn").addEventListener("click", hideOaPick);
byId("oaPickMask").addEventListener("click", function (e) {
    if (e.target === this) { hideOaPick(); }
});
byId("oaPickSearch").addEventListener("input", function () {
    if (oaPickType) { renderOaPickList(); }
});
byId("oaPickClearBtn").addEventListener("click", clearOaPickSelection);
byId("oaPickCancelBtn").addEventListener("click", hideOaPick);
byId("oaPickOkBtn").addEventListener("click", applyOaPick);
// 双击选项：多选（分部/部门）也立即确定选择并退出弹框
byId("oaPickList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group") || !oaPickType) { return; }
    // 双击前已收到两次单击（选中→取消），这里确保该行处于选中态后再确定
    if (oaPickSelected.indexOf(option.getAttribute("data-id")) < 0) { toggleOaPick(option); }
    applyOaPick();
});
byId("wxPickCloseBtn").addEventListener("click", hideWxPickModal);
byId("wxPickMask").addEventListener("click", function (e) {
    if (e.target === this) { hideWxPickModal(); }
});
byId("wxPickSearch").addEventListener("input", function () {
    if (wxPickOaId) { renderWxPickList(); }
});
/* B 页“企微部门”筛选弹框（多选 + 连带下级） */
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
byId("matchYesBtn").addEventListener("click", function () { chooseDefaultMatch(true); });
byId("matchNoBtn").addEventListener("click", function () { chooseDefaultMatch(false); });
byId("matchCloseBtn").addEventListener("click", hideDefaultMatchPrompt);
byId("matchMask").addEventListener("click", function (e) {
    if (e.target === this) { hideDefaultMatchPrompt(); }
});
document.addEventListener("click", function (e) {
    var option = e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (option) {
        // 树状分组行（部门/分部）不参与选择，由 picker.js 处理展开收起
        if (option.hasAttribute("data-group")) { return; }
        // B 页筛选栏“企微部门”弹框：多选（选中连带全部下级），确定后才写回筛选条件
        if (option.closest("#wxDeptPickList")) { toggleWxDeptPick(option); return; }
        // B 页（企微数据基准）选择 OA 人员弹框：单击选中（双击在弹框内另行确定）
        if (oaPersonPickWxId) { pickOaPersonOption(option); }
        else if (oaPickType) { toggleOaPick(option); }
        else if (wxPickOaId) { pickWxOption(option); }
        return;
    }
    var picker = e.target.closest ? e.target.closest(".wx-picker") : null;
    if (picker) {
        if (picker.getAttribute("data-kind") === "oaperson") { showOaPersonPick(picker.getAttribute("data-wx")); }
        else { showWxPickModal(picker.getAttribute("data-oa")); }
    }
});
// 各选择框：列表式/树状切换与树节点展开收起后重绘
OaonesPicker.register("oaPick", function () { renderOaPickList(); });
OaonesPicker.register("wxPick", function () { renderWxPickList(); });
OaonesPicker.register("oaPerson", function () { renderOaPersonList(); });
OaonesPicker.register("wxDeptPick", function () { renderWxDeptPickList(); });
OaonesPicker.syncSwitches();
byId("refreshBtn").addEventListener("click", loadData);
byId("refreshTmpBtn").addEventListener("click", updateTmp);

/**
 * 导出 Excel：按当前二级菜单导出（导出结果与页面当前筛选条件一致）。
 *   OA 数据基准 → 当前 OA 人员列表（含企微成员、部门绑定、绑定方式/原因、状态）
 *   企微数据基准 → 当前企微成员列表（一行一条绑定关系，未绑定/免绑定成员单独一行）
 *   树状映射     → “分部 → 部门 → 人员”的 OA ⇄ 企微 对照明细（含层级路径与未绑定人数）
 * 具体行数据与列定义由各面板脚本提供（exportOaExcel / exportWxExcel / exportTreeExcel）。
 */
function exportCurrentExcel() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    if (state.view === "manage") { exportOaExcel(); return; }
    if (state.view === "wxunbound") { exportWxExcel(); return; }
    if (state.view === "tree") { exportTreeExcel(); return; }
    showToast("当前页面没有可导出的数据", 1);
}

byId("exportExcelBtn").addEventListener("click", exportCurrentExcel);

loadCfgs();
</script>
</body>
</html>
