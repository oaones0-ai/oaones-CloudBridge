package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 会议 / 日程同步服务（一级菜单「会议同步」/「日程同步」）。
 *
 * <p>会议与日程共用一套实现，用 {@code biz}（0 会议 / 1 日程）区分；两个页面各自
 * 按当前平台（{@code type} 0 企微 / 1 钉钉 / 2 飞书）取租户与设置。</p>
 *
 * <p>职责：</p>
 * <ul>
 *   <li>「同步设置」：列出该平台租户 + 每个租户的同步开关与同步范围（分部）；</li>
 *   <li>「每个租户单独开启 / 关闭」：只改开关；</li>
 *   <li>「立即同步」/ 内置调度器：把分部下的 OA 会议 / 日程推到第三方平台，逐条写同步记录；</li>
 *   <li>「同步记录」：按时间 / 租户 / 状态 / 关键字查历史同步情况。</li>
 * </ul>
 */
public interface MtgSyncService {

    /**
     * 页面「同步设置」列表：该平台当前登录人可维护的租户 + 每个租户该业务的设置
     * （对齐 {@code biz + type}，租户没配过时返回默认值：开启、无范围）。
     */
    JSONArray listSettings(int biz, int type, String operatorId);

    /** 保存某个「业务 + 平台 + 租户」的设置（开关 + 同步范围）。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 「每个租户单独设置开启和关闭」：只改开关。 */
    JSONObject setEnabled(int biz, int type, String cfgId, int enabled, String operatorId);

    /**
     * 设置某个「业务 + 平台 + 租户」的**同步方向**（两个复选框，可双选，默认双选）：
     * {@code oa2Third} = OA 同步到第三方 / {@code third2Oa} = 第三方同步到 OA。
     * 租户级方向是个人方向的**上限**（见 {@link #userSetDirection}）。
     */
    JSONObject setDirection(int biz, int type, String cfgId, int oa2Third, int third2Oa, String operatorId);

    /**
     * 设置某个「业务 + 平台 + 租户」的**绑定应用**：本租户同步时调平台日程接口用哪个
     * “应用绑定”记录（{@code uf_oaones_app_bind.id}）。
     *
     * <p>平台日程接口要求用**有日程权限的应用自己的 Secret** 换 access_token
     * （租户配置里的通讯录 Secret 通常没有日程权限），所以支持多应用的租户要在这里逐租户指定；
     * {@code appId = 0} 表示未指定，回退租户配置的 Secret + AgentId。</p>
     *
     * <p>保存前校验：应用必须存在、属于该租户、属于该平台（应用绑定记录挂在租户下，租户用 type 区分平台）。
     * 租户还没配过该业务时先落一条“未开启”的设置再写 {@code app_id}，不会顺手把同步打开。</p>
     */
    JSONObject setApp(int biz, int type, String cfgId, int appId, String operatorId);

    /**
     * 「校验应用」：用该应用（或租户配置）的凭据真实调一次平台接口，验证
     * corpId / Secret / AgentId 是否正确、该应用是否具备日程（日历）权限。
     *
     * <p>先换 access_token（错 Secret 立刻能看出来），再用该租户已绑定的一个平台成员当日历
     * 拉一小段日程：拉得到（含“没有日程”的空结果）说明日程权限正常；
     * 没有已绑定成员时只做 token 校验，并在返回里说明。</p>
     */
    JSONObject checkApp(int biz, int type, String cfgId, int appId, String operatorId);

    /**
     * 「个人同步方向」只读列表：列出非“遵循系统设置”（{@code dir_mode = 1}）的个人及其具体方向，
     * 按登录人可维护租户过滤（带 {@code cfg_id} 时校验租户维护人）。
     * <b>只读</b>：管理员不能改，只能本人到个人账号中心改。
     */
    JSONObject listUserDirPref(JSONObject params, String operatorId);

    /** 立即同步一次（页面行内按钮）：按该租户的设置把分部下的会议 / 日程推到平台。 */
    JSONObject runNow(int biz, int type, String cfgId, String operatorId);

    /** 「同步记录」列表（分页由页面用 js/table.js 做，这里返回命中的全部行，最多 limit 条）。 */
    JSONObject listLog(JSONObject params, String operatorId);

