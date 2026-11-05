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

<%-- 二级菜单：平台数据基准（B 页，人员绑定 - 平台成员侧） --%>
<%-- 页面说明正文：由页壳 sub-tabs 右侧的感叹号（#wxHelpTip）悬浮/点击弹出 --%>
<div class="help-source" id="wxHelpText">以下为<%= oaonesPBShort %>人员快照中的全部正常成员，可按“已绑定/未绑定/免绑定”勾选筛选，可点击“选择<%= oaonesPBShort %>部门”按<%= oaonesPBShort %>部门（可多选、选中部门会自动带上其全部下级部门）筛选，也可输入关键字即时筛选。行内按钮统一放在“OA 人员”列（列表不再单独设“操作”列）。未绑定成员点击该列的选择框会弹出 OA 人员列表：弹框自动带入该行<%= oaonesPBShort %>成员姓名作为搜索关键字，单击选中、双击（或点击“确定”）即保存绑定，列表上不再单独放“绑定OA人员”按钮；选择框下方是“免绑定”按钮，点击后该成员不参与绑定选择，可取消免绑定后再次绑定。已绑定成员会在该列按“一行一个 OA 人员”列出该<%= oaonesPBShort %>成员的全部绑定关系（一个<%= oaonesPBShort %>成员对应多个 OA 人员时分行显示，每行的 OA 人员姓名可点击打开人员卡片），每行后面直接带该条绑定的“解绑”按钮，点击即可只解除该条绑定，解绑后可重新绑定。“部门绑定”列显示两侧部门是否已建立绑定；部门已绑定但人员未绑定时，该列会出现“同步人员”，点击后按部门绑定关系把该<%= oaonesPBShort %>成员新建/更新到对应 OA 部门并自动建立人员绑定。
           行首复选框用于批量操作（仅“未绑定”成员可勾选，已绑定与免绑定成员的复选框不可用）：表头复选框只全选当前页，要一次选中当前筛选条件下的全部成员（含未翻到的页）请勾选表头上方（与表头复选框对齐）的“跨页全选”，全部选中后可点统计行末尾的“仅显示已选择数据”只看已勾选行核对。批量“同步人员”按部门绑定关系把勾选的<%= oaonesPBShort %>成员逐条同步到 OA 并建立人员绑定，同步完成后弹出结果框，成功人数如上，失败人员按列表逐条给出失败原因（例如“所在部门没有进行绑定，请手动创建人员或先进行部门绑定”），失败人员会保持勾选，按提示处理后可再次点击“同步人员”重试；勾选后也可点击“批量免绑定”，一次把勾选的<%= oaonesPBShort %>成员标记为免绑定（免绑定成员不参与绑定选择，可取消免绑定后再次绑定）。</div>
        <div id="wxUnboundPanel" style="display:none;">
            <div class="filter-bar" style="justify-content:space-between;">
                <div style="display:flex; gap:10px; align-items:center; flex:1; flex-wrap:wrap;">
                    <span class="filter-label"><%= oaonesPBShort %>成员筛选：</span>
                    <div class="filter-picker" id="wxDeptFilterPicker"
                         title="点击选择<%= oaonesPBShort %>部门（可多选；选中部门会自动选中其全部下级部门，筛选按成员的<%= oaonesPBShort %>部门匹配）">
                        <input type="text" id="wxDeptFilterInput" class="filter-input" readonly
                               placeholder="选择<%= oaonesPBShort %>部门（可多选）">
                        <span class="filter-arrow">▾</span>
                        <button type="button" class="filter-clear" id="wxDeptFilterClear" title="清除部门筛选">×</button>
                    </div>
                    <input type="text" id="wxKeyword" placeholder="姓名/ID/部门" autocomplete="off" style="min-width:220px;max-width:300px;">
                    <div class="wx-state-filters" id="wxStateFilters">
                        <label class="wx-state-filter checked"><input type="checkbox" id="wxStateBound" value="bound" checked>已绑定</label>
                        <label class="wx-state-filter checked"><input type="checkbox" id="wxStateUnbound" value="unbound" checked>未绑定</label>
                        <label class="wx-state-filter checked"><input type="checkbox" id="wxStateNoBind" value="nobind" checked>免绑定</label>
                    </div>
                </div>
                <div style="display:flex; gap:10px; align-items:center; flex-wrap:wrap;">
                    <button type="button" class="btn small secondary" id="batchNoBindWxBtn"
                            title="把勾选的<%= oaonesPBShort %>成员一次标记为免绑定（行首复选框仅“未绑定”成员可用）：免绑定成员不参与绑定选择，可取消免绑定后再次绑定">批量免绑定</button>
                    <button type="button" class="btn small secondary" id="syncWxCheckedBtn"
                            data-oaones-feature="user.bind.sync,user.bind.sync.third2oa"
                            title="把勾选的<%= oaonesPBShort %>成员按部门绑定关系同步到 OA 并建立人员绑定；同步失败的人员会在结果弹框中列出失败原因">同步人员</button>
                    <span class="muted" id="wxCheckedTip"></span>
                </div>
            </div>
            <div class="bind-stats" id="wxStats" style="display:none;"></div>
            <div class="table-wrap">
                <table class="grid" id="wxTable">
                    <thead>
                    <tr>
                        <th style="width:36px;"><input type="checkbox" id="wxCheckAll"
                                                       title="全选/取消全选当前页可勾选（未绑定）的<%= oaonesPBShort %>成员；要选中全部（含未翻到的页）请用表头上方的“跨页全选”"></th>
                        <th style="width:26%;"><%= oaonesPBShort %>成员</th>
                        <th style="width:32%;">OA 人员</th>
                        <th style="width:16%;">部门绑定</th>
                        <th>状态</th>
                    </tr>
                    </thead>
                    <tbody id="wxBody"></tbody>
                </table>
                <div class="empty" id="wxEmpty" style="display:none;">暂无<%= oaonesPBShort %>成员</div>
            </div>
            <div class="pager" id="wxPager"></div>
        </div>

<%-- 批量“同步人员”（平台 → OA）结果：成功统计 + 失败人员列表（标注失败原因） --%>
<div class="modal-mask" id="wxSyncResultMask"></div>
<div class="modal sync-result-modal" id="wxSyncResultModal">
    <div class="modal-head">
        <span>同步人员结果（<%= oaonesPBShort %> → OA）</span>
        <button type="button" class="link" id="wxSyncResultCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body" id="wxSyncResultBody"></div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="wxSyncResultRetryBtn"
                title="仅保留失败人员勾选并重新同步">重试失败人员</button>
        <button type="button" class="btn" id="wxSyncResultOkBtn">知道了</button>
    </div>
