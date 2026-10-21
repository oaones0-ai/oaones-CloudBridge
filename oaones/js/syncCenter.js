/**
 * 【已停用】本脚本不再被任何页面引入（保留仅供对照历史实现，可直接删除）。
 *
 * 同步中心页面（/oaones/syncCenter/syncCenter.jsp）现在只保留“同步设置（计划任务）”与
 * “编辑同步设置”两块，页面脚本改为只加载 /oaones/js/syncPlan.js：
 *   * 同步范围选择（OA 分部 / OA 部门 / 平台部门）→ syncPlan.js 第六节（弹框标记在 syncPlanPanel.jsp）；
 *   * 同步明细弹窗（设置表行尾“结果详情”）→ syncPlan.js 第七节；
 *   * “按范围同步”的预览/执行入口与“同步日志”列表已从页面上移除。
 *
 * ---- 以下为停用前的说明（历史实现） ----
 * 同步中心通用页面脚本（/oaones/syncCenter/syncCenter.jsp，企微 / 钉钉 / 飞书共用一份）。
 *
 * 平台信息由页壳注入（见 syncCenter.jsp）：
 *   window.OAONES_SYNC_TYPE    0 企微 / 1 钉钉 / 2 飞书（同步设置与计划任务按它区分）
 *   window.OAONES_SYNC_SHORT   平台简称：企微 / 钉钉 / 飞书（本文件里的 SC_SHORT）
 *   window.OAONES_SYNC_LABEL   平台全称：企业微信 / 钉钉 / 飞书（本文件里的 SC_FULL）
 *   window.OAONES_API_URL      该平台的 api.jsp（WeCom / DingTalk / Feishu）
 *   window.OAONES_CFG_ID       地址上的 cfg_id（从租户工作区进来时锁定该租户）
 * 页面里所有平台文案（如“企微部门”“企业微信租户”）都由 SC_SHORT / SC_FULL 拼出来，
 * 因此同一份脚本在三个平台下都能用；DOM 结构来自各平台的 syncPanel.jsp（三个平台的 id 完全一致）。
 */
var SC_SHORT = window.OAONES_SYNC_SHORT || "企微";
var SC_FULL = window.OAONES_SYNC_LABEL || "企业微信";

"use strict";
function byId(id) { return document.getElementById(id); }
/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/js/toast.js 弹出。
 */
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}
function showModal(maskId, modalId) {
    byId(maskId).style.display = "block";
    byId(modalId).style.display = "block";
}
function hideModal(maskId, modalId) {
    byId(maskId).style.display = "none";
    byId(modalId).style.display = "none";
}

/** 页面状态：租户、同步范围（OA 分部/部门 + 企微部门）、同步日志与明细 */
var state = {
    cfgs: [],
    cfgId: "",
    direction: "",
    subs: [],
    depts: [],
    wxDepts: [],
    subIds: [],
    deptIds: [],
    wxDeptId: "",
    /** OA 分部选择框是否为单选（企微→OA 时 OA 是目标：目标分部必选一个，目标含下级） */
    subSingle: false,
    /** OA 部门选择框是否为单选（企微→OA 时 OA 是目标：目标部门只能选一个，或者为空） */
    deptSingle: false,
    preview: null,
    running: false,
    logRows: [],
    logKeyword: "",
    detail: { rows: [], type: "all", keyword: "", log: null }
};

/**
 * 第一行「同步方向」变化：按方向决定第二行（前者/来源）、第三行（后者/目标）放哪些选择框，
 * 并把 OA 分部 / OA 部门选择框切换为多选（OA→企微，OA 是来源）或单选
 * （企微→OA，OA 是目标：目标分部必选一个、目标部门可不选）。
 */
function renderDirection() {
    var dir = byId("direction").value;
    // 切换方向先清空两侧选择，避免把上一个方向的“来源/目标”带过来
    if (String(state.direction) !== String(dir)) {
        state.subIds = [];
        state.deptIds = [];
        state.wxDeptId = "";
        state.preview = null;
        byId("summaryArea").style.display = "none";
    }
    state.direction = dir;
    if (!dir) {
        byId("syncRowFormer").style.display = "none";
        byId("syncRowLatter").style.display = "none";
        byId("syncRowRun").style.display = "none";
        renderScopeInputs();
        return;
    }
    var oaToWx = dir === "0";
    state.subSingle = !oaToWx;
    state.deptSingle = !oaToWx;
    // 前者：OA→企微 是 OA 范围（多选）；企微→OA 是企微范围（租户 + 企微部门）
    byId("syncFormerLabel").textContent = oaToWx ? "OA 范围（来源）" : SC_SHORT + "范围（来源）";
    // 后者：OA→企微 是企微目标；企微→OA 是 OA 目标
    byId("syncLatterLabel").textContent = oaToWx ? SC_SHORT + "目标（目标）" : "OA 目标（目标）";
    var formerSlot = byId("syncRowFormer");
    var latterSlot = byId("syncRowLatter");
    var oaBlock = byId("oaScopeBlock");
    var wxBlock = byId("wxScopeBlock");
    if (oaToWx) {
        formerSlot.appendChild(oaBlock);
        latterSlot.appendChild(wxBlock);
    } else {
        formerSlot.appendChild(wxBlock);
        latterSlot.appendChild(oaBlock);
    }
    oaBlock.style.display = "inline-flex";
    wxBlock.style.display = "inline-flex";
    // 两侧都是“先选分部、再选部门”：OA→企微 的分部用来筛选来源部门；
    // 企微→OA 的分部是目标根（单选必选），目标部门非必选、候选只列该分部（含下级分部）下的部门
    byId("oaSubLabel").style.display = "";
    byId("syncSubPicker").style.display = "";
    byId("oaSubLabel").textContent = oaToWx ? "OA 分部" : "OA 目标分部";
    byId("syncSubPicker").title = oaToWx
        ? "点击选择 OA 分部（可多选，含下级分部）"
        : "点击选择 OA 目标分部（单选必选，新建部门挂在该分部下）";
    byId("syncSubInput").placeholder = oaToWx ? "选择 OA 分部（可多选）" : "选择 OA 目标分部（单选必选）";
    byId("syncSubAllBtn").style.display = state.subSingle ? "none" : "";
    byId("oaDeptLabel").textContent = oaToWx ? "OA 部门" : "OA 目标部门";
    byId("syncDeptPicker").title = oaToWx
        ? "点击选择 OA 部门（可多选，含下级部门）"
        : "点击选择 OA 目标部门（单选，可不选；先选目标分部）";
    byId("syncDeptAllBtn").style.display = state.deptSingle ? "none" : "";
    byId("syncDeptNoteText").innerHTML = state.deptSingle
        ? SC_SHORT + "→OA 时 OA 是目标：只能选一个部门，<span class='rule'>也可以不选</span>"
            + "（不选 = 新建部门直接挂在该目标分部下、成为该分部的顶级部门）。"
            + "单击即选中（再点其它部门即更换），双击即确定并退出。"
        : "可多选，<span class='rule'>选中部门自动含其下级部门</span>；已选分部时只列出该分部（含下级分部）下的部门。单击选中/取消，双击即确定并退出。";
    byId("syncDeptInput").placeholder = state.deptSingle
        ? "选择 OA 目标部门（单选，可不选）" : "选择 OA 部门（可多选）";
    byId("syncDeptModalTitle").textContent = oaToWx ? "选择 OA 部门" : "选择 OA 目标部门";
    byId("syncRowFormer").style.display = "";
    byId("syncRowLatter").style.display = "";
    byId("syncRowRun").style.display = "";
    renderScopeInputs();
}

/* ==================================================================
 * 一、选择范围：OA 分部 / OA 部门 / 企微部门
 *   弹框骨架（拖动、列表式/树状切换、树节点展开收起、全选按钮）由 js/picker.js 提供
 * ================================================================== */

