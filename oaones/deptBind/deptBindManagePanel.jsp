<%@ page pageEncoding="UTF-8" %>
<%
    /* 三个平台共用本片段：平台由引入方决定（request 属性 oaonesPBPlatform 优先，其次 platform 参数）；
       平台文案统一用 oaonesPBShort（企微 / 钉钉 / 飞书）与 oaonesPBFull（企业微信 / 钉钉 / 飞书）。 */
    String oaonesPBPlatform = (String) request.getAttribute("oaonesPBPlatform");
    if (oaonesPBPlatform == null || oaonesPBPlatform.trim().length() == 0) {
        oaonesPBPlatform = request.getParameter("platform");
    }
    if (oaonesPBPlatform == null) {
        oaonesPBPlatform = "";
    }
    oaonesPBPlatform = oaonesPBPlatform.trim().toLowerCase();
    boolean oaonesPBDd = "dingtalk".equals(oaonesPBPlatform);
    boolean oaonesPBFs = "feishu".equals(oaonesPBPlatform);
    String oaonesPBFull = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企业微信");
    String oaonesPBShort = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企微");
    // 租户标识 / 应用标识在各平台的叫法：企微 corpid + AgentId，钉钉 AppKey + AgentId，飞书 App ID + App ID
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
%>

<%-- 二级菜单：OA数据基准（部门绑定 - OA 部门侧） --%>
<%-- 页面说明正文：由页壳 sub-tabs 右侧的感叹号（#manageHelpTip）悬浮/点击弹出 --%>
<div class="help-source" id="manageHelpText">列表默认不展示，可点击“搜索”查看全部，或在分部/部门弹出框选择后自动搜索展示。分部/部门弹窗支持多选，选中分部时其下级分部会自动一并选中；分部/部门弹窗与<%= oaonesPBShort %>部门选择弹窗均支持“列表式 / 树状”两种显示模式。<span class="rule">选择即落库</span>：在“选择<%= oaonesPBShort %>部门”弹框里单击选中部门后，<span class="rule">双击该行（或点“确定”）立即写入数据库完成绑定</span>。一个 OA 部门可以映射同一租户的多个<%= oaonesPBShort %>部门；最早建立且仍有效的映射标记为<span class="rule">人员同步默认部门</span>，人员同步只取这一条。管理员解绑当前默认映射后，下一条有效映射自动成为默认；也可删除后重新绑定来调整默认映射。<span class="rule">智能匹配</span>可按<span class="rule">部门编号或名称</span>为尚未映射的 OA 部门自动匹配<%= oaonesPBShort %>部门。OA 部门到<%= oaonesPBShort %>部门也支持多个 OA 部门共享同一个<%= oaonesPBShort %>部门，已被其他 OA 部门绑定的<%= oaonesPBShort %>部门仍可在弹窗中选中。</div>
        <div id="managePanel">
            <div class="filter-bar">
                <span class="filter-label">OA 部门筛选：</span>
                <div class="filter-picker" id="oaFbPicker" title="点击选择分部（可多选）">
                    <input type="text" id="oaFbInput" class="filter-input" readonly placeholder="选择分部（可多选）">
                    <span class="filter-arrow">▾</span>
                    <button type="button" class="filter-clear" id="oaFbClear" title="清除分部筛选">×</button>
                </div>
                <div class="filter-picker" id="oaDeptPicker" title="点击选择部门（可多选）">
                    <input type="text" id="oaDeptInput" class="filter-input" readonly placeholder="选择部门（可多选）">
                    <span class="filter-arrow">▾</span>
                    <button type="button" class="filter-clear" id="oaDeptClear" title="清除部门筛选">×</button>
                </div>
                <button type="button" class="btn small" id="oaSearchBtn">搜索</button>
                <button type="button" class="btn small secondary" id="oaFilterReset">重置筛选</button>
                <button type="button" class="btn small" id="oaSmartMatchBtn" title="针对当前列表按部门编号或名称智能匹配<%= oaonesPBShort %>部门，匹配结果可直接自动绑定或只看匹配结果">智能匹配</button>
            </div>
            <div class="bind-stats" id="bindStats"></div>
            <div class="table-wrap">
                <table class="grid" id="manageTable">
                    <thead>
                    <tr>
                        <th style="width:36%;">OA 部门</th>
                        <th style="width:38%;"><%= oaonesPBShort %>部门</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="manageBody"></tbody>
                </table>
                <div class="empty" id="manageEmpty" style="display:none;">暂无 OA 部门</div>
            </div>
            <div class="pager" id="deptPager"></div>
        </div>

<div class="modal-mask" id="wxPickMask"></div>
<div class="modal wx-pick-modal" id="wxPickModal">
    <div class="modal-head">
        <span id="wxPickTitle">选择<%= oaonesPBShort %>部门</span>
        <button type="button" class="link" id="wxPickCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="wx-pick-tip" id="wxPickTip"></div>
        <div class="pick-view-bar">
            <input type="text" id="wxPickSearch" placeholder="输入<%= oaonesPBShort %>部门名称搜索" autocomplete="off">
            <div class="view-switch mini" id="wxPickViewSwitch" data-view-key="wxPick">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="wxPickList"></div>
        <div class="muted wx-pick-note">只展示快照中状态正常、且未标记“免绑定”的<%= oaonesPBShort %>部门；OA 到<%= oaonesPBShort %>为“多对一”（多个 OA 账户对应同一个<%= oaonesPBShort %>账户），同一<%= oaonesPBShort %>部门可被多个 OA 部门绑定，已被其他 OA 部门绑定的部门仍可选中，选项上会标注“已被 N 个 OA 部门绑定”。树状模式按<%= oaonesPBShort %>部门上下级展示（上级不可选时其下级按一级展示）。单击选中，<span class="rule">双击该行（或点“确定”）立即写入数据库完成绑定</span>，不需要再点保存。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="wxPickClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="wxPickCancelBtn">取消</button>
        <button type="button" class="btn" id="wxPickOkBtn">确定</button>
    </div>
