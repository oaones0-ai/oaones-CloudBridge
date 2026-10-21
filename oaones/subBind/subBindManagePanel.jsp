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

<%-- 分部绑定主体（一行一个 OA 分部，绑定对象为平台租户）：markup + 本片段脚本 --%>
<%-- 选择平台租户“双击 / 点确定”即写入数据库，页面上没有额外的保存/批量绑定按钮 --%>
<%-- 页面说明正文：由页壳工具栏右侧的感叹号（#cfgHelpTip）悬浮/点击弹出 --%>
        <div id="subManagePanel">
            <div class="filter-bar">
                <span class="filter-label">OA 分部筛选：</span>
                <div class="filter-picker" id="subFbPicker" title="点击选择分部（可多选）">
                    <input type="text" id="subFbInput" class="filter-input" readonly placeholder="选择分部（可多选）">
                    <span class="filter-arrow">▾</span>
                    <button type="button" class="filter-clear" id="subFbClear" title="清除分部筛选">×</button>
                </div>
                <button type="button" class="btn small secondary" id="oaFilterReset">重置筛选</button>
            </div>
            <div class="bind-stats" id="subStats"></div>
            <div class="table-wrap">
                <table class="grid" id="subTable">
                    <thead>
                    <tr>
                        <th style="width:38%;">OA 分部</th>
                        <th style="width:38%;"><%= oaonesPBShort %>租户</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="subBody"></tbody>
                </table>
                <div class="empty" id="subEmpty" style="display:none;">暂无 OA 分部</div>
            </div>
            <div class="pager" id="subPagerEl"></div>
        </div>

<div class="modal-mask" id="cfgPickMask"></div>
<div class="modal wx-pick-modal" id="cfgPickModal">
    <div class="modal-head">
        <span id="cfgPickTitle">选择<%= oaonesPBShort %>租户</span>
        <button type="button" class="link" id="cfgPickCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="wx-pick-tip" id="cfgPickTip"></div>
        <div class="pick-view-bar">
            <input type="text" id="cfgPickSearch" placeholder="输入<%= oaonesPBShort %>名称 / <%= oaonesPBKeyLabel %> / 租户ID搜索" autocomplete="off">
        </div>
        <div class="wx-pick-list" id="cfgPickList"></div>
        <div class="muted wx-pick-note">只展示你有权维护的<%= oaonesPBShort %>租户；分部与<%= oaonesPBShort %>租户为“一对一”，
            已被其他 OA 分部绑定的租户在列表里不可选（先解除该分部的绑定即可释放）。
            该分部对应的<%= oaonesPBShort %>部门取租户配置里维护的根部门（在“<%= oaonesPBShort %>租户配置”页维护）。
            单击选中，<span class="rule">双击该行（或点“确定”）立即写入数据库完成绑定</span>，页面上不需要再点保存。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="cfgPickClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="cfgPickCancelBtn">取消</button>
        <button type="button" class="btn" id="cfgPickOkBtn">确定</button>
    </div>
</div>

<div class="modal-mask" id="subPickMask"></div>
<div class="modal wx-pick-modal" id="subPickModal">
    <div class="modal-head">
        <span id="subPickTitle">选择分部</span>
        <button type="button" class="link" id="subPickCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="subPickSearch" placeholder="输入名称/编码/ID搜索" autocomplete="off">
            <div class="view-switch mini" id="subPickViewSwitch" data-view-key="subPick">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
            <button type="button" class="btn small secondary pick-all-btn" id="subPickAllBtn" data-pick-all="subPick"
                    title="全选/全不选当前列表（受搜索过滤影响）">全选</button>
        </div>
        <div class="wx-pick-list" id="subPickList"></div>
        <div class="muted wx-pick-note" id="subPickNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="subPickClearBtn">清空</button>
        <button type="button" class="btn secondary" id="subPickCancelBtn">取消</button>
        <button type="button" class="btn" id="subPickOkBtn">确定</button>
    </div>
</div>

