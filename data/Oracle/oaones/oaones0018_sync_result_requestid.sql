/* OAones：同步结果汇总与逐条明细增加 requestid（OA 流程请求ID）。 */
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_cnt FROM user_tab_columns
     WHERE table_name = 'UF_OAONES_SYNC_LOG' AND column_name = 'REQUESTID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE uf_oaones_sync_log ADD (requestid NUMBER(10) NULL)';
    END IF;

    SELECT COUNT(1) INTO v_cnt FROM user_tab_columns
     WHERE table_name = 'UF_OAONES_SYNC_LOG_ITEM' AND column_name = 'REQUESTID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE uf_oaones_sync_log_item ADD (requestid NUMBER(10) NULL)';
    END IF;

    SELECT COUNT(1) INTO v_cnt FROM user_indexes
     WHERE index_name = 'IDX_OAONES_SYNC_LOG_REQ';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_OAONES_SYNC_LOG_REQ ON uf_oaones_sync_log(requestid, id)';
    END IF;

    SELECT COUNT(1) INTO v_cnt FROM user_indexes
     WHERE index_name = 'IDX_OAONES_SYNC_LOG_ITEM_REQ';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_OAONES_SYNC_LOG_ITEM_REQ ON uf_oaones_sync_log_item(requestid, id)';
    END IF;
END;
/
