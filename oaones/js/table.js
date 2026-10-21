/**
 * OAones 列表组件（全模块唯一实现）：分页 / 勾选与全选 / 仅显示已选 / 表头拖拽列宽。
 *
 * 口径见 skill.md 第一节第 10 条（10.1 能力总览 / 10.2 分页 / 10.3 勾选与全选 /
 * 10.4 仅显示已选 / 10.5 列宽拖拽 / 10.8 页面接入清单）：
 *   - 分页：每页条数固定预设 10 / 20 / 50 / 100 / 1000，默认 10，切换档位回到第 1 页，
 *     分页条含总数与页码折叠，只有一页也显示总数；
 *   - 全选当页：表头复选框是三态，只作用于当前页的行（参数传当页数量）；
 *   - 跨页全选：渲染在“表头上方”（表头行之前）的一条操作条，左侧复选框与表头复选框同列对齐；
 *     勾选即选中“当前筛选条件下的全部行”（含未翻到的页），取消勾选即取消跨页全选，
 *     与“全选当页”是两个独立功能，已选状态翻页后保持；
 *   - 仅显示已选：统计行末尾提供“仅显示已选择数据 / 显示所有数据”切换，只改变列表显示范围，
 *     不改变勾选集合，便于批量操作前核对；
 *   - 表头拖拽列宽：拖动表头单元格右边缘调整该列宽度，列变窄时单元格内容自动换行，
 *     翻页/列表重绘后列宽保持。
 *
 * 对外 API（window.OaonesTable）：
 *   pager({key, el, onChange})        列表分页器（resetIfChanged / apply / render / hide / show）
 *   syncCheckAll(box, selected, total) 表头三态复选框（selected/total 传“当页”数量）
 *   checkAllTitle(box, what)           表头复选框统一悬浮提示（只作用于当前页）
 *   crossPageBar(table, opt)           表头上方的跨页全选条（复选框 + 文案，勾选即跨页全选）
 *   listActionsHtml(opt)               统计行末尾操作组 HTML（仅显示已选）
 *   refreshListActions(listEl, opt)    就地刷新上面这组按钮（勾选变化时不整表重绘）
 *   resizable(table, {key, min})       表头拖拽列宽（列宽按 key 记忆）
 *
 * 页面只负责按自己的口径算出过滤后的行数组与“每行是否可勾选/已勾选”，
 * 其余（切片、分页条、表头三态、统计行按钮、列宽）都交给本文件，
 * 页面不得各写一套分页条、全选或拖拽实现。
 */
