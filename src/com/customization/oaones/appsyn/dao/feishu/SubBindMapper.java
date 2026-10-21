package com.customization.oaones.appsyn.dao.feishu;

import com.customization.oaones.appsyn.dto.feishu.SubBindRow;
import com.customization.oaones.appsyn.entity.feishu.SubBind;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 分部绑定表 Mapper（增删改查 + 冲突校验查询）。
 *
 * <p>分部绑定只登记 OA 分部归属的飞书租户（cfg_id），不再存储飞书部门ID；
 * 分部到飞书租户为“一对一”：同一 OA 分部只保留一条绑定，
 * 同一飞书租户也只允许被一个 OA 分部绑定
 * （`(cfg_id, oa_sub_id)`、`oa_sub_id`、`cfg_id` 都是唯一索引，
 * 因此“同一分部分别绑到两个租户”在库层也会被挡住）。
 * 绑定状态 yx 口径为 <b>0 有效 / 1 无效</b>（与部门绑定表相反）。</p>
 */
public interface SubBindMapper {

    /** 列表（关联 OA 分部、飞书租户配置），入参含 cfgId/keyword 与全部业务表名。 */
    List<SubBindRow> listRows(Map<String, Object> params);

    /** 启用中的绑定（供“部门绑定先绑分部”校验使用）。 */
    List<SubBind> listEnabled(@Param("table") String table, @Param("cfgId") int cfgId);

    SubBind findById(@Param("table") String table, @Param("id") int id);

    /** 该 OA 分部现有的有效绑定（不分租户）：一对一，用于“更换绑定的租户”。 */
    Integer findIdByOaSub(@Param("table") String table, @Param("oaSubId") int oaSubId);

    /** 指定租户下该 OA 分部是否已有有效绑定（部门自动绑定的前置校验）。 */
    Integer findIdByCfgAndOaSub(@Param("table") String table,
                                @Param("cfgId") int cfgId,
                                @Param("oaSubId") int oaSubId);

    /** 该飞书租户是否已被某个 OA 分部绑定（一对一校验）。 */
    Integer findActiveIdByCfg(@Param("table") String table, @Param("cfgId") int cfgId);

    int countByCfgId(@Param("table") String table, @Param("cfgId") int cfgId);

    int insert(@Param("table") String table, @Param("e") SubBind entity);

    int update(@Param("table") String table, @Param("e") SubBind entity);

    /**
     * 记录该分部在飞书侧对应的部门（同步中心按范围同步时写入；飞书没有“分部”概念）。
     *
     * @param table    分部绑定表物理名
     * @param cfgId    飞书租户ID
     * @param oaSubId  OA 分部ID
     * @param wxDeptId 该分部在飞书侧对应的部门ID
     * @param modifier 操作人（OA 人员ID）
     * @param now      修改时间
     */
    int updateWxDeptId(@Param("table") String table,
                       @Param("cfgId") int cfgId,
                       @Param("oaSubId") int oaSubId,
                       @Param("wxDeptId") String wxDeptId,
                       @Param("modifier") int modifier,
                       @Param("now") String now);

    int deleteById(@Param("table") String table, @Param("id") int id);
}
