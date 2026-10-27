<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.ApiLogController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 「接口日志中心」（一级菜单）专用的前端 AJAX 入口。
     *
     * 该菜单是**跨平台只读汇总页**（企业微信 / 钉钉 / 飞书 / 个人微信都在同一个列表里），
     * 不依赖任何连接器的 api.jsp：页面走本入口，直接调用公共的 ApiLogController
     * （action 前缀 apilog.）。三个连接器的 OaonesController 也顺带路由了 apilog.*，
     * 以便在任意 api.jsp 上复用同一组 action。
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
    JSONObject result = new ApiLogController().handle(action, request, String.valueOf(user.getUID()));
    out.print(result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString());
%>
