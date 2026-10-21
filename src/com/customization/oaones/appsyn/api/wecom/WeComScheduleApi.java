package com.customization.oaones.appsyn.api.wecom;

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
 * 企业微信「日程」接口适配器：把一条 OA 会议 / 日程 推到企业微信日程。
 *
 * <p>用租户的企业标识（corpid）与密钥（Secret）换 access_token，
 * 调 {@code /cgi-bin/oa/schedule/add|update}：组织者进 {@code admins}、参与人进 {@code attendees}
 * （成员ID 由同步引擎按人员绑定从 OA 人员ID 换算好），成功返回 {@code schedule_id}。</p>
 *
 * <p>口径见企业微信「日程管理」接口文档；只做“推一条日程”，不读回企业微信侧数据。</p>
 */
public class WeComScheduleApi implements MtgPushAdapter, MtgPullAdapter {

    /** 同一个适配器实例（一次同步一个租户）复用同一个 API 客户端，token 只取一次。 */
    private WeComApi client;

    @Override
    public String push(MtgPushContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_WECHAT, ctx == null ? "" : ctx.getCorpId());
        try {
            return doPush(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private String doPush(MtgPushContext ctx) throws Exception {
        if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
            throw new RuntimeException("企业微信租户缺少 corpid / Secret，无法推送日程");
        }
        MtgPushRequest req = buildRequest(ctx);
        if (!isBlank(req.getPushError())) {
            // 组装阶段发现的问题（如没有开始时间）在真正推送时照旧抛出，失败原因与以前完全一致
            throw new RuntimeException(req.getPushError());
        }
        if (client == null) {
            client = new WeComApi(ctx.getCorpId(), ctx.getCorpSecret());
        }
        String token = client.getAccessToken();
        String url = OaonesProps.wxApiBase() + req.getPath()
                + "?access_token=" + OaonesUtil.urlEncode(token);
        JSONObject resp = OaonesHttp.postJson(url, req.getBody());
        if (resp.getIntValue("errcode") != 0) {
            throw new RuntimeException("企业微信日程接口返回错误：" + resp.toJSONString());
        }
        String id = resp.getString("schedule_id");
        return id == null ? "" : id;
    }

    /**
     * 组装企微「新增 / 更新日程」请求报文（页面「报文」预览与实际下发共用这一份）。
     * 组织者进 {@code admins}、参与人进 {@code attendees}，时间用秒级时间戳。
     */
    @Override
    public MtgPushRequest buildRequest(MtgPushContext ctx) {
        boolean update = ctx.isUpdate() && !isBlank(ctx.getExistWxId());
        MtgPushRequest req = new MtgPushRequest();
        req.setMethod("POST");
        req.setPath(update ? "/cgi-bin/oa/schedule/update" : "/cgi-bin/oa/schedule/add");
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

        JSONObject schedule = new JSONObject();
        if (update) {
            schedule.put("schedule_id", ctx.getExistWxId());
            req.note("schedule.schedule_id", "更新时必填：此前推送到企业微信的日程 ID（即列表里的“平台日程ID”）");
        }
        schedule.put("summary", isBlank(item.getTitle()) ? "未命名" : item.getTitle());
        req.note("schedule.summary", "日程标题：取 OA 标题，为空时用“未命名”");
        schedule.put("description", buildDescription(item));
        req.note("schedule.description", "日程正文：OA 说明 + 地点（有地点时另起一行“地点：…”）"
                + "，末尾固定带一句“" + OaonesConst.MTG_FOREIGN_HINT + "”（提醒企微侧不要改，要改回 OA 改）");
        schedule.put("start_time", start / 1000L);
        req.note("schedule.start_time", noTime
                ? "开始时间：OA 里没有可用的开始时间（按 0 组装）；实际同步会先失败：" + req.getPushError()
                : "开始时间：OA 开始时间转 Unix 秒级时间戳");
        schedule.put("end_time", end / 1000L);
        req.note("schedule.end_time", noTime
                ? "结束时间：同上，按 0 组装"
                : "结束时间：秒级时间戳；结束早于开始（或缺失）时按开始 + 1 小时");
        schedule.put("reminders", new JSONObject().fluentPut("is_remind", 0));
        req.note("schedule.reminders.is_remind", "是否提醒：0 = 不提醒（当前固定不发提醒）");

        if (!isBlank(ctx.getOwnerUserId())) {
            JSONArray admins = new JSONArray();
            admins.add(ctx.getOwnerUserId());
            schedule.put("admins", admins);
            req.note("schedule.admins", "组织者：OA 创建人按人员绑定换算出的企微 userid");
        }
        JSONArray attendees = new JSONArray();
        for (String uid : ctx.getAttendeeUserIds()) {
            if (isBlank(uid)) {
                continue;
            }
            attendees.add(new JSONObject().fluentPut("userid", uid));
        }
        if (!attendees.isEmpty()) {
            schedule.put("attendees", attendees);
            req.note("schedule.attendees[].userid", "参与人：OA 参与人按人员绑定换算出的企微 userid，"
                    + "未绑定或本人选择“不同步”的已剔除（本次 " + ctx.getAttendeeUserIds().size() + " 人）");
        }

        req.getBody().put("schedule", schedule);
        return req;
    }

    /**
     * 正文：OA 说明 + 地点（有则拼上）+ 一句「非本平台创建」的提示。
     *
     * <p>提示语只在没有时才补（避免重复追加）；创建方（OA）后续更新说明时这句仍然保留。</p>
     */
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
        String text = sb.toString();
        if (text.indexOf(OaonesConst.MTG_FOREIGN_HINT) < 0) {
            text = text.length() == 0 ? OaonesConst.MTG_FOREIGN_HINT
                    : text + "\n" + OaonesConst.MTG_FOREIGN_HINT;
        }
        return text;
    }

