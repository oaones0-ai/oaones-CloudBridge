<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.LoginController" %>
<%
    // 统一登录页的接口入口（login.settings / login.smsSend / login.smsLogin /
    // login.tenants / login.qrCreate / login.qrStatus / login.qrLogin / login.qrCancel）。
    //
    // 为什么不用 /oaones/WeCom/api.jsp：那里会先做 OA 登录校验（未登录直接返回 401），
    // 而登录页恰恰是**没有登录会话**的页面，员工点“扫码登录”时必然被挡掉。
    // 因此这里与 appEntry.jsp / WeComCallback.jsp 同一口径：不经 api.jsp，直接调用控制器；
    // 控制器是公共的 com.customization.oaones.appsyn.controller.LoginController
    // （企业应用扫码共用一张二维码；个人微信使用官方二维码，但共用一份票据，
    // 见 LoginController 类注释）。
    //
    // 安全口径：本入口只回“哪些平台/租户可以扫码、二维码图片地址是什么、扫码进行到哪一步”，
    // 不发任何 Secret、不接受任何“要登录成谁”的参数——登录身份一律由扫码落地页
    // 按“平台 code 换回的成员 + 人员绑定关系”核实（见各连接器的 ScanLoginServiceImpl），
    // 且登录会话只在 login.qrLogin（PC 的请求）里写入。
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    // 统一走 dispatch：其中 login.qrLogin（用一次性令牌写登录会话）必须在
    // **PC 自己的请求**里执行，Set-Cookie 才会落到发起登录的浏览器上
    JSONObject oaonesLoginResult = new LoginController().dispatch(request, response);
    out.print(oaonesLoginResult == null
            ? "{\"code\":-1,\"msg\":\"无返回\"}" : oaonesLoginResult.toJSONString());
%>
