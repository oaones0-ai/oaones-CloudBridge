IF COL_LENGTH('dbo.uf_oaones_login_config','email_enable') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_config ADD email_enable int NULL DEFAULT 0;
        PRINT 'added uf_oaones_login_config.email_enable';
    END
ELSE
    PRINT 'uf_oaones_login_config.email_enable already exists';
GO

IF COL_LENGTH('dbo.uf_oaones_login_sms','email') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_sms ADD email varchar(128) NULL;
        PRINT 'added uf_oaones_login_sms.email';
    END
ELSE
    PRINT 'uf_oaones_login_sms.email already exists';
GO

IF EXISTS (SELECT 1 FROM sys.columns c
           WHERE c.object_id = OBJECT_ID('dbo.uf_oaones_login_sms')
             AND c.name = 'mobile' AND c.is_nullable = 0)
    BEGIN
        ALTER TABLE dbo.uf_oaones_login_sms ALTER COLUMN mobile varchar(32) NULL;
        PRINT 'altered uf_oaones_login_sms.mobile -> NULL';
    END
ELSE
    PRINT 'uf_oaones_login_sms.mobile already nullable';
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
               WHERE i.object_id = OBJECT_ID('dbo.uf_oaones_login_sms')
                 AND i.name = 'IDX_OAONES_LOGIN_SMS_EMAIL')
    BEGIN
        CREATE INDEX IDX_OAONES_LOGIN_SMS_EMAIL ON dbo.uf_oaones_login_sms(email, send_time);
        PRINT 'created IDX_OAONES_LOGIN_SMS_EMAIL';
    END
ELSE
    PRINT 'IDX_OAONES_LOGIN_SMS_EMAIL already exists';
GO
