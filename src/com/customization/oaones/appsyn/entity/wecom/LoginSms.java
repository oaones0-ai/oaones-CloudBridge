package com.customization.oaones.appsyn.entity.wecom;

/**
 * 登录验证码实体（手机号 / 邮箱共用一张表：表名取 oaones.properties#oaones_login_sms_table，
 * 建模模块ID按表名反查，配置里不出现）。
 *
 * <p>一行 = 登录页发出的一条验证码：手机号验证码写 {@code mobile}、
 * 邮箱验证码写 {@code email}（另一个留空，两个渠道同一张表、同一套冷却 / 有效期 / 错误次数口径）；
 * 验证码**不存明文**，只存 {@code codeSalt} + {@code codeHash}（SHA-256(salt + 验证码) 的十六进制）。</p>
 *
 * <p>状态 state：0 待使用 / 1 已使用 / 2 已作废（作废原因写 failReason）。</p>
 *
 * <p>表名沿用历史上的 {@code uf_oaones_login_sms}（避免老库改表名）：表结构里
 * {@code mobile} 允许为空、新增 {@code email} 列后即可同时承载两种验证方式。</p>
 */
public class LoginSms {

    private Integer id;
    /** 接收验证码的手机号（已规范化成 11 位）；邮箱验证码的行留空。 */
    private String mobile;
    /** 接收验证码的邮箱；手机号验证码的行留空。 */
    private String email;
    /** 用途：login（登录验证码；两种验证方式共用同一个用途值）。 */
    private String purpose;
    /** 每条验证码随机的盐。 */
    private String codeSalt;
    /** SHA-256(salt + 验证码) 十六进制。 */
    private String codeHash;
    /** 0 待使用 / 1 已使用 / 2 已作废。 */
    private Integer state;
    /** 过期时间（毫秒时间戳）。 */
    private Long expireTime;
    /** 输错次数。 */
    private Integer wrongTimes;
    private Long sendTime;
    private String sendIp;
    private String sendUa;
    private Long verifyTime;
    private String verifyIp;
    private String failReason;
    private Integer formmodeid;
    private Integer modedatacreater;
    private String modedatacreatedate;
    private String modedatacreatetime;
    private Integer modedatamodifier;
    private String modedatamodifydatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getCodeSalt() { return codeSalt; }
    public void setCodeSalt(String codeSalt) { this.codeSalt = codeSalt; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public Integer getState() { return state; }
    public void setState(Integer state) { this.state = state; }

    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }

    public Integer getWrongTimes() { return wrongTimes; }
    public void setWrongTimes(Integer wrongTimes) { this.wrongTimes = wrongTimes; }

    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }

    public String getSendIp() { return sendIp; }
    public void setSendIp(String sendIp) { this.sendIp = sendIp; }

    public String getSendUa() { return sendUa; }
    public void setSendUa(String sendUa) { this.sendUa = sendUa; }

    public Long getVerifyTime() { return verifyTime; }
    public void setVerifyTime(Long verifyTime) { this.verifyTime = verifyTime; }

    public String getVerifyIp() { return verifyIp; }
    public void setVerifyIp(String verifyIp) { this.verifyIp = verifyIp; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public Integer getFormmodeid() { return formmodeid; }
    /** 建模模块ID按表名反查；反查不到（表不是建模表）时不写值（留 NULL，不写 0）。 */
    public void setFormmodeid(Integer formmodeid) {
        this.formmodeid = (formmodeid == null || formmodeid.intValue() <= 0) ? null : formmodeid;
    }

    public Integer getModedatacreater() { return modedatacreater; }
    public void setModedatacreater(Integer modedatacreater) { this.modedatacreater = modedatacreater; }

    public String getModedatacreatedate() { return modedatacreatedate; }
    public void setModedatacreatedate(String modedatacreatedate) { this.modedatacreatedate = modedatacreatedate; }

    public String getModedatacreatetime() { return modedatacreatetime; }
    public void setModedatacreatetime(String modedatacreatetime) { this.modedatacreatetime = modedatacreatetime; }

    public Integer getModedatamodifier() { return modedatamodifier; }
    public void setModedatamodifier(Integer modedatamodifier) { this.modedatamodifier = modedatamodifier; }

    public String getModedatamodifydatetime() { return modedatamodifydatetime; }
    public void setModedatamodifydatetime(String modedatamodifydatetime) {
        this.modedatamodifydatetime = modedatamodifydatetime;
    }
}
