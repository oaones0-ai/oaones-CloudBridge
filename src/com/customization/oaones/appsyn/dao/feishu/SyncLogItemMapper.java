package com.customization.oaones.appsyn.dao.feishu;

import com.customization.oaones.appsyn.entity.feishu.SyncLogItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 同步日志明细表 Mapper（一行一个同步对象：单条部门 / 单条人员）。
 */
public interface SyncLogItemMapper {

    /** 明细列表，入参含 logId/itemType/limit。 */
    List<SyncLogItem> list(Map<String, Object> params);

    int insert(@Param("table") String table, @Param("e") SyncLogItem entity);
}
