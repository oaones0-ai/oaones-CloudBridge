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

<%-- 二级菜单：树状映射（人员绑定 - 分部 / 部门 / 人员的 OA ⇄ 平台 对照树） --%>
<%-- 页面说明正文：由页壳 sub-tabs 右侧的感叹号（#treeHelpTip）悬浮/点击弹出 --%>
<div class="help-source" id="treeHelpText">本页把 OA 组织架构与<%= oaonesPBShort %>通讯录按“分部 → 部门 → 人员”逐级对照展示：左列是 OA 分部、OA 部门、OA 人员，右列是该 OA 部门已绑定的<%= oaonesPBShort %>部门、该 OA 人员已绑定的<%= oaonesPBShort %>账号（分部在<%= oaonesPBShort %>没有对应层级，右列显示“—”）。层级为<span class="rule">分部 → 部门（按 OA 上下级）→ 本部门直属人员</span>，<span class="rule">默认展开</span>，点击节点行左侧箭头或节点行左侧区域可收起/展开（分部名、部门名、人员姓名是泛微标准页面链接，点链接不会收起）；每个部门下面<span class="rule">只挂本部门的直属人员</span>，不含下级部门成员（下级部门单独成行）。分部行、部门行名称右侧的数字是该节点<span class="rule">未绑定<%= oaonesPBShort %>账号</span>的人员数，按“本节点 + 全部下级部门”求和（已标记“免绑定”的人员不需要绑定，不计入该数字，右列显示为“免绑定”）。<%= oaonesPBShort %>部门没有和 OA 部门绑定的不显示，右列显示“未绑定<%= oaonesPBShort %>部门”。上方筛选条件可组合使用：<span class="rule">分部</span>（可多选，选中分部自动含其下级分部）、<span class="rule">部门</span>（可多选，候选项按已选分部过滤，选中部门自动含其下级部门）、<span class="rule">人员关键字</span>（姓名/登录名/工号/<%= oaonesPBShort %>成员姓名或ID，输入即时过滤）；筛选后自动保留命中节点的上级路径，点“重置筛选”清空全部条件。OA 到<%= oaonesPBShort %>为多对一：多个 OA 人员可以绑定同一个<%= oaonesPBShort %>账号，因此同一<%= oaonesPBShort %>账号可能出现在多行；一个 OA 部门只保留一条<%= oaonesPBShort %>部门绑定（在“部门绑定”页维护）。右列<%= oaonesPBShort %>部门名称取自<%= oaonesPBShort %>部门快照，若显示为“<%= oaonesPBShort %>部门 + 数字ID”，请先到“部门绑定”页点击“更新<%= oaonesPBShort %>部门快照”。页面右上角“导出 Excel”可把当前筛选下的映射导出为 .xlsx：列含层级、名称、OA ID、登录名/工号、层级路径、<%= oaonesPBShort %>部门/成员、<%= oaonesPBShort %> ID、状态与未绑定人数（含下级）；导出范围是当前筛选命中的节点及其上级路径，节点收起/展开不影响导出内容。</div>

<div id="treePanel" style="display:none;">
    <div class="filter-bar">
        <span class="filter-label">映射筛选：</span>
        <div class="filter-picker" id="treeFbPicker" title="点击选择分部（可多选，选中分部自动含其下级分部）">
            <input type="text" id="treeFbInput" class="filter-input" readonly placeholder="选择分部（可多选）">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="treeFbClear" title="清除分部筛选">×</button>
        </div>
        <div class="filter-picker" id="treeDeptPicker" title="点击选择部门（可多选，含下级部门；候选受已选分部过滤）">
            <input type="text" id="treeDeptInput" class="filter-input" readonly placeholder="选择部门（可多选）">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="treeDeptClear" title="清除部门筛选">×</button>
        </div>
        <input type="text" id="treeKeyword" placeholder="人员：姓名/登录名/工号/<%= oaonesPBShort %>成员ID" autocomplete="off"
               style="min-width:220px;max-width:320px;" title="按 OA 人员姓名、登录名、工号或其绑定的<%= oaonesPBShort %>成员姓名/ID即时过滤">
        <button type="button" class="btn small secondary" id="treeExpandBtn"
                title="展开全部分部与部门（页面默认即为展开）">展开全部</button>
        <button type="button" class="btn small secondary" id="treeCollapseBtn"
                title="收起全部部门，只保留分部行、部门行与其右侧映射（人员行折叠；分部行保持展开）">收起全部</button>
        <button type="button" class="btn small secondary" id="treeResetBtn"
                title="清空分部、部门、人员三个筛选条件">重置筛选</button>
    </div>
    <div class="muted" id="treeTip" style="margin-bottom:10px;"></div>
    <div class="map-head">
        <div class="map-col">OA 分部 / 部门 / 成员</div>
        <div class="map-col"><%= oaonesPBShort %>部门 / 成员</div>
    </div>
    <div class="map-tree" id="mapTree"></div>
    <div class="empty" id="treeEmpty" style="display:none;">暂无可展示的分部/部门/人员</div>
</div>

<%-- 分部 / 部门 多选弹框（列表式 / 树状 + 全选，交互与“OA数据基准”页的分部/部门筛选一致） --%>
<div class="modal-mask" id="treePickMask"></div>
<div class="modal wx-pick-modal" id="treePickModal">
    <div class="modal-head">
        <span id="treePickTitle">选择分部</span>
        <button type="button" class="link" id="treePickCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="treePickSearch" placeholder="输入名称/编码/ID搜索" autocomplete="off">
            <div class="view-switch mini" id="treePickViewSwitch" data-view-key="treePick">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
            <button type="button" class="btn small secondary pick-all-btn" id="treePickAllBtn" data-pick-all="treePick"
                    title="全选/全不选当前列表（受搜索与前置条件过滤影响）">全选</button>
        </div>
        <div class="wx-pick-list" id="treePickList"></div>
        <div class="muted wx-pick-note" id="treePickNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="treePickClearBtn">清空</button>
        <button type="button" class="btn secondary" id="treePickCancelBtn">取消</button>
        <button type="button" class="btn" id="treePickOkBtn">确定</button>
    </div>
