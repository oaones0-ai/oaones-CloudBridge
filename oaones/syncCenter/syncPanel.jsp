<%@ page pageEncoding="UTF-8" %>
<%--
  【已停用】本片段不再被任何页面引入（保留仅供对照历史实现，可直接删除）。

  同步中心页面（/oaones/syncCenter/syncCenter.jsp）现在只保留两块：
    1) 同步设置（计划任务）；
    2) 编辑同步设置（同步方向 + **同步范围** + 计划任务）。
  “按范围同步”（先预览再执行）与“同步日志”列表已从页面上去掉：范围改在“编辑同步设置”里维护，
  保存后由计划任务每次按这条映射关系同步；“结果详情”仍可打开该次同步的统计 + 逐条明细
  （明细弹窗与本文件里的四个范围选择弹窗，已并入 syncPlanPanel.jsp + js/syncPlan.js）。

  ---- 以下为停用前的说明（历史实现） ----
  按范围同步主体（同步中心通用页面 /oaones/syncCenter/syncCenter.jsp 引入）：
  企微 / 钉钉 / 飞书**三个平台共用这一份标记**（三个平台的 DOM id 完全一致），
  交互脚本在 /oaones/js/syncCenter.js；同步设置（计划任务）在 syncPlanPanel.jsp。

  平台由引入方传：request 属性 oaonesSyncPlatform（页壳用 setAttribute 传，推荐）
  或 platform 参数；本片段据此算出 oaonesSyncFull（企业微信 / 钉钉 / 飞书）
  与 oaonesSyncShort（企微 / 钉钉 / 飞书），下文所有平台文案都用它们拼。

  1) 同步方向按“前者（来源）/ 后者（落点）”两行摆放，方向不同时 OA 侧与平台侧容器互换位置；
  2) 四个选择框都是弹框选择，双击选项即确定并退出选择；
  3) 开始同步先调 sync.preview 生成预览弹窗，确认后再调 sync.runScoped 真正写入；
  4) 同步结果、同步日志与明细弹窗（一行一个部门 / 一行一个人员）。
--%>
<%
    String oaonesSyncPlatform = (String) request.getAttribute("oaonesSyncPlatform");
    if (oaonesSyncPlatform == null || oaonesSyncPlatform.trim().length() == 0) {
        oaonesSyncPlatform = request.getParameter("platform");
    }
    if (oaonesSyncPlatform == null) {
        oaonesSyncPlatform = "";
    }
    oaonesSyncPlatform = oaonesSyncPlatform.trim().toLowerCase();
    boolean oaonesSyncDd = "dingtalk".equals(oaonesSyncPlatform);
    boolean oaonesSyncFs = "feishu".equals(oaonesSyncPlatform);
    String oaonesSyncFull = oaonesSyncFs ? "飞书" : (oaonesSyncDd ? "钉钉" : "企业微信");
    String oaonesSyncShort = oaonesSyncFs ? "飞书" : (oaonesSyncDd ? "钉钉" : "企微");
%>

