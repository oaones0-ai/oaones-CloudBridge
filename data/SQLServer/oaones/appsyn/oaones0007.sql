IF COL_LENGTH('dbo.uf_oaones_login_config','logo') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD logo varchar(500) NULL;
        PRINT 'added uf_oaones_login_config.logo';
    END
ELSE
    PRINT 'uf_oaones_login_config.logo already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','bg_image') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD bg_image varchar(500) NULL;
        PRINT 'added uf_oaones_login_config.bg_image';
    END
ELSE
    PRINT 'uf_oaones_login_config.bg_image already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','brand_html') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD brand_html nvarchar(max) NULL;
        PRINT 'added uf_oaones_login_config.brand_html';
    END
ELSE
    PRINT 'uf_oaones_login_config.brand_html already exists';
GO
