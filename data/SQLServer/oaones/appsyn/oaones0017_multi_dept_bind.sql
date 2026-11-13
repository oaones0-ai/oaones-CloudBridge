IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.uf_oaones_dept_bind')
      AND name = 'UK_OAONES_DEPT_BIND_OA'
)
    DROP INDEX UK_OAONES_DEPT_BIND_OA ON dbo.uf_oaones_dept_bind;
;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.uf_oaones_dept_bind')
      AND name = 'IDX_OAONES_DEPT_BIND_OA'
)
    CREATE INDEX IDX_OAONES_DEPT_BIND_OA
        ON dbo.uf_oaones_dept_bind(cfg_id, oa_dept_id, id);
;
