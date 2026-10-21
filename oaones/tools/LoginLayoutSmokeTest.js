/**
 * 「登录框位置与尺寸 / 桌面启动台自动跳转」自检
 * （node oaones/tools/LoginLayoutSmokeTest.js）。
 *
 * 只做静态一致性核对（不需要浏览器 / 应用服务器），覆盖：
 *   1. 设置页：可视化预览（拖动 / 右下角缩放）、四个像素输入、启动台秒数滑块与保存参数；
 *   2. 设置页内联脚本能通过语法检查；
 *   3. 登录页：按视口左上角像素写 CSS 变量、只在非弹框模式启用自定义布局；
 *   4. CSS：桌面端绝对定位 + 固定宽高，窄屏仍走默认移动端布局；
 *   5. 桌面启动台：跳转秒数读登录设置表（维护入口在应用列表，留空回退 properties）；
 *   6. 后端：五列的实体 / Mapper / 保存校验 / 实际生效值；
 *   7. 数据库：三库 × 两目录的 oaones0009.sql；历史 oaones0001.sql 保持原样、不回写本批字段。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");
    var vm = require("vm");

    var root = path.resolve(__dirname, "..");     // oaones/
    var eco = path.resolve(root, "..");           // ecology/
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

    /** 去掉 SQL 注释与空白行，用来核对说明版与纯 SQL 版只差注释。 */
    function stripSql(sql) {
        return sql.replace(/\/\*[\s\S]*?\*\//g, "")
            .split("\n")
            .map(function (line) { return line.replace(/--.*$/, "").replace(/\s+$/, "").trim(); })
            .filter(function (line) { return line.length > 0; })
            .join("\n");
    }

    var config = read(path.join(root, "loginConfig", "config.jsp"));
    var appListPanel = read(path.join(root, "appCenter", "appListPanel.jsp"));
    var login = read(path.join(root, "login", "login.jsp"));
    var loginCss = read(path.join(root, "css", "login.css"));
    var desktop = read(path.join(root, "appCenter", "Desktop.jsp"));
    var impl = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "impl", "wecom", "LoginConfigServiceImpl.java"));
    var entity = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "entity", "wecom", "LoginConfig.java"));
    var service = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "wecom", "LoginConfigService.java"));
    var sso = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "common", "OaonesSso.java"));
    var mapper = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", "LoginConfigMapper.xml"));

    console.log("【1】设置页：可视化预览与保存参数");
    [
        'id="loginBoxStage"', 'id="loginBoxPreview"', 'id="loginBoxResize"',
        'id="cfgLoginBoxCustom"', 'id="cfgLoginBoxViewport"', 'id="cfgLoginBoxLeft"', 'id="cfgLoginBoxTop"',
        'id="cfgLoginBoxWidth"', 'id="cfgLoginBoxHeight"', 'id="cfgLoginBoxResetBtn"'
    ].forEach(function (needle) {
        check("控件 " + needle, config.indexOf(needle) >= 0);
    });
    check("保存时提交登录框四个布局参数",
        config.indexOf("login_box_left: boxParams.login_box_left") >= 0
        && config.indexOf("login_box_top: boxParams.login_box_top") >= 0
        && config.indexOf("login_box_width: boxParams.login_box_width") >= 0
        && config.indexOf("login_box_height: boxParams.login_box_height") >= 0);
    check("登录设置页不再出现启动台跳转设置",
        config.indexOf("cfgGopage") < 0 && config.indexOf("gopage_seconds:") < 0);
    check("启动台跳转设置移到应用列表页（滑块 / 秒数 / 保存）",
        appListPanel.indexOf('id="appcGopageRange"') >= 0
        && appListPanel.indexOf('id="appcGopageSeconds"') >= 0
        && appListPanel.indexOf('id="appcGopageSaveBtn"') >= 0);
    check("应用列表页走独立接口，不动登录方式",
        appListPanel.indexOf('Oaones.api("appc.gopageSave"') >= 0
        && appListPanel.indexOf('gopage_seconds: value.blank ? "" : String(value.value)') >= 0);
    check("未勾选自定义时四个坐标 / 尺寸都发空串（恢复默认布局）",
        config.indexOf('login_box_left: "", login_box_top: "", login_box_width: "", login_box_height: ""') >= 0);
    check("拖动白框 / 右下角缩放都有指针事件",
        config.indexOf('beginLayoutDrag(e, "move")') >= 0
        && config.indexOf('beginLayoutDrag(e, "resize")') >= 0
        && config.indexOf('window.addEventListener("pointermove", moveLayoutDrag)') >= 0);

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

    console.log("【3】登录页：CSS 变量 + 弹框保护");
    check("读取 login_box_custom", login.indexOf('oaonesScanSettings.getBooleanValue("login_box_custom")') >= 0);
    check("写四个 CSS 变量",
        login.indexOf("--oa-login-card-left:") >= 0
        && login.indexOf("--oa-login-card-top:") >= 0
        && login.indexOf("--oa-login-card-width:") >= 0
        && login.indexOf("--oa-login-card-height:") >= 0);
    check("自定义布局排除弹框模式", login.indexOf("!oaonesPopup && oaonesScanSettings.getBooleanValue(\"login_box_custom\")") >= 0);
    check("body 带 login-page-custom 类", login.indexOf('" login-page-custom"') >= 0);
    check("不勾选自定义时 body 带 login-page-default 类", login.indexOf('" login-page-default"') >= 0);

    console.log("【4】CSS：桌面端绝对定位、窄屏默认布局");
    check("有 ≥861px 的自定义布局规则", loginCss.indexOf("@media (min-width: 861px)") >= 0);
    check("默认布局登录框水平、垂直居中",
        /\.login-page-default \.login-card\s*\{[^}]*left:50%/.test(loginCss)
        && /\.login-page-default \.login-card\s*\{[^}]*top:50%/.test(loginCss)
        && /\.login-page-default \.login-card\s*\{[^}]*transform:translate\(-50%,-50%\)/.test(loginCss));
    check("默认布局左侧说明区在登录框左边",
        /\.login-page-default \.login-brand\s*\{[^}]*left:24px/.test(loginCss));
    check("登录框绝对定位且使用四个变量",
        /\.login-page-custom \.login-card\s*\{[^}]*position:absolute/.test(loginCss)
        && /left:var\(--oa-login-card-left\)/.test(loginCss)
        && /top:var\(--oa-login-card-top\)/.test(loginCss)
        && /width:var\(--oa-login-card-width\)/.test(loginCss)
        && /height:var\(--oa-login-card-height\)/.test(loginCss));
    check("高度固定时卡片内滚动", /\.login-page-custom \.login-card\s*\{[^}]*overflow:auto/.test(loginCss));
    check("自定义模式下左侧说明区收窄到登录框左侧",
        /\.login-page-custom \.login-brand\s*\{[^}]*width:clamp\(/.test(loginCss));

    console.log("【5】桌面启动台：秒数读登录设置表（应用列表维护）");
    check("Desktop.jsp 调 gopageSeconds()",
        desktop.indexOf("new LoginConfigServiceImpl().gopageSeconds()") >= 0);
    check("Desktop.jsp 无条件读取自动跳转秒数",
        desktop.indexOf("int oaonesGopageSeconds = new LoginConfigServiceImpl().gopageSeconds();") >= 0);
    check("没有 gopage 时回退跳转到站点根路径",
        desktop.indexOf('var effectiveTarget = target || "/";') >= 0);
    check("外部 gopage 的倒计时文案显示目标网址",
        desktop.indexOf("oaonesGopageLower.startsWith(\"http://\")") >= 0
        && desktop.indexOf("oaonesGopageLower.startsWith(\"https://\")") >= 0
        && desktop.indexOf('oaonesGopageExternal ? "目标网址" : "OA默认首页"') >= 0);
    check("Desktop.jsp 的 gopage 支持完整 http(s) 网址",
        desktop.indexOf("OaonesSso.safeRedirectUrl(") >= 0
        && desktop.indexOf("internalPath(OaonesUtil.s(request.getParameter(\"gopage\")))") < 0);
    check("登录页的 gopage 支持完整 http(s) 网址",
        login.indexOf("OaonesSso.safeRedirectUrl(") >= 0
        && login.indexOf("internalPath(OaonesUtil.s(request.getParameter(\"gopage\")))") < 0);
    check("登录页带 gopage 时登录后直接使用原始目标，不先包进桌面启动台",
        login.indexOf("String oaonesGopage = oaonesGopageRaw;") >= 0
        && login.indexOf('+ "gopage=" + java.net.URLEncoder.encode(oaonesGopageRaw, "UTF-8")') < 0);
    check("gopage 专用校验允许 http/https 且拒绝账号信息与危险写法",
        sso.indexOf("public static String safeRedirectUrl") >= 0
        && sso.indexOf('"http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)') >= 0
        && sso.indexOf("uri.getRawUserInfo() == null") >= 0
        && sso.indexOf("value.indexOf('\\\\') >= 0") >= 0);
    check("gopageSeconds 不回读登录页 properties 以外的硬编码",
        service.indexOf("int gopageSeconds();") >= 0
        && impl.indexOf("OaonesProps.loginLandingGopageSeconds()") >= 0);

    console.log("【6】后端：实体 / Mapper / 保存校验");
    ["loginBoxLeft", "loginBoxTop", "loginBoxWidth", "loginBoxHeight", "gopageSeconds"].forEach(function (field) {
        check("实体字段 " + field, entity.indexOf("private Integer " + field + ";") >= 0);
    });
    check("Mapper 查询含五列",
        /login_box_left,[\s\S]*?login_box_top,[\s\S]*?login_box_width,[\s\S]*?login_box_height,[\s\S]*?login_box_opacity,[\s\S]*?gopage_seconds/.test(mapper));
    check("Mapper 写入含五列",
        mapper.indexOf("login_box_left = #{e.loginBoxLeft}") >= 0
        && mapper.indexOf("login_box_top = #{e.loginBoxTop}") >= 0
        && mapper.indexOf("login_box_width = #{e.loginBoxWidth}") >= 0
        && mapper.indexOf("login_box_height = #{e.loginBoxHeight}") >= 0
        && mapper.indexOf("gopage_seconds = #{e.gopageSeconds}") >= 0);
    check("四项要么全填要么全空", impl.indexOf("provided != 0 && provided != 4") >= 0);
    check("宽高 / 坐标有范围校验",
        impl.indexOf("MIN_BOX_WIDTH") >= 0 && impl.indexOf("MAX_BOX_HEIGHT") >= 0
        && impl.indexOf("MAX_BOX_POS") >= 0);
    check("启动台秒数 0~120 校验",
        impl.indexOf("MIN_GOPAGE_SECONDS") >= 0
        && impl.indexOf("MAX_GOPAGE_SECONDS") >= 0
        && impl.indexOf("effectiveGopageSeconds") >= 0);
    check("登录页下发 login_box_custom", impl.indexOf('data.put("login_box_custom"') >= 0);

    console.log("【7】数据库：三库 × 两目录 + 全新库建表段");
    var COLS = ["login_box_left", "login_box_top", "login_box_width", "login_box_height", "gopage_seconds"];
    ["Mysql", "SQLServer", "Oracle"].forEach(function (db) {
        var plainPath = path.join(eco, "data", db, "oaones", "oaones0009.sql");
        var docPath = path.join(eco, "sqlupgrade-sql", db, "oaones", "oaones0009.sql");
        check(db + "：两目录 0009 都在", fs.existsSync(plainPath) && fs.existsSync(docPath));
        var plain = read(plainPath);
        var doc = read(docPath);
        COLS.forEach(function (col) {
            check(db + "：0009 含 " + col, plain.indexOf(col) >= 0 && doc.indexOf(col) >= 0);
        });
        check(db + "：0009 两目录语句逐条一致", stripSql(plain) === stripSql(doc));
        var create = read(path.join(eco, "sqlupgrade-sql", db, "oaones", "oaones0001.sql"));
        // 历史 SQL 不回写：本批字段只出现在新增的 0009 脚本里。
        check(db + "：0001 保持历史基线（不含本批五列）",
            COLS.every(function (col) { return create.indexOf(col) < 0; }));
    });

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
    }
    console.log("\n全部通过 ✓");
})();
