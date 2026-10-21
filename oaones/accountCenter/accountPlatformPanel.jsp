<%@ page pageEncoding="UTF-8" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%--
  个人账号中心 - “本人租户绑定”列表片段（**企微 / 钉钉 / 飞书三个连接器共用一份实现**；
  同一个页面里可以放多份：每份的 id 前缀、JS 状态、分页与列宽 key 都按平台区分，互不干扰）。

  由包含页用动态 include 指定平台（p 取 wecom / dingtalk / feishu，认不出来的值一律按企微处理）：

    <jsp:include page="/oaones/accountCenter/accountPlatformPanel.jsp">
        <jsp:param name="p" value="wecom"/>
    </jsp:include>

  合并页 /oaones/accountCenter/MyAccount.jsp 一次放三份（企业微信 / 钉钉 / 飞书），
  每份都自带列表与“扫码新增/重绑”弹框；每个平台的请求按自己的接口入口走：
  企微 /oaones/WeCom/api.jsp、钉钉 /oaones/DingTalk/api.jsp、飞书 /oaones/Feishu/api.jsp
  （见 js/api.js 的 Oaones.apiAt(base, action, params, cb)）。

  片段对外只暴露 window.OaonesAccountPanels[平台]（render / onShow / onScanDone /
  isModalOpen / closeModal / platform），取数与扫码回调路由由页壳负责（见 MyAccount.jsp），
  片段不反向依赖页壳脚本。
--%>
<%
    // 平台参数：wecom（默认）/ dingtalk / feishu
    String acpPlatform = OaonesUtil.s(request.getParameter("p")).trim().toLowerCase();
    if (!"dingtalk".equals(acpPlatform) && !"feishu".equals(acpPlatform)) {
        acpPlatform = "wecom";
    }
    String acpLabel = "wecom".equals(acpPlatform) ? "企业微信"
            : ("dingtalk".equals(acpPlatform) ? "钉钉" : "飞书");
    String acpShort = "wecom".equals(acpPlatform) ? "企微" : acpLabel;
    String acpApi = "wecom".equals(acpPlatform) ? "/oaones/WeCom/api.jsp"
            : ("dingtalk".equals(acpPlatform) ? "/oaones/DingTalk/api.jsp" : "/oaones/Feishu/api.jsp");
    // 平台类型编号（OaonesConst.TYPE_*：0 企微 / 1 钉钉 / 2 飞书）：会议 / 日程同步的个人开关按它区分平台
    int acpType = ("dingtalk".equals(acpPlatform) ? 1 : ("feishu".equals(acpPlatform) ? 2 : 0));
    // 元素 id 前缀：同一页面放多份时不会互相覆盖
    String acpId = "acp-" + acpPlatform + "-";
%>
        <div id="<%=acpId%>panel">
            <div class="toolbar">
                <label>我的<%=acpLabel%>绑定</label>
                <%-- 会议 / 日程同步频率（本人页展示；规则来自页面上的「计划任务」，见 uf_oaones_mtg_cron） --%>
                <span class="notice" id="<%=acpId%>pullTip" style="display:none;"></span>
            </div>
            <div class="bind-stats" id="<%=acpId%>stats"></div>
            <div class="table-wrap">
                <table class="grid" id="<%=acpId%>table">
                    <thead>
                    <tr>
                        <th style="width:11%;"><%=acpShort%>租户</th>
                        <th style="width:6%;">租户状态</th>
                        <th style="width:7%;">绑定状态</th>
                        <th style="width:9%;"><%=acpShort%>成员</th>
                        <th style="width:10%;"><%=acpShort%>成员ID</th>
                        <th style="width:9%;"><%=acpShort%>部门</th>
                        <th style="width:6%;">绑定方式</th>
                        <th style="width:8%;">绑定时间</th>
                        <th style="width:7%;">最近同步</th>
                        <%-- 会议 / 日程同步：租户级开关（后台）与本人开关（本页）合并展示；
                             只有租户已开启才允许本人设置“不同步到第三方平台”（见下方脚本） --%>
                        <th style="width:13%;">会议同步</th>
                        <th style="width:13%;">日程同步</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="<%=acpId%>body"></tbody>
                </table>
                <div class="empty" id="<%=acpId%>empty" style="display:none;"></div>
            </div>
            <div class="pager" id="<%=acpId%>pager"></div>
        </div>

