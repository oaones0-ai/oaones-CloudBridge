<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesApiLog" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.msg.controller.MsgController" %>
<%@ page import="com.customization.oaones.appsyn.msg.service.MsgRenderService" %>
<%@ page import="weaver.general.BaseBean" %>
<%
    /*
     * OAones 统一消息接口（供 OA 之外的业务系统 / 脚本 / 流程自定义动作调用）
     *
     * 地址：POST/GET /oaones/appsyn/WeCom/msgapi.jsp?key=<密钥>&...
     * 密钥：oaones.appsyn.properties#msg.api.key（留空表示关闭该入口）
     *
     * 入参：
     *   key        必填，接口密钥
     *   type_id    本模块消息类型推送设置ID（uf_oaones_appsyn_msg_type.id），或 std_config_id 标准消息类型ID（字符串：内置类型如 20、自定义类型如 1LbVgIrt） / std_type 来源编号
     *   std_type   泛微标准消息类型 typecode（与 type_id 二选一）
     *   biz_type   业务来源（workflow / meeting / custom，默认 custom）
     *   biz_id     业务单号（幂等去重键；必填才会做去重）
     *   biz_title  业务标题
     *   accounts   OA 登录名或工号，多个用逗号分隔
     *   wx_userids 企微 userid（与 accounts 二选一或同时传；直接传入优先使用）
     *   oa_user_ids OA 人员ID，多个用逗号分隔
     *   cfg_id     指定企微租户（可选，默认按接收人绑定关系自动定位）
     *   title / context / target_name / url  常用模板变量
     *   v_xxx      任意业务字段，作为模板变量 ${xxx} 使用
     *
     * 返回：{"code":0,"msg":"推送完成...","data":{"pushed":1,"failed":0,...}}
     */
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");

    String key = OaonesUtil.s(request.getParameter("key")).trim();
    String propKey = "";
    try {
        propKey = OaonesUtil.s(new BaseBean().getPropValue("oaones.appsyn", "msg.api.key")).trim();
    } catch (Throwable t) {
        propKey = "";
    }
    if (propKey.length() == 0) {
        out.print("{\"code\":401,\"msg\":\"统一消息接口未开启（oaones.appsyn.properties#msg.api.key 为空）\"}");
        return;
    }
    if (!propKey.equals(key)) {
        out.print("{\"code\":401,\"msg\":\"接口密钥错误\"}");
        return;
    }
    try {
        JSONObject result = new MsgController().handle("msg.send", request, "0");
        String body = result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString();
        // 接口日志中心：记录这一次“第三方平台 / 外部系统调用统一消息接口”的入口调用
        boolean ok = result != null && result.getIntValue("code") == 0;
        OaonesApiLog.inbound(OaonesConst.TYPE_WECHAT,
                OaonesUtil.i(OaonesUtil.s(request.getParameter("cfg_id")), 0), "",
                OaonesApiLog.TYPE_MSG, "/oaones/appsyn/WeCom/msgapi.jsp",
                OaonesApiLog.requestSummary(request), body, ok,
                result == null ? "" : String.valueOf(result.getIntValue("code")),
                result == null ? "无返回" : OaonesUtil.s(result.getString("msg")), "0");
        out.print(body);
    } catch (Throwable t) {
        OaonesApiLog.inbound(OaonesConst.TYPE_WECHAT,
                OaonesUtil.i(OaonesUtil.s(request.getParameter("cfg_id")), 0), "",
                OaonesApiLog.TYPE_MSG, "/oaones/appsyn/WeCom/msgapi.jsp",
                OaonesApiLog.requestSummary(request), "", false, "-1",
                OaonesUtil.s(t.getMessage()), "0");
        out.print("{\"code\":-1,\"msg\":\"" + OaonesUtil.s(t.getMessage()).replace("\"", "'") + "\"}");
    }
%>
