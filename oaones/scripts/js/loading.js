/**
 * OAones 通用 Loading 组件。
 *
 * 页面接入：
 *   <link rel="stylesheet" href="/oaones/scripts/css/style.css?v=...">
 *   <script src="/oaones/scripts/js/loading.js?v=..."></script>
 *
 * API：
 *   OaonesLoading.show("文档加载中ing");
 *   OaonesLoading.showFor(document.getElementById("preview"), "文档加载中ing");
 *   OaonesLoading.hide();
 *   OaonesLoading.hideFor(document.getElementById("preview"));
 *
 * show() 为全屏遮罩；showFor(target) 会精确覆盖目标元素的当前可视区域，
 * 适合 iframe、预览容器等局部加载场景。相同 key 重复调用只更新文案。
 */
(function (global) {
    "use strict";

    var records = Object.create(null);
    var targetKeys = [];
    var sequence = 0;

    function ownerDocument(target) {
        return target && target.ownerDocument
            ? target.ownerDocument : global.document;
    }

    function loadingHost(documentRef) {
        return documentRef && (documentRef.body || documentRef.documentElement);
    }

    function textValue(value) {
        return value === null || value === undefined ? "" : String(value);
    }

    function buildKey(target, options) {
        var key = options.key;
        if (key) {
            return String(key);
        }
        if (target) {
            for (var i = 0; i < targetKeys.length; i += 1) {
                if (targetKeys[i].target === target) {
                    return targetKeys[i].key;
                }
            }
            key = "target-" + (++sequence);
            targetKeys.push({target: target, key: key});
            return key;
        }
        return "default";
    }

    function createRoot(documentRef, key) {
        var root = documentRef.createElement("div");
        root.className = "oaones-loading";
        root.setAttribute("data-oaones-loading-key", key);
        root.setAttribute("role", "status");
        root.setAttribute("aria-live", "polite");
        root.setAttribute("aria-busy", "true");

        var panel = documentRef.createElement("div");
        panel.className = "oaones-loading-panel";
        var spinner = documentRef.createElement("span");
        spinner.className = "oaones-loading-spinner";
        spinner.setAttribute("aria-hidden", "true");
        var text = documentRef.createElement("span");
        text.className = "oaones-loading-message";
        panel.appendChild(spinner);
        panel.appendChild(text);
        root.appendChild(panel);
        return {root: root, text: text};
    }

    function place(record) {
        if (!record.target || !record.root.parentNode) {
            return;
        }
        if (typeof record.target.isConnected === "boolean"
                && !record.target.isConnected) {
            hide(record.key);
            return;
        }
        var rect = record.target.getBoundingClientRect
            ? record.target.getBoundingClientRect() : null;
        if (!rect || rect.width <= 0 || rect.height <= 0) {
            record.root.style.visibility = "hidden";
            return;
        }
        record.root.style.visibility = "visible";
        record.root.style.top = rect.top + "px";
        record.root.style.left = rect.left + "px";
        record.root.style.width = rect.width + "px";
        record.root.style.height = rect.height + "px";
    }

    function bindPosition(record) {
        if (!record.target) {
            return;
        }
        var view = record.document && record.document.defaultView;
        if (!view) {
            return;
        }
        record.reposition = function () {
            place(record);
        };
        view.addEventListener("resize", record.reposition, false);
        view.addEventListener("scroll", record.reposition, true);
        place(record);
    }

    function unbindPosition(record) {
        if (!record.reposition) {
            return;
        }
        var view = record.document && record.document.defaultView;
        if (view) {
            view.removeEventListener("resize", record.reposition, false);
            view.removeEventListener("scroll", record.reposition, true);
        }
        record.reposition = null;
    }

    function clearTargetKey(key) {
        targetKeys = targetKeys.filter(function (entry) {
            return entry.key !== key;
        });
    }

    function show(message, options) {
        options = options || {};
        var target = options.target || null;
        var documentRef = ownerDocument(target);
        var host = loadingHost(documentRef);
        if (!documentRef || !host) {
            return null;
        }

        var key = buildKey(target, options);
        var record = records[key];
        if (record && record.root.ownerDocument !== documentRef) {
            hide(key);
            record = null;
        }
        if (record && record.target !== target) {
            hide(key);
            record = null;
        }
        if (!record) {
            var nodes = createRoot(documentRef, key);
            record = {
                key: key,
                target: target,
                document: documentRef,
                root: nodes.root,
                text: nodes.text
            };
            if (target) {
                record.root.className += " oaones-loading-target";
            }
            host.appendChild(record.root);
            records[key] = record;
            bindPosition(record);
        }
        record.text.textContent = textValue(message);
        if (options.className) {
            record.root.className = "oaones-loading"
                + (target ? " oaones-loading-target" : "")
                + " " + options.className;
        }
        place(record);
        return record.root;
    }

    function showFor(target, message, options) {
        var next = {};
        options = options || {};
        for (var key in options) {
            if (Object.prototype.hasOwnProperty.call(options, key)) {
                next[key] = options[key];
            }
        }
        next.target = target;
        return show(message, next);
    }

    function hide(key) {
        var record = records[String(key === undefined ? "default" : key)];
        if (!record) {
            return;
        }
        unbindPosition(record);
        if (record.root.parentNode) {
            record.root.parentNode.removeChild(record.root);
        }
        delete records[record.key];
        clearTargetKey(record.key);
    }

    function hideFor(target) {
        for (var i = 0; i < targetKeys.length; i += 1) {
            if (targetKeys[i].target === target) {
                hide(targetKeys[i].key);
                return;
            }
        }
    }

    function clear() {
        Object.keys(records).forEach(hide);
    }

    function get(key) {
        return records[String(key === undefined ? "default" : key)] || null;
    }

    global.OaonesLoading = {
        show: show,
        showFor: showFor,
        hide: hide,
        hideFor: hideFor,
        clear: clear,
        get: get
    };

    var currentScript = global.document && global.document.currentScript;
    var autoText = currentScript
        && currentScript.getAttribute("data-oaones-loading-auto");
    if (autoText !== null && autoText !== undefined) {
        show(autoText || "加载中", {
            key: currentScript.getAttribute("data-oaones-loading-key") || "auto"
        });
    }
})(window);
