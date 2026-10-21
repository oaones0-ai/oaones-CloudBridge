package com.customization.oaones.appsyn.api.personalwx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 个人微信开放接口封装（扫码授权换 openid / unionid / 昵称头像）。
 *
 * <p>与企微连接器（{@code WeComApi}）的关键差别：<b>个人微信没有“企业”概念</b>，
 * 没有 corpId + secret 的多租户体系、也没有 access_token 缓存——
 * 每次扫码授权换回的 code 只能换一次，换回来的 {@code access_token} 只用于
 * 紧接着读一次用户信息，因此本类不做 token 缓存（也避免把 A 应用令牌用到 B 应用上）。</p>
 *
 * <p>接口域名取自 {@code oaones.properties#pw.api.base}（默认微信开放平台
 * {@code https://api.weixin.qq.com}），换网关不用改代码。</p>
 */
public class PersonalWxApi {

    private final String appId;
    private final String appSecret;
    private final String apiBase;

    public PersonalWxApi(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.apiBase = OaonesProps.pwApiBase();
    }

    /**
     * 用扫码授权换回的 code 换取访问令牌。
     *
     * <p>返回体里的 {@code openid} 是扫码人（个人微信）在该 AppID 下的稳定标识，
     * {@code unionid} 只有该 AppID 已绑定到微信开放平台帐号时才有值。</p>
     */
    public JSONObject exchangeCode(String code) throws Exception {
        OaonesLog.step("调用个人微信授权换取访问令牌 appId=" + appId);
        Map<String, String> params = new HashMap<String, String>();
        params.put("appid", OaonesUtil.s(appId));
        params.put("secret", OaonesUtil.s(appSecret));
        params.put("code", OaonesUtil.s(code));
        params.put("grant_type", "authorization_code");
        JSONObject resp = requestJson(apiBase + "/sns/oauth2/access_token?"
                + OaonesUtil.buildQuery(params));
        ensureOk(resp, "个人微信授权换取访问令牌失败");
        if (OaonesUtil.s(resp.getString("openid")).trim().length() == 0) {
            throw new RuntimeException("个人微信未返回 openid，无法完成绑定（请确认 AppID/AppSecret 与授权回调域配置正确）");
        }
        return resp;
    }

    /**
     * 读取微信用户信息（昵称 / 头像 / unionid）。
     *
     * <p>必须在 {@link #exchangeCode(String)} 之后立刻调用：{@code access_token} 由该次授权产生，
     * 有效期内只用于读这一次用户信息。</p>
     */
    public JSONObject getUserInfo(String accessToken, String openid) throws Exception {
        OaonesLog.step("调用个人微信读取用户信息 openid=" + openid);
        Map<String, String> params = new HashMap<String, String>();
        params.put("access_token", OaonesUtil.s(accessToken));
        params.put("openid", OaonesUtil.s(openid));
        params.put("lang", "zh_CN");
        JSONObject resp = requestJson(apiBase + "/sns/userinfo?" + OaonesUtil.buildQuery(params));
        ensureOk(resp, "个人微信读取用户信息失败");
        return resp;
    }

    private void ensureOk(JSONObject resp, String tip) {
        if (resp == null) {
            throw new RuntimeException(tip + ": 无响应");
        }
        if (resp.containsKey("errcode") && resp.getIntValue("errcode") != 0) {
            throw new RuntimeException(tip + ": errcode=" + resp.getIntValue("errcode")
                    + ", errmsg=" + OaonesUtil.s(resp.getString("errmsg")));
        }
    }

    private JSONObject requestJson(String url) throws Exception {
        HttpURLConnection conn = null;
        long start = System.currentTimeMillis();
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(OaonesProps.connectTimeout());
            conn.setReadTimeout(OaonesProps.connectTimeout());
            conn.setDoInput(true);
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = readAll(in);
            OaonesApiLog.out(OaonesApiLog.PLATFORM_PERSONAL_WX, "", "GET", url, "", text, code,
                    System.currentTimeMillis() - start, null);
            return JSON.parseObject(text);
        } catch (Exception e) {
            OaonesApiLog.out(OaonesApiLog.PLATFORM_PERSONAL_WX, "", "GET", url, "", "", 0,
                    System.currentTimeMillis() - start, OaonesApiLog.stackTrace(e));
            throw e;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "{}";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
