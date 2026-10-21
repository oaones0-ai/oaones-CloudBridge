package com.customization.oaones.appsyn.service;

import com.customization.oaones.appsyn.dto.AttOaPunch;

/**
 * 考勤同步「OA → 第三方」推送时传给平台适配器的上下文。
 *
 * <p>适配器只认这一份结构：租户凭据、OA 侧那条打卡（人员 + 打卡时间 + 类型 + 是否补卡）、
 * 平台账号（由同步引擎按人员绑定换算好）与写入方式（mode：direct 直接写入 / card 走补卡审批 /
 * none 不支持）。</p>
 */
public class AttPushContext {

    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private int type;
    /** 租户配置ID。 */
    private int cfgId;
    private String corpId;
    private String corpSecret;
    private String agentId;
    /** OA 侧打卡记录。 */
    private AttOaPunch punch;
    /** 该人员的平台账号（未绑定时为空，引擎在调用前已跳过）。 */
    private String wxUserId;
    /** 写入方式：direct / card / none（OaonesConst.ATT_PUSH_MODE_*）。 */
    private String mode;
    /** OA 人员姓名（用于补卡申请正文，便于平台侧核对）。 */
    private String oaUserName;

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

    public AttOaPunch getPunch() { return punch; }
    public void setPunch(AttOaPunch punch) { this.punch = punch; }

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getOaUserName() { return oaUserName; }
    public void setOaUserName(String oaUserName) { this.oaUserName = oaUserName; }
}
