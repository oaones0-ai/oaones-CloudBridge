/**
 * 「登录页外观」自检（node oaones/tools/LoginAppearanceSmokeTest.js）。
 *
 * 覆盖「登录设置」里的三项外观设置：**logo / 整页背景图 / 左侧富文本**。只做静态一致性核对
 * （不需要浏览器 / DOM 沙箱），核对八件事：
 *   1. 设置页 loginConfig/config.jsp：三行控件齐全，回显与保存都带上 logo / bg_image / brand_html，
 *      非维护人只读时一并禁用；
 *   2. 设置页内联脚本能通过语法检查（node vm 编译），并引了富文本编辑器脚本；
 *   3. 上传入口 loginConfig/imageUpload.jsp：multipart 专用入口（收文件），调 logincfg.imageUpload，
 *      用途 kind 放在查询串上；
 *   4. 登录页 login.jsp：logo 在**右侧登录框内部上方**；背景图交给 --oa-login-bg（整页 cover）；
 *      左侧文字**原样输出**到 .login-brand-html（设置什么显示什么）；**弹框模式里三项都不显示**；
 *   5. CSS：logo 按比例缩放、背景图铺满等比（不拉伸）、弹框里背景不显示、富文本基础版式；
 *   6. 富文本编辑器 js/richtext.js 的关键能力（源码模式 / getHtml / setHtml / setDisabled）；
 *   7. 后端：三个连接器（企微 / 钉钉 / 飞书）的 Service / Impl / Controller / Mapper / 实体都带上
 *      uploadImage 与三个字段；
 *   8. 落点：三库 × 两目录的 oaones0007.sql 与 oaones0001.sql 的建表段都含这三列。
 *
 * 与 tools/LoginVerifySmokeTest.js、tools/AttSyncPageSmokeTest.js 同一套“提交前自检”口径。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");
    var vm = require("vm");

    var root = path.resolve(__dirname, "..");                 // oaones/
    var eco = path.resolve(root, "..");                       // ecology/
    var login = read(path.join(root, "login", "login.jsp"));
    var config = read(path.join(root, "loginConfig", "config.jsp"));
    var upload = read(path.join(root, "loginConfig", "imageUpload.jsp"));
    var richtext = read(path.join(root, "js", "richtext.js"));
    var loginCss = read(path.join(root, "css", "login.css"));
    var styleCss = read(path.join(root, "css", "style.css"));
    var assets = read(path.join(root, "assets.jsp"));

    /** 三个连接器的后端源码目录名（企微=公共实现，钉钉 / 飞书为同口径副本） */
    var CONNECTORS = ["wecom", "dingtalk", "feishu"];
    /** 三个连接器的 Mapper XML 所在子目录（企微在 oaones/ 根下） */
    var XML_SUBS = ["", "dingtalk/", "feishu/"];

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

    /** 把 JSP 里的 <%= ... %> 表达式换成 0，剩下的就是可直接做语法检查的 JS */
    function toPlainJs(js) {
        return js.replace(/<%=[\s\S]*?%>/g, "0");
    }

    /**
     * 去掉 SQL 里的注释与空行，用来逐条比对说明版与纯 SQL 版：
     * 去掉 /* … *\/ 块注释、行尾 `--` 注释与空白行（两目录只应差注释）。
     */
    function stripSql(sql) {
        return sql.replace(/\/\*[\s\S]*?\*\//g, "")
            .split("\n")
            .map(function (line) { return line.replace(/--.*$/, "").replace(/\s+$/, "").trim(); })
            .filter(function (line) { return line.length > 0; })
            .join("\n");
    }

    console.log("【1】设置页：logo / 背景图 / 左侧文字三行的控件与保存");
    [
        ["logo 地址（隐藏域）", "cfgLogo"], ["logo 文件输入", "cfgLogoFile"],
        ["logo 预览框", "cfgLogoPreview"], ["logo 上传按钮", "cfgLogoUploadBtn"],
        ["背景图地址（隐藏域）", "cfgBgImage"], ["背景图文件输入", "cfgBgFile"],
        ["背景图预览框", "cfgBgPreview"], ["背景图上传按钮", "cfgBgUploadBtn"],
        ["左侧文字（隐藏域）", "cfgBrandHtml"], ["左侧文字编辑器容器", "cfgBrandBox"],
        ["左侧文字恢复默认", "cfgBrandClearBtn"]
    ].forEach(function (pair) {
        check(pair[0] + "（#" + pair[1] + "）", config.indexOf('id="' + pair[1] + '"') >= 0);
    });
    check("上传入口是专用页（multipart，api.jsp 收不了文件）",
        config.indexOf('url: "/oaones/loginConfig/imageUpload.jsp"') >= 0);
    check("上传用 FormData 提交文件（字段名 file）", config.indexOf('form.append("file", file, file.name)') >= 0);
    check("上传把用途放在查询串上（?kind=logo|bg）",
        config.indexOf('LOGIN_IMG.url + "?kind=" + encodeURIComponent(spec.kind)') >= 0);
    check("回显带上三项（setImg logo / setImg bg / renderBrand）",
        config.indexOf('setImg("logo", data.logo || "")') >= 0
        && config.indexOf('setImg("bg", data.bg_image || "")') >= 0
        && config.indexOf('renderBrand(data.brand_html || "")') >= 0);
    check("保存带上三项（logo / bg_image / brand_html）",
        /logo:\s*byId\("cfgLogo"\)\.value/.test(config)
        && /bg_image:\s*byId\("cfgBgImage"\)\.value/.test(config)
        && /brand_html:\s*brandEditor \? brandEditor\.getHtml\(\) : ""/.test(config));
    check("非维护人只读时禁用上传 / 清除（disableAll 覆盖两类图）",
        /byId\("cfgLogoUploadBtn"\)\.disabled = true/.test(config)
        && /byId\("cfgBgUploadBtn"\)\.disabled = true/.test(config));
    check("非维护人只读时禁用左侧文字编辑器（brandEditor.setDisabled）",
        /brandEditor\.setDisabled\(true\)/.test(config));
    check("说明里写明三项的位置与弹框不显示",
        config.indexOf("右侧登录框的内部上方") >= 0
        && config.indexOf("整个登录页的背景") >= 0
        && config.indexOf("设置什么、登录页左侧就显示什么") >= 0
        && config.indexOf("弹框里不显示背景图") >= 0);

    console.log("【2】设置页脚本：引用与语法");
    check("引了富文本编辑器（js/richtext.js，带版本号）",
        config.indexOf('/oaones/js/richtext.js?v=<%=oaonesAssetV%>') >= 0);
    check("assets.jsp 已把 richtext.js 计入版本号链",
        assets.indexOf('"/oaones/js/richtext.js"') >= 0);
    var jsStart = config.lastIndexOf("<script>");
    var jsEnd = config.lastIndexOf("</script>");
    var inline = jsStart >= 0 && jsEnd > jsStart
        ? config.substring(jsStart + "<script>".length, jsEnd) : "";
    check("能取到设置页的内联脚本块", inline.indexOf("saveConfig") >= 0);
    if (inline.length > 0) {
        var syntaxError = "";
        try {
            new vm.Script(toPlainJs(inline), { filename: "loginConfig/config.jsp#inline" });
        } catch (e) {
            syntaxError = e.message;
        }
        check("内联脚本语法通过（node vm 编译）", syntaxError.length === 0, syntaxError);
    }

    console.log("【3】上传入口页：multipart 专用入口");
    check("是 JSP 页并输出 JSON", upload.indexOf("application/json;charset=UTF-8") >= 0);
    check("先做登录校验（未登录返回 401）", upload.indexOf("HrmUserVarify.getUser(request, response)") >= 0
        && upload.indexOf("401") >= 0);
    check("交给同一个控制器 action（logincfg.imageUpload）",
        upload.indexOf('"logincfg.imageUpload"') >= 0);
    check("说明里写明 kind 在查询串、上传只落盘", upload.indexOf("kind=logo") >= 0
        && upload.indexOf("只是落盘") >= 0);

    console.log("【4】登录页：三项外观的设置位置与弹框口径");
    check("服务端读出三项（logo / bg_image / brand_html）",
        login.indexOf('oaonesScanSettings.getString("logo")') >= 0
        && login.indexOf('oaonesScanSettings.getString("bg_image")') >= 0
        && login.indexOf('oaonesScanSettings.getString("brand_html")') >= 0);
    check("图片地址只认本模块上传的站内地址（前缀 + 无子目录，共用一个校验方法）",
        login.indexOf("private boolean isLoginImage(") >= 0
        && login.indexOf("OaonesConst.LOGIN_IMAGE_URL_PREFIX") >= 0
        && login.indexOf("OaonesConst.LOGIN_LOGO_FILE_PREFIX") >= 0
        && login.indexOf("OaonesConst.LOGIN_BG_FILE_PREFIX") >= 0);
    check("渲染条件排除了弹框模式（logo / 背景图 / 左侧文字三项）",
        login.indexOf("!oaonesPopup && oaonesLogoOn") >= 0
        && login.indexOf("!oaonesPopup && isLoginImage(oaonesBg") >= 0
        && login.indexOf("!oaonesPopup && hasBrandText(oaonesBrandHtml)") >= 0);
    check("logo 标记在登录卡片内部的页签上方",
        /<div class="login-card"[^>]*>[\s\S]{0,1200}?<div class="login-logo">/.test(login)
        && login.indexOf('class="login-logo"') < login.indexOf('class="login-tabs"'));
    check("背景图交给 CSS 变量 --oa-login-bg（整页背景由样式渲染）",
        login.indexOf("--oa-login-bg:url('") >= 0);
    check("左侧文字原样输出到 .login-brand-html（设置什么显示什么，不转义）",
        login.indexOf('<div class="login-brand-html"><%=oaonesBrandHtml%></div>') >= 0);
    check("没配左侧文字时仍渲染系统默认说明（标题 + 已开启方式清单）",
        login.indexOf("<ul class=\"login-brand-list\">") >= 0
        && login.indexOf("<h1><%=Util.toHtml(oaonesPageTitle)%></h1>") >= 0);

    console.log("【5】CSS：三者的呈现口径");
    check("登录页样式：logo 限宽 + 限高、宽高 auto（不拉伸）",
        /\.login-logo img \{[^}]*max-width:100%[^}]*max-height:96px[^}]*width:auto;[^}]*height:auto;[^}]*\}/.test(loginCss));
    check("登录页样式：整页背景按 cover 铺满等比缩放（前端自适应）",
        /\.login-page \{[^}]*background-image:var\(--oa-login-bg, none\)[^}]*background-size:cover;[^}]*\}/.test(loginCss));
    check("登录页样式：弹框模式里不显示 logo / 背景图",
        loginCss.indexOf(".login-page-popup .login-logo { display:none; }") >= 0
        && /\.login-page-popup \{[^}]*background-image:none;/.test(loginCss));
    check("登录页样式：富文本基础版式（.login-brand-html 含标题 / 列表 / 链接 / 图片）",
        /\.login-brand-html h1 \{[^}]*\}/.test(loginCss)
        && /\.login-brand-html ul,\.login-brand-html ol \{[^}]*\}/.test(loginCss)
        && /\.login-brand-html a \{[^}]*\}/.test(loginCss)
        && /\.login-brand-html img \{[^}]*max-width:100%/.test(loginCss));
    check("设置页样式：背景图预览按 cover 裁切显示",
        /\.cfg-bg-preview img \{[^}]*object-fit:cover;/.test(styleCss));
    check("设置页样式：编辑器工具栏 / 编辑区 / 源码 / 色块齐全",
        styleCss.indexOf(".rt-tool {") >= 0 && styleCss.indexOf(".rt-body {") >= 0
        && styleCss.indexOf(".rt-source {") >= 0 && styleCss.indexOf(".rt-c-primary {") >= 0);

    console.log("【6】富文本编辑器（js/richtext.js）");
    check("导出 OaonesRichText.init", richtext.indexOf("window.OaonesRichText = { init: init") >= 0);
    check("能力：取值 / 赋值 / 只读 / 源码切换",
        richtext.indexOf("getHtml: function ()") >= 0
        && richtext.indexOf("setHtml: function (html)") >= 0
        && richtext.indexOf("setDisabled: function (flag)") >= 0
        && richtext.indexOf("function toggleSource()") >= 0);
    check("不用原生 alert / confirm / prompt（走模块提示框与内联输入行）",
        richtext.indexOf("window.alert") < 0 && richtext.indexOf("window.confirm") < 0
        && richtext.indexOf("window.prompt") < 0 && richtext.indexOf("rt-link-row") >= 0);
    check("色值只写在 CSS 里（脚本用 getComputedStyle 读回来）",
        richtext.indexOf("getComputedStyle(dot).backgroundColor") >= 0);
    var rtSyntax = "";
    try {
        new vm.Script(richtext, { filename: "js/richtext.js" });
    } catch (e) {
        rtSyntax = e.message;
    }
    check("编辑器脚本语法通过（node vm 编译）", rtSyntax.length === 0, rtSyntax);

    console.log("【7】后端：三个连接器都带上三项外观");
    CONNECTORS.forEach(function (c) {
        var base = path.join(eco, "src", "com", "customization", "oaones", "appsyn");
        var iface = read(path.join(base, "service", c, "LoginConfigService.java"));
        var impl = read(path.join(base, "service", "impl", c, "LoginConfigServiceImpl.java"));
        var ctrl = read(path.join(base, "controller", c, "LoginConfigController.java"));
        var entity = read(path.join(base, "entity", c, "LoginConfig.java"));

        check(c + "：Service 接口有 uploadImage(kind, …)", iface.indexOf("uploadImage(String kind,") >= 0);
        check(c + "：实现类有 uploadImage（含落盘 / 校验 / 权限 / 用途分派）",
            impl.indexOf("public JSONObject uploadImage(") >= 0
            && impl.indexOf("OaonesImageSize.probe(") >= 0
            && impl.indexOf("loginImageSpec(kind)") >= 0
            && impl.indexOf("canMaintain(operatorId)") >= 0);
        check(c + "：两类图按各自前缀落盘与清理",
            impl.indexOf("OaonesConst.LOGIN_LOGO_FILE_PREFIX") >= 0
            && impl.indexOf("OaonesConst.LOGIN_BG_FILE_PREFIX") >= 0
            && impl.indexOf("deleteImageFile(") >= 0);
        check(c + "：不带参数时保持原值（三项都判断 containsKey）",
            impl.indexOf('params.containsKey("logo")') >= 0
            && impl.indexOf('params.containsKey("bg_image")') >= 0
            && impl.indexOf('params.containsKey("brand_html")') >= 0);
        check(c + "：左侧富文本设置什么存什么（只做长度与空内容校验）",
            impl.indexOf("normalizeBrandHtml(") >= 0
            && impl.indexOf("OaonesConst.LOGIN_BRAND_HTML_MAX") >= 0);
        check(c + "：publicSettings 下发三项（登录页要用）",
            impl.indexOf('data.put("logo"') >= 0
            && impl.indexOf('data.put("bg_image"') >= 0
            && impl.indexOf('data.put("brand_html"') >= 0);
        check(c + "：控制器有 logincfg.imageUpload 且收 multipart + kind",
            ctrl.indexOf('"logincfg.imageUpload"') >= 0
            && ctrl.indexOf("OaonesUpload.read(request, OaonesConst.LOGIN_IMAGE_MAX_BYTES)") >= 0
            && ctrl.indexOf('request.getParameter("kind")') >= 0);
        check(c + "：实体有 bgImage / brandHtml 字段",
            entity.indexOf("private String bgImage;") >= 0 && entity.indexOf("private String brandHtml;") >= 0);
    });
    XML_SUBS.forEach(function (sub) {
        var xml = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", sub, "LoginConfigMapper.xml"));
        check("Mapper XML（" + (sub || "公共") + "）：查询 / 写入都含三列",
            /logo,\s*bg_image,\s*brand_html/.test(xml)
            && xml.indexOf("logo = #{e.logo}") >= 0
            && xml.indexOf("bg_image = #{e.bgImage}") >= 0
            && xml.indexOf("brand_html = #{e.brandHtml}") >= 0);
    });
    var consts = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn", "common", "OaonesConst.java"));
    check("常量：两类图的前缀 / 目录 / 上限与富文本长度上限都在 OaonesConst",
        consts.indexOf('LOGIN_LOGO_FILE_PREFIX = "loginlogo_"') >= 0
        && consts.indexOf('LOGIN_BG_FILE_PREFIX = "loginbg_"') >= 0
        && consts.indexOf("LOGIN_IMAGE_URL_PREFIX = APP_ICON_URL_PREFIX") >= 0
        && consts.indexOf("LOGIN_IMAGE_DIR = APP_ICON_DIR") >= 0
        && consts.indexOf("LOGIN_BG_MAX_BYTES") >= 0
        && consts.indexOf("LOGIN_BRAND_HTML_MAX") >= 0);

    console.log("【8】数据库脚本：三库 × 两目录");
    ["Mysql", "SQLServer", "Oracle"].forEach(function (db) {
        var plain = path.join(eco, "data", db, "oaones", "oaones0007.sql");
        var doc = path.join(eco, "sqlupgrade-sql", db, "oaones", "oaones0007.sql");
        check(db + "：0007 增量脚本（说明版 + 纯 SQL 版）都在", fs.existsSync(plain) && fs.existsSync(doc));
        [read(doc), read(plain)].forEach(function (sql) {
            check(db + "：0007 给 uf_oaones_login_config 加三列",
                sql.indexOf("uf_oaones_login_config") >= 0 && sql.indexOf("logo") >= 0
                && sql.indexOf("bg_image") >= 0 && sql.indexOf("brand_html") >= 0);
        });
        var main = read(path.join(eco, "sqlupgrade-sql", db, "oaones", "oaones0001.sql"));
        check(db + "：0001 建表段已含三列（新库一次建齐）",
            main.indexOf("logo") >= 0 && main.indexOf("bg_image") >= 0 && main.indexOf("brand_html") >= 0);
        // 两目录的编号脚本要逐条一致（说明版去掉注释块后与纯 SQL 版相同）
        check(db + "：两目录 0007 语句逐条一致（说明版去注释 = 纯 SQL 版）",
            stripSql(read(doc)) === stripSql(read(plain)));
        // 口径（2026-10-16 明确）：data/ 目录只交付编号增量脚本，不往 data 的 0001 里追加新列
        var dataMain = read(path.join(eco, "data", db, "oaones", "oaones0001.sql"));
        check(db + "：data/oaones0001.sql 未追加外观三列（保持较早副本）",
            dataMain.indexOf("bg_image") < 0 && dataMain.indexOf("brand_html") < 0);
    });

    console.log("");
    if (failed === 0) {
        console.log("全部通过 ✓");
        return;
    }
    console.log(failed + " 项未通过 ✗");
    process.exit(1);
})();
