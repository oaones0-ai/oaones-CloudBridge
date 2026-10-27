"use strict";

var fs = require("fs");
var path = require("path");
var vm = require("vm");
var root = path.resolve(__dirname, "..", "..", "..");

function read(rel) {
    return fs.readFileSync(path.join(root, rel), "utf8");
}

function check(name, ok) {
    if (!ok) {
        console.error("FAIL " + name);
        process.exitCode = 1;
    } else {
        console.log("PASS " + name);
    }
}

var page = read("oaones/appsyn/orgMap/orgMap.jsp");
var script = read("oaones/appsyn/js/orgMap.js");
var apiPage = read("oaones/appsyn/orgMap/api.jsp");
var orgMapService = read("src/com/customization/oaones/appsyn/service/impl/OrgMapServiceImpl.java");
var sql = read("sqlupgrade-sql/Mysql/oaones/appsyn/oaones0016_sub_tenant_map.sql");
var mapper = read("WEB-INF/config/mapper/oaones/appsyn/OrgMapMapper.xml");

var headers = [];
var re = /<th[^>]*>([\s\S]*?)<\/th>/g;
var match;
while ((match = re.exec(page))) {
    headers.push(match[1].replace(/<[^>]+>/g, "").trim());
}

check("表格固定四列", headers.length === 4
    && headers[0] === "OA 分部"
    && headers[1] === "企业微信租户"
    && headers[2] === "钉钉租户"
    && headers[3] === "飞书租户");
check("页面保留单表三平台并支持树状 / 列表切换",
    page.indexOf("data-orgmap-view=\"tree\"") >= 0
    && page.indexOf("data-orgmap-view=\"list\"") >= 0
    && page.indexOf("platform-tabs") < 0);
check("默认树状并移除分部标签", page.indexOf(
    "class=\"view-btn orgmap-view-btn active\" data-orgmap-view=\"tree\"") >= 0
    && script.indexOf("view: \"tree\"") >= 0
    && script.indexOf("tag bound") < 0);
check("平台映射改为行内选择框", script.indexOf("orgmap-tenant-select") >= 0
    && script.indexOf("data-orgmap-select") >= 0
    && script.indexOf("data-orgmap-action='map'") < 0
    && page.indexOf("orgMapModal") < 0);
check("三平台数据同页加载", script.indexOf("PLATFORMS.length") >= 0
    && script.indexOf("loadAllData") >= 0);
check("列表展示完整父分部路径", script.indexOf("orgmap-org-path") >= 0
    && script.indexOf("deriveSubPath") >= 0
    && script.indexOf("path") >= 0);
check("父子分部按层级集中排列", script.indexOf("buildSubTree") >= 0
    && script.indexOf("orderedSubRows") >= 0
    && script.indexOf("children[parentId].push") >= 0);
check("映射接口齐全", script.indexOf("orgmap.list") >= 0
    && script.indexOf("orgmap.save") >= 0
    && script.indexOf("orgmap.delete") >= 0);
check("保存异常转换为 JSON 提示", apiPage.indexOf("catch (Throwable t)") >= 0
    && apiPage.indexOf("OaonesUtil.fail") >= 0
    && orgMapService.indexOf("saveConflictOrOriginal") >= 0);
check("支持按有绑定 / 无绑定筛选", page.indexOf("value=\"bound\">有绑定") >= 0
    && page.indexOf("value=\"unbound\">无绑定") >= 0
    && script.indexOf("hasAnyBinding") >= 0
    && script.indexOf("state.status") >= 0);
check("平台内两侧一对一唯一约束", sql.indexOf("(type, oa_sub_id)") >= 0
    && sql.indexOf("(type, cfg_id)") >= 0);
check("只处理 OA 分部", page.indexOf("OA 部门") < 0
    && script.indexOf("oa_departments") < 0
    && script.indexOf("oa_sub_id") >= 0);
