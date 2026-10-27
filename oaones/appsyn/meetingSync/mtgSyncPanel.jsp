<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    // 会议同步页 biz=0、日程同步页 biz=1（见 /oaones/appsyn/scheduleSync/scheduleSync.jsp 的 forward）：
    // 「同步清单」的标题与“OA 会议ID / OA 日程ID”列名按 biz 显示。
    boolean oaonesBizSchedule = "1".equals(request.getParameter("biz"));
    String oaonesItemTitle = oaonesBizSchedule ? "日程同步清单" : "会议同步清单";
    String oaonesItemIdLabel = oaonesBizSchedule ? "OA日程ID" : "OA会议ID";
    String oaonesItemWord = oaonesBizSchedule ? "日程" : "会议";
    // 「计划任务」的白天 / 夜间分界：与后端同一个口径（oaones.appsyn.properties#mtg.pull.hour.from/to，
    // 默认 9:00–18:00 为白天），页面上只展示、不在这里改
    int oaonesDayFrom = com.customization.oaones.appsyn.common.OaonesProps.mtgPullHourFrom();
    int oaonesDayTo = com.customization.oaones.appsyn.common.OaonesProps.mtgPullHourTo();
%>
<%--
  会议同步 / 日程同步 的公共页主体（标记部分）。

  两个一级菜单共用本片段：会议同步页（/oaones/appsyn/meetingSync/meetingSync.jsp）biz=0、
  日程同步页（/oaones/appsyn/scheduleSync/scheduleSync.jsp，服务端 forward 到前者并带 biz=1）biz=1，
  脚本统一在 /oaones/appsyn/js/mtgSync.js（读 window.OAONES_MTG_BIZ 等）。

  四个页签（在页壳的二级页签条上，顺序：同步设置 → 会议 / 日程同步清单 → 同步记录 → 个人同步方向）：
    * 同步设置：顶部一行「计划任务」（白天 / 夜间两套 cron，全局一份，存 uf_oaones_appsyn_mtg_cron），
      下面一行一个租户（当前平台），逐租户开 / 关同步（不设“同步范围 / 最近同步 / 操作”列）；
    * 会议 / 日程同步清单（data-mtg-panel="item"）：按 OA 对象维度看同步结果，
      跳过 / 失败可“重推”（一行一个 OA 会议 / 日程，取最近一条同步记录）；
    * 同步记录：历史同步情况（一行一条，带租户 / 状态 / 时间 / 关键字筛选与分页）；
    * 个人同步方向（data-mtg-panel="diruser"）：非“遵循系统设置”的个人及其方向，**只读**
      （按租户 / 分部 / 部门 / 人员 / 同步方向筛选，分部与部门是多选弹框、自动含下级、按 OA 分权出候选）。
  租户“开启同步”后由内置调度器按「计划任务」轮询：到点就跑一轮（OA→第三方 推送 + 第三方→OA 拉取），
  按内容快照“变了才同步”，不需要在页面上手动触发。提示走 js/toast.js。
--%>

