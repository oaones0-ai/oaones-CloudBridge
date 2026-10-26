package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.dto.TenantAccountRequest;
import com.customization.oaones.appsyn.dto.TenantAccountResult;

/**
 * 按 OA 部门绑定关系，在全部启用的第三方租户中创建/更新成员账号。
 *
 * <p>OA Action 可直接调用本接口，结果通过
 * {@link TenantAccountResult#toJsonString()} 写入流程字段或 Action 日志。</p>
 */
public interface TenantAccountService {

    /**
     * @param operatorId 操作人 OA 人员ID
     * @param requestId OA 流程 requestid
     * @param name 姓名
     * @param gender 性别
     * @param mobile 手机号
     * @param email 邮箱
     * @param oaDeptId OA 部门ID
     * @param parentOaId 上级 OA 人员ID
     */
    TenantAccountResult createInBoundDepartments(String operatorId, String requestId, String name,
                                                 String gender, String mobile, String email,
                                                 String oaDeptId, String parentOaId);

    /** 支持 JSON 入参及可选 platform_user_id。 */
    TenantAccountResult createInBoundDepartments(JSONObject params);

    TenantAccountResult createInBoundDepartments(TenantAccountRequest request);
}
