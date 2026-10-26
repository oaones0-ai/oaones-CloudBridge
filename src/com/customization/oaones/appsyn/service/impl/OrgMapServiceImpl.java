package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.OrgMapMapper;
import com.customization.oaones.appsyn.dto.OrgMapRow;
import com.customization.oaones.appsyn.entity.OrgMap;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.OrgMapService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OA 分部 - 平台租户一对一映射实现。
 *
 * <p>约束按平台分别生效：同一个 OA 分部在一个平台只能映射一个租户；
 * 同一个租户也只能映射一个 OA 分部。企微、钉钉、飞书三组映射互不影响。</p>
 */
public class OrgMapServiceImpl implements OrgMapService {

    private String table() {
        return OaonesProps.subTenantMapTable();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    @Override
    public JSONObject list(int type, String operatorId, String keyword) {
        int platformType = normalizeType(type);
        JSONArray tenants = tenantRows(platformType, operatorId);
        Set<String> allowedCfgIds = new HashSet<String>();
        for (int i = 0; i < tenants.size(); i++) {
            allowedCfgIds.add(OaonesUtil.s(tenants.getJSONObject(i).getString("id")));
        }

        String k = OaonesUtil.s(keyword).trim().toLowerCase();
        JSONArray mappings = new JSONArray();
        List<OrgMapRow> rows = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.listByType(table(), OaonesProps.cfgTable(), platformType));
        if (rows != null) {
            for (OrgMapRow row : rows) {
                if (!allowedCfgIds.contains(OaonesUtil.nullToEmpty(row.getCfgId()))) {
                    continue;
                }
                JSONObject json = mapRowJson(row);
                if (k.length() > 0 && !searchText(json).contains(k)) {
                    continue;
                }
                mappings.add(json);
            }
        }

        JSONArray subcompanies = oaService().listSubCompaniesFromCache(operatorId);
        JSONObject summary = new JSONObject();
        summary.put("mapping_count", mappings.size());
        summary.put("tenant_count", tenants.size());
        summary.put("oa_sub_count", subcompanies.size());

        JSONObject data = new JSONObject();
        data.put("type", platformType);
        data.put("platform", platformKey(platformType));
        data.put("platform_label", platformLabel(platformType));
        data.put("tenants", tenants);
        data.put("oa_subcompanies", subcompanies);
        data.put("mappings", mappings);
        data.put("summary", summary);
        return data;
    }

