/* OAones：同步结果汇总与逐条明细增加 requestid（OA 流程请求ID）。 */
IF COL_LENGTH('dbo.uf_oaones_sync_log', 'requestid') IS NULL
    ALTER TABLE dbo.uf_oaones_sync_log ADD requestid int NULL;
GO

IF COL_LENGTH('dbo.uf_oaones_sync_log_item', 'requestid') IS NULL
    ALTER TABLE dbo.uf_oaones_sync_log_item ADD requestid int NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.uf_oaones_sync_log')
      AND name = 'IDX_OAONES_SYNC_LOG_REQ'
)
    CREATE INDEX IDX_OAONES_SYNC_LOG_REQ
        ON dbo.uf_oaones_sync_log(requestid, id);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.uf_oaones_sync_log_item')
      AND name = 'IDX_OAONES_SYNC_LOG_ITEM_REQ'
)
    CREATE INDEX IDX_OAONES_SYNC_LOG_ITEM_REQ
        ON dbo.uf_oaones_sync_log_item(requestid, id);
GO
