/**
 * OAones URL 查询参数组件（OaonesQuery）。
 *
 * 提供只处理当前页面查询串的轻量方法，不修改浏览器历史，除非明确调用 set(..., { pushState: true })。
 */
(function (global) {
    "use strict";

    function params(search) {
        return new URLSearchParams(search === undefined ? global.location.search : search);
    }

    function get(name, defaultValue) {
        var value = params().get(name);
        return value === null ? (defaultValue === undefined ? "" : defaultValue) : value;
    }

    function getAll(name) {
        return params().getAll(name);
    }

    function has(name) {
        return params().has(name);
    }

    function object() {
        var out = {};
        params().forEach(function (value, key) {
            if (Object.prototype.hasOwnProperty.call(out, key)) {
                out[key] = Array.isArray(out[key]) ? out[key].concat(value) : [out[key], value];
            } else {
                out[key] = value;
            }
        });
        return out;
    }

    function set(name, value, options) {
        options = options || {};
        var current = params();
        if (value === null || value === undefined || value === "") {
            current.delete(name);
        } else {
            current.set(name, String(value));
        }
        var query = current.toString();
        var url = global.location.pathname + (query ? "?" + query : "") + global.location.hash;
        if (options.pushState && global.history && global.history.pushState) {
            global.history.pushState(options.state || null, "", url);
        }
        return url;
    }

    function remove(name, options) {
        return set(name, "", options);
    }

    function update(values, options) {
        var current = params();
        Object.keys(values || {}).forEach(function (key) {
            var value = values[key];
            if (value === null || value === undefined || value === "") { current.delete(key); }
            else { current.set(key, String(value)); }
        });
        var query = current.toString();
        var url = global.location.pathname + (query ? "?" + query : "") + global.location.hash;
        if (options && options.pushState && global.history && global.history.pushState) {
            global.history.pushState(options.state || null, "", url);
        }
        return url;
    }

    global.OaonesQuery = {
        get: get,
        getAll: getAll,
        has: has,
        object: object,
        set: set,
        remove: remove,
        update: update
    };
})(window);