<%-- ============ 页签一：同步设置 ============ --%>
<div class="tab-panel active" data-mtg-panel="setting">
    <div class="toolbar" style="justify-content:space-between;">
        <div class="toolbar-left">
            <span class="notice">按租户开启 / 关闭同步，并勾选同步方向（<b>OA→第三方 / 第三方→OA</b>，可双选，默认双选）；
                开启后按上面的<b>计划任务</b>自动跑一轮（双向：OA→第三方 推送 + 第三方→OA 拉取），
                <b>变了才同步</b>（新增 / 修改 / 删除，按内容快照比对）；
                <b>第一次同步失败 / 跳过的不会自动重推</b>，需要时到「<%=oaonesItemWord%>同步清单」手工点“重推”。
                会议 / 日程都是调第三方平台的<b>日程接口</b>推过去的，所以<b>要选一个“有日程权限”的应用（绑定应用）</b>：
                一个租户绑了多个应用时在这里逐租户指定，不指定就用租户配置的 Secret（通常没日程权限，可能报错）。</span>
        </div>
        <div>
            <input type="text" id="mtgSettingKeyword" placeholder="租户名称 / 企业标识" autocomplete="off"
                   data-lpignore="true" style="min-width:180px;max-width:260px;">
            <button type="button" class="btn small" id="mtgSettingSearchBtn">搜索</button>
            <button type="button" class="btn small secondary" id="mtgSettingResetBtn">重置</button>
        </div>
    </div>
    <%-- ============ 计划任务（全局一份）：两种同步方式 ============
         会议 / 日程同步双向共用这一套频率：到点跑一轮 = OA→第三方 推送 + 第三方→OA 拉取。
         ① 按时间段（默认）：明细表 uf_oaones_appsyn_mtg_plan_item，一行一个时间段（适用日 + 起止时间 +
            轮询间隔），页面上可自由加减；同一适用日下的时间段不允许重叠。
         ② 高级模式：直接写 Quartz cron（白天 / 夜间两档），并指定执行日。
         工作日 / 非工作日取泛微标准考勤口径（考勤组的固定排班 / 工作日勾选 + 节假日与调配工作日），
         由下面的「工作日口径」选考勤组。配置存 uf_oaones_appsyn_mtg_cron 单行（+ 明细表），改完立即生效。 --%>
    <div class="filter-bar" id="mtgPlanRow" style="flex-wrap:wrap;">
        <span class="filter-label">计划任务</span>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;"
               title="关闭后不再自动跑；页面上的「拉取」等手工入口仍然可用">
            <input type="checkbox" id="mtgPlanEnabled"> 启用自动同步
        </label>
        <span class="filter-label">同步方式</span>
        <label class="mtg-dir-opt" title="按公司上下班时间自由配时间段：可以配多个，每个时段一个轮询间隔">
            <input type="radio" name="mtgPlanMode" id="mtgPlanModeItem" value="0" checked> 按时间段
        </label>
        <label class="mtg-dir-opt" title="直接写 cron 表达式（Quartz 口径），适合特殊节奏">
            <input type="radio" name="mtgPlanMode" id="mtgPlanModeCron" value="1"> 高级模式（cron）
        </label>
        <span class="filter-label">工作日口径</span>
        <select id="mtgPlanGroup"
                title="工作日 / 非工作日按泛微标准考勤算：取这个考勤组的固定排班（或工作日勾选）+ 节假日 / 调休；选“不指定”就按周一至周五">
            <option value="0">不指定（周一至周五）</option>
        </select>
        <button type="button" class="btn small" id="mtgPlanSaveBtn">保存计划任务</button>
        <button type="button" class="btn small secondary" id="mtgPlanPreviewBtn">预测后 10 次</button>
        <button type="button" class="btn small secondary" id="mtgPlanResetBtn" title="恢复默认配置">恢复默认</button>
        <span class="help-tip" id="mtgPlanHelpTip" data-help="mtgPlanHelpText" role="button"
              tabindex="0" title="查看计划任务说明" aria-label="查看计划任务说明">?</span>
    </div>

    <%-- 方式一：按时间段（默认）——明细表，一行一个时间段；适用日按上面的「工作日口径」判定 --%>
    <div class="plan-items-box" id="mtgPlanItemBox">
        <%-- 「+ 新增时间段」放在明细表左上角（表头上面一行，靠左） --%>
        <div class="mtg-plan-actions">
            <button type="button" class="btn small secondary" id="mtgPlanItemAddBtn">+ 新增时间段</button>
            <span class="muted">一个时段内从<b>开始时间</b>起按间隔轮询（例：8:30 起每 45 分钟 → 8:30 / 9:15 / 10:00 …）；
                结束时间早于开始时间表示<b>跨天</b>（例：21:00–06:30）；同一适用日下的时间段<b>不能重复</b>。</span>
        </div>
        <table class="grid" id="mtgPlanItemTable">
            <thead>
            <tr>
                <th style="width:120px;">适用日</th>
                <th style="width:150px;">开始时间</th>
                <th style="width:150px;">结束时间</th>
                <th style="width:220px;">轮询间隔</th>
                <th>说明</th>
                <th style="width:80px;">操作</th>
            </tr>
            </thead>
            <tbody id="mtgPlanItemBody"></tbody>
        </table>
    </div>

    <%-- 方式二：高级模式（cron）——保留原来的白天 / 夜间两档表达式，另加一个「执行日」 --%>
    <div class="filter-bar" id="mtgPlanCronBox" style="display:none;flex-wrap:wrap;">
        <span class="filter-label">执行日</span>
        <select id="mtgPlanCronDayType" title="高级模式在哪些天执行（工作日口径与上面选的是同一个考勤组）">
            <option value="0">每天</option>
            <option value="1">仅工作日</option>
            <option value="2">仅非工作日</option>
        </select>
        <span class="filter-label">白天 <%=oaonesDayFrom%>:00–<%=oaonesDayTo%>:00</span>
        <input type="text" id="mtgPlanDayCron" placeholder="cron 表达式，例：0 0 0/1 * * ?" autocomplete="off"
               data-lpignore="true" style="min-width:200px;max-width:260px;"
               title="白天时段的计划任务：Quartz cron（秒 分 时 日 月 周），例：0 0 0/1 * * ? = 每小时一次">
        <span class="filter-label">夜间（其余时段）</span>
        <input type="text" id="mtgPlanNightCron" placeholder="cron 表达式，例：0 0 0/2 * * ?" autocomplete="off"
               data-lpignore="true" style="min-width:200px;max-width:260px;"
               title="夜间时段的计划任务：同上；留空 = 该时段不自动同步">
    </div>

    <%-- 「预测后 10 次」：按上面的配置试算**实际会执行的时间**（时间段模式按时段明细逐条推、
         高级模式按白天 / 夜间两档表达式推，都按时间先后合并），与调度器的到点判断共用同一份代码，
         所以预测出来的就是实际会跑的时间 --%>
    <div class="plan-cron-box" id="mtgPlanPreviewBox" style="display:none;">
        <div class="muted">实际执行时间（后 10 次）
            <button type="button" class="link" id="mtgPlanPreviewHide">收起</button></div>
        <ul class="plan-cron-list" id="mtgPlanPreviewList"></ul>
    </div>
    <div class="help-source" id="mtgPlanHelpText">
        <b>计划任务决定“多久跑一轮同步”</b>：一轮 = <span class="rule">OA→第三方 推送</span>（新增 / 修改 / 删除）
        + <span class="rule">第三方→OA 拉取</span>（新增 / 修改 / 删除），两个方向同一套频率。
        同步方式两种，按公司自己的上下班时间选：
        <p><b>① 按时间段（默认，推荐）</b>：像排课表一样一行一个时间段——<span class="rule">适用日</span>
        （工作日 / 非工作日 / 每天）+ <span class="rule">开始时间 ~ 结束时间</span>（点输入框右边的时钟图标选，
        结束时间早于开始时间 = 跨天）+ <span class="rule">轮询间隔</span>（5 分钟 ~ 24 小时，可填分钟或小时）。
        可以配多个时间段（例：上班 8:30–12:00 每 15 分钟、13:30–18:00 每 30 分钟；非工作日 9:00–18:00 每 2 小时），
        轮询<b>从每个时间段的开始时间起算</b>；<b>同一适用日下的时间段不能重复（不允许重叠）</b>，
        「每天」与「工作日 / 非工作日」也不允许同时配。</p>
        <p><b>② 高级模式（cron）</b>：直接写 <span class="rule">cron 表达式</span>（Quartz 口径
        <b>秒 分 时 日 月 周</b>，与「同步中心」/ E9 后台计划任务同一套语法；例：<code>0 0 0/1 * * ?</code>
        = 每小时一次、<code>0 0/30 * * * ?</code> = 每 30 分钟一次）。仍按
        <span class="rule">白天 <%=oaonesDayFrom%>:00–<%=oaonesDayTo%>:00</span> 与
        <span class="rule">夜间（其余时段）</span>两档，各配一个表达式，再选<span class="rule">执行日</span>
        （每天 / 仅工作日 / 仅非工作日）。</p>
        <p><b>工作日口径（两种方式共用）</b>：上面的<span class="rule">「工作日口径」下拉</span>选一个考勤组，
        工作日 / 非工作日就按<span class="rule">泛微标准考勤</span>算——该考勤组的固定排班（或工作日勾选）
        <b>叠加</b>节假日设置（法定节假日 / 调休算非工作日，<b>调配工作日</b>如国庆前的周六补班算工作日）；
        选「不指定」则按<b>周一至周五</b>算。考勤组时间 / 节假日改完后，保存一次计划任务即可立即生效。</p>
        <p>频率下限 <b>5 分钟</b>；高级模式下某一档留空 = 该时段不自动同步。
        保存前可以先点<span class="rule">「预测后 10 次」</span>看实际会跑的时间（会标明命中的是哪个时间段）。</p>
        <p><b>同步口径</b>：双向同步但<b>不做反向更新</b>——谁创建谁维护：OA 创建的对象，
        OA 侧的修改 / 删除会同步到平台（平台侧改了、删了不写回 OA）；平台创建的对象反之。
        每条对象都按内容快照比对，<span class="rule">没变就不会调接口、也不会写同步记录</span>。</p>
    </div>
    <p class="notice" id="mtgSchedulerTip" style="display:none;"></p>
    <div class="table-wrap">
        <table class="grid" id="mtgSettingTable">
            <thead>
            <tr>
                <th style="width:70px;">租户ID</th>
                <th>租户名称</th>
                <th>企业标识</th>
                <th style="width:90px;">租户状态</th>
                <th style="width:110px;">同步开关</th>
                <th style="width:250px;">绑定应用</th>
                <th style="width:200px;">同步方向</th>
            </tr>
            </thead>
            <tbody id="mtgSettingBody"></tbody>
        </table>
    </div>
    <div class="empty" id="mtgSettingEmpty" style="display:none;">暂无租户。请先在「企微租户设置 / 钉钉租户设置 / 飞书租户设置」里新增租户。</div>

