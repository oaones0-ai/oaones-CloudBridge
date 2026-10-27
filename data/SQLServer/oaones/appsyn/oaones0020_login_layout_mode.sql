IF COL_LENGTH('dbo.uf_oaones_login_config','login_layout_mode') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config
            ADD login_layout_mode tinyint NOT NULL
                CONSTRAINT DF_OAONES_LOGIN_CFG_LAYOUT_MODE DEFAULT 0;
        PRINT 'added uf_oaones_login_config.login_layout_mode';
    END
ELSE
    PRINT 'uf_oaones_login_config.login_layout_mode already exists';
GO
