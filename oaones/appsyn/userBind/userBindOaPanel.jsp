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

<%-- 二级菜单：OA数据基准（A 页，人员绑定 - OA 人员侧） --%>
<%-- 页面说明正文：由页壳 sub-tabs 右侧的感叹号（#oaHelpTip）悬浮/点击弹出 --%>
<div class="help-source" id="oaHelpText">OA 数据基准列表默认不展示，可先在分部/部门弹出框中选择（选择后自动搜索），也可输入姓名/登录名/工号/手机号/ID 后点击“搜索”，或直接点击“搜索”查看全部 OA 人员；点击“重置筛选”清空分部/部门/关键字。行内按钮统一放在“<%= oaonesPBShort %>成员”列（列表不再单独设“操作”列）：已绑定行是“解除绑定”；未绑定行是“免绑定”，所在部门已绑定<%= oaonesPBShort %>部门时还会出现“同步人员”；免绑定行是“取消免绑定”。绑定统一由该列的选择框点选成员完成（不再提供“绑定”按钮）。点击行内选择框会从<%= oaonesPBShort %>人员快照中弹出成员列表，点击成员即立即保存绑定；已绑定的行重新弹出选择并点击其他成员即立刻更换（因此不再提供“保存更换”按钮），也可直接点“解除绑定”。不需要绑定<%= oaonesPBShort %>成员的 OA 人员（如系统账号、外部人员）可点“免绑定”标记：标记后该人员不参与默认批量绑定与同步人员，状态显示为“免绑定”，同一列可“取消免绑定”回到未绑定状态。OA 到<%= oaonesPBShort %>的人员绑定支持“多对一”：多个 OA 账户可对应<%= oaonesPBShort %>同一个账户，同一<%= oaonesPBShort %>成员可被多个 OA 人员绑定，因此已被其他 OA 人员绑定的成员仍会出现在候选里，选项上标注“已被 N 个 OA 人员绑定”，核对后点击即可继续建立绑定。“默认批量绑定”按“默认绑定规则”页配置的规则，为当前筛选范围内未绑定 OA 人员匹配<%= oaonesPBShort %>成员；选择“仅显示匹配”时匹配结果只回填到选择框、不落库，核对后点开行内选择框，点选已带入的成员即保存绑定。“部门绑定”列显示两侧部门是否已建立绑定；部门已绑定但人员未绑定时，该列出现“同步人员”，点击后按部门绑定关系把该 OA 人员新建/更新到对应<%= oaonesPBShort %>部门并自动建立人员绑定。
           行首复选框用于批量操作（仅“未绑定”的行可勾选，已绑定与免绑定的行复选框不可用）：表头复选框只全选当前页，要一次选中当前筛选条件下的全部人员（含未翻到的页）请勾选表头上方（与表头复选框对齐）的“跨页全选”，全部选中后可点统计行末尾的“仅显示已选择数据”只看已勾选行核对。批量“同步人员”按部门绑定关系把勾选的 OA 人员逐条同步到<%= oaonesPBShort %>并建立人员绑定，同步完成后弹出结果框，成功人数如上，失败人员按列表逐条给出失败原因（例如“所在部门没有进行绑定，请手动创建人员或先进行部门绑定”），失败人员会保持勾选，按提示处理后可再次点击“同步人员”重试；勾选后也可点击“批量免绑定”，一次把勾选的 OA 人员标记为免绑定（免绑定人员不参与默认批量绑定与同步人员，可取消免绑定后再次绑定），失败的 OA 人员同样保持勾选。</div>
        <div id="managePanel">
            <div class="filter-bar">
                <span class="filter-label">OA 人员筛选：</span>
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
                <input type="text" id="oaKeyword" placeholder="姓名/登录名/工号/手机号/ID" autocomplete="off" style="min-width:220px;max-width:300px;">
                <button type="button" class="btn small" id="oaSearchBtn">搜索</button>
                <button type="button" class="btn small secondary" id="oaResetBtn">重置筛选</button>
                <button type="button" class="btn small" id="defaultBindBtn"
                        title="按“默认绑定规则”为当前列表中的未绑定 OA 人员匹配<%= oaonesPBShort %>成员">默认批量绑定</button>
                    <button type="button" class="btn small secondary" id="syncCheckedBtn"
                            title="把勾选的 OA 人员按部门绑定关系同步到<%= oaonesPBShort %>并建立人员绑定；同步失败的人员会在结果弹框中列出失败原因">同步人员</button>
                    <button type="button" class="btn small secondary" id="batchOaNoBindBtn"
                            title="把勾选的 OA 人员一次标记为免绑定（行首复选框仅“未绑定”人员可用）：免绑定人员不参与默认批量绑定与同步人员，可取消免绑定后再次绑定">批量免绑定</button>
                    <span class="muted" id="oaCheckedTip"></span>
            </div>
            <div class="bind-stats" id="oaStats" style="display:none;"></div>
            <div class="table-wrap">
                <table class="grid" id="manageTable">
                    <thead>
                    <tr>
                        <th style="width:36px;"><input type="checkbox" id="oaCheckAll"
                                                       title="全选/取消全选当前页可同步（未绑定）的 OA 人员；要选中全部（含未翻到的页）请用表头上方的“跨页全选”"></th>
                        <th style="width:22%;">OA 人员</th>
                        <th style="width:30%;"><%= oaonesPBShort %>成员</th>
                        <th style="width:14%;">部门绑定</th>
                        <th>绑定方式</th>
                        <th>绑定原因</th>
                        <th>状态</th>
                    </tr>
                    </thead>
                    <tbody id="manageBody"></tbody>
                </table>
                <div class="empty" id="manageEmpty" style="display:none;">暂无 OA 人员</div>
            </div>
            <div class="pager" id="oaPager"></div>
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

<div class="modal-mask" id="wxPickMask"></div>
<div class="modal wx-pick-modal" id="wxPickModal">
    <div class="modal-head">
        <span id="wxPickTitle">选择<%= oaonesPBShort %>成员</span>
        <button type="button" class="link" id="wxPickCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="wx-pick-tip" id="wxPickTip"></div>
        <div class="pick-view-bar">
            <input type="text" id="wxPickSearch" placeholder="输入姓名/ID/部门搜索" autocomplete="off">
            <div class="view-switch mini" id="wxPickViewSwitch" data-view-key="wxPick">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="wxPickList"></div>
        <div class="muted wx-pick-note">只展示人员快照中状态正常的<%= oaonesPBShort %>成员，已标记“免绑定”的成员不可选；OA 到<%= oaonesPBShort %>为“多对一”（多个 OA 账户对应同一个<%= oaonesPBShort %>账户），同一<%= oaonesPBShort %>成员可被多个 OA 人员绑定，已被其他 OA 人员绑定的成员仍可选中，选项上会标注“已被 N 个 OA 人员绑定”。点击成员行后立即保存绑定（列表行内不再有“绑定”按钮）。树状模式按<%= oaonesPBShort %>部门分组展示。</div>
    </div>
</div>

<div class="modal-mask" id="matchMask"></div>
<div class="modal" id="matchModal">
    <div class="modal-head">
        <span>默认批量绑定</span>
        <button type="button" class="link" id="matchCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div id="matchMsg" style="font-size:13px;line-height:1.9;color:var(--oa-g-900);"></div>
    </div>
<div class="modal-foot">
        <button type="button" class="btn secondary" id="matchNoBtn">否，仅显示匹配</button>
        <button type="button" class="btn" id="matchYesBtn">是，保存并仅显示匹配</button>
    </div>
</div>

<%-- 批量“同步人员”结果：成功统计 + 失败人员列表（标注失败原因） --%>
<div class="modal-mask" id="syncResultMask"></div>
<div class="modal sync-result-modal" id="syncResultModal">
    <div class="modal-head">
        <span>同步人员结果</span>
        <button type="button" class="link" id="syncResultCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body" id="syncResultBody"></div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="syncResultRetryBtn"
                title="仅保留失败人员勾选并重新同步">重试失败人员</button>
        <button type="button" class="btn" id="syncResultOkBtn">知道了</button>
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
/** 正在保存绑定的 OA 人员 ID，避免同一行重复提交 */
var bindSavingOa = {};
/** 行首复选框选中的 OA 人员 ID（仅当前列表内未绑定的人员） */
var oaCheckedIds = {};
/** 当前筛选条件下可勾选（未绑定）的全部 OA 人员 ID：跨页全选与勾选数量都按它统计 */
var oaSelectableIds = [];
/** 当前页可勾选（未绑定）的 OA 人员 ID：表头复选框只全选当页，跨页全选另用表头上方的勾选框 */
var oaPageSelectableIds = [];
/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js）：翻页后重绘当前列表 */
var oaPager = OaonesTable.pager({
    key: "userBind.oa",
    el: byId("oaPager"),
    onChange: function () { renderManage(); }
});
/** 批量“同步人员”进行中标记，避免重复提交 */
var oaSyncing = false;
/** 批量“同步人员”每次请求的人数：分片提交，便于展示进度并避免单次请求过长 */
var OA_SYNC_CHUNK_SIZE = 10;

