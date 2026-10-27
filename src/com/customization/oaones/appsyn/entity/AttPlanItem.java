package com.customization.oaones.appsyn.entity;

/**
 * 考勤同步「计划任务」的**时间段明细**（业务表：表名取
 * {@code oaones.appsyn.properties#oaones_att_plan_item_table}）。
 *
 * <p>与会议 / 日程同步的时间段明细同构：一行 = 一个时间段 = <b>适用日 + 开始时间 + 结束时间 +
 * 轮询间隔（分钟）</b>，挂在某一条同步设置上（{@code cfg_id}，需求 Q2：计划任务**每条设置一份**）。</p>
 *
 * <ul>
 *   <li>{@code dayType}：0 每天 / 1 工作日 / 2 非工作日（工作日按泛微标准考勤口径算，
 *       见 {@link com.customization.oaones.appsyn.common.OaonesWorkday}）；</li>
 *   <li>{@code startTime} / {@code endTime}：{@code HH:mm}，结束时间不大于开始时间 = **跨天**时段
 *       （例如夜班 21:00–06:30）；跨天时段按**开始那一天**的适用日算；</li>
 *   <li>{@code intervalMin}：轮询间隔（分钟，下限 {@code OaonesConst.ATT_PLAN_MIN_MINUTES}）；
 *       轮询从每段开始时间起算；</li>
 *   <li>同一适用日下的时间段不允许重叠（保存时校验）；保存时按设置**整表重写**。</li>
 * </ul>
 */
public class AttPlanItem {

    private Integer id;
    /** 所属同步设置ID（{@code uf_oaones_appsyn_att_cfg.id}）：计划任务每条设置一份（需求 Q2）。 */
    private Integer cfgId;
    /** 适用日：0 每天 / 1 工作日 / 2 非工作日。 */
    private Integer dayType;
    /** 时段开始时间 HH:mm（轮询从这一刻起算）。 */
    private String startTime;
    /** 时段结束时间 HH:mm；不大于开始时间 = 跨天到次日该时刻。 */
    private String endTime;
    /** 时段内的轮询间隔（分钟）。 */
    private Integer intervalMin;
    /** 页面显示顺序。 */
    private Integer showorder;
    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

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
