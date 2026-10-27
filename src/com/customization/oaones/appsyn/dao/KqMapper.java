package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.KqFixedSchedule;
import com.customization.oaones.appsyn.entity.KqGroup;
import com.customization.oaones.appsyn.entity.KqHoliday;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * E9 **标准考勤表**的只读查询（会议 / 日程同步「计划任务」的工作日 / 非工作日口径来源）。
 *
 * <p>只读三张标准表，不改它们的结构：{@code kq_group}（考勤组）、
 * {@code kq_fixedschedulce}（固定排班：星期几上班）、{@code kq_HolidaySet}（节假日 / 调休 / 调配工作日）。
 * 表名写死为 E9 标准表名（与平台自己的 SQL 一致），不放到 oaones.appsyn.properties 里配。</p>
 *
 * <p>调用方一律经 {@link com.customization.oaones.appsyn.common.OaonesWorkday}（带缓存），
 * 不要直接在业务代码里循环查这几张表。</p>
 */
public interface KqMapper {

    /** 考勤组下拉（未删除的组，按 id 排序）。 */
    List<KqGroup> listGroups();

    /** 某个考勤组的固定排班（星期几 → 班次）；没有行 = 该组不用固定排班。 */
    List<KqFixedSchedule> listFixedSchedule(@Param("groupId") int groupId);

    /**
     * 某个考勤组在 [from, to] 区间内的节假日 / 调休 / 调配工作日
     * （{@code holidayDate} 是 char(10) 的 yyyy-MM-dd，按字符串比较即可）。
     */
    List<KqHoliday> listHolidays(@Param("groupId") int groupId,
                                 @Param("from") String from,
                                 @Param("to") String to);
}
