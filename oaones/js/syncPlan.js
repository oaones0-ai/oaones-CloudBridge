/**
 * 同步中心（一级菜单）的「编辑同步设置」模块：**表单 + 紧随其后的同步设置列表**
 * （一行一条规则、首列显示租户名称；列表就在表单下方、同一张卡片里，中间没有单独的板块与说明文字）。
 *
 * 由 syncCenter/{WeCom,DingTalk,Feishu}/sync.jsp 在页尾引入（面板标记见
 * /oaones/syncCenter/syncPlanPanel.jsp，三个平台共用同一份）：
 *   * 页面为每个平台设好 window.OAONES_SYNC_TYPE（0 企微 / 1 钉钉 / 2 飞书）
 *     与 window.OAONES_SYNC_LABEL（平台名），接口沿用本页的 OaonesApi
 *     （WeCom / DingTalk / Feishu 各自的 api.jsp）；
 *   * 后端 action：syncplan.list / syncplan.get / syncplan.save / syncplan.delete /
 *     syncplan.state（暂停 / 启动某一档计划任务）/ syncplan.cronPreview（「预测后 10 次」，
 *     按页面选定的计划任务方式试算）/ syncplan.runNow
 *     （表 uf_oaones_sync_plan，
 *     一行 = 一条同步规则；同一“租户 + 方向”可以有多条规则，按主键 id 操作）。
 *
 * **计划任务有两种方式**（“同步方式”单选，与「会议同步 / 日程同步」的计划任务同一套交互）：
 *   * 轮询间隔（分钟，默认）：填“每 N 分钟同步一次”（5 ~ 1440 分钟），
 *     下一次执行 = 上一次执行时刻 + N 分钟（后端 plan_mode = 0，interval_min 存分钟数）；
 *   * 高级模式（cron）：直接写 cron 表达式（Quartz 口径：秒 分 时 日 月 周，后端 plan_mode = 1）；
 *   两种取值一起保存（来回切不丢值），执行时按选中的那套走。
 *
 * 配置范围只有“当前租户配置”一档（页面不再提供“全局默认配置”选项）：
 *   * 当前租户配置：cfg_id = 上方“租户”下拉选中的租户，只作用于该租户；
 *   * **同步设置列表**一行一条规则、**首列显示租户名称**：独立一级菜单里列出本人可维护租户的设置，
 *     从租户工作区点进来（带 cfg_id、租户锁定）时只列当前租户的设置；
 *     上方“租户”下拉必选一个具体租户（没有“全部可维护租户”一档），它决定表单保存到哪个租户；
 *   * 早期版本保存的“全局默认配置”（cfg_id = 0，作用于所有未单独配置该方向的租户）仍在列表里
 *     列出并在首列标注，但本页不再提供它的表单入口（只剩行内操作与“覆盖为当前租户配置”）；
 *     后端与同步引擎的口径不变（见 oaones/skill.md 第一节第 24 条）。
 */