function childMapOf(rows, parentKey) {
    var map = {};
    (rows || []).forEach(function (r) {
        var p = String(r[parentKey] == null ? "" : r[parentKey]);
        if (!map[p]) { map[p] = []; }
        map[p].push(String(r.id));
    });
    return map;
}

function descendantsOf(id, map) {
    var out = [];
    var seen = {};
    var stack = [String(id)];
    while (stack.length) {
        var cur = stack.pop();
        if (seen[cur]) { continue; }
        seen[cur] = true;
        out.push(cur);
        (map[cur] || []).forEach(function (c) { stack.push(c); });
    }
    return out;
}

function expandIds(ids, map) {
    var out = [];
    var seen = {};
    (ids || []).forEach(function (id) {
        descendantsOf(id, map).forEach(function (v) {
            if (!seen[v]) { seen[v] = true; out.push(v); }
        });
    });
    return out;
}

function rowText(row, keys) {
    return keys.map(function (key) {
        return String(row[key] == null ? "" : row[key]);
    }).join(" ");
}

function filterRows(rows, keyword, keys) {
    var k = (keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (r) {
        return rowText(r, keys).toLowerCase().indexOf(k) >= 0;
    });
}

/** 候选行 HTML（默认两列：ID + 名称），列表式与树状共用 */
function optionHtml(id, name, active, partial, tip, tagHtml) {
    var cls = "wx-pick-option" + (active ? " active" : (partial ? " partial" : ""));
    return "<button type='button' class='" + cls + "' data-id='" + Oaones.esc(id) + "'"
        + " title='" + Oaones.esc(tip || "") + "'>"
        + "<span>" + Oaones.esc(id) + "</span>"
        + "<span class='wx-pick-opt-name'>" + Oaones.esc(name)
        + (tagHtml || "") + "</span>"
        + "</button>";
}

function listHeadHtml(first, second) {
    return "<div class='wx-pick-head'><span>" + Oaones.esc(first) + "</span><span>"
        + Oaones.esc(second) + "</span></div>";
}

/* ---------------- OA 分部（多选，选中含下级） ---------------- */

function subChildMap() { return childMapOf(state.subs, "parent"); }

function subLabel(id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < state.subs.length; i++) {
        if (String(state.subs[i].id) === key) {
            return state.subs[i].name || state.subs[i].fullname || "";
        }
    }
    return "";
}

function renderSubList() {
    var keyword = byId("syncSubSearch").value;
    var rows = filterRows(state.subs, keyword, ["id", "code", "name", "fullname"]);
    var mode = OaonesPicker.mode("syncSub");
    var partial = OaonesPicker.partialSelected(state.subIds, subChildMap);
    var el = byId("syncSubList");
    // 企微→OA 时 OA 是目标：分部单选必选（目标根），说明文案按方向切换
    byId("syncSubModalTitle").textContent = state.subSingle ? "选择 OA 目标分部" : "选择 OA 分部";
    byId("syncSubNoteText").innerHTML = state.subSingle
        ? SC_SHORT + "→OA 时 OA 是目标：<span class='rule'>只能选一个分部（必选）</span>，"
            + "该分部下全部部门（含下级分部）都在目标范围内；目标部门<span class='rule'>可不选</span>"
            + "（不选 = 新建部门直接挂在该分部下、成为该分部的顶级部门）。"
            + "单击即选中（再点其它分部即更换），双击即确定并退出。"
        : "可多选，<span class='rule'>选中分部自动含其下级分部</span>（该分部下全部部门一并纳入同步范围）；"
            + "单击选中/取消，双击即确定并退出。";
    if (mode === "tree") {
        el.className = "wx-pick-list is-tree";
        el.innerHTML = OaonesPicker.renderTree("syncSub", rows, {
            parentOf: function (s) { return s.parent; },
            textOf: function (s) { return rowText(s, ["id", "code", "name", "fullname"]); },
            keyword: keyword,
            emptyText: "没有匹配的分部",
            optionHtml: function (s) { return subOptionHtml(s, partial); }
        });
    } else {
        el.className = "wx-pick-list";
        var html = rows.length ? listHeadHtml("ID", "分部") : "";
        rows.forEach(function (s) { html += subOptionHtml(s, partial); });
        el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的分部</div>";
    }
    syncSubAllState();
}

function subOptionHtml(s, partial) {
    var id = String(s.id);
    var active = state.subIds.indexOf(id) >= 0;
    var tip = state.subSingle
        ? ((active ? "已选中该目标分部（点其它分部即更换）" : "点击选择该分部作为 OA 目标")
            + "；目标含该分部的下级分部与部门")
        : ((active ? "点击取消选择" : "点击选择") + "；选中分部会自动选中其全部下级分部");
    tip += "｜" + (s.fullname || s.name || "");
    return optionHtml(id, s.name || s.fullname || "", active, !active && partial[id], tip);
}

function syncSubAllState() {
    // 单选（企微→OA 目标分部）时没有“全选”口径，按钮已隐藏，这里同步为未全选
    if (state.subSingle) {
        OaonesPicker.syncSelectAll("syncSub", false);
        return false;
    }
    var keyword = byId("syncSubSearch").value;
    var ids = filterRows(state.subs, keyword, ["id", "code", "name", "fullname"])
        .map(function (s) { return String(s.id); });
    var allOn = ids.length > 0 && ids.every(function (id) { return state.subIds.indexOf(id) >= 0; });
    OaonesPicker.syncSelectAll("syncSub", allOn);
    return allOn;
}

OaonesPicker.register("syncSub", renderSubList);
OaonesPicker.setSelectAll("syncSub", function () {
    if (state.subSingle) { return false; }
    var keyword = byId("syncSubSearch").value;
    var ids = filterRows(state.subs, keyword, ["id", "code", "name", "fullname"])
        .map(function (s) { return String(s.id); });
    var on = syncSubAllState();
    var next = [];
    if (on) {
        var drop = {};
        ids.forEach(function (v) { drop[v] = true; });
        next = state.subIds.filter(function (v) { return !drop[v]; });
    } else {
        next = state.subIds.concat(ids);
    }
    state.subIds = uniqueIds(next);
    renderSubList();
    return syncSubAllState();
});

/** 去重（保持顺序） */
function uniqueIds(ids) {
    var seen = {};
    var out = [];
    (ids || []).forEach(function (id) {
        var v = String(id);
        if (!seen[v]) { seen[v] = true; out.push(v); }
    });
    return out;
}

/**
 * 单击选中/取消（只改选中态，不重建列表，避免打断双击）。
 *
 * <p>只记录“用户明确选中的分部/部门”，其下级由后端按范围自动展开（页面提示里已写明含下级），
 * 这样回显的名称就是用户自己选的那几个。</p>
 *
 * <p>企微→OA 时 OA 是目标：目标分部<b>单选必选</b>（点一行即选中、再点其它行即更换、点同一行不取消，
 * 清空用输入框的 ×）；换了目标分部后目标部门候选随之改变，已选目标部门直接清空。</p>
 */
function toggleSub(id) {
    var key = String(id);
    if (state.subSingle) {
        if (state.subIds.length === 1 && state.subIds[0] === key) { return; }
        state.subIds = [key];
        state.deptIds = [];
        return;
    }
    var idx = state.subIds.indexOf(key);
    if (idx >= 0) {
        state.subIds = state.subIds.filter(function (v) { return v !== key; });
    } else {
        state.subIds = state.subIds.concat([key]);
    }
}

/* ---------------- OA 部门（多选，选中含下级；候选受已选分部过滤） ---------------- */

function deptChildMap() { return childMapOf(state.depts, "parent"); }

function deptLabel(id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < state.depts.length; i++) {
        if (String(state.depts[i].id) === key) {
            return state.depts[i].name || state.depts[i].fullname || "";
        }
    }
    return "";
}

/** 已选分部（含下级）集合：部门候选只显示这些分部下的部门 */
function selectedSubScope() {
    return expandIds(state.subIds, subChildMap());
}

