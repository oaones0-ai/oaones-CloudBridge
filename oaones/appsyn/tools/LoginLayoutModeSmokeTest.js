/**
 * 「登录框 / 左侧文字共用定位方式」自检
 * （node oaones/appsyn/tools/LoginLayoutModeSmokeTest.js）。
 *
 * 只做静态一致性核对，覆盖：
 *   1. 设置页：左上角相对 / 中心点相对二选一，且一个共享单选同时控制两块区域；
 *   2. 切换定位方式时按当前矩形换算 X / Y，中心模式允许负数；
 *   3. preview.jsp 与正式登录页读取同一个 login_layout_mode，并使用区域中心定位；
 *   4. 后端实体 / Mapper / 保存校验 / 旧配置默认值；
 *   5. MySQL、SQL Server、Oracle 的 data 与 sqlupgrade-sql 新迁移脚本。
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

    function compileInline(name, text) {
        var match = text.match(/<script>\s*"use strict";([\s\S]*?)<\/script>/);
        check(name + "：找到主脚本", !!match);
        if (!match) {
            return;
        }
        try {
            new vm.Script(match[1].replace(/<%=[\s\S]*?%>/g, "0"));
            check(name + "：内联脚本可编译", true);
        } catch (e) {
            check(name + "：内联脚本可编译", false, e.message);
        }
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
    var mapper = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", "appsyn",
        "LoginConfigMapper.xml"));

    console.log("【1】设置页：共享二选一");
    check("有左上角相对定位选项",
        config.indexOf('id="cfgLayoutModeLeftTop"') >= 0
        && config.indexOf('value="0" checked') >= 0);
    check("有中心点相对定位选项",
        config.indexOf('id="cfgLayoutModeCenter"') >= 0
        && config.indexOf('value="1"') >= 0);
    check("两项使用同一个 radio name，具备互斥选择",
        (config.match(/name="cfgLayoutMode"/g) || []).length === 2);
    check("说明明确登录框与左侧文字共用定位方式",
        config.indexOf("登录框与左侧文字共用") >= 0
        && config.indexOf("登录框和左侧文字始终使用同一种定位方式") >= 0);
    check("保存参数带 login_layout_mode",
        config.indexOf("login_layout_mode: String(layoutMode)") >= 0);
    check("中心模式输入允许负数并保留非整数拦截",
        config.indexOf("/^-?\\d+$/") >= 0
        && config.indexOf('setAttribute("min", center ? String(-LAYOUT_LIMITS.maxPos) : "0")') >= 0);
    check("切换模式按现有矩形换算，不清空两块区域",
        config.indexOf("function setLayoutMode(") >= 0
        && config.indexOf("writeLayoutInputs(cardBox)") >= 0
        && config.indexOf("writeBrandLayoutInputs(brandBox)") >= 0);
    check("中心模式保存 X / Y 偏移，而不是物理左上角",
        config.indexOf("login_box_left: String(box.x)") >= 0
        && config.indexOf("brand_left: String(brandBox.x)") >= 0);
    compileInline("config.jsp", config);

    console.log("【2】共用预览与正式登录页");
    check("预览接收 layoutMode",
        config.indexOf("layoutMode: layoutMode") >= 0
        && preview.indexOf("settings.layoutMode") >= 0);
    check("预览加中心定位类",
        preview.indexOf('centerLayout ? " login-page-layout-center" : ""') >= 0);
    check("正式登录页读取 login_layout_mode",
        login.indexOf('getIntValue("login_layout_mode")') >= 0);
    check("正式登录页加中心定位类",
        login.indexOf('oaonesLayoutCenter ? " login-page-layout-center" : ""') >= 0);
    check("弹框模式不启用中心定位",
        login.indexOf("oaonesPopup ? 0 : oaonesScanSettings.getIntValue") >= 0);
    check("登录框与左侧文字都用中心点换算",
        /\.login-page-layout-center\.login-page-custom \.login-card\s*\{[^}]*calc\(50% \+ var\(--oa-login-card-left\)\)/.test(loginCss)
        && /transform:translate\(-50%,-50%\)/.test(loginCss)
        && /\.login-page-layout-center\.login-page-brand-custom \.login-brand\s*\{[^}]*calc\(50% \+ var\(--oa-login-brand-left\)\)/.test(loginCss));
    compileInline("preview.jsp", preview);

    console.log("【3】后端：实体 / Mapper / 校验 / 兼容");
    check("实体字段 loginLayoutMode",
        entity.indexOf("private Integer loginLayoutMode;") >= 0);
    check("Mapper 查询 / 插入 / 更新都带 login_layout_mode",
        /logo, bg_image, brand_html, login_layout_mode/.test(mapper)
        && mapper.indexOf("#{e.loginLayoutMode}") >= 0
        && mapper.indexOf("login_layout_mode = #{e.loginLayoutMode}") >= 0);
    check("历史空值默认左上角定位",
        impl.indexOf("row.getLoginLayoutMode() == null") >= 0
        && impl.indexOf("? LAYOUT_MODE_LEFT_TOP : row.getLoginLayoutMode().intValue()") >= 0);
    check("中心模式用允许负数的整数解析",
        impl.indexOf("optionalSignedInt(params, \"login_box_left\"") >= 0
        && impl.indexOf("optionalSignedInt(params, \"brand_left\"") >= 0);
    check("保存与登录页下发同一定位方式",
        impl.indexOf("entity.setLoginLayoutMode(Integer.valueOf(layoutMode))") >= 0
        && impl.indexOf('data.put("login_layout_mode"') >= 0
        && impl.indexOf('json.put("login_layout_mode"') >= 0);

    console.log("【4】数据库：三库 × 两目录 oaones0020_login_layout_mode");
    ["Mysql", "SQLServer", "Oracle"].forEach(function (db) {
        var plainPath = path.join(eco, "data", db, "oaones", "appsyn",
            "oaones0020_login_layout_mode.sql");
        var docPath = path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn",
            "oaones0020_login_layout_mode.sql");
        check(db + "：两目录迁移都在", fs.existsSync(plainPath) && fs.existsSync(docPath));
        if (!fs.existsSync(plainPath) || !fs.existsSync(docPath)) {
            return;
        }
        var plain = read(plainPath);
        var doc = read(docPath);
        check(db + "：迁移增加 login_layout_mode 且默认 0",
            plain.indexOf("login_layout_mode") >= 0
            && plain.indexOf("DEFAULT 0") >= 0
            && doc.indexOf("login_layout_mode") >= 0
            && doc.indexOf("DEFAULT 0") >= 0);
        check(db + "：仅使用 ALTER 修改表结构",
            plain.indexOf("ALTER TABLE") >= 0
            && doc.indexOf("ALTER TABLE") >= 0
            && plain.indexOf("CREATE TABLE") < 0
            && doc.indexOf("CREATE TABLE") < 0);
        check(db + "：两目录 SQL 语句逐条一致", stripSql(plain) === stripSql(doc));
        ["data", "sqlupgrade-sql"].forEach(function (repo) {
            var dir = path.join(eco, repo, db, "oaones", "appsyn");
            var leaked = fs.readdirSync(dir).filter(function (name) {
                return name !== "oaones0020_login_layout_mode.sql"
                    && /\.sql$/i.test(name)
                    && read(path.join(dir, name)).indexOf("login_layout_mode") >= 0;
            });
            check(repo + "/" + db + "：历史 SQL 未写入本列", leaked.length === 0,
                leaked.join(", "));
        });
    });

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
    }
    console.log("\n全部通过 ✓");
})();
