package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesRules;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.dingtalk.UserBindMapper;
import com.customization.oaones.appsyn.dao.dingtalk.UserTmpMapper;
import com.customization.oaones.appsyn.dto.dingtalk.UserBindRow;
import com.customization.oaones.appsyn.entity.dingtalk.UserBind;
import com.customization.oaones.appsyn.entity.dingtalk.UserTmp;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.dingtalk.DeptBindService;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.dingtalk.UserBindService;
import com.customization.oaones.appsyn.service.dingtalk.UserRuleService;
import com.customization.oaones.appsyn.service.dingtalk.UserTmpService;
import weaver.general.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 人员绑定维护实现（DAO 层为 {@link UserBindMapper}）。
 */
public class UserBindServiceImpl implements UserBindService {

    private String table() {
        return OaonesProps.userBindTable();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    private UserTmpService userTmpService() {
        return new UserTmpServiceImpl();
    }

    private DeptBindService deptBindService() {
        return new DeptBindServiceImpl();
    }

    @Override
    public JSONArray listBound(String cfgId, String keyword) {
        OaonesLog.step("查询人员绑定列表 cfgId=" + cfgId + " keyword=" + keyword);
        JSONArray result = new JSONArray();
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        String k = keyword == null ? "" : keyword.trim();
        params.put("keyword", k.length() == 0 ? "" : "%" + k + "%");
        List<UserBindRow> rows = OaonesMyBatis.call(UserBindMapper.class, mapper -> mapper.listRows(params));
        if (rows == null) {
            return result;
        }
        for (UserBindRow row : rows) {
            result.add(toBoundJson(row));
        }
        return result;
    }

    @Override
    public JSONArray listOaUnbound(String cfgId, String keyword) throws Exception {
        JSONArray oaUsers = oaService().listUsers(keyword);
        Set<String> bound = boundOaUserIds(cfgId);
        // 已标记免绑定的 OA 人员不参与绑定，单方未绑定列表里同样不再列出
        Set<String> noBind = noBindOaUserIds(cfgId);
        JSONArray result = new JSONArray();
        for (int i = 0; i < oaUsers.size(); i++) {
            JSONObject u = oaUsers.getJSONObject(i);
            String id = OaonesUtil.s(u.getString("id"));
            if (!bound.contains(id) && !noBind.contains(id)) {
                result.add(u);
            }
        }
        return result;
    }

    @Override
    public JSONArray listWxUnbound(String cfgId) throws Exception {
        JSONArray result = new JSONArray();
        ConfigService cfgService = configService();
        JSONArray cfgRows;
        if (cfgId != null && cfgId.trim().length() > 0) {
            JSONObject one = cfgService.get(cfgId);
            cfgRows = new JSONArray();
            if (one != null) {
                cfgRows.add(one);
            }
        } else {
            cfgRows = cfgService.list();
        }
        for (int i = 0; i < cfgRows.size(); i++) {
            JSONObject cfg = cfgRows.getJSONObject(i);
            String cid = OaonesUtil.s(cfg.getString("id"));
            if (Util.getIntValue(OaonesUtil.s(cfg.getString("sync_enable")), 0) == 0) {
                continue;
            }
            JSONArray wxUsers = userTmpService().listNormal(cid);
            Set<String> bound = boundWxUserIds(cid);
            String cfgName = OaonesUtil.s(cfg.getString("corp_name"));
            for (int j = 0; j < wxUsers.size(); j++) {
                JSONObject row = wxUsers.getJSONObject(j);
                String wxUserId = OaonesUtil.s(row.getString("wx_user_id"));
                if (wxUserId.length() == 0 || bound.contains(wxUserId)
                        || "0".equals(OaonesUtil.s(row.getString("no_bind")))) {
                    continue;
                }
                row.put("cfg_id", cid);
                row.put("cfg_name", cfgName);
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public JSONArray listWxUsers(String cfgId, String keyword) throws Exception {
        JSONArray wxUsers = userTmpService().listNormal(cfgId);
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        JSONArray result = new JSONArray();
        for (int j = 0; j < wxUsers.size(); j++) {
            JSONObject row = wxUsers.getJSONObject(j);
            String wxUserId = OaonesUtil.s(row.getString("wx_user_id"));
            String name = OaonesUtil.s(row.getString("wx_name"));
            if (k.length() > 0 && !(name.toLowerCase().contains(k) || wxUserId.toLowerCase().contains(k))) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    @Override
    public JSONObject autoMatch(JSONObject params) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        if (cfgId.length() == 0) {
            throw new RuntimeException("缺少钉钉租户");
        }
        String ruleCsv = OaonesUtil.s(params.getString("rules"));
        String normalizedRules = UserRuleService.normalizeRules(ruleCsv);
        if (normalizedRules.length() == 0) {
            throw new RuntimeException("默认绑定规则为空，请先在“默认绑定规则”页配置邮箱/手机号/姓名/工号");
        }
        Set<String> rules = new HashSet<String>();
        for (String r : normalizedRules.split(",")) {
            rules.add(r.trim());
        }
        Set<String> scopeOaIds = splitCsvToSet(params.getString("oa_ids"));

        JSONArray oaRows = oaService().listUsers("");
        Set<String> boundOa = boundOaUserIds(cfgId);
        List<JSONObject> oaList = new ArrayList<JSONObject>();
        for (int i = 0; i < oaRows.size(); i++) {
            JSONObject oa = oaRows.getJSONObject(i);
            String oaId = OaonesUtil.s(oa.getString("id"));
            if (boundOa.contains(oaId)) {
                continue;
            }
            if (!scopeOaIds.isEmpty() && !scopeOaIds.contains(oaId)) {
                continue;
            }
            oaList.add(oa);
        }

        JSONArray wxRows = listWxUsers(cfgId, "");
        Set<String> boundWx = boundWxUserIds(cfgId);
        List<JSONObject> wxList = new ArrayList<JSONObject>();
        for (int i = 0; i < wxRows.size(); i++) {
            JSONObject wx = wxRows.getJSONObject(i);
            if (!boundWx.contains(OaonesUtil.s(wx.getString("wx_user_id")))
                    && !"0".equals(OaonesUtil.s(wx.getString("no_bind")))) {
                wxList.add(wx);
            }
        }

        JSONArray matches = new JSONArray();
        List<JSONObject> remainingOa = oaList;
        List<JSONObject> remainingWx = wxList;
        for (String rule : new String[]{"email", "mobile", "name", "workcode"}) {
            if (!rules.contains(rule)) {
                continue;
            }
            OaonesLog.step("默认人员自动匹配处理字段=" + rule
                    + " 剩余OA数=" + remainingOa.size()
                    + " 剩余钉钉数=" + remainingWx.size());
            Map<String, List<JSONObject>> oaByValue = new HashMap<String, List<JSONObject>>();
            for (JSONObject oa : remainingOa) {
                String value = ruleValue(rule, oa, false);
                if (value.length() == 0) {
                    continue;
                }
                List<JSONObject> list = oaByValue.get(value);
                if (list == null) {
                    list = new ArrayList<JSONObject>();
                    oaByValue.put(value, list);
                }
                list.add(oa);
            }
            Map<String, List<JSONObject>> wxByValue = new HashMap<String, List<JSONObject>>();
            for (JSONObject wx : remainingWx) {
                String value = ruleValue(rule, wx, true);
                if (value.length() == 0) {
                    continue;
                }
                List<JSONObject> list = wxByValue.get(value);
                if (list == null) {
                    list = new ArrayList<JSONObject>();
                    wxByValue.put(value, list);
                }
                list.add(wx);
            }
            List<JSONObject> matchedOa = new ArrayList<JSONObject>();
            List<JSONObject> matchedWx = new ArrayList<JSONObject>();
            for (Map.Entry<String, List<JSONObject>> entry : oaByValue.entrySet()) {
                List<JSONObject> oaHit = entry.getValue();
                if (oaHit.size() != 1) {
                    continue;
                }
                List<JSONObject> wxHit = wxByValue.get(entry.getKey());
                if (wxHit == null || wxHit.size() != 1) {
                    continue;
                }
                JSONObject oa = oaHit.get(0);
                JSONObject wx = wxHit.get(0);
                JSONObject match = new JSONObject();
                match.put("oa_user_id", OaonesUtil.s(oa.getString("id")));
                match.put("oa_name", OaonesUtil.s(oa.getString("lastname")));
                match.put("oa_loginid", OaonesUtil.s(oa.getString("loginid")));
                match.put("oa_workcode", OaonesUtil.s(oa.getString("workcode")));
                match.put("oa_mobile", OaonesUtil.s(oa.getString("mobile")));
                match.put("oa_dept_name", OaonesUtil.s(oa.getString("departmentname")));
                match.put("wx_user_id", OaonesUtil.s(wx.getString("wx_user_id")));
                match.put("wx_name", OaonesUtil.s(wx.getString("wx_name")));
                match.put("wx_dept_name", OaonesUtil.s(wx.getString("wx_dept_name")));
                match.put("wx_mobile", OaonesUtil.s(wx.getString("mobile")));
                match.put("rule", rule);
                matches.add(match);
                matchedOa.add(oa);
                matchedWx.add(wx);
            }
            if (!matchedOa.isEmpty()) {
                List<JSONObject> nextOa = new ArrayList<JSONObject>();
                for (JSONObject oa : remainingOa) {
                    if (!matchedOa.contains(oa)) {
                        nextOa.add(oa);
                    }
                }
                remainingOa = nextOa;
            }
            if (!matchedWx.isEmpty()) {
                List<JSONObject> nextWx = new ArrayList<JSONObject>();
                for (JSONObject wx : remainingWx) {
                    if (!matchedWx.contains(wx)) {
                        nextWx.add(wx);
                    }
                }
                remainingWx = nextWx;
            }
        }
        OaonesLog.step("默认人员自动匹配 cfgId=" + cfgId + " rules=" + normalizedRules
                + " matchCount=" + matches.size());
        JSONObject data = new JSONObject();
        data.put("matches", matches);
        data.put("count", matches.size());
        data.put("oa_unbound_count", oaList.size());
        data.put("wx_unbound_count", wxList.size());
        return data;
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) throws Exception {
        OaonesLog.step("保存人员绑定 cfgId=" + OaonesUtil.s(params.getString("cfg_id"))
                + " oaUserId=" + OaonesUtil.s(params.getString("oa_user_id"))
                + " wxUserId=" + OaonesUtil.s(params.getString("wx_user_id")));
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        String oaUserId = OaonesUtil.s(params.getString("oa_user_id"));
        String wxUserId = OaonesUtil.s(params.getString("wx_user_id"));
        String remark = OaonesUtil.s(params.getString("remark"));
        int bindSource = Util.getIntValue(OaonesUtil.s(params.getString("bind_source")),
                OaonesConst.BIND_SOURCE_MANUAL);
        if (bindSource != OaonesConst.BIND_SOURCE_SYSTEM
                && bindSource != OaonesConst.BIND_SOURCE_MANUAL
                && bindSource != OaonesConst.BIND_SOURCE_SELF) {
            bindSource = OaonesConst.BIND_SOURCE_MANUAL;
        }
        if (remark.length() == 0) {
            if (bindSource == OaonesConst.BIND_SOURCE_SYSTEM) {
                remark = "默认规则自动绑定";
            } else if (bindSource == OaonesConst.BIND_SOURCE_SELF) {
                remark = "自行绑定";
            } else {
                remark = "手动绑定";
            }
        }
        if (cfgId.length() == 0 || oaUserId.length() == 0 || wxUserId.length() == 0) {
            throw new RuntimeException("请选择钉钉租户、OA 人员与钉钉成员");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        JSONObject oaUser = oaService().getUser(oaUserId);
        if (oaUser == null) {
            throw new RuntimeException("OA 人员不存在或已被删除");
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (!userTmpService().isNormal(corpId, wxUserId)) {
            throw new RuntimeException("钉钉成员 " + wxUserId
                    + " 不在人员快照中或状态异常（已删除/已封存），请先更新钉钉人员快照");
        }
    if (userTmpService().isNoBind(corpId, wxUserId)
            && !isBoundPair(cfgId, oaUserId, wxUserId)) {
        throw new RuntimeException("钉钉成员 " + wxUserId
                + " 已标记免绑定，不能建立绑定；如确需绑定请先在“钉钉参照”页取消免绑定");
    }
    if (isOaNoBind(cfgId, oaUserId) && !isBoundPair(cfgId, oaUserId, wxUserId)) {
        throw new RuntimeException("OA 人员 " + oaUserId
                + " 已标记免绑定，不能建立绑定；如确需绑定请先在“OA数据基准”页取消免绑定");
    }
        String now = OaonesUtil.now();
        int modeId = OaonesProps.userBindModeId();
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int oaKey = OaonesUtil.i(oaUserId, 0);
        // OA → 钉钉为“多对一”：同一钉钉成员可被多个 OA 人员绑定（多个 OA 账户对应同一个钉钉账户），
        // 因此这里不再校验钉钉成员是否已被其他 OA 人员绑定；
        // 仍由 (cfg_id, oa_user_id) 唯一索引保证同一 OA 人员只保留一条绑定记录。
        // 推导钉钉部门：OA 人员当前所在部门若已做部门绑定，则记录其钉钉部门
        String oaDeptId = OaonesUtil.s(oaUser.getString("departmentid"));
        String wxDeptId = "";
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject db = deptBinds.getJSONObject(i);
            if (oaDeptId.equals(OaonesUtil.s(db.getString("oa_dept_id")))) {
                wxDeptId = OaonesUtil.s(db.getString("wx_dept_id"));
                break;
            }
        }
        Integer existId = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.findIdByOaUser(table(), cfgKey, oaKey));
    if (existId != null) {
        UserBind entity = new UserBind();
        entity.setId(existId);
        entity.setXUserId(wxUserId);
        entity.setWxDeptId(OaonesUtil.i(wxDeptId, 0));
        entity.setBindState(1);
        // 建立绑定时把免绑定行改回绑定行（bind_state 1=已绑定），免绑定标记随 bind_state 一起被覆盖
        entity.setBindSource(bindSource);
            entity.setRemark(remark);
            entity.setBindTime(now);
            entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
            entity.setModedatamodifydatetime(now);
            OaonesMyBatis.call(UserBindMapper.class, mapper -> {
                if (mapper.update(table(), entity) <= 0) {
                    throw new RuntimeException("人员绑定更新失败");
                }
                return 1;
            });
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(existId));
            return find(String.valueOf(existId));
        }
        UserBind entity = new UserBind();
        entity.setCfgId(cfgKey);
        entity.setOaUserId(oaKey);
        entity.setXUserId(wxUserId);
    entity.setWxDeptId(OaonesUtil.i(wxDeptId, 0));
    entity.setBindState(1);
    entity.setBindSource(bindSource);
        entity.setRemark(remark);
        entity.setBindTime(now);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(now);
        OaonesMyBatis.call(UserBindMapper.class, mapper -> {
            if (mapper.insert(table(), entity) <= 0) {
                throw new RuntimeException("人员绑定保存失败");
            }
            return 1;
        });
        Integer newId = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.findIdByOaUser(table(), cfgKey, oaKey));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operatorId);
        return find(String.valueOf(newId));
    }

    @Override
    public JSONObject batchSave(JSONObject params, String operatorId) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        List<String> oaIds = splitCsv(params.getString("oa_ids"));
        List<String> wxIds = splitCsv(params.getString("wx_ids"));
        if (cfgId.length() == 0) {
            throw new RuntimeException("缺少钉钉租户");
        }
        if (oaIds.isEmpty() || oaIds.size() != wxIds.size()) {
            throw new RuntimeException("批量绑定参数不完整");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        String bindReason = defaultBindReason(OaonesUtil.s(params.getString("rules")));
        Set<String> seenOa = new HashSet<String>();
        List<JSONObject> items = new ArrayList<JSONObject>();
        for (int i = 0; i < oaIds.size(); i++) {
            String oaId = oaIds.get(i);
            String wxId = wxIds.get(i);
            if (oaId.length() == 0 || wxId.length() == 0) {
                throw new RuntimeException("第 " + (i + 1) + " 条 OA人员/钉钉成员未选择完整");
            }
            // 同一个 OA 人员在一批中只能出现一次；钉钉成员允许重复（多对一：多个 OA 账户对应同一钉钉账户）
            if (!seenOa.add(oaId)) {
                throw new RuntimeException("批量绑定中 OA 人员ID " + oaId + " 重复");
            }
            JSONObject item = new JSONObject();
            item.put("cfg_id", cfgId);
            item.put("oa_user_id", oaId);
            item.put("wx_user_id", wxId);
            item.put("remark", bindReason);
            item.put("bind_source", OaonesConst.BIND_SOURCE_SYSTEM);
            items.add(item);
        }
        OaonesLog.step("批量保存人员绑定 cfgId=" + cfgId + " total=" + items.size());
        int success = 0;
        JSONArray failures = new JSONArray();
        for (JSONObject item : items) {
            try {
                save(item, operatorId);
                success++;
            } catch (Exception e) {
                failures.add("OA人员[" + OaonesUtil.s(item.getString("oa_user_id"))
                        + "→" + OaonesUtil.s(item.getString("wx_user_id")) + "]保存失败: "
                        + (e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("fail", items.size() - success);
        result.put("total", items.size());
        if (failures.size() > 0) {
            result.put("failures", failures);
        }
        return result;
    }

    @Override
    public void delete(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            throw new RuntimeException("缺少人员绑定记录ID");
        }
        OaonesMyBatis.run(UserBindMapper.class, mapper -> mapper.deleteById(table(), id));
        OaonesLog.step("删除人员绑定 bindId=" + bindId);
    }

    @Override
    public JSONArray listOaNoBind(String cfgId) {
        OaonesLog.step("查询 OA 人员免绑定列表 cfgId=" + cfgId);
        JSONArray result = new JSONArray();
        List<String> ids = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.listNoBindOaUserIds(table(), OaonesUtil.i(cfgId, 0)));
        if (ids == null) {
            return result;
        }
        for (String id : ids) {
            result.add(OaonesUtil.s(id));
        }
        return result;
    }

    @Override
    public JSONObject markOaNoBind(String cfgId, String oaUserId, String operatorId) throws Exception {
        return setOaNoBindFlag(cfgId, oaUserId, true, operatorId);
    }

    @Override
    public JSONObject cancelOaNoBind(String cfgId, String oaUserId) throws Exception {
        return setOaNoBindFlag(cfgId, oaUserId, false, "");
    }

    @Override
    public JSONObject batchOaNoBind(JSONObject params, String operatorId) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        List<String> oaIds = splitCsv(params.getString("oa_ids"));
        OaonesLog.step("批量标记 OA 人员免绑定 cfgId=" + cfgId + " oaIds=" + oaIds);
        if (cfgId.length() == 0) {
            throw new RuntimeException("缺少钉钉租户");
        }
        if (oaIds.isEmpty()) {
            throw new RuntimeException("请先勾选要设置免绑定的 OA 人员");
        }
        Set<String> seen = new HashSet<String>();
        for (String oaId : oaIds) {
            if (!seen.add(oaId)) {
                throw new RuntimeException("批量免绑定中 OA 人员ID " + oaId + " 重复");
            }
        }
        // 一次提交的人员逐条处理，逐条返回失败原因（例如已绑定钉钉成员、OA 人员已不存在）
        JSONArray failRows = new JSONArray();
        int success = 0;
        for (String oaId : oaIds) {
            try {
                setOaNoBindFlag(cfgId, oaId, true, operatorId);
                success++;
            } catch (Exception e) {
                JSONObject fail = new JSONObject();
                fail.put("oa_user_id", oaId);
                fail.put("reason", e.getMessage() == null ? "设置免绑定失败" : e.getMessage());
                failRows.add(fail);
            }
        }
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("total", oaIds.size());
        result.put("fail_rows", failRows);
        return result;
    }

    /**
     * OA 人员侧“免绑定”标记：直接维护 uf_oaones_appsyn_user_bind.bind_state
     * （0 = 免绑定，1 = 已绑定），不再使用独立的 no_bind 字段。
     *
     * <p>本表只落两类行：已绑定（{@code x_user_id} 非空、bind_state=1）与免绑定
     * （bind_state=0、{@code x_user_id} 为空）；“待绑定/未绑定”的 OA 人员不落库，
     * 由页面每次加载时按 OA 人员与绑定记录在代码中判定。</p>
     *
     * <p>所有“查绑定关系/同步”的语句都带 {@code bind_state = 1}，因此免绑定行不会被当成绑定。</p>
     */
    private JSONObject setOaNoBindFlag(String cfgId, String oaUserId, boolean noBind, String operatorId)
            throws Exception {
        OaonesLog.step((noBind ? "标记 OA 人员免绑定" : "取消 OA 人员免绑定")
                + " cfgId=" + cfgId + " oaUserId=" + oaUserId);
        if (cfgId == null || cfgId.trim().length() == 0) {
            throw new RuntimeException("缺少钉钉租户");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        String oaKey = oaUserId == null ? "" : oaUserId.trim();
        if (oaKey.length() == 0) {
            throw new RuntimeException("缺少 OA 人员ID");
        }
        JSONObject oaUser = oaService().getUser(oaKey);
        if (oaUser == null) {
            throw new RuntimeException("OA 人员不存在或已被删除");
        }
        int cfgKey = OaonesUtil.i(cfgId, 0);
        Integer activeId = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.findActiveIdByOaUser(table(), cfgKey, OaonesUtil.i(oaKey, 0)));
        String now = OaonesUtil.now();
        int modeId = OaonesProps.userBindModeId();
        if (noBind) {
            if (activeId != null) {
                throw new RuntimeException("该 OA 人员已绑定钉钉成员，不能标记为免绑定；如需免绑定请先解绑");
            }
            Integer existId = OaonesMyBatis.call(UserBindMapper.class,
                    mapper -> mapper.findIdByOaUser(table(), cfgKey, OaonesUtil.i(oaKey, 0)));
            if (existId != null) {
                UserBind entity = new UserBind();
                entity.setId(existId);
                entity.setXUserId("");
                entity.setWxDeptId(0);
                entity.setBindState(0);
                entity.setBindSource(0);
                entity.setRemark("免绑定");
                entity.setBindTime(now);
                entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
                entity.setModedatamodifydatetime(now);
                OaonesMyBatis.call(UserBindMapper.class, mapper -> {
                    if (mapper.update(table(), entity) <= 0) {
                        throw new RuntimeException("设置免绑定失败");
                    }
                    return 1;
                });
                OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(existId));
            } else {
                UserBind entity = new UserBind();
                entity.setCfgId(cfgKey);
                entity.setOaUserId(OaonesUtil.i(oaKey, 0));
                entity.setXUserId("");
                entity.setWxDeptId(0);
                entity.setBindState(0);
                entity.setBindSource(0);
                entity.setRemark("免绑定");
                entity.setBindTime(now);
                entity.setFormmodeid(modeId);
                entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
                entity.setModedatacreatedate(OaonesUtil.nowDate());
                entity.setModedatacreatetime(OaonesUtil.nowTime());
                entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
                entity.setModedatamodifydatetime(now);
                OaonesMyBatis.call(UserBindMapper.class, mapper -> {
                    if (mapper.insert(table(), entity) <= 0) {
                        throw new RuntimeException("设置免绑定失败");
                    }
                    return 1;
                });
                Integer newId = OaonesMyBatis.call(UserBindMapper.class,
                        mapper -> mapper.findIdByOaUser(table(), cfgKey, OaonesUtil.i(oaKey, 0)));
                if (newId != null) {
                    OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId),
                            operatorId);
                }
            }
        } else {
            Integer existId = OaonesMyBatis.call(UserBindMapper.class,
                    mapper -> mapper.findIdByOaUser(table(), cfgKey, OaonesUtil.i(oaKey, 0)));
            if (existId == null) {
                throw new RuntimeException("该 OA 人员未标记免绑定");
            }
            if (activeId != null) {
                // 已绑定的行本身就不是“免绑定”（建立绑定时会清掉标记），不覆盖绑定数据，直接返回当前状态
                return noBindResult(cfgId, oaKey, false);
            }
            // 免绑定记录本身不是绑定关系：取消免绑定后直接删除该行，回到“未绑定”
            OaonesMyBatis.run(UserBindMapper.class, mapper -> mapper.deleteById(table(), existId));
        }
        return noBindResult(cfgId, oaKey, noBind);
    }

