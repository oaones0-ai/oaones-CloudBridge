package com.customization.oaones.appsyn.msg.std.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 泛微标准「消息类型设置 → 消息类型」的一行（只读引用，表 {@code ecology_biz_mobile_config}）。
 *
 * <p>一行 = 标准页里配置的一个“消息类型”，它通过 {@code messagetypeid} 把若干
 * {@link StdMsgSource 消息来源} 打包在一起（例如“流程待办提醒”里含新到达 / 退回 / 撤销等多个来源），
 * 并挂在某个 {@link StdMsgGroup 分组}、某个“所属模块”下。</p>
 *
 * <p><b>本模块只读标准侧的这些设置</b>：标准页新建 / 改名 / 改来源 / 删分组后，
 * 本模块的“消息类型”列表随之变化，不在本模块重复维护一份类型清单。</p>
 */
public class StdMsgType {

    /**
     * 标准消息类型ID（{@code ecology_biz_mobile_config.id}）。
     *
     * <p><b>字符串</b>：平台这张表的 id 是 varchar，内置消息类型是数字（{@code "20"}），
     * 而建模 / 自定义方式新建的消息类型用的是 8 位字符串编号（如 {@code "1LbVgIrt"}，
     * 见 {@code com.engine.msgcenter.util.CustomMessageTypeFactory}）——两种都要原样保留，
     * 所以本模块的「标准消息类型ID」一律按字符串口径处理。</p>
     */
    private String id;
    /** 消息类型名称（标准页“名称”列，多语言已按当前语言取成纯文本）。 */
    private String name;
    /** 所属分组ID（{@code ecology_biz_mobile_group.id}，0 = 未分组）。 */
    private int groupId;
    /** 所属分组名称。 */
    private String groupName;
    /** 所属模块ID（{@code ecology_message_group.id}，0 = 未指定）。 */
    private int moduleId;
    /** 所属模块名称（标准页“模块”列）。 */
    private String moduleName;
    /** 包含的消息来源（标准页“消息来源”多选，按标准页里的顺序）。 */
    private List<StdMsgSource> sources = new ArrayList<StdMsgSource>();
    /** 标准侧是否启用（{@code y} 启用 / {@code n} 停用）：仅展示，表示标准页上的开关。 */
    private String enable;
    /** 标准侧“PC 端提醒”。 */
    private String enablePc;
    /** 标准侧“移动平台提醒”。 */
    private String enableEm;
    /** 标准侧“推送到移动端（EM 客户端）”。 */
    private String sendMobile;
    /** 标准侧是否内置类型（{@code y} 内置 / {@code n} 自建）。 */
    private boolean isDefault;
    /** 标准侧对接的 EM 应用ID（{@code agentid}），仅展示。 */
    private String agentId;
    /** 图标 / 默认推送图片（标准页维护，本模块仅展示）。 */
    private String imgUrl;
    private String picUrl;
    /** 标准侧的三个消息页面地址（OA / EM PC / EM 移动端），本模块仅展示。 */
    private String pcUrl;
    private String dialogUrl;
    private String emAppUrl;

    public String getId() { return id == null ? "" : id; }

    public void setId(String id) { this.id = id == null ? "" : id.trim(); }

    public String getName() { return name == null ? "" : name; }

    public void setName(String name) { this.name = name; }

    public int getGroupId() { return groupId; }

    public void setGroupId(int groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName == null ? "" : groupName; }

    public void setGroupName(String groupName) { this.groupName = groupName; }

    public int getModuleId() { return moduleId; }

    public void setModuleId(int moduleId) { this.moduleId = moduleId; }

    public String getModuleName() { return moduleName == null ? "" : moduleName; }

    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public List<StdMsgSource> getSources() { return sources == null ? new ArrayList<StdMsgSource>() : sources; }

    public void setSources(List<StdMsgSource> sources) { this.sources = sources; }

    public String getEnable() { return enable == null ? "" : enable; }

    public void setEnable(String enable) { this.enable = enable; }

    public String getEnablePc() { return enablePc == null ? "" : enablePc; }

    public void setEnablePc(String enablePc) { this.enablePc = enablePc; }

    public String getEnableEm() { return enableEm == null ? "" : enableEm; }

    public void setEnableEm(String enableEm) { this.enableEm = enableEm; }

    public String getSendMobile() { return sendMobile == null ? "" : sendMobile; }

    public void setSendMobile(String sendMobile) { this.sendMobile = sendMobile; }

    public boolean isDefault() { return isDefault; }

    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public String getAgentId() { return agentId == null ? "" : agentId; }

    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getImgUrl() { return imgUrl == null ? "" : imgUrl; }

    public void setImgUrl(String imgUrl) { this.imgUrl = imgUrl; }

    public String getPicUrl() { return picUrl == null ? "" : picUrl; }

    public void setPicUrl(String picUrl) { this.picUrl = picUrl; }

    public String getPcUrl() { return pcUrl == null ? "" : pcUrl; }

    public void setPcUrl(String pcUrl) { this.pcUrl = pcUrl; }

    public String getDialogUrl() { return dialogUrl == null ? "" : dialogUrl; }

    public void setDialogUrl(String dialogUrl) { this.dialogUrl = dialogUrl; }

    public String getEmAppUrl() { return emAppUrl == null ? "" : emAppUrl; }

    public void setEmAppUrl(String emAppUrl) { this.emAppUrl = emAppUrl; }

    /** 该类型是否包含某个来源编号（推送时按它把标准消息对应回类型）。 */
    public boolean containsTypeCode(int typeCode) {
        if (typeCode <= 0) {
            return false;
        }
        for (StdMsgSource source : getSources()) {
            if (source != null && source.getTypeCode() == typeCode) {
                return true;
            }
        }
        return false;
    }

    /** 展示用：包含的来源编号列表（{@code 20,21,25}）。 */
    public String typeCodeText() {
        StringBuilder sb = new StringBuilder();
        for (StdMsgSource source : getSources()) {
            if (source == null || source.getTypeCode() <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(source.getTypeCode());
        }
        return sb.toString();
    }

    /** 展示用：包含的来源名称列表（顿号分隔）。 */
    public String sourceNameText() {
        StringBuilder sb = new StringBuilder();
        for (StdMsgSource source : getSources()) {
            if (source == null) {
                continue;
            }
            String text = source.getName();
            if (text.length() == 0) {
                text = "消息来源 " + source.getTypeCode();
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(text);
        }
        return sb.toString();
    }

    /** 展示用：来源ID列表（{@code 12,13}）。 */
    public String sourceIdText() {
        StringBuilder sb = new StringBuilder();
        for (StdMsgSource source : getSources()) {
            if (source == null || source.getId() <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(source.getId());
        }
        return sb.toString();
    }
}