/**
 * OA 部门候选的前置条件：两侧都按“已选分部（含下级分部）”过滤。
 *
 * <p>企微→OA 时 OA 是目标：目标部门候选只列已选<b>目标分部</b>（含下级分部）下的部门，
 * 未选目标分部时没有候选（提示先去选目标分部）；目标部门本身可不选。</p>
 */
function deptPrecondition() {
    var subs = selectedSubScope();
    if (!subs.length) {
        if (state.deptSingle) {
            return {
                tip: SC_SHORT + "→OA：请先选择 OA 目标分部（单选必选）——目标部门候选只列出该分部"
                    + "（含下级分部）下的部门；目标部门可不选（不选 = 新建部门直接挂在该分部下）",
                filter: function () { return false; }
            };
        }
        return { tip: "未选分部时显示全部部门；先选分部后，这里只显示该分部（含下级）下的部门" };
    }
    return {
        tip: state.deptSingle
            ? "仅显示已选目标分部「" + state.subIds.map(subLabel).join("、") + "」（含下级分部）下的部门；"
                + "目标部门可不选（不选 = 新建部门直接挂在该分部下）"
            : "仅显示已选 " + state.subIds.length + " 个分部（含下级）下的部门",
        filter: function (d) {
            return subs.indexOf(String(d.subcompanyid1 == null ? "" : d.subcompanyid1)) >= 0;
        }
    };
}

OaonesPicker.setPrecondition("syncDept", deptPrecondition);

function renderDeptList() {
    var keyword = byId("syncDeptSearch").value;
    var filtered = filterRows(state.depts, keyword, ["id", "code", "name", "fullname"]);
    // 列表式要手动套前置条件（树状由 renderTree 内部套）：两种方向都按“已选分部（含下级）”过滤
    var rows = OaonesPicker.applyPrecondition("syncDept", filtered);
    var mode = OaonesPicker.mode("syncDept");
    var partial = OaonesPicker.partialSelected(state.deptIds, deptChildMap);
    var el = byId("syncDeptList");
    if (mode === "tree") {
        el.className = "wx-pick-list is-tree";
        el.innerHTML = OaonesPicker.renderTree("syncDept", rows, {
            parentOf: function (d) { return d.parent; },
            textOf: function (d) { return rowText(d, ["id", "code", "name", "fullname"]); },
            keyword: keyword,
            emptyText: "没有匹配的部门",
            optionHtml: function (d) { return deptOptionHtml(d, partial); }
        });
    } else {
        el.className = "wx-pick-list";
        var html = rows.length ? listHeadHtml("ID", "部门") : "";
        rows.forEach(function (d) { html += deptOptionHtml(d, partial); });
        el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的部门</div>";
    }
    OaonesPicker.preconditionTip("syncDept", "syncDeptNote");
    syncDeptAllState();
}

function deptOptionHtml(d, partial) {
    var id = String(d.id);
    var active = state.deptIds.indexOf(id) >= 0;
    var tip = state.deptSingle
        ? ((active ? "已选中该目标部门（点其它部门即更换）" : "点击选择该部门作为 OA 目标")
            + "；目标部门可不选，不选 = 新建部门直接挂在目标分部下")
        : ((active ? "点击取消选择" : "点击选择") + "；选中部门会自动选中其全部下级部门");
    tip += "｜" + (d.fullname || d.name || "");
    return optionHtml(id, d.name || d.fullname || "", active, !active && partial[id], tip);
}

function deptAllIds() {
    var keyword = byId("syncDeptSearch").value;
    if (state.deptSingle) { return []; }
    return OaonesPicker.applyPrecondition("syncDept",
            filterRows(state.depts, keyword, ["id", "code", "name", "fullname"]))
        .map(function (d) { return String(d.id); });
}

function syncDeptAllState() {
    var ids = deptAllIds();
    if (state.deptSingle) {
        OaonesPicker.syncSelectAll("syncDept", false);
        return false;
    }
    var allOn = ids.length > 0 && ids.every(function (id) { return state.deptIds.indexOf(id) >= 0; });
    OaonesPicker.syncSelectAll("syncDept", allOn);
    return allOn;
}

OaonesPicker.register("syncDept", renderDeptList);
OaonesPicker.setSelectAll("syncDept", function () {
    var ids = deptAllIds();
    var on = syncDeptAllState();
    var next = [];
    if (on) {
        var drop = {};
        ids.forEach(function (v) { drop[v] = true; });
        next = state.deptIds.filter(function (v) { return !drop[v]; });
    } else {
        next = state.deptIds.concat(ids);
    }
    state.deptIds = uniqueIds(next);
    renderDeptList();
    return syncDeptAllState();
});

function toggleDept(id) {
    var key = String(id);
    if (state.deptSingle) {
        // 目标单选：点一行即选中（再点其它部门即更换），不提供“全不选”，清空用输入框的 ×
        state.deptIds = [key];
        return;
    }
    var idx = state.deptIds.indexOf(key);
    if (idx >= 0) {
        state.deptIds = state.deptIds.filter(function (v) { return v !== key; });
    } else {
        state.deptIds = state.deptIds.concat([key]);
    }
}

/* ---------------- 企微部门（单选；留空 = root 节点） ---------------- */

function wxDeptChildMap() { return childMapOf(state.wxDepts, "parentid"); }

function wxDeptLabel(id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < state.wxDepts.length; i++) {
        if (String(state.wxDepts[i].id) === key) {
            return state.wxDepts[i].name || "";
        }
    }
    return "";
}

function renderWxList() {
    var keyword = byId("syncWxSearch").value;
    var rows = filterRows(state.wxDepts, keyword, ["id", "name"]);
    var mode = OaonesPicker.mode("syncWx");
    var el = byId("syncWxList");
    if (mode === "tree") {
        el.className = "wx-pick-list is-tree";
        el.innerHTML = OaonesPicker.renderTree("syncWx", rows, {
            parentOf: function (d) { return d.parentid; },
            textOf: function (d) { return rowText(d, ["id", "name"]); },
            keyword: keyword,
            emptyText: "没有匹配的" + SC_SHORT + "部门",
            optionHtml: wxDeptOptionHtml
        });
    } else {
        el.className = "wx-pick-list";
        var html = rows.length ? listHeadHtml("ID", SC_SHORT + "部门") : "";
        rows.forEach(function (d) { html += wxDeptOptionHtml(d); });
        el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的" + SC_SHORT + "部门</div>";
    }
}

function wxDeptOptionHtml(d) {
    var id = String(d.id);
    var active = String(state.wxDeptId) === id;
    var tip = (active ? "点击取消选择" : "点击选择")
        + "；选中后本次同步以该部门（含下级部门）为" + SC_SHORT + "侧节点"
        + (String(d.no_bind) === "0" ? "；该部门已标记免绑定（只作目标，不建立部门绑定）" : "");
    return optionHtml(id, d.name || (SC_SHORT + "部门 " + id), active, false, tip);
}

function toggleWxDept(id) {
    var key = String(id);
    state.wxDeptId = String(state.wxDeptId) === key ? "" : key;
    renderWxList();
}

OaonesPicker.register("syncWx", renderWxList);

/* ==================================================================
 * 二、范围显示与数据加载
 * ================================================================== */

