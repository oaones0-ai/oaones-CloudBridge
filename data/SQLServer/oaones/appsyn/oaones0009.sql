IF COL_LENGTH('dbo.uf_oaones_login_config','login_box_left') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD login_box_left int NULL;
        PRINT 'added uf_oaones_login_config.login_box_left';
    END
ELSE
    PRINT 'uf_oaones_login_config.login_box_left already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','login_box_top') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD login_box_top int NULL;
        PRINT 'added uf_oaones_login_config.login_box_top';
    END
ELSE
    PRINT 'uf_oaones_login_config.login_box_top already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','login_box_width') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD login_box_width int NULL;
        PRINT 'added uf_oaones_login_config.login_box_width';
    END
ELSE
    PRINT 'uf_oaones_login_config.login_box_width already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','login_box_height') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD login_box_height int NULL;
        PRINT 'added uf_oaones_login_config.login_box_height';
    END
ELSE
    PRINT 'uf_oaones_login_config.login_box_height already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_config','gopage_seconds') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD gopage_seconds int NULL;
        PRINT 'added uf_oaones_login_config.gopage_seconds';
    END
ELSE
    PRINT 'uf_oaones_login_config.gopage_seconds already exists';
GO
