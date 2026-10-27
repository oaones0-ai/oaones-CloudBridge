<%@ page language="java" contentType="text/plain; charset=UTF-8" pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" session="true" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * Nginx auth_request 专用会话校验端点。
     *
     * 不能用“Cookie 里有没有 ecology_JSessionid”判断登录：HrmUserVarify#getUser
     * 内部用的是 request.getSession(true)，未登录用户访问登录页后也会拿到这个 Cookie。
     * 这里按平台自己的登录态（session 里的 weaver_user@bean）判断：
     *   已登录 -> 200
     *   未登录 / 会话已失效 -> 401
     * 响应体只放 1/0，Nginx 只认状态码，不会把内容展示给用户。
     */
    response.setHeader("Cache-Control", "no-store");
    User oaonesAuthUser = HrmUserVarify.getUser(request, response);
    if (oaonesAuthUser == null) {
        response.setStatus(401);
        out.print("0");
    } else {
        response.setStatus(200);
        out.print("1");
    }
%>
