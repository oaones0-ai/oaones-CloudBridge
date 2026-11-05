package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.FeatureSettingCatalog;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.FeatureSettingMapper;
import com.customization.oaones.appsyn.entity.FeatureSetting;
import com.customization.oaones.appsyn.service.FeatureSettingService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能开关配置实现。
 *
 * <p>读取失败或配置表尚未创建时全部按启用处理；保存失败会明确提示先执行
 * oaones0022 迁移脚本。</p>
 */
public class FeatureSettingServiceImpl implements FeatureSettingService {

    private static volatile boolean missingTableLogged;

    private String table() {
        return OaonesProps.featureSettingTable();
    }

    @Override
    public JSONObject list(String operatorId) {
        requireAdmin(operatorId);
        List<FeatureSetting> rows = readRows(true);
        Map<String, Boolean> enabled = mapRows(rows);
        JSONArray items = new JSONArray();
        for (FeatureSettingCatalog.Definition definition : FeatureSettingCatalog.all()) {
            items.add(rowJson(definition, enabled.get(definition.getCode()).booleanValue()));
        }
        JSONObject data = new JSONObject();
        data.put("items", items);
        data.put("missing", Boolean.valueOf(rows == null));
        return data;
    }

    @Override
    public JSONObject save(String operatorId, JSONArray items) {
        requireAdmin(operatorId);
        if (items == null || items.size() == 0) {
            throw new RuntimeException("没有可保存的功能设置");
        }

        // 先整体校验，再逐项保存，避免“前面几项已写入、后面才发现不能关闭”的半保存状态。
        Map<String, Boolean> next = enabledMap();
        JSONArray normalized = new JSONArray();
        for (int i = 0; i < items.size(); i++) {
            JSONObject input = items.getJSONObject(i);
            if (input == null) {
                continue;
            }
            String code = OaonesUtil.s(input.getString("code")).trim();
            FeatureSettingCatalog.Definition definition = FeatureSettingCatalog.find(code);
            if (definition == null) {
                throw new RuntimeException("未知的功能开关：" + code);
            }
            boolean value = input.getBooleanValue("enabled");
            validateDependency(definition, value);
            next.put(code, Boolean.valueOf(value));
            JSONObject one = new JSONObject();
            one.put("code", code);
            one.put("enabled", Boolean.valueOf(value));
            normalized.add(one);
        }
        if (normalized.size() == 0) {
            throw new RuntimeException("没有可保存的功能设置");
        }

        for (int i = 0; i < normalized.size(); i++) {
            JSONObject one = normalized.getJSONObject(i);
            saveOne(one.getString("code"), one.getBooleanValue("enabled"), operatorId);
        }
        JSONObject data = new JSONObject();
        data.put("enabled", next);
        data.put("message", "已保存 " + normalized.size() + " 项功能设置");
        return data;
    }

    @Override
    public JSONObject publicSettings() {
        List<FeatureSetting> rows = readRows(false);
        JSONObject data = new JSONObject();
        data.put("enabled", mapRows(rows));
        data.put("missing", Boolean.valueOf(rows == null));
        return data;
    }

    @Override
    public Map<String, Boolean> enabledMap() {
        return mapRows(readRows(false));
    }

    private JSONObject rowJson(FeatureSettingCatalog.Definition definition, boolean enabled) {
        JSONObject row = new JSONObject();
        row.put("code", definition.getCode());
        row.put("group", definition.getGroup());
        row.put("module", definition.getModule());
        row.put("name", definition.getName());
        row.put("description", definition.getDescription());
        row.put("parentCode", definition.getParentCode());
        row.put("enabled", Boolean.valueOf(enabled));

        String dependency = definition.getDependency();
        if ("sync_plan".equals(dependency)) {
            Long count = countEnabledSyncPlans(OaonesProps.syncPlanTable());
            row.put("relatedCount", count);
            row.put("relatedLabel", "启用中的同步任务");
            row.put("canDisable", Boolean.valueOf(count != null && count.longValue() == 0L));
            if (count == null) {
                row.put("lockedReason", "暂时无法检查启用中的同步任务，不能关闭；请检查数据库连接后重试。");
            } else if (count.longValue() > 0L) {
                row.put("lockedReason", "已有 " + count + " 条启用中的同步任务，请先暂停或删除启用任务后再关闭。");
            }
        } else if ("app_bind".equals(dependency)) {
            Long count = countEnabledAppBinds(OaonesProps.appBindTable());
            row.put("relatedCount", count);
            row.put("relatedLabel", "启用中的应用绑定");
            row.put("canDisable", Boolean.valueOf(count != null && count.longValue() == 0L));
            if (count == null) {
                row.put("lockedReason", "暂时无法检查启用中的应用绑定，不能关闭；请检查数据库连接后重试。");
            } else if (count.longValue() > 0L) {
                row.put("lockedReason", "已有 " + count + " 条启用中的应用绑定，请先停用或删除这些应用后再关闭。");
            }
        }
        return row;
    }

