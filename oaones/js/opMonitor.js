/**
 * 操作监控平台（一级菜单 /oaones/opMonitor/opMonitor.jsp）交互脚本。
 *
 * 页面是**全站只读汇总**：事件类型 / 菜单两个下拉由 oplog.options 提供，
 * 列表走 oplog.list（一次最多取 500 条，前端用 OaonesTable.pager 分页），
 * 行内“人员”按 OA 人员卡片输出超链接（OaonesHrm.user）。
 *
 * 采集口径见 common/OaonesOpLog：登录 / 退出 / 菜单访问三类事件，
 * 菜单访问只记“请求地址命中 E9 菜单表 LeftMenuInfo”的页面请求。
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
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode }); }
        else if (window.console) { console.log(msg); }
    }
    function hrmUser(id, name) {
        var text = String(name == null ? "" : name);
        var uid = String(id == null ? "" : id);
        if (/^[1-9][0-9]*$/.test(uid) && window.OaonesHrm) { return window.OaonesHrm.user(uid, text); }
        return esc(text);
    }

    var PAGE_SIZE = 500;
    var options = null;
    var pager = null;
    var currentRows = [];

    function isAdmin() { return window.OAONES_OP_ADMIN === true; }

    function loadOptions() {
        Oaones.api("oplog.options", {}, function (res) {
            if (!res || res.code !== 0) { return; }
            options = res.data || {};
            fillSelect("opMonitorType", options.types, "全部事件");
            fillSelect("opMonitorMenu", options.menus, "全部菜单");
            var tip = byId("opMonitorTip");
            if (tip) {
                var msgs = [];
                if (options.enabled === false) { msgs.push("操作监控采集已关闭（oaones.properties#op.log.enable=0），新操作不再记录，历史记录仍可查看"); }
                if (options.keepDays === 0) { msgs.push("日志为永久保留（可在系统设置 → 日志保留中修改）"); }
                if (msgs.length > 0) { tip.textContent = msgs.join("；"); tip.style.display = ""; }
            }
            var cleanBtn = byId("opMonitorCleanBtn");
            if (cleanBtn && options.admin) { cleanBtn.style.display = ""; }
        });
    }

    function fillSelect(id, list, allText) {
        var el = byId(id);
        if (!el) { return; }
        var html = '<option value="">' + esc(allText) + "</option>";
        (list || []).forEach(function (item) {
            html += '<option value="' + esc(item.value) + '">' + esc(item.text) + "</option>";
        });
        el.innerHTML = html;
    }

    function params() {
        return {
            user_keyword: value("opMonitorUser"),
            op_type: value("opMonitorType"),
            menu_id: value("opMonitorMenu"),
            keyword: value("opMonitorKeyword"),
            from_time: value("opMonitorFrom"),
            to_time: value("opMonitorTo"),
            page: 1,
            size: PAGE_SIZE
        };
    }

    function search() {
        if (!isAdmin()) { return; }
        var body = byId("opMonitorBody");
        if (body) { body.innerHTML = '<tr><td colspan="8" style="text-align:center;color:#8b929e;">加载中…</td></tr>'; }
        Oaones.api("oplog.list", params(), function (res) {
            if (!res || res.code !== 0) {
                if (body) { body.innerHTML = '<tr><td colspan="8" style="text-align:center;color:#a8534f;">加载失败</td></tr>'; }
                return;
            }
            var data = res.data || {};
            if (data.missing) {
                toast("操作监控表（uf_oaones_op_log）尚未创建，请先执行建表脚本", "error");
            }
            renderSummary(data.stats, data.total);
            renderList(data.rows || []);
        });
    }

    function renderSummary(stats, total) {
        var el = byId("opMonitorSummary");
        if (!el) { return; }
        var sum = stats || {};
        var totalCount = sum.total == null ? (total == null ? 0 : total) : sum.total;
        el.innerHTML = "本页条件命中 <b>" + esc(totalCount) + "</b> 条"
            + "　登录 <b>" + esc(sum.login == null ? 0 : sum.login) + "</b>"
            + "　退出 <b>" + esc(sum.logout == null ? 0 : sum.logout) + "</b>"
            + "　菜单访问 <b style='color:#4a6fa5;'>" + esc(sum.menu == null ? 0 : sum.menu) + "</b>"
            + "　（列表最多展示最近 " + PAGE_SIZE + " 条，可叠加筛选缩小范围）";
    }

    function renderList(rows) {
        currentRows = rows || [];
        if (!pager) {
            pager = OaonesTable.pager({
                key: "opMonitor",
                el: "opMonitorPager",
                onChange: function () { renderList(currentRows); }
            });
        }
        pager.resetIfChanged(JSON.stringify(params()));
        var pageRows = pager.apply(currentRows);
        var body = byId("opMonitorBody");
        if (!body) { return; }
        if (!pageRows.length) {
            body.innerHTML = '<tr><td colspan="8" style="text-align:center;color:#8b929e;">暂无操作记录'
                + '（确认已执行 uf_oaones_op_log 建表脚本；采集开关 op.log.enable=1；'
                + '并已有登录或菜单访问发生）</td></tr>';
            pager.render();
            return;
        }
        var html = "";
        pageRows.forEach(function (row) {
            var typeText = row.op_type_text || "";
            var typeHtml = esc(typeText);
            if (row.op_type === "menu") {
                typeHtml = "<span style='color:#4a6fa5;'>" + esc(typeText) + "</span>";
            } else if (row.op_type === "login") {
                typeHtml = "<span style='color:#3f7a5e;'>" + esc(typeText) + "</span>";
            } else if (row.op_type === "logout") {
                typeHtml = "<span style='color:#8b929e;'>" + esc(typeText) + "</span>";
            }
            var person = hrmUser(row.user_id, row.user_name || row.login_name);
            if (row.login_name) {
                person += "<span style='color:#8b929e;'>（" + esc(row.login_name) + "）</span>";
            }
            var menu = row.menu_name
                ? ("<span title='" + esc(row.menu_path) + "'>" + esc(row.menu_name) + "</span>")
                : "—";
            html += "<tr>"
                + "<td>" + esc(row.create_at) + "</td>"
                + "<td>" + person + "</td>"
                + "<td>" + esc(row.dept_name || "—") + "</td>"
                + "<td>" + typeHtml + "</td>"
                + "<td>" + menu + "</td>"
                + "<td title='" + esc(row.page_url) + "'>" + esc(shortText(row.page_url, 60)) + "</td>"
                + "<td>" + esc(row.client_ip || "—") + "</td>"
                + "<td>" + esc(row.device || "—") + "</td>"
                + "</tr>";
        });
        body.innerHTML = html;
        pager.render();
    }

    function shortText(v, max) {
        var s = String(v == null ? "" : v);
        return s.length > max ? (s.substring(0, max) + "…") : s;
    }

    function clean() {
        if (!window.confirm("确定按保留天数清理历史操作日志吗？此操作不可恢复。")) { return; }
        Oaones.api("oplog.clean", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "清理失败", "error");
                return;
            }
            var d = res.data || {};
            toast((d.message ? d.message : ("已清理 " + (d.deleted == null ? 0 : d.deleted) + " 条历史日志")), "success");
            search();
        });
    }

    function reset() {
        ["opMonitorUser", "opMonitorType", "opMonitorMenu", "opMonitorKeyword",
            "opMonitorFrom", "opMonitorTo"].forEach(function (id) {
            var el = byId(id);
            if (el) { el.value = ""; }
        });
        search();
    }

    function bind() {
        var searchBtn = byId("opMonitorSearchBtn");
        if (searchBtn) { searchBtn.onclick = search; }
        var resetBtn = byId("opMonitorResetBtn");
        if (resetBtn) { resetBtn.onclick = reset; }
        var type = byId("opMonitorType");
        if (type) { type.onchange = search; }
        var menu = byId("opMonitorMenu");
        if (menu) { menu.onchange = search; }
        ["opMonitorUser", "opMonitorKeyword"].forEach(function (id) {
            var el = byId(id);
            if (el) { el.onkeydown = function (e) { if (e.key === "Enter") { search(); } }; }
        });
        var cleanBtn = byId("opMonitorCleanBtn");
        if (cleanBtn) { cleanBtn.onclick = clean; }
    }

    function start() {
        bind();
        if (!isAdmin()) {
            var card = byId("opMonitorCard");
            if (card) { card.style.display = "none"; }
            var tip = byId("opMonitorTip");
            if (tip) {
                tip.textContent = "本页是全站的操作留痕，只对系统管理员或被授权人员开放；如需查看请联系系统管理员把你要用的人员ID加到 oaones.properties#opmonitor.admin.ids。";
                tip.style.display = "";
            }
            return;
        }
        loadOptions();
        search();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
