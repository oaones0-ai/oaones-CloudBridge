package com.customization.oaones.appsyn.msg.dao;

import com.customization.oaones.appsyn.msg.entity.MsgApp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 推送用企微应用 Mapper：**只读**“应用绑定”表（uf_oaones_appsyn_app_bind）并关联租户配置。
 *
 * <p>应用与“应用绑定”共用同一张表，应用的新增/修改/停用都在“应用绑定”页完成，
 * 因此这里没有增删改语句。</p>
 */
public interface MsgAppMapper {

    /** 全部应用（含停用），启用优先 + 租户 + ID 排序。 */
    List<MsgApp> list(@Param("appBindTable") String appBindTable, @Param("cfgTable") String cfgTable);

    /** 启用中的应用。 */
    List<MsgApp> listEnabled(@Param("appBindTable") String appBindTable, @Param("cfgTable") String cfgTable);

    /** 按应用绑定记录ID取一条。 */
    MsgApp getById(@Param("appBindTable") String appBindTable,
                   @Param("cfgTable") String cfgTable,
                   @Param("id") int id);

    /** 指定租户下启用中的应用（按ID排序，取第一条作为该租户的默认推送应用）。 */
    List<MsgApp> listEnabledByCfgId(@Param("appBindTable") String appBindTable,
                                    @Param("cfgTable") String cfgTable,
                                    @Param("cfgId") int cfgId);
}