    /**
     * 「同步清单」列表：按 **OA 对象（会议 / 日程）维度**取每个对象的最近一条同步结果
     * （一行一个对象），页面据此看“这条会议同步到第三方平台的结果”，并对“跳过 / 失败”行重推。
     */
    JSONObject listItems(JSONObject params, String operatorId);

    /**
     * 「重推」：对某一条 OA 会议 / 日程（指定租户）重新同步一次。
     *
     * <p>不走“已同步过就跳过”的判断（跳过 / 失败的行本来就没有成功记录），
     * 每次重推都会像第一次一样写一条新的同步记录（`biz + type + cfgid + oa_id` 不变、
     * 说明里带“重推”前缀），因此「同步记录」里能看到“第一次 + 后续每次重推”的完整轨迹。</p>
     */
    JSONObject retry(JSONObject params, String operatorId);

    /**
     * 「报文」预览：按 {@code biz + type + cfg_id + oa_id} 组装**实际会下发给第三方平台的请求报文**
     * （HTTP 方法 + 接口路径 + 请求体 + 逐字段简要说明），<b>只组装、不发送</b>。
     *
     * <p>用与同步推送同一份组装代码（{@code MtgPushAdapter#buildRequest}），因此页面看到的报文
     * 就是同步时会下发的报文；报文里不含 access_token（真正调用时实时换取）。</p>
     */
    JSONObject payload(JSONObject params, String operatorId);

    /**
     * 内置调度器调用：**跑一轮同步**（计划任务到点时触发）。
     *
     * <p>一轮 = 两个方向各走一遍：</p>
     * <ol>
     *   <li><b>OA → 第三方（推送）</b>：新对象推“新增”，OA 侧改过的推“更新”，
     *       OA 侧删掉 / 取消的把平台对应日程删掉；</li>
     *   <li><b>第三方 → OA（拉取）</b>：平台新建的在 OA 新建，平台改过的更新 OA，
     *       平台删掉的把 OA 记录标记删除。</li>
     * </ol>
     *
     * <p>双向同步但<b>不做反向更新</b>（谁创建谁维护）：对方的改动只写一条同步记录
     * （说明里写明“不做反向更新”），不写回源端；只有<b>创建端</b>的修改 / 删除会同步到接收方。
     * 每条对象都用映射表 {@code uf_oaones_mtg_link} 的内容快照（{@code oa_hash} / {@code wx_hash}）
     * 比对，<b>没变就不调接口、也不写记录</b>。</p>
     *
     * @return 一轮的统计（推送 / 拉取各自的 扫描 / 新增 / 更新 / 跳过 / 失败 / 删除）
     */
    JSONObject syncRound();

    /**
     * 页面「计划任务」：读取当前配置（同步方式、时间段明细 / 高级 cron、工作日口径、启用开关、
     * 规则文案与下次执行时间）。
     *
     * <p>配置存 {@code uf_oaones_mtg_cron} 单行 + 时间段明细 {@code uf_oaones_mtg_plan_item}；
     * 表里没有行时返回 {@code oaones.properties} 里的默认值（白天 60 分钟 / 夜间 120 分钟两档 cron，
     * 启用），并给出「恢复默认」用的默认时间段。返回值里还带工作日口径的考勤组候选
     * （泛微标准考勤 {@code kq_group}）与今天的“工作日 / 非工作日”结论。</p>
     */
    JSONObject syncPlan();

    /**
     * 保存页面「计划任务」：
     *
     * <ul>
     *   <li>{@code mode}：0 按时间段（默认）/ 1 高级（cron）；</li>
     *   <li>{@code items}：按时间段模式的明细（JSON 数组，一行 = 适用日 + 起止时间 + 轮询间隔），
     *       同一适用日下不允许重叠，「每天」与「工作日 / 非工作日」不能混配；</li>
     *   <li>{@code day_cron} / {@code night_cron} / {@code cron_day_type}：高级模式的两档
     *       **Quartz cron（秒 分 时 日 月 周）** 与执行日；</li>
     *   <li>{@code workday_group_id}：工作日口径用哪个考勤组（0 = 不指定，按周一至周五）；</li>
     *   <li>{@code enabled}：1 启用 / 0 停用。</li>
     * </ul>
     *
     * <p>两种方式的配置都会保存（页面来回切不丢值）；当前方式那一套必须配出可执行的时间。
     * cron 校验口径与同步中心一致：能被 Quartz 解析、相邻两次执行的间隔不小于
     * {@code OaonesConst.MTG_PLAN_MIN_MINUTES} 分钟（频率下限）。保存后立即生效
     * （调度器每轮重新读一次，同时清掉考勤组 / 节假日缓存）。</p>
     */
    JSONObject saveSyncPlan(JSONObject params, String operatorId);

