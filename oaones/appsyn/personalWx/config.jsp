<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.PersonalWxServiceImpl" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 微信设置（独立一级菜单：/oaones/appsyn/personalWx/config.jsp）
     *
     * 与「企微租户设置 / 钉钉租户设置 / 消息设置 / 登录设置 / 绑定总览」并列的另一条独立菜单：
     *  ① 上半部分：维护**微信授权密钥**（AppID + AppSecret）——个人微信扫码绑定换 openid 用它；
     *  ② 下半部分：**个人微信绑定列表**（一行一个 OA 有效账号，**含系统管理员账号、不含子账户**）：
     *     管理员可在这里解绑，也可以选中某个 OA 人员扫码为其绑定一个微信号。
     *
     * 子账户口径：E9 标准 hrmresource.accounttype = 1 的**子账户（次账号）不参与个人微信**——
     * 不出现在绑定列表里（PersonalWxMapper#listOaUsers 过滤），因此也不计入列表的
     * “已绑定 / 未绑定 / OA 有效账号合计”统计；同时不允许绑定（assertBindableOaUser 拦截，
     * 本人自助与管理员代绑都会在服务端被拒），提示改用该人员的主账户绑定。
     *
     * 个人微信与企微 / 钉钉 / 飞书不同：它没有“企业 / 租户”概念，一套部署只有一份授权密钥，
     * 绑定关系按 OA 人员一对一存一条（表 uf_oaones_appsyn_personal_wx_config / uf_oaones_appsyn_personal_wx_bind），
     * 因此本页没有任何“租户”选择框。
     *
     * 绑定列表的筛选口径（见 skill.md 第一节第 23 条）：
     *  · **绑定状态**是“已绑定 / 未绑定”两个**按钮式多选**（不是下拉）：全不选与全选效果一致（= 不筛选）；
     *  · **人员**不铺在页面上，只留一个“选择人员”入口：点击弹出**人员搜索弹框**，
     *    弹框内可按**分部 / 部门 / 人员关键字**搜索（分部 / 部门是弹框里的多选选择框，
     *    与人员绑定页口径一致：列表式 / 树状、全选、选中自动含全部下级）。
     *
     * 页面地址条件（showSetting）：
     *   `/oaones/appsyn/personalWx/config.jsp?showSetting=true` —— 显示“微信设置”这一部分
     *   （微信授权密钥 AppID / AppSecret / 备注 + 保存），再往下是个人微信绑定列表；
     *   不带该参数时**只显示下方的个人微信绑定列表**，密钥部分整块不渲染。
     *   对应的接口口径：`pw.config` / `pw.save` 只有在请求带 showSetting=true / show_setting=1 时
     *   才下发 AppID / 密钥掩码 / 备注 / 修改人等**设置参数**，否则只回“密钥是否已配置”，
     *   避免“列表页”顺手把设置参数取走（见 PersonalWxService#getConfig）。
     *
     * 权限：系统管理员（user.id=1）与 oaones.appsyn.properties#pw.admin.ids 中列出的人员；
     * 接口层（pw.config / pw.save / pw.list，以及带 oa_user_id 的 pw.qrStart / pw.unbind）会再判一次。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    boolean oaonesPwCanMaintain = new PersonalWxServiceImpl().canMaintain(String.valueOf(user.getUID()));
    // ?showSetting=true 才显示“微信设置”（微信授权密钥）这一部分；否则只显示下方的绑定列表
    String oaonesPwShowSettingParam = request.getParameter("showSetting");
    if (oaonesPwShowSettingParam == null) {
        oaonesPwShowSettingParam = "";
    }
    oaonesPwShowSettingParam = oaonesPwShowSettingParam.trim();
    boolean oaonesPwShowSetting = "true".equalsIgnoreCase(oaonesPwShowSettingParam)
            || "1".equals(oaonesPwShowSettingParam);
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>微信设置 - OAones</title>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/picker.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <%-- 密码 / 密钥框的掩码口径：编辑时只显示 **** --%>
    <script src="/oaones/appsyn/js/secret.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/hrmlink.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/table.js?v=<%=oaonesAssetV%>"></script>
</head>
<body>
<%-- 一级标题只属于本菜单（“微信设置”）：不链到租户设置 / 消息设置 / 登录设置 --%>
<div class="navbar">
    <h1>微信设置</h1>
    <div class="nav">
        <a class="active" href="/oaones/appsyn/personalWx/config.jsp">微信设置</a>
    </div>
</div>
<div class="page">
    <%-- ① 微信授权密钥（AppID + AppSecret，全局一份）：
         只在地址带 ?showSetting=true 时渲染；不带时整块不输出（接口也不下发密钥相关参数） --%>
