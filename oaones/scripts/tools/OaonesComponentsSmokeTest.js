/**
 * OAones 公共组件冒烟测试。
 *
 * 用法：node scripts/tools/OaonesComponentsSmokeTest.js
 * 不连接浏览器/数据库，仅使用 Node + 最小对象验证公共组件契约与依赖方向。
 */
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const root = path.resolve(__dirname, "..");
const failures = [];
function check(label, condition, detail) {
    console.log("  " + (condition ? "✓" : "✗") + " " + label + (condition || !detail ? "" : "：" + detail));
    if (!condition) { failures.push(label); }
}
function read(name) { return fs.readFileSync(path.join(root, name), "utf8"); }
function run(name, window, extras) {
    const context = Object.assign({ window, document: window.document, console, Promise, URLSearchParams }, extras || {});
    vm.runInNewContext(read(name), context, { filename: name });
    return window;
}
function classList(initial) {
    const set = new Set(String(initial || "").split(/\s+/).filter(Boolean));
    return {
        contains(name) { return set.has(name); },
        add(name) { set.add(name); },
        remove(name) { set.delete(name); },
        toggle(name, force) { if (force === undefined ? !set.has(name) : force) { set.add(name); } else { set.delete(name); } }
    };
}
function fakeDocument() {
    const document = {
        currentScript: null,
        createElement() {
            const node = {
                children: [],
                parentNode: null,
                className: "",
                style: {},
                attributes: {},
                setAttribute(name, value) { this.attributes[name] = String(value); },
                getAttribute(name) { return this.attributes[name] ?? null; },
                appendChild(child) {
                    child.parentNode = this;
                    this.children.push(child);
                    return child;
                },
                removeChild(child) {
                    const index = this.children.indexOf(child);
                    if (index >= 0) { this.children.splice(index, 1); }
                    child.parentNode = null;
                    return child;
                }
            };
            Object.defineProperty(node, "ownerDocument", {value: document});
            return node;
        },
        addEventListener() {},
        removeEventListener() {}
    };
    document.defaultView = {
        addEventListener() {},
        removeEventListener() {}
    };
    document.documentElement = document.createElement("html");
    document.body = document.createElement("body");
    document.documentElement.appendChild(document.body);
    return document;
}

console.log("【1】树组件");
{
    const window = run("js/tree.js", {});
    const rows = [
        { id: "1", parent: "", name: "总部" },
        { id: "2", parent: "1", name: "研发部" },
        { id: "3", parent: "1", name: "销售部" }
    ];
    const model = window.OaonesTree.build(rows);
    check("组装根节点", model.roots.join(",") === "1");
    check("组装子节点", model.children["1"].join(",") === "2,3");
    const partial = window.OaonesTree.partialSelected(["3"], () => ({ "1": ["2", "3"] }));
    check("父节点部分选中", partial["1"] === true);
    const empty = window.OaonesTree.render([], { emptyText: "无数据" });
    check("空树输出统一空状态", empty.indexOf("无数据") >= 0 && empty.indexOf("wx-pick-empty") >= 0);
}

console.log("【2】通用 Loading");
{
    const document = fakeDocument();
    const window = run("js/loading.js", {document});
    const first = window.OaonesLoading.show("文档加载中ing", {key: "document"});
    window.OaonesLoading.show("请稍候", {key: "document"});
    check("Loading 显示动态文案", first.children[0].children[1].textContent === "请稍候");
    check("相同 key 不重复叠加", document.body.children.length === 1);
    const target = {
        ownerDocument: document,
        isConnected: true,
        getBoundingClientRect() {
            return {top: 20, left: 30, width: 640, height: 480};
        }
    };
    const overTarget = window.OaonesLoading.showFor(target, "文档加载中ing");
    check("区域 Loading 精确覆盖目标",
        overTarget.style.top === "20px" && overTarget.style.width === "640px");
    window.OaonesLoading.hideFor(target);
    check("区域 Loading 可独立关闭", document.body.children.length === 1);
    window.OaonesLoading.hide("document");
    check("全屏 Loading 可关闭", document.body.children.length === 0);
}