</div>

<div class="modal-mask" id="oaPickMask"></div>
<div class="modal wx-pick-modal" id="oaPickModal">
    <div class="modal-head">
        <span id="oaPickTitle">选择分部</span>
        <button type="button" class="link" id="oaPickCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="oaPickSearch" placeholder="输入名称/编码/ID搜索" autocomplete="off">
            <div class="view-switch mini" id="oaPickViewSwitch" data-view-key="oaPick">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
            <button type="button" class="btn small secondary pick-all-btn" id="oaPickAllBtn" data-pick-all="oaPick"
                    title="全选/全不选当前列表（受搜索与前置条件过滤影响）">全选</button>
        </div>
        <div class="wx-pick-list" id="oaPickList"></div>
        <div class="muted wx-pick-note" id="oaPickNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="oaPickClearBtn">清空</button>
        <button type="button" class="btn secondary" id="oaPickCancelBtn">取消</button>
        <button type="button" class="btn" id="oaPickOkBtn">确定</button>
    </div>
</div>

<div class="modal-mask" id="smartMask"></div>
<div class="modal" id="smartModal" style="width:560px;">
    <div class="modal-head">
        <span>智能匹配结果</span>
        <button type="button" class="link" id="smartCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div id="smartMsg" style="font-size:13px;line-height:1.8;color:var(--oa-g-900);"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="smartShowBtn">仅看匹配结果（不落库）</button>
        <button type="button" class="btn" id="smartApplyBtn">立即自动绑定</button>
    </div>
</div>

<script>
"use strict";
/** 片段脚本比页壳脚本先执行（页壳的 byId 在页面末尾才定义），片段内必须自带一份，否则解析期就报 byId is not defined */
function byId(id) { return document.getElementById(id); }
/** 弹框内已选但尚未落库的企微部门（OA 部门ID → 企微部门ID__父部门ID），仅作行内预览 */
var wxPickValues = {};
/** 打开企微部门弹框前的预览值：取消/关闭时回退到它，避免残留“看起来已绑定”的选择 */
var wxPickBackup = "";
/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js）：翻页后重绘当前列表 */
var deptPager = OaonesTable.pager({
    key: "deptBind.manage",
    el: byId("deptPager"),
    onChange: function () { renderManage(); }
});

function wxName(wxId) {
    wxId = String(wxId == null ? "" : wxId);
    for (var i = 0; i < wxDepts.length; i++) {
        if (String(wxDepts[i].id) === wxId) { return wxDepts[i].name; }
    }
    return "";
}

/** 列表行内“父部门”：OA 侧展示位统一输出“名称(id:x)”，名称取不到时退回“OA 部门 + ID” */
function oaParentDeptText(parentId) {
    var pid = parentId === null || parentId === undefined ? "" : String(parentId).trim();
    if (pid === "") { return ""; }
    var name = oaDeptLabel(pid);
    return name
        ? "父部门：" + OaonesHrm.dept(pid, name) + "(id:" + esc(pid) + ")"
        : "父部门：" + OaonesHrm.dept(pid, "");
}

function wxCurrentValue(oaId) {
    oaId = String(oaId);
    var cur = boundByOa[oaId];
    var wxId = cur && cur.wx_dept_id !== null && cur.wx_dept_id !== undefined
            && String(cur.wx_dept_id) !== "" ? String(cur.wx_dept_id) : "";
    if (wxId) {
        var parentId = cur.wx_parent_dept_id === null || cur.wx_parent_dept_id === undefined
                ? "" : String(cur.wx_parent_dept_id);
        return wxId + "__" + parentId;
    }
    // 弹框内已选但尚未落库的预览值（双击/“确定”时才写库；重绘后按它回填行内显示）
    if (wxPickValues[oaId]) { return wxPickValues[oaId]; }
    var sug = smartSuggestions[oaId];
    if (sug) {
        return String(sug.wx_id) + "__" + String(sug.parentid == null ? "" : sug.parentid);
    }
    return "";
}

function wxCurrentLabel(oaId) {
    oaId = String(oaId);
    var cur = boundByOa[oaId];
    var wxId = cur && cur.wx_dept_id !== null && cur.wx_dept_id !== undefined
            ? String(cur.wx_dept_id) : "";
    if (wxId) { return wxName(wxId) || P_SHORT + "部门ID " + wxId; }
    if (wxPickValues[oaId]) {
        var picked = String(wxPickValues[oaId]).split("__")[0];
        return wxName(picked) || P_SHORT + "部门ID " + picked;
    }
    var sug = smartSuggestions[oaId];
    if (sug) { return wxName(sug.wx_id) || P_SHORT + "部门ID " + sug.wx_id; }
    return "";
}

function wxPickerHtml(oaId, disabled, placeholderText) {
    var value = wxCurrentValue(oaId);
    var label = wxCurrentLabel(oaId);
    var dis = disabled ? " disabled" : "";
    var placeholder = placeholderText
        || (disabled ? "异常记录不可绑定" : "请点击选择" + P_SHORT + "部门");
    return '<div class="wx-picker' + (disabled ? " disabled" : "") + '" data-oa="' + esc(oaId) + '">'
        + '<input type="text" class="wx-picker-text" readonly' + dis + ' value="' + esc(label) + '"'
        + ' placeholder="' + esc(placeholder) + '" title="点击弹出" + P_SHORT + "部门列表选择（双击可直接确定并绑定）">'
        + '<input type="hidden" class="wx-picker-value" value="' + esc(value) + '">'
        + '<span class="wx-picker-arrow">▾</span>'
        + '</div>';
}

