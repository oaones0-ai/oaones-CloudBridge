ALTER TABLE uf_oaones_login_config
    ADD COLUMN login_layout_mode tinyint NOT NULL DEFAULT 0
        COMMENT '登录框与左侧文字共用定位方式：0左上角相对/1中心点相对' AFTER brand_html;
