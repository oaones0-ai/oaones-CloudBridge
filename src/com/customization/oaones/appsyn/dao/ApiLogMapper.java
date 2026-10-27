package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.ApiLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 接口日志 Mapper（表名由调用方按 {@code oaones.appsyn.properties#oaones_api_log_table} 传入）。
 */
public interface ApiLogMapper {

    /** 记一条接口日志（一次性写入最终结果，不需要回读自增ID）。 */
    int insert(@Param("table") String table, @Param("e") ApiLog entity);

    ApiLog getById(@Param("table") String table, @Param("id") long id);

    /** 列表查询（行号分页，兼容 SQL Server 2008+）。 */
    List<ApiLog> page(@Param("table") String table,
                      @Param("type") Integer type,
                      @Param("cfgId") Integer cfgId,
                      @Param("apiType") String apiType,
                      @Param("direction") Integer direction,
                      @Param("success") Integer success,
                      @Param("keyword") String keyword,
                      @Param("fromDate") String fromDate,
                      @Param("fromTime") String fromTime,
                      @Param("toDate") String toDate,
                      @Param("toTime") String toTime,
                      @Param("cfgIds") List<Integer> cfgIds,
                      @Param("offset") int offset,
                      @Param("limit") int limit);

    int count(@Param("table") String table,
              @Param("type") Integer type,
              @Param("cfgId") Integer cfgId,
              @Param("apiType") String apiType,
              @Param("direction") Integer direction,
              @Param("success") Integer success,
              @Param("keyword") String keyword,
              @Param("fromDate") String fromDate,
              @Param("fromTime") String fromTime,
              @Param("toDate") String toDate,
              @Param("toTime") String toTime,
              @Param("cfgIds") List<Integer> cfgIds);

    /** 区间内统计（看板用）：总数 / 成功 / 失败 / 平均耗时。 */
    Map<String, Object> sumStats(@Param("table") String table,
                                 @Param("type") Integer type,
                                 @Param("cfgId") Integer cfgId,
                                 @Param("apiType") String apiType,
                                 @Param("direction") Integer direction,
                                 @Param("keyword") String keyword,
                                 @Param("fromDate") String fromDate,
                                 @Param("fromTime") String fromTime,
                                 @Param("toDate") String toDate,
                                 @Param("toTime") String toTime,
                                 @Param("cfgIds") List<Integer> cfgIds);

    /** 按平台统计（看板用）。 */
    List<Map<String, Object>> groupByPlatform(@Param("table") String table,
                                              @Param("cfgIds") List<Integer> cfgIds,
                                              @Param("fromDate") String fromDate,
                                              @Param("fromTime") String fromTime,
                                              @Param("toDate") String toDate,
                                              @Param("toTime") String toTime);

    /** 按接口类型统计（看板用）。 */
    List<Map<String, Object>> groupByApiType(@Param("table") String table,
                                             @Param("cfgIds") List<Integer> cfgIds,
                                             @Param("fromDate") String fromDate,
                                             @Param("fromTime") String fromTime,
                                             @Param("toDate") String toDate,
                                             @Param("toTime") String toTime);

    /** 按平台 + 企业标识反查租户（记日志时用，取 id 与名称）。 */
    Map<String, Object> findCfg(@Param("cfgTable") String cfgTable,
                                @Param("type") int type,
                                @Param("corpId") String corpId);

    /** 清理早于某时间点的日志（保留天数维护）。 */
    int deleteBefore(@Param("table") String table,
                     @Param("beforeDate") String beforeDate,
                     @Param("beforeTime") String beforeTime);
}
