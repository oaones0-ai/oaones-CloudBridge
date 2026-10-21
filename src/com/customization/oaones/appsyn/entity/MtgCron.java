package com.customization.oaones.appsyn.entity;

/**
 * 会议 / 日程同步「计划任务」实体（业务表：表名取 oaones.properties#oaones_mtg_cron_table）。
 *
 * <p>这张表**只保留一行（id = 1）**：会议 / 日程同步（OA→第三方 推送 + 第三方→OA 拉取）
 * 由内置调度器按本行决定“什么时候跑一轮”。同步频率按公司上下班时间自由设置，
 * 因此本行有<b>两种同步方式</b>（{@code planMode}）：</p>
 * <ul>
 *   <li>{@code planMode = 0}（**默认**）按时间段：明细在子表 {@link MtgPlanItem}
 *       （{@code uf_oaones_mtg_plan_item}）里，页面上可自由加多个时间段、每段一个轮询间隔；</li>
 *   <li>{@code planMode = 1} 高级模式：直接写 Quartz cron（秒 分 时 日 月 周，频率下限 5 分钟），
 *       沿用原来的“白天 / 夜间”两档——{@code dayCron}（默认 9:00–18:00，见
 *       {@code mtg.pull.hour.from/to}）与 {@code nightCron}（其余时段，留空 = 该时段不跑）。</li>
 * </ul>
 *
 * <p>两种方式都支持按<b>工作日 / 非工作日</b>区分：工作日按泛微标准考勤口径算
 * （考勤组的固定排班 / 工作日勾选 + 节假日与调休，见
 * {@link com.customization.oaones.appsyn.common.OaonesWorkday}），
 * 由 {@code workdayGroupId} 指定用哪个考勤组（0 = 不指定，按周一至周五算）。
 * 时间段模式按明细行的 {@code dayType} 逐段区分；高级模式用 {@code cronDayType} 统一区分。</p>
 *
 * <p>没有本行、或本行为空时按 {@code oaones.properties} 里的默认值执行
 * （白天 60 分钟 / 夜间 120 分钟两档 cron）。</p>
 */
public class MtgCron {

    private Integer id;
    /**
     * 同步方式：0 = 按时间段（明细表 {@code uf_oaones_mtg_plan_item}，默认）/
     * 1 = 高级（下面的两个 cron）。见 {@code OaonesConst#MTG_PLAN_MODE_ITEM}。
     */
    private Integer planMode;
    /**
     * 工作日口径来源：考勤组ID（E9 标准表 {@code kq_group.id}）；
     * 0 / 空 = 不指定考勤组，工作日按“周一至周五”算。
     */
    private Integer workdayGroupId;
    /** 高级模式（planMode = 1）的适用日：0 每天 / 1 仅工作日 / 2 仅非工作日。 */
    private Integer cronDayType;
    /** 高级模式·白天（默认 9:00–18:00）的计划任务：Quartz cron（秒 分 时 日 月 周）；留空 = 该时段不跑。 */
    private String dayCron;
    /** 高级模式·夜间（其余时段）的计划任务：同上；留空 = 该时段不跑。 */
    private String nightCron;
    /** 计划任务开关：1 启用 / 0 停用（停用后不自动跑，页面上的“立即同步 / 拉取”仍可用）。 */
    private Integer enabled;
    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPlanMode() { return planMode; }
    public void setPlanMode(Integer planMode) { this.planMode = planMode; }

    public Integer getWorkdayGroupId() { return workdayGroupId; }
    public void setWorkdayGroupId(Integer workdayGroupId) { this.workdayGroupId = workdayGroupId; }

    public Integer getCronDayType() { return cronDayType; }
    public void setCronDayType(Integer cronDayType) { this.cronDayType = cronDayType; }

    public String getDayCron() { return dayCron; }
    public void setDayCron(String dayCron) { this.dayCron = dayCron; }

    public String getNightCron() { return nightCron; }
    public void setNightCron(String nightCron) { this.nightCron = nightCron; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

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
