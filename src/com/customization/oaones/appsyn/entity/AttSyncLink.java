package com.customization.oaones.appsyn.entity;

/**
 * 考勤同步「**平台打卡记录 ⇄ OA 打卡记录**」映射实体
 * （业务表：表名取 {@code oaones.properties#oaones_att_link_table}）。
 *
 * <p>一行 = 一个「平台 + 租户 + 方向」下的一条打卡对应的两侧记录：用途有三个（需求 4.1.2）：</p>
 * <ol>
 *   <li><b>重复执行去重</b>：同一时间窗反复执行时，命中去重键的打卡记“跳过：已同步”，不重复插入；</li>
 *   <li><b>重推定位</b>：失败 / 跳过的明细重推时按「平台 + 租户 + 人员 + 打卡时间 + 类型」定位；</li>
 *   <li><b>反向同步判定</b>：OA→第三方 时确认“这条 OA 记录本来就是从平台导进来的”，避免来回搬运。</li>
 * </ol>
 *
 * <p>去重键（需求 4.1.1）：平台 + 租户 + 人员 + 打卡时间（秒）+ 打卡类型（签到 / 签退）+ 来源
 * （正常 / 补卡 / 外勤 / 出差）。平台给出稳定记录ID 时优先用它作为补充判据
 * （{@code wxRecordId} 非空则先按它查）。</p>
 */
public class AttSyncLink {

    private Integer id;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private Integer type;
    /** 租户配置ID。 */
    private Integer cfgid;
    /** 方向：1 第三方→OA（导入） / 2 OA→第三方（推送）。 */
    private Integer dir;
    /** OA 人员ID。 */
    private Integer oaUserId;
    /** 平台账号（成员ID）。 */
    private String wxUserId;
    /** 打卡日期 yyyy-MM-dd（平台给出的考勤日期）。 */
    private String signDate;
    /** 打卡时间 HH:mm:ss。 */
    private String signTime;
    /** 打卡类型：1 签到 / 2 签退。 */
    private Integer signType;
    /** 数据来源：1 正常打卡 / 2 补签卡（补卡）。 */
    private Integer dataType;
    /** 平台侧记录标识（打卡记录ID / 补卡单号；没有时为空串，保证唯一键不含 NULL）。 */
    private String wxRecordId;
    /** OA 侧记录标识（{@code HrmScheduleSign.id}）。 */
    private String oaSignId;
    /** 来源：0 由「第三方→OA」导入建立 / 1 由「OA→第三方」推送建立（{@code OaonesConst.ATT_LINK_SOURCE_*}）。 */
    private Integer source;
    /** 最近一次同步时间 yyyy-MM-dd HH:mm:ss。 */
    private String lastTime;
    /** 平台原始类型摘要（正常 / 补卡 / 外勤 / 出差 / 平台类型未知）。 */
    private String rawType;
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

    public Integer getDir() { return dir; }
    public void setDir(Integer dir) { this.dir = dir; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public String getSignDate() { return signDate; }
    public void setSignDate(String signDate) { this.signDate = signDate; }

    public String getSignTime() { return signTime; }
    public void setSignTime(String signTime) { this.signTime = signTime; }

    public Integer getSignType() { return signType; }
    public void setSignType(Integer signType) { this.signType = signType; }

    public Integer getDataType() { return dataType; }
    public void setDataType(Integer dataType) { this.dataType = dataType; }

    public String getWxRecordId() { return wxRecordId; }
    public void setWxRecordId(String wxRecordId) { this.wxRecordId = wxRecordId; }

    public String getOaSignId() { return oaSignId; }
    public void setOaSignId(String oaSignId) { this.oaSignId = oaSignId; }

    public Integer getSource() { return source; }
    public void setSource(Integer source) { this.source = source; }

    public String getLastTime() { return lastTime; }
    public void setLastTime(String lastTime) { this.lastTime = lastTime; }

    public String getRawType() { return rawType; }
    public void setRawType(String rawType) { this.rawType = rawType; }

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
