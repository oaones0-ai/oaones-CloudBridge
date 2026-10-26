<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    /*
     * 接口日志中心（独立一级菜单：/oaones/apiLog/apiLog.jsp）
     *
     * 与「企微租户设置 / 消息设置 / 登录设置 / 微信设置 / 应用中心」并列的一条独立菜单：
     * 把 OAones 与第三方平台之间的**所有接口调用**放在同一个列表里展示——
     *   ① 企业微信 / 钉钉 / 飞书 / 个人微信（平台维度）；
     *   ② 每个平台下的租户（租户维度；个人微信没有租户）；
     *   ③ 接口类型：组织同步 / 消息推送 / 会议·日程同步 / 登录授权 / 其他；
     * 顶部可按 平台 / 租户 / 接口类型（另加 方向 / 结果 / 关键字 / 时间）过滤，行内可看报文详情。
     *
     * 数据来源：表 uf_oaones_api_log（各连接器的底层 HTTP 与外部接口入口统一落库，
     * 见 common/OaonesApiLog）。本页只读 + 按保留天数清理，没有设置类写入口。
     * 权限：系统管理员（user.id=1）或 oaones.properties#apilog.admin.ids 可看全部；
     * 其它登录人只看本人可维护租户的日志（接口层再校验一次）。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>接口日志中心 - OAones</title>
    <%-- 本菜单是跨平台只读汇总页：走自己的接口入口，action 前缀 apilog. --%>
    <script>window.OAONES_API_URL = "/oaones/apiLog/api.jsp";</script>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <%-- 弹框统一支持按表头拖动（picker.js 接管；本页的日志详情也是弹框） --%>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/datetime.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<div class="navbar">
    <h1>接口日志中心<span class="help-tip inline" id="apiLogHelpTip" data-help="apiLogHelpText"
                          role="button" tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span></h1>
    <div class="nav">
        <a class="active" href="/oaones/apiLog/apiLog.jsp">接口日志中心</a>
    </div>
</div>
<div class="page">
    <div class="help-source" id="apiLogHelpText"><% if (OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))) { %>你是<b>系统管理员</b>（user.id=1）或超管角色成员，可以看到全部平台 / 全部租户的接口日志。
        <% } else { %>本页只展示<b>你被指定为维护人的租户</b>的接口日志；个人微信（没有租户）的日志只有系统管理员可见。
        <% } %>
        本页把 OAones 与第三方平台之间的接口调用放在<b>同一个列表</b>里：既包含 OAones <b>发往</b>第三方平台的请求
        （组织同步 / 消息推送 / 会议·日程同步 / 登录授权），也包含第三方平台 <b>调用 OAones</b> 的入口
        （统一消息接口 / 工作台免登 / 扫码回调）。
        顶部可按 <b>平台 / 租户 / 接口类型</b> 过滤，另可叠加方向 / 结果 / 关键字 / 时间范围。
        <b>点任意一行的“查看”</b>可看这一次调用的请求报文、返回报文与异常堆栈
        （接口地址与报文<b>按原文完整展示</b>，不脱敏、不截断；报文长时点弹框右上角“全屏”查看）。
        <span class="rule">日志按接口调用逐条记录，量大时请先用时间与平台缩小范围。</span>
        日志保留天数在后台 <b>系统设置 → 日志保留</b> 中维护（默认永久保留，按天设置）。</div>
    <p class="notice" id="apiLogTip" style="display:none;color:var(--oa-warning);"></p>
    <jsp:include page="/oaones/apiLog/apiLogPanel.jsp"/>
</div>
<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/js/apiLog.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
