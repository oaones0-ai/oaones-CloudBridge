<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    /* 本页三个平台共用：平台由地址决定（?platform=wecom|dingtalk|feishu，不传按企微）；
       接口入口、平台类型、导航片段与文案都由下面变量派生，页面逻辑完全一致。 */
    String oaonesPBPlatform = request.getParameter("platform");
    if (oaonesPBPlatform == null || oaonesPBPlatform.trim().length() == 0) {
        oaonesPBPlatform = (String) request.getAttribute("oaonesPBPlatform");
    }
    if (oaonesPBPlatform == null) {
        oaonesPBPlatform = "";
    }
    oaonesPBPlatform = oaonesPBPlatform.trim().toLowerCase();
    boolean oaonesPBDd = "dingtalk".equals(oaonesPBPlatform);
    boolean oaonesPBFs = "feishu".equals(oaonesPBPlatform);
    String oaonesPBFull = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企业微信");
    String oaonesPBShort = oaonesPBFs ? "飞书" : (oaonesPBDd ? "钉钉" : "企微");
    int oaonesPBType = oaonesPBFs ? 2 : (oaonesPBDd ? 1 : 0);
    String oaonesPBApiUrl = oaonesPBFs ? "/oaones/Feishu/api.jsp"
            : (oaonesPBDd ? "/oaones/DingTalk/api.jsp" : "/oaones/WeCom/api.jsp");
    String oaonesPBNavbarPath = oaonesPBFs ? "/oaones/Feishu/navbar.jsp"
            : (oaonesPBDd ? "/oaones/DingTalk/navbar.jsp" : "/oaones/WeCom/navbar.jsp");
    // 生效的平台标识：地址没带 platform 时按企微，页面内所有平台跳转都用这个值（不再用原始参数，避免出现空的 platform=）
    String oaonesPBEffective = oaonesPBFs ? "feishu" : (oaonesPBDd ? "dingtalk" : "wecom");
    request.setAttribute("oaonesPBPlatform", oaonesPBEffective);
    String oaonesPBKeyLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    String oaonesPBAppIdLabel = oaonesPBFs ? "App ID" : "AgentId";
    // 模块首页（租户列表/租户设置）用的平台差异：列名、表单标签、根部门输入属性、是否有“应用标识”字段
    boolean oaonesPBHasAgent = !oaonesPBFs;                                  // 飞书租户没有 AgentId 字段
    String oaonesPBSecretName = oaonesPBFs ? "App Secret" : (oaonesPBDd ? "AppSecret" : "Secret");
    String oaonesPBRootDeptDefault = oaonesPBFs ? "0" : "1";
    String oaonesPBNameColLabel = oaonesPBFs ? "飞书组织" : (oaonesPBDd ? "钉钉组织" : "企微名称");
    String oaonesPBIdColLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "企业ID");
    // 弹窗里必填项统一在主色星号（.req-mark）里体现，标签文字不再自带 “*”
    String oaonesPBIdFormLabel = oaonesPBFs ? "App ID" : (oaonesPBDd ? "AppKey" : "corpid");
    // 弹窗里的凭据字段只留英文字段名：不再挂占位示例与行内说明文字
    String oaonesPBAgentLabel = "AgentId";
    // 根部门 ID 的输入属性：飞书根部门是 0（也支持 od-xxxx 形式的部门ID，故不做数字限制）
    String oaonesPBRootDeptAttrs = oaonesPBFs ? " value=\"0\"" : " type=\"number\" value=\"1\"";
%>

<%@ include file="/oaones/redirect.inc.jsp" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
%>
<%@ include file="/oaones/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <%-- 平台决定接口入口（AJAX 统一入口）与页面文案：三个平台共用本页，只换 platform 参数 --%>
    <script>window.OAONES_API_URL = "<%= oaonesPBApiUrl %>";
    window.OAONES_CFG_ALL_LABEL = "全部<%= oaonesPBFull %>租户";
    window.OAONES_CFG_TYPE = <%= oaonesPBType %>;
    var P_FULL = "<%= oaonesPBFull %>", P_SHORT = "<%= oaonesPBShort %>";
    var P_PLATFORM = "<%= oaonesPBEffective %>";
    var P_KeyLabel = "<%= oaonesPBKeyLabel %>", P_AppIdLabel = "<%= oaonesPBAppIdLabel %>";
    var P_RootDeptDefault = "<%= oaonesPBRootDeptDefault %>";
    var HAS_AGENT = <%= oaonesPBHasAgent %>;</script>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= oaonesPBShort %>租户设置 - OAones</title>
    <%-- 当前登录人：页面提示语按角色分别显示（sysAdmin = 系统管理员或超管角色成员，见后端 OaonesAdmin.isTotalAdmin） --%>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/help.js?v=<%=oaonesAssetV%>"></script>
    <%-- 密码 / 密钥框的掩码口径：编辑时只显示 **** --%>
    <script src="/oaones/js/secret.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <%-- 列表样式（表格 + 分页 + 列宽拖拽）用公共列表组件，不自行实现分页 --%>
    <script src="/oaones/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 本页是**独立的一级菜单「平台租户设置」**：只展示平台租户列表（默认块状，可切列表样式），
     不与「消息设置 / 登录设置」互链——那两条菜单各有自己的 URL，互相独立 --%>
<div class="navbar">
    <%-- 一级标题只写页面名：平台由地址决定（?platform=wecom|dingtalk|feishu），
         **标题右侧不再显示平台标识**（企业微信 / 钉钉 / 飞书），也不提供跨平台切换入口
         （三个平台的“租户设置”各自是一条独立的一级菜单，各有自己的 URL）。 --%>
    <h1><%= oaonesPBShort %>租户设置</h1>
