#!/usr/bin/env node
/**
 * OAones 表名 appsyn 迁移静态自检：
 *   node ecology/oaones/appsyn/tools/OaonesAppsynMigrationSmokeTest.js
 */
"use strict";

var fs = require("fs");
var path = require("path");

var root = path.resolve(__dirname, "../../..");
var failures = [];

function check(ok, message) {
    if (!ok) {
        failures.push(message);
    }
}

function read(relative) {
    return fs.readFileSync(path.join(root, relative), "utf8");
}

function matches(text, regex) {
    var rows = [];
    var match;
    while ((match = regex.exec(text))) {
        rows.push(match);
    }
    return rows;
}

function walk(dir, suffix) {
    var rows = [];
    fs.readdirSync(dir, { withFileTypes: true }).forEach(function (entry) {
        var full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            rows = rows.concat(walk(full, suffix));
        } else if (!suffix || entry.name.slice(-suffix.length) === suffix) {
            rows.push(full);
        }
    });
    return rows;
}

var mapperRoot = path.join(root, "WEB-INF", "config", "mapper", "oaones");
var mapperEntries = fs.readdirSync(mapperRoot, { withFileTypes: true });
check(mapperEntries.length === 1 && mapperEntries[0].isDirectory() && mapperEntries[0].name === "appsyn",
    "mapper/oaones 根目录下只能保留 appsyn 子目录");

var mappers = walk(path.join(mapperRoot, "appsyn"), ".xml");
check(mappers.length === 77, "Mapper XML 数量应为 77，实际 " + mappers.length);
check(mappers.every(function (file) {
    return file.indexOf(path.join("mapper", "oaones", "appsyn")) >= 0;
}), "仍有 Mapper XML 不在 mapper/oaones/appsyn 下");

var properties = read("WEB-INF/prop/oaones.appsyn.properties");
check(properties.indexOf("oaones_qywx_config_table=uf_oaones_appsyn_qywx_config") >= 0,
    "properties 的租户配置表名未切换为 appsyn");
check(!/=\s*uf_oaones_(?!appsyn_)/m.test(properties),
    "properties 仍存在旧前缀表名");

var props = read("src/com/customization/oaones/appsyn/common/OaonesProps.java");
var msgProps = read("src/com/customization/oaones/appsyn/msg/common/MsgProps.java");
check(props.indexOf('"uf_oaones_appsyn_qywx_config"') >= 0
        && props.indexOf('"uf_oaones_appsyn_personal_wx_config"') >= 0,
    "OaonesProps 默认表名未切换为 appsyn");
check(msgProps.indexOf('"uf_oaones_appsyn_msg_config"') >= 0
        && msgProps.indexOf('"uf_oaones_appsyn_msg_task"') >= 0,
    "MsgProps 默认表名未切换为 appsyn");

var currentText = [];
var javaFiles = walk(path.join(root, "src", "com", "customization", "oaones", "appsyn"), ".java");
var webFiles = walk(path.join(root, "oaones", "appsyn"), null).filter(function (file) {
    var relative = path.relative(root, file);
    return relative.indexOf(path.join("oaones", "appsyn", "tools")) !== 0
        && relative.indexOf(path.join("oaones", "appsyn", "img")) !== 0
        && relative !== path.join("oaones", "appsyn", "README.md")
        && relative !== path.join("oaones", "appsyn", "skill.md")
        && /\.(jsp|js|md|html|css)$/.test(file);
});
currentText = currentText.concat(javaFiles, mappers, webFiles);
var stale = currentText.filter(function (file) {
    return /\buf_oaones_(?!appsyn_)/.test(fs.readFileSync(file, "utf8"));
});
check(stale.length === 0,
    "当前源码 / Mapper / 页面 / 文档仍存在旧表名：" + stale.map(function (file) {
        return path.relative(root, file);
    }).join(", "));

var dialects = ["Mysql", "SQLServer", "Oracle"];
var migrationName = "oaones0021_appsyn_table_rename.sql";
dialects.forEach(function (db) {
    var plainPath = "data/" + db + "/oaones/appsyn/" + migrationName;
    var docPath = "sqlupgrade-sql/" + db + "/oaones/appsyn/" + migrationName;
    check(fs.existsSync(path.join(root, plainPath)), plainPath + " 不存在");
    check(fs.existsSync(path.join(root, docPath)), docPath + " 不存在");
    if (fs.existsSync(path.join(root, plainPath)) && fs.existsSync(path.join(root, docPath))) {
        check(read(plainPath) === read(docPath), db + " 两份 0021 迁移脚本不一致");
    }
});

