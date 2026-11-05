<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.controller.FeatureSettingController" %>
<%@ page import="com.customization.oaones.appsyn.controller.LogRetentionController" %>
<%@ page import="com.customization.oaones.appsyn.job.OaonesLogRetentionScheduler" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 系统设置专用接口入口（功能设置 action 前缀 features.，日志设置 action 前缀 logretention.）。
     */
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        out.print("{\"code\":401,\"msg\":\"未登录或会话已失效\"}");
        return;
    }
    OaonesLogRetentionScheduler.ensureStarted();
    String action = request.getParameter("action") == null ? "" : request.getParameter("action").trim();
    JSONObject result;
    try {
        if (action.startsWith("features.")) {
            result = new FeatureSettingController().handle(
                    action, request, String.valueOf(user.getUID()));
        } else {
            result = new LogRetentionController().handle(
                    action, request, String.valueOf(user.getUID()));
        }
    } catch (Throwable t) {
        result = OaonesUtil.fail(t.getMessage() == null ? "系统设置处理失败" : t.getMessage());
    }
    out.print(result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString());
%>
