package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dto.dingtalk.SyncScope;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.dingtalk.DeptTmpService;
import com.customization.oaones.appsyn.service.dingtalk.SyncScopeService;
import weaver.general.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同步范围解析实现。
 *
 * <p>OA 侧读 E9 组织缓存（SubCompanyComInfo / DepartmentComInfo 快照，见
 * {@link OaServiceImpl#listSubCompaniesFromCache()} / {@link OaServiceImpl#listDepartmentsFromCache()}），
 * 钉钉侧读部门快照（{@code uf_oaones_dep_tmp}，不实时调用钉钉）。</p>
 */
public class SyncScopeServiceImpl implements SyncScopeService {

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
    public SyncScope resolve(String cfgId, int direction, String oaSubIds, String oaDeptIds,
                             String wxDeptId) {
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
        }
        SyncScope scope = new SyncScope();
        scope.setCfg(cfg);
        scope.setCfgId(cfgId);
        scope.setDirection(direction);

        List<String> subIds = splitCsv(oaSubIds);
        List<String> deptIds = splitCsv(oaDeptIds);
        resolveOa(scope, subIds, deptIds);
        resolveWx(scope, wxDeptId);

        OaonesLog.step("解析同步范围 cfgId=" + cfgId + " direction=" + direction
                + " OA部门数=" + scope.getOaDepts().size()
                + " 钉钉部门数=" + scope.getWxDepts().size()
                + " 范围=" + scope.getScopeText() + " 落点=" + scope.getWxScopeText());
        return scope;
    }

    /* ---------------------------------------------------------------
     * OA 侧：分部/部门 → 含下级的部门集合
     * --------------------------------------------------------------- */

    private void resolveOa(SyncScope scope, List<String> subIds, List<String> deptIds) {
        JSONArray subRows = oaService().listSubCompaniesFromCache();
        JSONArray deptRows = oaService().listDepartmentsFromCache();

        Map<String, JSONObject> subById = new HashMap<String, JSONObject>();
        for (int i = 0; i < subRows.size(); i++) {
            JSONObject row = subRows.getJSONObject(i);
            subById.put(OaonesUtil.s(row.getString("id")), row);
        }
        Map<String, JSONObject> deptById = new HashMap<String, JSONObject>();
        for (int i = 0; i < deptRows.size(); i++) {
            JSONObject row = deptRows.getJSONObject(i);
            deptById.put(OaonesUtil.s(row.getString("id")), row);
        }

        // 1) 选中分部：含其下级分部
        Set<String> subScope = new LinkedHashSet<String>();
        for (String id : subIds) {
            if (!subById.containsKey(id)) {
                throw new RuntimeException("OA 分部[id=" + id + "]不存在或已封存，请重新选择同步范围");
            }
            subScope.add(id);
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < subRows.size(); i++) {
                JSONObject row = subRows.getJSONObject(i);
                String parent = OaonesUtil.s(row.getString("parent"));
                String id = OaonesUtil.s(row.getString("id"));
                if (parent.length() > 0 && subScope.contains(parent) && subScope.add(id)) {
                    grew = true;
                }
            }
        }

        // 2) 选中部门：含其下级部门；选中的分部则含其全部下级部门
        Set<String> deptScope = new LinkedHashSet<String>();
        for (String id : deptIds) {
            if (!deptById.containsKey(id)) {
                throw new RuntimeException("OA 部门[id=" + id + "]不存在或已封存，请重新选择同步范围");
            }
            deptScope.add(id);
        }
        grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < deptRows.size(); i++) {
                JSONObject row = deptRows.getJSONObject(i);
                String id = OaonesUtil.s(row.getString("id"));
                String parent = OaonesUtil.s(row.getString("parent"));
                if (deptScope.contains(parent) && deptScope.add(id)) {
                    grew = true;
                }
            }
        }
        if (!subScope.isEmpty()) {
            for (int i = 0; i < deptRows.size(); i++) {
                JSONObject row = deptRows.getJSONObject(i);
                if (subScope.contains(OaonesUtil.s(row.getString("subcompanyid1")))) {
                    deptScope.add(OaonesUtil.s(row.getString("id")));
                }
            }
        }

        // 3) 原始选择（用于页面文案与预览提示）
        for (String id : subIds) {
            scope.getOaSubs().add(subById.get(id));
        }
        for (String id : deptIds) {
            scope.getOaDeptsSelected().add(deptById.get(id));
        }

        // 4) 按“父 → 子”层级排序（同层按 ID，保证父部门先于子部门被处理）
        final Map<String, JSONObject> deptIndex = deptById;
        List<JSONObject> resolved = new ArrayList<JSONObject>();
        for (String id : deptScope) {
            JSONObject row = deptById.get(id);
            if (row != null) {
                resolved.add(row);
            }
        }
        Collections.sort(resolved, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                int da = oaDepth(deptIndex, OaonesUtil.s(a.getString("id")));
                int db = oaDepth(deptIndex, OaonesUtil.s(b.getString("id")));
                if (da != db) {
                    return da - db;
                }
                return Util.getIntValue(OaonesUtil.s(a.getString("id")), 0)
                        - Util.getIntValue(OaonesUtil.s(b.getString("id")), 0);
            }
        });
        scope.setOaDepts(resolved);
        scope.setScopeText(oaScopeText(scope, subIds, deptIds));
    }

    /** OA 部门层级深度（向上追 supdepid；出现环时最多追 30 层）。 */
    private int oaDepth(Map<String, JSONObject> deptIndex, String deptId) {
        int depth = 0;
        String current = deptId;
        Set<String> seen = new HashSet<String>();
        while (current != null && current.length() > 0 && depth < 30 && seen.add(current)) {
            JSONObject row = deptIndex.get(current);
            if (row == null) {
                break;
            }
            String parent = OaonesUtil.s(row.getString("parent"));
            if (parent.length() == 0 || "0".equals(parent)) {
                break;
            }
            current = parent;
            depth++;
        }
        return depth;
    }

    /** OA 侧范围文案，如“分部「研发中心」、部门「质量部」”。 */
    private String oaScopeText(SyncScope scope, List<String> subIds, List<String> deptIds) {
        List<String> parts = new ArrayList<String>();
        if (!subIds.isEmpty()) {
            parts.add("分部" + nameList(scope.getOaSubs()));
        }
        if (!deptIds.isEmpty()) {
            parts.add("部门" + nameList(scope.getOaDeptsSelected()));
        }
        if (parts.isEmpty()) {
            return "全部 OA 部门";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /* ---------------------------------------------------------------
     * 钉钉侧：部门（含下级）；未指定时取租户 root 节点
     * --------------------------------------------------------------- */

    private void resolveWx(SyncScope scope, String wxDeptId) {
        String cfgId = scope.getCfgId();
        JSONArray rows;
        try {
            rows = deptTmpService().listNormal(cfgId);
        } catch (Exception e) {
            throw new RuntimeException("读取钉钉部门快照失败：" + e.getMessage()
                    + "，请先更新钉钉部门快照", e);
        }
        Map<String, JSONObject> byId = new HashMap<String, JSONObject>();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            byId.put(OaonesUtil.s(row.getString("id")), row);
        }

        boolean specified = wxDeptId != null && wxDeptId.trim().length() > 0;
        String effective = specified ? wxDeptId.trim()
                : String.valueOf(Util.getIntValue(OaonesUtil.s(scope.getCfg().getString("root_dept_id")), 1));
        if ("0".equals(effective)) {
            effective = "1";
        }
        JSONObject node = byId.get(effective);
        if (node == null) {
            if (specified) {
                throw new RuntimeException("钉钉部门[id=" + effective
                        + "]不在部门快照里（可能已删除），请先更新钉钉部门快照或重新选择");
            }
            // root 节点本身常常不是快照里的“业务部门”（钉钉根为 1），这里补一个占位节点，
            // 保证“未指定部门 = root 节点”时新增部门仍能挂到 root 下
            node = new JSONObject();
            node.put("id", effective);
            node.put("name", "root 节点");
            node.put("parentid", "0");
        }
        scope.setWxDeptId(effective);

        Set<String> wxScope = new LinkedHashSet<String>();
        wxScope.add(effective);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = OaonesUtil.s(row.getString("id"));
                String parent = OaonesUtil.s(row.getString("parentid"));
                if (wxScope.contains(parent) && wxScope.add(id)) {
                    grew = true;
                }
            }
        }

        final Map<String, JSONObject> index = byId;
        List<JSONObject> resolved = new ArrayList<JSONObject>();
        for (String id : wxScope) {
            JSONObject row = byId.get(id);
            if (row != null) {
                resolved.add(row);
            }
        }
        Collections.sort(resolved, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                int da = wxDepth(index, OaonesUtil.s(a.getString("id")));
                int db = wxDepth(index, OaonesUtil.s(b.getString("id")));
                if (da != db) {
                    return da - db;
                }
                return Util.getIntValue(OaonesUtil.s(a.getString("id")), 0)
                        - Util.getIntValue(OaonesUtil.s(b.getString("id")), 0);
            }
        });
        scope.setWxDepts(resolved);
        String nodeName = OaonesUtil.s(node.getString("name"));
        scope.setWxScopeText((specified ? "钉钉部门「" : "钉钉 root 节点「")
                + (nodeName.length() == 0 ? effective : nodeName) + "」（含下级部门）");
    }

    /** 钉钉部门层级深度（向上追 parentid；出现环时最多追 30 层）。 */
    private int wxDepth(Map<String, JSONObject> index, String deptId) {
        int depth = 0;
        String current = deptId;
        Set<String> seen = new HashSet<String>();
        while (current != null && current.length() > 0 && depth < 30 && seen.add(current)) {
            JSONObject row = index.get(current);
            if (row == null) {
                break;
            }
            String parent = OaonesUtil.s(row.getString("parentid"));
            if (parent.length() == 0 || "0".equals(parent)) {
                break;
            }
            current = parent;
            depth++;
        }
        return depth;
    }

    /* --------------------------------------------------------------- */

    private List<String> splitCsv(String value) {
        List<String> list = new ArrayList<String>();
        if (value == null) {
            return list;
        }
        for (String token : value.split(",")) {
            String v = token == null ? "" : token.trim();
            if (v.length() > 0 && !list.contains(v)) {
                list.add(v);
            }
        }
        return list;
    }

    /** “「A」「B」「C」”形式的名称串；超过 5 个只列前 5 个并标注总数。 */
    private String nameList(List<JSONObject> rows) {
        StringBuilder sb = new StringBuilder();
        int show = Math.min(rows.size(), 5);
        for (int i = 0; i < show; i++) {
            JSONObject row = rows.get(i);
            String name = OaonesUtil.s(row.getString("name"));
            if (name.length() == 0) {
                name = OaonesUtil.s(row.getString("id"));
            }
            sb.append('「').append(name).append('」');
        }
        if (rows.size() > show) {
            sb.append("等 ").append(rows.size()).append(" 个");
        }
        return sb.toString();
    }

    /** 钉钉部门快照表名（供日志排查，说明本类不实时调用钉钉）。 */
    String snapshotTable() {
        return OaonesProps.deptTmpTable();
    }
}
