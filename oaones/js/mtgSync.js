(function () {
    "use strict";
    /**
     * 会议同步 / 日程同步 页面脚本（两个一级菜单共用，见 oaones/meetingSync/meetingSync.jsp）。
     *
     * 业务类型来自 window.OAONES_MTG_BIZ（0 会议 / 1 日程）、平台来自 window.OAONES_MTG_TYPE
     * （0 企微 / 1 钉钉 / 2 飞书）、接口入口来自 window.OAONES_API_URL（各平台 api.jsp）。
     *
     * 四个页签（顺序：同步设置 → 会议 / 日程同步清单 → 同步记录 → 个人同步方向）：
     *   同步设置：mtgsync.list / mtgsync.state / mtgsync.app / mtgsync.plan —— 一行一个租户，
     *             有“同步开关 + 绑定应用（应用要有日程权限）+ 同步方向”；
     *             不设“同步范围 / 最近同步 / 操作”列，也没有手动同步按钮；
     *             开启同步后由内置调度器轮询（周期最长不超过 3 分钟），有新会议 / 日程就自动推送。
     *             最上面一行是「计划任务」：① 按时间段（明细表，自由配多个时段 + 每段一个轮询间隔，
     *             按工作日 / 非工作日区分，工作日口径取泛微标准考勤）；② 高级模式（两档 cron + 执行日）。
     *   同步清单：一行一个 OA 会议 / 日程（mtgsync.items），行尾「重推」= mtgsync.retry。
     *   同步记录：mtgsync.logs（分页走 js/table.js）。
     *   个人同步方向：非“遵循系统设置”的人（mtgsync.dirUserList），只读；按租户 + 分部 / 部门
     *             （多选弹框，选中含下级；候选按 OA 分权过滤，见 mtgsync.orgOptions）+ 人员 + 同步方向筛选。
     *
     * **点“状态”（新增 / 更新 / 跳过 / 失败）= mtgsync.payload**：弹出这条会议 / 日程实际下发给
     * 第三方平台的接口报文 + 逐字段说明（只组装不下发）；「同步清单」与「同步记录」两个页签的状态标签
     * 都是这个入口。
     */
    var BIZ = parseInt(window.OAONES_MTG_BIZ || "0", 10) || 0;
    var TYPE = parseInt(window.OAONES_MTG_TYPE || "0", 10) || 0;
    var LABEL = window.OAONES_MTG_LABEL || "会议";
    var API = window.OAONES_API_URL || "/oaones/WeCom/api.jsp";

    var state = { rows: [], keyword: "", logs: [], logLoaded: false, logPager: null,
        items: [], itemsLoaded: false, itemPager: null,
        /** 「个人同步方向」页签：列表 / 分页器 / 是否已加载 */
        dirUsers: [], dirUserPager: null, dirUsersLoaded: false,
        /** 「个人同步方向」页签的分部 / 部门候选（按登录人的 OA 分权过滤）与已选 ID */
        dirSubs: [], dirDepts: [], dirOrgLoaded: false,
        dirSubIds: [], dirDeptIds: [],
        /** 弹框打开前的选择快照（点“取消 / × / 遮罩”时回退） */
        dirSubBackup: null, dirDeptBackup: null,
        /** 计划任务的默认两档 cron（后端按 oaones.properties 折算，供「恢复默认」用）。 */
        planDefaults: null,
        /** 计划任务「工作日口径」的考勤组候选（后端读泛微标准考勤 kq_group 下发）。 */
        planGroups: [],
        /** 「报文」弹框当前展示的接口报文（复制用），null = 未打开。 */
        payload: null };

    function byId(id) { return document.getElementById(id); }
    function esc(v) { return window.Oaones ? Oaones.esc(v) : String(v == null ? "" : v); }
    function toast(msg, mode, duration) {
        if (window.OaonesToast) { window.OaonesToast.show(msg, { mode: mode || 0, duration: duration }); }
    }
    function value(id) { var el = byId(id); return el ? String(el.value || "").trim() : ""; }
    function api(action, params, cb) { Oaones.apiAt(API, action, params, cb); }

    /** 同步方向文案：两个方向可双选（与后端 OaonesConst#mtgDirText 口径一致） */
    function dirText(oa2, t2oa) {
        if (oa2 && t2oa) { return "双向（OA→第三方 / 第三方→OA）"; }
        if (oa2) { return "仅 OA→第三方"; }
        if (t2oa) { return "仅 第三方→OA"; }
        return "未选择同步方向";
    }

    /* ---------------- 同步设置 ---------------- */
    function loadSettings() {
        api("mtgsync.list", { biz: BIZ, type: TYPE }, function (res) {
            if (!res || res.code !== 0) { toast((res && res.msg) || "读取同步设置失败", 2); return; }
            state.rows = res.data || [];
            fillCfgSelect("mtgLogCfg");
            fillCfgSelect("mtgItemCfg");
            fillCfgSelect("mtgDirUserCfg");
            renderSettings();
            renderSchedulerTip();
            loadPlan();
        });
    }

    function visibleRows() {
        var k = (state.keyword || "").toLowerCase();
        if (!k) { return state.rows; }
        return state.rows.filter(function (r) {
            return [r.cfg_id, r.cfg_name, r.corp_id].join(" ").toLowerCase().indexOf(k) >= 0;
        });
    }

    function renderSettings() {
        var body = byId("mtgSettingBody");
        if (!body) { return; }
        var list = visibleRows();
        var html = "";
        list.forEach(function (r) {
            var enabled = String(r.enabled) !== "0";
            var tenantTag = String(r.sync_enable) !== "0"
                ? "<span class='tag bound'>启用</span>" : "<span class='tag off'>停用</span>";
            var switchTag = enabled ? "<span class='tag bound'>已开启</span>" : "<span class='tag off'>已关闭</span>";
            var switchBtn = "<button class='link' data-mtg-state=\"" + esc(r.cfg_id) + "\" data-mtg-enable=\""
                + (enabled ? "0" : "1") + "\">" + (enabled ? "关闭" : "开启") + "</button>";
            // 同步方向：两个复选框（可双选，默认双选），勾选即保存（取消最后一个不让保存，回退）
            var oa2 = String(r.dir_oa2third) !== "0";
            var t2oa = String(r.dir_third2oa) !== "0";
            var dirCell = "<label class='mtg-dir-opt'><input type='checkbox' data-mtg-dir='oa2' data-cfg=\""
                + esc(r.cfg_id) + "\"" + (oa2 ? " checked" : "") + "> OA→第三方</label>"
                + "<label class='mtg-dir-opt'><input type='checkbox' data-mtg-dir='t2oa' data-cfg=\""
                + esc(r.cfg_id) + "\"" + (t2oa ? " checked" : "") + "> 第三方→OA</label>"
                + "<button type='button' class='link' data-mtg-pull=\"" + esc(r.cfg_id)
                + "\" title='立即从第三方平台拉取一轮（第三方→OA）：平台新建的在 OA 新建、平台改动更新 OA'>拉取</button>";
            html += "<tr>"
                + "<td>" + esc(r.cfg_id) + "</td>"
                + "<td>" + esc(r.cfg_name) + "</td>"
                + "<td>" + esc(r.corp_id || "-") + "</td>"
                + "<td>" + tenantTag + "</td>"
                + "<td>" + switchTag + " " + switchBtn + "</td>"
                + "<td>" + appCell(r) + "</td>"
                + "<td>" + dirCell + "</td>"
                + "</tr>";
        });
        body.innerHTML = html;
        var empty = byId("mtgSettingEmpty");
        empty.style.display = list.length ? "none" : "";
        empty.textContent = state.rows.length ? "没有匹配的租户"
            : "暂无租户。请先在「企微租户设置 / 钉钉租户设置 / 飞书租户设置」里新增租户。";
        if (window.OaonesTable) { OaonesTable.resizable(byId("mtgSettingTable"), { key: "mtgSetting" }); }
    }

    function rowByCfgId(cfgId) {
        for (var i = 0; i < state.rows.length; i++) {
            if (String(state.rows[i].cfg_id) === String(cfgId)) { return state.rows[i]; }
        }
        return null;
    }

    /* ---------------- 绑定应用（逐租户选一个“有日程权限”的应用） ---------------- */
    /**
     * 「绑定应用」列：该租户下的应用绑定下拉 + 「校验」（用该应用凭据真调一次平台接口）。
     * 会议 / 日程都是推第三方平台的**日程接口**，所以选的应用必须有**日程权限**
     * （应用自己的 Secret 优先，没单独配 Secret 时由租户配置的 Secret 兜底）。
     */
    function appCell(r) {
        var apps = r.apps || [];
        var cur = r.app_id === undefined || r.app_id === null ? "" : String(r.app_id);
        if (!apps.length && (cur === "" || cur === "0")) {
            return "<span class='notice'>该租户还没绑应用——请先到「应用绑定」新增（应用要有日程权限）</span>";
        }
        var html = "<select class='mtg-app-select' data-mtg-app=\"" + esc(r.cfg_id)
            + "\" title='选该租户同步时调平台日程接口用的应用（要有日程权限）'>"
            + "<option value='0'" + (cur === "" || cur === "0" ? " selected" : "")
            + ">未指定（用租户配置的 Secret）</option>";
        var matched = false;
        apps.forEach(function (a) {
            var id = String(a.id);
            if (cur === id) { matched = true; }
            var label = (a.app_name || ("#" + id)) + "（AgentId " + (a.agent_id || "-") + "）";
            if (a.enable === false || String(a.enable) === "false") { label += "｜已停用"; }
            if (String(a.secret_ready) === "0") { label += "｜缺 Secret"; }
            html += "<option value=\"" + esc(id) + "\"" + (cur === id ? " selected" : "") + ">"
                + esc(label) + "</option>";
        });
        // 保存过、但应用已被删除（选项里没有）：保留一个“已删除”的选中项，让下拉如实反映库里的值
        if (cur !== "" && cur !== "0" && !matched) {
            html += "<option value=\"" + esc(cur) + "\" selected>（原应用 #" + esc(cur)
                + " 已删除）</option>";
        }
        html += "</select> "
            + "<button type='button' class='link' data-mtg-appcheck=\"" + esc(r.cfg_id)
            + "\" title='用该应用的凭据调一次平台接口：验证企业标识 / Secret / AgentId，并试读一条日程看日程权限'>校验</button>";
        if (String(r.app_missing) === "1") {
            html += "<div class='notice' style='color:#a8534f;'>原选择的应用已不存在，请重新选择</div>";
        } else if (cur !== "" && cur !== "0" && r.app_agent_id) {
            html += "<div class='notice'>当前：AgentId " + esc(r.app_agent_id) + "</div>";
        }
        return html;
    }

    /** 保存某租户的「绑定应用」（选完即保存；0 = 未指定，回退租户配置的 Secret）。 */
    function saveApp(cfgId, appId) {
        var row = rowByCfgId(cfgId);
        api("mtgsync.app", { biz: BIZ, type: TYPE, cfg_id: cfgId, app_id: appId }, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) || "保存绑定应用失败", 2);
                loadSettings();
                return;
            }
            toast("已把「" + (row ? row.cfg_name : cfgId) + "」的" + LABEL + "同步绑定应用设为："
                + appText(row, appId), 0);
            // 重载一次：把“当前 AgentId / 应用已删除”等展示信息刷新成最新
            loadSettings();
        });
    }

    /** 下拉里某个应用值的可读文案（用于保存后的提示）。 */
    function appText(row, appId) {
        var id = String(appId);
        if (id === "0" || id === "") { return "未指定（用租户配置的 Secret）"; }
        var apps = (row && row.apps) || [];
        for (var i = 0; i < apps.length; i++) {
            if (String(apps[i].id) === id) {
                return apps[i].app_name + "（AgentId " + (apps[i].agent_id || "-") + "）";
            }
        }
        return "#" + id;
    }

    /** 「校验」：用该应用（或租户配置）的凭据调平台接口，验证 Secret 与日程权限。 */
    function checkApp(cfgId) {
        var row = rowByCfgId(cfgId);
        var appId = row ? row.app_id : 0;
        if (window.OaonesToast) { OaonesToast.loading("正在校验应用凭据与日程权限，请不要关闭页面…"); }
        api("mtgsync.appCheck", { biz: BIZ, type: TYPE, cfg_id: cfgId, app_id: appId }, function (res) {
            if (window.OaonesToast) { OaonesToast.hideLoading(); }
            if (!res || res.code !== 0) {
                toast((res && res.msg) || "校验失败", 2, 12000);
                return;
            }
            var d = res.data || {};
            toast((d.success ? "「" + (row ? row.cfg_name : cfgId) + "」校验通过：" : "校验未通过：")
                + (d.message || ""), d.success ? 0 : 2, d.success ? 8000 : 15000);
        });
    }

    /** 每个租户单独开启 / 关闭（只改开关，标的是“这个租户要不要同步”） */
    function toggle(cfgId, enable) {
        var row = rowByCfgId(cfgId);
        api("mtgsync.state", { biz: BIZ, type: TYPE, cfg_id: cfgId, enabled: enable }, function (res) {
            if (!res || res.code !== 0) { toast((res && res.msg) || "保存失败", 2); loadSettings(); return; }
            toast("已" + (enable === 1 ? "开启" : "关闭") + "「" + (row ? row.cfg_name : cfgId)
                + "」的" + LABEL + "同步" + (enable === 1 ? "，有新" + LABEL + "会自动同步" : ""), 0);
            loadSettings();
        });
    }

    /**
     * 保存某个租户的同步方向（勾选即保存）：两个复选框可双选，至少要勾选一个；
     * 取消最后一个勾选时不提交，并把本次这一下回退。
     */
    function saveDirection(cfgId, tr, changed) {
        var oa2El = tr ? tr.querySelector("input[data-mtg-dir='oa2']") : null;
        var t2oaEl = tr ? tr.querySelector("input[data-mtg-dir='t2oa']") : null;
        var oa2 = oa2El && oa2El.checked ? 1 : 0;
        var t2oa = t2oaEl && t2oaEl.checked ? 1 : 0;
        if (oa2 === 0 && t2oa === 0) {
            toast("同步方向至少要勾选一个（OA 同步到第三方 / 第三方同步到 OA）", 1, 6000);
            if (changed) { changed.checked = true; }
            return;
        }
        var row = rowByCfgId(cfgId);
        api("mtgsync.direction",
            { biz: BIZ, type: TYPE, cfg_id: cfgId, dir_oa2third: oa2, dir_third2oa: t2oa },
            function (res) {
                if (!res || res.code !== 0) { toast((res && res.msg) || "保存同步方向失败", 2); loadSettings(); return; }
                if (row) { row.dir_oa2third = String(oa2); row.dir_third2oa = String(t2oa); }
                toast("已保存「" + (row ? row.cfg_name : cfgId) + "」的" + LABEL + "同步方向："
                    + dirText(oa2 === 1, t2oa === 1), 0);
            });
    }

    /* ==================================================================
     * 个人同步方向（独立页签：非“遵循系统设置”的人，只读）
     *   位置在「同步记录」后面（页壳的二级页签条上）。
     *   筛选：租户 + 分部 / 部门（多选弹框，选中自动含其下级；候选按登录人的 OA 分权过滤）
     *         + 人员（姓名 / 登录名）+ 同步方向。
     *   “含其下级”的展开在服务端做（MtgSyncServiceImpl#subtreeIds），页面只传选中的 ID。
     * ================================================================== */
    function loadDirUsers() {
        state.dirUsersLoaded = true;
        api("mtgsync.dirUserList", {
            biz: BIZ, type: TYPE,
            cfg_id: value("mtgDirUserCfg"),
            keyword: value("mtgDirUserKeyword"),
            direction: value("mtgDirDirection"),
            sub_ids: state.dirSubIds.join(","),
            dept_ids: state.dirDeptIds.join(","),
            limit: 2000
        }, function (res) {
            if (!res || res.code !== 0) { toast((res && res.msg) || "读取个人同步方向失败", 2); return; }
            state.dirUsers = (res.data && res.data.rows) || [];
            renderDirUsers();
        });
    }

    function renderDirUsers() {
        var body = byId("mtgDirUserBody");
        if (!body) { return; }
        if (!state.dirUserPager) {
            state.dirUserPager = OaonesTable.pager({
                key: "mtgDirUser", el: "mtgDirUserPager", onChange: function () { renderDirUsers(); }
            });
        }
        state.dirUserPager.resetIfChanged(
            [value("mtgDirUserCfg"), value("mtgDirUserKeyword"), value("mtgDirDirection"),
                state.dirSubIds.join(","), state.dirDeptIds.join(",")].join("|"));
        var pageRows = state.dirUserPager.apply(state.dirUsers);
        var empty = byId("mtgDirUserEmpty");
        if (!state.dirUsers.length) {
            body.innerHTML = "";
            if (empty) { empty.style.display = ""; }
            state.dirUserPager.render();
            return;
        }
        if (empty) { empty.style.display = "none"; }
        var html = "";
        pageRows.forEach(function (r) {
            // 人员 / 分部 / 部门都走泛微标准页面超链接（见 skill 第一节第 9 条）
            var person = (window.OaonesHrm && typeof OaonesHrm.user === "function")
                ? OaonesHrm.user(r.oa_user_id, r.oa_user_name || ("#" + r.oa_user_id))
                : esc(r.oa_user_name || r.oa_user_id);
            var sub = (window.OaonesHrm && typeof OaonesHrm.sub === "function")
                ? OaonesHrm.sub(r.oa_sub_id, r.oa_sub_name || "-") : esc(r.oa_sub_name || "-");
            var dept = (window.OaonesHrm && typeof OaonesHrm.dept === "function")
                ? OaonesHrm.dept(r.oa_dept_id, r.oa_dept_name || "-") : esc(r.oa_dept_name || "-");
            html += "<tr>"
                + "<td><b>" + person + "</b>"
                + (r.oa_user_loginid ? "<div class='user-mini'>" + esc(r.oa_user_loginid) + "</div>" : "")
                + "</td>"
                + "<td>" + sub + "</td>"
                + "<td>" + dept + "</td>"
                + "<td>" + esc(r.cfg_name || ("#" + r.cfg_id)) + "</td>"
                + "<td>" + esc(r.dir_text || "-") + "</td>"
                + "<td>" + esc(r.update_time || "-") + "</td>"
                + "</tr>";
        });
        body.innerHTML = html;
        if (window.OaonesTable) { OaonesTable.resizable(byId("mtgDirUserTable"), { key: "mtgDirUser" }); }
        state.dirUserPager.render();
    }

    /* ---------------- 「个人同步方向」页签的分部 / 部门筛选（多选弹框，候选按 OA 分权过滤） ---------------- */

    /**
     * 取分部 / 部门候选。**候选按登录人的 OA 分权过滤**（OA 开了分权时只返回其范围内的组织），
     * 由服务端 mtgsync.orgOptions（OaServiceImpl#listSubCompaniesFromCache / #listDepartmentsFromCache
     * → common.OaonesHrmScope）保证；页面只取一次，之后复用。
     */
    function loadDirOrgOptions(cb) {
        if (state.dirOrgLoaded) { if (cb) { cb(true); } return; }
        api("mtgsync.orgOptions", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) || "读取分部 / 部门候选失败", 2);
                if (cb) { cb(false); }
                return;
            }
            var data = res.data || {};
            state.dirSubs = data.oa_subcompanies || [];
            state.dirDepts = data.oa_departments || [];
            state.dirOrgLoaded = true;
            if (cb) { cb(true); }
        });
    }

    function childMapOf(rows, parentKey) {
        var map = {};
        (rows || []).forEach(function (r) {
            var p = String(r[parentKey] == null ? "" : r[parentKey]);
            if (!map[p]) { map[p] = []; }
            map[p].push(String(r.id));
        });
        return map;
    }

    function descendantsOf(id, map) {
        var out = [];
        var seen = {};
        var stack = [String(id)];
        while (stack.length) {
            var cur = stack.pop();
            if (seen[cur]) { continue; }
            seen[cur] = true;
            out.push(cur);
            (map[cur] || []).forEach(function (c) { stack.push(c); });
        }
        return out;
    }

    function expandIds(ids, map) {
        var out = [];
        var seen = {};
        (ids || []).forEach(function (id) {
            descendantsOf(id, map).forEach(function (v) {
                if (!seen[v]) { seen[v] = true; out.push(v); }
            });
        });
        return out;
    }

    function uniqueIds(ids) {
        var seen = {};
        var out = [];
        (ids || []).forEach(function (id) {
            var v = String(id);
            if (!seen[v]) { seen[v] = true; out.push(v); }
        });
        return out;
    }

    function rowText(row, keys) {
        return keys.map(function (key) { return String(row[key] == null ? "" : row[key]); }).join(" ");
    }

    function filterRows(rows, keyword, keys) {
        var k = (keyword || "").trim().toLowerCase();
        if (!k) { return rows; }
        return rows.filter(function (r) { return rowText(r, keys).toLowerCase().indexOf(k) >= 0; });
    }

    /** 候选行 HTML（两列：ID + 名称），列表式与树状共用 */
    function optionHtml(id, name, active, partial, tip) {
        var cls = "wx-pick-option" + (active ? " active" : (partial ? " partial" : ""));
        return "<button type='button' class='" + cls + "' data-id='" + esc(id) + "'"
            + " title='" + esc(tip || "") + "'>"
            + "<span>" + esc(id) + "</span>"
            + "<span class='wx-pick-opt-name'>" + esc(name) + "</span>"
            + "</button>";
    }

    function listHeadHtml(first, second) {
        return "<div class='wx-pick-head'><span>" + esc(first) + "</span><span>"
            + esc(second) + "</span></div>";
    }

    function showModal(maskId, modalId) {
        byId(maskId).style.display = "block";
        byId(modalId).style.display = "block";
    }

    function hideModal(maskId, modalId) {
        byId(maskId).style.display = "none";
        byId(modalId).style.display = "none";
    }

    /* ---- OA 分部筛选（多选，选中自动含其下级分部） ---- */
    function subChildMap() { return childMapOf(state.dirSubs, "parent"); }

    function subLabel(id) {
        var key = String(id == null ? "" : id);
        for (var i = 0; i < state.dirSubs.length; i++) {
            if (String(state.dirSubs[i].id) === key) {
                return state.dirSubs[i].name || state.dirSubs[i].fullname || "";
            }
        }
        return "";
    }

    function renderSubList() {
        var keyword = byId("mtgDirSubSearch").value;
        var rows = filterRows(state.dirSubs, keyword, ["id", "code", "name", "fullname"]);
        var mode = OaonesPicker.mode("mtgDirSub");
        var partial = OaonesPicker.partialSelected(state.dirSubIds, subChildMap);
        var el = byId("mtgDirSubList");
        if (mode === "tree") {
            el.className = "wx-pick-list is-tree";
            el.innerHTML = OaonesPicker.renderTree("mtgDirSub", rows, {
                parentOf: function (s) { return s.parent; },
                textOf: function (s) { return rowText(s, ["id", "code", "name", "fullname"]); },
                keyword: keyword,
                emptyText: "没有匹配的分部",
                optionHtml: function (s) { return subOptionHtml(s, partial); }
            });
        } else {
            el.className = "wx-pick-list";
            var html = rows.length ? listHeadHtml("ID", "分部") : "";
            rows.forEach(function (s) { html += subOptionHtml(s, partial); });
            el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的分部</div>";
        }
        syncSubAllState();
    }

    function subOptionHtml(s, partial) {
        var id = String(s.id);
        var active = state.dirSubIds.indexOf(id) >= 0;
        var tip = ((active ? "点击取消选择" : "点击选择") + "；选中分部会自动选中其全部下级分部")
            + "｜" + (s.fullname || s.name || "");
        return optionHtml(id, s.name || s.fullname || "", active, !active && partial[id], tip);
    }

    function syncSubAllState() {
        var keyword = byId("mtgDirSubSearch").value;
        var ids = filterRows(state.dirSubs, keyword, ["id", "code", "name", "fullname"])
            .map(function (s) { return String(s.id); });
        var allOn = ids.length > 0 && ids.every(function (id) { return state.dirSubIds.indexOf(id) >= 0; });
        OaonesPicker.syncSelectAll("mtgDirSub", allOn);
        return allOn;
    }

    OaonesPicker.register("mtgDirSub", renderSubList);
    OaonesPicker.setSelectAll("mtgDirSub", function () {
        var keyword = byId("mtgDirSubSearch").value;
        var ids = filterRows(state.dirSubs, keyword, ["id", "code", "name", "fullname"])
            .map(function (s) { return String(s.id); });
        var on = syncSubAllState();
        var next;
        if (on) {
            var drop = {};
            ids.forEach(function (v) { drop[v] = true; });
            next = state.dirSubIds.filter(function (v) { return !drop[v]; });
        } else {
            next = state.dirSubIds.concat(ids);
        }
        state.dirSubIds = uniqueIds(next);
        renderSubList();
        return syncSubAllState();
    });

    function toggleSub(id) {
        var key = String(id);
        var idx = state.dirSubIds.indexOf(key);
        if (idx >= 0) {
            state.dirSubIds = state.dirSubIds.filter(function (v) { return v !== key; });
        } else {
            state.dirSubIds = state.dirSubIds.concat([key]);
        }
    }

    /* ---- OA 部门筛选（多选，选中自动含其下级部门；候选受“已选分部（含下级）”前置条件过滤） ---- */
    function deptChildMap() { return childMapOf(state.dirDepts, "parent"); }

    function deptLabel(id) {
        var key = String(id == null ? "" : id);
        for (var i = 0; i < state.dirDepts.length; i++) {
            if (String(state.dirDepts[i].id) === key) {
                return state.dirDepts[i].name || state.dirDepts[i].fullname || "";
            }
        }
        return "";
    }

    /** 已选分部（含下级）集合：部门候选只显示这些分部下的部门 */
    function selectedSubScope() {
        return expandIds(state.dirSubIds, subChildMap());
    }

    function deptPrecondition() {
        var subs = selectedSubScope();
        if (!subs.length) {
            return { tip: "未选分部时显示全部部门；先选分部后，这里只显示该分部（含下级分部）下的部门" };
        }
        return {
            tip: "仅显示已选 " + state.dirSubIds.length + " 个分部（含下级）下的部门",
            filter: function (d) {
                return subs.indexOf(String(d.subcompanyid1 == null ? "" : d.subcompanyid1)) >= 0;
            }
        };
    }

    OaonesPicker.setPrecondition("mtgDirDept", deptPrecondition);

    function renderDeptList() {
        var keyword = byId("mtgDirDeptSearch").value;
        var filtered = filterRows(state.dirDepts, keyword, ["id", "code", "name", "fullname"]);
        // 列表式要手动套前置条件（树状由 renderTree 内部套）
        var rows = OaonesPicker.applyPrecondition("mtgDirDept", filtered);
        var mode = OaonesPicker.mode("mtgDirDept");
        var partial = OaonesPicker.partialSelected(state.dirDeptIds, deptChildMap);
        var el = byId("mtgDirDeptList");
        if (mode === "tree") {
            el.className = "wx-pick-list is-tree";
            el.innerHTML = OaonesPicker.renderTree("mtgDirDept", rows, {
                parentOf: function (d) { return d.parent; },
                textOf: function (d) { return rowText(d, ["id", "code", "name", "fullname"]); },
                keyword: keyword,
                emptyText: "没有匹配的部门",
                optionHtml: function (d) { return deptOptionHtml(d, partial); }
            });
        } else {
            el.className = "wx-pick-list";
            var html = rows.length ? listHeadHtml("ID", "部门") : "";
            rows.forEach(function (d) { html += deptOptionHtml(d, partial); });
            el.innerHTML = html || "<div class='wx-pick-empty'>没有匹配的部门</div>";
        }
        OaonesPicker.preconditionTip("mtgDirDept", "mtgDirDeptNote");
        syncDeptAllState();
    }

    function deptOptionHtml(d, partial) {
        var id = String(d.id);
        var active = state.dirDeptIds.indexOf(id) >= 0;
        var tip = ((active ? "点击取消选择" : "点击选择") + "；选中部门会自动选中其全部下级部门")
            + "｜" + (d.fullname || d.name || "");
        return optionHtml(id, d.name || d.fullname || "", active, !active && partial[id], tip);
    }

    function deptAllIds() {
        var keyword = byId("mtgDirDeptSearch").value;
        return OaonesPicker.applyPrecondition("mtgDirDept",
                filterRows(state.dirDepts, keyword, ["id", "code", "name", "fullname"]))
            .map(function (d) { return String(d.id); });
    }

    function syncDeptAllState() {
        var ids = deptAllIds();
        var allOn = ids.length > 0 && ids.every(function (id) { return state.dirDeptIds.indexOf(id) >= 0; });
        OaonesPicker.syncSelectAll("mtgDirDept", allOn);
        return allOn;
    }

    OaonesPicker.register("mtgDirDept", renderDeptList);
    OaonesPicker.setSelectAll("mtgDirDept", function () {
        var ids = deptAllIds();
        var on = syncDeptAllState();
        var next;
        if (on) {
            var drop = {};
            ids.forEach(function (v) { drop[v] = true; });
            next = state.dirDeptIds.filter(function (v) { return !drop[v]; });
        } else {
            next = state.dirDeptIds.concat(ids);
        }
        state.dirDeptIds = uniqueIds(next);
        renderDeptList();
        return syncDeptAllState();
    });

    function toggleDept(id) {
        var key = String(id);
        var idx = state.dirDeptIds.indexOf(key);
        if (idx >= 0) {
            state.dirDeptIds = state.dirDeptIds.filter(function (v) { return v !== key; });
        } else {
            state.dirDeptIds = state.dirDeptIds.concat([key]);
        }
    }

    /* ---- 弹框开关（取消 = 回退到打开前的选择）与选择框回显 ---- */
    function renderDirPickerInputs() {
        var subInput = byId("mtgDirSubInput");
        var deptInput = byId("mtgDirDeptInput");
        if (subInput) {
            subInput.value = state.dirSubIds.length === 1
                ? subLabel(state.dirSubIds[0]) + "（ID " + state.dirSubIds[0] + "）"
                : (state.dirSubIds.length > 1 ? "已选 " + state.dirSubIds.length + " 个分部（含下级）" : "");
            byId("mtgDirSubPicker").classList.toggle("has-value", state.dirSubIds.length > 0);
        }
        if (deptInput) {
            deptInput.value = state.dirDeptIds.length === 1
                ? deptLabel(state.dirDeptIds[0]) + "（ID " + state.dirDeptIds[0] + "）"
                : (state.dirDeptIds.length > 1 ? "已选 " + state.dirDeptIds.length + " 个部门（含下级）" : "");
            byId("mtgDirDeptPicker").classList.toggle("has-value", state.dirDeptIds.length > 0);
        }
    }

    function openSubPick() {
        loadDirOrgOptions(function (loaded) {
            if (!loaded) { return; }
            state.dirSubBackup = state.dirSubIds.slice();
            byId("mtgDirSubSearch").value = "";
            OaonesPicker.resetTree("mtgDirSub");
            renderSubList();
            showModal("mtgDirSubMask", "mtgDirSubModal");
            byId("mtgDirSubSearch").focus();
        });
    }

    function closeSubPick(commit) {
        if (!commit && state.dirSubBackup) { state.dirSubIds = state.dirSubBackup.slice(); }
        state.dirSubBackup = null;
        hideModal("mtgDirSubMask", "mtgDirSubModal");
        if (commit) {
            // 换了分部后，已选部门可能落到新范围外（部门候选本来就受“已选分部”前置条件约束），
            // 这里把范围外的部门选择去掉，避免筛选条件自相矛盾（选了 A 分部却又按 B 分部的部门筛）。
            pruneDeptIdsToSubScope();
        }
        renderDirPickerInputs();
        if (commit) {
            loadDirUsers();
        }
    }

    /** 该部门属于哪个分部（部门候选行自带 subcompanyid1）。 */
    function subOfDept(id) {
        var key = String(id == null ? "" : id);
        for (var i = 0; i < state.dirDepts.length; i++) {
            if (String(state.dirDepts[i].id) === key) {
                return String(state.dirDepts[i].subcompanyid1 == null ? "" : state.dirDepts[i].subcompanyid1);
            }
        }
        return "";
    }

    /** 去掉“不在已选分部（含下级）范围内的部门”选择；未选分部时不收敛（部门候选本来就是全部）。 */
    function pruneDeptIdsToSubScope() {
        var scope = selectedSubScope();
        if (!scope.length) { return; }
        state.dirDeptIds = state.dirDeptIds.filter(function (id) {
            return scope.indexOf(subOfDept(id)) >= 0;
        });
    }

    function openDeptPick() {
        loadDirOrgOptions(function (loaded) {
            if (!loaded) { return; }
            state.dirDeptBackup = state.dirDeptIds.slice();
            byId("mtgDirDeptSearch").value = "";
            OaonesPicker.resetTree("mtgDirDept");
            renderDeptList();
            showModal("mtgDirDeptMask", "mtgDirDeptModal");
            byId("mtgDirDeptSearch").focus();
        });
    }

    function closeDeptPick(commit) {
        if (!commit && state.dirDeptBackup) { state.dirDeptIds = state.dirDeptBackup.slice(); }
        state.dirDeptBackup = null;
        hideModal("mtgDirDeptMask", "mtgDirDeptModal");
        renderDirPickerInputs();
        if (commit) { loadDirUsers(); }
    }

    /** 单击选项：只就地刷新选中态（不重建列表），保证双击仍命中同一行 */
    function refreshSubDom() {
        var opts = byId("mtgDirSubList").querySelectorAll(".wx-pick-option");
        var partial = OaonesPicker.partialSelected(state.dirSubIds, subChildMap);
        for (var i = 0; i < opts.length; i++) {
            var id = String(opts[i].getAttribute("data-id"));
            var active = state.dirSubIds.indexOf(id) >= 0;
            opts[i].classList.toggle("active", active);
            opts[i].classList.toggle("partial", !active && !!partial[id]);
        }
        syncSubAllState();
    }

    function refreshDeptDom() {
        var opts = byId("mtgDirDeptList").querySelectorAll(".wx-pick-option");
        var partial = OaonesPicker.partialSelected(state.dirDeptIds, deptChildMap);
        for (var i = 0; i < opts.length; i++) {
            var id = String(opts[i].getAttribute("data-id"));
            var active = state.dirDeptIds.indexOf(id) >= 0;
            opts[i].classList.toggle("active", active);
            opts[i].classList.toggle("partial", !active && !!partial[id]);
        }
        syncDeptAllState();
    }

    /* ---------------- 计划任务（全局一份：按时间段 / 高级 cron，两种方式） ---------------- */
    /** 读取计划任务：填“启用 + 同步方式 + 工作日口径 + 时间段明细（或两档 cron）”，随后刷新调度器提示。 */
    function loadPlan() {
        api("mtgsync.plan", {}, function (res) {
            if (!res || res.code !== 0) {
                toast((res && res.msg) || "读取计划任务失败", 2);
                return;
            }
            fillPlan(res.data || {});
            renderSchedulerTip();
        });
    }

    /**
     * 把计划任务填进表单：默认按时间段（明细表），可切到高级模式（白天 / 夜间两档 cron）。
     * 两种方式的取值都填好，来回切不丢值；「恢复默认」用后端给的默认明细 / 默认表达式。
     */
    function fillPlan(d) {
        state.planDefaults = {
            day: d.default_day_cron || "0 0 0/1 * * ?",
            night: d.default_night_cron || "0 0 0/2 * * ?",
            items: d.default_items || []
        };
        state.planGroups = d.workday_groups || [];
        state.workdayRule = d.workday_rule || "";
        state.todayWorkday = String(d.today_workday) === "0" ? 0 : 1;
        fillPlanGroups(d.workday_group_id);
        switchPlanMode(String(d.mode) === "1" ? "1" : "0");
        setVal("mtgPlanDayCron", d.day_cron || "");
        setVal("mtgPlanNightCron", d.night_cron || "");
        setVal("mtgPlanCronDayType", String(d.cron_day_type == null ? 0 : d.cron_day_type));
        renderPlanItems(d.items || []);
        var enabled = byId("mtgPlanEnabled");
        if (enabled) { enabled.checked = String(d.enabled) !== "0"; }
        hidePlanPreview();
    }

    /** 工作日口径的考勤组下拉（泛微标准考勤 kq_group；0 = 不指定，按周一至周五）。 */
    function fillPlanGroups(selected) {
        var el = byId("mtgPlanGroup");
        if (!el) { return; }
        var html = "<option value=\"0\">不指定（周一至周五）</option>";
        (state.planGroups || []).forEach(function (g) {
            html += "<option value=\"" + esc(g.id) + "\">" + esc(g.name || ("#" + g.id)) + "</option>";
        });
        el.innerHTML = html;
        el.value = selected == null ? "0" : String(selected);
        if (!el.value) { el.value = "0"; }
    }

    /** 切换同步方式：时间段明细块 / 高级 cron 块互斥显示（radio 值 0 按时间段 / 1 高级）。 */
    function switchPlanMode(mode) {
        var item = byId("mtgPlanModeItem");
        var cron = byId("mtgPlanModeCron");
        if (item) { item.checked = mode !== "1"; }
        if (cron) { cron.checked = mode === "1"; }
        var itemBox = byId("mtgPlanItemBox");
        var cronBox = byId("mtgPlanCronBox");
        if (itemBox) { itemBox.style.display = mode === "1" ? "none" : ""; }
        if (cronBox) { cronBox.style.display = mode === "1" ? "" : "none"; }
    }

    /** 当前同步方式：0 按时间段 / 1 高级（cron）。 */
    function planMode() {
        var cron = byId("mtgPlanModeCron");
        return cron && cron.checked ? "1" : "0";
    }

    function setVal(id, text) {
        var el = byId(id);
        if (el) { el.value = text == null ? "" : String(text); }
    }

    /* -------- 时间段明细（一行一个时间段：适用日 + 起止时间 + 轮询间隔，时间段不能重复） -------- */

    /** 适用日下拉的候选（1 工作日 / 2 非工作日 / 0 每天；工作日口径由「工作日口径」下拉决定）。 */
    function planDayOptions(selected) {
        var list = [{ v: "1", t: "工作日" }, { v: "2", t: "非工作日" }, { v: "0", t: "每天" }];
        var html = "";
        list.forEach(function (one) {
            html += "<option value=\"" + one.v + "\"" + (String(selected) === one.v ? " selected" : "")
                + ">" + one.t + "</option>";
        });
        return html;
    }

    /** 一行时间段的 HTML：时间输入框交给共用组件 js/datetime.js（data-oaones-date="time"）。 */
    function planItemRow(item) {
        var d = item || {};
        var dayType = String(d.day_type == null ? 1 : d.day_type);
        var minutes = parseInt(d.interval_min, 10);
        if (isNaN(minutes) || minutes <= 0) { minutes = 60; }
        var unit = (minutes % 60 === 0) ? 60 : 1;
        var num = Math.round(minutes / unit);
        return "<tr>"
            + "<td><select data-plan-day>" + planDayOptions(dayType) + "</select></td>"
            + "<td><input type=\"text\" data-oaones-date=\"time\" data-oaones-date-time=\"09:00:00\""
            + " data-plan-start style=\"width:112px;\" value=\"" + esc(d.start_time || "09:00:00") + "\""
            + " autocomplete=\"off\" data-lpignore=\"true\" placeholder=\"08:30\"></td>"
            + "<td><input type=\"text\" data-oaones-date=\"time\" data-oaones-date-time=\"18:00:00\""
            + " data-plan-end style=\"width:112px;\" value=\"" + esc(d.end_time || "18:00:00") + "\""
            + " autocomplete=\"off\" data-lpignore=\"true\" placeholder=\"18:00\"></td>"
            + "<td><input type=\"number\" data-plan-interval min=\"5\" step=\"5\" style=\"width:74px;\""
            + " value=\"" + num + "\" autocomplete=\"off\">"
            + "<select data-plan-unit style=\"margin-left:6px;\">"
            + "<option value=\"1\"" + (unit === 1 ? " selected" : "") + ">分钟</option>"
            + "<option value=\"60\"" + (unit === 60 ? " selected" : "") + ">小时</option></select></td>"
            + "<td class=\"muted\" data-plan-tip></td>"
            + "<td><button type=\"button\" class=\"link\" data-plan-del>删除</button></td>"
            + "</tr>";
    }

    /** 重画时间段明细（先按数据建行，再交给共用时间选择框组件接管时间输入）。 */
    function renderPlanItems(items) {
        var body = byId("mtgPlanItemBody");
        if (!body) { return; }
        var list = (items && items.length) ? items : [];
        var html = "";
        list.forEach(function (item) { html += planItemRow(item); });
        body.innerHTML = html;
        if (window.OaonesDate && OaonesDate.init) { OaonesDate.init(body); }
        updatePlanTips();
    }

    /** 收集页面上的时间段明细（提交给后端；时间照抄输入框，间隔换算成分钟）。 */
    function planItems() {
        var body = byId("mtgPlanItemBody");
        var rows = body ? body.querySelectorAll("tr") : [];
        var list = [];
        for (var i = 0; i < rows.length; i++) {
            var tr = rows[i];
            var day = tr.querySelector("[data-plan-day]");
            var start = tr.querySelector("[data-plan-start]");
            var end = tr.querySelector("[data-plan-end]");
            var num = tr.querySelector("[data-plan-interval]");
            var unit = tr.querySelector("[data-plan-unit]");
            var minutes = parseInt(num ? num.value : "", 10);
            var step = parseInt(unit ? unit.value : "1", 10) === 60 ? 60 : 1;
            list.push({
                day_type: parseInt(day ? day.value : "0", 10) || 0,
                start_time: start ? String(start.value || "").trim() : "",
                end_time: end ? String(end.value || "").trim() : "",
                interval_min: (isNaN(minutes) ? 0 : minutes) * step
            });
        }
        return list;
    }

    /** 每行右侧的“说明”列：人话复述这行的口径，配错了一眼能看出来。 */
    function updatePlanTips() {
        var rows = planItems();
        var body = byId("mtgPlanItemBody");
        var trs = body ? body.querySelectorAll("tr") : [];
        for (var i = 0; i < trs.length; i++) {
            var tip = trs[i].querySelector("[data-plan-tip]");
            if (!tip) { continue; }
            var one = rows[i] || {};
            var dayType = String(one.day_type);
            var dayText = dayType === "1" ? "工作日" : (dayType === "2" ? "非工作日" : "每天");
            var minutes = parseInt(one.interval_min, 10);
            var human = (isNaN(minutes) || minutes <= 0) ? "间隔填得不对" : humanMinutes(minutes);
            var startMin = planMinute(one.start_time);
            var endMin = planMinute(one.end_time);
            var cross = (startMin >= 0 && endMin >= 0 && endMin <= startMin) ? "（跨天）" : "";
            tip.textContent = dayText + " " + (one.start_time || "-") + "–" + (one.end_time || "-")
                + " " + human + cross;
        }
    }

    /** {@code HH:mm[:ss]} → 当天第几分钟；解析不了返回 -1（与后端 minuteOfTime 同口径）。 */
    function planMinute(text) {
        var m = /^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$/.exec(String(text || "").trim());
        if (!m) { return -1; }
        var h = parseInt(m[1], 10);
        var mi = parseInt(m[2], 10);
        if (isNaN(h) || isNaN(mi) || h < 0 || h > 23 || mi < 0 || mi > 59) { return -1; }
        return h * 60 + mi;
    }

    /** 分钟 → 人话（与后端 OaonesProps#humanMinutes 同一口径）。 */
    function humanMinutes(minutes) {
        var value = parseInt(minutes, 10);
        if (value % 60 === 0) {
            var hours = value / 60;
            return hours === 1 ? "每小时一次" : ("每 " + hours + " 小时一次");
        }
        return "每 " + value + " 分钟一次";
    }

    /** 保存 / 预测的公共入参：两种方式的取值都带上，后端按 mode 取用（切模式不丢值）。 */
    function planPayload() {
        return {
            mode: planMode(),
            workday_group_id: value("mtgPlanGroup"),
            cron_day_type: value("mtgPlanCronDayType"),
            day_cron: value("mtgPlanDayCron"),
            night_cron: value("mtgPlanNightCron"),
            items: JSON.stringify(planItems())
        };
    }

    /** 保存计划任务：时间段明细 / 两档 cron + 工作日口径 + 启用开关（校验与提示都由后端统一下发）。 */
    function savePlan() {
        var payload = planPayload();
        if (payload.mode === "0" && planItems().length === 0) {
            toast("按时间段同步：至少要配一个时间段（点「+ 新增时间段」）", 1, 9000);
            return;
        }
        if (payload.mode === "1" && !payload.day_cron && !payload.night_cron) {
            toast("高级模式：白天 / 夜间至少填一个 cron 表达式（留空 = 该时段不自动同步）", 1, 9000);
            return;
        }
        var enabled = byId("mtgPlanEnabled");
        payload.enabled = enabled && enabled.checked ? 1 : 0;
        api("mtgsync.planSave", payload, function (res) {
            if (!res || res.code !== 0) { toast((res && res.msg) || "保存计划任务失败", 2, 10000); return; }
            fillPlan(res.data || {});
            renderSchedulerTip();
            toast("计划任务已保存：" + ((res.data && res.data.rule_text) || ""), 0, 8000);
        });
    }

    /** 收起「预测后 10 次」的结果块（切数据 / 重新读取时用）。 */
    function hidePlanPreview() {
        var box = byId("mtgPlanPreviewBox");
        if (box) { box.style.display = "none"; }
        var list = byId("mtgPlanPreviewList");
        if (list) { list.innerHTML = ""; }
    }

    /**
     * 「预测后 10 次」：把表单里的配置（时间段明细，或高级模式的两档 cron）交给后端试算
     * **实际会执行的时间**（与调度器同一份判断），并标明每次命中的是哪个时间段 / 哪一档。
     */
    function previewPlan() {
        var payload = planPayload();
        if (payload.mode === "0" && planItems().length === 0) {
            toast("按时间段同步：至少要配一个时间段", 1, 9000);
            return;
        }
        if (payload.mode === "1" && !payload.day_cron && !payload.night_cron) {
            toast("高级模式：白天 / 夜间至少填一个 cron 表达式", 1, 9000);
            return;
        }
        payload.count = 10;
        api("mtgsync.planPreview", payload, function (res) {
            if (!res || res.code !== 0) { toast((res && res.msg) || "预测失败", 2, 12000); return; }
            var d = res.data || {};
            var runs = d.runs || [];
            var labels = d.labels || [];
            var html = "";
            runs.forEach(function (t, i) {
                html += "<li>" + esc(t) + "　<span class='muted'>" + esc(labels[i] || "") + "</span></li>";
            });
            byId("mtgPlanPreviewList").innerHTML = html;
            byId("mtgPlanPreviewBox").style.display = runs.length ? "" : "none";
            if (!runs.length) {
                toast("按当前配置找不到可执行的时间，请检查时间段 / cron 表达式", 1, 10000);
            }
        });
    }


    /**
     * 调度器状态：显示计划任务是否到点 / 上次与下次执行 / 最近一轮结果。
     * 口径：到点跑一轮（推送 + 拉取），按内容快照“变了才同步”。
     */
    function renderSchedulerTip() {
        api("mtgsync.scheduler", {}, function (res) {
            var el = byId("mtgSchedulerTip");
            if (!el || !res || res.code !== 0 || !res.data) { return; }
            var d = res.data;
            el.style.display = "";
            if (d.plan_error) {
                el.textContent = "计划任务读取失败：" + d.plan_error
                    + "（请确认已执行 uf_oaones_mtg_cron / uf_oaones_mtg_plan_item 建表脚本并重启 E9）";
                return;
            }
            var text = "";
            if (!d.enabled) {
                text = "内置调度器已关闭（oaones.properties#mtg.sync.enable=0）：开启的租户也不会自动同步。";
            } else if (String(d.plan_enabled) === "0") {
                text = "计划任务已停用：不会自动同步（页面上的「拉取」等手工入口仍可用）。";
            } else {
                text = (d.rule_text || "") + "  " + (d.workday_rule || "") + "  "
                    + (d.next_run
                        ? "下次执行：" + d.next_run + (d.next_label ? "（" + d.next_label + "）" : "") + "。"
                        : "计划任务没有可执行时间，请检查时间段 / cron 表达式。")
                    + (String(d.configured) === "false" ? "（计划任务还没保存过，当前按默认频率执行）" : "")
                    + (String(d.today_workday) === "0" ? "今天是【非工作日】。" : "今天是【工作日】。")
                    + "每 " + d.tick_seconds + " 秒检查一次是否到点（到点后最多晚 " + d.tick_seconds + " 秒执行）；"
                    + "到点跑一轮：OA→第三方 推送 + 第三方→OA 拉取，按内容快照“变了才同步”。"
                    + (d.last_run ? "  上次执行：" + d.last_run + "。" : "  尚未执行过。")
                    + (d.last_message ? "  最近结果：" + d.last_message : "");
            }
            el.textContent = text;
        });
    }

    /** 行内「拉取」：立即对该租户拉取一轮「第三方→OA」 */
    function pullNow(cfgId) {
        var row = rowByCfgId(cfgId);
        if (window.OaonesToast) { OaonesToast.loading("正在从第三方平台拉取，请不要关闭页面…"); }
        api("mtgsync.pullNow", { biz: BIZ, type: TYPE, cfg_id: cfgId }, function (res) {
            if (window.OaonesToast) { OaonesToast.hideLoading(); }
            if (!res || res.code !== 0) { toast((res && res.msg) || "拉取失败", 2, 8000); return; }
            var d = res.data || {};
            toast("「" + (row ? row.cfg_name : cfgId) + "」拉取完成："
                + (d.message || ("新增 " + (d.created || 0) + " / 更新 " + (d.updated || 0)
                    + " / 删除 " + (d.deleted || 0) + " / 跳过 " + (d.skipped || 0)
                    + " / 失败 " + (d.failed || 0))), 0, 8000);
            // 拉取会改变「同步清单」的内容：已经打开过那个页签就刷新它，没打开过等切过去再取
            if (state.itemsLoaded) { loadItems(); }
            if (state.logLoaded) { loadLogs(); }
        });
    }

    /* ---------------- 同步记录 ---------------- */
    /** 把当前平台可维护租户填进某个选择框（同步记录 / 同步清单的“租户”筛选共用）。 */
    function fillCfgSelect(elId) {
        var el = byId(elId);
        if (!el) { return; }
        var current = el.value;
        var html = "<option value=''>全部</option>";
        state.rows.forEach(function (r) {
            html += "<option value='" + esc(r.cfg_id) + "'>" + esc(r.cfg_name || ("#" + r.cfg_id)) + "</option>";
        });
        el.innerHTML = html;
        if (current) { el.value = current; }
    }

    /* ---------------- 同步清单（按 OA 对象维度看结果 + 重推） ---------------- */
    function loadItems() {
        state.itemsLoaded = true;
        api("mtgsync.items", {
            biz: BIZ, type: TYPE, cfg_id: value("mtgItemCfg"), op: value("mtgItemOp"),
            keyword: value("mtgItemKeyword"), limit: 2000
        }, function (res) {
            if (!res || res.code !== 0) { toast((res && res.msg) || "读取同步清单失败", 2); return; }
            state.items = (res.data && res.data.rows) || [];
            renderItems();
        });
    }

    function renderItems() {
        var body = byId("mtgItemBody");
        if (!body) { return; }
        if (!state.itemPager) {
            state.itemPager = OaonesTable.pager({
                key: "mtgItem", el: "mtgItemPager", onChange: function () { renderItems(); }
            });
        }
        state.itemPager.resetIfChanged([value("mtgItemCfg"), value("mtgItemOp"),
            value("mtgItemKeyword")].join("|"));
        var pageRows = state.itemPager.apply(state.items);
        if (!state.items.length) {
            body.innerHTML = "<tr><td colspan=\"11\"><div class=\"empty\">没有符合条件的同步清单</div></td></tr>";
            state.itemPager.render();
            return;
        }
        var html = "";
        pageRows.forEach(function (r) {
            var fail = String(r.op) === "3";
            var skip = String(r.op) === "2";
            var canRetry = fail || skip;
            // 状态 = 可点开的“报文”入口：点“新增 / 更新 / 跳过 / 失败”都弹这条会议 / 日程的报文
            var opTag = payloadTag(r);
            var ops = canRetry
                ? "<button class='btn small' data-mtg-retry=\"" + esc(r.cfg_id) + "|" + esc(r.oa_id) + "\">重推</button>"
                : "<span class='notice'>—</span>";
            html += "<tr" + (fail ? " style=\"background:#faefee;\"" : "") + ">"
                + "<td>" + esc(r.oa_id || "-") + "</td>"
                + "<td>" + esc(r.oa_title || "-") + "</td>"
                + "<td>" + esc(r.oa_sub_name || "-") + "</td>"
                + "<td>" + esc(r.begin_time || "-") + "</td>"
                + "<td>" + esc(r.cfg_name || ("#" + r.cfg_id)) + "</td>"
                + "<td>" + opTag + "</td>"
                + "<td>" + esc(r.try_count == null ? 1 : r.try_count) + "</td>"
                + "<td>" + esc(r.sync_time || "-") + "</td>"
                + "<td>" + esc(r.wx_id || "-") + "</td>"
                + "<td>" + esc(r.message || "-") + "</td>"
                + "<td>" + ops + "</td>"
                + "</tr>";
        });
        body.innerHTML = html;
        if (window.OaonesTable) { OaonesTable.resizable(byId("mtgItemTable"), { key: "mtgItem" }); }
        state.itemPager.render();
    }

    function retryItem(cfgId, oaId) {
        if (window.OaonesToast) { OaonesToast.loading("正在重推，请不要关闭页面…"); }
        api("mtgsync.retry", { biz: BIZ, type: TYPE, cfg_id: cfgId, oa_id: oaId }, function (res) {
            if (window.OaonesToast) { OaonesToast.hideLoading(); }
            if (!res || res.code !== 0) { toast((res && res.msg) || "重推失败", 2, 8000); return; }
            var d = res.data || {};
            toast("重推完成：" + (d.message || d.op_text || "已执行"), 0, 8000);
            loadItems();
            if (state.logLoaded) { loadLogs(); }
        });
    }

    /* ---------------- 「报文」：查看实际下发给第三方平台的接口报文 ---------------- */
    /**
     * 行内“状态”标签（同步清单 / 同步记录共用）：**本身就是「报文」入口**——
     * 点“新增 / 更新 / 跳过 / 失败”都会弹出这条 OA 会议 / 日程的接口报文与字段说明。
     */
    function payloadTag(r) {
        var op = String(r.op);
        var cls = op === "3" ? "fail" : (op === "2" ? "off" : "bound");
        var text = op === "3" ? "失败" : (op === "2" ? "跳过" : (esc(r.op_text || "") || "-"));
        return "<button type='button' class='tag " + cls + " mtg-status-tag' data-mtg-payload=\""
            + esc(r.cfg_id) + "|" + esc(r.oa_id)
            + "\" title='点击查看这条会议 / 日程下发给第三方平台的接口报文与字段说明'>"
            + text + "</button>";
    }

    /** 请求体 JSON 美化（后端给的是紧凑 JSON，解析失败时原样展示） */
    function prettyJson(text) {
        try {
            return JSON.stringify(JSON.parse(text), null, 2);
        } catch (e) {
            return String(text == null ? "" : text);
        }
    }

    /** 复制文本：优先剪贴板 API，非安全上下文（http）退化为临时文本框 + execCommand */
    function copyText(text, okMsg) {
        var payload = String(text == null ? "" : text);
        if (!payload) { toast("没有可复制的内容", 1); return; }
        function fallback() {
            var ta = document.createElement("textarea");
            ta.value = payload;
            ta.setAttribute("readonly", "readonly");
            ta.style.position = "fixed";
            ta.style.left = "-9999px";
            document.body.appendChild(ta);
            ta.select();
            var ok = false;
            try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
            document.body.removeChild(ta);
            toast(ok ? (okMsg || "已复制") : "复制失败，请手动选择报文复制", ok ? 0 : 2);
        }
        if (window.navigator && navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(payload).then(function () { toast(okMsg || "已复制", 0); }, fallback);
            return;
        }
        fallback();
    }

    function showPayload() {
        byId("mtgPayloadMask").style.display = "block";
        byId("mtgPayloadModal").style.display = "block";
    }

    function hidePayload() {
        byId("mtgPayloadMask").style.display = "none";
        byId("mtgPayloadModal").style.display = "none";
        state.payload = null;
    }

    /**
     * 打开某条会议 / 日程的「报文」：向后端取**实际会下发给第三方平台**的请求报文
     * （按 biz + type + cfg_id + oa_id 定位，与“重推 / 自动同步”同一份组装代码）。
     */
    function openPayload(cfgId, oaId) {
        hidePayload();
        if (window.OaonesToast) { OaonesToast.loading("正在组装接口报文，请不要关闭页面…"); }
        api("mtgsync.payload", { biz: BIZ, type: TYPE, cfg_id: cfgId, oa_id: oaId }, function (res) {
            if (window.OaonesToast) { OaonesToast.hideLoading(); }
            // 接口报错（无权限 / 对象已删除等）也把弹框打开、把原因写在弹框里，
            // 免得只弹一句话，看不出是“读取失败”还是“报文内容就是这样”
            if (!res || res.code !== 0) {
                renderPayload({ cfg_id: cfgId, oa_id: oaId, error: (res && res.msg) || "读取接口报文失败" });
                showPayload();
                return;
            }
            renderPayload(res.data || {});
            showPayload();
        });
    }

    function renderPayload(d) {
        var hasPayload = d.payload !== undefined && d.payload !== null && String(d.payload) !== "";
        var body = hasPayload ? prettyJson(d.payload)
            : (d.error
                ? ("读取接口报文失败：" + d.error + "\n\n（这条记录可能已被删除，或当前登录人不是该租户的维护人）")
                : "（后端没有返回报文内容：请确认服务器已部署最新 classbean 并重启 E9）");
        var apiText = hasPayload ? ((d.api_method || "POST") + " " + (d.api_url || d.api_path || "")) : "—";
        state.payload = { api: apiText, body: body };

        var title = (d.biz_text || LABEL) + "报文";
        if (d.oa_title) { title += " · " + d.oa_title; }
        byId("mtgPayloadTitle").textContent = title;
        byId("mtgPayloadMeta").textContent = "平台：" + (window.P_FULL || "")
            + "　租户：" + (d.cfg_name ? (d.cfg_name + "（#" + d.cfg_id + "）") : ("#" + d.cfg_id))
            + "　OA" + (d.biz_text || LABEL) + "ID：" + (d.oa_id || "-")
            + (d.begin_time ? "　开始时间：" + d.begin_time : "");
        byId("mtgPayloadApi").textContent = apiText;
        byId("mtgPayloadBody").textContent = body;

        var tips = d.tips || [];
        var tipEl = byId("mtgPayloadTips");
        if (tips.length) {
            var tipHtml = "";
            tips.forEach(function (one) { tipHtml += "<div>" + esc(one) + "</div>"; });
            tipEl.innerHTML = tipHtml;
            tipEl.style.display = "";
        } else {
            tipEl.innerHTML = "";
            tipEl.style.display = "none";
        }

        var fields = d.fields || [];
        var fieldBody = byId("mtgPayloadFieldBody");
        var fieldHtml = "";
        fields.forEach(function (f) {
            fieldHtml += "<tr><td><code>" + esc(f.field) + "</code></td><td>" + esc(f.text) + "</td></tr>";
        });
        fieldBody.innerHTML = fieldHtml;
        byId("mtgPayloadFieldEmpty").style.display = fields.length ? "none" : "";
        if (fields.length && window.OaonesTable) {
            OaonesTable.resizable(byId("mtgPayloadFieldTable"), { key: "mtgPayloadField" });
        }
    }

    function loadLogs() {
        state.logLoaded = true;
        // 时间检索走日期时间框（精确到秒）：一个框给完整 yyyy-MM-dd HH:mm:ss，
        // 后端拆成“日期 + 时间”两列过滤（只填日期时按 00:00:00 / 23:59:59 补）
        api("mtgsync.logs", {
            biz: BIZ, type: TYPE, cfg_id: value("mtgLogCfg"), op: value("mtgLogOp"),
            keyword: value("mtgLogKeyword"), from_time: value("mtgLogFrom"), to_time: value("mtgLogTo"), limit: 2000
        }, function (res) {
            if (!res || res.code !== 0) { toast((res && res.msg) || "读取同步记录失败", 2); return; }
            state.logs = (res.data && res.data.rows) || [];
            renderLogs(res.data || {});
        });
    }

    function renderLogs(data) {
        var body = byId("mtgLogBody");
        if (!body) { return; }
        if (!state.logPager) {
            state.logPager = OaonesTable.pager({
                key: "mtgLog", el: "mtgLogPager", onChange: function () { renderLogs(data); }
            });
        }
        state.logPager.resetIfChanged([value("mtgLogCfg"), value("mtgLogOp"), value("mtgLogKeyword"),
            value("mtgLogFrom"), value("mtgLogTo")].join("|"));
        var pageRows = state.logPager.apply(state.logs);
        if (!state.logs.length) {
            body.innerHTML = "<tr><td colspan=\"8\"><div class=\"empty\">没有符合条件的同步记录</div></td></tr>";
            state.logPager.render();
            return;
        }
        var html = "";
        pageRows.forEach(function (r) {
            var fail = String(r.op) === "3";
            html += "<tr" + (fail ? " style=\"background:#faefee;\"" : "") + ">"
                + "<td>" + esc(r.sync_time || "-") + "</td>"
                + "<td>" + esc(r.cfg_name || ("#" + r.cfg_id)) + "</td>"
                + "<td>" + esc(r.oa_title || "-") + "</td>"
                + "<td>" + esc(r.oa_sub_name || "-") + "</td>"
                + "<td>" + esc(r.begin_time || "-") + "</td>"
                + "<td>" + payloadTag(r) + "</td>"
                + "<td>" + esc(r.wx_id || "-") + "</td>"
                + "<td>" + esc(r.message || "-") + "</td>"
                + "</tr>";
        });
        body.innerHTML = html;
        if (data && data.truncated) {
            toast("记录较多，仅显示最近 " + state.logs.length + " 条，请缩小时间范围", 1, 8000);
        }
        state.logPager.render();
        if (window.OaonesTable) { OaonesTable.resizable(byId("mtgLogTable"), { key: "mtgLog" }); }
    }

    /* ---------------- 事件 ---------------- */
    function bindEvents() {
        // 计划任务：方式切换 / 时间段增删 / 保存 / 预测后 10 次 / 恢复默认
        byId("mtgPlanModeItem").addEventListener("change", function () { switchPlanMode("0"); });
        byId("mtgPlanModeCron").addEventListener("change", function () { switchPlanMode("1"); });
        byId("mtgPlanItemAddBtn").addEventListener("click", function () {
            var body = byId("mtgPlanItemBody");
            if (!body) { return; }
            body.insertAdjacentHTML("beforeend", planItemRow(null));
            // 新行的时间输入框要单独接入组件（整表重画时走 OaonesDate.init(body)）
            if (window.OaonesDate && OaonesDate.init) { OaonesDate.init(body); }
            updatePlanTips();
        });
        byId("mtgPlanItemBody").addEventListener("click", function (e) {
            if (!e.target.closest) { return; }
            if (!e.target.closest("[data-plan-del]")) { return; }
            var tr = e.target.closest("tr");
            if (tr && tr.parentNode) { tr.parentNode.removeChild(tr); }
            updatePlanTips();
        });
        byId("mtgPlanSaveBtn").addEventListener("click", savePlan);
        byId("mtgPlanPreviewBtn").addEventListener("click", previewPlan);
        byId("mtgPlanPreviewHide").addEventListener("click", hidePlanPreview);
        byId("mtgPlanResetBtn").addEventListener("click", function () {
            var def = state.planDefaults || {};
            if (planMode() === "1") {
                setVal("mtgPlanDayCron", def.day || "0 0 0/1 * * ?");
                setVal("mtgPlanNightCron", def.night || "0 0 0/2 * * ?");
                setVal("mtgPlanCronDayType", "0");
                toast("已填入默认 cron（" + (def.day || "0 0 0/1 * * ?") + " / "
                    + (def.night || "0 0 0/2 * * ?")
                    + "：白天每小时一次、其余时段每 2 小时一次），点「保存计划任务」生效", 0, 8000);
            } else {
                renderPlanItems(def.items || []);
                toast("已填入默认时间段（工作日 9:00–18:00 每小时一次、非工作日 9:00–18:00 每 2 小时一次），"
                    + "点「保存计划任务」生效", 0, 8000);
            }
        });
        // 时间段里的任何改动都刷新右侧“说明”列（时间、间隔、单位、适用日）
        ["change", "input"].forEach(function (evt) {
            byId("mtgPlanItemBody").addEventListener(evt, function (e) {
                if (!e.target.closest) { return; }
                if (!e.target.closest("[data-plan-day],[data-plan-start],[data-plan-end],"
                        + "[data-plan-interval],[data-plan-unit]")) { return; }
                updatePlanTips();
            });
        });
        byId("mtgSettingSearchBtn").addEventListener("click", function () {
            state.keyword = value("mtgSettingKeyword"); renderSettings();
        });
        byId("mtgSettingKeyword").addEventListener("keydown", function (e) {
            if (e.key === "Enter") { state.keyword = value("mtgSettingKeyword"); renderSettings(); }
        });
        byId("mtgSettingResetBtn").addEventListener("click", function () {
            state.keyword = ""; byId("mtgSettingKeyword").value = ""; renderSettings();
        });
        byId("mtgSettingBody").addEventListener("click", function (e) {
            if (!e.target.closest) { return; }
            var pull = e.target.closest("[data-mtg-pull]");
            if (pull) { pullNow(pull.getAttribute("data-mtg-pull")); return; }
            var chkApp = e.target.closest("[data-mtg-appcheck]");
            if (chkApp) { checkApp(chkApp.getAttribute("data-mtg-appcheck")); return; }
            var t = e.target.closest("[data-mtg-state]");
            if (t) { toggle(t.getAttribute("data-mtg-state"), parseInt(t.getAttribute("data-mtg-enable"), 10)); }
        });
        // 「绑定应用」下拉：选完即保存；同步方向复选框：勾选即保存（两个方向可双选，至少要选一个）
        byId("mtgSettingBody").addEventListener("change", function (e) {
            var sel = e.target.closest ? e.target.closest("select[data-mtg-app]") : null;
            if (sel) { saveApp(sel.getAttribute("data-mtg-app"), sel.value); return; }
            var chk = e.target.closest ? e.target.closest("input[data-mtg-dir]") : null;
            if (!chk) { return; }
            saveDirection(chk.getAttribute("data-cfg"), chk.closest("tr"), chk);
        });
        /* ---- 个人同步方向（独立页签）：租户 / 分部 / 部门 / 人员 / 方向 筛选 ----
           分部与部门是多选弹框，选中自动含其下级（展开在服务端做）；候选按登录人的 OA 分权过滤。 */
        byId("mtgDirUserSearchBtn").addEventListener("click", loadDirUsers);
        byId("mtgDirUserKeyword").addEventListener("keydown", function (e) {
            if (e.key === "Enter") { loadDirUsers(); }
        });
        byId("mtgDirUserCfg").addEventListener("change", loadDirUsers);
        byId("mtgDirDirection").addEventListener("change", loadDirUsers);
        byId("mtgDirUserResetBtn").addEventListener("click", function () {
            byId("mtgDirUserCfg").value = "";
            byId("mtgDirUserKeyword").value = "";
            byId("mtgDirDirection").value = "";
            state.dirSubIds = [];
            state.dirDeptIds = [];
            renderDirPickerInputs();
            loadDirUsers();
        });
        /* 分部弹框 */
        byId("mtgDirSubPicker").addEventListener("click", function (e) {
            if (e.target && e.target.closest && e.target.closest("#mtgDirSubClear")) { return; }
            openSubPick();
        });
        byId("mtgDirSubClear").addEventListener("click", function (e) {
            e.stopPropagation();
            state.dirSubIds = [];
            renderDirPickerInputs();
            loadDirUsers();
        });
        byId("mtgDirSubSearch").addEventListener("input", function () { renderSubList(); });
        byId("mtgDirSubList").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            toggleSub(btn.getAttribute("data-id"));
            refreshSubDom();
        });
        byId("mtgDirSubList").addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            // 双击前已收到两次单击（选中→取消），这里确保该行处于选中态后再确定
            if (state.dirSubIds.indexOf(btn.getAttribute("data-id")) < 0) {
                toggleSub(btn.getAttribute("data-id"));
                refreshSubDom();
            }
            closeSubPick(true);
        });
        byId("mtgDirSubOk").addEventListener("click", function () { closeSubPick(true); });
        byId("mtgDirSubCancel").addEventListener("click", function () { closeSubPick(false); });
        byId("mtgDirSubClose").addEventListener("click", function () { closeSubPick(false); });
        byId("mtgDirSubMask").addEventListener("click", function (e) {
            if (e.target === this) { closeSubPick(false); }
        });
        byId("mtgDirSubClearBtn").addEventListener("click", function () {
            state.dirSubIds = [];
            renderSubList();
        });
        /* 部门弹框（候选受“已选分部（含下级）”前置条件过滤） */
        byId("mtgDirDeptPicker").addEventListener("click", function (e) {
            if (e.target && e.target.closest && e.target.closest("#mtgDirDeptClear")) { return; }
            openDeptPick();
        });
        byId("mtgDirDeptClear").addEventListener("click", function (e) {
            e.stopPropagation();
            state.dirDeptIds = [];
            renderDirPickerInputs();
            loadDirUsers();
        });
        byId("mtgDirDeptSearch").addEventListener("input", function () { renderDeptList(); });
        byId("mtgDirDeptList").addEventListener("click", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            toggleDept(btn.getAttribute("data-id"));
            refreshDeptDom();
        });
        byId("mtgDirDeptList").addEventListener("dblclick", function (e) {
            var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
            if (!btn) { return; }
            if (state.dirDeptIds.indexOf(btn.getAttribute("data-id")) < 0) {
                toggleDept(btn.getAttribute("data-id"));
                refreshDeptDom();
            }
            closeDeptPick(true);
        });
        byId("mtgDirDeptOk").addEventListener("click", function () { closeDeptPick(true); });
        byId("mtgDirDeptCancel").addEventListener("click", function () { closeDeptPick(false); });
        byId("mtgDirDeptClose").addEventListener("click", function () { closeDeptPick(false); });
        byId("mtgDirDeptMask").addEventListener("click", function (e) {
            if (e.target === this) { closeDeptPick(false); }
        });
        byId("mtgDirDeptClearBtn").addEventListener("click", function () {
            state.dirDeptIds = [];
            renderDeptList();
        });
        byId("mtgLogSearchBtn").addEventListener("click", loadLogs);
        byId("mtgLogKeyword").addEventListener("keydown", function (e) { if (e.key === "Enter") { loadLogs(); } });
        byId("mtgLogCfg").addEventListener("change", loadLogs);
        byId("mtgLogOp").addEventListener("change", loadLogs);
        byId("mtgLogResetBtn").addEventListener("click", function () {
            byId("mtgLogCfg").value = ""; byId("mtgLogOp").value = "";
            byId("mtgLogKeyword").value = "";
            // 日期时间框走组件清空：同时复位文本框、“清空”按钮与已展开的面板
            if (window.OaonesDate && OaonesDate.clear) {
                OaonesDate.clear("mtgLogFrom");
                OaonesDate.clear("mtgLogTo");
            } else {
                byId("mtgLogFrom").value = ""; byId("mtgLogTo").value = "";
            }
            loadLogs();
        });
        // 同步记录：点状态（新增 / 更新 / 跳过 / 失败）看这条会议 / 日程的接口报文
        byId("mtgLogBody").addEventListener("click", function (e) {
            var pl = e.target.closest ? e.target.closest("[data-mtg-payload]") : null;
            if (!pl) { return; }
            var p = String(pl.getAttribute("data-mtg-payload")).split("|");
            openPayload(p[0], p[1]);
        });
        byId("mtgItemSearchBtn").addEventListener("click", loadItems);
        byId("mtgItemKeyword").addEventListener("keydown", function (e) { if (e.key === "Enter") { loadItems(); } });
        byId("mtgItemCfg").addEventListener("change", loadItems);
        byId("mtgItemOp").addEventListener("change", loadItems);
        byId("mtgItemResetBtn").addEventListener("click", function () {
            byId("mtgItemCfg").value = ""; byId("mtgItemOp").value = "";
            byId("mtgItemKeyword").value = "";
            loadItems();
        });
        byId("mtgItemBody").addEventListener("click", function (e) {
            if (!e.target.closest) { return; }
            var pl = e.target.closest("[data-mtg-payload]");
            if (pl) {
                var p = String(pl.getAttribute("data-mtg-payload")).split("|");
                openPayload(p[0], p[1]);
                return;
            }
            var t = e.target.closest("[data-mtg-retry]");
            if (!t) { return; }
            var parts = String(t.getAttribute("data-mtg-retry")).split("|");
            retryItem(parts[0], parts[1]);
        });

        // 「报文」弹框：关闭（×/关闭按钮/点遮罩/Esc 均可）与复制
        byId("mtgPayloadClose").addEventListener("click", hidePayload);
        byId("mtgPayloadOkBtn").addEventListener("click", hidePayload);
        byId("mtgPayloadMask").addEventListener("click", hidePayload);
        byId("mtgPayloadCopyBtn").addEventListener("click", function () {
            copyText(state.payload && state.payload.body, "已复制请求报文");
        });
        byId("mtgPayloadCopyAllBtn").addEventListener("click", function () {
            if (!state.payload) { return; }
            copyText("【接口】" + state.payload.api + "\n【请求报文】\n" + state.payload.body,
                "已复制接口与请求报文");
        });
        document.addEventListener("keydown", function (e) {
            if (e.key !== "Escape") { return; }
            var modal = byId("mtgPayloadModal");
            if (modal && modal.style.display === "block") { hidePayload(); return; }
            var sub = byId("mtgDirSubModal");
            if (sub && sub.style.display === "block") { closeSubPick(false); return; }
            var dept = byId("mtgDirDeptModal");
            if (dept && dept.style.display === "block") { closeDeptPick(false); }
        });
    }

    function init() {
        bindEvents();
        if (window.OaonesDate && OaonesDate.init) { OaonesDate.init(); }
        loadSettings();
        // 「同步清单」/「同步记录」两个页签的内容在切过去时按需加载（见 switchTo）
    }

    /**
     * 切页签：切到哪个载哪个（重复载幂等）。
     * 「同步清单」与「同步记录」各自有独立页签，首次切过去才取数，避免一进页面就把两个大列表都拉下来。
     */
    function switchTo(tab) {
        if (tab === "item" && !state.itemsLoaded) { loadItems(); }
        if (tab === "log" && !state.logLoaded) { loadLogs(); }
        if (tab === "diruser" && !state.dirUsersLoaded) { loadDirUsers(); }
    }

    /** 页面上「刷新」按钮：刷新已经加载过的内容（设置 / 计划任务 / 清单 / 记录 / 个人同步方向）。 */
    function reload() {
        loadSettings();
        if (state.itemsLoaded) { loadItems(); }
        if (state.logLoaded) { loadLogs(); }
        if (state.dirUsersLoaded) { loadDirUsers(); }
    }

    window.OaonesMtgSync = { init: init, reload: reload, switchTo: switchTo };
})();
