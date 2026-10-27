package com.customization.oaones.appsyn.entity.feishu;

/**
 * 飞书部门快照实体（对应业务表：表名取 oaones.appsyn.properties#oaones_dep_tmp_table，
 * 建模模块ID按表名反查，配置里不出现；主键 App ID + wxDeptId）。
 */
public class DeptTmp {

    private String corpId;
    private String wxDeptId;
    private String deptName;
    private String parentId;
    /** 快照状态：0 正常 / 1 关闭或封存。 */
    private Integer status;
    /**
     * 免绑定标记：0 = 免绑定（不参与部门绑定与同步）/ NULL = 正常（不写值）。
     *
     * <p>本表只用 0 表达“免绑定”，已取消 1 等其它状态值；快照刷新不会重置该标记。</p>
     */
    private Integer noBind;
    /** 平台类型：0 飞书 / 1 飞书。 */
    private Integer type;
    private String lastSyncTime;
    private String remark;
    private Integer formmodeid;

    public String getCorpid() { return corpId; }
    public void setCorpid(String corpId) { this.corpId = corpId; }

    public String getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(String wxDeptId) { this.wxDeptId = wxDeptId; }

    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getNoBind() { return noBind; }
    public void setNoBind(Integer noBind) { this.noBind = noBind; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

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
