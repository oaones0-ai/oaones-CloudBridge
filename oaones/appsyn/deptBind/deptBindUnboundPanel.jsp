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

<%-- 二级菜单：平台数据基准（部门绑定 - 平台部门侧） --%>
<%-- 页面说明正文：由页壳 sub-tabs 右侧的感叹号（#wxUnboundHelpTip）悬浮/点击弹出 --%>
<div class="help-source" id="wxUnboundHelpText">以下为<%= oaonesPBShort %>部门快照中的全部正常部门，可按“已绑定/未绑定/免绑定”勾选筛选，也可在筛选栏点开“选择<%= oaonesPBShort %>部门”弹框按<%= oaonesPBShort %>部门筛选（可多选、选中部门会自动带上其全部下级部门，筛选按<%= oaonesPBShort %>部门本身匹配：只看所选部门及其下级）。<span class="rule">已绑定</span>行按“一行一条绑定关系”列出该<%= oaonesPBShort %>部门绑定的全部 OA 部门（多对一：同一<%= oaonesPBShort %>部门可被多个 OA 部门绑定），每行行尾直接带该条绑定的“解绑”按钮，点击即只解除这一条绑定，解绑后可重新绑定。<span class="rule">未绑定</span>行在“OA 部门”列的选择框里选部门：单击选中后，<span class="rule">双击该行（或点“确定”）立即写入数据库完成绑定</span>，页面上不需要再点保存。<span class="rule">免绑定</span>行不参与绑定选择，可取消免绑定后再次绑定。勾选多行可“批量免绑定”（行首复选框只对“未绑定”部门可用，这也是本页唯一需要勾选的操作），表头复选框只全选当前页，要一次选中当前筛选条件下的全部部门（含未翻到的页）请勾选表头上方（与表头复选框对齐）的“跨页全选”。选择 OA 部门的弹窗与“选择<%= oaonesPBShort %>部门”筛选弹框均支持“列表式 / 树状”两种显示模式。</div>
        <div id="wxUnboundPanel" style="display:none;">
            <div class="filter-bar" style="justify-content:space-between;">
                <div style="display:flex; gap:10px; align-items:center; flex:1; flex-wrap:wrap;">
                    <span class="filter-label"><%= oaonesPBShort %>部门筛选：</span>
                    <div class="filter-picker" id="wxDeptFilterPicker"
                         title="点击选择<%= oaonesPBShort %>部门（可多选；选中部门会自动选中其全部下级部门，筛选按<%= oaonesPBShort %>部门本身匹配：只看所选部门及其下级）">
                        <input type="text" id="wxDeptFilterInput" class="filter-input" readonly
                               placeholder="选择<%= oaonesPBShort %>部门（可多选）">
                        <span class="filter-arrow">▾</span>
                        <button type="button" class="filter-clear" id="wxDeptFilterClear" title="清除部门筛选">×</button>
                    </div>
                    <div class="wx-state-filters" id="wxStateFilters">
                        <label class="wx-state-filter checked"><input type="checkbox" id="wxStateBound" value="bound" checked>已绑定</label>
                        <label class="wx-state-filter checked"><input type="checkbox" id="wxStateUnbound" value="unbound" checked>未绑定</label>
                        <label class="wx-state-filter checked"><input type="checkbox" id="wxStateNoBind" value="nobind" checked>免绑定</label>
                    </div>
                </div>
                <div style="display:flex; gap:8px; align-items:center;">
                    <button class="btn small secondary" id="batchNoBindWxBtn">批量免绑定</button>
                </div>
            </div>
            <div class="bind-stats" id="deptWxStats" style="display:none;"></div>
            <div class="table-wrap">
                <table class="grid" id="wxTable">
                    <thead>
                    <tr>
                        <th style="width:30px;"><input type="checkbox" id="checkAllWx"
                                                       title="全选/取消全选当前页的<%= oaonesPBShort %>部门（用于“批量免绑定”）；要选中全部（含未翻到的页）请用表头上方的“跨页全选”"></th>
                        <th style="width:32%;"><%= oaonesPBShort %>部门</th>
                        <th style="width:36%;">OA 部门</th>
                        <th>备注</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="wxBody"></tbody>
                </table>
                <div class="empty" id="wxEmpty" style="display:none;">暂无<%= oaonesPBShort %>部门</div>
            </div>
            <div class="pager" id="deptWxPager"></div>
        </div>

