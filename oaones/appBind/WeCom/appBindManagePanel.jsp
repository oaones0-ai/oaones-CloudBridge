<%@ page pageEncoding="UTF-8" %>
<%-- 应用绑定主体（一行一个企微工作台应用）：markup + 本片段脚本 --%>
<%-- 一行一条绑定：企微应用（AgentId）→ 在企微工作台打开的 OA 页面（PC/手机端各一条链接），免登录进入 --%>
        <div id="appManagePanel">
            <div class="filter-bar">
                <span class="filter-label">应用筛选：</span>
                <input type="text" id="appKeyword" name="oaonesAppKeyword" placeholder="应用名称 / AgentId / OA 地址 / 备注"
                       autocomplete="off" data-lpignore="true" style="min-width:240px;max-width:340px;">
                <button type="button" class="btn small" id="appSearchBtn">搜索</button>
                <button type="button" class="btn small secondary" id="appResetBtn">重置筛选</button>
            </div>
            <div class="bind-stats" id="appStats"></div>
            <div class="table-wrap">
                <table class="grid" id="appTable">
                    <thead>
                    <tr>
                        <th style="width:22%;">企微应用</th>
                        <th style="width:28%;">应用主页地址</th>
                        <th style="width:20%;">OA页面地址（PC / 手机端）</th>
                        <th>状态</th>
                        <th>更新时间</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="appBody"></tbody>
                </table>
                <div class="empty" id="appEmpty" style="display:none;">暂无应用绑定</div>
            </div>
            <div class="pager" id="appPagerEl"></div>
        </div>

<%-- 新建 / 编辑应用绑定弹框 --%>
<div class="modal-mask" id="appEditMask"></div>
<div class="modal" id="appEditModal" style="width:660px;">
    <div class="modal-head"><span id="appEditTitle">新建应用绑定</span><button type="button" class="modal-x" id="appEditCloseBtn" title="关闭" aria-label="关闭"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg></button></div>
    <div class="modal-body">
        <input type="hidden" id="appId">
        <div class="form-row"><label>应用名称</label><div class="field">
            <input id="appName" placeholder="企微后台该应用的名称，例如：OA 办公"></div></div>
        <div class="form-row"><label>AgentId *</label><div class="field">
            <input id="appAgentId" placeholder="企微后台“应用管理 → 自建 → 应用详情”里的 AgentId"></div></div>
        <div class="form-row"><label>应用 Secret</label><div class="field">
            <%-- autocomplete=new-password 与非登录语义的 name：避免浏览器把本页当成登录表单，
                 把保存的登录名（如 sysadmin）自动填进页面上面的输入框 --%>
            <input id="appSecret" name="oaonesAppSecret" type="password" autocomplete="new-password"
                   data-lpignore="true" placeholder="留空则用租户配置里的 Secret；已配置时显示 ****，不动即不修改"></div></div>
        <div class="form-row">
            <label>PC端OA页面地址 *<span class="help-tip inline" id="appUrlHelpTip" data-help="appUrlHelpText"
                    role="button" tabindex="0" title="查看地址填写说明" aria-label="查看地址填写说明">?</span></label>
            <div class="field"><input id="appOaPageUrl" placeholder="站内地址，例如 /wui/index.html#/main/cube/search?customid=132"></div>
        </div>
        <div class="form-row">
            <label>手机端OA页面地址</label>
            <div class="field"><input id="appOaPageUrlMobile"
                    placeholder="留空表示与 PC 端相同，例如 /spa/portal/index_mobx.html"></div>
        </div>
        <div class="help-source" id="appUrlHelpText">两个地址都是 <span class="rule">本站内的相对地址</span>（以 / 开头），
            不要填完整域名；也可以直接把浏览器地址栏里的整段网址粘进来，保存时会自动去掉域名，
            并<span class="rule">完整保留 # 及其后面的 ?customid=… 参数</span>。
            <span class="rule">PC端OA页面地址（必填）</span>：从企业微信<b>电脑版</b>、或电脑浏览器打开时进入的页面；
            <span class="rule">手机端OA页面地址</span>：从企业微信<b>手机端</b>打开时进入的页面（留空则手机端也用 PC 端地址）；
            客户端类型由企业微信客户端的 User-Agent 自动识别（手机端带 iPhone/Android 等标识、
            电脑端带 Windows/Macintosh 标识），不需要人工选择；
            也可以在本应用的入口地址后加 <span class="rule">device=pc</span> 或 <span class="rule">device=mobile</span>
            强制指定（与企微后台“应用主页 → 分别配置手机与电脑端”配合使用很方便）。
            常用取值：<span class="rule">/wui/index.html</span>（E9 门户）、
            <span class="rule">/spa/portal/index_mobx.html</span>（移动门户）、
            <span class="rule">/spa/workflow/index_mobx.html#/main/workflow/list</span>（我的待办）、
            以及 E9 里带 <span class="rule">#/main/…?customid=…</span> 的搜索页等。
            同一个应用要开多个不同页面时，可在企业微信后台该应用的其他入口地址后追加
            <span class="rule">state</span> 参数（值为站内路径或其 base64url 编码），该参数优先于本页配置。</div>
        <div class="form-row"><label>状态</label><div class="field">
            <select id="appState">
                <option value="0">启用（员工可从企微工作台免登录进入）</option>
                <option value="1">停用（打开应用时提示已停用）</option>
            </select></div></div>
        <div class="form-row"><label>备注</label><div class="field"><textarea id="appRemark" rows="2"></textarea></div></div>
        <div class="muted" id="appEntryPreview"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="appEditCancelBtn">取消</button>
        <button type="button" class="btn" id="appSaveBtn">保存</button>
    </div>
