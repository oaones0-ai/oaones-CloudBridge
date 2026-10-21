package com.customization.oaones.appsyn.api.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesHttp;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dto.MtgOaItem;
import com.customization.oaones.appsyn.dto.MtgPlatformItem;
import com.customization.oaones.appsyn.dto.MtgPushRequest;
import com.customization.oaones.appsyn.service.MtgPushAdapter;
import com.customization.oaones.appsyn.service.MtgPushContext;
import com.customization.oaones.appsyn.service.MtgPullAdapter;
import com.customization.oaones.appsyn.service.MtgPullContext;

/**
 * 钉钉「日程」接口适配器：把一条 OA 会议 / 日程 推到钉钉日程。
 *
 * <p>用租户的 AppKey + AppSecret 换 access_token，调
 * {@code /topapi/calendar/v2/event/create|update}。组织者用 {@code unionid} 字段、
 * 参与人用 {@code attendees[].id}（成员ID 由同步引擎按人员绑定从 OA 人员ID 换算好）。</p>
 *
 * <p><b>现场核对点</b>：钉钉日程接口的 {@code unionid} / {@code attendees[].id} 口径
 * 与企业通讯录 userid 不完全一致（部分版本要求 unionid），上线前用真实租户跑一次
 * “立即同步”，按同步记录里的返回信息核对；如返回 ID 口径不符，改这里在推送前
 * 用 {@code DingTalkApi#getUserIdByUnionId} 之类接口换算即可，不影响页面与记录。</p>
 */
public class DingTalkScheduleApi implements MtgPushAdapter, MtgPullAdapter {

    private static final String TIME_ZONE = "Asia/Shanghai";

    /** 同一个适配器实例（一次同步一个租户）复用同一个 API 客户端，token 只取一次。 */
    private DingTalkApi client;

