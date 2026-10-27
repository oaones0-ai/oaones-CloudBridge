<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.AuthReportController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 「统一认证报表」（一级菜单）专用的前端 AJAX 入口。
     *
     * 该菜单是**跨平台只读看板**（企微 / 钉钉 / 飞书的租户人数、绑定情况、今日登录与走势都在一页），
     * 不依赖任何连接器的 api.jsp：页面走本入口，直接调用公共的 AuthReportController
     * （action 前缀 authreport.，目前只有 authreport.data 一个只读 action）。
     *
     * 登录校验与其它入口同口径：未登录直接返回 401，由页面统一提示重新登录。
     */
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        out.print("{\"code\":401,\"msg\":\"未登录或会话已失效\"}");
        return;
    }
    String action = request.getParameter("action") == null ? "" : request.getParameter("action").trim();
    JSONObject result = new AuthReportController().handle(action, request, String.valueOf(user.getUID()));
    out.print(result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString());
%>
