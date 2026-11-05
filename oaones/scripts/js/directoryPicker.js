/**
 * OAones 通用目录选择器（OaonesDirectoryPicker）。
 *
 * 依赖：
 *   modal.js -> tree.js -> picker.js -> table.js -> directoryPicker.js
 *
 * 基础用法：
 *   OaonesDirectoryPicker.open({
 *       key: "knowledge-directory",
 *       title: "选择目录",
 *       multiple: true,
 *       maxSelected: 20,
 *       initialSelected: ["11", "12"],
 *       loadDirectories: function (context, done) {
 *           done(null, rows);
 *       },
 *       onConfirm: function (selection) {
 *           // selection.ids、selection.items
 *       }
 *   });
 *
 * 数据行默认识别 id、parentId、name；可通过 idOf、parentOf、textOf 等适配。
 * 加载函数同时兼容同步返回数组、返回 Promise 和调用 done(error, rows)。
 */
(function (global, document) {
    "use strict";

    var sequence = 0;

    function esc(value) {
        if (value === null || value === undefined) { return ""; }
        return String(value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function asArray(value) {
        if (value === null || value === undefined) { return []; }
        return Array.isArray(value) ? value.slice(0) : [value];
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

    function copyOptions(source) {
        var target = {};
        Object.keys(source || {}).forEach(function (key) { target[key] = source[key]; });
        return target;
    }

    function defaultId(row) {
        return row.id !== undefined ? row.id
            : row.nodeId !== undefined ? row.nodeId
                : row.directoryId !== undefined ? row.directoryId : row.dirId;
    }

    function defaultParent(row) {
        return row.parentId !== undefined ? row.parentId
            : row.parent !== undefined ? row.parent
                : row.pid !== undefined ? row.pid : row.parentNode;
    }

    function defaultText(row) {
        return row.name !== undefined ? row.name
            : row.title !== undefined ? row.title : row.directoryName;
    }

    function normalizeRows(rows, options) {
        options = options || {};
        var idOf = options.idOf || defaultId;
        var parentOf = options.parentOf || defaultParent;
        var textOf = options.textOf || defaultText;
        var selectableOf = options.selectableOf;
        var reasonOf = options.reasonOf;
        var countOf = options.countOf;
        var hasChildrenOf = options.hasChildrenOf;
        var pathOf = options.pathOf;
        var seen = {};
        var out = [];

        asArray(rows).forEach(function (raw) {
            var id = text(idOf(raw));
            if (!id || seen[id]) { return; }
            seen[id] = true;
            var parentId = text(parentOf(raw));
            if (parentId === "0" || parentId === "null" || parentId === "undefined") {
                parentId = "";
            }
            var selectable = typeof selectableOf === "function"
                ? selectableOf(raw) !== false
                : raw.selectable !== false && raw.disabled !== true;
            var reason = typeof reasonOf === "function" ? reasonOf(raw)
                : raw.disabledReason || raw.reason || "";
            var count = typeof countOf === "function" ? countOf(raw)
                : raw.count !== undefined ? raw.count : raw.childCount;
            var hasChildren = typeof hasChildrenOf === "function"
                ? hasChildrenOf(raw) === true
                : raw.hasChildren === true;
            out.push({
                raw: raw,
                id: id,
                parentId: parentId,
                name: text(textOf(raw), "目录 " + id),
                explicitPath: typeof pathOf === "function" ? text(pathOf(raw)) : "",
                selectable: selectable,
                reason: text(reason),
                count: count,
                hasChildren: hasChildren,
                path: "",
                depth: 0
            });
        });
        return out;
    }

    function buildModel(rows, options) {
        options = options || {};
        var model = {
            rows: rows || [],
            map: {},
            children: {},
            roots: [],
            order: [],
            circular: {}
        };
        model.rows.forEach(function (row) {
            model.map[row.id] = row;
            model.children[row.id] = [];
            model.order.push(row.id);
        });
        model.order.forEach(function (id) {
            var row = model.map[id];
            if (row.parentId && model.map[row.parentId] && row.parentId !== id) {
                model.children[row.parentId].push(id);
            } else {
                row.parentId = "";
                model.roots.push(id);
            }
        });

        // 断开循环父子链，避免任何渲染或路径计算出现无限递归。
        model.order.forEach(function (id) {
            var guard = {};
            var current = id;
            while (current) {
                if (guard[current]) {
                    model.circular[id] = true;
                    var row = model.map[id];
                    var oldParent = row.parentId;
                    if (oldParent && model.children[oldParent]) {
                        model.children[oldParent] = model.children[oldParent].filter(function (child) {
                            return child !== id;
                        });
                    }
                    row.parentId = "";
                    model.roots.push(id);
                    break;
                }
                guard[current] = true;
                current = model.map[current] ? model.map[current].parentId : "";
            }
        });
        if (global.OaonesTree && typeof global.OaonesTree.build === "function") {
            var sharedTree = global.OaonesTree.build(model.rows, {
                idOf: function (row) { return row.id; },
                parentOf: function (row) { return row.parentId; }
            });
            model.roots = sharedTree.roots.slice(0);
            model.children = sharedTree.children;
        }

        function pathFor(id) {
            var names = [];
            var guard = {};
            var current = id;
            while (current && model.map[current] && !guard[current]) {
                guard[current] = true;
                names.unshift(model.map[current].name);
                current = model.map[current].parentId;
            }
            return names.join(" / ");
        }

        function walk(ids, depth) {
            ids.forEach(function (id) {
                var row = model.map[id];
                if (!row) { return; }
                row.depth = depth;
                row.path = row.explicitPath || pathFor(id);
                walk(model.children[id] || [], depth + 1);
            });
        }
        walk(model.roots, 0);
        return model;
    }

    function descendants(model, id) {
        var out = [];
        var guard = {};
        function walk(nodeId) {
            if (!nodeId || guard[nodeId]) { return; }
            guard[nodeId] = true;
            out.push(nodeId);
            (model.children[nodeId] || []).forEach(walk);
        }
        walk(String(id));
        return out;
    }

    function matchRow(row, keyword) {
        var word = trim(keyword).toLowerCase();
        if (!word) { return true; }
        return [row.id, row.name, row.path].join(" ").toLowerCase().indexOf(word) >= 0;
    }

    function createSelection(options) {
        options = options || {};
        var idOf = options.idOf || defaultId;
        var textOf = options.textOf || defaultText;
        var multiple = options.multiple === true;
        var maxSelected = parseInt(options.maxSelected, 10);
        if (isNaN(maxSelected) || maxSelected < 0) { maxSelected = 0; }
        var order = [];
        var map = {};

        function idOfItem(item) {
            if (typeof item === "string" || typeof item === "number") {
                return text(item);
            }
            return text(item && item.id !== undefined ? item.id : idOf(item || {}));
        }

        function normalized(item) {
            var id = idOfItem(item);
            if (!id) { return null; }
            var raw = item && item.raw ? item.raw : item;
            return {
                id: id,
                name: text(item && item.name !== undefined ? item.name : textOf(raw || {}),
                    text(options.itemLabel, "目录") + " " + id),
                path: text(item && item.path),
                selectable: !item || item.selectable !== false,
                reason: text(item && item.reason),
                raw: raw || null
            };
        }

        function set(items) {
            order = [];
            map = {};
            asArray(items).forEach(function (item) {
                var data = normalized(item);
                if (!data) { return; }
                if (!multiple && order.length) { return; }
                if (maxSelected > 0 && order.length >= maxSelected) { return; }
                if (!map[data.id]) { order.push(data.id); }
                map[data.id] = data;
            });
        }

        function add(item) {
            var data = normalized(item);
            if (!data) { return { ok: false, reason: "invalid" }; }
            if (data.selectable === false) { return { ok: false, reason: "disabled" }; }
            if (map[data.id]) { return { ok: true, added: false, item: map[data.id] }; }
            if (!multiple && order.length) { return { ok: false, reason: "single" }; }
            if (maxSelected > 0 && order.length >= maxSelected) {
                return { ok: false, reason: "max", max: maxSelected };
            }
            order.push(data.id);
            map[data.id] = data;
            return { ok: true, added: true, item: data };
        }

        function remove(id) {
            var key = text(id);
            if (!map[key]) { return false; }
            delete map[key];
            order = order.filter(function (one) { return one !== key; });
            return true;
        }

        function clear() {
            order = [];
            map = {};
        }

        function items() {
            return order.map(function (id) { return map[id]; }).filter(Boolean);
        }

        function snapshot() {
            var list = items().map(function (item) {
                return {
                    id: item.id,
                    name: item.name,
                    path: item.path,
                    raw: item.raw
                };
            });
            return {
                ids: list.map(function (item) { return item.id; }),
                items: list
            };
        }

        return {
            multiple: multiple,
            maxSelected: maxSelected,
            size: function () { return order.length; },
            has: function (id) { return !!map[text(id)]; },
            get: function (id) { return map[text(id)] || null; },
            values: function () { return order.slice(0); },
            items: items,
            snapshot: snapshot,
            set: set,
            add: add,
            remove: remove,
            clear: clear
        };
    }

    function invoke(fn, args, done) {
        var finished = false;
        function finish(error, data) {
            if (finished) { return; }
            finished = true;
            done(error, data);
        }
        if (typeof fn !== "function") {
            finish(null, []);
            return;
        }
        var callArgs = args.slice(0);
        callArgs.push(finish);
        try {
            var result = fn.apply(null, callArgs);
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
        var o = options || {};
        if (typeof o.onMessage === "function") {
            o.onMessage(message, mode || "info");
            return;
        }
        var fn = mode === "warn" || mode === "error" ? "warn" : "info";
        if (global.OaonesToast && typeof global.OaonesToast[fn] === "function") {
            global.OaonesToast[fn](message);
        }
    }

    function labels(options) {
        var source = options.labels || {};
        return {
            title: text(options.title, text(source.title, "选择目录")),
            search: text(source.search, "搜索目录名称或 ID"),
            list: text(source.list, "列表式"),
            tree: text(source.tree, "树状"),
            selectedOnly: text(source.selectedOnly, "仅显示已选择"),
            split: text(source.split, "左右显示"),
            selectedTitle: text(source.selectedTitle, "已选择"),
            candidateTitle: text(source.candidateTitle, "目录"),
            confirm: text(options.confirmText, text(source.confirm, "确定")),
            cancel: text(source.cancel, "取消"),
            clear: text(source.clear, "清空已选"),
            empty: text(source.empty, "没有可选择的目录"),
            emptySearch: text(source.emptySearch, "没有匹配的目录"),
            emptySelected: text(source.emptySelected, "尚未选择目录"),
            loading: text(source.loading, "正在加载目录…"),
            failed: text(source.failed, "目录加载失败"),
            retry: text(source.retry, "重新加载"),
            max: function (max) { return "最多选择 " + max + " 个目录，请先移除已选目录"; }
        };
    }

    function create(rawOptions) {
        var options = copyOptions(rawOptions);
        var key = text(options.key, "oa-directory-picker-" + (++sequence));
        var multiple = options.multiple === true;
        options.multiple = multiple;
        var maxSelected = parseInt(options.maxSelected, 10);
        if (isNaN(maxSelected) || maxSelected < 0) { maxSelected = 0; }
        options.maxSelected = maxSelected;
        var L = labels(options);
        var selection = createSelection(options);
        var state = {
            rows: [],
            model: buildModel([], options),
            expanded: {},
            keyword: "",
            mode: "tree",
            selectedOnly: false,
            split: false,
            loading: false,
            error: "",
            initialized: false,
            destroyed: false,
            message: "",
            returnFocus: null
        };
        var refs = {};
        var mask = null;
        var modal = null;
        var searchTimer = 0;
        var requestSeq = 0;
        var pager = null;

        function emitChange() {
            var value = selection.snapshot();
            if (typeof options.onSelectionChange === "function") {
                options.onSelectionChange(value, context());
            }
        }

        function context() {
            return {
                key: key,
                keyword: state.keyword,
                selectedIds: selection.values(),
                expanded: copyOptions(state.expanded),
                multiple: multiple,
                maxSelected: maxSelected,
                mode: state.mode
            };
        }

        function shellHtml() {
            return ""
                + "<div class='oa-directory-picker-toolbar'>"
                + "<input type='search' class='oa-directory-picker-search' data-dp-search"
                + " placeholder='" + esc(L.search) + "' aria-label='" + esc(L.search) + "'>"
                + "<div class='oa-directory-picker-view view-switch mini' role='group' aria-label='显示方式'>"
                + "<button type='button' class='view-btn' data-dp-action='mode' data-mode='list'>"
                + esc(L.list) + "</button>"
                + "<button type='button' class='view-btn' data-dp-action='mode' data-mode='tree'>"
                + esc(L.tree) + "</button>"
                + "</div>"
                + "<div class='oa-directory-picker-display oa-multi-only' role='group' aria-label='多选显示模式'>"
                + "<button type='button' class='btn small secondary' data-dp-action='selected-only'>"
                + esc(L.selectedOnly) + "</button>"
                + "<button type='button' class='btn small secondary' data-dp-action='split'>"
                + esc(L.split) + "</button>"
                + "</div>"
                + "</div>"
                + "<div class='oa-directory-picker-message' data-dp-message role='status' aria-live='polite'></div>"
                + "<div class='oa-directory-picker-layout'>"
                + "<section class='oa-directory-picker-candidate' aria-label='" + esc(L.candidateTitle) + "'>"
                + "<div class='oa-directory-picker-content' data-dp-candidate role='tree'"
                + " aria-label='" + esc(L.candidateTitle) + "'></div>"
                + "<div class='pager oa-directory-picker-pager' data-dp-pager></div>"
                + "</section>"
                + "<section class='oa-directory-picker-selected-panel oa-multi-only'"
                + " aria-label='" + esc(L.selectedTitle) + "'>"
                + "<div class='oa-directory-picker-selected-head'>"
                + "<strong>" + esc(L.selectedTitle) + "</strong>"
                + "<span data-dp-selected-count></span>"
                + "</div>"
                + "<div class='oa-directory-picker-content' data-dp-selected role='tree'></div>"
                + "</section>"
                + "</div>"
                + "<div class='oa-directory-picker-foot'>"
                + "<span class='oa-directory-picker-count' data-dp-count></span>"
                + "<div class='oa-directory-picker-actions'>"
                + "<button type='button' class='btn secondary oa-multi-only' data-dp-action='clear'>"
                + esc(L.clear) + "</button>"
                + "<button type='button' class='btn secondary' data-dp-action='cancel'>"
                + esc(L.cancel) + "</button>"
                + "<button type='button' class='btn' data-dp-action='confirm'>"
                + esc(L.confirm) + "</button>"
                + "</div>"
                + "</div>";
        }

        function mount() {
            if (!document || !document.body || modal) { return; }
            mask = document.createElement("div");
            mask.className = "modal-mask";
            mask.setAttribute("data-directory-picker-mask", key);
            modal = document.createElement("div");
            modal.className = "modal oa-directory-picker-modal";
            modal.id = "oaDirectoryPickerModal-" + key.replace(/[^A-Za-z0-9_-]/g, "-");
            modal.setAttribute("role", "dialog");
            modal.setAttribute("aria-modal", "true");
            modal.setAttribute("aria-label", L.title);
            modal.setAttribute("data-modal-mask", mask.id || "");
            modal.innerHTML = ""
                + "<div class='modal-head'><span>" + esc(L.title) + "</span>"
                + "<button type='button' class='modal-close' data-dp-action='cancel'>×</button></div>"
                + "<div class='modal-body'>" + shellHtml() + "</div>";
            mask.id = "oaDirectoryPickerMask-" + key.replace(/[^A-Za-z0-9_-]/g, "-");
            modal.setAttribute("data-modal-mask", mask.id);
            document.body.appendChild(mask);
            document.body.appendChild(modal);
            refs.search = modal.querySelector("[data-dp-search]");
            refs.candidate = modal.querySelector("[data-dp-candidate]");
            refs.pager = modal.querySelector("[data-dp-pager]");
            refs.selected = modal.querySelector("[data-dp-selected]");
            refs.selectedPanel = modal.querySelector(".oa-directory-picker-selected-panel");
            refs.count = modal.querySelector("[data-dp-count]");
            refs.selectedCount = modal.querySelector("[data-dp-selected-count]");
            refs.message = modal.querySelector("[data-dp-message]");
            bind();
            mountPager();
        }

        function mountPager() {
            if (pager || !refs.pager || !global.OaonesTable
                    || typeof global.OaonesTable.pager !== "function") {
                return;
            }
            pager = global.OaonesTable.pager({
                key: key + "-directories",
                el: refs.pager,
                size: options.pageSize || 20,
                onChange: render
            });
        }

        function bind() {
            refs.search.addEventListener("input", function () {
                state.keyword = this.value || "";
                if (searchTimer) { global.clearTimeout(searchTimer); }
                searchTimer = global.setTimeout(function () {
                    searchTimer = 0;
                    if (typeof options.loadDirectories === "function" && options.remoteSearch) {
                        loadDirectories();
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
                var action = event.target && event.target.closest
                    ? event.target.closest("[data-dp-action]") : null;
                if (!action || action.closest(".oa-directory-picker-content")) { return; }
                handleAction(action.getAttribute("data-dp-action"), action);
            });
            modal.addEventListener("keydown", function (event) {
                if (event.key === "Escape" || event.keyCode === 27) {
                    event.preventDefault();
                    event.stopPropagation();
                    close(false);
                }
            });
            mask.addEventListener("click", function () { close(false); });
        }

        function onContentClick(event) {
            var target = event.target;
            if (!target || !target.closest) { return; }
            var caret = target.closest("[data-dp-expand]");
            if (caret) {
                event.preventDefault();
                toggleNode(caret.getAttribute("data-dp-expand"));
                return;
            }
            var remove = target.closest("[data-dp-remove]");
            if (remove) {
                event.preventDefault();
                selection.remove(remove.getAttribute("data-dp-remove"));
                emitChange();
                render();
                return;
            }
            var option = target.closest("[data-dp-id]");
            if (option && !option.disabled) {
                event.preventDefault();
                toggleSelection(option.getAttribute("data-dp-id"));
            }
        }

        function onDoubleClick(event) {
            var target = event.target;
            var option = target && target.closest ? target.closest("[data-dp-id]") : null;
            if (!option || option.disabled) { return; }
            event.preventDefault();
            if (selectOnly(option.getAttribute("data-dp-id"))) { confirm(); }
        }

        function handleAction(action, actionEl) {
            if (action === "cancel") { close(false); }
            else if (action === "confirm") { confirm(); }
            else if (action === "mode") {
                state.mode = actionEl && actionEl.getAttribute("data-mode") === "list" ? "list" : "tree";
                render();
            }
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
            }
        }

        function row(id) {
            return state.model.map[text(id)] || null;
        }

        function toggleSelection(id, force) {
            var item = row(id);
            if (!item) { return; }
            if (force === true || !selection.has(id)) {
                if (!multiple) {
                    selection.clear();
                }
                var result = selection.add(item);
                if (!result.ok && result.reason === "max") {
                    setMessage(L.max(maxSelected), "warn");
                    notify(options, L.max(maxSelected), "warn");
                } else if (!result.ok && result.reason === "disabled") {
                    setMessage(item.reason || "该目录不可选择", "warn");
                } else if (result.ok && multiple && options.selectChildren) {
                    var rejected = 0;
                    descendants(state.model, id).slice(1).forEach(function (childId) {
                        var child = row(childId);
                        if (child && child.selectable) {
                            var childResult = selection.add(child);
                            if (!childResult.ok && childResult.reason === "max") { rejected++; }
                        }
                    });
                    if (rejected) {
                        var childMessage = "下级目录中有 " + rejected + " 个因超过选择上限未选中";
                        setMessage(childMessage, "warn");
                        notify(options, childMessage, "warn");
                    }
                }
            } else {
                selection.remove(id);
            }
            emitChange();
            render();
        }

        function selectOnly(id) {
            var item = row(id);
            if (!item || !item.selectable) { return false; }
            if (selection.has(id)) { return true; }
            if (!multiple) { selection.clear(); }
            var result = selection.add(item);
            if (!result.ok && result.reason === "max") {
                setMessage(L.max(maxSelected), "warn");
                notify(options, L.max(maxSelected), "warn");
                return false;
            }
            emitChange();
            return true;
        }

        function toggleNode(id) {
            var item = row(id);
            if (!item) { return; }
            state.expanded[id] = state.expanded[id] !== true;
            if (state.expanded[id] && item.hasChildren
                    && !(state.model.children[id] || []).length
                    && typeof options.loadChildren === "function") {
                setMessage("正在加载下级目录…");
                invoke(options.loadChildren, [item.raw || item, context()], function (error, data) {
                    if (error) {
                        setMessage((error.message || String(error)) || "下级目录加载失败", "error");
                        render();
                        return;
                    }
                    var merged = mergeRows(state.rows, data);
                    state.rows = merged;
                    state.model = buildModel(merged, options);
                    item = row(id);
                    if (item) { item.hasChildren = false; }
                    setMessage("");
                    render();
                });
            }
            render();
        }

        function mergeRows(current, incoming) {
            var map = {};
            var merged = [];
            asArray(current).concat(normalizeRows(incoming, options)).forEach(function (rowValue) {
                var id = text(rowValue.id || (options.idOf || defaultId)(rowValue));
                if (!id || map[id]) { return; }
                map[id] = true;
                merged.push(rowValue);
            });
            return merged;
        }

        function allowedIds(includeAncestors) {
            var allowed = {};
            var keyword = trim(state.keyword);
            state.model.order.forEach(function (id) {
                var item = state.model.map[id];
                var selectedOk = !state.selectedOnly || selection.has(id);
                var searchOk = !keyword || matchRow(item, keyword);
                if (selectedOk && searchOk) {
                    allowed[id] = true;
                    if (includeAncestors || keyword) {
                        var parent = item.parentId;
                        var guard = {};
                        while (parent && state.model.map[parent] && !guard[parent]) {
                            guard[parent] = true;
                            allowed[parent] = true;
                            parent = state.model.map[parent].parentId;
                        }
                    }
                }
            });
            return allowed;
        }

        function optionHtml(item, side) {
            var selected = selection.has(item.id);
            var classes = "oa-directory-picker-option";
            if (selected) { classes += " is-selected"; }
            if (!item.selectable) { classes += " is-disabled"; }
            var sub = item.path && item.path !== item.name
                ? "<span class='oa-directory-picker-path'>" + esc(item.path) + "</span>" : "";
            var count = item.count === undefined || item.count === null || item.count === ""
                ? "" : "<span class='oa-directory-picker-node-count'>" + esc(item.count) + "</span>";
            var reason = !item.selectable && item.reason
                ? "<span class='oa-directory-picker-reason'>" + esc(item.reason) + "</span>" : "";
            if (side === "selected") {
                if (!selection.has(item.id)) {
                    return "<div class='oa-directory-picker-selected-row is-context'>"
                        + "<span class='oa-directory-picker-name'>" + esc(item.name) + "</span>"
                        + "<small>" + esc(item.path || "") + "</small></div>";
                }
                return "<div class='oa-directory-picker-selected-row'>"
                    + "<span class='oa-directory-picker-name'>" + esc(item.name) + "</span>"
                    + "<button type='button' class='btn small secondary' data-dp-remove='"
                    + esc(item.id) + "' title='移除'>移除</button></div>";
            }
            return "<button type='button' class='" + classes + "' data-dp-id='" + esc(item.id) + "'"
                + " role='" + (multiple ? "checkbox" : "radio") + "'"
                + " aria-checked='" + (selected ? "true" : "false") + "'"
                + (item.selectable ? "" : " disabled aria-disabled='true'")
                + " title='" + esc(item.path || item.name) + "'>"
                + "<span class='oa-directory-picker-check' aria-hidden='true'></span>"
                + "<span class='oa-directory-picker-label'>"
                + "<span class='oa-directory-picker-name'>" + esc(item.name) + "</span>"
                + sub + reason + "</span>" + count + "</button>";
        }

        function treeHtml(allowed, side) {
            function walk(ids) {
                var html = "";
                ids.forEach(function (id) {
                    if (!allowed[id]) { return; }
                    var item = state.model.map[id];
                    var children = state.model.children[id] || [];
                    var visibleChildren = children.filter(function (child) {
                        return allowed[child];
                    });
                    var open = side === "selected" || state.expanded[id] === true || !!trim(state.keyword);
                    var canExpand = visibleChildren.length > 0 || (item.hasChildren && side !== "selected");
                    html += "<div class='oa-directory-picker-row' role='treeitem' aria-level='"
                        + (item.depth + 1) + "' data-dp-tree-row='" + esc(id) + "'>";
                    if (canExpand) {
                        html += "<button type='button' class='oa-directory-picker-caret'"
                            + " data-dp-expand='" + esc(id) + "' aria-label='"
                            + (open ? "收起下级" : "展开下级") + "'>"
                            + (open ? "▾" : "▸") + "</button>";
                    } else {
                        html += "<span class='oa-directory-picker-caret is-leaf' aria-hidden='true'></span>";
                    }
                    html += optionHtml(item, side);
                    html += "</div>";
                    if (open && visibleChildren.length) {
                        html += walk(visibleChildren);
                    }
                });
                return html;
            }
            return walk(state.model.roots);
        }

        function listHtml(allowed, side) {
            var ids = state.model.order.filter(function (id) { return allowed[id]; });
            if (side === "candidate" && pager) {
                pager.resetIfChanged((state.keyword || "") + "|" + (state.selectedOnly ? "1" : "0"));
                ids = pager.apply(ids);
                pager.render();
                pager.show();
            }
            return ids.map(function (id) {
                var item = state.model.map[id];
                return "<div class='oa-directory-picker-row'>"
                    + "<span class='oa-directory-picker-caret is-leaf' aria-hidden='true'></span>"
                    + optionHtml(item, side) + "</div>";
            }).join("");
        }

        function emptyHtml(message) {
            return "<div class='oa-directory-picker-empty' role='status'>" + esc(message) + "</div>";
        }

        function renderContent(target, allowed, side) {
            if (state.loading && side !== "selected") {
                if (side === "candidate" && refs.pager) { refs.pager.hidden = true; }
                target.innerHTML = emptyHtml(L.loading);
                target.setAttribute("aria-busy", "true");
                return;
            }
            if (state.error && side !== "selected") {
                if (side === "candidate" && refs.pager) { refs.pager.hidden = true; }
                target.setAttribute("aria-busy", "false");
                target.innerHTML = "<div class='oa-directory-picker-error' role='alert'>"
                    + "<span>" + esc(state.error) + "</span>"
                    + "<button type='button' class='btn small secondary' data-dp-action='retry'>"
                    + esc(L.retry) + "</button></div>";
                return;
            }
            target.setAttribute("aria-busy", "false");
            if (side === "candidate" && refs.pager) {
                if (state.mode === "list") {
                    refs.pager.hidden = false;
                    if (pager) { pager.show(); }
                } else {
                    refs.pager.hidden = true;
                    if (pager) { pager.hide(); }
                }
            }
            var html = state.mode === "tree" ? treeHtml(allowed, side) : listHtml(allowed, side);
            if (!html) {
                target.innerHTML = emptyHtml(state.selectedOnly ? L.emptySelected
                    : trim(state.keyword) ? L.emptySearch : L.empty);
            } else {
                target.innerHTML = html;
            }
        }

        function render() {
            if (!modal || state.destroyed) { return; }
            var multiOnly = modal.querySelectorAll(".oa-multi-only");
            for (var i = 0; i < multiOnly.length; i++) {
                multiOnly[i].hidden = !multiple;
            }
            var modeBtns = modal.querySelectorAll("[data-dp-action='mode']");
            for (var j = 0; j < modeBtns.length; j++) {
                modeBtns[j].classList.toggle("active", modeBtns[j].getAttribute("data-mode") === state.mode);
                modeBtns[j].setAttribute("aria-pressed",
                    modeBtns[j].getAttribute("data-mode") === state.mode ? "true" : "false");
            }
            var selectedBtn = modal.querySelector("[data-dp-action='selected-only']");
            var splitBtn = modal.querySelector("[data-dp-action='split']");
            if (selectedBtn) {
                selectedBtn.classList.toggle("active", state.selectedOnly);
                selectedBtn.setAttribute("aria-pressed", state.selectedOnly ? "true" : "false");
            }
            if (splitBtn) {
                splitBtn.classList.toggle("active", state.split);
                splitBtn.setAttribute("aria-pressed", state.split ? "true" : "false");
            }
            if (refs.search && refs.search.value !== state.keyword) {
                refs.search.value = state.keyword;
            }
            refs.count.textContent = multiple
                ? "已选 " + selection.size() + (maxSelected ? " / " + maxSelected : "")
                : selection.size() ? "已选 1 个目录" : "未选择目录";
            refs.selectedCount.textContent = selection.size() + " 项";
            refs.selectedPanel.hidden = !multiple || !state.split;
            refs.message.textContent = state.message || "";
            refs.message.hidden = !state.message;
            renderContent(refs.candidate, allowedIds(state.mode === "tree"), "candidate");
            if (multiple && state.split) {
                var selectedAllowed = {};
                selection.values().forEach(function (id) {
                    selectedAllowed[id] = true;
                    var parent = row(id) ? row(id).parentId : "";
                    var guard = {};
                    while (parent && state.model.map[parent] && !guard[parent]) {
                        guard[parent] = true;
                        selectedAllowed[parent] = true;
                        parent = state.model.map[parent].parentId;
                    }
                });
                if (state.mode === "tree") {
                    refs.selected.innerHTML = treeHtml(selectedAllowed, "selected")
                        || emptyHtml(L.emptySelected);
                } else {
                    var items = selection.items();
                    refs.selected.innerHTML = items.length
                        ? items.map(function (item) {
                            return optionHtml(row(item.id) || item, "selected");
                        }).join("")
                        : emptyHtml(L.emptySelected);
                }
            }
        }

        function setMessage(message, mode) {
            state.message = text(message);
            if (refs.message) {
                refs.message.className = "oa-directory-picker-message"
                    + (mode ? " is-" + mode : "");
                refs.message.textContent = state.message;
                refs.message.hidden = !state.message;
            }
        }

        function normalizeData(data) {
            if (data && Array.isArray(data.items)) { return data.items; }
            if (data && Array.isArray(data.rows)) { return data.rows; }
            return asArray(data);
        }

        function validateSelection() {
            var next = [];
            var before = selection.size();
            selection.values().forEach(function (id) {
                var item = row(id);
                if (item && !item.selectable) {
                    selection.remove(id);
                    return;
                }
                next.push(item || selection.get(id));
            });
            if (next.length !== before) {
                setMessage("已移除 " + (before - next.length) + " 个不可选择的目录", "warn");
            }
            selection.set(next);
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

        function loadDirectories() {
            var seq = ++requestSeq;
            state.loading = true;
            state.error = "";
            render();
            if (typeof options.loadDirectories !== "function") {
                state.rows = normalizeRows(options.rows || options.directories || [], options);
                state.model = buildModel(state.rows, options);
                state.loading = false;
                state.initialized = true;
                validateSelection();
                render();
                return;
            }
            invoke(options.loadDirectories, [context()], function (error, data) {
                if (seq !== requestSeq || state.destroyed) { return; }
                state.loading = false;
                if (error) {
                    state.error = error.message || String(error) || L.failed;
                    render();
                    return;
                }
                state.rows = normalizeRows(normalizeData(data), options);
                state.model = buildModel(state.rows, options);
                state.initialized = true;
                validateSelection();
                render();
            });
        }

        function confirm() {
            if (!selection.size() && options.allowEmpty === false) {
                setMessage("请选择目录", "warn");
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
            state.mode = options.mode === "list" ? "list" : "tree";
            state.selectedOnly = false;
            state.split = false;
            state.expanded = {};
            state.rows = [];
            state.model = buildModel([], options);
            state.error = "";
            state.message = "";
            mount();
            mask.style.display = "block";
            modal.style.display = "block";
            render();
            loadDirectories();
            if (global.OaonesModal && typeof global.OaonesModal.open === "function") {
                global.OaonesModal.open(modal, { mask: mask, focus: false });
            }
            modal.style.display = "flex";
            global.setTimeout(function () {
                if (refs.search && refs.search.focus) { refs.search.focus(); }
            }, 0);
            return controller;
        }

        function close(confirmed) {
            if (!modal) { return; }
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
            refresh: loadDirectories,
            render: render,
            getSelection: function () { return selection.snapshot(); },
            setSelection: function (items) { selection.set(items); emitChange(); render(); },
            setRows: function (rows) {
                state.rows = normalizeRows(rows, options);
                state.model = buildModel(state.rows, options);
                state.loading = false;
                state.error = "";
                render();
            },
            setMessage: setMessage,
            destroy: function () {
                state.destroyed = true;
                if (modal) { close(false); }
            }
        };

        // 内容区的重试按钮由 modal 级委托触发。
        var originalMount = mount;
        mount = function () {
            originalMount();
            if (!modal || modal.getAttribute("data-dp-retry-bound") === "1") { return; }
            modal.setAttribute("data-dp-retry-bound", "1");
            modal.addEventListener("click", function (event) {
                var retry = event.target && event.target.closest
                    ? event.target.closest("[data-dp-action='retry']") : null;
                if (retry) { loadDirectories(); }
            });
        };
        return controller;
    }

    function open(options) {
        return create(options || {}).open();
    }

    global.OaonesDirectoryPicker = {
        open: open,
        create: create,
        normalizeRows: normalizeRows,
        buildModel: buildModel,
        descendants: descendants,
        selection: createSelection,
        matchRow: matchRow,
        esc: esc
    };
})(window, document);
