<%@ page pageEncoding="UTF-8" %>
<%--
  同步中心“编辑同步设置”面板（表单 + 紧随其后的**同步设置列表**——一行一条规则、首列显示租户名称）：
  三个平台共用同一份标记
  （由通用页面 syncCenter/syncCenter.jsp 用 <jsp:include> 引入；
   平台由页壳 request.setAttribute("oaonesSyncPlatform", ...) 传，也兼容 platform 参数），
  交互脚本在 /oaones/js/syncPlan.js。

  页面口径（与后端 uf_oaones_sync_plan 一一对应）：
    1) 租户选择：下拉**必选一个具体租户**（没有“全部可维护租户”一档；带 cfg_id 进来时锁定该租户），
       它只决定“编辑同步设置”里的改动保存到哪个租户；
    2) 配置范围：只剩“当前租户配置”一档（cfg_id = 上面选中的租户ID）；
       早期版本保存的“全局默认配置”（cfg_id = 0，作用于本平台所有未单独配置该方向的租户）
       仍在“同步设置列表”里列出并在首列标注，但本页不再提供它的表单入口
       （行尾只剩“覆盖为当前租户配置”与系统管理员的暂停·启动 / 立即执行 / 删除）；
    3) 同步设置列表（**跟在“编辑同步设置”的表单下方、同一张卡片里**，中间不再单独一个“同步设置列表”板块、
       也不铺说明文字）：一行一条同步规则、**首列显示租户名称**——独立一级菜单里列出本人可维护租户的规则
       （后端不带 cfg_id 时按维护人过滤），从租户工作区进来时只列当前租户的规则；
    4) 同步方向：A = OA → 本平台（显示平台名 + 当前租户名）/ B = 本平台（同前）→ OA；
    5) 同步范围只选源侧：A（OA → 本平台）选 OA 分部 / OA 部门；
       B（本平台 → OA）选本平台部门；目标侧统一按租户现有部门映射取第一条写入人员，
       页面不再指定平台落点或 OA 落点；不勾选“按范围同步”= 沿用历史全量口径；
    6) 成员“没有找到部门”（所在部门未绑定 / 目标侧部门不存在）的处理：
       两个方向统一为“不同步 / 新增部门 / 默认部门”，选择新增部门时提示风险；
       平台→OA 只有租户已映射 OA 分部时才允许新建；
    7) 计划任务有两种方式（**“同步方式”单选**，两种取值一起保存、来回切不丢值）：
       ① 轮询间隔（分钟，默认）：填“每 N 分钟同步一次”（下限 5 分钟、上限 1440 分钟 = 24 小时）；
       ② 高级模式（cron 表达式，Quartz 口径：秒 分 时 日 月 周）；
       另有启用开关；保存前可点“预测后 10 次”核对实际执行时间（按当前方式试算）；
    8) 一行 = 一条同步规则：同一个“租户 + 方向”**可以有多条规则**
       （各自的同步范围 / 计划任务 / 口径不同，计划任务按每条规则自己的方式与配置分别执行）；
       表单带 id 保存 = 更新该条，不带 id = 新增一条；(type, cfgid, direction) 上只是普通索引。
--%>
<%
    // 平台由引入方传入：页壳用 request.setAttribute 传（避免 jsp:param 里嵌表达式），
    // 直接 include 本片段的旧写法（platform 参数）也继续支持
    String oaonesPlanPlatform = (String) request.getAttribute("oaonesSyncPlatform");
    if (oaonesPlanPlatform == null || oaonesPlanPlatform.trim().length() == 0) {
        oaonesPlanPlatform = request.getParameter("platform");
    }
    if (oaonesPlanPlatform == null) {
        oaonesPlanPlatform = "";
    }
    oaonesPlanPlatform = oaonesPlanPlatform.trim().toLowerCase();
    if (!"dingtalk".equals(oaonesPlanPlatform) && !"feishu".equals(oaonesPlanPlatform)) {
        oaonesPlanPlatform = "wecom";
    }
    String oaonesPlanLabel = "feishu".equals(oaonesPlanPlatform) ? "飞书"
            : ("dingtalk".equals(oaonesPlanPlatform) ? "钉钉" : "企业微信");
    // 是否从租户工作区进来（地址带 cfg_id）：是则页壳不出标题栏，页面说明图标（#cfgHelpTip）
    // 由本片段挂在“租户 / 配置范围”一行的行尾（说明正文 #cfgHelpText 由页壳输出）
    String oaonesPlanCfgId = request.getParameter("cfg_id");
    boolean oaonesPlanEmbedded = oaonesPlanCfgId != null && oaonesPlanCfgId.trim().matches("\\d+");