<% if (oaonesPwShowSetting) { %>
    <div class="card">
        <div class="toolbar" style="justify-content:space-between;">
            <div class="toolbar-left">
                <h3 style="margin:2px 0;">微信授权密钥</h3>
            </div>
            <div>
                <button type="button" class="btn" id="pwSaveBtn">保存密钥</button>
            </div>
        </div>
        <form id="pwConfigForm" onsubmit="return false;">
            <table class="grid" style="max-width:900px;">
                <thead>
                <tr>
                    <th style="width:22%;">配置项</th>
                    <th style="width:38%;">值</th>
                    <th>说明</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td><b>AppID</b></td>
                    <td>
                        <input type="text" id="pwAppId" maxlength="64" autocomplete="off" data-lpignore="true"
                               style="width:100%;" placeholder="微信开放平台网站应用的 AppID，例如 wx1234567890abcdef">
                    </td>
                    <td>个人微信扫码授权用；填错会导致扫码后微信提示“redirect_uri 参数错误”或拿不到 openid。</td>
                </tr>
                <tr>
                    <td><b>AppSecret</b></td>
                    <td>
                        <input type="password" id="pwAppSecret" name="oaonesPwAppSecret" maxlength="128"
                               autocomplete="new-password" data-lpignore="true"
                               style="width:100%;" placeholder="首次配置必填；已配置后显示 ****，不动即不修改">
                    </td>
                    <td>用于把扫码换回的 code 换成 openid / unionid。保存后不再回显，只显示掩码
                        （当前：<span class="mono" id="pwSecretMasked">未配置</span>）。</td>
                </tr>
                <tr>
                    <td><b>备注</b></td>
                    <td colspan="2">
                        <input type="text" id="pwRemark" maxlength="200" autocomplete="off" data-lpignore="true"
                               style="width:100%;" placeholder="例如：2026-09 起启用个人微信绑定">
                    </td>
                </tr>
                </tbody>
            </table>
            <p class="muted" style="margin-top:10px;">最近修改：<span id="pwUpdateInfo">-</span></p>
            <p class="notice" id="pwConfigTip" style="display:none;"></p>
        </form>
    </div>
<% } %>

    <%-- ② 个人微信绑定列表（一行一个 OA 有效账号，含系统管理员账号、不含子账户）：
         这一部分**始终显示**（列表页就是这个页面的主体） --%>
    <div class="card" style="margin-top:14px;">
        <div class="toolbar">
            <label>个人微信绑定列表
                <span class="help-tip inline" data-help="pwHelpText" role="button" tabindex="0"
                      title="查看页面说明" aria-label="查看页面说明">?</span>
            </label>
            <span style="flex:1;"></span>
            <button type="button" class="btn secondary" id="pwRefreshBtn">刷新</button>
        </div>
        <div class="help-source" id="pwHelpText">
            <% if (oaonesPwShowSetting) { %>
            <p>本页是<b>独立的一级菜单「微信设置」</b>，当前显示<b>两部分</b>：
                先维护<b>微信授权密钥</b>，再是下方的<b>个人微信绑定列表</b>
                （地址上带了 <span class="mono">?showSetting=true</span>）。</p>
            <% } else { %>
            <p>本页是<b>独立的一级菜单「微信设置」</b>，当前<b>只显示个人微信绑定列表</b>
                （地址上没有带 <span class="mono">?showSetting=true</span>）。</p>
            <% } %>
            <p><b>个人微信</b>是与企业微信 / 钉钉 / 飞书并列的另一个平台，区别是它
                <b>没有“企业 / 租户”概念</b>：整套系统只维护<b>一份</b>微信授权密钥
                （AppID + AppSecret），员工的绑定关系按 OA 人员<b>一对一</b>存一条。</p>
            <% if (oaonesPwShowSetting) { %>
            <p><b>微信授权密钥从哪来</b>：到微信开放平台创建“网站应用”后拿到
                <span class="mono">AppID</span> 与 <span class="mono">AppSecret</span>，
                并把本 OA 的对外域名登记为该应用的“授权回调域”。
                扫码授权页面（<span class="mono">open.weixin.qq.com</span>）与接口域名
                （<span class="mono">api.weixin.qq.com</span>）在本模块配置里，换网关只改配置、不用改代码。</p>
            <p><b>AppSecret 只写不读</b>：保存后页面上只显示掩码，不再回显完整密钥；
                再次保存时该框留空表示<b>保持原密钥不变</b>，只有填了新值才覆盖。</p>
            <% } else { %>
            <p><b>微信授权密钥</b>（AppID / AppSecret）是扫码绑定必需的全局配置，属于「微信设置」的
                <b>设置部分</b>：需要查看 / 修改时，在地址后加上
                <span class="mono">?showSetting=true</span> 打开设置视图（仅维护人可见）。
                本页（不带该条件时）只展示绑定列表，<b>接口也不会下发密钥相关参数</b>。</p>
            <% } %>
            <p><b>绑定列表</b>一行一个 OA 有效账号（<b>含系统管理员账号、不含子账户</b>）：
                管理员可以“解绑”，也可以点“绑定微信 / 换绑”让该员工用本人微信扫码——
                一个微信号只能绑定一个 OA 账号，一个 OA 账号也只保留一条绑定，重复扫码即换绑。
                员工自己扫码的入口在<b>个人账号中心 → 个人微信</b>页签（本人自助，不需要管理员代劳）。</p>
            <p><b>子账户不参与个人微信</b>：E9 标准 <span class="mono">hrmresource.accounttype</span>
                为 <span class="mono">1</span> 的<b>子账户（次账号）</b>不显示在这个列表里，
                也<b>不计入</b>下方的“已绑定 / 未绑定 / OA 有效账号合计”统计；
                子账户<b>不能绑定</b>个人微信（本人自助扫码与管理员代绑都会被服务端拒绝），
                请改用该人员的主账户绑定。</p>
            <p><b>怎么筛选</b>：<b>绑定状态</b>是“已绑定 / 未绑定”两个<b>按钮</b>，可多选，
                <b>全不选与全选效果一致</b>（都表示不按绑定状态筛选）；
                <b>OA 人员筛选</b>与企微“人员绑定”页一致，是<b>三个并列的搜索框，依次为
                <span class="mono">分部 / 部门 / 人员</span></b>：点开任一框都在<b>弹框里搜索</b>
                （与企微绑定页同一套弹框组件，不是下拉、也不是自造的输入联想）——
                分部、部门支持多选、列表式 / 树状、全选，<b>选中会自动带上其全部下级</b>，
                部门候选受已选分部（含下级）过滤；人员弹框按姓名 / 登录名 / 工号 / 手机号 / 人员ID 搜索，
                候选行标出所属部门与绑定状态（候选同样只含主账户，不含子账户）。
                三个条件可任意组合，各自的“×”单独清除。</p>
            <p>部署提示：建表脚本 <span class="mono">sql/public/oaones_tables.sql</span>，
                接口回调页 <span class="mono">/oaones/appsyn/personalWx/PersonalWxCallback.jsp</span>，
                相关配置项 <span class="mono">pw.*</span>（见 <span class="mono">oaones.appsyn.properties</span>）。</p>
            <% if (oaonesPwCanMaintain) { %>
            <p>你有维护权限：系统管理员可维护全部设置，<span class="mono">pw.admin.ids</span>
                中列出的人员也能维护本页。</p>
            <% } else { %>
            <p>你没有维护权限：仅系统管理员与 <span class="mono">pw.admin.ids</span>
                中列出的人员可维护本页。如需调整微信授权密钥或解绑，请联系系统管理员。</p>
            <% } %>
        </div>
        <div class="filter-bar">
            <span class="filter-label">绑定状态：</span>
            <%-- 按钮式多选（不是下拉）：全不选与全选效果一致 = 不按绑定状态筛选 --%>
            <div class="wx-state-filters" id="pwStateFilters">
                <label class="wx-state-filter checked"><input type="checkbox" id="pwStateBound" value="bound" checked>已绑定</label>
                <label class="wx-state-filter checked"><input type="checkbox" id="pwStateUnbound" value="unbound" checked>未绑定</label>
            </div>
            <span class="filter-label">OA 人员筛选：</span>
            <%-- 与企微“人员绑定”页一致：三个并列的搜索条件框，依次是 分部 / 部门 / 人员，
                 每个都用弹框搜索组件（点击弹出选择弹框，不是下拉、也不是自造的输入联想） --%>
            <div class="filter-picker" id="pwSubPicker"
                 title="点击选择分部（可多选；选中分部会自动选中其全部下级分部）">
                <input type="text" id="pwSubInput" class="filter-input" readonly
                       placeholder="选择分部（可多选）">
                <span class="filter-arrow">▾</span>
                <button type="button" class="filter-clear" id="pwSubClear" title="清除分部条件">×</button>
            </div>
            <div class="filter-picker" id="pwDeptPicker"
                 title="点击选择部门（可多选；选中部门会自动选中其全部下级部门，候选受已选分部含下级过滤）">
                <input type="text" id="pwDeptInput" class="filter-input" readonly
                       placeholder="选择部门（可多选）">
                <span class="filter-arrow">▾</span>
                <button type="button" class="filter-clear" id="pwDeptClear" title="清除部门条件">×</button>
            </div>
            <div class="filter-picker" id="pwPersonPicker"
                 title="点击选择人员（弹框内可按姓名 / 登录名 / 工号 / 手机号 / 人员ID 搜索）">
                <input type="text" id="pwPersonInput" class="filter-input" readonly
                       placeholder="选择人员（可搜索）">
                <span class="filter-arrow">▾</span>
                <button type="button" class="filter-clear" id="pwPersonClear" title="清除人员条件">×</button>
            </div>
            <button type="button" class="btn small secondary" id="pwResetBtn">重置筛选</button>
            <span class="muted" id="pwFilterTip"></span>
        </div>
        <div class="bind-stats" id="pwStats"></div>
        <div class="table-wrap">
            <table class="grid" id="pwTable">
                <thead>
                <tr>
                    <th style="width:16%;">OA 人员</th>
                    <th style="width:11%;">登录名</th>
                    <th style="width:15%;">所属部门</th>
                    <th style="width:9%;">绑定状态</th>
                    <th style="width:14%;">微信昵称</th>
                    <th style="width:14%;">微信 openid</th>
                    <th style="width:9%;">绑定方式</th>
                    <th style="width:12%;">绑定时间</th>
                    <th>操作</th>
                </tr>
                </thead>
                <tbody id="pwBody"></tbody>
            </table>
            <div class="empty" id="pwEmpty" style="display:none;"></div>
        </div>
        <div class="pager" id="pwPager"></div>
    </div>
</div>

