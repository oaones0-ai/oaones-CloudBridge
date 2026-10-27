package com.customization.oaones.appsyn.service.impl.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.wecom.WeComApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.wecom.DeptBindMapper;
import com.customization.oaones.appsyn.dao.wecom.DeptTmpMapper;
import com.customization.oaones.appsyn.entity.wecom.DeptTmp;
import com.customization.oaones.appsyn.service.wecom.ConfigService;
import com.customization.oaones.appsyn.service.wecom.DeptTmpService;
import weaver.general.BaseBean;
import weaver.general.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 部门快照表维护实现（DAO 层为 {@link DeptTmpMapper}）。
 *
 * <p>type=0 表示企微、type=1 表示钉钉（当前阶段只处理企微）。</p>
 */
public class DeptTmpServiceImpl implements DeptTmpService {

    private String table() {
        return OaonesProps.deptTmpTable();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    @Override
    public JSONObject refreshByCfgId(String cfgId) throws Exception {
        OaonesLog.step("刷新部门快照 cfgId=" + cfgId + " type=" + OaonesConst.TYPE_WECHAT);
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("企微租户配置不存在: id=" + cfgId);
        }
        WeComApi api = configService().getClient(cfgId);
        JSONArray depts = api.listDepartments();
        OaonesLog.step("企微接口返回部门数 cfgId=" + cfgId + " count=" + depts.size());
        return refresh(cfg, depts);
    }

    @Override
    public JSONObject refreshAll() {
        JSONObject result = new JSONObject();
        int okCount = 0;
        int failCount = 0;
        JSONArray cfgs = configService().list();
        for (int i = 0; i < cfgs.size(); i++) {
            JSONObject cfg = cfgs.getJSONObject(i);
            String cfgId = OaonesUtil.s(cfg.getString("id"));
            if (Util.getIntValue(OaonesUtil.s(cfg.getString("sync_enable")), 0) == 0) {
                continue;
            }
            try {
                refreshByCfgId(cfgId);
                okCount++;
            } catch (Throwable t) {
                failCount++;
                new BaseBean().writeLog("oaones dept tmp refresh fail cfgId=" + cfgId, t);
            }
        }
        result.put("ok", okCount);
        result.put("fail", failCount);
        return result;
    }

    @Override
    public JSONObject refresh(JSONObject cfg, JSONArray depts) throws Exception {
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("企微租户未配置 corpid");
        }
        String now = OaonesUtil.now();
        // 先把当前租户已有快照全部置为 status=1，
        // 未在本轮接口返回的部门保持关闭，后续逐条查询决定 update/insert
        final String corp = corpId;
        OaonesMyBatis.call(DeptTmpMapper.class, mapper -> mapper.closeAll(
                table(), corp, OaonesConst.TYPE_WECHAT, OaonesConst.STATUS_CLOSED, now));

