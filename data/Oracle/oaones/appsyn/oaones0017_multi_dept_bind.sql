/* OAones：允许同一 OA 部门在同一租户映射多个平台部门。
 * 人员同步默认部门 = 该 OA 部门启用绑定中 id 最小的一条；删除后由下一条自动接替。 */
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
