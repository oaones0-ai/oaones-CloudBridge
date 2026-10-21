/**
 * OAones 冒烟脚本：「会议同步 / 日程同步」页（meetingSync/meetingSync.jsp + meetingSync/mtgSyncPanel.jsp
 * + js/mtgSync.js）里第四个页签「个人同步方向」的接线自检。
 *
 * 为什么需要它：这页由三个文件拼成（页壳 JSP 内联脚本 + 面板 JSP 的标记 + 外链 js/mtgSync.js），
 * 脚本在 bindEvents() 里对每个节点做 byId(...).addEventListener(...)。一旦某个 id 只写在面板里、
 * 而脚本按老 id 找（或反过来），页面初始化就会抛
 * `Cannot read properties of null (reading 'addEventListener')`，整段脚本中断，
 * 表现是“页签点不动、搜索没反应”——静态检查（语法 / id 配对）看不出来。
 * 所以这里用最小 DOM 沙箱把页面跑一遍，检查：
 *   ① 脚本执行不抛错；② 切到「个人同步方向」页签会调 mtgsync.dirUserList（带分部 / 部门 / 方向参数）；
 *   ③ 点“分部 / 部门”筛选框能打开多选弹框、选中后回显；④ 页签顺序里「个人同步方向」在「同步记录」后面。
 *
 * 用法（本机装了 node 即可；纯静态自检，不连数据库、不依赖容器）：
 *   node ecology/oaones/tools/MtgSyncDirTabSmokeTest.js
 * 退出码 0 = 通过；非 0 = 失败（会打印失败原因）。
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const shell = fs.readFileSync(path.join(root, 'meetingSync', 'meetingSync.jsp'), 'utf8');
const panel = fs.readFileSync(path.join(root, 'meetingSync', 'mtgSyncPanel.jsp'), 'utf8');
const script = fs.readFileSync(path.join(root, 'js', 'mtgSync.js'), 'utf8');

const failures = [];
function check(label, cond) {
    console.log('  ' + label + ': ' + (cond ? '✓' : '✗'));
    if (!cond) { failures.push(label); }
}

/* ---------- 静态：页签顺序（个人同步方向 在 同步记录 之后） ---------- */
const tabsAt = shell.indexOf('id="mtgTabs"');
const tabBlock = shell.slice(tabsAt, shell.indexOf('</div>', tabsAt));
const tabOrder = [...tabBlock.matchAll(/data-tab="([a-z]+)"/g)].map(m => m[1]);
console.log('页签顺序：' + tabOrder.join(' → '));
check('页签顺序 = setting,item,log,diruser', tabOrder.join(',') === 'setting,item,log,diruser');
const panelOrder = [...panel.matchAll(/<div class="tab-panel[^"]*" data-mtg-panel="([a-z]+)"/g)].map(m => m[1]);
console.log('面板页签块顺序：' + panelOrder.join(' → '));
check('面板里有「个人同步方向」页签块（data-mtg-panel="diruser"）', panelOrder.indexOf('diruser') >= 0);
check('「个人同步方向」页签块紧跟在同步记录页签块后面',
    panelOrder.join(',') === 'setting,item,log,diruser');
console.log('');

/* ---------- 最小 DOM 沙箱：把三份文件拼起来跑一遍 ---------- */
function run() {
    const ids = new Set([...(shell + panel).matchAll(/id="([A-Za-z0-9_]+)"/g)].map(m => m[1]));
    const handlers = {};
    const cache = {};
    const apiCalls = [];

    const tabButtons = tabOrder.map(t => ({
        _tab: t,
        classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
        getAttribute() { return this._tab; },
        addEventListener(ev, fn) { handlers['tab:' + this._tab] = fn; }
    }));
    const panelEls = ['setting', 'item', 'log', 'diruser'].map(k => ({
        _panel: k,
        classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
        getAttribute() { return this._panel; }
    }));

    function makeEl(id) {
        const el = {
            id, style: {}, value: '', checked: false, textContent: '', innerHTML: '', disabled: false,
            className: '', rows: [],
            classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
            addEventListener(ev, fn) {
                handlers[id] = handlers[id] || {};
                handlers[id][ev] = handlers[id][ev] || [];
                handlers[id][ev].push(fn);
            },
            querySelector() { return makeEl(id + '-q'); },
            querySelectorAll() { return []; },
            getAttribute() { return ''; },
            setAttribute() {},
            closest() { return null; },
            insertAdjacentHTML() {},
            appendChild() {},
            focus() {},
        };
        el.parentNode = { classList: { toggle() {} }, removeChild() {} };
        return el;
    }

    const document = {
        getElementById: id => (ids.has(id) ? (cache[id] = cache[id] || makeEl(id)) : null),
        addEventListener(ev, fn) {
            handlers['#document'] = handlers['#document'] || {};
            handlers['#document'][ev] = handlers['#document'][ev] || [];
            handlers['#document'][ev].push(fn);
        },
        createElement: () => makeEl('created'),
        querySelector: () => null,
        querySelectorAll: sel => (sel.indexOf('#mtgTabs') >= 0 ? tabButtons
            : (sel.indexOf('data-mtg-panel') >= 0 ? panelEls : [])),
        readyState: 'complete',
    };

    /** 各 action 的假返回：够页面渲染即可（列表都为空，避免依赖真实数据） */
    const fake = {
        'mtgsync.list': { code: 0, data: [] },
        'mtgsync.plan': { code: 0, data: {} },
        'mtgsync.scheduler': { code: 0, data: {} },
        'mtgsync.orgOptions': { code: 0, data: {
            oa_subcompanies: [{ id: '55', name: '测试分部' }, { id: '56', name: '下级分部', parent: '55' }],
            oa_departments: [{ id: '77', name: '测试部门', subcompanyid1: '55' }] } },
        'mtgsync.dirUserList': { code: 0, data: { rows: [] } },
    };
    const apiAt = (base, action, params, cb) => {
        apiCalls.push({ action, params });
        cb(fake[action] || { code: 0, data: {} });
    };

    const sandbox = {
        window: null, document, console, parseInt, String, Number, Date, isNaN, encodeURIComponent,
        OAONES_USER: { id: '1', sysAdmin: true },
        Oaones: { api: apiAt, apiAt, esc: v => String(v == null ? '' : v) },
        OaonesToast: { show() {}, confirm(fn) { fn && fn(); }, system() {}, loading() {}, hideLoading() {} },
        OaonesTips: { pick: (a, b) => b, isSysAdmin: () => true },
        OaonesHrm: { user: (id, n) => n || '', dept: (id, n) => n || '', sub: (id, n) => n || '' },
        OaonesDate: { init() {}, clear() {} },
        OaonesTable: {
            pager: () => ({ apply: r => r || [], render() {}, hide() {}, show() {}, resetIfChanged() {} }),
            resizable() {}, syncCheckAll() {}, crossPageBar() {}, listActionsHtml: () => '',
        },
        OaonesPicker: {
            mode: () => 'list', applyPrecondition: (k, r) => r || [], partialSelected: () => ({}),
            renderTree: () => '', resetTree() {}, preconditionTip() {}, setPrecondition() {},
            register() {}, setSelectAll() {}, syncSelectAll() {},
        },
    };
    sandbox.window = {
        document, location: { href: '', replace() {} }, addEventListener() {}, Oaones: sandbox.Oaones,
        OAONES_MTG_BIZ: 0, OAONES_MTG_TYPE: 0, OAONES_MTG_LABEL: '会议', OAONES_MTG_TITLE: '会议同步',
        OAONES_MTG_TAB: 'setting', OAONES_API_URL: '/oaones/WeCom/api.jsp',
        OAONES_CFG_ALL_LABEL: '全部企微租户', OAONES_USER: sandbox.OAONES_USER,
    };

    // 页壳内联脚本：去掉 JSP 表达式 / 脚本块，只保留可执行 JS
    const shellJs = [...shell.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)]
        .map(m => m[1]).join('\n')
        .replace(/<%=[^%]*%>/g, '0')
        .replace(/<%.*?%>/gs, '');

    try {
        vm.runInNewContext(script, sandbox, { filename: 'mtgSync.js' });
        vm.runInNewContext(shellJs, sandbox, { filename: 'meetingSync.jsp.js' });
    } catch (e) {
        check('脚本执行不抛错（实际抛错：' + e.message + '）', false);
        return;
    }
    check('脚本执行不抛错', true);

    // ① 切到「个人同步方向」页签：应调 dirUserList（带分部 / 部门 / 方向参数）
    const click = (id, ev) => ((handlers[id] || {}).click || [])
        .forEach(fn => fn(Object.assign({ target: { closest: () => null }, stopPropagation() {} }, ev || {})));

    const dirTab = handlers['tab:diruser'];
    check('「个人同步方向」页签已绑定点击事件', !!dirTab);
    if (dirTab) { dirTab.call({ getAttribute: () => 'diruser' }, {}); }
    const dirCall = apiCalls.filter(c => c.action === 'mtgsync.dirUserList').pop();
    check('切页签会调 mtgsync.dirUserList', !!dirCall);
    check('请求参数带 sub_ids / dept_ids / direction',
        !!dirCall && 'sub_ids' in dirCall.params && 'dept_ids' in dirCall.params
        && 'direction' in dirCall.params);

    // ② 点“分部”筛选框：打开分部多选弹框
    click('mtgDirSubPicker');
    check('点“分部”筛选框打开分部弹框',
        !!(cache['mtgDirSubModal'] && cache['mtgDirSubModal'].style.display === 'block'));

    // ③ 弹框里选一行 → 确定 → 输入框回显“测试分部”，并重查列表
    const option = { getAttribute: () => '55' };
    ((handlers['mtgDirSubList'] || {}).click || []).forEach(fn => fn({
        target: { closest: sel => (sel === '.wx-pick-option' ? option : null) }
    }));
    click('mtgDirSubOk');
    check('选完分部后选择框回显候选名称',
        !!(cache['mtgDirSubInput'] && String(cache['mtgDirSubInput'].value).indexOf('测试分部') >= 0));
    const afterPick = apiCalls.filter(c => c.action === 'mtgsync.dirUserList').pop();
    check('选完分部后重查列表并带上 sub_ids=55',
        !!afterPick && String(afterPick.params.sub_ids).indexOf('55') >= 0);

    // ④ 点“部门”筛选框：打开部门多选弹框
    click('mtgDirDeptPicker');
    check('点“部门”筛选框打开部门弹框',
        !!(cache['mtgDirDeptModal'] && cache['mtgDirDeptModal'].style.display === 'block'));
}

console.log('自检文件：' + path.join(root, 'meetingSync', 'meetingSync.jsp'));
run();
console.log('');
console.log(failures.length ? ('自检失败 ✗：' + failures.join('；')) : '自检通过 ✓');
process.exit(failures.length ? 1 : 0);
