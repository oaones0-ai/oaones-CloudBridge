<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    /* 本页三个平台共用：平台由地址决定（?platform=wecom|dingtalk|feishu，不传按企微）；
       接口入口、平台类型、导航片段与文案都由下面变量派生，页面逻辑完全一致。 */
    String oaonesPBPlatform = request.getParameter("platform");
    if (oaonesPBPlatform == null || oaonesPBPlatform.trim().length() == 0) {
        oaonesPBPlatform = (String) request.getAttribute("oaonesPBPlatform");
    }
    if (oaonesPBPlatform == null) {
        oaonesPBPlatform = "";
    }
    oaonesPBPlatform = oaonesPBPlatform.trim().toLowerCase();
    boolean oaonesPBDd = "dingtalk".equals(oaonesPBPlatform);
    boolean oaonesPBFs = "feishu".equals(oaonesPBPlatform);
    String oaonesPBFull = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企业微信");
    String oaonesPBShort = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企微");
    int oaonesPBType = oaonesPBFs ? 2 : (oaonesPBDd ? 1 : 0);
    String oaonesPBApiUrl = oaonesPBFs ? "/oaones/appsyn/Feishu/api.jsp"
            : (oaonesPBDd ? "/oaones/appsyn/DingTalk/api.jsp" : "/oaones/appsyn/WeCom/api.jsp");
    String oaonesPBNavbarPath = oaonesPBFs ? "/oaones/appsyn/Feishu/navbar.jsp"
            : (oaonesPBDd ? "/oaones/appsyn/DingTalk/navbar.jsp" : "/oaones/appsyn/WeCom/navbar.jsp");
    request.setAttribute("oaonesPBPlatform", oaonesPBFs ? "feishu" : (oaonesPBDd ? "dingtalk" : "wecom"));
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
%>

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
    <%-- 平台决定接口入口（AJAX 统一入口）与页面文案：三个平台共用本页，只换 platform 参数 --%>
    <script>window.OAONES_API_URL = "<%= oaonesPBApiUrl %>";
    window.OAONES_CFG_ALL_LABEL = "全部<%= oaonesPBFull %>租户";
    window.OAONES_CFG_TYPE = <%= oaonesPBType %>;
    var P_FULL = "<%= oaonesPBFull %>", P_SHORT = "<%= oaonesPBShort %>";
    var P_PLATFORM = "<%= oaonesPBPlatform %>";
    var P_KeyLabel = "<%= oaonesPBKeyLabel %>", P_AppIdLabel = "<%= oaonesPBAppIdLabel %>";</script>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>规则列表 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<jsp:include page="<%= oaonesPBNavbarPath %>">
    <jsp:param name="active" value="userRule"/>
