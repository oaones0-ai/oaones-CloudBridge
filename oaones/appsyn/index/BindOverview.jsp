<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
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
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>绑定总览 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <%-- 本页租户筛选是“全部平台租户”（企微 + 钉钉 + 飞书），覆盖 api.js 的默认文案 --%>
    <script>window.OAONES_CFG_ALL_LABEL = "全部平台租户";</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/excel.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 本页是模块首页目录下的**公共只读总览页**：把三个连接器（企微 / 钉钉 / 飞书）的
     全部平台租户放在一起，分别展示部门绑定 / 人员绑定；
     它不挂在某个租户的二级菜单下（地址不带 cfg_id），也不与其它菜单互链 —— 由 E9 后台菜单单独配入口。 --%>
<div class="navbar">
    <h1>绑定总览</h1>
</div>
<div class="page">
    <div class="card">
        <div class="toolbar" style="justify-content:space-between;">
            <div class="toolbar-left">
                <div><h3 style="margin:2px 0;">部门 / 人员绑定总览
                    <span class="help-tip inline" id="ovHelpTip" data-help="ovHelpText" role="button" tabindex="0"
                          title="查看页面说明" aria-label="查看页面说明">?</span></h3></div>
            </div>
            <div>
                <button class="btn secondary" id="ovExpandBtn" title="展开全部分组">展开全部</button>
                <button class="btn secondary" id="ovCollapseBtn" title="收起全部分组，只保留每个平台租户的分组行">收起全部</button>
                <button class="btn secondary" id="ovRefreshBtn">刷新</button>
                <button class="btn" id="ovExportBtn" title="把当前页签、当前筛选条件下的明细导出为 Excel">导出 Excel</button>
            </div>
        </div>
        <%-- 页面说明按登录人角色分两套：系统管理员（全部租户口径）与维护人/普通用户（本人口径） --%>
        <div class="help-source" id="ovHelpText"><% if (OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))) { %>本页是<span class="rule">公共只读总览</span>：把<span class="rule">全部平台租户</span>（企业微信 / 钉钉 / 飞书）放在一起，
            分<b>部门绑定 / 人员绑定</b>两块展示“谁绑到了哪个平台的哪个对象”。
            你是<b>系统管理员</b>（user.id=1）或超管角色成员，看到的是全部租户（含未指定维护人的租户）。
            本页<span class="rule">只能查看、不能设置</span>：绑定/解绑/同步等操作仍在各连接器的
            “部门绑定 / 人员绑定”页维护，本页不提供任何写入口，也不会展示 Secret 等敏感配置。
            页签里按<b>平台租户分组</b>展示：分组行是该租户（平台 / 名称 / 企业标识 / 启停 / 绑定条数），
            点分组行可收起或展开（绑定记录很多时默认收起，便于先看整体），组内一行一条绑定关系。
            上方筛选可组合使用：<b>平台</b>（全部 / 企业微信 / 钉钉 / 飞书）、<b>平台租户</b>（全部 / 某个租户）、
            <b>关键字</b>（OA 部门/人员名称、登录名、工号，或平台成员/部门、租户名称），
            统计行与分组列表都随筛选条件变化（默认不筛选时就是全部平台租户与全部绑定记录；
            带关键字时只列出有关键字命中的租户）。点“导出 Excel”导出的是
            <b>当前页签 + 当前筛选条件</b>下的明细（一行一条绑定关系），文件名带时间戳。
        <% } else { %>本页是<span class="rule">公共只读总览</span>：把<span class="rule">全部平台租户</span>（企业微信 / 钉钉 / 飞书）放在一起，
            分<b>部门绑定 / 人员绑定</b>两块展示“谁绑到了哪个平台的哪个对象”。
            本页只列<b>全部平台租户</b>的绑定现状，便于对照查看；它<span class="rule">只能查看、不能设置</span>：
            需要维护绑定关系时，请到你有维护权的租户对应的
            “部门绑定 / 人员绑定”页操作，本页不提供任何写入口。
            页签里按<b>平台租户分组</b>展示：分组行是该租户（平台 / 名称 / 企业标识 / 启停 / 绑定条数），
            点分组行可收起或展开（绑定记录很多时默认收起，便于先看整体），组内一行一条绑定关系。
            上方筛选可组合使用：<b>平台</b>（全部 / 企业微信 / 钉钉 / 飞书）、<b>平台租户</b>（全部 / 某个租户）、
            <b>关键字</b>（OA 部门/人员名称、登录名、工号，或平台成员/部门、租户名称），
            统计行与分组列表都随筛选条件变化（默认不筛选时就是全部平台租户与全部绑定记录；
            带关键字时只列出有关键字命中的租户）。点“导出 Excel”导出的是
            <b>当前页签 + 当前筛选条件</b>下的明细（一行一条绑定关系），文件名带时间戳。
        <% } %></div>
        <div class="filter-bar">
            <span class="filter-label">筛选：</span>
            <select id="ovPlatform" name="oaonesOvPlatform" title="按平台筛选：企业微信 / 钉钉 / 飞书">
                <option value="">全部平台</option>
                <option value="0">企业微信</option>
                <option value="1">钉钉</option>
                <option value="2">飞书</option>
            </select>
            <select id="ovCfg" name="oaonesOvCfg" title="按平台租户筛选">
                <option value="">全部平台租户</option>
            </select>
            <input type="text" id="ovKeyword" name="oaonesOvKeyword" autocomplete="off" data-lpignore="true"
                   style="min-width:260px;max-width:360px;"
                   placeholder="关键字：OA 部门/人员、登录名、工号、平台成员/部门"
                   title="按 OA 部门/人员名称、登录名、工号，或平台成员/部门、租户名称过滤">
            <button type="button" class="btn small" id="ovSearchBtn">搜索</button>
            <button type="button" class="btn small secondary" id="ovResetBtn">重置筛选</button>
        </div>
        <div class="bind-stats" id="ovStats"
             title="统计口径：当前筛选条件下的平台租户数与两类绑定条数"></div>
        <div class="sub-tabs" id="ovTabs">
            <button type="button" class="active" data-kind="dept">部门绑定</button>
            <button type="button" data-kind="user">人员绑定</button>
        </div>
        <div class="ov-head" id="ovHead"></div>
        <div class="ov-body" id="ovBody"></div>
        <div class="empty" id="ovEmpty" style="display:none;">暂无可展示的绑定数据</div>
    </div>
