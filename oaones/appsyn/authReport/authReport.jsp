<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 统一认证报表（独立一级菜单：/oaones/appsyn/authReport/authReport.jsp）
     *
     * 与「绑定总览 / 接口日志中心 / 操作监控平台 / 消息设置 …」并列的一条独立菜单，
     * 是一个**跨平台只读看板**，回答“各个租户下有多少人、绑定了多少、今天绑了多少、今天登录了多少”：
     *   ① 数字看板：每个租户的总人数 / 已绑定 / 未绑定（+ 合计）；
     *   ② 今日堆柱状图：今日新增绑定人数与今日统一认证登录人数（按租户堆叠）；
     *   ③ 折线图：上面两个指标按日 / 按月的走势（可切换粒度）。
     *
     * 数据来源（全部本模块自己的表，只读）：
     *   租户        uf_oaones_appsyn_qywx_config（企微 / 钉钉 / 飞书，type 区分）
     *   平台成员数  uf_oaones_appsyn_user_tmp（成员快照，status = 0）
     *   绑定情况    uf_oaones_appsyn_user_bind（bind_state = 1）
     *   统一认证登录 uf_oaones_appsyn_op_log（op_type = login，由本模块的 URL 过滤器采集）
     *
     * 权限：与「绑定总览」同口径 —— 只读看板，只展示人数与趋势、不含任何 Secret 或人员明细，
     * 因此不按租户维护人分权；入口的可见范围由 E9 后台菜单授权控制（接口层已在 api.jsp 做登录校验）。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>统一认证报表 - OAones</title>
    <%-- 本菜单是跨平台只读看板：走自己的接口入口，action 前缀 authreport. --%>
    <script>window.OAONES_API_URL = "/oaones/appsyn/authReport/api.jsp";</script>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/datetime.js?v=<%=oaonesAssetV%>"></script>
    <%-- 图表用 E9 平台自带的 ECharts（/js/echart/echarts.min.js），不额外引入第三方框架 --%>
    <script src="/js/echart/echarts.min.js"></script>
</head>
<body>
<div class="navbar">
    <h1>统一认证报表<span class="help-tip inline" id="arHelpTip" data-help="arHelpText"
                        role="button" tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span></h1>
    <div class="nav">
        <a class="active" href="/oaones/appsyn/authReport/authReport.jsp">统一认证报表</a>
    </div>
</div>
<div class="page">
    <div class="help-source" id="arHelpText">
        本页是<span class="rule">跨平台只读看板</span>：把企业微信 / 钉钉 / 飞书三个连接器的
        <b>全部平台租户</b>放在一起，回答“各个租户下有多少人、绑定了多少、今天登录了多少”。
        <b>总人数</b>取该租户的<b>平台成员快照</b>（同步下来的成员数）；
        <b>已绑定</b>取人员绑定表中该租户“已绑定”的去重人数；
        <b>未绑定</b> = 总人数 − 已绑定。
        <b>今日新增绑定</b>是“绑定时间落在今天”的人数；
        <b>今日统一认证登录</b>是“今天通过本模块统一登录进入 OA 的人数”，按登录人的租户绑定归属到租户
        （没绑定任何租户的登录归到“未归属租户”）。
        上方可按 <b>分部（可多选）/ 租户（可多选）/ 日期范围</b> 组合筛选：
        分部与租户作用于“已绑定 / 新增绑定 / 登录”，日期范围作用于走势统计与今日之外的明细。
        <span class="rule">本页只查看、不设置</span>：绑定 / 解绑 / 同步仍在各连接器对应的功能页维护。
    </div>
    <p class="notice" id="arTopTip" style="display:none;color:var(--oa-warning);"></p>
    <jsp:include page="/oaones/appsyn/authReport/authReportPanel.jsp"/>
</div>
<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/appsyn/js/authReport.js?v=<%=oaonesAssetV%>"></script>
</body>
</html>
