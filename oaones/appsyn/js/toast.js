/**
 * OAones 统一弹出框（提示框 + 确认框），oaones 下所有页面共用。
 *
 * 组件约束（与 skill.md 一致）：
 *  1) 不准使用原生 alert 弹出框，只能使用本系统弹出框；
 *  2) 提示框支持四种模式：0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误；
 *  3) 支持设置显示时间，时间过后自动消失（duration <= 0 表示常驻，需手动关闭）。
 *
 * 页面接入方式：
 *   OaonesToast.show(msg, opt);                     // opt = { mode, duration, title }
 *   OaonesToast.show(msg, 2);                       // opt 直接给模式号也支持
 *   OaonesToast.info / warn / error / system(msg[, duration]);
 *   OaonesToast.loading(msg) / OaonesToast.hideLoading();   // 耗时操作等待框（挡住页面，禁止重复点）
 *   OaonesToast.confirm(msg, onOk, opt);            // 替代原生 confirm，opt = { mode, title, okText, cancelText, danger }
 *   OaonesToast.hide(el) / OaonesToast.clear();
 *
 * 说明：原生 alert 会被本文件兜底接管（转成模式 3 系统错误弹出框），
 * 页面里不得再主动调用 alert；原生 confirm 无法同步返回结果，已由 confirm() 取代。
 */
