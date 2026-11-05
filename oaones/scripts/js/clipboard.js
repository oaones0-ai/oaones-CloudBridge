/**
 * OAones 剪贴板组件（OaonesClipboard）。
 *
 * 用法：
 *   OaonesClipboard.copy(text);
 *   OaonesClipboard.copy(text, { successMessage: "已复制", errorMessage: "复制失败" });
 *   OaonesClipboard.copy(text, { onSuccess: fn, onError: fn });
 *
 * 优先使用 navigator.clipboard；HTTP 非安全上下文自动退化为隐藏 textarea + execCommand。
 */
(function (global, document, navigator) {
    "use strict";

    function toast(message, mode) {
        if (!message) { return; }
        if (global.OaonesToast) {
            global.OaonesToast.show(message, { mode: mode || 0 });
        } else if (global.console && global.console.log) {
            global.console.log(message);
        }
    }

    function fallback(text) {
        var textarea = document.createElement("textarea");
        textarea.value = text;
        textarea.setAttribute("readonly", "readonly");
        textarea.style.position = "fixed";
        textarea.style.left = "-9999px";
        textarea.style.top = "0";
        document.body.appendChild(textarea);
        textarea.select();
        var ok = false;
        try {
            ok = document.execCommand && document.execCommand("copy");
        } catch (ignore) {
            ok = false;
        }
        document.body.removeChild(textarea);
        return !!ok;
    }

    function copy(text, options) {
        options = options || {};
        var payload = String(text == null ? "" : text);
        if (!payload) {
            toast(options.emptyMessage || "没有可复制的内容", 1);
            return Promise.resolve(false);
        }
        var successMessage = options.successMessage === undefined ? "已复制" : options.successMessage;
        var errorMessage = options.errorMessage || "复制失败，请手动选中内容后复制";

        function success() {
            toast(successMessage, 0);
            if (typeof options.onSuccess === "function") { options.onSuccess(); }
            return true;
        }

        function failed() {
            toast(errorMessage, 1);
            if (typeof options.onError === "function") { options.onError(); }
            return false;
        }

        if (navigator.clipboard && navigator.clipboard.writeText) {
            return navigator.clipboard.writeText(payload).then(success, function () {
                return fallback(payload) ? success() : failed();
            });
        }
        return Promise.resolve(fallback(payload) ? success() : failed());
    }

    global.OaonesClipboard = { copy: copy };
})(window, document, navigator);
