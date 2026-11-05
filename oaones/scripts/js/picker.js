/**
 * OAones 通用弹出选择器（OaonesPicker）。
 *
 * 本组件是选择弹框的完整实现，负责：
 *   - 列表式 / 树状展示与切换；
 *   - 单选 / 多选、最大数量、可选与禁用状态；
 *   - 搜索、业务过滤、选中高亮、部分选中；
 *   - 草稿选择、确定、取消、清空；
 *   - 双击选择并确认；
 *   - 全选当前结果；
 *   - 多选“仅显示已选择”；
 *   - 多选左右分栏、分隔线拖拽和右侧已选移除；
 *   - 列表分页（存在 OaonesTable 时）；
 *   - 加载、空状态、失败重试、请求竞态保护和焦点恢复。
 *
 * 依赖：
 *   modal.js -> tree.js -> picker.js
 * 列表分页可选依赖：
 *   table.js
 *
 * 用法：
 *   OaonesPicker.open({
 *       key: "user-picker",
 *       title: "选择人员",
 *       multiple: true,
 *       rows: rows,
 *       onConfirm: function (selection) {
 *           // selection.ids、selection.items
 *       }
 *   });
 */
(function (global, document) {
    "use strict";

    var sequence = 0;
    var splitWidths = {};

    function esc(value) {
        if (value === null || value === undefined) { return ""; }
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function text(value, fallback) {
        if (value === null || value === undefined || value === "") {
            return fallback === undefined ? "" : fallback;
        }
        return String(value);
    }

    function trim(value) {
        return text(value).replace(/^\s+|\s+$/g, "");
    }

    function asArray(value) {
        if (value === null || value === undefined) { return []; }
        return Array.isArray(value) ? value.slice(0) : [value];
    }

    function copyObject(source) {
        var target = {};
        Object.keys(source || {}).forEach(function (key) { target[key] = source[key]; });
        return target;
    }

    function defaultId(row) {
        return row.id !== undefined ? row.id
            : row.nodeId !== undefined ? row.nodeId
                : row.value !== undefined ? row.value : row.key;
    }

    function defaultParent(row) {
        return row.parentId !== undefined ? row.parentId
            : row.parent !== undefined ? row.parent
                : row.pid !== undefined ? row.pid : row.parentNode;
    }

    function defaultText(row) {
        return row.name !== undefined ? row.name
            : row.title !== undefined ? row.title : row.label;
    }

    function normalizeRows(rows, options) {
        options = options || {};
        var idOf = options.idOf || defaultId;
        var parentOf = options.parentOf || defaultParent;
        var textOf = options.textOf || defaultText;
        var detailOf = options.detailOf || function (row) {
            return row.detail !== undefined ? row.detail
                : row.code !== undefined ? row.code : row.description;
        };
        var badgeOf = options.badgeOf || function (row) {
            return row.badge !== undefined ? row.badge : row.tag;
        };
        var selectableOf = options.selectableOf || function (row) {
            return row.selectable !== false && row.disabled !== true;
        };
        var reasonOf = options.reasonOf || function (row) {
            return row.disabledReason || row.reason || "";
        };
        var countOf = options.countOf || function (row) {
            return row.count !== undefined ? row.count : row.childCount;
        };
        var pathOf = options.pathOf;
        var seen = {};
        var normalized = [];

        asArray(rows).forEach(function (raw) {
            var id = text(idOf(raw || {}));
            if (!id || seen[id]) { return; }
            seen[id] = true;
            var parentId = text(parentOf(raw || {}));
            if (parentId === "0" || parentId === "null" || parentId === "undefined") {
                parentId = "";
            }
            var selectable = selectableOf(raw) !== false;
            normalized.push({
                id: id,
                parentId: parentId,
                name: text(textOf(raw || {}), "选项 " + id),
                detail: text(detailOf(raw || {})),
                badge: text(badgeOf(raw || {})),
                selectable: selectable,
                reason: text(reasonOf(raw || {})),
                count: countOf(raw || {}),
                explicitPath: typeof pathOf === "function" ? text(pathOf(raw)) : "",
                path: "",
                depth: 0,
                raw: raw
            });
        });
        return normalized;
    }

    function buildModel(rows) {
        var list = rows || [];
        var model = {
            rows: list,
            map: {},
            children: {},
            roots: [],
            order: [],
            hasHierarchy: false,
            circular: {}
        };
        list.forEach(function (row) {
            model.map[row.id] = row;
            model.children[row.id] = [];
            model.order.push(row.id);
        });

        // 先断开循环和自引用，确保后续树路径遍历不会无限递归。
        model.order.forEach(function (id) {
            var guard = {};
            var current = id;
            while (current) {
                if (guard[current]) {
                    model.circular[id] = true;
                    model.map[id].parentId = "";
                    break;
                }
                guard[current] = true;
                current = model.map[current] ? model.map[current].parentId : "";
            }
        });
        model.order.forEach(function (id) {
            var row = model.map[id];
            if (row.parentId && model.map[row.parentId] && row.parentId !== id) {
                model.children[row.parentId].push(id);
                model.hasHierarchy = true;
            } else {
                row.parentId = "";
                model.roots.push(id);
            }
        });

        if (global.OaonesTree && typeof global.OaonesTree.build === "function") {
            var shared = global.OaonesTree.build(list, {
                idOf: function (row) { return row.id; },
                parentOf: function (row) { return row.parentId; }
            });
            model.roots = shared.roots.slice(0);
            model.children = shared.children;
            model.hasHierarchy = model.order.some(function (id) {
                return !!(model.map[id] && model.map[id].parentId);
            });
        }

        function calculatePath(id) {
            var parts = [];
            var guard = {};
            var current = id;
            while (current && model.map[current] && !guard[current]) {
                guard[current] = true;
                parts.unshift(model.map[current].name);
                current = model.map[current].parentId;
            }
            return parts.join(" / ");
        }

        function walk(ids, depth) {
            ids.forEach(function (id) {
                var row = model.map[id];
                if (!row) { return; }
                row.depth = depth;
                row.path = row.explicitPath || calculatePath(id);
                walk(model.children[id] || [], depth + 1);
            });
        }
        walk(model.roots, 0);
        return model;
    }

    function descendants(model, id) {
        var out = [];
        var guard = {};
        function walk(current) {
            if (!current || guard[current]) { return; }
            guard[current] = true;
            out.push(current);
            (model.children[current] || []).forEach(walk);
        }
        walk(text(id));
        return out;
    }

    function matchesRow(row, keyword) {
        var word = trim(keyword).toLowerCase();
        if (!word) { return true; }
        return [row.id, row.name, row.detail, row.badge, row.path]
            .join(" ").toLowerCase().indexOf(word) >= 0;
    }

    function createSelection(options) {
        options = options || {};
        var idOf = options.idOf || defaultId;
        var textOf = options.textOf || defaultText;
        var multiple = options.multiple === true;
        var limit = parseInt(options.maxSelected, 10);
        if (isNaN(limit) || limit < 0) { limit = 0; }
        var order = [];
        var map = {};

        function itemId(item) {
            if (typeof item === "string" || typeof item === "number") { return text(item); }
            return text(item && item.id !== undefined ? item.id : idOf(item || {}));
        }

        function normalize(item) {
            var id = itemId(item);
            if (!id) { return null; }
            var raw = item && item.raw ? item.raw : item;
            return {
                id: id,
                name: text(item && item.name !== undefined ? item.name : textOf(raw || {}),
                    "选项 " + id),
                detail: text(item && item.detail),
                path: text(item && item.path),
                selectable: !item || item.selectable !== false,
                reason: text(item && item.reason),
                raw: raw || null
            };
        }

        function add(item) {
            var data = normalize(item);
            if (!data) { return { ok: false, reason: "invalid" }; }
            if (!data.selectable) { return { ok: false, reason: "disabled" }; }
            if (map[data.id]) { return { ok: true, added: false, item: map[data.id] }; }
            if (!multiple && order.length) { return { ok: false, reason: "single" }; }
            if (limit > 0 && order.length >= limit) {
                return { ok: false, reason: "max", max: limit };
            }
            order.push(data.id);
            map[data.id] = data;
            return { ok: true, added: true, item: data };
        }

        function set(items) {
            order = [];
            map = {};
            asArray(items).forEach(function (item) {
                var data = normalize(item);
                if (!data || !data.selectable) { return; }
                if (!multiple && order.length) { return; }
                if (limit > 0 && order.length >= limit) { return; }
                order.push(data.id);
                map[data.id] = data;
            });
        }

        function remove(id) {
            var key = text(id);
            if (!map[key]) { return false; }
            delete map[key];
            order = order.filter(function (one) { return one !== key; });
            return true;
        }

        function items() {
            return order.map(function (id) { return map[id]; }).filter(Boolean);
        }

        function snapshot() {
            var value = items().map(function (item) {
                return {
                    id: item.id,
                    name: item.name,
                    detail: item.detail,
                    path: item.path,
                    raw: item.raw
                };
            });
            return {
                ids: value.map(function (item) { return item.id; }),
                items: value
            };
        }

        return {
            multiple: multiple,
            maxSelected: limit,
            size: function () { return order.length; },
            has: function (id) { return !!map[text(id)]; },
            get: function (id) { return map[text(id)] || null; },
            values: function () { return order.slice(0); },
            items: items,
            snapshot: snapshot,
            set: set,
            add: add,
            remove: remove,
            clear: function () { order = []; map = {}; }
        };
    }

    function callLoader(loader, args, done) {
        if (typeof loader !== "function") {
            done(null, []);
            return;
        }
        var finished = false;
        function finish(error, data) {
            if (finished) { return; }
            finished = true;
            done(error, data);
        }
        var callArgs = args.slice(0);
        callArgs.push(finish);
        try {
            var result = loader.apply(null, callArgs);
            if (result && typeof result.then === "function") {
                result.then(function (data) { finish(null, data); }, function (error) {
                    finish(error || new Error("加载失败"));
                });
            } else if (result !== undefined) {
                finish(null, result);
            }
        } catch (error) {
            finish(error || new Error("加载失败"));
        }
    }

    function notify(options, message, mode) {
        if (typeof options.onMessage === "function") {
            options.onMessage(message, mode || "info");
            return;
        }
        var name = mode === "error" ? "error" : mode === "warn" ? "warn" : "info";
        if (global.OaonesToast && typeof global.OaonesToast[name] === "function") {
            global.OaonesToast[name](message);
        }
    }

    function labels(options) {
        var source = options.labels || {};
        return {
            title: text(options.title, text(source.title, "请选择")),
            search: text(source.search, "搜索名称或 ID"),
            list: text(source.list, "列表式"),
            tree: text(source.tree, "树状"),
            selectedOnly: text(source.selectedOnly, "仅显示已选择"),
            split: text(source.split, "左右显示"),
            selectedTitle: text(source.selectedTitle, "已选择"),
            clear: text(source.clear, "清空已选"),
            cancel: text(source.cancel, "取消"),
            confirm: text(options.confirmText, text(source.confirm, "确定")),
            selectAll: text(source.selectAll, "全选"),
            unselectAll: text(source.unselectAll, "全不选"),
            loading: text(source.loading, "正在加载…"),
            failed: text(source.failed, "加载失败"),
            retry: text(source.retry, "重新加载"),
            empty: text(source.empty, "没有可选择的项目"),
            emptySearch: text(source.emptySearch, "没有匹配的项目"),
            emptySelected: text(source.emptySelected, "尚未选择项目"),
            remove: text(source.remove, "移除"),
            close: text(source.close, "关闭"),
            max: function (max) { return "最多选择 " + max + " 项，请先移除已选项目"; }
        };
    }

    function create(options) {
        options = copyObject(options || {});
        var key = text(options.key, "oa-picker-" + (++sequence));
        var multiple = options.multiple === true;
        options.multiple = multiple;
        var maxSelected = parseInt(options.maxSelected, 10);
        if (isNaN(maxSelected) || maxSelected < 0) { maxSelected = 0; }
        options.maxSelected = maxSelected;
        var pageSize = parseInt(options.pageSize, 10);
        if (!(pageSize > 0)) { pageSize = 20; }
        var L = labels(options);
        var selection = createSelection(options);
        var state = {
            rows: [],
            model: buildModel([]),
            expanded: {},
            keyword: "",
            mode: "list",
            selectedOnly: false,
            split: false,
            loading: false,
            error: "",
            message: "",
            pageSize: pageSize,
            destroyed: false,
            returnFocus: null
        };
        var refs = {};
        var mask = null;
        var modal = null;
        var pager = null;
        var requestSeq = 0;
        var searchTimer = 0;
        var splitDragStop = null;

        function context() {
            return {
                key: key,
                keyword: state.keyword,
                selectedIds: selection.values(),
                expanded: copyObject(state.expanded),
                multiple: multiple,
                maxSelected: maxSelected,
                mode: state.mode
            };
        }

        function emitChange() {
            if (typeof options.onSelectionChange === "function") {
                options.onSelectionChange(selection.snapshot(), context());
            }
        }

        function setMessage(message, mode) {
            state.message = text(message);
            if (!refs.message) { return; }
            refs.message.className = "oa-picker-message" + (mode ? " is-" + mode : "");
            refs.message.textContent = state.message;
            refs.message.hidden = !state.message;
        }

        function shellHtml() {
            return ""
                + "<div class='oa-picker-toolbar'>"
                + "<input type='search' class='oa-picker-search' data-oa-picker-search"
                + " placeholder='" + esc(L.search) + "' aria-label='" + esc(L.search) + "'>"
                + "<div class='view-switch mini oa-picker-view' role='group' aria-label='显示方式'>"
                + "<button type='button' class='view-btn' data-oa-picker-mode='list' aria-pressed='false'>"
                + esc(L.list) + "</button>"
                + "<button type='button' class='view-btn' data-oa-picker-mode='tree' aria-pressed='false'>"
                + esc(L.tree) + "</button>"
                + "</div>"
                + "<div class='view-switch mini oa-picker-display oa-picker-multi-only'"
                + " role='group' aria-label='多选显示模式'>"
                + "<button type='button' class='view-btn' data-oa-picker-action='selected-only'"
                + " aria-pressed='false'>" + esc(L.selectedOnly) + "</button>"
                + "<button type='button' class='view-btn' data-oa-picker-action='split'"
                + " aria-pressed='false'>" + esc(L.split) + "</button>"
                + "</div>"
                + "<button type='button' class='btn small secondary oa-picker-multi-only'"
                + " data-oa-picker-action='select-all'>" + esc(L.selectAll) + "</button>"
                + "</div>"
                + "<div class='oa-picker-message' data-oa-picker-message role='status' aria-live='polite'></div>"
                + "<div class='oa-picker-layout'>"
                + "<section class='oa-picker-candidate' aria-label='候选项'>"
                + "<div class='oa-picker-content' data-oa-picker-candidate role='tree'></div>"
                + "<div class='pager oa-picker-pager' data-oa-picker-pager></div>"
                + "</section>"
                + "<div class='oa-picker-splitter oa-picker-multi-only' data-oa-picker-splitter"
                + " role='separator' aria-orientation='vertical' tabindex='0'"
                + " title='拖动调整左右宽度，双击恢复默认宽度'"
                + " aria-label='调整左右分栏宽度' hidden></div>"
                + "<section class='oa-picker-selected-panel oa-picker-multi-only' aria-label='"
                + esc(L.selectedTitle) + "'>"
                + "<div class='oa-picker-selected-head'><strong>" + esc(L.selectedTitle)
                + "</strong><span data-oa-picker-selected-count></span></div>"
                + "<div class='oa-picker-content' data-oa-picker-selected role='tree'></div>"
                + "</section>"
                + "</div>"
                + "<div class='oa-picker-foot'>"
                + "<span class='oa-picker-count' data-oa-picker-count></span>"
                + "<div class='oa-picker-actions'>"
                + "<button type='button' class='btn secondary' data-oa-picker-action='clear'>"
                + esc(L.clear) + "</button>"
                + "<button type='button' class='btn secondary' data-oa-picker-action='cancel'>"
                + esc(L.cancel) + "</button>"
                + "<button type='button' class='btn' data-oa-picker-action='confirm'>"
                + esc(L.confirm) + "</button>"
                + "</div></div>";
        }

        function mount() {
            if (!document || !document.body || modal) { return; }
            mask = document.createElement("div");
            mask.className = "modal-mask";
            mask.id = "oaPickerMask-" + key.replace(/[^A-Za-z0-9_-]/g, "-");
            modal = document.createElement("div");
            modal.className = "modal oa-picker-modal";
            modal.setAttribute("role", "dialog");
            modal.setAttribute("aria-modal", "true");
            modal.setAttribute("aria-label", L.title);
            modal.setAttribute("data-modal-mask", mask.id);
            modal.innerHTML = "<div class='modal-head'><span>" + esc(L.title) + "</span>"
                + "<button type='button' class='modal-close' data-oa-picker-action='cancel'"
                + " title='" + esc(L.close) + "' aria-label='" + esc(L.close) + "'>×</button></div>"
                + "<div class='modal-body'>" + shellHtml() + "</div>";
            document.body.appendChild(mask);
            document.body.appendChild(modal);
            refs.search = modal.querySelector("[data-oa-picker-search]");
            refs.candidate = modal.querySelector("[data-oa-picker-candidate]");
            refs.selected = modal.querySelector("[data-oa-picker-selected]");
            refs.selectedPanel = modal.querySelector(".oa-picker-selected-panel");
            refs.layout = modal.querySelector(".oa-picker-layout");
            refs.splitter = modal.querySelector("[data-oa-picker-splitter]");
            refs.pager = modal.querySelector("[data-oa-picker-pager]");
            refs.count = modal.querySelector("[data-oa-picker-count]");
            refs.selectedCount = modal.querySelector("[data-oa-picker-selected-count]");
            refs.message = modal.querySelector("[data-oa-picker-message]");
            refs.confirm = modal.querySelector("[data-oa-picker-action='confirm']");
            bind();
            mountPager();
        }

        function bind() {
            refs.search.addEventListener("input", function () {
                var value = this.value || "";
                if (searchTimer) { global.clearTimeout(searchTimer); }
                searchTimer = global.setTimeout(function () {
                    searchTimer = 0;
                    state.keyword = value;
                    if (options.remoteSearch && typeof options.loadRows === "function") {
                        loadRows();
                    } else {
                        render();
                    }
                }, options.searchDelay === undefined ? 180 : options.searchDelay);
            });
            refs.candidate.addEventListener("click", onContentClick);
            refs.selected.addEventListener("click", onContentClick);
            refs.candidate.addEventListener("dblclick", onDoubleClick);
            refs.selected.addEventListener("dblclick", onDoubleClick);
            modal.addEventListener("click", function (event) {
                var target = event.target && event.target.closest
                    ? event.target.closest("[data-oa-picker-action]") : null;
                if (target) { handleAction(target.getAttribute("data-oa-picker-action")); return; }
                var modeButton = event.target && event.target.closest
                    ? event.target.closest("[data-oa-picker-mode]") : null;
                if (modeButton) { setMode(modeButton.getAttribute("data-oa-picker-mode")); }
            });
            modal.addEventListener("keydown", function (event) {
                if (event.key === "Escape" || event.keyCode === 27) {
                    event.preventDefault();
                    event.stopPropagation();
                    close(false);
                }
            });
            mask.addEventListener("click", function () { close(false); });
            refs.splitter.addEventListener("mousedown", startSplitDrag);
            refs.splitter.addEventListener("keydown", onSplitterKeydown);
            refs.splitter.addEventListener("dblclick", function () {
                applySplitWidth(defaultSplitWidth(), true);
            });
            global.addEventListener("resize", keepSplitWidthInRange);
        }

        function splitBounds() {
            var total = refs.layout && refs.layout.getBoundingClientRect
                ? refs.layout.getBoundingClientRect().width : 0;
            if (!(total > 0) && modal) { total = modal.clientWidth || 780; }
            var handle = refs.splitter && refs.splitter.offsetWidth
                ? refs.splitter.offsetWidth : 9;
            var minRight = 180;
            var minLeft = Math.min(260, Math.max(150, total * 0.42));
            var maxRight = Math.min(520, Math.max(minRight, total - handle - minLeft));
            return { min: minRight, max: maxRight, total: total };
        }

        function defaultSplitWidth() {
            var range = splitBounds();
            return Math.max(range.min, Math.min(370, range.total * 0.38));
        }

        function applySplitWidth(width, remember) {
            if (!refs.selectedPanel || !refs.splitter) { return 0; }
            var range = splitBounds();
            var next = Math.round(Math.max(range.min, Math.min(range.max, Number(width) || 0)));
            refs.selectedPanel.style.flexBasis = next + "px";
            refs.selectedPanel.style.maxWidth = "none";
            refs.splitter.setAttribute("aria-valuemin", String(range.min));
            refs.splitter.setAttribute("aria-valuemax", String(Math.round(range.max)));
            refs.splitter.setAttribute("aria-valuenow", String(next));
            refs.splitter.setAttribute("aria-valuetext", "右侧已选区域宽 " + next + " 像素");
            if (remember) { splitWidths[key] = next; }
            return next;
        }

        function keepSplitWidthInRange() {
            if (!refs.splitter || refs.splitter.hidden) { return; }
            applySplitWidth(splitWidths[key] || defaultSplitWidth(), false);
        }

        function startSplitDrag(event) {
            if (!event || (event.button !== undefined && event.button !== 0)) { return; }
            if (splitDragStop) { splitDragStop(); }
            var startX = event.clientX;
            var startWidth = refs.selectedPanel.getBoundingClientRect().width
                || parseFloat(refs.selectedPanel.style.flexBasis) || defaultSplitWidth();
            refs.splitter.classList.add("is-dragging");
            if (document.body) { document.body.classList.add("oa-picker-split-resizing"); }

            function move(moveEvent) {
                if (moveEvent.preventDefault) { moveEvent.preventDefault(); }
                applySplitWidth(startWidth + startX - moveEvent.clientX, true);
            }

            function stop() {
                document.removeEventListener("mousemove", move);
                document.removeEventListener("mouseup", stop);
                if (refs.splitter) { refs.splitter.classList.remove("is-dragging"); }
                if (document.body) { document.body.classList.remove("oa-picker-split-resizing"); }
                if (splitDragStop === stop) { splitDragStop = null; }
            }

            splitDragStop = stop;
            document.addEventListener("mousemove", move);
            document.addEventListener("mouseup", stop);
            if (event.preventDefault) { event.preventDefault(); }
        }

        function onSplitterKeydown(event) {
            var range = splitBounds();
            var current = parseFloat(refs.splitter.getAttribute("aria-valuenow")) || defaultSplitWidth();
            var next = current;
            if (event.key === "ArrowLeft") { next = current + 16; }
            else if (event.key === "ArrowRight") { next = current - 16; }
            else if (event.key === "Home") { next = range.min; }
            else if (event.key === "End") { next = range.max; }
            else { return; }
            if (event.preventDefault) { event.preventDefault(); }
            applySplitWidth(next, true);
        }

        function mountPager() {
            if (pager || !refs.pager || !global.OaonesTable
                    || typeof global.OaonesTable.pager !== "function") {
                return;
            }
            pager = global.OaonesTable.pager({
                key: key + "-pages",
                el: refs.pager,
                size: state.pageSize,
                onChange: render
            });
        }

        function handleAction(action) {
            if (action === "cancel") { close(false); }
            else if (action === "confirm") { confirm(); }
            else if (action === "clear") {
                selection.clear();
                emitChange();
                render();
            } else if (action === "selected-only") {
                state.selectedOnly = !state.selectedOnly;
                if (state.selectedOnly) { state.split = false; }
                render();
            } else if (action === "split") {
                state.split = !state.split;
                if (state.split) { state.selectedOnly = false; }
                render();
            } else if (action === "select-all") {
                toggleAllVisible();
            } else if (action === "retry") {
                loadRows();
            }
        }

        function setMode(modeValue) {
            state.mode = modeValue === "tree" && state.model.hasHierarchy ? "tree" : "list";
            render();
        }

        function onContentClick(event) {
            var target = event.target;
            if (!target || !target.closest) { return; }
            var caret = target.closest("[data-oa-picker-expand]");
            if (caret) {
                var id = caret.getAttribute("data-oa-picker-expand");
                state.expanded[id] = state.expanded[id] !== true;
                render();
                return;
            }
            var remove = target.closest("[data-oa-picker-remove]");
            if (remove) {
                selection.remove(remove.getAttribute("data-oa-picker-remove"));
                emitChange();
                render();
                return;
            }
            var option = target.closest("[data-oa-picker-id]");
            if (option && !option.disabled) {
                toggleItem(option.getAttribute("data-oa-picker-id"));
            }
        }

        function onDoubleClick(event) {
            var option = event.target && event.target.closest
                ? event.target.closest("[data-oa-picker-id]") : null;
            if (!option || option.disabled) { return; }
            event.preventDefault();
            if (selectItem(option.getAttribute("data-oa-picker-id"), true)) {
                confirm();
            }
        }

        function row(id) {
            return state.model.map[text(id)] || null;
        }

        function selectItem(id, force) {
            var item = row(id);
            if (!item || !item.selectable) { return false; }
            if (!selection.has(id)) {
                if (!multiple) { selection.clear(); }
                var result = selection.add(item);
                if (!result.ok && result.reason === "max") {
                    setMessage(L.max(maxSelected), "warn");
                    notify(options, L.max(maxSelected), "warn");
                    return false;
                }
                if (multiple && options.selectChildren) {
                    var rejected = 0;
                    descendants(state.model, id).slice(1).forEach(function (childId) {
                        var child = row(childId);
                        if (!child || !child.selectable) { return; }
                        var childResult = selection.add(child);
                        if (!childResult.ok && childResult.reason === "max") { rejected++; }
                    });
                    if (rejected) {
                        var message = "下级项目中有 " + rejected + " 个因超过选择上限未选中";
                        setMessage(message, "warn");
                        notify(options, message, "warn");
                    }
                }
            } else if (!force) {
                selection.remove(id);
            }
            emitChange();
            render();
            return true;
        }

        function toggleItem(id) {
            var changed = selectItem(id, false);
            if (changed && !multiple && options.confirmOnSelect) {
                confirm();
            }
        }

        function allowedIds(treeMode) {
            var allowed = {};
            var keyword = trim(state.keyword);
            state.model.order.forEach(function (id) {
                var item = state.model.map[id];
                var selectedOk = !state.selectedOnly || selection.has(id);
                var searchOk = !keyword || matchesRow(item, keyword);
                var filterOk = typeof options.filter !== "function"
                    || options.filter(item.raw, item) !== false;
                if (!(selectedOk && searchOk && filterOk)) { return; }
                allowed[id] = true;
                if (treeMode || keyword) {
                    var parent = item.parentId;
                    var guard = {};
                    while (parent && state.model.map[parent] && !guard[parent]) {
                        guard[parent] = true;
                        allowed[parent] = true;
                        parent = state.model.map[parent].parentId;
                    }
                }
            });
            return allowed;
        }

        function partialSelected(id) {
            if (selection.has(id)) { return false; }
            return descendants(state.model, id).slice(1).some(function (childId) {
                return selection.has(childId);
            });
        }

        function optionHtml(item) {
            var selected = selection.has(item.id);
            var partial = multiple && partialSelected(item.id);
            var disabled = !item.selectable;
            var custom = typeof options.optionHtml === "function"
                ? options.optionHtml(item.raw, {
                    selected: selected,
                    partial: partial,
                    disabled: disabled,
                    multiple: multiple
                }) : "";
            var content = custom || (
                "<span class='oa-picker-label'>"
                + "<span class='oa-picker-name'>" + esc(item.name) + "</span>"
                + (item.path && item.path !== item.name
                    ? "<span class='oa-picker-path'>" + esc(item.path) + "</span>" : "")
                + (!disabled && item.reason
                    ? "<span class='oa-picker-reason'>" + esc(item.reason) + "</span>"
                    : item.detail && !item.path
                        ? "<span class='oa-picker-path'>" + esc(item.detail) + "</span>" : "")
                + "</span>"
                + (item.badge ? "<span class='oa-picker-badge'>" + esc(item.badge) + "</span>" : "")
                + (item.count !== undefined && item.count !== null && item.count !== ""
                    ? "<span class='oa-picker-count-badge'>" + esc(item.count) + "</span>" : "")
            );
            return "<button type='button' class='oa-picker-option"
                + (selected ? " is-selected" : "")
                + (partial ? " is-partial" : "")
                + (disabled ? " is-disabled" : "")
                + "' data-oa-picker-id='" + esc(item.id) + "'"
                + " role='" + (multiple ? "checkbox" : "radio") + "'"
                + " aria-checked='" + (partial ? "mixed" : selected ? "true" : "false") + "'"
                + (disabled ? " disabled aria-disabled='true'" : "")
                + " title='" + esc(item.path || item.name) + "'>"
                + "<span class='oa-picker-check' aria-hidden='true'></span>"
                + content
                + (disabled && item.reason
                    ? "<span class='oa-picker-reason'>" + esc(item.reason) + "</span>" : "")
                + "</button>";
        }

        function treeHtml(allowed, selectedRight) {
            function walk(ids) {
                var html = "";
                ids.forEach(function (id) {
                    if (!allowed[id]) { return; }
                    var item = state.model.map[id];
                    var children = (state.model.children[id] || []).filter(function (childId) {
                        return allowed[childId];
                    });
                    var open = state.expanded[id] === true || !!trim(state.keyword) || selectedRight;
                    var depth = Math.max(0, Number(item.depth) || 0);
                    var indent = depth * 18;
                    html += "<div class='oa-picker-tree-row' role='treeitem' aria-level='"
                        + (depth + 1) + "' data-depth='" + depth
                        + "' style='--oa-picker-tree-indent:" + indent + "px;padding-left:" + indent + "px;'>";
                    if (children.length) {
                        html += "<button type='button' class='oa-picker-caret'"
                            + " data-oa-picker-expand='" + esc(id) + "' aria-label='"
                            + (open ? "收起下级" : "展开下级") + "'>" + (open ? "▾" : "▸") + "</button>";
                    } else {
                        html += "<span class='oa-picker-caret is-leaf' aria-hidden='true'></span>";
                    }
                    if (selectedRight) {
                        html += selectedTreeOption(item);
                    } else {
                        html += optionHtml(item);
                    }
                    html += "</div>";
                    if (open && children.length) { html += walk(children); }
                });
                return html;
            }
            return walk(state.model.roots);
        }

        function selectedTreeOption(item) {
            if (!selection.has(item.id)) {
                return "<div class='oa-picker-selected-row is-context'>"
                    + "<span class='oa-picker-name'>" + esc(item.name) + "</span>"
                    + "<small>" + esc(item.path || "") + "</small></div>";
            }
            return "<div class='oa-picker-selected-row'>"
                + "<div><strong>" + esc(item.name) + "</strong>"
                + "<small>" + esc(item.path || item.detail || "") + "</small></div>"
                + "<button type='button' class='oa-picker-selected-remove' data-oa-picker-remove='"
                + esc(item.id) + "' title='" + esc(L.remove) + "' aria-label='"
                + esc(L.remove + " " + item.name) + "'>−</button></div>";
        }

        function listHtml(allowed, selectedRight) {
            var ids = state.model.order.filter(function (id) { return allowed[id]; });
            if (!selectedRight && pager) {
                pager.resetIfChanged((state.keyword || "") + "|" + (state.selectedOnly ? "1" : "0"));
                ids = pager.apply(ids);
                pager.render();
                pager.show();
            }
            return ids.map(function (id) {
                var item = state.model.map[id];
                return "<div class='oa-picker-list-row'>"
                    + (selectedRight ? selectedTreeOption(item) : optionHtml(item))
                    + "</div>";
            }).join("");
        }

        function emptyHtml(message) {
            return "<div class='oa-picker-empty' role='status'>" + esc(message) + "</div>";
        }

        function renderCandidate() {
            if (state.loading) {
                refs.pager.hidden = true;
                refs.candidate.setAttribute("aria-busy", "true");
                refs.candidate.innerHTML = emptyHtml(L.loading);
                return;
            }
            if (state.error) {
                refs.pager.hidden = true;
                refs.candidate.setAttribute("aria-busy", "false");
                refs.candidate.innerHTML = "<div class='oa-picker-error' role='alert'>"
                    + "<span>" + esc(state.error) + "</span>"
                    + "<button type='button' class='btn small secondary' data-oa-picker-action='retry'>"
                    + esc(L.retry) + "</button></div>";
                return;
            }
            refs.candidate.setAttribute("aria-busy", "false");
            refs.pager.hidden = state.mode !== "list";
            if (state.mode === "list" && pager) { pager.show(); }
            else if (pager) { pager.hide(); }
            var allowed = allowedIds(state.mode === "tree");
            var html = state.mode === "tree" ? treeHtml(allowed, false) : listHtml(allowed, false);
            refs.candidate.innerHTML = html || emptyHtml(state.selectedOnly ? L.emptySelected
                : trim(state.keyword) ? L.emptySearch : L.empty);
        }

        function renderSelected() {
            if (!refs.selected) { return; }
            refs.selectedCount.textContent = selection.size() + " 项";
            if (!multiple || !state.split) { return; }
            var allowed = {};
            selection.values().forEach(function (id) {
                allowed[id] = true;
                var item = row(id);
                var parent = item ? item.parentId : "";
                var guard = {};
                while (parent && state.model.map[parent] && !guard[parent]) {
                    guard[parent] = true;
                    allowed[parent] = true;
                    parent = state.model.map[parent].parentId;
                }
            });
            if (state.mode === "tree") {
                refs.selected.innerHTML = treeHtml(allowed, true) || emptyHtml(L.emptySelected);
            } else {
                var items = selection.items();
                refs.selected.innerHTML = items.length
                    ? items.map(function (item) {
                        return selectedTreeOption(row(item.id) || item);
                    }).join("")
                    : emptyHtml(L.emptySelected);
            }
        }

        function render() {
            if (!modal || state.destroyed) { return; }
            var multiOnly = modal.querySelectorAll(".oa-picker-multi-only");
            for (var i = 0; i < multiOnly.length; i++) {
                multiOnly[i].hidden = !multiple;
            }
            var listButton = modal.querySelector("[data-oa-picker-mode='list']");
            var treeButton = modal.querySelector("[data-oa-picker-mode='tree']");
            listButton.classList.toggle("active", state.mode === "list");
            listButton.setAttribute("aria-pressed", state.mode === "list" ? "true" : "false");
            treeButton.hidden = !state.model.hasHierarchy;
            treeButton.classList.toggle("active", state.mode === "tree");
            treeButton.setAttribute("aria-pressed", state.mode === "tree" ? "true" : "false");
            var selectedButton = modal.querySelector("[data-oa-picker-action='selected-only']");
            var splitButton = modal.querySelector("[data-oa-picker-action='split']");
            if (selectedButton) {
                selectedButton.classList.toggle("active", state.selectedOnly);
                selectedButton.setAttribute("aria-pressed", state.selectedOnly ? "true" : "false");
            }
            if (splitButton) {
                splitButton.classList.toggle("active", state.split);
                splitButton.setAttribute("aria-pressed", state.split ? "true" : "false");
            }
            refs.selectedPanel.hidden = !multiple || !state.split;
            refs.splitter.hidden = !multiple || !state.split;
            if (!refs.splitter.hidden) {
                applySplitWidth(splitWidths[key] || defaultSplitWidth(), false);
            }
            refs.count.textContent = multiple
                ? "已选 " + selection.size() + (maxSelected ? " / " + maxSelected : "")
                : selection.size() ? "已选 1 项" : "未选择";
            refs.search.value = state.keyword;
            refs.confirm.textContent = L.confirm + (selection.size() ? "（" + selection.size() + "）" : "");
            if (refs.message.textContent !== state.message) {
                refs.message.textContent = state.message;
                refs.message.hidden = !state.message;
            }
            renderCandidate();
            renderSelected();
            renderAllButton();
        }

        function renderAllButton() {
            if (!multiple) { return; }
            var button = modal.querySelector("[data-oa-picker-action='select-all']");
            if (!button) { return; }
            var allowed = allowedIds(false);
            var ids = Object.keys(allowed).filter(function (id) {
                return state.model.map[id] && state.model.map[id].selectable;
            });
            var selected = ids.filter(function (id) { return selection.has(id); });
            var all = ids.length > 0 && selected.length === ids.length;
            button.textContent = all ? L.unselectAll : L.selectAll;
            button.classList.toggle("active", all);
            button.disabled = state.loading || !!state.error || ids.length === 0;
        }

        function toggleAllVisible() {
            var allowed = allowedIds(false);
            var items = Object.keys(allowed).map(function (id) {
                return state.model.map[id];
            }).filter(function (item) {
                return item && item.selectable;
            });
            var all = items.length > 0 && items.every(function (item) {
                return selection.has(item.id);
            });
            if (all) {
                items.forEach(function (item) { selection.remove(item.id); });
                emitChange();
                render();
                return;
            }
            var rejected = 0;
            items.forEach(function (item) {
                if (selection.has(item.id)) { return; }
                var result = selection.add(item);
                if (!result.ok && result.reason === "max") { rejected++; }
            });
            emitChange();
            render();
            if (rejected) {
                setMessage("有 " + rejected + " 项因超过选择上限未选中", "warn");
                notify(options, "有 " + rejected + " 项因超过选择上限未选中", "warn");
            }
        }

        function syncSelectionWithRows() {
            var before = selection.size();
            var next = [];
            selection.values().forEach(function (id) {
                var item = row(id);
                if (item && !item.selectable) { return; }
                next.push(item || selection.get(id));
            });
            selection.set(next);
            if (next.length !== before) {
                setMessage("已移除 " + (before - next.length) + " 个不可选择的项目", "warn");
            }
            selection.values().forEach(function (id) {
                var item = row(id);
                var parent = item ? item.parentId : "";
                var guard = {};
                while (parent && state.model.map[parent] && !guard[parent]) {
                    guard[parent] = true;
                    state.expanded[parent] = true;
                    parent = state.model.map[parent].parentId;
                }
            });
        }

        function normalizeData(data) {
            if (data && Array.isArray(data.items)) { return data.items; }
            if (data && Array.isArray(data.rows)) { return data.rows; }
            return asArray(data);
        }

        function loadRows() {
            var request = ++requestSeq;
            state.loading = true;
            state.error = "";
            render();
            if (typeof options.loadRows !== "function") {
                state.rows = normalizeRows(options.rows || [], options);
                state.model = buildModel(state.rows);
                state.loading = false;
                syncSelectionWithRows();
                render();
                return;
            }
            callLoader(options.loadRows, [context()], function (error, data) {
                if (request !== requestSeq || state.destroyed) { return; }
                state.loading = false;
                if (error) {
                    state.error = error.message || String(error) || L.failed;
                    render();
                    return;
                }
                state.rows = normalizeRows(normalizeData(data), options);
                state.model = buildModel(state.rows);
                syncSelectionWithRows();
                render();
            });
        }

        function confirm() {
            if (!selection.size() && options.allowEmpty === false) {
                setMessage("请选择项目", "warn");
                return;
            }
            var value = selection.snapshot();
            if (typeof options.onConfirm === "function") {
                options.onConfirm(value, context());
            }
            close(true);
        }

        function open() {
            state.destroyed = false;
            state.returnFocus = document ? document.activeElement : null;
            selection.set(options.initialSelected || options.selected || []);
            state.keyword = "";
            state.expanded = {};
            state.selectedOnly = false;
            state.split = false;
            state.error = "";
            state.message = "";
            state.rows = [];
            state.model = buildModel([]);
            mount();
            state.mode = options.tree === false ? "list"
                : options.mode === "tree" ? "tree"
                    : options.mode === "list" ? "list" : "tree";
            mask.style.display = "block";
            modal.style.display = "flex";
            if (global.OaonesModal && typeof global.OaonesModal.open === "function") {
                global.OaonesModal.open(modal, { mask: mask, focus: false });
            }
            modal.style.display = "flex";
            render();
            loadRows();
            global.setTimeout(function () {
                if (refs.search && refs.search.focus) { refs.search.focus(); }
            }, 0);
            return controller;
        }

        function close(confirmed) {
            if (!modal) { return; }
            if (splitDragStop) { splitDragStop(); }
            global.removeEventListener("resize", keepSplitWidthInRange);
            if (global.OaonesModal && typeof global.OaonesModal.close === "function") {
                global.OaonesModal.close(modal, { mask: mask });
            } else {
                mask.style.display = "none";
                modal.style.display = "none";
            }
            if (mask.parentNode) { mask.parentNode.removeChild(mask); }
            if (modal.parentNode) { modal.parentNode.removeChild(modal); }
            modal = null;
            mask = null;
            refs = {};
            pager = null;
            if (!confirmed && typeof options.onCancel === "function") {
                options.onCancel(context());
            }
            if (state.returnFocus && typeof state.returnFocus.focus === "function") {
                try { state.returnFocus.focus(); } catch (ignoreFocus) { }
            }
            state.returnFocus = null;
        }

        var controller = {
            key: key,
            open: open,
            close: function () { close(false); },
            refresh: loadRows,
            getSelection: function () { return selection.snapshot(); },
            setSelection: function (items) {
                selection.set(items);
                emitChange();
                render();
            },
            setRows: function (rows) {
                state.rows = normalizeRows(rows, options);
                state.model = buildModel(state.rows);
                state.loading = false;
                state.error = "";
                syncSelectionWithRows();
                render();
            },
            setMode: setMode,
            setMessage: setMessage,
            destroy: function () {
                state.destroyed = true;
                if (modal) { close(false); }
            }
        };
        return controller;
    }

    function open(options) {
        return create(options || {}).open();
    }

    global.OaonesPicker = {
        open: open,
        create: create,
        normalizeRows: normalizeRows,
        buildModel: buildModel,
        descendants: descendants,
        selection: createSelection,
        matchesRow: matchesRow,
        esc: esc
    };
})(window, document);
