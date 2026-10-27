ALTER TABLE uf_oaones_login_config ADD COLUMN email_enable int NULL DEFAULT 0;
ALTER TABLE uf_oaones_login_sms ADD COLUMN email varchar(128) NULL;
ALTER TABLE uf_oaones_login_sms MODIFY COLUMN mobile varchar(32) NULL;
CREATE INDEX IDX_OAONES_LOGIN_SMS_EMAIL ON uf_oaones_login_sms(email, send_time);