<script>
"use strict";
/** 片段脚本比页壳脚本先执行（页壳的 byId 在页面末尾才定义），片段内必须自带一份，否则解析期就报 byId is not defined */
function byId(id) { return document.getElementById(id); }

/**
 * 企微部门状态：已绑定 / 未绑定 / 免绑定。
 * 免绑定取自 uf_oaones_appsyn_dep_tmp.no_bind = 0（正常不写值，返回空串）。
 */
function wxStateOf(d) {
    var id = String(d.id);
    if (boundByWx[id]) { return "bound"; }
    if (String(d.no_bind) === "0") { return "nobind"; }
    return "unbound";
}

/** 企微部门三种状态的数量（状态/关键字筛选前的口径，用于统计行）。 */
function wxStateCounts() {
    var counts = { bound: 0, unbound: 0, nobind: 0 };
    wxDepts.forEach(function (d) { counts[wxStateOf(d)]++; });
    return counts;
}

function applyWxStateFilter(value, on) {
    var idx = state.wxStates.indexOf(value);
    if (on) {
        if (idx < 0) { state.wxStates.push(value); }
    } else if (idx >= 0) {
        state.wxStates.splice(idx, 1);
    }
    if (state.view === "wxunbound") { renderWxUnbound(); }
}

/* 本页“绑定时的 OA 部门选择”：弹框内已选但未落库的预览值记在 wxOaPick（企微部门ID → OA 部门ID），
 * 双击该行或点“确定”时立即调用 dept.save 落库；列表式/树状两种显示模式共用同一份待选数据。 */
var wxOaPick = {};
/** 行首复选框选中的企微部门 ID（跨页保留，本页只用于“批量免绑定”） */
var wxCheckedIds = {};
/** 当前筛选条件下可勾选（未绑定）的全部企微部门 ID：跨页全选与勾选数量都按它统计 */
var wxSelectableIds = [];
/** 当前页可勾选（未绑定）的企微部门 ID：表头复选框只全选当页，跨页全选另用表头上方的勾选框 */
var wxPageSelectableIds = [];
/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js）：翻页后重绘当前列表 */
var deptWxPager = OaonesTable.pager({
    key: "deptBind.wxUnbound",
    el: byId("deptWxPager"),
    onChange: function () { renderWxUnbound(); }
});

function oaDeptOptionMap() {
    var map = {};
    for (var i = 0; i < oaDeptOptions.length; i++) {
        map[String(oaDeptOptions[i].id)] = oaDeptOptions[i];
    }
    return map;
}

// 可选 OA 部门：未绑定、状态正常；已被本页其他企微部门选中的标记 used（展示但不可点）
function oaBindCandidates(wxId) {
    var opts = oaDeptOptionMap();
    var rows = [];
    var seen = {};
    oaRows().forEach(function (r) {
        var id = String(r.id);
        if (seen[id]) { return; }
        seen[id] = true;
        if (boundByOa[id] || String(r.canceled) === "1") { return; }
        var opt = opts[id];
        var parent = (r.parent == null || String(r.parent) === "")
            ? (opt && opt.parent != null ? opt.parent : "")
            : r.parent;
        rows.push({
            id: id,
            name: (opt && opt.name) || r.name || "",
            fullname: (opt && opt.fullname) || r.name || "",
            code: (opt && opt.code) || "",
            parent: String(parent == null ? "" : parent)
        });
    });
    return rows;
}

function oaBindLabel(oaId) {
    oaId = String(oaId == null ? "" : oaId);
    if (!oaId) { return ""; }
    var name = "";
    oaRows().forEach(function (r) {
        if (String(r.id) === oaId && r.name) { name = r.name; }
    });
    if (!name) {
        var opts = oaDeptOptionMap();
        if (opts[oaId]) { name = opts[oaId].fullname || opts[oaId].name || ""; }
    }
    return (name || "OA部门") + "（ID " + oaId + "）";
}

