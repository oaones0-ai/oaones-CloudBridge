/**
 * OAones 日期 / 时间 / 日期时间 选择框（`/oaones` 下所有日期类文本框共用，不得逐页自造）。
 *
 * 三类文本框共用一份实现（标记 `data-oaones-date="date|time|datetime"`）：
 *   date     yyyy-MM-dd
 *   time     HH:mm:ss
 *   datetime yyyy-MM-dd HH:mm:ss
 *
 * 统一行为：
 *  1) 日历模式：面板主体就是月历（周日起始、固定 6 行、上下月补格灰显），
 *     选中日高亮、今天单独标注；
 *  2) 手动调整年月：面板顶部 `« ‹ [年] [月] › »`，年与月都是下拉框（可直接跳到任意年月），
 *     两侧是上一年 / 上一月 / 下一月 / 下一年；换年月只翻月历（不擅自改已选值），
 *     选中某天后才写回文本框；
 *  3) 手动输入：文本框本身可编辑，失焦或回车时按标准格式回写，支持
 *     `2026-09-15` / `2026/9/15` / `2026.9.15` / `20260915` / `2026年9月15日` 与
 *     `13:00:00` / `13:00` / `130000` / `1300` / `13`，日期时间之间用空格（或 `T`）分隔；
 *     写法不合法时用模式 1 提示示例并还原成上一次有效值，避免把非法值传给接口；
 *  4) 时间列（时间 / 日期时间）：时 / 分 / 秒 三列滚动选择（00~23 / 00~59 / 00~59）；
 *  5) 即时回写：面板里每选一次（选中某天、换时分秒、今天、此刻）立即写回文本框并派发
 *     `input` / `change` 事件，页面照旧用 `value` 取标准格式文本；
 *     「确定」提交并收起、「取消」/`Esc` 放弃本次选择（回到打开前的值）、「清空」清空；
 *  6) 面板固定定位、越界自动上翻 / 收进视口，点面板不打断输入框焦点，
 *     点面板外或滚动页面自动收起；隐藏页签里的字段不会弹出面板。
 *
 * 页面接入方式：
 *   <span class="dt-field" style="width:206px;">
 *     <input type="text" id="msgLogFrom" data-oaones-date="datetime"
 *            data-oaones-date-time="00:00:00" autocomplete="off" data-lpignore="true"
 *            placeholder="起 yyyy-MM-dd HH:mm:ss">
 *   </span>
 *   OaonesDate.init();                                                 // 扫描并接入（幂等，可重复调用）
 *   OaonesDate.attach("msgLogFrom", "datetime", { time: "23:59:59" });  // 单个接入
 *   OaonesDate.set("msgLogFrom", "2026-09-15 13:00:00");                // 赋值（按标准格式规范化）
 *   OaonesDate.clear("msgLogFrom");                                     // 清空（同步“清空”按钮与面板）
 *   OaonesDate.value("msgLogFrom");                                     // 取标准格式文本
 *   OaonesDate.open("msgLogFrom");                                      // 代码里打开面板
 *   OaonesDate.parse(text, type) / OaonesDate.format(parts, type);      // 纯解析 / 格式化
 *
 * 空值初值口径：文本框为空时打开面板，选中某天默认带 `data-oaones-date-time`
 * （缺省 `00:00:00`，配 `now` 表示当前时刻）——检索类“起/止”两框分别配
 * `00:00:00` / `23:59:59` 即可各自覆盖整天。
 */
