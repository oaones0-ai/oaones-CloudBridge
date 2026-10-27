<%@ page pageEncoding="UTF-8" %>
<%-- 个人账号中心 - “个人信息”字段片段：本人 OA 账号明细（只读）。
     三个连接器（企微 / 钉钉 / 飞书）下的账号信息来源一致（都是 OA 侧数据），因此只有这一份，
     由合并页 MyAccount.jsp 直接 include 到**顶部账号卡片**里（不再单独占一个“账号信息”页签，
     避免与顶部重复）；字段网格自带 label 定宽，配合卡片里的头像与标题使用。

     固定字段的展示位（不是分页列表），因此不套用 js/table.js 列表组件。 --%>
        <div class="account-fields" id="accountInfoFields"></div>

<script>
"use strict";
/** 片段脚本比页壳脚本先执行，片段内必须自带一份本地兜底 */
function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }

/** 本人是否为子账户（E9 标准 hrmresource.accounttype：0 主账户 / 1 子账户） */
function isSubAccount(info) {
    return String(info.account_type == null ? "" : info.account_type) === "1";
}

/** 本人是子账户时，从 account.info.accounts 里取归属的主账户行（主账户已删除时返回 null） */
function infoMainAccountRow(info) {
    var list = info.accounts || [];
    for (var i = 0; i < list.length; i++) {
        if (String(list[i].account_type == null ? "" : list[i].account_type) !== "1"
            && list[i].is_self !== true) {
            return list[i];
        }
    }
    return null;
}

/** 本人名下子账户数量（account.info.accounts 中 account_type=1 的行数） */
function infoSubAccountCount(info) {
    var list = info.accounts || [];
    var count = 0;
    for (var i = 0; i < list.length; i++) {
        if (String(list[i].account_type == null ? "" : list[i].account_type) === "1") { count++; }
    }
    return count;
}

/** 页壳（MyAccount.jsp）加载完 account.info 后调用：渲染顶部“个人信息”字段（姓名/部门/分部带泛微标准页面链接） */
function renderAccountInfoPanel(info) {
    info = info || {};
    var isSub = isSubAccount(info);
    var fields = [];
    fields.push({ label: "账号", html: OaonesHrm.user(info.oa_user_id, info.name) });
    // 账户类型：主账户 / 子账户（子账户由主账户在“人员信息 - 系统信息”里指定归属）
    fields.push({
        label: "账户类型",
        html: '<span class="tag ' + (isSub ? "acct-sub" : "acct-main") + '">'
            + esc(info.account_type_text || (isSub ? "子账户" : "主账户")) + '</span>'
    });
    var main = infoMainAccountRow(info);
    if (isSub && main) {
        fields.push({ label: "主账户", html: OaonesHrm.user(main.oa_user_id, main.name) });
    }
    if (!isSub) {
        var subCount = infoSubAccountCount(info);
        fields.push({ label: "子账户", text: subCount > 0 ? subCount + " 个" : "无" });
    }
    fields.push({ label: "登录名", text: info.loginid });
    fields.push({ label: "员工编号", text: info.workcode });
    fields.push({ label: "手机号", text: info.mobile });
    fields.push({ label: "邮箱", text: info.email });
    fields.push({
        label: "所属部门",
        html: info.oa_dept_name ? OaonesHrm.dept(info.oa_dept_id, info.oa_dept_name) : ""
    });
    fields.push({
        label: "所属分部",
        html: info.oa_sub_name ? OaonesHrm.sub(info.oa_sub_id, info.oa_sub_name) : ""
    });
    fields.push({ label: "账号状态", text: info.oa_status_text });
    fields.push({ label: "OA 人员ID", text: info.oa_user_id });

    var html = "";
    fields.forEach(function (f) {
        html += '<div class="account-field"><span>' + esc(f.label) + '</span><b>'
            + (f.html !== undefined ? (f.html || '<span class="muted">未维护</span>')
                : (esc(f.text || "") || '<span class="muted">未维护</span>'))
            + '</b></div>';
    });
    byId("accountInfoFields").innerHTML = html;
}
</script>
