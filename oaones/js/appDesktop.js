/**
 * 应用中心「桌面启动台」脚本（/oaones/appCenter/Desktop.jsp）。
 *
 * 职责：
 *   1) 取启用中的应用（action appc.desktop），渲染成 macOS 桌面图标（**只有桌面图标区，
 *      没有底部 Dock**：Dock 那排小图标已按需求去掉）；
 *   2) 鼠标移上去放大由 CSS（.mac-icon:hover）负责，本文件不参与缩放；
 *   3) 点击图标：先调 appc.entry 让后端按该应用配置的“单点登录实现类”生成最终地址，
 *      再按该应用的“打开方式”（openMode）打开：0 默认 = 只在当前页面/框架里打开（外层 OA 框架不动）、
 *      1 = 新标签页、2 = 原标签页（整个页面 reload）。
 *      （没配实现类时后端直接返回应用默认地址；实现类不可用时退回默认地址并给出提示。）
 *
 * 约定（见 skill.md 第一节第 7 条）：本文件只声明函数，不在解析期访问 DOM，
 * 初始化统一放在 MacDesktop.load()，由页壳在 DOMContentLoaded 之后调用。
 *
 * 本页与「应用列表」（appCenter/AppList.jsp）**互不链接**：菜单栏、桌面、空状态里
 * 都不提供该页入口——两个页面不设任何菜单关联，各自单独打开、单独显示。
 */
