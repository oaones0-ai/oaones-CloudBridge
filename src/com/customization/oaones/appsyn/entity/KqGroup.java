package com.customization.oaones.appsyn.entity;

/**
 * E9 标准考勤「考勤组」（表 {@code kq_group}）的只读投影。
 *
 * <p>会议 / 日程同步的「计划任务」要区分<b>工作日 / 非工作日</b>，口径直接取泛微标准考勤设置：
 * 这一行给出考勤组名称与它的“工作日”勾选（{@code weekday}，老版本 E9 用），
 * 搭配 {@code kq_fixedschedulce}（固定排班，新版 E9 用）与 {@code kq_HolidaySet}（节假日 / 调休）
 * 一起算出某一天算不算工作日，见 {@link com.customization.oaones.appsyn.common.OaonesWorkday}。</p>
 */
public class KqGroup {

    private Integer id;
    /** 考勤组名称（页面下拉用）。 */
    private String name;
    /**
     * 考勤组的“工作日”勾选（原样取出，格式 `0,1,2,3,4` 或 `1;2;3;4;5` 之类）；
     * 空 = 该组没勾（新版 E9 改用固定排班 {@code kq_fixedschedulce}）。
     */
    private String weekday;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWeekday() { return weekday; }
    public void setWeekday(String weekday) { this.weekday = weekday; }
}
