package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.AttPlanItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考勤同步「计划任务」时间段明细表 Mapper
 * （表名由入参 {@code table} 传入，取值见 {@code OaonesProps#attPlanItemTable}）。
 *
 * <p>计划任务是**每条设置一份**（需求 Q2）：一行 = 一个时间段（适用日 + 起止时间 + 轮询间隔），
 * 挂在设置行上（cfg_id）；页面保存时按设置**整表重写**，
 * 所以这里只需要“按设置取全部 / 按设置删全部 / 逐行插入”。</p>
 */
public interface AttPlanItemMapper {

    /** 取某条设置的全部时间段（按页面顺序）。 */
    List<AttPlanItem> listByCfg(@Param("table") String table, @Param("cfgId") int cfgId);

    /** 取多条设置的全部时间段（调度器一轮扫描用）。 */
    List<AttPlanItem> listByCfgIds(@Param("table") String table, @Param("cfgIds") List<Integer> cfgIds);

    /** 删除某条设置的全部时间段（保存时整表重写用）。 */
    int deleteByCfg(@Param("table") String table, @Param("cfgId") int cfgId);

    /** 插入一个时间段。 */
    int insert(@Param("table") String table, @Param("e") AttPlanItem entity);
}
