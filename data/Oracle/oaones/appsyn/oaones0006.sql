ALTER TABLE uf_oaones_login_config ADD (email_enable NUMBER(10) DEFAULT 0);
COMMENT ON COLUMN uf_oaones_login_config.email_enable IS '邮箱验证登录：0 关 / 1 开';
ALTER TABLE uf_oaones_login_sms ADD (email VARCHAR2(128 CHAR));
COMMENT ON COLUMN uf_oaones_login_sms.email IS '接收验证码的邮箱；手机号验证码的行留空';
ALTER TABLE uf_oaones_login_sms MODIFY (mobile VARCHAR2(32 CHAR) NULL);
COMMENT ON COLUMN uf_oaones_login_sms.mobile IS '接收验证码的手机号（规范化后的 11 位）；邮箱验证码的行留空';
CREATE INDEX IDX_OAONES_LOGIN_SMS_EMAIL ON uf_oaones_login_sms(email, send_time);
COMMENT ON TABLE uf_oaones_login_sms IS 'OAones 登录验证码（手机号 / 邮箱）';
