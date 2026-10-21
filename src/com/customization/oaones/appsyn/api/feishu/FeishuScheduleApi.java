package com.customization.oaones.appsyn.api.feishu;

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

import java.util.HashMap;
import java.util.Map;

/**
 * 飞书「日历」接口适配器：把一条 OA 会议 / 日程 推到飞书日历事件。
 *
 * <p>用租户的 App ID + App Secret 换 tenant_access_token，调
 * {@code POST /open-apis/calendar/v4/calendars/{calendar_id}/events}
 * （更新走 {@code PATCH .../events/{event_id}}）。</p>
 *
 * <p><b>现场核对点</b>：飞书日历接口要一个 {@code calendar_id}，这里默认用
 * {@code primary}（当前应用的默认日历）；若现场飞书不允许该别名，把它换成实际日历ID
 * （或改成“每租户一个日历ID”的配置）即可，不改同步引擎。</p>
 */
public class FeishuScheduleApi implements MtgPushAdapter, MtgPullAdapter {

    private static final String TIME_ZONE = "Asia/Shanghai";
    /** 目标日历：飞书默认日历别名；现场若不允许，改成实际 calendar_id。 */
    private static final String DEFAULT_CALENDAR_ID = "primary";

    /** 同一个适配器实例（一次同步一个租户）复用同一个 API 客户端，token 只取一次。 */
    private FeishuApi client;

    @Override
    public String push(MtgPushContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_FEISHU, ctx == null ? "" : ctx.getCorpId());
        try {
            return doPush(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private String doPush(MtgPushContext ctx) throws Exception {
        if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
            throw new RuntimeException("飞书租户缺少 App ID / App Secret，无法推送日程");
        }
        MtgPushRequest req = buildRequest(ctx);
        if (!isBlank(req.getPushError())) {
            // 组装阶段发现的问题（如没有开始时间）在真正推送时照旧抛出，失败原因与以前完全一致
            throw new RuntimeException(req.getPushError());
        }
        if (client == null) {
            client = new FeishuApi(ctx.getCorpId(), ctx.getCorpSecret());
        }
        String token = client.getAccessToken();
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + token);
        String url = OaonesProps.fsApiBase() + req.getPath();
        JSONObject resp = "PATCH".equalsIgnoreCase(req.getMethod())
                ? OaonesHttp.patchJson(url, req.getBody(), headers)
                : OaonesHttp.postJson(url, req.getBody(), headers);
        if (resp.getIntValue("code") != 0) {
            throw new RuntimeException("飞书日历接口返回错误：" + resp.toJSONString());
        }
        String id = readEventId(resp);
        return id == null ? "" : id;
    }

    /**
     * 组装飞书「新增 / 更新日历事件」请求报文（页面「报文」预览与实际下发共用这一份）。
     * 新增 POST 到日历 events、更新 PATCH 到 {@code events/{event_id}}，时间用秒级时间戳字符串。
     */
    @Override
    public MtgPushRequest buildRequest(MtgPushContext ctx) {
        MtgOaItem item = ctx.getItem();
        long start = com.customization.oaones.appsyn.common.OaonesUtil.parseDateTime(item.getBeginTime());
        long end = com.customization.oaones.appsyn.common.OaonesUtil.parseDateTime(item.getEndTime());
        // 没有可用开始时间：页面「报文」预览仍要把报文显示出来，所以不在这里抛异常——
        // 原因挂到报文上（push() 照旧抛，失败原因与同步记录里那句完全一致），时间按 0 组装
        boolean noTime = start <= 0L;
        if (noTime) {
            start = 0L;
            end = 0L;
        } else if (end <= start) {
            end = start + 3600L * 1000L;
        }

        boolean update = ctx.isUpdate() && !isBlank(ctx.getExistWxId());
        String base = "/open-apis/calendar/v4/calendars/" + DEFAULT_CALENDAR_ID + "/events";
        MtgPushRequest req = new MtgPushRequest();
        if (noTime) {
            req.setPushError("会议 / 日程缺少开始时间，无法推送");
        }
        req.setMethod(update ? "PATCH" : "POST");
        req.setPath(update ? (base + "/" + ctx.getExistWxId()) : base);

        JSONObject body = new JSONObject();
        body.put("summary", isBlank(item.getTitle()) ? "未命名" : item.getTitle());
        req.note("summary", "日程标题：取 OA 标题，为空时用“未命名”");
        body.put("description", buildDescription(item));
        req.note("description", "日程正文：OA 说明 + 地点（有地点时另起一行“地点：…”）");
        body.put("start_time", timeObj(start));
        req.note("start_time", noTime
                ? "开始时间：OA 里没有可用的开始时间（按 0 组装）；实际同步会先失败：" + req.getPushError()
                : "开始时间：{timestamp 秒级时间戳字符串, timezone 固定 Asia/Shanghai}");
        body.put("end_time", timeObj(end));
        req.note("end_time", noTime
                ? "结束时间：同上，按 0 组装"
                : "结束时间：同上；结束早于开始（或缺失）时按开始 + 1 小时");
        body.put("attendee_ability", "can_see_others");
        req.note("attendee_ability", "参与人权限：can_see_others = 参与人可互相看到（当前固定值）");
        req.note("（路径）calendar_id", "目标日历：固定 " + DEFAULT_CALENDAR_ID + "（应用默认日历；"
                + "现场不允许该别名时改成实际 calendar_id）");
        if (update) {
            req.note("（路径）event_id", "更新时带在路径上的平台事件 ID（即列表里的“平台日程ID”）");
        }

        req.getBody().putAll(body);
        return req;
    }