function wxPickerOptions(oaId, keyword) {
    var rows = wxDepts.filter(function (d) {
        var id = String(d.id);
        // 已标记免绑定的企微部门不可再被选择（当前 OA 已绑定的旧数据除外，避免显示异常）
        // 免绑定取自 uf_oaones_dep_tmp.no_bind = 0（正常不写值，返回空串）
        if (String(d.no_bind) === "0" && !boundByWx[id]) { return false; }
        // OA 部门 → 企微部门为“多对一”：同一企微部门可被多个 OA 部门绑定，
        // 因此已被其他 OA 部门绑定的企微部门仍作为候选，只在选项上打“已被 N 个 OA 部门绑定”标记。
        return true;
    });
    var k = (keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (d) {
        var id = String(d.id);
        var text = (String(d.name == null ? "" : d.name) + " " + id).toLowerCase();
        return text.indexOf(k) >= 0;
    });
}

function showWxPickModal(oaId) {
    var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + oaId + "']");
    if (!picker || picker.classList.contains("disabled")) { return; }
    wxPickOaId = oaId;
    wxPickBackup = wxPickValues[String(oaId)] || "";
    wxPickValue = wxCurrentValue(oaId);
    var parts = wxPickValue ? wxPickValue.split("__") : [];
    wxPickId = parts[0] || "";
    wxPickLabel = wxCurrentLabel(oaId);
    var deptName = "";
    oaRows().forEach(function (d) {
        if (String(d.id) === String(oaId)) { deptName = d.name || ""; }
    });
    byId("wxPickTip").textContent = deptName
        ? "为 OA 部门“" + deptName + "”选择" + P_SHORT + "部门"
        : "为 OA 部门（ID " + oaId + "）选择" + P_SHORT + "部门";
    // 直接把该行左侧的 OA 部门名称带入搜索条件：列表式/树状都按它过滤
    // （树状自动展开命中路径），便于优先定位同名企微部门；用户可随时改写或清空。
    // 但行内已带回“尚未保存”的企微部门（智能匹配结果或刚点选的值）时清空关键字，
    // 避免默认关键字把它过滤掉、看不到该行的高亮项（与 OA 人员侧选择弹框口径一致）。
    var staged = !boundByOa[String(oaId)] && !!wxPickValue;
    byId("wxPickSearch").value = staged ? "" : (deptName || "");
    // 树状默认收起分支：每次打开弹框重置展开状态
    OaonesPicker.resetTree("wxPick");
    renderWxPickList();
    byId("wxPickMask").style.display = "block";
    byId("wxPickModal").style.display = "block";
    // 预填时全选关键字：直接输入即可整体替换，避免在既有名称上追加
    byId("wxPickSearch").focus();
    if (byId("wxPickSearch").value) { byId("wxPickSearch").select(); }
}

function hideWxPickModal() {
    byId("wxPickMask").style.display = "none";
    var modal = byId("wxPickModal");
    if (modal) { modal.style.display = "none"; }
    wxPickOaId = "";
}

function renderWxPickList() {
    var listEl = byId("wxPickList");
    var mode = deptViewMode("wxPick");
    var keyword = byId("wxPickSearch").value;
    // 树状模式取全部可选部门后按层级渲染，关键字过滤交由树渲染处理（保留上级路径）
    var rows = wxPickerOptions(wxPickOaId, mode === "tree" ? "" : keyword);
    var html;
    if (mode === "tree") {
        html = renderDeptTreeHtml("wxPick", rows, {
            parentOf: function (d) { return d.parentid; },
            textOf: function (d) { return String(d.name == null ? "" : d.name) + " " + String(d.id); },
            keyword: keyword,
            emptyText: "没有匹配的" + P_SHORT + "部门",
            optionHtml: function (d) { return wxPickOptionHtml(d); }
        });
    } else {
        html = "";
        if (!rows.length) {
            html = "<div class='wx-pick-empty'>没有匹配的" + P_SHORT + "部门</div>";
        } else {
            html = "<div class='wx-pick-head'><span>部门ID</span><span>部门名称</span></div>";
        }
        rows.forEach(function (d) { html += wxPickOptionHtml(d); });
    }
    listEl.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
    listEl.innerHTML = html;
}

function wxPickOptionHtml(d) {
    var id = String(d.id);
    var parentId = d.parentid === undefined || d.parentid === null ? "" : String(d.parentid);
    var active = wxPickId === id ? " active" : "";
    return "<button type='button' class='wx-pick-option" + active + "'"
        + " data-id='" + esc(id) + "'"
        + " data-value='" + esc(id + "__" + parentId) + "'"
        + " title='" + esc(d.name) + "（ID " + esc(id) + "）'>"
        + "<span class='wx-pick-opt-id'>" + esc(id) + "</span>"
        + "<span class='wx-pick-opt-name'>" + esc(d.name) + wxBoundTag(wxPickOaId, id) + "</span>"
        + "</button>";
}

/**
 * 多对一标记：该企微部门已被“其他 OA 部门”绑定时给出数量与部门明细
 * （当前正在编辑的这条 OA 部门绑定不算，避免与自身冲突提示）。
 */
