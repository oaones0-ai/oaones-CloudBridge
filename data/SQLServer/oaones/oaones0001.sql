IF OBJECT_ID('dbo.uf_oaones_qywx_config','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_qywx_config (
                                                   id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                   corp_name              varchar(200) NULL,
                                                   corp_id                varchar(128) NULL,
                                                   corp_secret            varchar(256) NULL,
                                                   agent_id               varchar(64)  NULL,
                                                   root_dept_id           int NULL DEFAULT 1,
                                                   root_dept_key          varchar(128) NULL,
                                                   qy                     int NULL DEFAULT 1,
                                                   type                   int NOT NULL DEFAULT 0,
                                                   msg_enable             int NOT NULL DEFAULT 1,
                                                   remark                 varchar(500) NULL,
                                                   maintainer_ids         nvarchar(max) NULL,
                                                   modedatacreater        int NULL,
                                                   modedatacreatedate     varchar(10) NULL,
                                                   modedatacreatetime     varchar(8) NULL,
                                                   modedatamodifier       int NULL,
                                                   modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_QYWX_CONFIG_CORP ON dbo.uf_oaones_qywx_config(type, corp_id);
        CREATE INDEX IDX_OAONES_QYWX_CONFIG_TYPE ON dbo.uf_oaones_qywx_config(type, qy);
        PRINT 'created uf_oaones_qywx_config';
    END
ELSE
    PRINT 'uf_oaones_qywx_config already exists';


IF OBJECT_ID('dbo.uf_oaones_dept_bind','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_dept_bind (
                                                 id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                 cfg_id                 int NULL,
                                                 oa_dept_id             int NULL,
                                                 wx_dept_id             int NULL,
                                                 wx_dept_key            varchar(128) NULL,
                                                 wx_parent_dept_id      int NULL,
                                                 wx_parent_dept_key     varchar(128) NULL,
                                                 yx                     int NULL DEFAULT 1,
                                                 remark                 varchar(500) NULL,
                                                 modedatacreater        int NULL,
                                                 modedatacreatedate     varchar(10) NULL,
                                                 modedatacreatetime     varchar(8) NULL,
                                                 modedatamodifier       int NULL,
                                                 modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_DEPT_BIND_OA ON dbo.uf_oaones_dept_bind(cfg_id, oa_dept_id);
        CREATE INDEX IDX_OAONES_DEPT_BIND_WX ON dbo.uf_oaones_dept_bind(cfg_id, wx_dept_id);
        CREATE INDEX IDX_OAONES_DEPT_BIND_KEY ON dbo.uf_oaones_dept_bind(cfg_id, wx_dept_key);
        PRINT 'created uf_oaones_dept_bind';
    END
ELSE
    PRINT 'uf_oaones_dept_bind already exists';


IF OBJECT_ID('dbo.uf_oaones_user_bind','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_user_bind (
                                                 id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                 remark                 varchar(500) NULL,
                                                 cfg_id                 int NULL,
                                                 oa_user_id             int NULL,
                                                 x_user_id              varchar(128) NULL,
                                                 wx_dept_id             int NULL,
                                                 bind_state             int NULL DEFAULT 1,
                                                 bind_source            int NOT NULL CONSTRAINT DF_UF_OAONES_USER_BIND_SOURCE DEFAULT 1,
                                                 bind_time              varchar(19) NULL,
                                                 last_sync_direction    int NULL,
                                                 last_sync_time         varchar(19) NULL,
                                                 modedatacreater        int NULL,
                                                 modedatacreatedate     varchar(10) NULL,
                                                 modedatacreatetime     varchar(8) NULL,
                                                 modedatamodifier       int NULL,
                                                 modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_USER_BIND_OA ON dbo.uf_oaones_user_bind(cfg_id, oa_user_id);
        CREATE INDEX IDX_OAONES_USER_BIND_WX ON dbo.uf_oaones_user_bind(cfg_id, x_user_id);
        PRINT 'created uf_oaones_user_bind';
    END
ELSE
    PRINT 'uf_oaones_user_bind already exists';


IF OBJECT_ID('dbo.uf_oaones_sync_log','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_sync_log (
                                                id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                cfgid                  int NULL,
                                                corpname               varchar(100) NULL,
                                                direction              int NULL,
                                                totalcount             int NOT NULL DEFAULT 0,
                                                successcount           int NOT NULL DEFAULT 0,
                                                failcount              int NOT NULL DEFAULT 0,
                                                skipcount              int NOT NULL DEFAULT 0,
                                                deptcount              int NOT NULL DEFAULT 0,
                                                usercount              int NOT NULL DEFAULT 0,
                                                nodeptcount            int NOT NULL DEFAULT 0,
                                                nodeptdept             varchar(255) NULL,
                                                starttime              varchar(19) NULL,
                                                endtime                varchar(19) NULL,
                                                runstatus              int NULL,
                                                detail                 nvarchar(max) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_SYNC_LOG_CFG ON dbo.uf_oaones_sync_log(cfgid, id);
        PRINT 'created uf_oaones_sync_log';
    END
ELSE
    PRINT 'uf_oaones_sync_log already exists';


IF OBJECT_ID('dbo.uf_oaones_sync_log_item','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_sync_log_item (
                                                     id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                     log_id                 int NULL,
                                                     cfgid                  int NULL,
                                                     direction              int NULL,
                                                     item_type              int NULL,
                                                     op                     int NULL,
                                                     oa_id                  varchar(64) NULL,
                                                     oa_name                varchar(255) NULL,
                                                     wx_id                  varchar(128) NULL,
                                                     wx_name                varchar(255) NULL,
                                                     parent_id              varchar(128) NULL,
                                                     parent_name            varchar(255) NULL,
                                                     seq                    int NULL,
                                                     message                varchar(1000) NULL,
                                                     formmodeid             int NULL,
                                                     modedatacreater        int NULL,
                                                     modedatacreatedate     varchar(10) NULL,
                                                     modedatacreatetime     varchar(8) NULL,
                                                     modedatamodifier       int NULL,
                                                     modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_SYNC_LOG_ITEM_LOG ON dbo.uf_oaones_sync_log_item(log_id, id);
        CREATE INDEX IDX_OAONES_SYNC_LOG_ITEM_CFG ON dbo.uf_oaones_sync_log_item(cfgid, item_type);
        PRINT 'created uf_oaones_sync_log_item';
    END
ELSE
    PRINT 'uf_oaones_sync_log_item already exists';


IF OBJECT_ID('dbo.uf_oaones_dep_tmp','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_dep_tmp (
                                               id             int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                               corpid         varchar(128) NOT NULL,
                                               wx_dept_id     int           NOT NULL,
                                               wx_dept_key    varchar(128) NULL,
                                               dept_name      varchar(200) NULL,
                                               parent_id      int           NULL,
                                               parent_key     varchar(128) NULL,
                                               status         int           NOT NULL DEFAULT 0,
                                               no_bind        int           NULL,
                                               type           int           NOT NULL DEFAULT 0,
                                               last_sync_time varchar(19)   NULL,
                                               remark         varchar(200) NULL,
                                               modedatacreater        int NULL,
                                               modedatacreatedate     varchar(10) NULL,
                                               modedatacreatetime     varchar(8) NULL,
                                               modedatamodifier       int NULL,
                                               modedatamodifydatetime varchar(100) NULL,
                                               CONSTRAINT UK_OAONES_DEP_TMP_KEY UNIQUE (corpid, wx_dept_id, wx_dept_key)
        );
        CREATE INDEX IDX_OAONES_DEP_TMP_STATUS ON dbo.uf_oaones_dep_tmp(corpid, type, status);
        CREATE INDEX IDX_OAONES_DEP_TMP_KEY ON dbo.uf_oaones_dep_tmp(corpid, type, wx_dept_key);
        PRINT 'created uf_oaones_dep_tmp';
    END
ELSE
    PRINT 'uf_oaones_dep_tmp already exists';


IF OBJECT_ID('dbo.uf_oaones_user_rule','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_user_rule (
                                                 id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                 cfg_id                 int NULL,
                                                 rules                  varchar(100) NOT NULL DEFAULT '',
                                                 remark                 varchar(500) NULL,
                                                 modedatacreater        int NULL,
                                                 modedatacreatedate     varchar(10) NULL,
                                                 modedatacreatetime     varchar(8) NULL,
                                                 modedatamodifier       int NULL,
                                                 modedatamodifydatetime varchar(100) NULL
        );
        PRINT 'created uf_oaones_user_rule';
    END
ELSE
    PRINT 'uf_oaones_user_rule already exists';


IF OBJECT_ID('dbo.uf_oaones_user_tmp','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_user_tmp (
                                                id                int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                corpid            varchar(128) NOT NULL,
                                                wx_user_id        varchar(128) NOT NULL,
                                                wx_name           varchar(200) NULL,
                                                wx_dept_id        int           NULL,
                                                wx_dept_key       varchar(128)  NULL,
                                                wx_position       varchar(200) NULL,
                                                wx_mobile         varchar(64)  NULL,
                                                wx_email          varchar(200) NULL,
                                                wx_biz_mail       varchar(200) NULL,
                                                wx_gender         int           NULL,
                                                wx_avatar         varchar(600) NULL,
                                                wx_telephone      varchar(64)  NULL,
                                                wx_status         int           NULL,
                                                status            int           NOT NULL DEFAULT 0,
                                                type              int           NOT NULL DEFAULT 0,
                                                no_bind           int           NULL,
                                                last_sync_time    varchar(19)   NULL,
                                                remark            varchar(200) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL,
                                                CONSTRAINT UK_OAONES_USER_TMP_KEY UNIQUE (corpid, wx_user_id)
        );
        CREATE INDEX IDX_OAONES_USER_TMP_STATUS ON dbo.uf_oaones_user_tmp(corpid, type, status);
        CREATE INDEX IDX_OAONES_USER_TMP_DEPT_KEY ON dbo.uf_oaones_user_tmp(corpid, type, wx_dept_key);
        PRINT 'created uf_oaones_user_tmp';
    END
ELSE
    PRINT 'uf_oaones_user_tmp already exists';





IF OBJECT_ID('dbo.uf_oaones_sub_bind','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_sub_bind (
                                                id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                cfg_id                 int NULL,
                                                oa_sub_id              int NULL,
                                                wx_dept_id             int NULL,
                                                wx_dept_key            varchar(128) NULL,
                                                yx                     int NULL DEFAULT 0,
                                                type                   int NOT NULL DEFAULT 0,
                                                remark                 varchar(300) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_SUB_BIND_OA ON dbo.uf_oaones_sub_bind(cfg_id, oa_sub_id);
        CREATE UNIQUE INDEX UK_OAONES_SUB_BIND_SUB ON dbo.uf_oaones_sub_bind(type, oa_sub_id);
        CREATE UNIQUE INDEX UK_OAONES_SUB_BIND_CFG ON dbo.uf_oaones_sub_bind(cfg_id);
        PRINT 'created uf_oaones_sub_bind';
    END
ELSE
    PRINT 'uf_oaones_sub_bind already exists';

IF OBJECT_ID('dbo.uf_oaones_app_bind','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_app_bind (
                                                id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                cfg_id                 int NULL,
                                                agent_id               varchar(64)  NULL,
                                                app_secret             varchar(256) NULL,
                                                app_name               varchar(200) NULL,
                                                oa_page_url            varchar(500) NOT NULL,
                                                oa_page_url_mobile     varchar(500) NULL,
                                                yx                     int NULL DEFAULT 0,
                                                remark                 varchar(500) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_APP_BIND_AGENT ON dbo.uf_oaones_app_bind(cfg_id, agent_id);
        CREATE INDEX IDX_OAONES_APP_BIND_CFG ON dbo.uf_oaones_app_bind(cfg_id);
        PRINT 'created uf_oaones_app_bind';
    END
ELSE
    PRINT 'uf_oaones_app_bind already exists';


IF OBJECT_ID('dbo.uf_oaones_app','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_app (
                                           id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                           app_name               varchar(200) NOT NULL,
                                           app_url                varchar(500) NOT NULL,
                                           sso_class              varchar(300) NULL,
                                           sso_method             varchar(200) NULL,
                                           icon                   varchar(500) NULL,
                                           icon_seed              varchar(200) NULL,
                                           open_mode              int NULL DEFAULT 0,
                                           maintainer_ids         varchar(2000) NULL,
                                           vis_all                int NULL DEFAULT 0,
                                           vis_sub_ids            varchar(2000) NULL,
                                           vis_dept_ids           varchar(2000) NULL,
                                           vis_user_ids           varchar(2000) NULL,
                                           sort_no                int NULL DEFAULT 0,
                                           qy                     int NULL DEFAULT 1,
                                           remark                 varchar(500) NULL,
                                           formmodeid             int NULL,
                                           modedatacreater        int NULL,
                                           modedatacreatertype    int NULL,
                                           modedatacreatedate     varchar(10) NULL,
                                           modedatacreatetime     varchar(8) NULL,
                                           modedatamodifier       int NULL,
                                           modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_APP_NAME ON dbo.uf_oaones_app(app_name);
        CREATE INDEX IDX_OAONES_APP_SORT ON dbo.uf_oaones_app(sort_no);
        PRINT 'created uf_oaones_app';
    END
ELSE
    PRINT 'uf_oaones_app already exists';


IF COL_LENGTH('dbo.uf_oaones_app','icon_seed') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD icon_seed varchar(200) NULL;
        PRINT 'added uf_oaones_app.icon_seed';
    END
ELSE
    PRINT 'uf_oaones_app.icon_seed already exists';


UPDATE dbo.uf_oaones_app
SET icon_seed = app_name
WHERE icon_seed IS NULL
  AND (icon IS NULL OR icon = '');


IF COL_LENGTH('dbo.uf_oaones_app','open_mode') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD open_mode int NULL DEFAULT 0;
        PRINT 'added uf_oaones_app.open_mode';
    END
ELSE
    PRINT 'uf_oaones_app.open_mode already exists';


IF COL_LENGTH('dbo.uf_oaones_app','maintainer_ids') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD maintainer_ids varchar(2000) NULL;
        PRINT 'added uf_oaones_app.maintainer_ids';
    END
ELSE
    PRINT 'uf_oaones_app.maintainer_ids already exists';


IF COL_LENGTH('dbo.uf_oaones_app','vis_sub_ids') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD vis_sub_ids varchar(2000) NULL;
        PRINT 'added uf_oaones_app.vis_sub_ids';
    END
ELSE
    PRINT 'uf_oaones_app.vis_sub_ids already exists';


IF COL_LENGTH('dbo.uf_oaones_app','vis_dept_ids') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD vis_dept_ids varchar(2000) NULL;
        PRINT 'added uf_oaones_app.vis_dept_ids';
    END
ELSE
    PRINT 'uf_oaones_app.vis_dept_ids already exists';


IF COL_LENGTH('dbo.uf_oaones_app','vis_user_ids') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD vis_user_ids varchar(2000) NULL;
        PRINT 'added uf_oaones_app.vis_user_ids';
    END
ELSE
    PRINT 'uf_oaones_app.vis_user_ids already exists';


IF COL_LENGTH('dbo.uf_oaones_app','vis_all') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_app ADD vis_all int NULL DEFAULT 0;
        PRINT 'added uf_oaones_app.vis_all';
    END
ELSE
    PRINT 'uf_oaones_app.vis_all already exists';


IF OBJECT_ID('dbo.uf_oaones_scan_login','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_scan_login (
                                                  id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                  ticket                 varchar(64)  NOT NULL,
                                                  browser_key            varchar(64)  NULL,
                                                  qr_text                varchar(500) NULL,
                                                  platform               varchar(16)  NULL,
                                                  cfg_id                 int NULL,
                                                  out_user_id            varchar(128) NULL,
                                                  out_user_name          varchar(128) NULL,
                                                  oa_user_id             int NULL,
                                                  login_token            varchar(64)  NULL,
                                                  state                  int NULL DEFAULT 0,
                                                  msg                    varchar(999) NULL,
                                                  landing_url            varchar(500) NULL,
                                                  expire_time            varchar(100) NULL,
                                                  confirm_time           varchar(100) NULL,
                                                  used_time              varchar(100) NULL,
                                                  create_ip              varchar(64) NULL,
                                                  create_ua              varchar(500) NULL,
                                                  scan_ip                varchar(64) NULL,
                                                  scan_ua                varchar(500) NULL,
                                                  modedatacreater        int NULL,
                                                  modedatacreatedate     varchar(10) NULL,
                                                  modedatacreatetime     varchar(8) NULL,
                                                  modedatamodifier       int NULL,
                                                  modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_SCAN_LOGIN_TICKET ON dbo.uf_oaones_scan_login(ticket);
        CREATE INDEX IDX_OAONES_SCAN_LOGIN_EXPIRE ON dbo.uf_oaones_scan_login(expire_time);
        PRINT 'created uf_oaones_scan_login';
    END
ELSE
    PRINT 'uf_oaones_scan_login already exists';


IF OBJECT_ID('dbo.uf_oaones_login_config','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_login_config (
                                                    id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                    password_enable        int NULL DEFAULT 1,
                                                    mobile_enable          int NULL DEFAULT 0,
                                                    email_enable           int NULL DEFAULT 0,
                                                    wecom_qr_enable        int NULL DEFAULT 1,
                                                    dingtalk_qr_enable     int NULL DEFAULT 0,
                                                    feishu_qr_enable       int NULL DEFAULT 0,
                                                    personal_wx_enable     int NULL DEFAULT 0,
                                                    qr_ttl_seconds         int NULL,
                                                    remark                 varchar(500) NULL,
                                                    modedatacreater        int NULL,
                                                    modedatacreatedate     varchar(10) NULL,
                                                    modedatacreatetime     varchar(8) NULL,
                                                    modedatamodifier       int NULL,
                                                    modedatamodifydatetime varchar(100) NULL
        );
        PRINT 'created uf_oaones_login_config';
    END
ELSE
    PRINT 'uf_oaones_login_config already exists';


IF OBJECT_ID('dbo.uf_oaones_login_sms','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_login_sms (
                                                 id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                 mobile                 varchar(32)  NULL,
                                                 email                  varchar(128) NULL,
                                                 purpose                varchar(32)  NULL,
                                                 code_salt              varchar(32)  NULL,
                                                 code_hash              varchar(128) NULL,
                                                 state                  int NULL DEFAULT 0,
                                                 expire_time            bigint NULL,
                                                 wrong_times            int NULL DEFAULT 0,
                                                 send_time              bigint NULL,
                                                 send_ip                varchar(64) NULL,
                                                 send_ua                varchar(500) NULL,
                                                 verify_time            bigint NULL,
                                                 verify_ip              varchar(64) NULL,
                                                 fail_reason            varchar(500) NULL,
                                                 modedatacreater        int NULL,
                                                 modedatacreatedate     varchar(10) NULL,
                                                 modedatacreatetime     varchar(8) NULL,
                                                 modedatamodifier       int NULL,
                                                 modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_LOGIN_SMS_MOBILE ON dbo.uf_oaones_login_sms(mobile, send_time);
        CREATE INDEX IDX_OAONES_LOGIN_SMS_EMAIL ON dbo.uf_oaones_login_sms(email, send_time);
        CREATE INDEX IDX_OAONES_LOGIN_SMS_EXPIRE ON dbo.uf_oaones_login_sms(expire_time);
        PRINT 'created uf_oaones_login_sms';
    END
ELSE
    PRINT 'uf_oaones_login_sms already exists';


IF OBJECT_ID('dbo.uf_oaones_personal_wx_config','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_personal_wx_config (
                                                          id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                          app_id                 varchar(64)  NULL,
                                                          app_secret             varchar(128) NULL,
                                                          remark                 varchar(500) NULL,
                                                          modedatacreater        int NULL,
                                                          modedatacreatedate     varchar(10) NULL,
                                                          modedatacreatetime     varchar(8) NULL,
                                                          modedatamodifier       int NULL,
                                                          modedatamodifydatetime varchar(100) NULL
        );
        PRINT 'created uf_oaones_personal_wx_config';
    END
ELSE
    PRINT 'uf_oaones_personal_wx_config already exists';


IF OBJECT_ID('dbo.uf_oaones_personal_wx_bind','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_personal_wx_bind (
                                                        id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                        oa_user_id             int          NOT NULL,
                                                        openid                 varchar(128) NULL,
                                                        unionid                varchar(128) NULL,
                                                        nickname               varchar(200) NULL,
                                                        headimgurl             varchar(500) NULL,
                                                        bind_source            int NULL DEFAULT 2,
                                                        bind_time              varchar(100) NULL,
                                                        remark                 varchar(500) NULL,
                                                        modedatacreater        int NULL,
                                                        modedatacreatedate     varchar(10) NULL,
                                                        modedatacreatetime     varchar(8) NULL,
                                                        modedatamodifier       int NULL,
                                                        modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_PW_BIND_OA_USER ON dbo.uf_oaones_personal_wx_bind(oa_user_id);
        CREATE UNIQUE INDEX UK_OAONES_PW_BIND_OPENID ON dbo.uf_oaones_personal_wx_bind(openid);
        PRINT 'created uf_oaones_personal_wx_bind';
    END
ELSE
    PRINT 'uf_oaones_personal_wx_bind already exists';


IF OBJECT_ID('dbo.uf_oaones_msg_config','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_msg_config (
                                                  id                  int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                  push_enable         int NULL DEFAULT 1,
                                                  default_app_id      int NULL DEFAULT 0,
                                                  retry_times         int NULL DEFAULT 3,
                                                  retry_interval      int NULL DEFAULT 60,
                                                  shard_size          int NULL DEFAULT 1000,
                                                  timeout_ms          int NULL DEFAULT 30000,
                                                  token_advance_sec   int NULL DEFAULT 300,
                                                  dedup_window_sec    int NULL DEFAULT 600,
                                                  log_keep_days       int NULL DEFAULT 90,
                                                  task_keep_days      int NULL DEFAULT 30,
                                                  remark              varchar(500) NULL,
                                                  modedatacreater     int NULL,
                                                  modedatacreatedate  varchar(10) NULL,
                                                  modedatacreatetime  varchar(8) NULL,
                                                  modedatamodifier    int NULL,
                                                  modedatamodifydatetime varchar(100) NULL
        );
        PRINT 'created uf_oaones_msg_config';
    END
ELSE
    PRINT 'uf_oaones_msg_config already exists';


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_msg_type (
                                                id                  int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                type                int NOT NULL DEFAULT 0,
                                                std_config_id       varchar(64) NOT NULL DEFAULT '0',
                                                type_name           varchar(200) NOT NULL,
                                                std_type            int NOT NULL DEFAULT 0,
                                                wx_msgtype          varchar(32) NULL,
                                                card_action         varchar(16) NULL,
                                                title_tpl           varchar(500) NULL,
                                                context_tpl         nvarchar(max) NULL,
                                                url_tpl             varchar(500) NULL,
                                                mobile_url_tpl      varchar(500) NULL,
                                                cover_url           varchar(500) NULL,
                                                btn_json            nvarchar(max) NULL,
                                                app_id              int NULL DEFAULT 0,
                                                yx                  int NULL DEFAULT 0,
                                                sort_no             decimal(38,1) NULL DEFAULT 0,
                                                remark              varchar(500) NULL,
                                                modedatacreater     int NULL,
                                                modedatacreatedate  varchar(10) NULL,
                                                modedatacreatetime  varchar(8) NULL,
                                                modedatamodifier    int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_MSG_TYPE_KEY ON dbo.uf_oaones_msg_type(std_config_id, std_type, type);
        PRINT 'created uf_oaones_msg_type';
    END
ELSE
    PRINT 'uf_oaones_msg_type already exists';


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND COL_LENGTH('dbo.uf_oaones_msg_type','std_config_id') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_msg_type ADD std_config_id varchar(64) NOT NULL DEFAULT '0';
        PRINT 'altered uf_oaones_msg_type add std_config_id';
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'uf_oaones_msg_type' AND COLUMN_NAME = 'std_config_id'
                  AND DATA_TYPE IN ('int', 'bigint', 'smallint', 'tinyint', 'numeric', 'decimal'))
    BEGIN
        DECLARE @oaones_mt_df sysname;
        SELECT @oaones_mt_df = dc.name
        FROM sys.default_constraints dc
                 JOIN sys.columns c
                      ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
        WHERE dc.parent_object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
          AND c.name = 'std_config_id';
        IF @oaones_mt_df IS NOT NULL
            EXEC('ALTER TABLE dbo.uf_oaones_msg_type DROP CONSTRAINT [' + @oaones_mt_df + ']');

        IF EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID('dbo.uf_oaones_msg_type') AND name = 'UK_OAONES_MSG_TYPE_KEY')
            DROP INDEX UK_OAONES_MSG_TYPE_KEY ON dbo.uf_oaones_msg_type;
        IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UK_OAONES_MSG_TYPE_KEY')
        ALTER TABLE dbo.uf_oaones_msg_type DROP CONSTRAINT UK_OAONES_MSG_TYPE_KEY;

        ALTER TABLE dbo.uf_oaones_msg_type ALTER COLUMN std_config_id varchar(64) NOT NULL;
        IF NOT EXISTS (SELECT 1 FROM sys.default_constraints
                       WHERE parent_object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
                         AND col_name(parent_object_id, parent_column_id) = 'std_config_id')
        ALTER TABLE dbo.uf_oaones_msg_type
            ADD CONSTRAINT DF_UF_OAONES_MSG_TYPE_STD_CONFIG_ID DEFAULT '0' FOR std_config_id;
        PRINT 'altered uf_oaones_msg_type.std_config_id to varchar(64)';
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND COL_LENGTH('dbo.uf_oaones_msg_type','std_config_id') IS NOT NULL
    BEGIN
        UPDATE dbo.uf_oaones_msg_type SET std_config_id = '0'
        WHERE std_config_id IS NULL OR std_config_id = '';
        PRINT 'normalized uf_oaones_msg_type.std_config_id';
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
                  AND name = 'UK_OAONES_MSG_TYPE_STD')
    BEGIN
        DROP INDEX UK_OAONES_MSG_TYPE_STD ON dbo.uf_oaones_msg_type;
        PRINT 'dropped old unique index UK_OAONES_MSG_TYPE_STD';
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
                  AND name = 'UK_OAONES_MSG_TYPE_STD_TYPE')
    BEGIN
        DROP INDEX UK_OAONES_MSG_TYPE_STD_TYPE ON dbo.uf_oaones_msg_type;
        PRINT 'dropped old unique index UK_OAONES_MSG_TYPE_STD_TYPE';
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM sys.indexes
                    WHERE object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
                      AND name = 'UK_OAONES_MSG_TYPE_KEY')
    AND NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_msg_type
                    GROUP BY std_config_id, std_type, type
                    HAVING COUNT(1) > 1)
    BEGIN
        CREATE UNIQUE INDEX UK_OAONES_MSG_TYPE_KEY
            ON dbo.uf_oaones_msg_type(std_config_id, std_type, type);
        PRINT 'created unique index UK_OAONES_MSG_TYPE_KEY';
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM sys.indexes
                    WHERE object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
                      AND name = 'UK_OAONES_MSG_TYPE_KEY')
    AND EXISTS (SELECT 1 FROM dbo.uf_oaones_msg_type
                GROUP BY std_config_id, std_type, type
                HAVING COUNT(1) > 1)
    BEGIN
        PRINT '存在重复的 (std_config_id, std_type, type)：请先清理重复数据再重跑本段；本脚本不删任何数据。';
    END


DECLARE @oaones_mt_rows int;
IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND COL_LENGTH('dbo.uf_oaones_msg_type','std_type') IS NOT NULL
    BEGIN
        UPDATE dbo.uf_oaones_msg_type SET std_type = 0 WHERE std_type IS NULL;
        SET @oaones_mt_rows = @@ROWCOUNT;
        IF @oaones_mt_rows > 0
            PRINT 'normalized uf_oaones_msg_type.std_type (NULL -> 0) rows: ' + CAST(@oaones_mt_rows AS varchar(20));
        IF EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
                     AND name = 'std_type' AND is_nullable = 1)
        ALTER TABLE dbo.uf_oaones_msg_type ALTER COLUMN std_type int NOT NULL;
        IF NOT EXISTS (SELECT 1 FROM sys.default_constraints
                       WHERE parent_object_id = OBJECT_ID('dbo.uf_oaones_msg_type')
                         AND col_name(parent_object_id, parent_column_id) = 'std_type')
        ALTER TABLE dbo.uf_oaones_msg_type
            ADD CONSTRAINT DF_UF_OAONES_MSG_TYPE_STD_TYPE DEFAULT 0 FOR std_type;
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type','U') IS NOT NULL
    AND OBJECT_ID('dbo.ecology_biz_mobile_config','U') IS NOT NULL
    AND OBJECT_ID('dbo.ecology_message_type','U') IS NOT NULL
    BEGIN
        UPDATE m
        SET m.std_config_id = ISNULL((
                                         SELECT MIN(c.id)
                                         FROM dbo.ecology_biz_mobile_config c
                                                  JOIN dbo.ecology_message_type s
                                                       ON c.messagetypeid LIKE '%,' + CAST(s.id AS varchar(20)) + ',%'
                                                           OR c.messagetypeid = CAST(s.id AS varchar(20))
                                         WHERE (c.isshow = 'y' OR c.isshow IS NULL)
                                           AND CAST(s.typecode AS varchar(60)) = CAST(m.std_type AS varchar(60))), '0')
        FROM dbo.uf_oaones_msg_type m
        WHERE m.std_config_id = '0' AND m.std_type > 0;
        PRINT 'backfilled uf_oaones_msg_type.std_config_id by standard message type';
    END


IF OBJECT_ID('dbo.uf_oaones_msg_type_app','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_msg_type_app (
                                                    id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                    type                   int NOT NULL DEFAULT 0,
                                                    std_config_id          varchar(64) NOT NULL DEFAULT '0',
                                                    std_type               int NOT NULL DEFAULT 0,
                                                    cfg_id                 int NOT NULL DEFAULT 0,
                                                    app_id                 int NOT NULL DEFAULT 0,
                                                    modedatacreater        int NULL,
                                                    modedatacreatedate     varchar(10) NULL,
                                                    modedatacreatetime     varchar(8) NULL,
                                                    modedatamodifier       int NULL,
                                                    modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_MSG_TYPE_APP_KEY ON dbo.uf_oaones_msg_type_app(type, std_config_id, std_type, cfg_id);
        CREATE INDEX IDX_OAONES_MSG_TYPE_APP_APP ON dbo.uf_oaones_msg_type_app(app_id);
        PRINT 'created uf_oaones_msg_type_app';
    END
ELSE
    PRINT 'uf_oaones_msg_type_app already exists';


IF OBJECT_ID('dbo.uf_oaones_msg_log','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_msg_log (
                                               id                  int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                               type                int NOT NULL DEFAULT 0,
                                               biz_type            varchar(32)  NULL,
                                               biz_id              varchar(128) NULL,
                                               biz_title           varchar(500) NULL,
                                               std_type            int NULL,
                                               type_name           varchar(200) NULL,
                                               wx_msgtype          varchar(32)  NULL,
                                               card_action         varchar(16)  NULL,
                                               app_id              int NULL,
                                               corp_id             varchar(128) NULL,
                                               agent_id            varchar(64)  NULL,
                                               task_id             varchar(128) NULL,
                                               dedup_key           varchar(250) NULL,
                                               receiver_oa         varchar(500) NULL,
                                               receiver_wx         nvarchar(max) NULL,
                                               receiver_count      int NULL,
                                               shard_index         int NULL DEFAULT 1,
                                               shard_total         int NULL DEFAULT 1,
                                               status              int NULL DEFAULT 0,
                                               retry_count         int NULL DEFAULT 0,
                                               next_retry_time     varchar(19) NULL,
                                               api_log_id          int NULL,
                                               reason              nvarchar(1000) NULL,
                                               src_json            nvarchar(max) NULL,
                                               operator_id         int NULL,
                                               formmodeid          int NULL,
                                               modedatacreater         int NULL,
                                               modedatacreatedate      varchar(10) NULL,
                                               modedatacreatetime      varchar(8)  NULL,
                                               modedatamodifier        int NULL,
                                               modedatamodifydatetime  varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_MSG_LOG_BIZ ON dbo.uf_oaones_msg_log(biz_type, biz_id);
        CREATE INDEX IDX_OAONES_MSG_LOG_STATUS ON dbo.uf_oaones_msg_log(status, retry_count);
        CREATE INDEX IDX_OAONES_MSG_LOG_TIME ON dbo.uf_oaones_msg_log(modedatacreatedate, modedatacreatetime);
        CREATE INDEX IDX_OAONES_MSG_LOG_DEDUP ON dbo.uf_oaones_msg_log(dedup_key, modedatacreatedate, modedatacreatetime);
        CREATE INDEX IDX_OAONES_MSG_LOG_API ON dbo.uf_oaones_msg_log(api_log_id);
        CREATE INDEX IDX_OAONES_MSG_LOG_TYPE ON dbo.uf_oaones_msg_log(type, modedatacreatedate, status);
        PRINT 'created uf_oaones_msg_log';
    END
ELSE
    PRINT 'uf_oaones_msg_log already exists';


IF OBJECT_ID('dbo.uf_oaones_msg_task','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_msg_task (
                                                id                  int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                type                int NOT NULL DEFAULT 0,
                                                task_id             varchar(128) NOT NULL,
                                                biz_type            varchar(32)  NULL,
                                                biz_id              varchar(128) NULL,
                                                biz_title           varchar(500) NULL,
                                                app_id              int NULL,
                                                corp_id             varchar(128) NULL,
                                                agent_id            varchar(64)  NULL,
                                                oa_user_id          int NULL,
                                                wx_userid           varchar(128) NULL,
                                                task_state          varchar(16)  NULL,
                                                log_id              varchar(50) NULL,
                                                expire_time         varchar(19) NULL,
                                                modedatacreater         int NULL,
                                                modedatacreatedate      varchar(10) NULL,
                                                modedatacreatetime      varchar(8)  NULL,
                                                modedatamodifier        int NULL,
                                                modedatamodifydatetime  varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_MSG_TASK ON dbo.uf_oaones_msg_task(task_id);
        CREATE INDEX IDX_OAONES_MSG_TASK_BIZ ON dbo.uf_oaones_msg_task(biz_type, biz_id, wx_userid);
        CREATE INDEX IDX_OAONES_MSG_TASK_TYPE ON dbo.uf_oaones_msg_task(type, biz_type, biz_id, wx_userid, task_state);
        PRINT 'created uf_oaones_msg_task';
    END
ELSE
    PRINT 'uf_oaones_msg_task already exists';


IF OBJECT_ID('dbo.uf_oaones_sync_plan','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_sync_plan (
                                                 id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                 type                   int NOT NULL DEFAULT 0,
                                                 cfgid                  int NULL,
                                                 direction              int NOT NULL,
                                                 enabled                int NOT NULL DEFAULT 1,
                                                 plan_mode              int NULL DEFAULT 1,
                                                 interval_min           int NULL DEFAULT 30,
                                                 cron                   varchar(100) NULL,
                                                 nodept_policy          int NULL,
                                                 default_dept_id        varchar(64) NULL,
                                                 default_dept_name      varchar(255) NULL,
                                                 scope_set              int NULL DEFAULT 0,
                                                 oa_sub_ids             varchar(500) NULL,
                                                 oa_dept_ids            varchar(500) NULL,
                                                 wx_dept_id             varchar(64) NULL,
                                                 scope_text             varchar(500) NULL,
                                                 bind_forced            int NULL DEFAULT 0,
                                                 next_run_time          varchar(19) NULL,
                                                 last_run_time          varchar(19) NULL,
                                                 last_log_id            int NULL,
                                                 last_result            varchar(500) NULL,
                                                 remark                 varchar(200) NULL,
                                                 modedatacreater        int NULL,
                                                 modedatacreatedate     varchar(10) NULL,
                                                 modedatacreatetime     varchar(8) NULL,
                                                 modedatamodifier       int NULL,
                                                 modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_SYNC_PLAN_CFG ON dbo.uf_oaones_sync_plan(type, cfgid, direction);
        CREATE INDEX IDX_OAONES_SYNC_PLAN_NEXT ON dbo.uf_oaones_sync_plan(enabled, next_run_time);
        PRINT 'created uf_oaones_sync_plan';
    END
ELSE
    PRINT 'uf_oaones_sync_plan already exists';


IF COL_LENGTH('dbo.uf_oaones_sync_plan','scope_set') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_sync_plan ADD
            scope_set  int NULL DEFAULT 0,
            oa_sub_ids varchar(500) NULL,
            oa_dept_ids varchar(500) NULL,
            wx_dept_id varchar(64) NULL,
            scope_text varchar(500) NULL,
            bind_forced int NULL DEFAULT 0;
        PRINT 'altered uf_oaones_sync_plan (scope_set)';
    END
ELSE
    PRINT 'uf_oaones_sync_plan.scope_set already exists';


IF COL_LENGTH('dbo.uf_oaones_sync_plan','plan_mode') IS NULL
    BEGIN
        ALTER TABLE dbo.uf_oaones_sync_plan ADD
            plan_mode    int NULL DEFAULT 1,
            interval_min int NULL DEFAULT 30;
        PRINT 'altered uf_oaones_sync_plan (plan_mode)';
    END
ELSE
    PRINT 'uf_oaones_sync_plan.plan_mode already exists';


IF COL_LENGTH('dbo.uf_oaones_sync_plan','plan_mode') IS NOT NULL
    BEGIN
        UPDATE dbo.uf_oaones_sync_plan SET plan_mode = 1 WHERE plan_mode IS NULL;
        UPDATE dbo.uf_oaones_sync_plan SET interval_min = 30 WHERE interval_min IS NULL OR interval_min <= 0;
    END


IF OBJECT_ID('dbo.uf_oaones_mtg_sync','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_mtg_sync (
                                                id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                biz                    int NOT NULL DEFAULT 0,
                                                type                   int NOT NULL DEFAULT 0,
                                                cfgid                  int NOT NULL,
                                                enabled                int NOT NULL DEFAULT 1,
                                                dir_oa2third           int NOT NULL DEFAULT 1,
                                                dir_third2oa           int NOT NULL DEFAULT 1,
                                                app_id                 int NOT NULL DEFAULT 0,
                                                remark                 varchar(200) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_MTG_SYNC ON dbo.uf_oaones_mtg_sync(biz, type, cfgid);
        CREATE INDEX IDX_OAONES_MTG_SYNC_ENABLE ON dbo.uf_oaones_mtg_sync(enabled, biz, type);
        PRINT 'created uf_oaones_mtg_sync';
    END
ELSE
    PRINT 'uf_oaones_mtg_sync already exists';


IF OBJECT_ID('dbo.uf_oaones_mtg_sync_log','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_mtg_sync_log (
                                                    id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                    biz                    int NOT NULL DEFAULT 0,
                                                    type                   int NOT NULL DEFAULT 0,
                                                    cfgid                  int NOT NULL,
                                                    batch_id               varchar(64) NULL,
                                                    oa_id                  varchar(64) NULL,
                                                    oa_title               varchar(500) NULL,
                                                    oa_sub_id              varchar(64) NULL,
                                                    oa_sub_name            varchar(255) NULL,
                                                    oa_dept_id             varchar(64) NULL,
                                                    oa_dept_name           varchar(255) NULL,
                                                    begin_time             varchar(19) NULL,
                                                    end_time               varchar(19) NULL,
                                                    wx_id                  varchar(128) NULL,
                                                    op                     int NULL,
                                                    message                varchar(1000) NULL,
                                                    modedatacreater        int NULL,
                                                    modedatacreatedate     varchar(10) NULL,
                                                    modedatacreatetime     varchar(8) NULL,
                                                    modedatamodifier       int NULL,
                                                    modedatamodifydatetime varchar(100) NULL
        );
        CREATE INDEX IDX_OAONES_MTG_SYNC_LOG_CFG ON dbo.uf_oaones_mtg_sync_log(biz, type, cfgid);
        CREATE INDEX IDX_OAONES_MTG_SYNC_LOG_OA ON dbo.uf_oaones_mtg_sync_log(biz, type, cfgid, oa_id);
        CREATE INDEX IDX_OAONES_MTG_SYNC_LOG_TIME ON dbo.uf_oaones_mtg_sync_log(modedatacreatedate, modedatacreatetime);
        PRINT 'created uf_oaones_mtg_sync_log';
    END
ELSE
    PRINT 'uf_oaones_mtg_sync_log already exists';


IF OBJECT_ID('dbo.uf_oaones_mtg_sync_user','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_mtg_sync_user (
                                                     id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                     biz                    int NOT NULL DEFAULT 0,
                                                     type                   int NOT NULL DEFAULT 0,
                                                     cfgid                  int NOT NULL,
                                                     oa_user_id             int NOT NULL,
                                                     enabled                int NOT NULL DEFAULT 1,
                                                     dir_mode               int NOT NULL DEFAULT 0,
                                                     dir_oa2third           int NOT NULL DEFAULT 1,
                                                     dir_third2oa           int NOT NULL DEFAULT 1,
                                                     remark                 varchar(200) NULL,
                                                     modedatacreater        int NULL,
                                                     modedatacreatedate     varchar(10) NULL,
                                                     modedatacreatetime     varchar(8) NULL,
                                                     modedatamodifier       int NULL,
                                                     modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_MTG_SYNC_USER ON dbo.uf_oaones_mtg_sync_user(biz, type, cfgid, oa_user_id);
        CREATE INDEX IDX_OAONES_MTG_SYNC_USER_CFG ON dbo.uf_oaones_mtg_sync_user(type, cfgid, biz);
        PRINT 'created uf_oaones_mtg_sync_user';
    END
ELSE
    PRINT 'uf_oaones_mtg_sync_user already exists';


IF OBJECT_ID('dbo.uf_oaones_mtg_link','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_mtg_link (
                                                id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                biz                    int NOT NULL DEFAULT 0,
                                                type                   int NOT NULL DEFAULT 0,
                                                cfgid                  int NOT NULL,
                                                oa_id                  varchar(64) NOT NULL,
                                                wx_id                  varchar(128) NOT NULL,
                                                source                 int NOT NULL DEFAULT 0,
                                                oa_hash                varchar(64) NULL,
                                                wx_hash                varchar(64) NULL,
                                                last_pull_time         varchar(19) NULL,
                                                remark                 varchar(200) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        CREATE UNIQUE INDEX UK_OAONES_MTG_LINK_WX ON dbo.uf_oaones_mtg_link(biz, type, cfgid, wx_id);
        CREATE UNIQUE INDEX UK_OAONES_MTG_LINK_OA ON dbo.uf_oaones_mtg_link(biz, type, cfgid, oa_id);
        CREATE INDEX IDX_OAONES_MTG_LINK_SRC ON dbo.uf_oaones_mtg_link(biz, type, cfgid, source);
        PRINT 'created uf_oaones_mtg_link';
    END
ELSE
    PRINT 'uf_oaones_mtg_link already exists';


IF OBJECT_ID('dbo.uf_oaones_api_log','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_api_log (
                                               id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                               type                   int NOT NULL DEFAULT 0,
                                               cfg_id                 int NULL DEFAULT 0,
                                               corp_name              nvarchar(200) NULL,
                                               api_type               varchar(32) NULL,
                                               direction              int NULL DEFAULT 0,
                                               method                 varchar(16) NULL,
                                               api_path               nvarchar(500) NULL,
                                               api_url                nvarchar(1000) NULL,
                                               req_body               nvarchar(max) NULL,
                                               resp_body              nvarchar(max) NULL,
                                               http_status            int NULL,
                                               success                int NULL DEFAULT 0,
                                               err_code               varchar(64) NULL,
                                               err_msg                nvarchar(max) NULL,
                                               cost_ms                int NULL,
                                               error_stack            nvarchar(max) NULL,
                                               operator_id            int NULL,
                                               formmodeid             int NULL,
                                               modedatacreatedate     varchar(10) NULL,
                                               modedatacreatetime     varchar(8)  NULL
        );
        CREATE INDEX IDX_OAONES_API_LOG_FILTER ON dbo.uf_oaones_api_log(type, cfg_id, api_type, modedatacreatedate);
        CREATE INDEX IDX_OAONES_API_LOG_TIME ON dbo.uf_oaones_api_log(modedatacreatedate, modedatacreatetime);
        CREATE INDEX IDX_OAONES_API_LOG_RESULT ON dbo.uf_oaones_api_log(success, modedatacreatedate);
        PRINT 'created uf_oaones_api_log';
    END
ELSE
    PRINT 'uf_oaones_api_log already exists';


IF OBJECT_ID('dbo.uf_oaones_op_log','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_op_log (
                                              id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                              user_id                int NULL,
                                              login_name             varchar(100) NULL,
                                              user_name              nvarchar(200) NULL,
                                              dept_name              nvarchar(200) NULL,
                                              op_type                varchar(16) NULL,
                                              menu_id                varchar(64) NULL,
                                              menu_name              nvarchar(200) NULL,
                                              menu_path              nvarchar(500) NULL,
                                              page_url               nvarchar(1000) NULL,
                                              client_ip              varchar(100) NULL,
                                              device                 varchar(16) NULL,
                                              session_id             varchar(100) NULL,
                                              formmodeid             int NULL,
                                              modedatacreatedate     varchar(10) NULL,
                                              modedatacreatetime     varchar(8)  NULL
        );
        CREATE INDEX IDX_OAONES_OP_LOG_USER ON dbo.uf_oaones_op_log(user_id, modedatacreatedate);
        CREATE INDEX IDX_OAONES_OP_LOG_TYPE ON dbo.uf_oaones_op_log(op_type, modedatacreatedate);
        CREATE INDEX IDX_OAONES_OP_LOG_TIME ON dbo.uf_oaones_op_log(modedatacreatedate, modedatacreatetime);
        CREATE INDEX IDX_OAONES_OP_LOG_MENU ON dbo.uf_oaones_op_log(menu_id, modedatacreatedate);
        PRINT 'created uf_oaones_op_log';
    END
ELSE
    PRINT 'uf_oaones_op_log already exists';


IF OBJECT_ID('dbo.uf_oaones_mtg_cron','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_mtg_cron (
                                                id                     int NOT NULL PRIMARY KEY,
                                                plan_mode              int NULL DEFAULT 0,
                                                workday_group_id       int NULL DEFAULT 0,
                                                cron_day_type          int NULL DEFAULT 0,
                                                day_cron               varchar(100) NULL,
                                                night_cron             varchar(100) NULL,
                                                enabled                int NULL DEFAULT 1,
                                                remark                 varchar(500) NULL,
                                                modedatacreater        int NULL,
                                                modedatacreatedate     varchar(10) NULL,
                                                modedatacreatetime     varchar(8) NULL,
                                                modedatamodifier       int NULL,
                                                modedatamodifydatetime varchar(100) NULL
        );
        PRINT 'created uf_oaones_mtg_cron';
    END
ELSE
    PRINT 'uf_oaones_mtg_cron already exists';


IF OBJECT_ID('dbo.uf_oaones_mtg_plan_item','U') IS NULL
    BEGIN
        CREATE TABLE dbo.uf_oaones_mtg_plan_item (
                                                     id                     int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                                                     plan_id                int NOT NULL DEFAULT 1,
                                                     day_type               int NULL DEFAULT 1,
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
        CREATE INDEX IDX_OAONES_MTG_PLAN_ITEM ON dbo.uf_oaones_mtg_plan_item(plan_id, showorder);
        PRINT 'created uf_oaones_mtg_plan_item';
    END
ELSE
    PRINT 'uf_oaones_mtg_plan_item already exists';


PRINT 'OAones public tables created.';


IF OBJECT_ID('dbo.uf_oaones_msg_config','U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_msg_config WHERE id = 1)
    BEGIN
        SET IDENTITY_INSERT dbo.uf_oaones_msg_config ON;
        INSERT INTO dbo.uf_oaones_msg_config
        (id, push_enable, default_app_id, retry_times, retry_interval, shard_size,
         timeout_ms, token_advance_sec, dedup_window_sec, log_keep_days, task_keep_days, remark)
        VALUES
            (1, 1, 0, 3, 60, 1000, 30000, 300, 600, 90, 30, N'默认参数，可在“消息中心-推送配置”页维护');
        SET IDENTITY_INSERT dbo.uf_oaones_msg_config OFF;
        PRINT 'init uf_oaones_msg_config row';
    END


IF OBJECT_ID('dbo.uf_oaones_login_config','U') IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_login_config)
    BEGIN
        INSERT INTO dbo.uf_oaones_login_config
        (password_enable, mobile_enable, wecom_qr_enable, dingtalk_qr_enable, feishu_qr_enable,
         personal_wx_enable, qr_ttl_seconds, modedatacreatedate, modedatacreatetime)
        VALUES (1, 0, 1, 0, 0, 0, 180, CONVERT(varchar(10), GETDATE(), 120), '000000');
        PRINT 'init uf_oaones_login_config row';
    END

IF OBJECT_ID('dbo.ecology_biz_mobile_config','U') IS NOT NULL
    AND OBJECT_ID('dbo.ecology_message_type','U') IS NOT NULL
    BEGIN
        UPDATE m
        SET m.std_config_id = ISNULL((
                                         SELECT MIN(c.id)
                                         FROM dbo.ecology_biz_mobile_config c
                                                  JOIN dbo.ecology_message_type s
                                                       ON c.messagetypeid LIKE '%,' + CAST(s.id AS varchar(20)) + ',%'
                                                           OR c.messagetypeid = CAST(s.id AS varchar(20))
                                         WHERE (c.isshow = 'y' OR c.isshow IS NULL)
                                           AND CAST(s.typecode AS varchar(60)) = CAST(m.std_type AS varchar(60))), '0')
        FROM dbo.uf_oaones_msg_type m
        WHERE m.std_config_id = '0' AND m.std_type > 0;
        PRINT 'backfilled uf_oaones_msg_type.std_config_id by standard message type';
    END


PRINT 'OAones public tables init finished.';


;WITH src AS (
    SELECT t.id,
           t.typecode,
           t.sourcename,
           t.typename,
           CASE WHEN t.typecode NOT LIKE '%[^0-9]%' AND t.typecode <> ''
                    THEN CAST(t.typecode AS int) END AS code_int
    FROM dbo.ECOLOGY_MESSAGE_TYPE t
)
 INSERT INTO dbo.uf_oaones_msg_type
 (type, type_name, std_type, wx_msgtype, card_action,
  title_tpl, context_tpl, url_tpl, mobile_url_tpl, cover_url, btn_json,
  app_id, yx, sort_no, remark)
 SELECT
     1 as type,
     isnull(nullif((select top 1 l.labelname
                    from dbo.HtmlLabelInfo l
                    where l.indexid = t.typename and l.languageid = 7), ''),
            isnull(nullif(case
                              when charindex('~`~`7 ', t.sourcename) > 0
                                  and charindex('`~`8', t.sourcename,
                                                charindex('~`~`7 ', t.sourcename) + 6) > charindex('~`~`7 ', t.sourcename) + 6
                                  then ltrim(rtrim(substring(t.sourcename,
                                                             charindex('~`~`7 ', t.sourcename) + 6,
                                                             charindex('`~`8', t.sourcename,
                                                                       charindex('~`~`7 ', t.sourcename) + 6)
                                                                 - charindex('~`~`7 ', t.sourcename) - 6)))
                              when charindex('~`~`', t.sourcename) = 0 then ltrim(rtrim(t.sourcename))
                              end, ''),
                   N'消息类型 ' + cast(t.typecode as nvarchar(20)))) as type_name,
     t.code_int,
     case when t.code_int in (20, 21, 25, 27, 34, 35, 38, 63)
              then N'task_card' else N'markdown' end as wx_msgtype,
     case when t.code_int in (20, 21, 63) then N'create'
          when t.code_int = 25 then N'done'
          when t.code_int in (27, 34, 35, 38) then N'invalid'
          else N'none' end as card_action,
     case when t.code_int in (20, 21, 63) then N'${targetName}'
          when t.code_int = 25 then N'流程已办结'
          when t.code_int = 27 then N'流程已删除'
          when t.code_int = 34 then N'流程已暂停'
          when t.code_int = 35 then N'流程已撤销'
          when t.code_int = 38 then N'流程已收回'
          else N'${title}' end as title_tpl,
     case when t.code_int = 20 then N'流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。'
          when t.code_int = 21 then N'流程「${targetName}」被退回，请重新处理。'
          when t.code_int = 63 then N'任务「${targetName}」已到达，请及时处理。'
          when t.code_int = 25 then N'流程「${targetName}」已办结。'
          when t.code_int = 27 then N'流程「${targetName}」已删除，待办已失效。'
          when t.code_int = 34 then N'流程「${targetName}」已暂停，待办已失效。'
          when t.code_int = 35 then N'流程「${targetName}」已撤销，待办已失效。'
          when t.code_int = 38 then N'流程「${targetName}」已被收回/撤销，待办已失效。'
          else N'${context}' end as context_tpl,
     NULL, NULL, NULL,
     case when t.code_int in (20, 63) then
              N'[{"key":"agree","name":"同意","replace_name":"已同意","color":"blue","is_bold":true},'
                  + N'{"key":"reject","name":"驳回","replace_name":"已驳回","color":"red","is_bold":false}]'
          when t.code_int = 21 then
              N'[{"key":"agree","name":"处理","replace_name":"已处理","color":"blue","is_bold":true}]'
          else NULL end as btn_json,
     0, 0, t.code_int,
     N'由「补齐全部消息类型」按默认口径初始化，可在本页调整'
 FROM src t
 WHERE t.code_int > 0
   AND NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_msg_type m WHERE m.std_type = t.code_int and m.type = 1);

PRINT '补齐 uf_oaones_msg_type：本次新增 ' + cast(@@ROWCOUNT as varchar(20)) + ' 行';


;WITH src AS (
    SELECT t.id,
           t.typecode,
           t.sourcename,
           t.typename,
           CASE WHEN t.typecode NOT LIKE '%[^0-9]%' AND t.typecode <> ''
                    THEN CAST(t.typecode AS int) END AS code_int
    FROM dbo.ECOLOGY_MESSAGE_TYPE t
)
 INSERT INTO dbo.uf_oaones_msg_type
 (type, type_name, std_type, wx_msgtype, card_action,
  title_tpl, context_tpl, url_tpl, mobile_url_tpl, cover_url, btn_json,
  app_id, yx, sort_no, remark)
 SELECT
     2 as type,
     isnull(nullif((select top 1 l.labelname
                    from dbo.HtmlLabelInfo l
                    where l.indexid = t.typename and l.languageid = 7), ''),
            isnull(nullif(case
                              when charindex('~`~`7 ', t.sourcename) > 0
                                  and charindex('`~`8', t.sourcename,
                                                charindex('~`~`7 ', t.sourcename) + 6) > charindex('~`~`7 ', t.sourcename) + 6
                                  then ltrim(rtrim(substring(t.sourcename,
                                                             charindex('~`~`7 ', t.sourcename) + 6,
                                                             charindex('`~`8', t.sourcename,
                                                                       charindex('~`~`7 ', t.sourcename) + 6)
                                                                 - charindex('~`~`7 ', t.sourcename) - 6)))
                              when charindex('~`~`', t.sourcename) = 0 then ltrim(rtrim(t.sourcename))
                              end, ''),
                   N'消息类型 ' + cast(t.typecode as nvarchar(20)))) as type_name,
     t.code_int,
     case when t.code_int in (20, 21, 25, 27, 34, 35, 38, 63)
              then N'task_card' else N'post' end as wx_msgtype,
     case when t.code_int in (20, 21, 63) then N'create'
          when t.code_int = 25 then N'done'
          when t.code_int in (27, 34, 35, 38) then N'invalid'
          else N'none' end as card_action,
     case when t.code_int in (20, 21, 63) then N'${targetName}'
          when t.code_int = 25 then N'流程已办结'
          when t.code_int = 27 then N'流程已删除'
          when t.code_int = 34 then N'流程已暂停'
          when t.code_int = 35 then N'流程已撤销'
          when t.code_int = 38 then N'流程已收回'
          else N'${title}' end as title_tpl,
     case when t.code_int = 20 then N'流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。'
          when t.code_int = 21 then N'流程「${targetName}」被退回，请重新处理。'
          when t.code_int = 63 then N'任务「${targetName}」已到达，请及时处理。'
          when t.code_int = 25 then N'流程「${targetName}」已办结。'
          when t.code_int = 27 then N'流程「${targetName}」已删除，待办已失效。'
          when t.code_int = 34 then N'流程「${targetName}」已暂停，待办已失效。'
          when t.code_int = 35 then N'流程「${targetName}」已撤销，待办已失效。'
          when t.code_int = 38 then N'流程「${targetName}」已被收回/撤销，待办已失效。'
          else N'${context}' end as context_tpl,
     NULL, NULL, NULL,
     case when t.code_int in (20, 63) then
              N'[{"key":"agree","name":"同意","replace_name":"已同意","color":"blue","is_bold":true},'
                  + N'{"key":"reject","name":"驳回","replace_name":"已驳回","color":"red","is_bold":false}]'
          when t.code_int = 21 then
              N'[{"key":"agree","name":"处理","replace_name":"已处理","color":"blue","is_bold":true}]'
          else NULL end as btn_json,
     0, 0, t.code_int,
     N'由「补齐全部消息类型」按默认口径初始化，可在本页调整'
 FROM src t
 WHERE t.code_int > 0
   AND NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_msg_type m WHERE m.std_type = t.code_int and m.type = 2);

PRINT '补齐 uf_oaones_msg_type（飞书 type=2）：本次新增 ' + cast(@@ROWCOUNT as varchar(20)) + ' 行';


;WITH src AS (
    SELECT t.id,
           t.typecode,
           t.sourcename,
           t.typename,
           CASE WHEN t.typecode NOT LIKE '%[^0-9]%' AND t.typecode <> ''
                    THEN CAST(t.typecode AS int) END AS code_int
    FROM dbo.ECOLOGY_MESSAGE_TYPE t
)
 INSERT INTO dbo.uf_oaones_msg_type
 (type_name, std_type, wx_msgtype, card_action,
  title_tpl, context_tpl, url_tpl, mobile_url_tpl, cover_url, btn_json,
  app_id, yx, sort_no, remark)
 SELECT
     isnull(nullif((select top 1 l.labelname
                    from dbo.HtmlLabelInfo l
                    where l.indexid = t.typename and l.languageid = 7), ''),
            isnull(nullif(case
                              when charindex('~`~`7 ', t.sourcename) > 0
                                  and charindex('`~`8', t.sourcename,
                                                charindex('~`~`7 ', t.sourcename) + 6) > charindex('~`~`7 ', t.sourcename) + 6
                                  then ltrim(rtrim(substring(t.sourcename,
                                                             charindex('~`~`7 ', t.sourcename) + 6,
                                                             charindex('`~`8', t.sourcename,
                                                                       charindex('~`~`7 ', t.sourcename) + 6)
                                                                 - charindex('~`~`7 ', t.sourcename) - 6)))
                              when charindex('~`~`', t.sourcename) = 0 then ltrim(rtrim(t.sourcename))
                              end, ''),
                   N'消息类型 ' + cast(t.typecode as nvarchar(20)))) as type_name,
     t.code_int,
     N'textcard' as wx_msgtype,
     N'none' as card_action,
     case when t.code_int in (20, 21, 63) then N'${targetName}'
          when t.code_int = 25 then N'流程已办结'
          when t.code_int = 27 then N'流程已删除'
          when t.code_int = 34 then N'流程已暂停'
          when t.code_int = 35 then N'流程已撤销'
          when t.code_int = 38 then N'流程已收回'
          else N'${title}' end as title_tpl,
     case when t.code_int = 20 then N'流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。'
          when t.code_int = 21 then N'流程「${targetName}」被退回，请重新处理。'
          when t.code_int = 63 then N'任务「${targetName}」已到达，请及时处理。'
          when t.code_int = 25 then N'流程「${targetName}」已办结。'
          when t.code_int = 27 then N'流程「${targetName}」已删除，待办已失效。'
          when t.code_int = 34 then N'流程「${targetName}」已暂停，待办已失效。'
          when t.code_int = 35 then N'流程「${targetName}」已撤销，待办已失效。'
          when t.code_int = 38 then N'流程「${targetName}」已被收回/撤销，待办已失效。'
          else N'${context}' end as context_tpl,
     NULL, NULL, NULL,
     NULL as btn_json,
     0, 0, t.code_int,
     N'由「补齐全部消息类型」按默认口径初始化，可在本页调整'
 FROM src t
 WHERE t.code_int > 0
   AND NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_msg_type m WHERE m.std_type = t.code_int);
PRINT '补齐 uf_oaones_msg_type：本次新增 ' + cast(@@ROWCOUNT as varchar(20)) + ' 行';


IF COL_LENGTH('dbo.uf_oaones_msg_log','api_log_id') IS NULL
ALTER TABLE dbo.uf_oaones_msg_log ADD api_log_id int NULL;

IF COL_LENGTH('dbo.uf_oaones_msg_log','reason') IS NULL
ALTER TABLE dbo.uf_oaones_msg_log ADD reason nvarchar(1000) NULL;


IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IDX_OAONES_MSG_LOG_API'
                 AND object_id = OBJECT_ID('dbo.uf_oaones_msg_log'))
CREATE INDEX IDX_OAONES_MSG_LOG_API ON dbo.uf_oaones_msg_log(api_log_id);


IF COL_LENGTH('dbo.uf_oaones_msg_log','request_json') IS NOT NULL
ALTER TABLE dbo.uf_oaones_msg_log DROP COLUMN request_json;

IF COL_LENGTH('dbo.uf_oaones_msg_log','response_json') IS NOT NULL
ALTER TABLE dbo.uf_oaones_msg_log DROP COLUMN response_json;

IF COL_LENGTH('dbo.uf_oaones_msg_log','error_stack') IS NOT NULL
ALTER TABLE dbo.uf_oaones_msg_log DROP COLUMN error_stack;

IF COL_LENGTH('dbo.uf_oaones_msg_log','errcode') IS NOT NULL
ALTER TABLE dbo.uf_oaones_msg_log DROP COLUMN errcode;

IF COL_LENGTH('dbo.uf_oaones_msg_log','errmsg') IS NOT NULL
ALTER TABLE dbo.uf_oaones_msg_log DROP COLUMN errmsg;

IF COL_LENGTH('dbo.uf_oaones_msg_log','cost_ms') IS NOT NULL
ALTER TABLE dbo.uf_oaones_msg_log DROP COLUMN cost_ms;


PRINT 'uf_oaones_msg_log -> 推送状态表（api_log_id / reason）就绪';
