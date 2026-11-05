/**
 * OAones 通用树组件（OaonesTree）。
 *
 * 只负责树模型、搜索路径和 HTML 渲染，不绑定业务数据来源。
 *
 * opt = {
 *   idOf, parentOf, textOf,
 *   isGroup, groupHtml, optionHtml,
 *   keyword, emptyText, state, key
 * }
 */
(function (global) {
    "use strict";

    function esc(value) {
        if (value === null || value === undefined) { return ""; }
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function build(rows, options) {
        options = options || {};
        var idOf = options.idOf || function (row) { return row.id; };
        var parentOf = options.parentOf || function (row) { return row.parent; };
        var model = { byId: {}, children: {}, parent: {}, depth: {}, roots: [], order: [] };
        (rows || []).forEach(function (row) {
            var id = String(idOf(row));
            if (model.byId[id]) { return; }
            model.byId[id] = row;
            model.children[id] = [];
            model.order.push(id);
        });
        model.order.forEach(function (id) {
            var parentValue = parentOf(model.byId[id]);
            var parentId = String(parentValue == null ? "" : parentValue);
            if (!parentId || parentId === "0" || parentId === id || !model.byId[parentId]) {
                model.parent[id] = "";
                model.roots.push(id);
            } else {
                model.parent[id] = parentId;
                model.children[parentId].push(id);
            }
        });
        (function walk(ids, depth) {
            ids.forEach(function (id) {
                model.depth[id] = depth;
                walk(model.children[id], depth + 1);
            });
        })(model.roots, 0);
        return model;
    }

    function partialSelected(ids, childMapFn) {
        var map = childMapFn ? childMapFn() : {};
        var selected = {};
        (ids || []).forEach(function (id) { selected[String(id)] = true; });
        var memo = {};
        function hasSelectedBelow(id) {
            if (memo[id] !== undefined) { return memo[id]; }
            memo[id] = false;
            var children = map[id] || [];
            for (var i = 0; i < children.length; i++) {
                var child = String(children[i]);
                if (selected[child] || hasSelectedBelow(child)) {
                    memo[id] = true;
                    break;
                }
            }
            return memo[id];
        }
        var out = {};
        Object.keys(map).forEach(function (id) {
            if (!selected[id] && hasSelectedBelow(id)) { out[id] = true; }
        });
        return out;
    }

    function groupHtml(key, row, open, isHit) {
        var count = row.count === undefined || row.count === null ? "" : row.count;
        return "<button type='button' class='wx-pick-group" + (isHit ? " group-hit" : "") + "'"
            + " data-tree='" + esc(key) + "' data-node='" + esc(row.id) + "'"
            + " title='" + esc(row.title || row.fullname || row.name || "") + "'>"
            + "<span class='wx-pick-group-name'>" + esc(row.name || "") + "</span>"
            + (count === "" ? "" : "<span class='wx-pick-group-count'>" + esc(count) + "</span>")
            + "</button>";
    }

    function render(rows, options) {
        options = options || {};
        var key = String(options.key || "");
        var model = build(rows, options);
        var textOf = options.textOf || function (row) { return row.name; };
        var isGroup = options.isGroup || function () { return false; };
        var keyword = String(options.keyword == null ? "" : options.keyword).replace(/^\s+|\s+$/g, "").toLowerCase();
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
                var current = id;
                while (current && !visible[current]) {
                    visible[current] = true;
                    current = model.parent[current];
                }
            });
            if (!model.order.some(function (id) { return hit[id]; })) {
                return "<div class='" + (options.emptyClass || "wx-pick-empty") + "'>"
                    + esc(options.emptyText || "没有匹配的数据") + "</div>";
            }
        }
        var state = options.state || {};
        var html = "";
        (function walk(ids) {
            ids.forEach(function (id) {
                if (visible && !visible[id]) { return; }
                var row = model.byId[id];
                var children = model.children[id] || [];
                var open = keyword ? true : state[id] === true;
                var padding = 4 + model.depth[id] * 16;
                html += "<div class='tree-row" + (hit[id] ? " tree-hit" : "") + "' style='padding-left:" + padding + "px;'>";
                if (children.length) {
                    html += "<button type='button' class='tree-caret' data-tree='" + esc(key) + "'"
                        + " data-node='" + esc(id) + "' title='" + (open ? "收起下级" : "展开下级") + "'>"
                        + (open ? "▾" : "▸") + "</button>";
                } else {
                    html += "<span class='tree-caret leaf'>•</span>";
                }
                if (isGroup(row) && options.groupHtml) {
                    html += options.groupHtml(row, open, !!hit[id]);
                } else if (options.optionHtml) {
                    html += options.optionHtml(row, !!hit[id]);
                }
                html += "</div>";
                if (children.length && open) { walk(children); }
            });
        })(model.roots);
        return html || "<div class='" + (options.emptyClass || "wx-pick-empty") + "'>"
            + esc(options.emptyText || "没有匹配的数据") + "</div>";
    }

    global.OaonesTree = {
        build: build,
        buildTree: build,
        partialSelected: partialSelected,
        groupHtml: groupHtml,
        render: render,
        renderTree: render
    };
})(window);
