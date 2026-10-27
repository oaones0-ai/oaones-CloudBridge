<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
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
    // 应用绑定页目前仍是分平台的（飞书应用表结构不同），这里按平台拼它的地址
    String oaonesPBAppBindPath = oaonesPBFs ? "/oaones/appsyn/appBind/Feishu/appBind.jsp"
            : (oaonesPBDd ? "/oaones/appsyn/appBind/DingTalk/appBind.jsp" : "/oaones/appsyn/appBind/WeCom/appBind.jsp");
%>

<%--
  统一消息中心 → 推送配置
  系统参数（总开关/重试/分片/超时/去重/卡片任务保留）+ 推送用平台应用（只读引用“应用绑定”表）。
  应用不在这里维护：平台自建应用与【应用绑定】共用同一行（uf_oaones_appsyn_app_bind），
  本页只做“设为缺省应用”和“连通测试”。
--%>
<div class="help-source" id="msgCfgHelpText">
    参数保存后<strong>即时生效</strong>，无需改配置文件、无需重启：
    推送总开关关闭后所有标准消息只记录拦截日志、不外发；
    “各消息类型独立开关”在“消息类型”页逐条维护（标准消息类型来自泛微标准后台「应用中心 → 消息类型」）。
    调用<%= oaonesPBFull %>用的<strong>企业ID / <%= oaonesPBAppIdLabel %> / Secret 取自下方应用列表</strong>
    ——应用与租户二级菜单的「应用绑定」页（<%= oaonesPBAppBindPath %>）共用同一张表，
    消息中心不再单独维护一套应用配置；
    接收人所在租户有多个应用时，按“消息类型指定应用 → 该租户的应用 → 缺省应用”的顺序选择。
    应用 Secret 留空时回退用租户配置里的 Secret（页面会标注“租户兜底”）。
    <span class="rule">标准消息按“消息ID + 接收人”去重</span>：同一条标准消息被消息中心回调多次
    （多条订阅 / 重复投递）只推第一条，其余记“重复拦截”；同一业务重提 / 加签 / 转办是<b>新消息</b>，
    照常推送（业务单号带 12 位推送流水，推送日志页仍可按业务ID 检索）；
    页面测试推送与外部统一消息接口仍按传入的业务单号去重。
    站内跳转地址会自动包一层【<%= oaonesPBShort %>工作台免登录入口】（避免在<%= oaonesPBFull %>里点开提示“登录超时”），
    环境里已有别的 SSO 覆盖时可配 <code>oaones.appsyn.properties#msg.oa.login.entry=0</code> 关闭。
</div>

<div class="toolbar">
    <label style="font-size:15px;font-weight:600;">推送参数</label>
    <span class="help-tip" data-help="msgCfgHelpText" role="button" tabindex="0"
          title="查看参数说明" aria-label="查看参数说明">?</span>
    <span style="flex:1;"></span>
    <span class="notice" id="msgCfgQueue"></span>
    <button class="btn" id="msgCfgSaveBtn">保存参数</button>
</div>

