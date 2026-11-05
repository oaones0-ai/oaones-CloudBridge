package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.entity.FeatureSetting;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 功能开关配置 Mapper。
 */
public interface FeatureSettingMapper {

    List<FeatureSetting> list(@Param("table") String table);

    FeatureSetting getByCode(@Param("table") String table,
                             @Param("featureCode") String featureCode);

    Long countEnabledAppBinds(@Param("table") String table);

    Long countEnabledSyncPlans(@Param("table") String table);

    int insert(@Param("table") String table,
               @Param("e") FeatureSetting entity,
               @Param("operatorId") String operatorId,
               @Param("createDate") String createDate,
               @Param("createTime") String createTime,
               @Param("now") String now);

    int updateByCode(@Param("table") String table,
                     @Param("e") FeatureSetting entity,
                     @Param("operatorId") String operatorId,
                     @Param("now") String now);
}