    /** 免绑定操作结果：{@code no_bind = 0} 表示已免绑定，空串表示未免绑定（与表口径一致）。 */
    private JSONObject noBindResult(String cfgId, String oaUserId, boolean noBind) {
        JSONObject result = new JSONObject();
        result.put("cfg_id", cfgId);
        result.put("oa_user_id", oaUserId);
        result.put("no_bind", noBind ? OaonesConst.NO_BIND_MARK : "");
        return result;
    }

    @Override
    public JSONArray listBoundRows(String cfgId) {
        OaonesLog.step("查询人员绑定同步明细 cfgId=" + cfgId);
        JSONArray result = new JSONArray();
        Integer cfg = OaonesUtil.integer(cfgId);
        List<UserBindRow> rows = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.listBoundRows(table(), cfg == null ? 0 : cfg));
        if (rows == null) {
            return result;
        }
        for (UserBindRow row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
            json.put("oa_user_id", OaonesUtil.nullToEmpty(row.getOaUserId()));
            json.put("oa_name", OaonesUtil.nullToEmpty(row.getOaName()));
            json.put("oa_loginid", OaonesUtil.nullToEmpty(row.getLoginid()));
            json.put("oa_mobile", OaonesUtil.nullToEmpty(row.getOaMobile()));
            json.put("oa_email", OaonesUtil.nullToEmpty(row.getOaEmail()));
            json.put("oa_workcode", OaonesUtil.nullToEmpty(row.getWorkcode()));
            json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getOaDeptId()));
            json.put("oa_subcompany_id", OaonesUtil.nullToEmpty(row.getOaSubcompanyId()));
            json.put("oa_status", OaonesUtil.nullToEmpty(row.getOaStatus()));
            json.put("oa_canceled", OaonesUtil.nullToEmpty(row.getOaCanceled()));
            json.put("wx_user_id", OaonesUtil.nullToEmpty(row.getWxUserId()));
            json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
            json.put("bind_source", OaonesUtil.nullToEmpty(row.getBindSource()));
            result.add(json);
        }
        return result;
    }

    @Override
    public void markSynced(String cfgId, String oaUserId, int direction) {
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int oaKey = OaonesUtil.i(oaUserId, 0);
        String now = OaonesUtil.now();
        OaonesMyBatis.run(UserBindMapper.class, mapper ->
                mapper.markSynced(table(), cfgKey, oaKey, direction, now));
        OaonesLog.step("标记人员已同步 cfgId=" + cfgId + " oaUserId=" + oaUserId
                + " direction=" + direction);
    }

    @Override
    public String resolveWxUserId(String cfgId, String oaUserId) throws Exception {
        JSONObject oaUser = oaService().getUser(oaUserId);
        if (oaUser == null) {
            throw new RuntimeException("OA 人员[id=" + oaUserId + "]不存在或已离职/封存");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
        }
        return resolveWxUserId(OaonesUtil.s(cfg.getString("corp_id")), oaUser);
    }

    @Override
    public JSONObject syncToWx(JSONObject params, String operatorId) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        String oaUserId = OaonesUtil.s(params.getString("oa_user_id"));
        OaonesLog.step("同步人员 OA->钉钉 cfgId=" + cfgId + " oaUserId=" + oaUserId);
        if (cfgId.length() == 0 || oaUserId.length() == 0) {
            throw new RuntimeException("请选择钉钉租户与 OA 人员");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        JSONObject oaUser = oaService().getUser(oaUserId);
        if (oaUser == null) {
            throw new RuntimeException("OA 人员不存在或已离职/封存，无法同步人员");
        }
    Integer existBind = OaonesMyBatis.call(UserBindMapper.class,
            mapper -> mapper.findActiveIdByOaUser(table(), OaonesUtil.i(cfgId, 0), OaonesUtil.i(oaUserId, 0)));
    if (existBind != null) {
        throw new RuntimeException("该 OA 人员已绑定钉钉成员，无需同步人员；如需更换请先解绑");
    }
    if (isOaNoBind(cfgId, oaUserId)) {
        throw new RuntimeException("该 OA 人员已标记免绑定，请先取消免绑定后再同步人员");
    }
        String oaDeptId = OaonesUtil.s(oaUser.getString("departmentid"));
        if (oaDeptId.length() == 0 || "0".equals(oaDeptId)) {
            throw new RuntimeException("OA 人员未归属部门，无法按部门绑定同步人员");
        }
        JSONObject deptBind = deptBindByOa(cfgId, oaDeptId);
        if (deptBind == null) {
            throw new RuntimeException("OA 部门[id=" + oaDeptId + "]未绑定钉钉部门，"
                    + "请先在“部门绑定”页建立部门绑定后再同步人员");
        }
        String wxDeptId = OaonesUtil.s(deptBind.getString("wx_dept_id"));
        if (wxDeptId.length() == 0 || "0".equals(wxDeptId)) {
            throw new RuntimeException("OA 部门对应的钉钉部门无效，无法同步人员");
        }
        String name = OaonesUtil.s(oaUser.getString("lastname"));
        if (name.length() == 0) {
            throw new RuntimeException("OA 人员姓名为空，无法同步到钉钉");
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("钉钉租户未配置 AppKey");
        }
        String mobile = OaonesUtil.s(oaUser.getString("mobile"));
        String email = OaonesUtil.s(oaUser.getString("email"));
        DingTalkApi api = configService().getClient(cfgId);
        if (!wxDeptExists(api, wxDeptId)) {
            throw new RuntimeException("钉钉部门[id=" + wxDeptId + "]在钉钉侧已不存在，"
                    + "按约束不自动创建部门，无法同步人员");
        }
        String wxUserId = resolveWxUserId(corpId, oaUser);
        // OA → 钉钉为“多对一”：同一个钉钉成员可能已被其他 OA 人员绑定（多个 OA 账户对应同一个钉钉账户），
        // 这里继续按部门绑定关系把本 OA 人员同步到该钉钉成员并建立绑定，不再拦截。
        JSONArray deptArr = new JSONArray();
        deptArr.add(Long.parseLong(wxDeptId));
        JSONObject wxUser = new JSONObject();
        wxUser.put("userid", wxUserId);
        wxUser.put("name", name);
        wxUser.put("department", deptArr);
        if (mobile.length() > 0) {
            wxUser.put("mobile", mobile);
        }
        if (email.length() > 0) {
            wxUser.put("email", email);
        }
        boolean exists = api.userExists(wxUserId);
        if (exists) {
            api.updateUser(wxUser);
        } else {
            api.createUser(wxUser);
        }
        upsertSnapshot(corpId, wxUserId, name, wxDeptId, mobile, email);
        JSONObject bind = bindUser(cfgId, oaUserId, wxUserId, wxDeptId,
                OaonesConst.SYNC_BIND_REMARK, operatorId);
        markSynced(cfgId, oaUserId, OaonesConst.DIR_OA_TO_WX);
        JSONObject result = new JSONObject();
        result.put("cfg_id", cfgId);
        result.put("oa_user_id", oaUserId);
        result.put("oa_name", name);
        result.put("wx_user_id", wxUserId);
        result.put("wx_dept_id", wxDeptId);
        result.put("wx_created", !exists);
        result.put("bind", bind);
        OaonesLog.step("同步人员 OA->钉钉完成 oaUserId=" + oaUserId
                + " wxUserId=" + wxUserId + " created=" + !exists);
        return result;
    }

    @Override
    public JSONObject syncToWxBatch(JSONObject params, String operatorId) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        List<String> oaIds = splitCsv(params.getString("oa_ids"));
        if (cfgId.length() == 0) {
            throw new RuntimeException("请选择钉钉租户");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        if (oaIds.isEmpty()) {
            throw new RuntimeException("请先勾选需要同步人员的 OA 人员");
        }
        OaonesLog.step("批量同步人员 OA->钉钉 cfgId=" + cfgId + " total=" + oaIds.size());
        JSONArray successRows = new JSONArray();
        JSONArray failRows = new JSONArray();
        Set<String> seenOa = new HashSet<String>();
        int wxCreated = 0;
        for (String oaId : oaIds) {
            if (oaId.length() == 0 || !seenOa.add(oaId)) {
                continue;
            }
            JSONObject one = new JSONObject();
            one.put("cfg_id", cfgId);
            one.put("oa_user_id", oaId);
            try {
                JSONObject data = syncToWx(one, operatorId);
                boolean created = Boolean.TRUE.equals(data.get("wx_created"));
                if (created) {
                    wxCreated++;
                }
                JSONObject row = new JSONObject();
                row.put("oa_user_id", OaonesUtil.s(data.getString("oa_user_id")));
                row.put("oa_name", OaonesUtil.s(data.getString("oa_name")));
                row.put("oa_dept_name", oaUserLabel(oaId)[1]);
                row.put("wx_user_id", OaonesUtil.s(data.getString("wx_user_id")));
                row.put("wx_dept_id", OaonesUtil.s(data.getString("wx_dept_id")));
                row.put("wx_created", created);
                successRows.add(row);
            } catch (Exception e) {
                // 单人失败不打断整批：逐条记录原因，由页面弹框列表提示用户
                String[] label = oaUserLabel(oaId);
                failRows.add(toSyncFailRow(oaId, label[0], label[1], e.getMessage(), false));
            }
        }
        JSONObject result = new JSONObject();
        result.put("total", successRows.size() + failRows.size());
        result.put("success", successRows.size());
        result.put("fail", failRows.size());
        result.put("wx_created", wxCreated);
        result.put("wx_updated", successRows.size() - wxCreated);
        result.put("success_rows", successRows);
        result.put("fail_rows", failRows);
        OaonesLog.step("批量同步人员 OA->钉钉完成 cfgId=" + cfgId
                + " success=" + successRows.size() + " fail=" + failRows.size());
        return result;
    }

    /**
     * 批量同步失败明细：带上被同步人员的姓名/所在部门，并把后端异常信息转成页面可读的失败原因
     * （例如“所在部门没有进行绑定，请手动创建人员或先进行部门绑定”）。
     *
     * @param toOa true 表示“钉钉 → OA”方向（B 页），false 表示“OA → 钉钉”方向（A 页）
     */
    private JSONObject toSyncFailRow(String userId, String name, String deptName, String message,
                                     boolean toOa) {
        String raw = OaonesUtil.s(message);
        if (raw.length() == 0) {
            raw = "同步人员失败，请稍后重试";
        }
        JSONObject row = new JSONObject();
        row.put("sync_direction", toOa ? "WX_TO_OA" : "OA_TO_WX");
        row.put("reason_code", syncFailCode(raw, toOa));
        row.put("reason", syncFailReason(raw, toOa));
        row.put("raw_reason", raw);
        row.put("user_id", OaonesUtil.s(userId));
        row.put("user_name", OaonesUtil.s(name));
        row.put("user_dept_name", OaonesUtil.s(deptName));
        // 同步补充方向专属字段，便于按页面对照
        if (toOa) {
            row.put("wx_user_id", OaonesUtil.s(userId));
            row.put("wx_name", OaonesUtil.s(name));
            row.put("wx_dept_name", OaonesUtil.s(deptName));
        } else {
            row.put("oa_user_id", OaonesUtil.s(userId));
            row.put("oa_name", OaonesUtil.s(name));
            row.put("oa_dept_name", OaonesUtil.s(deptName));
        }
        return row;
    }

    /** 同步失败原因分类（页面可按编码做差异化提示）。 */
    private String syncFailCode(String message, boolean toOa) {
        if (message.contains("未绑定钉钉部门") || message.contains("未绑定 OA 部门")) {
            return "dept_unbound";
        }
        if (message.contains("未归属部门")) {
            return "dept_missing";
        }
        if (message.contains("已绑定钉钉成员")) {
            return "already_bound";
        }
        if (message.contains("已绑定 OA 人员")) {
            return "already_bound";
        }
        if (message.contains("已不存在")) {
            return "wx_dept_missing";
        }
        if (message.contains("OA 部门") && message.contains("不存在或已删除")) {
            return "oa_dept_missing";
        }
        if (message.contains("已封存")) {
            return "oa_dept_canceled";
        }
        if (message.contains("不在人员快照中")) {
            return "wx_snapshot_missing";
        }
        if (message.contains("姓名为空")) {
            return "name_empty";
        }
        if (message.contains("不存在或已离职")) {
            return "oa_user_missing";
        }
        if (message.contains("OA 人员") && message.contains("免绑定")) {
            return "oa_no_bind";
        }
        if (message.contains("免绑定")) {
            return "no_bind";
        }
        if (message.contains("已存在登录名")) {
            return "oa_loginid_taken";
        }
        if (message.contains("无法作为 OA 登录名")) {
            return "wx_loginid_invalid";
        }
        if (message.contains("生成钉钉成员ID")) {
            return "wx_user_id_missing";
        }
        if (message.contains("租户配置不存在")) {
            return "cfg_missing";
        }
        return "other";
    }

    /** 同步失败原因转页面可读文案；无法识别时回退后端原始信息。 */
    private String syncFailReason(String message, boolean toOa) {
        String code = syncFailCode(message, toOa);
        if ("dept_unbound".equals(code)) {
            return "所在部门没有进行绑定，请手动创建人员或先进行部门绑定。";
        }
        if ("dept_missing".equals(code)) {
            return toOa
                    ? "该钉钉成员未归属部门，无法按部门绑定同步，请先在钉钉侧维护成员部门并完成部门绑定。"
                    : "该人员未归属部门，无法按部门绑定同步，请先维护 OA 人员部门并完成部门绑定。";
        }
        if ("already_bound".equals(code)) {
            return toOa
                    ? "该钉钉成员已绑定 OA 人员，无需重复同步人员。"
                    : "该人员已绑定钉钉成员，无需重复同步人员。";
        }
        if ("wx_dept_missing".equals(code)) {
            return "对应的钉钉部门在钉钉侧已不存在，请先重新建立部门绑定后再同步人员。";
        }
        if ("oa_dept_missing".equals(code)) {
            return "对应的 OA 部门不存在或已删除，请先在“部门绑定”页重新建立部门绑定后再同步人员。";
        }
        if ("oa_dept_canceled".equals(code)) {
            return "对应的 OA 部门已封存，请先在 OA 侧解封部门或调整部门绑定后再同步人员。";
        }
        if ("wx_snapshot_missing".equals(code)) {
            return "该钉钉成员不在人员快照中或状态异常，请先点击“更新钉钉人员快照”后重试。";
        }
        if ("name_empty".equals(code)) {
            return toOa
                    ? "该钉钉成员姓名为空，请先在钉钉侧补全成员姓名后再同步人员。"
                    : "该人员姓名为空，请先补全 OA 人员姓名后再同步人员。";
        }
        if ("oa_user_missing".equals(code)) {
            return "该 OA 人员不存在或已离职/封存，请先在 OA 侧确认人员状态或取消勾选。";
        }
    if ("no_bind".equals(code)) {
        return "对应的钉钉成员账号已标记免绑定，请先在“钉钉数据基准”页取消免绑定，"
            + "或调整该人员的 OA 登录名/工号后重试。";
    }
    if ("oa_no_bind".equals(code)) {
        return "该 OA 人员已标记免绑定，请先在“OA数据基准”页取消免绑定后再同步人员。";
    }
        if ("oa_loginid_taken".equals(code)) {
            return appendPeriod(message);
        }
        if ("wx_loginid_invalid".equals(code)) {
            return appendPeriod(message);
        }
        if ("wx_user_id_missing".equals(code)) {
            return "无法根据该人员生成钉钉成员ID，请先补全 OA 登录名或工号后再同步人员。";
        }
        return appendPeriod(message);
    }

    private String appendPeriod(String message) {
        String text = OaonesUtil.s(message);
        if (text.length() == 0) {
            return "同步人员失败，请稍后重试。";
        }
        char last = text.charAt(text.length() - 1);
        if (last == '。' || last == '！' || last == '？' || last == '；' || last == '.') {
            return text;
        }
        return text + "。";
    }

    /** OA 人员姓名与所在部门名称（取不到返回空串，页面显示为“—”）。 */
    private String[] oaUserLabel(String oaUserId) {
        try {
            JSONObject oaUser = oaService().getUser(oaUserId);
            if (oaUser != null) {
                return new String[]{OaonesUtil.s(oaUser.getString("lastname")),
                        OaonesUtil.s(oaUser.getString("departmentname"))};
            }
        } catch (Exception ignore) {
            // 人员信息缺失不影响同步结果展示
        }
        return new String[]{"", ""};
    }

    /** 钉钉成员姓名与所在部门名称（取不到返回空串，页面显示为“—”）。 */
    private String[] wxUserLabel(String cfgId, String wxUserId) {
        try {
            JSONObject row = snapshotUser(cfgId, wxUserId);
            if (row != null) {
                return new String[]{OaonesUtil.s(row.getString("wx_name")),
                        OaonesUtil.s(row.getString("wx_dept_name"))};
            }
        } catch (Exception ignore) {
            // 人员信息缺失不影响同步结果展示
        }
        return new String[]{"", ""};
    }

    @Override
    public JSONObject syncToOa(JSONObject params, String operatorId) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        String wxUserId = OaonesUtil.s(params.getString("wx_user_id"));
        OaonesLog.step("同步人员 钉钉->OA cfgId=" + cfgId + " wxUserId=" + wxUserId);
        if (cfgId.length() == 0 || wxUserId.length() == 0) {
            throw new RuntimeException("请选择钉钉租户与钉钉成员");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        JSONObject wxRow = snapshotUser(cfgId, wxUserId);
        if (wxRow == null) {
            throw new RuntimeException("钉钉成员 " + wxUserId
                    + " 不在人员快照中或状态异常，请先“更新钉钉人员快照”");
        }
        if ("0".equals(OaonesUtil.s(wxRow.getString("no_bind")))) {
            throw new RuntimeException("钉钉成员 " + wxUserId
                    + " 已标记免绑定，请先在“钉钉数据基准”页取消免绑定后再同步人员");
        }
        Integer bound = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.findActiveIdByWxUser(table(), OaonesUtil.i(cfgId, 0), wxUserId));
        if (bound != null) {
            throw new RuntimeException("该钉钉成员已绑定 OA 人员，无需同步人员；如需更换请先解绑");
        }
        String wxDeptId = OaonesUtil.s(wxRow.getString("wx_dept_id"));
        if (wxDeptId.length() == 0 || "0".equals(wxDeptId)) {
            throw new RuntimeException("钉钉成员未归属部门，无法按部门绑定同步人员");
        }
        JSONObject deptBind = deptBindByWx(cfgId, wxDeptId);
        if (deptBind == null) {
            throw new RuntimeException("钉钉部门[id=" + wxDeptId + "]未绑定 OA 部门，"
                    + "请先在“部门绑定”页建立部门绑定后再同步人员");
        }
        String oaDeptId = OaonesUtil.s(deptBind.getString("oa_dept_id"));
        JSONObject oaDept = oaService().getDepartment(oaDeptId);
        if (oaDept == null) {
            throw new RuntimeException("对应的 OA 部门[id=" + oaDeptId + "]不存在或已删除，无法同步人员");
        }
        if ("1".equals(OaonesUtil.s(oaDept.getString("canceled")))) {
            throw new RuntimeException("对应的 OA 部门[id=" + oaDeptId + "]已封存，无法同步人员");
        }
        String name = OaonesUtil.s(wxRow.getString("wx_name"));
        if (name.length() == 0) {
            throw new RuntimeException("钉钉成员姓名为空，无法同步到 OA");
        }
        String loginid = resolveOaLoginId(wxUserId);
        if (oaService().findUserIdByLoginId(loginid) != null) {
            throw new RuntimeException("OA 已存在登录名为 " + loginid + " 的账号，无法自动建号；"
                    + "请先在“OA 数据基准”页把该 OA 人员绑定到本钉钉成员");
        }
        int newOaId = oaService().createUser(loginid, name,
                OaonesUtil.s(wxRow.getString("mobile")), OaonesUtil.s(wxRow.getString("email")),
                oaDeptId, OaonesUtil.s(oaDept.getString("subcompanyid1")), null, operatorId);
        JSONObject bind = bindUser(cfgId, String.valueOf(newOaId), wxUserId, wxDeptId,
                OaonesConst.SYNC_BIND_REMARK, operatorId);
        markSynced(cfgId, String.valueOf(newOaId), OaonesConst.DIR_WX_TO_OA);
        JSONObject result = new JSONObject();
        result.put("cfg_id", cfgId);
        result.put("oa_user_id", String.valueOf(newOaId));
        result.put("oa_loginid", loginid);
        result.put("oa_name", name);
        result.put("wx_user_id", wxUserId);
        result.put("wx_dept_id", wxDeptId);
        result.put("bind", bind);
        OaonesLog.step("同步人员 钉钉->OA 完成 wxUserId=" + wxUserId + " newOaUserId=" + newOaId);
        return result;
    }

    @Override
    public JSONObject syncToOaBatch(JSONObject params, String operatorId) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        List<String> wxIds = splitCsv(params.getString("wx_ids"));
        if (cfgId.length() == 0) {
            throw new RuntimeException("请选择钉钉租户");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        if (wxIds.isEmpty()) {
            throw new RuntimeException("请先勾选需要同步人员的钉钉成员");
        }
        OaonesLog.step("批量同步人员 钉钉->OA cfgId=" + cfgId + " total=" + wxIds.size());
        JSONArray successRows = new JSONArray();
        JSONArray failRows = new JSONArray();
        Set<String> seenWx = new HashSet<String>();
        for (String wxId : wxIds) {
            if (wxId.length() == 0 || !seenWx.add(wxId)) {
                continue;
            }
            JSONObject one = new JSONObject();
            one.put("cfg_id", cfgId);
            one.put("wx_user_id", wxId);
            try {
                JSONObject data = syncToOa(one, operatorId);
                String[] label = wxUserLabel(cfgId, wxId);
                JSONObject row = new JSONObject();
                row.put("wx_user_id", OaonesUtil.s(data.getString("wx_user_id")));
                row.put("wx_name", label[0].length() > 0
                        ? label[0] : OaonesUtil.s(data.getString("oa_name")));
                row.put("wx_dept_name", label[1]);
                row.put("oa_user_id", OaonesUtil.s(data.getString("oa_user_id")));
                row.put("oa_loginid", OaonesUtil.s(data.getString("oa_loginid")));
                row.put("oa_name", OaonesUtil.s(data.getString("oa_name")));
                row.put("oa_created", true);
                successRows.add(row);
            } catch (Exception e) {
                // 单人失败不打断整批：逐条记录原因，由页面弹框列表提示用户
                String[] label = wxUserLabel(cfgId, wxId);
                failRows.add(toSyncFailRow(wxId, label[0], label[1], e.getMessage(), true));
            }
        }
        JSONObject result = new JSONObject();
        result.put("total", successRows.size() + failRows.size());
        result.put("success", successRows.size());
        result.put("fail", failRows.size());
        result.put("oa_created", successRows.size());
        result.put("success_rows", successRows);
        result.put("fail_rows", failRows);
        OaonesLog.step("批量同步人员 钉钉->OA 完成 cfgId=" + cfgId
                + " success=" + successRows.size() + " fail=" + failRows.size());
        return result;
    }

    /** 建立/更新人员绑定（“同步人员”专用：固定为系统绑定并写入同步原因）。 */
    private JSONObject bindUser(String cfgId, String oaUserId, String wxUserId, String wxDeptId,
                                String remark, String operatorId) {
        String now = OaonesUtil.now();
        int modeId = OaonesProps.userBindModeId();
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int oaKey = OaonesUtil.i(oaUserId, 0);
        Integer existId = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.findIdByOaUser(table(), cfgKey, oaKey));
        if (existId != null) {
            UserBind entity = new UserBind();
            entity.setId(existId);
            entity.setXUserId(wxUserId);
        entity.setWxDeptId(OaonesUtil.i(wxDeptId, 0));
        entity.setBindState(1);
        entity.setBindSource(OaonesConst.BIND_SOURCE_SYSTEM);
        entity.setRemark(remark);
        entity.setBindTime(now);
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
            entity.setModedatamodifydatetime(now);
            OaonesMyBatis.call(UserBindMapper.class, mapper -> {
                if (mapper.update(table(), entity) <= 0) {
                    throw new RuntimeException("人员绑定更新失败");
                }
                return 1;
            });
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(existId));
            return find(String.valueOf(existId));
        }
        UserBind entity = new UserBind();
        entity.setCfgId(cfgKey);
        entity.setOaUserId(oaKey);
        entity.setXUserId(wxUserId);
        entity.setWxDeptId(OaonesUtil.i(wxDeptId, 0));
        entity.setBindState(1);
        entity.setBindSource(OaonesConst.BIND_SOURCE_SYSTEM);
        entity.setRemark(remark);
        entity.setBindTime(now);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(now);
        OaonesMyBatis.call(UserBindMapper.class, mapper -> {
            if (mapper.insert(table(), entity) <= 0) {
                throw new RuntimeException("人员绑定保存失败");
            }
            return 1;
        });
        Integer newId = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.findIdByOaUser(table(), cfgKey, oaKey));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operatorId);
        return find(String.valueOf(newId));
    }

    /** 部门绑定：按 OA 部门查（A 页“OA 数据基准”判断部门是否已绑定）。 */
    private JSONObject deptBindByOa(String cfgId, String oaDeptId) {
        JSONArray rows = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (oaDeptId.equals(OaonesUtil.s(row.getString("oa_dept_id")))) {
                return row;
            }
        }
        return null;
    }

    /** 部门绑定：按钉钉部门查（B 页“钉钉数据基准”判断部门是否已绑定）。 */
    private JSONObject deptBindByWx(String cfgId, String wxDeptId) {
        JSONArray rows = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (wxDeptId.equals(OaonesUtil.s(row.getString("wx_dept_id")))) {
                return row;
            }
        }
        return null;
    }

    /** 人员快照中的单个成员（读取正常状态快照）。 */
    private JSONObject snapshotUser(String cfgId, String wxUserId) throws Exception {
        JSONArray rows = userTmpService().listNormal(cfgId);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (wxUserId.equals(OaonesUtil.s(row.getString("wx_user_id")))) {
                return row;
            }
        }
        return null;
    }

    /**
     * OA -> 钉钉成员ID：优先 OA 登录名，其次工号，最后兜底 oa+人员ID；
     * 生成的 userid 若已被标记免绑定则直接拒绝。
     */
    private String resolveWxUserId(String corpId, JSONObject oaUser) throws Exception {
        String oaUserId = OaonesUtil.s(oaUser.getString("id"));
        String[] candidates = new String[]{
                OaonesUtil.s(oaUser.getString("loginid")),
                OaonesUtil.s(oaUser.getString("workcode")),
                "oa" + oaUserId
        };
        for (int i = 0; i < candidates.length; i++) {
            String candidate = sanitizeUserId(candidates[i]);
            if (candidate.length() == 0) {
                continue;
            }
            if (userTmpService().isNoBind(corpId, candidate)) {
                throw new RuntimeException("钉钉成员 " + candidate + " 已标记免绑定，"
                        + "请先在“钉钉数据基准”页取消免绑定后再同步人员");
            }
            return candidate;
        }
        throw new RuntimeException("无法根据 OA 人员生成钉钉成员ID，请检查 OA 登录名/工号是否为空");
    }

    /** 钉钉 -> OA 登录名：使用钉钉成员ID（钉钉 userid 与 OA 账号口径一致）。 */
    private String resolveOaLoginId(String wxUserId) {
        String loginid = sanitizeUserId(wxUserId);
        if (loginid.length() == 0) {
            throw new RuntimeException("钉钉成员ID " + wxUserId + " 无法作为 OA 登录名，无法自动建号");
        }
        return loginid;
    }

    /** 账号ID归一化：只保留钉钉/OA 账号允许的字符（字母数字与 _ - . @）。 */
    private String sanitizeUserId(String value) {
        String source = OaonesUtil.s(value).trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length() && sb.length() < 64; i++) {
            char c = source.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '@';
            if (keep) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 钉钉部门是否仍存在（按部门接口实时查询）。 */
    private boolean wxDeptExists(DingTalkApi api, String wxDeptId) throws Exception {
        JSONArray depts = api.listDepartments();
        for (int i = 0; i < depts.size(); i++) {
            JSONObject dept = depts.getJSONObject(i);
            if (wxDeptId.equals(String.valueOf(dept.getLongValue("id")))) {
                return true;
            }
        }
        return false;
    }

    /** 把“同步人员”新建/更新的钉钉成员回写人员快照，便于“钉钉数据基准”页立即看到。 */
    private void upsertSnapshot(String corpId, String wxUserId, String name, String wxDeptId,
                                String mobile, String email) {
        String tmpTable = OaonesProps.userTmpTable();
        String now = OaonesUtil.now();
        UserTmp entity = new UserTmp();
        entity.setCorpid(corpId);
        entity.setWxUserId(wxUserId);
        entity.setWxName(name);
        entity.setWxDeptId(OaonesUtil.i(wxDeptId, 0));
        entity.setWxPosition("");
        entity.setWxMobile(OaonesUtil.s(mobile));
        entity.setWxEmail(OaonesUtil.s(email));
        entity.setWxBizMail("");
        entity.setWxAvatar("");
        entity.setWxTelephone("");
        entity.setStatus(OaonesConst.STATUS_NORMAL);
        entity.setType(OaonesConst.TYPE_DINGTALK);
        entity.setLastSyncTime(now);
        entity.setRemark("");
        entity.setFormmodeid(OaonesProps.userTmpModeId());
        String exists = OaonesMyBatis.call(UserTmpMapper.class, mapper ->
                mapper.findWxUserId(tmpTable, corpId, wxUserId, OaonesConst.TYPE_DINGTALK));
        if (exists != null) {
            OaonesMyBatis.call(UserTmpMapper.class,
                    mapper -> mapper.updateUser(tmpTable, entity));
        } else {
            OaonesMyBatis.call(UserTmpMapper.class,
                    mapper -> mapper.insertUser(tmpTable, entity));
        }
    }

    /** 绑定详情列表行转前端 JSON。 */
    private JSONObject toBoundJson(UserBindRow row) {
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("cfg_name", OaonesUtil.nullToEmpty(row.getCfgName()));
        json.put("cfg_corp_id", OaonesUtil.nullToEmpty(row.getCfgCorpId()));
        json.put("oa_user_id", OaonesUtil.nullToEmpty(row.getOaUserId()));
        json.put("oa_name", OaonesUtil.nullToEmpty(row.getOaName()));
        json.put("oa_loginid", OaonesUtil.nullToEmpty(row.getLoginid()));
        json.put("oa_mobile", OaonesUtil.nullToEmpty(row.getOaMobile()));
        json.put("oa_email", OaonesUtil.nullToEmpty(row.getOaEmail()));
        json.put("oa_workcode", OaonesUtil.nullToEmpty(row.getWorkcode()));
        json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getOaDeptId()));
        json.put("oa_dept_name", OaonesUtil.nullToEmpty(row.getOaDeptName()));
        json.put("wx_user_id", OaonesUtil.nullToEmpty(row.getWxUserId()));
        json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
        json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
        json.put("bind_state", OaonesUtil.nullToEmpty(row.getBindState()));
        json.put("bind_source", OaonesUtil.nullToEmpty(row.getBindSource()));
        json.put("bind_time", OaonesUtil.nullToEmpty(row.getBindTime()));
        json.put("last_sync_direction", OaonesUtil.nullToEmpty(row.getLastSyncDirection()));
        json.put("last_sync_time", OaonesUtil.nullToEmpty(row.getLastSyncTime()));
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        return json;
    }

    private String defaultBindReason(String ruleCsv) {
        String normalized = UserRuleService.normalizeRules(ruleCsv);
        StringBuilder sb = new StringBuilder("默认规则自动绑定");
        if (normalized.length() > 0) {
            sb.append('（');
            boolean first = true;
            for (String rule : normalized.split(",")) {
                if (!first) {
                    sb.append('、');
                }
                sb.append(OaonesRules.label(rule));
                first = false;
            }
            sb.append('）');
        }
        return sb.toString();
    }

    private List<String> splitCsv(String value) {
        List<String> list = new ArrayList<String>();
        if (value == null || value.trim().length() == 0) {
            return list;
        }
        for (String token : value.split(",")) {
            String v = token == null ? "" : token.trim();
            if (v.length() > 0) {
                list.add(v);
            }
        }
        return list;
    }

    private Set<String> splitCsvToSet(String value) {
        return new HashSet<String>(splitCsv(value));
    }

    /** 手机号归一化：仅保留数字并去掉常见国际区号前缀。 */
    private String normMobile(String value) {
        String source = OaonesUtil.s(value);
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        String mobile = digits.toString();
        if (mobile.startsWith("0086")) {
            mobile = mobile.substring(4);
        } else if (mobile.startsWith("86") && mobile.length() > 11) {
            mobile = mobile.substring(2);
        }
        return mobile;
    }

    /** 按匹配字段取值并归一化（wx 侧为 true 时读取钉钉成员行字段）。 */
    private String ruleValue(String rule, JSONObject row, boolean isWx) {
        if ("email".equals(rule)) {
            return normEmail(OaonesUtil.s(row.getString("email")));
        }
        if ("mobile".equals(rule)) {
            return normMobile(OaonesUtil.s(row.getString("mobile")));
        }
        if ("name".equals(rule)) {
            String name = isWx
                    ? OaonesUtil.s(row.getString("wx_name"))
                    : OaonesUtil.s(row.getString("lastname"));
            return normName(name);
        }
        if ("workcode".equals(rule)) {
            String workcode;
            if (isWx) {
                workcode = OaonesUtil.s(row.getString("workcode"));
                if (workcode.length() == 0) {
                    workcode = OaonesUtil.s(row.getString("wx_user_id"));
                }
            } else {
                workcode = OaonesUtil.s(row.getString("workcode"));
            }
            return workcode.trim().toLowerCase();
        }
        return "";
    }

    /** 姓名归一化：小写并去掉首尾/内部空白、全角空格、零宽字符等不可见差异。 */
    private String normName(String value) {
        return normText(value);
    }

    /** 邮箱归一化：小写并去掉首尾/内部空白、全角空格、零宽字符等不可见差异。 */
    private String normEmail(String value) {
        return normText(value);
    }

    /** 姓名/邮箱共用的文本归一化：去掉空白与零宽字符后转小写。 */
    private String normText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || c == 0x200B || c == 0x200C || c == 0x200D || c == 0xFEFF) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private Set<String> boundOaUserIds(String cfgId) {
        Set<String> ids = new HashSet<String>();
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        List<String> rows = OaonesMyBatis.call(UserBindMapper.class,
                mapper -> mapper.listBoundOaUserIds(params));
        if (rows != null) {
            for (String row : rows) {
                ids.add(OaonesUtil.s(row));
            }
        }
        return ids;
    }

