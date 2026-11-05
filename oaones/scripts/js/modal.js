/**
 * OAones 通用弹窗组件（OaonesModal）。
 *
 * 负责所有 `.modal` / `.oa-modal` 的公共行为：
 *   - 打开、关闭、切换；
 *   - 按住 `.modal-head` / `.oa-modal-head` 拖动；
 *   - 全屏、退出全屏；
 *   - Esc 关闭最上层弹窗；
 *   - 窗口缩放后把已拖动弹窗收回可视区域。
 *
 * 页面标记约定：
 *   <div class="modal-mask" id="xxxMask"></div>
 *   <div class="modal" id="xxxModal" data-modal-mask="xxxMask">...</div>
 * 兼容现有结构：弹窗没有 data-modal-mask 时，自动使用前一个 `.modal-mask` 兄弟节点。
 *
 * 也可以通过按钮声明式触发：
 *   data-modal-open="xxxModal"
 *   data-modal-close="xxxModal"
 *   data-modal-fullscreen="xxxModal"
 */
(function (global, document) {
    "use strict";

    var DRAG_IGNORE = "button, a, input, select, textarea, label, .no-drag";
    var DRAG_HEAD = ".modal-head, .oa-modal-head";
    var DRAG_BOX = ".modal, .oa-modal";
    var bound = false;

    function byId(id) {
        return id ? document.getElementById(String(id)) : null;
    }

    function resolve(target) {
        return typeof target === "string" ? byId(target) : target;
    }

    function toModal(target) {
        var el = resolve(target);
        if (!el) { return null; }
        if (el.classList && el.classList.contains("modal-mask")) {
            return el.querySelector(".modal, .oa-modal");
        }
        return el;
    }

    function toMask(target, modal) {
        var el = resolve(target);
        if (el && el.classList && el.classList.contains("modal-mask")) { return el; }
        var maskId = modal ? modal.getAttribute("data-modal-mask") : "";
        if (maskId) { return byId(maskId); }
        var prev = modal ? modal.previousElementSibling : null;
        while (prev) {
            if (prev.classList && prev.classList.contains("modal-mask")) { return prev; }
            prev = prev.previousElementSibling;
        }
        return null;
    }

    function setVisible(el, visible) {
        if (!el) { return; }
        el.style.display = visible ? "block" : "none";
        el.setAttribute("aria-hidden", visible ? "false" : "true");
    }

    function focusFirst(modal) {
        if (!modal) { return; }
        var target = modal.querySelector("[autofocus]")
            || modal.querySelector("input:not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([disabled]), button:not([disabled])")
            || modal;
        try {
            if (target && target.focus) { target.focus(); }
        } catch (ignore) {
            // 焦点失败不影响弹窗显示。
        }
    }

    function open(target, options) {
        options = options || {};
        var modal = toModal(target);
        if (!modal) { return null; }
        var mask = toMask(options.mask, modal);
        setVisible(mask, true);
        setVisible(modal, true);
        if (options.focus !== false) {
            global.setTimeout(function () { focusFirst(modal); }, 0);
        }
        return modal;
    }

    function close(target, options) {
        options = options || {};
        var modal = toModal(target);
        if (!modal) { return null; }
        if (modal.classList.contains("modal-fullscreen")) {
            toggleFullscreen(modal, false);
        }
        setVisible(modal, false);
        setVisible(toMask(options.mask, modal), false);
        return modal;
    }

    function toggle(target, force, options) {
        var modal = toModal(target);
        if (!modal) { return null; }
        var show = force === undefined ? !isOpen(modal) : !!force;
        return show ? open(modal, options) : close(modal, options);
    }

    function isOpen(target) {
        var modal = toModal(target);
        return !!(modal && modal.style.display !== "none");
    }

    function savePosition(modal) {
        return modal ? {
            left: modal.style.left,
            top: modal.style.top,
            transform: modal.style.transform,
            margin: modal.style.margin
        } : null;
    }

    function restorePosition(modal, saved) {
        if (!modal || !saved) { return; }
        modal.style.left = saved.left || "";
        modal.style.top = saved.top || "";
        modal.style.transform = saved.transform || "";
        modal.style.margin = saved.margin || "";
    }

    function toggleFullscreen(target, force) {
        var modal = toModal(target);
        if (!modal) { return false; }
        var shouldOpen = force === undefined ? !modal.classList.contains("modal-fullscreen") : !!force;
        if (shouldOpen) {
            if (!modal.__oaonesFullscreenPosition) {
                modal.__oaonesFullscreenPosition = savePosition(modal);
            }
            modal.classList.add("modal-fullscreen");
            modal.style.display = "flex";
            modal.setAttribute("aria-modal", "true");
            return true;
        }
        modal.classList.remove("modal-fullscreen");
        modal.style.display = "block";
        restorePosition(modal, modal.__oaonesFullscreenPosition);
        modal.__oaonesFullscreenPosition = null;
        return false;
    }

    function startDrag(modal, e) {
        var rect = modal.getBoundingClientRect();
        modal.style.transform = "none";
        modal.style.margin = "0";
        if (!modal.style.position) {
            var pos = global.getComputedStyle ? global.getComputedStyle(modal).position : "";
            if (!pos || pos === "static") { modal.style.position = "fixed"; }
        }
        modal.style.left = rect.left + "px";
        modal.style.top = rect.top + "px";
        modal.classList.add("modal-dragging");
        document.body.classList.add("modal-dragging");
        var offX = e.clientX - rect.left;
        var offY = e.clientY - rect.top;

        function move(ev) {
            var maxLeft = Math.max(0, global.innerWidth - 80);
            var maxTop = Math.max(0, global.innerHeight - 40);
            var left = Math.max(-(modal.offsetWidth - 80), Math.min(ev.clientX - offX, maxLeft));
            var top = Math.max(0, Math.min(ev.clientY - offY, maxTop));
            modal.style.left = left + "px";
            modal.style.top = top + "px";
            ev.preventDefault();
        }

        function up() {
            document.removeEventListener("mousemove", move);
            document.removeEventListener("mouseup", up);
            modal.classList.remove("modal-dragging");
            document.body.classList.remove("modal-dragging");
        }

        document.addEventListener("mousemove", move);
        document.addEventListener("mouseup", up);
        e.preventDefault();
    }

    function keepInView() {
        var modals = document.querySelectorAll(DRAG_BOX);
        for (var i = 0; i < modals.length; i++) {
            var modal = modals[i];
            if (!modal.style.left || modal.style.display === "none") { continue; }
            var top = parseFloat(modal.style.top);
            var left = parseFloat(modal.style.left);
            if (isNaN(top) || isNaN(left)) { continue; }
            var maxTop = Math.max(0, global.innerHeight - 40);
            var maxLeft = Math.max(0, global.innerWidth - 80);
            modal.style.top = Math.max(0, Math.min(top, maxTop)) + "px";
            modal.style.left = Math.max(-(modal.offsetWidth - 80), Math.min(left, maxLeft)) + "px";
        }
    }

    function topVisibleModal() {
        var list = document.querySelectorAll(DRAG_BOX);
        for (var i = list.length - 1; i >= 0; i--) {
            if (list[i].style.display !== "none") { return list[i]; }
        }
        return null;
    }

    function bind() {
        if (bound) { return; }
        bound = true;

        document.addEventListener("mousedown", function (e) {
            if (e.button !== 0) { return; }
            var target = e.target;
            if (!target || !target.closest) { return; }
            var head = target.closest(DRAG_HEAD);
            if (!head || target.closest(DRAG_IGNORE)) { return; }
            var modal = head.closest(DRAG_BOX);
            if (!modal || modal.classList.contains("modal-fullscreen")) { return; }
            startDrag(modal, e);
        });

        document.addEventListener("click", function (e) {
            var target = e.target;
            if (!target || !target.closest) { return; }
            var openBtn = target.closest("[data-modal-open]");
            if (openBtn) { open(openBtn.getAttribute("data-modal-open")); return; }
            var closeBtn = target.closest("[data-modal-close]");
            if (closeBtn) { close(closeBtn.getAttribute("data-modal-close")); return; }
            var fullBtn = target.closest("[data-modal-fullscreen]");
            if (fullBtn) { toggleFullscreen(fullBtn.getAttribute("data-modal-fullscreen")); }
        });

        document.addEventListener("keydown", function (e) {
            if (e.key !== "Escape" && e.keyCode !== 27) { return; }
            var modal = topVisibleModal();
            if (!modal) { return; }
            if (modal.classList.contains("modal-fullscreen")) {
                toggleFullscreen(modal, false);
            } else {
                close(modal);
            }
        });

        global.addEventListener("resize", keepInView);
    }

    global.OaonesModal = {
        bind: bind,
        open: open,
        close: close,
        toggle: toggle,
        isOpen: isOpen,
        toggleFullscreen: toggleFullscreen,
        savePosition: savePosition,
        restorePosition: restorePosition,
        focusFirst: focusFirst
    };

    bind();
})(window, document);
