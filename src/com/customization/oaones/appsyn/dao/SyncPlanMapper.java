package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.SyncPlanCfg;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 同步中心“同步设置”表 Mapper（表名由入参 {@code table} 传入，取值见 OaonesProps）。
 *
 * <p>一行 = 一条同步规则；同一个“平台 + 租户 + 方向”<b>可以有多条规则</b>，
 * 因此除“生效口径”用的 {@link #get}（取第一条）外，规则一律按主键 {@code id} 定位。</p>
 */
public interface SyncPlanMapper {

    /** 规则列表（type / cfgId / direction / 可见租户 都可为空表示不过滤）；按 cfgid、direction、id 排序。 */
    List<SyncPlanCfg> list(@Param("table") String table,
                           @Param("type") Integer type,
                           @Param("cfgId") Integer cfgId,
                           @Param("direction") Integer direction,
                           @Param("cfgIds") List<Integer> cfgIds);

    /** 某个“平台 + 租户 + 方向”的<b>第一条</b>规则（多条时取 id 最小的一条；没配过返回 null）。 */
    SyncPlanCfg get(@Param("table") String table,
                    @Param("type") Integer type,
                    @Param("cfgId") Integer cfgId,
                    @Param("direction") Integer direction);

    /** 按主键取一条规则（页面行内操作：编辑 / 暂停启动 / 立即执行 / 删除）。 */
    SyncPlanCfg getById(@Param("table") String table, @Param("id") Integer id);

    /** 某“平台 + 租户 + 方向”最新插入的一条规则（新增后回读，返回给页面带 id）。 */
    SyncPlanCfg getLast(@Param("table") String table,
                        @Param("type") Integer type,
                        @Param("cfgId") Integer cfgId,
                        @Param("direction") Integer direction);

    /** 计划任务用：已启用且 next_run_time 已到点的设置。 */
    List<SyncPlanCfg> listDue(@Param("table") String table,
                              @Param("now") String now,
                              @Param("limit") Integer limit);

    int insert(@Param("table") String table, @Param("e") SyncPlanCfg entity);

    int update(@Param("table") String table, @Param("e") SyncPlanCfg entity);

    /**
     * 计划任务启停（同步设置列表的“暂停 / 启动”）：只动 enabled 与 next_run_time，
     * 其余设置（同步范围 / cron / 口径 / 备注 / 最近执行信息）一律不动。
     */
    int updateEnabled(@Param("table") String table,
                      @Param("id") Integer id,
                      @Param("enabled") Integer enabled,
                      @Param("nextRun") String nextRun,
                      @Param("modifier") Integer modifier,
                      @Param("modifyTime") String modifyTime);

    /** 按“平台 + 租户 + 方向”删除该档的<b>全部</b>规则（清理 / 兼容用；页面“删除”走 {@link #deleteById}）。 */
    int delete(@Param("table") String table,
               @Param("type") Integer type,
               @Param("cfgId") Integer cfgId,
               @Param("direction") Integer direction);

    /** 删除一条规则（按主键，不影响同一档的其它规则）。 */
    int deleteById(@Param("table") String table, @Param("id") Integer id);

    /**
     * 计划任务“认领”：把 next_run_time 从 oldNext 改成 newNext，返回受影响行数。
     *
     * <p>只有返回 1 的实例才执行该设置——多实例部署或重复触发时不会重复同步。</p>
     */
    int claimRun(@Param("table") String table,
                 @Param("id") Integer id,
                 @Param("oldNext") String oldNext,
                 @Param("newNext") String newNext,
                 @Param("runTime") String runTime,
                 @Param("runningText") String runningText,
                 @Param("modifyTime") String modifyTime);

    /** 计划任务执行后回写：最近执行时间 / 日志ID / 结果提示 / 下次执行时间。 */
    int updateRun(@Param("table") String table,
                  @Param("id") Integer id,
                  @Param("runTime") String runTime,
                  @Param("logId") Integer logId,
                  @Param("result") String result,
                  @Param("nextRun") String nextRun,
                  @Param("modifier") Integer modifier,
                  @Param("modifyTime") String modifyTime);
}
