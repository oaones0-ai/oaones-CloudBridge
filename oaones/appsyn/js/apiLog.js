/**
 * 接口日志中心（一级菜单 /oaones/appsyn/apiLog/apiLog.jsp）交互脚本。
 *
 * 页面是**跨平台只读汇总**：平台 / 租户 / 接口类型三个下拉由 apilog.options 提供，
 * 列表走 apilog.list（一次最多取 500 条，前端用 OaonesTable.pager 分页），
 * 行内“查看”走 apilog.detail 打开报文详情。
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

    var PAGE_SIZE = 500;
    var options = null;
    var pager = null;
    var currentRows = [];
    var currentDetail = null;
    /** 全屏前的弹框位置（拖动过的弹框是行内定位，退出全屏时还原）。 */
    var fullSavedBox = null;
    var COPY_BLOCKS = {
        apiLogDetailUrl: { title: "【接口地址】", name: "接口地址" },
        apiLogDetailRequest: { title: "【请求报文】", name: "请求报文" },
        apiLogDetailResponse: { title: "【返回报文】", name: "返回报文" },
        apiLogDetailStack: { title: "【异常堆栈】", name: "异常堆栈" }
    };

    function loadOptions() {
        Oaones.api("apilog.options", {}, function (res) {
            if (!res || res.code !== 0) { return; }
            options = res.data || {};
            fillSelect("apiLogPlatform", options.platforms, "全部平台");
            fillSelect("apiLogType", options.types, "全部类型");
            // 平台下拉带“个人微信”，但个人微信没有租户：切到它时租户下拉只留“全部租户”
            rebuildTenantSelect();
            var tip = byId("apiLogTip");
            if (tip) {
                var msgs = [];
                if (options.enabled === false) { msgs.push("接口日志已关闭（oaones.appsyn.properties#api.log.enable=0）"); }
                if (options.keepDays === 0) { msgs.push("日志为永久保留（可在系统设置 → 日志保留中修改）"); }
                if (msgs.length > 0) { tip.textContent = msgs.join("；"); tip.style.display = ""; }
            }
            var cleanBtn = byId("apiLogCleanBtn");
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

    function rebuildTenantSelect() {
        var el = byId("apiLogTenant");
        if (!el) { return; }
        var platform = value("apiLogPlatform");
        var current = el.value;
        var html = '<option value="">全部租户</option>';
        var tenants = (options && options.tenants) || [];
        tenants.forEach(function (t) {
            if (platform !== "" && String(t.type) !== String(platform)) { return; }
            var label = t.corp_name ? (t.corp_name + "（" + t.platform + " #" + t.id + "）") : ("#" + t.id);
            html += '<option value="' + esc(t.id) + '">' + esc(label) + "</option>";
        });
        el.innerHTML = html;
        if (current) {
            el.value = current;
            if (el.value !== current) { el.value = ""; }
        }
        // 个人微信没有租户：禁用租户下拉
        el.disabled = String(platform) === "3";
    }

    function params() {
        return {
            type: value("apiLogPlatform"),
            cfg_id: value("apiLogTenant"),
            api_type: value("apiLogType"),
            direction: value("apiLogDirection"),
            success: value("apiLogSuccess"),
            keyword: value("apiLogKeyword"),
            from_time: value("apiLogFrom"),
            to_time: value("apiLogTo"),
            page: 1,
            size: PAGE_SIZE
        };
    }

    function search() {
        var body = byId("apiLogBody");
        if (body) { body.innerHTML = '<tr><td colspan="11" style="text-align:center;color:#8b929e;">加载中…</td></tr>'; }
        Oaones.api("apilog.list", params(), function (res) {
            if (!res || res.code !== 0) {
                if (body) { body.innerHTML = '<tr><td colspan="11" style="text-align:center;color:#a8534f;">加载失败</td></tr>'; }
                return;
            }
            var data = res.data || {};
            if (data.missing) {
                toast("接口日志表（uf_oaones_appsyn_api_log）尚未创建，请先执行建表脚本", "error");
            }
            renderSummary(data.stats, data.total);
            renderList(data.rows || []);
        });
    }

    function renderSummary(stats, total) {
        var el = byId("apiLogSummary");
        if (!el) { return; }
        var sum = (stats && stats.sum) || {};
        var totalCount = sum.total == null ? (total == null ? 0 : total) : sum.total;
        var success = sum.success == null ? 0 : sum.success;
        var fail = sum.fail == null ? 0 : sum.fail;
        var avg = sum.avgCost == null ? 0 : Math.round(Number(sum.avgCost) || 0);
        el.innerHTML = "本页条件命中 <b>" + esc(totalCount) + "</b> 条"
            + "　成功 <b style='color:#3f7a5e;'>" + esc(success) + "</b>"
            + "　失败 <b style='color:#a8534f;'>" + esc(fail) + "</b>"
            + "　平均耗时 <b>" + esc(avg) + "</b> ms"
            + "　（列表最多展示最近 " + PAGE_SIZE + " 条，可叠加筛选缩小范围）";
    }

    function renderList(rows) {
        currentRows = rows || [];
        if (!pager) {
            pager = OaonesTable.pager({
                key: "apiLog",
                el: "apiLogPager",
                onChange: function () { renderList(currentRows); }
            });
        }
        pager.resetIfChanged(JSON.stringify(params()));
        var pageRows = pager.apply(currentRows);
        var body = byId("apiLogBody");
        if (!body) { return; }
        if (!pageRows.length) {
            body.innerHTML = '<tr><td colspan="11" style="text-align:center;color:#8b929e;">暂无接口日志'
                + '（确认已执行 uf_oaones_appsyn_api_log 建表脚本，并已产生接口调用）</td></tr>';
            pager.render();
            return;
        }
        var html = "";
        pageRows.forEach(function (row) {
            var ok = String(row.success) === "1";
            var statusText = ok ? "成功" : "失败";
            var statusHtml = ok ? "<span style='color:#3f7a5e;'>成功</span>"
                : "<span style='color:#a8534f;'>失败</span>";
            var tenant = row.type === 3 ? "—" : (row.corp_name ? (esc(row.corp_name) + " #" + esc(row.cfg_id)) : ("#" + esc(row.cfg_id)));
            html += "<tr>"
                + "<td>" + esc(row.create_at) + "</td>"
                + "<td>" + esc(row.platform) + "</td>"
                + "<td>" + tenant + "</td>"
                + "<td>" + esc(row.api_type_text) + "</td>"
                + "<td>" + esc(row.direction_text) + "</td>"
                + "<td>" + esc(row.method) + "</td>"
                + "<td title='" + esc(row.api_path) + "'>" + esc(shortText(row.api_path, 48)) + "</td>"
                + "<td>" + esc(row.http_status == null ? "" : row.http_status) + "</td>"
                + "<td>" + statusHtml + "</td>"
                + "<td>" + esc(row.cost_ms == null ? "" : row.cost_ms) + "</td>"
                + "<td><button type='button' class='btn small secondary api-log-view' data-id='"
                + esc(row.id) + "'>查看</button></td>"
                + "</tr>";
        });
        body.innerHTML = html;
        pager.render();
        bindRowButtons();
    }

    function shortText(v, max) {
        var s = String(v == null ? "" : v);
        return s.length > max ? (s.substring(0, max) + "…") : s;
    }

    function bindRowButtons() {
        var buttons = document.querySelectorAll(".api-log-view");
        Array.prototype.forEach.call(buttons, function (btn) {
            btn.onclick = function () { openDetail(btn.getAttribute("data-id")); };
        });
    }

    function openDetail(id) {
        if (!id) { return; }
        Oaones.api("apilog.detail", { id: id }, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "查看详情失败", "error");
                return;
            }
            currentDetail = res.data || {};
            renderDetail(currentDetail);
            showModal(true);
        });
    }

    function renderDetail(row) {
        byId("apiLogDetailSummary").innerHTML =
            "平台 <b>" + esc(row.platform) + "</b>"
            + "　租户 <b>" + esc(row.type === 3 ? "—" : (row.corp_name || ("#" + row.cfg_id))) + "</b>"
            + "　接口类型 <b>" + esc(row.api_type_text) + "</b>"
            + "　方向 <b>" + esc(row.direction_text) + "</b>"
            + "　结果 <b style='color:" + (String(row.success) === "1" ? "#3f7a5e" : "#a8534f") + ";'>"
            + (String(row.success) === "1" ? "成功" : "失败") + "</b>"
            + "　耗时 <b>" + esc(row.cost_ms == null ? "" : row.cost_ms) + "</b> ms"
            + "　时间 <b>" + esc(row.create_at) + "</b>"
            + (row.err_code ? ("　错误码 <b>" + esc(row.err_code) + "</b>") : "")
            + (row.err_msg ? ("<br>错误信息：" + esc(row.err_msg)) : "");
        byId("apiLogDetailUrl").textContent = row.method + " " + (row.api_url || row.api_path || "");
        setBlock("apiLogDetailRequest", row.req_body);
        setBlock("apiLogDetailResponse", row.resp_body);
        setBlock("apiLogDetailStack", row.error_stack);
        var stackRow = byId("apiLogDetailStackRow");
        if (stackRow) { stackRow.style.display = row.error_stack ? "" : "none"; }
    }

    function setBlock(id, text) {
        var el = byId(id);
        if (!el) { return; }
        var s = String(text == null ? "" : text);
        if (!s) { el.textContent = "（无）"; return; }
        // 报文尽量美化：JSON 直接缩进显示，非 JSON 原样
        var pretty = s;
        try { pretty = JSON.stringify(JSON.parse(s), null, 2); } catch (e) { pretty = s; }
        el.textContent = pretty;
    }

    function showModal(show) {
        var mask = byId("apiLogDetailMask");
        var modal = byId("apiLogDetailModal");
        if (mask) { mask.style.display = show ? "block" : "none"; }
        if (modal) { modal.style.display = show ? "block" : "none"; }
    }

    /** 详情弹框全屏 / 退出全屏：报文很长时整页铺满，方便逐段核对。 */
    function toggleFullscreen() {
        var modal = byId("apiLogDetailModal");
        var btn = byId("apiLogDetailFullBtn");
        if (!modal || !btn) { return; }
        var full = !modal.classList.contains("modal-fullscreen");
        if (full) {
            fullSavedBox = {
                left: modal.style.left, top: modal.style.top,
                transform: modal.style.transform, margin: modal.style.margin
            };
            modal.classList.add("modal-fullscreen");
            modal.style.left = "";
            modal.style.top = "";
            modal.style.transform = "";
            modal.style.margin = "";
            modal.style.display = "flex";
            btn.textContent = "退出全屏";
            btn.setAttribute("title", "退出全屏（Esc）");
        } else {
            modal.classList.remove("modal-fullscreen");
            modal.style.display = "block";
            if (fullSavedBox) {
                modal.style.left = fullSavedBox.left;
                modal.style.top = fullSavedBox.top;
                modal.style.transform = fullSavedBox.transform;
                modal.style.margin = fullSavedBox.margin;
            }
            fullSavedBox = null;
            btn.textContent = "全屏";
            btn.setAttribute("title", "整页全屏查看报文（再点一次或按 Esc 退出）");
        }
    }

    function closeDetail() {
        // 关闭时一并退出全屏，下次打开还是常规弹框
        var modal = byId("apiLogDetailModal");
        if (modal && modal.classList.contains("modal-fullscreen")) { toggleFullscreen(); }
        showModal(false);
    }

    function copyBlock(id) {
        var meta = COPY_BLOCKS[id];
        var el = byId(id);
        if (!el) { return; }
        var text = (meta ? (meta.title + "\n") : "") + (el.textContent || "");
        copyText(text);
    }

    function copyAll() {
        if (!currentDetail) { return; }
        var parts = [];
        parts.push("【接口地址】\n" + (currentDetail.method + " " + (currentDetail.api_url || currentDetail.api_path || "")));
        parts.push("【请求报文】\n" + (currentDetail.req_body || "（无）"));
        parts.push("【返回报文】\n" + (currentDetail.resp_body || "（无）"));
        if (currentDetail.error_stack) { parts.push("【异常堆栈】\n" + currentDetail.error_stack); }
        copyText(parts.join("\n\n"));
    }

    function copyText(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () { toast("已复制", "success"); },
                function () { fallbackCopy(text); });
        } else {
            fallbackCopy(text);
        }
    }

    function fallbackCopy(text) {
        var ta = document.createElement("textarea");
        ta.value = text;
        ta.style.position = "fixed";
        ta.style.left = "-9999px";
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand("copy"); toast("已复制", "success"); }
        catch (e) { toast("复制失败，请手动选择", "error"); }
        document.body.removeChild(ta);
    }

    function clean() {
        if (!window.confirm("确定按保留天数清理历史接口日志吗？此操作不可恢复。")) { return; }
        Oaones.api("apilog.clean", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "清理失败", "error");
                return;
            }
            var d = res.data || {};
            toast("已清理 " + (d.deleted == null ? 0 : d.deleted) + " 条历史日志", "success");
            search();
        });
    }

    function bind() {
        var searchBtn = byId("apiLogSearchBtn");
        if (searchBtn) { searchBtn.onclick = search; }
        var resetBtn = byId("apiLogResetBtn");
        if (resetBtn) {
            resetBtn.onclick = function () {
                ["apiLogPlatform", "apiLogTenant", "apiLogType", "apiLogDirection",
                    "apiLogSuccess", "apiLogKeyword", "apiLogFrom", "apiLogTo"].forEach(function (id) {
                    var el = byId(id);
                    if (el) { el.value = ""; }
                });
                rebuildTenantSelect();
                search();
            };
        }
        var platform = byId("apiLogPlatform");
        if (platform) { platform.onchange = function () { rebuildTenantSelect(); }; }
        var type = byId("apiLogType");
        if (type) { type.onchange = search; }
        var direction = byId("apiLogDirection");
        if (direction) { direction.onchange = search; }
        var success = byId("apiLogSuccess");
        if (success) { success.onchange = search; }
        var tenant = byId("apiLogTenant");
        if (tenant) { tenant.onchange = search; }
        var kw = byId("apiLogKeyword");
        if (kw) { kw.onkeydown = function (e) { if (e.key === "Enter") { search(); } }; }

        var closeBtn = byId("apiLogDetailClose");
        var okBtn = byId("apiLogDetailOkBtn");
        var mask = byId("apiLogDetailMask");
        var fullBtn = byId("apiLogDetailFullBtn");
        if (closeBtn) { closeBtn.onclick = closeDetail; }
        if (okBtn) { okBtn.onclick = closeDetail; }
        if (mask) { mask.onclick = closeDetail; }
        if (fullBtn) { fullBtn.onclick = toggleFullscreen; }
        // Esc：先退全屏，再关弹框
        document.addEventListener("keydown", function (e) {
            var key = e.key || e.keyCode;
            if (key !== "Escape" && key !== "Esc" && key !== 27) { return; }
            var modal = byId("apiLogDetailModal");
            if (!modal || modal.style.display === "none" || modal.style.display === "") { return; }
            if (modal.classList.contains("modal-fullscreen")) { toggleFullscreen(); return; }
            closeDetail();
        });
        var copyAllBtn = byId("apiLogDetailCopyAllBtn");
        if (copyAllBtn) { copyAllBtn.onclick = copyAll; }
        Array.prototype.forEach.call(document.querySelectorAll("[data-copy]"), function (btn) {
            btn.onclick = function () { copyBlock(btn.getAttribute("data-copy")); };
        });
        var cleanBtn = byId("apiLogCleanBtn");
        if (cleanBtn) { cleanBtn.onclick = clean; }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () { bind(); loadOptions(); search(); });
    } else {
        bind(); loadOptions(); search();
    }
})();
