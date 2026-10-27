IF COL_LENGTH('dbo.uf_oaones_login_config','brand_left') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD brand_left int NULL;
        PRINT 'added uf_oaones_login_config.brand_left';
    END
ELSE
    PRINT 'uf_oaones_login_config.brand_left already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','brand_top') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD brand_top int NULL;
        PRINT 'added uf_oaones_login_config.brand_top';
    END
ELSE
    PRINT 'uf_oaones_login_config.brand_top already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','brand_width') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD brand_width int NULL;
        PRINT 'added uf_oaones_login_config.brand_width';
    END
ELSE
    PRINT 'uf_oaones_login_config.brand_width already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','brand_height') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD brand_height int NULL;
        PRINT 'added uf_oaones_login_config.brand_height';
    END
ELSE
    PRINT 'uf_oaones_login_config.brand_height already exists';
GO
