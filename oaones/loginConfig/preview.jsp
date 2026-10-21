<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSON" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesProps" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ include file="/oaones/assets.jsp" %>
<%
    /*
     * 「登录设置 → 效果预览」的 iframe 内容。
     *
     * 设置页只保留**一个**整页预览（config.jsp 顶部），所有与登录页外观有关的设置都渲染到这里：
     * 左侧文字 / 左侧文字背景透明度 / Logo / 整页背景图 / 登录框位置与尺寸 /
     * 登录框透明度 / 登录方式页签。
     * 预览不读数据库、也不写任何配置：设置页把当前表单值（含尚未保存的修改）通过
     * OaonesLoginPreview.apply(...) 传进来，这里只负责画出来。
     *
     * 版式直接复用 css/login.css 的真实类名，避免预览与实际登录页出现两套观感；
     * 页面里不放表单提交、二维码生成等真实登录逻辑。
     */
    String oaonesPreviewTitle = OaonesUtil.s(OaonesProps.loginPageTitle());
    if (oaonesPreviewTitle.length() == 0) {
        oaonesPreviewTitle = "OA 协同办公系统";
    }
%>
<!DOCTYPE html>
<html lang="zh-CN" class="login-preview-root">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>登录页效果预览</title>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <link rel="stylesheet" href="/oaones/css/login.css?v=<%=oaonesAssetV%>">
</head>
<body class="login-page login-preview-frame login-page-default">
<div class="login-wrap">
    <div class="login-brand" id="previewBrand">
        <div class="login-brand-html" id="previewBrandHtml"></div>
    </div>
    <div class="login-card" id="previewCard">
        <div class="login-logo" id="previewLogo" style="display:none;">
            <img id="previewLogoImg" alt="登录页 Logo">
        </div>
        <div class="login-tabs" id="previewTabs">
            <button type="button" class="login-tab active" data-preview-tab="pwd">账号密码登录</button>
            <button type="button" class="login-tab" data-preview-tab="verify">验证登录</button>
            <button type="button" class="login-tab" data-preview-tab="scan">扫码登录</button>
        </div>
        <div class="login-pane" data-preview-pane="pwd">
            <div class="login-field">
                <label>账号</label>
                <input type="text" value="" placeholder="请输入登录名" readonly tabindex="-1">
            </div>
            <div class="login-field">
                <label>密码</label>
                <input type="password" value="" placeholder="请输入密码" readonly tabindex="-1">
            </div>
            <div class="login-row">
                <label class="login-remember">
                    <input type="checkbox" tabindex="-1"> 记住账户密码
                </label>
                <a class="login-link" href="#" onclick="return false;">忘记密码</a>
            </div>
            <button type="button" class="btn login-submit" tabindex="-1">登 录</button>
        </div>
        <div class="login-pane" data-preview-pane="verify" style="display:none;">
            <div class="login-field" id="previewVerifyMethodRow">
                <label>验证方式</label>
                <div class="login-methods">
                    <label class="login-method"><input type="radio" name="previewVerifyChannel"
                           value="mobile" checked tabindex="-1"> 手机号</label>
                    <label class="login-method"><input type="radio" name="previewVerifyChannel"
                           value="email" tabindex="-1"> 邮箱</label>
                </div>
            </div>
            <div class="login-field">
                <label id="previewVerifyTargetLabel">手机号</label>
                <input type="text" id="previewVerifyTarget" placeholder="请输入在 OA 里维护的手机号"
                       readonly tabindex="-1">
            </div>
            <div class="login-field">
                <label id="previewVerifyCodeLabel">手机验证码</label>
                <div class="login-captcha">
                    <input type="text" placeholder="请输入短信验证码" readonly tabindex="-1">
                    <button type="button" class="btn secondary" tabindex="-1">获取验证码</button>
                </div>
            </div>
            <button type="button" class="btn login-submit" tabindex="-1">登 录</button>
        </div>
        <div class="login-pane" data-preview-pane="scan" style="display:none;">
            <div class="login-qr-box">
                <div class="login-qr-img-box">
                    <div class="login-preview-qr">登录二维码</div>
                </div>
                <p class="login-qr-tip" id="previewScanTip">用手机扫描二维码并在手机上确认登录</p>
            </div>
        </div>
        <div class="login-preview-pane-empty" id="previewEmpty" style="display:none;">
            至少开启一种登录方式后，登录框里才会出现对应页签
        </div>
    </div>
