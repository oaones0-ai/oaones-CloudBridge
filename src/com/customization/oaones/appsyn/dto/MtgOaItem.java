package com.customization.oaones.appsyn.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 「会议同步 / 日程同步」的 OA 侧来源对象（多表关联查询结果）。
 *
 * <p>会议取 E9 {@code Meeting} 表、日程取 E9 {@code WorkPlan} 表（表名在
 * {@code oaones.properties} 里可配，见 {@code OaonesProps}），两张表字段不同，
 * 这里统一归一化成同一个载体后再交给同步引擎：标题 / 正文 / 起止时间 / 所属分部 / 创建人 /
 * 参与人，平台适配器只认这一份结构。</p>
 *
 * <p>该类只承载查询结果，不落库（落库走 {@code entity.MtgSyncLog}）。</p>
 */
public class MtgOaItem {

    /** OA 侧对象ID：会议ID / 日程ID。 */
    private String oaId;
    /** 标题：会议名 / 日程名。 */
    private String title;
    /** 正文 / 说明。 */
    private String content;
    /** 开始时间 yyyy-MM-dd HH:mm:ss。 */
    private String beginTime;
    /** 结束时间 yyyy-MM-dd HH:mm:ss。 */
    private String endTime;
    /** 所属 OA 分部ID（会议取 hrmSubCompanys 归一后的第一个分部；日程取 subcompanyId）。 */
    private String subId;
    /** 所属 OA 分部名称（同步引擎回填，用于记录展示）。 */
    private String subName;
    /** 所属 OA 部门ID（可空）。 */
    private String deptId;
    /** 所属 OA 部门名称（可空）。 */
    private String deptName;
    /** 创建人 OA 人员ID（会议 creater / 日程 createrid）。 */
    private String ownerId;
    /** 地点（可空）。 */
    private String location;
    /**
     * 该对象是否已删除 / 已取消：null / 0 = 正常，1 = 已删除（会议 {@code cancel = '1'}、
     * 日程 {@code deleted = '1'}）。
     *
     * <p>“删除对账”用：按 OA 对象ID 取单条时**不过滤删除标记**，据本字段判断
     * “OA 侧删了 → 平台侧也要删”。列表查询（{@code listMeetings} / {@code listSchedules}）
     * 仍然过滤掉已删除的对象，不会把它们当成同步候选。</p>
     */
    private Integer deleted;
    /** 参与人 OA 人员ID 列表（会议取 Meeting_Member2、日程取 workplanMember）。 */
    private List<String> attendeeIds = new ArrayList<String>();
    /**
     * 会议原始的分部ID串（Meeting.hrmSubCompanys，逗号分隔）。
     *
     * <p>会议可能被多个分部共用，包含任一在同步范围内的分部即算命中，
     * 判定在同步引擎里做，因此这里把原始串带出来。</p>
     */
    private String rawSubIds;

    public String getOaId() { return oaId; }
    public void setOaId(String oaId) { this.oaId = oaId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getBeginTime() { return beginTime; }
    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getSubId() { return subId; }
    public void setSubId(String subId) { this.subId = subId; }

    public String getSubName() { return subName; }
    public void setSubName(String subName) { this.subName = subName; }

    public String getDeptId() { return deptId; }
    public void setDeptId(String deptId) { this.deptId = deptId; }

    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public List<String> getAttendeeIds() { return attendeeIds; }
    public void setAttendeeIds(List<String> attendeeIds) { this.attendeeIds = attendeeIds; }

    public String getRawSubIds() { return rawSubIds; }
    public void setRawSubIds(String rawSubIds) { this.rawSubIds = rawSubIds; }
}