</div>

<%-- ============ 页签二：会议 / 日程同步清单（按 OA 对象维度看同步结果，跳过 / 失败可“重推”） ============
     与「同步设置」「同步记录」并列的一个页签（在两者之间）：一行一个 OA 会议 / 日程，
     取该对象最近一条同步记录，供按会议维度核对“同步到第三方平台的结果”。
     第一次同步与每次“重推”都会写进「同步记录」页签。 --%>
<div class="tab-panel" data-mtg-panel="item">
    <div class="mtg-items">
        <%-- 清单已是独立页签（页签顶部），不需要“跟在租户表后面”的上间距 --%>
        <div class="toolbar" style="justify-content:space-between;">
            <div class="toolbar-left">
                <b id="mtgItemTitle"><%=oaonesItemTitle%></b>
                <span class="notice">本页签按 OA <%=oaonesItemWord%>一个一个看同步到第三方平台的结果；
                    <b>点“状态”（新增 / 更新 / 跳过 / 失败）可查看这条<%=oaonesItemWord%>下发给第三方平台的
                    接口报文与字段说明</b>；第一次同步跳过 / 失败的行<b>不会自动重推</b>，需要时点“重推”手工推
                    （重推同样会写进「同步记录」页签）。</span>
            </div>
            <div>
                <select id="mtgItemCfg"><option value="">全部租户</option></select>
                <select id="mtgItemOp">
                    <option value="">全部状态</option>
                    <option value="0">新增</option>
                    <option value="1">更新</option>
                    <option value="2">跳过</option>
                    <option value="3">失败</option>
                    <option value="4">删除</option>
                </select>
                <input type="text" id="mtgItemKeyword" placeholder="标题 / 分部 / ID" autocomplete="off"
                       data-lpignore="true" style="min-width:150px;max-width:220px;">
                <button type="button" class="btn small" id="mtgItemSearchBtn">查询</button>
                <button type="button" class="btn small secondary" id="mtgItemResetBtn">重置</button>
            </div>
        </div>
        <div class="table-wrap">
            <table class="grid" id="mtgItemTable">
                <thead>
                <tr>
                    <th style="width:110px;"><%=oaonesItemIdLabel%></th>
                    <th>标题</th>
                    <th style="width:140px;">分部</th>
                    <th style="width:150px;">开始时间</th>
                    <th style="width:150px;">租户</th>
                    <th style="width:80px;">状态</th>
                    <th style="width:60px;">次数</th>
                    <th style="width:150px;">最近同步</th>
                    <th style="width:150px;">平台日程ID</th>
                    <th>说明</th>
                    <th style="width:80px;">操作</th>
                </tr>
                </thead>
                <tbody id="mtgItemBody"></tbody>
            </table>
        </div>
        <div class="pager" id="mtgItemPager"></div>
        <div class="empty" id="mtgItemEmpty" style="display:none;">暂无同步清单。租户开启同步后，新<%=oaonesItemWord%>会自动同步并在这里出现。</div>
    </div>
