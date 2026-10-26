package com.customization.oaones.appsyn.service.impl.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.wecom.DeptBindMapper;
import com.customization.oaones.appsyn.dto.wecom.DeptBindRow;
import com.customization.oaones.appsyn.entity.wecom.DeptBind;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.wecom.ConfigService;
import com.customization.oaones.appsyn.service.wecom.DeptBindService;
import com.customization.oaones.appsyn.service.wecom.DeptTmpService;
import com.customization.oaones.appsyn.service.OaService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部门绑定维护实现（DAO 层为 {@link DeptBindMapper}）。
 */
public class DeptBindServiceImpl implements DeptBindService {

    private String table() {
        return OaonesProps.deptBindTable();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    private DeptTmpService deptTmpService() {
        return new DeptTmpServiceImpl();
    }

    @Override
    public JSONArray list(String cfgId, String keyword) {
        OaonesLog.step("查询部门绑定列表 cfgId=" + cfgId + " keyword=" + keyword);
        JSONArray result = new JSONArray();
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        String k = keyword == null ? "" : keyword.trim();
        params.put("keyword", k.length() == 0 ? "" : "%" + k + "%");
        List<DeptBindRow> rows = OaonesMyBatis.call(DeptBindMapper.class, mapper -> mapper.listRows(params));
        if (rows == null) {
            return result;
        }
        Set<String> defaultMarked = new HashSet<String>();
        for (DeptBindRow row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
            json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getOaDeptId()));
            json.put("oa_dept_name", OaonesUtil.nullToEmpty(row.getOaDeptName()));
            json.put("oa_dept_parent", OaonesUtil.nullToEmpty(row.getOaDeptParent()));
            json.put("oa_subcompany_id", OaonesUtil.nullToEmpty(row.getOaSubcompanyId()));
            json.put("oa_subcompany_name", OaonesUtil.nullToEmpty(row.getOaSubcompanyName()));
            json.put("oa_canceled", OaonesUtil.nullToEmpty(row.getOaCanceled()));
            json.put("wx_tmp_status", OaonesUtil.nullToEmpty(row.getWxTmpStatus()));
            json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("wx_parent_dept_id", OaonesUtil.nullToEmpty(row.getWxParentDeptId()));
            json.put("bind_state", OaonesUtil.nullToEmpty(row.getBindState()));
            String createDate = OaonesUtil.s(row.getModedatacreatedate());
            String createTime = OaonesUtil.s(row.getModedatacreatetime());
            json.put("bind_time", createDate.length() == 0 && createTime.length() == 0
                    ? "" : createDate + " " + createTime);
            json.put("update_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
            json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
            json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
            json.put("corp_name", OaonesUtil.nullToEmpty(row.getCfgName()));
            json.put("corp_id", OaonesUtil.nullToEmpty(row.getCfgCorpId()));
            String oaKey = String.valueOf(row.getCfgId()) + ":" + String.valueOf(row.getOaDeptId());
            int syncDefault = "1".equals(OaonesUtil.nullToEmpty(row.getBindState()))
                    && defaultMarked.add(oaKey) ? 1 : 0;
            json.put("sync_default", syncDefault);
            result.add(json);
        }
        return result;
    }