    // ==================================================================
    // 删除平台日程（源端删除同步到接收方：OA 删了 → 企业微信也删）
    // ==================================================================

    /**
     * 删除企业微信日程：{@code POST /cgi-bin/oa/schedule/del}，body 用 {@code schedule_id}
     * （即映射表里的 {@code wx_id}）。
     *
     * <p>只有“OA 创建的对象”（映射 {@code source = 0}）会走到这里——企业微信创建、被拉到 OA 的日程
     * 不反向同步（见 {@code MtgSyncServiceImpl#reconcileDeletes}）。失败直接抛异常，由引擎按节流重试。</p>
     */
    @Override
    public void delete(MtgPushContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_WECHAT, ctx == null ? "" : ctx.getCorpId());
        try {
            if (ctx == null || isBlank(ctx.getExistWxId())) {
                throw new RuntimeException("缺少企业微信日程ID（schedule_id），无法删除");
            }
            if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
                throw new RuntimeException("企业微信租户缺少 corpid / Secret，无法删除日程");
            }
            if (client == null) {
                client = new WeComApi(ctx.getCorpId(), ctx.getCorpSecret());
            }
            String token = client.getAccessToken();
            String url = OaonesProps.wxApiBase()
                    + "/cgi-bin/oa/schedule/del?access_token=" + OaonesUtil.urlEncode(token);
            JSONObject body = new JSONObject();
            body.put("schedule_id", ctx.getExistWxId());
            JSONObject resp = OaonesHttp.postJson(url, body);
            if (resp.getIntValue("errcode") != 0) {
                throw new RuntimeException("企业微信日程删除接口返回错误：" + resp.toJSONString());
            }
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    // ==================================================================
    // 第三方→OA 拉取：读某个日历时程（/cgi-bin/oa/schedule/get_by_calendar）
    // ==================================================================