</div>
<div class="page">
    <div class="card">
        <%-- 卡片头：标题 + 辅助小字 + 操作区（展示方式 / 刷新 / 新增） --%>
        <div class="page-head">
            <div class="page-head-main">
                <h3 class="page-title"><%= oaonesPBShort %>租户设置
                    <span class="help-tip inline" id="idxHelpTip" data-help="idxHelpText" role="button" tabindex="0"
                          title="查看页面说明" aria-label="查看页面说明">?</span></h3>
                <p class="page-sub">维护<%= oaonesPBFull %>租户凭据<%= oaonesPBHasAgent
                        ? "（" + oaonesPBKeyLabel + " / AgentId / " + oaonesPBSecretName + "）"
                        : "（" + oaonesPBKeyLabel + " / " + oaonesPBSecretName + "）" %>，并指定租户维护人</p>
            </div>
            <div class="page-actions">
                <span class="view-switch" title="切换展示方式：默认块状，也可以按列表查看">
                    <button type="button" class="view-btn active" id="viewCardBtn" onclick="setIdxView('card')">块状</button>
                    <button type="button" class="view-btn" id="viewListBtn" onclick="setIdxView('list')">列表</button>
                </span>
                <button class="btn secondary" id="refreshBtn">刷新</button>
                <button class="btn" id="addBtn" onclick="openEdit(null)">新增<%= oaonesPBShort %>租户</button>
            </div>
        </div>
        <%-- 页面说明按登录人角色分两套：系统管理员（全部租户）与维护人/普通用户（只看本人维护的租户） --%>
        <div class="help-source" id="idxHelpText"><% if (OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))) { %>本页是模块首页，只展示<b>块状<%= oaonesPBShort %>租户</b>：每个块对应一个独立<%= oaonesPBFull %>主体（<%= oaonesPBKeyLabel %> + <%= oaonesPBSecretName %>）。
            你是<b>系统管理员</b>（user.id=1）或超管角色成员，可查看与管理全部租户：未指定维护人的租户也只对系统管理员开放。
            在首页即可查看<b>租户数量</b>、<b>新增租户</b>、<b>删除租户</b>、<b>测试连接</b>；
            <b>点击租户块</b>直接进入该租户（默认“人员绑定”）：顶部左侧是模块标题、右侧是<b>当前租户名 + 设置</b>，
            下方是该租户的<b>二级菜单（页签）</b>——同步中心 / 部门绑定 / 人员绑定 / 快照列表 / 应用绑定，
            点页签即可切换功能（进入后“<%= oaonesPBFull %>租户”即为该租户、<b>不能切换租户</b>），
            页面上不再展示<%= oaonesPBShort %>租户主信息；租户主信息（企业ID、Secret、维护人等）在“设置”里。
            <b>消息设置</b>（OA 消息推送：推送配置 / 租户开关 / 消息类型 / 推送日志 / 数据看板）
            是<b>另外一条独立的一级菜单</b>（<span class="mono">/oaones/msgCenter/msgCenter.jsp</span>），
            本页不提供它的入口，从 E9 后台菜单单独打开即可；
            <b><%= oaonesPBShort %>工作台应用（<%= oaonesPBAppIdLabel %> → OA 页面、免登录）在租户二级菜单的“应用绑定”页维护</b>，
            消息推送只引用同一张表，不在消息中心里重复维护应用。
            <b>登录设置</b>（<span class="mono">/oaones/loginConfig/config.jsp</span>）同样是独立的一级菜单：
            控制<b>统一登录页</b>显示哪些登录方式（账号密码 / 手机号验证 / 扫码登录：企业微信·钉钉·飞书三个勾选框）、
            扫码二维码有效期多少秒（扫码是同一张二维码，勾上的平台才允许扫）。
            本模块的几条一级菜单（各平台的“租户设置”、消息设置、登录设置）<b>互相独立</b>，各有自己的 URL。
            <% if (oaonesPBFs) { %>飞书租户没有 <span class="rule">AgentId</span> 字段（弹窗只维护
            名称 / App ID / App Secret / 根部门），但共用租户表里有 agent_id 列，保存时会整行更新。<% } %>
            租户按 <b>维护人</b> 分权：被指定为维护人的用户可查看、设置该租户，并可在“同步中心”选择该租户同步；
            请在新增/编辑弹框的“维护人”里为各租户指定维护人（支持多个 OA 员工，留空即仅系统管理员可见）。
        <% } else { %>本页是模块首页，只展示<b>块状<%= oaonesPBShort %>租户</b>：每个块对应一个独立<%= oaonesPBFull %>主体（<%= oaonesPBKeyLabel %> + <%= oaonesPBSecretName %>）。
            租户按 <b>维护人</b> 分权：本页只列出你被指定为维护人的租户，你可以查看、设置这些租户，
            并在首页查看<b>租户数量</b>、<b>新增租户</b>、<b>删除租户</b>、<b>测试连接</b>；
            <b>点击租户块</b>直接进入该租户（默认“人员绑定”）：顶部左侧是模块标题、右侧是<b>当前租户名 + 设置</b>，
            下方是该租户的<b>二级菜单（页签）</b>——人员绑定 / 部门绑定 / 同步中心 / 快照列表 / 应用绑定，
            点页签即可切换功能，页面上不再展示<%= oaonesPBShort %>租户主信息；租户主信息在“设置”里。
            <b>消息设置</b>（推送配置 / 租户开关 / 消息类型 / 推送日志 / 数据看板）是另外一条独立的一级菜单
            （<span class="mono">/oaones/msgCenter/msgCenter.jsp</span>），本页不提供它的入口；
            <%= oaonesPBShort %>工作台应用在租户二级菜单的<b>“应用绑定”</b>页维护。
            未指定维护人的租户不对外可见，也不会出现在你的列表中。
        <% } %></div>
        <div class="filter-bar">
            <span class="filter-label">租户筛选：</span>
            <input type="text" id="idxKeyword" name="oaonesIdxKeyword" placeholder="<%= oaonesPBShort %>名称/企业ID/<%= oaonesPBAppIdLabel %>/根部门ID/维护人/备注/开关" autocomplete="off" data-lpignore="true">
            <button type="button" class="btn small" id="idxSearchBtn">搜索</button>
            <button type="button" class="btn small secondary" id="idxResetBtn">重置筛选</button>
        </div>
        <div class="bind-stats" id="idxStats" style="display:none;"></div>
        <div class="table-wrap" id="listView" style="display:none;">
            <table class="grid" id="cfgTable">
                <thead>
                <tr>
                    <th style="width:56px;">ID</th><th><%= oaonesPBNameColLabel %></th>
                    <th><%= oaonesPBIdColLabel %></th><% if (oaonesPBHasAgent) { %><th><%= oaonesPBAppIdLabel %></th><% } %>
                    <th style="width:88px;">根部门ID</th><th>维护人</th><th style="width:88px;">同步开关</th>
                    <th style="width:88px;">消息通知</th><th style="width:150px;">更新时间</th><th>备注</th>
                    <th style="width:190px;">操作</th>
                </tr>
                </thead>
                <tbody id="dataBody"></tbody>
            </table>
        </div>
        <div class="cfg-card-grid" id="cardView"></div>
        <div class="pager" id="idxPager"></div>
        <div class="empty" id="emptyTip" style="display:none;">暂无租户配置，请点击“新增<%= oaonesPBShort %>租户”</div>
    </div>