    @Override
    public String push(MtgPushContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_DINGTALK, ctx == null ? "" : ctx.getCorpId());
        try {
            return doPush(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private String doPush(MtgPushContext ctx) throws Exception {
        if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
            throw new RuntimeException("钉钉租户缺少 AppKey / AppSecret，无法推送日程");
        }
        MtgPushRequest req = buildRequest(ctx);
        if (!isBlank(req.getPushError())) {
            // 组装阶段发现的问题（如没有开始时间）在真正推送时照旧抛出，失败原因与以前完全一致
            throw new RuntimeException(req.getPushError());
        }
        if (client == null) {
            client = new DingTalkApi(ctx.getCorpId(), ctx.getCorpSecret());
        }
        String token = client.getAccessToken();
        String url = OaonesProps.ddApiBase() + req.getPath()
                + "?access_token=" + OaonesUtil.urlEncode(token);
        JSONObject resp = OaonesHttp.postJson(url, req.getBody());
        if (resp.getIntValue("errcode") != 0) {
            throw new RuntimeException("钉钉日程接口返回错误：" + resp.toJSONString());
        }
        JSONObject result = resp.getJSONObject("result");
        String id = result == null ? null : result.getString("id");
        return id == null ? "" : id;
    }

    /**
     * 组装钉钉「新增 / 更新日程」请求报文（页面「报文」预览与实际下发共用这一份）。
     * 组织者进 {@code unionid}、参与人进 {@code attendees[].id}，时间用 {@code {date_time, time_zone}}。
     */
    @Override
    public MtgPushRequest buildRequest(MtgPushContext ctx) {
        boolean update = ctx.isUpdate() && !isBlank(ctx.getExistWxId());
        MtgPushRequest req = new MtgPushRequest();
        req.setMethod("POST");
        req.setPath(update ? "/topapi/calendar/v2/event/update" : "/topapi/calendar/v2/event/create");
        MtgOaItem item = ctx.getItem();
        long start = OaonesUtil.parseDateTime(item.getBeginTime());
        long end = OaonesUtil.parseDateTime(item.getEndTime());
        // 没有可用开始时间：页面「报文」预览仍要把报文显示出来，所以不在这里抛异常——
        // 原因挂到报文上（push() 照旧抛，失败原因与同步记录里那句完全一致），时间按 0 组装
        boolean noTime = start <= 0L;
        if (noTime) {
            req.setPushError("会议 / 日程缺少开始时间，无法推送");
            start = 0L;
            end = 0L;
        } else if (end <= start) {
            end = start + 3600L * 1000L;
        }

        JSONObject body = new JSONObject();
        if (update) {
            body.put("id", ctx.getExistWxId());
            req.note("id", "更新时必填：此前推送到钉钉的日程 ID（即列表里的“平台日程ID”）");
        }
        body.put("summary", isBlank(item.getTitle()) ? "未命名" : item.getTitle());
        req.note("summary", "日程标题：取 OA 标题，为空时用“未命名”");
        body.put("description", buildDescription(item));
        req.note("description", "日程正文：OA 说明 + 地点（有地点时另起一行“地点：…”）");
        body.put("start", timeObj(start));
        req.note("start", noTime
                ? "开始时间：OA 里没有可用的开始时间（按 0 组装）；实际同步会先失败：" + req.getPushError()
                : "开始时间：{date_time 毫秒时间戳, time_zone 固定 Asia/Shanghai}");
        body.put("end", timeObj(end));
        req.note("end", noTime
                ? "结束时间：同上，按 0 组装"
                : "结束时间：同上；结束早于开始（或缺失）时按开始 + 1 小时");
        // 组织者：钉钉日程接口用 unionid 字段；绑定表里存的是 userid，先按原值下发，现场核对后如需换算在此处理
        if (!isBlank(ctx.getOwnerUserId())) {
            body.put("unionid", ctx.getOwnerUserId());
            req.note("unionid", "组织者：OA 创建人按人员绑定换算出的成员ID（现场口径要求 unionid 时在此换算）");
        }
        JSONArray attendees = new JSONArray();
        for (String uid : ctx.getAttendeeUserIds()) {
            if (!isBlank(uid)) {
                attendees.add(new JSONObject().fluentPut("id", uid));
            }
        }
        if (!attendees.isEmpty()) {
            body.put("attendees", attendees);
            req.note("attendees[].id", "参与人：OA 参与人按人员绑定换算出的成员ID，"
                    + "未绑定或本人选择“不同步”的已剔除（本次 " + ctx.getAttendeeUserIds().size() + " 人）");
        }

        req.getBody().putAll(body);
        return req;
    }

    private JSONObject timeObj(long millis) {
        return new JSONObject().fluentPut("date_time", millis).fluentPut("time_zone", TIME_ZONE);
    }

    private String buildDescription(MtgOaItem item) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(item.getContent())) {
            sb.append(item.getContent());
        }
        if (!isBlank(item.getLocation())) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("地点：").append(item.getLocation());
        }
        return sb.toString();
    }

    // ==================================================================
    // 删除平台日程（源端删除同步到接收方：OA 删了 → 钉钉也删）
    // ==================================================================

    /**
     * 删除钉钉日程：{@code POST /topapi/calendar/v2/event/delete}，body 用 {@code id}
     * （即映射表里的 {@code wx_id}）。
     *
     * <p><b>现场核对点</b>：钉钉 v2 日程接口里“删除”的路径与入参字段名（{@code id} /
     * {@code calendar_id}）随版本略有差异——首次上线若报“接口不存在 / 参数错误”，
     * 按当时文档只改本方法即可，不影响引擎、页面与同步记录。</p>
     */
    @Override
    public void delete(MtgPushContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_DINGTALK, ctx == null ? "" : ctx.getCorpId());
        try {
            if (ctx == null || isBlank(ctx.getExistWxId())) {
                throw new RuntimeException("缺少钉钉日程ID，无法删除");
            }
            if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
                throw new RuntimeException("钉钉租户缺少 AppKey / AppSecret，无法删除日程");
            }
            if (client == null) {
                client = new DingTalkApi(ctx.getCorpId(), ctx.getCorpSecret());
            }
            String token = client.getAccessToken();
            String url = OaonesProps.ddApiBase()
                    + "/topapi/calendar/v2/event/delete?access_token=" + OaonesUtil.urlEncode(token);
            JSONObject body = new JSONObject();
            body.put("id", ctx.getExistWxId());
            JSONObject resp = OaonesHttp.postJson(url, body);
            if (resp.getIntValue("errcode") != 0) {
                throw new RuntimeException("钉钉日程删除接口返回错误：" + resp.toJSONString());
            }
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    // ==================================================================
    // 第三方→OA 拉取：读某个日历的日程（/topapi/calendar/v2/event/list）
    // ==================================================================

    /**
     * 从钉钉拉取时间窗内的日程：对每个日历（默认用成员 unionid 作 calendar_id）调
     * {@code /topapi/calendar/v2/event/list} 分页读取。
     *
     * <p><b>现场核对点</b>：钉钉日程列表接口的请求字段（{@code unionid} / {@code calendar_id} /
     * {@code time_min} / {@code time_max}）与返回结构（{@code result.items} 等）随版本略有差异，
     * 这里做了兼容读取（items / events / event_list，时间既支持毫秒也支持 {@code {date_time}} 对象）；
     * 上线前用真实租户核对一次，如字段不符只改本方法，不影响引擎与页面。</p>
     */
    @Override
    public java.util.List<MtgPlatformItem> list(MtgPullContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_DINGTALK, ctx == null ? "" : ctx.getCorpId());
        try {
            return doList(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private java.util.List<MtgPlatformItem> doList(MtgPullContext ctx) throws Exception {
        if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
            throw new RuntimeException("钉钉租户缺少 AppKey / AppSecret，无法拉取日程");
        }
        if (client == null) {
            client = new DingTalkApi(ctx.getCorpId(), ctx.getCorpSecret());
        }
        String token = client.getAccessToken();
        String url = OaonesProps.ddApiBase()
                + "/topapi/calendar/v2/event/list?access_token=" + OaonesUtil.urlEncode(token);
        long from = OaonesUtil.parseDateTime(ctx.getFromTime());
        long to = OaonesUtil.parseDateTime(ctx.getToTime());
        int limit = Math.max(1, ctx.getLimit());
        java.util.List<MtgPlatformItem> all = new java.util.ArrayList<MtgPlatformItem>();
        for (String calId : ctx.getCalendarIds()) {
            if (isBlank(calId) || all.size() >= limit) {
                continue;
            }
            int offset = 0;
            while (all.size() < limit) {
                JSONObject body = new JSONObject();
                body.put("unionid", calId);
                body.put("calendar_id", calId);
                body.put("time_min", from > 0L ? from : 0L);
                body.put("time_max", to > 0L ? to : 0L);
                body.put("limit", 100);
                body.put("offset", offset);
                JSONObject resp;
                try {
                    resp = OaonesHttp.postJson(url, body);
                } catch (Exception e) {
                    break;
                }
                if (resp.getIntValue("errcode") != 0) {
                    break;
                }
                // 这个日历本轮确实取到了结果：删除对账只在这种日历上推断“平台侧已删除”
                ctx.markListed(calId);
                JSONArray list = readEventArray(resp.getJSONObject("result"));
                if (list == null || list.isEmpty()) {
                    break;
                }
                for (int i = 0; i < list.size(); i++) {
                    MtgPlatformItem item = parseEvent(list.getJSONObject(i), calId);
                    if (item != null) {
                        all.add(item);
                    }
                }
                offset += list.size();
                if (list.size() < 100) {
                    break;
                }
            }
        }
        return all;
    }

    /** 兼容读取钉钉返回的事件数组（result.items / result.events / result.event_list）。 */
    private JSONArray readEventArray(JSONObject result) {
        if (result == null) {
            return null;
        }
        JSONArray list = result.getJSONArray("items");
        if (list == null) {
            list = result.getJSONArray("events");
        }
        if (list == null) {
            list = result.getJSONArray("event_list");
        }
        return list;
    }

    /** 钉钉日程 JSON → 统一载体（时间兼容毫秒数与 {date_time} 对象）。 */
    private MtgPlatformItem parseEvent(JSONObject src, String calId) {
        String id = OaonesUtil.s(src.getString("id"));
        if (id.length() == 0) {
            id = OaonesUtil.s(src.getString("event_id"));
        }
        if (id.length() == 0) {
            return null;
        }
        MtgPlatformItem item = new MtgPlatformItem();
        item.setWxId(id);
        item.setTitle(src.getString("summary"));
        item.setContent(src.getString("description"));
        item.setBeginTime(OaonesUtil.formatDateTime(readMillis(src.get("start"))));
        item.setEndTime(OaonesUtil.formatDateTime(readMillis(src.get("end"))));
        item.setLocation(src.getString("location"));
        String organizer = OaonesUtil.s(src.getString("organizer"));
        if (organizer.length() == 0) {
            organizer = OaonesUtil.s(src.getString("unionid"));
        }
        item.setOwnerWxId(organizer.length() > 0 ? organizer : calId);
        JSONArray attendees = src.getJSONArray("attendees");
        if (attendees != null) {
            for (int i = 0; i < attendees.size(); i++) {
                Object one = attendees.get(i);
                String uid = "";
                if (one instanceof JSONObject) {
                    uid = OaonesUtil.s(((JSONObject) one).getString("id"));
                    if (uid.length() == 0) {
                        uid = OaonesUtil.s(((JSONObject) one).getString("unionid"));
                    }
                } else if (one != null) {
                    uid = OaonesUtil.s(String.valueOf(one));
                }
                if (uid.length() > 0) {
                    item.getAttendeeWxIds().add(uid);
                }
            }
        }
        return item;
    }

    /** 兼容读取时间：毫秒数 / {date_time:毫秒} / {date_time:"yyyy-MM-dd HH:mm:ss"}。 */
    private long readMillis(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof JSONObject) {
            Object dt = ((JSONObject) value).get("date_time");
            return dt == null ? 0L : readMillis(dt);
        }
        if (value instanceof Number) {
            long v = ((Number) value).longValue();
            // 钉钉口径是毫秒；个别接口给秒级时间戳（< 1e11）时按秒处理
            return v > 0L && v < 100000000000L ? v * 1000L : v;
        }
        String s = OaonesUtil.s(String.valueOf(value)).trim();
        if (s.length() == 0) {
            return 0L;
        }
        try {
            long v = Long.parseLong(s);
            return v > 0L && v < 100000000000L ? v * 1000L : v;
        } catch (NumberFormatException ignore) {
            return OaonesUtil.parseDateTime(s);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().length() == 0;
    }
}
