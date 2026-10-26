package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.SyncResultItem;

import java.util.List;
import java.util.Map;

/**
 * 同步结果独立页面查询 Mapper。
 *
 * <p>一次查询直接返回“部门 / 分部 / 人员”粒度明细，入参 Map 中同时携带同步日志表、
 * 明细表、租户配置表的物理表名以及页面筛选条件。</p>
 */
public interface SyncResultMapper {

    List<SyncResultItem> list(Map<String, Object> params);

    Integer count(Map<String, Object> params);

    Map<String, Object> stats(Map<String, Object> params);
}
