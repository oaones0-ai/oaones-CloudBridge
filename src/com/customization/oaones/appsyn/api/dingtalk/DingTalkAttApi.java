package com.customization.oaones.appsyn.api.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesHttp;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dto.AttOaPunch;
import com.customization.oaones.appsyn.dto.AttPunch;
import com.customization.oaones.appsyn.dto.AttPushRequest;
import com.customization.oaones.appsyn.service.AttPullAdapter;
import com.customization.oaones.appsyn.service.AttPullContext;
import com.customization.oaones.appsyn.service.AttPushAdapter;
import com.customization.oaones.appsyn.service.AttPushContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 钉钉「考勤」接口适配器：拉取打卡结果（第三方 → OA）与反向写入（OA → 第三方）。
 *
 * <p>凭据口径（需求 3.1.3）：企业内部应用 AppKey + AppSecret，并且**勾选了考勤相关权限点**；
 * 可以与通讯录共用同一个应用，但权限必须包含考勤，因此每条设置要指定「考勤应用」并支持「校验」。</p>
 *
 * <p>接口（首次上线逐条实测，路径都可配）：考勤组 {@code /topapi/attendance/getsimplegroups}、
 * 打卡结果 {@code /topapi/attendance/list}（按 workDateFrom / workDateTo + userIdList 分批取，
 * 每批上限见配置）；补卡识别按返回里的 {@code approveId}（来自审批的补卡记录带审批单号，需求 3.4.4）。</p>
 *
 * <p><b>反向写入</b>：钉钉不开放“写入打卡记录”，退化方案是走**补卡审批**——
 * 用 {@code /topapi/processinstance/create} 代员工提交一次补卡申请（等价于员工自己补卡）。
 * 该方案需要现场配好补卡审批模板编码（{@code att.push.dingtalk.process.code}）与审批人
 * （{@code att.push.dingtalk.approvers}），没配就按“未配置 = 不支持”处理；
 * 默认 {@code att.push.mode.dingtalk=none}，实测确认后改成 {@code card} 才会真正提交。</p>
 */
public class DingTalkAttApi implements AttPullAdapter, AttPushAdapter {

    private final String appKey;
    private final String appSecret;
    private DingTalkApi client;

    public DingTalkAttApi(String appKey, String appSecret) {
        this.appKey = OaonesUtil.s(appKey).trim();
        this.appSecret = OaonesUtil.s(appSecret).trim();
    }

    private String token() throws Exception {
        if (client == null) {
            client = new DingTalkApi(appKey, appSecret);
        }
        return client.getAccessToken();
    }

