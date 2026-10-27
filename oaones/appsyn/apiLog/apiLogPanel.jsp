<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  「接口日志中心」页主体：过滤栏 + 汇总条 + 列表 + 详情弹框。
  只是标记与容器，交互脚本统一在 /oaones/appsyn/js/apiLog.js。
--%>
<div class="card">
<div class="filter-bar">
    <span class="filter-label">平台</span>
    <select id="apiLogPlatform" name="apiLogPlatform" style="width:130px;">
        <option value="">全部平台</option>
    </select>
    <span class="filter-label">租户</span>
    <select id="apiLogTenant" name="apiLogTenant" style="width:180px;">
        <option value="">全部租户</option>
    </select>
    <span class="filter-label">接口类型</span>
    <select id="apiLogType" name="apiLogType" style="width:150px;">
        <option value="">全部类型</option>
    </select>
    <span class="filter-label">方向</span>
    <select id="apiLogDirection" name="apiLogDirection" style="width:130px;">
        <option value="">全部方向</option>
        <option value="0">发往第三方</option>
        <option value="1">第三方调用</option>
    </select>
    <span class="filter-label">结果</span>
    <select id="apiLogSuccess" name="apiLogSuccess" style="width:110px;">
        <option value="">全部</option>
        <option value="1">成功</option>
        <option value="0">失败</option>
    </select>
    <span class="filter-label">关键字</span>
    <input type="text" id="apiLogKeyword" autocomplete="off" data-lpignore="true"
           placeholder="路径 / 租户 / 错误" style="width:160px;">
    <span class="filter-label">时间</span>
    <span class="dt-field" style="width:200px;">
        <input type="text" id="apiLogFrom" data-oaones-date="datetime" data-oaones-date-time="00:00:00"
               autocomplete="off" data-lpignore="true" placeholder="起 yyyy-MM-dd HH:mm:ss">
    </span>
    <span class="filter-label">~</span>
    <span class="dt-field" style="width:200px;">
        <input type="text" id="apiLogTo" data-oaones-date="datetime" data-oaones-date-time="23:59:59"
               autocomplete="off" data-lpignore="true" placeholder="止 yyyy-MM-dd HH:mm:ss">
    </span>
    <button class="btn" id="apiLogSearchBtn">查询</button>
    <button class="btn secondary" id="apiLogResetBtn">重置</button>
    <button class="btn secondary" id="apiLogCleanBtn" style="display:none;"
            title="按保留天数清理历史接口日志（仅系统管理员 / 被授权人员可用）">清理</button>
</div>

<div class="summary detail-summary" id="apiLogSummary" style="margin:0 0 10px;"></div>

<div class="table-wrap">
    <table class="grid" id="apiLogTable">
        <thead>
        <tr>
            <th style="width:150px;">调用时间</th>
            <th style="width:90px;">平台</th>
            <th style="width:150px;">租户</th>
            <th style="width:120px;">接口类型</th>
            <th style="width:100px;">方向</th>
            <th style="width:70px;">方法</th>
            <th style="width:230px;">接口路径</th>
            <th style="width:70px;">HTTP</th>
            <th style="width:90px;">结果</th>
            <th style="width:80px;">耗时</th>
            <th style="width:90px;">操作</th>
        </tr>
        </thead>
        <tbody id="apiLogBody"></tbody>
    </table>
</div>
<div class="pager" id="apiLogPager"></div>

<div class="modal-mask" id="apiLogDetailMask"></div>
<div class="modal" id="apiLogDetailModal" style="width:900px;display:none;">
    <div class="modal-head">
        <span id="apiLogDetailTitle">接口日志详情</span>
        <span class="modal-head-actions">
            <button type="button" class="btn small secondary" id="apiLogDetailFullBtn"
                    title="整页全屏查看报文（再点一次或按 Esc 退出）">全屏</button>
            <button type="button" class="modal-close" id="apiLogDetailClose">×</button>
        </span>
    </div>
    <div class="modal-body" style="max-height:70vh;overflow:auto;">
        <div class="summary detail-summary" id="apiLogDetailSummary"></div>
        <div class="form-row" style="margin-top:14px;">
            <label>接口地址</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="apiLogDetailUrl"
                            title="复制本次调用的接口地址">复制</button>
                </div>
                <div class="detail" id="apiLogDetailUrl"></div>
            </div>
        </div>
        <div class="form-row">
            <label>请求报文</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="apiLogDetailRequest"
                            title="复制请求报文">复制</button>
                </div>
                <div class="detail" id="apiLogDetailRequest"></div>
            </div>
        </div>
        <div class="form-row">
            <label>返回报文</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="apiLogDetailResponse"
                            title="复制返回报文">复制</button>
                </div>
                <div class="detail" id="apiLogDetailResponse"></div>
            </div>
        </div>
        <div class="form-row" id="apiLogDetailStackRow">
            <label>异常堆栈</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" data-copy="apiLogDetailStack"
                            title="复制异常堆栈">复制</button>
                </div>
                <div class="detail" id="apiLogDetailStack"></div>
            </div>
        </div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" id="apiLogDetailCopyAllBtn"
                title="把接口地址、请求报文、返回报文与异常堆栈按段落一起复制">复制全部报文</button>
        <button class="btn" id="apiLogDetailOkBtn">关闭</button>
    </div>
</div>
</div>
