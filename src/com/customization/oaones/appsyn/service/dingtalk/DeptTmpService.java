package com.customization.oaones.appsyn.service.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 部门快照表维护（业务表名取 oaones.properties#oaones_dep_tmp_table）。
 *
 * <p>租户连通测试成功后或定时任务执行时，把平台部门列表写入快照；
 * AppKey + 平台部门ID 唯一。</p>
 */
public interface DeptTmpService {

    /** 刷新指定租户的钉钉部门快照。 */
    JSONObject refreshByCfgId(String cfgId) throws Exception;

    /** 刷新全部启用租户的快照（单个租户失败不影响其他租户）。 */
    JSONObject refreshAll();

    /** 拉取成功后把当前部门 upsert 到快照表。 */
    JSONObject refresh(JSONObject cfg, JSONArray depts) throws Exception;

    /** 返回快照中状态正常的部门，供部门绑定页面使用。 */
    JSONArray listNormal(String cfgId) throws Exception;

    /** 将钉钉部门标记为免绑定。 */
    JSONObject markNoBind(String cfgId, String wxDeptId);

    /** 取消钉钉部门免绑定。 */
    JSONObject cancelNoBind(String cfgId, String wxDeptId);

    /** 批量将钉钉部门标记为免绑定。 */
    JSONObject batchNoBind(String cfgId, String wxDeptIds);

    /** 校验钉钉部门是否存在于快照且状态正常。 */
    boolean isNormal(String corpId, String wxDeptId) throws Exception;

    /** 快照状态文本。 */
    String statusText(String status);
}
