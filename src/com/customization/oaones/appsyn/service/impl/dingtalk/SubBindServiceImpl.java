package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.dingtalk.SubBindMapper;
import com.customization.oaones.appsyn.dto.dingtalk.SubBindRow;
import com.customization.oaones.appsyn.entity.dingtalk.SubBind;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.dingtalk.SubBindService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分部绑定维护实现（DAO 层为 {@link SubBindMapper}）。
 *
 * <p>分部绑定只登记“该 OA 分部归属哪个钉钉租户”（cfg_id）：钉钉部门侧不再重复存储，
 * 该分部对应的钉钉部门取租户配置 {@code uf_oaones_qywx_config.root_dept_id}
 * （在“钉钉租户配置”页维护）。分部到钉钉租户为“一对一”：同一 OA 分部只保留一条绑定，
 * 同一钉钉租户也只允许被一个 OA 分部绑定；解除分部绑定时，
 * 该分部下已建立的 OA 部门绑定会一并解除。</p>
 *
 * <p>绑定状态 yx 口径：<b>0 有效 / 1 无效</b>（见 {@link OaonesConst#SUB_BIND_STATE_VALID}）。</p>
 */
public class SubBindServiceImpl implements SubBindService {

    private String table() {
        return OaonesProps.subBindTable();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    @Override
    public JSONArray list(String cfgId, String keyword) {
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", OaonesUtil.integer(cfgId));
        params.put("cfgIds", null);
        return listBy(params, keyword);
    }

    @Override
    public JSONArray listForCfgs(List<Integer> cfgIds, String keyword) {
        if (cfgIds == null || cfgIds.isEmpty()) {
            // 当前登录人没有任何可维护租户：不查库，直接返回空列表
            return new JSONArray();
        }
        Map<String, Object> params = OaonesParam.of();
        params.put("cfgId", null);
        params.put("cfgIds", new ArrayList<Integer>(new HashSet<Integer>(cfgIds)));
        return listBy(params, keyword);
    }

    private JSONArray listBy(Map<String, Object> params, String keyword) {
        OaonesLog.step("查询分部绑定列表 cfgId=" + params.get("cfgId")
                + " cfgIds=" + params.get("cfgIds") + " keyword=" + keyword);
        JSONArray result = new JSONArray();
        String k = keyword == null ? "" : keyword.trim();
        params.put("keyword", k.length() == 0 ? "" : "%" + k + "%");
        List<SubBindRow> rows = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.listRows(params));
        if (rows == null) {
            return result;
        }
        for (SubBindRow row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
            json.put("corp_name", OaonesUtil.nullToEmpty(row.getCfgName()));
            json.put("corp_id", OaonesUtil.nullToEmpty(row.getCfgCorpId()));
            // 分部对应的钉钉部门：取租户配置中维护的根部门
            json.put("root_dept_id", OaonesUtil.nullToEmpty(row.getCfgRootDeptId()));
            json.put("sync_enable", OaonesUtil.nullToEmpty(row.getCfgSyncEnable()));
            json.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getOaSubId()));
            json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("oa_sub_name", OaonesUtil.nullToEmpty(row.getOaSubName()));
            json.put("oa_sub_parent", OaonesUtil.nullToEmpty(row.getOaSubParent()));
            json.put("oa_sub_canceled", OaonesUtil.nullToEmpty(row.getOaSubCanceled()));
            json.put("bind_state", OaonesUtil.nullToEmpty(row.getBindState()));
            String createDate = OaonesUtil.s(row.getModedatacreatedate());
            String createTime = OaonesUtil.s(row.getModedatacreatetime());
            json.put("bind_time", createDate.length() == 0 && createTime.length() == 0
                    ? "" : createDate + " " + createTime);
            json.put("update_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
            json.put("formmodeid", OaonesUtil.nullToEmpty(row.getFormmodeid()));
            json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
            result.add(json);
        }
        return result;
    }

    @Override
    public JSONArray listEnabled(String cfgId) {
        OaonesLog.step("查询有效的分部绑定 cfgId=" + cfgId);
        JSONArray result = new JSONArray();
        Integer cfg = OaonesUtil.integer(cfgId);
        List<SubBind> rows = OaonesMyBatis.call(SubBindMapper.class,
                mapper -> mapper.listEnabled(table(), cfg == null ? 0 : cfg));
        if (rows == null) {
            return result;
        }
        for (SubBind row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getId()));
            json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
            json.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getOaSubId()));
            // 分部绑定的 yx 口径为 0 有效 / 1 无效，listEnabled 只返回 yx=0 的行
            json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("bind_state", OaonesUtil.nullToEmpty(row.getYx()));
            result.add(json);
        }
        return result;
    }

    @Override
    public Integer cfgIdOf(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            return null;
        }
        SubBind row = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.findById(table(), id));
        return row == null ? null : row.getCfgId();
    }

    @Override
    public void markWxDept(String cfgId, String oaSubId, String wxDeptId, String operatorId) {
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int subKey = OaonesUtil.i(oaSubId, 0);
        int deptKey = OaonesUtil.i(wxDeptId, 0);
        if (cfgKey <= 0 || subKey <= 0 || deptKey <= 0) {
            throw new RuntimeException("记录分部对应的钉钉部门缺少参数（cfg_id/oa_sub_id/wx_dept_id）");
        }
        OaonesLog.step("记录分部对应的钉钉部门 cfgId=" + cfgKey + " oaSubId=" + subKey
                + " wxDeptId=" + deptKey);
        int rows = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.updateWxDeptId(
                table(), cfgKey, subKey, deptKey, OaonesUtil.i(operatorId, 0), OaonesUtil.now()));
        if (rows <= 0) {
            throw new RuntimeException("分部绑定不存在，无法记录该分部对应的钉钉部门");
        }
    }

    @Override
    public Integer cfgIdOfSub(String oaSubId) {
        Integer subId = OaonesUtil.integer(oaSubId);
        if (subId == null || subId <= 0) {
            return null;
        }
        Integer id = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.findIdByOaSub(table(), subId));
        if (id == null) {
            return null;
        }
        SubBind row = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.findById(table(), id));
        return row == null ? null : row.getCfgId();
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        String oaSubId = OaonesUtil.s(params.getString("oa_sub_id"));
        String remark = OaonesUtil.s(params.getString("remark"));
        OaonesLog.step("保存分部绑定 cfgId=" + cfgId + " oaSubId=" + oaSubId);
        if (cfgId.length() == 0 || oaSubId.length() == 0) {
            throw new RuntimeException("请选择 OA 分部与要绑定的钉钉租户");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        JSONObject oaSub = oaService().getSubCompany(oaSubId);
        if (oaSub == null) {
            throw new RuntimeException("OA 分部不存在");
        }
        if ("1".equals(OaonesUtil.s(oaSub.getString("canceled")))) {
            throw new RuntimeException("OA 分部已封存，不允许建立/更换绑定");
        }
        int cfgKey = OaonesUtil.i(cfgId, 0);
        int subKey = OaonesUtil.i(oaSubId, 0);
        // 一对一：同一钉钉租户只允许被一个 OA 分部绑定
        validateCfgNotBoundByOtherSub(cfgKey, subKey);
        String now = OaonesUtil.now();
        int modeId = OaonesProps.subBindModeId();
        Integer existId = OaonesMyBatis.call(SubBindMapper.class,
                mapper -> mapper.findIdByOaSub(table(), subKey));
        if (existId != null) {
            SubBind entity = new SubBind();
            entity.setId(existId);
            entity.setCfgId(cfgKey);
            entity.setOaSubId(subKey);
            entity.setYx(OaonesConst.SUB_BIND_STATE_VALID);
            entity.setRemark(remark);
            entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
            entity.setModedatamodifydatetime(now);
            OaonesMyBatis.call(SubBindMapper.class, mapper -> {
                if (mapper.update(table(), entity) <= 0) {
                    throw new RuntimeException("分部绑定更新失败");
                }
                return 1;
            });
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(existId));
            return find(String.valueOf(existId));
        }
        SubBind entity = new SubBind();
        entity.setCfgId(cfgKey);
        entity.setOaSubId(subKey);
        entity.setYx(OaonesConst.SUB_BIND_STATE_VALID);
        entity.setRemark(remark);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(OaonesUtil.i(operatorId, 0));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        entity.setModedatamodifydatetime(now);
        OaonesMyBatis.call(SubBindMapper.class, mapper -> {
            if (mapper.insert(table(), entity) <= 0) {
                throw new RuntimeException("分部绑定保存失败");
            }
            return 1;
        });
        Integer newId = OaonesMyBatis.call(SubBindMapper.class,
                mapper -> mapper.findIdByOaSub(table(), subKey));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operatorId);
        return find(String.valueOf(newId));
    }

    @Override
    public JSONObject batchSave(JSONObject params, String operatorId) {
        String[] oaIds = splitCsv(params.getString("oa_ids"));
        String[] cfgIds = splitCsv(params.getString("cfg_ids"));
        OaonesLog.step("批量绑定分部 oaIds=" + OaonesUtil.s(params.getString("oa_ids"))
                + " cfgIds=" + OaonesUtil.s(params.getString("cfg_ids")));
        if (oaIds.length == 0 || oaIds.length != cfgIds.length) {
            throw new RuntimeException("批量绑定参数不完整");
        }
        // 一对一：同一批中 OA 分部ID、钉钉租户ID都不允许重复
        Set<String> seenOa = new HashSet<String>();
        Set<String> seenCfg = new HashSet<String>();
        for (int i = 0; i < oaIds.length; i++) {
            String oaId = oaIds[i].trim();
            String cfgId = cfgIds[i].trim();
            if (oaId.length() == 0 || cfgId.length() == 0 || "0".equals(cfgId)) {
                throw new RuntimeException("第 " + (i + 1) + " 条 OA分部/钉钉租户未选择完整");
            }
            if (!seenOa.add(oaId)) {
                throw new RuntimeException("批量绑定中 OA 分部ID " + oaId + " 重复");
            }
            if (!seenCfg.add(cfgId)) {
                throw new RuntimeException("批量绑定中钉钉租户ID " + cfgId
                        + " 重复（分部与钉钉租户为一对一，同一钉钉租户只能绑定一个 OA 分部）");
            }
            if (configService().get(cfgId) == null) {
                throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
            }
            JSONObject oaSub = oaService().getSubCompany(oaId);
            if (oaSub == null) {
                throw new RuntimeException("OA 分部ID " + oaId + " 不存在");
            }
            if ("1".equals(OaonesUtil.s(oaSub.getString("canceled")))) {
                throw new RuntimeException("OA 分部ID " + oaId + " 已封存，不允许绑定");
            }
            validateCfgNotBoundByOtherSub(OaonesUtil.i(cfgId, 0), OaonesUtil.i(oaId, 0));
        }
        List<JSONObject> items = new ArrayList<JSONObject>();
        for (int i = 0; i < oaIds.length; i++) {
            JSONObject item = new JSONObject();
            item.put("cfg_id", cfgIds[i].trim());
            item.put("oa_sub_id", oaIds[i].trim());
            item.put("remark", "");
            items.add(item);
        }
        int success = 0;
        for (JSONObject item : items) {
            save(item, operatorId);
            success++;
        }
        OaonesLog.step("批量绑定分部完成 success=" + success + " total=" + items.size());
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("total", items.size());
        return result;
    }

    @Override
    public JSONObject delete(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            throw new RuntimeException("缺少分部绑定记录ID");
        }
        SubBind row = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.findById(table(), id));
        if (row == null) {
            throw new RuntimeException("分部绑定记录不存在或已删除");
        }
        OaonesMyBatis.run(SubBindMapper.class, mapper -> mapper.deleteById(table(), id));
        // 说明：手动建立的部门绑定不依赖分部绑定，因此解除分部绑定时**不动**其下部门绑定；
        // 只影响“按编号/名称自动绑定”——需要重新绑定分部后才能继续自动绑定该分部下的部门。
        OaonesLog.step("删除分部绑定 bindId=" + bindId
                + " oaSubId=" + OaonesUtil.nullToEmpty(row.getOaSubId()));
        JSONObject result = new JSONObject();
        result.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getOaSubId()));
        result.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        return result;
    }

    /**
     * 一对一校验：同一钉钉租户只允许被一个 OA 分部绑定。
     *
     * @param oaSubId 当前正在绑定的 OA 分部（同一分部的重复保存允许，视为更换绑定）
     */
    private void validateCfgNotBoundByOtherSub(int cfgKey, int oaSubId) {
        Integer existId = OaonesMyBatis.call(SubBindMapper.class,
                mapper -> mapper.findActiveIdByCfg(table(), cfgKey));
        if (existId == null) {
            return;
        }
        SubBind exist = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.findById(table(), existId));
        if (exist == null || exist.getOaSubId() == null || exist.getOaSubId() == oaSubId) {
            return;
        }
        JSONObject otherSub = oaService().getSubCompany(OaonesUtil.nullToEmpty(exist.getOaSubId()));
        String otherName = otherSub == null ? "" : OaonesUtil.s(otherSub.getString("name"));
        throw new RuntimeException("钉钉租户ID " + cfgKey + " 已被 OA 分部"
                + (otherName.length() == 0 ? "" : "「" + otherName + "」")
                + "（ID " + exist.getOaSubId() + "）绑定；分部与钉钉租户为一对一，"
                + "请先解除该分部绑定后再绑定");
    }

    private JSONObject find(String bindId) {
        Integer id = OaonesUtil.integer(bindId);
        if (id == null || id <= 0) {
            return null;
        }
        SubBind row = OaonesMyBatis.call(SubBindMapper.class, mapper -> mapper.findById(table(), id));
        if (row == null) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgId()));
        json.put("oa_sub_id", OaonesUtil.nullToEmpty(row.getOaSubId()));
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
