package com.customization.oaones.appsyn.dao.dingtalk;

import com.customization.oaones.appsyn.entity.dingtalk.UserRule;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 默认人员绑定规则表 Mapper。
 */
public interface UserRuleMapper {

    List<UserRule> list(@Param("table") String table);

    /** 某个租户的独立配置（取最早一条）。 */
    UserRule findByCfgId(@Param("table") String table, @Param("cfgId") int cfgId);

    /** 默认配置（cfg_id 为空或 0，取最早一条）。 */
    UserRule findDefault(@Param("table") String table);

    int insert(@Param("table") String table, @Param("e") UserRule entity);

    int updateRules(@Param("table") String table,
                    @Param("id") int id,
                    @Param("rules") String rules,
                    @Param("formmodeid") int formmodeid,
                    @Param("modifier") int modifier,
                    @Param("modifyTime") String modifyTime);

    int deleteByCfgId(@Param("table") String table, @Param("cfgId") int cfgId);
}
