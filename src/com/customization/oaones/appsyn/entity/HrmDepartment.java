package com.customization.oaones.appsyn.entity;

/**
 * OA 标准部门实体（hrmdepartment + hrmsubcompany 关联结果，只读）。
 */
public class HrmDepartment {

    private Integer id;
    private String name;
    private Integer parent;
    private Integer subcompanyid1;
    private String subcompanyName;
    private String canceled;
    private Integer showorder;
    /** 上级部门路径（hrmdepartment.allsupdepid，本环境多为空）。 */
    private String allSupDepId;
    /** 部门层级（hrmdepartment.tlevel，根层为 1）。 */
    private Integer tlevel;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getParent() { return parent; }
    public void setParent(Integer parent) { this.parent = parent; }

    public Integer getSubcompanyid1() { return subcompanyid1; }
    public void setSubcompanyid1(Integer subcompanyid1) { this.subcompanyid1 = subcompanyid1; }

    public String getSubcompanyName() { return subcompanyName; }
    public void setSubcompanyName(String subcompanyName) { this.subcompanyName = subcompanyName; }

    public String getCanceled() { return canceled; }
    public void setCanceled(String canceled) { this.canceled = canceled; }

    public Integer getShoworder() { return showorder; }
    public void setShoworder(Integer showorder) { this.showorder = showorder; }

    public String getAllSupDepId() { return allSupDepId; }
    public void setAllSupDepId(String allSupDepId) { this.allSupDepId = allSupDepId; }

    public Integer getTlevel() { return tlevel; }
    public void setTlevel(Integer tlevel) { this.tlevel = tlevel; }
}
