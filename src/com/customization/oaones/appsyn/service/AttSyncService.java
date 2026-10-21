package com.customization.oaones.appsyn.service;

import com.customization.oaones.appsyn.entity.AttPlanItem;
import com.customization.oaones.appsyn.entity.AttSyncCfg;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Date;
import java.util.List;

/**
 * 考勤同步服务（独立一级菜单「考勤同步」，页面 /oaones/attSync/attSync.jsp）。
 *
 * <p>把第三方平台（企微 / 钉钉 / 飞书）的考勤打卡数据写进 OA 的 E9 标准考勤打卡表，
 * 并支持反向（OA 的打卡写到第三方平台）；两个方向各自独立配置、独立执行、独立记日志。</p>
 *
 * <p>职责：设置（一行 = 平台 + 租户 + 方向，可多条）、计划任务（每条设置一份）、
 * 立即执行一次（单条 / 整批，可临时指定时间范围）、日志（批次 + 打卡明细）、重推、
 * 考勤应用校验、内置调度器到点判断与一轮执行。</p>
 */
public interface AttSyncService {

    /**
     * 页面「同步设置」数据：当前平台下**本人可维护**的租户 + 每个租户下的设置行。
     *
     * <p>返回 {@code tenants}（租户级开关、企业标识、考勤应用候选、反向写入能力说明）
     * 与 {@code rows}（设置行：方向、范围文案、数据类型、时间口径、计划任务摘要、
     * 最近执行 / 连续失败 / 告警文案）。</p>
     */
    JSONObject list(int type, String operatorId);

    /** 保存一条设置（新增或更新）：范围、数据类型、时段、时间口径、考勤应用、方向、启停、计划任务。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 删除一条设置（时间段明细一并清掉；历史日志保留）。 */
    JSONObject remove(JSONObject params, String operatorId);

    /** 单条设置启停（停用后只允许手工执行）。 */
    JSONObject setEnabled(int id, int enabled, String operatorId);

    /** 租户级开关（平台 + 租户整级启停，需求 3.10.3）：只改开关，不动历史日志。 */
    JSONObject setTenantEnabled(int type, int cfgId, int enabled, String operatorId);

    /**
     * 指定该<b>租户</b>的「考勤应用」（应用绑定记录ID；0 = 清除 → 该租户不执行同步）。
     *
     * <p>考勤应用属于租户、不属于单条设置：页面上在租户卡片上选一次即可，
     * 新增同步设置时不再重复选。选好后<b>立刻真调一次平台考勤接口</b>（token + 考勤组 +
     * 至少一条打卡 / 排班）把结论落到租户行上，返回 {@code ok / message / groups / punch_count}。</p>
     */
    JSONObject setTenantApp(int type, int cfgId, int appId, String operatorId);

    /**
     * 「校验」：用该租户当前考勤应用的凭据真调一次平台考勤接口，
     * 结论写回租户行（页面上的「重新校验」用它；保存设置时也复用同一份逻辑）。
     */
    JSONObject checkApp(int type, int cfgId, String operatorId);

    /**
     * 「立即执行一次」：默认用该设置配置的时间口径，也允许临时指定一次时间范围
     * （{@code win_from / win_to / win_mode}，不落库、只影响本次）。
     * <b>不改变计划任务的配置与下次执行时间</b>（需求 3.7.1）。
     */
    JSONObject runNow(JSONObject params, String operatorId);

    /** 「整批执行」：当前平台 + 租户下所有启用中的设置各跑一轮（需求 3.7.2）。 */
    JSONObject runBatch(int type, int cfgId, String operatorId);

    /**
     * 「重推」：按明细行定位（平台 + 租户 + 人员 + 打卡时间 + 类型），不受当前时间窗限制；
     * 每次重推新写一条明细（说明带“重推：”前缀），不覆盖历史（需求 3.8.5）。
     */
    JSONObject retry(JSONObject params, String operatorId);

    /** 「同步日志 · 批次」列表（平台 / 租户 / 方向 / 状态 / 执行时间 / 关键字）。 */
    JSONObject logs(JSONObject params, String operatorId);

    /** 「同步日志 · 打卡明细」列表（可按批次 / 人员 / 结果 / 状态 / 打卡时间 / 关键字）。 */
    JSONObject items(JSONObject params, String operatorId);

    /**
     * 「预测后 10 次」：按页面上的计划任务配置（时间段明细 / cron）试算实际会执行的时间，
     * 每条标明命中的时段 / 档位（与调度器的到点判断共用同一份代码）。不落库、不分权。
     */
    JSONObject planPreview(JSONObject params);

    /** 范围选择框的候选：OA 分部 + OA 部门（按登录人的泛微标准分权过滤）。 */
    JSONObject orgOptions(String operatorId);

    /** 人员候选（姓名 / 登录名 / 工号关键字；按登录人的泛微标准分权过滤）。 */
    JSONArray oaUsers(String keyword, String operatorId);

    /** 设置行上的健康度说明（最近执行 / 结果 / 连续失败 / 告警文案），页面直接渲染。 */
    JSONObject health(int id);

    /**
     * 内置调度器调用：扫描启用中的设置，**到点的各跑一轮**（每条设置按自己的范围 / 数据类型 /
     * 时间口径执行，多条设置互不影响，一条失败不阻塞其它，需求 3.3.7）。
     */
    JSONObject syncRound();

    /**
     * 某条设置现在是否到点（内置调度器每轮调用；与「预测后 10 次」共用同一份计划判断）。
     *
     * @param cfg      设置行
     * @param items    该设置的时间段明细
     * @param lastRun  上次执行时间（null = 从未执行，立即到点）
     */
    boolean planDue(AttSyncCfg cfg, List<AttPlanItem> items, Date lastRun, Date now);
}