(function () {
    "use strict";

    /** 图标底色（按默认文字图标的基准散列取一个，纯 CSS 渐变，不依赖任何图片资源）。 */
    var PALETTE = [
        "linear-gradient(160deg,#ff9a44,#fc6076)",
        "linear-gradient(160deg,#42e695,#3bb2b8)",
        "linear-gradient(160deg,#5b86e5,#36d1dc)",
        "linear-gradient(160deg,#f7971e,#ffd200)",
        "linear-gradient(160deg,#a18cd1,#fbc2eb)",
        "linear-gradient(160deg,#ff6a88,#ff99ac)",
        "linear-gradient(160deg,#0ba360,#3cba92)",
        "linear-gradient(160deg,#4facfe,#00f2fe)",
        "linear-gradient(160deg,#f093fb,#f5576c)",
        "linear-gradient(160deg,#667eea,#764ba2)"
    ];

    /**
     * 打开方式（与后端 uf_oaones_app.open_mode 一致）：
     *   0 默认（CURRENT）—— 只在当前页面 / 框架里打开，外层 OA 框架不动（不是整个页面 reload）；
     *   1 新标签页（NEW_TAB）—— 浏览器新开一个标签页打开；
     *   2 原标签页（TOP）—— 整个页面 reload：最外层窗口（含外层 OA 框架）换成该应用地址。
     */
    var OPEN_MODE = { CURRENT: 0, NEW_TAB: 1, TOP: 2 };

    var state = { apps: [], bound: false };

    function byId(id) { return document.getElementById(id); }

    function esc(v) { return window.Oaones ? Oaones.esc(v) : String(v == null ? "" : v); }

    function toast(msg, mode, duration) {
        if (msg === null || msg === undefined || String(msg) === "") { return; }
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode, duration: duration }); }
    }

    /** 字符串散列（与 Java 的 hashCode 无关，只要同一名字每次落到同一个颜色即可）。 */
    function hash(str) {
        var s = String(str == null ? "" : str);
        var h = 0;
        for (var i = 0; i < s.length; i++) {
            h = (h * 31 + s.charCodeAt(i)) % 1000000007;
        }
        return h;
    }

    function tileBg(name) {
        return PALETTE[hash(name) % PALETTE.length];
    }

    /**
     * 默认文字图标的取字 / 取色基准：优先后端保存时固化的 iconSeed
     * （维护人改名后图标不跟着自动变——这是“使用系统默认文字图标”时的既定口径），
     * 老数据（还没固化）才退回当前应用名。与列表页 appListPanel.jsp 的 appcTileSeed 是同一口径。
     */
    function tileSeed(app) {
        var seed = String(app.iconSeed || "").trim();
        return seed.length > 0 ? seed : String(app.name || "").trim();
    }

    /** 图标里的字：有图标地址用图片，没有就用固化下来的默认文字图标（首字）。 */
    function tileInner(app) {
        var icon = String(app.icon || "").trim();
        if (icon.length > 0) {
            return "<img src=\"" + esc(icon) + "\" alt=\"" + esc(app.name) + "\" onerror=\"this.style.display='none'\">";
        }
        var seed = tileSeed(app);
        return esc(seed.length > 0 ? seed.charAt(0).toUpperCase() : "?");
    }

    function ssoBadge(app) {
        return app.sso ? "<span class='mac-sso-badge' title='该应用配置了单点登录实现类，点击时先做对方身份认证'>SSO</span>" : "";
    }

    /** 打开方式：只认 1 新标签页 / 2 原标签页，其它（老数据 NULL / 非法值）都按 0 默认。 */
    function openModeOf(app) {
        var m = Number(app && app.openMode);
        return (m === OPEN_MODE.NEW_TAB || m === OPEN_MODE.TOP) ? m : OPEN_MODE.CURRENT;
    }

    function openModeText(app) {
        var m = openModeOf(app);
        if (m === OPEN_MODE.NEW_TAB) { return "新标签页"; }
        if (m === OPEN_MODE.TOP) { return "原标签页（整个页面）"; }
        return "默认（当前页面）";
    }

    /**
     * 图标链接的 target：只有“新标签页”模式写成 _blank——
     * 这样键盘 / 中键、右键“在新标签页打开”与配置的打开方式一致，脚本没跑起来时也按配置打开；
     * 默认与原标签页不加 target（正常点击由脚本按模式分派）。
     */
    function targetAttr(app) {
        return openModeOf(app) === OPEN_MODE.NEW_TAB ? " target='_blank' rel='noopener'" : "";
    }

    function tipOf(app) {
        var parts = [String(app.name || "")];
        if (app.sso) {
            parts.push("单点登录实现类：" + String(app.ssoClass || ""));
        } else {
            parts.push("直接打开默认地址");
        }
        parts.push("打开方式：" + openModeText(app));
        if (app.remark) { parts.push(String(app.remark)); }
        return parts.join("\n");
    }

    /* ------------------------------------------------------------------
     * 渲染
     * ------------------------------------------------------------------ */

    function renderDesktop() {
        var host = byId("macDesktop");
        var html = "";
        state.apps.forEach(function (app) {
            html += "<a class='mac-icon' href='" + esc(app.url) + "' data-app-id='" + esc(app.id) + "'"
                + targetAttr(app) + " title=\"" + esc(tipOf(app)) + "\">"
                + "<div class='mac-icon-tile' style='background:" + tileBg(tileSeed(app)) + "'>"
                + tileInner(app) + ssoBadge(app) + "</div>"
                + "<div class='mac-icon-label'>" + esc(app.name) + "</div>"
                + "</a>";
        });
        host.innerHTML = html;
    }

    function renderEmpty() {
        var empty = byId("macEmpty");
        if (!empty) { return; }
        if (state.apps.length > 0) {
            empty.style.display = "none";
            return;
        }
        empty.style.display = "";
        // 只提示，不给「应用列表」入口：两个页面不设任何菜单关联（各自单独打开）
        // 两种情况都可能：一个应用都没有；或应用都设了可见范围（未勾选“全员可见”、
        // 四类都没设置 = 无权限）而没轮到你
        empty.innerHTML = "<b>桌面上没有对你可用的应用</b>"
            + "<br>请联系管理员在「应用中心 → 应用列表」里添加应用，"
            + "或在应用的「可见范围」里把你的分部 / 部门 / 人员加进去，"
            + "或直接勾选「全员可见」（所有登录人都能看到）";
    }

    function render() {
        renderDesktop();
        renderEmpty();
    }

    /* ------------------------------------------------------------------
     * 点击启动：先按单点登录实现类生成地址，再按该应用的“打开方式”打开
     * ------------------------------------------------------------------ */

    function findApp(id) {
        var key = String(id == null ? "" : id);
        for (var i = 0; i < state.apps.length; i++) {
            if (String(state.apps[i].id) === key) { return state.apps[i]; }
        }
        return null;
    }

    /**
     * 新标签页模式先占一个空白页签：图标点击是**同步**的用户手势，
     * 等 appc.entry 异步返回后再调 window.open 会被浏览器当弹窗拦掉，
     * 所以这里先开着空页签，拿到最终地址再给它赋 location。
     * 被拦截（浏览器返回 null / 抛错）时返回 null，交给 openByMode 兜底。
     */
    function openPlaceholderTab() {
        try {
            return window.open("", "_blank");
        } catch (e) {
            return null;
        }
    }

    /** 关掉没用上的占位页签（取地址失败时不留空白页签给用户） */
    function closePlaceholderTab(tab) {
        if (!tab) { return; }
        try {
            if (!tab.closed) { tab.close(); }
        } catch (e) {
            // 关不掉就算了（只是留一个空白页签）
        }
    }

    /**
     * 按“打开方式”打开最终地址：
     *   0 默认     —— 只换当前页面 / 框架（window.location），外层 OA 框架不动，不是整个页面 reload；
     *   1 新标签页 —— 用点击时占好的空白页签（tab）打开；占位失败就再试一次 window.open，
     *                 仍被拦时退回当前页面打开并提示（保证点击一定有反应）；
     *   2 原标签页 —— 整个页面 reload：最外层窗口（window.top，含外层 OA 框架）换成该地址；
     *                 外层不可写（跨域等）时退回当前页面打开。
     * @param tab 仅在“新标签页”模式下有值：点击时先占好的空白页签
     */
    function openByMode(url, mode, tab) {
        if (mode === OPEN_MODE.NEW_TAB) {
            if (tab && !tab.closed) {
                tab.location.href = url;
                return;
            }
            var again = null;
            try {
                again = window.open(url, "_blank");
            } catch (e) {
                again = null;
            }
            if (again) { return; }
            toast("浏览器拦住了新标签页，已在当前页面打开", 1, 6000);
            window.location.href = url;
            return;
        }
        if (mode === OPEN_MODE.TOP) {
            try {
                // 原标签页 = 整个页面 reload：连最外层窗口（外层 OA 框架）一起换掉
                window.top.location.href = url;
                return;
            } catch (e) {
                // 外层窗口不可写（跨域限制等）：退回当前页面打开，至少能打开
            }
        }
        window.location.href = url;
    }

    function launch(app) {
        if (!app) { return; }
        var mode = openModeOf(app);
        // 新标签页：必须在点击的同步过程里先把页签占住，见 openPlaceholderTab
        var tab = mode === OPEN_MODE.NEW_TAB ? openPlaceholderTab() : null;
        if (window.OaonesToast) { window.OaonesToast.loading("正在打开「" + app.name + "」…"); }
        Oaones.api("appc.entry", { id: app.id }, function (res) {
            if (window.OaonesToast) { window.OaonesToast.hideLoading(); }
            if (!res || res.code !== 0 || !res.data || !res.data.url) {
                closePlaceholderTab(tab);
                toast((res && res.msg) ? res.msg : "打开应用失败，请稍后再试", 2);
                return;
            }
            var url = String(res.data.url);
            if (res.data.sso_error) {
                // 实现类不可用：仍然打开默认地址，但把原因明确告诉用户
                toast(res.data.sso_error, 1, 6000);
                window.setTimeout(function () { openByMode(url, mode, tab); }, 900);
                return;
            }
            openByMode(url, mode, tab);
        });
    }

    function bindLaunch() {
        if (state.bound) { return; }
        state.bound = true;
        // 事件委托：图标是整表重绘的，绑在容器上一次即可
        var host = byId("macDesktop");
        if (!host) { return; }
        host.addEventListener("click", function (e) {
            var a = e.target && e.target.closest ? e.target.closest("[data-app-id]") : null;
            if (!a) { return; }
            // 站内相对地址、带 hash 的地址都交给后端拼最终地址，这里统一拦下来
            e.preventDefault();
            launch(findApp(a.getAttribute("data-app-id")));
        });
    }

    /* ------------------------------------------------------------------
     * 顶部菜单栏时钟（macOS 风格）
     * ------------------------------------------------------------------ */

    function tickClock() {
        var el = byId("macClock");
        if (!el) { return; }
        var now = new Date();
        var week = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][now.getDay()];
        var pad = function (n) { return (n < 10 ? "0" : "") + n; };
        el.textContent = (now.getMonth() + 1) + "月" + now.getDate() + "日 " + week
            + " " + pad(now.getHours()) + ":" + pad(now.getMinutes());
    }

    /* ------------------------------------------------------------------
     * 初始化
     * ------------------------------------------------------------------ */

    function load() {
        Oaones.api("appc.desktop", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) ? res.msg : "读取应用列表失败", 2);
                return;
            }
            var data = res.data || {};
            state.apps = data.apps || [];
            render();
        });
    }

    window.MacDesktop = {
        /** 页面加载（页壳在 DOMContentLoaded 之后调用；重复调用必须幂等） */
        load: function () {
            bindLaunch();
            tickClock();
            window.setInterval(tickClock, 10000);
            load();
        },
        refresh: load
    };
})();
