<%@ page pageEncoding="UTF-8" %>
<%--
  个人账号中心 - 「个人微信」页签片段（本人自助）。

  个人微信与企微 / 钉钉 / 飞书并列，但它**没有“企业 / 租户”概念**：
  一套部署只有一份微信授权密钥（由管理员在独立一级菜单「微信设置」页维护），
  一个 OA 人员最多一条绑定，因此这里**不是一行一个租户的列表**，而是一行本人绑定状态。

  子账户（E9 标准 hrmresource.accounttype = 1）**不参与个人微信绑定**：pw.mine 回
  sub_account / account_type 后，本片段给出说明并禁用扫码绑定入口（服务端 qrStart 也会拒绝）；
  子账户上历史遗留的那一条绑定仍可用“解绑”清除（否则没人能收尾）。

  本片段对外只暴露 window.OaonesAccountPanels.personalwx（render / onShow / onScanDone /
  isModalOpen / closeModal），请求与取数时机由页壳负责（见 MyAccount.jsp），
  片段不反向依赖页壳脚本；扫码回调由页壳 window.OaonesWxScanDone 按当前页签路由到 onScanDone。
--%>
<div id="acpw-panel">
    <div class="toolbar">
        <label>我的个人微信</label>
        <span style="flex:1;"></span>
        <button type="button" class="btn secondary" id="acpw-refreshBtn">刷新</button>
        <button type="button" class="btn" id="acpw-bindBtn">扫码绑定</button>
    </div>
    <p class="notice" id="acpw-notice" style="display:none;"></p>
    <div class="bind-stats" id="acpw-stats"></div>
    <div class="table-wrap">
        <table class="grid" id="acpw-table">
            <thead>
            <tr>
                <th style="width:10%;">绑定状态</th>
                <th style="width:16%;">微信昵称</th>
                <th style="width:24%;">微信 openid</th>
                <th style="width:11%;">绑定方式</th>
                <th style="width:16%;">绑定时间</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody id="acpw-body"></tbody>
        </table>
        <div class="empty" id="acpw-empty" style="display:none;"></div>
    </div>
</div>

<%-- 扫码绑定弹框：内嵌微信授权二维码（二维码地址由后端按签名 state 生成） --%>
<div class="modal-mask" id="acpw-qrMask"></div>
<div class="modal wx-qr-modal" id="acpw-qrModal">
    <div class="modal-head">
        <span id="acpw-qrTitle">微信扫码绑定</span>
        <button type="button" class="link" id="acpw-qrCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <p class="wx-qr-tip">请使用<span class="rule">本人的个人微信</span>扫描下方二维码，并在手机上点击确认：
            确认后会把扫码得到的微信号绑定到<span class="rule">你的 OA 账号</span>（只影响你自己）。
            同一个 OA 账号只保留一条绑定，重复扫码即<b>换绑</b>；一个微信号只能绑定一个 OA 账号。</p>
        <div class="wx-qr-box" id="acpw-qrBox"></div>
        <p class="wx-qr-status" id="acpw-qrStatus"></p>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="acpw-qrWindowBtn"
                title="浏览器拦截内嵌二维码（第三方 Cookie）时改用新窗口扫码">在新窗口打开二维码</button>
        <button type="button" class="btn secondary" id="acpw-qrAgainBtn">重新生成二维码</button>
        <button type="button" class="btn" id="acpw-qrDoneBtn">我知道了</button>
    </div>
</div>