</div>

<script>
"use strict";
/**
 * 面板脚本早于页壳脚本（userBind.jsp 底部）执行，页壳里的 byId 此时还没定义，
 * 面板末尾的按钮事件绑定不能依赖它；这里放一份同实现的本地兜底，
 * 避免“byId is not defined”中断后面的绑定（页壳随后用同实现的函数覆盖，行为一致）。
 */
function byId(id) { return document.getElementById(id); }
/**
 * 本页（企微数据基准）绑定时的 OA 人员选择：待选结果先记在 wxOaPersonPick（企微成员ID → OA 人员ID），
 * 在弹框里双击成员行或点“确定”时立即落库（列表上不再单独放“绑定OA人员”按钮）；
 * 弹框样式与“部门绑定”页的 OA 部门选择弹框保持一致。
 */
var wxOaPersonPick = {};
/** 每次重新加载数据前清空尚未落库的“企微成员 → OA 人员”草稿，避免刷新后残留旧选择。 */
function resetOaPersonPick() {
    wxOaPersonPick = {};
}
/** 行首复选框选中的企微成员ID（仅当前列表内“未绑定”的成员） */
var wxCheckedIds = {};
/** 当前筛选条件下可勾选（未绑定）的全部企微成员ID：跨页全选与勾选数量都按它统计 */
var wxSelectableIds = [];
/** 当前页可勾选（未绑定）的企微成员ID：表头复选框只全选当页，跨页全选另用表头上方的勾选框 */
var wxPageSelectableIds = [];
/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js）：翻页后重绘当前列表 */
var wxPager = OaonesTable.pager({
    key: "userBind.wx",
    el: byId("wxPager"),
    onChange: function () { renderWxUnbound(); }
});
/** 批量“同步人员”进行中标记，避免重复提交 */
var wxSyncing = false;
/** 批量“同步人员”每次请求的人数：分片提交，便于展示进度并避免单次请求过长 */
var WX_SYNC_CHUNK_SIZE = 10;

/**
 * 企微成员状态：已绑定 / 未绑定 / 免绑定。
 * 免绑定取自 uf_oaones_appsyn_user_tmp.no_bind = 0（正常不写值，返回空串）。
 */
function wxStateOf(u) {
    var id = String(u.wx_user_id);
    if (boundByWx[id]) { return "bound"; }
    if (String(u.no_bind) === "0") { return "nobind"; }
    return "unbound";
}

