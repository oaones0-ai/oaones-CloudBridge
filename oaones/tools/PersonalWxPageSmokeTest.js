/**
 * OAones 冒烟脚本：「微信设置」页（personalWx/config.jsp）页面脚本的两视图自检。
 *
 * 为什么需要它：这张页面的一级结构会随**地址条件**变化——
 *   不带 ?showSetting=true 时**只渲染下方的个人微信绑定列表**（“微信授权密钥”卡片整块不输出，见 JSP 的
 *   oaonesPwShowSetting）；带 ?showSetting=true 时才多出那张卡片。
 * 于是“只属于设置卡片”的节点（#pwSaveBtn / #pwAppId / #pwAppSecret …）在不带参数时**并不存在**。
 * 一旦脚本里出现 `byId("pwSaveBtn").addEventListener(...)` 这种裸绑定，不带参数时就会抛
 * `Cannot read properties of null (reading 'addEventListener')`，**整段脚本中断**，
 * 后面所有事件（三个搜索框、列表行内按钮、扫码弹框）都注册不上——表现就是“点搜索框没反应”。
 * 这类问题静态检查（语法/配对/id）看不出来，所以用最小 DOM 沙箱把两种视图各跑一遍：
 *   ① 脚本不抛错；② 点“分部 / 部门 / 人员”三个搜索框确实打开了对应弹框；③ 设置卡片不存在时也能正常工作。
 *
 * 用法（本机装了 node 即可；纯静态自检，不连数据库、不依赖容器）：
 *   node ecology/oaones/tools/PersonalWxPageSmokeTest.js
 *   node ecology/oaones/tools/PersonalWxPageSmokeTest.js /path/to/config.jsp
 * 退出码 0 = 两视图都通过；非 0 = 有视图失败（会打印失败原因）。
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const jspPath = process.argv[2] ||
    path.resolve(__dirname, '..', 'personalWx', 'config.jsp');
const jsp = fs.readFileSync(jspPath, 'utf8');
// 页面依赖的公共脚本：与浏览器里的加载顺序一致，先在沙箱里执行一遍
const secretJs = fs.readFileSync(path.resolve(__dirname, '..', 'js', 'secret.js'), 'utf8');

/** 收集页面上的 id；hidden 模式去掉“微信授权密钥”卡片（if (oaonesPwShowSetting) { … }）内的 id。 */
function idsOf(hidden) {
    const all = [...jsp.matchAll(/id="([^"<]+)"/g)].map(m => m[1]);
    const start = jsp.indexOf('<% if (oaonesPwShowSetting) { %>');
    const end = jsp.indexOf('<% } %>', start);
    const settings = new Set([...jsp.slice(start, end).matchAll(/id="([^"<]+)"/g)].map(m => m[1]));
    return new Set(hidden ? all.filter(i => !settings.has(i)) : all);
}

/** 用最小 DOM 沙箱跑一次页面脚本，返回执行结果与点击验证结果。 */
function run(hidden) {
    const ids = idsOf(hidden);
    const handlers = {};
    const cache = {};
    const makeEl = id => {
        const el = {
            id, style: {}, value: '', checked: true, textContent: '', innerHTML: '', disabled: false, className: '',
            classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
            addEventListener(ev, fn) {
                handlers[id] = handlers[id] || {};
                (handlers[id][ev] = handlers[id][ev] || []).push(fn);
            },
            querySelector() { return makeEl(id + '-inner'); },
            querySelectorAll() { return []; },
            getAttribute() { return ''; }, setAttribute() {}, closest() { return null; },
            appendChild() {}, focus() {}, select() {},
        };
        el.parentNode = { classList: { toggle() {} } };
        return el;
    };
    const document = {
        getElementById: id => (ids.has(id) ? (cache[id] = cache[id] || makeEl(id)) : null),
        addEventListener(ev, fn) {
            handlers['#document'] = handlers['#document'] || {};
            (handlers['#document'][ev] = handlers['#document'][ev] || []).push(fn);
        },
        createElement: () => makeEl('created'),
        querySelectorAll: () => [],
        readyState: 'complete',
    };
    const okJson = { code: 0, data: {
        items: [], configured: true, total: 0, bound_count: 0, unbound_count: 0, bind_total: 0,
        oa_subcompanies: [], oa_departments: [] } };
    const api = (action, params, cb) => cb(okJson);
    const sandbox = {
        window: null, document, console,
        OAONES_USER: { id: '1', sysAdmin: true },
        Oaones: { api, apiAt: (b, a, p, cb) => cb(okJson), esc: v => String(v == null ? '' : v) },
        OaonesToast: { show() {}, confirm(fn) { fn && fn(); }, system() {} },
        OaonesTips: { pick: (a, b) => b, isSysAdmin: () => true },
        OaonesHrm: { user: (id, n) => n || '', dept: (id, n) => n || '', sub: (id, n) => n || '' },
        OaonesTable: {
            pager: () => ({ apply: r => r || [], render() {}, hide() {}, show() {}, resetIfChanged() {} }),
            resizable() {}, syncCheckAll() {}, crossPageBar() {}, listActionsHtml: () => '',
        },
        OaonesPicker: {
            mode: () => 'list', applyPrecondition: (k, r) => r || [], partialSelected: () => ({}),
            renderTree: () => '', resetTree() {}, preconditionTip() {},
            setPrecondition() {}, register() {}, setSelectAll() {}, syncSelectAll() {},
        },
    };
    sandbox.window = {
        document, location: { href: '', replace() {} }, open: () => ({ close() {}, location: {} }),
        addEventListener() {}, Oaones: sandbox.Oaones,
    };

    let js = [...jsp.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');
    // JSP 表达式：维护人固定为真（否则人员弹框会按“无权限”提前返回），showSetting 按当前视图给值
    js = js.replace(/<%=\s*oaonesPwCanMaintain\s*%>/g, '1')
           .replace(/<%=\s*oaonesPwShowSetting\s*%>/g, hidden ? '0' : '1')
           .replace(/<%=[^%]*%>/g, '0')
           .replace(/<%.*?%>/gs, '');

    const name = hidden ? '不带 showSetting（只显示列表）' : '带 showSetting=true（含密钥卡片）';
    try {
        vm.runInNewContext(secretJs, sandbox, { filename: 'js/secret.js' });
        // 沙箱里 window 不是全局对象（浏览器里两者同一个）：把公共脚本挂在 window 上的能力同步到全局
        sandbox.OaonesSecret = sandbox.window.OaonesSecret;
        vm.runInNewContext(js, sandbox, { filename: path.basename(jspPath) + '.js' });
    } catch (e) {
        console.log('[' + name + '] 脚本执行：抛错 ✗ -> ' + e.message);
        return false;
    }
    console.log('[' + name + '] 脚本执行：未抛错 ✓');

    let ok = true;
    const click = id => ((handlers[id] || {}).click || [])
        .forEach(fn => fn({ target: { closest: () => null }, stopPropagation() {} }));
    const check = (label, cond) => {
        console.log('  点“' + label + '”搜索框 -> 弹框打开: ' + (cond ? '✓' : '✗'));
        if (!cond) { ok = false; }
    };
    const shown = id => !!(cache[id] && cache[id].style && cache[id].style.display === 'block');

    click('pwSubPicker');
    check('分部', shown('pwOrgModal'));
    if (cache['pwOrgModal']) { cache['pwOrgModal'].style.display = 'none'; }
    click('pwDeptPicker');
    check('部门', shown('pwOrgModal'));
    if (cache['pwOrgModal']) { cache['pwOrgModal'].style.display = 'none'; }
    click('pwPersonPicker');
    check('人员', shown('pwPersonModal'));

    const hasSaveBtn = !!handlers['pwSaveBtn'];
    console.log('  “保存密钥”按钮已绑定:', hidden
        ? (hasSaveBtn ? '✗（不带参数时不该存在）' : '否（未渲染，符合预期）✓')
        : (hasSaveBtn ? '是 ✓' : '✗（带参数时应存在）'));
    if (hidden ? hasSaveBtn : !hasSaveBtn) { ok = false; }
    return ok;
}

console.log('自检文件：' + jspPath);
const okHidden = run(true);
console.log('');
const okShown = run(false);
console.log('');
console.log((okHidden && okShown) ? '两视图自检通过 ✓' : '自检失败 ✗');
process.exit((okHidden && okShown) ? 0 : 1);
