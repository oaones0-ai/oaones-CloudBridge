<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 个人账号中心（合并页：/oaones/accountCenter/MyAccount.jsp）
     *
     * 与 accountCenter/{WeCom,DingTalk,Feishu}/accountCenter.jsp 是同一件事，
     * 区别只在于**三个平台合到一页**：
     *   ① 顶部固定显示“个人信息”（头像 + 本人 OA 账号明细，不再单独占一个页签）；
     *   ② 第一个页签是“我的账户（主账户 / 子账户）”（本人所属的主账户与全部子账户）；
     *   ③ 其后是企微 / 钉钉 / 飞书三个平台页签，各放一份“本人绑定列表”与扫码弹框。
     * 员工一个页面就能看完并维护自己在三个平台的绑定，不需要分别打开三个入口。
     *
     * 平台隔离：三个平台各走自己的接口入口（企微 /oaones/WeCom/api.jsp、钉钉 /oaones/DingTalk/api.jsp、
     * 飞书 /oaones/Feishu/api.jsp，见 js/api.js#apiAt），扫码回调按“当前所在页签”路由到对应片段，
     * 因此合并到一页不会把 A 平台的凭证/身份用到 B 平台上。
     *
     * 列表本身由**同一个片段** accountPlatformPanel.jsp 渲染（<jsp:include> 传 p 参数），
     * 片段里按平台区分元素 id 前缀、分页与列宽 key，所以同页放三份互不干扰。
     *
     * 权限：与个人账号中心同口径——面向每一个登录人本人，不按租户维护人分权，
     * 服务端（account.info / account.wxList / account.wxQrStart / account.wxUnbind）
     * 只读写登录会话本人的数据。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    // 可选 ?tab=family|wecom|dingtalk|feishu：退休的老地址（accountCenter/{WeCom,DingTalk,Feishu}/
    // accountCenter.jsp）302 到本页时会带上 ?tab=<平台>，用户落到合并页后仍停在原来那个平台；
    // 老的 ?tab=info（原“账号信息”页签，现在信息固定在顶部）兼容到第一个页签。
    // 不传或认不出来时默认第一个页签“我的账户”。
    String acTab = OaonesUtil.s(request.getParameter("tab")).trim().toLowerCase();
    if ("info".equals(acTab)) {
        acTab = "family";
    }
    if (!"wecom".equals(acTab) && !"dingtalk".equals(acTab) && !"feishu".equals(acTab)
            && !"personalwx".equals(acTab)) {
        acTab = "family";
    }
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>个人账号中心 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 合并页是独立页壳：不引用模块导航（navbar.jsp），
     不给页面标题与页签加感叹号说明（个人自助页保持干净），入口单独配 E9 菜单即可。 --%>
