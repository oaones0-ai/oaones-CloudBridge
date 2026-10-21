<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    /* 三个平台共用本片段：平台由引入方决定（request 属性 oaonesPBPlatform 优先，其次 platform 参数）；
       平台文案统一用 oaonesPBShort（企微 / 钉钉 / 飞书）与 oaonesPBFull（企业微信 / 钉钉 / 飞书）。 */
    String oaonesPBPlatform = (String) request.getAttribute("oaonesPBPlatform");
    if (oaonesPBPlatform == null || oaonesPBPlatform.trim().length() == 0) {
        oaonesPBPlatform = request.getParameter("platform");
    }
    if (oaonesPBPlatform == null) {
        oaonesPBPlatform = "";
    }
    oaonesPBPlatform = oaonesPBPlatform.trim().toLowerCase();
    boolean oaonesPBDd = "dingtalk".equals(oaonesPBPlatform);
    boolean oaonesPBFs = "feishu".equals(oaonesPBPlatform);
    String oaonesPBFull = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企业微信");
    String oaonesPBShort = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企微");
    // 租户标识 / 应用标识在各平台的叫法：企微 corpid + AgentId，钉钉 AppKey + AgentId，飞书 App ID + App ID
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
%>

<%
    String userSwitchActive = request.getParameter("active");
    if (userSwitchActive == null) {
        userSwitchActive = "";
    }
    // 保持租户上下文：地址带 cfg_id（从二级标题的“人员绑定”页签进来）时，模块内切换也带上它
    String userSwitchCfgId = request.getParameter("cfg_id");
    if (userSwitchCfgId == null) {
        userSwitchCfgId = "";
    }
    userSwitchCfgId = userSwitchCfgId.trim();
    if (!userSwitchCfgId.matches("\\d+")) {
        userSwitchCfgId = "";
    }
    String userSwitchCfgQuery = userSwitchCfgId.length() == 0 ? "" : ("?cfg_id=" + userSwitchCfgId);
    // 人员绑定三个页签都是同一个通用页（带 platform）；cfg_id 用 & 拼在后面
    String userSwitchCfgAnd = userSwitchCfgId.length() == 0 ? "" : ("&cfg_id=" + userSwitchCfgId);
%>
<div class="user-module-switch">
    <div class="view-switch">
        <button type="button" class="view-btn<% if ("detail".equals(userSwitchActive)) { %> active<% } %>"
                onclick="location.href='/oaones/userBind/userBind.jsp?platform=<%=oaonesPBPlatform%><%=userSwitchCfgAnd%>'">绑定详情</button>
        <button type="button" class="view-btn<% if ("rule".equals(userSwitchActive)) { %> active<% } %>"
                onclick="location.href='/oaones/userBind/userRule.jsp?platform=<%=oaonesPBPlatform%><%=userSwitchCfgAnd%>'">默认绑定规则</button>
        <button type="button" class="view-btn<% if ("ruleList".equals(userSwitchActive)) { %> active<% } %>"
                onclick="location.href='/oaones/userBind/userRuleList.jsp?platform=<%=oaonesPBPlatform%><%=userSwitchCfgAnd%>'">规则列表</button>
    </div>
</div>