    @Override
    public JSONArray listEnabled(String cfgId) {
        OaonesLog.step("查询启用中的部门绑定 cfgId=" + cfgId);
        JSONArray result = new JSONArray();
        Integer cfg = OaonesUtil.integer(cfgId);
        List<DeptBind> rows = OaonesMyBatis.call(DeptBindMapper.class,
                mapper -> mapper.listEnabled(table(), cfg == null ? 0 : cfg));
        if (rows == null) {
            return result;
        }
        Set<String> defaultMarked = new HashSet<String>();
        for (DeptBind row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
            json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getOaDeptId()));
            json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("wx_parent_dept_id", OaonesUtil.nullToEmpty(row.getWxParentDeptId()));
            json.put("sync_default", defaultMarked.add(String.valueOf(row.getOaDeptId())) ? 1 : 0);
            result.add(json);
        }
        return result;
    }

    @Override
    public Set<String> boundOaDeptIds(String cfgId) {
        Set<String> ids = new HashSet<String>();
        JSONArray rows = listEnabled(cfgId);
        for (int i = 0; i < rows.size(); i++) {
            ids.add(OaonesUtil.s(rows.getJSONObject(i).getString("oa_dept_id")));
        }
        return ids;
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        OaonesLog.step("保存部门绑定 cfgId=" + OaonesUtil.s(params.getString("cfg_id"))
                + " oaDeptId=" + OaonesUtil.s(params.getString("oa_dept_id"))
                + " wxDeptId=" + OaonesUtil.s(params.getString("wx_dept_id")));
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        String oaDeptId = OaonesUtil.s(params.getString("oa_dept_id"));
        String wxDeptId = OaonesUtil.s(params.getString("wx_dept_id"));
        String wxParentDeptId = OaonesUtil.s(params.getString("wx_parent_dept_id"));
        String remark = OaonesUtil.s(params.getString("remark"));
        if (cfgId.length() == 0 || oaDeptId.length() == 0) {
            throw new RuntimeException("请选择企业微信租户与 OA 部门");
        }
        if (wxDeptId.length() == 0 || "0".equals(wxDeptId)) {
            throw new RuntimeException("企业微信部门尚不存在时不允许建立绑定，也不会自动创建企微部门");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("企微租户配置不存在");
        }
        JSONObject oaDept = oaService().getDepartment(oaDeptId);
        if (oaDept == null) {
            throw new RuntimeException("OA 部门不存在");
        }
        if ("1".equals(OaonesUtil.s(oaDept.getString("canceled")))) {
            throw new RuntimeException("OA 部门已封存，不允许建立/更换绑定");
        }
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int oaKey = OaonesUtil.i(oaDeptId, 0);
        int wxKey = OaonesUtil.i(wxDeptId, 0);
        String now = OaonesUtil.now();
        int modeId = OaonesProps.deptBindModeId();
        // 同一 OA 部门可以映射同租户的多个企微部门；同一 pair 原地更新，新部门新增一条。
        // 人员同步始终取启用绑定中 id 最小的一条作为默认映射。
        Integer existId = OaonesMyBatis.call(DeptBindMapper.class,
                mapper -> mapper.findIdByPair(table(), cfgKey, oaKey, wxKey));
        if (existId != null) {
            DeptBind entity = new DeptBind();
            entity.setId(existId);
            entity.setWxDeptId(wxKey);
            entity.setWxParentDeptId(OaonesUtil.i(wxParentDeptId, 0));
            entity.setYx(1);
            entity.setRemark(remark);
            entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
            entity.setModedatamodifydatetime(now);
            OaonesMyBatis.call(DeptBindMapper.class, mapper -> {
                if (mapper.update(table(), entity) <= 0) {
                    throw new RuntimeException("部门绑定更新失败");
                }
                return 1;
            });
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(existId));
            return find(String.valueOf(existId));
        }
        DeptBind entity = new DeptBind();
        entity.setCfgId(cfgKey);
        entity.setOaDeptId(oaKey);
        entity.setWxDeptId(wxKey);
        entity.setWxParentDeptId(OaonesUtil.i(wxParentDeptId, 0));
        entity.setYx(1);
        entity.setRemark(remark);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(now);
        OaonesMyBatis.call(DeptBindMapper.class, mapper -> {
            if (mapper.insert(table(), entity) <= 0) {
                throw new RuntimeException("部门绑定保存失败");
            }
            return 1;
        });
        Integer newId = OaonesMyBatis.call(DeptBindMapper.class,
                mapper -> mapper.findIdByPair(table(), cfgKey, oaKey, wxKey));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operatorId);
        return find(String.valueOf(newId));
    }

    @Override
    public JSONObject autoBatchSave(JSONObject params, String operatorId) {
        return doBatchSave(params, operatorId);
    }

    @Override
    public JSONObject batchSave(JSONObject params, String operatorId) {
        return doBatchSave(params, operatorId);
    }

    /** 批量绑定部门（手动绑定与按编号/名称匹配的自动绑定使用同一套规则）。 */
    private JSONObject doBatchSave(JSONObject params, String operatorId) {
        OaonesLog.step("批量绑定部门 cfgId=" + OaonesUtil.s(params.getString("cfg_id"))
                + " oaIds=" + OaonesUtil.s(params.getString("oa_ids"))
                + " wxIds=" + OaonesUtil.s(params.getString("wx_ids")));
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        String[] oaIds = splitCsv(params.getString("oa_ids"));
        String[] wxIds = splitCsv(params.getString("wx_ids"));
        String[] wxParents = splitCsv(params.getString("wx_parent_ids"));
        if (cfgId.length() == 0) {
            throw new RuntimeException("缺少企业微信租户");
        }
        if (oaIds.length == 0 || oaIds.length != wxIds.length || wxParents.length != wxIds.length) {
            throw new RuntimeException("批量绑定参数不完整");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("企微租户配置不存在");
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("企微租户未配置 corpid");
        }
        for (int i = 0; i < oaIds.length; i++) {
            String oaId = oaIds[i].trim();
            String wxId = wxIds[i].trim();
            if (oaId.length() == 0 || wxId.length() == 0 || "0".equals(wxId)) {
                throw new RuntimeException("第 " + (i + 1) + " 条 OA部门/企微部门未选择完整");
            }
            JSONObject oaDept = oaService().getDepartment(oaId);
            if (oaDept == null) {
                throw new RuntimeException("OA 部门ID " + oaId + " 不存在");
            }
            if ("1".equals(OaonesUtil.s(oaDept.getString("canceled")))) {
                throw new RuntimeException("OA 部门ID " + oaId + " 已封存，不允许绑定");
            }
            try {
                if (!deptTmpService().isNormal(corpId, wxId)) {
                    throw new RuntimeException("企微部门ID " + wxId
                            + " 不在部门快照中或状态异常（已封存/已删除），不允许绑定");
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("校验企微部门快照失败: " + e.getMessage());
            }
        }
        List<JSONObject> items = new ArrayList<JSONObject>();
        for (int i = 0; i < oaIds.length; i++) {
            JSONObject item = new JSONObject();
            item.put("cfg_id", cfgId);
            item.put("oa_dept_id", oaIds[i].trim());
            item.put("wx_dept_id", wxIds[i].trim());
            item.put("wx_parent_dept_id", wxParents[i].trim());
            item.put("remark", "");
            items.add(item);
        }
        int success = 0;
        for (JSONObject item : items) {
            save(item, operatorId);
            success++;
        }
        OaonesLog.step("批量绑定部门完成 success=" + success + " total=" + items.size());
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("total", items.size());
        return result;
    }

    @Override
    public void delete(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            throw new RuntimeException("缺少部门绑定记录ID");
        }
        OaonesMyBatis.run(DeptBindMapper.class, mapper -> mapper.deleteById(table(), id));
        OaonesLog.step("删除部门绑定 bindId=" + bindId);
    }

    private JSONObject find(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            return null;
        }
        DeptBind row = OaonesMyBatis.call(DeptBindMapper.class, mapper -> mapper.findById(table(), id));
        if (row == null) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getOaDeptId()));
        json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
        json.put("wx_parent_dept_id", OaonesUtil.nullToEmpty(row.getWxParentDeptId()));
        json.put("bind_state", OaonesUtil.nullToEmpty(row.getYx()));
        String createDate = OaonesUtil.s(row.getModedatacreatedate());
        String createTime = OaonesUtil.s(row.getModedatacreatetime());
        json.put("bind_time", createDate.length() == 0 && createTime.length() == 0
                ? "" : createDate + " " + createTime);
        json.put("update_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
        json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        return json;
    }

    private String[] splitCsv(String value) {
        if (value == null || value.trim().length() == 0) {
            return new String[0];
        }
        List<String> list = new ArrayList<String>();
        for (String s : value.split(",")) {
            if (s != null && s.trim().length() > 0) {
                list.add(s.trim());
            }
        }
        return list.toArray(new String[0]);
    }

}
