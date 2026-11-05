<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * OAones 系统设置：功能设置 / 日志设置。
     *
     * 功能设置只控制页面菜单与操作入口的显隐，不拦截后端接口。
     * 日志设置按表配置保留天数，<= 0 表示永久保留；自动清理每天执行一次。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    boolean oaonesSystemSettingsAdmin = OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()));
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>系统设置 - OAones</title>
    <script>window.OAONES_API_URL = "/oaones/appsyn/systemSettings/api.jsp";</script>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=oaonesSystemSettingsAdmin%> };</script>
    <link rel="stylesheet" href="/oaones/scripts/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/scripts/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/scripts/js/modal.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/scripts/js/tabs.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/scripts/js/tree.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/scripts/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/scripts/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/scripts/js/help.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<div class="navbar">
    <h1>系统设置</h1>
    <div class="nav" id="systemSettingsTabs">
        <a class="active" href="#feature" data-settings-tab="feature">功能设置</a>
        <span class="help-tip" data-settings-help="feature" data-help="featureSettingsHelpText"
              role="button" tabindex="0" title="查看功能设置说明" aria-label="查看功能设置说明">?</span>
        <a href="#log" data-settings-tab="log">日志设置</a>
        <span class="help-tip" data-settings-help="log" data-help="logSettingsHelpText"
              role="button" tabindex="0" title="查看日志设置说明" aria-label="查看日志设置说明"
              style="display:none;">?</span>
    </div>
</div>
<div class="page">
    <div class="card">
        <div class="tab-panel active" id="featureSettingsPanel" data-settings-panel="feature">
            <div class="help-source" id="featureSettingsHelpText">
                <b>功能设置</b>控制 OAones 功能菜单与操作入口的显示。关闭开关只隐藏对应入口，
                不改变已有数据，也不影响后端接口；“定时同步”和“应用绑定”在已有业务数据时不允许关闭。
                开关保存后，刷新相关功能页即可看到最新显示状态。
            </div>
<% if (oaonesSystemSettingsAdmin) { %>
            <p class="notice" id="featureSettingsMissingTip" style="display:none;"></p>
            <div class="feature-settings-groups" id="featureSettingsGroups">
                <p class="notice">正在加载功能设置…</p>
            </div>
<% } else { %>
            <p class="notice retention-empty">只有系统管理员或超管角色成员可以维护功能设置。</p>
<% } %>
        </div>

        <div class="tab-panel" id="logSettingsPanel" data-settings-panel="log">
            <div class="help-source" id="logSettingsHelpText">
                <b>日志设置</b>按数据库日志表或 mylog 日志目录分别设置，单位是“天”；配置为 <span class="rule">0 或负数</span> 时永久保留。
                顶部可输入一个天数并“应用到全部”，再点“保存全部”一次保存；也可以点每行末尾的“保存”单独设置。
                mylog 文件项显示 <code>log/mylog</code> 下全部日志文件的总大小，保留天数只作用于
                <code>mylog_YYYYMMDD.zip</code>，不会删除当前 <code>mylog.log</code>。
                自动清理每天检查一次，只删除超过各自保留天数的数据，不会清理仍在保留期内的记录。
                系统升级或新增日志表后，未出现在配置表中的日志项按永久保留处理。
            </div>
<% if (oaonesSystemSettingsAdmin) { %>
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
                        <th class="retention-row-count" title="数据库日志显示当前行数；mylog 文件显示 log/mylog 下全部日志文件的总大小">当前行数 / 总大小</th>
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
            <p class="notice retention-empty">只有系统管理员或超管角色成员可以维护日志设置。</p>
<% } %>
        </div>
    </div>
</div>
<script src="/oaones/appsyn/script/js/featureConfig.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/scripts/js/featureGate.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/scripts/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/appsyn/script/js/appContext.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/appsyn/script/js/systemSettings.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