private Set<String> boundWxUserIds(String cfgId) {
    Set<String> ids = new HashSet<String>();
    Map<String, Object> params = OaonesParam.of();
    params.put("cfgId", OaonesUtil.i(cfgId, 0));
    List<String> rows = OaonesMyBatis.call(UserBindMapper.class,
            mapper -> mapper.listBoundWxUserIds(params));
    if (rows != null) {
        for (String row : rows) {
            ids.add(OaonesUtil.s(row));
        }
    }
    return ids;
}

/** 已标记免绑定的 OA 人员ID集合（bind_state=0；已绑定为 1，待绑定不落库）。 */
private Set<String> noBindOaUserIds(String cfgId) {
    Set<String> ids = new HashSet<String>();
    List<String> rows = OaonesMyBatis.call(UserBindMapper.class,
            mapper -> mapper.listNoBindOaUserIds(table(), OaonesUtil.i(cfgId, 0)));
    if (rows != null) {
        for (String row : rows) {
            ids.add(OaonesUtil.s(row));
        }
    }
    return ids;
}

/** OA 人员与钉钉成员之间是否已存在绑定（用于免绑定标记的兼容判断）。 */
private boolean isBoundPair(String cfgId, String oaUserId, String wxUserId) {
    Integer found = OaonesMyBatis.call(UserBindMapper.class, mapper -> mapper.findPairId(
            table(), OaonesUtil.i(cfgId, 0), OaonesUtil.i(oaUserId, 0), wxUserId));
    return found != null;
}

