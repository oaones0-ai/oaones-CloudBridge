<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.dingtalk.OaonesController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * OAones 钉钉连接器前端 AJAX 统一入口（与企微连接器的 /oaones/WeCom/api.jsp 对称）。
     *
     * 钉钉页面（各模块目录下的 DingTalk/*.jsp，如 /oaones/index/TenantIndex.jsp?platform=dingtalk）在引 js/api.js 之前设置
     * window.OAONES_API_URL = "/oaones/DingTalk/api.jsp"，于是同一套公共脚本
     * （表格 / 选择框 / 提示 / 导出）的请求全部落到钉钉连接器的控制器：
     *   cfg. / dept. / sub. / user. / sync. / account. / app. / login. / logincfg. / msg.
     * 路由与参数口径与企微侧完全一致，只是租户范围限定为“钉钉租户”（type=1）。
     *
     * 登录校验与企微入口同口径：未登录直接返回 401，由页面统一提示重新登录。
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
    JSONObject result = new OaonesController().handle(action, request, String.valueOf(user.getUID()));
    out.print(result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString());
%>