</div>

<script>
"use strict";
/** 片段脚本比页壳脚本先执行（页壳的 byId 在页面末尾才定义），片段内必须自带一份，否则解析期就报 byId is not defined */
function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

/** 列表分页器（每页 10/20/50/100/1000，见 js/table.js）：翻页后重绘当前列表 */
var appPager = OaonesTable.pager({
    key: "appBind.manage",
    el: byId("appPagerEl"),
    onChange: function () { renderAppManage(); }
});

/* ------------------------------------------------------------------
 * 列表：一行一个企微工作台应用（多应用绑定），行内给出可直接粘贴到企微后台的应用主页地址
 * ------------------------------------------------------------------ */

function appKeywordValue() {
    return (byId("appKeyword").value || "").trim();
}

function appText(a) {
    return [a.app_name, a.agent_id, a.oa_page_url, a.oa_page_url_mobile, a.remark, a.creator_name]
        .map(function (v) { return String(v == null ? "" : v); }).join(" ").toLowerCase();
}

function filterAppRows() {
    var rows = state.apps || [];
    var k = appKeywordValue().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (a) { return appText(a).indexOf(k) >= 0; });
}

/** 筛选条件签名：租户或关键字变化都要回到第 1 页 */
function appFilterSignature() {
    return String(state.cfgId) + "|" + appKeywordValue();
}

/** 应用主页地址：由后端给出的入口前缀 + 本行的租户ID与 AgentId 拼成（可整段粘到企微后台） */
function appEntryUrl(a) {
    if (!state.entryBase || !a || !a.agent_id) { return ""; }
    return state.entryBase + "?cfg_id=" + encodeURIComponent(String(a.cfg_id))
        + "&agent_id=" + encodeURIComponent(String(a.agent_id));
}

function cfgNameOf(cfgId) {
    var id = String(cfgId == null ? "" : cfgId);
    for (var i = 0; i < (state.cfgs || []).length; i++) {
        if (String(state.cfgs[i].id) === id) { return state.cfgs[i].corp_name || ("企微租户 ID " + id); }
    }
    return "企微租户 ID " + id;
}

function urlCellHtml(url, emptyText) {
    var value = String(url == null ? "" : url);
    if (!value) { return "<span class='muted'>" + esc(emptyText) + "</span>"; }
    return "<code class='app-url'>" + esc(value) + "</code>";
}

function renderAppStats(rows) {
    rows = rows || filterAppRows();
    var enabled = 0;
    var disabled = 0;
    rows.forEach(function (a) { if (a.enable) { enabled++; } else { disabled++; } });
    byId("appStats").innerHTML = ""
        + "<span class='bind-stat'><span>应用绑定</span><b>" + rows.length + "</b> 个</span>"
        + "<span class='bind-stat'><span>启用中</span><b>" + enabled + "</b> 个</span>"
        + "<span class='bind-stat'><span>已停用</span><b>" + disabled + "</b> 个</span>"
        + "<span class='bind-stat'><span>当前租户</span><b>" + esc(state.cfgId ? cfgNameOf(state.cfgId) : "-") + "</b></span>";
}