console.log("【3】API 核心与 appsyn 上下文");
(async () => {
    let requested = "";
    const window = {
        OAONES_API_URL: "/demo/api.jsp",
        OAONES_CFG_ID: "12",
        OAONES_CFG_ALL_LABEL: "全部演示租户",
        location: { href: "" },
        fetch(url) {
            requested = url;
            return Promise.resolve({ ok: true, json: () => Promise.resolve({ code: 0, data: { ok: 1 } }) });
        }
    };
    run("js/api.js", window, { fetch: window.fetch.bind(window) });
    let result = null;
    window.Oaones.api("demo.read", { id: 1 }, json => { result = json; });
    await new Promise(resolve => setTimeout(resolve, 0));
    check("请求 URL 带 action 与参数", requested.indexOf("action=demo.read") >= 0 && requested.indexOf("id=1") >= 0);
    check("请求回调返回结果", result && result.data.ok === 1);
    run("../appsyn/script/js/appContext.js", window);
    window.Oaones.cfgSelectRows([{ id: 1, corp_name: "租户甲", sync_enable: "1" }]);
    check("appsyn 上下文补齐默认登录地址", window.OAONES_API_CONFIG.loginUrl === "/oaones/appsyn/login");
    check("appsyn 租户上下文仍可用", window.Oaones.cfgLocked() && window.Oaones.lockedCfgId() === "12");

    console.log("【3.1】通用弹出面板");
    {
        function panelDocument() {
            const listeners = {};
            const document = {
                activeElement: null,
                createElement(tagName) {
                    const node = {
                        tagName: String(tagName).toUpperCase(),
                        nodeType: 1,
                        className: "",
                        style: {},
                        attributes: {},
                        children: [],
                        parentNode: null,
                        handlers: {},
                        textContent: "",
                        hidden: false,
                        disabled: false,
                        setAttribute(name, value) { this.attributes[name] = String(value); },
                        getAttribute(name) { return this.attributes[name] ?? null; },
                        removeAttribute(name) { delete this.attributes[name]; },
                        appendChild(child) {
                            if (child.parentNode) { child.parentNode.removeChild(child); }
                            child.parentNode = this;
                            this.children.push(child);
                            return child;
                        },
                        removeChild(child) {
                            const index = this.children.indexOf(child);
                            if (index >= 0) { this.children.splice(index, 1); }
                            child.parentNode = null;
                            return child;
                        },
                        addEventListener(type, fn) {
                            (this.handlers[type] || (this.handlers[type] = [])).push(fn);
                        },
                        removeEventListener(type, fn) {
                            this.handlers[type] = (this.handlers[type] || []).filter(item => item !== fn);
                        },
                        querySelector() { return null; },
                        querySelectorAll() { return []; },
                        focus() { document.activeElement = this; }
                    };
                    Object.defineProperty(node, "firstChild", {
                        get() { return this.children[0] || null; }
                    });
                    Object.defineProperty(node, "lastChild", {
                        get() { return this.children[this.children.length - 1] || null; }
                    });
                    let html = "";
                    Object.defineProperty(node, "innerHTML", {
                        get() { return html; },
                        set(value) {
                            html = String(value);
                            if (html === "") {
                                this.children.slice().forEach(child => this.removeChild(child));
                            }
                        }
                    });
                    return node;
                },
                addEventListener(type, fn, capture) {
                    const key = type + (capture ? ":capture" : ":bubble");
                    (listeners[key] || (listeners[key] = [])).push(fn);
                },
                removeEventListener(type, fn, capture) {
                    const key = type + (capture ? ":capture" : ":bubble");
                    listeners[key] = (listeners[key] || []).filter(item => item !== fn);
                },
                querySelectorAll(selector) {
                    if (selector !== ".modal, .oa-modal") { return []; }
                    return document.body.children.filter(node =>
                        String(node.className || "").split(/\s+/).some(name =>
                            name === "modal" || name === "oa-modal"));
                },
                emitKeydown(event) {
                    (listeners["keydown:capture"] || []).slice().forEach(fn => fn(event));
                }
            };
            document.body = document.createElement("body");
            document.activeElement = document.body;
            document.documentElement = document.createElement("html");
            return document;
        }
        function nodesByClass(node, className) {
            const result = [];
            (node.children || []).forEach(child => {
                if (String(child.className || "").split(/\s+/).indexOf(className) >= 0) { result.push(child); }
                result.push(...nodesByClass(child, className));
            });
            return result;
        }

        const panelDom = panelDocument();
        const panelWindow = run("js/panel.js", {
            document: panelDom,
            console,
            setTimeout(fn) { fn(); return 1; },
            clearTimeout() {}
        });
        const modalCalls = [];
        panelWindow.OaonesModal = {
            open(modal, options) {
                modalCalls.push("open");
                modal.style.display = "block";
                options.mask.style.display = "block";
            },
            close(modal, options) {
                modalCalls.push("close");
                modal.style.display = "none";
                options.mask.style.display = "none";
            }
        };
        const panelSizes = panelWindow.OaonesPanel.normalizeSize({
            size: "large",
            height: 520,
            maxWidth: "invalid"
        });
        check("弹出面板预设与显式尺寸归一化",
            panelSizes.width === "900px" && panelSizes.height === "520px"
            && panelSizes.maxWidth === "92vw");

        let confirmed = 0;
        let cancelled = "";
        let customClicked = "";
        const host = panelDom.createElement("div");
        host.className = "nested-content";
        const panel = panelWindow.OaonesPanel.create({
            key: "smoke",
            title: "主标题",
            subtitle: "小标题",
            size: { width: 780, height: 540 },
            content: host,
            buttons: [{
                key: "preview",
                text: "预览",
                position: "left",
                onClick(target) { customClicked = target.getContentElement() === host; }
            }, {
                key: "draft",
                text: "暂存",
                position: "right"
            }],
            onButtonClick(target, button) {
                if (button.key === "draft") { customClicked = "draft"; }
            },
            onCancel(target, reason) { cancelled = reason; },
            onConfirm() { confirmed += 1; }
        });
        panel.open();
        check("弹出面板按配置设置尺寸", panel.getElement().style.width === "780px"
            && panel.getElement().style.height === "540px");
        check("弹出面板渲染标题、副标题和嵌套内容",
            nodesByClass(panel.getElement(), "oa-panel-title")[0].textContent === "主标题"
            && nodesByClass(panel.getElement(), "oa-panel-subtitle")[0].textContent === "小标题"
            && panel.getContentElement().children[0] === host);
        const buttons = panel.getButtons();
        check("固定取消与确定按钮同时存在",
            buttons.cancel.textContent === "取消" && buttons.confirm.textContent === "确定");
        buttons.custom.preview.handlers.click[0]({ preventDefault() {} });
        buttons.custom.draft.handlers.click[0]({ preventDefault() {} });
        check("个性化按钮支持单项与统一点击回调",
            customClicked === "draft" && !!(panel.getButton("preview") && panel.getButton("draft")));
        panel.addButton({ key: "reset", text: "重置" });
        panel.updateButton("reset", { disabled: true, text: "重置中" });
        check("个性化按钮可动态追加并更新",
            panel.getButton("reset").disabled && panel.getButton("reset").textContent === "重置中");
        panel.removeButton("draft");
        check("个性化按钮可动态移除", !panel.getButton("draft"));

        // 确定事件由 modal 级委托统一处理。
        panel.getElement().handlers.click[0]({
            target: panel.getButton("confirm"),
            preventDefault() {}
        });
        check("确定按钮触发回调并退出", confirmed === 1 && !panel.isOpen());
        panel.open();
        panel.cancel("esc");
        check("取消路径带原因退出", cancelled === "esc" && !panel.isOpen());
        panel.open();
        panel.getElement().handlers.click[0]({
            target: nodesByClass(panel.getElement(), "oa-panel-close")[0],
            preventDefault() {}
        });
        check("右上角关闭按钮可退出", cancelled === "close" && !panel.isOpen());

        const parent = panelWindow.OaonesPanel.open({ title: "外层", content: "outer" });
        const child = panelWindow.OaonesPanel.open({ title: "内层", content: "inner" });
        panelDom.emitKeydown({
            key: "Escape",
            preventDefault() {},
            stopImmediatePropagation() {}
        });
        check("嵌套面板 Esc 只关闭最上层", !child.isOpen() && parent.isOpen());
        parent.close("test");
        parent.destroy();
        child.destroy();
        panel.destroy();

        const panelSource = read("js/panel.js");
        const panelStyle = read("css/style.css");
        check("弹出面板复用标题栏拖动并支持 Esc/右上角关闭",
            panelSource.indexOf("modal-head oa-panel-head") >= 0
            && panelSource.indexOf('event.key === "Escape"') >= 0
            && panelSource.indexOf("modal-close oa-panel-close") >= 0
            && modalCalls.indexOf("open") >= 0 && modalCalls.indexOf("close") >= 0);
        check("个性化按钮接口已预留",
            panelSource.indexOf("addButton: function") >= 0
            && panelSource.indexOf("removeButton: function") >= 0
            && panelSource.indexOf("updateButton: function") >= 0
            && panelSource.indexOf("onButtonClick") >= 0);
        check("弹出面板尺寸与内容区样式已注册",
            panelStyle.indexOf(".oa-panel-modal") >= 0
            && panelStyle.indexOf(".oa-panel-title") >= 0
            && panelStyle.indexOf(".oa-panel-subtitle") >= 0
            && panelStyle.indexOf(".oa-panel-content") >= 0);
    }

    console.log("【4】表单、上传、JSON、查询参数");
    const field = (name, value) => ({
        name, value, disabled: false, type: "text",
        getAttribute(key) { return key === "name" ? name : null; },
        classList: classList(""), setAttribute() {}, removeAttribute() {}, focus() {}
    });
    const nameField = field("name", "  演示  ");
    const form = {
        querySelectorAll(sel) { return sel.indexOf("input[name]") >= 0 ? [nameField] : []; },
        querySelector(sel) {
            if (sel.indexOf("name='name'") >= 0) { return nameField; }
            return null;
        }
    };
    const formWindow = run("js/form.js", { document: { getElementById: () => form, querySelector: () => form } });
    const values = formWindow.OaonesForm.values(form);
    check("表单读取并 trim", values.name === "演示");
    check("表单必填校验", formWindow.OaonesForm.validate(form, { name: [{ type: "required" }] }));
    const uploadWindow = run("js/upload.js", { document: {} });
    check("上传类型校验", !uploadWindow.OaonesUpload.validate({ name: "x.svg", size: 10 }, { extensions: /\.(png|jpg)$/i }).ok);
    check("上传大小校验", !uploadWindow.OaonesUpload.validate({ name: "x.png", size: 20 }, { maxBytes: 10 }).ok);
    const jsonWindow = run("js/jsonViewer.js", {});
    check("JSON 自动格式化", jsonWindow.OaonesJsonViewer.format('{"a":1}').indexOf('\n  "a": 1') >= 0);
    const queryWindow = run("js/query.js", {
        location: { search: "?a=1&a=2&b=x", pathname: "/p", hash: "#h" },
        history: { pushState() {} }
    }, { URLSearchParams });
    check("查询参数单值", queryWindow.OaonesQuery.get("b") === "x");
    check("查询参数多值", queryWindow.OaonesQuery.getAll("a").join(",") === "1,2");

    console.log("【5】页签与组织选择适配");
    const tabsWindow = run("js/tabs.js", {
        document: {
            querySelectorAll(selector) {
                if (selector === "#tabs button") { return tabsWindow._buttons; }
                if (selector === "[data-tab-panel]") { return tabsWindow._panels; }
                return [];
            }
        }
    });
    tabsWindow._buttons = ["a", "b"].map(key => ({ key, _handlers: [], classList: classList(key === "a" ? "active" : ""),
        getAttribute() { return this.key; }, setAttribute() {}, addEventListener(ev, fn) { this._handlers.push(fn); } }));
    tabsWindow._panels = ["a", "b"].map(key => ({ key, classList: classList(key === "a" ? "active" : ""),
        style: {}, getAttribute() { return this.key; }, setAttribute() {} }));
    let changed = "";
    const tabs = tabsWindow.OaonesTabs.bind({
        buttons: "#tabs button", panels: "[data-tab-panel]", initial: "a",
        onChange(key) { changed = key; }
    });
    tabs.switchTo("b");
    check("页签切换回调", changed === "b" && tabs.active() === "b");
    const hrmWindow = run("js/hrmPicker.js", {});
    const defaults = hrmWindow.OaonesHrmPicker.defaults("department");
    check("部门字段默认适配", defaults.idOf({ id: 7 }) === 7 && defaults.textOf({ name: "研发部" }) === "研发部");
    check("多选切换", hrmWindow.OaonesHrmPicker.toggle(["1"], "2").join(",") === "1,2");
    const aiPickerWindow = run("js/aiPicker.js", {
        document: { readyState: "loading", addEventListener() {} }
    });
    check("AI 选择器公开 API", !!(aiPickerWindow.OaonesAiPicker
        && typeof aiPickerWindow.OaonesAiPicker.create === "function"
        && typeof aiPickerWindow.OaonesAiPicker.mountAll === "function"));
    check("AI 选择器文本转义", aiPickerWindow.OaonesAiPicker.esc("<AI>") === "&lt;AI&gt;");

    console.log("【6】目录与文档选择器");
    const directoryWindow = run("js/directoryPicker.js", {});
    const directoryRows = directoryWindow.OaonesDirectoryPicker.normalizeRows([
        { id: 1, parentId: 0, name: "根目录" },
        { id: 2, parentId: 1, name: "子目录", count: 3 }
    ]);
    const directoryModel = directoryWindow.OaonesDirectoryPicker.buildModel(directoryRows);
    check("目录字段标准化", directoryRows[0].id === "1" && directoryRows[0].parentId === "");
    check("目录层级与路径", directoryModel.children["1"].join(",") === "2"
        && directoryModel.map["2"].path === "根目录 / 子目录");
    const circularRows = directoryWindow.OaonesDirectoryPicker.normalizeRows([
        { id: "a", parentId: "b", name: "A" },
        { id: "b", parentId: "a", name: "B" }
    ]);
    const circularModel = directoryWindow.OaonesDirectoryPicker.buildModel(circularRows);
    check("目录循环关系被断开", circularModel.roots.length > 0 && Object.keys(circularModel.circular).length > 0);
    const directorySelection = directoryWindow.OaonesDirectoryPicker.selection({
        multiple: true,
        maxSelected: 1
    });
    directorySelection.set(["1"]);
    const limited = directorySelection.add({ id: "2", name: "第二个目录" });
    check("目录字符串 ID 与选择上限", directorySelection.has("1")
        && !limited.ok && limited.reason === "max");

    const documentWindow = {
        OaonesDirectoryPicker: directoryWindow.OaonesDirectoryPicker
    };
    run("js/documentPicker.js", documentWindow);
    const documentPage = documentWindow.OaonesDocumentPicker.normalizePage({
        items: [
            { documentId: 21, title: "员工手册", folderId: 1, statusText: "可选择", fileSize: 2048 },
            { docId: 22, name: "只读制度", disabled: true, reason: "无编辑权限" }
        ],
        total: 31,
        page: 2,
        pageSize: 20
    });
    check("文档分页协议标准化", documentPage.total === 31 && documentPage.page === 2
        && documentPage.items[0].id === "21" && documentPage.items[0].sizeText === "2.0 KB");
    check("文档禁用原因保留", documentPage.items[1].selectable === false
        && documentPage.items[1].reason === "无编辑权限");
    check("文档搜索匹配标题与 ID", documentWindow.OaonesDocumentPicker.matchesDocument(documentPage.items[0], "21")
        && documentWindow.OaonesDocumentPicker.matchesDocument(documentPage.items[0], "员工"));
    check("文档选择器公开入口", typeof documentWindow.OaonesDocumentPicker.open === "function"
        && typeof documentWindow.OaonesDocumentPicker.create === "function");
    const documentPickerSource = read("js/documentPicker.js");
    const documentPickerStyle = read("css/style.css");
    check("文档选择器目录按实际层级缩进",
        documentPickerSource.indexOf("--oa-document-picker-dir-depth:") >= 0
        && documentPickerStyle.indexOf("calc(var(--oa-document-picker-dir-depth, 0) * 18px)") >= 0);
    check("文档选择器弹层高于业务页面弹层",
        documentPickerSource.indexOf("modal-mask oa-document-picker-mask") >= 0
        && documentPickerStyle.indexOf(".oa-document-picker-mask") >= 0
        && documentPickerStyle.indexOf("z-index:1000100") >= 0
        && documentPickerStyle.indexOf("z-index:1000101") >= 0);
    check("文档选择器选中项同步复选框样式",
        documentPickerSource.indexOf('(selected ? " is-selected" : "")') >= 0
        && documentPickerStyle.indexOf(
            ".oa-document-picker-option.is-selected .oa-document-picker-check") >= 0);

    const pickerWindow = run("js/picker.js", {});
    const pickerRows = pickerWindow.OaonesPicker.normalizeRows([
        { id: "root", parentId: "", name: "根节点" },
        { id: "child", parentId: "root", name: "子节点", detail: "详情" }
    ]);
    const pickerModel = pickerWindow.OaonesPicker.buildModel(pickerRows);
    check("通用选择器树模型", pickerModel.roots.join(",") === "root"
        && pickerModel.children.root.join(",") === "child"
        && pickerModel.map.child.path === "根节点 / 子节点");
    const pickerSelection = pickerWindow.OaonesPicker.selection({
        multiple: true,
        maxSelected: 1
    });
    pickerSelection.set(["root"]);
    const pickerLimited = pickerSelection.add({ id: "child", name: "子节点" });
    check("通用选择器上限与字符串 ID", pickerSelection.has("root")
        && !pickerLimited.ok && pickerLimited.reason === "max");
    check("通用选择器完整公开入口", typeof pickerWindow.OaonesPicker.open === "function"
        && typeof pickerWindow.OaonesPicker.create === "function"
        && typeof pickerWindow.OaonesPicker.normalizeRows === "function");
    const pickerSource = read("js/picker.js");
    check("通用选择器树状行按层级缩进",
        pickerSource.indexOf("data-depth='") >= 0
        && pickerSource.indexOf("--oa-picker-tree-indent:") >= 0
        && pickerSource.indexOf("padding-left:") >= 0
        && read("css/style.css").indexOf("background-size:var(--oa-picker-tree-indent") >= 0);
    check("通用选择器支持拖拽分栏和减号移除",
        pickerSource.indexOf("data-oa-picker-splitter") >= 0
        && pickerSource.indexOf("view-switch mini oa-picker-display") >= 0
        && pickerSource.indexOf("startSplitDrag") >= 0
        && pickerSource.indexOf("oa-picker-selected-remove") >= 0
        && pickerSource.indexOf(">−</button>") >= 0
        && read("css/style.css").indexOf(".oa-picker-splitter") >= 0
        && read("css/style.css").indexOf(".oa-picker-multi-only[hidden]") >= 0
        && read("css/style.css").indexOf("cursor:col-resize") >= 0);
    check("通用选择器关闭按钮使用统一图标按钮",
        pickerSource.indexOf("class='modal-close'") >= 0
        && pickerSource.indexOf("aria-label='\" + esc(L.close)") >= 0
        && read("css/style.css").indexOf(".modal .modal-close::before") >= 0);
    const userBindPanelSource = read("../appsyn/userBind/userBindPanel.jsp");
    check("企微绑定页人员选择重置函数已定义",
        read("../appsyn/userBind/userBind.jsp").indexOf("resetOaPersonPick();") >= 0
        && userBindPanelSource.indexOf("function resetOaPersonPick()") >= 0
        && userBindPanelSource.indexOf("wxOaPersonPick = {};") >= 0);

    console.log("【7】表格拖拽列宽");
    const dragListeners = {};
    const dragDocument = {
        createElement(tag) {
            return {
                tagName: String(tag).toUpperCase(),
                className: "",
                style: {},
                attributes: {},
                children: [],
                handlers: {},
                classList: classList(""),
                setAttribute(name, value) { this.attributes[name] = String(value); },
                getAttribute(name) { return this.attributes[name] ?? null; },
                appendChild(child) {
                    this.children.push(child);
                    child.parentNode = this;
                    return child;
                },
                addEventListener(type, fn) { this.handlers[type] = fn; }
            };
        },
        body: { classList: classList("") },
        addEventListener(type, fn) { (dragListeners[type] || (dragListeners[type] = [])).push(fn); },
        removeEventListener(type, fn) {
            dragListeners[type] = (dragListeners[type] || []).filter(item => item !== fn);
        }
    };
    function emit(type, event) {
        (dragListeners[type] || []).slice().forEach(fn => fn(event));
    }
    const dragWindow = run("js/table.js", { document: dragDocument });
    function alignCell(tagName, value, align) {
        return {
            tagName,
            textContent: value,
            attributes: align ? { "data-align": align } : {},
            classList: classList(""),
            getAttribute(name) { return this.attributes[name] ?? null; },
            querySelector() { return null; }
        };
    }
    const alignCells = [
        alignCell("TH", "这是一个很长的表头文字", ""),
        alignCell("TD", "短文本", ""),
        alignCell("TD", "这是超过十个字符的正文内容", ""),
        alignCell("TD", "固定右对齐", "right")
    ];
    const alignTable = {
        querySelectorAll() { return alignCells; }
    };
    dragWindow.OaonesTable.align(alignTable, { longTextThreshold: 10 });
    check("表头与短内容默认居中", alignCells[0].classList.contains("oa-cell-align-center")
        && alignCells[1].classList.contains("oa-cell-align-center"));
    check("较长文本默认居左", alignCells[2].classList.contains("oa-cell-align-left"));
    check("data-align 覆盖默认对齐", alignCells[3].classList.contains("oa-cell-align-right"));
    const widths = [120, 160, 200];
    const cells = widths.map(function (width) {
        return {
            style: {},
            offsetWidth: width,
            children: [],
            getBoundingClientRect() { return { width: width }; },
            appendChild(child) { this.children.push(child); }
        };
    });
    const dragTable = {
        id: "dragTable",
        style: {},
        attributes: {},
        offsetWidth: 480,
        classList: classList("grid"),
        tHead: { rows: [{ cells: cells }] },
        setAttribute(name, value) { this.attributes[name] = String(value); },
        getAttribute(name) { return this.attributes[name] ?? null; }
    };
    dragWindow.OaonesTable.resizable(dragTable, { key: "drag-test", min: 60 });
    check("所有表头均生成拖拽把手", cells.every(cell => cell.children.length === 1));
    check("表格切换固定列宽布局", dragTable.attributes["data-col-resize"] === "1"
        && dragTable.style.tableLayout === "fixed");
    const firstHandle = cells[0].children[0];
    firstHandle.handlers.mousedown({
        button: 0,
        clientX: 100,
        preventDefault() {},
        stopPropagation() {}
    });
    emit("mousemove", { clientX: 150, preventDefault() {} });
    emit("mouseup", {});
    check("鼠标拖拽改变列宽", cells[0].style.width === "170px"
        && dragTable.style.width === "530px");
    firstHandle.handlers.keydown({
        key: "ArrowRight",
        shiftKey: false,
        preventDefault() {}
    });
    check("键盘调整列宽", cells[0].style.width === "178px");
    dragWindow.OaonesTable.resizable(dragTable, { key: "drag-test", min: 60 });
    check("重复初始化不重复绑定", cells.every(cell => cell.children.length === 1));

    console.log("【8】依赖方向");
    const publicFiles = fs.readdirSync(path.join(root, "js")).filter(name => name.endsWith(".js"));
    const bad = publicFiles.filter(name => {
        const text = read("js/" + name);
        return /(?:src|href)\s*=\s*["'][^"']*\/oaones\/appsyn\/[^"']+\.(?:js|css)["']/i.test(text)
            || /@import\s+[^;]*\/oaones\/appsyn\//i.test(text);
    });
    check("公共组件不加载 appsyn 的 JS/CSS", bad.length === 0, bad.join(", "));
    const css = read("css/style.css");
    check("公共 CSS 无 appsyn @import", !/@import\s+[^;]*\/oaones\/appsyn\//i.test(css));

    const appsynRoot = path.resolve(root, "..", "appsyn");
    function allJsp(dir) {
        const out = [];
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
            const full = path.join(dir, entry.name);
            if (entry.isDirectory()) { out.push(...allJsp(full)); }
            else if (entry.name.endsWith(".jsp")) { out.push(full); }
        }
        return out;
    }
    const jspFiles = allJsp(appsynRoot);
    const badOrder = jspFiles.filter(file => {
        const text = fs.readFileSync(file, "utf8");
        if (text.indexOf("scripts/js/picker.js") < 0) { return false; }
        const modal = text.indexOf("scripts/js/modal.js");
        const tree = text.indexOf("scripts/js/tree.js");
        const picker = text.indexOf("scripts/js/picker.js");
        return modal < 0 || tree < 0 || !(modal < tree && tree < picker);
    });
    check("选择器页面按 modal -> tree -> picker 加载", badOrder.length === 0,
        badOrder.map(file => path.relative(appsynRoot, file)).join(", "));
    const badContext = jspFiles.filter(file => {
        const text = fs.readFileSync(file, "utf8");
        const api = text.indexOf("scripts/js/api.js?v=");
        const context = text.indexOf("appsyn/script/js/appContext.js?v=");
        return api >= 0 && (context < 0 || context < api);
    });
    check("appsyn API 页面都在 api.js 后加载 appContext.js", badContext.length === 0,
        badContext.map(file => path.relative(appsynRoot, file)).join(", "));
    const badFeature = jspFiles.filter(file => {
        const text = fs.readFileSync(file, "utf8");
        const config = text.indexOf("appsyn/script/js/featureConfig.js?v=");
        const gate = text.indexOf("scripts/js/featureGate.js?v=");
        return gate >= 0 && (config < 0 || config > gate);
    });
    check("featureGate.js 前加载 appsyn featureConfig.js", badFeature.length === 0,
        badFeature.map(file => path.relative(appsynRoot, file)).join(", "));
    const missingAssets = [];
    jspFiles.forEach(file => {
        const text = fs.readFileSync(file, "utf8");
        const re = /<(?:script|link)\b[^>]*(?:src|href)="(\/oaones\/[^"?]+?\.(?:js|css))/g;
        let match;
        while ((match = re.exec(text)) !== null) {
            const local = path.resolve(root, "..", match[1].replace(/^\/oaones\//, ""));
            if (!fs.existsSync(local)) { missingAssets.push(path.relative(appsynRoot, file) + " -> " + match[1]); }
        }
    });
    check("JSP 引用的本地 JS/CSS 均存在", missingAssets.length === 0, missingAssets.join(", "));
    check("公共组件标准类已注册", [".oa-toolbar", ".oa-empty", ".oa-tag", ".oa-kpi-card", ".oa-form-error", ".oa-upload", ".oa-json-viewer", ".oa-multi-select", ".oa-ai-picker", ".oa-panel-modal", ".oa-picker-modal", ".oa-directory-picker-modal", ".oa-document-picker-modal", ".oaones-loading"]
        .every(selector => css.indexOf(selector) >= 0));

    console.log(failures.length ? "\n自检失败 ✗：" + failures.join("；") : "\n全部通过 ✓");
    process.exit(failures.length ? 1 : 0);
})().catch(error => {
    console.error(error);
    process.exit(1);
});
