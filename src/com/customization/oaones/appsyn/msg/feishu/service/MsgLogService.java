package com.customization.oaones.appsyn.msg.feishu.service;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.msg.feishu.common.FeishuMsgConst;
import com.customization.oaones.appsyn.msg.feishu.common.FeishuMsgProps;
import com.customization.oaones.appsyn.msg.feishu.dao.MsgLogMapper;
import com.customization.oaones.appsyn.msg.feishu.entity.MsgLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送日志服务：全量落库、多维检索、失败重试扫描、按保留天数清理。
 */
public class MsgLogService {

    private static final String MIN_DATE = "1970-01-01";
    private static final String MIN_TIME = "00:00:00";
    private static final String MAX_DATE = "2999-12-31";
    private static final String MAX_TIME = "23:59:59";

    /** 接口日志中心表名：本表只存状态，报文 / 结果 / 耗时按 api_log_id 到它取。 */
    private static String apiTable() {
        return OaonesProps.apiLogTable();
    }

    /** 落一条日志（推送即落库；分片时一片一条）。 */
    public void save(MsgLog log) {
        if (log == null) {
            return;
        }
        try {
            insert(log);
            return;
        } catch (Throwable t) {
            // 落库失败绝不能影响“消息已经发出去”的事实与后续接收人：这里吞掉异常继续处理
            OaonesLog.error("[msg] 写推送日志失败（不影响本次推送结果，将按截断重写）：" + firstLine(t), t);
        }
        // 兜底一：表列长度不足（建模引擎/老脚本把 src_json 建成 nvarchar(n) 等）时，
        // 按档位截断重写。否则会出现“推送成功、推送日志页却没有记录”的账对不上问题
        int[] limits = {8000, 2000, 500};
        for (int i = 0; i < limits.length; i++) {
            trimForInsert(log, limits[i]);
            try {
                insert(log);
                OaonesLog.step("[msg] 推送日志已按 " + limits[i] + " 字符截断后写入（表列长度不足）；"
                        + "请确认 uf_oaones_appsyn_msg_log.src_json / uf_oaones_appsyn_api_log 报文列是 nvarchar(max)/longtext");
                return;
            } catch (Throwable t) {
                OaonesLog.step("[msg] 推送日志按 " + limits[i] + " 字符截断写入仍失败：" + firstLine(t));
            }
        }
        OaonesLog.error("[msg] 推送日志最终写入失败，本条推送未落库：请检查表 " + FeishuMsgProps.logTable()
                + " 的列类型（可执行 sql/alter_oaones_msg_log_widen.sql 修正）", null);
    }

    private void insert(MsgLog log) {
        OaonesMyBatis.run(MsgLogMapper.class, mapper -> mapper.insert(FeishuMsgProps.logTable(), log));
    }

    /**
     * 按各列的定义长度截断文本字段（只用于插入失败后的兜底重写）。
     *
     * <p>长文本列（报文/堆栈）按传入的档位 {@code limit} 截断，其余列按建表脚本里的长度上限截断，
     * 保证任何一列超长都不会让整条日志写不进去。</p>
     */
    private void trimForInsert(MsgLog log, int limit) {
        log.setSrcJson(limit(log.getSrcJson(), limit));
        log.setReason(limit(log.getReason(), Math.min(limit, 900)));
        log.setBizTitle(limit(log.getBizTitle(), Math.min(limit, 500)));
        log.setReceiverWx(limit(log.getReceiverWx(), Math.min(limit, 2000)));
        log.setReceiverOa(limit(log.getReceiverOa(), Math.min(limit, 500)));
        log.setDedupKey(limit(log.getDedupKey(), Math.min(limit, 250)));
        log.setTypeName(limit(log.getTypeName(), Math.min(limit, 200)));
        log.setBizId(limit(log.getBizId(), Math.min(limit, 128)));
        log.setTaskId(limit(log.getTaskId(), Math.min(limit, 128)));
        log.setCorpId(limit(log.getCorpId(), Math.min(limit, 128)));
        log.setAgentId(limit(log.getAgentId(), Math.min(limit, 64)));
        log.setBizType(limit(log.getBizType(), Math.min(limit, 32)));
        log.setWxMsgtype(limit(log.getWxMsgtype(), Math.min(limit, 32)));
        log.setCardAction(limit(log.getCardAction(), Math.min(limit, 16)));
    }

