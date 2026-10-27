ALTER TABLE uf_oaones_app ADD (vis_role_ids VARCHAR2(2000 CHAR));
COMMENT ON COLUMN uf_oaones_app.vis_role_ids IS '可见范围·角色（OA角色ID，逗号分隔，可多选；角色成员按当前组织归属判定）';
