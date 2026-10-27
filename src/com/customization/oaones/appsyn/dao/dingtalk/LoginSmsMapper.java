package com.customization.oaones.appsyn.dao.dingtalk;

import com.customization.oaones.appsyn.entity.dingtalk.LoginSms;
import org.apache.ibatis.annotations.Param;

/**
 * 登录验证码表 Mapper（uf_oaones_appsyn_login_sms，手机号 / 邮箱两种验证方式共用）。
 *
 * <p>本表只服务“统一登录设置”里的“验证登录”：发送时插入一行，
 * 校验时按手机号（或邮箱）取**最近一条**（不管状态，由业务层区分
 * “待使用/已使用/已作废/已超时”，这样提示语才能分别是
 * “请先获取验证码 / 已使用 / 已超时”）。</p>
 *
 * <p>两个渠道的定位口径：{@code mobile} 与 {@code email} **只传其中一个非空值**
 * （手机号验证码查 {@code mobile}、邮箱验证码查 {@code email}），
 * 另一个留空时对应条件不参与过滤；{@code purpose} 两种渠道相同（{@code login}）。</p>
 */
public interface LoginSmsMapper {

    /** 取该手机号 / 邮箱最近一条验证码记录（不限状态）；没有记录返回 null。 */
    LoginSms findLatest(@Param("table") String table,
                        @Param("mobile") String mobile,
                        @Param("email") String email,
                        @Param("purpose") String purpose);

    /** 统计该手机号 / 邮箱在指定时间之后发出的验证码条数（发送冷却/每日上限用）。 */
    int countSince(@Param("table") String table,
                   @Param("mobile") String mobile,
                   @Param("email") String email,
                   @Param("purpose") String purpose,
                   @Param("since") long since);

    int insert(@Param("table") String table, @Param("e") LoginSms entity);

    /** 校验失败：累加错误次数并记录原因（状态仍为待使用，允许继续尝试直到超过上限）。 */
    int updateOnFail(@Param("table") String table,
                     @Param("id") int id,
                     @Param("wrongTimes") int wrongTimes,
                     @Param("failReason") String failReason);

    /** 校验通过：状态置 1（已使用）并记录校验时间与 IP。 */
    int markUsed(@Param("table") String table,
                 @Param("id") int id,
                 @Param("verifyTime") long verifyTime,
                 @Param("verifyIp") String verifyIp);

    /** 作废：状态置 2 并写原因（超时、错误次数过多、短信发送失败等）。 */
    int markInvalid(@Param("table") String table,
                    @Param("id") int id,
                    @Param("failReason") String failReason);

    /** 清理过期记录（发送新验证码时顺手清理，不需要额外计划任务）。 */
    int deleteExpired(@Param("table") String table, @Param("before") long before);
}
