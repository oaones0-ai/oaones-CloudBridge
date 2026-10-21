/**
 * OAones 页面提示语通用能力（/oaones 下所有页面共用）。
 *
 * “提示语”统一以问号图标（.help-tip）呈现：说明正文不直接铺在页面上，
 * 而是挂在图标后，鼠标悬浮或点击图标时弹出说明面板，页面更干净、口径也统一。
 *
 * 页面接入方式：
 *   <span class="help-tip" data-help="xxxHelpText" role="button" tabindex="0"
 *         title="查看页面说明" aria-label="查看页面说明">?</span>
 *   <div class="help-source" id="xxxHelpText">说明正文（可含 <span class="rule"> 高亮）</div>
 *
 * 说明正文放在 data-help 指向的隐藏节点（.help-source）里，便于随页面维护。
 * 悬浮显示、点击固定（再次点击关闭）、鼠标移出延时收起、Esc 关闭、
 * 越界自动上翻/收进视口、滚动与窗口缩放时跟随定位，均由本文件处理。
 * 说明正文按“1 / 2 / 3 / 4”分点展示（段落与句号都会断点，序号统一由 <ol> 生成），
 * 页面里的 .help-source 照旧写连续正文即可，不需要自己写编号。
 *
 * 提示语还要**按登录人角色分别显示**（系统管理员 / 租户维护人 / 普通用户）：
 * 各一级菜单页壳在 <head> 注入登录人信息，随数据变化的提示语用 OaonesTips.pick()
 * 二选一；页面上的静态说明正文（.help-source）由 JSP 按角色直接输出，见下方 OaonesTips 说明。
 */