    @Override
    public List<AttPunch> list(AttPullContext ctx) throws Exception {
        if (ctx.getUserIds().isEmpty()) {
            return new ArrayList<AttPunch>();
        }
        requireCredential();
        OaonesApiLog.httpContext(OaonesConst.TYPE_DINGTALK, appKey);
        try {
            return doList(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private List<AttPunch> doList(AttPullContext ctx) throws Exception {
        List<AttPunch> result = new ArrayList<AttPunch>();
        String token = token();
        String url = OaonesProps.ddApiBase() + OaonesProps.attDingTalkListPath()
                + "?access_token=" + OaonesUtil.urlEncode(token);
        int batch = OaonesProps.attSyncUserBatch();
        for (int i = 0; i < ctx.getUserIds().size(); i += batch) {
            List<String> users = ctx.getUserIds().subList(i, Math.min(i + batch, ctx.getUserIds().size()));
            long offset = 0L;
            while (true) {
                JSONObject body = new JSONObject();
                body.put("workDateFrom", OaonesUtil.s(ctx.getFromDate()) + " 00:00:00");
                body.put("workDateTo", OaonesUtil.s(ctx.getToDate()) + " 23:59:59");
                body.put("userIdList", users);
                body.put("offset", offset);
                body.put("limit", 50L);
                JSONObject resp = OaonesHttp.postJson(url, body);
                if (resp.getIntValue("errcode") != 0) {
                    throw new RuntimeException("钉钉打卡结果接口返回错误：" + resp.toJSONString());
                }
                JSONObject resultObj = resp.getJSONObject("result");
                JSONArray rows = resultObj == null ? null : resultObj.getJSONArray("recordresult");
                if (rows == null || rows.isEmpty()) {
                    break;
                }
                for (int k = 0; k < rows.size(); k++) {
                    AttPunch punch = toPunch(rows.getJSONObject(k));
                    if (punch != null) {
                        result.add(punch);
                    }
                }
                if (rows.size() < 50 || result.size() >= ctx.getLimit()) {
                    break;
                }
                offset += rows.size();
            }
            if (result.size() >= ctx.getLimit()) {
                break;
            }
        }
        return result;
    }

    /**
     * 钉钉打卡结果归一化：{@code checkType} OnDuty 上班 / OffDuty 下班；
     * {@code locationResult = NotSigned} 表示未打卡（跳过）；{@code Outside} 是外勤；
     * {@code approveId} 非 0 表示这条是**审批（补卡）**产生的记录（需求 3.4.4）。
     */
    private AttPunch toPunch(JSONObject row) {
        String locationResult = OaonesUtil.s(row.getString("locationResult"));
        if ("NotSigned".equalsIgnoreCase(locationResult)) {
            return null;
        }
        JSONObject checkTime = row.getJSONObject("userCheckTime");
        long ts = checkTime == null ? 0L : checkTime.getLongValue("time");
        if (ts <= 0L) {
            ts = row.getLongValue("userCheckTime");
        }
        String userId = OaonesUtil.s(row.getString("userId"));
        if (ts <= 0L || userId.length() == 0) {
            return null;
        }
        String checkType = OaonesUtil.s(row.getString("checkType"));
        AttPunch punch = new AttPunch();
        punch.setWxUserId(userId);
        punch.setSignTime(OaonesUtil.formatDateTime(ts));
        punch.setSignDate(punch.getSignTime().length() >= 10
                ? punch.getSignTime().substring(0, 10)
                : OaonesUtil.s(row.getString("workDate")));
        punch.setSignType("OffDuty".equalsIgnoreCase(checkType)
                ? Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_OUT)
                : Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_IN));
        long approveId = row.getLongValue("approveId");
        if (approveId > 0L) {
            punch.setDataType(Integer.valueOf(OaonesConst.ATT_DATA_REMEDY));
            punch.setRawType("补卡（钉钉审批单 " + approveId + "）");
            punch.setWxRecordId(String.valueOf(approveId));
        } else {
            punch.setDataType(Integer.valueOf(OaonesConst.ATT_DATA_NORMAL));
            punch.setRawType("Outside".equalsIgnoreCase(locationResult) ? "外勤打卡（钉钉 Outside）"
                    : (OaonesUtil.s(row.getString("timeResult")).length() == 0
                    ? "正常打卡" : "正常打卡（" + OaonesUtil.s(row.getString("timeResult")) + "）"));
            punch.setWxRecordId(OaonesUtil.s(row.getString("id")));
        }
        punch.setRawSummary("locationResult=" + locationResult
                + "、deviceId=" + OaonesUtil.s(row.getString("deviceId")));
        return punch;
    }

    @Override
    public JSONObject probe(AttPullContext ctx) throws Exception {
        JSONObject out = new JSONObject();
        out.put("ok", false);
        out.put("step", "token");
        try {
            requireCredential();
        } catch (RuntimeException e) {
            out.put("message", e.getMessage());
            return out;
        }
        OaonesApiLog.httpContext(OaonesConst.TYPE_DINGTALK, appKey);
        try {
            String token = token();
            out.put("step", "groups");
            JSONObject groupResp = OaonesHttp.postJson(
                    OaonesProps.ddApiBase() + OaonesProps.attDingTalkGroupPath()
                            + "?access_token=" + OaonesUtil.urlEncode(token), new JSONObject());
            if (groupResp.getIntValue("errcode") != 0) {
                out.put("message", "读取考勤组失败（钉钉原文）：" + groupResp.toJSONString());
                return out;
            }
            JSONObject groupResult = groupResp.getJSONObject("result");
            JSONArray groups = groupResult == null ? null : groupResult.getJSONArray("groups");
            out.put("groups", groups == null ? 0 : groups.size());

            out.put("step", "data");
            List<String> users = ctx.getUserIds();
            if (users.isEmpty()) {
                out.put("ok", true);
                out.put("message", "鉴权与考勤组读取正常；该租户还没有已绑定成员，跳过打卡数据试读");
                return out;
            }
            JSONObject body = new JSONObject();
            String today = OaonesUtil.formatDateTime(System.currentTimeMillis()).substring(0, 10);
            body.put("workDateFrom", today + " 00:00:00");
            body.put("workDateTo", today + " 23:59:59");
            body.put("userIdList", users.subList(0, Math.min(1, users.size())));
            body.put("offset", 0L);
            body.put("limit", 50L);
            JSONObject dataResp = OaonesHttp.postJson(
                    OaonesProps.ddApiBase() + OaonesProps.attDingTalkListPath()
                            + "?access_token=" + OaonesUtil.urlEncode(token), body);
            if (dataResp.getIntValue("errcode") != 0) {
                out.put("message", "读取打卡数据失败（钉钉原文）：" + dataResp.toJSONString());
                return out;
            }
            JSONObject dataResult = dataResp.getJSONObject("result");
            JSONArray rows = dataResult == null ? null : dataResult.getJSONArray("recordresult");
            out.put("punch_count", rows == null ? 0 : rows.size());
            out.put("ok", true);
            out.put("message", "校验通过：考勤组 " + OaonesUtil.s(out.getString("groups"))
                    + " 个，今日读到打卡数据 " + (rows == null ? 0 : rows.size()) + " 条");
            return out;
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private void requireCredential() {
        if (appKey.length() == 0 || appSecret.length() == 0) {
            throw new RuntimeException("钉钉考勤应用缺少 AppKey / AppSecret，无法读取打卡数据");
        }
    }

    // ---------------- 反向写入（OA → 第三方：补卡审批） ----------------

    @Override
    public AttPushRequest buildRequest(AttPushContext ctx) {
        AttPushRequest req = new AttPushRequest();
        req.setMethod("POST");
        req.setPath("/topapi/processinstance/create");
        String processCode = OaonesProps.attPushDingTalkProcessCode();
        AttOaPunch punch = ctx == null ? null : ctx.getPunch();
        JSONObject body = new JSONObject();
        body.put("process_code", processCode);
        body.put("originator_user_id", OaonesUtil.s(ctx == null ? "" : ctx.getWxUserId()));
        req.note("process_code", "补卡审批模板编码（oaones.appsyn.properties#att.push.dingtalk.process.code，留空 = 未配置）");
        req.note("originator_user_id", "发起人：该 OA 人员绑定的钉钉 userId");
        JSONArray components = new JSONArray();
        JSONObject signDate = new JSONObject();
        signDate.put("name", "补卡日期");
        signDate.put("value", punch == null ? "" : OaonesUtil.s(punch.getSignDate()));
        components.add(signDate);
        JSONObject signTime = new JSONObject();
        signTime.put("name", "补卡时间");
        signTime.put("value", punch == null ? "" : OaonesUtil.s(punch.getSignTime()));
        components.add(signTime);
        JSONObject signType = new JSONObject();
        signType.put("name", "补卡类型");
        signType.put("value", punch != null && Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_OUT).equals(punch.getSignType())
                ? "下班卡" : "上班卡");
        components.add(signType);
        JSONObject reason = new JSONObject();
        reason.put("name", "补卡事由");
        reason.put("value", "由 OA 考勤同步提交（OA 打卡记录已存在）");
        components.add(reason);
        body.put("form_component_values", components);
        req.note("form_component_values", "补卡表单：字段名按现场补卡审批模板的组件名核对（对不上只改本适配器）");
        String approvers = OaonesProps.attPushDingTalkApprovers();
        if (approvers.length() > 0) {
            body.put("approvers", approvers);
            req.note("approvers", "审批人 userId（oaones.appsyn.properties#att.push.dingtalk.approvers，逗号分隔）");
        }
        req.setBody(body.toJSONString());
        if (processCode.length() == 0) {
            req.setPushError("未配置钉钉补卡审批模板编码（att.push.dingtalk.process.code），该方向暂不可用");
        }
        return req;
    }

    @Override
    public String push(AttPushContext ctx) throws Exception {
        AttPushRequest req = buildRequest(ctx);
        if (!OaonesUtil.s(req.getPushError()).isEmpty()) {
            throw new RuntimeException(req.getPushError());
        }
        requireCredential();
        OaonesApiLog.httpContext(OaonesConst.TYPE_DINGTALK, appKey);
        try {
            String token = token();
            String url = OaonesProps.ddApiBase() + req.getPath()
                    + "?access_token=" + OaonesUtil.urlEncode(token);
            JSONObject resp = OaonesHttp.postJson(url, JSONObject.parseObject(req.getBody()));
            if (resp.getIntValue("errcode") != 0) {
                throw new RuntimeException("钉钉补卡审批接口返回错误：" + resp.toJSONString());
            }
            String instanceId = OaonesUtil.s(resp.getString("process_instance_id"));
            return instanceId.length() > 0 ? instanceId : OaonesUtil.s(resp.getString("result"));
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    @Override
    public String capabilityNote(AttPushContext ctx) {
        if (OaonesProps.attPushDingTalkProcessCode().length() == 0) {
            return "钉钉：不开放打卡写入，退化方案是提交补卡审批；当前未配置补卡审批模板编码，"
                    + "该方向暂不可用（配好 att.push.dingtalk.process.code 并实测后启用）";
        }
        OaonesLog.step("[attSync] 钉钉反向写入走补卡审批（process_code="
                + OaonesProps.attPushDingTalkProcessCode() + "）");
        return "钉钉：走补卡审批（代员工提交一次补卡申请），审批单号会记在明细的平台记录ID 上";
    }
}
