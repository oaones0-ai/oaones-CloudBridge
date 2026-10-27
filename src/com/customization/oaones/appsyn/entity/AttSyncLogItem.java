package com.customization.oaones.appsyn.entity;

/**
 * 考勤同步**打卡明细**实体（业务表：表名取 {@code oaones.appsyn.properties#oaones_att_log_item_table}）。
 *
 * <p>一行 = 一条打卡的一次同步动作（新增 / 更新 / 跳过 / 失败），是问题追溯的**最小单位**：
 * 「这条打卡为什么没进 OA」在批次（{@link AttSyncLog}）+ 明细（本类）+ 接口报文（{@code uf_oaones_appsyn_api_log}）
 * 三层信息里都查得到（需求 3.8 / 验收 9）。</p>
 *
 * <p>个人信息控制（需求 5.3）：只落必要字段，平台原始的打卡类型 / 设备 / 地点只存**摘要**
 * （{@code rawType} / {@code rawSummary}），不落全量原始对象。</p>
 */
public class AttSyncLogItem {

    private Integer id;
    /** 所属批次ID（{@code uf_oaones_appsyn_att_log.id}）。 */
    private Integer logId;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private Integer type;
    /** 租户配置ID。 */
    private Integer cfgid;
    /** 同步设置ID（{@code uf_oaones_appsyn_att_cfg.id}）。 */
    private Integer cfgId;
    /** 方向：1 第三方→OA / 2 OA→第三方。 */
    private Integer dir;
    /** 触发方式：0 计划任务 / 1 手工 / 2 重推。 */
    private Integer trigger;
    /** OA 人员ID（未匹配到 OA 人员时为 0）。 */
    private Integer oaUserId;
    /** OA 人员姓名快照（列表展示；页面同时给出泛微标准人员链接）。 */
    private String oaUserName;
    /** OA 人员登录名快照。 */
    private String loginId;
    /** 平台账号（未绑定时为平台返回值 / 空）。 */
    private String wxUserId;
    /** 打卡日期 yyyy-MM-dd（平台给出的考勤日期，跨天班次据此核对）。 */
    private String signDate;
    /** 打卡时间 HH:mm:ss。 */
    private String signTime;
    /** 打卡时间戳 yyyy-MM-dd HH:mm:ss（去重键里的“打卡时间（秒）”，落库以它为准）。 */
    private String signDatetime;
    /** 打卡类型：1 签到 / 2 签退。 */
    private Integer signType;
    /** 数据来源：1 正常打卡 / 2 补签卡（补卡）（{@code OaonesConst.ATT_DATA_*}）。 */
    private Integer dataType;
    /** 平台原始类型（补卡 / 外勤 / 出差 / 正常，识别不出时写“平台类型未知”）。 */
    private String rawType;
    /** 平台原始信息摘要（设备 / 地点等只存摘要，不落全量原始对象）。 */
    private String rawSummary;
    /** 动作：0 新增 / 1 更新 / 2 跳过 / 3 失败。 */
    private Integer op;
    /** 说明（跳过用固定文案；失败带平台原文与异常摘要）。 */
    private String message;
    /** OA 侧记录标识（写进 {@code HrmScheduleSign} 后取回的ID）。 */
    private String oaSignId;
    /** 平台侧记录标识（平台返回的打卡记录ID / 补卡单号）。 */
    private String wxRecordId;
    /** 关联的接口日志ID（{@code uf_oaones_appsyn_api_log.id}），明细行可直接跳报文。 */
    private Integer apiLogId;
    /** 打卡时间所在日（时间窗过滤的归类日期，页面按天筛选用）。 */
    private String winDate;
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

    public Integer getLogId() { return logId; }
    public void setLogId(Integer logId) { this.logId = logId; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public Integer getDir() { return dir; }
    public void setDir(Integer dir) { this.dir = dir; }

    public Integer getTrigger() { return trigger; }
    public void setTrigger(Integer trigger) { this.trigger = trigger; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getOaUserName() { return oaUserName; }
    public void setOaUserName(String oaUserName) { this.oaUserName = oaUserName; }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public String getSignDate() { return signDate; }
    public void setSignDate(String signDate) { this.signDate = signDate; }

    public String getSignTime() { return signTime; }
    public void setSignTime(String signTime) { this.signTime = signTime; }

    public String getSignDatetime() { return signDatetime; }
    public void setSignDatetime(String signDatetime) { this.signDatetime = signDatetime; }

    public Integer getSignType() { return signType; }
    public void setSignType(Integer signType) { this.signType = signType; }

    public Integer getDataType() { return dataType; }
    public void setDataType(Integer dataType) { this.dataType = dataType; }

    public String getRawType() { return rawType; }
    public void setRawType(String rawType) { this.rawType = rawType; }

    public String getRawSummary() { return rawSummary; }
    public void setRawSummary(String rawSummary) { this.rawSummary = rawSummary; }

    public Integer getOp() { return op; }
    public void setOp(Integer op) { this.op = op; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getOaSignId() { return oaSignId; }
    public void setOaSignId(String oaSignId) { this.oaSignId = oaSignId; }

    public String getWxRecordId() { return wxRecordId; }
    public void setWxRecordId(String wxRecordId) { this.wxRecordId = wxRecordId; }

    public Integer getApiLogId() { return apiLogId; }
    public void setApiLogId(Integer apiLogId) { this.apiLogId = apiLogId; }

    public String getWinDate() { return winDate; }
    public void setWinDate(String winDate) { this.winDate = winDate; }

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