</div>

<div class="modal-mask" id="modalMask"></div>
<div class="modal modal-wide" id="editModal">
    <div class="modal-head">
        <span id="modalTitle"><%= oaonesPBShort %>租户配置</span>
        <%-- 关闭用线性图标（与页面其它图标同一粗细），不再用字符 ✕ --%>
        <button type="button" class="modal-x" onclick="closeEdit()" title="关闭" aria-label="关闭">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg>
        </button>
    </div>
    <div class="modal-body">
        <input type="hidden" id="id">
        <%-- 表单分组：基础信息 / 平台凭据 / 使用与权限（分组小标题 + 浅分割，替代整片平铺的字段列表） --%>
        <div class="form-group">
            <div class="form-group-title">基础信息</div>
            <div class="form-row"><label><%= oaonesPBShort %>名称<span class="req-mark">*</span></label>
                <div class="field"><input type="text" id="corp_name" placeholder="例如：集团<%= oaonesPBShort %>"></div></div>
            <div class="form-row"><label>根部门ID<% if (oaonesPBFs) { %><span class="help-tip inline"
                    id="rootDeptHelpTip" data-help="rootDeptHelpText" role="button" tabindex="0"
                    title="查看根部门说明" aria-label="查看根部门说明">?</span><% } %></label>
                <div class="field"><input id="root_dept_id"<%= oaonesPBRootDeptAttrs %>></div></div>
            <% if (oaonesPBFs) { %><%-- 飞书的根部门口径从行内占位说明改成“?”提示，表单本身保持干净 --%>
            <div class="help-source" id="rootDeptHelpText">飞书租户的根部门填 <b>0</b>；
                形如 <b>od-xxxx</b> 的部门ID 也支持，因此该输入框不做数字限制。</div><% } %>
        </div>
        <div class="form-group">
            <div class="form-group-title">平台凭据</div>
            <div class="form-row"><label><%= oaonesPBIdFormLabel %><span class="req-mark">*</span></label>
                <div class="field"><input type="text" id="corp_id"></div></div>
            <%-- 平台凭据的字段顺序：corpid → AgentId → Secret（AgentId 在 Secret 上面） --%>
            <% if (oaonesPBHasAgent) { %>
            <div class="form-row"><label><%= oaonesPBAgentLabel %></label>
                <div class="field"><input type="text" id="agent_id"></div></div>
            <% } %>
            <div class="form-row"><label><%= oaonesPBSecretName %><span class="req-mark">*</span></label>
                <div class="field"><input id="corp_secret" name="oaonesCorpSecret" type="password"
                        autocomplete="new-password" data-lpignore="true"></div></div>
        </div>
        <div class="form-group">
            <div class="form-group-title">使用与权限</div>
            <div class="form-row">
                <label>维护人<span class="help-tip inline" id="maintainerHelpTip" data-help="maintainerHelpText"
                        role="button" tabindex="0" title="查看维护人说明" aria-label="查看维护人说明">?</span></label>
                <div class="field">
                    <div class="mt-picker" id="mtBox" title="点击选择 OA 员工（可多选，点击标签上的 × 可移除）">
                        <span class="mt-chips" id="mtChips"></span>
                        <%-- 箭头由公共样式（style.css 的 .mt-picker-arrow）用线性 SVG 背景绘制，页面里不再放字形 --%>
                        <span class="mt-picker-arrow" aria-hidden="true"></span>
                    </div>
                </div>
            </div>
            <%-- 维护人说明同样按角色：系统管理员看“仅系统管理员可见”，其他登录人看“自己也会失去该租户” --%>
            <div class="help-source" id="maintainerHelpText"><% if (OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))) { %>可多选 OA 员工；只有被指定为维护人的用户才能查看、设置该租户，并在“同步中心”选择该租户同步。
                不设置维护人时，该租户仅系统管理员（user.id=1）可查看与维护；新建租户时若未指定维护人，
                创建人（系统管理员除外）会自动成为维护人。
            <% } else { %>可多选 OA 员工；只有被指定为维护人的用户才能查看、设置该租户，并在“同步中心”选择该租户同步。
                把自己移除或清空维护人后，你和其他维护人都将失去该租户的查看与维护权限；
                新建租户时若未指定维护人，创建人会自动成为维护人。
            <% } %></div>
            <div class="form-row">
                <label>同步开关</label>
                <div class="field">
                    <select id="sync_enable">
                        <option value="1">启用</option>
                        <option value="0">停用</option>
                    </select>
                </div>
            </div>
            <div class="form-row"><label>备注</label><div class="field"><textarea id="remark" rows="2"></textarea></div></div>
        </div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" onclick="closeEdit()">取消</button>
        <button class="btn" id="saveBtn">保存</button>
    </div>