function wxStateCounts() {
    var counts = { bound: 0, unbound: 0, nobind: 0 };
    wxUsers.forEach(function (u) {
        var st = wxStateOf(u);
        if (counts[st] == null) { counts[st] = 0; }
        counts[st]++;
    });
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

/** 可选 OA 人员：尚未绑定的 OA 人员；已被本次其他企微成员选中的标记 used（展示但不可点）。 */
function oaPersonCandidates(wxId) {
    var used = {};
    Object.keys(wxOaPersonPick).forEach(function (k) {
        var v = wxOaPersonPick[k];
        if (v && String(k) !== String(wxId)) { used[String(v)] = true; }
    });
    var rows = [];
    oaUsers.forEach(function (u) {
        var id = String(u.id);
        if (boundByOa[id]) { return; }
        // 已标记免绑定的 OA 人员不参与绑定（需先在“OA数据基准”页取消免绑定）
        if (isOaNoBind(id)) { return; }
        rows.push({
            id: id,
            name: u.lastname || "",
            loginid: u.loginid || "",
            workcode: u.workcode || "",
            mobile: u.mobile || "",
            deptId: u.departmentid == null ? "" : String(u.departmentid),
            subcompanyId: u.subcompanyid1 == null ? "" : String(u.subcompanyid1),
            deptName: u.departmentname || "",
            used: used[id] ? "1" : "0"
        });
    });
    return rows;
}

function oaPersonLabel(oaId) {
    var id = String(oaId == null ? "" : oaId);
    if (!id) { return ""; }
    var name = "";
    for (var i = 0; i < oaUsers.length; i++) {
        if (String(oaUsers[i].id) === id) { name = oaUsers[i].lastname || ""; break; }
    }
    return (name || "OA人员") + "（ID " + id + "）";
}

function oaPersonPickerHtml(wxId) {
    var oaId = wxOaPersonPick[String(wxId)] || "";
    return '<div class="wx-picker" data-kind="oaperson" data-wx="' + esc(wxId) + '" title="点击弹出 OA 人员列表选择（双击可直接确定）">'
        + '<input type="text" class="wx-picker-text" readonly value="' + esc(oaId ? oaPersonLabel(oaId) : "") + '"'
        + ' placeholder="请点击选择 OA 人员">'
        + '<input type="hidden" class="wx-picker-value" value="' + esc(oaId) + '">'
        + '<span class="wx-picker-arrow">▾</span>'
        + '</div>';
}

function showOaPersonPick(wxId) {
    wxId = String(wxId == null ? "" : wxId);
    if (!wxId) { return; }
    var memberName = "";
    for (var i = 0; i < wxUsers.length; i++) {
        if (String(wxUsers[i].wx_user_id) === wxId) { memberName = wxUsers[i].wx_name || ""; break; }
    }
    var candidates = oaPersonCandidates(wxId);
    var treeRows = oaPersonTreeRows(candidates);
    var rows = [];
    treeRows.forEach(function (r) {
        if (r.group === true) {
            rows.push({
                id: r.id, parentId: r.parent || "", name: r.name,
                selectable: false, count: r.count
            });
            return;
        }
        var person = r.person;
        rows.push({
            id: r.id,
            parentId: r.parent || "",
            name: (person.name || "") + (person.deptName ? " / " + person.deptName : ""),
            detail: [person.id, person.loginid, person.workcode, person.mobile].filter(Boolean).join(" / "),
            selectable: String(person.used) !== "1",
            reason: String(person.used) === "1" ? "该 OA 人员已被本次其他成员选择" : "",
            person: person
        });
    });
    OaonesPicker.open({
        key: "oaPerson",
        title: memberName
            ? "为" + P_SHORT + "成员“" + memberName + "”选择 OA 人员"
            : "为" + P_SHORT + "成员（ID " + wxId + "）选择 OA 人员",
        multiple: false,
        allowEmpty: false,
        initialSelected: wxOaPersonPick[wxId]
            ? [{ id: "u:" + wxOaPersonPick[wxId], name: oaPersonLabel(wxOaPersonPick[wxId]) }]
            : [],
        rows: rows,
        onConfirm: function (selection) {
            if (!selection.ids.length) { return; }
            saveWxOaBind(wxId, String(selection.ids[0]).replace(/^u:/, ""));
        }
    });
}

/**
 * OA 人员树：按 OA 分部 / 部门上下级展示，人员作为部门下的可选叶子。
 * 仅保留“含候选人”的部门及其全部上级，避免出现大量空部门。
 */
function oaPersonTreeRows(rows) {
    var deptById = {};
    oaDeptOptions.forEach(function (d) { deptById[String(d.id)] = d; });
    var subById = {};
    oaSubOptions.forEach(function (s) { subById[String(s.id)] = s; });
    var keep = {};
    (rows || []).forEach(function (r) {
        var cur = String(r.deptId == null ? "" : r.deptId);
        var guard = 0;
        while (cur && deptById[cur] && !keep[cur] && guard++ < 100) {
            keep[cur] = true;
            var p = String(deptById[cur].parent == null ? "" : deptById[cur].parent);
            cur = (p && p !== "0" && deptById[p]) ? p : "";
        }
    });
    var out = [];
    var addedSub = {};
    Object.keys(keep).forEach(function (id) {
        var d = deptById[id];
        var pid = String(d.parent == null ? "" : d.parent);
        var parent = (pid && pid !== "0" && deptById[pid] && keep[pid]) ? "d:" + pid : "";
        if (!parent) {
            var sid = String(d.subcompanyid1 == null ? "" : d.subcompanyid1);
            if (sid) {
                if (!addedSub[sid]) {
                    addedSub[sid] = true;
                    var s = subById[sid];
                    out.push({
                        group: true, id: "s:" + sid, parent: "",
                        name: (s && (s.name || s.fullname)) || ("分部 " + sid), count: 0
                    });
                }
                parent = "s:" + sid;
            }
        }
        out.push({
            group: true, id: "d:" + id, parent: parent,
            name: d.name || d.fullname || ("部门 " + id),
            fullname: d.fullname || d.name || "", count: 0
        });
    });
    var unassigned = null;
    (rows || []).forEach(function (r) {
        var did = String(r.deptId == null ? "" : r.deptId);
        var parent = (did && deptById[did] && keep[did]) ? "d:" + did : "";
        if (!parent) {
            if (!unassigned) {
                unassigned = { group: true, id: "d:none", parent: "", name: "未分配部门", count: 0 };
                out.push(unassigned);
            }
            parent = "d:none";
        }
        out.push({
            group: false, id: "u:" + String(r.id), parent: parent, person: r,
            searchText: [r.id, r.name, r.loginid, r.workcode, r.mobile, r.deptName].map(function (v) {
                return String(v == null ? "" : v);
            }).join(" ")
        });
    });
    var nodeById = {};
    out.forEach(function (n) { nodeById[n.id] = n; });
    var counts = {};
    out.forEach(function (n) {
        if (n.group) { return; }
        var cur = n.parent;
        var guard = 0;
        while (cur && nodeById[cur] && guard++ < 100) {
            counts[cur] = (counts[cur] || 0) + 1;
            cur = nodeById[cur].parent;
        }
    });
    out.forEach(function (n) { if (n.group) { n.count = counts[n.id] || 0; } });
    return out;
}

function updateOaPersonPickerDom(wxId) {
    var picker = document.querySelector("#wxBody .wx-picker[data-kind='oaperson'][data-wx='" + wxId + "']");
    if (!picker) { return; }
    var oaId = wxOaPersonPick[String(wxId)] || "";
    var hidden = picker.querySelector(".wx-picker-value");
    var text = picker.querySelector(".wx-picker-text");
    if (hidden) { hidden.value = oaId; }
    if (text) { text.value = oaId ? oaPersonLabel(oaId) : ""; }
}

/* ---------------------------------------------------------------
 * 企微部门筛选（筛选栏「选择企微部门」）
 *   - 候选来自页壳加载的企微部门快照 wxDepts（uf_oaones_appsyn_dep_tmp，status=0）
 *   - 多选，选中部门连带其全部下级部门（与 A 页“分部/部门”筛选口径一致）
 *   - 过滤按成员的企微部门（uf_oaones_appsyn_user_tmp.wx_dept_id，即成员主部门）
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

/** 批量连带下级展开（用于“确定”写回与全选）。 */
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

/** 企微部门名称：快照里取名称，取不到回退“企微部门 + ID”。 */
function wxDeptLabel(id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < (wxDepts || []).length; i++) {
        if (String(wxDepts[i].id) === key) {
            return String(wxDepts[i].name || "") || (P_SHORT + "部门 " + key);
        }
    }
    return P_SHORT + "部门 " + key;
}

