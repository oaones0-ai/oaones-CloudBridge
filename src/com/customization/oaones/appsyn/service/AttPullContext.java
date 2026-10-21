package com.customization.oaones.appsyn.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 考勤同步「第三方 → OA」拉取时传给平台适配器的上下文。
 *
 * <p>适配器只认这一份结构，不直接读库：租户凭据、要查询的平台成员（已按人员绑定换算好的平台账号）、
 * 查询日期区间与单次上限都在这里。</p>
 *
 * <p>时间口径（需求 3.5.4 跨天班次）：平台接口多数按“考勤日期 / 排班日期”查询，与“打卡时间所在自然日”
 * 可能不一致，所以调用平台接口时按查询日期区间取数（必要时向前多取一天），再按打卡时间过滤；
 * 落库与去重以打卡时间为准，同时记录平台给出的考勤日期供核对。</p>
 */
public class AttPullContext {

    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private int type;
    /** 租户配置ID（uf_oaones_qywx_config.id）。 */
    private int cfgId;
    /** 企业标识：企微 corpid / 钉钉 AppKey / 飞书 App ID。 */
    private String corpId;
    /** 应用密钥：考勤应用的 Secret（没有单独配时由租户 Secret 兜底）。 */
    private String corpSecret;
    /** 应用ID：企微 AgentId / 钉钉 AgentId / 飞书 App ID。 */
    private String agentId;
    /** 要查询的平台成员（企微 userid / 钉钉 userid / 飞书 open_id 或 user_id）。 */
    private List<String> userIds = new ArrayList<String>();
    /** 查询起始日期（yyyy-MM-dd，按平台的考勤日期维度）。 */
    private String fromDate;
    /** 查询截止日期（yyyy-MM-dd）。 */
    private String toDate;
    /** 一次最多返回多少条（适配器可据此截断；分页由适配器自己取全）。 */
    private int limit;
    /** 是否只读补卡（补签卡）数据：各平台的补卡识别方式不同，适配器里做兼容读取。 */
    private boolean remedyOnly;

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public int getCfgId() { return cfgId; }
    public void setCfgId(int cfgId) { this.cfgId = cfgId; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getCorpSecret() { return corpSecret; }
    public void setCorpSecret(String corpSecret) { this.corpSecret = corpSecret; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public List<String> getUserIds() { return userIds; }
    public void setUserIds(List<String> userIds) {
        this.userIds = userIds == null ? new ArrayList<String>() : userIds;
    }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    public boolean isRemedyOnly() { return remedyOnly; }
    public void setRemedyOnly(boolean remedyOnly) { this.remedyOnly = remedyOnly; }
}