</div>

<%-- 维护人选择：从 OA 员工中多选（选中即加入标签，确定后回填） --%>
<div class="modal-mask" id="mtPickMask"></div>
<div class="modal wx-pick-modal" id="mtPickModal" style="width:700px;">
    <div class="modal-head">
        <span>选择维护人（可多选 OA 员工）</span>
        <button type="button" class="modal-x" onclick="closeMtPick()" title="关闭" aria-label="关闭">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg>
        </button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="mtPickSearch" placeholder="输入姓名/登录名/工号/手机号/ID/部门搜索" autocomplete="off">
            <div class="view-switch mini" id="mtPickViewSwitch" data-view-key="mtPick">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
            <button type="button" class="btn small secondary pick-all-btn" id="mtPickAllBtn" data-pick-all="mtPick"
                    title="全选/全不选当前列表（搜索后为命中的前 200 人）">全选</button>
        </div>
        <div class="wx-pick-list" id="mtPickList"></div>
        <div class="muted wx-pick-note" id="mtPickNote">支持多选，选中后点击“确定”回填维护人标签；树状模式按 OA 部门分组展示，双击某行可直接确定并退出。</div>
    </div>
    <div class="modal-foot">
        <button class="btn secondary" onclick="clearMtPick()">清空已选</button>
        <button class="btn secondary" onclick="closeMtPick()">取消</button>
        <button class="btn" onclick="confirmMtPick()">确定</button>
    </div>
</div>

<script src="/oaones/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
function byId(id) { return document.getElementById(id); }
function esc(v) { return Oaones.esc(v); }

/**
 * 页面统一提示：mode 0 普通提示 / 1 警告 / 2 操作错误 / 3 系统错误，
 * duration 为显示毫秒数（不传用各模式默认值，<=0 表示常驻）。
 * 禁止使用原生 alert / confirm，统一由 /oaones/js/toast.js 弹出。
 */
function showToast(msg, mode, duration) {
    if (msg === null || msg === undefined || String(msg) === "") { return; }
    OaonesToast.show(msg, { mode: mode, duration: duration });
}

/** 当前登录人可见的租户（cfg.list，已按维护人过滤）；首页只展示块状租户，不套分页 */
var cfgRows = [];
var idxKeyword = "";
/**
 * 展示方式：card = 块状（默认）/ list = 列表。
 * 两种方式是同一份数据、同一个筛选与统计，只是渲染不同；选择记在浏览器本地，
 * 下次打开仍用上次的样式（按“菜单 + 展示方式”存，不影响其它页面）。
 */
var idxViewMode = "card";
/** 列表方式的分页器（块状方式展示全部命中行，不套分页） */
var idxPager = null;
/* 维护人（分权）：mtSelected 为已选维护人 [{id,name}]；mtAllUsers 为懒加载的 OA 人员缓存 */
var mtSelected = [];
var mtAllUsers = null;
var mtPickTemp = {};
var MT_PICK_MAX = 200;

/* ---------------- 维护人：展示与选择 ---------------- */

function rowMaintainers(r) {
    var out = [];
    var list = (r && r.maintainers) || [];
    list.forEach(function (m) {
        if (!m || m.id === null || m.id === undefined) { return; }
        out.push({ id: String(m.id), name: m.name ? String(m.name) : "" });
    });
    return out;
}

function maintainerLabel(m) {
    return m.name ? m.name : ("ID " + m.id);
}

/** 卡片“维护人”展示：人员标签 + “我维护”标记 */
function maintainerCellHtml(r) {
    var rows = rowMaintainers(r);
    if (!rows.length) { return "<span class='muted'>未设置</span>"; }
    var html = "<span class='mt-tag-list'>";
    rows.forEach(function (m) {
        html += "<span class='mt-chip-static' title='OA 人员ID " + esc(m.id) + "'>"
            + OaonesHrm.user(m.id, maintainerLabel(m)) + "</span>";
    });
    html += "</span>";
    if (r && r.can_maintain) { html += " <span class='tag manual'>我维护</span>"; }
    return html;
}

/** 编辑弹框维护人标签 */
function renderMtChips() {
    var html = "";
    mtSelected.forEach(function (m) {
        html += "<span class='mt-chip' title='OA 人员ID " + esc(m.id) + "'>"
            + OaonesHrm.user(m.id, maintainerLabel(m))
            + "<button type='button' class='mt-chip-del' data-id='" + esc(m.id)
            + "' title='移除该维护人'>×</button></span>";
    });
    if (!mtSelected.length) {
        // 提示语按登录人角色：系统管理员提示“仅你可查看”，其他登录人提示“自己也会失去该租户”
        html = "<span class='mt-empty'>" + OaonesTips.pick(
            "未设置维护人（仅系统管理员可查看与维护）",
            "未设置维护人（保存后你将失去该租户）") + "</span>";
    }
    byId("mtChips").innerHTML = html;
}

function setMaintainerSelection(row) {
    mtSelected = rowMaintainers(row);
    renderMtChips();
}

function onMtChipClick(e) {
    var t = e.target;
    if (!t || !t.classList || !t.classList.contains("mt-chip-del")) { return; }
    e.stopPropagation();
    var id = String(t.getAttribute("data-id"));
    mtSelected = mtSelected.filter(function (m) { return m.id !== id; });
    renderMtChips();
}

function mtUserText(u) {
    return [u.lastname, u.loginid, u.workcode, u.mobile, u.id, u.departmentname].map(function (v) {
        return String(v == null ? "" : v);
    }).join(" ");
}

