package com.customization.oaones.appsyn.entity;

/**
 * E9 标准考勤「节假日设置」（表 {@code kq_HolidaySet}）的只读投影：考勤组 + 日期 → 类型。
 *
 * <p>{@code changeType} 口径（与平台一致，见 E9 考勤升级脚本与页面上“遇公众假日 / 遇调配工作日 /
 * 遇调配休息日”三档）：<b>1 = 公众假日（休息）、2 = 调配工作日（上班，覆盖周末）、
 * 3 = 调配休息日（休息）</b>。</p>
 */
public class KqHoliday {

    /** 日期 yyyy-MM-dd（{@code kq_HolidaySet.holidayDate}）。 */
    private String holidayDate;
    /** 类型：1 公众假日 / 2 调配工作日 / 3 调配休息日。 */
    private Integer changeType;

    public String getHolidayDate() { return holidayDate; }
    public void setHolidayDate(String holidayDate) { this.holidayDate = holidayDate; }

    public Integer getChangeType() { return changeType; }
    public void setChangeType(Integer changeType) { this.changeType = changeType; }
}
