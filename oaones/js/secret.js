/**
 * OAones 密码 / 密钥类输入框统一口径（/oaones 下所有含 Secret / 密钥的框共用）。
 *
 * 口径：**编辑时只显示 ******，绝不回显真实密钥；用户不动它 = 不修改，
 * 保存时提交空串（后端按“空则保持原值”处理）。
 *
 * 页面接入方式（三处调用，缺一不可）：
 *   <input id="corp_secret" type="password" autocomplete="new-password" data-lpignore="true">
 *   OaonesSecret.reset(byId("corp_secret"), !!row);        // 打开编辑弹框/载入数据时
 *   OaonesSecret.bind(byId("corp_secret"));                // 页面初始化时绑定一次
 *   corp_secret: OaonesSecret.value(byId("corp_secret"))   // 提交时取值
 *
 * 行为：
 *  - reset(el, true)  -> 字段显示 ****（掩码态，表示“已配置，留空即不修改”）；
 *    reset(el, false) -> 字段清空（新增记录，必须自己填）；
 *  - 用户点进掩码态的输入框：掩码自动让开、字段切回密码态（输入的内容不落明文）；
 *    没输入就离开：掩码恢复，相当于“不修改”；
 *  - value(el)：仍是掩码时返回空串。
 *
 * 掩码态用 type=text 渲染（这样看到的才是字面的 ****，而不是浏览器把密码渲染成的圆点），
 * 一旦进入输入就切回 type=password，输入过程依然不可见。
 */
(function (global) {
    "use strict";

    /** 编辑态密钥的显示标记：值等于它即视为“没有改动” */
    var MASK = "****";
    /** 标记该框代表的记录里是否已有密钥 */
    var FLAG = "data-oa-secret-set";

    function setFlag(el, hasSecret) {
        el.setAttribute(FLAG, hasSecret ? "1" : "0");
    }

    function hasSecretFlag(el) {
        return el.getAttribute(FLAG) === "1";
    }

    function showMask(el) {
        el.type = "text";
        el.value = MASK;
        el.classList.add("oa-secret-masked");
    }

    function clearForInput(el) {
        el.type = "password";
        el.value = "";
        el.classList.remove("oa-secret-masked");
    }

    /**
     * 打开编辑弹框（或载入配置）时调用：已有密钥显示掩码，新增（或未配置）显示空框。
     * hasSecret 传“这条记录是否已维护密钥”。
     */
    function reset(el, hasSecret) {
        if (!el) { return; }
        setFlag(el, hasSecret === true);
        if (hasSecret === true) { showMask(el); } else { clearForInput(el); }
    }

    /** 提交时取值：掩码 = 不修改，返回空串；用户改过就返回输入内容 */
    function value(el) {
        if (!el) { return ""; }
        if (hasSecretFlag(el) && el.value === MASK) { return ""; }
        return String(el.value == null ? "" : el.value);
    }

    /** 绑定掩码态与输入态的切换（每页对每个密钥框调用一次） */
    function bind(el) {
        if (!el || el.getAttribute("data-oa-secret-bound") === "1") { return; }
        el.setAttribute("data-oa-secret-bound", "1");
        el.addEventListener("focus", function () {
            // 点进掩码框：让出掩码、切回密码态，用户直接输入新密钥（不修改就原样离开）
            if (hasSecretFlag(el) && el.value === MASK) { clearForInput(el); }
        });
        el.addEventListener("blur", function () {
            // 没输入就离开：恢复掩码，等于“不修改”
            if (hasSecretFlag(el) && el.value === "") { showMask(el); }
        });
    }

    global.OaonesSecret = {
        MASK: MASK,
        reset: reset,
        bind: bind,
        value: value
    };
})(window);
