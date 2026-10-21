package com.customization.oaones.appsyn.service;

import com.customization.oaones.appsyn.dto.MtgOaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 「会议 / 日程同步」推送到第三方平台时传给平台适配器的上下文。
 *
 * <p>适配器只认这一份结构，不直接读库：租户凭据、OA 侧对象、换算好的平台成员ID
 * （由同步引擎按 {@code uf_oaones_user_bind} 把 OA 人员ID 换成平台 userid）都在这里。</p>
 */
public class MtgPushContext {

    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private int type;
    /** 租户配置ID（uf_oaones_qywx_config.id）。 */
    private int cfgId;
    /** 企业标识：企微 corpid / 钉钉 AppKey / 飞书 App ID。 */
    private String corpId;
    /** 应用密钥：企微通讯录 Secret / 钉钉 AppSecret / 飞书 App Secret。 */
    private String corpSecret;
    /** 应用ID：企微 AgentId / 钉钉 AgentId / 飞书 App ID。 */
    private String agentId;
    /** OA 侧对象（会议 / 日程）。 */
    private MtgOaItem item;
    /** 组织者平台成员ID（OA 创建人按人员绑定换出来的；为空时用参与人里的第一个）。 */
    private String ownerUserId;
    /** 参与人平台成员ID 列表（已按人员绑定换算，未绑定的已剔除）。 */
    private List<String> attendeeUserIds = new ArrayList<String>();
    /** 是否更新（true = 该 OA 对象此前已同步过，走平台“更新日程”接口）。 */
    private boolean update;
    /** 更新时平台侧已存在的对象ID。 */
    private String existWxId;

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

    public MtgOaItem getItem() { return item; }
    public void setItem(MtgOaItem item) { this.item = item; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public List<String> getAttendeeUserIds() { return attendeeUserIds; }
    public void setAttendeeUserIds(List<String> attendeeUserIds) {
        this.attendeeUserIds = attendeeUserIds == null ? new ArrayList<String>() : attendeeUserIds;
    }

    public boolean isUpdate() { return update; }
    public void setUpdate(boolean update) { this.update = update; }

    public String getExistWxId() { return existWxId; }
    public void setExistWxId(String existWxId) { this.existWxId = existWxId; }
}
