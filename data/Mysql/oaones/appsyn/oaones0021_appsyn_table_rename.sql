-- OAones 表名适配 appsyn 子模块。
-- 本脚本为一次性迁移：逐表执行，不使用前缀扫描、游标或批量重命名。
-- 请先停止 E9，并按数据库实际对象状态执行；每张表必须单独确认源表存在且目标表不存在。
-- 不修改任何历史 SQL 文件中的建表语句。

ALTER TABLE `uf_oaones_qywx_config` RENAME TO `uf_oaones_appsyn_qywx_config`;

ALTER TABLE `uf_oaones_dept_bind` RENAME TO `uf_oaones_appsyn_dept_bind`;

ALTER TABLE `uf_oaones_sub_tenant_map` RENAME TO `uf_oaones_appsyn_sub_tenant_map`;

ALTER TABLE `uf_oaones_user_bind` RENAME TO `uf_oaones_appsyn_user_bind`;

ALTER TABLE `uf_oaones_user_rule` RENAME TO `uf_oaones_appsyn_user_rule`;

ALTER TABLE `uf_oaones_sync_log` RENAME TO `uf_oaones_appsyn_sync_log`;

ALTER TABLE `uf_oaones_sync_log_item` RENAME TO `uf_oaones_appsyn_sync_log_item`;

ALTER TABLE `uf_oaones_sync_plan` RENAME TO `uf_oaones_appsyn_sync_plan`;

ALTER TABLE `uf_oaones_dep_tmp` RENAME TO `uf_oaones_appsyn_dep_tmp`;

ALTER TABLE `uf_oaones_user_tmp` RENAME TO `uf_oaones_appsyn_user_tmp`;

ALTER TABLE `uf_oaones_app_bind` RENAME TO `uf_oaones_appsyn_app_bind`;

ALTER TABLE `uf_oaones_app` RENAME TO `uf_oaones_appsyn_app`;

ALTER TABLE `uf_oaones_mtg_sync` RENAME TO `uf_oaones_appsyn_mtg_sync`;

ALTER TABLE `uf_oaones_mtg_sync_log` RENAME TO `uf_oaones_appsyn_mtg_sync_log`;

ALTER TABLE `uf_oaones_mtg_sync_user` RENAME TO `uf_oaones_appsyn_mtg_sync_user`;

ALTER TABLE `uf_oaones_mtg_link` RENAME TO `uf_oaones_appsyn_mtg_link`;

ALTER TABLE `uf_oaones_mtg_cron` RENAME TO `uf_oaones_appsyn_mtg_cron`;

ALTER TABLE `uf_oaones_mtg_plan_item` RENAME TO `uf_oaones_appsyn_mtg_plan_item`;

ALTER TABLE `uf_oaones_att_cfg` RENAME TO `uf_oaones_appsyn_att_cfg`;

ALTER TABLE `uf_oaones_att_plan_item` RENAME TO `uf_oaones_appsyn_att_plan_item`;

ALTER TABLE `uf_oaones_att_log` RENAME TO `uf_oaones_appsyn_att_log`;

ALTER TABLE `uf_oaones_att_log_item` RENAME TO `uf_oaones_appsyn_att_log_item`;

ALTER TABLE `uf_oaones_att_link` RENAME TO `uf_oaones_appsyn_att_link`;

ALTER TABLE `uf_oaones_scan_login` RENAME TO `uf_oaones_appsyn_scan_login`;

ALTER TABLE `uf_oaones_login_config` RENAME TO `uf_oaones_appsyn_login_config`;

ALTER TABLE `uf_oaones_login_sms` RENAME TO `uf_oaones_appsyn_login_sms`;

ALTER TABLE `uf_oaones_personal_wx_config` RENAME TO `uf_oaones_appsyn_personal_wx_config`;

ALTER TABLE `uf_oaones_personal_wx_bind` RENAME TO `uf_oaones_appsyn_personal_wx_bind`;

ALTER TABLE `uf_oaones_api_log` RENAME TO `uf_oaones_appsyn_api_log`;

ALTER TABLE `uf_oaones_op_log` RENAME TO `uf_oaones_appsyn_op_log`;

ALTER TABLE `uf_oaones_log_retention` RENAME TO `uf_oaones_appsyn_log_retention`;

ALTER TABLE `uf_oaones_msg_config` RENAME TO `uf_oaones_appsyn_msg_config`;

ALTER TABLE `uf_oaones_msg_type` RENAME TO `uf_oaones_appsyn_msg_type`;

ALTER TABLE `uf_oaones_msg_type_app` RENAME TO `uf_oaones_appsyn_msg_type_app`;

ALTER TABLE `uf_oaones_msg_log` RENAME TO `uf_oaones_appsyn_msg_log`;

ALTER TABLE `uf_oaones_msg_task` RENAME TO `uf_oaones_appsyn_msg_task`;

ALTER TABLE `uf_oaones_sub_bind` RENAME TO `uf_oaones_appsyn_sub_bind`;
