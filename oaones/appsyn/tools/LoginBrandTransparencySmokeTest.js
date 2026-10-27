/**
 * 「登录页左侧文字背景透明度」自检
 * （node oaones/appsyn/tools/LoginBrandTransparencySmokeTest.js）。
 *
 * 只做静态一致性核对（不需要浏览器 / 应用服务器），覆盖：
 *   1. 设置页：控件、顺序、实时预览与保存参数；
 *   2. 设置页 / 预览页内联脚本能通过语法检查；
 *   3. 登录页：透明度换算为 CSS alpha，默认 100 = 无底板，弹框模式不受影响；
 *   4. CSS：只改左侧文字区白色底板，不使用整块 opacity，文字不变淡；
 *   5. 后端：实体 / Mapper / 保存校验 / get 与 publicSettings；
 *   6. 数据库：三库 × 两目录的 oaones0012.sql，历史 0001 不回写。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");
    var vm = require("vm");

    var root = path.resolve(__dirname, "..");
    var eco = path.resolve(root, "../..");
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
            .map(function (line) {
                return line.replace(/--.*$/, "").replace(/\s+$/, "").trim();
            })
            .filter(function (line) { return line.length > 0; })
            .join("\n");
    }

    var config = read(path.join(root, "loginConfig", "config.jsp"));
    var preview = read(path.join(root, "loginConfig", "preview.jsp"));
    var login = read(path.join(root, "login", "login.jsp"));
    var loginCss = read(path.join(root, "css", "login.css"));
    var impl = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "impl", "wecom", "LoginConfigServiceImpl.java"));
    var entity = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "entity", "wecom", "LoginConfig.java"));
    var consts = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "common", "OaonesConst.java"));
    var mapper = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", "appsyn", "LoginConfigMapper.xml"));

    console.log("【1】设置页：控件、顺序与保存参数");
    [
        'id="cfgBrandTransparency"', 'id="cfgBrandTransparencyValue"',
        'id="cfgBrandTransparencyResetBtn"', 'id="cfgBrandTransparencyResult"'
    ].forEach(function (needle) {
        check("控件 " + needle, config.indexOf(needle) >= 0);
    });
    // 说明区也提到同样的标题；取最后一次出现的表格字段行。
    var textRow = config.lastIndexOf("<b>登录页左侧文字</b>");
    var alphaRow = config.lastIndexOf("<b>左侧文字背景透明度</b>");
    var layoutRow = config.lastIndexOf("<b>文字说明位置与尺寸</b>");
    check("顺序：登录页左侧文字 → 背景透明度 → 位置与尺寸",
        textRow >= 0 && alphaRow > textRow && layoutRow > alphaRow);
    check("滑块 / 数字双向联动",
        config.indexOf('byId("cfgBrandTransparency").addEventListener("input"') >= 0
        && config.indexOf('byId("cfgBrandTransparencyValue").addEventListener("input"') >= 0);
    check("保存参数带 brand_opacity",
        config.indexOf("brand_opacity: String(brandAlpha.value)") >= 0);
    check("透明度变化只更新共用预览、不调用保存接口",
        /function renderBrandAlphaPreview[\s\S]*?renderUnifiedPreview\(\)[\s\S]*?\n    \}/.test(config)
        && !/function renderBrandAlphaPreview[\s\S]{0,1400}Oaones\.api/.test(config));
    check("非维护人禁用透明度控件",
        config.indexOf('byId("cfgBrandTransparency").disabled = true') >= 0
        && config.indexOf("无维护权限，不展示当前左侧文字背景透明度") >= 0);

    console.log("【2】设置页 / 预览页内联脚本语法");
    [
        ["config.jsp", config],
        ["preview.jsp", preview]
    ].forEach(function (item) {
        var re = /<script>\s*"use strict";([\s\S]*?)<\/script>/g;
        var match;
        var index = 0;
        while ((match = re.exec(item[1])) !== null) {
            index++;
            try {
                new vm.Script(match[1].replace(/<%=[\s\S]*?%>/g, "0"));
                check(item[0] + " 第 " + index + " 段脚本可编译", true);
            } catch (e) {
                check(item[0] + " 第 " + index + " 段脚本可编译", false, e.message);
            }
        }
        check(item[0] + " 找到脚本", index > 0);
    });

    console.log("【3】共用预览与登录页：alpha 换算");
    check("预览设置含 brandOpacity",
        /\bbrandOpacity\s*:/.test(config));
    check("preview.jsp 接收 brandOpacity 并加底板类",
        preview.indexOf("settings.brandOpacity") >= 0
        && preview.indexOf("login-page-brand-surface") >= 0
        && preview.indexOf("--oa-login-brand-alpha") >= 0);
    check("登录页读取 brand_opacity",
        login.indexOf('getIntValue("brand_opacity")') >= 0);
    check("登录页限制在 0~100",
        login.indexOf("OaonesConst.LOGIN_BRAND_OPACITY_MIN") >= 0
        && login.indexOf("OaonesConst.LOGIN_BRAND_OPACITY_MAX") >= 0);
    check("写 --oa-login-brand-alpha",
        login.indexOf('"--oa-login-brand-alpha:"') >= 0);
    check("alpha = 1 - 透明度 / 100",
        login.indexOf("1.0 - (oaonesBrandOpacity / 100.0)") >= 0);
    check("透明度 < 100 才加 login-page-brand-surface",
        login.indexOf("oaonesBrandOpacity < OaonesConst.LOGIN_BRAND_OPACITY_MAX") >= 0
        && login.indexOf('" login-page-brand-surface"') >= 0);
    check("弹框模式按默认 100、不启用底板",
        login.indexOf("oaonesPopup ? OaonesConst.LOGIN_BRAND_OPACITY_DEFAULT") >= 0);

    console.log("【4】CSS：只改左侧文字底板");
    check("底板使用 --oa-login-brand-alpha",
        loginCss.indexOf("background-color:rgba(255,255,255,var(--oa-login-brand-alpha,0))") >= 0);
    check("底板类带圆角与内边距",
        /\.login-page-brand-surface \.login-brand\s*\{[^}]*border-radius/.test(loginCss)
        && /\.login-page-brand-surface \.login-brand\s*\{[^}]*padding/.test(loginCss));
    check("没有给左侧文字设置 opacity",
        !/\.login-page-brand-surface \.login-brand\s*\{[^}]*\bopacity\s*:/.test(loginCss));

    console.log("【5】后端：实体 / Mapper / 保存校验 / 下发");
    check("实体字段 brandOpacity",
        entity.indexOf("private Integer brandOpacity;") >= 0);
    check("常量 0~100、默认 100",
        consts.indexOf("LOGIN_BRAND_OPACITY_MIN = 0") >= 0
        && consts.indexOf("LOGIN_BRAND_OPACITY_MAX = 100") >= 0
        && consts.indexOf("LOGIN_BRAND_OPACITY_DEFAULT = 100") >= 0);
    check("Mapper 查询含 brand_opacity",
        /brand_left,[\s\S]*?brand_height,\s*brand_opacity/.test(mapper));
    check("Mapper 插入含 brand_opacity",
        mapper.indexOf("#{e.brandWidth}, #{e.brandHeight}, #{e.brandOpacity}") >= 0);
    check("Mapper 更新含 brand_opacity",
        mapper.indexOf("brand_opacity = #{e.brandOpacity}") >= 0);
    check("保存读 brand_opacity",
        impl.indexOf('containsKey("brand_opacity")') >= 0);
    check("空串回默认 100",
        impl.indexOf("LOGIN_BRAND_OPACITY_DEFAULT") >= 0
        && impl.indexOf("左侧文字背景透明度必须在") >= 0);
    check("历史 NULL 按 100 返回",
        impl.indexOf("row.getBrandOpacity() == null") >= 0);
    check("get / publicSettings 都下发 brand_opacity",
        impl.indexOf('json.put("brand_opacity"') >= 0
        && impl.indexOf('data.put("brand_opacity"') >= 0);

    console.log("【6】数据库：三库 × 两目录 0012");
    ["Mysql", "SQLServer", "Oracle"].forEach(function (db) {
        var plainPath = path.join(eco, "data", db, "oaones", "appsyn", "oaones0012.sql");
        var docPath = path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0012.sql");
        check(db + "：两目录 0012 都在", fs.existsSync(plainPath) && fs.existsSync(docPath));
        if (!fs.existsSync(plainPath) || !fs.existsSync(docPath)) { return; }
        var plain = read(plainPath);
        var doc = read(docPath);
        check(db + "：0012 含 brand_opacity",
            plain.indexOf("brand_opacity") >= 0 && doc.indexOf("brand_opacity") >= 0);
        check(db + "：0012 两目录语句逐条一致", stripSql(plain) === stripSql(doc));
        var create = read(path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0001.sql"));
        check(db + "：0001 保持历史基线（不含 brand_opacity）",
            create.indexOf("brand_opacity") < 0);
    });

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
    }
    console.log("\n全部通过 ✓");
})();
