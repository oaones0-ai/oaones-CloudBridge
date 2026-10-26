package com.customization.oaones.appsyn.dto;

/**
 * OA 分部 - 平台租户映射列表行。
 */
public class OrgMapRow {

    private Integer id;
    private Integer cfgId;
    private String cfgName;
    private String cfgCorpId;
    private Integer oaSubId;
    private String oaSubName;
    private Integer oaSubParentId;
    private Integer type;
    private Integer bindState;
    private String remark;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public String getCfgCorpId() { return cfgCorpId; }
    public void setCfgCorpId(String cfgCorpId) { this.cfgCorpId = cfgCorpId; }

    public Integer getOaSubId() { return oaSubId; }
    public void setOaSubId(Integer oaSubId) { this.oaSubId = oaSubId; }

    public String getOaSubName() { return oaSubName; }
    public void setOaSubName(String oaSubName) { this.oaSubName = oaSubName; }

    public Integer getOaSubParentId() { return oaSubParentId; }
    public void setOaSubParentId(Integer oaSubParentId) { this.oaSubParentId = oaSubParentId; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getBindState() { return bindState; }
    public void setBindState(Integer bindState) { this.bindState = bindState; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) { this.modedatamodifydatetime = modedatamodifydatetime; }
}
