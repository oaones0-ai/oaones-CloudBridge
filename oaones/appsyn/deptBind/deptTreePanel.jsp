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

<%-- 二级菜单：树状映射（部门绑定 - OA 部门 ⇄ 平台部门 对照树） --%>
<%-- 页面说明正文：由页壳 sub-tabs 右侧的感叹号（#deptTreeHelpTip）悬浮/点击弹出 --%>
<div class="help-source" id="deptTreeHelpText">本页把 OA 组织架构与<%= oaonesPBShort %>部门按“分部 → 部门”逐级对照展示：左列是 OA 分部、OA 部门（部门按 OA 上下级缩进），右列是该 OA 部门已绑定的<%= oaonesPBShort %>部门（分部在<%= oaonesPBShort %>没有对应层级，右列显示“—”）。层级为<span class="rule">分部 → 部门（按 OA 上下级）</span>，<span class="rule">默认展开</span>，点击节点行左侧箭头或节点行左侧区域可收起/展开（分部名、部门名是泛微标准页面链接，点链接不会收起）。分部行、部门行名称右侧的数字是该节点<span class="rule">未绑定<%= oaonesPBShort %>部门</span>的部门数，按“本节点 + 全部下级部门”求和（已绑定的部门不计入）。上方筛选条件可组合使用：<span class="rule">分部</span>（可多选，选中分部自动含其下级分部）、<span class="rule">部门</span>（可多选，候选项按已选分部过滤，选中部门自动含其下级部门）、<span class="rule">部门关键字</span>（OA 部门名称/编号、已绑定<%= oaonesPBShort %>部门名称或ID，输入即时过滤）；筛选后自动保留命中节点的上级路径，点“重置筛选”清空全部条件。OA 部门到<%= oaonesPBShort %>部门为<span class="rule">多对一</span>：多个 OA 部门可以绑定同一个<%= oaonesPBShort %>部门，因此同一<%= oaonesPBShort %>部门可能出现在多行（右列标注“已被 N 个 OA 部门绑定”）；一个 OA 部门只保留一条<%= oaonesPBShort %>部门绑定（在“OA数据基准”页维护，选择即落库）。右列<%= oaonesPBShort %>部门名称取自<%= oaonesPBShort %>部门快照，若显示为“<%= oaonesPBShort %>部门 + 数字ID”，请先在页壳工具栏点击“更新<%= oaonesPBShort %>部门快照”。</div>

<div id="deptTreePanel" style="display:none;">
    <div class="filter-bar">
        <span class="filter-label">映射筛选：</span>
        <div class="filter-picker" id="deptTreeFbPicker" title="点击选择分部（可多选，选中分部自动含其下级分部）">
            <input type="text" id="deptTreeFbInput" class="filter-input" readonly placeholder="选择分部（可多选）">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="deptTreeFbClear" title="清除分部筛选">×</button>
        </div>
        <div class="filter-picker" id="deptTreeDeptPicker" title="点击选择部门（可多选，含下级部门；候选受已选分部过滤）">
            <input type="text" id="deptTreeDeptInput" class="filter-input" readonly placeholder="选择部门（可多选）">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="deptTreeDeptClear" title="清除部门筛选">×</button>
        </div>
        <input type="text" id="deptTreeKeyword" placeholder="部门：名称/编号/<%= oaonesPBShort %>部门名称或ID" autocomplete="off"
               style="min-width:220px;max-width:320px;" title="按 OA 部门名称、部门编号或其绑定的<%= oaonesPBShort %>部门名称/ID即时过滤">
        <button type="button" class="btn small secondary" id="deptTreeExpandBtn"
                title="展开全部分部与部门（页面默认即为展开）">展开全部</button>
        <button type="button" class="btn small secondary" id="deptTreeCollapseBtn"
                title="收起全部部门，只保留分部行与部门行（仍可单独展开）">收起全部</button>
        <button type="button" class="btn small secondary" id="deptTreeResetBtn"
                title="清空分部、部门、关键字三个筛选条件">重置筛选</button>
    </div>
    <div class="muted" id="deptTreeTip" style="margin-bottom:10px;"></div>
    <div class="map-head">
        <div class="map-col">OA 分部 / 部门</div>
        <div class="map-col"><%= oaonesPBShort %>部门</div>
    </div>
    <div class="map-tree" id="deptMapTree"></div>
    <div class="empty" id="deptTreeEmpty" style="display:none;">暂无可展示的分部/部门</div>