<script>
"use strict";
/** 片段脚本比页壳脚本先执行（页壳的 byId 在页面末尾才定义），片段内必须自带一份，否则解析期就报 byId is not defined */
function byId(id) { return document.getElementById(id); }
/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js）：翻页后重绘当前列表 */
var subPager = OaonesTable.pager({
    key: "subBind.manage",
    el: byId("subPagerEl"),
    onChange: function () { renderSubManage(); }
});

function cfgById(cfgId) {
    cfgId = String(cfgId == null ? "" : cfgId);
    for (var i = 0; i < state.cfgs.length; i++) {
        if (String(state.cfgs[i].id) === cfgId) { return state.cfgs[i]; }
    }
    return null;
}

/** 企微租户显示名（取不到时退化为“企微租户 ID n”） */
function cfgName(cfgId) {
    var cfg = cfgById(cfgId);
    return cfg ? (cfg.corp_name || P_SHORT + "租户 ID " + cfg.id) : P_SHORT + "租户 ID " + cfgId;
}

/** 该分部当前显示的企微租户ID：已保存的绑定优先，其次弹框内“已选未落库”的选择 */
function cfgCurrentValue(subId) {
    subId = String(subId);
    var cur = boundBySub[subId];
    if (cur && String(cur.cfg_id) !== "") { return String(cur.cfg_id); }
    if (cfgPickValues[subId]) { return String(cfgPickValues[subId]); }
    return "";
}

function cfgCurrentLabel(subId) {
    var value = cfgCurrentValue(subId);
    return value ? cfgName(value) : "";
}

function cfgPickerHtml(subId, disabled) {
    var value = cfgCurrentValue(subId);
    var label = cfgCurrentLabel(subId);
    var dis = disabled ? " disabled" : "";
    var placeholder = disabled ? "异常记录不可绑定" : "请点击选择" + P_SHORT + "租户";
    return '<div class="wx-picker' + (disabled ? " disabled" : "") + '" data-sub="' + esc(subId) + '">'
        + '<input type="text" class="wx-picker-text" readonly' + dis + ' value="' + esc(label) + '"'
        + ' placeholder="' + esc(placeholder) + '" title="点击弹出" + P_SHORT + "租户列表选择（双击可直接确定）">'
        + '<input type="hidden" class="wx-picker-value" value="' + esc(value) + '">'
        + '<span class="wx-picker-arrow">▾</span>'
        + '</div>';
}

/** 把行内选择框刷新为“已保存绑定 / 弹框内已选”的值（保存失败与取消时回退也走这里） */
function updateCfgPickerDom(subId) {
    var picker = document.querySelector("#subBody .wx-picker[data-sub='" + subId + "']");
    if (!picker) { return; }
    var value = cfgCurrentValue(subId);
    var hidden = picker.querySelector(".wx-picker-value");
    var text = picker.querySelector(".wx-picker-text");
    if (hidden) { hidden.value = value; }
    if (text) { text.value = value ? cfgCurrentLabel(subId) : ""; }
}

