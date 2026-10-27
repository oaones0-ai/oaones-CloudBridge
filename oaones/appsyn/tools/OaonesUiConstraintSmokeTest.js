/**
 * OAones 冒烟脚本：四条全模块 UI 约束的落地自检。
 *
 *  1) 密码 / 密钥类框编辑时只显示 ****（js/secret.js + 各页接入）；
 *  2) 所有弹出框可按表头拖动（js/picker.js，含登录页 .oa-modal）；
 *  3) 选择框弹出页（树状选择页）与其中列表降低高度、更紧凑（css/style.css）；
 *  4) 说明问号（?）里的正文按 1 / 2 / 3 / 4 分点展示（js/help.js）。
 *
 * 为什么需要它：help.js 的分点解析、picker.js 的拖动接管、secret.js 的掩码取值
 * 都是“全模块共用”的一段代码——一旦抛错，表现是“说明/弹框/密钥框在几十个页面上一起坏”，
 * 静态审阅看不出来。这里用最小 DOM 沙箱把这三份脚本真跑一遍，并对样式、跨页引用做静态核对。
 *
 * 用法（本机装了 node 即可；纯静态自检，不连数据库、不依赖容器）：
 *   node ecology/oaones/appsyn/tools/OaonesUiConstraintSmokeTest.js
 * 退出码 0 = 通过；非 0 = 失败（会打印失败原因）。
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const failures = [];
function check(label, cond) {
    console.log('  ' + label + ': ' + (cond ? '✓' : '✗'));
    if (!cond) { failures.push(label); }
}
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function listJsp(dir) {
    const out = [];
    (fs.readdirSync(path.join(root, dir), { withFileTypes: true }) || []).forEach(e => {
        const rel = dir ? dir + '/' + e.name : e.name;
        if (e.isDirectory()) { out.push(...listJsp(rel)); }
        else if (e.name.endsWith('.jsp')) { out.push(rel); }
    });
    return out;
}

/* ==========================================================================
 * 最小 DOM 沙箱：够跑 js/help.js、js/picker.js、js/secret.js 即可
 * ========================================================================== */
