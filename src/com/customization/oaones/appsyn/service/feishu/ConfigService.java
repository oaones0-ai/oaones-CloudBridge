package com.customization.oaones.appsyn.service.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.feishu.FeishuApi;

/**
 * 多飞书租户配置维护（业务表名取 oaones.properties#oaones_qywx_config_table）。
 */
public interface ConfigService {

    /** 全部租户配置（内部/计划任务使用，不做维护人过滤）。 */
    JSONArray list();

    /**
     * 当前登录人可查看/维护的租户配置，并附带 can_maintain 标记。
     *
     * <p>仅被指定为该租户维护人的用户可查看；未指定维护人的租户仅系统管理员
     * （user.id = 1）可查看/维护。</p>
     */
    JSONArray listForUser(String operatorId);

    /** 同 {@link #listForUser(String)}：仅返回当前登录人可维护的租户配置。 */
    JSONArray listMaintainable(String operatorId);

    /**
     * 当前登录人是否可查看/维护该租户。
     *
     * <p>仅维护人本人可查看与设置；系统管理员（user.id = 1）可查看与维护全部租户；
     * 未指定维护人的租户除系统管理员外任何人都不可查看/设置。</p>
     */
    boolean canMaintain(String cfgId, String operatorId);

    /** 单个租户配置，不存在返回 null。 */
    JSONObject get(String cfgId);

    /** 根据配置记录构造飞书接口客户端。 */
    FeishuApi getClient(String cfgId);

    /** 新增或更新租户配置。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 删除租户配置（存在绑定关系时拒绝）。 */
    void delete(String cfgId);

    /** 校验飞书连通性：取 token，拉取部门与成员并刷新对应快照。 */
    JSONObject test(String cfgId) throws Exception;
}
