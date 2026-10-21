package com.customization.oaones.appsyn.msg.feishu.dao;

import com.customization.oaones.appsyn.msg.feishu.entity.MsgLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 推送日志（状态表）Mapper。
 *
 * <p>本表只存业务状态；报文与结果在接口日志中心 uf_oaones_api_log，读取时按
 * {@code api_log_id} 左关联（{@code apiTable} 传入接口日志表名）带出。</p>
 */
public interface MsgLogMapper {

    int insert(@Param("table") String table, @Param("e") MsgLog entity);

    int updateResult(@Param("table") String table, @Param("e") MsgLog entity);

    /** 单条（按接口日志表左关联带出报文/结果，供详情与重推使用）。 */
    MsgLog getById(@Param("table") String table,
                   @Param("apiTable") String apiTable,
                   @Param("id") long id);

    /** 幂等校验：窗口内同一幂等键是否存在“待推送/成功”的日志。 */
    int countDedup(@Param("table") String table,
                   @Param("dedupKey") String dedupKey,
                   @Param("sinceDate") String sinceDate,
                   @Param("sinceTime") String sinceTime);

    /** 列表查询（行号分页，兼容 SQL Server 2008+；左关联接口日志带出耗时/结果）。 */
    List<MsgLog> page(@Param("table") String table,
                      @Param("apiTable") String apiTable,
                      @Param("fromDate") String fromDate,
                      @Param("fromTime") String fromTime,
                      @Param("toDate") String toDate,
                      @Param("toTime") String toTime,
                      @Param("bizId") String bizId,
                      @Param("stdType") Integer stdType,
                      @Param("status") Integer status,
                      @Param("keyword") String keyword,
                      @Param("offset") int offset,
                      @Param("limit") int limit);

    int count(@Param("table") String table,
              @Param("apiTable") String apiTable,
              @Param("fromDate") String fromDate,
              @Param("fromTime") String fromTime,
              @Param("toDate") String toDate,
              @Param("toTime") String toTime,
              @Param("bizId") String bizId,
              @Param("stdType") Integer stdType,
              @Param("status") Integer status,
              @Param("keyword") String keyword);

    /** 到期需重试的失败日志（可重试错误、未达重试上限）。 */
    List<MsgLog> listRetryable(@Param("table") String table,
                               @Param("now") String now,
                               @Param("maxRetry") int maxRetry,
                               @Param("limit") int limit);

    /** 看板：区间内总体统计（平均耗时从接口日志取）。 */
    Map<String, Object> sumStats(@Param("table") String table,
                                 @Param("apiTable") String apiTable,
                                 @Param("fromDate") String fromDate,
                                 @Param("fromTime") String fromTime,
                                 @Param("toDate") String toDate,
                                 @Param("toTime") String toTime);

    /** 看板：按消息类型统计。 */
    List<Map<String, Object>> groupByType(@Param("table") String table,
                                          @Param("fromDate") String fromDate,
                                          @Param("fromTime") String fromTime,
                                          @Param("toDate") String toDate,
                                          @Param("toTime") String toTime);

    /** 看板：失败原因 TOP（失败原因从接口日志取）。 */
    List<Map<String, Object>> failTop(@Param("table") String table,
                                      @Param("apiTable") String apiTable,
                                      @Param("fromDate") String fromDate,
                                      @Param("fromTime") String fromTime,
                                      @Param("toDate") String toDate,
                                      @Param("toTime") String toTime,
                                      @Param("limit") int limit);

    int deleteBefore(@Param("table") String table,
                     @Param("beforeDate") String beforeDate,
                     @Param("beforeTime") String beforeTime);
}
