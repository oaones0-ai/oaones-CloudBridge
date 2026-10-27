<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesLog" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.controller.OrgMapController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * OA 分部 - 平台租户一对一映射专用接口。
     *
     * 页面通过 platform=wecom|dingtalk|feishu 指定平台；所有 action 都带 orgmap. 前缀。
     * 该入口只处理分部映射维护，不调用任何同步或部门绑定接口。
     */
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        out.print("{\"code\":401,\"msg\":\"未登录或会话已失效\"}");
        return;
    }
    String platform = request.getParameter("platform");
    int type = OaonesConst.TYPE_WECHAT;
    if ("dingtalk".equalsIgnoreCase(platform)) {
        type = OaonesConst.TYPE_DINGTALK;
    } else if ("feishu".equalsIgnoreCase(platform)) {
        type = OaonesConst.TYPE_FEISHU;
    }
    String action = request.getParameter("action") == null ? "" : request.getParameter("action").trim();
    try {
        JSONObject result = new OrgMapController(type).handle(
                action, request, String.valueOf(user.getUID()));
        out.print(result == null ? OaonesUtil.fail("未知 action").toJSONString() : result.toJSONString());
    } catch (Throwable t) {
        try {
            OaonesLog.error("OA 分部映射接口处理失败 action=" + action, t);
        } catch (Throwable ignore) {
            // 日志异常不能再次把业务提示变成 HTTP 500
        }
        String message = OaonesUtil.s(t.getMessage()).trim();
        out.print(OaonesUtil.fail(message.length() == 0 ? "处理失败，请刷新页面后重试" : message).toJSONString());
    }
%>
