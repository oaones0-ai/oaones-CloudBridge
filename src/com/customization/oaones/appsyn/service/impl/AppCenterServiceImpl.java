package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.engine.hrm.util.HrmRoleMemberUtil;
import com.customization.oaones.appsyn.appsso.AppSsoContext;
import com.customization.oaones.appsyn.appsso.AppSsoHandler;
import com.customization.oaones.appsyn.appsso.AppSsoRegistry;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesImageSize;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesModeRight;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.AppCenterMapper;
import com.customization.oaones.appsyn.dto.AppInfoRow;
import com.customization.oaones.appsyn.entity.AppInfo;
import com.customization.oaones.appsyn.service.AppCenterService;
import com.customization.oaones.appsyn.service.impl.wecom.LoginConfigServiceImpl;
import weaver.conn.RecordSet;
import weaver.general.GCONST;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.company.SubCompanyComInfo;
import weaver.hrm.resource.ResourceComInfo;
import weaver.hrm.roles.RolesComInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 应用中心实现（DAO 层为 {@link AppCenterMapper}）。
 *
 * <p>「应用中心」没有租户维度：一套部署一份应用列表，
 * 页面准入（能进本页、能新建应用）= 系统管理员（user.id=1）
 * + {@code oaones.properties#appc.admin.ids} 中列出的人员
 * + 被指定为某个应用创建人 / 维护人的人员（见 {@link #hasOwnApp}）；
 * 具体某个应用则由**创建人 + 维护人**维护（见 {@link #belongsTo}），
 * 系统管理员可查看与维护**全部**应用，其它登录人只看得到自己创建或自己维护的应用；
 * 桌面启动台（{@code /oaones/appCenter/Desktop.jsp}）对本模块任何登录人只读可用。</p>
 */
public class AppCenterServiceImpl implements AppCenterService {

    /** 应用名称 / 地址 / 类的长度上限（与建表语句的列宽对齐）。 */
    private static final int NAME_MAX = 200;
    private static final int URL_MAX = 500;
    private static final int CLASS_MAX = 300;
    private static final int METHOD_MAX = 200;
    private static final int REMARK_MAX = 500;
    /**
     * 维护人字段的长度上限（与建表语句 maintainer_ids 的列宽对齐）。
     *
     * <p>按“逗号分隔的人员ID”计，2000 字符约可放 300 人（远大于页面选择框一次全选的 200 人候选），
     * 超过时**直接拒绝保存并提示**，不做静默截断——静默截断会让人以为选了却少存几个维护人。</p>
     */
    private static final int MAINTAINER_MAX = 2000;

    /** 应用状态：1 启用（桌面可见、可跳转）。 */
    private static final int OAONES_ENABLE = 1;
    /** 应用状态：0 停用（桌面不出现，直接访问也会被拒绝）。 */
    private static final int OAONES_DISABLED = 0;

    /** 打开方式：0 默认（在当前页面 / 框架内打开，外层 OA 框架不动，不是整个页面刷新）。 */
    private static final int OAONES_OPEN_CURRENT = 0;
    /** 打开方式：1 新标签页（浏览器新开一个标签页打开）。 */
    private static final int OAONES_OPEN_NEW_TAB = 1;
    /** 打开方式：2 原标签页（整个页面 reload：当前浏览器标签页整体换成该应用）。 */
    private static final int OAONES_OPEN_TOP = 2;

    /** 生成上传文件名用的随机十六进制字符。 */
    private static final String ICON_NAME_HEX = "0123456789abcdef";

    private String table() {
        return OaonesProps.appTable();
    }

    /* ------------------------------------------------------------------
     * 权限
     * ------------------------------------------------------------------ */

    @Override
    public boolean canMaintain(String operatorId) {
        // 页面准入：能进本页、能新建应用的人（具体某个应用能不能维护见 belongsTo / assertAppMaintain）。
        // 三类人可进：系统管理员、oaones.properties#appc.admin.ids 名单，以及
        // **被指定为某个应用创建人 / 维护人**的人（否则把应用交给不在名单里的人维护时，他连本页都进不来）
        String uid = OaonesUtil.s(operatorId).trim();
        if (uid.length() == 0) {
            return false;
        }
        if (isSysAdmin(uid)) {
            return true;
        }
        String admins = OaonesUtil.s(OaonesProps.appcAdminIds());
        String[] parts = admins.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (uid.equals(parts[i].trim())) {
                return true;
            }
        }
        return hasOwnApp(uid);
    }

    @Override
    public boolean canMaintainGopage(String operatorId) {
        return new LoginConfigServiceImpl().canMaintainGopage(operatorId);
    }

    @Override
    public JSONObject gopageSettings(String operatorId) {
        if (!canMaintainGopage(operatorId)) {
            throw new RuntimeException("你不是「应用中心」的管理员，无权查看启动台自动跳转设置"
                    + "（仅系统管理员与 appc.admin.ids 中的人员可维护）");
        }
        return new LoginConfigServiceImpl().gopageSettings();
    }

    @Override
    public JSONObject saveGopageSeconds(String seconds, String operatorId) {
        return new LoginConfigServiceImpl().saveGopageSeconds(seconds, operatorId);
    }

    private void assertMaintain(String operatorId) {
        if (!canMaintain(operatorId)) {
            throw new RuntimeException("你不是「应用中心」的维护人，无权进入应用中心（可用的是：系统管理员、"
                    + "oaones.properties#appc.admin.ids 中列出的人员，以及被指定为某个应用创建人/维护人的人员）");
        }
    }

    /**
     * 名下是否有应用（自己创建的，或自己是维护人的）：应用中心的“页面准入”兜底。
     *
     * <p>应用清单量级很小（一套部署几十条），这里直接复用列表查询在内存里判断，
     * 与 {@link #list} 的归属口径（{@link #belongsTo}）完全一致，不额外维护第二套 SQL 条件。</p>
     */
    private boolean hasOwnApp(String uid) {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("appTable", table());
        params.put("keyword", "");
        List<AppInfoRow> rows = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.listRows(params));
        if (rows == null) {
            return false;
        }
        for (AppInfoRow row : rows) {
            if (belongsTo(row, uid)) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------
     * 维护人（应用级分权）：应用由“创建人 + 维护人”维护，系统管理员看全部
     * ------------------------------------------------------------------ */

    /** 是否拥有「应用中心」的全局管理权限：系统管理员（user.id = 1）或超管角色（total.admin.roleid）。 */
    private boolean isSysAdmin(String userId) {
        return OaonesAdmin.isTotalAdmin(userId);
    }

    /** 逗号分隔的 OA 对象ID串（维护人 / 可见范围）拆分为有序的ID列表。 */
    private List<String> idList(String raw) {
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

    /**
     * 维护人字段归一化（可见范围三个字段同口径，只是标签不同）。
     */
    private String normalizeMaintainers(String raw) {
        return normalizeIdList(raw, "维护人");
    }

    /**
     * 逗号分隔的 OA 对象ID串归一化：只保留正整数ID，去重并保持先后顺序，逗号分隔；
     * 超过列宽（{@link #MAINTAINER_MAX}）时抛错提示（**不静默丢人**：静默截断会让人以为选了却少存几个）。
     *
     * @param raw   页面提交的原始串（可含全角逗号 / 分号 / 空格分隔）
     * @param label 字段名（用于超长时的提示，如“维护人”“可见范围·分部”）
     */
    private String normalizeIdList(String raw, String label) {
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
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            int next = sb.length() + (sb.length() == 0 ? 0 : 1) + id.length();
            if (next > MAINTAINER_MAX) {
                throw new RuntimeException(label + "过多（已超过 " + MAINTAINER_MAX
                        + " 字符上限，约 " + (MAINTAINER_MAX / 11) + " 个），请减少后再保存");
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** 判断维护人字段（逗号分隔人员ID）中是否包含指定用户。 */
    private boolean isMaintainer(String maintainerIds, String userId) {
        String uid = OaonesUtil.s(userId).trim();
        if (uid.length() == 0) {
            return false;
        }
        for (String id : idList(maintainerIds)) {
            if (id.equals(uid)) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------
     * 可见范围（角色 / 分部 / 部门 / 人员四类，满足其一即可见）
     * ------------------------------------------------------------------ */

    /** 登录人的组织归属（人员ID + 主部门 + 主分部）与角色ID：可见范围按这些归属判定。 */
    private static final class ViewerOrg {
        private String userId = "";
        private String deptId = "";
        private String subId = "";
        /** 登录人所属角色；null = 尚未读取（只有遇到按角色配置的应用时才算）。 */
        private Set<String> roleIds;
    }

    /**
     * 读登录人的主部门 / 主分部（E9 人员缓存）与所属角色（hrmrolemembers）；
     * 读不到时留空，只按其余条件判定。
     */
    private ViewerOrg viewerOrg(String operatorId) {
        ViewerOrg viewer = new ViewerOrg();
        int uid = OaonesUtil.i(OaonesUtil.s(operatorId).trim(), 0);
        if (uid <= 0) {
            return viewer;
        }
        viewer.userId = String.valueOf(uid);
        try {
            ResourceComInfo rci = new ResourceComInfo();
            viewer.deptId = OaonesUtil.s(rci.getDepartmentID(viewer.userId)).trim();
            viewer.subId = OaonesUtil.s(rci.getSubCompanyID(viewer.userId)).trim();
        } catch (Throwable t) {
            OaonesLog.error("读取登录人主部门/主分部失败（可见范围判定）uid=" + uid, t);
        }
        return viewer;
    }

    /** 登录人所属角色（按需读取一次并缓存到 ViewerOrg）。 */
    private Set<String> viewerRoles(ViewerOrg viewer) {
        if (viewer.roleIds == null) {
            viewer.roleIds = viewerRoleIds(viewer);
        }
        return viewer.roleIds;
    }

    /**
     * 登录人所属的 OA 角色ID：条件由平台标准工具
     * {@link HrmRoleMemberUtil#getHrmRoleMembers_queryConditionSql(int, String)} 生成，
     * 覆盖角色里的直接人员、分部 / 部门成员（含安全级别区间）与岗位成员，
     * 不在本模块重写角色成员规则。
     */
    private Set<String> viewerRoleIds(ViewerOrg viewer) {
        Set<String> ids = new LinkedHashSet<String>();
        if (viewer == null || viewer.userId.length() == 0) {
            return ids;
        }
        try {
            String condition = HrmRoleMemberUtil.getHrmRoleMembers_queryConditionSql(
                    OaonesUtil.i(viewer.userId, 0), "m");
            String sql = "select distinct m.roleid from hrmrolemembers m where " + condition;
            RecordSet rs = new RecordSet();
            rs.executeSql(sql);
            while (rs.next()) {
                String roleId = OaonesUtil.s(rs.getString("roleid")).trim();
                if (roleId.matches("\\d+")) {
                    ids.add(roleId);
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("读取登录人所属角色失败（可见范围判定）uid=" + viewer.userId, t);
        }
        return ids;
    }

    /**
     * 应用对某登录人是否可见（工作台/桌面启动台的展示与打开都用这一套）：
     * 勾选**全员可见**（vis_all = 1）= 不控制权限，所有登录人都可见；
     * 未勾选时看角色 / 分部 / 部门 / 人员四类，**满足其一即可见**；四类都没设置 = **无权限**
     * （工作台上谁都看不到，创建人 / 维护人 / 系统管理员走 {@link #canMaintainApp} 单独放行，
     * 便于验证自己维护的应用）。
     *
     * <p><b>只按“当前分部 / 当前部门”判定，不含下级组织</b>：选了某部门，只有**主部门正好是它**的员工命中，
     * 其下级部门的员工不在范围内（要覆盖下级就把那些下级一并选上）；分部同理——用户按
     * {@code hrmresource.departmentid} / {@code hrmresource.subcompanyid1} 的主归属比。</p>
     */
    private boolean visibleTo(AppInfo app, ViewerOrg viewer) {
        if (app == null) {
            return false;
        }
        if (isVisAll(app)) {
            // 勾选“全员可见”= 不控制权限：工作台上所有登录人都能看到这个应用（四类范围不再参与判定）
            return true;
        }
        List<String> roles = idList(app.getVisRoleIds());
        List<String> subs = idList(app.getVisSubIds());
        List<String> depts = idList(app.getVisDeptIds());
        List<String> users = idList(app.getVisUserIds());
        if (roles.isEmpty() && subs.isEmpty() && depts.isEmpty() && users.isEmpty()) {
            // 未勾选“全员可见”且四类都没设置 = 无权限：工作台上谁都看不到
            // （维护人 / 创建人 / 系统管理员另走 canMaintainApp）
            return false;
        }
        if (viewer == null) {
            return false;
        }
        if (viewer.userId.length() > 0 && users.contains(viewer.userId)) {
            return true;
        }
        if (!roles.isEmpty()) {
            Set<String> viewerRoleIds = viewerRoles(viewer);
            for (String roleId : roles) {
                if (viewerRoleIds.contains(roleId)) {
                    return true;
                }
            }
        }
        if (viewer.deptId.length() > 0 && depts.contains(viewer.deptId)) {
            return true;
        }
        return viewer.subId.length() > 0 && subs.contains(viewer.subId);
    }

    /** 该应用是否勾选了“全员可见”（vis_all = 1；库里为 NULL 的历史行按 0 处理）。 */
    private boolean isVisAll(AppInfo app) {
        return app != null && app.getVisAll() != null && app.getVisAll().intValue() == 1;
    }

    /** “全员可见”的入库值（0 / 1）：NULL 与非法值都按 0。 */
    private int visAllOf(AppInfo app) {
        return isVisAll(app) ? 1 : 0;
    }

    /** 页面提交的“全员可见”解析：只认 1 / true，其余（含空串、未提交）按 0 不勾选处理。 */
    private int parseVisAll(String value) {
        String v = OaonesUtil.s(value).trim();
        return ("1".equals(v) || "true".equalsIgnoreCase(v)) ? 1 : 0;
    }

    /**
     * 这条应用是否“归”该登录人：**创建人本人**（modedatacreater，天然可维护，不用重复填进维护人），
     * 或**维护人名单**里有他。系统管理员不走这里，由 {@link #canMaintainApp} / {@link #assertAppMaintain} 单独放行。
     */
    private boolean belongsTo(AppInfo app, String userId) {
        String uid = OaonesUtil.s(userId).trim();
        if (app == null || uid.length() == 0) {
            return false;
        }
        String creator = OaonesUtil.nullToEmpty(app.getModedatacreater()).trim();
        if (creator.length() > 0 && creator.equals(uid)) {
            return true;
        }
        return isMaintainer(app.getMaintainerIds(), uid);
    }

    /** 当前登录人能否维护这条应用：创建人 / 维护人 / 系统管理员。 */
    private boolean canMaintainApp(AppInfo app, String operatorId) {
        return isSysAdmin(operatorId) || belongsTo(app, operatorId);
    }

    /** 改、启停、删除前的兜底校验：只能动自己创建或自己维护的应用（系统管理员例外）。 */
    private void assertAppMaintain(AppInfo app, String operatorId) {
        if (app == null) {
            throw new RuntimeException("应用记录不存在或已删除");
        }
        if (canMaintainApp(app, operatorId)) {
            return;
        }
        throw new RuntimeException("只能维护自己创建或作为维护人的应用：应用「"
                + OaonesUtil.s(app.getAppName()) + "」的创建人与维护人都不是你（系统管理员可维护全部应用）");
    }

    /** 人员ID → 姓名（E9 人员缓存，一次遍历；查不到的ID不返回，页面按“ID xxx”展示）。 */
    private Map<String, String> userNameMap(Collection<String> ids) {
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
            // 姓名解析失败不影响列表（页面按人员ID展示）
            OaonesLog.error("读取创建人/维护人姓名失败，页面按人员ID展示", t);
        }
        return names;
    }

    /** 部门ID → 部门简称（为空时退回部门全称）；部门缓存的字段口径与 `user.orgOptions` 一致。 */
    private Map<String, String> deptNameMap(Collection<String> ids) {
        Map<String, String> names = new HashMap<String, String>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        Set<String> wanted = new LinkedHashSet<String>(ids);
        try {
            DepartmentComInfo dci = new DepartmentComInfo();
            dci.setTofirstRow();
            while (dci.next() && !wanted.isEmpty()) {
                String id = OaonesUtil.s(dci.getDepartmentid());
                if (wanted.contains(id) && !names.containsKey(id)) {
                    String mark = OaonesUtil.s(dci.getDepartmentmark());
                    names.put(id, mark.length() > 0 ? mark : OaonesUtil.s(dci.getDepartmentname()));
                    wanted.remove(id);
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("读取部门名称失败，页面按部门ID展示", t);
        }
        return names;
    }

    /** 分部ID → 分部名称（E9 分部缓存）。 */
    private Map<String, String> subNameMap(Collection<String> ids) {
        Map<String, String> names = new HashMap<String, String>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        Set<String> wanted = new LinkedHashSet<String>(ids);
        try {
            SubCompanyComInfo sci = new SubCompanyComInfo();
            sci.setTofirstRow();
            while (sci.next() && !wanted.isEmpty()) {
                String id = OaonesUtil.s(sci.getSubCompanyid());
                if (wanted.contains(id) && !names.containsKey(id)) {
                    names.put(id, OaonesUtil.s(sci.getSubCompanyname()));
                    wanted.remove(id);
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("读取分部名称失败，页面按分部ID展示", t);
        }
        return names;
    }

    /** 角色ID → 角色名称（E9 RolesComInfo 缓存）。 */
    private Map<String, String> roleNameMap(Collection<String> ids) {
        Map<String, String> names = new HashMap<String, String>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        try {
            RolesComInfo rci = new RolesComInfo();
            for (String id : ids) {
                String name = OaonesUtil.s(rci.getRolesname(id)).trim();
                if (name.length() > 0) {
                    names.put(id, name);
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("读取角色名称失败，页面按角色ID展示", t);
        }
        return names;
    }

    /** 四类 OA 对象的“ID → 名称”缓存（角色 / 人员 / 部门 / 分部）：整页一次性解析，避免逐行查缓存。 */
    private static final class OrgNames {
        private final Map<String, String> roles = new HashMap<String, String>();
        private final Map<String, String> users = new HashMap<String, String>();
        private final Map<String, String> depts = new HashMap<String, String>();
        private final Map<String, String> subs = new HashMap<String, String>();
    }

    /** 汇总这些应用行里出现的 创建人 / 维护人 / 可见范围角色、人员、部门、分部 ID，一次性解析成名称。 */
    private OrgNames orgNames(List<? extends AppInfo> rows) {
        Set<String> roleIds = new LinkedHashSet<String>();
        Set<String> userIds = new LinkedHashSet<String>();
        Set<String> deptIds = new LinkedHashSet<String>();
        Set<String> subIds = new LinkedHashSet<String>();
        if (rows != null) {
            for (AppInfo row : rows) {
                roleIds.addAll(idList(row.getVisRoleIds()));
                userIds.addAll(idList(row.getMaintainerIds()));
                userIds.addAll(idList(row.getVisUserIds()));
                String creator = OaonesUtil.nullToEmpty(row.getModedatacreater()).trim();
                if (creator.length() > 0) {
                    userIds.add(creator);
                }
                deptIds.addAll(idList(row.getVisDeptIds()));
                subIds.addAll(idList(row.getVisSubIds()));
            }
        }
        OrgNames names = new OrgNames();
        names.roles.putAll(roleNameMap(roleIds));
        names.users.putAll(userNameMap(userIds));
        names.depts.putAll(deptNameMap(deptIds));
        names.subs.putAll(subNameMap(subIds));
        return names;
    }

    /**
     * 可见范围 · 角色候选：OA 标准角色表 hrmroles，按 ID 升序。
     * 名称优先取角色标记 rolesmark（列表展示口径），为空时退回 rolesname。
     */
    @Override
    public List<Map<String, Object>> roles(String operatorId) {
        assertMaintain(operatorId);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        RecordSet rs = new RecordSet();
        SubCompanyComInfo subComInfo = new SubCompanyComInfo();
        rs.executeSql("select id, rolesmark, rolesname, subcompanyid from hrmroles where id > 0 order by id");
        while (rs.next()) {
            String id = OaonesUtil.s(rs.getString("id")).trim();
            if (!id.matches("\\d+")) {
                continue;
            }
            String mark = OaonesUtil.s(rs.getString("rolesmark")).trim();
            String roleName = OaonesUtil.s(rs.getString("rolesname")).trim();
            String name = mark.length() > 0 ? mark : roleName;
            if (name.length() == 0) {
                name = "角色 " + id;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", id);
            row.put("name", name);
            row.put("mark", mark);
            row.put("role_name", roleName);
            String subcompanyId = OaonesUtil.s(rs.getString("subcompanyid")).trim();
            row.put("subcompanyid", subcompanyId);
            String subcompanyName = "";
            if (subcompanyId.length() > 0) {
                try {
                    subcompanyName = OaonesUtil.s(subComInfo.getSubCompanyname(subcompanyId));
                } catch (Throwable ignored) {
                    // 分部名称只是展示辅助，取不到时页面按角色编号展示
                }
            }
            row.put("subcompanyname", subcompanyName);
            result.add(row);
        }
        return result;
    }

    /** [{id,name}] 明细，供页面渲染标签 / 角色·人员·部门·分部链接（名称缺失时页面回退成“ID xxx”）。 */
    private JSONArray idNameJson(Collection<String> ids, Map<String, String> names) {
        JSONArray list = new JSONArray();
        if (ids == null) {
            return list;
        }
        for (String id : ids) {
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
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(nameOrId(item));
        }
        return sb.toString();
    }

    /** 姓名缺失（人员已删除 / 缓存里查不到）时回退成“ID xxx”，避免显示空白。 */
    private String nameOrId(JSONObject item) {
        String name = OaonesUtil.s(item.getString("name"));
        return name.length() == 0 ? "ID " + OaonesUtil.s(item.getString("id")) : name;
    }

    /* ------------------------------------------------------------------
     * 查询
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject list(String keyword, String operatorId) {
        assertMaintain(operatorId);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("appTable", table());
        String k = OaonesUtil.s(keyword).trim();
        params.put("keyword", k.length() == 0 ? "" : "%" + k + "%");
        List<AppInfoRow> rows = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.listRows(params));
        String uid = OaonesUtil.s(operatorId).trim();
        boolean sysAdmin = isSysAdmin(uid);
        // 可见范围：系统管理员看全部；其它登录人只看“自己创建的 + 自己是维护人的”应用
        List<AppInfoRow> visible = new ArrayList<AppInfoRow>();
        if (rows != null) {
            for (AppInfoRow row : rows) {
                if (!sysAdmin && !belongsTo(row, uid)) {
                    continue;
                }
                visible.add(row);
            }
        }
        OrgNames names = orgNames(visible);
        JSONArray apps = new JSONArray();
        for (AppInfoRow row : visible) {
            apps.add(toJson(row, names));
        }
        JSONObject data = new JSONObject();
        data.put("apps", apps);
        data.put("canMaintain", true);
        data.put("sysAdmin", Boolean.valueOf(sysAdmin));
        data.put("ssoInterface", AppSsoHandler.class.getName());
        OaonesLog.step("应用列表 operatorId=" + uid + " 全部=" + (rows == null ? 0 : rows.size())
                + " 可见=" + visible.size() + (sysAdmin ? "（系统管理员，全部可见）" : "（仅创建人/维护人可见）"));
        return data;
    }

    @Override
    public List<Map<String, Object>> desktopApps(String operatorId) {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("appTable", table());
        params.put("keyword", "");
        params.put("enable", Integer.valueOf(OAONES_ENABLE));
        List<AppInfoRow> rows = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.listRows(params));
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (rows == null) {
            return result;
        }
        // 可见范围：勾选“全员可见”的全部下发；否则四类满足其一才下发；
        // 维护人 / 创建人 / 系统管理员不受范围限制（要能验证自己维护的应用）
        ViewerOrg viewer = viewerOrg(operatorId);
        boolean sysAdmin = isSysAdmin(operatorId);
        for (AppInfoRow row : rows) {
            if (!visibleTo(row, viewer) && !sysAdmin && !belongsTo(row, operatorId)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", row.getId() == null ? "" : String.valueOf(row.getId()));
            item.put("name", OaonesUtil.s(row.getAppName()));
            item.put("url", OaonesUtil.s(row.getAppUrl()));
            item.put("icon", OaonesUtil.s(row.getIcon()));
            // 默认文字图标的基准（icon 为空时用）：桌面脚本按它算首字与底色，
            // 保存时已固化，所以改名不会让图标跟着变（见 resolveIconSeed）
            item.put("iconSeed", OaonesUtil.s(row.getIconSeed()));
            // 打开方式（0 默认 / 1 新标签页 / 2 原标签页）：桌面脚本按它决定怎么打开生成出来的地址
            item.put("openMode", Integer.valueOf(openModeOf(row.getOpenMode())));
            // 是否走单点登录：由“单点登录实现类”是否配置决定，桌面图标上给一个小标记
            String ssoClass = OaonesUtil.s(row.getSsoClass()).trim();
            item.put("sso", Boolean.valueOf(ssoClass.length() > 0));
            item.put("ssoClass", ssoClass);
            item.put("remark", OaonesUtil.s(row.getRemark()));
            result.add(item);
        }
        return result;
    }

    /* ------------------------------------------------------------------
     * 维护
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        assertMaintain(operatorId);
        String id = OaonesUtil.s(params.getString("id")).trim();
        String appName = OaonesUtil.s(params.getString("app_name")).trim();
        String appUrl = OaonesUtil.s(params.getString("app_url")).trim();
        String ssoClass = OaonesUtil.s(params.getString("sso_class")).trim();
        String ssoMethod = OaonesUtil.s(params.getString("sso_method")).trim();
        String icon = OaonesUtil.s(params.getString("icon")).trim();
        String iconSeedIn = OaonesUtil.s(params.getString("icon_seed")).trim();
        String openModeIn = OaonesUtil.s(params.getString("open_mode")).trim();
        // 维护人：可多选 OA 员工（逗号分隔的人员ID）；请求没带该参数时（老页面 / 直接调接口）保留原值
        boolean maintainerProvided = params.containsKey("maintainer_ids");
        String maintainerIds = normalizeMaintainers(params.getString("maintainer_ids"));
        // 可见范围：全员可见开关 + 角色 / 分部 / 部门 / 人员四类各自多选（未勾选全员可见时四类满足其一即可见）；
        // 请求没带这些参数时（老页面 / 直接调接口）保留原值——开关与四类各自判断，
        // 免得「只调 vis_all」把已有的四类范围冲掉（取消勾选时还要按它们判定）
        boolean visAllProvided = params.containsKey("vis_all");
        boolean visRoleProvided = params.containsKey("vis_role_ids");
        boolean visSubProvided = params.containsKey("vis_sub_ids");
        boolean visDeptProvided = params.containsKey("vis_dept_ids");
        boolean visUserProvided = params.containsKey("vis_user_ids");
        int visAll = parseVisAll(params.getString("vis_all"));
        String visRoleIds = normalizeIdList(params.getString("vis_role_ids"), "可见范围·角色");
        String visSubIds = normalizeIdList(params.getString("vis_sub_ids"), "可见范围·分部");
        String visDeptIds = normalizeIdList(params.getString("vis_dept_ids"), "可见范围·部门");
        String visUserIds = normalizeIdList(params.getString("vis_user_ids"), "可见范围·人员");
        String sortNo = OaonesUtil.s(params.getString("sort_no")).trim();
        String state = OaonesUtil.s(params.getString("enable")).trim();
        String remark = OaonesUtil.s(params.getString("remark"));
        OaonesLog.step("保存应用 id=" + id + " appName=" + appName
                + " appUrl=" + appUrl + " ssoClass=" + ssoClass);

        if (appName.length() == 0) {
            throw new RuntimeException("请填写应用名称");
        }
        if (appName.length() > NAME_MAX) {
            throw new RuntimeException("应用名称不能超过 " + NAME_MAX + " 个字符");
        }
        if (appUrl.length() == 0) {
            throw new RuntimeException("请填写应用默认访问地址");
        }
        if (appUrl.length() > URL_MAX) {
            throw new RuntimeException("应用默认访问地址不能超过 " + URL_MAX + " 个字符");
        }
        if (!isNavigableUrl(appUrl)) {
            throw new RuntimeException("应用默认访问地址要以 http://、https:// 开头，或以 / 开头的站内地址");
        }
        if (ssoClass.length() > CLASS_MAX) {
            throw new RuntimeException("单点登录实现类不能超过 " + CLASS_MAX + " 个字符");
        }
        if (ssoMethod.length() > METHOD_MAX) {
            throw new RuntimeException("身份认证方法说明不能超过 " + METHOD_MAX + " 个字符");
        }
        if (icon.length() > URL_MAX) {
            throw new RuntimeException("图标地址不能超过 " + URL_MAX + " 个字符");
        }
        if (icon.length() > 0 && !isUploadedIconUrl(icon)) {
            throw new RuntimeException("图标请用弹框里的「上传图标」按钮上传（上传后保存在 "
                    + OaonesConst.APP_ICON_DIR + "/ 下，地址形如 " + OaonesConst.APP_ICON_URL_PREFIX
                    + "appicon_xxx.png），不支持手工填写图片地址；不填图标时桌面按应用名首字生成色块图标");
        }
        if (iconSeedIn.length() > NAME_MAX) {
            throw new RuntimeException("默认文字图标的基准不能超过 " + NAME_MAX + " 个字符");
        }
        if (remark.length() > REMARK_MAX) {
            throw new RuntimeException("备注不能超过 " + REMARK_MAX + " 个字符");
        }
        // 单点登录实现类：保存前先校验（类不存在 / 未实现接口 / 无法实例化时直接拒绝，避免配了却打不开）
        String classReason = AppSsoRegistry.check(ssoClass);
        if (classReason.length() > 0) {
            throw new RuntimeException("单点登录实现类不可用：" + classReason);
        }
        int key = OaonesUtil.i(id, 0);
        Integer existId = OaonesMyBatis.call(AppCenterMapper.class,
                mapper -> mapper.findIdByName(table(), appName, key));
        if (existId != null && existId.intValue() != key) {
            throw new RuntimeException("应用「" + appName + "」已存在（记录ID " + existId + "），应用名称不能重复");
        }
        int enable = "0".equals(state) ? OAONES_DISABLED : OAONES_ENABLE;
        int sort = OaonesUtil.i(sortNo, 0);
        String now = OaonesUtil.now();
        int modeId = OaonesProps.appModeId();
        if (key > 0) {
            AppInfo exist = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.findById(table(), key));
            // 应用级维护权限：只有创建人、维护人与系统管理员能改这条应用
            assertAppMaintain(exist, operatorId);
            AppInfo entity = new AppInfo();
            entity.setId(key);
            entity.setAppName(appName);
            entity.setAppUrl(appUrl);
            entity.setSsoClass(ssoClass);
            entity.setSsoMethod(ssoMethod);
            entity.setIcon(icon);
            entity.setIconSeed(resolveIconSeed(iconSeedIn, key, appName,
                    OaonesUtil.s(exist.getIconSeed())));
            entity.setOpenMode(Integer.valueOf(parseOpenMode(openModeIn)));
            entity.setMaintainerIds(maintainerProvided
                    ? maintainerIds : normalizeMaintainers(exist.getMaintainerIds()));
            entity.setVisAll(Integer.valueOf(visAllProvided ? visAll : visAllOf(exist)));
            entity.setVisRoleIds(visRoleProvided ? visRoleIds
                    : normalizeIdList(exist.getVisRoleIds(), "可见范围·角色"));
            entity.setVisSubIds(visSubProvided ? visSubIds
                    : normalizeIdList(exist.getVisSubIds(), "可见范围·分部"));
            entity.setVisDeptIds(visDeptProvided ? visDeptIds
                    : normalizeIdList(exist.getVisDeptIds(), "可见范围·部门"));
            entity.setVisUserIds(visUserProvided ? visUserIds
                    : normalizeIdList(exist.getVisUserIds(), "可见范围·人员"));
            entity.setSortNo(Integer.valueOf(sort));
            entity.setEnable(Integer.valueOf(enable));
            entity.setRemark(remark);
            entity.setModedatamodifier(Integer.valueOf(OaonesUtil.i(operatorId, 0)));
            entity.setModedatamodifydatetime(now);
            OaonesMyBatis.call(AppCenterMapper.class, mapper -> {
                if (mapper.update(table(), entity) <= 0) {
                    throw new RuntimeException("应用保存失败");
                }
                return Integer.valueOf(1);
            });
            // 换了图标：把不再被引用的旧图标文件删掉（只删本页上传生成的文件，见 cleanupIconFile）
            String oldIcon = OaonesUtil.s(exist.getIcon()).trim();
            if (oldIcon.length() > 0 && !oldIcon.equals(icon)) {
                cleanupIconFile(oldIcon, key);
            }
            OaonesModeRight.rebuildOnEdit(table(), modeId, String.valueOf(key));
            return get(String.valueOf(key));
        }
        AppInfo entity = new AppInfo();
        entity.setAppName(appName);
        entity.setAppUrl(appUrl);
        entity.setSsoClass(ssoClass);
        entity.setSsoMethod(ssoMethod);
        entity.setIcon(icon);
        entity.setIconSeed(resolveIconSeed(iconSeedIn, 0, appName, null));
        entity.setOpenMode(Integer.valueOf(parseOpenMode(openModeIn)));
        // 新建：创建人（modedatacreater，下面写入）天然可维护，所以不把创建人自动塞进维护人
        entity.setMaintainerIds(maintainerIds);
        entity.setVisAll(Integer.valueOf(visAll));
        entity.setVisRoleIds(visRoleIds);
        entity.setVisSubIds(visSubIds);
        entity.setVisDeptIds(visDeptIds);
        entity.setVisUserIds(visUserIds);
        entity.setSortNo(Integer.valueOf(sort));
        entity.setEnable(Integer.valueOf(enable));
        entity.setRemark(remark);
        entity.setFormmodeid(modeId);
        entity.setModedatacreater(Integer.valueOf(OaonesUtil.i(operatorId, 0)));
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(Integer.valueOf(OaonesUtil.i(operatorId, 0)));
        entity.setModedatamodifydatetime(now);
        OaonesMyBatis.call(AppCenterMapper.class, mapper -> {
            if (mapper.insert(table(), entity) <= 0) {
                throw new RuntimeException("应用保存失败");
            }
            return Integer.valueOf(1);
        });
        Integer newId = OaonesMyBatis.call(AppCenterMapper.class,
                mapper -> mapper.findIdByName(table(), appName, 0));
        if (newId == null) {
            return null;
        }
        OaonesModeRight.shareNewData(table(), modeId, String.valueOf(newId), operatorId);
        return get(String.valueOf(newId));
    }

    @Override
    public JSONObject setState(String id, String enable, String operatorId) {
        assertMaintain(operatorId);
        Integer key = OaonesUtil.integer(id);
        if (key == null || key <= 0) {
            throw new RuntimeException("缺少应用记录ID");
        }
        AppInfo exist = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.findById(table(), key));
        // 应用级维护权限：只有创建人、维护人与系统管理员能启停这条应用
        assertAppMaintain(exist, operatorId);
        int state = "0".equals(OaonesUtil.s(enable).trim()) ? OAONES_DISABLED : OAONES_ENABLE;
        OaonesLog.step("设置应用状态 id=" + id + " enable=" + state);
        OaonesMyBatis.run(AppCenterMapper.class, mapper -> mapper.updateState(table(), key, state,
                OaonesUtil.i(operatorId, 0), OaonesUtil.now()));
        OaonesModeRight.rebuildOnEdit(table(), OaonesProps.appModeId(), String.valueOf(key));
        return get(String.valueOf(key));
    }

    @Override
    public JSONObject delete(String id, String operatorId) {
        assertMaintain(operatorId);
        Integer key = OaonesUtil.integer(id);
        if (key == null || key <= 0) {
            throw new RuntimeException("缺少应用记录ID");
        }
        AppInfo exist = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.findById(table(), key));
        // 应用级维护权限：只有创建人、维护人与系统管理员能删除这条应用
        assertAppMaintain(exist, operatorId);
        OaonesLog.step("删除应用 id=" + id + " appName=" + exist.getAppName());
        OaonesMyBatis.run(AppCenterMapper.class, mapper -> mapper.deleteById(table(), key));
        // 应用删掉后，它上传的图标文件如果不再被别的应用引用就一并清掉
        cleanupIconFile(OaonesUtil.s(exist.getIcon()), key);
        return OaonesUtil.ok();
    }

    @Override
    public JSONObject saveIcon(String originalName, byte[] content, String operatorId) {
        assertMaintain(operatorId);
        if (content == null || content.length == 0) {
            throw new RuntimeException("没有收到图标文件，请重新选择图片上传");
        }
        if (content.length > OaonesConst.APP_ICON_MAX_BYTES) {
            throw new RuntimeException("图标文件不能超过 "
                    + (OaonesConst.APP_ICON_MAX_BYTES / 1024) + "KB（建议 200KB 以内）");
        }
        // 类型按文件头判定：改了后缀名的其它文件（含 SVG，见 OaonesImageSize 类注释）在这里被拒
        OaonesImageSize.Info info = OaonesImageSize.probe(content);
        if (info == null) {
            throw new RuntimeException("图标只支持 " + OaonesConst.APP_ICON_EXTENSIONS
                    + " 格式的图片（按文件内容判定，改后缀名不算）");
        }
        int width = info.getWidth();
        int height = info.getHeight();
        if (width > OaonesConst.APP_ICON_MAX_SIZE || height > OaonesConst.APP_ICON_MAX_SIZE) {
            throw new RuntimeException("图标长宽不能超过 " + OaonesConst.APP_ICON_MAX_SIZE
                    + " 像素（当前 " + describeSize(width, height) + "），建议 "
                    + OaonesConst.APP_ICON_SUGGEST_SIZE + "×" + OaonesConst.APP_ICON_SUGGEST_SIZE + " 像素");
        }
        File dir = iconDir();
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new RuntimeException("图标目录不可用：" + dir.getPath() + "（请检查 E9 部署目录的写权限）");
        }
        File target = newIconFile(dir, info.getExtension());
        OutputStream out = null;
        try {
            out = new FileOutputStream(target);
            out.write(content);
            out.flush();
        } catch (IOException e) {
            OaonesLog.error("图标写入失败 " + target.getPath(), e);
            throw new RuntimeException("图标保存失败：" + (e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            closeQuietly(out);
        }
        String url = OaonesConst.APP_ICON_URL_PREFIX + target.getName();
        OaonesLog.step("上传应用图标 originalName=" + OaonesUtil.s(originalName)
                + " saved=" + url + " size=" + content.length + "px=" + describeSize(width, height));
        JSONObject data = new JSONObject();
        data.put("url", url);
        data.put("file_name", target.getName());
        data.put("original_name", OaonesUtil.s(originalName));
        data.put("content_type", info.getContentType());
        data.put("width", Integer.valueOf(width));
        data.put("height", Integer.valueOf(height));
        data.put("size", Integer.valueOf(content.length));
        data.put("max_size", Long.valueOf(OaonesConst.APP_ICON_MAX_BYTES));
        data.put("suggest_size", Integer.valueOf(OaonesConst.APP_ICON_SUGGEST_SIZE));
        data.put("min_size", Integer.valueOf(OaonesConst.APP_ICON_MIN_SIZE));
        return data;
    }

    @Override
    public JSONObject checkClass(String className) {
        String reason = AppSsoRegistry.check(className);
        JSONObject data = new JSONObject();
        data.put("ok", Boolean.valueOf(reason.length() == 0));
        data.put("msg", reason);
        data.put("interfaceName", AppSsoHandler.class.getName());
        data.put("method", "loginUrl(AppSsoContext)");
        return data;
    }

    /* ------------------------------------------------------------------
     * 桌面启动：按“单点登录实现类”生成最终地址
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject entry(String appId, String operatorId) {
        Integer key = OaonesUtil.integer(appId);
        if (key == null || key <= 0) {
            throw new RuntimeException("缺少应用记录ID");
        }
        AppInfo app = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.findById(table(), key));
        if (app == null) {
            throw new RuntimeException("应用不存在或已删除");
        }
        if (app.getEnable() == null || app.getEnable().intValue() != OAONES_ENABLE) {
            throw new RuntimeException("应用「" + OaonesUtil.s(app.getAppName()) + "」已停用，请联系管理员");
        }
        // 可见范围：不在范围内的登录人不允许打开（维护人 / 创建人 / 系统管理员例外，便于验证自己的应用）
        if (!canMaintainApp(app, operatorId) && !visibleTo(app, viewerOrg(operatorId))) {
            throw new RuntimeException("应用「" + OaonesUtil.s(app.getAppName())
                    + "」未对你的分部 / 部门 / 人员开放，请联系管理员");
        }
        String ssoClass = OaonesUtil.s(app.getSsoClass()).trim();
        JSONObject data = new JSONObject();
        data.put("id", String.valueOf(key));
        data.put("app_name", OaonesUtil.s(app.getAppName()));
        data.put("sso_class", ssoClass);
        if (ssoClass.length() == 0) {
            // 未配置单点登录实现类：直连应用默认地址
            data.put("url", OaonesUtil.s(app.getAppUrl()));
            data.put("sso", Boolean.FALSE);
            return data;
        }
        AppSsoHandler handler = AppSsoRegistry.resolve(ssoClass);
        if (handler == null) {
            // 实现类不可用（未部署 / 类名写错）：退回默认地址，保证“点得开”，同时把原因带回页面提示
            data.put("url", OaonesUtil.s(app.getAppUrl()));
            data.put("sso", Boolean.FALSE);
            data.put("sso_error", "单点登录实现类不可用，已按应用默认地址打开：" + ssoClass);
            return data;
        }
        AppSsoContext ctx = buildContext(app, ssoClass, operatorId);
        String url;
        try {
            url = handler.loginUrl(ctx);
        } catch (Exception e) {
            OaonesLog.error("应用单点登录失败 id=" + appId + " ssoClass=" + ssoClass, e);
            throw new RuntimeException("单点登录失败（" + ssoClass + "）："
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        String finalUrl = OaonesUtil.s(url).trim();
        if (finalUrl.length() == 0) {
            throw new RuntimeException("单点登录实现类没有返回访问地址：" + ssoClass);
        }
        data.put("url", finalUrl);
        data.put("sso", Boolean.TRUE);
        return data;
    }

    /** 组装交给实现类的上下文（当前登录人 + 应用配置 + 站点前缀）。 */
    private AppSsoContext buildContext(AppInfo app, String ssoClass, String operatorId) {
        AppSsoContext ctx = new AppSsoContext();
        ctx.setAppId(app.getId() == null ? 0 : app.getId().intValue());
        ctx.setAppName(OaonesUtil.s(app.getAppName()));
        ctx.setAppUrl(OaonesUtil.s(app.getAppUrl()));
        ctx.setSsoClass(ssoClass);
        ctx.setSsoMethod(OaonesUtil.s(app.getSsoMethod()));
        ctx.setRemark(OaonesUtil.s(app.getRemark()));
        int uid = OaonesUtil.i(operatorId, 0);
        ctx.setOaUserId(uid);
        if (uid > 0) {
            // 当前登录人的登录名 / 姓名：多数第三方系统按登录名匹配身份，实现类直接用 ctx.getOaLoginId() 即可。
            // 这里不走 OaService#getUser（那个方法会把 sysadmin 与离职账号当作“不存在”过滤掉），
            // 因为维护人本人（可能就是 sysadmin）也需要试用桌面启动。
            try {
                ResourceComInfo rci = new ResourceComInfo();
                ctx.setOaLoginId(OaonesUtil.s(rci.getLoginID(String.valueOf(uid))));
                ctx.setOaUserName(OaonesUtil.s(rci.getLastname(String.valueOf(uid))));
            } catch (Throwable t) {
                OaonesLog.error("读取当前登录人信息失败（单点登录上下文）uid=" + uid, t);
            }
        }
        return ctx;
    }

    /* ------------------------------------------------------------------
     * 工具
     * ------------------------------------------------------------------ */

    /**
     * 可跳转地址校验：允许 http(s) 绝对地址与以 {@code /} 开头的站内地址。
     *
     * <p>拒绝 {@code javascript:}、{@code //host} 等写法（前者会被当成脚本执行，
     * 后者是“协议相对”的跨站地址，容易被用来做跳转钓鱼）。</p>
     */
    private boolean isNavigableUrl(String url) {
        String value = OaonesUtil.s(url).trim();
        String lower = value.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return value.indexOf(' ') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
        }
        return value.startsWith("/") && !value.startsWith("//") && value.indexOf('\\') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    /**
     * 图标地址校验：只接受本页上传后产生的站内地址（{@code /oaones/img/<文件名>}）。
     *
     * <p>图标一律走页面上传（见 {@link #saveIcon}），所以这里既不接受外部图片地址，
     * 也不接受带子目录/回退段的地址——避免有人把 {@code icon} 填成别的路径或跳转地址。</p>
     */
    private boolean isUploadedIconUrl(String url) {
        String value = OaonesUtil.s(url).trim();
        if (!value.startsWith(OaonesConst.APP_ICON_URL_PREFIX)) {
            return false;
        }
        String name = value.substring(OaonesConst.APP_ICON_URL_PREFIX.length());
        if (name.length() == 0 || name.length() > 200) {
            return false;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf("..") >= 0
                || name.indexOf('?') >= 0 || name.indexOf('#') >= 0) {
            return false;
        }
        return value.indexOf(' ') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    /**
     * 默认文字图标的“取字 / 取色基准”（表列 {@code icon_seed}）。
     *
     * <p>没有上传图片（{@code icon} 为空）时，列表与桌面按这个基准生成渐变色块图标：
     * 首字 = 基准的第一个字符，底色 = 基准的散列（两边脚本用同一套算法，看到的颜色一致）。</p>
     *
     * <p><b>为什么要有这个基准列</b>：基准若直接取当前应用名，改名后图标会跟着自动变
     * （首字与颜色都变），维护人只是改个名字、图标却变了，不符合使用“系统默认文字图标”时的预期。
     * 因此口径是：**首次以默认图标保存时固化为当时的应用名，之后改名不跟随**；
     * 页面点「清除图标」= 清掉上传的图片并按**当前应用名**重新生成默认图标，
     * 这时页面会显式传 {@code icon_seed}（参数 {@code seedIn}），这里就按它刷新基准。</p>
     *
     * @param seedIn    页面显式指定的基准（新建 / 清除图标时为当前应用名；其它情况为空）
     * @param id        记录ID（0 = 新建）
     * @param appName   本次保存的应用名称（新记录与“补列前的老数据”用它固化）
     * @param existSeed 库里已固化的基准（编辑已有记录时用）
     * @return 本次要落库的基准（非空）
     */
    private String resolveIconSeed(String seedIn, int id, String appName, String existSeed) {
        String seed = OaonesUtil.s(seedIn).trim();
        if (seed.length() == 0 && id > 0) {
            // 页面没显式指定：沿用已固化的基准（改名不跟随就是这个分支）
            seed = OaonesUtil.s(existSeed).trim();
        }
        if (seed.length() == 0) {
            // 首次以默认图标保存（含刚补列、基准还是空的老数据）：固化当前应用名
            seed = OaonesUtil.s(appName).trim();
        }
        return seed;
    }

    /**
     * 页面提交的“打开方式”（表列 {@code open_mode}）：只认 0 默认 / 1 新标签页 / 2 原标签页，
     * 空值与其它值（含页面被改造、直接调接口传进来的脏值）一律按 0 默认，
     * 与桌面脚本里的 openModeOf 是同一口径。
     */
    private int parseOpenMode(String value) {
        int mode = OaonesUtil.i(OaonesUtil.s(value).trim(), OAONES_OPEN_CURRENT);
        return (mode == OAONES_OPEN_NEW_TAB || mode == OAONES_OPEN_TOP) ? mode : OAONES_OPEN_CURRENT;
    }

    /** 库里存的“打开方式”：老库补列后的历史行是 NULL，按 0 默认处理（与升级前行为一致）。 */
    private int openModeOf(Integer stored) {
        return stored == null ? OAONES_OPEN_CURRENT : parseOpenMode(String.valueOf(stored));
    }

    /** 图标上传目录（E9 站点根下的 {@code oaones/img}）。 */
    private File iconDir() {
        return new File(OaonesUtil.s(GCONST.getRootPath()), OaonesConst.APP_ICON_DIR);
    }

    /**
     * 生成本次上传的落盘文件：名字由服务端拼（时间戳 + 随机串），
     * 不使用上传上来的文件名——既避免路径穿越，也避免中文名在各系统的乱码问题。
     */
    private File newIconFile(File dir, String extension) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Random random = new Random();
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(ICON_NAME_HEX.charAt(random.nextInt(ICON_NAME_HEX.length())));
            }
            File file = new File(dir, OaonesConst.APP_ICON_FILE_PREFIX + stamp + "_" + hex + "." + extension);
            if (!file.exists()) {
                return file;
            }
        }
        // 极端情况下（同一秒内同名）直接用毫秒兜底，保证一定拿到一个新名字
        return new File(dir, OaonesConst.APP_ICON_FILE_PREFIX + System.currentTimeMillis() + "." + extension);
    }

    /**
     * 删除不再被引用的图标文件。

     * <p>两道保险：① 只处理 {@code /oaones/img/} 下、且文件名带本模块前缀（
     * {@link OaonesConst#APP_ICON_FILE_PREFIX}）的文件，手工放进该目录的图片不动；
     * ② 删除前先查还有没有别的应用引用同一地址（多个应用可以指向同一张图，这里不误删）。</p>
     */
    private void cleanupIconFile(String icon, int excludeId) {
        String path = OaonesUtil.s(icon).trim();
        if (!isUploadedIconUrl(path)) {
            return;
        }
        String name = path.substring(OaonesConst.APP_ICON_URL_PREFIX.length());
        if (!name.startsWith(OaonesConst.APP_ICON_FILE_PREFIX)) {
            return;
        }
        try {
            Integer used = OaonesMyBatis.call(AppCenterMapper.class,
                    mapper -> mapper.countByIcon(table(), path, excludeId));
            if (used != null && used.intValue() > 0) {
                return;
            }
            File file = new File(iconDir(), name);
            if (file.isFile() && !file.delete()) {
                OaonesLog.step("旧图标文件删除失败（可手工清理）：" + file.getPath());
            }
        } catch (Throwable t) {
            // 清理失败不影响主流程（图标文件留着只是占空间）
            OaonesLog.error("清理旧图标文件失败 icon=" + path, t);
        }
    }

    /** 长宽文案：读不出尺寸时只说“尺寸未知”（不抛异常）。 */
    private String describeSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "尺寸未知";
        }
        return width + "×" + height;
    }

    private void closeQuietly(OutputStream out) {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
    }

    private JSONObject get(String id) {
        Integer key = OaonesUtil.integer(id);
        if (key == null || key <= 0) {
            return null;
        }
        AppInfo row = OaonesMyBatis.call(AppCenterMapper.class, mapper -> mapper.findById(table(), key));
        if (row == null) {
            return null;
        }
        // 单条返回也要带创建人 / 维护人 / 可见范围的名称明细（页面保存后回填用）
        return toJson(row, orgNames(Collections.singletonList(row)));
    }

    private JSONObject toJson(AppInfo row, OrgNames names) {
        JSONObject obj = new JSONObject();
        obj.put("id", row.getId() == null ? "" : String.valueOf(row.getId()));
        obj.put("app_name", OaonesUtil.s(row.getAppName()));
        obj.put("app_url", OaonesUtil.s(row.getAppUrl()));
        obj.put("sso_class", OaonesUtil.s(row.getSsoClass()));
        obj.put("sso_method", OaonesUtil.s(row.getSsoMethod()));
        obj.put("icon", OaonesUtil.s(row.getIcon()));
        // 默认文字图标的基准（icon 为空时列表用它算首字与底色；保存时固化，改名不跟随）
        obj.put("icon_seed", OaonesUtil.s(row.getIconSeed()));
        // 打开方式：0 默认（当前页面内打开）/ 1 新标签页 / 2 原标签页（整个页面 reload）
        obj.put("open_mode", Integer.valueOf(openModeOf(row.getOpenMode())));
        obj.put("sort_no", row.getSortNo() == null ? 0 : row.getSortNo().intValue());
        obj.put("enable", row.getEnable() == null ? OAONES_ENABLE : row.getEnable().intValue());
        obj.put("remark", OaonesUtil.s(row.getRemark()));
        // 维护人：maintainer_ids 为逗号分隔的人员ID，maintainers 为 id+姓名明细（页面渲染标签/人员链接）
        JSONArray maintainers = idNameJson(idList(row.getMaintainerIds()), names.users);
        obj.put("maintainer_ids", normalizeMaintainers(row.getMaintainerIds()));
        obj.put("maintainers", maintainers);
        obj.put("maintainer_names", maintainersNameText(maintainers));
        // 可见范围：vis_all = 1 全员可见（不控制权限）；否则角色 / 分部 / 部门 / 人员四类各自多选，**满足其一即可见**
        obj.put("vis_all", Integer.valueOf(visAllOf(row)));
        List<String> visRoles = idList(row.getVisRoleIds());
        List<String> visSubs = idList(row.getVisSubIds());
        List<String> visDepts = idList(row.getVisDeptIds());
        List<String> visUsers = idList(row.getVisUserIds());
        obj.put("vis_role_ids", normalizeIdList(row.getVisRoleIds(), "可见范围·角色"));
        obj.put("vis_sub_ids", normalizeIdList(row.getVisSubIds(), "可见范围·分部"));
        obj.put("vis_dept_ids", normalizeIdList(row.getVisDeptIds(), "可见范围·部门"));
        obj.put("vis_user_ids", normalizeIdList(row.getVisUserIds(), "可见范围·人员"));
        obj.put("vis_roles", idNameJson(visRoles, names.roles));
        obj.put("vis_subs", idNameJson(visSubs, names.subs));
        obj.put("vis_depts", idNameJson(visDepts, names.depts));
        obj.put("vis_users", idNameJson(visUsers, names.users));
        // 未勾选“全员可见”且四类都没设置 = 无权限（工作台上除创建人/维护人/系统管理员外都看不到）
        obj.put("vis_unset", Boolean.valueOf(!isVisAll(row)
                && visRoles.isEmpty() && visSubs.isEmpty() && visDepts.isEmpty() && visUsers.isEmpty()));
        // 创建人（应用由创建人 + 维护人维护）：人员ID + 姓名，列表里对超管展示归属
        String creator = OaonesUtil.nullToEmpty(row.getModedatacreater());
        obj.put("create_by", creator);
        String creatorName = names == null ? null : names.users.get(creator.trim());
        obj.put("creator_name", creatorName == null ? "" : creatorName);
        obj.put("create_time", OaonesUtil.nullToEmpty(row.getModedatacreatedate()) + " "
                + OaonesUtil.nullToEmpty(row.getModedatacreatetime()));
        obj.put("modify_time", OaonesUtil.nullToEmpty(row.getModedatamodifydatetime()));
        // 单点登录实现类的“自述”与可用性：列表里直接展示（类不可用时给出提示，便于维护人发现配错）
        String ssoClass = OaonesUtil.s(row.getSsoClass()).trim();
        if (ssoClass.length() > 0) {
            String reason = AppSsoRegistry.check(ssoClass);
            obj.put("sso_ok", Boolean.valueOf(reason.length() == 0));
            obj.put("sso_error", reason);
            AppSsoHandler handler = AppSsoRegistry.resolve(ssoClass);
            obj.put("sso_describe", handler == null ? "" : OaonesUtil.s(handler.describe()));
        } else {
            obj.put("sso_ok", Boolean.TRUE);
            obj.put("sso_error", "");
            obj.put("sso_describe", "");
        }
        return obj;
    }
}