function renderAppManage() {
    var rows = filterAppRows();
    appPager.resetIfChanged(appFilterSignature());
    var pageRows = appPager.apply(rows);
    renderAppStats(rows);
    var body = "";
    pageRows.forEach(function (a) {
        var id = String(a.id);
        var entryUrl = appEntryUrl(a);
        var statusHtml = a.enable
            ? "<span class='tag bound'>启用</span>"
            : "<span class='tag off'>停用</span>";
        var tenantOff = String(a.cfg_sync_enable) !== "1";
        var note = "";
        if (tenantOff) {
            note += "<div class='row-note'>所属企微租户已停用：即使本绑定启用，员工也无法免登录进入</div>";
        }
        body += "<tr>"
            + "<td><div><b>" + esc(a.app_name || ("AgentId " + a.agent_id)) + "</b></div>"
            + "<div class='user-mini'>AgentId：" + esc(a.agent_id) + "</div>"
            + "<div class='user-mini'>企微租户：" + esc(cfgNameOf(a.cfg_id)) + "</div>"
            + "<div class='user-mini'>创建：" + esc(a.creator_name || a.create_by || "-")
            + "　" + esc(a.create_time || "-") + "</div>" + note + "</td>"
            + "<td>" + (entryUrl
                ? "<code class='app-url'>" + esc(entryUrl) + "</code>"
                    + "<div class='row-ops'><button type='button' class='btn small secondary app-copy-btn' data-url='"
                    + esc(entryUrl) + "' title='复制后粘贴到企微后台该自建应用的“应用主页”'>复制</button></div>"
                : "<span class='muted'>缺少 AgentId，保存后可生成</span>") + "</td>"
            + "<td><div class='user-mini'>PC端：</div>"
            + "<div>" + urlCellHtml(a.oa_page_url, "未配置") + "</div>"
            + "<div class='user-mini'>手机端：</div>"
            + "<div>" + urlCellHtml(a.oa_page_url_mobile, a.oa_page_url ? "与 PC 端相同" : "未配置") + "</div></td>"
            + "<td>" + statusHtml + "</td>"
            + "<td>" + esc(a.update_time || a.create_time || "-") + "</td>"
            + "<td><div class='row-ops'>"
            + "<button type='button' class='link app-edit-btn' data-id='" + esc(id) + "'>编辑</button>"
            + "<button type='button' class='link app-state-btn' data-id='" + esc(id) + "' data-state='"
            + (a.enable ? "1" : "0") + "'>" + (a.enable ? "停用" : "启用") + "</button>"
            + "<button type='button' class='link danger app-del-btn' data-id='" + esc(id) + "' data-name='"
            + esc(a.app_name || a.agent_id) + "'>删除</button>"
            + "</div></td>"
            + "</tr>";
    });
    byId("appBody").innerHTML = body;
    appPager.show();
    appPager.render();
    OaonesTable.resizable(byId("appTable"), { key: "appBind.appTable" });
    var empty = byId("appEmpty");
    empty.style.display = pageRows.length ? "none" : "block";
    empty.textContent = appKeywordValue()
        ? "没有匹配的应用绑定"
        : (state.cfgId ? "当前租户还没有应用绑定，点右上角“新建应用绑定”添加" : "请先在右上角选择企业微信租户");
}

function findApp(id) {
    var key = String(id == null ? "" : id);
    for (var i = 0; i < (state.apps || []).length; i++) {
        if (String(state.apps[i].id) === key) { return state.apps[i]; }
    }
    return null;
}

/* ------------------------------------------------------------------
 * 新建 / 编辑：应用主页地址由 AgentId 生成，保存后由后端校验唯一性与站内地址
 * ------------------------------------------------------------------ */