/** 范围输入框文案（三处选择框的“已选”回显） */
function renderScopeInputs() {
    // 租户上下文（带 cfg_id 从某个租户点“同步中心”进来）：租户锁定，只读展示、点不出租户列表
    var cfgLocked = Oaones.cfgLocked();
    byId("syncCfgPicker").classList.toggle("locked", cfgLocked);
    byId("syncCfgInput").title = cfgLocked
        ? "当前租户由“" + SC_SHORT + "租户”列表点开决定，本页不能切换租户" : "";
    byId("syncCfgInput").value = state.cfgId ? cfgLabel(state.cfgId) : "";
    byId("syncCfgPicker").classList.toggle("has-value", !!state.cfgId);
    byId("syncSubInput").value = state.subIds.length === 1
        ? subLabel(state.subIds[0]) + "（ID " + state.subIds[0]
            + (state.subSingle ? "，目标含下级" : "") + "）"
        : (state.subIds.length > 1 ? "已选 " + state.subIds.length + " 个分部（含下级）" : "");
    byId("syncDeptInput").value = state.deptIds.length === 1
        ? deptLabel(state.deptIds[0]) + "（ID " + state.deptIds[0] + "）"
        : (state.deptIds.length > 1 ? "已选 " + state.deptIds.length + " 个部门（含下级）" : "");
    byId("syncWxDeptInput").value = state.wxDeptId
        ? wxDeptLabel(state.wxDeptId) + "（ID " + state.wxDeptId + "，含下级部门）" : "";
    byId("syncSubPicker").classList.toggle("has-value", state.subIds.length > 0);
    byId("syncDeptPicker").classList.toggle("has-value", state.deptIds.length > 0);
    byId("syncWxDeptPicker").classList.toggle("has-value", !!state.wxDeptId);
    renderScopeTip();
}

/** 租户名称（取不到时退化为 #租户ID） */
function cfgLabel(cfgId) {
    var key = String(cfgId == null ? "" : cfgId);
    for (var i = 0; i < state.cfgs.length; i++) {
        if (String(state.cfgs[i].id) === key) {
            var name = state.cfgs[i].corp_name || ("#" + key);
            return String(state.cfgs[i].sync_enable) === "1" ? name : (name + "（已停用）");
        }
    }
    return key ? ("#" + key) : "";
}

/** 范围提示行：说明本次“来源 → 目标”，避免用户在没看清范围时点同步 */
function renderScopeTip() {
    var tip = byId("syncScopeTip");
    if (!byId("direction").value) {
        tip.textContent = "请先在第一行选择同步方向；选择后才出现“前者（来源）”与“后者（目标）”的选择框。";
        return;
    }
    var oaToWx = byId("direction").value === "0";
    if (!state.cfgId) {
        tip.textContent = "请选择" + SC_FULL + "（必选一个，不能“全部租户”）。";
        return;
    }
    var oaText = [];
    if (state.subIds.length) {
        oaText.push("OA 分部「" + state.subIds.map(subLabel).join("、") + "」");
    }
    if (state.deptIds.length) {
        oaText.push("OA 部门「" + state.deptIds.map(deptLabel).join("、") + "」");
    }
    var wxText = state.wxDeptId
        ? SC_SHORT + "部门「" + wxDeptLabel(state.wxDeptId) + "」（含下级部门）"
        : SC_SHORT + " root 节点（含其下全部部门）";
    if (oaToWx) {
        if (!state.subIds.length && !state.deptIds.length) {
            tip.textContent = "请在第二行选择 OA 范围（OA 分部 / OA 部门，都含下级，至少选一个）。";
            return;
        }
        tip.textContent = "本次同步范围：" + oaText.join("、") + "（含下级数据） → "
            + wxText + "（前者选中的多部门全部挂在后者下方）";
    } else {
        if (!state.subIds.length) {
            tip.textContent = "请先在第三行选择 OA 目标分部（单选、必选）：新建的 OA 部门挂在该分部下；"
                + "再选目标部门（非必选）可把新建部门挂到该分部的某个部门下。";
            return;
        }
        tip.textContent = "本次同步范围：" + wxText + " → OA 目标分部「" + subLabel(state.subIds[0])
            + "」（含下级分部与部门）"
            + (state.deptIds.length
                ? " + 目标部门「" + state.deptIds.map(deptLabel).join("、") + "」（含下级部门）"
                : "（未指定目标部门：新建部门直接挂在该分部下、成为该分部的顶级部门）");
    }
}

function syncParams() {
    return {
        cfg_id: state.cfgId,
        direction: byId("direction").value,
        oa_sub_ids: state.subIds.join(","),
        oa_dept_ids: state.deptIds.join(","),
        wx_dept_id: state.wxDeptId
    };
}

function loadCfgs() {
    // cfg.myList：仅返回当前登录人可维护（或未指定维护人）的租户，实现租户分权
    Oaones.api("cfg.myList", {}, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        state.cfgs = res.data || [];
        var locked = Oaones.cfgLocked();
        var tip = byId("cfgPermTip");
        var wanted = String(window.OAONES_CFG_ID || "");
        var lockedCfgFound = !locked;
        for (var j = 0; locked && j < state.cfgs.length; j++) {
            if (String(state.cfgs[j].id) === wanted) { lockedCfgFound = true; break; }
        }
        if (locked && !lockedCfgFound) {
            // 锁定租户不在本人可维护范围内：只给提示（本页不能切换租户）
            tip.textContent = "当前租户（#" + Oaones.lockedCfgId() + "）不在你可以维护的" + SC_SHORT + "租户范围内，本页不能切换租户；"
                + "请联系管理员把你加入该租户的维护人，或回“" + SC_SHORT + "租户”列表重新点开另一个租户。";
            tip.style.display = "";
        } else if (!state.cfgs.length) {
            tip.textContent = OaonesTips.pick(
                "当前还没有任何" + SC_SHORT + "租户，请先到“" + SC_SHORT + "租户配置”页新建租户，再回到本页执行同步。",
                "当前登录人不是任何" + SC_SHORT + "租户的维护人，无法选择租户执行同步；"
                    + "请联系管理员在“" + SC_SHORT + "租户配置”页把本人加入该租户的维护人。");
            tip.style.display = "";
        } else {
            tip.style.display = "none";
        }
        // 租户上下文（带 cfg_id 从某个租户点“同步中心”进来）：锁定为这个租户、不能切换；
        // 不带 cfg_id 时要求必须指定一个租户（本页不提供“全部租户”口径）
        var picked = "";
        if (locked) {
            picked = Oaones.lockedCfgId();
        } else if (wanted) {
            for (var i = 0; i < state.cfgs.length; i++) {
                if (String(state.cfgs[i].id) === wanted) { picked = wanted; break; }
            }
        }
        applyCfg(picked);
    });
}

/**
 * 应用当前租户：清空同步范围与选择框候选，重新加载组织选项与日志。
 *
 * <p>租户必须指定一个（不能“全部租户”）：未选择时范围选择与日志都提示先选租户。</p>
 */
function applyCfg(cfgId) {
    state.cfgId = String(cfgId == null ? "" : cfgId);
    state.subIds = [];
    state.deptIds = [];
    state.wxDeptId = "";
    state.subs = [];
    state.depts = [];
    state.wxDepts = [];
    state.preview = null;
    byId("summaryArea").style.display = "none";
    renderScopeInputs();
    if (state.cfgId) {
        loadOrgOptions();
        loadLogs();
    } else {
        state.logRows = [];
        renderLogs();
    }
}

/** 组织选项：OA 分部 / OA 部门（E9 缓存快照）与企微部门（部门快照） */
function loadOrgOptions() {
    if (!state.cfgId) { return; }
    Oaones.api("sync.orgOptions", { cfg_id: state.cfgId }, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        var d = res.data || {};
        state.subs = d.subcompanies || [];
        state.depts = d.departments || [];
        state.wxDepts = d.wx_depts || [];
        if (!state.wxDepts.length) {
            showToast("该租户还没有" + SC_SHORT + "部门快照，请先在“部门绑定”页更新" + SC_SHORT + "部门快照后再同步", 1, 8000);
        }
        renderScopeInputs();
    });
}

/* ==================================================================
 * 三、同步：预览确认 → 执行
 * ================================================================== */

