package com.customization.oaones.appsyn.entity;

/**
 * 接口日志（表名取 oaones.appsyn.properties#oaones_api_log_table）。
 *
 * <p>一行 = 一次接口调用：既包含 OAones 发往第三方平台（企业微信 / 钉钉 / 飞书 / 个人微信）
 * 的请求，也包含第三方平台调用 OAones 的入口（统一消息接口 / 工作台免登 / 扫码回调）。
 * 记录平台、租户、接口类型、方向、请求与返回报文、HTTP 状态、耗时与错误，
 * 供「接口日志中心」（一级菜单）在一个列表里按 平台 / 租户 / 接口类型 过滤查看。</p>
 *
 * <p>本表<b>不做权限重构</b>：只记 {@code formmodeid}（按表名反查，便于在建模引擎里查日志），
 * 不调用 ModeRightInfo。</p>
 */
public class ApiLog {

    private Long id;
    /** 平台类型：0 企业微信 / 1 钉钉 / 2 飞书 / 3 个人微信（无租户）。 */
    private Integer type;
    /** 租户配置ID（uf_oaones_appsyn_qywx_config.id）；个人微信 / 无法归属的调用为 0。 */
    private Integer cfgId;
    /** 租户名称（落库快照，便于日志不依赖配置表）。 */
    private String corpName;
    /** 接口类型：org 组织同步 / msg 消息推送 / mtg 会议日程同步 / login 登录授权 / other 其他。 */
    private String apiType;
    /** 方向：0 发往第三方平台（OA→平台）/ 1 第三方平台调用 OA（平台→OA）。 */
    private Integer direction;
    private String method;
    /** 接口路径（不含域名，已去掉查询串里的密钥）。 */
    private String apiPath;
    /** 完整接口地址（已脱敏，便于排查）。 */
    private String apiUrl;
    private String reqBody;
    private String respBody;
    private Integer httpStatus;
    /** 结果：1 成功 / 0 失败。 */
    private Integer success;
    private String errCode;
    private String errMsg;
    private Integer costMs;
    private String errorStack;
    /** 触发人（OA 人员ID，外部调用 / 定时任务为 0）。 */
    private Integer operatorId;
    private Integer formmodeid;
    /** 调用日期（建模系统标准字段）：yyyy-MM-dd。 */
    private String modedatacreatedate;
    /** 调用时间（建模系统标准字段）：HH:mm:ss。 */
    private String modedatacreatetime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getCorpName() { return corpName; }
    public void setCorpName(String corpName) { this.corpName = corpName; }

    public String getApiType() { return apiType; }
    public void setApiType(String apiType) { this.apiType = apiType; }

    public Integer getDirection() { return direction; }
    public void setDirection(Integer direction) { this.direction = direction; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getApiPath() { return apiPath; }
    public void setApiPath(String apiPath) { this.apiPath = apiPath; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getReqBody() { return reqBody; }
    public void setReqBody(String reqBody) { this.reqBody = reqBody; }

    public String getRespBody() { return respBody; }
    public void setRespBody(String respBody) { this.respBody = respBody; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public Integer getSuccess() { return success; }
    public void setSuccess(Integer success) { this.success = success; }

    public String getErrCode() { return errCode; }
    public void setErrCode(String errCode) { this.errCode = errCode; }

    public String getErrMsg() { return errMsg; }
    public void setErrMsg(String errMsg) { this.errMsg = errMsg; }

    public Integer getCostMs() { return costMs; }
    public void setCostMs(Integer costMs) { this.costMs = costMs; }

    public String getErrorStack() { return errorStack; }
    public void setErrorStack(String errorStack) { this.errorStack = errorStack; }

    public Integer getOperatorId() { return operatorId; }
    public void setOperatorId(Integer operatorId) { this.operatorId = operatorId; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) {
        this.modedatacreatedate = modedatacreatedate;
    }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) {
        this.modedatacreatetime = modedatacreatetime;
    }
}