function oaBindPickerHtml(wxId) {
    var oaId = wxOaPick[String(wxId)] || "";
    return '<div class="wx-picker" data-kind="oabind" data-wx="' + esc(wxId)
        + '" title="点击弹出 OA 部门列表选择（双击可直接确定并绑定）">'
        + '<input type="text" class="wx-picker-text" readonly value="' + esc(oaId ? oaBindLabel(oaId) : "") + '"'
        + ' placeholder="请点击选择 OA 部门">'
        + '<input type="hidden" class="wx-picker-value" value="' + esc(oaId) + '">'
        + '<span class="wx-picker-arrow">▾</span>'
        + '</div>';
}

function showOaBindPick(wxId) {
    wxId = String(wxId == null ? "" : wxId);
    if (!wxId) { return; }
    OaonesPicker.open({
        key: "oaBind",
        title: "为" + P_SHORT + "部门“" + (wxName(wxId) || "ID " + wxId) + "”选择 OA 部门",
        multiple: false,
        allowEmpty: false,
        initialSelected: wxOaPick[wxId] ? [wxOaPick[wxId]] : [],
        rows: oaBindCandidates(wxId),
        idOf: function (d) { return d.id; },
        parentOf: function (d) { return d.parent; },
        textOf: function (d) { return d.name || d.fullname; },
        detailOf: function (d) { return [d.code, d.fullname].filter(Boolean).join(" / "); },
        onConfirm: function (selection) {
            if (selection.ids.length) { saveWxOaBind(wxId, selection.ids[0]); }
        }
    });
}

function updateOaBindPickerDom(wxId) {
    var picker = document.querySelector("#wxBody .wx-picker[data-kind='oabind'][data-wx='" + wxId + "']");
    if (!picker) { return; }
    var oaId = wxOaPick[String(wxId)] || "";
    var hidden = picker.querySelector(".wx-picker-value");
    var text = picker.querySelector(".wx-picker-text");
    if (hidden) { hidden.value = oaId; }
    if (text) { text.value = oaId ? oaBindLabel(oaId) : ""; }
}

/* ---------------------------------------------------------------
 * 企微部门筛选（筛选栏「选择企微部门」弹框）
 *   - 候选来自页壳加载的企微部门快照 wxDepts（uf_oaones_appsyn_dep_tmp）
 *   - 多选，选中部门连带其全部下级部门（与“OA数据基准”页分部/部门筛选口径一致）
 *   - 本页列表就是企微部门快照，因此筛选按企微部门本身匹配：只看所选部门及其下级
 * --------------------------------------------------------------- */

/** 企微部门父子关系：parentid → [子部门ID] */
function wxDeptChildMap() {
    var map = {};
    (wxDepts || []).forEach(function (d) {
        var p = String(d.parentid == null ? "" : d.parentid);
        if (!map[p]) { map[p] = []; }
        map[p].push(String(d.id));
    });
    return map;
}