%>
<%-- ① 上下文：租户 + 配置范围（“编辑同步设置”的表单按它取数；设置列表一行一条规则、首列显示租户名称） --%>
<div class="card">
    <div class="filter-bar" style="margin-bottom:0;">
        <span class="filter-label">租户</span>
        <select id="planCfgSelect" style="min-width:240px;max-width:400px;">
            <option value="">请选择租户</option>
        </select>
        <span class="filter-label">配置范围</span>
        <%-- 配置范围只保留“当前租户配置”一档：同步设置一律保存到上面选中的租户，
             不再提供“全局默认配置”（早期版本存的全局默认行仍会在列表里列出并标注，见 js/syncPlan.js） --%>
        <select id="planScopeSelect" title="同步设置按“租户 + 同步方向”维护：保存到上方选中的租户，列表一行一条规则、首列显示租户名称">
            <option value="tenant">当前租户配置</option>
        </select>
        <span class="muted" id="planCfgTip">选择一个租户后即可维护该租户的同步设置。</span>
        <% if (oaonesPlanEmbedded) { %><span class="help-tip" id="cfgHelpTip" data-help="cfgHelpText"
              role="button" tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span><% } %>
    </div>
    <p class="notice" id="planSchedulerTip" style="margin:10px 0 0;">内置调度器：检查中…</p>
</div>

<%-- ② 编辑同步设置：**计划任务排在最上面**（先设“什么时候跑”，再设“同步方向 / 同步范围”与“成员没有找到部门”） --%>
<%-- 编辑同步设置这张卡片的高度比通用 .card（padding:16px 18px）**多 20px**：
     底部内边距 16px + 20px = 36px，卡片整体高度正好增加 20px。 --%>