<%-- 扫码绑定弹框：内嵌平台官方二维码，扫码成功后由同源回调页调用父窗口的
     OaonesWxScanDone 通知，页壳按当前所在平台转到本片段的 onScanDone 完成收尾。 --%>
<div class="modal-mask" id="<%=acpId%>qrMask"></div>
<div class="modal wx-qr-modal" id="<%=acpId%>qrModal">
    <div class="modal-head">
        <span id="<%=acpId%>qrTitle"><%=acpLabel%>扫码绑定</span>
        <button type="button" class="link" id="<%=acpId%>qrCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <p class="wx-qr-tip">请使用<span class="rule">本人的<%=acpLabel%></span>扫描下方二维码，并在手机上点击确认：
            确认后会把扫码得到的<%=acpShort%>成员绑定到<span class="rule">你的 OA 账号</span>（一次只影响你自己的账号）。
            同一个<%=acpShort%>租户下你只保留一条绑定，重复扫码即更换所绑定的<%=acpShort%>账号。</p>
        <div class="wx-qr-box" id="<%=acpId%>qrBox"></div>
        <p class="wx-qr-status" id="<%=acpId%>qrStatus"></p>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="<%=acpId%>qrWindowBtn"
                title="浏览器拦截内嵌二维码（第三方 Cookie）时改用新窗口扫码">在新窗口打开二维码</button>
        <button type="button" class="btn secondary" id="<%=acpId%>qrAgainBtn">重新生成二维码</button>
        <button type="button" class="btn" id="<%=acpId%>qrDoneBtn">我知道了</button>
    </div>
</div>

<%-- 同步方向设置弹框（会议 / 日程共用；每次打开时按业务 biz 决定标题与文案）：
     本人可选「遵循系统设置」（默认，用租户级方向）或「指定同步方向」（两个复选框，可双选，至少要选一个）。 --%>
<div class="modal-mask" id="<%=acpId%>dirMask"></div>
<div class="modal" id="<%=acpId%>dirModal">
    <div class="modal-head">
        <span id="<%=acpId%>dirTitle">同步方向</span>
        <button type="button" class="link" id="<%=acpId%>dirCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <p class="notice" id="<%=acpId%>dirTenantTip"></p>
        <div class="mtg-dir-form">
            <label class="radio-line">
                <input type="radio" id="<%=acpId%>dirModeFollow" name="<%=acpId%>dirMode" value="0">
                遵循系统设置
            </label>
            <div class="user-mini" id="<%=acpId%>dirSysText" style="margin:2px 0 8px 22px;"></div>
            <label class="radio-line">
                <input type="radio" id="<%=acpId%>dirModeCustom" name="<%=acpId%>dirMode" value="1">
                指定同步方向
            </label>
            <div id="<%=acpId%>dirCustom" style="margin:6px 0 0 22px;">
                <label class="mtg-dir-opt">
                    <input type="checkbox" id="<%=acpId%>dirOa2"> OA 同步到第三方
                </label>
                <label class="mtg-dir-opt">
                    <input type="checkbox" id="<%=acpId%>dirT2oa"> 第三方同步到 OA
                </label>
                <div class="user-mini" style="margin-top:6px;">
                    只能收窄租户允许的方向：个人勾选 <b>且</b> 系统设置为选中时，该方向才生效。
                </div>
            </div>
        </div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="<%=acpId%>dirCancelBtn">取消</button>
        <button type="button" class="btn" id="<%=acpId%>dirSaveBtn">保存</button>
    </div>
</div>

