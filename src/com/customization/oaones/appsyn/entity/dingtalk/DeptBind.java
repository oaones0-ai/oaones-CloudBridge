package com.customization.oaones.appsyn.entity.dingtalk;

/**
 * OA部门 - 钉钉部门 绑定实体（对应业务表：表名取 oaones.properties#oaones_dept_bind_table，
 * 建模模块ID按表名反查，配置里不出现）。
 */
public class DeptBind {

    private Integer id;
    private Integer cfgId;
    private Integer oaDeptId;
    private Integer wxDeptId;
    private Integer wxParentDeptId;
    /** 绑定状态：1 有效 / 0 失效（物理列 yx）。 */
    private Integer yx;
    private String remark;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public Integer getOaDeptId() { return oaDeptId; }
    public void setOaDeptId(Integer oaDeptId) { this.oaDeptId = oaDeptId; }

    public Integer getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(Integer wxDeptId) { this.wxDeptId = wxDeptId; }

    public Integer getWxParentDeptId() { return wxParentDeptId; }
    public void setWxParentDeptId(Integer wxParentDeptId) { this.wxParentDeptId = wxParentDeptId; }

    public Integer getYx() { return yx; }
    public void setYx(Integer yx) { this.yx = yx; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }

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