function previewSync() {
    if (state.running) { return; }
    if (!byId("direction").value) { showToast("请先在第一行选择同步方向", 1); return; }
    if (!state.cfgId) { showToast("请选择" + SC_FULL + "（必选一个，不能“全部租户”）", 1); return; }
    if (byId("direction").value === "0" && !state.subIds.length && !state.deptIds.length) {
        showToast("请先在第二行选择要同步的 OA 分部或 OA 部门（含下级，至少选一个）", 1);
        return;
    }
    // 企微→OA：OA 侧是目标，目标分部必选（目标部门非必选）
    if (byId("direction").value === "1" && !state.subIds.length) {
        showToast("请先在第三行选择 OA 目标分部（单选、必选）：新建的 OA 部门挂在该分部下；"
            + "再选目标部门（非必选）可把新建部门挂到该分部的某个部门下", 1);
        return;
    }
    var btn = byId("previewBtn");
    btn.disabled = true;
    btn.textContent = "预览中…";
    Oaones.api("sync.preview", syncParams(), function (res) {
        btn.disabled = false;
        btn.textContent = "开始同步";
        if (res.code !== 0) {
            // “A/B 不干净”这类前置校验失败：原样常驻提示（含冲突清单），便于逐项解绑后再来同步
            if (String(res.msg || "").indexOf("无法同步") === 0) {
                showToast(res.msg, 1, 0);
            } else {
                showToast("同步预览失败：" + res.msg, 2);
            }
            return;
        }
        state.preview = res.data || {};
        renderPreview(state.preview);
        showModal("previewMask", "previewModal");
    });
}

/** 预览弹窗：提示文案 + 统计 + 更新/新增部门与人员清单 */
function renderPreview(data) {
    byId("previewMessage").textContent = data.message || "";
    var c = data.counts || {};
    byId("pDeptUpdate").textContent = c.dept_update || 0;
    byId("pDeptCreate").textContent = c.dept_create || 0;
    byId("pUserUpdate").textContent = c.user_update || 0;
    byId("pUserCreate").textContent = c.user_create || 0;
    byId("pSkip").textContent = (c.dept_skip || 0) + (c.user_skip || 0);
    byId("pFail").textContent = (c.dept_fail || 0) + (c.user_fail || 0);
    var lists = data.lists || {};
    var html = "";
    html += previewBlock("更新部门（" + (c.dept_update || 0) + "）", lists.dept_update, "dept", "更新");
    html += previewBlock("新增部门（" + (c.dept_create || 0) + "）", lists.dept_create, "dept", "新增");
    html += previewBlock("更新人员（" + (c.user_update || 0) + "）", lists.user_update, "user", "更新");
    html += previewBlock("新增人员（" + (c.user_create || 0) + "）", lists.user_create, "user", "新增");
    html += previewBlock("按规则跳过（" + ((c.dept_skip || 0) + (c.user_skip || 0)) + "）",
        (lists.dept_skip || []).concat(lists.user_skip || []), "", "跳过");
    html += previewBlock("无法同步（" + ((c.dept_fail || 0) + (c.user_fail || 0)) + "）",
        (lists.dept_fail || []).concat(lists.user_fail || []), "", "失败");
    byId("previewLists").innerHTML = html || "本次没有需要处理的部门与人员。";
}

function previewBlock(title, rows, kind, opText) {
    rows = rows || [];
    if (!rows.length) { return ""; }
    // 每种类型单独一行标题（类型之间换行显示），每条明细各自一行
    var html = "<div class='preview-type'>" + Oaones.esc(title) + "</div>";
    var show = Math.min(rows.length, 50);
    for (var i = 0; i < show; i++) {
        var row = rows[i];
        var name = row.oa_name || row.wx_name || row.wx_id || "-";
        var wx = row.wx_name ? (" → " + SC_SHORT + "：" + row.wx_name + (row.wx_id ? "（ID " + row.wx_id + "）" : "")) : "";
        var parent = row.parent_name ? "，挂靠「" + row.parent_name + "」" : "";
        html += "<div class='preview-item'>" + Oaones.esc(opText + "：" + name) + Oaones.esc(wx + parent)
            + (row.message ? " <span class='muted'>" + Oaones.esc(row.message) + "</span>" : "")
            + "</div>";
    }
    if (rows.length > show) {
        html += "<div class='preview-item muted'>……共 " + rows.length + " 条，已列出前 " + show + " 条</div>";
    }
    return html;
}

/** 用户确认后真正执行（后端按同一套计划逐条写入并把明细写进同步日志） */
function runScopedSync() {
    if (state.running) { return; }
    var btn = byId("previewOk");
    state.running = true;
    btn.disabled = true;
    btn.textContent = "同步中…";
    Oaones.api("sync.runScoped", syncParams(), function (res) {
        state.running = false;
        btn.disabled = false;
        btn.textContent = "确认开始同步";
        hideModal("previewMask", "previewModal");
        if (res.code !== 0) { showToast("同步失败：" + res.msg, 2); return; }
        var d = res.data || {};
        byId("sTotal").textContent = d.total || 0;
        byId("sSuccess").textContent = d.success || 0;
        byId("sSkip").textContent = d.skip || 0;
        byId("sFail").textContent = d.fail || 0;
        byId("messageDetail").textContent = (d.messages || []).join("\n") || "无明细";
        byId("summaryArea").style.display = "block";
        showToast("同步完成：成功 " + (d.success || 0) + "，跳过 " + (d.skip || 0)
            + "，失败 " + (d.fail || 0) + "；明细见同步日志", 0, 6000);
        loadLogs();
    });
}

/* ==================================================================
 * 四、同步日志（汇总）与同步明细（逐条部门/人员）
 * ================================================================== */

/** 日志列表分页器（每页 10/20/50/100/1000，见 js/table.js） */
var logPager = OaonesTable.pager({
    key: "syncCenter.logs",
    el: byId("logPager"),
    onChange: function () { renderLogs(); }
});
/** 明细弹窗内的分页器（逐条部门/人员可能上百条） */
var detailPager = OaonesTable.pager({
    key: "syncCenter.detail",
    el: byId("detailPager"),
    onChange: function () { renderDetailRows(); }
});

function logDirText(r) {
    return r.direction === "0" ? "OA→" + SC_SHORT : (r.direction === "1" ? SC_SHORT + "→OA"
        : String(r.direction == null ? "" : r.direction));
}

function logStatusText(r) {
    if (String(r.run_status) === "1") { return "部分成功"; }
    if (String(r.run_status) === "2") { return "失败"; }
    return "成功";
}

function logRowText(r) {
    return [r.id, r.cfg_id, r.corp_name, logDirText(r), r.total_count, r.success_count,
        r.skip_count, r.fail_count, r.dept_count, r.user_count, r.nodept_count,
        r.start_time, r.end_time, logStatusText(r)].join(" ");
}

function filteredLogRows() {
    var k = (state.logKeyword || "").trim().toLowerCase();
    if (!k) { return state.logRows; }
    return state.logRows.filter(function (r) {
        return logRowText(r).toLowerCase().indexOf(k) >= 0;
    });
}

function renderLogStats(rows) {
    var ok = 0;
    var partial = 0;
    var fail = 0;
    state.logRows.forEach(function (r) {
        var st = String(r.run_status);
        if (st === "1") { partial++; }
        else if (st === "2") { fail++; }
        else { ok++; }
    });
    var statsEl = byId("logStats");
    statsEl.style.display = "";
    statsEl.innerHTML = ""
        + "<span class='bind-stat'><span>当前加载</span><b>" + state.logRows.length + "</b> 条</span>"
        + "<span class='bind-stat'><span>成功</span><b>" + ok + "</b> 条</span>"
        + "<span class='bind-stat'><span>部分成功</span><b>" + partial + "</b> 条</span>"
        + "<span class='bind-stat'><span>失败</span><b>" + fail + "</b> 条</span>"
        + "<span class='bind-stat'><span>当前显示</span><b>" + rows.length + "</b> 条</span>";
}