function mtPickRows() {
    var rows = mtAllUsers || [];
    var k = (byId("mtPickSearch").value || "").trim().toLowerCase();
    if (!k) { return rows; }
    return rows.filter(function (u) { return mtUserText(u).toLowerCase().indexOf(k) >= 0; });
}

function mtPickSelectedCount() {
    var count = 0;
    Object.keys(mtPickTemp).forEach(function (id) { if (mtPickTemp[id]) { count++; } });
    return count;
}

function updateMtPickNote(matchCount, shownCount) {
    var note = byId("mtPickNote");
    var text = "已选 " + mtPickSelectedCount() + " 人，确定后写入维护人标签；树状模式按 OA 部门分组，双击某行可直接确定并退出";
    if (matchCount !== undefined && matchCount > shownCount) {
        text += "；当前匹配 " + matchCount + " 人，仅显示前 " + shownCount + " 人，请输入更精确的关键词";
    }
    note.textContent = text;
}

function renderMtPickList() {
    syncMtPickAllState();
    if (mtAllUsers === null) { return; }
    var listEl = byId("mtPickList");
    var mode = OaonesPicker.mode("mtPick");
    var keyword = byId("mtPickSearch").value;
    var html;
    var matchCount;
    var shownCount;
    if (mode === "tree") {
        var limited = (mtAllUsers || []).slice(0, MT_PICK_MAX);
        matchCount = limited.length;
        shownCount = limited.length;
        html = OaonesPicker.renderTree("mtPick", mtPickTreeRows(limited), {
            parentOf: function (r) { return r.parent; },
            textOf: function (r) { return r.searchText; },
            isGroup: function (r) { return r.group === true; },
            groupHtml: function (r, open, hit) { return OaonesPicker.groupHtml("mtPick", r, open, hit); },
            keyword: keyword,
            emptyText: "没有匹配的 OA 员工",
            optionHtml: function (r) { return mtPickRowHtml(r.user); }
        });
    } else {
        var rows = mtPickRows();
        var shown = rows.slice(0, MT_PICK_MAX);
        matchCount = rows.length;
        shownCount = shown.length;
        if (!shown.length) {
            html = "<div class='wx-pick-empty'>没有匹配的 OA 员工</div>";
        } else {
            html = "<div class='wx-pick-head mt-pick-head'>"
                + "<span>选择</span><span>姓名</span><span>登录名 / 工号 / 部门</span></div>";
            shown.forEach(function (u) { html += mtPickRowHtml(u); });
        }
    }
    listEl.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
    listEl.innerHTML = html;
    updateMtPickNote(matchCount, shownCount);
}

/** 当前列表可全选的候选：与两种显示模式一致（先取前 MT_PICK_MAX 人，再按搜索关键字过滤）。 */
function mtPickAllRows() {
    var limited = (mtAllUsers || []).slice(0, MT_PICK_MAX);
    var k = (byId("mtPickSearch").value || "").trim().toLowerCase();
    return k ? limited.filter(function (u) { return mtUserText(u).toLowerCase().indexOf(k) >= 0; }) : limited;
}

/** “全选”按钮状态：当前列表候选是否已全部选中，返回是否全选。 */
function syncMtPickAllState() {
    var rows = mtPickAllRows();
    var allOn = rows.length > 0 && rows.every(function (u) { return mtPickTemp[String(u.id)] === true; });
    OaonesPicker.syncSelectAll("mtPick", allOn);
    return allOn;
}

/**
 * 全选/全不选：把当前列表（含搜索过滤）里的 OA 人员一次性选中或取消，确定后写入维护人标签。
 * 已全部选中时按钮变“全不选”，再点即取消当前列表里的选择。
 */
OaonesPicker.setSelectAll("mtPick", function () {
    var rows = mtPickAllRows();
    var on = syncMtPickAllState();
    rows.forEach(function (u) {
        var id = String(u.id);
        if (on) { delete mtPickTemp[id]; } else { mtPickTemp[id] = true; }
    });
    renderMtPickList();
    return syncMtPickAllState();
});

/** 维护人候选行（列表式/树状共用），选中态取自 mtPickTemp。 */
function mtPickRowHtml(u) {
    var id = String(u.id);
    var on = mtPickTemp[id] ? true : false;
    var sub = [u.loginid, u.workcode, u.departmentname].filter(function (v) {
        return v !== null && v !== undefined && String(v).length > 0;
    }).join(" / ");
    return "<label class='mt-pick-row" + (on ? " checked" : "") + "'>"
        + "<input type='checkbox' class='mt-pick-box' data-id='" + esc(id) + "'"
        + (on ? " checked" : "") + ">"
        + "<span class='mt-pick-name'>" + esc(u.lastname || "") + "</span>"
        + "<span class='mt-pick-sub'>" + esc(sub) + "</span>"
        + "</label>";
}

/** 维护人树：按 OA 部门分组，部门为分组节点、员工为可选叶子。 */
function mtPickTreeRows(rows) {
    var out = [];
    var groups = {};
    (rows || []).forEach(function (u) {
        var deptName = String(u.departmentname == null ? "" : u.departmentname).trim() || "未分配部门";
        var gid = "g:" + deptName;
        if (!groups[gid]) {
            groups[gid] = { group: true, id: gid, parent: "", name: deptName, count: 0 };
            out.push(groups[gid]);
        }
        groups[gid].count++;
        out.push({
            group: false, id: "u:" + String(u.id), parent: gid, user: u,
            searchText: mtUserText(u)
        });
    });
    return out;
}