<script>
(function () {
"use strict";
/** 平台标识（wecom / dingtalk / feishu）、该平台的接口入口与文案短名 */
var P = "<%=acpPlatform%>";
var API = "<%=acpApi%>";
var SHORT = "<%=acpShort%>";
var ID = "<%=acpId%>";
/** 平台类型编号（OaonesConst.TYPE_*：0 企微 / 1 钉钉 / 2 飞书）：会议 / 日程同步的个人开关按它区分平台 */
var TYPE = <%=acpType%>;
/** 会议 / 日程同步的业务编号（OaonesConst.MTG_BIZ_*：0 会议 / 1 日程） */
var BIZ_MEETING = 0, BIZ_SCHEDULE = 1;

function byId(id) { return document.getElementById(ID + id); }
function esc(v) { return Oaones.esc(v); }
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

/** 本人在该平台各租户的绑定行（一行一个租户，含未绑定行） */
var tenantRows = [];
/**
 * 各租户的会议 / 日程同步状态（cfg_id -> { mtg_tenant, mtg_personal, sch_tenant, sch_personal }）：
 * 由 mtgsync.userStates 取回（本人自助口径）。租户开关只有“已开启”才算允许；
 * 个人开关默认允许（没落过行 = 1），只有显式点过“不同步”才是 0。
 */
var syncStates = {};
/** 同步状态是否读取失败（失败时两列显示“读取失败”，点“刷新”重试） */
var syncStatesError = false;
/** 当前弹框对应的租户ID（空 = 没开弹框） */
var qrCfgId = "";
/** 同步方向弹框对应的租户ID / 业务（空 = 没开弹框） */
var dirCfgId = "";
var dirBiz = BIZ_MEETING;
/** 二维码请求中标记：避免连点重复生成 */
var qrLoading = false;
/** 自行绑定（bind_source=2）的记录在“重新扫码（更换）/解绑”时的二次确认文案 */
var SELF_BIND_CONFIRM_MSG = "该数据为用户自行绑定，确定是否继续操作？";
/** 该行是否为本人扫码建立的“自行绑定”（bind_source=2） */
function isSelfBind(row) {
    return !!row && String(row.bind_source == null ? "" : row.bind_source) === "2";
}

/** “重新加载本平台列表”：由页壳注入（片段不反向依赖页壳脚本） */
function reload() {
    if (window.OaonesAccount && typeof window.OaonesAccount.reload === "function") {
        window.OaonesAccount.reload(P);
    }
}

/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js）：翻页后重绘当前列表 */
var pager = OaonesTable.pager({
    key: "accountCenter." + P,
    el: byId("pager"),
    onChange: function () { renderRows(); }
});

/** 页壳加载完本平台的 account.wxList 后调用 */
function render(tenants, boundCount, tenantCount) {
    tenantRows = tenants || [];
    pager.resetIfChanged(String(tenantRows.length) + "|" + (boundCount || 0));
    renderStats(boundCount, tenantCount);
    loadSyncStates();
    renderRows();
}

function renderStats(boundCount, tenantCount) {
    var total = tenantCount === undefined || tenantCount === null ? tenantRows.length : tenantCount;
    byId("stats").innerHTML = '<span class="bind-stat">已绑定租户 <b>' + (boundCount || 0)
        + '</b> 个</span><span class="bind-stat">租户合计 <b>' + total + '</b> 个</span>';
}

/**
 * 取本人视角的「会议 / 日程同步状态」（action mtgsync.userStates，本人自助，不分租户维护人）。
 * 成功后重画列表把两列状态补上；失败只把该列标成“读取失败”，不影响绑定列表本身
 * （点右上角“刷新”会重新取）。
 */
function loadSyncStates() {
    Oaones.apiAt(API, "mtgsync.userStates", { type: TYPE }, function (res) {
        if (!res || res.code !== 0) {
            syncStates = {};
            syncStatesError = true;
            renderRows();
            return;
        }
        syncStatesError = false;
        syncStates = (res.data && res.data.states) || {};
        // 同步频率规则：取服务端按页面「计划任务」实时生成的文案
        // （按时间段模式 = 各时间段 + 轮询间隔；高级模式 = 白天 / 夜间两档 cron；
        // 两种方式都带工作日 / 非工作日口径，两个方向同一频率）
        var rule = (res.data && res.data.pull_rule_text) || "";
        var tipEl = byId("pullTip");
        if (tipEl) {
            tipEl.textContent = rule;
            tipEl.style.display = rule ? "" : "none";
        }
        renderRows();
    });
}

/**
 * 「会议 / 日程同步」列：租户开关 + 本人开关。
 * - 租户未开启：本人开关不可设置（服务端也会拒绝），但仍显示本人已选状态
 *   （后台把租户关闭再开启不影响本人这条选择，页面保持“不同步”）；
 * - 租户已开启：显示“同步中 / 不同步”并提供“关闭同步 / 开启同步”按钮。
 */
function syncCell(cfgId, biz) {
    var st = syncStates[String(cfgId)];
    if (!st) {
        return '<span class="muted">' + (syncStatesError ? "读取失败" : "读取中…") + '</span>';
    }
    var p = biz === BIZ_MEETING ? "mtg" : "sch";
    var tenantOn = String(st[p + "_tenant"]) === "1";
    var personalOn = String(st[p + "_personal"]) !== "0";
    var word = biz === BIZ_MEETING ? "会议" : "日程";
    // 同步方向（租户方向是上限；个人「遵循系统设置」时生效方向 = 租户方向）
    var effText = st[p + "_eff_dir_text"] || "";
    var dirLine = '<div class="user-mini">方向：'
        + (String(st[p + "_dir_mode"]) === "1"
            ? ('指定（' + esc(effText) + '）') : ('遵循系统（' + esc(effText) + '）'))
        + '</div>';
    if (!tenantOn) {
        return '<span class="tag off">租户未开启</span>'
            + '<div class="user-mini">个人：' + (personalOn ? "同步" : "不同步") + '</div>'
            + '<div class="user-mini">系统方向：' + esc(st[p + "_tenant_dir_text"] || "—") + '</div>';
    }
    var tag = personalOn ? '<span class="tag bound">同步中</span>' : '<span class="tag off">不同步</span>';
    var label = personalOn ? "关闭同步" : "开启同步";
    var next = personalOn ? 0 : 1;
    var title = personalOn
        ? ("把本人的" + word + "设为不同步到" + SHORT + "平台")
        : ("恢复本人" + word + "同步到" + SHORT + "平台");
    return tag + dirLine
        + '<div class="row-ops">'
        + '<button type="button" class="btn small secondary" data-mtg-user="' + esc(cfgId) + '|'
        + biz + '|' + next + '" title="' + title + '">' + label + '</button>'
        + '<button type="button" class="btn small secondary" data-mtg-dir-open="' + esc(cfgId) + '|'
        + biz + '" title="选择「遵循系统设置」或指定本人的' + word + '同步方向">同步方向</button>'
        + '</div>';
}

/**
 * 设置本人的会议 / 日程同步开关（action mtgsync.userState）。
 * 关闭（enable = 0）走统一确认框；开启不再二次确认。
 */
function setUserSyncState(cfgId, biz, enable) {
    var row = findRow(cfgId);
    var word = biz === BIZ_MEETING ? "会议" : "日程";
    var run = function () {
        Oaones.apiAt(API, "mtgsync.userState",
            { biz: biz, type: TYPE, cfg_id: cfgId, enabled: enable }, function (res) {
                if (!res || res.code !== 0) {
                    showToast((res && res.msg) || "设置失败", 2);
                    // 失败时重新取一次状态，避免页面与后台不一致（例如租户刚好被管理员关闭）
                    loadSyncStates();
                    return;
                }
                var st = syncStates[String(cfgId)] || {};
                if (biz === BIZ_MEETING) {
                    st.mtg_tenant = "1";
                    st.mtg_personal = String(enable);
                } else {
                    st.sch_tenant = "1";
                    st.sch_personal = String(enable);
                }
                syncStates[String(cfgId)] = st;
                showToast((res.data && res.data.message)
                    || ("已" + (enable === 1 ? "开启" : "关闭") + "本人的" + word + "同步"));
                renderRows();
            });
    };
    if (enable === 0) {
        OaonesToast.confirm("确认关闭本人关于「" + (row ? row.cfg_name : cfgId) + "」的" + word
            + "同步？关闭后你创建 / 参与的" + word + "不再同步到" + SHORT
            + "；后台把租户同步关闭再开启，这里仍然保持“不同步”。", run, { danger: true });
        return;
    }
    run();
}

/* ------------------------------------------------------------------
 * 同步方向弹框：本人「遵循系统设置」（默认）或「指定同步方向」
 *（两个复选框，可双选；只能收窄租户允许的方向，见服务端口径）
 * ------------------------------------------------------------------ */
function openDirModal(cfgId, biz) {
    var st = syncStates[String(cfgId)] || {};
    var p = biz === BIZ_MEETING ? "mtg" : "sch";
    var word = biz === BIZ_MEETING ? "会议" : "日程";
    var row = findRow(cfgId);
    dirCfgId = String(cfgId);
    dirBiz = biz;
    byId("dirTitle").textContent = word + "同步方向"
        + (row && row.cfg_name ? " - " + row.cfg_name : "");
    byId("dirTenantTip").textContent = "租户「" + (row ? row.cfg_name : cfgId) + "」已开启" + word
        + "同步。默认遵循系统设置；也可以只保留你想要的方向。";
    byId("dirSysText").textContent = "当前系统设置：" + (st[p + "_tenant_dir_text"] || "—");
    setDirMode(String(st[p + "_dir_mode"]) === "1" ? "1" : "0");
    byId("dirOa2").checked = String(st[p + "_dir_oa2third"]) !== "0";
    byId("dirT2oa").checked = String(st[p + "_dir_third2oa"]) !== "0";
    byId("dirMask").style.display = "block";
    byId("dirModal").style.display = "block";
}

function closeDirModal() {
    byId("dirMask").style.display = "none";
    byId("dirModal").style.display = "none";
    dirCfgId = "";
}

/** 切换「遵循系统设置 / 指定同步方向」：指定时才显示两个方向复选框。 */
function setDirMode(mode) {
    var custom = mode === "1";
    byId("dirModeFollow").checked = !custom;
    byId("dirModeCustom").checked = custom;
    byId("dirCustom").style.display = custom ? "" : "none";
}

/** 保存本人的同步方向（遵循 / 指定）；保存成功后重新取一次状态（生效方向由服务端算）。 */
function saveDir() {
    if (!dirCfgId) { return; }
    var custom = byId("dirModeCustom").checked;
    var oa2 = byId("dirOa2").checked ? 1 : 0;
    var t2oa = byId("dirT2oa").checked ? 1 : 0;
    if (custom && oa2 === 0 && t2oa === 0) {
        showToast("指定同步方向时至少要勾选一个方向（OA 同步到第三方 / 第三方同步到 OA）", 1, 6000);
        return;
    }
    Oaones.apiAt(API, "mtgsync.userDirection", {
        biz: dirBiz, type: TYPE, cfg_id: dirCfgId,
        dir_mode: custom ? 1 : 0, dir_oa2third: oa2, dir_third2oa: t2oa
    }, function (res) {
        if (!res || res.code !== 0) {
            showToast((res && res.msg) || "保存同步方向失败", 2);
            return;
        }
        showToast((res.data && res.data.message) || "已保存同步方向");
        closeDirModal();
        loadSyncStates();
    });
}

function renderRows() {
    var rows = pager.apply(tenantRows);
    var html = "";
    rows.forEach(function (row) { html += rowHtml(row); });
    byId("body").innerHTML = html;
    renderEmpty();
    pager.render();
    OaonesTable.resizable(byId("table"), { key: "accountCenter." + P, min: 60 });
}

/** 没有任何启用租户时给出说明；有租户但当前页无数据时只提示分页 */
function renderEmpty() {
    var el = byId("empty");
    if (tenantRows.length > 0) {
        el.style.display = "none";
        el.textContent = "";
        return;
    }
    el.textContent = OaonesTips.pick(
        "当前还没有启用中的" + SHORT + "租户：请先到“" + SHORT + "租户配置”页新建租户并勾选启用，再回到本页扫码绑定。",
        "当前没有可自助绑定的" + SHORT + "租户（租户已停用或尚未维护）；请联系管理员在“" + SHORT + "租户配置”页处理。");
    el.style.display = "";
}

function rowHtml(row) {
    var cfgId = esc(row.cfg_id);
    var bound = row.bound === true;
    // 租户状态与绑定状态分成两列（与租户列表页的“启用 / 停用”口径一致）：
    // 停用租户只有“本人仍有绑定”时才会出现在本列表（后端只回本平台租户）
    var tenantStatus = row.disabled
        ? '<span class="tag off">停用</span>'
        : '<span class="tag bound">启用</span>';
    var status = bound
        ? '<span class="tag bound">已绑定</span>'
        : '<span class="tag unbound">未绑定</span>';
    // 平台侧对象没有泛微标准页面，保持纯文本（见 skill 第一节第 9 条）
    var xName = bound
        ? (row.wx_name ? esc(row.wx_name) : '<span class="muted">快照缺失</span>')
        : '<span class="muted">—</span>';
    var xUserId = bound ? esc(row.wx_user_id) : '<span class="muted">—</span>';
    var xDept = bound
        ? (row.wx_dept_name ? esc(row.wx_dept_name)
            + (row.wx_dept_id ? '<div class="user-mini">ID ' + esc(row.wx_dept_id) + '</div>' : "")
            : '<span class="muted">未匹配到' + SHORT + '部门</span>')
        : '<span class="muted">—</span>';
    var bindSource = bound
        ? '<span class="tag ' + (String(row.bind_source) === "1" ? "system"
            : (String(row.bind_source) === "2" ? "self" : "manual")) + '">'
            + esc(row.bind_source_text || "") + '</span>'
            + (row.remark ? '<div class="user-mini" title="备注">' + esc(row.remark) + '</div>' : "")
        : '<span class="muted">—</span>';
    var bindTime = bound ? esc(row.bind_time || "-") : '<span class="muted">—</span>';
    var syncText = bound
        ? esc(row.last_sync_text || "尚未同步")
        : '<span class="muted">—</span>';

    var ops = "";
    if (bound && !row.disabled) {
        ops += '<button type="button" class="btn small secondary" data-bind="' + cfgId
            + '" title="重新扫码：把该租户下的绑定换成新扫码的' + SHORT + '账号">重新扫码</button> ';
        ops += '<button type="button" class="btn small ghost-danger" data-unbind="' + cfgId
            + '" title="只解除你本人在该租户的绑定">解绑</button>';
    } else if (bound) {
        // 停用租户不能再扫码（服务端 account.wxQrStart 会拒绝“租户已停用”），只留解绑入口
        ops += '<button type="button" class="btn small ghost-danger" data-unbind="' + cfgId
            + '" title="只解除你本人在该租户的绑定">解绑</button>';
        ops += ' <span class="muted">租户已停用，无法重新扫码</span>';
    } else if (row.disabled) {
        ops += '<span class="muted">租户已停用，无法扫码绑定</span>';
    } else {
        ops += '<button type="button" class="btn small" data-bind="' + cfgId
            + '" title="用本人' + SHORT + '扫码，把' + SHORT + '账号绑定到你的 OA 账号">扫码绑定</button>';
    }

    return '<tr>'
        + '<td><b>' + esc(row.cfg_name) + '</b><div class="user-mini">租户ID ' + cfgId + '</div></td>'
        + '<td>' + tenantStatus + '</td>'
        + '<td>' + status + '</td>'
        + '<td>' + xName + '</td>'
        + '<td>' + xUserId + '</td>'
        + '<td>' + xDept + '</td>'
        + '<td>' + bindSource + '</td>'
        + '<td>' + bindTime + '</td>'
        + '<td>' + syncText + '</td>'
        + '<td>' + syncCell(row.cfg_id, BIZ_MEETING) + '</td>'
        + '<td>' + syncCell(row.cfg_id, BIZ_SCHEDULE) + '</td>'
        + '<td><div class="row-ops">' + ops + '</div></td>'
        + '</tr>';
}

/* ------------------------------------------------------------------
 * 扫码绑定弹框：内嵌平台官方二维码，扫码成功后由同源回调页调用父窗口的
 * OaonesWxScanDone 完成通知（页壳按当前平台转到本片段的 onScanDone）。
 * ------------------------------------------------------------------ */
/**
 * 行内“扫码绑定 / 重新扫码”入口：
 * 已绑定且为“自行绑定”（bind_source=2）的记录，重新扫码等于更换绑定，先二次确认。
 */
function startBind(cfgId) {
    var row = findRow(cfgId);
    if (isSelfBind(row)) {
        OaonesToast.confirm(SELF_BIND_CONFIRM_MSG, function () { openQrModal(cfgId); },
            { danger: true });
        return;
    }
    openQrModal(cfgId);
}

function openQrModal(cfgId) {
    if (qrLoading) { return; }
    qrCfgId = String(cfgId == null ? "" : cfgId);
    if (!qrCfgId) { return; }
    qrLoading = true;
    byId("qrTitle").textContent = SHORT + "扫码绑定";
    setQrStatus("二维码生成中…", "");
    byId("qrBox").innerHTML = '<div class="wx-qr-loading">正在向' + SHORT + '申请二维码…</div>';
    byId("qrMask").style.display = "block";
    byId("qrModal").style.display = "block";
    Oaones.apiAt(API, "account.wxQrStart", { cfg_id: qrCfgId }, function (res) {
        qrLoading = false;
        if (res.code !== 0) {
            byId("qrBox").innerHTML = '<div class="wx-qr-loading">二维码生成失败</div>';
            setQrStatus(res.msg, "fail");
            return;
        }
        var data = res.data || {};
        byId("qrTitle").textContent = SHORT + "扫码绑定"
            + (data.cfg_name ? " - " + data.cfg_name : "");
        renderQrFrame(data.qr_url);
        setQrStatus("请用本人的" + SHORT + "扫码并确认；确认后本页列表会自动刷新（也可以点“我知道了”后手动刷新）。", "");
    });
}

function renderQrFrame(url) {
    var box = byId("qrBox");
    box.innerHTML = "";
    if (!url) {
        box.innerHTML = '<div class="wx-qr-loading">' + SHORT + '未返回二维码地址</div>';
        return;
    }
    var frame = document.createElement("iframe");
    frame.className = "wx-qr-frame";
    frame.setAttribute("frameborder", "0");
    // 扫码授权页本身不需要滚动，但扫码成功后同一个 iframe 会跳到本模块的回调页，
    // 长一点的失败原因需要能滚动查看，因此这里用 auto
    frame.setAttribute("scrolling", "auto");
    frame.setAttribute("title", SHORT + "扫码绑定二维码");
    frame.src = url;
    box.appendChild(frame);
}

function setQrStatus(text, state) {
    var el = byId("qrStatus");
    el.className = "wx-qr-status" + (state ? " " + state : "");
    el.textContent = text || "";
}

function closeQrModal() {
    byId("qrMask").style.display = "none";
    byId("qrModal").style.display = "none";
    // 关掉内嵌页面，避免二维码/回调页在后台继续加载
    byId("qrBox").innerHTML = "";
    qrCfgId = "";
    qrLoading = false;
}

/** 扫码结果回调（页壳 window.OaonesWxScanDone 转发）：写状态，成功时关弹框并刷新列表 */
function onScanDone(state, res) {
    if (!res) { return; }
    if (res.ok) {
        setQrStatus(res.msg || "绑定成功", "ok");
        showToast(res.msg || "绑定成功");
        closeQrModal();
        reload();
        return;
    }
    setQrStatus(res.msg || "绑定失败，请重新扫码", "fail");
}

/**
 * 在新窗口打开二维码：部分浏览器会拦截 iframe 里的第三方 Cookie，
 * 导致内嵌二维码“扫了没反应”。这里先同步 window.open（避免异步回调被当成弹窗拦截），
 * 拿到二维码地址后再把新窗口指过去；回调页会通过 window.opener 通知本页刷新。
 */
function openQrWindow() {
    if (!qrCfgId) { return; }
    var win = window.open("", "_blank");
    Oaones.apiAt(API, "account.wxQrStart", { cfg_id: qrCfgId }, function (res) {
        if (res.code !== 0) {
            if (win) { win.close(); }
            setQrStatus(res.msg, "fail");
            return;
        }
        if (!win) {
            setQrStatus("浏览器拦截了新窗口，请允许本站弹出窗口后重试，或直接使用上方内嵌二维码扫码。", "fail");
            return;
        }
        win.location.href = (res.data || {}).qr_url;
        setQrStatus("已在新窗口打开二维码：请在新窗口里用本人" + SHORT + "扫码；"
            + "扫码成功后本页会自动刷新（若没有自动刷新，点“我知道了”后手动“刷新”）。", "");
    });
}

/** 解绑：只解除登录人本人在该租户的绑定（平台侧多对一，不影响其它 OA 人员） */
function unbind(cfgId) {
    var row = findRow(cfgId);
    var cfgName = row ? row.cfg_name : ("租户ID " + cfgId);
    var xLabel = row && row.wx_name ? "（" + SHORT + "成员：" + row.wx_name + "）"
        : (row && row.wx_user_id ? "（" + SHORT + "成员ID：" + row.wx_user_id + "）" : "");
    // 自行绑定（bind_source=2）的记录解绑前用统一文案二次确认
    var msg = isSelfBind(row) ? SELF_BIND_CONFIRM_MSG
        : ("确认解除本人在「" + cfgName + "」的" + SHORT + "绑定" + xLabel + "？"
            + "解绑后该租户不再同步你的账号信息，其它 OA 人员的绑定不受影响；需要时可再次扫码绑定。");
    OaonesToast.confirm(msg,
        function () {
            Oaones.apiAt(API, "account.wxUnbind", { cfg_id: cfgId }, function (res) {
                if (res.code === 0) {
                    showToast("已解除绑定");
                    reload();
                } else {
                    showToast(res.msg, 2);
                }
            });
        }, { danger: true });
}

function findRow(cfgId) {
    var key = String(cfgId == null ? "" : cfgId);
    for (var i = 0; i < tenantRows.length; i++) {
        if (String(tenantRows[i].cfg_id) === key) { return tenantRows[i]; }
    }
    return null;
}

/** 切到本平台 tab 时由页壳调用：重画一次，保证“表头拖拽列宽”在表格可见后初始化 */
function onShow() { renderRows(); }

window.OaonesAccountPanels = window.OaonesAccountPanels || {};
window.OaonesAccountPanels[P] = {
    platform: P,
    render: render,
    onShow: onShow,
    onScanDone: onScanDone,
    isModalOpen: function () { return byId("qrModal").style.display === "block"; },
    closeModal: closeQrModal
};

/* 本片段自己的事件注册（页壳脚本在页面末尾执行，不依赖页壳函数） */
byId("body").addEventListener("click", function (e) {
    var target = e.target;
    if (!target || !target.closest) { return; }
    var bindBtn = target.closest("button[data-bind]");
    if (bindBtn) { startBind(bindBtn.getAttribute("data-bind")); return; }
    var unbindBtn = target.closest("button[data-unbind]");
    if (unbindBtn) { unbind(unbindBtn.getAttribute("data-unbind")); return; }
    var mtgBtn = target.closest("button[data-mtg-user]");
    if (mtgBtn) {
        var parts = String(mtgBtn.getAttribute("data-mtg-user")).split("|");
        setUserSyncState(parts[0], parseInt(parts[1], 10), parseInt(parts[2], 10));
        return;
    }
    var dirBtn = target.closest("button[data-mtg-dir-open]");
    if (dirBtn) {
        var dirParts = String(dirBtn.getAttribute("data-mtg-dir-open")).split("|");
        openDirModal(dirParts[0], parseInt(dirParts[1], 10));
    }
});
byId("qrMask").addEventListener("click", closeQrModal);
byId("qrCloseBtn").addEventListener("click", closeQrModal);
byId("qrDoneBtn").addEventListener("click", function () {
    closeQrModal();
    reload();
});
byId("qrWindowBtn").addEventListener("click", openQrWindow);
byId("qrAgainBtn").addEventListener("click", function () {
    if (!qrCfgId) { return; }
    openQrModal(qrCfgId);
});
// 同步方向弹框
byId("dirMask").addEventListener("click", closeDirModal);
byId("dirCloseBtn").addEventListener("click", closeDirModal);
byId("dirCancelBtn").addEventListener("click", closeDirModal);
byId("dirSaveBtn").addEventListener("click", saveDir);
byId("dirModeFollow").addEventListener("change", function () { setDirMode("0"); });
byId("dirModeCustom").addEventListener("change", function () { setDirMode("1"); });
document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && byId("qrModal").style.display === "block") { closeQrModal(); }
    if (e.key === "Escape" && byId("dirModal").style.display === "block") { closeDirModal(); }
});
})();
</script>