</div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
/**
 * 绑定总览（公共只读页）：三个连接器的全部平台租户 + 两类绑定关系。
 *
 * 数据来自 overview.list（一次返回全部平台租户、部门/人员两类绑定行与统计），
 * 页面只做展示、分组与导出，不提供任何写操作；
 * 平台/租户/关键字三个筛选条件由后端过滤（切页签不重新请求）。
 */
/** 平台类型 → 文案（0 企微 / 1 钉钉 / 2 飞书） */
var OV_PLATFORM_TEXT = { "0": "企业微信", "1": "钉钉", "2": "飞书" };
/** 页签：key 与接口返回的 depts / users 对应 */
var OV_TABS = [
    { key: "dept", label: "部门绑定", rowsKey: "depts" },
    { key: "user", label: "人员绑定", rowsKey: "users" }
];
/** 绑定记录很多时默认收起分组，避免一次渲染上千行（仍可点分组行或“展开全部”查看） */
var OV_AUTO_COLLAPSE_AT = 300;
var ovState = {
    kind: "dept",
    /** 分组展示用的租户（受平台 / 租户 / 关键字筛选影响） */
    cfgs: [],
    /** 租户下拉候选（只按平台筛选，选中某个租户后不会只剩它自己） */
    cfgOptions: [],
    rows: { depts: [], users: [] },
    count: {},
    /** 分组收起状态：租户键 → true 表示已收起 */
    collapsed: {},
    /** 本次渲染是否默认收起（当前页签绑定记录很多时为 true） */
    autoCollapsed: false
};

