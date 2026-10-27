ALTER TABLE uf_oaones_login_config ADD (brand_opacity NUMBER(3));
COMMENT ON COLUMN uf_oaones_login_config.brand_opacity IS '左侧文字背景透明度（0 = 不透明，100 = 完全透明；空 = 100）';
