/**
 * 「登录设置 → 整页效果预览」+「启动台自动跳转移到应用列表」自检
 * （node oaones/appsyn/tools/LoginPreviewSmokeTest.js）。
 *
 * 只做静态一致性核对：
 *   1. 登录设置页只有一个共用预览（iframe），不再为透明度 / 布局各画一块小预览；
 *   2. 预览把左侧文字 / 左侧文字背景透明度 / Logo / 背景图 / 登录方式 / 登录框位置 / 透明度
 *      一起交给 preview.jsp；
 *   3. preview.jsp 用真实登录页类名渲染，且不改库、不请求接口；
 *   4. 登录页对“只有空标记”的 brand_html 回退到系统默认说明；自定义布局下左侧文字不再整块消失；
 *   5. 启动台自动跳转已从登录设置移除，改由应用列表页的 appc.gopage / appc.gopageSave 维护，
 *      后端只写 gopage_seconds 一列。
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

    function compileInline(file, text) {
        var blocks = [];
        var re = /<script>\s*(?:"use strict";)?([\s\S]*?)<\/script>/g;
        var match;
        while ((match = re.exec(text)) !== null) {
            blocks.push(match[1]);
        }
        check(file + "：找到内联脚本", blocks.length > 0);
        blocks.forEach(function (js, index) {
            try {
                new vm.Script(js.replace(/<%=[\s\S]*?%>/g, "0"));
                check(file + "：第 " + (index + 1) + " 段脚本可编译", true);
            } catch (e) {
                check(file + "：第 " + (index + 1) + " 段脚本可编译", false, e.message);
            }
        });
    }

    var config = read(path.join(root, "loginConfig", "config.jsp"));
    var preview = read(path.join(root, "loginConfig", "preview.jsp"));
    var login = read(path.join(root, "login", "login.jsp"));
    var loginCss = read(path.join(root, "css", "login.css"));
    var appList = read(path.join(root, "appCenter", "AppList.jsp"));
    var appPanel = read(path.join(root, "appCenter", "appListPanel.jsp"));
    var controller = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "controller", "AppCenterController.java"));
    var impl = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "impl", "wecom", "LoginConfigServiceImpl.java"));

    console.log("【1】登录设置页：只有一个共用预览");
    check("顶部有整页预览 iframe", config.indexOf('id="loginPreviewFrame"') >= 0);
    check("预览加载 preview.jsp", config.indexOf("/oaones/appsyn/loginConfig/preview.jsp") >= 0);
    check("登录框拖动 / 缩放手柄都在这一个预览上",
        config.indexOf('id="loginBoxPreview"') >= 0
        && config.indexOf('id="loginBoxResize"') >= 0);
    check("预览固定在浏览器右侧偏上",
        /\.login-effect-preview\s*\{[^}]*position:fixed/.test(config)
        && /\.login-effect-preview\s*\{[^}]*right:16px/.test(config)
        && /\.login-effect-preview\s*\{[^}]*top:76px/.test(config));
    check("浮动预览可收起 / 展开",
        config.indexOf('id="loginPreviewToggleBtn"') >= 0
        && config.indexOf('classList.toggle("collapsed"') >= 0);
    check("不再有透明度的独立小预览",
        config.indexOf("cfgCardAlphaStage") < 0
        && config.indexOf("card-alpha-stage") < 0);
    check("不再有布局的独立小预览",
        config.indexOf("loginBoxStageBrand") < 0
        && config.indexOf("layout-stage-card") < 0);

    console.log("【2】所有设置都进入共用预览");
    ["brandHtml", "brandOpacity", "logo", "bg", "opacity", "custom", "left", "top", "width", "height",
        "methods", "scanText"].forEach(function (key) {
        check("预览设置含 " + key,
            new RegExp("\\b" + key + "\\s*:").test(config));
    });
    check("设置页调用预览接口 apply", config.indexOf("api.apply(currentPreviewSettings())") >= 0);
    check("登录方式开关会刷新共用预览",
        /cfgWecomQrEnable[\s\S]{0,300}addEventListener\("change", renderUnifiedPreview\)/.test(config));
    check("左侧富文本输入会刷新共用预览",
        /onChange:\s*function\s*\(\)\s*\{\s*renderUnifiedPreview\(\)/.test(config));
    check("Logo / 背景图地址变化会刷新共用预览",
        /function setImg[\s\S]{0,220}renderUnifiedPreview\(\)/.test(config));

    console.log("【3】preview.jsp：复用真实版式、只画不写");
    check("暴露 OaonesLoginPreview.apply", preview.indexOf("window.OaonesLoginPreview = { apply: apply }") >= 0);
    check("使用真实登录页类名", preview.indexOf('class="login-wrap"') >= 0
        && preview.indexOf('class="login-brand"') >= 0
        && preview.indexOf('class="login-card"') >= 0);
    check("有登录方式页签与二维码占位", preview.indexOf("data-preview-tab") >= 0
        && preview.indexOf("login-preview-qr") >= 0);
    check("预览页不调用后端接口", preview.indexOf("Oaones.api") < 0
        && preview.indexOf("fetch(") < 0);
    check("预览页不读写登录配置", preview.indexOf("logincfg.") < 0);
    compileInline("preview.jsp", preview);

    console.log("【4】登录页左侧文字：空标记回退 + 自定义布局不再整块隐藏");
    check("登录页对空标记使用默认说明",
        login.indexOf("!oaonesPopup && hasBrandText(oaonesBrandHtml)") >= 0
        && login.indexOf("private boolean hasBrandText(") >= 0);
    check("自定义布局的左侧说明最少保留 200px",
        /\.login-page-custom \.login-brand\s*\{[^}]*width:clamp\(200px,/.test(loginCss));
    check("默认布局登录框在视口水平、垂直居中",
        login.indexOf('" login-page-default"') >= 0
        && preview.indexOf('" login-page-default"') >= 0
        && /\.login-page-default \.login-card\s*\{[^}]*left:50%/.test(loginCss)
        && /\.login-page-default \.login-card\s*\{[^}]*top:50%/.test(loginCss)
        && /\.login-page-default \.login-card\s*\{[^}]*transform:translate\(-50%,-50%\)/.test(loginCss));
    check("预览页复用登录页样式而不另造一套配色",
        preview.indexOf("/oaones/appsyn/css/login.css") >= 0
        && preview.indexOf("/oaones/appsyn/css/style.css") >= 0);

    console.log("【5】启动台自动跳转：已移到应用列表页");
    check("登录设置页没有任何 gopage 控件",
        config.indexOf("cfgGopage") < 0 && config.indexOf("gopage_seconds:") < 0);
    check("应用列表页有秒数设置与保存按钮",
        appPanel.indexOf('id="appcGopageRange"') >= 0
        && appPanel.indexOf('id="appcGopageSeconds"') >= 0
        && appPanel.indexOf('id="appcGopageSaveBtn"') >= 0);
    check("应用列表页走独立接口",
        appPanel.indexOf('Oaones.api("appc.gopage"') >= 0
        && appPanel.indexOf('Oaones.api("appc.gopageSave"') >= 0);
    check("页壳只给系统管理员 / appc.admin.ids 渲染该设置",
        appList.indexOf("canMaintainGopage") >= 0
        && appPanel.indexOf("gopageCanMaintain") >= 0);
    check("控制器有 appc.gopage / appc.gopageSave",
        controller.indexOf('"appc.gopage".equals(action)') >= 0
        && controller.indexOf('"appc.gopageSave".equals(action)') >= 0);
    check("保存只写 gopage_seconds，不改登录方式",
        /public JSONObject saveGopageSeconds[\s\S]{0,2600}entity\.setGopageSeconds\(value\)/.test(impl)
        && !/public JSONObject saveGopageSeconds[\s\S]{0,2600}flag\(params,/.test(impl));

    compileInline("config.jsp", config);

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
    }
    console.log("\n全部通过 ✓");
})();