function ovById(id) { return document.getElementById(id); }

/** 租户分组键：平台 + 租户ID（绑定行与租户列表用同一口径，保证分组与收起状态对齐） */
function ovKeyOf(platformType, cfgId) {
    return String(platformType == null ? "" : platformType) + "-"
        + String(cfgId == null ? "" : cfgId);
}
function ovRowKey(row) { return ovKeyOf(row.platform_type, row.cfg_id); }
function ovCfgKey(cfg) { return ovKeyOf(cfg.platform_type, cfg.id); }

function ovActiveTab() {
    for (var i = 0; i < OV_TABS.length; i++) {
        if (OV_TABS[i].key === ovState.kind) { return OV_TABS[i]; }
    }
    return OV_TABS[0];
}

function ovRows() {
    var rows = ovState.rows[ovActiveTab().rowsKey];
    return rows || [];
}

/** 平台侧对象展示：名称优先，取不到时退化为ID；两者都没有时显示 — */
function ovPlatformCell(name, ref) {
    var n = String(name == null ? "" : name).trim();
    var r = String(ref == null ? "" : ref).trim();
    if (n.length === 0 && r.length === 0) { return "—"; }
    var html = n.length === 0 ? Oaones.esc(r) : Oaones.esc(n);
    if (n.length > 0 && r.length > 0 && n !== r) {
        html += ' <span class="ov-sub">(' + Oaones.esc(r) + ")</span>";
    }
    return html;
}

/** OA 侧封存/离职标记 */
function ovOaStateTag(canceled) {
    return String(canceled == null ? "" : canceled) === "1"
        ? ' <span class="tag fail">已封存</span>' : "";
}

/* ==================================================================
 * 一、页签列定义（每个页签一套列，render 返回 HTML、text 用于导出）
 * ================================================================== */
var OV_COLUMNS = {
    dept: [
        { title: "OA 部门", width: 24, render: function (r) {
            return OaonesHrm.dept(r.oa_id, r.oa_name) + ovOaStateTag(r.oa_canceled);
        }, text: function (r) { return r.oa_name || ""; } },
        { title: "所属分部", width: 18, render: function (r) {
            return r.oa_sub_id ? OaonesHrm.sub(r.oa_sub_id, r.oa_sub_name) : "—";
        }, text: function (r) { return r.oa_sub_name || ""; } },
        { title: "上级部门", width: 18, render: function (r) {
            return r.oa_parent_id ? OaonesHrm.dept(r.oa_parent_id, r.oa_parent_name) : "—";
        }, text: function (r) { return r.oa_parent_name || ""; } },
        { title: "平台侧部门", width: 28, render: function (r) {
            return ovPlatformCell(r.platform_dept_name, r.platform_dept_ref);
        }, text: function (r) { return ovCellText(r.platform_dept_name, r.platform_dept_ref); } },
        { title: "绑定时间", width: 18, render: function (r) {
            return Oaones.esc(r.bind_time || "—");
        }, text: function (r) { return r.bind_time || ""; } }
    ],
    user: [
        { title: "OA 人员", width: 16, render: function (r) {
            return OaonesHrm.user(r.oa_id, r.oa_name) + ovOaStateTag(ovUserCanceled(r));
        }, text: function (r) { return r.oa_name || ""; } },
        { title: "登录名 / 工号", width: 14, render: function (r) {
            var login = String(r.oa_login_id || "");
            var code = String(r.oa_workcode || "");
            if (login.length === 0 && code.length === 0) { return "—"; }
            return Oaones.esc(login || "—") + (code.length > 0 ? ' <span class="ov-sub">(' + Oaones.esc(code) + ")</span>" : "");
        }, text: function (r) { return r.oa_login_id || r.oa_workcode || ""; } },
        { title: "所属部门 / 分部", width: 22, render: function (r) {
            var dept = r.oa_dept_id ? OaonesHrm.dept(r.oa_dept_id, r.oa_dept_name) : Oaones.esc(r.oa_dept_name || "—");
            var sub = r.oa_sub_id ? OaonesHrm.sub(r.oa_sub_id, r.oa_sub_name) : Oaones.esc(r.oa_sub_name || "");
            return sub ? (dept + ' <span class="ov-sep">/</span> ' + sub) : dept;
        }, text: function (r) {
            var dept = r.oa_dept_name || "";
            var sub = r.oa_sub_name || "";
            return sub ? (dept + " / " + sub) : dept;
        } },
        { title: "平台侧成员", width: 20, render: function (r) {
            return ovPlatformCell(r.platform_user_name, r.platform_user_ref);
        }, text: function (r) { return ovCellText(r.platform_user_name, r.platform_user_ref); } },
        { title: "平台侧部门", width: 20, render: function (r) {
            return ovPlatformCell(r.platform_dept_name, r.platform_dept_ref);
        }, text: function (r) { return ovCellText(r.platform_dept_name, r.platform_dept_ref); } },
        { title: "绑定时间", width: 18, render: function (r) {
            return Oaones.esc(r.bind_time || "—");
        }, text: function (r) { return r.bind_time || ""; } }
    ]
};