<div class="card">
    <%-- 第一行：同步方向（必须先选方向，下面的选择框才出现） --%>
    <div class="toolbar">
        <span class="filter-label">同步方向</span>
        <select id="direction">
            <option value="">请选择同步方向</option>
            <option value="0">OA → <%= oaonesSyncFull %></option>
            <option value="1"><%= oaonesSyncFull %> → OA</option>
        </select>
        <span class="help-tip" id="syncScopeHelpTip" data-help="syncScopeHelpText" role="button" tabindex="0"
              title="查看页面说明" aria-label="查看页面说明">?</span>
    </div>
    <%-- 第二行：方向里的“前者”（来源侧）选择框；选择方向后才出现 --%>
    <div class="toolbar" id="syncRowFormer" style="display:none;">
        <span class="filter-label" id="syncFormerLabel">来源</span>
        <%-- OA 侧选择框容器（方向不同时在第二/第三行之间移动） --%>
        <span class="sync-scope-block" id="oaScopeBlock">
            <span class="filter-label" id="oaSubLabel">OA 分部</span>
            <div class="filter-picker" id="syncSubPicker" title="点击选择 OA 分部（可多选，含下级分部）">
                <input type="text" id="syncSubInput" class="filter-input" readonly
                       placeholder="选择 OA 分部（可多选）" autocomplete="off" data-lpignore="true">
                <span class="filter-arrow">▾</span>
                <button type="button" class="filter-clear" id="syncSubClear" title="清除分部选择">×</button>
            </div>
            <span class="filter-label" id="oaDeptLabel">OA 部门</span>
            <div class="filter-picker" id="syncDeptPicker" title="点击选择 OA 部门">
                <input type="text" id="syncDeptInput" class="filter-input" readonly
                       placeholder="选择 OA 部门" autocomplete="off" data-lpignore="true">
                <span class="filter-arrow">▾</span>
                <button type="button" class="filter-clear" id="syncDeptClear" title="清除部门选择">×</button>
            </div>
        </span>
        <%-- 平台侧选择框容器（租户 + 租户下部门；方向不同时在第二/第三行之间移动） --%>
        <span class="sync-scope-block" id="wxScopeBlock" style="display:none;">
            <span class="filter-label"><%= oaonesSyncFull %>租户</span>
            <div class="filter-picker" id="syncCfgPicker" title="点击选择<%= oaonesSyncFull %>租户（必选一个，不能选全部租户）">
                <input type="text" id="syncCfgInput" class="filter-input" readonly
                       placeholder="选择<%= oaonesSyncFull %>租户（必选）" autocomplete="off" data-lpignore="true">
                <span class="filter-arrow">▾</span>
            </div>
            <span class="filter-label"><%= oaonesSyncShort %>部门</span>
            <div class="filter-picker" id="syncWxDeptPicker" title="点击选择<%= oaonesSyncFull %>部门（单选，留空表示 root 节点）">
                <input type="text" id="syncWxDeptInput" class="filter-input" readonly
                       placeholder="不选 = root 节点（含其下级部门）" autocomplete="off" data-lpignore="true">
                <span class="filter-arrow">▾</span>
                <button type="button" class="filter-clear" id="syncWxDeptClear" title="清除<%= oaonesSyncShort %>部门选择">×</button>
            </div>
        </span>
    </div>
    <%-- 第三行：方向里的“后者”（目标侧）选择框；选择方向后才出现 --%>
    <div class="toolbar" id="syncRowLatter" style="display:none;">
        <span class="filter-label" id="syncLatterLabel">落点</span>
    </div>
    <%-- 第四行：开始同步 --%>
    <div class="toolbar" id="syncRowRun" style="display:none;">
        <button class="btn" id="previewBtn">开始同步</button>
    </div>
    <p class="notice" id="syncScopeTip"></p>
    <div class="help-source" id="syncScopeHelpText">页面固定四行：<span class="rule">第一行选同步方向</span>（选中后才出现后面的选择框）、
    <span class="rule">第二行是方向里的“前者”（来源侧）</span>、<span class="rule">第三行是“后者”（落点侧）</span>、
    <span class="rule">第四行开始同步</span>。<span class="rule">前者选中的多部门会全部挂在后者下方</span>。
    两侧范围都<span class="rule">含下级</span>；所有选择框都是弹框选择，<span class="rule">双击选项即确定并退出选择</span>。
    <br><span class="rule">OA → <%= oaonesSyncFull %></span>：第二行 = OA 范围（<b>OA 分部 / OA 部门都可多选</b>，至少选一个，
    选中分部自动含其下级分部与部门、选中部门自动含其下级部门），第三行 = <%= oaonesSyncShort %>落点
    （<%= oaonesSyncShort %>租户<b>必选一个</b>，不提供“全部租户”口径、停用租户不可选；<%= oaonesSyncShort %>部门<b>单选，可为空</b>，
    为空按该租户配置的 root 节点处理，含 root 下全部下级部门）。
    <br><span class="rule"><%= oaonesSyncFull %> → OA</span>：第二行 = <%= oaonesSyncShort %>范围（<%= oaonesSyncShort %>租户<b>必选一个</b>；
    <%= oaonesSyncShort %>部门<b>单选，可为空</b>，为空 = root 节点及其全部下级部门），
    第三行 = OA 落点：<span class="rule">先选 OA 落点分部（单选、必选）</span>，
    再选 <span class="rule">OA 落点部门（单选、非必选）</span>；落点部门候选只列出该落点分部
    （含下级分部）下的部门，未选落点分部时点落点部门会先提示选分部。
    只选分部（不选落点部门）= 落点就是该分部：<%= oaonesSyncShort %>部门按“部门绑定 / 同名”在该分部里匹配
    （匹配到就更新名称并建立绑定），匹配不到的部门<span class="rule">在该分部下新建顶级部门</span>；
    选了落点部门则新建部门挂在落点部门下。
    上级<%= oaonesSyncShort %>部门本次没同步成功时，其下级部门<span class="rule">记为跳过</span>（不会悄悄挂到落点上）。
    （历史数据里两个落点都为空时仍按旧口径：只在已有 OA 部门里按“部门绑定 / 同名”匹配，匹配不到的部门与人员不新建、记为跳过。）
    点“开始同步”<span class="rule">不会立即写数据</span>：先弹出确认框，列清本次将<span class="rule">更新/新增的部门与人员</span>，
    确认后才真正执行；执行过程按<span class="rule">单条部门、单条人员</span>写入同步日志明细，可在“同步日志 → 明细”逐条核对。
    未建立绑定的部门/人员会按本次范围<span class="rule">自动建立绑定并写入目标侧</span>（免绑定对象跳过；<%= oaonesSyncShort %>成员免绑定、OA 人员免绑定的都不参与同步）。</div>

    <div id="summaryArea" style="display:none;">
        <div class="summary">
            <div class="num"><span>处理总数</span><b id="sTotal">0</b></div>
            <div class="num"><span>成功</span><b id="sSuccess" style="color:var(--oa-success);">0</b></div>
            <div class="num"><span>跳过</span><b id="sSkip" style="color:var(--oa-warning);">0</b></div>
            <div class="num"><span>失败</span><b id="sFail" style="color:var(--oa-danger);">0</b></div>
        </div>
        <div class="detail" id="messageDetail" style="margin-top:10px;"></div>
    </div>
