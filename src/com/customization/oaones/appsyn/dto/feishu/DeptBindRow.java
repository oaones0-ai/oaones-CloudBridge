package com.customization.oaones.appsyn.dto.feishu;

/**
 * 部门绑定列表行（绑定表 + hrmdepartment/hrmsubcompany + 飞书租户配置 + 部门快照 关联结果）。
 */
public class DeptBindRow {

    private Integer id;
    private Integer cfgId;
    private String cfgName;
    private String cfgCorpId;

    private Integer oaDeptId;
    private String oaDeptName;
    private Integer oaDeptParent;
    private Integer oaSubcompanyId;
    private String oaSubcompanyName;
    private String oaCanceled;

    private String wxDeptId;
    private String wxParentDeptId;
    private Integer wxTmpStatus;

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

    public Integer getOaDeptId() { return oaDeptId; }
    public void setOaDeptId(Integer oaDeptId) { this.oaDeptId = oaDeptId; }

    public String getOaDeptName() { return oaDeptName; }
    public void setOaDeptName(String oaDeptName) { this.oaDeptName = oaDeptName; }

    public Integer getOaDeptParent() { return oaDeptParent; }
    public void setOaDeptParent(Integer oaDeptParent) { this.oaDeptParent = oaDeptParent; }

    public Integer getOaSubcompanyId() { return oaSubcompanyId; }
    public void setOaSubcompanyId(Integer oaSubcompanyId) { this.oaSubcompanyId = oaSubcompanyId; }

    public String getOaSubcompanyName() { return oaSubcompanyName; }
    public void setOaSubcompanyName(String oaSubcompanyName) { this.oaSubcompanyName = oaSubcompanyName; }

    public String getOaCanceled() { return oaCanceled; }
    public void setOaCanceled(String oaCanceled) { this.oaCanceled = oaCanceled; }

    public String getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(String wxDeptId) { this.wxDeptId = wxDeptId; }

    public String getWxParentDeptId() { return wxParentDeptId; }
    public void setWxParentDeptId(String wxParentDeptId) { this.wxParentDeptId = wxParentDeptId; }

    public Integer getWxTmpStatus() { return wxTmpStatus; }
    public void setWxTmpStatus(Integer wxTmpStatus) { this.wxTmpStatus = wxTmpStatus; }

    public Integer getBindState() { return bindState; }
    public void setBindState(Integer bindState) { this.bindState = bindState; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) { this.modedatamodifydatetime = modedatamodifydatetime; }

    public Integer getFormmodeid() { return formmodeid; }
    public void setFormmodeid(Integer formmodeid) { this.formmodeid = formmodeid; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
