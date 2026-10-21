package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.ModeAudit;
import org.apache.ibatis.annotations.Param;

/**
 * E9 建模数据审计字段 Mapper（重建数据共享权限时读取原创建人/模块ID）。
 */
public interface ModeDataMapper {

    ModeAudit selectModeAudit(@Param("table") String table, @Param("id") int id);
}