<div class="card" style="padding-bottom:36px;">
    <div class="toolbar">
        <h3 style="margin:2px 0;">编辑同步设置</h3>
        <span class="muted">同一“配置范围 + 同步方向”<b>可以保存多条规则</b>（同步范围与计划任务各自独立）：
            表单里改完点“保存设置”= <b>新增一条</b>；点列表行尾“编辑”把某条读进表单后再保存 = <b>更新那一条</b>。</span>
        <%-- 页面说明（! 图标）：原来挂在“同步设置列表”板块的标题行上，现在列表已并进本卡片下方，
             这个说明图标跟着挪到本卡片的标题行（正文仍不铺在页面上，见 js/help.js）。 --%>
        <span class="help-tip" id="planHelpTip" data-help="planHelpText" role="button" tabindex="0"
              title="查看页面说明" aria-label="查看页面说明">?</span>
    </div>
    <%-- 本卡片内部：左侧 80% 放设置项，右侧 20% 放“预测后 10 次”的执行时间，中间虚线分隔；
         只有这一块是两栏布局，下面的同步设置列表与页面其它卡片（上下文）都仍是整宽。 --%>
    <div class="plan-edit-cols">
        <div class="plan-edit-main">
    <%-- 计划任务：两种方式（与「会议同步 / 日程同步」的计划任务同一套版式）——
         ① 轮询间隔（分钟，默认）：现场不需要懂 cron，填“每 N 分钟同步一次”；
         ② 高级模式（cron 表达式）：原来的 cron 口径（Quartz：秒 分 时 日 月 周）。
         「同步方式」单选切换两块互斥显示；两种取值一起保存，来回切不丢值。 --%>
    <div class="filter-bar" id="planTaskRow" style="flex-wrap:wrap;">
        <span class="filter-label">计划任务</span>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;">
            <input type="checkbox" id="planEnabled" checked> 启用计划任务
        </label>
        <span class="filter-label">同步方式</span>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;"
               title="按分钟轮询：现场不需要懂 cron，填“每 N 分钟同步一次”即可">
            <input type="radio" name="planMode" id="planModeInterval" value="0" checked> 轮询间隔（分钟）
        </label>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;"
               title="直接写 cron 表达式（Quartz 口径：秒 分 时 日 月 周），适合特殊节奏">
            <input type="radio" name="planMode" id="planModeCron" value="1"> 高级模式（cron）
        </label>
        <button type="button" class="btn small secondary" id="planCronPreviewBtn">预测后 10 次</button>
    </div>
    <%-- 方式一：轮询间隔（分钟，默认）——从“上一次执行时刻”起每 N 分钟一次 --%>
    <div class="filter-bar" id="planIntervalRow">
        <span class="filter-label">轮询间隔</span>
        <input type="number" id="planInterval" min="5" max="1440" step="1" value="30"
               autocomplete="off" data-lpignore="true" style="width:90px;"
               title="每 N 分钟同步一次：下限 5 分钟，上限 1440 分钟（= 24 小时）">
        <span class="muted">分钟 —— 每 N 分钟同步一次（5 ~ 1440 分钟之间，例：30 = 每半小时一次）；
            保存后由内置调度器按这个间隔轮询。</span>
    </div>
    <%-- 方式二：高级模式（cron）——原来的口径，Quartz（秒 分 时 日 月 周） --%>
    <div class="filter-bar" id="planCronRow" style="display:none;">
        <span class="filter-label">cron 表达式</span>
        <input type="text" id="planCron" placeholder="cron 表达式，例：0 0 2 * * ?" autocomplete="off"
               data-lpignore="true" style="min-width:220px;max-width:320px;"
               title="Quartz cron（秒 分 时 日 月 周），例：0 0 0/1 * * ? 每小时一次、0 0/30 * * * ? 每 30 分钟一次">
        <span class="muted">Quartz 口径（<b>秒 分 时 日 月 周</b>，与 E9 后台计划任务同一套语法）；
            频率下限 5 分钟。</span>
    </div>
    <div class="filter-bar">
        <span class="filter-label">同步方向</span>
        <select id="planDirection">
            <option value="0">A：OA → <%= oaonesPlanLabel %></option>
            <option value="1">B：<%= oaonesPlanLabel %> → OA</option>
        </select>
        <span class="filter-label">同步范围</span>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;"
               title="勾选后只选择源侧组织（含下级）；目标部门按现有租户部门映射写入">
            <input type="checkbox" id="planScopeEnabled"> 按范围同步
        </label>
        <span class="muted" id="planScopeHint"></span>
    </div>
    <%-- 同步范围只展示源侧：A = OA 分部/部门；B = 平台部门（均可多选，含下级）。 --%>
    <div class="filter-bar" id="planScopeRow" style="display:none;">
        <span class="filter-label" id="planSubLabel">OA 分部</span>
        <div class="filter-picker" id="planSubPicker" title="点击选择 OA 分部（可多选，含下级分部与部门）">
            <input type="text" id="planSubInput" class="filter-input" readonly
                   placeholder="选择 OA 分部（可多选）" autocomplete="off" data-lpignore="true">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="planSubClear" title="清除分部选择">×</button>
        </div>
        <span class="filter-label" id="planDeptScopeLabel">OA 部门</span>
        <div class="filter-picker" id="planDeptScopePicker" title="点击选择 OA 部门">
            <input type="text" id="planDeptScopeInput" class="filter-input" readonly
                   placeholder="选择 OA 部门（可多选）" autocomplete="off" data-lpignore="true">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="planDeptScopeClear" title="清除部门选择">×</button>
        </div>
        <span class="filter-label" id="planWxLabel"><%= oaonesPlanLabel %>部门（来源）</span>
        <div class="filter-picker" id="planWxPicker" title="点击选择平台来源部门（可多选，含下级部门）">
            <input type="text" id="planWxInput" class="filter-input" readonly
                   placeholder="选择平台部门（可多选，含下级部门）" autocomplete="off" data-lpignore="true">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="planWxClear" title="清除平台部门选择">×</button>
        </div>
        <span class="muted" id="planScopeNote"></span>
    </div>
    <div class="filter-bar" id="planNodeptRowA" style="flex-wrap:wrap;">
        <span class="filter-label">成员没有找到部门</span>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;">
            <input type="radio" name="planNodeptPolicy" value="0" checked> 不同步
        </label>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;">
            <input type="radio" name="planNodeptPolicy" value="1"> 新增部门
        </label>
        <label class="muted" style="display:inline-flex;align-items:center;gap:4px;">
            <input type="radio" name="planNodeptPolicy" value="2"> 默认部门
        </label>
        <span class="notice" id="planNodeptWarn" style="display:none;margin:0;color:#b45309;">
            请尽量不要选择该方式，可能由于错误设置导致部门创建错误，请尽量先设置部门映射。
        </span>
    </div>
    <div class="filter-bar" id="planNodeptRowB" style="display:none;">
        <span class="filter-label" id="planDefaultDeptLabel">默认部门</span>
        <div class="filter-picker" id="planDeptPicker" title="点击选择默认部门（双击即确定并退出）">
            <input type="text" id="planDeptInput" class="filter-input" readonly
                   placeholder="选择租户下的部门" autocomplete="off" data-lpignore="true">
            <span class="filter-arrow">▾</span>
            <button type="button" class="filter-clear" id="planDeptClear" title="清空默认部门">×</button>
        </div>
        <input type="hidden" id="planDefaultDept" value="">
        <span class="muted">成员所在部门找不到绑定时落到该部门。</span>
    </div>
    <div class="filter-bar">
        <span class="filter-label">备注</span>
        <input type="text" id="planRemark" placeholder="可留空" autocomplete="off" data-lpignore="true"
               style="min-width:220px;max-width:420px;">
        <button type="button" class="btn" id="planSaveBtn">保存设置</button>
        <button type="button" class="btn secondary" id="planRunNowBtn">立即执行一次</button>
        <button type="button" class="btn secondary" id="planResetBtn">重置表单</button>
        <button type="button" class="btn secondary" id="planDeleteBtn">删除该设置</button>
    </div>
    <p class="notice" id="planTip"></p>
        </div><%-- /plan-edit-main：设置项（左 80%） --%>
        <div class="plan-edit-side">
            <div class="plan-side-title">计划任务预测（后 10 次）</div>
            <%-- 预测结果：按当前“同步方式”试算**实际会执行的时间**（轮询间隔逐次加 N 分钟、
                 高级模式按 cron 推），与内置调度器的到点判断共用同一份代码，所以预测的就是实际会跑的。
                 结果固定显示在这一栏里（左侧 80% 仍是设置项）。 --%>
            <div class="muted" id="planCronPreviewEmpty">点上面的“预测后 10 次”，
                按当前同步方式（轮询间隔 / 高级 cron）试算会执行的时间。</div>
            <div id="planCronPreview" style="display:none;">
                <ul class="plan-cron-list" id="planCronPreviewList"></ul>
            </div>
        </div><%-- /plan-edit-side：预测结果（右 20%） --%>
    </div><%-- /plan-edit-cols --%>
    <%-- ③ 同步设置列表（就在上面表单的下方，**同一张卡片里**，中间没有单独的板块、也不铺说明文字）：
         一行一条规则、首列显示租户名称，保存后立刻出现在这里；
         行尾可编辑 / 暂停·启动 / 立即执行 / 结果详情（打开独立同步结果页） / 删除该条规则。 --%>
    <div class="table-wrap" style="margin-top:16px;">
        <table class="grid" id="planTable">
            <thead>
            <tr>
                <th>租户名称</th><th>同步方向</th>
                <%-- 同步范围拆成两栏：源组织（多组织一行一个，超过 3 行折叠）/ 目标组织（恒一行，
                     一个落点），见 js/syncPlan.js#orgCell 与表格下方的统一说明 --%>
                <th style="min-width:150px;" title="要同步的源侧组织（含其下级）：A（OA→平台）= OA 分部 / OA 部门；B（平台→OA）= 平台部门">源组织</th>
                <th style="min-width:150px;" title="目标侧完全按租户现有部门映射确定，页面不再指定落点">目标组织</th>
                <th>成员没有找到部门</th><th>计划任务</th><th>状态</th>
                <th>下次执行</th><th>最近执行</th><th>最近结果</th><th>操作</th>
            </tr>
            </thead>
            <tbody id="planBody"></tbody>
        </table>
        <div class="empty" id="planEmpty" style="display:none;">还没有同步设置：在上面选好租户与同步方向，
            填好后点“保存设置”（同一“租户 + 方向”可以保存多条规则）</div>
    </div>
    <%-- 「源组织 / 目标组织」两栏的统一口径说明（原来跟在每个组织后面，多组织时太啰嗦）：
         含下级、目标只有一个落点、以及“未设置范围（全量同步）”时两栏的含义 --%>
    <div class="muted" style="margin-top:8px;line-height:1.7;">
        <b>源组织</b>都按<b>含下级</b>同步（OA 分部含其下级分部与部门，OA 部门 / <%= oaonesPlanLabel %>部门各含其下级部门），
        多个组织<b>一行一个</b>、超过 3 行折叠（移到鼠标上看全部）。
        目标组织不再由页面指定：两个方向都按租户现有部门映射选取第一条有效映射写入人员；
        同一 OA 部门映射多个平台部门时，第一条（ID 最小）为人员同步默认部门。
        未设置范围（全量同步）的行按该租户全部映射执行。
    </div>
    <div class="help-source" id="planHelpText">
        本区维护<b>同步范围 + 计划任务</b>与<b>成员“没有找到部门”时的处理口径</b>：<br>
        1）<span class="rule">列表一行一条规则、首列就是租户名称</span>：独立一级菜单里列出本人可维护租户的同步设置，
        从租户工作区点“同步中心”进来时只列当前租户的设置；上方的“租户”下拉<b>必选一个具体租户</b>
        （已经没有“全部可维护租户”一档），它决定「编辑同步设置」里的改动<b>保存到哪个租户</b>。
        <span class="rule">同步范围在列表里拆成「源组织 / 目标组织」两栏</span>：
        <b>源组织一行一个组织</b>（多个时按行显示，超过 3 行折叠、移到鼠标上看全部），
        目标组织按租户现有部门映射生成，页面不指定落点；两栏的“含下级 / 全量同步”口径见列表下方的说明。<br>
        1.1）<span class="rule">一个“租户 + 方向”可以保存多条规则</span>：每条规则各自维护自己的
        <b>同步范围、计划任务（方式 + 配置 + 启停）、成员“没有找到部门”的口径与备注</b>，
        计划任务按每条规则自己的方式与配置分别执行（列表里用“规则 #id”区分）；表单不带 id 保存 = <b>新增一条</b>，
        点列表行尾“编辑”把某条读进表单后再保存 = <b>更新那一条</b>（需要时上方“租户”会跟着切到该行所属租户），
        点“重置表单”回到新增。<br>
        2）<span class="rule">同步方向</span>：A = OA → <%= oaonesPlanLabel %>（方向文案带平台名与当前租户），
        B = <%= oaonesPlanLabel %> → OA。<br>
        3）<span class="rule">同步范围</span>（勾选“按范围同步”后生效；不勾选 = 全量同步，与历史口径一致）：
        <b>A 方向</b>只选 <span class="rule">OA 分部（多选）+ OA 部门（多选）</span>；
        <b>B 方向</b>只选 <span class="rule"><%= oaonesPlanLabel %>部门（多选）</span>。
        选中的源组织<span class="rule">自动含其下级</span>；目标侧人员统一按租户的部门映射写入。
        保存后<span class="rule">每次执行（计划任务 / 立即执行一次）都按这条映射关系同步</span>。<br>
        4）<span class="rule">成员没有找到部门</span>（所在部门未映射 / 目标侧部门不存在）：
        两个方向均可选“不同步”“新增部门”“默认部门”。选择“新增部门”时会提示风险：
        <span class="rule">请尽量不要选择该方式，可能由于错误设置导致部门创建错误，请尽量先设置部门映射。</span>
        平台→OA 只有租户已映射到 OA 分部时才允许新建。默认部门选择租户下的具体部门；
        树状模式直接展示租户组织根节点，不再额外显示“租户本身”选项。<br>
        5）<span class="rule">计划任务</span>有两种方式（“同步方式”单选切换，两种取值一起保存、来回切不丢值）：
        <span class="rule">① 轮询间隔（分钟，默认）</span>——填“每 N 分钟同步一次”
        （下限 5 分钟、上限 1440 分钟 = 24 小时），下一次执行 = 上一次执行时刻 + N 分钟；
        <span class="rule">② 高级模式（cron）</span>——直接写 cron 表达式（Quartz 口径，
        6 个字段<b>秒 分 时 日 月 周</b>，例 <span class="mono">0 0 2 * * ?</span> = 每天 02:00；
        <span class="mono">0 0/30 * * * ?</span> = 每 30 分钟）。
        点“预测后 10 次”可按当前方式核对<b>实际会执行的时间</b>（右侧栏里给出）。
        <span class="rule">保存后即按页面上选的这套方式执行</span>：
        由本模块的<b>内置调度器</b>执行（默认每 30 秒扫描一次到点设置，<b>不需要在 E9 后台配任何计划任务</b>），
        它在有人访问 OAones 页面/接口时自动启动；状态见页面上方“内置调度器”一行。
        设置表行尾的<span class="rule">“暂停 / 启动”</span>可随时暂停或恢复该档计划任务
        （只改启停开关，同步范围与计划任务配置都不动，历史日志保留）。
        6）保存不会影响历史同步日志；“立即执行一次”按 **该条规则** 已保存的设置马上同步一次
        （列表行尾的“立即执行”执行的就是那一行；“删除”只删掉这一条规则，同档其它规则不受影响）；
        设置表行尾的<span class="rule">“结果详情”</span>直接打开独立的“同步结果”页，按批次查看
        <b>统计 + 逐条部门/人员明细</b>；流程建号产生的 requestid 也可在同一页查询。<br>
        7）<span class="rule">历史遗留的“全局默认配置”行</span>（早期版本保存的、作用于本平台所有
        <b>未单独配置该方向</b>的租户）：仍在列表里列出并在首列标注为“全局默认配置”，
        <b>本页不再提供“全局默认配置”的编辑入口</b>；要在具体租户上改成自己的设置，
        点该行行尾“覆盖为当前租户配置”把它读进表单（保存后即成为上方所选租户自己的规则）。
    </div>