</div>

<script>
"use strict";
/**
 * 面板脚本早于页壳脚本（deptBind.jsp 底部）执行：页壳里的 byId 此时还没定义，
 * 面板里的即时事件绑定不能依赖它；这里放一份同实现的本地兜底
 * （页壳随后用同实现的函数覆盖，行为一致）。
 */
function byId(id) { return document.getElementById(id); }

/**
 * “树状映射”页（部门绑定）：以 OA 组织架构为骨架的“OA 部门 ⇄ 企微部门”对照树。
 *
 *   - 层级：分部 → 部门（按 OA 上下级缩进）；左列 OA 对象、右列该 OA 部门已绑定的企微部门
 *     （分部在企微没有对应层级，右列显示“—”）。
 *   - 默认展开；“分部行/部门行”名称右侧的数字 = 该节点未绑定企微部门的部门数，
 *     按“本节点 + 全部下级部门”求和。
 *   - 只显示 OA 部门侧的树：未与 OA 部门绑定的企微部门不在本页出现。
 *   - 上方筛选：分部（多选，含下级分部）、部门（多选，含下级部门，候选受已选分部过滤）、
 *     部门关键字（OA 部门名称/编号、已绑定企微部门名称或ID）。
 *
 * 数据来自页壳 loadData() 已加载的组织、绑定与快照（oaDepts / oaSubOptions / oaDeptOptions /
 * binds→boundByOa / dept.wxlist→wxDepts），复用页壳与“OA数据基准”页的公共实现：
 * oaSubLabel / oaDeptLabel（页壳）、oaSubChildMap / oaFbDescendantIds / expandFbIds（A 页）、
 * oaDeptSubId（页壳，部门所属分部），不重复实现一份。
 */
/** 已收起的分部/部门节点ID（默认全部展开） */
var deptTreeCollapsed = {};
/** 部门关键字筛选（部门名称/编号、已绑定企微部门名称或ID） */
var deptTreeKeyword = "";
/** 分部筛选（已含下级分部）、部门筛选（已含下级部门） */
var deptTreeFbIds = [];
var deptTreeDeptIds = [];
/** 上次渲染的租户：切换租户时重置收起状态与全部筛选 */
var deptTreeStateCfg = null;
/** 分部/部门选择弹框：当前选择对象（fb / dept）与待确定的选择 */
/** 本次渲染的节点列表、顶层节点与汇总 */
var deptTreeNodeList = [];
var deptTreeRootIds = [];
var deptTreeTotalSubs = 0;
var deptTreeTotalDepts = 0;
var deptTreeTotalUnbound = 0;

function deptTreeKey() {
    return deptTreeKeyword == null ? "" : String(deptTreeKeyword).trim().toLowerCase();
}

/** 是否存在筛选条件（分部 / 部门 / 关键字）。 */
function deptTreeFilterOn() {
    return (deptTreeFbIds || []).length > 0 || (deptTreeDeptIds || []).length > 0
        || deptTreeKey().length > 0;
}

/** 企微部门名称：快照里取不到时返回空（调用方退化为“企微部门 + ID”）。 */
function deptTreeWxDeptName(wxDeptId) {
    var id = String(wxDeptId == null ? "" : wxDeptId);
    for (var i = 0; i < (wxDepts || []).length; i++) {
        if (String(wxDepts[i].id) === id) { return wxDepts[i].name || ""; }
    }
    return "";
}

/** 部门编号：从部门候选缓存（oaDeptOptions）按 ID 取。 */
function deptTreeDeptCode(deptId) {
    var id = String(deptId == null ? "" : deptId);
    for (var i = 0; i < (oaDeptOptions || []).length; i++) {
        if (String(oaDeptOptions[i].id) === id) { return oaDeptOptions[i].code || ""; }
    }
    return "";
}

/** 部门父子关系（parent → [子部门ID]），用于“含下级”展开与部分选中展示。 */
function deptTreeChildMap() {
    var map = {};
    (oaDeptOptions || []).forEach(function (d) {
        var p = String(d.parent == null ? "" : d.parent);
        if (!map[p]) { map[p] = []; }
        map[p].push(String(d.id));
    });
    return map;
}

