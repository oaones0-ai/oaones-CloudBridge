<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesApiLog" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.controller.feishu.AppBindController" %>
<%
    // 飞书工作台应用主页的免登录入口：
    //   ① 不做 OA 登录校验 —— 用户是在飞书里点开应用，浏览器里没有 OA 会话，
    //      身份由“飞书网页授权 code + 该应用/租户的 Secret”核实（见 AppBindServiceImpl.workbenchLogin）；
    //   ② 从飞书工作台打开时，飞书会在本页地址后自动追加 code（和 state），
    //      核实通过后就地写入 OA 登录会话，再用“极简页面 + JS 跳转”进入绑定的 OA 页面
    //      （不用 302：`#` 之后带 ?参数 的地址会被 302 重排而丢掉页面参数，见下方注释）；
    //   ③ 免登录入口地址（应用主页地址）由“应用绑定”页按“cfg_id + agent_id”生成，可整段复制到飞书开放平台。
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    JSONObject oaonesResult = new AppBindController().entry(request, response);
    boolean oaonesOk = oaonesResult != null && oaonesResult.getIntValue("code") == 0;
    // 接口日志中心：记录这一次“飞书工作台免登入口”的调用（第三方平台→OA 的入口）
    OaonesApiLog.inbound(OaonesConst.TYPE_FEISHU,
            OaonesUtil.i(OaonesUtil.s(request.getParameter("cfg_id")), 0), "",
            OaonesApiLog.TYPE_LOGIN, "/oaones/appBind/Feishu/appEntry.jsp",
            OaonesApiLog.requestSummary(request), oaonesResult == null ? "" : oaonesResult.toJSONString(),
            oaonesOk, oaonesResult == null ? "" : String.valueOf(oaonesResult.getIntValue("code")),
            oaonesResult == null ? "无返回" : OaonesUtil.s(oaonesResult.getString("msg")), "0");
    JSONObject oaonesData = oaonesResult == null ? null : oaonesResult.getJSONObject("data");
    String oaonesRedirect = oaonesData == null ? "" : OaonesUtil.s(oaonesData.getString("redirect_url"));
    if (oaonesOk && oaonesRedirect.length() > 0) {
        boolean oaonesOauthJump = oaonesData != null && oaonesData.getBooleanValue("oauth");
        if (oaonesOauthJump) {
            // 去飞书网页授权地址：保持直接 302（由飞书客户端接管授权）
            response.sendRedirect(oaonesRedirect);
            return;
        }
        // 免登录成功后的落地跳转：不用 302，先返回一个极简页面再 JS 跳转。
        // 原因①（主要）：E9 的地址形如 /wui/index.html#/main/cube/search?customid=132 ——
        //   `#` 之后还有 page 参数，Servlet 容器/代理生成 302 的 Location 时会把 query 段与
        //   anchor(#) 段重排成 /wui/index.html?customid=132#/main/cube/search，页面参数就丢了，
        //   落地页会退回门户首页；JS 跳转是浏览器按原字符串解析，地址不会被改写。
        // 原因②：部分内置浏览器（飞书 WKWebView）对 302 响应里的 Set-Cookie 处理不稳定，
        //   会丢掉刚写入的 OA 会话；JS 跳转时 Cookie 已落到客户端。
        response.setContentType("text/html;charset=UTF-8");
        String oaonesJsTarget = com.alibaba.fastjson.JSON.toJSONString(oaonesRedirect).replace("<", "\\u003c");
        String oaonesHtmlTarget = weaver.general.Util.toHtml(oaonesRedirect);
        out.print("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>正在进入 OA（飞书） - OAones</title></head>"
                + "<body style=\"font-family:inherit;padding:24px;text-align:center;color:#666e7d\">"
                + "<div id=\"oaones-jump-text\">正在进入 OA…</div>"
                + "<p><a href=\"" + oaonesHtmlTarget + "\" style=\"color:#4a6fa5\">如果没有自动跳转，请点击这里</a></p>"
                + "<script>(function(){var u=" + oaonesJsTarget + ";"
                + "try{location.replace(u);}catch(e){location.href=u;}})();</script>"
                + "</body></html>");
        return;
    }
    String oaonesMsg = oaonesResult == null ? "免登录失败，请从飞书工作台重新打开该应用"
            : OaonesUtil.s(oaonesResult.getString("msg"));
    if (oaonesMsg.length() == 0) {
        oaonesMsg = "免登录失败，请从飞书工作台重新打开该应用";
    }
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>飞书工作台免登录 - OAones</title>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
</head>
<body class="wx-callback-page">
<div class="wx-callback-icon">&#33;</div>
<div class="wx-callback-title fail">无法免登录进入 OA</div>
<div class="wx-callback-text"><%=weaver.general.Util.toHtml(oaonesMsg)%></div>
<a class="wx-callback-link" href="/oaones/accountCenter/MyAccount.jsp?tab=feishu">打开“个人账号中心”扫码绑定本人飞书</a>
<br>
<a class="wx-callback-link" href="/wui/index.html">前往 OA 登录页</a>
</body>
</html>
