package com.customization.oaones.appsyn.common;

import com.engine.common.service.HrmCommonService;
import com.engine.common.service.impl.HrmCommonServiceImpl;
import weaver.conn.RecordSet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAones 全局超级管理员判定。
 *
 * <p>“超管”= 系统管理员（{@code user.id=1}）<b>或</b>
 * {@code oaones.properties#total.admin.roleid} 这个 E9 角色内的人员。
 * 超管在 OAones 里拥有**全部页面的全部管理权限**，口径与 {@code user.id=1} 完全一致
 * （所有平台 / 所有租户，含未指定维护人的租户）。</p>
 *
 * <p><b>角色成员怎么判</b>：不自己拼“人员 / 分部 / 部门 / 岗位 + OA 安全级别”的规则，
 * 直接调平台标准能力
 * {@link HrmCommonService#getHrmRoleMembers_queryConditionSql(int, String)}
 * （平台自带的角色成员 / 共享判定用的就是它，见 {@code com.engine.hrm.util.HrmRoleMemberUtil}
 * 与平台 {@code HrmRolesMembers.jsp}）：它按登录人返回 {@code HrmRoleMembers} 的查询条件，
 * 覆盖四类成员——</p>
 * <ol>
 *   <li>角色里按「人员」添加的成员（resourcetype in (1,7,8)，resourceid = 登录人ID）；</li>
 *   <li>角色里按「分部」添加的成员（resourcetype=2，命中登录人所在分部，
 *       且登录人的 OA 安全级别落在该成员行的 seclevelfrom ~ seclevelto 区间内）；</li>
 *   <li>角色里按「部门」添加的成员（resourcetype=3，口径同上）；</li>
 *   <li>角色里按「岗位」添加的成员（resourcetype=5，含岗位的适用分部 / 部门范围）。</li>
 * </ol>
 * <p>因此“往角色里加一个人 / 一个部门 / 一个分部（并框定安全级别）”都会自动获得超管权限，
 * 不需要在本模块单独登记人员ID。</p>
 *
 * <p><b>缓存</b>：判定结果按登录人缓存 60 秒（角色成员变动最多 1 分钟生效），
 * 避免 OAones 每个页面 / 每次接口都查一次库；“未配置角色”不走缓存，直接返回。</p>
 */
public final class OaonesAdmin {

    /** 判定结果缓存时长（毫秒）：角色成员增删后最多 1 分钟生效。 */
    private static final long CACHE_TTL_MS = 60000L;

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<String, Cached>();

    /** 缓存项：某登录人是否命中超管角色。 */
    private static final class Cached {
        private final boolean value;
        private final long expireAt;

        Cached(boolean value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private OaonesAdmin() {
    }

    /**
     * 是否拥有 OAones 的全部管理权限：系统管理员（user.id=1）或超管角色（total.admin.roleid）内的人员。
     *
     * @param operatorId 登录人ID（OA 人员ID；空 / 非法 / 非正数一律按“不是超管”处理）
     */
    public static boolean isTotalAdmin(String operatorId) {
        int uid = OaonesUtil.i(OaonesUtil.s(operatorId).trim(), 0);
        if (uid <= 0) {
            return false;
        }
        if (uid == OaonesConst.SYS_ADMIN_USER_ID) {
            return true;
        }
        int roleId = OaonesProps.totalAdminRoleId();
        if (roleId <= 0) {
            return false;
        }
        String key = uid + "@" + roleId;
        Cached cached = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAt > now) {
            return cached.value;
        }
        boolean inRole = inRole(uid, roleId);
        CACHE.put(key, new Cached(inRole, now + CACHE_TTL_MS));
        return inRole;
    }

    /**
     * 某人是否在指定 E9 角色内（按平台标准口径判定：人员 / 分部 / 部门 / 岗位四类成员，
     * 分部 / 部门成员还要按登录人的 OA 安全级别落在成员行的区间内）。
     *
     * @param uid    OA 人员ID
     * @param roleId E9 角色ID（HrmRoles.id）
     */
    public static boolean inRole(int uid, int roleId) {
        if (uid <= 0 || roleId <= 0) {
            return false;
        }
        try {
            // 平台标准能力：按登录人给出 HrmRoleMembers 的查询条件（别名留空 = 不限定表别名）
            HrmCommonService hrmCommonService = new HrmCommonServiceImpl();
            String condition = OaonesUtil.s(hrmCommonService.getHrmRoleMembers_queryConditionSql(uid, ""));
            if (condition.trim().length() == 0) {
                return false;
            }
            String sql = "select count(1) from HrmRoleMembers where roleid = " + roleId + " and " + condition;
            RecordSet rs = new RecordSet();
            rs.executeSql(sql);
            return rs.next() && rs.getInt(1) > 0;
        } catch (Throwable t) {
            // 角色表缺失 / 取数异常时按“不是超管”处理（退回“只有 user.id=1 是超管”），不影响页面打开
            OaonesLog.error("判定超管角色（total.admin.roleid=" + roleId + "，uid=" + uid
                    + "）失败，本次按“不是超管”处理", t);
            return false;
        }
    }
}
