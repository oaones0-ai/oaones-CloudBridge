package com.customization.oaones.appsyn.entity;

/**
 * 会议 / 日程同步记录实体（业务表：表名取 oaones.appsyn.properties#oaones_mtg_sync_log_table）。
 *
 * <p>一行 = 一个 OA 会议 / 日程 在某次同步里的一个动作（新增 / 更新 / 跳过 / 失败），
 * 会议与日程共用一张表，用 {@code biz} 区分（0 会议 / 1 日程）；「同步记录」列表按它展示
 * （一条记录一行，见页面“同步记录”页签）。一次同步用同一个 {@code batchId} 串联，
 * 便于按批次查看“这次一共同步了哪些”。</p>
 *
 * <p>该表只做历史留痕，不参与任何权限判定；按 {@code type + cfgid} 与租户绑定，
 * 页面读取时按登录人可维护租户过滤。</p>
 */
public class MtgSyncLog {

    private Integer id;
    /** 业务类型：0 会议 / 1 日程（{@code OaonesConst.MTG_BIZ_*}）。 */
    private Integer biz;
    /** 平台类型：0 企微 / 1 钉钉 / 2 飞书（{@code OaonesConst.TYPE_*}）。 */
    private Integer type;
    /** 租户配置ID（uf_oaones_appsyn_qywx_config.id）。 */
    private Integer cfgid;
    /** 一次同步的批次ID（同一批记录共用一个值，页面按批查看）。 */
    private String batchId;
    /** OA 侧对象ID：会议ID / 日程ID。 */
    private String oaId;
    /** OA 侧对象标题（会议名 / 日程名）。 */
    private String oaTitle;
    /** 来源 OA 分部ID（同步范围里的分部）。 */
    private String oaSubId;
    /** 来源 OA 分部名称快照。 */
    private String oaSubName;
    /** 来源 OA 部门ID（会议/日程所属部门，可空）。 */
    private String oaDeptId;
    /** 来源 OA 部门名称快照。 */
    private String oaDeptName;
    /** 开始时间 yyyy-MM-dd HH:mm:ss。 */
    private String beginTime;
    /** 结束时间 yyyy-MM-dd HH:mm:ss。 */
    private String endTime;
    /** 平台侧对象ID（推送到第三方平台后返回的日程 / 事件 ID）。 */
    private String wxId;
    /** 动作：0 新增 / 1 更新 / 2 跳过 / 3 失败（{@code OaonesConst.SYNC_OP_*}）。 */
    private Integer op;
    /** 过程 / 结果说明（失败原因、跳过原因、平台返回信息）。 */
    private String message;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    /** 联表查询带出的租户名称（只读展示字段，不落库）。 */
    private String cfgName;

    /** 「同步清单」联表带出的累计同步次数（第一次 + 每次重推，只读展示字段，不落库）。 */
    private Integer tryCount;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getBiz() { return biz; }
    public void setBiz(Integer biz) { this.biz = biz; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgid() { return cfgid; }
    public void setCfgid(Integer cfgid) { this.cfgid = cfgid; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getOaId() { return oaId; }
    public void setOaId(String oaId) { this.oaId = oaId; }

    public String getOaTitle() { return oaTitle; }
    public void setOaTitle(String oaTitle) { this.oaTitle = oaTitle; }

    public String getOaSubId() { return oaSubId; }
    public void setOaSubId(String oaSubId) { this.oaSubId = oaSubId; }

    public String getOaSubName() { return oaSubName; }
    public void setOaSubName(String oaSubName) { this.oaSubName = oaSubName; }

    public String getOaDeptId() { return oaDeptId; }
    public void setOaDeptId(String oaDeptId) { this.oaDeptId = oaDeptId; }

    public String getOaDeptName() { return oaDeptName; }
    public void setOaDeptName(String oaDeptName) { this.oaDeptName = oaDeptName; }

    public String getBeginTime() { return beginTime; }
    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getWxId() { return wxId; }
    public void setWxId(String wxId) { this.wxId = wxId; }

    public Integer getOp() { return op; }
    public void setOp(Integer op) { this.op = op; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

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

    public String getCfgName() { return cfgName; }
    public void setCfgName(String cfgName) { this.cfgName = cfgName; }

    public Integer getTryCount() { return tryCount; }
    public void setTryCount(Integer tryCount) { this.tryCount = tryCount; }
}
