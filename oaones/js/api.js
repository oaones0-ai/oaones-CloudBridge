(function () {
    "use strict";
    /**
     * 接口入口地址（**每次调用时读**，不再只在加载时读一次）：
     * 默认走企微连接器的 /oaones/WeCom/api.jsp；钉钉页面在引本脚本前设置
     * {@code window.OAONES_API_URL = "/oaones/DingTalk/api.jsp"}；
     * 「消息设置」这类跨平台页面还可以用 {@code Oaones.apiAt(base, ...)} 指定某次调用走哪个入口。
     */
    function apiBase() {
        return window.OAONES_API_URL || "/oaones/WeCom/api.jsp";
    }
    /** 租户下拉的“全部租户”文案（钉钉页面用 window.OAONES_CFG_ALL_LABEL 覆盖成“全部钉钉租户”）。 */
    var CFG_ALL_LABEL = window.OAONES_CFG_ALL_LABEL || "全部企微租户";
    /** 系统错误提示（模式 3）：接口/网络层异常统一走本系统弹出框，禁用原生 alert */
    function sysToast(msg) {
        if (window.OaonesToast) { window.OaonesToast.system(msg, 0); return; }
        if (window.console && console.log) { console.log(msg); }
    }
    function build(baseUrl, action, params) {
        var parts = ["action=" + encodeURIComponent(action)];
        params = params || {};
        Object.keys(params).forEach(function (k) {
            var v = params[k];
            if (v !== undefined && v !== null) {
                parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(v));
            }
        });
        return baseUrl + "?" + parts.join("&");
    }
    function apiAt(baseUrl, action, params, cb) {
        var url = build(baseUrl || apiBase(), action, params);
        fetch(url, { credentials: "same-origin" })
            .then(function (r) {
                if (!r.ok) {
                    throw new Error("HTTP " + r.status + " " + url);
                }
                return r.json();
            })
            .then(function (json) {
                if (json.code === 401) {
                    sysToast("登录已失效，请重新登录");
                    // 统一回 OAones 登录页；登录成功后按 login.landing.url 落桌面启动台
                    location.href = "/oaones/login";
                    return;
                }
                cb && cb(json);
            })
            .catch(function (e) {
                // 系统级异常（网络/HTTP/解析）统一用模式 3 弹出框提示；
                // 回给页面空 msg，避免页面再拼一遍同样的错误信息重复弹出。
                sysToast("请求异常: " + e.message);
                cb && cb({ code: -1, msg: "", system: true });
            });
    }
    function api(action, params, cb) {
        return apiAt(apiBase(), action, params, cb);
    }
    function esc(v) {
        if (v === null || v === undefined) { return ""; }
        return String(v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function fmtTime(v) { return v ? String(v) : "-"; }
    /**
     * 租户上下文（锁定租户）：从“企微租户”列表点开某个租户后，租户二级菜单各功能页的地址都带 cfg_id，
     * 页壳把它注入 window.OAONES_CFG_ID；此时该页只能操作这个租户、不能切换租户
     * （页面把“企业微信租户”选择框换成只读展示，见 css/style.css 的 .cfg-locked）。
     */
    function lockedCfgId() {
        var v = window.OAONES_CFG_ID;
        return String(v === null || v === undefined ? "" : v).trim();
    }
    /** 当前页是否处于租户上下文（带 cfg_id 进入：租户锁定） */
    function cfgLocked() { return lockedCfgId().length > 0; }
    /** 锁定租户的只读展示文案：取不到名称时退化为“#租户ID” */
    function lockedCfgText(name) {
        var label = String(name === null || name === undefined ? "" : name);
        return (label.length > 0 ? label : ("#" + lockedCfgId()));
    }
    function cfgSelectRows(cfgs) {
        var s = '<option value="">' + CFG_ALL_LABEL + "</option>";
        (cfgs || []).forEach(function (c) {
            s += '<option value="' + esc(c.id) + '">' + esc(c.corp_name) + (c.sync_enable === "1" ? "" : "（停用）") + "</option>";
        });
        return s;
    }
    /**
     * 兜底：正常页面都会引入 js/help.js（提示语按登录人角色显示，见 OaonesTips）。
     * 万一某页漏引，这里补一份“非系统管理员”口径，避免提示语取值时报错。
     */
    if (!window.OaonesTips) {
        window.OaonesTips = {
            isSysAdmin: function () { return false; },
            pick: function (adminText, userText) { return userText; }
        };
    }
    window.Oaones = {
        api: api, apiAt: apiAt, apiBase: apiBase, esc: esc, fmtTime: fmtTime, cfgSelectRows: cfgSelectRows,
        lockedCfgId: lockedCfgId, cfgLocked: cfgLocked, lockedCfgText: lockedCfgText
    };
})();
