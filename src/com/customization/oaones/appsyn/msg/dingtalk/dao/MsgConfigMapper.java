package com.customization.oaones.appsyn.msg.dingtalk.dao;

import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgConfig;
import org.apache.ibatis.annotations.Param;

/**
 * 消息中心系统参数 Mapper（单行配置）。
 */
public interface MsgConfigMapper {

    MsgConfig getOne(@Param("table") String table);

    int insert(@Param("table") String table, @Param("e") MsgConfig entity);

    int update(@Param("table") String table, @Param("e") MsgConfig entity);
}