<div class="page">
    <div class="card">
        <div class="toolbar">
            <label class="account-title">个人账号中心</label>
            <span class="muted" id="accountPlatformHint">个人信息固定在顶部；我的账户与三个平台的本人绑定按页签查看</span>
            <span style="flex:1;"></span>
            <button class="btn secondary" id="accountRefreshBtn">刷新</button>
        </div>
        <p class="notice" id="accountPermTip" style="display:none;"></p>

        <%-- 个人信息（固定在顶部，不占页签）：头像 + 本人 OA 账号明细（账号 / 账户类型 /
             主账户 / 子账户 / 登录名 / 手机号 / 员工编号 / 邮箱 / 所属部门 / 所属分部 / 账号状态），
             字段与渲染都在公共片段 accountInfoPanel.jsp 里（原先单独一个“账号信息”页签与这里重复，
             现在只保留顶部这一处） --%>
        <div class="account-card" id="accountCard">
            <div id="accountAvatarBox" class="account-avatar-box"></div>
            <div class="account-main">
                <div class="toolbar" style="margin:0 0 6px;">
                    <label>个人信息</label>
                </div>
                <jsp:include page="accountInfoPanel.jsp"/>
            </div>
        </div>

        <%-- 页签：第一个固定为“我的账户（主账户 / 子账户）”，其后是企微 / 钉钉 / 飞书三个平台的本人绑定。
             平台页签始终保留；当前平台没有可用租户时，在面板内显示空状态，避免点击后页签突然消失。 --%>
        <div class="sub-tabs" id="accountTabs">
            <button type="button" class="<%= "family".equals(acTab) ? "active" : "" %>"
                    data-tab="family">我的账户（主账户 / 子账户）</button>
            <button type="button" class="<%= "wecom".equals(acTab) ? "active" : "" %>"
                    data-tab="wecom">企业微信</button>
            <button type="button" class="<%= "dingtalk".equals(acTab) ? "active" : "" %>"
                    data-tab="dingtalk">钉钉</button>
            <button type="button" class="<%= "feishu".equals(acTab) ? "active" : "" %>"
                    data-tab="feishu">飞书</button>
            <%-- 个人微信：与三个平台并列的第四个页签。它没有“租户”概念（一人一条绑定），
                 未绑定、甚至还没维护授权密钥时页签也必须显示，否则员工没有入口去绑定。 --%>
            <button type="button" class="<%= "personalwx".equals(acTab) ? "active" : "" %>"
                    data-tab="personalwx">个人微信</button>
        </div>

        <div class="tab-panel<%= "family".equals(acTab) ? " active" : "" %>" id="tabPanelFamily"
             data-tab-panel="family">
            <%-- 我的账户：本人所属的主账户与全部子账户（E9 标准 hrmresource.accounttype/belongto），
                 一行一个账号、行首标出「主账户 / 子账户」，本人行加底色与「本人」标记 --%>
            <div class="toolbar">
                <label>我的账户（主账户 / 子账户）</label>
                <span style="flex:1;"></span>
                <span class="account-family-hint" id="accountFamilyHint"></span>
            </div>
            <div class="table-wrap">
                <table class="grid" id="accountFamilyTable">
                    <thead>
                    <tr>
                        <th style="width:130px;">账户类型</th>
                        <th style="width:18%;">账号</th>
                        <th style="width:13%;">登录名</th>
                        <th style="width:13%;">手机号</th>
                        <th style="width:11%;">员工编号</th>
                        <th>所属部门</th>
                        <th style="width:9%;">账号状态</th>
                    </tr>
                    </thead>
                    <tbody id="accountFamilyBody"></tbody>
                </table>
            </div>
        </div>

        <div class="tab-panel<%= "wecom".equals(acTab) ? " active" : "" %>" id="tabPanelWecom"
             data-tab-panel="wecom">
            <%-- 企业微信 tab：本人企微绑定列表 + 扫码新增绑定（企微接口入口） --%>
            <jsp:include page="accountPlatformPanel.jsp">
                <jsp:param name="p" value="wecom"/>
            </jsp:include>
        </div>
        <div class="tab-panel<%= "dingtalk".equals(acTab) ? " active" : "" %>" id="tabPanelDingtalk"
             data-tab-panel="dingtalk">
            <%-- 钉钉 tab：本人钉钉绑定列表 + 扫码新增绑定（钉钉接口入口） --%>
            <jsp:include page="accountPlatformPanel.jsp">
                <jsp:param name="p" value="dingtalk"/>
            </jsp:include>
        </div>
        <div class="tab-panel<%= "feishu".equals(acTab) ? " active" : "" %>" id="tabPanelFeishu"
             data-tab-panel="feishu">
            <%-- 飞书 tab：本人飞书绑定列表 + 扫码新增绑定（飞书接口入口） --%>
            <jsp:include page="accountPlatformPanel.jsp">
                <jsp:param name="p" value="feishu"/>
            </jsp:include>
        </div>
        <div class="tab-panel<%= "personalwx".equals(acTab) ? " active" : "" %>" id="tabPanelPersonalWx"
             data-tab-panel="personalwx">
            <%-- 个人微信 tab：本人绑定状态 + 扫码绑定 / 换绑 / 解绑（本人自助，接口入口 /oaones/WeCom/api.jsp 的 pw.*） --%>
            <jsp:include page="accountPersonalWxPanel.jsp"/>
        </div>
    </div>
</div>

<%-- 页壳自己的接口封装（Oaones / Oaones.apiAt）在公共脚本里，必须在页壳脚本与片段渲染之前引：
     片段本身的脚本只做注册，真正调用 Oaones.* 是在取数之后渲染时，因此放在这里与其它页面同一口径。
     漏引这一个文件会让本页取数直接报错（列表空白），改动本页时不要把它删掉。 --%>