function loadMtUsers() {
    byId("mtPickList").innerHTML = "<div class='wx-pick-empty'>正在加载 OA 人员…</div>";
    Oaones.api("user.oaUsers", {}, function (res) {
        if (res.code !== 0) {
            mtAllUsers = [];
            byId("mtPickList").innerHTML = "<div class='wx-pick-empty'>OA 人员加载失败："
                + esc(res.msg) + "</div>";
            return;
        }
        mtAllUsers = res.data || [];
        renderMtPickList();
    });
}

function openMtPick() {
    mtPickTemp = {};
    mtSelected.forEach(function (m) { mtPickTemp[m.id] = true; });
    byId("mtPickSearch").value = "";
    // 树状默认收起分支：每次打开弹框重置展开状态
    OaonesPicker.resetTree("mtPick");
    byId("mtPickMask").style.display = "block";
    byId("mtPickModal").style.display = "block";
    if (mtAllUsers === null) { loadMtUsers(); } else { renderMtPickList(); }
    byId("mtPickSearch").focus();
}

function closeMtPick() {
    byId("mtPickMask").style.display = "none";
    byId("mtPickModal").style.display = "none";
    mtPickTemp = {};
}

function clearMtPick() {
    mtPickTemp = {};
    renderMtPickList();
}

function onMtPickListChange(e) {
    var t = e.target;
    if (!t || !t.classList || !t.classList.contains("mt-pick-box")) { return; }
    var id = String(t.getAttribute("data-id"));
    if (t.checked) { mtPickTemp[id] = true; } else { delete mtPickTemp[id]; }
    var row = t.parentNode;
    while (row && row.classList && !row.classList.contains("mt-pick-row")) { row = row.parentNode; }
    if (row && row.classList) { row.classList.toggle("checked", t.checked); }
    updateMtPickNote();
}

/** 确认：按 OA 人员列表顺序回填，保留已失效（不在列表中）的历史维护人 */
function confirmMtPick() {
    var picked = [];
    var seen = {};
    (mtAllUsers || []).forEach(function (u) {
        var id = String(u.id);
        if (mtPickTemp[id] && !seen[id]) {
            picked.push({ id: id, name: u.lastname || "" });
            seen[id] = true;
        }
    });
    mtSelected.forEach(function (m) {
        if (mtPickTemp[m.id] && !seen[m.id]) {
            picked.push(m);
            seen[m.id] = true;
        }
    });
    mtSelected = picked;
    renderMtChips();
    closeMtPick();
}

/* ---------------- 首页：租户块 ---------------- */