/** 单元格文本（导出用）：名称 +（ID），两者都没有时为空 */
function ovCellText(name, ref) {
    var n = String(name == null ? "" : name).trim();
    var r = String(ref == null ? "" : ref).trim();
    if (n.length === 0) { return r; }
    return r.length > 0 && r !== n ? (n + "（" + r + "）") : n;
}

/** OA 人员是否已失效：status >= 4（离职/解聘/退休等） */
function ovUserCanceled(row) {
    var status = parseInt(row.oa_status, 10);
    return isNaN(status) ? "0" : (status >= 4 ? "1" : "0");
}

/* ==================================================================
 * 二、渲染
 * ================================================================== */

/** 统计行：平台租户（按平台拆分）与当前筛选下的两类绑定条数 */
function ovRenderStats() {
    var c = ovState.count || {};
    var html = ""
        + '<span class="bind-stat"><span>平台租户</span><b>' + Oaones.esc(c.tenant || 0) + "</b>"
        + '<span class="ov-sub">企业微信 ' + Oaones.esc(c.tenant_wecom || 0)
        + " / 钉钉 " + Oaones.esc(c.tenant_dingtalk || 0)
        + " / 飞书 " + Oaones.esc(c.tenant_feishu || 0) + "</span></span>"
        + '<span class="bind-stat"><span>部门绑定</span><b>' + Oaones.esc(c.dept || 0) + "</b></span>"
        + '<span class="bind-stat"><span>人员绑定</span><b>' + Oaones.esc(c.user || 0) + "</b></span>";
    ovById("ovStats").innerHTML = html;
}

/** 表头：按当前页签的列定义渲染（列数决定栅格样式） */
function ovRenderHead() {
    var cols = OV_COLUMNS[ovState.kind] || [];
    var html = "";
    for (var i = 0; i < cols.length; i++) {
        html += "<div>" + Oaones.esc(cols[i].title) + "</div>";
    }
    var head = ovById("ovHead");
    head.className = "ov-head ov-cols-" + cols.length;
    head.innerHTML = html;
}

/**
 * 分组行：平台租户（平台标记 + 租户名 + 企业标识 + 启停 + 本组条数）。
 * 点分组行收起/展开（绑定记录很多时默认收起，先看整体）。
 */