(function () {
    "use strict";

    function byId(id) { return document.getElementById(id); }

    function esc(v) {
        if (v === null || v === undefined) { return ""; }
        return String(v).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function showToast(msg, mode, duration) {
        if (!msg) { return; }
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode, duration: duration }); }
    }

    function showModal(maskId, modalId) {
        if (typeof window.showModal === "function") { window.showModal(maskId, modalId); return; }
        byId(maskId).style.display = "block";
        byId(modalId).style.display = "block";
    }

    function hideModal(maskId, modalId) {
        if (typeof window.hideModal === "function") { window.hideModal(maskId, modalId); return; }
        byId(maskId).style.display = "none";
        byId(modalId).style.display = "none";
    }

    var state = {
        type: 0,
        platformLabel: "企业微信",
        platformShort: "企微",
        sysAdmin: false,
        cfgs: [],
        /**
         * 选中的租户ID（下拉必选一个具体租户；从租户工作区带 cfg_id 进来时即该租户，且锁定）。
         * 它只决定「编辑同步设置」保存到哪个租户：列表本身一行一条规则、首列显示租户名称。
         */
        cfgId: "",
        locked: false,
        /** 配置范围：只剩 tenant 当前租户（页面不再提供 global 全局默认配置一档） */
        scope: "tenant",
        rows: [],
        /**
         * 表单正在编辑的规则 id（空 = 新增一条）。同一个“平台 + 租户 + 方向”可以保存多条规则：
         * 点列表行尾“编辑”= 编辑该条（带 id）；点“重置表单”= 回到“新增一条”。
         */
        editId: "",
        /** 同步范围候选：OA 分部 / OA 部门 / 平台部门（随租户加载） */
        subs: [],
        depts: [],
        wxDepts: [],
        deptCfgId: "",
        /** 表单里已选的默认 OA 部门ID（提交前的值，未保存） */
        deptValue: "",
        deptBackup: null,
        /** 同步范围（编辑表单“按范围同步”用；ID 一律按字符串比较） */
        subIds: [],
        deptIds: [],
        wxDeptId: "",
        /** A（OA→平台）时 OA 侧可多选；B（平台→OA）时 OA 是落点：分部单选必选、部门单选可不选 */
        subSingle: false,
        deptSingle: false,
        /** 三个范围弹框打开前的选择（取消时回退） */
        subBackup: null,
        deptScopeBackup: null,
        wxBackup: null,
        /** 明细弹窗（设置表行尾“结果详情”） */
        detail: { rows: [], type: "all", keyword: "", log: null }
    };

    /**
     * 「源组织 / 目标组织」两栏里，源组织最多直接显示几行：多出来的折叠成“…”（悬浮看全部），
     * 避免源组织选了很多分部/部门时把整行撑得过高。目标组织只有一个落点，恒为一行。
     */
    var ORG_LINE_LIMIT = 3;

    function tenantName(cfgId) {
        for (var i = 0; i < state.cfgs.length; i++) {
            if (String(state.cfgs[i].id) === String(cfgId)) {
                return String(state.cfgs[i].corp_name == null ? "" : state.cfgs[i].corp_name);
            }
        }
        return "";
    }

    /**
     * 提交 / 查询用的 cfg_id：配置范围只剩“当前租户配置”一档，因此就是上方选中的租户ID
     * （早期版本 cfg_id = 0 的“全局默认配置”已不再从表单维护，只在列表里列出与覆盖）。
     */
    function targetCfgId() {
        return state.scope === "global" ? "0" : state.cfgId;
    }

    /** 表单当前是否只读：页面不再提供“全局默认配置”的编辑入口，因此始终可编辑（保留判断以兼容老口径）。 */
    function formReadOnly() {
        return state.scope === "global" && !state.sysAdmin;
    }

    /* ==================================================================
     * 一、租户 / 配置范围
     * ================================================================== */

    function loadCfgs() {
        Oaones.api("cfg.myList", {}, function (res) {
            if (res.code !== 0) { showToast(res.msg, 2); return; }
            state.cfgs = res.data || [];
            var sel = byId("planCfgSelect");
            // 租户下拉只列本人可维护的租户（**必选一个具体租户**）：不再有“全部可维护租户”一档——
            // 列表本身一行一条规则、首列显示租户名称，这个下拉决定「编辑同步设置」保存到哪个租户。
            var html = "";
            state.cfgs.forEach(function (c) {
                html += "<option value=\"" + esc(c.id) + "\">" + esc(c.corp_name)
                    + "（ID " + esc(c.id) + "）</option>";
            });
            sel.innerHTML = html;
            // 默认选中：从租户工作区点进来（地址带 cfg_id）就是该租户，否则取第一个可维护租户
            var preset = window.OAONES_CFG_ID || "";
            var presetOk = !!preset && state.cfgs.some(function (c) {
                return String(c.id) === String(preset);
            });
            if (presetOk) {
                sel.value = preset;
            } else if (state.cfgs.length) {
                sel.value = String(state.cfgs[0].id);
            }
            if (window.Oaones && Oaones.cfgLocked && Oaones.cfgLocked()) {
                state.locked = true;
                var locked = Oaones.lockedCfgId();
                sel.innerHTML = "<option value=\"" + esc(locked) + "\">"
                    + esc(tenantName(locked) || ("#" + locked)) + "（当前租户，已锁定）</option>";
                sel.value = String(locked);
                sel.disabled = true;
            }
            renderPermTip();
            onCfgChange();
        });
    }

    /**
     * 租户分权提示（页面上的 #cfgPermTip）：
     * 锁定租户不在本人可维护范围内 / 本人不是任何租户的维护人时给出提示，其余情况隐藏。
     */
    function renderPermTip() {
        var tip = byId("cfgPermTip");
        if (!tip) { return; }
        var locked = !!(window.Oaones && Oaones.cfgLocked && Oaones.cfgLocked());
        var wanted = String(window.OAONES_CFG_ID || "");
        var lockedFound = !locked;
        for (var i = 0; locked && i < state.cfgs.length; i++) {
            if (String(state.cfgs[i].id) === wanted) { lockedFound = true; break; }
        }
        if (locked && !lockedFound) {
            tip.textContent = "当前租户（#" + Oaones.lockedCfgId() + "）不在你可以维护的"
                + state.platformShort + "租户范围内，本页不能切换租户、也不能保存该租户的同步设置；"
                + "请联系管理员把你加入该租户的维护人，或回租户列表重新点开另一个租户。";
            tip.style.display = "";
        } else if (state.cfgs.length === 0) {
            tip.textContent = window.OaonesTips ? OaonesTips.pick(
                "当前还没有任何" + state.platformShort + "租户，请先到租户配置页新建租户，再回到本页维护同步设置。",
                "当前登录人不是任何" + state.platformShort + "租户的维护人，无法维护同步设置；"
                    + "请联系管理员在租户配置页把本人加入该租户的维护人。")
                : ("当前没有可维护的" + state.platformShort + "租户。");
            tip.style.display = "";
        } else {
            tip.style.display = "none";
        }
    }

    function onCfgChange() {
        var sel = byId("planCfgSelect");
        state.cfgId = sel ? sel.value : "";
        renderTip();
        renderDirectionLabels();
        loadPlans();
        loadOrgOptions();
        fillForm();
    }

    function renderTip() {
        var tip = byId("planCfgTip");
        if (!tip) { return; }
        if (!state.cfgId) {
            tip.textContent = "请先在上面的“租户”下拉里选择一个租户（必选一个具体租户），"
                + "再在下面的「编辑同步设置」里维护该租户的同步设置。";
            return;
        }
        tip.textContent = "「编辑同步设置」的目标租户：" + tenantName(state.cfgId)
            + "（ID " + state.cfgId + "）；同步设置列表一行一条规则、首列显示租户名称，"
            + "同一个“租户 + 方向”可以有多条规则，用“规则 #id”区分。";
    }

    /** 方向下拉的文案：把平台名与当前租户名显示出来（需求：A/B 都要显示具体平台或租户）。 */
    function renderDirectionLabels() {
        var sel = byId("planDirection");
        if (!sel) { return; }
        var platform = state.platformLabel;
        var suffix = state.cfgId ? ("（" + (tenantName(state.cfgId) || ("#" + state.cfgId)) + "）") : "";
        sel.options[0].textContent = "A：OA → " + platform + suffix;
        sel.options[1].textContent = "B：" + platform + suffix + " → OA";
    }

    /* ==================================================================
     * 二、同步设置列表（一行一条规则、首列显示租户名称；就铺在“编辑同步设置”表单下方）
     * ================================================================== */

    /**
     * 设置列表取数：一行一条规则，首列显示租户名称。
     *
     * <p>从租户工作区进来（租户锁定）时只看该租户的设置；独立一级菜单里不带 cfg_id，
     * 由后端按“平台 + 维护人”过滤出本人可维护租户的规则（并带上历史的“全局默认配置”行）。
     * 上方“租户”下拉只决定「编辑同步设置」保存到哪个租户，不再切换列表范围。</p>
     */
    function loadPlans() {
        var params = { type: state.type };
        if (state.locked && state.cfgId) { params.cfg_id = state.cfgId; }
        Oaones.api("syncplan.list", params, function (res) {
            if (res.code !== 0) { showToast(res.msg, 2); return; }
            state.rows = res.data || [];
            renderPlanTable();
            fillForm();
        });
    }

    /** 按 id 取规则（行内操作：编辑 / 暂停启动 / 立即执行 / 删除都用它定位）。 */
    function rowById(id) {
        for (var i = 0; i < state.rows.length; i++) {
            if (String(state.rows[i].id) === String(id)) { return state.rows[i]; }
        }
        return null;
    }

    /** 表单当前编辑的那条规则（state.editId 为空 = 正在新增一条）。 */
    function editingRow() {
        return state.editId ? rowById(state.editId) : null;
    }

    /** 某配置范围（tenant / global）某方向的<b>全部</b>规则：同一“租户 + 方向”可以有多条。 */
    function rowsOf(scope, direction) {
        var cfg = scope === "global" ? "0" : String(state.cfgId || "");
        var out = [];
        if (!cfg) { return out; }
        state.rows.forEach(function (r) {
            if (String(r.cfg_id) === cfg && String(r.direction) === String(direction)) { out.push(r); }
        });
        return out;
    }

    /**
     * 设置列表要显示的行：**平铺列表取到的全部行**（一行一条规则，首列显示租户名称）。
     *
     * <p>后端已按“平台 + 维护人”过滤：独立一级菜单里就是本人可维护租户的规则（外加历史遗留的
     * “全局默认配置”行），从租户工作区进来时只带当前租户的设置。列表不再按“当前租户是否单独
     * 配置过该方向”做“生效口径”二选一——每行自己带租户，首列直接显示租户名称。</p>
     */
    function displayRows() {
        return state.rows;
    }

    /** 该行是否为“全局默认配置”（cfg_id = 0：早期版本保存的、作用于所有未单独配置该方向的租户）。 */
    function isGlobalRow(r) {
        return String(r.cfg_id) === "0";
    }

    /* ---------------------------------------------------------------
     * 2.0 计划任务：两种方式（0 轮询间隔（分钟）/ 1 高级 cron）
     *     与「会议同步 / 日程同步」的计划任务同一套交互：单选切换两块互斥显示，
     *     两种取值一起保存（来回切不丢值），执行时按选中的那套走。
     * --------------------------------------------------------------- */

    /** 当前选中的计划任务方式：0 轮询间隔（分钟）/ 1 高级模式（cron）。 */
    function planMode() {
        var cron = byId("planModeCron");
        return cron && cron.checked ? "1" : "0";
    }

    /** 切换计划任务方式：轮询间隔块 / 高级（cron）块互斥显示。 */
    function switchPlanMode(mode) {
        var intervalRadio = byId("planModeInterval");
        var cronRadio = byId("planModeCron");
        if (intervalRadio) { intervalRadio.checked = mode !== "1"; }
        if (cronRadio) { cronRadio.checked = mode === "1"; }
        var intervalRow = byId("planIntervalRow");
        var cronRow = byId("planCronRow");
        if (intervalRow) { intervalRow.style.display = mode === "1" ? "none" : ""; }
        if (cronRow) { cronRow.style.display = mode === "1" ? "" : "none"; }
    }

    /** 轮询间隔（分钟）：页面输入合法（5 ~ 1440 的整数）时返回分钟数，否则返回 -1（调用方给提示）。 */
    function checkPlanInterval() {
        var value = parseInt(byId("planInterval").value, 10);
        if (isNaN(value) || value < 5 || value > 1440) { return -1; }
        return value;
    }

    /** 计划任务单元格：轮询间隔显示“每 N 分钟轮询一次”（配方式标签），高级模式显示 cron 原文。 */
    function planCellOf(r) {
        var mode = String(r.plan_mode == null ? "0" : r.plan_mode) === "1" ? "1" : "0";
        var text = String(r.plan_text == null ? "" : r.plan_text);
        if (!text) {
            return "<span class='muted'>未配置</span>";
        }
        if (mode === "1") {
            return "<span class='mono'>" + esc(text) + "</span>"
                + " <span class='muted'>高级模式</span>";
        }
        return esc(text) + " <span class='muted'>轮询间隔</span>";
    }

    /**
     * 「源组织 / 目标组织」单元格。
     *
     * <p>取值：`scope_src_text` / `scope_dst_text`（后端按同步方向算）——**源组织一行一个组织**
     * （A（OA→平台）时源是 OA 侧的分部/部门；B（平台→OA）时源是平台侧的一个节点），
     * **目标组织恒为一行**（A = 一个平台部门 / root 节点；B = 一条落点路径“落点：分部「X」› 部门「Y」”）。
     * 未设置范围（`scope_set = 0`，历史行与“全局默认配置”）按引擎实际口径显示并置灰
     * （A：全部 OA 部门 → 该租户 root 节点；B：该租户 root 节点 → 不指定落点）。
     * 源组织超过 {@link ORG_LINE_LIMIT} 行时只显示前几行，其余折叠成“…共 N 个组织”，
     * 悬浮提示里给出该侧说明、全部组织与这条规则的完整范围文案。</p>
     *
     * @param key   {@code scope_src_text} 或 {@code scope_dst_text}
     * @param label “源组织”或“目标组织”（提示语用）
     */
    function orgCell(r, key, label) {
        var text = String(r[key] == null ? "" : r[key]);
        var side = String((key === "scope_src_text" ? r.scope_src_side : r.scope_dst_side) || "");
        var lines = [];
        text.split("\n").forEach(function (one) {
            var value = String(one).trim();
            if (value.length > 0) { lines.push(value); }
        });
        // 兼容：老数据（后端还没算出两栏时）退回原来的整段范围文案，只放在“源组织”一栏
        if (!lines.length && key === "scope_src_text") {
            var legacy = String(r.scope_range_text || r.scope_label || "").trim();
            if (legacy) { lines = [legacy]; }
        }
        // 悬浮提示：这一侧是什么 + （折叠时）全部组织 + 本条规则保存时的完整范围文案
        var title = label + (side ? "（" + side + "）" : "")
            + (Number(r.scope_set) === 1 ? "" : "：全量同步（未设置范围）");
        if (lines.length > 1) {
            title += "；共 " + lines.length + " 个组织：" + lines.join("；");
        }
        if (String(r.scope_range_text || "").length > 0) {
            title += "；范围：" + r.scope_range_text;
        }
        var muted = Number(r.scope_set) !== 1;
        var html;
        if (!lines.length) {
            html = "<span class='muted'>—</span>";
        } else {
            var shown = lines.slice(0, ORG_LINE_LIMIT);
            html = "";
            shown.forEach(function (one, i) {
                if (i > 0) { html += "<br>"; }
                html += esc(one);
            });
            if (lines.length > shown.length) {
                html += "<br><span class='muted'>…共 " + lines.length + " 个组织</span>";
            }
            if (muted) { html = "<span class='muted'>" + html + "</span>"; }
        }
        return "<span title=\"" + esc(title) + "\">" + html + "</span>";
    }

    /** 首列文案：这条规则属于哪个租户（历史遗留的“全局默认配置”行单独标注）。 */
    function tenantCell(r) {
        if (isGlobalRow(r)) {
            return "<span class='tag unbound'>全局默认配置</span> "
                + "<span class='muted'>（未单独配置该方向的租户生效）</span>";
        }
        var name = tenantName(r.cfg_id);
        return esc(name || ("#" + r.cfg_id)) + " <span class='muted'>（ID " + esc(r.cfg_id) + "）</span>";
    }

    function renderPlanTable() {
        var body = "";
        displayRows().forEach(function (r) {
            var global = isGlobalRow(r);
            // 计划任务：两种方式（0 轮询间隔（分钟）/ 1 高级 cron），后端给什么就显示什么
            // （plan_text：轮询间隔 = “每 30 分钟轮询一次”，高级模式 = cron 表达式原文）
            var planCell = planCellOf(r);
            // 源组织 / 目标组织：源组织一行一个（超过 3 行折叠，悬浮看全部），目标组织恒一行；
            // 未设置范围（scope_set = 0，历史行与“全局默认配置”）按引擎实际口径显示并置灰
            var srcCell = orgCell(r, "scope_src_text", "源组织");
            var dstCell = orgCell(r, "scope_dst_text", "目标组织");
            var status = String(r.enabled) === "1"
                ? "<span class='tag bound'>已启用</span>" : "<span class='tag unbound'>已停用</span>";
            var detailBtn = Number(r.last_log_id) > 0
                ? (" <button class='link' data-plan-detail='" + esc(r.last_log_id) + "'>结果详情</button>") : "";
            var action = "";
            if (global) {
                // 历史遗留的“全局默认配置”行：本页不再有“配置范围 = 全局默认配置”的表单入口，
                // 因此不给“编辑”；只保留不依赖表单的行内操作（系统管理员：暂停·启动 / 立即执行 / 删除）
                // 与“覆盖为当前租户配置”（把它的口径读进表单，存成上方所选租户自己的规则）。
                if (state.sysAdmin) {
                    var globalOn = String(r.enabled) === "1";
                    action = "<button class='link' data-plan-state='" + esc(r.id) + "|"
                        + (globalOn ? "0" : "1") + "' title=\""
                        + (globalOn ? "暂停后该条规则的计划任务不再自动执行（设置与历史日志都保留，可随时再启动）"
                            : "启动后按该条规则保存的计划任务（轮询间隔 / 高级 cron）自动执行") + "\">"
                        + (globalOn ? "暂停" : "启动") + "</button>"
                        + " <button class='link' data-plan-run='" + esc(r.id) + "'>立即执行</button>"
                        + detailBtn
                        + " <button class='link' data-plan-delete='" + esc(r.id) + "'>删除</button>";
                }
                if (state.cfgId) {
                    action += (action ? " " : "")
                        + "<button class='link' data-plan-override='" + esc(r.id)
                        + "'>覆盖为当前租户配置</button>";
                }
                if (!action) { action = "<span class='muted'>只读</span>"; }
            } else {
                // 行内操作全部按规则 id 定位（同一“租户 + 方向”可以有多条规则）
                var planOn = String(r.enabled) === "1";
                action = "<button class='link' data-plan-edit='" + esc(r.id) + "'>编辑</button>"
                    + " <button class='link' data-plan-state='" + esc(r.id) + "|"
                    + (planOn ? "0" : "1") + "' title=\""
                    + (planOn ? "暂停后该条规则的计划任务不再自动执行（设置与历史日志都保留，可随时再启动）"
                        : "启动后按该条规则保存的计划任务（轮询间隔 / 高级 cron）自动执行") + "\">"
                    + (planOn ? "暂停" : "启动") + "</button>"
                    + " <button class='link' data-plan-run='" + esc(r.id) + "'>立即执行</button>"
                    + detailBtn
                    + " <button class='link' data-plan-delete='" + esc(r.id) + "'>删除</button>";
            }
            // 当前“目标租户”（上方租户下拉选中的那个、表单保存到的就是它）的行加底色
            var rowCls = (!global && String(r.cfg_id) === String(state.cfgId)) ? " class='plan-cur-row'" : "";
            body += "<tr" + rowCls + ">"
                + "<td>" + tenantCell(r) + "</td>"
                + "<td>" + esc(r.direction_label || "")
                + " <span class='muted'>规则 #" + esc(r.id) + "</span></td>"
                + "<td>" + srcCell + "</td>"
                + "<td>" + dstCell + "</td>"
                + "<td>" + esc(r.nodept_text || "") + "</td>"
                + "<td>" + planCell + "</td>"
                + "<td>" + status + "</td>"
                + "<td>" + esc(r.next_run_time || "-") + "</td>"
                + "<td>" + esc(r.last_run_time || "-") + "</td>"
                + "<td>" + esc(r.last_result || "-") + "</td>"
                + "<td>" + action + "</td>"
                + "</tr>";
        });
        var shown = displayRows();
        byId("planBody").innerHTML = body;
        var emptyEl = byId("planEmpty");
        emptyEl.style.display = shown.length === 0 ? "block" : "none";
        if (shown.length === 0) {
            emptyEl.textContent = state.locked
                ? "该租户还没有同步规则（在上面的“编辑同步设置”里填好后点“保存设置”；同一方向可以保存多条）"
                : "还没有任何同步设置：在上面的“编辑同步设置”里选好租户与同步方向，填好后点“保存设置”"
                    + "（同一“租户 + 方向”可以保存多条规则）";
        }
        if (window.OaonesTable) { OaonesTable.resizable(byId("planTable"), { key: "syncCenter.planTable" }); }
    }

    /**
     * 行内“编辑”：把该条规则读进表单（表单的“目标租户 + 方向”跟着这一行走）。
     *
     * <p>同一“租户 + 方向”可以有多条规则，所以按规则 id 定位；列表是跨租户平铺的
     * （首列显示租户名称），点到别的租户那一行时，先把上方“租户”下拉一起切过去，
     * 这样表单保存回的就是这条规则自己的租户。</p>
     */
    function selectRule(id) {
        var row = rowById(id);
        if (!row) { showToast("该规则不存在或已被删除，请刷新页面后重试", 1); return; }
        // 历史遗留的“全局默认配置”行没有“编辑”（见 renderPlanTable），这里只按租户行切表单
        if (!state.locked && String(state.cfgId) !== String(row.cfg_id)) {
            var sel = byId("planCfgSelect");
            sel.value = String(row.cfg_id);
            state.cfgId = String(row.cfg_id);
            renderTip();
            renderDirectionLabels();
            loadOrgOptions();
        }
        state.scope = "tenant";
        byId("planScopeSelect").value = "tenant";
        byId("planDirection").value = String(row.direction);
        state.editId = String(row.id);
        fillForm();
        showToast("已把规则 #" + row.id + " 读进表单，改完点“保存设置”即更新这条（要新增一条请点“重置表单”）", 0, 6000);
    }

    /* ==================================================================
     * 三、编辑表单
     * ================================================================== */

    function fillForm() {
        var direction = byId("planDirection").value;
        // 正在编辑的那条规则（state.editId 为空 = 新增一条）
        var row = editingRow();
        if (!row && state.editId) {
            // 正在编辑的规则已经不在列表里（被别的页面删掉了）：回到“新增一条”
            state.editId = "";
        }
        if (row && (String(row.cfg_id) !== targetCfgId() || String(row.direction) !== String(direction))) {
            // 表单已经切到别的“配置范围 / 租户 / 方向”：这次变成“新增一条”
            state.editId = "";
            row = null;
        }
        // 新增一条时的预填口径：该租户该方向还没有自己的规则、而存在历史遗留的“全局默认配置”时按它预填
        // （租户维护人可以直接在此基础上改一改存成自己的第 1 条规则）；
        // 该档已经有规则时表单留空，避免顺手再存一条一模一样的（要改某条请点行尾“编辑”）。
        if (!row && state.scope === "tenant" && state.cfgId && !rowsOf(state.scope, direction).length) {
            var globalRows = rowsOf("global", direction);
            if (globalRows.length) { row = globalRows[0]; }
        }
        // 计划任务：两种方式的取值都填好（来回切不丢值）；新增一条时默认“轮询间隔（分钟）+ 30 分钟”
        switchPlanMode(row && String(row.plan_mode) === "1" ? "1" : "0");
        byId("planInterval").value = String(row && row.interval_min ? row.interval_min : 30);
        byId("planCron").value = row && row.cron ? row.cron : "";
        byId("planEnabled").checked = !row || String(row.enabled) === "1";
        byId("planRemark").value = row && row.remark ? row.remark : "";
        var policy = row && String(row.nodept_policy) === "1" ? "1" : "0";
        var radios = document.getElementsByName("planNodeptPolicy");
        for (var i = 0; i < radios.length; i++) {
            radios[i].checked = (radios[i].value === policy);
        }
        state.deptValue = row && row.default_dept_id ? String(row.default_dept_id) : "";
        renderDeptInput();
        // 同步范围：表单按上面选定的设置行预填（新增时若该租户继承了历史遗留的“全局默认配置”，
        // 上面已把 row 指向那一条，保存后即成为该租户自己的设置）
        state.subIds = splitIds(row && row.oa_sub_ids);
        state.deptIds = splitIds(row && row.oa_dept_ids);
        state.wxDeptId = row && row.wx_dept_id ? String(row.wx_dept_id) : "";
        byId("planScopeEnabled").checked = !!(row && Number(row.scope_set) === 1);
        renderScopeRow();
        renderNodeptRow();
        renderFormState(row);
        hideCronPreview();
    }

    /**
     * 收起右侧“计划任务预测（后 10 次）”的结果，并把占位提示恢复显示。
     *
     * <p>预测结果固定显示在「编辑同步设置」卡片右侧 20% 的栏里（左侧 80% 是设置项，中间虚线分隔），
     * 切换设置行 / 重置表单时先收起上一次的预测，避免看到过期的执行时间。</p>
     */
    function hideCronPreview() {
        var previewEl = byId("planCronPreview");
        if (previewEl) { previewEl.style.display = "none"; }
        var emptyEl = byId("planCronPreviewEmpty");
        if (emptyEl) { emptyEl.style.display = ""; }
    }

    /** A（OA→平台）显示“不同步 / 直接挂在租户下”，B（平台→OA）显示“默认部门”弹框。 */
    function renderNodeptRow() {
        var isA = String(byId("planDirection").value) === "0";
        byId("planNodeptRowA").style.display = isA ? "" : "none";
        byId("planNodeptRowB").style.display = isA ? "none" : "";
    }

    /** 表单可编辑状态 + 提示（配置范围只有“当前租户配置”一档，表单按上面的“目标租户”维护）。 */
    function renderFormState(row) {
        var readOnly = formReadOnly();
        ["planCron", "planInterval", "planRemark", "planEnabled", "planCronPreviewBtn",
            "planSaveBtn", "planRunNowBtn", "planDeleteBtn", "planScopeEnabled"].forEach(function (id) {
            byId(id).disabled = readOnly;
        });
        var radios = document.getElementsByName("planNodeptPolicy");
        for (var i = 0; i < radios.length; i++) { radios[i].disabled = readOnly; }
        var modeRadios = document.getElementsByName("planMode");
        for (var j = 0; j < modeRadios.length; j++) { modeRadios[j].disabled = readOnly; }
        byId("planDeptPicker").classList.toggle("disabled", readOnly);
        renderScopeRow();
        var tip = byId("planTip");
        if (!tip) { return; }
        if (!state.cfgId) {
            // 没有可维护租户（或租户下拉还没取到数据）：先不给“目标租户”类的提示，避免出现空租户名
            tip.textContent = "请先在上面的“租户”下拉里选择一个租户，再维护该租户的同步设置。";
            return;
        }
        var scopeText = "当前租户配置";
        var directionValue = byId("planDirection").value;
        var editing = editingRow();
        var ownRows = rowsOf("tenant", directionValue);
        var globalRows = (!ownRows.length && state.cfgId)
            ? rowsOf("global", directionValue) : [];
        var parts = [];
        if (editing) {
            var editBits = [scopeText];
            if (editing.enabled_text) { editBits.push(editing.enabled_text); }
            if (editing.next_run_time) { editBits.push("下次执行 " + editing.next_run_time); }
            parts.push("当前编辑：规则 #" + editing.id + "（租户 "
                + (tenantName(editing.cfg_id) || ("#" + editing.cfg_id)) + "（ID " + editing.cfg_id + "），"
                + editBits.join("，")
                + "）；改完点“保存设置”即更新这一条，点“重置表单”可改成新增一条。");
        } else if (ownRows.length) {
            parts.push("当前编辑：新增一条规则（目标租户 "
                + (tenantName(state.cfgId) || ("#" + state.cfgId)) + "（ID " + state.cfgId + "），"
                + scopeText + "；该方向已有 " + ownRows.length
                + " 条规则，表单是空的）；要改某一条请点列表里那一行的“编辑”。");
        } else if (globalRows.length) {
            parts.push("当前编辑：新增一条规则（目标租户 "
                + (tenantName(state.cfgId) || ("#" + state.cfgId)) + "（ID " + state.cfgId + "），"
                + scopeText + "）。该租户该方向还没有自己的规则，"
                + "当前生效的是历史遗留的“全局默认配置”（表单已按它预填）；直接保存即成为该租户自己的第 1 条规则，"
                + "保存后该方向不再套用全局默认配置。");
        } else {
            parts.push("当前编辑：新增一条规则（目标租户 "
                + (tenantName(state.cfgId) || ("#" + state.cfgId)) + "（ID " + state.cfgId + "），"
                + scopeText + "），填写后点“保存设置”即可新增。"
                + "同一“租户 + 同步方向”可以保存多条规则（各自的同步范围与计划任务不同）。");
        }
        tip.textContent = parts.join("｜");
    }

    function resetForm() {
        // 计划任务回到默认口径：轮询间隔（分钟）+ 30 分钟（两种取值都清干净，避免顺手存下上一条的 cron）
        switchPlanMode("0");
        byId("planInterval").value = "30";
        byId("planCron").value = "";
        byId("planEnabled").checked = true;
        byId("planRemark").value = "";
        document.getElementsByName("planNodeptPolicy")[0].checked = true;
        state.deptValue = "";
        renderDeptInput();
        // 同步范围一并清空（不勾选“按范围同步”= 全量同步）
        state.subIds = [];
        state.deptIds = [];
        state.wxDeptId = "";
        byId("planScopeEnabled").checked = false;
        renderScopeRow();
        hideCronPreview();
    }

    /* ==================================================================
     * 四、保存 / 删除 / 立即执行 / 计划任务预测
     * ================================================================== */

    /**
     * 保存设置。
     *
     * @param force    是否“强制同步”：首次检测发现范围内（含下级）已有部门/成员绑定时，
     *                 用户在弹框里选择强制同步后带 1 再保存一次（后端会保存成功并置“已确认”）
     * @param runAfter 保存成功后是否立即按这条映射关系同步一次（强制同步后立即执行）
     */
    function savePlan(force, runAfter) {
        if (formReadOnly()) { showToast("“全局默认配置”只允许系统管理员维护", 1, 6000); return; }
        var cfgId = targetCfgId();
        if (!cfgId) { showToast("请先在上面的“租户”下拉里选择一个租户，再保存同步设置", 1); return; }
        var policy = "0";
        var radios = document.getElementsByName("planNodeptPolicy");
        for (var i = 0; i < radios.length; i++) {
            if (radios[i].checked) { policy = radios[i].value; }
        }
        var direction = byId("planDirection").value;
        var scopeEnabled = byId("planScopeEnabled").checked && !byId("planScopeEnabled").disabled;
        // 按范围同步的必填校验（与后端同一口径，先在前端提示，避免白跑一趟接口）
        if (scopeEnabled) {
            if (direction === "0" && state.subIds.length === 0 && state.deptIds.length === 0) {
                showToast("请选择同步范围：OA → 平台 方向至少要选一个 OA 分部或 OA 部门（含下级）；"
                    + "平台侧部门留空 = 该租户的 root 节点", 1, 8000);
                return;
            }
            if (direction === "1" && state.subIds.length === 0) {
                showToast("请选择 OA 落点分部：平台 → OA 方向把平台侧范围（含下级部门）同步到该落点分部下的 OA 部门",
                    1, 8000);
                return;
            }
            if (direction === "1" && (state.subIds.length > 1 || state.deptIds.length > 1)) {
                showToast("平台 → OA 方向的 OA 落点是单选：只能选一个落点分部与一个落点部门（落点部门可以不选）",
                    1, 8000);
                return;
            }
        }
        // 计划任务的必填校验（与后端同一口径）：启用时当前方式那一套必须配好
        var mode = planMode();
        if (byId("planEnabled").checked) {
            if (mode === "0") {
                if (checkPlanInterval() < 0) {
                    showToast("请填写轮询间隔（分钟）：5 ~ 1440 分钟之间（例：30 = 每 30 分钟同步一次）；"
                        + "要按 cron 执行请把“同步方式”切到“高级模式（cron）”", 1, 9000);
                    return;
                }
            } else if (!String(byId("planCron").value || "").trim()) {
                showToast("“高级模式（cron）”要填写 cron 表达式（Quartz 口径：秒 分 时 日 月 周）；"
                    + "想按分钟轮询请把“同步方式”切回“轮询间隔（分钟）”", 1, 9000);
                return;
            }
        }
        Oaones.api("syncplan.save", {
            type: state.type,
            // 带 id = 更新这条规则；不带 = 新增一条（同一“租户 + 方向”可以有多条规则）
            id: state.editId || "",
            cfg_id: cfgId,
            direction: direction,
            enabled: byId("planEnabled").checked ? 1 : 0,
            // 计划任务：两种方式的取值都带上（后端按 plan_mode 取用其中一套，切方式不丢值）
            plan_mode: mode,
            interval_min: byId("planInterval").value,
            cron: byId("planCron").value,
            nodept_policy: policy,
            default_dept_id: state.deptValue,
            remark: byId("planRemark").value,
            // 同步范围：不勾选“按范围同步”= 0（沿用历史的全量同步）
            scope_set: scopeEnabled ? 1 : 0,
            oa_sub_ids: scopeEnabled ? state.subIds.join(",") : "",
            oa_dept_ids: scopeEnabled ? state.deptIds.join(",") : "",
            wx_dept_id: scopeEnabled ? state.wxDeptId : "",
            force: force ? 1 : 0
        }, function (res) {
            if (res.code !== 0) { showToast(res.msg, 2); return; }
            // 首次检测：范围内（含下级）已有部门/成员绑定时后端不保存，先弹框询问是否强制同步
            var data = res.data || {};
            if (data.need_confirm) {
                openConflictDialog(data);
                return;
            }
            var wasUpdate = !!state.editId;
            var saved = res.data || {};
            showToast(wasUpdate
                ? ("已更新规则 #" + state.editId)
                : ("已新增一条同步规则" + (saved.id ? "（规则 #" + saved.id + "）" : "")
                    + "；同一“租户 + 方向”可以再存多条"), 0, 7000);
            // 新增后回到“新增一条”状态（表单留空，避免顺手再存一条一样的）；更新则继续编辑这一条
            if (!wasUpdate) { resetForm(); }
            loadPlans();
            loadSchedulerStatus();
            // 强制同步并保存后立即按这条规则同步一次（用刚保存那条的 id）
            if (runAfter) { runNow(saved.id || state.editId); }
        });
    }

    /* ---------------------------------------------------------------
     * 4.1 首次检测弹框（范围内已有绑定关系 → 是否强制同步）
     * --------------------------------------------------------------- */

    /** 打开“范围内已有绑定关系”的确认弹框（列出冲突对象，可选择强制同步）。 */
    function openConflictDialog(data) {
        byId("planConflictMessage").textContent = data.message
            || "该同步范围内（含下级）已有部门或成员绑定关系，是否强制同步？";
        var html = "";
        var conflicts = data.conflicts || [];
        conflicts.forEach(function (text) { html += "<li>" + esc(text) + "</li>"; });
        byId("planConflictList").innerHTML = html
            || "<li>（未返回明细，可直接选择强制同步）</li>";
        showModal("planConflictMask", "planConflictModal");
    }

    function closeConflictDialog() {
        hideModal("planConflictMask", "planConflictModal");
    }

    /** 删除一条规则（不传 id 时删表单正在编辑的那条）。同一个“租户 + 方向”的其它规则不受影响。 */
    function deletePlan(ruleId) {
        if (formReadOnly()) { showToast("“全局默认配置”只允许系统管理员维护", 1, 6000); return; }
        var id = String(ruleId || state.editId || "");
        if (!id) { showToast("请先点列表里该条规则的“编辑”，或直接在列表行尾点“删除”", 1); return; }
        var row = rowById(id);
        if (!row) { showToast("该规则不存在或已被删除，请刷新页面后重试", 1); return; }
        var doDelete = function () {
            Oaones.api("syncplan.delete", {
                type: state.type, id: id
            }, function (res) {
                if (res.code !== 0) { showToast(res.msg, 2); return; }
                if (String(state.editId) === id) {
                    // 删掉的正是表单里编辑的那条：回到“新增一条”
                    state.editId = "";
                    resetForm();
                }
                showToast("已删除规则 #" + id, 0);
                loadPlans();
            });
        };
        var label = "规则 #" + id + "（" + (isGlobalRow(row) ? "全局默认配置"
            : ((tenantName(row.cfg_id) || ("#" + row.cfg_id)) + "（ID " + row.cfg_id + "）"))
            + "「" + (row.direction_label || "") + "」）";
        if (window.OaonesToast && OaonesToast.confirm) {
            // 注意：OaonesToast.confirm(msg, onOk) 的 onOk 是**无参回调**（点“确定”即执行），
            // 不能再写成 function (ok) { if (ok) … }：那样 ok 恒为 undefined，点“确定”不会有任何动作。
            OaonesToast.confirm("确认删除" + label + "吗？删除后该条计划任务不再执行"
                + "（历史同步日志保留）。", doDelete);
        } else {
            doDelete();
        }
    }

    /**
     * 暂停 / 启动某一条规则的计划任务（设置表行尾的“暂停 / 启动”按钮）。
     *
     * <p>只改启停开关：同步方向、同步范围、计划任务配置（方式 / 间隔 / cron）、
     * 成员“没有找到部门”的口径都不动；
     * 启动时后端按该条规则保存的计划任务方式重算“下次执行时间”，该方式配置缺失 / 非法会拒绝并提示。
     * 暂停只影响计划任务，随时可以再点“启动”。</p>
     *
     * @param ruleId  规则 id
     * @param enabled 目标状态：1 = 启动 / 0 = 暂停
     */
    function setPlanState(ruleId, enabled) {
        var on = String(enabled) === "1";
        Oaones.api("syncplan.state", {
            type: state.type, id: ruleId, enabled: on ? 1 : 0
        }, function (res) {
            if (res.code !== 0) { showToast(res.msg, 2); return; }
            showToast((on ? "已启动" : "已暂停") + "规则 #" + ruleId + " 的计划任务"
                + (on ? "（按该条规则保存的计划任务方式执行）" : "（不再按计划执行，规则与日志保留）"), 0);
            loadPlans();
            loadSchedulerStatus();
        });
    }

    /** 立即执行一次：不传 id 时执行表单里正在编辑的那条规则（按它自己保存的同步范围与口径）。 */
    function runNow(ruleId) {
        if (formReadOnly()) { showToast("“全局默认配置”只允许系统管理员维护", 1, 6000); return; }
        var id = String(ruleId || state.editId || "");
        if (!id) {
            showToast("请先点列表里该条规则的“编辑”（或直接点行尾“立即执行”）——"
                + "“立即执行”执行的是已保存的规则", 1, 8000);
            return;
        }
        Oaones.api("syncplan.runNow", {
            type: state.type, id: id
        }, function (res) {
            if (res.code !== 0) { showToast(res.msg, 2); return; }
            var sync = (res.data || {}).sync || {};
            var msg;
            if (sync.scoped) {
                // 按范围同步（设置里配了同步范围）：直接展示按映射关系同步的结果
                msg = sync.msg || "按范围同步完成";
            } else if (sync.global) {
                msg = "全局默认配置已执行：覆盖 " + (sync.tenant_count || 0) + " 个租户（成功 "
                    + (sync.success_count || 0) + "，失败 " + (sync.fail_count || 0) + "），共 "
                    + (sync.dept_count || 0) + " 个部门 " + (sync.user_count || 0) + " 个员工";
            } else {
                msg = "同步完成：本次同步 " + (sync.dept_count || 0) + " 个部门 "
                    + (sync.user_count || 0) + " 个员工"
                    + (Number(sync.nodept_count) > 0
                        ? ("，其中 " + sync.nodept_count + " 个员工没有找到部门"
                            + (sync.nodept_dept ? "，在「" + sync.nodept_dept + "」下" : ""))
                        : "");
            }
            showToast(msg, 0, 8000);
            loadPlans();
        });
    }

    /**
     * 「预测后 10 次」：把表单里选中的那套方式（轮询间隔（分钟）或高级 cron）交给后端试算
     * **实际会执行的时间**（与内置调度器的到点判断共用同一份代码），结果显示在卡片右侧的预测栏里。
     */
    function previewPlan() {
        var mode = planMode();
        if (mode === "0") {
            if (checkPlanInterval() < 0) {
                showToast("请填写轮询间隔（分钟）：5 ~ 1440 分钟之间（例：30 = 每 30 分钟同步一次）", 1, 9000);
                return;
            }
        } else if (!String(byId("planCron").value || "").trim()) {
            showToast("“高级模式（cron）”要填写 cron 表达式（Quartz 口径：秒 分 时 日 月 周）", 1, 9000);
            return;
        }
        Oaones.api("syncplan.cronPreview", {
            mode: mode,
            cron: byId("planCron").value,
            interval_min: byId("planInterval").value,
            count: 10
        }, function (res) {
            if (res.code !== 0) { showToast(res.msg, 2); return; }
            var times = res.data || [];
            var html = "";
            times.forEach(function (t) { html += "<li>" + esc(t) + "</li>"; });
            byId("planCronPreviewList").innerHTML = html;
            // 结果固定显示在「编辑同步设置」卡片右侧的预测栏里（左侧 80% 仍是设置项）
            byId("planCronPreview").style.display = "";
            var emptyEl = byId("planCronPreviewEmpty");
            if (emptyEl) { emptyEl.style.display = "none"; }
            if (times.length === 0) { showToast("当前方式没有可预测的执行时间，请检查配置", 1); }
        });
    }

    /**
     * 内置调度器状态：本模块自己按页面上保存的计划任务（轮询间隔 / 高级 cron）执行（不用 E9 后台计划任务），
     * 这里把“有没有在跑、最近一次扫描到什么时候”直接显示出来，便于现场确认。
     */
    function loadSchedulerStatus() {
        Oaones.api("syncplan.scheduler", {}, function (res) {
            var el = byId("planSchedulerTip");
            if (!el || res.code !== 0) { return; }
            var d = res.data || {};
            var parts = [];
            if (d.enabled === false) {
                parts.push("内置调度器：<b>已关闭</b>（oaones.properties 的 sync.plan.enable=0）；"
                    + "此时页面上的计划任务不会执行，需要在 E9 后台按计划任务类 "
                    + "<span class='mono'>com.customization.oaones.appsyn.job.OaonesSyncPlanCronJob</span> 运行");
            } else if (d.started === true) {
                parts.push("内置调度器：<b>运行中</b>（每 " + (d.tick_seconds || 30) + " 秒扫描一次，"
                    + (d.running ? "当前正在执行任务" : "空闲")
                    + (d.last_tick ? "；最近一次扫描 " + esc(d.last_tick)
                        + (d.last_run_count > 0 ? "，执行 " + d.last_run_count + " 个同步设置" : "，无到点任务") : "")
                    + "）");
            } else {
                parts.push("内置调度器：<b>尚未启动</b>（打开任意 OAones 页面或接口即自动启动）");
            }
            parts.push("页面上保存的计划任务（轮询间隔 / 高级 cron）即执行时间，无需在 E9 后台配置计划任务");
            el.innerHTML = parts.join("；") + "。";
        });
    }

    /** 行尾“结果详情”：打开本页的同步明细弹窗（统计 + 逐条表格，见第七节）。 */
    function showResultDetail(logId) {
        showDetail(logId);
    }

    /* ==================================================================
     * 五、组织候选（OA 分部 / OA 部门 / 平台部门）
     *   一次接口（sync.orgOptions）取回三份候选：默认 OA 部门选择与“同步范围”共用
     * ================================================================== */

    function loadOrgOptions() {
        var cfgId = state.cfgId || "";
        if (state.deptCfgId === cfgId) { return; }
        Oaones.api("sync.orgOptions", cfgId ? { cfg_id: cfgId } : {}, function (res) {
            if (res.code !== 0) { showToast(res.msg, 2); return; }
            var data = res.data || {};
            var rows = data.departments || [];
            state.depts = rows.slice().sort(function (a, b) {
                return String(a.name || "").localeCompare(String(b.name || ""), "zh-Hans-CN");
            });
            state.subs = (data.subcompanies || []).slice().sort(function (a, b) {
                return String(a.name || "").localeCompare(String(b.name || ""), "zh-Hans-CN");
            });
            state.wxDepts = (data.wx_depts || []).slice().sort(function (a, b) {
                return String(a.name || "").localeCompare(String(b.name || ""), "zh-Hans-CN");
            });
            state.deptCfgId = cfgId;
            if (state.deptValue) { renderDeptInput(); }
            renderScopeInputs();
        });
    }

    /** 逗号分隔的 ID 串 → 字符串数组（去空格、去空项）。 */
    function splitIds(value) {
        var text = String(value == null ? "" : value).trim();
        if (!text) { return []; }
        return text.split(",").map(function (v) { return String(v).trim(); })
            .filter(function (v) { return v.length > 0; });
    }

    function subById(id) {
        var key = String(id == null ? "" : id);
        for (var i = 0; i < state.subs.length; i++) {
            if (String(state.subs[i].id) === key) { return state.subs[i]; }
        }
        return null;
    }

    function deptById(id) {
        var key = String(id == null ? "" : id);
        for (var i = 0; i < state.depts.length; i++) {
            if (String(state.depts[i].id) === key) { return state.depts[i]; }
        }
        return null;
    }

    /** 选择框显示：已选部门名（或“（不指定…”占位） */
    function renderDeptInput() {
        var d = deptById(state.deptValue);
        byId("planDefaultDept").value = state.deptValue || "";
        byId("planDeptInput").value = state.deptValue
            ? ((d && (d.name || d.fullname)) ? (d.name || d.fullname) : ("部门 " + state.deptValue))
            : "";
        byId("planDeptInput").placeholder = "（不指定：只回写人员基础信息）";
    }

    function deptChildMap() {
        var map = {};
        state.depts.forEach(function (d) {
            var p = String(d.parent == null ? "" : d.parent);
            if (!map[p]) { map[p] = []; }
            map[p].push(String(d.id));
        });
        return map;
    }

    function deptText(d) {
        return [d.id, d.code, d.name, d.fullname].map(function (k) {
            return String(d[k] == null ? "" : d[k]);
        }).join(" ");
    }

    function filterDepts(keyword) {
        var k = String(keyword || "").trim().toLowerCase();
        if (!k) { return state.depts; }
        return state.depts.filter(function (d) {
            return deptText(d).toLowerCase().indexOf(k) >= 0;
        });
    }

    function planDeptOptionHtml(d) {
        var id = String(d.id);
        var active = String(state.deptValue) === id;
        var cls = "wx-pick-option pick-cols-wide" + (active ? " active" : "");
        var tip = (active ? "已选中该部门（再点其它部门即更换）" : "点击选择该部门作为默认 OA 部门")
            + "｜双击即确定并退出｜" + (d.fullname || d.name || "");
        return "<button type='button' class='" + cls + "' data-id='" + esc(id) + "'"
            + " title='" + esc(tip) + "'>"
            + "<span class='wx-pick-opt-name'>" + esc(d.name || d.fullname || ("部门 " + id)) + "</span>"
            + "<span>" + esc((d.code ? d.code : "-") + "（ID " + id + "）") + "</span>"
            + "</button>";
    }

    function renderPlanDeptList() {
        var keyword = byId("planDeptSearch").value;
        var rows = filterDepts(keyword);
        var el = byId("planDeptList");
        if (OaonesPicker.mode("planDept") === "tree") {
            el.className = "wx-pick-list is-tree";
            el.innerHTML = OaonesPicker.renderTree("planDept", rows, {
                parentOf: function (d) { return d.parent; },
                textOf: function (d) { return deptText(d); },
                keyword: keyword,
                emptyText: "没有匹配的部门",
                optionHtml: function (d) { return planDeptOptionHtml(d); }
            });
        } else {
            el.className = "wx-pick-list";
            var html = rows.length
                ? "<div class='wx-pick-head pick-cols-wide'><span>部门名称</span><span>部门编号 / ID</span></div>" : "";
            rows.forEach(function (d) { html += planDeptOptionHtml(d); });
            el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的部门</div>";
        }
    }

    /** 单选：点一行即选中该部门（再点其它行即更换，点同一行不取消） */
    function togglePlanDept(id) {
        state.deptValue = String(id == null ? "" : id);
    }

    function refreshPlanDeptDom() {
        var opts = byId("planDeptList").querySelectorAll(".wx-pick-option");
        for (var i = 0; i < opts.length; i++) {
            opts[i].classList.toggle("active",
                String(opts[i].getAttribute("data-id")) === String(state.deptValue));
        }
    }

    function openPlanDeptPick() {
        if (formReadOnly()) {
            showToast("“全局默认配置”只允许系统管理员维护，当前账号只读查看", 1, 6000);
            return;
        }
        state.deptBackup = state.deptValue;
        byId("planDeptSearch").value = "";
        OaonesPicker.resetTree("planDept");
        renderPlanDeptList();
        showModal("planDeptMask", "planDeptModal");
        byId("planDeptSearch").focus();
    }

    function closePlanDeptPick(commit) {
        if (!commit) {
            state.deptValue = state.deptBackup || "";
            renderDeptInput();
        }
        state.deptBackup = null;
        hideModal("planDeptMask", "planDeptModal");
    }

    /* ==================================================================
     * 六、同步范围选择（OA 分部 / OA 部门 / 平台部门）
     *   弹框骨架（拖动、列表式/树状切换、树节点展开收起、全选）由 js/picker.js 提供
     * ================================================================== */

    function scopeDirection() { return byId("planDirection").value; }

    /** 当前表单是否“按范围同步”（勾选且当前档允许设置范围）。 */
    function scopeOn() {
        var box = byId("planScopeEnabled");
        return box.checked && !box.disabled;
    }

    /** 按方向与配置范围刷新范围行的可见性、文案与“单选 / 多选”口径。 */
    function renderScopeRow() {
        var oaToWx = scopeDirection() === "0";
        byId("planScopeRow").style.display = scopeOn() ? "" : "none";
        byId("planScopeHint").textContent = scopeOn()
            ? "已勾选：每次执行都按下面的范围（含下级）同步"
            : "未勾选 = 全量同步（与历史口径一致）";
        // A（OA→平台）：OA 是来源，分部/部门都可多选；B（平台→OA）：OA 是落点，分部单选必选、部门单选可不选
        state.subSingle = !oaToWx;
        state.deptSingle = !oaToWx;
        byId("planSubLabel").textContent = oaToWx ? "OA 分部" : "OA 落点分部";
        byId("planSubPicker").title = oaToWx
            ? "点击选择 OA 分部（可多选，含下级分部与部门）"
            : "点击选择 OA 落点分部（单选必选，新建的 OA 部门挂在该分部下）";
        byId("planSubInput").placeholder = oaToWx
            ? "选择 OA 分部（可多选）" : "选择 OA 落点分部（单选必选）";
        byId("planSubAllBtn").style.display = state.subSingle ? "none" : "";
        byId("planSubModalTitle").textContent = oaToWx ? "选择 OA 分部" : "选择 OA 落点分部";
        byId("planSubNoteText").innerHTML = state.subSingle
            ? "平台 → OA 时 OA 是落点：<span class='rule'>只能选一个分部（必选）</span>，"
                + "该分部（含其下级分部与部门）就是落点范围；单击即选中（再点其它分部即更换），"
                + "双击即确定并退出。"
            : "可多选，<span class='rule'>选中分部自动含其下级分部与部门</span>；"
                + "单击选中/取消，双击即确定并退出。";
        byId("planDeptScopeLabel").textContent = oaToWx ? "OA 部门" : "OA 落点部门";
        byId("planDeptScopePicker").title = oaToWx
            ? "点击选择 OA 部门（可多选，含下级部门）"
            : "点击选择 OA 落点部门（单选，可不选；先选落点分部）";
        byId("planDeptScopeInput").placeholder = oaToWx
            ? "选择 OA 部门（可多选）" : "选择 OA 落点部门（单选，可不选）";
        byId("planDeptScopeAllBtn").style.display = state.deptSingle ? "none" : "";
        byId("planDeptScopeModalTitle").textContent = oaToWx ? "选择 OA 部门" : "选择 OA 落点部门";
        byId("planDeptScopeNoteText").innerHTML = state.deptSingle
            ? "平台 → OA 时 OA 是落点：只能选一个部门，<span class='rule'>也可以不选</span>"
                + "（不选 = 新建部门直接挂在该落点分部下、成为该分部的顶级部门）。"
                + "单击即选中，双击即确定并退出。"
            : "可多选，<span class='rule'>选中部门自动含其下级部门</span>；"
                + "已选分部时只列出该分部（含下级分部）下的部门。单击选中/取消，双击即确定并退出。";
        byId("planWxLabel").textContent = state.platformLabel + "部门";
        byId("planWxModalTitle").textContent = "选择" + state.platformLabel + "部门";
        // 表单始终针对一个具体租户：平台侧部门就是该租户自己的部门快照，可以指定（留空 = root 节点）
        byId("planWxPicker").classList.toggle("disabled", formReadOnly());
        byId("planScopeNote").textContent = "";
        renderScopeInputs();
    }

    /** 三个范围选择框的显示文本（已选名称；未选时留空走 placeholder）。 */
    function renderScopeInputs() {
        byId("planSubInput").value = namesOf(state.subIds, subById, "分部");
        byId("planDeptScopeInput").value = namesOf(state.deptIds, deptById, "部门");
        if (state.wxDeptId) {
            var w = wxDeptById(state.wxDeptId);
            byId("planWxInput").value = (w && w.name) ? w.name : ("部门 " + state.wxDeptId);
        } else {
            byId("planWxInput").value = "";
        }
    }

    function namesOf(ids, lookup, fallbackPrefix) {
        return (ids || []).map(function (id) {
            var row = lookup(id);
            return (row && (row.name || row.fullname)) ? (row.name || row.fullname)
                : (fallbackPrefix + " " + id);
        }).join("、");
    }

    /* ---- 选择框通用：过滤 / 去重 / 选项 HTML ---- */

    function childMapOf(rows, parentKey) {
        var map = {};
        (rows || []).forEach(function (r) {
            var p = String(r[parentKey] == null ? "" : r[parentKey]);
            if (!map[p]) { map[p] = []; }
            map[p].push(String(r.id));
        });
        return map;
    }

    function rowTextOf(row, keys) {
        return keys.map(function (k) { return String(row[k] == null ? "" : row[k]); }).join(" ");
    }

    function filterRows(rows, keyword, keys) {
        var k = String(keyword == null ? "" : keyword).trim().toLowerCase();
        if (!k) { return rows || []; }
        return (rows || []).filter(function (r) {
            return rowTextOf(r, keys).toLowerCase().indexOf(k) >= 0;
        });
    }

    function uniqueIds(ids) {
        var seen = {};
        var out = [];
        (ids || []).forEach(function (id) {
            var v = String(id);
            if (!seen[v]) { seen[v] = true; out.push(v); }
        });
        return out;
    }

    /** 候选行 HTML：名称 + 编号/ID（列表式与树状共用，与页面其它选择框同一外观） */
    function scopeOptionHtml(id, name, tail, active, partial, tip) {
        var cls = "wx-pick-option pick-cols-wide" + (active ? " active" : (partial ? " partial" : ""));
        return "<button type='button' class='" + cls + "' data-id='" + esc(id) + "' title='" + esc(tip) + "'>"
            + "<span class='wx-pick-opt-name'>" + esc(name) + "</span>"
            + "<span>" + esc(tail) + "</span>"
            + "</button>";
    }

    function scopeListHead(first, second) {
        return "<div class='wx-pick-head pick-cols-wide'><span>" + esc(first) + "</span><span>"
            + esc(second) + "</span></div>";
    }

    /* ---- OA 分部（A 多选含下级 / B 单选必选） ---- */

    function subChildMap() { return childMapOf(state.subs, "parent"); }

    function subOptionHtml(s, partial) {
        var id = String(s.id);
        var active = state.subIds.indexOf(id) >= 0;
        var tip = state.subSingle
            ? ((active ? "已选中该落点分部（点其它分部即更换）" : "点击选择该分部作为 OA 落点")
                + "；落点含该分部的下级分部与部门")
            : ((active ? "点击取消选择" : "点击选择") + "；选中分部会自动含其全部下级分部与部门");
        tip += "｜" + (s.fullname || s.name || "");
        return scopeOptionHtml(id, s.name || s.fullname || ("分部 " + id),
            (s.code ? s.code : "-") + "（ID " + id + "）", active, !active && partial[id], tip);
    }

    function renderSubList() {
        var keyword = byId("planSubSearch").value;
        var rows = filterRows(state.subs, keyword, ["id", "code", "name", "fullname"]);
        var partial = OaonesPicker.partialSelected(state.subIds, subChildMap);
        var el = byId("planSubList");
        if (OaonesPicker.mode("planSub") === "tree") {
            el.className = "wx-pick-list is-tree";
            el.innerHTML = OaonesPicker.renderTree("planSub", rows, {
                parentOf: function (s) { return s.parent; },
                textOf: function (s) { return rowTextOf(s, ["id", "code", "name", "fullname"]); },
                keyword: keyword,
                emptyText: "没有匹配的分部",
                optionHtml: function (s) { return subOptionHtml(s, partial); }
            });
        } else {
            el.className = "wx-pick-list";
            var html = rows.length ? scopeListHead("分部名称", "分部编号 / ID") : "";
            rows.forEach(function (s) { html += subOptionHtml(s, partial); });
            el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的分部</div>";
        }
        subAllState();
    }

    function subAllState() {
        if (state.subSingle) {
            OaonesPicker.syncSelectAll("planSub", false);
            return false;
        }
        var ids = filterRows(state.subs, byId("planSubSearch").value, ["id", "code", "name", "fullname"])
            .map(function (s) { return String(s.id); });
        var allOn = ids.length > 0 && ids.every(function (id) { return state.subIds.indexOf(id) >= 0; });
        OaonesPicker.syncSelectAll("planSub", allOn);
        return allOn;
    }

    function toggleSub(id) {
        var key = String(id);
        if (state.subSingle) {
            if (state.subIds.length === 1 && state.subIds[0] === key) { return; }
            state.subIds = [key];
            // 换了落点分部，落点部门候选随之改变：已选落点部门清空
            state.deptIds = [];
            return;
        }
        var idx = state.subIds.indexOf(key);
        state.subIds = idx >= 0 ? state.subIds.filter(function (v) { return v !== key; })
            : state.subIds.concat([key]);
    }

    function refreshSubDom() {
        var opts = byId("planSubList").querySelectorAll(".wx-pick-option");
        for (var i = 0; i < opts.length; i++) {
            opts[i].classList.toggle("active",
                state.subIds.indexOf(String(opts[i].getAttribute("data-id"))) >= 0);
        }
    }

    function openSubPick() {
        if (formReadOnly()) {
            showToast("“全局默认配置”只允许系统管理员维护，当前账号只读查看", 1, 6000);
            return;
        }
        state.subBackup = state.subIds.slice();
        byId("planSubSearch").value = "";
        OaonesPicker.resetTree("planSub");
        renderSubList();
        showModal("planSubMask", "planSubModal");
        byId("planSubSearch").focus();
    }

    function closeSubPick(commit) {
        if (!commit) { state.subIds = state.subBackup || []; }
        state.subBackup = null;
        renderScopeInputs();
        hideModal("planSubMask", "planSubModal");
    }

    /* ---- OA 部门（候选受已选分部（含下级分部）前置条件过滤） ---- */

    /** 已选分部 + 全部下级分部（页面口径：选中分部自动含下级） */
    function selectedSubScope() {
        var ids = {};
        state.subIds.forEach(function (id) { ids[String(id)] = true; });
        var map = subChildMap();
        var grew = true;
        while (grew) {
            grew = false;
            Object.keys(map).forEach(function (p) {
                if (!ids[p]) { return; }
                (map[p] || []).forEach(function (c) {
                    if (!ids[c]) { ids[c] = true; grew = true; }
                });
            });
        }
        return ids;
    }

    /** 部门候选的前置条件：已选分部时只列出该分部（含下级分部）下的部门。 */
    function deptScopePrecondition() {
        var subs = selectedSubScope();
        var hasSub = Object.keys(subs).length > 0;
        return {
            tip: hasSub ? "只列出已选分部（含下级分部）下的部门" : "",
            filter: function (d) { return !hasSub || !!subs[String(d.subcompanyid1)]; }
        };
    }

    function deptScopeOptionHtml(d, partial) {
        var id = String(d.id);
        var active = state.deptIds.indexOf(id) >= 0;
        var tip = state.deptSingle
            ? ((active ? "已选中该落点部门（点其它部门即更换）" : "点击选择该部门作为 OA 落点")
                + "；落点含该部门的下级部门")
            : ((active ? "点击取消选择" : "点击选择") + "；选中部门会自动含其全部下级部门");
        tip += "｜" + (d.fullname || d.name || "");
        return scopeOptionHtml(id, d.name || d.fullname || ("部门 " + id),
            (d.code ? d.code : "-") + "（ID " + id + "）", active, !active && partial[id], tip);
    }

    function renderScopeDeptList() {
        var keyword = byId("planDeptScopeSearch").value;
        var rows = filterRows(state.depts, keyword, ["id", "code", "name", "fullname"]);
        var partial = OaonesPicker.partialSelected(state.deptIds, deptChildMap);
        var el = byId("planDeptScopeList");
        OaonesPicker.preconditionTip("planDeptScope", "planDeptScopeNote");
        if (OaonesPicker.mode("planDeptScope") === "tree") {
            el.className = "wx-pick-list is-tree";
            el.innerHTML = OaonesPicker.renderTree("planDeptScope", rows, {
                parentOf: function (d) { return d.parent; },
                textOf: function (d) { return rowTextOf(d, ["id", "code", "name", "fullname"]); },
                keyword: keyword,
                emptyText: "没有匹配的部门",
                optionHtml: function (d) { return deptScopeOptionHtml(d, partial); }
            });
        } else {
            el.className = "wx-pick-list";
            var list = OaonesPicker.applyPrecondition("planDeptScope", rows);
            var html = list.length ? scopeListHead("部门名称", "部门编号 / ID") : "";
            list.forEach(function (d) { html += deptScopeOptionHtml(d, partial); });
            el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的部门</div>";
        }
        scopeDeptAllState();
    }

    function scopeDeptAllState() {
        if (state.deptSingle) {
            OaonesPicker.syncSelectAll("planDeptScope", false);
            return false;
        }
        var ids = OaonesPicker.applyPrecondition("planDeptScope",
            filterRows(state.depts, byId("planDeptScopeSearch").value, ["id", "code", "name", "fullname"]))
            .map(function (d) { return String(d.id); });
        var allOn = ids.length > 0 && ids.every(function (id) { return state.deptIds.indexOf(id) >= 0; });
        OaonesPicker.syncSelectAll("planDeptScope", allOn);
        return allOn;
    }

    function toggleScopeDept(id) {
        var key = String(id);
        if (state.deptSingle) {
            if (state.deptIds.length === 1 && state.deptIds[0] === key) { return; }
            state.deptIds = [key];
            return;
        }
        var idx = state.deptIds.indexOf(key);
        state.deptIds = idx >= 0 ? state.deptIds.filter(function (v) { return v !== key; })
            : state.deptIds.concat([key]);
    }

    function refreshScopeDeptDom() {
        var opts = byId("planDeptScopeList").querySelectorAll(".wx-pick-option");
        for (var i = 0; i < opts.length; i++) {
            opts[i].classList.toggle("active",
                state.deptIds.indexOf(String(opts[i].getAttribute("data-id"))) >= 0);
        }
    }

    function openScopeDeptPick() {
        if (formReadOnly()) {
            showToast("“全局默认配置”只允许系统管理员维护，当前账号只读查看", 1, 6000);
            return;
        }
        state.deptScopeBackup = state.deptIds.slice();
        byId("planDeptScopeSearch").value = "";
        OaonesPicker.resetTree("planDeptScope");
        renderScopeDeptList();
        showModal("planDeptScopeMask", "planDeptScopeModal");
        byId("planDeptScopeSearch").focus();
    }

    function closeScopeDeptPick(commit) {
        if (!commit) { state.deptIds = state.deptScopeBackup || []; }
        state.deptScopeBackup = null;
        renderScopeInputs();
        hideModal("planDeptScopeMask", "planDeptScopeModal");
    }

    /* ---- 平台部门（单选，留空 = 租户 root 节点） ---- */

    function wxDeptById(id) {
        var key = String(id == null ? "" : id);
        for (var i = 0; i < state.wxDepts.length; i++) {
            if (String(state.wxDepts[i].id) === key) { return state.wxDepts[i]; }
        }
        return null;
    }

    function wxOptionHtml(d) {
        var id = String(d.id);
        var active = String(state.wxDeptId) === id;
        var tip = active ? "已选中该部门（再点其它部门即更换）" : "点击选择该部门（含其下级部门）";
        return scopeOptionHtml(id, d.name || ("部门 " + id), "ID " + id, active, false, tip);
    }

    function renderWxList() {
        var keyword = byId("planWxSearch").value;
        var rows = filterRows(state.wxDepts, keyword, ["id", "name"]);
        var el = byId("planWxList");
        if (OaonesPicker.mode("planWx") === "tree") {
            el.className = "wx-pick-list is-tree";
            el.innerHTML = OaonesPicker.renderTree("planWx", rows, {
                parentOf: function (d) { return d.parentid; },
                textOf: function (d) { return rowTextOf(d, ["id", "name"]); },
                keyword: keyword,
                emptyText: "没有匹配的部门（先“更新部门快照”再设置）",
                optionHtml: function (d) { return wxOptionHtml(d); }
            });
        } else {
            el.className = "wx-pick-list";
            var html = rows.length ? scopeListHead("部门名称", "部门ID") : "";
            rows.forEach(function (d) { html += wxOptionHtml(d); });
            el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的部门（先“更新部门快照”再设置）</div>";
        }
    }

    function toggleWxDept(id) {
        // 单选：点同一行不取消（清空用弹框里的“清空（= root 节点）”或输入框的 ×）
        state.wxDeptId = String(id);
    }

    function refreshWxDom() {
        var opts = byId("planWxList").querySelectorAll(".wx-pick-option");
        for (var i = 0; i < opts.length; i++) {
            opts[i].classList.toggle("active",
                String(opts[i].getAttribute("data-id")) === String(state.wxDeptId));
        }
    }

    function openWxPick() {
        if (formReadOnly()) {
            showToast("“全局默认配置”只允许系统管理员维护，当前账号只读查看", 1, 6000);
            return;
        }
        if (byId("planWxPicker").classList.contains("disabled")) {
            showToast("“全局默认配置”的平台侧固定为各租户的 root 节点，不能指定具体部门", 1, 6000);
            return;
        }
        state.wxBackup = state.wxDeptId;
        byId("planWxSearch").value = "";
        OaonesPicker.resetTree("planWx");
        renderWxList();
        showModal("planWxMask", "planWxModal");
        byId("planWxSearch").focus();
    }

    function closeWxPick(commit) {
        if (!commit) { state.wxDeptId = state.wxBackup || ""; }
        state.wxBackup = null;
        renderScopeInputs();
        hideModal("planWxMask", "planWxModal");
    }

    /* ==================================================================
     * 七、同步明细弹窗（设置表行尾“结果详情” → 该次同步的统计 + 逐条明细）
     * ================================================================== */

    var detailPager = OaonesTable.pager({
        key: "syncCenter.planDetail",
        el: byId("detailPager"),
        onChange: function () { renderDetailRows(); }
    });

    function logDirText(r) {
        var short = state.platformShort;
        return String(r.direction) === "0" ? "OA→" + short
            : (String(r.direction) === "1" ? short + "→OA" : String(r.direction == null ? "" : r.direction));
    }

    function logStatusText(r) {
        if (String(r.run_status) === "1") { return "部分成功"; }
        if (String(r.run_status) === "2") { return "失败"; }
        return "成功";
    }

    /** 行尾“结果详情”：先按日志ID取该条日志（拿统计文本），再拉逐条明细。 */
    function showDetail(logId) {
        Oaones.api("sync.logs", { cfg_id: state.cfgId || "", direction: "all", limit: 200 },
            function (listRes) {
                var row = null;
                var rows = (listRes.code === 0 && listRes.data) ? listRes.data : [];
                for (var i = 0; i < rows.length; i++) {
                    if (String(rows[i].id) === String(logId)) { row = rows[i]; break; }
                }
                openDetailModal(logId, row);
            });
    }

    function openDetailModal(logId, row) {
        state.detail = { rows: [], type: "all", keyword: "", log: row };
        byId("detailTitle").textContent = "同步明细 · 日志 " + logId;
        byId("detailMeta").textContent = row
            ? ("本次同步 " + (row.dept_count || 0) + " 个部门 " + (row.user_count || 0) + " 个员工"
                + (Number(row.nodept_count) > 0
                    ? ("，其中 " + row.nodept_count + " 个员工没有找到部门"
                        + (row.nodept_dept ? "，在「" + row.nodept_dept + "」下" : ""))
                    : "")
                + "｜租户：" + (row.corp_name || "") + "｜方向：" + logDirText(row)
                + "｜结果：" + logStatusText(row) + "｜总数 " + (row.total_count || 0)
                + "，成功 " + (row.success_count || 0) + "，跳过 " + (row.skip_count || 0)
                + "，失败 " + (row.fail_count || 0) + "｜" + (row.start_time || "") + " ~ "
                + (row.end_time || ""))
            : "";
        byId("detailText").textContent = row && row.detail ? row.detail : "";
        byId("detailKeyword").value = "";
        state.detail.keyword = "";
        var btns = byId("detailTypeSwitch").querySelectorAll(".view-btn");
        for (var i = 0; i < btns.length; i++) {
            btns[i].classList.toggle("active", btns[i].getAttribute("data-type") === "all");
        }
        showModal("detailMask", "detailModal");
        Oaones.api("sync.logItems", {
            log_id: logId, cfg_id: state.cfgId || "", item_type: "all", limit: 5000
        }, function (res) {
            state.detail.rows = (res.code === 0 && res.data) ? res.data : [];
            renderDetail();
        });
    }

    function detailFilteredRows() {
        var rows = state.detail.rows || [];
        if (state.detail.type === "dept") {
            // 部门口径含“分部”：平台侧只有部门概念，A = OA 分部时该分部也落成一个部门
            rows = rows.filter(function (r) {
                var t = String(r.item_type);
                return t === "0" || t === "2";
            });
        } else if (state.detail.type === "user") {
            rows = rows.filter(function (r) { return String(r.item_type) === "1"; });
        }
        var k = String(state.detail.keyword || "").trim().toLowerCase();
        if (!k) { return rows; }
        return rows.filter(function (r) {
            return [r.oa_id, r.oa_name, r.wx_id, r.wx_name, r.parent_id, r.parent_name, r.message]
                .map(function (v) { return String(v == null ? "" : v); })
                .join(" ").toLowerCase().indexOf(k) >= 0;
        });
    }

    function renderDetail() {
        var rows = detailFilteredRows();
        var create = 0;
        var update = 0;
        var skip = 0;
        var fail = 0;
        rows.forEach(function (r) {
            var op = String(r.op);
            if (op === "0") { create++; }
            else if (op === "1") { update++; }
            else if (op === "2") { skip++; }
            else if (op === "3") { fail++; }
        });
        var statsEl = byId("detailStats");
        statsEl.style.display = "";
        statsEl.innerHTML = ""
            + "<span class='bind-stat'><span>明细</span><b>" + rows.length + "</b> 条</span>"
            + "<span class='bind-stat'><span>新增</span><b>" + create + "</b> 条</span>"
            + "<span class='bind-stat'><span>更新</span><b>" + update + "</b> 条</span>"
            + "<span class='bind-stat'><span>跳过</span><b>" + skip + "</b> 条</span>"
            + "<span class='bind-stat'><span>失败</span><b>" + fail + "</b> 条</span>";
        detailPager.resetIfChanged([state.detail.type, state.detail.keyword].join("|"));
        renderDetailRows();
    }

    function renderDetailRows() {
        var rows = detailPager.apply(detailFilteredRows());
        var body = "";
        rows.forEach(function (r) {
            body += "<tr>"
                + "<td>" + esc(detailTypeText(r.item_type)) + "</td>"
                + "<td>" + detailOpTag(r.op) + "</td>"
                + "<td>" + detailOaCell(r) + "</td>"
                + "<td>" + detailWxCell(r) + "</td>"
                + "<td>" + esc(r.parent_name || "")
                + (String(r.parent_id || "").length ? "（" + esc(r.parent_id) + "）" : "") + "</td>"
                + "<td>" + esc(r.message || "") + "</td>"
                + "</tr>";
        });
        byId("detailBody").innerHTML = body;
        detailPager.show();
        detailPager.render();
        OaonesTable.resizable(byId("detailTable"), { key: "syncCenter.planDetailTable" });
        var emptyEl = byId("detailEmpty");
        var noItem = (state.detail.rows || []).length === 0;
        emptyEl.style.display = rows.length ? "none" : "block";
        emptyEl.textContent = noItem ? "该日志没有逐条明细（历史日志只有汇总文本）" : "没有匹配的明细";
    }

    function detailOpTag(op) {
        var text = String(op) === "0" ? "新增" : (String(op) === "1" ? "更新"
            : (String(op) === "2" ? "跳过" : "失败"));
        var cls = String(op) === "0" ? "tag bound" : (String(op) === "1" ? "tag system"
            : (String(op) === "2" ? "tag unbound" : "tag fail"));
        return "<span class='" + cls + "'>" + esc(text) + "</span>";
    }

    /** 明细对象类型文案：0 部门 / 1 人员 / 2 分部（OA 分部在平台侧落成一个部门） */
    function detailTypeText(itemType) {
        var t = String(itemType);
        if (t === "1") { return "人员"; }
        if (t === "2") { return "分部"; }
        return "部门";
    }

    /** OA 侧对象：泛微对象一律输出标准页面超链接（平台侧保持纯文本） */
    function detailOaCell(r) {
        var id = String(r.oa_id == null ? "" : r.oa_id);
        var name = String(r.oa_name == null ? "" : r.oa_name);
        if (!id) { return esc(name || "-"); }
        if (String(r.item_type) === "2") {
            return OaonesHrm.sub(id, name || ("OA 分部 " + id));
        }
        if (String(r.item_type) === "1") {
            return OaonesHrm.user(id, name || ("OA 人员 " + id));
        }
        return OaonesHrm.dept(id, name || ("OA 部门 " + id));
    }

    /** 平台侧对象：纯文本（平台部门/成员在泛微没有标准页面） */
    function detailWxCell(r) {
        var name = String(r.wx_name == null ? "" : r.wx_name);
        var id = String(r.wx_id == null ? "" : r.wx_id);
        if (!name && !id) { return "-"; }
        return esc(name ? (name + (id ? "（ID " + id + "）" : "")) : id);
    }

    /* ==================================================================
     * 八、事件绑定与初始化
     * ================================================================== */

    function bindEvents() {
        byId("planCfgSelect").addEventListener("change", onCfgChange);
        byId("planScopeSelect").addEventListener("change", function () {
            // 配置范围只有“当前租户配置”一档（页面已去掉“全局默认配置”选项）：这里保留兜底，
            // 换了范围就等于离开“正在编辑的那条规则”，回到“新增一条”
            state.scope = byId("planScopeSelect").value === "global" ? "global" : "tenant";
            state.editId = "";
            fillForm();
        });
        byId("planDirection").addEventListener("change", function () {
            state.editId = "";
            fillForm();
        });
        byId("planSaveBtn").addEventListener("click", function () { savePlan(false, false); });
        // 注意：这两个函数带“规则 id”入参，直接挂 addEventListener 会把 click 事件当成 id 传进去
        byId("planDeleteBtn").addEventListener("click", function () { deletePlan(); });
        byId("planRunNowBtn").addEventListener("click", function () { runNow(); });
        byId("planResetBtn").addEventListener("click", function () {
            // “重置表单” = 回到“新增一条规则”：清空表单并退出编辑该条的状态
            state.editId = "";
            resetForm();
            renderFormState(null);
        });
        byId("planCronPreviewBtn").addEventListener("click", previewPlan);
        // 计划任务：同步方式单选切换两块互斥显示（轮询间隔（分钟）/ 高级模式（cron））；
        // 切换只改显示，两种取值都留在表单里，保存时一起提交（来回切不丢值）
        byId("planModeInterval").addEventListener("change", function () { switchPlanMode("0"); });
        byId("planModeCron").addEventListener("change", function () { switchPlanMode("1"); });
        // “按范围同步”开关：勾选/取消只切换范围行的显示（选择的值保留在 state 里，重新勾选即恢复）
        byId("planScopeEnabled").addEventListener("change", renderScopeRow);

        /* ---- 同步范围：OA 分部 / OA 部门 / 平台部门（弹框选择，双击即确定） ---- */
        byId("planSubPicker").addEventListener("click", function (e) {
            if (e.target && e.target.closest && e.target.closest("#planSubClear")) { return; }
            openSubPick();
        });
        byId("planSubClear").addEventListener("click", function (e) {
            e.stopPropagation();
            state.subIds = [];
            if (state.subSingle) { state.deptIds = []; }
            renderScopeInputs();
        });
        byId("planSubSearch").addEventListener("input", function () { renderSubList(); });
        byId("planSubList").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            toggleSub(btn.getAttribute("data-id"));
            refreshSubDom();
        });
        byId("planSubList").addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            // 双击前已收到两次单击（同一行重复点不会取消选择），这里确保选中态后再确定
            if (state.subIds.indexOf(String(btn.getAttribute("data-id"))) < 0) {
                toggleSub(btn.getAttribute("data-id"));
                refreshSubDom();
            }
            closeSubPick(true);
        });
        byId("planSubOk").addEventListener("click", function () { closeSubPick(true); });
        byId("planSubCancel").addEventListener("click", function () { closeSubPick(false); });
        byId("planSubClose").addEventListener("click", function () { closeSubPick(false); });
        byId("planSubMask").addEventListener("click", function (e) {
            if (e.target === this) { closeSubPick(false); }
        });
        byId("planSubClearBtn").addEventListener("click", function () {
            state.subIds = [];
            if (state.subSingle) { state.deptIds = []; }
            renderSubList();
        });

        byId("planDeptScopePicker").addEventListener("click", function (e) {
            if (e.target && e.target.closest && e.target.closest("#planDeptScopeClear")) { return; }
            openScopeDeptPick();
        });
        byId("planDeptScopeClear").addEventListener("click", function (e) {
            e.stopPropagation();
            state.deptIds = [];
            renderScopeInputs();
        });
        byId("planDeptScopeSearch").addEventListener("input", function () { renderScopeDeptList(); });
        byId("planDeptScopeList").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            toggleScopeDept(btn.getAttribute("data-id"));
            refreshScopeDeptDom();
        });
        byId("planDeptScopeList").addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            if (state.deptIds.indexOf(String(btn.getAttribute("data-id"))) < 0) {
                toggleScopeDept(btn.getAttribute("data-id"));
                refreshScopeDeptDom();
            }
            closeScopeDeptPick(true);
        });
        byId("planDeptScopeOk").addEventListener("click", function () { closeScopeDeptPick(true); });
        byId("planDeptScopeCancel").addEventListener("click", function () { closeScopeDeptPick(false); });
        byId("planDeptScopeClose").addEventListener("click", function () { closeScopeDeptPick(false); });
        byId("planDeptScopeMask").addEventListener("click", function (e) {
            if (e.target === this) { closeScopeDeptPick(false); }
        });
        byId("planDeptScopeClearBtn").addEventListener("click", function () {
            state.deptIds = [];
            renderScopeDeptList();
        });

        byId("planWxPicker").addEventListener("click", function (e) {
            if (e.target && e.target.closest && e.target.closest("#planWxClear")) { return; }
            openWxPick();
        });
        byId("planWxClear").addEventListener("click", function (e) {
            e.stopPropagation();
            state.wxDeptId = "";
            renderScopeInputs();
        });
        byId("planWxSearch").addEventListener("input", function () { renderWxList(); });
        byId("planWxList").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            toggleWxDept(btn.getAttribute("data-id"));
            refreshWxDom();
        });
        byId("planWxList").addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            toggleWxDept(btn.getAttribute("data-id"));
            refreshWxDom();
            closeWxPick(true);
        });
        byId("planWxOk").addEventListener("click", function () { closeWxPick(true); });
        byId("planWxCancel").addEventListener("click", function () { closeWxPick(false); });
        byId("planWxClose").addEventListener("click", function () { closeWxPick(false); });
        byId("planWxMask").addEventListener("click", function (e) {
            if (e.target === this) { closeWxPick(false); }
        });
        byId("planWxClearBtn").addEventListener("click", function () {
            state.wxDeptId = "";
            renderWxList();
        });

        /* ---- 首次检测弹框：范围内已有绑定关系 → 可选“强制同步并保存”（保存后立即同步一次） ---- */
        byId("planConflictForce").addEventListener("click", function () {
            closeConflictDialog();
            savePlan(true, true);
        });
        byId("planConflictCancel").addEventListener("click", closeConflictDialog);
        byId("planConflictClose").addEventListener("click", closeConflictDialog);
        byId("planConflictMask").addEventListener("click", function (e) {
            if (e.target === this) { closeConflictDialog(); }
        });

        /* ---- 同步明细弹窗（设置表行尾“结果详情”） ---- */
        byId("detailClose").addEventListener("click", function () { hideModal("detailMask", "detailModal"); });
        byId("detailOkBtn").addEventListener("click", function () { hideModal("detailMask", "detailModal"); });
        byId("detailMask").addEventListener("click", function (e) {
            if (e.target === this) { hideModal("detailMask", "detailModal"); }
        });
        byId("detailKeyword").addEventListener("input", function () {
            state.detail.keyword = this.value;
            renderDetail();
        });
        byId("detailTypeSwitch").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".view-btn") : null;
            if (!btn) { return; }
            state.detail.type = btn.getAttribute("data-type") || "all";
            var all = this.querySelectorAll(".view-btn");
            for (var i = 0; i < all.length; i++) {
                all[i].classList.toggle("active", all[i] === btn);
            }
            renderDetail();
        });

        // 默认 OA 部门：弹框选择（单击选中 / 双击即确定并退出）
        byId("planDeptPicker").addEventListener("click", function (e) {
            if (e.target && e.target.id === "planDeptClear") {
                state.deptValue = "";
                renderDeptInput();
                return;
            }
            openPlanDeptPick();
        });
        byId("planDeptList").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            togglePlanDept(btn.getAttribute("data-id"));
            refreshPlanDeptDom();
        });
        byId("planDeptList").addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            // 双击前已收到两次单击（同一行重复点不会取消选择），这里确保选中态后再确定
            togglePlanDept(btn.getAttribute("data-id"));
            refreshPlanDeptDom();
            renderDeptInput();
            closePlanDeptPick(true);
        });
        byId("planDeptSearch").addEventListener("input", renderPlanDeptList);
        byId("planDeptOk").addEventListener("click", function () {
            renderDeptInput();
            closePlanDeptPick(true);
        });
        byId("planDeptCancel").addEventListener("click", function () { closePlanDeptPick(false); });
        byId("planDeptClose").addEventListener("click", function () { closePlanDeptPick(false); });
        byId("planDeptMask").addEventListener("click", function (e) {
            if (e.target === this) { closePlanDeptPick(false); }
        });
        byId("planDeptClearBtn").addEventListener("click", function () {
            state.deptValue = "";
            refreshPlanDeptDom();
            renderDeptInput();
            closePlanDeptPick(true);
        });

        // 设置表行内操作：编辑 / 暂停·启动 / 立即执行 / 结果详情 / 删除
        byId("planBody").addEventListener("click", function (e) {
            var el = e.target && e.target.closest ? e.target.closest("button") : null;
            if (!el) { return; }
            var detailId = el.getAttribute("data-plan-detail");
            if (detailId !== null && detailId !== "") {
                showResultDetail(detailId);
                return;
            }
            var override = el.getAttribute("data-plan-override");
            if (override !== null) {
                // 覆盖为当前租户配置：表单切到“当前租户配置 + 该方向”，并按这条全局默认规则预填；
                // 保存后即成为该租户自己的第 1 条规则（该方向从此不再套用全局默认）
                if (!state.cfgId) {
                    showToast("请先在“租户”下拉里选择一个租户，再点“覆盖为当前租户配置”", 1, 7000);
                    return;
                }
                var src = rowById(override);
                state.scope = "tenant";
                state.editId = "";
                byId("planScopeSelect").value = "tenant";
                byId("planDirection").value = String(src ? src.direction : "0");
                fillForm();
                showToast("已切到“当前租户配置”并预填了这条全局默认规则，"
                    + "改完点“保存设置”即新增该租户自己的第 1 条规则", 0, 6000);
                return;
            }
            var stateToken = el.getAttribute("data-plan-state");
            var editToken = el.getAttribute("data-plan-edit");
            var runToken = el.getAttribute("data-plan-run");
            var deleteToken = el.getAttribute("data-plan-delete");
            if (stateToken !== null) {
                // 暂停 / 启动：按钮上带“规则id|目标状态”，直接调接口后刷新列表（不切表单）
                var stateParts = String(stateToken).split("|");
                setPlanState(stateParts[0], stateParts[1]);
                return;
            }
            if (editToken !== null) { selectRule(editToken); return; }
            if (runToken !== null) { runNow(runToken); return; }
            if (deleteToken !== null) { deletePlan(deleteToken); }
        });
    }

    function init() {
        state.type = parseInt(window.OAONES_SYNC_TYPE || "0", 10) || 0;
        state.platformLabel = window.OAONES_SYNC_LABEL || "企业微信";
        state.platformShort = window.OAONES_SYNC_SHORT || "企微";
        state.sysAdmin = !!(window.OAONES_USER && window.OAONES_USER.sysAdmin);
        if (window.OaonesPicker && OaonesPicker.register) {
            OaonesPicker.register("planDept", renderPlanDeptList);
            // 同步范围三个选择框：列表式/树状切换 + 全选（多选时）+ “先分部、后部门”前置条件
            OaonesPicker.register("planSub", renderSubList);
            OaonesPicker.register("planDeptScope", renderScopeDeptList);
            OaonesPicker.register("planWx", renderWxList);
            OaonesPicker.setPrecondition("planDeptScope", deptScopePrecondition);
            OaonesPicker.setSelectAll("planSub", function () {
                if (state.subSingle) { return false; }
                var ids = filterRows(state.subs, byId("planSubSearch").value,
                    ["id", "code", "name", "fullname"]).map(function (s) { return String(s.id); });
                var on = subAllState();
                var next = [];
                if (on) {
                    var drop = {};
                    ids.forEach(function (v) { drop[v] = true; });
                    next = state.subIds.filter(function (v) { return !drop[v]; });
                } else {
                    next = state.subIds.concat(ids);
                }
                state.subIds = uniqueIds(next);
                renderSubList();
                return subAllState();
            });
            OaonesPicker.setSelectAll("planDeptScope", function () {
                if (state.deptSingle) { return false; }
                var ids = OaonesPicker.applyPrecondition("planDeptScope",
                    filterRows(state.depts, byId("planDeptScopeSearch").value,
                        ["id", "code", "name", "fullname"])).map(function (d) { return String(d.id); });
                var on = scopeDeptAllState();
                var next = [];
                if (on) {
                    var drop = {};
                    ids.forEach(function (v) { drop[v] = true; });
                    next = state.deptIds.filter(function (v) { return !drop[v]; });
                } else {
                    next = state.deptIds.concat(ids);
                }
                state.deptIds = uniqueIds(next);
                renderScopeDeptList();
                return scopeDeptAllState();
            });
        }
        bindEvents();
        loadSchedulerStatus();
        loadCfgs();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
