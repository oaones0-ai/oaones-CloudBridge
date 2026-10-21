IF OBJECT_ID('dbo.uf_oaones_att_cfg','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_att_cfg (
                                               id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                               type                   int NOT NULL DEFAULT 0,
                                               cfgid                  int NOT NULL,
                                               row_type               int NOT NULL DEFAULT 0,
                                               cfg_name               varchar(60) NOT NULL DEFAULT '',
                                               dir                    int NOT NULL DEFAULT 0,
                                               scope_subs             varchar(1000) NULL,
                                               scope_depts            varchar(1000) NULL,
                                               scope_users            varchar(4000) NULL,
                                               scope_text             varchar(1000) NULL,
                                               data_types             int NOT NULL DEFAULT 1,
                                               sign_periods           int NOT NULL DEFAULT 3,
                                               win_mode               int NOT NULL DEFAULT 0,
                                               win_from               varchar(19) NULL,
                                               win_to                 varchar(19) NULL,
                                               win_days               int NULL DEFAULT 2,
                                               app_id                 int NOT NULL DEFAULT 0,
                                               plan_mode              int NOT NULL DEFAULT 0,
                                               plan_workday_group_id  int NULL DEFAULT 0,
                                               cron_day_type          int NULL DEFAULT 0,
                                               day_cron               varchar(100) NULL,
                                               night_cron             varchar(100) NULL,
                                               enabled                int NOT NULL DEFAULT 0,
                                               check_state            int NOT NULL DEFAULT 0,
                                               check_msg              varchar(1000) NULL,
                                               check_time             varchar(19) NULL,
                                               last_run_time          varchar(19) NULL,
                                               last_result            int NULL,
                                               last_result_text       varchar(1000) NULL,
                                               fail_streak            int NULL DEFAULT 0,
                                               last_log_id            int NULL DEFAULT 0,
                                               remark                 varchar(500) NULL,
                                               modedatacreater        int NULL,
                                               modedatacreatedate     varchar(10) NULL,
                                               modedatacreatetime     varchar(8) NULL,
                                               modedatamodifier       int NULL,
                                               modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_ATT_CFG_NAME ON dbo.uf_oaones_att_cfg(type, cfgid, row_type, cfg_name);
        CREATE INDEX IDX_OAONES_ATT_CFG_TENANT ON dbo.uf_oaones_att_cfg(type, cfgid, row_type);
        CREATE INDEX IDX_OAONES_ATT_CFG_ENABLE ON dbo.uf_oaones_att_cfg(enabled, type);
        PRINT 'created uf_oaones_att_cfg';
    END
ELSE
    PRINT 'uf_oaones_att_cfg already exists';
GO

IF OBJECT_ID('dbo.uf_oaones_att_plan_item','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_att_plan_item (
                                                     id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                     cfg_id                 int NOT NULL,
                                                     day_type               int NULL DEFAULT 0,
                                                     start_time             varchar(5) NULL,
                                                     end_time               varchar(5) NULL,
                                                     interval_min           int NULL,
                                                     showorder              int NULL,
                                                     remark                 varchar(500) NULL,
                                                     modedatacreater        int NULL,
                                                     modedatacreatedate     varchar(10) NULL,
                                                     modedatacreatetime     varchar(8) NULL,
                                                     modedatamodifier       int NULL,
                                                     modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX idx_oaones_att_plan_item_cfg ON dbo.uf_oaones_att_plan_item(cfg_id, showorder);
        PRINT 'created uf_oaones_att_plan_item';
    END
ELSE
    PRINT 'uf_oaones_att_plan_item already exists';
GO

IF OBJECT_ID('dbo.uf_oaones_att_log','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_att_log (
                                               id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                               type                   int NOT NULL DEFAULT 0,
                                               cfgid                  int NOT NULL,
                                               trigger_type           int NULL DEFAULT 0,
                                               dir                    int NULL DEFAULT 0,
                                               cfg_id                 int NULL DEFAULT 0,
                                               cfg_name               varchar(60) NULL,
                                               win_mode               int NULL DEFAULT 0,
                                               win_from               varchar(19) NULL,
                                               win_to                 varchar(19) NULL,
                                               scope_text             varchar(1000) NULL,
                                               data_types             int NULL DEFAULT 0,
                                               begin_time             varchar(19) NULL,
                                               end_time               varchar(19) NULL,
                                               cost_ms                int NULL,
                                               add_count              int NULL DEFAULT 0,
                                               update_count           int NULL DEFAULT 0,
                                               skip_count             int NULL DEFAULT 0,
                                               fail_count             int NULL DEFAULT 0,
                                               api_count              int NULL DEFAULT 0,
                                               result                 int NULL,
                                               message                varchar(1000) NULL,
                                               operator_id            int NULL,
                                               operator_name          varchar(200) NULL,
                                               remark                 varchar(500) NULL,
                                               modedatacreater        int NULL,
                                               modedatacreatedate     varchar(10) NULL,
                                               modedatacreatetime     varchar(8) NULL,
                                               modedatamodifier       int NULL,
                                               modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_ATT_LOG_FILTER ON dbo.uf_oaones_att_log(type, cfgid, trigger_type, modedatacreatedate);
        CREATE INDEX IDX_OAONES_ATT_LOG_TIME ON dbo.uf_oaones_att_log(modedatacreatedate, modedatacreatetime);
        CREATE INDEX IDX_OAONES_ATT_LOG_CFG ON dbo.uf_oaones_att_log(cfg_id);
        CREATE INDEX IDX_OAONES_ATT_LOG_RESULT ON dbo.uf_oaones_att_log(result, modedatacreatedate);
        PRINT 'created uf_oaones_att_log';
    END
ELSE
    PRINT 'uf_oaones_att_log already exists';
GO

IF OBJECT_ID('dbo.uf_oaones_att_log_item','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_att_log_item (
                                                    id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                    log_id                 int NULL,
                                                    type                   int NOT NULL DEFAULT 0,
                                                    cfgid                  int NOT NULL,
                                                    cfg_id                 int NULL DEFAULT 0,
                                                    dir                    int NULL DEFAULT 0,
                                                    trigger_type           int NULL DEFAULT 0,
                                                    oa_user_id             int NULL,
                                                    oa_user_name           varchar(200) NULL,
                                                    login_id               varchar(100) NULL,
                                                    wx_user_id             varchar(128) NULL,
                                                    sign_date              varchar(10) NULL,
                                                    sign_time              varchar(8) NULL,
                                                    sign_datetime          varchar(19) NULL,
                                                    sign_type              int NULL,
                                                    data_type              int NULL,
                                                    raw_type               varchar(200) NULL,
                                                    raw_summary            varchar(500) NULL,
                                                    op                     int NULL,
                                                    message                varchar(1000) NULL,
                                                    oa_sign_id             varchar(64) NULL,
                                                    wx_record_id           varchar(128) NULL,
                                                    api_log_id             int NULL DEFAULT 0,
                                                    win_date               varchar(10) NULL,
                                                    remark                 varchar(500) NULL,
                                                    modedatacreater        int NULL,
                                                    modedatacreatedate     varchar(10) NULL,
                                                    modedatacreatetime     varchar(8) NULL,
                                                    modedatamodifier       int NULL,
                                                    modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_ATT_ITEM_LOG ON dbo.uf_oaones_att_log_item(log_id);
        CREATE INDEX IDX_OAONES_ATT_ITEM_OA ON dbo.uf_oaones_att_log_item(type, cfgid, oa_user_id, sign_date);
        CREATE INDEX IDX_OAONES_ATT_ITEM_TIME ON dbo.uf_oaones_att_log_item(modedatacreatedate, modedatacreatetime);
        CREATE INDEX IDX_OAONES_ATT_ITEM_OP ON dbo.uf_oaones_att_log_item(op, type, cfgid);
        PRINT 'created uf_oaones_att_log_item';
    END
ELSE
    PRINT 'uf_oaones_att_log_item already exists';
GO

IF OBJECT_ID('dbo.uf_oaones_att_link','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_att_link (
                                                id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                type                   int NOT NULL DEFAULT 0,
                                                cfgid                  int NOT NULL,
                                                dir                    int NOT NULL DEFAULT 0,
                                                oa_user_id             int NOT NULL,
                                                wx_user_id             varchar(128) NOT NULL DEFAULT '',
                                                sign_date              varchar(10) NOT NULL DEFAULT '',
                                                sign_time              varchar(8) NOT NULL DEFAULT '',
                                                sign_type              int NOT NULL DEFAULT 1,
                                                data_type              int NOT NULL DEFAULT 1,
                                                wx_record_id           varchar(128) NOT NULL DEFAULT '',
                                                oa_sign_id             varchar(64) NOT NULL DEFAULT '',
                                                source                 int NOT NULL DEFAULT 0,
                                                last_time              varchar(19) NULL,
                                                raw_type               varchar(200) NULL,
                                                remark                 varchar(500) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_ATT_LINK_KEY ON dbo.uf_oaones_att_link
            (type, cfgid, dir, oa_user_id, sign_date, sign_time, sign_type, data_type);
        CREATE INDEX IDX_OAONES_ATT_LINK_RECORD ON dbo.uf_oaones_att_link(type, cfgid, wx_record_id);
        CREATE INDEX IDX_OAONES_ATT_LINK_SOURCE ON dbo.uf_oaones_att_link(type, cfgid, source, dir);
        PRINT 'created uf_oaones_att_link';
    END
ELSE
    PRINT 'uf_oaones_att_link already exists';
GO
