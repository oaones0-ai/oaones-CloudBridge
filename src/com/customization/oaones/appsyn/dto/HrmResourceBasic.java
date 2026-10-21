package com.customization.oaones.appsyn.dto;

/**
 * OA 人员基础信息回写入参（仅姓名/手机/邮箱/部门/分部，动态更新非空字段）。
 */
public class HrmResourceBasic {

    private Integer id;
    private String lastname;
    private String mobile;
    private String email;
    private Integer departmentId;
    private Integer subcompanyId;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public Integer getSubcompanyId() { return subcompanyId; }
    public void setSubcompanyId(Integer subcompanyId) { this.subcompanyId = subcompanyId; }
}
