package com.customization.oaones.appsyn.dto;

/**
 * 个人账号中心的 OA 账号行（hrmresource + hrmdepartment + hrmsubcompany）。
 *
 * <p>只读本人账号信息，不做人员绑定页的“有效账号 / 排除 sysadmin”过滤：
 * 登录人查看自己的账号中心时必须能看到自己（含系统管理员账号）。</p>
 */
public class OaAccountRow {

    private Integer id;
    private String lastname;
    private String loginid;
    private String mobile;
    private String email;
    private String workcode;
    private Integer departmentId;
    private String departmentName;
    private Integer subcompanyId;
    private String subcompanyName;
    private String status;
    /** 头像文件ID（hrmresource.resourceimageid），0/空表示未设置头像。 */
    private Integer resourceImageId;
    /**
     * 账户类型（hrmresource.accounttype）：0 主账号（主账户）/ 1 次账号（子账户）。
     * 页面按该字段标识“主账户 / 子账户”。
     */
    private Integer accountType;
    /**
     * 归属的主账号人员ID（hrmresource.belongto）：子账户指向其主账户；
     * 主账户自身为空（0/null）。
     */
    private Integer belongTo;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }

    public String getLoginid() { return loginid; }
    public void setLoginid(String loginid) { this.loginid = loginid; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getWorkcode() { return workcode; }
    public void setWorkcode(String workcode) { this.workcode = workcode; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Integer getSubcompanyId() { return subcompanyId; }
    public void setSubcompanyId(Integer subcompanyId) { this.subcompanyId = subcompanyId; }

    public String getSubcompanyName() { return subcompanyName; }
    public void setSubcompanyName(String subcompanyName) { this.subcompanyName = subcompanyName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getResourceImageId() { return resourceImageId; }
    public void setResourceImageId(Integer resourceImageId) { this.resourceImageId = resourceImageId; }

    public Integer getAccountType() { return accountType; }
    public void setAccountType(Integer accountType) { this.accountType = accountType; }

    public Integer getBelongTo() { return belongTo; }
    public void setBelongTo(Integer belongTo) { this.belongTo = belongTo; }
}
