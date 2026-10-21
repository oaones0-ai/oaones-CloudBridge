package com.customization.oaones.appsyn.common;

/**
 * 扫码登录的“扫码平台”：企业微信 / 钉钉 / 飞书。
 *
 * <p>统一登录页（{@code /oaones/login/login.jsp}）上只有**一张**二维码：
 * 二维码内容只是本模块的扫码落地页（{@code /oaones/login/scan.jsp?t=票据}），
 * 不带“是哪家平台 / 哪家企业”的信息；扫码方打开该页后，由 OA 侧按扫码客户端的
 * User-Agent 判断平台，再用该平台的租户配置依次换成员身份，因此同一张二维码
 * 三个平台的客户端都能扫（各平台能否扫由“登录设置”里对应的开关决定）。
 * 因此新增一个平台只需要三处：本类加常量与 UA 识别、
 * {@code ScanLoginServiceImpl} 里补“授权地址 + 换取身份”两个方法、
 * 以及 properties 里补该平台的接入参数（见 README“扫码登录的平台接入”）。</p>
 */
public final class QrLoginPlatform {

    /** 企业微信（wxwork 客户端内置浏览器）。 */
    public static final String WECOM = "wecom";
    /** 钉钉（钉钉客户端内置浏览器）。 */
    public static final String DINGTALK = "dingtalk";
    /**
     * 飞书（飞书 / Lark 客户端内置浏览器）。

     * <p>飞书连接器与企微、钉钉各自的落地页与控制器是分开的：飞书扫码登录走
     * {@code /oaones/login/Feishu/scan.jsp} + {@code service.impl.feishu.ScanLoginServiceImpl}，
     * 用的是飞书租户的 App ID / App Secret，不跨连接器复用凭证。</p>
     */
    public static final String FEISHU = "feishu";

    private QrLoginPlatform() {
    }

    /** 支持扫码登录的全部平台（统一登录页按它遍历平台开关与租户）。 */
    public static java.util.List<String> all() {
        java.util.List<String> list = new java.util.ArrayList<String>();
        list.add(WECOM);
        list.add(DINGTALK);
        list.add(FEISHU);
        return list;
    }

    /** 是不是本模块认识的扫码平台（用于校验页面/state 里带回来的平台值）。 */
    public static boolean known(String platform) {
        return WECOM.equals(platform) || DINGTALK.equals(platform) || FEISHU.equals(platform);
    }

    /**
     * 登录设置里“该平台允许不允许扫码”的开关名（一行共享配置里的列名）。
     *
     * <p>命名口径：{@code <平台>_qr_enable}，三个平台共用同一行配置、同一张二维码。</p>
     */
    public static String enableKey(String platform) {
        return OaonesUtil.s(platform) + "_qr_enable";
    }

    /**
     * 按 User-Agent 判断扫码平台；识别不出返回空串。
     *
     * <p>用浏览器的 UA 而不是“让用户选平台”：员工本来就是用手机上的企业微信/钉钉
     * “扫一扫”打开二维码里的链接，UA 里一定带客户端标识，比让用户多选一步更省事。</p>
     */
    public static String detect(String userAgent) {
        String ua = OaonesUtil.s(userAgent).toLowerCase();
        if (ua.length() == 0) {
            return "";
        }
        // 企业微信：手机端/桌面端 UA 都带 wxwork（如 ... MicroMessenger/7.x wxwork/4.x ...）
        if (ua.indexOf("wxwork") >= 0) {
            return WECOM;
        }
        // 钉钉：手机端 UA 带 dingtalk，部分版本带 aliApp(DingTalk)
        if (ua.indexOf("dingtalk") >= 0 || ua.indexOf("dingtalkclient") >= 0) {
            return DINGTALK;
        }
        // 飞书：手机端/桌面端内置浏览器 UA 带 Lark（如 ... Lark/7.x ...），部分版本带 feishu
        if (ua.indexOf("lark") >= 0 || ua.indexOf("feishu") >= 0) {
            return FEISHU;
        }
        return "";
    }

    /** 平台名称（页面提示文案用）。 */
    public static String label(String platform) {
        if (WECOM.equals(platform)) {
            return "企业微信";
        }
        if (DINGTALK.equals(platform)) {
            return "钉钉";
        }
        if (FEISHU.equals(platform)) {
            return "飞书";
        }
        return "未知客户端";
    }

    /**
     * “登录设置”里**已经开启扫码**的平台标签（顺序固定：企业微信 / 钉钉 / 飞书）。
     *
     * <p>统一登录页（{@code /oaones/login/login.jsp}）与手机扫码落地页
     * （{@code /oaones/login/scan.jsp}）的提示文案都按它出：**没开启的平台不出现在文案里**
     * （后台没启用飞书时不提飞书、没启用钉钉时不提钉钉，企微同理），
     * 与“扫码页签在至少开启一个平台时出现”（{@code LoginConfigServiceImpl#publicSettings}）
     * 同一口径——页面与控制器里都不再写死“企业微信 / 钉钉 / 飞书”三家的名字。</p>
     *
     * <p>入参是 {@code /oaones/login/loginAction.jsp?action=login.settings} 回的公开设置
     * （{@code LoginConfigServiceImpl#publicSettings()}）；入参为 {@code null} 时一律视为未开启，
     * 返回空列表，调用方据此走“扫码登录已关闭”的兜底文案。</p>
     */
    public static java.util.List<String> enabledLabels(com.alibaba.fastjson.JSONObject settings) {
        java.util.List<String> labels = new java.util.ArrayList<String>();
        for (String platform : all()) {
            if (enabled(settings, platform)) {
                labels.add(label(platform));
            }
        }
        return labels;
    }

    /**
     * 已开启扫码的平台标签拼成文案里的“企业微信 / 钉钉”；**一个都没开时返回空串**
     * （调用方据此走兜底文案，而不是拼出“请用本人的扫一扫”这种半句话）。
     */
    public static String enabledLabelText(com.alibaba.fastjson.JSONObject settings) {
        return String.join(" / ", enabledLabels(settings));
    }

    /** 该平台是否已开启扫码（{@code <平台>_qr_enable}=1；设置为空时一律视为未开启）。 */
    public static boolean enabled(com.alibaba.fastjson.JSONObject settings, String platform) {
        return settings != null && settings.getIntValue(enableKey(platform)) == 1;
    }

    /**
     * 企微连接器的扫码落地逻辑（{@code ScanLoginServiceImpl}）是否处理该平台。
     *
     * <p>三个连接器的租户范围与应用凭证是分开的，所以换身份必须由**对应连接器**的实现来做：
     * 统一扫码落地页（{@code /oaones/login/scan.jsp}）先按客户端分派到对应连接器的控制器，
     * 再由该连接器换身份；企微这份实现只处理企业微信身份，
     * 不跨连接器换身份（否则会把企微租户的 Secret 用到钉钉/飞书接口上）。</p>
     */
    public static boolean supported(String platform) {
        return WECOM.equals(platform);
    }
}