function renderLogs() {
    var rows = filteredLogRows();
    logPager.resetIfChanged([state.cfgId, state.logKeyword].join("|"));
    var pageRows = logPager.apply(rows);
    var body = "";
    pageRows.forEach(function (r) {
        var status = "<span class='tag bound'>" + Oaones.esc(logStatusText(r)) + "</span>";
        if (String(r.run_status) === "1") { status = "<span class='tag unbound'>部分成功</span>"; }
        if (String(r.run_status) === "2") { status = "<span class='tag fail'>失败</span>"; }
        body += "<tr>"
            + "<td>" + Oaones.esc(r.id) + "</td>"
            + "<td>" + Oaones.esc(r.corp_name) + "</td>"
            + "<td>" + Oaones.esc(logDirText(r)) + "</td>"
            + "<td>" + Oaones.esc(r.total_count) + "</td>"
            + "<td>" + Oaones.esc(r.success_count) + "</td>"
            + "<td>" + Oaones.esc(r.skip_count) + "</td>"
            + "<td>" + Oaones.esc(r.fail_count) + "</td>"
            // 本次同步统计：部门（含分部）/ 员工 / 其中“没有找到部门”的员工
            + "<td>" + Oaones.esc(r.dept_count == null ? 0 : r.dept_count) + "</td>"
            + "<td>" + Oaones.esc(r.user_count == null ? 0 : r.user_count) + "</td>"
            + "<td>" + Oaones.esc(r.nodept_count == null ? 0 : r.nodept_count) + "</td>"
            + "<td>" + Oaones.esc(Oaones.fmtTime(r.start_time)) + "</td>"
            + "<td>" + Oaones.esc(Oaones.fmtTime(r.end_time)) + "</td>"
            + "<td>" + status + "</td>"
            + "<td><button class='link' data-log-id='" + Oaones.esc(r.id) + "'>明细</button></td>"
            + "</tr>";
    });
    byId("logBody").innerHTML = body;
    logPager.show();
    logPager.render();
    OaonesTable.resizable(byId("logTable"), { key: "syncCenter.logTable" });
    var emptyEl = byId("emptyTip");
    emptyEl.style.display = rows.length ? "none" : "block";
    emptyEl.textContent = !state.cfgId ? "请先在第二行选择" + SC_FULL + "，再查看该租户的同步日志"
        : (state.logKeyword ? "没有匹配的同步日志" : "该租户暂无同步日志");
    renderLogStats(rows);
}

function loadLogs() {
    if (!state.cfgId) {
        state.logRows = [];
        renderLogs();
        return;
    }
    Oaones.api("sync.logs", {
        cfg_id: state.cfgId, direction: "all", limit: 200
    }, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        state.logRows = res.data || [];
        renderLogs();
    });
}

function logRowById(id) {
    for (var i = 0; i < state.logRows.length; i++) {
        if (String(state.logRows[i].id) === String(id)) { return state.logRows[i]; }
    }
    return null;
}

/**
 * 明细弹窗：先展示该条日志的范围/统计文本，再按“类型（全部/部门/人员）+ 关键字”过滤逐条明细。
 * 历史日志没有明细行时退回展示日志自带的 detail 文本。
 */
function showDetail(logId) {
    var row = logRowById(logId);
    var cfgId = row ? String(row.cfg_id || "") : state.cfgId;
    state.detail = { rows: [], type: "all", keyword: "", log: row };
    byId("detailTitle").textContent = "同步明细 · 日志 " + logId;
    // 详情：先给“统计”（本次同步 x 个部门 x 个员工，其中 x 个员工没有找到部门、在「部门名」下），
    // 下面再是逐条明细表格（部门/分部一行、人员一行）
    byId("detailMeta").textContent = row
        ? ("本次同步 " + (row.dept_count || 0) + " 个部门 " + (row.user_count || 0) + " 个员工"
            + (Number(row.nodept_count) > 0
                ? ("，其中 " + row.nodept_count + " 个员工没有找到部门"
                    + (row.nodept_dept ? "，在「" + row.nodept_dept + "」下" : ""))
                : "")
            + "｜租户：" + (row.corp_name || "") + "｜方向：" + logDirText(row)
            + "｜结果：" + logStatusText(row) + "｜总数 " + (row.total_count || 0)
            + "，成功 " + (row.success_count || 0) + "，跳过 " + (row.skip_count || 0)
            + "，失败 " + (row.fail_count || 0) + "｜" + (row.start_time || "") + " ~ " + (row.end_time || ""))
        : "";
    byId("detailText").textContent = row && row.detail ? row.detail : "";
    byId("detailKeyword").value = "";
    state.detail.keyword = "";
    var btns = byId("detailTypeSwitch").querySelectorAll(".view-btn");
    for (var i = 0; i < btns.length; i++) {
        btns[i].classList.toggle("active", btns[i].getAttribute("data-type") === "all");
    }
    showModal("detailMask", "detailModal");
    Oaones.api("sync.logItems", { log_id: logId, cfg_id: cfgId, item_type: "all", limit: 5000 },
        function (res) {
            state.detail.rows = (res.code === 0 && res.data) ? res.data : [];
            renderDetail();
        });
}

function detailFilteredRows() {
    var rows = state.detail.rows || [];
    if (state.detail.type === "dept") {
        // 部门口径含“分部”：企微里只有部门概念，A = OA 分部时该分部也落成一个部门
        rows = rows.filter(function (r) {
            var t = String(r.item_type);
            return t === "0" || t === "2";
        });
    } else if (state.detail.type === "user") {
        rows = rows.filter(function (r) { return String(r.item_type) === "1"; });
    }
    var k = (state.detail.keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (r) {
        return [r.oa_id, r.oa_name, r.wx_id, r.wx_name, r.parent_id, r.parent_name, r.message]
            .map(function (v) { return String(v == null ? "" : v); })
            .join(" ").toLowerCase().indexOf(k) >= 0;
    });
}

function renderDetail() {
    var rows = detailFilteredRows();
    var statsEl = byId("detailStats");
    var create = 0;
    var update = 0;
    var skip = 0;
    var fail = 0;
    rows.forEach(function (r) {
        var op = String(r.op);
        if (op === "0") { create++; }
        else if (op === "1") { update++; }
        else if (op === "2") { skip++; }
        else if (op === "3") { fail++; }
    });
    statsEl.style.display = "";
    statsEl.innerHTML = ""
        + "<span class='bind-stat'><span>明细</span><b>" + rows.length + "</b> 条</span>"
        + "<span class='bind-stat'><span>新增</span><b>" + create + "</b> 条</span>"
        + "<span class='bind-stat'><span>更新</span><b>" + update + "</b> 条</span>"
        + "<span class='bind-stat'><span>跳过</span><b>" + skip + "</b> 条</span>"
        + "<span class='bind-stat'><span>失败</span><b>" + fail + "</b> 条</span>";
    detailPager.resetIfChanged([state.detail.type, state.detail.keyword].join("|"));
    renderDetailRows();
}

function renderDetailRows() {
    var rows = detailPager.apply(detailFilteredRows());
    var body = "";
    rows.forEach(function (r) {
        body += "<tr>"
            + "<td>" + Oaones.esc(detailTypeText(r.item_type)) + "</td>"
            + "<td>" + detailOpTag(r.op) + "</td>"
            + "<td>" + detailOaCell(r) + "</td>"
            + "<td>" + detailWxCell(r) + "</td>"
            + "<td>" + Oaones.esc(r.parent_name || "")
            + (String(r.parent_id || "").length ? "（" + Oaones.esc(r.parent_id) + "）" : "") + "</td>"
            + "<td>" + Oaones.esc(r.message || "") + "</td>"
            + "</tr>";
    });
    byId("detailBody").innerHTML = body;
    detailPager.show();
    detailPager.render();
    OaonesTable.resizable(byId("detailTable"), { key: "syncCenter.detailTable" });
    var emptyEl = byId("detailEmpty");
    var noItem = (state.detail.rows || []).length === 0;
    emptyEl.style.display = rows.length ? "none" : "block";
    emptyEl.textContent = noItem
        ? "该日志没有逐条明细（历史日志只有汇总文本）"
        : "没有匹配的明细";
}

