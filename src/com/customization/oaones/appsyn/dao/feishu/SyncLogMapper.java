package com.customization.oaones.appsyn.dao.feishu;

import com.customization.oaones.appsyn.entity.feishu.SyncLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 同步日志表 Mapper。
 */
public interface SyncLogMapper {

    /** 日志列表，入参含 cfgId/direction/limit。 */
    List<SyncLog> list(Map<String, Object> params);

    int insert(@Param("table") String table, @Param("e") SyncLog entity);

    Integer findIdByUuid(@Param("table") String table, @Param("modeuuid") String modeuuid);
}
