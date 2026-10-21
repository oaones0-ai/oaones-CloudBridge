package com.customization.oaones.appsyn.msg.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.msg.common.MsgConst;
import com.customization.oaones.appsyn.msg.common.MsgProps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业微信「应用消息」开放接口封装（自研推送专用）。
 *
 * <ul>
 *   <li>access_token 按 corpId + secret 缓存在内存，有效期取 expires_in 并提前
 *       {@code msg.token.advance.sec} 秒失效，避免边界失效；</li>
 *   <li>token 失效（40001/40014/42001）时强制刷新并自动重试一次，无需人工干预；</li>
 *   <li>所有调用记录请求/响应报文，由上层写入推送日志。</li>
 * </ul>
 */
public class WeComMsgApi {

    /** token 缓存：key = corpId|secret。 */
    private static final Map<String, TokenHolder> TOKEN_CACHE = new ConcurrentHashMap<String, TokenHolder>();

    private final String corpId;
    private final String secret;
    private final int timeoutMs;

    public WeComMsgApi(String corpId, String secret, int timeoutMs) {
        this.corpId = corpId == null ? "" : corpId.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : MsgProps.timeout();
    }

    public String getCorpId() {
        return corpId;
    }

    /** 获取 access_token（命中缓存直接返回）。 */
    public String accessToken() throws Exception {
        return accessToken(false);
    }

    /**
     * 获取 access_token。
     *
     * @param force 是否强制刷新（token 失效重试用）
     */
    public String accessToken(boolean force) throws Exception {
        String key = corpId + "|" + secret;
        long now = System.currentTimeMillis();
        TokenHolder holder = TOKEN_CACHE.get(key);
        if (!force && holder != null && now < holder.expireAt) {
            return holder.token;
        }
        synchronized (TOKEN_CACHE) {
            holder = TOKEN_CACHE.get(key);
            if (!force && holder != null && now < holder.expireAt) {
                return holder.token;
            }
            JSONObject resp = get("/cgi-bin/gettoken?corpid=" + urlEncode(corpId)
                    + "&corpsecret=" + urlEncode(secret));
            String token = resp.getString("access_token");
            if (token == null || token.length() == 0) {
                throw new RuntimeException("获取企业微信 access_token 失败: " + resp.toJSONString());
            }
            int expiresIn = resp.getIntValue("expires_in");
            if (expiresIn <= 0) {
                expiresIn = 7200;
            }
            int advance = MsgProps.tokenAdvanceSec();
            long ttl = (expiresIn - advance) * 1000L;
            if (ttl < 60 * 1000L) {
                ttl = 60 * 1000L;
            }
            TokenHolder next = new TokenHolder();
            next.token = token;
            next.expireAt = now + ttl;
            next.refreshTime = now;
            TOKEN_CACHE.put(key, next);
            OaonesLog.step("[msg] 刷新企业微信 access_token corpId=" + corpId
                    + " expiresIn=" + expiresIn + " 提前刷新秒数=" + advance);
            return token;
        }
    }

    /** token 缓存信息（页面“连通测试”展示用）。 */
    public JSONObject tokenInfo() {
        String key = corpId + "|" + secret;
        TokenHolder holder = TOKEN_CACHE.get(key);
        JSONObject obj = new JSONObject();
        obj.put("cached", holder != null);
        if (holder != null) {
            obj.put("expireInSec", Math.max(0, (holder.expireAt - System.currentTimeMillis()) / 1000));
            obj.put("refreshTime", holder.refreshTime);
        }
        return obj;
    }

    /** 发送应用消息（/cgi-bin/message/send）。 */
    public JSONObject send(JSONObject body) throws Exception {
        return call("/cgi-bin/message/send", body);
    }

    /** 更新任务卡片（/cgi-bin/message/update_taskcard）。 */
    public JSONObject updateTaskCard(JSONObject body) throws Exception {
        return call("/cgi-bin/message/update_taskcard", body);
    }

    /**
     * 代调用：自动带 token；token 失效自动刷新并重试一次。
     */
    public JSONObject call(String path, JSONObject body) throws Exception {
        JSONObject resp = post(path, body, accessToken(false));
        int errcode = resp.getIntValue("errcode");
        if (MsgConst.isTokenError(errcode)) {
            OaonesLog.step("[msg] access_token 失效 errcode=" + errcode + "，刷新后重试 path=" + path);
            resp = post(path, body, accessToken(true));
        }
        return resp;
    }

    private JSONObject post(String path, JSONObject body, String token) throws Exception {
        String url = OaonesProps.wxApiBase() + path + "?access_token=" + urlEncode(token);
        return requestJson("POST", url, body);
    }

    private JSONObject get(String pathAndQuery) throws Exception {
        return requestJson("GET", OaonesProps.wxApiBase() + pathAndQuery, null);
    }

    private JSONObject requestJson(String method, String url, JSONObject body) throws Exception {
        HttpURLConnection conn = null;
        long start = System.currentTimeMillis();
        String reqText = body == null ? "" : body.toJSONString();
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setDoInput(true);
            if (body != null) {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(body.toJSONString().getBytes("UTF-8"));
                    os.flush();
                } finally {
                    os.close();
                }
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = readAll(in);
            OaonesApiLog.out(OaonesConst.TYPE_WECHAT, corpId, method, url, reqText, text, code,
                    System.currentTimeMillis() - start, null);
            JSONObject resp = JSON.parseObject(text);
            return resp == null ? new JSONObject() : resp;
        } catch (Exception e) {
            OaonesApiLog.out(OaonesConst.TYPE_WECHAT, corpId, method, url, reqText, "", 0,
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

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    /** 清除指定应用/企业的 token 缓存（修改 Secret 后调用，保证配置即时生效）。 */
    public static void clearToken(String corpId, String secret) {
        if (corpId == null || secret == null) {
            return;
        }
        TOKEN_CACHE.remove(corpId.trim() + "|" + secret.trim());
    }

    /** 清空全部 token 缓存（推送配置保存后调用）。 */
    public static void clearAll() {
        TOKEN_CACHE.clear();
    }

    private static class TokenHolder {
        private String token;
        private long expireAt;
        private long refreshTime;
    }
}