<%-- ============ 选择人员（只用弹框搜索：分部 / 部门 / 人员） ============ --%>
<div class="modal-mask" id="pwPersonMask"></div>
<div class="modal wx-pick-modal" id="pwPersonModal">
    <div class="modal-head">
        <span>选择人员</span>
        <button type="button" class="link" id="pwPersonCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="wx-pick-tip" id="pwPersonTip"></div>
        <div class="pick-view-bar">
            <input type="text" id="pwPersonKeyword" placeholder="输入姓名/登录名/工号/手机号/人员ID搜索" autocomplete="off">
            <button type="button" class="btn small secondary pick-all-btn" id="pwPersonAllBtn" data-pick-all="pwPerson"
                    title="全选/全不选当前候选人员（受搜索关键字影响）">全选</button>
        </div>
        <div class="wx-pick-list" id="pwPersonList"></div>
        <div class="muted wx-pick-note">候选为全部 OA 有效账号（<b>主账户</b>，含系统管理员账号；不含子账户）：
            单击选中 / 取消，双击直接确定；
            不勾选人员即不按人员筛选（与全选效果一致）。已绑定行会标出“已绑定”，
            绑定 / 换绑 / 解绑仍在列表行内操作。</div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="pwPersonClearSelBtn">清空选择</button>
        <button type="button" class="btn secondary" id="pwPersonCancelBtn">取消</button>
        <button type="button" class="btn" id="pwPersonOkBtn">确定</button>
    </div>
</div>

<%-- 分部 / 部门选择弹框（页面上“分部 / 部门”两个选择框共用这一份标记，标题与候选随类型切换） --%>
<div class="modal-mask" id="pwOrgMask"></div>
<div class="modal wx-pick-modal" id="pwOrgModal">
    <div class="modal-head">
        <span id="pwOrgTitle">选择分部</span>
        <button type="button" class="link" id="pwOrgCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <div class="pick-view-bar">
            <input type="text" id="pwOrgSearch" placeholder="输入名称/编码/ID搜索" autocomplete="off">
            <div class="view-switch mini" id="pwOrgViewSwitch" data-view-key="pwOrg">
                <button type="button" class="view-btn" data-view="list">列表式</button>
                <button type="button" class="view-btn active" data-view="tree">树状</button>
            </div>
            <button type="button" class="btn small secondary pick-all-btn" id="pwOrgAllBtn" data-pick-all="pwOrg"
                    title="全选/全不选当前列表（受搜索与前置条件过滤影响）">全选</button>
        </div>
        <div class="wx-pick-list" id="pwOrgList"></div>
        <div class="muted wx-pick-note" id="pwOrgNote"></div>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="pwOrgClearBtn">清空</button>
        <button type="button" class="btn secondary" id="pwOrgCancelBtn">取消</button>
        <button type="button" class="btn" id="pwOrgOkBtn">确定</button>
    </div>
</div>

<%-- 扫码绑定弹框：内嵌微信授权二维码（自己生成 state，不是平台托管的二维码页） --%>
<div class="modal-mask" id="pwQrMask"></div>
<div class="modal wx-qr-modal" id="pwQrModal">
    <div class="modal-head">
        <span id="pwQrTitle">微信扫码绑定</span>
        <button type="button" class="link" id="pwQrCloseBtn" title="关闭">×</button>
    </div>
    <div class="modal-body">
        <p class="wx-qr-tip">请使用<span class="rule">本人的个人微信</span>扫描下方二维码并在手机上确认：
            确认后会把扫码得到的微信号绑定到<span class="rule" id="pwQrTarget">该 OA 账号</span>。
            一个 OA 账号只保留一条绑定，重复扫码即<b>换绑</b>；一个微信号也只能绑定一个 OA 账号。</p>
        <div class="wx-qr-box" id="pwQrBox"></div>
        <p class="wx-qr-status" id="pwQrStatus"></p>
    </div>
    <div class="modal-foot">
        <button type="button" class="btn secondary" id="pwQrWindowBtn"
                title="浏览器拦截内嵌二维码（第三方 Cookie）时改用新窗口扫码">在新窗口打开二维码</button>
        <button type="button" class="btn secondary" id="pwQrAgainBtn">重新生成二维码</button>
        <button type="button" class="btn" id="pwQrDoneBtn">我知道了</button>
    </div>
</div>

