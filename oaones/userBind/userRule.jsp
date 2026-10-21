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
    String oaonesPBApiUrl = oaonesPBFs ? "/oaones/Feishu/api.jsp"
            : (oaonesPBDd ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    String oaonesPBNavbarPath = oaonesPBFs ? "/oaones/Feishu/navbar.jsp"
            : (oaonesPBDd ? "/oaones/DingTalk/navbar.jsp" : "/oaones/WeCom/navbar.jsp");
    request.setAttribute("oaonesPBPlatform", oaonesPBFs ? "feishu" : (oaonesPBDd ? "dingtalk" : "wecom"));
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
%>

<%@ include file="/oaones/redirect.inc.jsp" %>
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
    // 从“规则列表”页带 cfg_id 跳转过来时，默认选中该租户独立配置
    String ruleInitCfgId = request.getParameter("cfg_id");
    ruleInitCfgId = ruleInitCfgId == null ? "" : ruleInitCfgId.trim().replaceAll("[^0-9]", "");
%>
<%@ include file="/oaones/assets.jsp" %>
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
    <title>默认绑定规则 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <%-- 深链初始配置对象：/oaones/userBind/userRule.jsp?platform=wecom&cfg_id=xxx --%>
    <script>window.OAONES_INIT_CFG_ID = "<%=ruleInitCfgId%>";</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<jsp:include page="<%= oaonesPBNavbarPath %>">
    <jsp:param name="active" value="userRule"/>
</jsp:include>
<div class="page">
    <jsp:include page="userSwitch.jsp">
        <jsp:param name="active" value="rule"/>
    </jsp:include>
    <div class="card" style="max-width:900px;">
        <div class="toolbar" style="justify-content:space-between;">
            <div>
                <h3 style="margin:2px 0;">默认绑定规则<span class="help-tip inline" id="ruleHelpTip"
                        data-help="ruleHelpText" role="button" tabindex="0"
                        title="查看页面说明" aria-label="查看页面说明">?</span></h3>
            </div>
            <button type="button" class="btn" id="saveRuleBtn">保存规则</button>
        </div>
        <div class="help-source" id="ruleHelpText">可维护“默认配置”和“租户独立配置”。租户未单独配置时，
            “绑定详情 → 默认批量绑定”自动使用默认配置。可勾选多个字段，
            同时勾选时按 <span class="rule">邮箱 → 手机号 → 姓名 → 工号</span> 的优先级
            依次为未绑定人员寻找唯一匹配；同一字段存在多条同名/同值候选时会跳过，避免误绑。
        </div>
        <div class="filter-bar">
            <span class="filter-label">配置对象：</span>
            <select id="scopeSelect" style="min-width:220px;">
                <option value="">默认配置（未单独配置的租户使用）</option>
            </select>
            <button type="button" class="btn small secondary" id="removeCfgBtn" style="display:none;"
                    title="删除该租户独立配置，保存后改用默认配置">删除租户配置，跟随默认</button>
        </div>
        <div class="rule-state" id="ruleState" style="margin-bottom:14px;">
            <span class="muted">加载中…</span>
        </div>
        <div class="form-row">
            <label>匹配字段<span class="help-tip inline" id="fieldHelpTip" data-help="fieldHelpText"
                    role="button" tabindex="0" title="查看匹配字段说明" aria-label="查看匹配字段说明">?</span></label>
            <div class="field rule-field-list">
                <label class="rule-check" id="checkEmail">
                    <input type="checkbox" value="email" id="ruleEmail">
                    <span>邮箱</span>
                </label>
                <label class="rule-check" id="checkMobile">
                    <input type="checkbox" value="mobile" id="ruleMobile">
                    <span>手机号</span>
                </label>
                <label class="rule-check" id="checkName">
                    <input type="checkbox" value="name" id="ruleName">
                    <span>姓名</span>
                </label>
                <label class="rule-check" id="checkWorkcode">
                    <input type="checkbox" value="workcode" id="ruleWorkcode">
                    <span>工号</span>
                </label>
            </div>
        </div>
        <div class="help-source" id="fieldHelpText"><div class="help-line">邮箱：OA 邮箱与<%= oaonesPBFull %>邮箱一致
            （自动忽略大小写与空格等格式差异）；</div>
            <div class="help-line">手机号：OA 手机号与<%= oaonesPBFull %>手机号一致
            （自动忽略区号/分隔符等格式差异）；</div>
            <div class="help-line">姓名：OA 姓名与<%= oaonesPBFull %>姓名完全一致；</div>
            <div class="help-line">工号：OA 工号与<%= oaonesPBFull %>成员 userid（或<%= oaonesPBShort %>侧工号）一致。</div></div>
    </div>
</div>

<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
var ruleLabels = { email: "邮箱", mobile: "手机号", name: "姓名", workcode: "工号" };
/** 匹配字段的展示与匹配优先级：邮箱 → 手机号 → 姓名 → 工号（与后端 OaonesRules 一致）。 */
var ruleFields = ["email", "mobile", "name", "workcode"];
var cfgRows = [];
var currentRuleData = null;

/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/js/toast.js 弹出。
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

function ruleText(rules) {
    var out = [];
    rules.forEach(function (r) { out.push(ruleLabels[r] || r); });
    return out.join("、");
}

function scopeCfgId() {
    return String(byId("scopeSelect").value == null ? "" : byId("scopeSelect").value);
}

function scopeCfgName() {
    var id = scopeCfgId();
    for (var i = 0; i < cfgRows.length; i++) {
        if (String(cfgRows[i].id) === id) { return cfgRows[i].corp_name || ""; }
    }
    return "";
}

function renderScopeSelect() {
    var html = '<option value="">默认配置（未单独配置的租户使用）</option>';
    cfgRows.forEach(function (c) {
        html += '<option value="' + esc(c.id) + '">' + esc(c.corp_name)
            + (String(c.sync_enable) === "1" ? "" : "（停用）") + "</option>";
    });
    byId("scopeSelect").innerHTML = html;
    // “规则列表”页“编辑”跳转过来的租户：仅当该租户在可见租户里才选中，否则退回默认配置
    var initCfgId = String(window.OAONES_INIT_CFG_ID || "");
    if (initCfgId) {
        var matched = false;
        for (var i = 0; i < cfgRows.length; i++) {
            if (String(cfgRows[i].id) === initCfgId) { matched = true; break; }
        }
        if (matched) { byId("scopeSelect").value = initCfgId; }
    }
}

function renderRuleState() {
    var rules = [];
    ruleFields.forEach(function (v) {
        var input = byId("rule" + v.charAt(0).toUpperCase() + v.slice(1));
        if (input && input.checked) { rules.push(v); }
    });
    var scope = scopeCfgId() ? "租户【" + scopeCfgName() + "】" : "默认配置";
    var effective = currentRuleData ? currentRuleData.source === "tenant" ? "独立配置" : "使用默认配置" : "";
    byId("ruleState").innerHTML = scope + "："
        + (rules.length
            ? "<b style='color:var(--oa-primary);'>" + esc(ruleText(rules)) + "</b>"
            : "<span class='muted'>暂未勾选字段</span>")
        + (effective && scopeCfgId() ? "<span class='muted'>（" + esc(effective) + "）</span>" : "");
}

function syncRuleLabels() {
    ruleFields.forEach(function (v) {
        var input = byId("rule" + v.charAt(0).toUpperCase() + v.slice(1));
        var label = byId("check" + v.charAt(0).toUpperCase() + v.slice(1));
        if (input && label) {
            label.classList.toggle("checked", input.checked);
        }
    });
    renderRuleState();
}

function applyRuleData(data) {
    currentRuleData = data || null;
    var cfgId = scopeCfgId();
    var rules = ruleArr(data ? data.rules : "");
    ruleFields.forEach(function (v) {
        var input = byId("rule" + v.charAt(0).toUpperCase() + v.slice(1));
        if (input) { input.checked = rules.indexOf(v) >= 0; }
    });
    syncRuleLabels();
    var isTenantConfigured = !!cfgId && !!data && data.source === "tenant";
    byId("removeCfgBtn").style.display = isTenantConfigured ? "" : "none";
}

function loadRule() {
    byId("ruleState").innerHTML = "<span class='muted'>加载中…</span>";
    Oaones.api("user.rule.get", { cfg_id: scopeCfgId() }, function (res) {
        if (res.code !== 0) {
            currentRuleData = null;
            byId("removeCfgBtn").style.display = "none";
            showToast("读取规则失败：" + res.msg, 2);
            return;
        }
        applyRuleData(res.data || null);
        if (!res.data) {
            byId("ruleState").innerHTML = scopeCfgId()
                ? "租户【" + esc(scopeCfgName()) + "】未单独配置，当前默认配置也为空"
                : "默认配置尚未设置";
        }
    });
}

function loadScopeOptions() {
    Oaones.api("cfg.list", {}, function (res) {
        if (res.code !== 0) {
            showToast("读取" + P_SHORT + "租户失败：" + res.msg, 2);
            cfgRows = [];
            renderScopeSelect();
            loadRule();
            return;
        }
        cfgRows = res.data || [];
        renderScopeSelect();
        loadRule();
    });
}

function saveRule() {
    var rules = [];
    ruleFields.forEach(function (v) {
        var input = byId("rule" + v.charAt(0).toUpperCase() + v.slice(1));
        if (input && input.checked) { rules.push(v); }
    });
    if (!rules.length) {
        showToast("请至少勾选一个匹配字段", 1);
        return;
    }
    var btn = byId("saveRuleBtn");
    btn.disabled = true;
    btn.textContent = "保存中…";
    Oaones.api("user.rule.save", {
        cfg_id: scopeCfgId(),
        rules: rules.join(",")
    }, function (res) {
        btn.disabled = false;
        btn.textContent = "保存规则";
        if (res.code !== 0) {
            showToast("保存失败：" + res.msg, 2);
            return;
        }
        loadRule();
        showToast(scopeCfgId() ? "租户【" + scopeCfgName() + "】默认绑定规则已保存"
            : "默认绑定规则已保存");
    });
}

function removeCfgRule() {
    var cfgId = scopeCfgId();
    if (!cfgId) { return; }
    OaonesToast.confirm("删除租户【" + scopeCfgName() + "】的独立规则后，将自动使用默认配置，确认删除？", function () {
        var btn = byId("removeCfgBtn");
        btn.disabled = true;
        Oaones.api("user.rule.delete", { cfg_id: cfgId }, function (res) {
            btn.disabled = false;
            if (res.code !== 0) {
                showToast("删除失败：" + res.msg, 2);
                return;
            }
            loadRule();
            showToast("已删除租户独立规则，该租户将使用默认配置");
        });
    }, { danger: true });
}

byId("scopeSelect").addEventListener("change", loadRule);
byId("saveRuleBtn").addEventListener("click", saveRule);
byId("removeCfgBtn").addEventListener("click", removeCfgRule);
ruleFields.forEach(function (v) {
    var input = byId("rule" + v.charAt(0).toUpperCase() + v.slice(1));
    if (input) {
        input.addEventListener("change", syncRuleLabels);
    }
});

loadScopeOptions();
</script>
</body>
</html>