</div>

<%-- 默认 OA 部门选择：单选 + 列表式/树状 + 搜索，单击选中、双击即确定并退出（与页面其它选择框同一交互） --%>
<div class="modal-mask" id="planDeptMask"></div>
<div class="modal wx-pick-modal" id="planDeptModal">
    <div class="modal-head">
        <span id="planDeptModalTitle">选择默认部门</span>
        <button type="button" class="link" id="planDeptClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="planDeptSearch" placeholder="输入部门名称/编号搜索" autocomplete="off"
                   data-lpignore="true">
            <div class="view-switch mini" data-view-key="planDept">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="planDeptList"></div>
        <div class="muted wx-pick-note">选择租户下的部门，树状模式直接展示组织根节点；单击选中，
            <span class="rule">双击即确定并退出</span>。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="planDeptClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="planDeptCancel">取消</button>
        <button type="button" class="btn" id="planDeptOk">确定</button>
    </div>
</div>

<%-- 同步范围·OA 分部选择：仅 A 方向使用，可多选（含下级分部与部门） --%>
<div class="modal-mask" id="planSubMask"></div>
<div class="modal wx-pick-modal" id="planSubModal">
    <div class="modal-head">
        <span id="planSubModalTitle">选择 OA 分部</span>
        <button type="button" class="link" id="planSubClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="planSubSearch" placeholder="输入分部名称/编号搜索" autocomplete="off"
                   data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" id="planSubAllBtn"
                    data-pick-all="planSub">全选</button>
            <div class="view-switch mini" data-view-key="planSub">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="planSubList"></div>
        <div class="muted wx-pick-note" id="planSubNoteText">可多选，<span class="rule">选中分部自动含其下级分部与部门</span>；
            单击选中/取消，双击即确定并退出。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="planSubClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="planSubCancel">取消</button>
        <button type="button" class="btn" id="planSubOk">确定</button>
    </div>
