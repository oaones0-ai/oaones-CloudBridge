package com.customization.oaones.appsyn.service.dingtalk;

import com.alibaba.fastjson.JSONObject;

/**
 * “验证登录”的验证码（手机号 / 邮箱；本模块自管，不依赖其它定制包的实现）。
 *
 * <p><b>为什么自己写</b>：平台标准登录页只提供两种短信验证——登录页“动态密码”
 * （`/api/hrm/login/getDynamicPassword`，写 `hrmpassword`、登录后强制改密）与二次认证动态密码
 * （`/api/encrypt/secondauthsetting/getSecDynamicPassword`，取的是**已登录会话**的当前用户），
 * 两者都不能当作“免密登录”用；邮箱侧平台也没有现成的“登录验证码”能力。
 * 因此本模块按自己的业务口径实现：验证码、有效期、错误次数、发送频率全部落在自己的表
 * {@code uf_oaones_login_sms}（不沿用任何历史验证码表），两种验证方式同一张表、同一套口径。</p>
 *
 * <p><b>发送环节只复用平台标准能力</b>：手机号走 {@code weaver.sms.SMSManager#sendSMS}
 * （后台“短信设置”里配置的通道，含自定义通道，并落平台“我的短信”）；
 * 邮箱走 {@code weaver.email.externalmail.send.EmailSend}
 * （发件账号取后台“邮件设置 → 群发邮件设置”的系统群发邮箱，与平台“邮件提醒”同一条通道）。
 * 本模块不直连任何短信服务商 / 邮件服务器、也不引用其它定制包。</p>
 *
 * <p>返回结构与登录页约定：{@code status} 为字符串，{@code info} 是可直接展示的提示语——
 * 发送：{@code 1} 成功、{@code -1} 失败（含冷却/超每日上限/通道不可用）；
 * 校验：{@code 0} 通过、{@code -1} 验证码不对、{@code -2} 已超时或尝试次数过多。</p>
 */
public interface LoginSmsService {

    /** 验证码用途：登录（手机号 / 邮箱两种验证方式共用）。 */
    String PURPOSE_LOGIN = "login";

    /**
     * 手机号 → 唯一的有效 OA 人员ID。
     *
     * <p>取不到（没维护手机号 / 账号已解聘离职 / 登录名为空）或对应多个账号时抛异常，
     * 异常消息是面向员工的口径（“请联系管理员核对人员信息”），不暴露 SQL 细节。</p>
     */
    int requireOaUserByMobile(String mobile);

    /**
     * 邮箱 → 唯一的有效 OA 人员ID。
     *
     * <p>口径与 {@link #requireOaUserByMobile} 完全一致（取 {@code hrmresource.email}，
     * 状态 0-3 且登录名非空，不唯一直接报错让管理员核对），异常消息是面向员工的口径。</p>
     */
    int requireOaUserByEmail(String email);

    /**
     * 发送手机验证码。
     *
     * <p>顺序：规范化并校验手机号 → 手机号必须唯一对应一个有效 OA 账号 →
     * 发送冷却（`login.sms.cooldown`）→ 每日上限（`login.sms.daily.max`）→
     * 生成验证码并落库（只存盐+摘要）→ 用平台标准能力发送。</p>
     *
     * @param mobile 员工填写的手机号
     * @param ip     发起发送的客户端IP（审计）
     * @param ua     发起发送的 User-Agent（审计）
     */
    JSONObject sendCode(String mobile, String ip, String ua);

    /**
     * 发送邮箱验证码。
     *
     * <p>顺序与短信完全一致（规范化并校验邮箱 → 邮箱必须唯一对应一个有效 OA 账号 →
     * 发送冷却 → 每日上限 → 生成验证码并落库（只存盐+摘要）→ 用平台标准邮件能力发送）；
     * 冷却 / 每日上限 / 有效期 / 位数 / 错误次数上限与短信共用 `login.sms.*` 这一套配置，
     * 邮件主题与正文模板取 `login.email.subject` / `login.email.template`。</p>
     */
    JSONObject sendEmailCode(String email, String ip, String ua);

    /**
     * 校验手机验证码。
     *
     * <p>校验口径：取该手机号最近一条验证码 → 已使用/已作废/已超时/错误次数超限分别给出明确提示 →
     * 比对摘要（不一致则累加错误次数）→ 通过后立即置为已使用（同一验证码不能用第二次）。</p>
     */
    JSONObject checkCode(String mobile, String code, String ip);

    /**
     * 校验邮箱验证码。
     *
     * <p>校验口径与 {@link #checkCode} 完全一致，只是按邮箱取最近一条验证码。</p>
     */
    JSONObject checkEmailCode(String email, String code, String ip);
}
