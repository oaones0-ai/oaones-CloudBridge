/**
 * OAones 选择弹框通用能力（oaones 下所有“点击弹出选择框”共用）。
 *
 * 提供的统一行为：
 *  1) 拖动：按住弹框顶部（.modal-head；登录页自己的弹层 .oa-modal-head 同样支持）
 *     即可在页面上拖动弹框，关闭按钮/输入框等不触发拖动；
 *  2) 显示模式：列表式 / 树状 切换（.view-switch[data-view-key] + .view-btn[data-view]）；
 *     候选有上下级关系（切换条上带“树状”按钮）的选择框**默认树状**，只有列表式的仍默认列表式；
 *  3) 树状渲染：默认收起分支，搜索时自动展开命中路径并高亮命中节点；
 *  4) 双击确定：页面只需在弹框内容区监听 dblclick 完成“确定并退出”，本文件负责拖动与树/列表骨架。
 *  5) 前置条件过滤：如“先选择分部，再选择部门”，仅显示已选分部（含下级）下的部门。
 *  6) 多选弹框的“全选 / 全不选”：候选列表支持多选时，在弹框顶部提供一键全选按钮
 *     （单选弹框点一行即完成绑定，不适用）。
 *
 * 页面接入方式：
 *   OaonesPicker.register(key, renderFn);  // key 与 data-view-key 一致，切换模式/展开收起时回调重绘
 *   OaonesPicker.mode(key);                // 取当前显示模式（"list" / "tree"）
 *   OaonesPicker.renderTree(key, rows, opt);// 渲染树状 html
 *   OaonesPicker.resetTree(key);           // 打开弹框时调用，使树状默认全部收起
 *   OaonesPicker.partialSelected(ids, childMapFn); // 多选树：下级有选中时的“部分选中”提示
 *   OaonesPicker.setPrecondition(key, fn); // 前置条件：fn() 返回 { tip, filter }，filter(row) 为真的行才出现
 *   OaonesPicker.applyPrecondition(key, rows); // 手动套用前置条件（列表模式用）
 *   OaonesPicker.preconditionTip(key, elId);   // 把前置条件说明写进说明节点
 *   OaonesPicker.setSelectAll(key, fn);    // 多选弹框：登记“全选”按钮处理（fn 返回操作后是否已全选）
 *   OaonesPicker.syncSelectAll(key, all);  // 列表重绘后同步“全选/全不选”按钮文案与选中态
 */
