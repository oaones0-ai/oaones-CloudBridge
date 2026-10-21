<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="weaver.general.Util" %>
<%@ include file="/oaones/assets.jsp" %>
<%
    // 找回密码弹层页（由统一登录页 /oaones/login/login.jsp 的“忘记密码”按钮以 iframe 引入）。
    //
    // 只做一件事：帮忘记密码的用户自助重设密码。**不校验登录会话**（它就是给未登录用户用的）。
    // 全流程都调**平台自己的“忘记密码”接口**（/api/hrm/forgotPassword/*），本页只做界面与步骤编排：
    // 不自己收发短信 / 邮件、不自己校验验证码、不自己判断密码规则，避免出现第二套口径。
    //
    // 三步（与平台登录页的“找回密码”弹层完全同一套接口与参数）：
    //   ① 身份校验：登录名 + 图形验证码 + 找回方式（短信 / 邮件 / 密保问题）
    //      → forgotPasswordCheckMsg 认出账号 → forgotPasswordToNext 由平台发短信 / 邮件
    //   ② 验证：短信码 / 邮件码（可重发，倒计时取平台回的 forgotpasswordExpiration；这一档还要再输
    //      一次图形验证码）或密保问题答案 → confirmPhoneCodeToNext / confirmEmailCodeToNext /
    //      passwordQuestionToNext
    //   ③ 重设密码：两次一致 + 平台回的密码规则（minpasslen / passwordComplexity）+ 弱密码校验
    //      （/api/hrm/password/isWeakPassword），平台开了 RSA（openRSA=1）时按平台口径加密后提交
    //      → saveResetPassword
    //
    // 图形验证码取平台 /weaver/weaver.file.MakeValidateCode，且必须带 from=_forgort：
    // 平台把“忘记密码”的验证码单独存在会话 validateRand_forgort 里，与登录验证码互不干扰；
    // 固定 4 位数字（isView=1&validatetype=0&validatenum=4），与平台自己的找回弹层一致。
    //
    // 后台没开“忘记密码功能”（组织权限中心 - 账户中心 - 安全设置 - 基本设置 →“登录设置”→
    // “启用忘记密码功能”）时，平台接口直接返回“忘记密码功能未启用”，本页原样显示，不绕过该开关。
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    int oaonesFpLang = Util.getIntValue(request.getParameter("langid"), 7);
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>找回密码</title>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <link rel="stylesheet" href="/oaones/css/login.css?v=<%=oaonesAssetV%>">
</head>
<body class="fp-page">

<div class="fp-wrap">
    <div class="fp-step-tip" id="fpSteps">
        <span class="fp-step active" id="fpStepTag1">1 身份校验</span>
        <span class="fp-step" id="fpStepTag2">2 验证身份</span>
        <span class="fp-step" id="fpStepTag3">3 重设密码</span>
    </div>

    <%-- ① 身份校验：登录名 + 图形验证码 + 找回方式（找回方式由平台按“忘记密码”设置下发） --%>
    <div class="fp-step-pane" id="fpPane1">
        <div class="login-field">
            <label for="fp1_loginid">登录名</label>
            <input type="text" id="fp1_loginid" data-fpkey="loginid" autocomplete="username"
                   data-lpignore="true" placeholder="请输入登录名 / 编号 / 手机号 / 企业邮箱" maxlength="100">
        </div>
        <div class="login-field">
            <label for="fp1_type">找回方式</label>
            <select id="fp1_type" data-fpkey="type">
                <option value="">加载中…</option>
            </select>
        </div>
        <div class="login-field">
            <label for="fp1_validatecode">验证码</label>
            <div class="login-captcha">
                <input type="text" id="fp1_validatecode" data-fpkey="validatecode" autocomplete="off"
                       data-lpignore="true" placeholder="请输入验证码" maxlength="10">
                <img id="fp1CodeImg" alt="验证码" title="看不清？点击换一张">
            </div>
        </div>
        <div class="fp-ops">
            <button type="button" class="btn" id="fpNext1">下一步</button>
            <button type="button" class="btn secondary" id="fpCancel1">取消</button>
        </div>
    </div>

    <%-- ② 验证身份：短信 / 邮件验证码（可重发）或密保问题，字段由平台下发的表单定义渲染 --%>
    <div class="fp-step-pane" id="fpPane2" style="display:none;">
        <p class="fp-tip" id="fpSendTip"></p>
        <div id="fpCheckFields"></div>
        <div class="fp-ops">
            <button type="button" class="btn" id="fpNext2">验证</button>
            <button type="button" class="btn secondary" id="fpRestart2">重新开始</button>
        </div>
    </div>

    <%-- ③ 重设密码：两次输入一致 + 平台下发的密码规则 + 弱密码校验 --%>
    <div class="fp-step-pane" id="fpPane3" style="display:none;">
        <p class="fp-tip" id="fpRuleTip"></p>
        <div id="fpPwdFields"></div>
        <div class="fp-ops">
            <button type="button" class="btn" id="fpSave">保存新密码</button>
            <button type="button" class="btn secondary" id="fpCancel3">取消</button>
        </div>
    </div>

    <%-- 成功：提示用新密码登录，并通知外层的登录页关掉弹层 --%>
    <div class="fp-step-pane" id="fpPane4" style="display:none;">
        <p class="fp-tip ok" id="fpDoneTip">密码已重置，请用新密码登录。</p>
        <div class="fp-ops">
            <button type="button" class="btn" id="fpDone">返回登录</button>
        </div>
    </div>

    <%-- 平台没开“启用忘记密码功能”时：不摆一个填不了的死表单，直接给出原因与下一步 --%>
    <div class="fp-step-pane" id="fpUnavail" style="display:none;">
        <p class="fp-tip fail" id="fpUnavailTip"></p>
        <div class="fp-ops">
            <button type="button" class="btn" id="fpUnavailClose">关闭</button>
        </div>
    </div>

    <p class="login-msg" id="fpMsg"></p>
