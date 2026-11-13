SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS uf_oaones_appsyn_feature_setting (
    id                     int NOT NULL AUTO_INCREMENT,
    feature_code           varchar(80) NOT NULL,
    enabled                tinyint NOT NULL DEFAULT 1,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_FEATURE_SETTING_CODE (feature_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 功能设置（1 启用 / 0 隐藏）';

INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'person.sync.oa2third', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'person.sync.oa2third');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'person.sync.third2oa', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'person.sync.third2oa');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'person.sync.schedule', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'person.sync.schedule');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'app.bind', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'app.bind');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'user.bind.sync', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.sync');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'user.bind.sync.oa2third', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.sync.oa2third');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'user.bind.sync.third2oa', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.sync.third2oa');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'user.bind.self', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.self');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'user.bind.export', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'user.bind.export');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'att.sync', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'att.sync');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'att.sync.oa2third', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'att.sync.oa2third');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'att.sync.third2oa', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'att.sync.third2oa');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'mtg.sync', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'mtg.sync');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'mtg.sync.oa2third', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'mtg.sync.oa2third');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'mtg.sync.third2oa', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'mtg.sync.third2oa');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'schedule.sync', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'schedule.sync');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'schedule.sync.oa2third', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'schedule.sync.oa2third');
INSERT INTO uf_oaones_appsyn_feature_setting (feature_code, enabled)
SELECT 'schedule.sync.third2oa', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_appsyn_feature_setting WHERE feature_code = 'schedule.sync.third2oa');
