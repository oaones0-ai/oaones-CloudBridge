/**
 * OAones 页签组件（OaonesTabs）。
 *
 * 用法：
 *   var tabs = OaonesTabs.bind({
 *       buttons: "#msgTabs button",
 *       panels: "[data-tab-panel]",
 *       initial: "config",
 *       onChange: function (tab) { ... }
 *   });
 *   tabs.switchTo("log");
 *
 * 约定：按钮使用 data-tab，面板使用 data-tab-panel；激活态统一为 active。
 */
(function (global, document) {
    "use strict";

    function list(selector, root) {
        if (!selector) { return []; }
        if (typeof selector === "string") { return (root || document).querySelectorAll(selector); }
        if (selector.length !== undefined) { return selector; }
        return [selector];
    }

    function bind(options) {
        options = options || {};
        var buttons = list(options.buttons, options.root);
        var buttonAttribute = options.buttonAttribute || "data-tab";
        var panelAttribute = options.panelAttribute || "data-tab-panel";
        var panels = list(options.panels || ("[" + panelAttribute + "]"), options.root);
        var activeKey = "";

        function keyOf(button) {
            return button ? String(button.getAttribute(buttonAttribute) || "") : "";
        }

        function panelOf(key) {
            for (var i = 0; i < panels.length; i++) {
                if (panels[i].getAttribute(panelAttribute) === key) { return panels[i]; }
            }
            return null;
        }

        function switchTo(key, silent) {
            if (!key || !panelOf(key)) { return false; }
            activeKey = key;
            for (var i = 0; i < buttons.length; i++) {
                var active = keyOf(buttons[i]) === key;
                buttons[i].classList.toggle(options.activeClass || "active", active);
                buttons[i].setAttribute("aria-selected", active ? "true" : "false");
            }
            for (var j = 0; j < panels.length; j++) {
                var shown = panels[j].getAttribute(panelAttribute) === key;
                panels[j].classList.toggle(options.activeClass || "active", shown);
                if (options.toggleDisplay) { panels[j].style.display = shown ? "" : "none"; }
                panels[j].setAttribute("aria-hidden", shown ? "false" : "true");
            }
            if (!silent && typeof options.onChange === "function") {
                options.onChange(key, panelOf(key));
            }
            return true;
        }

        function clickHandler(e) {
            var target = e.target;
            if (!target || !target.closest) { return; }
            var button = target.closest("[" + buttonAttribute + "]");
            if (!button) { return; }
            var href = button.getAttribute ? String(button.getAttribute("href") || "") : "";
            if (href.charAt(0) === "#" && e.preventDefault) { e.preventDefault(); }
            switchTo(keyOf(button));
        }

        if (options.container) {
            var container = typeof options.container === "string"
                ? document.querySelector(options.container) : options.container;
            if (container) { container.addEventListener("click", clickHandler); }
        } else {
            for (var i = 0; i < buttons.length; i++) {
                buttons[i].addEventListener("click", clickHandler);
            }
        }

        var initial = options.initial || "";
        if (!initial) {
            for (var j = 0; j < buttons.length; j++) {
                if (buttons[j].classList.contains("active")) { initial = keyOf(buttons[j]); break; }
            }
        }
        if (!initial && buttons.length) { initial = keyOf(buttons[0]); }
        if (initial) { switchTo(initial, true); }

        return {
            switchTo: switchTo,
            active: function () { return activeKey; },
            buttons: buttons,
            panels: panels
        };
    }

    global.OaonesTabs = { bind: bind };
})(window, document);
