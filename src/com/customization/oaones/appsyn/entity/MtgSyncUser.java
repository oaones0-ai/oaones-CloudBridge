package com.customization.oaones.appsyn.entity;

/**
 * 会议 / 日程同步的「个人同步开关」实体（业务表：表名取
 * oaones.properties#oaones_mtg_sync_user_table，建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行 = 一个「业务（biz 0 会议 / 1 日程）+ 平台（type 0 企微 / 1 钉钉 / 2 飞书）
 * + 租户（cfgid）+ OA 人员（oa_user_id）」的个人选择，唯一键
 * {@code (biz, type, cfgid, oa_user_id)}：</p>
 *
 * <ul>
 *   <li>{@code enabled = 1}：本人允许把自己相关（本人创建、本人参与）的会议 / 日程
 *       同步到该平台租户；</li>
 *   <li>{@code enabled = 0}：本人选择「不同步到第三方平台」。</li>
 * </ul>
 *
 * <p><b>默认（表里没有行）= 允许同步</b>：租户在后台开启同步后，默认所有人同步会议和日程；
 * 只有个人显式点过「不同步」才会落一行 {@code enabled = 0}。因此后台把租户同步<b>关闭再开启</b>时，
 * 个人这条选择不受租户开关影响，个人账号中心仍显示「不同步」——
 * 真正同步的条件是「租户开启 <b>且</b> 个人允许」。</p>
 */
public class MtgSyncUser {

    private Integer id;
    /** 业务类型：0 会议 / 1 日程（{@code OaonesConst.MTG_BIZ_*}）。 */
    private Integer biz;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书（{@code OaonesConst.TYPE_*}）。 */
    private Integer type;
    /** 租户配置ID（uf_oaones_qywx_config.id）。 */
    private Integer cfgid;
    /** OA 人员ID（本人）。 */
    private Integer oaUserId;
    /** 个人同步开关：1 允许同步 / 0 不同步（默认没有行 = 允许）。 */
    private Integer enabled;
    /** 个人同步方向口径：0 遵循系统设置（默认）/ 1 指定同步方向（{@code OaonesConst.MTG_DIR_MODE_*}）。 */
    private Integer dirMode;
    /** 「指定同步方向」时：OA 同步到第三方（1 选中 / 0 不选，默认 1）。 */
    private Integer dirOa2Third;
    /** 「指定同步方向」时：第三方同步到 OA（1 选中 / 0 不选，默认 1）。 */
    private Integer dirThird2Oa;
    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;
    /** 关联查询字段：租户名称（uf_oaones_qywx_config.corp_name，只用于列表展示）。 */
    private String cfgName;
    /** 关联查询字段：OA 人员姓名（hrmresource.lastname，只用于列表展示）。 */
    private String oaUserName;
    /** 关联查询字段：OA 登录名（hrmresource.loginid，只用于列表展示）。 */
    private String oaUserLoginid;
    /** 关联查询字段：OA 人员所在分部ID（hrmresource.subcompanyid1，只用于列表展示与筛选）。 */
    private Integer oaSubId;
    /** 关联查询字段：OA 人员所在分部名称（hrmsubcompany.subcompanyname，只用于列表展示）。 */
    private String oaSubName;
    /** 关联查询字段：OA 人员所在部门ID（hrmresource.departmentid，只用于列表展示与筛选）。 */
    private Integer oaDeptId;
    /** 关联查询字段：OA 人员所在部门名称（hrmdepartment.departmentname，只用于列表展示）。 */
    private String oaDeptName;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getBiz() { return biz; }
    public void setBiz(Integer biz) { this.biz = biz; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public Integer getDirMode() { return dirMode; }
    public void setDirMode(Integer dirMode) { this.dirMode = dirMode; }

    public Integer getDirOa2Third() { return dirOa2Third; }
    public void setDirOa2Third(Integer dirOa2Third) { this.dirOa2Third = dirOa2Third; }

    public Integer getDirThird2Oa() { return dirThird2Oa; }
    public void setDirThird2Oa(Integer dirThird2Oa) { this.dirThird2Oa = dirThird2Oa; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public String getOaUserName() { return oaUserName; }
    public void setOaUserName(String oaUserName) { this.oaUserName = oaUserName; }

    public String getOaUserLoginid() { return oaUserLoginid; }
    public void setOaUserLoginid(String oaUserLoginid) { this.oaUserLoginid = oaUserLoginid; }

    public Integer getOaSubId() { return oaSubId; }
    public void setOaSubId(Integer oaSubId) { this.oaSubId = oaSubId; }

    public String getOaSubName() { return oaSubName; }
    public void setOaSubName(String oaSubName) { this.oaSubName = oaSubName; }

    public Integer getOaDeptId() { return oaDeptId; }
    public void setOaDeptId(Integer oaDeptId) { this.oaDeptId = oaDeptId; }

    public String getOaDeptName() { return oaDeptName; }
    public void setOaDeptName(String oaDeptName) { this.oaDeptName = oaDeptName; }
}