</div>

<script>
"use strict";
(function () {
    /** 平台“忘记密码”接口前缀（与平台登录页的找回弹层同一套接口） */
    var API_BASE = "/api/hrm/forgotPassword/";
    /** 弱密码校验（平台口径：弱密码是否允许保存由它回） */
    var API_WEAK = "/api/hrm/password/isWeakPassword";
    /**
     * 图形验证码：必须带 from=_forgort（平台把找回密码的验证码单独存会话 validateRand_forgort，
     * 与登录验证码分开），固定 4 位数字，口径与平台自己的找回弹层完全一致。
     */
    var CODE_URL = "/weaver/weaver.file.MakeValidateCode"
        + "?from=_forgort&isView=1&validatetype=0&validatenum=4&seriesnum_=";
    /** 当前语言（平台按它回提示文案） */
    var LANG_ID = "<%=oaonesFpLang%>";

    /** 本次找回的上下文：账号 id、找回方式、验证码掩码后的收件人、重发倒计时与 RSA 开关 */
    var state = {id: "", type: "", target: "", ttl: 60, left: 0, timer: 0, openRSA: "0"};
    /** 三步里各自提交过的表单值（与平台一样按“后一步覆盖前一步”合并后提交） */
    var form1 = {}, form2 = {}, form3 = {};
    /** 新密码规则（平台 resetPasswordForm 回的 otherParams）与弱密码拦截开关 */
    var pwdRule = {minpasslen: 1, passwordComplexity: "0"};

    function byId(id) { return document.getElementById(id); }
    function val(id) {
        var el = byId(id);
        return el ? String(el.value == null ? "" : el.value).replace(/^\s+|\s+$/g, "") : "";
    }
    function esc(v) {
        return String(v == null ? "" : v).replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }
    function setMsg(text, mode) {
        var el = byId("fpMsg");
        el.className = "login-msg" + (mode ? " " + mode : "");
        el.textContent = text || "";
    }
    function clearMsg() { setMsg("", ""); }
    function merge(target, from) {
        var out = target || {};
        Object.keys(from || {}).forEach(function (k) {
            if (from[k] !== undefined && from[k] !== null) { out[k] = from[k]; }
        });
        return out;
    }

    /* ------------------------------------------------------------------
     * 通用请求：平台“忘记密码”接口都是 POST 表单参数 + JSON 返回
     * （{status:"1"} 成功、{status:"-1", message:"..."} 失败，message 直接用平台的文案）
     * ------------------------------------------------------------------ */
    function post(url, params) {
        var body = [];
        Object.keys(params || {}).forEach(function (k) {
            body.push(encodeURIComponent(k) + "="
                + encodeURIComponent(params[k] == null ? "" : params[k]));
        });
        return fetch(url, {
            method: "POST",
            credentials: "same-origin",
            headers: {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"},
            body: body.join("&")
        }).then(function (r) {
            if (!r.ok) { throw new Error("HTTP " + r.status + " " + url); }
            return r.json();
        });
    }
    function api(name, params) {
        return post(API_BASE + name, merge({languageId: LANG_ID}, params));
    }
    /** 平台约定：status 为字符串 "1" 才算成功 */
    function ok(res) { return !!res && String(res.status) === "1"; }
    /** 失败时把平台给的文案显示出来（没有就退化成本步骤的通用文案） */
    function fail(res, fallback) {
        setMsg((res && res.message) || fallback || "操作失败，请稍后重试", "fail");
    }

    /* ------------------------------------------------------------------
     * 步骤切换与按钮置灰
     * ------------------------------------------------------------------ */
    function showStep(n) {
        [1, 2, 3, 4].forEach(function (i) {
            var pane = byId("fpPane" + i);
            if (pane) { pane.style.display = i === n ? "" : "none"; }
            var tag = byId("fpStepTag" + i);
            if (tag) {
                tag.className = "fp-step" + (i === n ? " active" : (i < n ? " done" : ""));
                tag.style.display = i === 4 ? "none" : "";
            }
        });
        byId("fpSteps").style.display = n === 4 ? "none" : "";
        byId("fpUnavail").style.display = "none";
    }
    /** 整个找回不可用（平台没开“启用忘记密码功能”，或没有任何可用找回方式）时的整页提示 */
    function showUnavailable(text) {
        [1, 2, 3, 4].forEach(function (i) {
            var pane = byId("fpPane" + i);
            if (pane) { pane.style.display = "none"; }
        });
        byId("fpSteps").style.display = "none";
        byId("fpUnavailTip").textContent = text;
        byId("fpUnavail").style.display = "";
        setMsg("", "");
    }
    function busy(on) {
        ["fpNext1", "fpNext2", "fpSave", "fpRestart2"].forEach(function (id) {
            var el = byId(id);
            if (el) { el.disabled = !!on; }
        });
    }
    /** 换一张图形验证码（进入每一步都换，平台的验证码是一次性的） */
    function refreshCode() {
        var img = byId("fp1CodeImg");
        if (img) { img.src = CODE_URL + new Date().getTime(); }
    }
    function stopTimer() {
        if (state.timer) { clearInterval(state.timer); state.timer = 0; }
    }
    /** 告诉外层的统一登录页：关掉弹层（找回成功后另发一条 done） */
    function notifyParent(kind) {
        try {
            if (window.parent && window.parent !== window) {
                window.parent.postMessage({oaonesForgotPassword: kind}, location.origin);
                return;
            }
        } catch (e) { /* 跨域时忽略：下面按“直接打开本页”处理 */ }
        if (kind === "done" || kind === "close") {
            // 直接打开本页（不是弹层里的 iframe）时，回到统一登录页
            location.href = "/oaones/login";
        }
    }

    /* ------------------------------------------------------------------
     * 平台下发的表单定义：conditions → 条目（domkey / label / conditionType / options）
     * 本页只按条目里的字段名渲染我们自己的输入框，值仍按平台口径提交
     * ------------------------------------------------------------------ */
    function itemsOf(conditions) {
        var out = [];
        (conditions || []).forEach(function (group) {
            (group && group.items ? group.items : []).forEach(function (item) {
                if (item && item.domkey && item.domkey.length > 0) { out.push(item); }
            });
        });
        return out;
    }
    function findItem(conditions, key) {
        var found = null;
        itemsOf(conditions).forEach(function (item) {
            if (!found && item.domkey[0] === key) { found = item; }
        });
        return found;
    }
    /** 下拉项：平台回的是 {key, showname}（兼容 value/label 写法） */
    function optionPairs(item) {
        var list = (item && (item.options && item.options.length ? item.options : item.valueList)) || [];
        var out = [];
        list.forEach(function (o) {
            if (!o) { return; }
            var v = (o.key !== undefined && o.key !== null) ? o.key
                : (o.value !== undefined && o.value !== null ? o.value : o.id);
            var t = (o.showname !== undefined && o.showname !== null) ? o.showname
                : (o.label !== undefined && o.label !== null ? o.label : o.name);
            out.push({value: String(v == null ? "" : v), text: String(t == null ? "" : t)});
        });
        return out;
    }

    /**
     * 按平台下发的条目渲染输入区。
     *
     * <p>元素 id 统一加本步前缀（如 fp2_phoneCode），避免不同步骤的同名字段撞 id；
     * 提交时只看 data-fpkey（那就是平台要的字段名）。</p>
     */
    function renderItems(boxId, prefix, conditions, needResend) {
        var box = byId(boxId);
        var items = itemsOf(conditions);
        var html = "";
        items.forEach(function (item) {
            var key = item.domkey[0];
            var label = item.label ? String(item.label) : key;
            var inputId = prefix + "_" + key;
            html += '<div class="login-field"><label for="' + esc(inputId) + '">'
                + esc(label) + "</label>";
            if (item.conditionType === "SELECT") {
                html += '<select id="' + esc(inputId) + '" data-fpkey="' + esc(key) + '">';
                optionPairs(item).forEach(function (o) {
                    html += '<option value="' + esc(o.value) + '">' + esc(o.text) + "</option>";
                });
                html += "</select>";
            } else if (key === "validatecode") {
                // 短信 / 邮件这一档要再输一次图形验证码：平台会给这张码图单独发一张新的
                html += '<div class="login-captcha">'
                    + '<input type="text" id="' + esc(inputId) + '" data-fpkey="validatecode"'
                    + ' autocomplete="off" data-lpignore="true" placeholder="请输入验证码" maxlength="10">'
                    + '<img id="' + esc(prefix) + 'Code" class="fp-inline-code" alt="验证码"'
                    + ' title="看不清？点击换一张"></div>';
            } else if (key === "phoneCode" || key === "emailCode") {
                html += '<div class="login-captcha">'
                    + '<input type="text" id="' + esc(inputId) + '" data-fpkey="' + esc(key) + '"'
                    + ' autocomplete="off" data-lpignore="true" placeholder="请输入收到的验证码" maxlength="10">'
                    + '<button type="button" class="btn secondary" id="' + esc(prefix) + 'Resend"'
                    + (needResend ? "" : ' style="display:none;"') + ">重新发送</button></div>";
            } else if (key === "newpswd" || key === "cfmpswd") {
                html += '<input type="password" id="' + esc(inputId) + '" data-fpkey="' + esc(key) + '"'
                    + ' autocomplete="new-password" data-lpignore="true" maxlength="100"'
                    + ' placeholder="请输入新密码">';
            } else {
                html += '<input type="text" id="' + esc(inputId) + '" data-fpkey="' + esc(key) + '"'
                    + ' autocomplete="off" data-lpignore="true" maxlength="100">';
            }
            html += "</div>";
        });
        box.innerHTML = html;
        // 本步带图形验证码时给它一张新码，并支持点图换一张
        var codeImg = box.querySelector(".fp-inline-code");
        if (codeImg) {
            codeImg.src = CODE_URL + new Date().getTime();
            codeImg.addEventListener("click", function () { codeImg.src = CODE_URL + new Date().getTime(); });
        }
    }
    /** 取一个容器里所有带 data-fpkey 的输入值 */
    function formParamsOf(boxId) {
        var out = {};
        var box = byId(boxId);
        Array.prototype.forEach.call(box.querySelectorAll("[data-fpkey]"), function (el) {
            out[el.getAttribute("data-fpkey")] = String(el.value == null ? "" : el.value).replace(/^\s+|\s+$/g, "");
        });
        return out;
    }

    /* ------------------------------------------------------------------
     * ① 身份校验：拿平台下发的“找回方式”选项，再校验账号 + 图形验证码
     * ------------------------------------------------------------------ */
    function initStep1() {
        busy(true);
        api("getForgotPasswordForm", {}).then(function (res) {
            busy(false);
            var sel = byId("fp1_type");
            if (!ok(res)) {
                // 平台没开“启用忘记密码功能”时就是这里：原样显示平台文案，不绕过该开关
                var why = (res && res.message) ? String(res.message) : "读取找回密码表单失败";
                showUnavailable(why + "。请联系系统管理员重置密码，或在平台登录页用其它方式登录。");
                return;
            }
            var typeItem = findItem(res.conditions, "type");
            var pairs = optionPairs(typeItem);
            if (pairs.length === 0) {
                showUnavailable("当前没有可用的找回方式（短信 / 邮件通道或密保问题都没启用），"
                    + "请联系系统管理员重置密码。");
                return;
            }
            var html = "";
            pairs.forEach(function (o) {
                html += '<option value="' + esc(o.value) + '">' + esc(o.text) + "</option>";
            });
            sel.innerHTML = html;
            refreshCode();
        }).catch(function (e) {
            busy(false);
            setMsg("读取找回密码表单失败：" + e.message, "fail");
        });
    }

    function step1Next() {
        var loginid = val("fp1_loginid");
        var code = val("fp1_validatecode");
        var type = val("fp1_type");
        if (loginid.length === 0) { setMsg("请输入登录名", "fail"); byId("fp1_loginid").focus(); return; }
        if (type.length === 0) { setMsg("请选择找回方式", "fail"); return; }
        if (code.length === 0) { setMsg("请输入验证码", "fail"); byId("fp1_validatecode").focus(); return; }
        clearMsg();
        busy(true);
        form1 = {loginid: loginid, validatecode: code, type: type};
        // 平台先认出账号（忘记密码验证账号类型是否存在），再按找回方式发短信 / 邮件
        api("forgotPasswordCheckMsg", form1).then(function (res) {
            if (!ok(res)) {
                busy(false);
                fail(res, "账号校验不通过，请检查登录名与验证码");
                refreshCode();
                byId("fp1_validatecode").value = "";
                return;
            }
            state.id = String(res.id == null ? "" : res.id);
            return api("forgotPasswordToNext", merge({id: state.id}, form1)).then(function (r2) {
                if (!ok(r2)) {
                    busy(false);
                    fail(r2, "发送验证码失败，请稍后重试");
                    refreshCode();
                    return;
                }
                state.type = type;
                state.target = String(r2.id == null ? "" : r2.id);
                state.ttl = parseInt(r2.forgotpasswordExpiration, 10);
                if (!(state.ttl > 0)) { state.ttl = 60; }
                loadStep2();
            });
        }).catch(function (e) {
            busy(false);
            setMsg("找回密码请求失败：" + e.message, "fail");
        });
    }

    /* ------------------------------------------------------------------
     * ② 验证身份：短信 / 邮件验证码（可重发）或密保问题
     * ------------------------------------------------------------------ */
    function loadStep2() {
        var name = state.type === "phoneCode" ? "confirmPhoneCodeForm"
            : (state.type === "emailCode" ? "confirmEmailCodeForm" : "passwordQuestionForm");
        api(name, {validatecode: form1.validatecode, loginid: form1.loginid}).then(function (res) {
            busy(false);
            if (!ok(res)) {
                fail(res, "读取验证表单失败");
                refreshCode();
                return;
            }
            var bySms = state.type === "phoneCode";
            var byMail = state.type === "emailCode";
            byId("fpSendTip").className = "fp-tip";
            if (bySms) {
                byId("fpSendTip").textContent = "验证码已发送到手机号 " + (state.target || "")
                    + "，请在有效期内输入；短信验证码与下方图形验证码都要填。";
            } else if (byMail) {
                byId("fpSendTip").textContent = "验证码已发送到邮箱 " + (state.target || "")
                    + "，请在有效期内输入；邮件验证码与下方图形验证码都要填。";
            } else {
                byId("fpSendTip").textContent = "请选择密保问题并填写答案（答案不区分大小写由平台判断）。";
            }
            renderItems("fpCheckFields", "fp2", res.conditions, bySms || byMail);
            showStep(2);
            if (bySms || byMail) { startResend(); }
            var first = byId("fpCheckFields").querySelector("input");
            if (first) { first.focus(); }
        }).catch(function (e) {
            busy(false);
            setMsg("读取验证表单失败：" + e.message, "fail");
        });
    }

    /** 重发倒计时：秒数取平台回的 forgotpasswordExpiration，到点才能再发 */
    function startResend() {
        stopTimer();
        var btn = byId("fp2Resend");
        if (!btn) { return; }
        state.left = state.ttl;
        var tick = function () {
            if (state.left <= 0) {
                stopTimer();
                btn.disabled = false;
                btn.textContent = "重新发送";
                return;
            }
            btn.disabled = true;
            btn.textContent = state.left + " 秒后可重发";
            state.left--;
        };
        tick();
        state.timer = setInterval(tick, 1000);
        btn.addEventListener("click", resend);
    }

    /** 重发：平台口径是再调一次 forgotPasswordToNext（带上本步的图形验证码） */
    function resend() {
        var now = formParamsOf("fpCheckFields");
        if (!now.validatecode) {
            setMsg("请输入验证码", "fail");
            return;
        }
        clearMsg();
        busy(true);
        api("forgotPasswordToNext", merge(merge({id: state.id}, form1),
            {validatecode: now.validatecode})).then(function (res) {
            busy(false);
            if (!ok(res)) {
                fail(res, "重新发送失败，请稍后重试");
                refreshInlineCode();
                return;
            }
            state.target = String(res.id == null ? "" : res.id);
            state.ttl = parseInt(res.forgotpasswordExpiration, 10);
            if (!(state.ttl > 0)) { state.ttl = 60; }
            setMsg("验证码已重新发送，请查收。（收件人 " + state.target + "）", "ok");
            refreshInlineCode();
            startResend();
        }).catch(function (e) {
            busy(false);
            setMsg("重新发送失败：" + e.message, "fail");
        });
    }

    function refreshInlineCode() {
        var img = byId("fpCheckFields").querySelector(".fp-inline-code");
        if (img) { img.src = CODE_URL + new Date().getTime(); }
    }

    function step2Next() {
        var now = formParamsOf("fpCheckFields");
        var miss = "";
        Object.keys(now).forEach(function (k) {
            if (!miss && now[k].length === 0) { miss = k; }
        });
        if (miss) {
            var el = byId("fpCheckFields").querySelector('[data-fpkey="' + miss + '"]');
            setMsg("请把本步的验证信息填完整", "fail");
            if (el) { el.focus(); }
            return;
        }
        clearMsg();
        busy(true);
        form2 = now;
        // 平台口径：把三步的表单按“后覆盖前”合并提交（validatecode 用本步这张码）
        var params = merge(merge({id: state.id}, form1), form2);
        var name = state.type === "phoneCode" ? "confirmPhoneCodeToNext"
            : (state.type === "emailCode" ? "confirmEmailCodeToNext" : "passwordQuestionToNext");
        api(name, params).then(function (res) {
            if (!ok(res)) {
                busy(false);
                fail(res, "验证不通过，请重新输入");
                refreshInlineCode();
                return;
            }
            return api("resetPasswordForm", params).then(function (r2) {
                busy(false);
                if (!ok(r2)) {
                    fail(r2, "读取新密码表单失败");
                    refreshInlineCode();
                    return;
                }
                state.openRSA = String(r2.openRSA == null ? "0" : r2.openRSA);
                var item = findItem(r2.conditions, "newpswd");
                var other = (item && item.otherParams) || {};
                pwdRule.minpasslen = parseInt(other.minpasslen, 10);
                if (!(pwdRule.minpasslen > 0)) { pwdRule.minpasslen = 1; }
                pwdRule.passwordComplexity = String(other.passwordComplexity == null ? "0" : other.passwordComplexity);
                byId("fpRuleTip").className = "fp-tip";
                byId("fpRuleTip").textContent = "新密码长度不少于 " + pwdRule.minpasslen + " 位"
                    + complexityTip(pwdRule.passwordComplexity) + "。";
                renderItems("fpPwdFields", "fp3", r2.conditions, false);
                showStep(3);
                var first = byId("fpPwdFields").querySelector("input");
                if (first) { first.focus(); }
            });
        }).catch(function (e) {
            busy(false);
            setMsg("验证请求失败：" + e.message, "fail");
        });
    }

    function complexityTip(complexity) {
        if (String(complexity) === "1") { return "，且需包含大写字母、小写字母与数字"; }
        if (String(complexity) === "2") { return "，且需包含字母、数字与特殊字符"; }
        if (String(complexity) === "3") { return "，且需包含字母与数字"; }
        if (String(complexity) === "4") { return "，且需包含大写字母、小写字母、数字与特殊字符"; }
        return "";
    }

    /* ------------------------------------------------------------------
     * ③ 重设密码：两个输入一致 + 平台密码规则 + 弱密码校验（平台口径）→ RSA 加密后提交
     * ------------------------------------------------------------------ */
    function checkComplexity(v, complexity) {
        var c = String(complexity);
        if (c === "1") {
            return /[a-z]+/.test(v) && /[A-Z]+/.test(v) && /\d+/.test(v);
        }
        if (c === "2") {
            return /[a-zA-Z_]+/.test(v) && /\W+/.test(v) && /\d+/.test(v);
        }
        if (c === "3") {
            return /[a-zA-Z_]+/.test(v) && /\d+/.test(v);
        }
        if (c === "4") {
            return /[a-z]+/.test(v) && /[A-Z]+/.test(v) && /\d+/.test(v) && /\W+/.test(v);
        }
        return true;
    }

    function savePassword(forceWeak) {
        var now = formParamsOf("fpPwdFields");
        var pwd = now.newpswd || "";
        var cfm = now.cfmpswd || "";
        if (pwd.length === 0) { setMsg("请输入新密码", "fail"); return; }
        if (pwd !== cfm) { setMsg("两次输入的新密码不一致，请重新输入", "fail"); return; }
        if (pwd.length < pwdRule.minpasslen) {
            setMsg("密码长度不能小于 " + pwdRule.minpasslen, "fail");
            return;
        }
        if (!checkComplexity(pwd, pwdRule.passwordComplexity)) {
            setMsg("新密码不符合复杂度要求" + complexityTip(pwdRule.passwordComplexity), "fail");
            return;
        }
        clearMsg();
        busy(true);
        // 弱密码口径也走平台：平台说“禁止保存弱密码”就拦下来，说“允许”就再确认一次
        post(API_WEAK, {password: pwd}).then(function (weak) {
            var isWeak = !!weak && (weak.isWeakPassword === true || String(weak.isWeakPassword) === "true"
                || String(weak.isWeakPassword) === "1");
            if (isWeak && !forceWeak) {
                if (String(weak.weakPasswordDisable) === "1") {
                    busy(false);
                    setMsg("该密码比较容易被猜到，为了系统安全请重新设置一个更复杂的密码", "fail");
                    return;
                }
                busy(false);
                if (window.confirm("该密码比较容易被猜到，确定保存吗？")) {
                    savePassword(true);
                }
                return;
            }
            return doSave(pwd, cfm);
        }).catch(function () {
            // 弱密码校验接口不可用时按平台“不强拦”的口径继续，真正的规则仍由 saveResetPassword 兜底
            return doSave(pwd, cfm);
        });
    }

    function doSave(pwd, cfm) {
        form3 = {newpswd: pwd, cfmpswd: cfm};
        // 平台口径：三步的表单值“后覆盖前”合并后一起提交（validatecode 用第②步那张码）
        var params = merge(merge(merge({id: state.id}, form1), form2), form3);
        if (state.openRSA !== "1") {
            return api("saveResetPassword", params).then(afterSave, saveError);
        }
        // 平台开了登录 RSA：按平台口径加密新密码后再提交（密文口径与 /js/rsa/rsa.js 一致）
        return rsaEncryptPair(pwd, cfm).then(function (enc) {
            params.newpswd = enc.newpswd;
            params.cfmpswd = enc.cfmpswd;
            return api("saveResetPassword", params).then(afterSave, saveError);
        }).catch(function (e) {
            busy(false);
            setMsg("密码加密失败（" + e.message + "），请刷新页面后重试", "fail");
        });
    }

    function afterSave(res) {
        busy(false);
        if (!ok(res)) {
            // saveResetPassword 失败时平台会把原因放在 message（如密码策略、密码重复等）
            fail(res, "保存新密码失败，请稍后重试");
            return;
        }
        showStep(4);
        notifyParent("done");
    }

    function saveError(e) {
        busy(false);
        setMsg("保存新密码失败：" + e.message, "fail");
    }

    /* ------------------------------------------------------------------
     * RSA：与统一登录页同一套口径（平台自带 /js/rsa/jsencrypt.js + 公钥接口
     * /rsa/weaver.rsa.GetRsaInfo，密文 = RSA(值 + 盐) + 分隔符）
     * ------------------------------------------------------------------ */
    var rsaScriptPromise = null;

    function loadRsaScript() {
        if (window.JSEncrypt) { return Promise.resolve(); }
        if (!rsaScriptPromise) {
            rsaScriptPromise = new Promise(function (resolve, reject) {
                var s = document.createElement("script");
                s.src = "/js/rsa/jsencrypt.js";
                s.async = true;
                s.onload = function () {
                    if (window.JSEncrypt) { resolve(); } else { reject(new Error("加密组件加载异常")); }
                };
                s.onerror = function () { reject(new Error("加密组件加载失败")); };
                document.head.appendChild(s);
            });
            rsaScriptPromise.catch(function () { rsaScriptPromise = null; });
        }
        return rsaScriptPromise;
    }

    function rsaEncryptPair(pwd, cfm) {
        return loadRsaScript().then(function () {
            return fetch("/rsa/weaver.rsa.GetRsaInfo?ts=" + new Date().getTime(),
                {credentials: "same-origin"}).then(function (r) {
                if (!r.ok) { throw new Error("HTTP " + r.status); }
                return r.json();
            });
        }).then(function (keys) {
            if (!keys || !keys.rsa_pub) { throw new Error("未取到 RSA 公钥"); }
            var salt = keys.rsa_code == null ? "" : String(keys.rsa_code);
            var flag = keys.rsa_flag == null ? "" : String(keys.rsa_flag);
            var encrypt = new window.JSEncrypt();
            encrypt.setPublicKey(keys.rsa_pub);
            var one = function (v) {
                var out = encrypt.encrypt(v + salt);
                if (!out) { throw new Error("无法加密（新密码可能过长）"); }
                return out + flag;
            };
            return {newpswd: one(pwd), cfmpswd: one(cfm)};
        });
    }

    /* ------------------------------------------------------------------
     * 事件注册与初始化
     * ------------------------------------------------------------------ */
    byId("fpNext1").addEventListener("click", step1Next);
    byId("fpNext2").addEventListener("click", step2Next);
    byId("fpSave").addEventListener("click", function () { savePassword(false); });
    byId("fpRestart2").addEventListener("click", restart);
    byId("fpCancel1").addEventListener("click", function () { notifyParent("close"); });
    byId("fpCancel3").addEventListener("click", function () { notifyParent("close"); });
    byId("fpDone").addEventListener("click", function () { notifyParent("done"); });
    byId("fpUnavailClose").addEventListener("click", function () { notifyParent("close"); });
    byId("fp1CodeImg").addEventListener("click", refreshCode);
    byId("fp1_loginid").addEventListener("keydown", function (e) {
        if (e.keyCode === 13) { byId("fp1_validatecode").focus(); }
    });
    byId("fp1_validatecode").addEventListener("keydown", function (e) {
        if (e.keyCode === 13) { step1Next(); }
    });
    // 弹层里的回车提交：按当前步骤走
    document.addEventListener("keydown", function (e) {
        if (e.keyCode !== 13) { return; }
        var tag = e.target && e.target.tagName ? e.target.tagName.toLowerCase() : "";
        if (tag === "textarea") { return; }
        if (byId("fpPane2").style.display !== "none") { step2Next(); }
        else if (byId("fpPane3").style.display !== "none") { savePassword(false); }
    });

    /** 重新开始：回到第一步并换一张验证码（平台每次校验都会把用过的码作废） */
    function restart() {
        stopTimer();
        state = {id: "", type: "", target: "", ttl: 60, left: 0, timer: 0, openRSA: "0"};
        form1 = {}; form2 = {}; form3 = {};
        byId("fpCheckFields").innerHTML = "";
        byId("fpPwdFields").innerHTML = "";
        byId("fp1_validatecode").value = "";
        byId("fp1_type").disabled = false;
        byId("fpNext1").disabled = false;
        clearMsg();
        showStep(1);
        refreshCode();
        initStep1();
    }

    (function init() {
        showStep(1);
        initStep1();
    })();
})();
</script>
</body>
</html>