const VOID_TAGS = new Set(['br', 'hr', 'img', 'input', 'meta', 'link']);
const ENTITIES = { '&lt;': '<', '&gt;': '>', '&amp;': '&', '&quot;': '"', '&#39;': "'", '&nbsp;': '\u00a0' };
function decode(s) { return s.replace(/&(lt|gt|amp|quot|#39|nbsp);/g, m => ENTITIES[m]); }

function makeDocument() {
    function textNode(value) {
        return {
            nodeType: 3, nodeValue: value, parentNode: null,
            get textContent() { return this.nodeValue; },
            cloneNode() { return textNode(this.nodeValue); }
        };
    }
    function element(tag) {
        const el = {
            nodeType: 1, tagName: String(tag).toUpperCase(), childNodes: [], attrs: {}, parentNode: null,
            style: {}, value: '', type: '', disabled: false, innerHTML: '',
            get textContent() { return this.childNodes.map(c => c.textContent || '').join(''); },
            get className() { return this.attrs.class || ''; },
            set className(v) { this.attrs.class = v; },
            classList: {
                add(c) { el.attrs.class = ((el.attrs.class || '') + ' ' + c).trim(); },
                remove(c) { el.attrs.class = (el.attrs.class || '').split(/\s+/).filter(x => x && x !== c).join(' '); },
                contains(c) { return (el.attrs.class || '').split(/\s+/).indexOf(c) >= 0; },
                toggle(c, on) { if (on) { el.classList.add(c); } else { el.classList.remove(c); } }
            },
            appendChild(node) { node.parentNode = el; el.childNodes.push(node); return node; },
            removeChild(node) { el.childNodes = el.childNodes.filter(c => c !== node); return node; },
            setAttribute(k, v) { el.attrs[k] = String(v); },
            getAttribute(k) { return Object.prototype.hasOwnProperty.call(el.attrs, k) ? el.attrs[k] : null; },
            _listeners: {},
            addEventListener(ev, fn) { el._listeners[ev] = el._listeners[ev] || []; el._listeners[ev].push(fn); },
            removeEventListener() {},
            closest() { return null; },
            contains(node) { return el.childNodes.indexOf(node) >= 0; },
            querySelector() { return null; },
            querySelectorAll() { return []; },
            getBoundingClientRect() { return { left: 100, top: 100, right: 700, bottom: 500, width: 600, height: 400 }; },
            focus() {}, select() {},
            get offsetWidth() { return 600; },
            get offsetHeight() { return 400; },
            cloneNode(deep) {
                const copy = element(tag);
                copy.attrs = Object.assign({}, el.attrs);
                if (deep) { el.childNodes.forEach(c => copy.appendChild(c.cloneNode(true))); }
                return copy;
            }
        };
        return el;
    }
    /** 够用的 HTML → 迷你 DOM（只支持本模块说明正文里用到的写法） */
    function parseInto(container, html) {
        const re = /<\/?([a-zA-Z][a-zA-Z0-9]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/g;
        const stack = [container];
        let last = 0, m;
        const top = () => stack[stack.length - 1];
        while ((m = re.exec(html))) {
            const text = html.slice(last, m.index);
            if (text) { top().appendChild(textNode(decode(text))); }
            last = re.lastIndex;
            const closing = m[0].charAt(1) === '/';
            const tag = m[1].toLowerCase();
            if (closing) { if (stack.length > 1) { stack.pop(); } continue; }
            const el = element(tag);
            (m[2] || '').replace(/([a-zA-Z-]+)\s*=\s*"([^"]*)"/g, (all, k, v) => { el.setAttribute(k, v); return all; });
            top().appendChild(el);
            if (!VOID_TAGS.has(tag) && !/\/>$/.test(m[0])) { stack.push(el); }
        }
        const rest = html.slice(last);
        if (rest) { top().appendChild(textNode(decode(rest))); }
        return container;
    }

    const byId = {};
    const doc = {
        readyState: 'complete',
        _handlers: {},
        body: element('body'),
        createElement: tag => element(tag),
        createTextNode: textNode,
        getElementById: id => byId[id] || null,
        register: (id, el) => { byId[id] = el; return el; },
        /** 用一段 HTML 造一个带 id 的节点（模拟页面里的 .help-source / .modal） */
        make: (tag, attrs, html) => {
            const el = element(tag);
            Object.keys(attrs || {}).forEach(k => el.setAttribute(k, attrs[k]));
            if (html) { parseInto(el, html); }
            if (el.getAttribute('id')) { byId[el.getAttribute('id')] = el; }
            return el;
        },
        addEventListener(ev, fn) {
            doc._handlers[ev] = doc._handlers[ev] || [];
            doc._handlers[ev].push(fn);
        },
        querySelector: () => null,
        querySelectorAll: sel => (sel === '.help-tip' ? doc._tips || [] : []),
        _fire(ev, arg) { (doc._handlers[ev] || []).forEach(fn => fn(arg)); }
    };
    return doc;
}

/** 生成一个跑给定脚本的环境（document / window 齐备） */
function makeEnv(doc) {
    const win = {
        document: doc, innerWidth: 1280, innerHeight: 800, getComputedStyle: () => ({ position: 'static' }),
        _handlers: {},
        addEventListener(ev, fn) { win._handlers[ev] = win._handlers[ev] || []; win._handlers[ev].push(fn); },
        setTimeout: () => 0, clearTimeout: () => {}
    };
    win.window = win;
    return win;
}
function runScript(win, rel) {
    vm.runInNewContext(read(rel), { window: win, document: win.document, setTimeout: win.setTimeout, clearTimeout: win.clearTimeout, console }, { filename: rel });
}

/* ==========================================================================
 * 1) 说明正文分点展示（js/help.js）
 * ========================================================================== */
console.log('【1】帮助（?）正文按 1 / 2 / 3 / 4 分点展示：js/help.js');
{
    const doc = makeDocument();
    // 真实的三种写法各取一段：逐行正文（租户设置）/ 段落块（登录页）/ 自带序号（同步中心）
    const src = doc.make('div', { class: 'help-source', id: 'idxHelpText' },
        '本页是模块首页，只展示<b>块状企微租户</b>：每个块对应一个独立企业微信主体（corpid + Secret）。\n'
        + '你是<b>系统管理员</b>（user.id=1）或超管角色成员，可查看与管理全部租户。\n'
        + '<p><b>账号密码登录</b>：与平台登录完全同一套校验；要不要填验证码取后台设置。</p>'
        + '<p><b>忘记密码</b>：在本页弹出的窗口里自助找回。</p>'
        + '<div class="help-line">邮箱：OA 邮箱与企业微信邮箱一致；</div>'
        + '<div class="help-line">手机号：OA 手机号与企业微信手机号一致。</div>'
        + '1）<span class="rule">列表一行一条规则</span>：首列就是租户名称。<br>'
        + '1.1）一个“租户 + 方向”可以保存多条规则。<br>');
    doc.body.appendChild(src);

    const tip = doc.make('span', { class: 'help-tip', id: 'idxHelpTip', 'data-help': 'idxHelpText' });
    tip.closest = sel => (sel === '.help-tip' ? tip : null);
    doc._tips = [tip];

    let win = makeEnv(doc);
    try {
        runScript(win, 'js/help.js');
        check('js/help.js 执行不抛错', true);
    } catch (e) {
        check('js/help.js 执行不抛错（实际抛错：' + e.message + '）', false);
    }

    // 触发说明面板（help.js 通过 document 上的 mouseover 委托显示）
    const pop = (() => {
        doc._fire('mouseover', { target: tip });
        return doc.body.childNodes.filter(n => n.nodeType === 1 && n.classList.contains('help-pop'))[0];
    })();
    check('说明面板已生成', !!pop);
    const ol = pop && pop.childNodes.filter(n => n.nodeType === 1 && n.tagName === 'OL')[0];
    check('正文被渲染成有序列表 <ol class="help-points">', !!(ol && ol.classList.contains('help-points')));
    const items = ol ? ol.childNodes.filter(n => n.nodeType === 1 && n.tagName === 'LI') : [];
    const texts = items.map(li => li.textContent.replace(/\s+/g, ''));
    console.log('     分点结果：' + texts.length + ' 点 → ' + texts.map(t => t.slice(0, 16)).join(' | '));
    // 逐行正文 2 点 + <p> 各 1 点 2 个 + <div class="help-line"> 各 1 点 2 个 + 自带序号的两条 2 点
    check('分点数量 = 8（段落块各一点、句号再断点）', texts.length === 8);
    check('识别并去掉了自带序号“1）”', texts.indexOf('列表一行一条规则：首列就是租户名称。') >= 0);
    check('识别并去掉了自带序号“1.1）”', texts.filter(t => t.indexOf('一个“租户+方向”可以保存多条规则。') >= 0).length === 1);
    check('行内标记 <b> / <span class="rule"> 原样保留',
        items.length > 1 && items[0].childNodes.some(n => n.nodeType === 1 && n.tagName === 'B')
        && items.some(li => li.childNodes.some(n => n.nodeType === 1 && n.classList.contains('rule'))));
}
console.log('');

/* ==========================================================================
 * 2) 密码 / 密钥框：编辑时只显示 ****（js/secret.js）
 * ========================================================================== */
console.log('【2】密码 / 密钥框编辑时显示 ****：js/secret.js');
{
    const doc = makeDocument();
    const win = makeEnv(doc);
    const el = doc.make('input', { id: 'corp_secret', type: 'password' });
    try {
        runScript(win, 'js/secret.js');
        check('js/secret.js 执行不抛错', true);
    } catch (e) {
        check('js/secret.js 执行不抛错（实际抛错：' + e.message + '）', false);
    }
    const S = win.OaonesSecret;
    check('暴露 OaonesSecret.reset / bind / value', !!(S && S.reset && S.bind && S.value));
    if (S) {
        S.bind(el);
        S.reset(el, true);
        check('编辑已有密钥：字段显示 ****', el.value === '****' && el.classList.contains('oa-secret-masked'));
        check('掩码态提交 = 不修改（返回空串）', S.value(el) === '');

        doc._handlers = {};
        doc._fire('mouseover', { target: el }); // 无关事件不应影响字段
        // 用户点进输入：掩码让开、切回密码态
        win.document.getElementById('corp_secret');
        const focus = (el._listeners || {}).focus;
        check('掩码态绑定了 focus/blur', !!focus);

        S.reset(el, false);
        check('新增记录：字段是空框（不显示 ****）', el.value === '' && !el.classList.contains('oa-secret-masked'));
    }
}
console.log('');

/* ==========================================================================
 * 3) 弹出框按表头拖动（js/picker.js，含登录页 .oa-modal）
 * ========================================================================== */
console.log('【3】所有弹出框可按表头拖动：js/picker.js');
{
    const doc = makeDocument();
    const win = makeEnv(doc);
    win.OaonesPicker = undefined;
    // 两个弹框：模块通用 .modal（已是 fixed）与登录页 .oa-modal（遮罩 flex 居中、静态定位）
    const modal = doc.make('div', { class: 'modal', id: 'editModal' });
    const modalHead = doc.make('div', { class: 'modal-head' });
    modal.appendChild(modalHead);
    const oaModal = doc.make('div', { class: 'oa-modal', id: 'loginForgotBox' });
    const oaHead = doc.make('div', { class: 'oa-modal-head' });
    oaModal.appendChild(oaHead);
    modalHead.closest = sel => (sel.indexOf('.modal-head') >= 0 || sel.indexOf('.oa-modal-head') >= 0 ? modalHead
        : (sel === '.modal, .oa-modal' ? modal : null));
    oaHead.closest = sel => (sel.indexOf('.oa-modal-head') >= 0 ? oaHead
        : (sel === '.modal, .oa-modal' ? oaModal : null));

    try {
        runScript(win, 'js/picker.js');
        check('js/picker.js 执行不抛错', true);
    } catch (e) {
        check('js/picker.js 执行不抛错（实际抛错：' + e.message + '）', false);
    }
    const handlers = doc._handlers.mousedown || [];
    check('已接管 document 上的 mousedown（弹框拖动）', handlers.length > 0);
    const down = target => handlers.forEach(fn => fn({
        button: 0, clientX: 300, clientY: 200, preventDefault() {}, target
    }));
    down(modalHead);
    check('.modal：按住表头即开始拖动（left/top 已定位）',
        modal.style.left === '100px' && modal.style.top === '100px' && modal.classList.contains('modal-dragging'));
    down(oaHead);
    check('登录页 .oa-modal：同样可按表头拖动', oaModal.style.left === '100px' && oaModal.style.top === '100px');
    check('静态定位弹框拖动时补成 fixed（否则 left/top 不生效）', oaModal.style.position === 'fixed');
    down(Object.assign(doc.make('button', { class: 'modal-x' }), {
        closest: sel => (sel.indexOf('.modal-head') >= 0 || sel.indexOf('.oa-modal-head') >= 0 ? modalHead
            : (sel === '.modal, .oa-modal' ? modal : null))
    }));
    check('表头上的按钮（关闭）不触发拖动', modal.style.left === '100px');
}
console.log('');

/* ==========================================================================
 * 4) 选择框弹出页与下方列表：高度更低、更紧凑（css/style.css）
 * ========================================================================== */
console.log('【4】选择框弹出页 / 列表降高：css/style.css');
{
    const css = read('css/style.css');
    const heights = [...css.matchAll(/\.wx-pick-list\s*\{[^}]*max-height:\s*(\d+)px/g)].map(m => Number(m[1]));
    const finalHeight = heights.length ? heights[heights.length - 1] : null;
    console.log('     .wx-pick-list max-height：' + heights.join(' → '));
    check('选择框列表限高已压到 240px 以内（原 300px）', finalHeight !== null && finalHeight <= 240);
    check('选择弹框内边距 / 行距已收紧（.wx-pick-modal .modal-body）',
        /\.wx-pick-modal\s+\.modal-body\s*\{[^}]*padding:\s*16px 20px/.test(css));
    check('选项行内边距已收紧（.wx-pick-option / .mt-pick-row）',
        /\.wx-pick-option,\s*\.mt-pick-row\s*\{[^}]*padding:\s*7px 11px/.test(css));
    check('说明面板：分点编号样式与限高滚动已就位',
        /\.help-pop \.help-points/.test(css) && /\.help-pop\s*\{[^}]*max-height/.test(css));
    check('密钥掩码态样式已就位', /\.oa-secret-masked/.test(css));

    // 叶子占位箭头：类名 `.leaf`，绝不能再用 `.empty`——`.empty` 是空状态块（padding:56px 0），
    // 同名会把“树状映射”的人员行、选择框树里的叶子行上下各撑 56px（比上级行高一大截）。
    const caretEmits = ['js/picker.js', 'userBind/userTreePanel.jsp', 'deptBind/deptTreePanel.jsp',
        'snapshot/snapshot.jsp'];
    check('叶子占位箭头不再撞名空状态类 .empty（页面 / 脚本统一 .tree-caret.leaf）',
        caretEmits.every(f => !/tree-caret empty/.test(read(f)))
        && /class='tree-caret leaf'/.test(read('js/picker.js')));
    check('叶子占位箭头不被 .empty 的空状态留白撑高（含历史写法兜底）',
        /\.tree-caret\.leaf,\s*\.tree-caret\.empty\s*\{[^}]*padding:\s*0[^}]*font-size:\s*11px/.test(css));
    check('选择框候选行统一行盒高度（选项行 / 人员行 / 分组行同高）',
        /\.wx-pick-option,\s*\.mt-pick-row,\s*\.wx-pick-group\s*\{[^}]*line-height:\s*normal/.test(css));
    check('树状映射三种行统一最小行高（分部 / 部门 / 人员），人员行不再单独压内边距',
        /\.map-tree\s+\.map-row\s*>\s*\.map-col\s*\{[^}]*min-height:\s*\d+px/.test(css)
        && !/\.map-row-user\s+\.map-col-oa[^{]*\{[^}]*padding-top/.test(css));
    // 树的点击判断也必须跟着改成 .leaf：占位箭头没有 data-node，不能再按 .empty 判（那是空状态类）
    check('树行点击判断按 .tree-caret.leaf 识别占位箭头（没有遗留 .empty 判断）',
        caretEmits.every(f => !/classList[^\n]*contains\(\s*"empty"/.test(read(f))));
    check('绑定总览分组行与明细行同高（同内边距 + 统一最小行高）',
        /\.ov-group,\s*\.ov-row\s*\{[^}]*min-height:\s*\d+px/.test(css)
        && /\.ov-group\s*\{[^}]*padding:\s*7px 12px/.test(css)
        && /\.ov-row\s*>\s*div\s*\{[^}]*padding:\s*7px 12px/.test(css));
}
console.log('');

/* ==========================================================================
 * 5) 跨页接入：有密钥框 / 有弹框的页面都引了对应公共脚本
 * ========================================================================== */
console.log('【5】跨页接入核对');
{
    const all = listJsp('');
    const jspFiles = new Set(all);

    // 密钥框：这三个页面的密钥输入必须按掩码口径接入
    ['index/TenantIndex.jsp', 'appBind/WeCom/appBindManagePanel.jsp', 'appBind/DingTalk/appBindManagePanel.jsp',
        'appBind/Feishu/appBindManagePanel.jsp', 'personalWx/config.jsp'].forEach(f => {
        const src = read(f);
        check(f + '：用 OaonesSecret.reset / value 接管密钥框',
            /OaonesSecret\.(reset|value)\(/.test(src));
    });
    ['index/TenantIndex.jsp', 'appBind/WeCom/appBind.jsp', 'appBind/DingTalk/appBind.jsp',
        'appBind/Feishu/appBind.jsp', 'personalWx/config.jsp'].forEach(f => {
        check(f + '：引了 js/secret.js', /js\/secret\.js/.test(read(f)));
    });

    // 弹框拖动：含 .modal-mask 的页面，本目录或上一级目录得有引了 picker.js 的页壳
    const hosts = all.filter(f => /class="modal-mask"/.test(read(f)));
    const withPicker = new Set(all.filter(f => /js\/picker\.js/.test(read(f))));
    const missing = hosts.filter(f => {
        const dirs = [path.dirname(f), path.dirname(path.dirname(f))];
        return dirs.every(d => ![...withPicker].some(p => path.dirname(p) === (d === '.' ? '.' : d)));
    });
    console.log('     含弹框的页面：' + hosts.length + ' 个；未接入拖动：' + (missing.length ? missing.join(' / ') : '无'));
    check('含弹框的页面都能拖表头（本目录或上级目录的页壳引了 picker.js）', missing.length === 0);
    check('assets.jsp 已把 secret.js 计入版本号链', /oaonesSecretJsUrl/.test(read('assets.jsp')));
}
console.log('');

console.log(failures.length ? ('自检失败 ✗：' + failures.join('；')) : '自检通过 ✓');
process.exit(failures.length ? 1 : 0);