function ovGroupHeadHtml(cfg, rows) {
    var open = !ovState.collapsed[ovCfgKey(cfg)];
    var active = String(cfg.enable == null ? "" : cfg.enable);
    var html = '<div class="ov-group" data-group="' + Oaones.esc(ovCfgKey(cfg)) + '" role="button" tabindex="0"'
        + ' title="' + (open ? "点击收起该租户的绑定明细" : "点击展开该租户的绑定明细") + '">'
        + '<span class="ov-group-arrow">' + (open ? "▾" : "▸") + "</span>"
        + '<span class="tag ' + (active === "0" ? "off" : "bound") + '">'
        + Oaones.esc(cfg.platform || OV_PLATFORM_TEXT[String(cfg.platform_type)] || "平台") + "</span>"
        + '<span class="ov-group-name">' + Oaones.esc(cfg.corp_name || ("#" + cfg.id)) + "</span>"
        + '<span class="ov-sub">租户 #' + Oaones.esc(cfg.id) + "</span>";
    if (cfg.corp_id) {
        html += '<span class="ov-sub">企业标识 ' + Oaones.esc(cfg.corp_id) + "</span>";
    }
    if (cfg.root_dept_ref) {
        html += '<span class="ov-sub">根部门 ' + Oaones.esc(cfg.root_dept_name || cfg.root_dept_ref) + "</span>";
    }
    if (active === "0") {
        html += '<span class="tag off">同步已停用</span>';
    }
    html += '<span class="ov-group-count">' + ovActiveTab().label + " " + rows.length
        + " 条</span></div>";
    return html;
}

/** 分组体：一行一条绑定关系；空分组给一行说明 */
function ovRenderBody() {
    var cols = OV_COLUMNS[ovState.kind] || [];
    var rows = ovRows();
    var byGroup = {};
    for (var i = 0; i < rows.length; i++) {
        var key = ovRowKey(rows[i]);
        if (!byGroup[key]) { byGroup[key] = []; }
        byGroup[key].push(rows[i]);
    }
    var html = "";
    var shown = 0;
    for (var c = 0; c < ovState.cfgs.length; c++) {
        var cfg = ovState.cfgs[c];
        var groupRows = byGroup[ovCfgKey(cfg)] || [];
        html += ovGroupHeadHtml(cfg, groupRows);
        shown++;
        if (ovState.collapsed[ovCfgKey(cfg)]) { continue; }
        if (groupRows.length === 0) {
            html += '<div class="ov-row ov-cols-1"><div class="ov-sub">该租户暂无'
                + Oaones.esc(ovActiveTab().label) + "记录</div></div>";
            continue;
        }
        for (var r = 0; r < groupRows.length; r++) {
            html += '<div class="ov-row ov-cols-' + cols.length + '">';
            for (var k = 0; k < cols.length; k++) {
                html += "<div>" + cols[k].render(groupRows[r]) + "</div>";
            }
            html += "</div>";
        }
    }
    // 批注：绑定记录所属租户已被删除（绑定数据还在）时也展示出来，避免“数据看不见”
    var extraKeys = [];
    for (var gk in byGroup) {
        if (!Object.prototype.hasOwnProperty.call(byGroup, gk)) { continue; }
        var matched = false;
        for (var m = 0; m < ovState.cfgs.length; m++) {
            if (ovCfgKey(ovState.cfgs[m]) === gk) { matched = true; break; }
        }
        if (!matched) { extraKeys.push(gk); }
    }
    for (var e = 0; e < extraKeys.length; e++) {
        var extraRows = byGroup[extraKeys[e]];
        var head = extraRows[0];
        html += '<div class="ov-group" data-group="' + Oaones.esc(extraKeys[e]) + '" role="button" tabindex="0">'
            + '<span class="ov-group-arrow">' + (ovState.collapsed[extraKeys[e]] ? "▸" : "▾") + "</span>"
            + '<span class="tag fail">租户已删除</span>'
            + '<span class="ov-group-name">' + Oaones.esc(head.cfg_name || ("#" + head.cfg_id)) + "</span>"
            + '<span class="ov-sub">租户 #' + Oaones.esc(head.cfg_id) + "</span>"
            + '<span class="ov-group-count">' + ovActiveTab().label + " " + extraRows.length + " 条</span></div>";
        if (ovState.collapsed[extraKeys[e]]) { continue; }
        for (var er = 0; er < extraRows.length; er++) {
            html += '<div class="ov-row ov-cols-' + cols.length + '">';
            for (var ek = 0; ek < cols.length; ek++) {
                html += "<div>" + cols[ek].render(extraRows[er]) + "</div>";
            }
            html += "</div>";
        }
    }
    ovById("ovBody").innerHTML = html;
    ovById("ovEmpty").style.display = shown === 0 && extraKeys.length === 0 ? "" : "none";
}

