<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  考勤同步页面主体（标记片段）：三个平台共用一份（页面 attSync.jsp 用 <jsp:include> 引它）。
  内容 = 三个页签的面板 + 编辑弹框 + 选择弹框 + 明细 / 预测弹框；
  所有行为在 js/attSync.js（window.OaonesAttSync）里实现，本文件只放结构。
--%>

<%-- ============ 页签 1：同步设置 ============ --%>
<div class="tab-panel active" data-att-panel="setting">
    <%-- 租户页签在页壳里（页面级，三个页签共用）：这里只放选中租户的卡片。 --%>
    <div id="attTenantBox"></div>
    <div class="notice" id="attSchedTip" style="margin-top:12px;">正在读取内置调度器状态…</div>
</div>

<%-- ============ 页签 2：同步日志（批次） ============ --%>
<div class="tab-panel" data-att-panel="log">
    <div class="toolbar" style="flex-wrap:wrap;">
        <div class="toolbar-left" style="flex-wrap:wrap;">
            <span class="notice">当前租户：<b id="attLogTenant">—</b></span>
            <select id="attLogDir" class="oa-select">
                <option value="">全部方向</option>
                <option value="1">第三方 → OA</option>
                <option value="2">OA → 第三方</option>
            </select>
            <select id="attLogResult" class="oa-select">
                <option value="">全部结果</option>
                <option value="0">成功</option>
                <option value="3">失败</option>
            </select>
            <select id="attLogTrigger" class="oa-select">
                <option value="">全部触发方式</option>
                <option value="0">计划任务</option>
                <option value="1">手工执行</option>
                <option value="2">重推</option>
            </select>
            <input type="text" id="attLogFrom" data-oaones-date="datetime" placeholder="执行时间起">
            <input type="text" id="attLogTo" data-oaones-date="datetime" placeholder="执行时间止">
            <input type="text" id="attLogKeyword" placeholder="设置名称 / 说明">
            <button class="btn small" id="attLogQueryBtn">查询</button>
            <button class="btn small secondary" id="attLogResetBtn">重置</button>
        </div>
        <div><span class="notice" id="attLogTotal"></span></div>
    </div>
    <table class="grid" id="attLogTable">
        <thead>
        <tr>
            <th>执行时间</th>
            <th>租户</th>
            <th>设置</th>
            <th>方向</th>
            <th>触发方式</th>
            <th>时间口径 / 起止</th>
            <th>新增 / 更新 / 跳过 / 失败</th>
            <th>平台请求</th>
            <th>结果</th>
            <th>说明</th>
            <th>操作</th>
        </tr>
        </thead>
        <tbody id="attLogBody"></tbody>
    </table>
    <div id="attLogPager"></div>
</div>

<%-- ============ 页签 3：打卡明细 ============ --%>
<div class="tab-panel" data-att-panel="item">
    <div class="toolbar" style="flex-wrap:wrap;">
        <div class="toolbar-left" style="flex-wrap:wrap;">
            <span class="notice">当前租户：<b id="attItemTenant">—</b></span>
            <select id="attItemDir" class="oa-select">
                <option value="">全部方向</option>
                <option value="1">第三方 → OA</option>
                <option value="2">OA → 第三方</option>
            </select>
            <select id="attItemOp" class="oa-select">
                <option value="">全部结果</option>
                <option value="0">新增</option>
                <option value="1">更新</option>
                <option value="2">跳过</option>
                <option value="3">失败</option>
            </select>
            <input type="text" id="attItemFrom" data-oaones-date="date" placeholder="打卡日期起">
            <input type="text" id="attItemTo" data-oaones-date="date" placeholder="打卡日期止">
            <input type="text" id="attItemKeyword" placeholder="姓名 / 登录名 / 平台账号 / 说明">
            <button class="btn small" id="attItemQueryBtn">查询</button>
            <button class="btn small secondary" id="attItemResetBtn">重置</button>
        </div>
        <div><span class="notice" id="attItemTotal"></span></div>
    </div>
    <table class="grid" id="attItemTable">
        <thead>
        <tr>
            <th>执行时间</th>
            <th>租户</th>
            <th>人员</th>
            <th>平台账号</th>
            <th>打卡日期时间</th>
            <th>打卡类型</th>
            <th>数据来源</th>
            <th>结果</th>
            <th>说明</th>
            <th>操作</th>
        </tr>
        </thead>
        <tbody id="attItemBody"></tbody>
    </table>
    <div id="attItemPager"></div>
</div>