</div>

<script>
"use strict";
/**
 * 面板脚本早于页壳脚本（userBind.jsp 底部）执行：页壳里的 byId 此时还没定义，
 * 面板里的即时事件绑定不能依赖它；这里放一份同实现的本地兜底
 * （页壳随后用同实现的函数覆盖，行为一致）。
 */
function byId(id) { return document.getElementById(id); }

/**
 * “树状映射”页：以 OA 组织架构为骨架的“OA ⇄ 企微”对照树。
 *
 *   - 层级：分部 → 部门（按 OA 上下级）→ 本部门直属人员；左列 OA 对象、右列对应企微对象
 *     （OA 部门已绑定的企微部门、OA 人员已绑定的企微账号；分部在企微没有对应层级，显示“—”）。
 *   - 默认展开；部门下面只挂本部门直属人员，不含下级部门成员（下级部门单独成行）。
 *   - 分部行、部门行名称右侧的数字 = 该节点“未绑定企微账号”的人员数，按
 *     “本节点 + 全部下级部门”求和；已标记“免绑定”的人员不计入（右列显示为“免绑定”）。
 *   - 企微部门没有和 OA 部门绑定的不显示。
 *   - 上方筛选：分部（多选，含下级分部）、部门（多选，含下级部门，候选受已选分部过滤）、
 *     人员关键字（姓名/登录名/工号/企微成员姓名或ID），三者可组合过滤。
 *
 * 数据来自页壳 loadData() 已加载的组织、人员、绑定与快照（含 dept.wxlist 企微部门快照）；
 * 分部/部门候选与“含下级”展开复用“OA数据基准”页（userBindOaPanel.jsp）的公共实现：
 * oaSubLabel / oaDeptLabel（页壳）、oaSubChildMap / oaDeptChildMap / expandFbIds / expandDeptIds
 * / oaDeptDescendantIds / oaFbDescendantIds，不重复实现一份。
 */
/** 企微部门快照（dept.wxlist：id/name/parentid），用于显示已绑定的企微部门名称 */
var wxDepts = [];
/** 已收起的分部/部门节点ID（默认不收起：需求要求“默认展开”） */
var treeCollapsed = {};
/** 人员关键字筛选（姓名/登录名/工号/企微成员姓名或ID） */
var treeKeyword = "";
/** 分部筛选（已含下级分部）、部门筛选（已含下级部门） */
var treeFbIds = [];
var treeDeptIds = [];
/** 上次渲染的租户：切换租户时重置收起状态与全部筛选 */
var treeStateCfg = null;
/** 分部/部门选择弹框：当前选择对象（fb / dept）与待确定的选择 */
var treePickType = "";
var treePickSelected = [];
/** 本次渲染的节点索引、节点列表、顶层节点（分部） */
var treeNodeMap = {};
var treeNodeList = [];
var treeRootIds = [];
/** 本次渲染的汇总（用于筛选栏下方提示） */
var treeTotalSubs = 0;
var treeTotalDepts = 0;
var treeTotalUsers = 0;
var treeTotalUnbound = 0;
/** 本次渲染的过滤上下文（关键字与开关），供递归统计使用 */
var treeCtx = { keyword: "", keywordOn: false };

function treeKey() {
    return treeKeyword == null ? "" : String(treeKeyword).trim().toLowerCase();
}

/** 是否存在筛选条件（分部 / 部门 / 人员关键字）。 */
function treeFilterOn() {
    return (treeFbIds || []).length > 0 || (treeDeptIds || []).length > 0 || treeKey().length > 0;
}

/** 企微部门名称：快照里取不到时返回空（调用方退化为“企微部门 + ID”）。 */
function treeWxDeptName(wxDeptId) {
    var id = String(wxDeptId == null ? "" : wxDeptId);
    for (var i = 0; i < (wxDepts || []).length; i++) {
        if (String(wxDepts[i].id) === id) { return wxDepts[i].name || ""; }
    }
    return "";
}

/** 企微成员快照：按企微成员ID取成员行（取不到返回 null）。 */
function treeWxMember(wxId) {
    var id = String(wxId == null ? "" : wxId);
    for (var i = 0; i < (wxUsers || []).length; i++) {
        if (String(wxUsers[i].wx_user_id) === id) { return wxUsers[i]; }
    }
    return null;
}

/**
 * 右列“企微成员”：姓名（企微成员ID）/ 所在企微部门；快照取不到时退化为企微成员ID。
 * 姓名与 ID 放在同一个 .map-wx-acct 容器里（列是 flex 布局，避免姓名与括号 ID 被 gap 拆开）。
 */
function treeWxMemberHtml(wxId) {
    var id = String(wxId == null ? "" : wxId);
    if (!id) { return "<span class='map-empty-cell'>—</span>"; }
    var u = treeWxMember(id);
    var name = (u && u.wx_name) ? u.wx_name : (P_SHORT + "成员 " + id);
    var deptName = (u && u.wx_dept_name) ? u.wx_dept_name : "";
    return "<span class='map-wx-acct'>"
        + "<span class='map-user-name'>" + esc(name) + "</span>"
        + "<span class='map-wx-sub'>（" + esc(id) + "）</span>"
        + "</span>"
        + (deptName ? "<span class='map-wx-sub'>" + esc(deptName) + "</span>" : "");
}

/**
 * 该 OA 人员是否计入“未绑定”数字：已绑定企微账号、已标记“免绑定”的人员都不计入
 * （免绑定是明确不需要绑定的状态，在右列单独标注为“免绑定”）。
 */
function treeIsUnbound(oaId) {
    var id = String(oaId == null ? "" : oaId);
    if (!id) { return false; }
    if (boundByOa[id]) { return false; }
    if (isOaNoBind(id)) { return false; }
    return true;
}

