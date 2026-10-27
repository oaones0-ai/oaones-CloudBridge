ALTER TABLE uf_oaones_att_cfg
    ADD COLUMN oa_account_rule int NOT NULL DEFAULT 0
        COMMENT 'OA多账户同步规则：0只同步主账户/1所有账户均同步' AFTER scope_text;
