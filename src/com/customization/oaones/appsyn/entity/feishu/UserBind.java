package com.customization.oaones.appsyn.entity.feishu;

/**
 * OA人员 - 飞书成员 绑定实体（对应业务表：表名取 oaones.appsyn.properties#oaones_user_bind_table，
 * 建模模块ID按表名反查，配置里不出现）。
 */
public class UserBind {

    private Integer id;
    private Integer cfgId;
    private Integer oaUserId;
    /** 飞书成员 userid（物理列 x_user_id）。 */
    private String xUserId;
    private String wxDeptId;
    /**
     * 绑定状态：0 免绑定 / 1 已绑定。
     *
     * <p>本表只落两类行：已绑定（有 {@code x_user_id}）与免绑定（{@code bind_state=0}、无 {@code x_user_id}）。
     * “待绑定/未绑定”的 OA 人员不落库，由页面每次加载时按 OA 人员与绑定记录在代码中判定。</p>
     */
    private Integer bindState;
    /** 绑定方式：0 手动绑定 / 1 系统（默认规则）绑定。 */
    private Integer bindSource;
    private String bindTime;
    /** 最近一次同步方向：0 OA->飞书 / 1 飞书->OA。 */
    private Integer lastSyncDirection;
    private String lastSyncTime;
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

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getXUserId() { return xUserId; }
    public void setXUserId(String xUserId) { this.xUserId = xUserId; }

    public String getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(String wxDeptId) { this.wxDeptId = wxDeptId; }

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
