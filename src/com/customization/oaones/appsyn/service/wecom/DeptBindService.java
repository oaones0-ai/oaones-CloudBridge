package com.customization.oaones.appsyn.service.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Set;

/**
 * OA部门 - 企业微信部门 绑定关系维护。
 *
 * <p>同步约束的核心：只有建立了部门绑定的 OA 部门才允许写入企业微信部门树；
 * 企业微信侧没有对应绑定记录时，模块绝不自动创建部门。</p>
 */
public interface DeptBindService {

    /** 部门绑定列表（可按租户/关键字过滤）。 */
    JSONArray list(String cfgId, String keyword);

    /** 仅返回启用中的绑定（供同步引擎使用）。 */
    JSONArray listEnabled(String cfgId);

    /** 已绑定 OA 部门 ID 集合（用于计算“未绑定 OA 部门”）。 */
    Set<String> boundOaDeptIds(String cfgId);

    /** 建立/更新部门绑定。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 批量绑定部门。 */
    JSONObject batchSave(JSONObject params, String operatorId);

    /** 删除部门绑定。 */
    void delete(String bindId);

    /**
     * 自动绑定（按部门编号/名称匹配的结果）批量绑定部门。
     *
     * <p>自动绑定与手动批量绑定使用同一套校验规则。</p>
     */
    JSONObject autoBatchSave(JSONObject params, String operatorId);
}
