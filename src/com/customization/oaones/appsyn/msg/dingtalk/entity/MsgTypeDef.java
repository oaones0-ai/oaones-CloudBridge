package com.customization.oaones.appsyn.msg.dingtalk.entity;

/**
 * 消息类型推送设置（表名取 oaones.properties#oaones_msg_type_table）。
 *
 * <p><b>一行 = 泛微标准消息类型设置里的一个“消息类型”</b>（{@link #stdConfigId} =
 * {@code ecology_biz_mobile_config.id}，由标准页
 * {@code /spa/smallApp/static4engine/engine.html#/ecAndEM/messagetypeconfig} 维护），
 * 记录本模块额外的推送设置：推不推（{@link #yx}）、推到哪个绑定的第三方应用（{@link #appId}），
 * 以及可选的推送形态 / 模板 / 卡片动作（不配就用平台默认形态）。</p>
 *
 * <p>{@link #stdType} 只对“<b>没有纳入任何标准消息类型</b>”的独立来源有意义
 * （如平台自建来源、建模“自定义提醒”这类还没在标准页归类的来源，此时 {@code stdConfigId = 0}），
 * 按 typecode 匹配推送；已归到标准消息类型的行 {@code stdConfigId} 非空、{@code stdType = 0}
 * （来源编号、名称都从标准侧实时读取，不在本表冗余，避免标准页改了之后两边不一致）。</p>
 */
public class MsgTypeDef {

    private Integer id;
    /**
     * 泛微标准「消息类型」ID（{@code ecology_biz_mobile_config.id}，<b>字符串</b>）：
     * 空 / {@code "0"} = 未纳入任何标准消息类型的独立来源（按 {@link #stdType} 匹配）。
     *
     * <p>平台这张表的 id 是 varchar：内置类型是 {@code "20"} 这类数字编号，
     * 建模 / 自定义类型是 {@code "1LbVgIrt"} 这类 8 位字符串编号，所以这里必须是字符串，
     * 否则自定义类型的设置行既存不下、也匹配不上（页面上会表现为“点启用 / 停用报错”）。</p>
     */
    private String stdConfigId;
    private String typeName;
    /**
     * 标准消息来源编号 typecode（{@code com.cloudstore.dev.api.bean.MessageType#getCode()}）。
     *
     * <p>只用于“未纳入标准消息类型”的独立来源行；已归入标准消息类型的行本列为 0。</p>
     */
    private Integer stdType;
    /** text / news / textcard / template_card / task_card。 */
    private String wxMsgtype;
    /** none / create / done / rejected / invalid。 */
    private String cardAction;
    private String titleTpl;
    private String contextTpl;
    private String urlTpl;
    private String mobileUrlTpl;
    private String coverUrl;
    private String btnJson;
    private Integer appId;
    /** 0 启用 / 1 停用。 */
    private Integer yx;
    private Integer sortNo;
    private String remark;
    /** 展示用（不落库）：所属标准消息类型名称。 */
    private String stdConfigName;
    /** 展示用（不落库）：该标准消息类型包含的消息来源名称（顿号分隔）。 */
    private String stdSourceText;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getStdConfigId() { return stdConfigId == null ? "" : stdConfigId.trim(); }
    public void setStdConfigId(String stdConfigId) { this.stdConfigId = stdConfigId; }

    /** 是否挂在标准「消息类型」上（{@code std_config_id} 非空且不是 {@code "0"}）。 */
    public boolean hasStdConfig() { return getStdConfigId().length() > 0 && !"0".equals(getStdConfigId()); }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public Integer getStdType() { return stdType; }
    public void setStdType(Integer stdType) { this.stdType = stdType; }

    public String getWxMsgtype() { return wxMsgtype; }
    public void setWxMsgtype(String wxMsgtype) { this.wxMsgtype = wxMsgtype; }

    public String getCardAction() { return cardAction; }
    public void setCardAction(String cardAction) { this.cardAction = cardAction; }

    public String getTitleTpl() { return titleTpl; }
    public void setTitleTpl(String titleTpl) { this.titleTpl = titleTpl; }

    public String getContextTpl() { return contextTpl; }
    public void setContextTpl(String contextTpl) { this.contextTpl = contextTpl; }

    public String getUrlTpl() { return urlTpl; }
    public void setUrlTpl(String urlTpl) { this.urlTpl = urlTpl; }

    public String getMobileUrlTpl() { return mobileUrlTpl; }
    public void setMobileUrlTpl(String mobileUrlTpl) { this.mobileUrlTpl = mobileUrlTpl; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getBtnJson() { return btnJson; }
    public void setBtnJson(String btnJson) { this.btnJson = btnJson; }

    public Integer getAppId() { return appId; }
    public void setAppId(Integer appId) { this.appId = appId; }

    public Integer getYx() { return yx; }
    public void setYx(Integer yx) { this.yx = yx; }

    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStdConfigName() { return stdConfigName == null ? "" : stdConfigName; }
    public void setStdConfigName(String stdConfigName) { this.stdConfigName = stdConfigName; }

    public String getStdSourceText() { return stdSourceText == null ? "" : stdSourceText; }
    public void setStdSourceText(String stdSourceText) { this.stdSourceText = stdSourceText; }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