(function () {
    "use strict";

    function byId(id) { return document.getElementById(id); }

    var helpPopEl = null;
    var helpPopTip = null;
    var helpPopPinned = false;
    var helpHideTimer = 0;

    function ensureHelpPop() {
        if (helpPopEl && document.body.contains(helpPopEl)) { return helpPopEl; }
        if (!document.body) { return null; }
        helpPopEl = document.createElement("div");
        helpPopEl.className = "help-pop";
        helpPopEl.addEventListener("mouseenter", function () { cancelHelpHide(); });
        helpPopEl.addEventListener("mouseleave", function () {
            if (!helpPopPinned) { scheduleHelpHide(); }
        });
        helpPopEl.addEventListener("click", function (e) { e.stopPropagation(); });
        document.body.appendChild(helpPopEl);
        return helpPopEl;
    }

    function cancelHelpHide() {
        if (helpHideTimer) { clearTimeout(helpHideTimer); helpHideTimer = 0; }
    }

    function scheduleHelpHide() {
        cancelHelpHide();
        helpHideTimer = setTimeout(function () { hideHelpPop(); }, 220);
    }

    /* ---------------- 说明正文分点展示（1 / 2 / 3 / 4） ----------------

       说明正文（.help-source）在页面源码里是连续文字：换行、缩进只是书写格式，
       直接塞进说明面板会连成一大段。这里把它解析成若干“点”，用 <ol class="help-points">
       分点编号展示，口径全模块一致：
         - 段落块（<p> / <div> / <li>，以及 <br>）各成一点；
         - 同一段里出现句号（。）也作为分点边界——一行里写了多个要点时同样能分开；
         - 每点开头若自带序号（1）、1.、1.1）、① 等），去掉它：序号统一由 <ol> 生成，
           不会出现“1. 1）……”这种双重编号；
         - 说明正文里原有的行内标记（<b> / <span class="rule"> / <code> / 链接）原样保留。 */
    var HELP_BLOCK = {
        P: 1, DIV: 1, LI: 1, DT: 1, DD: 1, BR: 1, UL: 1, OL: 1,
        H1: 1, H2: 1, H3: 1, H4: 1, H5: 1, H6: 1,
        SECTION: 1, ARTICLE: 1, BLOCKQUOTE: 1, PRE: 1,
        TABLE: 1, THEAD: 1, TBODY: 1, TR: 1, TD: 1, TH: 1
    };
    var HELP_ORDINAL = /^\s*(?:（[0-9]+）|\([0-9]+\)|[0-9]+(?:\.[0-9]+){0,2}\s*[）)．.、:：]|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])\s*/;

    /** 一组节点里是否没有任何可见文字（只有空白 / 只由序号组成的点会被丢掉） */
    function isBlankNodes(nodes) {
        var text = "";
        for (var i = 0; i < nodes.length; i++) { text += nodes[i].textContent || ""; }
        return text.replace(/[\s\u00a0]+/g, "") === "";
    }

    /** 解析说明正文为“点”的数组，每个点是一组待渲染的节点 */
    function helpPointNodes(srcEl) {
        var points = [];
        var cur = [];

        function flush() {
            if (cur.length && !isBlankNodes(cur)) { points.push(cur); }
            cur = [];
        }

        /** 纯文本按句号切开：句号（含）留在前一点尾部 */
        function pushText(text) {
            var rest = String(text == null ? "" : text);
            var at = rest.indexOf("。");
            while (at >= 0) {
                cur.push(document.createTextNode(rest.substring(0, at + 1)));
                flush();
                rest = rest.substring(at + 1);
                at = rest.indexOf("。");
            }
            if (rest !== "") { cur.push(document.createTextNode(rest)); }
        }

        function walk(node) {
            var kids = node.childNodes;
            for (var i = 0; i < kids.length; i++) {
                var child = kids[i];
                if (child.nodeType === 3) { pushText(child.nodeValue); continue; }
                if (child.nodeType !== 1) { continue; }
                var tag = child.tagName;
                if (tag === "BR") { flush(); continue; }
                if (HELP_BLOCK[tag]) { flush(); walk(child); flush(); continue; }
                var clone = child.cloneNode(true);
                cur.push(clone);
                // 行内标记自己以句号收尾（如 <b>……。</b>）时，同样在这里断点
                if (/。\s*$/.test(clone.textContent || "")) { flush(); }
            }
        }

        walk(srcEl);
        flush();
        return points;
    }

    /** 去掉一个点开头的自带序号（就地改节点，保证行内标记不动） */
    function stripHelpOrdinal(nodes) {
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            if (node.nodeType === 3) {
                node.nodeValue = node.nodeValue.replace(HELP_ORDINAL, "");
                if (node.nodeValue.replace(/[\s\u00a0]+/g, "") !== "") { return; }
                continue;
            }
            if (node.nodeType === 1 && node.tagName !== "BR" && stripHelpOrdinalIn(node)) { return; }
        }
    }

    function stripHelpOrdinalIn(el) {
        var kids = el.childNodes;
        for (var i = 0; i < kids.length; i++) {
            var node = kids[i];
            if (node.nodeType === 3) {
                node.nodeValue = node.nodeValue.replace(HELP_ORDINAL, "");
                if (node.nodeValue.replace(/[\s\u00a0]+/g, "") !== "") { return true; }
                continue;
            }
            if (node.nodeType === 1 && stripHelpOrdinalIn(node)) { return true; }
        }
        return false;
    }

    /** 把说明正文渲染进说明面板：分点用 <ol> 编号；解析不出内容时退回原文 */
    function fillHelpPop(pop, srcEl) {
        var points = helpPointNodes(srcEl);
        pop.innerHTML = "";
        if (!points.length) { pop.innerHTML = srcEl.innerHTML; return; }
        var ol = document.createElement("ol");
        ol.className = "help-points";
        for (var i = 0; i < points.length; i++) {
            stripHelpOrdinal(points[i]);
            if (isBlankNodes(points[i])) { continue; }
            var li = document.createElement("li");
            var nodes = points[i];
            for (var j = 0; j < nodes.length; j++) { li.appendChild(nodes[j]); }
            ol.appendChild(li);
        }
        if (!ol.childNodes.length) { pop.innerHTML = srcEl.innerHTML; return; }
        pop.appendChild(ol);
    }

    function showHelpPop(tip) {
        var srcEl = byId(tip.getAttribute("data-help"));
        var pop = ensureHelpPop();
        if (!pop || !srcEl) { return; }
        cancelHelpHide();
        if (helpPopTip !== tip) {
            fillHelpPop(pop, srcEl);
        }
        helpPopTip = tip;
        pop.style.display = "block";
        placeHelpPop(tip, pop);
        var tips = document.querySelectorAll(".help-tip");
        for (var i = 0; i < tips.length; i++) { tips[i].classList.toggle("active", tips[i] === tip); }
    }

    /** 优先显示在图标下方，越界时上翻/左右收进视口。 */
    function placeHelpPop(tip, pop) {
        var r = tip.getBoundingClientRect();
        var w = pop.offsetWidth;
        var h = pop.offsetHeight;
        var left = Math.round(r.left);
        var maxLeft = window.innerWidth - w - 12;
        if (left > maxLeft) { left = maxLeft; }
        if (left < 12) { left = 12; }
        var top = Math.round(r.bottom + 8);
        if (top + h > window.innerHeight - 12) {
            top = Math.round(r.top - h - 8);
            if (top < 12) { top = 12; }
        }
        pop.style.left = left + "px";
        pop.style.top = top + "px";
    }

    function hideHelpPop() {
        cancelHelpHide();
        helpPopPinned = false;
        helpPopTip = null;
        if (helpPopEl) { helpPopEl.style.display = "none"; }
        var tips = document.querySelectorAll(".help-tip");
        for (var i = 0; i < tips.length; i++) { tips[i].classList.remove("active"); }
    }

    document.addEventListener("mouseover", function (e) {
        var tip = e.target.closest ? e.target.closest(".help-tip") : null;
        if (tip && !helpPopPinned) { showHelpPop(tip); }
    });
    document.addEventListener("mouseout", function (e) {
        var tip = e.target.closest ? e.target.closest(".help-tip") : null;
        if (tip && !helpPopPinned) { scheduleHelpHide(); }
    });
    document.addEventListener("click", function (e) {
        var tip = e.target.closest ? e.target.closest(".help-tip") : null;
        if (tip) {
            // 点击：固定在图标上（再次点击关闭），不受鼠标移出影响
            if (helpPopPinned && helpPopTip === tip) { hideHelpPop(); }
            else {
                helpPopPinned = true;
                showHelpPop(tip);
            }
            return;
        }
        if (helpPopEl && helpPopEl.contains(e.target)) { return; }
        if (helpPopTip) { hideHelpPop(); }
    });
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") { hideHelpPop(); }
    });
    // 点击图标即显示（键盘回车/空格同样可用）
    document.addEventListener("keydown", function (e) {
        var tip = e.target && e.target.classList && e.target.classList.contains("help-tip") ? e.target : null;
        if (!tip) { return; }
        if (e.key === "Enter" || e.key === " " || e.key === "Spacebar") {
            e.preventDefault();
            helpPopPinned = true;
            showHelpPop(tip);
        }
    });
    window.addEventListener("resize", hideHelpPop);
    window.addEventListener("scroll", function () {
        if (helpPopTip && helpPopEl && helpPopEl.style.display === "block") { placeHelpPop(helpPopTip, helpPopEl); }
    }, true);

    /* ---------------- 按登录人角色显示不同提示语 ----------------

       各一级菜单页壳（config.jsp / sync.jsp / userBind.jsp / deptBind.jsp）在 <head> 注入：
         window.OAONES_USER = { id: "<当前登录人ID>", sysAdmin: true|false }
       其中 sysAdmin 由页壳调后端 OaonesAdmin.isTotalAdmin 得到（系统管理员 user.id=1，
       或 oaones.properties#total.admin.roleid 这个超管角色内的人员，两者口径一致），
       未注入时按“非系统管理员”处理（提示语走维护人/普通用户口径，不会误把普通用户当超管）。

       角色口径：系统管理员（user.id=1）与超管角色成员可查看与维护全部租户，未指定维护人的租户仅其可见；
       维护人只能查看与维护本人被指定的租户；两者都不是的普通用户看不到任何租户。
       落点：
       - 静态说明正文（.help-source）由 JSP 按角色输出（服务端渲染，无闪烁、无需等接口）；
       - 随接口数据变化的提示语（空列表提示、确认文案等）由页面用 OaonesTips.pick() 取。 */
    var roleUser = window.OAONES_USER || {};

    /** 当前登录人是否拥有全局管理权限（系统管理员 user.id=1 或超管角色成员）。 */
    function isSysAdmin() {
        return roleUser.sysAdmin === true;
    }

    /**
     * 按角色二选一提示语：系统管理员取 adminText，维护人/普通用户取 userText。
     * 页面只负责准备两套文案，角色判断口径统一在本文件，避免各页各写一遍。
     */
    function pick(adminText, userText) {
        return isSysAdmin() ? adminText : userText;
    }

    window.OaonesTips = { isSysAdmin: isSysAdmin, pick: pick };
})();
