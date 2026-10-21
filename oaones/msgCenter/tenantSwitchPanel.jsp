<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  消息设置 → 租户开关

  一行一个租户，**只列当前平台的租户**（企微页只列企微、钉钉页只列钉钉、飞书页只列飞书，
  “当前平台”由页壳的 ?platform=wecom|dingtalk|feishu 决定，与页面右上角的平台切换一致），
  逐个开 / 关“消息通知”：
    * 关闭的租户不再推送 OA 消息（标准消息订阅与外部统一消息接口两条路都跳过，记“已跳过”日志）；
    * 与“同步开关”互不影响：同步照常，只是不发消息；
    * 开关落在租户表复用列 uf_oaones_qywx_config.msg_enable（不新建表）。

  取数：只调当前平台自己的接口入口（企微 /oaones/WeCom/api.jsp、钉钉 /oaones/DingTalk/api.jsp、
  飞书 /oaones/Feishu/api.jsp），用 Oaones.apiAt(base, ...) 显式指定入口，不再合并另外两侧的租户。
--%>
<%
    /* 当前平台：口径与 msgTypePanel.jsp 等面板一致（request 属性优先，其次 platform 参数），默认企微。 */
    String oaonesTenantPlatform = (String) request.getAttribute("oaonesPBPlatform");
    if (oaonesTenantPlatform == null || oaonesTenantPlatform.trim().length() == 0) {
        oaonesTenantPlatform = request.getParameter("platform");
    }
    if (oaonesTenantPlatform == null) { oaonesTenantPlatform = ""; }
    oaonesTenantPlatform = oaonesTenantPlatform.trim().toLowerCase();
    if (!"dingtalk".equals(oaonesTenantPlatform) && !"feishu".equals(oaonesTenantPlatform)) {
        oaonesTenantPlatform = "wecom";
    }
    String oaonesTenantFull = "feishu".equals(oaonesTenantPlatform) ? "飞书"
            : ("dingtalk".equals(oaonesTenantPlatform) ? "钉钉" : "企业微信");
    String oaonesTenantMenuLabel = "feishu".equals(oaonesTenantPlatform) ? "飞书租户设置"
            : ("dingtalk".equals(oaonesTenantPlatform) ? "钉钉租户设置" : "企微租户设置");
    String oaonesTenantApiUrl = "feishu".equals(oaonesTenantPlatform) ? "/oaones/Feishu/api.jsp"
            : ("dingtalk".equals(oaonesTenantPlatform) ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    String oaonesTenantEmptyTip = "暂无租户。请先在「" + oaonesTenantMenuLabel + "」里新增租户。";
%>
<div class="toolbar" style="justify-content:space-between;">
    <div class="toolbar-left">
        <span class="notice">当前平台：<%= oaonesTenantFull %>；按租户开启 / 关闭消息推送，
            关闭后同步照常，只是不再给该租户发消息。</span>
        <span class="help-tip" id="msgTenantHelpTip" data-help="msgTenantHelpText" role="button"
              tabindex="0" title="查看说明" aria-label="查看说明">?</span>
    </div>
    <div>
        <input type="text" id="msgTenantKeyword" placeholder="租户名称 / 企业ID / App ID" autocomplete="off"
               data-lpignore="true" style="min-width:200px;max-width:280px;">
        <button type="button" class="btn small" id="msgTenantSearchBtn">搜索</button>
        <button type="button" class="btn small secondary" id="msgTenantResetBtn">重置</button>
    </div>
</div>
<div class="help-source" id="msgTenantHelpText">
    本页签按<b>租户</b>控制消息通知：<span class="rule">只列当前平台（<%= oaonesTenantFull %>）的租户</span>，
    要开 / 关另一侧的租户用页面右上角的平台切换（企微 / 钉钉 / 飞书），切过去同样只列那一侧的租户。
    逐个开关即可。关闭的租户在推送前置校验里被跳过并记一条“已跳过”的推送日志，
    不会外发、也不需要解绑人员。开关与租户的“同步开关”互相独立：
    <span class="rule">同步照常执行，只是不发消息</span>。
</div>
<p class="notice" id="msgTenantColumnTip" style="display:none;"></p>
<div class="table-wrap">
    <table class="grid" id="msgTenantTable">
        <thead>
        <tr>
            <th style="width:96px;">平台</th>
            <th style="width:70px;">租户ID</th>
            <th>租户名称</th>
            <th>企业标识</th>
            <th style="width:96px;">同步开关</th>
            <th style="width:120px;">消息通知</th>
            <th style="width:150px;">更新时间</th>
        </tr>
        </thead>
        <tbody id="msgTenantBody"></tbody>
    </table>
</div>
<div class="empty" id="msgTenantEmpty" style="display:none;">暂无租户。请先在「<%= oaonesTenantMenuLabel %>」里新增租户。</div>

<script>
(function () {
    "use strict";
    function byId(id) { return document.getElementById(id); }
    function esc(v) { return window.Oaones ? Oaones.esc(v) : String(v == null ? "" : v); }
    function toast(msg, mode) {
        if (window.MsgCenterToast) { window.MsgCenterToast(msg, mode || 0); return; }
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode || 0 }); }
    }
    /**
     * 只取当前平台的租户：平台由页壳（?platform=wecom|dingtalk|feishu）决定，
     * 接口入口对应当前平台（企微 /oaones/WeCom/api.jsp、钉钉 /oaones/DingTalk/api.jsp、
     * 飞书 /oaones/Feishu/api.jsp）；切平台用页面右上角的平台切换，本页签不再合并另外两侧。
     */
    var BASES = [
        { platform: "<%= oaonesTenantPlatform %>", label: "<%= oaonesTenantFull %>", base: "<%= oaonesTenantApiUrl %>" }
    ];
    /** 一条租户都没有时的空状态文案（按当前平台提示该去哪个菜单新增） */
    var EMPTY_TIP = "<%= oaonesTenantEmptyTip %>";
    var rows = [];
    var keyword = "";

    function visibleRows() {
        var k = (keyword || "").trim().toLowerCase();
        if (!k) { return rows; }
        return rows.filter(function (r) {
            return [r.platformLabel, r.cfg_name, r.corp_id, r.cfg_id].join(" ")
                .toLowerCase().indexOf(k) >= 0;
        });
    }

    function render() {
        var list = visibleRows();
        var html = "";
        list.forEach(function (r) {
            var syncOn = String(r.sync_enable) === "1"
                ? "<span class='tag bound'>启用</span>" : "<span class='tag off'>停用</span>";
            var msgOff = String(r.msg_enable) === "0";
            var msgOn = msgOff
                ? "<span class='tag off'>已关闭</span>" : "<span class='tag bound'>已开启</span>";
            // 开关按钮：关闭 → 开启；开启 → 关闭（点一下即时落库，无需二次保存）
            var btn = "<button class='link' onclick='MsgTenantSwitchPanel.toggleById(\""
                + esc(r.platform) + "\",\"" + esc(r.cfg_id) + "\"," + (msgOff ? "true" : "false") + ")'>"
                + (msgOff ? "开启" : "关闭") + "</button>";
            html += "<tr>"
                + "<td>" + esc(r.platformLabel) + "</td>"
                + "<td>" + esc(r.cfg_id) + "</td>"
                + "<td>" + esc(r.cfg_name) + "</td>"
                + "<td>" + esc(r.corp_id) + "</td>"
                + "<td>" + syncOn + "</td>"
                + "<td>" + msgOn + " " + btn + "</td>"
                + "<td>" + esc(r.update_time || "-") + "</td>"
                + "</tr>";
        });
        byId("msgTenantBody").innerHTML = html;
        var emptyEl = byId("msgTenantEmpty");
        emptyEl.style.display = list.length ? "none" : "";
        if (!list.length) {
            emptyEl.textContent = rows.length ? "没有匹配的租户" : EMPTY_TIP;
        }
        if (window.OaonesTable) {
            OaonesTable.resizable(byId("msgTenantTable"), { key: "msgCenter.tenantTable" });
        }
        // 开关列（uf_oaones_qywx_config.msg_enable）还没建时给出运维可执行的提示：
        // 此时开关一律按“已开启”展示与生效，不影响消息推送
        var tipEl = byId("msgTenantColumnTip");
        if (tipEl) {
            var notReady = rows.some(function (r) { return String(r.switch_ready) === "0"; });
            tipEl.style.display = notReady ? "" : "none";
            tipEl.textContent = notReady
                ? "提示：消息通知开关列还没建（uf_oaones_qywx_config.msg_enable），"
                    + "当前所有租户按“已开启”处理；执行 docs/sql_legacy_upgrade/alter_oaones_tenant_msg_enable.sql "
                    + "后刷新本页即可逐个开关。"
                : "";
        }
    }

    /** 取当前平台的租户清单 */
    function load() {
        rows = [];
        var pending = BASES.length;
        BASES.forEach(function (one) {
            Oaones.apiAt(one.base, "msg.tenantList", {}, function (res) {
                if (res && res.code === 0 && res.data) {
                    (res.data || []).forEach(function (r) {
                        rows.push({
                            platform: one.platform,
                            platformLabel: one.label,
                            base: one.base,
                            cfg_id: String(r.cfg_id || ""),
                            cfg_name: r.cfg_name || "",
                            corp_id: r.corp_id || "",
                            sync_enable: r.sync_enable || "",
                            msg_enable: r.msg_enable === "0" ? "0" : "1",
                            switch_ready: String(r.switch_ready || "1"),
                            update_time: r.update_time || ""
                        });
                    });
                } else if (res && res.code !== 0 && !res.system) {
                    // 没有该平台维护权限时接口会拒绝：提示一次（本页签只列当前平台的租户）
                    toast(res.msg || "读取租户清单失败", 1);
                }
                pending--;
                if (pending === 0) {
                    rows.sort(function (a, b) {
                        if (a.platform !== b.platform) {
                            var order = { wecom: 0, dingtalk: 1, feishu: 2 };
                            return (order[a.platform] || 0) - (order[b.platform] || 0);
                        }
                        return Number(a.cfg_id) - Number(b.cfg_id);
                    });
                    render();
                }
            });
        });
    }

    /** 打开/关闭某租户的消息通知 */
    function toggle(r, enable) {
        Oaones.apiAt(r.base, "msg.tenantToggle",
            { cfg_id: r.cfg_id, msg_enable: enable ? "1" : "0" }, function (res) {
                if (!res || res.code !== 0) {
                    toast((res && res.msg) || "保存失败", 2);
                    load();
                    return;
                }
                r.msg_enable = enable ? "1" : "0";
                toast("已" + (enable ? "开启" : "关闭") + "「" + r.cfg_name + "」的消息通知", 0);
                render();
            });
    }

    byId("msgTenantSearchBtn").addEventListener("click", function () {
        keyword = byId("msgTenantKeyword").value || "";
        render();
    });
    byId("msgTenantKeyword").addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
            keyword = byId("msgTenantKeyword").value || "";
            render();
        }
    });
    byId("msgTenantResetBtn").addEventListener("click", function () {
        keyword = "";
        byId("msgTenantKeyword").value = "";
        render();
    });

    /** 供行内按钮调用：按平台 + 租户ID 直接开/关（enable=true 表示要开启） */
    function toggleById(platform, cfgId, enable) {
        var target = null;
        rows.forEach(function (r) {
            if (r.platform === platform && String(r.cfg_id) === String(cfgId)) { target = r; }
        });
        if (target) { toggle(target, !!enable); }
    }

    window.MsgTenantSwitchPanel = { load: load, toggle: toggle, toggleById: toggleById };
})();
</script>
