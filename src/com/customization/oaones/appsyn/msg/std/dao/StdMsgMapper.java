package com.customization.oaones.appsyn.msg.std.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 泛微标准「消息类型设置」只读 Mapper（分组 / 消息来源 / 消息类型）。
 *
 * <p>读的都是<b>平台标准表</b>（{@code ecology_biz_mobile_group} /
 * {@code ecology_message_type} / {@code ecology_message_group} /
 * {@code ecology_biz_mobile_config}）——这些表由泛微标准页
 * {@code /spa/smallApp/static4engine/engine.html#/ecAndEM/messagetypeconfig} 维护，
 * 本模块<b>只读不写</b>，与既有 {@code MsgTypeMapper#listStandardTypes}（读 {@code ecology_message_type}）
 * 同一口径。三个连接器（企微 / 钉钉 / 飞书）共用本 Mapper，天然一致。</p>
 *
 * <p>SQL 里标准表的名称是<b>平台固定表名</b>（不是本模块的业务表），因此不配到
 * {@code oaones.appsyn.properties}；本模块自己的业务表仍然只从 properties 取表名。</p>
 */
public interface StdMsgMapper {

    /** 标准「分组设置」清单（{@code ecology_biz_mobile_group}），按排序号、ID。 */
    List<Map<String, Object>> listGroups();

    /** 标准「消息来源」清单（{@code ecology_message_type}），名称按语言取。 */
    List<Map<String, Object>> listSources(@Param("lang") int lang);

    /** 标准「所属模块」清单（{@code ecology_message_group}），名称按语言取。 */
    List<Map<String, Object>> listModules(@Param("lang") int lang);

    /** 标准「消息类型」清单（{@code ecology_biz_mobile_config}，未删除的），按 ID。 */
    List<Map<String, Object>> listTypes();
}
