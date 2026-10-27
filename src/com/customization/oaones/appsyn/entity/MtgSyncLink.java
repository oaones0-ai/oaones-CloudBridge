package com.customization.oaones.appsyn.entity;

/**
 * 会议 / 日程同步「对象映射」实体（业务表：表名取 oaones.appsyn.properties#oaones_mtg_link_table）。
 *
 * <p>一行 = 一个「业务（biz）+ 平台（type）+ 租户（cfgid）」下的
 * <b>OA 对象ID</b>（Meeting.id / WorkPlan.id）与 <b>第三方平台对象ID</b>（日程 / 事件 ID）的映射，
 * 唯一键 {@code (biz, type, cfgid, wx_id)} 与 {@code (biz, type, cfgid, oa_id)}。</p>
 *
 * <ul>
 *   <li>{@code source = 0}：由「OA→第三方」推送建立（OA 是数据源）；</li>
 *   <li>{@code source = 1}：由「第三方→OA」拉取建立（平台是数据源）——
 *       推送引擎据此跳过这些 OA 记录，避免把平台已有的事件又推一遍造成重复。</li>
 * </ul>
 *
 * <p>拉取（第三方→OA）按 {@code wx_id} 找到映射：有映射 → 更新对应 OA 记录（{@code oa_id}）；
 * 没有映射 → 在 OA 新建记录并写一条 {@code source = 1} 的映射。</p>
 *
 * <p><b>顺带说明“变了才同步”用的两列内容快照</b>（{@code oa_hash} / {@code wx_hash}）：
 * OA 创建的对象（{@code source = 0}）推送成功后记 {@code oa_hash}，下次比对“OA 改了没”；
 * 平台创建的对象（{@code source = 1}）把平台数据写进 OA 后记 {@code oa_hash}。拉取时无论哪个来源
 * 都会记 {@code wx_hash}：{@code source = 1} 用它判断“平台改了才更新 OA”，
 * {@code source = 0} 用它判断“平台侧改过了”，只在同步记录里留一条“不反向更新”的说明。
 * 两列为 NULL 表示还没建基线：该轮只记基线，不重推 / 不重写。</p>
 */
public class MtgSyncLink {

    private Integer id;
    /** 业务类型：0 会议 / 1 日程（{@code OaonesConst.MTG_BIZ_*}）。 */
    private Integer biz;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书（{@code OaonesConst.TYPE_*}）。 */
    private Integer type;
    /** 租户配置ID（uf_oaones_appsyn_qywx_config.id）。 */
    private Integer cfgid;
    /** OA 侧对象ID（Meeting.id / WorkPlan.id）。 */
    private String oaId;
    /** 平台侧对象ID（日程 / 事件 ID）。 */
    private String wxId;
    /** 来源：0 由 OA→第三方推送建立 / 1 由第三方→OA 拉取建立。 */
    private Integer source;
    /** OA 侧内容快照（上次同步时的 OA 内容指纹；NULL = 还没建基线）。 */
    private String oaHash;
    /** 平台侧内容快照（上次同步时的平台内容指纹；NULL = 还没建基线）。 */
    private String wxHash;
    /** 最近一次从第三方拉取（更新 OA）的时间。 */
    private String lastPullTime;
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

    public String getOaId() { return oaId; }
    public void setOaId(String oaId) { this.oaId = oaId; }

    public String getWxId() { return wxId; }
    public void setWxId(String wxId) { this.wxId = wxId; }

    public Integer getSource() { return source; }
    public void setSource(Integer source) { this.source = source; }

    public String getOaHash() { return oaHash; }
    public void setOaHash(String oaHash) { this.oaHash = oaHash; }

    public String getWxHash() { return wxHash; }
    public void setWxHash(String wxHash) { this.wxHash = wxHash; }

    public String getLastPullTime() { return lastPullTime; }
    public void setLastPullTime(String lastPullTime) { this.lastPullTime = lastPullTime; }

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
