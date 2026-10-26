package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.LogRetentionCatalog;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.LogRetentionMapper;
import com.customization.oaones.appsyn.entity.LogRetentionSetting;
import com.customization.oaones.appsyn.service.LogRetentionService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志保留设置实现。
 *
 * <p>默认值固定为 0：数据库没有对应行、配置表尚未创建或读取失败时都按永久保留处理，
 * 不会因为新功能上线意外删除历史日志。只有管理员明确保存大于 0 的天数后才开始清理。</p>
 */
public class LogRetentionServiceImpl implements LogRetentionService {

    private static volatile boolean missingTableLogged;
    /** 父子日志先清子表、再清汇总表，尽量缩短明细成为孤儿记录的时间窗口。 */
    private static final String[] CLEANUP_ORDER = {
            LogRetentionCatalog.API_LOG,
            LogRetentionCatalog.OP_LOG,
            LogRetentionCatalog.MSG_LOG,
            LogRetentionCatalog.ATT_LOG_ITEM,
            LogRetentionCatalog.ATT_LOG,
            LogRetentionCatalog.SYNC_LOG_ITEM,
            LogRetentionCatalog.SYNC_LOG,
            LogRetentionCatalog.MTG_SYNC_LOG
    };

    private String settingTable() {
        return OaonesProps.logRetentionTable();
    }

    @Override
    public JSONObject list(String operatorId) {
        requireAdmin(operatorId);
        Map<String, Integer> saved = settingsMap();
        JSONArray rows = new JSONArray();
        for (LogRetentionCatalog.Definition item : LogRetentionCatalog.all()) {
            Integer keep = saved.get(item.getCode());
            rows.add(rowJson(item, keep == null ? 0 : keep.intValue(), countRows(item)));
        }
        JSONObject data = new JSONObject();
        data.put("items", rows);
        data.put("missing", Boolean.valueOf(saved == null));
        data.put("scheduler", com.customization.oaones.appsyn.job.OaonesLogRetentionScheduler.status());
        return data;
    }

    @Override
    public JSONObject save(String operatorId, String logCode, int keepDays) {
        requireAdmin(operatorId);
        String code = requireCode(logCode);
        int days = normalizeDays(keepDays);
        saveOne(code, days);
        JSONObject data = new JSONObject();
        data.put("item", rowJson(LogRetentionCatalog.find(code), days));
        data.put("message", days <= 0 ? "已设置为永久保留" : ("已设置为保留 " + days + " 天"));
        return data;
    }