<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
/** 三个平台各自的接口入口（未登录也能用的统一入口，见 js/api.js#apiAt） */
var API = {
    wecom: "/oaones/WeCom/api.jsp",
    dingtalk: "/oaones/DingTalk/api.jsp",
    feishu: "/oaones/Feishu/api.jsp"
};
/** 平台顺序（与页签顺序一致；第一个是默认页签） */
var PLATFORMS = ["wecom", "dingtalk", "feishu"];
var PLATFORM_LABEL = { wecom: "企业微信", dingtalk: "钉钉", feishu: "飞书" };
/** 个人微信页签标识：它不是“租户型”平台（一人一条绑定），因此不在 PLATFORMS 里，取数单独走 */
var PW_TAB = "personalwx";
/** 页壳状态：本人账号信息 + 各平台绑定列表是否已取过（切页签时按需取，刷新时一并刷新） */
var acctState = {
    info: null,
    /** 该平台的绑定列表是否已取回过（用于判断页签要不要重画） */
    loaded: { wecom: false, dingtalk: false, feishu: false, personalwx: false },
    /** 该平台是否已经发过取数请求（避免切页签与首屏加载重复请求） */
    requested: { wecom: false, dingtalk: false, feishu: false, personalwx: false }
};
/** 初始页签：老地址 302 过来时按 ?tab= 指定，默认企业微信（与页面上 active 的页签一致） */
var INIT_TAB = "<%=acTab%>";
var loadSeq = 0;

function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/js/toast.js 弹出。
 */
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}
/** 已注册的平台片段（accountPlatformPanel.jsp 每个实例注册一份） */
function panels() { return window.OaonesAccountPanels || {}; }

/* ------------------------------------------------------------------
 * 数据加载：账号信息（account.info，三个平台口径一致，取一个入口即可）
 *          + 每个平台的本人绑定列表（account.wxList，各走各的接口入口）
 * 本页所有接口都只返回“登录人本人”的数据，页壳不需要传任何人ID。
 * ------------------------------------------------------------------ */
function loadAccount() {
    loadSeq++;
    var seq = loadSeq;
    Oaones.apiAt(API.wecom, "account.info", {}, function (res) {
        if (seq !== loadSeq) { return; }
        if (res.code !== 0) {
            showToast(res.msg, 2);
            byId("accountPermTip").textContent = res.msg || "账号信息读取失败";
            byId("accountPermTip").style.display = "";
            return;
        }
        acctState.info = res.data || {};
        byId("accountPermTip").style.display = "none";
        // 顶部“个人信息”：头像 + 字段（字段与渲染都在 accountInfoPanel.jsp 片段里）
        renderAccountAvatar(acctState.info);
        if (typeof renderAccountInfoPanel === "function") { renderAccountInfoPanel(acctState.info); }
        // 第一个页签“我的账户（主账户 / 子账户）”
        renderAccountFamily();
        // 绑定列表：三个平台都取（每个平台一次轻量查询+绑定查询）。
        // 页签始终保留，空平台由面板自己的空状态说明；“刷新”按钮走的也是这里，会一起更新三家。
        PLATFORMS.forEach(function (p) { loadBindings(p); });
        loadPersonalWx();
    });
}

/** 读取某个平台的“本人绑定”列表（失败只提示，不影响其它平台与本页其它区域） */
function loadBindings(platform) {
    var p = String(platform || "");
    if (!API[p]) { return; }
    var seq = loadSeq;
    acctState.requested[p] = true;
    Oaones.apiAt(API[p], "account.wxList", {}, function (res) {
        if (seq !== loadSeq) { return; }
        if (res.code !== 0) {
            showToast(res.msg || (PLATFORM_LABEL[p] + "绑定列表读取失败"), 2);
            return;
        }
        var data = res.data || {};
        var tenants = data.tenants || [];
        acctState.loaded[p] = true;
        var panel = panels()[p];
        if (panel) { panel.render(tenants, data.bound_count || 0, data.tenant_count); }
        // 表格刚可见时要重画一次：列宽拖拽在 display:none 时量不到宽度
        if (panel && accountTab() === p) { panel.onShow(); }
    });
}

/**
 * 「个人微信」页签：本人的绑定状态 + 扫码绑定 / 换绑 / 解绑（pw.mine，一行本人绑定）。
 *
 * 个人微信与企微 / 钉钉 / 飞书并列，但没有“企业 / 租户”概念：整套系统只有一份授权密钥
 * （由管理员在「微信设置」页维护）、每个 OA 人员最多一条绑定，因此这里不走
 * loadBindings（那是“一行一个租户”的清单），页签始终显示。
 */
