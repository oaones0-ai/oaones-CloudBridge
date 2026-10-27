<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/oaones/appsyn/redirect.inc.jsp" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesProps" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesAdmin" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesRoutes" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.wecom.LoginConfigServiceImpl" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 登录设置（独立一级菜单：/oaones/appsyn/loginConfig/config.jsp）
     *
     * 与「企微租户设置 / 钉钉租户设置 / 消息设置」并列的第四条独立菜单：
     * 本页只做与登录页有关的设置（登录方式 / 登录页外观 / 登录框与左侧文字布局），
     * 页面里不出现租户维护入口、也不出现消息设置入口。
     *
     * 登录方式开关是**一行共享配置**（表 uf_oaones_appsyn_login_config 只保留一行，
     * 三个连接器共用，不新建表）：账号密码 / 验证登录（手机号 · 邮箱） / 扫码登录（企业微信 · 钉钉 · 飞书）
     * / 个人微信，再加扫码二维码有效期。保存后作用于**统一登录页**
     * /oaones/appsyn/login/login.jsp（账号密码 / 验证登录 / 扫码登录三种方式都在这一页；
     * 扫码是一个页签、一张二维码，三个平台共用）。
     *
     * 权限：系统管理员（user.id=1）与 oaones.appsyn.properties#login.admin.ids 中列出的人员；
     * 接口层（logincfg.get / logincfg.save）会再判一次。
     */
    request.setCharacterEncoding("UTF-8");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        oaonesRedirect(request, response, "/wui/index.html");
        return;
    }
    boolean oaonesCanMaintain = new LoginConfigServiceImpl().canMaintain(String.valueOf(user.getUID()));
