/**
 * 个人账号中心页签冒烟检查。
 *
 * 回归点：平台接口返回“无租户、无绑定”时，只允许在面板内显示空状态，
 * 不允许把企业微信 / 钉钉 / 飞书页签按钮设为 display:none，否则点击后页签会消失。
 *
 * 用法：node ecology/oaones/appsyn/tools/AccountCenterTabsSmokeTest.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const pagePath = path.join(root, 'accountCenter', 'MyAccount.jsp');
const panelPath = path.join(root, 'accountCenter', 'accountPlatformPanel.jsp');
const page = fs.readFileSync(pagePath, 'utf8');
const panel = fs.readFileSync(panelPath, 'utf8');
const failures = [];

function check(label, condition) {
    console.log('  ' + label + ': ' + (condition ? '✓' : '✗'));
    if (!condition) {
        failures.push(label);
    }
}

console.log('个人账号中心：平台页签点击后保持显示');
['family', 'wecom', 'dingtalk', 'feishu', 'personalwx'].forEach(function (tab) {
    check('存在页签 ' + tab, page.indexOf('data-tab="' + tab + '"') >= 0);
    check('存在面板 ' + tab, page.indexOf('data-tab-panel="' + tab + '"') >= 0);
});
check('不再调用空平台隐藏逻辑', page.indexOf('applyPlatformTab') < 0 && page.indexOf('firstVisibleTab') < 0);
check('不再动态隐藏页签按钮',
    !/querySelector\([^\n]*accountTabs[\s\S]{0,300}style\.display\s*=\s*["']none["']/.test(page));
check('点击事件仍调用 switchAccountTab', /accountTabs[\s\S]*switchAccountTab\(/.test(page));
check('空平台由面板显示空状态', /function renderEmpty\(\)/.test(panel)
    && /el\.style\.display\s*=\s*["']["']/.test(panel));

const scriptMatch = page.match(/<script>\s*"use strict";([\s\S]*?)<\/script>/);
check('找到页壳内联脚本', !!scriptMatch);
if (scriptMatch) {
    const script = scriptMatch[1].replace(/<%=[\s\S]*?%>/g, 'family');
    try {
        new vm.Script(script, { filename: 'MyAccount.jsp#inline-script' });
        check('页壳内联脚本语法有效', true);
    } catch (e) {
        check('页壳内联脚本语法有效（实际错误：' + e.message + '）', false);
    }
}

if (failures.length) {
    console.error('\n失败 ' + failures.length + ' 项：' + failures.join(' | '));
    process.exit(1);
}
console.log('\n全部通过。');
