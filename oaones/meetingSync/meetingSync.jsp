<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    /*
     * 会议同步（独立一级菜单：/oaones/meetingSync/meetingSync.jsp）
     *
     * 与「日程同步 / 消息设置 / 同步中心」并列的一条独立一级菜单：本页只做“把分部下的会议同步到
     * 第三方平台（企业微信 / 钉钉 / 飞书）”，页面里不出现租户维护、也不出现日程同步的入口。
     *
     * 版式参考消息中心（msgCenter/msgCenter.jsp）：顶部一级标题 + 平台切换，下面一张卡片里
     * 提示行 + 感叹号说明 + 四个页签（同步设置 / 会议·日程同步清单 / 同步记录 / 个人同步方向）。
     *   * 同步设置：顶部一行「计划任务」（白天 9:00–18:00 / 夜间其余时段两个周期的频率，全局一份），
     *     下面一行一个租户（当前平台），逐租户开 / 关同步（不设“同步范围 / 最近同步 / 操作”列）；
     *     开启后由内置调度器按计划任务轮询，到点跑一轮（OA→第三方 推送 + 第三方→OA 拉取），无需手工触发；
     *   * 会议 / 日程同步清单：按 OA 对象维度看同步结果（一行一个对象，跳过 / 失败可“重推”）；
     *   * 同步记录：历史同步情况（一行一条，列表样式，带分页 / 筛选）；
     *   * 个人同步方向：非“遵循系统设置”的个人及其方向，只读，位置在「同步记录」后面
     *     （可按租户 / 分部 / 部门 / 人员 / 同步方向筛选；分部与部门是多选弹框、自动含下级、
     *     候选按 OA 分权过滤）。
     * 会议与日程共用同一套面板与脚本（js/mtgSync.js），用 biz 区分：本页 biz=0，
     * 日程同步页（/oaones/scheduleSync/scheduleSync.jsp）biz=1。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    String oaonesPlatform = request.getParameter("platform");
    if (oaonesPlatform == null) { oaonesPlatform = ""; }
    oaonesPlatform = oaonesPlatform.trim().toLowerCase();
    if (!"dingtalk".equals(oaonesPlatform) && !"feishu".equals(oaonesPlatform)) {
        oaonesPlatform = "wecom";
    }
    boolean oaonesDd = "dingtalk".equals(oaonesPlatform);
    boolean oaonesFs = "feishu".equals(oaonesPlatform);
    int oaonesType = oaonesFs ? 2 : (oaonesDd ? 1 : 0);
    String oaonesPlatformLabel = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企业微信");
    String oaonesPlatformShort = oaonesFs ? "飞书" : (oaonesDd ? "钉钉" : "企微");
    String oaonesApiUrl = oaonesFs ? "/oaones/Feishu/api.jsp"
            : (oaonesDd ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    String oaonesCfgAllLabel = oaonesFs ? "全部飞书租户" : (oaonesDd ? "全部钉钉租户" : "全部企微租户");
    String oaonesPlatformKeyLabel = oaonesFs ? "App ID" : (oaonesDd ? "AppKey" : "corpid");
    // 页签：?tab=setting|item|log|diruser（默认“同步设置”）
    String oaonesMtgTab = request.getParameter("tab");
    if (!"item".equals(oaonesMtgTab) && !"log".equals(oaonesMtgTab)
            && !"diruser".equals(oaonesMtgTab)) { oaonesMtgTab = "setting"; }
    boolean oaonesSysAdmin = OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()));
    // biz：0 会议（本页入口）/ 1 日程（由 /oaones/scheduleSync/scheduleSync.jsp 转发进来），
    // 两条一级菜单共用同一个页壳与面板，只换标题 / 文案 / 业务类型。
    int oaonesBiz = "1".equals(request.getParameter("biz")) ? 1 : 0;
    String oaonesBizTitle = oaonesBiz == 1 ? "日程同步" : "会议同步";
    String oaonesBizLabel = oaonesBiz == 1 ? "日程" : "会议";
    // 「同步清单」页签的标题（与 mtgSyncPanel.jsp 里的 <b id="mtgItemTitle"> 同一口径）
    String oaonesListItemTitle = oaonesBiz == 1 ? "日程同步清单" : "会议同步清单";
    String oaonesEntry = oaonesBiz == 1
            ? "/oaones/scheduleSync/scheduleSync.jsp" : "/oaones/meetingSync/meetingSync.jsp";
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <script>var P_FULL = "<%=oaonesPlatformLabel%>", P_SHORT = "<%=oaonesPlatformShort%>";
    var P_PLATFORM = "<%=oaonesPlatform%>";
    var P_KeyLabel = "<%=oaonesPlatformKeyLabel%>", P_AppIdLabel = "AgentId";</script>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%=oaonesBizTitle%> - OAones</title>
    <script>window.OAONES_API_URL = "<%=oaonesApiUrl%>";
    window.OAONES_CFG_ALL_LABEL = "<%=oaonesCfgAllLabel%>";
    window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=oaonesSysAdmin%> };
    window.OAONES_MTG_BIZ = <%=oaonesBiz%>;
    window.OAONES_MTG_TYPE = <%=oaonesType%>;
    window.OAONES_MTG_TITLE = "<%=oaonesBizTitle%>";
    window.OAONES_MTG_LABEL = "<%=oaonesBizLabel%>";
    window.OAONES_MTG_TAB = "<%=oaonesMtgTab%>";</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/datetime.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 一级标题只属于本菜单（“会议同步”）：不链到租户设置，也不链到登录设置 / 日程同步 --%>
<div class="navbar">
    <h1><%=oaonesBizTitle%></h1>
    <div class="nav">
        <span class="view-switch" title="切换平台：企微 / 钉钉 / 飞书的消息与日程接口不同，分别维护">
            <a class="view-btn<%= oaonesDd || oaonesFs ? "" : " active" %>"
               href="<%=oaonesEntry%>?platform=wecom&tab=<%=oaonesMtgTab%>">企业微信</a>
            <a class="view-btn<%= oaonesDd ? " active" : "" %>"
               href="<%=oaonesEntry%>?platform=dingtalk&tab=<%=oaonesMtgTab%>">钉钉</a>
            <a class="view-btn<%= oaonesFs ? " active" : "" %>"
               href="<%=oaonesEntry%>?platform=feishu&tab=<%=oaonesMtgTab%>">飞书</a>
        </span>
    </div>
</div>
<div class="page">
    <div class="card">
        <div class="toolbar" style="justify-content:space-between;">
            <div class="toolbar-left">
                <span class="notice" id="mtgUserTip"></span>
                <span class="help-tip" id="mtgHelpTip" data-help="mtgHelpText" role="button"
                      tabindex="0" title="查看页面说明" aria-label="查看页面说明">?</span>
            </div>
            <div>
                <button class="btn secondary" id="mtgRefreshBtn">刷新</button>
            </div>
        </div>
        <div class="help-source" id="mtgHelpText">
            <b>本页是独立的一级菜单「<%=oaonesBizTitle%>」</b>，把 OA 的<b><%=oaonesBizLabel%></b>同步到
            <span class="rule"><%=oaonesPlatformLabel%></span>的日程（会议 / 日程共一套推送逻辑，
            另一条菜单是「<%=oaonesBiz == 1 ? "会议同步" : "日程同步"%>」）。
            <p><b>同步设置</b>页签：最上面一行是<span class="rule">计划任务</span>——同步用轮询，
            频率按公司自己的上下班时间来配，两种同步方式：
            <span class="rule">① 按时间段</span>（默认，像排课表一样一行一个时间段：适用日
            <b>工作日 / 非工作日 / 每天</b> + 开始 / 结束时间 + 轮询间隔，可以配多个，
            同一适用日下的时间段不能重复；轮询从每个时段的开始时间起算，结束时间早于开始时间 = 跨天）；
            <span class="rule">② 高级模式</span>（直接写 cron 表达式，Quartz 口径：秒 分 时 日 月 周，
            与「同步中心」同一套语法，仍分白天 9:00–18:00 与夜间两档，另可选执行日）。
            <b>工作日 / 非工作日按泛微标准考勤算</b>（选一个考勤组：它的固定排班 / 工作日勾选
            叠加节假日与调休，调配工作日算上班），频率下限 5 分钟；
            配置存 <code>uf_oaones_mtg_cron</code> + 时间段明细 <code>uf_oaones_mtg_plan_item</code>，
            <b>改完立即生效</b>；保存前可点
            <span class="rule">「预测后 10 次」</span>看实际会执行的时间（会标明命中的是哪个时间段）；
            <span class="rule">到点跑一轮</span>：先 OA→第三方 推送（新增 / 修改 / 删除），
            再 第三方→OA 拉取（新增 / 修改 / 删除）。下面一行一个租户，
            <span class="rule">每个租户单独设置开启 / 关闭</span>——
            并勾选<span class="rule">同步方向</span>（<b>OA 同步到第三方 / 第三方同步到 OA</b>，两个复选框可双选，默认双选），
            再选<span class="rule">绑定应用</span>：会议 / 日程都是调第三方平台的<b>日程接口</b>推过去的，
            所以要用一个<b>有日程权限的应用</b>的 Secret 换 access_token；
            一个租户绑了多个应用（「应用绑定」页维护）时在这里逐租户指定，点旁边的「校验」可以用该应用的凭据
            真调一次平台接口，验证 Secret 与日程权限；<b>不指定</b>则用租户配置里的 Secret（通常没日程权限，可能报错）。
            租户<b>开启同步后，按上面的计划任务自动跑</b>，不需要在页面上手工触发。
            <span class="rule">每条对象都按内容快照比对，没变就不会调接口、也不会写记录</span>（“变了才同步”）；
            第一次同步失败 / 跳过的不会被自动重推，
            需要时到中间那个「<%=oaonesListItemTitle%>」页签手工点“重推”。</p>
            <p><b>同步口径（双向但“不做反向更新”= 谁创建谁维护）</b>：
            <span class="rule">OA 创建的<%=oaonesBizLabel%></span>，OA 侧的修改 / 删除会同步到
            <%=oaonesPlatformShort%>；<b><%=oaonesPlatformShort%>侧的修改 / 取消不写回 OA</b>
            （只在「同步记录」里留一条说明）。<span class="rule"><%=oaonesPlatformShort%>侧创建的日程</span>
            会拉到 OA；<b>OA 侧改了它也不反向同步到 <%=oaonesPlatformShort%></b>，
            而 <%=oaonesPlatformShort%> 侧的修改 / 删除会同步回 OA（OA 里按“标记删除”处理，不物理删行）。</p>
            <p><b>个人同步方向（非遵循系统设置）</b>（最后一个页签，在「同步记录」后面）：
            租户开启后，个人可在<b>个人账号中心</b>选择<span class="rule">遵循系统设置</span>（默认）
            或<span class="rule">指定同步方向</span>；本页签把“没有跟随系统设置”的人列出来，按<b>租户</b>
            与<span class="rule">分部 / 部门（多选弹框，选中自动含其下级；候选按泛微标准分权过滤，
            选了分部后部门只列该分部（含下级分部）下的部门）/ 人员（姓名 / 登录名）/ 同步方向</span>筛选，
            <b>只读</b>——要改只能本人在个人账号中心自己改。</p>
            <p><b><%=oaonesListItemTitle%></b>（三个页签里的第二个，在「同步设置」与「同步记录」中间）：
            按 OA 的
            <%=oaonesBizLabel%>逐个显示同步到第三方平台的结果（一行一个<%=oaonesBizLabel%>，取最近一次状态）；
            <span class="rule">点“状态”</span>（新增 / 更新 / 跳过 / 失败）可以查看这条<%=oaonesBizLabel%>下发给
            <span class="rule"><%=oaonesPlatformLabel%></span>的<b>接口报文</b>（请求地址 + 请求体 + 逐字段简要说明，
            只组装不下发、不含 access_token）；
            <span class="rule">“跳过 / 失败”的行可以点“重推”重新同步</span>，第一次同步与每次重推都会写进「同步记录」。</p>
            <p><b>同步记录</b>页签：历史同步情况（一行一条），可按租户、状态、时间、关键字筛选，
            失败行标红并在“说明”里给出失败原因；<span class="rule">点“状态”同样可以看报文</span>。</p>
            <p><b>时间窗</b>：只把<b>新建日期或开始日期落在时间窗内</b>的<%=oaonesBizLabel%>当成“新增”候选（
            <span class="rule">窗口前后天数</span>在 oaones.properties 里配，默认前 1 天 ~ 后 30 天）；
            创建人未绑定平台账号的<%=oaonesBizLabel%>会记为“跳过”，不会外发。</p>
        </div>
        <div class="sub-tabs" id="mtgTabs">
            <button type="button" data-tab="setting" class="active">同步设置</button>
            <button type="button" data-tab="item"><%=oaonesListItemTitle%></button>
            <button type="button" data-tab="log">同步记录</button>
            <button type="button" data-tab="diruser">个人同步方向</button>
        </div>
        <%-- 会议 / 日程共用的面板（标记 + 脚本）：本页 biz=0；日程同步页用同一份面板，biz=1 --%>
        <jsp:include page="mtgSyncPanel.jsp"/>
    </div>
</div>
<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/js/mtgSync.js?v=<%=oaonesAssetV%>"></script>
<script>
/* 页签切换：与消息中心同一套口径（切到哪个载哪个，重复载幂等） */
(function () {
    "use strict";
    function switchTab(tab) {
        if (tab !== "setting" && tab !== "item" && tab !== "log" && tab !== "diruser") { return; }
        var tabs = document.querySelectorAll("#mtgTabs button");
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].classList.toggle("active", tabs[i].getAttribute("data-tab") === tab);
        }
        var panels = document.querySelectorAll("[data-mtg-panel]");
        for (var j = 0; j < panels.length; j++) {
            panels[j].classList.toggle("active", panels[j].getAttribute("data-mtg-panel") === tab);
        }
        if (window.OaonesMtgSync) { window.OaonesMtgSync.switchTo(tab); }
    }
    window.MtgCenterTabs = { switchTo: switchTab };
    function init() {
        var tip = document.getElementById("mtgUserTip");
        if (tip) {
            tip.textContent = (window.OAONES_USER && window.OAONES_USER.sysAdmin)
                ? "你是系统管理员，可维护全部租户的会议同步"
                : "本页只列出你被指定为维护人的租户";
        }
        var tabs = document.querySelectorAll("#mtgTabs button");
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].addEventListener("click", function () {
                switchTab(this.getAttribute("data-tab"));
            });
        }
        var refresh = document.getElementById("mtgRefreshBtn");
        if (refresh) {
            refresh.addEventListener("click", function () {
                if (window.OaonesMtgSync) { window.OaonesMtgSync.reload(); }
            });
        }
        if (window.OaonesMtgSync) { window.OaonesMtgSync.init(); }
        var initial = window.OAONES_MTG_TAB;
        if (initial !== "item" && initial !== "log" && initial !== "diruser") { initial = "setting"; }
        switchTab(initial);
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
</script>
</body>
</html>
