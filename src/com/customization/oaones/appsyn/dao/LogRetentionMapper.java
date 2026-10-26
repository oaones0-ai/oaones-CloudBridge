package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.LogRetentionSetting;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 日志保留配置与通用过期删除 Mapper。
 *
 * <p>所有 OAones 日志表都有 {@code modedatacreatedate / modedatacreatetime}
 * 标准时间列，因此可以用同一条删除语句按精确时间截点清理。</p>
 */
public interface LogRetentionMapper {

    List<LogRetentionSetting> list(@Param("table") String table);

    LogRetentionSetting getByCode(@Param("table") String table, @Param("logCode") String logCode);

    Long countRows(@Param("table") String table);

    int insert(@Param("table") String table, @Param("e") LogRetentionSetting entity);

    int updateByCode(@Param("table") String table, @Param("e") LogRetentionSetting entity);

    int deleteBefore(@Param("table") String table,
                     @Param("beforeDate") String beforeDate,
                     @Param("beforeTime") String beforeTime);
}
