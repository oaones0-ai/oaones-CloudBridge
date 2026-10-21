package com.customization.oaones.appsyn.dto.wecom;

/**
 * 企微成员快照行（人员快照表 + 部门快照 关联结果）。
 */
public class WxUserRow {

    private String wxUserId;
    private String wxName;
    private Integer wxDeptId;
    private String wxDeptName;
    private String wxPosition;
    private String wxMobile;
    private String wxEmail;
    private String wxBizMail;
    private Integer wxGender;
    private String wxAvatar;
    private String wxTelephone;
    private Integer wxStatus;
    /** 免绑定标记：0 = 免绑定；NULL（前端收到空串）= 正常（本表只保留 0）。 */
    private Integer noBind;
    private String lastSyncTime;

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public String getWxName() { return wxName; }
    public void setWxName(String wxName) { this.wxName = wxName; }

    public Integer getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(Integer wxDeptId) { this.wxDeptId = wxDeptId; }

    public String getWxDeptName() { return wxDeptName; }
    public void setWxDeptName(String wxDeptName) { this.wxDeptName = wxDeptName; }

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

    public Integer getNoBind() { return noBind; }
    public void setNoBind(Integer noBind) { this.noBind = noBind; }

    public String getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(String lastSyncTime) { this.lastSyncTime = lastSyncTime; }
}
