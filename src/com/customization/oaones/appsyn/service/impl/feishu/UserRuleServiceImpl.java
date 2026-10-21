package com.customization.oaones.appsyn.service.impl.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.feishu.UserRuleMapper;
import com.customization.oaones.appsyn.entity.feishu.UserRule;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.feishu.ConfigService;
import com.customization.oaones.appsyn.service.feishu.UserRuleService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认人员绑定规则维护实现（DAO 层为 {@link UserRuleMapper}）。
 */
public class UserRuleServiceImpl implements UserRuleService {

    private String table() {
        return OaonesProps.userRuleTable();
    }

    @Override
    public JSONArray list(String operatorId) {
        JSONArray result = new JSONArray();
        List<UserRule> rows = OaonesMyBatis.call(UserRuleMapper.class, mapper -> mapper.list(table()));
        if (rows == null) {
            return result;
        }
        ConfigService cfgService = new ConfigServiceImpl();
        boolean sysAdmin = OaonesAdmin.isTotalAdmin(operatorId);
        Map<String, String> nameCache = new HashMap<String, String>();
        for (UserRule row : rows) {
            String cfgId = OaonesUtil.nullToEmpty(row.getCfgId());
            boolean isDefault = cfgId.length() == 0 || "0".equals(cfgId);
            if (!isDefault && !sysAdmin && !cfgService.canMaintain(cfgId, operatorId)) {
                // 租户分权：非系统管理员只看得到本人为维护人的租户独立配置
                continue;
            }
            JSONObject json = toJson(row, isDefault ? "default" : "tenant");
            // 建模标准字段里的创建人/修改人只有ID，列表页需要姓名，这里按需解析并缓存
            json.put("creator_name", userName(OaonesUtil.s(json.getString("creator_id")), nameCache));
            json.put("modifier_name", userName(OaonesUtil.s(json.getString("modifier_id")), nameCache));
            result.add(json);
        }
        return result;
    }

    @Override
    public JSONObject getEffective(String cfgId) {
        Integer cfg = OaonesUtil.integer(cfgId);
        if (cfg != null && cfg > 0) {
            UserRule tenant = OaonesMyBatis.call(UserRuleMapper.class,
                    mapper -> mapper.findByCfgId(table(), cfg));
            if (tenant != null) {
                return toJson(tenant, "tenant");
            }
        }
        UserRule def = OaonesMyBatis.call(UserRuleMapper.class, mapper -> mapper.findDefault(table()));
        return def == null ? null : toJson(def, "default");
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        boolean isDefault = cfgId.length() == 0 || "0".equals(cfgId);
        String cfg = isDefault ? "" : cfgId;
        String rules = UserRuleService.normalizeRules(OaonesUtil.s(params.getString("rules")));
        OaonesLog.step("保存人员绑定规则 cfgId=" + cfg + " rules=" + rules
                + " operatorId=" + operatorId);
        String now = OaonesUtil.now();
        int modeId = OaonesProps.userRuleModeId();
        Integer cfgKey = isDefault ? null : OaonesUtil.integer(cfg);

        UserRule exist = isDefault
                ? OaonesMyBatis.call(UserRuleMapper.class, mapper -> mapper.findDefault(table()))
                : OaonesMyBatis.call(UserRuleMapper.class, mapper -> mapper.findByCfgId(table(), cfgKey));

        if (exist != null) {
            final int existId = exist.getId() == null ? 0 : exist.getId();
            OaonesMyBatis.call(UserRuleMapper.class, mapper -> {
                int rows = mapper.updateRules(table(), existId, rules, modeId,
                        OaonesUtil.i(operatorId, 0), now);
                if (rows <= 0) {
                    throw new RuntimeException("默认绑定规则保存失败");
                }
                return rows;
            });
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(existId));
            return getEffective(cfg);
        }

        UserRule entity = new UserRule();
        entity.setCfgId(cfgKey);
        entity.setRules(rules);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(now);
        OaonesMyBatis.call(UserRuleMapper.class, mapper -> {
            if (mapper.insert(table(), entity) <= 0) {
                throw new RuntimeException("默认绑定规则保存失败");
            }
            return 1;
        });
        UserRule created = cfgKey == null
                ? OaonesMyBatis.call(UserRuleMapper.class, mapper -> mapper.findDefault(table()))
                : OaonesMyBatis.call(UserRuleMapper.class, mapper -> mapper.findByCfgId(table(), cfgKey));
        if (created != null && created.getId() != null) {
            OaonesModeRight.shareNewData(table(), modeId, String.valueOf(created.getId()), operatorId);
        }
        return getEffective(cfg);
    }

    @Override
    public void delete(String cfgId) {
        String cfg = OaonesUtil.s(cfgId);
        if (cfg.length() == 0 || "0".equals(cfg)) {
            throw new RuntimeException("默认配置不能删除，可直接编辑覆盖");
        }
        Integer cfgKey = OaonesUtil.integer(cfg);
        OaonesMyBatis.run(UserRuleMapper.class, mapper -> mapper.deleteByCfgId(table(), cfgKey));
        OaonesLog.step("删除租户独立默认绑定规则 cfgId=" + cfg);
    }

    private JSONObject toJson(UserRule row, String source) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("rules", OaonesUtil.nullToEmpty(row.getRules()));
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        json.put("creator_id", OaonesUtil.nullToEmpty(row.getModedatacreater()));
        json.put("modifier_id", OaonesUtil.nullToEmpty(row.getModedatamodifier()));
        String createDate = OaonesUtil.s(row.getModedatacreatedate());
        String createTime = OaonesUtil.s(row.getModedatacreatetime());
        String createTimeText = createDate.length() == 0 && createTime.length() == 0
                ? "" : createDate + " " + createTime;
        json.put("create_time", createTimeText);
        String modifyTime = OaonesUtil.s(row.getModedatamodifydatetime());
        json.put("modify_time", modifyTime.length() == 0 ? createTimeText : modifyTime);
        json.put("source", source);
        return json;
    }

    /** 建模标准字段中的操作人姓名（取不到返回空串，由页面显示为“—”）。 */
    private String userName(String userId, Map<String, String> cache) {
        if (userId.length() == 0 || "0".equals(userId)) {
            return "";
        }
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        String name = "";
        try {
            JSONObject user = new OaServiceImpl().getUser(userId);
            if (user != null) {
                name = OaonesUtil.s(user.getString("lastname"));
            }
        } catch (Exception ignore) {
            // 人员信息缺失不影响规则列表展示
        }
        cache.put(userId, name);
        return name;
    }
}
