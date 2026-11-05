<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.controller.FeatureSettingController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 功能开关只读入口：供各功能页隐藏菜单与按钮。
     *
     * 只返回布尔值，不返回租户、人员或业务数据，也不承担后端接口拦截。
     */
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        out.print("{\"code\":401,\"msg\":\"未登录或会话已失效\"}");
        return;
    }
    String action = request.getParameter("action");
    if (action == null || action.trim().length() == 0) {
        action = "features.read";
    }
    JSONObject result;
    try {
        result = new FeatureSettingController().handle(
                action.trim(), request, String.valueOf(user.getUID()));
    } catch (Throwable t) {
        result = OaonesUtil.fail(t.getMessage() == null ? "功能设置读取失败" : t.getMessage());
    }
    out.print(result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString());
%>