    @Override
    public JSONObject saveAll(String operatorId, JSONArray items) {
        requireAdmin(operatorId);
        if (items == null || items.size() == 0) {
            throw new RuntimeException("没有可保存的日志保留设置");
        }
        List<JSONObject> results = new ArrayList<JSONObject>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject input = items.getJSONObject(i);
            if (input == null) {
                continue;
            }
            String code = requireCode(input.getString("code"));
            int days = normalizeDays(OaonesUtil.i(input.getString("days"), 0));
            saveOne(code, days);
            JSONObject saved = new JSONObject();
            saved.put("code", code);
            saved.put("keepDays", Integer.valueOf(days));
            results.add(saved);
        }
        JSONObject data = new JSONObject();
        data.put("items", results);
        data.put("message", "已保存 " + results.size() + " 项日志保留设置");
        return data;
    }

    @Override
    public JSONObject cleanNow(String logCode) {
        String code = requireCode(logCode);
        LogRetentionCatalog.Definition item = LogRetentionCatalog.find(code);
        int days = keepDays(code);
        JSONObject result = new JSONObject();
        result.put("code", code);
        result.put("name", item.getName());
        result.put("table", item.getTableName());
        result.put("keepDays", Integer.valueOf(days));
        if (days <= 0) {
            result.put("deleted", Integer.valueOf(0));
            result.put("skipped", Boolean.TRUE);
            result.put("message", "永久保留，未清理");
            return result;
        }

        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.DAY_OF_MONTH, -days);
        String before = OaonesUtil.formatDateTime(cutoff.getTimeInMillis());
        String beforeDate = before.substring(0, 10);
        String beforeTime = before.substring(11);
        try {
            Integer deleted = OaonesMyBatis.call(LogRetentionMapper.class,
                    mapper -> mapper.deleteBefore(item.getTableName(), beforeDate, beforeTime));
            int count = deleted == null ? 0 : deleted.intValue();
            result.put("deleted", Integer.valueOf(count));
            result.put("cutoff", before);
            result.put("message", "已清理 " + count + " 条");
            if (count > 0) {
                OaonesLog.step("[logRetention] " + item.getName() + " 已清理 " + count
                        + " 条，时间截点=" + before);
            }
        } catch (Throwable t) {
            result.put("deleted", Integer.valueOf(0));
            result.put("failed", Boolean.TRUE);
            result.put("message", "清理失败：" + firstLine(t));
            OaonesLog.error("[logRetention] 清理日志失败 code=" + code
                    + " table=" + item.getTableName(), t);
        }
        return result;
    }

    @Override
    public JSONObject cleanAll(String operatorId) {
        requireAdmin(operatorId);
        return cleanExpiredAll();
    }

    @Override
    public JSONObject cleanExpiredAll() {
        JSONArray results = new JSONArray();
        int deleted = 0;
        int failed = 0;
        for (int i = 0; i < CLEANUP_ORDER.length; i++) {
            JSONObject one = cleanNow(CLEANUP_ORDER[i]);
            results.add(one);
            deleted += one.getIntValue("deleted");
            if (one.getBooleanValue("failed")) {
                failed++;
            }
        }
        JSONObject data = new JSONObject();
        data.put("items", results);
        data.put("deleted", Integer.valueOf(deleted));
        data.put("failed", Integer.valueOf(failed));
        data.put("message", failed > 0
                ? ("清理完成，共删除 " + deleted + " 条，" + failed + " 项失败")
                : ("清理完成，共删除 " + deleted + " 条"));
        return data;
    }

    @Override
    public int keepDays(String logCode) {
        String code = OaonesUtil.s(logCode).trim();
        if (LogRetentionCatalog.find(code) == null) {
            return 0;
        }
        LogRetentionSetting row = findSetting(code);
        return row == null ? 0 : normalizeDays(row.getKeepDays() == null ? 0 : row.getKeepDays().intValue());
    }

    private void saveOne(String logCode, int keepDays) {
        LogRetentionSetting entity = new LogRetentionSetting();
        entity.setLogCode(logCode);
        entity.setKeepDays(Integer.valueOf(keepDays));
        try {
            LogRetentionSetting exists = OaonesMyBatis.call(LogRetentionMapper.class,
                    mapper -> mapper.getByCode(settingTable(), logCode));
            if (exists == null) {
                OaonesMyBatis.run(LogRetentionMapper.class,
                        mapper -> mapper.insert(settingTable(), entity));
            } else {
                OaonesMyBatis.run(LogRetentionMapper.class,
                        mapper -> mapper.updateByCode(settingTable(), entity));
            }
        } catch (Throwable t) {
            throw new RuntimeException("保存日志保留设置失败，请先执行 oaones0019 迁移脚本并重启 E9："
                    + firstLine(t));
        }
    }

    private LogRetentionSetting findSetting(String logCode) {
        try {
            return OaonesMyBatis.call(LogRetentionMapper.class,
                    mapper -> mapper.getByCode(settingTable(), logCode));
        } catch (Throwable t) {
            noteMissingTable(t);
            return null;
        }
    }

    private Map<String, Integer> settingsMap() {
        try {
            List<LogRetentionSetting> rows = OaonesMyBatis.call(LogRetentionMapper.class,
                    mapper -> mapper.list(settingTable()));
            Map<String, Integer> map = new HashMap<String, Integer>();
            if (rows != null) {
                for (LogRetentionSetting row : rows) {
                    if (row != null && row.getLogCode() != null) {
                        map.put(row.getLogCode(),
                                Integer.valueOf(normalizeDays(row.getKeepDays() == null ? 0 : row.getKeepDays().intValue())));
                    }
                }
            }
            return map;
        } catch (Throwable t) {
            noteMissingTable(t);
            return null;
        }
    }

    private Long countRows(LogRetentionCatalog.Definition item) {
        try {
            return OaonesMyBatis.call(LogRetentionMapper.class,
                    mapper -> mapper.countRows(item.getTableName()));
        } catch (Throwable t) {
            OaonesLog.error("[logRetention] 查询日志表当前行数失败 code=" + item.getCode()
                    + " table=" + item.getTableName(), t);
            return null;
        }
    }

    private void noteMissingTable(Throwable t) {
        if (!missingTableLogged) {
            missingTableLogged = true;
            OaonesLog.error("[logRetention] 读取日志保留配置失败，已按全部永久保留继续运行；"
                    + "请执行 oaones0019 迁移脚本", t);
        }
    }

    private static int normalizeDays(int keepDays) {
        return keepDays <= 0 ? 0 : keepDays;
    }

    private static String requireCode(String logCode) {
        String code = OaonesUtil.s(logCode).trim();
        if (LogRetentionCatalog.find(code) == null) {
            throw new RuntimeException("未知的日志类型：" + code);
        }
        return code;
    }

    private static void requireAdmin(String operatorId) {
        if (!OaonesAdmin.isTotalAdmin(operatorId)) {
            throw new RuntimeException("只有系统管理员或超管角色成员可以维护日志保留设置");
        }
    }

    private static JSONObject rowJson(LogRetentionCatalog.Definition item, int keepDays) {
        return rowJson(item, keepDays, null);
    }

    private static JSONObject rowJson(LogRetentionCatalog.Definition item, int keepDays, Long rowCount) {
        JSONObject row = new JSONObject();
        row.put("code", item.getCode());
        row.put("name", item.getName());
        row.put("description", item.getDescription());
        row.put("table", item.getTableName());
        row.put("rowCount", rowCount);
        row.put("rowCountAvailable", Boolean.valueOf(rowCount != null));
        row.put("keepDays", Integer.valueOf(normalizeDays(keepDays)));
        row.put("permanent", Boolean.valueOf(keepDays <= 0));
        return row;
    }

    private static String firstLine(Throwable t) {
        String message = t == null ? "" : OaonesUtil.s(t.getMessage());
        int index = message.indexOf('\n');
        return index > 0 ? message.substring(0, index) : message;
    }
}