<%-- ============ 弹框 1：编辑同步设置 ============ --%>
<div class="modal" id="attCfgModal" style="width:900px;">
    <div class="modal-head">
        <span id="attCfgModalTitle">新增同步设置</span>
        <button type="button" class="modal-x" data-att-close="attCfgModal" title="关闭"></button>
    </div>
    <div class="modal-body">
        <input type="hidden" id="attCfgId" value="0">
        <input type="hidden" id="attCfgType" value="0">
        <input type="hidden" id="attCfgTenant" value="0">

        <%-- 一次配完（2026-10-15 现场反馈）：考勤应用与租户绑定、在租户卡片上维护一次，
             弹框里不再有“第 1 步选应用 + 校验”那一层，只留这条设置自己的配置。 --%>
        <div id="attCfgBody">
        <div class="form-group">
            <div class="form-group-title">同步规则</div>
            <div class="form-row">
                <label>租户</label>
                <div><span class="notice" id="attCfgTenantText">—</span></div>
            </div>
            <div class="form-row">
                <label>考勤应用</label>
                <div><span class="notice" id="attCfgAppText">—</span></div>
            </div>
            <div class="form-row">
                <label>设置名称<span class="req-mark">*</span></label>
                <div>
                    <input type="text" id="attCfgName" maxlength="60" placeholder="例：工作日早上补跑">
                    <div class="field-note">同一租户下名称唯一；同一「平台 + 租户 + 方向」可以配多条设置
                        （不同范围 / 数据类型 / 时间口径 / 频率各一条）。</div>
                </div>
            </div>
            <div class="form-row">
                <label>同步方向<span class="req-mark">*</span></label>
                <div>
                    <select id="attCfgDir" class="oa-select">
                        <option value="0">双向同步（第三方 → OA、OA → 第三方）</option>
                        <option value="1">仅 第三方 → OA（把平台打卡写进 OA）</option>
                        <option value="2">仅 OA → 第三方（把 OA 打卡写到平台）</option>
                    </select>
                </div>
            </div>
            <div class="form-row">
                <label>启用</label>
                <div>
                    <label class="oa-check"><input type="checkbox" id="attCfgEnabled"> 开启这条设置</label>
                    <div class="field-note">新增设置默认<b>停用</b>，需显式开启；停用后只允许手工执行。</div>
                </div>
            </div>
        </div>

        <div class="form-group">
            <div class="form-group-title">同步范围</div>
            <div class="form-row">
                <label>分部</label>
                <div style="display:flex;gap:8px;align-items:center;">
                    <input type="text" id="attScopeSubText" readonly placeholder="全部（未限定分部）">
                    <button class="btn small secondary" id="attScopeSubBtn">选择</button>
                </div>
            </div>
            <div class="form-row">
                <label>部门</label>
                <div style="display:flex;gap:8px;align-items:center;">
                    <input type="text" id="attScopeDeptText" readonly placeholder="全部（未限定部门）">
                    <button class="btn small secondary" id="attScopeDeptBtn">选择</button>
                </div>
            </div>
            <div class="form-row">
                <label>人员</label>
                <div style="display:flex;gap:8px;align-items:center;">
                    <input type="text" id="attScopeUserText" readonly placeholder="全部（未限定人员）">
                    <button class="btn small secondary" id="attScopeUserBtn">选择</button>
                </div>
            </div>
            <input type="hidden" id="attScopeSubs" value="">
            <input type="hidden" id="attScopeDepts" value="">
            <input type="hidden" id="attScopeUsers" value="">
        </div>

        <div class="form-group">
            <div class="form-group-title">数据类型与打卡时段</div>
            <div class="form-row">
                <label>数据类型<span class="req-mark">*</span></label>
                <div>
                    <label class="oa-check"><input type="checkbox" id="attDataNormal"> 正常打卡</label>
                    <label class="oa-check"><input type="checkbox" id="attDataRemedy"> 补签卡（补卡）</label>
                    <div class="field-note">正常打卡默认包含平台的外勤 / 出差打卡（日志里保留平台原始类型，
                        是否算有效打卡交给泛微标准考勤规则判定）。</div>
                </div>
            </div>
            <div class="form-row">
                <label>打卡时段</label>
                <div>
                    <label class="oa-check"><input type="checkbox" id="attPeriodIn"> 签到（上班卡）</label>
                    <label class="oa-check"><input type="checkbox" id="attPeriodOut"> 签退（下班卡）</label>
                    <div class="field-note">默认两种都同步；只勾一种时另一种不会写入 / 推送。</div>
                </div>
            </div>
        </div>

        <div class="form-group">
            <div class="form-group-title">数据时间口径</div>
            <div class="form-row">
                <label>时间口径<span class="req-mark">*</span></label>
                <div>
                    <select id="attWinMode" class="oa-select">
                        <option value="0">当天数据（执行时刻所在自然日）</option>
                        <option value="1">前一天数据</option>
                        <option value="3">最近 N 天（补跑漏数据）</option>
                        <option value="2">任意时间范围（手工指定）</option>
                    </select>
                </div>
            </div>
            <div class="form-row" id="attWinDaysRow">
                <label>N（天）</label>
                <div><input type="number" id="attWinDays" min="1" max="90" step="1" value="2"></div>
            </div>
            <div class="form-row" id="attWinRangeRow">
                <label>时间范围<span class="req-mark">*</span></label>
                <div style="display:flex;gap:8px;align-items:center;">
                    <span class="dt-field" style="width:206px;">
                        <input type="text" id="attWinFrom" data-oaones-date="datetime"
                               data-oaones-date-time="00:00:00" placeholder="开始（含秒）"
                               autocomplete="off" data-lpignore="true">
                    </span>
                    <span class="notice">~</span>
                    <span class="dt-field" style="width:206px;">
                        <input type="text" id="attWinTo" data-oaones-date="datetime"
                               data-oaones-date-time="23:59:59" placeholder="结束（含秒）"
                               autocomplete="off" data-lpignore="true">
                    </span>
                </div>
            </div>
        </div>

        <div class="form-group">
            <div class="form-group-title">计划任务</div>
            <div class="form-row">
                <label>同步方式</label>
                <div>
                    <select id="attPlanMode" class="oa-select">
                        <option value="0">按时间段（推荐）</option>
                        <option value="1">高级模式（cron 表达式）</option>
                    </select>
                </div>
            </div>
            <div id="attPlanItemsBox" class="plan-items-box">
                <div class="mtg-plan-actions">
                    <button class="btn small secondary" id="attPlanAddBtn">+ 新增时间段</button>
                    <span class="muted">适用日 + 开始 / 结束时间 + 轮询间隔（分钟，下限 5）；轮询从每段开始时间起算，
                        结束时间早于开始时间 = 跨天；同一适用日下不允许重叠。</span>
                </div>
                <table class="grid">
                    <thead>
                    <tr>
                        <th style="width:140px;">适用日</th>
                        <th style="width:130px;">开始</th>
                        <th style="width:130px;">结束</th>
                        <th style="width:150px;">轮询间隔</th>
                        <th>说明</th>
                        <th style="width:70px;">操作</th>
                    </tr>
                    </thead>
                    <tbody id="attPlanItemBody"></tbody>
                </table>
            </div>
            <div id="attPlanCronBox">
                <div class="form-row">
                    <label>白天 / 夜间</label>
                    <div>
                        <input type="text" id="attDayCron" placeholder="白天 cron，例：0 0/30 9-18 * * ?">
                        <input type="text" id="attNightCron" placeholder="夜间 cron（留空 = 该时段不自动同步）">
                    </div>
                    <div class="field-note">Quartz 口径：秒 分 时 日 月 周；频率下限 5 分钟；留空表示该时段不自动同步。</div>
                </div>
                <div class="form-row">
                    <label>执行日</label>
                    <div>
                        <select id="attCronDayType" class="oa-select">
                            <option value="0">每天</option>
                            <option value="1">仅工作日</option>
                            <option value="2">仅非工作日</option>
                        </select>
                    </div>
                </div>
            </div>
            <div class="form-row">
                <label>工作日口径</label>
                <div>
                    <select id="attWorkdayGroup" class="oa-select"></select>
                    <div class="field-note">工作日 / 非工作日按泛微标准考勤算（考勤组的固定排班 / 工作日勾选
                        叠加节假日与调休，调配工作日算上班）；不指定则按周一至周五。</div>
                    <div class="field-note" id="attWorkdayNote"></div>
                </div>
            </div>
            <div class="form-row">
                <label>预测</label>
                <div style="display:flex;gap:8px;align-items:center;">
                    <button class="btn small secondary" id="attPreviewBtn">预测后 10 次</button>
                    <span class="notice">按当前表单里的配置试算，不会保存。</span>
                </div>
            </div>
        </div>

        <div class="form-group">
            <div class="form-group-title">备注</div>
            <div class="form-row">
                <label>备注</label>
                <div><input type="text" id="attCfgRemark" maxlength="200" placeholder="选填"></div>
            </div>
        </div>
        </div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" data-att-close="attCfgModal">取消</button>
        <button class="btn" id="attCfgSaveBtn">保存</button>
    </div>
