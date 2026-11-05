/**
 * OAones 文件上传组件（OaonesUpload）。
 *
 * 用法：
 *   OaonesUpload.validate(file, { extensions: /\.(png|jpe?g)$/i, maxBytes: 2 * 1024 * 1024 });
 *   OaonesUpload.post({
 *       url: "/upload.jsp",
 *       file: file,
 *       fieldName: "file",
 *       query: { kind: "logo" },
 *       extra: { scene: "login" }
 *   }).then(function (json) { ... });
 */
(function (global, document) {
    "use strict";

    function validate(file, options) {
        options = options || {};
        if (!file) {
            return { ok: false, message: options.requiredMessage || "请选择文件" };
        }
        var name = String(file.name || "");
        if (options.extensions && !options.extensions.test(name)) {
            return { ok: false, message: options.extensionMessage || "文件类型不支持" };
        }
        if (options.mimeTypes && options.mimeTypes.length > 0) {
            var allowed = false;
            for (var i = 0; i < options.mimeTypes.length; i++) {
                if (String(file.type || "").indexOf(options.mimeTypes[i]) === 0) { allowed = true; break; }
            }
            if (!allowed) {
                return { ok: false, message: options.mimeMessage || "文件类型不支持" };
            }
        }
        if (options.maxBytes && Number(file.size || 0) > Number(options.maxBytes)) {
            return { ok: false, message: options.maxMessage || "文件超过大小限制" };
        }
        if (options.minBytes && Number(file.size || 0) < Number(options.minBytes)) {
            return { ok: false, message: options.minMessage || "文件过小" };
        }
        return { ok: true, message: "" };
    }

    function appendQuery(url, query) {
        var parts = [];
        Object.keys(query || {}).forEach(function (key) {
            var value = query[key];
            if (value !== undefined && value !== null) {
                parts.push(encodeURIComponent(key) + "=" + encodeURIComponent(value));
            }
        });
        if (!parts.length) { return url; }
        return url + (url.indexOf("?") >= 0 ? "&" : "?") + parts.join("&");
    }

    function post(options) {
        options = options || {};
        if (!options.file) {
            return Promise.reject(new Error(options.requiredMessage || "请选择文件"));
        }
        var form = new FormData();
        form.append(options.fieldName || "file", options.file, options.file.name || "file");
        Object.keys(options.extra || {}).forEach(function (key) {
            var value = options.extra[key];
            if (value !== undefined && value !== null) { form.append(key, value); }
        });
        var url = appendQuery(options.url, options.query);
        return fetch(url, {
            method: options.method || "POST",
            body: form,
            credentials: options.credentials || "same-origin",
            headers: options.headers || {}
        }).then(function (response) {
            if (!response.ok) { throw new Error("HTTP " + response.status); }
            return response.json();
        });
    }

    function pick(input, onFile) {
        var el = typeof input === "string" ? document.getElementById(input) : input;
        if (!el || typeof onFile !== "function") { return; }
        el.addEventListener("change", function () {
            var file = this.files && this.files.length ? this.files[0] : null;
            if (file) { onFile(file); }
            this.value = "";
        });
    }

    global.OaonesUpload = {
        validate: validate,
        post: post,
        pick: pick
    };
})(window, document);