    /**
     * 「预测后 N 次」：按表单里的配置（时间段明细或白天 / 夜间两套 cron）试算
     * **实际会执行的时间**（不落库、不分权）。
     *
     * <p>返回 {@code runs}（时间串）与一一对应的 {@code labels}（命中的那一档，
     * 例“工作日 08:30–12:00 每 30 分钟一次” / “白天”），以及规则文案 {@code rule_text}；
     * 与调度器的到点判断共用同一份代码，因此**预测出来的就是实际会跑的时间**。</p>
     */
    JSONObject planPreview(JSONObject params);

    /**
     * 计划任务的频率文案（个人账号中心也会显示同一句话）：
     * 按时间段模式形如“同步频率（OA ⇄ 第三方平台）：工作日 08:30–12:00 每 30 分钟一次、…；非工作日 …。”，
     * 高级模式形如“同步频率（OA ⇄ 第三方平台）：高级模式（cron）白天 9:00–18:00 每小时一次，其余时段 …。”。
     */
    String planRuleText();

    /**
     * 内置调度器调用：按「租户勾了「第三方→OA」」逐个租户从第三方平台拉取日程 / 事件，
     * 平台新建的在 OA 新建记录、此前从 OA 推过去的按平台改动更新 OA 记录。
     *
     * <p>是否“到点”由调度器按规则判断（见 {@code oaones.properties} 的 {@code mtg.pull.*}：
     * 默认 9:00–18:00 每小时一次、其余时段每 2 小时一次），本方法被调用即执行一轮拉取。</p>
     *
     * @return 本轮统计（扫描租户 / 新增 / 更新 / 跳过 / 失败）
     */
    JSONObject pullNow();

    /**
     * 页面「立即拉取」：按租户维护人校验后，对该「业务 + 平台 + 租户」拉取一轮「第三方→OA」。
     *
     * @return 本轮统计（扫描 / 新增 / 更新 / 跳过 / 失败）
     */
    JSONObject pullCfgNow(int biz, int type, String cfgId, String operatorId);

    // ==================================================================
    // 个人同步开关（个人账号中心：本人自助，不做租户维护人分权）
    // ==================================================================

    /**
     * 个人账号中心「会议 / 日程同步状态」：按当前平台（{@code type}）返回<b>平台全部租户</b>的
     * 同步状态（登录人本人口径）——
     * 每个租户给该业务（会议 / 日程）的租户开关（{@code mtg_tenant} / {@code sch_tenant}）
     * 与本人开关（{@code mtg_personal} / {@code sch_personal}）。
     *
     * <p>个人开关默认允许（没落过行 = 1）；租户开关只有“已开启”才算允许
     * （没有配过 / 已关闭 = 0）。真正同步的条件是「租户开启 <b>且</b> 个人允许」。</p>
     */
    JSONObject userStates(int type, String oaUserId);

    /**
     * 个人账号中心：设置本人对某个「业务 + 平台 + 租户」的同步开关（{@code enabled} 1 允许 / 0 不同步）。
     *
     * <p>本人自助，不做租户维护人分权；但要求该租户该业务<b>已由后台开启</b>
     * （与页面口径一致：租户没允许时个人不设置）。落库后返回该租户的最新状态。</p>
     */
    JSONObject userSetState(JSONObject params, String oaUserId);

    /**
     * 个人账号中心：设置本人的**同步方向**——
     * {@code dir_mode} 0 遵循系统设置（默认）/ 1 指定同步方向（{@code dir_oa2third} / {@code dir_third2oa}）。
     *
     * <p>本人自助，不做租户维护人分权；与个人开关同一口径：要求租户该业务已开启。
     * 生效方向 = 租户勾选 <b>且</b> 个人勾选（个人「遵循系统设置」时直接取租户方向）。</p>
     */
    JSONObject userSetDirection(JSONObject params, String oaUserId);
}
