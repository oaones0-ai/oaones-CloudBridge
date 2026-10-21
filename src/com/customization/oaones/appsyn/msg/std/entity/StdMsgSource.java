package com.customization.oaones.appsyn.msg.std.entity;

/**
 * 泛微标准「消息类型设置 → 消息来源」的一行（只读引用，表 {@code ecology_message_type}）。
 *
 * <p>一行 = 一种标准消息来源（如“新到达流程”），{@link #typeCode} 就是标准消息
 * {@code MessageBean#getMessageType()#getCode()} 的值；本模块用它把收到的标准消息
 * 对应回标准页里配置的“消息类型”。</p>
 */
public class StdMsgSource {

    /** 来源ID（{@code ecology_message_type.id}，标准「消息类型」的 {@code messagetypeid} 存的是它）。 */
    private int id;
    /** 来源编号 typecode（标准消息回调里带的类型码）。 */
    private int typeCode;
    /** 来源名称（标准页“消息来源”列的名称）。 */
    private String name;
    /** 所属模块ID（{@code ecology_message_group.id}）。 */
    private int moduleId;
    /** 所属模块名称。 */
    private String moduleName;
    /** 标准侧是否内置来源（{@code y} 内置、{@code n} 自建）。 */
    private boolean isDefault;

    public int getId() { return id; }

    public void setId(int id) { this.id = id; }

    public int getTypeCode() { return typeCode; }

    public void setTypeCode(int typeCode) { this.typeCode = typeCode; }

    public String getName() { return name == null ? "" : name; }

    public void setName(String name) { this.name = name; }

    public int getModuleId() { return moduleId; }

    public void setModuleId(int moduleId) { this.moduleId = moduleId; }

    public String getModuleName() { return moduleName == null ? "" : moduleName; }

    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public boolean isDefault() { return isDefault; }

    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    /** 展示用：{@code 来源名称(typecode)}，取不到名称时退化成 {@code 消息来源 <typecode>}。 */
    public String getDisplay() {
        String text = getName();
        if (text.length() == 0) {
            text = "消息来源 " + typeCode;
        }
        return typeCode > 0 ? text + "(" + typeCode + ")" : text;
    }
}
