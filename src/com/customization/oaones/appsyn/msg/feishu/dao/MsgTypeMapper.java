package com.customization.oaones.appsyn.msg.feishu.dao;

import com.customization.oaones.appsyn.msg.feishu.entity.MsgTypeDef;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息类型推送设置 Mapper。
 *
 * <p>一行 = 泛微标准页「应用中心 → 消息类型」里的一个消息类型
 * （{@code std_config_id = ecology_biz_mobile_config.id}），本模块只存自己的推送设置；
 * {@code std_config_id = 0} 的行是“未纳入任何标准消息类型”的独立来源，按 {@code std_type}（typecode）匹配。</p>
 */
public interface MsgTypeMapper {

    List<MsgTypeDef> list(@Param("table") String table);

    List<MsgTypeDef> listEnabled(@Param("table") String table);

    MsgTypeDef getById(@Param("table") String table, @Param("id") int id);

    MsgTypeDef getByStdType(@Param("table") String table, @Param("stdType") int stdType);

    /**
     * 按标准消息类型ID取本平台的推送设置行（未配置返回 null）。
     *
     * @param table       本平台的消息类型表名（oaones.appsyn.properties#oaones_msg_type_table）
     * @param stdConfigId 标准「消息类型」ID（{@code ecology_biz_mobile_config.id}，<b>字符串</b>：
     *                    内置类型是 "20" 这类数字，自定义类型是 "1LbVgIrt" 这类 8 位编号）
     */
    MsgTypeDef getByStdConfigId(@Param("table") String table, @Param("stdConfigId") String stdConfigId);

    Integer findIdByStdType(@Param("table") String table,
                            @Param("stdType") int stdType,
                            @Param("id") int id);

    /**
     * 同一标准消息类型是否已有配置行（排除自身 id，用于保存前的唯一性校验）。
     *
     * @param id 自身主键（新增时传 0）
     */
    Integer findIdByStdConfigId(@Param("table") String table,
                                @Param("stdConfigId") String stdConfigId,
                                @Param("id") int id);

    int insert(@Param("table") String table, @Param("e") MsgTypeDef entity);

    int update(@Param("table") String table, @Param("e") MsgTypeDef entity);

    int deleteById(@Param("table") String table, @Param("id") int id);

    /**
     * 泛微标准消息中心的消息类型（ECOLOGY_MESSAGE_TYPE + 多语言名称），
     * 供“消息类型映射”页面下拉选择，口径与平台标准浏览按钮一致。
     */
    List<java.util.Map<String, Object>> listStandardTypes(@Param("lang") int lang);
}