function wxNameOf(wxId) {
    wxId = String(wxId == null ? "" : wxId);
    for (var i = 0; i < wxUsers.length; i++) {
        if (String(wxUsers[i].wx_user_id) === wxId) { return wxUsers[i].wx_name || ""; }
    }
    return "";
}

function oaNameOf(oaId) {
    oaId = String(oaId == null ? "" : oaId);
    for (var i = 0; i < oaUsers.length; i++) {
        if (String(oaUsers[i].id) === oaId) { return oaUsers[i].lastname || ""; }
    }
    return "";
}

function wxCurrentValue(oaId) {
    oaId = String(oaId);
    var cur = boundByOa[oaId];
    var wxId = cur && cur.wx_user_id !== null && cur.wx_user_id !== undefined
        ? String(cur.wx_user_id) : "";
    return wxId;
}

function wxCurrentLabel(oaId) {
    var wxId = wxCurrentValue(oaId);
    if (wxId) { return wxNameOf(wxId) || P_SHORT + "成员ID " + wxId; }
    return "";
}

function wxPickerHtml(oaId) {
    return '<div class="wx-picker" data-oa="' + esc(oaId) + '">'
        + '<input type="text" class="wx-picker-text" readonly value="' + esc(wxCurrentLabel(oaId)) + '"'
        + ' placeholder="请点击选择" + P_SHORT + "成员" title="点击选择" + P_SHORT + "成员">'
        + '<input type="hidden" class="wx-picker-value" value="' + esc(wxCurrentValue(oaId)) + '">'
        + '<span class="wx-picker-arrow">▾</span>'
        + '</div>';
}

function wxPickRows(oaId, keyword) {
    oaId = String(oaId);
    var rows = wxUsers.filter(function (u) {
        var id = String(u.wx_user_id);
        // 已标记免绑定的企微成员不可再被选择（当前 OA 人员已绑定的旧数据除外，避免显示异常）
        // 免绑定取自 uf_oaones_appsyn_user_tmp.no_bind = 0（正常不写值，返回空串）
        if (String(u.no_bind) === "0" && !boundByWx[id]) { return false; }
        // OA → 企微为“多对一”：同一企微成员可被多个 OA 人员绑定（多个 OA 账户对应同一个企微账户），
        // 因此已被其他 OA 人员绑定的成员仍然作为候选，只在选项上打“已被 N 个 OA 人员绑定”标记，
        // 由使用者核对后自行决定是否继续绑定。
        return true;
    });
    var k = (keyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (u) {
        var text = [u.wx_name, u.wx_user_id, u.wx_dept_name].map(function (v) {
            return String(v == null ? "" : v);
        }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
}

function showWxPickModal(oaId) {
    oaId = String(oaId);
    if (bindSavingOa[oaId]) { showToast("该人员绑定保存中，请稍候…", 1); return; }
    var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + oaId + "']");
    if (!picker) { return; }
    wxPickOaId = oaId;
    // 已绑定行带入当前绑定；“默认批量绑定 → 仅显示匹配”只回填未保存的匹配结果时也一并带入，
    // 这样点选被高亮的成员即可保存绑定（行内已不再提供“绑定”按钮）
    var pickerValueEl = picker.querySelector(".wx-picker-value");
    var pickerTextEl = picker.querySelector(".wx-picker-text");
    var staged = pickerValueEl ? String(pickerValueEl.value || "") : "";
    wxPickId = wxCurrentValue(oaId) || staged;
    wxPickValue = wxPickId;
    var oaName = oaNameOf(oaId);
    byId("wxPickTip").textContent = oaName
        ? "为 OA 人员“" + oaName + "”选择" + P_SHORT + "成员"
        : "为 OA 人员（ID " + oaId + "）选择" + P_SHORT + "成员";
    // 默认带入左侧 OA 人员姓名，便于直接定位同名企微成员；
    // 带入未保存的匹配值时清空关键字，避免默认关键字把带回的成员过滤掉、看不到高亮项
    byId("wxPickSearch").value = staged ? "" : oaName;
    // 树状默认收起分支：每次打开弹框重置展开状态
    OaonesPicker.resetTree("wxPick");
    renderWxPickList();
    byId("wxPickMask").style.display = "block";
    byId("wxPickModal").style.display = "block";
    byId("wxPickSearch").focus();
    byId("wxPickSearch").select();
}

function hideWxPickModal() {
    byId("wxPickMask").style.display = "none";
    var modal = byId("wxPickModal");
    if (modal) { modal.style.display = "none"; }
    wxPickOaId = "";
    wxPickId = "";
    wxPickValue = "";
}

function renderWxPickList() {
    var listEl = byId("wxPickList");
    var mode = OaonesPicker.mode("wxPick");
    var keyword = byId("wxPickSearch").value;
    var rows = wxPickRows(wxPickOaId, mode === "tree" ? "" : keyword);
    var html;
    if (mode === "tree") {
        html = OaonesPicker.renderTree("wxPick", wxMemberTreeRows(rows), {
            parentOf: function (r) { return r.parent; },
            textOf: function (r) { return r.searchText; },
            isGroup: function (r) { return r.group === true; },
            groupHtml: function (r, open, hit) { return OaonesPicker.groupHtml("wxPick", r, open, hit); },
            keyword: keyword,
            emptyText: "没有匹配的" + P_SHORT + "成员",
            optionHtml: function (r) { return wxMemberOptionHtml(r.user); }
        });
    } else if (!rows.length) {
        html = "<div class='wx-pick-empty'>没有匹配的" + P_SHORT + "成员</div>";
    } else {
        html = "<div class='wx-pick-head'><span>成员ID</span><span>成员姓名/部门</span></div>";
        rows.forEach(function (u) { html += wxMemberOptionHtml(u); });
    }
    listEl.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
    listEl.innerHTML = html;
}

function wxMemberOptionHtml(u) {
    var id = String(u.wx_user_id);
    var active = wxPickId === id ? " active" : "";
    var tip = (u.wx_name || "") + "（" + id + "）" + (u.wx_dept_name ? " / " + u.wx_dept_name : "");
    var nameText = (u.wx_name || "") + (u.wx_dept_name ? " / " + u.wx_dept_name : "");
    return "<button type='button' class='wx-pick-option" + active + "'"
        + " data-id='" + esc(id) + "'"
        + " title='" + esc(tip) + "'>"
        + "<span class='wx-pick-opt-id'>" + esc(id) + "</span>"
        + "<span class='wx-pick-opt-name'>" + esc(nameText) + wxBoundTag(wxPickOaId, id) + "</span>"
        + "</button>";
}

/**
 * 多对一标记：该企微成员已被“其他 OA 人员”绑定时给出数量与人员明细
 * （当前正在编辑的这条 OA 人员绑定不算，避免与自身冲突提示）。
 */
function wxBoundTag(oaId, wxId) {
    var list = boundListByWx[String(wxId)] || [];
    var others = [];
    list.forEach(function (b) {
        if (String(b.oa_user_id) !== String(oaId)) { others.push(b); }
    });
    if (!others.length) { return ""; }
    var names = others.map(function (b) {
        return (b.oa_name || oaNameOf(b.oa_user_id) || "OA人员") + "（ID " + b.oa_user_id + "）";
    });
    var tip = "已被其他 OA 人员绑定：" + names.join("、")
        + "；同一" + P_SHORT + "成员可被多个 OA 人员绑定（一对多），确认后点击即可为当前 OA 人员也建立绑定";
    return "<span class='tag bound' title='" + esc(tip) + "'>已被 " + others.length + " 个 OA 人员绑定</span>";
}

/** 企微成员树：按企微部门分组，部门为分组节点、成员为可选叶子。 */
function wxMemberTreeRows(rows) {
    var out = [];
    var groups = {};
    (rows || []).forEach(function (u) {
        var deptId = String(u.wx_dept_id == null ? "" : u.wx_dept_id);
        var deptName = String(u.wx_dept_name == null ? "" : u.wx_dept_name);
        var gid = "g:" + (deptId || ("name:" + deptName) || "0");
        if (!groups[gid]) {
            groups[gid] = { group: true, id: gid, parent: "", name: deptName || "未分配部门", count: 0 };
            out.push(groups[gid]);
        }
        groups[gid].count++;
        out.push({
            group: false,
            id: "u:" + String(u.wx_user_id),
            parent: gid,
            user: u,
            searchText: [u.wx_name, u.wx_user_id, u.wx_dept_name].map(function (v) {
                return String(v == null ? "" : v);
            }).join(" ")
        });
    });
    return out;
}

function pickWxOption(btn) {
    wxPickId = btn.getAttribute("data-id");
    wxPickValue = wxPickId;
    var opts = byId("wxPickList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        opts[i].classList.remove("active");
    }
    btn.classList.add("active");
    // 点击成员行后立即回填并关闭弹框
    confirmWxPick();
}

function confirmWxPick() {
    if (!wxPickOaId || !wxPickId) { return; }
    var oaId = String(wxPickOaId);
    var wxId = wxPickValue;
    hideWxPickModal();
    // 在弹框里点选企微成员后立即保存绑定（行内不再有“绑定”按钮）。
    // 选择框显示不在这里回填：保存成功后由 loadData() 按服务端数据重绘，
    // 避免“自行绑定”二次确认被取消时，行内显示了一个并未落库的成员。
    saveBindFromManage(oaId, { auto: true, wxId: wxId });
}

function filterOaUsers() {
    var fbIds = (state.oaFbIds || []).map(String);
    var deptIds = expandDeptIds(state.oaDeptIds || []).map(String);
    var rows = oaUsers.filter(function (u) {
        var subOk = !fbIds.length || fbIds.indexOf(String(u.subcompanyid1 == null ? "" : u.subcompanyid1)) >= 0;
        var deptOk = !deptIds.length || deptIds.indexOf(String(u.departmentid == null ? "" : u.departmentid)) >= 0;
        return subOk && deptOk;
    });
    return filterByKeyword(rows, ["lastname", "loginid", "workcode", "mobile", "email", "id"], state.oaKeyword);
}

/** 选择框候选行；keyword 为 null 时读取搜索框，树状模式传 "" 由树渲染统一处理关键字。 */
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
        tip: "仅显示所选 " + fbIds.length + " 个分部（含下级）下的部门；选中部门时其下级部门自动一并选中",
        filter: function (d) {
            return fbIds.indexOf(String(d.subcompanyid1 == null ? "" : d.subcompanyid1)) >= 0;
        }
    };
}
OaonesPicker.setPrecondition("oaPick", oaPickPrecondition);

