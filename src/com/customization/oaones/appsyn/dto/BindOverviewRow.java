package com.customization.oaones.appsyn.dto;

/**
 * 绑定总览行（`/oaones/appsyn/index/BindOverview.jsp` 用，只读）。
 *
 * <p>总览页把三个连接器（企微 / 钉钉 / 飞书）的全部平台租户放在一起展示，
 * 两类绑定各有各的列，本 DTO 用一份字段承载两类行的并集：</p>
 * <ul>
 *   <li>部门绑定（{@code dept}）：OA 部门 → 平台部门（多对一）；</li>
 *   <li>人员绑定（{@code user}）：OA 人员 → 平台成员（多对一）。</li>
 * </ul>
 *
 * <p>不涉及的字段留空，不做“一个字段两种含义”的复用（例如 OA 侧上级名称统一放
 * {@link #oaParentName}，人员所在部门另放 {@link #oaDeptName}）。</p>
 */
public class BindOverviewRow {

    /** 绑定记录ID（各绑定表主键）。 */
    private Integer id;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书。 */
    private Integer platformType;
    private Integer cfgId;
    private String cfgName;
    private String corpId;
    /** 租户同步开关：1 启用 / 0 停用（物理列 qy）。 */
    private Integer cfgEnable;
    /** 租户根部门（企微 / 钉钉，数字ID）。 */
    private Integer rootDeptId;
    /** 租户根部门（飞书，字符串ID；根部门为 0）。 */
    private String rootDeptKey;
    /** 租户根部门名称（取部门快照，取不到为空）。 */
    private String rootDeptName;
    /** 绑定状态：部门 yx=1 有效；人员 bind_state=1 已绑定。 */
    private Integer bindState;

    /** OA 侧对象ID（分部ID / 部门ID / 人员ID）。 */
    private Integer oaId;
    /** OA 侧对象名称。 */
    private String oaName;
    /** OA 侧封存标记（部门；人员侧为空，人员有效性看 status）。 */
    private String oaCanceled;
    /** OA 侧上级部门名称。 */
    private String oaParentName;
    /** OA 侧上级部门ID，用于渲染泛微标准页面链接。 */
    private Integer oaParentId;
    /** OA 侧所属分部名称（部门、人员用）。 */
    private String oaSubName;
    /** OA 侧所属分部ID（部门、人员用）。 */
    private Integer oaSubId;
    /** OA 侧所在部门ID / 名称（人员用）。 */
    private Integer oaDeptId;
    private String oaDeptName;
    /** OA 人员登录名 / 工号 / 手机号（人员用）。 */
    private String oaLoginId;
    private String oaWorkcode;
    private String oaMobile;
    /** OA 人员状态（0~3 有效，>=4 离职/解聘等失效）。 */
    private Integer oaStatus;

    /** 平台侧部门ID（企微 / 钉钉）。 */
    private Integer wxDeptId;
    /** 平台侧部门ID（飞书，字符串）。 */
    private String wxDeptKey;
    /** 平台侧部门名称（取部门快照，取不到为空）。 */
    private String wxDeptName;
    /** 平台侧成员ID（企微 userid / 钉钉 userid / 飞书 open_id）。 */
    private String wxUserId;
    /** 平台侧成员名称（取人员快照，取不到为空）。 */
    private String wxUserName;
    /** 平台侧成员主部门（取人员快照）与其名称。 */
    private Integer wxUserDeptId;
    private String wxUserDeptKey;
    private String wxUserDeptName;

