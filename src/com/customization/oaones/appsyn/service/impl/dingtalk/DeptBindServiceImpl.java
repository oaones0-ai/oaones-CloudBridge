package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.dingtalk.DeptBindMapper;
import com.customization.oaones.appsyn.dao.dingtalk.SubBindMapper;
import com.customization.oaones.appsyn.dto.dingtalk.DeptBindRow;
import com.customization.oaones.appsyn.entity.dingtalk.DeptBind;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.dingtalk.DeptBindService;
import com.customization.oaones.appsyn.service.dingtalk.DeptTmpService;
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
        for (DeptBind row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
            json.put("oa_dept_id", OaonesUtil.nullToEmpty(row.getOaDeptId()));
            json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("wx_parent_dept_id", OaonesUtil.nullToEmpty(row.getWxParentDeptId()));
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
            throw new RuntimeException("请选择钉钉租户与 OA 部门");
        }
        if (wxDeptId.length() == 0 || "0".equals(wxDeptId)) {
            throw new RuntimeException("钉钉部门尚不存在时不允许建立绑定，也不会自动创建钉钉部门");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        JSONObject oaDept = oaService().getDepartment(oaDeptId);
        if (oaDept == null) {
            throw new RuntimeException("OA 部门不存在");
        }
        if ("1".equals(OaonesUtil.s(oaDept.getString("canceled")))) {
            throw new RuntimeException("OA 部门已封存，不允许建立/更换绑定");
        }
        // 手动绑定不受“分部已绑定”限制（该限制只作用于按编号/名称的自动绑定，见 autoBatchSave）
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int oaKey = OaonesUtil.i(oaDeptId, 0);
        int wxKey = OaonesUtil.i(wxDeptId, 0);
        String now = OaonesUtil.now();
        int modeId = OaonesProps.deptBindModeId();
        // OA 部门 → 钉钉部门为“多对一”：同一钉钉部门可被多个 OA 部门绑定，
        // 因此这里不再校验钉钉部门是否已被其他 OA 部门占用；
        // 仍由 (cfg_id, oa_dept_id) 唯一索引保证同一 OA 部门只保留一条绑定记录。
        Integer existId = OaonesMyBatis.call(DeptBindMapper.class,
                mapper -> mapper.findIdByOaDept(table(), cfgKey, oaKey));
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
                mapper -> mapper.findIdByOaDept(table(), cfgKey, oaKey));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operatorId);
        return find(String.valueOf(newId));
    }

    @Override
    public JSONObject autoBatchSave(JSONObject params, String operatorId) {
        return doBatchSave(params, operatorId, true);
    }

    @Override
    public JSONObject batchSave(JSONObject params, String operatorId) {
        return doBatchSave(params, operatorId, false);
    }

    /**
     * 批量绑定部门。
     *
     * @param needSubBound 是否要求“OA 部门所属分部已绑定钉钉租户”：
     *                     自动绑定（按部门编号/名称匹配的结果）为 true，
     *                     手动勾选后批量绑定为 false
     */
    private JSONObject doBatchSave(JSONObject params, String operatorId, boolean needSubBound) {
        OaonesLog.step("批量绑定部门 cfgId=" + OaonesUtil.s(params.getString("cfg_id"))
                + " oaIds=" + OaonesUtil.s(params.getString("oa_ids"))
                + " wxIds=" + OaonesUtil.s(params.getString("wx_ids"))
                + " needSubBound=" + needSubBound);
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        String[] oaIds = splitCsv(params.getString("oa_ids"));
        String[] wxIds = splitCsv(params.getString("wx_ids"));
        String[] wxParents = splitCsv(params.getString("wx_parent_ids"));
        if (cfgId.length() == 0) {
            throw new RuntimeException("缺少钉钉租户");
        }
        if (oaIds.length == 0 || oaIds.length != wxIds.length || wxParents.length != wxIds.length) {
            throw new RuntimeException("批量绑定参数不完整");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("钉钉租户未配置 AppKey");
        }
        Set<String> seenOa = new HashSet<String>();
        for (int i = 0; i < oaIds.length; i++) {
            String oaId = oaIds[i].trim();
            String wxId = wxIds[i].trim();
            if (oaId.length() == 0 || wxId.length() == 0 || "0".equals(wxId)) {
                throw new RuntimeException("第 " + (i + 1) + " 条 OA部门/钉钉部门未选择完整");
            }
            // 同一个 OA 部门在一批中只能出现一次；钉钉部门允许重复（多对一：多个 OA 部门对应同一钉钉部门）
            if (!seenOa.add(oaId)) {
                throw new RuntimeException("批量绑定中 OA 部门ID " + oaId + " 重复");
            }
            JSONObject oaDept = oaService().getDepartment(oaId);
            if (oaDept == null) {
                throw new RuntimeException("OA 部门ID " + oaId + " 不存在");
            }
            if ("1".equals(OaonesUtil.s(oaDept.getString("canceled")))) {
                throw new RuntimeException("OA 部门ID " + oaId + " 已封存，不允许绑定");
            }
            // 自动绑定要求分部已绑定：任一行的分部未绑定，整批不落库；手动绑定不校验该条件
            if (needSubBound) {
                requireSubBound(cfgId, oaDept);
            }
            try {
                if (!deptTmpService().isNormal(corpId, wxId)) {
                    throw new RuntimeException("钉钉部门ID " + wxId
                            + " 不在部门快照中或状态异常（已封存/已删除），不允许绑定");
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("校验钉钉部门快照失败: " + e.getMessage());
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

    /**
     * 校验 OA 部门所属分部已建立钉钉租户绑定（仅“自动绑定”使用）。
     *
     * <p>自动绑定（按部门编号/名称匹配）以分部绑定为前置条件：
     * 只有分部已绑定钉钉租户，才能按编号/名称自动匹配并绑定该分部下的部门；
     * 手动绑定不受此限制，见 {@code batchSave} / {@code save}。</p>
     */
    private void requireSubBound(String cfgId, JSONObject oaDept) {
        String subId = OaonesUtil.s(oaDept.getString("subcompanyid1"));
        if (subId.length() == 0 || "0".equals(subId)) {
            throw new RuntimeException("OA 部门未归属任何分部，不允许自动绑定部门；"
                    + "请先在 OA 组织架构中维护该部门所属分部");
        }
        Integer cfgKey = OaonesUtil.integer(cfgId);
        int cfg = cfgKey == null ? 0 : cfgKey;
        int sub = OaonesUtil.i(subId, 0);
        Integer bindId = OaonesMyBatis.call(SubBindMapper.class,
                mapper -> mapper.findIdByCfgAndOaSub(OaonesProps.subBindTable(), cfg, sub));
        if (bindId != null) {
            return;
        }
        JSONObject oaSub = oaService().getSubCompany(subId);
        String subName = oaSub == null ? "" : OaonesUtil.s(oaSub.getString("name"));
        throw new RuntimeException("部门所在分部" + (subName.length() == 0 ? "" : "「" + subName + "」")
                + "（ID " + subId + "）尚未绑定钉钉租户，不允许自动绑定部门；"
                + "请先在“分部绑定”页完成分部绑定后再自动绑定（手动绑定不受此限制）");
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