function loadPersonalWx() {
    var seq = loadSeq;
    acctState.requested[PW_TAB] = true;
    // 个人微信的 pw.* 接口统一由企微连接器的接口入口路由（见 controller.wecom.OaonesController）
    Oaones.apiAt(API.wecom, "pw.mine", {}, function (res) {
        if (seq !== loadSeq) { return; }
        if (res.code !== 0) {
            showToast(res.msg || "个人微信绑定读取失败", 2);
            return;
        }
        acctState.loaded[PW_TAB] = true;
        var panel = panels()[PW_TAB];
        if (panel) { panel.render(res.data || {}); }
        if (panel && accountTab() === PW_TAB) { panel.onShow(); }
    });
}

/* ------------------------------------------------------------------
 * 账号卡片 / 我的账户（与三个连接器的老账号中心页同一套口径）
 * ------------------------------------------------------------------ */
/**
 * 账户类型标签：E9 标准 hrmresource.accounttype（0 主账号=主账户 / 1 次账号=子账户）。
 * 主账户与子账户必须一眼可辨（顶部“个人信息”与“我的账户”页签都用这套口径）。
 */
function accountTypeTagHtml(type, text) {
    var isSub = String(type == null ? "" : type) === "1";
    return '<span class="tag ' + (isSub ? "acct-sub" : "acct-main") + '">'
        + esc(text || (isSub ? "子账户" : "主账户")) + '</span>';
}

/** 单元格兜底：无值时显示“—”，避免出现空白格 */
function accountCellText(value) {
    var text = String(value == null ? "" : value);
    return text.length ? esc(text) : '<span class="muted">—</span>';
}

/**
 * 本人所属的账号集合（主账户在前、子账户在后）。
 *
 * <p>取 account.info.accounts（后端按 hrmresource.belongto 一次性下发）；
 * 后端未下发该字段时（老版本）退化为只显示本人一行，页面结构保持可用。</p>
 */
function accountFamilyRows(info) {
    var list = info.accounts;
    if (list && list.length) { return list; }
    return [{
        oa_user_id: info.oa_user_id,
        name: info.name,
        loginid: info.loginid,
        mobile: info.mobile,
        workcode: info.workcode,
        oa_dept_id: info.oa_dept_id,
        oa_dept_name: info.oa_dept_name,
        oa_status_text: info.oa_status_text,
        account_type: info.account_type,
        account_type_text: info.account_type_text,
        is_self: true,
        is_main: String(info.account_type == null ? "0" : info.account_type) !== "1"
    }];
}

/**
 * “我的账户”列表：一行一个账号，标出「主账户 / 子账户」；
 * 本人所在行加底色并标「本人」——本人是子账户时，同时列出归属的主账户与其全部子账户。
 */
function renderAccountFamily() {
    var info = acctState.info || {};
    var rows = accountFamilyRows(info);
    var isSub = String(info.account_type == null ? "" : info.account_type) === "1";
    var subCount = 0;
    var html = "";
    rows.forEach(function (row) {
        var rowIsSub = String(row.account_type == null ? "" : row.account_type) === "1";
        if (rowIsSub) { subCount++; }
        var isSelf = row.is_self === true;
        html += '<tr' + (isSelf ? ' class="account-self-row"' : "") + '>'
            + '<td>' + accountTypeTagHtml(row.account_type, row.account_type_text)
            + (isSelf ? '<span class="account-self-tag">本人</span>' : '') + '</td>'
            + '<td><b>' + OaonesHrm.user(row.oa_user_id, row.name) + '</b></td>'
            + '<td>' + accountCellText(row.loginid) + '</td>'
            + '<td>' + accountCellText(row.mobile) + '</td>'
            + '<td>' + accountCellText(row.workcode) + '</td>'
            + '<td>' + (row.oa_dept_name
                ? OaonesHrm.dept(row.oa_dept_id, row.oa_dept_name)
                : '<span class="muted">未维护</span>') + '</td>'
            + '<td>' + accountCellText(row.oa_status_text) + '</td>'
            + '</tr>';
    });
    byId("accountFamilyBody").innerHTML = html;
    byId("accountFamilyHint").textContent = isSub
        ? "你登录的是子账户：上表同时列出归属的主账户与全部子账户。"
        : (subCount > 0
            ? "你登录的是主账户：上表列出本人与其 " + subCount + " 个子账户。"
            : "你登录的是主账户，当前没有子账户。");
}

