CREATE TABLE IF NOT EXISTS uf_oaones_sub_tenant_map (
    id                     int NOT NULL AUTO_INCREMENT,
    cfg_id                 int NOT NULL,
    oa_sub_id              int NOT NULL COMMENT 'hrmsubcompany.id',
    type                   int NOT NULL DEFAULT 0 COMMENT '平台类型：0企微 / 1钉钉 / 2飞书',
    yx                     int NULL DEFAULT 0 COMMENT '状态：0有效 / 1失效',
    remark                 varchar(300) NULL,
    formmodeid             int NULL,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_SUB_TENANT_SUB (type, oa_sub_id),
    UNIQUE KEY UK_OAONES_SUB_TENANT_CFG (type, cfg_id),
    KEY IDX_OAONES_SUB_TENANT_CFG (cfg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones OA分部-平台租户一对一映射';
