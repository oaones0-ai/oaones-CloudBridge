package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.MtgCron;
import org.apache.ibatis.annotations.Param;

/**
 * 会议 / 日程同步「计划任务」表 Mapper（表名由入参 {@code table} 传入，取值见 {@code OaonesProps#mtgCronTable}）。
 *
 * <p>这张表只保留一行（id = 1）：白天 / 夜间两套 Quartz cron + 启用开关，
 * 页面（会议同步 / 日程同步 → 同步设置）维护，内置调度器 {@code job.OaonesMtgSyncScheduler} 按它执行。</p>
 */
public interface MtgCronMapper {

    /** 取唯一一行（没有则返回 null，调用方按 oaones.appsyn.properties 的默认频率处理）。 */
    MtgCron findFirst(@Param("table") String table);

    /** 写入第一行（id = 1）。 */
    int insert(@Param("table") String table, @Param("e") MtgCron entity);

    /** 更新该行（页面保存计划任务）。 */
    int update(@Param("table") String table, @Param("e") MtgCron entity);
}
