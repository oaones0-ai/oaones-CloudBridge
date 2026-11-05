/**
 * OAones OA 组织选择适配器（OaonesHrmPicker）。
 *
 * 负责统一人员 / 部门 / 分部的数据字段、列表渲染、树渲染和选中态计算；
 * 弹框本身仍由 modal.js / picker.js 负责，业务页面只提供数据与确认动作。
 *
 * 用法：
 *   var html = OaonesHrmPicker.render("deptPick", rows, {
 *       type: "department",
 *       mode: "tree",
 *       keyword: "销售",
 *       selected: ["101", "102"],
 *       multiple: true,
 *       onBadge: function (row) { return row.disabled ? "已占用" : ""; }
 *   });
 */
(function (global) {
    "use strict";

    var TYPE_DEFAULTS = {
        user: {
            idOf: function (row) { return row.id || row.user_id || row.uid; },
            parentOf: function (row) { return row.dept_id || row.departmentid || row.parent_id; },
            textOf: function (row) { return row.name || row.lastname || row.loginid || ""; },
            codeOf: function (row) { return row.loginid || row.login || row.workcode || ""; },
            subOf: function (row) { return row.dept_name || row.department_name || ""; },
            title: "选择人员"
        },
        department: {
            idOf: function (row) { return row.id || row.dept_id || row.departmentid; },
            parentOf: function (row) { return row.parent || row.parentid || row.parent_id || row.supdepid; },
            textOf: function (row) { return row.name || row.department_name || row.dept_name || ""; },
            codeOf: function (row) { return row.code || row.dept_code || row.department_code || ""; },
            subOf: function (row) { return row.subcompany_name || row.sub_name || ""; },
            title: "选择部门"
        },
        subcompany: {
            idOf: function (row) { return row.id || row.subcompanyid || row.sub_id; },
            parentOf: function (row) { return row.parent || row.parentid || row.parent_id; },
            textOf: function (row) { return row.name || row.subcompany_name || row.sub_name || ""; },
            codeOf: function (row) { return row.code || row.subcompany_code || row.sub_code || ""; },
            subOf: function () { return ""; },
            title: "选择分部"
        }
    };

    function esc(value) {
        if (value === null || value === undefined) { return ""; }
        return String(value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function defaults(type, options) {
        options = options || {};
        var base = TYPE_DEFAULTS[String(type || "user")] || TYPE_DEFAULTS.user;
        return {
            type: type || "user",
            title: options.title || base.title,
            idOf: options.idOf || base.idOf,
            parentOf: options.parentOf || base.parentOf,
            textOf: options.textOf || base.textOf,
            codeOf: options.codeOf || base.codeOf,
            subOf: options.subOf || base.subOf
        };
    }

    function selectedMap(selected) {
        var out = {};
        (selected || []).forEach(function (id) { out[String(id)] = true; });
        return out;
    }

    function toggle(selected, id) {
        var target = String(id == null ? "" : id);
        var list = (selected || []).slice(0);
        var index = list.map(String).indexOf(target);
        if (index >= 0) { list.splice(index, 1); } else { list.push(target); }
        return list;
    }

    function defaultOptionHtml(row, options, selected) {
        var type = options.type || "user";
        var name = options.textOf(row) || "";
        var code = options.codeOf ? options.codeOf(row) : "";
        var sub = options.subOf ? options.subOf(row) : "";
        var badge = typeof options.onBadge === "function" ? options.onBadge(row) : "";
        var cls = "wx-pick-option" + (selected ? " active" : "");
        var inner = "";
        if (options.multiple) {
            inner += "<input type='checkbox' tabindex='-1'" + (selected ? " checked" : "")
                + (options.disabledOf && options.disabledOf(row) ? " disabled" : "") + ">";
        }
        inner += "<span class='wx-pick-opt-name'>" + esc(name) + "</span>";
        if (type !== "subcompany") {
            inner += "<span class='wx-pick-opt-sub'>" + esc(code || sub || "-") + "</span>";
        }
        if (badge) { inner += "<span class='wx-pick-opt-badge'>" + esc(badge) + "</span>"; }
        return "<button type='button' class='" + cls + "' data-id='" + esc(options.idOf(row)) + "'"
            + (options.disabledOf && options.disabledOf(row) ? " disabled" : "") + ">" + inner + "</button>";
    }


    function render(key, rows, options) {
        options = defaults(options && options.type, options);
        var selected = options.selectedMap || selectedMap(options.selected);
        var textOf = options.textOf;
        var optionHtml = options.optionHtml || function (row, hit) {
            return defaultOptionHtml(row, options, !!selected[String(options.idOf(row))]);
        };
        var pageRows = [];
        var keyword = String(options.keyword == null ? "" : options.keyword).replace(/^\s+|\s+$/g, "");
        if (!options.isGroup) {
            pageRows = (rows || []).filter(function (row) {
                var text = [textOf(row), options.codeOf ? options.codeOf(row) : "",
                    options.subOf ? options.subOf(row) : ""].join(" ").toLowerCase();
                return !keyword || text.indexOf(keyword.toLowerCase()) >= 0;
            });
        }
        if (options.mode === "tree" && global.OaonesTree) {
            var treeOptions = {
                key: key,
                state: options.state || {},
                idOf: options.idOf,
                parentOf: options.parentOf,
                textOf: textOf,
                isGroup: options.isGroup,
                groupHtml: options.groupHtml,
                optionHtml: optionHtml,
                keyword: keyword,
                emptyText: options.emptyText || "没有匹配的数据",
                emptyClass: "wx-pick-empty"
            };
            return global.OaonesTree.render(pageRows, treeOptions);
        }
        if (!pageRows.length) {
            return "<div class='wx-pick-empty'>" + esc(options.emptyText || "没有匹配的数据") + "</div>";
        }
        var html = "";
        pageRows.forEach(function (row) {
            html += "<div class='wx-pick-row'>" + optionHtml(row, false) + "</div>";
        });
        return html;
    }

    global.OaonesHrmPicker = {
        types: TYPE_DEFAULTS,
        defaults: defaults,
        selectedMap: selectedMap,
        toggle: toggle,
        render: render,
        defaultOptionHtml: defaultOptionHtml
    };
})(window);
