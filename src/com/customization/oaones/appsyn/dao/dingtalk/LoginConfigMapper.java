package com.customization.oaones.appsyn.dao.dingtalk;

import com.customization.oaones.appsyn.entity.dingtalk.LoginConfig;
import org.apache.ibatis.annotations.Param;

/**
 * 统一登录设置表 Mapper（uf_oaones_login_config）。
 *
 * <p>本表只保留一行：读取取 id 最小的一条（没有数据时由 Service 按默认值返回，
 * 不要求先手工插数据）；保存时优先更新这一行，没有则插入。</p>
 */
public interface LoginConfigMapper {

    /** 取当前设置行（id 最小的一条）；没有数据返回 null。 */
    LoginConfig findFirst(@Param("table") String table);

    int insert(@Param("table") String table, @Param("e") LoginConfig entity);

    int update(@Param("table") String table, @Param("e") LoginConfig entity);
}
