package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkApi;
import com.customization.oaones.appsyn.api.feishu.FeishuApi;
import com.customization.oaones.appsyn.api.wecom.WeComApi;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.dingtalk.UserBindMapper;
import com.customization.oaones.appsyn.dto.TenantAccountRequest;
import com.customization.oaones.appsyn.dto.TenantAccountResult;
import com.customization.oaones.appsyn.service.TenantAccountService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第三方租户账户创建服务。
 *
 * <p>该服务只负责“OA Action -> 第三方平台通讯录”的建号，不创建 OA 人员，
 * 也不改写部门绑定关系。判断顺序固定为：租户启用 -> OA 部门存在有效绑定 ->
 * 平台部门仍存在 -> 创建/更新平台成员。每个租户无论成功、失败或跳过都会生成一条结果。</p>
 */
public class TenantAccountServiceImpl implements TenantAccountService {

    private static final Platform WECOM = new Platform("wecom", "企业微信");
    private static final Platform DINGTALK = new Platform("dingtalk", "钉钉");
    private static final Platform FEISHU = new Platform("feishu", "飞书");

    @Override
    public TenantAccountResult createInBoundDepartments(String operatorId, String requestId,
                                                        String name, String gender, String mobile,
                                                        String email, String oaDeptId,
                                                        String parentOaId) {
        TenantAccountRequest request = new TenantAccountRequest();
        request.setOperatorId(operatorId);
        request.setRequestId(requestId);
        request.setName(name);
        request.setGender(gender);
        request.setMobile(mobile);
        request.setEmail(email);
        request.setOaDeptId(oaDeptId);
        request.setParentOaId(parentOaId);
        return createInBoundDepartments(request);
    }

    @Override
    public TenantAccountResult createInBoundDepartments(JSONObject params) {
        return createInBoundDepartments(TenantAccountRequest.fromJson(params));
    }

    @Override
    public TenantAccountResult createInBoundDepartments(TenantAccountRequest request) {
        String started = OaonesUtil.now();
        TenantAccountRequest req = normalize(request);
        TenantAccountResult result = new TenantAccountResult(req.getRequestId());
        String invalid = validate(req);
        if (invalid.length() > 0) {
            addGlobalFailure(result, req, null, "INVALID_REQUEST", invalid);
            return result;
        }

        OaonesLog.step("OA Action 第三方租户建号 requestId=" + req.getRequestId()
                + " operatorId=" + req.getOperatorId()
                + " oaDeptId=" + req.getOaDeptId()
                + " parentOaId=" + req.getParentOaId());
        createForPlatform(result, req, WECOM);
        createForPlatform(result, req, DINGTALK);
        createForPlatform(result, req, FEISHU);
        persistResultItems(result, req, started);
        OaonesLog.step("OA Action 第三方租户建号完成 requestId=" + req.getRequestId()
                + " success=" + result.getSuccess().size()
                + " failure=" + result.getFailure().size());
        return result;
    }

