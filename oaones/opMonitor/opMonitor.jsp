<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.OpLogServiceImpl" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 操作监控平台（独立一级菜单：/oaones/opMonitor/opMonitor.jsp）
     *
     * 与「接口日志中心 / 企微租户设置 / 消息设置 / 登录设置 / 微信设置 / 应用中心」并列的一条独立菜单：
     * 回答“**某某人什么时间登录、访问了哪个菜单**”：
     *   ① 事件类型：登录 / 退出 / 菜单访问；
     *   ② 人员：姓名 / 登录名 / 主部门（登录 / 退出时落库快照）；
     *   ③ 菜单：命中 E9 菜单表 LeftMenuInfo 的菜单名称与菜单地址，以及本次实际请求地址；
     *   ④ 来源：客户端 IP 与 PC / MOBILE。
     *
     * 数据来源：表 uf_oaones_op_log（由 OAones 的 URL 过滤器 common.OaonesOpLogFilter
     * 在页面请求上采集，见 common/OaonesOpLog 的口径说明）。本页只读 + 按保留天数清理。
     *
     * 权限：系统管理员（user.id=1）或 oaones.properties#opmonitor.admin.ids 可查看；
     * 其它登录人打开本页只看到说明（接口层再校验一次）。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    boolean opMonitorAdmin = new OpLogServiceImpl().canView(String.valueOf(user.getUID()));
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>操作监控平台 - OAones</title>
    <%-- 本菜单是跨平台只读汇总页：走自己的接口入口，action 前缀 oplog. --%>
    <script>window.OAONES_API_URL = "/oaones/opMonitor/api.jsp";</script>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <script>window.OAONES_OP_ADMIN = <%=opMonitorAdmin%>;</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/datetime.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<div class="navbar">
    <h1>操作监控平台<span class="help-tip inline" id="opMonitorHelpTip" data-help="opMonitorHelpText"
                        role="button" tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span></h1>
    <div class="nav">
        <a class="active" href="/oaones/opMonitor/opMonitor.jsp">操作监控平台</a>
    </div>
</div>
<div class="page">
    <div class="help-source" id="opMonitorHelpText">
        本页回答 <span class="rule">“某某人什么时间登录、访问了哪个菜单”</span>：
        一行一条记录，事件类型分 <b>登录 / 退出 / 菜单访问</b> 三种。
        <% if (!opMonitorAdmin) { %>
        本页是<b>全站的操作留痕</b>，只对系统管理员或被授权人员开放；你没有查看权限，
        如需查看请联系系统管理员（把你要用的人员ID加到 <code>oaones.properties#opmonitor.admin.ids</code>）。
        <% } else { %>
        <b>登录</b>记的是“登录成功后第一次进入 OA 页面”（记录 IP 与客户端）；
        <b>菜单访问</b>记的是“本次请求地址命中 E9 菜单表 <code>LeftMenuInfo</code> 里配置的菜单”，
        因此记到的就是菜单级明细（菜单名称 + 菜单地址 + 本次实际请求地址）；
        没配成菜单的平台内部页面、接口调用与静态资源都不落记录。
        同一会话去重窗口内重复打开同一个菜单只记一条（窗口见 <code>op.log.dedupe.seconds</code>）。
        顶部可按 <b>人员 / 事件类型 / 菜单 / 时间</b> 过滤，另可叠加关键字（菜单名称 / 访问地址 / IP）。
        <span class="rule">日志条数按“菜单打开”增长，量大时请先用人员与时间缩小范围。</span>
        保留天数由 <code>oaones.properties#op.log.keep.days</code> 控制（默认 90 天，0 表示永久保留）；
        采集总开关是 <code>oaones.properties#op.log.enable</code>（0 = 关闭采集，历史记录仍可查看）。
        <% } %>
    </div>
    <p class="notice" id="opMonitorTip" style="display:none;color:var(--oa-warning);"></p>
    <jsp:include page="/oaones/opMonitor/opMonitorPanel.jsp"/>
</div>
<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/js/opMonitor.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
