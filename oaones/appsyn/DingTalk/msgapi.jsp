<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesApiLog" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.msg.dingtalk.controller.DingTalkMsgController" %>
<%@ page import="weaver.general.BaseBean" %>
<%
    /*
     * OAones 钉钉统一消息接口（供 OA 之外的业务系统 / 脚本 / 流程自定义动作调用）
     *
     * 地址：POST/GET /oaones/appsyn/DingTalk/msgapi.jsp?key=<密钥>&...
     * 密钥：oaones.appsyn.properties#msg.dingtalk.api.key（留空表示关闭该入口；
     *      留空且 msg.api.key 非空时不回退，避免误用企微密钥推送钉钉）
     *
     * 入参（与企微的 /oaones/appsyn/WeCom/msgapi.jsp 同口径，接收人 userid 指钉钉 userid）：
     *   key        必填，接口密钥
     *   type_id    本模块钉钉消息类型推送设置ID（uf_oaones_appsyn_msg_type.id，type=1），或 std_config_id 标准消息类型ID（字符串：内置类型如 20、自定义类型如 1LbVgIrt） / std_type 来源编号
     *   std_type   泛微标准消息类型 typecode（与 type_id 二选一）
     *   biz_type   业务来源（workflow / meeting / custom，默认 custom）
     *   biz_id     业务单号（幂等去重键；必填才会做去重）
     *   biz_title  业务标题
     *   accounts   OA 登录名或工号，多个用逗号分隔
     *   wx_userids 钉钉 userid（与 accounts 二选一或同时传；直接传入优先使用）
     *   oa_user_ids OA 人员ID，多个用逗号分隔
     *   cfg_id     指定钉钉租户（可选，默认按接收人绑定关系自动定位）
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
        propKey = OaonesUtil.s(new BaseBean().getPropValue("oaones.appsyn", "msg.dingtalk.api.key")).trim();
    } catch (Throwable t) {
        propKey = "";
    }
    if (propKey.length() == 0) {
        out.print("{\"code\":401,\"msg\":\"钉钉统一消息接口未开启（oaones.appsyn.properties#msg.dingtalk.api.key 为空）\"}");
        return;
    }
    if (!propKey.equals(key)) {
        out.print("{\"code\":401,\"msg\":\"接口密钥错误\"}");
        return;
    }
    try {
        JSONObject result = new DingTalkMsgController().handle("msg.send", request, "0");
        String body = result == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : result.toJSONString();
        boolean ok = result != null && result.getIntValue("code") == 0;
        OaonesApiLog.inbound(OaonesConst.TYPE_DINGTALK,
                OaonesUtil.i(OaonesUtil.s(request.getParameter("cfg_id")), 0), "",
                OaonesApiLog.TYPE_MSG, "/oaones/appsyn/DingTalk/msgapi.jsp",
                OaonesApiLog.requestSummary(request), body, ok,
                result == null ? "" : String.valueOf(result.getIntValue("code")),
                result == null ? "无返回" : OaonesUtil.s(result.getString("msg")), "0");
        out.print(body);
    } catch (Throwable t) {
        OaonesApiLog.inbound(OaonesConst.TYPE_DINGTALK,
                OaonesUtil.i(OaonesUtil.s(request.getParameter("cfg_id")), 0), "",
                OaonesApiLog.TYPE_MSG, "/oaones/appsyn/DingTalk/msgapi.jsp",
                OaonesApiLog.requestSummary(request), "", false, "-1",
                OaonesUtil.s(t.getMessage()), "0");
        out.print("{\"code\":-1,\"msg\":\"" + OaonesUtil.s(t.getMessage()).replace("\"", "'") + "\"}");
    }
%>
