/**
 * OAones appsyn 功能开关配置。
 * 必须在公共 /oaones/scripts/js/featureGate.js 之前加载。
 */
(function (global) {
    "use strict";
    global.OAONES_FEATURE_GATE = {
        apiUrl: "/oaones/appsyn/systemSettings/featureApi.jsp?action=features.read",
        moduleFeatures: [
            { token: "/oaones/appsyn/syncCenter/", code: "person.sync.schedule" },
            { token: "/oaones/appsyn/appBind/", code: "app.bind" },
            { token: "/oaones/appsyn/attSync/", code: "att.sync" },
            { token: "/oaones/appsyn/meetingSync/", code: "mtg.sync" },
            { token: "/oaones/appsyn/scheduleSync/", code: "schedule.sync" }
        ]
    };
})(window);
