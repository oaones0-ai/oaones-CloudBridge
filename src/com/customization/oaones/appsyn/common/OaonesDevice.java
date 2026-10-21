package com.customization.oaones.appsyn.common;

/**
 * 客户端来源识别：区分「手机端」与「PC（桌面）端」。
 *
 * <p>企业微信打开应用主页时会把客户端信息带在 User-Agent 里（含 {@code wxwork/版本}），
 * 手机端还带 {@code iPhone/Android/HarmonyOS} 等平台标识，桌面端带
 * {@code Windows NT/Macintosh/Linux} 等平台标识，因此可以据此选择
 * “手机端 OA 链接”还是“PC 端 OA 链接”。</p>
 *
 * <p>判定顺序：先匹配手机端标识（Android 的 UA 里也含 Linux，必须先判手机），
 * 再匹配桌面端标识，都匹配不到时按 {@link #PC} 处理（宁可给 PC 链接，也不要给空白页）。</p>
 *
 * <p>入口地址上的 {@code device=pc|mobile} 参数优先级高于 UA 判定
 * （便于与企微后台“应用主页 → 分别配置手机与电脑端”配合使用）。</p>
 */
public final class OaonesDevice {

    /** 桌面端。 */
    public static final String PC = "pc";
    /** 手机端。 */
    public static final String MOBILE = "mobile";

    /** 手机端标识（注意 "Mobile/" 用于识别 iPadOS 13+ 的桌面模式 UA）。 */
    private static final String[] MOBILE_TOKENS = {
            "iphone", "ipod", "ipad", "android", "harmony", "windows phone",
            "mobile/", "blackberry", "symbian"
    };
    /** 桌面端标识。 */
    private static final String[] PC_TOKENS = {
            "windows nt", "windows 9", "macintosh", "x11", "linux", "cros"
    };
    /** 企业微信客户端标识。 */
    private static final String WECOM_TOKEN = "wxwork";
    /** 钉钉客户端标识（手机端/桌面端 UA 都带 dingtalk/aliApp(DingTalk)）。 */
    private static final String[] DINGTALK_TOKENS = {"dingtalk", "aliApp(DingTalk)"};
    /** 飞书客户端标识（手机端/桌面端 UA 都带 Lark；部分版本带 feishu）。 */
    private static final String[] FEISHU_TOKENS = {"lark", "feishu"};

    private OaonesDevice() {
    }

    /**
     * 识别客户端类型。
     *
     * @param device    入口地址上的显式参数（pc / mobile，其它值忽略）
     * @param userAgent 请求的 User-Agent
     * @return {@link #MOBILE} 或 {@link #PC}
     */
    public static String detect(String device, String userAgent) {
        String explicit = OaonesUtil.s(device).trim().toLowerCase();
        if (PC.equals(explicit) || MOBILE.equals(explicit)) {
            return explicit;
        }
        String ua = OaonesUtil.s(userAgent).toLowerCase();
        if (ua.length() == 0) {
            return PC;
        }
        for (String token : MOBILE_TOKENS) {
            if (ua.indexOf(token) >= 0) {
                return MOBILE;
            }
        }
        for (String token : PC_TOKENS) {
            if (ua.indexOf(token) >= 0) {
                return PC;
            }
        }
        return PC;
    }

    /** 是否为企业微信客户端（手机端或桌面端）。 */
    public static boolean isWeCom(String userAgent) {
        return OaonesUtil.s(userAgent).toLowerCase().indexOf(WECOM_TOKEN) >= 0;
    }

    /**
     * 是否为钉钉客户端（手机端或桌面端）。
     *
     * <p>钉钉工作台打开应用主页时 UA 里带 {@code dingtalk}（部分版本带 {@code aliApp(DingTalk)}），
     * 据此选择“钉钉手机端 OA 页面 / 钉钉 PC 端 OA 页面”与落地页口径。</p>
     */
    public static boolean isDingTalk(String userAgent) {
        String ua = OaonesUtil.s(userAgent).toLowerCase();
        for (String token : DINGTALK_TOKENS) {
            if (ua.indexOf(token.toLowerCase()) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为飞书客户端（手机端或桌面端，含海外版 Lark）。
     *
     * <p>飞书工作台打开网页应用时 UA 里带 {@code Lark/版本}（海外版标 {@code feishu}），
     * 据此选择“飞书手机端 OA 页面 / 飞书 PC 端 OA 页面”与落地页口径。</p>
     */
    public static boolean isFeishu(String userAgent) {
        String ua = OaonesUtil.s(userAgent).toLowerCase();
        for (String token : FEISHU_TOKENS) {
            if (ua.indexOf(token.toLowerCase()) >= 0) {
                return true;
            }
        }
        return false;
    }
}