(function (global) {
    "use strict";

    /** 模式：0 普通提示 1 警告 2 操作错误 3 系统错误 */
    var MODE = { INFO: 0, WARN: 1, ERROR: 2, SYSTEM: 3 };
    /** 各模式默认显示时间（毫秒）；0 表示不自动消失 */
    var DEFAULT_DURATION = [3000, 5000, 8000, 0];
    /** 各模式右侧文字标签，普通提示不显示标签 */
    var MODE_LABEL = ["", "警告", "操作错误", "系统错误"];
    /** 同屏最多保留的提示框数量 */
    var MAX_KEEP = 6;

    var stackEl = null;
    var seq = 0;
    var confirmCtx = null;

    function esc(v) {
        if (v === null || v === undefined) { return ""; }
        return String(v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function modeNo(v) {
        var n = parseInt(v, 10);
        return (n >= 0 && n <= 3) ? n : MODE.INFO;
    }

    function normalizeOpt(opt) {
        if (opt === null || opt === undefined) { return {}; }
        if (typeof opt === "number" || typeof opt === "string") { return { mode: opt }; }
        return opt;
    }

    function ensureStack() {
        if (stackEl && document.body && document.body.contains(stackEl)) { return stackEl; }
        if (!document.body) { return null; }
        stackEl = document.createElement("div");
        stackEl.className = "toast-stack";
        stackEl.setAttribute("role", "status");
        stackEl.setAttribute("aria-live", "polite");
        document.body.appendChild(stackEl);
        return stackEl;
    }

    /** 弹出提示框，返回该提示框元素（duration<=0 时一直保留到手动关闭）。 */
    function show(msg, opt) {
        var o = normalizeOpt(opt);
        var mode = modeNo(o.mode);
        var duration = o.duration;
        if (duration === null || duration === undefined || duration === "") {
            duration = DEFAULT_DURATION[mode];
        }
        duration = parseInt(duration, 10);
        if (isNaN(duration)) { duration = DEFAULT_DURATION[mode]; }

        var stack = ensureStack();
        if (!stack) { return null; }

        var el = document.createElement("div");
        el.className = "toast toast-mode-" + mode;
        el.setAttribute("data-toast-id", String(++seq));
        el.setAttribute("role", mode >= MODE.ERROR ? "alert" : "status");
        el.innerHTML = "<span class='toast-icon' aria-hidden='true'>!</span>"
            + (MODE_LABEL[mode] ? "<span class='toast-label'>" + MODE_LABEL[mode] + "</span>" : "")
            + "<span class='toast-text'></span>"
            + "<button type='button' class='toast-close' title='关闭提示' aria-label='关闭提示'>×</button>";
        el.querySelector(".toast-text").textContent = (o.title ? o.title + "\n" : "") + String(msg == null ? "" : msg);
        stack.appendChild(el);

        // 超出同屏上限时，移除最早的一条
        var all = stack.querySelectorAll(".toast");
        if (all.length > MAX_KEEP) { remove(all[0]); }

        var timer = 0;
        var remain = duration;
        var startAt = 0;

        function stopTimer() {
            if (timer) { clearTimeout(timer); timer = 0; }
        }

        function startTimer() {
            stopTimer();
            if (remain <= 0) { return; }
            startAt = now();
            timer = setTimeout(function () { remove(el); }, remain);
        }

        el.addEventListener("click", function (e) {
            if (e.target && e.target.classList && e.target.classList.contains("toast-close")) {
                remove(el);
            }
        });
        // 鼠标停留时暂停计时，移开后继续走剩余时间，避免关键信息读不完就消失
        el.addEventListener("mouseenter", function () {
            if (duration > 0) { remain -= (now() - startAt); stopTimer(); }
        });
        el.addEventListener("mouseleave", function () {
            if (duration > 0) { startTimer(); }
        });
        startTimer();
        return el;
    }

    function now() { return new Date().getTime(); }

    /** 关闭指定提示框（带收起动画）。 */
    function remove(el) {
        if (!el || !el.parentNode) { return; }
        el.classList.add("toast-out");
        setTimeout(function () {
            if (el.parentNode) { el.parentNode.removeChild(el); }
        }, 180);
    }

    function hide(el) { remove(el); }

    /** 关闭全部提示框。 */
    function clear() {
        if (!stackEl) { return; }
        var all = stackEl.querySelectorAll(".toast");
        for (var i = 0; i < all.length; i++) { remove(all[i]); }
    }

    function info(msg, duration) { return show(msg, { mode: MODE.INFO, duration: duration }); }
    function warn(msg, duration) { return show(msg, { mode: MODE.WARN, duration: duration }); }
    function error(msg, duration) { return show(msg, { mode: MODE.ERROR, duration: duration }); }
    function system(msg, duration) { return show(msg, { mode: MODE.SYSTEM, duration: duration }); }

    /* ---------------------------------------------------------------
     * 加载框（等待提示）：用于耗时操作期间挡住页面并提示不要关闭页面。
     *   OaonesToast.loading(msg);   // 显示（重复调用只更新文案，不会叠加）
     *   OaonesToast.hideLoading();  // 关闭
     * 与提示框互不影响：堆叠层级低于提示框（z-index 见 css/style.css），
     * 所以“先 hideLoading() 再 show()”或直接 show() 都能正常看到提示。
     * --------------------------------------------------------------- */
    var loadingEl = null;
    var loadingTextEl = null;

    function loading(msg) {
        if (!document.body) { return null; }
        if (!loadingEl || !loadingEl.parentNode) {
            loadingEl = document.createElement("div");
            loadingEl.className = "oaones-loading-mask";
            loadingEl.setAttribute("role", "status");
            loadingEl.setAttribute("aria-live", "polite");
            loadingEl.innerHTML = ""
                + "<div class='oaones-loading-box'>"
                + "<span class='oaones-loading-spin' aria-hidden='true'></span>"
                + "<span class='oaones-loading-text'></span>"
                + "</div>";
            document.body.appendChild(loadingEl);
            loadingTextEl = loadingEl.querySelector(".oaones-loading-text");
        }
        if (loadingTextEl) { loadingTextEl.textContent = String(msg == null ? "" : msg); }
        loadingEl.style.display = "block";
        return loadingEl;
    }

    /** 关闭加载框；没有显示时调用无副作用。 */
    function hideLoading() {
        if (loadingEl && loadingEl.parentNode) { loadingEl.parentNode.removeChild(loadingEl); }
        loadingEl = null;
        loadingTextEl = null;
    }

    /* ---------------------------------------------------------------
     * 确认框：替代原生 confirm（原生 confirm 无法同步返回结果，故改为回调式）
     * --------------------------------------------------------------- */

    function confirm(msg, onOk, opt) {
        var o = normalizeOpt(opt);
        var mode = modeNo(o.mode === undefined ? MODE.WARN : o.mode);
        closeConfirm();
        if (!document.body) { return; }

        var mask = document.createElement("div");
        mask.className = "modal-mask";
        var modal = document.createElement("div");
        modal.className = "modal oaones-confirm";
        modal.innerHTML = ""
            + "<div class='modal-head'><span class='oaones-confirm-title'></span>"
            + "<button type='button' class='modal-close' title='关闭' aria-label='关闭'>×</button></div>"
            + "<div class='modal-body'><p class='oaones-confirm-text'></p></div>"
            + "<div class='modal-foot'>"
            + "<button type='button' class='btn secondary' data-act='cancel'></button>"
            + "<button type='button' class='btn" + (o.danger ? " danger" : "") + "' data-act='ok'></button>"
            + "</div>";
        modal.querySelector(".oaones-confirm-title").textContent = o.title || MODE_LABEL[mode] || "确认操作";
        modal.querySelector(".oaones-confirm-text").textContent = String(msg == null ? "" : msg);
        modal.querySelector("[data-act='cancel']").textContent = o.cancelText || "取消";
        modal.querySelector("[data-act='ok']").textContent = o.okText || "确定";
        document.body.appendChild(mask);
        document.body.appendChild(modal);
        mask.style.display = "block";
        modal.style.display = "block";

        var onDocKey = function (e) {
            if (e.key === "Escape") { closeConfirm(); }
        };
        confirmCtx = { mask: mask, modal: modal, onOk: onOk, onDocKey: onDocKey };

        function ok() {
            var cb = confirmCtx && confirmCtx.onOk;
            closeConfirm();
            if (typeof cb === "function") { cb(); }
        }

        modal.querySelector("[data-act='ok']").addEventListener("click", ok);
        modal.querySelector("[data-act='cancel']").addEventListener("click", closeConfirm);
        modal.querySelector(".modal-close").addEventListener("click", closeConfirm);
        // 点击遮罩关闭：与原生 confirm 的“取消”等价
        mask.addEventListener("click", closeConfirm);
        // 回车确认、Esc 取消
        modal.addEventListener("keydown", function (e) {
            if (e.key === "Enter") { e.preventDefault(); ok(); }
            else if (e.key === "Escape") { e.preventDefault(); closeConfirm(); }
        });
        var focusBtn = modal.querySelector("[data-act='ok']");
        try { focusBtn.focus(); } catch (ignoreFocus) { }

        document.addEventListener("keydown", onDocKey, true);
    }

    function closeConfirm() {
        if (!confirmCtx) { return; }
        var ctx = confirmCtx;
        confirmCtx = null;
        if (ctx.onDocKey) { document.removeEventListener("keydown", ctx.onDocKey, true); }
        if (ctx.mask && ctx.mask.parentNode) { ctx.mask.parentNode.removeChild(ctx.mask); }
        if (ctx.modal && ctx.modal.parentNode) { ctx.modal.parentNode.removeChild(ctx.modal); }
    }

    /* 兜底：页面误用原生 alert 时，改用本系统弹出框（模式 3 系统错误，需手动关闭） */
    global.alert = function (msg) {
        show(msg, { mode: MODE.SYSTEM, duration: 0 });
    };

    global.OaonesToast = {
        MODE: MODE,
        DEFAULT_DURATION: DEFAULT_DURATION,
        esc: esc,
        show: show,
        hide: hide,
        clear: clear,
        info: info,
        warn: warn,
        error: error,
        system: system,
        loading: loading,
        hideLoading: hideLoading,
        confirm: confirm,
        closeConfirm: closeConfirm
    };

})(window);