</div>
<script>
"use strict";
(function () {
    var TITLE = <%=JSON.toJSONString(oaonesPreviewTitle)%>;
    var activePane = "pwd";
    var last = {};

    function byId(id) { return document.getElementById(id); }

    /** 与后端 normalizeBrandHtml / 登录页同一个口径：只有空标记 / &nbsp; 视为“没设置”。 */
    function brandHasText(html) {
        return String(html || "")
            .replace(/&nbsp;/g, "").replace(/\u00a0/g, "")
            .replace(/&#160;|&#xa0;/gi, "")
            .replace(/<[^>]*>/g, "").trim().length > 0;
    }

    /** 没有自定义左侧文字时，按登录页的系统默认说明输出（标题 + 已开启方式清单）。 */
    function renderDefaultBrand(methods, scanText) {
        var box = byId("previewBrandHtml");
        box.innerHTML = "";

        var title = document.createElement("h1");
        title.textContent = TITLE;
        box.appendChild(title);

        var desc = document.createElement("p");
        desc.className = "login-brand-text";
        desc.textContent = "请选择下方任一已开启的方式登录（具体可用的方式由管理员在“登录设置”里决定）。";
        box.appendChild(desc);

        var list = document.createElement("ul");
        list.className = "login-brand-list";
        if (methods.password) {
            addDefaultItem(list, "账号密码", "：与平台登录同一套校验（密码策略、登录验证码、二次认证均按“安全设置”执行）");
        }
        if (methods.mobile || methods.email) {
            var target = methods.mobile && methods.email ? "手机号 / 邮箱" : (methods.email ? "邮箱" : "手机号");
            var code = methods.mobile && methods.email ? "短信 / 邮件" : (methods.email ? "邮件" : "短信");
            addDefaultItem(list, "验证登录", "：填 OA 里维护的" + target + "，收到" + code + "验证码后登录");
        }
        if (methods.scan) {
            addDefaultItem(list, "扫码登录", "：用本人的" + (scanText || "企业微信 / 钉钉 / 飞书")
                + "扫同一张二维码并在手机上确认，需先在“个人账号中心”绑定本人 OA 账号");
        }
        if (list.childNodes.length > 0) {
            box.appendChild(list);
        }
    }

    function addDefaultItem(list, label, text) {
        var li = document.createElement("li");
        var b = document.createElement("b");
        b.textContent = label;
        li.appendChild(b);
        li.appendChild(document.createTextNode(text));
        list.appendChild(li);
    }

    function setPane(next) {
        var tabs = byId("previewTabs").querySelectorAll("[data-preview-tab]");
        var panes = document.querySelectorAll("[data-preview-pane]");
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].className = "login-tab" + (tabs[i].getAttribute("data-preview-tab") === next ? " active" : "");
        }
        for (var j = 0; j < panes.length; j++) {
            panes[j].style.display = panes[j].getAttribute("data-preview-pane") === next ? "" : "none";
        }
        activePane = next;
    }

    /** 设置页每次表单变化都会调用：只改画面，不触发任何请求。 */
    function apply(settings) {
        settings = settings || {};
        var methods = settings.methods || {};
        var body = document.body;
        var brandOpacity = Number(settings.brandOpacity);
        if (!isFinite(brandOpacity)) {
            brandOpacity = 100;
        }
        brandOpacity = Math.min(100, Math.max(0, Math.round(brandOpacity)));

        body.className = "login-page login-preview-frame"
            + (settings.custom ? " login-page-custom" : " login-page-default")
            + (settings.brandCustom ? " login-page-brand-custom" : "")
            + (brandOpacity < 100 ? " login-page-brand-surface" : "");
        if (settings.bg) {
            body.style.setProperty("--oa-login-bg", "url('" + String(settings.bg).replace(/'/g, "%27") + "')");
        } else {
            body.style.removeProperty("--oa-login-bg");
        }
        if (settings.custom) {
            body.style.setProperty("--oa-login-card-left", Math.round(Number(settings.left) || 0) + "px");
            body.style.setProperty("--oa-login-card-top", Math.round(Number(settings.top) || 0) + "px");
            body.style.setProperty("--oa-login-card-width", Math.round(Number(settings.width) || 400) + "px");
            body.style.setProperty("--oa-login-card-height", Math.round(Number(settings.height) || 560) + "px");
        } else {
            body.style.removeProperty("--oa-login-card-left");
            body.style.removeProperty("--oa-login-card-top");
            body.style.removeProperty("--oa-login-card-width");
            body.style.removeProperty("--oa-login-card-height");
        }
        if (settings.brandCustom) {
            body.style.setProperty("--oa-login-brand-left", Math.round(Number(settings.brandLeft) || 0) + "px");
            body.style.setProperty("--oa-login-brand-top", Math.round(Number(settings.brandTop) || 0) + "px");
            body.style.setProperty("--oa-login-brand-width",
                Math.round(Number(settings.brandWidth) || 504) + "px");
            body.style.setProperty("--oa-login-brand-height",
                Math.round(Number(settings.brandHeight) || 240) + "px");
        } else {
            body.style.removeProperty("--oa-login-brand-left");
            body.style.removeProperty("--oa-login-brand-top");
            body.style.removeProperty("--oa-login-brand-width");
            body.style.removeProperty("--oa-login-brand-height");
        }
        if (brandOpacity < 100) {
            body.style.setProperty("--oa-login-brand-alpha", (1 - brandOpacity / 100).toFixed(3));
        } else {
            body.style.removeProperty("--oa-login-brand-alpha");
        }
        var opacity = Math.min(100, Math.max(0, Math.round(Number(settings.opacity) || 0)));
        if (opacity > 0) {
            body.style.setProperty("--oa-login-card-alpha", (1 - opacity / 100).toFixed(3));
        } else {
            body.style.removeProperty("--oa-login-card-alpha");
        }

        if (settings.logo) {
            byId("previewLogoImg").src = settings.logo;
            byId("previewLogo").style.display = "";
        } else {
            byId("previewLogoImg").removeAttribute("src");
            byId("previewLogo").style.display = "none";
        }

        if (brandHasText(settings.brandHtml)) {
            if (last.brandHtml !== settings.brandHtml) {
                byId("previewBrandHtml").innerHTML = settings.brandHtml;
            }
            last.usedDefault = false;
        } else {
            var defaultKey = JSON.stringify(methods) + "|" + (settings.scanText || "");
            if (!last.usedDefault || last.defaultBrandKey !== defaultKey) {
                renderDefaultBrand(methods, settings.scanText);
            }
            last.usedDefault = true;
        }
        last.brandHtml = settings.brandHtml || "";
        last.defaultBrandKey = JSON.stringify(methods) + "|" + (settings.scanText || "");

        var showPwd = !!methods.password;
        var showVerify = !!(methods.mobile || methods.email);
        var showScan = !!methods.scan;
        var any = showPwd || showVerify || showScan;
        byId("previewTabs").style.display = any ? "" : "none";
        byId("previewEmpty").style.display = any ? "none" : "";
        byId("previewTabs").querySelector('[data-preview-tab="pwd"]').style.display = showPwd ? "" : "none";
        byId("previewTabs").querySelector('[data-preview-tab="verify"]').style.display = showVerify ? "" : "none";
        byId("previewTabs").querySelector('[data-preview-tab="scan"]').style.display = showScan ? "" : "none";

        byId("previewVerifyMethodRow").style.display = methods.mobile && methods.email ? "" : "none";
        var verifyEmailOnly = !methods.mobile && methods.email;
        byId("previewVerifyTargetLabel").textContent = verifyEmailOnly ? "邮箱" : "手机号";
        byId("previewVerifyTarget").placeholder = verifyEmailOnly
            ? "请输入在 OA 里维护的邮箱" : "请输入在 OA 里维护的手机号";
        byId("previewVerifyCodeLabel").textContent = verifyEmailOnly ? "邮箱验证码" : "手机验证码";
        byId("previewScanTip").textContent = "请用本人的" + (settings.scanText || "企业微信 / 钉钉 / 飞书")
            + "扫一扫扫描上方二维码，在手机上点“确认登录”后本页会自动进入 OA";

        var visible = [];
        if (showPwd) { visible.push("pwd"); }
        if (showVerify) { visible.push("verify"); }
        if (showScan) { visible.push("scan"); }
        if (any) {
            setPane(visible.indexOf(activePane) >= 0 ? activePane : visible[0]);
        } else {
            var panes = document.querySelectorAll("[data-preview-pane]");
            for (var i = 0; i < panes.length; i++) {
                panes[i].style.display = "none";
            }
        }
    }

    // 预览里的页签可以点：管理员能直接核对三种登录方式的版式（不会真的登录）
    byId("previewTabs").addEventListener("click", function (e) {
        var tab = e.target && e.target.closest ? e.target.closest("[data-preview-tab]") : null;
        if (!tab || tab.style.display === "none") { return; }
        setPane(tab.getAttribute("data-preview-tab"));
    });

    window.OaonesLoginPreview = { apply: apply };
})();
</script>
</body>
</html>
