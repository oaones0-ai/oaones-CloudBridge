package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * OA 组织人员数据读取服务。
 *
 * <p>读取 E9 标准组织表 hrmdepartment / hrmresource，
 * 不直接修改 OA 标准表结构，仅负责查询与按需更新人员基础信息。</p>
 */
public interface OaService {

    /** 分部筛选数据（来源于 E9 SubCompanyComInfo 缓存）。 */
    JSONArray listSubCompaniesFromCache();

    /**
     * 分部筛选数据（按登录人的 OA 分权范围过滤）。
     *
     * <p>页面上的“选择 OA 分部”候选用本重载；OA 没开分权时与
     * {@link #listSubCompaniesFromCache()} 完全一致。后台同步仍用无参重载（全量）。</p>
     */
    JSONArray listSubCompaniesFromCache(String operatorId);

    /** 部门筛选数据（来源于 E9 DepartmentComInfo 缓存）。 */
    JSONArray listDepartmentsFromCache();

    /** 部门筛选数据（按登录人的 OA 分权范围过滤，见 {@link #listSubCompaniesFromCache(String)}）。 */
    JSONArray listDepartmentsFromCache(String operatorId);

    /** 未封存的部门列表（hrmdepartment + hrmsubcompany）。 */
    JSONArray listDepartments();

    /** 未封存的部门列表（按登录人的 OA 分权范围过滤）。 */
    JSONArray listDepartments(String operatorId);

    /** 按 ID 读取单个部门。 */
    JSONObject getDepartment(String deptId);

    /** 按 ID 读取单个分部（hrmsubcompany，来源 E9 SubCompanyComInfo 缓存）。 */
    JSONObject getSubCompany(String subId);

    /** OA 有效账号人员列表（ResourceComInfo 缓存）。 */
    JSONArray listUsers(String keyword) throws Exception;

    /** OA 有效账号人员列表（按登录人的 OA 分权范围过滤）。 */
    JSONArray listUsers(String keyword, String operatorId) throws Exception;

    /** 按 ID 读取单个 OA 账号（ResourceComInfo 缓存）。 */
    JSONObject getUser(String userId) throws Exception;

    /** 部门名称。 */
    String getDeptName(String deptId);

    /** 仅更新人员基础信息（姓名/手机/邮箱/部门/分部），不修改登录名与账号权限。 */
    boolean updateUserBasic(String userId, String name, String mobile, String email,
                            String departmentId, String subcompanyId);

    /** 按登录名查询 OA 人员ID（不存在返回 null）。 */
    Integer findUserIdByLoginId(String loginid);

    /**
     * 在指定 OA 部门下新建账号（“企微数据基准”页把企微成员同步到 OA 时使用），
     * 返回新建人员ID；登录名重复或部门异常时抛出业务异常。
     */
    int createUser(String loginid, String name, String mobile, String email,
                   String departmentId, String subcompanyId, String password, String operatorId)
            throws Exception;

    /**
     * 在指定上级部门下新建 OA 部门（同步中心“企微 → OA”按范围同步使用）。
     *
     * <p>只写部门名称/简称/上级部门/分部/层级（hrmdepartment.id 是自增列，不显式写入），
     * 与 E9 平台自己的部门导入口径一致；新建后刷新 E9 部门缓存。</p>
     *
     * @return 含 {@code id}（新部门ID）、{@code name}、{@code parent}、{@code subcompanyid1}
     */
    JSONObject createDepartment(String name, String parentDeptId, String operatorId);

    /**
     * 新建 OA 部门（指定上级部门，或“落在某分部下的顶级部门”两种口径）。
     *
     * <p>同步中心“企微 → OA”按范围同步用：OA 落点部门非必选——</p>
     * <ul>
     *   <li>{@code parentDeptId} 非空：在该上级部门下新建（分部/层级随上级部门）；</li>
     *   <li>{@code parentDeptId} 为空 + {@code subCompanyId} 非空：在指定分部下新建<b>顶级部门</b>
     *       （{@code supdepid=0}、{@code tlevel=1}、{@code subcompanyid1}=该分部），
     *       用于“企微 → OA 只选了 OA 落点分部、没选落点部门”的场景。</li>
     * </ul>
     *
     * @return 含 {@code id}（新部门ID）、{@code name}、{@code parent}、{@code subcompanyid1}
     */
    JSONObject createDepartment(String name, String parentDeptId, String subCompanyId, String operatorId);

    /** 更新 OA 部门名称（同步中心“企微 → OA”按范围同步使用）；名称一致时不写库。 */
    boolean updateDepartmentName(String deptId, String name, String operatorId);

    /**
     * 把已有 OA 部门挂靠到新的上级（同步中心“企微 → OA”按范围同步使用）。
     *
     * <p>口径与 E9 平台自己的“部门调整”一致：改写上级部门 / 上级部门路径 / 所属分部，
     * 再用 {@code OrgOperationUtil#updateDepartmentLevel} 重算该部门与全部下级部门的层级，
     * 最后刷新部门缓存；名称、简称、显示顺序不受影响（改名见 {@link #updateDepartmentName}）。</p>
     *
     * <p>同步中心的落点口径是“来源挂在落点下面”：命中已有 OA 部门（部门绑定 / 同名）时
     * 必须按本次落点调整挂靠，而不是只改名不动位置。</p>
     *
     * @param parentDeptId  新的上级部门ID；为空表示“在指定分部下作为顶级部门”（此时 subCompanyId 必填）
     * @param subCompanyId 目标分部ID（parentDeptId 非空时忽略，按上级部门所属分部取）
     * @return true = 确实调整了挂靠；false = 本来就在该上级下（无需调整）
     */
    boolean moveDepartment(String deptId, String parentDeptId, String subCompanyId, String operatorId);
}
