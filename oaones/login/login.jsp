<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesLog" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesConst" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesProps" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesRoutes" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesSso" %>
<%@ page import="com.customization.oaones.appsyn.common.OaonesUtil" %>
<%@ page import="com.customization.oaones.appsyn.common.QrLoginPlatform" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.wecom.LoginConfigServiceImpl" %>
<%@ page import="weaver.general.Util" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%@ page import="weaver.hrm.settings.ChgPasswdReminder" %>
<%@ page import="weaver.hrm.settings.RemindSettings" %>
<%@ include file="/oaones/assets.jsp" %>
<%
    // 统一登录页：账号密码登录 + 验证登录（手机号 / 邮箱）+ 扫码登录（一张二维码，三个平台都能扫）。
    //
    // 本页是**独立页壳**（与“个人账号中心”同一口径）：不引 navbar.jsp、不配模块导航，
    // 也**不做登录校验**——它就是给未登录用户用的；已登录时直接跳进 OA 主页，避免重复登录。
    //
    // 三种登录方式都在本页，页签与二维码有效期都取“统一登录设置”
    // （/oaones/loginConfig/config.jsp），页面不写死“有哪些方式”。
    //
    // 账号密码：不重实现一套校验，页面直接把表单提交给 E9 平台自己的登录接口
    // （/api/hrm/login/getLoginForm、/api/hrm/login/checkLogin），
    // 密码策略 / 验证码 / 弱密码 / 二次认证 / 登录日志全部沿用平台口径。
    // 登录验证码跟随“组织权限中心 - 账户中心 - 安全设置”里的“验证码”开关（含类型与位数）：
    // 要就显示输入框并提交 validatecode + validateCodeKey，不要就不出现该输入框；
    // “动态令牌”口令框本页不提供（登录页不需要），提交不带 tokenAuthKey。
    // 系统开启登录 RSA 加密时，按平台登录页同一套口径（/js/rsa/jsencrypt.js + 公钥接口
    // /rsa/weaver.rsa.GetRsaInfo）先在浏览器里加密账号与密码再提交，密码不以明文出网。
    // 验证登录：验证码由本模块自管（表 uf_oaones_login_sms，手机号写 mobile、邮箱写 email），
    // 只有“发出去”这一步走平台标准能力（手机号发短信、邮箱发邮件）。
    //
    // 扫码登录：**只有一个页签、只有一张二维码**，企业微信 / 钉钉 / 飞书客户端都能扫
    // （以前是三个平台各自的登录页、各画一张二维码）。
    // 二维码由本系统自己生成，内容只是本站的扫码落地页（/oaones/login/scan.jsp?t=票据），
    // 不带“是哪家平台 / 哪家企业”的信息：手机扫码后由落地页按客户端 UA 判断平台，
    // 再把请求交给对应连接器用它的租户与凭证换身份，最后按人员绑定关系定位 OA 账号。
    // 页面自己只做两件事：轮询票据状态 + 命中“已确认”时用一次性令牌写登录会话
    // （写会话必须在**本页自己的请求**里做，Cookie 才会落到这台电脑上）；
    // 平台分派见 com.customization.oaones.appsyn.controller.LoginController，
    // 接口入口是 /oaones/login/loginAction.jsp（本页没有登录会话，不能走 /oaones/WeCom/api.jsp）。
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    User oaonesLoginUser = HrmUserVarify.getUser(request, response);
    boolean oaonesLoggedIn = oaonesLoginUser != null;
    // 弹框模式判断要放在 gopage 之前：普通整页登录和超时重登弹框都直接使用原始 gopage。
    boolean oaonesPopup = "1".equals(OaonesUtil.s(request.getParameter("popup")));
    // 原始 gopage：接受站内路径或完整的 http/https 网址，#hash 里的页面参数整段保留
    String oaonesGopageRaw = OaonesSso.safeRedirectUrl(
            OaonesUtil.s(request.getParameter("gopage")), "");
    String oaonesLanding = OaonesProps.loginLandingUrl();
    if (oaonesLanding.length() == 0) {
        // 兜底与配置默认一致：登录成功进“应用中心 - 桌面启动台”，不再进系统首页
        oaonesLanding = "/oaones/appCenter/Desktop.jsp";
    }
    // 落地页统一按 URL 路由输出**对外地址**（oaones.properties 的 route.* 配了就用对外地址，
    // 没配原样返回）：登录成功后地址栏直接是对外地址，不会先落到真实 .jsp 再被挡一次
    oaonesLanding = OaonesRoutes.publicUrl(oaonesLanding);
    // 登录页带 gopage 时，登录成功直接跳目标地址，不再先进入桌面启动台；
    // 只有没带 gopage 才使用 landing（默认仍是桌面启动台）。
    String oaonesGopage = oaonesGopageRaw;
    String oaonesPageTitle = OaonesUtil.s(OaonesProps.loginPageTitle());
    if (oaonesPageTitle.length() == 0) {
        oaonesPageTitle = "OA 协同办公系统";
    }
    String oaonesTab = OaonesUtil.s(request.getParameter("tab"));
    // 地址上带 ?tab=scan 时优先落在扫码页签（旧地址习惯写 ?tab=wx，同样认）
    boolean oaonesScanTabFirst = "scan".equalsIgnoreCase(oaonesTab) || "wx".equalsIgnoreCase(oaonesTab);
    // 弹框模式（?popup=1）：门户超时后的“重新登录”弹框用同源 iframe 引入本页
    // （实现见 ecode 前端代码 oaones/ecode/relogin/init.js，它覆盖了 window.doLoginPop）。
    // 此模式下：① 页面只留**登录卡片本身**——页头说明区、二维码长说明、手机号说明、
    //    “说明问号”等都不出现，只留一行“支持哪些客户端扫码”这类简要说明；
    //    卡片的白底 / 圆角 / 阴影由父页的 iframe 提供，本页保持透明，免得叠两层；
    //    ② 卡片高度随内容上报给父页（postMessage：height），页签切换、二维码出现时
    //    弹框跟着变高变矮；二维码按可视高度自适应（弹框里不出现竖滚动条）；
    //    ③ 弹框**不给用户关**（没有关闭按钮，点空白处与 ESC 都不关）：卡片底部放一个
    //    “通过登录页登录”按钮，需要在整页上登录时在新窗口打开完整登录页；
    //    ④ 登录成功后**不整页跳转**，改为 postMessage 通知父页“登录成功”：
    //    父页让弹框消失并提示“已登录成功”，**不刷新浏览器页面**（用户停在原来那一页）。
    if (oaonesLoggedIn) {
        OaonesLog.step("已登录用户访问登录页，直接跳转落地页 loginid="
                + OaonesUtil.s(oaonesLoginUser.getLoginid()));
    }
    // 扫码提示文案里的客户端名单：**只列“登录设置”里已开启扫码的平台**
    // （后台没启用飞书就不出现“飞书”，钉钉、企微同理），页面里不写死三个平台名。
    // 与“扫码页签在至少开启一个平台时出现”同一口径（都取统一登录设置）；
    // 设置读不到时按 publicSettings() 的默认口径（默认只有企业微信开），本页照常可用。
    JSONObject oaonesScanSettings = new LoginConfigServiceImpl().publicSettings();
    java.util.List<String> oaonesScanLabels = QrLoginPlatform.enabledLabels(oaonesScanSettings);
    String oaonesScanText = QrLoginPlatform.enabledLabelText(oaonesScanSettings);
    boolean oaonesScanOn = oaonesScanLabels.size() > 0;
    // “验证登录”页签：手机号 / 邮箱任一开启就出现（与页面脚本 renderLoginMethods 同一口径）；
    // 品牌说明与说明区只列已开启的验证方式，后台没开的方式不出现（与扫码提示只列已开启平台同口径）
    boolean oaonesVerifyMobile = oaonesScanSettings.getIntValue("mobile_enable") == 1;
    boolean oaonesVerifyEmail = oaonesScanSettings.getIntValue("email_enable") == 1;
    boolean oaonesPasswordOn = oaonesScanSettings.getIntValue("password_enable") == 1;
    boolean oaonesVerifyOn = oaonesVerifyMobile || oaonesVerifyEmail;
    String oaonesVerifyTargetText = oaonesVerifyMobile && oaonesVerifyEmail ? "手机号 / 邮箱"
            : (oaonesVerifyEmail ? "邮箱" : "手机号");
    String oaonesVerifyCodeText = oaonesVerifyMobile && oaonesVerifyEmail ? "短信 / 邮件"
            : (oaonesVerifyEmail ? "邮件" : "短信");
    // 只剩一家平台时不再说“几个客户端扫的是同一张二维码”这句
    boolean oaonesScanMulti = oaonesScanLabels.size() > 1;
    // 二维码有效期（秒）的实际生效值：倒计时起始值按它显示；建码后由 login.qrCreate 回的
    // expire_at 接管（设置页最长 180 秒，留空默认 120 秒）
    int oaonesScanTtl = oaonesScanSettings.getIntValue("qr_ttl_effective");
    if (oaonesScanTtl <= 0) {
        oaonesScanTtl = 120;
    }
    // 账号密码登录的验证码**跟随平台系统设置**（组织权限中心 - 账户中心 - 安全设置 - 基本设置
    // →“登录设置”→“验证码”）：是否开启 needvalidate / 类型 validatetype（0 数字 1 字母 2 中文）/
    // 位数 validatenum（4~10）。这里读平台自己的设置类只是**首屏兜底**——页面不依赖 AJAX 也能
    // 正确显示验证码框与位数，图片本身也由平台 /weaver/weaver.file.MakeValidateCode 按同一份设置渲染；
    // 平台接口 /api/hrm/login/getLoginForm 回来后，仍以它回的 loginSetting.hasValidateCode 为准。
    boolean oaonesCaptchaOn = false;
    int oaonesCaptchaNum = 0;        // 0 = 读不到安全设置，输入框按 10 位上限降级
    int oaonesCaptchaType = 0;
    // “启用忘记密码功能”（同一页的“登录设置”里）：没开时登录页**不显示“忘记密码”入口**
    boolean oaonesForgotOn = false;
    try {
        RemindSettings oaonesSecuritySettings = new ChgPasswdReminder().getRemindSettings();
        oaonesCaptchaOn = oaonesSecuritySettings.getNeedvalidate() == 1;
        oaonesCaptchaType = oaonesSecuritySettings.getValidatetype();
        oaonesForgotOn = "1".equals(oaonesSecuritySettings.getNeedforgotpassword());
        int oaonesCaptchaNumRaw = oaonesSecuritySettings.getValidatenum();
        if (oaonesCaptchaNumRaw > 0) {
            // 与平台取图接口的边界口径一致（那边也是 4~10 截断），位数不至于对不上图片
            oaonesCaptchaNum = Math.min(10, Math.max(4, oaonesCaptchaNumRaw));
        }
    } catch (Throwable oaonesCaptchaErr) {
        // 读不到安全设置不阻断登录页：按“由平台接口决定”的降级口径显示验证码
        OaonesLog.error("读取安全设置(登录验证码)失败，登录页按平台接口口径显示验证码", oaonesCaptchaErr);
    }
    // 登录页外观（“登录设置”里维护，未设置时用系统默认外观）：
    // ① logo：按比例缩放显示在**右侧登录框的内部上方**；
    // ② 背景图：整页背景，前端按窗口自动缩放（css/login.css 的 cover）；
    // ③ 左侧文字：富文本 HTML，**原样输出**（设置什么显示什么），非空时整段替换系统默认的左侧说明。
    // ④ 登录框透明度：只改白色底色的 alpha，让背景图透出来，表单内容保持不透明。
    // ⑤ 左侧文字背景透明度：只改文字区白色底板的 alpha，文字保持不透明；默认 100 = 无底板。
    // ⑥ 位置与尺寸：登录框与左侧文字区各自可按视口左上角像素自定义，互不影响。
    // 弹框模式（?popup=1，超时后的“重新登录”弹框）里这些外观项都**不显示 / 不生效**：弹框要的是一眼就能登录，
    // 不再占图片与说明的高度（页面会为它上报高度，少一块就少一段空白）。
    // 两个图片地址只认本模块上传后产生的站内地址（与 LoginConfigServiceImpl 保存时的校验同一口径）：
    // 库里这些列被手工改成别的地址（含 javascript: 之类）时一律不渲染。
    String oaonesLogo = OaonesUtil.s(oaonesScanSettings.getString("logo"));
    boolean oaonesLogoOn = isLoginImage(oaonesLogo, OaonesConst.LOGIN_LOGO_FILE_PREFIX);
    String oaonesBg = OaonesUtil.s(oaonesScanSettings.getString("bg_image"));
    boolean oaonesBgOn = !oaonesPopup && isLoginImage(oaonesBg, OaonesConst.LOGIN_BG_FILE_PREFIX);
    // 左侧富文本：库里原样存着 HTML，这里原样输出（管理员维护；见“登录设置 → 登录页左侧文字”）
    String oaonesBrandHtml = OaonesUtil.s(oaonesScanSettings.getString("brand_html"));
    // 只有空白 / 空标记（历史数据里的 <p><br></p>、&nbsp; 之类）按“没设置”处理：
    // 否则页面会渲染一个空的左侧自定义区，把系统默认说明整段顶掉，看起来就是“左侧文字不见了”。
    boolean oaonesBrandCustom = !oaonesPopup && hasBrandText(oaonesBrandHtml);
    // 登录框位置与尺寸（“登录设置 → 登录框位置与尺寸”）：以**视口左上角像素**为基准，
    // 四项都设置时才启用绝对定位；弹框模式与窄屏（≤860px）仍走默认布局。
    boolean oaonesBoxCustom = !oaonesPopup && oaonesScanSettings.getBooleanValue("login_box_custom");
    int oaonesBoxLeft = oaonesBoxCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("login_box_left"),
                0, OaonesConst.LOGIN_BOX_MAX_POS) : 0;
    int oaonesBoxTop = oaonesBoxCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("login_box_top"),
                0, OaonesConst.LOGIN_BOX_MAX_POS) : 0;
    int oaonesBoxWidth = oaonesBoxCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("login_box_width"),
                OaonesConst.LOGIN_BOX_MIN_WIDTH, OaonesConst.LOGIN_BOX_MAX_WIDTH) : 0;
    int oaonesBoxHeight = oaonesBoxCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("login_box_height"),
                OaonesConst.LOGIN_BOX_MIN_HEIGHT, OaonesConst.LOGIN_BOX_MAX_HEIGHT) : 0;
    // 登录页左侧文字的位置与尺寸（“登录设置 → 文字说明位置与尺寸”）：同样是视口左上角像素，
    // 四项都设置时才启用绝对定位；未设置时跟随系统默认版式（在登录框左边），弹框 / 窄屏不启用。
    boolean oaonesBrandLayoutCustom = !oaonesPopup
            && oaonesScanSettings.getBooleanValue("brand_custom");
    int oaonesBrandLeft = oaonesBrandLayoutCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("brand_left"),
                0, OaonesConst.LOGIN_BRAND_MAX_POS) : 0;
    int oaonesBrandTop = oaonesBrandLayoutCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("brand_top"),
                0, OaonesConst.LOGIN_BRAND_MAX_POS) : 0;
    int oaonesBrandWidth = oaonesBrandLayoutCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("brand_width"),
                OaonesConst.LOGIN_BRAND_MIN_WIDTH, OaonesConst.LOGIN_BRAND_MAX_WIDTH) : 0;
    int oaonesBrandHeight = oaonesBrandLayoutCustom
            ? loginBoxValue(oaonesScanSettings.getIntValue("brand_height"),
                OaonesConst.LOGIN_BRAND_MIN_HEIGHT, OaonesConst.LOGIN_BRAND_MAX_HEIGHT) : 0;
    // 登录框背景透明度（0 = 不透明，100 = 完全透明）：只改白色底色，文字 / 输入框 / 按钮不受影响。
    // 弹框模式不复用整页外观，保持父页给的白底卡片（透明度 0）。
    int oaonesBoxOpacity = oaonesPopup ? 0 : loginBoxValue(
            oaonesScanSettings.getIntValue("login_box_opacity"),
            OaonesConst.LOGIN_BOX_OPACITY_MIN, OaonesConst.LOGIN_BOX_OPACITY_MAX);
    // 左侧文字背景透明度（0 = 不透明，100 = 透明）：只改文字区底板，文字不受影响。
    // 默认 100 = 没有底板，保持历史登录页外观；弹框模式里左侧区域本来就不显示。
    int oaonesBrandOpacity = oaonesPopup ? OaonesConst.LOGIN_BRAND_OPACITY_DEFAULT : loginBoxValue(
            oaonesScanSettings.getIntValue("brand_opacity"),
            OaonesConst.LOGIN_BRAND_OPACITY_MIN, OaonesConst.LOGIN_BRAND_OPACITY_MAX);
    boolean oaonesBrandSurface = oaonesBrandOpacity < OaonesConst.LOGIN_BRAND_OPACITY_MAX;
    // body 的内联样式统一拼一次：背景图变量 + 登录框四个坐标 / 尺寸变量 + 透明度 alpha（CSS 见 css/login.css）
    StringBuilder oaonesBodyStyle = new StringBuilder();
    if (oaonesBgOn) {
        oaonesBodyStyle.append("--oa-login-bg:url('").append(Util.toHtml(oaonesBg)).append("');");
    }
    if (oaonesBoxCustom) {
        oaonesBodyStyle.append("--oa-login-card-left:").append(oaonesBoxLeft).append("px;")
                .append("--oa-login-card-top:").append(oaonesBoxTop).append("px;")
                .append("--oa-login-card-width:").append(oaonesBoxWidth).append("px;")
                .append("--oa-login-card-height:").append(oaonesBoxHeight).append("px;");
    }
    if (oaonesBrandLayoutCustom) {
        oaonesBodyStyle.append("--oa-login-brand-left:").append(oaonesBrandLeft).append("px;")
                .append("--oa-login-brand-top:").append(oaonesBrandTop).append("px;")
                .append("--oa-login-brand-width:").append(oaonesBrandWidth).append("px;")
                .append("--oa-login-brand-height:").append(oaonesBrandHeight).append("px;");
    }
    if (oaonesBoxOpacity > 0) {
        // CSS rgba 的 alpha：透明度 0% → 1（不透明），100% → 0（全透明）
        double oaonesBoxAlpha = 1.0 - (oaonesBoxOpacity / 100.0);
        oaonesBodyStyle.append("--oa-login-card-alpha:")
                .append(String.format(java.util.Locale.ROOT, "%.3f", Double.valueOf(oaonesBoxAlpha)))
                .append(";");
    }
    if (oaonesBrandSurface) {
        // 左侧文字底板 alpha：透明度 0% → 1（不透明白底），100% → 0（无底板）
        double oaonesBrandAlpha = 1.0 - (oaonesBrandOpacity / 100.0);
        oaonesBodyStyle.append("--oa-login-brand-alpha:")
                .append(String.format(java.util.Locale.ROOT, "%.3f", Double.valueOf(oaonesBrandAlpha)))
                .append(";");
    }