(function (global) {
    "use strict";

    /** key -> "list" | "tree" */
    var modes = {};
    /** key -> 重绘函数 */
    var renderers = {};
    /** key -> { nodeId: true(展开) } */
    var treeState = {};
    /** key -> 前置条件提供函数 */
    var preconditions = {};
    /** key -> “全选”按钮处理函数（多选弹框） */
    var selectAllHandlers = {};

    function esc(v) {
        if (v === null || v === undefined) { return ""; }
        return String(v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    /* ---------------------------------------------------------------
     * 显示模式：列表式 / 树状
     * --------------------------------------------------------------- */

    /**
     * 取当前显示模式（"list" / "tree"）。
     *
     * 未在本页显式切换过的选择框按下述规则取默认值：切换条上提供“树状”按钮（即候选有
     * 上下级关系，如 OA / 企微的分部、部门、人员）的选择框默认树状；只有“列表式”按钮或
     * 没有切换条的选择框（如企微租户——本身没有上下级关系）仍默认列表式。
     * 用户在本页点过切换按钮后，按选择框分别记忆所选的模式。
     */
    function mode(key) {
        if (modes[key] === "tree" || modes[key] === "list") { return modes[key]; }
        return supportsTree(key) ? "tree" : "list";
    }

    /** 该选择框的切换条上是否提供“树状”按钮（即候选有上下级关系）。 */
    function supportsTree(key) {
        if (!key || typeof document === "undefined") { return false; }
        var sw = document.querySelector(".view-switch[data-view-key='" + String(key).replace(/'/g, "\\'") + "']");
        var btn = sw ? sw.querySelector(".view-btn[data-view='tree']") : null;
        // 没有上下级关系的候选（如应用中心「角色」）会隐藏树状按钮：隐藏 / 禁用后按列表式处理
        return !!(btn && !btn.hidden && !btn.disabled && btn.style.display !== "none");
    }

    function register(key, renderFn) {
        if (!key) { return; }
        if (renderFn) { renderers[key] = renderFn; }
    }

    function rerender(key) {
        var fn = renderers[key];
        if (fn) { fn(); }
    }

    function setMode(key, m) {
        if (!key) { return; }
        modes[key] = (m === "tree") ? "tree" : "list";
        syncSwitches(key);
        rerender(key);
    }

    /** 同步所有显示模式切换按钮的选中态；onlyKey 为空时同步全部。 */
    function syncSwitches(onlyKey) {
        var list = document.querySelectorAll(".view-switch[data-view-key]");
        for (var i = 0; i < list.length; i++) {
            var key = list[i].getAttribute("data-view-key");
            if (onlyKey && key !== onlyKey) { continue; }
            var m = mode(key);
            var btns = list[i].querySelectorAll(".view-btn");
            for (var j = 0; j < btns.length; j++) {
                btns[j].classList.toggle("active", btns[j].getAttribute("data-view") === m);
            }
        }
    }

    /* ---------------------------------------------------------------
     * 树状模型与渲染（默认收起分支）
     * --------------------------------------------------------------- */

    function resetTree(key) {
        if (key) { treeState[key] = {}; return; }
        treeState = {};
    }

    /* ---------------------------------------------------------------
     * 前置条件过滤（如“先选择分部，再选择部门”）
     * --------------------------------------------------------------- */

    /**
     * 登记选择框的前置条件。fn() 返回 { tip, filter }：
     *   tip    前置条件说明（写入弹框说明节点，随条件变化更新）；
     *   filter 行过滤函数，返回 false 的行不出现在候选里（不提供则不过滤）。
     */
    function setPrecondition(key, fn) {
        if (!key) { return; }
        preconditions[key] = (typeof fn === "function") ? fn : null;
    }

    function preconditionState(key) {
        var fn = preconditions[key];
        if (typeof fn !== "function") { return { key: key, tip: "", filter: null }; }
        var r = fn() || {};
        return {
            key: key,
            tip: r.tip === null || r.tip === undefined ? "" : String(r.tip),
            filter: typeof r.filter === "function" ? r.filter : null
        };
    }

    /** 按前置条件过滤候选行；未登记前置条件时原样返回。 */
    function applyPrecondition(key, rows) {
        var pre = preconditionState(key);
        var list = rows || [];
        return pre.filter ? list.filter(function (row) { return pre.filter(row) !== false; }) : list;
    }

    /** 把前置条件说明写入页面上的说明节点（如弹框里的 .wx-pick-note）。 */
    function preconditionTip(key, elId) {
        var el = document.getElementById(elId);
        if (!el) { return ""; }
        el.textContent = preconditionState(key).tip;
        return el.textContent;
    }

    /* ---------------------------------------------------------------
     * 多选弹框：全选 / 全不选
     * --------------------------------------------------------------- */

    /**
     * 登记“全选”按钮处理（仅多选弹框；单选弹框点一行即完成绑定，不适用）。
     *
     * 页面在 .pick-view-bar 里放按钮：
     *   <button type="button" class="btn small secondary pick-all-btn"
     *           data-pick-all="key" id="xxxAllBtn" title="全选/全不选当前列表">全选</button>
     * 点击时调用 fn()，由页面按“当前弹框里可见且可选的整行”（已套用搜索关键字与前置条件过滤）
     * 一次性全选/全不选并重绘列表；fn() 返回操作后是否“已全部选中”，本文件据此同步按钮文案。
     */
    function setSelectAll(key, fn) {
        if (!key) { return; }
        selectAllHandlers[key] = (typeof fn === "function") ? fn : null;
    }

    /** 同步所有“全选”按钮的文案与选中态；allSelected 为当前列表是否已全部选中。 */
    function syncSelectAll(key, allSelected) {
        if (!key) { return; }
        var btns = document.querySelectorAll("[data-pick-all='" + key + "']");
        for (var i = 0; i < btns.length; i++) {
            btns[i].textContent = allSelected ? "全不选" : "全选";
            btns[i].classList.toggle("active", allSelected === true);
            btns[i].setAttribute("aria-pressed", allSelected === true ? "true" : "false");
        }
    }

    function expandedOf(key) {
        if (!treeState[key]) { treeState[key] = {}; }
        return treeState[key];
    }

    function toggleNode(key, nodeId) {
        if (!key || !nodeId) { return; }
        var exp = expandedOf(key);
        exp[nodeId] = exp[nodeId] !== true;
        rerender(key);
    }

    /**
     * 按 id / parent 把平铺行组装成树；父节点不在当前数据集内时按根节点处理。
     * opt = { idOf:fn(row), parentOf:fn(row) }
     */
    function buildTree(rows, opt) {
        opt = opt || {};
        var idOf = opt.idOf || function (r) { return r.id; };
        var parentOf = opt.parentOf || function (r) { return r.parent; };
        var model = { byId: {}, children: {}, parent: {}, depth: {}, roots: [], order: [] };
        (rows || []).forEach(function (r) {
            var id = String(idOf(r));
            if (model.byId[id]) { return; }
            model.byId[id] = r;
            model.children[id] = [];
            model.order.push(id);
        });
        model.order.forEach(function (id) {
            var pv = parentOf(model.byId[id]);
            var pid = String(pv == null ? "" : pv);
            if (!pid || pid === "0" || pid === id || !model.byId[pid]) {
                model.parent[id] = "";
                model.roots.push(id);
            } else {
                model.parent[id] = pid;
                model.children[pid].push(id);
            }
        });
        (function walk(ids, d) {
            ids.forEach(function (id) {
                model.depth[id] = d;
                walk(model.children[id], d + 1);
            });
        })(model.roots, 0);
        return model;
    }

    /**
     * 渲染树状列表。
     * opt = {
     *   idOf:fn(row) 默认 row.id；parentOf:fn(row) 默认 row.parent；
     *   textOf:fn(row) 搜索匹配文本（分组节点不参与匹配）；
     *   isGroup:fn(row) 是否分组节点；groupHtml:fn(row, open, isHit) 分组节点html；
     *   optionHtml:fn(row, isHit) 可选行html；keyword:string；emptyText:string
     * }
     */
    function renderTree(key, rows, opt) {
        opt = opt || {};
        rows = applyPrecondition(key, rows);
        var model = buildTree(rows, opt);
        var textOf = opt.textOf || function (r) { return r.name; };
        var isGroup = opt.isGroup || function () { return false; };
        var keyword = String(opt.keyword == null ? "" : opt.keyword).trim().toLowerCase();
        var visible = null;
        var hit = {};
        if (keyword) {
            visible = {};
            model.order.forEach(function (id) {
                var row = model.byId[id];
                if (isGroup(row)) { return; }
                var text = String(textOf(row) == null ? "" : textOf(row));
                if (text.toLowerCase().indexOf(keyword) < 0) { return; }
                hit[id] = true;
                // 命中节点自身及全部上级都保留，搜索时自动展开到命中位置
                var cur = id;
                while (cur && !visible[cur]) {
                    visible[cur] = true;
                    cur = model.parent[cur];
                }
            });
            if (!model.order.some(function (id) { return hit[id]; })) {
                return "<div class='wx-pick-empty'>" + esc(opt.emptyText || "没有匹配的数据") + "</div>";
            }
        }
        var exp = expandedOf(key);
        var html = "";
        (function walk(ids) {
            ids.forEach(function (id) {
                if (visible && !visible[id]) { return; }
                var row = model.byId[id];
                var kids = model.children[id] || [];
                // 默认收起分支：仅显式展开过的节点才展开；搜索时自动展开命中路径
                var open = keyword ? true : exp[id] === true;
                var pad = 4 + model.depth[id] * 16;
                html += "<div class='tree-row" + (hit[id] ? " tree-hit" : "") + "' style='padding-left:" + pad + "px;'>";
                if (kids.length) {
                    html += "<button type='button' class='tree-caret' data-tree='" + esc(key) + "'"
                        + " data-node='" + esc(id) + "' title='" + (open ? "收起下级" : "展开下级") + "'>"
                        + (open ? "▾" : "▸") + "</button>";
                } else {
                    html += "<span class='tree-caret leaf'>•</span>";
                }
                if (isGroup(row) && opt.groupHtml) {
                    html += opt.groupHtml(row, open, !!hit[id]);
                } else {
                    html += opt.optionHtml(row, !!hit[id]);
                }
                html += "</div>";
                if (kids.length && open) { walk(kids); }
            });
        })(model.roots);
        return html || "<div class='wx-pick-empty'>" + esc(opt.emptyText || "没有匹配的数据") + "</div>";
    }

    /**
     * 树状分组节点（部门/分部等）的通用 html：
     * data-tree / data-node 供点击展开收起，样式与可选行区分，不参与“选择”。
     */
    function groupHtml(key, row, open, isHit) {
        var count = row.count === undefined || row.count === null ? "" : row.count;
        return "<button type='button' class='wx-pick-group" + (isHit ? " group-hit" : "") + "'"
            + " data-tree='" + esc(key) + "' data-node='" + esc(row.id) + "'"
            + " title='" + esc(row.title || row.fullname || row.name || "") + "'>"
            + "<span class='wx-pick-group-name'>" + esc(row.name || "") + "</span>"
            + (count === "" ? "" : "<span class='wx-pick-group-count'>" + esc(count) + "</span>")
            + "</button>";
    }

    /** 多选树：某节点未被选中、但其下级有被选中时给出“部分选中”提示。 */
    function partialSelected(ids, childMapFn) {
        var map = childMapFn ? childMapFn() : {};
        var sel = {};
        (ids || []).forEach(function (id) { sel[String(id)] = true; });
        var memo = {};
        function hasSelBelow(id) {
            if (memo[id] !== undefined) { return memo[id]; }
            memo[id] = false;
            var kids = map[id] || [];
            for (var i = 0; i < kids.length; i++) {
                var c = String(kids[i]);
                if (sel[c] || hasSelBelow(c)) { memo[id] = true; break; }
            }
            return memo[id];
        }
        var out = {};
        Object.keys(map).forEach(function (id) {
            if (!sel[id] && hasSelBelow(id)) { out[id] = true; }
        });
        return out;
    }

    /* ---------------------------------------------------------------
     * 弹框拖动：按住顶部拖动条在页面上移动弹框
     * --------------------------------------------------------------- */

    var DRAG_IGNORE = "button, a, input, select, textarea, label, .no-drag";
    /* 可拖动的弹框：.modal 是模块通用弹框，.oa-modal 是登录页自己的弹层（css/login.css），
       “所有弹出框都能按表头拖动”这条约束对两者都生效，这里一并接管。 */
    var DRAG_HEAD = ".modal-head, .oa-modal-head";
    var DRAG_BOX = ".modal, .oa-modal";

    function startDrag(modal, e) {
        var rect = modal.getBoundingClientRect();
        // 由“居中定位(transform)”切换为绝对定位，拖动过程中跟随鼠标
        modal.style.transform = "none";
        modal.style.margin = "0";
        // 靠遮罩 flex 居中的弹框（登录页 .oa-modal）是静态定位，left/top 不生效，这里补成固定定位
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
            var maxLeft = Math.max(0, window.innerWidth - 80);
            var maxTop = Math.max(0, window.innerHeight - 40);
            var nl = Math.max(-(modal.offsetWidth - 80), Math.min(ev.clientX - offX, maxLeft));
            var nt = Math.max(0, Math.min(ev.clientY - offY, maxTop));
            modal.style.left = nl + "px";
            modal.style.top = nt + "px";
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

    function bindModalDrag() {
        document.addEventListener("mousedown", function (e) {
            if (e.button !== 0) { return; }
            var target = e.target;
            if (!target || !target.closest) { return; }
            var head = target.closest(DRAG_HEAD);
            if (!head) { return; }
            if (target.closest(DRAG_IGNORE)) { return; }
            var modal = head.closest(DRAG_BOX);
            if (!modal) { return; }
            // 全屏弹框（推送日志详情）铺满整页，不需要也不能拖动
            if (modal.classList.contains("modal-fullscreen")) { return; }
            startDrag(modal, e);
        });
        // 窗口尺寸变化时，把已拖到视口外的弹框收回可见范围
        window.addEventListener("resize", function () {
            var modals = document.querySelectorAll(DRAG_BOX);
            for (var i = 0; i < modals.length; i++) {
                var m = modals[i];
                if (!m.style.left || m.style.display === "none") { continue; }
                var top = parseFloat(m.style.top);
                var left = parseFloat(m.style.left);
                if (isNaN(top) || isNaN(left)) { continue; }
                var maxTop = Math.max(0, window.innerHeight - 40);
                var maxLeft = Math.max(0, window.innerWidth - 80);
                m.style.top = Math.max(0, Math.min(top, maxTop)) + "px";
                m.style.left = Math.max(-(m.offsetWidth - 80), Math.min(left, maxLeft)) + "px";
            }
        });
    }

    /* ---------------------------------------------------------------
     * 事件委托：显示模式切换 + 树节点展开收起 + 分组节点点击
     * --------------------------------------------------------------- */

    function bindDelegates() {
        document.addEventListener("click", function (e) {
            var target = e.target;
            if (!target || !target.closest) { return; }
            var viewBtn = target.closest(".view-switch[data-view-key] .view-btn");
            if (viewBtn) {
                var sw = viewBtn.closest(".view-switch");
                setMode(sw.getAttribute("data-view-key"), viewBtn.getAttribute("data-view"));
                return;
            }
            var allBtn = target.closest("[data-pick-all]");
            if (allBtn) {
                var allKey = allBtn.getAttribute("data-pick-all");
                var handler = selectAllHandlers[allKey];
                var allOn = handler ? handler() === true : false;
                syncSelectAll(allKey, allOn);
                return;
            }
            var caret = target.closest(".tree-caret");
            // 占位箭头（.tree-caret.leaf，没有下级）不是展开箭头：点它不做展开收起（也不吞掉点击）
            if (caret && !caret.classList.contains("leaf")) {
                toggleNode(caret.getAttribute("data-tree"), caret.getAttribute("data-node"));
                return;
            }
            var group = target.closest(".wx-pick-group");
            if (group) {
                toggleNode(group.getAttribute("data-tree"), group.getAttribute("data-node"));
                return;
            }
        });
    }

    function ready(fn) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", fn);
        } else {
            fn();
        }
    }

    global.OaonesPicker = {
        esc: esc,
        mode: mode,
        register: register,
        setMode: setMode,
        syncSwitches: syncSwitches,
        resetTree: resetTree,
        setPrecondition: setPrecondition,
        preconditionState: preconditionState,
        applyPrecondition: applyPrecondition,
        preconditionTip: preconditionTip,
        setSelectAll: setSelectAll,
        syncSelectAll: syncSelectAll,
        toggleNode: toggleNode,
        buildTree: buildTree,
        renderTree: renderTree,
        groupHtml: groupHtml,
        partialSelected: partialSelected
    };

    bindModalDrag();
    bindDelegates();
    ready(function () { syncSwitches(); });
})(window);
