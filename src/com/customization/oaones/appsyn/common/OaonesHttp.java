package com.customization.oaones.appsyn.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * 轻量 JSON HTTP 客户端（会议 / 日程同步推送第三方平台用）。
 *
 * <p>本模块各连接器原有的 api 类各自实现了一份 {@code requestJson}（因为要各自处理平台的
 * 返回码与 token），本类只服务于「会议 / 日程同步」这条新链路：三个平台的日程推送都用它发
 * {@code POST}/<code>GET</code>，避免再复制三份几乎一样的连接代码；平台返回码的解读仍在
 * 各适配器里（{@code api.{wecom,dingtalk,feishu}.*ScheduleApi}）。</p>
 */
public final class OaonesHttp {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 20000;

    private OaonesHttp() {
    }

    /** 发一个 JSON POST，返回解析后的 JSON（响应不是 JSON 时包装成 {@code {"raw": ...}}）。 */
    public static JSONObject postJson(String url, JSONObject body) throws Exception {
        return postJson(url, body, null);
    }

    public static JSONObject postJson(String url, JSONObject body, Map<String, String> headers)
            throws Exception {
        byte[] data = body == null ? new byte[0] : body.toJSONString().getBytes("UTF-8");
        return request("POST", url, data, headers);
    }

    /** 发一个 GET，返回解析后的 JSON。 */
    public static JSONObject getJson(String url) throws Exception {
        return request("GET", url, null, null);
    }

    /** 发一个带自定义请求头的 GET，返回解析后的 JSON（飞书日历事件列表用）。 */
    public static JSONObject getJson(String url, Map<String, String> headers) throws Exception {
        return request("GET", url, null, headers);
    }

    /** 发一个带自定义请求头的 JSON PATCH，返回解析后的 JSON（飞书事件更新用）。 */
    public static JSONObject patchJson(String url, JSONObject body, Map<String, String> headers)
            throws Exception {
        return request("PATCH", url, body == null ? null : body.toJSONString().getBytes("UTF-8"), headers);
    }

    /** 发一个带自定义请求头的 DELETE（不带请求体），返回解析后的 JSON（飞书事件删除用）。 */
    public static JSONObject deleteJson(String url, Map<String, String> headers) throws Exception {
        return request("DELETE", url, null, headers);
    }

    private static JSONObject request(String method, String url, byte[] body, Map<String, String> headers)
            throws Exception {
        HttpURLConnection conn = null;
        long start = System.currentTimeMillis();
        String reqText = body == null ? "" : new String(body, "UTF-8");
        int platform = OaonesApiLog.httpContextPlatform();
        String corpId = OaonesApiLog.httpContextCorpId();
        try {
            OaonesLog.step("OAones HTTP " + method + " " + url);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoInput(true);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            }
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (body != null) {
                OutputStream out = conn.getOutputStream();
                try {
                    out.write(body);
                    out.flush();
                } finally {
                    out.close();
                }
            }
            int status = conn.getResponseCode();
            String text = readString(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (platform >= 0) {
                OaonesApiLog.out(platform, corpId, method, url, reqText, text, status,
                        System.currentTimeMillis() - start, null);
            }
            if (text == null || text.trim().length() == 0) {
                JSONObject empty = new JSONObject();
                empty.put("http_status", status);
                return empty;
            }
            JSONObject json;
            try {
                json = JSON.parseObject(text);
            } catch (Exception parseError) {
                json = new JSONObject();
                json.put("raw", text);
            }
            json.put("http_status", status);
            return json;
        } catch (Exception e) {
            if (platform >= 0) {
                OaonesApiLog.out(platform, corpId, method, url, reqText, "", 0,
                        System.currentTimeMillis() - start, OaonesApiLog.stackTrace(e));
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readString(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }
}
