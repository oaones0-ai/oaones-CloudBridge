<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  租户二级菜单「应用绑定」的页主体（页壳 = appBind/DingTalk/appBind.jsp）
  内容就是原来的“应用绑定”功能：钉钉工作台应用（AgentId）→ 在钉钉工作台打开的 OA 页面，免登录进入。
  列表与新建/编辑弹框片段固定复用 appBind/DingTalk/appBindManagePanel.jsp（同一份实现，不复制第二份）；
  该片段需要宿主提供两份全局对象（与改造前 appBind.jsp 页壳提供的完全一致）：
    state = { cfgs, cfgId, apps, entryBase, appKeyword, saving } 与 loadData()，
  本片段负责提供它们（「应用绑定」页即该片段在此页的宿主），见 skill.md 第一节第 14 / 16 条。
--%>
<%
    // 静态说明按登录人角色分别输出（系统管理员 / 其他维护人，见 skill.md 第一节第 4 条）：
    // 角色由页壳用 <jsp:param name="sysAdmin"> 传入，片段内不再另写角色判断。
    boolean oaonesAppBindSysAdmin = "true".equals(request.getParameter("sysAdmin"));
%>
<div class="help-source" id="appBindHelpText">本页维护
    <span class="rule">钉钉工作台应用 → OA 页面</span> 的绑定：
    一条记录对应钉钉后台的一个<span class="rule">自建应用</span>（AgentId）。
    员工在钉钉工作台点开该应用时，系统先按钉钉网页授权拿到成员身份，
    再按“人员绑定”里已建立的绑定关系找到他的 OA 账号，<span class="rule">免登录</span>进入本记录配置的 OA 页面，
    OA 里不需要再输一次账号密码。
    <span class="rule">支持多应用绑定</span>：同一个钉钉租户可以绑定多个应用（不同 AgentId 各一条记录），
    每个应用可分别设置自己的 PC 端与手机端 OA 页面地址。
    配置步骤：① 先在钉钉后台“应用管理 → 自建”里创建应用，
    把“应用主页”设为本页每行给出的<span class="rule">应用主页地址</span>（点“复制”整段粘贴），
    并把本 OA 的域名配置为该应用的<span class="rule">可信域名/网页授权域名</span>；
    ② 回到本页“新建应用绑定”，填写该应用的 AgentId、应用名称、应用 Secret
    （留空则用租户配置里的 Secret）与在钉钉工作台打开的 OA 页面地址；
    ③ 员工需先在“个人账号中心”用本人钉钉扫码绑定 OA 账号，
    否则从工作台打开时会提示“还没有绑定 OA 账号”。
    <span class="rule">PC端OA页面地址是必填项</span>，手机端地址可留空（留空时手机端也用 PC 端地址）；
    客户端类型由钉钉客户端的 User-Agent 自动识别，也可在入口地址后加
    <span class="rule">device=pc</span> / <span class="rule">device=mobile</span> 强制指定。
    停用某条绑定后，员工从钉钉工作台打开该应用会看到“绑定已停用”的提示，不会免登录进入 OA。
    <b>从“钉钉租户”列表点开某个租户后进入本页时，当前租户锁定为该租户</b>：
    工具栏的“钉钉租户”只读展示、不能切换租户（要换租户请回“钉钉租户”列表重新点开另一个租户）。
    <% if (oaonesAppBindSysAdmin) { %>你是<b>系统管理员</b>（user.id=1），没有租户上下文时本页的“钉钉租户”选择框列出<b>全部</b>钉钉租户
    （含未指定维护人的租户）；维护人可在“钉钉租户配置”里指定。
    <% } else { %>本页按登录人可见范围展示：没有租户上下文时只列出你被指定为维护人的钉钉租户，
    未指定维护人的租户不会出现在这里（需要维护时请联系管理员把你加入该租户的维护人）。
    <% } %></div>

