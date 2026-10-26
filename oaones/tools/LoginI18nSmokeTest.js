/**
 * 统一登录页多语言自检：
 *   node oaones/tools/LoginI18nSmokeTest.js
 *
 * 核对登录页语种来源、切换口径、登录 / 找回密码文案资源与页面覆盖范围。
 * 只做源码与前端资源静态检查，不访问数据库或 OA 服务。
 */
(function () {
    "use strict";

    var fs = require("fs");
    var path = require("path");
    var vm = require("vm");
    var root = path.resolve(__dirname, "..");
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

    var login = read(path.join(root, "login", "login.jsp"));
    var forgot = read(path.join(root, "login", "forgotPassword.jsp"));
    var i18nSource = read(path.join(root, "js", "login.i18n.js"));
    var assets = read(path.join(root, "assets.jsp"));
    var css = read(path.join(root, "css", "login.css"));

    console.log("【1】语种来源与优先级");
    check("候选语种读取泛微 syslanguage（Util.getActiveLanguageInfo）",
        login.indexOf("Util.getActiveLanguageInfo()") >= 0);
    check("默认语种跟随泛微 defaultLanguage（LanguageService.getDefaultLang）",
        login.indexOf("LanguageService.getDefaultLang()") >= 0);
    check("登录前选择语种沿用 languageidweaver Cookie",
        login.indexOf('"languageidweaver"') >= 0
        && login.indexOf("Util.setCookie(response, \"languageidweaver\"") >= 0);
    check("URL 参数 languageid / langid / islanguid 均可识别",
        login.indexOf('getParameter("languageid")') >= 0
        && login.indexOf('getParameter("langid")') >= 0
        && login.indexOf('getParameter("islanguid")') >= 0);
    check("下拉框选项由服务端语种列表循环生成",
        login.indexOf("oaonesLanguageList.size()") >= 0
        && login.indexOf("oaonesLangEntry.getValue()") >= 0);
    check("只有一个启用语种时不显示切换框",
        login.indexOf("oaonesLanguageSwitchOn") >= 0
        && login.indexOf("oaonesLanguageList.size() > 1") >= 0);
    check("非法 / 未启用语种回退系统默认语种",
        login.indexOf("oaonesLanguageMatched") >= 0
        && login.indexOf("!oaonesLanguageMatched") >= 0);

    console.log("【2】页面切换与平台接口语种");
    check("当前语种由服务端写入页面配置",
        login.indexOf('languageId: "<%=oaonesLanguageId%>"') >= 0);
    check("登录页脚本不再固定简体中文",
        login.indexOf('var LANG_ID = "7"') < 0
        && login.indexOf("var LANG_ID = String(CFG.languageId") >= 0);
    check("切换时写 languageidweaver Cookie",
        login.indexOf('document.cookie = "languageidweaver="') >= 0);
    check("切换时保留地址参数并刷新重新初始化页面",
        login.indexOf("next.searchParams.set(\"languageid\", id)") >= 0
        && login.indexOf("window.location.replace(next.toString())") >= 0);
    check("AJAX 统一登录接口带 languageId",
        login.indexOf("var requestParams = {languageId: LANG_ID};") >= 0);
    check("平台登录表单沿用所选 langid",
        login.indexOf("logintype: \"1\", langid: LANG_ID") >= 0);
    check("平台登录校验沿用所选 islanguid",
        login.indexOf("islanguid: LANG_ID") >= 0);
    check("弹框完整页与新窗口入口保留语种",
        login.indexOf("/oaones/login/login.jsp?languageid=") >= 0);
    check("忘记密码 iframe 同时传 languageid / langid",
        login.indexOf("languageid=") >= 0 && login.indexOf("&langid=") >= 0);

    console.log("【3】登录页静态与动态文案覆盖");
    var loginTags = login.match(/data-i18n(?:-placeholder|-title|-aria-label|-alt)?=/g) || [];
    check("登录页标记了足够多的可翻译文案", loginTags.length >= 35, "实际 " + loginTags.length);
    check("右上角语言标签固定显示英文 Language",
        /<label for="loginLanguage">Language<\/label>/.test(login)
        && !/<label for="loginLanguage"[^>]*data-i18n=/.test(login));
    check("页签、表单、二维码、个人微信与找回弹层均接入",
        ["passwordTab", "verifyTab", "scanTab", "personalWxTab", "qrExpireLabel",
            "personalWxQuick", "forgotTitle"].every(function (key) {
            return login.indexOf('data-i18n="' + key + '"') >= 0;
        }));
    var loginHtml = login.replace(/<%[\s\S]*?%>/g, "");
    var passwordCaptchaInput = loginHtml.match(/<input\b[^>]*\bid="validatecode"[^>]*>/);
    check("账号密码验证码输入框不显示提示语，语种初始化也不会重新填入",
        !!passwordCaptchaInput && !/\bplaceholder\s*=|data-i18n-placeholder/.test(passwordCaptchaInput[0]));
    check("账号密码验证码仍保留标签、输入长度限制和点击换图入口",
        login.indexOf('<label for="validatecode" data-i18n="captcha">') >= 0
        && login.indexOf('maxlength="<%=oaonesCaptchaNum > 0 ? oaonesCaptchaNum : 10%>"') >= 0
        && login.indexOf('id="loginCaptchaImg"') >= 0
        && login.indexOf('data-i18n-title="refreshCaptchaTitle"') >= 0);
    check("扫码平台名称随语种生成", login.indexOf("function scanClients()") >= 0
        && login.indexOf('t("wecomShort")') >= 0
        && login.indexOf('t("feishuShort")') >= 0
        && login.indexOf('t("dingtalkShort")') >= 0);
    check("动态提示改走 t(...) 资源", login.indexOf('"qrExpired"') >= 0
        && login.indexOf('"loginFailedCheck"') >= 0
        && login.indexOf('"mobileInvalid"') >= 0
        && login.indexOf('"emailInvalid"') >= 0);
    check("找回密码页读取服务端语种",
        forgot.indexOf('getParameter("languageid")') >= 0
        && forgot.indexOf('getParameter("langid")') >= 0
        && forgot.indexOf('"languageidweaver"') >= 0);
    check("找回密码页静态与动态文案均接入资源",
        forgot.indexOf('data-i18n="fpStepIdentity"') >= 0
        && forgot.indexOf('t("fpCodeSentMobile"') >= 0
        && forgot.indexOf('t("fpPasswordMismatch")') >= 0);

    var expireTranslations = i18nSource.match(/qrExpire(?:Seconds|Suffix): "[^"\n]*"/g) || [];
    check("简体、繁体及英文二维码有效期文案均不包含平台名括号",
        expireTranslations.length === 6 && expireTranslations.every(function (text) {
            return !/[（）()]/.test(text);
        }));

    console.log("【4】前端语种资源");
    var sandbox = {
        window: null,
        document: {
            documentElement: {},
            body: { getAttribute: function () { return ""; } },
            querySelectorAll: function () { return []; }
        }
    };
    sandbox.window = sandbox;
    vm.createContext(sandbox);
    vm.runInContext(i18nSource, sandbox);
    var i18n = sandbox.OaonesLoginI18n;
    check("导出 OaonesLoginI18n", !!i18n);
    var samples = ["passwordTab", "loginButton", "qrExpireLabel", "remember",
        "fpStepIdentity", "fpSavePassword"];
    ["7", "8", "9"].forEach(function (lang) {
        i18n.setLanguage(lang);
        check("语种 " + lang + " 的核心文案有翻译",
            samples.every(function (key) { return i18n.t(key) !== key; }));
    });
    i18n.setLanguage("8");
    check("英文登录按钮与页签",
        i18n.t("loginButton") === "Sign in"
        && i18n.t("passwordTab") === "Account sign-in");
    i18n.setLanguage("9");
    check("繁体中文登录页签", i18n.t("passwordTab") === "帳號密碼登入");
    i18n.setLanguage("12");
    check("系统启用但暂无资源的语种回退英文，不显示资源键名",
        i18n.t("passwordTab") === "Account sign-in");
    check("字面量 t(\"key\") 在所有已提供资源中都有值", (function () {
        var keys = new Set();
        [login, forgot].forEach(function (source) {
            var re = /\bt\("([A-Za-z0-9_]+)"/g;
            var match;
            while ((match = re.exec(source)) !== null) { keys.add(match[1]); }
        });
        return ["7", "8", "9"].every(function (lang) {
            i18n.setLanguage(lang);
            var ok = true;
            keys.forEach(function (key) {
                if (i18n.t(key) === key) { ok = false; }
            });
            return ok;
        });
    })());
    check("资源已被静态缓存版本号链覆盖",
        assets.indexOf("/oaones/js/login.i18n.js") >= 0);
    check("登录页与找回页都引入同一份资源",
        login.indexOf("login.i18n.js") >= 0 && forgot.indexOf("login.i18n.js") >= 0);
    check("语种切换样式包含弹框与窄屏适配",
        css.indexOf(".login-language") >= 0
        && css.indexOf(".login-page-popup .login-language") >= 0
        && css.indexOf("@media (max-width: 860px)") >= 0);

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
        return;
    }
    console.log("\n全部通过 ✓");
})();
