ALTER TABLE uf_oaones_att_cfg ADD (oa_account_rule NUMBER(10) DEFAULT 0 NOT NULL);
COMMENT ON COLUMN uf_oaones_att_cfg.oa_account_rule IS 'OA多账户同步规则：0只同步主账户/1所有账户均同步';
