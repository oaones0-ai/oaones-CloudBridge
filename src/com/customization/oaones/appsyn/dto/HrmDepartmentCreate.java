package com.customization.oaones.appsyn.dto;

/**
 * OA 部门新建入参（同步中心“企微 → OA”按范围同步时，在 OA 侧新建部门用）。
 *
 * <p>只写入部门名称/简称/上级部门/分部/层级等基础字段，与 E9 平台自己的部门导入口径一致
 * （hrmdepartment.id 是自增列，不显式写入），其余字段交由 E9 默认值处理。</p>
 */
public class HrmDepartmentCreate {

    private String name;
    private String mark;
    private String code;
    private Integer subCompanyId;
    private Integer supDepId;
    private String allSupDepId;
    private Integer tlevel;
    private Integer showOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMark() { return mark; }
    public void setMark(String mark) { this.mark = mark; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getSubCompanyId() { return subCompanyId; }
    public void setSubCompanyId(Integer subCompanyId) { this.subCompanyId = subCompanyId; }

    public Integer getSupDepId() { return supDepId; }
    public void setSupDepId(Integer supDepId) { this.supDepId = supDepId; }

    public String getAllSupDepId() { return allSupDepId; }
    public void setAllSupDepId(String allSupDepId) { this.allSupDepId = allSupDepId; }

    public Integer getTlevel() { return tlevel; }
    public void setTlevel(Integer tlevel) { this.tlevel = tlevel; }

    public Integer getShowOrder() { return showOrder; }
    public void setShowOrder(Integer showOrder) { this.showOrder = showOrder; }
}
