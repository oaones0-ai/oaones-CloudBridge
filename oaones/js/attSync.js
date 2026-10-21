/**
 * 考勤同步页面脚本（独立一级菜单 /oaones/attSync/attSync.jsp，三个平台共用）。
 *
 * 页面三个页签（切到哪个载哪个）：
 *   同步设置：该租户的卡片——租户级开关 + **考勤应用（与租户绑定，选完立刻保存并校验）** + 设置行列表；
 *   同步日志：批次列表（一次执行一行），行尾「明细」看这一批的打卡明细（失败 / 跳过可重推）；
 *   打卡明细：全部明细的平铺列表（同一份数据，按人员 / 结果 / 打卡时间 / 关键字筛）。
 *
 * **租户页签是页面级的、且排在功能页签前面**（页壳里的 #attTenantTabs，2026-10-15 现场反馈）：
 * 先选租户、再看功能——一个租户一个页签、一屏只看一个租户，下面那排
 * 「同步设置 / 同步日志 / 打卡明细」都跟着当前选中的租户走（日志 / 明细按它筛，不再各自放租户下拉）。
 *
 * 编辑弹框里：范围三要素（分部 / 部门 / 人员，多选弹框、选中含下级、候选按 OA 分权过滤）、
 * 数据类型与打卡时段、时间口径、计划任务（按时间段 / 高级 cron，可「预测后 10 次」）；
 * 考勤应用只读展示（在租户卡片上维护）。
 *
 * 组件一律用公共件：OaonesToast（提示 / 确认）、OaonesTable（分页）、OaonesDate（日期时间框）、
 * OaonesHrm（人员超链接）、OaonesPicker（弹框拖动）。
 */
