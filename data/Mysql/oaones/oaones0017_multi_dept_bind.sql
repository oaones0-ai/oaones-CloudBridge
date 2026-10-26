/* OAones：允许同一 OA 部门在同一租户映射多个平台部门。
 * 人员同步默认部门 = 该 OA 部门启用绑定中 id 最小的一条；删除后由下一条自动接替。 */
SET @oaones_drop_uk := (
    SELECT IF(COUNT(1) > 0,
        'ALTER TABLE uf_oaones_dept_bind DROP INDEX UK_OAONES_DEPT_BIND_OA',
        'SELECT ''UK_OAONES_DEPT_BIND_OA not exists''')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'uf_oaones_dept_bind'
      AND index_name = 'UK_OAONES_DEPT_BIND_OA'
);
PREPARE oaones_drop_uk_stmt FROM @oaones_drop_uk;
EXECUTE oaones_drop_uk_stmt;
DEALLOCATE PREPARE oaones_drop_uk_stmt;

SET @oaones_add_idx := (
    SELECT IF(COUNT(1) = 0,
        'ALTER TABLE uf_oaones_dept_bind ADD INDEX IDX_OAONES_DEPT_BIND_OA (cfg_id, oa_dept_id, id)',
        'SELECT ''IDX_OAONES_DEPT_BIND_OA already exists''')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'uf_oaones_dept_bind'
      AND index_name = 'IDX_OAONES_DEPT_BIND_OA'
);
PREPARE oaones_add_idx_stmt FROM @oaones_add_idx;
EXECUTE oaones_add_idx_stmt;
DEALLOCATE PREPARE oaones_add_idx_stmt;
