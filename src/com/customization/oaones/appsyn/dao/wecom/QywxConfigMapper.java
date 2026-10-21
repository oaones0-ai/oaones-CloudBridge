package com.customization.oaones.appsyn.dao.wecom;

import com.customization.oaones.appsyn.entity.wecom.QywxConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企微租户配置表 Mapper（增删改查）。
 *
 * <p>表名由 oaones.properties 配置，通过 {@code ${table}} 传入；
 * 租户表与钉钉连接器共用（设置类表结构可复用时不新建表），
 * 本连接器一律按 {@code type = 0}（企微）过滤。</p>
 */
public interface QywxConfigMapper {

    List<QywxConfig> list(@Param("table") String table);

    /** 指定平台的租户配置（0 企微 / 1 钉钉）。 */
    List<QywxConfig> listByType(@Param("table") String table, @Param("type") int type);

    QywxConfig getById(@Param("table") String table, @Param("id") int id);

    Integer getIdByCorpId(@Param("table") String table, @Param("corpId") String corpId,
                          @Param("type") int type);

    Integer getIdByCorpIdExcept(@Param("table") String table,
                                @Param("corpId") String corpId,
                                @Param("id") int id,
                                @Param("type") int type);

    int insert(@Param("table") String table, @Param("e") QywxConfig entity);

    int update(@Param("table") String table, @Param("e") QywxConfig entity);

    /** 单独更新“消息通知”开关（独立菜单“消息设置 → 租户开关”，不影响其它字段）。 */
    int updateMsgEnable(@Param("table") String table, @Param("id") int id,
                        @Param("msgEnable") int msgEnable,
                        @Param("modifier") int modifier, @Param("now") String now);

    /** 取某租户的消息通知开关（推送前置校验用；租户不存在返回 null）。 */
    Integer getMsgEnable(@Param("table") String table, @Param("id") int id);

    /** 一次性取全部租户的消息通知开关（id → msg_enable）；列未建时调用方按“全部开启”兜底。 */
    java.util.List<java.util.Map<String, Object>> listMsgEnable(@Param("table") String table);

    int deleteById(@Param("table") String table, @Param("id") int id);
}