<script>
(function () {
"use strict";
/** 个人微信的接口入口：统一由企微连接器的 /oaones/WeCom/api.jsp 路由（action 前缀 pw.） */
var API_PW = "/oaones/WeCom/api.jsp";
/** 本页签的绑定状态（后端 pw.mine 下发） */
var pwState = null;
/** 二维码请求中标记：避免连点重复生成 */
var qrLoading = false;

function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

/** 页壳取到 pw.mine 后调用 */
function render(data) {
    pwState = data || {};
    var configured = pwState.configured === true;
    var bound = pwState.bound === true;
    // 子账户（E9 标准 hrmresource.accounttype = 1）不参与个人微信绑定：入口直接禁用并给出说明
    var subAccount = pwState.sub_account === true;
    byId("acpw-stats").innerHTML = '<span class="bind-stat">绑定状态 <b>'
        + (bound ? "已绑定" : "未绑定") + '</b></span>'
        + '<span class="bind-stat">账号 <b>' + esc(pwState.name || "-") + '</b></span>';
    byId("acpw-bindBtn").textContent = bound ? "换绑微信" : "扫码绑定";
    byId("acpw-bindBtn").disabled = false;

    var notice = byId("acpw-notice");
    if (subAccount) {
        notice.textContent = "你登录的是子账户（次账号）：子账户不参与个人微信绑定，"
            + "请改用该人员的主账户登录后再扫码绑定；绑定列表也只维护主账户。"
            + (bound ? "本账号上还留着一条历史绑定，可直接“解绑”清除。" : "");
        notice.style.display = "";
        byId("acpw-bindBtn").disabled = true;
    } else if (!configured) {
        notice.textContent = "本系统的微信授权密钥（AppID / AppSecret）尚未维护，"
            + "暂时无法扫码绑定个人微信；请联系管理员在「微信设置」页补充。";
        notice.style.display = "";
        byId("acpw-bindBtn").disabled = true;
    } else if (pwState.sys_admin === true) {
        notice.textContent = "你登录的是系统管理员账号：个人微信绑定同样支持系统管理员，"
            + "扫码后会把微信号绑定到本账号。";
        notice.style.display = "";
    } else {
        notice.style.display = "none";
    }
    byId("acpw-body").innerHTML = rowHtml(bound);
    renderEmpty(bound);
}

function renderEmpty(bound) {
    var el = byId("acpw-empty");
    if (bound) {
        el.style.display = "none";
        el.textContent = "";
        return;
    }
    if (pwState && pwState.sub_account === true) {
        el.textContent = "你登录的是子账户（次账号），不支持绑定个人微信；"
            + "请改用该人员的主账户登录后再扫码绑定。";
        el.style.display = "";
        return;
    }
    el.textContent = "你当前还没有绑定个人微信：点右上角“扫码绑定”，用本人微信扫二维码并在手机上确认即可。";
    el.style.display = "";
}

function rowHtml(bound) {
    var s = pwState || {};
    var status = bound
        ? '<span class="tag bound">已绑定</span>'
        : '<span class="tag unbound">未绑定</span>';
    var avatar = bound && s.headimgurl
        ? '<img class="pw-wx-avatar" alt="微信头像" src="' + esc(s.headimgurl) + '">' : "";
    var nick = bound
        ? (s.nickname ? esc(s.nickname) : '<span class="muted">未取到昵称</span>')
        : '<span class="muted">—</span>';
    var openid = bound
        ? esc(s.openid_masked || "") + (s.openid
            ? '<div class="user-mini" title="' + esc(s.openid) + '">ID ' + esc(s.openid) + '</div>' : "")
        : '<span class="muted">—</span>';
    var source = bound
        ? '<span class="tag ' + (String(s.bind_source) === "2" ? "self"
            : (String(s.bind_source) === "1" ? "system" : "manual")) + '">'
            + esc(s.bind_source_text || "") + '</span>'
            + (s.remark ? '<div class="user-mini" title="备注">' + esc(s.remark) + '</div>' : "")
        : '<span class="muted">—</span>';
    var bindTime = bound ? esc(s.bind_time || "-") : '<span class="muted">—</span>';
    // 子账户不参与绑定：不提供“扫码绑定 / 换绑”；历史遗留的那一条仍可“解绑”清除（否则没人能收尾）
    var ops = s.sub_account === true
        ? (bound
            ? '<button type="button" class="btn small ghost-danger" data-unbind="1" title="解除本人这一条个人微信绑定（历史遗留）">解绑</button>'
            : '<span class="muted">子账户不支持绑定个人微信</span>')
        : bound
        ? '<button type="button" class="btn small secondary" data-rebind="1" title="换绑：用新的微信号扫码，覆盖当前的绑定">换绑</button> '
            + '<button type="button" class="btn small ghost-danger" data-unbind="1" title="解除本人这一条个人微信绑定">解绑</button>'
        : '<button type="button" class="btn small" data-rebind="1" title="用本人微信扫码，把微信号绑定到你的 OA 账号">扫码绑定</button>';
    return '<tr>'
        + '<td>' + status + '</td>'
        + '<td>' + avatar + nick + '</td>'
        + '<td>' + openid + '</td>'
        + '<td>' + source + '</td>'
        + '<td>' + bindTime + '</td>'
        + '<td><div class="row-ops">' + ops + '</div></td>'
        + '</tr>';
}

/** “重新加载本页签”：由页壳注入（片段不反向依赖页壳脚本） */
function reload() {
    if (window.OaonesAccount && typeof window.OaonesAccount.reload === "function") {
        window.OaonesAccount.reload("personalwx");
    }
}

/* ---------------- 扫码绑定弹框 ---------------- */
function startBind() {
    if (pwState && pwState.sub_account === true) {
        showToast("你登录的是子账户（次账号），不参与个人微信绑定；请用该人员的主账户绑定", 1, 0);
        return;
    }
    if (pwState && pwState.configured !== true) {
        showToast("本系统的微信授权密钥（AppID / AppSecret）尚未维护，暂时无法扫码绑定", 1, 0);
        return;
    }
    if (qrLoading) { return; }
    qrLoading = true;
    byId("acpw-qrTitle").textContent = "微信扫码绑定";
    setQrStatus("二维码生成中…", "");
    byId("acpw-qrBox").innerHTML = '<div class="wx-qr-loading">正在向微信申请二维码…</div>';
    byId("acpw-qrMask").style.display = "block";
    byId("acpw-qrModal").style.display = "block";
    // 不带 oa_user_id = 绑定本人（服务端只读写登录会话本人的那一行）
    Oaones.apiAt(API_PW, "pw.qrStart", {}, function (res) {
        qrLoading = false;
        if (res.code !== 0) {
            byId("acpw-qrBox").innerHTML = '<div class="wx-qr-loading">二维码生成失败</div>';
            setQrStatus(res.msg, "fail");
            return;
        }
        var data = res.data || {};
        renderQrFrame(data.qr_url);
        setQrStatus("请用本人的个人微信扫码并确认；确认后本页会自动刷新"
            + "（也可以点“我知道了”后手动刷新）。", "");
    });
}

function renderQrFrame(url) {
    var box = byId("acpw-qrBox");
    box.innerHTML = "";
    if (!url) {
        box.innerHTML = '<div class="wx-qr-loading">微信未返回二维码地址</div>';
        return;
    }
    var frame = document.createElement("iframe");
    frame.className = "wx-qr-frame";
    frame.setAttribute("frameborder", "0");
    // 扫码成功后同一个 iframe 会跳到本模块的回调页，长一点的失败原因需要能滚动查看
    frame.setAttribute("scrolling", "auto");
    frame.setAttribute("title", "微信扫码绑定二维码");
    frame.src = url;
    box.appendChild(frame);
}

function setQrStatus(text, state) {
    var el = byId("acpw-qrStatus");
    el.className = "wx-qr-status" + (state ? " " + state : "");
    el.textContent = text || "";
}

function closeQrModal() {
    byId("acpw-qrMask").style.display = "none";
    byId("acpw-qrModal").style.display = "none";
    // 关掉内嵌页面，避免二维码/回调页在后台继续加载
    byId("acpw-qrBox").innerHTML = "";
    qrLoading = false;
}

/** 扫码结果回调（页壳 window.OaonesWxScanDone 转发）：成功时关弹框并刷新本页签 */
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

/** 在新窗口打开二维码：部分浏览器会拦截 iframe 里的第三方 Cookie，导致内嵌二维码“扫了没反应” */
function openQrWindow() {
    var win = window.open("", "_blank");
    Oaones.apiAt(API_PW, "pw.qrStart", {}, function (res) {
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
        setQrStatus("已在新窗口打开二维码：请在新窗口里用本人微信扫码；"
            + "扫码成功后本页会自动刷新（若没有自动刷新，点“我知道了”后手动“刷新”）。", "");
    });
}

/** 解绑：只解除登录人本人这一条个人微信绑定 */
function unbind() {
    var nick = pwState && pwState.nickname ? "（微信：" + pwState.nickname + "）" : "";
    OaonesToast.confirm("确认解除你本人的个人微信绑定" + nick + "？"
        + "解绑后该微信号可以重新绑定到其它 OA 账号，需要时可以再次扫码绑定。",
        function () {
            Oaones.apiAt(API_PW, "pw.unbind", {}, function (res) {
                if (res.code === 0) {
                    showToast("已解除绑定");
                    reload();
                } else {
                    showToast(res.msg, 2);
                }
            });
        }, { danger: true });
}

/** 切到本页签时由页壳调用：重画一次（表格从 display:none 变可见后列宽等才能量准） */
function onShow() { render(pwState); }

window.OaonesAccountPanels = window.OaonesAccountPanels || {};
window.OaonesAccountPanels.personalwx = {
    platform: "personalwx",
    render: render,
    onShow: onShow,
    onScanDone: onScanDone,
    isModalOpen: function () { return byId("acpw-qrModal").style.display === "block"; },
    closeModal: closeQrModal
};

/* 本片段自己的事件注册（页壳脚本在页面末尾执行，不依赖页壳函数） */
byId("acpw-bindBtn").addEventListener("click", startBind);
byId("acpw-refreshBtn").addEventListener("click", reload);
byId("acpw-body").addEventListener("click", function (e) {
    var target = e.target;
    if (!target || !target.closest) { return; }
    if (target.closest("button[data-rebind]")) { startBind(); return; }
    if (target.closest("button[data-unbind]")) { unbind(); }
});
byId("acpw-qrMask").addEventListener("click", closeQrModal);
byId("acpw-qrCloseBtn").addEventListener("click", closeQrModal);
byId("acpw-qrDoneBtn").addEventListener("click", function () { closeQrModal(); reload(); });
byId("acpw-qrWindowBtn").addEventListener("click", openQrWindow);
byId("acpw-qrAgainBtn").addEventListener("click", startBind);
document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && byId("acpw-qrModal").style.display === "block") { closeQrModal(); }
});
})();
</script>
