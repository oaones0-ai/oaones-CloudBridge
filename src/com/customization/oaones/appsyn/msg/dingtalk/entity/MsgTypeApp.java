package com.customization.oaones.appsyn.msg.dingtalk.entity;

/**
 * 消息类型「推送目标应用」明细行（表名取 oaones.appsyn.properties#oaones_msg_type_app_table）。
 *
 * <p><b>一行 = 一个消息类型在某一个租户上绑定的推送目标应用</b>
 * （{@link #stdConfigId} / {@link #stdType} 定位消息类型，{@link #cfgId} = 租户，
 * {@link #appId} = {@code uf_oaones_appsyn_app_bind.id}）。同一个消息类型在同一个租户上只能有一行
 * （唯一键 {@code type + std_config_id + std_type + cfg_id}），也就是
 * “<b>同一个消息类型每个租户只能设置一个应用</b>”。</p>
 *
 * <p>{@link #stdConfigId} / {@link #stdType} 的口径与 {@link MsgTypeDef} 完全一致：
 * {@code stdConfigId} 非空 = 泛微标准「消息类型」ID（{@code ecology_biz_mobile_config.id}，字符串）；
 * {@code stdConfigId} 为空、{@code stdType} 非 0 = 未纳入任何标准消息类型的独立来源（按来源编号匹配）。</p>
 */
public class MsgTypeApp {

    private Integer id;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书（三个连接器共用本表）。 */
    private Integer type;
    /** 泛微标准「消息类型」ID（字符串）；空 = 未纳入标准消息类型的独立来源（按 {@link #stdType} 匹配）。 */
    private String stdConfigId;
    /** 标准消息来源编号 typecode；只对独立来源行有意义（标准类型行固定 0）。 */
    private Integer stdType;
    /** 租户ID（{@code uf_oaones_appsyn_qywx_config.id}，三个平台共用一张租户表，按 type 区分）。 */
    private Integer cfgId;
    /** 绑定的推送目标应用（{@code uf_oaones_appsyn_app_bind.id}）。 */
    private Integer appId;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    /** 标准消息类型ID（空串 = 未纳入标准消息类型的独立来源）。 */
    public String getStdConfigId() { return stdConfigId == null ? "" : stdConfigId.trim(); }
    public void setStdConfigId(String stdConfigId) { this.stdConfigId = stdConfigId; }

    public Integer getStdType() { return stdType; }
    public void setStdType(Integer stdType) { this.stdType = stdType; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public Integer getAppId() { return appId; }
    public void setAppId(Integer appId) { this.appId = appId; }

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
