/**
 * 「登录页左侧文字 → 文字说明位置与尺寸」自检
 * （node oaones/tools/LoginBrandLayoutSmokeTest.js）。
 *
 * 只做静态一致性核对（不需要浏览器 / 应用服务器），覆盖：
 *   1. 设置页：登录框布局 → 登录页左侧文字 → 左侧文字背景透明度 →
 *      文字说明位置与尺寸 的顺序；
 *   2. 设置页：左侧文字也有“自定义 / 四个像素输入 / 预览拖动 / 缩放 / 恢复默认”；
 *   3. 共用预览：preview.jsp 接收 brandCustom / brandLeft / top / width / height；
 *   4. 登录页：非弹框模式写 --oa-login-brand-* 并加 login-page-brand-custom；
 *   5. CSS：桌面端绝对定位 + 固定宽高 + 文字区内滚动，窄屏仍走默认布局；
 *   6. 后端：实体 / Mapper / 保存校验 / get 与 publicSettings；
 *   7. 数据库：三库 × 两目录的 oaones0011.sql；历史脚本不回写本批字段。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");
    var vm = require("vm");

    var root = path.resolve(__dirname, "..");
    var eco = path.resolve(root, "..");
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
    var preview = read(path.join(root, "loginConfig", "preview.jsp"));
    var login = read(path.join(root, "login", "login.jsp"));
    var loginCss = read(path.join(root, "css", "login.css"));
    var impl = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "impl", "wecom", "LoginConfigServiceImpl.java"));
    var entity = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "entity", "wecom", "LoginConfig.java"));
    var consts = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "common", "OaonesConst.java"));
    var mapper = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", "LoginConfigMapper.xml"));

    console.log("【1】设置页顺序：登录框位置与尺寸 → 登录页左侧文字 → 背景透明度 → 文字说明位置与尺寸");
    // 说明区也提到同样的标题；取最后一次出现的表格字段行。
    var boxRow = config.lastIndexOf("<b>登录框位置与尺寸</b>");
    var textRow = config.lastIndexOf("<b>登录页左侧文字</b>");
    var alphaRow = config.lastIndexOf("<b>左侧文字背景透明度</b>");
    var brandRow = config.lastIndexOf("<b>文字说明位置与尺寸</b>");
    check("四行都存在", boxRow >= 0 && textRow >= 0 && alphaRow >= 0 && brandRow >= 0);
    check("登录框布局在左侧文字之前", boxRow >= 0 && textRow > boxRow);
    check("背景透明度在左侧文字之后、位置尺寸之前", alphaRow > textRow && brandRow > alphaRow);
    check("文字说明布局在左侧文字之后", brandRow > textRow);

    console.log("【2】设置页：左侧文字的位置 / 尺寸控件与保存参数");
    [
        'id="brandPreview"', 'id="brandResize"',
        'id="cfgBrandCustom"', 'id="cfgBrandLeft"', 'id="cfgBrandTop"',
        'id="cfgBrandWidth"', 'id="cfgBrandHeight"', 'id="cfgBrandResetBtn"'
    ].forEach(function (needle) {
        check("控件 " + needle, config.indexOf(needle) >= 0);
    });
    check("保存时提交左侧文字四个布局参数",
        config.indexOf("brand_left: brandParams.brand_left") >= 0
        && config.indexOf("brand_top: brandParams.brand_top") >= 0
        && config.indexOf("brand_width: brandParams.brand_width") >= 0
        && config.indexOf("brand_height: brandParams.brand_height") >= 0);
    check("未勾选自定义时四个坐标 / 尺寸都发空串",
        config.indexOf('brand_left: "", brand_top: "", brand_width: "", brand_height: ""') >= 0);
    check("保存前按范围校验四项",
        config.indexOf("文字说明位置与尺寸要一起设置") >= 0
        && config.indexOf("BRAND_LIMITS.minWidth") >= 0
        && config.indexOf("BRAND_LIMITS.maxPos") >= 0);
    check("拖动文字区 / 右下角缩放都有指针事件",
        config.indexOf('beginBrandDrag(e, "move")') >= 0
        && config.indexOf('beginBrandDrag(e, "resize")') >= 0
        && config.indexOf('window.addEventListener("pointermove", moveBrandDrag)') >= 0);
    check("非维护人禁用文字说明布局",
        config.indexOf('byId("cfgBrandCustom").disabled = true') >= 0
        && config.indexOf("无维护权限，不展示当前文字说明布局") >= 0);

    console.log("【3】设置页内联脚本语法");
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

    console.log("【4】共用预览：左侧文字布局进入同一个画面");
    ["brandCustom", "brandLeft", "brandTop", "brandWidth", "brandHeight"].forEach(function (key) {
        check("预览设置含 " + key, new RegExp("\\b" + key + "\\s*:").test(config));
    });
    check("按 iframe 里 .login-brand 的真实矩形摆手柄",
        config.indexOf('getElementById("previewBrand")') >= 0
        && config.indexOf("brandRect") >= 0);
    check("preview.jsp 应用左侧文字布局",
        preview.indexOf("settings.brandCustom") >= 0
        && preview.indexOf("login-page-brand-custom") >= 0
        && preview.indexOf("--oa-login-brand-left") >= 0
        && preview.indexOf("--oa-login-brand-width") >= 0);
    if (preview.indexOf("<script>") >= 0) {
        var previewScript = preview.match(/<script>\s*"use strict";([\s\S]*?)<\/script>/);
        check("preview.jsp 有内联脚本", !!previewScript);
        if (previewScript) {
            try {
                new vm.Script(previewScript[1].replace(/<%=[\s\S]*?%>/g, "0"));
                check("preview.jsp 脚本可编译", true);
            } catch (e) {
                check("preview.jsp 脚本可编译", false, e.message);
            }
        }
    }

    console.log("【5】登录页：左侧文字布局变量 + 弹框保护");
    check("读取 brand_custom",
        login.indexOf('oaonesScanSettings.getBooleanValue("brand_custom")') >= 0);
    check("写四个 CSS 变量",
        login.indexOf("--oa-login-brand-left:") >= 0
        && login.indexOf("--oa-login-brand-top:") >= 0
        && login.indexOf("--oa-login-brand-width:") >= 0
        && login.indexOf("--oa-login-brand-height:") >= 0);
    check("自定义版式排除弹框模式",
        /!oaonesPopup\s*&&\s*oaonesScanSettings\.getBooleanValue\("brand_custom"\)/.test(login));
    check("body 带 login-page-brand-custom 类",
        login.indexOf('" login-page-brand-custom"') >= 0);
    check("窄屏不启用固定坐标（CSS 只在 ≥861px 内生效）",
        /@media \(min-width: 861px\)[\s\S]*\.login-page-brand-custom \.login-brand\s*\{/.test(loginCss));

    console.log("【6】CSS：绝对定位 + 固定宽高 + 文字区内滚动");
    check("使用四个品牌变量",
        /\.login-page-brand-custom \.login-brand\s*\{[^}]*left:var\(--oa-login-brand-left\)/.test(loginCss)
        && /\.login-page-brand-custom \.login-brand\s*\{[^}]*top:var\(--oa-login-brand-top\)/.test(loginCss)
        && /\.login-page-brand-custom \.login-brand\s*\{[^}]*width:var\(--oa-login-brand-width\)/.test(loginCss)
        && /\.login-page-brand-custom \.login-brand\s*\{[^}]*height:var\(--oa-login-brand-height\)/.test(loginCss));
    check("文字区固定高度时内部滚动",
        /\.login-page-brand-custom \.login-brand\s*\{[^}]*overflow:auto/.test(loginCss));
    check("覆盖默认版式的垂直居中位移",
        /\.login-page-brand-custom \.login-brand\s*\{[^}]*transform:none/.test(loginCss));

    console.log("【7】后端：实体 / Mapper / 保存校验 / 下发");
    ["brandLeft", "brandTop", "brandWidth", "brandHeight"].forEach(function (field) {
        check("实体字段 " + field, entity.indexOf("private Integer " + field + ";") >= 0);
    });
    check("Mapper 查询含四列",
        /brand_left,[\s\S]*?brand_top,[\s\S]*?brand_width,[\s\S]*?brand_height/.test(mapper));
    check("Mapper 写入含四列",
        mapper.indexOf("brand_left = #{e.brandLeft}") >= 0
        && mapper.indexOf("brand_top = #{e.brandTop}") >= 0
        && mapper.indexOf("brand_width = #{e.brandWidth}") >= 0
        && mapper.indexOf("brand_height = #{e.brandHeight}") >= 0);
    check("四项要么全填要么全空",
        impl.indexOf("文字说明位置与尺寸要一起设置") >= 0
        && impl.indexOf("provided != 0 && provided != 4") >= 0);
    check("宽高 / 坐标有范围校验",
        impl.indexOf("MIN_BRAND_WIDTH") >= 0 && impl.indexOf("MAX_BRAND_HEIGHT") >= 0
        && impl.indexOf("MAX_BRAND_POS") >= 0);
    check("常量在 OaonesConst",
        consts.indexOf("LOGIN_BRAND_MIN_WIDTH") >= 0
        && consts.indexOf("LOGIN_BRAND_MAX_HEIGHT") >= 0);
    check("get 下发 brand_custom 与四个值",
        impl.indexOf('json.put("brand_custom"') >= 0
        && impl.indexOf('json.put("brand_left"') >= 0
        && impl.indexOf('json.put("brand_width"') >= 0);
    check("publicSettings 下发 login 页需要的 brand_custom 与四个值",
        impl.indexOf('data.put("brand_custom"') >= 0
        && impl.indexOf('data.put("brand_height"') >= 0);

    console.log("【8】数据库：三库 × 两目录 0011");
    var COLS = ["brand_left", "brand_top", "brand_width", "brand_height"];
    ["Mysql", "SQLServer", "Oracle"].forEach(function (db) {
        var plainPath = path.join(eco, "data", db, "oaones", "oaones0011.sql");
        var docPath = path.join(eco, "sqlupgrade-sql", db, "oaones", "oaones0011.sql");
        check(db + "：两目录 0011 都在", fs.existsSync(plainPath) && fs.existsSync(docPath));
        if (!fs.existsSync(plainPath) || !fs.existsSync(docPath)) { return; }
        var plain = read(plainPath);
        var doc = read(docPath);
        COLS.forEach(function (col) {
            check(db + "：0011 含 " + col, plain.indexOf(col) >= 0 && doc.indexOf(col) >= 0);
        });
        check(db + "：0011 两目录语句逐条一致", stripSql(plain) === stripSql(doc));
        var create = read(path.join(eco, "sqlupgrade-sql", db, "oaones", "oaones0001.sql"));
        check(db + "：0001 保持历史基线（不含本批四列）",
            COLS.every(function (col) { return create.indexOf(col) < 0; }));
    });

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
    }
    console.log("\n全部通过 ✓");
})();
