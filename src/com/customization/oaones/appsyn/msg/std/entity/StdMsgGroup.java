package com.customization.oaones.appsyn.msg.std.entity;

/**
 * 泛微标准「消息类型设置 → 分组设置」的一行（只读引用，表 {@code ecology_biz_mobile_group}）。
 *
 * <p>与标准页 {@code /spa/smallApp/static4engine/engine.html#/ecAndEM/messagetypeconfig}
 * 的“分组设置”页签完全同一份数据：分组维护在标准页，本模块只读引用并在列表里按它分组展示。</p>
 */
public class StdMsgGroup {

    /** 分组ID（{@code ecology_biz_mobile_group.id}）。 */
    private int id;
    /** 分组名称（多语言标记串已按当前语言取成纯文本）。 */
    private String name;
    /** 排序号（标准页“排序”列，越小越靠前）。 */
    private int showNum;
    /** 标准侧状态：{@code n} 为新增/正常（与标准 GroupingCmd 一致），其它值原样透出。 */
    private String status;

    public int getId() { return id; }

    public void setId(int id) { this.id = id; }

    public String getName() { return name == null ? "" : name; }

    public void setName(String name) { this.name = name; }

    public int getShowNum() { return showNum; }

    public void setShowNum(int showNum) { this.showNum = showNum; }

    public String getStatus() { return status == null ? "" : status; }

    public void setStatus(String status) { this.status = status; }
}
