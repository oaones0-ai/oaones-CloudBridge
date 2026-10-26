<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    /*
     * 同步结果（独立页面）
     *
     *   /oaones/syncCenter/syncResult.jsp?platform=wecom|dingtalk|feishu
     *       [&cfg_id=<租户ID>][&requestid=<OA流程请求ID>][&log_id=<同步批次ID>]
     *       [&hide_filters=1][&hide_summary=1]
     *
     * 数据源：uf_oaones_sync_log_item（一行一个部门 / 分部 / 人员）。
     * 同步中心按规则执行与 OA 流程 TenantAccountServiceImpl 建号都写这张表；
     * 流程路径会同时写 requestid，因此可跨平台、跨租户按同一个流程请求ID查询。
     * requestid 可直接作为 URL 参数：页面加载后会回填过滤框并立即按该请求 ID 查询。
     * hide_filters=1 隐藏搜索条件；hide_summary=1 隐藏统计行与截断提示，两个参数可独立使用。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }

    String oaonesPlatform = request.getParameter("platform");
    oaonesPlatform = oaonesPlatform == null ? "" : oaonesPlatform.trim().toLowerCase();
    boolean oaonesPlatformSupplied =
            "dingtalk".equals(oaonesPlatform) || "feishu".equals(oaonesPlatform)
                    || "wecom".equals(oaonesPlatform);
    if (!"dingtalk".equals(oaonesPlatform) && !"feishu".equals(oaonesPlatform)) {
        oaonesPlatform = "wecom";
    }
    boolean oaonesDd = "dingtalk".equals(oaonesPlatform);
    boolean oaonesFs = "feishu".equals(oaonesPlatform);
    String oaonesSyncLabel = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企业微信");
    String oaonesNavbarPath = oaonesFs ? "/oaones/Feishu/navbar.jsp"
            : (oaonesDd ? "/oaones/DingTalk/navbar.jsp" : "/oaones/WeCom/navbar.jsp");

    String oaonesCfgId = request.getParameter("cfg_id");
    oaonesCfgId = oaonesCfgId == null ? "" : oaonesCfgId.trim();
    if (!oaonesCfgId.matches("\\d+")) {
        oaonesCfgId = "";
    }

    String oaonesRequestId = request.getParameter("requestid");
    oaonesRequestId = oaonesRequestId == null ? "" : oaonesRequestId.trim();
    if (!oaonesRequestId.matches("\\d+")) {
        oaonesRequestId = "";
    }
    String oaonesLogId = request.getParameter("log_id");
    oaonesLogId = oaonesLogId == null ? "" : oaonesLogId.trim();
    if (!oaonesLogId.matches("\\d+")) {
        oaonesLogId = "";
    }
    String oaonesHideFiltersParam = request.getParameter("hide_filters");
    boolean oaonesHideFilters = "1".equals(oaonesHideFiltersParam)
            || "true".equalsIgnoreCase(oaonesHideFiltersParam)
            || "yes".equalsIgnoreCase(oaonesHideFiltersParam);
    String oaonesHideSummaryParam = request.getParameter("hide_summary");
    boolean oaonesHideSummary = "1".equals(oaonesHideSummaryParam)
            || "true".equalsIgnoreCase(oaonesHideSummaryParam)
            || "yes".equalsIgnoreCase(oaonesHideSummaryParam);
    StringBuilder oaonesResultKeepQuery = new StringBuilder();
    if (oaonesRequestId.length() > 0) {
        oaonesResultKeepQuery.append("&requestid=").append(oaonesRequestId);
    }
    if (oaonesLogId.length() > 0) {
        oaonesResultKeepQuery.append("&log_id=").append(oaonesLogId);
    }
    if (oaonesHideFilters) {
        oaonesResultKeepQuery.append("&hide_filters=1");
    }
    if (oaonesHideSummary) {
        oaonesResultKeepQuery.append("&hide_summary=1");
    }
    request.setAttribute("oaonesHideFilters", Boolean.valueOf(oaonesHideFilters));
    request.setAttribute("oaonesHideSummary", Boolean.valueOf(oaonesHideSummary));
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>同步结果（<%=oaonesSyncLabel%>） - OAones</title>
    <script>
        window.OAONES_API_URL = "/oaones/syncCenter/resultApi.jsp";
        window.OAONES_SYNC_RESULT_PLATFORM = "<%=oaonesPlatformSupplied ? oaonesPlatform : ""%>";
        window.OAONES_SYNC_RESULT_LABEL = "<%=oaonesSyncLabel%>";
        window.OAONES_SYNC_RESULT_CFG_ID = "<%=oaonesCfgId%>";
        window.OAONES_SYNC_RESULT_REQUEST_ID = "<%=oaonesRequestId%>";
        window.OAONES_SYNC_RESULT_LOG_ID = "<%=oaonesLogId%>";
        window.OAONES_SYNC_RESULT_HIDE_FILTERS = <%=oaonesHideFilters%>;
        window.OAONES_SYNC_RESULT_HIDE_SUMMARY = <%=oaonesHideSummary%>;
        window.OAONES_USER = {
            id: "<%=user.getUID()%>",
            sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%>
        };
    </script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/datetime.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<% if (oaonesCfgId.length() == 0) { %>
<div class="navbar">
    <h1>同步结果<span class="help-tip inline" id="syncResultHelpTip" data-help="syncResultHelpText"
                      role="button" tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span></h1>
    <div class="nav">
        <span class="view-switch" title="切换平台；按流程 requestid 查询时不受平台限制">
            <a class="view-btn<%= oaonesDd || oaonesFs ? "" : " active" %>"
               href="/oaones/syncCenter/syncResult.jsp?platform=wecom<%=oaonesResultKeepQuery%>">企业微信</a>
            <a class="view-btn<%= oaonesDd ? " active" : "" %>"
               href="/oaones/syncCenter/syncResult.jsp?platform=dingtalk<%=oaonesResultKeepQuery%>">钉钉</a>
            <a class="view-btn<%= oaonesFs ? " active" : "" %>"
               href="/oaones/syncCenter/syncResult.jsp?platform=feishu<%=oaonesResultKeepQuery%>">飞书</a>
        </span>
    </div>
</div>
<% } else { %>
<jsp:include page="<%= oaonesNavbarPath %>">
    <jsp:param name="active" value="syncResult"/>
    <jsp:param name="cfg_id" value="<%= oaonesCfgId %>"/>
</jsp:include>
<% } %>
<div class="page">
    <div class="help-source" id="syncResultHelpText">
        本页逐条展示同步结果表 <b>uf_oaones_sync_log_item</b>：一行对应一个部门、分部或人员。
        同一个同步批次的汇总在 <b>uf_oaones_sync_log</b>，两边通过批次ID关联。
        <b>流程建号</b>（TenantAccountServiceImpl）与同步中心执行都会写入逐条结果；
        流程触发的记录带有 <b>requestid（OA 流程请求ID）</b>，可以据此跨平台、跨租户查看同一次流程产生的全部结果。
        定时同步没有流程上下文，requestid 为空。
        列表最多返回当前筛选条件最近 500 条，命中总数与部门/人员/动作统计按数据库全部匹配数据计算；
        记录较多时请用 requestid、租户与时间缩小范围。
    </div>
    <jsp:include page="/oaones/syncCenter/syncResultPanel.jsp"/>
</div>
<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/js/syncResult.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
