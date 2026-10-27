package com.customization.oaones.appsyn.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「会议 / 日程同步」从第三方平台**拉取**（第三方→OA）时传给平台适配器的上下文。
 *
 * <p>适配器只认这一份结构，不直接读库：租户凭据、要查询的日历（平台成员ID）与时间窗都在这里。</p>
 */
public class MtgPullContext {

    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private int type;
    /** 租户配置ID（uf_oaones_appsyn_qywx_config.id）。 */
    private int cfgId;
    /** 企业标识：企微 corpid / 钉钉 AppKey / 飞书 App ID。 */
    private String corpId;
    /** 应用密钥：企微通讯录 Secret / 钉钉 AppSecret / 飞书 App Secret。 */
    private String corpSecret;
    /** 应用ID：企微 AgentId / 钉钉 AgentId / 飞书 App ID。 */
    private String agentId;
    /** 要查询的平台日历（平台成员ID：企微 userid / 钉钉 userid / 飞书 open_id 或 user id）。 */
    private List<String> calendarIds = new ArrayList<String>();
    /** 时间窗开始 yyyy-MM-dd HH:mm:ss。 */
    private String fromTime;
    /** 时间窗结束 yyyy-MM-dd HH:mm:ss。 */
    private String toTime;
    /** 一个租户一次最多返回多少条（适配器可据此截断）。 */
    private int limit;
    /**
     * 本轮**确实取到了结果**的日历ID（适配器在成功读到某个日历后放进来，含飞书固定查询的 {@code primary}）。
     *
     * <p>删除对账用：只有“这个日历本轮取过、而对象不在结果里”才敢判断平台侧已删除；
     * 没取过的日历（缺少成员绑定、接口报错被跳过）一律不推断，避免误删 OA 数据。</p>
     */
    private Set<String> listedCalendars = new LinkedHashSet<String>();

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

    public List<String> getCalendarIds() { return calendarIds; }
    public void setCalendarIds(List<String> calendarIds) {
        this.calendarIds = calendarIds == null ? new ArrayList<String>() : calendarIds;
    }

    public String getFromTime() { return fromTime; }
    public void setFromTime(String fromTime) { this.fromTime = fromTime; }

    public String getToTime() { return toTime; }
    public void setToTime(String toTime) { this.toTime = toTime; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    public Set<String> getListedCalendars() { return listedCalendars; }

    public void setListedCalendars(Set<String> listedCalendars) {
        this.listedCalendars = listedCalendars == null ? new LinkedHashSet<String>() : listedCalendars;
    }

    /** 记一个“本轮确实取到结果”的日历ID（适配器内部用）。 */
    public void markListed(String calendarId) {
        if (calendarId != null && calendarId.trim().length() > 0) {
            listedCalendars.add(calendarId.trim());
        }
    }
}
