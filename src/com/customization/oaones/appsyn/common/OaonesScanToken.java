package com.customization.oaones.appsyn.common;

import com.alibaba.fastjson.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * 企业微信扫码绑定的 state 令牌（无状态 + 签名防伪造）。
 *
 * <p>“个人账号中心”的扫码绑定走企业微信网页授权：页面内嵌官方二维码，
 * 用户用本人企业微信扫码后，企业微信把浏览器重定向到回调页并带回
 * {@code code} + {@code state}。回调落地时不能依赖登录会话（例如在手机上扫码、
 * 或在其它设备上打开），因此把“发起人 + 目标租户 + 有效期”放进 state，
 * 并用该租户的 corp_secret 做 HMAC-SHA256 签名：
 * 回调侧先按 state 里的租户取回 secret 校验签名，签名不符或已过期一律拒绝，
 * 避免有人伪造 state 替别人建立绑定。</p>
 *
 * <p>明文格式：{@code oaUserId|cfgId|expireAtMillis|nonce}；
 * 令牌格式：{@code base64url(明文).base64url(HMAC-SHA256(明文明文base64, secret))}，
 * 整体长度控制在企业微信 state 上限（128 字节）以内。</p>
 */
public final class OaonesScanToken {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String CHARSET = "UTF-8";
    private static final String SEP = "|";
    /** 令牌最短有效期：即使配置得很短，也不低于 1 分钟，避免刚生成就过期。 */
    private static final long MIN_TTL_MILLIS = 60000L;

    private OaonesScanToken() {
    }

    /**
     * 生成 state 令牌。
     *
     * @param oaUserId  发起绑定的 OA 人员ID（登录人）
     * @param cfgId     目标企微租户配置ID
     * @param secret    该租户的 corp_secret（仅用于签名，不写入令牌）
     * @param ttlMillis 有效期毫秒数
     */
    public static String issue(String oaUserId, String cfgId, String secret, long ttlMillis) {
        long expireAt = System.currentTimeMillis() + Math.max(MIN_TTL_MILLIS, ttlMillis);
        String payload = OaonesUtil.s(oaUserId) + SEP + OaonesUtil.s(cfgId) + SEP + expireAt
                + SEP + nonce();
        String payloadText = encode(payload);
        return payloadText + "." + sign(payloadText, secret);
    }

    /**
     * 读取令牌里的明文部分（**不校验签名**，仅用于取出租户ID去查 secret）。
     * 格式非法时返回 null。
     */
    public static JSONObject read(String token) {
        String[] parts = split(token);
        if (parts == null) {
            return null;
        }
        String payload = decode(parts[0]);
        if (payload == null) {
            return null;
        }
        String[] fields = payload.split("\\" + SEP, -1);
        if (fields.length != 4) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.put("oa_user_id", fields[0]);
        json.put("cfg_id", fields[1]);
        // 毫秒时间戳（13 位）超出 int 范围：必须按 long 解析。
        // 注意不要用 OaonesUtil.i / weaver.general.Util.getIntValue —— 它内部是 Integer.parseInt，
        // 解析失败会返回默认值 0，导致“刚生成的 state 也判定为已过期”。
        json.put("expire_at", toMillis(fields[2]));
        json.put("nonce", fields[3]);
        return json;
    }

    /** 校验签名与有效期；任一不通过返回 false。 */
    public static boolean verify(String token, String secret) {
        String[] parts = split(token);
        if (parts == null || secret == null || secret.trim().length() == 0) {
            return false;
        }
        String expected = sign(parts[0], secret);
        if (!constantTimeEquals(expected, parts[1])) {
            return false;
        }
        JSONObject payload = read(token);
        return payload != null && payload.getLongValue("expire_at") > System.currentTimeMillis();
    }

    /** 签发时的过期时间（毫秒），未校验；供页面提示用。 */
    public static long expireAt(String token) {
        JSONObject payload = read(token);
        return payload == null ? 0L : payload.getLongValue("expire_at");
    }

    /** 解析毫秒时间戳；非法值返回 0（视为已过期）。 */
    private static long toMillis(String value) {
        try {
            return Long.parseLong(OaonesUtil.s(value).trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String[] split(String token) {
        String value = OaonesUtil.s(token).trim();
        int dot = value.indexOf('.');
        if (dot <= 0 || dot >= value.length() - 1 || value.indexOf('.', dot + 1) >= 0) {
            return null;
        }
        return new String[]{value.substring(0, dot), value.substring(dot + 1)};
    }

    private static String sign(String payloadText, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(OaonesUtil.s(secret).getBytes(CHARSET), ALGORITHM));
            return encodeBytes(mac.doFinal(payloadText.getBytes(CHARSET)));
        } catch (Exception e) {
            throw new RuntimeException("扫码状态签名失败: " + e.getMessage());
        }
    }

    private static String encode(String text) {
        try {
            return encodeBytes(text.getBytes(CHARSET));
        } catch (Exception e) {
            throw new RuntimeException("扫码状态编码失败: " + e.getMessage());
        }
    }

    private static String decode(String text) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(text);
            return new String(bytes, CHARSET);
        } catch (Exception e) {
            return null;
        }
    }

    private static String encodeBytes(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String nonce() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** 定长比较，避免按字符提前返回带来的时序差异。 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
