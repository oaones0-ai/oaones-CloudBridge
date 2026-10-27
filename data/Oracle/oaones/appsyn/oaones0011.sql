ALTER TABLE uf_oaones_login_config ADD (brand_left NUMBER(10));
ALTER TABLE uf_oaones_login_config ADD (brand_top NUMBER(10));
ALTER TABLE uf_oaones_login_config ADD (brand_width NUMBER(10));
ALTER TABLE uf_oaones_login_config ADD (brand_height NUMBER(10));
COMMENT ON COLUMN uf_oaones_login_config.brand_left IS '登录页左侧文字左上角 X（CSS 像素）；空 = 默认版式';
COMMENT ON COLUMN uf_oaones_login_config.brand_top IS '登录页左侧文字左上角 Y（CSS 像素）；空 = 默认版式';
COMMENT ON COLUMN uf_oaones_login_config.brand_width IS '登录页左侧文字宽度（px）；空 = 默认版式';
COMMENT ON COLUMN uf_oaones_login_config.brand_height IS '登录页左侧文字高度（px，内容超出时区内滚动）；空 = 默认版式';
