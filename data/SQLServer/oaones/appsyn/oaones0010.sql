IF COL_LENGTH('dbo.uf_oaones_login_config','login_box_opacity') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD login_box_opacity int NULL;
        PRINT 'added uf_oaones_login_config.login_box_opacity';
    END
ELSE
    PRINT 'uf_oaones_login_config.login_box_opacity already exists';
GO