/** 首次加载/切换页签：绑定记录很多时默认收起全部分组（先看整体，再按需展开） */
function ovApplyAutoCollapse() {
    var total = ovRows().length;
    ovState.collapsed = {};
    ovState.autoCollapsed = total > OV_AUTO_COLLAPSE_AT;
    if (!ovState.autoCollapsed) { return; }
    for (var i = 0; i < ovState.cfgs.length; i++) {
        ovState.collapsed[ovCfgKey(ovState.cfgs[i])] = true;
    }
}

function ovRender() {
    ovRenderStats();
    ovRenderHead();
    ovRenderBody();
}

/* ==================================================================
 * 三、筛选、加载与导出
 * ================================================================== */

/**
 * 租户下拉：候选取接口的 cfg_options（只按平台筛过，含没有任何绑定关系的租户）；
 * 本方法再按当前平台双保险过滤一次，已选租户不属于该平台时自动清空。
 */
function ovFillCfgSelect() {
    var sel = ovById("ovCfg");
    var current = sel.value || "";
    var platform = ovById("ovPlatform").value;
    var options = ovState.cfgOptions || [];
    var html = '<option value="">全部平台租户</option>';
    var keep = false;
    for (var i = 0; i < options.length; i++) {
        var c = options[i];
        if (platform !== "" && String(c.platform_type) !== String(platform)) { continue; }
        if (String(c.id) === current) { keep = true; }
        html += '<option value="' + Oaones.esc(c.id) + '">'
            + Oaones.esc((c.platform || "") + " · " + (c.corp_name || ("#" + c.id)))
            + (String(c.enable) === "0" ? "（停用）" : "") + "</option>";
    }
    sel.innerHTML = html;
    sel.value = keep ? current : "";
}

function ovLoad(keepCollapse) {
    var params = {
        platform: ovById("ovPlatform").value,
        cfg_id: ovById("ovCfg").value,
        keyword: ovById("ovKeyword").value
    };
    Oaones.api("overview.list", params, function (res) {
        if (!res || res.code !== 0) {
            if (res && res.msg) { OaonesToast.error(res.msg); }
            return;
        }
        var data = res.data || {};
        ovState.cfgs = data.cfgs || [];
        ovState.cfgOptions = data.cfg_options || [];
        ovState.rows = {
            depts: data.depts || [],
            users: data.users || []
        };
        ovState.count = data.count || {};
        ovFillCfgSelect();
        ovById("ovCfg").value = params.cfg_id || "";
        if (!keepCollapse) {
            ovApplyAutoCollapse();
        } else if (ovState.autoCollapsed) {
            // 已默认收起时，新增的租户（例如切换筛选后出现的租户）同样按收起处理
            for (var i = 0; i < ovState.cfgs.length; i++) {
                var key = ovCfgKey(ovState.cfgs[i]);
                if (ovState.collapsed[key] === undefined) { ovState.collapsed[key] = true; }
            }
        }
        ovRender();
    });
}

