IF OBJECT_ID('dbo.uf_oaones_sub_tenant_map','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_sub_tenant_map (
            id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
            cfg_id                 int NOT NULL,
            oa_sub_id              int NOT NULL,
            type                   int NOT NULL DEFAULT 0,
            yx                     int NULL DEFAULT 0,
            remark                 varchar(300) NULL,
            formmodeid             int NULL,
            modedatacreater        int NULL,
            modedatacreatedate     varchar(10) NULL,
            modedatacreatetime     varchar(8) NULL,
            modedatamodifier       int NULL,
            modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_SUB_TENANT_SUB
            ON dbo.uf_oaones_sub_tenant_map(type, oa_sub_id);
        CREATE UNIQUE INDEX UK_OAONES_SUB_TENANT_CFG
            ON dbo.uf_oaones_sub_tenant_map(type, cfg_id);
        CREATE INDEX IDX_OAONES_SUB_TENANT_CFG
            ON dbo.uf_oaones_sub_tenant_map(cfg_id);
        PRINT 'created uf_oaones_sub_tenant_map';
    END
ELSE
    PRINT 'uf_oaones_sub_tenant_map already exists';
;
