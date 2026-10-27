package com.customization.oaones.appsyn.msg.dingtalk.entity;

/**
 * 消息推送**状态表**（表名取 oaones.appsyn.properties#oaones_msg_log_table）。
 *
 * <p>口径（接口日志中心方案 B）：本表只记录一次推送的**业务状态**——
 * 业务单号 / 消息类型 / 接收人 / 分片 / 去重键 / 卡片任务 / 重试计划；
 * <b>接口报文与结果（请求 / 返回报文、平台返回码与信息、耗时、异常堆栈）不再落本表</b>，
 * 统一保存在接口日志中心 {@code uf_oaones_appsyn_api_log}，用 {@link #apiLogId} 关联——
 * 同一次接口调用全库只存一份。推送日志页面的报文 / 结果 / 耗时都按它到接口日志里取。</p>
 *
 * <p>没有实际发起接口调用的行（待推送 / 去重拦截 / 跳过）没有 {@code api_log_id}，
 * 其业务原因写在 {@link #reason}。</p>
 */
public class MsgLog {

    private Long id;
    private String bizType;
    private String bizId;
    private String bizTitle;
    private Integer stdType;
    private String typeName;
    private String wxMsgtype;
    private String cardAction;
    private Integer appId;
    private String corpId;
    private String agentId;
    private String taskId;
    private String dedupKey;
    private String receiverOa;
    private String receiverWx;
    private Integer receiverCount;
    private Integer shardIndex;
    private Integer shardTotal;
    private Integer status;
    private Integer retryCount;
    private String nextRetryTime;
    /** 最近一次接口调用对应的接口日志ID（uf_oaones_appsyn_api_log.id）；没有调用过时为空。 */
    private Long apiLogId;
    /** 跳过 / 去重 / 拦截等业务原因（平台返回结果看接口日志）。 */
    private String reason;
    /** 原始入参（标准消息/外部入参）——OA 侧入参，不是接口报文。 */
    private String srcJson;
    private Integer operatorId;
    /**
     * 建模模块ID（写入时按 {@code oaones.appsyn.properties#oaones_msg_log_table} 反查）。
     *
     * <p>日志表**不做权限重构**：只记录模块ID 便于在建模引擎里查询日志，
     * 不调用 ModeRightInfo，不需要在建模引擎里维护权限共享规则。</p>
     */
    private Integer formmodeid;
    /** 推送日期（建模系统标准字段）：yyyy-MM-dd，写入即固定，用于时间范围检索与看板统计。 */
    private String modedatacreatedate;
    /** 推送时间（建模系统标准字段）：HH:mm:ss。 */
    private String modedatacreatetime;
    /** 最近更新时间（建模系统标准字段）：yyyy-MM-dd HH:mm:ss，重试/重推后回写。 */
    private String modedatamodifydatetime;

    /* ------------------------------------------------------------------
     * 只读联表字段：从接口日志中心 uf_oaones_appsyn_api_log 带出（不落本表）
     * ------------------------------------------------------------------ */
    /** 耗时（毫秒，接口日志 cost_ms）。 */
    private Integer apiCostMs;
    /** 平台返回码（接口日志 err_code）。 */
    private String apiErrCode;
    /** 平台返回信息（接口日志 err_msg）。 */
    private String apiErrMsg;
    /** 请求报文（接口日志 req_body）。 */
    private String apiReqBody;
    /** 返回报文（接口日志 resp_body）。 */
    private String apiRespBody;
    /** 异常堆栈（接口日志 error_stack）。 */
    private String apiErrStack;
    /** 结果（接口日志 success：1 成功 / 0 失败）。 */
    private Integer apiSuccess;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }

    public String getBizTitle() { return bizTitle; }
    public void setBizTitle(String bizTitle) { this.bizTitle = bizTitle; }

    public Integer getStdType() { return stdType; }
    public void setStdType(Integer stdType) { this.stdType = stdType; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getWxMsgtype() { return wxMsgtype; }
    public void setWxMsgtype(String wxMsgtype) { this.wxMsgtype = wxMsgtype; }

    public String getCardAction() { return cardAction; }
    public void setCardAction(String cardAction) { this.cardAction = cardAction; }

    public Integer getAppId() { return appId; }
    public void setAppId(Integer appId) { this.appId = appId; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }

    public String getReceiverOa() { return receiverOa; }
    public void setReceiverOa(String receiverOa) { this.receiverOa = receiverOa; }

    public String getReceiverWx() { return receiverWx; }
    public void setReceiverWx(String receiverWx) { this.receiverWx = receiverWx; }

    public Integer getReceiverCount() { return receiverCount; }
    public void setReceiverCount(Integer receiverCount) { this.receiverCount = receiverCount; }

    public Integer getShardIndex() { return shardIndex; }
    public void setShardIndex(Integer shardIndex) { this.shardIndex = shardIndex; }

    public Integer getShardTotal() { return shardTotal; }
    public void setShardTotal(Integer shardTotal) { this.shardTotal = shardTotal; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(String nextRetryTime) { this.nextRetryTime = nextRetryTime; }

    public Long getApiLogId() { return apiLogId; }
    public void setApiLogId(Long apiLogId) { this.apiLogId = apiLogId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSrcJson() { return srcJson; }
    public void setSrcJson(String srcJson) { this.srcJson = srcJson; }

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

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }

    /* ---------- 联表只读字段（来自接口日志中心） ---------- */

    public Integer getApiCostMs() { return apiCostMs; }
    public void setApiCostMs(Integer apiCostMs) { this.apiCostMs = apiCostMs; }

    public String getApiErrCode() { return apiErrCode; }
    public void setApiErrCode(String apiErrCode) { this.apiErrCode = apiErrCode; }

    public String getApiErrMsg() { return apiErrMsg; }
    public void setApiErrMsg(String apiErrMsg) { this.apiErrMsg = apiErrMsg; }

    public String getApiReqBody() { return apiReqBody; }
    public void setApiReqBody(String apiReqBody) { this.apiReqBody = apiReqBody; }

    public String getApiRespBody() { return apiRespBody; }
    public void setApiRespBody(String apiRespBody) { this.apiRespBody = apiRespBody; }

    public String getApiErrStack() { return apiErrStack; }
    public void setApiErrStack(String apiErrStack) { this.apiErrStack = apiErrStack; }

    public Integer getApiSuccess() { return apiSuccess; }
    public void setApiSuccess(Integer apiSuccess) { this.apiSuccess = apiSuccess; }
}