    private String readEventId(JSONObject resp) {
        JSONObject data = resp.getJSONObject("data");
        if (data == null) {
            return null;
        }
        JSONObject event = data.getJSONObject("event");
        if (event != null && event.getString("event_id") != null) {
            return event.getString("event_id");
        }
        return data.getString("event_id");
    }

    private JSONObject timeObj(long millis) {
        return new JSONObject()
                .fluentPut("timestamp", String.valueOf(millis / 1000L))
                .fluentPut("timezone", TIME_ZONE);
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
    // 删除平台日程（源端删除同步到接收方：OA 删了 → 飞书也删）
    // ==================================================================

    /**
     * 删除飞书日历事件：{@code DELETE /open-apis/calendar/v4/calendars/{calendar_id}/events/{event_id}}
     * （{@code calendar_id} 用推送时用的 {@code primary}，即本应用默认日历）。
     */
    @Override
    public void delete(MtgPushContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_FEISHU, ctx == null ? "" : ctx.getCorpId());
        try {
            if (ctx == null || isBlank(ctx.getExistWxId())) {
                throw new RuntimeException("缺少飞书日程ID（event_id），无法删除");
            }
            if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
                throw new RuntimeException("飞书租户缺少 App ID / App Secret，无法删除日程");
            }
            if (client == null) {
                client = new FeishuApi(ctx.getCorpId(), ctx.getCorpSecret());
            }
            String token = client.getAccessToken();
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "Bearer " + token);
            String url = OaonesProps.fsApiBase() + "/open-apis/calendar/v4/calendars/"
                    + OaonesUtil.urlEncode(DEFAULT_CALENDAR_ID) + "/events/"
                    + OaonesUtil.urlEncode(ctx.getExistWxId());
            JSONObject resp = OaonesHttp.deleteJson(url, headers);
            if (resp.getIntValue("code") != 0) {
                throw new RuntimeException("飞书日历事件删除接口返回错误：" + resp.toJSONString());
            }
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    // ==================================================================
    // 第三方→OA 拉取：读日历事件（GET /open-apis/calendar/v4/calendars/{id}/events）
    // ==================================================================

    /**
     * 从飞书拉取时间窗内的日历事件：对每个日历（含推送用的 {@code primary}）调
     * {@code GET /open-apis/calendar/v4/calendars/{calendar_id}/events} 分页读取，
     * 时间窗用 {@code start_time}/{@code end_time}（秒）。
     *
     * <p>{@code primary} 覆盖“此前由 OA 推送到飞书的日程”（推送落在这个日历），
     * 成员日历覆盖“平台自己新建的日程”。参与人再按需调 attendees 接口补齐（best-effort）。
     * <b>现场核对点</b>：飞书日历事件的组织者字段与 attendees 接口的 {@code user_id_type}
     * 随版本略有差异，这里兼容读取多个字段名；上线前核对一次，如不符只改本方法。</p>
     */
    @Override
    public java.util.List<MtgPlatformItem> list(MtgPullContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_FEISHU, ctx == null ? "" : ctx.getCorpId());
        try {
            return doList(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private java.util.List<MtgPlatformItem> doList(MtgPullContext ctx) throws Exception {
        if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
            throw new RuntimeException("飞书租户缺少 App ID / App Secret，无法拉取日程");
        }
        if (client == null) {
            client = new FeishuApi(ctx.getCorpId(), ctx.getCorpSecret());
        }
        String token = client.getAccessToken();
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + token);
        long from = OaonesUtil.parseDateTime(ctx.getFromTime());
        long to = OaonesUtil.parseDateTime(ctx.getToTime());
        int limit = Math.max(1, ctx.getLimit());
        java.util.LinkedHashSet<String> calendars = new java.util.LinkedHashSet<String>();
        calendars.add(DEFAULT_CALENDAR_ID);   // primary：覆盖 OA 推过去的日程
        for (String calId : ctx.getCalendarIds()) {
            if (!isBlank(calId)) {
                calendars.add(calId);
            }
        }
        java.util.List<MtgPlatformItem> all = new java.util.ArrayList<MtgPlatformItem>();
        for (String calId : calendars) {
            if (all.size() >= limit) {
                break;
            }
            String pageToken = "";
            while (all.size() < limit) {
                String url = OaonesProps.fsApiBase() + "/open-apis/calendar/v4/calendars/"
                        + OaonesUtil.urlEncode(calId) + "/events"
                        + "?start_time=" + (from / 1000L) + "&end_time=" + (to / 1000L) + "&page_size=100";
                if (!isBlank(pageToken)) {
                    url = url + "&page_token=" + OaonesUtil.urlEncode(pageToken);
                }
                JSONObject resp;
                try {
                    resp = OaonesHttp.getJson(url, headers);
                } catch (Exception e) {
                    break;
                }
                if (resp.getIntValue("code") != 0) {
                    break;
                }
                // 这个日历本轮确实取到了结果：删除对账只在这种日历上推断“平台侧已删除”
                ctx.markListed(calId);
                JSONObject data = resp.getJSONObject("data");
                JSONArray items = data == null ? null : data.getJSONArray("items");
                if (items == null || items.isEmpty()) {
                    break;
                }
                for (int i = 0; i < items.size(); i++) {
                    MtgPlatformItem item = parseEvent(items.getJSONObject(i), calId);
                    if (item == null) {
                        continue;
                    }
                    fillAttendees(item, calId, headers);
                    all.add(item);
                }
                pageToken = data == null ? "" : OaonesUtil.s(data.getString("page_token"));
                if (isBlank(pageToken) || !(data.getBooleanValue("has_more"))) {
                    break;
                }
            }
        }
        return all;
    }