<div id="msgCfgForm">
    <div class="form-row">
        <label>推送总开关</label>
        <div class="field" style="max-width:260px;">
            <select id="msgCfgPushEnable" name="msgCfgPushEnable">
                <option value="1">启用（标准消息推送到<%= oaonesPBFull %>）</option>
                <option value="0">停用（只记录拦截日志，不外发）</option>
            </select>
        </div>
    </div>
    <div class="form-row">
        <label>缺省<%= oaonesPBShort %>应用</label>
        <div class="field" style="max-width:380px;">
            <select id="msgCfgDefaultApp" name="msgCfgDefaultApp"></select>
            <span class="notice">接收人租户没有绑定应用时使用</span>
        </div>
    </div>
    <div class="form-row">
        <label>重试次数</label>
        <div class="field" style="max-width:160px;">
            <input type="text" id="msgCfgRetryTimes" name="msgCfgRetryTimes" autocomplete="off">
        </div>
        <label style="width:120px;text-align:right;padding-right:12px;">重试间隔(秒)</label>
        <div class="field" style="max-width:160px;">
            <input type="text" id="msgCfgRetryInterval" name="msgCfgRetryInterval" autocomplete="off">
        </div>
    </div>
    <div class="form-row">
        <label>单批接收人数</label>
        <div class="field" style="max-width:160px;">
            <input type="text" id="msgCfgShardSize" name="msgCfgShardSize" autocomplete="off">
            <span class="notice"><%= oaonesPBFull %>单接口上限 1000 人，超出自动分片</span>
        </div>
    </div>
    <div class="form-row">
        <label>接口超时(毫秒)</label>
        <div class="field" style="max-width:160px;">
            <input type="text" id="msgCfgTimeout" name="msgCfgTimeout" autocomplete="off">
        </div>
        <label style="width:150px;text-align:right;padding-right:12px;">token 提前刷新(秒)</label>
        <div class="field" style="max-width:160px;">
            <input type="text" id="msgCfgTokenAdvance" name="msgCfgTokenAdvance" autocomplete="off">
        </div>
    </div>
    <div class="form-row">
        <label>幂等去重窗口(秒)</label>
        <div class="field" style="max-width:160px;">
            <input type="text" id="msgCfgDedupWindow" name="msgCfgDedupWindow" autocomplete="off">
            <span class="notice">标准消息按“消息ID + 接收人”去重（同一条消息多次投递只推一次）；
                页面测试推送与外部接口按“消息类型 + 业务单号”去重</span>
        </div>
    </div>
    <div class="form-row">
        <label>卡片任务保留(天)</label>
        <div class="field" style="max-width:160px;">
            <input type="text" id="msgCfgTaskKeep" name="msgCfgTaskKeep" autocomplete="off">
            <span class="notice">0 = 永久保留；日志保留统一在「系统设置 → 日志保留」维护</span>
        </div>
    </div>
    <div class="form-row">
        <label>备注</label>
        <div class="field">
            <input type="text" id="msgCfgRemark" name="msgCfgRemark" autocomplete="off">
        </div>
    </div>
    <div class="form-row">
        <label>OA 访问地址</label>
        <div class="field">
            <input type="text" id="msgCfgOaBase" autocomplete="off" readonly
                   style="background:var(--oa-g-25);" placeholder="自动取后台系统设置里的 OA 网址">
            <span class="notice" id="msgCfgOaBaseTip"></span>
        </div>
    </div>
</div>

<div class="toolbar" style="margin-top:20px;">
    <label style="font-size:15px;font-weight:600;"><%= oaonesPBFull %>应用（推送用）</label>
    <span class="help-tip inline" data-help="msgCfgAppHelpText" role="button" tabindex="0"
          title="查看应用说明" aria-label="查看应用说明">?</span>
    <div class="help-source" id="msgCfgAppHelpText">
        这里列出的就是租户二级菜单「应用绑定」页里维护的<%= oaonesPBShort %>自建应用
        （<strong>同一张表 uf_oaones_appsyn_app_bind</strong>）：
        一行 = 一个自建应用，应用名称 / <%= oaonesPBAppIdLabel %> / 应用 Secret / 所属租户 / 启停 都在应用绑定页维护，
        消息中心只引用，不重复维护。
        <span class="rule">新增、修改密钥、停用应用请到「应用绑定」页操作</span>，
        本页只能“设为缺省应用”和“测试”（用该应用的有效密钥获取 access_token）。
        应用 Secret 留空时回退用租户配置里的 Secret，行内会标注“租户兜底”；
        “工作台入口”列显示该行是否同时配置了<%= oaonesPBShort %>工作台免登录入口。
    </div>
    <span style="flex:1;"></span>
    <button class="btn secondary" id="msgAppGotoBindBtn" title="打开租户二级菜单的「应用绑定」页维护<%= oaonesPBShort %>自建应用">去「应用绑定」页维护</button>
    <button class="btn secondary" id="msgAppReloadBtn">刷新</button>
</div>

<p class="notice" id="msgAppEmptyTip" style="display:none;">
    还没有可用的<%= oaonesPBShort %>应用：请先到租户二级菜单的「应用绑定」页新增一个<%= oaonesPBShort %>自建应用
    （填写所属租户、应用名称、<%= oaonesPBAppIdLabel %>、应用 Secret），
    再回到本页设为缺省应用或给消息类型指定应用。
</p>

<div class="table-wrap">
    <table class="grid" id="msgAppTable">
        <thead>
        <tr>
            <th style="width:150px;">应用名称</th>
            <th style="width:150px;">所属租户</th>
            <th style="width:180px;">企业ID</th>
            <th style="width:90px;"><%= oaonesPBAppIdLabel %></th>
            <th style="width:130px;">应用密钥</th>
            <th style="width:110px;">工作台入口</th>
            <th style="width:80px;">状态</th>
            <th style="width:80px;">缺省</th>
            <th style="width:160px;">操作</th>
        </tr>
        </thead>
        <tbody id="msgAppBody"></tbody>
    </table>
</div>