/** 批量连带下级展开（用于“确定”写回与“全选”） */
function expandWxDeptIds(ids) {
    var map = wxDeptChildMap();
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

/** 企微部门名称：快照里取名称，取不到回退“企微部门 + ID” */
function wxDeptFilterLabel(id) {
    return wxName(id) || (P_SHORT + "部门 " + String(id == null ? "" : id));
}

/** 列表行内“父部门”：快照里能取到名称时输出“名称(id:x)”，取不到名称时退回只显示 ID */
function wxParentDeptText(parentId) {
    var pid = parentId === null || parentId === undefined ? "" : String(parentId).trim();
    if (pid === "") { return "父部门：-"; }
    var name = wxName(pid);
    return name
        ? "父部门：" + esc(name) + "(id:" + esc(pid) + ")"
        : "父部门：" + esc(pid);
}

/** 筛选栏显示：未选时显占位符，单选显示“名称（ID）”，多选显示已选数量 */
function renderWxDeptFilterInput() {
    var input = byId("wxDeptFilterInput");
    var picker = byId("wxDeptFilterPicker");
    if (!input) { return; }
    var ids = (state.wxDeptIds || []).map(String);
    input.value = ids.length === 0 ? ""
        : (ids.length === 1
            ? wxDeptFilterLabel(ids[0]) + "（ID " + ids[0] + "）"
            : "已选 " + ids.length + " 个" + P_SHORT + "部门（含下级）");
    if (picker) { picker.classList.toggle("has-value", ids.length > 0); }
}

function openWxDeptPick() {
    OaonesPicker.open({
        key: "wxDeptPick",
        title: "选择" + P_SHORT + "部门",
        multiple: true,
        selectChildren: true,
        initialSelected: state.wxDeptIds || [],
        rows: wxDepts || [],
        idOf: function (d) { return d.id; },
        parentOf: function (d) { return d.parentid; },
        textOf: function (d) { return d.name || (P_SHORT + "部门 " + d.id); },
        detailOf: function (d) { return "部门ID " + d.id; },
        onConfirm: function (selection) {
            state.wxDeptIds = expandWxDeptIds(selection.ids);
            renderWxDeptFilterInput();
            if (state.view === "wxunbound") { renderWxUnbound(); }
        }
    });
}

/** 筛选栏“×”：清空企微部门筛选 */
function clearWxDeptFilter() {
    state.wxDeptIds = [];
    renderWxDeptFilterInput();
    if (state.view === "wxunbound") { renderWxUnbound(); }
}

/** 当前生效的企微部门筛选ID（已在写回时连带下级） */
function wxDeptFilterIds() {
    return (state.wxDeptIds || []).map(String);
}

function renderWxUnbound() {
    var statsEl = byId("deptWxStats");
    var statuses = (state.wxStates || []).slice();
    // 快照中的全部企微部门：按“已绑定/未绑定/免绑定”状态勾选筛选（都不勾选时显示全部）
    var rows = statuses.length
        ? wxDepts.filter(function (d) { return statuses.indexOf(wxStateOf(d)) >= 0; })
        : wxDepts.slice();
    // 企微部门筛选（多选 + 连带下级）：本页列表就是企微部门快照，按部门本身匹配
    var deptIds = wxDeptFilterIds();
    if (deptIds.length) {
        rows = rows.filter(function (d) { return deptIds.indexOf(String(d.id)) >= 0; });
    }
    // 勾选范围（只服务于“批量免绑定”）：当前筛选条件下的全部“未绑定”企微部门（已绑定/免绑定不参与）
    wxSelectableIds = [];
    rows.forEach(function (d) {
        if (wxStateOf(d) === "unbound") { wxSelectableIds.push(String(d.id)); }
    });
    // 勾选状态只保留当前列表内仍可勾选（未绑定）的部门，避免出现“看不见的已选部门”
    var selectableKeep = {};
    wxSelectableIds.forEach(function (id) {
        if (wxCheckedIds[id]) { selectableKeep[id] = true; }
    });
    wxCheckedIds = selectableKeep;
    // “仅显示已选择数据”：只把显示范围收窄到已勾选的行（含其他页已选），跨页勾选集合不变
    var displayRows = state.wxOnlyChecked
        ? rows.filter(function (d) { return !!wxCheckedIds[String(d.id)]; })
        : rows;
    deptWxPager.resetIfChanged(wxUnboundFilterSignature());
    var pageRows = deptWxPager.apply(displayRows);
    // 表头复选框只作用于当页：这里记下当前页可勾选的企微部门
    wxPageSelectableIds = [];
    pageRows.forEach(function (d) {
        if (wxStateOf(d) === "unbound") { wxPageSelectableIds.push(String(d.id)); }
    });
    var counts = wxStateCounts();
    // 未选租户时不显示统计数据（列表本身也还没有数据）
    statsEl.style.display = state.cfgId ? "" : "none";
    statsEl.innerHTML = ""
        + "<span class='bind-stat'><span>" + P_SHORT + "部门共计</span><b>" + wxDepts.length + "</b> 个</span>"
        + "<span class='bind-stat'><span>已绑定</span><b>" + counts.bound + "</b> 个</span>"
        + "<span class='bind-stat'><span>未绑定</span><b>" + counts.unbound + "</b> 个</span>"
        + "<span class='bind-stat'><span>免绑定</span><b>" + counts.nobind + "</b> 个</span>"
        + "<span class='bind-stat'><span>当前显示</span><b>" + displayRows.length + "</b> 个</span>"
        // 统计数据之后：仅显示已选择数据 / 显示所有数据（跨页全选已挪到表头上方）
        + OaonesTable.listActionsHtml({
            total: (wxSelectableIds || []).length,
            unit: "个",
            what: "可批量免绑定的" + P_SHORT + "部门",
            onlyChecked: !!state.wxOnlyChecked,
            checked: wxUnboundCheckedCount()
        });
    var body = "";
    pageRows.forEach(function (d) {
        var id = String(d.id);
        var st = wxStateOf(d);
        // 行首复选框只对“未绑定”部门可用（批量免绑定）
        var checkHtml = st === "unbound"
            ? "<input type='checkbox' class='wx-check' data-wx='" + esc(id) + "'"
                + (wxCheckedIds[id] ? " checked" : "") + ">"
            : "<span class='muted'>—</span>";
        var chooseHtml;
        var noteHtml;
        var opsHtml;
        if (st === "bound") {
            // 多对一：同一企微部门可被多个 OA 部门绑定（多个 OA 部门对应同一个企微部门），
            // 单元格内每个 OA 部门单独一行，行尾直接跟该条绑定的“解绑”按钮
            var boundRows = boundListByWx[id] || [];
            if (!boundRows.length && boundByWx[id]) { boundRows = [boundByWx[id]]; }
            var bindLines = [];
            boundRows.forEach(function (b) {
                var oaId = String(b.oa_dept_id);
                var oaName = b.oa_dept_name || oaDeptLabel(oaId);
                bindLines.push("<div class='bind-line'>"
                    + "<span class='bind-line-text'>"
                    + OaonesHrm.dept(oaId, oaName || "OA部门") + "（ID " + esc(oaId) + "）</span>"
                    + "<button class='link danger unbind-btn' data-id='" + esc(b.id)
                    + "' title='解除该" + P_SHORT + "部门与 " + esc(oaName || ("OA 部门 ID " + oaId))
                    + " 的绑定'>解绑</button></div>");
            });
            chooseHtml = "<div class='bind-lines' title='"
                + esc("同一" + P_SHORT + "部门可被多个 OA 部门绑定，如需再绑定其他 OA 部门，"
                    + "请在“OA数据基准”页对该 OA 部门选择本部门") + "'>"
                + "<div class='user-mini'>已绑定 OA 部门（" + boundRows.length + " 个）</div>"
                + bindLines.join("")
                + "</div>";
            noteHtml = "<span class='tag bound'>已绑定</span>"
                + ((boundByWx[id] && boundByWx[id].remark)
                    ? "<div class='row-note'>" + esc(boundByWx[id].remark) + "</div>" : "");
            // 解绑按钮已随每条绑定关系逐行放在“OA 部门”列，不再另起一行
            opsHtml = "<span class='muted'>—</span>";
        } else if (st === "nobind") {
            chooseHtml = "<div class='muted' style='padding:5px 0;'>免绑定部门暂不可选择 OA 部门，请先取消免绑定</div>";
            noteHtml = "<span class='tag nobind'>免绑定</span>";
            opsHtml = "<button class='link cancel-nobind-btn' data-wx='" + esc(id) + "'>取消免绑定</button>";
        } else {
            chooseHtml = "<div class='pick-col'>" + oaBindPickerHtml(id) + "</div>";
            noteHtml = "<span class='tag unbound'>" + P_SHORT + "未绑定</span>";
            opsHtml = "<button class='link no-bind-btn' data-wx='" + esc(id) + "'>免绑定</button>";
        }
        // 弹框内已选但未落库的 OA 部门：明确标成“待绑定（未保存）”，双击/“确定”才写库
        var pendingOa = st === "unbound" ? String(wxOaPick[id] || "") : "";
        if (pendingOa) {
            noteHtml += "<div class='row-note'>待绑定（未保存）：" + esc(oaBindLabel(pendingOa))
                + "（双击该部门或点“确定”即完成绑定）</div>";
        }
        body += "<tr>"
            + "<td>" + checkHtml + "</td>"
            + "<td><div><b>" + esc(d.name) + "</b>（ID " + esc(id) + "）</div>"
            + "<div class='user-mini'>" + wxParentDeptText(d.parentid) + "</div></td>"
            + "<td>" + chooseHtml + "</td>"
            + "<td>" + noteHtml + "</td>"
            + "<td>" + opsHtml + "</td>"
            + "</tr>";
    });
    byId("wxBody").innerHTML = body;
    deptWxPager.show();
    deptWxPager.render();
    OaonesTable.resizable(byId("wxTable"), { key: "deptBind.wxTable" });
    updateWxUnboundCheckedState();
    byId("wxEmpty").style.display = displayRows.length ? "none" : "block";
    if (state.wxOnlyChecked) {
        // 只看已选时为空，多半是当前筛选范围内没有勾选（或刚把勾选都取消了）
        byId("wxEmpty").textContent = "当前范围没有已勾选的数据，点统计行末尾“显示所有数据”可查看全部";
    } else if (!wxDepts.length) {
        byId("wxEmpty").textContent = P_SHORT + "部门快照为空，请先点击“更新" + P_SHORT + "部门快照”";
    } else if (deptIds.length) {
        byId("wxEmpty").textContent = "所选" + P_SHORT + "部门（含下级）下没有符合当前条件的" + P_SHORT + "部门，可调整或清除“选择" + P_SHORT + "部门”筛选";
    } else if (statuses.length) {
        byId("wxEmpty").textContent = "当前勾选的状态下暂无" + P_SHORT + "部门，可调整“已绑定/未绑定/免绑定”筛选";
    } else {
        byId("wxEmpty").textContent = "快照中暂无" + P_SHORT + "部门";
    }
}

/** 筛选条件签名：任一条件变化都要回到第 1 页（部门筛选、状态勾选、仅显示已选、租户） */
function wxUnboundFilterSignature() {
    return [state.cfgId, wxDeptFilterIds().join(","), (state.wxStates || []).join(","),
        state.wxOnlyChecked ? "1" : "0"].join("|");
}

/** 当前筛选条件下已勾选（可绑定）的企微部门数量：跨页统计，用于“已选 N / 共 M”与跨页全选条 */
function wxUnboundCheckedCount() {
    var checked = 0;
    (wxSelectableIds || []).forEach(function (id) {
        if (wxCheckedIds[id]) { checked++; }
    });
    return checked;
}

/** 当前页已勾选（可绑定）的企微部门数量：表头复选框只看当页 */
function wxUnboundPageCheckedCount() {
    var checked = 0;
    (wxPageSelectableIds || []).forEach(function (id) {
        if (wxCheckedIds[id]) { checked++; }
    });
    return checked;
}

/** 当前筛选条件下的可勾选企微部门是否已全部勾选（跨页） */
function wxUnboundAllSelected() {
    var total = (wxSelectableIds || []).length;
    return total > 0 && wxUnboundCheckedCount() >= total;
}

/** 表头复选框：只全选/取消全选当前页可绑定的企微部门（当页全选）。 */
function toggleWxUnboundPageChecks(checked) {
    (wxPageSelectableIds || []).forEach(function (id) {
        if (checked) { wxCheckedIds[id] = true; } else { delete wxCheckedIds[id]; }
    });
    renderWxUnbound();
}

/** 统计行末尾的“仅显示已选择数据 / 显示所有数据”：只改列表显示范围，不动跨页勾选集合。 */
function toggleWxUnboundOnlyChecked() {
    state.wxOnlyChecked = !state.wxOnlyChecked;
    renderWxUnbound();
}

/** 同步表头当页全选框 + 表头上方跨页全选条（跨页勾选数量按 wxCheckedIds 统计）。 */
function updateWxUnboundCheckedState() {
    var total = (wxSelectableIds || []).length;
    var checked = wxUnboundCheckedCount();
    // 表头复选框只反映当前页（当页全选）
    var all = byId("checkAllWx");
    OaonesTable.checkAllTitle(all, "可批量免绑定的" + P_SHORT + "部门");
    OaonesTable.syncCheckAll(all, wxUnboundPageCheckedCount(), (wxPageSelectableIds || []).length);
    // 表头上方的“跨页全选”条：左侧复选框与表头复选框同列对齐，勾选/取消即跨页全选/取消
    OaonesTable.crossPageBar(byId("wxTable"), {
        total: total,
        unit: "个",
        allSelected: wxUnboundAllSelected(),
        checked: checked,
        what: "可批量免绑定的" + P_SHORT + "部门",
        onChange: toggleAllWxUnboundChecks
    });
    // 统计行末尾的“仅显示已选择数据 / 显示所有数据”：同步“已选 N / 共 M”（行内勾选后不整表重绘也要准）
    OaonesTable.refreshListActions(byId("deptWxStats"), {
        total: total,
        unit: "个",
        what: "可批量免绑定的" + P_SHORT + "部门",
        onlyChecked: !!state.wxOnlyChecked,
        checked: checked
    });
}

/** 跨页全选（表头上方勾选框）：作用于当前筛选条件下全部可绑定的企微部门（含未翻到的页）。 */
function toggleAllWxUnboundChecks(checked) {
    (wxSelectableIds || []).forEach(function (id) {
        if (checked) { wxCheckedIds[id] = true; } else { delete wxCheckedIds[id]; }
    });
    renderWxUnbound();
}

/** 当前选中（且仍可绑定）的企微部门 ID，按列表顺序返回。 */
function checkedWxUnboundIds() {
    var ids = [];
    (wxSelectableIds || []).forEach(function (id) {
        if (wxCheckedIds[id]) { ids.push(String(id)); }
    });
    return ids;
}

function batchNoBindFromWx() {
    var ids = checkedWxUnboundIds();
    if (!ids.length) { showToast("请先勾选要设置免绑定的" + P_SHORT + "部门", 1); return; }
    OaonesToast.confirm("确认将 " + ids.length + " 个" + P_SHORT + "部门设置为免绑定？免绑定部门不参与绑定选择，可取消免绑定后再次绑定。",
        function () { doBatchNoBindFromWx(ids); });
}

function doBatchNoBindFromWx(ids) {
    var btn = byId("batchNoBindWxBtn");
    btn.disabled = true;
    Oaones.api("dept.batchNoBind", {
        cfg_id: state.cfgId,
        wx_dept_ids: ids.join(",")
    }, function (res) {
        btn.disabled = false;
        if (res.code === 0) {
            showToast("批量免绑定成功，共 " + (res.data && res.data.success) + " 个");
            loadData();
        } else {
            showToast(res.msg, 2);
        }
    });
}

function toggleWxNoBind(btn, mark) {
    var wxId = btn.getAttribute("data-wx");
    if (!state.cfgId || !wxId) { return; }
    btn.disabled = true;
    Oaones.api(mark ? "dept.noBind" : "dept.cancelNoBind", {
        cfg_id: state.cfgId,
        wx_dept_id: wxId
    }, function (res) {
        if (res.code !== 0) {
            btn.disabled = false;
            showToast(res.msg, 2);
            return;
        }
        showToast(mark ? "已设置免绑定" : "已取消免绑定，可再次绑定");
        loadData();
    });
}

/* 行首复选框：勾选态写入 wxCheckedIds（跨页保留），表头全选框同步三态 */
byId("wxBody").addEventListener("change", function (e) {
    var t = e.target;
    if (!t || !t.classList || !t.classList.contains("wx-check")) { return; }
    var id = String(t.getAttribute("data-wx"));
    if (t.checked) { wxCheckedIds[id] = true; } else { delete wxCheckedIds[id]; }
    // “仅显示已选择数据”时取消勾选要立即把该行移出列表，否则只同步勾选态与统计行
    if (state.wxOnlyChecked) { renderWxUnbound(); } else { updateWxUnboundCheckedState(); }
});

// 统计行末尾的操作组：仅显示已选择数据（按钮由渲染时拼进统计行，这里用事件委托接住）
// 跨页全选已挪到表头上方的勾选框（OaonesTable.crossPageBar，见 updateWxUnboundCheckedState）
byId("deptWxStats").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("only-checked-btn")) {
        toggleWxUnboundOnlyChecked();
    }
});

</script>
