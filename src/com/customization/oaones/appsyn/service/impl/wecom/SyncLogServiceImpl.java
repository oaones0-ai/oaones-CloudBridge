package com.customization.oaones.appsyn.service.impl.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.wecom.SyncLogMapper;
import com.customization.oaones.appsyn.dao.wecom.SyncLogItemMapper;
import com.customization.oaones.appsyn.entity.wecom.SyncLogItem;
import com.customization.oaones.appsyn.entity.wecom.SyncLog;
import com.customization.oaones.appsyn.service.wecom.SyncLogService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 同步日志服务实现（DAO 层为 {@link SyncLogMapper}）。
 */
public class SyncLogServiceImpl implements SyncLogService {

    private String table() {
        return OaonesProps.syncLogTable();
    }

    private String itemTable() {
        return OaonesProps.syncLogItemTable();
    }

    @Override
    public JSONArray list(String cfgId, String direction, int limit) {
        return list(cfgId, direction, limit, null);
    }

    @Override
    public JSONArray list(String cfgId, String direction, int limit, List<Integer> allowedCfgIds) {
        OaonesLog.step("查询同步日志 cfgId=" + cfgId + " direction=" + direction
                + " limit=" + limit + " 可见租户=" + (allowedCfgIds == null ? "全部" : allowedCfgIds.size()));
        JSONArray result = new JSONArray();
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        String dir = direction == null ? "" : direction.trim();
        params.put("direction", dir.length() == 0 || "all".equalsIgnoreCase(dir)
                ? null : OaonesUtil.integer(dir));
        params.put("limit", limit > 0 ? limit : 200);
        params.put("cfgIds", allowedCfgIds);
        List<SyncLog> rows = OaonesMyBatis.call(SyncLogMapper.class, mapper -> mapper.list(params));
        if (rows == null) {
            return result;
        }
        for (SyncLog row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("requestid", OaonesUtil.nullToEmpty(row.getRequestid()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgid()));
            json.put("corp_name", OaonesUtil.nullToEmpty(row.getCorpname()));
            json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
            json.put("direction", OaonesUtil.nullToEmpty(row.getDirection()));
            json.put("total_count", OaonesUtil.nullToEmpty(row.getTotalcount()));
            json.put("success_count", OaonesUtil.nullToEmpty(row.getSuccesscount()));
            json.put("fail_count", OaonesUtil.nullToEmpty(row.getFailcount()));
            json.put("skip_count", OaonesUtil.nullToEmpty(row.getSkipcount()));
            // 同步结果统计：本次同步 x 个部门 x 个员工，其中 x 个员工没有找到部门、挂在哪里
            json.put("dept_count", OaonesUtil.nullToEmpty(row.getDeptcount()));
            json.put("user_count", OaonesUtil.nullToEmpty(row.getUsercount()));
            json.put("nodept_count", OaonesUtil.nullToEmpty(row.getNodeptcount()));
            json.put("nodept_dept", OaonesUtil.nullToEmpty(row.getNodeptdept()));
            json.put("start_time", OaonesUtil.nullToEmpty(row.getStarttime()));
            json.put("end_time", OaonesUtil.nullToEmpty(row.getEndtime()));
            json.put("run_status", OaonesUtil.nullToEmpty(row.getRunstatus()));
            json.put("detail", OaonesUtil.nullToEmpty(row.getDetail()));
            json.put("create_by", OaonesUtil.nullToEmpty(row.getModedatacreater()));
            json.put("create_time", "");
            result.add(json);
        }
        return result;
    }