    /** 超长截断（null 安全）。 */
    public static String limit(String value, int max) {
        if (value == null || max <= 0 || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /** 异常信息第一行（避免把整个堆栈写进日志摘要）。 */
    private static String firstLine(Throwable t) {
        String message = t == null ? "" : String.valueOf(t.getMessage());
        if (message.length() == 0 && t != null && t.getCause() != null) {
            message = String.valueOf(t.getCause().getMessage());
        }
        int index = message.indexOf('\n');
        if (index > 0) {
            message = message.substring(0, index);
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    public MsgLog getById(long id) {
        // 联表接口日志中心，带出报文 / 结果 / 耗时（详情与重推用）
        return OaonesMyBatis.call(MsgLogMapper.class,
                mapper -> mapper.getById(FeishuMsgProps.logTable(), apiTable(), id));
    }

    /** 幂等校验：窗口内同一幂等键是否已推送（含待重试）。 */
    public boolean existsDedupKey(String dedupKey, String sinceTime) {
        if (dedupKey == null || dedupKey.length() == 0) {
            return false;
        }
        // 日志时间用建模标准字段（日期 + 时间两列），这里把“窗口起点”拆成日期与时间
        String[] parts = splitDateTime(sinceTime, MIN_TIME);
        Integer count = OaonesMyBatis.call(MsgLogMapper.class,
                mapper -> mapper.countDedup(FeishuMsgProps.logTable(), dedupKey, parts[0], parts[1]));
        return count != null && count > 0;
    }

    /**
     * 分页查询推送日志（可视化日志页面）。
     *
     * @param filters 支持 fromTime / toTime / bizId / stdType / status / keyword
     */
    public JSONObject page(Map<String, String> filters, int pageIndex, int pageSize) {
        // 页面按“起止时间”检索，入库是日期 + 时间两列，这里拆开传参
        String[] from = splitDateTime(value(filters, "fromTime"), MIN_TIME);
        String[] to = splitDateTime(value(filters, "toTime"), MAX_TIME);
        String bizId = value(filters, "bizId");
        String keyword = value(filters, "keyword");
        Integer stdType = intValue(filters, "stdType");
        Integer status = intValue(filters, "status");
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 1000);
        int index = pageIndex <= 0 ? 1 : pageIndex;
        int offset = (index - 1) * size;

        List<MsgLog> list = OaonesMyBatis.call(MsgLogMapper.class, mapper -> mapper.page(
                FeishuMsgProps.logTable(), apiTable(),
                from[0], from[1], to[0], to[1], bizId, stdType, status, keyword, offset, size));
        Integer total = OaonesMyBatis.call(MsgLogMapper.class, mapper -> mapper.count(
                FeishuMsgProps.logTable(), apiTable(),
                from[0], from[1], to[0], to[1], bizId, stdType, status, keyword));

        List<JSONObject> rows = new ArrayList<JSONObject>();
        if (list != null) {
            for (MsgLog item : list) {
                rows.add(toRow(item));
            }
        }
        JSONObject result = new JSONObject();
        result.put("list", rows);
        result.put("total", total == null ? 0 : total);
        result.put("pageIndex", index);
        result.put("pageSize", size);
        return result;
    }

    /** 列表行（不返回大字段报文，详情单独取，避免列表接口过重）。 */
    private JSONObject toRow(MsgLog item) {
        JSONObject row = new JSONObject();
        row.put("id", item.getId());
        row.put("bizType", item.getBizType());
        row.put("bizId", item.getBizId());
        row.put("bizTitle", item.getBizTitle());
        row.put("stdType", item.getStdType());
        row.put("typeName", item.getTypeName());
        row.put("wxMsgtype", item.getWxMsgtype());
        row.put("cardAction", item.getCardAction());
        row.put("appId", item.getAppId());
        row.put("agentId", item.getAgentId());
        row.put("taskId", item.getTaskId());
        row.put("receiverOa", item.getReceiverOa());
        row.put("receiverWx", item.getReceiverWx());
        row.put("receiverCount", item.getReceiverCount());
        row.put("shardIndex", item.getShardIndex());
        row.put("shardTotal", item.getShardTotal());
        row.put("status", item.getStatus());
        row.put("statusText", FeishuMsgConst.logStatusText(item.getStatus() == null ? 0 : item.getStatus()));
        row.put("retryCount", item.getRetryCount());
        row.put("nextRetryTime", item.getNextRetryTime());
        // 平台返回码 / 信息 / 耗时来自接口日志中心（本表不再存）；
        // 平台返回信息与业务原因（跳过 / 去重原因、错误码运维提示）都有时一起显示
        String apiMsg = item.getApiErrMsg() == null ? "" : item.getApiErrMsg();
        String reason = item.getReason() == null ? "" : item.getReason();
        String resultMsg = apiMsg;
        if (reason.length() > 0) {
            resultMsg = apiMsg.length() == 0 ? reason : (apiMsg + " [" + reason + "]");
        }
        row.put("errcode", item.getApiErrCode());
        row.put("errmsg", resultMsg);
        row.put("costMs", item.getApiCostMs());
        row.put("apiLogId", item.getApiLogId());
        // 跳转地址：从接口日志中心里这条推送的请求报文解析，日志页直接展示成超链接
        row.put("jumpUrl", jumpUrl(item.getApiReqBody()));
        // 时间统一取建模系统标准字段：写入日期/时间 + 最近更新时间
        row.put("createdDate", item.getModedatacreatedate());
        row.put("createdTime", item.getModedatacreatetime());
        row.put("modifyDatetime", item.getModedatamodifydatetime());
        return row;
    }

    /**
     * 从飞书请求报文里取出跳转地址（推送日志列表 / 详情展示用）。
     *
     * <p>日志页要能直接看到“这条消息点开是哪个页面”，所以这里把实际发出去的报文里
     * 的链接解析出来（文本卡片的 {@code textcard.url}、模板卡片的
     * {@code template_card.card_action.url}、图文的 {@code news.articles[0].url}、
     * 任务卡片 / 交互式任务卡片的 {@code url}）；文本消息没有链接字段，
     * 取正文里拼进去的网址（{@code MsgRenderService#textContentWithUrl}）。
     * 报文里确实没有链接时返回空串。</p>
     */
    public static String jumpUrl(String requestJson) {
        String json = requestJson == null ? "" : requestJson.trim();
        if (json.length() == 0) {
            return "";
        }
        JSONObject body;
        try {
            body = JSONObject.parseObject(json);
        } catch (Exception e) {
            return "";
        }
        if (body == null) {
            return "";
        }
        String msgtype = body.getString("msgtype") == null ? "" : body.getString("msgtype").trim();
        if ("textcard".equals(msgtype)) {
            JSONObject card = body.getJSONObject("textcard");
            return card == null ? "" : trim(card.getString("url"));
        }
        if ("template_card".equals(msgtype)) {
            JSONObject card = body.getJSONObject("template_card");
            JSONObject action = card == null ? null : card.getJSONObject("card_action");
            return action == null ? "" : trim(action.getString("url"));
        }
        if ("news".equals(msgtype)) {
            JSONObject news = body.getJSONObject("news");
            com.alibaba.fastjson.JSONArray articles = news == null ? null : news.getJSONArray("articles");
            if (articles == null || articles.isEmpty()) {
                return "";
            }
            JSONObject first = articles.getJSONObject(0);
            return first == null ? "" : trim(first.getString("url"));
        }
        if ("taskcard".equals(msgtype) || "interactive_taskcard".equals(msgtype)) {
            JSONObject card = body.getJSONObject("taskcard");
            if (card == null) {
                card = body.getJSONObject("interactive_taskcard");
            }
            return card == null ? "" : trim(card.getString("url"));
        }
        // 文本消息：正文里的第一个 http/https 网址（推送时把业务链接拼在正文末尾）
        JSONObject text = body.getJSONObject("text");
        return text == null ? "" : firstUrl(trim(text.getString("content")));
    }

    /** 取文本里第一个 http/https 网址。 */
    private static String firstUrl(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        int http = text.indexOf("http://");
        int https = text.indexOf("https://");
        int begin = http < 0 ? https : (https < 0 ? http : Math.min(http, https));
        if (begin < 0) {
            return "";
        }
        int end = begin;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(begin, end);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 到期需重试的日志（可重试错误 + 未达重试上限）。 */
    public List<MsgLog> listRetryable(String now, int maxRetry, int limit) {
        List<MsgLog> list = OaonesMyBatis.call(MsgLogMapper.class,
                mapper -> mapper.listRetryable(FeishuMsgProps.logTable(), now, maxRetry, limit));
        return list == null ? new ArrayList<MsgLog>() : list;
    }

    /** 重试后回写结果。 */
    public void updateAfterRetry(MsgLog log) {
        if (log == null || log.getId() == null) {
            return;
        }
        try {
            OaonesMyBatis.run(MsgLogMapper.class, mapper -> mapper.updateResult(FeishuMsgProps.logTable(), log));
            return;
        } catch (Throwable t) {
            OaonesLog.error("[msg] 重试结果回写失败（不影响本次重试结果），将按截断重写：" + firstLine(t), t);
        }
        // 与新增日志同样的兜底：列长度不足时截断后重写，避免重试结果丢了
        int[] limits = {8000, 2000, 500};
        for (int i = 0; i < limits.length; i++) {
            trimForInsert(log, limits[i]);
            try {
                OaonesMyBatis.run(MsgLogMapper.class, mapper -> mapper.updateResult(FeishuMsgProps.logTable(), log));
                OaonesLog.step("[msg] 重试结果已按 " + limits[i] + " 字符截断后回写；"
                        + "请确认 uf_oaones_appsyn_msg_log.src_json 是 nvarchar(max)/longtext");
                return;
            } catch (Throwable t) {
                OaonesLog.step("[msg] 重试结果按 " + limits[i] + " 字符截断回写仍失败：" + firstLine(t));
            }
        }
        OaonesLog.error("[msg] 重试结果最终回写失败，日志 id=" + log.getId(), null);
    }

    /** 按保留天数清理历史日志，返回清理条数。 */
    public int cleanExpired(int keepDays) {
        if (keepDays <= 0) {
            return 0;
        }
        String[] before = splitDateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(System.currentTimeMillis() - keepDays * 24L * 3600 * 1000)), MIN_TIME);
        int rows = OaonesMyBatis.call(MsgLogMapper.class, mapper -> {
            return mapper.deleteBefore(FeishuMsgProps.logTable(), before[0], before[1]);
        });
        OaonesLog.step("[msg] 清理推送日志 before=" + before[0] + " " + before[1] + " rows=" + rows);
        return rows;
    }

    /** 看板统计（今日 / 昨日 / 累计）。 */
    public JSONObject board() {
        java.text.SimpleDateFormat dayFmt = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String today = dayFmt.format(new java.util.Date());
        String yesterday = dayFmt.format(new java.util.Date(System.currentTimeMillis() - 24L * 3600 * 1000));
        JSONObject board = new JSONObject();
        board.put("today", sum(today, MIN_TIME, today, MAX_TIME));
        board.put("yesterday", sum(yesterday, MIN_TIME, yesterday, MAX_TIME));
        board.put("total", sum(MIN_DATE, MIN_TIME, MAX_DATE, MAX_TIME));
        board.put("types", groupByType(today, MIN_TIME, today, MAX_TIME));
        board.put("failTop", failTop(today, MIN_TIME, today, MAX_TIME, 10));
        return board;
    }

    /** 区间统计（含成功率）。 */
    public JSONObject sum(String fromDate, String fromTime, String toDate, String toTime) {
        Map<String, Object> raw = OaonesMyBatis.call(MsgLogMapper.class,
                mapper -> mapper.sumStats(FeishuMsgProps.logTable(), apiTable(), fromDate, fromTime, toDate, toTime));
        JSONObject item = new JSONObject();
        item.put("total", num(raw, "totalCount"));
        item.put("success", num(raw, "successCount"));
        item.put("fail", num(raw, "failCount"));
        item.put("dedup", num(raw, "dedupCount"));
        item.put("skip", num(raw, "skipCount"));
        long total = item.getLongValue("total");
        long success = item.getLongValue("success");
        item.put("rate", total <= 0 ? "-" : String.format("%.2f%%", success * 100.0 / total));
        item.put("avgCost", raw == null || raw.get("avgCost") == null
                ? 0 : Math.round(Double.parseDouble(String.valueOf(raw.get("avgCost")))));
        return item;
    }

    public List<JSONObject> groupByType(String fromDate, String fromTime, String toDate, String toTime) {
        List<Map<String, Object>> raw = OaonesMyBatis.call(MsgLogMapper.class,
                mapper -> mapper.groupByType(FeishuMsgProps.logTable(), fromDate, fromTime, toDate, toTime));
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (raw != null) {
            for (Map<String, Object> item : raw) {
                JSONObject row = new JSONObject();
                row.put("typeName", str(item, "typeName"));
                row.put("wxMsgtype", str(item, "wxMsgtype"));
                row.put("total", num(item, "totalCount"));
                row.put("success", num(item, "successCount"));
                row.put("fail", num(item, "failCount"));
                list.add(row);
            }
        }
        return list;
    }

    public List<JSONObject> failTop(String fromDate, String fromTime, String toDate, String toTime, int limit) {
        List<Map<String, Object>> raw = OaonesMyBatis.call(MsgLogMapper.class,
                mapper -> mapper.failTop(FeishuMsgProps.logTable(), apiTable(), fromDate, fromTime, toDate, toTime, limit));
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (raw != null) {
            for (Map<String, Object> item : raw) {
                JSONObject row = new JSONObject();
                row.put("errmsg", str(item, "errmsg"));
                row.put("total", num(item, "totalCount"));
                list.add(row);
            }
        }
        return list;
    }

    private String value(Map<String, String> filters, String key) {
        if (filters == null) {
            return null;
        }
        String value = filters.get(key);
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }

    /**
     * 把 {@code yyyy-MM-dd HH:mm:ss} 拆成 [日期, 时间] 两段（对应建模标准字段
     * {@code modedatacreatedate} / {@code modedatacreatetime}）。
     *
     * <p>只填日期（yyyy-MM-dd）时，时间按默认值补齐：起点默认 00:00:00，终点默认 23:59:59；
     * 空值返回 [null, null]，表示该侧不限制。</p>
     *
     * <p>实现已收敛到公共的 {@link com.customization.oaones.appsyn.common.OaonesUtil#splitDateTime}
     * （会议 / 日程同步的“同步记录”时间检索用同一份口径），这里保留方法只为兼容既有调用方。</p>
     */
    public static String[] splitDateTime(String value, String defaultTime) {
        return com.customization.oaones.appsyn.common.OaonesUtil.splitDateTime(value, defaultTime);
    }

    private Integer intValue(Map<String, String> filters, String key) {
        String value = value(filters, key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private long num(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object value = map.get(key);
        if (value == null) {
            value = map.get(key.toUpperCase());
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.valueOf(String.valueOf(value)).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object value = map.get(key);
        if (value == null) {
            value = map.get(key.toUpperCase());
        }
        return value == null ? "" : String.valueOf(value);
    }
}
