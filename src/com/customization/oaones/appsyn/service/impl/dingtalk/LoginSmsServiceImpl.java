package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.HrmMapper;
import com.customization.oaones.appsyn.dao.dingtalk.LoginSmsMapper;
import com.customization.oaones.appsyn.dto.OaAccountRow;
import com.customization.oaones.appsyn.entity.dingtalk.LoginSms;
import com.customization.oaones.appsyn.service.dingtalk.LoginSmsService;
import weaver.email.externalmail.send.EmailSend;
import weaver.email.externalmail.send.entity.EmailSendEntity;
import weaver.email.externalmail.send.entity.EmailSenderEntity;
import weaver.sms.SMSManager;
import weaver.sms.SmsFromMouldEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * “验证登录”的验证码实现（手机号 / 邮箱；本模块自管 + 平台标准发送能力）。
 *
 * <p>不依赖其它定制包（如 `com.customization.ghedu.*`）、不沿用历史验证码表
 * （如 `uf_verificationcode`）：验证码、有效期、错误次数、发送频率全部落在本模块自己的表
 * {@code uf_oaones_appsyn_login_sms} 上，两种验证方式同一张表、同一套口径
 * （手机号写 {@code mobile} 列、邮箱写 {@code email} 列，另一个留空）。</p>
 *
 * <p>发送环节只调用平台标准能力：手机号走 {@link SMSManager#sendSMS(String, String)}
 * （后台“短信设置”里配的通道，含自定义通道）；邮箱走 {@link EmailSend}
 * （发件账号取后台“邮件设置 → 群发邮件设置”的系统群发邮箱，与平台“邮件提醒”同一条通道）。</p>
 *
 * <p>安全口径：验证码只存 {@code 盐 + SHA-256(salt + 验证码)}，不存明文；
 * 校验通过即置为已使用（不可复用）；同一验证码错误次数超限、或超过有效期都直接作废，
 * 必须重新获取；同一手机号 / 邮箱的发送冷却与每日上限在本表上统计。</p>
 *
 * <p><b>类名保留历史命名</b>（`LoginSmsService` / `LoginSmsServiceImpl`）：本类同时服务
 * 手机号与邮箱两种验证方式，改名会牵动三个连接器的镜像类、控制器与部署脚本，
 * 因此只在注释里说明口径，不改类名。</p>
 */
public class LoginSmsServiceImpl implements LoginSmsService {

    /** 验证码字符集与长度上限（数字验证码，管理端可配 4~8 位）。 */
    private static final int MIN_CODE_LENGTH = 4;
    private static final int MAX_CODE_LENGTH = 8;

    /** 验证方式：手机号（写 mobile 列、走平台短信）。 */
    private static final String CHANNEL_MOBILE = "mobile";
    /** 验证方式：邮箱（写 email 列、走平台邮件）。 */
    private static final String CHANNEL_EMAIL = "email";

    /**
     * 邮箱格式校验（服务端与登录页用同一套规则：一个 `@`、域名带点、不含空格与包裹符号）。
     *
     * <p>比平台 `Util.isEmail` 严一些：登录页要按它给出“格式不对”的即时提示，
     * 口径必须前后端一致，所以规则写在业务层、页面照抄一份。</p>
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private String table() {
        return OaonesProps.loginSmsTable();
    }

    /* ------------------------------------------------------------------
     * 手机号 / 邮箱 → OA 账号
     * ------------------------------------------------------------------ */

    @Override
    public int requireOaUserByMobile(String mobile) {
        String value = normalizeMobile(mobile);
        if (value.length() == 0) {
            throw new RuntimeException("请输入手机号");
        }
        if (!isChinaMobile(value)) {
            throw new RuntimeException("请输入正确的 11 位手机号");
        }
        List<Integer> ids = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.listResourceIdsByMobile(value));
        return singleOaUser(ids, "手机号");
    }

    @Override
    public int requireOaUserByEmail(String email) {
        String value = normalizeEmail(email);
        if (value.length() == 0) {
            throw new RuntimeException("请输入邮箱");
        }
        if (!isEmail(value)) {
            throw new RuntimeException("请输入正确的邮箱地址");
        }
        List<Integer> ids = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.listResourceIdsByEmail(value));
        return singleOaUser(ids, "邮箱");
    }

    /**
     * 手机号 / 邮箱 → **唯一**的有效 OA 人员ID（两种验证方式同一套判断，只换文案里的“手机号 / 邮箱”）。
     *
     * <p>取不到（没维护 / 账号已解聘离职 / 登录名为空）或对应多个账号时抛异常，
     * 异常消息是面向员工的口径（“请联系管理员核对人员信息”），不暴露 SQL 细节。</p>
     */
    private int singleOaUser(List<Integer> ids, String what) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("该" + what + "没有对应的 OA 账号（或账号已解聘/离职），"
                    + "请联系管理员在人员信息里核对" + what);
        }
        if (ids.size() > 1) {
            throw new RuntimeException("该" + what + "对应多个 OA 账号（共 " + ids.size()
                    + " 个），无法确定登录哪个账号；请联系管理员核对人员" + what);
        }
        Integer oaUserId = ids.get(0);
        if (oaUserId == null || oaUserId <= 0) {
            throw new RuntimeException("该" + what + "没有对应的 OA 账号，请联系管理员核对人员信息");
        }
        OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getResourceAccount(oaUserId));
        if (row == null) {
            throw new RuntimeException("该" + what + "对应的 OA 账号不存在或已被删除，请联系管理员核对");
        }
        int status = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getStatus()), -1);
        if (status >= 4) {
            throw new RuntimeException("该" + what + "对应的 OA 账号状态为「" + statusText(row.getStatus())
                    + "」，不能登录；如有疑问请联系管理员");
        }
        return oaUserId;
    }

    /* ------------------------------------------------------------------
     * 发送验证码
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject sendCode(String mobile, String ip, String ua) {
        String value = normalizeMobile(mobile);
        return sendCode(CHANNEL_MOBILE, value, ip, ua, requireOaUserByMobile(value));
    }

    @Override
    public JSONObject sendEmailCode(String email, String ip, String ua) {
        String value = normalizeEmail(email);
        return sendCode(CHANNEL_EMAIL, value, ip, ua, requireOaUserByEmail(value));
    }

    /**
     * 发送验证码（两种验证方式共用：冷却 / 每日上限 / 有效期 / 位数 / 落库 / 失败作废）。
     *
     * @param channel 验证方式（{@link #CHANNEL_MOBILE} / {@link #CHANNEL_EMAIL}）
     * @param target  已规范化的手机号或邮箱（必须已经过“唯一对应一个有效账号”校验）
     */
    private JSONObject sendCode(String channel, String target, String ip, String ua, int oaUserId) {
        boolean email = CHANNEL_EMAIL.equals(channel);
        String what = email ? "邮箱" : "手机号";
        long now = System.currentTimeMillis();
        cleanExpired(now);
        int cooldown = Math.max(1, OaonesProps.loginSmsCooldown());
        int dailyMax = Math.max(1, OaonesProps.loginSmsDailyMax());
        // 发送冷却（同一手机号 / 邮箱）：避免连点与被当成短信 / 邮件轰炸入口
        int recent = countSince(channel, target, now - cooldown * 1000L);
        if (recent > 0) {
            return fail("验证码已发送，同一" + what + " " + cooldown + " 秒内只能发送一次，"
                    + "请稍后再试或使用已收到的验证码");
        }
        // 每日上限（同一手机号 / 邮箱）
        int today = countSince(channel, target, dayStartMillis(now));
        if (today >= dailyMax) {
            return fail("同一" + what + "每天最多发送 " + dailyMax + " 次验证码，请明天再试或联系管理员");
        }
        int ttl = Math.max(30, OaonesProps.loginSmsTtl());
        int codeLength = Math.min(MAX_CODE_LENGTH, Math.max(MIN_CODE_LENGTH, OaonesProps.loginSmsLength()));
        String code = randomCode(codeLength);
        String salt = randomSalt();
        LoginSms entity = new LoginSms();
        if (email) {
            entity.setEmail(target);
        } else {
            entity.setMobile(target);
        }
        entity.setPurpose(PURPOSE_LOGIN);
        entity.setCodeSalt(salt);
        entity.setCodeHash(hash(salt, code));
        entity.setState(0);
        entity.setExpireTime(now + ttl * 1000L);
        entity.setWrongTimes(0);
        entity.setSendTime(now);
        entity.setSendIp(OaonesUtil.s(ip));
        entity.setSendUa(OaonesUtil.s(ua));
        entity.setFormmodeid(OaonesProps.loginSmsModeId());
        entity.setModedatacreater(oaUserId);
        entity.setModedatacreatedate(OaonesUtil.nowDate());
        entity.setModedatacreatetime(OaonesUtil.nowTime());
        entity.setModedatamodifier(oaUserId);
        entity.setModedatamodifydatetime(OaonesUtil.now());
        try {
            OaonesMyBatis.run(LoginSmsMapper.class, mapper -> mapper.insert(table(), entity));
        } catch (Throwable t) {
            OaonesLog.error("验证登录：写入验证码失败 " + (email ? "email=" : "mobile=") + mask(target), t);
            return fail("验证码生成失败：请确认已执行 sql/create_oaones_login_sms.sql 建好 "
                    + "uf_oaones_appsyn_login_sms 表、且 oaones.appsyn.properties 的 oaones_login_sms_table 指向该表"
                    + "（老库还要执行 oaones0006.sql 补 email 列、并把 mobile 改成可空）");
        }
        // 正文模板：{code} 验证码、{minutes} 有效分钟数；短信取 login.sms.template、
        // 邮件正文取 login.email.template（值里含中文，文件按 GBK 存，OaonesProps 会按 GBK 还原）
        String template = email ? OaonesProps.loginEmailTemplate() : OaonesProps.loginSmsTemplate();
        String content = template
                .replace("{code}", code)
                .replace("{minutes}", String.valueOf(Math.max(1, ttl / 60)));
        SendResult result = email
                ? sendMailByPlatform(oaUserId, target, OaonesProps.loginEmailSubject(), content)
                : sendSmsByPlatform(oaUserId, target, content);
        if (!result.ok) {
            // 发送失败：把这条验证码作废，避免“没收到短信 / 邮件但验证码还有效”的错觉
            // （插入后不依赖自增主键回填：刚插入的那条就是该手机号 / 邮箱最新一条）
            LoginSms justSent = latest(channel, target);
            invalidate(justSent == null ? null : justSent.getId(), result.reason);
            OaonesLog.step("验证登录：验证码发送失败 " + (email ? "email=" : "mobile=") + mask(target)
                    + " reason=" + result.reason);
            return fail(result.reason);
        }
        OaonesLog.step("验证登录：验证码已发送 " + (email ? "email=" : "mobile=") + mask(target)
                + " oaUserId=" + oaUserId + " ttl=" + ttl + "s");
        JSONObject json = new JSONObject();
        json.put("status", "1");
        json.put("info", "验证码已发送到 " + mask(target) + "，" + Math.max(1, ttl / 60)
                + " 分钟内有效" + (email ? "，请查收邮件（含垃圾邮件箱）" : ""));
        return json;
    }

    /* ------------------------------------------------------------------
     * 校验验证码
     * ------------------------------------------------------------------ */

    @Override
    public JSONObject checkCode(String mobile, String code, String ip) {
        return checkCode(CHANNEL_MOBILE, normalizeMobile(mobile), code, ip);
    }

    @Override
    public JSONObject checkEmailCode(String email, String code, String ip) {
        return checkCode(CHANNEL_EMAIL, normalizeEmail(email), code, ip);
    }

    /** 校验验证码（两种验证方式共用：取最近一条 → 已使用 / 已作废 / 已超时 / 错误次数 → 比对摘要）。 */
    private JSONObject checkCode(String channel, String target, String code, String ip) {
        boolean email = CHANNEL_EMAIL.equals(channel);
        String what = email ? "邮箱" : "手机号";
        // 验证码提示里的简称：手机号 → 手机（“手机验证码”）、邮箱 → 邮箱（“邮箱验证码”）
        String codeLabel = email ? "邮箱" : "手机";
        String input = OaonesUtil.s(code).trim();
        if (target.length() == 0) {
            return fail(-1, "请输入" + what);
        }
        if (input.length() == 0) {
            return fail(-1, "请输入" + codeLabel + "验证码");
        }
        LoginSms row = latest(channel, target);
        if (row == null) {
            return fail(-1, "请先获取" + codeLabel + "验证码");
        }
        int state = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getState()), 0);
        if (state == 1) {
            return fail(-1, "该验证码已使用，请重新获取验证码");
        }
        if (state == 2) {
            String reason = OaonesUtil.s(row.getFailReason());
            return fail(-1, reason.length() == 0 ? "该验证码已失效，请重新获取" : reason + "，请重新获取");
        }
        long now = System.currentTimeMillis();
        if (row.getExpireTime() != null && row.getExpireTime() <= now) {
            invalidate(row.getId(), "验证码已超时");
            return fail(-2, "验证码已超时，请重新获取");
        }
        int maxWrong = Math.max(1, OaonesProps.loginSmsMaxWrong());
        int wrong = OaonesUtil.i(OaonesUtil.nullToEmpty(row.getWrongTimes()), 0);
        if (wrong >= maxWrong) {
            invalidate(row.getId(), "验证码错误次数过多");
            return fail(-2, "验证码错误次数过多，请重新获取验证码");
        }
        if (!hash(OaonesUtil.s(row.getCodeSalt()), input).equalsIgnoreCase(OaonesUtil.s(row.getCodeHash()))) {
            int next = wrong + 1;
            markWrong(row.getId(), next, "验证码不正确（已错 " + next + " 次）");
            OaonesLog.step("验证登录：验证码不正确 " + (email ? "email=" : "mobile=") + mask(target)
                    + " 第 " + next + " 次");
            return fail(-1, "验证码不正确（还可尝试 " + Math.max(0, maxWrong - next) + " 次）");
        }
        OaonesMyBatis.run(LoginSmsMapper.class,
                mapper -> mapper.markUsed(table(), row.getId(), now, OaonesUtil.s(ip)));
        OaonesLog.step("验证登录：验证码校验通过 " + (email ? "email=" : "mobile=") + mask(target));
        JSONObject json = new JSONObject();
        json.put("status", "0");
        json.put("info", "验证通过");
        return json;
    }

    /* ------------------------------------------------------------------
     * 平台标准发送能力：短信
     * ------------------------------------------------------------------ */

    /** 发送结果（成功/失败原因），避免在业务分支里到处判断布尔值。 */
    private static final class SendResult {
        private final boolean ok;
        private final String reason;

        private SendResult(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }
    }

    /**
     * 用**平台标准能力**发送短信：{@link SMSManager#sendSMS}。
     *
     * <p>{@code SMSManager} 内部会走后台“短信设置”里配置的通道（含自定义通道实现），
     * 并把短信落到平台“我的短信”里，本模块不直连服务商、也不引用其它定制包。
     * 发送人（userid）与分部按**接收人**的账号填，便于分权取通道与事后对账。</p>
     *
     * <p>若本环境的“短信设置”没有启用平台短信服务（此时 {@link SMSManager#isValid()} 为 false），
     * 允许在 {@code oaones.appsyn.properties#login.sms.service.class} 里填一个
     * **实现了平台接口 {@code weaver.sms.SmsService} 的短信服务类名**，
     * 由本方法反射实例化后调用——这样既能用现场已有的自定义短信通道，
     * 又只依赖平台接口、编译期不引用任何其它定制包。</p>
     */
    private SendResult sendSmsByPlatform(int oaUserId, String mobile, String content) {
        try {
            SMSManager sms = new SMSManager();
            sms.setUserid(String.valueOf(oaUserId));
            OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, mapper -> mapper.getResourceAccount(oaUserId));
            String subcompanyId = "";
            if (row != null && row.getSubcompanyId() != null && row.getSubcompanyId() > 0) {
                subcompanyId = String.valueOf(row.getSubcompanyId());
                sms.setSubcompanyid(subcompanyId);
            }
            // 不用短信模板：直接发正文（内容里只有验证码与有效期，不含敏感信息）
            sms.setMouldkey("");
            sms.setFromMould(SmsFromMouldEnum.HRM);
            if (sms.isValid()) {
                boolean ok = sms.sendSMS(mobile, content);
                if (ok) {
                    return new SendResult(true, "");
                }
                OaonesLog.step("验证登录：平台短信服务返回失败，尝试自定义短信服务类（若已配置）");
            } else {
                OaonesLog.step("验证登录：平台短信服务不可用（后台短信设置未启用），"
                        + "尝试自定义短信服务类（若已配置）");
            }
            return sendByConfiguredService(subcompanyId, mobile, content);
        } catch (Throwable t) {
            OaonesLog.error("验证登录：调用平台短信接口异常 mobile=" + mask(mobile), t);
            return new SendResult(false, "短信发送异常："
                    + (t.getMessage() == null ? "请联系管理员" : t.getMessage()));
        }
    }

    /**
     * 兜底：用 properties 里配置的短信服务类发送（该类必须实现平台接口 {@code weaver.sms.SmsService}）。
     *
     * <p>只按类名反射调用平台接口，编译期不引用任何定制包；未配置时返回可执行的提示，
     * 让管理员知道该去后台启用短信通道、或在这里配置现场已有的短信服务类。</p>
     */
    private SendResult sendByConfiguredService(String subcompanyId, String mobile, String content) {
        String className = OaonesProps.loginSmsServiceClass();
        if (className.length() == 0) {
            return new SendResult(false, "短信服务不可用：请在后台“短信设置”启用短信通道，"
                    + "或在 oaones.appsyn.properties 的 login.sms.service.class 里配置短信服务类"
                    + "（需实现 weaver.sms.SmsService），然后重试");
        }
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.newInstance();
            if (!(instance instanceof weaver.sms.SmsService)) {
                return new SendResult(false, "配置的短信服务类 " + className
                        + " 没有实现 weaver.sms.SmsService 接口，请在 oaones.appsyn.properties 里改正");
            }
            // 平台接口 weaver.sms.SmsService#sendSMS(短信/分权标识, 手机号, 内容)
            boolean ok = ((weaver.sms.SmsService) instance).sendSMS(
                    subcompanyId.length() == 0 ? "-1" : subcompanyId, mobile, content);
            return ok ? new SendResult(true, "")
                    : new SendResult(false, "短信发送失败：自定义短信服务类返回失败，请联系管理员检查通道配置");
        } catch (ClassNotFoundException e) {
            return new SendResult(false, "找不到配置的短信服务类：" + className
                    + "（请检查 oaones.appsyn.properties 的 login.sms.service.class）");
        } catch (Throwable t) {
            OaonesLog.error("验证登录：调用自定义短信服务类异常 class=" + className
                    + " mobile=" + mask(mobile), t);
            return new SendResult(false, "短信发送异常："
                    + (t.getMessage() == null ? "请联系管理员" : t.getMessage()));
        }
    }

    /* ------------------------------------------------------------------
     * 平台标准发送能力：邮件
     * ------------------------------------------------------------------ */

    /**
     * 用**平台标准能力**发送邮件：{@link EmailSend}。
     *
     * <p>发件账号取后台“邮件设置 → 群发邮件设置”里的**系统群发邮箱**
     * （{@link EmailSend#getEmailSenderOfSystemSet()}，与平台“邮件提醒”同一条通道），
     * 本模块不直连邮件服务器、也不引用其它定制包；发送是同步的（失败能立刻告诉用户，
     * 与短信同一口径），超时与认证口径都按平台邮件设置走。</p>
     */
    private SendResult sendMailByPlatform(int oaUserId, String email, String subject, String content) {
        try {
            EmailSenderEntity sender = EmailSend.getEmailSenderOfSystemSet();
            if (sender == null || OaonesUtil.s(sender.getEmailAddress()).length() == 0) {
                return new SendResult(false, "邮件发送失败：请先在后台“邮件设置”里配置群发邮箱"
                        + "（发件账号 / 发件服务器），或在“登录设置”里关闭邮箱验证登录");
            }
            EmailSend send = new EmailSend();
            // 与平台“邮件提醒”同一套组装口径：UTF-8、html 模式、普通优先级、不分别发送
            EmailSendEntity entity = send.packParameterToEntity(
                    OaonesUtil.s(sender.getEmailAddress()), email, "", "", OaonesUtil.s(subject),
                    3, OaonesUtil.s(content), null, null, null, 0, null, false, "3", "0", true);
            entity.getFrom().setNickname(OaonesUtil.s(sender.getNickName()));
            boolean ok = send.sendMail(sender, entity);
            if (ok) {
                return new SendResult(true, "");
            }
            OaonesLog.step("验证登录：平台邮件服务返回失败 email=" + mask(email)
                    + " oaUserId=" + oaUserId);
            return new SendResult(false, "邮件发送失败：发件服务器返回失败，"
                    + "请联系管理员检查后台“邮件设置”里的群发邮箱配置");
        } catch (Throwable t) {
            OaonesLog.error("验证登录：调用平台邮件接口异常 email=" + mask(email), t);
            return new SendResult(false, "邮件发送异常："
                    + (t.getMessage() == null ? "请联系管理员" : t.getMessage()));
        }
    }

    /* ------------------------------------------------------------------
     * 表操作与工具
     * ------------------------------------------------------------------ */

    /** 取该手机号 / 邮箱最近一条验证码（两个渠道只传其中一个）。 */
    private LoginSms latest(String channel, String target) {
        boolean email = CHANNEL_EMAIL.equals(channel);
        return OaonesMyBatis.call(LoginSmsMapper.class, mapper -> mapper.findLatest(
                table(), email ? null : target, email ? target : null, PURPOSE_LOGIN));
    }

    /** 统计该手机号 / 邮箱在指定时间之后发出的验证码条数（发送冷却 / 每日上限用）。 */
    private int countSince(String channel, String target, long since) {
        boolean email = CHANNEL_EMAIL.equals(channel);
        Integer count = OaonesMyBatis.call(LoginSmsMapper.class, mapper -> mapper.countSince(
                table(), email ? null : target, email ? target : null, PURPOSE_LOGIN, since));
        return count == null ? 0 : count;
    }

    private void markWrong(Integer id, int wrongTimes, String reason) {
        if (id == null || id <= 0) {
            return;
        }
        OaonesMyBatis.run(LoginSmsMapper.class,
                mapper -> mapper.updateOnFail(table(), id, wrongTimes, reason));
    }

    private void invalidate(Integer id, String reason) {
        if (id == null || id <= 0) {
            return;
        }
        OaonesMyBatis.run(LoginSmsMapper.class,
                mapper -> mapper.markInvalid(table(), id, reason));
    }

    /** 清理过期/过老的验证码记录（发送时顺手清理，保留小时数取 login.sms.keep.hours）。 */
    private void cleanExpired(long now) {
        long keep = Math.max(1, OaonesProps.loginSmsKeepHours()) * 3600L * 1000L;
        try {
            OaonesMyBatis.run(LoginSmsMapper.class,
                    mapper -> mapper.deleteExpired(table(), now - keep));
        } catch (Throwable t) {
            OaonesLog.error("清理过期登录验证码失败（忽略，不影响本次发送）", t);
        }
    }

    /** 当天 00:00:00 的毫秒时间戳（用本地时区计算，避免跨库日期函数差异）。 */
    private long dayStartMillis(long now) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** 手机号规范化：去掉空格/中划线/括号等，兼容带 86 区号的写法。 */
    private String normalizeMobile(String mobile) {
        String value = OaonesUtil.s(mobile).trim();
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        String digits = sb.toString();
        if (digits.length() == 13 && digits.startsWith("86")) {
            digits = digits.substring(2);
        }
        return digits;
    }

    /**
     * 邮箱规范化：去掉首尾空格与常见包裹符号（`&lt; &gt; " '`），其余原样保留。
     *
     * <p>**不做大小写拉平**：E9 的人员邮箱是原样存取的，拉平会让“库里是大写、
     * 用户输入小写”这类情况查不到人；格式校验与查库都用规范化后的原文。</p>
     */
    private String normalizeEmail(String email) {
        String value = OaonesUtil.s(email).trim();
        while (value.length() > 0 && (value.startsWith("<") || value.startsWith("\"")
                || value.startsWith("'") || value.startsWith(" "))) {
            value = value.substring(1).trim();
        }
        while (value.length() > 0 && (value.endsWith(">") || value.endsWith("\"")
                || value.endsWith("'") || value.endsWith(" "))) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    /** 中国大陆手机号（11 位、1 开头）；与平台短信发送对接收号码的要求一致。 */
    private boolean isChinaMobile(String mobile) {
        return mobile.length() == 11 && mobile.charAt(0) == '1';
    }

    /** 邮箱格式校验（规则与登录页一致，见 {@link #EMAIL_PATTERN}）。 */
    private boolean isEmail(String email) {
        return email.length() <= 128 && EMAIL_PATTERN.matcher(email).matches();
    }

    /** 生成纯数字验证码（避免 0/1/字母混淆，直接数字即可）。 */
    private String randomCode(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('0' + random.nextInt(10)));
        }
        return sb.toString();
    }

    private String randomSalt() {
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, 16);
    }

    /** SHA-256(salt + 验证码) 十六进制（验证码不落明文）。 */
    private String hash(String salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((OaonesUtil.s(salt) + "|" + OaonesUtil.s(code))
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("验证码摘要计算失败：" + e.getMessage());
        }
    }

    /** 手机号脱敏（日志与页面提示里不落全号）。 */
    private String maskMobile(String mobile) {
        String value = OaonesUtil.s(mobile);
        if (value.length() < 7) {
            return value;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /** 邮箱脱敏（日志与页面提示里不落完整地址）：只留首字符与域名。 */
    private String maskEmail(String email) {
        String value = OaonesUtil.s(email);
        int at = value.indexOf('@');
        if (at <= 0) {
            return value;
        }
        String name = value.substring(0, at);
        String head = name.length() <= 1 ? name : name.substring(0, 1) + "***";
        return head + value.substring(at);
    }

    /** 按渠道脱敏（邮箱 → 邮箱脱敏；其余按手机号脱敏）。 */
    private String mask(String target) {
        String value = OaonesUtil.s(target);
        return value.indexOf('@') > 0 ? maskEmail(value) : maskMobile(value);
    }

    /** 失败返回（status=-1）。 */
    private JSONObject fail(String info) {
        return fail(-1, info);
    }

    private JSONObject fail(int status, String info) {
        JSONObject json = new JSONObject();
        json.put("status", String.valueOf(status));
        json.put("info", OaonesUtil.s(info));
        return json;
    }

    /** OA 人员状态文本（与人员绑定页、免登录入口同一口径）。 */
    private String statusText(String status) {
        int value = OaonesUtil.i(status, -1);
        switch (value) {
            case 0: return "正式";
            case 1: return "试用";
            case 2: return "临时";
            case 3: return "试用延期";
            case 4: return "解聘";
            case 5: return "离职";
            case 6: return "退休";
            case 7: return "返聘";
            default: return status == null || status.length() == 0 ? "未知" : "其它（" + status + "）";
        }
    }
}