<div class="toolbar">
    <label>钉钉租户</label>
    <select id="appBindCfgFilter" name="oaonesAppBindCfgFilter" style="min-width:200px;"></select>
    <%-- 带 cfg_id 从某个钉钉租户点进来时租户锁定：这里只读展示当前租户，不提供切换（见 skill.md 第一节第 15 条） --%>
    <span class="cfg-locked" id="appBindCfgLocked" style="display:none;"
          title="当前租户由“钉钉租户”列表点开决定，本页不能切换租户"></span>
    <span class="help-tip" id="appBindHelpTip" data-help="appBindHelpText" role="button" tabindex="0"
          title="查看页面说明" aria-label="查看页面说明">?</span>
    <span style="flex:1;"></span>
    <button class="btn" id="appBindNewBtn" title="为当前钉钉租户新增一个工作台应用绑定（同一租户可绑定多个应用）">新建应用绑定</button>
    <button class="btn secondary" id="appBindRefreshBtn">刷新</button>
</div>
<p class="notice" id="appBindPermTip" style="display:none;"></p>

<%-- 应用绑定主体（列表 + 新建/编辑弹框）：唯一实现，跨目录用绝对路径引入 --%>
<jsp:include page="/oaones/appsyn/appBind/DingTalk/appBindManagePanel.jsp"/>

<script>
"use strict";
/* 片段脚本只声明函数，不在解析期访问 DOM；事件注册放在 window.AppBindPanel.load() 里（见 skill.md 第一节第 7 条）。 */
function appBindById(id) { return document.getElementById(id); }
function appBindToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode, duration: duration }); }
}

/**
 * 当前租户与应用列表：appBindManagePanel.jsp 的契约（与改造前 appBind.jsp 页壳同名同结构），
 * 由本片段提供，列表与弹框片段直接读写它。
 */
var state = {
    cfgs: [],
    /** 当前租户初值：地址带入的 cfg_id（从租户工作区各页进入时）优先，取不到时回退第一个可维护租户 */
    cfgId: String(window.OAONES_CFG_ID || ""),
    apps: [],
    /** 应用主页地址前缀（协议+域名+端口 + 入口页路径），由后端按当前访问地址计算 */
    entryBase: "",
    appKeyword: "",
    saving: false
};
var appBindLoadSeq = 0;
var appBindBound = false;

/** 租户下拉：只列本人可维护的租户（cfg.list 已按维护人过滤），默认选中第一个。 */
function renderCfgOptions() {
    var select = appBindById("appBindCfgFilter");
    if (!select) { return; }
    var locked = Oaones.cfgLocked();
    // 租户上下文（带 cfg_id 从某个租户点“应用绑定”进来）：隐藏下拉，改为只读展示（不能切换租户）
    select.style.display = locked ? "none" : "";
    var lockedEl = appBindById("appBindCfgLocked");
    lockedEl.style.display = locked ? "" : "none";
    if (locked) {
        lockedEl.innerHTML = "<b>" + Oaones.esc(Oaones.lockedCfgText(lockedCfgLabel())) + "</b>";
    }
    select.innerHTML = Oaones.cfgSelectRows(state.cfgs);
    // api.js 的 cfgSelectRows 首项是“全部钉钉租户”，本页必须选定一个租户才列应用，因此去掉它
    if (select.options.length && select.options[0].value === "") {
        select.remove(0);
    }
    select.value = state.cfgId;
    var hasCfg = Boolean(state.cfgId);
    appBindById("appBindNewBtn").disabled = !hasCfg;
    appBindById("appBindNewBtn").title = hasCfg
        ? "为当前钉钉租户新增一个工作台应用绑定（同一租户可绑定多个应用）"
        : "请先选择钉钉租户（或先到“钉钉租户配置”页新建租户）";
}

/** 当前锁定租户的名称（取不到时为空，展示时退化为 #租户ID） */
function lockedCfgLabel() {
    for (var i = 0; i < state.cfgs.length; i++) {
        if (String(state.cfgs[i].id) === String(state.cfgId)) { return state.cfgs[i].corp_name || ""; }
    }
    return "";
}

