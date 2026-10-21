package com.customization.oaones.appsyn.dto;

/**
 * 平台侧一条打卡记录的归一化结构（三个平台的适配器都返回它，业务层不感知平台字段差异）。
 *
 * <p>字段口径（需求 3.4 / 3.5）：{@code wxUserId} 是平台成员ID（企微 userid / 钉钉 userid /
 * 飞书 open_id 或 user_id），由同步引擎按人员绑定换算成 OA 人员；{@code signTime} 是打卡发生时间
 * （已统一换算到 Asia/Shanghai，格式 {@code yyyy-MM-dd HH:mm:ss}），落库与去重都以它为准，
 * {@code signDate} 是平台给出的考勤日期（跨天班次可能与打卡时间不同日，只作核对用）；
 * {@code signType} 归一化成 1 签到（上班卡）/ 2 签退（下班卡）；
 * {@code dataType} 为 1 正常打卡 / 2 补签卡（补卡）。</p>
 *
 * <p>{@code rawType} 保留平台原始类型（正常 / 外勤 / 出差 / 补卡 / 平台类型未知）——
 * 识别不出是不是补卡时按正常打卡写入，并在日志里标注“平台类型未知”（需求 Q5）；
 * {@code wxRecordId} 是平台侧记录标识（打卡记录ID / 补卡单号），有则作为去重的补充判据。</p>
 */
public class AttPunch {

    private String wxUserId;
    private String signTime;
    private String signDate;
    private Integer signType;
    private Integer dataType;
    private String rawType;
    private String wxRecordId;
    private String rawSummary;

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public String getSignTime() { return signTime; }
    public void setSignTime(String signTime) { this.signTime = signTime; }

    public String getSignDate() { return signDate; }
    public void setSignDate(String signDate) { this.signDate = signDate; }

    public Integer getSignType() { return signType; }
    public void setSignType(Integer signType) { this.signType = signType; }

    public Integer getDataType() { return dataType; }
    public void setDataType(Integer dataType) { this.dataType = dataType; }

    public String getRawType() { return rawType; }
    public void setRawType(String rawType) { this.rawType = rawType; }

    public String getWxRecordId() { return wxRecordId; }
    public void setWxRecordId(String wxRecordId) { this.wxRecordId = wxRecordId; }

    public String getRawSummary() { return rawSummary; }
    public void setRawSummary(String rawSummary) { this.rawSummary = rawSummary; }
}
