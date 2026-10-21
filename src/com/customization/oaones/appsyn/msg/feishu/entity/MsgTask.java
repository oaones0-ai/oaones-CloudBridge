package com.customization.oaones.appsyn.msg.feishu.entity;

/**
 * 卡片任务（表名取 oaones.properties#oaones_msg_task_table）。
 *
 * <p>待办卡片推送时登记 {@code taskId}（业务+接收人唯一），
 * 流程办结/退回/收回等标准消息到来时按 taskId 调用飞书 update_taskcard 更新卡片状态。</p>
 */
public class MsgTask {

    private Long id;
    private String taskId;
    private String bizType;
    private String bizId;
    private String bizTitle;
    private Integer appId;
    private String corpId;
    private String agentId;
    private Integer oaUserId;
    private String wxUserid;
    /** pending / done / rejected / invalid。 */
    private String taskState;
    private Long logId;
    private String expireTime;
    /** 登记日期（建模系统标准字段）：yyyy-MM-dd。 */
    private String modedatacreatedate;
    /** 登记时间（建模系统标准字段）：HH:mm:ss。 */
    private String modedatacreatetime;
    /** 最近更新时间（建模系统标准字段）：yyyy-MM-dd HH:mm:ss，卡片状态更新后回写。 */
    private String modedatamodifydatetime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }

    public String getBizTitle() { return bizTitle; }
    public void setBizTitle(String bizTitle) { this.bizTitle = bizTitle; }

    public Integer getAppId() { return appId; }
    public void setAppId(Integer appId) { this.appId = appId; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getWxUserid() { return wxUserid; }
    public void setWxUserid(String wxUserid) { this.wxUserid = wxUserid; }

    public String getTaskState() { return taskState; }
    public void setTaskState(String taskState) { this.taskState = taskState; }

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public String getExpireTime() { return expireTime; }
    public void setExpireTime(String expireTime) { this.expireTime = expireTime; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) {
        this.modedatacreatedate = modedatacreatedate;
    }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) {
        this.modedatacreatetime = modedatacreatetime;
    }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