/** 头像：OA 未设置头像（resourceimageid 为空）时用姓名首字兜底，不留空白 */
function renderAccountAvatar(info) {
    var name = String(info.name == null ? "" : info.name);
    var box = byId("accountAvatarBox");
    if (info.avatar_url) {
        box.innerHTML = '<img class="account-avatar" alt="' + esc(name) + '的头像" src="'
            + esc(info.avatar_url) + '">';
        return;
    }
    box.innerHTML = '<div class="account-avatar-fallback" title="未设置头像">'
        + esc(name.length > 0 ? name.substring(0, 1) : "?") + '</div>';
}

/* ------------------------------------------------------------------
 * tab 切换：第一个是“我的账户（主账户 / 子账户）”，其后是企微 / 钉钉 / 飞书三个平台页签
 * ------------------------------------------------------------------ */
function accountTab() {
    var btns = document.querySelectorAll("#accountTabs button[data-tab]");
    for (var i = 0; i < btns.length; i++) {
        if (btns[i].classList.contains("active")) { return btns[i].getAttribute("data-tab"); }
    }
    return "family";
}

function switchAccountTab(tab) {
    var btns = document.querySelectorAll("#accountTabs button[data-tab]");
    for (var i = 0; i < btns.length; i++) {
        btns[i].classList.toggle("active", btns[i].getAttribute("data-tab") === tab);
    }
    var panelsEls = document.querySelectorAll("[data-tab-panel]");
    for (var j = 0; j < panelsEls.length; j++) {
        panelsEls[j].classList.toggle("active", panelsEls[j].getAttribute("data-tab-panel") === tab);
    }
    var panel = panels()[tab];
    if (!panel) { return; }
    // 个人微信页签：一人一条绑定，取数走 pw.mine（不是“一行一个租户”的清单）
    if (PW_TAB === tab) {
        if (!acctState.requested[PW_TAB]) { loadPersonalWx(); return; }
        if (acctState.loaded[PW_TAB]) { panel.onShow(); }
        return;
    }
    // 平台页签：还没发过请求就现取（首屏已统一取过三家，正常不会走到这里）；
    // 已取到数据就重画一次——表格从 display:none 变可见后，列宽拖拽才能初始化
    if (!acctState.requested[tab]) { loadBindings(tab); return; }
    if (acctState.loaded[tab]) { panel.onShow(); }
}

/**
 * 扫码回调：弹框内嵌平台官方二维码，扫码成功后 iframe 会跳到该平台的回调页
 * （WeComCallback / DingTalkCallback / FeishuCallback），回调页调用父窗口的本函数。
 * 弹框只会开在当前所在平台页签上，因此按当前页签把结果转给对应片段即可。
 */
window.OaonesWxScanDone = function (state, res) {
    var panel = panels()[accountTab()];
    if (panel) { panel.onScanDone(state, res); }
};

/** 片段的“重新加载本页签”入口（片段不反向引用页壳脚本） */
window.OaonesAccount = {
    reload: function (p) {
        // 个人微信页签走自己的取数（pw.mine：一行本人绑定），不是平台租户清单
        if (String(p == null ? "" : p) === PW_TAB) { loadPersonalWx(); return; }
        loadBindings(p);
    }
};

/* 事件注册与初始化（片段脚本先执行，这里在页面末尾统一注册） */
byId("accountRefreshBtn").addEventListener("click", function () { loadAccount(); });
byId("accountTabs").addEventListener("click", function (e) {
    var btn = e.target && e.target.closest ? e.target.closest("button[data-tab]") : null;
    if (!btn) { return; }
    switchAccountTab(btn.getAttribute("data-tab"));
});
// 公共脚本（js/api.js）没加载时不再静默空白：给出明确提示（历史上本页漏引过 api.js，列表整页无数据）
if (!window.Oaones || typeof window.Oaones.apiAt !== "function") {
    var acTip = byId("accountPermTip");
    acTip.textContent = "页面公共脚本 js/api.js 未加载，个人信息、我的账户与三个平台的绑定列表都无法读取；"
        + "请刷新页面，若仍然如此请联系管理员检查 /oaones/js/api.js。";
    acTip.style.display = "";
} else {
    loadAccount();
    // 初始页签不是第一个页签（我的账户）时切过去：平台页签的列表首屏已统一取过，切过去只重画
    if (INIT_TAB !== "family") { switchAccountTab(INIT_TAB); }
}
</script>
</body>
</html>
