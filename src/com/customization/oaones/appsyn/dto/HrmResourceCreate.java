package com.customization.oaones.appsyn.dto;

/**
 * OA 人员新建入参（“企微数据基准”页把企微成员同步到 OA 时使用）。
 *
 * <p>只写入登录名/密码/姓名/手机/邮箱/部门/分部等基础字段，
 * 其余字段交由 E9 平台默认值处理，避免破坏 OA 标准表结构。</p>
 */
public class HrmResourceCreate {

    private Integer id;
    private String loginid;
    private String password;
    private String lastname;
    private String mobile;
    private String email;
    private Integer departmentId;
    private Integer subcompanyId;
    private Integer status;
    private Integer accountType;
    private Integer systemLanguage;
    private Integer createrId;
    private String createDate;
    private Integer lastModId;
    private String lastModDate;
    private String lastLoginDate;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getLoginid() { return loginid; }
    public void setLoginid(String loginid) { this.loginid = loginid; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

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

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getAccountType() { return accountType; }
    public void setAccountType(Integer accountType) { this.accountType = accountType; }

    public Integer getSystemLanguage() { return systemLanguage; }
    public void setSystemLanguage(Integer systemLanguage) { this.systemLanguage = systemLanguage; }

    public Integer getCreaterId() { return createrId; }
    public void setCreaterId(Integer createrId) { this.createrId = createrId; }

    public String getCreateDate() { return createDate; }
    public void setCreateDate(String createDate) { this.createDate = createDate; }

    public Integer getLastModId() { return lastModId; }
    public void setLastModId(Integer lastModId) { this.lastModId = lastModId; }

    public String getLastModDate() { return lastModDate; }
    public void setLastModDate(String lastModDate) { this.lastModDate = lastModDate; }

    public String getLastLoginDate() { return lastLoginDate; }
    public void setLastLoginDate(String lastLoginDate) { this.lastLoginDate = lastLoginDate; }
}