    /** 绑定时间（人员绑定表的 bind_time 列）。 */
    private String bindTime;
    /** 建模创建日期 / 时间（部门绑定没有单独的绑定时间列，用它拼绑定时间）。 */
    private String createdDate;
    private String createdTime;
    /** 最近修改时间（建模标准字段 modedatamodifydatetime）。 */
    private String updateTime;
    private String remark;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPlatformType() { return platformType; }
    public void setPlatformType(Integer platformType) { this.platformType = platformType; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public Integer getCfgEnable() { return cfgEnable; }
    public void setCfgEnable(Integer cfgEnable) { this.cfgEnable = cfgEnable; }

    public Integer getRootDeptId() { return rootDeptId; }
    public void setRootDeptId(Integer rootDeptId) { this.rootDeptId = rootDeptId; }

    public String getRootDeptKey() { return rootDeptKey; }
    public void setRootDeptKey(String rootDeptKey) { this.rootDeptKey = rootDeptKey; }

    public String getRootDeptName() { return rootDeptName; }
    public void setRootDeptName(String rootDeptName) { this.rootDeptName = rootDeptName; }

    public Integer getBindState() { return bindState; }
    public void setBindState(Integer bindState) { this.bindState = bindState; }

    public Integer getOaId() { return oaId; }
    public void setOaId(Integer oaId) { this.oaId = oaId; }

    public String getOaName() { return oaName; }
    public void setOaName(String oaName) { this.oaName = oaName; }

    public String getOaCanceled() { return oaCanceled; }
    public void setOaCanceled(String oaCanceled) { this.oaCanceled = oaCanceled; }

    public String getOaParentName() { return oaParentName; }
    public void setOaParentName(String oaParentName) { this.oaParentName = oaParentName; }

    public Integer getOaParentId() { return oaParentId; }
    public void setOaParentId(Integer oaParentId) { this.oaParentId = oaParentId; }

    public String getOaSubName() { return oaSubName; }
    public void setOaSubName(String oaSubName) { this.oaSubName = oaSubName; }

    public Integer getOaSubId() { return oaSubId; }
    public void setOaSubId(Integer oaSubId) { this.oaSubId = oaSubId; }

    public Integer getOaDeptId() { return oaDeptId; }
    public void setOaDeptId(Integer oaDeptId) { this.oaDeptId = oaDeptId; }

    public String getOaDeptName() { return oaDeptName; }
    public void setOaDeptName(String oaDeptName) { this.oaDeptName = oaDeptName; }

    public String getOaLoginId() { return oaLoginId; }
    public void setOaLoginId(String oaLoginId) { this.oaLoginId = oaLoginId; }

    public String getOaWorkcode() { return oaWorkcode; }
    public void setOaWorkcode(String oaWorkcode) { this.oaWorkcode = oaWorkcode; }

    public String getOaMobile() { return oaMobile; }
    public void setOaMobile(String oaMobile) { this.oaMobile = oaMobile; }

    public Integer getOaStatus() { return oaStatus; }
    public void setOaStatus(Integer oaStatus) { this.oaStatus = oaStatus; }

    public Integer getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(Integer wxDeptId) { this.wxDeptId = wxDeptId; }

    public String getWxDeptKey() { return wxDeptKey; }
    public void setWxDeptKey(String wxDeptKey) { this.wxDeptKey = wxDeptKey; }

    public String getWxDeptName() { return wxDeptName; }
    public void setWxDeptName(String wxDeptName) { this.wxDeptName = wxDeptName; }

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public String getWxUserName() { return wxUserName; }
    public void setWxUserName(String wxUserName) { this.wxUserName = wxUserName; }

    public Integer getWxUserDeptId() { return wxUserDeptId; }
    public void setWxUserDeptId(Integer wxUserDeptId) { this.wxUserDeptId = wxUserDeptId; }

    public String getWxUserDeptKey() { return wxUserDeptKey; }
    public void setWxUserDeptKey(String wxUserDeptKey) { this.wxUserDeptKey = wxUserDeptKey; }

    public String getWxUserDeptName() { return wxUserDeptName; }
    public void setWxUserDeptName(String wxUserDeptName) { this.wxUserDeptName = wxUserDeptName; }

    public String getBindTime() { return bindTime; }
    public void setBindTime(String bindTime) { this.bindTime = bindTime; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