    private String base(String calId) throws Exception {
        return OaonesProps.fsApiBase() + "/open-apis/calendar/v4/calendars/" + OaonesUtil.urlEncode(calId);
    }

    /** 飞书事件 JSON → 统一载体（start_time / end_time 是 {timestamp(秒), timezone}）。 */
    private MtgPlatformItem parseEvent(JSONObject src, String calId) {
        String id = OaonesUtil.s(src.getString("event_id"));
        if (id.length() == 0) {
            return null;
        }
        MtgPlatformItem item = new MtgPlatformItem();
        item.setWxId(id);
        item.setTitle(src.getString("summary"));
        item.setContent(src.getString("description"));
        item.setBeginTime(OaonesUtil.formatDateTimeSeconds(readTs(src.get("start_time"))));
        item.setEndTime(OaonesUtil.formatDateTimeSeconds(readTs(src.get("end_time"))));
        JSONObject loc = src.getJSONObject("location");
        item.setLocation(loc == null ? src.getString("location")
                : firstNonBlank(loc.getString("display_name"), loc.getString("name")));
        JSONObject org = src.getJSONObject("event_organizer");
        String owner = org == null ? "" : firstNonBlank(org.getString("user_id"), org.getString("open_id"));
        if (owner.length() == 0) {
            owner = firstNonBlank(src.getString("organizer_calendar_id"), calId);
        }
        item.setOwnerWxId(owner);
        return item;
    }

    /** 补参与人（best-effort）：GET .../events/{event_id}/attendees。 */
    private void fillAttendees(MtgPlatformItem item, String calId, Map<String, String> headers) {
        try {
            JSONObject resp = OaonesHttp.getJson(base(calId) + "/events/"
                    + OaonesUtil.urlEncode(item.getWxId()) + "/attendees?user_id_type=open_id&page_size=100",
                    headers);
            if (resp.getIntValue("code") != 0) {
                return;
            }
            JSONObject data = resp.getJSONObject("data");
            JSONArray items = data == null ? null : data.getJSONArray("items");
            if (items == null) {
                return;
            }
            for (int i = 0; i < items.size(); i++) {
                JSONObject one = items.getJSONObject(i);
                if (one == null) {
                    continue;
                }
                String uid = firstNonBlank(one.getString("open_id"), one.getString("user_id"));
                if (uid.length() > 0) {
                    item.getAttendeeWxIds().add(uid);
                }
            }
        } catch (Exception ignore) {
            // 参与人取不到不影响事件本身
        }
    }

    /** 兼容读取时间戳（秒）：{timestamp:"1700000000"} / 数字 / 字符串。 */
    private long readTs(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof JSONObject) {
            Object ts = ((JSONObject) value).get("timestamp");
            return ts == null ? 0L : readTs(ts);
        }
        String s = OaonesUtil.s(String.valueOf(value)).trim();
        if (s.length() == 0) {
            return 0L;
        }
        try {
            long v = Long.parseLong(s);
            // 毫秒（>1e11）折算成秒
            return v > 100000000000L ? v / 1000L : v;
        } catch (NumberFormatException ignore) {
            return OaonesUtil.parseDateTime(s) / 1000L;
        }
    }

    private String firstNonBlank(String a, String b) {
        return isBlank(a) ? OaonesUtil.s(b) : OaonesUtil.s(a);
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().length() == 0;
    }
}
