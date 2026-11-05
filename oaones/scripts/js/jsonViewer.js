/**
 * OAones JSON / 报文查看组件（OaonesJsonViewer）。
 *
 * 用法：
 *   OaonesJsonViewer.format(text);                    // 返回格式化后的字符串
 *   OaonesJsonViewer.render(element, text);           // 写入元素 textContent
 *   OaonesJsonViewer.render(element, obj, { emptyText: "（无）" });
 */
(function (global) {
    "use strict";

    function format(value) {
        if (value === null || value === undefined) { return ""; }
        if (typeof value === "object") {
            try { return JSON.stringify(value, null, 2); } catch (ignore) { return String(value); }
        }
        var text = String(value);
        var trimmed = text.replace(/^\s+|\s+$/g, "");
        if (!trimmed) { return ""; }
        if (trimmed.charAt(0) === "{" || trimmed.charAt(0) === "[") {
            try { return JSON.stringify(JSON.parse(trimmed), null, 2); } catch (ignore) { return text; }
        }
        return text;
    }

    function render(element, value, options) {
        if (!element) { return ""; }
        options = options || {};
        var text = format(value);
        if (!text) { text = options.emptyText || "（无）"; }
        element.textContent = text;
        return text;
    }

    global.OaonesJsonViewer = { format: format, render: render };
})(window);
