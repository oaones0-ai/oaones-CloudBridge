/**
 * OAones 功能开关的前端显隐。
 *
 * 只隐藏菜单和操作入口，不拦截接口。页面可以通过
 * data-oaones-feature="feature.code,other.code" 声明依赖的功能。
 */
(function () {
    "use strict";

    var API_URL = "/oaones/appsyn/systemSettings/featureApi.jsp?action=features.read";
    var MODULE_FEATURES = [
        { token: "/oaones/appsyn/syncCenter/", code: "person.sync.schedule" },
        { token: "/oaones/appsyn/appBind/", code: "app.bind" },
        { token: "/oaones/appsyn/attSync/", code: "att.sync" },
        { token: "/oaones/appsyn/meetingSync/", code: "mtg.sync" },
        { token: "/oaones/appsyn/scheduleSync/", code: "schedule.sync" }
    ];
    var state = {
        loaded: false,
        enabled: {},
        callbacks: []
    };

    function isEnabled(code, defaultValue) {
        if (!state.loaded) { return defaultValue !== false; }
        return state.enabled[String(code)] !== false;
    }

    function shouldShow(spec) {
        var codes = String(spec || "").split(",");
        for (var i = 0; i < codes.length; i++) {
            var code = codes[i].replace(/^\s+|\s+$/g, "");
            if (code && !isEnabled(code, true)) { return false; }
        }
        return true;
    }

    function hideElement(el) {
        if (!el || el.getAttribute("data-oaones-feature-hidden") === "1") { return; }
        el.setAttribute("data-oaones-feature-hidden", "1");
        el.style.display = "none";
        // E9 原生左菜单通常把网址放在 <a> 上、菜单项放在最近的 li；
        // 同时隐藏 li，避免图标、留白和分隔线仍占位。
        var parent = el.parentElement;
        while (parent && parent !== document.body && parent.tagName !== "UL") {
            if (parent.tagName === "LI" || parent.classList.contains("menu-item")
                    || parent.classList.contains("tenant-menu-item")) {
                if (parent.getAttribute("data-oaones-feature-hidden") !== "1") {
                    parent.setAttribute("data-oaones-feature-hidden", "1");
                    parent.style.display = "none";
                }
                break;
            }
            parent = parent.parentElement;
        }
    }

    function showElement(el) {
        if (!el || el.getAttribute("data-oaones-feature-hidden") !== "1") { return; }
        el.style.display = "";
        el.removeAttribute("data-oaones-feature-hidden");
        var parent = el.parentElement;
        while (parent && parent !== document.body && parent.tagName !== "UL") {
            if (parent.getAttribute("data-oaones-feature-hidden") === "1") {
                if (!parent.querySelector('[data-oaones-feature-hidden="1"]')) {
                    parent.style.display = "";
                    parent.removeAttribute("data-oaones-feature-hidden");
                }
                break;
            }
            parent = parent.parentElement;
        }
    }

    function processDocument(doc) {
        if (!doc || !doc.querySelectorAll) { return; }

        var marked = doc.querySelectorAll("[data-oaones-feature]");
        Array.prototype.forEach.call(marked, function (el) {
            if (shouldShow(el.getAttribute("data-oaones-feature"))) { showElement(el); }
            else { hideElement(el); }
        });
        var parentBound = doc.querySelectorAll("[data-oaones-feature-parent]");
        Array.prototype.forEach.call(parentBound, function (el) {
            var parent = el.getAttribute("data-oaones-feature-parent");
            if (parent && !isEnabled(parent, true) && "disabled" in el) {
                el.disabled = true;
            }
        });

        var links = doc.querySelectorAll("a[href]");
        Array.prototype.forEach.call(links, function (link) {
            var href = String(link.getAttribute("href") || "");
            for (var i = 0; i < MODULE_FEATURES.length; i++) {
                var rule = MODULE_FEATURES[i];
                if (href.indexOf(rule.token) >= 0) {
                    if (isEnabled(rule.code, true)) { showElement(link); }
                    else { hideElement(link); }
                    return;
                }
            }
        });
    }

    function processAllDocuments() {
        processDocument(document);
        try {
            if (window.parent && window.parent !== window) { processDocument(window.parent.document); }
        } catch (ignore) {
            // 跨域 / 沙箱阻止访问父窗口时，仅处理当前页。
        }
    }

    function notifyReady() {
        state.loaded = true;
        processAllDocuments();
        var callbacks = state.callbacks.slice(0);
        state.callbacks = [];
        callbacks.forEach(function (callback) {
            try { callback(state.enabled); } catch (ignore) { /* 页面回调异常不影响显隐 */ }
        });
    }

    function load() {
        try {
            var xhr = new XMLHttpRequest();
            xhr.open("GET", API_URL + "&_=" + new Date().getTime(), true);
            xhr.onreadystatechange = function () {
                if (xhr.readyState !== 4) { return; }
                try {
                    var res = JSON.parse(xhr.responseText || "{}");
                    state.enabled = (res && res.code === 0 && res.data && res.data.enabled)
                        ? res.data.enabled : {};
                } catch (ignore) {
                    state.enabled = {};
                }
                notifyReady();
            };
            xhr.send();
        } catch (ignore) {
            notifyReady();
        }
    }

    function onReady(callback) {
        if (typeof callback !== "function") { return; }
        if (state.loaded) {
            callback(state.enabled);
            return;
        }
        state.callbacks.push(callback);
    }

    function observe() {
        if (!window.MutationObserver || !document.body) { return; }
        var observer = new MutationObserver(function (mutations) {
            if (!state.loaded) { return; }
            for (var i = 0; i < mutations.length; i++) {
                if (mutations[i].addedNodes && mutations[i].addedNodes.length) {
                    processAllDocuments();
                    return;
                }
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }

    window.OaonesFeatureGate = {
        isEnabled: isEnabled,
        onReady: onReady,
        refresh: function () {
            state.loaded = false;
            load();
        }
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            observe();
            load();
        });
    } else {
        observe();
        load();
    }
})();