<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
(function () {
    var CAN_MAINTAIN = <%=oaonesPwCanMaintain%>;
    /**
     * 地址上是否带 ?showSetting=true：只有带了才显示“微信设置”（微信授权密钥）这一部分。
     * 不带时页面**不请求**设置参数，接口（pw.config）也只回“密钥是否已配置”，
     * 不下发 AppID / 密钥掩码 / 备注等设置参数。
     */
    var SHOW_SETTING = <%=oaonesPwShowSetting%>;
    /** 授权密钥是否已配置（AppID + AppSecret 都有值）：未配置时禁止发起扫码绑定 */
    var configured = false;
    /** 绑定列表全量行（pw.list：一行一个 OA 有效账号） */
    var rows = [];
    /** OA 分部 / 部门候选（pw.orgOptions：与人员绑定页同一份组织缓存口径） */
    var oaSubOptions = [];
    var oaDeptOptions = [];
    /**
     * 页面筛选栏里三个并列搜索框（分部 / 部门 / 人员）对应的筛选条件：
     * subIds / deptIds 在 applyOrgPick 里已展开成“含全部下级”，personIds 是明确勾选的人员。
     * 任一项为空即不按该项过滤（人员条件为空同样等价于“全选”）。
     */
    var filter = { subIds: [], deptIds: [], personIds: [] };
    /** 人员弹框内的临时选择（点“确定”才写回 filter.personIds，取消 / 关闭则丢弃） */
    var personPickIds = [];
    /** 当前打开的分部 / 部门选择弹框类型：fb | dept */
    var orgType = "";
    var orgSelected = [];
    /** 当前二维码弹框要绑定的 OA 人员ID（空 = 没开弹框） */
    var qrOaUserId = "";
    /** 二维码请求中标记：避免连点重复生成 */
    var qrLoading = false;

    function byId(id) { return document.getElementById(id); }
    function esc(v) { return Oaones.esc(v); }
    function showToast(msg, mode, duration) {
        if (msg === null || msg === undefined || String(msg) === "") { return; }
        OaonesToast.show(msg, { mode: mode, duration: duration });
    }
    function cellText(value) {
        var text = String(value == null ? "" : value);
        return text.length ? esc(text) : '<span class="muted">—</span>';
    }
    function s(v) { return String(v == null ? "" : v); }
    function findRow(oaUserId) {
        var key = s(oaUserId);
        for (var i = 0; i < rows.length; i++) {
            if (s(rows[i].oa_user_id) === key) { return rows[i]; }
        }
        return null;
    }

    /* ------------------------------------------------------------------
     * ① 微信授权密钥
     * ------------------------------------------------------------------ */
    function renderConfig(data) {
        data = data || {};
        configured = data.configured === true;
        if (!SHOW_SETTING) { return; }
        byId("pwAppId").value = data.app_id || "";
        // 密钥不回显：已配置时只显示 ****（不动它保存 = 保持原密钥不变），未配置时是空框
        OaonesSecret.reset(byId("pwAppSecret"), data.app_secret_set === true);
        byId("pwSecretMasked").textContent = data.app_secret_set
            ? (data.app_secret_masked || "已配置") : "未配置";
        byId("pwRemark").value = data.remark || "";
        byId("pwUpdateInfo").textContent = (data.update_time || "-")
            + (data.update_by ? "（人员ID " + data.update_by + "）" : "");
        var tip = byId("pwConfigTip");
        if (!configured) {
            tip.textContent = "尚未维护微信授权密钥（AppID / AppSecret）：个人微信扫码绑定当前不可用，"
                + "请先在微信开放平台创建“网站应用”并在这里填入 AppID 与 AppSecret。";
            tip.style.display = "";
        } else {
            tip.style.display = "none";
        }
    }

    function loadConfig() {
        // 不带 showSetting=true（只显示绑定列表）时不请求设置参数；接口侧同样不下发
        if (!SHOW_SETTING) { return; }
        if (!CAN_MAINTAIN) { disableConfig(); return; }
        Oaones.api("pw.config", { show_setting: "1" }, function (res) {
            if (res.code !== 0) {
                showToast(res.msg || "读取微信授权密钥失败", 2, 0);
                return;
            }
            renderConfig(res.data || {});
        });
    }

    /** 非维护人：字段只读并禁用保存（接口也会拒绝），避免看到一份“全是空”的假象 */
    function disableConfig() {
        if (!SHOW_SETTING) { return; }
        ["pwAppId", "pwAppSecret", "pwRemark"].forEach(function (id) { byId(id).disabled = true; });
        byId("pwSaveBtn").disabled = true;
        byId("pwSecretMasked").textContent = "无维护权限";
        byId("pwUpdateInfo").textContent = "无维护权限，不展示当前设置";
    }

    function saveConfig() {
        if (!SHOW_SETTING) { return; }
        if (!CAN_MAINTAIN) {
            showToast("你不是「微信设置」的维护人，无权保存", 1);
            return;
        }
        var appId = s(byId("pwAppId").value).replace(/^\s+|\s+$/g, "");
        if (!appId) {
            showToast("微信 AppID 不能为空（微信开放平台网站应用的 AppID）", 1);
            return;
        }
        var btn = byId("pwSaveBtn");
        btn.disabled = true;
        Oaones.api("pw.save", {
            show_setting: "1",
            app_id: appId,
            app_secret: OaonesSecret.value(byId("pwAppSecret")),
            remark: byId("pwRemark").value
        }, function (res) {
            btn.disabled = false;
            if (res.code !== 0) {
                showToast(res.msg || "保存失败", 2, 0);
                return;
            }
            showToast("已保存微信授权密钥", 0);
            renderConfig(res.data || {});
        });
    }

    /* ------------------------------------------------------------------
     * ② 绑定列表：筛选条件（状态按钮多选 + 弹框选人）与列表渲染
     * ------------------------------------------------------------------ */
    var pager = OaonesTable.pager({
        key: "personalWx.config",
        el: byId("pwPager"),
        onChange: function () { renderRows(); }
    });

    function loadList() {
        if (!CAN_MAINTAIN) { rows = []; renderRows(); return; }
        // 列表一次取全（含未绑定行）：状态按钮与“选择人员”的分部/部门/人员条件都在前端过滤，
        // 这样弹框里的候选人员可以即时响应条件变化，不用每次筛选都跑一趟服务端。
        Oaones.api("pw.list", {}, function (res) {
            if (res.code !== 0) {
                showToast(res.msg || "读取个人微信绑定列表失败", 2, 0);
                rows = [];
                renderRows();
                return;
            }
            var data = res.data || {};
            rows = data.items || [];
            configured = data.configured === true;
            renderRows();
        });
    }

    /** 组织候选（分部 / 部门）：pw.orgOptions，与人员绑定页 user.orgOptions 同口径 */
    function loadOrgOptions() {
        if (!CAN_MAINTAIN) { return; }
        Oaones.api("pw.orgOptions", {}, function (res) {
            if (res.code !== 0) {
                oaSubOptions = [];
                oaDeptOptions = [];
                return;
            }
            var data = res.data || {};
            oaSubOptions = data.oa_subcompanies || [];
            oaDeptOptions = data.oa_departments || [];
        });
    }

    /** 绑定状态按钮多选：全勾（全选）与全不勾（不选）效果一致 = 不按绑定状态筛选 */
    function stateFilter() {
        var bound = byId("pwStateBound").checked;
        var unbound = byId("pwStateUnbound").checked;
        if (bound === unbound) { return ""; }
        return bound ? "1" : "0";
    }

    function syncStateFilterLabels() {
        ["pwStateBound", "pwStateUnbound"].forEach(function (id) {
            var box = byId(id);
            var label = box.parentNode;
            if (label && label.classList) { label.classList.toggle("checked", box.checked); }
        });
    }

    /** 当前筛选条件下可见的行（绑定状态 + 筛选栏三个搜索框：分部 / 部门 / 人员） */
    function visibleRows() {
        var st = stateFilter();
        var subIds = filter.subIds || [];
        var deptIds = filter.deptIds || [];
        var personIds = filter.personIds || [];
        return rows.filter(function (r) {
            if (st === "1" && r.bound !== true) { return false; }
            if (st === "0" && r.bound === true) { return false; }
            if (subIds.length && subIds.indexOf(s(r.oa_sub_id)) < 0) { return false; }
            if (deptIds.length && deptIds.indexOf(s(r.oa_dept_id)) < 0) { return false; }
            if (personIds.length && personIds.indexOf(s(r.oa_user_id)) < 0) { return false; }
            return true;
        });
    }

    function renderRows() {
        var visible = visibleRows();
        pager.resetIfChanged([stateFilter(), filter.subIds.join(","),
            filter.deptIds.join(","), filter.personIds.join(","), rows.length].join("|"));
        var boundCount = 0;
        var unboundCount = 0;
        visible.forEach(function (r) { if (r.bound === true) { boundCount++; } else { unboundCount++; } });
        byId("pwStats").innerHTML = '<span class="bind-stat">当前筛选 <b>' + visible.length
            + '</b> 行</span><span class="bind-stat">已绑定 <b>' + boundCount
            + '</b> 人</span><span class="bind-stat">未绑定 <b>' + unboundCount
            + '</b> 人</span><span class="bind-stat">OA 有效账号合计（不含子账户）<b>'
            + rows.length + '</b> 人</span>';

        var pageRows = pager.apply(visible);
        var html = "";
        pageRows.forEach(function (row) { html += rowHtml(row); });
        byId("pwBody").innerHTML = html;
        renderEmpty(visible);
        pager.render();
        OaonesTable.resizable(byId("pwTable"), { key: "personalWx.config", min: 60 });
    }

    function renderEmpty(visible) {
        var el = byId("pwEmpty");
        if (visible.length > 0) {
            el.style.display = "none";
            el.textContent = "";
            return;
        }
        if (!CAN_MAINTAIN) {
            el.textContent = "你没有「微信设置」的维护权限，看不到个人微信绑定列表；"
                + "如需查看本人在个人微信的绑定，请到“个人账号中心 → 个人微信”。";
        } else if (rows.length === 0) {
            el.textContent = "没有可展示的 OA 有效账号（列表只含主账户、不含子账户；"
                + "也可能还没刷新或组织数据为空）。";
        } else {
            el.textContent = "当前筛选条件下没有 OA 人员：可把“已绑定 / 未绑定”都勾上（或都不勾），"
                + "或用“选择人员”里的“清空条件”后重试。";
        }
        el.style.display = "";
    }

    function rowHtml(row) {
        var oaUserId = esc(row.oa_user_id);
        var bound = row.bound === true;
        var status = bound
            ? '<span class="tag bound">已绑定</span>'
            : '<span class="tag unbound">未绑定</span>';
        var avatar = bound && row.headimgurl
            ? '<img class="pw-wx-avatar" alt="微信头像" src="' + esc(row.headimgurl) + '">' : "";
        var nick = bound
            ? (row.nickname ? esc(row.nickname) : '<span class="muted">未取到昵称</span>')
            : '<span class="muted">—</span>';
        var openid = bound
            ? esc(row.openid_masked || "") + '<div class="user-mini" title="' + esc(row.openid)
                + '">ID ' + esc(row.openid) + '</div>'
            : '<span class="muted">—</span>';
        var source = bound
            ? '<span class="tag ' + (String(row.bind_source) === "1" ? "system"
                : (String(row.bind_source) === "2" ? "self" : "manual")) + '">'
                + esc(row.bind_source_text || "") + '</span>'
                + (row.remark ? '<div class="user-mini" title="备注">' + esc(row.remark) + '</div>' : "")
            : '<span class="muted">—</span>';
        var bindTime = bound ? esc(row.bind_time || "-") : '<span class="muted">—</span>';

        var ops = "";
        if (bound) {
            ops += '<button type="button" class="btn small secondary" data-bind="' + oaUserId
                + '" title="换绑：用新的微信号扫码，覆盖该 OA 账号当前的绑定">换绑</button> ';
            ops += '<button type="button" class="btn small ghost-danger" data-unbind="' + oaUserId
                + '" title="解除该 OA 账号的个人微信绑定">解绑</button>';
        } else {
            ops += '<button type="button" class="btn small" data-bind="' + oaUserId
                + '" title="让该员工用本人微信扫码，把微信号绑定到他的 OA 账号">绑定微信</button>';
        }
        // 超管账号也在这个列表里（系统管理员同样可以绑定一个微信号）
        var adminTag = row.sys_admin === true ? ' <span class="tag acct-main">系统管理员</span>' : "";

        return '<tr>'
            + '<td><b>' + OaonesHrm.user(row.oa_user_id, row.name) + '</b>' + adminTag
            + '<div class="user-mini">ID ' + oaUserId + '</div></td>'
            + '<td>' + cellText(row.loginid) + '</td>'
            + '<td>' + (row.oa_dept_name
                ? OaonesHrm.dept(row.oa_dept_id, row.oa_dept_name)
                : '<span class="muted">未维护</span>') + '</td>'
            + '<td>' + status + '</td>'
            + '<td>' + avatar + nick + '</td>'
            + '<td>' + openid + '</td>'
            + '<td>' + source + '</td>'
            + '<td>' + bindTime + '</td>'
            + '<td><div class="row-ops">' + ops + '</div></td>'
            + '</tr>';
    }

    /* ------------------------------------------------------------------
     * ③ 「选择人员」弹框（筛选栏第 3 个搜索框用）
     *    筛选栏的分部 / 部门两个搜索框共用第 ④ 节的选择弹框；
     *    三者都是“点击搜索框 → 弹框里搜索 → 选中即落条件”，页面上不出现下拉与自造控件。
     * ------------------------------------------------------------------ */
    function openPersonPick() {
        if (!CAN_MAINTAIN) {
            showToast("你不是「微信设置」的维护人，无权选择人员", 1);
            return;
        }
        // 打开时回填已选人员，便于在原有条件上继续微调
        personPickIds = (filter.personIds || []).slice();
        byId("pwPersonKeyword").value = "";
        renderPersonList();
        byId("pwPersonMask").style.display = "block";
        byId("pwPersonModal").style.display = "block";
        byId("pwPersonKeyword").focus();
    }

    function hidePersonPick() {
        byId("pwPersonMask").style.display = "none";
        byId("pwPersonModal").style.display = "none";
    }

    /** 候选人员：全部 OA 有效账号按关键字过滤（姓名 / 登录名 / 工号 / 手机号 / 人员ID） */
    function personCandidates() {
        var k = s(byId("pwPersonKeyword").value).trim().toLowerCase();
        if (!k.length) { return rows.slice(); }
        return rows.filter(function (r) {
            var text = [r.name, r.loginid, r.workcode, r.mobile, r.oa_user_id].map(s).join(" ").toLowerCase();
            return text.indexOf(k) >= 0;
        });
    }

    function personOptionHtml(r) {
        var id = s(r.oa_user_id);
        var active = personPickIds.indexOf(id) >= 0;
        var tip = (active ? "点击取消选择" : "点击选择")
            + "；双击直接确定｜" + s(r.name)
            + (r.loginid ? "（登录名 " + s(r.loginid) + "）" : "")
            + (r.oa_dept_name ? "｜" + s(r.oa_dept_name) : "")
            + (r.bound === true ? "｜已绑定" : "｜未绑定");
        return "<button type='button' class='wx-pick-option pick-cols-person" + (active ? " active" : "")
            + "' data-id='" + esc(id) + "' title='" + esc(tip) + "'>"
            + "<span class='wx-pick-opt-name'>" + esc(s(r.name) || "-") + "</span>"
            + "<span>" + esc(s(r.loginid) || "-") + "</span>"
            + "<span class='wx-pick-opt-name'>" + esc(s(r.oa_dept_name) || "-") + "</span>"
            + "<span>" + (r.bound === true
                ? '<span class="tag bound">已绑定</span>' : '<span class="tag unbound">未绑定</span>') + "</span>"
            + "</button>";
    }

    /** 人员弹框的提示行 + “全选”按钮状态 */
    function renderPersonTip() {
        var list = personCandidates();
        var allOn = list.length > 0 && list.every(function (r) {
            return personPickIds.indexOf(s(r.oa_user_id)) >= 0;
        });
        OaonesPicker.syncSelectAll("pwPerson", allOn);
        byId("pwPersonTip").textContent = "候选 " + list.length + " 人"
            + (personPickIds.length ? "，已选 " + personPickIds.length + " 人" : "")
            + "：单击选中 / 取消，双击直接确定；不勾选人员即不按人员筛选。";
    }

    function renderPersonList() {
        var list = personCandidates();
        renderPersonTip();
        var el = byId("pwPersonList");
        el.className = "wx-pick-list";
        if (!list.length) {
            el.innerHTML = "<div class='wx-pick-empty'>没有匹配的 OA 人员（可清空关键字后重试）</div>";
            return;
        }
        var html = "<div class='wx-pick-head pick-cols-person'><span>姓名</span><span>登录名</span>"
            + "<span>所属部门</span><span>绑定状态</span></div>";
        list.forEach(function (r) { html += personOptionHtml(r); });
        el.innerHTML = html;
    }

    function togglePersonOption(btn) {
        var id = s(btn.getAttribute("data-id"));
        var idx = personPickIds.indexOf(id);
        if (idx >= 0) {
            personPickIds.splice(idx, 1);
        } else {
            personPickIds.push(id);
        }
        // 只就地刷新选中态，不重建列表：保证双击时命中的是同一个 DOM 节点
        btn.classList.toggle("active", personPickIds.indexOf(id) >= 0);
        renderPersonTip();
    }

    /** 全选 / 全不选当前候选人员（受关键字过滤影响） */
    OaonesPicker.setSelectAll("pwPerson", function () {
        var list = personCandidates();
        if (!list.length) { return false; }
        var allOn = list.every(function (r) { return personPickIds.indexOf(s(r.oa_user_id)) >= 0; });
        if (allOn) {
            var drop = {};
            list.forEach(function (r) { drop[s(r.oa_user_id)] = true; });
            personPickIds = personPickIds.filter(function (id) { return !drop[id]; });
        } else {
            var set = {};
            personPickIds.forEach(function (id) { set[id] = true; });
            list.forEach(function (r) { set[s(r.oa_user_id)] = true; });
            personPickIds = Object.keys(set);
        }
        renderPersonList();
        return !allOn;
    });

    /** 确定：把选中人员写回筛选条件并重绘列表 */
    function applyPersonPick() {
        filter.personIds = personPickIds.slice();
        hidePersonPick();
        renderFilterInputs();
        renderRows();
    }

    /** 清空选择（弹框内） */
    function clearPersonPickSelection() {
        personPickIds = [];
        renderPersonList();
    }

    /** 三个搜索框的文案、“×”可用态与条件摘要（页面上唯一的筛选条件展示处） */
    function renderFilterInputs() {
        byId("pwSubInput").value = filter.subIds.length
            ? ("已选 " + filter.subIds.length + " 个分部（含下级）") : "";
        byId("pwDeptInput").value = filter.deptIds.length
            ? ("已选 " + filter.deptIds.length + " 个部门（含下级）") : "";
        byId("pwPersonInput").value = filter.personIds.length
            ? ("已选 " + filter.personIds.length + " 人") : "";
        // 与全模块一致：选择框有值时右侧才出现“×”清除按钮（.filter-picker.has-value）
        byId("pwSubPicker").classList.toggle("has-value", filter.subIds.length > 0);
        byId("pwDeptPicker").classList.toggle("has-value", filter.deptIds.length > 0);
        byId("pwPersonPicker").classList.toggle("has-value", filter.personIds.length > 0);
        var parts = [];
        if (filter.subIds.length) { parts.push("分部 " + filter.subIds.length + " 个"); }
        if (filter.deptIds.length) { parts.push("部门 " + filter.deptIds.length + " 个"); }
        if (filter.personIds.length) { parts.push("人员 " + filter.personIds.length + " 人"); }
        byId("pwFilterTip").textContent = parts.length
            ? ("当前按 " + parts.join("、") + " 筛选（点各搜索框右侧“×”可单独清除）") : "";
    }

    /**
     * 事件绑定：元素不存在时跳过。
     *
     * <p>“微信授权密钥”那张卡片只在地址带 {@code ?showSetting=true} 时渲染（见 JSP 的
     * {@code oaonesPwShowSetting}），不带时页面上没有 {@code #pwSaveBtn} / {@code #pwAppId} 等节点。
     * 若直接写 {@code byId("pwSaveBtn").addEventListener(...)}，不带参数时这行会抛
     * {@code Cannot read properties of null}，**后面所有事件（含三个搜索框、列表行内按钮）都注册不上**，
     * 表现就是“点搜索框没反应”。统一走本方法可彻底避免这一类问题。</p>
     */
    function on(id, event, fn) {
        var el = byId(id);
        if (el) { el.addEventListener(event, fn); }
    }

    /** 选择框点击即打开对应弹框；点“×”不触发打开（由各自的清除处理接管） */
    function bindPickerOpen(wrapId, clearId, openFn) {
        on(wrapId, "click", function (e) {
            if (e.target && e.target.closest && e.target.closest("#" + clearId)) { return; }
            openFn();
        });
        var wrap = byId(wrapId);
        var input = wrap ? wrap.querySelector(".filter-input") : null;
        if (input) {
            input.addEventListener("keydown", function (e) {
                if (e.key === "Enter" || e.key === " ") { openFn(); }
            });
        }
    }

    /* ------------------------------------------------------------------
     * ④ 分部 / 部门选择弹框（筛选栏第 1、2 个搜索框共用；交互与人员绑定页一致）
     * ------------------------------------------------------------------ */
    function subChildMap() {
        var map = {};
        oaSubOptions.forEach(function (d) {
            var p = s(d.parent);
            if (!map[p]) { map[p] = []; }
            map[p].push(s(d.id));
        });
        return map;
    }

    function deptChildMap() {
        var map = {};
        oaDeptOptions.forEach(function (d) {
            var p = s(d.parent);
            if (!map[p]) { map[p] = []; }
            map[p].push(s(d.id));
        });
        return map;
    }

    /** 把选中的 id 展开成“含全部下级”的 id 列表（分部、部门共用） */
    function expandOrgIds(ids, childMap, allOptions) {
        var out = [];
        var set = {};
        var stack = [];
        var idx = {};
        (allOptions || []).forEach(function (d) { idx[s(d.id)] = true; });
        (ids || []).forEach(function (id) {
            if (idx[s(id)]) { stack.push(s(id)); }
        });
        while (stack.length) {
            var cur = stack.pop();
            if (set[cur]) { continue; }
            set[cur] = true;
            out.push(cur);
            (childMap[cur] || []).forEach(function (c) { stack.push(c); });
        }
        return out;
    }

    function expandFbIds(ids) { return expandOrgIds(ids, subChildMap(), oaSubOptions); }
    function expandDeptIds(ids) { return expandOrgIds(ids, deptChildMap(), oaDeptOptions); }

    function orgPickPrecondition() {
        if (orgType !== "dept") {
            return { tip: "支持多选，选中分部时其下级分部自动一并选中；单击选中 / 取消，双击直接确定。" };
        }
        var fbIds = filter.subIds.map(String);
        if (!fbIds.length) {
            return { tip: "未选分部时显示全部部门；页面上选了“分部”之后，这里只显示该分部（含下级）下的部门。" };
        }
        return {
            tip: "仅显示已选 " + fbIds.length + " 个分部（含下级）下的部门；选中部门时其下级部门自动一并选中。",
            filter: function (d) { return fbIds.indexOf(s(d.subcompanyid1)) >= 0; }
        };
    }
    OaonesPicker.setPrecondition("pwOrg", orgPickPrecondition);
    OaonesPicker.register("pwOrg", function () { renderOrgPickList(); });

    function orgPickRows(keyword) {
        var src = orgType === "fb" ? oaSubOptions : oaDeptOptions;
        var k = s(keyword == null ? byId("pwOrgSearch").value : keyword).trim().toLowerCase();
        if (!k) { return src.slice(); }
        return src.filter(function (d) {
            var text = [d.id, d.code, d.name, d.fullname].map(s).join(" ").toLowerCase();
            return text.indexOf(k) >= 0;
        });
    }

    function orgOptionHtml(d, isSub, partialMap) {
        var id = s(d.id);
        var active = orgSelected.indexOf(id) >= 0;
        var partial = !active && !!partialMap && partialMap[id] === true;
        var cls = "wx-pick-option pick-cols3" + (active ? " active" : (partial ? " partial" : ""));
        var tip = (active ? "点击取消选择" : "点击选择")
            + (isSub ? "；选中分部会自动选中其全部下级分部" : "；选中部门会自动选中其全部下级部门")
            + "；双击直接确定｜" + (d.fullname || d.name || "")
            + (d.code ? "（编码 " + d.code + "）" : "");
        return "<button type='button' class='" + cls + "' data-id='" + esc(id) + "'"
            + " title='" + esc(tip) + "'>"
            + "<span>" + esc(id) + "</span>"
            + "<span>" + esc(d.code || "-") + "</span>"
            + "<span>" + esc(d.name || "-") + "</span>"
            + "</button>";
    }

    function renderOrgPickList() {
        var isSub = orgType === "fb";
        var mode = OaonesPicker.mode("pwOrg");
        syncOrgAllState();
        var partialMap = OaonesPicker.partialSelected(orgSelected, isSub ? subChildMap : deptChildMap);
        var list = OaonesPicker.applyPrecondition("pwOrg", orgPickRows(mode === "tree" ? "" : null));
        var empty = isSub ? "没有匹配的分部" : "没有匹配的部门";
        var html;
        if (mode === "tree") {
            html = OaonesPicker.renderTree("pwOrg", list, {
                parentOf: function (d) { return d.parent; },
                textOf: function (d) { return [d.id, d.code, d.name, d.fullname].join(" "); },
                keyword: byId("pwOrgSearch").value,
                emptyText: empty,
                optionHtml: function (d) { return orgOptionHtml(d, isSub, partialMap); }
            });
        } else if (!list.length) {
            html = "<div class='wx-pick-empty'>" + empty + "</div>";
        } else {
            html = "<div class='wx-pick-head pick-cols3'><span>ID</span><span>编码</span><span>简称</span></div>";
            list.forEach(function (d) { html += orgOptionHtml(d, isSub, partialMap); });
        }
        var el = byId("pwOrgList");
        el.className = "wx-pick-list" + (mode === "tree" ? " is-tree" : "");
        el.innerHTML = html;
        var title = isSub ? "选择分部" : "选择部门";
        byId("pwOrgTitle").textContent = orgSelected.length
            ? (title + "（已选 " + orgSelected.length + " 个）") : title;
    }

    /** 当前可全选的候选 id（已套用搜索与前置条件过滤） */
    function orgPickAllIds() {
        return OaonesPicker.applyPrecondition("pwOrg", orgPickRows(null)).map(function (d) { return s(d.id); });
    }

    function syncOrgAllState() {
        var ids = orgPickAllIds();
        var allOn = ids.length > 0 && ids.every(function (id) { return orgSelected.indexOf(id) >= 0; });
        OaonesPicker.syncSelectAll("pwOrg", allOn);
        return allOn;
    }

    OaonesPicker.setSelectAll("pwOrg", function () {
        var ids = orgPickAllIds();
        var on = syncOrgAllState();
        var next;
        if (on) {
            // 取消“全选”时，把当前列表里各级的选中项一起摘掉（含下级已自动带上的项）
            var drop = {};
            var isSub = orgType === "fb";
            ids.forEach(function (id) {
                expandOrgIds([id], isSub ? subChildMap() : deptChildMap(),
                    isSub ? oaSubOptions : oaDeptOptions).forEach(function (v) { drop[v] = true; });
            });
            next = orgSelected.filter(function (id) { return !drop[s(id)]; });
        } else {
            next = orgType === "fb"
                ? expandFbIds(orgSelected.concat(ids))
                : expandDeptIds(orgSelected.concat(ids));
        }
        var seen = {};
        orgSelected = next.map(s).filter(function (id) {
            if (seen[id]) { return false; }
            seen[id] = true;
            return true;
        });
        renderOrgPickList();
        return syncOrgAllState();
    });

    function openOrgPick(type) {
        orgType = type;
        orgSelected = ((type === "fb" ? filter.subIds : filter.deptIds) || []).slice();
        byId("pwOrgTitle").textContent = type === "fb" ? "选择分部" : "选择部门";
        byId("pwOrgSearch").value = "";
        OaonesPicker.preconditionTip("pwOrg", "pwOrgNote");
        OaonesPicker.resetTree("pwOrg");
        renderOrgPickList();
        byId("pwOrgMask").style.display = "block";
        byId("pwOrgModal").style.display = "block";
        byId("pwOrgSearch").focus();
    }

    function hideOrgPick() {
        byId("pwOrgMask").style.display = "none";
        byId("pwOrgModal").style.display = "none";
        orgType = "";
        orgSelected = [];
    }

    function toggleOrgOption(btn) {
        var id = s(btn.getAttribute("data-id"));
        var isSub = orgType === "fb";
        var idx = orgSelected.indexOf(id);
        if (idx >= 0) {
            var removes = expandOrgIds([id], isSub ? subChildMap() : deptChildMap(),
                isSub ? oaSubOptions : oaDeptOptions);
            orgSelected = orgSelected.filter(function (v) { return removes.indexOf(s(v)) < 0; });
        } else {
            var next = orgSelected.concat([id]);
            orgSelected = isSub ? expandFbIds(next) : expandDeptIds(next);
        }
        refreshOrgPickDom();
    }

    /** 就地刷新选中态 / 部分选中态与标题，不重建列表（避免打断双击） */
    function refreshOrgPickDom() {
        var isSub = orgType === "fb";
        var partialMap = OaonesPicker.partialSelected(orgSelected, isSub ? subChildMap : deptChildMap);
        var opts = byId("pwOrgList").querySelectorAll(".wx-pick-option");
        for (var i = 0; i < opts.length; i++) {
            var id = s(opts[i].getAttribute("data-id"));
            var active = orgSelected.indexOf(id) >= 0;
            var partial = !active && partialMap[id] === true;
            opts[i].classList.toggle("active", active);
            opts[i].classList.toggle("partial", partial);
        }
        var title = isSub ? "选择分部" : "选择部门";
        byId("pwOrgTitle").textContent = orgSelected.length
            ? (title + "（已选 " + orgSelected.length + " 个）") : title;
        syncOrgAllState();
    }

    /** 确定：把选中的分部 / 部门写回筛选条件，并即时刷新列表 */
    function applyOrgPick() {
        if (orgType === "fb") {
            filter.subIds = expandFbIds(orgSelected);
            // 保留仍属于所选分部（含下级）范围的部门条件，避免出现空交集
            var keep = [];
            filter.deptIds.forEach(function (id) {
                var dept = null;
                for (var i = 0; i < oaDeptOptions.length; i++) {
                    if (s(oaDeptOptions[i].id) === s(id)) { dept = oaDeptOptions[i]; break; }
                }
                if (dept && filter.subIds.indexOf(s(dept.subcompanyid1)) >= 0) { keep.push(s(id)); }
            });
            filter.deptIds = keep;
        } else {
            filter.deptIds = expandDeptIds(orgSelected);
        }
        hideOrgPick();
        renderFilterInputs();
        renderRows();
    }

    function clearOrgPickSelection() {
        orgSelected = [];
        renderOrgPickList();
    }

    /* ------------------------------------------------------------------
     * ⑤ 扫码绑定弹框（与之前一致）
     * ------------------------------------------------------------------ */
    function startBind(oaUserId, oaName) {
        if (!CAN_MAINTAIN) {
            showToast("你不是「微信设置」的维护人，无权为员工绑定微信", 1);
            return;
        }
        if (!configured) {
            showToast("尚未维护微信授权密钥（AppID / AppSecret），请先在上方保存后再绑定", 1, 0);
            return;
        }
        openQrModal(oaUserId, oaName);
    }

    function openQrModal(oaUserId, oaName) {
        if (qrLoading) { return; }
        qrOaUserId = s(oaUserId);
        if (!qrOaUserId) { return; }
        qrLoading = true;
        byId("pwQrTitle").textContent = "微信扫码绑定";
        byId("pwQrTarget").textContent = oaName ? ("「" + oaName + "」的 OA 账号") : "该 OA 账号";
        setQrStatus("二维码生成中…", "");
        byId("pwQrBox").innerHTML = '<div class="wx-qr-loading">正在向微信申请二维码…</div>';
        byId("pwQrMask").style.display = "block";
        byId("pwQrModal").style.display = "block";
        Oaones.api("pw.qrStart", { oa_user_id: qrOaUserId }, function (res) {
            qrLoading = false;
            if (res.code !== 0) {
                byId("pwQrBox").innerHTML = '<div class="wx-qr-loading">二维码生成失败</div>';
                setQrStatus(res.msg, "fail");
                return;
            }
            var data = res.data || {};
            byId("pwQrTitle").textContent = "微信扫码绑定"
                + (data.target_oa_user_name ? " - " + data.target_oa_user_name : "");
            renderQrFrame(data.qr_url);
            setQrStatus("请用被绑定人的个人微信扫码并确认；确认后本页列表会自动刷新"
                + "（也可以点“我知道了”后手动刷新）。", "");
        });
    }

    function renderQrFrame(url) {
        var box = byId("pwQrBox");
        box.innerHTML = "";
        if (!url) {
            box.innerHTML = '<div class="wx-qr-loading">微信未返回二维码地址</div>';
            return;
        }
        var frame = document.createElement("iframe");
        frame.className = "wx-qr-frame";
        frame.setAttribute("frameborder", "0");
        // 扫码成功后同一个 iframe 会跳到本模块的回调页，长一点的失败原因需要能滚动查看
        frame.setAttribute("scrolling", "auto");
        frame.setAttribute("title", "微信扫码绑定二维码");
        frame.src = url;
        box.appendChild(frame);
    }

    function setQrStatus(text, state) {
        var el = byId("pwQrStatus");
        el.className = "wx-qr-status" + (state ? " " + state : "");
        el.textContent = text || "";
    }

    function closeQrModal() {
        byId("pwQrMask").style.display = "none";
        byId("pwQrModal").style.display = "none";
        byId("pwQrBox").innerHTML = "";
        qrOaUserId = "";
        qrLoading = false;
    }

    /** 扫码结果回调（由回调页调用本页的 OaonesWxScanDone）：成功时关弹框并刷新列表 */
    function onScanDone(state, res) {
        if (!res) { return; }
        if (res.ok) {
            setQrStatus(res.msg || "绑定成功", "ok");
            showToast(res.msg || "绑定成功");
            closeQrModal();
            loadList();
            return;
        }
        setQrStatus(res.msg || "绑定失败，请重新扫码", "fail");
    }
    window.OaonesWxScanDone = onScanDone;

    /** 在新窗口打开二维码：部分浏览器会拦截 iframe 里的第三方 Cookie，导致内嵌二维码“扫了没反应” */
    function openQrWindow() {
        if (!qrOaUserId) { return; }
        var win = window.open("", "_blank");
        Oaones.api("pw.qrStart", { oa_user_id: qrOaUserId }, function (res) {
            if (res.code !== 0) {
                if (win) { win.close(); }
                setQrStatus(res.msg, "fail");
                return;
            }
            if (!win) {
                setQrStatus("浏览器拦截了新窗口，请允许本站弹出窗口后重试，或直接使用上方内嵌二维码扫码。", "fail");
                return;
            }
            win.location.href = (res.data || {}).qr_url;
            setQrStatus("已在新窗口打开二维码：请在新窗口里用被绑定人的个人微信扫码；"
                + "扫码成功后本页会自动刷新（若没有自动刷新，点“我知道了”后手动“刷新”）。", "");
        });
    }

    /* ------------------------------------------------------------------
     * ⑥ 解绑
     * ------------------------------------------------------------------ */
    function unbind(oaUserId, oaName) {
        var msg = "确认解除 OA 账号「" + (oaName || ("ID " + oaUserId))
            + "」的个人微信绑定？解绑后该微信号可以重新绑定到其它 OA 账号，员工也可以在"
            + "“个人账号中心 → 个人微信”重新扫码绑定。";
        OaonesToast.confirm(msg, function () {
            Oaones.api("pw.unbind", { oa_user_id: oaUserId }, function (res) {
                if (res.code === 0) {
                    showToast("已解除绑定");
                    loadList();
                } else {
                    showToast(res.msg, 2);
                }
            });
        }, { danger: true });
    }

    /* ------------------------------------------------------------------
     * ⑦ 事件注册与初始化
     * ------------------------------------------------------------------ */
    function resetFilter() {
        byId("pwStateBound").checked = true;
        byId("pwStateUnbound").checked = true;
        syncStateFilterLabels();
        filter = { subIds: [], deptIds: [], personIds: [] };
        renderFilterInputs();
        renderRows();
    }

    // “保存密钥”按钮只在 ?showSetting=true 时存在，on() 会自行跳过
    on("pwSaveBtn", "click", saveConfig);
    on("pwRefreshBtn", "click", function () { loadConfig(); loadList(); });
    // 密钥框：点进掩码时让出掩码、切回密码态；没输入就离开则恢复 ****（未渲染密钥卡片时没有该节点）
    if (byId("pwAppSecret")) { OaonesSecret.bind(byId("pwAppSecret")); }
    on("pwResetBtn", "click", resetFilter);
    ["pwStateBound", "pwStateUnbound"].forEach(function (id) {
        on(id, "change", function () {
            syncStateFilterLabels();
            renderRows();
        });
    });
    // 筛选栏三个搜索框：分部 / 部门 / 人员（都是“点击框体 → 弹框搜索”，点“×”只清该项）
    bindPickerOpen("pwSubPicker", "pwSubClear", function () { openOrgPick("fb"); });
    on("pwSubClear", "click", function (e) {
        e.stopPropagation();
        filter.subIds = [];
        renderFilterInputs();
        renderRows();
    });
    bindPickerOpen("pwDeptPicker", "pwDeptClear", function () { openOrgPick("dept"); });
    on("pwDeptClear", "click", function (e) {
        e.stopPropagation();
        filter.deptIds = [];
        renderFilterInputs();
        renderRows();
    });
    bindPickerOpen("pwPersonPicker", "pwPersonClear", openPersonPick);
    on("pwPersonClear", "click", function (e) {
        e.stopPropagation();
        filter.personIds = [];
        renderFilterInputs();
        renderRows();
    });

    // 人员弹框
    on("pwPersonKeyword", "input", function () { renderPersonList(); });
    on("pwPersonList", "click", function (e) {
        var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
        if (btn) { togglePersonOption(btn); }
    });
    on("pwPersonList", "dblclick", function (e) {
        var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
        if (!btn) { return; }
        togglePersonOption(btn);
        applyPersonPick();
    });
    on("pwPersonOkBtn", "click", applyPersonPick);
    on("pwPersonCancelBtn", "click", hidePersonPick);
    on("pwPersonCloseBtn", "click", hidePersonPick);
    on("pwPersonMask", "click", hidePersonPick);
    on("pwPersonClearSelBtn", "click", clearPersonPickSelection);

    // 分部 / 部门选择弹框
    on("pwOrgList", "click", function (e) {
        var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
        if (btn) { toggleOrgOption(btn); }
    });
    on("pwOrgList", "dblclick", function (e) {
        var btn = e.target && e.target.closest ? e.target.closest(".wx-pick-option") : null;
        if (!btn) { return; }
        toggleOrgOption(btn);
        applyOrgPick();
    });
    on("pwOrgSearch", "input", function () { renderOrgPickList(); });
    on("pwOrgOkBtn", "click", applyOrgPick);
    on("pwOrgCancelBtn", "click", hideOrgPick);
    on("pwOrgCloseBtn", "click", hideOrgPick);
    on("pwOrgMask", "click", hideOrgPick);
    on("pwOrgClearBtn", "click", clearOrgPickSelection);

    // 列表行内操作
    on("pwBody", "click", function (e) {
        var target = e.target;
        if (!target || !target.closest) { return; }
        var bindBtn = target.closest("button[data-bind]");
        if (bindBtn) {
            var row = findRow(bindBtn.getAttribute("data-bind"));
            startBind(bindBtn.getAttribute("data-bind"), row ? row.name : "");
            return;
        }
        var unbindBtn = target.closest("button[data-unbind]");
        if (unbindBtn) {
            var row2 = findRow(unbindBtn.getAttribute("data-unbind"));
            unbind(unbindBtn.getAttribute("data-unbind"), row2 ? row2.name : "");
        }
    });

    // 二维码弹框
    on("pwQrMask", "click", closeQrModal);
    on("pwQrCloseBtn", "click", closeQrModal);
    on("pwQrDoneBtn", "click", function () { closeQrModal(); loadList(); });
    on("pwQrWindowBtn", "click", openQrWindow);
    on("pwQrAgainBtn", "click", function () {
        if (!qrOaUserId) { return; }
        var row = findRow(qrOaUserId);
        openQrModal(qrOaUserId, row ? row.name : "");
    });

    // Esc：先关最上层的弹框（分部/部门 → 人员 → 二维码）
    document.addEventListener("keydown", function (e) {
        if (e.key !== "Escape") { return; }
        if (byId("pwOrgModal").style.display === "block") { hideOrgPick(); return; }
        if (byId("pwPersonModal").style.display === "block") { hidePersonPick(); return; }
        if (byId("pwQrModal").style.display === "block") { closeQrModal(); }
    });

    // 公共脚本（js/api.js）没加载时给出明确提示，避免页面“点了没反应”
    if (!window.Oaones || typeof window.Oaones.api !== "function") {
        // 不带 showSetting=true 时页面上没有 #pwConfigTip（密钥卡片未渲染），退回列表卡片的提示位
        var tip = byId("pwConfigTip") || byId("pwFilterTip");
        tip.textContent = "页面公共脚本 js/api.js 未加载，绑定列表无法读取；"
            + "请刷新页面，若仍然如此请联系管理员检查 /oaones/appsyn/js/api.js。";
        tip.style.display = "";
        return;
    }
    syncStateFilterLabels();
    renderFilterInputs();
    loadConfig();
    loadList();
    loadOrgOptions();
})();
</script>
</body>
</html>
