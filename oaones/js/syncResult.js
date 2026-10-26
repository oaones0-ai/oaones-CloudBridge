/**
 * 同步结果独立页面。
 *
 * 数据源：uf_oaones_sync_log_item（一行一个部门 / 分部 / 人员）。
 * 流程建号与同步中心共用这张表；requestid 是 OA 流程请求ID。
 */
(function () {
    "use strict";

    var API_ROWS = 500;
    var current = { rows: [], total: 0, stats: {}, lockedPlatform: false, lockedTenant: false };
    var options = null;
    var pager = null;
    var initialCfgId = String(window.OAONES_SYNC_RESULT_CFG_ID || "");

    function byId(id) { return document.getElementById(id); }
    function value(id) {
        var el = byId(id);
        return el ? String(el.value == null ? "" : el.value).trim() : "";
    }
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function toast(msg, mode) {
        if (window.OaonesToast) {
            window.OaonesToast.show(msg, { mode: mode || "info", duration: mode === "error" ? 7000 : 3500 });
        }
    }
    function num(v) {
        var n = Number(v == null || v === "" ? 0 : v);
        return isNaN(n) ? 0 : n;
    }
    function platformText(type) {
        var t = String(type == null ? "" : type);
        if (t === "0") { return "企业微信"; }
        if (t === "1") { return "钉钉"; }
        if (t === "2") { return "飞书"; }
        return "—";
    }
    function shortPlatform(type) {
        var t = String(type == null ? "" : type);
        if (t === "0") { return "企微"; }
        if (t === "1") { return "钉钉"; }
        if (t === "2") { return "飞书"; }
        return "第三方";
    }
    function directionText(row) {
        var d = String(row.direction == null ? "" : row.direction);
        if (d === "0") { return "OA → " + shortPlatform(row.platform_type); }
        if (d === "1") { return shortPlatform(row.platform_type) + " → OA"; }
        return d || "—";
    }
    function typeText(type) {
        var t = String(type == null ? "" : type);
        if (t === "1") { return "人员"; }
        if (t === "2") { return "分部"; }
        return "部门";
    }
    function opText(op) {
        var v = String(op == null ? "" : op);
        if (v === "0") { return "新增"; }
        if (v === "1") { return "更新"; }
        if (v === "2") { return "跳过"; }
        if (v === "3") { return "失败"; }
        return v || "—";
    }
    function opTag(op) {
        var v = String(op == null ? "" : op);
        var cls = v === "0" ? "tag bound" : (v === "1" ? "tag system"
            : (v === "2" ? "tag unbound" : "tag fail"));
        return "<span class='" + cls + "'>" + esc(opText(v)) + "</span>";
    }
    function fillSelect(id, rows, allText) {
        var el = byId(id);
        if (!el) { return; }
        var html = "<option value=''>" + esc(allText) + "</option>";
        (rows || []).forEach(function (row) {
            html += "<option value='" + esc(row.value) + "'>" + esc(row.text) + "</option>";
        });
        el.innerHTML = html;
    }

    function loadOptions() {
        Oaones.api("syncresult.options", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) || "读取筛选项失败", "error");
                search();
                return;
            }
            options = res.data || {};
            fillSelect("syncResultPlatform", options.platforms, "全部平台");
            applyInitialFilters();
            search();
        });
    }

    function applyInitialFilters() {
        var platform = byId("syncResultPlatform");
        var tenant = byId("syncResultTenant");
        var requestId = byId("syncResultRequestId");
        var logId = byId("syncResultLogId");
        var initialPlatform = String(window.OAONES_SYNC_RESULT_PLATFORM || "");
        var initialCfg = String(window.OAONES_SYNC_RESULT_CFG_ID || "");
        var initialRequestId = String(window.OAONES_SYNC_RESULT_REQUEST_ID || "");
        var initialLogId = String(window.OAONES_SYNC_RESULT_LOG_ID || "");

        if (initialPlatform) { platform.value = initialPlatform; }
        if (initialCfg) {
            current.lockedTenant = true;
            platform.disabled = true;
            current.lockedPlatform = true;
        }
        if (initialRequestId) { requestId.value = initialRequestId; }
        if (initialLogId) { logId.value = initialLogId; }
        rebuildTenantSelect();
        if (initialCfg) {
            tenant.value = initialCfg;
            if (tenant.value !== initialCfg) {
                current.lockedTenant = false;
            }
            tenant.disabled = true;
        }
    }

    function rebuildTenantSelect() {
        var el = byId("syncResultTenant");
        if (!el) { return; }
        var platform = value("syncResultPlatform");
        var previous = el.value;
        var tenants = (options && options.tenants) || [];
        var html = "<option value=''>全部租户</option>";
        var lockedFound = !initialCfgId;
        tenants.forEach(function (tenant) {
            if (platform !== "" && String(tenant.type) !== platform) { return; }
            if (String(tenant.id) === initialCfgId) { lockedFound = true; }
            var label = (tenant.corp_name || ("#" + tenant.id))
                + "（" + platformText(tenant.type) + " #" + tenant.id + "）";
            html += "<option value='" + esc(tenant.id) + "'>" + esc(label) + "</option>";
        });
        if (!lockedFound) {
            html += "<option value='" + esc(initialCfgId) + "'>当前租户 #"
                + esc(initialCfgId) + "（不可维护或已删除）</option>";
        }
        el.innerHTML = html;
        if (previous) {
            el.value = previous;
            if (el.value !== previous) { el.value = ""; }
        }
    }

    function params() {
        return {
            type: value("syncResultPlatform"),
            cfg_id: value("syncResultTenant"),
            requestid: value("syncResultRequestId"),
            log_id: value("syncResultLogId"),
            direction: value("syncResultDirection"),
            item_type: value("syncResultItemType"),
            op: value("syncResultOp"),
            keyword: value("syncResultKeyword"),
            from_time: value("syncResultFrom"),
            to_time: value("syncResultTo"),
            limit: API_ROWS
        };
    }

    function search() {
        var requestId = value("syncResultRequestId");
        var logId = value("syncResultLogId");
        if (requestId && !/^\d+$/.test(requestId)) {
            toast("requestid 必须是 OA 流程请求ID（数字）", "error");
            return;
        }
        if (logId && !/^\d+$/.test(logId)) {
            toast("批次ID必须是数字", "error");
            return;
        }
        var body = byId("syncResultBody");
        body.innerHTML = "<tr><td colspan='12' style='text-align:center;color:#8b929e;'>加载中…</td></tr>";
        byId("syncResultEmpty").style.display = "none";
        Oaones.api("syncresult.list", params(), function (res) {
            if (!res || res.code !== 0) {
                var msg = (res && res.msg) || "查询同步结果失败";
                body.innerHTML = "<tr><td colspan='12' style='text-align:center;color:#a8534f;'>"
                    + esc(msg) + "</td></tr>";
                toast(msg, "error");
                return;
            }
            var data = res.data || {};
            current.rows = data.rows || [];
            current.total = num(data.total);
            current.stats = data.stats || {};
            renderSummary();
            renderRows();
            renderTip();
        });
    }

    function renderSummary() {
        var s = current.stats || {};
        byId("syncResultSummary").innerHTML =
            "<span class='bind-stat'><span>命中明细</span><b>" + esc(current.total) + "</b> 条</span>"
            + "<span class='bind-stat'><span>部门/分部</span><b>" + esc(num(s.deptCount)) + "</b> 条</span>"
            + "<span class='bind-stat'><span>人员</span><b>" + esc(num(s.userCount)) + "</b> 条</span>"
            + "<span class='bind-stat'><span>新增</span><b>" + esc(num(s.createCount)) + "</b> 条</span>"
            + "<span class='bind-stat'><span>更新</span><b>" + esc(num(s.updateCount)) + "</b> 条</span>"
            + "<span class='bind-stat'><span>跳过</span><b>" + esc(num(s.skipCount)) + "</b> 条</span>"
            + "<span class='bind-stat'><span>失败</span><b class='fail-num'>" + esc(num(s.failCount)) + "</b> 条</span>";
    }

    function renderTip() {
        var tip = byId("syncResultTip");
        if (window.OAONES_SYNC_RESULT_HIDE_SUMMARY) {
            tip.style.display = "none";
            return;
        }
        if (current.total > current.rows.length) {
            tip.textContent = "当前条件共命中 " + current.total + " 条，列表展示最近 "
                + current.rows.length + " 条。请用 requestid、租户或时间缩小范围。";
            tip.style.display = "";
        } else {
            tip.style.display = "none";
        }
    }

    function renderRows() {
        if (!pager) {
            pager = OaonesTable.pager({
                key: "syncResult.list",
                el: byId("syncResultPager"),
                onChange: renderPage
            });
        }
        pager.resetIfChanged(JSON.stringify(params()));
        renderPage();
    }

    function renderPage() {
        var pageRows = pager.apply(current.rows);
        var body = byId("syncResultBody");
        if (!pageRows.length) {
            body.innerHTML = "";
            byId("syncResultEmpty").style.display = "block";
            pager.render();
            return;
        }
        byId("syncResultEmpty").style.display = "none";
        var html = "";
        pageRows.forEach(function (row) {
            var tenant = row.corp_name || (String(row.cfg_id) === "0" ? "未识别租户" : ("#" + row.cfg_id));
            var request = requestIdCell(row.requestid);
            html += "<tr>"
                + "<td>" + esc((row.create_date || "") + (row.create_time ? " " + row.create_time : "")) + "</td>"
                + "<td>" + esc(platformText(row.platform_type)) + "</td>"
                + "<td title='" + esc(tenant + "（ID " + row.cfg_id + "）") + "'>" + esc(tenant)
                + " <span class='muted'>#" + esc(row.cfg_id) + "</span></td>"
                + "<td>" + request + "</td>"
                + "<td>" + esc(directionText(row)) + "</td>"
                + "<td>" + esc(typeText(row.item_type)) + "</td>"
                + "<td>" + opTag(row.op) + "</td>"
                + "<td>" + oaCell(row) + "</td>"
                + "<td>" + wxCell(row) + "</td>"
                + "<td>" + parentCell(row) + "</td>"
                + "<td>" + esc(row.message || "—") + "</td>"
                + "<td>" + esc(row.log_id || "—") + "</td>"
                + "</tr>";
        });
        body.innerHTML = html;
        pager.render();
        OaonesTable.resizable(byId("syncResultTable"), { key: "syncCenter.resultTable" });
    }

    function requestIdCell(id) {
        var text = String(id == null ? "" : id);
        if (!text || text === "0") { return "—"; }
        return "<a class='hrm-link' target='_blank' rel='noopener' title='打开 OA 流程' href='"
            + esc("/workflow/request/ViewRequestForwardSPA.jsp?requestid=" + encodeURIComponent(text))
            + "'>" + esc(text) + "</a>";
    }

    function oaCell(row) {
        var id = String(row.oa_id == null ? "" : row.oa_id);
        var name = String(row.oa_name == null ? "" : row.oa_name);
        if (!id) { return esc(name || "—"); }
        if (String(row.item_type) === "1") {
            return OaonesHrm.user(id, name || ("OA 人员 " + id));
        }
        if (String(row.item_type) === "2") {
            return OaonesHrm.sub(id, name || ("OA 分部 " + id));
        }
        return OaonesHrm.dept(id, name || ("OA 部门 " + id));
    }

    function wxCell(row) {
        var name = String(row.wx_name == null ? "" : row.wx_name);
        var id = String(row.wx_id == null ? "" : row.wx_id);
        if (!name && !id) { return "—"; }
        return esc(name || id) + (name && id ? " <span class='muted'>（ID " + esc(id) + "）</span>" : "");
    }

    function parentCell(row) {
        var name = String(row.parent_name == null ? "" : row.parent_name);
        var id = String(row.parent_id == null ? "" : row.parent_id);
        if (!name && !id) { return "—"; }
        return esc(name || id) + (name && id ? " <span class='muted'>（ID " + esc(id) + "）</span>" : "");
    }

    function reset() {
        ["syncResultPlatform", "syncResultTenant", "syncResultRequestId", "syncResultLogId",
            "syncResultDirection", "syncResultItemType", "syncResultOp", "syncResultKeyword",
            "syncResultFrom", "syncResultTo"].forEach(function (id) {
            var el = byId(id);
            if (el) { el.value = ""; }
        });
        var platform = byId("syncResultPlatform");
        if (!current.lockedPlatform) { platform.disabled = false; }
        var tenant = byId("syncResultTenant");
        if (current.lockedTenant) {
            tenant.disabled = true;
            tenant.value = initialCfgId;
        } else {
            tenant.disabled = false;
        }
        rebuildTenantSelect();
        search();
    }

    function bind() {
        byId("syncResultSearchBtn").onclick = search;
        byId("syncResultResetBtn").onclick = reset;
        byId("syncResultPlatform").onchange = function () {
            rebuildTenantSelect();
            search();
        };
        ["syncResultTenant", "syncResultDirection", "syncResultItemType", "syncResultOp"].forEach(function (id) {
            byId(id).onchange = search;
        });
        byId("syncResultKeyword").onkeydown = function (e) {
            if (e.key === "Enter" || e.keyCode === 13) { search(); }
        };
        byId("syncResultRequestId").onkeydown = function (e) {
            if (e.key === "Enter" || e.keyCode === 13) { search(); }
        };
        byId("syncResultLogId").onkeydown = function (e) {
            if (e.key === "Enter" || e.keyCode === 13) { search(); }
        };
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () { bind(); loadOptions(); });
    } else {
        bind();
        loadOptions();
    }
})();