        for (int i = 0; i < depts.size(); i++) {
            JSONObject d = depts.getJSONObject(i);
            int wxId = (int) d.getLongValue("id");
            String name = OaonesUtil.s(d.getString("name"));
            int parentId = d.containsKey("parentid") ? (int) d.getLongValue("parentid") : 0;
            Integer exists = OaonesMyBatis.call(DeptTmpMapper.class,
                    mapper -> mapper.findWxDeptId(table(), corp, wxId, OaonesConst.TYPE_WECHAT));
            DeptTmp entity = new DeptTmp();
            entity.setCorpid(corp);
            entity.setWxDeptId(wxId);
            entity.setDeptName(name);
            entity.setParentId(parentId);
            entity.setStatus(OaonesConst.STATUS_NORMAL);
            entity.setLastSyncTime(now);
            entity.setType(OaonesConst.TYPE_WECHAT);
            if (exists != null) {
                // 快照中已存在：恢复为正常并更新部门信息
                OaonesMyBatis.call(DeptTmpMapper.class, mapper -> mapper.updateDept(table(), entity));
            } else {
                // 快照中不存在：新增
                entity.setFormmodeid(OaonesProps.deptTmpModeId());
                Integer inserted = OaonesMyBatis.call(DeptTmpMapper.class, mapper -> {
                    if (mapper.insertDept(table(), entity) <= 0) {
                        throw new RuntimeException("部门快照写入失败：corpid=" + corp
                                + ", wx_dept_id=" + entity.getWxDeptId()
                                + "，请检查 " + table() + " 表字段是否与 SQL 脚本一致");
                    }
                    return 1;
                });
                if (inserted == null) {
                    throw new RuntimeException("部门快照写入失败：corpid=" + corp);
                }
            }
        }
        JSONObject result = new JSONObject();
        result.put("corp_id", corpId);
        result.put("department_count", depts.size());
        result.put("msg", "企微部门快照更新成功");
        OaonesLog.step("部门快照更新完成 corpId=" + corpId + " count=" + depts.size());
        return result;
    }

    @Override
    public JSONArray listNormal(String cfgId) throws Exception {
        OaonesLog.step("查询正常部门快照 cfgId=" + cfgId + " type=" + OaonesConst.TYPE_WECHAT);
        JSONArray result = new JSONArray();
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            return result;
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            return result;
        }
        List<DeptTmp> rows = OaonesMyBatis.call(DeptTmpMapper.class, mapper -> mapper.listNormal(
                table(), corpId, OaonesConst.TYPE_WECHAT, OaonesConst.STATUS_NORMAL));
        if (rows == null) {
            return result;
        }
        for (DeptTmp row : rows) {
            JSONObject json = new JSONObject();
            json.put("id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
            json.put("name", OaonesUtil.nullToEmpty(row.getDeptName()));
            json.put("parentid", OaonesUtil.nullToEmpty(row.getParentId()));
            json.put("status", OaonesUtil.nullToEmpty(row.getStatus()));
            json.put("no_bind", OaonesUtil.nullToEmpty(row.getNoBind()));
            result.add(json);
        }
        return result;
    }

    @Override
    public JSONObject markNoBind(String cfgId, String wxDeptId) {
        return setNoBindFlag(cfgId, wxDeptId, true);
    }

    @Override
    public JSONObject cancelNoBind(String cfgId, String wxDeptId) {
        return setNoBindFlag(cfgId, wxDeptId, false);
    }

    @Override
    public JSONObject batchNoBind(String cfgId, String wxDeptIds) {
        OaonesLog.step("批量标记企微部门免绑定 cfgId=" + cfgId + " wxDeptIds=" + wxDeptIds);
        if (cfgId == null || cfgId.trim().length() == 0) {
            throw new RuntimeException("缺少企业微信租户");
        }
        String[] ids = splitCsv(wxDeptIds);
        if (ids.length == 0) {
            throw new RuntimeException("请先勾选要设置免绑定的企微部门");
        }
        Set<String> seen = new HashSet<String>();
        for (String id : ids) {
            if (!seen.add(id)) {
                throw new RuntimeException("批量免绑定中企微部门ID " + id + " 重复");
            }
        }
        int success = 0;
        for (String id : ids) {
            setNoBindFlag(cfgId, id, true);
            success++;
        }
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("total", ids.length);
        return result;
    }

    @Override
    public boolean isNormal(String corpId, String wxDeptId) throws Exception {
        if (corpId == null || corpId.trim().length() == 0
                || wxDeptId == null || wxDeptId.trim().length() == 0) {
            return false;
        }
        Integer found = OaonesMyBatis.call(DeptTmpMapper.class, mapper -> mapper.findNormalWxDeptId(
                table(), corpId.trim(), OaonesUtil.i(wxDeptId, 0),
                OaonesConst.TYPE_WECHAT, OaonesConst.STATUS_NORMAL));
        return found != null;
    }

    @Override
    public String statusText(String status) {
        if (OaonesConst.STATUS_NORMAL == Util.getIntValue(status, -1)) {
            return "正常";
        }
        if (OaonesConst.STATUS_CLOSED == Util.getIntValue(status, -1)) {
            return "关闭/封存/已删除";
        }
        return "未知";
    }

    /**
     * 企微部门侧“免绑定”标记：维护 uf_oaones_appsyn_dep_tmp.no_bind。
     *
     * <p>本表只用 {@link OaonesConst#NO_BIND_MARK}（0）表达“免绑定”，取消其它状态值：
     * 标记免绑定写 0，取消免绑定把该列置回 NULL（正常态不写值）。</p>
     */
    private JSONObject setNoBindFlag(String cfgId, String wxDeptId, boolean noBind) {
        OaonesLog.step((noBind ? "标记企微部门免绑定" : "取消企微部门免绑定")
                + " cfgId=" + cfgId + " wxDeptId=" + wxDeptId);
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("企微租户配置不存在: id=" + cfgId);
        }
        String corpId = OaonesUtil.s(cfg.getString("corp_id"));
        if (corpId.length() == 0) {
            throw new RuntimeException("企微租户未配置 corpid");
        }
        String deptId = wxDeptId == null ? "" : wxDeptId.trim();
        if (deptId.length() == 0) {
            throw new RuntimeException("缺少企微部门ID");
        }
        int wxId = OaonesUtil.i(deptId, 0);
        Integer exists = OaonesMyBatis.call(DeptTmpMapper.class,
                mapper -> mapper.findWxDeptId(table(), corpId, wxId, OaonesConst.TYPE_WECHAT));
        if (exists == null) {
            throw new RuntimeException("企微部门快照中不存在该部门，请先更新部门快照");
        }
        if (noBind) {
            Integer cfgKey = OaonesUtil.integer(cfgId);
            Integer bound = OaonesMyBatis.call(DeptBindMapper.class, mapper -> mapper.findActiveIdByWxDept(
                    OaonesProps.deptBindTable(), cfgKey == null ? 0 : cfgKey, wxId));
            if (bound != null) {
                throw new RuntimeException("该企微部门已绑定 OA 部门，不能标记为免绑定");
            }
        }
        OaonesMyBatis.call(DeptTmpMapper.class, mapper -> {
            int rows = mapper.updateNoBind(table(), corpId, wxId,
                    OaonesConst.TYPE_WECHAT, noBind ? Integer.valueOf(OaonesConst.NO_BIND_MARK) : null);
            if (rows <= 0) {
                throw new RuntimeException(noBind ? "设置免绑定失败" : "取消免绑定失败");
            }
            return rows;
        });
        JSONObject result = new JSONObject();
        result.put("cfg_id", cfgId);
        result.put("wx_dept_id", deptId);
        result.put("no_bind", noBind ? OaonesConst.NO_BIND_MARK : "");
        return result;
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
