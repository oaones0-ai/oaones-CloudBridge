-- OAones 表名适配 appsyn 子模块。
-- 本脚本为一次性迁移：逐表执行，不使用前缀扫描、游标或批量重命名。
-- SQL Server 不支持 ALTER TABLE ... RENAME TO，因此每张表使用一条显式 sp_rename。
-- 请先停止 E9，并按数据库实际对象状态执行；每张表必须单独确认源表存在且目标表不存在。
-- 不修改任何历史 SQL 文件中的建表语句。

EXEC sys.sp_rename N'dbo.uf_oaones_qywx_config', N'uf_oaones_appsyn_qywx_config', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_dept_bind', N'uf_oaones_appsyn_dept_bind', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_sub_tenant_map', N'uf_oaones_appsyn_sub_tenant_map', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_user_bind', N'uf_oaones_appsyn_user_bind', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_user_rule', N'uf_oaones_appsyn_user_rule', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_sync_log', N'uf_oaones_appsyn_sync_log', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_sync_log_item', N'uf_oaones_appsyn_sync_log_item', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_sync_plan', N'uf_oaones_appsyn_sync_plan', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_dep_tmp', N'uf_oaones_appsyn_dep_tmp', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_user_tmp', N'uf_oaones_appsyn_user_tmp', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_app_bind', N'uf_oaones_appsyn_app_bind', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_app', N'uf_oaones_appsyn_app', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_mtg_sync', N'uf_oaones_appsyn_mtg_sync', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_mtg_sync_log', N'uf_oaones_appsyn_mtg_sync_log', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_mtg_sync_user', N'uf_oaones_appsyn_mtg_sync_user', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_mtg_link', N'uf_oaones_appsyn_mtg_link', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_mtg_cron', N'uf_oaones_appsyn_mtg_cron', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_mtg_plan_item', N'uf_oaones_appsyn_mtg_plan_item', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_att_cfg', N'uf_oaones_appsyn_att_cfg', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_att_plan_item', N'uf_oaones_appsyn_att_plan_item', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_att_log', N'uf_oaones_appsyn_att_log', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_att_log_item', N'uf_oaones_appsyn_att_log_item', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_att_link', N'uf_oaones_appsyn_att_link', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_scan_login', N'uf_oaones_appsyn_scan_login', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_login_config', N'uf_oaones_appsyn_login_config', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_login_sms', N'uf_oaones_appsyn_login_sms', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_personal_wx_config', N'uf_oaones_appsyn_personal_wx_config', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_personal_wx_bind', N'uf_oaones_appsyn_personal_wx_bind', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_api_log', N'uf_oaones_appsyn_api_log', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_op_log', N'uf_oaones_appsyn_op_log', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_log_retention', N'uf_oaones_appsyn_log_retention', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_msg_config', N'uf_oaones_appsyn_msg_config', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_msg_type', N'uf_oaones_appsyn_msg_type', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_msg_type_app', N'uf_oaones_appsyn_msg_type_app', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_msg_log', N'uf_oaones_appsyn_msg_log', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_msg_task', N'uf_oaones_appsyn_msg_task', N'OBJECT';
GO

EXEC sys.sp_rename N'dbo.uf_oaones_sub_bind', N'uf_oaones_appsyn_sub_bind', N'OBJECT';
GO
