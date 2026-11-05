/**
 * 系统设置（/oaones/appsyn/systemSettings/systemSettings.jsp）。
 *
 * “功能设置”维护菜单/按钮显隐开关；“日志设置”维护各日志表的保留天数。
 * 功能开关只影响页面入口，后端接口不做拦截。
 */
(function () {
    "use strict";

    var state = {
        logItems: [],
        featureItems: []
    };

    function byId(id) { return document.getElementById(id); }
    function valueOf(el) {
        return String(el && el.value != null ? el.value : "").trim();
    }
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
    function bytesText(value) {
        var bytes = Number(value);
        if (!isFinite(bytes) || bytes < 0) { return "—"; }
        if (bytes < 1024) { return Math.floor(bytes) + " B"; }
        if (bytes < 1024 * 1024) { return (bytes / 1024).toFixed(1) + " KB"; }
        if (bytes < 1024 * 1024 * 1024) {
            return (bytes / 1024 / 1024).toFixed(1) + " MB";
        }
        return (bytes / 1024 / 1024 / 1024).toFixed(1) + " GB";
    }

    function switchTab(tab) {
        if (tab !== "feature" && tab !== "log") { tab = "feature"; }
        var tabs = document.querySelectorAll("#systemSettingsTabs [data-settings-tab]");
        Array.prototype.forEach.call(tabs, function (button) {
            var active = button.getAttribute("data-settings-tab") === tab;
            button.classList.toggle("active", active);
            button.setAttribute("aria-selected", active ? "true" : "false");
        });
        var panels = document.querySelectorAll("[data-settings-panel]");
        Array.prototype.forEach.call(panels, function (panel) {
            panel.classList.toggle("active", panel.getAttribute("data-settings-panel") === tab);
        });
        var helps = document.querySelectorAll("[data-settings-help]");
        Array.prototype.forEach.call(helps, function (help) {
            help.style.display = help.getAttribute("data-settings-help") === tab ? "" : "none";
        });
        if (window.history && window.history.replaceState) {
            window.history.replaceState(null, "", tab === "log" ? "#log" : "#feature");
        }
    }

    function initialTab() {
        var hash = String(window.location.hash || "").replace(/^#/, "");
        if (hash === "log") { return "log"; }
        var query = String(window.location.search || "");
        return /(?:^|[?&])tab=log(?:&|$)/.test(query) ? "log" : "feature";
    }

    /* ---------------- 功能设置 ---------------- */

    function loadFeatures() {
        if (!byId("featureSettingsGroups")) { return; }
        Oaones.api("features.list", {}, function (res) {
            if (!res || res.code !== 0) {
                byId("featureSettingsGroups").innerHTML =
                    '<p class="notice retention-empty">' + esc((res && res.msg) || "功能设置加载失败") + "</p>";
                return;
            }
            var data = res.data || {};
            state.featureItems = data.items || [];
            renderFeatures(data);
        });
    }

    function renderFeatures(data) {
        var root = byId("featureSettingsGroups");
        if (!root) { return; }
        var missing = byId("featureSettingsMissingTip");
        if (missing) {
            missing.textContent = data.missing
                ? "功能设置表尚未创建，当前全部按启用处理；请先执行 oaones0022 迁移脚本并重启 E9。"
                : "";
            missing.style.display = data.missing ? "" : "none";
        }
        if (!state.featureItems.length) {
            root.innerHTML = '<p class="notice retention-empty">没有可配置的功能开关</p>';
            return;
        }

        var groups = [];
        var groupRows = {};
        state.featureItems.forEach(function (item) {
            var group = String(item.group || "其他");
            if (!groupRows[group]) {
                groupRows[group] = [];
                groups.push(group);
            }
            groupRows[group].push(item);
        });

        var html = "";
        groups.forEach(function (group) {
            html += '<section class="feature-setting-group">'
                + '<h3 class="feature-setting-group-title">' + esc(group) + "</h3>";
            groupRows[group].forEach(function (item) {
                var enabled = item.enabled !== false;
                var canDisable = item.canDisable !== false;
                var locked = enabled && !canDisable;
                var reason = item.lockedReason || "";
                html += '<div class="feature-setting-row' + (enabled ? "" : " is-off") + '"'
                    + " data-feature-row='" + esc(item.code) + "'>"
                    + '<div class="feature-setting-copy">'
                    + '<div class="feature-setting-name">' + esc(item.name)
                    + '<span class="feature-setting-state">' + (enabled ? "显示" : "隐藏") + "</span></div>"
                    + '<div class="feature-setting-desc">' + esc(item.description) + "</div>"
                    + (reason ? '<div class="feature-setting-reason">' + esc(reason) + "</div>" : "")
                    + "</div>"
                    + '<label class="feature-switch" title="' + esc(locked ? reason : "切换显示状态") + '">'
                    + '<input type="checkbox" data-feature-code="' + esc(item.code) + '"'
                    + " data-parent-code='" + esc(item.parentCode || "") + "'"
                    + ' data-can-disable="' + (canDisable ? "1" : "0") + '"'
                    + (enabled ? " checked" : "") + (locked ? " disabled" : "") + ">"
                    + '<span class="feature-switch-track" aria-hidden="true"></span>'
                    + '<span class="feature-switch-text">' + (enabled ? "已启用" : "已关闭") + "</span>"
                    + "</label></div>";
            });
            html += "</section>";
        });
        root.innerHTML = html;
        bindFeatureInputs();
        refreshFeatureDependencies();
    }

    function featureInput(code) {
        return document.querySelector("input[data-feature-code='" + code + "']");
    }

    function refreshFeatureDependencies() {
        var inputs = document.querySelectorAll("input[data-feature-code]");
        Array.prototype.forEach.call(inputs, function (input) {
            var code = input.getAttribute("data-feature-code");
            var row = input.closest ? input.closest("[data-feature-row]") : null;
            var enabled = input.checked;
            var saving = input.getAttribute("data-saving") === "1";
            if (row) { row.classList.toggle("is-off", !enabled); }
            var stateEl = row ? row.querySelector(".feature-setting-state") : null;
            var textEl = row ? row.querySelector(".feature-switch-text") : null;
            if (stateEl) { stateEl.textContent = enabled ? "显示" : "隐藏"; }
            if (textEl) { textEl.textContent = enabled ? "已启用" : "已关闭"; }

            var parentCode = input.getAttribute("data-parent-code") || "";
            if (parentCode) {
                var parent = featureInput(parentCode);
                var parentOff = parent && !parent.checked;
                var ownLocked = enabled && input.getAttribute("data-can-disable") === "0";
                input.disabled = Boolean(saving || parentOff || ownLocked);
                if (row) {
                    row.classList.toggle("is-parent-off", Boolean(parentOff));
                }
                if (parentOff) {
                    input.title = "请先启用上级功能";
                } else {
                    input.title = "";
                }
            }
            if (!parentCode) {
                var ownLockedRoot = enabled && input.getAttribute("data-can-disable") === "0";
                input.disabled = Boolean(saving || ownLockedRoot);
            }
            void code;
        });
    }

    function bindFeatureInputs() {
        var inputs = document.querySelectorAll("input[data-feature-code]");
        Array.prototype.forEach.call(inputs, function (input) {
            input.onchange = function () {
                saveFeatureInput(input);
            };
        });
    }

    function saveFeatureInput(input) {
        if (!input || input.getAttribute("data-saving") === "1") { return; }
        var code = input.getAttribute("data-feature-code");
        var enabled = Boolean(input.checked);
        input.setAttribute("data-saving", "1");
        input.disabled = true;
        refreshFeatureDependencies();
        Oaones.api("features.save", {
            items: JSON.stringify([{ code: code, enabled: enabled }])
        }, function (res) {
            input.removeAttribute("data-saving");
            if (!res || res.code !== 0) {
                input.checked = !enabled;
                refreshFeatureDependencies();
                toast((res && res.msg) ? res.msg : "功能设置保存失败", "error");
                return;
            }
            state.featureItems.forEach(function (item) {
                if (item.code === code) { item.enabled = enabled; }
            });
            refreshFeatureDependencies();
            toast("已保存", "success");
            if (window.OaonesFeatureGate && window.OaonesFeatureGate.refresh) {
                window.OaonesFeatureGate.refresh();
            }
        });
    }

    /* ---------------- 日志设置 ---------------- */

    function loadLogSettings() {
        if (!byId("logRetentionBody")) { return; }
        Oaones.api("logretention.list", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "加载日志设置失败", "error");
                return;
            }
            var data = res.data || {};
            state.logItems = data.items || [];
            renderLogRows();
            renderLogStatus(data);
        });
    }

    function renderLogRows() {
        var body = byId("logRetentionBody");
        if (!body) { return; }
        if (!state.logItems.length) {
            body.innerHTML = '<tr><td colspan="7" class="retention-empty">没有可配置的日志表</td></tr>';
            return;
        }
        var html = "";
        state.logItems.forEach(function (item) {
            var days = Number(item.keepDays == null ? 0 : item.keepDays);
            var isFileLog = item.rowType === "file";
            var metricHtml = isFileLog
                ? '<span title="log/mylog 下共 ' + esc(item.fileCount || 0) + ' 个文件">'
                    + esc(bytesText(item.totalSize)) + "</span>"
                : esc(countText(item.rowCount));
            html += "<tr data-retention-row='" + esc(item.code) + "'>"
                + "<td><b>" + esc(item.name) + "</b></td>"
                + "<td class='retention-desc'>" + esc(item.description) + "</td>"
                + "<td><code>" + esc(item.table) + "</code></td>"
                + "<td class='retention-row-count'><span data-retention-count='" + esc(item.code) + "'>"
                + metricHtml + "</span></td>"
                + "<td><input class='retention-days' type='number' step='1'"
                + " data-retention-code='" + esc(item.code) + "'"
                + " value='" + esc(days) + "' aria-label='" + esc(item.name) + "保留天数'></td>"
                + "<td><span data-retention-state='" + esc(item.code) + "'>"
                + esc(statusText(days)) + "</span></td>"
                + "<td><span class='row-ops'>"
                + "<button type='button' class='btn small secondary'"
                + " data-retention-save='" + esc(item.code) + "'>保存</button>"
                + "<button type='button' class='btn small secondary'"
                + " data-retention-clean='" + esc(item.code) + "'>立即清理</button>"
                + "</span></td>"
                + "</tr>";
        });
        body.innerHTML = html;
        bindLogRows();
    }

    function renderLogStatus(data) {
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
                pageTip.textContent = "日志设置配置表尚未创建，当前全部按永久保留处理。"
                    + "请先执行 oaones0019 迁移脚本并重启 E9。";
                pageTip.style.display = "";
            } else {
                pageTip.style.display = "none";
            }
        }
    }

    function refreshLogState(code) {
        var input = document.querySelector("input[data-retention-code='" + code + "']");
        var label = document.querySelector("[data-retention-state='" + code + "']");
        if (!input || !label) { return; }
        label.textContent = statusText(normalizeInput(input));
    }

    function bindLogRows() {
        var inputs = document.querySelectorAll("input[data-retention-code]");
        Array.prototype.forEach.call(inputs, function (input) {
            input.oninput = function () { refreshLogState(input.getAttribute("data-retention-code")); };
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
                    loadLogSettings();
                });
            };
        });
        var cleanButtons = document.querySelectorAll("button[data-retention-clean]");
        Array.prototype.forEach.call(cleanButtons, function (button) {
            button.onclick = function () {
                cleanLogNow(button.getAttribute("data-retention-clean"), button);
            };
        });
    }

    function cleanLogNow(code, button) {
        var item = null;
        state.logItems.forEach(function (candidate) {
            if (candidate.code === code) { item = candidate; }
        });
        if (!item) {
            toast("日志设置项不存在，请刷新后重试", "error");
            return;
        }
        if (Number(item.keepDays == null ? 0 : item.keepDays) <= 0) {
            toast(item.name + "当前为永久保留，不会清理任何数据", "info");
            return;
        }
        var run = function () {
            button.disabled = true;
            Oaones.api("logretention.cleanOne", { code: code }, function (res) {
                button.disabled = false;
                if (!res || res.code !== 0) {
                    toast((res && res.msg) ? res.msg : "清理失败", "error");
                    return;
                }
                toast((res.data && res.data.message) || "清理完成", "success");
                loadLogSettings();
            });
        };
        window.OaonesToast.confirm(
            "确定按当前保留设置立即清理“" + item.name + "”中已过期的数据吗？此操作不可恢复。",
            run,
            { mode: 1, title: "确认清理", okText: "立即清理", danger: true });
    }

    function applyAllLogDays() {
        var global = byId("logRetentionGlobalDays");
        if (!global || !validDays(global.value)) {
            toast("统一设置请输入整数天数", "error");
            return;
        }
        var inputs = document.querySelectorAll("input[data-retention-code]");
        Array.prototype.forEach.call(inputs, function (input) {
            input.value = valueOf(global);
            refreshLogState(input.getAttribute("data-retention-code"));
        });
        toast("已应用到全部日志项，请点“保存全部”生效", "success");
    }

    function saveAllLogDays() {
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
        if (button) { button.disabled = true; }
        Oaones.api("logretention.saveAll", { items: JSON.stringify(items) }, function (res) {
            if (button) { button.disabled = false; }
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "保存失败", "error");
                return;
            }
            toast((res.data && res.data.message) || "已保存全部设置", "success");
            loadLogSettings();
        });
    }

    function cleanLogsNow() {
        var button = byId("logRetentionCleanBtn");
        var run = function () {
            if (button) { button.disabled = true; }
            Oaones.api("logretention.clean", {}, function (res) {
                if (button) { button.disabled = false; }
                if (!res || res.code !== 0) {
                    toast((res && res.msg) ? res.msg : "清理失败", "error");
                    return;
                }
                toast((res.data && res.data.message) || "清理完成", "success");
                loadLogSettings();
            });
        };
        if (window.OaonesToast && window.OaonesToast.confirm) {
            window.OaonesToast.confirm("确定按当前日志设置立即清理已过期数据吗？此操作不可恢复。",
                run, { mode: 1, title: "确认清理", okText: "立即清理", danger: true });
            return;
        }
        run();
    }

    function init() {
        var tabs = document.querySelectorAll("#systemSettingsTabs [data-settings-tab]");
        Array.prototype.forEach.call(tabs, function (button) {
            button.onclick = function (event) {
                if (event) { event.preventDefault(); }
                switchTab(button.getAttribute("data-settings-tab"));
            };
        });
        switchTab(initialTab());

        if (byId("featureSettingsGroups")) {
            loadFeatures();
        }
        if (byId("logRetentionBody")) {
            byId("logRetentionApplyAllBtn").onclick = applyAllLogDays;
            byId("logRetentionSaveAllBtn").onclick = saveAllLogDays;
            byId("logRetentionCleanBtn").onclick = cleanLogsNow;
            loadLogSettings();
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
