package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.LogRetentionCatalog;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.ApiLogMapper;
import com.customization.oaones.appsyn.entity.ApiLog;
import com.customization.oaones.appsyn.service.ApiLogService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 接口日志中心服务实现（DAO 层为 {@link ApiLogMapper}）。
 *
 * <p>分权口径与同步日志一致：系统管理员（user.id=1）或
 * {@code oaones.properties#apilog.admin.ids} 中列出的人员可查看全部日志；
 * 其他登录人只看得到<b>本人可维护租户</b>的日志（个人微信 {@code cfg_id=0} 只对管理员可见）。</p>
 */
public class ApiLogServiceImpl implements ApiLogService {

    private String table() {
        return OaonesProps.apiLogTable();
    }

    @Override
    public JSONObject query(JSONObject params, String operatorId) {
        List<Integer> allowed = allowedCfgIds(operatorId);
        Integer type = nullableInt(params.getString("type"));
        Integer cfgId = nullableInt(params.getString("cfg_id"));
        String apiType = OaonesUtil.s(params.getString("api_type")).trim();
        Integer direction = nullableInt(params.getString("direction"));
        Integer success = nullableInt(params.getString("success"));
        String keyword = OaonesUtil.s(params.getString("keyword")).trim();
        String[] from = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("from_time")), "00:00:00");
        String[] to = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("to_time")), "23:59:59");
        int page = Math.max(OaonesUtil.i(OaonesUtil.s(params.getString("page")), 1), 1);
        int size = OaonesUtil.i(OaonesUtil.s(params.getString("size")), 50);
        size = size < 1 ? 50 : (size > 500 ? 500 : size);
        int offset = (page - 1) * size;

        final Integer fType = type;
        final Integer fCfg = cfgId;
        final String fApiType = apiType.length() == 0 ? null : apiType;
        final Integer fDir = direction;
        final Integer fSuccess = success;
        final String fKeyword = keyword.length() == 0 ? null : keyword;
        final String fFromDate = from[0];
        final String fFromTime = from[1];
        final String fToDate = to[0];
        final String fToTime = to[1];
        final List<Integer> fCfgIds = allowed;
        final int fOffset = offset;
        final int fSize = size;

        JSONObject data = new JSONObject();
        JSONArray rows = new JSONArray();
        int total = 0;
        try {
            List<ApiLog> list = OaonesMyBatis.call(ApiLogMapper.class, mapper -> mapper.page(
                    table(), fType, fCfg, fApiType, fDir, fSuccess, fKeyword,
                    fFromDate, fFromTime, fToDate, fToTime, fCfgIds, fOffset, fSize));
            if (list != null) {
                for (ApiLog row : list) {
                    rows.add(rowJson(row, false));
                }
            }
            Integer count = OaonesMyBatis.call(ApiLogMapper.class, mapper -> mapper.count(
                    table(), fType, fCfg, fApiType, fDir, fSuccess, fKeyword,
                    fFromDate, fFromTime, fToDate, fToTime, fCfgIds));
            total = count == null ? 0 : count.intValue();
        } catch (Throwable t) {
            // 表还没建：给出可读提示，而不是 500
            OaonesLog.error("查询接口日志失败（请确认已执行 uf_oaones_api_log 建表脚本）", t);
            data.put("rows", rows);
            data.put("total", Integer.valueOf(0));
            data.put("page", Integer.valueOf(page));
            data.put("size", Integer.valueOf(size));
            data.put("stats", new JSONObject());
            data.put("missing", Boolean.TRUE);
            return data;
        }
        data.put("rows", rows);
        data.put("total", Integer.valueOf(total));
        data.put("page", Integer.valueOf(page));
        data.put("size", Integer.valueOf(size));
        data.put("stats", stats(fType, fCfg, fApiType, fDir, fKeyword, fFromDate, fFromTime,
                fToDate, fToTime, fCfgIds));
        return data;
    }

    @Override
    public JSONObject detail(String id, String operatorId) {
        long logId = OaonesUtil.i(OaonesUtil.s(id), 0);
        if (logId <= 0) {
            throw new RuntimeException("缺少日志ID，无法查看详情");
        }
        ApiLog row = OaonesMyBatis.call(ApiLogMapper.class, mapper -> mapper.getById(table(), logId));
        if (row == null) {
            throw new RuntimeException("该接口日志不存在（id=" + id + "），可能已被清理");
        }
        // 分权：非管理员只能看本人可维护租户的日志（个人微信 cfg_id=0 只对管理员可见）
        List<Integer> allowed = allowedCfgIds(operatorId);
        if (allowed != null) {
            Integer cfgId = row.getCfgId();
            if (cfgId == null || cfgId.intValue() <= 0 || !allowed.contains(cfgId)) {
                throw new RuntimeException("您没有权限查看该接口日志");
            }
        }
        return rowJson(row, true);
    }

    @Override
    public JSONObject options(String operatorId) {
        boolean admin = isApiLogAdmin(operatorId);
        JSONArray tenants = new JSONArray();
        addTenants(tenants, OaonesConst.TYPE_WECHAT, admin, operatorId);
        addTenants(tenants, OaonesConst.TYPE_DINGTALK, admin, operatorId);
        addTenants(tenants, OaonesConst.TYPE_FEISHU, admin, operatorId);

        JSONArray platforms = new JSONArray();
        platforms.add(platformOption(OaonesConst.TYPE_WECHAT));
        platforms.add(platformOption(OaonesConst.TYPE_DINGTALK));
        platforms.add(platformOption(OaonesConst.TYPE_FEISHU));
        platforms.add(platformOption(OaonesApiLog.PLATFORM_PERSONAL_WX));

        JSONArray types = new JSONArray();
        types.add(typeOption(OaonesApiLog.TYPE_ORG));
        types.add(typeOption(OaonesApiLog.TYPE_MSG));
        types.add(typeOption(OaonesApiLog.TYPE_MTG));
        // 考勤同步（一级菜单「考勤同步」的打卡读取 / 反向写入，需求 3.8.3）
        types.add(typeOption(OaonesApiLog.TYPE_ATT));
        types.add(typeOption(OaonesApiLog.TYPE_LOGIN));
        types.add(typeOption(OaonesApiLog.TYPE_OTHER));

        JSONObject data = new JSONObject();
        data.put("tenants", tenants);
        data.put("platforms", platforms);
        data.put("types", types);
        data.put("keepDays", Integer.valueOf(new LogRetentionServiceImpl().keepDays(LogRetentionCatalog.API_LOG)));
        data.put("enabled", Boolean.valueOf(OaonesProps.apiLogEnabled()));
        data.put("admin", Boolean.valueOf(admin));
        return data;
    }

    @Override
    public void cleanIfDue() {
        // 自动清理由 OaonesLogRetentionScheduler 每天统一执行；这里仅确保调度器已启动。
        com.customization.oaones.appsyn.job.OaonesLogRetentionScheduler.ensureStarted();
    }

    @Override
    public JSONObject clean(String operatorId) {
        if (!isApiLogAdmin(operatorId)) {
            throw new RuntimeException("只有系统管理员或被授权的接口日志管理员可以清理接口日志");
        }
        int keepDays = new LogRetentionServiceImpl().keepDays(LogRetentionCatalog.API_LOG);
        JSONObject data = new JSONObject();
        if (keepDays <= 0) {
            data.put("deleted", Integer.valueOf(0));
            data.put("message", "当前为永久保留（系统设置 → 日志保留），未清理");
            return data;
        }
        return new LogRetentionServiceImpl().cleanNow(LogRetentionCatalog.API_LOG);
    }

    /* ------------------------------------------------------------------
     * 内部
     * ------------------------------------------------------------------ */

    private JSONObject stats(Integer type, Integer cfgId, String apiType, Integer direction,
                             String keyword, String fromDate, String fromTime,
                             String toDate, String toTime, List<Integer> cfgIds) {
        JSONObject stats = new JSONObject();
        try {
            Map<String, Object> sum = OaonesMyBatis.call(ApiLogMapper.class, mapper -> mapper.sumStats(
                    table(), type, cfgId, apiType, direction, keyword, fromDate, fromTime,
                    toDate, toTime, cfgIds));
            JSONObject sumJson = new JSONObject();
            if (sum != null) {
                sumJson.put("total", sum.get("totalCount"));
                sumJson.put("success", sum.get("successCount"));
                sumJson.put("fail", sum.get("failCount"));
                sumJson.put("avgCost", sum.get("avgCost"));
            }
            stats.put("sum", sumJson);
            stats.put("platforms", groupJson(OaonesMyBatis.call(ApiLogMapper.class,
                    mapper -> mapper.groupByPlatform(table(), cfgIds, fromDate, fromTime, toDate, toTime)), "typeCode"));
            stats.put("types", groupJson(OaonesMyBatis.call(ApiLogMapper.class,
                    mapper -> mapper.groupByApiType(table(), cfgIds, fromDate, fromTime, toDate, toTime)), "apiType"));
        } catch (Throwable ignore) {
            stats.put("sum", new JSONObject());
            stats.put("platforms", new JSONArray());
            stats.put("types", new JSONArray());
        }
        return stats;
    }

    private JSONArray groupJson(List<Map<String, Object>> list, String keyName) {
        JSONArray array = new JSONArray();
        if (list == null) {
            return array;
        }
        for (Map<String, Object> row : list) {
            JSONObject json = new JSONObject();
            Object key = row.get(keyName);
            if ("typeCode".equals(keyName)) {
                int code = OaonesUtil.i(key == null ? "" : String.valueOf(key), 0);
                json.put("key", Integer.valueOf(code));
                json.put("keyText", OaonesApiLog.platformText(code));
            } else {
                String code = key == null ? "" : String.valueOf(key);
                json.put("key", code);
                json.put("keyText", OaonesApiLog.apiTypeText(code));
            }
            json.put("total", row.get("totalCount"));
            json.put("success", row.get("successCount"));
            json.put("fail", row.get("failCount"));
            array.add(json);
        }
        return array;
    }

    private JSONObject rowJson(ApiLog row, boolean withBody) {
        int type = row.getType() == null ? 0 : row.getType().intValue();
        int direction = row.getDirection() == null ? 0 : row.getDirection().intValue();
        JSONObject json = new JSONObject();
        json.put("id", row.getId());
        json.put("type", Integer.valueOf(type));
        json.put("platform", OaonesApiLog.platformText(type));
        json.put("cfg_id", row.getCfgId());
        json.put("corp_name", OaonesUtil.s(row.getCorpName()));
        json.put("api_type", OaonesUtil.s(row.getApiType()));
        json.put("api_type_text", OaonesApiLog.apiTypeText(row.getApiType()));
        json.put("direction", Integer.valueOf(direction));
        json.put("direction_text", direction == 1 ? "第三方调用" : "发往第三方");
        json.put("method", OaonesUtil.s(row.getMethod()));
        json.put("api_path", OaonesUtil.s(row.getApiPath()));
        json.put("api_url", OaonesUtil.s(row.getApiUrl()));
        json.put("http_status", row.getHttpStatus());
        json.put("success", row.getSuccess());
        json.put("err_code", OaonesUtil.s(row.getErrCode()));
        json.put("err_msg", OaonesUtil.s(row.getErrMsg()));
        json.put("cost_ms", row.getCostMs());
        json.put("create_date", OaonesUtil.s(row.getModedatacreatedate()));
        json.put("create_time", OaonesUtil.s(row.getModedatacreatetime()));
        json.put("create_at", OaonesUtil.s(row.getModedatacreatedate()) + " " + OaonesUtil.s(row.getModedatacreatetime()));
        if (withBody) {
            json.put("req_body", OaonesUtil.s(row.getReqBody()));
            json.put("resp_body", OaonesUtil.s(row.getRespBody()));
            json.put("error_stack", OaonesUtil.s(row.getErrorStack()));
            json.put("operator_id", row.getOperatorId());
        }
        return json;
    }

    private void addTenants(JSONArray target, int type, boolean admin, String operatorId) {
        JSONArray rows;
        try {
            if (type == OaonesConst.TYPE_DINGTALK) {
                com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl svc =
                        new com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl();
                rows = admin ? svc.list() : svc.listMaintainable(operatorId);
            } else if (type == OaonesConst.TYPE_FEISHU) {
                com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl svc =
                        new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl();
                rows = admin ? svc.list() : svc.listMaintainable(operatorId);
            } else {
                com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl svc =
                        new com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl();
                rows = admin ? svc.list() : svc.listMaintainable(operatorId);
            }
        } catch (Throwable t) {
            rows = new JSONArray();
        }
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            JSONObject cfg = rows.getJSONObject(i);
            JSONObject json = new JSONObject();
            json.put("id", cfg.getString("id"));
            json.put("corp_name", cfg.getString("corp_name"));
            json.put("type", Integer.valueOf(type));
            json.put("platform", OaonesApiLog.platformText(type));
            target.add(json);
        }
    }

    /**
     * 非管理员返回“本人可维护租户”ID 列表（跨三个平台）；管理员返回 null 表示不按租户过滤。
     */
    private List<Integer> allowedCfgIds(String operatorId) {
        if (isApiLogAdmin(operatorId)) {
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

    /** 是否为接口日志管理员（系统管理员 / 超管角色 / apilog.admin.ids）。 */
    private boolean isApiLogAdmin(String operatorId) {
        int uid = OaonesUtil.i(operatorId, 0);
        if (OaonesAdmin.isTotalAdmin(operatorId)) {
            return true;
        }
        String ids = OaonesProps.apiLogAdminIds();
        if (ids != null && ids.trim().length() > 0) {
            for (String item : ids.split(",")) {
                if (uid > 0 && item.trim().equals(String.valueOf(uid))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Integer nullableInt(String value) {
        String s = OaonesUtil.s(value).trim();
        if (s.length() == 0) {
            return null;
        }
        Integer i = OaonesUtil.integer(s);
        return i;
    }

    private JSONObject platformOption(int type) {
        JSONObject json = new JSONObject();
        json.put("value", Integer.valueOf(type));
        json.put("text", OaonesApiLog.platformText(type));
        return json;
    }

    private JSONObject typeOption(String code) {
        JSONObject json = new JSONObject();
        json.put("value", code);
        json.put("text", OaonesApiLog.apiTypeText(code));
        return json;
    }
}
