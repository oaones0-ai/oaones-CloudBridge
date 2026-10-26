(function () {
    "use strict";

    var CFG = window.OAONES_ORGMAP || {};
    var PLATFORMS = [
        { key: "wecom", type: 0, label: "企业微信" },
        { key: "dingtalk", type: 1, label: "钉钉" },
        { key: "feishu", type: 2, label: "飞书" }
    ];
    var state = {
        apiUrl: CFG.apiUrl || "/oaones/orgMap/api.jsp",
        data: {},
        subs: [],
        mapBySub: {},
        mapByCfg: {},
        keyword: "",
        status: "",
        view: "tree",
        collapsed: {},
        saving: {}
    };

    function byId(id) { return document.getElementById(id); }
    function esc(v) { return Oaones.esc(v); }
    function s(v) { return v === null || v === undefined ? "" : String(v); }
    function subKey(type, subId) { return s(type) + ":" + s(subId); }

    /** OA 分部只要任一平台有映射，即视为“有绑定”。 */
    function hasAnyBinding(subId) {
        for (var i = 0; i < PLATFORMS.length; i++) {
            if (mappingForSub(PLATFORMS[i].type, subId)) { return true; }
        }
        return false;
    }

    function toast(msg, mode) {
        if (msg) { OaonesToast.show(msg, { mode: mode }); }
    }

    function apiFor(platform, action, params, cb) {
        var body = params || {};
        body.platform = platform.key;
        Oaones.apiAt(state.apiUrl, action, body, cb);
    }

    function mappingForSub(type, subId) {
        return state.mapBySub[subKey(type, subId)] || null;
    }

    function mappingForCfg(type, cfgId) {
        return state.mapByCfg[subKey(type, cfgId)] || null;
    }

    function buildSubRows(responses) {
        var subMap = {};
        responses.forEach(function (res) {
            if (!res || res.code !== 0 || !res.data) { return; }
            (res.data.oa_subcompanies || []).forEach(function (row) {
                subMap[s(row.id)] = {
                    id: s(row.id),
                    name: row.name || "",
                    fullname: row.fullname || row.name || ("分部 " + row.id),
                    path: row.path || "",
                    parentId: s(row.parent)
                };
            });
        });
        PLATFORMS.forEach(function (platform) {
            var data = state.data[platform.type];
            if (!data) { return; }
            (data.mappings || []).forEach(function (row) {
                var id = s(row.oa_sub_id);
                if (!subMap[id]) {
                    subMap[id] = {
                        id: id,
                        name: row.oa_sub_name || "",
                        fullname: row.oa_sub_name || ("OA 分部 " + id),
                        path: "",
                        parentId: s(row.oa_sub_parent_id)
                    };
                }
            });
        });
        var rows = Object.keys(subMap).map(function (id) {
            return subMap[id];
        });
        rows.forEach(function (sub) {
            if (!s(sub.path)) { sub.path = deriveSubPath(sub, subMap); }
            if (!s(sub.fullname)) { sub.fullname = s(sub.path) || s(sub.name); }
        });
        return rows;
    }

    /** API 未返回 path 时的兜底：沿父分部链拼出“父 / 子”路径。 */
    function deriveSubPath(sub, subMap) {
        var names = [];
        var seen = {};
        var current = sub;
        while (current) {
            var id = s(current.id);
            if (!id || seen[id] || names.length >= 100) { break; }
            seen[id] = true;
            names.push(s(current.name) || s(current.fullname) || ("分部 " + id));
            var parentId = s(current.parentId);
            if (!parentId || parentId === "0" || parentId === id) { break; }
            current = subMap[parentId] || null;
        }
        return names.reverse().join(" / ");
    }

    function compareSubs(a, b) {
        return s(a.path).localeCompare(s(b.path), "zh-Hans-CN")
            || s(a.name).localeCompare(s(b.name), "zh-Hans-CN");
    }

    /**
     * 以 parentId 建立分部树。父分部存在时，子分部只挂到父节点下；
     * 上级不在当前授权候选中的分部作为根展示，完整路径仍由后端 path 提供。
     */
    function buildSubTree() {
        var byId = {};
        var children = {};
        var roots = [];
        state.subs.forEach(function (sub) {
            byId[sub.id] = sub;
            children[sub.id] = [];
        });
        state.subs.forEach(function (sub) {
            var parentId = s(sub.parentId);
            if (parentId && parentId !== "0" && parentId !== sub.id && byId[parentId]) {
                children[parentId].push(sub);
            } else {
                roots.push(sub);
            }
        });
        roots.sort(compareSubs);
        Object.keys(children).forEach(function (id) {
            children[id].sort(compareSubs);
        });
        var model = { byId: byId, children: children, roots: roots };

        // 防御异常环数据：未被正常根节点遍历到的分部仍以根节点展示，渲染时会去重防循环。
        var visited = {};
        function mark(sub) {
            if (!sub || visited[sub.id]) { return; }
            visited[sub.id] = true;
            (children[sub.id] || []).forEach(mark);
        }
        roots.forEach(mark);
        state.subs.filter(function (sub) { return !visited[sub.id]; })
            .sort(compareSubs)
            .forEach(function (sub) { roots.push(sub); });
        return model;
    }

    /**
     * 关键字与绑定状态按 AND 组合。
     * 列表只保留真正命中的分部；树状额外保留全部上级节点作为层级路径。
     */
    function visibleSubSet(model, keyword, status) {
        var visible = {};
        var forceOpen = {};
        if (!keyword && !status) {
            state.subs.forEach(function (sub) { visible[sub.id] = true; });
            return { visible: visible, forceOpen: forceOpen };
        }
        state.subs.forEach(function (sub) {
            if (subSearchText(sub).indexOf(keyword) < 0) { return; }
            var bound = hasAnyBinding(sub.id);
            if ((status === "bound" && !bound) || (status === "unbound" && bound)) { return; }
            visible[sub.id] = true;
            forceOpen[sub.id] = true;
            if (state.view !== "tree") { return; }
            var parentId = s(sub.parentId);
            var current = parentId && parentId !== "0" && parentId !== sub.id
                ? model.byId[parentId] : null;
            var guard = 0;
            while (current && guard++ < 100) {
                visible[current.id] = true;
                forceOpen[current.id] = true;
                parentId = s(current.parentId);
                current = parentId && parentId !== "0" && parentId !== current.id
                    ? model.byId[parentId] : null;
            }
        });
        return { visible: visible, forceOpen: forceOpen };
    }

    /**
     * 列表与树状共用同一份深度优先顺序：
     * 父分部 → 该父分部的子分部 → 下一个同级分部，确保同父子分部集中且紧跟父分部。
     */
    function orderedSubRows(model, filter) {
        var rows = [];
        var traversed = {};
        function walk(sub, depth) {
            var id = s(sub.id);
            if (traversed[id]) { return; }
            traversed[id] = true;
            var childRows = model.children[id] || [];
            if (filter.visible[id]) {
                var visibleChildren = childRows.filter(function (child) {
                    return !!filter.visible[child.id];
                });
                var collapsed = state.view === "tree" && !!state.collapsed[id]
                    && !filter.forceOpen[id];
                rows.push({
                    sub: sub,
                    depth: depth,
                    hasChildren: visibleChildren.length > 0,
                    collapsed: collapsed
                });
                if (collapsed) { return; }
            }
            // 列表过滤掉上级时仍需穿过隐藏节点，才能按层级顺序找到命中的下级。
            childRows.forEach(function (child) { walk(child, depth + 1); });
        }
        model.roots.forEach(function (sub) { walk(sub, 0); });
        return rows;
    }

    function rebuildIndexes() {
        state.mapBySub = {};
        state.mapByCfg = {};
        PLATFORMS.forEach(function (platform) {
            var data = state.data[platform.type];
            (data && data.mappings ? data.mappings : []).forEach(function (row) {
                state.mapBySub[subKey(platform.type, row.oa_sub_id)] = row;
                state.mapByCfg[subKey(platform.type, row.cfg_id)] = row;
            });
        });
    }

    function loadAllData() {
        byId("orgMapBody").innerHTML = "<tr><td colspan='4' class='orgmap-loading'>正在读取三个平台的分部映射...</td></tr>";
        byId("orgMapEmpty").style.display = "none";
        var responses = [];
        var pending = PLATFORMS.length;
        var failed = 0;
        PLATFORMS.forEach(function (platform) {
            apiFor(platform, "orgmap.list", {}, function (res) {
                responses.push(res);
                if (!res || res.code !== 0) { failed++; }
                pending--;
                if (pending > 0) { return; }
                if (failed === PLATFORMS.length) {
                    byId("orgMapBody").innerHTML = "";
                    byId("orgMapEmpty").textContent = "读取失败，请稍后刷新";
                    byId("orgMapEmpty").style.display = "block";
                    toast("读取分部映射失败", 2);
                    return;
                }
                if (failed > 0) { toast("部分平台映射读取失败，请稍后刷新", 1); }
                state.data = {};
                responses.forEach(function (item) {
                    if (item && item.code === 0 && item.data) {
                        state.data[item.data.type] = item.data;
                    }
                });
                rebuildIndexes();
                state.subs = buildSubRows(responses);
                renderStats();
                renderTable();
            });
        });
    }

    function renderStats() {
        var mappingCount = 0;
        var tenantCount = 0;
        PLATFORMS.forEach(function (platform) {
            var data = state.data[platform.type];
            mappingCount += data && data.mappings ? data.mappings.length : 0;
            tenantCount += data && data.tenants ? data.tenants.length : 0;
        });
        var totalSlots = state.subs.length * PLATFORMS.length;
        var coverage = totalSlots ? Math.round(mappingCount * 100 / totalSlots) : 0;
        byId("orgMapStats").innerHTML =
            "<span class='bind-stat'><b>" + esc(state.subs.length) + "</b>OA 分部</span>" +
            "<span class='bind-stat'><b>" + esc(mappingCount) + "</b>三平台分部映射</span>" +
            "<span class='bind-stat'><b>" + esc(tenantCount) + "</b>可维护租户</span>" +
            "<span class='bind-stat'><b>" + esc(coverage) + "%</b>映射覆盖</span>";
    }

    function subSearchText(sub) {
        var text = [sub.path, sub.fullname, sub.name, sub.id, "分部"].join(" ");
        PLATFORMS.forEach(function (platform) {
            var mapping = mappingForSub(platform.type, sub.id);
            if (mapping) {
                text += " " + [mapping.cfg_name, mapping.cfg_corp_id, mapping.cfg_id, mapping.remark].join(" ");
            }
        });
        return text.toLowerCase();
    }

    function platformCellHtml(platform, sub) {
        var mapping = mappingForSub(platform.type, sub.id);
        var data = state.data[platform.type];
        var tenants = data && data.tenants ? data.tenants : [];
        var options = "<option value=''>"
            + (tenants.length ? "请选择租户" : "当前无可维护租户")
            + "</option>";
        tenants.forEach(function (tenant) {
            var available = tenantAvailable(platform, tenant.id, sub.id);
            var selected = mapping && s(mapping.cfg_id) === s(tenant.id);
            options += "<option value='" + esc(tenant.id) + "'"
                + (available ? "" : " disabled")
                + (selected ? " selected" : "") + ">"
                + esc(tenant.corp_name || ("租户 #" + tenant.id))
                + (tenant.corp_id ? "（" + esc(tenant.corp_id) + "）" : "")
                + (s(tenant.sync_enable) === "0" ? "（已停用）" : "")
                + (available ? "" : " · 已被其他 OA 分部占用")
                + "</option>";
        });
        var selectTitle = mapping ? "点击更换租户" : "点击选择租户";
        var html = "<div class='orgmap-inline-map'>"
            + "<select class='orgmap-tenant-select' data-orgmap-select='1'"
            + " data-platform='" + platform.key + "' data-sub-id='" + esc(sub.id) + "'"
            + " title='" + selectTitle + "' aria-label='" + esc(platform.label + "租户") + "'"
            + (tenants.length ? "" : " disabled") + ">"
            + options + "</select>";
        if (mapping) {
            html += "<button type='button' class='btn small secondary orgmap-unbind-btn'"
                + " data-orgmap-action='delete' data-platform='" + platform.key + "'"
                + " data-id='" + esc(mapping.id) + "'>解绑</button>";
        }
        return html + "</div>";
    }

    function subCellHtml(item) {
        var sub = item.sub;
        var name = s(sub.name) || s(sub.fullname) || ("OA 分部 " + sub.id);
        var path = s(sub.path) || s(sub.fullname) || name;
        var html = "";
        if (state.view === "tree") {
            var indent = item.depth * 18;
            var toggle = item.hasChildren
                ? "<button type='button' class='orgmap-tree-toggle' data-orgmap-action='toggle'"
                    + " data-sub-id='" + esc(sub.id) + "' aria-expanded='" + (!item.collapsed) + "'"
                    + " title='" + (item.collapsed ? "展开下级分部" : "收起下级分部") + "'>"
                    + (item.collapsed ? "▸" : "▾") + "</button>"
                : "<span class='orgmap-tree-toggle leaf' aria-hidden='true'></span>";
            html += "<div class='orgmap-tree-line' style='padding-left:" + indent + "px'>"
                + toggle
                + "<div class='orgmap-org-name'>" + OaonesHrm.sub(sub.id, name) + "</div>"
                + "</div>";
        } else {
            html += "<div class='orgmap-org-name'>" + OaonesHrm.sub(sub.id, name) + "</div>"
                + "<div class='orgmap-org-path' title='完整路径：" + esc(path) + "'>"
                + esc(path) + "</div>";
        }
        return html
            + "<div class='orgmap-org-meta'>"
            + "<span class='orgmap-org-id'>ID " + esc(sub.id) + "</span></div>";
    }

    function renderTable() {
        var k = s(state.keyword).trim().toLowerCase();
        var model = buildSubTree();
        var filter = visibleSubSet(model, k, state.status);
        var rows = orderedSubRows(model, filter);
        var body = byId("orgMapBody");
        var empty = byId("orgMapEmpty");
        if (!rows.length) {
            body.innerHTML = "";
            empty.textContent = k ? "没有符合搜索条件的 OA 分部" : "暂无可展示的 OA 分部";
            empty.style.display = "block";
            return;
        }
        empty.style.display = "none";
        var html = "";
        rows.forEach(function (item) {
            var sub = item.sub;
            html += "<tr class='orgmap-data-row " + (state.view === "tree" ? "is-tree" : "is-list")
                + "'><td class='orgmap-org-cell'>" + subCellHtml(item) + "</td>";
            PLATFORMS.forEach(function (platform) {
                html += "<td class='orgmap-platform-cell'>" + platformCellHtml(platform, sub) + "</td>";
            });
            html += "</tr>";
        });
        body.innerHTML = html;
    }

    function tenantAvailable(platform, cfgId, subId) {
        var mapping = mappingForCfg(platform.type, cfgId);
        return !mapping || s(mapping.oa_sub_id) === s(subId);
    }

    /** 行内租户选择框：选中即保存；清空选择不视为解绑，解绑仍走单独按钮。 */
    function saveInlineMapping(select) {
        var platform = null;
        var platformKey = select.getAttribute("data-platform");
        for (var i = 0; i < PLATFORMS.length; i++) {
            if (PLATFORMS[i].key === platformKey) { platform = PLATFORMS[i]; }
        }
        var subId = s(select.getAttribute("data-sub-id"));
        if (!platform || !subId) { return; }
        var mapping = mappingForSub(platform.type, subId);
        var cfgId = s(select.value);
        if (!cfgId) {
            select.value = mapping ? s(mapping.cfg_id) : "";
            if (mapping) { toast("如需解除映射，请点击“解绑”", 1); }
            return;
        }
        if (mapping && s(mapping.cfg_id) === cfgId) { return; }
        var savingKey = subKey(platform.type, subId);
        if (state.saving[savingKey]) { return; }
        state.saving[savingKey] = true;
        select.disabled = true;
        apiFor(platform, "orgmap.save", {
            cfg_id: cfgId,
            oa_sub_id: subId,
            remark: mapping ? s(mapping.remark) : ""
        }, function (res) {
            state.saving[savingKey] = false;
            if (!res || res.code !== 0) {
                select.disabled = false;
                select.value = mapping ? s(mapping.cfg_id) : "";
                toast((res && res.msg) || "保存映射失败", 2);
                loadAllData();
                return;
            }
            toast(mapping ? "分部映射已更换" : "分部映射已保存", 0);
            loadAllData();
        });
    }

    function deleteMapping(platformKey, mappingId) {
        var platform = null;
        for (var i = 0; i < PLATFORMS.length; i++) {
            if (PLATFORMS[i].key === platformKey) { platform = PLATFORMS[i]; }
        }
        if (!platform) { return; }
        var mapping = mappingForCfgByMappingId(platform.type, mappingId);
        if (!mapping) { return; }
        OaonesToast.confirm("确认解绑 OA 分部“" + (mapping.oa_sub_name || mapping.oa_sub_id)
            + "”与" + platform.label + "租户“" + (mapping.cfg_name || ("#" + mapping.cfg_id))
            + "”的映射？", function () {
            apiFor(platform, "orgmap.delete", { id: mappingId }, function (res) {
                if (!res || res.code !== 0) {
                    toast((res && res.msg) || "解绑失败", 2);
                    return;
                }
                toast("已解绑", 0);
                loadAllData();
            });
        });
    }

    function mappingForCfgByMappingId(type, mappingId) {
        var data = state.data[type];
        var rows = data && data.mappings ? data.mappings : [];
        for (var i = 0; i < rows.length; i++) {
            if (s(rows[i].id) === s(mappingId)) { return rows[i]; }
        }
        return null;
    }

    function setSubView(view) {
        state.view = view === "tree" ? "tree" : "list";
        var buttons = document.querySelectorAll(".orgmap-view-btn");
        for (var i = 0; i < buttons.length; i++) {
            var active = buttons[i].getAttribute("data-orgmap-view") === state.view;
            if (active) {
                buttons[i].classList.add("active");
            } else {
                buttons[i].classList.remove("active");
            }
            buttons[i].setAttribute("aria-pressed", active ? "true" : "false");
        }
        renderTable();
    }

    function bindEvents() {
        byId("orgMapRefreshBtn").addEventListener("click", loadAllData);
        byId("orgMapKeyword").addEventListener("input", function () {
            state.keyword = this.value;
            renderTable();
        });
        byId("orgMapStatus").addEventListener("change", function () {
            state.status = this.value;
            renderTable();
        });
        var viewButtons = document.querySelectorAll(".orgmap-view-btn");
        for (var i = 0; i < viewButtons.length; i++) {
            viewButtons[i].addEventListener("click", function () {
                setSubView(this.getAttribute("data-orgmap-view"));
            });
        }
        byId("orgMapBody").addEventListener("click", function (e) {
            var button = e.target.closest("[data-orgmap-action]");
            if (!button) { return; }
            var action = button.getAttribute("data-orgmap-action");
            if (action === "toggle") {
                var subId = s(button.getAttribute("data-sub-id"));
                if (state.collapsed[subId]) {
                    delete state.collapsed[subId];
                } else {
                    state.collapsed[subId] = true;
                }
                renderTable();
            } else if (action === "delete") {
                deleteMapping(button.getAttribute("data-platform"), button.getAttribute("data-id"));
            }
        });
        byId("orgMapBody").addEventListener("change", function (e) {
            var select = e.target.closest("[data-orgmap-select]");
            if (select) {
                saveInlineMapping(select);
            }
        });
    }

    bindEvents();
    loadAllData();
})();
