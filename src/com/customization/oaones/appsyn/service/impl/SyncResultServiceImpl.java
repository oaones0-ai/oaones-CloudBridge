package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.SyncResultMapper;
import com.customization.oaones.appsyn.entity.SyncResultItem;
import com.customization.oaones.appsyn.service.SyncResultService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同步结果独立页面实现（三个平台共用）。
 *
 * <p>数据源是同步中心逐条结果表 {@code uf_oaones_appsyn_sync_log_item}，一行就是一条部门、
 * 分部或人员同步动作；同时左关联批次表与租户配置表，带出 requestid、平台和租户名称。</p>
 */
public class SyncResultServiceImpl implements SyncResultService {

    private static final int MAX_ROWS = 500;

    @Override
    public JSONObject options(String operatorId) {
        boolean admin = OaonesAdmin.isTotalAdmin(operatorId);
        JSONArray tenants = new JSONArray();
        addTenants(tenants, OaonesConst.TYPE_WECHAT, admin, operatorId);
        addTenants(tenants, OaonesConst.TYPE_DINGTALK, admin, operatorId);
        addTenants(tenants, OaonesConst.TYPE_FEISHU, admin, operatorId);

        JSONArray platforms = new JSONArray();
        platforms.add(option(String.valueOf(OaonesConst.TYPE_WECHAT), "企业微信"));
        platforms.add(option(String.valueOf(OaonesConst.TYPE_DINGTALK), "钉钉"));
        platforms.add(option(String.valueOf(OaonesConst.TYPE_FEISHU), "飞书"));

        JSONObject data = new JSONObject();
        data.put("tenants", tenants);
        data.put("platforms", platforms);
        data.put("admin", Boolean.valueOf(admin));
        return data;
    }

