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
