<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  「统一认证报表」页主体：筛选栏 + 数字看板 + 今日堆柱状图 + 两个折线图。
  只是标记与容器，交互脚本统一在 /oaones/appsyn/js/authReport.js。
--%>
<div class="card" id="arFilterCard">
    <div class="filter-bar" style="margin-bottom:0;">
        <span class="filter-label">分部</span>
        <span class="ms" id="arSubMs">
            <button type="button" class="btn small secondary ms-btn" id="arSubBtn">全部分部 ▾</button>
            <span class="ms-pop" id="arSubPop">
                <span class="ms-tools">
                    <a href="javascript:void(0)" id="arSubAll">全选</a>
                    <a href="javascript:void(0)" id="arSubNone">清空</a>
                </span>
                <span class="ms-list" id="arSubList"></span>
            </span>
        </span>
        <span class="filter-label">租户</span>
        <span class="ms" id="arCfgMs">
            <button type="button" class="btn small secondary ms-btn" id="arCfgBtn">全部租户 ▾</button>
            <span class="ms-pop" id="arCfgPop">
                <span class="ms-tools">
                    <a href="javascript:void(0)" id="arCfgAll">全选</a>
                    <a href="javascript:void(0)" id="arCfgNone">清空</a>
                </span>
                <span class="ms-list" id="arCfgList"></span>
            </span>
        </span>
        <span class="filter-label">日期</span>
        <span class="dt-field" style="width:150px;">
            <input type="text" id="arFrom" data-oaones-date="date" autocomplete="off"
                   data-lpignore="true" placeholder="起 yyyy-MM-dd">
        </span>
        <span class="filter-label">~</span>
        <span class="dt-field" style="width:150px;">
            <input type="text" id="arTo" data-oaones-date="date" autocomplete="off"
                   data-lpignore="true" placeholder="止 yyyy-MM-dd">
        </span>
        <button class="btn" id="arSearchBtn">查询</button>
        <button class="btn secondary" id="arResetBtn">重置</button>
    </div>
    <p class="notice" id="arTip" style="display:none;color:var(--oa-warning);margin:10px 0 0;"></p>
</div>

<div class="card">
    <div class="toolbar" style="justify-content:space-between;">
        <h3 style="margin:0;">租户人数与绑定（总人数 / 已绑定 / 未绑定）</h3>
        <span style="color:var(--oa-g-500);font-size:12px;" id="arCardNote"></span>
    </div>
    <div class="kpi-wrap" id="arCards">
        <div class="kpi-empty">加载中…</div>
    </div>
</div>

<div class="card">
    <div class="toolbar" style="justify-content:space-between;">
        <h3 style="margin:0;">今日新增绑定 / 今日统一认证登录</h3>
        <span style="color:var(--oa-g-500);font-size:12px;" id="arTodayNote"></span>
    </div>
    <div class="chart-box" id="arTodayChart"></div>
</div>

<div class="card">
    <div class="toolbar" style="justify-content:space-between;">
        <h3 style="margin:0;">趋势统计</h3>
        <span class="sub-tabs" style="border:none;margin:0;" id="arGrainTabs">
            <button type="button" class="active" data-grain="day">按日</button>
            <button type="button" data-grain="month">按月</button>
        </span>
    </div>
    <div class="chart-box" id="arBindChart"></div>
    <div class="chart-box" id="arLoginChart" style="margin-top:14px;"></div>
</div>
