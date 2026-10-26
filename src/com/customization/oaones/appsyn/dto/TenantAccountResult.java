package com.customization.oaones.appsyn.dto;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;

/**
 * 第三方租户账户创建结果。
 *
 * <p>成功与失败分别保存在两个 JSON 数组中。未绑定部门、租户停用等“跳过”记录
 * 同样放入 {@link #getFailure()}，通过 {@code status/error_code} 区分。</p>
 */
public class TenantAccountResult {

    private final String requestId;
    private final JSONArray success = new JSONArray();
    private final JSONArray failure = new JSONArray();

    public TenantAccountResult(String requestId) {
        this.requestId = OaonesUtil.s(requestId);
    }

    public void addSuccess(JSONObject row) {
        success.add(row);
    }

    public void addFailure(JSONObject row) {
        failure.add(row);
    }

    public JSONArray getSuccess() {
        return success;
    }

    public JSONArray getFailure() {
        return failure;
    }

    /** 直接返回给 OA Action 使用的两个数组及汇总数量。 */
    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.put("request_id", requestId);
        result.put("success", success);
        result.put("failure", failure);
        result.put("success_total", success.size());
        result.put("failure_total", failure.size());
        return result;
    }

    public String toJsonString() {
        return toJson().toJSONString();
    }

    /** 拼接成功与失败数组，成功在前、失败在后。 */
    public JSONArray merge() {
        JSONArray all = new JSONArray();
        all.addAll(success);
        all.addAll(failure);
        return all;
    }

    /** 只取成功数据。 */
    public JSONArray onlySuccess() {
        return success;
    }

    /** 只取失败/跳过数据。 */
    public JSONArray onlyFailure() {
        return failure;
    }

    public String mergeJsonString() {
        return merge().toJSONString();
    }

    public String successJsonString() {
        return onlySuccess().toJSONString();
    }

    public String failureJsonString() {
        return onlyFailure().toJSONString();
    }
}
