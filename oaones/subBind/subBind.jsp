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
    <title>分部绑定 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<jsp:include page="<%= oaonesPBNavbarPath %>">
    <jsp:param name="active" value="subBind"/>
</jsp:include>
<div class="page">
    <div class="card">
        <div class="toolbar">
            <label>OA 分部 ⇄ <%= oaonesPBShort %>租户</label>
            <span class="help-tip" id="cfgHelpTip" data-help="cfgHelpText" role="button" tabindex="0"
                  title="查看页面说明" aria-label="查看页面说明">?</span>
            <span style="flex:1;"></span>
            <button class="btn secondary" id="refreshBtn">刷新</button>
        </div>
        <div class="help-source" id="cfgHelpText">本页维护 <span class="rule">OA 分部 → <%= oaonesPBShort %>租户</span> 的绑定关系：
            分部绑定只登记“该分部归属哪个<%= oaonesPBFull %>租户”，分部对应的<span class="rule"><%= oaonesPBShort %>部门取该租户配置里维护的根部门</span>
            （在“<%= oaonesPBShort %>租户配置”页维护，本页不再单独维护<%= oaonesPBShort %>部门，因此不重复存储部门字段）。
            目前只支持 <span class="rule">一对一</span>：一个 OA 分部只绑定一个<%= oaonesPBShort %>租户，同一个<%= oaonesPBShort %>租户也只能被一个 OA 分部绑定
            （已被其他分部绑定的租户在弹框里不可选，需先解除那个分部的绑定）。
            页面按登录人可见范围展示：只列出你有权维护的<%= oaonesPBShort %>租户（未指定维护人的租户仅系统管理员可见）。
            <span class="rule">分部绑定是“部门自动绑定”的前置条件</span>：绑定分部与<%= oaonesPBShort %>（租户）后，
            “部门绑定”页的“智能匹配”可按部门编号或名称自动绑定该分部下的 OA 部门；
            分部未绑定<%= oaonesPBShort %>租户时，其下部门不参与自动匹配。
            <span class="rule">手动绑定部门不受此条件限制</span>，解除分部绑定也不会解除已建立（含手动建立）的部门绑定，
            只是这些部门要重新绑定分部后才能再按编号/名称自动匹配。
            绑定即时生效：点某行右侧的“<%= oaonesPBShort %>租户”选择框，在弹框里单击选中租户后
            <span class="rule">双击该行（或点“确定”）立即写入数据库完成绑定</span>，
            不需要再点保存；同一分部再次选择即更换所绑定的租户，已绑定行可“解绑”
            （解绑只解除分部与租户的绑定，不动已建立的部门绑定）。
            列表按 OA 分部分页展示，左侧“OA 分部筛选”支持多选（选中分部自动含其下级分部）。</div>
        <p class="notice" id="cfgPermTip" style="display:none;"></p>

<%-- 分部绑定主体（绑定列表与两个选择弹框） --%>
<jsp:include page="subBindManagePanel.jsp"/>

    </div>
</div>

<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
var state = { cfgs: [], subFbIds: [], saving: false };
/** 当前登录人可见的企微租户（cfg.list，已按维护人过滤） */
var oaSubOptions = [];
/** 分部绑定记录（仅本人可维护租户） */
var binds = [];
/** oa_sub_id → 绑定记录 */
var boundBySub = {};
/** cfg_id → 绑定记录（一对一：一个企微租户只会有一个分部绑定） */
var boundSubByCfg = {};
var cfgPickSubId = "";
var cfgPickId = "";
var cfgPickLabel = "";
/** OA 分部ID → 弹框内“已选未落库”的企微租户ID（仅作行内预览，双击/确定时落库） */
var cfgPickValues = {};
/** 打开弹框前的行内预览值：取消/关闭时回退到它，保证不残留未保存的选择 */
var cfgPickBackup = "";
var subPickType = "";
var subPickSelected = [];
var loadSeq = 0;

function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/js/toast.js 弹出。
 */
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

function loadCfgs() {
    Oaones.api("cfg.list", {}, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        state.cfgs = res.data || [];
        applyCfgPermTip(state.cfgs);
        loadData();
    });
}

