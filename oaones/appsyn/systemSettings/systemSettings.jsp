<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * OAones 系统设置 → 日志保留。
     *
     * 一条配置对应一张日志表；保留天数 <= 0 表示永久保留。
     * 页面既可以统一设置后一次保存，也可以逐行单独保存。
     * 自动清理由 OaonesLogRetentionScheduler 每天执行一次。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    boolean oaonesLogRetentionAdmin = OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()));
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>系统设置 - 日志保留 - OAones</title>
    <script>window.OAONES_API_URL = "/oaones/appsyn/systemSettings/api.jsp";</script>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=oaonesLogRetentionAdmin%> };</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<div class="navbar">
    <h1>系统设置<span class="help-tip inline" id="logRetentionHelpTip" data-help="logRetentionHelpText"
                       role="button" tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span></h1>
    <div class="nav">
        <a class="active" href="/oaones/appsyn/systemSettings/systemSettings.jsp">日志保留</a>
    </div>
</div>
<div class="page">
    <div class="card">
        <div class="help-source" id="logRetentionHelpText">
            <b>日志保留</b>按表分别设置，单位是“天”；配置为 <span class="rule">0 或负数</span> 时永久保留。
            顶部可输入一个天数并“应用到全部”，再点“保存全部”一次保存；也可以点每行末尾的“保存”单独设置。
            自动清理每天检查一次，只删除超过各自保留天数的数据，不会清理仍在保留期内的记录。
            系统升级或新增日志表后，未出现在配置表中的日志项按永久保留处理。
        </div>
<% if (oaonesLogRetentionAdmin) { %>
        <div class="toolbar retention-toolbar">
            <label for="logRetentionGlobalDays">统一设置</label>
            <input class="retention-days" type="number" step="1" id="logRetentionGlobalDays"
                   name="logRetentionGlobalDays" value="0" autocomplete="off"
                   aria-label="统一保留天数">
            <span class="notice">天（0 或负数 = 永久保留）</span>
            <button type="button" class="btn secondary" id="logRetentionApplyAllBtn">应用到全部</button>
            <span class="retention-toolbar-space"></span>
            <button type="button" class="btn" id="logRetentionSaveAllBtn">保存全部</button>
            <button type="button" class="btn secondary" id="logRetentionCleanBtn"
                    title="按当前保留设置立即清理已过期日志">立即清理</button>
        </div>
        <p class="notice" id="logRetentionSchedulerTip"></p>
        <p class="notice" id="logRetentionPageTip" style="display:none;"></p>

        <div class="table-wrap">
            <table class="grid retention-grid" id="logRetentionTable">
                <thead>
                <tr>
                    <th>日志类型</th>
                    <th>说明</th>
                    <th>物理表</th>
                    <th class="retention-row-count" title="每次加载页面或执行操作后从数据库实时统计">当前行数</th>
                    <th>保留天数</th>
                    <th>当前状态</th>
                    <th>操作</th>
                </tr>
                </thead>
                <tbody id="logRetentionBody">
                <tr><td colspan="7" class="retention-empty">加载中…</td></tr>
                </tbody>
            </table>
        </div>
<% } else { %>
        <p class="notice retention-empty">只有系统管理员或超管角色成员可以维护日志保留设置。</p>
<% } %>
    </div>
</div>
<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/appsyn/js/systemSettings.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
