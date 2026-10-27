package com.customization.oaones.appsyn.entity;

/**
 * 考勤同步**设置**实体（业务表：表名取 {@code oaones.appsyn.properties#oaones_att_cfg_table}）。
 *
 * <p>一行 = <b>平台 + 租户 + 方向</b>（{@code rowType = 0}，同一组合**可以有多条**：
 * 不同范围 / 不同数据类型 / 不同时间口径 / 不同频率各自一条），
 * 另有一类 {@code rowType = 1} 的行作为“平台 + 租户”整级的启停开关（需求 3.10.3）。</p>
 *
 * <p>关键字段：</p>
 * <ul>
 *   <li>{@code dir}：方向，0 双向 / 1 第三方→OA / 2 OA→第三方（两个方向各自独立配置、执行、记日志）；</li>
 *   <li>{@code scopeSubs / scopeDepts / scopeUsers}：同步范围三要素（分部 / 部门 / 人员，多选，
 *       <b>三者之间是 OR 并集</b>：分部含其全部下级分部、部门含其全部下级部门），
 *       三者都为空 = 该租户**全部已绑定人员**；{@code scopeText} 是保存时的文案快照（列表直接展示）；</li>
 *   <li>{@code oaAccountRule}：同一平台账号绑定多个 OA 账户时的处理规则，
 *       0 = 只同步主账户 / 1 = 所有账户均同步；历史配置按 0 处理；</li>
 *   <li>{@code dataTypes}：数据类型位（1 正常打卡 / 2 补签卡，可多选）；
 *       {@code signPeriods}：打卡时段位（1 签到 / 2 签退，默认两种）；</li>
 *   <li>{@code winMode}：时间口径（0 当天 / 1 前一天 / 2 任意范围 / 3 最近 N 天），
 *       {@code winFrom / winTo / winDays} 配合它使用；</li>
 *   <li>{@code appId}：**考勤应用**（复用“应用绑定”记录，必须单独指定并校验有考勤数据权限；
 *       0 = 未指定，<b>不执行同步</b>，也不静默回退到租户的通讯录 Secret）；</li>
 *   <li>{@code planMode / planWorkdayGroupId / cronDayType / dayCron / nightCron}：本条设置自己的
 *       **计划任务**（与会议 / 日程同步同一套语法），时间段明细在 {@link AttPlanItem}；</li>
 *   <li>{@code checkState / checkMsg / checkTime}：考勤应用凭据与权限的校验结果
 *       （应用被删除 / Secret 被改后失效，执行时按“应用不可用”处理）；</li>
 *   <li>{@code lastRunTime / nextRunTime / lastResult / lastResultText / failStreak / lastLogId}：
 *       设置行上直接可见的健康度（连续失败到阈值时出显式告警文案）。</li>
 * </ul>
 */
public class AttSyncCfg {

    private Integer id;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书（{@code OaonesConst.TYPE_*}）。 */
    private Integer type;
    /** 租户配置ID（uf_oaones_appsyn_qywx_config.id）。 */
    private Integer cfgid;
    /** 行类型：0 同步设置 / 1 租户级开关（{@code OaonesConst.ATT_ROW_*}）。 */
    private Integer rowType;
    /** 设置名称（同一平台租户下行类型 + 名称唯一，便于多条设置区分）。 */
    private String cfgName;
    /** 方向：0 双向 / 1 第三方→OA / 2 OA→第三方（{@code OaonesConst.ATT_DIR_*}）。 */
    private Integer dir;

    /** 范围·分部（多选 OA 分部门ID，逗号分隔；含其全部下级分部）。 */
    private String scopeSubs;
    /** 范围·部门（多选 OA 部门ID，逗号分隔；含其全部下级部门）。 */
    private String scopeDepts;
    /** 范围·人员（多选 OA 人员ID，逗号分隔）。 */
    private String scopeUsers;
    /** 范围文案快照（保存时落一份，列表直接展示；组织改名后名称现算）。 */
    private String scopeText;

