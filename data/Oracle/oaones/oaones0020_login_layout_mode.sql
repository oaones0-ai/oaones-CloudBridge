ALTER TABLE uf_oaones_login_config ADD (login_layout_mode NUMBER(1) DEFAULT 0 NOT NULL);
COMMENT ON COLUMN uf_oaones_login_config.login_layout_mode IS '登录框与左侧文字共用定位方式：0左上角相对/1中心点相对';
