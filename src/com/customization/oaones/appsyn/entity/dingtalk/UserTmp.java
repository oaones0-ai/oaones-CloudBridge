package com.customization.oaones.appsyn.entity.dingtalk;

/**
 * 钉钉人员快照实体（对应业务表：表名取 oaones.appsyn.properties#oaones_user_tmp_table，
 * 建模模块ID按表名反查，配置里不出现；主键 AppKey + wxUserId）。
 */
public class UserTmp {

    private String AppKey;
    private String wxUserId;
    private String wxName;
    private Integer wxDeptId;
    private String wxPosition;
    private String wxMobile;
    private String wxEmail;
    private String wxBizMail;
    /** 性别：0 男 / 1 女（序号从 0 开始）。 */
    private Integer wxGender;
    private String wxAvatar;
    private String wxTelephone;
    /** 钉钉账号状态：0 激活 / 1 禁用 / 2 未激活 / 3 退出（序号从 0 开始）。 */
    private Integer wxStatus;
    /** 快照状态：0 正常 / 1 已删除或已封存。 */
    private Integer status;
    /** 平台类型：0 钉钉 / 1 钉钉。 */
    private Integer type;
    /**
     * 免绑定标记：0 = 免绑定（不参与人员绑定与同步）/ NULL = 正常（不写值）。
     *
     * <p>本表只用 0 表达“免绑定”，已取消 1 等其它状态值；快照刷新不会重置该标记。</p>
     */
    private Integer noBind;
    private String lastSyncTime;
    private String remark;
    private Integer formmodeid;

    public String getCorpid() { return AppKey; }
    public void setCorpid(String AppKey) { this.AppKey = AppKey; }

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public String getWxName() { return wxName; }
    public void setWxName(String wxName) { this.wxName = wxName; }

    public Integer getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(Integer wxDeptId) { this.wxDeptId = wxDeptId; }

    public String getWxPosition() { return wxPosition; }
    public void setWxPosition(String wxPosition) { this.wxPosition = wxPosition; }

    public String getWxMobile() { return wxMobile; }
    public void setWxMobile(String wxMobile) { this.wxMobile = wxMobile; }

    public String getWxEmail() { return wxEmail; }
    public void setWxEmail(String wxEmail) { this.wxEmail = wxEmail; }

    public String getWxBizMail() { return wxBizMail; }
    public void setWxBizMail(String wxBizMail) { this.wxBizMail = wxBizMail; }

    public Integer getWxGender() { return wxGender; }
    public void setWxGender(Integer wxGender) { this.wxGender = wxGender; }

    public String getWxAvatar() { return wxAvatar; }
    public void setWxAvatar(String wxAvatar) { this.wxAvatar = wxAvatar; }

    public String getWxTelephone() { return wxTelephone; }
    public void setWxTelephone(String wxTelephone) { this.wxTelephone = wxTelephone; }

    public Integer getWxStatus() { return wxStatus; }
    public void setWxStatus(Integer wxStatus) { this.wxStatus = wxStatus; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getNoBind() { return noBind; }
    public void setNoBind(Integer noBind) { this.noBind = noBind; }

    public String getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(String lastSyncTime) { this.lastSyncTime = lastSyncTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }
}