check("Mapper 返回三个平台的租户名称", mapper.indexOf("cfg_name") >= 0
    && mapper.indexOf("m.type = #{type}") >= 0);

/* 运行 orgMap.js：用一棵三级分部树验证列表顺序、路径、树状折叠与搜索。 */
var htmlById = {};
var listeners = {};
function mockEl(id) {
    if (htmlById[id]) { return htmlById[id]; }
    var classes = {};
    htmlById[id] = {
        id: id,
        innerHTML: "",
        textContent: "",
        value: "",
        disabled: false,
        style: {},
        classList: {
            add: function (name) { classes[name] = true; },
            remove: function (name) { delete classes[name]; }
        },
        addEventListener: function (type, fn) { listeners[id + ":" + type] = fn; }
    };
    return htmlById[id];
}
function mockViewButton(view) {
    var attrs = { "data-orgmap-view": view };
    var classes = {};
    return {
        getAttribute: function (name) { return attrs[name] || ""; },
        setAttribute: function (name, value) { attrs[name] = String(value); },
        classList: {
            add: function (name) { classes[name] = true; },
            remove: function (name) { delete classes[name]; }
        },
        addEventListener: function (type, fn) { listeners["view-" + view + ":" + type] = fn; }
    };
}
var viewButtons = [mockViewButton("tree"), mockViewButton("list")];
var subs = [
    { id: "1", name: "总部", fullname: "总部", path: "总部", parent: "0" },
    { id: "2", name: "华东", fullname: "华东", path: "总部 / 华东", parent: "1" },
    { id: "3", name: "上海", fullname: "上海", path: "总部 / 华东 / 上海", parent: "2" },
    { id: "4", name: "华南", fullname: "华南", path: "总部 / 华南", parent: "1" },
    { id: "5", name: "独立", fullname: "独立", path: "独立", parent: "0" }
];
var actions = [];
var saveParams = null;
var saveResponse = { code: 0, msg: "success" };
var toastMessages = [];
var mockMappings = [{
    id: "501", cfg_id: "10", cfg_name: "测试租户", cfg_corp_id: "corp-10",
    oa_sub_id: "2", oa_sub_name: "华东", oa_sub_parent_id: "1", remark: "", type: "0"
}];
var context = {
    console: console,
    document: {
        getElementById: mockEl,
        querySelectorAll: function (selector) {
            return selector === ".orgmap-view-btn" ? viewButtons : [];
        },
        addEventListener: function () {}
    },
    window: {
        OAONES_ORGMAP: { apiUrl: "/test", initialCfgId: "" },
        Oaones: {
            esc: function (v) {
                return String(v == null ? "" : v).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
            },
            apiAt: function (base, action, params, cb) {
                actions.push(action);
                if (action === "orgmap.save") { saveParams = params; }
                if (action === "orgmap.save" && saveResponse.code !== 0) {
                    cb(saveResponse);
                    return;
                }
                cb({
                    code: 0,
                    data: {
                        type: params.platform === "dingtalk" ? 1 : (params.platform === "feishu" ? 2 : 0),
                        tenants: [{
                            id: "10", corp_name: "测试租户", corp_id: "corp-10", sync_enable: "1"
                        }],
                        oa_subcompanies: subs,
                        mappings: params.platform === "wecom" ? mockMappings : []
                    }
                });
            }
        },
        OaonesToast: {
            show: function (msg) { toastMessages.push(msg); },
            confirm: function () {}
        },
        OaonesHrm: {
            sub: function (id, name) { return "<span class='mock-sub'>" + name + "</span>"; }
        }
    }
};
context.Oaones = context.window.Oaones;
context.OaonesToast = context.window.OaonesToast;
context.OaonesHrm = context.window.OaonesHrm;
vm.runInNewContext(script, context, { filename: "orgMap.js" });
var initialTreeHtml = mockEl("orgMapBody").innerHTML;
check("首次加载默认使用树状",
    initialTreeHtml.indexOf("orgmap-tree-toggle") >= 0
    && initialTreeHtml.indexOf("orgmap-org-path") < 0
    && initialTreeHtml.indexOf("orgmap-tenant-select") >= 0);