/** 树节点：kind = "sub"（分部）/ "dept"（部门）；virtual 表示兜底分组（未分配分部 / 未分配部门）。 */
function treeNewNode(id, name, kind, virtual, subId) {
    return {
        id: id, name: name, kind: kind, virtual: !!virtual,
        rawId: "", parentId: "", subId: subId == null ? "" : String(subId),
        users: [], childNodes: [], shown: [],
        binding: null, text: "",
        subtreeUsers: 0, subtreeUnbound: 0, visibleChildCount: 0,
        fbGate: true, deptGate: true, selfHit: true, visible: false
    };
}

/** 节点统一按名称排序（中文按拼音，保证每次渲染顺序稳定）。 */
function treeNameCompare(a, b) {
    return String(a == null ? "" : a).localeCompare(String(b == null ? "" : b), "zh-Hans-CN");
}

function treeByNodeName(a, b) { return treeNameCompare(a.name, b.name); }

/** 部门的搜索文本：部门名 + 已绑定企微部门名称/ID。 */
function treeDeptSearchText(node) {
    var text = node.name;
    if (node.binding) {
        var wxDeptId = String(node.binding.wx_dept_id == null ? "" : node.binding.wx_dept_id);
        text += " " + treeWxDeptName(wxDeptId) + " " + wxDeptId;
    }
    return text.toLowerCase();
}

/** 给一批 OA 人员算好搜索文本（含其绑定的企微账号）并按姓名排序。 */
function treePrepareUsers(list) {
    (list || []).forEach(function (u) {
        var line = [u.lastname, u.loginid, u.workcode].join(" ");
        var bind = boundByOa[String(u.id)];
        if (bind) {
            var wxId = String(bind.wx_user_id == null ? "" : bind.wx_user_id);
            var member = treeWxMember(wxId);
            line += " " + wxId + " " + ((member && member.wx_name) || "");
        }
        u.__treeText = line.toLowerCase();
    });
    (list || []).sort(function (a, b) {
        return treeNameCompare(a.lastname || "", b.lastname || "");
    });
}

function treeToSet(ids) {
    var set = {};
    (ids || []).forEach(function (id) { set[String(id)] = true; });
    return set;
}

function treeSetSize(set) {
    return Object.keys(set || {}).length;
}

function treeFindOption(list, id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < (list || []).length; i++) {
        if (String(list[i].id) === key) { return list[i]; }
    }
    return null;
}

/**
 * 构建树模型：分部 → 部门（含人员）三级结构；“分部 / 部门 / 人员关键字”三个筛选条件
 * 在这一步套用（未被筛中的部门不出现，命中节点的上级路径保留）。
 */
function treeBuildModel() {
    var keyword = treeKey();
    var keywordOn = keyword.length > 0;
    treeCtx = { keyword: keyword, keywordOn: keywordOn };
    var fbSet = treeToSet(expandFbIds(treeFbIds));
    var deptSet = treeToSet(expandDeptIds(treeDeptIds));
    var fbFilterOn = treeSetSize(fbSet) > 0;
    var deptFilterOn = treeSetSize(deptSet) > 0;

    // 1) 分部节点（顶层；企微没有分部层级）
    var subs = {};
    (oaSubOptions || []).forEach(function (s) {
        var id = String(s.id == null ? "" : s.id);
        if (!id) { return; }
        var node = treeNewNode("s:" + id, s.name || s.fullname || ("分部 " + id), "sub", false, id);
        node.fbGate = !fbFilterOn || !!fbSet[id];
        subs[id] = node;
    });
    var noSub = treeNewNode("s:__none__", "未分配分部", "sub", true, "");
    noSub.fbGate = !fbFilterOn;

    // 2) 部门节点
    var depts = {};
    (oaDeptOptions || []).forEach(function (d) {
        var id = String(d.id == null ? "" : d.id);
        if (!id) { return; }
        var node = treeNewNode("d:" + id, d.name || d.fullname || ("OA 部门 " + id), "dept", false,
            d.subcompanyid1);
        node.rawId = id;
        node.parentId = String(d.parent == null ? "" : d.parent);
        node.binding = deptBindOfOa(id);
        node.text = treeDeptSearchText(node);
        depts[id] = node;
    });
    // 人员所属部门不在部门缓存里（如已封存部门）时按人员数据补节点，避免漏人
    (oaUsers || []).forEach(function (u) {
        var did = String(u.departmentid == null ? "" : u.departmentid);
        if (!did || did === "0" || depts[did]) { return; }
        var node = treeNewNode("d:" + did, u.departmentname || ("OA 部门 " + did), "dept", false,
            u.subcompanyid1);
        node.rawId = did;
        node.binding = deptBindOfOa(did);
        node.text = treeDeptSearchText(node);
        depts[did] = node;
    });
    // 部门父子关系：父部门不存在/为 0 时视为该分部的顶层部门
    var topBySub = {};
    Object.keys(depts).forEach(function (id) {
        var node = depts[id];
        var pid = node.parentId;
        if (pid && pid !== "0" && pid !== id && depts[pid]) {
            depts[pid].childNodes.push(node);
        } else {
            var subKey = subs[node.subId] ? node.subId : "__none__";
            (topBySub[subKey] = topBySub[subKey] || []).push(node);
        }
    });

    // 3) 人员挂到所属部门；没有部门的人员归入“未分配部门”
    var noDept = treeNewNode("d:__none__", "未分配部门", "dept", true, "");
    noDept.text = noDept.name.toLowerCase();
    noDept.deptGate = !deptFilterOn;
    (oaUsers || []).forEach(function (u) {
        var did = String(u.departmentid == null ? "" : u.departmentid);
        var node = (did && did !== "0" && depts[did]) ? depts[did] : noDept;
        node.users.push(u);
    });

    // 4) 每个部门的筛选闸门（分部 + 部门）、关键字命中标记与排序
    Object.keys(depts).forEach(function (id) {
        var node = depts[id];
        node.fbGate = !fbFilterOn || !!fbSet[node.subId];
        node.deptGate = !deptFilterOn || !!deptSet[node.rawId];
        // 无关键字时视为“全部命中”；有关键字时按部门名（含已绑定企微部门名/ID）判断
        node.selfHit = !keywordOn || node.text.indexOf(keyword) >= 0;
        treePrepareUsers(node.users);
        node.childNodes.sort(treeByNodeName);
    });
    noDept.selfHit = !keywordOn || noDept.text.indexOf(keyword) >= 0;
    treePrepareUsers(noDept.users);

    // 5) 顶层部门挂到各自分部；无法归属分部的部门与“未分配部门”都归入“未分配分部”
    Object.keys(subs).forEach(function (id) {
        subs[id].childNodes = (topBySub[id] || []).slice().sort(treeByNodeName);
    });
    noSub.childNodes = (topBySub["__none__"] || []).slice().sort(treeByNodeName);
    if (noDept.users.length) { noSub.childNodes.push(noDept); }

    var roots = Object.keys(subs).map(function (id) { return subs[id]; }).sort(treeByNodeName);
    if (noSub.childNodes.length) { roots.push(noSub); }

    // 6) 自下而上统计（未绑定人数 = 本节点 + 全部下级部门求和）与可见性
    var totalUsers = 0;
    var totalUnbound = 0;
    var totalSubs = 0;
    var totalDepts = 0;
    var allNodes = [];
    roots.forEach(function (sub) {
        if (sub.childNodes.length) { allNodes.push(sub); }
        sub.childNodes.forEach(function (d) { treeCollectNodes(d, allNodes); });
        var r = treeWalkSub(sub);
        totalUsers += r.users;
        totalUnbound += r.unbound;
        if (sub.visible) { totalSubs++; }
        totalDepts += treeCountVisibleDepts(sub);
    });
    treeNodeMap = {};
    allNodes.forEach(function (n) { treeNodeMap[n.id] = n; });
    treeNodeList = allNodes;
    treeRootIds = roots;
    treeTotalSubs = totalSubs;
    treeTotalDepts = totalDepts;
    treeTotalUsers = totalUsers;
    treeTotalUnbound = totalUnbound;
}