</div>

<%-- ============ 弹框 2：选分部 / 选部门 / 选人员
     与「日程同步」同一套选择框：两列（ID + 名称）、单击选中 / 双击确定并退出、全选、
     分部与部门可切列表式 / 树状；打开时取快照，点「取消 / ×」回退本次改动。 ============ --%>
<div class="modal wx-pick-modal" id="attSubModal" style="width:760px;">
    <div class="modal-head">
        <span>选择分部（选中含其全部下级分部 / 部门）</span>
        <button type="button" class="modal-x" data-att-close="attSubModal" title="关闭"></button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="attSubSearch" placeholder="输入分部名称 / 编号搜索"
                   autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" id="attSubAllBtn"
                    data-pick-all="attSub">全选</button>
            <div class="view-switch mini" data-view-key="attSub">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list is-tree" id="attSubList"></div>
        <div class="wx-pick-note" id="attSubNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="attSubClearBtn" style="margin-right:auto;">清空选择</button>
        <button class="btn secondary" data-att-close="attSubModal">取消</button>
        <button class="btn" id="attSubOkBtn">确定</button>
    </div>
</div>

<div class="modal wx-pick-modal" id="attDeptModal" style="width:760px;">
    <div class="modal-head">
        <span>选择部门（选中含其全部下级部门）</span>
        <button type="button" class="modal-x" data-att-close="attDeptModal" title="关闭"></button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="attDeptSearch" placeholder="输入部门名称 / 编号搜索"
                   autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" id="attDeptAllBtn"
                    data-pick-all="attDept">全选</button>
            <div class="view-switch mini" data-view-key="attDept">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list is-tree" id="attDeptList"></div>
        <div class="wx-pick-note" id="attDeptNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="attDeptClearBtn" style="margin-right:auto;">清空选择</button>
        <button class="btn secondary" data-att-close="attDeptModal">取消</button>
        <button class="btn" id="attDeptOkBtn">确定</button>
    </div>
