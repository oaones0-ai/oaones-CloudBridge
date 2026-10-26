package com.customization.oaones.appsyn.entity;

/**
 * OA 分部 - 平台租户 一对一映射实体。
 *
 * <p>一行只表示一个平台内的一条映射：{@code type} 区分企微 / 钉钉 / 飞书，
 * OA 侧固定为分部。跨平台互不影响。</p>
 */
public class OrgMap {

    private Integer id;
    private Integer cfgId;
    private Integer oaSubId;
    private Integer type;
    /** 数据状态：0 有效 / 1 失效。 */
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

    public Integer getOaSubId() { return oaSubId; }
    public void setOaSubId(Integer oaSubId) { this.oaSubId = oaSubId; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getYx() { return yx; }
    public void setYx(Integer yx) { this.yx = yx; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；未配置为建模表时不写 0。 */
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
