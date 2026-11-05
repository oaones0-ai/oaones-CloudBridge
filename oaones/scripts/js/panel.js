/**
 * OAones 通用弹出面板（OaonesPanel）。
 *
 * 标准结构：
 *   大标题
 *   小标题
 *   内容区（可放 HTML、DOM 节点或其它 OAones 组件）
 *   底部取消 / 确定按钮
 *
 * 强制交互：
 *   - 按住标题栏拖动；
 *   - 右上角 × 退出；
 *   - Esc 关闭最上层弹出面板。
 *
 * 依赖：
 *   modal.js -> panel.js
 *
 * 示例：
 *   OaonesPanel.open({
 *       title: "编辑用户",
 *       subtitle: "修改后点击确定保存",
 *       size: { width: 760, height: 560 },
 *       content: document.getElementById("editForm"),
 *       onCancel: function (panel, reason) {},
 *       onConfirm: function (panel, event) {}
 *   });
 */
(function (global, document) {
    "use strict";

    var sequence = 0;
    var instances = [];
    var eventsBound = false;
    var missingModalWarned = false;
    var MODAL_SELECTOR = ".modal, .oa-modal";
    var FOCUS_SELECTOR = "a[href],button:not([disabled]),input:not([disabled]):not([type='hidden']),"
        + "select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex='-1'])";

    var SIZE_PRESETS = {
        small: 480,
        medium: 680,
        large: 900,
        xlarge: 1120
    };

    var DEFAULTS = {
        title: "",
        subtitle: "",
        ariaLabel: "操作面板",
        cancelText: "取消",
        confirmText: "确定",
        confirmDanger: false,
        cancelDisabled: false,
        confirmDisabled: false,
        closeOnCancel: true,
        closeOnConfirm: true,
        content: "",
        contentType: "html",
        className: "",
        buttons: [],
        onOpen: null,
        onClose: null,
        onCancel: null,
        onConfirm: null,
        onButtonClick: null,
        onClosed: null
    };

    var DEFAULT_SIZE = {
        width: 680,
        height: "auto",
        minWidth: 320,
        maxWidth: "92vw",
        minHeight: "auto",
        maxHeight: "84vh"
    };

    function assign(target) {
        for (var i = 1; i < arguments.length; i++) {
            var source = arguments[i] || {};
            for (var key in source) {
                if (Object.prototype.hasOwnProperty.call(source, key)) {
                    target[key] = source[key];
                }
            }
        }
        return target;
    }

    function esc(value) {
        if (value === null || value === undefined) { return ""; }
        return String(value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function isNode(value) {
        return !!(value && typeof value === "object" && typeof value.nodeType === "number");
    }

    function isArray(value) {
        return Object.prototype.toString.call(value) === "[object Array]";
    }

    function validCssSize(value) {
        if (value === null || value === undefined || value === "") { return null; }
        if (value === "auto") { return value; }
        if (typeof value === "number" && isFinite(value) && value >= 0) {
            return value + "px";
        }
        var text = String(value).replace(/^\s+|\s+$/g, "");
        return /^(?:\d+(?:\.\d+)?|\.\d+)(?:px|rem|em|%|vw|vh)$/.test(text) ? text : null;
    }

    /**
     * 归一化尺寸。可传：
     *   size: "small" | "medium" | "large" | "xlarge"
     *   size: 800
     *   size: { width: 800, height: 600 }
     *   width / height / minWidth / maxWidth / minHeight / maxHeight
     */
    function normalizeSize(input, base) {
        var source = (typeof input === "number" || typeof input === "string")
            ? { size: input }
            : (input || {});
        var result = assign({}, base || DEFAULT_SIZE);
        var values = {};
        var size = source.size;

        if (typeof size === "number" || typeof size === "string") {
            if (typeof size === "string" && SIZE_PRESETS[size]) {
                values.width = SIZE_PRESETS[size];
            } else {
                values.width = size;
            }
        } else if (size && typeof size === "object") {
            assign(values, size);
        }

        ["width", "height", "minWidth", "maxWidth", "minHeight", "maxHeight"].forEach(function (key) {
            if (source[key] !== undefined) { values[key] = source[key]; }
        });

        Object.keys(values).forEach(function (key) {
            var normalized = validCssSize(values[key]);
            if (normalized !== null) { result[key] = normalized; }
        });
        return result;
    }

    function optionalText(value) {
        return value === null || value === undefined ? "" : String(value);
    }

    function safeId(value) {
        return String(value || "panel").replace(/[^A-Za-z0-9_-]/g, "-");
    }

    function applySize(modal, size) {
        modal.style.width = size.width;
        modal.style.height = size.height;
        modal.style.minWidth = size.minWidth;
        modal.style.maxWidth = size.maxWidth;
        modal.style.minHeight = size.minHeight;
        modal.style.maxHeight = size.maxHeight;
    }

    function renderContent(target, content, contentType, panel) {
        if (typeof content === "function") {
            var rendered = content(target, panel);
            if (rendered !== undefined && rendered !== null) {
                renderContent(target, rendered, contentType, panel);
            }
            return;
        }
        if (isArray(content)) {
            content.forEach(function (item) {
                renderContent(target, item, contentType, panel);
            });
            return;
        }
        if (isNode(content)) {
            target.appendChild(content);
            return;
        }
        if (content === null || content === undefined || content === "") { return; }
        if (contentType === "text") {
            target.textContent = String(content);
        } else {
            target.innerHTML = String(content);
        }
    }

    function clearContent(target) {
        target.innerHTML = "";
        while (target.firstChild) {
            target.removeChild(target.firstChild);
        }
    }

    function visibleModal() {
        if (!document || typeof document.querySelectorAll !== "function") { return null; }
        var layers = document.querySelectorAll(MODAL_SELECTOR);
        for (var i = layers.length - 1; i >= 0; i--) {
            if (layers[i] && layers[i].style && layers[i].style.display !== "none") {
                return layers[i];
            }
        }
        return null;
    }

    function focusableElements(modal) {
        var list = modal.querySelectorAll ? modal.querySelectorAll(FOCUS_SELECTOR) : [];
        var result = [];
        for (var i = 0; i < list.length; i++) {
            var el = list[i];
            if (!el || el.disabled || el.hidden) { continue; }
            if (el.getAttribute && el.getAttribute("aria-hidden") === "true") { continue; }
            if ("offsetParent" in el && el.offsetParent === null && el.getClientRects) { continue; }
            result.push(el);
        }
        return result;
    }

    function focusInside(modal) {
        if (!modal) { return; }
        var preferred = modal.querySelector
            ? modal.querySelector("[autofocus], .oa-panel-body input:not([disabled]), "
                + ".oa-panel-body select:not([disabled]), .oa-panel-body textarea:not([disabled]), "
                + ".oa-panel-body button:not([disabled])")
            : null;
        var target = preferred || modal;
        try {
            if (target && typeof target.focus === "function") { target.focus(); }
        } catch (ignore) {
            // 焦点失败不影响弹出面板显示。
        }
    }

    function bringToFront(mask, modal) {
        if (!document || !document.body) { return; }
        // appendChild 会把已有节点移动到末尾，使最近打开的嵌套面板位于同层最上方。
        if (mask) { document.body.appendChild(mask); }
        if (modal) { document.body.appendChild(modal); }
    }

    function createElement(tagName, className) {
        var el = document.createElement(tagName);
        el.className = className || "";
        return el;
    }

    function removeArray(list, item) {
        for (var i = list.length - 1; i >= 0; i--) {
            if (list[i] === item) { list.splice(i, 1); }
        }
    }

    function create(options) {
        var opt = assign({}, DEFAULTS, options || {});
        if (!document || !document.body) { return null; }

        sequence += 1;
        var id = "oaPanel-" + safeId(opt.key) + "-" + sequence;
        var titleId = id + "-title";
        var subtitleId = id + "-subtitle";
        var sizes = normalizeSize(opt);
        var state = {
            open: false,
            destroyed: false,
            sizes: sizes,
            previousFocus: null
        };

        var mask = createElement("div", "modal-mask oa-panel-mask");
        mask.id = id + "-mask";
        mask.setAttribute("aria-hidden", "true");

        var modal = createElement("div", "modal oa-panel-modal");
        if (opt.className) { modal.className += " " + opt.className; }
        modal.id = id;
        modal.tabIndex = -1;
        modal.setAttribute("role", "dialog");
        modal.setAttribute("aria-modal", "true");
        modal.setAttribute("aria-labelledby", titleId);
        modal.setAttribute("aria-describedby", subtitleId);
        modal.setAttribute("data-modal-mask", mask.id);
        modal.setAttribute("data-oa-panel", safeId(opt.key || id));

        var head = createElement("div", "modal-head oa-panel-head");
        var heading = createElement("div", "oa-panel-heading");
        var title = createElement("h2", "oa-panel-title");
        title.id = titleId;
        var subtitle = createElement("p", "oa-panel-subtitle");
        subtitle.id = subtitleId;
        var closeButton = createElement("button", "modal-close oa-panel-close");
        closeButton.type = "button";
        closeButton.setAttribute("title", "关闭");
        closeButton.setAttribute("aria-label", "关闭");
        closeButton.setAttribute("data-oa-panel-action", "cancel");
        closeButton.innerHTML = "×";
        heading.appendChild(title);
        heading.appendChild(subtitle);
        head.appendChild(heading);
        head.appendChild(closeButton);

        var body = createElement("div", "modal-body oa-panel-body");
        var content = createElement("div", "oa-panel-content");
        body.appendChild(content);

        var foot = createElement("div", "modal-foot oa-panel-foot");
        var customLeft = createElement("div", "oa-panel-buttons oa-panel-buttons-left");
        var actions = createElement("div", "oa-panel-buttons oa-panel-buttons-right");
        var customRight = createElement("span", "oa-panel-custom-buttons");
        var cancelButton = createElement("button", "btn secondary oa-panel-button oa-panel-cancel");
        cancelButton.type = "button";
        cancelButton.setAttribute("data-oa-panel-action", "cancel");
        var confirmButton = createElement("button", "btn oa-panel-button oa-panel-confirm" + (opt.confirmDanger ? " danger" : ""));
        confirmButton.type = "button";
        confirmButton.setAttribute("data-oa-panel-action", "confirm");
        actions.appendChild(customRight);
        actions.appendChild(cancelButton);
        actions.appendChild(confirmButton);
        foot.appendChild(customLeft);
        foot.appendChild(actions);

        modal.appendChild(head);
        modal.appendChild(body);
        modal.appendChild(foot);
        modal.style.display = "none";
        applySize(modal, sizes);

        function setTitle(value) {
            opt.title = optionalText(value);
            title.textContent = opt.title;
            title.hidden = !opt.title;
            if (opt.title) {
                modal.setAttribute("aria-labelledby", titleId);
                modal.removeAttribute("aria-label");
            } else {
                modal.removeAttribute("aria-labelledby");
                modal.setAttribute("aria-label", opt.ariaLabel || DEFAULTS.ariaLabel);
            }
        }

        function setSubtitle(value) {
            opt.subtitle = optionalText(value);
            subtitle.textContent = opt.subtitle;
            subtitle.hidden = !opt.subtitle;
            if (opt.subtitle) {
                modal.setAttribute("aria-describedby", subtitleId);
            } else {
                modal.removeAttribute("aria-describedby");
            }
        }

        function setButtonState(button, disabled, text) {
            button.disabled = !!disabled;
            if (text !== undefined) { button.textContent = optionalText(text); }
            button.setAttribute("aria-disabled", button.disabled ? "true" : "false");
        }

        function updateButtons() {
            setButtonState(cancelButton, opt.cancelDisabled, opt.cancelText);
            setButtonState(confirmButton, opt.confirmDisabled, opt.confirmText);
            confirmButton.className = "btn oa-panel-button oa-panel-confirm"
                + (opt.confirmDanger ? " danger" : "");
        }

        var customButtons = [];

        function findCustomButton(key) {
            for (var i = 0; i < customButtons.length; i++) {
                if (customButtons[i].key === String(key)) { return customButtons[i]; }
            }
            return null;
        }

        function customClassName(config) {
            var className = config.className || "secondary";
            return "btn oa-panel-button oa-panel-custom-button " + className
                + (config.danger ? " danger" : "");
        }

        function runCustomButton(ctx, event) {
            if (!ctx || ctx.disabled) { return; }
            if (event && event.preventDefault) { event.preventDefault(); }
            if (typeof ctx.onClick === "function" && ctx.onClick(api, ctx.config, event) === false) {
                return;
            }
            if (typeof opt.onButtonClick === "function"
                && opt.onButtonClick(api, ctx.config, event) === false) {
                return;
            }
            if (ctx.close) {
                close(typeof ctx.close === "string" ? ctx.close : "button:" + ctx.key, event);
            }
        }

        function makeCustomButton(config, index) {
            var item = assign({
                key: "custom-" + (sequence + "-" + (index + 1)),
                text: "自定义操作",
                className: "secondary",
                position: "left",
                title: "",
                disabled: false,
                danger: false,
                close: false
            }, config || {});
            item.key = String(item.key);
            var button = createElement("button", customClassName(item));
            button.type = "button";
            button.setAttribute("data-oa-panel-custom", item.key);
            if (item.title) { button.setAttribute("title", item.title); }
            var ctx = {
                key: item.key,
                config: item,
                element: button,
                disabled: !!item.disabled,
                onClick: item.onClick,
                close: item.close
            };
            button.addEventListener("click", function (event) {
                runCustomButton(ctx, event);
            });
            customButtons.push(ctx);
            return ctx;
        }

        function renderCustomButtons() {
            while (customLeft.firstChild) { customLeft.removeChild(customLeft.firstChild); }
            while (customRight.firstChild) { customRight.removeChild(customRight.firstChild); }
            customButtons.length = 0;
            var source = isArray(opt.buttons) ? opt.buttons : [];
            var usedKeys = {};
            var list = source.map(function (config, index) {
                var item = assign({}, config || {});
                var key = item.key === undefined || item.key === "" ? "" : String(item.key);
                if (!key || usedKeys[key]) {
                    key = "custom-" + sequence + "-" + (index + 1);
                    while (usedKeys[key]) { key += "-1"; }
                }
                item.key = key;
                usedKeys[key] = true;
                return item;
            });
            opt.buttons = list;
            list.forEach(function (config, index) {
                var ctx = makeCustomButton(config, index);
                var group = ctx.config.position === "right" ? customRight : customLeft;
                group.appendChild(ctx.element);
                refreshCustomButton(ctx);
            });
        }

        function refreshCustomButton(ctx) {
            if (!ctx || !ctx.element) { return; }
            ctx.element.textContent = optionalText(ctx.config.text);
            ctx.element.className = customClassName(ctx.config);
            ctx.element.disabled = !!(ctx.disabled || ctx.config.disabled);
            ctx.element.setAttribute("aria-disabled", ctx.element.disabled ? "true" : "false");
        }

        function open() {
            if (state.destroyed) { return api; }
            if (!state.open) {
                state.previousFocus = document.activeElement || null;
                state.open = true;
            }
            bringToFront(mask, modal);
            if (!global.OaonesModal && !missingModalWarned && global.console) {
                missingModalWarned = true;
                global.console.warn("OaonesPanel 需要先加载 modal.js，才能启用拖动能力。");
            }
            if (global.OaonesModal && typeof global.OaonesModal.open === "function") {
                global.OaonesModal.open(modal, { mask: mask, focus: false });
            } else {
                mask.setAttribute("aria-hidden", "false");
                mask.style.display = "block";
            }
            modal.setAttribute("aria-hidden", "false");
            modal.style.display = "flex";
            if (typeof opt.onOpen === "function") { opt.onOpen(api); }
            global.setTimeout(function () {
                if (state.open) { focusInside(modal); }
            }, 0);
            return api;
        }

        function close(reason, event) {
            if (state.destroyed || !state.open) { return api; }
            state.open = false;
            if (global.OaonesModal && typeof global.OaonesModal.close === "function") {
                global.OaonesModal.close(modal, { mask: mask });
            } else {
                mask.setAttribute("aria-hidden", "true");
                mask.style.display = "none";
                modal.style.display = "none";
            }
            modal.setAttribute("aria-hidden", "true");
            if (typeof opt.onClose === "function") { opt.onClose(api, reason || "close", event); }
            var previousFocus = state.previousFocus;
            state.previousFocus = null;
            global.setTimeout(function () {
                if (previousFocus && previousFocus.isConnected === false) { return; }
                try {
                    if (previousFocus && typeof previousFocus.focus === "function") {
                        previousFocus.focus();
                    }
                } catch (ignore) {
                    // 原焦点元素已移除时无需恢复。
                }
            }, 0);
            if (typeof opt.onClosed === "function") { opt.onClosed(api, reason || "close", event); }
            return api;
        }

        function cancel(reason, event) {
            if (state.destroyed || !state.open) { return false; }
            if (opt.cancelDisabled) { return false; }
            if (typeof opt.onCancel === "function"
                && opt.onCancel(api, reason || "cancel", event) === false) {
                return false;
            }
            if (opt.closeOnCancel) { close(reason || "cancel", event); }
            return true;
        }

        function confirm(event) {
            if (state.destroyed || !state.open || opt.confirmDisabled) { return false; }
            if (typeof opt.onConfirm === "function"
                && opt.onConfirm(api, event) === false) {
                return false;
            }
            if (opt.closeOnConfirm) { close("confirm", event); }
            return true;
        }

        function onModalClick(event) {
            var target = event.target;
            if (!target) { return; }
            var actionEl = target;
            while (actionEl && actionEl !== modal) {
                if (actionEl.getAttribute && actionEl.getAttribute("data-oa-panel-action")) { break; }
                actionEl = actionEl.parentNode;
            }
            if (!actionEl || actionEl === modal || !actionEl.getAttribute) { return; }
            var action = actionEl.getAttribute("data-oa-panel-action");
            if (action === "cancel") {
                if (event.preventDefault) { event.preventDefault(); }
                cancel(actionEl === closeButton ? "close" : "cancel", event);
            } else if (action === "confirm") {
                if (event.preventDefault) { event.preventDefault(); }
                confirm(event);
            }
        }

        var api = {
            id: id,
            key: opt.key || "",
            open: open,
            close: close,
            cancel: cancel,
            confirm: confirm,
            isOpen: function () { return state.open; },
            getElement: function () { return modal; },
            getMaskElement: function () { return mask; },
            getContentElement: function () { return content; },
            getButtons: function () {
                var custom = {};
                customButtons.forEach(function (item) { custom[item.key] = item.element; });
                return { cancel: cancelButton, confirm: confirmButton, custom: custom };
            },
            getButton: function (key) {
                if (key === "cancel") { return cancelButton; }
                if (key === "confirm") { return confirmButton; }
                var custom = findCustomButton(key);
                return custom ? custom.element : null;
            },
            setTitle: function (value) { setTitle(value); return api; },
            setSubtitle: function (value) { setSubtitle(value); return api; },
            setContent: function (value, contentType) {
                clearContent(content);
                renderContent(content, value, contentType || opt.contentType, api);
                return api;
            },
            setSize: function (value) {
                state.sizes = normalizeSize(value, state.sizes);
                opt.size = value;
                applySize(modal, state.sizes);
                return api;
            },
            setCancelState: function (disabled, text) {
                opt.cancelDisabled = !!disabled;
                if (text !== undefined) { opt.cancelText = text; }
                updateButtons();
                return api;
            },
            setConfirmState: function (disabled, text, danger) {
                opt.confirmDisabled = !!disabled;
                if (text !== undefined) { opt.confirmText = text; }
                if (danger !== undefined) { opt.confirmDanger = !!danger; }
                updateButtons();
                return api;
            },
            setButtons: function (buttons) {
                opt.buttons = isArray(buttons) ? buttons.slice() : [];
                renderCustomButtons();
                return api;
            },
            addButton: function (button, position) {
                var item = assign({}, button || {});
                if (position) { item.position = position; }
                opt.buttons.push(item);
                renderCustomButtons();
                return api;
            },
            removeButton: function (key) {
                opt.buttons = (isArray(opt.buttons) ? opt.buttons : []).filter(function (button) {
                    return String(button && button.key) !== String(key);
                });
                renderCustomButtons();
                return api;
            },
            updateButton: function (key, next) {
                var changed = false;
                opt.buttons = (isArray(opt.buttons) ? opt.buttons : []).map(function (button) {
                    if (String(button && button.key) !== String(key)) { return button; }
                    changed = true;
                    return assign({}, button, next || {});
                });
                if (changed) { renderCustomButtons(); }
                return api;
            },
            setOptions: function (next) {
                assign(opt, next || {});
                setTitle(opt.title);
                setSubtitle(opt.subtitle);
                updateButtons();
                state.sizes = normalizeSize(opt, state.sizes);
                applySize(modal, state.sizes);
                renderCustomButtons();
                return api;
            },
            destroy: function () {
                if (state.destroyed) { return; }
                if (state.open) { close("destroy"); }
                state.destroyed = true;
                modal.removeEventListener("click", onModalClick);
                if (modal.parentNode) { modal.parentNode.removeChild(modal); }
                if (mask.parentNode) { mask.parentNode.removeChild(mask); }
                removeArray(instances, api);
            }
        };

        modal.addEventListener("click", onModalClick);
        setTitle(opt.title);
        setSubtitle(opt.subtitle);
        updateButtons();
        renderCustomButtons();
        renderContent(content, opt.content, opt.contentType, api);
        document.body.appendChild(mask);
        document.body.appendChild(modal);
        instances.push(api);
        return api;
    }

    function topOpenPanel() {
        var top = visibleModal();
        if (!top) { return null; }
        for (var i = instances.length - 1; i >= 0; i--) {
            if (instances[i].getElement() === top && instances[i].isOpen()) {
                return instances[i];
            }
        }
        return null;
    }

    function bindEvents() {
        if (eventsBound) { return; }
        eventsBound = true;
        document.addEventListener("keydown", function (event) {
            var panel = topOpenPanel();
            if (!panel) { return; }
            var isEscape = event.key === "Escape" || event.key === "Esc" || event.keyCode === 27;
            if (isEscape) {
                if (event.preventDefault) { event.preventDefault(); }
                if (event.stopImmediatePropagation) { event.stopImmediatePropagation(); }
                panel.cancel("esc", event);
                return;
            }
            if (event.key !== "Tab") { return; }
            var modal = panel.getElement();
            var focusable = focusableElements(modal);
            if (!focusable.length) {
                if (event.preventDefault) { event.preventDefault(); }
                try { modal.focus(); } catch (ignore) { }
                return;
            }
            var active = document.activeElement;
            var first = focusable[0];
            var last = focusable[focusable.length - 1];
            if (event.shiftKey && (active === first || active === modal)) {
                if (event.preventDefault) { event.preventDefault(); }
                try { last.focus(); } catch (ignoreFirst) { }
            } else if (!event.shiftKey && active === last) {
                if (event.preventDefault) { event.preventDefault(); }
                try { first.focus(); } catch (ignoreLast) { }
            }
        }, true);
    }

    function getInstance(target) {
        if (!target) { return null; }
        for (var i = instances.length - 1; i >= 0; i--) {
            var panel = instances[i];
            if (target === panel || target === panel.key || target === panel.id
                || target === panel.getElement()) {
                return panel;
            }
        }
        return null;
    }

    function closeAll(reason) {
        var list = instances.slice();
        for (var i = list.length - 1; i >= 0; i--) {
            if (list[i].isOpen()) { list[i].close(reason || "close-all"); }
        }
    }

    function open(options) {
        var panel = create(options);
        return panel ? panel.open() : null;
    }

    bindEvents();

    global.OaonesPanel = {
        SIZE_PRESETS: SIZE_PRESETS,
        DEFAULTS: DEFAULTS,
        esc: esc,
        normalizeSize: normalizeSize,
        create: create,
        open: open,
        getInstance: getInstance,
        closeAll: closeAll
    };
})(window, document);
