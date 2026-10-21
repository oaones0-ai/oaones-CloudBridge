package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.MtgPlanItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会议 / 日程同步「计划任务」**时间段明细**表 Mapper
 * （表名由入参 {@code table} 传入，取值见 {@code OaonesProps#mtgPlanItemTable}）。
 *
 * <p>一行 = 一个时间段（适用日 + 起止时间 + 轮询间隔）；页面保存时**整表重写**
 * （先删本计划的行、再按页面顺序逐行插入），所以这里只需要“按计划取全部 / 删全部 / 逐行插入”。</p>
 */
public interface MtgPlanItemMapper {

    /** 取某个计划的全部时间段（按页面顺序）。 */
    List<MtgPlanItem> listByPlan(@Param("table") String table, @Param("planId") int planId);

    /** 删除某个计划的全部时间段（保存时整表重写用）。 */
    int deleteByPlan(@Param("table") String table, @Param("planId") int planId);

    /** 插入一个时间段。 */
    int insert(@Param("table") String table, @Param("e") MtgPlanItem entity);
}