(function (global) {
    "use strict";

    var DOC = global.document;

    /** 三类文本框的标准格式、示例与图标 */
    var TYPES = {
        date: { label: "日期", sample: "2026-09-15", icon: "date" },
        time: { label: "时间", sample: "13:00:00", icon: "time" },
        datetime: { label: "日期时间", sample: "2026-09-15 13:00:00", icon: "date" }
    };
    var WEEK = ["日", "一", "二", "三", "四", "五", "六"];
    var CAL_ROWS = 6;
    var HOUR_COUNT = 24;
    var MINUTE_COUNT = 60;
    var SECOND_COUNT = 60;
    var YEAR_MIN = 1900;
    var YEAR_MAX = 2099;
    var POP_ID = "oaonesDtPop";

    /** 日历 / 时钟图标（内联 SVG 静态串，随按钮一起生成） */
    var ICON_DATE = '<svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">'
        + '<rect x="1.6" y="3.1" width="12.8" height="11.3" rx="2" fill="none" stroke="currentColor" stroke-width="1.4"/>'
        + '<path d="M1.6 6.4h12.8" stroke="currentColor" stroke-width="1.4"/>'
        + '<path d="M5.1 1.6v3M10.9 1.6v3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>'
        + "</svg>";
    var ICON_TIME = '<svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">'
        + '<circle cx="8" cy="8" r="6.3" fill="none" stroke="currentColor" stroke-width="1.4"/>'
        + '<path d="M8 4.3v4.1l2.7 1.6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>'
        + "</svg>";

    var seq = 0;
    /** key -> item（item = {key, el, wrap, type, opt, defaultTime, lastValue}） */
    var items = {};
    /** 当前打开面板的 item key；空串表示面板收起 */
    var activeKey = "";
    /** 面板里的候选值（面板收起时为 null） */
    var draft = null;
    /** 面板里月历正在显示的月份 */
    var view = null;
    /** 打开面板时的原值（「取消」/`Esc` 回退用） */
    var snapshot = "";
    var pop = null;
    var yearRange = null;
    /** 面板内点击的原始事件：面板重绘会换掉被点的节点，靠它把“面板内点击”与“点面板外”区分开 */
    var popClickEvent = null;

    /* ------------------------------ 基础工具 ------------------------------ */

    function pad2(n) { return (n < 10 ? "0" : "") + n; }
    function byId(id) { return DOC.getElementById(id); }
    function num(v, dflt) { var n = parseInt(v, 10); return isNaN(n) ? dflt : n; }
    function attr(el, name) { return el && el.getAttribute ? el.getAttribute(name) : null; }
    function closest(el, sel) { return (el && el.closest) ? el.closest(sel) : null; }
    /** 某年某月的天数（month：1~12） */
    function daysInMonth(year, month) { return new Date(year, month, 0).getDate(); }
    function nowParts() {
        var d = new Date();
        return {
            y: d.getFullYear(), mo: d.getMonth() + 1, d: d.getDate(),
            h: d.getHours(), mi: d.getMinutes(), s: d.getSeconds()
        };
    }
    function cloneParts(p) {
        return { y: p.y, mo: p.mo, d: p.d, h: p.h, mi: p.mi, s: p.s };
    }
    /** 前置校验类提示走模式 1（同 js/toast.js 口径，禁止原生 alert） */
    function warn(msg) {
        if (global.OaonesToast) { global.OaonesToast.warn(msg); }
    }
    function fireChange(el) {
        try {
            el.dispatchEvent(new Event("input", { bubbles: true }));
            el.dispatchEvent(new Event("change", { bubbles: true }));
        } catch (e) {
            var ev = DOC.createEvent("HTMLEvents");
            ev.initEvent("change", true, false);
            el.dispatchEvent(ev);
        }
    }

    /* ------------------------------ 解析 / 格式化 ------------------------------ */

    /**
     * 宽松解析（月/日/时/分/秒都做真实范围校验）：
     *   日期 2026-09-15 / 2026/9/15 / 2026.9.15 / 20260915 / 2026年9月15日
     *   时间 13:00:00 / 13:00 / 130000 / 1300 / 13
     *   日期时间 日期 + 空格（或 T）+ 时间、紧凑 20260915130000；
     *   只写时间按“今天”，只写日期按 00:00:00
     * 解析成功返回 {y,mo,d,h,mi,s}，失败返回 null。
     */
    function parse(text, type) {
        var raw = String(text == null ? "" : text).trim();
        if (!raw) { return null; }
        var s = raw.replace(/[年月]/g, "-").replace(/日/g, " ")
            .replace(/[\/.]/g, "-").replace(/[Tt]/g, " ")
            .replace(/\s+/g, " ").trim();
        // 紧凑写法 20260915130000 → 2026-09-15 13:00:00
        var compact = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})$/.exec(s);
        if (compact) {
            s = compact[1] + "-" + compact[2] + "-" + compact[3]
                + " " + compact[4] + ":" + compact[5] + ":" + compact[6];
        }
        var datePart = "";
        var timePart = "";
        var sp = s.indexOf(" ");
        if (sp >= 0) {
            datePart = s.substring(0, sp);
            timePart = s.substring(sp + 1).trim();
        } else if (type === "time" || s.indexOf(":") >= 0) {
            timePart = s;
        } else {
            datePart = s;
        }
        var parts = nowParts();
        var hasDate = false;
        var hasTime = false;
        if (datePart) {
            var dm = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(datePart);
            if (!dm) { dm = /^(\d{4})(\d{2})(\d{2})$/.exec(datePart); }
            if (!dm) { return null; }
            var y = +dm[1];
            var mo = +dm[2];
            var d = +dm[3];
            if (mo < 1 || mo > 12 || d < 1 || d > daysInMonth(y, mo)) { return null; }
            parts.y = y;
            parts.mo = mo;
            parts.d = d;
            hasDate = true;
        }
        if (timePart) {
            var tm = /^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$/.exec(timePart);
            if (!tm) {
                var compact = /^(\d{2})(\d{2})(\d{2})$/.exec(timePart)
                    || /^(\d{2})(\d{2})$/.exec(timePart)
                    || /^(\d{1,2})$/.exec(timePart);
                if (!compact) { return null; }
                tm = [null, compact[1], (compact[2] === undefined ? "0" : compact[2]),
                    (compact[3] === undefined ? "0" : compact[3])];
            }
            var h = +tm[1];
            var mi = +tm[2];
            var sec = +(tm[3] === undefined ? 0 : tm[3]);
            if (h > 23 || mi > 59 || sec > 59) { return null; }
            parts.h = h;
            parts.mi = mi;
            parts.s = sec;
            hasTime = true;
        }
        if (type === "date" && !hasDate) { return null; }
        if (type === "time" && !hasTime) { return null; }
        if (type === "datetime" && !hasDate && !hasTime) { return null; }
        if (!hasTime) { parts.h = 0; parts.mi = 0; parts.s = 0; }
        return parts;
    }

    /** 按类型输出标准格式（date：yyyy-MM-dd；time：HH:mm:ss；datetime：yyyy-MM-dd HH:mm:ss） */
    function format(parts, type) {
        if (!parts) { return ""; }
        var d = parts.y + "-" + pad2(parts.mo) + "-" + pad2(parts.d);
        var t = pad2(parts.h) + ":" + pad2(parts.mi) + ":" + pad2(parts.s);
        if (type === "date") { return d; }
        if (type === "time") { return t; }
        return d + " " + t;
    }

    /* ------------------------------ 实例登记 ------------------------------ */

    function itemOf(target) {
        if (!target) { return null; }
        // 组件内部拿到的是登记项本身（open/clear/set 都按 id、元素、登记项三种入参兼容）
        if (typeof target === "object" && target.el && target.type && items[target.key]) { return target; }
        if (typeof target === "string") {
            if (items[target]) { return items[target]; }
            var el = byId(target);
            return (el && el.__oaonesDtKey && items[el.__oaonesDtKey]) ? items[el.__oaonesDtKey] : null;
        }
        return (target.__oaonesDtKey && items[target.__oaonesDtKey]) ? items[target.__oaonesDtKey] : null;
    }

    /** “清空”按钮只在有值时出现（对齐 .filter-picker 的 has-value 口径） */
    function syncField(item) {
        if (item.wrap && item.wrap.classList) {
            item.wrap.classList.toggle("dt-has-value", !!String(item.el.value || "").trim());
        }
    }

    function setValue(item, value, fire) {
        var next = String(value == null ? "" : value);
        var changed = item.el.value !== next;
        if (changed) {
            item.el.value = next;
        }
        item.lastValue = next;
        syncField(item);
        if (changed && fire) { fireChange(item.el); }
    }

    /** 空值初值：按 data-oaones-date-time 补齐时分秒（缺省 00:00:00，"now" 取当前时刻） */
    function withDefaultTime(parts) {
        var out = cloneParts(parts);
        var item = items[activeKey];
        var dflt = String((item && item.defaultTime) || "00:00:00");
        if (dflt === "now") { return out; }
        var m = /^(\d{1,2}):(\d{1,2}):(\d{1,2})$/.exec(dflt);
        out.h = m ? +m[1] : 0;
        out.mi = m ? +m[2] : 0;
        out.s = m ? +m[3] : 0;
        return out;
    }

    /* ------------------------------ 面板骨架 ------------------------------ */

    function popHtml() {
        var week = "";
        for (var i = 0; i < WEEK.length; i++) { week += "<span>" + WEEK[i] + "</span>"; }
        return ""
            + '<div class="dt-pop-head">'
            + '<div class="dt-cal-head">'
            + '<button type="button" class="dt-nav" data-dt-nav="py" title="上一年">&#171;</button>'
            + '<button type="button" class="dt-nav" data-dt-nav="pm" title="上一月">&#8249;</button>'
            + '<select class="dt-year" data-dt-pick="year" title="年" aria-label="年"></select>'
            + '<select class="dt-month" data-dt-pick="month" title="月" aria-label="月"></select>'
            + '<button type="button" class="dt-nav" data-dt-nav="nm" title="下一月">&#8250;</button>'
            + '<button type="button" class="dt-nav" data-dt-nav="ny" title="下一年">&#187;</button>'
            + "</div>"
            + '<span class="dt-gap"></span>'
            + '<button type="button" class="dt-quick dt-quick-now" data-dt-quick="now" title="当前日期时间">此刻</button>'
            + '<button type="button" class="dt-quick dt-quick-today" data-dt-quick="today" title="今天">今天</button>'
            + "</div>"
            + '<div class="dt-pop-body">'
            + '<div class="dt-cal">'
            + '<div class="dt-week">' + week + "</div>"
            + '<div class="dt-days" data-dt-days></div>'
            + "</div>"
            + '<div class="dt-time">'
            + '<div class="dt-time-head"><span>时间</span><span class="dt-time-tip">时 / 分 / 秒</span></div>'
            + '<div class="dt-time-cols">'
            + '<div class="dt-time-col"><div class="dt-time-col-title">时</div>'
            + '<div class="dt-time-list" data-dt-col="h"></div></div>'
            + '<div class="dt-time-col"><div class="dt-time-col-title">分</div>'
            + '<div class="dt-time-list" data-dt-col="mi"></div></div>'
            + '<div class="dt-time-col"><div class="dt-time-col-title">秒</div>'
            + '<div class="dt-time-list" data-dt-col="s"></div></div>'
            + "</div>"
            + "</div>"
            + "</div>"
            + '<div class="dt-pop-foot">'
            + '<button type="button" class="btn small secondary" data-dt-act="clear">清空</button>'
            + '<span class="dt-gap"></span>'
            + '<button type="button" class="btn small secondary" data-dt-act="cancel">取消</button>'
            + '<button type="button" class="btn small" data-dt-act="ok">确定</button>'
            + "</div>";
    }

    function buildYearOptions() {
        if (!pop) { return; }
        var sel = pop.querySelector('[data-dt-pick="year"]');
        if (!sel) { return; }
        var html = "";
        for (var y = YEAR_MIN; y <= YEAR_MAX; y++) { html += '<option value="' + y + '">' + y + "</option>"; }
        sel.innerHTML = html;
        yearRange = { min: YEAR_MIN, max: YEAR_MAX };
    }

    function buildMonthOptions() {
        var sel = pop.querySelector('[data-dt-pick="month"]');
        if (!sel) { return; }
        var html = "";
        for (var m = 1; m <= 12; m++) { html += '<option value="' + m + '">' + m + " 月</option>"; }
        sel.innerHTML = html;
    }

    /** 时 / 分 / 秒三列候选（00~23 / 00~59 / 00~59），只在建面板时生成一次 */
    function buildTimeColumns() {
        var units = [["h", HOUR_COUNT], ["mi", MINUTE_COUNT], ["s", SECOND_COUNT]];
        for (var u = 0; u < units.length; u++) {
            var list = pop.querySelector('[data-dt-col="' + units[u][0] + '"]');
            if (!list) { continue; }
            var html = "";
            for (var i = 0; i < units[u][1]; i++) {
                html += '<button type="button" class="dt-time-item" data-dt-time="' + units[u][0]
                    + '" data-dt-val="' + i + '">' + pad2(i) + "</button>";
            }
            list.innerHTML = html;
        }
    }

    function ensurePop() {
        if (pop && DOC.body && DOC.body.contains(pop)) { return pop; }
        if (!DOC.body) { return null; }
        pop = DOC.createElement("div");
        pop.id = POP_ID;
        pop.className = "dt-pop";
        pop.innerHTML = popHtml();
        // 点面板不打断输入框焦点（原生 select 除外，否则拉不出下拉列表）
        pop.addEventListener("mousedown", function (e) {
            if (e.target && e.target.tagName === "SELECT") { return; }
            e.preventDefault();
        });
        pop.addEventListener("click", onPopClick);
        pop.addEventListener("change", onPopChange);
        DOC.body.appendChild(pop);
        buildYearOptions();
        buildMonthOptions();
        buildTimeColumns();
        return pop;
    }

    /* ------------------------------ 面板渲染 ------------------------------ */

    function renderYearMonth() {
        var ySel = pop.querySelector('[data-dt-pick="year"]');
        var mSel = pop.querySelector('[data-dt-pick="month"]');
        if (ySel) {
            if (!yearRange || view.y < yearRange.min || view.y > yearRange.max) {
                yearRange = yearRange || { min: view.y, max: view.y };
                // 超出 1900~2099 的年份补进选项（比 1900 小就补在最前，比 2099 大就补在最后）
                ySel.innerHTML = view.y < yearRange.min
                    ? '<option value="' + view.y + '">' + view.y + "</option>" + ySel.innerHTML
                    : ySel.innerHTML + '<option value="' + view.y + '">' + view.y + "</option>";
                yearRange.min = Math.min(yearRange.min, view.y);
                yearRange.max = Math.max(yearRange.max, view.y);
            }
            ySel.value = String(view.y);
        }
        if (mSel) { mSel.value = String(view.mo); }
    }

    function renderDays() {
        var box = pop.querySelector("[data-dt-days]");
        if (!box) { return; }
        var y = view.y;
        var mo = view.mo;
        var first = new Date(y, mo - 1, 1).getDay();
        var dim = daysInMonth(y, mo);
        var prevY = mo === 1 ? y - 1 : y;
        var prevMo = mo === 1 ? 12 : mo - 1;
        var prevDim = daysInMonth(prevY, prevMo);
        var nextY = mo === 12 ? y + 1 : y;
        var nextMo = mo === 12 ? 1 : mo + 1;
        var cells = [];
        var i;
        for (i = first; i > 0; i--) { cells.push({ y: prevY, mo: prevMo, d: prevDim - i + 1 }); }
        for (i = 1; i <= dim; i++) { cells.push({ y: y, mo: mo, d: i }); }
        var nextDay = 1;
        while (cells.length < CAL_ROWS * 7) {
            cells.push({ y: nextY, mo: nextMo, d: nextDay++ });
        }
        var today = nowParts();
        var html = "";
        for (i = 0; i < cells.length; i++) {
            var c = cells[i];
            var cls = "dt-day";
            if (c.y !== y || c.mo !== mo) { cls += " other"; }
            if (c.y === today.y && c.mo === today.mo && c.d === today.d) { cls += " today"; }
            if (draft && c.y === draft.y && c.mo === draft.mo && c.d === draft.d) { cls += " active"; }
            html += '<button type="button" class="' + cls + '" data-dt-day="' + c.d
                + '" data-dt-y="' + c.y + '" data-dt-mo="' + c.mo + '">' + c.d + "</button>";
        }
        box.innerHTML = html;
    }

    function renderTime() {
        var units = ["h", "mi", "s"];
        for (var u = 0; u < units.length; u++) {
            var list = pop.querySelector('[data-dt-col="' + units[u] + '"]');
            if (!list) { continue; }
            var cur = draft ? draft[units[u]] : -1;
            for (var i = 0; i < list.children.length; i++) {
                list.children[i].classList.toggle("active", i === cur);
            }
        }
    }

    function renderPanel() {
        var item = items[activeKey];
        if (!item || !pop) { return; }
        pop.className = "dt-pop dt-mode-" + item.type + " dt-open";
        if (item.type !== "time") {
            renderYearMonth();
            renderDays();
        }
        if (item.type !== "date") { renderTime(); }
    }

    /** 打开时把已选中的年 / 月 / 日 / 时 / 分 / 秒滚进可视区 */
    function scrollActiveIntoView() {
        if (!pop) { return; }
        var actives = pop.querySelectorAll(".dt-time-item.active");
        for (var i = 0; i < actives.length; i++) {
            var box = actives[i].parentNode;
            var top = actives[i].offsetTop - (box.clientHeight - actives[i].offsetHeight) / 2;
            box.scrollTop = top > 0 ? top : 0;
        }
    }

    function placePop() {
        var item = items[activeKey];
        if (!item || !pop) { return; }
        var r = item.el.getBoundingClientRect();
        var w = pop.offsetWidth;
        var h = pop.offsetHeight;
        var left = Math.round(r.left);
        var maxLeft = global.innerWidth - w - 12;
        if (left > maxLeft) { left = maxLeft; }
        if (left < 12) { left = 12; }
        var top = Math.round(r.bottom + 6);
        if (top + h > global.innerHeight - 12) {
            top = Math.round(r.top - h - 6);
            if (top < 12) { top = 12; }
        }
        pop.style.left = left + "px";
        pop.style.top = top + "px";
    }

    /* ------------------------------ 面板交互 ------------------------------ */

    function open(target) {
        var item = itemOf(target);
        if (!item) { return; }
        if (item.el.disabled || item.el.readOnly) { return; }
        // 隐藏页签 / 隐藏弹框里的字段：此时量不到位置，不打开面板
        if (!item.el.getClientRects || !item.el.getClientRects().length) { return; }
        if (activeKey === item.key && pop && pop.className.indexOf("dt-open") >= 0) { return; }
        if (!ensurePop()) { return; }
        activeKey = item.key;
        snapshot = item.el.value;
        draft = parse(item.el.value, item.type);
        var base = draft ? cloneParts(draft) : nowParts();
        view = { y: base.y, mo: base.mo };
        renderPanel();
        placePop();
        scrollActiveIntoView();
    }

    function closePop() {
        if (pop) { pop.className = "dt-pop"; }
        activeKey = "";
        draft = null;
        view = null;
        snapshot = "";
    }

    /** 面板里选中的结果即时回写文本框（派发 input/change，页面照旧读 value） */
    function applyDraft() {
        var item = items[activeKey];
        if (!item || !draft) { return; }
        setValue(item, format(draft, item.type), true);
    }

    /** 换月历显示的年月（只翻月历，不动已选值） */
    function setViewMonth(year, month) {
        if (!view) { return; }
        var total = year * 12 + (month - 1);
        view.y = Math.floor(total / 12);
        view.mo = (total % 12) + 1;
        renderPanel();
    }

    function onPopClick(e) {
        popClickEvent = e;
        var t = e.target;
        var item = items[activeKey];
        if (!item) { return; }
        var node = closest(t, "[data-dt-nav]");
        if (node) {
            var nav = attr(node, "data-dt-nav");
            if (nav === "py") { setViewMonth(view.y - 1, view.mo); }
            else if (nav === "ny") { setViewMonth(view.y + 1, view.mo); }
            else if (nav === "pm") { setViewMonth(view.y, view.mo - 1); }
            else { setViewMonth(view.y, view.mo + 1); }
            return;
        }
        node = closest(t, "[data-dt-day]");
        if (node) {
            var day = num(attr(node, "data-dt-day"), 1);
            var y = num(attr(node, "data-dt-y"), view.y);
            var mo = num(attr(node, "data-dt-mo"), view.mo);
            if (!draft) { draft = withDefaultTime(nowParts()); }
            draft.y = y;
            draft.mo = mo;
            draft.d = day;
            view.y = y;
            view.mo = mo;
            applyDraft();
            renderPanel();
            // 日期型只有日期，选完即收起；日期时间型留在面板里继续调时分秒
            if (item.type === "date") { closePop(); }
            return;
        }
        node = closest(t, "[data-dt-time]");
        if (node) {
            if (!draft) { draft = withDefaultTime(nowParts()); }
            draft[attr(node, "data-dt-time")] = num(attr(node, "data-dt-val"), 0);
            applyDraft();
            renderPanel();
            return;
        }
        node = closest(t, "[data-dt-quick]");
        if (node) {
            var quick = attr(node, "data-dt-quick");
            draft = quick === "now" ? nowParts() : withDefaultTime(nowParts());
            applyDraft();
            closePop();
            return;
        }
        node = closest(t, "[data-dt-act]");
        if (node) {
            var act = attr(node, "data-dt-act");
            if (act === "clear") {
                draft = null;
                setValue(item, "", true);
            } else if (act === "cancel") {
                setValue(item, snapshot, false);
            } else if (draft) {
                applyDraft();
            }
            closePop();
        }
    }

    function onPopChange(e) {
        var t = e.target;
        var pick = attr(t, "data-dt-pick");
        if (!pick || !activeKey || !view) { return; }
        if (pick === "year") { setViewMonth(num(t.value, view.y), view.mo); }
        else { setViewMonth(view.y, num(t.value, view.mo)); }
    }

    /* ------------------------------ 输入框交互 ------------------------------ */

    /** 失焦 / 回车：按标准格式规范化；写法不合法时提示示例并还原上一次有效值 */
    function commitText(item) {
        var text = String(item.el.value || "").trim();
        var parts = parse(text, item.type);
        if (!text) {
            setValue(item, "", false);
            return true;
        }
        if (!parts) {
            warn("「" + TYPES[item.type].label + "」格式不正确，示例：" + TYPES[item.type].sample);
            setValue(item, item.lastValue || "", false);
            return false;
        }
        setValue(item, format(parts, item.type), false);
        return true;
    }

    function onInput() {
        var item = items[this.__oaonesDtKey];
        if (!item) { return; }
        syncField(item);
        // 手输过程中同步面板高亮（面板打开且能解析时）
        if (activeKey !== item.key) { return; }
        var parts = parse(item.el.value, item.type);
        if (!parts) { return; }
        draft = parts;
        view = { y: parts.y, mo: parts.mo };
        renderPanel();
    }

    function onKeydown(e) {
        var item = items[this.__oaonesDtKey];
        if (!item) { return; }
        if (e.key === "Enter") {
            e.preventDefault();
            if (commitText(item)) { closePop(); }
        } else if (e.key === "ArrowDown" && e.altKey) {
            e.preventDefault();
            open(item);
        }
    }

    /* ------------------------------ 接入 ------------------------------ */

    /**
     * 接入一个文本框：`input[data-oaones-date]` 直接 `OaonesDate.init()` 即可，
     * 也可代码里指定 id / 元素。重复接入同一元素不会重复挂按钮。
     */
    function attach(target, type, opt) {
        var el = (typeof target === "string") ? byId(target) : target;
        if (!el) { return null; }
        if (el.__oaonesDtKey && items[el.__oaonesDtKey]) { return items[el.__oaonesDtKey]; }
        var dtType = String(type || attr(el, "data-oaones-date") || "datetime").trim();
        if (!TYPES[dtType]) { dtType = "datetime"; }
        opt = opt || {};
        var item = {
            key: "oaonesDt" + (++seq),
            el: el,
            type: dtType,
            lastValue: String(el.value || ""),
            defaultTime: String(opt.time || attr(el, "data-oaones-date-time") || "00:00:00")
        };
        var wrap = (el.parentNode && el.parentNode.classList
            && el.parentNode.classList.contains("dt-field")) ? el.parentNode : null;
        if (!wrap) {
            wrap = DOC.createElement("span");
            wrap.className = "dt-field";
            if (el.style && el.style.width) {
                wrap.style.width = el.style.width;
                el.style.width = "";
            }
            el.parentNode.insertBefore(wrap, el);
            wrap.appendChild(el);
        }
        item.wrap = wrap;
        if (el.classList) { el.classList.add("dt-input"); }
        var icon = DOC.createElement("button");
        icon.type = "button";
        icon.className = "dt-icon";
        icon.innerHTML = TYPES[dtType].icon === "time" ? ICON_TIME : ICON_DATE;
        icon.title = "选择" + TYPES[dtType].label + "（" + TYPES[dtType].sample + "）";
        icon.setAttribute("aria-label", icon.title);
        // 防止点图标时输入框失焦（失焦会触发格式校验）
        icon.addEventListener("mousedown", function (e) { e.preventDefault(); });
        icon.addEventListener("click", function (e) {
            e.preventDefault();
            if (activeKey === item.key) { closePop(); } else { open(item); }
        });
        var clearBtn = DOC.createElement("button");
        clearBtn.type = "button";
        clearBtn.className = "dt-clear";
        clearBtn.innerHTML = "&times;";
        clearBtn.title = "清空";
        clearBtn.setAttribute("aria-label", "清空");
        clearBtn.addEventListener("mousedown", function (e) { e.preventDefault(); });
        clearBtn.addEventListener("click", function (e) {
            e.preventDefault();
            clear(item);
        });
        wrap.appendChild(icon);
        wrap.appendChild(clearBtn);
        el.__oaonesDtKey = item.key;
        items[item.key] = item;
        el.addEventListener("click", function () { open(item); });
        el.addEventListener("input", onInput);
        el.addEventListener("keydown", onKeydown);
        el.addEventListener("blur", function () { commitText(item); });
        syncField(item);
        return item;
    }

    /** 扫描页面上的 `input[data-oaones-date]` 并接入（幂等，可重复调用） */
    function init(root) {
        var scope = root || DOC;
        var list = (scope.querySelectorAll ? scope.querySelectorAll("input[data-oaones-date]") : []);
        for (var i = 0; i < list.length; i++) {
            attach(list[i], null, null);
        }
    }

    /** 赋值（按标准格式规范化；传空即清空） */
    function set(target, value) {
        var item = itemOf(target);
        if (!item) { return ""; }
        var parts = parse(value, item.type);
        setValue(item, parts ? format(parts, item.type) : "", false);
        if (activeKey === item.key && draft) {
            draft = parts;
            if (draft) { view = { y: draft.y, mo: draft.mo }; }
            renderPanel();
        }
        return item.el.value;
    }

    /** 清空（同时复位“清空”按钮与面板） */
    function clear(target) {
        var item = itemOf(target);
        if (!item) { return; }
        setValue(item, "", true);
        if (activeKey === item.key) { closePop(); }
    }

    /** 取标准格式文本（页面查询直接用它，等价于 input.value 去空格） */
    function value(target) {
        var item = itemOf(target);
        return item ? String(item.el.value || "").trim() : "";
    }

    DOC.addEventListener("click", function (e) {
        if (!activeKey || !pop) { return; }
        // 面板内点击（哪怕面板刚重绘过、被点的节点已经换掉）不算“点面板外”
        if (e === popClickEvent) { popClickEvent = null; return; }
        var item = items[activeKey];
        if (pop.contains(e.target)) { return; }
        if (item && (e.target === item.el || (item.wrap && item.wrap.contains(e.target)))) { return; }
        // 点面板外：保留面板里已选的值（每次选择都已即时回写）
        closePop();
    });
    DOC.addEventListener("keydown", function (e) {
        if (e.key !== "Escape" || !activeKey) { return; }
        var item = items[activeKey];
        if (item) { setValue(item, snapshot, false); }
        closePop();
    });
    global.addEventListener("resize", function () { if (activeKey) { placePop(); } });
    global.addEventListener("scroll", function () { if (activeKey) { placePop(); } }, true);

    global.OaonesDate = {
        init: init,
        attach: attach,
        set: set,
        clear: clear,
        value: value,
        open: open,
        close: closePop,
        parse: parse,
        format: format,
        types: TYPES
    };

    if (DOC.readyState === "loading") {
        DOC.addEventListener("DOMContentLoaded", function () { init(); });
    } else {
        init();
    }
})(window);