</div>

<div class="modal wx-pick-modal" id="attUserModal" style="width:760px;">
    <div class="modal-head">
        <span>选择人员</span>
        <button type="button" class="modal-x" data-att-close="attUserModal" title="关闭"></button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="attUserSearch" placeholder="姓名 / 登录名 / 工号"
                   autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small secondary" id="attUserSearchBtn">搜索</button>
            <button type="button" class="btn small secondary pick-all-btn" id="attUserAllBtn"
                    data-pick-all="attUser">全选</button>
        </div>
        <div class="wx-pick-list" id="attUserList"></div>
        <div class="wx-pick-note" id="attUserNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="attUserClearBtn" style="margin-right:auto;">清空选择</button>
        <button class="btn secondary" data-att-close="attUserModal">取消</button>
        <button class="btn" id="attUserOkBtn">确定</button>
    </div>
</div>

<%-- ============ 弹框 3：批次明细 ============ --%>
<div class="modal" id="attLogModal">
    <div class="modal-head">
        <span>同步批次明细</span>
        <button type="button" class="modal-x" data-att-close="attLogModal" title="关闭"></button>
    </div>
    <div class="modal-body modal-wide">
        <div id="attLogDetailHead"></div>
        <table class="grid">
            <thead>
            <tr>
                <th>人员</th>
                <th>平台账号</th>
                <th>打卡日期时间</th>
                <th>类型</th>
                <th>来源</th>
                <th>结果</th>
                <th>说明</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody id="attLogItemBody"></tbody>
        </table>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" data-att-close="attLogModal">关闭</button>
    </div>
</div>

<%-- ============ 弹框 4：预测后 10 次 ============ --%>
<div class="modal" id="attPreviewModal">
    <div class="modal-head">
        <span>预测后 10 次执行时间</span>
        <button type="button" class="modal-x" data-att-close="attPreviewModal" title="关闭"></button>
    </div>
    <div class="modal-body">
        <div class="notice" id="attPreviewRule"></div>
        <table class="grid">
            <thead>
            <tr>
                <th style="width:200px;">执行时间</th>
                <th>命中</th>
            </tr>
            </thead>
            <tbody id="attPreviewBody"></tbody>
        </table>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" data-att-close="attPreviewModal">关闭</button>
    </div>
</div>

<%-- ============ 弹框 5：校验结果 / 平台原文 ============ --%>
<div class="modal" id="attResultModal">
    <div class="modal-head">
        <span id="attResultTitle">校验结果</span>
        <button type="button" class="modal-x" data-att-close="attResultModal" title="关闭"></button>
    </div>
    <div class="modal-body">
        <div id="attResultBody"></div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" data-att-close="attResultModal">关闭</button>
    </div>
</div>