    /**
     * 从企业微信拉取时间窗内的日程：对每个日历（cal_id = 成员 userid）调
     * {@code /cgi-bin/oa/schedule/get_by_calendar} 分页读取，按 {@code start_time} 过滤时间窗。
     *
     * <p>企业微信没有“整租户日程列表”接口，只有“按日历取”，因此拉取按成员日历逐个读
     * （OA 推过去的日程由组织者（admins）持有，读组织者日历即可取到；参与人日历里的同一日程
     * 由引擎按 {@code schedule_id} 去重）。单个日历读取失败只跳过，不影响其它日历。</p>
     */
    @Override
    public java.util.List<MtgPlatformItem> list(MtgPullContext ctx) throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_WECHAT, ctx == null ? "" : ctx.getCorpId());
        try {
            return doList(ctx);
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    private java.util.List<MtgPlatformItem> doList(MtgPullContext ctx) throws Exception {
        if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
            throw new RuntimeException("企业微信租户缺少 corpid / Secret，无法拉取日程");
        }
        if (client == null) {
            client = new WeComApi(ctx.getCorpId(), ctx.getCorpSecret());
        }
        String token = client.getAccessToken();
        String url = OaonesProps.wxApiBase()
                + "/cgi-bin/oa/schedule/get_by_calendar?access_token=" + OaonesUtil.urlEncode(token);
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
                body.put("cal_id", calId);
                body.put("offset", offset);
                body.put("limit", 100);
                JSONObject resp;
                try {
                    resp = OaonesHttp.postJson(url, body);
                } catch (Exception e) {
                    // 单个日历失败（无权限 / 已离职等）只跳过
                    break;
                }
                if (resp.getIntValue("errcode") != 0) {
                    break;
                }
                // 这个日历本轮确实取到了结果：删除对账只在这种日历上推断“平台侧已删除”
                ctx.markListed(calId);
                JSONArray list = resp.getJSONArray("schedule_list");
                if (list == null || list.isEmpty()) {
                    break;
                }
                for (int i = 0; i < list.size(); i++) {
                    MtgPlatformItem item = parseSchedule(list.getJSONObject(i), calId);
                    if (item == null) {
                        continue;
                    }
                    long start = OaonesUtil.parseDateTime(item.getBeginTime());
                    if (start > 0L && from > 0L && (start < from || (to > 0L && start > to))) {
                        continue;
                    }
                    all.add(item);
                }
                offset += list.size();
                if (list.size() < 100) {
                    break;
                }
            }
        }
        return all;
    }

    // ==================================================================
    // 按日程ID取详情（订阅回调 / 删除对账用）
    // ==================================================================

    /**
     * 按日程ID取详情：{@code POST /cgi-bin/oa/schedule/get}，body 用 {@code schedule_id_list}（一次最多 1000 条）。
     *
     * <p>订阅回调（{@code modify_schedule} / {@code delete_schedule}）只带日程ID、不带内容，
     * 删除对账也要逐条确认“还在不在”，所以统一走这里现查；解析口径与拉取共用 {@link #parseSchedule}。</p>
     *
     * <p>返回的 {@code status}：0-正常 / 1-已取消 —— 调用方按“已取消 = 已删除”处理；
     * 日程已被彻底删除时接口不会返回该条，调用方按“查不到 = 已删除”处理。</p>
     */
    @Override
    public java.util.List<MtgPlatformItem> getByIds(MtgPullContext ctx, java.util.List<String> scheduleIds)
            throws Exception {
        OaonesApiLog.httpContext(OaonesConst.TYPE_WECHAT, ctx == null ? "" : ctx.getCorpId());
        try {
            java.util.List<MtgPlatformItem> all = new java.util.ArrayList<MtgPlatformItem>();
            if (ctx == null || scheduleIds == null || scheduleIds.isEmpty()) {
                return all;
            }
            JSONArray idList = new JSONArray();
            for (String one : scheduleIds) {
                if (!isBlank(one)) {
                    idList.add(one.trim());
                }
            }
            if (idList.isEmpty()) {
                return all;
            }
            if (isBlank(ctx.getCorpId()) || isBlank(ctx.getCorpSecret())) {
                throw new RuntimeException("企业微信租户缺少 corpid / Secret，无法查询日程详情");
            }
            if (client == null) {
                client = new WeComApi(ctx.getCorpId(), ctx.getCorpSecret());
            }
            String token = client.getAccessToken();
            String url = OaonesProps.wxApiBase()
                    + "/cgi-bin/oa/schedule/get?access_token=" + OaonesUtil.urlEncode(token);
            JSONObject body = new JSONObject();
            body.put("schedule_id_list", idList);
            JSONObject resp = OaonesHttp.postJson(url, body);
            if (resp.getIntValue("errcode") != 0) {
                throw new RuntimeException("企业微信日程详情接口返回错误：" + resp.toJSONString());
            }
            JSONArray list = resp.getJSONArray("schedule_list");
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    JSONObject one = list.getJSONObject(i);
                    MtgPlatformItem item = parseSchedule(one, OaonesUtil.s(one.getString("cal_id")));
                    if (item != null) {
                        all.add(item);
                    }
                }
            }
            return all;
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    /** 按单个日程ID取详情（取不到返回 null —— 说明该日程已不存在）。 */
    public MtgPlatformItem getById(MtgPullContext ctx, String scheduleId) throws Exception {
        if (isBlank(scheduleId)) {
            return null;
        }
        java.util.List<String> ids = new java.util.ArrayList<String>();
        ids.add(scheduleId.trim());
        java.util.List<MtgPlatformItem> list = getByIds(ctx, ids);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 企微日程 JSON → 统一载体（start_time / end_time 是秒级时间戳）。 */
    private MtgPlatformItem parseSchedule(JSONObject src, String calId) {
        String id = OaonesUtil.s(src.getString("schedule_id"));
        if (id.length() == 0) {
            return null;
        }
        MtgPlatformItem item = new MtgPlatformItem();
        item.setWxId(id);
        item.setTitle(src.getString("summary"));
        item.setContent(src.getString("description"));
        item.setBeginTime(OaonesUtil.formatDateTimeSeconds(src.getLongValue("start_time")));
        item.setEndTime(OaonesUtil.formatDateTimeSeconds(src.getLongValue("end_time")));
        item.setLocation(src.getString("location"));
        // 日程状态：0-正常 / 1-已取消（订阅回调与删除对账据此把“已取消”按删除处理）
        item.setStatus(Integer.valueOf(src.getIntValue("status")));
        JSONArray admins = src.getJSONArray("admins");
        if (admins != null && !admins.isEmpty()) {
            item.setOwnerWxId(OaonesUtil.s(admins.getString(0)));
        }
        if (isBlank(item.getOwnerWxId())) {
            item.setOwnerWxId(calId);
        }
        JSONArray attendees = src.getJSONArray("attendees");
        if (attendees != null) {
            for (int i = 0; i < attendees.size(); i++) {
                JSONObject one = attendees.getJSONObject(i);
                String uid = one == null ? "" : OaonesUtil.s(one.getString("userid"));
                if (uid.length() > 0) {
                    item.getAttendeeWxIds().add(uid);
                }
            }
        }
        return item;
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().length() == 0;
    }
}
