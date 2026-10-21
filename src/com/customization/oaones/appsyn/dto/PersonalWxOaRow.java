package com.customization.oaones.appsyn.dto;

/**
 * 个人微信绑定列表里的“OA 人员”行（hrmresource + hrmdepartment 查询结果）。
 *
 * <p>与人员绑定页的 OA 人员口径有一处**刻意不同**：本列表**包含系统管理员账号**
 * （登录名通常是 {@code sysadmin}）——系统管理员同样可以绑定一个微信号
 * （见「微信设置」页与个人账号中心的“个人微信”页签）；
 * 其余口径一致：只取有效账号（{@code status < 4}）。</p>
 *
 * <p>另一处差别：本列表**不显示子账户**（E9 标准 {@code hrmresource.accounttype = 1}），
 * 子账户既不参与绑定列表的统计，也不允许绑定个人微信
 * （{@code PersonalWxMapper#listOaUsers} 直接把子账户过滤掉；
 * {@code PersonalWxServiceImpl#assertBindableOaUser} 在绑定时再兜一层）。
 * 因此这里的 {@link #accountType} 只用于绑定前校验与提示，列表里的行都是主账户。</p>
 */
public class PersonalWxOaRow {

    private Integer id;
    private String lastname;
    private String loginid;
    private String workcode;
    private String mobile;
    private Integer departmentId;
    private String departmentName;
    private Integer subcompanyId;
    private String subcompanyName;
    private Integer status;
    /** 账户类型（hrmresource.accounttype）：0（或空）主账户 / 1 子账户（次账号）。 */
    private Integer accountType;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }

    public String getLoginid() { return loginid; }
    public void setLoginid(String loginid) { this.loginid = loginid; }

    public String getWorkcode() { return workcode; }
    public void setWorkcode(String workcode) { this.workcode = workcode; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Integer getSubcompanyId() { return subcompanyId; }
    public void setSubcompanyId(Integer subcompanyId) { this.subcompanyId = subcompanyId; }

    public String getSubcompanyName() { return subcompanyName; }
    public void setSubcompanyName(String subcompanyName) { this.subcompanyName = subcompanyName; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getAccountType() { return accountType; }
    public void setAccountType(Integer accountType) { this.accountType = accountType; }
}