</jsp:include>
<div class="page">
    <jsp:include page="userSwitch.jsp">
        <jsp:param name="active" value="ruleList"/>
    </jsp:include>
    <div class="card">
        <div class="toolbar">
            <div>
                <h3 style="margin:2px 0;">默认绑定规则列表<span class="help-tip inline" id="ruleListHelpTip"
                        data-help="ruleListHelpText" role="button" tabindex="0"
                        title="查看页面说明" aria-label="查看页面说明">?</span></h3>
            </div>
            <span style="flex:1;"></span>
            <button type="button" class="btn small secondary" id="toRuleEditBtn"
                    title="到“默认绑定规则”页维护默认配置或某个租户的独立配置">维护规则</button>
            <button type="button" class="btn small secondary" id="refreshRuleListBtn">刷新</button>
        </div>
        <div class="help-source" id="ruleListHelpText">本页以列表方式展示全部默认人员绑定规则，
            含<span class="rule">默认配置</span>与各<span class="rule">租户独立配置</span>：
            “配置对象”列区分默认配置与具体租户；“匹配字段”列给出该规则启用的字段，
            自动匹配按 <span class="rule">邮箱 → 手机号 → 姓名 → 工号</span> 的固定优先级依次执行。
            “默认批量绑定”优先取当前租户的独立配置，租户未单独配置时回退使用默认配置；
            点“编辑”到“默认绑定规则”页并自动选中对应配置对象，“删除”仅对租户独立配置开放
            （删除后该租户重新跟随默认配置，默认配置不能删除、只能编辑覆盖）。</div>
        <div class="filter-bar">
            <span class="filter-label">关键字：</span>
            <input type="text" id="ruleKeyword" placeholder="租户名称 / 匹配字段 / 修改人" autocomplete="off"
                   style="min-width:240px;max-width:320px;">
            <button type="button" class="btn small" id="ruleSearchBtn">搜索</button>
            <button type="button" class="btn small secondary" id="ruleResetBtn">重置</button>
        </div>
        <div class="bind-stats" id="ruleStats" style="display:none;"></div>
        <div class="table-wrap">
            <table class="grid" id="ruleTable">
                <thead>
                <tr>
                    <th style="width:25%;">配置对象</th>
                    <th style="width:23%;">匹配字段</th>
                    <th style="width:24%;">生效规则</th>
                    <th>最近修改</th>
                    <th style="width:13%;">操作</th>
                </tr>
                </thead>
                <tbody id="ruleBody"></tbody>
            </table>
            <div class="empty" id="ruleEmpty" style="display:none;">暂无绑定规则</div>
        </div>
        <div class="pager" id="rulePager"></div>
    </div>
</div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
/** 自动匹配的固定优先级（后端 UserBindServiceImpl.autoMatch 按此顺序执行） */
var RULE_FIELD_ORDER = ["email", "mobile", "name", "workcode"];
var ruleLabels = { email: "邮箱", mobile: "手机号", name: "姓名", workcode: "工号" };
/** 全部规则（默认配置 + 当前登录人可见的租户独立配置） */
var ruleRows = [];
/** 当前登录人可见的企微租户（用于把 cfg_id 显示成租户名称） */
var cfgRows = [];
var ruleKeyword = "";
/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js） */
var rulePager = OaonesTable.pager({
    key: "userRule.list",
    el: byId("rulePager"),
    onChange: function () { render(); }
});

function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }

/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/appsyn/js/toast.js 弹出。
 */
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

function ruleArr(value) {
    var out = [];
    String(value == null ? "" : value).split(",").forEach(function (v) {
        v = String(v == null ? "" : v).trim();
        if (v && out.indexOf(v) < 0) { out.push(v); }
    });
    return out;
}

function isDefaultRow(row) {
    var cfgId = String(row && row.cfg_id == null ? "" : row.cfg_id);
    return row && (row.source === "default" || cfgId === "" || cfgId === "0");
}

function cfgOf(cfgId) {
    var id = String(cfgId == null ? "" : cfgId);
    for (var i = 0; i < cfgRows.length; i++) {
        if (String(cfgRows[i].id) === id) { return cfgRows[i]; }
    }
    return null;
}

/** 配置对象名称：默认配置或租户名称（租户查不到时回退为租户ID提示）。 */
function ruleTargetName(row) {
    if (isDefaultRow(row)) { return "默认配置"; }
    var cfg = cfgOf(row.cfg_id);
    if (cfg) { return "租户【" + (cfg.corp_name || "") + "】"; }
    return P_SHORT + "租户 ID " + row.cfg_id;
}

/** 匹配字段标签：按固定优先级顺序展示该规则启用的字段。 */
function fieldTagsHtml(rules) {
    var picked = RULE_FIELD_ORDER.filter(function (v) { return rules.indexOf(v) >= 0; });
    // 兼容历史数据里出现的未知字段值（原样展示，避免漏看）
    rules.forEach(function (v) {
        if (RULE_FIELD_ORDER.indexOf(v) < 0) { picked.push(v); }
    });
    if (!picked.length) { return "<span class='tag fail'>未配置字段</span>"; }
    return picked.map(function (v) {
        return "<span class='tag bound'>" + esc(ruleLabels[v] || v) + "</span>";
    }).join(" ");
}

