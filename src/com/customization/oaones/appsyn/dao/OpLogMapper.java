package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.OpLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 操作监控日志 Mapper（表名由调用方按 {@code oaones.properties#oaones_op_log_table} 传入）。
 *
 * <p>另带两条与 E9 平台表（不做改名）的只读查询：菜单索引 {@link #listMenus()}、
 * 人员信息 {@link #findUser(int)}——它们都不是本模块的业务表，固定读 E9 标准表名。</p>
 */
public interface OpLogMapper {

    /** 记一条操作监控日志（一次性写入最终结果，不需要回读自增ID）。 */
    int insert(@Param("table") String table, @Param("e") OpLog entity);

    OpLog getById(@Param("table") String table, @Param("id") long id);

    /** 列表查询（行号分页，兼容 SQL Server 2008+）。 */
    List<OpLog> page(@Param("table") String table,
                     @Param("userId") Integer userId,
                     @Param("opType") String opType,
                     @Param("menuId") String menuId,
                     @Param("keyword") String keyword,
                     @Param("userKeyword") String userKeyword,
                     @Param("fromDate") String fromDate,
                     @Param("fromTime") String fromTime,
                     @Param("toDate") String toDate,
                     @Param("toTime") String toTime,
                     @Param("offset") int offset,
                     @Param("limit") int limit);

    int count(@Param("table") String table,
              @Param("userId") Integer userId,
              @Param("opType") String opType,
              @Param("menuId") String menuId,
              @Param("keyword") String keyword,
              @Param("userKeyword") String userKeyword,
              @Param("fromDate") String fromDate,
              @Param("fromTime") String fromTime,
              @Param("toDate") String toDate,
              @Param("toTime") String toTime);

    /** 区间内按事件类型统计（汇总条用）：type / totalCount。 */
    List<Map<String, Object>> groupByType(@Param("table") String table,
                                          @Param("userId") Integer userId,
                                          @Param("menuId") String menuId,
                                          @Param("keyword") String keyword,
                                          @Param("userKeyword") String userKeyword,
                                          @Param("fromDate") String fromDate,
                                          @Param("fromTime") String fromTime,
                                          @Param("toDate") String toDate,
                                          @Param("toTime") String toTime);

    /** 清理早于某时间点的日志（保留天数维护）。 */
    int deleteBefore(@Param("table") String table,
                     @Param("beforeDate") String beforeDate,
                     @Param("beforeTime") String beforeTime);

    /**
     * E9 菜单索引（平台标准表 LeftMenuInfo，只读）：
     * 取“有地址”的菜单，供“访问的页面 → 菜单名称”匹配。
     */
    List<Map<String, Object>> listMenus();

    /**
     * 人员信息（E9 标准表 HrmResource + HrmDepartment，只读）：
     * 登录名 / 姓名 / 主部门名称，用于补齐登录 / 退出记录的落库快照。
     */
    Map<String, Object> findUser(@Param("userId") int userId);
}