</div>

<%-- ============ 页签三：同步记录 ============ --%>
<div class="tab-panel" data-mtg-panel="log">
    <p class="notice" style="margin-bottom:10px;">点“状态”（新增 / 更新 / 跳过 / 失败）可查看这条<%=oaonesItemWord%>
        下发给第三方平台的<b>接口报文</b>与逐字段说明（只组装不下发、不含 access_token）。</p>
    <div class="filter-bar">
        <label>租户
            <select id="mtgLogCfg"><option value="">全部</option></select>
        </label>
        <label>状态
            <select id="mtgLogOp">
                <option value="">全部</option>
                <option value="0">新增</option>
                <option value="1">更新</option>
                <option value="2">跳过</option>
                <option value="3">失败</option>
                <option value="4">删除</option>
            </select>
        </label>
        <label>关键字
            <input type="text" id="mtgLogKeyword" placeholder="标题 / 分部 / ID" autocomplete="off"
                   data-lpignore="true" style="min-width:150px;max-width:220px;">
        </label>
        <%-- 时间检索：日期时间框（精确到秒，日历 + 时 / 分 / 秒三列 + 手工输入，见 js/datetime.js）。
             “起 / 止”空值初值分别按 00:00:00 / 23:59:59 补齐，只选日期时正好覆盖整天 --%>
        <span class="filter-label">时间</span>
        <span class="dt-field" style="width:206px;">
            <input type="text" id="mtgLogFrom" data-oaones-date="datetime" data-oaones-date-time="00:00:00"
                   autocomplete="off" data-lpignore="true" placeholder="起 yyyy-MM-dd HH:mm:ss">
        </span>
        <span class="filter-label">~</span>
        <span class="dt-field" style="width:206px;">
            <input type="text" id="mtgLogTo" data-oaones-date="datetime" data-oaones-date-time="23:59:59"
                   autocomplete="off" data-lpignore="true" placeholder="止 yyyy-MM-dd HH:mm:ss">
        </span>
        <button type="button" class="btn small" id="mtgLogSearchBtn">查询</button>
        <button type="button" class="btn small secondary" id="mtgLogResetBtn">重置</button>
    </div>
    <div class="table-wrap">
        <table class="grid" id="mtgLogTable">
            <thead>
            <tr>
                <th style="width:150px;">同步时间</th>
                <th style="width:160px;">租户</th>
                <th>标题</th>
                <th style="width:160px;">分部</th>
                <th style="width:150px;">开始时间</th>
                <th style="width:80px;">状态</th>
                <th style="width:170px;">平台日程ID</th>
                <th>说明</th>
            </tr>
            </thead>
            <tbody id="mtgLogBody"></tbody>
        </table>
    </div>
    <div class="pager" id="mtgLogPager"></div>
    <div class="empty" id="mtgLogEmpty" style="display:none;">没有符合条件的同步记录。</div>
