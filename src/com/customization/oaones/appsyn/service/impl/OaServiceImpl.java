package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesHrmScope;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.HrmMapper;
import com.customization.oaones.appsyn.dto.HrmResourceBasic;
import com.customization.oaones.appsyn.dto.HrmResourceCreate;
import com.customization.oaones.appsyn.dto.HrmDepartmentCreate;
import com.customization.oaones.appsyn.entity.HrmDepartment;
import com.customization.oaones.appsyn.service.OaService;
import weaver.conn.RecordSet;
import weaver.general.PasswordUtil;
import weaver.general.Util;
import weaver.hrm.company.DepartmentComInfo;
import weaver.hrm.company.OrgOperationUtil;
import weaver.hrm.company.SubCompanyComInfo;
import weaver.hrm.resource.ResourceComInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OA 组织人员数据读取实现（DAO 层为 {@link HrmMapper}）。
 *
 * <p>组织树读取优先使用 E9 缓存（ResourceComInfo / DepartmentComInfo），
 * 避免直接 SQL 依赖 HrmResource 表结构（如 canceled 列不存在时导致的查询失败）。</p>
 */
public class OaServiceImpl implements OaService {

    @Override
    public JSONArray listSubCompaniesFromCache() {
        OaonesLog.step("读取 OA 分部缓存 SubCompanyComInfo");
        JSONArray result = new JSONArray();
        SubCompanyComInfo sci = new SubCompanyComInfo();
        sci.setTofirstRow();
        while (sci.next()) {
            if ("1".equals(OaonesUtil.s(sci.getCompanyiscanceled()))) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("id", OaonesUtil.s(sci.getSubCompanyid()));
            row.put("code", OaonesUtil.s(sci.getSubCompanyCode()));
            row.put("name", OaonesUtil.s(sci.getSubCompanyname()));
            row.put("fullname", OaonesUtil.s(sci.getSubCompanydesc()));
            row.put("parent", OaonesUtil.s(sci.getSupsubcomid()));
            result.add(row);
        }
        return result;
    }

    @Override
    public JSONArray listDepartmentsFromCache() {
        OaonesLog.step("读取 OA 部门缓存 DepartmentComInfo");
        JSONArray result = new JSONArray();
        DepartmentComInfo dci = new DepartmentComInfo();
        dci.setTofirstRow();
        while (dci.next()) {
            if ("1".equals(OaonesUtil.s(dci.getDeparmentcanceled()))) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("id", OaonesUtil.s(dci.getDepartmentid()));
            row.put("code", OaonesUtil.s(dci.getDepartmentCode()));
            row.put("name", OaonesUtil.s(dci.getDepartmentmark()));
            row.put("fullname", OaonesUtil.s(dci.getDepartmentname()));
            row.put("parent", OaonesUtil.s(dci.getDepartmentsupdepid()));
            row.put("subcompanyid1", OaonesUtil.s(dci.getSubcompanyid1()));
            result.add(row);
        }
        return result;
    }