function openAppEdit(app) {
    if (!state.cfgId) {
        showToast("请先选择企业微信租户（没有租户时请先到“企微租户配置”页新建）", 1);
        return;
    }
    byId("appEditTitle").textContent = app ? "编辑应用绑定" : "新建应用绑定";
    byId("appId").value = app ? String(app.id) : "";
    byId("appName").value = app ? String(app.app_name || "") : "";
    byId("appAgentId").value = app ? String(app.agent_id || "") : "";
    // 应用 Secret 不回显真实值：编辑时只显示 ****（不动它 = 继续用原 Secret / 租户兜底）
    OaonesSecret.reset(byId("appSecret"), !!(app && app.app_secret));
    byId("appOaPageUrl").value = app ? String(app.oa_page_url || "") : "";
    byId("appOaPageUrlMobile").value = app ? String(app.oa_page_url_mobile || "") : "";
    byId("appState").value = app && !app.enable ? "1" : "0";
    byId("appRemark").value = app ? String(app.remark || "") : "";
    renderAppEntryPreview();
    byId("appEditMask").style.display = "block";
    byId("appEditModal").style.display = "block";
    byId("appAgentId").focus();
}

function closeAppEdit() {
    byId("appEditMask").style.display = "none";
    byId("appEditModal").style.display = "none";
}

/** 弹框内的应用主页地址预览：AgentId 填好后即时显示，便于先配企微后台 */
function renderAppEntryPreview() {
    var agentId = (byId("appAgentId").value || "").trim();
    var base = state.entryBase;
    var preview = byId("appEntryPreview");
    if (!base) {
        preview.textContent = "应用主页地址：保存后在列表里“复制”（当前未取到入口地址前缀）";
        return;
    }
    preview.textContent = agentId
        ? "应用主页地址（填进企微后台该自建应用的“应用主页”）：" + base
            + "?cfg_id=" + state.cfgId + "&agent_id=" + agentId
            + "（手机端/电脑端地址都填这一条即可，客户端类型会自动识别；"
            + "若用企微后台“分别配置手机与电脑端”，手机端那条可加 &device=mobile，电脑端加 &device=pc）"
        : "应用主页地址：填写 AgentId 后自动生成（形如 " + base + "?cfg_id=" + state.cfgId + "&agent_id=1000002）";
}

function saveAppEdit() {
    if (state.saving) { return; }
    var agentId = (byId("appAgentId").value || "").trim();
    var pageUrl = (byId("appOaPageUrl").value || "").trim();
    var pageUrlMobile = (byId("appOaPageUrlMobile").value || "").trim();
    if (!agentId) {
        showToast("请填写企业微信自建应用的 AgentId", 1);
        byId("appAgentId").focus();
        return;
    }
    if (!pageUrl) {
        showToast("请填写 PC端OA页面地址（必填）", 1);
        byId("appOaPageUrl").focus();
        return;
    }
    var params = {
        id: byId("appId").value,
        cfg_id: state.cfgId,
        agent_id: agentId,
        app_name: (byId("appName").value || "").trim(),
        app_secret: OaonesSecret.value(byId("appSecret")).trim(),
        oa_page_url: pageUrl,
        oa_page_url_mobile: pageUrlMobile,
        bind_state: byId("appState").value,
        remark: byId("appRemark").value || ""
    };
    state.saving = true;
    byId("appSaveBtn").disabled = true;
    Oaones.api("app.save", params, function (res) {
        state.saving = false;
        byId("appSaveBtn").disabled = false;
        if (res.code === 0) {
            showToast("应用绑定已保存");
            closeAppEdit();
            loadData();
        } else {
            showToast(res.msg, 2);
        }
    });
}

function toggleAppState(id) {
    var app = findApp(id);
    if (!app) { return; }
    var nextState = app.enable ? "1" : "0";
    var tip = app.enable
        ? "停用应用「" + (app.app_name || app.agent_id) + "」的绑定？停用后员工从企微工作台打开该应用会看到“绑定已停用”，不会免登录进入 OA。"
        : "启用应用「" + (app.app_name || app.agent_id) + "」的绑定？启用后员工从企微工作台打开该应用即可免登录进入配置的 OA 页面。";
    OaonesToast.confirm(tip, function () {
        Oaones.api("app.state", { id: id, cfg_id: app.cfg_id, bind_state: nextState }, function (res) {
            if (res.code === 0) {
                showToast(nextState === "1" ? "应用绑定已停用" : "应用绑定已启用");
                loadData();
            } else {
                showToast(res.msg, 2);
            }
        });
    });
}