function modifyCellHtml(row) {
    var time = String(row.modify_time == null ? "" : row.modify_time);
    var modifierName = String(row.modifier_name || "");
    var by = modifierName || String(row.creator_name || "");
    if (!time) { return "<span class='muted'>—</span>"; }
    if (!by) { return esc(time); }
    // 修改人/创建人姓名输出人员卡片超链接（与姓名对应的ID一致）
    var byId = modifierName ? row.modifier_id : row.creator_id;
    return esc(time) + "<div class='user-mini'>修改人：" + OaonesHrm.user(byId, by) + "</div>";
}

function opsCellHtml(row) {
    var cfgId = isDefaultRow(row) ? "" : String(row.cfg_id == null ? "" : row.cfg_id);
    var html = "<button type='button' class='link rule-edit-btn' data-cfg='" + esc(cfgId) + "'>编辑</button>";
    if (!isDefaultRow(row)) {
        html += " <button type='button' class='link danger rule-del-btn' data-cfg='" + esc(cfgId)
            + "' data-name='" + esc(ruleTargetName(row)) + "'>删除</button>";
    }
    return html;
}

function rowHtml(row) {
    var isDefault = isDefaultRow(row);
    var rules = ruleArr(row.rules);
    var cfg = isDefault ? null : cfgOf(row.cfg_id);
    var target = isDefault
        ? "<b>默认配置</b><div class='user-mini'>未单独配置的租户都使用它</div>"
        : "<b>" + esc(cfg ? (cfg.corp_name || "") : (P_SHORT + "租户 ID " + row.cfg_id)) + "</b>"
            + (cfg && String(cfg.sync_enable) !== "1" ? " <span class='tag off'>停用</span>" : "")
            + "<div class='user-mini'>" + P_SHORT + "租户 ID " + esc(row.cfg_id) + "</div>";
    var effect = isDefault
        ? "<span class='tag system'>全局兜底</span><div class='user-mini'>所有未单独配置的租户自动使用</div>"
        : "<span class='tag manual'>仅本租户</span><div class='user-mini'>其他租户仍跟随默认配置</div>";
    return "<tr>"
        + "<td>" + target + "</td>"
        + "<td>" + fieldTagsHtml(rules)
        + "<div class='user-mini'>按 邮箱 → 手机号 → 姓名 → 工号 优先级匹配</div></td>"
        + "<td>" + effect + "</td>"
        + "<td>" + modifyCellHtml(row) + "</td>"
        + "<td>" + opsCellHtml(row) + "</td>"
        + "</tr>";
}

function visibleRows() {
    var rows = ruleRows.slice();
    // 展示顺序：默认配置在最前，其余按租户名称排序（名称取不到时排在最后）
    rows.sort(function (a, b) {
        var aDefault = isDefaultRow(a) ? 0 : 1;
        var bDefault = isDefaultRow(b) ? 0 : 1;
        if (aDefault !== bDefault) { return aDefault - bDefault; }
        if (aDefault === 0) { return 0; }
        var an = ruleTargetName(a);
        var bn = ruleTargetName(b);
        return an.localeCompare(bn, "zh-Hans-CN");
    });
    var k = String(ruleKeyword || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (r) {
        var rules = ruleArr(r.rules).map(function (v) { return ruleLabels[v] || v; }).join(" ");
        var text = [ruleTargetName(r), rules, r.modifier_name, r.creator_name, r.modify_time, r.create_time]
            .map(function (v) { return String(v == null ? "" : v); }).join(" ");
        return text.toLowerCase().indexOf(k) >= 0;
    });
}

