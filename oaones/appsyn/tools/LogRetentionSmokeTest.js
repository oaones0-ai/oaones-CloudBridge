#!/usr/bin/env node
/**
 * 日志保留功能静态自检：
 *   node ecology/oaones/appsyn/tools/LogRetentionSmokeTest.js
 */
"use strict";

var fs = require("fs");
var path = require("path");

var root = path.resolve(__dirname, "../../..");
var failures = [];

function read(relative) {
    return fs.readFileSync(path.join(root, relative), "utf8");
}

function check(ok, message) {
    if (!ok) { failures.push(message); }
}

var catalog = read("src/com/customization/oaones/appsyn/common/LogRetentionCatalog.java");
var expected = [];
var re = /public static final String [A-Z_]+ = "([a-z_]+)"/g;
var match;
while ((match = re.exec(catalog))) { expected.push(match[1]); }
check(expected.length === 8, "日志清单应为 8 项，实际 " + expected.length);

var dbs = ["Mysql", "SQLServer", "Oracle"];
dbs.forEach(function (db) {
    ["data", "sqlupgrade-sql"].forEach(function (base) {
        var relative = base + "/" + db + "/oaones/appsyn/oaones0019_log_retention.sql";
        var sql = read(relative);
        expected.forEach(function (code) {
            check(sql.indexOf("'" + code + "'") >= 0, relative + " 缺少日志项 " + code);
        });
        check(/keep_days[^,\n]*DEFAULT 0/i.test(sql)
                || /keep_days[^,\n]*DEFAULT 0 NOT NULL/i.test(sql),
            relative + " 的 keep_days 默认值不是 0");
    });
});

dbs.forEach(function (db) {
    var pure = read("data/" + db + "/oaones/appsyn/oaones0019_log_retention.sql");
    var docs = read("sqlupgrade-sql/" + db + "/oaones/appsyn/oaones0019_log_retention.sql");
    check(pure === docs, db + " 两份 0019 迁移脚本不一致");
});

var scheduler = read("src/com/customization/oaones/appsyn/job/OaonesLogRetentionScheduler.java");
check(/TICK_SECONDS\s*=\s*24\s*\*\s*60\s*\*\s*60/.test(scheduler), "自动清理不是每天执行");

var listener = read("WEB-INF/resin-web-post.xml");
check(listener.indexOf("OaonesLogRetentionSchedulerListener") >= 0, "E9 启动监听器未注册");

var controller = read("src/com/customization/oaones/appsyn/controller/LogRetentionController.java");
["logretention.list", "logretention.save", "logretention.saveAll", "logretention.clean"].forEach(function (action) {
    check(controller.indexOf(action) >= 0, "控制器缺少 action " + action);
});

var page = read("oaones/appsyn/systemSettings/systemSettings.jsp");
check(page.indexOf("/oaones/appsyn/js/systemSettings.js") >= 0, "系统设置页未引入页面脚本");
check(page.indexOf("应用到全部") >= 0 && page.indexOf("保存全部") >= 0, "缺少统一 / 批量设置入口");
check(page.indexOf("当前行数") >= 0 && page.indexOf('colspan="7"') >= 0,
    "系统设置页缺少当前行数列或表头列数未同步");

var pageScript = read("oaones/appsyn/js/systemSettings.js");
check(pageScript.indexOf("item.rowCount") >= 0 && pageScript.indexOf('colspan="7"') >= 0,
    "页面脚本未渲染当前行数");

var mapper = read("src/com/customization/oaones/appsyn/dao/LogRetentionMapper.java");
var mapperXml = read("WEB-INF/config/mapper/oaones/appsyn/LogRetentionMapper.xml");
var retentionService = read("src/com/customization/oaones/appsyn/service/impl/LogRetentionServiceImpl.java");
check(mapper.indexOf("Long countRows") >= 0
        && mapperXml.indexOf('id="countRows"') >= 0
        && mapperXml.indexOf("select count(*) from ${table}") >= 0,
    "日志表行数未通过 Mapper 直接查询数据库");
check(retentionService.indexOf("countRows(item)") >= 0,
    "日志保留列表未返回当前行数");

if (failures.length > 0) {
    failures.forEach(function (message) { console.error("FAIL: " + message); });
    process.exit(1);
}
console.log("LogRetentionSmokeTest OK");
