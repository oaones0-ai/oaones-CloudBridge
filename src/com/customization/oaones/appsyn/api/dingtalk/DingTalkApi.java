package com.customization.oaones.appsyn.api.dingtalk;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 钉钉开放接口封装（通讯录 + 扫码/免登换取身份）。
 *
 * <p>一套 appKey + appSecret（企业内部应用）复用一个实例，内部缓存 access_token。
 * 与 {@code api.wecom.WeComApi} 的分工完全对称：**对上层暴露同一组方法与同一套字段名**
 * （成员 {@code userid/name/mobile/email/position/department/main_department}，
 * 部门 {@code id/name/parentid/order}），
 * 把钉钉的字段名（{@code dept_id_list/job_number/title/parent_id}）在 {@link #normalizeUser(JSONObject)}
 * 与 {@link #normalizeDepartment(JSONObject)} 里归一化，这样绑定/同步/选择框等业务代码
 * 不必为了一个连接器再写一套字段口径。</p>
 *
 * <p>接口来源（钉钉开放平台）：</p>
 * <ul>
 *   <li>企业内部应用 access_token：{@code GET /gettoken?appkey=&appsecret=}；</li>
 *   <li>部门：{@code /topapi/v2/department/get}、{@code /topapi/v2/department/listsub}、
 *       {@code /topapi/v2/department/create|update|delete}；</li>
 *   <li>成员：{@code /topapi/v2/user/get|list|create|update|delete}；</li>
 *   <li>扫码登录 / 网页授权：{@code login.dingtalk.com/oauth2/auth} 拿 code →
 *       {@code /v1.0/oauth2/userAccessToken} 换用户令牌 →
 *       {@code /v1.0/contact/users/me} 拿 unionId → {@code /topapi/user/getbyunionid} 换 userid；</li>
 *   <li>工作台免登：微应用主页地址上钉钉追加的 code → {@code /topapi/v2/user/getuserinfo}。</li>
 * </ul>
 */
public class DingTalkApi {

    /** 钉钉根部门ID（企业内部应用的通讯录根节点固定为 1）。 */
    public static final long ROOT_DEPT_ID = 1L;
    /** 递归遍历的最大层级与分页保护（防止接口异常时无限递归）。 */
    private static final int MAX_DEPTH = 20;
    private static final int MAX_PAGES = 200;
    /** 分页拉取成员的每页条数（钉钉上限 100）。 */
    private static final int PAGE_SIZE = 100;

    private final String appKey;
    private final String appSecret;
    private final String apiBase;
    private final String openApiBase;
    private String accessToken;
    private long tokenExpireTime;

    public DingTalkApi(String appKey, String appSecret) {
        this.appKey = OaonesUtil.s(appKey).trim();
        this.appSecret = OaonesUtil.s(appSecret).trim();
        this.apiBase = OaonesProps.ddApiBase();
        this.openApiBase = OaonesProps.ddOpenApiBase();
    }

    public String getAppKey() {
        return appKey;
    }

    /** 企业内部应用 access_token（缓存，剩余不足 5 分钟时自动刷新）。 */
    public String getAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (accessToken != null && accessToken.length() > 0 && now < tokenExpireTime) {
            return accessToken;
        }
        OaonesLog.step("获取钉钉 access_token（appKey=" + appKey + "）");
        Map<String, String> params = new HashMap<String, String>();
        params.put("appkey", appKey);
        params.put("appsecret", appSecret);
        JSONObject resp = requestJson("GET", apiBase + "/gettoken?" + OaonesUtil.buildQuery(params), null, null);
        ensureOk(resp, "获取钉钉 access_token 失败");
        String token = OaonesUtil.s(resp.getString("access_token"));
        if (token.length() == 0) {
            throw new RuntimeException("获取钉钉 access_token 失败: " + resp.toJSONString());
        }
        int expiresIn = resp.getIntValue("expires_in");
        if (expiresIn <= 0) {
            expiresIn = 7200;
        }
        long ttl = (expiresIn - 300) * 1000L;
        if (ttl < 60 * 1000L) {
            ttl = 60 * 1000L;
        }
        this.accessToken = token;
        this.tokenExpireTime = now + ttl;
        return token;
    }

    /* ------------------------------------------------------------------
     * 通讯录：部门
     * ------------------------------------------------------------------ */

    /**
     * 拉取全量部门（含根部门），字段口径与钉钉一致：{@code id / name / parentid / order}。
     *
     * <p>钉钉没有“一次拉全部部门”的接口，这里按 {@code /topapi/v2/department/listsub}
     * 从根部门递归取子部门，与钉钉 {@code /cgi-bin/department/list} 的返回口径对齐。</p>
     */
    public JSONArray listDepartments() throws Exception {
        OaonesLog.step("调用钉钉获取部门列表（根部门 " + ROOT_DEPT_ID + " 起递归）");
        JSONArray all = new JSONArray();
        JSONObject root = getDepartment(ROOT_DEPT_ID);
        if (root != null) {
            all.add(normalizeDepartment(root));
        }
        collectDepartments(ROOT_DEPT_ID, all, 0);
        return all;
    }

    /** 某个部门下的直属子部门（字段口径与 {@link #listDepartments()} 一致）。 */
    public JSONArray listSubDepartments(long deptId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("dept_id", deptId);
        JSONObject resp = post("/topapi/v2/department/listsub", body);
        if (resp.getIntValue("errcode") != 0) {
            // 子部门不存在/无权限时按“没有子部门”处理，不打断整体同步
            OaonesLog.step("钉钉获取子部门为空 deptId=" + deptId + " errcode="
                    + resp.getIntValue("errcode") + " errmsg=" + OaonesUtil.s(resp.getString("errmsg")));
            return new JSONArray();
        }
        JSONArray result = resp.getJSONArray("result");
        JSONArray normalized = new JSONArray();
        if (result == null) {
            return normalized;
        }
        for (int i = 0; i < result.size(); i++) {
            JSONObject row = result.getJSONObject(i);
            if (row != null) {
                normalized.add(normalizeDepartment(row));
            }
        }
        return normalized;
    }

    /** 单个部门详情；不存在返回 null。 */
    public JSONObject getDepartment(long deptId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("dept_id", deptId);
        body.put("language", "zh_CN");
        JSONObject resp = post("/topapi/v2/department/get", body);
        if (resp.getIntValue("errcode") != 0) {
            return null;
        }
        return resp.getJSONObject("result");
    }

    /** 创建部门，返回钉钉部门ID。 */
    public long createDepartment(String name, long parentId, Integer order) throws Exception {
        OaonesLog.step("调用钉钉创建部门 name=" + name + " parentId=" + parentId);
        JSONObject body = new JSONObject();
        body.put("name", name);
        body.put("parent_id", parentId);
        body.put("order", order == null ? 0 : order.intValue());
        // 允许同一父部门下出现同名部门（与钉钉口径一致，否则同名OA部门会创建失败）
        body.put("create_dept_group", true);
        JSONObject resp = post("/topapi/v2/department/create", body);
        ensureOk(resp, "创建钉钉部门失败");
        JSONObject result = resp.getJSONObject("result");
        long deptId = result == null ? 0L : result.getLongValue("dept_id");
        if (deptId <= 0) {
            throw new RuntimeException("创建钉钉部门失败: 返回体没有 dept_id, resp=" + resp.toJSONString());
        }
        return deptId;
    }

    /** 更新部门（只更新传入的字段）。 */
    public void updateDepartment(long id, String name, Long parentId, Integer order) throws Exception {
        OaonesLog.step("调用钉钉更新部门 id=" + id);
        JSONObject body = new JSONObject();
        body.put("dept_id", id);
        if (name != null && name.length() > 0) {
            body.put("name", name);
        }
        if (parentId != null) {
            body.put("parent_id", parentId);
        }
        if (order != null) {
            body.put("order", order.intValue());
        }
        JSONObject resp = post("/topapi/v2/department/update", body);
        ensureOk(resp, "更新钉钉部门失败");
    }

    /** 删除部门（钉钉要求部门下没有成员和子部门）。 */
    public void deleteDepartment(long id) throws Exception {
        JSONObject body = new JSONObject();
        body.put("dept_id", id);
        JSONObject resp = post("/topapi/v2/department/delete", body);
        ensureOk(resp, "删除钉钉部门失败");
    }

    private void collectDepartments(long parentId, JSONArray all, int depth) throws Exception {
        if (depth >= MAX_DEPTH) {
            return;
        }
        JSONArray children = listSubDepartments(parentId);
        for (int i = 0; i < children.size(); i++) {
            JSONObject child = children.getJSONObject(i);
            all.add(child);
            long childId = child.getLongValue("id");
            if (childId > 0) {
                collectDepartments(childId, all, depth + 1);
            }
        }
    }

    /* ------------------------------------------------------------------
     * 通讯录：成员
     * ------------------------------------------------------------------ */

    /**
     * 拉取部门下成员（详细字段）。
     *
     * @param departmentId 起始部门（一般取租户配置里的根部门）
     * @param fetchChild   是否包含子部门成员（钉钉按部门分页取，子部门在这里递归展开）
     */
    public JSONArray listUsers(long departmentId, boolean fetchChild) throws Exception {
        OaonesLog.step("调用钉钉获取成员列表 departmentId=" + departmentId + " fetchChild=" + fetchChild);
        JSONArray result = new JSONArray();
        Set<Long> visited = new HashSet<Long>();
        collectUsers(departmentId, result, visited, fetchChild, 0);
        return result;
    }

    private void collectUsers(long deptId, JSONArray out, Set<Long> visited,
                              boolean fetchChild, int depth) throws Exception {
        if (depth >= MAX_DEPTH || !visited.add(deptId)) {
            return;
        }
        out.addAll(listUsersOfDept(deptId));
        if (!fetchChild) {
            return;
        }
        JSONArray children = listSubDepartments(deptId);
        for (int i = 0; i < children.size(); i++) {
            long childId = children.getJSONObject(i).getLongValue("id");
            if (childId > 0) {
                collectUsers(childId, out, visited, true, depth + 1);
            }
        }
    }

    /** 某个部门的直属成员（分页拉完，字段已归一化）。 */
    public JSONArray listUsersOfDept(long deptId) throws Exception {
        JSONArray users = new JSONArray();
        long cursor = 0L;
        for (int page = 0; page < MAX_PAGES; page++) {
            JSONObject body = new JSONObject();
            body.put("dept_id", deptId);
            body.put("cursor", cursor);
            body.put("size", PAGE_SIZE);
            body.put("order_field", "entry_asc");
            body.put("contain_access_limit", true);
            body.put("language", "zh_CN");
            JSONObject resp = post("/topapi/v2/user/list", body);
            if (resp.getIntValue("errcode") != 0) {
                OaonesLog.step("钉钉拉取部门成员失败 deptId=" + deptId
                        + " errcode=" + resp.getIntValue("errcode")
                        + " errmsg=" + OaonesUtil.s(resp.getString("errmsg")));
                break;
            }
            JSONObject result = resp.getJSONObject("result");
            if (result == null) {
                break;
            }
            JSONArray list = result.getJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    JSONObject row = list.getJSONObject(i);
                    if (row != null && OaonesUtil.s(row.getString("userid")).length() > 0) {
                        users.add(normalizeUser(row));
                    }
                }
            }
            if (!result.getBooleanValue("has_more")) {
                break;
            }
            long next = result.getLongValue("next_cursor");
            if (next <= cursor) {
                break;
            }
            cursor = next;
        }
        return users;
    }

    /** 查询单个成员；不存在返回 null。 */
    public JSONObject getUser(String userId) throws Exception {
        String id = OaonesUtil.s(userId).trim();
        if (id.length() == 0) {
            return null;
        }
        JSONObject body = new JSONObject();
        body.put("userid", id);
        body.put("language", "zh_CN");
        JSONObject resp = post("/topapi/v2/user/get", body);
        if (resp.getIntValue("errcode") != 0) {
            // 60003 / 60121 / 88xxx 等：成员不存在或已删除，按“查不到”返回
            OaonesLog.step("钉钉查询成员为空 userid=" + id + " errcode=" + resp.getIntValue("errcode")
                    + " errmsg=" + OaonesUtil.s(resp.getString("errmsg")));
            return null;
        }
        JSONObject result = resp.getJSONObject("result");
        if (result == null || OaonesUtil.s(result.getString("userid")).length() == 0) {
            return null;
        }
        return normalizeUser(result);
    }

    public boolean userExists(String userId) throws Exception {
        return getUser(userId) != null;
    }

    /** 创建成员（入参为归一化口径，内部转成钉钉字段）。 */
    public JSONObject createUser(JSONObject user) throws Exception {
        OaonesLog.step("调用钉钉创建成员 userid="
                + OaonesUtil.s(user == null ? "" : user.getString("userid")));
        JSONObject resp = post("/topapi/v2/user/create", userBody(user));
        ensureOk(resp, "创建钉钉成员失败");
        JSONObject result = resp.getJSONObject("result");
        return result == null ? new JSONObject() : normalizeUser(result);
    }

    /** 更新成员（入参为归一化口径，内部转成钉钉字段）。 */
    public void updateUser(JSONObject user) throws Exception {
        OaonesLog.step("调用钉钉更新成员 userid="
                + OaonesUtil.s(user == null ? "" : user.getString("userid")));
        JSONObject resp = post("/topapi/v2/user/update", userBody(user));
        ensureOk(resp, "更新钉钉成员失败");
    }

    /** 删除成员。 */
    public void deleteUser(String userId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("userid", OaonesUtil.s(userId));
        JSONObject resp = post("/topapi/v2/user/delete", body);
        ensureOk(resp, "删除钉钉成员失败");
    }

    /* ------------------------------------------------------------------
     * 扫码登录 / 工作台免登：code 换成员身份
     * ------------------------------------------------------------------ */

    /**
     * 用授权码换取扫码人身份，返回体与钉钉口径一致：本企业成员带 {@code userid}。
     *
     * <p>两种 code 都兼容，按顺序尝试（调用方拿到的 code 从哪来，取决于员工从哪里打开页面）：</p>
     * <ol>
     *   <li><b>扫码登录 / 网页授权</b>：{@code login.dingtalk.com/oauth2/auth} 回跳带的 code，
     *       经“用户令牌 → contact/users/me → unionId → userid”换出成员ID；</li>
     *   <li><b>工作台免登</b>：钉钉在微应用主页地址后追加的 code，直接
     *       {@code /topapi/v2/user/getuserinfo} 换 userid。</li>
     * </ol>
     * <p>两条路都换不出 userid 时抛异常，由调用方按“不是本企业成员”提示。</p>
     */
    public JSONObject getAuthUserInfo(String code) throws Exception {
        String authCode = OaonesUtil.s(code).trim();
        if (authCode.length() == 0) {
            throw new RuntimeException("钉钉授权码为空");
        }
        OaonesLog.step("调用钉钉换取扫码人身份 appKey=" + appKey);
        String lastError = "";
        try {
            JSONObject me = getUserInfoByUserToken(authCode);
            if (me != null) {
                String unionId = OaonesUtil.s(me.getString("unionId"));
                String userId = getUserIdByUnionId(unionId);
                if (userId.length() > 0) {
                    JSONObject result = new JSONObject();
                    result.put("userid", userId);
                    result.put("openid", unionId);
                    result.put("unionid", unionId);
                    result.put("nick", OaonesUtil.s(me.getString("nick")));
                    result.put("errcode", 0);
                    return result;
                }
                lastError = "unionId=" + unionId + " 未换到钉钉成员ID（可能不是本企业成员）";
            }
        } catch (Exception e) {
            lastError = OaonesUtil.s(e.getMessage());
            OaonesLog.step("钉钉扫码授权码换取身份失败，回退按工作台免登 code 处理: " + lastError);
        }
        String userId = getUserIdByJsapiCode(authCode);
        if (userId.length() > 0) {
            JSONObject result = new JSONObject();
            result.put("userid", userId);
            result.put("errcode", 0);
            return result;
        }
        throw new RuntimeException("钉钉授权码换取成员身份失败（不是该企业成员或应用可见范围外）"
                + (lastError.length() == 0 ? "" : "：" + lastError));
    }

    /** 第一步：OAuth2 授权码换用户令牌，再取当前登录用户信息（含 unionId）。 */
    public JSONObject getUserInfoByUserToken(String authCode) throws Exception {
        JSONObject tokenReq = new JSONObject();
        tokenReq.put("clientId", appKey);
        tokenReq.put("clientSecret", appSecret);
        tokenReq.put("code", OaonesUtil.s(authCode));
        tokenReq.put("grantType", "authorization_code");
        JSONObject tokenResp = requestJson("POST", openApiBase + "/v1.0/oauth2/userAccessToken", tokenReq, null);
        String userToken = OaonesUtil.s(tokenResp.getString("accessToken"));
        if (userToken.length() == 0) {
            throw new RuntimeException("钉钉用户令牌换取失败: " + tokenResp.toJSONString());
        }
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-acs-dingtalk-access-token", userToken);
        return requestJson("GET", openApiBase + "/v1.0/contact/users/me", null, headers);
    }

    /** unionId → 成员 userid（企业内部应用）。 */
    public String getUserIdByUnionId(String unionId) throws Exception {
        String id = OaonesUtil.s(unionId).trim();
        if (id.length() == 0) {
            return "";
        }
        JSONObject body = new JSONObject();
        body.put("unionid", id);
        JSONObject resp = post("/topapi/user/getbyunionid", body);
        if (resp.getIntValue("errcode") != 0) {
            OaonesLog.step("钉钉 unionId 换成员ID失败 unionId=" + id
                    + " errcode=" + resp.getIntValue("errcode")
                    + " errmsg=" + OaonesUtil.s(resp.getString("errmsg")));
            return "";
        }
        JSONObject result = resp.getJSONObject("result");
        return result == null ? "" : OaonesUtil.s(result.getString("userid")).trim();
    }

    /** 工作台免登 code → 成员 userid。 */
    public String getUserIdByJsapiCode(String authCode) throws Exception {
        JSONObject body = new JSONObject();
        body.put("code", OaonesUtil.s(authCode));
        JSONObject resp = post("/topapi/v2/user/getuserinfo", body);
        if (resp.getIntValue("errcode") != 0) {
            OaonesLog.step("钉钉免登 code 换成员ID失败 errcode=" + resp.getIntValue("errcode")
                    + " errmsg=" + OaonesUtil.s(resp.getString("errmsg")));
            return "";
        }
        JSONObject result = resp.getJSONObject("result");
        return result == null ? "" : OaonesUtil.s(result.getString("userid")).trim();
    }

    /* ------------------------------------------------------------------
     * 字段归一化：钉钉 → 与钉钉一致的字段名
     * ------------------------------------------------------------------ */

    /** 部门归一化：{@code dept_id→id}、{@code parent_id→parentid}。 */
    public JSONObject normalizeDepartment(JSONObject src) {
        JSONObject dept = new JSONObject();
        if (src == null) {
            return dept;
        }
        dept.put("id", src.getLongValue("dept_id"));
        dept.put("name", OaonesUtil.s(src.getString("name")));
        dept.put("parentid", src.containsKey("parent_id") ? src.getLongValue("parent_id") : 0L);
        dept.put("order", src.containsKey("order") ? src.getLongValue("order") : 0L);
        // 快照表 status：钉钉没有“关闭/封存”这一说，拉到的部门一律按正常写 0
        dept.put("status", 0);
        dept.put("source", src);
        return dept;
    }

    /**
     * 成员归一化：钉钉字段 → 钉钉字段名。
     *
     * <table>
     *   <tr><td>userid</td><td>userid</td></tr>
     *   <tr><td>name</td><td>name</td></tr>
     *   <tr><td>mobile</td><td>mobile</td></tr>
     *   <tr><td>email / org_email</td><td>email / biz_mail</td></tr>
     *   <tr><td>title</td><td>position</td></tr>
     *   <tr><td>job_number</td><td>workcode</td></tr>
     *   <tr><td>dept_id_list</td><td>department（数组）</td></tr>
     *   <tr><td>dept_order_list 里 order 非 0 的部门</td><td>main_department（主部门）</td></tr>
     *   <tr><td>active / status</td><td>status（1 激活 / 2 禁用 / 4 未激活 / 5 退出）</td></tr>
     * </table>
     */
    public JSONObject normalizeUser(JSONObject src) {
        JSONObject user = new JSONObject();
        if (src == null) {
            return user;
        }
        user.put("userid", OaonesUtil.s(src.getString("userid")));
        user.put("name", OaonesUtil.s(src.getString("name")));
        user.put("mobile", OaonesUtil.s(src.getString("mobile")));
        user.put("email", OaonesUtil.s(src.getString("email")));
        user.put("biz_mail", OaonesUtil.s(src.getString("org_email")));
        user.put("position", OaonesUtil.s(src.getString("title")));
        user.put("telephone", OaonesUtil.s(src.getString("telephone")));
        user.put("avatar", OaonesUtil.s(src.getString("avatar")));
        user.put("workcode", OaonesUtil.s(src.getString("job_number")));
        // 钉钉不返回性别，留空（快照列允许为空，页面按“-”展示）
        user.put("gender", "");
        user.put("status", mapUserStatus(src));
        JSONArray depts = src.getJSONArray("dept_id_list");
        user.put("department", depts == null ? new JSONArray() : depts);
        user.put("main_department", mainDeptId(src, depts));
        user.put("unionid", OaonesUtil.s(src.getString("unionid")));
        user.put("source", src);
        return user;
    }

    /**
     * 成员状态：钉钉 {@code active=false} 表示未激活/已停用；
     * 部分接口返回 {@code status}（1 已激活 / 2 已禁用 / 3 未激活 / 4 待激活 / 5 退出），
     * 统一映射成钉钉口径的 1 / 2 / 4 / 5（快照列再按它压成从 0 开始的序号）。
     */
    private String mapUserStatus(JSONObject src) {
        if (src.containsKey("active")) {
            return src.getBooleanValue("active") ? "1" : "2";
        }
        int status = src.getIntValue("status");
        if (status == 1) {
            return "1";
        }
        if (status == 2) {
            return "2";
        }
        if (status == 3 || status == 4) {
            return "4";
        }
        if (status == 5) {
            return "5";
        }
        return "";
    }

    /** 主部门：优先 {@code dept_order_list} 里 order 非 0 的部门，其次部门列表第一个。 */
    private long mainDeptId(JSONObject src, JSONArray depts) {
        JSONArray orders = src.getJSONArray("dept_order_list");
        if (orders != null) {
            for (int i = 0; i < orders.size(); i++) {
                JSONObject row = orders.getJSONObject(i);
                if (row != null && row.getLongValue("order") != 0L) {
                    return row.getLongValue("dept_id");
                }
            }
        }
        if (depts != null && depts.size() > 0) {
            return depts.getLongValue(0);
        }
        return 0L;
    }

    /** 归一化成员 → 钉钉成员接口入参。 */
    private JSONObject userBody(JSONObject user) {
        JSONObject body = new JSONObject();
        if (user == null) {
            return body;
        }
        body.put("userid", OaonesUtil.s(user.getString("userid")));
        body.put("name", OaonesUtil.s(user.getString("name")));
        String mobile = OaonesUtil.s(user.getString("mobile"));
        if (mobile.length() > 0) {
            body.put("mobile", mobile);
        }
        String email = OaonesUtil.s(user.getString("email"));
        if (email.length() > 0) {
            body.put("email", email);
        }
        String position = OaonesUtil.s(user.getString("position"));
        if (position.length() > 0) {
            body.put("title", position);
        }
        String workcode = OaonesUtil.s(user.getString("workcode"));
        if (workcode.length() > 0) {
            body.put("job_number", workcode);
        }
        String telephone = OaonesUtil.s(user.getString("telephone"));
        if (telephone.length() > 0) {
            body.put("telephone", telephone);
        }
        String managerUserId = OaonesUtil.s(user.getString("manager_userid"));
        if (managerUserId.length() > 0) {
            body.put("manager_userid", managerUserId);
        }
        JSONArray depts = user.getJSONArray("department");
        JSONArray deptIds = new JSONArray();
        if (depts != null) {
            for (int i = 0; i < depts.size(); i++) {
                long id = depts.getLongValue(i);
                if (id > 0) {
                    deptIds.add(id);
                }
            }
        }
        if (deptIds.isEmpty()) {
            deptIds.add(ROOT_DEPT_ID);
        }
        body.put("dept_id_list", deptIds);
        return body;
    }

    /* ------------------------------------------------------------------
     * HTTP
     * ------------------------------------------------------------------ */

    /** 带 access_token 的 POST（topapi 老接口：token 放在查询串里）。 */
    private JSONObject post(String path, JSONObject body) throws Exception {
        return requestJson("POST", apiBase + path + "?access_token=" + getAccessToken(), body, null);
    }

    private void ensureOk(JSONObject resp, String tip) {
        if (resp == null) {
            throw new RuntimeException(tip + ": 无响应");
        }
        if (resp.getIntValue("errcode") != 0) {
            throw new RuntimeException(tip + ": errcode=" + resp.getIntValue("errcode")
                    + ", errmsg=" + OaonesUtil.s(resp.getString("errmsg")));
        }
    }

    private JSONObject requestJson(String method, String url, JSONObject body,
                                   Map<String, String> headers) throws Exception {
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
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
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
            OaonesApiLog.out(OaonesConst.TYPE_DINGTALK, appKey, method, url, reqText, text, code,
                    System.currentTimeMillis() - start, null);
            JSONObject resp = JSON.parseObject(text);
            return resp == null ? new JSONObject() : resp;
        } catch (Exception e) {
            OaonesApiLog.out(OaonesConst.TYPE_DINGTALK, appKey, method, url, reqText, "", 0,
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
