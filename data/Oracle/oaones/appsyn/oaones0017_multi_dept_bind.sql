DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_cnt FROM user_indexes
     WHERE index_name = 'UK_OAONES_DEPT_BIND_OA';
    IF v_cnt > 0 THEN
        EXECUTE IMMEDIATE 'DROP INDEX UK_OAONES_DEPT_BIND_OA';
    END IF;

    SELECT COUNT(1) INTO v_cnt FROM user_indexes
     WHERE index_name = 'IDX_OAONES_DEPT_BIND_OA';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_OAONES_DEPT_BIND_OA ON uf_oaones_dept_bind(cfg_id, oa_dept_id, id)';
    END IF;
END;
/
