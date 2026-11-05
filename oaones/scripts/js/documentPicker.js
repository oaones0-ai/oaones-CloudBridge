/**
 * OAones 通用文档选择器（OaonesDocumentPicker）。
 *
 * 依赖：
 *   modal.js -> tree.js -> picker.js -> table.js -> directoryPicker.js -> documentPicker.js
 *
 * 基础用法：
 *   OaonesDocumentPicker.open({
 *       key: "document-picker",
 *       multiple: true,
 *       maxSelected: 10,
 *       loadDirectories: function (context, done) {},
 *       loadDocuments: function (context, done) {
 *           done(null, { items: rows, total: 120, page: 1, pageSize: 20 });
 *       },
 *       onConfirm: function (selection) {}
 *   });
 *
 * loadDocuments 必须接收目录、搜索、筛选、排序和页码上下文。既支持服务端分页返回
 * {items,total,page,pageSize}，也支持一次返回数组后由 OaonesTable.pager() 前端分页。
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

    function copyOptions(source) {
        var target = {};
        Object.keys(source || {}).forEach(function (key) { target[key] = source[key]; });
        return target;
    }

    function defaultId(row) {
        return row.id !== undefined ? row.id
            : row.documentId !== undefined ? row.documentId
                : row.docId !== undefined ? row.docId : row.docid;
    }

    function defaultText(row) {
        return row.name !== undefined ? row.name
            : row.title !== undefined ? row.title
                : row.documentName !== undefined ? row.documentName : row.docTitle;
    }

    function formatSize(bytes) {
        var value = Number(bytes);
        if (!isFinite(value) || value < 0) { return ""; }
        if (value < 1024) { return value + " B"; }
        if (value < 1024 * 1024) { return (value / 1024).toFixed(1) + " KB"; }
        if (value < 1024 * 1024 * 1024) {
            return (value / (1024 * 1024)).toFixed(1) + " MB";
        }
        return (value / (1024 * 1024 * 1024)).toFixed(1) + " GB";
    }

    function normalizeDocument(row, options) {
        options = options || {};
        var idOf = options.idOf || defaultId;
        var textOf = options.textOf || defaultText;
        var directoryOf = options.directoryOf || function (item) {
            return item.directoryId !== undefined ? item.directoryId
                : item.folderId !== undefined ? item.folderId : item.dirId;
        };
        var directoryNameOf = options.directoryNameOf || function (item) {
            return item.directoryName !== undefined ? item.directoryName
                : item.folderName !== undefined ? item.folderName : item.dirName;
        };
        var statusOf = options.statusOf || function (item) {
            return item.statusText !== undefined ? item.statusText
                : item.status !== undefined ? item.status : item.state;
        };
        var disabledOf = options.disabledOf || function (item) {
            return item.selectable === false || item.disabled === true;
        };
        var reasonOf = options.reasonOf || function (item) {
            return item.disabledReason || item.reason || "";
        };
        var id = text(idOf(row || {}));
        if (!id) { return null; }
        var disabled = disabledOf(row) === true;
        var type = text(options.typeOf ? options.typeOf(row) : (row.type || row.fileType || row.extension || row.ext));
        var sizeValue = options.sizeOf ? options.sizeOf(row)
            : row.size !== undefined ? row.size : row.fileSize;
        var updatedValue = options.updatedOf ? options.updatedOf(row)
            : row.updatedAt !== undefined ? row.updatedAt
                : row.updateTime !== undefined ? row.updateTime : row.modifiedAt;
        return {
            id: id,
            name: text(textOf(row || {}), "文档 " + id),
            directoryId: text(directoryOf(row || {})),
            directoryName: text(directoryNameOf(row || {})),
            statusText: text(statusOf(row || {})),
            type: type,
            size: sizeValue,
            sizeText: formatSize(sizeValue),
            updatedAt: text(updatedValue),
            selectable: !disabled,
            reason: text(reasonOf(row || {})),
            raw: row
        };
    }

    function normalizeDocuments(rows, options) {
        var seen = {};
        var out = [];
        asArray(rows).forEach(function (row) {
            var item = normalizeDocument(row, options);
            if (!item || seen[item.id]) { return; }
            seen[item.id] = true;
            out.push(item);
        });
        return out;
    }

    function normalizePage(result, options) {
        var data = result || {};
        var rows = Array.isArray(result) ? result
            : data.items || data.rows || data.list || [];
        var items = normalizeDocuments(rows, options);
        var total = data.total === undefined ? items.length : Number(data.total);
        if (!isFinite(total) || total < 0) { total = items.length; }
        return {
            items: items,
            total: total,
            page: Number(data.page) > 0 ? Number(data.page) : 1,
            pageSize: Number(data.pageSize) > 0 ? Number(data.pageSize) : 20
        };
    }

    function matchesDocument(item, keyword) {
        var word = trim(keyword).toLowerCase();
        if (!word) { return true; }
        return [item.id, item.name, item.directoryName, item.type]
            .join(" ").toLowerCase().indexOf(word) >= 0;
    }

    function fallbackSelection(options) {
        var order = [];
        var map = {};
        var multiple = options.multiple === true;
        var limit = parseInt(options.maxSelected, 10);
        if (isNaN(limit) || limit < 0) { limit = 0; }
        function keyOf(item) {
            if (typeof item === "string" || typeof item === "number") { return text(item); }
            return text(item && item.id !== undefined ? item.id : (options.idOf || defaultId)(item || {}));
        }
        function add(item) {
            var id = keyOf(item);
            if (!id) { return { ok: false, reason: "invalid" }; }
            if (map[id]) { return { ok: true, added: false, item: map[id] }; }
            if (!multiple && order.length) { return { ok: false, reason: "single" }; }
            if (limit > 0 && order.length >= limit) { return { ok: false, reason: "max", max: limit }; }
            var data = typeof item === "object" && item
                ? item : { id: id, name: "文档 " + id, raw: item };
            map[id] = data;
            order.push(id);
            return { ok: true, added: true, item: data };
        }
        return {
            size: function () { return order.length; },
            values: function () { return order.slice(0); },
            items: function () { return order.map(function (id) { return map[id]; }); },
            has: function (id) { return !!map[text(id)]; },
            get: function (id) { return map[text(id)] || null; },
            add: add,
            remove: function (id) {
                var key = text(id);
                if (!map[key]) { return false; }
                delete map[key];
                order = order.filter(function (one) { return one !== key; });
                return true;
            },
            clear: function () { map = {}; order = []; },
            set: function (items) {
                map = {};
                order = [];
                asArray(items).forEach(add);
            },
            snapshot: function () {
                var list = order.map(function (id) { return map[id]; });
                return { ids: order.slice(0), items: list.slice(0) };
            }
        };
    }

    function createSelection(options) {
        if (global.OaonesDirectoryPicker
                && typeof global.OaonesDirectoryPicker.selection === "function") {
            return global.OaonesDirectoryPicker.selection(options);
        }
        return fallbackSelection(options);
    }

    function directoryHelpers() {
        if (global.OaonesDirectoryPicker
                && typeof global.OaonesDirectoryPicker.normalizeRows === "function") {
            return {
                normalize: global.OaonesDirectoryPicker.normalizeRows,
                model: global.OaonesDirectoryPicker.buildModel
            };
        }
        return {
            normalize: function (rows) {
                return asArray(rows).map(function (row) {
                    return {
                        raw: row,
                        id: text(row.id),
                        parentId: text(row.parentId),
                        name: text(row.name),
                        path: text(row.name),
                        depth: 0,
                        count: row.count,
                        selectable: row.selectable !== false,
                        reason: row.reason || "",
                        hasChildren: row.hasChildren === true
                    };
                });
            },
            model: function (rows) {
                var map = {};
                var children = {};
                var roots = [];
                rows.forEach(function (row) {
                    map[row.id] = row;
                    children[row.id] = [];
                });
                rows.forEach(function (row) {
                    if (row.parentId && map[row.parentId]) {
                        children[row.parentId].push(row.id);
                    } else {
                        roots.push(row.id);
                    }
                });
                return { rows: rows, map: map, children: children, roots: roots, order: rows.map(function (r) { return r.id; }) };
            }
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

    function notice(options, message, mode) {
        if (typeof options.onMessage === "function") {
            options.onMessage(message, mode || "info");
            return;
        }
        var fn = mode === "error" ? "error" : mode === "warn" ? "warn" : "info";
        if (global.OaonesToast && typeof global.OaonesToast[fn] === "function") {
            global.OaonesToast[fn](message);
        }
    }

    function labels(options) {
        var source = options.labels || {};
        return {
            title: text(options.title, text(source.title, "选择文档")),
            directoryTitle: text(source.directoryTitle, "目录"),
            directorySearch: text(source.directorySearch, "搜索目录"),
            allDirectories: text(source.allDirectories, "全部目录"),
            documentSearch: text(source.documentSearch, "搜索标题或文档 ID"),
            selectedOnly: text(source.selectedOnly, "仅显示已选择"),
            split: text(source.split, "左右显示"),
            selectedTitle: text(source.selectedTitle, "已选择"),
            selectCurrentPage: text(source.selectCurrentPage, "选择当前页"),
            selectFiltered: text(source.selectFiltered, "选择当前条件下全部"),
            clearSearch: text(source.clearSearch, "清空搜索"),
            clear: text(source.clear, "清空已选"),
            cancel: text(source.cancel, "取消"),
            confirm: text(options.confirmText, text(source.confirm, "确定")),
            loadingDirectories: text(source.loadingDirectories, "正在加载目录…"),
            loadingDocuments: text(source.loadingDocuments, "正在加载文档…"),
            directoryFailed: text(source.directoryFailed, "目录加载失败"),
            documentFailed: text(source.documentFailed, "文档列表加载失败"),
            retry: text(source.retry, "重新加载"),
            emptyDirectories: text(source.emptyDirectories, "没有可选目录"),
            emptyDocuments: text(source.emptyDocuments, "当前目录暂无可选文档"),
            emptySearch: text(source.emptySearch, "没有符合当前搜索条件的文档"),
            emptySelected: text(source.emptySelected, "尚未选择文档"),
            preview: text(source.preview, "预览"),
            remove: text(source.remove, "移除"),
            noPermission: text(source.noPermission, "没有权限"),
            max: function (max) { return "最多选择 " + max + " 个文档，请先移除已选文档"; }
        };
    }

    function create(rawOptions) {
        var options = copyOptions(rawOptions);
        var key = text(options.key, "oa-document-picker-" + (++sequence));
        var multiple = options.multiple === true;
        options.multiple = multiple;
        var maxSelected = parseInt(options.maxSelected, 10);
        if (isNaN(maxSelected) || maxSelected < 0) { maxSelected = 0; }
        options.maxSelected = maxSelected;
        options.itemLabel = text(options.itemLabel, "文档");
        var pageSize = parseInt(options.pageSize, 10);
        if (!(pageSize > 0)) { pageSize = 20; }
        options.pageSize = pageSize;
        var L = labels(options);
        var selection = createSelection(options);
        var dirHelpers = directoryHelpers();
        var state = {
            directoryRows: [],
            directoryModel: dirHelpers.model([]),
            expanded: {},
            directoryKeyword: "",
            directoryId: text(options.directoryId),
            allDirectories: [],
            allDocuments: [],
            items: [],
            total: 0,
            page: 1,
            pageSize: pageSize,
            keyword: "",
            filters: copyOptions(options.filters || {}),
            sort: text(options.sort, "updated-desc"),
            loadingDirectories: false,
            loadingDocuments: false,
            directoryError: "",
            documentError: "",
            selectedOnly: false,
            split: false,
            message: "",
            destroyed: false,
            returnFocus: null
        };
        var refs = {};
        var mask = null;
        var modal = null;
        var pager = null;
        var dirRequest = 0;
        var docRequest = 0;
        var searchTimer = 0;

        function context() {
            return {
                key: key,
                mode: options.mode || "select",
                directoryId: state.directoryId,
                keyword: state.keyword,
                filters: copyOptions(state.filters),
                page: state.page,
                pageSize: state.pageSize,
                sort: state.sort,
                selectedIds: selection.values(),
                multiple: multiple,
                maxSelected: maxSelected
            };
        }

        function emitChange() {
            if (typeof options.onSelectionChange === "function") {
                options.onSelectionChange(selection.snapshot(), context());
            }
        }

        function setMessage(message, mode) {
            state.message = text(message);
            if (refs.message) {
                refs.message.className = "oa-document-picker-message" + (mode ? " is-" + mode : "");
                refs.message.textContent = state.message;
                refs.message.hidden = !state.message;
            }
        }

        function shellHtml() {
            return ""
                + "<div class='oa-document-picker-layout'>"
                + "<aside class='oa-document-picker-directories' aria-label='" + esc(L.directoryTitle) + "'>"
                + "<div class='oa-document-picker-aside-head'><strong>" + esc(L.directoryTitle) + "</strong></div>"
                + "<input type='search' class='oa-document-picker-dir-search' data-docp-dir-search"
                + " placeholder='" + esc(L.directorySearch) + "' aria-label='" + esc(L.directorySearch) + "'>"
                + "<div class='oa-document-picker-dir-tree' data-docp-dir-tree role='tree'"
                + " aria-label='" + esc(L.directoryTitle) + "'></div>"
                + "</aside>"
                + "<section class='oa-document-picker-main' aria-label='" + esc(L.title) + "'>"
                + "<div class='oa-document-picker-toolbar'>"
                + "<input type='search' class='oa-document-picker-search' data-docp-search"
                + " placeholder='" + esc(L.documentSearch) + "' aria-label='" + esc(L.documentSearch) + "'>"
                + "<select class='oa-document-picker-sort' data-docp-sort aria-label='排序'>"
                + "<option value='updated-desc'>最近更新</option>"
                + "<option value='name-asc'>名称升序</option>"
                + "</select>"
                + "<div class='oa-document-picker-display oa-docp-multi-only' role='group' aria-label='多选显示模式'>"
                + "<button type='button' class='btn small secondary' data-docp-action='selected-only'>"
                + esc(L.selectedOnly) + "</button>"
                + "<button type='button' class='btn small secondary' data-docp-action='split'>"
                + esc(L.split) + "</button>"
                + "</div>"
                + "<button type='button' class='btn small secondary oa-docp-multi-only'"
                + " data-docp-action='select-filtered'>" + esc(L.selectFiltered) + "</button>"
                + "</div>"
                + "<div class='oa-document-picker-message' data-docp-message role='status' aria-live='polite'></div>"
                + "<div class='oa-document-picker-table-wrap'>"
                + "<table class='grid oa-document-picker-table'><thead><tr>"
                + "<th class='oa-document-picker-check-col'>"
                + "<input type='checkbox' class='oa-document-picker-check-all' data-docp-check-current"
                + " aria-label='" + esc(L.selectCurrentPage) + "'></th>"
                + "<th>文档</th><th>目录</th><th>状态</th><th>更新时间</th><th>大小</th><th></th>"
                + "</tr></thead><tbody data-docp-body></tbody></table>"
                + "</div>"
                + "<div class='pager oa-document-picker-pager' data-docp-pager></div>"
                + "</section>"
                + "<aside class='oa-document-picker-selected oa-docp-multi-only'"
                + " aria-label='" + esc(L.selectedTitle) + "'>"
                + "<div class='oa-document-picker-aside-head'><strong>" + esc(L.selectedTitle) + "</strong>"
                + "<span data-docp-selected-count></span></div>"
                + "<div class='oa-document-picker-selected-list' data-docp-selected></div>"
                + "</aside>"
                + "</div>"
                + "<div class='oa-document-picker-foot'>"
                + "<span class='oa-document-picker-count' data-docp-count></span>"
                + "<div class='oa-document-picker-actions'>"
                + "<button type='button' class='btn secondary oa-docp-multi-only' data-docp-action='clear'>"
                + esc(L.clear) + "</button>"
                + "<button type='button' class='btn secondary' data-docp-action='cancel'>"
                + esc(L.cancel) + "</button>"
                + "<button type='button' class='btn' data-docp-action='confirm'>"
                + esc(L.confirm) + "</button>"
                + "</div></div>";
        }

        function mount() {
            if (!document || !document.body || modal) { return; }
            mask = document.createElement("div");
            mask.className = "modal-mask oa-document-picker-mask";
            mask.id = "oaDocumentPickerMask-" + key.replace(/[^A-Za-z0-9_-]/g, "-");
            modal = document.createElement("div");
            modal.className = "modal oa-document-picker-modal";
            modal.setAttribute("role", "dialog");
            modal.setAttribute("aria-modal", "true");
            modal.setAttribute("aria-label", L.title);
            modal.setAttribute("data-modal-mask", mask.id);
            modal.innerHTML = ""
                + "<div class='modal-head'><span>" + esc(L.title) + "</span>"
                + "<button type='button' class='modal-close' data-docp-action='cancel'>×</button></div>"
                + "<div class='modal-body'>" + shellHtml() + "</div>";
            document.body.appendChild(mask);
            document.body.appendChild(modal);
            refs.dirSearch = modal.querySelector("[data-docp-dir-search]");
            refs.dirTree = modal.querySelector("[data-docp-dir-tree]");
            refs.search = modal.querySelector("[data-docp-search]");
            refs.sort = modal.querySelector("[data-docp-sort]");
            refs.body = modal.querySelector("[data-docp-body]");
            refs.table = modal.querySelector(".oa-document-picker-table");
            refs.checkAll = modal.querySelector("[data-docp-check-current]");
            refs.pager = modal.querySelector("[data-docp-pager]");
            refs.selected = modal.querySelector("[data-docp-selected]");
            refs.selectedPanel = modal.querySelector(".oa-document-picker-selected");
            refs.count = modal.querySelector("[data-docp-count]");
            refs.selectedCount = modal.querySelector("[data-docp-selected-count]");
            refs.message = modal.querySelector("[data-docp-message]");
            refs.selectFiltered = modal.querySelector("[data-docp-action='select-filtered']");
            refs.sort.value = state.sort;
            bind();
            mountPager();
        }

        function bind() {
            refs.dirSearch.addEventListener("input", function () {
                state.directoryKeyword = this.value || "";
                renderDirectories();
            });
            refs.search.addEventListener("input", function () {
                var value = this.value || "";
                if (searchTimer) { global.clearTimeout(searchTimer); }
                searchTimer = global.setTimeout(function () {
                    searchTimer = 0;
                    state.keyword = value;
                    resetPage();
                    loadDocuments();
                }, options.searchDelay === undefined ? 220 : options.searchDelay);
            });
            refs.sort.addEventListener("change", function () {
                state.sort = this.value || "updated-desc";
                resetPage();
                loadDocuments();
            });
            refs.dirTree.addEventListener("click", onDirectoryClick);
            refs.checkAll.addEventListener("change", toggleCurrentPage);
            refs.body.addEventListener("click", onBodyClick);
            refs.body.addEventListener("dblclick", onBodyDoubleClick);
            refs.selected.addEventListener("click", onBodyClick);
            modal.addEventListener("click", function (event) {
                var target = event.target && event.target.closest
                    ? event.target.closest("[data-docp-action]") : null;
                if (!target) { return; }
                handleAction(target.getAttribute("data-docp-action"));
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

        function onDirectoryClick(event) {
            var target = event.target;
            if (!target || !target.closest) { return; }
            var caret = target.closest("[data-docp-dir-expand]");
            if (caret) {
                var expandId = caret.getAttribute("data-docp-dir-expand");
                state.expanded[expandId] = state.expanded[expandId] !== true;
                renderDirectories();
                return;
            }
            var node = target.closest("[data-docp-dir-id]");
            if (!node || node.disabled) { return; }
            state.directoryId = node.getAttribute("data-docp-dir-id");
            resetPage();
            renderDirectories();
            loadDocuments();
        }

        function onBodyClick(event) {
            var target = event.target;
            if (!target || !target.closest) { return; }
            var remove = target.closest("[data-docp-remove]");
            if (remove) {
                selection.remove(remove.getAttribute("data-docp-remove"));
                emitChange();
                render();
                return;
            }
            var preview = target.closest("[data-docp-preview]");
            if (preview) {
                var previewItem = findItem(preview.getAttribute("data-docp-preview"));
                if (previewItem && typeof options.onPreview === "function") {
                    options.onPreview(previewItem.raw || previewItem, context());
                }
                return;
            }
            var option = target.closest("[data-docp-id]");
            if (option && !option.disabled) {
                toggleDocument(option.getAttribute("data-docp-id"));
            }
        }

        function onBodyDoubleClick(event) {
            var target = event.target;
            if (!target || !target.closest || target.closest("[data-docp-preview]")) { return; }
            var row = target.closest("[data-docp-row]");
            var option = target.closest("[data-docp-id]")
                || (row ? row.querySelector("[data-docp-id]") : null);
            if (!option || option.disabled) { return; }
            if (selectDocument(option.getAttribute("data-docp-id"), true)) { confirm(); }
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
            } else if (action === "select-filtered") {
                selectFiltered();
            } else if (action === "retry-documents") {
                loadDocuments();
            } else if (action === "retry-directories") {
                loadDirectories();
            } else if (action === "clear-search") {
                state.keyword = "";
                if (refs.search) { refs.search.value = ""; }
                resetPage();
                loadDocuments();
            }
        }

        function currentItems() {
            if (state.selectedOnly) {
                return selection.items().filter(function (item) {
                    return matchesDocument(item, state.keyword);
                });
            }
            return state.items.slice(0);
        }

        function findItem(id) {
            var keyId = text(id);
            var item = selection.get(keyId);
            if (item) { return item; }
            for (var i = 0; i < state.items.length; i++) {
                if (state.items[i].id === keyId) { return state.items[i]; }
            }
            return null;
        }

        function toggleDocument(id) {
            if (selection.has(id)) {
                selection.remove(id);
            } else {
                selectDocument(id, false);
                return;
            }
            emitChange();
            render();
        }

        function selectDocument(id, allowAlreadySelected) {
            var item = findItem(id);
            if (!item || item.selectable === false) { return false; }
            if (selection.has(id)) {
                if (!allowAlreadySelected) { return true; }
                return true;
            }
            if (!multiple) { selection.clear(); }
            var result = selection.add(item);
            if (!result.ok && result.reason === "max") {
                var message = L.max(maxSelected);
                setMessage(message, "warn");
                notice(options, message, "warn");
                return false;
            }
            emitChange();
            render();
            return true;
        }

        function addItems(items) {
            var added = 0;
            var rejected = 0;
            items.forEach(function (item) {
                if (!item || item.selectable === false || selection.has(item.id)) { return; }
                var result = selection.add(item);
                if (result.ok && result.added) { added++; }
                else if (!result.ok && result.reason === "max") { rejected++; }
            });
            emitChange();
            render();
            if (rejected) {
                var msg = "本次新增 " + added + " 个，另有 " + rejected + " 个因超过选择上限未选";
                setMessage(msg, "warn");
                notice(options, msg, "warn");
            } else if (added) {
                setMessage("本次新增 " + added + " 个文档");
            }
            return { added: added, rejected: rejected };
        }

        function toggleCurrentPage() {
            var items = currentItems().filter(function (item) { return item.selectable !== false; });
            var allSelected = items.length > 0 && items.every(function (item) {
                return selection.has(item.id);
            });
            if (allSelected) {
                items.forEach(function (item) { selection.remove(item.id); });
                emitChange();
                render();
            } else {
                addItems(items);
            }
        }

        function localFilteredDocuments() {
            return (state.allDocuments || []).filter(function (item) {
                if (state.directoryId && item.directoryId !== state.directoryId) {
                    // 目录包含下级目录时优先使用业务 adapter 提供的 directoryIds。
                    var allowed = options.directoryIdsOf
                        ? asArray(options.directoryIdsOf(item.raw || item)).map(String)
                        : [];
                    if (allowed.indexOf(state.directoryId) < 0) { return false; }
                }
                return matchesDocument(item, state.keyword);
            });
        }

        function selectFiltered() {
            if (typeof options.loadAllIds === "function") {
                setMessage("正在读取当前条件下的全部文档…");
                invoke(options.loadAllIds, [context()], function (error, data) {
                    if (error) {
                        var failed = error.message || String(error) || L.documentFailed;
                        setMessage(failed, "error");
                        return;
                    }
                    var items = normalizeDocuments(Array.isArray(data) ? data : data.items || [], options);
                    addItems(items);
                });
                return;
            }
            if (typeof options.loadDocuments === "function") {
                var unavailable = "当前未提供条件下全选接口，只能全选当前页";
                setMessage(unavailable, "warn");
                notice(options, unavailable, "warn");
                return;
            }
            addItems(localFilteredDocuments());
        }

        function ensurePager() {
            if (pager || !refs.pager || !global.OaonesTable
                    || typeof global.OaonesTable.pager !== "function") {
                return;
            }
            pager = global.OaonesTable.pager({
                key: key + "-documents",
                el: refs.pager,
                size: state.pageSize,
                onChange: function () {
                    state.page = pager.page();
                    state.pageSize = pager.size();
                    loadDocuments();
                }
            });
        }

        function mountPager() {
            ensurePager();
        }

        function resetPage() {
            state.page = 1;
            if (pager) { pager.reset(); }
        }

        function syncPager(total) {
            if (!pager) {
                state.total = total;
                return;
            }
            pager.apply(new Array(total));
            state.page = pager.page();
            state.pageSize = pager.size();
            state.total = total;
            pager.render();
            pager.show();
        }

        function hidePager() {
            if (pager) { pager.hide(); }
        }

        function directoryAllowed() {
            var allowed = {};
            var keyword = trim(state.directoryKeyword);
            state.directoryModel.order.forEach(function (id) {
                var row = state.directoryModel.map[id];
                if (!keyword || [row.id, row.name, row.path].join(" ").toLowerCase()
                        .indexOf(keyword.toLowerCase()) >= 0) {
                    allowed[id] = true;
                    if (keyword) {
                        var parent = row.parentId;
                        var guard = {};
                        while (parent && state.directoryModel.map[parent] && !guard[parent]) {
                            guard[parent] = true;
                            allowed[parent] = true;
                            parent = state.directoryModel.map[parent].parentId;
                        }
                    }
                }
            });
            return allowed;
        }

        function renderDirectories() {
            if (!refs.dirTree) { return; }
            if (state.loadingDirectories) {
                refs.dirTree.setAttribute("aria-busy", "true");
                refs.dirTree.innerHTML = "<div class='oa-document-picker-state'>"
                    + esc(L.loadingDirectories) + "</div>";
                return;
            }
            if (state.directoryError) {
                refs.dirTree.setAttribute("aria-busy", "false");
                refs.dirTree.innerHTML = "<div class='oa-document-picker-state is-error'>"
                    + esc(state.directoryError)
                    + "<button type='button' class='btn small secondary' data-docp-action='retry-directories'>"
                    + esc(L.retry) + "</button></div>";
                return;
            }
            var allowed = directoryAllowed();
            var html = "<button type='button' class='oa-document-picker-dir"
                + (state.directoryId === "" ? " is-active" : "")
                + "' data-docp-dir-id='' aria-pressed='" + (state.directoryId === "" ? "true" : "false")
                + "'>" + esc(L.allDirectories) + "</button>";
            function walk(ids) {
                ids.forEach(function (id) {
                    if (!allowed[id]) { return; }
                    var row = state.directoryModel.map[id];
                    var children = state.directoryModel.children[id] || [];
                    var visible = children.filter(function (child) { return allowed[child]; });
                    var open = state.expanded[id] === true || !!trim(state.directoryKeyword);
                    var depth = Math.max(0, Number(row.depth) || 0);
                    html += "<div class='oa-document-picker-dir-row' role='treeitem' aria-level='"
                        + (depth + 1) + "' style='--oa-document-picker-dir-depth:"
                        + depth + "'>";
                    if (visible.length || row.hasChildren) {
                        html += "<button type='button' class='oa-document-picker-dir-caret'"
                            + " data-docp-dir-expand='" + esc(id) + "' aria-label='"
                            + (open ? "收起下级" : "展开下级") + "'>" + (open ? "▾" : "▸") + "</button>";
                    } else {
                        html += "<span class='oa-document-picker-dir-caret is-leaf'></span>";
                    }
                    html += "<button type='button' class='oa-document-picker-dir"
                        + (state.directoryId === id ? " is-active" : "")
                        + "' data-docp-dir-id='" + esc(id) + "' aria-pressed='"
                        + (state.directoryId === id ? "true" : "false") + "'"
                        + (row.selectable === false ? " disabled" : "")
                        + " title='" + esc(row.path || row.name) + "'>"
                        + "<span>" + esc(row.name) + "</span>"
                        + (row.count === undefined || row.count === null || row.count === ""
                            ? "" : "<span class='oa-document-picker-dir-count'>" + esc(row.count) + "</span>")
                        + "</button></div>";
                    if (open && visible.length) { walk(visible); }
                });
            }
            walk(state.directoryModel.roots);
            if (state.directoryModel.order.length === 0) {
                html += "<div class='oa-document-picker-state'>" + esc(L.emptyDirectories) + "</div>";
            }
            refs.dirTree.innerHTML = html;
            refs.dirTree.setAttribute("aria-busy", "false");
        }

        function statusClass(item) {
            if (item.selectable === false) { return " is-disabled"; }
            return " is-selectable";
        }

        function documentRowHtml(item) {
            var selected = selection.has(item.id);
            var disabled = item.selectable === false;
            var status = item.statusText || (disabled ? item.reason || L.noPermission : "可选择");
            var preview = typeof options.onPreview === "function"
                ? "<button type='button' class='btn small secondary' data-docp-preview='"
                    + esc(item.id) + "'>" + esc(L.preview) + "</button>" : "";
            return "<tr class='" + (selected ? "is-selected" : "") + (disabled ? " is-disabled" : "") + "'"
                + " data-docp-row='" + esc(item.id) + "'>"
                + "<td><button type='button' class='oa-document-picker-option" + statusClass(item)
                + (selected ? " is-selected" : "")
                + "' data-docp-id='" + esc(item.id) + "'"
                + " role='" + (multiple ? "checkbox" : "radio") + "'"
                + " aria-checked='" + (selected ? "true" : "false") + "'"
                + (disabled ? " disabled aria-disabled='true'" : "")
                + "><span class='oa-document-picker-check' aria-hidden='true'></span></button></td>"
                + "<td><div class='oa-document-picker-doc-title'>" + esc(item.name) + "</div>"
                + "<div class='oa-document-picker-doc-id'>ID：" + esc(item.id)
                + (item.type ? " · " + esc(item.type) : "") + "</div></td>"
                + "<td title='" + esc(item.directoryName) + "'>" + esc(item.directoryName || "-") + "</td>"
                + "<td><span class='oa-document-picker-status'>" + esc(status) + "</span>"
                + (disabled && item.reason && item.reason !== status
                    ? "<small>" + esc(item.reason) + "</small>" : "") + "</td>"
                + "<td>" + esc(item.updatedAt || "-") + "</td>"
                + "<td>" + esc(item.sizeText || "-") + "</td>"
                + "<td>" + preview + "</td></tr>";
        }

        function renderDocuments() {
            if (!refs.body) { return; }
            var items = currentItems();
            if (state.loadingDocuments && !state.selectedOnly) {
                refs.body.innerHTML = "<tr><td colspan='7'><div class='oa-document-picker-state'>"
                    + esc(L.loadingDocuments) + "</div></td></tr>";
            } else if (state.documentError && !state.selectedOnly) {
                refs.body.innerHTML = "<tr><td colspan='7'><div class='oa-document-picker-state is-error'>"
                    + esc(state.documentError)
                    + "<button type='button' class='btn small secondary' data-docp-action='retry-documents'>"
                    + esc(L.retry) + "</button></div></td></tr>";
            } else if (!items.length) {
                var empty = state.selectedOnly ? L.emptySelected
                    : trim(state.keyword) ? L.emptySearch : L.emptyDocuments;
                refs.body.innerHTML = "<tr><td colspan='7'><div class='oa-document-picker-state'>"
                    + esc(empty)
                    + (trim(state.keyword) ? "<button type='button' class='btn small secondary'"
                        + " data-docp-action='clear-search'>" + esc(L.clearSearch) + "</button>" : "")
                    + "</div></td></tr>";
            } else {
                refs.body.innerHTML = items.map(documentRowHtml).join("");
            }

            var selectable = items.filter(function (item) { return item.selectable !== false; });
            var selectedCount = selectable.filter(function (item) {
                return selection.has(item.id);
            }).length;
            if (global.OaonesTable && typeof global.OaonesTable.syncCheckAll === "function") {
                global.OaonesTable.syncCheckAll(refs.checkAll, selectedCount, selectable.length);
            } else {
                refs.checkAll.setAttribute("aria-checked",
                    selectable.length && selectedCount === selectable.length ? "true"
                        : selectedCount ? "mixed" : "false");
            }
            if (global.OaonesTable && typeof global.OaonesTable.checkAllTitle === "function") {
                global.OaonesTable.checkAllTitle(refs.checkAll, "当前页文档");
            }
        }

        function renderSelected() {
            var items = selection.items();
            if (refs.selectedCount) { refs.selectedCount.textContent = items.length + " 项"; }
            if (!refs.selected) { return; }
            refs.selected.innerHTML = items.length ? items.map(function (item) {
                return "<div class='oa-document-picker-selected-row'>"
                    + "<div><strong>" + esc(item.name || ("文档 " + item.id)) + "</strong>"
                    + "<small>ID：" + esc(item.id) + "</small></div>"
                    + "<button type='button' class='btn small secondary' data-docp-remove='"
                    + esc(item.id) + "'>" + esc(L.remove) + "</button></div>";
            }).join("") : "<div class='oa-document-picker-state'>" + esc(L.emptySelected) + "</div>";
        }

        function render() {
            if (!modal || state.destroyed) { return; }
            var multiOnly = modal.querySelectorAll(".oa-docp-multi-only");
            for (var i = 0; i < multiOnly.length; i++) {
                multiOnly[i].hidden = !multiple;
            }
            var selectedBtn = modal.querySelector("[data-docp-action='selected-only']");
            var splitBtn = modal.querySelector("[data-docp-action='split']");
            if (selectedBtn) {
                selectedBtn.classList.toggle("active", state.selectedOnly);
                selectedBtn.setAttribute("aria-pressed", state.selectedOnly ? "true" : "false");
            }
            if (splitBtn) {
                splitBtn.classList.toggle("active", state.split);
                splitBtn.setAttribute("aria-pressed", state.split ? "true" : "false");
            }
            if (refs.selectedPanel) {
                refs.selectedPanel.hidden = !multiple || !state.split;
            }
            var actionText = selection.size() ? (options.confirmText || L.confirm)
                : L.confirm;
            var confirmBtn = modal.querySelector("[data-docp-action='confirm']");
            if (confirmBtn) {
                confirmBtn.textContent = actionText + (selection.size() ? "（" + selection.size() + "）" : "");
            }
            if (refs.count) {
                refs.count.textContent = multiple
                    ? "已选 " + selection.size() + (maxSelected ? " / " + maxSelected : "")
                    : selection.size() ? "已选 1 个文档" : "未选择文档";
            }
            if (refs.search && refs.search.value !== state.keyword) {
                refs.search.value = state.keyword;
            }
            if (refs.dirSearch && refs.dirSearch.value !== state.directoryKeyword) {
                refs.dirSearch.value = state.directoryKeyword;
            }
            if (refs.sort && refs.sort.value !== state.sort) {
                refs.sort.value = state.sort;
            }
            renderDirectories();
            renderDocuments();
            renderSelected();
            if (state.selectedOnly) {
                hidePager();
            } else if (pager) {
                pager.render();
                pager.show();
            }
        }

        function loadDirectories() {
            var request = ++dirRequest;
            state.loadingDirectories = true;
            state.directoryError = "";
            render();
            if (typeof options.loadDirectories !== "function") {
                state.directoryRows = dirHelpers.normalize(options.directories || [], options.directoryOptions || {});
                state.directoryModel = dirHelpers.model(state.directoryRows);
                state.loadingDirectories = false;
                render();
                return;
            }
            var dirContext = context();
            dirContext.keyword = state.directoryKeyword;
            invoke(options.loadDirectories, [dirContext], function (error, data) {
                if (request !== dirRequest || state.destroyed) { return; }
                state.loadingDirectories = false;
                if (error) {
                    state.directoryError = error.message || String(error) || L.directoryFailed;
                    render();
                    return;
                }
                var rows = Array.isArray(data) ? data
                    : data && (data.items || data.rows) || [];
                state.directoryRows = dirHelpers.normalize(rows, options.directoryOptions || {});
                state.directoryModel = dirHelpers.model(state.directoryRows);
                render();
            });
        }

        function loadDocuments() {
            if (state.selectedOnly) {
                render();
                return;
            }
            var request = ++docRequest;
            state.loadingDocuments = true;
            state.documentError = "";
            render();
            if (typeof options.loadDocuments !== "function") {
                state.loadingDocuments = false;
                var all = normalizeDocuments(options.documents || [], options);
                state.allDocuments = all;
                var filtered = localFilteredDocuments();
                syncPager(filtered.length);
                state.items = pager ? pager.apply(filtered) : filtered.slice(0, state.pageSize);
                state.page = pager ? pager.page() : 1;
                state.pageSize = pager ? pager.size() : state.pageSize;
                syncCurrentSelection(state.items);
                render();
                return;
            }
            var docContext = context();
            docContext.page = state.page;
            docContext.pageSize = state.pageSize;
            invoke(options.loadDocuments, [docContext], function (error, data) {
                if (request !== docRequest || state.destroyed) { return; }
                state.loadingDocuments = false;
                if (error) {
                    state.documentError = error.message || String(error) || L.documentFailed;
                    render();
                    return;
                }
                var pageData = normalizePage(data, options);
                state.items = pageData.items;
                state.page = pageData.page;
                state.pageSize = pageData.pageSize;
                syncPager(pageData.total);
                syncCurrentSelection(pageData.items);
                if (global.OaonesTable && typeof global.OaonesTable.resizable === "function") {
                    global.OaonesTable.resizable(refs.table, { key: key + "-table", min: 60 });
                }
                render();
            });
        }

        function confirm() {
            if (!selection.size() && options.allowEmpty === false) {
                setMessage("请选择文档", "warn");
                return;
            }
            var snapshot = selection.snapshot();
            if (typeof options.onConfirm === "function") {
                options.onConfirm(snapshot, context());
            }
            close(true);
        }

        function syncCurrentSelection(items) {
            var selectedIds = selection.values();
            if (!selectedIds.length) { return; }
            var current = {};
            items.forEach(function (item) { current[item.id] = item; });
            var next = [];
            selectedIds.forEach(function (id) {
                var item = current[id] || selection.get(id);
                if (item && item.selectable !== false) { next.push(item); }
            });
            if (next.length !== selectedIds.length) {
                selection.set(next);
                var message = "已移除 " + (selectedIds.length - next.length) + " 个不可选择的文档";
                setMessage(message, "warn");
                notice(options, message, "warn");
            } else {
                selection.set(next);
            }
        }

        function open() {
            state.destroyed = false;
            state.returnFocus = document ? document.activeElement : null;
            selection.set(options.initialSelected || options.selected || []);
            state.directoryId = text(options.directoryId);
            state.directoryKeyword = "";
            state.keyword = "";
            state.filters = copyOptions(options.filters || {});
            state.sort = text(options.sort, "updated-desc");
            state.selectedOnly = false;
            state.split = false;
            state.items = [];
            state.allDocuments = [];
            state.total = 0;
            state.page = 1;
            state.pageSize = pageSize;
            state.message = "";
            state.documentError = "";
            state.directoryError = "";
            state.expanded = {};
            mount();
            if (pager) {
                pager.reset();
                state.page = pager.page();
                state.pageSize = pager.size();
            }
            mask.style.display = "block";
            modal.style.display = "block";
            if (global.OaonesModal && typeof global.OaonesModal.open === "function") {
                global.OaonesModal.open(modal, { mask: mask, focus: false });
            }
            modal.style.display = "flex";
            render();
            loadDirectories();
            loadDocuments();
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
            reload: function () {
                loadDirectories();
                loadDocuments();
            },
            refreshDocuments: loadDocuments,
            getSelection: function () { return selection.snapshot(); },
            setSelection: function (items) { selection.set(items); emitChange(); render(); },
            setFilters: function (filters) {
                state.filters = copyOptions(filters || {});
                resetPage();
                loadDocuments();
            },
            setDirectory: function (id) {
                state.directoryId = text(id);
                resetPage();
                renderDirectories();
                loadDocuments();
            },
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

    global.OaonesDocumentPicker = {
        open: open,
        create: create,
        normalizeDocument: normalizeDocument,
        normalizeDocuments: normalizeDocuments,
        normalizePage: normalizePage,
        matchesDocument: matchesDocument,
        formatSize: formatSize,
        esc: esc
    };
})(window, document);
