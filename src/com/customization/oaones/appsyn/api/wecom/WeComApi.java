package com.customization.oaones.appsyn.api.wecom;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信通讯录开放接口封装。
 *
 * <p>同一 corpId + secret 复用一个实例，内部缓存 access_token；
 * 所有方法在接口返回非 0 errcode 时抛出异常。</p>
 */
public class WeComApi {

    private final String corpId;
    private final String corpSecret;
    private final String apiBase;
    private String accessToken;
    private long tokenExpireTime;

    public WeComApi(String corpId, String corpSecret) {
        this.corpId = corpId;
        this.corpSecret = corpSecret;
        this.apiBase = OaonesProps.wxApiBase();
    }

    public String getAccessToken() throws Exception {
        OaonesLog.step("获取企业微信 access_token（corpId=" + corpId + "）");
        long now = System.currentTimeMillis();
        if (accessToken != null && now < tokenExpireTime) {
            return accessToken;
        }
        Map<String, String> params = new HashMap<String, String>();
        params.put("corpid", corpId);
        params.put("corpsecret", corpSecret);
        String url = apiBase + "/cgi-bin/gettoken?" + OaonesUtil.buildQuery(params);
        JSONObject resp = requestJson("GET", url, null);
        if (!resp.containsKey("access_token")) {
            throw new RuntimeException("获取企业微信 access_token 失败: " + resp.toJSONString());
        }
        this.accessToken = resp.getString("access_token");
        int expiresIn = resp.getIntValue("expires_in");
        // 提前 5 分钟过期，避免边界失效
        this.tokenExpireTime = now + (expiresIn - 300) * 1000L;
        return this.accessToken;
    }

    /** 拉取全量部门。 */
    public JSONArray listDepartments() throws Exception {
        OaonesLog.step("调用企业微信获取部门列表");
        JSONObject resp = get("/cgi-bin/department/list", null);
        ensureOk(resp, "获取企业微信部门列表失败");
        JSONArray arr = resp.getJSONArray("department");
        return arr == null ? new JSONArray() : arr;
    }

    /** 拉取部门下成员（详细字段）。 */
    public JSONArray listUsers(long departmentId, boolean fetchChild) throws Exception {
        OaonesLog.step("调用企业微信获取成员列表 departmentId=" + departmentId
                + " fetchChild=" + fetchChild);
        Map<String, String> params = new HashMap<String, String>();
        params.put("department_id", String.valueOf(departmentId));
        params.put("fetch_child", fetchChild ? "1" : "0");
        JSONObject resp = get("/cgi-bin/user/list", params);
        ensureOk(resp, "获取企业微信成员列表失败");
        JSONArray arr = resp.getJSONArray("userlist");
        return arr == null ? new JSONArray() : arr;
    }

    /** 查询单个成员；不存在返回 null。 */
    public JSONObject getUser(String userId) throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("userid", userId);
        JSONObject resp = get("/cgi-bin/user/get", params);
        if (resp.getIntValue("errcode") == 60111) {
            return null;
        }
        ensureOk(resp, "查询企业微信成员失败");
        return resp;
    }

    /**
     * 用扫码授权换回的 code 换取扫码人身份。
     *
     * <p>返回体中，本企业成员带 {@code userid}（后续按该ID建立绑定）；
     * 非本企业成员只带 {@code openid}，调用方需据此提示“不是该企业成员”。</p>
     */
    public JSONObject getAuthUserInfo(String code) throws Exception {
        OaonesLog.step("调用企业微信扫码授权换取身份 corpId=" + corpId);
        Map<String, String> params = new HashMap<String, String>();
        params.put("code", OaonesUtil.s(code));
        JSONObject resp = get("/cgi-bin/auth/getuserinfo", params);
        ensureOk(resp, "企业微信扫码授权换取成员身份失败");
        return resp;
    }

    public boolean userExists(String userId) throws Exception {
        return getUser(userId) != null;
    }

    public long createDepartment(String name, long parentId, Integer order) throws Exception {
        OaonesLog.step("调用企业微信创建部门 name=" + name + " parentId=" + parentId);
        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("parentid", parentId);
        if (order != null) {
            body.put("order", order);
        }
        JSONObject resp = post("/cgi-bin/department/create", body);
        ensureOk(resp, "创建企业微信部门失败");
        return resp.getLongValue("id");
    }

    public void updateDepartment(long id, String name, Long parentId, Integer order) throws Exception {
        OaonesLog.step("调用企业微信更新部门 id=" + id);
        JSONObject body = new JSONObject();
        body.put("id", id);
        if (name != null && name.length() > 0) {
            body.put("name", name);
        }
        if (parentId != null) {
            body.put("parentid", parentId);
        }
        if (order != null) {
            body.put("order", order);
        }
        JSONObject resp = post("/cgi-bin/department/update", body);
        ensureOk(resp, "更新企业微信部门失败");
    }

    public void deleteDepartment(long id) throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("id", String.valueOf(id));
        JSONObject resp = get("/cgi-bin/department/delete", params);
        ensureOk(resp, "删除企业微信部门失败");
    }

    public void createUser(JSONObject user) throws Exception {
        OaonesLog.step("调用企业微信创建成员 userid="
                + OaonesUtil.s(user == null ? "" : user.getString("userid")));
        JSONObject resp = post("/cgi-bin/user/create", user);
        ensureOk(resp, "创建企业微信成员失败");
    }

    public void updateUser(JSONObject user) throws Exception {
        OaonesLog.step("调用企业微信更新成员 userid="
                + OaonesUtil.s(user == null ? "" : user.getString("userid")));
        JSONObject resp = post("/cgi-bin/user/update", user);
        ensureOk(resp, "更新企业微信成员失败");
    }

    public void deleteUser(String userId) throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("userid", userId);
        JSONObject resp = get("/cgi-bin/user/delete", params);
        ensureOk(resp, "删除企业微信成员失败");
    }

    private JSONObject get(String path, Map<String, String> query) throws Exception {
        StringBuilder url = new StringBuilder(apiBase).append(path);
        Map<String, String> params = new HashMap<String, String>();
        params.put("access_token", getAccessToken());
        if (query != null) {
            params.putAll(query);
        }
        String qs = OaonesUtil.buildQuery(params);
        if (qs.length() > 0) {
            url.append('?').append(qs);
        }
        return requestJson("GET", url.toString(), null);
    }

    private JSONObject post(String path, JSONObject body) throws Exception {
        return requestJson("POST", apiBase + path + "?access_token=" + getAccessToken(), body);
    }

    private void ensureOk(JSONObject resp, String tip) {
        if (resp == null) {
            throw new RuntimeException(tip + ": 无响应");
        }
        if (resp.getIntValue("errcode") != 0) {
            throw new RuntimeException(tip + ": errcode=" + resp.getIntValue("errcode")
                    + ", errmsg=" + resp.getString("errmsg"));
        }
    }

    private JSONObject requestJson(String method, String url, JSONObject body) throws Exception {
        HttpURLConnection conn = null;
        long start = System.currentTimeMillis();
        String reqText = body == null ? "" : body.toJSONString();
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(OaonesProps.connectTimeout());
            conn.setReadTimeout(OaonesProps.connectTimeout());
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
            return JSON.parseObject(text);
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
}