    /**
     * 把 OA Action 的租户建号结果也落到统一的同步结果表。
     * 每个“平台 + 租户”生成一条汇总日志，成功/更新/跳过/失败人员各一行明细；
     * 汇总与明细都写同一个 OA 流程 requestid，供独立同步结果页统一查询。
     */
    private void persistResultItems(TenantAccountResult result, TenantAccountRequest req,
                                    String started) {
        JSONArray rows = result.merge();
        Map<String, JSONArray> grouped = new LinkedHashMap<String, JSONArray>();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String platform = text(row.getString("platform"));
            String tenantId = text(row.getString("tenant_id"));
            String key = platform + "|" + (tenantId.length() == 0 ? "0" : tenantId);
            JSONArray group = grouped.get(key);
            if (group == null) {
                group = new JSONArray();
                grouped.put(key, group);
            }
            group.add(row);
        }
        for (Map.Entry<String, JSONArray> entry : grouped.entrySet()) {
            JSONArray group = entry.getValue();
            if (group == null || group.isEmpty()) {
                continue;
            }
            JSONObject first = group.getJSONObject(0);
            String tenantId = text(first.getString("tenant_id"));
            int success = 0;
            int fail = 0;
            int skip = 0;
            JSONArray items = new JSONArray();
            for (int i = 0; i < group.size(); i++) {
                JSONObject row = group.getJSONObject(i);
                String status = text(row.getString("status"));
                int op = "created".equals(status) ? 0 : ("updated".equals(status) ? 1
                        : ("skipped".equals(status) ? 2 : 3));
                if (op == 0 || op == 1) { success++; }
                else if (op == 2) { skip++; }
                else { fail++; }
                JSONObject item = new JSONObject();
                item.put("cfg_id", tenantId);
                item.put("requestid", req.getRequestId());
                item.put("direction", "0");
                item.put("item_type", "1");
                item.put("op", String.valueOf(op));
                item.put("oa_name", text(row.getString("name")));
                item.put("wx_id", text(row.getString("platform_user_id")));
                item.put("wx_name", text(row.getString("name")));
                item.put("parent_id", text(row.getString("platform_dept_id")));
                item.put("parent_name", text(row.getString("platform_dept_id")).length() == 0
                        ? "" : "平台部门 " + text(row.getString("platform_dept_id")));
                item.put("seq", String.valueOf(i + 1));
                String errorCode = text(row.getString("error_code"));
                String error = text(row.getString("error_message"));
                item.put("message", "OA Action 建号 requestId=" + req.getRequestId()
                        + "，结果=" + status
                        + (errorCode.length() == 0 ? "" : "，错误码=" + errorCode)
                        + (error.length() == 0 ? "" : "，" + error));
                items.add(item);
            }
            JSONObject logRow = new JSONObject();
            logRow.put("requestid", req.getRequestId());
            logRow.put("cfg_id", tenantId.length() == 0 ? "0" : tenantId);
            logRow.put("corp_name", text(first.getString("tenant_name")));
            logRow.put("direction", "0");
            logRow.put("total_count", String.valueOf(group.size()));
            logRow.put("success_count", String.valueOf(success));
            logRow.put("fail_count", String.valueOf(fail));
            logRow.put("skip_count", String.valueOf(skip));
            logRow.put("dept_count", "0");
            logRow.put("user_count", String.valueOf(group.size()));
            logRow.put("nodept_count", "0");
            logRow.put("nodept_dept", "");
            logRow.put("start_time", started);
            logRow.put("end_time", OaonesUtil.now());
            logRow.put("run_status", String.valueOf(fail == 0 ? 0 : (success > 0 ? 1 : 2)));
            logRow.put("detail", "OA Action 第三方租户建号逐条结果。requestId="
                    + req.getRequestId());
            logRow.put("create_by", req.getOperatorId());
            try {
                com.customization.oaones.appsyn.service.wecom.SyncLogService logService =
                        new com.customization.oaones.appsyn.service.impl.wecom.SyncLogServiceImpl();
                int logId = logService.add(logRow);
                if (logId > 0) {
                    logService.addItems(logId, items, req.getOperatorId());
                }
            } catch (Throwable t) {
                OaonesLog.error("OA Action 建号结果写同步结果表失败 requestId="
                        + req.getRequestId(), t);
            }
        }
    }

    private TenantAccountRequest normalize(TenantAccountRequest src) {
        TenantAccountRequest req = src == null ? new TenantAccountRequest() : src;
        req.setOperatorId(text(req.getOperatorId()));
        req.setRequestId(text(req.getRequestId()));
        req.setName(text(req.getName()));
        req.setGender(text(req.getGender()));
        req.setMobile(text(req.getMobile()));
        req.setEmail(text(req.getEmail()));
        req.setOaDeptId(text(req.getOaDeptId()));
        req.setParentOaId(text(req.getParentOaId()));
        req.setPlatformUserId(text(req.getPlatformUserId()));
        return req;
    }

    private String validate(TenantAccountRequest req) {
        if (req.getRequestId().length() == 0) {
            return "缺少 OA 流程 requestid";
        }
        if (req.getName().length() == 0) {
            return "缺少姓名";
        }
        if (req.getOaDeptId().length() == 0) {
            return "缺少 OA 部门ID";
        }
        return "";
    }

    private void createForPlatform(TenantAccountResult result, TenantAccountRequest req,
                                   Platform platform) {
        JSONArray tenants;
        try {
            tenants = listTenants(platform);
        } catch (Throwable t) {
            addGlobalFailure(result, req, platform, "TENANT_LIST_FAILED",
                    "读取" + platform.label + "租户失败：" + message(t));
            return;
        }
        if (tenants == null || tenants.isEmpty()) {
            addGlobalFailure(result, req, platform, "NO_TENANT",
                    "未配置" + platform.label + "租户");
            return;
        }
        for (int i = 0; i < tenants.size(); i++) {
            JSONObject tenant = tenants.getJSONObject(i);
            if (tenant == null) {
                continue;
            }
            String cfgId = text(tenant.getString("id"));
            if ("0".equals(text(tenant.getString("sync_enable")))) {
                addFailure(result, req, platform, tenant, "", "", "TENANT_DISABLED",
                        platform.label + "租户已停用，未创建账户", true, new JSONArray());
                continue;
            }
            try {
                createForTenant(result, req, platform, tenant);
            } catch (Throwable t) {
                addFailure(result, req, platform, tenant, "", "", "CREATE_FAILED",
                        message(t), false, new JSONArray());
                OaonesLog.step("OA Action 建号失败 requestId=" + req.getRequestId()
                        + " platform=" + platform.code + " cfgId=" + cfgId
                        + " error=" + message(t));
            }
        }
    }

    private void createForTenant(TenantAccountResult result, TenantAccountRequest req,
                                 Platform platform, JSONObject tenant) throws Exception {
        String cfgId = text(tenant.getString("id"));
        JSONArray bindings = listBindings(platform, cfgId);
        JSONObject binding = findBinding(bindings, req.getOaDeptId());
        if (binding == null) {
            addFailure(result, req, platform, tenant, "", "", "DEPT_UNBOUND",
                    "OA 部门[id=" + req.getOaDeptId() + "]在" + platform.label
                            + "租户下没有有效部门绑定，未创建账户",
                    true, new JSONArray());
            return;
        }
        String platformDeptId = text(binding.getString("wx_dept_id"));
        if (platformDeptId.length() == 0 || "0".equals(platformDeptId)) {
            addFailure(result, req, platform, tenant, platformDeptId, "", "DEPT_BIND_INVALID",
                    "OA 部门绑定的" + platform.label + "部门ID为空，未创建账户",
                    true, new JSONArray());
            return;
        }

        PlatformApi api = createClient(platform, cfgId);
        if (!departmentExists(api, platformDeptId)) {
            addFailure(result, req, platform, tenant, platformDeptId, "", "PLATFORM_DEPT_MISSING",
                    platform.label + "部门[id=" + platformDeptId + "]不存在或应用无权读取，未创建账户",
                    true, new JSONArray());
            return;
        }

        JSONArray warnings = new JSONArray();
        if (req.getGender().length() > 0) {
            if (platform == DINGTALK) {
                warnings.add("钉钉通讯录接口不提供性别字段，性别未写入钉钉");
            } else if (genderCode(req.getGender()) == 0) {
                warnings.add("性别值[" + req.getGender() + "]无法识别，已按未知处理");
            }
        }
        String parentPlatformId = resolveParent(platform, cfgId, req.getParentOaId(),
                api, warnings);
        String accountId = resolvePlatformUserId(req);
        JSONObject existing = null;
        String targetUserId = accountId;
        if (api.userExists(accountId)) {
            existing = api.getUser(accountId);
        } else if (req.getMobile().length() > 0) {
            try {
                JSONObject matched = findByMobile(api.listUsersOfDept(platformDeptId),
                        req.getMobile());
                if (matched != null) {
                    String matchedId = memberId(matched);
                    if (matchedId.length() > 0) {
                        targetUserId = matchedId;
                        existing = matched;
                    }
                }
            } catch (Throwable t) {
                warnings.add("按手机号查找已有成员失败，继续按新成员创建："
                        + message(t));
            }
        }

        JSONObject member = memberBody(platform, targetUserId, req, platformDeptId,
                parentPlatformId);
        String action;
        JSONObject created = null;
        if (existing != null) {
            api.updateUser(member);
            action = "updated";
        } else {
            try {
                created = api.createUser(member);
                action = "created";
            } catch (Throwable createError) {
                // 并发重试时另一个线程可能已建好同一成员：再查一次并更新，避免误报失败。
                if (api.userExists(targetUserId)) {
                    api.updateUser(member);
                    action = "updated";
                } else {
                    throw new RuntimeException(message(createError), createError);
                }
            }
        }
        String actualUserId = memberId(created);
        if (actualUserId.length() == 0) {
            actualUserId = targetUserId;
        }
        if (parentPlatformId.length() > 0) {
            // 创建接口返回的平台ID可能与数据库绑定ID口径不同，写入结果仍保留数据库映射值。
            OaonesLog.step("OA Action 平台直属上级 requestId=" + req.getRequestId()
                    + " platform=" + platform.code + " parent=" + parentPlatformId);
        }
        refreshSnapshot(platform, cfgId, actualUserId, warnings);
        addSuccess(result, req, platform, tenant, platformDeptId, actualUserId,
                parentPlatformId, action, warnings);
    }

    private JSONArray listTenants(Platform platform) {
        if (platform == WECOM) {
            return new com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl().list();
        }
        if (platform == DINGTALK) {
            return new com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl().list();
        }
        return new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl().list();
    }

    private JSONArray listBindings(Platform platform, String cfgId) {
        if (platform == WECOM) {
            return new com.customization.oaones.appsyn.service.impl.wecom.DeptBindServiceImpl()
                    .listEnabled(cfgId);
        }
        if (platform == DINGTALK) {
            return new com.customization.oaones.appsyn.service.impl.dingtalk.DeptBindServiceImpl()
                    .listEnabled(cfgId);
        }
        return new com.customization.oaones.appsyn.service.impl.feishu.DeptBindServiceImpl()
                .listEnabled(cfgId);
    }

    private JSONObject findBinding(JSONArray bindings, String oaDeptId) {
        if (bindings == null) {
            return null;
        }
        for (int i = 0; i < bindings.size(); i++) {
            JSONObject row = bindings.getJSONObject(i);
            if (row != null && oaDeptId.equals(text(row.getString("oa_dept_id")))) {
                return row;
            }
        }
        return null;
    }

    private PlatformApi createClient(final Platform platform, final String cfgId) {
        if (platform == WECOM) {
            final WeComApi api = new com.customization.oaones.appsyn.service.impl.wecom
                    .ConfigServiceImpl().getClient(cfgId);
            return new PlatformApi() {
                public JSONArray listDepartments() throws Exception {
                    return api.listDepartments();
                }
                public boolean userExists(String userId) throws Exception {
                    return api.userExists(userId);
                }
                public JSONObject getUser(String userId) throws Exception {
                    return api.getUser(userId);
                }
                public JSONArray listUsersOfDept(String deptId) throws Exception {
                    return api.listUsers(OaonesUtil.i(deptId, 0), false);
                }
                public JSONObject createUser(JSONObject user) throws Exception {
                    return api.createUser(user);
                }
                public void updateUser(JSONObject user) throws Exception {
                    api.updateUser(user);
                }
            };
        }
        if (platform == DINGTALK) {
            final DingTalkApi api = new com.customization.oaones.appsyn.service.impl.dingtalk
                    .ConfigServiceImpl().getClient(cfgId);
            return new PlatformApi() {
                public JSONArray listDepartments() throws Exception {
                    return api.listDepartments();
                }
                public boolean userExists(String userId) throws Exception {
                    return api.userExists(userId);
                }
                public JSONObject getUser(String userId) throws Exception {
                    return api.getUser(userId);
                }
                public JSONArray listUsersOfDept(String deptId) throws Exception {
                    return api.listUsersOfDept(OaonesUtil.i(deptId, 0));
                }
                public JSONObject createUser(JSONObject user) throws Exception {
                    return api.createUser(user);
                }
                public void updateUser(JSONObject user) throws Exception {
                    api.updateUser(user);
                }
            };
        }
        final com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl config =
                new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl();
        final FeishuApi api = config.getClient(cfgId);
        return new PlatformApi() {
            public JSONArray listDepartments() throws Exception {
                return api.listDepartments();
            }
            public boolean userExists(String userId) throws Exception {
                return api.userExists(userId);
            }
            public JSONObject getUser(String userId) throws Exception {
                return api.getUser(userId);
            }
            public JSONArray listUsersOfDept(String deptId) throws Exception {
                return api.listUsersOfDept(deptId);
            }
            public JSONObject createUser(JSONObject user) throws Exception {
                return api.createUser(user);
            }
            public void updateUser(JSONObject user) throws Exception {
                api.updateUser(user);
            }
        };
    }

    private boolean departmentExists(PlatformApi api, String platformDeptId) throws Exception {
        JSONArray departments = api.listDepartments();
        if (departments == null) {
            return false;
        }
        for (int i = 0; i < departments.size(); i++) {
            JSONObject dept = departments.getJSONObject(i);
            if (dept != null
                    && platformDeptId.equals(text(dept.getString("id")))) {
                return true;
            }
        }
        return false;
    }

    private String resolveParent(Platform platform, String cfgId, String parentOaId,
                                 PlatformApi api, JSONArray warnings) {
        if (parentOaId.length() == 0) {
            return "";
        }
        Integer parentId = OaonesUtil.integer(parentOaId);
        if (parentId == null || parentId <= 0) {
            warnings.add("上级OA人员ID不是有效数字，未设置平台直属上级");
            return "";
        }
        String mapped;
        int cfgKey = OaonesUtil.i(cfgId, 0);
        if (platform == WECOM) {
            mapped = OaonesMyBatis.call(
                    com.customization.oaones.appsyn.dao.wecom.UserBindMapper.class,
                    mapper -> mapper.findActiveWxUserIdByOaUser(
                            OaonesProps.userBindTable(), cfgKey, parentId));
        } else if (platform == DINGTALK) {
            mapped = OaonesMyBatis.call(UserBindMapper.class,
                    mapper -> mapper.findActiveWxUserIdByOaUser(
                            OaonesProps.userBindTable(), cfgKey, parentId));
        } else {
            mapped = OaonesMyBatis.call(
                    com.customization.oaones.appsyn.dao.feishu.UserBindMapper.class,
                    mapper -> mapper.findActiveWxUserIdByOaUser(
                            OaonesProps.userBindTable(), cfgKey, parentId));
        }
        mapped = text(mapped);
        if (mapped.length() == 0) {
            warnings.add("上级OA人员[id=" + parentOaId + "]在该租户下未绑定平台成员，"
                    + "已创建账户但未设置直属上级");
            return "";
        }
        try {
            JSONObject parent = api.getUser(mapped);
            if (parent == null) {
                warnings.add("上级OA人员绑定平台成员[id=" + mapped + "]在"
                        + platform.label + "侧不存在，已创建账户但未设置直属上级");
                return "";
            }
            if (platform == FEISHU) {
                String openId = text(parent.getString("open_id"));
                return openId.length() > 0 ? openId : memberId(parent);
            }
            return mapped;
        } catch (Throwable t) {
            warnings.add("校验上级平台成员失败，已创建账户但未设置直属上级："
                    + message(t));
            return "";
        }
    }

    private JSONObject memberBody(Platform platform, String userId, TenantAccountRequest req,
                                  String platformDeptId, String parentPlatformId) {
        JSONObject user = new JSONObject();
        user.put("userid", userId);
        user.put("name", req.getName());
        if (req.getMobile().length() > 0) {
            user.put("mobile", req.getMobile());
        }
        if (req.getEmail().length() > 0) {
            user.put("email", req.getEmail());
        }
        JSONArray departments = new JSONArray();
        if (platform == FEISHU) {
            departments.add(platformDeptId);
        } else {
            departments.add(OaonesUtil.i(platformDeptId, 0));
        }
        user.put("department", departments);
        int gender = genderCode(req.getGender());
        if (platform == WECOM) {
            user.put("gender", gender);
        } else if (platform == FEISHU) {
            user.put("gender", gender);
        }
        if (parentPlatformId.length() > 0) {
            if (platform == DINGTALK) {
                user.put("manager_userid", parentPlatformId);
            } else if (platform == WECOM) {
                JSONArray leaders = new JSONArray();
                leaders.add(parentPlatformId);
                user.put("direct_leader", leaders);
            } else {
                user.put("leader_user_id", parentPlatformId);
            }
        }
        return user;
    }

    private int genderCode(String gender) {
        String value = text(gender).toLowerCase();
        if ("男".equals(value) || "male".equals(value) || "m".equals(value)
                || "1".equals(value)) {
            return 1;
        }
        if ("女".equals(value) || "female".equals(value) || "f".equals(value)
                || "2".equals(value)) {
            return 2;
        }
        return 0;
    }

    private JSONObject findByMobile(JSONArray users, String mobile) {
        String expected = mobileDigits(mobile);
        if (users == null || expected.length() == 0) {
            return null;
        }
        for (int i = 0; i < users.size(); i++) {
            JSONObject user = users.getJSONObject(i);
            if (user != null && expected.equals(mobileDigits(user.getString("mobile")))) {
                return user;
            }
        }
        return null;
    }

    private String mobileDigits(String value) {
        String source = text(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private String memberId(JSONObject user) {
        if (user == null) {
            return "";
        }
        String userId = text(user.getString("userid"));
        if (userId.length() == 0) {
            userId = text(user.getString("open_id"));
        }
        return userId;
    }

    private String resolvePlatformUserId(TenantAccountRequest req) {
        String explicit = sanitizeUserId(req.getPlatformUserId());
        if (explicit.length() > 0) {
            return explicit;
        }
        String sanitized = sanitizeUserId(req.getRequestId());
        if (sanitized.length() == 0) {
            sanitized = Integer.toHexString(req.getRequestId().hashCode());
        }
        if (sanitized.length() > 62) {
            sanitized = sanitized.substring(0, 62);
        }
        return "oa" + sanitized;
    }

    /** 平台成员ID只保留字母、数字与 _ - . @，最长 64 位。 */
    private String sanitizeUserId(String value) {
        String source = text(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length() && out.length() < 64; i++) {
            char c = source.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '@';
            if (keep) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void refreshSnapshot(Platform platform, String cfgId, String userId,
                                 JSONArray warnings) {
        try {
            if (platform == WECOM) {
                new com.customization.oaones.appsyn.service.impl.wecom.UserTmpServiceImpl()
                        .refreshMember(cfgId, userId);
            } else if (platform == DINGTALK) {
                new com.customization.oaones.appsyn.service.impl.dingtalk.UserTmpServiceImpl()
                        .refreshMember(cfgId, userId);
            } else {
                new com.customization.oaones.appsyn.service.impl.feishu.UserTmpServiceImpl()
                        .refreshMember(cfgId, userId);
            }
        } catch (Throwable t) {
            warnings.add("账户已写入平台，但人员快照刷新失败：" + message(t));
        }
    }

    private void addSuccess(TenantAccountResult result, TenantAccountRequest req,
                            Platform platform, JSONObject tenant, String platformDeptId,
                            String accountUserId, String parentPlatformUserId,
                            String action, JSONArray warnings) {
        JSONObject row = contextRow(req, platform, tenant);
        row.put("status", action);
        row.put("created", "created".equals(action));
        row.put("updated", "updated".equals(action));
        row.put("oa_dept_id", req.getOaDeptId());
        row.put("platform_dept_id", platformDeptId);
        row.put("platform_user_id", accountUserId);
        row.put("parent_oa_id", req.getParentOaId());
        row.put("parent_platform_id", parentPlatformUserId);
        row.put("parent_linked", parentPlatformUserId.length() > 0);
        row.put("name", req.getName());
        row.put("gender", req.getGender());
        row.put("mobile", req.getMobile());
        row.put("email", req.getEmail());
        row.put("warnings", warnings == null ? new JSONArray() : warnings);
        result.addSuccess(row);
    }

    private void addFailure(TenantAccountResult result, TenantAccountRequest req,
                            Platform platform, JSONObject tenant, String platformDeptId,
                            String accountUserId, String errorCode, String errorMessage,
                            boolean skipped, JSONArray warnings) {
        JSONObject row = contextRow(req, platform, tenant);
        row.put("status", skipped ? "skipped" : "failed");
        row.put("created", false);
        row.put("updated", false);
        row.put("oa_dept_id", req.getOaDeptId());
        row.put("platform_dept_id", text(platformDeptId));
        row.put("platform_user_id", text(accountUserId));
        row.put("parent_oa_id", req.getParentOaId());
        row.put("parent_platform_id", "");
        row.put("parent_linked", false);
        row.put("name", req.getName());
        row.put("gender", req.getGender());
        row.put("mobile", req.getMobile());
        row.put("email", req.getEmail());
        row.put("error_code", errorCode);
        row.put("error_message", errorMessage);
        row.put("warnings", warnings == null ? new JSONArray() : warnings);
        result.addFailure(row);
    }

    private void addGlobalFailure(TenantAccountResult result, TenantAccountRequest req,
                                  Platform platform, String errorCode, String errorMessage) {
        addFailure(result, req, platform, new JSONObject(), "", "", errorCode,
                errorMessage, false, new JSONArray());
    }

    private JSONObject contextRow(TenantAccountRequest req, Platform platform,
                                  JSONObject tenant) {
        JSONObject row = new JSONObject();
        row.put("request_id", req.getRequestId());
        row.put("operator_id", req.getOperatorId());
        row.put("platform", platform == null ? "" : platform.code);
        row.put("platform_name", platform == null ? "" : platform.label);
        row.put("tenant_id", tenant == null ? "" : text(tenant.getString("id")));
        row.put("tenant_name", tenant == null ? "" : text(tenant.getString("corp_name")));
        row.put("corp_id", tenant == null ? "" : text(tenant.getString("corp_id")));
        return row;
    }

    private String text(String value) {
        return OaonesUtil.s(value).trim();
    }

    private String message(Throwable t) {
        Throwable current = t;
        String result = "";
        while (current != null) {
            String msg = text(current.getMessage());
            if (msg.length() > 0) {
                result = msg;
            }
            current = current.getCause();
        }
        return result.length() == 0 ? t.getClass().getSimpleName() : result;
    }

    private interface PlatformApi {
        JSONArray listDepartments() throws Exception;
        boolean userExists(String userId) throws Exception;
        JSONObject getUser(String userId) throws Exception;
        JSONArray listUsersOfDept(String deptId) throws Exception;
        JSONObject createUser(JSONObject user) throws Exception;
        void updateUser(JSONObject user) throws Exception;
    }

    private static final class Platform {
        private final String code;
        private final String label;

        private Platform(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }
}