    private void validateDependency(FeatureSettingCatalog.Definition definition, boolean enabled) {
        if (enabled || definition.getDependency().length() == 0) {
            return;
        }
        if ("sync_plan".equals(definition.getDependency())) {
            Long count = countEnabledSyncPlans(OaonesProps.syncPlanTable());
            if (count == null) {
                throw new RuntimeException("暂时无法检查启用中的同步任务，不能关闭定时同步");
            }
            if (count.longValue() > 0L) {
                throw new RuntimeException("已有 " + count + " 条启用中的同步任务，请先暂停或删除启用任务后再关闭定时同步");
            }
        } else if ("app_bind".equals(definition.getDependency())) {
            Long count = countEnabledAppBinds(OaonesProps.appBindTable());
            if (count == null) {
                throw new RuntimeException("暂时无法检查启用中的应用绑定，不能关闭应用绑定");
            }
            if (count.longValue() > 0L) {
                throw new RuntimeException("已有 " + count + " 条启用中的应用绑定，请先停用或删除这些应用后再关闭");
            }
        }
    }

    private List<FeatureSetting> readRows(boolean settingsPage) {
        try {
            return OaonesMyBatis.call(FeatureSettingMapper.class,
                    mapper -> mapper.list(table()));
        } catch (Throwable t) {
            if (settingsPage || !missingTableLogged) {
                OaonesLog.error("[featureSettings] 读取功能开关失败，已全部按启用处理；"
                        + "请执行 oaones0022 迁移脚本", t);
                missingTableLogged = true;
            }
            return null;
        }
    }

    private Map<String, Boolean> mapRows(List<FeatureSetting> rows) {
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        result.putAll(FeatureSettingCatalog.defaultEnabledMap());
        if (rows != null) {
            for (FeatureSetting row : rows) {
                if (row != null && FeatureSettingCatalog.find(row.getFeatureCode()) != null) {
                    result.put(row.getFeatureCode(),
                            Boolean.valueOf(row.getEnabled() != null && row.getEnabled().intValue() == 1));
                }
            }
        }
        return result;
    }

    private void saveOne(String code, boolean enabled, String operatorId) {
        FeatureSetting entity = new FeatureSetting();
        entity.setFeatureCode(code);
        entity.setEnabled(Integer.valueOf(enabled ? 1 : 0));
        String now = OaonesUtil.now();
        String createDate = OaonesUtil.nowDate();
        String createTime = OaonesUtil.nowTime();
        try {
            FeatureSetting exists = OaonesMyBatis.call(FeatureSettingMapper.class,
                    mapper -> mapper.getByCode(table(), code));
            if (exists == null) {
                OaonesMyBatis.run(FeatureSettingMapper.class,
                        mapper -> mapper.insert(table(), entity, operatorId, createDate, createTime, now));
            } else {
                OaonesMyBatis.run(FeatureSettingMapper.class,
                        mapper -> mapper.updateByCode(table(), entity, operatorId, now));
            }
        } catch (Throwable t) {
            throw new RuntimeException("保存功能设置失败，请先执行 oaones0022 迁移脚本并重启 E9："
                    + firstLine(t));
        }
    }

    private Long countEnabledAppBinds(String targetTable) {
        try {
            return OaonesMyBatis.call(FeatureSettingMapper.class,
                    mapper -> mapper.countEnabledAppBinds(targetTable));
        } catch (Throwable t) {
            OaonesLog.error("[featureSettings] 查询启用中的应用绑定数量失败 table=" + targetTable, t);
            return null;
        }
    }

    private Long countEnabledSyncPlans(String targetTable) {
        try {
            return OaonesMyBatis.call(FeatureSettingMapper.class,
                    mapper -> mapper.countEnabledSyncPlans(targetTable));
        } catch (Throwable t) {
            OaonesLog.error("[featureSettings] 查询启用中的同步任务数量失败 table=" + targetTable, t);
            return null;
        }
    }

    private static void requireAdmin(String operatorId) {
        if (!OaonesAdmin.isTotalAdmin(operatorId)) {
            throw new RuntimeException("只有系统管理员或超管角色成员可以维护功能设置");
        }
    }

    private static String firstLine(Throwable t) {
        String message = t == null ? "" : OaonesUtil.s(t.getMessage());
        int index = message.indexOf('\n');
        return index > 0 ? message.substring(0, index) : message;
    }
}