    @Override
    public JSONArray listDepartments() {
        OaonesLog.step("查询 OA 部门列表");
        JSONArray result = new JSONArray();
        List<HrmDepartment> rows = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.listDepartments());
        if (rows == null) {
            return result;
        }
        for (HrmDepartment dept : rows) {
            result.add(toJson(dept));
        }
        return result;
    }

    /**
     * 页面用重载：按登录人的 OA 分权范围过滤候选（OA 没开分权时原样返回全量）。
     *
     * <p>后台同步（企微 ⇄ OA）固定用无 operatorId 的重载——同步由调度器执行、
     * 没有登录人上下文，必须仍然看到全量组织；分权只作用于页面上的“选择 OA 组织架构”。</p>
     */
    @Override
    public JSONArray listSubCompaniesFromCache(String operatorId) {
        return OaonesHrmScope.filterSubCompanies(listSubCompaniesFromCache(), operatorId);
    }

    @Override
    public JSONArray listDepartmentsFromCache(String operatorId) {
        return OaonesHrmScope.filterDepartments(listDepartmentsFromCache(), operatorId);
    }

    @Override
    public JSONArray listDepartments(String operatorId) {
        return OaonesHrmScope.filterDepartments(listDepartments(), operatorId);
    }

    @Override
    public JSONArray listUsers(String keyword, String operatorId) throws Exception {
        return OaonesHrmScope.filterUsers(listUsers(keyword), operatorId);
    }

    @Override
    public JSONObject getDepartment(String deptId) {
        Integer id = OaonesUtil.integer(deptId);
        if (id == null || id <= 0) {
            return null;
        }
        HrmDepartment dept = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getDepartment(id));
        return dept == null ? null : toJson(dept);
    }

    @Override
    public JSONObject getSubCompany(String subId) {
        Integer id = OaonesUtil.integer(subId);
        if (id == null || id <= 0) {
            return null;
        }
        // 分部数量有限，遍历缓存按 ID 命中；与 listSubCompaniesFromCache 同一数据源，
        // 这里不跳过已封存分部（调用方需要区分“不存在”与“已封存”）。
        String key = String.valueOf(id);
        SubCompanyComInfo sci = new SubCompanyComInfo();
        sci.setTofirstRow();
        while (sci.next()) {
            if (!key.equals(OaonesUtil.s(sci.getSubCompanyid()))) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("id", key);
            row.put("name", OaonesUtil.s(sci.getSubCompanyname()));
            row.put("code", OaonesUtil.s(sci.getSubCompanyCode()));
            row.put("desc", OaonesUtil.s(sci.getSubCompanydesc()));
            row.put("parent", OaonesUtil.s(sci.getSupsubcomid()));
            row.put("canceled", OaonesUtil.s(sci.getCompanyiscanceled()));
            return row;
        }
        return null;
    }

    @Override
    public JSONArray listUsers(String keyword) throws Exception {
        OaonesLog.step("从 ResourceComInfo 缓存读取 OA 人员列表 keyword=" + keyword);
        JSONArray result = new JSONArray();
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        ResourceComInfo rci = new ResourceComInfo();
        DepartmentComInfo dci = new DepartmentComInfo();
        rci.setTofirstRow();
        while (rci.next()) {
            String loginId = OaonesUtil.s(rci.getLoginID());
            // 只排除系统管理员账号：OA 里存在大量“没有登录名”的在职账号
            // （新建待分配账号、只挂组织架构不登录的人员等），它们同样是部门人员，
            // 必须出现在人员绑定页，否则按部门核对时会整部门漏人。
            if ("sysadmin".equalsIgnoreCase(loginId)) {
                continue;
            }
            // 与历史“有效账号”口径一致：只保留状态 0-3（在职/试用等）的人员
            String status = OaonesUtil.s(rci.getStatus());
            if (status.length() > 0 && Util.getIntValue(status, 99) >= 4) {
                continue;
            }
            String lastname = OaonesUtil.s(rci.getLastname());
            String workcode = OaonesUtil.s(rci.getWorkcode());
            String mobile = OaonesUtil.s(rci.getMobile());
            String email = OaonesUtil.s(rci.getEmail());
            String userId = OaonesUtil.s(rci.getResourceid());
            if (k.length() > 0
                    && !(lastname.toLowerCase().contains(k)
                    || loginId.toLowerCase().contains(k)
                    || workcode.toLowerCase().contains(k)
                    || mobile.toLowerCase().contains(k)
                    || email.toLowerCase().contains(k)
                    || userId.toLowerCase().contains(k))) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("id", userId);
            row.put("loginid", loginId);
            row.put("lastname", lastname);
            row.put("mobile", mobile);
            row.put("email", email);
            row.put("workcode", workcode);
            String departmentId = OaonesUtil.s(rci.getDepartmentID());
            row.put("departmentid", departmentId);
            row.put("departmentname", deptNameOf(dci, departmentId));
            row.put("subcompanyid1", OaonesUtil.s(rci.getSubCompanyID()));
            row.put("status", status);
            result.add(row);
        }
        return result;
    }

    @Override
    public JSONObject getUser(String userId) throws Exception {
        if (userId == null || userId.trim().length() == 0) {
            return null;
        }
        ResourceComInfo rci = new ResourceComInfo();
        DepartmentComInfo dci = new DepartmentComInfo();
        String loginId = OaonesUtil.s(rci.getLoginID(userId));
        String lastname = OaonesUtil.s(rci.getLastname(userId));
        // 与 listUsers 口径一致：不因“没有登录名”判定人员不存在
        if ("sysadmin".equalsIgnoreCase(loginId)) {
            return null;
        }
        String status = OaonesUtil.s(rci.getStatus(userId));
        if (status.length() > 0 && Util.getIntValue(status, 99) >= 4) {
            return null;
        }
        JSONObject row = new JSONObject();
        row.put("id", OaonesUtil.s(userId));
        row.put("loginid", loginId);
        row.put("lastname", lastname);
        row.put("mobile", OaonesUtil.s(rci.getMobile(userId)));
        row.put("email", OaonesUtil.s(rci.getEmail(userId)));
        row.put("workcode", OaonesUtil.s(rci.getWorkcode(userId)));
        String departmentId = OaonesUtil.s(rci.getDepartmentID(userId));
        row.put("departmentid", departmentId);
        row.put("departmentname", deptNameOf(dci, departmentId));
        row.put("subcompanyid1", OaonesUtil.s(rci.getSubCompanyID(userId)));
        row.put("status", status);
        return row;
    }

    @Override
    public String getDeptName(String deptId) {
        return deptNameOf(new DepartmentComInfo(), deptId);
    }

    @Override
    public boolean updateUserBasic(String userId, String name, String mobile, String email,
                                   String departmentId, String subcompanyId) {
        Integer id = OaonesUtil.integer(userId);
        if (id == null || id <= 0) {
            return false;
        }
        HrmResourceBasic entity = new HrmResourceBasic();
        entity.setId(id);
        entity.setLastname(name);
        entity.setMobile(mobile);
        entity.setEmail(email);
        entity.setDepartmentId(OaonesUtil.integer(departmentId));
        entity.setSubcompanyId(OaonesUtil.integer(subcompanyId));
        if (name == null && mobile == null && email == null
                && entity.getDepartmentId() == null && entity.getSubcompanyId() == null) {
            return true;
        }
        Integer rows = OaonesMyBatis.call(HrmMapper.class,
                mapper -> mapper.updateResourceBasic(entity));
        return rows != null && rows >= 0;
    }

    @Override
    public Integer findUserIdByLoginId(String loginid) {
        String value = OaonesUtil.s(loginid);
        if (value.length() == 0) {
            return null;
        }
        return OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.findResourceIdByLoginId(value));
    }

    @Override
    public int createUser(String loginid, String name, String mobile, String email,
                          String departmentId, String subcompanyId, String password, String operatorId)
            throws Exception {
        String login = OaonesUtil.s(loginid);
        if (login.length() == 0) {
            throw new RuntimeException("新建 OA 人员缺少登录名");
        }
        String userName = OaonesUtil.s(name);
        if (userName.length() == 0) {
            throw new RuntimeException("新建 OA 人员缺少姓名");
        }
        JSONObject oaDept = getDepartment(departmentId);
        if (oaDept == null) {
            throw new RuntimeException("OA 部门不存在，无法新建账号");
        }
        if ("1".equals(OaonesUtil.s(oaDept.getString("canceled")))) {
            throw new RuntimeException("OA 部门已封存，无法新建账号");
        }
        Integer exists = findUserIdByLoginId(login);
        if (exists != null) {
            throw new RuntimeException("OA 已存在登录名为 " + login + " 的账号（ID " + exists
                    + "），请在“企微数据基准”页手工绑定，或调整企微成员ID后重试");
        }
        int newId = nextResourceId();
        String pwd = OaonesUtil.s(password);
        if (pwd.length() == 0) {
            pwd = "1";
        }
        String[] encrypted = PasswordUtil.encrypt(pwd);
        String nowDate = OaonesUtil.nowDate();
        HrmResourceCreate entity = new HrmResourceCreate();
        entity.setId(newId);
        entity.setLoginid(login);
        entity.setPassword(encrypted != null && encrypted.length > 0 ? encrypted[0] : null);
        entity.setLastname(userName);
        entity.setMobile(emptyToNull(mobile));
        entity.setEmail(emptyToNull(email));
        entity.setDepartmentId(OaonesUtil.i(departmentId, 0));
        entity.setSubcompanyId(OaonesUtil.i(subcompanyId, 0));
        // 与 E9 人员导入一致：新建账号默认在职(0)、普通账号(0)、简体中文(7)
        entity.setStatus(0);
        entity.setAccountType(0);
        entity.setSystemLanguage(7);
        entity.setCreaterId(OaonesUtil.i(operatorId, 0));
        entity.setCreateDate(nowDate);
        entity.setLastModId(OaonesUtil.i(operatorId, 0));
        entity.setLastModDate(nowDate);
        entity.setLastLoginDate(nowDate);
        OaonesLog.step("新建 OA 账号 loginid=" + login + " name=" + userName
                + " departmentId=" + departmentId);
        OaonesMyBatis.call(HrmMapper.class, mapper -> {
            if (mapper.insertResource(entity) <= 0) {
                throw new RuntimeException("OA 人员新建失败：loginid=" + login);
            }
            return 1;
        });
        if (encrypted != null && encrypted.length > 1 && OaonesUtil.s(encrypted[1]).length() > 0) {
            PasswordUtil.updateResourceSalt(String.valueOf(newId), encrypted[1]);
        }
        try {
            new ResourceComInfo().updateResourceInfoCache(String.valueOf(newId));
        } catch (Throwable t) {
            OaonesLog.error("刷新 OA 人员缓存失败 id=" + newId + "（不影响账号创建）", t);
        }
        return newId;
    }

    /** E9 标准人员ID生成过程；与平台人员导入保持一致的取号方式。 */
    private int nextResourceId() {
        RecordSet rs = new RecordSet();
        rs.executeProc("HrmResourceMaxId_Get", "");
        if (rs.next()) {
            int id = rs.getInt(1);
            if (id > 0) {
                return id;
            }
        }
        throw new RuntimeException("获取 OA 人员ID失败（HrmResourceMaxId_Get），无法新建账号");
    }

    @Override
    public JSONObject createDepartment(String name, String parentDeptId, String operatorId) {
        return createDepartment(name, parentDeptId, "", operatorId);
    }

    @Override
    public JSONObject createDepartment(String name, String parentDeptId, String subCompanyId,
                                       String operatorId) {
        String deptName = OaonesUtil.s(name).trim();
        if (deptName.length() == 0) {
            throw new RuntimeException("新建 OA 部门缺少部门名称");
        }
        HrmDepartmentCreate entity = new HrmDepartmentCreate();
        entity.setName(deptName);
        // 与本环境数据口径一致：部门简称默认与名称相同（平台导入口径亦然）
        entity.setMark(deptName);
        Integer parentId;
        String subId;
        if (OaonesUtil.s(parentDeptId).trim().length() > 0) {
            JSONObject parent = getDepartment(parentDeptId);
            if (parent == null) {
                throw new RuntimeException("上级 OA 部门[id=" + parentDeptId + "]不存在，无法新建下级部门");
            }
            if ("1".equals(OaonesUtil.s(parent.getString("canceled")))) {
                throw new RuntimeException("上级 OA 部门[" + OaonesUtil.s(parent.getString("name"))
                        + "]已封存，无法在其下新建部门");
            }
            parentId = OaonesUtil.integer(parentDeptId);
            subId = OaonesUtil.s(parent.getString("subcompanyid1"));
            entity.setAllSupDepId(allSupDepIdOf(parent, parentId));
            entity.setTlevel(levelOf(parent) + 1);
        } else {
            // 没指定上级部门（同步中心“企微 → OA”只选了 OA 落点分部）：在指定分部下新建顶级部门
            String subKey = OaonesUtil.s(subCompanyId).trim();
            JSONObject sub = getSubCompany(subKey);
            if (sub == null) {
                throw new RuntimeException("OA 分部[id=" + subKey + "]不存在，无法在该分部下新建部门");
            }
            if ("1".equals(OaonesUtil.s(sub.getString("canceled")))) {
                throw new RuntimeException("OA 分部[" + OaonesUtil.s(sub.getString("name"))
                        + "]已封存，无法在其下新建部门");
            }
            parentId = Integer.valueOf(0);
            subId = subKey;
            // 顶级部门的上级路径留空（与本环境数据口径一致）
            entity.setAllSupDepId("");
            entity.setTlevel(1);
        }
        entity.setSubCompanyId(OaonesUtil.integer(subId));
        entity.setSupDepId(parentId);
        entity.setShowOrder(999);
        OaonesLog.step("新建 OA 部门 name=" + deptName + " parentId=" + parentId
                + " subcompanyId=" + subId + " operatorId=" + operatorId);
        int rows = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.insertDepartment(entity));
        if (rows <= 0) {
            throw new RuntimeException("OA 部门新建失败：" + deptName);
        }
        Integer newId = OaonesMyBatis.call(HrmMapper.class,
                mapper -> mapper.findLatestDepartmentId(deptName, entity.getSubCompanyId(), parentId));
        if (newId == null || newId.intValue() <= 0) {
            throw new RuntimeException("OA 部门[" + deptName + "]新建后取不到部门ID，请检查 hrmdepartment 表");
        }
        // 新建部门后刷新部门缓存（含组织树相关的超级部门缓存），保证页面立即可见
        refreshDepartmentCache(String.valueOf(newId), true);
        JSONObject result = new JSONObject();
        result.put("id", String.valueOf(newId));
        result.put("name", deptName);
        result.put("parent", String.valueOf(parentId));
        result.put("subcompanyid1", subId);
        return result;
    }

    @Override
    public boolean updateDepartmentName(String deptId, String name, String operatorId) {
        Integer id = OaonesUtil.integer(deptId);
        if (id == null || id.intValue() <= 0) {
            throw new RuntimeException("缺少 OA 部门ID，无法更新部门名称");
        }
        String deptName = OaonesUtil.s(name).trim();
        if (deptName.length() == 0) {
            throw new RuntimeException("OA 部门名称为空，无法更新");
        }
        JSONObject dept = getDepartment(deptId);
        if (dept == null) {
            throw new RuntimeException("OA 部门[id=" + deptId + "]不存在，无法更新名称");
        }
        if (deptName.equals(OaonesUtil.s(dept.getString("name")))) {
            return true;
        }
        OaonesLog.step("更新 OA 部门名称 deptId=" + deptId + " name=" + deptName
                + " operatorId=" + operatorId);
        int rows = OaonesMyBatis.call(HrmMapper.class,
                mapper -> mapper.updateDepartmentName(id, deptName, deptName));
        refreshDepartmentCache(String.valueOf(id), false);
        return rows > 0;
    }

    @Override
    public boolean moveDepartment(String deptId, String parentDeptId, String subCompanyId,
                                  String operatorId) {
        Integer id = OaonesUtil.integer(deptId);
        if (id == null || id.intValue() <= 0) {
            throw new RuntimeException("缺少 OA 部门ID，无法调整部门挂靠");
        }
        JSONObject dept = getDepartment(deptId);
        if (dept == null) {
            throw new RuntimeException("OA 部门[id=" + deptId + "]不存在，无法调整挂靠");
        }
        if ("1".equals(OaonesUtil.s(dept.getString("canceled")))) {
            throw new RuntimeException("OA 部门[" + OaonesUtil.s(dept.getString("name"))
                    + "]已封存，无法调整挂靠");
        }
        // 目标上级：上级部门（分部随上级部门）或“某分部下的顶级部门”
        Integer parentId;
        String subId;
        JSONObject parent = null;
        if (OaonesUtil.s(parentDeptId).trim().length() > 0) {
            parent = getDepartment(parentDeptId);
            if (parent == null) {
                throw new RuntimeException("上级 OA 部门[id=" + parentDeptId + "]不存在，无法调整部门挂靠");
            }
            if ("1".equals(OaonesUtil.s(parent.getString("canceled")))) {
                throw new RuntimeException("上级 OA 部门[" + OaonesUtil.s(parent.getString("name"))
                        + "]已封存，无法把部门挂靠到它下面");
            }
            parentId = OaonesUtil.integer(parentDeptId);
            subId = OaonesUtil.s(parent.getString("subcompanyid1"));
        } else {
            String subKey = OaonesUtil.s(subCompanyId).trim();
            JSONObject sub = getSubCompany(subKey);
            if (sub == null) {
                throw new RuntimeException("OA 分部[id=" + subKey + "]不存在，无法把部门挂靠到该分部下");
            }
            if ("1".equals(OaonesUtil.s(sub.getString("canceled")))) {
                throw new RuntimeException("OA 分部[" + OaonesUtil.s(sub.getString("name"))
                        + "]已封存，无法把部门挂靠到该分部下");
            }
            parentId = Integer.valueOf(0);
            subId = subKey;
        }
        if (OaonesUtil.s(subId).trim().length() == 0) {
            // 上级部门没带分部（异常数据）：保持原分部，避免把 subcompanyid1 写空
            subId = OaonesUtil.s(dept.getString("subcompanyid1"));
        }
        if (String.valueOf(id).equals(String.valueOf(parentId))) {
            throw new RuntimeException("不能把部门[" + OaonesUtil.s(dept.getString("name"))
                    + "]挂靠到它自己下面");
        }
        // 目标上级不能是该部门自己的下级（否则组织树成环）
        if (parentId.intValue() > 0 && isDescendantDept(String.valueOf(parentId), String.valueOf(id))) {
            throw new RuntimeException("不能把部门[" + OaonesUtil.s(dept.getString("name"))
                    + "]挂靠到它自己的下级部门[id=" + parentId + "]下面");
        }
        if (String.valueOf(parentId).equals(OaonesUtil.s(dept.getString("parent")))
                && subId.equals(OaonesUtil.s(dept.getString("subcompanyid1")))) {
            // 已经挂在目标上级与分部之下：不需要调整（避免无谓写库与缓存刷新）
            return false;
        }
        String allSupDepId = parent == null ? "" : allSupDepIdOf(parent, parentId);
        OaonesLog.step("调整 OA 部门挂靠 deptId=" + id + " supdepid=" + parentId
                + " subcompanyid1=" + subId + " allsupdepid=" + OaonesUtil.s(allSupDepId)
                + " operatorId=" + operatorId);
        final Integer moveParentId = parentId;
        final Integer moveSubCompanyId = OaonesUtil.integer(subId);
        final String moveAllSupDepId = (allSupDepId == null || allSupDepId.length() == 0) ? null : allSupDepId;
        int rows = OaonesMyBatis.call(HrmMapper.class,
                mapper -> mapper.moveDepartment(id, moveParentId, moveSubCompanyId, moveAllSupDepId));
        if (rows <= 0) {
            throw new RuntimeException("OA 部门[id=" + id + "]挂靠调整失败");
        }
        // 与平台“部门调整”同口径：重算本部门与全部下级部门的层级，再刷新部门缓存
        try {
            new OrgOperationUtil().updateDepartmentLevel(String.valueOf(id), "0");
        } catch (Throwable t) {
            OaonesLog.error("重算部门层级失败 deptId=" + id + "（挂靠已写入）", t);
        }
        refreshDepartmentCache(String.valueOf(id), true);
        return true;
    }

    /** goalDeptId 是否在 startDeptId 的下级部门里（向上追 supdepid，出现环时最多追 30 层）。 */
    private boolean isDescendantDept(String goalDeptId, String startDeptId) {
        String current = goalDeptId;
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 30 && current != null && current.length() > 0
                && !"0".equals(current) && seen.add(current); i++) {
            if (current.equals(startDeptId)) {
                return true;
            }
            JSONObject row = getDepartment(current);
            if (row == null) {
                return false;
            }
            current = OaonesUtil.s(row.getString("parent"));
        }
        return false;
    }

    /** 上级路径（allsupdepid）：本环境多为空，父部门为空时同样留空，避免造成与平台口径不一致的数据。 */
    private String allSupDepIdOf(JSONObject parent, Integer parentId) {
        String parentAll = OaonesUtil.s(parent.getString("allsupdepid"));
        if (parentAll.length() == 0) {
            return null;
        }
        if (parentId == null) {
            return parentAll;
        }
        return parentAll + "," + parentId;
    }

    /** 部门层级：优先取 hrmdepartment.tlevel，取不到按缓存层级推算。 */
    private int levelOf(JSONObject dept) {
        Integer tlevel = OaonesUtil.integer(OaonesUtil.s(dept.getString("tlevel")));
        if (tlevel != null && tlevel.intValue() > 0) {
            return tlevel.intValue();
        }
        try {
            int level = new DepartmentComInfo().getLevelByDepId(OaonesUtil.s(dept.getString("id")));
            return level > 0 ? level : 1;
        } catch (Throwable t) {
            return 1;
        }
    }

    /** 部门缓存刷新：新建用整表重载（会重建组织树缓存），改名只更新单条。 */
    private void refreshDepartmentCache(String deptId, boolean fullReload) {
        try {
            DepartmentComInfo dci = new DepartmentComInfo();
            if (fullReload) {
                dci.removeCompanyCache();
            } else {
                dci.updateCache(deptId);
            }
        } catch (Throwable t) {
            OaonesLog.error("刷新 OA 部门缓存失败 deptId=" + deptId + "（不影响部门写入）", t);
        }
    }

    private String emptyToNull(String value) {
        String v = OaonesUtil.s(value);
        return v.length() == 0 ? null : v;
    }

    private JSONObject toJson(HrmDepartment dept) {
        JSONObject row = new JSONObject();
        row.put("id", OaonesUtil.nullToEmpty(dept.getId()));
        row.put("name", OaonesUtil.nullToEmpty(dept.getName()));
        row.put("parent", OaonesUtil.nullToEmpty(dept.getParent()));
        row.put("subcompanyid1", OaonesUtil.nullToEmpty(dept.getSubcompanyid1()));
        row.put("subcompany_name", OaonesUtil.nullToEmpty(dept.getSubcompanyName()));
        row.put("canceled", OaonesUtil.nullToEmpty(dept.getCanceled()));
        row.put("allsupdepid", OaonesUtil.nullToEmpty(dept.getAllSupDepId()));
        row.put("tlevel", OaonesUtil.nullToEmpty(dept.getTlevel()));
        return row;
    }

    private String deptNameOf(DepartmentComInfo dci, String deptId) {
        if (deptId == null || deptId.length() == 0 || "0".equals(deptId)) {
            return "";
        }
        return Util.null2String(dci.getDepartmentname(deptId));
    }
}