function treeCollectNodes(node, out) {
    out.push(node);
    node.childNodes.forEach(function (c) { treeCollectNodes(c, out); });
}

function treeCountVisibleDepts(node) {
    var count = 0;
    node.childNodes.forEach(function (c) {
        if (c.visible && !c.virtual) { count++; }
        count += treeCountVisibleDepts(c);
    });
    return count;
}

/** 分部节点：子树统计 + 可见性（分部被筛掉时整棵子树都不显示）。 */
function treeWalkSub(node) {
    var users = 0;
    var unbound = 0;
    var childVisible = 0;
    node.childNodes.forEach(function (c) {
        var r = treeWalkDept(c);
        users += r.users;
        unbound += r.unbound;
        if (c.visible) { childVisible++; }
    });
    node.subtreeUsers = users;
    node.subtreeUnbound = unbound;
    node.visibleChildCount = childVisible;
    node.visible = node.fbGate && childVisible > 0;
    return { users: users, unbound: unbound };
}

/**
 * 部门节点：先按“分部/部门闸门 + 人员关键字”算出本部门要展示的直属人员，再汇总下级部门。
 * 部门自身未命中筛选时仍会作为下级命中节点的上级路径保留（只是不展示自己的直属人员）。
 */
function treeWalkDept(node) {
    var keyword = treeCtx.keyword;
    var keywordOn = treeCtx.keywordOn;
    var gate = node.fbGate && node.deptGate;
    var shown = [];
    if (gate) {
        node.users.forEach(function (u) {
            // 部门自身命中关键字时列出其全部直属人员，否则只列出命中的人员
            if (keywordOn && !node.selfHit && u.__treeText.indexOf(keyword) < 0) { return; }
            shown.push(u);
        });
    }
    node.shown = shown;
    var users = shown.length;
    var unbound = 0;
    shown.forEach(function (u) { if (treeIsUnbound(u.id)) { unbound++; } });
    var childVisible = 0;
    node.childNodes.forEach(function (c) {
        var r = treeWalkDept(c);
        users += r.users;
        unbound += r.unbound;
        if (c.visible) { childVisible++; }
    });
    node.subtreeUsers = users;
    node.subtreeUnbound = unbound;
    node.visibleChildCount = childVisible;
    // 可见性：有要展示的人员/下级部门就展示；此外
    //   - 没有关键字筛选时，已绑定企微部门但暂时没人的部门也保留（便于核对部门映射）；
    //   - 有关键字筛选时，只有部门名（含已绑定企微部门名/ID）命中才保留，避免搜索时留下大量空部门。
    node.visible = shown.length > 0 || childVisible > 0
        || (gate && (keywordOn ? node.selfHit : !!node.binding));
    return { users: users, unbound: unbound };
}

/** 逐层展开成行列表：分部行 → 部门行 → 本部门人员行 → 下级部门行。 */
function treeCollectRows() {
    var rows = [];
    treeRootIds.forEach(function (sub) {
        if (sub.visible) { treePushSub(sub, rows); }
    });
    return rows;
}

function treePushSub(node, rows) {
    rows.push({ kind: "sub", node: node, depth: 0 });
    if (treeCollapsed[node.id]) { return; }
    node.childNodes.forEach(function (c) {
        if (c.visible) { treePushDept(c, 1, rows); }
    });
}

function treePushDept(node, depth, rows) {
    rows.push({ kind: "dept", node: node, depth: depth });
    if (treeCollapsed[node.id]) { return; }
    (node.shown || []).forEach(function (u) {
        rows.push({ kind: "user", node: node, user: u, depth: depth + 1 });
    });
    node.childNodes.forEach(function (c) {
        if (c.visible) { treePushDept(c, depth + 1, rows); }
    });
}

