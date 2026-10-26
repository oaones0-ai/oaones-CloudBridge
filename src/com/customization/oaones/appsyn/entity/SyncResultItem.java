package com.customization.oaones.appsyn.entity;

/**
 * 同步结果页的数据行：一条部门、分部或人员同步动作。
 *
 * <p>查询时关联同步批次表与租户配置表，带出 requestid、平台类型、租户名称和批次起止时间。</p>
 */
public class SyncResultItem {

    private Integer id;
    private Integer logId;
    private Integer requestId;
    private Integer cfgId;
    private Integer platformType;
    private String corpName;
    private Integer direction;
    private Integer itemType;
    private Integer op;
    private String oaId;
    private String oaName;
    private String wxId;
    private String wxName;
    private String parentId;
    private String parentName;
    private Integer seq;
    private String message;
    private String startTime;
    private String endTime;
    private String createDate;
    private String createTimeValue;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getLogId() { return logId; }
    public void setLogId(Integer logId) { this.logId = logId; }

    public Integer getRequestId() { return requestId; }
    public void setRequestId(Integer requestId) { this.requestId = requestId; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public Integer getPlatformType() { return platformType; }
    public void setPlatformType(Integer platformType) { this.platformType = platformType; }

    public String getCorpName() { return corpName; }
    public void setCorpName(String corpName) { this.corpName = corpName; }

    public Integer getDirection() { return direction; }
    public void setDirection(Integer direction) { this.direction = direction; }

    public Integer getItemType() { return itemType; }
    public void setItemType(Integer itemType) { this.itemType = itemType; }

    public Integer getOp() { return op; }
    public void setOp(Integer op) { this.op = op; }

    public String getOaId() { return oaId; }
    public void setOaId(String oaId) { this.oaId = oaId; }

    public String getOaName() { return oaName; }
    public void setOaName(String oaName) { this.oaName = oaName; }

    public String getWxId() { return wxId; }
    public void setWxId(String wxId) { this.wxId = wxId; }

    public String getWxName() { return wxName; }
    public void setWxName(String wxName) { this.wxName = wxName; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getCreateDate() { return createDate; }
    public void setCreateDate(String createDate) { this.createDate = createDate; }

    public String getCreateTimeValue() { return createTimeValue; }
    public void setCreateTimeValue(String createTimeValue) { this.createTimeValue = createTimeValue; }
}