    /**
     * OA 多账户同步规则：0 = 只同步主账户 / 1 = 所有账户均同步。
     *
     * <p>E9 标准 {@code hrmresource.accounttype}：0 / 空 = 主账户，1 = 子账户。
     * 同一平台账号绑定多个 OA 账户时，这条设置决定哪些 OA 账户参与两个方向的同步。</p>
     */
    private Integer oaAccountRule;

    /** 数据类型位：1 正常打卡 / 2 补签卡（补卡）（可多选）。 */
    private Integer dataTypes;
    /** 打卡时段位：1 签到 / 2 签退（默认 3 = 两种都同步）。 */
    private Integer signPeriods;

    /** 时间口径：0 当天 / 1 前一天 / 2 任意范围 / 3 最近 N 天（{@code OaonesConst.ATT_WIN_*}）。 */
    private Integer winMode;
    /** 任意范围口径的起（yyyy-MM-dd HH:mm:ss）。 */
    private String winFrom;
    /** 任意范围口径的止（yyyy-MM-dd HH:mm:ss）。 */
    private String winTo;
    /** 「最近 N 天」口径的 N（默认取配置 {@code att.win.last.days.default}）。 */
    private Integer winDays;

    /** 考勤应用（“应用绑定”记录ID {@code uf_oaones_appsyn_app_bind.id}；0 = 未指定则**不执行同步**）。 */
    private Integer appId;

    /** 计划任务方式：0 按时间段 / 1 高级（cron）。 */
    private Integer planMode;
    /** 计划任务的工作日口径：考勤组ID（{@code kq_group.id}），0 = 不指定（按周一至周五）。 */
    private Integer planWorkdayGroupId;
    /** 高级模式的执行日：0 每天 / 1 仅工作日 / 2 仅非工作日。 */
    private Integer cronDayType;
    /** 高级模式·白天：Quartz cron（秒 分 时 日 月 周）；留空 = 该时段不自动同步。 */
    private String dayCron;
    /** 高级模式·夜间：同上；留空 = 该时段不自动同步。 */
    private String nightCron;
    /** 计划任务启用：1 启用 / 0 停用（停用后只允许手工执行）。 */
    private Integer enabled;

    /** 校验状态：0 未校验 / 1 通过 / 2 失败（{@code OaonesConst.ATT_CHECK_*}）。 */
    private Integer checkState;
    /** 校验结果说明（平台原始错误信息也写在这里）。 */
    private String checkMsg;
    /** 最近一次校验时间 yyyy-MM-dd HH:mm:ss。 */
    private String checkTime;

    /** 最近一次执行时间 yyyy-MM-dd HH:mm:ss。 */
    private String lastRunTime;
    /** 最近一次执行结果：0 新增 / 1 更新 / 2 跳过 / 3 失败（批次结果口径，见 {@code OaonesConst.SYNC_OP_*}）。 */
    private Integer lastResult;
    /** 最近一次执行的说明文案（成功 / 跳过 / 失败的摘要）。 */
    private String lastResultText;
    /** 连续失败次数（成功一轮即清零；到阈值出告警）。 */
    private Integer failStreak;
    /** 最近一次执行的批次ID（关联 {@code uf_oaones_appsyn_att_log.id}）。 */
    private Integer lastLogId;

    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    /* ---------------- 联表 / 计算出来的只读展示字段（不落库） ---------------- */