</div>

<%-- 同步范围·OA 部门选择：候选受“已选分部（含下级分部）”前置条件过滤 --%>
<div class="modal-mask" id="planDeptScopeMask"></div>
<div class="modal wx-pick-modal" id="planDeptScopeModal">
    <div class="modal-head">
        <span id="planDeptScopeModalTitle">选择 OA 部门</span>
        <button type="button" class="link" id="planDeptScopeClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="planDeptScopeSearch" placeholder="输入部门名称/编号搜索" autocomplete="off"
                   data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" id="planDeptScopeAllBtn"
                    data-pick-all="planDeptScope">全选</button>
            <div class="view-switch mini" data-view-key="planDeptScope">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-tip" id="planDeptScopeNote"></div>
        <div class="wx-pick-list" id="planDeptScopeList"></div>
        <div class="muted wx-pick-note" id="planDeptScopeNoteText">可多选，<span class="rule">选中部门自动含其下级部门</span>；
            已选分部时只列出该分部（含下级分部）下的部门。单击选中/取消，双击即确定并退出。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="planDeptScopeClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="planDeptScopeCancel">取消</button>
        <button type="button" class="btn" id="planDeptScopeOk">确定</button>
    </div>
</div>

<%-- 同步范围·平台部门选择：仅 B 方向使用，可多选（含下级部门） --%>
<div class="modal-mask" id="planWxMask"></div>
<div class="modal wx-pick-modal" id="planWxModal">
    <div class="modal-head">
        <span id="planWxModalTitle">选择<%= oaonesPlanLabel %>来源部门</span>
        <button type="button" class="link" id="planWxClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="planWxSearch" placeholder="输入部门名称/ID搜索" autocomplete="off"
                   data-lpignore="true">
            <div class="view-switch mini" data-view-key="planWx">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="planWxList"></div>
        <div class="muted wx-pick-note">候选来自<%= oaonesPlanLabel %>部门快照（先“更新部门快照”再设置）；
            可多选，<span class="rule">选中部门自动含其下级部门</span>。
            单击选中/取消，<span class="rule">双击即确定并退出</span>。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="planWxClearBtn">清空</button>
        <button type="button" class="btn secondary" id="planWxCancel">取消</button>
        <button type="button" class="btn" id="planWxOk">确定</button>
    </div>
