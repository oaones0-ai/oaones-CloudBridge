package com.customization.oaones.appsyn.dao.wecom;

import com.customization.oaones.appsyn.entity.wecom.DeptTmp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企微部门快照表 Mapper。
 */
public interface DeptTmpMapper {

    /** 快照刷新前把该租户全部记录置为“关闭”。 */
    int closeAll(@Param("table") String table,
                 @Param("corpid") String corpid,
                 @Param("type") int type,
                 @Param("status") int status,
                 @Param("syncTime") String syncTime);

    Integer findWxDeptId(@Param("table") String table,
                         @Param("corpid") String corpid,
                         @Param("wxDeptId") int wxDeptId,
                         @Param("type") int type);

    Integer findNormalWxDeptId(@Param("table") String table,
                               @Param("corpid") String corpid,
                               @Param("wxDeptId") int wxDeptId,
                               @Param("type") int type,
                               @Param("status") int status);

    List<DeptTmp> listNormal(@Param("table") String table,
                             @Param("corpid") String corpid,
                             @Param("type") int type,
                             @Param("status") int status);

    int insertDept(@Param("table") String table, @Param("e") DeptTmp entity);

    int updateDept(@Param("table") String table, @Param("e") DeptTmp entity);

    /**
     * 维护免绑定标记：{@code noBind = 0} 标记免绑定，传 {@code null} 取消（回到“不写值”的正常态）。
     *
     * <p>本表只用 0 表达免绑定，取消其它状态值。</p>
     */
    int updateNoBind(@Param("table") String table,
                     @Param("corpid") String corpid,
                     @Param("wxDeptId") int wxDeptId,
                     @Param("type") int type,
                     @Param("noBind") Integer noBind);
}