    /** 租户名称（联表带出）。 */
    private String tenantName;
    /** 租户企业标识（联表带出）。 */
    private String corpId;
    /** 考勤应用的名称（联表带出）。 */
    private String appName;
    /** 考勤应用是否已配置 Secret（不下发 Secret 本身）。 */
    private boolean appSecretReady;
    /** 设置行是否到了该跑的时间（调度器只读判断，页面展示用）。 */
    private boolean dueNow;
    /** 下一次计划执行时间（按计划任务实时算出来，只读）。 */
    private String nextRunTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public Integer getRowType() { return rowType; }
    public void setRowType(Integer rowType) { this.rowType = rowType; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public Integer getDir() { return dir; }
    public void setDir(Integer dir) { this.dir = dir; }

    public String getScopeSubs() { return scopeSubs; }
    public void setScopeSubs(String scopeSubs) { this.scopeSubs = scopeSubs; }

    public String getScopeDepts() { return scopeDepts; }
    public void setScopeDepts(String scopeDepts) { this.scopeDepts = scopeDepts; }

    public String getScopeUsers() { return scopeUsers; }
    public void setScopeUsers(String scopeUsers) { this.scopeUsers = scopeUsers; }

    public String getScopeText() { return scopeText; }
    public void setScopeText(String scopeText) { this.scopeText = scopeText; }

    public Integer getOaAccountRule() { return oaAccountRule; }
    public void setOaAccountRule(Integer oaAccountRule) { this.oaAccountRule = oaAccountRule; }

    public Integer getDataTypes() { return dataTypes; }
    public void setDataTypes(Integer dataTypes) { this.dataTypes = dataTypes; }

    public Integer getSignPeriods() { return signPeriods; }
    public void setSignPeriods(Integer signPeriods) { this.signPeriods = signPeriods; }

    public Integer getWinMode() { return winMode; }
    public void setWinMode(Integer winMode) { this.winMode = winMode; }

    public String getWinFrom() { return winFrom; }
    public void setWinFrom(String winFrom) { this.winFrom = winFrom; }

    public String getWinTo() { return winTo; }
    public void setWinTo(String winTo) { this.winTo = winTo; }

    public Integer getWinDays() { return winDays; }
    public void setWinDays(Integer winDays) { this.winDays = winDays; }

    public Integer getAppId() { return appId; }
    public void setAppId(Integer appId) { this.appId = appId; }

    public Integer getPlanMode() { return planMode; }
    public void setPlanMode(Integer planMode) { this.planMode = planMode; }

    public Integer getPlanWorkdayGroupId() { return planWorkdayGroupId; }
    public void setPlanWorkdayGroupId(Integer planWorkdayGroupId) { this.planWorkdayGroupId = planWorkdayGroupId; }

    public Integer getCronDayType() { return cronDayType; }
    public void setCronDayType(Integer cronDayType) { this.cronDayType = cronDayType; }

    public String getDayCron() { return dayCron; }
    public void setDayCron(String dayCron) { this.dayCron = dayCron; }

    public String getNightCron() { return nightCron; }
    public void setNightCron(String nightCron) { this.nightCron = nightCron; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public Integer getCheckState() { return checkState; }
    public void setCheckState(Integer checkState) { this.checkState = checkState; }

    public String getCheckMsg() { return checkMsg; }
    public void setCheckMsg(String checkMsg) { this.checkMsg = checkMsg; }

    public String getCheckTime() { return checkTime; }
    public void setCheckTime(String checkTime) { this.checkTime = checkTime; }

    public String getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(String lastRunTime) { this.lastRunTime = lastRunTime; }

    public Integer getLastResult() { return lastResult; }
    public void setLastResult(Integer lastResult) { this.lastResult = lastResult; }

    public String getLastResultText() { return lastResultText; }
    public void setLastResultText(String lastResultText) { this.lastResultText = lastResultText; }

    public Integer getFailStreak() { return failStreak; }
    public void setFailStreak(Integer failStreak) { this.failStreak = failStreak; }

    public Integer getLastLogId() { return lastLogId; }
    public void setLastLogId(Integer lastLogId) { this.lastLogId = lastLogId; }

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

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public boolean isAppSecretReady() { return appSecretReady; }
    public void setAppSecretReady(boolean appSecretReady) { this.appSecretReady = appSecretReady; }

    public boolean isDueNow() { return dueNow; }
    public void setDueNow(boolean dueNow) { this.dueNow = dueNow; }

    public String getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(String nextRunTime) { this.nextRunTime = nextRunTime; }
}