function cfgPickOptions(keyword) {
    var rows = (state.cfgs || []).slice();
    var k = (keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (c) {
        var text = [c.id, c.corp_name, c.corp_id, c.root_dept_id].map(function (v) {
            return String(v == null ? "" : v);
        }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
}

function showCfgPickModal(subId) {
    var picker = document.querySelector("#subBody .wx-picker[data-sub='" + subId + "']");
    if (!picker || picker.classList.contains("disabled")) { return; }
    cfgPickSubId = String(subId);
    cfgPickBackup = cfgPickValues[cfgPickSubId] || "";
    cfgPickId = cfgCurrentValue(cfgPickSubId);
    cfgPickLabel = cfgCurrentLabel(cfgPickSubId);
    var subName = "";
    subRows().forEach(function (s) {
        if (String(s.id) === String(subId)) { subName = s.name || ""; }
    });
    byId("cfgPickTip").textContent = (subName
        ? "为 OA 分部“" + subName + "”选择" + P_SHORT + "租户"
        : "为 OA 分部（ID " + subId + "）选择" + P_SHORT + "租户")
        + "：单击选中，双击该行（或点“确定”）立即完成绑定";
    byId("cfgPickSearch").value = "";
    renderCfgPickList();
    byId("cfgPickMask").style.display = "block";
    byId("cfgPickModal").style.display = "block";
    byId("cfgPickSearch").focus();
}

function hideCfgPickModal() {
    byId("cfgPickMask").style.display = "none";
    var modal = byId("cfgPickModal");
    if (modal) { modal.style.display = "none"; }
    cfgPickSubId = "";
}

function renderCfgPickList() {
    var listEl = byId("cfgPickList");
    var rows = cfgPickOptions(byId("cfgPickSearch").value);
    var html;
    if (!rows.length) {
        html = "<div class='wx-pick-empty'>没有匹配的" + P_SHORT + "租户</div>";
    } else {
        html = "<div class='wx-pick-head'><span>" + P_SHORT + "租户</span><span>" + P_KeyLabel + " / 根部门</span></div>";
    }
    rows.forEach(function (c) { html += cfgPickOptionHtml(c); });
    listEl.className = "wx-pick-list";
    listEl.innerHTML = html;
}

function cfgPickOptionHtml(c) {
    var id = String(c.id);
    var active = cfgPickId === id ? " active" : "";
    var owner = boundSubByCfg[id];
    var ownerSubId = owner ? String(owner.oa_sub_id) : "";
    // 一对一：租户已被其他分部绑定时不可选
    var takenByOther = !!owner && ownerSubId !== String(cfgPickSubId);
    var cls = "wx-pick-option" + active + (takenByOther ? " disabled" : "");
    var name = c.corp_name || (P_SHORT + "租户 ID " + id);
    var tip = takenByOther
        ? "该" + P_SHORT + "租户已被 OA 分部「" + (oaSubLabel(ownerSubId) || "ID " + ownerSubId) + "」绑定（一对一），"
            + "需先解除该分部绑定才能改绑"
        : name + (c.corp_id ? "（" + P_KeyLabel + " " + c.corp_id + "）" : "") + "；双击即完成绑定";
    return "<button type='button' class='" + cls + "'"
        + " data-id='" + esc(id) + "'"
        + " title='" + esc(tip) + "'>"
        + "<span class='wx-pick-opt-id'>" + esc(name)
        + (takenByOther ? "<span class='tag nobind'>已被 " + esc(oaSubLabel(ownerSubId) || "其他分部") + " 绑定</span>" : "")
        + "</span>"
        + "<span class='wx-pick-opt-name'>" + esc(c.corp_id || "-")
        + "　根部门 " + esc(c.root_dept_id == null || String(c.root_dept_id) === "" ? "-" : c.root_dept_id)
        + (String(c.sync_enable) === "1" ? "" : "　<span class='tag off'>停用</span>")
        + "</span>"
        + "</button>";
}

/** 单击选项：只就地选中（弹框内高亮 + 行内预览），落库由双击 / “确定”触发 */
function pickCfgOption(btn) {
    if (btn.classList.contains("disabled")) {
        showToast("该" + P_SHORT + "租户已被其他 OA 分部绑定（一对一），请先解除该分部绑定后再选择", 1);
        return;
    }
    if (!cfgPickSubId) { return; }
    cfgPickId = btn.getAttribute("data-id");
    cfgPickLabel = cfgName(cfgPickId);
    cfgPickValues[String(cfgPickSubId)] = String(cfgPickId);
    var opts = byId("cfgPickList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        opts[i].classList.remove("active");
    }
    btn.classList.add("active");
    updateCfgPickerDom(String(cfgPickSubId));
}

/** 双击 / “确定”即落库：页面上不再需要额外的保存按钮 */
function confirmCfgPick() {
    if (!cfgPickSubId) { return; }
    var subId = String(cfgPickSubId);
    var cfgId = String(cfgPickId || "");
    if (!cfgId) {
        showToast("请先选择" + P_SHORT + "租户（单击选中后双击或点击“确定”）", 1);
        return;
    }
    hideCfgPickModal();
    saveSubBind(subId, cfgId);
}

/** 取消/关闭弹框：放弃弹框内的本次选择，行内显示回到打开弹框前的状态 */
function cancelCfgPick() {
    if (cfgPickSubId) {
        var subId = String(cfgPickSubId);
        if (cfgPickBackup) { cfgPickValues[subId] = cfgPickBackup; } else { delete cfgPickValues[subId]; }
        updateCfgPickerDom(subId);
    }
    hideCfgPickModal();
}

/** 清空选择：只清掉弹框内/行内未落库的预览选择，不影响已保存的绑定 */
function clearCfgPickSelection() {
    if (!cfgPickSubId) { return; }
    cfgPickId = "";
    delete cfgPickValues[String(cfgPickSubId)];
    updateCfgPickerDom(String(cfgPickSubId));
    renderCfgPickList();
}

function filterSubRows() {
    var fbIds = (state.subFbIds || []).map(String);
    return subRows().filter(function (r) {
        if (!fbIds.length) { return true; }
        // 选中分部自动含其下级分部（选择时已展开为全部下级）
        return fbIds.indexOf(String(r.id)) >= 0;
    });
}

function clearSubFilter() {
    state.subFbIds = [];
    renderSubManage();
}

function resetSubFilters() {
    state.subFbIds = [];
    renderSubManage();
}

/* ------------------------------------------------------------------
 * 分部筛选弹框：多选；选中分部时自动含其全部下级分部。
 * ------------------------------------------------------------------ */
function subChildMap() {
    var map = {};
    oaSubOptions.forEach(function (s) {
        var p = String(s.parent == null ? "" : s.parent);
        if (!map[p]) { map[p] = []; }
        map[p].push(String(s.id));
    });
    return map;
}

function subDescendantIds(id) {
    var map = subChildMap();
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

function expandSubIds(ids) {
    var out = [];
    var seen = {};
    (ids || []).forEach(function (id) {
        subDescendantIds(id).forEach(function (v) {
            if (seen[v]) { return; }
            seen[v] = true;
            out.push(v);
        });
    });
    return out;
}

function subPickRows(keyword) {
    var rows = oaSubOptions.slice();
    var k = (keyword == null ? byId("subPickSearch").value : keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (d) {
        var text = [d.id, d.code, d.name, d.fullname].map(function (v) {
            return String(v == null ? "" : v);
        }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
}

function subPickAllIds() {
    return subPickRows(null).map(function (d) { return String(d.id); });
}

function syncSubPickAllState() {
    var ids = subPickAllIds();
    var allOn = ids.length > 0 && ids.every(function (id) { return subPickSelected.indexOf(id) >= 0; });
    OaonesPicker.syncSelectAll("subPick", allOn);
    return allOn;
}

OaonesPicker.setSelectAll("subPick", function () {
    var ids = subPickAllIds();
    var on = syncSubPickAllState();
    var next;
    if (on) {
        var drop = {};
        ids.forEach(function (id) {
            subDescendantIds(id).forEach(function (v) { drop[v] = true; });
        });
        next = subPickSelected.filter(function (v) { return !drop[v]; });
    } else {
        next = expandSubIds(subPickSelected.concat(ids));
    }
    var seen = {};
    subPickSelected = next.filter(function (v) {
        v = String(v);
        if (seen[v]) { return false; }
        seen[v] = true;
        return true;
    });
    renderSubPickList();
    return syncSubPickAllState();
});

function subPickOptionHtml(d, partialMap) {
    var id = String(d.id);
    var active = subPickSelected.indexOf(id) >= 0;
    var partial = !active && !!partialMap && !!partialMap[id];
    var cls = "wx-pick-option pick-cols3" + (active ? " active" : (partial ? " partial" : ""));
    var tip = (active ? "点击取消选择" : "点击选择")
        + "；选中分部会自动选中其全部下级分部｜" + (d.fullname || d.name || "")
        + (d.code ? "（编码 " + d.code + "）" : "");
    return "<button type='button' class='" + cls + "'"
        + " data-id='" + esc(id) + "'"
        + " title='" + esc(tip) + "'>"
        + "<span>" + esc(id) + "</span>"
        + "<span>" + esc(d.code || "-") + "</span>"
        + "<span>" + esc(d.name || "-") + "</span>"
        + "</button>";
}

function renderSubPickList() {
    byId("subPickTitle").textContent = subPickSelected.length
        ? "选择分部（已选 " + subPickSelected.length + " 个）"
        : "选择分部";
    syncSubPickAllState();
    var listEl = byId("subPickList");
    var mode = subViewMode("subPick");
    var rows = subPickRows(mode === "tree" ? "" : null);
    var partialMap = partialSelectedMap(subPickSelected, subChildMap);
    var html;
    if (mode === "tree") {
        html = renderSubTreeHtml("subPick", rows, {
            parentOf: function (d) { return d.parent; },
            textOf: function (d) { return [d.id, d.code, d.name, d.fullname].join(" "); },
            keyword: byId("subPickSearch").value,
            emptyText: "没有匹配的分部",
            optionHtml: function (d) { return subPickOptionHtml(d, partialMap); }
        });
    } else {
        html = "";
        if (!rows.length) {
            html = "<div class='wx-pick-empty'>没有匹配的分部</div>";
        } else {
            html = "<div class='wx-pick-head pick-cols3'><span>ID</span><span>编码</span><span>简称</span></div>";
        }
        rows.forEach(function (d) { html += subPickOptionHtml(d, partialMap); });
    }
    listEl.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
    listEl.innerHTML = html;
    byId("subPickNote").textContent = "支持多选，选中分部时其下级分部自动一并选中；"
        + "筛选只影响列表展示范围，不影响绑定关系";
}

function openSubPick(type) {
    subPickType = type;
    subPickSelected = (state.subFbIds || []).slice();
    byId("subPickTitle").textContent = "选择分部";
    byId("subPickSearch").value = "";
    // 树状默认收起分支：每次打开弹框重置展开状态
    OaonesPicker.resetTree("subPick");
    renderSubPickList();
    byId("subPickMask").style.display = "block";
    byId("subPickModal").style.display = "block";
    byId("subPickSearch").focus();
}

function hideSubPick() {
    byId("subPickMask").style.display = "none";
    byId("subPickModal").style.display = "none";
    subPickType = "";
    subPickSelected = [];
}

function toggleSubPick(btn) {
    var id = btn.getAttribute("data-id");
    var idx = subPickSelected.indexOf(id);
    if (idx >= 0) {
        // 取消某个分部时连同它的下级分部一起取消（与“选中含下级”对应）
        var removes = subDescendantIds(id);
        subPickSelected = subPickSelected.filter(function (v) { return removes.indexOf(v) < 0; });
    } else {
        subPickSelected = expandSubIds(subPickSelected.concat([id]));
    }
    var seen = {};
    subPickSelected = subPickSelected.filter(function (v) {
        v = String(v);
        if (seen[v]) { return false; }
        seen[v] = true;
        return true;
    });
    renderSubPickList();
}

function applySubPick() {
    if (subPickType === "fb") {
        state.subFbIds = expandSubIds(subPickSelected);
    }
    hideSubPick();
    renderSubManage();
}

function clearSubPickSelection() {
    subPickSelected = [];
    renderSubPickList();
}

/* ------------------------------------------------------------------
 * 列表渲染：一行一个 OA 分部，右侧选择企微租户（双击 / 确定即落库）
 * ------------------------------------------------------------------ */

/** 绑定异常口径：OA 分部已封存/已删除，或绑定的企微租户已不存在/已从可见范围移除 */
function subIssueText(bind) {
    if (!bind) { return ""; }
    if (String(bind.oa_sub_canceled) === "1") {
        return "分部绑定异常（OA 分部已封存/已删除）";
    }
    if (!cfgById(bind.cfg_id)) {
        return "分部绑定异常（绑定的" + P_SHORT + "租户已不存在或你已无该租户权限）";
    }
    return "";
}

function renderSubManage() {
    renderSubFilterInput();
    var rows = filterSubRows();
    subPager.resetIfChanged(subFilterSignature());
    var pageRows = subPager.apply(rows);
    renderSubStats(rows);
    var body = "";
    pageRows.forEach(function (s) {
        var subId = String(s.id);
        var bind = boundBySub[subId];
        var issue = subIssueText(bind);
        if (!issue && String(s.canceled) === "1") {
            issue = "分部绑定异常（OA 分部已封存/已删除）";
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
        if (bind) {
            var cfg = cfgById(bind.cfg_id);
            note += "<div class='user-mini'>当前：" + esc(cfgName(bind.cfg_id))
                + (cfg && cfg.corp_id ? "（" + P_KeyLabel + " " + esc(cfg.corp_id) + "）" : "")
                + "　根部门：" + esc(cfg && cfg.root_dept_id != null && String(cfg.root_dept_id) !== ""
                    ? cfg.root_dept_id : "-")
                + "</div>";
        }
        if (issue) {
            note += "<div class='row-note'>" + esc(issue) + "</div>";
        }
        var ops = "";
        if (bind) {
            ops += "<button class='link danger unbind-btn' data-id='" + esc(bind.id)
                + "' data-cfg='" + esc(bind.cfg_id) + "'>解绑</button>";
        }
        var parentId = s.parent == null ? "" : String(s.parent);
        var parentName = parentId && parentId !== "0" ? oaSubLabel(parentId) : "";
        body += "<tr>"
            + "<td><div><b>" + OaonesHrm.sub(subId, s.name) + "</b>（ID " + esc(subId) + "）</div>"
            + "<div class='user-mini'>"
            + (parentId && parentId !== "0"
                ? "上级分部：" + (parentName ? OaonesHrm.sub(parentId, parentName) : esc(parentId)) : "上级分部：-")
            + "</div></td>"
            + "<td><div class='pick-col'>" + cfgPickerHtml(subId, disabled) + "</div>"
            + note + "</td>"
            + "<td>" + statusHtml + "</td>"
            + "<td>" + ops + "</td>"
            + "</tr>";
    });
    byId("subBody").innerHTML = body;
    subPager.show();
    subPager.render();
    OaonesTable.resizable(byId("subTable"), { key: "subBind.subTable" });
    byId("subEmpty").style.display = pageRows.length ? "none" : "block";
    byId("subEmpty").textContent = (state.subFbIds && state.subFbIds.length)
        ? "没有匹配的 OA 分部" : "暂无 OA 分部（请检查 OA 组织架构中是否已维护分部）";
}

/** 筛选条件签名：任一条件变化都要回到第 1 页（分部筛选） */
function subFilterSignature() {
    return (state.subFbIds || []).join(",");
}

function renderSubStats(rows) {
    rows = rows || subRows();
    var total = rows.length;
    var bound = 0;
    var unbound = 0;
    rows.forEach(function (s) {
        if (boundBySub[String(s.id)]) { bound++; } else { unbound++; }
    });
    byId("subStats").innerHTML = ""
        + "<span class='bind-stat'><span>分部共计</span><b>" + total + "</b> 个</span>"
        + "<span class='bind-stat'><span>已绑定分部</span><b>" + bound + "</b> 个</span>"
        + "<span class='bind-stat'><span>未绑定分部</span><b>" + unbound + "</b> 个</span>"
        + "<span class='bind-stat'><span>可维护" + P_SHORT + "租户</span><b>" + (state.cfgs || []).length + "</b> 个</span>";
}

/* 行内解绑：只解除分部与企微租户的绑定，不动已建立的部门绑定 */
byId("subBody").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("unbind-btn")) {
        delBind(t.getAttribute("data-id"), t.getAttribute("data-cfg"));
    }
});
</script>
