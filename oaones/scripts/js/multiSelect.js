/**
 * OAones 多选标签组件（OaonesMultiSelect）。
 *
 * 用法：
 *   var control = OaonesMultiSelect.create(container, {
 *       items: [{ id: "1", name: "张三" }],
 *       selected: ["1"],
 *       idOf: function (item) { return item.id; },
 *       labelOf: function (item) { return item.name; },
 *       onChange: function (ids, items) { ... }
 *   });
 *   control.setSelected(["2"]);
 *   control.values();
 */
(function (global, document) {
    "use strict";

    var seq = 0;

    function asArray(value) {
        if (value === null || value === undefined) { return []; }
        return Array.isArray(value) ? value.slice(0) : [value];
    }

    function create(container, options) {
        options = options || {};
        var root = typeof container === "string" ? document.getElementById(container) : container;
        if (!root) { return null; }
        var idOf = options.idOf || function (item) { return item.id; };
        var labelOf = options.labelOf || function (item) { return item.name; };
        var items = (options.items || []).slice(0);
        var selected = asArray(options.selected).map(String);
        var inputName = options.name || ("oaMultiSelect" + (++seq));

        function find(id) {
            for (var i = 0; i < items.length; i++) {
                if (String(idOf(items[i])) === String(id)) { return items[i]; }
            }
            return null;
        }

        function selectedItems() {
            return selected.map(find).filter(function (item) { return !!item; });
        }

        function render() {
            var html = "";
            selected.forEach(function (id) {
                var item = find(id);
                if (!item) { return; }
                html += "<span class='oa-chip' data-value='" + escapeAttr(id) + "'>"
                    + "<span class='oa-chip-label'>" + escapeHtml(labelOf(item)) + "</span>"
                    + "<button type='button' class='oa-chip-remove' data-remove='" + escapeAttr(id)
                    + "' aria-label='移除'>&times;</button></span>";
            });
            if (typeof options.input !== "string" || options.input !== "none") {
                html += "<input type='text' class='oa-multi-select-input' autocomplete='off'"
                    + " placeholder='" + escapeAttr(options.placeholder || "选择") + "'>";
            }
            html += "";
            root.classList.add("oa-multi-select");
            root.innerHTML = html;
            for (var i = 0; i < selected.length; i++) {
                var hidden = document.createElement("input");
                hidden.type = "hidden";
                hidden.name = inputName;
                hidden.value = selected[i];
                hidden.setAttribute("data-oa-multi-value", selected[i]);
                root.appendChild(hidden);
            }
        }

        function escapeHtml(value) {
            return String(value == null ? "" : value).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
        }

        function escapeAttr(value) { return escapeHtml(value); }

        function emit() {
            var values = selected.slice(0);
            if (typeof options.onChange === "function") { options.onChange(values, selectedItems()); }
        }

        function setSelected(values) {
            selected = asArray(values).map(String);
            render();
            emit();
        }

        function add(id) {
            var value = String(id);
            if (selected.indexOf(value) < 0) { selected.push(value); render(); emit(); }
        }

        function remove(id) {
            var value = String(id);
            selected = selected.filter(function (one) { return one !== value; });
            render();
            emit();
        }

        function onClick(e) {
            var target = e.target;
            if (!target || !target.closest) { return; }
            var button = target.closest("[data-remove]");
            if (button) { remove(button.getAttribute("data-remove")); }
        }

        root.addEventListener("click", onClick);
        render();

        return {
            values: function () { return selected.slice(0); },
            items: selectedItems,
            setSelected: setSelected,
            add: add,
            remove: remove,
            rerender: render
        };
    }

    global.OaonesMultiSelect = { create: create };
})(window, document);
