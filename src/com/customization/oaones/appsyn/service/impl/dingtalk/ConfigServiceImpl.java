package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkApi;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.dingtalk.AppBindMapper;
import com.customization.oaones.appsyn.dao.dingtalk.DeptBindMapper;
import com.customization.oaones.appsyn.dao.dingtalk.DingTalkConfigMapper;
import com.customization.oaones.appsyn.dao.dingtalk.UserBindMapper;
import com.customization.oaones.appsyn.entity.dingtalk.DingTalkConfig;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import weaver.hrm.resource.ResourceComInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多钉钉租户配置维护实现（DAO 层为 {@link DingTalkConfigMapper}）。
 */
public class ConfigServiceImpl implements ConfigService {

    private String table() {
        return OaonesProps.cfgTable();
    }

    @Override
    public JSONArray list() {
        // 租户表与企微连接器共用（设置类表结构可复用时不新建表），这里只取本平台（type=1）的租户
        OaonesLog.step("查询钉钉租户配置列表 type=" + OaonesConst.TYPE_DINGTALK);
        JSONArray result = new JSONArray();
        List<DingTalkConfig> rows = OaonesMyBatis.call(DingTalkConfigMapper.class,
                mapper -> mapper.listByType(table(), OaonesConst.TYPE_DINGTALK));
        if (rows == null) {
            return result;
        }
        Map<String, String> names = nameMapOf(rows);
        for (DingTalkConfig row : rows) {
            result.add(toJson(row, names));
        }
        return result;
    }

    @Override
    public JSONArray listForUser(String operatorId) {
        return visibleRows(operatorId);
    }

    @Override
    public JSONArray listMaintainable(String operatorId) {
        return visibleRows(operatorId);
    }

