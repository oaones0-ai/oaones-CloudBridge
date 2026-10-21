IF COL_LENGTH('dbo.uf_oaones_login_config','brand_opacity') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD brand_opacity int NULL;
        PRINT 'added uf_oaones_login_config.brand_opacity';
    END
ELSE
    PRINT 'uf_oaones_login_config.brand_opacity already exists';
GO
