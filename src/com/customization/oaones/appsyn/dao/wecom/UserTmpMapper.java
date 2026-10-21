package com.customization.oaones.appsyn.dao.wecom;

import com.customization.oaones.appsyn.dto.wecom.WxUserRow;
import com.customization.oaones.appsyn.entity.wecom.UserTmp;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 企微人员快照表 Mapper。
 */
public interface UserTmpMapper {

    int closeAll(@Param("table") String table,
                 @Param("corpid") String corpid,
                 @Param("type") int type,
                 @Param("status") int status,
                 @Param("syncTime") String syncTime);

    String findWxUserId(@Param("table") String table,
                        @Param("corpid") String corpid,
                        @Param("wxUserId") String wxUserId,
                        @Param("type") int type);

    String findNormalWxUserId(@Param("table") String table,
                              @Param("corpid") String corpid,
                              @Param("wxUserId") String wxUserId,
                              @Param("type") int type,
                              @Param("status") int status);

    /** 已标记免绑定的成员（no_bind = 0）。 */
    String findNoBindWxUserId(@Param("table") String table,
                              @Param("corpid") String corpid,
                              @Param("wxUserId") String wxUserId,
                              @Param("type") int type);

    /** 正常状态成员及其主要数据（关联部门快照取部门名称）。 */
    List<WxUserRow> listNormal(Map<String, Object> params);

    /**
     * 单个正常状态成员（关联部门快照取部门名称），入参含 corpid/wxUserId/type/status 与全部业务表名。
     *
     * <p>个人账号中心用于把本人绑定的企微成员名称/部门展示出来，避免整租户快照全量查询。</p>
     */
    WxUserRow findNormalDetail(Map<String, Object> params);

    int insertUser(@Param("table") String table, @Param("e") UserTmp entity);

    int updateUser(@Param("table") String table, @Param("e") UserTmp entity);

    /**
     * 维护免绑定标记：{@code noBind = 0} 标记免绑定，传 {@code null} 取消（回到“不写值”的正常态）。
     *
     * <p>本表只用 0 表达免绑定，取消其它状态值。</p>
     */
    int updateNoBind(@Param("table") String table,
                     @Param("corpid") String corpid,
                     @Param("wxUserId") String wxUserId,
                     @Param("type") int type,
                     @Param("noBind") Integer noBind);
}
