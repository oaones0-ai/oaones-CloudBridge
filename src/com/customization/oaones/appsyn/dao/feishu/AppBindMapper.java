package com.customization.oaones.appsyn.dao.feishu;

import com.customization.oaones.appsyn.dto.feishu.AppBindRow;
import com.customization.oaones.appsyn.entity.feishu.AppBind;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 飞书工作台应用绑定表 Mapper（增删改查 + 唯一性/免登录入口校验查询）。
 *
 * <p>绑定状态 yx：0 有效 / 1 无效（查询一律带 {@code yx = 0}）；
 * 同一租户下同一个 App ID 只允许一条记录（`(cfg_id, agent_id)` 唯一索引兜底）。</p>
 */
public interface AppBindMapper {

    /** 列表（关联飞书租户配置与创建人），入参含 cfgId/cfgIds/keyword 与全部业务表名。 */
    List<AppBindRow> listRows(Map<String, Object> params);

    AppBind findById(@Param("table") String table, @Param("id") int id);

    /** 同一租户下该 App ID 的绑定记录ID（唯一性校验；不含状态过滤）。 */
    Integer findIdByCfgAgent(@Param("table") String table,
                             @Param("cfgId") int cfgId,
                             @Param("agentId") String agentId);

    /** 免登录入口：按租户 + App ID 取启用中的绑定。 */
    AppBind findEnabledByAgent(@Param("table") String table,
                               @Param("cfgId") int cfgId,
                               @Param("agentId") String agentId);

    /** 该租户下的应用绑定条数（删除租户前的占用校验）。 */
    int countByCfgId(@Param("table") String table, @Param("cfgId") int cfgId);

    int insert(@Param("table") String table, @Param("e") AppBind entity);

    int update(@Param("table") String table, @Param("e") AppBind entity);

    int updateState(@Param("table") String table,
                    @Param("id") int id,
                    @Param("yx") int yx,
                    @Param("modifier") int modifier,
                    @Param("modifyTime") String modifyTime);

    int deleteById(@Param("table") String table, @Param("id") int id);
}