/** 该 OA 人员是否已标记免绑定（A 页 OA 数据基准的“免绑定”）。 */
private boolean isOaNoBind(String cfgId, String oaUserId) {
    Integer found = OaonesMyBatis.call(UserBindMapper.class, mapper -> mapper.findNoBindIdByOaUser(
            table(), OaonesUtil.i(cfgId, 0), OaonesUtil.i(oaUserId, 0)));
    return found != null;
}

    private JSONObject find(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            return null;
        }
        UserBind row = OaonesMyBatis.call(UserBindMapper.class, mapper -> mapper.findById(table(), id));
        if (row == null) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("oa_user_id", OaonesUtil.nullToEmpty(row.getOaUserId()));
        json.put("wx_user_id", OaonesUtil.nullToEmpty(row.getXUserId()));
        json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
        json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
        json.put("bind_state", OaonesUtil.nullToEmpty(row.getBindState()));
        json.put("bind_source", OaonesUtil.nullToEmpty(row.getBindSource()));
        json.put("bind_time", OaonesUtil.nullToEmpty(row.getBindTime()));
        json.put("last_sync_direction", OaonesUtil.nullToEmpty(row.getLastSyncDirection()));
        json.put("last_sync_time", OaonesUtil.nullToEmpty(row.getLastSyncTime()));
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        return json;
    }
}