<script>
"use strict";
(function () {
    /* 面板脚本先于页壳脚本执行：自带本地助手，不依赖页壳函数（见 skill.md 第 7 条） */
    function byId(id) { return document.getElementById(id); }
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function toast(msg, mode) {
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode }); }
    }
    function value(id) { var el = byId(id); return el ? String(el.value == null ? "" : el.value).trim() : ""; }
    function setValue(id, v) { var el = byId(id); if (el) { el.value = v == null ? "" : v; } }

    var apps = [];
    var bound = false;

    /** 缺省应用下拉：只用启用中的应用；缺省应用被停用/删除时按“未指定”展示并提示。 */
    function appOptionHtml(selected) {
        var html = "<option value=\"0\">未指定（按接收人租户匹配，匹配不到则跳过）</option>";
        apps.forEach(function (app) {
            var id = String(app.id);
            html += "<option value=\"" + esc(id) + "\"" + (String(selected) === id ? " selected" : "") + ">"
                + esc(app.appName) + "（" + esc(app.cfgName || ("租户 " + app.cfgId))
                + " / AgentId " + esc(app.agentId) + "）</option>";
        });
        return html;
    }

    function renderApps() {
        var body = byId("msgAppBody");
        if (!body) { return; }
        byId("msgAppEmptyTip").style.display = apps.length ? "none" : "";
        if (!apps.length) {
            body.innerHTML = "<tr><td colspan=\"9\"><div class=\"empty\">还没有企微应用，请先到「应用绑定」页新增</div></td></tr>";
            return;
        }
        var html = "";
        apps.forEach(function (app) {
            var disabled = String(app.yx) !== "0";
            html += "<tr>"
                + "<td>" + esc(app.appName) + "</td>"
                + "<td>" + esc(app.cfgName || ("租户 " + app.cfgId))
                + (String(app.cfgQy) === "0" ? " <span class=\"tag off\">租户停用</span>" : "") + "</td>"
                + "<td>" + esc(app.corpId || "-") + "</td>"
                + "<td>" + esc(app.agentId) + "</td>"
                + "<td>" + (app.secretTail
                    ? esc(app.secretTail) + (app.secretFromCfg ? " <span class=\"tag nobind\">租户兜底</span>" : "")
                    : "<span class=\"tag fail\">未配置</span>") + "</td>"
                + "<td>" + (app.entryConfigured
                    ? "<span class=\"tag bound\">已配置</span>"
                    : "<span class=\"tag off\">仅推送</span>") + "</td>"
                + "<td>" + (disabled ? "<span class=\"tag off\">停用</span>" : "<span class=\"tag bound\">启用</span>") + "</td>"
                + "<td>" + (String(app.isDefault) === "1"
                    ? "<span class=\"tag bound\">缺省</span>" : "<span class=\"tag off\">-</span>") + "</td>"
                + "<td>"
                + (String(app.isDefault) === "1" || disabled
                    ? "<button class=\"btn small secondary\" disabled>设为缺省</button> "
                    : "<button class=\"btn small secondary\" data-act=\"default\" data-id=\"" + esc(app.id) + "\">设为缺省</button> ")
                + "<button class=\"btn small secondary\" data-act=\"test\" data-id=\"" + esc(app.id) + "\">测试</button>"
                + "</td></tr>";
        });
        body.innerHTML = html;
    }

    function loadConfig() {
        Oaones.api("msg.config.get", {}, function (res) {
            if (res.code !== 0) { toast(res.msg || "加载参数失败", 2); return; }
            var data = res.data || {};
            var cfg = data.config || {};
            setValue("msgCfgPushEnable", String(cfg.pushEnable == null ? 1 : cfg.pushEnable));
            setValue("msgCfgRetryTimes", cfg.retryTimes);
            setValue("msgCfgRetryInterval", cfg.retryInterval);
            setValue("msgCfgShardSize", cfg.shardSize);
            setValue("msgCfgTimeout", cfg.timeoutMs);
            setValue("msgCfgTokenAdvance", cfg.tokenAdvanceSec);
            setValue("msgCfgDedupWindow", cfg.dedupWindowSec);
            setValue("msgCfgTaskKeep", cfg.taskKeepDays);
            setValue("msgCfgRemark", cfg.remark);
            byId("msgCfgQueue").textContent = "推送队列：" + (data.queue || "-");
            renderDefaultSelect(cfg.defaultAppId);
            renderOaBase(data.oaBase || {});
        });
    }

    /**
     * OA 访问地址（只读，自动取）：消息里的卡片跳转链接要用它把站内相对路径补成完整网址，
     * " + P_FULL + "要求卡片链接带 http/https 协议头，否则整条消息会被 40127 拒收。
     */
    function renderOaBase(info) {
        var base = String(info.base || "");
        var from = String(info.from || "");
        setValue("msgCfgOaBase", base || "（未取到）");
        var tip = "";
        if (base) {
            tip = "来源：" + (from || "自动取值")
                + "；消息里的卡片链接会自动补成 " + base + "/...";
        } else {
            tip = "没取到 OA 访问地址：系统设置读到的「OA访问地址」为 "
                + (info.systemOaUrl ? "“" + info.systemOaUrl + "”" : "空")
                + "。消息链接前缀只取 后台 系统设置 → 微信设置 → 集成设置 里的「OA访问地址」"
                + "（表 wechat_Set.oaUrl，与 E9 自带企微推送同一项）；"
                + "没维护它时站内相对地址会让卡片丢掉链接（企微 40127），"
                + "请在那里填上 OA 对外地址（如 https://oatest.ghedu.com），保存后打开本页刷新即生效。";
        }
        byId("msgCfgOaBaseTip").textContent = tip;
    }

    function renderDefaultSelect(defaultAppId) {
        byId("msgCfgDefaultApp").innerHTML = appOptionHtml(defaultAppId);
    }

    function loadApps() {
        Oaones.api("msg.app.list", {}, function (res) {
            if (res.code !== 0) { toast(res.msg || "加载应用失败", 2); return; }
            var data = res.data || {};
            apps = data.list || [];
            renderApps();
            renderDefaultSelect(value("msgCfgDefaultApp"));
        });
    }

    function saveConfig() {
        Oaones.api("msg.config.save", {
            push_enable: value("msgCfgPushEnable"),
            default_app_id: value("msgCfgDefaultApp"),
            retry_times: value("msgCfgRetryTimes"),
            retry_interval: value("msgCfgRetryInterval"),
            shard_size: value("msgCfgShardSize"),
            timeout_ms: value("msgCfgTimeout"),
            token_advance_sec: value("msgCfgTokenAdvance"),
            dedup_window_sec: value("msgCfgDedupWindow"),
            task_keep_days: value("msgCfgTaskKeep"),
            remark: value("msgCfgRemark")
        }, function (res) {
            if (res.code !== 0) { toast(res.msg || "保存失败", 2); return; }
            toast("参数已保存，即时生效", 0);
            loadConfig();
            loadApps();
        });
    }

    function setDefault(id) {
        Oaones.api("msg.app.default", { id: id }, function (res) {
            if (res.code !== 0) { toast(res.msg || "设置失败", 2); return; }
            toast("已设为缺省推送应用", 0);
            loadConfig();
            loadApps();
        });
    }

    function testApp(id) {
        Oaones.api("msg.app.test", { id: id }, function (res) {
            if (res.code !== 0) { toast(res.msg || "测试失败", 2); return; }
            var data = res.data || {};
            var msg = data.msg || (data.success ? "鉴权成功" : "鉴权失败");
            if (data.success && data.secretSource) { msg += "（密钥来源：" + data.secretSource + "）"; }
            toast(msg, data.success ? 0 : 2);
        });
    }

    function bindEvents() {
        if (bound) { return; }
        bound = true;
        byId("msgCfgSaveBtn").addEventListener("click", saveConfig);
        byId("msgAppReloadBtn").addEventListener("click", loadApps);
        // “应用绑定”属于「租户设置」菜单的租户工作区：消息设置是**独立的一级菜单**，
        // 不从这里跳到别的菜单去；只有带租户上下文打开本页（历史入口/书签）时才保留这个快捷按钮。
        var navBindBtn = byId("msgAppGotoBindBtn");
        var navCfgId = String(window.OAONES_CFG_ID || "");
        if (navBindBtn) {
            if (navCfgId) {
                navBindBtn.addEventListener("click", function () {
                    window.location.href = "<%= oaonesPBAppBindPath %>?cfg_id=" + navCfgId;
                });
            } else {
                navBindBtn.style.display = "none";
            }
        }
        byId("msgAppBody").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest("button[data-act]") : null;
            if (!btn) { return; }
            var id = btn.getAttribute("data-id");
            var act = btn.getAttribute("data-act");
            if (act === "default") {
                setDefault(id);
            } else if (act === "test") {
                testApp(id);
            }
        });
    }

    window.MsgConfigPanel = {
        load: function () {
            bindEvents();
            loadConfig();
            loadApps();
        }
    };
})();
</script>
