package com.customization.oaones.appsyn.dto;

/**
 * 建模数据审计字段（用于重建单条数据共享权限）。
 */
public class ModeAudit {

    private Integer formmodeid;
    private Integer modedatacreater;

    public Integer getFormmodeid() { return formmodeid; }
    public void setFormmodeid(Integer formmodeid) { this.formmodeid = formmodeid; }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }
}
