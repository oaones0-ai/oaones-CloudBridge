/* =====================================================================
   ★ 口径变更（消息类型改为引用泛微标准页）：
     本脚本属于“一行一个来源编号”的老口径；现在**消息类型 / 分组 / 消息来源**都以泛微标准后台
     「应用中心 → 消息类型」为准（页面 /spa/smallApp/static4engine/engine.html#/ecAndEM/messagetypeconfig），
     本模块的推送设置按“标准消息类型”维护（uf_oaones_msg_type.std_config_id），
     页面也不再提供“补齐全部消息类型”（标准页新建类型后本页列表自动出现）。
     本脚本仍可执行：写入的行是“未纳入标准消息类型的独立来源”的默认推送设置（std_config_id = '0'）；
     若这些来源在标准页里已经挂到了某个消息类型上，执行后由号 1 脚本第 17.1 段的“按标准消息类型归位”
     把它们归到那个类型上（老库也可单独重跑那段，幂等）。
   OAones：把泛微标准消息中心里的“全部消息类型”补齐到 uf_oaones_msg_type（企业微信，type=0）
   ★ 三库同步（skill.md 第二节）：本文件必须与 SQLServer/、Oracle/ 下的同名脚本一起改
     （表、字段、索引/唯一键、初始化数据一处不漏），改完核对三库的列与索引一致。
   MySQL 5.7 / 8.0 + E9
   与 SQLServer/4.20260928alter_oaones_msg_type_init_all.sql 功能一致。

   与钉钉（2 号）、飞书（3 号）的同名脚本成对：那张表三个连接器共用
   （设置类表结构可复用时不新建表），用 type 区分（0 企微 / 1 钉钉 / 2 飞书）。

   背景：
     1.20260928oaones_tables.sql 只初始化了流程 / 会议的 12 条常用映射，
     而泛微标准消息中心（后台 应用中心 -> 消息类型，平台表 ECOLOGY_MESSAGE_TYPE）
     里还有大量类型（意见征询 / 流程删除暂停撤销 / 日程 / 协作 / 任务 / 公文 /
     项目 / 库存 / 人力 / 微博 …）。本脚本把“平台有、映射表没有”的类型一次补齐。

   同一件事的可视化入口是页面「消息类型映射 -> 补齐全部消息类型」
   （action msg.type.init，见 MsgTypeService#initStandardTypes），两边口径一致。

   默认口径（与 MsgTypeService#defaultDef 一致）：
     全部标准消息类型 = 文本卡片（textcard）+ 不处理卡片（none），不建待办卡片、
                        不配置任何卡片按钮（企微客户端只有一个「详情」按钮，点开跳该业务的 OA 页面）；
     流程 / 任务类另外给一套现成的标题与正文模板（20/21/63 用 ${targetName}，
     25 已办结、27 已删除、34 已暂停、35 已撤销、38 已收回），其余用 ${title}/${context}。
     状态 yx = 0（启用）、sort_no = 标准类型编号（typecode）、
     备注写明由补齐功能初始化，之后照常在页面上调整模板与状态。

   名称口径与平台“消息类型”浏览按钮一致：HtmlLabelInfo（简体中文 languageid = 7）
   优先；取不到再读 sourcename（多语言标记串取简体中文那一段，不含标记的纯文本名称原样用）；
   两者都没有才用“消息类型 <typecode>”占位（口径与页面下拉、Java 侧 multiLang 一致）。

   幂等：靠 uf_oaones_msg_type 的唯一键 (std_type, type) + INSERT IGNORE，
         已存在同 std_type 的企微映射一律跳过（含已停用的与改过模板的），可重复执行；
         误删某条映射、或平台新增了消息类型后重跑一次即可回齐。

   前置：先执行同目录 1.20260928oaones_tables.sql（建 uf_oaones_msg_type）。
   平台表名：ECOLOGY_MESSAGE_TYPE、HtmlLabelInfo——MySQL 库一般是小写写法，
   若你的库区分表名大小写（Linux 下 lower_case_table_names=0）且实际是小写，
   把下面两处表名改成库里的实际写法即可。

   注：SQL Server 版这条脚本的 INSERT 列里没有 type（靠列默认值 0），
       MySQL 版显式写 type = 0，效果相同、语义更直观；
       又因唯一键是 (std_type, type)，这里只跳过“同平台”的映射——
       存在钉钉映射（type=1）不会挡住企微映射（type=0）的补齐（比 SQL Server 版更精确）。
   ===================================================================== */

-- 体检（可选，只读）：映射表里现有的企微映射数量
SELECT COUNT(1) AS wecom_mappings_before FROM uf_oaones_msg_type WHERE type = 0;

/* 补齐：source 里是“平台有、纯数字编号”的标准消息类型，用 NOT EXISTS 的等价写法
   （唯一键 (std_type, type) + INSERT IGNORE）跳过已配置的 std_type。 */
INSERT IGNORE INTO uf_oaones_msg_type
    (type, type_name, std_type, wx_msgtype, card_action,
     title_tpl, context_tpl, url_tpl, mobile_url_tpl, cover_url, btn_json,
     app_id, yx, sort_no, remark)
SELECT 0 AS type,
       -- 名称口径（与页面下拉完全一致）：① HtmlLabelInfo 简体中文标签 → ② sourcename
       -- （多语言站点里是 ~`~`7 中文`~`8 English`~`~ 这种编码串，取简体中文那一段；不含标记的
       --   纯文本名称原样用）→ ③ “消息类型 <typecode>”占位。
       -- 用子查询而不是 join，避免同一标签多语言/多行时把一条标准类型补成两行；
       -- 连的是 typename（标签索引 int），sourcename 只用来兜底取名（不 join、不转换）。
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
       -- 默认一律「文本卡片 + 不处理卡片」：不再默认建待办卡片，避免客户端出现点了没反应的卡片按钮
       'textcard' AS wx_msgtype,
       'none' AS card_action,
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
       -- 文本卡片不带卡片按钮；要改回任务卡片（OA 待办）请在本页配置形态、卡片动作与按钮 JSON
       NULL AS btn_json,
       0 AS app_id,
       0 AS yx,
       t.code_int AS sort_no,
       '由「补齐全部消息类型」按默认口径初始化，可在本页调整' AS remark
  FROM (
        -- typecode 在 ECOLOGY_MESSAGE_TYPE 里是 varchar(20)，只有纯数字才能当编号用：
        -- 直接 CAST 会在遇到非数字行时产生告警/截断（SQL Server 版对应的是 245 转换错误），
        -- 所以先按正则筛出纯数字，再算成 code_int，下面所有数字口径都用它。
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
