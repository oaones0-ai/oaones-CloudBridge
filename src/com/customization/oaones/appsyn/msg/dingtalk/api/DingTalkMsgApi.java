package com.customization.oaones.appsyn.msg.dingtalk.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgConst;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgProps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钉钉「工作通知 / 待办」开放接口封装（自研推送专用）。
 *
 * <ul>
 *   <li>access_token 按 AppKey + AppSecret 缓存在内存，有效期取 expires_in 并提前
 *       {@code msg.token.advance.sec} 秒失效，避免边界失效；</li>
 *   <li>token 失效（40001/40014/41001/42001）时强制刷新并自动重试一次，无需人工干预；</li>
 *   <li>工作通知走 {@code /topapi/message/corpconversation/asyncsend_v2}，
 *       待办（task_card）走 {@code /topapi/workrecord/add|update}；</li>
 *   <li>**接口网关取 {@code oaones.properties#dd.api.base}（默认 https://oapi.dingtalk.com）**：
 *       上述 /topapi 与 /gettoken 都属于钉钉服务端网关，不能落到企微网关
 *       （落到 qyapi.weixin.qq.com 会一律返回 404/400，消息永远发不出去）；</li>
 *   <li>所有调用记录请求/响应报文，由上层写入推送日志。</li>
 * </ul>
 */
public class DingTalkMsgApi {

    /** token 缓存：key = appKey|appSecret。 */
    private static final Map<String, TokenHolder> TOKEN_CACHE = new ConcurrentHashMap<String, TokenHolder>();

    private final String corpId;
    private final String secret;
    private final int timeoutMs;

    public DingTalkMsgApi(String corpId, String secret, int timeoutMs) {
        this.corpId = corpId == null ? "" : corpId.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DingTalkMsgProps.timeout();
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
            JSONObject resp = get("/gettoken?appkey=" + urlEncode(corpId)
                    + "&appsecret=" + urlEncode(secret));
            String token = resp.getString("access_token");
            if (token == null || token.length() == 0) {
                throw new RuntimeException("获取钉钉 access_token 失败: " + resp.toJSONString());
            }
            int expiresIn = resp.getIntValue("expires_in");
            if (expiresIn <= 0) {
                expiresIn = 7200;
            }
            int advance = DingTalkMsgProps.tokenAdvanceSec();
            long ttl = (expiresIn - advance) * 1000L;
            if (ttl < 60 * 1000L) {
                ttl = 60 * 1000L;
            }
            TokenHolder next = new TokenHolder();
            next.token = token;
            next.expireAt = now + ttl;
            next.refreshTime = now;
            TOKEN_CACHE.put(key, next);
            OaonesLog.step("[msg] 刷新钉钉 access_token appKey=" + corpId
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

    /**
     * 发送工作通知（{@code /topapi/message/corpconversation/asyncsend_v2}）。
     *
     * <p>入参是渲染层统一口径的消息体（{@code touser / agentid / msgtype / 各形态正文段}），
     * 这里翻译成钉钉入参：{@code userid_list / agent_id / msg{msgtype,...}}；
     * {@code task_card}（待办）走钉钉待办接口 {@code /topapi/workrecord/add}，
     * 返回体里会带 {@code result.record_id} 供后续更新状态。</p>
     */
    public JSONObject send(JSONObject body) throws Exception {
        if ("task_card".equals(txt(body.getString("msgtype")))) {
            return addWorkRecord(body);
        }
        JSONObject payload = new JSONObject();
        payload.put("agent_id", agentIdOf(body.get("agentid")));
        payload.put("userid_list", txt(body.getString("touser")));
        payload.put("to_all_user", false);
        JSONObject msg = new JSONObject();
        String msgtype = txt(body.getString("msgtype"));
        msg.put("msgtype", msgtype);
        JSONObject section = body.getJSONObject(msgtype);
        if (section != null) {
            msg.put(msgtype, section);
        }
        payload.put("msg", msg);
        return call("/topapi/message/corpconversation/asyncsend_v2", payload);
    }

    /**
     * 新增钉钉待办（{@code /topapi/workrecord/add}）。
     *
     * <p>钉钉待办会进入员工的“待办”列表；返回体 {@code result.record_id} 就是后续更新用的
     * 待办ID（由推送层回写到 {@code uf_oaones_msg_task.task_id}）。</p>
     */
    public JSONObject addWorkRecord(JSONObject body) throws Exception {
        JSONObject card = body.getJSONObject("task_card");
        String userid = firstUserid(body.getString("touser"));
        JSONObject payload = new JSONObject();
        payload.put("userid", userid);
        payload.put("title", card == null ? "" : txt(card.getString("title")));
        payload.put("url", card == null ? "" : txt(card.getString("url")));
        payload.put("pcUrl", card == null ? "" : txt(card.getString("pc_url")));
        payload.put("mobileUrl", card == null ? "" : txt(card.getString("mobile_url")));
        payload.put("source_name", card == null ? "OA" : txt(card.getString("source_name")));
        payload.put("create_time", System.currentTimeMillis());
        String description = card == null ? "" : txt(card.getString("description"));
        JSONObject resp = call("/topapi/workrecord/add", payload);
        // 把待办描述作为附加信息记进返回体，便于推送日志排查（钉钉 add 接口本身不接收 description）
        if (resp != null && description.length() > 0) {
            resp.put("workrecord_desc", description);
        }
        return resp;
    }

    /**
     * 更新待办状态（{@code /topapi/workrecord/update}）。
     *
     * <p>入参沿用渲染层的卡片更新口径：{@code userids} / {@code task_id}（这里即 record_id）/
     * {@code replace_name}（“已完成 / 已拒绝 / 已失效”文案，仅用于日志），
     * 状态码取 {@link DingTalkMsgConst#WORKRECORD_STATUS_DONE} 等。</p>
     */
    public JSONObject updateTaskCard(JSONObject body) throws Exception {
        String recordId = txt(body.getString("task_id"));
        if (recordId.length() == 0) {
            recordId = txt(body.getString("record_id"));
        }
        int status = body.getIntValue("status");
        if (status <= 0) {
            status = DingTalkMsgConst.WORKRECORD_STATUS_DONE;
        }
        JSONObject payload = new JSONObject();
        payload.put("userid", firstUserid(body.getString("touser") == null
                ? body.getString("userids") : body.getString("touser")));
        payload.put("record_id", recordId);
        payload.put("status", status);
        return call("/topapi/workrecord/update", payload);
    }

    /** 取第一个接收人（钉钉待办接口按单人创建）。 */
    private String firstUserid(String value) {
        String text = txt(value);
        int comma = text.indexOf(',');
        return comma > 0 ? text.substring(0, comma).trim() : text;
    }

    /** agentid 归一：钉钉要求整型，字符串形式可能被拒；转不了就原样下发。 */
    private Object agentIdOf(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        try {
            return Long.valueOf(text);
        } catch (Exception e) {
            return text;
        }
    }

    private String txt(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 代调用：自动带 token；token 失效自动刷新并重试一次。
     */
    public JSONObject call(String path, JSONObject body) throws Exception {
        JSONObject resp = post(path, body, accessToken(false));
        int errcode = resp.getIntValue("errcode");
        if (DingTalkMsgConst.isTokenError(errcode)) {
            OaonesLog.step("[msg] access_token 失效 errcode=" + errcode + "，刷新后重试 path=" + path);
            resp = post(path, body, accessToken(true));
        }
        return resp;
    }

    /**
     * 带 access_token 的 POST（/topapi 老接口：token 放在查询串里）。
     *
     * <p>网关固定取 {@link OaonesProps#ddApiBase()}（oaones.properties#dd.api.base），
     * 与 {@code api.dingtalk.DingTalkApi} 的 topapi 调用保持同一个网关；
     * 换环境（如私有化代理）只改配置，不改代码。</p>
     */
    private JSONObject post(String path, JSONObject body, String token) throws Exception {
        String url = OaonesProps.ddApiBase() + path + "?access_token=" + urlEncode(token);
        return requestJson("POST", url, body);
    }

    /** 不带 token 的 GET（当前用于 {@code /gettoken}，同样走钉钉网关）。 */
    private JSONObject get(String pathAndQuery) throws Exception {
        return requestJson("GET", OaonesProps.ddApiBase() + pathAndQuery, null);
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
            OaonesApiLog.out(OaonesConst.TYPE_DINGTALK, corpId, method, url, reqText, text, code,
                    System.currentTimeMillis() - start, null);
            JSONObject resp = JSON.parseObject(text);
            return resp == null ? new JSONObject() : resp;
        } catch (Exception e) {
            OaonesApiLog.out(OaonesConst.TYPE_DINGTALK, corpId, method, url, reqText, "", 0,
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