</div>

<div class="card">
    <div class="toolbar" style="justify-content:space-between;">
        <div><h3 style="margin:2px 0;">同步日志</h3></div>
        <button class="btn secondary" id="refreshLog">刷新</button>
    </div>
    <div class="filter-bar">
        <span class="filter-label">日志筛选：</span>
        <input type="text" id="logKeyword" placeholder="租户名称/日志ID/方向/状态/数量" autocomplete="off"
               data-lpignore="true" style="min-width:240px;max-width:360px;">
        <button type="button" class="btn small" id="logSearchBtn">搜索</button>
        <button type="button" class="btn small secondary" id="logResetBtn">重置筛选</button>
    </div>
    <div class="bind-stats" id="logStats" style="display:none;"></div>
    <div class="table-wrap">
        <table class="grid" id="logTable">
            <thead>
            <tr>
                <th>ID</th><th>租户</th><th>方向</th><th>总数</th><th>成功</th><th>跳过</th><th>失败</th>
                <th>部门</th><th>人员</th><th>未找到部门</th>
                <th>开始时间</th><th>结束时间</th><th>结果</th><th>操作</th>
            </tr>
            </thead>
            <tbody id="logBody"></tbody>
        </table>
        <div class="empty" id="emptyTip" style="display:none;">暂无同步日志</div>
    </div>
    <div class="pager" id="logPager"></div>
</div>

<%-- 平台租户选择（单选必选；租户之间没有上下级关系，按规范只提供“列表式”） --%>
<div class="modal-mask" id="syncCfgMask"></div>
<div class="modal wx-pick-modal" id="syncCfgModal">
    <div class="modal-head">
        <span>选择<%= oaonesSyncFull %>租户</span>
        <button type="button" class="link" id="syncCfgClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="syncCfgSearch" placeholder="输入租户名称/企业ID搜索" autocomplete="off" data-lpignore="true">
        </div>
        <div class="wx-pick-list" id="syncCfgList"></div>
        <div class="muted wx-pick-note">同步<span class="rule">必须指定一个<%= oaonesSyncFull %>租户</span>（本页不提供“全部租户”口径，
            停用中的租户不可选）；只列出你有权维护的租户。单击选中，<span class="rule">双击即确定并退出</span>。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="syncCfgCancel">取消</button>
        <button type="button" class="btn" id="syncCfgOk">确定</button>
    </div>
</div>

<%-- OA 分部选择：OA→平台 是来源分部（多选，含下级）；平台→OA 是 OA 落点分部（单选必选） --%>
<div class="modal-mask" id="syncSubMask"></div>
<div class="modal wx-pick-modal" id="syncSubModal">
    <div class="modal-head">
        <span id="syncSubModalTitle">选择 OA 分部</span>
        <button type="button" class="link" id="syncSubClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="syncSubSearch" placeholder="输入分部名称/编号搜索" autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" id="syncSubAllBtn"
                    data-pick-all="syncSub">全选</button>
            <div class="view-switch mini" data-view-key="syncSub">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="syncSubList"></div>
        <div class="muted wx-pick-note" id="syncSubNoteText">可多选，<span class="rule">选中分部自动含其下级分部</span>（该分部下全部部门一并纳入同步范围）；
            单击选中/取消，双击即确定并退出。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="syncSubClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="syncSubCancel">取消</button>
        <button type="button" class="btn" id="syncSubOk">确定</button>
    </div>
</div>

