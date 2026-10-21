<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  日程同步（独立一级菜单：/oaones/scheduleSync/scheduleSync.jsp）

  与「会议同步」并列的另一条独立一级菜单：把 OA 里“分部下的日程”（E9 WorkPlan）同步到
  第三方平台（企业微信 / 钉钉 / 飞书）的日程。

  会议同步与日程同步共用同一个页壳 / 面板 / 脚本（/oaones/meetingSync/meetingSync.jsp 与
  /oaones/meetingSync/mtgSyncPanel.jsp），只换 biz（会议 0 / 日程 1）；
  因此本页只做一次带 biz=1 的服务端 forward（浏览器地址仍是本页，平台切换链接也回本页）。
--%>
<% request.setCharacterEncoding("UTF-8"); %>
<jsp:forward page="/oaones/meetingSync/meetingSync.jsp">
    <jsp:param name="biz" value="1"/>
</jsp:forward>