</div>

<%-- 首次检测弹框：同步范围内（含下级）已有部门/成员绑定时提示，可选择“强制同步” --%>
<div class="modal-mask" id="planConflictMask"></div>
<div class="modal" id="planConflictModal" style="width:640px;">
    <div class="modal-head">
        <span>同步范围内已有绑定关系</span>
        <button type="button" class="link" id="planConflictClose">×</button>
    </div>
    <div class="modal-body">
        <p class="rule" id="planConflictMessage" style="line-height:1.7;"></p>
        <div class="muted" style="margin-bottom:4px;">检测到的部门 / 成员绑定（范围内含下级）：</div>
        <ol id="planConflictList" style="max-height:260px;overflow:auto;line-height:1.7;margin:0 0 0 18px;"></ol>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="planConflictCancel">取消</button>
        <button type="button" class="btn" id="planConflictForce">强制同步并保存</button>
    </div>
</div>

<%-- 同步明细弹窗：设置表行尾“结果详情”打开该次同步的统计 + 逐条明细 --%>
<div class="modal-mask" id="detailMask"></div>
<div class="modal" id="detailModal" style="width:900px;">
    <div class="modal-head">
        <span id="detailTitle">同步明细</span>
        <button type="button" class="link" id="detailClose">×</button>
    </div>
    <div class="modal-body">
        <div class="wx-pick-tip" id="detailMeta"></div>
        <div class="pick-view-bar">
            <input type="text" id="detailKeyword" placeholder="按名称/ID/说明过滤" autocomplete="off"
                   data-lpignore="true">
            <%-- 明细对象类型筛选：只作用于本弹窗的明细表，不涉及 picker.js 的显示模式 --%>
            <div class="view-switch mini" id="detailTypeSwitch">
                <button type="button" class="view-btn active" data-type="all">全部</button>
                <button type="button" class="view-btn" data-type="dept">部门/分部</button>
                <button type="button" class="view-btn" data-type="user">人员</button>
            </div>
        </div>
        <div class="bind-stats" id="detailStats" style="display:none;"></div>
        <div class="table-wrap" style="max-height:420px;overflow:auto;">
            <table class="grid" id="detailTable">
                <thead>
                <tr>
                    <th>类型</th><th>操作</th><th>OA 对象</th><th><%= oaonesPlanLabel %>对象</th>
                    <th>目标父节点</th><th>同步过程</th>
                </tr>
                </thead>
                <tbody id="detailBody"></tbody>
            </table>
            <div class="empty" id="detailEmpty" style="display:none;">该日志没有逐条明细（历史日志只有汇总文本）</div>
        </div>
        <div class="pager" id="detailPager"></div>
        <div class="detail" id="detailText" style="margin-top:10px;"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="detailOkBtn">关闭</button>
    </div>
</div>