function detailOpTag(op) {
    var text = String(op) === "0" ? "新增" : (String(op) === "1" ? "更新"
        : (String(op) === "2" ? "跳过" : "失败"));
    var cls = String(op) === "0" ? "tag bound" : (String(op) === "1" ? "tag system"
        : (String(op) === "2" ? "tag unbound" : "tag fail"));
    return "<span class='" + cls + "'>" + Oaones.esc(text) + "</span>";
}

/** 明细对象类型文案：0 部门 / 1 人员 / 2 分部（OA 分部在企微侧落成一个部门） */
function detailTypeText(itemType) {
    var t = String(itemType);
    if (t === "1") { return "人员"; }
    if (t === "2") { return "分部"; }
    return "部门";
}

/** OA 侧对象：泛微对象一律输出标准页面超链接（企微侧保持纯文本，见 skill.md 第一节第 9 条） */
function detailOaCell(r) {
    var id = String(r.oa_id == null ? "" : r.oa_id);
    var name = String(r.oa_name == null ? "" : r.oa_name);
    if (!id) { return Oaones.esc(name || "-"); }
    if (String(r.item_type) === "2") {
        return OaonesHrm.sub(id, name || ("OA 分部 " + id));
    }
    if (String(r.item_type) === "1") {
        return OaonesHrm.user(id, name || ("OA 人员 " + id));
    }
    return OaonesHrm.dept(id, name || ("OA 部门 " + id));
}

/** 企微侧对象：纯文本（企微部门/成员在泛微没有标准页面） */
function detailWxCell(r) {
    var name = String(r.wx_name == null ? "" : r.wx_name);
    var id = String(r.wx_id == null ? "" : r.wx_id);
    if (!name && !id) { return "-"; }
    return Oaones.esc(name ? (name + (id ? "（ID " + id + "）" : "")) : id);
}

/* ==================================================================
 * 五、选择弹框的开关（取消时回退到打开前的选择）
 * ================================================================== */

function openSubPick() {
    state._subBackup = state.subIds.slice();
    byId("syncSubSearch").value = "";
    OaonesPicker.resetTree("syncSub");
    renderSubList();
    showModal("syncSubMask", "syncSubModal");
    byId("syncSubSearch").focus();
}

function closeSubPick(commit) {
    if (!commit && state._subBackup) { state.subIds = state._subBackup.slice(); }
    state._subBackup = null;
    hideModal("syncSubMask", "syncSubModal");
    renderScopeInputs();
}

function openDeptPick() {
    // 企微→OA：目标部门候选只列目标分部（含下级分部）下的部门，先选分部再来选部门
    if (state.deptSingle && !state.subIds.length) {
        showToast(SC_SHORT + "→OA：请先选择 OA 目标分部（单选、必选），目标部门候选只列出该分部"
            + "（含下级分部）下的部门", 1, 6000);
        return;
    }
    state._deptBackup = state.deptIds.slice();
    byId("syncDeptSearch").value = "";
    OaonesPicker.resetTree("syncDept");
    renderDeptList();
    showModal("syncDeptMask", "syncDeptModal");
    byId("syncDeptSearch").focus();
}

function closeDeptPick(commit) {
    if (!commit && state._deptBackup) { state.deptIds = state._deptBackup.slice(); }
    state._deptBackup = null;
    hideModal("syncDeptMask", "syncDeptModal");
    renderScopeInputs();
}

function openWxPick() {
    state._wxBackup = String(state.wxDeptId || "");
    byId("syncWxSearch").value = "";
    OaonesPicker.resetTree("syncWx");
    renderWxList();
    showModal("syncWxMask", "syncWxModal");
    byId("syncWxSearch").focus();
}

function closeWxPick(commit) {
    if (!commit) { state.wxDeptId = state._wxBackup || ""; }
    state._wxBackup = null;
    hideModal("syncWxMask", "syncWxModal");
    renderScopeInputs();
}

/** 单击选项：只就地刷新选中态（不重建列表），保证双击仍然命中同一行 */
function refreshSubDom() {
    var opts = byId("syncSubList").querySelectorAll(".wx-pick-option");
    var partial = OaonesPicker.partialSelected(state.subIds, subChildMap);
    for (var i = 0; i < opts.length; i++) {
        var id = String(opts[i].getAttribute("data-id"));
        var active = state.subIds.indexOf(id) >= 0;
        opts[i].classList.toggle("active", active);
        opts[i].classList.toggle("partial", !active && !!partial[id]);
    }
    syncSubAllState();
}

function refreshDeptDom() {
    var opts = byId("syncDeptList").querySelectorAll(".wx-pick-option");
    var partial = OaonesPicker.partialSelected(state.deptIds, deptChildMap);
    for (var i = 0; i < opts.length; i++) {
        var id = String(opts[i].getAttribute("data-id"));
        var active = state.deptIds.indexOf(id) >= 0;
        opts[i].classList.toggle("active", active);
        opts[i].classList.toggle("partial", !active && !!partial[id]);
    }
    syncDeptAllState();
}

function refreshWxDom() {
    var opts = byId("syncWxList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        opts[i].classList.toggle("active",
            String(opts[i].getAttribute("data-id")) === String(state.wxDeptId));
    }
}

/* ---------------- 企业微信租户弹框（单选必选；租户之间没有上下级，只提供列表式） ---------------- */

function renderCfgList() {
    var keyword = byId("syncCfgSearch").value;
    var rows = filterRows(state.cfgs, keyword, ["id", "corp_name", "corp_id"]);
    var el = byId("syncCfgList");
    var html = rows.length
        ? "<div class='wx-pick-head pick-cols-wide'><span>租户名称</span><span>企业ID / 状态</span></div>" : "";
    rows.forEach(function (c) { html += cfgOptionHtml(c); });
    el.className = "wx-pick-list";
    el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的" + SC_SHORT + "租户</div>";
}

function cfgOptionHtml(c) {
    var id = String(c.id);
    var active = String(state.cfgId) === id;
    var stopped = String(c.sync_enable) !== "1";
    var cls = "wx-pick-option pick-cols-wide" + (active ? " active" : "")
        + (stopped ? " disabled" : "");
    var tip = (active ? "当前已选中该租户" : (stopped ? "该租户已停用，不能同步" : "点击选择该租户"))
        + (c.corp_id ? "｜企业ID " + c.corp_id : "");
    return "<button type='button' class='" + cls + "' data-id='" + Oaones.esc(id) + "'"
        + " title='" + Oaones.esc(tip) + "'>"
        + "<span class='wx-pick-opt-name'>" + Oaones.esc(c.corp_name || ("#" + id)) + "</span>"
        + "<span>" + Oaones.esc((c.corp_id || "-") + (stopped ? "（已停用）" : "")) + "</span>"
        + "</button>";
}

/** 单选：点一行即选中该租户（必选一个，点同一行不取消） */
function toggleCfg(id) {
    state.cfgId = String(id == null ? "" : id);
}

function refreshCfgDom() {
    var opts = byId("syncCfgList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        opts[i].classList.toggle("active",
            String(opts[i].getAttribute("data-id")) === String(state.cfgId));
    }
}

function openCfgPick() {
    if (Oaones.cfgLocked()) {
        showToast("当前租户由“" + SC_SHORT + "租户”列表点开决定，本页不能切换租户；要换租户请先回“" + SC_SHORT + "租户”列表", 1, 6000);
        return;
    }
    state._cfgBackup = String(state.cfgId || "");
    byId("syncCfgSearch").value = "";
    renderCfgList();
    showModal("syncCfgMask", "syncCfgModal");
    byId("syncCfgSearch").focus();
}

