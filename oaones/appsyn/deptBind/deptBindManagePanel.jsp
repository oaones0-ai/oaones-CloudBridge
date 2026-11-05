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
        // 免绑定取自 uf_oaones_appsyn_dep_tmp.no_bind = 0（正常不写值，返回空串）
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
    var value = wxCurrentValue(oaId);
    var wxId = value ? value.split("__")[0] : "";
    var deptName = "";
    oaRows().forEach(function (d) {
        if (String(d.id) === String(oaId)) { deptName = d.name || ""; }
    });
    OaonesPicker.open({
        key: "wxPick",
        title: (deptName ? "为 OA 部门“" + deptName + "”" : "为 OA 部门（ID " + oaId + "）")
            + "选择" + P_SHORT + "部门",
        multiple: false,
        allowEmpty: false,
        initialSelected: wxId ? [wxId] : [],
        rows: wxPickerOptions(oaId, ""),
        idOf: function (d) { return d.id; },
        parentOf: function (d) { return d.parentid; },
        textOf: function (d) { return d.name || (P_SHORT + "部门 " + d.id); },
        detailOf: function (d) { return "部门ID " + d.id; },
        badgeOf: function (d) {
            var others = (boundListByWx[String(d.id)] || []).filter(function (b) {
                return String(b.oa_dept_id) !== String(oaId);
            });
            return others.length ? "已被 " + others.length + " 个 OA 部门绑定" : "";
        },
        onConfirm: function (selection) {
            if (!selection.ids.length) { return; }
            var pickedWxId = String(selection.ids[0]);
            var parentId = String(selection.items[0].raw.parentid == null
                ? "" : selection.items[0].raw.parentid);
            var pickedValue = pickedWxId + "__" + parentId;
            wxPickValues[String(oaId)] = pickedValue;
            smartSuggestions[String(oaId)] = { wx_id: pickedWxId, parentid: parentId };
            saveDeptBind(String(oaId), pickedValue);
        }
    });
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

function oaSubChildMap() {
    var map = {};
    oaSubOptions.forEach(function (s) {
        var p = String(s.parent == null ? "" : s.parent);
        if (!map[p]) { map[p] = []; }
        map[p].push(String(s.id));
    });
    return map;
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

function openOaPick(type) {
    var isSub = type === "fb";
    var fbIds = (state.oaFbIds || []).map(String);
    OaonesPicker.open({
        key: "oaPick:" + type,
        title: isSub ? "选择分部" : "选择部门",
        multiple: true,
        selectChildren: isSub,
        initialSelected: (isSub ? state.oaFbIds : state.oaDeptIds) || [],
        rows: isSub ? oaSubOptions : oaDeptOptions,
        idOf: function (d) { return d.id; },
        parentOf: function (d) { return d.parent; },
        textOf: function (d) { return d.name || d.fullname; },
        detailOf: function (d) { return [d.code, d.fullname].filter(Boolean).join(" / "); },
        filter: function (d) {
            return isSub || !fbIds.length || fbIds.indexOf(String(d.subcompanyid1)) >= 0;
        },
        onConfirm: function (selection) {
            if (isSub) {
                state.oaFbIds = expandFbIds(selection.ids);
                var keepSub = {};
                state.oaFbIds.forEach(function (id) { keepSub[id] = true; });
                state.oaDeptIds = (state.oaDeptIds || []).filter(function (id) {
                    var dept = null;
                    for (var i = 0; i < oaDeptOptions.length; i++) {
                        if (String(oaDeptOptions[i].id) === String(id)) { dept = oaDeptOptions[i]; break; }
                    }
                    return dept && keepSub[String(dept.subcompanyid1 == null ? "" : dept.subcompanyid1)];
                });
            } else {
                state.oaDeptIds = selection.ids.slice();
            }
            doOaSearch();
        }
    });
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
