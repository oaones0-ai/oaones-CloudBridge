package com.customization.oaones.appsyn.common;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import weaver.conn.RecordSet;
import weaver.general.Util;
import weaver.hrm.appdetach.AppDetachComInfo;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.resource.ResourceComInfo;
import weaver.systeminfo.systemright.CheckSubCompanyRight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * OA 分权范围：OAones 页面里“选择 OA 组织架构（分部 / 部门 / 人员）”的候选
 * 必须与 OA 自身的分权口径一致——OA 开了分权时，登录人只能看到自己范围内的组织与成员，
 * 不能再把全量组织下发给前端。
 *
 * <p>口径<b>完全沿用 E9 平台自己的组织/人员选择框</b>，按 E9 实际启用的分权模式二选一：</p>
 * <ol>
 *   <li><b>应用分权</b>（{@code SystemSet.appdetachable=1}，E9 后台「分权管理 → 应用分权」）：
 *       用平台 {@link AppDetachComInfo} 取登录人的可见分部 / 部门 / 人员
 *       （{@code checkUserAppDetach}），与平台自带的资源 / 部门选择框同一口径；</li>
 *   <li><b>管理分权 / 角色分权</b>（{@code SystemSet.detachable=1} 或权限点自身开了分权）：
 *       调用 {@link CheckSubCompanyRight#getDecentralizationAttr(int, String, int, int, int)}
 *       取“是否可看全部（isall）/ 范围是部门级还是分部级 / 部门ID集合 / 分部ID集合”，
 *       与平台的 {@code DepartmentBrowserByDec.jsp} / {@code ResourceBrowserByRight.jsp} 同一口径。</li>
 * </ol>
 *
 * <p>三类候选各自对应一个 E9 分权权限点（默认值见下，可在 oaones.properties 覆盖）：</p>
 * <ul>
 *   <li>分部：{@code oa.sec.right.sub}，默认 {@code Subcompanys:decentralization}；</li>
 *   <li>部门：{@code oa.sec.right.dept}，默认 {@code Departments:decentralization}；</li>
 *   <li>人员：{@code oa.sec.right.user}，默认 {@code Resources:decentralization}。</li>
 * </ul>
 *
 * <p><b>OA 没开任何分权时不过滤</b>（保持模块原有“全量候选”的行为）；
 * {@code oa.sec.force} 可强制开 / 关。系统管理员（user.id=1）、不在任何“应用分权”
 * 范围内的账号、以及“可查看全部分部”的管理员，按平台口径都是“可看全部”。</p>
 *
 * <p>后台同步（企微 ⇄ OA）<b>不走本类</b>：同步由模块内置调度器按配置执行，
 * 没有登录人上下文，仍按全量组织读写（见 {@code OaServiceImpl} 的无 operatorId 重载）。</p>
 */
public final class OaonesHrmScope {

    private OaonesHrmScope() {
    }

    /** 分权范围：{@code all}=不需要过滤（分权未开 / 可看全部）。 */
    private static final class Scope {
        static final Scope ALL = new Scope(true, null, false,
                Collections.<String>emptySet(), Collections.<String>emptySet(),
                Collections.<String>emptySet());
        final boolean all;
        /** 应用分权（非 null 时按它判断，见 {@link #visibleDept} / {@link #visibleUser}）。 */
        final AppDetachComInfo appDetach;
        /** true=范围是“部门级”（按 {@link #deptIds} 过滤）；false=范围是“分部级”（按 {@link #subIds} 过滤）。 */
        final boolean deptBased;
        final Set<String> deptIds;
        final Set<String> subIds;
        /** 应用分权：范围里“已授权分部”（含下级），用于快速放行；仅可查看的上级不放进来。 */
        final Set<String> appGrantSubIds;

        Scope(boolean all, AppDetachComInfo appDetach, boolean deptBased,
              Set<String> deptIds, Set<String> subIds, Set<String> appGrantSubIds) {
            this.all = all;
            this.appDetach = appDetach;
            this.deptBased = deptBased;
            this.deptIds = deptIds;
            this.subIds = subIds;
            this.appGrantSubIds = appGrantSubIds;
        }

        /** 候选部门是否可见。 */
        boolean visibleDept(String deptId, String subCompanyId) {
            if (all) {
                return true;
            }
            String dept = OaonesUtil.s(deptId).trim();
            String sub = OaonesUtil.s(subCompanyId).trim();
            if (appDetach != null) {
                // 与平台 checkUserAppDetach(dept,"3") 一致：该部门在范围内，或其所属分部在范围内
                return appGrantSubIds.contains(sub) || appDetach.checkUserAppDetach(dept, "3") > 0;
            }
            if (deptBased) {
                return deptIds.contains(dept);
            }
            return subIds.contains(sub);
        }

        /** 候选人员是否可见（{@code userId} 只在应用分权时用到）。 */
        boolean visibleUser(String userId, String deptId, String subCompanyId) {
            if (all) {
                return true;
            }
            String user = OaonesUtil.s(userId).trim();
            if (appDetach != null) {
                if (appGrantSubIds.contains(OaonesUtil.s(subCompanyId).trim())) {
                    return true;
                }
                return appDetach.checkUserAppDetach(user, "1") > 0;
            }
            return visibleDept(deptId, subCompanyId);
        }

        /** 候选分部是否可见。 */
        boolean visibleSub(String subCompanyId) {
            if (all) {
                return true;
            }
            String sub = OaonesUtil.s(subCompanyId).trim();
            if (appDetach != null) {
                // 与平台 checkUserAppDetach(sub,"2") 一致：1=有权限 / 2=仅可查看（组织树的上级路径）
                return appGrantSubIds.contains(sub) || appDetach.checkUserAppDetach(sub, "2") > 0;
            }
            return subIds.contains(sub);
        }
    }

    /** 过滤“OA 分部”候选（{@code id}）。 */
    public static JSONArray filterSubCompanies(JSONArray rows, String operatorId) {
        Scope scope = subScope(operatorId);
        if (scope.all || rows == null) {
            return rows;
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row != null && scope.visibleSub(row.getString("id"))) {
                out.add(row);
            }
        }
        return out;
    }

    /** 过滤“OA 部门”候选（{@code id} + {@code subcompanyid1}），部门级范围按部门ID、分部级范围按所属分部。 */
    public static JSONArray filterDepartments(JSONArray rows, String operatorId) {
        Scope scope = deptScope(operatorId);
        if (scope.all || rows == null) {
            return rows;
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row != null && scope.visibleDept(row.getString("id"), row.getString("subcompanyid1"))) {
                out.add(row);
            }
        }
        return out;
    }

    /** 过滤“OA 人员”候选（{@code departmentid} + {@code subcompanyid1}）。 */
    public static JSONArray filterUsers(JSONArray rows, String operatorId) {
        Scope scope = userScope(operatorId);
        if (scope.all || rows == null) {
            return rows;
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row != null && scope.visibleUser(row.getString("id"),
                    row.getString("departmentid"), row.getString("subcompanyid1"))) {
                out.add(row);
            }
        }
        return out;
    }

    private static Scope subScope(String operatorId) {
        return scopeOf(operatorId, OaonesProps.secRightSub());
    }

    private static Scope deptScope(String operatorId) {
        return scopeOf(operatorId, OaonesProps.secRightDept());
    }

    private static Scope userScope(String operatorId) {
        return scopeOf(operatorId, OaonesProps.secRightUser());
    }

    /**
     * 取登录人在某个 E9 分权权限点下的可见范围。
     *
     * <p>取不到（平台异常）时按“本人所属部门 / 分部”兜底，避免异常时把全量组织下发给前端；
     * 连本人所属组织都取不到时才退回“不过滤”，避免把模块整体锁死。</p>
     */
    private static Scope scopeOf(String operatorId, String rightStr) {
        int uid = Util.getIntValue(OaonesUtil.s(operatorId), 0);
        if (uid <= 0) {
            return Scope.ALL;
        }
        String force = OaonesProps.secForce();
        if ("0".equals(force)) {
            return Scope.ALL;
        }
        // ① 应用分权（E9 后台「分权管理 → 应用分权」）：与平台自己的组织/人员选择框同一口径
        Scope appScope = appScope(uid);
        if (appScope != null) {
            return appScope;
        }
        // ② 管理分权 / 角色分权：分权没开时不过滤（保持模块原有“全量候选”行为）
        if (!"1".equals(force) && !detachableOn(rightStr)) {
            return Scope.ALL;
        }
        try {
            CheckSubCompanyRight right = new CheckSubCompanyRight();
            right.setDetachable(1);
            right.setIsbill(1);
            boolean onlySelfDept = right.getDecentralizationAttr(uid, rightStr, 0, 0, 1);
            if (right.getIsall()) {
                return Scope.ALL;
            }
            Set<String> deptIds = splitIds(right.getDepartmentids());
            Set<String> subIds = splitIds(right.getSubcompanyids());
            if (onlySelfDept) {
                // 部门级范围：补出这些部门所属的分部，供“分部”候选与组织树定位
                DepartmentComInfo dci = new DepartmentComInfo();
                for (String deptId : deptIds) {
                    String subId = OaonesUtil.s(dci.getSubcompanyid1(deptId));
                    if (subId.length() > 0) {
                        subIds.add(subId);
                    }
                }
            }
            OaonesLog.step("OA 分权范围 right=" + rightStr + " user=" + uid
                    + " deptBased=" + onlySelfDept
                    + " depts=" + deptIds.size() + " subs=" + subIds.size());
            return new Scope(false, null, onlySelfDept,
                    Collections.unmodifiableSet(deptIds), Collections.unmodifiableSet(subIds),
                    Collections.<String>emptySet());
        } catch (Throwable t) {
            OaonesLog.error("读取 OA 分权范围失败 right=" + rightStr + " user=" + uid, t);
            return ownScope(uid);
        }
    }

    /**
     * 应用分权范围（{@code SystemSet.appdetachable=1} 时生效）。
     *
     * <p>与平台 {@code hrm/resource/*Browser*.jsp}、{@code hrm/company/*DepartmentBrowser*.jsp}
     * 同一口径：登录人若不是任何“应用分权”的成员（或本来就无需分权，例如系统管理员、
     * 客户账号），平台返回“可看全部”，这里返回 {@code null} 表示“本轮不按应用分权过滤”。</p>
     *
     * @return 需要过滤时返回范围；不需要（含未开启、无需分权、读取异常）返回 {@code null}
     */
    private static Scope appScope(int uid) {
        try {
            AppDetachComInfo adci = new AppDetachComInfo();
            if (!adci.isUseAppDetach()) {
                return null;
            }
            adci.loadUserAppDetach(String.valueOf(uid));
            if (adci.isNotCheckUserAppDetach()) {
                return null;
            }
            // 只取“已授权分部（含下级）”做快速放行；仅可查看的上级由 checkUserAppDetach 判定
            // （不能把可查看的上级也算进快速放行，否则其下部门会被误放行）
            Set<String> subs = new LinkedHashSet<String>();
            addAll(subs, adci.getAlllowsubcompany());
            OaonesLog.step("OA 应用分权范围 user=" + uid
                    + " subs=" + subs.size()
                    + " depts=" + size(adci.getAlllowdepartment())
                    + " resources=" + size(adci.getAlllowresource()));
            return new Scope(false, adci, false,
                    Collections.<String>emptySet(), Collections.<String>emptySet(),
                    Collections.unmodifiableSet(subs));
        } catch (Throwable t) {
            OaonesLog.error("读取 OA 应用分权范围失败 user=" + uid, t);
            return null;
        }
    }

    /** 把平台分权范围列表并入集合（跳过空值与虚拟组织负数ID）。 */
    private static void addAll(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : new ArrayList<String>(values)) {
            String id = OaonesUtil.s(value).trim();
            if (id.length() > 0 && !"0".equals(id)) {
                target.add(id);
            }
        }
    }

    private static int size(List<String> values) {
        return values == null ? 0 : new ArrayList<String>(values).size();
    }

    /** 异常兜底：只放行本人所属部门 / 分部（两者都取不到时返回不过滤）。 */
    private static Scope ownScope(int uid) {
        try {
            ResourceComInfo rci = new ResourceComInfo();
            String deptId = OaonesUtil.s(rci.getDepartmentID(String.valueOf(uid))).trim();
            String subId = OaonesUtil.s(rci.getSubCompanyID(String.valueOf(uid))).trim();
            // 未设置部门 / 分部时缓存取到的是 "0"，按空处理
            if ("0".equals(deptId)) {
                deptId = "";
            }
            if ("0".equals(subId)) {
                subId = "";
            }
            if (deptId.length() == 0 && subId.length() == 0) {
                return Scope.ALL;
            }
            Set<String> deptIds = new LinkedHashSet<String>();
            if (deptId.length() > 0) {
                deptIds.add(deptId);
            }
            Set<String> subIds = new LinkedHashSet<String>();
            if (subId.length() > 0) {
                subIds.add(subId);
            }
            return new Scope(false, null, deptId.length() > 0, deptIds, subIds,
                    Collections.<String>emptySet());
        } catch (Throwable t) {
            OaonesLog.error("读取 OA 分权兜底范围失败 user=" + uid, t);
            return Scope.ALL;
        }
    }

    /**
     * OA 是否开了分权：{@code SystemSet.detachable=1}（人力资源分权总开关，
     * 与 {@code hrm/company/HrmCompany_frm.jsp} 读的是同一个字段），
     * 或该权限点自己在 {@code SystemRightDetail} 上配了分权。
     */
    private static boolean detachableOn(String rightStr) {
        try {
            RecordSet rs = new RecordSet();
            rs.setChecksql(false);
            if (rs.executeSql("select detachable from SystemSet") && rs.next() && rs.getInt(1) == 1) {
                return true;
            }
        } catch (Throwable t) {
            OaonesLog.error("读取 SystemSet.detachable 失败", t);
        }
        try {
            return new CheckSubCompanyRight().getDetachableByRight(rightStr);
        } catch (Throwable t) {
            OaonesLog.error("读取权限点分权开关失败 right=" + rightStr, t);
        }
        return false;
    }

    /** 逗号 / 分号 / 空白分隔的ID串 → 去重集合（忽略空值与 0）。 */
    private static Set<String> splitIds(String ids) {
        Set<String> out = new LinkedHashSet<String>();
        String text = OaonesUtil.s(ids).trim();
        if (text.length() == 0) {
            return out;
        }
        for (String id : text.split("[,;\\s]+")) {
            String value = id.trim();
            if (value.length() > 0 && !"0".equals(value)) {
                out.add(value);
            }
        }
        return out;
    }
}
