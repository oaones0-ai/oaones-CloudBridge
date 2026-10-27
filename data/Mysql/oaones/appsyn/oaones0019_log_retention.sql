/* OAones：日志保留设置。默认 0 = 永久保留，自动任务每天检查一次。 */
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS uf_oaones_log_retention (
    id                     int NOT NULL AUTO_INCREMENT,
    log_code               varchar(64) NOT NULL,
    keep_days              int NOT NULL DEFAULT 0,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_LOG_RETENTION_CODE (log_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 日志保留设置（<=0 永久保留）';

INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'api_log', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'api_log');
INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'op_log', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'op_log');
INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'msg_log', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'msg_log');
INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'att_log', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'att_log');
INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'att_log_item', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'att_log_item');
INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'sync_log', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'sync_log');
INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'sync_log_item', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'sync_log_item');
INSERT INTO uf_oaones_log_retention (log_code, keep_days)
SELECT 'mtg_sync_log', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uf_oaones_log_retention WHERE log_code = 'mtg_sync_log');
