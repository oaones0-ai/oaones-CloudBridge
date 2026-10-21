package com.customization.oaones.appsyn.dto.wecom;

/**
 * 分部绑定列表行（绑定表 + hrmsubcompany + 企微租户配置 关联结果）。
 *
 * <p>企微侧只关联租户配置（corp_name / corpid / 根部门 root_dept_id），
 * 不再关联部门快照：分部绑定只登记归属的企微租户，企微部门取租户配置中维护的根部门。</p>
 */
public class SubBindRow {

    private Integer id;
    private Integer cfgId;
    private String cfgName;
    private String cfgCorpId;
    private Integer cfgRootDeptId;
    private Integer cfgSyncEnable;

    private Integer oaSubId;
    /** 该分部在企微侧对应的部门ID（同步中心按范围同步时写入；老数据为空）。 */
    private Integer wxDeptId;
    private String oaSubName;
    private Integer oaSubParent;
    private String oaSubCanceled;

    private Integer bindState;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private String modedatamodifydatetime;
    private Integer formmodeid;
    private String remark;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public String getCfgCorpId() { return cfgCorpId; }
    public void setCfgCorpId(String cfgCorpId) { this.cfgCorpId = cfgCorpId; }

    public Integer getCfgRootDeptId() { return cfgRootDeptId; }
    public void setCfgRootDeptId(Integer cfgRootDeptId) { this.cfgRootDeptId = cfgRootDeptId; }

    public Integer getCfgSyncEnable() { return cfgSyncEnable; }
    public void setCfgSyncEnable(Integer cfgSyncEnable) { this.cfgSyncEnable = cfgSyncEnable; }

    public Integer getOaSubId() { return oaSubId; }
    public void setOaSubId(Integer oaSubId) { this.oaSubId = oaSubId; }

    public Integer getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(Integer wxDeptId) { this.wxDeptId = wxDeptId; }

    public String getOaSubName() { return oaSubName; }
    public void setOaSubName(String oaSubName) { this.oaSubName = oaSubName; }

    public Integer getOaSubParent() { return oaSubParent; }
    public void setOaSubParent(Integer oaSubParent) { this.oaSubParent = oaSubParent; }

    public String getOaSubCanceled() { return oaSubCanceled; }
    public void setOaSubCanceled(String oaSubCanceled) { this.oaSubCanceled = oaSubCanceled; }

    public Integer getBindState() { return bindState; }
    public void setBindState(Integer bindState) { this.bindState = bindState; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) {
        this.modedatacreatedate = modedatacreatedate;
    }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }

    public Integer getFormmodeid() { return formmodeid; }
    public void setFormmodeid(Integer formmodeid) { this.formmodeid = formmodeid; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
