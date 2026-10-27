<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.SyncResultController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        out.print("{\"code\":401,\"msg\":\"未登录或会话已失效\"}");
        return;
    }
    String action = request.getParameter("action") == null ? "" : request.getParameter("action").trim();
    try {
        JSONObject result = new SyncResultController().handle(action, request, String.valueOf(user.getUID()));
        out.print(result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString());
    } catch (Throwable t) {
        JSONObject result = new JSONObject();
        result.put("code", Integer.valueOf(-1));
        result.put("msg", t.getMessage() == null ? "查询同步结果失败" : t.getMessage());
        out.print(result.toJSONString());
    }
%>