    @Override
    public JSONObject query(JSONObject params, String operatorId) {
        final List<Integer> allowedCfgIds = allowedCfgIds(operatorId);
        Map<String, Object> query = OaonesParam.of();
        query.put("type", nullableInt(params.getString("type")));
        query.put("cfgId", nullableInt(params.getString("cfg_id")));
        query.put("requestId", nullableInt(params.getString("requestid")));
        query.put("logId", nullableInt(params.getString("log_id")));
        query.put("direction", nullableInt(params.getString("direction")));
        query.put("itemType", nullableInt(params.getString("item_type")));
        query.put("op", nullableInt(params.getString("op")));

        String keyword = OaonesUtil.s(params.getString("keyword")).trim();
        query.put("keyword", keyword.length() == 0 ? null : keyword);
        String[] from = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("from_time")), "00:00:00");
        String[] to = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("to_time")), "23:59:59");
        query.put("fromDate", from[0]);
        query.put("fromTime", from[1]);
        query.put("toDate", to[0]);
        query.put("toTime", to[1]);
        query.put("cfgIds", allowedCfgIds);

        int limit = OaonesUtil.i(OaonesUtil.s(params.getString("limit")), MAX_ROWS);
        if (limit <= 0 || limit > MAX_ROWS) {
            limit = MAX_ROWS;
        }
        query.put("limit", Integer.valueOf(limit));

        JSONObject data = new JSONObject();
        JSONArray rows = new JSONArray();
        try {
            List<SyncResultItem> list = OaonesMyBatis.call(SyncResultMapper.class,
                    mapper -> mapper.list(query));
            if (list != null) {
                for (SyncResultItem item : list) {
                    rows.add(toJson(item));
                }
            }
            Integer count = OaonesMyBatis.call(SyncResultMapper.class,
                    mapper -> mapper.count(query));
            Map<String, Object> stats = OaonesMyBatis.call(SyncResultMapper.class,
                    mapper -> mapper.stats(query));
            data.put("rows", rows);
            data.put("total", count == null ? Integer.valueOf(rows.size()) : count);
            data.put("stats", stats == null ? new JSONObject() : stats);
            data.put("limit", Integer.valueOf(limit));
        } catch (Throwable t) {
            OaonesLog.error("查询同步结果失败（请确认已执行 oaones0018_sync_result_requestid.sql）", t);
            String message = OaonesUtil.s(t.getMessage());
            data.put("rows", rows);
            data.put("total", Integer.valueOf(0));
            data.put("stats", new JSONObject());
            data.put("limit", Integer.valueOf(limit));
            data.put("missingMigration",
                    Boolean.valueOf(message.toLowerCase().contains("requestid")));
            throw new RuntimeException(data.getBooleanValue("missingMigration")
                    ? "同步结果表缺少 requestid 字段，请先执行 oaones0018_sync_result_requestid.sql"
                    : "查询同步结果失败：" + message, t);
        }
        return data;
    }

    private JSONObject toJson(SyncResultItem row) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("log_id", OaonesUtil.nullToEmpty(row.getLogId()));
        json.put("requestid", OaonesUtil.nullToEmpty(row.getRequestId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("platform_type", OaonesUtil.nullToEmpty(row.getPlatformType()));
        json.put("corp_name", OaonesUtil.nullToEmpty(row.getCorpName()));
        json.put("direction", OaonesUtil.nullToEmpty(row.getDirection()));
        json.put("item_type", OaonesUtil.nullToEmpty(row.getItemType()));
        json.put("op", OaonesUtil.nullToEmpty(row.getOp()));
        json.put("oa_id", OaonesUtil.nullToEmpty(row.getOaId()));
        json.put("oa_name", OaonesUtil.nullToEmpty(row.getOaName()));
        json.put("wx_id", OaonesUtil.nullToEmpty(row.getWxId()));
        json.put("wx_name", OaonesUtil.nullToEmpty(row.getWxName()));
        json.put("parent_id", OaonesUtil.nullToEmpty(row.getParentId()));
        json.put("parent_name", OaonesUtil.nullToEmpty(row.getParentName()));
        json.put("seq", OaonesUtil.nullToEmpty(row.getSeq()));
        json.put("message", OaonesUtil.nullToEmpty(row.getMessage()));
        json.put("start_time", OaonesUtil.nullToEmpty(row.getStartTime()));
        json.put("end_time", OaonesUtil.nullToEmpty(row.getEndTime()));
        json.put("create_date", OaonesUtil.nullToEmpty(row.getCreateDate()));
        json.put("create_time", OaonesUtil.nullToEmpty(row.getCreateTimeValue()));
        return json;
    }

    private JSONObject option(String value, String text) {
        JSONObject row = new JSONObject();
        row.put("value", value);
        row.put("text", text);
        return row;
    }

    private Integer nullableInt(String value) {
        String text = OaonesUtil.s(value).trim();
        if (text.length() == 0 || "all".equalsIgnoreCase(text)) {
            return null;
        }
        return OaonesUtil.integer(text);
    }

    private void addTenants(JSONArray target, int type, boolean admin, String operatorId) {
        JSONArray rows;
        try {
            if (type == OaonesConst.TYPE_DINGTALK) {
                com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl service =
                        new com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl();
                rows = admin ? service.list() : service.listMaintainable(operatorId);
            } else if (type == OaonesConst.TYPE_FEISHU) {
                com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl service =
                        new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl();
                rows = admin ? service.list() : service.listMaintainable(operatorId);
            } else {
                com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl service =
                        new com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl();
                rows = admin ? service.list() : service.listMaintainable(operatorId);
            }
        } catch (Throwable t) {
            rows = new JSONArray();
        }
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            JSONObject cfg = rows.getJSONObject(i);
            JSONObject row = new JSONObject();
            row.put("id", cfg.getString("id"));
            row.put("corp_name", cfg.getString("corp_name"));
            row.put("type", Integer.valueOf(type));
            target.add(row);
        }
    }

    /**
     * 管理员不限制租户；其他登录人只能看到本人维护租户的同步结果。
     */
    private List<Integer> allowedCfgIds(String operatorId) {
        if (OaonesAdmin.isTotalAdmin(operatorId)) {
            return null;
        }
        List<Integer> ids = new ArrayList<Integer>();
        collectMaintainable(ids, OaonesConst.TYPE_WECHAT, operatorId);
        collectMaintainable(ids, OaonesConst.TYPE_DINGTALK, operatorId);
        collectMaintainable(ids, OaonesConst.TYPE_FEISHU, operatorId);
        return ids;
    }

    private void collectMaintainable(List<Integer> ids, int type, String operatorId) {
        JSONArray rows;
        try {
            if (type == OaonesConst.TYPE_DINGTALK) {
                rows = new com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl()
                        .listMaintainable(operatorId);
            } else if (type == OaonesConst.TYPE_FEISHU) {
                rows = new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl()
                        .listMaintainable(operatorId);
            } else {
                rows = new com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl()
                        .listMaintainable(operatorId);
            }
        } catch (Throwable t) {
            rows = new JSONArray();
        }
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Integer id = OaonesUtil.integer(rows.getJSONObject(i).getString("id"));
            if (id != null && id.intValue() > 0 && !ids.contains(id)) {
                ids.add(id);
            }
        }
    }
}
