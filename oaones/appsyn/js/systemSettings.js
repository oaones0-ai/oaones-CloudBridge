/**
 * 系统设置 → 日志保留（/oaones/appsyn/systemSettings/systemSettings.jsp）。
 *
 * 支持“应用到全部后保存”和逐行单独保存。所有 <= 0 的输入由后端归一为 0（永久保留）。
 */
(function () {
    "use strict";

    var state = { items: [] };

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
    function validDays(raw) {
        return /^[+-]?\d+$/.test(String(raw == null ? "" : raw).trim());
    }
    function normalizeInput(el) {
        if (!el) { return 0; }
        var n = Number(el.value);
        if (isNaN(n)) { return 0; }
        return n < 0 ? Math.ceil(n) : Math.floor(n);
    }
    function valueOf(el) {
        return String(el && el.value != null ? el.value : "").trim();
    }
    function statusText(days) {
        return days <= 0 ? "永久保留" : ("超过 " + days + " 天自动清理");
    }
    function countText(value) {
        if (value == null || value === "") { return "—"; }
        var count = Number(value);
        if (!isFinite(count) || count < 0) { return "—"; }
        try {
            return count.toLocaleString("zh-CN");
        } catch (e) {
            return String(count);
        }
    }

    function load() {
        Oaones.api("logretention.list", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "加载日志保留设置失败", "error");
                return;
            }
            var data = res.data || {};
            state.items = data.items || [];
            renderRows();
            renderStatus(data);
        });
    }

    function renderRows() {
        var body = byId("logRetentionBody");
        if (!body) { return; }
        if (!state.items.length) {
            body.innerHTML = '<tr><td colspan="7" class="retention-empty">没有可配置的日志表</td></tr>';
            return;
        }
        var html = "";
        state.items.forEach(function (item) {
            var days = Number(item.keepDays == null ? 0 : item.keepDays);
            html += "<tr data-retention-row='" + esc(item.code) + "'>"
                + "<td><b>" + esc(item.name) + "</b></td>"
                + "<td class='retention-desc'>" + esc(item.description) + "</td>"
                + "<td><code>" + esc(item.table) + "</code></td>"
                + "<td class='retention-row-count'><span data-retention-count='" + esc(item.code) + "'>"
                + esc(countText(item.rowCount)) + "</span></td>"
                + "<td><input class='retention-days' type='number' step='1'"
                + " data-retention-code='" + esc(item.code) + "'"
                + " value='" + esc(days) + "' aria-label='" + esc(item.name) + "保留天数'></td>"
                + "<td><span data-retention-state='" + esc(item.code) + "'>"
                + esc(statusText(days)) + "</span></td>"
                + "<td><button type='button' class='btn small secondary'"
                + " data-retention-save='" + esc(item.code) + "'>保存</button></td>"
                + "</tr>";
        });
        body.innerHTML = html;
        bind();
    }

    function renderStatus(data) {
        var scheduler = data.scheduler || {};
        var tip = byId("logRetentionSchedulerTip");
        if (tip) {
            var text = scheduler.started
                ? "自动清理：每天检查一次"
                : "自动清理：等待 E9 启动或首次访问本页后启动";
            if (scheduler.lastRun) {
                text += "；最近检查 " + scheduler.lastRun;
                if (scheduler.lastMessage) { text += "；" + scheduler.lastMessage; }
            }
            tip.textContent = text;
        }
        var pageTip = byId("logRetentionPageTip");
        if (pageTip) {
            if (data.missing) {
                pageTip.textContent = "日志保留配置表尚未创建，当前全部按永久保留处理。"
                    + "请先执行 oaones0019 迁移脚本并重启 E9。";
                pageTip.style.display = "";
            } else {
                pageTip.style.display = "none";
            }
        }
    }

    function refreshState(code) {
        var input = document.querySelector("input[data-retention-code='" + code + "']");
        var label = document.querySelector("[data-retention-state='" + code + "']");
        if (!input || !label) { return; }
        label.textContent = statusText(normalizeInput(input));
    }

    function bind() {
        var inputs = document.querySelectorAll("input[data-retention-code]");
        Array.prototype.forEach.call(inputs, function (input) {
            input.oninput = function () { refreshState(input.getAttribute("data-retention-code")); };
        });
        var buttons = document.querySelectorAll("button[data-retention-save]");
        Array.prototype.forEach.call(buttons, function (button) {
            button.onclick = function () {
                var code = button.getAttribute("data-retention-save");
                var input = document.querySelector("input[data-retention-code='" + code + "']");
                if (!input || !validDays(input.value)) {
                    toast("请输入整数天数", "error");
                    return;
                }
                button.disabled = true;
                Oaones.api("logretention.save", { code: code, days: valueOf(input) }, function (res) {
                    button.disabled = false;
                    if (!res || res.code !== 0) {
                        toast((res && res.msg) ? res.msg : "保存失败", "error");
                        return;
                    }
                    toast((res.data && res.data.message) || "已保存", "success");
                    load();
                });
            };
        });
    }

    function applyAll() {
        var global = byId("logRetentionGlobalDays");
        if (!global || !validDays(global.value)) {
            toast("统一设置请输入整数天数", "error");
            return;
        }
        var inputs = document.querySelectorAll("input[data-retention-code]");
        Array.prototype.forEach.call(inputs, function (input) {
            input.value = valueOf(global);
            refreshState(input.getAttribute("data-retention-code"));
        });
        toast("已应用到全部日志项，请点“保存全部”生效", "success");
    }

    function saveAll() {
        var inputs = document.querySelectorAll("input[data-retention-code]");
        var items = [];
        var invalid = null;
        Array.prototype.forEach.call(inputs, function (input) {
            if (!validDays(input.value)) { invalid = input; return; }
            items.push({
                code: input.getAttribute("data-retention-code"),
                days: valueOf(input)
            });
        });
        if (invalid) {
            toast("请输入整数天数", "error");
            invalid.focus();
            return;
        }
        var button = byId("logRetentionSaveAllBtn");
        button.disabled = true;
        Oaones.api("logretention.saveAll", { items: JSON.stringify(items) }, function (res) {
            button.disabled = false;
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "保存失败", "error");
                return;
            }
            toast((res.data && res.data.message) || "已保存全部设置", "success");
            load();
        });
    }

    function cleanNow() {
        var button = byId("logRetentionCleanBtn");
        var run = function () {
            button.disabled = true;
            Oaones.api("logretention.clean", {}, function (res) {
                button.disabled = false;
                if (!res || res.code !== 0) {
                    toast((res && res.msg) ? res.msg : "清理失败", "error");
                    return;
                }
                toast((res.data && res.data.message) || "清理完成", "success");
                load();
            });
        };
        if (window.OaonesToast && window.OaonesToast.confirm) {
            window.OaonesToast.confirm("确定按当前日志保留设置立即清理已过期数据吗？此操作不可恢复。",
                run, { mode: 1, title: "确认清理", okText: "立即清理", danger: true });
            return;
        }
        run();
    }

    function init() {
        if (!byId("logRetentionBody")) { return; }
        byId("logRetentionApplyAllBtn").onclick = applyAll;
        byId("logRetentionSaveAllBtn").onclick = saveAll;
        byId("logRetentionCleanBtn").onclick = cleanNow;
        load();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
