package com.customization.oaones.appsyn.service.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesRules;

/**
 * 默认人员绑定规则配置（业务表名取 oaones.appsyn.properties#oaones_user_rule_table）。
 *
 * <p>cfg_id 为空/0 表示“默认配置”；非空表示某个企微租户的独立配置。
 * “绑定详情 → 默认批量绑定”优先取当前租户独立配置，租户未配置时自动回退默认配置。</p>
 */
public interface UserRuleService {

    /**
     * 规则记录列表（默认配置 + 各租户独立配置），用于“默认绑定规则 → 规则列表”页。
     *
     * <p>租户分权：系统管理员返回全部；维护人/普通用户只返回默认配置
     * 与本人为维护人的租户独立配置。</p>
     */
    JSONArray list(String operatorId);

    /** 获取某个租户实际生效的规则，两者都未配置时返回 null。 */
    JSONObject getEffective(String cfgId);

    /** 保存规则：cfg_id 为空/0 保存默认配置，否则保存/新建该租户独立配置。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 删除某租户独立配置，使其重新跟随默认配置。 */
    void delete(String cfgId);

    /** 按固定优先级清洗并去重规则字段。 */
    static String normalizeRules(String value) {
        return OaonesRules.normalize(value);
    }
}
