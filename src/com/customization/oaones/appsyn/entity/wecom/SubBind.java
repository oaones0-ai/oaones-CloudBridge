package com.customization.oaones.appsyn.entity.wecom;

/**
 * OA分部 - 企微租户 绑定实体（对应业务表：表名取 oaones.properties#oaones_sub_bind_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>分部绑定只登记“该 OA 分部归属哪个企微租户”（cfg_id），企微部门侧不再重复存储：
 * 该分部对应的企微部门取租户配置（uf_oaones_qywx_config）中维护的根部门。
 * 与部门绑定（{@link DeptBind}）的区别：分部到企微租户为“一对一”，
 * 同一企微租户只允许被一个 OA 分部绑定；部门绑定仍是“多对一”。</p>
 */
public class SubBind {

    private Integer id;
    private Integer cfgId;
    private Integer oaSubId;
    /**
     * 该分部在企微侧对应的部门ID（企微只有部门概念，没有分部）。
     *
     * <p>老的“分部绑定”页只登记 分部 → 企微租户，企微部门按租户根部门口径，本列为空；
     * “同步中心 → 按范围同步”（A = OA 分部）会为该分部在企微新建一个部门并把ID写在这里，
     * 该分部下的部门挂在这个部门下面。</p>
     */
    private Integer wxDeptId;
    /**
     * 绑定状态（物理列 yx）：0 有效 / 1 无效。
     *
     * <p>注意与部门绑定 {@link DeptBind#getYx()}（1 有效 / 0 失效）口径不同，
     * 本表按建模枚举序号从 0 开始，见 {@code OaonesConst.SUB_BIND_STATE_VALID}。</p>
     */
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

    public Integer getWxDeptId() { return wxDeptId; }
    public void setWxDeptId(Integer wxDeptId) { this.wxDeptId = wxDeptId; }

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
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
