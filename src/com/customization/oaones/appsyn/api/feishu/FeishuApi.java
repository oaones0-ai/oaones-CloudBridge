package com.customization.oaones.appsyn.api.feishu;

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
 * 飞书开放接口封装（通讯录 + 扫码/免登换取身份）。
 *
 * <p>一套 App ID + App Secret（企业自建应用）复用一个实例，内部缓存 tenant_access_token
 * （换取身份时另缓存 app_access_token）。与 {@code api.wecom.WeComApi}、{@code api.dingtalk.DingTalkApi}
 * 的分工完全对称：**对上层暴露同一组方法与同一套字段名**
 * （成员 {@code userid/name/mobile/email/position/department/main_department}，
 * 部门 {@code id/name/parentid/order}），把飞书的字段名
 * （{@code open_department_id/parent_department_id/employee_no/job_title/department_ids}）
 * 在 {@link #normalizeUser(JSONObject)} 与 {@link #normalizeDepartment(JSONObject)} 里归一化，
 * 这样绑定/同步/选择框等业务代码不必为了一个连接器再写一套字段口径。</p>
 *
 * <p><b>部门ID是字符串</b>：飞书的部门标识形如 {@code od-xxxxxxxx}（根部门是 {@code 0}），
 * 与企微/钉钉的数值部门ID不同，因此本连接器的部门标识一律按字符串处理
 * （业务表用 {@code wx_dept_key} 列存，见 {@code sql/public/alter_oaones_platform_feishu.sql}）。</p>
 *
 * <p>接口来源（飞书开放平台）：</p>
 * <ul>
 *   <li>自建应用 token：{@code POST /open-apis/auth/v3/tenant_access_token/internal}
 *       （换取身份时另用 {@code /open-apis/auth/v3/app_access_token/internal}）；</li>
 *   <li>部门：{@code GET /open-apis/contact/v3/departments/{department_id}}、
 *       {@code GET /open-apis/contact/v3/departments/{department_id}/children}、
 *       {@code POST|PATCH|DELETE /open-apis/contact/v3/departments}；</li>
 *   <li>成员：{@code GET /open-apis/contact/v3/users/{user_id}}、
 *       {@code GET /open-apis/contact/v3/users/find_by_department}、
 *       {@code POST|PATCH|DELETE /open-apis/contact/v3/users}；</li>
 *   <li>网页授权（扫码登录 / 工作台免登 / 扫码绑定）：授权页
 *       {@code {fs.login.base}{fs.login.auth.path}} 回跳带 {@code code} →
 *       {@code POST /open-apis/authen/v1/oidc/access_token} 换 user_access_token →
 *       {@code GET /open-apis/authen/v1/user_info} 拿 user_id / open_id / union_id。</li>
 * </ul>
 */
public class FeishuApi {

    /** 飞书根部门ID（飞书通讯录根节点固定为字符串 {@code 0}）。 */
    public static final String ROOT_DEPT_ID = "0";
    /** 递归遍历的最大层级与分页保护（防止接口异常时无限递归）。 */
    private static final int MAX_DEPTH = 20;
    private static final int MAX_PAGES = 200;
    /** 分页拉取的每页条数（飞书通讯录接口上限 50）。 */
    private static final int PAGE_SIZE = 50;
    /** open_id 前缀：飞书的 open_id 形如 ou_xxx。 */
    private static final String OPEN_ID_PREFIX = "ou_";

    private final String appId;
    private final String appSecret;
    private final String apiBase;
    private String tenantToken;
    private long tenantTokenExpireTime;
    private String appToken;
    private long appTokenExpireTime;

    public FeishuApi(String appId, String appSecret) {
        this.appId = OaonesUtil.s(appId).trim();
        this.appSecret = OaonesUtil.s(appSecret).trim();
        this.apiBase = OaonesProps.fsApiBase();
    }

    public String getAppId() {
        return appId;
    }

    /** 企业自建应用 tenant_access_token（缓存，剩余不足 5 分钟时自动刷新）。 */
    public String getAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (tenantToken != null && tenantToken.length() > 0 && now < tenantTokenExpireTime) {
            return tenantToken;
        }
        OaonesLog.step("获取飞书 tenant_access_token（appId=" + appId + "）");
        JSONObject body = new JSONObject();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);
        JSONObject resp = requestJson("POST",
                apiBase + "/open-apis/auth/v3/tenant_access_token/internal", body, null);
        ensureOk(resp, "获取飞书 tenant_access_token 失败");
        String token = OaonesUtil.s(resp.getString("tenant_access_token"));
        if (token.length() == 0) {
            throw new RuntimeException("获取飞书 tenant_access_token 失败: " + resp.toJSONString());
        }
        int expire = resp.getIntValue("expire");
        if (expire <= 0) {
            expire = 7200;
        }
        this.tenantToken = token;
        this.tenantTokenExpireTime = now + ttlMillis(expire);
        return token;
    }

    /**
     * 企业自建应用 app_access_token（缓存）。
     *
     * <p>“授权码换 user_access_token”（{@code /open-apis/authen/v1/oidc/access_token}）
     * 要求请求头带的是 <b>app_access_token</b>，因此这里单独取一次并缓存；
     * 该接口失败时上层会退回“body 里带 app_access_token”的老口径
     * （{@code /open-apis/authen/v1/access_token}），两条路都试不到才算失败。</p>
     */
    public String getAppToken() throws Exception {
        long now = System.currentTimeMillis();
        if (appToken != null && appToken.length() > 0 && now < appTokenExpireTime) {
            return appToken;
        }
        JSONObject body = new JSONObject();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);
        JSONObject resp = requestJson("POST",
                apiBase + "/open-apis/auth/v3/app_access_token/internal", body, null);
        ensureOk(resp, "获取飞书 app_access_token 失败");
        String token = OaonesUtil.s(resp.getString("app_access_token"));
        if (token.length() == 0) {
            throw new RuntimeException("获取飞书 app_access_token 失败: " + resp.toJSONString());
        }
        int expire = resp.getIntValue("expire");
        if (expire <= 0) {
            expire = 7200;
        }
        this.appToken = token;
        this.appTokenExpireTime = now + ttlMillis(expire);
        return token;
    }

    /** token 有效期：提前 5 分钟失效，最少留 1 分钟。 */
    private long ttlMillis(int expireSeconds) {
        long ttl = (expireSeconds - 300) * 1000L;
        return ttl < 60 * 1000L ? 60 * 1000L : ttl;
    }

    /* ------------------------------------------------------------------
     * 通讯录：部门（字段口径与企微一致：id / name / parentid / order）
     * ------------------------------------------------------------------ */

    /**
     * 拉取全量部门（含根部门）。
     *
     * <p>飞书没有“一次拉全部部门”的接口，这里从根部门 {@code 0} 起按
     * {@code /open-apis/contact/v3/departments/{department_id}/children} 递归取子部门，
     * 与企微 {@code /cgi-bin/department/list} 的返回口径对齐。</p>
     */
    public JSONArray listDepartments() throws Exception {
        OaonesLog.step("调用飞书获取部门列表（根部门 " + ROOT_DEPT_ID + " 起递归）");
        JSONArray all = new JSONArray();
        JSONObject root = getDepartment(ROOT_DEPT_ID);
        if (root != null) {
            all.add(normalizeDepartment(root));
        } else {
            // 根部门取不到时补一条虚拟根，保证“根部门”在绑定/同步口径里始终存在
            JSONObject virtualRoot = new JSONObject();
            virtualRoot.put("open_department_id", ROOT_DEPT_ID);
            virtualRoot.put("name", "根部门");
            virtualRoot.put("parent_department_id", ROOT_DEPT_ID);
            all.add(normalizeDepartment(virtualRoot));
        }
        collectDepartments(ROOT_DEPT_ID, all, 0);
        return all;
    }

    /** 某个部门下的直属子部门（字段口径与 {@link #listDepartments()} 一致）。 */
    public JSONArray listSubDepartments(String deptId) throws Exception {
        String id = deptKey(deptId);
        JSONArray normalized = new JSONArray();
        String pageToken = "";
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> query = new HashMap<String, String>();
            query.put("department_id_type", "open_department_id");
            query.put("user_id_type", "open_id");
            query.put("fetch_child", "false");
            query.put("page_size", String.valueOf(PAGE_SIZE));
            if (pageToken.length() > 0) {
                query.put("page_token", pageToken);
            }
            JSONObject resp = get("/open-apis/contact/v3/departments/"
                    + OaonesUtil.urlEncode(id) + "/children", query);
            if (!isOk(resp)) {
                // 子部门不存在/无权限时按“没有子部门”处理，不打断整体同步
                OaonesLog.step("飞书获取子部门为空 deptId=" + id + " code=" + resp.getIntValue("code")
                        + " msg=" + OaonesUtil.s(resp.getString("msg")));
                return normalized;
            }
            JSONObject data = resp.getJSONObject("data");
            JSONArray items = data == null ? null : data.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject row = items.getJSONObject(i);
                    if (row != null) {
                        normalized.add(normalizeDepartment(row));
                    }
                }
            }
            if (data == null || !data.getBooleanValue("has_more")) {
                break;
            }
            String next = OaonesUtil.s(data.getString("page_token"));
            if (next.length() == 0 || next.equals(pageToken)) {
                break;
            }
            pageToken = next;
        }
        return normalized;
    }

    /** 单个部门详情；不存在返回 null。 */
    public JSONObject getDepartment(String deptId) throws Exception {
        String id = deptKey(deptId);
        Map<String, String> query = new HashMap<String, String>();
        query.put("department_id_type", "open_department_id");
        JSONObject resp = get("/open-apis/contact/v3/departments/" + OaonesUtil.urlEncode(id), query);
        if (!isOk(resp)) {
            return null;
        }
        JSONObject data = resp.getJSONObject("data");
        JSONObject dept = data == null ? null : data.getJSONObject("department");
        if (dept == null || OaonesUtil.s(dept.getString("open_department_id")).length() == 0) {
            return null;
        }
        return dept;
    }

    /** 创建部门，返回飞书部门ID（字符串）。 */
    public String createDepartment(String name, String parentId, Integer order) throws Exception {
        OaonesLog.step("调用飞书创建部门 name=" + name + " parentId=" + parentId);
        JSONObject body = new JSONObject();
        body.put("name", OaonesUtil.s(name));
        body.put("parent_department_id", deptKey(parentId));
        body.put("order", order == null ? "0" : String.valueOf(order.intValue()));
        Map<String, String> query = new HashMap<String, String>();
        query.put("department_id_type", "open_department_id");
        JSONObject resp = post("/open-apis/contact/v3/departments", body, query);
        ensureOk(resp, "创建飞书部门失败");
        JSONObject data = resp.getJSONObject("data");
        JSONObject dept = data == null ? null : data.getJSONObject("department");
        String deptId = dept == null ? "" : OaonesUtil.s(dept.getString("open_department_id"));
        if (deptId.length() == 0) {
            throw new RuntimeException("创建飞书部门失败: 返回体没有 open_department_id, resp=" + resp.toJSONString());
        }
        return deptId;
    }

    /** 更新部门（只更新传入的字段；部门ID是字符串）。 */
    public void updateDepartment(String id, String name, String parentId, Integer order) throws Exception {
        String deptId = deptKey(id);
        OaonesLog.step("调用飞书更新部门 id=" + deptId);
        JSONObject body = new JSONObject();
        if (name != null && name.length() > 0) {
            body.put("name", name);
        }
        if (parentId != null && OaonesUtil.s(parentId).trim().length() > 0) {
            body.put("parent_department_id", deptKey(parentId));
        }
        if (order != null) {
            body.put("order", String.valueOf(order.intValue()));
        }
        if (body.isEmpty()) {
            return;
        }
        Map<String, String> query = new HashMap<String, String>();
        query.put("department_id_type", "open_department_id");
        JSONObject resp = patch("/open-apis/contact/v3/departments/" + OaonesUtil.urlEncode(deptId), body, query);
        ensureOk(resp, "更新飞书部门失败");
    }

    /** 删除部门（飞书要求部门下没有成员和子部门）。 */
    public void deleteDepartment(String id) throws Exception {
        String deptId = deptKey(id);
        Map<String, String> query = new HashMap<String, String>();
        query.put("department_id_type", "open_department_id");
        JSONObject resp = delete("/open-apis/contact/v3/departments/" + OaonesUtil.urlEncode(deptId), query);
        ensureOk(resp, "删除飞书部门失败");
    }

    private void collectDepartments(String parentId, JSONArray all, int depth) throws Exception {
        if (depth >= MAX_DEPTH) {
            return;
        }
        JSONArray children = listSubDepartments(parentId);
        for (int i = 0; i < children.size(); i++) {
            JSONObject child = children.getJSONObject(i);
            all.add(child);
            String childId = OaonesUtil.s(child.getString("id"));
            if (childId.length() > 0) {
                collectDepartments(childId, all, depth + 1);
            }
        }
    }

    /* ------------------------------------------------------------------
     * 通讯录：成员（字段口径与企微一致：userid / name / mobile / department …）
     * ------------------------------------------------------------------ */

    /**
     * 拉取部门下成员（详细字段）。
     *
     * @param departmentId 起始部门（一般取租户配置里的根部门，字符串）
     * @param fetchChild   是否包含子部门成员（飞书按部门分页取，子部门在这里递归展开）
     */
    public JSONArray listUsers(String departmentId, boolean fetchChild) throws Exception {
        String deptId = deptKey(departmentId);
        OaonesLog.step("调用飞书获取成员列表 departmentId=" + deptId + " fetchChild=" + fetchChild);
        JSONArray result = new JSONArray();
        Set<String> visited = new HashSet<String>();
        collectUsers(deptId, result, visited, fetchChild, 0);
        return result;
    }

    private void collectUsers(String deptId, JSONArray out, Set<String> visited,
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
            String childId = OaonesUtil.s(children.getJSONObject(i).getString("id"));
            if (childId.length() > 0) {
                collectUsers(childId, out, visited, true, depth + 1);
            }
        }
    }

    /** 某个部门的直属成员（分页拉完，字段已归一化）。 */
    public JSONArray listUsersOfDept(String deptId) throws Exception {
        String id = deptKey(deptId);
        JSONArray users = new JSONArray();
        Set<String> seen = new HashSet<String>();
        String pageToken = "";
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> query = new HashMap<String, String>();
            query.put("department_id", id);
            query.put("department_id_type", "open_department_id");
            query.put("user_id_type", "open_id");
            query.put("page_size", String.valueOf(PAGE_SIZE));
            if (pageToken.length() > 0) {
                query.put("page_token", pageToken);
            }
            JSONObject resp = get("/open-apis/contact/v3/users/find_by_department", query);
            if (!isOk(resp)) {
                OaonesLog.step("飞书拉取部门成员失败 deptId=" + id
                        + " code=" + resp.getIntValue("code")
                        + " msg=" + OaonesUtil.s(resp.getString("msg")));
                break;
            }
            JSONObject data = resp.getJSONObject("data");
            if (data == null) {
                break;
            }
            JSONArray items = data.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject row = items.getJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    String openId = OaonesUtil.s(row.getString("open_id"));
                    if (openId.length() > 0 && !seen.add(openId)) {
                        continue;
                    }
                    if (memberId(row).length() > 0) {
                        users.add(normalizeUser(row));
                    }
                }
            }
            if (!data.getBooleanValue("has_more")) {
                break;
            }
            String next = OaonesUtil.s(data.getString("page_token"));
            if (next.length() == 0 || next.equals(pageToken)) {
                break;
            }
            pageToken = next;
        }
        return users;
    }

    /**
     * 查询单个成员；不存在返回 null。
     *
     * <p>{@code userId} 传本模块统一口径的成员标识（飞书 user_id 或 open_id），
     * 内部按前缀判断用哪种 {@code user_id_type}。</p>
     */
    public JSONObject getUser(String userId) throws Exception {
        String id = OaonesUtil.s(userId).trim();
        if (id.length() == 0) {
            return null;
        }
        Map<String, String> query = new HashMap<String, String>();
        query.put("user_id_type", idType(id));
        JSONObject resp = get("/open-apis/contact/v3/users/" + OaonesUtil.urlEncode(id), query);
        if (!isOk(resp)) {
            // 成员不存在 / 已删除 / 不在应用可见范围：按“查不到”返回
            OaonesLog.step("飞书查询成员为空 userid=" + id + " code=" + resp.getIntValue("code")
                    + " msg=" + OaonesUtil.s(resp.getString("msg")));
            return null;
        }
        JSONObject data = resp.getJSONObject("data");
        JSONObject user = data == null ? null : data.getJSONObject("user");
        if (user == null || memberId(user).length() == 0) {
            return null;
        }
        return normalizeUser(user);
    }

    public boolean userExists(String userId) throws Exception {
        return getUser(userId) != null;
    }

    /** 创建成员（入参为归一化口径，内部转成飞书字段）。 */
    public JSONObject createUser(JSONObject user) throws Exception {
        OaonesLog.step("调用飞书创建成员 userid="
                + OaonesUtil.s(user == null ? "" : user.getString("userid")));
        Map<String, String> query = new HashMap<String, String>();
        query.put("user_id_type", "open_id");
        query.put("department_id_type", "open_department_id");
        JSONObject resp = post("/open-apis/contact/v3/users", userBody(user, false), query);
        ensureOk(resp, "创建飞书成员失败");
        JSONObject data = resp.getJSONObject("data");
        JSONObject created = data == null ? null : data.getJSONObject("user");
        return created == null ? new JSONObject() : normalizeUser(created);
    }

    /** 更新成员（入参为归一化口径，内部转成飞书字段）。 */
    public void updateUser(JSONObject user) throws Exception {
        String userId = OaonesUtil.s(user == null ? "" : user.getString("userid")).trim();
        if (userId.length() == 0) {
            throw new RuntimeException("更新飞书成员失败：缺少成员ID");
        }
        OaonesLog.step("调用飞书更新成员 userid=" + userId);
        Map<String, String> query = new HashMap<String, String>();
        query.put("user_id_type", idType(userId));
        query.put("department_id_type", "open_department_id");
        JSONObject resp = patch("/open-apis/contact/v3/users/" + OaonesUtil.urlEncode(userId),
                userBody(user, true), query);
        ensureOk(resp, "更新飞书成员失败");
    }

    /** 删除成员。 */
    public void deleteUser(String userId) throws Exception {
        String id = OaonesUtil.s(userId).trim();
        Map<String, String> query = new HashMap<String, String>();
        query.put("user_id_type", idType(id));
        JSONObject resp = delete("/open-apis/contact/v3/users/" + OaonesUtil.urlEncode(id), query);
        ensureOk(resp, "删除飞书成员失败");
    }

    /* ------------------------------------------------------------------
     * 网页授权 / 工作台免登：code 换成员身份
     * ------------------------------------------------------------------ */

    /**
     * 用授权码换取扫码人身份，返回体与企微口径一致：本企业成员带 {@code userid}。
     *
     * <p>三种来源的 code 都是飞书网页授权回跳带的同一个 code（自建应用扫码登录、
     * 工作台网页应用免登、个人账号中心扫码绑定），因此这里只有一条链路：</p>
     * <ol>
     *   <li>{@code POST /open-apis/authen/v1/oidc/access_token}（带 app_access_token）
     *       用 code 换 user_access_token；</li>
     *   <li>{@code GET /open-apis/authen/v1/user_info} 拿当前登录人身份
     *       （{@code user_id} / {@code open_id} / {@code union_id} / 姓名 / 手机号）。</li>
     * </ol>
     * <p>成员标识优先取 {@code user_id}（企业内稳定、与通讯录接口口径一致），
     * 应用没申请 {@code user_id} 权限时退回 {@code open_id}（通讯录接口同样能识别）。
     * 换不出身份时抛异常，由调用方按“不是本企业成员”提示。</p>
     */
    public JSONObject getAuthUserInfo(String code) throws Exception {
        String authCode = OaonesUtil.s(code).trim();
        if (authCode.length() == 0) {
            throw new RuntimeException("飞书授权码为空");
        }
        OaonesLog.step("调用飞书换取扫码人身份 appId=" + appId);
        JSONObject userAccess = getUserInfoByUserToken(authCode);
        String userToken = OaonesUtil.s(userAccess.getString("access_token"));
        if (userToken.length() == 0) {
            throw new RuntimeException("飞书用户令牌换取失败: " + userAccess.toJSONString());
        }
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + userToken);
        JSONObject infoResp = requestJson("GET", apiBase + "/open-apis/authen/v1/user_info", null, headers);
        ensureOk(infoResp, "飞书获取登录用户信息失败");
        JSONObject info = infoResp.getJSONObject("data");
        if (info == null) {
            throw new RuntimeException("飞书获取登录用户信息失败: " + infoResp.toJSONString());
        }
        String userId = OaonesUtil.s(info.getString("user_id")).trim();
        String openId = OaonesUtil.s(info.getString("open_id")).trim();
        if (openId.length() == 0) {
            openId = OaonesUtil.s(userAccess.getString("open_id")).trim();
        }
        if (userId.length() == 0 && openId.length() == 0) {
            throw new RuntimeException("飞书授权码换取成员身份失败（不是该企业成员或应用可见范围外）："
                    + infoResp.toJSONString());
        }
        JSONObject result = new JSONObject();
        result.put("userid", userId.length() > 0 ? userId : openId);
        result.put("user_id", userId);
        result.put("openid", openId);
        result.put("unionid", OaonesUtil.s(info.getString("union_id")));
        result.put("nick", OaonesUtil.s(info.getString("name")));
        result.put("mobile", OaonesUtil.s(info.getString("mobile")));
        result.put("email", OaonesUtil.s(info.getString("email")));
        result.put("avatar", OaonesUtil.s(info.getString("avatar_url")));
        result.put("errcode", 0);
        return result;
    }

    /**
     * 授权码换 user_access_token。
     *
     * <p>先走官方推荐口径 {@code /open-apis/authen/v1/oidc/access_token}
     * （请求头带 app_access_token）；部分老环境/老应用只认
     * {@code /open-apis/authen/v1/access_token}（app_access_token 放在 body 里），
     * 因此失败时自动回退一次，避免因为开放平台版本差异导致扫码登录不可用。</p>
     */
    public JSONObject getUserInfoByUserToken(String authCode) throws Exception {
        JSONObject body = new JSONObject();
        body.put("grant_type", "authorization_code");
        body.put("code", OaonesUtil.s(authCode));
        try {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "Bearer " + getAppToken());
            JSONObject resp = requestJson("POST",
                    apiBase + "/open-apis/authen/v1/oidc/access_token", body, headers);
            JSONObject data = resp.getJSONObject("data");
            if (isOk(resp) && data != null && OaonesUtil.s(data.getString("access_token")).length() > 0) {
                return data;
            }
            OaonesLog.step("飞书 oidc/access_token 换取用户令牌失败 code=" + resp.getIntValue("code")
                    + " msg=" + OaonesUtil.s(resp.getString("msg")) + "，回退 v1/access_token");
        } catch (Exception e) {
            OaonesLog.step("飞书 oidc/access_token 调用异常，回退 v1/access_token: " + OaonesUtil.s(e.getMessage()));
        }
        JSONObject fallback = new JSONObject();
        fallback.put("grant_type", "authorization_code");
        fallback.put("code", OaonesUtil.s(authCode));
        fallback.put("app_access_token", getAppToken());
        JSONObject resp = requestJson("POST", apiBase + "/open-apis/authen/v1/access_token", fallback, null);
        ensureOk(resp, "飞书授权码换取用户令牌失败");
        JSONObject data = resp.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("飞书授权码换取用户令牌失败: " + resp.toJSONString());
        }
        return data;
    }

    /* ------------------------------------------------------------------
     * 字段归一化：飞书 → 企微同名口径（业务层不感知平台字段差异）
     * ------------------------------------------------------------------ */

    /** 部门归一化：{@code open_department_id→id}、{@code parent_department_id→parentid}。 */
    public JSONObject normalizeDepartment(JSONObject src) {
        JSONObject dept = new JSONObject();
        if (src == null) {
            return dept;
        }
        dept.put("id", deptKey(src.getString("open_department_id")));
        dept.put("name", OaonesUtil.s(src.getString("name")));
        dept.put("parentid", deptKey(src.getString("parent_department_id")));
        dept.put("order", OaonesUtil.s(src.getString("order")));
        // 快照表 status：飞书用 status.is_deleted 标记已删除的部门，拉到的部门一律按正常写 0
        dept.put("status", 0);
        dept.put("source", src);
        return dept;
    }

    /**
     * 成员归一化：飞书字段 → 企微字段名。
     *
     * <table>
     *   <tr><td>userid</td><td>user_id（取不到时用 open_id）</td></tr>
     *   <tr><td>name</td><td>name</td></tr>
     *   <tr><td>mobile</td><td>mobile</td></tr>
     *   <tr><td>email / biz_mail</td><td>email / enterprise_email</td></tr>
     *   <tr><td>position</td><td>job_title</td></tr>
     *   <tr><td>workcode</td><td>employee_no</td></tr>
     *   <tr><td>department</td><td>department_ids（部门ID数组，字符串）</td></tr>
     *   <tr><td>main_department</td><td>部门列表第一个</td></tr>
     *   <tr><td>avatar</td><td>avatar.avatar_72（取不到时用 avatar_origin / avatar_url）</td></tr>
     *   <tr><td>status</td><td>status.is_activated / is_frozen / is_resigned</td></tr>
     * </table>
     */
    public JSONObject normalizeUser(JSONObject src) {
        JSONObject user = new JSONObject();
        if (src == null) {
            return user;
        }
        user.put("userid", memberId(src));
        user.put("name", OaonesUtil.s(src.getString("name")));
        user.put("mobile", OaonesUtil.s(src.getString("mobile")));
        user.put("email", OaonesUtil.s(src.getString("email")));
        user.put("biz_mail", OaonesUtil.s(src.getString("enterprise_email")));
        user.put("position", OaonesUtil.s(src.getString("job_title")));
        user.put("telephone", OaonesUtil.s(src.getString("mobile_visible")));
        user.put("avatar", avatarOf(src));
        user.put("workcode", OaonesUtil.s(src.getString("employee_no")));
        // 飞书性别口径：1 男 / 2 女；0 或缺失按未知处理。
        int gender = src.getIntValue("gender");
        user.put("gender", gender > 0 ? String.valueOf(gender) : "");
        user.put("status", mapUserStatus(src));
        JSONArray depts = deptKeys(src.getJSONArray("department_ids"));
        user.put("department", depts);
        user.put("main_department", depts.size() > 0 ? depts.getString(0) : ROOT_DEPT_ID);
        user.put("unionid", OaonesUtil.s(src.getString("union_id")));
        user.put("open_id", OaonesUtil.s(src.getString("open_id")));
        user.put("source", src);
        return user;
    }

    /**
     * 成员状态：统一映射成企微口径的 1 激活 / 2 禁用 / 4 未激活 / 5 退出
     * （快照列再按它压成从 0 开始的序号）。
     *
     * <p>飞书 {@code status} 是对象：{@code is_activated} 已激活、{@code is_frozen} 冻结（禁用）、
     * {@code is_resigned} 已离职；都没给时按未激活处理。</p>
     */
    private String mapUserStatus(JSONObject src) {
        JSONObject status = src.getJSONObject("status");
        if (status == null) {
            return "1";
        }
        if (status.containsKey("is_resigned") && status.getBooleanValue("is_resigned")) {
            return "5";
        }
        if (status.containsKey("is_frozen") && status.getBooleanValue("is_frozen")) {
            return "2";
        }
        if (status.containsKey("is_activated") && status.getBooleanValue("is_activated")) {
            return "1";
        }
        return "4";
    }

    /** 头像地址：优先取 72px 一档（页面用小图），取不到依次回退。 */
    private String avatarOf(JSONObject src) {
        JSONObject avatar = src.getJSONObject("avatar");
        if (avatar != null) {
            String mid = OaonesUtil.s(avatar.getString("avatar_72"));
            if (mid.length() == 0) {
                mid = OaonesUtil.s(avatar.getString("avatar_240"));
            }
            if (mid.length() == 0) {
                mid = OaonesUtil.s(avatar.getString("avatar_origin"));
            }
            if (mid.length() > 0) {
                return mid;
            }
        }
        return OaonesUtil.s(src.getString("avatar_url"));
    }

    /**
     * 归一化成员 → 飞书成员接口入参。
     *
     * @param forUpdate 更新场景（PATCH）不带 open_id 这类主键字段
     */
    private JSONObject userBody(JSONObject user, boolean forUpdate) {
        JSONObject body = new JSONObject();
        if (user == null) {
            return body;
        }
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
            body.put("job_title", position);
        }
        String workcode = OaonesUtil.s(user.getString("workcode"));
        if (workcode.length() > 0) {
            body.put("employee_no", workcode);
        }
        if (user.containsKey("gender")) {
            body.put("gender", user.getIntValue("gender"));
        }
        String leaderUserId = OaonesUtil.s(user.getString("leader_user_id"));
        if (leaderUserId.length() > 0) {
            body.put("leader_user_id", leaderUserId);
        }
        JSONArray depts = user.getJSONArray("department");
        JSONArray deptIds = new JSONArray();
        if (depts != null) {
            for (int i = 0; i < depts.size(); i++) {
                String key = deptKey(depts.getString(i));
                if (key.length() > 0) {
                    deptIds.add(key);
                }
            }
        }
        if (deptIds.isEmpty()) {
            deptIds.add(ROOT_DEPT_ID);
        }
        body.put("department_ids", deptIds);
        if (!forUpdate) {
            // 新建成员时带上 open_id（飞书允许指定），避免同一批数据被重复建号
            String openId = OaonesUtil.s(user.getString("open_id"));
            if (openId.length() > 0) {
                body.put("open_id", openId);
            }
        }
        return body;
    }

    /** 部门ID归一：空值按根部门处理（飞书根部门ID是字符串 "0"）。 */
    private String deptKey(String value) {
        String key = OaonesUtil.s(value).trim();
        return key.length() == 0 ? ROOT_DEPT_ID : key;
    }

    /** 部门ID数组归一成字符串数组。 */
    private JSONArray deptKeys(JSONArray src) {
        JSONArray out = new JSONArray();
        if (src == null) {
            return out;
        }
        for (int i = 0; i < src.size(); i++) {
            String key = OaonesUtil.s(src.getString(i)).trim();
            if (key.length() > 0) {
                out.add(key);
            }
        }
        return out;
    }

    /** 成员标识：优先 user_id（企业内稳定），取不到用 open_id。 */
    private String memberId(JSONObject src) {
        String userId = OaonesUtil.s(src.getString("user_id")).trim();
        if (userId.length() > 0) {
            return userId;
        }
        return OaonesUtil.s(src.getString("open_id")).trim();
    }

    /** 成员ID是 open_id 还是 user_id（通讯录接口按它选 user_id_type）。 */
    private String idType(String userId) {
        return OaonesUtil.s(userId).startsWith(OPEN_ID_PREFIX) ? "open_id" : "user_id";
    }

    /* ------------------------------------------------------------------
     * HTTP
     * ------------------------------------------------------------------ */

    private JSONObject get(String path, Map<String, String> query) throws Exception {
        return requestJson("GET", apiBase + path + queryString(query), null, tokenHeaders());
    }

    private JSONObject post(String path, JSONObject body, Map<String, String> query) throws Exception {
        return requestJson("POST", apiBase + path + queryString(query), body, tokenHeaders());
    }

    private JSONObject patch(String path, JSONObject body, Map<String, String> query) throws Exception {
        return requestJson("PATCH", apiBase + path + queryString(query), body, tokenHeaders());
    }

    private JSONObject delete(String path, Map<String, String> query) throws Exception {
        return requestJson("DELETE", apiBase + path + queryString(query), null, tokenHeaders());
    }

    private Map<String, String> tokenHeaders() throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + getAccessToken());
        return headers;
    }

    private String queryString(Map<String, String> query) throws Exception {
        if (query == null || query.isEmpty()) {
            return "";
        }
        return "?" + OaonesUtil.buildQuery(query);
    }

    /** 飞书统一返回体：{@code code=0} 为成功。 */
    private boolean isOk(JSONObject resp) {
        return resp != null && resp.getIntValue("code") == 0;
    }

    private void ensureOk(JSONObject resp, String tip) {
        if (resp == null) {
            throw new RuntimeException(tip + ": 无响应");
        }
        if (resp.getIntValue("code") != 0) {
            throw new RuntimeException(tip + ": code=" + resp.getIntValue("code")
                    + ", msg=" + OaonesUtil.s(resp.getString("msg")));
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
            OaonesApiLog.out(OaonesConst.TYPE_FEISHU, appId, method, url, reqText, text, code,
                    System.currentTimeMillis() - start, null);
            JSONObject resp = JSON.parseObject(text);
            return resp == null ? new JSONObject() : resp;
        } catch (Exception e) {
            OaonesApiLog.out(OaonesConst.TYPE_FEISHU, appId, method, url, reqText, "", 0,
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
