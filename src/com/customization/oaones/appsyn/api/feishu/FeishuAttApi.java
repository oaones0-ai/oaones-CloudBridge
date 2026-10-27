package com.customization.oaones.appsyn.api.feishu;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书「考勤」接口适配器：拉取打卡流水（第三方 → OA）与反向写入（OA → 第三方）。
 *
 * <p>凭据口径（需求 3.1.3）：企业自建应用 App ID + App Secret，并且**开通考勤相关权限**；
 * 可以与通讯录共用应用，但权限必须包含考勤。</p>
 *
 * <p>接口（首次上线逐条实测，路径都可配）：考勤组 {@code /open-apis/attendance/v1/groups}、
 * 打卡流水 {@code /open-apis/attendance/v1/user_flows/query}（按 {@code check_time_from / to} +
 * {@code user_ids} 分批取）、补卡记录 {@code /open-apis/attendance/v1/user_task_remedys/query}
 * （补卡专用接口，需求 3.4.4 飞书那一行）。</p>
 *
 * <p><b>反向写入</b>：飞书不开放“写入打卡记录”，退化方案走**补卡审批**
 * （{@code /open-apis/approval/v4/instances} 代员工提交补卡申请）。
 * 需要现场配好补卡审批定义编码（{@code att.push.feishu.approval.code}）与审批人
 * （{@code att.push.feishu.approvers}），没配就按“未配置 = 不支持”处理；
 * 默认 {@code att.push.mode.feishu=none}，实测确认后改成 {@code card} 才会真正提交。</p>
 */
public class FeishuAttApi implements AttPullAdapter, AttPushAdapter {

    private final String appId;
    private final String appSecret;
    private FeishuApi client;

    public FeishuAttApi(String appId, String appSecret) {
        this.appId = OaonesUtil.s(appId).trim();
        this.appSecret = OaonesUtil.s(appSecret).trim();
    }

    private String token() throws Exception {
        if (client == null) {
            client = new FeishuApi(appId, appSecret);
        }
        return client.getAccessToken();
    }