    /** 当前登录人可查看/维护的租户（维护人本人；系统管理员为全部租户）。 */
    private JSONArray visibleRows(String operatorId) {
        JSONArray result = new JSONArray();
        String uid = OaonesUtil.s(operatorId).trim();
        JSONArray rows = list();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            boolean own = isMaintainerRow(row, uid);
            if (own || isSysAdmin(uid)) {
                row.put("can_maintain", own);
                result.add(row);
            }
        }
        OaonesLog.step("当前用户可见租户 operatorId=" + uid
                + " 全部=" + rows.size() + " 可见=" + result.size()
                + (isSysAdmin(uid) ? "（系统管理员）" : ""));
        return result;
    }

    @Override
    public boolean canMaintain(String cfgId, String operatorId) {
        String uid = OaonesUtil.s(operatorId).trim();
        if (isSysAdmin(uid)) {
            return true;
        }
        DingTalkConfig cfg = entity(cfgId);
        return cfg != null && isMaintainer(cfg.getMaintainerIds(), uid);
    }

    @Override
    public JSONObject get(String cfgId) {
        DingTalkConfig cfg = entity(cfgId);
        if (cfg == null) {
            return null;
        }
        return toJson(cfg, nameMap(maintainerIdList(cfg.getMaintainerIds())));
    }

    /** 原始实体（不做维护人姓名解析），供权限校验与内部读写使用。 */
    private DingTalkConfig entity(String cfgId) {
        Integer id = OaonesUtil.integer(cfgId);
        if (id == null || id <= 0) {
            return null;
        }
        return OaonesMyBatis.call(DingTalkConfigMapper.class, mapper -> mapper.getById(table(), id));
    }

    @Override
    public DingTalkApi getClient(String cfgId) {
        JSONObject cfg = get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        String corpSecret = OaonesUtil.s(cfg.getString("corp_secret"));
        if (corpId.length() == 0 || corpSecret.length() == 0) {
            throw new RuntimeException("钉钉租户[" + OaonesUtil.s(cfg.getString("corp_name"))
                    + "]未维护 AppKey / AppSecret（企业内部应用）");
        }
        return new DingTalkApi(corpId, corpSecret);
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        OaonesLog.step("保存钉钉租户配置 operatorId=" + operatorId
                + " id=" + OaonesUtil.s(params.getString("id"))
                + " corpId=" + OaonesUtil.s(params.getString("corp_id")));
        String id = OaonesUtil.s(params.getString("id"));
        String corpName = OaonesUtil.s(params.getString("corp_name"));
        String corpId = OaonesUtil.s(params.getString("corp_id"));
        String corpSecret = OaonesUtil.s(params.getString("corp_secret"));
        String agentId = OaonesUtil.s(params.getString("agent_id"));
        int rootDeptId = OaonesUtil.i(params.getString("root_dept_id"), 1);
        int syncEnable = OaonesUtil.i(params.getString("sync_enable"), 1);
        String remark = OaonesUtil.s(params.getString("remark"));
        // 维护人：可多选 OA 员工（逗号分隔的人员ID）；请求未带该参数时保留原值
        boolean maintainerProvided = params.containsKey("maintainer_ids");
        String maintainerIds = normalizeMaintainers(params.getString("maintainer_ids"));
        if (corpName.length() == 0) {
            throw new RuntimeException("请输入钉钉组织名称");
        }
        if (corpId.length() == 0) {
            throw new RuntimeException("请输入企业内部应用 AppKey");
        }
        if (corpSecret.length() == 0 && id.length() == 0) {
            throw new RuntimeException("请输入 AppSecret");
        }
        String operator = String.valueOf(OaonesUtil.i(operatorId, 0));
        String now = OaonesUtil.now();
        int modeId = OaonesProps.cfgModeId();

        if (id.length() == 0) {
            // 新建：非系统管理员且维护人未包含本人时自动补上，
            // 否则新建后该租户对创建人本人也不可见（未指定/未包含维护人 = 无权限查看）
            if (OaonesUtil.i(operator, 0) > 0 && !isSysAdmin(operator)
                    && !isMaintainer(maintainerIds, operator)) {
                maintainerIds = maintainerIds.length() == 0 ? operator : maintainerIds + "," + operator;
            }
            OaonesLog.step("新增钉钉租户维护人 maintainerIds=" + maintainerIds);
            return insertNew(corpName, corpId, corpSecret, agentId, rootDeptId,
                    syncEnable, remark, maintainerIds, operator, now, modeId);
        }
        String cfgId = String.valueOf(OaonesUtil.i(id, 0));
        if (corpSecret.length() == 0 || !maintainerProvided) {
            DingTalkConfig old = entity(cfgId);
            if (corpSecret.length() == 0) {
                corpSecret = old == null ? "" : OaonesUtil.s(old.getCorpSecret());
            }
            if (!maintainerProvided) {
                maintainerIds = old == null ? "" : normalizeMaintainers(old.getMaintainerIds());
            }
        }
        if (OaonesMyBatis.call(DingTalkConfigMapper.class,
                mapper -> mapper.getIdByCorpIdExcept(table(), corpId, OaonesUtil.i(id, 0),
                        OaonesConst.TYPE_DINGTALK)) != null) {
            throw new RuntimeException("AppKey 已被其他钉钉租户占用: " + corpId);
        }
        DingTalkConfig cfg = new DingTalkConfig();
        cfg.setId(OaonesUtil.i(id, 0));
        cfg.setType(OaonesConst.TYPE_DINGTALK);
        // 消息通知默认开启，之后在“消息设置 → 租户开关”里按租户调整
        cfg.setMsgEnable(1);
        cfg.setCorpName(corpName);
        cfg.setCorpId(corpId);
        cfg.setCorpSecret(corpSecret);
        cfg.setAgentId(agentId);
        cfg.setRootDeptId(rootDeptId);
        cfg.setQy(syncEnable);
        cfg.setRemark(remark);
        cfg.setMaintainerIds(maintainerIds);
        cfg.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        cfg.setModedatamodifydatetime(now);
        OaonesMyBatis.call(DingTalkConfigMapper.class, mapper -> {
            int rows = mapper.update(table(), cfg);
            if (rows <= 0) {
                throw new RuntimeException("钉钉租户配置更新失败");
            }
            return rows;
        });
        OaonesModeRight.rebuildOnEdit(table(), modeId, cfgId);
        return get(cfgId);
    }

    private JSONObject insertNew(String corpName, String corpId, String corpSecret, String agentId,
                                 int rootDeptId, int syncEnable, String remark, String maintainerIds,
                                 String operator, String now, int modeId) {
        if (OaonesMyBatis.call(DingTalkConfigMapper.class,
                mapper -> mapper.getIdByCorpId(table(), corpId, OaonesConst.TYPE_DINGTALK)) != null) {
            throw new RuntimeException("已存在 AppKey 为 " + corpId + " 的钉钉租户配置");
        }
        DingTalkConfig cfg = new DingTalkConfig();
        cfg.setType(OaonesConst.TYPE_DINGTALK);
        cfg.setCorpName(corpName);
        cfg.setCorpId(corpId);
        cfg.setCorpSecret(corpSecret);
        cfg.setAgentId(agentId);
        cfg.setRootDeptId(rootDeptId);
        cfg.setQy(syncEnable);
        cfg.setRemark(remark);
        cfg.setMaintainerIds(maintainerIds);
        cfg.setFormmodeid(modeId);
        cfg.setModedatacreater(OaonesUtil.i(operator, 0));
        cfg.setModedatacreatedate(OaonesUtil.nowDate());
        cfg.setModedatacreatetime(OaonesUtil.nowTime());
        cfg.setModedatamodifier(OaonesUtil.i(operator, 0));
        cfg.setModedatamodifydatetime(now);
        OaonesMyBatis.call(DingTalkConfigMapper.class, mapper -> {
            if (mapper.insert(table(), cfg) <= 0) {
                throw new RuntimeException("钉钉租户配置保存失败");
            }
            return null;
        });
        Integer newId = OaonesMyBatis.call(DingTalkConfigMapper.class,
                mapper -> mapper.getIdByCorpId(table(), corpId, OaonesConst.TYPE_DINGTALK));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operator);
        return get(String.valueOf(newId));
    }

    @Override
    public void delete(String cfgId) {
        Integer id = OaonesUtil.integer(cfgId);
        if (id == null || id <= 0) {
            throw new RuntimeException("缺少钉钉租户ID");
        }
        Integer bindCount = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.countActiveByCfgId(OaonesProps.userBindTable(), id));
        if (bindCount != null && bindCount > 0) {
            throw new RuntimeException("该租户下仍存在人员绑定关系，请先解绑后再删除配置");
        }
        Integer deptCount = OaonesMyBatis.call(DeptBindMapper.class,
                mapper -> mapper.countByCfgId(OaonesProps.deptBindTable(), id));
        if (deptCount != null && deptCount > 0) {
            throw new RuntimeException("该租户下仍存在部门绑定关系，请先删除部门绑定后再删除配置");
        }
        Integer appCount = OaonesMyBatis.call(AppBindMapper.class,
                mapper -> mapper.countByCfgId(OaonesProps.appBindTable(), id));
        if (appCount != null && appCount > 0) {
            throw new RuntimeException("该租户下仍存在应用绑定关系，请先到“应用绑定”页删除后再删除配置");
        }
        OaonesMyBatis.run(DingTalkConfigMapper.class, mapper -> mapper.deleteById(table(), id));
        OaonesLog.step("删除钉钉租户配置 cfgId=" + cfgId);
    }

    @Override
    public JSONObject test(String cfgId) throws Exception {
        OaonesLog.step("钉钉租户连通测试 cfgId=" + cfgId);
        JSONObject cfg = get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        // 连通测试只验证“能通即可”：能拿到 access_token 即代表 AppKey / AppSecret 正确、
        // 服务器到钉钉接口的网络可通。这里**不再**拉取全量部门与成员（大企业通讯录要递归
        // 逐部门分页拉取，耗时很长，页面上表现为点了没反应）。
        // 部门/人员快照请到“部门绑定 / 人员绑定”页刷新，或由定时任务维护。
        String token = getClient(cfgId).getAccessToken();
        JSONObject data = new JSONObject();
        data.put("corp_name", OaonesUtil.s(cfg.getString("corp_name")));
        data.put("access_token", token == null ? "" : token.substring(0, Math.min(6, token.length())) + "****");
        data.put("message", "连通成功");
        return data;
    }

    /* ------------------------------------------------------------------
     * 维护人（分权）：每个租户可指定多个 OA 员工为维护人，
     * 仅维护人可在“同步中心”选择该租户执行同步；未设置维护人的租户为公共租户。
     * ------------------------------------------------------------------ */

    /** 维护人字段归一化：仅保留正整数ID，去重并保持先后顺序，逗号分隔。 */
    private String normalizeMaintainers(String raw) {
        String value = OaonesUtil.s(raw).trim();
        if (value.length() == 0) {
            return "";
        }
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        for (String part : value.split("[,，;；\\s]+")) {
            String id = part == null ? "" : part.trim();
            if (id.length() == 0 || !id.matches("\\d+")) {
                continue;
            }
            int num = OaonesUtil.i(id, 0);
            if (num > 0) {
                ids.add(String.valueOf(num));
            }
        }
        if (ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
            if (sb.length() >= 900) {
                break;
            }
        }
        return sb.toString();
    }

    /** 维护人字段拆分为有序的人员ID列表。 */
    private List<String> maintainerIdList(String raw) {
        List<String> ids = new ArrayList<String>();
        String value = OaonesUtil.s(raw).trim();
        if (value.length() == 0) {
            return ids;
        }
        for (String id : value.split(",")) {
            String v = id == null ? "" : id.trim();
            if (v.length() > 0) {
                ids.add(v);
            }
        }
        return ids;
    }

    /** 行级判断：见 {@link #isMaintainer(String, String)}。 */
    private boolean isMaintainerRow(JSONObject row, String userId) {
        return row != null && isMaintainer(row.getString("maintainer_ids"), userId);
    }

    /**
     * 判断维护人字段（逗号分隔人员ID）中是否包含指定用户。
     *
     * <p>只有被指定的维护人本人有权查看与设置该租户；
     * 未指定维护人的租户对任何人都不可见（系统管理员走 {@link #canMaintain(String, String)} 单独放行）。</p>
     */
    private boolean isMaintainer(String maintainerIds, String userId) {
        String maintainers = OaonesUtil.s(maintainerIds).trim();
        String uid = OaonesUtil.s(userId).trim();
        if (maintainers.length() == 0 || uid.length() == 0) {
            return false;
        }
        for (String id : maintainerIdList(maintainers)) {
            if (id.equals(uid)) {
                return true;
            }
        }
        return false;
    }

    /** 是否拥有全部租户的维护权限：系统管理员（user.id = 1）或超管角色（total.admin.roleid）。 */
    private boolean isSysAdmin(String userId) {
        return OaonesAdmin.isTotalAdmin(userId);
    }

    /** 一次遍历汇总所有行维护人ID，用于批量解析姓名。 */
    private Map<String, String> nameMapOf(List<DingTalkConfig> rows) {
        Set<String> ids = new LinkedHashSet<String>();
        for (DingTalkConfig row : rows) {
            ids.addAll(maintainerIdList(row.getMaintainerIds()));
        }
        return nameMap(ids);
    }

    /**
     * 维护人ID → 姓名（E9 人员缓存，一次遍历；查不到的ID不返回，页面按ID展示）。
     */
    private Map<String, String> nameMap(Collection<String> ids) {
        Map<String, String> names = new HashMap<String, String>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        Set<String> wanted = new LinkedHashSet<String>(ids);
        try {
            ResourceComInfo rci = new ResourceComInfo();
            rci.setTofirstRow();
            while (rci.next() && !wanted.isEmpty()) {
                String id = OaonesUtil.s(rci.getResourceid());
                if (wanted.contains(id) && !names.containsKey(id)) {
                    names.put(id, OaonesUtil.s(rci.getLastname()));
                    wanted.remove(id);
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("读取维护人姓名失败，页面按人员ID展示", t);
        }
        return names;
    }

    /** 维护人 JSON 数组（id + name），供页面渲染标签。 */
    private JSONArray maintainersJson(DingTalkConfig row, Map<String, String> names) {
        JSONArray list = new JSONArray();
        for (String id : maintainerIdList(row.getMaintainerIds())) {
            String name = names == null ? null : names.get(id);
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("name", name == null ? "" : name);
            list.add(item);
        }
        return list;
    }

    /** 维护人姓名文本（姓名缺失时回退为“ID xxx”）。 */
    private String maintainersNameText(JSONArray maintainers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maintainers.size(); i++) {
            JSONObject item = maintainers.getJSONObject(i);
            String name = OaonesUtil.s(item.getString("name"));
            if (name.length() == 0) {
                name = "ID " + OaonesUtil.s(item.getString("id"));
            }
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(name);
        }
        return sb.toString();
    }

    /** 实体转前端 JSON（字段名与页面保持一致）。 */
    private JSONObject toJson(DingTalkConfig row, Map<String, String> names) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("corp_name", OaonesUtil.nullToEmpty(row.getCorpName()));
        json.put("corp_id", OaonesUtil.nullToEmpty(row.getCorpId()));
        json.put("corp_secret", OaonesUtil.nullToEmpty(row.getCorpSecret()));
        json.put("agent_id", OaonesUtil.nullToEmpty(row.getAgentId()));
        json.put("root_dept_id", OaonesUtil.nullToEmpty(row.getRootDeptId()));
        // 建模表中“同步开关/启用”物理字段为 qy
        json.put("sync_enable", OaonesUtil.nullToEmpty(row.getQy()));
        // 消息通知开关：在独立菜单“消息设置 → 租户开关”里维护（1 开启 / 0 关闭）
        json.put("msg_enable", row.getMsgEnable() == null ? "1" : String.valueOf(row.getMsgEnable()));
        json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        // 维护人：maintainer_ids 为逗号分隔的 OA 人员ID，maintainers 为 id+姓名明细
        JSONArray maintainers = maintainersJson(row, names);
        json.put("maintainer_ids", normalizeMaintainers(row.getMaintainerIds()));
        json.put("maintainers", maintainers);
        json.put("maintainer_names", maintainersNameText(maintainers));
        // create/update 使用 E9 建模系统标准审计字段
        json.put("create_by", OaonesUtil.nullToEmpty(row.getModedatacreater()));
        String createDate = OaonesUtil.s(row.getModedatacreatedate());
        String createTime = OaonesUtil.s(row.getModedatacreatetime());
        json.put("create_time", createDate.length() == 0 && createTime.length() == 0
                ? "" : createDate + " " + createTime);
        json.put("update_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
        return json;
    }

}
