package com.customization.oaones.appsyn.dao.dingtalk;

import com.customization.oaones.appsyn.dto.dingtalk.DeptBindRow;
import com.customization.oaones.appsyn.entity.dingtalk.DeptBind;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 部门绑定表 Mapper（增删改查 + 授权/冲突校验查询）。
 */
public interface DeptBindMapper {

    /** 列表（关联 OA 部门/分部、钉钉租户配置、部门快照），入参含 cfgId/keyword 与全部业务表名。 */
    List<DeptBindRow> listRows(Map<String, Object> params);

    /** 启用中的绑定（供同步引擎使用）。 */
    List<DeptBind> listEnabled(@Param("table") String table, @Param("cfgId") int cfgId);

    DeptBind findById(@Param("table") String table, @Param("id") int id);

    Integer findIdByOaDept(@Param("table") String table,
                           @Param("cfgId") int cfgId,
                           @Param("oaDeptId") int oaDeptId);

    /** 同一租户、OA 部门、平台部门是否已有绑定；有则原地更新，没有则新增。 */
    Integer findIdByPair(@Param("table") String table,
                         @Param("cfgId") int cfgId,
                         @Param("oaDeptId") int oaDeptId,
                         @Param("wxDeptId") int wxDeptId);

    /** 该钉钉部门是否已存在启用中的绑定（免绑定标记的校验）。 */
    Integer findActiveIdByWxDept(@Param("table") String table,
                                 @Param("cfgId") int cfgId,
                                 @Param("wxDeptId") int wxDeptId);

    int countByCfgId(@Param("table") String table, @Param("cfgId") int cfgId);

    int insert(@Param("table") String table, @Param("e") DeptBind entity);

    int update(@Param("table") String table, @Param("e") DeptBind entity);

    int deleteById(@Param("table") String table, @Param("id") int id);
}