/** commit=true 才把弹框里选中的租户应用到页面（取消则回退到打开前的租户） */
function closeCfgPick(commit) {
    if (!commit) { state.cfgId = state._cfgBackup || ""; }
    state._cfgBackup = null;
    hideModal("syncCfgMask", "syncCfgModal");
    if (commit) {
        applyCfg(state.cfgId);
    } else {
        renderScopeInputs();
    }
}

/* ==================================================================
 * 六、事件注册（页壳末尾统一注册，片段脚本只声明函数）
 * ================================================================== */

byId("previewBtn").addEventListener("click", previewSync);
byId("previewOk").addEventListener("click", runScopedSync);
byId("previewCancel").addEventListener("click", function () { hideModal("previewMask", "previewModal"); });
byId("previewClose").addEventListener("click", function () { hideModal("previewMask", "previewModal"); });
byId("previewMask").addEventListener("click", function (e) {
    if (e.target === this) { hideModal("previewMask", "previewModal"); }
});

byId("direction").addEventListener("change", renderDirection);
byId("refreshLog").addEventListener("click", loadLogs);
byId("logKeyword").addEventListener("input", function () {
    state.logKeyword = this.value;
    renderLogs();
});
byId("logKeyword").addEventListener("keydown", function (e) {
    if (e.key === "Enter") { state.logKeyword = this.value; renderLogs(); }
});
byId("logSearchBtn").addEventListener("click", function () {
    state.logKeyword = byId("logKeyword").value;
    renderLogs();
});
byId("logResetBtn").addEventListener("click", function () {
    state.logKeyword = "";
    byId("logKeyword").value = "";
    renderLogs();
});
byId("logBody").addEventListener("click", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest("button[data-log-id]") : null;
    if (!btn) { return; }
    showDetail(btn.getAttribute("data-log-id"));
});

/* ---- 企业微信租户弹框（单选必选） ---- */
byId("syncCfgPicker").addEventListener("click", openCfgPick);
byId("syncCfgSearch").addEventListener("input", function () { renderCfgList(); });
byId("syncCfgList").addEventListener("click", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn || btn.classList.contains("disabled")) { return; }
    toggleCfg(btn.getAttribute("data-id"));
    refreshCfgDom();
});
byId("syncCfgList").addEventListener("dblclick", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn || btn.classList.contains("disabled")) { return; }
    // 双击即确定并退出选择
    toggleCfg(btn.getAttribute("data-id"));
    closeCfgPick(true);
});
byId("syncCfgOk").addEventListener("click", function () { closeCfgPick(true); });
byId("syncCfgCancel").addEventListener("click", function () { closeCfgPick(false); });
byId("syncCfgClose").addEventListener("click", function () { closeCfgPick(false); });
byId("syncCfgMask").addEventListener("click", function (e) {
    if (e.target === this) { closeCfgPick(false); }
});

/* ---- OA 分部弹框 ---- */
byId("syncSubPicker").addEventListener("click", function (e) {
    if (e.target && e.target.closest && e.target.closest("#syncSubClear")) { return; }
    openSubPick();
});
byId("syncSubClear").addEventListener("click", function (e) {
    e.stopPropagation();
    state.subIds = [];
    // 企微→OA：目标分部是目标根，清空后目标部门候选不再有范围，一并清空
    if (state.subSingle) { state.deptIds = []; }
    renderScopeInputs();
});
byId("syncSubSearch").addEventListener("input", function () { renderSubList(); });
byId("syncSubList").addEventListener("click", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn) { return; }
    toggleSub(btn.getAttribute("data-id"));
    refreshSubDom();
});
byId("syncSubList").addEventListener("dblclick", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn) { return; }
    // 双击前已收到两次单击（选中→取消），这里确保该行处于选中态后再确定
    if (state.subIds.indexOf(btn.getAttribute("data-id")) < 0) {
        toggleSub(btn.getAttribute("data-id"));
        refreshSubDom();
    }
    closeSubPick(true);
});
byId("syncSubOk").addEventListener("click", function () { closeSubPick(true); });
byId("syncSubCancel").addEventListener("click", function () { closeSubPick(false); });
byId("syncSubClose").addEventListener("click", function () { closeSubPick(false); });
byId("syncSubMask").addEventListener("click", function (e) {
    if (e.target === this) { closeSubPick(false); }
});
byId("syncSubClearBtn").addEventListener("click", function () {
    state.subIds = [];
    if (state.subSingle) { state.deptIds = []; }
    renderSubList();
});

/* ---- OA 部门弹框 ---- */
byId("syncDeptPicker").addEventListener("click", function (e) {
    if (e.target && e.target.closest && e.target.closest("#syncDeptClear")) { return; }
    openDeptPick();
});
byId("syncDeptClear").addEventListener("click", function (e) {
    e.stopPropagation();
    state.deptIds = [];
    renderScopeInputs();
});
byId("syncDeptSearch").addEventListener("input", function () { renderDeptList(); });
byId("syncDeptList").addEventListener("click", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn) { return; }
    toggleDept(btn.getAttribute("data-id"));
    refreshDeptDom();
});
byId("syncDeptList").addEventListener("dblclick", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn) { return; }
    if (state.deptIds.indexOf(btn.getAttribute("data-id")) < 0) {
        toggleDept(btn.getAttribute("data-id"));
        refreshDeptDom();
    }
    closeDeptPick(true);
});
byId("syncDeptOk").addEventListener("click", function () { closeDeptPick(true); });
byId("syncDeptCancel").addEventListener("click", function () { closeDeptPick(false); });
byId("syncDeptClose").addEventListener("click", function () { closeDeptPick(false); });
byId("syncDeptMask").addEventListener("click", function (e) {
    if (e.target === this) { closeDeptPick(false); }
});
byId("syncDeptClearBtn").addEventListener("click", function () {
    state.deptIds = [];
    renderDeptList();
});

/* ---- 企微部门弹框（单选） ---- */
byId("syncWxDeptPicker").addEventListener("click", function (e) {
    if (e.target && e.target.closest && e.target.closest("#syncWxDeptClear")) { return; }
    openWxPick();
});
byId("syncWxDeptClear").addEventListener("click", function (e) {
    e.stopPropagation();
    state.wxDeptId = "";
    renderScopeInputs();
});
byId("syncWxSearch").addEventListener("input", function () { renderWxList(); });
byId("syncWxList").addEventListener("click", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn) { return; }
    toggleWxDept(btn.getAttribute("data-id"));
    refreshWxDom();
});
byId("syncWxList").addEventListener("dblclick", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!btn) { return; }
    closeWxPick(true);
});
byId("syncWxOk").addEventListener("click", function () { closeWxPick(true); });
byId("syncWxCancel").addEventListener("click", function () { closeWxPick(false); });
byId("syncWxClose").addEventListener("click", function () { closeWxPick(false); });
byId("syncWxMask").addEventListener("click", function (e) {
    if (e.target === this) { closeWxPick(false); }
});
byId("syncWxClearBtn").addEventListener("click", function () {
    state.wxDeptId = "";
    renderWxList();
});

/* ---- 明细弹窗 ---- */
byId("detailClose").addEventListener("click", function () { hideModal("detailMask", "detailModal"); });
byId("detailOkBtn").addEventListener("click", function () { hideModal("detailMask", "detailModal"); });
byId("detailMask").addEventListener("click", function (e) {
    if (e.target === this) { hideModal("detailMask", "detailModal"); }
});
byId("detailKeyword").addEventListener("input", function () {
    state.detail.keyword = this.value;
    renderDetail();
});
byId("detailTypeSwitch").addEventListener("click", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest(".view-btn") : null;
    if (!btn) { return; }
    var type = btn.getAttribute("data-type") || "all";
    state.detail.type = type;
    var all = this.querySelectorAll(".view-btn");
    for (var i = 0; i < all.length; i++) {
        all[i].classList.toggle("active", all[i] === btn);
    }
    renderDetail();
});

// 第一行方向未选时先隐藏后面的选择框（选择方向后再出现）
renderDirection();
loadCfgs();
