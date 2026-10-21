package com.customization.oaones.appsyn.dto;

/**
 * 考勤同步的人员候选行：同步范围圈出来的人 + 其在该租户下的平台绑定情况。
 *
 * <p>范围三要素（分部 / 部门 / 人员，OR 并集）先在 OA 侧把人圈出来，够不够格同步再看这里：
 * 只有 {@code bindState = 1} 且 {@code wxUserId} 非空的人才是“已绑定人员”；
 * 没绑定的人在同步时跳过并留下原因（{@code 未绑定平台账号，跳过}），不做猜测性匹配（需求 3.2.2）。
 * 离职 / 停用（{@code status >= 4}）的人同样跳过。</p>
 */
public class AttMember {

    private Integer oaUserId;
    private String name;
    private String loginId;
    private String workCode;
    private Integer status;
    private String wxUserId;
    private Integer bindState;
    private Integer deptId;
    private Integer subId;

    public Integer getOaUserId() { return oaUserId; }
    public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getWorkCode() { return workCode; }
    public void setWorkCode(String workCode) { this.workCode = workCode; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getWxUserId() { return wxUserId; }
    public void setWxUserId(String wxUserId) { this.wxUserId = wxUserId; }

    public Integer getBindState() { return bindState; }
    public void setBindState(Integer bindState) { this.bindState = bindState; }

    public Integer getDeptId() { return deptId; }
    public void setDeptId(Integer deptId) { this.deptId = deptId; }

    public Integer getSubId() { return subId; }
    public void setSubId(Integer subId) { this.subId = subId; }
}
