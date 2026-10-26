package com.customization.oaones.appsyn.dto;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;

/**
 * OA 流程创建第三方租户账户的入参。
 *
 * <p>平台成员ID默认按 {@code oa + 流程requestid} 生成，保证同一流程重复执行时更新同一成员，
 * 不会因按钮重试或 Action 重跑而重复建号。如业务必须覆盖该ID，可通过
 * {@link #setPlatformUserId(String)} 单独指定。</p>
 */
public class TenantAccountRequest {

    /** 操作人 OA 人员ID。 */
    private String operatorId;
    /** OA 流程 requestid；同时用于生成幂等的平台成员ID。 */
    private String requestId;
    private String name;
    /** 性别支持“男/女”、male/female、1/2 等常见口径。 */
    private String gender;
    private String mobile;
    private String email;
    /** OA 部门ID，按该ID与各租户的有效部门绑定关系决定是否建号。 */
    private String oaDeptId;
    /** 上级 OA 人员ID；在该租户存在人员绑定时同步为平台直属上级。 */
    private String parentOaId;
    /** 可选：显式指定平台成员ID；为空时使用 oa + requestId。 */
    private String platformUserId;

    public static TenantAccountRequest fromJson(JSONObject params) {
        TenantAccountRequest request = new TenantAccountRequest();
        if (params == null) {
            return request;
        }
        request.setOperatorId(first(params, "operator_id", "operatorId", "operator", "操作人"));
        request.setRequestId(first(params, "request_id", "requestId", "requestid", "流程requestid"));
        request.setName(first(params, "name", "lastname", "姓名"));
        request.setGender(first(params, "gender", "sex", "性别"));
        request.setMobile(first(params, "mobile", "手机号"));
        request.setEmail(first(params, "email", "邮箱"));
        request.setOaDeptId(first(params, "oa_dept_id", "oaDeptId", "department_id",
                "departmentid", "OA中部门id"));
        request.setParentOaId(first(params, "parent_oa_id", "parentOaId",
                "上级oa的id", "上级OA的id"));
        request.setPlatformUserId(first(params, "platform_user_id", "platformUserId"));
        return request;
    }

    private static String first(JSONObject params, String... keys) {
        for (String key : keys) {
            if (params.containsKey(key)) {
                String value = OaonesUtil.s(params.getString(key)).trim();
                if (value.length() > 0) {
                    return value;
                }
            }
        }
        return "";
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOaDeptId() {
        return oaDeptId;
    }

    public void setOaDeptId(String oaDeptId) {
        this.oaDeptId = oaDeptId;
    }

    public String getParentOaId() {
        return parentOaId;
    }

    public void setParentOaId(String parentOaId) {
        this.parentOaId = parentOaId;
    }

    public String getPlatformUserId() {
        return platformUserId;
    }

    public void setPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
    }
}
