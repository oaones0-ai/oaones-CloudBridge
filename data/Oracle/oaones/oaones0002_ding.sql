SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    v_added NUMBER;
BEGIN
    INSERT INTO uf_oaones_msg_type
        (type, type_name, std_type, wx_msgtype, card_action,
         title_tpl, context_tpl, url_tpl, mobile_url_tpl, cover_url, btn_json,
         app_id, yx, sort_no, remark)
    SELECT 1,
           NVL(NULLIF((SELECT l.labelname
                         FROM HtmlLabelInfo l
                        WHERE l.indexid = t.typename
                          AND l.languageid = 7
                          AND ROWNUM = 1), ''),
               NVL(NULLIF(CASE
                   WHEN INSTR(t.sourcename, '~`~`7 ') > 0
                        AND INSTR(t.sourcename, '`~`8', INSTR(t.sourcename, '~`~`7 ') + 6)
                            > INSTR(t.sourcename, '~`~`7 ') + 6
                        THEN TRIM(SUBSTR(t.sourcename, INSTR(t.sourcename, '~`~`7 ') + 6,
                                 INSTR(t.sourcename, '`~`8', INSTR(t.sourcename, '~`~`7 ') + 6)
                                     - INSTR(t.sourcename, '~`~`7 ') - 6))
                   WHEN INSTR(t.sourcename, '~`~`') = 0 THEN TRIM(t.sourcename)
               END, ''),
                   '消息类型 ' || t.typecode)),
           t.code_int,
           CASE WHEN t.code_int IN (20, 21, 25, 27, 34, 35, 38, 63)
                THEN 'task_card' ELSE 'markdown' END,
           CASE WHEN t.code_int IN (20, 21, 63) THEN 'create'
                WHEN t.code_int = 25 THEN 'done'
                WHEN t.code_int IN (27, 34, 35, 38) THEN 'invalid'
                ELSE 'none' END,
           CASE WHEN t.code_int IN (20, 21, 63) THEN '${targetName}'
                WHEN t.code_int = 25 THEN '流程已办结'
                WHEN t.code_int = 27 THEN '流程已删除'
                WHEN t.code_int = 34 THEN '流程已暂停'
                WHEN t.code_int = 35 THEN '流程已撤销'
                WHEN t.code_int = 38 THEN '流程已收回'
                ELSE '${title}' END,
           CASE WHEN t.code_int = 20 THEN '流程「${targetName}」由 ${creater} 于 ${date} ${time} 提交，请及时处理。'
                WHEN t.code_int = 21 THEN '流程「${targetName}」被退回，请重新处理。'
                WHEN t.code_int = 63 THEN '任务「${targetName}」已到达，请及时处理。'
                WHEN t.code_int = 25 THEN '流程「${targetName}」已办结。'
                WHEN t.code_int = 27 THEN '流程「${targetName}」已删除，待办已失效。'
                WHEN t.code_int = 34 THEN '流程「${targetName}」已暂停，待办已失效。'
                WHEN t.code_int = 35 THEN '流程「${targetName}」已撤销，待办已失效。'
                WHEN t.code_int = 38 THEN '流程「${targetName}」已被收回/撤销，待办已失效。'
                ELSE '${context}' END,
           NULL, NULL, NULL,
           CASE WHEN t.code_int IN (20, 63) THEN
                     '[{"key":"agree","name":"同意","replace_name":"已同意","color":"blue","is_bold":true},{"key":"reject","name":"驳回","replace_name":"已驳回","color":"red","is_bold":false}]'
                WHEN t.code_int = 21 THEN
                     '[{"key":"agree","name":"处理","replace_name":"已处理","color":"blue","is_bold":true}]'
                ELSE NULL END,
           0, 0, t.code_int,
           '由「补齐全部消息类型」按默认口径初始化，可在本页调整'
      FROM (SELECT /*+ NO_MERGE */
                   t.id, t.typecode, t.sourcename, t.typename,
                   -- typecode 在 ECOLOGY_MESSAGE_TYPE 里是 varchar2：只有纯数字才能当编号用。
                   CASE WHEN REGEXP_LIKE(t.typecode, '^[0-9]+$')
                        THEN TO_NUMBER(t.typecode) END AS code_int
              FROM ECOLOGY_MESSAGE_TYPE t) t
     WHERE t.code_int > 0
       AND NOT EXISTS (SELECT 1
                         FROM uf_oaones_msg_type m
                        WHERE m.std_type = t.code_int
                          AND m.type = 1);

    v_added := SQL%ROWCOUNT;
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('补齐 uf_oaones_msg_type（钉钉 type=1）：本次新增 ' || v_added || ' 行');
END;

SELECT type, COUNT(1) AS mapping_rows FROM uf_oaones_msg_type GROUP BY type ORDER BY type;