%>
<%@ include file="/oaones/appsyn/assets.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>登录设置 - OAones</title>
    <script>window.OAONES_USER = { id: "<%=user.getUID()%>", sysAdmin: <%=OaonesAdmin.isTotalAdmin(String.valueOf(user.getUID()))%> };</script>
    <link rel="stylesheet" href="/oaones/appsyn/css/style.css?v=<%=oaonesAssetV%>">
    <script src="/oaones/appsyn/js/toast.js?v=<%=oaonesAssetV%>"></script>
    <script src="/oaones/appsyn/js/help.js?v=<%=oaonesAssetV%>"></script>
    <style>
        /* 登录页效果预览：固定在浏览器右侧偏上，随页面滚动保持可见；可收起，避免挡住设置项 */
        .login-effect-preview { position:fixed; z-index:40; right:16px; top:76px;
            width:min(560px, 46vw); min-width:440px; max-height:calc(100vh - 96px);
            display:flex; flex-direction:column; border:1px solid var(--oa-g-200);
            border-radius:12px; background:#fff; overflow:hidden;
            box-shadow:0 14px 40px rgba(35,39,47,.18); }
        .login-effect-preview.collapsed { width:auto; min-width:0; max-height:none; }
        .login-effect-preview.collapsed .login-effect-body,
        .login-effect-preview.collapsed .login-effect-foot { display:none; }
        .login-effect-preview.collapsed .login-effect-head { border-bottom:0; }
        .login-effect-preview.collapsed .login-effect-title span { display:none; }
        .login-effect-preview.collapsed .login-effect-tools > *:not(#loginPreviewToggleBtn) { display:none; }
        .login-effect-head { display:flex; align-items:center; justify-content:space-between;
            gap:12px; flex-wrap:wrap; padding:10px 14px; background:var(--oa-g-50);
            border-bottom:1px solid var(--oa-g-200); }
        .login-effect-title { display:flex; align-items:baseline; gap:10px; flex-wrap:wrap; }
        .login-effect-title b { font-size:14px; color:var(--oa-g-900); }
        .login-effect-title span { font-size:12px; color:var(--oa-g-500); }
        .login-effect-tools { display:flex; align-items:center; gap:8px; flex-wrap:wrap;
            font-size:12px; color:var(--oa-g-600); }
        .login-effect-tools select { padding:5px 8px; font-size:12px; min-width:150px; }
        .login-effect-body { display:flex; justify-content:center; padding:14px;
            background:linear-gradient(135deg,#f7f9fc 0%,#eef3fa 55%,#e8eef8 100%);
            overflow:auto; flex:1 1 auto; }
        .login-effect-scale { position:relative; flex:none; overflow:hidden; background:#fff;
            border:1px solid var(--oa-g-200); border-radius:10px;
            box-shadow:0 8px 24px rgba(35,39,47,.12); }
        .login-effect-scale iframe { position:absolute; left:0; top:0; display:block;
            border:0; background:#fff; transform-origin:top left; }
        .login-effect-drag { position:absolute; z-index:4; height:12px; cursor:move;
            touch-action:none; border:1px dashed rgba(74,111,165,.6); border-radius:4px;
            background:rgba(74,111,165,.12); }
        .login-effect-drag::before { content:""; position:absolute; left:50%; top:4px;
            width:36px; height:3px; margin-left:-18px; border-radius:2px;
            background:rgba(74,111,165,.6); }
        .login-effect-drag.active { outline:2px solid var(--oa-primary); outline-offset:1px; }
        .login-effect-resize { position:absolute; z-index:5; width:18px; height:18px;
            cursor:nwse-resize; touch-action:none; }
        .login-effect-resize::before { content:""; position:absolute; right:2px; bottom:2px;
            width:8px; height:8px; border-right:2px solid var(--oa-primary);
            border-bottom:2px solid var(--oa-primary); }
        /* 左侧文字的手柄放在登录框手柄下面：两块区域重叠时先保证登录框仍可拖动 */
        .login-effect-drag.brand-handle { z-index:2; border-color:rgba(191,132,0,.65);
            background:rgba(230,162,60,.14); }
        .login-effect-drag.brand-handle::before { background:rgba(191,132,0,.65); }
        .login-effect-resize.brand-handle { z-index:3; }
        .login-effect-resize.brand-handle::before { border-color:var(--oa-warning); }
        .login-effect-foot { display:flex; justify-content:space-between; gap:10px;
            flex-wrap:wrap; padding:7px 14px 9px; font-size:12px; line-height:1.7;
            color:var(--oa-g-500); }
        .login-effect-foot .warn { color:var(--oa-warning); }
        @media (max-width: 900px) {
            .login-effect-preview { position:static; width:auto; min-width:0; max-height:none;
                margin:0 0 16px; box-shadow:none; }
        }

        /* 登录框布局：位置 / 尺寸输入（拖动在上方整页预览里完成） */
        .layout-editor { display:block; }
        .layout-fields { width:100%; max-width:560px; display:grid;
            grid-template-columns:1fr 1fr; gap:9px 10px; }
        .layout-fields .layout-mode { grid-column:1 / -1; display:flex; align-items:center;
            gap:7px; font-size:13px; color:var(--oa-g-900); }
        .layout-fields .layout-mode input[type=checkbox] { margin:0; accent-color:var(--oa-primary); }
        .layout-anchor-options { grid-column:1 / -1; display:flex; flex-wrap:wrap;
            gap:10px 28px; align-items:flex-start; }
        .layout-anchor-option { display:flex; align-items:flex-start; gap:7px;
            max-width:250px; font-size:13px; line-height:1.65; color:var(--oa-g-900); }
        .layout-anchor-option input[type=radio] { margin:3px 0 0; accent-color:var(--oa-primary); }
        .layout-anchor-option .muted { display:block; font-size:12px; color:var(--oa-g-500); }
        .layout-mode-result { grid-column:1 / -1; }
        .layout-field label { display:block; font-size:12px; color:var(--oa-g-600); margin-bottom:4px; }
        .layout-field input { width:100%; }
        .layout-field .unit { display:block; margin-top:2px; text-align:right;
            font-size:11px; color:var(--oa-g-500); }
        .layout-actions { grid-column:1 / -1; display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
        .layout-tip { grid-column:1 / -1; font-size:12px; line-height:1.8; color:var(--oa-g-500); }

        /* 登录框 / 左侧文字透明度：滑块 + 数字双向联动，效果在上方整页预览里查看 */
        .card-alpha-controls { display:flex; flex-direction:column; gap:10px; }
        .card-alpha-row { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
        .card-alpha-row input[type=range] { flex:1 1 160px; min-width:140px;
            accent-color:var(--oa-primary); }
        .card-alpha-row input[type=number] { width:84px; }
        .card-alpha-ticks { display:flex; justify-content:space-between; margin:0 2px;
            font-size:11px; color:var(--oa-g-500); }
        .card-alpha-tip { font-size:12px; line-height:1.8; color:var(--oa-g-500); }
    </style>
</head>
<body>
<%-- 一级标题只属于本菜单（“登录设置”）：不链到租户设置、也不链到消息设置 --%>
<div class="navbar">
    <h1>登录设置</h1>
    <div class="nav">
        <a class="active" href="/oaones/appsyn/loginConfig/config.jsp">登录设置</a>
    </div>
</div>
<div class="page">
    <div class="card">
        <div class="toolbar" style="justify-content:space-between;">
            <div class="toolbar-left">
                <h3 style="margin:2px 0;">登录设置
                    <span class="help-tip inline" data-help="loginCfgHelpText" role="button" tabindex="0"
                          title="查看页面说明" aria-label="查看页面说明">?</span>
                </h3>
            </div>
            <div>
                <button type="button" class="btn" id="loginCfgSaveBtn">保存设置</button>
            </div>
        </div>
        <%-- 所有登录页外观设置共用一个整页预览：改任何一项都即时反映在这里，不需要逐项各看一块小预览 --%>
        <div class="login-effect-preview" id="loginEffectPreview">
            <div class="login-effect-head">
                <div class="login-effect-title">
                    <b>登录页效果预览</b>
                    <span>所有设置共用这一个预览，未保存的修改也会即时显示</span>
                </div>
                <div class="login-effect-tools">
                    <label for="cfgLoginBoxViewport">预览屏幕</label>
                    <select id="cfgLoginBoxViewport">
                        <option value="current">当前窗口（自动）</option>
                        <option value="1280x720">1280 × 720</option>
                        <option value="1366x768">1366 × 768</option>
                        <option value="1440x900">1440 × 900</option>
                        <option value="1920x1080">1920 × 1080</option>
                        <option value="2560x1440">2560 × 1440</option>
                    </select>
                    <a class="btn small secondary" href="<%=OaonesRoutes.publicUrl("/oaones/appsyn/login/login.jsp")%>"
                       target="_blank" title="在新窗口打开实际登录页（只反映已保存的设置）">打开实际登录页</a>
                    <button type="button" class="btn small secondary" id="loginPreviewToggleBtn"
                            title="收起 / 展开效果预览">收起</button>
                </div>
            </div>
            <div class="login-effect-body">
                <%-- iframe 内容是 loginConfig/preview.jsp：只复用登录页版式，不读写配置；表单值由本页即时传入 --%>
                <div class="login-effect-scale" id="loginBoxStage">
                    <iframe id="loginPreviewFrame" title="登录页效果预览"
                            src="/oaones/appsyn/loginConfig/preview.jsp?v=<%=oaonesAssetV%>"></iframe>
                    <%-- 拖动 / 缩放手柄盖在预览里的登录框上：坐标换算与真实登录页一致 --%>
                    <span class="login-effect-drag" id="loginBoxPreview"
                          title="按住拖动登录框位置" style="display:none;"></span>
                    <span class="login-effect-resize" id="loginBoxResize"
                          title="拖动调整登录框宽高" style="display:none;"></span>
                    <%-- 左侧文字的位置 / 尺寸也直接在同一个预览里拖：与登录框手柄互不遮挡（文字层在下） --%>
                    <span class="login-effect-drag brand-handle" id="brandPreview"
                          title="按住拖动左侧文字位置" style="display:none;"></span>
                    <span class="login-effect-resize brand-handle" id="brandResize"
                          title="拖动调整左侧文字宽高" style="display:none;"></span>
                </div>
            </div>
            <div class="login-effect-foot">
                <span id="loginBoxStageInfo">预览按当前窗口等比缩放</span>
                <span id="loginBoxStageWarn"></span>
            </div>
        </div>
        <div class="help-source" id="loginCfgHelpText">
            <p>本页是<b>独立的一级菜单「登录设置」</b>：只维护登录方式、登录页外观与登录框 / 左侧文字的布局。
                桌面启动台的自动跳转秒数在「应用中心 → 应用列表」维护，不在本页。
                登录方式决定统一登录页
                （<span class="mono"><%=OaonesRoutes.publicUrl("/oaones/appsyn/login/login.jsp")%></span>）显示哪些方式，
                以及扫码二维码的有效期。<b>只有开启的方式才会在登录页上出现</b>，
                关闭的方式即使有人直接访问接口也会被服务端拒绝。</p>
            <p><b>效果预览</b>：浏览器右侧偏上有一个浮动的整页预览，<b>所有登录页设置共用它</b>——
                左侧文字、Logo、整页背景图、登录方式页签、登录框位置与尺寸、文字说明位置与尺寸、
                登录框透明度、左侧文字背景透明度
                都在这一个画面里同时呈现；改任何一项都会即时更新，<b>不需要先保存</b>。
                预览<b>固定在浏览器右侧偏上位置</b>，页面滚动时保持可见；挡住设置项时点“收起”即可，
                需要时再展开。
                预览默认按当前窗口等比缩放，也可以选择 1920×1080 等参考屏幕核对点位。
                “打开实际登录页”打开的是正式页面，只反映<b>已经保存</b>的设置。</p>
            <p><b>账号密码登录</b>：沿用 E9 平台自己的登录接口，密码策略、验证码、登录日志都按平台口径。</p>
            <p><b>验证登录</b>：登录页的第二个页签，页签内可切换<b>手机号</b> / <b>邮箱</b>两种验证方式——
                只有勾选的方式才会在该页签里出现（只勾一种时页签内不出现切换）。
                验证码由本模块自己生成与管理（发送频率、有效期、错误次数都以系统设置为准），
                只有“发出去”这一步借用平台标准通道：手机号走后台“短信设置”的短信通道，
                邮箱走后台“邮件设置”里群发邮箱那条邮件通道；
                手机号 / 邮箱都必须是在人员信息里维护的、且<b>唯一</b>对应一个有效账号。</p>
            <p><b>扫码登录</b>：登录页上只有<b>一个扫码页签、一张二维码</b>（本系统自己生成），
                企业微信 / 钉钉 / 飞书客户端扫的是同一张码——扫码时由服务端按客户端类型
                自动判断平台，再用该平台已对接的租户换成员身份。
                下面的三个开关是<b>同一张二维码按平台的准入</b>：关掉某个平台只是不允许用该平台扫码，
                不影响另外两个平台使用同一张二维码。</p>
            <p><b>个人微信登录</b>：开启后登录页出现独立的<b>“个人微信登录”</b>页签，
                使用微信开放平台官方二维码；扫码后按「微信设置」里的个人微信绑定关系定位 OA 账号。
                使用前必须先在「微信设置」页维护 AppID / AppSecret，并让员工完成个人微信绑定。
                个人微信与企业微信 / 钉钉 / 飞书不共用二维码，但共用扫码登录票据、确认流程与有效期。</p>
            <p><b>扫码二维码有效期（秒）</b>：超时后登录页提示
                <span class="rule">已超时，请手动刷新二维码</span>；允许 <b>30 ~ 180 秒</b>（最长 3 分钟），
                留空表示用系统默认值（默认 <b>120 秒</b>）。登录页二维码下方会按秒倒计时，
                点二维码图片或“刷新二维码”都会重新生成并重新计时。</p>
            <p><b>登录页 Logo</b>：上传一张图片（<span class="mono">png / jpg / gif / bmp / webp</span>，
                单边不超过 <b>2048</b> 像素、体积不超过 <b>1MB</b>，建议用透明底 PNG），
                点“保存设置”后<b>按比例缩放</b>显示在统一登录页<b>右侧登录框的内部上方</b>
                （宽度不超过登录框，高度自适应，不会拉伸变形）。
                图片保存在站点的 <span class="mono">oaones/appsyn/img/</span> 目录下；不上传或点“清除”时登录页不显示 logo。
                <b>超时后的“重新登录”弹框里一律不显示 logo</b>——弹框要的是一眼就能登录，不再占一行图片的高度。</p>
            <p><b>登录页背景图</b>：上传一张图片（<span class="mono">png / jpg / gif / bmp / webp</span>，
                单边不超过 <b>8192</b> 像素、体积不超过 <b>2MB</b>），点“保存设置”后作为<b>整个登录页的背景</b>：
                前端按窗口大小<b>自动缩放</b>（铺满窗口、等比放大缩小、居中裁切，不会拉伸变形，窗口变大变小都跟着变）。
                不上传或点“清除”则用系统默认底色。建议用横向、浅色的图（左侧文字是深色）；
                右侧登录框本身是白底，表单区始终清晰。
                <b>超时后的“重新登录”弹框里不显示背景图</b>（弹框由父页给白底卡片，本页只留登录卡片本身）。</p>
            <p><b>登录框透明度</b>：调的是右侧登录框的<b>白色底色</b>，让整页背景图透出来；
                <b>0% = 完全不透明，100% = 完全透明</b>。文字、输入框、按钮始终按原样显示，不会跟着变淡，
                所以即使调到较高的透明度，表单仍然可读。调整后直接看右侧浮动的整页效果预览；
                超时后的“重新登录”弹框不受这里影响，仍由父页提供白底卡片。</p>
            <p><b>左侧文字背景透明度</b>：调的是登录页<b>左侧文字区的白色底板</b>；
                <b>0% = 完全不透明，100% = 完全透明</b>。100% 是默认值，也就是没有底板，与历史登录页一致；
                只调整底板，左侧文字本身始终保持清晰。调整后直接看右侧浮动的整页效果预览，
                超时后的“重新登录”弹框里不显示左侧区域，也不受这里影响。</p>
            <p><b>登录框与左侧文字定位方式</b>：二选一，并<b>同时作用于登录框和左侧文字</b>。
                “左上角相对定位”沿用现有方式，以浏览器可视区域左上角为 0,0；
                “中心点相对定位”以浏览器视口中心为 0,0，X / Y 表示区域中心相对视口中心的偏移，
                向右 / 向下为正，向左 / 向上为负。两种方式都可继续在效果预览中拖动，保存后始终使用同一个定位基准。</p>
            <p><b>登录框位置与尺寸</b>：按当前选择的定位方式设置登录框的 X、Y、宽度和高度。
                直接在右侧浮动的效果预览里<b>拖动登录框</b>调整位置，
                拖右下角调整宽高；四项要么都设置、要么都不设置。<b>不勾选“自定义位置与尺寸”时，
                登录框在页面居中（水平、垂直），左侧文字保留在登录框左边</b>（系统默认布局）。
                坐标允许 0，所以“留空”才表示默认。宽高按 CSS 像素保存；高度固定时表单内容超出
                会在登录框内部滚动。视口宽度 ≤ 860px 时登录页仍自动使用移动端默认布局，
                避免固定坐标把登录框挤出手机屏幕。超时后的“重新登录”弹框不受这里影响。</p>
            <p><b>登录页左侧文字</b>：用富文本编辑器写，<b>设置什么、登录页左侧就显示什么</b>
                （HTML 原样输出，不转义、不过滤标签；支持加粗 / 斜体 / 下划线 / 字号 / 颜色 / 列表 / 对齐 / 链接，
                点工具栏的“源码”可以直接编辑 HTML）。填了内容就<b>整段替换</b>系统默认的左侧说明
                （默认说明是登录页标题 + 已开启登录方式的清单）；点“恢复默认”并保存即回到默认。
                内容上限 <b>20000</b> 个字符；因为这里是原样输出，请只粘贴可信的来源。
                左侧区域的位置与尺寸在下一行「文字说明位置与尺寸」单独设置；
                左侧区域在<b>超时后的“重新登录”弹框里不显示</b>（弹框只留登录本身）。</p>
            <p><b>文字说明位置与尺寸</b>：设置的是登录页<b>左侧文字区</b>（自定义富文本或系统默认说明都在里面）。
                口径与登录框完全一致，使用同一个定位方式：按像素设置左侧文字的 X、Y、宽度和高度
                （左上角模式为左边缘 / 上边缘，中心模式为区域中心相对视口中心的偏移）；
                直接在右侧浮动的效果预览里<b>拖动文字区</b>调整位置，拖右下角调整宽高，
                四项要么都设置、要么都不设置。<b>不勾选“自定义位置与尺寸”时跟随系统默认版式</b>
                （在登录框左边，随登录框的默认 / 自定义布局自动让位）。高度固定时内容超出会在文字区内滚动；
                视口宽度 ≤ 860px 时仍使用移动端默认布局；超时后的“重新登录”弹框里不显示左侧区域。</p>
            <p>安全底线：<b>账号密码 / 验证登录（手机号或邮箱至少勾一个）/ 扫码登录（至少允许一个平台）至少要开启一种</b>，
                否则保存会被拒绝——避免把所有人挡在登录页外。
                若只允许某一家扫码，员工用另外两家客户端扫码时会看到明确的“已关闭”提示。</p>
            <% if (oaonesCanMaintain) { %>
            <p>你有维护权限：系统管理员可维护全部设置，<span class="mono">login.admin.ids</span>
                中列出的人员也能维护本页。保存后立即对统一登录页生效。</p>
            <% } else { %>
            <p>你没有维护权限：仅系统管理员与 <span class="mono">login.admin.ids</span>
                中列出的人员可维护本页。如需调整登录方式，请联系系统管理员。</p>
            <% } %>
        </div>
        <form id="loginCfgForm" onsubmit="return false;">
            <table class="grid" style="max-width:1200px;">
                <thead>
                <tr>
                    <th style="width:30%;">登录方式</th>
                    <th style="width:16%;">开关</th>
                    <th>说明</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td><b>账号密码登录</b></td>
                    <td><label><input type="checkbox" id="cfgPasswordEnable" name="password_enable"> 开启</label></td>
                    <td>开启后登录页显示“账号密码登录”页签，校验沿用平台口径。</td>
                </tr>
                <tr>
                    <td>
                        <b>验证登录</b>
                        <div class="muted" style="margin-top:4px;">允许的验证方式：</div>
                    </td>
                    <td>
                        <label><input type="checkbox" id="cfgMobileEnable" name="mobile_enable"> 手机号</label>
                        <label style="margin-left:14px;"><input type="checkbox" id="cfgEmailEnable" name="email_enable"> 邮箱</label>
                    </td>
                    <td>登录页的第二个页签（<b>验证登录</b>）里出现哪些验证方式由这里决定：
                        勾两种时页签内可切换手机号 / 邮箱，只勾一种就只出现这一种；两个都不勾则没有该页签。
                        验证码由本模块自己发送与校验（发送频率、有效期、错误次数以系统设置为准）：
                        手机号走平台标准短信能力（后台“短信设置”的通道），
                        邮箱走平台标准邮件能力（后台“邮件设置”的群发邮箱）。</td>
                </tr>
                <tr>
                    <td>
                        <b>扫码登录</b>
                        <div class="muted" style="margin-top:4px;">允许扫码的客户端：</div>
                    </td>
                    <td>
                        <label><input type="checkbox" id="cfgWecomQrEnable" name="wecom_qr_enable"> 企业微信</label>
                        <label style="margin-left:14px;"><input type="checkbox" id="cfgDingtalkQrEnable" name="dingtalk_qr_enable"> 钉钉</label>
                        <label style="margin-left:14px;"><input type="checkbox" id="cfgFeishuQrEnable" name="feishu_qr_enable"> 飞书</label>
                    </td>
                    <td>登录页（<span class="mono"><%=OaonesRoutes.publicUrl("/oaones/appsyn/login/login.jsp")%></span>）显示“扫码登录”页签：
                        只有一个页签、只有一张二维码，三个客户端扫的是同一张码。
                        这里勾选的平台才允许扫码（至少勾一个扫码页签才会出现）。</td>
                </tr>
                <tr>
                    <td><b>个人微信登录</b></td>
                    <td><label><input type="checkbox" id="cfgPersonalWxEnable" name="personal_wx_enable"> 开启</label></td>
                    <td>开启后登录页显示独立的“个人微信登录”页签，使用微信开放平台官方二维码。
                        使用前需在独立菜单「微信设置」维护 AppID / AppSecret，
                        并与员工建立个人微信绑定；未配置密钥时登录页不会把这路方式算作可用。
                        <span id="cfgPersonalWxState" class="muted"></span></td>
                </tr>
                <tr>
                    <td><b>扫码二维码有效期（秒）</b></td>
                    <td>
                        <input type="number" id="cfgQrTtl" name="oaonesQrTtl" min="30" max="180" step="1"
                               autocomplete="off" data-lpignore="true" style="width:120px;"
                               placeholder="留空用默认 120">
                    </td>
                    <td>超时后登录页提示“<span class="rule">已超时，请手动刷新二维码</span>”；
                        允许 <b>30 ~ 180 秒</b>（最长 3 分钟），留空表示使用系统默认值
                        （<span id="cfgQrTtlDefault">-</span> 秒）；登录页二维码下方按秒倒计时，
                        点二维码图片或“刷新二维码”都会重新计时。</td>
                </tr>
                <tr>
                    <td>
                        <b>登录页 Logo</b>
                        <div class="muted" style="margin-top:4px;">
                            png / jpg / gif / bmp / webp，≤ <span id="cfgLogoMaxKb">-</span>KB
                        </div>
                    </td>
                    <td colspan="2">
                        <%-- logo 一律从这里上传（不手填地址）：上传只落盘，点“保存设置”才写进配置行 --%>
                        <div class="cfg-logo">
                            <span class="cfg-logo-preview" id="cfgLogoPreview"></span>
                            <input type="hidden" id="cfgLogo">
                            <input type="file" id="cfgLogoFile" style="display:none;" data-lpignore="true"
                                   accept=".png,.jpg,.jpeg,.gif,.bmp,.webp">
                            <button type="button" class="btn small secondary" id="cfgLogoUploadBtn"
                                    title="选择图片上传（保存后生效）">上传 logo</button>
                            <button type="button" class="btn small secondary" id="cfgLogoClearBtn"
                                    title="清除已上传的 logo">清除</button>
                            <span class="cfg-logo-result" id="cfgLogoResult"></span>
                        </div>
                        <div class="muted" style="margin-top:6px;">
                            保存后按比例缩放显示在统一登录页<b>右侧登录框的内部上方</b>；
                            超时后的“重新登录”弹框里不显示。不上传（或点“清除”再保存）则登录页不显示 logo。
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <b>登录页背景图</b>
                        <div class="muted" style="margin-top:4px;">
                            png / jpg / gif / bmp / webp，≤ <span id="cfgBgMaxKb">-</span>KB
                        </div>
                    </td>
                    <td colspan="2">
                        <%-- 整页背景图：与 logo 同一套上传口径（落 oaones/appsyn/img/，前缀不同） --%>
                        <div class="cfg-logo">
                            <span class="cfg-bg-preview" id="cfgBgPreview"></span>
                            <input type="hidden" id="cfgBgImage">
                            <input type="file" id="cfgBgFile" style="display:none;" data-lpignore="true"
                                   accept=".png,.jpg,.jpeg,.gif,.bmp,.webp">
                            <button type="button" class="btn small secondary" id="cfgBgUploadBtn"
                                    title="选择图片上传（保存后生效）">上传背景图</button>
                            <button type="button" class="btn small secondary" id="cfgBgClearBtn"
                                    title="清除已上传的背景图">清除</button>
                            <span class="cfg-logo-result" id="cfgBgResult"></span>
                        </div>
                        <div class="muted" style="margin-top:6px;">
                            保存后作为<b>整个登录页的背景</b>，前端按窗口大小自动缩放（铺满、等比、居中裁切，不拉伸变形）；
                            超时后的“重新登录”弹框里不显示。不上传（或点“清除”再保存）则用系统默认底色。
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <b>登录框透明度</b>
                        <div class="muted" style="margin-top:4px;">0% 不透明，100% 全透明</div>
                    </td>
                    <td colspan="2">
                        <div class="card-alpha-controls" style="max-width:460px;">
                            <div class="card-alpha-row">
                                <input type="range" id="cfgLoginBoxTransparency" min="0" max="100"
                                       step="1" value="0" aria-label="登录框透明度">
                                <input type="number" id="cfgLoginBoxTransparencyValue" min="0" max="100"
                                       step="1" value="0" autocomplete="off" data-lpignore="true">
                                <span class="muted" style="font-size:12px;">%</span>
                            </div>
                            <div class="card-alpha-ticks"><span>0% 不透明</span><span>100% 全透明</span></div>
                            <div class="card-alpha-tip">
                                只调整登录框白色底色，文字、输入框和按钮保持清晰；
                                调整后直接看右侧浮动的“登录页效果预览”，保存后统一登录页生效。
                            </div>
                            <div class="card-alpha-row">
                                <button type="button" class="btn small secondary"
                                        id="cfgLoginBoxTransparencyResetBtn">恢复不透明</button>
                                <span class="cfg-logo-result" id="cfgLoginBoxTransparencyResult"></span>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <b>定位方式</b>
                        <div class="muted" style="margin-top:4px;">登录框与左侧文字共用</div>
                    </td>
                    <td colspan="2">
                        <div class="layout-editor">
                            <div class="layout-fields">
                                <div class="layout-anchor-options" role="radiogroup" aria-label="登录框与左侧文字定位方式">
                                    <label class="layout-anchor-option">
                                        <input type="radio" name="cfgLayoutMode" id="cfgLayoutModeLeftTop"
                                               value="0" checked>
                                        <span>左上角相对定位
                                            <span class="muted">保留现有方式，X / Y 是区域左上角坐标</span>
                                        </span>
                                    </label>
                                    <label class="layout-anchor-option">
                                        <input type="radio" name="cfgLayoutMode" id="cfgLayoutModeCenter"
                                               value="1">
                                        <span>中心点相对定位
                                            <span class="muted">X / Y 是区域中心相对视口中心的偏移，可为负数</span>
                                        </span>
                                    </label>
                                </div>
                                <div class="layout-tip" id="cfgLayoutModeHint">
                                    登录框和左侧文字始终使用同一种定位方式；切换时会按预览中的当前位置换算，
                                    不会改变画面上的点位。
                                </div>
                                <span class="cfg-logo-result layout-mode-result" id="cfgLayoutModeResult"></span>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <b>登录框位置与尺寸</b>
                        <div class="muted" style="margin-top:4px;">在顶部效果预览中拖动登录框</div>
                    </td>
                    <td colspan="2">
                        <div class="layout-editor">
                            <div class="layout-fields">
                                <label class="layout-mode">
                                    <input type="checkbox" id="cfgLoginBoxCustom">
                                    使用自定义位置与尺寸（不勾选 = 系统默认居中布局）
                                </label>
                                <div class="layout-field">
                                    <label for="cfgLoginBoxLeft" id="cfgLoginBoxLeftLabel">距页面左侧（X）</label>
                                    <input type="number" id="cfgLoginBoxLeft" min="0"
                                           max="<%=OaonesConst.LOGIN_BOX_MAX_POS%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-field">
                                    <label for="cfgLoginBoxTop" id="cfgLoginBoxTopLabel">距页面顶部（Y）</label>
                                    <input type="number" id="cfgLoginBoxTop" min="0"
                                           max="<%=OaonesConst.LOGIN_BOX_MAX_POS%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-field">
                                    <label for="cfgLoginBoxWidth">登录框宽度</label>
                                    <input type="number" id="cfgLoginBoxWidth"
                                           min="<%=OaonesConst.LOGIN_BOX_MIN_WIDTH%>"
                                           max="<%=OaonesConst.LOGIN_BOX_MAX_WIDTH%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-field">
                                    <label for="cfgLoginBoxHeight">登录框高度</label>
                                    <input type="number" id="cfgLoginBoxHeight"
                                           min="<%=OaonesConst.LOGIN_BOX_MIN_HEIGHT%>"
                                           max="<%=OaonesConst.LOGIN_BOX_MAX_HEIGHT%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-actions">
                                    <button type="button" class="btn small secondary" id="cfgLoginBoxResetBtn">
                                        恢复默认布局
                                    </button>
                                    <span class="cfg-logo-result" id="cfgLoginBoxResult"></span>
                                </div>
                                <div class="layout-tip" id="cfgLoginBoxPositionTip">
                                    当前坐标以<b>浏览器可视区域左上角</b>为 0,0，单位是 CSS 像素；
                                    不勾选时登录框按系统默认布局<b>居中显示</b>，左侧说明区在它左边；
                                    可以直接在右侧浮动的效果预览里拖动登录框到任意点位，拖右下角改宽高。
                                    高度固定时内容超出会在登录框内滚动；
                                    窄屏（≤860px）登录页仍自动使用移动端默认布局。
                                </div>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <b>登录页左侧文字</b>
                        <div class="muted" style="margin-top:4px;">富文本，设置什么显示什么</div>
                    </td>
                    <td colspan="2">
                        <%-- 左侧富文本：编辑器（js/richtext.js）把 HTML 同步到 cfgBrandHtml 随表单提交 --%>
                        <input type="hidden" id="cfgBrandHtml">
                        <div id="cfgBrandBox"></div>
                        <div class="muted" style="margin-top:6px;">
                            显示在统一登录页<b>左侧</b>（右侧是登录框）：这里写什么，登录页就显示什么
                            （HTML 原样输出，不转义、不过滤标签；支持加粗 / 颜色 / 字号 / 列表 / 对齐 / 链接，
                            点“源码”可直接改 HTML）。留空（或点“恢复默认”）则用系统默认的左侧说明。
                            文字区的<b>位置与尺寸在下方单独设置</b>；超时后的“重新登录”弹框里不显示左侧区域。
                        </div>
                        <div style="margin-top:8px; display:flex; align-items:center; gap:12px; flex-wrap:wrap;">
                            <button type="button" class="btn small secondary" id="cfgBrandClearBtn"
                                    title="清空内容并在保存后恢复系统默认的左侧说明">恢复默认</button>
                            <span class="cfg-logo-result" id="cfgBrandResult"></span>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <b>左侧文字背景透明度</b>
                        <div class="muted" style="margin-top:4px;">0% 不透明，100% 全透明</div>
                    </td>
                    <td colspan="2">
                        <div class="card-alpha-controls" style="max-width:460px;">
                            <div class="card-alpha-row">
                                <input type="range" id="cfgBrandTransparency" min="0" max="100"
                                       step="1" value="100" aria-label="左侧文字背景透明度">
                                <input type="number" id="cfgBrandTransparencyValue" min="0" max="100"
                                       step="1" value="100" autocomplete="off" data-lpignore="true">
                                <span class="muted" style="font-size:12px;">%</span>
                            </div>
                            <div class="card-alpha-ticks"><span>0% 不透明</span><span>100% 全透明</span></div>
                            <div class="card-alpha-tip">
                                只调整左侧文字区的白色底板，文字保持清晰；0% 为不透明白底，
                                100% 为无底板（默认，与历史登录页一致）。可直接在效果预览里查看。
                            </div>
                            <div class="card-alpha-row">
                                <button type="button" class="btn small secondary"
                                        id="cfgBrandTransparencyResetBtn">恢复全透明</button>
                                <span class="cfg-logo-result" id="cfgBrandTransparencyResult"></span>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <b>文字说明位置与尺寸</b>
                        <div class="muted" style="margin-top:4px;">左侧文字区，在效果预览中拖动</div>
                    </td>
                    <td colspan="2">
                        <div class="layout-editor">
                            <div class="layout-fields">
                                <label class="layout-mode">
                                    <input type="checkbox" id="cfgBrandCustom">
                                    使用自定义位置与尺寸（不勾选 = 跟随系统默认版式）
                                </label>
                                <div class="layout-field">
                                    <label for="cfgBrandLeft" id="cfgBrandLeftLabel">距页面左侧（X）</label>
                                    <input type="number" id="cfgBrandLeft" min="0"
                                           max="<%=OaonesConst.LOGIN_BRAND_MAX_POS%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-field">
                                    <label for="cfgBrandTop" id="cfgBrandTopLabel">距页面顶部（Y）</label>
                                    <input type="number" id="cfgBrandTop" min="0"
                                           max="<%=OaonesConst.LOGIN_BRAND_MAX_POS%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-field">
                                    <label for="cfgBrandWidth">文字区宽度</label>
                                    <input type="number" id="cfgBrandWidth"
                                           min="<%=OaonesConst.LOGIN_BRAND_MIN_WIDTH%>"
                                           max="<%=OaonesConst.LOGIN_BRAND_MAX_WIDTH%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-field">
                                    <label for="cfgBrandHeight">文字区高度</label>
                                    <input type="number" id="cfgBrandHeight"
                                           min="<%=OaonesConst.LOGIN_BRAND_MIN_HEIGHT%>"
                                           max="<%=OaonesConst.LOGIN_BRAND_MAX_HEIGHT%>" step="1"
                                           autocomplete="off" data-lpignore="true" disabled>
                                    <span class="unit">px</span>
                                </div>
                                <div class="layout-actions">
                                    <button type="button" class="btn small secondary" id="cfgBrandResetBtn">
                                        恢复默认版式
                                    </button>
                                    <span class="cfg-logo-result" id="cfgBrandLayoutResult"></span>
                                </div>
                                <div class="layout-tip" id="cfgBrandPositionTip">
                                    坐标与登录框使用同一种定位方式，单位为 CSS 像素；
                                    不勾选时左侧文字跟随系统默认版式（在登录框左边）；
                                    可以直接在右侧浮动的效果预览里拖动左侧文字区到任意点位，拖右下角改宽高。
                                    高度固定时内容超出会在文字区内滚动；
                                    窄屏（≤860px）登录页仍自动使用移动端默认布局。
                                </div>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td><b>备注</b></td>
                    <td colspan="2">
                        <input type="text" id="cfgRemark" name="remark" maxlength="200"
                               autocomplete="off" data-lpignore="true" style="width:100%;"
                               placeholder="例如：2026-10 起启用邮箱验证登录">
                    </td>
                </tr>
                </tbody>
            </table>
            <p class="muted" style="margin-top:10px;">最近修改：<span id="cfgUpdateInfo">-</span></p>
        </form>
    </div>
</div>
<script src="/oaones/appsyn/js/api.js?v=<%=oaonesAssetV%>"></script>
<script src="/oaones/appsyn/js/richtext.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
(function () {
    var CAN_MAINTAIN = <%=oaonesCanMaintain%>;
    /** 个人微信 AppID / AppSecret 是否已在「微信设置」配置好；未配置时实际登录页不显示该页签。 */
    var personalWxConfigured = false;
    /** 服务端默认的有效期（秒）：设置页留空时按它工作，页面提示按它显示 */
    var defaultTtl = 0;

    /*
     * 登录页图片上传（logo / 整页背景图）的前端口径：数值全部取后端常量（OaonesConst），
     * 与 Service 的校验一致，页面上显示的上限就是后端认的那一套，避免两处各写一份数字。
     * 上传只落盘（回一个“待保存”的地址），点“保存设置”时随 logincfg.save 一起写进配置行；
     * 上传失败 / 体积超限的提示由后端给（前端只按后缀与体积粗筛一次，少一次无谓的上传）。
     * kind 放在查询串上（multipart 请求体里的字段容器不会并进 getParameter，见 imageUpload.jsp）。
     */
    var LOGIN_IMG = {
        url: "/oaones/appsyn/loginConfig/imageUpload.jsp",
        logo: {
            kind: "logo",
            maxBytes: <%=com.customization.oaones.appsyn.common.OaonesConst.LOGIN_LOGO_MAX_BYTES%>,
            extensions: "<%=com.customization.oaones.appsyn.common.OaonesConst.LOGIN_LOGO_EXTENSIONS%>",
            uploading: false
        },
        bg: {
            kind: "bg",
            maxBytes: <%=com.customization.oaones.appsyn.common.OaonesConst.LOGIN_BG_MAX_BYTES%>,
            extensions: "<%=com.customization.oaones.appsyn.common.OaonesConst.LOGIN_BG_EXTENSIONS%>",
            uploading: false
        }
    };

    /**
     * 两类图片在页面上的控件约定（kind → 隐藏域 / 预览框 / 状态文案 / 触发按钮 / 文件框）。
     * 上传、预览、清除三件事都按这张表分派，避免为 logo 与背景图各写一套实现。
     */
    var IMG_UI = {
        logo: { input: "cfgLogo", preview: "cfgLogoPreview", result: "cfgLogoResult",
            pickBtn: "cfgLogoUploadBtn", clearBtn: "cfgLogoClearBtn", fileEl: "cfgLogoFile",
            empty: "未上传", emptyResult: "" },
        bg: { input: "cfgBgImage", preview: "cfgBgPreview", result: "cfgBgResult",
            pickBtn: "cfgBgUploadBtn", clearBtn: "cfgBgClearBtn", fileEl: "cfgBgFile",
            empty: "未上传", emptyResult: "" }
    };

    function byId(id) { return document.getElementById(id); }
    function showToast(msg, mode, duration) {
        if (msg === null || msg === undefined || String(msg) === "") { return; }
        OaonesToast.show(msg, { mode: mode, duration: duration });
    }
    function checked(id) { return byId(id).checked ? "1" : "0"; }
    function setChecked(id, value) { byId(id).checked = String(value) === "1"; }

    /** 图片状态文案：普通 / 提醒（尺寸偏大等）/ 失败（与图标上传同一套配色） */
    function imgResult(kind, msg, mode) {
        var el = byId(IMG_UI[kind].result);
        el.className = "cfg-logo-result" + (mode ? " " + mode : "");
        el.textContent = msg || "";
    }

    /**
     * 渲染图片预览：logo 按比例缩放显示（与登录页同一个“不拉伸”的口径）；
     * 背景图按“铺满裁切”显示（与登录页 cover 的观感一致）。没有地址时提示未上传。
     */
    function renderImgPreview(kind) {
        var ui = IMG_UI[kind];
        var url = String(byId(ui.input).value || "");
        var box = byId(ui.preview);
        if (url.length === 0) {
            box.innerHTML = "<span class='muted'>" + ui.empty + "</span>";
            return;
        }
        box.innerHTML = "<img src='" + Oaones.esc(url) + "' alt='登录页图片预览'>";
    }

    /** 设置某类图片的地址（空串 = 清除）并刷新预览 */
    function setImg(kind, url) {
        byId(IMG_UI[kind].input).value = url || "";
        renderImgPreview(kind);
        renderUnifiedPreview();
    }

    /**
     * 上传图片：POST multipart 给专用入口页（api.jsp 是 GET 口径，收不了文件）。
     * 前端只按后缀与体积粗筛一次（少一次无谓上传），真正的判定在后端。
     */
    function uploadImg(kind, file) {
        if (!file) { return; }
        var spec = LOGIN_IMG[kind];
        var label = kind === "bg" ? "背景图" : "logo";
        if (!/\.(png|jpe?g|gif|bmp|webp)$/i.test(String(file.name || ""))) {
            showToast(label + "只支持 " + spec.extensions + " 图片", 2);
            return;
        }
        if (file.size > spec.maxBytes) {
            showToast(label + "不能超过 " + Math.round(spec.maxBytes / 1024) + "KB，请压缩后再上传", 2);
            return;
        }
        var form = new FormData();
        form.append("file", file, file.name);
        spec.uploading = true;
        imgResult(kind, "上传中…");
        // kind 放查询串：multipart 请求体里的字段容器不会并进 getParameter（见 imageUpload.jsp）
        fetch(LOGIN_IMG.url + "?kind=" + encodeURIComponent(spec.kind),
            { method: "POST", body: form, credentials: "same-origin" })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                spec.uploading = false;
                if (!res || res.code !== 0) {
                    var msg = (res && res.msg) ? res.msg : (label + "上传失败");
                    imgResult(kind, msg, "error");
                    showToast(msg, 2, 0);
                    return;
                }
                var data = res.data || {};
                setImg(kind, String(data.url || ""));
                var size = Number(data.width) > 0 && Number(data.height) > 0
                    ? "（" + data.width + "×" + data.height + "）" : "";
                imgResult(kind, "已上传" + size + "，点“保存设置”后生效");
                if (kind === "logo") {
                    if (Number(data.width) > 0 && Number(data.height) > 0
                            && (Number(data.width) > 1200 || Number(data.height) > 400)) {
                        showToast("这张 logo 偏宽大" + size + "，登录页会按比例缩到登录框宽度，"
                            + "建议用横向、高度 60~120 像素的图片", 1, 6000);
                    } else {
                        showToast("logo 已上传，点“保存设置”后生效");
                    }
                    return;
                }
                // 背景图：太小的图铺满后会发虚，这里提前提醒（不拦截）
                if (Number(data.width) > 0 && Number(data.height) > 0
                        && (Number(data.width) < 1280 || Number(data.height) < 720)) {
                    showToast("这张背景图偏小" + size + "，大屏上铺满会发虚，"
                        + "建议用 1920×1080 或更大的横向图片", 1, 6000);
                } else {
                    showToast("背景图已上传，点“保存设置”后生效");
                }
            })
            .catch(function (e) {
                spec.uploading = false;
                imgResult(kind, "上传失败：" + e.message, "error");
                showToast(label + "上传失败：" + e.message, 2, 0);
            });
    }

    /** 打开“选择图片”对话框（真正的 <input type="file"> 藏起来，界面上只留按钮） */
    function pickImg(kind) {
        if (LOGIN_IMG[kind].uploading) {
            showToast("上一张图片还在上传，请稍候", 1);
            return;
        }
        var input = byId(IMG_UI[kind].fileEl);
        input.value = "";
        input.click();
    }

    /** 清除某类图片：清掉当前地址（保存后登录页就不再显示，旧文件由服务端清理） */
    function clearImg(kind) {
        setImg(kind, "");
        imgResult(kind, kind === "bg"
            ? "已清除，点“保存设置”后登录页改用系统默认底色"
            : "已清除，点“保存设置”后登录页不再显示 logo");
    }

    /* ---------------- 登录框透明度（实时预览） ---------------- */

    /** 与后端 OaonesConst 同一口径：0 = 不透明，100 = 完全透明，默认 0（兼容历史配置）。 */
    var CARD_ALPHA = {
        min: <%=OaonesConst.LOGIN_BOX_OPACITY_MIN%>,
        max: <%=OaonesConst.LOGIN_BOX_OPACITY_MAX%>,
        defaultValue: <%=OaonesConst.LOGIN_BOX_OPACITY_DEFAULT%>
    };

    function cardAlphaResult(msg, mode) {
        var el = byId("cfgLoginBoxTransparencyResult");
        el.className = "cfg-logo-result" + (mode ? " " + mode : "");
        el.textContent = msg || "";
    }

    /** 读数字输入框：空串 = 默认 0（不透明）；非整数返回 null。 */
    function readCardAlphaInput() {
        var raw = String(byId("cfgLoginBoxTransparencyValue").value || "")
            .replace(/^\s+|\s+$/g, "");
        if (raw.length === 0) {
            return { blank: true, value: CARD_ALPHA.defaultValue };
        }
        if (!/^\d+$/.test(raw)) {
            return null;
        }
        var n = Number(raw);
        if (!isFinite(n)) {
            return null;
        }
        return { blank: false, value: Math.round(n) };
    }

    /** 透明度变化只更新控件与顶部共用预览；不调用保存接口。 */
    function renderCardAlphaPreview(value, blank) {
        var opacity = clampNumber(Math.round(Number(value) || 0), CARD_ALPHA.min, CARD_ALPHA.max);
        byId("cfgLoginBoxTransparency").value = String(opacity);
        byId("cfgLoginBoxTransparencyValue").title = blank
            ? "留空按默认 " + CARD_ALPHA.defaultValue + "%（不透明）" : "当前：" + opacity + "%";
        cardAlphaResult(opacity <= 0
            ? "当前不透明（默认）"
            : "当前透明度 " + opacity + "%，保存后登录页生效");
        renderUnifiedPreview();
    }

    /** 回显接口返回的透明度（历史行 NULL 时后端已按 0 返回）。 */
    function renderCardAlphaConfig(data) {
        CARD_ALPHA.min = Number(data.login_box_opacity_min) >= 0
            ? Number(data.login_box_opacity_min) : CARD_ALPHA.min;
        CARD_ALPHA.max = Number(data.login_box_opacity_max) > 0
            ? Number(data.login_box_opacity_max) : CARD_ALPHA.max;
        byId("cfgLoginBoxTransparency").setAttribute("min", String(CARD_ALPHA.min));
        byId("cfgLoginBoxTransparency").setAttribute("max", String(CARD_ALPHA.max));
        byId("cfgLoginBoxTransparencyValue").setAttribute("min", String(CARD_ALPHA.min));
        byId("cfgLoginBoxTransparencyValue").setAttribute("max", String(CARD_ALPHA.max));
        var value = Number(data.login_box_opacity);
        if (!isFinite(value)) {
            value = CARD_ALPHA.defaultValue;
        }
        value = clampNumber(Math.round(value), CARD_ALPHA.min, CARD_ALPHA.max);
        byId("cfgLoginBoxTransparency").value = String(value);
        byId("cfgLoginBoxTransparencyValue").value = String(value);
        renderCardAlphaPreview(value, false);
    }

    /* ---------------- 左侧文字背景透明度（实时预览） ---------------- */

    /** 0 = 不透明，100 = 全透明；默认 100 与历史“无底板”外观一致。 */
    var BRAND_ALPHA = {
        min: <%=OaonesConst.LOGIN_BRAND_OPACITY_MIN%>,
        max: <%=OaonesConst.LOGIN_BRAND_OPACITY_MAX%>,
        defaultValue: <%=OaonesConst.LOGIN_BRAND_OPACITY_DEFAULT%>
    };

    function brandAlphaResult(msg, mode) {
        var el = byId("cfgBrandTransparencyResult");
        el.className = "cfg-logo-result" + (mode ? " " + mode : "");
        el.textContent = msg || "";
    }

    /** 空串按默认 100（全透明）；非整数返回 null。 */
    function readBrandAlphaInput() {
        var raw = String(byId("cfgBrandTransparencyValue").value || "")
            .replace(/^\s+|\s+$/g, "");
        if (raw.length === 0) {
            return { blank: true, value: BRAND_ALPHA.defaultValue };
        }
        if (!/^\d+$/.test(raw)) {
            return null;
        }
        var n = Number(raw);
        if (!isFinite(n)) {
            return null;
        }
        return { blank: false, value: Math.round(n) };
    }

    function renderBrandAlphaPreview(value, blank) {
        var opacity = clampNumber(Math.round(Number(value) || 0), BRAND_ALPHA.min, BRAND_ALPHA.max);
        byId("cfgBrandTransparency").value = String(opacity);
        byId("cfgBrandTransparencyValue").title = blank
            ? "留空按默认 " + BRAND_ALPHA.defaultValue + "%（全透明）" : "当前：" + opacity + "%";
        brandAlphaResult(opacity >= 100
            ? "当前无底板（默认全透明）"
            : "当前透明度 " + opacity + "%，保存后登录页生效");
        renderUnifiedPreview();
    }

    /** 回显接口返回的透明度（历史行 NULL 时后端已按 100 返回）。 */
    function renderBrandAlphaConfig(data) {
        BRAND_ALPHA.min = Number(data.brand_opacity_min) >= 0
            ? Number(data.brand_opacity_min) : BRAND_ALPHA.min;
        BRAND_ALPHA.max = Number(data.brand_opacity_max) > 0
            ? Number(data.brand_opacity_max) : BRAND_ALPHA.max;
        byId("cfgBrandTransparency").setAttribute("min", String(BRAND_ALPHA.min));
        byId("cfgBrandTransparency").setAttribute("max", String(BRAND_ALPHA.max));
        byId("cfgBrandTransparencyValue").setAttribute("min", String(BRAND_ALPHA.min));
        byId("cfgBrandTransparencyValue").setAttribute("max", String(BRAND_ALPHA.max));
        var value = Number(data.brand_opacity);
        if (!isFinite(value)) {
            value = BRAND_ALPHA.defaultValue;
        }
        value = clampNumber(Math.round(value), BRAND_ALPHA.min, BRAND_ALPHA.max);
        byId("cfgBrandTransparency").value = String(value);
        byId("cfgBrandTransparencyValue").value = String(value);
        renderBrandAlphaPreview(value, false);
    }

    /* ---------------- 登录页左侧文字（富文本） ---------------- */

    /** 富文本编辑器实例（登录页左侧文字；值同步到隐藏域 cfgBrandHtml） */
    var brandEditor = OaonesRichText.init({
        mount: "cfgBrandBox",
        input: "cfgBrandHtml",
        placeholder: "在这里写登录页左侧要显示的文字（留空 = 用系统默认的左侧说明）",
        onChange: function () { renderUnifiedPreview(); }
    });

    function brandResult(msg, mode) {
        var el = byId("cfgBrandResult");
        el.className = "cfg-logo-result" + (mode ? " " + mode : "");
        el.textContent = msg || "";
    }

    /** 与后端 normalizeBrandHtml / 登录页同一口径：只有空标记 / &nbsp; 视为“没设置”。 */
    function brandHasText(html) {
        return String(html || "")
            .replace(/&nbsp;/g, "").replace(/\u00a0/g, "")
            .replace(/&#160;|&#xa0;/gi, "")
            .replace(/<[^>]*>/g, "").trim().length > 0;
    }

    /** 左侧文字回显：有内容就按它显示，没有就说明会用系统默认说明 */
    function renderBrand(html) {
        if (!brandEditor) { return; }
        brandEditor.setHtml(html || "");
        var has = brandHasText(html);
        byId("cfgBrandClearBtn").disabled = !has;
        brandResult(has ? "当前已是自定义左侧文字（保存后生效）"
            : "留空：登录页左侧用系统默认说明（标题 + 已开启登录方式的清单）");
        renderUnifiedPreview();
    }

    /** 恢复默认：清空编辑器（保存后登录页回到系统默认的左侧说明） */
    function clearBrand() {
        if (!brandEditor) { return; }
        brandEditor.setHtml("");
        byId("cfgBrandClearBtn").disabled = true;
        brandResult("已清空，点“保存设置”后登录页左侧恢复系统默认说明");
        renderUnifiedPreview();
    }

    /* ---------------- 登录框位置与尺寸（可视化拖动） ---------------- */

    /** 与后端 LoginConfigServiceImpl 的取值范围一致；login_box_* 返回值里也会带一份，页面优先用后端的。 */
    var LAYOUT_LIMITS = {
        minWidth: <%=OaonesConst.LOGIN_BOX_MIN_WIDTH%>, maxWidth: <%=OaonesConst.LOGIN_BOX_MAX_WIDTH%>,
        minHeight: <%=OaonesConst.LOGIN_BOX_MIN_HEIGHT%>, maxHeight: <%=OaonesConst.LOGIN_BOX_MAX_HEIGHT%>,
        maxPos: <%=OaonesConst.LOGIN_BOX_MAX_POS%>
    };
    /** 登录框与左侧文字共用的定位方式：0 = 左上角相对，1 = 区域中心相对视口中心。 */
    var LAYOUT_MODE_LEFT_TOP = 0;
    var LAYOUT_MODE_CENTER = 1;
    var layoutMode = LAYOUT_MODE_LEFT_TOP;
    /**
     * 布局预览状态：winWidth / winHeight 是“登录页实际会看到的浏览器视口”，
     * scale 是把视口等比缩到设置页预览框里的比例；left/top/width/height 始终存**实际像素**。
     */
    var layoutView = {
        winWidth: Math.max(861, document.documentElement.clientWidth || window.innerWidth || 1280),
        winHeight: Math.max(600, document.documentElement.clientHeight || window.innerHeight || 800),
        scale: 1,
        custom: false,
        viewportMode: "current",
        left: 0, top: 0, width: 400, height: 560,
        drag: null
    };

    /** 登录页左侧文字（文字说明）布局：与登录框同一套视口像素口径，宽 / 高下限更小。 */
    var BRAND_LIMITS = {
        minWidth: <%=OaonesConst.LOGIN_BRAND_MIN_WIDTH%>,
        maxWidth: <%=OaonesConst.LOGIN_BRAND_MAX_WIDTH%>,
        minHeight: <%=OaonesConst.LOGIN_BRAND_MIN_HEIGHT%>,
        maxHeight: <%=OaonesConst.LOGIN_BRAND_MAX_HEIGHT%>,
        maxPos: <%=OaonesConst.LOGIN_BRAND_MAX_POS%>
    };
    var brandLayoutView = {
        custom: false,
        left: 24, top: 64, width: 504, height: 240,
        drag: null,
        shown: null
    };

    function clampNumber(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function isCenterLayoutMode() {
        return layoutMode === LAYOUT_MODE_CENTER;
    }

    /**
     * 输入框 X / Y → 预览中的实际左上角矩形。
     * 左上角模式直接使用 X / Y；中心模式把“区域中心相对视口中心的偏移”还原为 left / top。
     */
    function boxFromLayoutValues(x, y, width, height) {
        var w = Math.round(width);
        var h = Math.round(height);
        var left = Math.round(x);
        var top = Math.round(y);
        if (isCenterLayoutMode()) {
            left = Math.round(layoutView.winWidth / 2 + x - w / 2);
            top = Math.round(layoutView.winHeight / 2 + y - h / 2);
        }
        return { x: Math.round(x), y: Math.round(y), left: left, top: top, width: w, height: h };
    }

    /** 预览中的实际矩形 → 当前定位方式下应回显 / 保存的 X / Y。 */
    function layoutValuesFromBox(box) {
        var width = Math.round(Number(box.width) || 0);
        var height = Math.round(Number(box.height) || 0);
        if (isCenterLayoutMode()) {
            return {
                x: Math.round(box.left + width / 2 - layoutView.winWidth / 2),
                y: Math.round(box.top + height / 2 - layoutView.winHeight / 2)
            };
        }
        return { x: Math.round(box.left), y: Math.round(box.top) };
    }

    function positionModeResult(msg, mode) {
        var el = byId("cfgLayoutModeResult");
        el.className = "cfg-logo-result" + (mode ? " " + mode : "");
        el.textContent = msg || "";
    }

    /** 两种定位方式共用一套 X / Y 输入框，只切换标签、允许的符号与说明。 */
    function applyLayoutModeUi() {
        var center = isCenterLayoutMode();
        byId("cfgLayoutModeLeftTop").checked = !center;
        byId("cfgLayoutModeCenter").checked = center;
        byId("cfgLoginBoxLeftLabel").textContent = center ? "相对中心水平偏移（X）" : "距页面左侧（X）";
        byId("cfgLoginBoxTopLabel").textContent = center ? "相对中心垂直偏移（Y）" : "距页面顶部（Y）";
        byId("cfgBrandLeftLabel").textContent = center ? "相对中心水平偏移（X）" : "距页面左侧（X）";
        byId("cfgBrandTopLabel").textContent = center ? "相对中心垂直偏移（Y）" : "距页面顶部（Y）";
        byId("cfgLoginBoxLeft").setAttribute("min", center ? String(-LAYOUT_LIMITS.maxPos) : "0");
        byId("cfgLoginBoxTop").setAttribute("min", center ? String(-LAYOUT_LIMITS.maxPos) : "0");
        byId("cfgBrandLeft").setAttribute("min", center ? String(-BRAND_LIMITS.maxPos) : "0");
        byId("cfgBrandTop").setAttribute("min", center ? String(-BRAND_LIMITS.maxPos) : "0");
        byId("cfgLayoutModeHint").innerHTML = center
            ? "当前为<b>中心点相对定位</b>：0,0 表示区域中心与视口中心重合；向右 / 向下为正，"
                + "向左 / 向上为负。登录框和左侧文字共同使用这一方式。"
            : "当前为<b>左上角相对定位</b>（保留现有方式）：X / Y 是区域左上角相对视口左上角的像素坐标。"
                + "登录框和左侧文字共同使用这一方式。";
        byId("cfgLoginBoxPositionTip").innerHTML = center
            ? "X / Y 是<b>登录框中心相对浏览器视口中心</b>的偏移，单位是 CSS 像素，可为负数；"
                + "不勾选自定义时仍使用系统默认居中布局。切换预览屏幕时中心点保持不变。"
            : "坐标以<b>浏览器可视区域左上角</b>为 0,0，单位是 CSS 像素；"
                + "不勾选自定义时登录框按系统默认布局<b>居中显示</b>。";
        byId("cfgBrandPositionTip").innerHTML = center
            ? "X / Y 是<b>文字区中心相对浏览器视口中心</b>的偏移，与登录框使用同一个定位方式；"
                + "可为负数，切换预览屏幕时中心点保持不变。"
            : "坐标与登录框一样以<b>浏览器可视区域左上角</b>为 0,0，使用同一个定位方式；"
                + "不勾选自定义时左侧文字跟随系统默认版式。";
        byId("cfgLayoutModeLeftTop").disabled = !CAN_MAINTAIN;
        byId("cfgLayoutModeCenter").disabled = !CAN_MAINTAIN;
    }

    /** 切换共用定位方式：以画面上现有矩形为基准换算 X / Y，避免切换后位置跳动。 */
    function setLayoutMode(nextMode, announce) {
        if (!CAN_MAINTAIN) { return; }
        nextMode = Number(nextMode) === LAYOUT_MODE_CENTER
            ? LAYOUT_MODE_CENTER : LAYOUT_MODE_LEFT_TOP;
        if (nextMode === layoutMode) {
            applyLayoutModeUi();
            return;
        }
        var cardBox = {
            left: layoutView.left, top: layoutView.top,
            width: layoutView.width, height: layoutView.height
        };
        var brandBox = {
            left: brandLayoutView.left, top: brandLayoutView.top,
            width: brandLayoutView.width, height: brandLayoutView.height
        };
        layoutMode = nextMode;
        writeLayoutInputs(cardBox);
        writeBrandLayoutInputs(brandBox);
        applyLayoutModeUi();
        positionModeResult(isCenterLayoutMode()
            ? "已选择中心点相对定位（登录框与左侧文字共用）"
            : "已选择左上角相对定位（登录框与左侧文字共用）");
        if (announce !== false) {
            renderUnifiedPreview();
        }
    }

    /** 默认布局（css/login.css 的 .login-page-default）：宽 400、在视口水平 / 垂直居中。 */
    function defaultLayoutBox() {
        var winW = layoutView.winWidth;
        var winH = layoutView.winHeight;
        var height = Math.max(LAYOUT_LIMITS.minHeight, Math.min(560, winH - 88));
        return {
            left: Math.max(0, Math.round((winW - 400) / 2)),
            top: Math.max(0, Math.round((winH - height) / 2)),
            width: 400,
            height: height
        };
    }

    /**
     * 左侧文字的默认版式（css/login.css）：不勾选自定义时它跟随登录框的默认 / 自定义布局。
     * 这里只给首屏兜底，预览加载完成后会用 iframe 里 .login-brand 的真实矩形覆盖。
     */
    function defaultBrandBox() {
        var winW = layoutView.winWidth;
        var winH = layoutView.winHeight;
        var height = Math.max(BRAND_LIMITS.minHeight, Math.min(240, winH - 160));
        if (layoutView.custom) {
            var left = Math.max(24, Math.round(winW / 2 - 476));
            return {
                left: left,
                top: 64,
                width: clampNumber(layoutView.left - left - 48, 200, 504),
                height: height
            };
        }
        return {
            left: 24,
            top: Math.max(0, Math.round((winH - height) / 2)),
            width: Math.min(504, Math.max(160, Math.round(winW / 2 - 248))),
            height: height
        };
    }

    function layoutResult(msg, mode) {
        var el = byId("cfgLoginBoxResult");
        el.className = "cfg-logo-result" + (mode ? " " + mode : "");
        el.textContent = msg || "";
    }

    /**
     * 读登录框四个数字输入框；X / Y 按当前定位方式解释成实际矩形。
     * 空值 / 非整数返回 null；左上角模式不允许负坐标。
     */
    function readLayoutInputs() {
        var ids = ["cfgLoginBoxLeft", "cfgLoginBoxTop", "cfgLoginBoxWidth", "cfgLoginBoxHeight"];
        var values = [];
        for (var i = 0; i < ids.length; i++) {
            var raw = String(byId(ids[i]).value || "").replace(/^\s+|\s+$/g, "");
            if (!/^-?\d+$/.test(raw)) {
                return null;
            }
            var n = Number(raw);
            if (!isFinite(n)) {
                return null;
            }
            values.push(Math.round(n));
        }
        if (!isCenterLayoutMode() && (values[0] < 0 || values[1] < 0)) {
            return null;
        }
        return boxFromLayoutValues(values[0], values[1], values[2], values[3]);
    }

    function writeLayoutInputs(box) {
        var values = layoutValuesFromBox(box);
        byId("cfgLoginBoxLeft").value = String(values.x);
        byId("cfgLoginBoxTop").value = String(values.y);
        byId("cfgLoginBoxWidth").value = String(Math.round(box.width));
        byId("cfgLoginBoxHeight").value = String(Math.round(box.height));
    }

    function setLayoutInputsDisabled() {
        var disabled = !CAN_MAINTAIN || !layoutView.custom;
        ["cfgLoginBoxLeft", "cfgLoginBoxTop", "cfgLoginBoxWidth", "cfgLoginBoxHeight"].forEach(function (id) {
            byId(id).disabled = disabled;
        });
        byId("cfgLoginBoxCustom").disabled = !CAN_MAINTAIN;
        byId("cfgLoginBoxResetBtn").disabled = !CAN_MAINTAIN;
    }

    /* ---------------- 文字说明（登录页左侧文字区）位置与尺寸 ---------------- */

    function brandLayoutResult(msg, mode) {
        var el = byId("cfgBrandLayoutResult");
        el.className = "cfg-logo-result" + (mode ? " " + mode : "");
        el.textContent = msg || "";
    }

    /** 读左侧文字四个数字输入框；与登录框共用定位方式，左上角模式不允许负坐标。 */
    function readBrandLayoutInputs() {
        var ids = ["cfgBrandLeft", "cfgBrandTop", "cfgBrandWidth", "cfgBrandHeight"];
        var values = [];
        for (var i = 0; i < ids.length; i++) {
            var raw = String(byId(ids[i]).value || "").replace(/^\s+|\s+$/g, "");
            if (!/^-?\d+$/.test(raw)) {
                return null;
            }
            var n = Number(raw);
            if (!isFinite(n)) {
                return null;
            }
            values.push(Math.round(n));
        }
        if (!isCenterLayoutMode() && (values[0] < 0 || values[1] < 0)) {
            return null;
        }
        return boxFromLayoutValues(values[0], values[1], values[2], values[3]);
    }

    function writeBrandLayoutInputs(box) {
        var values = layoutValuesFromBox(box);
        byId("cfgBrandLeft").value = String(values.x);
        byId("cfgBrandTop").value = String(values.y);
        byId("cfgBrandWidth").value = String(Math.round(box.width));
        byId("cfgBrandHeight").value = String(Math.round(box.height));
    }

    function setBrandLayoutInputsDisabled() {
        var disabled = !CAN_MAINTAIN || !brandLayoutView.custom;
        ["cfgBrandLeft", "cfgBrandTop", "cfgBrandWidth", "cfgBrandHeight"].forEach(function (id) {
            byId(id).disabled = disabled;
        });
        byId("cfgBrandCustom").disabled = !CAN_MAINTAIN;
        byId("cfgBrandResetBtn").disabled = !CAN_MAINTAIN;
    }

    /** 勾选 / 取消“自定义位置与尺寸”：取消只改模式，不清掉已填数值（重新勾选可继续用）。 */
    function setBrandLayoutCustom(on, seedDefault) {
        if (!CAN_MAINTAIN) { return; }
        brandLayoutView.custom = !!on;
        if (brandLayoutView.custom) {
            var current = readBrandLayoutInputs();
            if (!current || seedDefault) {
                var def = brandLayoutView.shown || defaultBrandBox();
                brandLayoutView.left = Math.round(def.left);
                brandLayoutView.top = Math.round(def.top);
                brandLayoutView.width = Math.round(def.width);
                brandLayoutView.height = Math.round(def.height);
                writeBrandLayoutInputs(brandLayoutView);
            } else {
                brandLayoutView.left = current.left;
                brandLayoutView.top = current.top;
                brandLayoutView.width = current.width;
                brandLayoutView.height = current.height;
            }
            brandLayoutResult("自定义版式已启用，点“保存设置”后生效");
        } else {
            brandLayoutResult("未启用自定义版式：左侧文字跟随系统默认版式");
        }
        byId("cfgBrandCustom").checked = brandLayoutView.custom;
        setBrandLayoutInputsDisabled();
        renderUnifiedPreview();
    }

    /** 输入框改动：能读全四个数字就即时刷新预览（不强行写回输入框，避免打断输入）。 */
    function applyBrandLayoutInputs() {
        var box = readBrandLayoutInputs();
        if (!box) {
            brandLayoutResult("X、Y、宽度、高度四项都要填整数像素（左上角模式不能为负）", "error");
            return;
        }
        brandLayoutView.left = box.left;
        brandLayoutView.top = box.top;
        brandLayoutView.width = box.width;
        brandLayoutView.height = box.height;
        brandLayoutResult("");
        renderUnifiedPreview();
    }

    /** 拖动 / 缩放结束后把结果写回输入框并做一次范围收敛。 */
    function normalizeBrandAfterDrag() {
        brandLayoutView.left = clampNumber(Math.round(brandLayoutView.left), 0, BRAND_LIMITS.maxPos);
        brandLayoutView.top = clampNumber(Math.round(brandLayoutView.top), 0, BRAND_LIMITS.maxPos);
        brandLayoutView.width = clampNumber(Math.round(brandLayoutView.width),
            BRAND_LIMITS.minWidth, BRAND_LIMITS.maxWidth);
        brandLayoutView.height = clampNumber(Math.round(brandLayoutView.height),
            BRAND_LIMITS.minHeight, BRAND_LIMITS.maxHeight);
        writeBrandLayoutInputs(brandLayoutView);
        brandLayoutResult("已调整文字说明位置 / 尺寸，点“保存设置”后生效");
        renderUnifiedPreview();
    }

    function beginBrandDrag(e, mode) {
        if (!CAN_MAINTAIN) { return; }
        if (!brandLayoutView.custom) {
            // 直接拖文字区 = 开始自定义：以画面上当前的文字区矩形为起点
            setBrandLayoutCustom(true, true);
        }
        e.preventDefault();
        brandLayoutView.drag = {
            mode: mode,
            pointerX: e.clientX,
            pointerY: e.clientY,
            left: brandLayoutView.left,
            top: brandLayoutView.top,
            width: brandLayoutView.width,
            height: brandLayoutView.height
        };
        byId("brandPreview").classList.add("active");
    }

    function moveBrandDrag(e) {
        var drag = brandLayoutView.drag;
        if (!drag) { return; }
        var dx = (e.clientX - drag.pointerX) / layoutView.scale;
        var dy = (e.clientY - drag.pointerY) / layoutView.scale;
        if (drag.mode === "move") {
            var maxLeft = Math.max(0, layoutView.winWidth - brandLayoutView.width);
            var maxTop = Math.max(0, layoutView.winHeight - brandLayoutView.height);
            brandLayoutView.left = clampNumber(Math.round(drag.left + dx), 0, maxLeft);
            brandLayoutView.top = clampNumber(Math.round(drag.top + dy), 0, maxTop);
        } else {
            var maxWidth = Math.max(BRAND_LIMITS.minWidth,
                Math.min(BRAND_LIMITS.maxWidth, layoutView.winWidth - brandLayoutView.left));
            var maxHeight = Math.max(BRAND_LIMITS.minHeight,
                Math.min(BRAND_LIMITS.maxHeight, layoutView.winHeight - brandLayoutView.top));
            brandLayoutView.width = clampNumber(Math.round(drag.width + dx),
                BRAND_LIMITS.minWidth, maxWidth);
            brandLayoutView.height = clampNumber(Math.round(drag.height + dy),
                BRAND_LIMITS.minHeight, maxHeight);
        }
        renderUnifiedPreview();
    }

    function endBrandDrag() {
        if (!brandLayoutView.drag) { return; }
        brandLayoutView.drag = null;
        normalizeBrandAfterDrag();
    }

    /** 预览 iframe 里的登录页接口（同源）：设置页只负责把当前表单值传进去。 */
    var previewRetryPending = false;
    var previewRetryCount = 0;
    function previewApi() {
        try {
            var frame = byId("loginPreviewFrame");
            return frame && frame.contentWindow ? frame.contentWindow.OaonesLoginPreview : null;
        } catch (e) {
            return null;
        }
    }

    /** iframe 还没加载完时短间隔重试一次，避免 load 事件在网络极快时先于脚本注册发生。 */
    function retryPreviewWhenReady() {
        if (previewRetryPending || previewRetryCount >= 40) { return; }
        previewRetryPending = true;
        previewRetryCount++;
        window.setTimeout(function () {
            previewRetryPending = false;
            renderUnifiedPreview();
        }, 150);
    }

    /** 预览里的图片只认同本模块上传后的站内地址（与登录页服务端校验同一口径）。 */
    function previewImageUrl(url, prefix) {
        var value = String(url || "");
        var base = "/oaones/appsyn/img/";
        if (value.indexOf(base) !== 0) { return ""; }
        var name = value.substring(base.length);
        if (name.indexOf(prefix) !== 0 || name.indexOf("..") >= 0
                || !/^[A-Za-z0-9_.-]+$/.test(name)) {
            return "";
        }
        return value;
    }

    function previewMethods() {
        return {
            password: byId("cfgPasswordEnable").checked,
            mobile: byId("cfgMobileEnable").checked,
            email: byId("cfgEmailEnable").checked,
            scan: byId("cfgWecomQrEnable").checked || byId("cfgDingtalkQrEnable").checked
                || byId("cfgFeishuQrEnable").checked,
            personalWx: byId("cfgPersonalWxEnable").checked && personalWxConfigured
        };
    }

    function previewScanText() {
        var labels = [];
        if (byId("cfgWecomQrEnable").checked) { labels.push("企业微信"); }
        if (byId("cfgDingtalkQrEnable").checked) { labels.push("钉钉"); }
        if (byId("cfgFeishuQrEnable").checked) { labels.push("飞书"); }
        return labels.join(" / ");
    }

    /** 当前表单值（含尚未保存的修改）→ 整页预览。 */
    function currentPreviewSettings() {
        var opacity = Number(byId("cfgLoginBoxTransparency").value);
        if (!isFinite(opacity)) { opacity = CARD_ALPHA.defaultValue; }
        var brandOpacity = Number(byId("cfgBrandTransparency").value);
        if (!isFinite(brandOpacity)) { brandOpacity = BRAND_ALPHA.defaultValue; }
        // preview.jsp 与正式登录页同样按“当前定位方式”解释 X / Y，因此这里传当前方式下的坐标。
        var cardPosition = layoutValuesFromBox(layoutView);
        var brandPosition = layoutValuesFromBox(brandLayoutView);
        return {
            brandHtml: brandEditor ? brandEditor.getHtml() : byId("cfgBrandHtml").value,
            logo: previewImageUrl(byId("cfgLogo").value, "loginlogo_"),
            bg: previewImageUrl(byId("cfgBgImage").value, "loginbg_"),
            opacity: clampNumber(Math.round(opacity), CARD_ALPHA.min, CARD_ALPHA.max),
            brandOpacity: clampNumber(Math.round(brandOpacity), BRAND_ALPHA.min, BRAND_ALPHA.max),
            layoutMode: layoutMode,
            custom: layoutView.custom,
            left: cardPosition.x,
            top: cardPosition.y,
            width: layoutView.width,
            height: layoutView.height,
            brandCustom: brandLayoutView.custom,
            brandLeft: brandPosition.x,
            brandTop: brandPosition.y,
            brandWidth: brandLayoutView.width,
            brandHeight: brandLayoutView.height,
            methods: previewMethods(),
            scanText: previewScanText()
        };
    }

    /** 按当前视口把整页预览等比缩放到面板里：iframe 内部仍是真实的 W×H 像素。 */
    function sizePreviewStage() {
        var stage = byId("loginBoxStage");
        var frame = byId("loginPreviewFrame");
        var panel = stage.parentNode;
        var availW = Math.max(260, Math.min(560,
            (panel.clientWidth || 900) - 28));
        var availH = Math.max(220, Math.min(480,
            (window.innerHeight || 900) - 210));
        var scale = Math.min(availW / layoutView.winWidth, availH / layoutView.winHeight, 1);
        layoutView.scale = Math.max(0.08, scale);
        stage.style.width = Math.round(layoutView.winWidth * layoutView.scale) + "px";
        stage.style.height = Math.round(layoutView.winHeight * layoutView.scale) + "px";
        frame.style.width = layoutView.winWidth + "px";
        frame.style.height = layoutView.winHeight + "px";
        frame.style.transform = "scale(" + layoutView.scale + ")";
    }

    /**
     * 切换预览屏幕：管理员的当前窗口不一定等于员工实际用的屏幕（常见 1920×1080），
     * 选一个参考分辨率后，预览仍按实际像素等比缩放；坐标保存的仍是实际 CSS 像素，与预览比例无关。
     */
    function applyLayoutViewport() {
        var mode = String(byId("cfgLoginBoxViewport").value || "current");
        // 中心点相对定位不随分辨率改变偏移量：换参考屏幕时保留 X / Y，只按新视口重新换算实际矩形。
        var centerCard = isCenterLayoutMode() && layoutView.custom
            ? layoutValuesFromBox(layoutView) : null;
        var centerBrand = isCenterLayoutMode() && brandLayoutView.custom
            ? layoutValuesFromBox(brandLayoutView) : null;
        layoutView.viewportMode = mode;
        if (mode === "current") {
            layoutView.winWidth = Math.max(861, document.documentElement.clientWidth || window.innerWidth || 1280);
            layoutView.winHeight = Math.max(600, document.documentElement.clientHeight || window.innerHeight || 800);
        } else {
            var parts = mode.split("x");
            layoutView.winWidth = Math.max(861, Number(parts[0]) || 1280);
            layoutView.winHeight = Math.max(600, Number(parts[1]) || 720);
        }
        if (!layoutView.custom) {
            var def = defaultLayoutBox();
            layoutView.left = def.left;
            layoutView.top = def.top;
            layoutView.width = def.width;
            layoutView.height = def.height;
        } else if (centerCard) {
            var cardBox = boxFromLayoutValues(centerCard.x, centerCard.y,
                layoutView.width, layoutView.height);
            layoutView.left = cardBox.left;
            layoutView.top = cardBox.top;
        }
        if (!brandLayoutView.custom) {
            var brandDef = defaultBrandBox();
            brandLayoutView.left = brandDef.left;
            brandLayoutView.top = brandDef.top;
            brandLayoutView.width = brandDef.width;
            brandLayoutView.height = brandDef.height;
        } else if (centerBrand) {
            var brandBox = boxFromLayoutValues(centerBrand.x, centerBrand.y,
                brandLayoutView.width, brandLayoutView.height);
            brandLayoutView.left = brandBox.left;
            brandLayoutView.top = brandBox.top;
        }
        writeLayoutInputs(layoutView);
        writeBrandLayoutInputs(brandLayoutView);
        applyLayoutModeUi();
        sizePreviewStage();
        renderUnifiedPreview();
    }

    /**
     * 所有设置共用的整页预览：把当前表单值交给 preview.jsp 重画，
     * 再用 iframe 里登录框的真实矩形摆放拖动 / 缩放手柄（不必为每个设置各画一块小预览）。
     */
    function renderUnifiedPreview() {
        if (!CAN_MAINTAIN) { return; }
        var api = previewApi();
        if (!api) {
            retryPreviewWhenReady();
            return;
        }
        previewRetryCount = 0;
        api.apply(currentPreviewSettings());

        var scale = layoutView.scale;
        var shown = layoutView.custom ? layoutView : defaultLayoutBox();
        var shownBrand = brandLayoutView.custom ? brandLayoutView : defaultBrandBox();
        // 预览页加载完成后，以 iframe 里登录框的实际矩形为准（默认布局也能量准）
        try {
            var doc = byId("loginPreviewFrame").contentDocument;
            var card = doc ? doc.getElementById("previewCard") : null;
            if (card) {
                var rect = card.getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0) {
                    shown = {
                        left: rect.left, top: rect.top,
                        width: rect.width, height: rect.height
                    };
                    if (!layoutView.custom) {
                        layoutView.left = Math.round(shown.left);
                        layoutView.top = Math.round(shown.top);
                        layoutView.width = Math.round(shown.width);
                        layoutView.height = Math.round(shown.height);
                        writeLayoutInputs(layoutView);
                    }
                }
            }
            var brand = doc ? doc.getElementById("previewBrand") : null;
            if (brand) {
                var brandRect = brand.getBoundingClientRect();
                if (brandRect.width > 0 && brandRect.height > 0) {
                    shownBrand = {
                        left: brandRect.left, top: brandRect.top,
                        width: brandRect.width, height: brandRect.height
                    };
                    if (!brandLayoutView.custom) {
                        brandLayoutView.left = Math.round(shownBrand.left);
                        brandLayoutView.top = Math.round(shownBrand.top);
                        brandLayoutView.width = Math.round(shownBrand.width);
                        brandLayoutView.height = Math.round(shownBrand.height);
                        writeBrandLayoutInputs(brandLayoutView);
                    }
                }
            }
        } catch (e) {
            // iframe 尚未加载完成时沿用输入框里的值；加载完成后会再画一次
        }
        layoutView.shown = shown;
        brandLayoutView.shown = shownBrand;

        var drag = byId("loginBoxPreview");
        var resize = byId("loginBoxResize");
        drag.style.left = Math.round(shown.left * scale) + "px";
        drag.style.top = Math.round(shown.top * scale) + "px";
        drag.style.width = Math.round(shown.width * scale) + "px";
        drag.style.height = Math.max(8, Math.round(12 * scale)) + "px";
        drag.style.display = "";
        resize.style.left = Math.round((shown.left + shown.width) * scale - 18) + "px";
        resize.style.top = Math.round((shown.top + shown.height) * scale - 18) + "px";
        resize.style.display = "";
        var brandDrag = byId("brandPreview");
        var brandResize = byId("brandResize");
        brandDrag.style.left = Math.round(shownBrand.left * scale) + "px";
        brandDrag.style.top = Math.round(shownBrand.top * scale) + "px";
        brandDrag.style.width = Math.round(shownBrand.width * scale) + "px";
        brandDrag.style.height = Math.max(8, Math.round(12 * scale)) + "px";
        brandDrag.style.display = "";
        brandResize.style.left = Math.round((shownBrand.left + shownBrand.width) * scale - 18) + "px";
        brandResize.style.top = Math.round((shownBrand.top + shownBrand.height) * scale - 18) + "px";
        brandResize.style.display = "";
        byId("loginBoxStageInfo").textContent = "预览窗口 " + layoutView.winWidth + "×"
            + layoutView.winHeight + "，缩放 " + Math.round(scale * 100) + "%"
            + "；拖动虚线框可调登录框 / 文字说明位置，拖右下角可改宽高";
        var cardOverflow = layoutView.custom
            && (shown.left + shown.width > layoutView.winWidth
                || shown.top + shown.height > layoutView.winHeight);
        var brandOverflow = brandLayoutView.custom
            && (shownBrand.left + shownBrand.width > layoutView.winWidth
                || shownBrand.top + shownBrand.height > layoutView.winHeight);
        var warn = byId("loginBoxStageWarn");
        warn.className = (cardOverflow || brandOverflow) ? "warn" : "";
        warn.textContent = (cardOverflow || brandOverflow)
            ? "已超出当前预览窗口，实际以登录页的浏览器窗口为准" : "";
    }

    /** 勾选 / 取消“自定义位置与尺寸”：取消只改模式，不清掉已填的数值（重新勾选可继续用）。 */
    function setLayoutCustom(on, seedDefault) {
        if (!CAN_MAINTAIN) { return; }
        layoutView.custom = !!on;
        if (layoutView.custom) {
            var current = readLayoutInputs();
            if (!current || seedDefault) {
                // 直接拖预览里的登录框时，以这一刻画面上的实际矩形为起点，避免跳一下
                var def = layoutView.shown || defaultLayoutBox();
                layoutView.left = def.left;
                layoutView.top = def.top;
                layoutView.width = def.width;
                layoutView.height = def.height;
                writeLayoutInputs(def);
            } else {
                layoutView.left = current.left;
                layoutView.top = current.top;
                layoutView.width = current.width;
                layoutView.height = current.height;
            }
            layoutResult("自定义布局已启用，点“保存设置”后生效");
        } else {
            layoutResult("未启用自定义布局：登录页使用系统默认的居中布局");
        }
        byId("cfgLoginBoxCustom").checked = layoutView.custom;
        setLayoutInputsDisabled();
        renderUnifiedPreview();
    }

    /** 输入框改动：能读全四个数字就即时刷新预览（不强行写回输入框，避免打断输入）。 */
    function applyLayoutInputs() {
        var box = readLayoutInputs();
        if (!box) {
            layoutResult("X、Y、宽度、高度四项都要填整数像素（左上角模式不能为负）", "error");
            return;
        }
        layoutView.left = box.left;
        layoutView.top = box.top;
        layoutView.width = box.width;
        layoutView.height = box.height;
        layoutResult("");
        renderUnifiedPreview();
    }

    /** 拖动 / 缩放结束后把结果写回输入框并做一次范围收敛。 */
    function normalizeLayoutAfterDrag() {
        layoutView.left = clampNumber(Math.round(layoutView.left), 0, LAYOUT_LIMITS.maxPos);
        layoutView.top = clampNumber(Math.round(layoutView.top), 0, LAYOUT_LIMITS.maxPos);
        layoutView.width = clampNumber(Math.round(layoutView.width),
            LAYOUT_LIMITS.minWidth, LAYOUT_LIMITS.maxWidth);
        layoutView.height = clampNumber(Math.round(layoutView.height),
            LAYOUT_LIMITS.minHeight, LAYOUT_LIMITS.maxHeight);
        writeLayoutInputs(layoutView);
        layoutResult("已调整位置 / 尺寸，点“保存设置”后生效");
        renderUnifiedPreview();
    }

    function beginLayoutDrag(e, mode) {
        if (!CAN_MAINTAIN) { return; }
        if (!layoutView.custom) {
            // 直接拖白框 = 开始自定义：先用默认布局做起点，拖起来更自然
            setLayoutCustom(true, true);
        }
        e.preventDefault();
        layoutView.drag = {
            mode: mode,
            pointerX: e.clientX,
            pointerY: e.clientY,
            left: layoutView.left,
            top: layoutView.top,
            width: layoutView.width,
            height: layoutView.height
        };
        byId("loginBoxPreview").classList.add("active");
    }

    function moveLayoutDrag(e) {
        var drag = layoutView.drag;
        if (!drag) { return; }
        var dx = (e.clientX - drag.pointerX) / layoutView.scale;
        var dy = (e.clientY - drag.pointerY) / layoutView.scale;
        if (drag.mode === "move") {
            var maxLeft = Math.max(0, layoutView.winWidth - layoutView.width);
            var maxTop = Math.max(0, layoutView.winHeight - layoutView.height);
            layoutView.left = clampNumber(Math.round(drag.left + dx), 0, maxLeft);
            layoutView.top = clampNumber(Math.round(drag.top + dy), 0, maxTop);
        } else {
            var maxWidth = Math.max(LAYOUT_LIMITS.minWidth,
                Math.min(LAYOUT_LIMITS.maxWidth, layoutView.winWidth - layoutView.left));
            var maxHeight = Math.max(LAYOUT_LIMITS.minHeight,
                Math.min(LAYOUT_LIMITS.maxHeight, layoutView.winHeight - layoutView.top));
            layoutView.width = clampNumber(Math.round(drag.width + dx),
                LAYOUT_LIMITS.minWidth, maxWidth);
            layoutView.height = clampNumber(Math.round(drag.height + dy),
                LAYOUT_LIMITS.minHeight, maxHeight);
        }
        renderUnifiedPreview();
    }

    function endLayoutDrag() {
        if (!layoutView.drag) { return; }
        layoutView.drag = null;
        normalizeLayoutAfterDrag();
    }

    /** 回显接口返回的登录框布局：四列都有值才启用，否则按默认布局显示。 */
    function renderLoginBoxLayout(data) {
        // 定位方式是全局配置，必须先于登录框 / 左侧文字坐标解释。
        layoutMode = Number(data.login_layout_mode) === LAYOUT_MODE_CENTER
            ? LAYOUT_MODE_CENTER : LAYOUT_MODE_LEFT_TOP;
        LAYOUT_LIMITS.minWidth = Number(data.login_box_min_width) > 0
            ? Number(data.login_box_min_width) : LAYOUT_LIMITS.minWidth;
        LAYOUT_LIMITS.maxWidth = Number(data.login_box_max_width) > 0
            ? Number(data.login_box_max_width) : LAYOUT_LIMITS.maxWidth;
        LAYOUT_LIMITS.minHeight = Number(data.login_box_min_height) > 0
            ? Number(data.login_box_min_height) : LAYOUT_LIMITS.minHeight;
        LAYOUT_LIMITS.maxHeight = Number(data.login_box_max_height) > 0
            ? Number(data.login_box_max_height) : LAYOUT_LIMITS.maxHeight;
        LAYOUT_LIMITS.maxPos = Number(data.login_box_max_pos) > 0
            ? Number(data.login_box_max_pos) : LAYOUT_LIMITS.maxPos;
        byId("cfgLoginBoxWidth").setAttribute("min", String(LAYOUT_LIMITS.minWidth));
        byId("cfgLoginBoxWidth").setAttribute("max", String(LAYOUT_LIMITS.maxWidth));
        byId("cfgLoginBoxHeight").setAttribute("min", String(LAYOUT_LIMITS.minHeight));
        byId("cfgLoginBoxHeight").setAttribute("max", String(LAYOUT_LIMITS.maxHeight));
        byId("cfgLoginBoxLeft").setAttribute("max", String(LAYOUT_LIMITS.maxPos));
        byId("cfgLoginBoxTop").setAttribute("max", String(LAYOUT_LIMITS.maxPos));

        layoutView.custom = !!data.login_box_custom;
        if (layoutView.custom) {
            var box = boxFromLayoutValues(Number(data.login_box_left) || 0,
                Number(data.login_box_top) || 0,
                Number(data.login_box_width) || 400,
                Number(data.login_box_height) || 560);
            layoutView.left = box.left;
            layoutView.top = box.top;
            layoutView.width = box.width;
            layoutView.height = box.height;
        } else {
            var def = defaultLayoutBox();
            layoutView.left = def.left;
            layoutView.top = def.top;
            layoutView.width = def.width;
            layoutView.height = def.height;
        }
        writeLayoutInputs(layoutView);
        byId("cfgLoginBoxCustom").checked = layoutView.custom;
        applyLayoutModeUi();
        setLayoutInputsDisabled();
        sizePreviewStage();
        renderUnifiedPreview();
        layoutResult(layoutView.custom
            ? "当前为自定义布局（保存后生效）"
            : "当前为系统默认居中布局");
    }

    /** 回显接口返回的左侧文字布局：四列都有值才启用，否则按默认版式显示。 */
    function renderBrandLayout(data) {
        BRAND_LIMITS.minWidth = Number(data.brand_min_width) > 0
            ? Number(data.brand_min_width) : BRAND_LIMITS.minWidth;
        BRAND_LIMITS.maxWidth = Number(data.brand_max_width) > 0
            ? Number(data.brand_max_width) : BRAND_LIMITS.maxWidth;
        BRAND_LIMITS.minHeight = Number(data.brand_min_height) > 0
            ? Number(data.brand_min_height) : BRAND_LIMITS.minHeight;
        BRAND_LIMITS.maxHeight = Number(data.brand_max_height) > 0
            ? Number(data.brand_max_height) : BRAND_LIMITS.maxHeight;
        BRAND_LIMITS.maxPos = Number(data.brand_max_pos) > 0
            ? Number(data.brand_max_pos) : BRAND_LIMITS.maxPos;
        byId("cfgBrandWidth").setAttribute("min", String(BRAND_LIMITS.minWidth));
        byId("cfgBrandWidth").setAttribute("max", String(BRAND_LIMITS.maxWidth));
        byId("cfgBrandHeight").setAttribute("min", String(BRAND_LIMITS.minHeight));
        byId("cfgBrandHeight").setAttribute("max", String(BRAND_LIMITS.maxHeight));
        byId("cfgBrandLeft").setAttribute("max", String(BRAND_LIMITS.maxPos));
        byId("cfgBrandTop").setAttribute("max", String(BRAND_LIMITS.maxPos));

        brandLayoutView.custom = !!data.brand_custom;
        if (brandLayoutView.custom) {
            var brandBox = boxFromLayoutValues(Number(data.brand_left) || 0,
                Number(data.brand_top) || 0,
                Number(data.brand_width) || 504,
                Number(data.brand_height) || 240);
            brandLayoutView.left = brandBox.left;
            brandLayoutView.top = brandBox.top;
            brandLayoutView.width = brandBox.width;
            brandLayoutView.height = brandBox.height;
        } else {
            var def = defaultBrandBox();
            brandLayoutView.left = def.left;
            brandLayoutView.top = def.top;
            brandLayoutView.width = def.width;
            brandLayoutView.height = def.height;
        }
        writeBrandLayoutInputs(brandLayoutView);
        byId("cfgBrandCustom").checked = brandLayoutView.custom;
        applyLayoutModeUi();
        setBrandLayoutInputsDisabled();
        renderUnifiedPreview();
        brandLayoutResult(brandLayoutView.custom
            ? "当前为自定义版式（保存后生效）"
            : "当前跟随系统默认版式");
    }

    function renderConfig(data) {
        data = data || {};
        setChecked("cfgPasswordEnable", data.password_enable);
        setChecked("cfgMobileEnable", data.mobile_enable);
        setChecked("cfgEmailEnable", data.email_enable);
        setChecked("cfgWecomQrEnable", data.wecom_qr_enable);
        setChecked("cfgDingtalkQrEnable", data.dingtalk_qr_enable);
        setChecked("cfgFeishuQrEnable", data.feishu_qr_enable);
        setChecked("cfgPersonalWxEnable", data.personal_wx_enable);
        personalWxConfigured = data.personal_wx_configured === true;
        byId("cfgPersonalWxState").textContent = Number(data.personal_wx_enable) === 1
            ? (personalWxConfigured
                ? "（微信授权密钥已配置）"
                : "（微信授权密钥未配置，保存后仍不会对员工显示）")
            : "";
        defaultTtl = Number(data.qr_ttl_effective) > 0 ? Number(data.qr_ttl_effective) : 0;
        byId("cfgQrTtlDefault").textContent = defaultTtl > 0 ? String(defaultTtl) : "-";
        var ttl = Number(data.qr_ttl_seconds);
        byId("cfgQrTtl").value = ttl > 0 ? String(ttl) : "";
        setImg("logo", data.logo || "");
        imgResult("logo", String(data.logo || "").length > 0 ? "当前 logo（点“上传 logo”可换一张）" : "");
        setImg("bg", data.bg_image || "");
        imgResult("bg", String(data.bg_image || "").length > 0
            ? "当前背景图（点“上传背景图”可换一张）" : "");
        renderCardAlphaConfig(data);
        renderBrandAlphaConfig(data);
        // 左侧富文本：库里有内容就按它回显，空则说明用系统默认说明
        renderBrand(data.brand_html || "");
        // 登录框布局（与其它设置共用右侧浮动的整页效果预览）
        renderLoginBoxLayout(data);
        // 左侧文字位置与尺寸：在登录框布局之后回显，默认版式按登录框的实际布局计算
        renderBrandLayout(data);
        byId("cfgRemark").value = data.remark || "";
        byId("cfgUpdateInfo").textContent = (data.update_time || "-")
            + (data.update_by ? "（人员ID " + data.update_by + "）" : "");
    }

    function loadConfig() {
        if (!CAN_MAINTAIN) {
            // 非维护人：不调接口（接口也会拒绝），只把字段置为只读，避免看到一份“全关”的假象
            disableAll();
            return;
        }
        Oaones.api("logincfg.get", {}, function (res) {
            if (res.code !== 0) {
                showToast(res.msg || "读取登录设置失败", 2, 0);
                return;
            }
            renderConfig(res.data || {});
        });
    }

    /** 非维护人：字段全部只读、禁用保存 */
    function disableAll() {
        ["cfgPasswordEnable", "cfgMobileEnable", "cfgEmailEnable", "cfgWecomQrEnable", "cfgDingtalkQrEnable",
            "cfgFeishuQrEnable", "cfgPersonalWxEnable", "cfgQrTtl", "cfgRemark"].forEach(function (id) {
            byId(id).disabled = true;
        });
        byId("cfgLogoUploadBtn").disabled = true;
        byId("cfgLogoClearBtn").disabled = true;
        // 图片与左侧文字都是维护动作：非维护人一并禁用（后端 uploadImage / save 会再判一次权限）
        imgResult("logo", "无维护权限，不展示当前 logo");
        imgResult("bg", "无维护权限，不展示当前背景图");
        byId("cfgBgUploadBtn").disabled = true;
        byId("cfgBgClearBtn").disabled = true;
        byId("cfgLoginBoxTransparency").disabled = true;
        byId("cfgLoginBoxTransparencyValue").disabled = true;
        byId("cfgLoginBoxTransparencyResetBtn").disabled = true;
        byId("cfgBrandTransparency").disabled = true;
        byId("cfgBrandTransparencyValue").disabled = true;
        byId("cfgBrandTransparencyResetBtn").disabled = true;
        byId("loginPreviewFrame").style.visibility = "hidden";
        byId("loginBoxPreview").style.display = "none";
        byId("loginBoxResize").style.display = "none";
        byId("brandPreview").style.display = "none";
        byId("brandResize").style.display = "none";
        byId("loginBoxStageInfo").textContent = "无维护权限，不展示效果预览";
        byId("loginBoxStageWarn").textContent = "";
        cardAlphaResult("无维护权限，不展示当前透明度");
        brandAlphaResult("无维护权限，不展示当前左侧文字背景透明度");
        if (brandEditor) {
            brandEditor.setDisabled(true);
        }
        byId("cfgBrandClearBtn").disabled = true;
        brandResult("无维护权限，不展示当前左侧文字");
        byId("cfgLayoutModeLeftTop").disabled = true;
        byId("cfgLayoutModeCenter").disabled = true;
        positionModeResult("无维护权限，不展示当前定位方式");
        // 登录框布局同样是维护动作：全部禁用，并明确说明不展示当前值
        byId("cfgLoginBoxCustom").disabled = true;
        ["cfgLoginBoxLeft", "cfgLoginBoxTop", "cfgLoginBoxWidth", "cfgLoginBoxHeight"].forEach(function (id) {
            byId(id).disabled = true;
            byId(id).value = "";
        });
        byId("cfgLoginBoxResetBtn").disabled = true;
        byId("cfgLoginBoxViewport").disabled = true;
        layoutResult("无维护权限，不展示当前登录框布局");
        // 文字说明布局同样是维护动作：全部禁用，并明确说明不展示当前值
        byId("cfgBrandCustom").disabled = true;
        ["cfgBrandLeft", "cfgBrandTop", "cfgBrandWidth", "cfgBrandHeight"].forEach(function (id) {
            byId(id).disabled = true;
            byId(id).value = "";
        });
        byId("cfgBrandResetBtn").disabled = true;
        brandLayoutResult("无维护权限，不展示当前文字说明布局");
        byId("loginCfgSaveBtn").disabled = true;
        byId("cfgUpdateInfo").textContent = "无维护权限，不展示当前设置";
    }

    function saveConfig() {
        if (!CAN_MAINTAIN) {
            showToast("你不是登录设置的维护人，无权保存", 1);
            return;
        }
        // 前置校验与后端一致：账号密码 / 验证登录（手机号或邮箱）/ 扫码登录（至少允许一个平台）至少开启一种
        var usable = (byId("cfgPasswordEnable").checked ? 1 : 0)
            + (byId("cfgMobileEnable").checked ? 1 : 0)
            + (byId("cfgEmailEnable").checked ? 1 : 0)
            + (byId("cfgWecomQrEnable").checked ? 1 : 0)
            + (byId("cfgDingtalkQrEnable").checked ? 1 : 0)
            + (byId("cfgFeishuQrEnable").checked ? 1 : 0)
            + (byId("cfgPersonalWxEnable").checked ? 1 : 0);
        if (usable === 0) {
            showToast("账号密码登录、验证登录（手机号 / 邮箱）、扫码登录至少要开启一种"
                + "（企业应用扫码或个人微信扫码至少允许一种）", 1, 0);
            return;
        }
        var ttl = String(byId("cfgQrTtl").value || "").replace(/^\s+|\s+$/g, "");
        if (ttl.length > 0 && !/^\d+$/.test(ttl)) {
            showToast("扫码二维码有效期请填数字秒数（留空表示使用系统默认值）", 1);
            return;
        }
        if (ttl.length > 0 && (Number(ttl) < 30 || Number(ttl) > 180)) {
            showToast("扫码二维码有效期必须在 30 ~ 180 秒之间（最长 3 分钟；留空表示使用系统默认值）", 1);
            return;
        }
        // 登录框位置与尺寸：勾选自定义时必须四项齐全且在允许范围内（与后端校验同一口径）
        var boxParams = {
            login_box_left: "", login_box_top: "", login_box_width: "", login_box_height: ""
        };
        if (layoutView.custom) {
            var box = readLayoutInputs();
            if (!box) {
                showToast("登录框位置与尺寸要一起设置：X、Y、宽度、高度四项都填整数像素"
                    + "（左上角模式不能为负）", 1, 0);
                return;
            }
            if (box.width < LAYOUT_LIMITS.minWidth || box.width > LAYOUT_LIMITS.maxWidth) {
                showToast("登录框宽度必须在 " + LAYOUT_LIMITS.minWidth + " ~ "
                    + LAYOUT_LIMITS.maxWidth + " 像素之间", 1);
                return;
            }
            if (box.height < LAYOUT_LIMITS.minHeight || box.height > LAYOUT_LIMITS.maxHeight) {
                showToast("登录框高度必须在 " + LAYOUT_LIMITS.minHeight + " ~ "
                    + LAYOUT_LIMITS.maxHeight + " 像素之间", 1);
                return;
            }
            if (!isCenterLayoutMode()) {
                if (box.x + box.width > LAYOUT_LIMITS.maxPos
                        || box.y + box.height > LAYOUT_LIMITS.maxPos) {
                    showToast("登录框右侧 / 底部位置超出允许范围（请检查坐标与宽高）", 1);
                    return;
                }
            } else if (Math.abs(box.x) > LAYOUT_LIMITS.maxPos
                    || Math.abs(box.y) > LAYOUT_LIMITS.maxPos) {
                showToast("登录框中心相对视图中心的偏移超出允许范围（绝对值不能超过 "
                    + LAYOUT_LIMITS.maxPos + " 像素）", 1);
                return;
            }
            boxParams = {
                login_box_left: String(box.x),
                login_box_top: String(box.y),
                login_box_width: String(box.width),
                login_box_height: String(box.height)
            };
        }
        // 文字说明位置与尺寸：同样是勾选自定义时四项齐全且在允许范围内
        var brandParams = {
            brand_left: "", brand_top: "", brand_width: "", brand_height: ""
        };
        if (brandLayoutView.custom) {
            var brandBox = readBrandLayoutInputs();
            if (!brandBox) {
                showToast("文字说明位置与尺寸要一起设置：X、Y、宽度、高度四项都填整数像素"
                    + "（左上角模式不能为负）", 1, 0);
                return;
            }
            if (brandBox.width < BRAND_LIMITS.minWidth || brandBox.width > BRAND_LIMITS.maxWidth) {
                showToast("文字说明宽度必须在 " + BRAND_LIMITS.minWidth + " ~ "
                    + BRAND_LIMITS.maxWidth + " 像素之间", 1);
                return;
            }
            if (brandBox.height < BRAND_LIMITS.minHeight || brandBox.height > BRAND_LIMITS.maxHeight) {
                showToast("文字说明高度必须在 " + BRAND_LIMITS.minHeight + " ~ "
                    + BRAND_LIMITS.maxHeight + " 像素之间", 1);
                return;
            }
            if (!isCenterLayoutMode()) {
                if (brandBox.x + brandBox.width > BRAND_LIMITS.maxPos
                        || brandBox.y + brandBox.height > BRAND_LIMITS.maxPos) {
                    showToast("文字说明右侧 / 底部位置超出允许范围（请检查坐标与宽高）", 1);
                    return;
                }
            } else if (Math.abs(brandBox.x) > BRAND_LIMITS.maxPos
                    || Math.abs(brandBox.y) > BRAND_LIMITS.maxPos) {
                showToast("文字说明中心相对视图中心的偏移超出允许范围（绝对值不能超过 "
                    + BRAND_LIMITS.maxPos + " 像素）", 1);
                return;
            }
            brandParams = {
                brand_left: String(brandBox.x),
                brand_top: String(brandBox.y),
                brand_width: String(brandBox.width),
                brand_height: String(brandBox.height)
            };
        }
        // 登录框透明度：0 = 不透明，100 = 完全透明；空串按默认 0 处理
        var cardAlpha = readCardAlphaInput();
        if (!cardAlpha) {
            showToast("登录框透明度请填 0 ~ 100 的整数百分比（0 = 不透明，100 = 完全透明）", 1);
            return;
        }
        if (cardAlpha.value < CARD_ALPHA.min || cardAlpha.value > CARD_ALPHA.max) {
            showToast("登录框透明度必须在 " + CARD_ALPHA.min + " ~ " + CARD_ALPHA.max + " 之间", 1);
            return;
        }
        // 左侧文字背景透明度：0 = 不透明，100 = 全透明（默认）；空串按 100 处理
        var brandAlpha = readBrandAlphaInput();
        if (!brandAlpha) {
            showToast("左侧文字背景透明度请填 0 ~ 100 的整数百分比（0 = 不透明，100 = 全透明）", 1);
            return;
        }
        if (brandAlpha.value < BRAND_ALPHA.min || brandAlpha.value > BRAND_ALPHA.max) {
            showToast("左侧文字背景透明度必须在 " + BRAND_ALPHA.min + " ~ "
                + BRAND_ALPHA.max + " 之间", 1);
            return;
        }
        var btn = byId("loginCfgSaveBtn");
        btn.disabled = true;
        Oaones.api("logincfg.save", {
            password_enable: checked("cfgPasswordEnable"),
            mobile_enable: checked("cfgMobileEnable"),
            email_enable: checked("cfgEmailEnable"),
            wecom_qr_enable: checked("cfgWecomQrEnable"),
            dingtalk_qr_enable: checked("cfgDingtalkQrEnable"),
            feishu_qr_enable: checked("cfgFeishuQrEnable"),
            personal_wx_enable: checked("cfgPersonalWxEnable"),
            qr_ttl_seconds: ttl,
            // 空串 = 不显示 logo（后端按“空 = 清除”处理，并把不再被引用的旧文件删掉）
            logo: byId("cfgLogo").value,
            // 空串 = 用系统默认底色（后端同样按“空 = 清除”处理旧文件）
            bg_image: byId("cfgBgImage").value,
            // 空串 = 左侧用系统默认说明；有值就是富文本编辑器里的 HTML（原样保存）
            brand_html: brandEditor ? brandEditor.getHtml() : "",
            // 登录框与左侧文字共用定位方式：0 = 左上角，1 = 中心点相对视口中心
            login_layout_mode: String(layoutMode),
            // 四个都空 = 恢复默认居中布局；四个都有值 = 按上方共用定位方式定位 + 固定宽高
            login_box_left: boxParams.login_box_left,
            login_box_top: boxParams.login_box_top,
            login_box_width: boxParams.login_box_width,
            login_box_height: boxParams.login_box_height,
            // 四个都空 = 恢复默认版式；四个都有值 = 与登录框使用同一个定位方式 + 固定宽高
            brand_left: brandParams.brand_left,
            brand_top: brandParams.brand_top,
            brand_width: brandParams.brand_width,
            brand_height: brandParams.brand_height,
            // 0 = 不透明（默认），100 = 完全透明；只影响登录框白色底色
            login_box_opacity: String(cardAlpha.value),
            // 0 = 不透明，100 = 全透明（默认）；只影响左侧文字区白色底板
            brand_opacity: String(brandAlpha.value),
            remark: byId("cfgRemark").value
        }, function (res) {
            btn.disabled = false;
            if (res.code !== 0) {
                showToast(res.msg || "保存失败", 2, 0);
                return;
            }
            showToast("已保存：统一登录页会按新设置生效", 0);
            renderConfig(res.data || {});
        });
    }

    byId("loginCfgSaveBtn").addEventListener("click", saveConfig);
    // 体积上限文案与按钮事件都按 IMG_UI 分派（logo / 背景图同一套实现）
    byId("cfgLogoMaxKb").textContent = String(Math.round(LOGIN_IMG.logo.maxBytes / 1024));
    byId("cfgBgMaxKb").textContent = String(Math.round(LOGIN_IMG.bg.maxBytes / 1024));
    Object.keys(IMG_UI).forEach(function (kind) {
        byId(IMG_UI[kind].pickBtn).addEventListener("click", function () { pickImg(kind); });
        byId(IMG_UI[kind].clearBtn).addEventListener("click", function () { clearImg(kind); });
        byId(IMG_UI[kind].fileEl).addEventListener("change", function () {
            uploadImg(kind, this.files && this.files[0]);
        });
        renderImgPreview(kind);
    });
    byId("cfgBrandClearBtn").addEventListener("click", clearBrand);
    // 编辑器里的内容变了就说明“已是自定义左侧文字”，按钮状态跟着变
    if (brandEditor) {
        brandEditor.setHtml(byId("cfgBrandHtml").value || "");
        renderBrand(byId("cfgBrandHtml").value || "");
    }

    /* 登录框透明度：滑块与数字双向联动，预览卡片即时刷新 */
    byId("cfgLoginBoxTransparency").addEventListener("input", function () {
        if (!CAN_MAINTAIN) { return; }
        var value = clampNumber(Number(this.value) || 0, CARD_ALPHA.min, CARD_ALPHA.max);
        byId("cfgLoginBoxTransparencyValue").value = String(value);
        renderCardAlphaPreview(value, false);
    });
    byId("cfgLoginBoxTransparencyValue").addEventListener("input", function () {
        if (!CAN_MAINTAIN) { return; }
        var value = readCardAlphaInput();
        if (!value) {
            cardAlphaResult("请填 0 ~ 100 的整数百分比", "error");
            return;
        }
        renderCardAlphaPreview(value.value, value.blank);
    });
    byId("cfgLoginBoxTransparencyValue").addEventListener("change", function () {
        if (!CAN_MAINTAIN) { return; }
        var value = readCardAlphaInput();
        if (!value) {
            this.value = String(CARD_ALPHA.defaultValue);
            renderCardAlphaPreview(CARD_ALPHA.defaultValue, true);
            return;
        }
        var normalized = clampNumber(value.value, CARD_ALPHA.min, CARD_ALPHA.max);
        this.value = String(normalized);
        renderCardAlphaPreview(normalized, false);
    });
    byId("cfgLoginBoxTransparencyResetBtn").addEventListener("click", function () {
        if (!CAN_MAINTAIN) { return; }
        byId("cfgLoginBoxTransparency").value = String(CARD_ALPHA.defaultValue);
        byId("cfgLoginBoxTransparencyValue").value = String(CARD_ALPHA.defaultValue);
        renderCardAlphaPreview(CARD_ALPHA.defaultValue, false);
    });

    /* 左侧文字背景透明度：滑块与数字双向联动，预览即时刷新 */
    byId("cfgBrandTransparency").addEventListener("input", function () {
        if (!CAN_MAINTAIN) { return; }
        var value = clampNumber(Number(this.value) || 0, BRAND_ALPHA.min, BRAND_ALPHA.max);
        byId("cfgBrandTransparencyValue").value = String(value);
        renderBrandAlphaPreview(value, false);
    });
    byId("cfgBrandTransparencyValue").addEventListener("input", function () {
        if (!CAN_MAINTAIN) { return; }
        var value = readBrandAlphaInput();
        if (!value) {
            brandAlphaResult("请填 0 ~ 100 的整数百分比", "error");
            return;
        }
        renderBrandAlphaPreview(value.value, value.blank);
    });
    byId("cfgBrandTransparencyValue").addEventListener("change", function () {
        if (!CAN_MAINTAIN) { return; }
        var value = readBrandAlphaInput();
        if (!value) {
            this.value = String(BRAND_ALPHA.defaultValue);
            renderBrandAlphaPreview(BRAND_ALPHA.defaultValue, true);
            return;
        }
        var normalized = clampNumber(value.value, BRAND_ALPHA.min, BRAND_ALPHA.max);
        this.value = String(normalized);
        renderBrandAlphaPreview(normalized, false);
    });
    byId("cfgBrandTransparencyResetBtn").addEventListener("click", function () {
        if (!CAN_MAINTAIN) { return; }
        byId("cfgBrandTransparency").value = String(BRAND_ALPHA.defaultValue);
        byId("cfgBrandTransparencyValue").value = String(BRAND_ALPHA.defaultValue);
        renderBrandAlphaPreview(BRAND_ALPHA.defaultValue, false);
    });

    /* 登录框布局：勾选 / 输入 / 拖动 / 拖拽缩放 */
    ["cfgLayoutModeLeftTop", "cfgLayoutModeCenter"].forEach(function (id) {
        byId(id).addEventListener("change", function () {
            if (!this.checked) { return; }
            setLayoutMode(Number(this.value));
        });
    });
    byId("cfgLoginBoxCustom").addEventListener("change", function () {
        setLayoutCustom(this.checked, false);
    });
    byId("cfgLoginBoxViewport").addEventListener("change", applyLayoutViewport);
    ["cfgLoginBoxLeft", "cfgLoginBoxTop", "cfgLoginBoxWidth", "cfgLoginBoxHeight"].forEach(function (id) {
        byId(id).addEventListener("input", applyLayoutInputs);
        byId(id).addEventListener("change", function () {
            var box = readLayoutInputs();
            if (!box) { return; }
            layoutView.left = box.left;
            layoutView.top = box.top;
            layoutView.width = clampNumber(box.width, LAYOUT_LIMITS.minWidth, LAYOUT_LIMITS.maxWidth);
            layoutView.height = clampNumber(box.height, LAYOUT_LIMITS.minHeight, LAYOUT_LIMITS.maxHeight);
            writeLayoutInputs(layoutView);
            renderUnifiedPreview();
        });
    });
    byId("cfgLoginBoxResetBtn").addEventListener("click", function () {
        if (!CAN_MAINTAIN) { return; }
        layoutView.custom = false;
        var def = defaultLayoutBox();
        layoutView.left = def.left;
        layoutView.top = def.top;
        layoutView.width = def.width;
        layoutView.height = def.height;
        byId("cfgLoginBoxCustom").checked = false;
        writeLayoutInputs(layoutView);
        setLayoutInputsDisabled();
        renderUnifiedPreview();
        layoutResult("已恢复系统默认居中布局，点“保存设置”后生效");
    });
    byId("loginBoxPreview").addEventListener("pointerdown", function (e) {
        if (e.target === byId("loginBoxResize")) { return; }
        beginLayoutDrag(e, "move");
    });
    byId("loginBoxResize").addEventListener("pointerdown", function (e) {
        e.stopPropagation();
        beginLayoutDrag(e, "resize");
    });

    /* 文字说明布局：勾选 / 输入 / 在同一个预览里拖动 / 右下角缩放 */
    byId("cfgBrandCustom").addEventListener("change", function () {
        setBrandLayoutCustom(this.checked, false);
    });
    ["cfgBrandLeft", "cfgBrandTop", "cfgBrandWidth", "cfgBrandHeight"].forEach(function (id) {
        byId(id).addEventListener("input", applyBrandLayoutInputs);
        byId(id).addEventListener("change", function () {
            var box = readBrandLayoutInputs();
            if (!box) { return; }
            brandLayoutView.left = box.left;
            brandLayoutView.top = box.top;
            brandLayoutView.width = clampNumber(box.width,
                BRAND_LIMITS.minWidth, BRAND_LIMITS.maxWidth);
            brandLayoutView.height = clampNumber(box.height,
                BRAND_LIMITS.minHeight, BRAND_LIMITS.maxHeight);
            writeBrandLayoutInputs(brandLayoutView);
            renderUnifiedPreview();
        });
    });
    byId("cfgBrandResetBtn").addEventListener("click", function () {
        if (!CAN_MAINTAIN) { return; }
        brandLayoutView.custom = false;
        var def = defaultBrandBox();
        brandLayoutView.left = def.left;
        brandLayoutView.top = def.top;
        brandLayoutView.width = def.width;
        brandLayoutView.height = def.height;
        byId("cfgBrandCustom").checked = false;
        writeBrandLayoutInputs(brandLayoutView);
        setBrandLayoutInputsDisabled();
        renderUnifiedPreview();
        brandLayoutResult("已恢复系统默认版式，点“保存设置”后生效");
    });
    byId("brandPreview").addEventListener("pointerdown", function (e) {
        if (e.target === byId("brandResize")) { return; }
        beginBrandDrag(e, "move");
    });
    byId("brandResize").addEventListener("pointerdown", function (e) {
        e.stopPropagation();
        beginBrandDrag(e, "resize");
    });
    window.addEventListener("pointermove", moveLayoutDrag);
    window.addEventListener("pointerup", endLayoutDrag);
    window.addEventListener("pointercancel", endLayoutDrag);
    window.addEventListener("pointermove", moveBrandDrag);
    window.addEventListener("pointerup", endBrandDrag);
    window.addEventListener("pointercancel", endBrandDrag);
    window.addEventListener("resize", function () {
        var centerCard = isCenterLayoutMode() && layoutView.custom
            ? layoutValuesFromBox(layoutView) : null;
        var centerBrand = isCenterLayoutMode() && brandLayoutView.custom
            ? layoutValuesFromBox(brandLayoutView) : null;
        if (layoutView.viewportMode === "current") {
            layoutView.winWidth = Math.max(861, document.documentElement.clientWidth || window.innerWidth || 1280);
            layoutView.winHeight = Math.max(600, document.documentElement.clientHeight || window.innerHeight || 800);
            if (!layoutView.custom) {
                var def = defaultLayoutBox();
                layoutView.left = def.left;
                layoutView.top = def.top;
                layoutView.width = def.width;
                layoutView.height = def.height;
            } else if (centerCard) {
                var cardBox = boxFromLayoutValues(centerCard.x, centerCard.y,
                    layoutView.width, layoutView.height);
                layoutView.left = cardBox.left;
                layoutView.top = cardBox.top;
            }
            if (!brandLayoutView.custom) {
                var brandDef = defaultBrandBox();
                brandLayoutView.left = brandDef.left;
                brandLayoutView.top = brandDef.top;
                brandLayoutView.width = brandDef.width;
                brandLayoutView.height = brandDef.height;
            } else if (centerBrand) {
                var brandBox = boxFromLayoutValues(centerBrand.x, centerBrand.y,
                    brandLayoutView.width, brandLayoutView.height);
                brandLayoutView.left = brandBox.left;
                brandLayoutView.top = brandBox.top;
            }
        }
        writeLayoutInputs(layoutView);
        writeBrandLayoutInputs(brandLayoutView);
        sizePreviewStage();
        renderUnifiedPreview();
    });

    // 登录方式 / Logo / 背景图 / 左侧文字的任何改动都只更新顶部这一个预览
    ["cfgPasswordEnable", "cfgMobileEnable", "cfgEmailEnable",
        "cfgWecomQrEnable", "cfgDingtalkQrEnable", "cfgFeishuQrEnable"].forEach(function (id) {
        byId(id).addEventListener("change", renderUnifiedPreview);
    });
    // iframe 首次加载完成后再画一次（首屏 API 可能还没回来，或预览页尚未就绪）
    byId("loginPreviewFrame").addEventListener("load", function () {
        sizePreviewStage();
        renderUnifiedPreview();
    });
    // 浮动预览可能挡住右侧内容：允许收起成一条标题栏，展开后再按面板宽度重算缩放
    var previewCollapsed = false;
    byId("loginPreviewToggleBtn").addEventListener("click", function () {
        previewCollapsed = !previewCollapsed;
        byId("loginEffectPreview").classList.toggle("collapsed", previewCollapsed);
        this.textContent = previewCollapsed ? "展开" : "收起";
        this.title = previewCollapsed ? "展开效果预览" : "收起效果预览";
        if (!previewCollapsed) {
            sizePreviewStage();
            renderUnifiedPreview();
        }
    });

    // 首屏先把预览框按当前窗口摆好（接口回来后会再用配置值回显一次）
    applyLayoutModeUi();
    sizePreviewStage();
    renderUnifiedPreview();
    loadConfig();
})();
</script>
</body>
</html>
