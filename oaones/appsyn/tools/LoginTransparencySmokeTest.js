/**
 * 「登录框透明度」自检（node oaones/appsyn/tools/LoginTransparencySmokeTest.js）。
 *
 * 只做静态一致性核对（不需要浏览器 / 应用服务器），覆盖：
 *   1. 设置页：滑块 / 数字 / 恢复默认 / 实时预览控件，保存时带 login_box_opacity；
 *   2. 设置页内联脚本能通过语法检查，预览不触发保存接口；
 *   3. 登录页：读取透明度并换算成 CSS alpha，弹框模式不受影响；
 *   4. CSS：只改登录框白色底色的 alpha，内容不变淡；
 *   5. 后端：实体 / Mapper / 保存校验 / 0~100 范围；
 *   6. 数据库：三库 × 两目录的 oaones0010.sql，历史 0001 不回写。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");
    var vm = require("vm");

    var root = path.resolve(__dirname, "..");     // oaones/
    var eco = path.resolve(root, "../..");           // ecology/
    var failed = 0;

    function read(file) {
        if (!fs.existsSync(file)) {
            console.error("缺少文件：" + file);
            process.exit(1);
        }
        return fs.readFileSync(file, "utf8");
    }

    function check(name, condition, detail) {
        if (condition) {
            console.log("  ✓ " + name);
            return;
        }
        failed++;
        console.log("  ✗ " + name + (detail ? "：" + detail : ""));
    }

    function stripSql(sql) {
        return sql.replace(/\/\*[\s\S]*?\*\//g, "")
            .split("\n")
            .map(function (line) { return line.replace(/--.*$/, "").replace(/\s+$/, "").trim(); })
            .filter(function (line) { return line.length > 0; })
            .join("\n");
    }

    var config = read(path.join(root, "loginConfig", "config.jsp"));
    var login = read(path.join(root, "login", "login.jsp"));
    var loginCss = read(path.join(root, "css", "login.css"));
    var impl = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "impl", "wecom", "LoginConfigServiceImpl.java"));
    var entity = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "entity", "wecom", "LoginConfig.java"));
    var mapper = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", "appsyn", "LoginConfigMapper.xml"));

    console.log("【1】设置页：控件与保存参数");
    [
        'id="cfgLoginBoxTransparency"', 'id="cfgLoginBoxTransparencyValue"',
        'id="cfgLoginBoxTransparencyResetBtn"', 'id="loginPreviewFrame"',
        'id="loginBoxStage"', 'id="loginBoxPreview"'
    ].forEach(function (needle) {
        check("控件 " + needle, config.indexOf(needle) >= 0);
    });
    check("保存参数带 login_box_opacity",
        config.indexOf("login_box_opacity: String(cardAlpha.value)") >= 0);
    check("滑块 / 数字双向联动",
        config.indexOf('byId("cfgLoginBoxTransparency").addEventListener("input"') >= 0
        && config.indexOf('byId("cfgLoginBoxTransparencyValue").addEventListener("input"') >= 0);
    check("透明度只更新共用的整页预览、不调用保存接口",
        /function renderCardAlphaPreview[\s\S]*?renderUnifiedPreview\(\)[\s\S]*?\n    \}/.test(config)
        && !/function renderCardAlphaPreview[\s\S]{0,1200}Oaones\.api/.test(config));
    check("不再保留单独的透明度小预览",
        config.indexOf("card-alpha-stage") < 0 && config.indexOf("cfgCardAlphaStage") < 0);

    console.log("【2】设置页内联脚本语法");
    var scriptMatch = config.match(/<script>\s*"use strict";([\s\S]*?)<\/script>/);
    check("找到设置页主脚本", !!scriptMatch);
    if (scriptMatch) {
        try {
            new vm.Script(scriptMatch[1].replace(/<%=[\s\S]*?%>/g, "0"));
            check("内联脚本可编译", true);
        } catch (e) {
            check("内联脚本可编译", false, e.message);
        }
    }

    console.log("【3】登录页：读取透明度并换算 alpha");
    check("读取 login_box_opacity", login.indexOf('getIntValue("login_box_opacity")') >= 0);
    check("限制在 0~100", login.indexOf("OaonesConst.LOGIN_BOX_OPACITY_MIN") >= 0
        && login.indexOf("OaonesConst.LOGIN_BOX_OPACITY_MAX") >= 0);
    check("写 --oa-login-card-alpha", login.indexOf('"--oa-login-card-alpha:"') >= 0);
    check("alpha = 1 - 透明度 / 100", login.indexOf("1.0 - (oaonesBoxOpacity / 100.0)") >= 0);
    check("弹框模式保持不透明", login.indexOf("oaonesPopup ? 0 : loginBoxValue(") >= 0);

    console.log("【4】CSS：只改登录框底色 alpha");
    check("登录框底色使用 --oa-login-card-alpha",
        loginCss.indexOf("background-color:rgba(255,255,255,var(--oa-login-card-alpha,1))") >= 0);
    check("没有给整张卡片设置 opacity",
        !/\.login-card\s*\{[^}]*\bopacity\s*:/.test(loginCss));

    console.log("【5】后端：实体 / Mapper / 保存校验");
    check("实体字段 loginBoxOpacity",
        entity.indexOf("private Integer loginBoxOpacity;") >= 0);
    check("Mapper 查询含 login_box_opacity",
        mapper.indexOf("login_box_opacity") >= 0);
    check("Mapper 更新含 login_box_opacity",
        mapper.indexOf("login_box_opacity = #{e.loginBoxOpacity}") >= 0);
    check("保存读 login_box_opacity",
        impl.indexOf('containsKey("login_box_opacity")') >= 0);
    check("范围常量 0~100",
        impl.indexOf("MIN_BOX_OPACITY = OaonesConst.LOGIN_BOX_OPACITY_MIN") >= 0
        && impl.indexOf("MAX_BOX_OPACITY = OaonesConst.LOGIN_BOX_OPACITY_MAX") >= 0);
    check("历史 NULL 按 0 返回",
        impl.indexOf("row.getLoginBoxOpacity() == null") >= 0);

    console.log("【6】数据库：三库 × 两目录的 oaones0010.sql");
    ["Mysql", "SQLServer", "Oracle"].forEach(function (db) {
        var plainPath = path.join(eco, "data", db, "oaones", "appsyn", "oaones0010.sql");
        var docPath = path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0010.sql");
        check(db + "：两目录 0010 都在", fs.existsSync(plainPath) && fs.existsSync(docPath));
        var plain = read(plainPath);
        var doc = read(docPath);
        check(db + "：0010 含 login_box_opacity",
            plain.indexOf("login_box_opacity") >= 0 && doc.indexOf("login_box_opacity") >= 0);
        check(db + "：0010 两目录语句逐条一致", stripSql(plain) === stripSql(doc));
        var create = read(path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0001.sql"));
        check(db + "：0001 保持历史基线（不含 login_box_opacity）",
            create.indexOf("login_box_opacity") < 0);
    });

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
    }
    console.log("\n全部通过 ✓");
})();
