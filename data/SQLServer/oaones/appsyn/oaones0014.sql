IF COL_LENGTH('dbo.uf_oaones_att_cfg','oa_account_rule') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_att_cfg
            ADD oa_account_rule int NOT NULL
                CONSTRAINT DF_OAONES_ATT_CFG_OA_ACCOUNT_RULE DEFAULT 0;
        PRINT 'added uf_oaones_att_cfg.oa_account_rule';
    END
ELSE
    PRINT 'uf_oaones_att_cfg.oa_account_rule already exists';
GO
