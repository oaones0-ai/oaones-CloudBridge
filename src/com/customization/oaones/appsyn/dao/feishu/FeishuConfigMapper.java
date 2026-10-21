package com.customization.oaones.appsyn.dao.feishu;

import com.customization.oaones.appsyn.entity.feishu.FeishuConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 飞书租户配置表 Mapper（增删改查）。
 *
 * <p>表名由 oaones.properties 配置，通过 {@code ${table}} 传入；
 * 飞书与企微、钉钉共用同一张租户表，本连接器一律按 {@code type = 2}（飞书）过滤，
 * 企微 / 钉钉连接器分别按 {@code type = 0} / {@code type = 1} 过滤，互不可见。</p>
 */
public interface FeishuConfigMapper {

    List<FeishuConfig> list(@Param("table") String table);

    /** 指定平台的租户配置（0 飞书 / 1 飞书）。 */
    List<FeishuConfig> listByType(@Param("table") String table, @Param("type") int type);

    FeishuConfig getById(@Param("table") String table, @Param("id") int id);

    Integer getIdByCorpId(@Param("table") String table, @Param("corpId") String corpId,
                          @Param("type") int type);

    Integer getIdByCorpIdExcept(@Param("table") String table,
                                @Param("corpId") String corpId,
                                @Param("id") int id,
                                @Param("type") int type);

    int insert(@Param("table") String table, @Param("e") FeishuConfig entity);

    int update(@Param("table") String table, @Param("e") FeishuConfig entity);

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
