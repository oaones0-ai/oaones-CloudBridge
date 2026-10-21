package com.customization.oaones.appsyn.entity;

/**
 * 「会议 / 日程同步」第三方向拉取（平台 → OA）时写入 / 更新 OA 侧对象（E9 `Meeting` / `WorkPlan`）的载体。
 *
 * <p>不是业务表实体，只承载一次写入的字段；会议与日程字段不同，这里归一化成一份，
 * 由 Mapper 分别映射到各自列（如会议 {@code desc_n/description} 与日程 {@code description}）。</p>
 *
 * <ul>
 *   <li>新建时 {@code id} 由 {@code selectKey} 从自增列回填（Meeting / WorkPlan 的 id 都是 IDENTITY）；</li>
 *   <li>{@code ownerId} 是换算出来的 OA 创建人（平台组织者按人员绑定换出）；</li>
 *   <li>{@code subId}/{@code deptId} 取创建人所在分部 / 部门（会议写 hrmSubCompanys/hrmDepartments，日程写 subcompanyId/deptId）。</li>
 * </ul>
 */
public class MtgOaWrite {

    /** OA 对象ID（新建时由自增列回填；更新时是已有记录ID）。 */
    private Integer id;
    private String title;
    private String content;
    private String location;
    private String beginDate;
    private String beginTime;
    private String endDate;
    private String endTime;
    /** yyyy-MM-dd HH:mm:ss（会议表的 beginDateTime / endDateTime）。 */
    private String beginDateTime;
    private String endDateTime;
    /** OA 创建人人员ID。 */
    private Integer ownerId;
    /** 所属 OA 分部ID。 */
    private Integer subId;
    /** 所属 OA 部门ID。 */
    private Integer deptId;
    /** 参与人数量（会议 totalmember）。 */
    private Integer memberCount;
    /** 参与人 OA 人员ID 串（日程 WorkPlan.resourceid，逗号分隔）。 */
    private String resourceId;
    private String createDate;
    private String createTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getBeginDate() { return beginDate; }
    public void setBeginDate(String beginDate) { this.beginDate = beginDate; }

    public String getBeginTime() { return beginTime; }
    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getBeginDateTime() { return beginDateTime; }
    public void setBeginDateTime(String beginDateTime) { this.beginDateTime = beginDateTime; }

    public String getEndDateTime() { return endDateTime; }
    public void setEndDateTime(String endDateTime) { this.endDateTime = endDateTime; }

    public Integer getOwnerId() { return ownerId; }
    public void setOwnerId(Integer ownerId) { this.ownerId = ownerId; }

    public Integer getSubId() { return subId; }
    public void setSubId(Integer subId) { this.subId = subId; }

    public Integer getDeptId() { return deptId; }
    public void setDeptId(Integer deptId) { this.deptId = deptId; }

    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getCreateDate() { return createDate; }
    public void setCreateDate(String createDate) { this.createDate = createDate; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