</div>

<%-- ============ 页签四：个人同步方向（非遵循系统设置） ============
     独立页签，位置在「同步记录」后面：把当前业务 / 平台下**非“遵循系统设置”（dir_mode = 1）**的个人
     列出来（一行一个人 + 租户，取本人最近一次指定的方向）。
     筛选：租户 + **分部 / 部门（多选弹框，都自动含其下级；候选按 OA 分权过滤，
     选分部后部门候选只列该分部（含下级分部）下的部门）** + 人员（姓名 / 登录名）+ 同步方向。
     本页签**只读**：没有维护入口，管理员改不了，只能本人在「个人账号中心」自己改（见第一节第 13 条）。 --%>
<div class="tab-panel" data-mtg-panel="diruser">
    <div class="toolbar" style="justify-content:space-between;">
        <div class="toolbar-left">
            <b>个人同步方向（非遵循系统设置）</b>
            <span class="notice">下面的人自己指定了同步方向（没有跟随系统设置）；
                本列表<b>只读</b>，要改请本人在「个人账号中心」自己修改。</span>
        </div>
    </div>
    <div class="filter-bar">
        <label>租户
            <select id="mtgDirUserCfg"><option value="">全部</option></select>
        </label>
        <span class="filter-label">分部</span>
        <div class="filter-picker" id="mtgDirSubPicker"
             title="点击选择 OA 分部（可多选，选中分部自动含其下级分部）">
            <input type="text" id="mtgDirSubInput" class="filter-input" readonly
                   placeholder="选择 OA 分部（可多选）" autocomplete="off" data-lpignore="true">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="mtgDirSubClear" title="清除分部选择">×</button>
        </div>
        <span class="filter-label">部门</span>
        <div class="filter-picker" id="mtgDirDeptPicker"
             title="点击选择 OA 部门（可多选，选中部门自动含其下级部门）">
            <input type="text" id="mtgDirDeptInput" class="filter-input" readonly
                   placeholder="选择 OA 部门（可多选）" autocomplete="off" data-lpignore="true">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="mtgDirDeptClear" title="清除部门选择">×</button>
        </div>
        <label>人员
            <input type="text" id="mtgDirUserKeyword" placeholder="人员姓名 / 登录名" autocomplete="off"
                   data-lpignore="true" style="min-width:150px;max-width:220px;">
        </label>
        <label>同步方向
            <select id="mtgDirDirection">
                <option value="">全部</option>
                <option value="oa2">仅 OA→第三方</option>
                <option value="t2oa">仅 第三方→OA</option>
                <option value="both">双向（OA→第三方 / 第三方→OA）</option>
            </select>
        </label>
        <button type="button" class="btn small" id="mtgDirUserSearchBtn">查询</button>
        <button type="button" class="btn small secondary" id="mtgDirUserResetBtn">重置</button>
    </div>
    <div class="table-wrap">
        <table class="grid" id="mtgDirUserTable">
            <thead>
            <tr>
                <th style="width:160px;">人员</th>
                <th style="width:180px;">分部</th>
                <th style="width:180px;">部门</th>
                <th>租户</th>
                <th style="width:230px;">同步方向</th>
                <th style="width:160px;">最近修改</th>
            </tr>
            </thead>
            <tbody id="mtgDirUserBody"></tbody>
        </table>
    </div>
    <div class="pager" id="mtgDirUserPager"></div>
    <div class="empty" id="mtgDirUserEmpty" style="display:none;">没有符合条件的记录（没有指定同步方向的人员，或筛选后无命中）。</div>
