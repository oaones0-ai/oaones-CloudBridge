ALTER TABLE uf_oaones_login_config ADD (logo VARCHAR2(500 CHAR));
ALTER TABLE uf_oaones_login_config ADD (bg_image VARCHAR2(500 CHAR));
ALTER TABLE uf_oaones_login_config ADD (brand_html CLOB);
COMMENT ON COLUMN uf_oaones_login_config.logo IS '登录页 logo 的站内地址（/oaones/img/loginlogo_xxx.png）；空 = 登录页不显示';
COMMENT ON COLUMN uf_oaones_login_config.bg_image IS '登录页整页背景图的站内地址（/oaones/img/loginbg_xxx.png）；空 = 用系统默认底色';
COMMENT ON COLUMN uf_oaones_login_config.brand_html IS '登录页左侧富文本（HTML）；空 = 用系统默认的左侧说明';
