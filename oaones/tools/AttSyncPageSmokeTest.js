/**
 * 考勤同步页面自检（node oaones/tools/AttSyncPageSmokeTest.js）。
 *
 * 只做**静态一致性核对**（不需要浏览器 / DOM 沙箱），核对三件事：
 *   1. 页壳与面板的接线：页壳引了公共脚本、面板里有三个页签对应的面板块与全部弹框；
 *   2. `js/attSync.js` 里 `byId("xxx")` 用到的每个 id，都能在页壳 / 面板里找到（防“改了 id 忘了改 JS”）；
 *   3. JS 调用的 action 都在 `AttSyncController` 里存在（防“前端调了个不存在的 action”）。
 *
 * 与 tools/MtgSyncDirTabSmokeTest.js（会议同步的页签自检）、
 * tools/OaonesUiConstraintSmokeTest.js（全模块 UI 硬约束）同一套“提交前自检”口径。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");

    var root = path.resolve(__dirname, "..");            // oaones/
    var jsp = read(path.join(root, "attSync", "attSync.jsp"));
    var panel = read(path.join(root, "attSync", "attSyncPanel.jsp"));
    var js = read(path.join(root, "js", "attSync.js"));
    var controller = read(path.resolve(root, "..", "src", "com", "customization", "oaones", "appsyn",
        "controller", "AttSyncController.java"));

    var failed = 0;

    function read(file) {
        if (!fs.existsSync(file)) {
            console.error("缺少文件：" + file);
            process.exit(1);
        }
        return fs.readFileSync(file, "utf8");
    }

    function check(name, condition, detail) {
        if (condition) {
            console.log("  ✓ " + name);
            return;
        }
        failed++;
        console.log("  ✗ " + name + (detail ? "：" + detail : ""));
    }

    console.log("【1】页壳接线");
    check("登录校验（HrmUserVarify）", jsp.indexOf("HrmUserVarify.getUser") >= 0);
    check("引了 assets.jsp", jsp.indexOf("/oaones/assets.jsp") >= 0);
    check("引了 js/attSync.js（带 ?v=）", /js\/attSync\.js\?v=/.test(jsp));
    ["toast.js", "picker.js", "help.js", "hrmlink.js", "table.js", "datetime.js", "api.js"].forEach(function (one) {
        check("引了 " + one, jsp.indexOf("/oaones/js/" + one) >= 0);
    });
    check("平台切换（企微 / 钉钉 / 飞书）", jsp.indexOf("platform=wecom") >= 0
        && jsp.indexOf("platform=dingtalk") >= 0 && jsp.indexOf("platform=feishu") >= 0);
    check("三个页签（同步设置 / 同步日志 / 打卡明细）",
        jsp.indexOf('data-tab="setting"') >= 0 && jsp.indexOf('data-tab="log"') >= 0
        && jsp.indexOf('data-tab="item"') >= 0);
    check("页壳引了面板 attSyncPanel.jsp", jsp.indexOf("attSyncPanel.jsp") >= 0);
    check("页壳注入了平台 / 用户的全局变量",
        jsp.indexOf("OAONES_ATT_TYPE") >= 0 && jsp.indexOf("OAONES_API_URL") >= 0
        && jsp.indexOf("OAONES_USER") >= 0);
    // 页头不放工具条那一行：登录人角色提示 / 问号说明 / 「刷新」按钮都不要（2026-10-15 现场反馈，
    // 与「统一消息中心」页壳同一口径——重新进入本菜单即重新取数，不需要页级刷新按钮；
    // 顶部问号说明整行去掉之后，隐藏的 .help-source 说明正文也没有入口了，一并删掉）。
    check("页头不放工具条那一行（角色提示 / 问号说明 / 刷新按钮）",
        jsp.indexOf("attUserTip") < 0 && jsp.indexOf("attRefreshBtn") < 0
        && jsp.indexOf("attHelpTip") < 0 && jsp.indexOf('class="help-source"') < 0);

    console.log("【2】面板结构");
    ["setting", "log", "item"].forEach(function (tab) {
        check("面板块 data-att-panel=\"" + tab + "\"", panel.indexOf('data-att-panel="' + tab + '"') >= 0);
    });
    ["attCfgModal", "attSubModal", "attDeptModal", "attUserModal", "attLogModal", "attPreviewModal",
        "attResultModal"].forEach(function (id) {
        var block = new RegExp('id="' + id + '"[\\s\\S]*?modal-head');
        check("弹框 " + id + " 有表头（可拖动）", block.test(panel));
        check("弹框 " + id + " 有关闭按钮（modal-x）",
            new RegExp('id="' + id + '"[\\s\\S]*?modal-x').test(panel));
    });
    check("计划任务时间段明细表（含新增按钮）",
        panel.indexOf("attPlanItemBody") >= 0 && panel.indexOf("attPlanAddBtn") >= 0);
    check("范围三要素（分部 / 部门 / 人员）", panel.indexOf("attScopeSubBtn") >= 0
        && panel.indexOf("attScopeDeptBtn") >= 0 && panel.indexOf("attScopeUserBtn") >= 0);
    // 考勤应用与租户绑定（2026-10-15 现场反馈）：在租户卡片上选一次 + 可重新校验，
    // 选完立刻保存并校验；新增 / 编辑设置的弹框里只读展示，不再重复选。
    check("租户卡片上的考勤应用 + 重新校验（弹框里只读）",
        panel.indexOf('id="attTenantBox"') >= 0
        && panel.indexOf('id="attCfgAppText"') >= 0
        && js.indexOf("data-att-tenant-app") >= 0 && js.indexOf("attsync.tenantApp") >= 0
        && js.indexOf("tenant-recheck") >= 0 && js.indexOf("attsync.check") >= 0);
    // 租户页签是**页面级**的（页壳里，三个页签共用）、而且排在功能页签**前面**（先选租户再看功能），
    // 日志 / 明细跟着它走、不再各放租户下拉
    check("租户页签在页壳里（页面级），日志 / 明细跟随当前租户",
        jsp.indexOf('id="attTenantTabs"') >= 0 && panel.indexOf('id="attTenantTabs"') < 0
        && panel.indexOf('id="attLogTenant"') >= 0 && panel.indexOf('id="attItemTenant"') >= 0
        && panel.indexOf('id="attLogCfg"') < 0 && panel.indexOf('id="attItemCfg"') < 0
        && js.indexOf("cfgid: state.activeTenant") >= 0);
    check("租户页签排在「同步设置 / 同步日志 / 打卡明细」前面（先选租户再看功能）",
        jsp.indexOf('id="attTenantTabs"') < jsp.indexOf('id="attTabs"')
        && jsp.indexOf('id="attTenantTabs"') > 0);
    check("预测后 10 次弹框", panel.indexOf("attPreviewBtn") >= 0 && panel.indexOf("attPreviewBody") >= 0);
    // 「新增 / 编辑同步设置」一次配完（考勤应用已挪到租户卡片）：只有「取消 + 保存」。
    check("一步弹框（取消 / 保存，无上一步 · 下一步）",
        panel.indexOf('id="attCfgBody"') >= 0 && panel.indexOf('id="attCfgSaveBtn"') >= 0
        && panel.indexOf("attCfgNextBtn") < 0 && panel.indexOf("attCfgPrevBtn") < 0
        && js.indexOf("showStep(") < 0 && js.indexOf("goNextStep") < 0);
    // 「整批执行一次」按钮已去掉（2026-10-15 现场反馈）：页面与脚本里都不再有它
    check("没有「整批执行一次」按钮", panel.indexOf("整批执行") < 0 && js.indexOf("runBatch") < 0
        && js.indexOf("run-batch") < 0);
    // 选择框与「日程同步」同一套：两列候选（ID + 名称）、单击选中 / 双击确定、全选、列表式 / 树状、清空选择。
    check("选择框是两列候选 + 全选 + 列表式 / 树状 + 清空选择",
        (panel.match(/data-pick-all="/g) || []).length === 3
        && (panel.match(/class="view-switch mini"/g) || []).length === 2
        && panel.indexOf('id="attSubClearBtn"') >= 0 && panel.indexOf('id="attUserClearBtn"') >= 0
        && js.indexOf("pickOptionHtml") >= 0 && js.indexOf("bindPickList") >= 0);
    check("同步范围下不放说明文字",
        panel.indexOf("选中分部即包含它") < 0 && panel.indexOf("选中部门即包含它") < 0
        && panel.indexOf("直接指定人；候选按泛微标准分权过滤") < 0);
    // 「新增同步设置」在租户卡片右上角（一个租户一条规则，按钮直接带上该租户的 cfg_id）。
    // 顶层工具栏不要再放全局按钮：那需要先选租户，而 toast 的确认框只渲染纯文本、选不了。
    check("「新增同步设置」在租户卡片头（每个租户一条，顶层无全局按钮）",
        /data-att-action="add-cfg"/.test(js) && /action === "add-cfg"/.test(js)
        && panel.indexOf("attCfgAddBtn") < 0);

    console.log("【3】JS 与页面的 id 对得上");
    var ids = {};
    var m;
    var idRe = /byId\("([A-Za-z0-9_]+)"\)/g;
    while ((m = idRe.exec(js)) !== null) {
        ids[m[1]] = true;
    }
    var missing = [];
    Object.keys(ids).forEach(function (id) {
        // 页面里没有的 id：也可能是 JS 自己拼出来的（确认弹框 / 列表块），一并认账
        var dynamic = js.indexOf('id="' + id + '"') >= 0 || js.indexOf("id=\\'" + id + "\\'") >= 0;
        if (!dynamic && panel.indexOf('id="' + id + '"') < 0 && jsp.indexOf('id="' + id + '"') < 0) {
            missing.push(id);
        }
    });
    check("JS 用到的 " + Object.keys(ids).length + " 个 id 都在页面里", missing.length === 0,
        missing.join(", "));

    console.log("【4】JS 调用的 action 都在控制器里");
    var actions = {};
    var actionRe = /call\("(attsync\.[A-Za-z]+)"/g;
    while ((m = actionRe.exec(js)) !== null) {
        actions[m[1]] = true;
    }
    var unknown = [];
    Object.keys(actions).forEach(function (action) {
        if (controller.indexOf('"' + action + '"') < 0) {
            unknown.push(action);
        }
    });
    check("JS 调用的 " + Object.keys(actions).length + " 个 action 都存在", unknown.length === 0,
        unknown.join(", "));
    check("页面导出了 OaonesAttSync（init / switchTo / reload）",
        js.indexOf("window.OaonesAttSync = {") >= 0 && js.indexOf("init:") >= 0
        && js.indexOf("switchTo:") >= 0 && js.indexOf("reload:") >= 0);

    /* ------------------------------------------------------------------
     * 【5】最小 DOM 沙箱：页壳 + 面板 + js/attSync.js 拼起来跑一遍
     *
     * 静态检查看不出“租户页签点不动 / 换应用没保存”这类问题：那些都是运行期行为。
     * 所以这里按「会议同步页签自检」同一套沙箱做法把页面跑一遍，核对 2026-10-15 现场反馈的四条：
     *   ① 租户渲染成页签、一屏只显示一个租户（切页签不重新取数）；
     *   ② 租户卡片上有考勤应用（换一个 = 立刻保存 + 校验，调 attsync.tenantApp）；
     *   ③ 新增 / 编辑设置是一次配完的弹框（不再有选应用 + 校验那一步）；
     *   ④ 没有「整批执行一次」按钮。
     * ------------------------------------------------------------------ */
    function runSandbox() {
        var vm = require("vm");
        var ids = new Set();
        var idRe = /id="([A-Za-z0-9_]+)"/g;
        var m;
        [jsp, panel].forEach(function (text) {
            idRe.lastIndex = 0;
            while ((m = idRe.exec(text)) !== null) { ids.add(m[1]); }
        });

        var handlers = {};
        var cache = {};
        var calls = [];

        function makeEl(id) {
            var el = {
                id: id, style: {}, value: "", checked: false, textContent: "", innerHTML: "",
                disabled: false, className: "", title: "",
                classList: {
                    add: function () {}, remove: function () {},
                    toggle: function () {}, contains: function () { return false; }
                },
                addEventListener: function (ev, fn) {
                    handlers[id] = handlers[id] || {};
                    handlers[id][ev] = handlers[id][ev] || [];
                    handlers[id][ev].push(fn);
                },
                querySelector: function () { return null; },
                querySelectorAll: function () { return []; },
                getAttribute: function () { return ""; },
                setAttribute: function () {},
                closest: function () { return null; },
                appendChild: function () {},
                focus: function () {}
            };
            el.parentNode = { classList: { toggle: function () {} }, removeChild: function () {} };
            return el;
        }

        var doc = {
            readyState: "complete",
            getElementById: function (id) {
                return ids.has(id) ? (cache[id] = cache[id] || makeEl(id)) : null;
            },
            createElement: function () { return makeEl("created"); },
            querySelector: function () { return null; },
            querySelectorAll: function () { return []; },
            addEventListener: function (ev, fn) {
                handlers["#document"] = handlers["#document"] || {};
                handlers["#document"][ev] = handlers["#document"][ev] || [];
                handlers["#document"][ev].push(fn);
            }
        };

        var fake = {
            "attsync.list": { code: 0, data: {
                tenants: [
                    { cfg_id: "11", tenant_name: "甲租户", corp_id: "ww-a", qy: "1", tenant_enabled: "1",
                        app_id: 5, app_name: "考勤应用A", app_ready: 1, check_state: 1,
                        check_msg: "考勤组 2 个", check_time: "2026-10-15 10:00:00",
                        // enable：后端按 yx（0 有效 / 1 无效）算好的布尔值
                        apps: [{ id: 5, app_name: "考勤应用A", enable: true, secret_ready: "1" }],
                        push_note: "反向写入方式：card（企业微信：走「为打卡人员补卡」写入）" },
                    { cfg_id: "22", tenant_name: "乙租户", corp_id: "ww-b", qy: "1", tenant_enabled: "0",
                        app_id: 0, app_name: "", app_ready: 0, check_state: 0, check_msg: "", check_time: "",
                        apps: [{ id: 7, app_name: "考勤应用B", enable: true, secret_ready: "1" },
                            { id: 8, app_name: "考勤应用C", enable: false, secret_ready: "0" }],
                        push_note: "—" }
                ],
                rows: [{ id: 101, cfg_id: "11", cfg_name: "工作日补跑", dir_text: "第三方 → OA", enabled: 1,
                    scope_text: "全部已绑定人员", data_text: "正常打卡", period_text: "签到 / 签退",
                    win_text: "当天", rule_text: "工作日 09:00–18:00", last_result: -1, fail_streak: 0 }],
                groups: [], today_workday: true
            } },
            "attsync.scheduler": { code: 0, data: { enabled: true, started: true, tick_seconds: 60 } },
            "attsync.logs": { code: 0, data: { rows: [], total: 0 } },
            "attsync.items": { code: 0, data: { rows: [], total: 0 } },
            "attsync.tenantApp": { code: 0, data: { ok: true, message: "校验通过", groups: 2, punch_count: 3 } },
            "attsync.check": { code: 0, data: { ok: true, message: "校验通过", groups: 2 } }
        };
        var apiCall = function (action, params, cb) {
            calls.push({ action: action, params: params || {} });
            cb(fake[action] || { code: 0, data: {} });
        };
        var sandbox = { window: null, document: doc, console: console };
        sandbox.Oaones = { api: apiCall, apiAt: apiCall };
        sandbox.OaonesToast = {
            show: function () {}, warn: function () {}, system: function () {},
            confirm: function (msg, cb) { if (cb) { cb(); } }
        };
        sandbox.OaonesDate = { init: function () {}, clear: function () {} };
        sandbox.OaonesHrm = { user: function (id, n) { return n || ""; }, userUrl: function (id) { return "#" + id; } };
        sandbox.OaonesTable = {
            pager: function () {
                return { apply: function (r) { return r || []; }, render: function () {}, hide: function () {},
                    show: function () {}, resetIfChanged: function () {} };
            },
            resizable: function () {}, syncCheckAll: function () {}, crossPageBar: function () {},
            listActionsHtml: function () { return ""; }
        };
        sandbox.OaonesPicker = {
            mode: function () { return "list"; }, renderTree: function () { return ""; },
            resetTree: function () {}, partialSelected: function () { return {}; },
            applyPrecondition: function (k, r) { return r || []; },
            register: function () {}, setSelectAll: function () {}, syncSelectAll: function () {}
        };
        sandbox.window = {
            document: doc, location: { href: "", replace: function () {} }, addEventListener: function () {},
            Oaones: sandbox.Oaones, OaonesToast: sandbox.OaonesToast, OaonesDate: sandbox.OaonesDate,
            OaonesHrm: sandbox.OaonesHrm, OaonesTable: sandbox.OaonesTable, OaonesPicker: sandbox.OaonesPicker,
            OAONES_ATT_TYPE: 0, OAONES_ATT_TAB: "setting", OAONES_API_URL: "/oaones/WeCom/api.jsp",
            OAONES_CFG_ALL_LABEL: "全部企微租户", OAONES_USER: { id: "1", sysAdmin: true }
        };

        var shellJs = (jsp.match(/<script(?![^>]*\bsrc=)[^>]*>[\s\S]*?<\/script>/g) || [])
            .map(function (one) {
                return one.replace(/^<script[^>]*>/, "").replace(/<\/script>$/, "");
            })
            .join("\n")
            .replace(/<%=[^%]*%>/g, "0")
            .replace(/<%.*?%>/g, "");

        try {
            vm.runInNewContext(js, sandbox, { filename: "js/attSync.js" });
            vm.runInNewContext(shellJs, sandbox, { filename: "attSync.jsp.js" });
        } catch (e) {
            check("脚本执行不抛错（实际抛错：" + e.message + "）", false);
            return;
        }
        check("脚本执行不抛错（页壳 + 面板 + js/attSync.js 拼起来能跑）", true);
        check("进入页面会调 attsync.list 拉同步设置",
            calls.filter(function (c) { return c.action === "attsync.list"; }).length > 0);

        var tabs = cache["attTenantTabs"];
        var box = cache["attTenantBox"];
        check("租户渲染成页签（两个租户两个页签，第一个是激活态）",
            !!tabs && (tabs.innerHTML.match(/data-att-action="tenant-tab"/g) || []).length === 2
            && tabs.innerHTML.indexOf('data-cfg-id="11" class="active"') >= 0);
        check("一屏只渲染当前租户的卡片（甲租户）",
            !!box && box.innerHTML.indexOf('data-att-tenant="11"') >= 0
            && box.innerHTML.indexOf('data-att-tenant="22"') < 0);
        check("租户卡片上有考勤应用下拉 + 「重新校验」",
            !!box && box.innerHTML.indexOf('data-att-tenant-app="11"') >= 0
            && box.innerHTML.indexOf('id="attTenantApp"') >= 0
            && box.innerHTML.indexOf("tenant-recheck") >= 0);
        // uf_oaones_app_bind.yx 是 0 有效 / 1 无效（与部门绑定表相反）：启用的应用不能被标成「已停用」
        check("启用中的应用不显示「已停用」（后端 enable 由 yx = 0 算出）",
            !!box && box.innerHTML.indexOf("（已停用）") < 0
            && box.innerHTML.indexOf('value="5" selected') >= 0
            && box.innerHTML.indexOf("value=\"5\" selected disabled") < 0);
        check("没有「整批执行一次」按钮",
            !!box && box.innerHTML.indexOf("整批执行") < 0 && box.innerHTML.indexOf("run-batch") < 0);

        function fire(key, ev, fn) {
            var list = (handlers[key] || {})[ev] || [];
            list.forEach(function (one) { one(fn); });
        }
        function attrHandler(pairs) {
            return { getAttribute: function (k) { return pairs[k] === undefined ? "" : pairs[k]; } };
        }

        // 切租户页签：只换显示，不重新取数
        var before = calls.length;
        fire("attTenantTabs", "click", { target: { closest: function () {
            return attrHandler({ "data-att-action": "tenant-tab", "data-cfg-id": "22" });
        } } });
        box = cache["attTenantBox"];
        check("切页签后只显示乙租户的卡片（且带「未指定考勤应用」提示）",
            !!box && box.innerHTML.indexOf('data-att-tenant="22"') >= 0
            && box.innerHTML.indexOf('data-att-tenant="11"') < 0
            && box.innerHTML.indexOf("还没有指定考勤应用") >= 0);
        check("真正停用的应用才标「已停用」并禁用（乙租户里 1 个停用 / 1 个启用）",
            !!box && (box.innerHTML.match(/（已停用）/g) || []).length === 1
            && box.innerHTML.indexOf('value="8" disabled') >= 0
            && box.innerHTML.indexOf('value="7" disabled') < 0);
        check("切页签不重新取数（只换显示）", calls.length === before);

        // 换租户的考勤应用：一步做掉「保存 + 立刻校验」
        var appEl = attrHandler({ "data-att-tenant-app": "22" });
        appEl.value = "7";
        fire("attTenantBox", "change", { target: { closest: function () { return appEl; } } });
        var appCall = calls.filter(function (c) { return c.action === "attsync.tenantApp"; }).pop();
        check("换考勤应用会调 attsync.tenantApp（带租户与应用ID）",
            !!appCall && appCall.params.cfg_id === "22" && appCall.params.app_id === "7");

        // 新增同步设置：一步配完的弹框（顶部只读展示租户的考勤应用）
        fire("attTenantBox", "click", { target: { closest: function () {
            return attrHandler({ "data-att-action": "add-cfg", "data-cfg-id": "22" });
        } } });
        check("点「新增同步设置」打开弹框",
            !!(cache["attCfgModal"] && cache["attCfgModal"].style.display === "block"));
        check("弹框里只读展示租户的考勤应用（不再选应用 / 校验）",
            !!(cache["attCfgAppText"]
                && cache["attCfgAppText"].textContent.indexOf("还没有指定考勤应用") >= 0));

        // 日志 / 明细跟着当前租户走：切页签按当前租户取数，切租户按新租户重取
        sandbox.window.OaonesAttSync.switchTo("log");
        var logCall = calls.filter(function (c) { return c.action === "attsync.logs"; }).pop();
        check("切到「同步日志」会按当前租户（乙租户）取数",
            !!logCall && String(logCall.params.cfgid) === "22"
            && !!(cache["attLogTenant"] && cache["attLogTenant"].textContent === "乙租户"));
        fire("attTenantTabs", "click", { target: { closest: function () {
            return attrHandler({ "data-att-action": "tenant-tab", "data-cfg-id": "11" });
        } } });
        var logCall2 = calls.filter(function (c) { return c.action === "attsync.logs"; }).pop();
        check("在日志页签里切租户会按新租户重取（甲租户）",
            !!logCall2 && String(logCall2.params.cfgid) === "11"
            && logCall2 !== logCall
            && !!(cache["attLogTenant"] && cache["attLogTenant"].textContent === "甲租户"));
        check("「当前租户」提示在工具条上（不再有租户下拉）",
            !!(cache["attItemTenant"] && cache["attItemTenant"].textContent === "甲租户"));
    }

    console.log("【5】租户页签 / 租户考勤应用（最小 DOM 沙箱）");
    runSandbox();

    if (failed > 0) {
        console.log("\n自检未通过：共 " + failed + " 项 ✗");
        process.exit(1);
    }
    console.log("\n自检通过 ✓");
})();
