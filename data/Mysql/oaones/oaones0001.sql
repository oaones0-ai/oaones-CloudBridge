SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS uf_oaones_qywx_config (
                                                     id                     int NOT NULL AUTO_INCREMENT,
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
    maintainer_ids         longtext NULL,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_QYWX_CONFIG_CORP (type, corp_id),
    KEY IDX_OAONES_QYWX_CONFIG_TYPE (type, qy)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 租户配置（企微 / 钉钉 / 飞书共用，type 区分平台）';

CREATE TABLE IF NOT EXISTS uf_oaones_dept_bind (
                                                   id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_DEPT_BIND_OA (cfg_id, oa_dept_id),
    KEY IDX_OAONES_DEPT_BIND_WX (cfg_id, wx_dept_id),
    KEY IDX_OAONES_DEPT_BIND_KEY (cfg_id, wx_dept_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones OA部门 - 平台部门 绑定（多对一）';

CREATE TABLE IF NOT EXISTS uf_oaones_user_bind (
                                                   id                     int NOT NULL AUTO_INCREMENT,
                                                   remark                 varchar(500) NULL,
    cfg_id                 int NULL,
    oa_user_id             int NULL,
    x_user_id              varchar(128) NULL,
    wx_dept_id             int NULL,
    bind_state             int NULL DEFAULT 1,
    bind_source            int NOT NULL DEFAULT 1,
    bind_time              varchar(19) NULL,
    last_sync_direction    int NULL,
    last_sync_time         varchar(19) NULL,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_USER_BIND_OA (cfg_id, oa_user_id),
    KEY IDX_OAONES_USER_BIND_WX (cfg_id, x_user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones OA人员 - 平台成员 绑定';

CREATE TABLE IF NOT EXISTS uf_oaones_sync_log (
                                                  id                     int NOT NULL AUTO_INCREMENT,
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
    detail                 longtext NULL,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_SYNC_LOG_CFG (cfgid, id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 同步日志（汇总，一次同步一行）';

CREATE TABLE IF NOT EXISTS uf_oaones_sync_log_item (
                                                       id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_SYNC_LOG_ITEM_LOG (log_id, id),
    KEY IDX_OAONES_SYNC_LOG_ITEM_CFG (cfgid, item_type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 同步日志明细（一个对象一行）';

CREATE TABLE IF NOT EXISTS uf_oaones_dep_tmp (
                                                 id             int NOT NULL AUTO_INCREMENT,
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
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_DEP_TMP_KEY (corpid, wx_dept_id, wx_dept_key),
    KEY IDX_OAONES_DEP_TMP_STATUS (corpid, type, status),
    KEY IDX_OAONES_DEP_TMP_DEPT_KEY (corpid, type, wx_dept_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 部门快照（type 区分平台）';

CREATE TABLE IF NOT EXISTS uf_oaones_user_rule (
                                                   id                     int NOT NULL AUTO_INCREMENT,
                                                   cfg_id                 int NULL,
                                                   rules                  varchar(100) NOT NULL DEFAULT '',
    remark                 varchar(500) NULL,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 默认人员绑定规则（cfg_id 为空=默认配置）';

CREATE TABLE IF NOT EXISTS uf_oaones_user_tmp (
                                                  id                int NOT NULL AUTO_INCREMENT,
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
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_USER_TMP_KEY (corpid, wx_user_id),
    KEY IDX_OAONES_USER_TMP_STATUS (corpid, type, status),
    KEY IDX_OAONES_USER_TMP_DEPT_KEY (corpid, type, wx_dept_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 人员快照（type 区分平台）';

CREATE TABLE IF NOT EXISTS uf_oaones_sub_bind (
                                                  id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_SUB_BIND_OA (cfg_id, oa_sub_id),
    UNIQUE KEY UK_OAONES_SUB_BIND_SUB (type, oa_sub_id),
    UNIQUE KEY UK_OAONES_SUB_BIND_CFG (cfg_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones OA分部 - 租户 绑定（一对一）';

CREATE TABLE IF NOT EXISTS uf_oaones_app_bind (
                                                  id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_APP_BIND_AGENT (cfg_id, agent_id),
    KEY IDX_OAONES_APP_BIND_CFG (cfg_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 工作台应用 - OA页面 绑定';

CREATE TABLE IF NOT EXISTS uf_oaones_app (
                                             id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_APP_NAME (app_name),
    KEY IDX_OAONES_APP_SORT (sort_no)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 应用中心（第三方应用列表）';

SET @oaones_app_ddl := (
    SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_app ADD COLUMN icon_seed varchar(200) NULL AFTER icon',
    'SELECT ''uf_oaones_app.icon_seed already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
    AND table_name = 'uf_oaones_app'
    AND column_name = 'icon_seed');
        PREPARE oaones_app_stmt FROM @oaones_app_ddl;
EXECUTE oaones_app_stmt;
DEALLOCATE PREPARE oaones_app_stmt;

UPDATE uf_oaones_app
SET icon_seed = app_name
WHERE icon_seed IS NULL
  AND (icon IS NULL OR icon = '');

SET @oaones_app_ddl := (
    SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_app ADD COLUMN open_mode int NULL DEFAULT 0 AFTER icon_seed',
    'SELECT ''uf_oaones_app.open_mode already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
    AND table_name = 'uf_oaones_app'
    AND column_name = 'open_mode');
        PREPARE oaones_app_stmt FROM @oaones_app_ddl;
EXECUTE oaones_app_stmt;
DEALLOCATE PREPARE oaones_app_stmt;

SET @oaones_app_ddl := (
    SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_app ADD COLUMN maintainer_ids varchar(2000) NULL AFTER open_mode',
    'SELECT ''uf_oaones_app.maintainer_ids already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
    AND table_name = 'uf_oaones_app'
    AND column_name = 'maintainer_ids');
        PREPARE oaones_app_stmt FROM @oaones_app_ddl;
EXECUTE oaones_app_stmt;
DEALLOCATE PREPARE oaones_app_stmt;

SET @oaones_app_ddl := (
    SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_app ADD COLUMN vis_sub_ids varchar(2000) NULL AFTER maintainer_ids, ADD COLUMN vis_dept_ids varchar(2000) NULL AFTER vis_sub_ids, ADD COLUMN vis_user_ids varchar(2000) NULL AFTER vis_dept_ids',
    'SELECT ''uf_oaones_app.vis_sub_ids already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
    AND table_name = 'uf_oaones_app'
    AND column_name = 'vis_sub_ids');
        PREPARE oaones_app_stmt FROM @oaones_app_ddl;
EXECUTE oaones_app_stmt;
DEALLOCATE PREPARE oaones_app_stmt;

SET @oaones_app_ddl := (
    SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_app ADD COLUMN vis_all int NULL DEFAULT 0 AFTER maintainer_ids',
    'SELECT ''uf_oaones_app.vis_all already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
    AND table_name = 'uf_oaones_app'
    AND column_name = 'vis_all');
        PREPARE oaones_app_stmt FROM @oaones_app_ddl;
EXECUTE oaones_app_stmt;
DEALLOCATE PREPARE oaones_app_stmt;

CREATE TABLE IF NOT EXISTS uf_oaones_scan_login (
                                                    id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_SCAN_LOGIN_TICKET (ticket),
    KEY IDX_OAONES_SCAN_LOGIN_EXPIRE (expire_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 扫码登录票据';

CREATE TABLE IF NOT EXISTS uf_oaones_login_config (
                                                      id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 统一登录设置（只保留一行）';

CREATE TABLE IF NOT EXISTS uf_oaones_login_sms (
                                                   id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_LOGIN_SMS_MOBILE (mobile, send_time),
    KEY IDX_OAONES_LOGIN_SMS_EMAIL (email, send_time),
    KEY IDX_OAONES_LOGIN_SMS_EXPIRE (expire_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 登录验证码（手机号 / 邮箱）';

CREATE TABLE IF NOT EXISTS uf_oaones_personal_wx_config (
                                                            id                     int NOT NULL AUTO_INCREMENT,
                                                            app_id                 varchar(64)  NULL,
    app_secret             varchar(128) NULL,
    remark                 varchar(500) NULL,
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 个人微信授权密钥（只保留一行）';

CREATE TABLE IF NOT EXISTS uf_oaones_personal_wx_bind (
                                                          id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_PW_BIND_OA_USER (oa_user_id),
    UNIQUE KEY UK_OAONES_PW_BIND_OPENID (openid)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 个人微信绑定（一人一行）';

CREATE TABLE IF NOT EXISTS uf_oaones_msg_config (
                                                    id                  int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 消息中心系统参数（单行）';

CREATE TABLE IF NOT EXISTS uf_oaones_msg_type (
                                                  id                  int NOT NULL AUTO_INCREMENT,
                                                  type                int NOT NULL DEFAULT 0,
                                                  std_config_id       varchar(64) NOT NULL DEFAULT '0',
    type_name           varchar(200) NOT NULL,
    std_type            int NOT NULL DEFAULT 0,
    wx_msgtype          varchar(32) NULL,
    card_action         varchar(16) NULL,
    title_tpl           varchar(500) NULL,
    context_tpl         longtext NULL,
    url_tpl             varchar(500) NULL,
    mobile_url_tpl      varchar(500) NULL,
    cover_url           varchar(500) NULL,
    btn_json            longtext NULL,
    app_id              int NULL DEFAULT 0,
    yx                  int NULL DEFAULT 0,
    sort_no             decimal(38,1) NULL DEFAULT 0,
    remark              varchar(500) NULL,
    modedatacreater     int NULL,
    modedatacreatedate  varchar(10) NULL,
    modedatacreatetime  varchar(8) NULL,
    modedatamodifier    int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_MSG_TYPE_KEY (std_config_id, std_type, type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 消息类型推送设置（std_config_id + std_type + type 唯一）';

SET @oaones_mt_tbl := (SELECT IF(COUNT(1) > 0, 1, 0) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_type');
SET @oaones_mt_col := (SELECT IF(COUNT(1) > 0, 1, 0) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_type'
    AND column_name = 'std_config_id');
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_tbl = 1 AND @oaones_mt_col = 0,
    'ALTER TABLE uf_oaones_msg_type ADD COLUMN std_config_id varchar(64) NOT NULL DEFAULT ''0'' AFTER type',
    'SELECT ''uf_oaones_msg_type.std_config_id ready'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

SET @oaones_mt_numcol := (SELECT IF(COUNT(1) > 0, 1, 0) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_type'
    AND column_name = 'std_config_id'
    AND data_type IN ('int', 'bigint', 'smallint', 'tinyint', 'decimal', 'numeric'));
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_numcol = 1,
    'ALTER TABLE uf_oaones_msg_type MODIFY COLUMN std_config_id varchar(64) NOT NULL DEFAULT ''0''',
    'SELECT ''uf_oaones_msg_type.std_config_id varchar ready'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

UPDATE uf_oaones_msg_type SET std_config_id = '0'
WHERE std_config_id IS NULL OR std_config_id = '';

SET @oaones_mt_old_uk := (SELECT IF(COUNT(1) > 0, 1, 0) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_type'
    AND index_name = 'UK_OAONES_MSG_TYPE_STD_TYPE');
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_tbl = 1 AND @oaones_mt_old_uk = 1,
    'ALTER TABLE uf_oaones_msg_type DROP INDEX UK_OAONES_MSG_TYPE_STD_TYPE',
    'SELECT ''uf_oaones_msg_type old unique key ready'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

SET @oaones_mt_old_uk2 := (SELECT IF(COUNT(1) > 0, 1, 0) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_type'
    AND index_name = 'UK_OAONES_MSG_TYPE_STD');
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_tbl = 1 AND @oaones_mt_old_uk2 = 1,
    'ALTER TABLE uf_oaones_msg_type DROP INDEX UK_OAONES_MSG_TYPE_STD',
    'SELECT ''uf_oaones_msg_type legacy unique key ready'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

SET @oaones_mt_dup := (SELECT IF(COUNT(1) > 0, 1, 0) FROM (
    SELECT 1 FROM uf_oaones_msg_type
    GROUP BY std_config_id, std_type, type HAVING COUNT(1) > 1) t_dup);
SET @oaones_mt_new_uk := (SELECT IF(COUNT(1) > 0, 1, 0) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_type'
    AND index_name = 'UK_OAONES_MSG_TYPE_KEY');
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_tbl = 1 AND @oaones_mt_new_uk = 0 AND @oaones_mt_dup = 0,
    'ALTER TABLE uf_oaones_msg_type ADD UNIQUE KEY UK_OAONES_MSG_TYPE_KEY (std_config_id, std_type, type)',
    IF(@oaones_mt_tbl = 1 AND @oaones_mt_dup > 0,
    'SELECT ''存在重复的 (std_config_id, std_type, type)，请先清理重复数据再重跑本段；本脚本不删任何数据''',
    'SELECT ''uf_oaones_msg_type new unique key ready''')));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

SET @oaones_mt_nullable := (SELECT IF(COUNT(1) > 0, 1, 0) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_type'
    AND column_name = 'std_type' AND is_nullable = 'YES');
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_tbl = 1 AND @oaones_mt_nullable = 1,
    'UPDATE uf_oaones_msg_type SET std_type = 0 WHERE std_type IS NULL',
    'SELECT ''uf_oaones_msg_type.std_type not null ready'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

SET @oaones_mt_sql := (SELECT IF(@oaones_mt_tbl = 1 AND @oaones_mt_nullable = 1,
    'ALTER TABLE uf_oaones_msg_type MODIFY COLUMN std_type int NOT NULL DEFAULT 0',
    'SELECT ''uf_oaones_msg_type.std_type not null ready'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

SET @oaones_mt_std := (SELECT IF(COUNT(1) = 2, 1, 0) FROM information_schema.tables
    WHERE table_schema = DATABASE()
    AND table_name IN ('ecology_biz_mobile_config', 'ecology_message_type'));
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_tbl = 1 AND @oaones_mt_std = 1,
    'UPDATE uf_oaones_msg_type m SET m.std_config_id = COALESCE((SELECT MIN(c.id) FROM ecology_biz_mobile_config c JOIN ecology_message_type s ON (c.messagetypeid LIKE CONCAT(''%,'', s.id, '',%'') OR c.messagetypeid = CAST(s.id AS char)) WHERE (c.isshow = ''y'' OR c.isshow IS NULL) AND s.typecode = CAST(m.std_type AS char)), ''0'') WHERE m.std_config_id = ''0'' AND m.std_type > 0',
    'SELECT ''uf_oaones_msg_type std_config_id backfill skipped'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

CREATE TABLE IF NOT EXISTS uf_oaones_msg_type_app (
                                                      id                     int NOT NULL AUTO_INCREMENT,
                                                      type                   int NOT NULL DEFAULT 0 COMMENT '平台类型：0 企微 / 1 钉钉 / 2 飞书（共用本表）',
                                                      std_config_id          varchar(64) NOT NULL DEFAULT '0' COMMENT '泛微标准“消息类型”ID（字符串）；'' = 未纳入标准消息类型的独立来源',
    std_type               int NOT NULL DEFAULT 0 COMMENT '仅“独立来源”行使用：标准消息来源 typecode（标准类型行固定 0）',
    cfg_id                 int NOT NULL DEFAULT 0 COMMENT '租户ID（uf_oaones_qywx_config.id，按 type 区分平台）',
    app_id                 int NOT NULL DEFAULT 0 COMMENT '绑定的推送目标应用（uf_oaones_app_bind.id）',
    modedatacreater        int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8) NULL,
    modedatamodifier       int NULL,
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_MSG_TYPE_APP_KEY (type, std_config_id, std_type, cfg_id),
    KEY IDX_OAONES_MSG_TYPE_APP_APP (app_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS uf_oaones_msg_log (
                                                 id                  int NOT NULL AUTO_INCREMENT,
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
    receiver_wx         longtext NULL,
    receiver_count      int NULL,
    shard_index         int NULL DEFAULT 1,
    shard_total         int NULL DEFAULT 1,
    status              int NULL DEFAULT 0,
    retry_count         int NULL DEFAULT 0,
    next_retry_time     varchar(19) NULL,
    api_log_id          int NULL,
    reason              varchar(1000) NULL,
    src_json            longtext NULL,
    operator_id         int NULL,
    formmodeid          int NULL,
    modedatacreater         int NULL,
    modedatacreatedate      varchar(10) NULL,
    modedatacreatetime      varchar(8)  NULL,
    modedatamodifier        int NULL,
    modedatamodifydatetime  varchar(100) NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_MSG_LOG_BIZ (biz_type, biz_id),
    KEY IDX_OAONES_MSG_LOG_STATUS (status, retry_count),
    KEY IDX_OAONES_MSG_LOG_TIME (modedatacreatedate, modedatacreatetime),
    KEY IDX_OAONES_MSG_LOG_DEDUP (dedup_key, modedatacreatedate, modedatacreatetime),
    KEY IDX_OAONES_MSG_LOG_API (api_log_id),
    KEY IDX_OAONES_MSG_LOG_TYPE (type, modedatacreatedate, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 推送日志（状态表；接口报文与结果在 uf_oaones_api_log）';

CREATE TABLE IF NOT EXISTS uf_oaones_msg_task (
                                                  id                  int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime  varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_MSG_TASK (task_id),
    KEY IDX_OAONES_MSG_TASK_BIZ (biz_type, biz_id, wx_userid),
    KEY IDX_OAONES_MSG_TASK_TYPE (type, biz_type, biz_id, wx_userid, task_state)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 卡片（待办）任务';

CREATE TABLE IF NOT EXISTS uf_oaones_sync_plan (
                                                   id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_SYNC_PLAN_CFG (type, cfgid, direction),
    KEY IDX_OAONES_SYNC_PLAN_NEXT (enabled, next_run_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 同步中心同步设置（计划任务，一行一条 同步规则，同一租户+方向可多条）';

SET @oaones_plan_ddl := (
    SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_sync_plan ADD COLUMN scope_set int NULL DEFAULT 0 AFTER default_dept_name, ADD COLUMN oa_sub_ids varchar(500) NULL AFTER scope_set, ADD COLUMN oa_dept_ids varchar(500) NULL AFTER oa_sub_ids, ADD COLUMN wx_dept_id varchar(64) NULL AFTER oa_dept_ids, ADD COLUMN scope_text varchar(500) NULL AFTER wx_dept_id, ADD COLUMN bind_forced int NULL DEFAULT 0 AFTER scope_text',
    'SELECT ''uf_oaones_sync_plan.scope_set already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
    AND table_name = 'uf_oaones_sync_plan'
    AND column_name = 'scope_set');
        PREPARE oaones_plan_stmt FROM @oaones_plan_ddl;
EXECUTE oaones_plan_stmt;
DEALLOCATE PREPARE oaones_plan_stmt;

SET @oaones_plan_mode_ddl := (
    SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_sync_plan ADD COLUMN plan_mode int NULL DEFAULT 1 AFTER enabled, ADD COLUMN interval_min int NULL DEFAULT 30 AFTER plan_mode',
    'SELECT ''uf_oaones_sync_plan.plan_mode already exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
    AND table_name = 'uf_oaones_sync_plan'
    AND column_name = 'plan_mode');
        PREPARE oaones_plan_mode_stmt FROM @oaones_plan_mode_ddl;
EXECUTE oaones_plan_mode_stmt;
DEALLOCATE PREPARE oaones_plan_mode_stmt;

UPDATE uf_oaones_sync_plan SET plan_mode = 1 WHERE plan_mode IS NULL;
UPDATE uf_oaones_sync_plan SET interval_min = 30 WHERE interval_min IS NULL OR interval_min <= 0;

CREATE TABLE IF NOT EXISTS uf_oaones_mtg_sync (
                                                  id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_MTG_SYNC (biz, type, cfgid),
    KEY IDX_OAONES_MTG_SYNC_ENABLE (enabled, biz, type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 会议/日程同步设置（一行一个 业务+平台+租户）';

CREATE TABLE IF NOT EXISTS uf_oaones_mtg_sync_log (
                                                      id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_MTG_SYNC_LOG_CFG (biz, type, cfgid),
    KEY IDX_OAONES_MTG_SYNC_LOG_OA (biz, type, cfgid, oa_id),
    KEY IDX_OAONES_MTG_SYNC_LOG_TIME (modedatacreatedate, modedatacreatetime)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 会议/日程同步记录（一行一个对象的一次同步动作）';

CREATE TABLE IF NOT EXISTS uf_oaones_mtg_sync_user (
                                                       id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_MTG_SYNC_USER (biz, type, cfgid, oa_user_id),
    KEY IDX_OAONES_MTG_SYNC_USER_CFG (type, cfgid, biz)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 会议/日程同步个人开关（一行一个 业务+平台+租户+人员）';

CREATE TABLE IF NOT EXISTS uf_oaones_mtg_link (
                                                  id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_OAONES_MTG_LINK_WX (biz, type, cfgid, wx_id),
    UNIQUE KEY UK_OAONES_MTG_LINK_OA (biz, type, cfgid, oa_id),
    KEY IDX_OAONES_MTG_LINK_SRC (biz, type, cfgid, source)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 会议/日程同步对象映射（OA对象ID 与 平台对象ID）';

CREATE TABLE IF NOT EXISTS uf_oaones_api_log (
                                                 id                     int NOT NULL AUTO_INCREMENT,
                                                 type                   int NOT NULL DEFAULT 0,
                                                 cfg_id                 int NULL DEFAULT 0,
                                                 corp_name              varchar(200) NULL,
    api_type               varchar(32) NULL,
    direction              int NULL DEFAULT 0,
    method                 varchar(16) NULL,
    api_path               varchar(500) NULL,
    api_url                varchar(1000) NULL,
    req_body               longtext NULL,
    resp_body              longtext NULL,
    http_status            int NULL,
    success                int NULL DEFAULT 0,
    err_code               varchar(64) NULL,
    err_msg                longtext NULL,
    cost_ms                int NULL,
    error_stack            longtext NULL,
    operator_id            int NULL,
    formmodeid             int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8)  NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_API_LOG_FILTER (type, cfg_id, api_type, modedatacreatedate),
    KEY IDX_OAONES_API_LOG_TIME (modedatacreatedate, modedatacreatetime),
    KEY IDX_OAONES_API_LOG_RESULT (success, modedatacreatedate)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 接口日志';

CREATE TABLE IF NOT EXISTS uf_oaones_op_log (
                                                id                     int NOT NULL AUTO_INCREMENT,
                                                user_id                int NULL,
                                                login_name             varchar(100) NULL,
    user_name              varchar(200) NULL,
    dept_name              varchar(200) NULL,
    op_type                varchar(16) NULL,
    menu_id                varchar(64) NULL,
    menu_name              varchar(200) NULL,
    menu_path              varchar(500) NULL,
    page_url               varchar(1000) NULL,
    client_ip              varchar(100) NULL,
    device                 varchar(16) NULL,
    session_id             varchar(100) NULL,
    formmodeid             int NULL,
    modedatacreatedate     varchar(10) NULL,
    modedatacreatetime     varchar(8)  NULL,
    PRIMARY KEY (id),
    KEY IDX_OAONES_OP_LOG_USER (user_id, modedatacreatedate),
    KEY IDX_OAONES_OP_LOG_TYPE (op_type, modedatacreatedate),
    KEY IDX_OAONES_OP_LOG_TIME (modedatacreatedate, modedatacreatetime),
    KEY IDX_OAONES_OP_LOG_MENU (menu_id, modedatacreatedate)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 操作监控日志（登录 / 退出 / 菜单访问）';

CREATE TABLE IF NOT EXISTS uf_oaones_mtg_cron (
                                                  id                     int NOT NULL,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 会议/日程 第三方→OA 拉取计划（单行）';

CREATE TABLE IF NOT EXISTS uf_oaones_mtg_plan_item (
                                                       id                     int NOT NULL AUTO_INCREMENT,
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
    modedatamodifydatetime varchar(100) NULL,
    PRIMARY KEY (id),
    KEY idx_oaones_mtg_plan_item_plan (plan_id, showorder)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAones 会议/日程 计划任务时间段明细';

SELECT 'OAones 公共表建表完成（已存在的表连同索引一起跳过）' AS message;

INSERT IGNORE INTO uf_oaones_msg_config
    (id, push_enable, default_app_id, retry_times, retry_interval, shard_size,
     timeout_ms, token_advance_sec, dedup_window_sec, log_keep_days, task_keep_days, remark)
VALUES
    (1, 1, 0, 3, 60, 1000, 30000, 300, 600, 90, 30, '默认参数，可在“消息中心-推送配置”页维护');

SET @oaones_login_cfg_empty := (SELECT COUNT(1) = 0 FROM uf_oaones_login_config);

INSERT INTO uf_oaones_login_config
(password_enable, mobile_enable, wecom_qr_enable, dingtalk_qr_enable, feishu_qr_enable,
 personal_wx_enable, qr_ttl_seconds, modedatacreatedate, modedatacreatetime)
SELECT 1, 0, 1, 0, 0, 0, 180, DATE_FORMAT(NOW(), '%Y-%m-%d'), '000000'
FROM DUAL
WHERE @oaones_login_cfg_empty = 1;

SET @oaones_msg_type_empty := (SELECT COUNT(1) = 0 FROM uf_oaones_msg_type);

INSERT INTO uf_oaones_msg_type
(type, type_name, std_type, wx_msgtype, card_action, title_tpl, context_tpl,
 url_tpl, mobile_url_tpl, cover_url, btn_json, app_id, yx, sort_no, remark)
SELECT * FROM (
                  SELECT 0 AS type, '流程待办提醒' AS type_name, 20 AS std_type, 'textcard' AS wx_msgtype, 'none' AS card_action,
                         '${targetName}' AS title_tpl,
                         '流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。' AS context_tpl,
                         NULL AS url_tpl, NULL AS mobile_url_tpl, NULL AS cover_url,
                         NULL AS btn_json,
                         0 AS app_id, 0 AS yx, 10 AS sort_no, 'OA 新到达流程 -> 企微文本卡片' AS remark
                  UNION ALL SELECT 0, '流程退回提醒', 21, 'textcard', 'none',
                                   '${targetName}', '流程「${targetName}」被退回，请重新处理。',
                                   NULL, NULL, NULL,
                                   NULL,
                                   0, 0, 20, NULL
                  UNION ALL SELECT 0, '流程办结提醒', 25, 'textcard', 'none',
                                   '流程已办结', '流程「${targetName}」已办结。', NULL, NULL, NULL, NULL, 0, 0, 30, NULL
                  UNION ALL SELECT 0, '流程撤销/收回提醒', 38, 'textcard', 'none',
                                   '流程已收回', '流程「${targetName}」已被收回/撤销。', NULL, NULL, NULL, NULL, 0, 0, 40, '如需 27/34/35 同口径，可复制本行改 std_type'
                  UNION ALL SELECT 0, '流程抄送/传阅提醒', 23, 'textcard', 'none',
                                   '流程抄送：${targetName}', '由 ${creater} 于 ${date} ${time} 抄送给你，请查阅。', NULL, NULL, NULL, NULL, 0, 0, 50, NULL
                  UNION ALL SELECT 0, '流程转发提醒', 22, 'textcard', 'none',
                                   '流程转发：${targetName}', '${creater} 转发了流程「${targetName}」，请查阅。', NULL, NULL, NULL, NULL, 0, 0, 60, NULL
                  UNION ALL SELECT 0, '流程超时提醒', 120, 'textcard', 'none',
                                   '流程超时：${targetName}', '流程「${targetName}」已超时，请尽快处理。', NULL, NULL, NULL, NULL, 0, 0, 70, NULL
                  UNION ALL SELECT 0, '会议提醒', 64, 'textcard', 'none',
                                   '会议提醒：${targetName}', '${context}', NULL, NULL, NULL, NULL, 0, 0, 80, NULL
                  UNION ALL SELECT 0, '新会议提醒', 124, 'textcard', 'none',
                                   '新会议：${targetName}', '${context}', NULL, NULL, NULL, NULL, 0, 0, 90, NULL
                  UNION ALL SELECT 0, '会议开始前提醒', 125, 'textcard', 'none',
                                   '会议即将开始：${targetName}', '${context}', NULL, NULL, NULL, NULL, 0, 0, 100, NULL
                  UNION ALL SELECT 0, '会议结束前提醒', 126, 'textcard', 'none',
                                   '会议即将结束：${targetName}', '${context}', NULL, NULL, NULL, NULL, 0, 0, 110, NULL
                  UNION ALL SELECT 0, '会议取消提醒', 127, 'textcard', 'none',
                                   '会议取消：${targetName}', '${context}', NULL, NULL, NULL, NULL, 0, 0, 120, NULL
              ) AS t_init
WHERE @oaones_msg_type_empty = 1;

SET @oaones_mt_std := (SELECT IF(COUNT(1) = 2, 1, 0) FROM information_schema.tables
    WHERE table_schema = DATABASE()
    AND table_name IN ('ecology_biz_mobile_config', 'ecology_message_type'));
SET @oaones_mt_sql := (SELECT IF(@oaones_mt_std = 1,
    'UPDATE uf_oaones_msg_type m SET m.std_config_id = COALESCE((SELECT MIN(c.id) FROM ecology_biz_mobile_config c JOIN ecology_message_type s ON (c.messagetypeid LIKE CONCAT(''%,'', s.id, '',%'') OR c.messagetypeid = CAST(s.id AS char)) WHERE (c.isshow = ''y'' OR c.isshow IS NULL) AND s.typecode = CAST(m.std_type AS char)), ''0'') WHERE m.std_config_id = ''0'' AND m.std_type > 0',
    'SELECT ''uf_oaones_msg_type std_config_id backfill skipped'''));
        PREPARE oaones_mt_stmt FROM @oaones_mt_sql;
EXECUTE oaones_mt_stmt;
DEALLOCATE PREPARE oaones_mt_stmt;

SELECT CONCAT('初始化完成：uf_oaones_msg_config=', (SELECT COUNT(1) FROM uf_oaones_msg_config),
              ' 行，uf_oaones_msg_type=', (SELECT COUNT(1) FROM uf_oaones_msg_type),
              ' 行，uf_oaones_login_config=', (SELECT COUNT(1) FROM uf_oaones_login_config), ' 行') AS message;

SELECT COUNT(1) AS dingtalk_mappings_before FROM uf_oaones_msg_type WHERE type = 1;

INSERT IGNORE INTO uf_oaones_msg_type
    (type, type_name, std_type, wx_msgtype, card_action,
     title_tpl, context_tpl, url_tpl, mobile_url_tpl, cover_url, btn_json,
     app_id, yx, sort_no, remark)
SELECT 1 AS type,
       IFNULL(NULLIF((SELECT l.labelname
                      FROM HtmlLabelInfo l
                      WHERE l.indexid = t.typename AND l.languageid = 7
                          LIMIT 1), ''),
              IFNULL(NULLIF(CASE
                                WHEN LOCATE('~`~`7 ', t.sourcename) > 0
                                    AND LOCATE('`~`8', t.sourcename, LOCATE('~`~`7 ', t.sourcename) + 6)
                                         > LOCATE('~`~`7 ', t.sourcename) + 6
                                    THEN TRIM(SUBSTRING(t.sourcename, LOCATE('~`~`7 ', t.sourcename) + 6,
                                                        LOCATE('`~`8', t.sourcename, LOCATE('~`~`7 ', t.sourcename) + 6)
                                                            - LOCATE('~`~`7 ', t.sourcename) - 6))
                                WHEN LOCATE('~`~`', t.sourcename) = 0 THEN TRIM(t.sourcename)
                                END, ''),
                     CONCAT('消息类型 ', t.typecode))) AS type_name,
       t.code_int AS std_type,
       CASE WHEN t.code_int IN (20, 21, 25, 27, 34, 35, 38, 63)
                THEN 'task_card' ELSE 'markdown' END AS wx_msgtype,
       CASE WHEN t.code_int IN (20, 21, 63) THEN 'create'
            WHEN t.code_int = 25 THEN 'done'
            WHEN t.code_int IN (27, 34, 35, 38) THEN 'invalid'
            ELSE 'none' END AS card_action,
       CASE WHEN t.code_int IN (20, 21, 63) THEN '${targetName}'
            WHEN t.code_int = 25 THEN '流程已办结'
            WHEN t.code_int = 27 THEN '流程已删除'
            WHEN t.code_int = 34 THEN '流程已暂停'
            WHEN t.code_int = 35 THEN '流程已撤销'
            WHEN t.code_int = 38 THEN '流程已收回'
            ELSE '${title}' END AS title_tpl,
       CASE WHEN t.code_int = 20 THEN '流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。'
            WHEN t.code_int = 21 THEN '流程「${targetName}」被退回，请重新处理。'
            WHEN t.code_int = 63 THEN '任务「${targetName}」已到达，请及时处理。'
            WHEN t.code_int = 25 THEN '流程「${targetName}」已办结。'
            WHEN t.code_int = 27 THEN '流程「${targetName}」已删除，待办已失效。'
            WHEN t.code_int = 34 THEN '流程「${targetName}」已暂停，待办已失效。'
            WHEN t.code_int = 35 THEN '流程「${targetName}」已撤销，待办已失效。'
            WHEN t.code_int = 38 THEN '流程「${targetName}」已被收回/撤销，待办已失效。'
            ELSE '${context}' END AS context_tpl,
       NULL, NULL, NULL,
       CASE WHEN t.code_int IN (20, 63) THEN
                '[{"key":"agree","name":"同意","replace_name":"已同意","color":"blue","is_bold":true},{"key":"reject","name":"驳回","replace_name":"已驳回","color":"red","is_bold":false}]'
            WHEN t.code_int = 21 THEN
                '[{"key":"agree","name":"处理","replace_name":"已处理","color":"blue","is_bold":true}]'
            ELSE NULL END AS btn_json,
       0 AS app_id,
       0 AS yx,
       t.code_int AS sort_no,
       '由「补齐全部消息类型」按默认口径初始化，可在本页调整' AS remark
FROM (
         SELECT t.id,
                t.typecode,
                t.sourcename,
                t.typename,
                CASE WHEN t.typecode REGEXP '^[0-9]+$' AND t.typecode <> ''
             THEN CAST(t.typecode AS SIGNED) END AS code_int
          FROM ECOLOGY_MESSAGE_TYPE t
       ) AS t
 WHERE t.code_int > 0;

SELECT ROW_COUNT() AS added_rows;

SELECT type, COUNT(1) AS mapping_rows FROM uf_oaones_msg_type GROUP BY type ORDER BY type;

SELECT COUNT(1) AS feishu_mappings_before FROM uf_oaones_msg_type WHERE type = 2;

INSERT IGNORE INTO uf_oaones_msg_type
    (type, type_name, std_type, wx_msgtype, card_action,
     title_tpl, context_tpl, url_tpl, mobile_url_tpl, cover_url, btn_json,
     app_id, yx, sort_no, remark)
SELECT 2 AS type,
       IFNULL(NULLIF((SELECT l.labelname
                      FROM HtmlLabelInfo l
                      WHERE l.indexid = t.typename AND l.languageid = 7
                          LIMIT 1), ''),
              IFNULL(NULLIF(CASE
                                WHEN LOCATE('~`~`7 ', t.sourcename) > 0
                                    AND LOCATE('`~`8', t.sourcename, LOCATE('~`~`7 ', t.sourcename) + 6)
                                         > LOCATE('~`~`7 ', t.sourcename) + 6
                                    THEN TRIM(SUBSTRING(t.sourcename, LOCATE('~`~`7 ', t.sourcename) + 6,
                                                        LOCATE('`~`8', t.sourcename, LOCATE('~`~`7 ', t.sourcename) + 6)
                                                            - LOCATE('~`~`7 ', t.sourcename) - 6))
                                WHEN LOCATE('~`~`', t.sourcename) = 0 THEN TRIM(t.sourcename)
                                END, ''),
                     CONCAT('消息类型 ', t.typecode))) AS type_name,
       t.code_int AS std_type,
       CASE WHEN t.code_int IN (20, 21, 25, 27, 34, 35, 38, 63)
                THEN 'task_card' ELSE 'post' END AS wx_msgtype,
       CASE WHEN t.code_int IN (20, 21, 63) THEN 'create'
            WHEN t.code_int = 25 THEN 'done'
            WHEN t.code_int IN (27, 34, 35, 38) THEN 'invalid'
            ELSE 'none' END AS card_action,
       CASE WHEN t.code_int IN (20, 21, 63) THEN '${targetName}'
            WHEN t.code_int = 25 THEN '流程已办结'
            WHEN t.code_int = 27 THEN '流程已删除'
            WHEN t.code_int = 34 THEN '流程已暂停'
            WHEN t.code_int = 35 THEN '流程已撤销'
            WHEN t.code_int = 38 THEN '流程已收回'
            ELSE '${title}' END AS title_tpl,
       CASE WHEN t.code_int = 20 THEN '流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。'
            WHEN t.code_int = 21 THEN '流程「${targetName}」被退回，请重新处理。'
            WHEN t.code_int = 63 THEN '任务「${targetName}」已到达，请及时处理。'
            WHEN t.code_int = 25 THEN '流程「${targetName}」已办结。'
            WHEN t.code_int = 27 THEN '流程「${targetName}」已删除，待办已失效。'
            WHEN t.code_int = 34 THEN '流程「${targetName}」已暂停，待办已失效。'
            WHEN t.code_int = 35 THEN '流程「${targetName}」已撤销，待办已失效。'
            WHEN t.code_int = 38 THEN '流程「${targetName}」已被收回/撤销，待办已失效。'
            ELSE '${context}' END AS context_tpl,
       NULL, NULL, NULL,
       CASE WHEN t.code_int IN (20, 63) THEN
                '[{"key":"agree","name":"同意","replace_name":"已同意","color":"blue","is_bold":true},{"key":"reject","name":"驳回","replace_name":"已驳回","color":"red","is_bold":false}]'
            WHEN t.code_int = 21 THEN
                '[{"key":"agree","name":"处理","replace_name":"已处理","color":"blue","is_bold":true}]'
            ELSE NULL END AS btn_json,
       0 AS app_id,
       0 AS yx,
       t.code_int AS sort_no,
       '由「补齐全部消息类型」按默认口径初始化，可在本页调整' AS remark
FROM (
         SELECT t.id,
                t.typecode,
                t.sourcename,
                t.typename,
                CASE WHEN t.typecode REGEXP '^[0-9]+$' AND t.typecode <> ''
             THEN CAST(t.typecode AS SIGNED) END AS code_int
          FROM ECOLOGY_MESSAGE_TYPE t
       ) AS t
 WHERE t.code_int > 0;

SET @ddl := (SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_msg_log ADD COLUMN api_log_id int NULL COMMENT ''最近一次接口调用对应的接口日志ID（uf_oaones_api_log.id）''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'api_log_id');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) = 0,
    'ALTER TABLE uf_oaones_msg_log ADD COLUMN reason varchar(1000) NULL COMMENT ''跳过 / 去重 / 拦截等业务原因''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'reason');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) = 0,
    'CREATE INDEX IDX_OAONES_MSG_LOG_API ON uf_oaones_msg_log(api_log_id)',
    'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND index_name = 'IDX_OAONES_MSG_LOG_API');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) > 0,
    'ALTER TABLE uf_oaones_msg_log DROP COLUMN request_json', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'request_json');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) > 0,
    'ALTER TABLE uf_oaones_msg_log DROP COLUMN response_json', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'response_json');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) > 0,
    'ALTER TABLE uf_oaones_msg_log DROP COLUMN error_stack', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'error_stack');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) > 0,
    'ALTER TABLE uf_oaones_msg_log DROP COLUMN errcode', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'errcode');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) > 0,
    'ALTER TABLE uf_oaones_msg_log DROP COLUMN errmsg', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'errmsg');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(1) > 0,
    'ALTER TABLE uf_oaones_msg_log DROP COLUMN cost_ms', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'uf_oaones_msg_log' AND column_name = 'cost_ms');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SELECT 'uf_oaones_msg_log -> 推送状态表（api_log_id / reason）就绪' AS message;