    @Override
    public List<AttPunch> list(AttPullContext ctx) throws Exception {
        if (ctx.getUserIds().isEmpty()) {
            return new ArrayList<AttPunch>();
        }
        requireCredential();
        OaonesApiLog.httpContext(OaonesConst.TYPE_FEISHU, appId);
        try {
            return ctx.isRemedyOnly() ? doListRemedy(ctx) : doListFlows(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private List<AttPunch> doListFlows(AttPullContext ctx) throws Exception {
        List<AttPunch> result = new ArrayList<AttPunch>();
        String token = token();
        String url = OaonesProps.fsApiBase() + OaonesProps.attFeishuFlowPath()
                + "?employee_type=employee_id";
        int batch = OaonesProps.attSyncUserBatch();
        for (int i = 0; i < ctx.getUserIds().size(); i += batch) {
            List<String> users = ctx.getUserIds().subList(i, Math.min(i + batch, ctx.getUserIds().size()));
            JSONObject body = new JSONObject();
            body.put("user_ids", users);
            putCheckTimeRange(body, ctx.getFromDate(), ctx.getToDate());
            JSONObject resp = OaonesHttp.postJson(url, body, bearer(token));
            if (resp.getIntValue("code") != 0) {
                throw new RuntimeException("飞书打卡流水接口返回错误：" + resp.toJSONString());
            }
            JSONObject data = resp.getJSONObject("data");
            JSONArray rows = data == null ? null : data.getJSONArray("user_flow_results");
            if (rows == null) {
                continue;
            }
            for (int k = 0; k < rows.size(); k++) {
                JSONObject row = rows.getJSONObject(k);
                String userId = OaonesUtil.s(row.getString("user_id"));
                JSONArray flows = row.getJSONArray("user_flows");
                if (flows == null || flows.isEmpty()) {
                    // 新版 user_flows/query 返回扁平的 UserFlow 列表；
                    // 保留 user_flows 嵌套解析以兼容旧版接口响应。
                    AttPunch punch = toPunch(userId, row);
                    if (punch != null) {
                        result.add(punch);
                    }
                    continue;
                }
                for (int f = 0; f < flows.size(); f++) {
                    AttPunch punch = toPunch(userId, flows.getJSONObject(f));
                    if (punch != null) {
                        result.add(punch);
                    }
                }
            }
            if (result.size() >= ctx.getLimit()) {
                break;
            }
        }
        return result;
    }

    /** 补卡记录：飞书有补卡专用接口（user_task_remedys/query），用它读补签卡。 */
    private List<AttPunch> doListRemedy(AttPullContext ctx) throws Exception {
        List<AttPunch> result = new ArrayList<AttPunch>();
        String token = token();
        String url = OaonesProps.fsApiBase() + OaonesProps.attFeishuRemedyPath()
                + "?employee_type=employee_id";
        int batch = OaonesProps.attSyncUserBatch();
        for (int i = 0; i < ctx.getUserIds().size(); i += batch) {
            List<String> users = ctx.getUserIds().subList(i, Math.min(i + batch, ctx.getUserIds().size()));
            JSONObject body = new JSONObject();
            body.put("user_ids", users);
            putCheckTimeRange(body, ctx.getFromDate(), ctx.getToDate());
            JSONObject resp = OaonesHttp.postJson(url, body, bearer(token));
            if (resp.getIntValue("code") != 0) {
                throw new RuntimeException("飞书补卡记录接口返回错误：" + resp.toJSONString());
            }
            JSONObject data = resp.getJSONObject("data");
            JSONArray rows = data == null ? null : data.getJSONArray("user_remedys");
            if (rows == null && data != null) {
                rows = data.getJSONArray("user_remedy_results");
            }
            if (rows == null) {
                continue;
            }
            for (int k = 0; k < rows.size(); k++) {
                JSONObject row = rows.getJSONObject(k);
                String userId = OaonesUtil.s(row.getString("user_id"));
                JSONArray items = row.getJSONArray("remedy_records");
                if (items == null) {
                    items = row.getJSONArray("user_remedys");
                }
                if (items == null || items.isEmpty()) {
                    // 新版接口的 data.user_remedys 就是扁平的 UserTaskRemedy 列表。
                    AttPunch punch = toRemedyPunch(userId, row);
                    if (punch != null) {
                        result.add(punch);
                    }
                    continue;
                }
                for (int f = 0; f < items.size(); f++) {
                    AttPunch punch = toRemedyPunch(userId, items.getJSONObject(f));
                    if (punch != null) {
                        result.add(punch);
                    }
                }
            }
            if (result.size() >= ctx.getLimit()) {
                break;
            }
        }
        return result;
    }

    /** 打卡流水归一化：check_time 是“yyyy-MM-dd HH:mm:ss”（飞书口径），check_type 上班 / 下班。 */
    private AttPunch toPunch(String userId, JSONObject flow) {
        if (flow == null) {
            return null;
        }
        String time = OaonesUtil.s(flow.getString("check_time"));
        long ts = OaonesUtil.parseDateTime(time);
        if (ts <= 0L) {
            time = OaonesUtil.s(flow.getString("check_time_iso"));
            ts = OaonesUtil.parseDateTime(time);
        }
        if (ts <= 0L || userId.length() == 0) {
            return null;
        }
        AttPunch punch = new AttPunch();
        punch.setWxUserId(userId);
        punch.setSignTime(OaonesUtil.formatDateTime(ts));
        punch.setSignDate(punch.getSignTime().substring(0, 10));
        String checkType = OaonesUtil.s(flow.getString("check_type"));
        punch.setSignType("OffDuty".equalsIgnoreCase(checkType) ? Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_OUT)
                : Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_IN));
        punch.setDataType(Integer.valueOf(OaonesConst.ATT_DATA_NORMAL));
        punch.setRawType("正常打卡（飞书 " + (checkType.length() == 0 ? "类型未知" : checkType) + "）");
        String recordId = OaonesUtil.s(flow.getString("flow_id"));
        if (recordId.length() == 0) {
            recordId = OaonesUtil.s(flow.getString("record_id"));
        }
        punch.setWxRecordId(recordId);
        punch.setRawSummary("location_name=" + OaonesUtil.s(flow.getString("location_name"))
                + "、check_result=" + OaonesUtil.s(flow.getString("check_result")));
        return punch;
    }

    /** 补卡记录归一化（飞书补卡专用接口）：remedy_time 为补卡时间。 */
    private AttPunch toRemedyPunch(String userId, JSONObject row) {
        if (row == null) {
            return null;
        }
        String time = OaonesUtil.s(row.getString("remedy_time"));
        long ts = OaonesUtil.parseDateTime(time);
        if (ts <= 0L) {
            long seconds = row.getLongValue("time");
            if (seconds > 0L) {
                ts = seconds > 100000000000L ? seconds : seconds * 1000L;
            }
        }
        if (ts <= 0L || userId.length() == 0) {
            return null;
        }
        AttPunch punch = new AttPunch();
        punch.setWxUserId(userId);
        punch.setSignTime(OaonesUtil.formatDateTime(ts));
        punch.setSignDate(punch.getSignTime().substring(0, 10));
        int workType = row.getIntValue("work_type");
        punch.setSignType(workType == 2
                ? Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_OUT)
                : Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_IN));
        punch.setDataType(Integer.valueOf(OaonesConst.ATT_DATA_REMEDY));
        punch.setRawType("补卡（飞书补卡记录）");
        String recordId = OaonesUtil.s(row.getString("remedy_id"));
        if (recordId.length() == 0) {
            recordId = OaonesUtil.s(row.getString("approval_id"));
        }
        punch.setWxRecordId(recordId);
        punch.setRawSummary("reason=" + OaonesUtil.s(row.getString("reason")));
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
        OaonesApiLog.httpContext(OaonesConst.TYPE_FEISHU, appId);
        try {
            String token = token();
            out.put("step", "groups");
            JSONObject groupResp = OaonesHttp.getJson(
                    OaonesProps.fsApiBase() + OaonesProps.attFeishuGroupPath()
                            + "?page_size=10&query_type=1", bearer(token));
            if (groupResp.getIntValue("code") != 0) {
                out.put("message", "读取考勤组失败（飞书原文）：" + groupResp.toJSONString());
                return out;
            }
            JSONObject groupData = groupResp.getJSONObject("data");
            JSONArray items = groupData == null ? null : groupData.getJSONArray("items");
            out.put("groups", items == null ? 0 : items.size());

            out.put("step", "data");
            List<String> users = ctx.getUserIds();
            if (users.isEmpty()) {
                out.put("ok", true);
                out.put("message", "鉴权与考勤组读取正常；该租户还没有已绑定成员，跳过打卡数据试读");
                return out;
            }
            String today = OaonesUtil.formatDateTime(System.currentTimeMillis()).substring(0, 10);
            JSONObject body = new JSONObject();
            body.put("user_ids", users.subList(0, 1));
            putCheckTimeRange(body, today, today);
            JSONObject dataResp = OaonesHttp.postJson(
                    OaonesProps.fsApiBase() + OaonesProps.attFeishuFlowPath()
                            + "?employee_type=employee_id", body, bearer(token));
            if (dataResp.getIntValue("code") != 0) {
                out.put("message", "读取打卡流水失败（飞书原文）：" + dataResp.toJSONString());
                return out;
            }
            JSONObject data = dataResp.getJSONObject("data");
            JSONArray rows = data == null ? null : data.getJSONArray("user_flow_results");
            out.put("punch_count", rows == null ? 0 : rows.size());
            out.put("ok", true);
            out.put("message", "校验通过：考勤组 " + OaonesUtil.s(out.getString("groups"))
                    + " 个，今日读到打卡流水 " + (rows == null ? 0 : rows.size()) + " 条");
            return out;
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private Map<String, String> bearer(String token) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    /**
     * 飞书打卡流水的查询区间是秒级时间戳，且区间为 {@code [from, to)}。
     * 页面传入的是闭区间日期，因此结束日期要顺延到次日 00:00:00。
     */
    private void putCheckTimeRange(JSONObject body, String fromDate, String toDate) {
        String fromText = OaonesUtil.s(fromDate).trim();
        String toText = OaonesUtil.s(toDate).trim();
        long from = OaonesUtil.parseDateTime(fromText + " 00:00:00");
        long to = OaonesUtil.parseDateTime(OaonesUtil.shiftDate(toText, 1) + " 00:00:00");
        if (from <= 0L || to <= from) {
            throw new RuntimeException("飞书考勤查询时间范围无效：fromDate=" + fromText + "，toDate=" + toText);
        }
        body.put("check_time_from", String.valueOf(from / 1000L));
        body.put("check_time_to", String.valueOf(to / 1000L));
    }

    private void requireCredential() {
        if (appId.length() == 0 || appSecret.length() == 0) {
            throw new RuntimeException("飞书考勤应用缺少 App ID / App Secret，无法读取打卡数据");
        }
    }

    // ---------------- 反向写入（OA → 第三方：补卡审批） ----------------

    @Override
    public AttPushRequest buildRequest(AttPushContext ctx) {
        AttPushRequest req = new AttPushRequest();
        req.setMethod("POST");
        req.setPath("/open-apis/approval/v4/instances");
        String approvalCode = OaonesProps.attPushFeishuApprovalCode();
        AttOaPunch punch = ctx == null ? null : ctx.getPunch();
        JSONObject body = new JSONObject();
        body.put("approval_code", approvalCode);
        body.put("user_id", OaonesUtil.s(ctx == null ? "" : ctx.getWxUserId()));
        req.note("approval_code", "补卡审批定义编码（oaones.appsyn.properties#att.push.feishu.approval.code，留空 = 未配置）");
        req.note("user_id", "发起人：该 OA 人员绑定的飞书 user_id");
        JSONArray form = new JSONArray();
        form.add(component("补卡日期", punch == null ? "" : OaonesUtil.s(punch.getSignDate())));
        form.add(component("补卡时间", punch == null ? "" : OaonesUtil.s(punch.getSignTime())));
        form.add(component("补卡类型", punch != null
                && Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_OUT).equals(punch.getSignType()) ? "下班卡" : "上班卡"));
        body.put("form", form);
        req.note("form", "补卡表单：字段名按现场补卡审批定义的控件名核对（对不上只改本适配器）");
        String approvers = OaonesProps.attPushFeishuApprovers();
        if (approvers.length() > 0) {
            JSONArray nodes = new JSONArray();
            JSONObject node = new JSONObject();
            node.put("approver_chs", approvers.split(","));
            nodes.add(node);
            body.put("node_approver_user_id_list", nodes);
            req.note("node_approver_user_id_list", "审批人 user_id（oaones.appsyn.properties#att.push.feishu.approvers）");
        }
        req.setBody(body.toJSONString());
        if (approvalCode.length() == 0) {
            req.setPushError("未配置飞书补卡审批定义编码（att.push.feishu.approval.code），该方向暂不可用");
        }
        return req;
    }

