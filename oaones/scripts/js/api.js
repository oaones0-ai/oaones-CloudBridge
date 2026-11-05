/**
 * OAones 通用接口请求组件（Oaones）。
 *
 * 公共层只负责组织 URL、请求、统一错误提示和 401 处理，不包含任何项目默认地址。
 * 页面可以通过 window.OAONES_API_CONFIG / window.OAONES_API_URL 注入配置：
 *
 *   window.OAONES_API_CONFIG = {
 *       baseUrl: "/oaones/project/api.jsp",
 *       loginUrl: "/oaones/project/login"
 *   };
 *
 * 也兼容原有单页配置：
 *   window.OAONES_API_URL = "/oaones/project/api.jsp";
 */
(function (global) {
    "use strict";

    function config() {
        return global.OAONES_API_CONFIG || {};
    }

    function apiBase() {
        var cfg = config();
        return global.OAONES_API_URL || cfg.baseUrl || "";
    }

    function loginUrl() {
        return global.OAONES_LOGIN_URL || config().loginUrl || "";
    }

    function sysToast(msg) {
        if (global.OaonesToast) { global.OaonesToast.system(msg, 0); return; }
        if (global.console && global.console.log) { global.console.log(msg); }
    }

    function build(baseUrl, action, params) {
        var parts = ["action=" + encodeURIComponent(action)];
        params = params || {};
        Object.keys(params).forEach(function (key) {
            var value = params[key];
            if (value !== undefined && value !== null) {
                parts.push(encodeURIComponent(key) + "=" + encodeURIComponent(value));
            }
        });
        return baseUrl + (baseUrl.indexOf("?") >= 0 ? "&" : "?") + parts.join("&");
    }

    function apiAt(baseUrl, action, params, cb) {
        var base = String(baseUrl || apiBase() || "");
        if (!base) {
            sysToast("未配置接口地址");
            if (cb) { cb({ code: -1, msg: "", system: true }); }
            return;
        }
        var url = build(base, action, params);
        fetch(url, { credentials: "same-origin" })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("HTTP " + response.status + " " + url);
                }
                return response.json();
            })
            .then(function (json) {
                if (json && json.code === 401) {
                    sysToast("登录已失效，请重新登录");
                    var target = loginUrl();
                    if (target) { global.location.href = target; }
                    if (cb) { cb(json); }
                    return;
                }
                if (cb) { cb(json); }
            })
            .catch(function (error) {
                sysToast("请求异常: " + error.message);
                if (cb) { cb({ code: -1, msg: "", system: true }); }
            });
    }

    function api(action, params, cb) {
        return apiAt(apiBase(), action, params, cb);
    }

    function esc(value) {
        if (value === null || value === undefined) { return ""; }
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function fmtTime(value) {
        return value ? String(value) : "-";
    }

    global.Oaones = {
        api: api,
        apiAt: apiAt,
        apiBase: apiBase,
        buildUrl: build,
        esc: esc,
        fmtTime: fmtTime
    };
})(window);