function rowArg(r) {
    return JSON.stringify(r).replace(/"/g, "&quot;");
}

/**
 * 进入该租户：直接落到二级标题的第一个页签“人员绑定”，
 * 一级标题右侧显示“当前租户名 + 设置”，不再展示" + P_SHORT + "租户主信息（主信息在“设置”里）。
 */
function enterTenant(id) {
    location.href = "/oaones/userBind/userBind.jsp?platform=" + P_PLATFORM
        + "&cfg_id=" + encodeURIComponent(id);
}

function renderStats(rows) {
    var enabled = 0;
    var mine = 0;
    cfgRows.forEach(function (r) {
        if (String(r.sync_enable) === "1") { enabled++; }
        if (r.can_maintain) { mine++; }
    });
    var statsEl = byId("idxStats");
    statsEl.style.display = "";
    statsEl.innerHTML = ""
        + "<span class='bind-stat'><span>租户共计</span><b>" + cfgRows.length + "</b> 个</span>"
        + "<span class='bind-stat'><span>启用</span><b>" + enabled + "</b> 个</span>"
        + "<span class='bind-stat'><span>停用</span><b>" + (cfgRows.length - enabled) + "</b> 个</span>"
        + "<span class='bind-stat'><span>我维护</span><b>" + mine + "</b> 个</span>"
        + "<span class='bind-stat'><span>当前显示</span><b>" + rows.length + "</b> 个</span>";
}

function cfgRowText(r) {
    return [r.id, r.corp_name, r.corp_id, r.agent_id, r.root_dept_id, r.remark,
        r.maintainer_names,
        r.sync_enable === "1" ? "启用" : "停用"].join(" ");
}

function filteredCfgRows() {
    var k = (idxKeyword || "").trim().toLowerCase();
    if (!k) { return cfgRows; }
    return cfgRows.filter(function (r) {
        return cfgRowText(r).toLowerCase().indexOf(k) >= 0;
    });
}

function renderCards(rows) {
    var html = "";
    rows.forEach(function (r) {
        var on = r.sync_enable === "1" ? "<span class='tag bound'>启用</span>" : "<span class='tag off'>停用</span>";
        var arg = rowArg(r);
        html += "<div class='cfg-item' title='点击进入该租户（默认“人员绑定”）' onclick='enterTenant(" + esc(r.id) + ")'>"
            + "<div class='cfg-item-head'><span class='cfg-item-name'>" + esc(r.corp_name) + "</span>" + on + "</div>"
            + "<div class='cfg-item-sub'>ID #" + esc(r.id) + " · 更新时间：" + esc(Oaones.fmtTime(r.update_time)) + "</div>"
            + "<div class='cfg-item-row'><span><%= oaonesPBIdColLabel %></span><b>" + esc(r.corp_id) + "</b></div>"
            + (HAS_AGENT ? ("<div class='cfg-item-row'><span><%= oaonesPBAppIdLabel %></span><b>"
                + esc(r.agent_id || "-") + "</b></div>") : "")
            + "<div class='cfg-item-row'><span>根部门ID</span><b>" + esc(r.root_dept_id) + "</b></div>"
            + "<div class='cfg-item-row'><span>维护人</span><b>" + maintainerCellHtml(r) + "</b></div>"
            + "<div class='cfg-item-row'><span>备注</span><b>" + esc(r.remark || "-") + "</b></div>"
            + "<div class='cfg-item-foot'>"
            + "<button class='link' onclick='event.stopPropagation();enterTenant(" + esc(r.id) + ")'>进入</button>"
            + "<button class='link' onclick='event.stopPropagation();openEdit(" + arg + ")'>编辑</button>"
            + "<button class='link' onclick='event.stopPropagation();testCfg(" + esc(r.id) + ")'>连通测试</button>"
            + "<button class='link danger' onclick='event.stopPropagation();delCfg(" + esc(r.id) + ")'>删除</button>"
            + "</div></div>";
    });
    byId("cardView").innerHTML = html;
}

/* ---------------- 展示方式：块状（默认）/ 列表 ---------------- */

/** 列表方式的租户表格（一行一个租户；分页由 idxPager 控制） */
function renderList(rows) {
    var pageRows = rows;
    if (idxPager) {
        idxPager.resetIfChanged(String(idxKeyword));
        pageRows = idxPager.apply(rows);
    }
    var body = "";
    pageRows.forEach(function (r) {
        var on = r.sync_enable === "1" ? "<span class='tag bound'>启用</span>" : "<span class='tag off'>停用</span>";
        // 消息通知开关只读展示（改开关在独立菜单「消息设置 → 租户开关」里维护）
        var msgOn = String(r.msg_enable) === "0"
            ? "<span class='tag off'>已关闭</span>" : "<span class='tag bound'>已开启</span>";
        var arg = rowArg(r);
        body += "<tr>"
            + "<td>" + esc(r.id) + "</td>"
            + "<td>" + esc(r.corp_name) + "</td>"
            + "<td>" + esc(r.corp_id) + "</td>"
            + (HAS_AGENT ? ("<td>" + esc(r.agent_id || "-") + "</td>") : "")
            + "<td>" + esc(r.root_dept_id) + "</td>"
            + "<td>" + maintainerCellHtml(r) + "</td>"
            + "<td>" + on + "</td>"
            + "<td>" + msgOn + "</td>"
            + "<td>" + esc(Oaones.fmtTime(r.update_time)) + "</td>"
            + "<td>" + esc(r.remark || "-") + "</td>"
            + "<td>"
            + "<button class='link' onclick='enterTenant(" + esc(r.id) + ")'>进入</button>"
            + "<button class='link' onclick='openEdit(" + arg + ")'>编辑</button>"
            + "<button class='link' onclick='testCfg(" + esc(r.id) + ")'>连通测试</button>"
            + "<button class='link danger' onclick='delCfg(" + esc(r.id) + ")'>删除</button>"
            + "</td></tr>";
    });
    byId("dataBody").innerHTML = body;
    if (window.OaonesTable) {
        OaonesTable.resizable(byId("cfgTable"), { key: "wecomIndex.cfgTable" });
    }
    if (idxPager) { idxPager.show(); idxPager.render(); }
}

/**
 * 切换展示方式：块状（默认，展示全部命中行）/ 列表（表格 + 分页）。
 * 选择写进 localStorage（按菜单存），下次打开保持上次的样式。
 */
function setIdxView(mode, silent) {
    idxViewMode = mode === "list" ? "list" : "card";
    byId("listView").style.display = idxViewMode === "list" ? "" : "none";
    byId("cardView").style.display = idxViewMode === "card" ? "" : "none";
    byId("viewCardBtn").classList.toggle("active", idxViewMode === "card");
    byId("viewListBtn").classList.toggle("active", idxViewMode === "list");
    if (idxViewMode === "card" && idxPager) { idxPager.hide(); }
    if (!silent) {
        try { localStorage.setItem("oaones.wecomIndex.view", idxViewMode); } catch (e) { /* 隐私模式忽略 */ }
    }
    // 两种方式共用同一份筛选结果：切换后按当前关键字重画一次
    if (!silent) { doIdxSearch(); }
}

function doIdxSearch() {
    // 以输入框当前值为准（含维护人姓名），保证“搜索/回车”真正生效
    idxKeyword = byId("idxKeyword").value || "";
    var rows = filteredCfgRows();
    // 块状展示全部命中行；列表展示当前页（分页条只在列表方式下出现）
    renderCards(rows);
    renderList(rows);
    var emptyEl = byId("emptyTip");
    emptyEl.style.display = rows.length ? "none" : "block";
    if (!rows.length) {
        // cfgRows 为空说明当前登录人没有任何可查看的租户：提示语按角色给（超管=还没建租户；其他=不是任何租户的维护人）
        emptyEl.textContent = cfgRows.length > 0 ? "没有匹配的租户配置" : OaonesTips.pick(
            "暂无租户配置：请点击右上角“新增" + P_SHORT + "租户”创建第一个" + P_SHORT + "租户。",
            "没有可查看的租户：本页只列出你被指定为维护人的租户，请联系管理员把你加入对应租户的维护人，"
                + "或点击右上角“新增" + P_SHORT + "租户”创建自己的租户。");
    }
    renderStats(rows);
}

function loadData() {
    Oaones.api("cfg.list", {}, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        cfgRows = res.data || [];
        doIdxSearch();
    });
}

function openEdit(row) {
    byId("modalTitle").textContent = row ? ("编辑" + P_SHORT + "租户") : ("新增" + P_SHORT + "租户");
    byId("id").value = row ? (row.id || "") : "";
    byId("corp_name").value = row ? (row.corp_name || "") : "";
    byId("corp_id").value = row ? (row.corp_id || "") : "";
    // Secret 不回显真实值：编辑时只显示 ****（保存时不改则原样保留），新增时是空框
    OaonesSecret.reset(byId("corp_secret"), !!row);
    if (byId("agent_id")) { byId("agent_id").value = row ? (row.agent_id || "") : ""; }
    byId("root_dept_id").value = row ? (row.root_dept_id || P_RootDeptDefault) : P_RootDeptDefault;
    byId("sync_enable").value = row ? (row.sync_enable === "0" ? "0" : "1") : "1";
    byId("remark").value = row ? (row.remark || "") : "";
    setMaintainerSelection(row);
    byId("modalMask").style.display = "block";
    byId("editModal").style.display = "block";
}
function closeEdit() {
    byId("modalMask").style.display = "none";
    byId("editModal").style.display = "none";
}
/** 连通测试：只验证“能通即可”，结果只报连通与否（不展示部门数/成员数/token）。
 *  测试期间用加载框挡住页面，避免重复点击（快照请到“部门绑定 / 人员绑定”页更新）。 */
