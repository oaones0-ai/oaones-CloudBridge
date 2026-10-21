package com.customization.oaones.appsyn.dto.dingtalk;

/**
 * 人员绑定列表行（绑定表 + hrmresource/hrmdepartment + 钉钉租户配置 关联结果）。
 *
 * <p>同时服务于“绑定详情”页展示与同步引擎的绑定明细读取。</p>
 */
public class UserBindRow {

    private Integer id;
    private Integer cfgId;
    private String cfgName;
    private String cfgCorpId;

    private Integer oaUserId;
    private String oaName;
    private String loginid;
    private String oaMobile;
    private String oaEmail;
    private String workcode;
    private Integer oaDeptId;
    private String oaDeptName;
    private Integer oaSubcompanyId;
    private String oaStatus;
    /**
     * OA 人员是否已失效（不再同步）：1 失效 / 0 有效。
     *
     * <p>E9 标准 {@code hrmresource} <b>没有 canceled 列</b>（canceled 只用于
     * {@code hrmdepartment}/{@code hrmsubcompany} 表示部门/分部封存），
     * 这里的取值由 {@code hrmresource.status} 推导：0~3 有效、>=4（离职/解聘/退休等）为失效，
     * 与 {@code OaServiceImpl}（只取 status &lt; 4）同一口径。</p>
     */
    private String oaCanceled;

    private String wxUserId;
    private Integer wxDeptId;

    private Integer formmodeid;
    private Integer bindState;
    private Integer bindSource;
    private String bindTime;
    private Integer lastSyncDirection;
    private String lastSyncTime;
    private String remark;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public String getCfgCorpId() { return cfgCorpId; }
    public void setCfgCorpId(String cfgCorpId) { this.cfgCorpId = cfgCorpId; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getOaName() { return oaName; }
    public void setOaName(String oaName) { this.oaName = oaName; }

    public String getLoginid() { return loginid; }
    public void setLoginid(String loginid) { this.loginid = loginid; }

    public String getOaMobile() { return oaMobile; }
    public void setOaMobile(String oaMobile) { this.oaMobile = oaMobile; }

    public String getOaEmail() { return oaEmail; }
    public void setOaEmail(String oaEmail) { this.oaEmail = oaEmail; }

    public String getWorkcode() { return workcode; }
    public void setWorkcode(String workcode) { this.workcode = workcode; }

    public Integer getOaDeptId() { return oaDeptId; }
    public void setOaDeptId(Integer oaDeptId) { this.oaDeptId = oaDeptId; }

    public String getOaDeptName() { return oaDeptName; }
    public void setOaDeptName(String oaDeptName) { this.oaDeptName = oaDeptName; }

    public Integer getOaSubcompanyId() { return oaSubcompanyId; }
    public void setOaSubcompanyId(Integer oaSubcompanyId) { this.oaSubcompanyId = oaSubcompanyId; }

    public String getOaStatus() { return oaStatus; }
    public void setOaStatus(String oaStatus) { this.oaStatus = oaStatus; }

    public String getOaCanceled() { return oaCanceled; }
    public void setOaCanceled(String oaCanceled) { this.oaCanceled = oaCanceled; }

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public Integer getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(Integer wxDeptId) { this.wxDeptId = wxDeptId; }

    public Integer getFormmodeid() { return formmodeid; }
    public void setFormmodeid(Integer formmodeid) { this.formmodeid = formmodeid; }

    public Integer getBindState() { return bindState; }
    public void setBindState(Integer bindState) { this.bindState = bindState; }

    public Integer getBindSource() { return bindSource; }
    public void setBindSource(Integer bindSource) { this.bindSource = bindSource; }

    public String getBindTime() { return bindTime; }
    public void setBindTime(String bindTime) { this.bindTime = bindTime; }

    public Integer getLastSyncDirection() { return lastSyncDirection; }
    public void setLastSyncDirection(Integer lastSyncDirection) { this.lastSyncDirection = lastSyncDirection; }

    public String getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(String lastSyncTime) { this.lastSyncTime = lastSyncTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
