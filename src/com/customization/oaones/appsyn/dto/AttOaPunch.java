package com.customization.oaones.appsyn.dto;

/**
 * OA 侧一条打卡记录（反向同步「OA → 第三方」的数据来源，读 E9 标准考勤打卡表）。
 *
 * <p>默认只取 OA 侧真实产生的打卡，不含本功能从第三方导入回来的记录
 * （按来源标识排除 + 映射表二次排除），避免来回搬运（需求 3.9.1 / Q9）。</p>
 */
public class AttOaPunch {

    private String signId;
    private Integer oaUserId;
    private String signDate;
    private String signTime;
    private String signDatetime;
    private Integer signType;
    private String signFrom;
    private boolean remedy;

    public String getSignId() { return signId; }
    public void setSignId(String signId) { this.signId = signId; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getSignDate() { return signDate; }
    public void setSignDate(String signDate) { this.signDate = signDate; }

    public String getSignTime() { return signTime; }
    public void setSignTime(String signTime) { this.signTime = signTime; }

    public String getSignDatetime() { return signDatetime; }
    public void setSignDatetime(String signDatetime) { this.signDatetime = signDatetime; }

    public Integer getSignType() { return signType; }
    public void setSignType(Integer signType) { this.signType = signType; }

    public String getSignFrom() { return signFrom; }
    public void setSignFrom(String signFrom) { this.signFrom = signFrom; }

    public boolean isRemedy() { return remedy; }
    public void setRemedy(boolean remedy) { this.remedy = remedy; }
}
