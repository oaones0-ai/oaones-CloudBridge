package com.customization.oaones.appsyn.api.wecom;

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
 * 企业微信「打卡」接口适配器：拉取考勤打卡数据（第三方 → OA），
 * 并按需求 3.9 的三级方案承担反向写入（OA → 第三方）。
 *
 * <p>凭据口径（需求 3.1.3）：必须用**「打卡」应用的 Secret**换 access_token——
 * 既不是通讯录 Secret，也不是工作台自建应用 Secret，所以每条设置要单独指定「考勤应用」
 * （默认不指定则不执行同步，也不静默回退）。</p>
 *
 * <p>接口（首次上线逐条实测，路径都可以在 oaones.properties 里改）：
 * 考勤组 / 打卡规则两条路——按成员读 {@code /cgi-bin/checkin/getcheckinoption}
 * （请求体 {@code datetime} + {@code useridlist}，**两个字段都是必填**，
 * 2026-10-15 现场报的 {@code 40058 missing field useridlist} 就是这里的字段名写错造成的）、
 * 企业级读 {@code /cgi-bin/checkin/getcorpcheckinoption}（请求体为空，拿全企业的打卡规则，
 * 零绑定成员的租户也能校验考勤数据权限）；打卡数据 {@code /cgi-bin/checkin/getcheckindata}
 * （{@code opencheckindatatype} = 3 取全部：上下班 + 外出 / 出差）。</p>
 *
 * <p><b>时间戳口径</b>：平台接口要的是**真正的 Unix 秒**，所以这里的“当天 0 点 / 当天 23:59:59”
 * 一律走 {@link OaonesUtil#parseDateTimeTgz(String)}（显式按 Asia/Shanghai 解析，与运行环境时区无关）。
 * 不能直接用 {@code parseDateTime} 再减 8 小时：那等于假设运行环境是 UTC，
 * 在东八区机器上会把窗口整体前移 8 小时（当天下班卡取不回来）。</p>
 *
 * <p><b>反向写入</b>（需求 3.9 第 4 条的三级方案，两条都可落地，按配置切换）：
 * <ul>
 *   <li><b>{@code direct}</b> →「添加打卡记录」{@code /cgi-bin/checkin/add_checkin_record}
 *       （文档：添加打卡记录。一批最多 200 条、**60 次/分钟**；{@code location_title} /
 *       {@code location_detail} / {@code device_type} / {@code device_detail} 都是必填，
 *       这里用 {@code att.push.wecom.*} 配的固定值）；</li>
 *   <li><b>{@code card}</b> →「为打卡人员补卡」{@code /cgi-bin/checkin/punch_correction}
 *       （文档：为打卡人员补卡。{@code schedule_date_time} = 应打卡日期当天 0 点的 Unix 秒、
 *       {@code checkin_time} = 实际打卡时间，**600 次/分钟**；平台侧记为补卡）；</li>
 *   <li><b>{@code none}</b> → 兜底口径：页面上显式说明“不支持”，明细写“跳过”，不做假成功。</li>
 * </ul>
 * 两个写接口都要求**自建应用被配置到「打卡 - 可调用接口的应用」**，用的仍是本租户的
 * 「考勤应用」（打卡应用）Secret 换的 access_token；接口只回 {@code errcode / errmsg}，
 * 不返回记录 ID，所以明细的“平台凭据”为空。</p>
 */
public class WeComAttApi implements AttPullAdapter, AttPushAdapter {

    private final String corpId;
    private final String corpSecret;
    private WeComApi client;

    public WeComAttApi(String corpId, String corpSecret) {
        this.corpId = OaonesUtil.s(corpId).trim();
        this.corpSecret = OaonesUtil.s(corpSecret).trim();
    }

    private String token() throws Exception {
        if (client == null) {
            client = new WeComApi(corpId, corpSecret);
        }
        return client.getAccessToken();
    }

    @Override
    public List<AttPunch> list(AttPullContext ctx) throws Exception {
        if (ctx.getUserIds().isEmpty()) {
            return new ArrayList<AttPunch>();
        }
        requireCredential();
        OaonesApiLog.httpContext(OaonesConst.TYPE_WECHAT, corpId);
        try {
            return doList(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private List<AttPunch> doList(AttPullContext ctx) throws Exception {
        List<AttPunch> result = new ArrayList<AttPunch>();
        String token = token();
        String url = OaonesProps.wxApiBase() + OaonesProps.attWeComDataPath()
                + "?access_token=" + OaonesUtil.urlEncode(token);
        int batch = OaonesProps.attSyncUserBatch();
        for (int i = 0; i < ctx.getUserIds().size(); i += batch) {
            List<String> users = ctx.getUserIds().subList(i, Math.min(i + batch, ctx.getUserIds().size()));
            JSONObject body = new JSONObject();
            body.put("opencheckindatatype", 3);
            body.put("starttime", dayStartSeconds(ctx.getFromDate()));
            body.put("endtime", dayEndSeconds(ctx.getToDate()));
            body.put("useridlist", users);
            JSONObject resp = OaonesHttp.postJson(url, body);
            if (resp.getIntValue("errcode") != 0) {
                throw new RuntimeException("企业微信打卡数据接口返回错误：" + resp.toJSONString());
            }
            JSONArray data = resp.getJSONArray("checkindata");
            if (data == null) {
                data = resp.getJSONArray("data");
            }
            if (data == null) {
                continue;
            }
            for (int k = 0; k < data.size(); k++) {
                JSONObject row = data.getJSONObject(k);
                AttPunch punch = toPunch(row);
                if (punch != null) {
                    result.add(punch);
                }
            }
            if (result.size() >= ctx.getLimit()) {
                break;
            }
        }
        return result;
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
        OaonesApiLog.httpContext(OaonesConst.TYPE_WECHAT, corpId);
        try {
            String token = token();
            List<String> users = ctx.getUserIds();
            String today = OaonesUtil.now().substring(0, 10);

            // 第 1 步：读考勤组 / 打卡规则。
            // getcheckinoption 的 useridlist 是必填字段，租户还没有已绑定成员时没有成员可问
            //（发空数组只会拿到 40058），这种情况改读「企业所有打卡规则」——同样是打卡权限范围内的接口，
            // 零绑定的租户也能真正校验到有没有考勤数据读取权限。
            out.put("step", "groups");
            if (users.isEmpty()) {
                JSONObject corpResp = OaonesHttp.postJson(
                        OaonesProps.wxApiBase() + OaonesProps.attWeComCorpGroupPath()
                                + "?access_token=" + OaonesUtil.urlEncode(token), new JSONObject());
                if (corpResp.getIntValue("errcode") != 0) {
                    out.put("message", "读取考勤组失败（企业微信原文）：" + corpResp.toJSONString());
                    return out;
                }
                JSONArray corpGroups = corpResp.getJSONArray("group");
                int corpCount = corpGroups == null ? 0 : corpGroups.size();
                out.put("groups", corpCount);
                out.put("punch_count", 0);
                out.put("ok", true);
                out.put("message", "鉴权与考勤组读取正常（企业考勤规则 " + corpCount + " 条）；"
                        + "该租户还没有已绑定成员，跳过打卡数据试读"
                        + "（绑定成员后会自动读出打卡数据）");
                return out;
            }
            JSONObject groupBody = new JSONObject();
            // datetime = 要取规则的那天“当天 0 点”的 Unix 秒；useridlist 是文档里的字段名
            groupBody.put("datetime", dayStartSeconds(today));
            JSONArray firstUsers = new JSONArray();
            firstUsers.add(users.get(0));
            groupBody.put("useridlist", firstUsers);
            JSONObject groupResp = OaonesHttp.postJson(
                    OaonesProps.wxApiBase() + OaonesProps.attWeComGroupPath()
                            + "?access_token=" + OaonesUtil.urlEncode(token), groupBody);
            if (groupResp.getIntValue("errcode") != 0) {
                out.put("message", "读取考勤组失败（企业微信原文）：" + groupResp.toJSONString());
                return out;
            }
            JSONArray infos = groupResp.getJSONArray("info");
            int groups = infos == null ? 0 : infos.size();
            out.put("groups", groups);

            out.put("step", "data");
            // 试读只取一批人（平台单次最多 100 个 useridlist，这里按配置的分批上限来）：
            // 校验只要证明“能读到打卡数据”，没必要把整个租户的人一次发过去。
            int probeBatch = OaonesProps.attSyncUserBatch();
            List<String> probeUsers = users.size() > probeBatch ? users.subList(0, probeBatch) : users;
            JSONObject dataBody = new JSONObject();
            dataBody.put("opencheckindatatype", 3);
            dataBody.put("starttime", dayStartSeconds(today));
            dataBody.put("endtime", dayEndSeconds(today));
            dataBody.put("useridlist", probeUsers);
            JSONObject dataResp = OaonesHttp.postJson(
                    OaonesProps.wxApiBase() + OaonesProps.attWeComDataPath()
                            + "?access_token=" + OaonesUtil.urlEncode(token), dataBody);
            if (dataResp.getIntValue("errcode") != 0) {
                out.put("message", "读取打卡数据失败（企业微信原文）：" + dataResp.toJSONString());
                return out;
            }
            JSONArray data = dataResp.getJSONArray("checkindata");
            out.put("punch_count", data == null ? 0 : data.size());
            out.put("ok", true);
            out.put("message", "校验通过：考勤组 " + groups + " 个，今日读到打卡数据 "
                    + (data == null ? 0 : data.size()) + " 条");
            return out;
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    /**
     * 把企微的一条打卡记录归一化。
     *
     * <p>checkin_type 的口径（企微原文）：上班打卡 / 下班打卡 / 外出 / 出差；
     * 含“补卡”字样的记录按补签卡处理（企微的补卡走审批，打卡流水里体现为一条打卡记录，
     * 因此这里按类型文案兼容识别，识别不出就按正常打卡写入并标注“平台类型未知”，需求 Q5）。</p>
     */
    private AttPunch toPunch(JSONObject row) {
        String wxUserId = OaonesUtil.s(row.getString("userid"));
        long ts = row.getLongValue("checkin_time");
        if (ts <= 0L) {
            return null;
        }
        String checkinType = OaonesUtil.s(row.getString("checkin_type"));
        AttPunch punch = new AttPunch();
        punch.setWxUserId(wxUserId);
        punch.setSignTime(OaonesUtil.formatDateTime(ts > 100000000000L ? ts : ts * 1000L));
        punch.setSignDate(punch.getSignTime().length() >= 10 ? punch.getSignTime().substring(0, 10) : "");
        punch.setSignType(checkinType.contains("下班") ? Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_OUT)
                : Integer.valueOf(OaonesConst.ATT_PERIOD_SIGN_IN));
        if (checkinType.contains("补卡") || checkinType.contains("补签")) {
            punch.setDataType(Integer.valueOf(OaonesConst.ATT_DATA_REMEDY));
            punch.setRawType("补卡（企业微信：" + checkinType + "）");
        } else {
            punch.setDataType(Integer.valueOf(OaonesConst.ATT_DATA_NORMAL));
            punch.setRawType(checkinType.length() == 0 ? "平台类型未知" : checkinType);
        }
        punch.setWxRecordId("");
        punch.setRawSummary(summary(row, new String[]{"location_title", "device_name", "wifiname", "notes"}));
        return punch;
    }

    private String summary(JSONObject row, String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            String value = OaonesUtil.s(row.getString(field));
            if (value.length() > 0) {
                if (sb.length() > 0) {
                    sb.append("、");
                }
                sb.append(field).append("=").append(value);
            }
        }
        return sb.length() > 60 ? sb.substring(0, 60) + "…" : sb.toString();
    }

    private void requireCredential() {
        if (corpId.length() == 0 || corpSecret.length() == 0) {
            throw new RuntimeException("企业微信考勤应用缺少企业标识 / Secret，无法读取打卡数据");
        }
    }

    /** “当天 0 点”的 Unix 秒（企微要的是真正的 Unix 时间戳，按东八区挂钟时间算，见类注）。 */
    private long dayStartSeconds(String date) {
        return daySeconds(date, " 00:00:00");
    }

    /** “当天 23:59:59”的 Unix 秒（同上）。 */
    private long dayEndSeconds(String date) {
        return daySeconds(date, " 23:59:59");
    }

    private long daySeconds(String date, String time) {
        long ms = OaonesUtil.parseDateTimeTgz(OaonesUtil.s(date) + time);
        return ms <= 0L ? 0L : ms / 1000L;
    }

    /**
     * OA 那条打卡的“实际打卡时间”→ Unix 秒（同样按东八区挂钟时间换算）。
     * 优先用 E9 的完整时间文本，取不到再拿“日期 + 时间”拼（OA 的时间列可能只有 {@code HH:mm}）。
     */
    private long punchSeconds(AttOaPunch punch) {
        if (punch == null) {
            return 0L;
        }
        long ms = OaonesUtil.parseDateTimeTgz(OaonesUtil.s(punch.getSignDatetime()));
        if (ms <= 0L) {
            String date = OaonesUtil.s(punch.getSignDate());
            String time = OaonesUtil.s(punch.getSignTime());
            if (date.length() > 0 && time.length() > 0) {
                ms = OaonesUtil.parseDateTimeTgz(date + " " + time);
            }
        }
        return ms <= 0L ? 0L : ms / 1000L;
    }

    // ---------------- 反向写入（OA → 第三方） ----------------

    /**
     * 按当前配置的落地方式组装写入报文（页面上的“报文预览”与实际下发共用这一份代码）：
     * {@code direct} 走「添加打卡记录」、{@code card} 走「为打卡人员补卡」，
     * 其它值（{@code none} / 未配置）走兜底口径（显式标记不支持）。
     */
    @Override
    public AttPushRequest buildRequest(AttPushContext ctx) {
        String mode = OaonesUtil.s(ctx == null ? "" : ctx.getMode());
        if (OaonesConst.ATT_PUSH_MODE_DIRECT.equalsIgnoreCase(mode)) {
            return buildAddRecordRequest(ctx);
        }
        if (OaonesConst.ATT_PUSH_MODE_CARD.equalsIgnoreCase(mode)) {
            return buildPunchCorrectionRequest(ctx);
        }
        return buildUnsupportedRequest(ctx);
    }

    /**
     * direct：调用「添加打卡记录」把 OA 那条打卡写成平台的一条打卡记录。
     *
     * <p>平台把 {@code location_title / location_detail / device_type / device_detail} 都列为必填，
     * 且没有“由接口写入”这一档设备类型，因此按配置写固定值（默认地点“OA考勤同步”、
     * 设备类型 3 = 其他）——不伪造门禁 / 考勤机。</p>
     */
    private AttPushRequest buildAddRecordRequest(AttPushContext ctx) {
        AttPushRequest req = new AttPushRequest();
        req.setMethod("POST");
        req.setPath(OaonesProps.attWeComAddRecordPath());
        AttOaPunch punch = ctx == null ? null : ctx.getPunch();
        String userId = OaonesUtil.s(ctx == null ? "" : ctx.getWxUserId()).trim();
        long seconds = punchSeconds(punch);
        String locationTitle = OaonesProps.attPushWeComLocationTitle();
        String locationDetail = OaonesProps.attPushWeComLocationDetail();
        String deviceDetail = OaonesProps.attPushWeComDeviceDetail();
        JSONObject record = new JSONObject();
        record.put("userid", userId);
        record.put("checkin_time", Long.valueOf(seconds));
        record.put("location_title", locationTitle);
        record.put("location_detail", locationDetail);
        record.put("device_type", Integer.valueOf(OaonesProps.attPushWeComDeviceType()));
        record.put("device_detail", deviceDetail);
        String notes = OaonesProps.attPushWeComNotes();
        if (notes.length() > 0) {
            record.put("notes", notes);
        }
        JSONArray records = new JSONArray();
        records.add(record);
        JSONObject body = new JSONObject();
        body.put("records", records);
        req.setBody(body.toJSONString());
        req.note("records[0].userid", "该 OA 人员绑定的企业微信成员 userid");
        req.note("records[0].checkin_time", "OA 那条打卡的时间（Unix 秒，按东八区挂钟时间换算）");
        req.note("records[0].location_title", "打卡地点标题（必填，固定值来自 "
                + "oaones.properties#att.push.wecom.location.title）");
        req.note("records[0].location_detail", "打卡地点详情（必填，固定值来自 "
                + "oaones.properties#att.push.wecom.location.detail）");
        req.note("records[0].device_type", "打卡设备类型（必填，1 门禁 / 2 考勤机 / 3 其他；"
                + "固定值来自 oaones.properties#att.push.wecom.device.type）");
        req.note("records[0].device_detail", "打卡设备品牌（必填，固定值来自 "
                + "oaones.properties#att.push.wecom.device.detail）");
        if (userId.length() == 0) {
            req.setPushError("该人员没有绑定企业微信账号，无法写入打卡记录");
        } else if (seconds <= 0L) {
            req.setPushError("取不到这条 OA 打卡的时间，无法写入打卡记录");
        } else if (locationTitle.length() == 0 || locationDetail.length() == 0 || deviceDetail.length() == 0) {
            req.setPushError("未配置企业微信打卡写入必填的打卡地点 / 设备（att.push.wecom.*），该方向暂不可用");
        }
        return req;
    }

    /**
     * card：调用「为打卡人员补卡」，按 OA 那条打卡帮员工补一次卡。
     *
     * <p>{@code schedule_date_time} 是**应打卡日期当天 0 点**的 Unix 秒（必填，取 OA 那条打卡的日期），
     * {@code checkin_time} 是实际打卡时间；{@code schedule_checkin_time}（应打卡时间点）是可选参数，
     * 不填时平台按该员工的打卡规则自行判定正常 / 迟到 / 早退。</p>
     */
    private AttPushRequest buildPunchCorrectionRequest(AttPushContext ctx) {
        AttPushRequest req = new AttPushRequest();
        req.setMethod("POST");
        req.setPath(OaonesProps.attWeComPunchCorrectionPath());
        AttOaPunch punch = ctx == null ? null : ctx.getPunch();
        String userId = OaonesUtil.s(ctx == null ? "" : ctx.getWxUserId()).trim();
        long scheduleDate = punch == null ? 0L : dayStartSeconds(punch.getSignDate());
        long seconds = punchSeconds(punch);
        JSONObject body = new JSONObject();
        body.put("userid", userId);
        body.put("schedule_date_time", Long.valueOf(scheduleDate));
        body.put("checkin_time", Long.valueOf(seconds));
        String remark = "OA考勤同步：" + OaonesUtil.s(ctx == null ? "" : ctx.getOaUserName())
                + " " + OaonesUtil.s(punch == null ? "" : punch.getSignDatetime());
        body.put("remark", remark.trim());
        req.setBody(body.toJSONString());
        req.note("userid", "该 OA 人员绑定的企业微信成员 userid");
        req.note("schedule_date_time", "应打卡日期：OA 那条打卡的日期当天 0 点的 Unix 秒");
        req.note("checkin_time", "实际打卡时间：OA 那条打卡的时间（Unix 秒，按东八区挂钟时间换算）");
        req.note("remark", "备注（平台侧在补卡记录上可见，便于核对来源）");
        req.note("（未下发）schedule_checkin_time", "应打卡时间点，可选参数；不填时平台按打卡规则判定正常 / 迟到 / 早退");
        if (userId.length() == 0) {
            req.setPushError("该人员没有绑定企业微信账号，无法提交补卡");
        } else if (scheduleDate <= 0L || seconds <= 0L) {
            req.setPushError("取不到这条 OA 打卡的日期 / 时间，无法提交补卡");
        }
        return req;
    }

    /** 兜底（none / 未配置）：显式标记不支持，明细写“跳过”而不是假装成功。 */
    private AttPushRequest buildUnsupportedRequest(AttPushContext ctx) {
        AttPushRequest req = new AttPushRequest();
        req.setMethod("POST");
        req.setPath(OaonesProps.attWeComDataPath());
        req.setBody("");
        req.setPushError("企业微信反向写入当前配置为" + OaonesProps.attPushMode(OaonesConst.TYPE_WECHAT)
                + "（未启用），该方向暂不写入");
        req.note("（说明）", "企业微信打卡已开放两个写接口：direct =「添加打卡记录」"
                + "（/cgi-bin/checkin/add_checkin_record）、card =「为打卡人员补卡」"
                + "（/cgi-bin/checkin/punch_correction）；当前 att.push.mode.wecom 不是 direct / card，"
                + "因此按兜底口径处理：显式标记不支持，不做“看起来在同步、其实没写成”的假成功");
        AttOaPunch punch = ctx == null ? null : ctx.getPunch();
        if (punch != null) {
            req.note("userid", "拟写入的成员：" + OaonesUtil.s(ctx.getWxUserId()));
            req.note("checkin_time", "拟写入的打卡时间：" + OaonesUtil.s(punch.getSignDatetime()));
        }
        return req;
    }

    @Override
    public String push(AttPushContext ctx) throws Exception {
        String mode = OaonesUtil.s(ctx == null ? "" : ctx.getMode());
        boolean direct = OaonesConst.ATT_PUSH_MODE_DIRECT.equalsIgnoreCase(mode);
        boolean card = OaonesConst.ATT_PUSH_MODE_CARD.equalsIgnoreCase(mode);
        if (!direct && !card) {
            // 兜底口径（none / 未配置）：不发明文请求，直接按“不支持”记失败（引擎在 none 时已先跳过）
            throw new RuntimeException(capabilityNote(ctx));
        }
        AttPushRequest req = buildRequest(ctx);
        if (!OaonesUtil.s(req.getPushError()).isEmpty()) {
            throw new RuntimeException(req.getPushError());
        }
        if (corpId.length() == 0 || corpSecret.length() == 0) {
            throw new RuntimeException("企业微信考勤应用缺少企业标识 / Secret，无法写入打卡");
        }
        OaonesApiLog.httpContext(OaonesConst.TYPE_WECHAT, corpId);
        try {
            String token = token();
            String url = OaonesProps.wxApiBase() + req.getPath()
                    + "?access_token=" + OaonesUtil.urlEncode(token);
            JSONObject resp = OaonesHttp.postJson(url, JSONObject.parseObject(req.getBody()));
            if (resp.getIntValue("errcode") != 0) {
                throw new RuntimeException("企业微信打卡写入接口返回错误：" + resp.toJSONString());
            }
            // 两个写接口都只回 errcode / errmsg，不返回记录 ID，所以这里没有平台凭据可回填
            return "";
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    @Override
    public String capabilityNote(AttPushContext ctx) {
        String mode = OaonesUtil.s(ctx == null ? "" : ctx.getMode());
        if (mode.length() == 0) {
            // 调用方没带 mode 时按配置兜底，避免出现“配置是 card、说明却说未启用”的自相矛盾
            mode = OaonesProps.attPushMode(OaonesConst.TYPE_WECHAT);
        }
        if (OaonesConst.ATT_PUSH_MODE_DIRECT.equalsIgnoreCase(mode)) {
            OaonesLog.step("[attSync] 企业微信反向写入走「添加打卡记录」（direct）");
            return "企业微信：走「添加打卡记录」直接写入（打卡地点 / 设备按 att.push.wecom.* 的固定值，"
                    + "平台侧是一条正常打卡记录；接口限 60 次/分钟）";
        }
        if (OaonesConst.ATT_PUSH_MODE_CARD.equalsIgnoreCase(mode)) {
            OaonesLog.step("[attSync] 企业微信反向写入走「为打卡人员补卡」（card）");
            return "企业微信：走「为打卡人员补卡」写入（平台侧记为补卡，打卡时间取 OA 那条打卡；限 600 次/分钟）";
        }
        OaonesLog.step("[attSync] 企业微信反向写入未启用（att.push.mode.wecom=" + mode + "）");
        return "企业微信：打卡接口已开放「添加打卡记录」与「为打卡人员补卡」，"
                + "当前 att.push.mode.wecom=" + (mode.length() == 0 ? "none" : mode)
                + "（未启用反向写入；改成 direct / card 即生效）";
    }
}