/** 部门行右列：该 OA 部门已绑定的企微部门；未绑定显示“未绑定企微部门”。 */
function treeDeptWxHtml(node) {
    if (node.virtual || !node.binding) { return "<span class='map-empty-cell'>未绑定" + P_SHORT + "部门</span>"; }
    var wxDeptId = String(node.binding.wx_dept_id == null ? "" : node.binding.wx_dept_id);
    var wxName = treeWxDeptName(wxDeptId);
    return "<span class='tag bound'>部门已绑定</span>"
        + "<span class='map-wx-acct'>"
        + "<span class='map-user-name'>" + esc(wxName || (P_SHORT + "部门 " + wxDeptId)) + "</span>"
        + "<span class='map-wx-sub'>（" + esc(wxDeptId) + "）</span>"
        + "</span>";
}

/** 人员行右列：该 OA 人员已绑定的企微账号；免绑定/未绑定分别标注。 */
function treeUserWxHtml(u) {
    var bind = boundByOa[String(u.id)];
    if (bind) { return treeWxMemberHtml(bind.wx_user_id); }
    if (isOaNoBind(u.id)) { return "<span class='tag nobind'>免绑定</span>"; }
    return "<span class='map-empty-cell'>未绑定</span>";
}

/** 带收起箭头的“分组行”（分部/部门）左侧单元格：箭头 + 名称 + 未绑定人数徽标。 */
function treeGroupCellHtml(row, nameHtml) {
    var node = row.node;
    var hasKids = node.users.length > 0 || node.visibleChildCount > 0;
    var open = !treeCollapsed[node.id];
    var caret = hasKids
        ? "<button type='button' class='tree-caret' data-node='" + esc(node.id) + "' title='"
            + (open ? "收起下级" : "展开下级") + "'>" + (open ? "▾" : "▸") + "</button>"
        : "<span class='tree-caret leaf'>•</span>";
    var badgeTip = "未绑定" + P_SHORT + "账号的 OA 人员 " + node.subtreeUnbound + " 人（含下级部门）；"
        + "本节点 " + node.users.length + " 人，含下级部门共 " + node.subtreeUsers + " 人";
    var badge = "<span class='map-badge" + (node.subtreeUnbound ? "" : " zero") + "'"
        + " title='" + esc(badgeTip) + "'>" + node.subtreeUnbound + "</span>";
    return "<div class='map-col map-col-oa' style='padding-left:" + (8 + row.depth * 20) + "px;'>"
        + caret
        + "<span class='map-dept-name'>" + nameHtml + "</span>"
        + badge
        + "</div>";
}

function treeSubRowHtml(row) {
    var node = row.node;
    var nameHtml = node.virtual ? esc(node.name) : OaonesHrm.sub(node.subId, node.name);
    return "<div class='map-row map-row-sub' data-node='" + esc(node.id) + "'>"
        + treeGroupCellHtml(row, nameHtml)
        + "<div class='map-col map-col-wx'><span class='map-empty-cell'>—</span></div>"
        + "</div>";
}

function treeDeptRowHtml(row) {
    var node = row.node;
    var nameHtml = node.virtual ? esc(node.name) : OaonesHrm.dept(node.rawId, node.name);
    return "<div class='map-row map-row-dept' data-node='" + esc(node.id) + "'>"
        + treeGroupCellHtml(row, nameHtml)
        + "<div class='map-col map-col-wx'>" + treeDeptWxHtml(node) + "</div>"
        + "</div>";
}

function treeUserRowHtml(row) {
    var u = row.user;
    var pad = 8 + row.depth * 20 + 20;
    return "<div class='map-row map-row-user'>"
        + "<div class='map-col map-col-oa' style='padding-left:" + pad + "px;'>"
        + "<span class='tree-caret leaf'>•</span>"
        + "<span class='map-user-name'>" + OaonesHrm.user(u.id, u.lastname) + "</span>"
        + (u.loginid ? "<span class='map-wx-sub'>" + esc(u.loginid) + "</span>" : "")
        + "</div>"
        + "<div class='map-col map-col-wx'>" + treeUserWxHtml(u) + "</div>"
        + "</div>";
}

function treeRowHtml(row) {
    if (row.kind === "sub") { return treeSubRowHtml(row); }
    if (row.kind === "dept") { return treeDeptRowHtml(row); }
    return treeUserRowHtml(row);
}

/** 筛选栏下方提示：当前展示规模 + 当前筛选条件。 */
function treeSummaryText() {
    var text = "当前展示 " + treeTotalSubs + " 个分部、" + treeTotalDepts + " 个部门、"
        + treeTotalUsers + " 名 OA 人员；其中未绑定" + P_SHORT + "账号 " + treeTotalUnbound + " 人（数字按节点含下级汇总）";
    var filters = [];
    if ((treeFbIds || []).length) { filters.push("分部 " + treeFbIds.length + " 个"); }
    if ((treeDeptIds || []).length) { filters.push("部门 " + treeDeptIds.length + " 个"); }
    if (treeKey()) { filters.push("人员关键字“" + treeKeyword + "”"); }
    if (filters.length) { text += "；当前筛选：" + filters.join("、"); }
    return text;
}

/** 渲染整棵树：数据来自页壳已加载的 OA 组织、人员、绑定与企微快照。 */
function renderTree() {
    var wrap = byId("mapTree");
    var emptyEl = byId("treeEmpty");
    var tipEl = byId("treeTip");
    if (!wrap || !emptyEl) { return; }
    // 切换租户：重置收起状态与全部筛选条件
    if (treeStateCfg !== String(state.cfgId == null ? "" : state.cfgId)) {
        treeStateCfg = String(state.cfgId == null ? "" : state.cfgId);
        treeCollapsed = {};
        treeKeyword = "";
        treeFbIds = [];
        treeDeptIds = [];
        var kw = byId("treeKeyword");
        if (kw) { kw.value = ""; }
        treeRenderFilterInputs();
    }
    if (!state.cfgId) {
        wrap.innerHTML = "";
        emptyEl.style.display = "block";
        emptyEl.textContent = "请先选择" + P_FULL + "租户";
        if (tipEl) { tipEl.textContent = ""; }
        return;
    }
    treeBuildModel();
    var rows = treeCollectRows();
    if (!rows.length) {
        wrap.innerHTML = "";
        emptyEl.style.display = "block";
        emptyEl.textContent = treeFilterOn()
            ? "没有符合筛选条件的分部/部门/人员，请调整分部、部门或人员关键字"
            : "当前租户下没有可展示的 OA 分部/部门/人员，请先维护 OA 组织架构后刷新";
        if (tipEl) { tipEl.textContent = treeSummaryText(); }
        return;
    }
    var html = "";
    rows.forEach(function (row) { html += treeRowHtml(row); });
    wrap.innerHTML = html;
    emptyEl.style.display = "none";
    if (tipEl) { tipEl.textContent = treeSummaryText(); }
}

