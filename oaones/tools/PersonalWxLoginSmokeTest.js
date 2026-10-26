/**
 * 个人微信登录自检：
 *   node oaones/tools/PersonalWxLoginSmokeTest.js
 *
 * 核对设置开关、微信官方二维码入口、统一票据换身份和登录页脚本是否连成一条完整链路。
 * 包含源码检查与隔离执行登录页函数，不访问微信接口、不读写数据库。
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

    function compileInline(file, text) {
        var re = /<script>\s*(?:"use strict";)?([\s\S]*?)<\/script>/g;
        var count = 0;
        var match;
        while ((match = re.exec(text)) !== null) {
            count++;
            try {
                new vm.Script(match[1].replace(/<%=[\s\S]*?%>/g, "0"));
                check(file + " 第 " + count + " 段内联脚本可编译", true);
            } catch (e) {
                check(file + " 第 " + count + " 段内联脚本可编译", false, e.message);
            }
        }
        check(file + " 找到内联脚本", count > 0);
    }

    var platform = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "common", "QrLoginPlatform.java"));
    var configImpl = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "impl", "wecom", "LoginConfigServiceImpl.java"));
    var controller = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "controller", "LoginController.java"));
    var personal = read(path.join(eco, "src", "com", "customization", "oaones", "appsyn",
        "service", "impl", "PersonalWxServiceImpl.java"));
    var config = read(path.join(root, "loginConfig", "config.jsp"));
    var preview = read(path.join(root, "loginConfig", "preview.jsp"));
    var scan = read(path.join(root, "login", "scan.jsp"));
    var login = read(path.join(root, "login", "login.jsp"));
    var i18nSource = read(path.join(root, "js", "login.i18n.js"));
    var css = read(path.join(root, "css", "login.css"));
    var props = read(path.join(eco, "WEB-INF", "prop", "oaones.properties"));
    var scanMapper = read(path.join(eco, "WEB-INF", "config", "mapper", "oaones",
        "ScanLoginMapper.xml"));

    console.log("【1】设置页：个人微信是可生效的登录方式");
    check("个人微信行不再写“未接入”",
        config.indexOf("个人微信登录</b> <span class=\"muted\">（未接入）") < 0
        && config.indexOf("尚未接入") < 0);
    check("保存开关仍为 personal_wx_enable",
        config.indexOf('id="cfgPersonalWxEnable"') >= 0
        && config.indexOf("personal_wx_enable: checked(\"cfgPersonalWxEnable\")") >= 0);
    check("前端可用性校验包含个人微信",
        /var usable[\s\S]{0,500}cfgPersonalWxEnable[\s\S]{0,80}\? 1 : 0/.test(config));
    check("后端可用性校验包含个人微信",
        /password \+ mobile \+ email \+ wecom \+ dingtalk \+ feishu \+ personalWx == 0/.test(configImpl));
    check("密钥未配置时不允许只开个人微信而锁死登录页",
        /personalWx == 1 && !new PersonalWxServiceImpl\(\)\.configured\(\)/.test(configImpl));

    console.log("【2】登录方式判定：个人微信不再被排除");
    check("QrLoginPlatform 定义 PERSONAL_WX", platform.indexOf('String PERSONAL_WX = "personalwx"') >= 0);
    check("普通微信 UA 被识别为个人微信",
        /ua\.indexOf\("micromessenger"\)[\s\S]{0,100}return PERSONAL_WX/.test(platform));
    check("personal_wx_enable 作为个人微信开关",
        /PERSONAL_WX\.equals\(platform\)[\s\S]{0,100}return "personal_wx_enable"/.test(platform));
    check("统一公开设置下发 personal_wx_available",
        configImpl.indexOf('data.put("personal_wx_available", personalWxReady)') >= 0);
    check("qr_enable 包含可用的个人微信登录",
        /boolean qr = enterpriseQr \|\| personalWxReady/.test(configImpl));

    console.log("【3】个人微信登录后端链路");
    check("控制器有 login.personalwxQrCreate", controller.indexOf('"login.personalwxQrCreate"') >= 0);
    check("控制器把个人微信回跳分派到 PersonalWxService",
        /PERSONAL_WX\.equals\(platform\)[\s\S]{0,500}loginScanStep/.test(controller));
    check("个人微信登录复用统一扫码票据",
        personal.indexOf("public JSONObject loginQrStart(String ticket") >= 0
        && personal.indexOf("ScanLoginState.encode(scanTicket, QrLoginPlatform.PERSONAL_WX") >= 0);
    check("微信 code 换 openid 后按个人微信绑定表定位 OA 账号",
        personal.indexOf("new PersonalWxApi(appId, appSecret).exchangeCode(code)") >= 0
        && personal.indexOf("findBindByOpenid(openid)") >= 0
        && personal.indexOf("mapper.markScanned(OaonesProps.scanLoginTable(), entity)") >= 0);
    check("系统管理员与子账户不能通过个人微信登录",
        personal.indexOf("系统管理员账号不支持个人微信扫码登录") >= 0
        && personal.indexOf("子账户（次账号）不支持个人微信登录") >= 0);
    check("扫码成功后重新计算确认有效期",
        personal.indexOf("confirmExpireAt") >= 0
        && personal.indexOf("entity.setExpireTime") >= 0
        && /markScanned[\s\S]{0,1000}expire_time = #\{e\.expireTime\}/.test(scanMapper));

    console.log("【4】登录页：独立个人微信页签与官方二维码");
    check("有个人微信页签与面板",
        login.indexOf('id="loginTabPersonalWx"') >= 0
        && login.indexOf('id="loginPanePersonalWx"') >= 0);
    check("个人微信二维码来自微信官方 auth_url",
        login.indexOf('actionApi("login.personalwxQrCreate"') >= 0
        && login.indexOf('String(data.auth_url || "")') >= 0
        && login.indexOf('frame.className = "login-personal-wx-frame"') >= 0);
    var renderFrameSource = login.match(/function renderPersonalWxFrame\(url\) \{[\s\S]*?\n    \}/);
    check("可提取个人微信二维码渲染函数", !!renderFrameSource);
    if (renderFrameSource) {
        var callbackUrl = "https://oa.example.com/oaones/login/scan.jsp?from=login&lang=zh_CN";
        var authUrl = "https://open.weixin.qq.com/connect/qrconnect?appid=test_app"
            + "&redirect_uri=" + encodeURIComponent(callbackUrl)
            + "&response_type=code&scope=snsapi_login&state=ticket_personalwx"
            + "#wechat_redirect";
        var renderCases = [
            {url: authUrl, callback: callbackUrl},
            {url: authUrl.replace("#wechat_redirect",
                "&login_type=custom&self_redirect=false#wechat_redirect"), callback: callbackUrl},
            // 复现 HTTPS 登录页收到 HTTP 回调：只升级同域协议，完整保留回调路径和参数。
            {url: authUrl.replace(encodeURIComponent(callbackUrl),
                encodeURIComponent(callbackUrl.replace("https:", "http:"))), callback: callbackUrl},
            {url: authUrl.replace(encodeURIComponent(callbackUrl),
                encodeURIComponent("http://auth.example.com/oaones/login/scan.jsp?from=login")),
                callback: "http://auth.example.com/oaones/login/scan.jsp?from=login"},
            {url: authUrl.replace(encodeURIComponent(callbackUrl),
                encodeURIComponent("http://oa.example.com:8080/oaones/login/scan.jsp")),
                callback: "http://oa.example.com:8080/oaones/login/scan.jsp"},
            {url: authUrl.replace(encodeURIComponent(callbackUrl),
                encodeURIComponent(callbackUrl.replace("https:", "http:"))),
                page: "http://oa.example.com/oaones/login/login.jsp",
                callback: callbackUrl.replace("https:", "http:")}
        ];
        renderCases.forEach(function (testCase, index) {
            var url = testCase.url;
            var frame;
            var resized = 0;
            var box = {innerHTML: "old", appendChild: function (child) { frame = child; }};
            var frameSandbox = {
                URL: URL,
                window: {location: {href: testCase.page || "https://oa.example.com/oaones/login/login.jsp"}},
                byId: function () { return box; },
                document: {createElement: function () {
                    return {setAttribute: function (name, value) { this[name] = value; }};
                }},
                t: function (key) { return key; },
                esc: function (value) { return value; },
                sizePersonalWxFrame: function () { resized++; },
                personalWxAuthUrl: url
            };
            vm.runInNewContext(renderFrameSource[0] + "; renderPersonalWxFrame(personalWxAuthUrl);",
                frameSandbox);
            var result = new URL(frame.src);
            check("内嵌二维码设置 jssdk 并在 iframe 内回调（用例 " + (index + 1) + "）",
                result.searchParams.get("login_type") === "jssdk"
                && result.searchParams.get("self_redirect") === "true"
                && result.searchParams.getAll("login_type").length === 1
                && result.searchParams.getAll("self_redirect").length === 1);
            check("回调协议符合访问场景，授权上下文及快捷登录原地址不变（用例 " + (index + 1) + "）",
                result.searchParams.get("appid") === "test_app"
                && result.searchParams.get("redirect_uri") === testCase.callback
                && result.searchParams.get("response_type") === "code"
                && result.searchParams.get("scope") === "snsapi_login"
                && result.searchParams.get("state") === "ticket_personalwx"
                && result.hash === "#wechat_redirect"
                && frameSandbox.personalWxAuthUrl === url
                && frame.scrolling === "no" && resized === 1);
        });
        var emptyBox = {innerHTML: ""};
        vm.runInNewContext(renderFrameSource[0] + '; renderPersonalWxFrame("");', {
            byId: function () { return emptyBox; },
            t: function (key) { return key; },
            esc: function (value) { return value; }
        });
        check("授权地址为空时仍显示原有提示，不创建 iframe",
            emptyBox.innerHTML.indexOf("personalWxNoAuthUrl") >= 0);
    }
    check("个人微信与三个企业应用二维码分开展示",
        login.indexOf('var scan = enterpriseScanOn;') >= 0
        && login.indexOf("var personalwx = tab === \"personalwx\";") >= 0);
    check("个人微信登录仍由 PC 自己的请求换会话",
        /startPersonalWxQr[\s\S]{0,2600}startPolling\(\)/.test(login)
        && /function onQrStatus[\s\S]{0,1800}login\.qrLogin/.test(login));
    var onQrStatusSource = login.match(/function onQrStatus\(data\) \{[\s\S]*?\n    \}/);
    check("可提取电脑端扫码状态处理函数", !!onQrStatusSource);
    if (onQrStatusSource) {
        [true, false].forEach(function (loginSucceeded) {
            var calls = [];
            var statusSandbox = {
                qrTicket: "test_ticket", qrConfirmed: false,
                CFG: {landing: "/oaones/desktop", gopage: ""},
                t: function (key) { return key; },
                stopPolling: function () { calls.push("stopPolling"); },
                stopCountdown: function () { calls.push("stopCountdown"); },
                setActiveQrOverlay: function () {},
                setActiveQrMsg: function () {},
                browserKeyOf: function () { return "original_browser_key"; },
                actionApi: function (action, data) {
                    calls.push({action: action, data: data});
                    return {then: function (callback) {
                        callback(loginSucceeded ? {code: 0, data: {landing_url: "/oaones/desktop"}}
                            : {code: 1, msg: "login failed"});
                        return {catch: function () {}};
                    }};
                },
                closePersonalWxQuickWindow: function () { calls.push("closeQuickWindow"); },
                goto: function (url) { calls.push({goto: url}); }
            };
            vm.runInNewContext(onQrStatusSource[0]
                + '; onQrStatus({state: 2, login_token: "one_time_token"});', statusSandbox);
            var request = calls[2] || {};
            check("扫码确认后仍由原登录页携带 browser_key 和一次性令牌换会话（"
                + (loginSucceeded ? "成功" : "失败") + "用例）",
                request.action === "login.qrLogin" && request.data.ticket === "test_ticket"
                && request.data.browser_key === "original_browser_key"
                && request.data.token === "one_time_token" && statusSandbox.qrConfirmed);
            var redirect = calls.filter(function (call) { return call && call.goto; });
            check(loginSucceeded ? "换会话成功后才进入工作台" : "换会话失败不误跳工作台",
                loginSucceeded ? redirect.length === 1 && redirect[0].goto === "/oaones/desktop"
                    && calls[3] === "closeQuickWindow" : redirect.length === 0);
        });
    }
    check("个人微信页签不再放独立“重新生成”按钮，点击二维码即可刷新",
        login.indexOf('id="loginPersonalWxRefreshBtn"') < 0
        && login.indexOf('byId("loginPersonalWxBox").addEventListener("click"') >= 0
        && login.indexOf("startPersonalWxQr(false)") >= 0);
    check("个人微信快捷登录按钮位于二维码下方",
        login.indexOf('id="loginPersonalWxBox"') < login.indexOf('id="loginPersonalWxOpenBtn"')
        && login.indexOf('id="loginPersonalWxOpenBtn"') < login.indexOf('id="loginPersonalWxExpire"'));
    check("“在新窗口打开”改名为“快捷登录”且仍使用新窗口",
        /id="loginPersonalWxOpenBtn"[\s\S]{0,180}>快捷登录<\/button>/.test(login)
        && login.indexOf("window.open(personalWxAuthUrl, \"_blank\")") >= 0);
    check("快捷登录成功后自动关闭新窗口",
        login.indexOf("function closePersonalWxQuickWindow()") >= 0
        && /login\.qrLogin[\s\S]{0,1200}closePersonalWxQuickWindow\(\)/.test(login)
        && /window\.opener[\s\S]{0,120}window\.close\(\)/.test(scan));
    check("个人微信二维码容器有样式", css.indexOf(".login-personal-wx-frame") >= 0);
    check("扫码登录默认不做手机端二次确认",
        props.indexOf("login.qr.confirm=0") >= 0);
    check("扫码登录成功默认直接进工作台对外地址",
        props.indexOf("login.landing.url=/oaones/desktop") >= 0);
    check("扫码区只保留统一倒计时文案",
        login.indexOf('id="loginQrExpire"') >= 0
        && login.indexOf('data-i18n="qrExpireLabel"') >= 0
        && login.indexOf('data-i18n="qrExpireSuffix"') >= 0
        && login.indexOf("在手机上点“确认登录”") < 0);
    check("第三方平台扫码不再显示平台与租户数量提示",
        login.indexOf('t("qrSupports"') < 0
        && login.indexOf('t("tenantItem"') < 0
        && i18nSource.indexOf("qrSupports:") < 0
        && i18nSource.indexOf("本二维码支持：") < 0);
    check("企业二维码保持 150% 尺寸，个人微信保留 75% 缩放并使用正方形视窗",
        /\.login-qr-img \{ width:195px; height:195px;/.test(css)
        && /\.login-qr-img-box \{ position:relative; height:210px;/.test(css)
        && /\.login-qr-overlay \{[^}]*width:195px; height:195px;/.test(css)
        && /\.login-personal-wx-frame-box \{[^}]*width:225px; height:225px;/.test(css)
        && /scale\(\.75\)/.test(css));
    check("窄屏二维码和遮罩同步放大到 150%",
        /\.login-qr-img-box \{ height:187\.5px; \}/.test(css)
        && /\.login-qr-img \{ width:172\.5px; height:172\.5px; \}/.test(css)
        && /\.login-qr-overlay \{ width:172\.5px; height:172\.5px; \}/.test(css)
        && /\.login-personal-wx-frame-box \{ width:225px; height:225px; \}/.test(css));
    check("弹框二维码同步放大且保留可用高度自适应",
        css.indexOf("height:min(187.5px, calc(var(--oa-popup-avail, 100vh) * .255))") >= 0
        && css.indexOf("width:min(172.5px, calc(var(--oa-popup-avail, 100vh) * .225))") >= 0
        && css.indexOf("width:min(225px, calc(var(--oa-popup-avail, 100vh) * .285))") >= 0);
    check("个人微信官方页固定排版，仅展示二维码且不显示滚动条",
        /\.login-personal-wx-frame \{[^}]*top:-30\.75px; width:300px; height:400px;/.test(css)
        && /transform-origin:center top;/.test(css)
        && !/\.login-page-popup \.login-personal-wx-frame \{/.test(css)
        && login.indexOf('frame.setAttribute("scrolling", "no")') >= 0);
    check("普通页与弹框有效期提示恢复原色，仅平台名称使用 #05f",
        /\.login-qr-expire \{[^}]*color:var\(--oa-g-600\);/.test(css)
        && /\.login-page-popup \.login-qr-expire \{[^}]*color:var\(--oa-g-500\);/.test(css)
        && /\.login-qr-expire \.login-qr-client-text,\s*\.login-qr-expire \[data-i18n="personalWxShort"\] \{ color:#05f; \}/.test(css));
    var qrExpireFragments = login.match(/data-i18n="qrExpire(?:Seconds|Suffix)">[^<]*/g) || [];
    check("企业扫码与个人微信有效期提示均去掉平台名括号",
        qrExpireFragments.length === 4 && qrExpireFragments.every(function (text) {
            return !/[（）()]/.test(text);
        }));
    var sizeFrameSource = login.match(/function sizePersonalWxFrame\(\) \{[\s\S]*?\n    \}/);
    var sizeFramePassed = !!sizeFrameSource;
    if (sizeFrameSource) {
        [225, 142.5, 114].forEach(function (width) {
            var frame = {style: {}};
            var box = {clientWidth: 0, querySelector: function () { return frame; }};
            var sizeSandbox = {byId: function () { return box; }, window: {
                getComputedStyle: function () { return {width: width + "px"}; }
            }};
            vm.runInNewContext(sizeFrameSource[0] + "; sizePersonalWxFrame();", sizeSandbox);
            var scale = width / 300;
            // 官方二维码区域为 x=9..291、y=47..329，裁切后四边都必须完整保留。
            sizeFramePassed = sizeFramePassed
                && frame.style.transform === "translateX(-50%) scale(" + scale + ")"
                && Math.abs(parseFloat(frame.style.top) + 41 * scale) < 0.001
                && parseFloat(frame.style.top) + 47 * scale > 0
                && parseFloat(frame.style.top) + 329 * scale < width;
        });
    }
    check("普通页、小弹框及隐藏页签均等比缩放且保留完整二维码", sizeFramePassed);
    check("生成二维码、父页可用高度变化与窗口缩放均同步二维码缩放",
        /box\.appendChild\(frame\);\s*sizePersonalWxFrame\(\);/.test(login)
        && /setProperty\(POPUP_AVAIL_VAR[^;]*;\s*sizePersonalWxFrame\(\);/.test(login)
        && login.indexOf('window.addEventListener("resize", sizePersonalWxFrame)') >= 0);
    var enterprisePane = login.slice(login.indexOf('id="loginPaneScan"'),
        login.indexOf('id="loginPanePersonalWx"'));
    var personalPane = login.slice(login.indexOf('id="loginPanePersonalWx"'),
        login.indexOf('id="loginPersonalWxMsg"'));
    check("企业扫码提示仍按已启用的办公平台渲染",
        enterprisePane.indexOf('class="login-qr-client-text"') >= 0
        && enterprisePane.indexOf("oaonesQrClientText") >= 0
        && login.indexOf('oaonesQrClients.add("个人微信")') < 0);
    check("个人微信提示独立翻译且不会被办公平台名单覆盖",
        personalPane.indexOf('data-i18n="personalWxShort">个人微信</span>') >= 0
        && personalPane.indexOf("login-qr-client-text") < 0
        && personalPane.indexOf("oaonesQrClientText") < 0);
    var scanClientsSource = login.match(/function scanClients\(\) \{[\s\S]*?\n    \}/);
    var scanClientCasesPassed = !!scanClientsSource;
    if (scanClientsSource) {
        var clientSandbox = {CFG: {}, t: function (key) { return key; }};
        vm.createContext(clientSandbox);
        vm.runInContext(scanClientsSource[0], clientSandbox);
        for (var mask = 0; mask < 8; mask++) {
            [false, true].forEach(function (personalAvailable) {
                clientSandbox.CFG = {scanPlatforms: {wecom: !!(mask & 1),
                    feishu: !!(mask & 2), dingtalk: !!(mask & 4)},
                    personalWxAvailable: personalAvailable};
                var expected = [];
                if (mask & 1) { expected.push("wecomShort"); }
                if (mask & 2) { expected.push("feishuShort"); }
                if (mask & 4) { expected.push("dingtalkShort"); }
                var actual = vm.runInContext("scanClients()", clientSandbox);
                scanClientCasesPassed = scanClientCasesPassed
                    && actual === (expected.length ? expected.join(" ") : "officeClient");
            });
        }
    }
    check("16 种平台开关组合均不将个人微信混入企业扫码提示", scanClientCasesPassed);
    check("普通整页登录的四个页签使用统一最小高度",
        /\.login-pane \{ min-height:336px; \}/.test(css));
    check("登录页按服务端剩余秒数计算倒计时，避免电脑时钟偏差",
        login.indexOf("function applyQrExpiry(data)") >= 0
        && login.indexOf("Date.now() + ttl * 1000") >= 0);
    check("本地倒计时到点后仍等待服务端票据状态",
        /function startCountdown[\s\S]{0,2200}pollQrStatusOnce\(\)/.test(login)
        && !/function startCountdown[\s\S]{0,2200}stopPolling\(\)/.test(login));
    check("个人微信回调不会被页面卸载提前作废票据",
        login.indexOf('window.addEventListener("beforeunload"') < 0
        && login.indexOf("navigator.sendBeacon") < 0);
    check("扫码落地页按个人微信记录接口日志",
        scan.indexOf("OaonesApiLog.PLATFORM_PERSONAL_WX") >= 0);
    check("预览页显示个人微信登录页签",
        preview.indexOf('data-preview-tab="personalwx"') >= 0
        && preview.indexOf("methods.personalWx") >= 0);

    compileInline("config.jsp", config);
    compileInline("login.jsp", login);
    compileInline("preview.jsp", preview);
    compileInline("scan.jsp", scan);

    if (failed > 0) {
        console.log("\n" + failed + " 项未通过 ✗");
        process.exit(1);
    }
    console.log("\n全部通过 ✓");
})();