/** 导出：当前页签 + 当前筛选条件下的明细（一行一条绑定关系，按租户分组顺序） */
function ovExport() {
    var cols = OV_COLUMNS[ovState.kind] || [];
    var rows = ovRows();
    if (rows.length === 0) {
        OaonesToast.warn("当前页签没有可导出的绑定数据");
        return;
    }
    var columns = [{ title: "平台", width: 12 }, { title: "平台租户", width: 20 },
        { title: "租户ID", width: 10 }, { title: "企业标识", width: 22 }];
    for (var i = 0; i < cols.length; i++) {
        columns.push({ title: cols[i].title, width: cols[i].width || 18 });
    }
    var data = [];
    for (var r = 0; r < rows.length; r++) {
        var row = rows[r];
        var line = [
            row.platform || "",
            row.cfg_name || "",
            row.cfg_id == null ? "" : String(row.cfg_id),
            row.corp_id || ""
        ];
        for (var c = 0; c < cols.length; c++) {
            line.push(cols[c].text(row));
        }
        data.push(line);
    }
    var tab = ovActiveTab();
    var ok = OaonesExcel.export({
        filename: "绑定总览_" + tab.label + "_" + OaonesExcel.stamp() + ".xlsx",
        sheet: tab.label,
        columns: columns,
        rows: data
    });
    if (ok) {
        OaonesToast.info("已导出 " + data.length + " 条" + tab.label + "数据");
    }
}

/* ==================================================================
 * 四、事件注册（页壳末尾统一注册，不在解析期访问 DOM）
 * ================================================================== */
function ovBindEvents() {
    var tabs = ovById("ovTabs");
    if (tabs) {
        tabs.addEventListener("click", function (e) {
            var btn = e.target;
            while (btn && btn !== tabs && btn.tagName !== "BUTTON") { btn = btn.parentNode; }
            if (!btn || btn === tabs) { return; }
            ovState.kind = btn.getAttribute("data-kind") || "dept";
            var all = tabs.getElementsByTagName("button");
            for (var i = 0; i < all.length; i++) {
                if (all[i] === btn) { all[i].className = "active"; } else { all[i].className = ""; }
            }
            ovState.collapsed = {};
            ovApplyAutoCollapse();
            ovRender();
        });
    }
    var body = ovById("ovBody");
    if (body) {
        var toggle = function (e) {
            var el = e.target;
            while (el && el !== body && el.className.indexOf("ov-group") < 0) { el = el.parentNode; }
            if (!el || el === body) { return; }
            var key = el.getAttribute("data-group");
            if (!key) { return; }
            ovState.collapsed[key] = !ovState.collapsed[key];
            ovRenderBody();
        };
        body.addEventListener("click", toggle);
        body.addEventListener("keydown", function (e) {
            if (e.keyCode !== 13 && e.keyCode !== 32) { return; }
            toggle(e);
        });
    }
    ovById("ovPlatform").addEventListener("change", function () {
        ovFillCfgSelect();
        ovLoad(false);
    });
    ovById("ovCfg").addEventListener("change", function () { ovLoad(false); });
    ovById("ovSearchBtn").addEventListener("click", function () { ovLoad(false); });
    ovById("ovKeyword").addEventListener("keydown", function (e) {
        if (e.keyCode === 13) { ovLoad(false); }
    });
    ovById("ovResetBtn").addEventListener("click", function () {
        ovById("ovPlatform").value = "";
        ovById("ovKeyword").value = "";
        ovFillCfgSelect();
        ovById("ovCfg").value = "";
        ovLoad(false);
    });
    ovById("ovRefreshBtn").addEventListener("click", function () { ovLoad(true); });
    ovById("ovExportBtn").addEventListener("click", ovExport);
    ovById("ovExpandBtn").addEventListener("click", function () {
        ovState.collapsed = {};
        ovState.autoCollapsed = false;
        ovRenderBody();
    });
    ovById("ovCollapseBtn").addEventListener("click", function () {
        for (var i = 0; i < ovState.cfgs.length; i++) {
            ovState.collapsed[ovCfgKey(ovState.cfgs[i])] = true;
        }
        ovRenderBody();
    });
    // 筛选框不许浏览器自动填充（本页没有密码框，仍按列表页口径关闭自动填充）
    var kw = ovById("ovKeyword");
    if (kw && kw.value !== "") { kw.value = ""; }
}

ovBindEvents();
ovLoad(false);
</script>
</body>
</html>
