package com.customization.oaones.appsyn.msg.dingtalk.dao;

import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgTypeApp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息类型「推送目标应用」明细 Mapper（一行 = 一个消息类型 + 一个租户 + 一个应用）。
 *
 * <p>消息类型由 {@code std_config_id}（泛微标准「消息类型」ID，字符串）或
 * {@code std_type}（未纳入标准消息类型的独立来源编号）定位，两者口径与
 * {@link MsgTypeMapper} 的 {@code uf_oaones_msg_type} 完全一致。</p>
 */
public interface MsgTypeAppMapper {

    /**
     * 本平台全部绑定行（页面一次取回后按消息类型分组）。
     *
     * <p>平台在各自的 Mapper XML 里固定（企微 type = 0 / 钉钉 1 / 飞书 2），与
     * {@link MsgTypeMapper} 同口径，因此这里不再传平台参数。</p>
     */
    List<MsgTypeApp> list(@Param("table") String table);

    /** 某个消息类型（标准类型或独立来源）的全部绑定行。 */
    List<MsgTypeApp> listByKey(@Param("table") String table,
                               @Param("stdConfigId") String stdConfigId,
                               @Param("stdType") int stdType);

    /** 某个消息类型 + 某个租户的绑定行（没有返回 null）。 */
    MsgTypeApp getByKeyAndCfg(@Param("table") String table,
                              @Param("stdConfigId") String stdConfigId,
                              @Param("stdType") int stdType,
                              @Param("cfgId") int cfgId);

    int insert(@Param("table") String table, @Param("e") MsgTypeApp entity);

    int update(@Param("table") String table, @Param("e") MsgTypeApp entity);

    int deleteById(@Param("table") String table, @Param("id") int id);

    /** 删掉某个消息类型 + 某个租户的绑定行（页面把某个租户的应用改回“默认”时用）。 */
    int deleteByKeyAndCfg(@Param("table") String table,
                          @Param("stdConfigId") String stdConfigId,
                          @Param("stdType") int stdType,
                          @Param("cfgId") int cfgId);

    /** 删掉某个消息类型的全部绑定行（消息类型在标准页删除后清理用）。 */
    int deleteByKey(@Param("table") String table,
                    @Param("stdConfigId") String stdConfigId,
                    @Param("stdType") int stdType);
}
