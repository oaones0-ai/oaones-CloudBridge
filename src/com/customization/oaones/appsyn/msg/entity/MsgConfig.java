package com.customization.oaones.appsyn.msg.entity;

/**
 * 消息中心系统参数（表名取 oaones.properties#oaones_msg_config_table，单行 id=1）。
 */
public class MsgConfig {

    private Integer id;
    /** 推送总开关：1 开 / 0 关。 */
    private Integer pushEnable;
    private Integer defaultAppId;
    private Integer retryTimes;
    private Integer retryInterval;
    private Integer shardSize;
    private Integer timeoutMs;
    private Integer tokenAdvanceSec;
    private Integer dedupWindowSec;
    private Integer logKeepDays;
    private Integer taskKeepDays;
    private String remark;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPushEnable() { return pushEnable; }
    public void setPushEnable(Integer pushEnable) { this.pushEnable = pushEnable; }

    public Integer getDefaultAppId() { return defaultAppId; }
    public void setDefaultAppId(Integer defaultAppId) { this.defaultAppId = defaultAppId; }

    public Integer getRetryTimes() { return retryTimes; }
    public void setRetryTimes(Integer retryTimes) { this.retryTimes = retryTimes; }

    public Integer getRetryInterval() { return retryInterval; }
    public void setRetryInterval(Integer retryInterval) { this.retryInterval = retryInterval; }

    public Integer getShardSize() { return shardSize; }
    public void setShardSize(Integer shardSize) { this.shardSize = shardSize; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public Integer getTokenAdvanceSec() { return tokenAdvanceSec; }
    public void setTokenAdvanceSec(Integer tokenAdvanceSec) { this.tokenAdvanceSec = tokenAdvanceSec; }

    public Integer getDedupWindowSec() { return dedupWindowSec; }
    public void setDedupWindowSec(Integer dedupWindowSec) { this.dedupWindowSec = dedupWindowSec; }

    public Integer getLogKeepDays() { return logKeepDays; }
    public void setLogKeepDays(Integer logKeepDays) { this.logKeepDays = logKeepDays; }

    public Integer getTaskKeepDays() { return taskKeepDays; }
    public void setTaskKeepDays(Integer taskKeepDays) { this.taskKeepDays = taskKeepDays; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