function treeToggleNode(id) {
    var key = String(id == null ? "" : id);
    if (treeCollapsed[key]) { delete treeCollapsed[key]; } else { treeCollapsed[key] = true; }
    renderTree();
}

function treeExpandAll() {
    treeCollapsed = {};
    renderTree();
}

/**
 * 收起全部部门：只保留分部行、部门行与右列映射（人员行全部折叠）。
 * 分部行不收起，保证仍能看到“分部 → 部门”的层级与映射总览；需要时仍可单独收起某个分部。
 */
function treeCollapseAll() {
    treeCollapsed = {};
    (treeNodeList || []).forEach(function (node) {
        if (node.kind === "dept" && node.visible && (node.users.length || node.visibleChildCount)) {
            treeCollapsed[node.id] = true;
        }
    });
    renderTree();
}

function treeResetFilter() {
    treeKeyword = "";
    treeFbIds = [];
    treeDeptIds = [];
    treeCollapsed = {};
    byId("treeKeyword").value = "";
    treeRenderFilterInputs();
    renderTree();
}

function treeClearFbFilter() {
    treeFbIds = [];
    treeCollapsed = {};
    treeRenderFilterInputs();
    renderTree();
}

function treeClearDeptFilter() {
    treeDeptIds = [];
    treeCollapsed = {};
    treeRenderFilterInputs();
    renderTree();
}

/** 筛选输入框显示：未选为空占位，单选显示名称+ID，多选显示数量（与 A 页口径一致）。 */
function treeRenderFilterInputs() {
    var fbIds = treeFbIds || [];
    var deptIds = treeDeptIds || [];
    var fbInput = byId("treeFbInput");
    var deptInput = byId("treeDeptInput");
    if (fbInput) {
        fbInput.value = fbIds.length === 1
            ? oaSubLabel(fbIds[0]) + "（ID " + fbIds[0] + "）"
            : (fbIds.length > 1 ? "已选 " + fbIds.length + " 个分部" : "");
    }
    if (deptInput) {
        deptInput.value = deptIds.length === 1
            ? oaDeptLabel(deptIds[0]) + "（ID " + deptIds[0] + "）"
            : (deptIds.length > 1 ? "已选 " + deptIds.length + " 个部门" : "");
    }
    byId("treeFbPicker").classList.toggle("has-value", fbIds.length > 0);
    byId("treeDeptPicker").classList.toggle("has-value", deptIds.length > 0);
}

/* ------------------------------------------------------------------
 * 分部 / 部门 多选弹框：交互与“OA数据基准”页的分部/部门筛选一致
 * （列表式/树状、双击确定、全选、先选分部再选部门的前置条件过滤）。
 * ------------------------------------------------------------------ */

