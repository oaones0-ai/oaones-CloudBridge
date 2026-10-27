/**
 * 统一认证报表（一级菜单 /oaones/appsyn/authReport/authReport.jsp）交互脚本。
 *
 * 页面是**跨平台只读看板**，一次 `authreport.data` 取回全部数据：
 *   · 数字看板：各租户总人数 / 已绑定 / 未绑定（+ 合计）；
 *   · 今日堆柱状图：今日新增绑定、今日统一认证登录（按租户堆叠）；
 *   · 折线图：上面两个指标按日 / 按月的走势（前端切换粒度，不再回后端）。
 *
 * 图表用 E9 平台自带的 ECharts（页壳引入 /js/echart/echarts.min.js），
 * 不引第三方框架；提示统一走 OaonesToast。
 */
(function () {
    "use strict";

    function byId(id) { return document.getElementById(id); }
    function value(id) { var el = byId(id); return el ? String(el.value == null ? "" : el.value).trim() : ""; }
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function toast(msg, mode) {
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode == null ? 2 : mode }); }
        else if (window.console) { console.log(msg); }
    }
    function num(v) { var n = parseInt(v, 10); return isNaN(n) ? 0 : n; }

    var state = {
        subs: [],
        cfgs: [],
        subSel: {},          // id -> true
        cfgSel: {},          // id -> true
        grain: "day",
        data: null,
        charts: {}           // key -> echarts instance
    };

    /* ------------------------------------------------------------------
     * 多选下拉（分部 / 租户）
     * ------------------------------------------------------------------ */

    function renderMulti(group, list, textOf) {
        var box = byId(group === "sub" ? "arSubList" : "arCfgList");
        if (!box) { return; }
        if (!list || !list.length) {
            box.innerHTML = '<span class="ms-empty">暂无可选项</span>';
            return;
        }
        var html = "";
        list.forEach(function (item) {
            var id = String(item.id);
            var checked = (group === "sub" ? state.subSel : state.cfgSel)[id] ? " checked" : "";
            html += '<label class="ms-item"><input type="checkbox" class="ms-cb" data-group="' + group
                + '" value="' + esc(id) + '"' + checked + '>' + esc(textOf(item)) + "</label>";
        });
        box.innerHTML = html;
    }

    function selectedIds(group) {
        var map = group === "sub" ? state.subSel : state.cfgSel;
        return Object.keys(map).filter(function (k) { return map[k]; });
    }

    function refreshMultiLabel(group) {
        var btn = byId(group === "sub" ? "arSubBtn" : "arCfgBtn");
        if (!btn) { return; }
        var allText = group === "sub" ? "全部分部" : "全部租户";
        var ids = selectedIds(group);
        if (!ids.length) { btn.textContent = allText + " ▾"; return; }
        if (ids.length === 1) {
            var one = itemName(group, ids[0]);
            btn.textContent = (one || (allText.charAt(0) + "#" + ids[0])) + " ▾";
            return;
        }
        btn.textContent = "已选 " + ids.length + " 个" + (group === "sub" ? "分部" : "租户") + " ▾";
    }

    function itemName(group, id) {
        var list = group === "sub" ? state.subs : state.cfgs;
        for (var i = 0; i < (list || []).length; i++) {
            if (String(list[i].id) === String(id)) {
                return group === "sub" ? list[i].name : itemText(list[i]);
            }
        }
        return "";
    }

    function itemText(item) {
        var label = item.platform_text ? ("[" + item.platform_text + "] ") : "";
        return label + (item.cfg_name || "");
    }

    /* ------------------------------------------------------------------
     * 数据加载
     * ------------------------------------------------------------------ */

    function params() {
        return {
            sub_ids: selectedIds("sub").join(","),
            cfg_ids: selectedIds("cfg").join(","),
            from_date: value("arFrom"),
            to_date: value("arTo")
        };
    }

    function search() {
        var wrap = byId("arCards");
        if (wrap) { wrap.innerHTML = '<div class="kpi-empty">加载中…</div>'; }
        Oaones.api("authreport.data", params(), function (res) {
            if (!res || res.code !== 0) { return; }
            state.data = res.data || {};
            applyOptions();
            renderCards();
            renderCharts();
            renderTip();
        });
    }

    /** 首次加载（或接口返回）后刷新下拉候选；已选项保留。 */
    function applyOptions() {
        var d = state.data || {};
        state.subs = d.subs || [];
        state.cfgs = d.cfg_options || [];
        renderMulti("sub", state.subs, function (s) { return s.name; });
        renderMulti("cfg", state.cfgs, itemText);
        refreshMultiLabel("sub");
        refreshMultiLabel("cfg");
        var note = byId("arCardNote");
        if (note) {
            var f = d.filter || {};
            note.textContent = "统计区间：" + (f.from_date || "-") + " ~ " + (f.to_date || "-")
                + "　（总人数为平台成员快照数，不受分部筛选影响；合计数为各租户相加，"
                + "同一人多租户绑定时会重复计入）";
        }
    }

    function renderTip() {
        var tip = byId("arTip");
        if (!tip) { return; }
        if (state.data && state.data.login_missing) {
            tip.textContent = "统一认证登录留痕表（uf_oaones_appsyn_op_log）尚未创建，登录相关指标暂为 0，"
                + "请先执行建表脚本。";
            tip.style.display = "";
        } else {
            tip.style.display = "none";
        }
    }

    /* ------------------------------------------------------------------
     * 数字看板
     * ------------------------------------------------------------------ */

    function renderCards() {
        var wrap = byId("arCards");
        if (!wrap) { return; }
        var d = state.data || {};
        var cfgs = d.cfgs || [];
        var html = "";
        if (!cfgs.length) {
            wrap.innerHTML = '<div class="kpi-empty">当前筛选条件下没有租户</div>';
            return;
        }
        var totals = d.totals || {};
        html += cardHtml("合计（按租户汇总）", totals.member_count, totals.bound_count, totals.unbound_count, true);
        cfgs.forEach(function (cfg) {
            html += cardHtml(itemText(cfg) + (num(cfg.cfg_enable) === 1 ? "" : "（停用）"),
                cfg.member_count, cfg.bound_count, cfg.unbound_count, false);
        });
        wrap.innerHTML = html;
    }

    function cardHtml(title, member, bound, unbound, isTotal) {
        return '<div class="kpi-card' + (isTotal ? " kpi-total" : "") + '">'
            + '<div class="kpi-title" title="' + esc(title) + '">' + esc(title) + "</div>"
            + '<div class="kpi-nums">'
            + kpiNum("总人数", member, "#23272f")
            + kpiNum("已绑定", bound, "#3f7a5e")
            + kpiNum("未绑定", unbound, "#9a6e2e")
            + "</div></div>";
    }

    function kpiNum(label, value, color) {
        return '<span class="kpi-num"><span>' + esc(label) + "</span>"
            + '<b style="color:' + color + ';">' + esc(num(value)) + "</b></span>";
    }

    /* ------------------------------------------------------------------
     * 图表
     * ------------------------------------------------------------------ */

    function cfgKeyOf(id) { return String(id); }

    function tenantLabel(cfgId) {
        if (String(cfgId) === "0") { return "未归属租户"; }
        var cfgs = (state.data && state.data.cfgs) || [];
        for (var i = 0; i < cfgs.length; i++) {
            if (String(cfgs[i].id) === String(cfgId)) { return itemText(cfgs[i]); }
        }
        var opts = (state.data && state.data.cfg_options) || [];
        for (var j = 0; j < opts.length; j++) {
            if (String(opts[j].id) === String(cfgId)) { return itemText(opts[j]); }
        }
        return "租户#" + cfgId;
    }

    /** 看板里要展示的租户键：当前筛选出的租户 + 登录数据里出现的“未归属租户”(0)。 */
    function barKeys() {
        var d = state.data || {};
        var keys = [];
        (d.cfgs || []).forEach(function (c) { keys.push(String(c.id)); });
        var hasOrphan = false;
        ((d.today && d.today.login) || []).forEach(function (r) {
            if (String(r.cfg_id) === "0") { hasOrphan = true; }
        });
        if (hasOrphan && keys.indexOf("0") < 0) { keys.push("0"); }
        return keys;
    }

    function mapByCfg(rows) {
        var map = {};
        (rows || []).forEach(function (r) { map[cfgKeyOf(r.cfg_id)] = num(r.cnt); });
        return map;
    }

    function chart(key, domId) {
        var dom = byId(domId);
        if (!dom || !window.echarts) { return null; }
        if (state.charts[key] && state.charts[key].getDom() !== dom) {
            state.charts[key].dispose();
            state.charts[key] = null;
        }
        if (!state.charts[key]) { state.charts[key] = window.echarts.init(dom); }
        return state.charts[key];
    }

    function renderCharts() {
        renderTodayChart();
        renderTrendCharts();
    }

    function renderTodayChart() {
        var d = state.data || {};
        var inst = chart("today", "arTodayChart");
        if (!inst) { return; }
        var keys = barKeys();
        var bindMap = mapByCfg(d.today && d.today.bind);
        var loginMap = mapByCfg(d.today && d.today.login);
        var names = keys.map(tenantLabel);
        var bindData = keys.map(function (k) { return bindMap[k] || 0; });
        var loginData = keys.map(function (k) { return loginMap[k] || 0; });
        var note = byId("arTodayNote");
        if (note) {
            note.textContent = "统计日期：" + ((d.today && d.today.date) || "-")
                + "　新增绑定 " + ((d.today && d.today.bind_total) || 0)
                + " 人，统一认证登录 " + ((d.today && d.today.login_total) || 0) + " 人";
        }
        if (!names.length) {
            inst.clear();
            inst.setOption({ title: { text: "暂无可展示的租户", left: "center", top: "middle",
                textStyle: { color: "#8b929e", fontSize: 13, fontWeight: "normal" } } });
            return;
        }
        inst.clear();
        inst.setOption({
            tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
            legend: { data: ["今日新增绑定", "今日统一认证登录"], top: 0 },
            grid: { left: 55, right: 24, top: 40, bottom: 70 },
            xAxis: {
                type: "category", data: names,
                axisLabel: { interval: 0, rotate: names.length > 4 ? 20 : 0,
                    formatter: function (v) { return v.length > 14 ? (v.substring(0, 14) + "…") : v; } }
            },
            yAxis: { type: "value", minInterval: 1 },
            series: [
                { name: "今日新增绑定", type: "bar", stack: "today", barMaxWidth: 46,
                  itemStyle: { color: "#4a6fa5" }, data: bindData },
                { name: "今日统一认证登录", type: "bar", stack: "today", barMaxWidth: 46,
                  itemStyle: { color: "#3f7a5e" }, data: loginData }
            ]
        });
    }

    function renderTrendCharts() {
        var d = state.data || {};
        var block = (d.trend || {})[state.grain] || {};
        renderOneTrend("bind", "arBindChart", block.bind || [], "新增绑定人数走势", "#4a6fa5");
        renderOneTrend("login", "arLoginChart", block.login || [], "统一认证登录人数走势", "#3f7a5e");
    }

    function renderOneTrend(key, domId, rows, title, color) {
        var inst = chart(key, domId);
        if (!inst) { return; }
        inst.clear();
        if (!rows.length) {
            inst.setOption({ title: { text: title + "：暂无数据", left: "center", top: "middle",
                textStyle: { color: "#8b929e", fontSize: 13, fontWeight: "normal" } } });
            return;
        }
        // 期间升序
        var periods = [];
        var seen = {};
        rows.forEach(function (r) {
            var p = String(r.period || "");
            if (p && !seen[p]) { seen[p] = true; periods.push(p); }
        });
        periods.sort();
        // 系列：每个租户一条线（只保留有数据的租户）
        var seriesKeys = [];
        var byKey = {};
        rows.forEach(function (r) {
            var k = cfgKeyOf(r.cfg_id);
            if (seriesKeys.indexOf(k) < 0) { seriesKeys.push(k); }
            if (!byKey[k]) { byKey[k] = {}; }
            byKey[k][String(r.period)] = num(r.cnt);
        });
        var series = seriesKeys.map(function (k) {
            return {
                name: tenantLabel(k), type: "line", smooth: true, connectNulls: true,
                symbolSize: 6, itemStyle: { color: color },
                data: periods.map(function (p) { return byKey[k][p] == null ? 0 : byKey[k][p]; })
            };
        });
        inst.setOption({
            title: { text: title, left: 0, top: 0,
                textStyle: { fontSize: 13, color: "#23272f", fontWeight: 600 } },
            tooltip: { trigger: "axis" },
            legend: { data: series.map(function (s) { return s.name; }), top: 0, right: 0 },
            grid: { left: 55, right: 24, top: 46, bottom: 60 },
            xAxis: { type: "category", boundaryGap: false, data: periods },
            yAxis: { type: "value", minInterval: 1 },
            series: series
        });
    }

    /* ------------------------------------------------------------------
     * 事件
     * ------------------------------------------------------------------ */

    function togglePop(group, show) {
        var box = byId(group === "sub" ? "arSubMs" : "arCfgMs");
        if (!box) { return; }
        if (show) { box.classList.add("ms-open"); } else { box.classList.remove("ms-open"); }
    }

    function popOpen(group) {
        var box = byId(group === "sub" ? "arSubMs" : "arCfgMs");
        return !!(box && box.classList.contains("ms-open"));
    }

    function bindMulti(group, btnId, allId, noneId) {
        var btn = byId(btnId);
        if (btn) {
            btn.onclick = function (e) {
                e.stopPropagation();
                var open = popOpen(group);
                togglePop("sub", false);
                togglePop("cfg", false);
                togglePop(group, !open);
            };
        }
        var all = byId(allId);
        if (all) {
            all.onclick = function () {
                // “全选”等价于不筛选：清空已选，交给后端按“全部”处理
                clearGroup(group);
                syncCheckboxes(group);
                refreshMultiLabel(group);
            };
        }
        var none = byId(noneId);
        if (none) {
            none.onclick = function () {
                clearGroup(group);
                syncCheckboxes(group);
                refreshMultiLabel(group);
            };
        }
    }

    function clearGroup(group) {
        if (group === "sub") { state.subSel = {}; } else { state.cfgSel = {}; }
    }

    function syncCheckboxes(group) {
        var map = group === "sub" ? state.subSel : state.cfgSel;
        var box = byId(group === "sub" ? "arSubList" : "arCfgList");
        if (!box) { return; }
        var cbs = box.querySelectorAll("input.ms-cb");
        for (var i = 0; i < cbs.length; i++) {
            cbs[i].checked = !!map[String(cbs[i].value)];
        }
    }

    function bind() {
        bindMulti("sub", "arSubBtn", "arSubAll", "arSubNone");
        bindMulti("cfg", "arCfgBtn", "arCfgAll", "arCfgNone");

        // 复选框变化（事件委托，候选是动态渲染的）
        ["arSubList", "arCfgList"].forEach(function (boxId) {
            var box = byId(boxId);
            if (!box) { return; }
            box.onclick = function (e) {
                var t = e.target;
                if (!t || !t.classList || !t.classList.contains("ms-cb")) { return; }
                var group = t.getAttribute("data-group");
                var map = group === "sub" ? state.subSel : state.cfgSel;
                if (t.checked) { map[String(t.value)] = true; } else { delete map[String(t.value)]; }
                refreshMultiLabel(group);
            };
        });

        var searchBtn = byId("arSearchBtn");
        if (searchBtn) { searchBtn.onclick = search; }
        var resetBtn = byId("arResetBtn");
        if (resetBtn) {
            resetBtn.onclick = function () {
                clearGroup("sub");
                clearGroup("cfg");
                syncCheckboxes("sub");
                syncCheckboxes("cfg");
                refreshMultiLabel("sub");
                refreshMultiLabel("cfg");
                ["arFrom", "arTo"].forEach(function (id) { var el = byId(id); if (el) { el.value = ""; } });
                if (window.OaonesDate) { window.OaonesDate.init(); }
                search();
            };
        }
        // 粒度切换
        var tabs = byId("arGrainTabs");
        if (tabs) {
            tabs.onclick = function (e) {
                var t = e.target;
                if (!t || t.tagName !== "BUTTON") { return; }
                state.grain = t.getAttribute("data-grain") === "month" ? "month" : "day";
                var btns = tabs.querySelectorAll("button");
                for (var i = 0; i < btns.length; i++) {
                    btns[i].className = (btns[i] === t) ? "active" : "";
                }
                renderTrendCharts();
            };
        }
        // 点页面别处收起下拉
        document.addEventListener("click", function () { togglePop("sub", false); togglePop("cfg", false); });
        var pop = byId("arSubPop");
        if (pop) { pop.onclick = function (e) { e.stopPropagation(); }; }
        var pop2 = byId("arCfgPop");
        if (pop2) { pop2.onclick = function (e) { e.stopPropagation(); }; }
        window.addEventListener("resize", function () {
            Object.keys(state.charts).forEach(function (k) {
                if (state.charts[k]) { state.charts[k].resize(); }
            });
        });
    }

    function start() {
        bind();
        search();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