%>
<%!
    /**
     * 登录页图片地址校验（logo / 背景图共用一个口径）：只认本模块上传后产生的站内地址
     * （{@code /oaones/img/} + 各自前缀，文件名里不带子目录 / 回退段 / 引号）。
     *
     * <p>与 {@code LoginConfigServiceImpl} 保存时的 {@code normalizeImageUrl} 同一口径——
     * 库里这一列被手工改成别的地址（含 {@code javascript:} 之类）时页面一律不渲染。</p>
     */
    private boolean isLoginImage(String url, String filePrefix) {
        String value = url == null ? "" : url.trim();
        String prefix = com.customization.oaones.appsyn.common.OaonesConst.LOGIN_IMAGE_URL_PREFIX;
        if (!value.startsWith(prefix)) {
            return false;
        }
        String name = value.substring(prefix.length());
        return name.startsWith(filePrefix)
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf("..") < 0
                && name.indexOf('"') < 0 && name.indexOf('<') < 0 && name.indexOf('\'') < 0
                && name.indexOf(' ') < 0 && name.indexOf('\n') < 0 && name.indexOf('\r') < 0;
    }

    /**
     * 登录框像素值收敛：保存时已按同一范围校验，这里再兜一层，
     * 避免库里被手工改成 0 / 负数 / 超大值时登录框整个消失或跑到屏幕外。
     */
    private int loginBoxValue(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 登录页左侧富文本是否真的有内容：与 LoginConfigServiceImpl#normalizeBrandHtml 同一口径，
     * 去掉标签与 &nbsp; 后没有文字就视为空，回退到系统默认说明（避免历史空标记把左侧显示成空白）。
     */
    private boolean hasBrandText(String html) {
        String value = html == null ? "" : html;
        return value.replace("&nbsp;", "").replace("\u00a0", "")
                .replace("&#160;", "").replace("&#xa0;", "").replace("&#XA0;", "")
                .replaceAll("<[^>]*>", "").trim().length() > 0;
    }
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>登录 - <%=Util.toHtml(oaonesPageTitle)%></title>
    <link rel="stylesheet" href="/oaones/css/style.css?v=<%=oaonesAssetV%>">
    <link rel="stylesheet" href="/oaones/css/login.css?v=<%=oaonesAssetV%>">
</head>
<%-- 整页背景图（“登录设置 → 登录页背景图”）：把地址交给 css/login.css 的 --oa-login-bg，
     由样式按“铺满窗口等比缩放（cover）”渲染；接口未设置 / 弹框模式时不写这个变量，走默认底色 --%>
<body class="login-page<%=oaonesPopup ? " login-page-popup" : (oaonesBoxCustom
    ? " login-page-custom" : " login-page-default")%><%=
    oaonesBrandLayoutCustom ? " login-page-brand-custom" : ""%><%=
    oaonesBrandSurface ? " login-page-brand-surface" : ""%>"<%=
    oaonesBodyStyle.length() > 0 ? " style=\"" + oaonesBodyStyle + "\"" : ""%>>
