/**
 * OAones appsyn 项目上下文适配。
 *
 * 公共 api.js 不包含项目默认地址；本文件在 appsyn 页面加载后补齐：
 *   - 默认接口地址与登录地址；
 *   - 租户全局文案和租户锁定辅助方法；
 *   - 未引入 help.js 时的 OaonesTips 兜底。
 *
 * 必须在 /oaones/scripts/js/api.js 之后、业务脚本之前加载。
 */
(function (global) {
    "use strict";

    var existing = global.OAONES_API_CONFIG || {};
    var config = {};
    Object.keys(existing).forEach(function (key) { config[key] = existing[key]; });
    if (!config.baseUrl) {
        config.baseUrl = global.OAONES_API_URL || "/oaones/appsyn/WeCom/api.jsp";
    }
    if (!config.loginUrl) {
        config.loginUrl = "/oaones/appsyn/login";
    }
    global.OAONES_API_CONFIG = config;

    var CFG_ALL_LABEL = global.OAONES_CFG_ALL_LABEL || "全部企微租户";

    function esc(value) {
        return global.Oaones && global.Oaones.esc ? global.Oaones.esc(value) : String(value == null ? "" : value);
    }

    function lockedCfgId() {
        var value = global.OAONES_CFG_ID;
        return String(value === null || value === undefined ? "" : value).replace(/^\s+|\s+$/g, "");
    }

    function cfgLocked() {
        return lockedCfgId().length > 0;
    }

    function lockedCfgText(name) {
        var label = String(name === null || name === undefined ? "" : name);
        return label.length > 0 ? label : ("#" + lockedCfgId());
    }

    function cfgSelectRows(cfgs) {
        var html = '<option value="">' + esc(CFG_ALL_LABEL) + "</option>";
        (cfgs || []).forEach(function (cfg) {
            html += '<option value="' + esc(cfg.id) + '">' + esc(cfg.corp_name)
                + (cfg.sync_enable === "1" ? "" : "（停用）") + "</option>";
        });
        return html;
    }

    if (!global.OaonesTips) {
        global.OaonesTips = {
            isSysAdmin: function () { return false; },
            pick: function (adminText, userText) { return userText; }
        };
    }

    if (global.Oaones) {
        global.Oaones.cfgSelectRows = cfgSelectRows;
        global.Oaones.lockedCfgId = lockedCfgId;
        global.Oaones.cfgLocked = cfgLocked;
        global.Oaones.lockedCfgText = lockedCfgText;
    }
})(window);
