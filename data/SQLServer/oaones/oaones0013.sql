IF COL_LENGTH('dbo.uf_oaones_user_bind','wx_dept_key') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_user_bind ADD wx_dept_key varchar(128) NULL;
        PRINT 'added uf_oaones_user_bind.wx_dept_key';
    END
ELSE
    PRINT 'uf_oaones_user_bind.wx_dept_key already exists';
GO