<div class="login-wrap">
    <div class="login-brand">
        <% if (oaonesBrandCustom) { %>
        <%-- 管理员在“登录设置 → 登录页左侧文字”里写的富文本：**原样输出**（设置什么显示什么），
             基础版式见 css/login.css 的 .login-brand-html --%>
        <div class="login-brand-html"><%=oaonesBrandHtml%></div>
        <% } else { %>
        <h1><%=Util.toHtml(oaonesPageTitle)%></h1>
        <p class="login-brand-text">请选择下方任一已开启的方式登录（具体可用的方式由管理员在“登录设置”里决定）。</p>
        <ul class="login-brand-list">
            <% if (oaonesPasswordOn) { %>
            <li><b>账号密码</b>：与平台登录同一套校验（密码策略、登录验证码、二次认证均按“安全设置”执行）</li>
            <% } %>
            <% if (oaonesVerifyOn) { %>
            <li><b>验证登录</b>：填 OA 里维护的<%=oaonesVerifyTargetText%>，收到<%=oaonesVerifyCodeText%>验证码后登录</li>
            <% } %>
            <% if (oaonesScanOn) { %>
            <li><b>扫码登录</b>：用本人的<%=Util.toHtml(oaonesScanText)%>扫同一张二维码并在手机上确认，
                需先在“个人账号中心”绑定本人 OA 账号</li>
            <% } %>
        </ul>
        <% } %>
    </div>

    <div class="login-card" id="loginCard">
        <% if (!oaonesPopup && oaonesLogoOn) { %>
        <%-- 登录页 logo：来自“登录设置 → 登录页 Logo”，按比例缩放显示在登录框内部上方
             （宽度不超过登录框、高度自适应，见 css/login.css 的 .login-logo）；
             弹框模式（超时后的“重新登录”）里不出现 --%>
        <div class="login-logo">
            <img src="<%=Util.toHtml(oaonesLogo)%>" alt="<%=Util.toHtml(oaonesPageTitle)%>">
        </div>
        <% } %>
        <div class="login-tabs">
            <button type="button" class="login-tab <%=oaonesScanTabFirst ? "" : "active"%>" id="loginTabPwd">账号密码登录</button>
            <button type="button" class="login-tab" id="loginTabVerify" style="display:none;">验证登录</button>
            <button type="button" class="login-tab <%=oaonesScanTabFirst ? "active" : ""%>" id="loginTabScan">扫码登录</button>
        </div>

        <div class="login-pane" id="loginPanePwd" <%=oaonesScanTabFirst ? "style=\"display:none\"" : ""%>>
            <form class="login-form" id="loginForm" action="/api/hrm/login/checkLogin" method="post">
                <div class="login-field">
                    <label for="loginid">账号</label>
                    <input type="text" id="loginid" name="loginid" autocomplete="username"
                           placeholder="请输入登录名" maxlength="100">
                </div>
                <div class="login-field">
                    <label for="userpassword">密码</label>
                    <input type="password" id="userpassword" name="userpassword" autocomplete="current-password"
                           placeholder="请输入密码" maxlength="100">
                </div>
                <div class="login-field" id="loginCaptchaRow" <%=oaonesCaptchaOn ? "" : "style=\"display:none;\""%>>
                    <label for="validatecode">验证码</label>
                    <div class="login-captcha">
                        <input type="text" id="validatecode" name="validatecode" autocomplete="off"
                               data-lpignore="true" maxlength="<%=oaonesCaptchaNum > 0 ? oaonesCaptchaNum : 10%>"
                               placeholder="<%=
                                   oaonesCaptchaNum > 0
                                       ? "请输入 " + oaonesCaptchaNum + " 位验证码" : "请输入验证码"%>">
                        <%-- 验证码图由平台按“安全设置”的位数与类型渲染（数字 / 字母 / 中文），本页不自造；
                             后台没开验证码时不下发 src（页面不白取一张空图），开了就是首屏第一张码，点图片换一张 --%>
                        <img id="loginCaptchaImg" alt="验证码" title="看不清？点击换一张"<%=
                            oaonesCaptchaOn
                                    ? " src=\"/weaver/weaver.file.MakeValidateCode?seriesnum_="
                                        + System.currentTimeMillis() + "\""
                                    : ""%>>
                    </div>
                </div>
                <div class="login-field" id="loginDynamicRow" style="display:none;">
                    <label for="dynamicPassword">动态密码</label>
                    <input type="password" id="dynamicPassword" name="dynamicPassword"
                           autocomplete="new-password" data-lpignore="true" placeholder="请输入动态密码" maxlength="32">
                </div>
                <div class="login-row">
                    <label class="login-remember" title="仅保存在本浏览器，公用电脑请勿勾选">
                        <input type="checkbox" id="loginRemember"> 记住账户密码
                    </label>
                    <%-- 平台没开“启用忘记密码功能”时这里不出现（服务端先按安全设置定初始状态，
                         平台接口 getLoginForm 回来后由 JS 按 needforgotpassword 再校正一次） --%>
                    <a class="login-link" id="loginForgotLink" href="#" role="button"<%=
                        oaonesForgotOn ? "" : " style=\"display:none;\""%>>忘记密码</a>
                </div>
                <button type="submit" class="btn login-submit" id="loginSubmitBtn">登 录</button>
                <p class="login-msg" id="loginMsg"></p>
            </form>
        </div>

        <%-- 验证登录：一个页签里两种验证方式（手机号 / 邮箱），验证码由本模块自管、发送借平台标准通道
             （手机号走短信、邮箱走邮件）；只开了一种方式时下面的“验证方式”一行不出现 --%>
        <div class="login-pane" id="loginPaneVerify" style="display:none;">
            <form class="login-form" id="verifyForm" onsubmit="return false;">
                <div class="login-field" id="verifyMethodRow">
                    <label>验证方式</label>
                    <div class="login-methods">
                        <label class="login-method"><input type="radio" name="oaonesVerifyChannel"
                               id="verifyChannelMobile" value="mobile" checked> 手机号</label>
                        <label class="login-method"><input type="radio" name="oaonesVerifyChannel"
                               id="verifyChannelEmail" value="email"> 邮箱</label>
                    </div>
                </div>
                <div class="login-field">
                    <label for="verifyTarget" id="verifyTargetLabel">手机号</label>
                    <input type="text" id="verifyTarget" name="oaonesVerifyTarget" autocomplete="tel"
                           data-lpignore="true" placeholder="请输入在 OA 里维护的手机号" maxlength="20">
                </div>
                <div class="login-field">
                    <label for="verifyCode" id="verifyCodeLabel">手机验证码</label>
                    <div class="login-captcha">
                        <input type="text" id="verifyCode" name="oaonesVerifyCode" autocomplete="off"
                               data-lpignore="true" placeholder="请输入短信验证码" maxlength="10">
                        <button type="button" class="btn secondary" id="verifySendBtn"
                                title="向该手机号发送验证码">获取验证码</button>
                    </div>
                </div>
                <% if (!oaonesPopup) { %>
                <div class="login-row">
                    <span class="muted" id="verifyTip">
                        验证码由 OA 统一发送（发送频率与有效期以系统设置为准）
                    </span>
                </div>
                <% } %>
                <button type="submit" class="btn login-submit" id="verifySubmitBtn">登 录</button>
                <p class="login-msg" id="verifyMsg"></p>
            </form>
        </div>

        <div class="login-pane" id="loginPaneScan" <%=oaonesScanTabFirst ? "" : "style=\"display:none\""%>>
            <div class="login-qr-box">
                <div class="login-qr-img-box" id="loginQrBox"
                     title="点击二维码可刷新（重新生成并重新计时）">
                    <%-- 二维码上的状态提示（扫码成功/已超时）覆盖在码图上层，按需显示 --%>
                    <div class="login-qr-overlay" id="loginQrOverlay" style="display:none;"></div>
                    <div class="login-qr-loading">正在生成二维码…</div>
                </div>
                <% if (oaonesPopup) { %>
                <%-- 弹框里只留一行简要说明 + 一句实时状态：平台名单由服务端按“登录设置”下发，
                     怎么确认由码上的状态提示表达 --%>
                <p class="login-popup-note">支持 <%=Util.toHtml(oaonesScanText)%> 扫码登录</p>
                <p class="login-qr-expire">剩余 <span id="loginQrExpire"><%=oaonesScanTtl%></span> 秒</p>
                <% } else { %>
                <p class="login-qr-tip">请用<b>本人的<%=Util.toHtml(oaonesScanText)%></b>扫一扫扫描上方二维码，
                    在手机上点“确认登录”后本页会自动进入 OA；</p>
                <p>二维码剩余 <span id="loginQrExpire"><%=oaonesScanTtl%></span> 秒，
                    点一下二维码图片即可重新生成并重新计时。</p>
                <% } %>
            </div>
            <p class="login-msg" id="loginScanMsg"></p>
        </div>

        <% if (oaonesPopup) { %>
        <%-- 弹框不给关：需要在整页上登录（比如要用弹框里排不下的方式）时，在新窗口打开完整登录页 --%>
        <div class="login-popup-foot">
            <button type="button" class="btn secondary" id="loginPopupFullBtn">通过登录页登录</button>
        </div>
        <% } %>
    </div>
</div>

<%-- 忘记密码：**本页自己的弹层**（弹层内容是本模块自己的 /oaones/login/forgotPassword.jsp），
     不跳平台登录页；iframe 按需加载（点“忘记密码”才请求），关掉即清空，下次重走一遍 --%>
<div class="oa-modal-mask" id="loginForgotModal" style="display:none;">
    <div class="oa-modal" role="dialog" aria-modal="true" aria-label="找回密码">
        <div class="oa-modal-head">
            <span>找回密码</span>
            <button type="button" class="oa-modal-close" id="loginForgotClose"
                    title="关闭" aria-label="关闭">×</button>
        </div>
        <iframe class="oa-modal-frame" id="loginForgotFrame" title="找回密码"></iframe>
    </div>
</div>