</div>

<%-- ============ 「报文」弹框（点列表里的“状态”标签打开：同步清单 / 同步记录两个列表共用） ============
     展示同步到第三方平台时**实际下发的请求报文**（HTTP 方法 + 接口路径 + 请求体）与逐字段简要说明，
     只组装不下发、不含 access_token（真正调用时实时换取）；报文由后端用与推送同一份代码组装。 --%>
<div class="modal-mask" id="mtgPayloadMask"></div>
<div class="modal" id="mtgPayloadModal" style="width:900px;display:none;">
    <div class="modal-head">
        <span id="mtgPayloadTitle">接口报文</span>
        <span class="modal-head-actions">
            <button type="button" class="modal-close" id="mtgPayloadClose" title="关闭">×</button>
        </span>
    </div>
    <div class="modal-body" style="max-height:70vh;overflow:auto;">
        <div class="wx-pick-tip" id="mtgPayloadMeta"></div>
        <div class="detail-tip" id="mtgPayloadTips" style="display:none;"></div>
        <div class="form-row" style="margin-top:12px;">
            <label>接口</label>
            <div class="field">
                <div class="detail" id="mtgPayloadApi"
                     style="max-height:none;background:var(--oa-primary-tint);color:var(--oa-primary-active);word-break:break-all;"></div>
            </div>
        </div>
        <div class="form-row">
            <label>请求报文</label>
            <div class="field">
                <div class="detail-bar">
                    <button type="button" class="btn small secondary" id="mtgPayloadCopyBtn"
                            title="复制这条会议 / 日程实际下发的请求报文">复制</button>
                </div>
                <div class="detail" id="mtgPayloadBody" style="max-height:320px;"></div>
            </div>
        </div>
        <div class="form-row">
            <label>字段说明</label>
            <div class="field">
                <div class="table-wrap" style="max-height:300px;overflow:auto;">
                    <table class="grid" id="mtgPayloadFieldTable">
                        <thead>
                        <tr>
                            <th style="width:230px;">报文字段</th>
                            <th>说明</th>
                        </tr>
                        </thead>
                        <tbody id="mtgPayloadFieldBody"></tbody>
                    </table>
                </div>
                <div class="empty" id="mtgPayloadFieldEmpty" style="display:none;">这条接口没有可说明的字段。</div>
            </div>
        </div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="mtgPayloadCopyAllBtn"
                title="把接口与请求报文按段落一起复制">复制全部报文</button>
        <button type="button" class="btn" id="mtgPayloadOkBtn">关闭</button>
    </div>
