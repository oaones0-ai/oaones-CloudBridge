package com.customization.oaones.appsyn.entity;

/**
 * 会议 / 日程同步「计划任务」的**时间段明细**（业务表：表名取
 * {@code oaones.properties#oaones_mtg_plan_item_table}）。
 *
 * <p>计划任务选了“按时间段”（{@link com.customization.oaones.appsyn.common.OaonesConst#MTG_PLAN_MODE_ITEM}）时，
 * 一行 = 一个时间段：**适用日 + 开始时间 + 结束时间 + 轮询间隔**。可以自由配多条
 * （例如上学日 7:40–12:00 每 15 分钟、14:00–17:30 每 30 分钟），
 * <b>同一适用日下的时间段不允许重叠</b>（保存时校验，见 {@code MtgSyncServiceImpl#checkPlanItems}）。</p>
 *
 * <p>口径：</p>
 * <ul>
 *   <li>{@code dayType}：适用日，0 每天 / 1 工作日 / 2 非工作日（工作日按泛微标准考勤口径算，
 *       见 {@link com.customization.oaones.appsyn.common.OaonesWorkday}）；「每天」与
 *       「工作日 / 非工作日」不允许同时配（否则同一天会命中两套时段）。</li>
 *   <li>{@code startTime} / {@code endTime}：{@code HH:mm}；结束时间 <b>不大于</b>开始时间时视为
 *       **跨天时段**（例如 21:00–06:30 表示当天 21:00 到次日 06:30）。</li>
 *   <li>{@code intervalMin}：该时段内的轮询间隔（分钟，下限
 *       {@link com.customization.oaones.appsyn.common.OaonesConst#MTG_PLAN_MIN_MINUTES}）；
 *       轮询时间**从时段开始时间起算**（8:30 起每 45 分钟 → 8:30 / 9:15 / 10:00 …）。</li>
 * </ul>
 */
public class MtgPlanItem {

    private Integer id;
    /** 所属计划：固定 1（与 {@code uf_oaones_mtg_cron} 的单行 id 对应），预留给将来的多计划。 */
    private Integer planId;
    /** 适用日：0 每天 / 1 工作日 / 2 非工作日（见 {@code OaonesConst#MTG_PLAN_DAY_*}）。 */
    private Integer dayType;
    /** 时段开始时间 HH:mm（轮询从这一刻起算）。 */
    private String startTime;
    /** 时段结束时间 HH:mm；不大于开始时间 = 跨天（到次日该时刻）。 */
    private String endTime;
    /** 时段内的轮询间隔（分钟）。 */
    private Integer intervalMin;
    /** 页面显示顺序（保存时按页面顺序重排）。 */
    private Integer showorder;
    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPlanId() { return planId; }
    public void setPlanId(Integer planId) { this.planId = planId; }

    public Integer getDayType() { return dayType; }
    public void setDayType(Integer dayType) { this.dayType = dayType; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public Integer getIntervalMin() { return intervalMin; }
    public void setIntervalMin(Integer intervalMin) { this.intervalMin = intervalMin; }

    public Integer getShoworder() { return showorder; }
    public void setShoworder(Integer showorder) { this.showorder = showorder; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
