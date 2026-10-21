package com.customization.oaones.appsyn.dao.dingtalk;

import com.customization.oaones.appsyn.entity.dingtalk.DeptTmp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 钉钉部门快照表 Mapper。
 *
 * <p><b>入参名必须与 Mapper XML 里的占位符一致</b>：本表按“租户 + 部门”定位，
 * XML 用的是 {@code #{corpid}}（与企微共用同一张表、同一套列名），
 * 因此这里的入参也叫 {@code corpid}（钉钉侧它的取值是租户的 App ID，即 {@code corp_id} 列）。
 * 名称写成 {@code AppKey} 会让 MyBatis 抛
 * {@code There is no getter for property named 'corpid'}（XML 里找不到对应参数）。</p>
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
