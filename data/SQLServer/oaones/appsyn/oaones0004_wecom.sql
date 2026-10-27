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
    -- 名称口径（与页面下拉完全一致）：① HtmlLabelInfo 简体中文标签 → ② sourcename
    -- （多语言站点里是 ~`~`7 中文`~`8 English`~`~ 这种编码串，取简体中文那一段；不含标记的
    --   纯文本名称原样用）→ ③ “消息类型 <typecode>”占位。
    -- 用子查询而不是 join，避免同一标签多语言/多行时把一条标准类型补成两行；
    -- 连的是 typename（标签索引 int），sourcename 只用来兜底取名（不 join、不转换，避免 245）。
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
    -- 默认一律「文本卡片 + 不处理卡片」：不再默认建待办卡片，避免客户端出现点了没反应的卡片按钮
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
    -- 文本卡片不带卡片按钮；要改回任务卡片（OA 待办）请在本页配置形态、卡片动作与按钮 JSON
    NULL as btn_json,
    0, 0, t.code_int,
    N'由「补齐全部消息类型」按默认口径初始化，可在本页调整'
FROM src t
WHERE t.code_int > 0
  AND NOT EXISTS (SELECT 1 FROM dbo.uf_oaones_msg_type m WHERE m.std_type = t.code_int);
PRINT '补齐 uf_oaones_msg_type：本次新增 ' + cast(@@ROWCOUNT as varchar(20)) + ' 行';
GO