function wxBoundTag(oaId, wxId) {
    var list = boundListByWx[String(wxId)] || [];
    var others = [];
    list.forEach(function (b) {
        if (String(b.oa_dept_id) !== String(oaId)) { others.push(b); }
    });
    if (!others.length) { return ""; }
    var names = others.map(function (b) {
        var name = oaDeptLabel(b.oa_dept_id);
        return (name || "OA部门") + "（ID " + b.oa_dept_id + "）";
    });
    var tip = "已被其他 OA 部门绑定：" + names.join("、")
        + "；同一" + P_SHORT + "部门可被多个 OA 部门绑定（一对多），确认后点击即可为当前 OA 部门也建立绑定";
    return "<span class='tag bound' title='" + esc(tip) + "'>已被 " + others.length + " 个 OA 部门绑定</span>";
}

/** 把行内选择框刷新为“已保存绑定 / 弹框内已选”的值（保存失败与取消时回退也走这里） */
function updateWxPickerDom(oaId) {
    var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + oaId + "']");
    if (!picker) { return; }
    var value = wxCurrentValue(oaId);
    var hidden = picker.querySelector(".wx-picker-value");
    var text = picker.querySelector(".wx-picker-text");
    if (hidden) { hidden.value = value; }
    if (text) { text.value = value ? wxCurrentLabel(oaId) : ""; }
}

/** 单击选项：只就地选中（弹框内高亮 + 行内预览），落库由双击 / “确定”触发 */
function pickWxOption(btn) {
    if (!wxPickOaId) { return; }
    wxPickId = btn.getAttribute("data-id");
    wxPickValue = btn.getAttribute("data-value");
    var nameEl = btn.querySelector(".wx-pick-opt-name");
    wxPickLabel = nameEl ? nameEl.textContent : "";
    var opts = byId("wxPickList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        opts[i].classList.remove("active");
    }
    btn.classList.add("active");
    wxPickValues[String(wxPickOaId)] = wxPickValue;
    updateWxPickerDom(String(wxPickOaId));
}

/** 双击 / “确定”即落库：页面上不再有“批量绑定/保存”按钮 */
function confirmWxPick() {
    if (!wxPickOaId) { return; }
    if (!wxPickId) {
        showToast("请先选择" + P_SHORT + "部门（单击选中后双击或点击“确定”）", 1);
        return;
    }
    var pickedOaId = String(wxPickOaId);
    var value = String(wxPickValue || "");
    wxPickValues[pickedOaId] = value;
    smartSuggestions[pickedOaId] = {
        wx_id: wxPickId,
        parentid: value.indexOf("__") >= 0 ? value.split("__")[1] || "" : ""
    };
    hideWxPickModal();
    saveDeptBind(pickedOaId, value);
}

/** 取消/关闭弹框：放弃弹框内的本次选择，行内显示回到打开弹框前的状态 */
function cancelWxPick() {
    if (wxPickOaId) {
        var oaId = String(wxPickOaId);
        if (wxPickBackup) { wxPickValues[oaId] = wxPickBackup; } else { delete wxPickValues[oaId]; }
        updateWxPickerDom(oaId);
    }
    hideWxPickModal();
}

/** 清空选择：只清掉弹框内/行内未落库的预览选择，不影响已保存的绑定 */
function clearWxPickSelection() {
    if (!wxPickOaId) { return; }
    var oaId = String(wxPickOaId);
    wxPickId = "";
    wxPickValue = "";
    wxPickLabel = "";
    delete wxPickValues[oaId];
    updateWxPickerDom(oaId);
    renderWxPickList();
}

function filterOaRows() {
    var fbIds = (state.oaFbIds || []).map(String);
    var deptIds = (state.oaDeptIds || []).map(String);
    return oaRows().filter(function (r) {
        var subOk = !fbIds.length || fbIds.indexOf(String(r.subcompanyid1 == null ? "" : r.subcompanyid1)) >= 0;
        var deptOk = !deptIds.length || deptIds.indexOf(String(r.id)) >= 0;
        return subOk && deptOk;
    });
}

function clearOaFbFilter() {
    state.oaFbIds = [];
    state.oaDeptIds = [];
    state.oaMatchOnlyIds = [];
    smartSuggestions = {};
    renderManage();
}

function clearOaDeptFilter() {
    state.oaDeptIds = [];
    state.oaMatchOnlyIds = [];
    smartSuggestions = {};
    renderManage();
}

function resetOaFilters() {
    state.oaFbIds = [];
    state.oaDeptIds = [];
    state.oaMatchOnlyIds = [];
    smartSuggestions = {};
    renderManage();
}

function doOaSearch() {
    state.oaListVisible = true;
    state.oaMatchOnlyIds = [];
    smartSuggestions = {};
    renderManage();
}

/**
 * 前置条件过滤：先选分部，再选部门——部门候选只保留所选分部（含下级）下的部门。
 * 由 OaonesPicker 统一套用，列表式与树状两种模式都生效。
 */
function oaPickPrecondition() {
    if (oaPickType !== "dept") {
        return { tip: "支持多选，选中分部时其下级分部自动一并选中" };
    }
    var fbIds = (state.oaFbIds || []).map(String);
    if (!fbIds.length) {
        return { tip: "未选分部时显示全部部门；先选择分部后，这里只显示该分部（含下级）下的部门" };
    }
    return {
        tip: "仅显示所选 " + fbIds.length + " 个分部（含下级）下的部门",
        filter: function (d) {
            return fbIds.indexOf(String(d.subcompanyid1 == null ? "" : d.subcompanyid1)) >= 0;
        }
    };
}
OaonesPicker.setPrecondition("oaPick", oaPickPrecondition);

