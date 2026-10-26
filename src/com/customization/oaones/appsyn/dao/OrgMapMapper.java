package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.OrgMapRow;
import com.customization.oaones.appsyn.entity.OrgMap;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * OA 分部 - 平台租户一对一映射 Mapper。
 */
public interface OrgMapMapper {

    List<OrgMapRow> listByType(@Param("table") String table,
                               @Param("cfgTable") String cfgTable,
                               @Param("type") int type);

    OrgMap findById(@Param("table") String table, @Param("type") int type,
                    @Param("id") int id);

    OrgMap findBySub(@Param("table") String table, @Param("type") int type,
                     @Param("oaSubId") int oaSubId);

    OrgMap findByCfg(@Param("table") String table, @Param("type") int type,
                     @Param("cfgId") int cfgId);

    int insert(@Param("table") String table, @Param("e") OrgMap entity);

    int update(@Param("table") String table, @Param("e") OrgMap entity);

    int deleteById(@Param("table") String table, @Param("type") int type,
                   @Param("id") int id);
}
