/**
 * 「验证登录（手机号 / 邮箱）」自检（node oaones/appsyn/tools/LoginVerifySmokeTest.js）。
 *
 * 只做**静态一致性核对**（不需要浏览器 / DOM 沙箱），核对五件事：
 *   1. 登录页 login.jsp：第二个页签是“验证登录”（页签内可切手机号 / 邮箱），
 *      相关 id 齐全，且 `byId("xxx")` 用到的 id 都能在页面里找到（防“改了 id 忘了改 JS”）；
 *   2. 登录页调的 action 都在公共 `LoginController` 里存在（防“前端调了个不存在的 action”）；
 *   3. 设置页 loginConfig/config.jsp：开关里有“邮箱”一项，保存参数与前置校验都带它；
 *   4. 弹框模式（?popup=1）的**页签高度一致**：CSS 用公共高度变量、脚本量公共高度、
 *      量的时候不上报中间态；
 *   5. 前后端口径一致：邮箱格式校验规则（页面正则 = 服务端 EMAIL_PATTERN）、
 *      三个连接器的服务 / Mapper / SQL 脚本 / properties 都已就位。
 *
 * 与 tools/AttSyncPageSmokeTest.js、tools/MtgSyncDirTabSmokeTest.js、
 * tools/OaonesUiConstraintSmokeTest.js 同一套“提交前自检”口径。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");

    var root = path.resolve(__dirname, "..");                 // oaones/
    var eco = path.resolve(root, "../..");                       // ecology/
    var login = read(path.join(root, "login", "login.jsp"));
    var config = read(path.join(root, "loginConfig", "config.jsp"));
    var css = read(path.join(root, "css", "login.css"));
    var controller = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "controller", "LoginController.java"));

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

    /** 去掉 SQL 注释与空行，用来逐条比对说明版与纯 SQL 版（两目录只应差注释） */
    function stripSql(sql) {
        return sql.replace(/\/\*[\s\S]*?\*\//g, "")
            .split("\n")
            .map(function (line) { return line.replace(/--.*$/, "").replace(/\s+$/, "").trim(); })
            .filter(function (line) { return line.length > 0; })
            .join("\n");
    }

    console.log("【1】登录页：验证登录页签（手机号 / 邮箱）");
    check("页签叫“验证登录”（不是“手机号验证登录”）", /id="loginTabVerify"[^>]*>验证登录</.test(login));
    check("没有残留的“手机号验证登录”页签", login.indexOf("手机号验证登录") < 0);
    [
        ["页签", "loginTabVerify"], ["面板", "loginPaneVerify"], ["表单", "verifyForm"],
        ["验证方式·手机号", "verifyChannelMobile"], ["验证方式·邮箱", "verifyChannelEmail"],
        ["方式切换行", "verifyMethodRow"], ["账号输入", "verifyTarget"], ["账号标签", "verifyTargetLabel"],
        ["验证码输入", "verifyCode"], ["验证码标签", "verifyCodeLabel"],
        ["获取验证码按钮", "verifySendBtn"], ["登录按钮", "verifySubmitBtn"], ["提示行", "verifyMsg"],
        ["说明一行", "verifyTip"]
    ].forEach(function (pair) {
        check(pair[0] + "（#" + pair[1] + "）", login.indexOf('id="' + pair[1] + '"') >= 0);
    });
    check("页签里两种验证方式各一个单选项（值 mobile / email）",
        login.indexOf('value="mobile"') >= 0 && login.indexOf('value="email"') >= 0);
    check("没有残留的旧 id（loginMobile* / mobileForm）",
        !/id="(loginMobile|loginPaneMobile|loginTabMobile|mobileForm)/.test(login));

    console.log("【2】登录页脚本：id 与 action 一致性");
    var ids = [];
    var idRe = /byId\("([A-Za-z0-9_]+)"\)/g;
    var m;
    while ((m = idRe.exec(login)) !== null) {
        if (ids.indexOf(m[1]) < 0) { ids.push(m[1]); }
    }
    check("脚本里至少用到了 10 个 id（确认解析到脚本了）", ids.length >= 10, "实际 " + ids.length);
    ids.forEach(function (id) {
        check("byId(\"" + id + "\") 在页面里有对应节点", login.indexOf('id="' + id + '"') >= 0);
    });
    ["login.smsSend", "login.smsLogin", "login.emailSend", "login.emailLogin", "login.settings"].forEach(function (action) {
        check("页面调 " + action + " 且控制器里有该 action",
            login.indexOf('"' + action + '"') >= 0 && controller.indexOf('"' + action + '"') >= 0);
    });
    check("控制器：邮箱开关关闭时拒绝发码 / 登录（requireEmailEnabled）",
        controller.indexOf("requireEmailEnabled") >= 0
        && controller.indexOf('getIntValue("email_enable") != 1') >= 0);
    check("控制器：邮箱换账号（requireOaUserByEmail）+ 邮箱登录写会话",
        controller.indexOf("requireOaUserByEmail") >= 0 && controller.indexOf("emailLogin(") >= 0);
    check("页签与验证方式按登录设置渲染（mobile_enable / email_enable）",
        login.indexOf("loginSettings.email_enable") >= 0
        && login.indexOf("renderVerifyChannels") >= 0
        && login.indexOf("verifyMethodRow\").style.display") >= 0);
    check("只开一种验证方式时不出现“验证方式”切换（两种都开才显示）",
        /verifyMethodRow"\)\.style\.display\s*=\s*\n?\s*\(verifyChannels\.mobile && verifyChannels\.email\)/.test(login));

    console.log("【3】登录设置页：验证登录（手机号 / 邮箱）开关");
    check("开关行叫“验证登录”", config.indexOf("<b>验证登录</b>") >= 0);
    check("有手机号勾选框 #cfgMobileEnable", config.indexOf('id="cfgMobileEnable"') >= 0);
    check("有邮箱勾选框 #cfgEmailEnable", config.indexOf('id="cfgEmailEnable"') >= 0);
    check("回显带上 email_enable", config.indexOf('setChecked("cfgEmailEnable", data.email_enable)') >= 0);
    check("保存带上 email_enable", config.indexOf("email_enable: checked(\"cfgEmailEnable\")") >= 0);
    check("前置校验含邮箱（usable 累加邮箱开关）",
        config.indexOf('(byId("cfgEmailEnable").checked ? 1 : 0)') >= 0);
    check("非维护人只读时也禁用邮箱开关（disableAll 含 cfgEmailEnable）",
        config.indexOf('"cfgEmailEnable", "cfgWecomQrEnable"') >= 0);
    check("没有残留的“手机号验证登录”开关行", config.indexOf("cfgMobileEnable\", name=\"mobile_enable\"> 开启") < 0);

    console.log("【4】弹框模式：切换页签时高度一致");
    check("CSS：弹框里的面板用公共高度 --oa-popup-pane-h 作 min-height",
        css.indexOf(".login-page-popup .login-pane { min-height:var(--oa-popup-pane-h, 0px); }") >= 0);
    check("页面：量公共高度（measurePopupPaneHeight）", login.indexOf("function measurePopupPaneHeight()") >= 0);
    check("页面：按当前可用高度重新量（applyAvailHeight 里调用）",
        /applyAvailHeight[\s\S]{0,900}measurePopupPaneHeight\(\);/.test(login));
    check("页面：设置登录方式后量一次（renderLoginMethods 里调用）",
        /switchTab\(tab\);[\s\S]{0,300}measurePopupPaneHeight\(\);/.test(login));
    check("页面：量高度期间不上报中间态（popupMeasuring 挡住 reportHeight）",
        login.indexOf("if (!CFG.popup || popupMeasuring || !window.parent") >= 0);
    check("页面：量高度时给每个面板留一行提示（.login-msg 撑一行）",
        login.indexOf('el.textContent = "\\u00a0"') >= 0);
    check("页面：只量“会显示”的页签面板", login.indexOf("function visiblePopupPanes()") >= 0);

    console.log("【5】前后端口径与落点");
    [
        ["com.customization.oaones.appsyn.service.impl.wecom.LoginSmsServiceImpl"],
        ["com.customization.oaones.appsyn.service.impl.dingtalk.LoginSmsServiceImpl"],
        ["com.customization.oaones.appsyn.service.impl.feishu.LoginSmsServiceImpl"]
    ].forEach(function (one) {
        var file = path.join(eco, "src", one[0].split(".").join("/") + ".java");
        var src = read(file);
        check(one[0] + "：有邮箱验证码能力",
            src.indexOf("requireOaUserByEmail") >= 0 && src.indexOf("sendEmailCode") >= 0
            && src.indexOf("checkEmailCode") >= 0 && src.indexOf("sendMailByPlatform") >= 0);
        // 前后端邮箱规则必须一字不差（页面先拦一次只为即时提示，服务端还会再拦一次）
        var javaPattern = /EMAIL_PATTERN =[\s\S]{0,80}?Pattern\.compile\("([^"]+)"\)/.exec(src);
        var jsPattern = /\/(\^\[A-Za-z0-9\._%\+-\][^\n]*?\$)\//.exec(login);
        // Java 源码里的 "\\." 解开一层转义后就是正则里的 "\."，与页面 JS 里的写法一致
        var javaText = javaPattern ? javaPattern[1].split("\\\\").join("\\") : "";
        check(one[0].split(".").pop() + "：邮箱格式规则与页面一致",
            javaText.length > 0 && !!jsPattern && jsPattern[1] === javaText,
            "服务端 " + javaText + " / 页面 " + (jsPattern ? jsPattern[1] : "未找到"));
    });
    check("数据库脚本：三个库各有一份 0006 增量脚本（说明版 + 纯 SQL 版）",
        ["Mysql", "SQLServer", "Oracle"].every(function (db) {
            return fs.existsSync(path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0006.sql"))
                && fs.existsSync(path.join(eco, "data", db, "oaones", "appsyn", "oaones0006.sql"));
        }));
    check("数据库脚本：0006 里既有邮箱开关、也有邮箱列与索引",
        ["Mysql", "SQLServer", "Oracle"].every(function (db) {
            var sql = read(path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0006.sql"));
            return sql.indexOf("email_enable") >= 0 && sql.indexOf("email") >= 0
                && sql.indexOf("IDX_OAONES_LOGIN_SMS_EMAIL") >= 0;
        }));
    // 两目录的编号脚本要逐条一致（说明版去掉注释 = 纯 SQL 版）：
    // Oracle 的 COMMENT ON COLUMN / ON TABLE 是**真语句**（现场执行的是纯 SQL 版，表与列注释要落到库里），
    // 所以 0006 的纯 SQL 版同样要带上那 4 条注释（2026-10-16 与 0007 一起对齐）
    check("数据库脚本：0006 的两目录语句逐条一致（Oracle 含 COMMENT ON）",
        ["Mysql", "SQLServer", "Oracle"].every(function (db) {
            var doc = stripSql(read(path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0006.sql")));
            var plain = stripSql(read(path.join(eco, "data", db, "oaones", "appsyn", "oaones0006.sql")));
            return doc.length > 0 && doc === plain;
        }));
    check("主脚本：两张表的结构已含邮箱（新库一次建齐）",
        ["Mysql", "SQLServer", "Oracle"].every(function (db) {
            var sql = read(path.join(eco, "sqlupgrade-sql", db, "oaones", "appsyn", "oaones0001.sql"));
            return sql.indexOf("email_enable") >= 0 && sql.indexOf("IDX_OAONES_LOGIN_SMS_EMAIL") >= 0;
        }));
    check("properties：邮件主题与正文模板已登记",
        read(path.join(eco, "WEB-INF", "prop", "oaones.appsyn.properties"), "binary")
            .indexOf("login.email.template") >= 0);
    check("Mapper XML：邮箱验证码能按 email 定位（三个连接器各一份）",
        ["", "dingtalk/", "feishu/"].every(function (sub) {
            var xml = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", "appsyn", sub, "LoginSmsMapper.xml"));
            return xml.indexOf("email") >= 0 && xml.indexOf("and email = #{email}") >= 0;
        }));
    check("Mapper XML：设置表能读写的列含 email_enable（三个连接器各一份）",
        ["", "dingtalk/", "feishu/"].every(function (sub) {
            var xml = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones", "appsyn", sub, "LoginConfigMapper.xml"));
            return xml.indexOf("email_enable") >= 0;
        }));

    console.log("");
    if (failed === 0) {
        console.log("全部通过 ✓");
        return;
    }
    console.log(failed + " 项未通过 ✗");
    process.exit(1);
})();