    @Override
    public JSONObject save(JSONObject params, int type, String operatorId) {
        int platformType = normalizeType(type);
        String cfgId = OaonesUtil.s(params.getString("cfg_id")).trim();
        String subId = OaonesUtil.s(params.getString("oa_sub_id")).trim();
        String remark = OaonesUtil.s(params.getString("remark")).trim();
        if (cfgId.length() == 0 || subId.length() == 0) {
            throw new RuntimeException("请选择 OA 分部与平台租户");
        }
        JSONObject tenant = tenantOf(platformType, operatorId, cfgId);
        if (tenant == null) {
            throw new RuntimeException("该租户不存在、不属于当前平台，或当前登录人无权维护");
        }
        JSONObject sub = oaService().getSubCompany(subId);
        if (sub == null) {
            throw new RuntimeException("OA 分部不存在");
        }
        if ("1".equals(OaonesUtil.s(sub.getString("canceled")))) {
            throw new RuntimeException("OA 分部已封存，不能创建映射");
        }

        int cfgKey = OaonesUtil.i(cfgId, 0);
        int subKey = OaonesUtil.i(subId, 0);
        OrgMap bySub = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.findBySub(table(), platformType, subKey));
        OrgMap byCfg = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.findByCfg(table(), platformType, cfgKey));
        if (byCfg != null && intValue(byCfg.getOaSubId(), 0) != subKey) {
            throw new RuntimeException("租户“" + OaonesUtil.s(tenant.getString("corp_name"))
                    + "”已映射其他 OA 分部，同一个租户只能映射一个 OA 分部");
        }

        String operator = String.valueOf(OaonesUtil.i(operatorId, 0));
        String now = OaonesUtil.now();
        int modeId = OaonesProps.subTenantMapModeId();
        String subName = OaonesUtil.s(sub.getString("name"));
        if (bySub != null) {
            bySub.setCfgId(cfgKey);
            bySub.setYx(0);
            bySub.setRemark(remark);
            bySub.setModedatamodifier(OaonesUtil.i(operator, 0));
            bySub.setModedatamodifydatetime(now);
            try {
                OaonesMyBatis.call(OrgMapMapper.class, mapper -> {
                    if (mapper.update(table(), bySub) <= 0) {
                        throw new RuntimeException("分部映射更新失败");
                    }
                    return 1;
                });
            } catch (RuntimeException e) {
                throw saveConflictOrOriginal(platformType, cfgKey, subKey,
                        OaonesUtil.s(tenant.getString("corp_name")), e);
            }
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(bySub.getId()));
            OaonesLog.step("更新 OA 分部映射 id=" + bySub.getId()
                    + " type=" + platformType + " cfgId=" + cfgId + " oaSubId=" + subId);
            return savedJson(bySub.getId(), cfgId, OaonesUtil.s(tenant.getString("corp_name")),
                    OaonesUtil.s(tenant.getString("corp_id")), subId, subName, remark);
        }

        OrgMap entity = new OrgMap();
        entity.setCfgId(cfgKey);
        entity.setOaSubId(subKey);
        entity.setType(platformType);
        entity.setYx(0);
        entity.setRemark(remark);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(OaonesUtil.i(operator, 0));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(OaonesUtil.i(operator, 0));
        entity.setModedatamodifydatetime(now);
        try {
            OaonesMyBatis.call(OrgMapMapper.class, mapper -> {
                if (mapper.insert(table(), entity) <= 0) {
                    throw new RuntimeException("分部映射保存失败");
                }
                return 1;
            });
        } catch (RuntimeException e) {
            throw saveConflictOrOriginal(platformType, cfgKey, subKey,
                    OaonesUtil.s(tenant.getString("corp_name")), e);
        }
        OrgMap created = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.findBySub(table(), platformType, subKey));
        if (created == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(created.getId()), operator);
        OaonesLog.step("新增 OA 分部映射 id=" + created.getId()
                + " type=" + platformType + " cfgId=" + cfgId + " oaSubId=" + subId);
        return savedJson(created.getId(), cfgId, OaonesUtil.s(tenant.getString("corp_name")),
                OaonesUtil.s(tenant.getString("corp_id")), subId, subName, remark);
    }

    /**
     * 并发保存时，保存前的唯一性查询可能同时通过，最终由数据库唯一键拦下第二个请求。
     * 此时重新读取最新映射，把数据库异常转换成用户能理解的一对一冲突提示。
     */
    private RuntimeException saveConflictOrOriginal(int type, int cfgId, int subId,
                                                    String tenantName, RuntimeException original) {
        OrgMap latestByCfg = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.findByCfg(table(), type, cfgId));
        if (latestByCfg != null && intValue(latestByCfg.getOaSubId(), 0) != subId) {
            return new RuntimeException("租户“" + tenantName
                    + "”刚刚已被其他 OA 分部映射。同一个租户只能映射一个 OA 分部，请刷新页面后重新选择");
        }
        OrgMap latestBySub = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.findBySub(table(), type, subId));
        if (latestBySub != null && intValue(latestBySub.getCfgId(), 0) != cfgId) {
            return new RuntimeException("该 OA 分部刚刚已被其他租户映射。"
                    + "同一个 OA 分部只能映射一个租户，请刷新页面后重新选择");
        }
        return original;
    }

    @Override
    public void delete(String id, int type, String operatorId) {
        int platformType = normalizeType(type);
        int mappingId = OaonesUtil.i(id, 0);
        if (mappingId <= 0) {
            throw new RuntimeException("缺少映射记录ID");
        }
        OrgMap row = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.findById(table(), platformType, mappingId));
        if (row == null) {
            throw new RuntimeException("映射记录不存在或不属于当前平台");
        }
        JSONObject tenant = tenantOf(platformType, operatorId, OaonesUtil.nullToEmpty(row.getCfgId()));
        if (tenant == null) {
            throw new RuntimeException("当前登录人无权修改该租户的映射");
        }
        OaonesMyBatis.run(OrgMapMapper.class,
                mapper -> mapper.deleteById(table(), platformType, mappingId));
        OaonesLog.step("删除 OA 分部映射 id=" + mappingId + " type=" + platformType
                + " cfgId=" + row.getCfgId());
    }

    /**
     * 当前平台、当前登录人可维护的租户。
     *
     * <p>复用三个连接器已有的租户维护人分权实现，不另写一套权限规则；
     * 返回给页面时只保留展示字段，不携带 Secret。</p>
     */
    private JSONArray tenantRows(int type, String operatorId) {
        JSONArray raw;
        if (type == OaonesConst.TYPE_DINGTALK) {
            raw = new com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl()
                    .listMaintainable(operatorId);
        } else if (type == OaonesConst.TYPE_FEISHU) {
            raw = new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl()
                    .listMaintainable(operatorId);
        } else {
            raw = new com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl()
                    .listMaintainable(operatorId);
        }
        JSONArray out = new JSONArray();
        if (raw == null) {
            return out;
        }
        for (int i = 0; i < raw.size(); i++) {
            JSONObject row = raw.getJSONObject(i);
            JSONObject item = new JSONObject();
            item.put("id", OaonesUtil.s(row.getString("id")));
            item.put("corp_name", OaonesUtil.s(row.getString("corp_name")));
            item.put("corp_id", OaonesUtil.s(row.getString("corp_id")));
            item.put("sync_enable", OaonesUtil.s(row.getString("sync_enable")));
            item.put("can_maintain", row.getBooleanValue("can_maintain"));
            out.add(item);
        }
        return out;
    }

    private JSONObject tenantOf(int type, String operatorId, String cfgId) {
        String wanted = OaonesUtil.s(cfgId);
        JSONArray rows = tenantRows(type, operatorId);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (wanted.equals(OaonesUtil.s(row.getString("id")))) {
                return row;
            }
        }
        return null;
    }

    private JSONObject mapRowJson(OrgMapRow row) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("cfg_name", OaonesUtil.s(row.getCfgName()));
        json.put("cfg_corp_id", OaonesUtil.s(row.getCfgCorpId()));
        json.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getOaSubId()));
        json.put("oa_sub_name", OaonesUtil.s(row.getOaSubName()));
        json.put("oa_sub_parent_id", OaonesUtil.nullToEmpty(row.getOaSubParentId()));
        json.put("type", OaonesUtil.nullToEmpty(row.getType()));
        json.put("bind_state", OaonesUtil.nullToEmpty(row.getBindState()));
        json.put("remark", OaonesUtil.s(row.getRemark()));
        String createDate = OaonesUtil.s(row.getModedatacreatedate());
        String createTime = OaonesUtil.s(row.getModedatacreatetime());
        json.put("create_time", createDate.length() == 0 && createTime.length() == 0
                ? "" : createDate + " " + createTime);
        json.put("update_time", OaonesUtil.s(row.getModedatamodifydatetime()));
        return json;
    }

    private JSONObject savedJson(Integer id, String cfgId, String cfgName, String cfgCorpId,
                                 String subId, String subName, String remark) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(id));
        json.put("cfg_id", cfgId);
        json.put("cfg_name", cfgName);
        json.put("cfg_corp_id", cfgCorpId);
        json.put("oa_sub_id", subId);
        json.put("oa_sub_name", subName);
        json.put("remark", remark);
        return json;
    }

    private String searchText(JSONObject row) {
        return new StringBuilder()
                .append(OaonesUtil.s(row.getString("cfg_name"))).append(' ')
                .append(OaonesUtil.s(row.getString("cfg_corp_id"))).append(' ')
                .append(OaonesUtil.s(row.getString("oa_sub_name"))).append(' ')
                .append(OaonesUtil.s(row.getString("oa_sub_id")))
                .toString().toLowerCase();
    }

    private static int normalizeType(int type) {
        return type == OaonesConst.TYPE_DINGTALK || type == OaonesConst.TYPE_FEISHU
                ? type : OaonesConst.TYPE_WECHAT;
    }

    private static String platformKey(int type) {
        return type == OaonesConst.TYPE_DINGTALK ? "dingtalk"
                : (type == OaonesConst.TYPE_FEISHU ? "feishu" : "wecom");
    }

    private static String platformLabel(int type) {
        return type == OaonesConst.TYPE_DINGTALK ? "钉钉"
                : (type == OaonesConst.TYPE_FEISHU ? "飞书" : "企业微信");
    }

    private static int intValue(Integer value, int def) {
        return value == null ? def : value.intValue();
    }
}