<%-- OA 部门选择：OA→平台 是来源部门（多选，含下级）；平台→OA 是 OA 落点部门（单选非必选）；
     候选都受“已选分部（含下级分部）”前置条件过滤 --%>
<div class="modal-mask" id="syncDeptMask"></div>
<div class="modal wx-pick-modal" id="syncDeptModal">
    <div class="modal-head">
        <span id="syncDeptModalTitle">选择 OA 部门</span>
        <button type="button" class="link" id="syncDeptClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="syncDeptSearch" placeholder="输入部门名称/编号搜索" autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small secondary pick-all-btn" id="syncDeptAllBtn"
                    data-pick-all="syncDept">全选</button>
            <div class="view-switch mini" data-view-key="syncDept">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-tip" id="syncDeptNote"></div>
        <div class="wx-pick-list" id="syncDeptList"></div>
        <div class="muted wx-pick-note" id="syncDeptNoteText">可多选，<span class="rule">选中部门自动含其下级部门</span>；
            已选分部时只列出该分部（含下级分部）下的部门。单击选中/取消，双击即确定并退出。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="syncDeptClearBtn">清空选择</button>
        <button type="button" class="btn secondary" id="syncDeptCancel">取消</button>
        <button type="button" class="btn" id="syncDeptOk">确定</button>
    </div>
</div>

<%-- 平台部门选择（多选，含下级；留空 = root 节点） --%>
<div class="modal-mask" id="syncWxMask"></div>
<div class="modal wx-pick-modal" id="syncWxModal">
    <div class="modal-head">
        <span>选择<%= oaonesSyncShort %>部门</span>
        <button type="button" class="link" id="syncWxClose">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="syncWxSearch" placeholder="输入<%= oaonesSyncShort %>部门名称/ID搜索" autocomplete="off" data-lpignore="true">
            <div class="view-switch mini" data-view-key="syncWx">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
        </div>
        <div class="wx-pick-list" id="syncWxList"></div>
        <div class="muted wx-pick-note">候选来自<%= oaonesSyncShort %>部门快照（先“更新<%= oaonesSyncShort %>部门快照”再同步）；
            <span class="rule">留空表示租户配置的 root 节点</span>（含 root 下全部下级部门）。
            单击选中，<span class="rule">双击即确定并退出</span>。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="syncWxClearBtn">清空（= root 节点）</button>
        <button type="button" class="btn secondary" id="syncWxCancel">取消</button>
        <button type="button" class="btn" id="syncWxOk">确定</button>
    </div>
</div>

<%-- 同步前确认弹窗：本次将更新/新增哪些部门、哪些人员 --%>
<div class="modal-mask" id="previewMask"></div>
<div class="modal" id="previewModal" style="width:760px;">
    <div class="modal-head">
        <span>同步确认</span>
        <button type="button" class="link" id="previewClose">×</button>
    </div>
    <div class="modal-body">
        <p class="rule" id="previewMessage" style="line-height:1.7;"></p>
        <div class="summary" id="previewSummary">
            <div class="num"><span>更新部门</span><b id="pDeptUpdate">0</b></div>
            <div class="num"><span>新增部门</span><b id="pDeptCreate">0</b></div>
            <div class="num"><span>更新人员</span><b id="pUserUpdate">0</b></div>
            <div class="num"><span>新增人员</span><b id="pUserCreate">0</b></div>
            <div class="num"><span>跳过</span><b id="pSkip" style="color:var(--oa-warning);">0</b></div>
            <div class="num"><span>无法同步</span><b id="pFail" style="color:var(--oa-danger);">0</b></div>
        </div>
        <%-- 清单：按“类型一行标题 + 每条一行明细”换行展示（不用 .detail 深色块，避免与正文样式串味） --%>
        <div id="previewLists" style="margin-top:6px;max-height:340px;overflow:auto;"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="previewCancel">取消</button>
        <button type="button" class="btn" id="previewOk">确认开始同步</button>
    </div>
</div>

<%-- 同步明细弹窗：逐条部门 / 逐条人员 --%>
<div class="modal-mask" id="detailMask"></div>
<div class="modal" id="detailModal" style="width:900px;">
    <div class="modal-head">
        <span id="detailTitle">同步明细</span>
        <button type="button" class="link" id="detailClose">×</button>
    </div>
    <div class="modal-body">
        <div class="wx-pick-tip" id="detailMeta"></div>
        <div class="pick-view-bar">
            <input type="text" id="detailKeyword" placeholder="按名称/ID/说明过滤" autocomplete="off" data-lpignore="true">
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
                    <th>类型</th><th>操作</th><th>OA 对象</th><th><%= oaonesSyncShort %>对象</th><th>目标父节点</th><th>同步过程</th>
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