function treePickRows(keyword) {
    var rows = treePickType === "fb" ? (oaSubOptions || []).slice() : (oaDeptOptions || []).slice();
    var k = String(keyword == null ? (byId("treePickSearch").value || "") : keyword).trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (d) {
        var text = [d.id, d.code, d.name, d.fullname].map(function (v) {
            return String(v == null ? "" : v);
        }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
}

/** 前置条件：选择部门时只显示已选分部（含下级）下的部门；未选分部时显示全部。 */
function treePickPrecondition() {
    if (treePickType !== "dept") {
        return { tip: "支持多选，选中分部时其下级分部自动一并选中" };
    }
    var fbIds = (treeFbIds || []).map(String);
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

function treePickAllIds() {
    var rows = OaonesPicker.applyPrecondition("treePick", treePickRows(null));
    return rows.map(function (d) { return String(d.id); });
}

function treeOpenPick(type) {
    treePickType = type;
    treePickSelected = (type === "fb" ? treeFbIds : treeDeptIds).slice();
    byId("treePickTitle").textContent = type === "fb" ? "选择分部" : "选择部门";
    byId("treePickSearch").value = "";
    // 前置条件说明随当前选择实时生成
    OaonesPicker.preconditionTip("treePick", "treePickNote");
    // 树状默认收起分支：每次打开弹框重置展开状态
    OaonesPicker.resetTree("treePick");
    treeRenderPickList();
    byId("treePickMask").style.display = "block";
    byId("treePickModal").style.display = "block";
    byId("treePickSearch").focus();
}

function treeHidePick() {
    byId("treePickMask").style.display = "none";
    byId("treePickModal").style.display = "none";
    treePickType = "";
    treePickSelected = [];
}

function treePickOptionHtml(d, isSub, partialMap) {
    var id = String(d.id);
    var active = treePickSelected.indexOf(id) >= 0;
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

function treeRenderPickList() {
    var isSub = treePickType === "fb";
    var title = isSub ? "选择分部" : "选择部门";
    byId("treePickTitle").textContent = treePickSelected.length
        ? title + "（已选 " + treePickSelected.length + " 个）"
        : title;
    var mode = OaonesPicker.mode("treePick");
    var empty = isSub ? "没有匹配的分部" : "没有匹配的部门";
    // 前置条件过滤（先选分部再选部门）由 OaonesPicker 统一套用，两种显示模式一致
    var rows = OaonesPicker.applyPrecondition("treePick", treePickRows(mode === "tree" ? "" : null));
    // 先按当前搜索 + 前置条件算好“全选”状态，再渲染列表
    var allOn = treeSyncPickAllState();
    var partialMap = OaonesPicker.partialSelected(treePickSelected, isSub ? oaSubChildMap : oaDeptChildMap);
    var listEl = byId("treePickList");
    var html = "";
    if (mode === "tree") {
        html = OaonesPicker.renderTree("treePick", rows, {
            parentOf: function (d) { return d.parent; },
            textOf: function (d) { return [d.id, d.code, d.name, d.fullname].join(" "); },
            keyword: byId("treePickSearch").value,
            emptyText: empty,
            optionHtml: function (d) { return treePickOptionHtml(d, isSub, partialMap); }
        });
    } else if (!rows.length) {
        html = "<div class='wx-pick-empty'>" + empty + "</div>";
    } else {
        html = "<div class='wx-pick-head pick-cols3'><span>ID</span><span>编码</span><span>简称</span></div>";
        rows.forEach(function (d) { html += treePickOptionHtml(d, isSub, partialMap); });
    }
    listEl.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
    listEl.innerHTML = html;
    OaonesPicker.syncSelectAll("treePick", allOn);
}

/** 单击选项：只改选中态（双击或“确定”才落定并过滤），不重建列表以免打断双击。 */
function treeTogglePick(btn) {
    var id = btn.getAttribute("data-id");
    var isSub = treePickType === "fb";
    var idx = treePickSelected.indexOf(id);
    if (idx >= 0) {
        // 取消父节点时其下级一并取消（与 A 页口径一致）
        var removes = isSub ? oaFbDescendantIds(id) : oaDeptDescendantIds(id);
        treePickSelected = treePickSelected.filter(function (v) { return removes.indexOf(v) < 0; });
    } else {
        var next = treePickSelected.concat([id]);
        treePickSelected = isSub ? expandFbIds(next) : expandDeptIds(next);
    }
    treeRefreshPickDom();
}

function treeApplyPick() {
    if (treePickType === "fb") {
        treeFbIds = expandFbIds(treePickSelected);
        // 保留仍属于所选分部（含下级）范围的部门筛选，避免出现空交集
        var keep = treeToSet(treeFbIds);
        treeDeptIds = (treeDeptIds || []).filter(function (id) {
            var dept = treeFindOption(oaDeptOptions, id);
            return dept && keep[String(dept.subcompanyid1 == null ? "" : dept.subcompanyid1)];
        });
    } else {
        treeDeptIds = expandDeptIds(treePickSelected);
    }
    treeHidePick();
    // 筛选条件变化后展开全部，保证命中的节点不被折叠在分支里
    treeCollapsed = {};
    treeRenderFilterInputs();
    renderTree();
}

function treeClearPickSelection() {
    treePickSelected = [];
    treeRenderPickList();
}

function treeRefreshPickDom() {
    var isSub = treePickType === "fb";
    var partialMap = OaonesPicker.partialSelected(treePickSelected, isSub ? oaSubChildMap : oaDeptChildMap);
    var opts = byId("treePickList").querySelectorAll(".wx-pick-option");
    for (var i = 0; i < opts.length; i++) {
        var id = String(opts[i].getAttribute("data-id"));
        var active = treePickSelected.indexOf(id) >= 0;
        opts[i].classList.toggle("active", active);
        opts[i].classList.toggle("partial", !active && !!partialMap[id]);
    }
    var title = isSub ? "选择分部" : "选择部门";
    byId("treePickTitle").textContent = treePickSelected.length
        ? title + "（已选 " + treePickSelected.length + " 个）"
        : title;
    // 单行勾选/取消后同步“全选”按钮状态
    treeSyncPickAllState();
}

function treeSyncPickAllState() {
    var ids = treePickAllIds();
    var allOn = ids.length > 0 && ids.every(function (id) { return treePickSelected.indexOf(id) >= 0; });
    OaonesPicker.syncSelectAll("treePick", allOn);
    return allOn;
}

OaonesPicker.register("treePick", function () { treeRenderPickList(); });
OaonesPicker.setPrecondition("treePick", treePickPrecondition);
OaonesPicker.setSelectAll("treePick", function () {
    var isSub = treePickType === "fb";
    var ids = treePickAllIds();
    var on = treeSyncPickAllState();
    var next;
    if (on) {
        var drop = {};
        ids.forEach(function (id) {
            (isSub ? oaFbDescendantIds(id) : oaDeptDescendantIds(id)).forEach(function (v) { drop[v] = true; });
        });
        next = treePickSelected.filter(function (v) { return !drop[v]; });
    } else {
        next = isSub ? expandFbIds(treePickSelected.concat(ids)) : expandDeptIds(treePickSelected.concat(ids));
    }
    var seen = {};
    treePickSelected = next.filter(function (v) {
        v = String(v);
        if (seen[v]) { return false; }
        seen[v] = true;
        return true;
    });
    treeRenderPickList();
    return treeSyncPickAllState();
});

/* ------------------------------------------------------------------
 * 页面事件
 * ------------------------------------------------------------------ */

/* 树行交互：点箭头或节点行左侧区域收起/展开（链接与右侧企微列不触发）。 */
byId("mapTree").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.closest) { return; }
    var caret = t.closest(".tree-caret");
    // 占位箭头（.tree-caret.leaf，本人行/叶子行）不是展开箭头，落到下面的“点行展开”逻辑
    if (caret && !caret.classList.contains("leaf")) {
        treeToggleNode(caret.getAttribute("data-node"));
        return;
    }
    // 分部名/部门名/人员姓名是泛微标准页面链接，点链接只打开标准页面，不触发展开收起
    if (t.closest("a")) { return; }
    var row = t.closest(".map-row-sub") || t.closest(".map-row-dept");
    if (row && t.closest(".map-col-oa")) { treeToggleNode(row.getAttribute("data-node")); }
});

byId("treeFbPicker").addEventListener("click", function () { treeOpenPick("fb"); });
byId("treeDeptPicker").addEventListener("click", function () { treeOpenPick("dept"); });
byId("treeFbClear").addEventListener("click", function (e) {
    e.stopPropagation();
    treeClearFbFilter();
});
byId("treeDeptClear").addEventListener("click", function (e) {
    e.stopPropagation();
    treeClearDeptFilter();
});
byId("treePickCloseBtn").addEventListener("click", treeHidePick);
byId("treePickMask").addEventListener("click", function (e) {
    if (e.target === this) { treeHidePick(); }
});
byId("treePickSearch").addEventListener("input", function () {
    if (treePickType) { treeRenderPickList(); }
});
byId("treePickClearBtn").addEventListener("click", treeClearPickSelection);
byId("treePickCancelBtn").addEventListener("click", treeHidePick);
byId("treePickOkBtn").addEventListener("click", treeApplyPick);
// 双击选项：立即确定并退出弹框
byId("treePickList").addEventListener("dblclick", function (e) {
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group") || !treePickType) { return; }
    if (treePickSelected.indexOf(option.getAttribute("data-id")) < 0) { treeTogglePick(option); }
    treeApplyPick();
});
// 单击弹框内的选项：只改选中态（页壳的全局点击处理只服务 A/B 页的弹框，这里单独接管）
document.addEventListener("click", function (e) {
    if (!treePickType) { return; }
    var option = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
    if (!option || option.hasAttribute("data-group")) { return; }
    var list = byId("treePickList");
    if (!list || !list.contains(option)) { return; }
    treeTogglePick(option);
});

byId("treeKeyword").addEventListener("input", function () {
    treeKeyword = this.value;
    // 筛选变化后展开全部，保证命中的节点不被折叠在分支里
    treeCollapsed = {};
    renderTree();
});
byId("treeExpandBtn").addEventListener("click", treeExpandAll);
byId("treeCollapseBtn").addEventListener("click", treeCollapseAll);
byId("treeResetBtn").addEventListener("click", treeResetFilter);

/* ------------------------------------------------------------------
 * 导出 Excel：把当前筛选下的“分部 → 部门 → 人员”映射导出为明细表。
 * 与页面口径一致：只导出当前筛选（分部/部门/人员关键字）命中的节点与其上级路径，
 * 收起/展开状态不影响导出内容；未绑定人数按“本节点 + 全部下级部门”汇总。
 * ------------------------------------------------------------------ */

/** 树状映射的表格列定义（表头 + 列宽） */
var TREE_EXPORT_COLUMNS = [
    { title: "层级", width: 8 },
    { title: "名称", width: 20 },
    { title: "OA ID", width: 14 },
    { title: "登录名/工号", width: 20 },
    { title: "层级路径", width: 40 },
    { title: P_SHORT + "部门/成员", width: 22 },
    { title: P_SHORT + " ID", width: 16 },
    { title: "状态", width: 16 },
    { title: "未绑定人数（含下级）", width: 18 }
];

/** 某 OA 人员已绑定的企微成员名称（快照取不到时退化为 ID） */
function treeExportWxName(wxId) {
    var u = treeWxMember(wxId);
    return u ? (u.wx_name || String(wxId)) : String(wxId);
}

/** 导出用：把部门节点及其直属人员、下级部门写入明细（path 为上级路径） */
function treeExportDept(node, path, data) {
    var deptPath = path + " / " + node.name;
    var wxDeptId = node.binding ? String(node.binding.wx_dept_id == null ? "" : node.binding.wx_dept_id) : "";
    data.push([
        "部门",
        node.name,
        node.rawId || "",
        "",
        deptPath,
        node.binding ? (treeWxDeptName(wxDeptId) || (P_SHORT + "部门 " + wxDeptId)) : "",
        wxDeptId,
        node.binding ? "部门已绑定" : "未绑定" + P_SHORT + "部门",
        node.subtreeUnbound
    ]);
    (node.shown || []).forEach(function (u) {
        var oaId = String(u.id);
        var bind = boundByOa[oaId];
        var wxId = bind ? String(bind.wx_user_id == null ? "" : bind.wx_user_id) : "";
        var noBind = !bind && isOaNoBind(oaId);
        data.push([
            "人员",
            u.lastname || "",
            oaId,
            [u.loginid, u.workcode].filter(function (v) { return !!v; }).join(" / "),
            deptPath + " / " + (u.lastname || ""),
            wxId ? treeExportWxName(wxId) : (noBind ? "免绑定" : ""),
            wxId,
            bind ? "已绑定" : (noBind ? "免绑定" : "未绑定"),
            ""
        ]);
    });
    node.childNodes.forEach(function (c) {
        if (c.visible) { treeExportDept(c, deptPath, data); }
    });
}

/** “树状映射”导出 Excel：导出当前筛选下的分部 → 部门 → 人员映射明细 */
function exportTreeExcel() {
    if (!state.cfgId) { showToast("请先选择" + P_FULL + "租户", 1); return; }
    // 按当前筛选重建模型（可见性、筛选命中的人员、未绑定人数都在模型里）
    treeBuildModel();
    var data = [];
    treeRootIds.forEach(function (sub) {
        if (!sub.visible) { return; }
        data.push([
            "分部",
            sub.name,
            sub.subId || "",
            "",
            sub.name,
            "",
            "",
            "—",
            sub.subtreeUnbound
        ]);
        sub.childNodes.forEach(function (dept) {
            if (dept.visible) { treeExportDept(dept, sub.name, data); }
        });
    });
    if (!data.length) {
        showToast("当前筛选条件下没有可导出的分部/部门/人员，请调整筛选条件后重试", 1);
        return;
    }
    var ok = OaonesExcel.export({
        filename: "树状映射_" + OaonesExcel.stamp() + ".xlsx",
        sheet: "树状映射",
        columns: TREE_EXPORT_COLUMNS,
        rows: data
    });
    if (ok) { showToast("已导出 " + data.length + " 行映射数据"); }
}
</script>