<script>window.OAONES_LOGIN = {
    actionUrl: "/oaones/login/loginAction.jsp",
    loggedIn: <%=oaonesLoggedIn%>,
    gopage: "<%=Util.toHtml(oaonesGopage).replace("\"", "&quot;")%>",
    landing: "<%=Util.toHtml(oaonesLanding).replace("\"", "&quot;")%>",
    scanTabFirst: <%=oaonesScanTabFirst%>,
    // 弹框模式（?popup=1，门户超时后的“重新登录”弹框以 iframe 引入本页）：
    // 登录成功后不整页跳转，改由 postMessage 通知父页（见下面 goto / gotoCas）
    popup: <%=oaonesPopup%>,
    // 扫码提示里的客户端名单：只含“登录设置”里已开启扫码的平台（由服务端按设置下发）
    scanPlatformText: "<%=Util.toHtml(oaonesScanText).replace("\"", "&quot;")%>",
    // 账号密码登录的验证码口径：服务端按平台“组织权限中心 - 账户中心 - 安全设置”预读
    // （on 是否开启 / num 位数 / type 类型），平台接口回来后仍以 loginSetting 为准
    captcha: {on: <%=oaonesCaptchaOn%>, num: <%=oaonesCaptchaNum%>, type: <%=oaonesCaptchaType%>},
    // “启用忘记密码功能”的预读值：没开时登录页不显示“忘记密码”入口
    // （同样以平台接口回的 loginSetting.needforgotpassword 为准）
    forgot: {on: <%=oaonesForgotOn%>}
};</script>
<script src="/oaones/js/toast.js?v=<%=oaonesAssetV%>"></script>
<%-- 弹框统一支持按表头拖动（picker.js 接管；本页“忘记密码”弹层同样可拖） --%>
<script src="/oaones/js/picker.js?v=<%=oaonesAssetV%>"></script>
<script>
"use strict";
(function () {
    var CFG = window.OAONES_LOGIN || {};
    var ACTIONS = CFG.actionUrl || "/oaones/login/loginAction.jsp";
    /** 平台登录接口（与 /wui 登录页同一套接口，见 weaver_login_extension.properties） */
    var API_GET_FORM = "/api/hrm/login/getLoginForm";
    var API_CHECK = "/api/hrm/login/checkLogin";
    /** 当前语言：平台后续按它返回提示文案（默认 7 = 简体中文） */
    var LANG_ID = "7";
    /** 记住账户密码：账号与密码存在本浏览器 localStorage 中（可逆混淆，避免明文直读） */
    var LS_CREDENTIALS = "oaones.login.credentials";
    var CRED_MASK = "oaones.login.remember";
    /**
     * 扫码登录可用的平台（企业微信 / 钉钉 / 飞书）与各自的可用租户数：
     * 一张二维码覆盖全部平台的全部租户，这里只用于判断“扫码能不能用”与提示文案。
     */
    var qrPlatforms = [];
    var qrUsable = 0;
    /** 是否已经问过“有没有可用租户”（读取失败时为 false：再点刷新就重新问一次，当作重试） */
    var qrTenantsReady = false;
    /** 已启用扫码的平台名（如“企业微信 / 钉钉”）：由服务端按“登录设置”下发，不在页面里写死 */
    var scanClientText = CFG.scanPlatformText || "";
    /** 当前二维码票据与扫码地址（票据由 login.qrCreate 生成，过期/刷新后更换） */
    var qrTicket = "";
    /** 轮询间隔（毫秒）与过期时间（毫秒时间戳），都由服务端下发 */
    var qrPollMs = 1500;
    var qrExpireAt = 0;
    /** 本页的浏览器标识（创建/轮询/换登录必须一致）与轮询/倒计时定时器 */
    var qrBrowserKey = "";
    var qrPollTimer = 0;
    var qrCountTimer = 0;
    var qrLoading = false;
    /** 是否已“确认登录”（正在进 OA）：此时点二维码不再重新建码，避免打断换登录会话 */
    var qrConfirmed = false;

    function byId(id) { return document.getElementById(id); }
    function esc(v) { return window.OaonesToast ? window.OaonesToast.esc(v) : String(v == null ? "" : v); }
    /** 扫码提示里的客户端说法：平台名单为空（三个平台都被关掉）时退化成通用说法 */
    function scanClients() { return scanClientText.length > 0 ? scanClientText : "办公客户端"; }
    function toast(msg, mode, duration) {
        if (msg === null || msg === undefined || String(msg) === "") { return; }
        window.OaonesToast.show(msg, { mode: mode, duration: duration });
    }
    function setMsg(el, text, state) {
        el.className = "login-msg" + (state ? " " + state : "");
        el.textContent = text || "";
    }

    /* ------------------------------------------------------------------
     * 通用请求（本页没有登录会话，不能用 /oaones/WeCom/api.jsp，见 loginAction.jsp 注释）
     * ------------------------------------------------------------------ */
    function postForm(url, params) {
        var body = [];
        Object.keys(params || {}).forEach(function (k) {
            body.push(encodeURIComponent(k) + "=" + encodeURIComponent(params[k] == null ? "" : params[k]));
        });
        return fetch(url, {
            method: "POST",
            credentials: "same-origin",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: body.join("&")
        }).then(function (r) {
            if (!r.ok) { throw new Error("HTTP " + r.status + " " + url); }
            return r.json();
        });
    }

    function actionApi(action, params) {
        var parts = ["action=" + encodeURIComponent(action)];
        Object.keys(params || {}).forEach(function (k) {
            var v = params[k];
            if (v !== undefined && v !== null && String(v) !== "") {
                parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(v));
            }
        });
        return fetch(ACTIONS + "?" + parts.join("&"), { credentials: "same-origin" })
            .then(function (r) {
                if (!r.ok) { throw new Error("HTTP " + r.status + " " + ACTIONS); }
                return r.json();
            });
    }

    /**
     * 通知父页“已经登录成功”（只在弹框模式下用）：
     * 门户超时后的重新登录弹框（ecode 前端代码 oaones/ecode/relogin/init.js）以同源 iframe
     * 引入本页，收到这条消息后由它让弹框消失并提示“已登录成功”——**不刷新浏览器页面**，
     * 用户继续停在原来那一页（会话已经恢复）。
     * 跨域 / 老浏览器 / 不是嵌在 iframe 里（用户直接打开本页）时返回 false，调用方按整页跳转处理。
     */
    function notifyParent(payload) {
        if (!CFG.popup || !window.parent || window.parent === window) { return false; }
        try {
            window.parent.postMessage(payload, location.origin);
            return true;
        } catch (e) {
            return false;
        }
    }

    /** 登录成功后的整页跳转：用 JS 跳转（不用 302），`#` 之后的页面参数才不会被重排掉 */
    function goto(url) {
        var target = url && String(url).length > 0
            ? url : (CFG.gopage && CFG.gopage.length > 0 ? CFG.gopage : CFG.landing);
        // 弹框模式：交给父页收口（弹框消失 + 提示“已登录成功”），本页自己不跳、页面也不刷新
        if (notifyParent({oaonesRelogin: "ok", url: target})) { return; }
        try { location.replace(target); } catch (e) { location.href = target; }
    }

    /**
     * 弹框模式：把卡片高度报给父页（父页据此调整弹框大小，弹框里不会多出一条空白）。
     * 内容高度会随页签切换、二维码出现、提示出现而变化，所以用 ResizeObserver 盯着；
     * 老浏览器没有 ResizeObserver 时靠“首屏 + 几次定时上报”兜底（高度没变就不重复发）。
     */
    var popupHeight = 0;
    /** 各页签面板的公共高度（弹框模式：切页签时卡片高度不变，见 measurePopupPaneHeight） */
    var popupPaneHeight = 0;
    /** 正在量面板高度：期间的上报先丢掉，免得把中间态报给父页 */
    var popupMeasuring = false;

    function reportHeight() {
        if (!CFG.popup || popupMeasuring || !window.parent || window.parent === window) { return; }
        // 量 body（高度随内容撑开）而不是 documentElement：后者的 scrollHeight 不会小于 iframe 的视口高度，
        // 卡片比弹框矮时会一直停在旧高度、缩不回去
        var body = document.body;
        var h = body ? Math.max(body.scrollHeight, Math.round(body.getBoundingClientRect().height)) : 0;
        h = Math.ceil(h);
        if (h <= 0 || h === popupHeight) { return; }
        popupHeight = h;
        notifyParent({oaonesRelogin: "height", height: h});
    }

    /**
     * 父页给的“弹框可用高度”：写到 CSS 变量上，二维码据此决定大小。
     *
     * <p>**不能**直接按 iframe 自己的视口高度（vh）算：父页是按卡片内容给高度的，
     * 那样会“高度变小 → 二维码变小 → 内容更小 → 高度再变小”一路缩下去（矮窗口里二维码会缩得过分小）。
     * 父页在 iframe 地址上带 `avail=`，窗口缩放时还会用 postMessage（avail）再同步一次。</p>
     */
    var POPUP_AVAIL_VAR = "--oa-popup-avail";

    function applyAvailHeight(px) {
        if (!CFG.popup) { return; }
        var h = Number(px);
        if (!(h > 0)) { return; }
        document.documentElement.style.setProperty(POPUP_AVAIL_VAR, Math.round(h) + "px");
        // 二维码框的高度是按 --oa-popup-avail 算的，可用高度一变扫码面板的高度也跟着变：
        // 重新量一遍公共高度（量完自己会上报），切页签仍然保持一致
        measurePopupPaneHeight();
        reportHeight();
    }

    /**
     * 弹框模式：**让各页签的卡片高度保持一致**。
     *
     * <p>父页是按本页上报的高度给弹框定高的：如果按“当前页签的内容”上报，切页签时弹框
     * 就会一会儿高一会儿矮。这里先把每个**会显示**的页签面板逐个量一遍、取最高的一份，
     * 写成 CSS 变量 {@code --oa-popup-pane-h}（login.css 里作为
     * {@code .login-page-popup .login-pane} 的 min-height），于是切到哪个页签卡片高度都一样，
     * 上报给父页的高度自然一致。</p>
     *
     * <p>量的时候给每个面板**留一行提示**：弹框里 {@code .login-msg} 为空时不占位，
     * 不留这一行的话，“出现验证码错误提示”这类情况会让卡片突然变高；
     * 量完再上报（期间 {@link #reportHeight} 直接返回），不会把中间态报给父页。</p>
     */
    function measurePopupPaneHeight() {
        if (!CFG.popup) { return; }
        var panes = visiblePopupPanes();
        if (panes.length === 0) { return; }
        popupMeasuring = true;
        // 先把上一轮的公共高度清零：否则这一轮量到的是“被撑高过”的面板，窗口变矮时收不回去
        document.documentElement.style.setProperty("--oa-popup-pane-h", "0px");
        var saved = [];
        panes.forEach(function (id) {
            saved.push([byId(id), byId(id).style.display]);
            byId(id).style.display = "none";
        });
        var max = 0;
        panes.forEach(function (id) {
            var pane = byId(id);
            var msgs = pane.querySelectorAll(".login-msg");
            var texts = [];
            pane.style.display = "";
            Array.prototype.forEach.call(msgs, function (el) {
                texts.push(el.textContent);
                if (el.textContent.length === 0) { el.textContent = "\u00a0"; }
            });
            max = Math.max(max, pane.offsetHeight);
            Array.prototype.forEach.call(msgs, function (el, i) { el.textContent = texts[i]; });
            pane.style.display = "none";
        });
        saved.forEach(function (pair) { pair[0].style.display = pair[1]; });
        popupMeasuring = false;
        if (max !== popupPaneHeight) {
            popupPaneHeight = max;
            document.documentElement.style.setProperty("--oa-popup-pane-h", max + "px");
            // 公共高度变了就重报一次（reportHeight 只在高度变化时发消息）
            popupHeight = 0;
            reportHeight();
        }
    }

    /** 弹框模式下“会显示”的页签面板：页签隐藏 = 该面板永远看不到，不参与取最高 */
    function visiblePopupPanes() {
        var pairs = [["loginTabPwd", "loginPanePwd"], ["loginTabVerify", "loginPaneVerify"],
            ["loginTabScan", "loginPaneScan"]];
        return pairs.filter(function (p) {
            var tab = byId(p[0]);
            return tab && tab.style.display !== "none";
        }).map(function (p) { return p[1]; });
    }

    function availFromUrl() {
        try {
            var m = /[?&]avail=(\d+)/.exec(window.location.search || "");
            return m ? Number(m[1]) : 0;
        } catch (e) {
            return 0;
        }
    }

    /**
     * 弹框模式的收尾工作：按父页给的可用高度定住二维码大小、把卡片高度报给父页（弹框随内容自适应）+
     * 卡片底部“通过登录页登录”（在新窗口打开完整登录页）。
     *
     * <p>弹框**不给用户关**：没有关闭按钮，也不响应点空白处与 ESC——会话没恢复之前，
     * 用户就该待在这一步；唯一的收口是登录成功（走上面的 goto() → 父页关弹框）。</p>
     */
    function initPopupLayout() {
        if (!CFG.popup) { return; }
        applyAvailHeight(availFromUrl());
        // 窗口缩放时父页会再发一次可用高度（二维码跟着放大/缩小）
        window.addEventListener("message", function (e) {
            if (!e || e.origin !== location.origin) { return; }
            var data = e.data;
            if (!data || typeof data !== "object" || data.oaonesRelogin !== "avail") { return; }
            applyAvailHeight(data.height);
        });
        if (window.ResizeObserver && document.body) {
            try {
                new window.ResizeObserver(reportHeight).observe(document.body);
            } catch (e) { /* 观察不到就靠下面的定时上报 */ }
        }
        window.addEventListener("resize", reportHeight);
        [0, 60, 200, 500, 1000].forEach(function (delay) { window.setTimeout(reportHeight, delay); });
        var full = byId("loginPopupFullBtn");
        if (!full) { return; }
        full.addEventListener("click", function () {
            // 完整登录页：真实页会 302 到对外地址 /oaones/login（弹框留着，用户也可以直接在那里登录）
            if (!window.open("/oaones/login/login.jsp", "_blank")) {
                toast("浏览器拦截了新窗口，请允许本站弹出窗口后重试", 2);
            }
        });
    }

    /* ------------------------------------------------------------------
     * 账号密码：直接调平台登录接口，不重实现校验
     * ------------------------------------------------------------------ */
    /**
     * 这次登录要不要填验证码。
     *
     * <p>以平台接口 /api/hrm/login/getLoginForm 回的 loginSetting.hasValidateCode 为准
     * （它与后台“组织权限中心 - 账户中心 - 安全设置”里的“验证码”是同一份设置）；
     * 接口还没回来或读取失败时，用服务端按同一份安全设置预读的 CFG.captcha.on 兜底——
     * 否则“系统开了验证码、页面却没给出输入框”，用户反而登录不进去。</p>
     */
    function isCaptchaOn(setting) {
        var v = setting ? setting.hasValidateCode : undefined;
        if (v === undefined || v === null || String(v).length === 0) {
            return !!(CFG.captcha && CFG.captcha.on);
        }
        return String(v) === "1";
    }

    /**
     * 平台是否开着“启用忘记密码功能”（安全设置 → 登录设置）。
     *
     * <p>以平台接口 /api/hrm/login/getLoginForm 回的 loginSetting.needforgotpassword 为准；
     * 接口还没回来或读取失败时，用服务端按同一份安全设置预读的 CFG.forgot.on 兜底。
     * 没开时**前台不显示“忘记密码”入口**，不给用户一个点进去也用不了的按钮。</p>
     */
    function isForgotOn(setting) {
        var v = setting ? setting.needforgotpassword : undefined;
        if (v === undefined || v === null || String(v).length === 0) {
            return !!(CFG.forgot && CFG.forgot.on);
        }
        return String(v) === "1";
    }

    /** “忘记密码”入口：账号密码登录开着 + 平台开着忘记密码功能，两个条件都满足才出现 */
    function syncForgotEntry(setting) {
        var pwd = Number(loginSettings.password_enable) === 1;
        var on = isForgotOn(setting || loginSetting);
        byId("loginForgotLink").style.display = (pwd && on) ? "" : "none";
    }

    function showCapture(loginSetting) {
        var setting = loginSetting || {};
        var hasCaptcha = isCaptchaOn(setting);
        byId("loginCaptchaRow").style.display = hasCaptcha ? "" : "none";
        if (hasCaptcha) {
            // 服务端已按安全设置渲染过首张码（src 非空）时不再多取一张；否则补一张
            if (!byId("loginCaptchaImg").getAttribute("src")) { refreshCaptcha(); }
        } else {
            byId("validatecode").value = "";
        }
        // “忘记密码”入口跟着平台的“启用忘记密码功能”显示 / 隐藏
        syncForgotEntry(setting);
        // 动态密码：平台 loginSetting.showDynamicPwd 开着才显示（与“动态令牌”是两套，口径不变）
        byId("loginDynamicRow").style.display = String(setting.showDynamicPwd) === "1" ? "" : "none";
        // “动态令牌”（tokenAuthKey / userUsbType）口令框本页不提供：登录页不需要它，
        // 因此页面不渲染、提交也不带该参数（平台侧仍按自己收到的参数校验）
        return setting;
    }

    /** 取平台登录表单设置（验证码开关 / 验证码 key / RSA 开关等），失败时按“无验证码”降级 */
    function loadLoginSetting() {
        return postForm(API_GET_FORM, {logintype: "1", langid: LANG_ID,
            loginid: byId("loginid").value}).then(function (res) {
            var data = res && res.data ? res.data : res;
            return showCapture(data && data.loginSetting ? data.loginSetting : {});
        }).catch(function (e) {
            toast("读取登录设置失败（" + e.message + "），已按最简登录表单显示", 3, 0);
            return showCapture({});
        });
    }

    var loginSetting = {};

    /**
     * 换一张验证码（点图片、登录失败后都走这里）。
     *
     * <p>取图仍用平台自己的接口：验证码的位数与类型（数字 / 字母 / 中文）由后台
     * “组织权限中心 - 账户中心 - 安全设置”决定，图片是平台按那份设置渲染的，
     * 本页不自己生成、也不改校验口径；提交时按平台口径带上 validatecode 与 validateCodeKey。</p>
     */
    function refreshCaptcha() {
        var img = byId("loginCaptchaImg");
        var row = byId("loginCaptchaRow");
        if (!img || (row && row.style.display === "none")) { return; }
        // seriesnum_ 只用来让浏览器换一张图（平台按“安全设置”渲染），没有业务含义
        img.src = "/weaver/weaver.file.MakeValidateCode?seriesnum_=" + new Date().getTime();
        byId("validatecode").value = "";
    }

    function loadFormAndGoError() {
        loadLoginSetting().then(function (setting) {
            loginSetting = setting;
            refreshCaptcha();
        });
    }

    /**
     * RSA 加密（系统开启 openRSA 时用）：与平台登录页同一套口径。
     *
     * 公钥 / 盐 / 分隔符来自平台自己的接口 /rsa/weaver.rsa.GetRsaInfo
     * （与 /js/rsa/rsa.js 里 refreshRsaCode 取的是同一个地址、同一份数据），
     * 加密算法用平台自带的 /js/rsa/jsencrypt.js；密文口径与 rsa.js 的 rsa_data_encrypt 一致：
     * RSA(值 + 盐) + 分隔符。
     * 服务端仍按平台既有流程解密后再做密码校验，不改变任何校验口径。
     *
     * 加密组件按需加载一次、全页复用；加载失败不缓存，下次点登录会重新加载。
     */
    var rsaScriptPromise = null;

    function loadRsaScript() {
        if (window.JSEncrypt) { return Promise.resolve(); }
        if (!rsaScriptPromise) {
            rsaScriptPromise = new Promise(function (resolve, reject) {
                var s = document.createElement("script");
                s.src = "/js/rsa/jsencrypt.js";
                s.async = true;
                s.onload = function () {
                    if (window.JSEncrypt) { resolve(); } else { reject(new Error("加密组件加载异常")); }
                };
                s.onerror = function () { reject(new Error("加密组件加载失败")); };
                document.head.appendChild(s);
            });
            rsaScriptPromise.catch(function () { rsaScriptPromise = null; });
        }
        return rsaScriptPromise;
    }

    /** 取一份公钥 / 盐 / 分隔符：每次提交前都重取（盐与会话绑定，过期后要用新的） */
    function fetchRsaKeys() {
        return fetch("/rsa/weaver.rsa.GetRsaInfo?ts=" + new Date().getTime(),
                {credentials: "same-origin"})
            .then(function (r) {
                if (!r.ok) { throw new Error("HTTP " + r.status); }
                return r.json();
            })
            .then(function (data) {
                if (!data || !data.rsa_pub) { throw new Error("未取到 RSA 公钥"); }
                return data;
            });
    }

    /**
     * 加密单个值：RSA(值 + 盐) + 分隔符（与平台 rsa.js 的 rsa_data_encrypt 单段口径一致，
     * 账号与密码都是短值，与平台登录页走的也是同一条分支）。
     *
     * 平台 rsa.js 里另有“超过 240 字符分段”的分支，但 2048 位密钥单段最多 245 字节，
     * 240 字符 + 8 位盐已超限（那一段实际用不了），所以这里不做分段：
     * 本页账号 / 密码输入框限长 100，正常输入不会超；真超长时直接报错，不把明文或半成品提交上去。
     */
    function rsaEncryptValue(keys, value) {
        var encrypt = new window.JSEncrypt();
        encrypt.setPublicKey(keys.rsa_pub);
        var salt = keys.rsa_code === undefined || keys.rsa_code === null ? "" : String(keys.rsa_code);
        var flag = keys.rsa_flag === undefined || keys.rsa_flag === null ? "" : String(keys.rsa_flag);
        var out = encrypt.encrypt(value + salt);
        if (!out) { throw new Error("无法加密（账号或密码可能过长）"); }
        return out + flag;
    }

    /** 账号密码的加密结果：{loginid, userpassword} 都是密文 */
    function rsaEncryptCredentials(loginid, password) {
        return loadRsaScript().then(fetchRsaKeys).then(function (keys) {
            return {
                loginid: rsaEncryptValue(keys, loginid),
                userpassword: rsaEncryptValue(keys, password)
            };
        });
    }

    /**
     * 提交登录（表单校验 + 按需加密），真正的提交在 postPasswordLogin 里。
     */
    function submitLogin() {
        var loginid = byId("loginid").value.replace(/^\s+|\s+$/g, "");
        var password = byId("userpassword").value;
        var msgEl = byId("loginMsg");
        if (loginid.length === 0) { toast("请输入登录名", 1); byId("loginid").focus(); return; }
        if (password.length === 0) { toast("请输入密码", 1); byId("userpassword").focus(); return; }
        var hasCaptcha = isCaptchaOn(loginSetting);
        var captcha = byId("validatecode").value.replace(/^\s+|\s+$/g, "");
        if (hasCaptcha && captcha.length === 0) { toast("请输入验证码", 1); byId("validatecode").focus(); return; }
        // 登录页不提供“动态令牌”口令框，所以这里不校验、提交也不带 tokenAuthKey
        var remember = byId("loginRemember").checked;
        setMsg(msgEl, "", "");
        if (String(loginSetting.openRSA) !== "1") {
            postPasswordLogin(loginid, password, loginid, password, captcha, remember);
            return;
        }
        // 开启 RSA：先下公钥再加密（异步），期间按钮置灰避免重复提交
        var btn = byId("loginSubmitBtn");
        btn.disabled = true;
        btn.textContent = "加密处理中…";
        rsaEncryptCredentials(loginid, password).then(function (cred) {
            postPasswordLogin(cred.loginid, cred.userpassword, loginid, password, captcha, remember);
        }).catch(function (e) {
            btn.disabled = false;
            btn.textContent = "登 录";
            setMsg(msgEl, "登录失败：登录加密失败（" + e.message + "），请刷新页面后重试", "fail");
            toast("登录失败：登录加密失败（" + e.message + "）", 3, 0);
        });
    }

    /**
     * 真正提交账号密码。
     *
     * <p>开启 RSA 时 loginid / password 传的是密文；plainLoginid / plainPassword 只用于
     * “记住账户密码”与日志口径，不参与提交。</p>
     */
    function postPasswordLogin(loginid, password, plainLoginid, plainPassword, captcha, remember) {
        var msgEl = byId("loginMsg");
        var btn = byId("loginSubmitBtn");
        btn.disabled = true;
        btn.textContent = "登录中…";
        setMsg(msgEl, "", "");
        postForm(API_CHECK, {
            islanguid: LANG_ID,
            loginid: loginid,
            userpassword: password,
            dynamicPassword: byId("dynamicPassword").value,
            validatecode: captcha,
            validateCodeKey: loginSetting.validateCodeKey || "",
            logintype: "1",
            messages: "",
            isie: "false",
            isRememberPassword: remember ? "1" : "0"
        }).then(function (res) {
            btn.disabled = false;
            btn.textContent = "登 录";
            var ok = String(res.loginstatus) === "true" || String(res.loginstatus) === "1";
            if (ok) {
                if (remember) { saveRememberedCredentials(plainLoginid, plainPassword); }
                else { clearRememberedCredentials(); }
                // 单一登录（一机一账号）之外的集成：平台要求先去 CAS / 千里眼认证时按平台给到的地址跳
                if (String(res.casflag) === "1" && res.casurl) { gotoCas(res.casurl); return; }
                if (String(res.qysflag) === "1" && res.authUrl) { gotoCas(res.authUrl); return; }
                if (String(res.msgcode) === "101" && res.msg) {
                    // 密码即将过期：平台会登录成功并提示修改，这里提示后用同样口径继续进入 OA
                    toast(res.msg, 1, 0);
                }
                goto(CFG.gopage && CFG.gopage.length > 0 ? CFG.gopage : CFG.landing);
                return;
            }
            setMsg(msgEl, res.msg || "登录失败，请检查账号密码后重试", "fail");
            toast(res.msg || "登录失败，请检查账号密码后重试", 2);
            if (String(res.qysflag) === "-100" && res.qysmg) { toast(res.qysmg, 2, 0); }
            loadFormAndGoError();
        }).catch(function (e) {
            btn.disabled = false;
            btn.textContent = "登 录";
            setMsg(msgEl, "登录请求失败：" + e.message, "fail");
            toast("登录请求失败：" + e.message, 3, 0);
        });
    }

    /** 可逆混淆（XOR + Base64）：避免密码以明文出现在 localStorage，不能替代浏览器密码管理器 */
    function maskText(text) {
        var out = "";
        for (var i = 0; i < text.length; i++) {
            out += String.fromCharCode(text.charCodeAt(i) ^ CRED_MASK.charCodeAt(i % CRED_MASK.length));
        }
        return out;
    }

    function encodeCredentials(loginid, password) {
        var json = JSON.stringify({loginid: loginid, password: password});
        return btoa(maskText(unescape(encodeURIComponent(json))));
    }

    function decodeCredentials(raw) {
        return decodeURIComponent(escape(maskText(atob(raw))));
    }

    function saveRememberedCredentials(loginid, password) {
        try {
            localStorage.setItem(LS_CREDENTIALS, encodeCredentials(loginid, password));
        } catch (e) { /* 隐私模式 / 空间不足：忽略 */ }
    }

    function clearRememberedCredentials() {
        try {
            localStorage.removeItem(LS_CREDENTIALS);
        } catch (e) { /* 隐私模式下忽略 */ }
    }

    function loadRememberedCredentials() {
        try {
            var raw = localStorage.getItem(LS_CREDENTIALS);
            if (raw) {
                var obj = JSON.parse(decodeCredentials(raw));
                if (obj && typeof obj.loginid === "string" && typeof obj.password === "string") {
                    return obj;
                }
            }
        } catch (e) { /* 数据损坏时按未记住处理 */ }
        return null;
    }

    /** CAS / 千里眼这类平台侧认证地址可能带协议头，交给浏览器整页跳转 */
    function gotoCas(url) {
        // 弹框模式：这类认证页不能困在 iframe 里，交父页整页跳
        if (notifyParent({oaonesRelogin: "cas", url: url})) { return; }
        try { location.replace(url); } catch (e) { location.href = url; }
    }

    /* ------------------------------------------------------------------
     * 验证登录（登录页的第二个页签）：手机号 / 邮箱两种验证方式
     *
     * 验证码由本模块自己生成、发送与校验（发送冷却、每日次数上限、有效期、错误次数上限
     * 都由服务端按系统设置判断），页面只做格式校验、重发倒计时与提示：
     *   手机号 → login.smsSend / login.smsLogin（发短信走平台“短信设置”的通道）
     *   邮箱   → login.emailSend / login.emailLogin（发邮件走平台“邮件设置”的群发邮箱）
     * 两种方式的页面口径完全一致，只有提示语、格式校验与提交的 action / 参数不同。
     * ------------------------------------------------------------------ */
    /** 当前验证方式：mobile（手机号）/ email（邮箱） */
    var VERIFY_CHANNEL = "mobile";
    /** 该页签里可用的验证方式（取自统一登录设置：mobile_enable / email_enable） */
    var verifyChannels = {mobile: false, email: false};
    /** 获取验证码的重发冷却秒数（与服务端 login.sms.cooldown 的默认值一致，避免连点被限流） */
    var VERIFY_SEND_COOLDOWN = 60;
    var verifySendTimer = 0;

    function isEmailChannel() { return VERIFY_CHANNEL === "email"; }

    /**
     * 手机号 / 邮箱格式校验。
     *
     * <p>邮箱规则与服务端 {@code LoginSmsServiceImpl#EMAIL_PATTERN} **必须完全一致**：
     * 页面先拦一次是为了给即时提示，服务端还会再拦一次（页面拦不住就直接调接口的情况）。</p>
     */
    function verifyTargetOk(value) {
        return isEmailChannel()
            ? /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/.test(value)
            : /^1\d{10}$/.test(value);
    }

    /** 输入不合法时的提示（两种验证方式各一句） */
    function verifyTargetHint() {
        return isEmailChannel() ? "请输入正确的邮箱地址" : "请输入正确的 11 位手机号";
    }

    /**
     * 切换验证方式：输入框（标签 / 提示 / 校验 / 长度）、验证码框、发送按钮、说明一行整套跟着换，
     * 并清掉上一次留下的内容与提示——换方式后上一次的验证码已经不再适用。
     */
    function setVerifyChannel(channel) {
        VERIFY_CHANNEL = channel === "email" ? "email" : "mobile";
        var email = isEmailChannel();
        byId("verifyChannelMobile").checked = !email;
        byId("verifyChannelEmail").checked = email;
        byId("verifyTargetLabel").textContent = email ? "邮箱" : "手机号";
        var target = byId("verifyTarget");
        target.value = "";
        target.setAttribute("placeholder", email ? "请输入在 OA 里维护的邮箱" : "请输入在 OA 里维护的手机号");
        target.setAttribute("autocomplete", email ? "email" : "tel");
        target.setAttribute("maxlength", email ? "128" : "20");
        byId("verifyCodeLabel").textContent = email ? "邮箱验证码" : "手机验证码";
        var code = byId("verifyCode");
        code.value = "";
        code.setAttribute("placeholder", email ? "请输入邮箱验证码" : "请输入短信验证码");
        byId("verifySendBtn").setAttribute("title", email ? "向该邮箱发送验证码" : "向该手机号发送验证码");
        var tip = byId("verifyTip");
        if (tip) {
            tip.textContent = email
                ? "验证码由 OA 发到该邮箱（发送频率与有效期以系统设置为准）"
                : "验证码由 OA 统一发送（发送频率与有效期以系统设置为准）";
        }
        stopVerifyCooldown();
        var btn = byId("verifySendBtn");
        btn.disabled = false;
        btn.textContent = "获取验证码";
        setMsg(byId("verifyMsg"), "", "");
    }

    /**
     * 按“登录设置”渲染该页签里的验证方式：
     * 两种都开 → 出现“验证方式”切换；只开一种 → 只出现这一种（不给用户一个用不了的选项）。
     */
    function renderVerifyChannels() {
        verifyChannels.mobile = Number(loginSettings.mobile_enable) === 1;
        verifyChannels.email = Number(loginSettings.email_enable) === 1;
        byId("verifyMethodRow").style.display =
            (verifyChannels.mobile && verifyChannels.email) ? "" : "none";
        // 默认落在已开启的方式上（都开时默认手机号；只开邮箱时默认邮箱）
        setVerifyChannel(verifyChannels.mobile ? "mobile" : "email");
    }

    /** 提交/发送时要带的参数：手机号提交 mobile、邮箱提交 email（服务端按同名参数取值） */
    function verifyApiParams(extra) {
        var params = extra || {};
        params[isEmailChannel() ? "email" : "mobile"] =
            byId("verifyTarget").value.replace(/^\s+|\s+$/g, "");
        return params;
    }

    function verifySendCode() {
        var target = byId("verifyTarget").value.replace(/^\s+|\s+$/g, "");
        var msgEl = byId("verifyMsg");
        if (target.length === 0) {
            toast(isEmailChannel() ? "请输入邮箱" : "请输入手机号", 1);
            byId("verifyTarget").focus();
            return;
        }
        if (!verifyTargetOk(target)) {
            toast(verifyTargetHint(), 1);
            byId("verifyTarget").focus();
            return;
        }
        var btn = byId("verifySendBtn");
        btn.disabled = true;
        actionApi(isEmailChannel() ? "login.emailSend" : "login.smsSend", verifyApiParams({}))
            .then(function (res) {
                var data = (res && res.data) || {};
                if (!res || res.code !== 0) {
                    btn.disabled = false;
                    setMsg(msgEl, (res && res.msg) || "验证码发送失败", "fail");
                    return;
                }
                var ok = String(data.status) === "1";
                setMsg(msgEl, data.info || (ok
                    ? (isEmailChannel() ? "验证码已发送，请查收邮件" : "验证码已发送，请查收短信")
                    : "验证码发送失败"), ok ? "ok" : "fail");
                if (ok) {
                    startVerifyCooldown();
                    byId("verifyCode").focus();
                } else {
                    btn.disabled = false;
                }
            }).catch(function (e) {
                btn.disabled = false;
                setMsg(msgEl, "验证码发送失败：" + e.message, "fail");
            });
    }

    function startVerifyCooldown() {
        var btn = byId("verifySendBtn");
        var left = VERIFY_SEND_COOLDOWN;
        stopVerifyCooldown();
        var tick = function () {
            if (left <= 0) {
                stopVerifyCooldown();
                btn.disabled = false;
                btn.textContent = "获取验证码";
                return;
            }
            btn.disabled = true;
            btn.textContent = left + " 秒后重发";
            left--;
        };
        tick();
        verifySendTimer = setInterval(tick, 1000);
    }

    function stopVerifyCooldown() {
        if (verifySendTimer) { clearInterval(verifySendTimer); verifySendTimer = 0; }
    }

    function submitVerifyLogin() {
        var target = byId("verifyTarget").value.replace(/^\s+|\s+$/g, "");
        var code = byId("verifyCode").value.replace(/^\s+|\s+$/g, "");
        var msgEl = byId("verifyMsg");
        var email = isEmailChannel();
        if (target.length === 0) {
            toast(email ? "请输入邮箱" : "请输入手机号", 1);
            byId("verifyTarget").focus();
            return;
        }
        if (!verifyTargetOk(target)) {
            toast(verifyTargetHint(), 1);
            byId("verifyTarget").focus();
            return;
        }
        if (code.length === 0) {
            toast(email ? "请输入邮箱验证码" : "请输入手机验证码", 1);
            byId("verifyCode").focus();
            return;
        }
        var btn = byId("verifySubmitBtn");
        btn.disabled = true;
        btn.textContent = "登录中…";
        setMsg(msgEl, "", "");
        actionApi(email ? "login.emailLogin" : "login.smsLogin",
            verifyApiParams({code: code, gopage: CFG.gopage})).then(function (res) {
            btn.disabled = false;
            btn.textContent = "登 录";
            if (!res || res.code !== 0) {
                setMsg(msgEl, (res && res.msg) || (email ? "邮箱登录失败，请重试" : "手机号登录失败，请重试"), "fail");
                toast((res && res.msg) || (email ? "邮箱登录失败，请重试" : "手机号登录失败，请重试"), 2);
                return;
            }
            var landing = (res.data && res.data.landing_url) || CFG.landing;
            goto(CFG.gopage && CFG.gopage.length > 0 ? CFG.gopage : landing);
        }).catch(function (e) {
            btn.disabled = false;
            btn.textContent = "登 录";
            setMsg(msgEl, (email ? "邮箱登录请求失败：" : "手机号登录请求失败：") + e.message, "fail");
        });
    }

    /* ------------------------------------------------------------------
     * 统一登录设置：决定登录页显示哪些登录方式（只有开启的才出现）
     * ------------------------------------------------------------------ */
    // 读取失败时的默认口径：账号密码 + 扫码（扫码还要看“登录设置”里哪个平台开着）
    var loginSettings = {password_enable: 1, mobile_enable: 0, email_enable: 0,
        wecom_qr_enable: 1, dingtalk_qr_enable: 0, feishu_qr_enable: 0, qr_enable: true, available: true};

    function loadLoginSettings() {
        return actionApi("login.settings", {}).then(function (res) {
            if (res && res.code === 0 && res.data) {
                loginSettings = res.data;
            }
            return renderLoginMethods();
        }).catch(function () {
            // 读取失败时按默认口径（账号密码 + 扫码）显示，避免整页不可用
            return renderLoginMethods();
        });
    }

    /** 按设置显示/隐藏各登录方式；没有可用方式时给出明确提示 */
    function renderLoginMethods() {
        var pwd = Number(loginSettings.password_enable) === 1;
        var mobile = Number(loginSettings.mobile_enable) === 1;
        var email = Number(loginSettings.email_enable) === 1;
        // “验证登录”页签：手机号 / 邮箱任一开启就出现（页签里的验证方式由 renderVerifyChannels 渲染）
        var verify = mobile || email;
        // 扫码登录是同一张二维码：三个平台里只要有一个开着，页签就出现
        var scan = Number(loginSettings.wecom_qr_enable) === 1
            || Number(loginSettings.dingtalk_qr_enable) === 1
            || Number(loginSettings.feishu_qr_enable) === 1;
        if (!pwd && !verify && !scan) {
            var box = byId("loginCard");
            if (box) {
                box.innerHTML = '<div class="login-msg fail" style="margin:0;">'
                    + '当前没有开启任何可用的登录方式，请联系管理员在“登录设置”里至少开启'
                    + '账号密码登录、验证登录（手机号 / 邮箱）、扫码登录中的一种。</div>';
            }
            return;
        }
        byId("loginTabPwd").style.display = pwd ? "" : "none";
        byId("loginTabVerify").style.display = verify ? "" : "none";
        byId("loginTabScan").style.display = scan ? "" : "none";
        // 关闭账号密码登录、或平台没开“启用忘记密码功能”时，“忘记密码”入口都不出现
        syncForgotEntry();
        // 验证登录页签里出现哪些验证方式（只开一种时把另一种收起来）：
        // 必须在 switchTab 之前渲染——切到该页签时要聚焦正确的那个输入框
        if (verify) { renderVerifyChannels(); }
        // 默认落在第一个可用的方式上（地址上带 ?tab=scan 且扫码可用时优先扫码）
        var tab = (CFG.scanTabFirst && scan) ? "scan" : (pwd ? "pwd" : (verify ? "verify" : "scan"));
        switchTab(tab);
        // 弹框模式：把各页签面板的公共高度量出来（切页签时弹框高度保持一致）
        if (CFG.popup) { measurePopupPaneHeight(); }
        if (CFG.scanTabFirst && !scan && tab !== "scan") {
            // 地址指定了扫码但扫码被关闭：提示一下，避免用户以为页面出错
            toast("扫码登录当前已关闭，已切换到其他登录方式", 1, 0);
        }
    }

    /** 二维码上的状态提示：扫码成功 / 已超时（覆盖在码图上，文案与需求口径一致） */
    function setQrOverlay(text, state) {
        var el = byId("loginQrOverlay");
        if (!el) { return; }
        var value = text == null ? "" : String(text);
        el.textContent = value;
        el.className = "login-qr-overlay" + (state ? " " + state : "");
        el.style.display = value.length > 0 ? "" : "none";
    }

    /* ------------------------------------------------------------------
     * 扫码登录（二维码由本系统自己生成）
     *
     * 二维码内容只是本站的扫码落地页 + 票据（login.qrCreate 生成、平台与租户都无关），
     * 扫码方（企业微信 / 钉钉 / 飞书）打开该页后由 OA 侧按客户端判断平台、
     * 换成员身份、按人员绑定关系找 OA 账号；
     * 本页只做两件事：① 轮询票据状态；② 命中“已确认”时用一次性令牌换登录会话
     * （换会话必须在**本页自己的请求**里做，Cookie 才会落到这台电脑上）。
     * ------------------------------------------------------------------ */
    function renderTenants(data) {
        qrPlatforms = (data && data.platforms) || [];
        qrUsable = Number(data && data.usable_total) || 0;
        qrTenantsReady = true;
        if (qrUsable === 0) {
            // 没有一个平台可用：把管理员该补什么说清楚，同时保留账号密码 / 手机号登录
            var reasons = qrPlatforms.filter(function (p) {
                return p.enabled === true && p.reason;
            }).map(function (p) {
                return p.label + "：" + p.reason;
            });
            var reason = reasons.length > 0 ? reasons.join("；")
                : "管理员还没有开启任何平台的扫码登录";
            byId("loginQrBox").innerHTML = '<div class="login-qr-loading">扫码登录暂不可用：'
                + esc(reason) + "</div>";
            byId("loginQrExpire").parentNode.style.display = "none";
            setMsg(byId("loginScanMsg"), "扫码登录暂不可用（" + reason
                + "），请联系管理员处理，或改用其它登录方式。", "fail");
            return false;
        }
        // 一张二维码覆盖全部平台的全部可用租户（扫码时按客户端判断平台、再逐家识别企业），
        // 这里只把“支持哪些平台、共几家企业”告诉用户；弹框里不放这句说明（保持简洁）
        if (!CFG.popup) {
            var parts = qrPlatforms.filter(function (p) { return Number(p.usable_count) > 0; })
                .map(function (p) { return p.label + " " + p.usable_count + " 家"; });
            setMsg(byId("loginScanMsg"), "本二维码支持：" + parts.join("、")
                + "（共 " + qrUsable + " 家租户，扫码后自动识别平台与你所在的企业）。", "");
        }
        return true;
    }

    /** 浏览器标识：本页生成一次，创建/轮询/换登录都带同一个值（别人拿到票据也换不了登录） */
    function browserKeyOf() {
        if (qrBrowserKey) { return qrBrowserKey; }
        var store = "oaones.login.browserkey";
        var key = "";
        try {
            key = sessionStorage.getItem(store) || "";
            if (key.length < 16) {
                key = randomKey();
                sessionStorage.setItem(store, key);
            }
        } catch (e) {
            key = randomKey();
        }
        qrBrowserKey = key;
        return key;
    }

    function randomKey() {
        var chars = "0123456789abcdef";
        var out = "";
        for (var i = 0; i < 32; i++) {
            out += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return out;
    }

    /** 生成（或刷新）二维码：先作废旧票据，再建新票据并开始轮询 */
    function startQr(keepHint) {
        if (qrLoading) { return; }
        qrLoading = true;
        stopPolling();
        qrConfirmed = false;
        var old = qrTicket;
        qrTicket = "";
        setQrOverlay("", "");
        byId("loginQrBox").innerHTML = '<div class="login-qr-loading">正在生成二维码…</div>';
        // 弹框里不写这句：码框里本来就有“正在生成二维码…”的占位，再写一句就是多一条小字
        if (!keepHint && !CFG.popup) { setMsg(byId("loginScanMsg"), "二维码生成中…", ""); }
        var cancelFirst = old ? actionApi("login.qrCancel", {
            ticket: old, browser_key: browserKeyOf()
        }).catch(function () { /* 作废失败不影响重建 */ }) : Promise.resolve();
        cancelFirst.then(function () {
            return actionApi("login.qrCreate", {
                browser_key: browserKeyOf(), gopage: CFG.gopage
            });
        }).then(function (res) {
            qrLoading = false;
            if (!res || res.code !== 0) {
                // 码框整块都可以点 = 重试，所以提示里直接写“点这里重试”（页面上没有刷新按钮）
                byId("loginQrBox").innerHTML = '<div class="login-qr-loading">二维码生成失败，点这里重试</div>';
                setMsg(byId("loginScanMsg"), (res && res.msg) || "二维码生成失败，请稍后重试", "fail");
                return;
            }
            var data = res.data || {};
            qrTicket = String(data.ticket || "");
            qrPollMs = Number(data.poll_ms) > 200 ? Number(data.poll_ms) : 1500;
            qrExpireAt = Number(data.expire_at) || 0;
            if (data.landing_url) { CFG.landing = data.landing_url; }
            renderQrImage(data.qr_image_url);
            // 建码成功：整页登录页里写“拿什么扫、怎么确认”；弹框里不放这类说明（页签已说明是扫码，
            // 码上的状态提示会跟着走），但**必须把上一次留下的文案清干净**——否则刷新二维码后
            // “二维码生成中…”/上一次的失败提示会一直挂在码下面（弹框里空文案不占位，见 login.css）
            setMsg(byId("loginScanMsg"), CFG.popup ? "" : (data.need_confirm === false
                ? "请用本人的" + scanClients() + "扫一扫，扫码后本页会自动进入 OA。"
                : "请用本人的" + scanClients() + "扫一扫，并在手机上点“确认登录”。"), "");
            startPolling();
            startCountdown();
        }).catch(function (e) {
            qrLoading = false;
            byId("loginQrBox").innerHTML = '<div class="login-qr-loading">二维码生成失败，点这里重试</div>';
            setMsg(byId("loginScanMsg"), "二维码生成失败：" + e.message, "fail");
        });
    }

    /** 自建二维码图片（本站渲染的 SVG，不是企业微信 / 钉钉 / 飞书托管的二维码页） */
    function renderQrImage(url) {
        var box = byId("loginQrBox");
        if (!url) {
            box.innerHTML = '<div class="login-qr-loading">二维码地址为空</div>';
            return;
        }
        // 重建二维码时把状态提示节点一起补回来（overlay 覆盖在码图上）
        box.innerHTML = '<div class="login-qr-overlay" id="loginQrOverlay" style="display:none;"></div>';
        var img = document.createElement("img");
        img.className = "login-qr-img";
        img.setAttribute("alt", "扫码登录二维码");
        img.setAttribute("title", "请用本人的" + scanClients() + "扫一扫");
        img.onerror = function () {
            box.innerHTML = '<div class="login-qr-loading">二维码加载失败，点这里重试</div>';
        };
        img.src = url;
        box.appendChild(img);
    }

    /** 轮询票据状态：命中“已确认”就用一次性令牌在本页换登录会话 */
    function startPolling() {
        stopPolling();
        qrPollTimer = setInterval(function () {
            if (!qrTicket) { return; }
            actionApi("login.qrStatus", {ticket: qrTicket, browser_key: browserKeyOf()})
                .then(function (res) {
                    if (!res || res.code !== 0) {
                        // 票据不存在（服务端已清理）等：停止轮询并提示点码重来
                        stopPolling();
                        setMsg(byId("loginScanMsg"), (res && res.msg) || "二维码已失效，请点一下二维码重新生成", "fail");
                        return;
                    }
                    onQrStatus(res.data || {});
                }).catch(function (e) {
                    stopPolling();
                    setMsg(byId("loginScanMsg"), "查询扫码状态失败：" + e.message, "fail");
                });
        }, qrPollMs);
    }

    function stopPolling() {
        if (qrPollTimer) { clearInterval(qrPollTimer); qrPollTimer = 0; }
    }

    function stopCountdown() {
        if (qrCountTimer) { clearInterval(qrCountTimer); qrCountTimer = 0; }
    }

    function onQrStatus(data) {
        var state = Number(data.state);
        if (state === 0) {
            // 等待扫码：保持当前提示（不刷屏），只更新剩余时间
            return;
        }
        if (state === 1) {
            var who = data.out_user_name || data.out_user_id || "";
            var corp = data.cfg_name ? "（" + data.cfg_name + "）" : "";
            setMsg(byId("loginScanMsg"), "「" + who + "」" + corp + "已扫码，请在手机上点“确认登录”。", "");
            // 二维码上直接提示，扫码人不用盯着下方文字
            setQrOverlay("已扫码成功，请在手机上确认登录", "scanned");
            return;
        }
        if (state === 2) {
            stopPolling();
            stopCountdown();
            qrConfirmed = true;
            setQrOverlay("已确认登录，正在进入 OA…", "confirmed");
            setMsg(byId("loginScanMsg"), "已确认，正在进入 OA…", "ok");
            actionApi("login.qrLogin", {
                ticket: qrTicket, browser_key: browserKeyOf(), token: String(data.login_token || "")
            }).then(function (res) {
                if (!res || res.code !== 0) {
                    setMsg(byId("loginScanMsg"), (res && res.msg) || "登录失败，请点一下二维码重试", "fail");
                    return;
                }
                var landing = (res.data && res.data.landing_url) || CFG.landing;
                goto(CFG.gopage && CFG.gopage.length > 0 ? CFG.gopage : landing);
            }).catch(function (e) {
                setMsg(byId("loginScanMsg"), "登录失败：" + e.message, "fail");
            });
            return;
        }
        // 失败（3）/ 过期（4）/ 已使用（5）：停止轮询，把原因留在页面上
        stopPolling();
        stopCountdown();
        var msg = data.msg || (state === 4 ? "二维码已过期，点一下二维码重新生成" : "扫码登录失败，请重试");
        setMsg(byId("loginScanMsg"), msg, "fail");
        if (state === 4) {
            setQrOverlay("已超时，点二维码重新生成", "expired");
        } else {
            setQrOverlay("", "");
            toast(msg, 2, 0);
        }
    }

    /** 剩余有效期倒计时（到点停止轮询并提示刷新） */
    function startCountdown() {
        stopCountdown();
        if (!qrExpireAt) { return; }
        var tick = function () {
            var left = Math.max(0, Math.round((qrExpireAt - new Date().getTime()) / 1000));
            var el = byId("loginQrExpire");
            // 前台按秒倒计时（有效期最长 180 秒，按分钟显示看不出变化）
            if (el) { el.textContent = String(left); }
            if (left <= 0) {
                stopCountdown();
                stopPolling();
                // 超时口径：二维码上直接提示怎么继续（页面上没有“刷新”按钮，点码图就是刷新）
                setQrOverlay("已超时，点二维码重新生成", "expired");
                setMsg(byId("loginScanMsg"), "二维码已超时，点一下二维码重新生成后再扫。", "fail");
            }
        };
        tick();
        qrCountTimer = setInterval(tick, 1000);
    }

    /**
     * 刷新二维码（重新生成票据 + 重新计时）：**点二维码图片**就是刷新（页面上没有“刷新二维码”
     * “在新窗口打开扫码页”这类按钮，码框上带 title 提示、超时时码上也会提示）。
     *
     * <p>已确认登录（正在进 OA）、正在建码、或当前没有可用租户时不重来——
     * 这几个阶段页面已经给出了原因，重来只会把那条提示冲掉。</p>
     */
    function refreshQrByClick() {
        if (qrConfirmed || qrLoading) { return; }
        // 还没问过“有没有可用租户”、或上次问失败：这次点码就重新问一次（当作重试）
        if (!qrTenantsReady) { loadQrTenants(); return; }
        // 确认过没有任何可用租户：页面已给出原因，不重来（否则只会把那条原因冲掉）
        if (qrUsable <= 0) { return; }
        startQr(false);
    }

    /* ------------------------------------------------------------------
     * 忘记密码：本页自己的弹层，弹层里是本模块自己的页面
     * （/oaones/login/forgotPassword.jsp，以 iframe 引入后按需加载）
     *
     * 全程**不跳平台登录页**：密码找回本身仍走平台自己的“忘记密码”接口
     * （短信 / 邮件 / 密保问题 + 平台密码策略），口径与平台一致，页面换成本模块自己的。
     * 弹层与本页同源，靠 postMessage 通知“关掉 / 已重置”（登录页据此关弹层并提示重新登录）。
     * ------------------------------------------------------------------ */
    var FORGOT_URL = "/oaones/login/forgotPassword.jsp";

    function openForgot() {
        var frame = byId("loginForgotFrame");
        if (!frame.getAttribute("src")) {
            frame.setAttribute("src", FORGOT_URL + "?langid=" + encodeURIComponent(LANG_ID));
        }
        byId("loginForgotModal").style.display = "";
        setMsg(byId("loginMsg"), "", "");
    }

    function closeForgot() {
        byId("loginForgotModal").style.display = "none";
        // 弹层可按住表头拖动：关掉时把拖动留下的定位去掉，下次打开仍由遮罩居中
        var box = byId("loginForgotModal").querySelector(".oa-modal");
        if (box) {
            box.style.position = "";
            box.style.left = "";
            box.style.top = "";
            box.style.transform = "";
            box.style.margin = "";
        }
        // 关掉就把弹层里的页面清空：下次打开重新走一遍（验证码是一次性的，不能复用）
        byId("loginForgotFrame").setAttribute("src", "about:blank");
    }

    function onForgotMessage(e) {
        var frame = byId("loginForgotFrame");
        // 只认同源、且确实来自本页弹层里那个 iframe 的消息
        if (e.origin !== location.origin || !frame || e.source !== frame.contentWindow) { return; }
        var data = e.data || {};
        if (data.oaonesForgotPassword === "done") {
            closeForgot();
            toast("密码已重置，请用新密码登录", 1, 0);
            byId("userpassword").focus();
        } else if (data.oaonesForgotPassword === "close") {
            closeForgot();
        }
    }

    /* ------------------------------------------------------------------
     * 页签与初始化（事件注册统一放页面末尾）
     * ------------------------------------------------------------------ */
    function switchTab(tab) {
        var pwd = tab === "pwd";
        var verify = tab === "verify";
        var scan = tab === "scan";
        byId("loginTabPwd").className = "login-tab" + (pwd ? " active" : "");
        byId("loginTabVerify").className = "login-tab" + (verify ? " active" : "");
        byId("loginTabScan").className = "login-tab" + (scan ? " active" : "");
        byId("loginPanePwd").style.display = pwd ? "" : "none";
        byId("loginPaneVerify").style.display = verify ? "" : "none";
        byId("loginPaneScan").style.display = scan ? "" : "none";
        if (scan) {
            // 第一次切到扫码页签（还没票据）时才生成二维码，来回切页签不会反复换二维码
            if (!qrTicket) { loadQrTenants(); }
            return;
        }
        if (verify) {
            byId("verifyTarget").focus();
            return;
        }
        byId("loginid").focus();
    }

    /** 先确认“有没有可用租户”，再生成二维码；没有可用租户时给出可执行的提示 */
    function loadQrTenants() {
        actionApi("login.tenants", {}).then(function (res) {
            if (!res || res.code !== 0) {
                setMsg(byId("loginScanMsg"), (res && res.msg) || "读取可扫码租户失败", "fail");
                byId("loginQrBox").innerHTML = '<div class="login-qr-loading">扫码登录暂不可用</div>';
                return;
            }
            var data = res.data || {};
            if (data.landing_url) { CFG.landing = data.landing_url; }
            if (renderTenants(data)) { startQr(true); }
        }).catch(function (e) {
            byId("loginQrBox").innerHTML = '<div class="login-qr-loading">扫码登录暂不可用</div>';
            setMsg(byId("loginScanMsg"), "读取可扫码租户失败：" + e.message, "fail");
        });
    }

    byId("loginTabPwd").addEventListener("click", function () { switchTab("pwd"); });
    byId("loginTabVerify").addEventListener("click", function () { switchTab("verify"); });
    byId("loginTabScan").addEventListener("click", function () { switchTab("scan"); });
    byId("loginForm").addEventListener("submit", function (e) {
        e.preventDefault();
        submitLogin();
    });
    byId("verifyForm").addEventListener("submit", function (e) {
        e.preventDefault();
        submitVerifyLogin();
    });
    byId("verifySendBtn").addEventListener("click", verifySendCode);
    // 验证方式（手机号 / 邮箱）：只改了选中态就整套换文案与校验，不需要重载页面
    byId("verifyChannelMobile").addEventListener("change", function () { setVerifyChannel("mobile"); });
    byId("verifyChannelEmail").addEventListener("change", function () { setVerifyChannel("email"); });
    byId("loginCaptchaImg").addEventListener("click", refreshCaptcha);
    // 取消勾选立即清掉本机保存的账号密码，避免只取消勾选却没登录时仍残留
    byId("loginRemember").addEventListener("change", function () {
        if (!this.checked) { clearRememberedCredentials(); }
    });
    // 点二维码图片 = 刷新并重新计时（页面上没有“刷新二维码”按钮，这就是唯一的刷新入口）
    byId("loginQrBox").addEventListener("click", refreshQrByClick);
    // 忘记密码：打开本页自己的弹层（不跳平台登录页），并接收弹层里的“关掉 / 已重置”
    byId("loginForgotLink").addEventListener("click", function (e) {
        e.preventDefault();
        openForgot();
    });
    byId("loginForgotClose").addEventListener("click", closeForgot);
    byId("loginForgotModal").addEventListener("mousedown", function (e) {
        // 点遮罩关闭（点弹层内部不关）
        if (e.target === byId("loginForgotModal")) { closeForgot(); }
    });
    window.addEventListener("message", onForgotMessage);

    (function init() {
        // 弹框模式：先把“卡片高度上报 + 通过登录页登录”装好
        // （页签切换、二维码出现时弹框才会跟着变高变矮）
        initPopupLayout();
        if (CFG.loggedIn) {
            // 已登录：不再让用户重登（避免一人一号口径被破坏），直接进入 OA
            byId("loginMsg").textContent = "当前已登录，正在进入 OA…";
            goto(CFG.gopage && CFG.gopage.length > 0 ? CFG.gopage : CFG.landing);
            return;
        }
        var remembered = loadRememberedCredentials();
        if (remembered) {
            byId("loginid").value = remembered.loginid;
            if (remembered.password.length > 0) { byId("userpassword").value = remembered.password; }
            byId("loginRemember").checked = true;
        }
        // 先用“空表单”问一次平台：验证码开关等由系统设置决定，页面据此显示对应输入项
        loadLoginSetting().then(function (setting) {
            loginSetting = setting;
            // 开了 RSA 就把加密组件先拉起来，免得点登录时才去下（失败无所谓，提交时会再试）
            if (String(setting.openRSA) === "1") { loadRsaScript(); }
        });
        // 统一登录设置决定显示哪些登录方式（只有开启的方式才会出现），默认落在第一个可用方式上
        loadLoginSettings();
        // 离开页面时把票据作废（服务端也会按有效期过期，这里只是尽快清理）
        window.addEventListener("beforeunload", function () {
            if (!qrTicket) { return; }
            try {
                var url = ACTIONS + "?action=login.qrCancel&ticket=" + encodeURIComponent(qrTicket)
                    + "&browser_key=" + encodeURIComponent(browserKeyOf());
                if (navigator.sendBeacon) {
                    navigator.sendBeacon(url);
                } else {
                    fetch(url, { credentials: "same-origin", keepalive: true });
                }
            } catch (e) { /* 关闭页面的兜底：失败也无所谓，票据会自己过期 */ }
        });
    })();
})();
</script>
</body>
</html>
