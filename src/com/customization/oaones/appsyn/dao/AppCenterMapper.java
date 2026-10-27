package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.AppInfoRow;
import com.customization.oaones.appsyn.entity.AppInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 应用中心 Mapper（表 uf_oaones_appsyn_app）。
 *
 * <p>表名由 {@code OaonesParam} 统一放入入参 Map（{@code ${appTable}}），
 * 与应用绑定表（{@code uf_oaones_appsyn_app_bind}）是两张不同的表，不能混用。</p>
 */
public interface AppCenterMapper {

    /** 列表（keyword 同时匹配应用名 / 地址 / 单点登录类 / 备注，enable 为空表示不过滤状态）。 */
    List<AppInfoRow> listRows(Map<String, Object> params);

    AppInfo findById(@Param("table") String table, @Param("id") int id);

    /** 同名应用的记录ID（唯一性校验，excludeId 用于“编辑时排除自己”）。 */
    Integer findIdByName(@Param("table") String table,
                         @Param("appName") String appName,
                         @Param("excludeId") int excludeId);

    /** 还引用同一图标地址的记录数（删旧图标文件前判断，excludeId 用于“排除当前记录”）。 */
    Integer countByIcon(@Param("table") String table,
                        @Param("icon") String icon,
                        @Param("excludeId") int excludeId);

    int insert(@Param("table") String table, @Param("e") AppInfo entity);

    int update(@Param("table") String table, @Param("e") AppInfo entity);

    int updateState(@Param("table") String table,
                    @Param("id") int id,
                    @Param("enable") int enable,
                    @Param("modifier") int modifier,
                    @Param("modifyTime") String modifyTime);

    int deleteById(@Param("table") String table, @Param("id") int id);
}