function render() {
    var rows = visibleRows();
    rulePager.resetIfChanged(String(ruleKeyword == null ? "" : ruleKeyword));
    var pageRows = rulePager.apply(rows);
    var tenantCount = 0;
    var defaultCount = 0;
    ruleRows.forEach(function (r) {
        if (isDefaultRow(r)) { defaultCount++; } else { tenantCount++; }
    });
    byId("ruleStats").style.display = "";
    byId("ruleStats").innerHTML = ""
        + "<span class='bind-stat'><span>规则共计</span><b>" + ruleRows.length + "</b> 条</span>"
        + "<span class='bind-stat'><span>默认配置</span><b>" + defaultCount + "</b> 条</span>"
        + "<span class='bind-stat'><span>租户独立配置</span><b>" + tenantCount + "</b> 条</span>"
        + "<span class='bind-stat'><span>当前显示</span><b>" + rows.length + "</b> 条</span>";
    var body = "";
    pageRows.forEach(function (r) { body += rowHtml(r); });
    byId("ruleBody").innerHTML = body;
    rulePager.show();
    rulePager.render();
    OaonesTable.resizable(byId("ruleTable"), { key: "userRule.ruleTable" });
    var emptyEl = byId("ruleEmpty");
    emptyEl.style.display = rows.length ? "none" : "block";
    if (!ruleRows.length) {
        emptyEl.textContent = OaonesTips.pick(
            "当前还没有任何绑定规则：请到“默认绑定规则”页设置默认配置，或为某个租户保存独立配置。",
            "当前没有可维护的绑定规则：本页只列出默认配置，以及你被指定为维护人的租户独立配置；"
                + "请到“默认绑定规则”页维护默认配置，或联系管理员把你加入对应租户的维护人。");
    } else if (ruleKeyword) {
        emptyEl.textContent = "没有匹配“" + ruleKeyword + "”的绑定规则";
    } else {
        emptyEl.textContent = "当前筛选条件下暂无绑定规则";
    }
}

function loadRuleList() {
    byId("ruleStats").style.display = "none";
    Oaones.api("cfg.list", {}, function (cfgRes) {
        cfgRows = (cfgRes.code === 0 && cfgRes.data) ? cfgRes.data : [];
        Oaones.api("user.rule.list", {}, function (res) {
            if (res.code !== 0) {
                ruleRows = [];
                showToast("读取绑定规则失败：" + res.msg, 2);
            } else {
                ruleRows = res.data || [];
            }
            render();
        });
    });
}

function openRuleEdit(cfgId) {
    var id = String(cfgId == null ? "" : cfgId);
    // 带 cfg_id 跳转：默认绑定规则页会自动把“配置对象”选中为该租户
    location.href = "/oaones/appsyn/userBind/userRule.jsp?platform=" + P_PLATFORM
        + (id ? "&cfg_id=" + encodeURIComponent(id) : "");
}

function deleteRule(cfgId, targetName) {
    var id = String(cfgId == null ? "" : cfgId);
    if (!id) { return; }
    OaonesToast.confirm("删除" + targetName + "的独立规则后，该租户将自动使用默认配置，确认删除？", function () {
        Oaones.api("user.rule.delete", { cfg_id: id }, function (res) {
            if (res.code !== 0) {
                showToast("删除失败：" + res.msg, 2);
                return;
            }
            loadRuleList();
            showToast("已删除" + targetName + "的独立规则，该租户将使用默认配置");
        });
    }, { danger: true });
}

byId("ruleBody").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("rule-edit-btn")) {
        openRuleEdit(t.getAttribute("data-cfg"));
    } else if (t.classList.contains("rule-del-btn")) {
        deleteRule(t.getAttribute("data-cfg"), t.getAttribute("data-name"));
    }
});
byId("ruleSearchBtn").addEventListener("click", function () {
    ruleKeyword = byId("ruleKeyword").value;
    render();
});
byId("ruleKeyword").addEventListener("keydown", function (e) {
    if (e.key === "Enter") {
        ruleKeyword = byId("ruleKeyword").value;
        render();
    }
});
byId("ruleResetBtn").addEventListener("click", function () {
    byId("ruleKeyword").value = "";
    ruleKeyword = "";
    render();
});
byId("refreshRuleListBtn").addEventListener("click", loadRuleList);
byId("toRuleEditBtn").addEventListener("click", function () { openRuleEdit(""); });

loadRuleList();
</script>
</body>
</html>