/** 本页没有任何可用租户时，按登录人角色提示原因（超管=还没建租户；其他=不是任何租户的维护人）。 */
function applyCfgPermTip(cfgs) {
    var tip = appBindById("appBindPermTip");
    if (!tip) { return; }
    // 租户上下文（锁定租户）但该租户不在本人可见范围内：只给提示，本页不能切换租户
    if (Oaones.cfgLocked() && !lockedCfgLabel()) {
        tip.textContent = "当前租户（#" + Oaones.lockedCfgId() + "）不在你可以维护的钉钉租户范围内，本页不能切换租户；"
            + "请联系管理员把你加入该租户的维护人，或回“钉钉租户”列表重新点开另一个租户。";
        tip.style.display = "";
        return;
    }
    if ((cfgs || []).length) {
        tip.style.display = "none";
        tip.textContent = "";
        return;
    }
    tip.textContent = OaonesTips.pick(
        "当前还没有任何钉钉租户，请先到“钉钉租户配置”页新建租户，再回到本页维护应用绑定。",
        "当前登录人不是任何钉钉租户的维护人，暂无可以维护的应用绑定；"
            + "请联系管理员在“钉钉租户配置”页把本人加入该租户的维护人。");
    tip.style.display = "";
}

/**
 * 当前租户初值：地址带入的 cfg_id（从租户工作区点“应用绑定”进来时）在本人可维护租户里就用它，
 * 否则回退到第一个租户（没有可维护租户时为空）。
 */
function resolveCfgId(cfgs, wanted) {
    // 租户上下文：锁定地址上的租户，不切换、也不回退到其它租户
    if (Oaones.cfgLocked()) { return Oaones.lockedCfgId(); }
    var id = String(wanted == null ? "" : wanted);
    if (id) {
        for (var i = 0; i < cfgs.length; i++) {
            if (String(cfgs[i].id) === id) { return id; }
        }
    }
    return cfgs.length ? String(cfgs[0].id) : "";
}

function loadCfgs() {
    Oaones.api("cfg.list", {}, function (res) {
        if (res.code !== 0) { appBindToast(res.msg, 2); return; }
        state.cfgs = res.data || [];
        state.cfgId = resolveCfgId(state.cfgs, state.cfgId);
        applyCfgPermTip(state.cfgs);
        renderCfgOptions();
        loadData();
    });
}

/**
 * 当前租户的应用绑定（app.list）：由 appBindManagePanel.jsp 在保存/启停/删除后回调。
 * 与“消息设置 → 推送配置”读的是同一张表（uf_oaones_appsyn_app_bind），但两边是各自独立的页面：
 * 本页改完数据后，对方页面下次打开始终重新取数，不需要跨页通知。
 */
function loadData() {
    if (!state.cfgId) {
        state.apps = [];
        state.entryBase = "";
        renderAppManage();
        return;
    }
    appBindLoadSeq++;
    var seq = appBindLoadSeq;
    Oaones.api("app.list", { cfg_id: state.cfgId, keyword: "" }, function (res) {
        if (seq !== appBindLoadSeq) { return; }
        if (res.code !== 0) { appBindToast(res.msg, 2); return; }
        state.apps = (res.data && res.data.binds) || [];
        state.entryBase = (res.data && res.data.entry_base) || "";
        renderAppManage();
    });
}

function onAppBindCfgChange() {
    // 租户上下文里下拉是隐藏的（租户锁定），这里再兜一层，避免脚本/键盘误触发切换租户
    if (Oaones.cfgLocked()) { appBindById("appBindCfgFilter").value = state.cfgId; return; }
    state.cfgId = appBindById("appBindCfgFilter").value;
    state.appKeyword = "";
    var kw = appBindById("appKeyword");
    if (kw) { kw.value = ""; }
    loadData();
}

function bindAppBindEvents() {
    if (appBindBound) { return; }
    appBindBound = true;
    appBindById("appBindCfgFilter").addEventListener("change", onAppBindCfgChange);
    appBindById("appBindNewBtn").addEventListener("click", function () { openAppEdit(null); });
    appBindById("appBindRefreshBtn").addEventListener("click", function () { loadCfgs(); });
}

window.AppBindPanel = {
    /** 页面加载（页壳在打开/刷新时调用，重复调用必须幂等） */
    load: function () {
        bindAppBindEvents();
        loadCfgs();
    }
};
</script>
