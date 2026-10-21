package com.customization.oaones.appsyn.entity;

/**
 * 会议 / 日程同步设置实体（业务表：表名取 oaones.properties#oaones_mtg_sync_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行 = 一个「业务（会议 / 日程）+ 平台 + 租户」，唯一键 {@code (biz, type, cfgid)}：
 * 会议同步与日程同步是两条独立的一级菜单，但共用同一张设置表，用 {@code biz} 区分
 * （0 会议 / 1 日程）；同一平台租户的同一个业务只保留一行，页面保存即插入或更新。</p>
 *
 * <p>“每个租户单独设置开启和关闭”就落在这张表的 {@code enabled} 列——这是<b>唯一的业务字段</b>：
 * 同步设置页不再有同步范围 / 最近同步 / 操作列，所以表里也没有对应的列；</p>
 */
public class MtgSyncCfg {

    private Integer id;
    /** 业务类型：0 会议 / 1 日程（{@code OaonesConst.MTG_BIZ_*}）。 */
    private Integer biz;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书（{@code OaonesConst.TYPE_*}）。 */
    private Integer type;
    /** 租户配置ID（uf_oaones_qywx_config.id）。 */
    private Integer cfgid;
    /** 本租户该业务的同步开关：1 开启 / 0 关闭（“每个租户单独设置开启和关闭”）。 */
    private Integer enabled;
    /** 同步方向：OA 同步到第三方（1 选中 / 0 不选，默认选中）。 */
    private Integer dirOa2Third;
    /** 同步方向：第三方同步到 OA（1 选中 / 0 不选，默认选中）。 */
    private Integer dirThird2Oa;
    /**
     * 「绑定应用」：本租户本业务同步时调平台日程接口用的应用（“应用绑定”表 {@code uf_oaones_app_bind.id}）。
     *
     * <p>平台日程接口要求“有日程权限的应用”用自己的 Secret 换 access_token
     * （租户配置里的通讯录 Secret 通常没有日程权限），因此这里让管理员逐租户指定用哪个绑定应用：
     * {@code 0 / NULL = 未指定}，回退用租户配置里的 Secret + AgentId（与加本列之前的行为一致）。</p>
     */
    private Integer appId;
    private String remark;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getBiz() { return biz; }
    public void setBiz(Integer biz) { this.biz = biz; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public Integer getDirOa2Third() { return dirOa2Third; }
    public void setDirOa2Third(Integer dirOa2Third) { this.dirOa2Third = dirOa2Third; }

    public Integer getDirThird2Oa() { return dirThird2Oa; }
    public void setDirThird2Oa(Integer dirThird2Oa) { this.dirThird2Oa = dirThird2Oa; }

    public Integer getAppId() { return appId; }
    public void setAppId(Integer appId) { this.appId = appId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) { this.modedatamodifydatetime = modedatamodifydatetime; }
}