(function (window, document) {
    "use strict";

    var TYPE = window.OAONES_ATT_TYPE || 0;
    var state = {
        loaded: { setting: false, log: false, item: false },
        tab: "setting",
        tenants: [],
        rows: [],
        groups: [],
        logRows: [],
        itemRows: [],
        logPager: null,
        itemPager: null,
        activeTenant: "",
        scope: { subs: [], depts: [], users: [] },
        subOptions: [],
        deptOptions: [],
        userOptions: [],
        editing: { id: 0, cfgId: 0, items: [] }
    };

    function byId(id) { return document.getElementById(id); }

    function esc(v) {
        if (v === null || v === undefined) { return ""; }
        return String(v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function toast(msg) {
        if (window.OaonesToast) { window.OaonesToast.warn(msg); }
    }

    function confirmBox(msg, okText, cb) {
        if (window.OaonesToast && window.OaonesToast.confirm) {
            window.OaonesToast.confirm(msg, cb, { okText: okText });
            return;
        }
        cb();
    }

    function call(action, params, cb) {
        window.Oaones.api(action, params || {}, function (json) {
            if (!json || json.code !== 0) {
                if (json && !json.system && json.msg) { toast(json.msg); }
                if (cb) { cb(null, json); }
                return;
            }
            if (cb) { cb(json.data || {}, json); }
        });
    }

    function merge(base, extra) {
        var out = {};
        Object.keys(base || {}).forEach(function (key) { out[key] = base[key]; });
        Object.keys(extra || {}).forEach(function (key) { out[key] = extra[key]; });
        return out;
    }

    function openModal(id) {
        var modal = byId(id);
        if (modal) { modal.style.display = "block"; }
    }

    function closeModal(id) {
        var modal = byId(id);
        if (modal) { modal.style.display = "none"; }
    }

    function splitIds(value) {
        var out = [];
        String(value || "").split(",").forEach(function (one) {
            var id = Number(String(one).trim());
            if (id > 0 && out.indexOf(id) < 0) { out.push(id); }
        });
        return out;
    }

    /* ------------------------------------------------------------------
     * 一、同步设置
     * ------------------------------------------------------------------ */

    /** 取「租户 + 设置行 + 工作日考勤组」（租户页签是页面级的，切到哪个页签都要先有它）。 */
    function loadSetting(cb) {
        call("attsync.list", { type: TYPE }, function (data) {
            state.tenants = data.tenants || [];
            state.rows = data.rows || [];
            state.groups = data.groups || [];
            state.loaded.setting = true;
            renderTenantTabs();
            renderSetting();
            fillWorkdayGroups(data.groups || [], data.today_workday);
            if (cb) { cb(); }
        });
        loadScheduler();
    }

    /**
     * 租户页签（**页面级**，页壳里的 #attTenantTabs）+ 日志 / 明细工具条上的「当前租户」提示。
     *
     * 三个页签共用同一个选中租户（2026-10-15 现场反馈）：一屏只看一个租户，
     * 切租户只换显示并按当前页签重取该页签的数据。
     */
    function renderTenantTabs() {
        var tabs = byId("attTenantTabs");
        var logTip = byId("attLogTenant");
        var itemTip = byId("attItemTenant");
        if (!state.tenants.length) {
            if (tabs) { tabs.innerHTML = '<span class="notice">没有你被指定为维护人的租户。</span>'; }
            if (logTip) { logTip.textContent = "—"; }
            if (itemTip) { itemTip.textContent = "—"; }
            return;
        }
        if (!activeTenant()) {
            state.activeTenant = String(state.tenants[0].cfg_id);
        }
        var name = (activeTenant() || {}).tenant_name || "";
        if (logTip) { logTip.textContent = name; }
        if (itemTip) { itemTip.textContent = name; }
        if (!tabs) { return; }
        var html = "";
        for (var i = 0; i < state.tenants.length; i++) {
            var tenant = state.tenants[i];
            var on = String(tenant.cfg_id) === String(state.activeTenant);
            html += '<button type="button" data-att-action="tenant-tab" data-cfg-id="'
                + esc(tenant.cfg_id) + '"' + (on ? ' class="active"' : "") + '>'
                + esc(tenant.tenant_name)
                + (String(tenant.tenant_enabled) === "1" ? "" : "（已停用）")
                + '</button>';
        }
        tabs.innerHTML = html;
    }

    function loadScheduler() {
        call("attsync.scheduler", {}, function (data) {
            var tip = byId("attSchedTip");
            if (!tip) { return; }
            if (!data.enabled) {
                tip.textContent = "考勤同步功能总开关已关闭（oaones.properties#att.sync.enable=0）："
                    + "不执行任何自动同步，手工执行也禁用。";
                return;
            }
            var text = "内置调度器" + (data.started ? "已启动" : "未启动")
                + "：每 " + (data.tick_seconds || 60) + " 秒检查一次各条设置的计划任务是否到点；"
                + "到点的设置自己跑一轮，多条设置互不影响。";
            if (data.last_run) {
                text += "最近一轮：" + data.last_run
                    + (data.last_message ? "（" + data.last_message + "）" : "") + "。";
            } else if (data.last_tick) {
                text += "最近检查：" + data.last_tick
                    + (data.last_message ? "（" + data.last_message + "）" : "") + "。";
            }
            tip.textContent = text;
        });
    }

    /**
     * 「同步设置」：租户页签 + 当前选中租户的卡片。
     *
     * 一个页面只显示一个租户的设置（2026-10-15 现场反馈）：租户排成一行页签，
     * 点哪个看哪个；页签里带上租户的启停状态，一眼能看出哪个租户是开着的。
     */
    function renderSetting() {
        var box = byId("attTenantBox");
        if (!box) { return; }
        if (!state.tenants.length) {
            box.innerHTML = '<div class="empty">没有你被指定为维护人的租户。</div>';
            return;
        }
        var current = tenantOf(state.activeTenant);
        box.innerHTML = current ? tenantCardHtml(current) : "";
    }

    /** 当前选中的租户（页签对应的那个）。 */
    function activeTenant() {
        return tenantOf(state.activeTenant);
    }

    function tenantOf(cfgId) {
        for (var i = 0; i < state.tenants.length; i++) {
            if (String(state.tenants[i].cfg_id) === String(cfgId)) { return state.tenants[i]; }
        }
        return null;
    }

    function tenantCardHtml(tenant) {
        var enabled = String(tenant.tenant_enabled) === "1";
        var rows = "";
        for (var r = 0; r < state.rows.length; r++) {
            if (String(state.rows[r].cfg_id) === String(tenant.cfg_id)) {
                rows += cfgRowHtml(state.rows[r]);
            }
        }
        if (rows.length === 0) {
            rows = '<div class="empty">该租户还没有考勤同步设置：点右上角「新增同步设置」加一条。</div>';
        }
        return ''
            + '<div class="cfg-item" data-att-tenant="' + esc(tenant.cfg_id) + '">'
            + '<div class="cfg-item-head">'
            + '<div class="cfg-item-name">' + esc(tenant.tenant_name) + '</div>'
            + '<div class="list-row-actions">'
            + '<span class="tag' + (enabled ? " success" : "") + '">'
            + (enabled ? "租户已开启" : "租户已停用") + '</span>'
            + '<button class="link" data-att-action="add-cfg" data-cfg-id="' + esc(tenant.cfg_id)
            + '">新增同步设置</button>'
            + '<button class="link" data-att-action="tenant-state" data-cfg-id="' + esc(tenant.cfg_id)
            + '" data-enabled="' + (enabled ? "0" : "1") + '">' + (enabled ? "整体停用" : "整体启用") + '</button>'
            + '</div></div>'
            + '<div class="cfg-item-sub">企业标识 ' + esc(tenant.corp_id)
            + '（租户ID ' + esc(tenant.cfg_id) + '）</div>'
            + tenantAppHtml(tenant)
            + rows
            + '<div class="notice" id="attPushNote" style="margin-top:8px;">'
            + '反向同步（OA → 第三方）当前方式：' + esc(tenant.push_note || "—") + '</div>'
            + '</div>';
    }

    /**
     * 租户的「考勤应用」一行：与租户绑定（2026-10-15 现场反馈），选完**立刻保存并校验**
     * （后端 attsync.tenantApp 一步做掉保存 + 真调平台接口），不用等保存某一条同步设置。
     */
    function tenantAppHtml(tenant) {
        var apps = tenant.apps || [];
        var appId = Number(tenant.app_id) || 0;
        var canUse = false;
        var html = '<option value="0">（未指定，不执行同步）</option>';
        for (var i = 0; i < apps.length; i++) {
            var app = apps[i];
            // 应用是否可用由后端给布尔值 enable 判定（= uf_oaones_app_bind.yx 为 0；
            // 那一列是“0 有效 / 1 无效”，与部门绑定表相反，页面不自己判，免得写反——
            // 2026-10-15 现场：启用中的应用被标成「已停用」）。只有明确 false 才算停用。
            var disabled = app.enable === false;
            if (String(app.id) === String(appId) && !disabled) { canUse = true; }
            html += '<option value="' + esc(app.id) + '"'
                + (String(app.id) === String(appId) ? " selected" : "")
                + (disabled ? " disabled" : "") + ">"
                + esc(app.app_name)
                + "（" + (String(app.secret_ready) === "1" ? "已配 Secret" : "用租户 Secret") + "）"
                + (disabled ? "（已停用）" : "") + "</option>";
        }
        var checkState = Number(tenant.check_state) || 0;
        var tag = checkState === 1 ? '<span class="tag success">已校验通过</span>'
            : (checkState === 2 ? '<span class="tag danger">校验失败</span>' : '<span class="tag">未校验</span>');
        return ''
            + '<div class="form-row" style="margin:10px 0 4px;">'
            + '<label>考勤应用<span class="req-mark">*</span></label>'
            + '<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">'
            + '<select class="oa-select" id="attTenantApp" data-att-tenant-app="' + esc(tenant.cfg_id) + '">'
            + html + '</select>'
            + tag
            + '<button class="link" data-att-action="tenant-recheck" data-cfg-id="' + esc(tenant.cfg_id)
            + '">重新校验</button>'
            + '<span class="notice" id="attTenantAppNote">'
            + esc(appNote(tenant, appId, canUse)) + '</span>'
            + '</div>'
            + '</div>'
            + (String(tenant.check_msg)
                ? '<div class="field-note">最近一次校验：' + esc(tenant.check_time || "") + " "
                    + esc(tenant.check_msg) + '</div>' : "");
    }

    /** 租户应用那一行的提示文案（未指定 / 应用被停用 / 校验结论）。 */
    function appNote(tenant, appId, canUse) {
        if (appId <= 0) {
            return "还没有指定考勤应用：不指定则不执行任何同步，也不会回退到租户的通讯录 Secret。";
        }
        if (!canUse) {
            return "原来指定的应用已被停用或删除，请重新选择。";
        }
        if (Number(tenant.check_state) === 1) {
            return "校验通过：" + (tenant.check_time || "");
        }
        return "改了应用或还没校验过，点「重新校验」真调一次平台考勤接口。";
    }

    function cfgRowHtml(row) {
        var enabled = Number(row.enabled) === 1;
        var alert = row.alert_text ? '<div class="alert-text">' + esc(row.alert_text) + '</div>' : "";
        var lastTag = Number(row.last_result) === 3 ? '<span class="tag danger">失败</span>'
            : (Number(row.last_result) === 0 || Number(row.last_result) === 1
                ? '<span class="tag success">成功</span>' : '<span class="tag">未执行</span>');
        return ''
            + '<div class="cfg-item" data-att-cfg="' + esc(row.id) + '">'
            + '<div class="cfg-item-head">'
            + '<div class="cfg-item-name">' + esc(row.cfg_name)
            + ' <span class="tag">' + esc(row.dir_text) + '</span>'
            + (enabled ? '<span class="tag success">已启用</span>' : '<span class="tag">已停用</span>')
            + '</div>'
            + '<div class="list-row-actions">'
            + '<button class="link" data-att-action="cfg-state" data-id="' + esc(row.id)
            + '" data-enabled="' + (enabled ? "0" : "1") + '">' + (enabled ? "停用" : "启用") + '</button>'
            + '<button class="link" data-att-action="run-now" data-id="' + esc(row.id) + '">立即执行一次</button>'
            + '<button class="link" data-att-action="edit" data-id="' + esc(row.id) + '">编辑</button>'
            + '<button class="link danger" data-att-action="remove" data-id="' + esc(row.id) + '">删除</button>'
            + '</div></div>'
            + '<div class="cfg-item-row"><span>范围</span><b>'
            + esc(row.scope_text || "全部已绑定人员") + '</b></div>'
            + '<div class="cfg-item-row"><span>类型</span><b>' + esc(row.data_text)
            + '；' + esc(row.period_text) + '</b></div>'
            + '<div class="cfg-item-row"><span>时间口径</span><b>' + esc(row.win_text) + '</b></div>'
            + '<div class="cfg-item-row"><span>计划任务</span><b>' + esc(row.rule_text) + '</b></div>'
            + '<div class="cfg-item-row"><span>最近执行</span><b>' + esc(row.last_run_time || "—")
            + ' ' + lastTag + ' ' + esc(row.last_result_text || "")
            + (Number(row.fail_streak) > 0 ? '（连续失败 ' + esc(row.fail_streak) + ' 次）' : "")
            + '</b></div>'
            + '<div class="cfg-item-row"><span>下次执行</span><b>' + esc(row.next_run || "—") + '</b></div>'
            + alert
            + '</div>';
    }

    function showRunResult(data) {
        var lines = [];
        if (data.message) { lines.push(esc(data.message)); }
        lines.push("新增 " + (data.add || 0) + " / 更新 " + (data.update || 0)
            + " / 跳过 " + (data.skip || 0) + " / 失败 " + (data.fail || 0)
            + "，平台请求 " + (data.api || 0) + " 次");
        showResult("执行结果", lines.join("<br>"));
    }

    function showResult(title, html) {
        var t = byId("attResultTitle");
        var b = byId("attResultBody");
        if (t) { t.textContent = title; }
        if (b) { b.innerHTML = html; }
        openModal("attResultModal");
    }

    function runNow(id) {
        var html = ''
            + '<div class="form-row"><label>本次时间口径</label><div>'
            + '<select id="attRunWinMode" class="oa-select">'
            + '<option value="">按设置自己的口径</option>'
            + '<option value="0">临时：当天数据</option>'
            + '<option value="1">临时：前一天数据</option>'
            + '<option value="3">临时：最近 2 天</option>'
            + '</select></div></div>'
            + '<div class="field-note">「立即执行一次」不会改变计划任务的配置与下次执行时间。</div>';
        if (!window.OaonesToast || !window.OaonesToast.confirm) { return; }
        window.OaonesToast.confirm({
            title: "立即执行一次",
            message: html,
            html: true,
            okText: "执行",
            onOk: function () {
                var sel = byId("attRunWinMode");
                var params = { id: id };
                if (sel && sel.value !== "") { params.win_mode = sel.value; }
                call("attsync.runNow", params, function (data) {
                    showRunResult(data);
                    loadSetting();
                });
            }
        });
    }

    function onSettingAction(target) {
        var action = target.getAttribute("data-att-action");
        var id = target.getAttribute("data-id");
        if (action === "tenant-tab") {
            // 切租户页签（页面级）：同步设置只换显示不重新取数；
            // 日志 / 明细是按租户查的，切了要按新租户重取一次
            state.activeTenant = String(target.getAttribute("data-cfg-id") || "");
            renderTenantTabs();
            if (state.tab === "log") { loadLogs(); return; }
            if (state.tab === "item") { loadItems(); return; }
            renderSetting();
            return;
        }
        if (action === "cfg-state") {
            call("attsync.state", { id: id, enabled: target.getAttribute("data-enabled") }, function () {
                loadSetting();
            });
            return;
        }
        if (action === "tenant-state") {
            call("attsync.tenantState", {
                type: TYPE,
                cfg_id: target.getAttribute("data-cfg-id"),
                enabled: target.getAttribute("data-enabled")
            }, function () { loadSetting(); });
            return;
        }
        if (action === "tenant-recheck") {
            recheckTenantApp(target.getAttribute("data-cfg-id"));
            return;
        }
        if (action === "run-now") { runNow(id); return; }
        if (action === "add-cfg") { openNewCfg(target.getAttribute("data-cfg-id")); return; }
        if (action === "edit") { openCfg(id); return; }
        if (action === "remove") {
            confirmBox("确认删除这条同步设置？历史日志会保留。", "删除", function () {
                call("attsync.remove", { id: id }, function () { loadSetting(); });
            });
        }
    }

    /* ------------------------------------------------------------------
     * 一之前：租户的「考勤应用」（与租户绑定；选完立刻保存 + 校验，2026-10-15 现场反馈）
     * ------------------------------------------------------------------ */

    /** 换租户的考勤应用：一步做掉「保存 + 真调一次平台考勤接口」。 */
    function saveTenantApp(cfgId, appId) {
        var sel = byId("attTenantApp");
        var note = byId("attTenantAppNote");
        if (sel) { sel.disabled = true; }
        if (note) { note.textContent = "正在保存并校验（真调一次平台考勤接口，请稍候）…"; }
        call("attsync.tenantApp", { type: TYPE, cfg_id: cfgId, app_id: appId }, function (data) {
            if (sel) { sel.disabled = false; }
            if (data) { showProbeResult(data); }
            loadSetting();
        });
    }

    /** 「重新校验」：用当前应用的凭据再真调一次平台考勤接口。 */
    function recheckTenantApp(cfgId) {
        var note = byId("attTenantAppNote");
        if (note) { note.textContent = "正在校验（真调一次平台考勤接口，请稍候）…"; }
        call("attsync.check", { type: TYPE, cfg_id: cfgId }, function (data) {
            if (data) { showProbeResult(data); }
            loadSetting();
        });
    }

    /** 校验结论（保存应用 / 重新校验 / 开通同步前的当场校验共用一份展示）。 */
    function showProbeResult(data) {
        var ok = !!data.ok;
        setCheckTip(ok, data.message || "");
        showResult(ok ? "考勤应用校验通过" : "考勤应用校验未通过",
            '<div class="' + (ok ? "notice" : "alert-text") + '">' + esc(data.message || "") + "</div>"
            + (data.step ? '<div class="field-note">卡在步骤：' + esc(data.step) + "</div>" : "")
            + (data.groups !== undefined
                ? '<div class="field-note">读到的考勤组数量：' + esc(data.groups) + "</div>" : "")
            + (data.punch_count !== undefined
                ? '<div class="field-note">今日读到打卡数据：' + esc(data.punch_count) + " 条</div>" : ""));
    }

    /** 校验结果的即时反馈（行内 tag 与说明文字随后由 loadSetting 重新渲染）。 */
    function setCheckTip(ok, msg) {
        var note = byId("attTenantAppNote");
        if (note) { note.textContent = msg || ""; }
        if (window.OaonesToast) {
            window.OaonesToast.show("考勤应用校验" + (ok ? "通过" : "未通过"),
                { mode: ok ? 0 : 2 });
        }
    }

    /* ------------------------------------------------------------------
     * 二、编辑弹框（含计划任务与「预测后 10 次」）
     * ------------------------------------------------------------------ */

    function openCfg(id) {
        var row = null;
        for (var i = 0; i < state.rows.length; i++) {
            if (String(state.rows[i].id) === String(id)) { row = state.rows[i]; break; }
        }
        if (!row) { toast("这条同步设置不存在，请刷新后重试"); return; }
        fillCfgForm(row);
        openModal("attCfgModal");
    }

    function openNewCfg(cfgId) {
        fillCfgForm({
            id: 0, cfg_id: cfgId, cfg_name: "", dir: 0,
            scope_subs: "", scope_depts: "", scope_users: "",
            data_types: 1, sign_periods: 3,
            win_mode: 0, win_days: 2,
            plan_mode: 0, workday_group_id: 0, cron_day_type: 0, day_cron: "", night_cron: "",
            enabled: 0, items: [], remark: ""
        });
        openModal("attCfgModal");
    }

    function fillCfgForm(row) {
        state.editing = {
            id: Number(row.id) || 0,
            cfgId: Number(row.cfg_id) || 0,
            items: (row.items || []).slice(0)
        };
        byId("attCfgModalTitle").textContent = state.editing.id > 0 ? "编辑同步设置" : "新增同步设置";
        byId("attCfgId").value = state.editing.id;
        byId("attCfgType").value = TYPE;
        byId("attCfgTenant").value = row.cfg_id;
        byId("attCfgTenantText").textContent = tenantName(row.cfg_id) + "（租户ID " + row.cfg_id + "）";
        byId("attCfgName").value = row.cfg_name || "";
        byId("attCfgDir").value = String(Number(row.dir) || 0);
        byId("attCfgEnabled").checked = Number(row.enabled) === 1;
        byId("attCfgRemark").value = row.remark || "";
        fillCfgAppText(row.cfg_id);

        state.scope.subs = splitIds(row.scope_subs);
        state.scope.depts = splitIds(row.scope_depts);
        state.scope.users = splitIds(row.scope_users);
        ensureOrgOptions(renderScopeTexts);

        var types = Number(row.data_types) || 0;
        byId("attDataNormal").checked = (types & 1) !== 0;
        byId("attDataRemedy").checked = (types & 2) !== 0;
        var periods = Number(row.sign_periods) || 3;
        byId("attPeriodIn").checked = (periods & 1) !== 0;
        byId("attPeriodOut").checked = (periods & 2) !== 0;

        byId("attWinMode").value = String(Number(row.win_mode) || 0);
        byId("attWinDays").value = Number(row.win_days) || 2;
        byId("attWinFrom").value = row.win_from || "";
        byId("attWinTo").value = row.win_to || "";
        syncWinRows();

        byId("attPlanMode").value = String(Number(row.plan_mode) || 0);
        byId("attWorkdayGroup").value = String(Number(row.workday_group_id) || 0);
        byId("attCronDayType").value = String(Number(row.cron_day_type) || 0);
        byId("attDayCron").value = row.day_cron || "";
        byId("attNightCron").value = row.night_cron || "";
        syncPlanMode();
        renderPlanItems();
    }

    function tenantName(cfgId) {
        for (var i = 0; i < state.tenants.length; i++) {
            if (String(state.tenants[i].cfg_id) === String(cfgId)) { return state.tenants[i].tenant_name; }
        }
        return "#" + cfgId;
    }

    /** 弹框里的「考勤应用」只读展示（应用与租户绑定，在租户卡片上维护）。 */
    function fillCfgAppText(cfgId) {
        var text = byId("attCfgAppText");
        if (!text) { return; }
        var tenant = tenantOf(cfgId);
        var appId = tenant ? Number(tenant.app_id) || 0 : 0;
        if (appId <= 0) {
            text.textContent = "该租户还没有指定考勤应用——先关掉弹框，在租户卡片上选一个（不指定则不执行同步）";
            return;
        }
        var name = tenant.app_name || ("#" + appId);
        var checkState = Number(tenant.check_state) || 0;
        text.textContent = name + "（"
            + (checkState === 1 ? "已校验通过" : (checkState === 2 ? "校验失败，请重新校验" : "未校验"))
            + "；换应用在租户卡片上操作）";
    }

    function syncWinRows() {
        var mode = Number(byId("attWinMode").value);
        byId("attWinDaysRow").style.display = mode === 3 ? "" : "none";
        byId("attWinRangeRow").style.display = mode === 2 ? "" : "none";
    }

    function syncPlanMode() {
        var mode = Number(byId("attPlanMode").value);
        byId("attPlanItemsBox").style.display = mode === 0 ? "" : "none";
        byId("attPlanCronBox").style.display = mode === 1 ? "" : "none";
    }

    function fillWorkdayGroups(groups, todayWorkday) {
        var sel = byId("attWorkdayGroup");
        if (!sel) { return; }
        var html = '<option value="0">不指定（按周一至周五）</option>';
        for (var i = 0; i < groups.length; i++) {
            html += '<option value="' + esc(groups[i].id) + '">' + esc(groups[i].name) + "</option>";
        }
        sel.innerHTML = html;
        var note = byId("attWorkdayNote");
        if (note && typeof todayWorkday === "boolean") {
            note.textContent = "今天按泛微标准考勤算：" + (todayWorkday ? "工作日" : "非工作日");
        }
    }

    function dayTypeOptions(current) {
        var list = [{ v: 0, t: "每天" }, { v: 1, t: "工作日" }, { v: 2, t: "非工作日" }];
        var html = "";
        for (var i = 0; i < list.length; i++) {
            html += '<option value="' + list[i].v + '"' + (current === list[i].v ? " selected" : "")
                + ">" + list[i].t + "</option>";
        }
        return html;
    }

    function renderPlanItems() {
        var body = byId("attPlanItemBody");
        if (!body) { return; }
        var items = state.editing.items || [];
        if (!items.length) {
            body.innerHTML = '<tr><td colspan="6" class="empty">'
                + '还没有时间段：点左上角「+ 新增时间段」加一行。</td></tr>';
            return;
        }
        var html = "";
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            html += "<tr>"
                + '<td><select class="oa-select" style="width:100%;min-width:0;" data-att-item="day_type" data-index="' + i + '">'
                + dayTypeOptions(Number(item.day_type) || 0) + "</select></td>"
                + '<td><input type="text" data-att-item="start_time" data-index="' + i
                + '" data-oaones-date="time" style="width:100%" value="' + esc(item.start_time || "09:00") + '"></td>'
                + '<td><input type="text" data-att-item="end_time" data-index="' + i
                + '" data-oaones-date="time" style="width:100%" value="' + esc(item.end_time || "18:00") + '"></td>'
                + '<td><input type="number" min="5" max="1440" step="1" style="width:100%;min-width:0;"'
                + ' data-att-item="interval_min" data-index="'
                + i + '" value="' + esc(Number(item.interval_min) || 30) + '"></td>'
                + '<td class="muted">轮询从开始时间起算；结束时间早于开始时间表示跨天。</td>'
                + '<td><button class="link danger" data-att-plan-remove="' + i + '">删除</button></td>'
                + "</tr>";
        }
        body.innerHTML = html;
        if (window.OaonesDate && window.OaonesDate.init) { window.OaonesDate.init(); }
        var fields = body.querySelectorAll("[data-att-item]");
        for (var f = 0; f < fields.length; f++) {
            fields[f].addEventListener("change", onPlanItemChange);
            fields[f].addEventListener("input", onPlanItemChange);
        }
        var removes = body.querySelectorAll("[data-att-plan-remove]");
        for (var r = 0; r < removes.length; r++) {
            removes[r].addEventListener("click", onPlanItemRemove);
        }
    }

    function onPlanItemRemove() {
        var index = Number(this.getAttribute("data-att-plan-remove"));
        state.editing.items.splice(index, 1);
        renderPlanItems();
    }

    function onPlanItemChange() {
        var index = Number(this.getAttribute("data-index"));
        var field = this.getAttribute("data-att-item");
        if (!state.editing.items[index]) { return; }
        state.editing.items[index][field] = this.value;
    }

    function readPlanItems() {
        var out = [];
        var items = state.editing.items || [];
        for (var i = 0; i < items.length; i++) {
            out.push({
                day_type: Number(items[i].day_type) || 0,
                start_time: items[i].start_time || "",
                end_time: items[i].end_time || "",
                interval_min: Number(items[i].interval_min) || 0
            });
        }
        return out;
    }

    function saveCfg() {
        var dataTypes = 0;
        if (byId("attDataNormal").checked) { dataTypes += 1; }
        if (byId("attDataRemedy").checked) { dataTypes += 2; }
        if (dataTypes === 0) { toast("请至少勾选一种数据类型（正常打卡 / 补签卡）"); return; }
        var periods = 0;
        if (byId("attPeriodIn").checked) { periods += 1; }
        if (byId("attPeriodOut").checked) { periods += 2; }
        if (periods === 0) { toast("请至少勾选一个打卡时段（签到 / 签退）"); return; }
        var name = String(byId("attCfgName").value || "").trim();
        if (!name) { toast("请填写设置名称"); return; }
        call("attsync.save", {
            id: Number(byId("attCfgId").value) || 0,
            type: TYPE,
            cfg_id: byId("attCfgTenant").value,
            cfg_name: name,
            dir: byId("attCfgDir").value,
            enabled: byId("attCfgEnabled").checked ? 1 : 0,
            scope_subs: state.scope.subs.join(","),
            scope_depts: state.scope.depts.join(","),
            scope_users: state.scope.users.join(","),
            data_types: dataTypes,
            sign_periods: periods,
            win_mode: byId("attWinMode").value,
            win_days: byId("attWinDays").value,
            win_from: byId("attWinFrom").value,
            win_to: byId("attWinTo").value,
            plan_mode: byId("attPlanMode").value,
            workday_group_id: byId("attWorkdayGroup").value,
            cron_day_type: byId("attCronDayType").value,
            day_cron: byId("attDayCron").value,
            night_cron: byId("attNightCron").value,
            remark: byId("attCfgRemark").value,
            items: JSON.stringify(readPlanItems())
        }, function (data, json) {
            // 保存失败（校验没过 / 名称重复 / 后端报错）时**不关弹框**，错误提示由 api / toast 统一给出
            if (!json || json.code !== 0) { return; }
            closeModal("attCfgModal");
            loadSetting();
        });
    }

    function previewPlan() {
        call("attsync.planPreview", {
            id: Number(byId("attCfgId").value) || 0,
            plan_mode: byId("attPlanMode").value,
            workday_group_id: byId("attWorkdayGroup").value,
            cron_day_type: byId("attCronDayType").value,
            day_cron: byId("attDayCron").value,
            night_cron: byId("attNightCron").value,
            items: JSON.stringify(readPlanItems())
        }, function (data) {
            var runs = data.runs || [];
            var labels = data.labels || [];
            var html = "";
            for (var i = 0; i < runs.length; i++) {
                html += "<tr><td>" + esc(runs[i]) + "</td><td>" + esc(labels[i] || "") + "</td></tr>";
            }
            byId("attPreviewBody").innerHTML = html.length ? html
                : '<tr><td colspan="2" class="empty">当前配置推不出执行时间：'
                + "请检查时间段 / cron 与工作日口径。</td></tr>";
            byId("attPreviewRule").textContent = data.rule_text || "";
            openModal("attPreviewModal");
        });
    }

    /* ------------------------------------------------------------------
     * 三、范围选择（分部 / 部门 / 人员；候选按 OA 分权过滤，选中含下级）
     * ------------------------------------------------------------------ */

    function ensureOrgOptions(cb) {
        if (state.subOptions.length || state.deptOptions.length) {
            if (cb) { cb(); }
            return;
        }
        call("attsync.orgOptions", {}, function (data) {
            state.subOptions = data.oa_subcompanies || [];
            state.deptOptions = data.oa_departments || [];
            if (cb) { cb(); }
        });
    }

    function renderScopeTexts() {
        byId("attScopeSubText").value = namesText(state.subOptions, state.scope.subs) || "全部（未限定分部）";
        byId("attScopeDeptText").value = namesText(state.deptOptions, state.scope.depts) || "全部（未限定部门）";
        byId("attScopeUserText").value = userNamesText(state.scope.users) || "全部（未限定人员）";
        byId("attScopeSubs").value = state.scope.subs.join(",");
        byId("attScopeDepts").value = state.scope.depts.join(",");
        byId("attScopeUsers").value = state.scope.users.join(",");
    }

    function namesText(options, ids) {
        if (!ids.length) { return ""; }
        var names = [];
        for (var i = 0; i < ids.length; i++) {
            var found = "";
            for (var j = 0; j < options.length; j++) {
                if (String(options[j].id) === String(ids[i])) { found = options[j].name; break; }
            }
            names.push(found || ("#" + ids[i]));
        }
        return names.join("、");
    }

    function userNamesText(ids) {
        if (!ids.length) { return ""; }
        var names = [];
        for (var i = 0; i < ids.length; i++) {
            var found = "";
            for (var j = 0; j < state.userOptions.length; j++) {
                if (String(state.userOptions[j].id) === String(ids[i])) {
                    found = state.userOptions[j].lastname || state.userOptions[j].name || "";
                    break;
                }
            }
            names.push(found || ("#" + ids[i]));
        }
        return names.join("、");
    }

    function openPicker(which) {
        ensureOrgOptions(function () {
            snapshotScope();
            var key = PICK_KEYS[which];
            byId(key + "Search").value = "";
            if (window.OaonesPicker && window.OaonesPicker.resetTree) { window.OaonesPicker.resetTree(key); }
            if (which === "users") {
                loadUsers("", function () {
                    renderPick(which);
                    openModal("attUserModal");
                });
                return;
            }
            renderPick(which);
            openModal(which === "subs" ? "attSubModal" : "attDeptModal");
        });
    }

    /* ---- 选择框（与「日程同步」同一套：两列候选 + 单击选中 / 双击确定 + 全选 + 列表式 / 树状） ---- */

    /** 三个选择框共用的 key（与页面 .view-switch[data-view-key] / data-pick-all 一致）。 */
    var PICK_KEYS = { subs: "attSub", depts: "attDept", users: "attUser" };
    /** 打开弹框时的选择快照：点「取消 / ×」回退本次改动（点「确定」/ 双击才留下）。 */
    var pickBackup = null;

    function snapshotScope() {
        pickBackup = {
            subs: state.scope.subs.slice(0),
            depts: state.scope.depts.slice(0),
            users: state.scope.users.slice(0)
        };
    }

    function restoreScope() {
        if (!pickBackup) { return; }
        state.scope.subs = pickBackup.subs.slice(0);
        state.scope.depts = pickBackup.depts.slice(0);
        state.scope.users = pickBackup.users.slice(0);
        pickBackup = null;
        renderScopeTexts();
    }

    function commitScope() { pickBackup = null; }

    function scopeIds(which) {
        if (which === "subs") { return state.scope.subs; }
        return which === "depts" ? state.scope.depts : state.scope.users;
    }

    function setScopeIds(which, ids) {
        if (which === "subs") { state.scope.subs = ids; }
        else if (which === "depts") { state.scope.depts = ids; }
        else { state.scope.users = ids; }
    }

    function scopeOptions(which) {
        if (which === "subs") { return state.subOptions; }
        return which === "depts" ? state.deptOptions : state.userOptions;
    }

    function childMapOf(rows, key) {
        var map = {};
        for (var i = 0; i < rows.length; i++) {
            var parent = String(rows[i][key] == null ? "" : rows[i][key]);
            if (!parent) { continue; }
            (map[parent] = map[parent] || []).push(String(rows[i].id));
        }
        return map;
    }

    function pickChildMap(which) {
        if (which === "subs") { return childMapOf(state.subOptions, "parent"); }
        if (which === "depts") { return childMapOf(state.deptOptions, "parent"); }
        return null;
    }

    function pickPartial(which) {
        if (which === "users" || !window.OaonesPicker) { return {}; }
        return window.OaonesPicker.partialSelected(scopeIds(which).map(String),
            function () { return pickChildMap(which); });
    }

    /** 候选第一列（标识）：分部 / 部门是 ID，人员是登录名（退回工号 / ID）。 */
    function pickRowId(which, row) {
        if (which === "users") { return row.loginid || row.workcode || row.id; }
        return row.id;
    }

    /** 候选第二列（名称）：人员带上所属部门，便于区分同名。 */
    function pickRowName(which, row) {
        if (which === "users") {
            return (row.lastname || row.name || "")
                + (row.departmentname ? "（" + row.departmentname + "）" : "");
        }
        return row.name || row.fullname || "";
    }

    function pickRowHits(which, row, keyword) {
        if (!keyword) { return true; }
        var fields = which === "users"
            ? [row.id, row.loginid, row.lastname, row.name, row.workcode, row.departmentname]
            : [row.id, row.code, row.name, row.fullname];
        for (var i = 0; i < fields.length; i++) {
            if (String(fields[i] == null ? "" : fields[i]).toLowerCase().indexOf(keyword) >= 0) { return true; }
        }
        return false;
    }

    /** 当前候选（已按关键字与前置条件过滤）：部门候选只列已选分部（含下级）下的部门。 */
    function pickRows(which) {
        var keyword = String(byId(PICK_KEYS[which] + "Search").value || "").trim().toLowerCase();
        var allowed = which === "depts" ? allowedDeptIds() : null;
        var options = scopeOptions(which);
        var rows = [];
        for (var i = 0; i < options.length; i++) {
            if (allowed && allowed.indexOf(String(options[i].id)) < 0) { continue; }
            if (!pickRowHits(which, options[i], keyword)) { continue; }
            rows.push(options[i]);
        }
        return rows;
    }

    function pickOptionHtml(which, row, partial) {
        var id = String(row.id);
        var active = scopeIds(which).indexOf(Number(id)) >= 0;
        var cls = "wx-pick-option" + (active ? " active" : (!active && partial[id] ? " partial" : ""));
        var tip = (active ? "点击取消选择" : "点击选择") + "；双击即确定并退出｜" + pickRowName(which, row);
        return '<button type="button" class="' + cls + '" data-att-pick="' + esc(id) + '"'
            + ' title="' + esc(tip) + '">'
            + "<span>" + esc(pickRowId(which, row)) + "</span>"
            + '<span class="wx-pick-opt-name">' + esc(pickRowName(which, row)) + "</span>"
            + "</button>";
    }

    function pickHeadHtml(which) {
        var first = which === "users" ? "登录名" : "ID";
        var second = which === "users" ? "姓名（部门）" : (which === "subs" ? "分部" : "部门");
        return '<div class="wx-pick-head"><span>' + first + "</span><span>" + second + "</span></div>";
    }

    function pickEmptyText(which) {
        if (which === "users") { return "没有匹配的人员（候选按泛微标准分权过滤）。"; }
        if (which === "depts") { return "没有匹配的部门（可能是开了分权，或该分部（含下级）下没有部门）。"; }
        return "没有匹配的分部（可能是开了 OA 分权）。";
    }

    function pickNoteText(which) {
        if (which === "users") {
            return "已选 " + state.scope.users.length + " 人（候选按泛微标准分权过滤）";
        }
        var size = scopeIds(which).length;
        if (which === "subs") {
            return "已选 " + size + " 个分部（自动含其全部下级分部 / 部门）";
        }
        return "已选 " + size + " 个部门（自动含其全部下级部门）；"
            + (state.scope.subs.length ? "候选已按已选分部（含下级）过滤。" : "未选分部时列出全部部门。");
    }

    function renderPick(which) {
        var key = PICK_KEYS[which];
        var list = byId(key + "List");
        if (!list) { return; }
        var rows = pickRows(which);
        var partial = pickPartial(which);
        if (window.OaonesPicker && window.OaonesPicker.mode(key) === "tree") {
            list.className = "wx-pick-list is-tree";
            list.innerHTML = window.OaonesPicker.renderTree(key, rows, {
                parentOf: function (row) { return row.parent; },
                textOf: function (row) { return pickRowName(which, row); },
                keyword: String(byId(key + "Search").value || "").trim(),
                emptyText: pickEmptyText(which),
                optionHtml: function (row) { return pickOptionHtml(which, row, partial); }
            });
        } else {
            list.className = "wx-pick-list";
            var html = rows.length ? pickHeadHtml(which) : "";
            for (var i = 0; i < rows.length; i++) { html += pickOptionHtml(which, rows[i], partial); }
            list.innerHTML = html || '<div class="empty">' + esc(pickEmptyText(which)) + "</div>";
        }
        byId(key + "Note").textContent = pickNoteText(which);
        syncPickAllState(which);
    }

    /** 单击后只就地刷新选中态（不重建列表），保证双击仍命中同一行。 */
    function refreshPickDom(which) {
        var list = byId(PICK_KEYS[which] + "List");
        if (!list) { return; }
        var picked = scopeIds(which);
        var partial = pickPartial(which);
        var options = list.querySelectorAll(".wx-pick-option");
        for (var i = 0; i < options.length; i++) {
            var id = String(options[i].getAttribute("data-att-pick"));
            var active = picked.indexOf(Number(id)) >= 0;
            options[i].classList.toggle("active", active);
            options[i].classList.toggle("partial", !active && !!partial[id]);
        }
        byId(PICK_KEYS[which] + "Note").textContent = pickNoteText(which);
        syncPickAllState(which);
    }

    function pickAllIds(which) {
        var rows = pickRows(which);
        var ids = [];
        for (var i = 0; i < rows.length; i++) { ids.push(String(rows[i].id)); }
        return ids;
    }

    function allPicked(which, ids) {
        if (!ids.length) { return false; }
        var picked = scopeIds(which);
        for (var i = 0; i < ids.length; i++) {
            if (picked.indexOf(Number(ids[i])) < 0) { return false; }
        }
        return true;
    }

    function syncPickAllState(which) {
        if (!window.OaonesPicker || !window.OaonesPicker.syncSelectAll) { return; }
        window.OaonesPicker.syncSelectAll(PICK_KEYS[which], allPicked(which, pickAllIds(which)));
    }

    /** 「全选 / 全不选」：只作用于当前列表里的候选（搜索 / 前置条件过滤后的那批）。 */
    function togglePickAll(which) {
        var ids = pickAllIds(which);
        var on = allPicked(which, ids);
        var picked = scopeIds(which);
        if (on) {
            var drop = {};
            ids.forEach(function (id) { drop[id] = true; });
            setScopeIds(which, picked.filter(function (id) { return !drop[String(id)]; }));
        } else {
            var next = picked.slice(0);
            ids.forEach(function (id) {
                if (next.indexOf(Number(id)) < 0) { next.push(Number(id)); }
            });
            setScopeIds(which, next);
        }
        afterScopeChange(which);
        renderPick(which);
        return allPicked(which, pickAllIds(which));
    }

    function togglePick(which, id) {
        var picked = scopeIds(which);
        var index = picked.indexOf(Number(id));
        if (index >= 0) { picked.splice(index, 1); } else { picked.push(Number(id)); }
        afterScopeChange(which);
    }

    /** 改完某个维度后：分部一变就裁掉不再允许的部门，并刷新表单上三个只读回显。 */
    function afterScopeChange(which) {
        if (which === "subs") {
            var allowed = allowedDeptIds();
            if (allowed) {
                state.scope.depts = state.scope.depts.filter(function (id) {
                    return allowed.indexOf(String(id)) >= 0;
                });
            }
        }
        renderScopeTexts();
    }

    /** 部门候选的前置条件：已选分部（含下级）下的部门（与后端同一口径）。 */
    function allowedDeptIds() {
        if (!state.scope.subs.length) { return null; }
        var subIds = {};
        state.scope.subs.forEach(function (id) { subIds[String(id)] = true; });
        var grew = true;
        while (grew) {
            grew = false;
            state.subOptions.forEach(function (option) {
                if (subIds[String(option.parent)] && !subIds[String(option.id)]) {
                    subIds[String(option.id)] = true;
                    grew = true;
                }
            });
        }
        var allowed = [];
        state.deptOptions.forEach(function (option) {
            if (subIds[String(option.subcompanyid1)]) { allowed.push(String(option.id)); }
        });
        return allowed;
    }

    function loadUsers(keyword, cb) {
        call("attsync.oaUsers", { keyword: keyword || "" }, function (data) {
            state.userOptions = data || [];
            if (cb) { cb(); }
        });
    }

    /** 「确定」/ 双击候选行：留下本次选择并关闭弹框。 */
    function applyPick(which) {
        commitScope();
        renderScopeTexts();
        closeModal(which === "subs" ? "attSubModal" : (which === "depts" ? "attDeptModal" : "attUserModal"));
    }

    /** 「清空选择」：只清当前维度，仍留在弹框里可以接着选。 */
    function clearPicked(which) {
        setScopeIds(which, []);
        afterScopeChange(which);
        renderPick(which);
    }

    /** 候选列表：单击选中 / 取消（就地刷新），双击确定并退出。 */
    function bindPickList(which, listId) {
        byId(listId).addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            togglePick(which, btn.getAttribute("data-att-pick"));
            refreshPickDom(which);
        });
        byId(listId).addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            // 双击前已经收到两次单击（选中→取消），这里确保该行处于选中态后再确定
            var id = btn.getAttribute("data-att-pick");
            if (scopeIds(which).indexOf(Number(id)) < 0) {
                togglePick(which, id);
                refreshPickDom(which);
            }
            applyPick(which);
        });
    }

    /* ------------------------------------------------------------------
     * 四、同步日志（批次）与打卡明细
     * ------------------------------------------------------------------ */

    function readFilter(prefix) {
        // 租户由页面级的租户页签决定（日志 / 明细不再各放一个租户下拉）
        var out = { cfgid: state.activeTenant };
        var dir = byId(prefix + "Dir");
        if (dir && dir.value !== "") { out.dir = dir.value; }
        var keyword = byId(prefix + "Keyword");
        if (keyword && keyword.value) { out.keyword = keyword.value; }
        if (prefix === "attLog") {
            var result = byId("attLogResult");
            if (result && result.value !== "") { out.result = result.value; }
            var trigger = byId("attLogTrigger");
            if (trigger && trigger.value !== "") { out.trigger = trigger.value; }
            out.from_time = byId("attLogFrom").value || "";
            out.to_time = byId("attLogTo").value || "";
        } else {
            var op = byId("attItemOp");
            if (op && op.value !== "") { out.op = op.value; }
            out.sign_from = byId("attItemFrom").value || "";
            out.sign_to = byId("attItemTo").value || "";
        }
        return out;
    }

    function loadLogs() {
        call("attsync.logs", merge(readFilter("attLog"), { type: TYPE }), function (data) {
            state.logRows = data.rows || [];
            state.loaded.log = true;
            renderLogs();
            var total = byId("attLogTotal");
            if (total) {
                total.textContent = "共计 " + (data.total || 0) + " 批，当前显示 "
                    + state.logRows.length + " 条";
            }
        });
    }

    function renderLogs() {
        var body = byId("attLogBody");
        if (!body) { return; }
        var html = "";
        for (var i = 0; i < state.logRows.length; i++) {
            var row = state.logRows[i];
            var failed = Number(row.result) === 3;
            html += "<tr" + (failed ? ' class="row-fail"' : "") + ">"
                + "<td>" + esc(row.created) + "</td>"
                + "<td>" + esc(row.tenant_name) + "</td>"
                + "<td>" + esc(row.cfg_name) + "</td>"
                + "<td>" + esc(row.dir_text) + "</td>"
                + "<td>" + esc(row.trigger_text) + "</td>"
                + "<td>" + esc(row.win_text) + '<br><span class="muted">' + esc(row.win_from)
                + " ~ " + esc(row.win_to) + "</span></td>"
                + "<td>" + esc(row.add) + " / " + esc(row.update) + " / " + esc(row.skip)
                + " / " + esc(row.fail) + "</td>"
                + "<td>" + esc(row.api) + " 次</td>"
                + "<td>" + (failed ? '<span class="tag danger">失败</span>'
                    : '<span class="tag success">成功</span>') + "</td>"
                + "<td>" + esc(row.message) + "</td>"
                + '<td><button class="link" data-att-action="log-items" data-log-id="' + esc(row.id)
                + '">明细</button></td>'
                + "</tr>";
        }
        if (!html.length) {
            html = '<tr><td colspan="11" class="empty">没有符合条件的批次。</td></tr>';
        }
        body.innerHTML = html;
    }

    function loadItems() {
        call("attsync.items", merge(readFilter("attItem"), { type: TYPE }), function (data) {
            state.itemRows = data.rows || [];
            state.loaded.item = true;
            renderItems();
            var total = byId("attItemTotal");
            if (total) {
                total.textContent = "共计 " + (data.total || 0) + " 条，当前显示 "
                    + state.itemRows.length + " 条";
            }
        });
    }

    function renderItems() {
        var body = byId("attItemBody");
        if (!body) { return; }
        body.innerHTML = itemRowsHtml(state.itemRows, 10);
    }

    /** 明细行（日志弹框与明细页签共用；colspan 按列数传入）。 */
    function itemRowsHtml(rows, colspan) {
        var html = "";
        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var failed = Number(row.op) === 3;
            var opTag = Number(row.op) === 0 ? '<span class="tag success">新增</span>'
                : (Number(row.op) === 1 ? '<span class="tag">更新</span>'
                    : (Number(row.op) === 2 ? '<span class="tag warning">跳过</span>'
                        : '<span class="tag danger">失败</span>'));
            var canRetry = Number(row.op) === 2 || Number(row.op) === 3;
            html += "<tr" + (failed ? ' class="row-fail"' : "") + ">"
                + '<td><a href="#" data-att-user-link="' + esc(row.oa_user_id) + '">'
                + esc(row.oa_user_name || ("#" + row.oa_user_id)) + "</a></td>"
                + "<td>" + esc(row.wx_user_id) + "</td>"
                + "<td>" + esc(row.sign_datetime) + "</td>"
                + "<td>" + esc(row.sign_type_text) + "</td>"
                + "<td>" + esc(row.data_text) + (row.raw_type ? '<br><span class="muted">'
                    + esc(row.raw_type) + "</span>" : "") + "</td>"
                + "<td>" + opTag + "</td>"
                + "<td>" + esc(row.message) + "</td>"
                + "<td>" + (canRetry ? '<button class="link" data-att-action="retry" data-item-id="'
                    + esc(row.id) + '">重推</button>' : "—") + "</td>"
                + "</tr>";
        }
        return html.length ? html : '<tr><td colspan="' + (colspan || 10)
            + '" class="empty">没有符合条件的打卡明细。</td></tr>';
    }

    function openLogDetail(logId) {
        call("attsync.logs", { type: TYPE, log_id: logId }, function (data) {
            var rows = data.rows || [];
            var head = byId("attLogDetailHead");
            if (head) {
                if (rows.length) {
                    var one = rows[0];
                    head.innerHTML = '<div class="field-note">批次 #' + esc(one.id) + "｜租户 "
                        + esc(one.tenant_name) + "｜设置 " + esc(one.cfg_name) + "｜方向 " + esc(one.dir_text)
                        + "｜触发 " + esc(one.trigger_text) + "｜时间口径 " + esc(one.win_text)
                        + "（" + esc(one.win_from) + " ~ " + esc(one.win_to) + "）<br>范围："
                        + esc(one.scope_text) + "；数据类型：" + esc(one.data_text) + "；耗时 "
                        + esc(one.cost_ms) + " ms；平台请求 " + esc(one.api) + " 次<br>结果："
                        + esc(one.message) + "</div>";
                } else {
                    head.innerHTML = '<div class="field-note">这个批次已经过了保留期（日志已清理）。</div>';
                }
            }
            call("attsync.items", { type: TYPE, log_id: logId }, function (itemData) {
                byId("attLogItemBody").innerHTML = itemRowsHtml(itemData.rows || [], 8);
                openModal("attLogModal");
            });
        });
    }

    function retryItem(itemId) {
        confirmBox("重推这一条打卡？会按「平台 + 租户 + 人员 + 打卡时间 + 类型」重新处理一次，"
            + "并新写一条明细（不覆盖历史）。", "重推", function () {
                call("attsync.retry", { item_id: itemId }, function (data) {
                    showRunResult(data);
                    if (state.loaded.item) { loadItems(); }
                });
            });
    }

    /* ------------------------------------------------------------------
     * 五、事件绑定与对外入口
     * ------------------------------------------------------------------ */

    function bindEvents() {
        byId("attCfgSaveBtn").addEventListener("click", saveCfg);
        byId("attPreviewBtn").addEventListener("click", previewPlan);
        byId("attWinMode").addEventListener("change", syncWinRows);
        byId("attPlanMode").addEventListener("change", syncPlanMode);
        byId("attPlanAddBtn").addEventListener("click", function () {
            state.editing.items = state.editing.items || [];
            state.editing.items.push({ day_type: 1, start_time: "09:00", end_time: "18:00", interval_min: 30 });
            renderPlanItems();
        });
        byId("attScopeSubBtn").addEventListener("click", function () { openPicker("subs"); });
        byId("attScopeDeptBtn").addEventListener("click", function () { openPicker("depts"); });
        byId("attScopeUserBtn").addEventListener("click", function () { openPicker("users"); });
        byId("attSubSearch").addEventListener("input", function () { renderPick("subs"); });
        byId("attDeptSearch").addEventListener("input", function () { renderPick("depts"); });
        byId("attUserSearch").addEventListener("input", function () { renderPick("users"); });
        bindPickList("subs", "attSubList");
        bindPickList("depts", "attDeptList");
        bindPickList("users", "attUserList");
        byId("attSubOkBtn").addEventListener("click", function () { applyPick("subs"); });
        byId("attDeptOkBtn").addEventListener("click", function () { applyPick("depts"); });
        byId("attUserOkBtn").addEventListener("click", function () { applyPick("users"); });
        byId("attSubClearBtn").addEventListener("click", function () { clearPicked("subs"); });
        byId("attDeptClearBtn").addEventListener("click", function () { clearPicked("depts"); });
        byId("attUserClearBtn").addEventListener("click", function () { clearPicked("users"); });
        byId("attUserSearchBtn").addEventListener("click", function () {
            loadUsers(byId("attUserSearch").value, function () { renderPick("users"); });
        });
        if (window.OaonesPicker) {
            window.OaonesPicker.register("attSub", function () { renderPick("subs"); });
            window.OaonesPicker.register("attDept", function () { renderPick("depts"); });
            window.OaonesPicker.setSelectAll("attSub", function () { return togglePickAll("subs"); });
            window.OaonesPicker.setSelectAll("attDept", function () { return togglePickAll("depts"); });
            window.OaonesPicker.setSelectAll("attUser", function () { return togglePickAll("users"); });
        }
        byId("attLogQueryBtn").addEventListener("click", loadLogs);
        byId("attLogResetBtn").addEventListener("click", function () {
            ["attLogDir", "attLogResult", "attLogTrigger",
                "attLogFrom", "attLogTo", "attLogKeyword"].forEach(function (id) {
                byId(id).value = "";
            });
            loadLogs();
        });
        byId("attItemQueryBtn").addEventListener("click", loadItems);
        byId("attItemResetBtn").addEventListener("click", function () {
            ["attItemDir", "attItemOp", "attItemFrom", "attItemTo",
                "attItemKeyword"].forEach(function (id) {
                byId(id).value = "";
            });
            loadItems();
        });
        var tenantBox = byId("attTenantBox");
        tenantBox.addEventListener("click", function (e) {
            var target = e.target.closest("[data-att-action]");
            if (target) { onSettingAction(target); }
        });
        // 租户的「考勤应用」：换一个就是「保存 + 立刻校验」（一步做掉，2026-10-15 现场反馈）
        tenantBox.addEventListener("change", function (e) {
            var sel = e.target.closest("[data-att-tenant-app]");
            if (sel) { saveTenantApp(sel.getAttribute("data-att-tenant-app"), sel.value); }
        });
        // 租户页签（在面板顶部，不在租户卡片里）
        byId("attTenantTabs").addEventListener("click", function (e) {
            var target = e.target.closest("[data-att-action]");
            if (target) { onSettingAction(target); }
        });
        document.addEventListener("click", function (e) {
            var close = e.target.closest("[data-att-close]");
            if (close) {
                var modalId = close.getAttribute("data-att-close");
                // 选择框：点「取消 / ×」回退到打开弹框时的选择（点「确定」或双击才留下本次改动）
                if (modalId === "attSubModal" || modalId === "attDeptModal" || modalId === "attUserModal") {
                    restoreScope();
                }
                closeModal(modalId);
                return;
            }
            var action = e.target.closest("[data-att-action]");
            if (action && action.getAttribute("data-att-action") === "retry") {
                retryItem(action.getAttribute("data-item-id"));
                return;
            }
            var link = e.target.closest("[data-att-user-link]");
            if (link) {
                e.preventDefault();
                var uid = link.getAttribute("data-att-user-link");
                if (uid && Number(uid) > 0 && window.OaonesHrm) {
                    window.open(window.OaonesHrm.userUrl(uid), "_blank");
                }
            }
        });
        document.addEventListener("click", function (e) {
            var action = e.target.closest('[data-att-action="log-items"]');
            if (action) { openLogDetail(action.getAttribute("data-log-id")); }
        });
    }

    window.OaonesAttSync = {
        init: function () {
            state.editing.items = [];
            bindEvents();
            if (window.OaonesDate && window.OaonesDate.init) { window.OaonesDate.init(); }
        },
        /**
         * 切页签。**租户页签是页面级的**：不管切到哪个页签，都要先有租户列表
         * （首次进入时 `attsync.list` 会把租户 + 设置行 + 工作日的考勤组一起取回来），
         * 再按当前租户取该页签的数据。
         */
        switchTo: function (tab) {
            state.tab = tab;
            if (!state.loaded.setting) {
                // 先拿租户（页面级租户页签要它），拿到后再取本页签的数据
                loadSetting(function () {
                    if (tab === "log") { loadLogs(); }
                    else if (tab === "item") { loadItems(); }
                });
                return;
            }
            if (tab === "setting") { renderSetting(); return; }
            if (tab === "log") { loadLogs(); return; }
            loadItems();
        },
        reload: function () {
            var active = document.querySelector("#attTabs button.active");
            var tab = active ? active.getAttribute("data-tab") : "setting";
            if (tab === "setting") { loadSetting(); return; }
            if (tab === "log") { loadLogs(); return; }
            loadItems();
        }
    };
})(window, document);