function oaPickRows(keyword) {
    var rows = oaPickType === "fb" ? oaSubOptions.slice() : oaDeptOptions.slice();
    var k = (keyword == null ? byId("oaPickSearch").value : keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (d) {
        var text = [d.id, d.code, d.name, d.fullname].map(function (v) {
            return String(v == null ? "" : v);
        }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
}

function oaSubChildMap() {
    var map = {};
    oaSubOptions.forEach(function (s) {
        var p = String(s.parent == null ? "" : s.parent);
        if (!map[p]) { map[p] = []; }
        map[p].push(String(s.id));
    });
    return map;
}

function oaFbDescendantIds(id) {
    var map = oaSubChildMap();
    var out = [];
    var stack = [String(id)];
    var seen = {};
    while (stack.length) {
        var cur = stack.pop();
        if (seen[cur]) { continue; }
        seen[cur] = true;
        out.push(cur);
        (map[cur] || []).forEach(function (c) { stack.push(c); });
    }
    return out;
}

function expandFbIds(ids) {
    var map = oaSubChildMap();
    var out = [];
    var set = {};
    var stack = [];
    (ids || []).forEach(function (id) { stack.push(String(id)); });
    while (stack.length) {
        var cur = stack.pop();
        if (set[cur]) { continue; }
        set[cur] = true;
        out.push(cur);
        (map[cur] || []).forEach(function (c) { stack.push(c); });
    }
    return out;
}

function renderOaPickList() {
    var isSub = oaPickType === "fb";
    var title = isSub ? "选择分部" : "选择部门";
    byId("oaPickTitle").textContent = oaPickSelected.length
        ? title + "（已选 " + oaPickSelected.length + " 个）"
        : title;
    syncOaPickAllState();
    var listEl = byId("oaPickList");
    var mode = deptViewMode("oaPick");
    var empty = isSub ? "没有匹配的分部" : "没有匹配的部门";
    // 前置条件过滤（先选分部再选部门）由 OaonesPicker 统一套用，两种显示模式一致
    var rows = OaonesPicker.applyPrecondition("oaPick", oaPickRows(mode === "tree" ? "" : null));
    var partialMap = isSub ? partialSelectedMap(oaPickSelected, oaSubChildMap) : null;
    var html;
    if (mode === "tree") {
        html = renderDeptTreeHtml("oaPick", rows, {
            parentOf: function (d) { return d.parent; },
            textOf: function (d) { return [d.id, d.code, d.name, d.fullname].join(" "); },
            keyword: byId("oaPickSearch").value,
            emptyText: empty,
            optionHtml: function (d) { return oaPickOptionHtml(d, isSub, partialMap); }
        });
    } else {
        html = "";
        if (!rows.length) {
            html = "<div class='wx-pick-empty'>" + empty + "</div>";
        } else {
            html = "<div class='wx-pick-head pick-cols3'><span>ID</span><span>编码</span><span>简称</span></div>";
        }
        rows.forEach(function (d) { html += oaPickOptionHtml(d, isSub, partialMap); });
    }
    listEl.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
    listEl.innerHTML = html;
}

/** 当前可全选的候选行 id（已套用搜索关键字与“先选分部再选部门”前置条件过滤）。 */
function oaPickAllIds() {
    var rows = OaonesPicker.applyPrecondition("oaPick", oaPickRows(null));
    return rows.map(function (d) { return String(d.id); });
}

/** “全选”按钮状态：当前列表候选是否已全部选中，返回是否全选。 */
function syncOaPickAllState() {
    var ids = oaPickAllIds();
    var allOn = ids.length > 0 && ids.every(function (id) { return oaPickSelected.indexOf(id) >= 0; });
    OaonesPicker.syncSelectAll("oaPick", allOn);
    return allOn;
}

/**
 * 全选/全不选：把当前列表里的分部/部门一次性选中或取消。
 * 分部按本页口径连带全部下级分部；部门只选自身。已全部选中时按钮变“全不选”。
 */
OaonesPicker.setSelectAll("oaPick", function () {
    var isSub = oaPickType === "fb";
    var ids = oaPickAllIds();
    var on = syncOaPickAllState();
    var next;
    if (on) {
        var drop = {};
        ids.forEach(function (id) {
            (isSub ? oaFbDescendantIds(id) : [id]).forEach(function (v) { drop[v] = true; });
        });
        next = oaPickSelected.filter(function (v) { return !drop[v]; });
    } else {
        next = isSub ? expandFbIds(oaPickSelected.concat(ids)) : oaPickSelected.concat(ids);
    }
    var seen = {};
    oaPickSelected = next.filter(function (v) {
        v = String(v);
        if (seen[v]) { return false; }
        seen[v] = true;
        return true;
    });
    renderOaPickList();
    return syncOaPickAllState();
});

function oaPickOptionHtml(d, isSub, partialMap) {
    var id = String(d.id);
    var active = oaPickSelected.indexOf(id) >= 0;
    var partial = !active && !!partialMap && !!partialMap[id];
    var cls = "wx-pick-option pick-cols3" + (active ? " active" : (partial ? " partial" : ""));
    var tip = (active ? "点击取消选择" : "点击选择")
        + (isSub ? "；选中分部会自动选中其全部下级分部" : "")
        + "｜" + (d.fullname || d.name || "")
        + (d.code ? "（编码 " + d.code + "）" : "");
    return "<button type='button' class='" + cls + "'"
        + " data-id='" + esc(id) + "'"
        + " title='" + esc(tip) + "'>"
        + "<span>" + esc(id) + "</span>"
        + "<span>" + esc(d.code || "-") + "</span>"
        + "<span>" + esc(d.name || "-") + "</span>"
        + "</button>";
}

function openOaPick(type) {
    oaPickType = type;
    oaPickSelected = (type === "fb" ? state.oaFbIds : state.oaDeptIds).slice();
    byId("oaPickTitle").textContent = type === "fb" ? "选择分部" : "选择部门";
    byId("oaPickSearch").value = "";
    // 前置条件说明随当前选择实时生成
    OaonesPicker.preconditionTip("oaPick", "oaPickNote");
    // 树状默认收起分支：每次打开弹框重置展开状态
    OaonesPicker.resetTree("oaPick");
    renderOaPickList();
    byId("oaPickMask").style.display = "block";
    byId("oaPickModal").style.display = "block";
    byId("oaPickSearch").focus();
}

function hideOaPick() {
    byId("oaPickMask").style.display = "none";
    byId("oaPickModal").style.display = "none";
    oaPickType = "";
    oaPickSelected = [];
}

function toggleOaPick(btn) {
    var id = btn.getAttribute("data-id");
    var isSub = oaPickType === "fb";
    var idx = oaPickSelected.indexOf(id);
    if (idx >= 0) {
        if (isSub) {
            var removes = oaFbDescendantIds(id);
            oaPickSelected = oaPickSelected.filter(function (v) { return removes.indexOf(v) < 0; });
        } else {
            oaPickSelected.splice(idx, 1);
        }
    } else {
        var next = oaPickSelected.concat([id]);
        oaPickSelected = isSub ? expandFbIds(next) : next;
    }
    // 只就地刷新选中态，不重建列表：保证双击时命中的是同一个 DOM 节点
    refreshOaPickDom();
}

/** 就地刷新选择框选中态/部分选中态与标题，不重建列表（避免打断双击）。 */
function refreshOaPickDom() {
    var isSub = oaPickType === "fb";
    var partialMap = isSub ? partialSelectedMap(oaPickSelected, oaSubChildMap) : null;
    var opts = byId("oaPickList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        var id = String(opts[i].getAttribute("data-id"));
        var active = oaPickSelected.indexOf(id) >= 0;
        var partial = !active && !!partialMap && !!partialMap[id];
        opts[i].classList.toggle("active", active);
        opts[i].classList.toggle("partial", partial);
    }
    var title = isSub ? "选择分部" : "选择部门";
    byId("oaPickTitle").textContent = oaPickSelected.length
        ? title + "（已选 " + oaPickSelected.length + " 个）"
        : title;
    // 单行勾选/取消后同步“全选”按钮状态
    syncOaPickAllState();
}

function applyOaPick() {
    if (oaPickType === "fb") {
        state.oaFbIds = expandFbIds(oaPickSelected);
        // 保留仍属于所选分部（含下级）范围的部门筛选，避免出现空交集
        var expanded = state.oaFbIds.map(String);
        var keepDept = [];
        (state.oaDeptIds || []).forEach(function (id) {
            var dept = null;
            for (var i = 0; i < oaDeptOptions.length; i++) {
                if (String(oaDeptOptions[i].id) === String(id)) { dept = oaDeptOptions[i]; break; }
            }
            if (dept && expanded.indexOf(String(dept.subcompanyid1 == null ? "" : dept.subcompanyid1)) >= 0) {
                keepDept.push(String(id));
            }
        });
        state.oaDeptIds = keepDept;
    } else {
        state.oaDeptIds = oaPickSelected.slice();
    }
    hideOaPick();
    doOaSearch();
}

function clearOaPickSelection() {
    oaPickSelected = [];
    renderOaPickList();
}

function oaDeptCode(oaId) {
    oaId = String(oaId == null ? "" : oaId);
    if (!oaId) { return ""; }
    for (var i = 0; i < oaDeptOptions.length; i++) {
        if (String(oaDeptOptions[i].id) === oaId) {
            return String(oaDeptOptions[i].code == null ? "" : oaDeptOptions[i].code).trim();
        }
    }
    return "";
}

function oaDeptMatchName(row) {
    var full = "";
    for (var i = 0; i < oaDeptOptions.length; i++) {
        if (String(oaDeptOptions[i].id) === String(row.id)) {
            full = oaDeptOptions[i].fullname;
            break;
        }
    }
    return String(full == null ? "" : full).trim() || String(row.name == null ? "" : row.name).trim();
}

function wxDeptCode(w) {
    // 企微部门快照本身没有独立编码字段；若后续快照补充 code 则优先使用，
    // 否则以企微部门 ID 作为企微侧编码参与匹配。
    var c = w && w.code === undefined ? "" : String(w.code == null ? "" : w.code).trim();
    return c || String(w == null ? "" : w.id);
}

function findSmartWx(row, rowsWithResult, usedWx, byCode) {
    var oaCode = oaDeptCode(row.id);
    var oaName = oaDeptMatchName(row);
    for (var i = 0; i < wxDepts.length; i++) {
        var w = wxDepts[i];
        var wxId = String(w.id);
        // 多对一：同一企微部门可被多个 OA 部门绑定，已被其他 OA 部门绑定的部门同样可作为建议；
        // 免绑定的部门不参与，同一批建议中不重复推荐同一个企微部门。
        if (usedWx[wxId] || String(w.no_bind) === "0") { continue; }
        if (byCode) {
            if (oaCode.length > 0 && wxDeptCode(w) === oaCode) { return w; }
        } else {
            var wxName = String(w.name == null ? "" : w.name).trim();
            if (oaName.length > 0 && wxName === oaName) { return w; }
        }
    }
    return null;
}

function computeSmartMatches(rows) {
    var result = {};
    var usedWx = {};
    var pending = [];
    rows.forEach(function (r) {
        var oaId = String(r.id);
        // 已绑定、已封存的行不参与自动匹配
        if (boundByOa[oaId] || String(r.canceled) === "1") { return; }
        pending.push(r);
    });
    // 规则1：两边编码一致
    pending.forEach(function (r) {
        var oaId = String(r.id);
        if (result[oaId]) { return; }
        var w = findSmartWx(r, result, usedWx, true);
        if (w) {
            result[oaId] = { oa_id: oaId, wx_id: String(w.id), parentid: String(w.parentid == null ? "" : w.parentid), mode: "code" };
            usedWx[String(w.id)] = true;
        }
    });
    // 规则2：不满足编码匹配时，部门名称完全相同
    pending.forEach(function (r) {
        var oaId = String(r.id);
        if (result[oaId]) { return; }
        var w = findSmartWx(r, result, usedWx, false);
        if (w) {
            result[oaId] = { oa_id: oaId, wx_id: String(w.id), parentid: String(w.parentid == null ? "" : w.parentid), mode: "name" };
            usedWx[String(w.id)] = true;
        }
    });
    var arr = [];
    Object.keys(result).forEach(function (k) { arr.push(result[k]); });
    return { matches: arr };
}

function clearSmartHighlights() {
    var trs = document.querySelectorAll("#manageBody tr.smart-highlight");
    for (var i = 0; i < trs.length; i++) { trs[i].classList.remove("smart-highlight"); }
}

/**
 * 智能匹配命中行的底色（.smart-highlight，见 css/style.css）：
 * 由 renderManage 按 smartSuggestions 直接加在 <tr> 上，**保留到底**，
 * 直到重新搜索/重置筛选/重新加载数据（匹配结果被清空）为止，便于逐行核对后再决定是否自动绑定。
 */
function highlightSmartRows(matches) {
    (matches || []).forEach(function (m) {
        var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + m.oa_id + "']");
        if (!picker) { return; }
        var tr = picker.closest ? picker.closest("tr") : null;
        if (tr) { tr.classList.add("smart-highlight"); }
    });
}

function hideSmartModal() {
    byId("smartMask").style.display = "none";
    byId("smartModal").style.display = "none";
}

function showSmartResult(matches) {
    smartMatchData = matches || [];
    byId("smartMsg").textContent = "智能匹配完成：已为 " + matches.length
        + " 个未绑定 OA 部门生成智能匹配" + P_SHORT + "部门"
        + "。可点“立即自动绑定”直接写入这些绑定关系（自动绑定），"
        + "或点“仅看匹配结果”只筛选出这些部门，再逐行选择" + P_SHORT + "部门（手动绑定，即时落库）。"
        + "匹配到的行在列表中会带底色标记（保留到重新搜索/重载数据），便于逐行对照核对。";
    byId("smartMask").style.display = "block";
    byId("smartModal").style.display = "block";
}

/** 仅看匹配结果（不落库）：列表只显示本次匹配到的 OA 部门，逐行手动选择时即时保存 */
function smartShowOnly() {
    var ids = smartMatchData.map(function (m) { return String(m.oa_id); });
    hideSmartModal();
    clearSmartHighlights();
    state.oaListVisible = true;
    state.oaMatchOnlyIds = ids;
    renderManage();
    highlightSmartRows(smartMatchData);
}

/** 立即自动绑定：把本次匹配结果一次性写入 */
function smartApplyNow() {
    if (!smartMatchData.length) { hideSmartModal(); return; }
    hideSmartModal();
    clearSmartHighlights();
    state.oaMatchOnlyIds = [];
    state.oaListVisible = true;
    doAutoBind(smartMatchData);
}

/** 关闭匹配结果弹框：不改列表筛选，匹配到的行保留底色便于对照 */
function smartCloseOnly() {
    hideSmartModal();
    state.oaListVisible = true;
    highlightSmartRows(smartMatchData);
}

function doSmartMatch() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    if (!state.oaListVisible) {
        showToast("请先点击“搜索”展示当前 OA 部门列表，再执行智能匹配", 1);
        return;
    }
    var rows = filterOaRows();
    if (!rows.length) { showToast("当前列表为空，没有可匹配的 OA 部门", 1); return; }
    var smart = computeSmartMatches(rows);
    var matches = smart.matches;
    if (!matches.length) {
        showToast("未找到可自动匹配的" + P_SHORT + "部门。规则：两边编码一致，或部门名称完全相同；"
            + "免绑定的" + P_SHORT + "部门不参与，已被其他 OA 部门绑定的" + P_SHORT + "部门仍会参与（多对一）。", 1, 8000);
        return;
    }
    clearSmartHighlights();
    state.oaListVisible = true;
    state.oaMatchOnlyIds = [];
    smartSuggestions = {};
    matches.forEach(function (m) {
        smartSuggestions[String(m.oa_id)] = {
            wx_id: m.wx_id,
            parentid: m.parentid
        };
    });
    renderManage();
    showSmartResult(matches);
}

function renderBindStats(rows) {
    rows = rows || oaRows();
    var total = rows.length;
    var bound = 0;
    rows.forEach(function (r) {
        if (boundByOa[String(r.id)]) { bound++; }
    });
    var wxUnbound = 0;
    wxDepts.forEach(function (d) {
        if (!boundByWx[String(d.id)] && String(d.no_bind) !== "0") { wxUnbound++; }
    });
    byId("bindStats").innerHTML = ""
        + "<span class='bind-stat'><span>部门共计</span><b>" + total + "</b> 个</span>"
        + "<span class='bind-stat'><span>已绑定部门</span><b>" + bound + "</b> 个</span>"
        + "<span class='bind-stat'><span>" + P_SHORT + "待绑定部门</span><b>" + wxUnbound + "</b> 个</span>";
}

function issueText(bind) {
    if (!bind) { return ""; }
    if (String(bind.oa_canceled) === "1") {
        return "部门绑定异常（OA 部门已封存）";
    }
    if (bind.wx_tmp_status === null || bind.wx_tmp_status === undefined
            || bind.wx_tmp_status === "" || String(bind.wx_tmp_status) !== "0") {
        return "部门绑定异常（" + P_SHORT + "部门不在快照或已删除/已封存）";
    }
    return "";
}

function renderManage() {
    renderOaFilterInputs();
    if (!state.oaListVisible) {
        byId("bindStats").style.display = "none";
        byId("manageTable").style.display = "none";
        byId("manageBody").innerHTML = "";
        deptPager.hide();
        byId("manageEmpty").style.display = "block";
        byId("manageEmpty").textContent = state.cfgId
            ? "请选择分部/部门或点击“搜索”后查看 OA 部门列表"
            : "请先选择" + P_FULL + "租户";
        return;
    }
    byId("bindStats").style.display = "";
    byId("manageTable").style.display = "";
    var rows = filterOaRows();
    var matchIds = (state.oaMatchOnlyIds || []).map(String);
    if (matchIds.length) {
        rows = rows.filter(function (r) { return matchIds.indexOf(String(r.id)) >= 0; });
    }
    deptPager.resetIfChanged(deptFilterSignature());
    var pageRows = deptPager.apply(rows);
    renderBindStats(rows);
    var body = "";
    var hasTmp = wxDepts.length > 0;
    pageRows.forEach(function (d) {
        var oaId = String(d.id);
        var bind = boundByOa[oaId];
        var issue = issueText(bind);
        if (!issue && String(d.canceled) === "1") {
            issue = "部门绑定异常（OA 部门已删除/已封存）";
        }
        var disabled = issue !== "";
        var statusHtml;
        if (!bind) {
            statusHtml = "<span class='tag unbound'>未绑定</span>";
        } else if (issue) {
            statusHtml = "<span class='tag fail'>异常</span>";
        } else {
            statusHtml = "<span class='tag bound'>已绑定</span>";
        }
        var note = "";
        var oaBinds = binds.filter(function (b) { return String(b.oa_dept_id) === oaId; });
        if (oaBinds.length) {
            oaBinds.forEach(function (b) {
                note += "<div class='user-mini'>当前：" + esc(wxName(b.wx_dept_id) || "ID " + b.wx_dept_id)
                    + "（" + esc(b.wx_dept_id) + "）"
                    + (String(b.sync_default) === "1"
                        ? " <span class='tag bound'>人员同步默认部门</span>" : "")
                    + "</div>";
            });
        }
        if (issue) {
            note += "<div class='row-note'>" + esc(issue) + "</div>";
        } else {
            // 智能匹配 / 刚点选带回的“待绑定”值：双击该行或点“确定”才会写入数据库
            var sug = smartSuggestions[oaId];
            if (sug) {
                note += "<div class='row-note'>待绑定（未保存）："
                    + esc(wxName(sug.wx_id) || P_SHORT + "部门ID " + sug.wx_id)
                    + "（点该行选择框后双击该部门或点“确定”即完成绑定）</div>";
            }
            if (!bind && !hasTmp && !sug) {
                note += "<div class='row-note'>快照为空，请先点击“更新" + P_SHORT + "部门快照”</div>";
            }
        }
        var ops = "";
        oaBinds.forEach(function (b) {
            ops += "<button class='link danger unbind-btn' data-id='" + esc(b.id) + "'>解绑"
                + (String(b.sync_default) === "1" ? "默认" : "") + "</button> ";
        });
        // 分部ID/名称：OA 部门缓存优先，绑定记录兜底；名称缺失时按缓存里的分部名补全
        var subId = oaDeptSubId(d, bind);
        var subName = d.subcompany_name || (bind && bind.oa_subcompany_name) || oaSubLabel(subId) || "";
        var subStatusHtml = subId === ""
            ? "<span class='tag fail'>未归属分部</span>"
            : "";
        // 智能匹配命中的行加底色（保留到重新搜索/重载）：行内同时有“待绑定（未保存）”说明
        body += "<tr" + (smartSuggestions[oaId] ? " class='smart-highlight'" : "") + ">"
            + "<td><div><b>" + OaonesHrm.dept(oaId, d.name) + "</b>（ID " + esc(oaId) + "）</div>"
            + "<div class='user-mini'>分部：" + (subName ? OaonesHrm.sub(subId, subName) : "-")
            + " " + subStatusHtml
            + (d.parent && String(d.parent) !== "0"
                ? "　" + oaParentDeptText(d.parent) : "") + "</div></td>"
            + "<td><div class='pick-col'>"
            + wxPickerHtml(oaId, disabled) + "</div>"
            + note + "</td>"
            + "<td>" + statusHtml + "</td>"
            + "<td>" + ops + "</td>"
            + "</tr>";
    });
    byId("manageBody").innerHTML = body;
    deptPager.show();
    deptPager.render();
    OaonesTable.resizable(byId("manageTable"), { key: "deptBind.manageTable" });
    byId("manageEmpty").style.display = pageRows.length ? "none" : "block";
    byId("manageEmpty").textContent = (state.oaFbIds && state.oaFbIds.length) || (state.oaDeptIds && state.oaDeptIds.length)
        ? "没有匹配的 OA 部门" : "暂无 OA 部门";
}

/** 筛选条件签名：任一条件变化都要回到第 1 页（分部/部门、仅显示匹配、租户） */
function deptFilterSignature() {
    return [state.cfgId, (state.oaFbIds || []).join(","),
        (state.oaDeptIds || []).join(","), (state.oaMatchOnlyIds || []).join(",")].join("|");
}

</script>