/** 本页没有任何可用租户时，按登录人角色提示原因（超管=还没建租户；其他=不是任何租户的维护人）。 */
function applyCfgPermTip(cfgs) {
    var tip = byId("cfgPermTip");
    if ((cfgs || []).length) {
        tip.style.display = "none";
        tip.textContent = "";
        return;
    }
    tip.textContent = OaonesTips.pick(
        "当前还没有任何" + P_SHORT + "租户，请先到“" + P_SHORT + "租户配置”页新建租户，再回到本页进行分部绑定。",
        "当前登录人不是任何" + P_SHORT + "租户的维护人，暂无可绑定的租户；"
            + "请联系管理员在“" + P_SHORT + "租户配置”页把本人加入该租户的维护人。");
    tip.style.display = "";
}

function loadData() {
    resetPendingCfgPicks();
    loadSeq++;
    var seq = loadSeq;
    Oaones.api("sub.list", { keyword: "" }, function (res) {
        if (seq !== loadSeq) { return; }
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        binds = res.data.binds || [];
        oaSubOptions = res.data.oa_subcompanies || [];
        buildMaps();
        render();
    });
}

function buildMaps() {
    boundBySub = {};
    boundSubByCfg = {};
    binds.forEach(function (b) {
        boundBySub[String(b.oa_sub_id)] = b;
        // 一对一：同一企微租户只会有一个分部绑定
        boundSubByCfg[String(b.cfg_id)] = b;
    });
}

/**
 * 列表数据行：OA 分部缓存（未封存）为主，已绑定但缓存里取不到的分部（已封存/已删除）补一行，
 * 便于识别“绑定异常”并解绑。
 */
function subRows() {
    var rows = oaSubOptions.slice();
    binds.forEach(function (b) {
        var id = String(b.oa_sub_id);
        var exists = rows.some(function (r) { return String(r.id) === id; });
        if (!exists) {
            rows.push({
                id: b.oa_sub_id,
                name: b.oa_sub_name || "OA分部 ID " + b.oa_sub_id,
                canceled: "1",
                parent: b.oa_sub_parent || ""
            });
        }
    });
    return rows;
}

/* ------------------------------------------------------------------
 * 选择框：列表式 / 树状 两种显示模式（企微租户本身没有上下级关系，租户选择框只提供列表式）。
 * 拖动、列表/树状切换、树状渲染（默认收起分支）统一由 /oaones/js/picker.js 提供，
 * 这里仅保留薄封装，供本页片段继续调用。
 * ------------------------------------------------------------------ */
function subViewMode(key) { return OaonesPicker.mode(key); }
function renderSubTreeHtml(key, rows, opt) { return OaonesPicker.renderTree(key, rows, opt); }
function partialSelectedMap(ids, childGetter) { return OaonesPicker.partialSelected(ids, childGetter); }

// 选择框的显示模式切换/树节点展开收起后，回调对应渲染函数重绘
OaonesPicker.register("cfgPick", function () { renderCfgPickList(); });
OaonesPicker.register("subPick", function () { renderSubPickList(); });