var mysql = read("data/Mysql/oaones/appsyn/" + migrationName);
var sqlserver = read("data/SQLServer/oaones/appsyn/" + migrationName);
var oracle = read("data/Oracle/oaones/appsyn/" + migrationName);
var mysqlPairs = matches(mysql,
    /^ALTER TABLE `([^`]+)` RENAME TO `([^`]+)`;$/gm).map(function (m) {
        return [m[1], m[2]];
    });
var sqlserverPairs = matches(sqlserver,
    /^EXEC sys\.sp_rename N'dbo\.([^']+)', N'([^']+)', N'OBJECT';$/gm).map(function (m) {
        return [m[1], m[2]];
    });
var oraclePairs = matches(oracle,
    /^ALTER TABLE ([A-Z0-9_]+) RENAME TO ([A-Z0-9_]+);$/gm).map(function (m) {
        return [m[1].toLowerCase(), m[2].toLowerCase()];
    });
check(mysqlPairs.length === 37, "MySQL 应逐表执行 37 条 ALTER，实际 " + mysqlPairs.length);
check(sqlserverPairs.length === 37, "SQLServer 应逐表执行 37 条 sp_rename，实际 " + sqlserverPairs.length);
check(oraclePairs.length === 37, "Oracle 应逐表执行 37 条 ALTER，实际 " + oraclePairs.length);
[mysql, sqlserver, oracle].forEach(function (sql, index) {
    check(!/CURSOR|PREPARE|information_schema\.tables|SUBSTRING|LIKE\s+['"]|FOR\s+\w+\s+IN/i.test(sql),
        dialects[index] + " 迁移仍包含前缀扫描、游标或拼接式批量重命名");
});
check(mysql.indexOf("RENAME TABLE") < 0, "MySQL 仍使用批量 RENAME TABLE");
[mysqlPairs, sqlserverPairs, oraclePairs].forEach(function (pairs, index) {
    var actual = {};
    pairs.forEach(function (pair) {
        actual[pair[0]] = pair[1];
    });
    check(Object.keys(actual).length === 37, dialects[index] + " 逐表迁移存在重复源表");
    check(Object.keys(actual).every(function (oldName) {
        return actual[oldName] === oldName.replace("uf_oaones_", "uf_oaones_appsyn_");
    }), dialects[index] + " 逐表迁移名称映射不正确");
});
[mysql, sqlserver, oracle].forEach(function (sql, index) {
    check(sql.indexOf("workflow_bill") < 0,
        dialects[index] + " 不应修改 E9 workflow_bill");
});

var historical = read("data/Mysql/oaones/appsyn/oaones0001.sql");
check(historical.indexOf("CREATE TABLE IF NOT EXISTS uf_oaones_qywx_config") >= 0,
    "历史 0001 的建表语句被改动");

var historicalTables = {};
["data", "sqlupgrade-sql"].forEach(function (base) {
    dialects.forEach(function (db) {
        walk(path.join(root, base, db, "oaones", "appsyn"), ".sql").forEach(function (file) {
            var sql = fs.readFileSync(file, "utf8");
            var tableRe = /CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+(?:dbo\.)?(uf_oaones_[A-Za-z0-9_]+)/ig;
            var match;
            while ((match = tableRe.exec(sql))) {
                historicalTables[match[1]] = true;
            }
        });
    });
});
var historicalNames = Object.keys(historicalTables).sort();
check(historicalNames.length === 37,
    "历史脚本中的 OAones 表应为 37 张，实际 " + historicalNames.length);
check(historicalNames.every(function (name) {
    return /^uf_oaones_(?!appsyn_)[a-z0-9_]+$/.test(name);
}), "历史脚本中的表名不符合统一旧前缀");
[mysqlPairs, sqlserverPairs, oraclePairs].forEach(function (pairs, index) {
    var actual = {};
    pairs.forEach(function (pair) {
        actual[pair[0]] = pair[1];
    });
    var missing = historicalNames.filter(function (name) {
        return actual[name] !== name.replace("uf_oaones_", "uf_oaones_appsyn_");
    });
    check(missing.length === 0,
        dialects[index] + " 迁移没有逐表覆盖这些历史表：" + missing.join(", "));
});

var syno = fs.readFileSync(path.join(root, "..", "Synoaones.sh"), "utf8");
var sync47 = fs.readFileSync(path.join(root, "..", "47SyncEc.sh"), "utf8");
check(syno.indexOf("WEB-INF/config/mapper/oaones/appsyn/") >= 0
        && syno.indexOf('bak_sync_dir "WEB-INF/config/mapper/oaones/appsyn"') >= 0,
    "Synoaones.sh 未同步新的 Mapper 子目录");
check(sync47.indexOf("WEB-INF/config/mapper/oaones/appsyn") >= 0,
    "47SyncEc.sh 未校验新的 Mapper 子目录");

if (failures.length > 0) {
    failures.forEach(function (message) {
        console.error("FAIL: " + message);
    });
    process.exit(1);
}
console.log("OaonesAppsynMigrationSmokeTest OK");
