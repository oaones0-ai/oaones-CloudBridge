package com.customization.oaones.appsyn.entity;

/**
 * E9 标准考勤「固定排班」（表 {@code kq_fixedschedulce}）的只读投影：考勤组 + 星期几 → 班次。
 *
 * <p>{@code weekday} 口径：<b>0 = 周一 … 6 = 周日</b>（与该表数据一致：0~4 排上班班次、
 * 5 / 6 为空或 -1）。{@code serialid} = 该天排的班次ID：{@code -1} 或空 = 休息日。</p>
 */
public class KqFixedSchedule {

    /** 星期几：0 = 周一 … 6 = 周日。 */
    private Integer weekday;
    /** 班次ID（{@code kq_ShiftManagement.id}）；-1 或空 = 休息日。 */
    private Integer serialid;

    public Integer getWeekday() { return weekday; }
    public void setWeekday(Integer weekday) { this.weekday = weekday; }

    public Integer getSerialid() { return serialid; }
    public void setSerialid(Integer serialid) { this.serialid = serialid; }
}