    @Override
    public int add(JSONObject data) {
        OaonesLog.step("写入同步日志 cfgId=" + OaonesUtil.s(data.getString("cfg_id"))
                + " direction=" + OaonesUtil.s(data.getString("direction"))
                + " runStatus=" + OaonesUtil.s(data.getString("run_status")));
        String detail = OaonesUtil.s(data.getString("detail"));
        if (detail.length() > 3900) {
            detail = detail.substring(0, 3900);
        }
        int modeId = OaonesProps.syncLogModeId();
        final String uuid = UUID.randomUUID().toString();
        int operator = OaonesUtil.i(OaonesUtil.s(data.getString("create_by")), 0);

        SyncLog entity = new SyncLog();
        entity.setRequestid(OaonesUtil.integer(OaonesUtil.s(data.getString("requestid"))));
        entity.setCfgid(OaonesUtil.integer(OaonesUtil.s(data.getString("cfg_id"))));
        entity.setCorpname(OaonesUtil.s(data.getString("corp_name")));
        entity.setDirection(OaonesUtil.integer(OaonesUtil.s(data.getString("direction"))));
        entity.setTotalcount(OaonesUtil.integer(OaonesUtil.s(data.getString("total_count"))));
        entity.setSuccesscount(OaonesUtil.integer(OaonesUtil.s(data.getString("success_count"))));
        entity.setFailcount(OaonesUtil.integer(OaonesUtil.s(data.getString("fail_count"))));
        entity.setSkipcount(OaonesUtil.integer(OaonesUtil.s(data.getString("skip_count"))));
        entity.setDeptcount(OaonesUtil.integer(OaonesUtil.s(data.getString("dept_count"))));
        entity.setUsercount(OaonesUtil.integer(OaonesUtil.s(data.getString("user_count"))));
        entity.setNodeptcount(OaonesUtil.integer(OaonesUtil.s(data.getString("nodept_count"))));
        entity.setNodeptdept(OaonesUtil.s(data.getString("nodept_dept")));
        entity.setStarttime(OaonesUtil.s(data.getString("start_time")));
        entity.setEndtime(OaonesUtil.s(data.getString("end_time")));
        entity.setRunstatus(OaonesUtil.integer(OaonesUtil.s(data.getString("run_status"))));
        entity.setDetail(detail);
        entity.setFormmodeid(modeId);
        entity.setModeuuid(uuid);
        entity.setModedatacreater(operator);
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(operator);
        entity.setModedatamodifydatetime(OaonesUtil.now());
        OaonesMyBatis.call(SyncLogMapper.class, mapper -> {
            mapper.insert(table(), entity);
            return 1;
        });
        Integer newId = OaonesMyBatis.call(SyncLogMapper.class,
                mapper -> mapper.findIdByUuid(table(), uuid));
        if (newId != null) {
            OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId),
                    OaonesUtil.s(data.getString("create_by")));
            return newId.intValue();
        }
        return 0;
    }

    @Override
    public int addItems(int logId, JSONArray items, String operatorId) {
        if (logId <= 0 || items == null || items.isEmpty()) {
            return 0;
        }
        OaonesLog.step("写入同步明细 logId=" + logId + " 条数=" + items.size());
        int modeId = OaonesProps.syncLogItemModeId();
        int operator = OaonesUtil.i(OaonesUtil.s(operatorId), 0);
        String now = OaonesUtil.now();
        String nowDate = OaonesUtil.nowDate();
        String nowTime = OaonesUtil.nowTime();
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            JSONObject row = items.getJSONObject(i);
            if (row == null) {
                continue;
            }
            SyncLogItem entity = new SyncLogItem();
            entity.setLogId(logId);
            entity.setRequestid(OaonesUtil.integer(OaonesUtil.s(row.getString("requestid"))));
            entity.setCfgid(OaonesUtil.integer(OaonesUtil.s(row.getString("cfg_id"))));
            entity.setDirection(OaonesUtil.integer(OaonesUtil.s(row.getString("direction"))));
            entity.setItemType(OaonesUtil.integer(OaonesUtil.s(row.getString("item_type"))));
            entity.setOp(OaonesUtil.integer(OaonesUtil.s(row.getString("op"))));
            entity.setOaId(OaonesUtil.s(row.getString("oa_id")));
            entity.setOaName(cut(OaonesUtil.s(row.getString("oa_name")), 255));
            entity.setWxId(cut(OaonesUtil.s(row.getString("wx_id")), 128));
            entity.setWxName(cut(OaonesUtil.s(row.getString("wx_name")), 255));
            entity.setParentId(cut(OaonesUtil.s(row.getString("parent_id")), 128));
            entity.setParentName(cut(OaonesUtil.s(row.getString("parent_name")), 255));
            entity.setSeq(OaonesUtil.integer(OaonesUtil.s(row.getString("seq"))));
            entity.setMessage(cut(OaonesUtil.s(row.getString("message")), 1000));
            entity.setFormmodeid(modeId);
            entity.setModedatacreater(operator);
            entity.setModedatacreatedate(nowDate);
            entity.setModedatacreatetime(nowTime);
            entity.setModedatamodifier(operator);
            entity.setModedatamodifydatetime(now);
            OaonesMyBatis.run(SyncLogItemMapper.class,
                    mapper -> mapper.insert(itemTable(), entity));
            count++;
        }
        return count;
    }

    @Override
    public JSONArray listItems(String logId, String itemType, int limit) {
        JSONArray result = new JSONArray();
        Integer log = OaonesUtil.integer(logId);
        if (log == null || log <= 0) {
            return result;
        }
        OaonesLog.step("查询同步明细 logId=" + log + " itemType=" + itemType);
        Map<String, Object> params = OaonesParam.of();
        params.put("logId", log);
        String type = itemType == null ? "" : itemType.trim();
        params.put("itemType", type.length() == 0 || "all".equalsIgnoreCase(type)
                ? null : OaonesUtil.integer(type));
        params.put("limit", limit > 0 ? limit : 5000);
        List<SyncLogItem> rows = OaonesMyBatis.call(SyncLogItemMapper.class,
                mapper -> mapper.list(params));
        if (rows == null) {
            return result;
        }
        for (SyncLogItem row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("log_id", OaonesUtil.nullToEmpty(row.getLogId()));
            json.put("requestid", OaonesUtil.nullToEmpty(row.getRequestid()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgid()));
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
            json.put("create_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
            result.add(json);
        }
        return result;
    }

    /** 按表字段长度截断，避免写库时超长报错。 */
    private String cut(String value, int max) {
        String text = OaonesUtil.s(value);
        return text.length() > max ? text.substring(0, max) : text;
    }
}
