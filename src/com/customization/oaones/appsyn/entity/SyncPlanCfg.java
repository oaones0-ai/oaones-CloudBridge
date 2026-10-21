package com.customization.oaones.appsyn.entity;

/**
 * 同步中心“同步设置”实体（对应业务表：表名取 oaones.properties#oaones_sync_plan_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行 = 一条同步规则：同一个“平台 + 租户 + 同步方向”<b>可以有多条规则</b>
 * （各自的同步范围、计划任务与口径不同），因此规则一律按主键 {@code id} 定位，
 * {@code (type, cfgid, direction)} 上只是普通索引。</p>
 */
public class SyncPlanCfg {

    private Integer id;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书（{@code OaonesConst.TYPE_*}）。 */
    private Integer type;
    /** 租户配置ID（uf_oaones_qywx_config.id）。 */
    private Integer cfgid;
    /** 同步方向：0 OA→平台 / 1 平台→OA。 */
    private Integer direction;
    /** 计划任务开关：1 启用 / 0 停用。 */
    private Integer enabled;
    /**
     * 计划任务方式（{@code OaonesConst.SYNC_PLAN_MODE_*}）：0 轮询间隔（分钟，见 {@link #intervalMin}）/
     * 1 高级模式（cron 表达式，见 {@link #cron}）；两种方式的取值一起保存，页面来回切不丢值。
     */
    private Integer planMode;
    /** 计划任务·轮询间隔（分钟，下限 5 / 上限 1440）：{@code planMode = 0} 时按它执行。 */
    private Integer intervalMin;
    /** 计划任务 cron 表达式（Quartz 口径）：{@code planMode = 1} 时按它执行。 */
    private String cron;
    /** 成员没有找到部门时的口径：0 不同步 / 1 直接挂在租户下（OA→平台 方向）。 */
    private Integer nodeptPolicy;
    /** 成员没有找到部门时的默认 OA 部门ID（平台→OA 方向）。 */
    private String defaultDeptId;
    /** 默认 OA 部门名称快照（页面展示用）。 */
    private String defaultDeptName;
    /** 同步范围开关：0 未设置范围（沿用全量同步，兼容历史行）/ 1 按 oaSubIds + oaDeptIds + wxDeptId 的范围同步。 */
    private Integer scopeSet;
    /** 同步范围·OA 分部ID（逗号分隔，多选；含其下级分部与部门）。 */
    private String oaSubIds;
    /** 同步范围·OA 部门ID（逗号分隔，多选，含其下级部门；平台→OA 方向时是 OA 落点部门）。 */
    private String oaDeptIds;
    /** 同步范围·平台部门ID（单选，留空 = 该租户配置的 root 节点）。 */
    private String wxDeptId;
    /** 同步范围文案快照（页面列表直接展示，不参与同步逻辑）。 */
    private String scopeText;
    /**
     * 首次“下级已有绑定”检测状态：0 未检测/未确认（保存时检测范围内（含下级）是否已有部门绑定
     * 或成员绑定，检测到由页面弹框询问是否强制同步）/ 1 已确认（保存成功或用户选择强制同步后置 1）。
     */
    private Integer bindForced;
    /** 下次执行时间 yyyy-MM-dd HH:mm:ss。 */
    private String nextRunTime;
    /** 最近一次触发时间。 */
    private String lastRunTime;
    /** 最近一次同步写入的同步日志ID。 */
    private Integer lastLogId;
    /** 最近一次执行结果提示。 */
    private String lastResult;
    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public Integer getDirection() { return direction; }
    public void setDirection(Integer direction) { this.direction = direction; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public Integer getPlanMode() { return planMode; }
    public void setPlanMode(Integer planMode) { this.planMode = planMode; }

    public Integer getIntervalMin() { return intervalMin; }
    public void setIntervalMin(Integer intervalMin) { this.intervalMin = intervalMin; }

    public Integer getNodeptPolicy() { return nodeptPolicy; }
    public void setNodeptPolicy(Integer nodeptPolicy) { this.nodeptPolicy = nodeptPolicy; }

    public String getDefaultDeptId() { return defaultDeptId; }
    public void setDefaultDeptId(String defaultDeptId) { this.defaultDeptId = defaultDeptId; }

    public String getDefaultDeptName() { return defaultDeptName; }
    public void setDefaultDeptName(String defaultDeptName) { this.defaultDeptName = defaultDeptName; }

    public Integer getScopeSet() { return scopeSet; }
    public void setScopeSet(Integer scopeSet) { this.scopeSet = scopeSet; }

    public String getOaSubIds() { return oaSubIds; }
    public void setOaSubIds(String oaSubIds) { this.oaSubIds = oaSubIds; }

    public String getOaDeptIds() { return oaDeptIds; }
    public void setOaDeptIds(String oaDeptIds) { this.oaDeptIds = oaDeptIds; }

    public String getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(String wxDeptId) { this.wxDeptId = wxDeptId; }

    public String getScopeText() { return scopeText; }
    public void setScopeText(String scopeText) { this.scopeText = scopeText; }

    public Integer getBindForced() { return bindForced; }
    public void setBindForced(Integer bindForced) { this.bindForced = bindForced; }

    public String getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(String nextRunTime) { this.nextRunTime = nextRunTime; }

    public String getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(String lastRunTime) { this.lastRunTime = lastRunTime; }

    public Integer getLastLogId() { return lastLogId; }
    public void setLastLogId(Integer lastLogId) { this.lastLogId = lastLogId; }

    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }

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
    public void setModedatamodifydatetime(String modedatamodifydatetime) { this.modedatamodifydatetime = modedatamodifydatetime; }
}