var inlineSelect = {
    value: "10",
    disabled: false,
    getAttribute: function (name) {
        return { "data-platform": "wecom", "data-sub-id": "1" }[name] || "";
    }
};
listeners["orgMapBody:change"]({
    target: { closest: function () { return inlineSelect; } }
});
check("选择租户后直接保存",
    actions.indexOf("orgmap.save") >= 0 && saveParams
    && saveParams.cfg_id === "10" && saveParams.oa_sub_id === "1");

saveResponse = {
    code: -1,
    msg: "租户“测试租户”刚刚已被其他 OA 分部映射。同一个租户只能映射一个 OA 分部，请刷新页面后重新选择"
};
toastMessages = [];
inlineSelect = {
    value: "10",
    disabled: false,
    getAttribute: function (name) {
        return { "data-platform": "wecom", "data-sub-id": "4" }[name] || "";
    }
};
listeners["orgMapBody:change"]({
    target: { closest: function () { return inlineSelect; } }
});
check("并发冲突显示业务提示而不抛请求异常",
    toastMessages.length === 1 && toastMessages[0].indexOf("请刷新页面后重新选择") >= 0);

listeners["view-list:click"].call(viewButtons[1]);
var listHtml = mockEl("orgMapBody").innerHTML;
var order = ["总部", "华东", "上海", "华南", "独立"].map(function (name) {
    return listHtml.indexOf(">" + name + "</span>");
});
check("列表按父分部深度优先排列",
    order.every(function (pos) { return pos >= 0; })
    && order[0] < order[1] && order[1] < order[2] && order[2] < order[3]);
check("列表显示三级完整路径", listHtml.indexOf("总部 / 华东 / 上海") >= 0);

listeners["orgMapStatus:change"].call({ value: "bound" });
var boundHtml = mockEl("orgMapBody").innerHTML;
check("有绑定只显示至少一个平台已映射的分部",
    boundHtml.indexOf(">华东</span>") >= 0
    && boundHtml.indexOf(">总部</span>") < 0
    && boundHtml.indexOf(">上海</span>") < 0);

listeners["orgMapStatus:change"].call({ value: "unbound" });
var unboundHtml = mockEl("orgMapBody").innerHTML;
check("无绑定只显示三个平台均未映射的分部",
    unboundHtml.indexOf(">华东</span>") < 0
    && unboundHtml.indexOf(">总部</span>") >= 0
    && unboundHtml.indexOf(">上海</span>") >= 0
    && unboundHtml.indexOf(">独立</span>") >= 0);
listeners["orgMapStatus:change"].call({ value: "" });

listeners["view-tree:click"].call(viewButtons[0]);
var treeHtml = mockEl("orgMapBody").innerHTML;
check("树状模式输出展开控件",
    treeHtml.indexOf("orgmap-tree-toggle") >= 0 && treeHtml.indexOf(">上海</span>") >= 0);
var toggle = {
    getAttribute: function (name) {
        return { "data-orgmap-action": "toggle", "data-sub-id": "2" }[name] || "";
    }
};
listeners["orgMapBody:click"]({ target: { closest: function () { return toggle; } } });
var collapsedHtml = mockEl("orgMapBody").innerHTML;
check("树状模式可收起当前子树",
    collapsedHtml.indexOf(">上海</span>") < 0 && collapsedHtml.indexOf(">华南</span>") >= 0);

listeners["view-list:click"].call(viewButtons[1]);
listeners["orgMapKeyword:input"].call({ value: "上海" });
var searchedHtml = mockEl("orgMapBody").innerHTML;
check("搜索命中子分部时保留上级路径",
    searchedHtml.indexOf("总部 / 华东 / 上海") >= 0
    && searchedHtml.indexOf(">华南</span>") < 0);
