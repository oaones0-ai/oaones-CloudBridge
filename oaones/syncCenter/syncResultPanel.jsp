<%@ page pageEncoding="UTF-8" %>
<%--
  同步结果页主体：按平台 / 租户 / requestid 等筛选，以部门、分部、人员为数据行展示。
  页面壳：syncCenter/syncResult.jsp；交互脚本：js/syncResult.js。
--%>
<div class="card">
    <div class="filter-bar" id="syncResultFilterBar"
         style="flex-wrap:wrap;<%= Boolean.TRUE.equals(request.getAttribute("oaonesHideFilters")) ? "display:none;" : "" %>">
        <span class="filter-label">平台</span>
        <select id="syncResultPlatform" style="width:130px;">
            <option value="">全部平台</option>
        </select>
        <span class="filter-label">租户</span>
        <select id="syncResultTenant" style="width:220px;">
            <option value="">全部租户</option>
        </select>
        <span class="filter-label">requestid</span>
        <input type="text" id="syncResultRequestId" inputmode="numeric"
               placeholder="OA 流程请求ID" autocomplete="off" data-lpignore="true" style="width:150px;"
               title="按 OA 流程 requestid 精确查询；同一次流程跨平台、跨租户写入的结果都会列出">
        <span class="filter-label">批次ID</span>
        <input type="text" id="syncResultLogId" inputmode="numeric"
               placeholder="同步批次ID" autocomplete="off" data-lpignore="true" style="width:120px;"
               title="按 uf_oaones_sync_log.id 精确查询某一次同步批次">
        <span class="filter-label">方向</span>
        <select id="syncResultDirection" style="width:140px;">
            <option value="">全部方向</option>
            <option value="0">OA → 第三方</option>
            <option value="1">第三方 → OA</option>
        </select>
        <span class="filter-label">对象</span>
        <select id="syncResultItemType" style="width:110px;">
            <option value="">全部对象</option>
            <option value="0">部门</option>
            <option value="2">分部</option>
            <option value="1">人员</option>
        </select>
        <span class="filter-label">动作</span>
        <select id="syncResultOp" style="width:100px;">
            <option value="">全部动作</option>
            <option value="0">新增</option>
            <option value="1">更新</option>
            <option value="2">跳过</option>
            <option value="3">失败</option>
        </select>
        <span class="filter-label">关键字</span>
        <input type="text" id="syncResultKeyword"
               placeholder="名称 / ID / 租户 / 说明" autocomplete="off" data-lpignore="true" style="width:190px;">
        <span class="filter-label">时间</span>
        <span class="dt-field" style="width:185px;">
            <input type="text" id="syncResultFrom" data-oaones-date="datetime"
                   data-oaones-date-time="00:00:00" autocomplete="off" data-lpignore="true"
                   placeholder="起 yyyy-MM-dd HH:mm:ss">
        </span>
        <span class="filter-label">~</span>
        <span class="dt-field" style="width:185px;">
            <input type="text" id="syncResultTo" data-oaones-date="datetime"
                   data-oaones-date-time="23:59:59" autocomplete="off" data-lpignore="true"
                   placeholder="止 yyyy-MM-dd HH:mm:ss">
        </span>
        <button type="button" class="btn" id="syncResultSearchBtn">查询</button>
        <button type="button" class="btn secondary" id="syncResultResetBtn">重置</button>
    </div>

    <div class="summary detail-summary" id="syncResultSummary"
         style="margin:0 0 10px;<%= Boolean.TRUE.equals(request.getAttribute("oaonesHideSummary")) ? "display:none;" : "" %>"></div>
    <p class="notice" id="syncResultTip"
       style="margin:0 0 10px;display:none;<%= Boolean.TRUE.equals(request.getAttribute("oaonesHideSummary")) ? "display:none!important;" : "" %>"></p>

    <div class="table-wrap">
        <table class="grid" id="syncResultTable">
            <thead>
            <tr>
                <th style="width:145px;">执行时间</th>
                <th style="width:80px;">平台</th>
                <th style="width:160px;">租户</th>
                <th style="width:105px;">requestid</th>
                <th style="width:130px;">方向</th>
                <th style="width:70px;">对象</th>
                <th style="width:70px;">动作</th>
                <th style="width:170px;">OA 对象</th>
                <th style="width:180px;">第三方对象</th>
                <th style="width:160px;">目标父节点</th>
                <th style="min-width:260px;">同步过程</th>
                <th style="width:85px;">批次ID</th>
            </tr>
            </thead>
            <tbody id="syncResultBody"></tbody>
        </table>
        <div class="empty" id="syncResultEmpty" style="display:none;">暂无匹配的部门 / 人员同步结果</div>
    </div>
    <div class="pager" id="syncResultPager"></div>
</div>
