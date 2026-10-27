/* OAones：允许同一 OA 部门在同一租户映射多个平台部门。
 * 人员同步默认部门 = 该 OA 部门启用绑定中 id 最小的一条；删除后由下一条自动接替。 */
IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.uf_oaones_dept_bind')
      AND name = 'UK_OAONES_DEPT_BIND_OA'
)
    DROP INDEX UK_OAONES_DEPT_BIND_OA ON dbo.uf_oaones_dept_bind;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.uf_oaones_dept_bind')
      AND name = 'IDX_OAONES_DEPT_BIND_OA'
)
    CREATE INDEX IDX_OAONES_DEPT_BIND_OA
        ON dbo.uf_oaones_dept_bind(cfg_id, oa_dept_id, id);
GO
