package com.customization.oaones.appsyn.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 「会议 / 日程同步」的**第三方平台侧**对象（平台日程 / 日历事件拉取结果）。
 *
 * <p>平台适配器（企微 / 钉钉 / 飞书）把各自接口返回的日程归一化成这一份结构，
 * 交给拉取引擎（第三方→OA）使用：按 {@code wxId} 判断是“平台新建（在 OA 新建）”还是
 * “此前从 OA 推过去的（更新 OA 数据）”。</p>
 */
public class MtgPlatformItem {

    /** 平台侧对象ID（日程 / 事件 ID）。 */
    private String wxId;
    /** 标题。 */
    private String title;
    /** 正文 / 说明。 */
    private String content;
    /** 开始时间 yyyy-MM-dd HH:mm:ss。 */
    private String beginTime;
    /** 结束时间 yyyy-MM-dd HH:mm:ss。 */
    private String endTime;
    /** 地点（可空）。 */
    private String location;
    /** 组织者平台成员ID（用于换算成 OA 创建人；为空时用第一个参与人）。 */
    private String ownerWxId;
    /**
     * 平台侧日程状态：企微 {@code status} 0-正常 / 1-已取消（其它平台取不到时留空）。
     *
     * <p>订阅回调与删除对账据此把“已取消”按删除处理（见 {@code OaonesConst.MTG_EVENT_DELETE}）。</p>
     */
    private Integer status;
    /** 参与人平台成员ID 列表。 */
    private List<String> attendeeWxIds = new ArrayList<String>();

    public String getWxId() { return wxId; }
    public void setWxId(String wxId) { this.wxId = wxId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getBeginTime() { return beginTime; }
    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getOwnerWxId() { return ownerWxId; }
    public void setOwnerWxId(String ownerWxId) { this.ownerWxId = ownerWxId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public List<String> getAttendeeWxIds() { return attendeeWxIds; }
    public void setAttendeeWxIds(List<String> attendeeWxIds) {
        this.attendeeWxIds = attendeeWxIds == null ? new ArrayList<String>() : attendeeWxIds;
    }
}
