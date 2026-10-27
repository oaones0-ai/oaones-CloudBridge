<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  「操作监控平台」页主体：过滤栏 + 汇总条 + 列表。
  只是标记与容器，交互脚本统一在 /oaones/appsyn/js/opMonitor.js。
--%>
<div class="card" id="opMonitorCard">
<div class="filter-bar">
    <span class="filter-label">人员</span>
    <input type="text" id="opMonitorUser" autocomplete="off" data-lpignore="true"
           placeholder="姓名 / 登录名" style="width:150px;">
    <span class="filter-label">事件</span>
    <select id="opMonitorType" name="opMonitorType" style="width:130px;">
        <option value="">全部事件</option>
    </select>
    <span class="filter-label">菜单</span>
    <select id="opMonitorMenu" name="opMonitorMenu" style="width:220px;">
        <option value="">全部菜单</option>
    </select>
    <span class="filter-label">关键字</span>
    <input type="text" id="opMonitorKeyword" autocomplete="off" data-lpignore="true"
           placeholder="菜单 / 地址 / IP" style="width:160px;">
    <span class="filter-label">时间</span>
    <span class="dt-field" style="width:200px;">
        <input type="text" id="opMonitorFrom" data-oaones-date="datetime" data-oaones-date-time="00:00:00"
               autocomplete="off" data-lpignore="true" placeholder="起 yyyy-MM-dd HH:mm:ss">
    </span>
    <span class="filter-label">~</span>
    <span class="dt-field" style="width:200px;">
        <input type="text" id="opMonitorTo" data-oaones-date="datetime" data-oaones-date-time="23:59:59"
               autocomplete="off" data-lpignore="true" placeholder="止 yyyy-MM-dd HH:mm:ss">
    </span>
    <button class="btn" id="opMonitorSearchBtn">查询</button>
    <button class="btn secondary" id="opMonitorResetBtn">重置</button>
    <button class="btn secondary" id="opMonitorCleanBtn" style="display:none;"
            title="按保留天数清理历史操作日志（仅系统管理员 / 被授权人员可用）">清理</button>
</div>

<div class="summary detail-summary" id="opMonitorSummary" style="margin:0 0 10px;"></div>

<div class="table-wrap">
    <table class="grid" id="opMonitorTable">
        <thead>
        <tr>
            <th style="width:150px;">时间</th>
            <th style="width:160px;">人员</th>
            <th style="width:150px;">部门</th>
            <th style="width:90px;">事件</th>
            <th style="width:170px;">菜单</th>
            <th style="width:260px;">访问地址</th>
            <th style="width:130px;">IP</th>
            <th style="width:80px;">客户端</th>
        </tr>
        </thead>
        <tbody id="opMonitorBody"></tbody>
    </table>
</div>
<div class="pager" id="opMonitorPager"></div>
</div>
