IF OBJECT_ID('dbo.uf_oaones_appsyn_feature_setting','U') IS NULL
BEGIN
    CREATE TABLE dbo.uf_oaones_appsyn_feature_setting (
        id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
        feature_code           varchar(80) NOT NULL,
        enabled                tinyint NOT NULL DEFAULT 1,
        modedatacreater        int NULL,
        modedatacreatedate     varchar(10) NULL,
        modedatacreatetime     varchar(8) NULL,
        modedatamodifier       int NULL,
        modedatamodifydatetime varchar(100) NULL,
        CONSTRAINT UK_OAONES_FEATURE_SETTING_CODE UNIQUE (feature_code)
    );
    PRINT 'created uf_oaones_appsyn_feature_setting';
END
ELSE
    PRINT 'uf_oaones_appsyn_feature_setting already exists';
;

IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'person.sync.oa2third')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('person.sync.oa2third', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'person.sync.third2oa')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('person.sync.third2oa', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'person.sync.schedule')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('person.sync.schedule', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'app.bind')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('app.bind', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.sync')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('user.bind.sync', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.sync.oa2third')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('user.bind.sync.oa2third', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.sync.third2oa')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('user.bind.sync.third2oa', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.self')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('user.bind.self', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.export')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('user.bind.export', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'att.sync')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('att.sync', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'att.sync.oa2third')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('att.sync.oa2third', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'att.sync.third2oa')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('att.sync.third2oa', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'mtg.sync')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('mtg.sync', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'mtg.sync.oa2third')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('mtg.sync.oa2third', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'mtg.sync.third2oa')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('mtg.sync.third2oa', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'schedule.sync')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('schedule.sync', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'schedule.sync.oa2third')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('schedule.sync.oa2third', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_appsyn_feature_setting WHERE feature_code = 'schedule.sync.third2oa')
    INSERT INTO dbo.uf_oaones_appsyn_feature_setting (feature_code, enabled) VALUES ('schedule.sync.third2oa', 1);
;
