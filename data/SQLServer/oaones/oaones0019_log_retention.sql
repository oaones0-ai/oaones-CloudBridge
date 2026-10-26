/* OAones：日志保留设置。默认 0 = 永久保留，自动任务每天检查一次。 */
IF OBJECT_ID('dbo.uf_oaones_log_retention','U') IS NULL
BEGIN
    CREATE TABLE dbo.uf_oaones_log_retention (
        id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
        log_code               varchar(64) NOT NULL,
        keep_days              int NOT NULL DEFAULT 0,
        modedatacreater        int NULL,
        modedatacreatedate     varchar(10) NULL,
        modedatacreatetime     varchar(8) NULL,
        modedatamodifier       int NULL,
        modedatamodifydatetime varchar(100) NULL,
        CONSTRAINT UK_OAONES_LOG_RETENTION_CODE UNIQUE (log_code)
    );
    PRINT 'created uf_oaones_log_retention';
END
ELSE
    PRINT 'uf_oaones_log_retention already exists';
GO

IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'api_log')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('api_log', 0);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'op_log')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('op_log', 0);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'msg_log')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('msg_log', 0);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'att_log')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('att_log', 0);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'att_log_item')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('att_log_item', 0);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'sync_log')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('sync_log', 0);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'sync_log_item')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('sync_log_item', 0);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_log_retention WHERE log_code = 'mtg_sync_log')
    INSERT INTO dbo.uf_oaones_log_retention (log_code, keep_days) VALUES ('mtg_sync_log', 0);
GO
