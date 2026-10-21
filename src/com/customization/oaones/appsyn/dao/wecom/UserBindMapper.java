package com.customization.oaones.appsyn.dao.wecom;

import com.customization.oaones.appsyn.dto.wecom.UserBindRow;
import com.customization.oaones.appsyn.entity.wecom.UserBind;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 人员绑定表 Mapper（增删改查 + 绑定校验/已绑定ID集合查询）。
 */
public interface UserBindMapper {

    /** 绑定详情列表（关联 OA 人员/部门、企微租户配置），入参含 cfgId/keyword 与全部业务表名。 */
    List<UserBindRow> listRows(Map<String, Object> params);

    /**
     * 指定 OA 人员本人（各租户）的绑定记录，入参含 oaUserId 与全部业务表名。
     *
     * <p>个人账号中心用：一个 OA 人员在每个企微租户最多一条绑定
     * （`(cfg_id, oa_user_id)` 唯一），因此结果行数 = 本人已绑定的租户数。</p>
     */
    List<UserBindRow> listRowsByOaUser(Map<String, Object> params);

    /** 同步引擎使用的绑定明细（关联 OA 人员）。 */
    List<UserBindRow> listBoundRows(@Param("table") String table, @Param("cfgId") int cfgId);

    UserBind findById(@Param("table") String table, @Param("id") int id);

    Integer findIdByOaUser(@Param("table") String table,
                           @Param("cfgId") int cfgId,
                           @Param("oaUserId") int oaUserId);

    /** 指定 OA 人员与企微成员之间是否已存在绑定。 */
    Integer findPairId(@Param("table") String table,
                       @Param("cfgId") int cfgId,
                       @Param("oaUserId") int oaUserId,
                       @Param("wxUserId") String wxUserId);

    /** 该企微成员是否已存在启用中的绑定（免绑定标记的校验）。 */
    Integer findActiveIdByWxUser(@Param("table") String table,
                                 @Param("cfgId") int cfgId,
                                 @Param("wxUserId") String wxUserId);

    /** 该 OA 人员是否已存在启用中的绑定（bind_state=1，OA 侧免绑定的校验）。 */
    Integer findActiveIdByOaUser(@Param("table") String table,
                                 @Param("cfgId") int cfgId,
                                 @Param("oaUserId") int oaUserId);

    /** 该 OA 人员是否已标记免绑定（bind_state=0）。 */
    Integer findNoBindIdByOaUser(@Param("table") String table,
                                 @Param("cfgId") int cfgId,
                                 @Param("oaUserId") int oaUserId);

    /**
     * 指定企微成员在指定租户下已绑定的全部 OA 人员ID（仅 {@code bind_state=1}）。
     *
     * <p>“企微工作台应用免登录”用：OA→企微为多对一时，同一企微成员可能被多个 OA 人员绑定，
     * 免登录取其中人员ID最小的一条（调用方记日志），避免出现“找不到登录人”。</p>
     */
    List<Integer> listBoundOaUserIdsByWxUser(Map<String, Object> params);

    int countActiveByCfgId(@Param("table") String table, @Param("cfgId") int cfgId);

    /** 已绑定的 OA 人员ID集合；cfgId 为空表示不过滤租户。 */
    List<String> listBoundOaUserIds(Map<String, Object> params);

    /** 已绑定的企微成员ID集合。 */
    List<String> listBoundWxUserIds(Map<String, Object> params);

    /** 已标记免绑定的 OA 人员ID集合（bind_state=0）。 */
    List<String> listNoBindOaUserIds(@Param("table") String table, @Param("cfgId") int cfgId);

    int insert(@Param("table") String table, @Param("e") UserBind entity);

    int update(@Param("table") String table, @Param("e") UserBind entity);

    int deleteById(@Param("table") String table, @Param("id") int id);

    int markSynced(@Param("table") String table,
                   @Param("cfgId") int cfgId,
                   @Param("oaUserId") int oaUserId,
                   @Param("direction") int direction,
                   @Param("syncTime") String syncTime);
}
