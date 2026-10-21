package com.customization.oaones.appsyn.entity;

/**
 * 考勤同步**批次**实体（业务表：表名取 {@code oaones.properties#oaones_att_log_table}）。
 *
 * <p>一行 = 一次执行（计划任务到点 / 手工立即执行 / 重推）产生的整体记录；
 * 打卡明细（{@link AttSyncLogItem}）挂在批次下（{@code log_id}）。</p>
 *
 * <p>计数口径（需求 3.8.1）：成功 / 跳过 / 失败 / 新增 / 更新 + 平台请求次数；
 * 时间口径记本次执行实际用的起止（相对窗口在开始执行时就算好并落库，便于事后回答
 * “这一次到底取了哪一段”）。</p>
 */
public class AttSyncLog {

    private Integer id;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private Integer type;
    /** 租户配置ID。 */
    private Integer cfgid;
    /** 触发方式：0 计划任务 / 1 手工 / 2 重推（{@code OaonesConst.ATT_TRIGGER_*}）。 */
    private Integer trigger;
    /** 本次实际只跑的方向：1 第三方→OA / 2 OA→第三方 / 0 两个方向都跑（沿用设置的 dir）。 */
    private Integer dir;
    /** 所属同步设置ID（手工整批执行时为 0，表示“该租户下所有启用设置各跑一轮”）。 */
    private Integer cfgId;
    /** 设置名称快照。 */
    private String cfgName;
    /** 时间口径：0 当天 / 1 前一天 / 2 任意范围 / 3 最近 N 天。 */
    private Integer winMode;
    /** 本次时间窗起（yyyy-MM-dd HH:mm:ss）。 */
    private String winFrom;
    /** 本次时间窗止。 */
    private String winTo;
    /** 范围摘要（设置的 scope_text 快照）。 */
    private String scopeText;
    /** 数据类型位快照（1 正常 / 2 补卡）。 */
    private Integer dataTypes;
    /** 开始时间 yyyy-MM-dd HH:mm:ss。 */
    private String beginTime;
    /** 结束时间。 */
    private String endTime;
    /** 耗时（毫秒）。 */
    private Integer costMs;
    /** 新增条数。 */
    private Integer addCount;
    /** 更新条数。 */
    private Integer updateCount;
    /** 跳过条数。 */
    private Integer skipCount;
    /** 失败条数。 */
    private Integer failCount;
    /** 平台请求次数。 */
    private Integer apiCount;
    /** 结果：0 成功 / 3 失败（{@code OaonesConst.SYNC_OP_*}，与明细同一套动作口径）。 */
    private Integer result;
    /** 结果说明（失败原因 / 摘要）。 */
    private String message;
    private Integer operatorId;
    private String operatorName;
    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    /** 联表带出的租户名称（只读展示）。 */
    private String tenantName;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public Integer getTrigger() { return trigger; }
    public void setTrigger(Integer trigger) { this.trigger = trigger; }

    public Integer getDir() { return dir; }
    public void setDir(Integer dir) { this.dir = dir; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public Integer getWinMode() { return winMode; }
    public void setWinMode(Integer winMode) { this.winMode = winMode; }

    public String getWinFrom() { return winFrom; }
    public void setWinFrom(String winFrom) { this.winFrom = winFrom; }

    public String getWinTo() { return winTo; }
    public void setWinTo(String winTo) { this.winTo = winTo; }

    public String getScopeText() { return scopeText; }
    public void setScopeText(String scopeText) { this.scopeText = scopeText; }

    public Integer getDataTypes() { return dataTypes; }
    public void setDataTypes(Integer dataTypes) { this.dataTypes = dataTypes; }

    public String getBeginTime() { return beginTime; }
    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public Integer getCostMs() { return costMs; }
    public void setCostMs(Integer costMs) { this.costMs = costMs; }

    public Integer getAddCount() { return addCount; }
    public void setAddCount(Integer addCount) { this.addCount = addCount; }

    public Integer getUpdateCount() { return updateCount; }
    public void setUpdateCount(Integer updateCount) { this.updateCount = updateCount; }

    public Integer getSkipCount() { return skipCount; }
    public void setSkipCount(Integer skipCount) { this.skipCount = skipCount; }

    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }

    public Integer getApiCount() { return apiCount; }
    public void setApiCount(Integer apiCount) { this.apiCount = apiCount; }

    public Integer getResult() { return result; }
    public void setResult(Integer result) { this.result = result; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getOperatorId() { return operatorId; }
    public void setOperatorId(Integer operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

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
}