</div>

<%-- ============ 「个人同步方向」页签的分部 / 部门筛选弹框（多选） ============
     弹框骨架（拖动 / 列表式·树状切换 / 树节点展开收起 / 全选 / 前置条件）由 js/picker.js 提供，
     候选由 js/mtgSync.js 用 mtgsync.orgOptions 取——**按登录人的 OA 分权过滤**
     （OA 没开分权时 = 全量，口径见 common.OaonesHrmScope）；
     部门候选受“已选分部（含下级分部）”前置条件过滤（未选分部时显示全部部门）。 --%>
<div class="modal-mask" id="mtgDirSubMask"></div>
<div class="modal wx-pick-modal" id="mtgDirSubModal">
    <div class="modal-head">
        <span>选择 OA 分部</span>
        <button type="button" class="link" id="mtgDirSubClose" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="mtgDirSubSearch" placeholder="输入分部名称/编号搜索"
                   autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" data-pick-all="mtgDirSub">全选</button>
            <div class="view-switch mini" data-view-key="mtgDirSub">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="mtgDirSubList"></div>
        <div class="muted wx-pick-note">可多选，<span class="rule">选中分部自动含其下级分部</span>；
            候选按<b>泛微标准分权</b>过滤（OA 开了分权时只看得到自己范围内的分部）。
            单击选中/取消，双击即确定并退出。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="mtgDirSubClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="mtgDirSubCancel">取消</button>
        <button type="button" class="btn" id="mtgDirSubOk">确定</button>
    </div>
</div>

<div class="modal-mask" id="mtgDirDeptMask"></div>
<div class="modal wx-pick-modal" id="mtgDirDeptModal">
    <div class="modal-head">
        <span>选择 OA 部门</span>
        <button type="button" class="link" id="mtgDirDeptClose" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="mtgDirDeptSearch" placeholder="输入部门名称/编号搜索"
                   autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" data-pick-all="mtgDirDept">全选</button>
            <div class="view-switch mini" data-view-key="mtgDirDept">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-tip" id="mtgDirDeptNote"></div>
        <div class="wx-pick-list" id="mtgDirDeptList"></div>
        <div class="muted wx-pick-note">可多选，<span class="rule">选中部门自动含其下级部门</span>；
            候选按<b>泛微标准分权</b>过滤（OA 开了分权时只看得到自己范围内的部门），
            已选分部时只列出该分部（含下级分部）下的部门。单击选中/取消，双击即确定并退出。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="mtgDirDeptClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="mtgDirDeptCancel">取消</button>
        <button type="button" class="btn" id="mtgDirDeptOk">确定</button>
    </div>
</div>
