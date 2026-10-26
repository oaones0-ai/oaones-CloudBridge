package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * 同步中心“同步设置”服务（业务表名取 oaones.properties#oaones_sync_plan_table）。
 *
 * <p>本服务是三个连接器（企微 / 钉钉 / 飞书）<b>共用</b>的：设置表用 type 区分平台，
 * 页面（一级菜单“同步中心”）按平台 + 租户维护；同步引擎在每次执行时按
 * {@link #get(int, String, int)} 取本租户本周期的口径（成员没有部门时怎么处理）。</p>
 */
public interface SyncPlanService {

    /**
     * 规则列表；type/cfgId/direction 可为空表示不过滤，allowedCfgIds 为空表示不限租户。
     *
     * <p>同一个“平台 + 租户 + 方向”可以有多条规则（各自的同步范围 / cron / 口径不同），
     * 因此返回的是<b>多条</b>；带 cfgId 查询时同时返回该租户自己的规则与“全局默认配置”（cfgid = 0）的规则，
     * 由页面按“租户自己配了该方向就不再套全局默认”的口径展示。</p>
     */
    JSONArray list(String type, String cfgId, String direction, List<Integer> allowedCfgIds);

    /** 某个“平台 + 租户 + 方向”的第一条规则；没配过返回 null（cfg_id = 0 表示“全局默认配置”）。 */
    JSONObject get(int type, String cfgId, int direction);

    /** 按主键取一条规则（页面行内操作与控制器分权校验用）；不存在返回 null。 */
    JSONObject getById(String id);

    /**
     * 该租户该方向<b>实际生效</b>的口径：租户自己配了就用租户的，没配就用“全局默认配置”（cfg_id = 0）。
     *
     * <p>同步引擎（企微 / 钉钉 / 飞书三个连接器）统一按本方法取口径，
     * 这样“只维护一条全局默认配置”也能约束所有未单独配置的租户。</p>
     *
     * <p>同一“租户 + 方向”有多条规则时，本方法取<b>第一条（id 最小）</b>规则的口径：
     * 它只服务于“不带范围的整租户同步”这一条老路径；多条规则各自的执行（计划任务 /
     * 立即执行一次）一律按该条规则自己的 id 与同步范围走，不受本方法影响。</p>
     *
     * @return 生效的设置行；租户与全局都没配时返回 null（按默认约束：不自动创建部门、只回写基础信息）
     */
    JSONObject effective(int type, String cfgId, int direction);

    /**
     * 保存设置（同一“租户 + 方向”只有一条：没配过插入、配过更新，不会出现多条 A / 多条 B）。
     *
     * <p>参数带 {@code id} 时更新该条规则；不带（或为主键以外的空值）时<b>新增一条规则</b>——
     * 同一个“租户 + 方向”可以保存多条规则，各自维护自己的同步范围、cron 与口径。</p>
     *
     * <p><b>计划任务</b>有两种方式（{@code plan_mode}）：<b>轮询间隔（分钟，{@code interval_min}）</b>与
     * <b>高级模式（cron 表达式，{@code cron}）</b>；两种方式的取值一起保存，页面来回切不丢值，
     * 执行时按 {@code plan_mode} 取用其中一套。启用时必须有合法的轮询间隔（5 ~ 1440 分钟）
     * 或合法的 cron 表达式。</p>
     *
     * <p>校验：租户必填、方向为 0/1、
     * 平台→OA 方向指定默认部门时该 OA 部门必须存在；
     * 保存成功时按当前方式重算下次执行时间（轮询间隔 = 保存时刻 + N 分钟）。</p>
     *
     * <p>同步范围（{@code scope_set = 1}）：把页面上选的 OA 分部/部门与平台部门解析成
     * “含下级”的范围后保存，计划任务与“立即执行一次”每次执行都按这条<b>映射关系</b>同步
     * （{@code scope_set = 0} 表示不限制范围，沿用历史的全量同步）。</p>
     *
     * <p><b>首次检测</b>：该映射关系是新的（新设置，或范围发生了变化）时，检测范围内（含下级）
     * 是否已有部门绑定 / 成员绑定；检测到且用户还没确认过时，<b>不保存</b>并返回
     * {@code {need_confirm: true, conflicts: [...], message: "..."}}，由页面弹框询问是否强制同步；
     * 用户确认后带 {@code force = 1} 再保存，保存成功即置 {@code bind_forced = 1}，之后不再重复询问。</p>
     */
    JSONObject save(JSONObject params, String operatorId);

    /** 删除该条规则（按主键；同一档的其它规则不受影响）。 */
    JSONObject delete(String type, String id, String operatorId);

    /**
     * 暂停 / 启动某一条规则的计划任务（同步设置列表行尾的“暂停 / 启动”按钮）。
     *
     * <p>只改启停开关：同步方向、同步范围、cron 与“成员没有找到部门”的口径一律不动，
     * 也不会触发“首次检测”（范围内已有绑定关系）的弹框。</p>
     *
     * <p>启动（{@code enabled = 1}）时按这条规则已保存的**计划任务方式**重算“下次执行时间”
     * （轮询间隔 = 当前时刻 + N 分钟；高级模式 = cron 往后推一次），
     * 该方式对应的配置缺失 / 非法时拒绝启动；
     * 暂停（{@code enabled = 0}）时把“下次执行时间”置空，内置调度器不会再触发该档
     * （历史同步日志与“最近执行”信息保留，随时可以再启动）。</p>
     *
     * @param enabled 1 = 启动 / 0 = 暂停
     */
    JSONObject setEnabled(String type, String id, int enabled, String operatorId);

    /**
     * 「预测后 10 次」：按表单里选定的<b>计划任务方式</b>试算后 count 次执行时间（默认 10 次），
     * 与调度器到点判断共用同一份代码（页面所见即实际会跑的时间）。
     *
     * <p>轮询间隔方式（{@code mode = 0}）：从当前时刻起每 {@code intervalMin} 分钟一次；
     * 高级模式（{@code mode = 1}）：按 cron 表达式往后推。配置缺失 / 非法时抛出业务异常。</p>
     *
     * @param mode        计划任务方式（{@code OaonesConst.SYNC_PLAN_MODE_*}）；空值按“有 cron 就是高级模式”兼容
     * @param cron        高级模式的 cron 表达式（Quartz 口径）
     * @param intervalMin 轮询间隔（分钟，5 ~ 1440）
     */
    JSONArray planPreview(String mode, String cron, String intervalMin, int count);

    /** 计划任务用：已启用且已到点的设置（最多 limit 条）。 */
    JSONArray listDue(String now, int limit);

    /**
     * 内置调度器（{@code job.OaonesSyncPlanScheduler}）的一次扫描：把已到点的设置逐条<b>认领</b>后执行。
     *
     * <p>“认领”= 用条件更新把该行的 {@code next_run_time} 从旧值改成下一个执行时间：
     * 只有更新成功的实例才真正执行，因此<b>多实例部署、或同时配了 E9 后台计划任务时都不会重复跑</b>。</p>
     *
     * @return 本次实际执行的设置条数
     */
    int runDuePlans(int limit);

    /** 立即执行一次该条规则的同步（按已保存设置的口径），并把结果回写到该规则行。 */
    JSONObject runNow(String type, String id, String operatorId);

    /**
     * 计划任务用：执行一条已到点的设置，并回写“最近执行 + 下次执行时间”。
     *
     * @param plan {@link #listDue(String, int)} 返回的设置行
     *             （含 id / type / cfg_id / direction / plan_mode / interval_min / cron）
     * @return 该次同步的结果（含 log_id），页面/日志可直接引用
     */
    JSONObject runDue(JSONObject plan);

    /** 计划任务执行后回写设置行（最近执行时间 / 日志ID / 结果 / 下次执行时间）。 */
    void markRun(String id, String runTime, Integer logId, String result, String nextRun, String operatorId);

    /** 当前登录人可维护的租户ID（按平台取，用于设置列表分权过滤）。 */
    List<Integer> maintainableCfgIds(String type, String operatorId);
}
