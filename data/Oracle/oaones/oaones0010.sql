ALTER TABLE uf_oaones_login_config ADD (login_box_opacity NUMBER(3));
COMMENT ON COLUMN uf_oaones_login_config.login_box_opacity IS '登录框背景透明度（0 = 不透明，100 = 完全透明；空 = 0）';
