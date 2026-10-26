ALTER TABLE uf_oaones_user_bind ADD (wx_dept_key VARCHAR2(128));
COMMENT ON COLUMN uf_oaones_user_bind.wx_dept_key IS '平台部门字符串快照（飞书部门ID形如 od-xxxx；企微 / 钉钉使用 wx_dept_id）';