/** 某部门及其全部下级部门ID。 */
function deptTreeDescendantIds(id) {
    var map = deptTreeChildMap();
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

/** 批量连带下级展开。 */
function deptTreeExpandDeptIds(ids) {
    var map = deptTreeChildMap();
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

function deptTreeToSet(ids) {
    var set = {};
    (ids || []).forEach(function (id) { set[String(id)] = true; });
    return set;
}

function deptTreeSetSize(set) {
    return Object.keys(set || {}).length;
}

function deptTreeFindOption(list, id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < (list || []).length; i++) {
        if (String(list[i].id) === key) { return list[i]; }
    }
    return null;
}

/** 名称排序统一按中文拼音，保证每次渲染顺序稳定。 */
function deptTreeNameCompare(a, b) {
    return String(a == null ? "" : a).localeCompare(String(b == null ? "" : b), "zh-Hans-CN");
}

function deptTreeByNodeName(a, b) { return deptTreeNameCompare(a.name, b.name); }

/** 树节点：kind = "sub"（分部）/ "dept"（部门）；virtual 表示兜底分组（未分配分部）。 */
function deptTreeNewNode(id, name, kind, virtual, subId) {
    return {
        id: id, name: name, kind: kind, virtual: !!virtual,
        rawId: "", parentId: "", subId: subId == null ? "" : String(subId),
        childNodes: [], binding: null, text: "",
        subtreeDepts: 0, subtreeUnbound: 0, visibleChildCount: 0,
        fbGate: true, deptGate: true, selfHit: true, visible: false
    };
}

/** 部门的搜索文本：部门名 + 部门编号 + 已绑定企微部门名称/ID。 */
function deptTreeDeptSearchText(node) {
    var text = node.name + " " + (deptTreeDeptCode(node.rawId) || "");
    if (node.binding) {
        var wxDeptId = String(node.binding.wx_dept_id == null ? "" : node.binding.wx_dept_id);
        text += " " + deptTreeWxDeptName(wxDeptId) + " " + wxDeptId;
    }
    return text.toLowerCase();
}

/**
 * 构建树模型：分部 → 部门两级结构；“分部 / 部门 / 关键字”三个筛选条件在这一步套用
 * （未被筛中的部门不出现，命中节点的上级路径保留）。
 */
function deptTreeBuildModel() {
    var keyword = deptTreeKey();
    var keywordOn = keyword.length > 0;
    var fbSet = deptTreeToSet(expandFbIds(deptTreeFbIds));
    var deptSet = deptTreeToSet(deptTreeExpandDeptIds(deptTreeDeptIds));
    var fbFilterOn = deptTreeSetSize(fbSet) > 0;
    var deptFilterOn = deptTreeSetSize(deptSet) > 0;

    // 1) 分部节点（顶层；企微没有分部层级）
    var subs = {};
    (oaSubOptions || []).forEach(function (s) {
        var id = String(s.id == null ? "" : s.id);
        if (!id) { return; }
        var node = deptTreeNewNode("s:" + id, s.name || s.fullname || ("分部 " + id), "sub", false, id);
        node.fbGate = !fbFilterOn || !!fbSet[id];
        subs[id] = node;
    });
    var noSub = deptTreeNewNode("s:__none__", "未分配分部", "sub", true, "");
    noSub.fbGate = !fbFilterOn;

    // 2) 部门节点（OA 部门列表：id/name/parent/subcompanyid1）
    var depts = {};
    (oaDepts || []).forEach(function (d) {
        var id = String(d.id == null ? "" : d.id);
        if (!id) { return; }
        if (String(d.canceled == null ? "" : d.canceled) === "1") { return; }
        var bind = boundByOa[id] || null;
        var name = d.name || oaDeptLabel(id) || ("OA 部门 " + id);
        var node = deptTreeNewNode("d:" + id, name, "dept", false, oaDeptSubId(d, bind));
        node.rawId = id;
        node.parentId = String(d.parent == null ? "" : d.parent);
        node.binding = bind;
        node.text = deptTreeDeptSearchText(node);
        depts[id] = node;
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

    // 3) 每个部门的筛选闸门（分部 + 部门）与关键字命中标记
    Object.keys(depts).forEach(function (id) {
        var node = depts[id];
        node.fbGate = !fbFilterOn || !!fbSet[node.subId];
        node.deptGate = !deptFilterOn || !!deptSet[node.rawId];
        // 无关键字时视为“全部命中”；有关键字时按部门名/编号（含已绑定企微部门名/ID）判断
        node.selfHit = !keywordOn || node.text.indexOf(keyword) >= 0;
        node.childNodes.sort(deptTreeByNodeName);
    });

    // 4) 顶层部门挂到各自分部；无法归属分部的部门归入“未分配分部”
    Object.keys(subs).forEach(function (id) {
        subs[id].childNodes = (topBySub[id] || []).slice().sort(deptTreeByNodeName);
    });
    noSub.childNodes = (topBySub["__none__"] || []).slice().sort(deptTreeByNodeName);

    var roots = Object.keys(subs).map(function (id) { return subs[id]; }).sort(deptTreeByNodeName);
    if (noSub.childNodes.length) { roots.push(noSub); }

    // 5) 自下而上统计（未绑定部门数 = 本节点 + 全部下级部门求和）与可见性
    var totalDepts = 0;
    var totalUnbound = 0;
    var totalSubs = 0;
    var allNodes = [];
    roots.forEach(function (sub) {
        if (sub.childNodes.length) { allNodes.push(sub); }
        sub.childNodes.forEach(function (d) { deptTreeCollectNodes(d, allNodes); });
        // 先自下而上算好各子树统计与可见性，再只按“可见节点”汇总筛选栏提示
        deptTreeWalkSub(sub);
        if (sub.visible) {
            totalSubs++;
            totalDepts += deptTreeCountVisibleDepts(sub);
            totalUnbound += deptTreeCountVisibleUnbound(sub);
        }
    });
    deptTreeNodeList = allNodes;
    deptTreeRootIds = roots;
    deptTreeTotalSubs = totalSubs;
    deptTreeTotalDepts = totalDepts;
    deptTreeTotalUnbound = totalUnbound;
}

function deptTreeCollectNodes(node, out) {
    out.push(node);
    node.childNodes.forEach(function (c) { deptTreeCollectNodes(c, out); });
}

function deptTreeCountVisibleDepts(node) {
    var count = 0;
    node.childNodes.forEach(function (c) {
        if (c.visible && !c.virtual) { count++; }
        count += deptTreeCountVisibleDepts(c);
    });
    return count;
}

/** 可见节点里未绑定企微部门的部门数（数字与“当前展示”口径一致）。 */
function deptTreeCountVisibleUnbound(node) {
    var count = 0;
    node.childNodes.forEach(function (c) {
        if (!c.visible) { return; }
        if (!c.virtual && !c.binding) { count++; }
        count += deptTreeCountVisibleUnbound(c);
    });
    return count;
}

/** 分部节点：子树统计 + 可见性（分部被筛掉时整棵子树都不显示）。 */
function deptTreeWalkSub(node) {
    var count = 0;
    var unbound = 0;
    var childVisible = 0;
    node.childNodes.forEach(function (c) {
        var r = deptTreeWalkDept(c);
        count += r.count;
        unbound += r.unbound;
        if (c.visible) { childVisible++; }
    });
    node.subtreeDepts = count;
    node.subtreeUnbound = unbound;
    node.visibleChildCount = childVisible;
    node.visible = node.fbGate && childVisible > 0;
    return { count: count, unbound: unbound };
}

/**
 * 部门节点：先按“分部/部门闸门”判断本节点是否计入统计，再汇总下级部门。
 * 部门自身未命中关键字时仍会作为下级命中节点的上级路径保留。
 */
function deptTreeWalkDept(node) {
    var gate = node.fbGate && node.deptGate;
    var count = gate ? 1 : 0;
    var unbound = (gate && !node.binding) ? 1 : 0;
    var childVisible = 0;
    node.childNodes.forEach(function (c) {
        var r = deptTreeWalkDept(c);
        count += r.count;
        unbound += r.unbound;
        if (c.visible) { childVisible++; }
    });
    node.subtreeDepts = count;
    node.subtreeUnbound = unbound;
    node.visibleChildCount = childVisible;
    node.visible = gate && (node.selfHit || childVisible > 0);
    return { count: count, unbound: unbound };
}

/** 逐层展开成行列表：分部行 → 部门行 → 下级部门行。 */
function deptTreeCollectRows() {
    var rows = [];
    deptTreeRootIds.forEach(function (sub) {
        if (sub.visible) { deptTreePushSub(sub, rows); }
    });
    return rows;
}

function deptTreePushSub(node, rows) {
    rows.push({ kind: "sub", node: node, depth: 0 });
    if (deptTreeCollapsed[node.id]) { return; }
    node.childNodes.forEach(function (c) {
        if (c.visible) { deptTreePushDept(c, 1, rows); }
    });
}

function deptTreePushDept(node, depth, rows) {
    rows.push({ kind: "dept", node: node, depth: depth });
    if (deptTreeCollapsed[node.id]) { return; }
    node.childNodes.forEach(function (c) {
        if (c.visible) { deptTreePushDept(c, depth + 1, rows); }
    });
}

/** 部门行右列：该 OA 部门已绑定的企微部门；多对一时标注共享该企微部门的 OA 部门数。 */
function deptTreeWxCellHtml(node) {
    if (node.virtual || !node.binding) { return "<span class='map-empty-cell'>未绑定" + P_SHORT + "部门</span>"; }
    var wxDeptId = String(node.binding.wx_dept_id == null ? "" : node.binding.wx_dept_id);
    if (!wxDeptId || wxDeptId === "0") { return "<span class='map-empty-cell'>未绑定" + P_SHORT + "部门</span>"; }
    var wxName = deptTreeWxDeptName(wxDeptId);
    var share = (boundListByWx[wxDeptId] || []).length;
    var shareHtml = share > 1
        ? "<span class='tag manual' title='同一" + P_SHORT + "部门可被多个 OA 部门绑定（多对一），当前有 "
            + share + " 个 OA 部门绑定到该" + P_SHORT + "部门'>已被 " + share + " 个 OA 部门绑定</span>"
        : "";
    var defaultHtml = String(node.binding.sync_default) === "1"
        ? "<span class='tag system'>人员同步默认部门</span>" : "";
    return "<span class='tag bound'>部门已绑定</span>"
        + "<span class='map-wx-acct'>"
        + "<span class='map-user-name'>" + esc(wxName || (P_SHORT + "部门 " + wxDeptId)) + "</span>"
        + "<span class='map-wx-sub'>（" + esc(wxDeptId) + "）</span>"
        + "</span>"
        + defaultHtml + shareHtml;
}

/** 带收起箭头的“分组行”（分部/部门）左侧单元格：箭头 + 名称 + 未绑定部门数徽标。 */
function deptTreeGroupCellHtml(row, nameHtml) {
    var node = row.node;
    var hasKids = node.visibleChildCount > 0;
    var open = !deptTreeCollapsed[node.id];
    var caret = hasKids
        ? "<button type='button' class='tree-caret' data-node='" + esc(node.id) + "' title='"
            + (open ? "收起下级" : "展开下级") + "'>" + (open ? "▾" : "▸") + "</button>"
        : "<span class='tree-caret leaf'>•</span>";
    var badgeTip = "未绑定" + P_SHORT + "部门的 OA 部门 " + node.subtreeUnbound + " 个（含下级部门）；"
        + "含下级部门共 " + node.subtreeDepts + " 个";
    var badge = "<span class='map-badge" + (node.subtreeUnbound ? "" : " zero") + "'"
        + " title='" + esc(badgeTip) + "'>" + node.subtreeUnbound + "</span>";
    return "<div class='map-col map-col-oa' style='padding-left:" + (8 + row.depth * 20) + "px;'>"
        + caret
        + "<span class='map-dept-name'>" + nameHtml + "</span>"
        + badge
        + "</div>";
}

function deptTreeSubRowHtml(row) {
    var node = row.node;
    var nameHtml = node.virtual ? esc(node.name) : OaonesHrm.sub(node.subId, node.name);
    return "<div class='map-row map-row-sub' data-node='" + esc(node.id) + "'>"
        + deptTreeGroupCellHtml(row, nameHtml)
        + "<div class='map-col map-col-wx'><span class='map-empty-cell'>—</span></div>"
        + "</div>";
}

function deptTreeDeptRowHtml(row) {
    var node = row.node;
    var nameHtml = node.virtual ? esc(node.name) : OaonesHrm.dept(node.rawId, node.name);
    var code = node.virtual ? "" : deptTreeDeptCode(node.rawId);
    if (code) { nameHtml += "<span class='map-wx-sub'>（编号 " + esc(code) + "）</span>"; }
    return "<div class='map-row map-row-dept' data-node='" + esc(node.id) + "'>"
        + deptTreeGroupCellHtml(row, nameHtml)
        + "<div class='map-col map-col-wx'>" + deptTreeWxCellHtml(node) + "</div>"
        + "</div>";
}

function deptTreeRowHtml(row) {
    return row.kind === "sub" ? deptTreeSubRowHtml(row) : deptTreeDeptRowHtml(row);
}

/** 筛选栏下方提示：当前展示规模 + 当前筛选条件。 */
function deptTreeSummaryText() {
    var text = "当前展示 " + deptTreeTotalSubs + " 个分部、" + deptTreeTotalDepts + " 个 OA 部门；"
        + "其中未绑定" + P_SHORT + "部门 " + deptTreeTotalUnbound + " 个（数字按节点含下级汇总）";
    var filters = [];
    if ((deptTreeFbIds || []).length) { filters.push("分部 " + deptTreeFbIds.length + " 个"); }
    if ((deptTreeDeptIds || []).length) { filters.push("部门 " + deptTreeDeptIds.length + " 个"); }
    if (deptTreeKey()) { filters.push("关键字“" + deptTreeKeyword + "”"); }
    if (filters.length) { text += "；当前筛选：" + filters.join("、"); }
    return text;
}

/** 渲染整棵树：数据来自页壳已加载的 OA 组织、部门绑定与企微部门快照。 */
function renderDeptTree() {
    var wrap = byId("deptMapTree");
    var emptyEl = byId("deptTreeEmpty");
    var tipEl = byId("deptTreeTip");
    if (!wrap || !emptyEl) { return; }
    // 切换租户：重置收起状态与全部筛选条件
    if (deptTreeStateCfg !== String(state.cfgId == null ? "" : state.cfgId)) {
        deptTreeStateCfg = String(state.cfgId == null ? "" : state.cfgId);
        deptTreeCollapsed = {};
        deptTreeKeyword = "";
        deptTreeFbIds = [];
        deptTreeDeptIds = [];
        var kw = byId("deptTreeKeyword");
        if (kw) { kw.value = ""; }
        deptTreeRenderFilterInputs();
    }
    if (!state.cfgId) {
        wrap.innerHTML = "";
        emptyEl.style.display = "block";
        emptyEl.textContent = "请先选择" + P_FULL + "租户";
        if (tipEl) { tipEl.textContent = ""; }
        return;
    }
    deptTreeBuildModel();
    var rows = deptTreeCollectRows();
    if (!rows.length) {
        wrap.innerHTML = "";
        emptyEl.style.display = "block";
        emptyEl.textContent = deptTreeFilterOn()
            ? "没有符合筛选条件的分部/部门，请调整分部、部门或关键字"
            : "当前租户下没有可展示的 OA 分部/部门，请先维护 OA 组织架构后刷新";
        if (tipEl) { tipEl.textContent = deptTreeSummaryText(); }
        return;
    }
    var html = "";
    rows.forEach(function (row) { html += deptTreeRowHtml(row); });
    wrap.innerHTML = html;
    emptyEl.style.display = "none";
    if (tipEl) { tipEl.textContent = deptTreeSummaryText(); }
}

function deptTreeToggleNode(id) {
    var key = String(id == null ? "" : id);
    if (deptTreeCollapsed[key]) { delete deptTreeCollapsed[key]; } else { deptTreeCollapsed[key] = true; }
    renderDeptTree();
}

function deptTreeExpandAll() {
    deptTreeCollapsed = {};
    renderDeptTree();
}

/** 收起全部：只保留分部行与部门行（下级部门折叠），分部行不收起。 */
function deptTreeCollapseAll() {
    deptTreeCollapsed = {};
    (deptTreeNodeList || []).forEach(function (node) {
        if (node.kind === "dept" && node.visible && node.visibleChildCount) {
            deptTreeCollapsed[node.id] = true;
        }
    });
    renderDeptTree();
}

function deptTreeResetFilter() {
    deptTreeKeyword = "";
    deptTreeFbIds = [];
    deptTreeDeptIds = [];
    deptTreeCollapsed = {};
    byId("deptTreeKeyword").value = "";
    deptTreeRenderFilterInputs();
    renderDeptTree();
}

function deptTreeClearFbFilter() {
    deptTreeFbIds = [];
    deptTreeCollapsed = {};
    deptTreeRenderFilterInputs();
    renderDeptTree();
}

function deptTreeClearDeptFilter() {
    deptTreeDeptIds = [];
    deptTreeCollapsed = {};
    deptTreeRenderFilterInputs();
    renderDeptTree();
}

/** 筛选输入框显示：未选为空占位，单选显示名称+ID，多选显示数量（与 A 页口径一致）。 */
function deptTreeRenderFilterInputs() {
    var fbIds = deptTreeFbIds || [];
    var deptIds = deptTreeDeptIds || [];
    var fbInput = byId("deptTreeFbInput");
    var deptInput = byId("deptTreeDeptInput");
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
    byId("deptTreeFbPicker").classList.toggle("has-value", fbIds.length > 0);
    byId("deptTreeDeptPicker").classList.toggle("has-value", deptIds.length > 0);
}

/* ------------------------------------------------------------------
 * 分部 / 部门 多选弹框：交互与“OA数据基准”页的分部/部门筛选一致
 * （列表式/树状、双击确定、全选、先选分部再选部门的前置条件过滤）。
 * ------------------------------------------------------------------ */

function deptTreeOpenPick(type) {
    var isSub = type === "fb";
    var fbIds = (deptTreeFbIds || []).map(String);
    OaonesPicker.open({
        key: "deptTreePick:" + type,
        title: isSub ? "选择分部" : "选择部门",
        multiple: true,
        selectChildren: true,
        initialSelected: (isSub ? deptTreeFbIds : deptTreeDeptIds) || [],
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
                deptTreeFbIds = expandFbIds(selection.ids);
                var keepSub = deptTreeToSet(deptTreeFbIds);
                deptTreeDeptIds = (deptTreeDeptIds || []).filter(function (id) {
                    var dept = deptTreeFindOption(oaDeptOptions, id);
                    return dept && keepSub[String(dept.subcompanyid1 == null ? "" : dept.subcompanyid1)];
                });
            } else {
                deptTreeDeptIds = deptTreeExpandDeptIds(selection.ids);
            }
            deptTreeCollapsed = {};
            deptTreeRenderFilterInputs();
            renderDeptTree();
        }
    });
}

/* ------------------------------------------------------------------
 * 页面事件
 * ------------------------------------------------------------------ */

/* 树行交互：点箭头或节点行左侧区域收起/展开（链接与右侧企微列不触发）。 */
byId("deptMapTree").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.closest) { return; }
    var caret = t.closest(".tree-caret");
    // 占位箭头（.tree-caret.leaf，叶子部门）不是展开箭头，落到下面的“点行展开”逻辑
    if (caret && !caret.classList.contains("leaf")) {
        deptTreeToggleNode(caret.getAttribute("data-node"));
        return;
    }
    // 分部名/部门名是泛微标准页面链接，点链接只打开标准页面，不触发展开收起
    if (t.closest("a")) { return; }
    var row = t.closest(".map-row-sub") || t.closest(".map-row-dept");
    if (row && t.closest(".map-col-oa")) { deptTreeToggleNode(row.getAttribute("data-node")); }
});

byId("deptTreeFbPicker").addEventListener("click", function () { deptTreeOpenPick("fb"); });
byId("deptTreeDeptPicker").addEventListener("click", function () { deptTreeOpenPick("dept"); });
byId("deptTreeFbClear").addEventListener("click", function (e) {
    e.stopPropagation();
    deptTreeClearFbFilter();
});
byId("deptTreeDeptClear").addEventListener("click", function (e) {
    e.stopPropagation();
    deptTreeClearDeptFilter();
});
byId("deptTreeKeyword").addEventListener("input", function () {
    deptTreeKeyword = this.value;
    // 筛选变化后展开全部，保证命中的节点不被折叠在分支里
    deptTreeCollapsed = {};
    renderDeptTree();
});
byId("deptTreeExpandBtn").addEventListener("click", deptTreeExpandAll);
byId("deptTreeCollapseBtn").addEventListener("click", deptTreeCollapseAll);
byId("deptTreeResetBtn").addEventListener("click", deptTreeResetFilter);
</script>
