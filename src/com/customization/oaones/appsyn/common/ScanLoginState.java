package com.customization.oaones.appsyn.common;

import com.alibaba.fastjson.JSONObject;

/**
 * 扫码登录授权上下文（平台回跳 {@code state}）的编解码：{@code base64url(票据|平台|租户ID)}。
 *
 * <p>统一登录页的二维码是**一张**（见 {@code /oaones/login/login.jsp}），
 * 扫码方可能是企业微信 / 钉钉 / 飞书任意一个客户端，因此扫码落地页
 * （{@code /oaones/login/scan.jsp}）先按客户端分派到对应连接器，
 * 再由该连接器把“票据 / 平台 / 第几家企业”放进授权 {@code state} 里回传——
 * 三个连接器共用这一个协议，编解码只保留本类一份实现。</p>
 *
 * <p>解析不出时返回空 JSON（调用方用页面参数兜底），不抛异常：
 * {@code state} 被代理改写/截断属于可预期情况，报错会盖住更准确的原因。</p>
 */
public final class ScanLoginState {

    private ScanLoginState() {
    }

    /** 编码授权上下文（放在平台 {@code state} 里回传）。 */
    public static String encode(String ticket, String platform, String cfgId) {
        String plain = OaonesUtil.s(ticket) + "|" + OaonesUtil.s(platform) + "|" + OaonesUtil.s(cfgId);
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 解析授权上下文；解析不出时返回只有空值的对象（ticket / platform / cfg_id 都是空串）。 */
    public static JSONObject parse(String state) {
        JSONObject json = new JSONObject();
        json.put("ticket", "");
        json.put("platform", "");
        json.put("cfg_id", "");
        String value = OaonesUtil.s(state).trim();
        if (value.length() == 0) {
            return json;
        }
        try {
            String plain = new String(java.util.Base64.getUrlDecoder().decode(value),
                    java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = (plain + "||").split("\\|", -1);
            json.put("ticket", OaonesUtil.s(parts[0]));
            json.put("platform", OaonesUtil.s(parts.length > 1 ? parts[1] : ""));
            json.put("cfg_id", OaonesUtil.s(parts.length > 2 ? parts[2] : ""));
        } catch (Exception e) {
            OaonesLog.step("解析扫码登录 state 失败（按页面参数继续）: " + value);
        }
        return json;
    }
}