function oaPickRows(keyword) {
    var rows = oaPickType === "fb" ? oaSubOptions.slice() : oaDeptOptions.slice();
    var k = String(keyword == null ? (byId("oaPickSearch").value || "") : keyword).trim().toLowerCase();
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

function oaDeptChildMap() {
    var map = {};
    oaDeptOptions.forEach(function (d) {
        var p = String(d.parent == null ? "" : d.parent);
        if (!map[p]) { map[p] = []; }
        map[p].push(String(d.id));
    });
    return map;
}

function oaDeptDescendantIds(id) {
    var map = oaDeptChildMap();
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

function expandDeptIds(ids) {
    var map = oaDeptChildMap();
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
    var mode = OaonesPicker.mode("oaPick");
    var empty = isSub ? "没有匹配的分部" : "没有匹配的部门";
    // 前置条件过滤（先选分部再选部门）由 OaonesPicker 统一套用，两种显示模式一致
    var rows = OaonesPicker.applyPrecondition("oaPick", oaPickRows(mode === "tree" ? "" : null));
    syncOaPickAllState();
    var childMapFn = isSub ? oaSubChildMap : oaDeptChildMap;
    var partialMap = OaonesPicker.partialSelected(oaPickSelected, childMapFn);
    var listEl = byId("oaPickList");
    var html = "";
    if (mode === "tree") {
        html = OaonesPicker.renderTree("oaPick", rows, {
            parentOf: function (d) { return d.parent; },
            textOf: function (d) { return [d.id, d.code, d.name, d.fullname].join(" "); },
            keyword: byId("oaPickSearch").value,
            emptyText: empty,
            optionHtml: function (d) { return oaPickOptionHtml(d, isSub, partialMap); }
        });
    } else if (!rows.length) {
        html = "<div class='wx-pick-empty'>" + empty + "</div>";
    } else {
        html = "<div class='wx-pick-head pick-cols3'><span>ID</span><span>编码</span><span>简称</span></div>";
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
 * 分部与部门都按本页口径连带全部下级（与单行勾选一致）；已全部选中时按钮变“全不选”。
 */
OaonesPicker.setSelectAll("oaPick", function () {
    var isSub = oaPickType === "fb";
    var ids = oaPickAllIds();
    var on = syncOaPickAllState();
    var next;
    if (on) {
        var drop = {};
        ids.forEach(function (id) {
            (isSub ? oaFbDescendantIds(id) : oaDeptDescendantIds(id)).forEach(function (v) { drop[v] = true; });
        });
        next = oaPickSelected.filter(function (v) { return !drop[v]; });
    } else {
        next = isSub ? expandFbIds(oaPickSelected.concat(ids)) : expandDeptIds(oaPickSelected.concat(ids));
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
        + (isSub ? "；选中分部会自动选中其全部下级分部" : "；选中部门会自动选中其全部下级部门")
        + "；双击直接确定"
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
            var removes = oaDeptDescendantIds(id);
            oaPickSelected = oaPickSelected.filter(function (v) { return removes.indexOf(v) < 0; });
        }
    } else {
        var next = oaPickSelected.concat([id]);
        oaPickSelected = isSub ? expandFbIds(next) : expandDeptIds(next);
    }
    // 只就地刷新选中态，不重建列表：保证双击时命中的是同一个 DOM 节点
    refreshOaPickDom();
}

/** 就地刷新选择框选中态/部分选中态与标题，不重建列表（避免打断双击）。 */
function refreshOaPickDom() {
    var isSub = oaPickType === "fb";
    var partialMap = OaonesPicker.partialSelected(oaPickSelected, isSub ? oaSubChildMap : oaDeptChildMap);
    var opts = byId("oaPickList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        var id = String(opts[i].getAttribute("data-id"));
        var active = oaPickSelected.indexOf(id) >= 0;
        var partial = !active && !!partialMap[id];
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
        state.oaDeptIds = expandDeptIds(oaPickSelected);
    }
    hideOaPick();
    doOaSearch();
}

function clearOaPickSelection() {
    oaPickSelected = [];
    renderOaPickList();
}

function clearOaFbFilter() {
    state.oaFbIds = [];
    state.oaMatchOnlyIds = [];
    defaultMatchData = [];
    renderOaFilterInputs();
    if (state.oaListVisible) { renderManage(); }
}

function clearOaDeptFilter() {
    state.oaDeptIds = [];
    state.oaMatchOnlyIds = [];
    defaultMatchData = [];
    renderOaFilterInputs();
    if (state.oaListVisible) { renderManage(); }
}

function resetOaFilter() {
    state.oaKeyword = "";
    state.oaFbIds = [];
    state.oaDeptIds = [];
    state.oaMatchOnlyIds = [];
    defaultMatchData = [];
    byId("oaKeyword").value = "";
    renderOaFilterInputs();
    if (state.oaListVisible) { renderManage(); }
}

function doOaSearch() {
    state.oaListVisible = true;
    state.oaMatchOnlyIds = [];
    defaultMatchData = [];
    defaultMatchRules = [];
    renderManage();
}

function defaultRulesArr(value) {
    var out = [];
    String(value == null ? "" : value).split(",").forEach(function (v) {
        v = String(v == null ? "" : v).trim();
        if (v && out.indexOf(v) < 0) { out.push(v); }
    });
    return out;
}

function defaultRulesText(rules) {
    var labels = { email: "邮箱", mobile: "手机号", name: "姓名", workcode: "工号" };
    var out = [];
    (rules || []).forEach(function (r) { out.push(labels[r] || r); });
    return out.join("、");
}

function setDefaultBindBtnBusy(busy, text) {
    var btn = byId("defaultBindBtn");
    if (!btn) { return; }
    btn.disabled = !!busy;
    btn.textContent = text == null ? (busy ? "处理中…" : "默认批量绑定") : text;
}

function doDefaultBind() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    setDefaultBindBtnBusy(true, "读取规则中…");
    Oaones.api("user.rule.get", { cfg_id: state.cfgId }, function (res) {
        if (res.code !== 0) {
            setDefaultBindBtnBusy(false);
            showToast("读取当前租户绑定规则失败：" + res.msg, 2);
            return;
        }
        var data = res.data || null;
        var rules = defaultRulesArr(data ? data.rules : "");
        if (!rules.length) {
            setDefaultBindBtnBusy(false);
            showToast("当前租户未配置默认绑定规则，请先在“默认绑定规则”页为默认配置或该租户设置", 1);
            return;
        }
        defaultMatchRules = rules;
        runDefaultMatch();
    });
}

function runDefaultMatch() {
    var rows = filterOaUsers();
    var oaIds = [];
    rows.forEach(function (u) {
        var id = String(u.id);
        // 已绑定、已标记免绑定的 OA 人员都不参与默认批量绑定
        if (!boundByOa[id] && !isOaNoBind(id)) { oaIds.push(id); }
    });
    if (!oaIds.length) {
        setDefaultBindBtnBusy(false);
        showToast("当前 OA 人员中没有未绑定人员，无需默认批量绑定", 1);
        return;
    }
    setDefaultBindBtnBusy(true, "匹配中…");
    Oaones.api("user.autoMatch", {
        cfg_id: state.cfgId,
        rules: defaultMatchRules.join(","),
        oa_ids: oaIds.join(",")
    }, function (res) {
        setDefaultBindBtnBusy(false);
        if (res.code !== 0) {
            showToast("默认匹配失败：" + res.msg, 2);
            return;
        }
        var data = res.data || {};
        var matches = data.matches || [];
        if (!matches.length) {
            showToast("未按默认规则（" + defaultRulesText(defaultMatchRules)
                + "）匹配到可绑定人员：当前未绑定 OA " + (data.oa_unbound_count || 0)
                + " 人、" + P_SHORT + " " + (data.wx_unbound_count || 0)
                + " 人，请确认两侧姓名/字段一致且候选唯一，或先“更新" + P_SHORT + "人员快照”后重试", 1);
            return;
        }
        defaultMatchData = matches;
        byId("matchMsg").innerHTML = "共匹配到 <b>" + matches.length + "</b> 对未绑定人员（规则："
            + esc(defaultRulesText(defaultMatchRules)) + "）。<br>是否保存本次匹配的人员绑定？";
        byId("matchMask").style.display = "block";
        byId("matchModal").style.display = "block";
    });
}

function hideDefaultMatchPrompt() {
    byId("matchMask").style.display = "none";
    byId("matchModal").style.display = "none";
}

/**
 * “仅显示匹配”：把匹配到的企微成员回填到行内选择框（只回填、不落库）。
 * 行内已没有“绑定”按钮，点开该行选择框时会把回填的成员带成高亮项，点一下即保存绑定。
 */
function stageDefaultMatchRows() {
    defaultMatchData.forEach(function (m) {
        var oaId = String(m.oa_user_id);
        var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + oaId + "']");
        if (!picker) { return; }
        var hidden = picker.querySelector(".wx-picker-value");
        var text = picker.querySelector(".wx-picker-text");
        if (hidden) { hidden.value = String(m.wx_user_id); }
        if (text) {
            text.value = (m.wx_name || "") + (m.wx_dept_name ? " / " + m.wx_dept_name : "");
        }
    });
}

function chooseDefaultMatch(save) {
    hideDefaultMatchPrompt();
    var matches = defaultMatchData;
    if (!matches || !matches.length) { return; }
    var oaIds = [];
    var wxIds = [];
    matches.forEach(function (m) {
        oaIds.push(String(m.oa_user_id));
        wxIds.push(String(m.wx_user_id));
    });
    state.oaListVisible = true;
    state.oaMatchOnlyIds = oaIds.slice();
    // 默认批量绑定后要看的是本次匹配结果（可能一条都没勾选），先回到“显示所有数据”
    state.oaOnlyChecked = false;
    renderManage();
    stageDefaultMatchRows();
    if (!save) {
        showToast("已匹配 " + matches.length + " 人（未保存），列表仅显示本次匹配人员；"
            + "核对后点开行内选择框，点选已带入的成员即保存绑定");
        return;
    }
    setDefaultBindBtnBusy(true, "保存中…");
    Oaones.api("user.batchSave", {
        cfg_id: state.cfgId,
        oa_ids: oaIds.join(","),
        wx_ids: wxIds.join(","),
        rules: defaultMatchRules.join(",")
    }, function (res) {
        setDefaultBindBtnBusy(false);
        if (res.code !== 0) {
            showToast("批量保存失败：" + res.msg, 2);
            return;
        }
        var data = res.data || {};
        var msg = "默认批量绑定已保存，成功 " + (data.success || 0) + " 条";
        if ((data.fail || 0) > 0) { msg += "，失败 " + data.fail + " 条"; }
        showToast(msg);
        // 保存后重新加载绑定状态，并继续仅显示本次匹配人员
        loadData();
    });
}

/** 当前筛选条件下已勾选（可同步）的 OA 人员数量：跨页统计，用于“已选 N / 共 M”与跨页全选条 */
function oaCheckedCount() {
    var checked = 0;
    (oaSelectableIds || []).forEach(function (id) {
        if (oaCheckedIds[id]) { checked++; }
    });
    return checked;
}

/** 当前页已勾选（可同步）的 OA 人员数量：表头复选框只看当页 */
function oaPageCheckedCount() {
    var checked = 0;
    (oaPageSelectableIds || []).forEach(function (id) {
        if (oaCheckedIds[id]) { checked++; }
    });
    return checked;
}

/** 当前筛选条件下的可勾选 OA 人员是否已全部勾选（跨页）：决定表头上方跨页全选条的勾选态 */
function oaAllSelected() {
    var total = (oaSelectableIds || []).length;
    return total > 0 && oaCheckedCount() >= total;
}

function renderOaStats(rows) {
    var total = oaUsers.length;
    var bound = 0;
    var noBind = 0;
    oaUsers.forEach(function (u) {
        var id = String(u.id);
        if (boundByOa[id]) { bound++; }
        else if (isOaNoBind(id)) { noBind++; }
    });
    var checked = oaCheckedCount();
    var selectable = (oaSelectableIds || []).length;
    byId("oaStats").innerHTML = ""
        + "<span class='bind-stat'><span>OA 人员共计</span><b>" + total + "</b> 人</span>"
        + "<span class='bind-stat'><span>已绑定</span><b>" + bound + "</b> 人</span>"
        + "<span class='bind-stat'><span>未绑定</span><b>" + (total - bound - noBind) + "</b> 人</span>"
        + "<span class='bind-stat'><span>免绑定</span><b>" + noBind + "</b> 人</span>"
        + "<span class='bind-stat'><span>当前显示</span><b>" + rows.length + "</b> 人</span>"
        // 统计数据之后：仅显示已选择数据 / 显示所有数据（跨页全选已挪到表头上方）
        + OaonesTable.listActionsHtml({
            total: selectable,
            unit: "人",
            what: "可同步的 OA 人员",
            onlyChecked: !!state.oaOnlyChecked,
            checked: checked
        });
}

/** 统计行末尾的“仅显示已选择数据 / 显示所有数据”：只改列表显示范围，不动跨页勾选集合。 */
function toggleOaOnlyChecked() {
    state.oaOnlyChecked = !state.oaOnlyChecked;
    if (state.oaListVisible) { renderManage(); }
}

function bindSourceLabel(bind) {
    if (!bind) { return "—"; }
    var v = String(bind.bind_source == null ? "" : bind.bind_source);
    var cls = v === "1" ? "system" : (v === "2" ? "self" : "manual");
    return "<span class='tag " + cls + "'>" + bindSourceText(v) + "</span>";
}

/**
 * “绑定原因”文案：只取绑定方式字段 uf_oaones_appsyn_user_bind.bind_source，
 * 与备注列 remark 解耦（remark 仅作审计备注，页面与导出一律不展示）。
 * 0 = 手动绑定（页面人工选择成员）；1 = 系统绑定（默认规则自动批量绑定）；
 * 2 = 自行绑定（本人在个人账号中心扫码绑定）。
 */
function bindReasonText(bind) {
    if (!bind) { return "—"; }
    var v = String(bind.bind_source == null ? "" : bind.bind_source);
    if (v === "1") { return "默认规则自动绑定"; }
    if (v === "2") { return "自行绑定"; }
    return "手动绑定";
}

/** “免绑定”按钮：未绑定的 OA 人员行内按钮之一（与“企微成员”列同一列）。 */
function noBindBtnHtml(oaId) {
    return "<button class='link oa-no-bind-btn' data-oa='" + esc(oaId)
        + "' title='该 OA 人员不需要绑定" + P_SHORT + "成员；标记后不参与默认批量绑定与同步人员，可取消免绑定后再次绑定'>免绑定</button>";
}

/**
 * 人员行副标题：登录名 / 工号 / 所在部门，缺项自动跳过。
 * OA 里存在“没有登录名”的在职账号（新建待分配账号、只挂组织不登录的人员），
 * 这类人员同样要出现在列表里，副标题不能因为登录名为空就显示成以“ / ”开头。
 */
function oaUserMiniText(u) {
    var parts = [];
    if (u.loginid) { parts.push(esc(u.loginid)); }
    if (u.workcode) { parts.push(esc(u.workcode)); }
    if (u.departmentname) { parts.push(OaonesHrm.dept(u.departmentid, u.departmentname)); }
    return parts.length ? parts.join(" / ") : "未设置登录名/部门";
}

function renderManage() {
    renderOaFilterInputs();
    var emptyEl = byId("manageEmpty");
    var tableEl = byId("manageTable");
    var statsEl = byId("oaStats");
    byId("manageBody").innerHTML = "";
    if (!state.cfgId) {
        tableEl.style.display = "none";
        statsEl.style.display = "none";
        oaPager.hide();
        oaSelectableIds = [];
        oaPageSelectableIds = [];
        emptyEl.style.display = "block";
        emptyEl.textContent = "请先选择" + P_FULL + "租户";
        clearOaChecked();
        return;
    }
    if (!state.oaListVisible) {
        tableEl.style.display = "none";
        statsEl.style.display = "none";
        oaPager.hide();
        oaSelectableIds = [];
        oaPageSelectableIds = [];
        emptyEl.style.display = "block";
        emptyEl.textContent = "请选择分部/部门或点击“搜索”后查看 OA 人员列表";
        clearOaChecked();
        return;
    }
    tableEl.style.display = "";
    statsEl.style.display = "";
    var rows = filterOaUsers();
    var matchOnlyIds = (state.oaMatchOnlyIds || []).map(String);
    if (matchOnlyIds.length) {
        rows = rows.filter(function (r) { return matchOnlyIds.indexOf(String(r.id)) >= 0; });
    }
// 勾选状态只保留当前列表中仍可同步（未绑定）的人员，避免筛选/同步后出现“看不见的已选人员”
pruneOaChecked(rows);
// 跨页全选：勾选范围是当前筛选条件下的全部可同步人员，而不是当前页
oaSelectableIds = [];
rows.forEach(function (u) {
    var id = String(u.id);
    if (!boundByOa[id] && !isOaNoBind(id)) { oaSelectableIds.push(id); }
});
    // “仅显示已选择数据”：只把显示范围收窄到已勾选的行（含其他页已选），跨页勾选集合不变
    var displayRows = state.oaOnlyChecked
        ? rows.filter(function (u) { return !!oaCheckedIds[String(u.id)]; })
        : rows;
    oaPager.resetIfChanged(oaFilterSignature());
    var pageRows = oaPager.apply(displayRows);
    // 表头复选框只作用于当页：这里记下当前页可勾选的 OA 人员
    oaPageSelectableIds = [];
    pageRows.forEach(function (u) {
        var id = String(u.id);
        if (!boundByOa[id] && !isOaNoBind(id)) { oaPageSelectableIds.push(id); }
    });
    renderOaStats(displayRows);
    var body = "";
pageRows.forEach(function (u) {
    var oaId = String(u.id);
    var bind = boundByOa[oaId];
    var noBind = isOaNoBind(oaId);
    var curWx = bind ? String(bind.wx_user_id) : "";
    var status = bind
        ? "<span class='tag bound'>已绑定</span>"
        : (noBind ? "<span class='tag nobind'>免绑定</span>"
            : "<span class='tag unbound'>未绑定</span>");
        // 部门绑定：OA 人员所在 OA 部门是否已与企微部门建立绑定关系
        var oaDeptId = String(u.departmentid == null ? "" : u.departmentid);
        var deptBind = deptBindOfOa(oaDeptId);
        var deptCell = deptBindCellHtml(deptBind,
            (oaDeptId && oaDeptId !== "0") ? "该 OA 部门未绑定" + P_SHORT + "部门" : "OA 人员未归属部门");
    var ops = "";
    if (bind) {
        // 已绑定行：解除绑定按钮；重新点开行内选择框选择其他成员即立即更换，因此不再提供“保存更换”
        ops = "<button class='link danger unbind-btn' data-id='" + esc(bind.id)
            + "' title='解除该 OA 人员与" + P_SHORT + "成员 " + esc(curWx) + " 的绑定'>解除绑定</button>";
    } else if (noBind) {
        ops = "<button class='link cancel-oa-nobind-btn' data-oa='" + esc(oaId)
            + "' title='取消免绑定后该 OA 人员回到“未绑定”，可再次选择" + P_SHORT + "成员'>取消免绑定</button>";
    } else if (deptBind) {
        ops = noBindBtnHtml(oaId)
            + " <button class='btn small sync-btn' data-oa='" + esc(oaId)
            + "' title='按部门绑定关系把该 OA 人员同步到" + P_SHORT + "部门 "
            + esc(deptBind.wx_dept_id) + " 并建立绑定'>同步人员</button>";
    } else {
        // 未绑定行只有“免绑定”：绑定统一由“企微成员”列的选择框点选成员完成（不再提供“绑定”按钮）
        ops = noBindBtnHtml(oaId);
    }
    var curName = wxNameOf(curWx) || "";
    var note = "";
    if (bind) {
        note = curName
            ? "<span class='row-note'>当前：" + esc(curName) + "（" + esc(curWx) + "）</span>"
            : "<span class='row-note'>当前绑定" + P_SHORT + "成员不在人员快照中（ID " + esc(curWx)
                + "），请先“更新" + P_SHORT + "人员快照”或重新选择</span>";
    } else if (noBind) {
        note = "<span class='row-note'>已标记免绑定：该 OA 人员不需要绑定" + P_SHORT + "成员，"
            + "不参与默认批量绑定与同步人员；取消免绑定后可再次选择</span>";
    } else if (!wxUsers.length) {
        note = "<span class='row-note'>人员快照为空，请先点击“更新" + P_SHORT + "人员快照”</span>";
    }
    // 行首复选框：仅“未绑定”的行可勾选，用于批量“同步人员”/“批量免绑定”
    var checkCell = "<td class='chk-col'><input type='checkbox' class='oa-row-check'"
        + " data-oa='" + esc(oaId) + "'"
        + (bind ? " disabled title='该人员已绑定" + P_SHORT + "成员，无需同步人员'"
            : (noBind ? " disabled title='该 OA 人员已标记免绑定，请先取消免绑定后再批量操作'"
                : " title='勾选后可用上方“同步人员”批量同步到" + P_SHORT + "，或用“批量免绑定”统一标记为免绑定'"))
        + (bind || noBind ? "" : (oaCheckedIds[oaId] ? " checked" : ""))
        + "></td>";
    // “企微成员”列：行内按钮统一放这一列（列表不再单独设“操作”列）——
    // 未绑定行放选择框，已绑定行保留选择框用于更换绑定，免绑定行不放选择框（避免误绑定）
    var pickCell;
    if (noBind) {
        pickCell = "<span class='muted'>已标记免绑定，暂不可选择" + P_SHORT + "成员</span>";
    } else {
        pickCell = "<div class='pick-col'>" + wxPickerHtml(oaId) + "</div>";
    }
    body += "<tr>"
        + checkCell
        + "<td><div><b>" + OaonesHrm.user(oaId, u.lastname) + "</b>（ID " + esc(oaId) + "）</div>"
        + "<div class='user-mini'>" + oaUserMiniText(u) + "</div></td>"
        // 行内操作统一放在“企微成员”列：解除绑定 / 取消免绑定 / 免绑定 / 绑定 / 同步人员
        + "<td>" + pickCell + note + (ops ? "<div class='row-ops'>" + ops + "</div>" : "") + "</td>"
        + "<td>" + deptCell + "</td>"
        + "<td>" + bindSourceLabel(bind) + "</td>"
        + "<td>" + esc(bindReasonText(bind)) + "</td>"
        + "<td>" + status + "</td>"
        + "</tr>";
    });
    byId("manageBody").innerHTML = body;
    oaPager.show();
    oaPager.render();
    OaonesTable.resizable(tableEl, { key: "userBind.oaTable" });
    updateOaCheckedState();
    emptyEl.style.display = displayRows.length ? "none" : "block";
    if (state.oaOnlyChecked) {
        // 只看已选时为空，多半是当前筛选范围内没有勾选（或刚把勾选都取消了）
        emptyEl.textContent = "当前范围没有已勾选的数据，点统计行末尾“显示所有数据”可查看全部";
    } else {
        emptyEl.textContent = (state.oaKeyword || state.oaFbIds.length || state.oaDeptIds.length || state.oaMatchOnlyIds.length)
            ? "没有匹配的 OA 人员" : "暂无 OA 人员";
    }
}

/** 筛选条件签名：任一条件变化都要回到第 1 页（关键字、分部/部门、仅显示匹配、仅显示已选、租户） */
function oaFilterSignature() {
    return [state.cfgId, state.oaKeyword, (state.oaFbIds || []).join(","),
        (state.oaDeptIds || []).join(","), (state.oaMatchOnlyIds || []).join(","),
        state.oaOnlyChecked ? "1" : "0"].join("|");
}

/** 保存中：整行选择框置灰，避免重复选择/重复提交 */
function setOaPickerBusy(oaId, busy) {
    var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + String(oaId) + "']");
    if (!picker) { return; }
    var text = picker.querySelector(".wx-picker-text");
    picker.classList.toggle("wx-picker-busy", !!busy);
    if (text) {
        text.disabled = !!busy;
        if (busy) { text.value = "绑定中…"; }
    }
}

/**
 * 保存某行 OA 人员与企微成员的绑定关系：由“企微成员”列的选择弹框点选成员后立即触发
 * （`confirmWxPick` 传入 opts.auto 与 opts.wxId）；行内不再有“绑定”按钮，
 * 使用轻提示，失败后刷新列表保持页面可继续操作。
 * 若该 OA 人员当前是“自行绑定”（本人扫码建立），更换成员前先二次确认。
 */
function saveBindFromManage(oaId, opts) {
    opts = opts || {};
    var key = String(oaId);
    var wxId = opts.wxId;
    if (wxId === undefined || wxId === null) {
        var picker = document.querySelector("#manageBody .wx-picker[data-oa='" + key + "']");
        var hidden = picker ? picker.querySelector(".wx-picker-value") : null;
        wxId = hidden ? hidden.value : "";
    }
    wxId = String(wxId == null ? "" : wxId);
    if (!wxId) { return; }
    if (bindSavingOa[key]) { return; }
    var cur = boundByOa[key];
    var curWxId = cur && cur.wx_user_id !== null && cur.wx_user_id !== undefined
        ? String(cur.wx_user_id) : "";
    if (curWxId && curWxId === wxId) {
        showToast("该 OA 人员已绑定" + P_SHORT + "成员 " + (wxNameOf(wxId) || wxId) + "，无需重复绑定", 1);
        return;
    }
    if (isSelfBind(cur)) {
        OaonesToast.confirm(SELF_BIND_CONFIRM_MSG, function () {
            doSaveBindFromManage(key, wxId);
        }, { danger: true });
        return;
    }
    doSaveBindFromManage(key, wxId);
}

/** 真正提交绑定保存（saveBindFromManage 的落库部分，二次确认后调用）。 */
function doSaveBindFromManage(key, wxId) {
    if (!wxId) { return; }
    if (bindSavingOa[key]) { return; }
    bindSavingOa[key] = true;
    setOaPickerBusy(key, true);
    // 页面绑定固定为手动绑定（bind_source=0，不传由后端取默认值）：后端据此落库，
    // “绑定方式/绑定原因”两列即取该字段；自行绑定（bind_source=2）只由个人账号中心扫码产生。
    Oaones.api("user.save", {
        cfg_id: state.cfgId,
        oa_user_id: key,
        wx_user_id: wxId
    }, function (res) {
        bindSavingOa[key] = false;
        if (res.code !== 0) {
            showToast("绑定失败：" + res.msg, 2);
            loadData();
            return;
        }
        var oaName = oaNameOf(key);
        showToast("已绑定：" + (oaName ? oaName + " → " : "OA 人员 " + key + " → ")
            + (wxNameOf(wxId) || P_SHORT + "成员") + "（" + wxId + "）");
        loadData();
    });
}

/**
 * A 页（OA 数据基准）“同步人员”：部门已绑定、人员未绑定时，
 * 按部门绑定关系把该 OA 人员新建/更新到对应企微部门并自动建立人员绑定。
 */
function syncOaPerson(oaId) {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var oaUser = null;
    for (var i = 0; i < oaUsers.length; i++) {
        if (String(oaUsers[i].id) === String(oaId)) { oaUser = oaUsers[i]; break; }
    }
    var name = oaUser ? (oaUser.lastname || "") : "";
    var deptBind = deptBindOfOa(oaUser ? oaUser.departmentid : "");
    OaonesToast.confirm("确认把 OA 人员" + (name ? "“" + name + "”" : "（ID " + oaId + "）")
            + "按部门绑定同步到" + P_SHORT
            + (deptBind ? "部门 " + deptBind.wx_dept_id : "")
            + "并建立人员绑定？", function () {
        var btn = document.querySelector("#manageBody .sync-btn[data-oa='" + oaId + "']");
        if (btn) { btn.disabled = true; btn.textContent = "同步中…"; }
        Oaones.api("user.syncToWx", { cfg_id: state.cfgId, oa_user_id: oaId }, function (res) {
            if (btn) { btn.disabled = false; btn.textContent = "同步人员"; }
            if (res.code !== 0) { showToast("同步人员失败：" + res.msg, 2); return; }
            var data = res.data || {};
            showToast("已同步到" + P_SHORT + "：" + (data.wx_created ? "新建" : "更新")
                + P_SHORT + "成员 " + (data.wx_user_id || "") + "，并已建立人员绑定");
            loadData();
        });
    });
}

/**
 * A 页（OA 数据基准）“免绑定”：该 OA 人员不需要绑定企微成员，
 * 标记后不参与默认批量绑定与同步人员，可取消免绑定后再次绑定。
 */
function toggleOaNoBind(btn, mark) {
    var oaId = btn.getAttribute("data-oa");
    if (!state.cfgId || !oaId) { return; }
    var name = oaNameOf(oaId);
    if (mark) {
        OaonesToast.confirm("确认把 OA 人员" + (name ? "“" + name + "”" : "（ID " + oaId + "）")
            + "设置为免绑定？免绑定人员不参与默认批量绑定与同步人员，可取消免绑定后再次绑定。",
            function () { doToggleOaNoBind(btn, mark); });
        return;
    }
    doToggleOaNoBind(btn, mark);
}

function doToggleOaNoBind(btn, mark) {
    var oaId = btn.getAttribute("data-oa");
    btn.disabled = true;
    Oaones.api(mark ? "user.oaNoBind" : "user.oaCancelNoBind", {
        cfg_id: state.cfgId,
        oa_user_id: oaId
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
 * A 页（OA 数据基准）批量“免绑定”：把勾选的（未绑定）OA 人员一次提交给 user.oaBatchNoBind，
 * 后端逐条处理并返回失败原因；失败人员在列表里保持勾选，便于处理后重试。
 */
function batchNoBindCheckedOa() {
    if (oaSyncing) { return; }
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var ids = checkedOaIds();
    if (!ids.length) {
        showToast("请先勾选需要设置免绑定的 OA 人员（行首复选框），再点击“批量免绑定”", 1);
        return;
    }
    OaonesToast.confirm("确认把勾选的 " + ids.length
        + " 名 OA 人员设置为免绑定？免绑定人员不参与默认批量绑定与同步人员，可取消免绑定后再次绑定。",
        function () { runBatchNoBindOa(ids, 0, [], []); });
}

/** 分片批量免绑定：每批 {@link OA_SYNC_CHUNK_SIZE} 人，完成后统一提示成功/失败统计。 */
function runBatchNoBindOa(pending, doneCount, failRows, okIds) {
    var btn = byId("batchOaNoBindBtn");
    var chunk = pending.slice(0, OA_SYNC_CHUNK_SIZE);
    var rest = pending.slice(OA_SYNC_CHUNK_SIZE);
    var total = doneCount + pending.length;
    if (btn) { btn.disabled = true; btn.textContent = "免绑定中 " + (doneCount + chunk.length) + "/" + total + "…"; }
    Oaones.api("user.oaBatchNoBind", { cfg_id: state.cfgId, oa_ids: chunk.join(",") }, function (res) {
        if (res.code !== 0) {
            // 整个请求失败（如租户/权限/网络异常）：本批人员统一记为失败，保留原因
            chunk.forEach(function (id) {
                failRows.push({ oa_user_id: String(id), oa_name: oaNameOf(id), reason: "请求失败：" + (res.msg || "请稍后重试") });
            });
        } else {
            var data = res.data || {};
            var failIds = {};
            (data.fail_rows || []).forEach(function (r) {
                failIds[String(r.oa_user_id)] = true;
                failRows.push({
                    oa_user_id: String(r.oa_user_id),
                    oa_name: oaNameOf(r.oa_user_id),
                    reason: r.reason || "设置免绑定失败"
                });
            });
            chunk.forEach(function (id) {
                if (!failIds[String(id)]) { okIds.push(String(id)); }
            });
        }
        if (rest.length) {
            runBatchNoBindOa(rest, doneCount + chunk.length, failRows, okIds);
            return;
        }
        if (btn) { btn.disabled = false; btn.textContent = "批量免绑定"; }
        // 成功人员取消勾选；失败人员保持勾选，便于按提示处理后重试
        okIds.forEach(function (id) { delete oaCheckedIds[String(id)]; });
        var left = {};
        failRows.forEach(function (r) { left[String(r.oa_user_id)] = true; });
        Object.keys(oaCheckedIds).forEach(function (id) {
            if (!left[id]) { delete oaCheckedIds[id]; }
        });
        renderManage();
        var msg = "批量免绑定完成：成功 " + okIds.length + " 人";
        if (failRows.length) {
            var reasons = [];
            failRows.slice(0, 5).forEach(function (r) {
                var nm = r.oa_name ? r.oa_name : ("ID " + r.oa_user_id);
                reasons.push(nm + "：" + (r.reason || "设置免绑定失败"));
            });
            msg += "，失败 " + failRows.length + " 人（" + reasons.join("；")
                + (failRows.length > 5 ? "；…" : "") + "）";
            showToast(msg, 1, 10000);
        } else {
            showToast(msg);
        }
        loadData();
    });
}

/* ------------------------------------------------------------------
 * A 页（OA 数据基准）行首复选框 + 批量“同步人员”。
 * 勾选多行后点击筛选栏“同步人员”，按部门绑定关系逐条把 OA 人员同步到企微；
 * 同步完成后用弹框列表给出成功统计与失败原因（如“所在部门没有进行绑定，请手动创建人员或先进行部门绑定”）。
 * ------------------------------------------------------------------ */

/** 勾选状态只保留当前列表内仍可批量操作（未绑定且未标记免绑定）的人员。 */
function pruneOaChecked(rows) {
    var keep = {};
    (rows || []).forEach(function (u) {
        var id = String(u.id);
        if (oaCheckedIds[id] && !boundByOa[id] && !isOaNoBind(id)) { keep[id] = true; }
    });
    oaCheckedIds = keep;
}

function clearOaChecked() {
    oaCheckedIds = {};
    updateOaCheckedState();
}

/** 当前筛选条件下已勾选（且可同步）的 OA 人员 ID：含其他页已选，按列表顺序返回。 */
function checkedOaIds() {
    var ids = [];
    (oaSelectableIds || []).forEach(function (id) {
        if (oaCheckedIds[id]) { ids.push(String(id)); }
    });
    return ids;
}

function setSyncCheckedBtnBusy(busy, text) {
    var btn = byId("syncCheckedBtn");
    if (!btn) { return; }
    btn.disabled = !!busy;
    var tip = byId("oaCheckedTip");
    if (text == null) {
        btn.textContent = "同步人员";
        if (tip) { tip.textContent = ""; }
        updateOaCheckedState();
    } else {
        btn.textContent = text;
    }
}

/** 同步勾选状态提示（“已选 N / 共 M 人”）+ 表头当页全选框三态 + 表头上方跨页全选条 + 统计行“仅显示已选”。 */
function updateOaCheckedState() {
    var tip = byId("oaCheckedTip");
    var checked = oaCheckedCount();
    var total = (oaSelectableIds || []).length;
    if (tip) { tip.textContent = checked ? "已选 " + checked + " / 共 " + total + " 人（跨页保留）" : ""; }
    // 表头复选框只反映当前页（当页全选）
    var all = byId("oaCheckAll");
    OaonesTable.checkAllTitle(all, "可同步的 OA 人员");
    OaonesTable.syncCheckAll(all, oaPageCheckedCount(), (oaPageSelectableIds || []).length);
    // 表头上方的“跨页全选”条：左侧复选框与表头复选框同列对齐，勾选/取消即跨页全选/取消
    OaonesTable.crossPageBar(byId("manageTable"), {
        total: total,
        unit: "人",
        allSelected: oaAllSelected(),
        checked: checked,
        what: "可同步的 OA 人员",
        onChange: toggleAllOaChecks
    });
    // 统计行末尾的“仅显示已选择数据 / 显示所有数据”：同步“已选 N / 共 M”（行内勾选后不整表重绘也要准）
    OaonesTable.refreshListActions(byId("oaStats"), {
        total: total,
        unit: "人",
        what: "可同步的 OA 人员",
        onlyChecked: !!state.oaOnlyChecked,
        checked: checked
    });
}

/** 表头复选框：只全选/取消全选当前页可同步的 OA 人员（当页全选）。 */
function toggleOaPageChecks(checked) {
    (oaPageSelectableIds || []).forEach(function (id) {
        if (checked) { oaCheckedIds[id] = true; } else { delete oaCheckedIds[id]; }
    });
    // 勾选集合变了要整表重绘：当前页勾选态、表头三态、统计行按钮一起刷新
    renderManage();
}

/** 跨页全选（表头上方勾选框）：作用于当前筛选条件下全部可同步的行（含未翻到的页）。 */
function toggleAllOaChecks(checked) {
    (oaSelectableIds || []).forEach(function (id) {
        if (checked) { oaCheckedIds[id] = true; } else { delete oaCheckedIds[id]; }
    });
    // 立即刷新当前页行的勾选态（其他页的勾选态在翻页渲染时按 oaCheckedIds 回填）
    renderManage();
}

function syncCheckedOa() {
    if (oaSyncing) { return; }
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    var ids = checkedOaIds();
    if (!ids.length) {
        showToast("请先勾选需要同步人员的 OA 人员（行首复选框），再点击“同步人员”", 1);
        return;
    }
    OaonesToast.confirm("确认把勾选的 " + ids.length + " 名 OA 人员按部门绑定关系同步到" + P_FULL + "并建立人员绑定？", function () {
        oaSyncing = true;
        runSyncCheckedOa(ids, 0, [], []);
    });
}

/**
 * 分片同步：每批 {@link OA_SYNC_CHUNK_SIZE} 人，逐批提交并刷新按钮进度，
 * 全部完成后统一弹出结果框（成功统计 + 失败人员列表）。
 */
function runSyncCheckedOa(pending, doneCount, successRows, failRows) {
    var chunk = pending.slice(0, OA_SYNC_CHUNK_SIZE);
    var rest = pending.slice(OA_SYNC_CHUNK_SIZE);
    var total = doneCount + pending.length;
    setSyncCheckedBtnBusy(true, "同步中 " + (doneCount + chunk.length) + "/" + total + "…");
    Oaones.api("user.syncToWxBatch", { cfg_id: state.cfgId, oa_ids: chunk.join(",") }, function (res) {
        if (res.code !== 0) {
            // 整个请求失败（如租户/网络异常）：本批人员统一记为失败，保留原因
            chunk.forEach(function (id) {
                failRows.push({
                    oa_user_id: String(id),
                    oa_name: oaNameOf(id),
                    oa_dept_name: oaDeptNameByOaId(id),
                    reason: "同步请求失败：" + (res.msg || "请稍后重试")
                });
            });
        } else {
            var data = res.data || {};
            (data.success_rows || []).forEach(function (r) { successRows.push(r); });
            (data.fail_rows || []).forEach(function (r) { failRows.push(r); });
        }
        if (rest.length) {
            runSyncCheckedOa(rest, doneCount + chunk.length, successRows, failRows);
            return;
        }
        finishSyncCheckedOa(successRows, failRows);
    });
}

function finishSyncCheckedOa(successRows, failRows) {
    oaSyncing = false;
    setSyncCheckedBtnBusy(false);
    // 成功人员已在服务端建立绑定，失败人员保持勾选，便于按提示处理后重试
    oaCheckedIds = {};
    failRows.forEach(function (r) { oaCheckedIds[String(r.oa_user_id)] = true; });
    renderManage();
    showSyncResult(successRows, failRows);
    var msg = "同步人员完成：成功 " + successRows.length + " 人";
    if (failRows.length) { msg += "，失败 " + failRows.length + " 人（详见结果框）"; }
    // 全部成功为普通提示；存在失败人员时按警告提示并延长显示时间
    showToast(msg, failRows.length ? 1 : 0, failRows.length ? 8000 : 4000);
    // 重新拉取绑定关系：成功人员立即显示为“已绑定”，失败人员仍保持勾选便于重试
    loadData();
}

/** 失败人员所在部门：页面已加载的 OA 人员里取，取不到显示“—”。 */
function oaDeptNameByOaId(oaId) {
    oaId = String(oaId);
    for (var i = 0; i < oaUsers.length; i++) {
        if (String(oaUsers[i].id) === oaId) { return oaUsers[i].departmentname || ""; }
    }
    return "";
}

/** 失败人员所在 OA 部门ID：用于把“所在部门”渲染成泛微标准部门显示页面链接。 */
function oaDeptIdByOaId(oaId) {
    oaId = String(oaId);
    for (var i = 0; i < oaUsers.length; i++) {
        if (String(oaUsers[i].id) === oaId) { return oaUsers[i].departmentid || ""; }
    }
    return "";
}

/** 结果弹框：成功统计 + 失败人员列表（OA 人员 / 所在部门 / 失败原因）。 */
function showSyncResult(successRows, failRows) {
    successRows = successRows || [];
    failRows = failRows || [];
    var created = 0;
    successRows.forEach(function (r) { if (r.wx_created) { created++; } });
    var html = ""
        + "<div class='sync-result-summary'>"
        + "<span class='bind-stat'><span>本次同步</span><b>" + (successRows.length + failRows.length) + "</b> 人</span>"
        + "<span class='bind-stat'><span>同步成功</span><b>" + successRows.length + "</b> 人</span>"
        + "<span class='bind-stat'><span>同步失败</span><b class='fail-num'>" + failRows.length + "</b> 人</span>"
        + "</div>";
    if (successRows.length) {
        html += "<div class='sync-result-note muted'>成功明细：新建" + P_SHORT + "成员 " + created
            + " 人、更新" + P_SHORT + "成员 " + (successRows.length - created) + " 人，并已自动建立人员绑定。</div>";
    }
    if (failRows.length) {
        html += "<div class='sync-result-title'>以下 <b>" + failRows.length
            + "</b> 人同步失败，请按失败原因处理后重试（失败人员已在列表中保持勾选）：</div>"
            + "<div class='sync-result-list'>"
            + "<div class='sync-result-head'><span>OA 人员</span><span>所在部门</span><span>失败原因</span></div>";
        failRows.forEach(function (r) {
            var oaId = String(r.oa_user_id == null ? "" : r.oa_user_id);
            // 所在部门ID：优先用接口回传的 oa_dept_id，缺失时按当前列表里的 OA 人员反查，保证仍能跳转
            var oaDeptId = (r.oa_dept_id === undefined || r.oa_dept_id === null || r.oa_dept_id === "")
                ? oaDeptIdByOaId(oaId) : r.oa_dept_id;
            var deptName = String(r.oa_dept_name == null ? "" : r.oa_dept_name);
            var name = (r.oa_name ? r.oa_name : "OA 人员") + "（ID " + oaId + "）";
            html += "<div class='sync-result-row'>"
                + "<span class='sync-result-name' title='" + esc(name) + "'>"
                + OaonesHrm.user(oaId, r.oa_name) + "（ID " + esc(oaId) + "）</span>"
                + "<span class='sync-result-dept' title='" + esc(deptName) + "'>"
                + (deptName ? OaonesHrm.dept(oaDeptId, deptName) : "—") + "</span>"
                + "<span class='sync-result-reason'>" + esc(r.reason || "同步人员失败，请稍后重试。") + "</span>"
                + "</div>";
        });
        html += "</div>";
    } else {
        html += "<div class='sync-result-all-ok'>全部同步成功，无失败人员。</div>";
    }
    byId("syncResultBody").innerHTML = html;
    byId("syncResultRetryBtn").style.display = failRows.length ? "" : "none";
    byId("syncResultMask").style.display = "block";
    byId("syncResultModal").style.display = "block";
}

function hideSyncResult() {
    var mask = byId("syncResultMask");
    var modal = byId("syncResultModal");
    if (mask) { mask.style.display = "none"; }
    if (modal) { modal.style.display = "none"; }
}

/**
 * A 页（OA 数据基准）导出 Excel：导出当前列表的数据，行与列表完全一致
 * （分部/部门、仅显示匹配、仅显示已选等筛选条件都生效），列与页面列一一对应。
 */
function exportOaExcel() {
    if (!state.oaListVisible) {
        showToast("请先选择分部/部门或点击“搜索”展示 OA 人员列表后再导出", 1);
        return;
    }
    var rows = filterOaUsers();
    var matchOnlyIds = (state.oaMatchOnlyIds || []).map(String);
    if (matchOnlyIds.length) {
        rows = rows.filter(function (r) { return matchOnlyIds.indexOf(String(r.id)) >= 0; });
    }
    if (state.oaOnlyChecked) {
        rows = rows.filter(function (r) { return !!oaCheckedIds[String(r.id)]; });
    }
    if (!rows.length) {
        showToast("当前列表没有可导出的数据，请调整筛选条件后重试", 1);
        return;
    }
    var data = [];
    rows.forEach(function (u) {
        var oaId = String(u.id);
        var bind = boundByOa[oaId];
        var wxId = bind ? String(bind.wx_user_id == null ? "" : bind.wx_user_id) : "";
        var oaDeptId = String(u.departmentid == null ? "" : u.departmentid);
        var deptBind = deptBindOfOa(oaDeptId);
        var deptText;
        if (!oaDeptId || oaDeptId === "0") {
            deptText = "OA 人员未归属部门";
        } else if (deptBind) {
            deptText = "已绑定：" + P_SHORT + "部门 "
                + (treeWxDeptName(deptBind.wx_dept_id) || (P_SHORT + "部门 " + deptBind.wx_dept_id))
                + "（" + deptBind.wx_dept_id + "）";
        } else {
            deptText = "该 OA 部门未绑定" + P_SHORT + "部门";
        }
        data.push([
            u.lastname || "",
            oaId,
            u.loginid || "",
            u.workcode || "",
            u.departmentname || oaDeptLabel(oaDeptId),
            oaSubLabel(u.subcompanyid1),
            wxId ? (wxNameOf(wxId) || "") : "",
            wxId,
            deptText,
            bind ? bindSourceText(bind.bind_source) : "—",
            bindReasonText(bind),
            bind ? "已绑定" : (isOaNoBind(oaId) ? "免绑定" : "未绑定")
        ]);
    });
    var ok = OaonesExcel.export({
        filename: "OA数据基准_" + OaonesExcel.stamp() + ".xlsx",
        sheet: "OA数据基准",
        columns: [
            { title: "OA 人员", width: 16 },
            { title: "OA 人员ID", width: 14 },
            { title: "登录名", width: 16 },
            { title: "工号", width: 12 },
            { title: "OA 部门", width: 22 },
            { title: "OA 分部", width: 18 },
            { title: P_SHORT + "成员", width: 16 },
            { title: P_SHORT + "成员ID", width: 16 },
            { title: "部门绑定", width: 32 },
            { title: "绑定方式", width: 12 },
            { title: "绑定原因", width: 22 },
            { title: "状态", width: 10 }
        ],
        rows: data
    });
    if (ok) { showToast("已导出 " + data.length + " 条 OA 人员数据"); }
}

/** 重试失败人员：关闭结果框，仅保留失败人员勾选后再次同步。 */
function retrySyncChecked() {
    hideSyncResult();
    if (oaSyncing) { return; }
    if (!checkedOaIds().length) {
        showToast("没有可重试的失败人员", 1);
        return;
    }
    syncCheckedOa();
}

/* 批量“同步人员”相关事件绑定 */
byId("syncCheckedBtn").addEventListener("click", syncCheckedOa);
byId("batchOaNoBindBtn").addEventListener("click", batchNoBindCheckedOa);
// 统计行末尾的操作组：仅显示已选择数据（按钮由渲染时拼进统计行，这里用事件委托接住）
// 跨页全选已挪到表头上方的勾选框（OaonesTable.crossPageBar，见 updateOaCheckedState）
byId("oaStats").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("only-checked-btn")) {
        toggleOaOnlyChecked();
    }
});
// 表头复选框：只全选当页
byId("oaCheckAll").addEventListener("change", function () { toggleOaPageChecks(this.checked); });
byId("manageBody").addEventListener("change", function (e) {
    var t = e.target;
    if (!t || !t.classList || !t.classList.contains("oa-row-check")) { return; }
    var id = String(t.getAttribute("data-oa"));
    if (t.checked) { oaCheckedIds[id] = true; } else { delete oaCheckedIds[id]; }
    // “仅显示已选择数据”时取消勾选要立即把该行移出列表，否则只同步勾选态与统计行
    if (state.oaOnlyChecked) { renderManage(); } else { updateOaCheckedState(); }
});
byId("syncResultOkBtn").addEventListener("click", hideSyncResult);
byId("syncResultCloseBtn").addEventListener("click", hideSyncResult);
byId("syncResultRetryBtn").addEventListener("click", retrySyncChecked);
byId("syncResultMask").addEventListener("click", hideSyncResult);

</script>