    private JSONObject component(String name, String value) {
        JSONObject item = new JSONObject();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    @Override
    public String push(AttPushContext ctx) throws Exception {
        AttPushRequest req = buildRequest(ctx);
        if (!OaonesUtil.s(req.getPushError()).isEmpty()) {
            throw new RuntimeException(req.getPushError());
        }
        requireCredential();
        OaonesApiLog.httpContext(OaonesConst.TYPE_FEISHU, appId);
        try {
            String token = token();
            JSONObject resp = OaonesHttp.postJson(OaonesProps.fsApiBase() + req.getPath(),
                    JSONObject.parseObject(req.getBody()), bearer(token));
            if (resp.getIntValue("code") != 0) {
                throw new RuntimeException("飞书补卡审批接口返回错误：" + resp.toJSONString());
            }
            JSONObject data = resp.getJSONObject("data");
            return data == null ? "" : OaonesUtil.s(data.getString("instance_code"));
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    @Override
    public String capabilityNote(AttPushContext ctx) {
        if (OaonesProps.attPushFeishuApprovalCode().length() == 0) {
            return "飞书：不开放打卡写入，退化方案是提交补卡审批；当前未配置补卡审批定义编码，"
                    + "该方向暂不可用（配好 att.push.feishu.approval.code 并实测后启用）";
        }
        OaonesLog.step("[attSync] 飞书反向写入走补卡审批（approval_code="
                + OaonesProps.attPushFeishuApprovalCode() + "）");
        return "飞书：走补卡审批（代员工提交一次补卡申请），审批实例号会记在明细的平台记录ID 上";
    }
}