/** 筛选栏显示：未选时显占位符，单选显示“名称（ID）”，多选显示已选数量。 */
function renderWxDeptFilterInput() {
    var input = byId("wxDeptFilterInput");
    var picker = byId("wxDeptFilterPicker");
    if (!input) { return; }
    var ids = (state.wxDeptIds || []).map(String);
    input.value = ids.length === 0 ? ""
        : (ids.length === 1
            ? wxDeptLabel(ids[0]) + "（ID " + ids[0] + "）"
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

/** 筛选栏“×”：清空部门筛选。 */
function clearWxDeptFilter() {
    state.wxDeptIds = [];
    renderWxDeptFilterInput();
    if (state.view === "wxunbound") { renderWxUnbound(); }
}

/** 当前生效的部门筛选ID（已在写回时连带下级）。 */
function wxDeptFilterIds() {
    return (state.wxDeptIds || []).map(String);
}

/** 当前筛选（部门 + 状态 + 关键字）下可见的企微成员行。 */
function wxVisibleRows() {
    var states = state.wxStates || [];
    var rows = states.length
        ? wxUsers.filter(function (u) { return states.indexOf(wxStateOf(u)) >= 0; })
        : wxUsers.slice();
    rows = filterByKeyword(rows, ["wx_name", "wx_user_id", "wx_dept_name"], state.wxKeyword);
    var deptIds = wxDeptFilterIds();
    if (deptIds.length) {
        rows = rows.filter(function (u) {
            var id = String(u.wx_dept_id == null ? "" : u.wx_dept_id);
            return !!id && deptIds.indexOf(id) >= 0;
        });
    }
    return rows;
}

function renderWxUnbound() {
    var emptyEl = byId("wxEmpty");
    var statsEl = byId("wxStats");
    byId("wxBody").innerHTML = "";
    // 部门名称取自企微部门快照，数据加载完成后同步刷新筛选栏显示
    renderWxDeptFilterInput();
    if (!state.cfgId) {
        statsEl.style.display = "none";
        wxPager.hide();
        wxSelectableIds = [];
        wxPageSelectableIds = [];
        emptyEl.style.display = "block";
        emptyEl.textContent = "请先选择" + P_FULL + "租户";
        clearWxChecked();
        return;
    }
    statsEl.style.display = "";
    var counts = wxStateCounts();
    var rows = wxVisibleRows();
    // 勾选状态只保留当前列表中仍可同步（未绑定）的成员，避免筛选/同步后出现“看不见的已选成员”
    pruneWxChecked(rows);
    // 跨页全选：勾选范围是当前筛选条件下的全部可同步成员，而不是当前页
    wxSelectableIds = [];
    rows.forEach(function (u) {
        var id = String(u.wx_user_id);
        if (wxStateOf(u) === "unbound") { wxSelectableIds.push(id); }
    });
    wxPager.resetIfChanged(wxFilterSignature());
    // “仅显示已选择数据”：只把显示范围收窄到已勾选的行（含其他页已选），跨页勾选集合不变
    var displayRows = state.wxOnlyChecked
        ? rows.filter(function (u) { return !!wxCheckedIds[String(u.wx_user_id)]; })
        : rows;
    var pageRows = wxPager.apply(displayRows);
    // 表头复选框只作用于当页：这里记下当前页可勾选的企微成员
    wxPageSelectableIds = [];
    pageRows.forEach(function (u) {
        if (wxStateOf(u) === "unbound") { wxPageSelectableIds.push(String(u.wx_user_id)); }
    });
    var checkedCount = wxCheckedCount();
    var selectableCount = (wxSelectableIds || []).length;
    statsEl.innerHTML = ""
        + "<span class='bind-stat'><span>" + P_SHORT + "成员共计</span><b>" + wxUsers.length + "</b> 人</span>"
        + "<span class='bind-stat'><span>已绑定</span><b>" + counts.bound + "</b> 人</span>"
        + "<span class='bind-stat'><span>未绑定</span><b>" + counts.unbound + "</b> 人</span>"
        + "<span class='bind-stat'><span>免绑定</span><b>" + counts.nobind + "</b> 人</span>"
        + "<span class='bind-stat'><span>当前显示</span><b>" + displayRows.length + "</b> 人</span>"
        // 统计数据之后：仅显示已选择数据 / 显示所有数据（跨页全选已挪到表头上方）
        + OaonesTable.listActionsHtml({
            total: selectableCount,
            unit: "人",
            what: "可同步的" + P_SHORT + "成员",
            onlyChecked: !!state.wxOnlyChecked,
            checked: checkedCount
        });
    var body = "";
    pageRows.forEach(function (u) {
        var wxId = String(u.wx_user_id);
        var st = wxStateOf(u);
        // 部门绑定：企微成员主部门是否已与 OA 部门建立绑定关系
        var wxDeptId = String(u.wx_dept_id == null ? "" : u.wx_dept_id);
        var deptBind = deptBindOfWx(wxDeptId);
        var deptCell = deptBindCellHtml(deptBind,
            (wxDeptId && wxDeptId !== "0") ? "该" + P_SHORT + "部门未绑定 OA 部门" : P_SHORT + "成员未归属部门");
        var chooseHtml;
        var noteHtml;
        var opsHtml;
        if (st === "bound") {
            // 多对一：同一企微成员可被多个 OA 人员绑定（多个 OA 账户对应同一个企微账户），
            // 单元格内每个 OA 人员单独一行，行尾直接跟该条绑定的“解绑”按钮
            var boundRows = boundListByWx[wxId] || [];
            if (!boundRows.length && boundByWx[wxId]) { boundRows = [boundByWx[wxId]]; }
            var bindLines = [];
            boundRows.forEach(function (b) {
                var oaName = b.oa_name || "";
                // 已绑定的 OA 人员姓名输出人员卡片超链接（泛微标准人员卡片页面）
                var label = OaonesHrm.user(b.oa_user_id, oaName)
                    + (oaName ? "（ID " + esc(b.oa_user_id) + "）" : "");
                bindLines.push("<div class='bind-line'>"
                    + "<span class='bind-line-text'>" + label + "</span>"
                    + "<button class='link danger unbind-btn' data-id='" + esc(b.id)
                    + "' title='解除该" + P_SHORT + "成员与 " + esc(oaName || ("OA 人员 ID " + b.oa_user_id))
                    + " 的绑定'>解绑</button></div>");
            });
            chooseHtml = "<div class='bind-lines' title='"
                + esc("同一" + P_SHORT + "成员可被多个 OA 人员绑定，如需再绑定其他 OA 人员，"
                    + "请在“OA数据基准”页对该 OA 人员选择本成员") + "'>"
                + "<div class='user-mini'>已绑定 OA 人员（" + boundRows.length + " 人）</div>"
                + bindLines.join("")
                + "</div>";
            // 绑定方式取自 bind_source（0 手动 / 1 系统 / 2 自行绑定），不再展示 remark 备注
            noteHtml = "<span class='tag bound'>已绑定</span>"
                + (boundByWx[wxId]
                    ? "<div class='row-note'>" + esc(bindSourceText(boundByWx[wxId].bind_source))
                        + "</div>" : "");
    // 解绑按钮已随每条绑定关系逐行放在本列，不再另起一行
    opsHtml = "";
} else if (st === "nobind") {
    chooseHtml = "<span class='muted'>已标记免绑定，暂不可选择 OA 人员</span>";
    noteHtml = "<span class='tag nobind'>免绑定</span>";
    opsHtml = "<button class='link cancel-nobind-btn' data-wx='" + esc(wxId) + "'>取消免绑定</button>";
} else {
    // 选择 OA 人员：点击弹出选择框（样式与“部门绑定”页的 OA 部门选择框一致），
    // 弹框内双击/“确定”即保存绑定；行内按钮（免绑定/同步人员）统一放本列选择框下方
    chooseHtml = "<div class='pick-col'>" + oaPersonPickerHtml(wxId) + "</div>";
    noteHtml = "<span class='tag unbound'>" + P_SHORT + "未绑定</span>";
    // 不再放“绑定OA人员”（保存绑定改由选择弹框双击/“确定”触发）；部门已绑定时保留“同步人员”
    opsHtml = "<button class='link no-bind-btn' data-wx='" + esc(wxId)
        + "' title='该" + P_SHORT + "成员不需要绑定 OA 人员；标记后不参与绑定选择，可取消免绑定后再次绑定'>免绑定</button>";
    if (deptBind) {
        opsHtml += " <button class='btn small sync-btn' data-oaones-feature='user.bind.sync,user.bind.sync.third2oa' data-wx='" + esc(wxId)
            + "' title='按部门绑定关系把该" + P_SHORT + "成员同步到 OA 部门 "
            + esc(deptBind.oa_dept_id) + " 并建立绑定'>同步人员</button>";
    }
}
        // 行首复选框：仅“未绑定”成员可勾选，用于批量“同步人员”（企微 → OA）
        var checkTitle = st === "bound"
            ? "该" + P_SHORT + "成员已绑定 OA 人员，无需同步人员"
            : (st === "nobind"
                ? "该" + P_SHORT + "成员已标记免绑定，请先取消免绑定后再同步人员"
                : "勾选后可用上方“同步人员”批量同步到 OA，或用“批量免绑定”统一标记为免绑定");
        var checkCell = "<td class='chk-col'><input type='checkbox' class='wx-row-check'"
            + " data-wx='" + esc(wxId) + "'"
            + (st === "unbound" ? "" : " disabled")
            + " title='" + esc(checkTitle) + "'"
            + (st === "unbound" && wxCheckedIds[wxId] ? " checked" : "")
            + "></td>";
    body += "<tr>"
        + checkCell
        + "<td><div><b>" + esc(u.wx_name) + "</b>（" + esc(wxId) + "）</div>"
        + "<div class='user-mini'>" + esc(u.wx_dept_name || "-") + "</div></td>"
        // 行内按钮全部放“OA 人员”列：选择框/已绑定列表下方是免绑定、同步人员、取消免绑定
        + "<td>" + chooseHtml + (opsHtml ? "<div class='row-ops'>" + opsHtml + "</div>" : "") + "</td>"
        + "<td>" + deptCell + "</td>"
        + "<td>" + noteHtml + "</td>"
        + "</tr>";
    });
    byId("wxBody").innerHTML = body;
    wxPager.show();
    wxPager.render();
    OaonesTable.resizable(byId("wxTable"), { key: "userBind.wxTable" });
    updateWxCheckedState();
    emptyEl.style.display = displayRows.length ? "none" : "block";
    if (state.wxOnlyChecked) {
        // 只看已选时为空，多半是当前筛选范围内没有勾选（或刚把勾选都取消了）
        emptyEl.textContent = "当前范围没有已勾选的数据，点统计行末尾“显示所有数据”可查看全部";
    } else if (!wxUsers.length) {
        emptyEl.textContent = "人员快照为空，请先点击“更新" + P_SHORT + "人员快照”";
    } else if (wxDeptFilterIds().length && state.wxKeyword) {
        emptyEl.textContent = "当前关键字 + " + P_SHORT + "部门筛选下没有匹配的" + P_SHORT + "成员";
    } else if (wxDeptFilterIds().length) {
        emptyEl.textContent = "所选" + P_SHORT + "部门（含下级）下没有符合当前条件的" + P_SHORT + "成员";
    } else if (state.wxKeyword) {
        emptyEl.textContent = "没有匹配的" + P_SHORT + "成员";
    } else {
        emptyEl.textContent = (state.wxStates || []).length
            ? "当前勾选的状态下暂无" + P_SHORT + "成员，可调整“已绑定/未绑定/免绑定”筛选"
            : "人员快照中暂无" + P_SHORT + "成员";
    }
}

/** 筛选条件签名：任一条件变化都要回到第 1 页（关键字、状态勾选、企微部门、租户） */
function wxFilterSignature() {
    return [state.cfgId, state.wxKeyword, (state.wxStates || []).join(","),
        wxDeptFilterIds().join(","),
        state.wxOnlyChecked ? "1" : "0"].join("|");
}

/**
 * 保存“企微成员 → OA 人员”绑定：选择弹框里双击或点“确定”时立即调用（页面上不再有“绑定OA人员”按钮）。
 * 保存期间把该行选择框置灰（.wx-picker-busy），保存失败时回退行内选择框显示，避免看起来已绑定。
 * 若该 OA 人员当前是“自行绑定”（本人扫码建立），换绑前先二次确认。
 */
function saveWxOaBind(wxId, oaId) {
    wxId = String(wxId == null ? "" : wxId);
    oaId = String(oaId == null ? "" : oaId);
    if (!wxId || !oaId) { return; }
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var curBind = boundByOa[oaId];
    var curWxId = curBind && curBind.wx_user_id !== null && curBind.wx_user_id !== undefined
        ? String(curBind.wx_user_id) : "";
    if (isSelfBind(curBind) && curWxId !== wxId) {
        OaonesToast.confirm(SELF_BIND_CONFIRM_MSG, function () {
            doSaveWxOaBind(wxId, oaId);
        }, { danger: true });
        return;
    }
    doSaveWxOaBind(wxId, oaId);
}

/** 真正提交“企微成员 → OA 人员”绑定（saveWxOaBind 的落库部分，二次确认后调用）。 */
function doSaveWxOaBind(wxId, oaId) {
    var picker = document.querySelector("#wxBody .wx-picker[data-kind='oaperson'][data-wx='" + wxId + "']");
    if (picker) { picker.classList.add("wx-picker-busy"); }
    Oaones.api("user.save", {
        cfg_id: state.cfgId,
        oa_user_id: oaId,
        wx_user_id: wxId
    }, function (res) {
        if (picker) { picker.classList.remove("wx-picker-busy"); }
        if (res.code === 0) {
            delete wxOaPersonPick[String(wxId)];
            showToast("已绑定 OA 人员 " + oaPersonLabel(oaId));
            loadData();
        } else {
            showToast("绑定失败：" + res.msg, 2);
            // 保存失败：选择框回到“未选择”，不残留看起来已绑定的值
            delete wxOaPersonPick[String(wxId)];
            updateOaPersonPickerDom(wxId);
        }
    });
}

function toggleWxNoBind(btn, mark) {
    var wxId = btn.getAttribute("data-wx");
    if (!state.cfgId || !wxId) { return; }
    if (mark) {
        OaonesToast.confirm("确认将" + P_SHORT + "成员 " + wxId
            + " 设置为免绑定？免绑定成员不参与绑定选择，可取消免绑定后再次绑定。",
            function () { doToggleWxNoBind(btn, mark); });
        return;
    }
    doToggleWxNoBind(btn, mark);
}

function doToggleWxNoBind(btn, mark) {
    var wxId = btn.getAttribute("data-wx");
    btn.disabled = true;
    Oaones.api(mark ? "user.noBind" : "user.cancelNoBind", {
        cfg_id: state.cfgId,
        wx_user_id: wxId
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

/**
 * B 页（企微数据基准）“同步人员”：部门已绑定、人员未绑定时，
 * 按部门绑定关系把该企微成员新建到对应 OA 部门并自动建立人员绑定。
 */
function syncWxPerson(wxId) {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var wxUser = null;
    for (var i = 0; i < wxUsers.length; i++) {
        if (String(wxUsers[i].wx_user_id) === String(wxId)) { wxUser = wxUsers[i]; break; }
    }
    var name = wxUser ? (wxUser.wx_name || "") : "";
    var deptBind = deptBindOfWx(wxUser ? wxUser.wx_dept_id : "");
    OaonesToast.confirm("确认把" + P_SHORT + "成员" + (name ? "“" + name + "”" : "（ID " + wxId + "）")
            + "按部门绑定同步到 OA"
            + (deptBind ? "部门 " + deptBind.oa_dept_id : "")
            + "？将以其" + P_SHORT + "成员ID作为 OA 登录名新建账号并建立人员绑定。", function () {
        var btn = document.querySelector("#wxBody .sync-btn[data-wx='" + wxId + "']");
        if (btn) { btn.disabled = true; btn.textContent = "同步中…"; }
        Oaones.api("user.syncToOa", { cfg_id: state.cfgId, wx_user_id: wxId }, function (res) {
            if (btn) { btn.disabled = false; btn.textContent = "同步人员"; }
            if (res.code !== 0) { showToast("同步人员失败：" + res.msg, 2); return; }
            var data = res.data || {};
            showToast("已同步到 OA：新建 OA 账号 " + (data.oa_loginid || "")
                + "（ID " + (data.oa_user_id || "") + "），并已建立人员绑定");
            loadData();
        });
    });
}

/* ------------------------------------------------------------------
 * B 页（企微数据基准）行首复选框 + 批量操作（企微 → OA）。
 * 勾选多行后点击筛选栏“同步人员”，按部门绑定关系逐条把企微成员同步到 OA；
 * 同步完成后用弹框列表给出成功统计与失败原因（如“所在部门没有进行绑定，请手动创建人员或先进行部门绑定”）。
 * 同一批勾选也可点“批量免绑定”，一次把勾选的未绑定成员标记为免绑定。
 * ------------------------------------------------------------------ */

/** 勾选状态只保留当前列表内仍可同步（未绑定）的企微成员。 */
function pruneWxChecked(rows) {
    var keep = {};
    (rows || []).forEach(function (u) {
        var id = String(u.wx_user_id);
        if (wxCheckedIds[id] && wxStateOf(u) === "unbound") { keep[id] = true; }
    });
    wxCheckedIds = keep;
}

function clearWxChecked() {
    wxCheckedIds = {};
    updateWxCheckedState();
}

/** 当前筛选条件下已勾选（且可同步）的企微成员ID：含其他页已选，按列表顺序返回。 */
function checkedWxIds() {
    var ids = [];
    (wxSelectableIds || []).forEach(function (id) {
        if (wxCheckedIds[id]) { ids.push(String(id)); }
    });
    return ids;
}

function setSyncWxCheckedBtnBusy(busy, text) {
    var btn = byId("syncWxCheckedBtn");
    if (!btn) { return; }
    btn.disabled = !!busy;
    var tip = byId("wxCheckedTip");
    if (text == null) {
        btn.textContent = "同步人员";
        if (tip) { tip.textContent = ""; }
        updateWxCheckedState();
    } else {
        btn.textContent = text;
    }
}

/** 当前筛选条件下已勾选（可同步）的企微成员数量：跨页统计，用于“已选 N / 共 M”与跨页全选条 */
function wxCheckedCount() {
    var checked = 0;
    (wxSelectableIds || []).forEach(function (id) {
        if (wxCheckedIds[id]) { checked++; }
    });
    return checked;
}

/** 当前页已勾选（可同步）的企微成员数量：表头复选框只看当页 */
function wxPageCheckedCount() {
    var checked = 0;
    (wxPageSelectableIds || []).forEach(function (id) {
        if (wxCheckedIds[id]) { checked++; }
    });
    return checked;
}

/** 当前筛选条件下的可勾选企微成员是否已全部勾选（跨页） */
function wxAllSelected() {
    var total = (wxSelectableIds || []).length;
    return total > 0 && wxCheckedCount() >= total;
}

/** 表头复选框：只全选/取消全选当前页可同步的企微成员（当页全选）。 */
function toggleWxPageChecks(checked) {
    (wxPageSelectableIds || []).forEach(function (id) {
        if (checked) { wxCheckedIds[id] = true; } else { delete wxCheckedIds[id]; }
    });
    renderWxUnbound();
}

/** 统计行末尾的“仅显示已选择数据 / 显示所有数据”：只改列表显示范围，不动跨页勾选集合。 */
function toggleWxOnlyChecked() {
    state.wxOnlyChecked = !state.wxOnlyChecked;
    renderWxUnbound();
}

/** 同步勾选状态提示：按钮旁“已选 N / 共 M 人” + 表头当页全选框的选中/半选状态 + 表头上方跨页全选条。 */
function updateWxCheckedState() {
    var tip = byId("wxCheckedTip");
    var checked = wxCheckedCount();
    var total = (wxSelectableIds || []).length;
    if (tip) { tip.textContent = checked ? "已选 " + checked + " / 共 " + total + " 人（跨页保留）" : ""; }
    // 表头复选框只反映当前页（当页全选）
    var all = byId("wxCheckAll");
    OaonesTable.checkAllTitle(all, "可同步的" + P_SHORT + "成员");
    OaonesTable.syncCheckAll(all, wxPageCheckedCount(), (wxPageSelectableIds || []).length);
    // 表头上方的“跨页全选”条：左侧复选框与表头复选框同列对齐，勾选/取消即跨页全选/取消
    OaonesTable.crossPageBar(byId("wxTable"), {
        total: total,
        unit: "人",
        allSelected: wxAllSelected(),
        checked: checked,
        what: "可同步的" + P_SHORT + "成员",
        onChange: toggleAllWxChecks
    });
    // 统计行末尾的“仅显示已选择数据 / 显示所有数据”：同步“已选 N / 共 M”（行内勾选后不整表重绘也要准）
    OaonesTable.refreshListActions(byId("wxStats"), {
        total: total,
        unit: "人",
        what: "可同步的" + P_SHORT + "成员",
        onlyChecked: !!state.wxOnlyChecked,
        checked: checked
    });
}

/** 跨页全选（表头上方勾选框）：作用于当前筛选条件下全部可同步的行（含未翻到的页）。 */
function toggleAllWxChecks(checked) {
    (wxSelectableIds || []).forEach(function (id) {
        if (checked) { wxCheckedIds[id] = true; } else { delete wxCheckedIds[id]; }
    });
    // 立即刷新当前页行的勾选态（其他页的勾选态在翻页渲染时按 wxCheckedIds 回填）
    renderWxUnbound();
}

function syncCheckedWx() {
    if (wxSyncing) { return; }
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var ids = checkedWxIds();
    if (!ids.length) {
        showToast("请先勾选需要同步人员的" + P_SHORT + "成员（行首复选框），再点击“同步人员”", 1);
        return;
    }
    OaonesToast.confirm("确认把勾选的 " + ids.length + " 名" + P_SHORT + "成员按部门绑定关系同步到 OA 并建立人员绑定？", function () {
        wxSyncing = true;
        runSyncCheckedWx(ids, 0, [], []);
    });
}

/**
 * 批量“免绑定”：把勾选的（未绑定）企微成员一次提交给 user.batchNoBind。
 * 免绑定成员不参与绑定选择，可取消免绑定后再次绑定；
 * 已绑定 OA 人员的成员不在可勾选范围内（行首复选框对已绑定/免绑定成员不可用）。
 */
function batchNoBindCheckedWx() {
    if (wxSyncing) { return; }
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var ids = checkedWxIds();
    if (!ids.length) {
        showToast("请先勾选需要设置免绑定的" + P_SHORT + "成员（行首复选框），再点击“批量免绑定”", 1);
        return;
    }
    OaonesToast.confirm("确认把勾选的 " + ids.length
        + " 名" + P_SHORT + "成员设置为免绑定？免绑定成员不参与绑定选择，可取消免绑定后再次绑定。", function () {
        var btn = byId("batchNoBindWxBtn");
        if (btn) { btn.disabled = true; }
        Oaones.api("user.batchNoBind", {
            cfg_id: state.cfgId,
            wx_user_ids: ids.join(",")
        }, function (res) {
            if (btn) { btn.disabled = false; }
            if (res.code !== 0) {
                showToast("批量免绑定失败：" + res.msg, 2);
                return;
            }
            var data = res.data || {};
            showToast("批量免绑定成功，共 " + (data.success == null ? ids.length : data.success) + " 人");
            // 免绑定成员在列表里变为“免绑定”状态，勾选状态由下次渲染按行状态自动收敛
            loadData();
        });
    });
}

/**
 * 分片同步：每批 {@link WX_SYNC_CHUNK_SIZE} 人，逐批提交并刷新按钮进度，
 * 全部完成后统一弹出结果框（成功统计 + 失败人员列表）。
 */
function runSyncCheckedWx(pending, doneCount, successRows, failRows) {
    var chunk = pending.slice(0, WX_SYNC_CHUNK_SIZE);
    var rest = pending.slice(WX_SYNC_CHUNK_SIZE);
    var total = doneCount + pending.length;
    setSyncWxCheckedBtnBusy(true, "同步中 " + (doneCount + chunk.length) + "/" + total + "…");
    Oaones.api("user.syncToOaBatch", { cfg_id: state.cfgId, wx_ids: chunk.join(",") }, function (res) {
        if (res.code !== 0) {
            // 整个请求失败（如租户/网络异常）：本批人员统一记为失败，保留原因
            chunk.forEach(function (id) {
                failRows.push({
                    wx_user_id: String(id),
                    wx_name: wxNameByWxId(id),
                    wx_dept_name: wxDeptNameByWxId(id),
                    reason: "同步请求失败：" + (res.msg || "请稍后重试")
                });
            });
        } else {
            var data = res.data || {};
            (data.success_rows || []).forEach(function (r) { successRows.push(r); });
            (data.fail_rows || []).forEach(function (r) { failRows.push(r); });
        }
        if (rest.length) {
            runSyncCheckedWx(rest, doneCount + chunk.length, successRows, failRows);
            return;
        }
        finishSyncCheckedWx(successRows, failRows);
    });
}

function finishSyncCheckedWx(successRows, failRows) {
    wxSyncing = false;
    setSyncWxCheckedBtnBusy(false);
    // 成功成员已在服务端建立绑定，失败成员保持勾选，便于按提示处理后重试
    wxCheckedIds = {};
    failRows.forEach(function (r) { wxCheckedIds[String(r.wx_user_id)] = true; });
    renderWxUnbound();
    showWxSyncResult(successRows, failRows);
    var msg = "同步人员完成：成功 " + successRows.length + " 人";
    if (failRows.length) { msg += "，失败 " + failRows.length + " 人（详见结果框）"; }
    // 全部成功为普通提示；存在失败人员时按警告提示并延长显示时间
    showToast(msg, failRows.length ? 1 : 0, failRows.length ? 8000 : 4000);
    // 重新拉取绑定关系：成功成员立即显示为“已绑定”，失败成员仍保持勾选便于重试
    loadData();
}

/** 企微成员姓名 / 所在部门：页面已加载的快照里取，取不到显示“—”。 */
function wxNameByWxId(wxId) {
    wxId = String(wxId);
    for (var i = 0; i < wxUsers.length; i++) {
        if (String(wxUsers[i].wx_user_id) === wxId) { return wxUsers[i].wx_name || ""; }
    }
    return "";
}

function wxDeptNameByWxId(wxId) {
    wxId = String(wxId);
    for (var i = 0; i < wxUsers.length; i++) {
        if (String(wxUsers[i].wx_user_id) === wxId) { return wxUsers[i].wx_dept_name || ""; }
    }
    return "";
}

/** 结果弹框：成功统计 + 失败成员列表（企微成员 / 所在部门 / 失败原因）。 */
/**
 * B 页（企微数据基准）导出 Excel：导出当前列表的数据（“已绑定/未绑定/免绑定”状态筛选、
 * 关键字与“仅显示已选”都生效）。一行一条“企微成员 ⇄ OA 人员”绑定关系；
 * 未绑定/免绑定的成员各占一行，OA 人员列留空。
 */
function exportWxExcel() {
    var rows = wxVisibleRows();
    if (state.wxOnlyChecked) {
        rows = rows.filter(function (u) { return !!wxCheckedIds[String(u.wx_user_id)]; });
    }
    if (!rows.length) {
        showToast("当前列表没有可导出的数据，请调整筛选条件后重试", 1);
        return;
    }
    var data = [];
    rows.forEach(function (u) {
        var wxId = String(u.wx_user_id);
        var st = wxStateOf(u);
        var wxDeptId = String(u.wx_dept_id == null ? "" : u.wx_dept_id);
        var deptBind = deptBindOfWx(wxDeptId);
        var deptText;
        if (!wxDeptId || wxDeptId === "0") {
            deptText = P_SHORT + "成员未归属部门";
        } else if (deptBind) {
            // 同一企微部门可被多个 OA 部门绑定（一对多），与页面一致把该企微部门的全部 OA 部门列出
            var deptList = (deptBinds || []).filter(function (b) {
                return String(b.wx_dept_id) === wxDeptId;
            });
            if (!deptList.length) { deptList = [deptBind]; }
            deptText = "已绑定：" + deptList.map(function (b) {
                var id = String(b.oa_dept_id);
                return (oaDeptLabel(id) || ("OA 部门 " + id)) + "（" + id + "）";
            }).join("、");
        } else {
            deptText = "该" + P_SHORT + "部门未绑定 OA 部门";
        }
        var status = st === "bound" ? "已绑定" : (st === "nobind" ? "免绑定" : "未绑定");
        if (st === "bound") {
            var boundRows = boundListByWx[wxId] || [];
            if (!boundRows.length && boundByWx[wxId]) { boundRows = [boundByWx[wxId]]; }
            if (!boundRows.length) { boundRows = [null]; }
            boundRows.forEach(function (b) {
                var oaId = b ? String(b.oa_user_id == null ? "" : b.oa_user_id) : "";
                data.push([
                    u.wx_name || "",
                    wxId,
                    u.wx_dept_name || "",
                    oaId ? (b.oa_name || oaNameOf(oaId)) : "",
                    oaId,
                    deptText,
                    status
                ]);
            });
        } else {
            data.push([u.wx_name || "", wxId, u.wx_dept_name || "", "", "", deptText, status]);
        }
    });
    var ok = OaonesExcel.export({
        filename: P_SHORT + "数据基准_" + OaonesExcel.stamp() + ".xlsx",
        sheet: P_SHORT + "数据基准",
        columns: [
            { title: P_SHORT + "成员", width: 16 },
            { title: P_SHORT + "成员ID", width: 16 },
            { title: P_SHORT + "部门", width: 20 },
            { title: "OA 人员", width: 16 },
            { title: "OA 人员ID", width: 14 },
            { title: "部门绑定", width: 32 },
            { title: "状态", width: 10 }
        ],
        rows: data
    });
    if (ok) { showToast("已导出 " + data.length + " 条" + P_SHORT + "成员数据"); }
}

function showWxSyncResult(successRows, failRows) {
    successRows = successRows || [];
    failRows = failRows || [];
    var html = ""
        + "<div class='sync-result-summary'>"
        + "<span class='bind-stat'><span>本次同步</span><b>" + (successRows.length + failRows.length) + "</b> 人</span>"
        + "<span class='bind-stat'><span>同步成功</span><b>" + successRows.length + "</b> 人</span>"
        + "<span class='bind-stat'><span>同步失败</span><b class='fail-num'>" + failRows.length + "</b> 人</span>"
        + "</div>";
    if (successRows.length) {
        html += "<div class='sync-result-note muted'>成功明细：已在 OA 新建账号 " + successRows.length
            + " 人，并已自动建立人员绑定。</div>";
    }
    if (failRows.length) {
        html += "<div class='sync-result-title'>以下 <b>" + failRows.length
            + "</b> 人同步失败，请按失败原因处理后重试（失败人员已在列表中保持勾选）：</div>"
            + "<div class='sync-result-list'>"
            + "<div class='sync-result-head'><span>" + P_SHORT + "成员</span><span>所在部门</span><span>失败原因</span></div>";
        failRows.forEach(function (r) {
            var wxId = String(r.wx_user_id == null ? "" : r.wx_user_id);
            var name = r.wx_name ? r.wx_name + "（" + wxId + "）" : P_SHORT + "成员（" + wxId + "）";
            html += "<div class='sync-result-row'>"
                + "<span class='sync-result-name' title='" + esc(name) + "'>" + esc(name) + "</span>"
                + "<span class='sync-result-dept' title='" + esc(r.wx_dept_name || "") + "'>"
                + esc(r.wx_dept_name || "—") + "</span>"
                + "<span class='sync-result-reason'>" + esc(r.reason || "同步人员失败，请稍后重试。") + "</span>"
                + "</div>";
        });
        html += "</div>";
    } else {
        html += "<div class='sync-result-all-ok'>全部同步成功，无失败人员。</div>";
    }
    byId("wxSyncResultBody").innerHTML = html;
    byId("wxSyncResultRetryBtn").style.display = failRows.length ? "" : "none";
    byId("wxSyncResultMask").style.display = "block";
    byId("wxSyncResultModal").style.display = "block";
}

function hideWxSyncResult() {
    var mask = byId("wxSyncResultMask");
    var modal = byId("wxSyncResultModal");
    if (mask) { mask.style.display = "none"; }
    if (modal) { modal.style.display = "none"; }
}

/** 重试失败人员：关闭结果框，仅保留失败人员勾选后再次同步。 */
function retrySyncCheckedWx() {
    hideWxSyncResult();
    if (wxSyncing) { return; }
    if (!checkedWxIds().length) {
        showToast("没有可重试的失败人员", 1);
        return;
    }
    syncCheckedWx();
}

/* 批量“同步人员”（企微 → OA）相关事件绑定 */
byId("syncWxCheckedBtn").addEventListener("click", syncCheckedWx);
byId("batchNoBindWxBtn").addEventListener("click", batchNoBindCheckedWx);
// 统计行末尾的操作组：仅显示已选择数据（按钮由渲染时拼进统计行，这里用事件委托接住）
// 跨页全选已挪到表头上方的勾选框（OaonesTable.crossPageBar，见 updateWxCheckedState）
byId("wxStats").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("only-checked-btn")) {
        toggleWxOnlyChecked();
    }
});
// 表头复选框：只全选当页
byId("wxCheckAll").addEventListener("change", function () { toggleWxPageChecks(this.checked); });
byId("wxBody").addEventListener("change", function (e) {
    var t = e.target;
    if (!t || !t.classList || !t.classList.contains("wx-row-check")) { return; }
    var id = String(t.getAttribute("data-wx"));
    if (t.checked) { wxCheckedIds[id] = true; } else { delete wxCheckedIds[id]; }
    // “仅显示已选择数据”时取消勾选要立即把该行移出列表，否则只同步勾选态与统计行
    if (state.wxOnlyChecked) { renderWxUnbound(); } else { updateWxCheckedState(); }
});
byId("wxSyncResultOkBtn").addEventListener("click", hideWxSyncResult);
byId("wxSyncResultCloseBtn").addEventListener("click", hideWxSyncResult);
byId("wxSyncResultRetryBtn").addEventListener("click", retrySyncCheckedWx);
byId("wxSyncResultMask").addEventListener("click", hideWxSyncResult);

</script>