function delAppBind(id, name) {
    OaonesToast.confirm("确认删除应用「" + (name || "") + "」的绑定？"
        + "删除后该企微应用主页地址即刻失效（员工从工作台打开会提示没有找到绑定），"
        + "需要重新配置时请到企微后台更新应用主页；此操作只影响本页绑定，不会删除企业微信里的应用。",
        function () {
            Oaones.api("app.delete", { id: id }, function (res) {
                if (res.code === 0) {
                    showToast("应用绑定已删除");
                    loadData();
                } else {
                    showToast(res.msg, 2);
                }
            });
        }, { danger: true });
}

/* ------------------------------------------------------------------
 * 复制应用主页地址：优先用剪贴板 API，非安全上下文（http）退化为临时文本框 + execCommand
 * ------------------------------------------------------------------ */
function copyText(text) {
    text = String(text == null ? "" : text);
    if (!text) { return; }
    function fallback() {
        var ta = document.createElement("textarea");
        ta.value = text;
        ta.setAttribute("readonly", "readonly");
        ta.style.position = "fixed";
        ta.style.left = "-9999px";
        document.body.appendChild(ta);
        ta.select();
        var ok = false;
        try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
        document.body.removeChild(ta);
        showToast(ok ? "应用主页地址已复制，请粘贴到企微后台该自建应用的“应用主页”"
            : "复制失败，请手动选中地址后复制", ok ? 0 : 1);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () {
            showToast("应用主页地址已复制，请粘贴到企微后台该自建应用的“应用主页”");
        }, fallback);
        return;
    }
    fallback();
}

/* 事件接线：片段内的按钮统一走事件委托，避免整表重绘后失效 */
/**
 * 浏览器可能把保存的登录名（本系统登录名，如 sysadmin）自动填进“应用筛选”：
 * 页面初始化后清掉它，避免一进页面就按这个关键字过滤（它不是用户输入的内容）。
 * 用户动过该输入框后就不再干预。
 */
var appKeywordTouched = false;
function clearAutofilledAppKeyword() {
    var kw = byId("appKeyword");
    if (!kw || appKeywordTouched) { return; }
    if (kw.value) {
        kw.value = "";
        state.appKeyword = "";
        renderAppManage();
    }
}
[0, 400, 1200].forEach(function (delay) {
    window.setTimeout(clearAutofilledAppKeyword, delay);
});
// 少数浏览器会在窗口重新获得焦点时才回填：再兜一次（用户自己输入过就不动）
window.addEventListener("focus", clearAutofilledAppKeyword);

byId("appSearchBtn").addEventListener("click", function () { renderAppManage(); });
byId("appResetBtn").addEventListener("click", function () {
    byId("appKeyword").value = "";
    renderAppManage();
});
byId("appKeyword").addEventListener("input", function () {
    appKeywordTouched = true;
    renderAppManage();
});
byId("appBody").addEventListener("click", function (e) {
    var t = e.target;
    if (!t || !t.classList) { return; }
    if (t.classList.contains("app-copy-btn")) {
        copyText(t.getAttribute("data-url"));
        return;
    }
    if (t.classList.contains("app-edit-btn")) {
        openAppEdit(findApp(t.getAttribute("data-id")));
        return;
    }
    if (t.classList.contains("app-state-btn")) {
        toggleAppState(t.getAttribute("data-id"));
        return;
    }
    if (t.classList.contains("app-del-btn")) {
        delAppBind(t.getAttribute("data-id"), t.getAttribute("data-name"));
    }
});
byId("appEditCloseBtn").addEventListener("click", closeAppEdit);
byId("appEditCancelBtn").addEventListener("click", closeAppEdit);
byId("appEditMask").addEventListener("click", closeAppEdit);
byId("appSaveBtn").addEventListener("click", saveAppEdit);
byId("appAgentId").addEventListener("input", renderAppEntryPreview);
// 密钥框：点进掩码时让出掩码、切回密码态；没输入就离开则恢复 ****
OaonesSecret.bind(byId("appSecret"));
</script>