var testingCfg = false;
function testCfg(id) {
    if (testingCfg) { return; }
    testingCfg = true;
    OaonesToast.loading("连通测试中，请不要关闭页面");
    Oaones.api("cfg.test", { id: id }, function (res) {
        testingCfg = false;
        OaonesToast.hideLoading();
        if (res.code !== 0) { showToast("测试失败: " + res.msg, 2); return; }
        showToast("连通成功：" + P_SHORT + "租户“" + (res.data.corp_name || ("#" + id)) + "”可以连通", 0, 5000);
    });
}
function delCfg(id) {
    OaonesToast.confirm("确认删除该" + P_SHORT + "租户配置？存在绑定关系时不允许删除。", function () {
        Oaones.api("cfg.delete", { id: id }, function (res) {
            if (res.code === 0) { loadData(); } else { showToast(res.msg, 2); }
        });
    }, { danger: true });
}
byId("saveBtn").addEventListener("click", function () {
    if (!mtSelected.length) {
        // 清空维护人是危险操作，确认文案按角色：超管=仅自己可见；其他登录人=自己也会失去该租户
        OaonesToast.confirm(OaonesTips.pick(
            "未设置维护人时，该租户仅系统管理员（user.id=1）可查看与维护，确认保存？",
            "未设置维护人时，你和其他维护人都将失去该租户的查看与维护权限，确认保存？"),
            saveCfg, { mode: 1 });
        return;
    }
    saveCfg();
});
function saveCfg() {
    Oaones.api("cfg.save", {
        id: byId("id").value,
        corp_name: byId("corp_name").value,
        corp_id: byId("corp_id").value,
        corp_secret: OaonesSecret.value(byId("corp_secret")),
        agent_id: byId("agent_id") ? byId("agent_id").value : "",
        root_dept_id: byId("root_dept_id").value,
        sync_enable: byId("sync_enable").value,
        remark: byId("remark").value,
        maintainer_ids: mtSelected.map(function (m) { return m.id; }).join(",")
    }, function (res) {
        if (res.code === 0) {
            showToast("保存成功");
            closeEdit();
            loadData();
        } else {
            showToast(res.msg, 2);
        }
    });
}

/** 从“租户详情 → 编辑租户配置”进来时带 ?edit=<id>，加载完成后自动打开该租户的编辑弹框。 */
function editIdFromUrl() {
    var m = /[?&]edit=(\d+)/.exec(location.search || "");
    return m ? m[1] : "";
}

function openEditById(id) {
    var key = String(id == null ? "" : id);
    if (!key) { return; }
    for (var i = 0; i < cfgRows.length; i++) {
        if (String(cfgRows[i].id) === key) { openEdit(cfgRows[i]); return; }
    }
    showToast("租户不存在或你无权查看该租户", 1);
}

byId("idxKeyword").addEventListener("keydown", function (e) {
    if (e.key === "Enter") { doIdxSearch(); }
});
byId("idxSearchBtn").addEventListener("click", doIdxSearch);
byId("idxResetBtn").addEventListener("click", function () {
    idxKeyword = "";
    byId("idxKeyword").value = "";
    doIdxSearch();
});
byId("refreshBtn").addEventListener("click", loadData);
byId("mtBox").addEventListener("click", openMtPick);
byId("mtChips").addEventListener("click", onMtChipClick);
byId("mtPickSearch").addEventListener("input", renderMtPickList);
byId("mtPickList").addEventListener("change", onMtPickListChange);
byId("mtPickMask").addEventListener("click", closeMtPick);
// 双击某行：选中该行后立即确定并退出弹框
byId("mtPickList").addEventListener("dblclick", function (e) {
    var row = e.target && e.target.closest ? e.target.closest(".mt-pick-row") : null;
    if (!row) { return; }
    var box = row.querySelector(".mt-pick-box");
    if (!box) { return; }
    mtPickTemp[String(box.getAttribute("data-id"))] = true;
    confirmMtPick();
});
OaonesPicker.register("mtPick", renderMtPickList);
OaonesPicker.syncSwitches();
renderMtChips();
// 密钥框：点进掩码时让出掩码、切回密码态；没输入就离开则恢复 ****
OaonesSecret.bind(byId("corp_secret"));

/* 列表样式（可切换）：分页器与筛选条件是同一份数据，切换样式不改变当前页 */
if (window.OaonesTable) {
    idxPager = OaonesTable.pager({
        key: "wecomIndex.list",
        el: byId("idxPager"),
        onChange: function () { doIdxSearch(); }
    });
}
/* 展示方式：默认块状；上次选过列表就用列表（记在浏览器本地） */
var idxSavedView = "card";
try { idxSavedView = localStorage.getItem("oaones.wecomIndex.view") || "card"; } catch (e) { idxSavedView = "card"; }
setIdxView(idxSavedView, true);

/** 加载后按地址里的 ?edit=<id> 自动打开编辑弹框（首页即租户维护入口）。 */
function loadDataAndMaybeEdit() {
    var editId = editIdFromUrl();
    Oaones.api("cfg.list", {}, function (res) {
        if (res.code !== 0) { showToast(res.msg, 2); return; }
        cfgRows = res.data || [];
        doIdxSearch();
        if (editId) { openEditById(editId); }
    });
}
loadDataAndMaybeEdit();
</script>
</body>
</html>