(function (window, document) {
    "use strict";

    /** 每页条数预设档位（skill.md 固定 5 档） */
    var PAGE_SIZES = [10, 20, 50, 100, 1000];
    var DEFAULT_PAGE_SIZE = 10;
    /** 页码按钮：当前页左右各显示的页数 */
    var PAGE_WINDOW = 2;
    /** 列宽拖拽的最小宽度（低于该宽度的列视为复选框/序号等固定列，不提供拖拽把手） */
    var DEFAULT_MIN_COL_WIDTH = 60;

    var pagerStates = {};
    var colWidthStore = {};

    function byId(id) { return document.getElementById(id); }

    function num(value, dft) {
        var n = parseInt(value, 10);
        return isNaN(n) ? dft : n;
    }

    function contains(list, value) {
        for (var i = 0; i < list.length; i++) {
            if (list[i] === value) { return true; }
        }
        return false;
    }

    function esc(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    /* ==================================================================
     * 一、分页
     * ================================================================== */

    /** 页码序列：首末页 + 当前页附近若干页，中间用 ... 折叠 */
    function pageNumbers(page, pageCount) {
        var out = [];
        var start = Math.max(1, page - PAGE_WINDOW);
        var end = Math.min(pageCount, page + PAGE_WINDOW);
        if (start > 1) {
            out.push(1);
            if (start > 2) { out.push("..."); }
        }
        for (var i = start; i <= end; i++) { out.push(i); }
        if (end < pageCount) {
            if (end < pageCount - 1) { out.push("..."); }
            out.push(pageCount);
        }
        return out;
    }

    function pageBtn(action, label, disabled, active) {
        return "<button type='button' class='pager-btn" + (active ? " active" : "") + "'"
            + " data-page='" + action + "'"
            + (disabled ? " disabled" : "")
            + (active ? " aria-current='page'" : "")
            + ">" + label + "</button>";
    }

    /**
     * 创建一个列表分页器。
     *
     * opt = {
     *   key:      string   唯一标识（同一列表固定不变，用于记忆每页条数与当前页）
     *   el:       Element  分页条容器（<div class="pager">）
     *   onChange: function 翻页 / 切换每页条数后的重绘回调（页面重新渲染列表）
     * }
     *
     * 用法（页面每次渲染时）：
     *   pager.resetIfChanged(筛选条件签名);   // 筛选条件变化时回到第 1 页
     *   var pageRows = pager.apply(全部过滤后的行);  // 当前页要渲染的行
     *   ...渲染 pageRows...
     *   pager.render();                       // 渲染分页条
     */
    function pager(opt) {
        opt = opt || {};
        var key = String(opt.key || "");
        var el = opt.el && typeof opt.el === "object" ? opt.el : byId(opt.el);
        var onChange = opt.onChange || function () {};
        var st = pagerStates[key];
        if (!st) {
            st = { size: num(opt.size, DEFAULT_PAGE_SIZE), page: 1, total: 0, pageCount: 1, sig: null };
            pagerStates[key] = st;
        }
        if (!contains(PAGE_SIZES, st.size)) { st.size = DEFAULT_PAGE_SIZE; }

        function render() {
            if (!el) { return; }
            var html = "";
            html += "<span class='pager-info'>共 <b>" + st.total + "</b> 条，第 <b>" + st.page
                + "</b>/<b>" + st.pageCount + "</b> 页</span>";
            html += "<span class='pager-size'>每页 <select class='pager-size-select' title='每页显示条数'>";
            for (var i = 0; i < PAGE_SIZES.length; i++) {
                var size = PAGE_SIZES[i];
                html += "<option value='" + size + "'" + (size === st.size ? " selected" : "") + ">"
                    + size + "</option>";
            }
            html += "</select> 条</span>";
            html += "<span class='pager-btns'>";
            html += pageBtn("first", "首页", st.page <= 1, false);
            html += pageBtn("prev", "上一页", st.page <= 1, false);
            var nums = pageNumbers(st.page, st.pageCount);
            for (var j = 0; j < nums.length; j++) {
                if (nums[j] === "...") {
                    html += "<span class='pager-gap'>…</span>";
                } else {
                    html += pageBtn(nums[j], String(nums[j]), false, nums[j] === st.page);
                }
            }
            html += pageBtn("next", "下一页", st.page >= st.pageCount, false);
            html += pageBtn("last", "末页", st.page >= st.pageCount, false);
            html += "</span>";
            el.innerHTML = html;
        }

        function goto(action) {
            var target = st.page;
            if (action === "first") { target = 1; }
            else if (action === "prev") { target = st.page - 1; }
            else if (action === "next") { target = st.page + 1; }
            else if (action === "last") { target = st.pageCount; }
            else { target = num(action, st.page); }
            if (target < 1) { target = 1; }
            if (target > st.pageCount) { target = st.pageCount; }
            if (target === st.page) { return; }
            st.page = target;
            render();
            onChange();
        }

        if (el && el.getAttribute("data-pager-bound") !== "1") {
            el.setAttribute("data-pager-bound", "1");
            el.addEventListener("click", function (e) {
                var btn = e.target && e.target.closest ? e.target.closest("button[data-page]") : null;
                if (!btn || !el.contains(btn)) { return; }
                goto(btn.getAttribute("data-page"));
            });
            el.addEventListener("change", function (e) {
                var t = e.target;
                if (!t || !t.classList || !t.classList.contains("pager-size-select")) { return; }
                st.size = num(t.value, DEFAULT_PAGE_SIZE);
                if (!contains(PAGE_SIZES, st.size)) { st.size = DEFAULT_PAGE_SIZE; }
                st.page = 1;
                render();
                onChange();
            });
        }

        return {
            key: key,
            size: function () { return st.size; },
            page: function () { return st.page; },
            total: function () { return st.total; },
            pageCount: function () { return st.pageCount; },
            /** 回到第 1 页（不重绘） */
            reset: function () { st.page = 1; },
            /** 筛选条件签名变化时回到第 1 页（筛选、搜索关键字、租户变化都走这里） */
            resetIfChanged: function (sig) {
                sig = String(sig == null ? "" : sig);
                if (st.sig !== sig) {
                    st.sig = sig;
                    st.page = 1;
                }
            },
            /** 按当前页码/每页条数切出本页要渲染的行 */
            apply: function (rows) {
                rows = rows || [];
                st.total = rows.length;
                st.pageCount = Math.max(1, Math.ceil(st.total / st.size));
                if (st.page > st.pageCount) { st.page = st.pageCount; }
                if (st.page < 1) { st.page = 1; }
                var start = (st.page - 1) * st.size;
                return rows.slice(start, start + st.size);
            },
            render: render,
            /** 列表整体不展示（未选租户 / 未搜索 / 切到卡片视图）时隐藏分页条 */
            hide: function () {
                if (!el) { return; }
                el.style.display = "none";
                el.innerHTML = "";
            },
            show: function () {
                if (!el) { return; }
                el.style.display = "";
            }
        };
    }

    /* ==================================================================
     * 二、勾选与全选：行首复选框 / 全选当页（表头三态）/ 跨页全选（表头上方）/ 仅显示已选（统计行）
     * ================================================================== */

    /**
     * 同步表头复选框三态：selected = 当前页已选行数，total = 当前页可选行数
     * （表头复选框只作用于当页，跨页全选另见 crossPageBar：表头上方的那一条）。
     * 页面负责维护“选中集合”，本方法只负责三态显示。
     */
    function syncCheckAll(box, selected, total) {
        if (!box) { return; }
        selected = num(selected, 0);
        total = num(total, 0);
        box.disabled = total <= 0;
        box.checked = total > 0 && selected >= total;
        box.indeterminate = selected > 0 && selected < total;
    }

    /** 表头复选框统一提示语：只作用于当前页；跨页全选在表头上方的那一条（crossPageBar） */
    function checkAllTitle(box, what) {
        if (!box) { return; }
        box.title = "全选/取消全选当前页的" + (what || "行")
            + "；需要选中当前筛选条件下的全部行（含未翻到的页）请用表头上方的“跨页全选”";
    }

    /**
     * “仅显示已选择数据 / 显示所有数据”切换按钮（由页面拼进列表统计行末尾，统计数据之后）。
     * 按钮文案是“点下去会进入的视图”：正在显示全部时是“仅显示已选择数据”，
     * 只看已选时是“显示所有数据”；括号里带上“已选 N / 共 M”，跨页已选也能一眼核对。
     * 只切换列表显示范围，不改变跨页勾选集合。
     */
    function onlyCheckedBtnHtml(onlyChecked, checked, total, what) {
        checked = num(checked, 0);
        total = num(total, 0);
        var text = onlyChecked ? "显示所有数据" : "仅显示已选择数据";
        var tip = onlyChecked
            ? "返回全部数据：当前只显示已勾选的数据"
            : "只看已勾选的数据" + (what ? "（" + what + "）" : "")
                + "（含未翻到的页已勾选项）；再点一次回到全部数据";
        return "<button type='button' class='link only-checked-btn" + (onlyChecked ? " active" : "") + "'"
            + " title='" + esc(tip) + "'>" + text
            + (checked ? "（已选 " + checked + (total ? " / 共 " + total : "") + "）" : "")
            + "</button>";
    }

    /**
     * 跨页全选条（独立功能，不是表头复选框）：渲染在**表头上方**（表头行之前）的一条操作条，
     * 左侧复选框与表头复选框落在**同一列**（同一个表格、同一列宽），因此天然左右对齐；
     * 勾选 = 选中“当前筛选条件下的全部行”（含未翻到的页），取消勾选 = 取消跨页全选。
     *
     * 本方法可重复调用（幂等）：首次调用建条，之后只就地更新复选框状态与文案，
     * 不重建节点，行内勾选/取消后调用它也不会打断表头复选框与列宽拖拽。
     *
     * opt = {
     *   total:       当前筛选条件下可勾选的行数，<=0 时整条隐藏
     *   unit:        单位（人 / 个 / 条）
     *   allSelected: 是否已全部选中（决定复选框勾选态）
     *   checked:     已选数量（写进条上的“已选 N”提示，便于核对）
     *   what:        对象说明（“可同步的 OA 人员”等），用于悬浮提示
     *   onChange:    function(checked) 勾选/取消时的回调，页面在这里全选/清空勾选集合
     * }
     */
    function crossPageBar(table, opt) {
        if (!table || !table.tHead) { return; }
        opt = opt || {};
        var total = num(opt.total, 0);
        var row = table.tHead.querySelector("tr.cross-page-row");
        if (total <= 0) {
            if (row) { row.style.display = "none"; }
            return;
        }
        if (!row) {
            var headRow = table.tHead.rows && table.tHead.rows[0] ? table.tHead.rows[0] : null;
            var cols = headRow && headRow.cells ? headRow.cells.length : 1;
            row = document.createElement("tr");
            row.className = "cross-page-row";
            // 第一格放复选框：与表头复选框同列（同一个 table，同一列宽），无需另外对齐
            var chkCell = document.createElement("td");
            // 与表头复选框单元格同样的对齐（左对齐 + 同样的内边距），保证两个复选框上下对齐
            chkCell.className = "cross-page-chk";
            var box0 = document.createElement("input");
            box0.type = "checkbox";
            box0.className = "cross-page-check";
            box0.id = "crossPageCheck-" + String(table.id || "oaones-table");
            chkCell.appendChild(box0);
            // 第二格（跨剩余列）放文案，点文案等同点复选框
            var textCell = document.createElement("td");
            textCell.className = "cross-page-text";
            textCell.colSpan = Math.max(1, cols - 1);
            row.appendChild(chkCell);
            row.appendChild(textCell);
            table.tHead.insertBefore(row, headRow);
            box0.addEventListener("change", function () {
                var fn = row.__crossPageOnChange;
                if (typeof fn === "function") { fn(box0.checked); }
            });
        }
        var box = row.querySelector(".cross-page-check");
        var textCell = row.querySelector(".cross-page-text");
        var unit = String(opt.unit == null ? "" : opt.unit);
        var allSelected = !!opt.allSelected;
        var checked = num(opt.checked, 0);
        row.__crossPageOnChange = opt.onChange;
        row.style.display = "";
        box.checked = allSelected;
        box.disabled = total <= 0;
        box.title = "跨页全选：勾选后选中当前筛选条件下的全部 " + total + " " + unit
            + "（含未翻到的页）；取消勾选即取消跨页全选。"
            + "表头复选框只作用于当前页" + (opt.what ? "（" + opt.what + "）" : "") + "。";
        textCell.innerHTML = "<label for='" + esc(box.id) + "' title='" + esc(box.title) + "'>"
            + "跨页全选（共 " + total + " " + unit + "）"
            + (checked ? "<span class='cross-page-note'>已选 " + checked + "</span>" : "")
            + "</label>";
    }

    /**
     * 统计行末尾的“仅显示已选择数据 / 显示所有数据”操作组（跨页全选已挪到表头上方，见 crossPageBar）：
     * 只看已勾选的行，再点一次回到全部（按钮上带“已选 N / 共 M”）。
     * opt：{ total 可选总数, unit 单位（人/个/条）, what 对象说明, onlyChecked, checked }
     */
    function listActionsHtml(opt) {
        opt = opt || {};
        var total = num(opt.total, 0);
        var html = "<span class='list-row-actions'>";
        html += onlyCheckedBtnHtml(opt.onlyChecked, opt.checked, total, opt.what);
        return html + "</span>";
    }

    /**
     * 就地刷新统计行里的操作组（行内勾选/取消勾选后不必整表重绘，
     * 也能实时看到“已选 N / 共 M”）。只替换该节点，容器上的事件委托不受影响。
     * 跨页全选条走 crossPageBar（同样就地刷新）。
     */
    function refreshListActions(listEl, opt) {
        if (!listEl) { return; }
        var box = listEl.querySelector(".list-row-actions");
        if (!box) { return; }
        var holder = document.createElement("span");
        holder.innerHTML = listActionsHtml(opt);
        if (holder.firstChild) { listEl.replaceChild(holder.firstChild, box); }
    }

    /* ==================================================================
     * 三、表头拖拽列宽
     * ================================================================== */

    function tableWidthSum(cells, widths) {
        var sum = 0;
        for (var i = 0; i < widths.length; i++) {
            if (widths[i] > 0) { sum += widths[i]; }
        }
        return sum;
    }

    function applyTableMinWidth(table, cells, widths) {
        var sum = tableWidthSum(cells, widths);
        if (sum > 0) {
            table.style.minWidth = sum + "px";
        } else {
            table.style.minWidth = "";
        }
    }

    function bindDrag(table, cells, index, minWidth, key, widths, handle) {
        handle.addEventListener("mousedown", function (e) {
            if (e.button !== 0) { return; }
            e.preventDefault();
            e.stopPropagation();
            var th = cells[index];
            var startX = e.clientX;
            var startW = th.offsetWidth;
            document.body.classList.add("col-resizing");
            handle.classList.add("dragging");

            function onMove(ev) {
                var w = Math.max(minWidth, Math.round(startW + (ev.clientX - startX)));
                th.style.width = w + "px";
                th.style.minWidth = w + "px";
                widths[index] = w;
                applyTableMinWidth(table, cells, widths);
            }

            function onUp() {
                document.removeEventListener("mousemove", onMove, true);
                document.removeEventListener("mouseup", onUp, true);
                document.body.classList.remove("col-resizing");
                handle.classList.remove("dragging");
                colWidthStore[key] = widths.slice(0);
            }

            document.addEventListener("mousemove", onMove, true);
            document.addEventListener("mouseup", onUp, true);
        });
    }

    /**
     * 让 table.grid 表头支持拖拽调整列宽。
     *
     * opt = { key: 记忆列宽的标识, min: 最小列宽 }
     *
     * 可重复调用（已初始化的表直接返回）；表格隐藏时量不到宽度，会在首次可见的调用里初始化。
     * 列宽调整后写入 colWidthStore，翻页/列表重绘（只重绘 tbody）后保持不变。
     */
    function resizable(table, opt) {
        if (!table) { return; }
        opt = opt || {};
        if (table.getAttribute("data-col-resize") === "1") { return; }
        var head = table.tHead;
        if (!head || !head.rows.length) { return; }
        var cells = head.rows[head.rows.length - 1].cells;
        if (!cells || cells.length < 2) { return; }
        // 表格处于 display:none 时量不到列宽，等它可见后再初始化
        if (!table.offsetWidth) { return; }

        var key = String(opt.key || table.id || "oaones-table");
        var minWidth = num(opt.min, DEFAULT_MIN_COL_WIDTH);
        var widths = [];
        var stored = colWidthStore[key];
        var i;
        for (i = 0; i < cells.length; i++) {
            if (stored && stored.length === cells.length && num(stored[i], 0) > 0) {
                widths[i] = num(stored[i], 0);
                cells[i].style.width = widths[i] + "px";
                cells[i].style.minWidth = widths[i] + "px";
            } else {
                widths[i] = 0;
            }
        }
        table.setAttribute("data-col-resize", "1");
        table.classList.add("col-resizable");
        applyTableMinWidth(table, cells, widths);

        for (i = 0; i < cells.length; i++) {
            // 最后一列拖拽会把整表宽度顶出容器，且没有右侧边界，不提供把手
            if (i >= cells.length - 1) { continue; }
            // 复选框、序号等窄列保持固定宽度，不参与拖拽
            if (cells[i].offsetWidth < minWidth) { continue; }
            var handle = document.createElement("span");
            handle.className = "col-resizer";
            handle.title = "按住左右拖动，调整该列宽度";
            bindDrag(table, cells, i, minWidth, key, widths, handle);
            cells[i].appendChild(handle);
        }
    }

    window.OaonesTable = {
        PAGE_SIZES: PAGE_SIZES,
        DEFAULT_PAGE_SIZE: DEFAULT_PAGE_SIZE,
        pager: pager,
        syncCheckAll: syncCheckAll,
        checkAllTitle: checkAllTitle,
        crossPageBar: crossPageBar,
        onlyCheckedBtnHtml: onlyCheckedBtnHtml,
        listActionsHtml: listActionsHtml,
        refreshListActions: refreshListActions,
        resizable: resizable
    };
})(window, document);
