package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.PersonalWxOaRow;
import com.customization.oaones.appsyn.entity.PersonalWxBind;
import com.customization.oaones.appsyn.entity.PersonalWxConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 个人微信 Mapper（授权密钥表 + 绑定表 + 绑定列表要用的 OA 人员查询）。
 *
 * <p>本连接器没有“租户”维度：授权密钥表只保留一行，绑定表按 OA 人员唯一，
 * 因此两张表的语句都在这里，物理名分别取
 * {@code oaones_personal_wx_config_table} / {@code oaones_personal_wx_bind_table}
 * （由 {@code OaonesParam} 统一注入 `table` 参数）。
 * OA 人员查询走 E9 标准表 {@code hrmresource} / {@code hrmdepartment}。</p>
 */
public interface PersonalWxMapper {

    /* ---------------- 授权密钥（单行） ---------------- */

    /** 取当前授权密钥行（id 最小的一条）；没有数据返回 null。 */
    PersonalWxConfig findConfig(@Param("table") String table);

    int insertConfig(@Param("table") String table, @Param("e") PersonalWxConfig entity);

    int updateConfig(@Param("table") String table, @Param("e") PersonalWxConfig entity);

    /* ---------------- 个人微信绑定 ---------------- */

    /** 全部绑定记录（绑定列表用，页面在内存里按 OA 人员合并）。 */
    List<PersonalWxBind> listBinds(@Param("table") String table);

    /** 某个 OA 人员的绑定（没有绑定返回 null）。 */
    PersonalWxBind findByOaUser(@Param("table") String table, @Param("oaUserId") Integer oaUserId);

    /** 某个微信号已绑定的记录（一个微信号只能绑一个 OA 账号，没有返回 null）。 */
    PersonalWxBind findByOpenid(@Param("table") String table, @Param("openid") String openid);

    int insertBind(@Param("table") String table, @Param("e") PersonalWxBind entity);

    int updateBind(@Param("table") String table, @Param("e") PersonalWxBind entity);

    /** 按 OA 人员删除绑定（解绑：本人自助或管理员操作）。 */
    int deleteByOaUser(@Param("table") String table, @Param("oaUserId") Integer oaUserId);

    /* ---------------- 绑定列表要用的 OA 人员 ---------------- */

    /**
     * OA 有效账号（{@code status < 4}），**含系统管理员账号**；可按关键字过滤。
     *
     * <p>关键字同时匹配姓名 / 登录名 / 工号 / 手机号 / 人员ID；传空串取全部。</p>
     */
    List<PersonalWxOaRow> listOaUsers(@Param("keyword") String keyword);

    /** 单个 OA 人员（含离职等状态，用于给提示语取姓名与账号状态）。 */
    PersonalWxOaRow getOaUser(@Param("id") int id);
}
