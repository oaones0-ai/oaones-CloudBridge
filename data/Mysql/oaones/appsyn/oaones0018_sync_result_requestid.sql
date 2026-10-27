/* OAones：同步结果汇总与逐条明细增加 requestid（OA 流程请求ID）。
 * 流程建号写同一 requestid，可在同步结果页跨平台、跨租户统一查询。 */
SET @oaones_add_log_requestid := (
    SELECT IF(COUNT(1) = 0,
        'ALTER TABLE uf_oaones_sync_log ADD COLUMN requestid int NULL AFTER id',
        'SELECT ''uf_oaones_sync_log.requestid already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'uf_oaones_sync_log'
      AND column_name = 'requestid'
);
PREPARE oaones_add_log_requestid_stmt FROM @oaones_add_log_requestid;
EXECUTE oaones_add_log_requestid_stmt;
DEALLOCATE PREPARE oaones_add_log_requestid_stmt;

SET @oaones_add_item_requestid := (
    SELECT IF(COUNT(1) = 0,
        'ALTER TABLE uf_oaones_sync_log_item ADD COLUMN requestid int NULL AFTER log_id',
        'SELECT ''uf_oaones_sync_log_item.requestid already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'uf_oaones_sync_log_item'
      AND column_name = 'requestid'
);
PREPARE oaones_add_item_requestid_stmt FROM @oaones_add_item_requestid;
EXECUTE oaones_add_item_requestid_stmt;
DEALLOCATE PREPARE oaones_add_item_requestid_stmt;

SET @oaones_add_log_requestid_idx := (
    SELECT IF(COUNT(1) = 0,
        'ALTER TABLE uf_oaones_sync_log ADD INDEX IDX_OAONES_SYNC_LOG_REQ (requestid, id)',
        'SELECT ''IDX_OAONES_SYNC_LOG_REQ already exists''')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'uf_oaones_sync_log'
      AND index_name = 'IDX_OAONES_SYNC_LOG_REQ'
);
PREPARE oaones_add_log_requestid_idx_stmt FROM @oaones_add_log_requestid_idx;
EXECUTE oaones_add_log_requestid_idx_stmt;
DEALLOCATE PREPARE oaones_add_log_requestid_idx_stmt;

SET @oaones_add_item_requestid_idx := (
    SELECT IF(COUNT(1) = 0,
        'ALTER TABLE uf_oaones_sync_log_item ADD INDEX IDX_OAONES_SYNC_LOG_ITEM_REQ (requestid, id)',
        'SELECT ''IDX_OAONES_SYNC_LOG_ITEM_REQ already exists''')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'uf_oaones_sync_log_item'
      AND index_name = 'IDX_OAONES_SYNC_LOG_ITEM_REQ'
);
PREPARE oaones_add_item_requestid_idx_stmt FROM @oaones_add_item_requestid_idx;
EXECUTE oaones_add_item_requestid_idx_stmt;
DEALLOCATE PREPARE oaones_add_item_requestid_idx_stmt;