function resetPendingCfgPicks() {
    // 行内“已选但未保存”的选择：数据重新加载（含批量绑定成功后）一律清空，避免残留
    if (typeof cfgPickValues !== "undefined") { cfgPickValues = {}; }
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

function renderSubFilterInput() {
    var fbIds = state.subFbIds || [];
    byId("subFbInput").value = fbIds.length === 1
        ? oaSubLabel(fbIds[0]) + "（ID " + fbIds[0] + "）"
        : (fbIds.length > 1 ? "已选 " + fbIds.length + " 个分部" : "");
    byId("subFbPicker").classList.toggle("has-value", fbIds.length > 0);
}

function render() {
    renderSubManage();
}

/**
 * 保存“OA 分部 → 企微租户”绑定：选择弹框里双击或点“确定”时立即调用（页面上不再有保存/批量绑定按钮）。
 *
 * <p>保存期间把该行选择框置灰（`.wx-picker-busy`），失败时回退行内显示到已保存的绑定，
 * 避免看起来已经绑定成功。</p>
 */
function saveSubBind(subId, cfgId) {
    subId = String(subId == null ? "" : subId);
    cfgId = String(cfgId == null ? "" : cfgId);
    if (!subId || !cfgId) { return; }
    if (state.saving) { return; }
    state.saving = true;
    var picker = document.querySelector("#subBody .wx-picker[data-sub='" + subId + "']");
    if (picker) { picker.classList.add("wx-picker-busy"); }
    Oaones.api("sub.save", { cfg_id: cfgId, oa_sub_id: subId }, function (res) {
        state.saving = false;
        if (picker) { picker.classList.remove("wx-picker-busy"); }
        delete cfgPickValues[subId];
        if (res.code === 0) {
            showToast("已绑定" + P_SHORT + "租户 " + cfgName(cfgId));
            loadData();
        } else {
            showToast("绑定失败：" + res.msg, 2);
            // 保存失败：行内回到已保存的绑定（没有绑定则显示为空），不残留看起来已绑定的值
            updateCfgPickerDom(subId);
        }
    });
}

/** 解绑分部：只解除分部与企微租户的绑定，不动已建立的部门绑定（自动绑定需重新绑分部后再用）。 */
function delBind(id, cfgId) {
    var bind = null;
    Object.keys(boundBySub).forEach(function (k) {
        if (bind) { return; }
        if (boundBySub[k] && String(boundBySub[k].id) === String(id)) { bind = boundBySub[k]; }
    });
    var subId = bind ? String(bind.oa_sub_id) : "";
    var subName = subId ? (oaSubLabel(subId) || (bind && bind.oa_sub_name) || "") : "";
    OaonesToast.confirm("确认解除分部" + (subName ? "「" + subName + "」" : "")
        + "与该" + P_SHORT + "租户的绑定？已建立的部门绑定（含手动绑定）保持不变，"
        + "但该分部下的 OA 部门要重新绑定分部后才能继续按编号/名称自动绑定。", function () {
        Oaones.api("sub.delete", { id: id, cfg_id: cfgId }, function (res) {
            if (res.code === 0) {
                showToast("分部绑定已解除，部门绑定保持不变");
                loadData();
            } else {
                showToast(res.msg, 2);
            }
        });
    }, { danger: true });
}

document.addEventListener("click", function (e) {
    if (!e.target || !e.target.closest) { return; }
    // 列表式 / 树状 切换与树节点展开收起由 /oaones/js/picker.js 统一处理
    var option = e.target.closest(".wx-pick-option");
    if (option) {
        if (option.hasAttribute("data-group")) { return; }
        if (subPickType) { toggleSubPick(option); }
        else { pickCfgOption(option); }
        return;
    }
    var picker = e.target.closest(".wx-picker");
    if (picker && !picker.classList.contains("disabled")) {
        showCfgPickModal(picker.getAttribute("data-sub"));
    }
});
// 关闭按钮 / 点遮罩 / 取消：都放弃弹框内本次选择，行内显示回退到打开前状态（不落库）
byId("cfgPickCloseBtn").addEventListener("click", cancelCfgPick);
byId("cfgPickMask").addEventListener("click", function (e) {
    if (e.target === this) { cancelCfgPick(); }
});
byId("cfgPickSearch").addEventListener("input", function () {
    if (cfgPickSubId) { renderCfgPickList(); }
});
byId("subPickCloseBtn").addEventListener("click", hideSubPick);
byId("subPickMask").addEventListener("click", function (e) {
    if (e.target === this) { hideSubPick(); }
});
byId("subPickSearch").addEventListener("input", function () {
    if (subPickType) { renderSubPickList(); }
});
// 双击选项：多选（分部筛选）也立即确定选择并退出弹框
byId("subPickList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group") || !subPickType) { return; }
    // 双击前已收到两次单击（选中→取消），这里确保该行处于选中态后再确定
    if (subPickSelected.indexOf(option.getAttribute("data-id")) < 0) { toggleSubPick(option); }
    applySubPick();
});
byId("subPickClearBtn").addEventListener("click", clearSubPickSelection);
byId("subPickCancelBtn").addEventListener("click", hideSubPick);
byId("subPickOkBtn").addEventListener("click", applySubPick);
byId("subFbPicker").addEventListener("click", function () { openSubPick("fb"); });
byId("subFbClear").addEventListener("click", function (e) {
    e.stopPropagation();
    clearSubFilter();
});
byId("oaFilterReset").addEventListener("click", resetSubFilters);
// 企微租户弹框：单击选中，双击该行即落库；底部“清空选择/取消/确定”同理（确定=保存绑定）
byId("cfgPickOkBtn").addEventListener("click", confirmCfgPick);
byId("cfgPickClearBtn").addEventListener("click", clearCfgPickSelection);
byId("cfgPickCancelBtn").addEventListener("click", cancelCfgPick);
byId("cfgPickList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group")) { return; }
    // 双击前已收到两次单击，这里确保该行处于选中态后再落库
    if (!option.classList.contains("disabled")) { pickCfgOption(option); }
    confirmCfgPick();
});
byId("refreshBtn").addEventListener("click", function () { loadCfgs(); });

OaonesPicker.syncSwitches();
loadCfgs();
</script>
</body>
</html>
