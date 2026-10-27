package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkAttApi;
import com.customization.oaones.appsyn.api.feishu.FeishuAttApi;
import com.customization.oaones.appsyn.api.wecom.WeComAttApi;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesCronExpr;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.OaonesWorkday;
import com.customization.oaones.appsyn.dao.AttPlanItemMapper;
import com.customization.oaones.appsyn.dao.AttSignMapper;
import com.customization.oaones.appsyn.dao.AttSyncMapper;
import com.customization.oaones.appsyn.dao.wecom.QywxConfigMapper;
import com.customization.oaones.appsyn.dto.AttAppOption;
import com.customization.oaones.appsyn.dto.AttMember;
import com.customization.oaones.appsyn.dto.AttOaPunch;
import com.customization.oaones.appsyn.dto.AttPunch;
import com.customization.oaones.appsyn.dto.AttPushRequest;
import com.customization.oaones.appsyn.entity.AttPlanItem;
import com.customization.oaones.appsyn.entity.AttSyncCfg;
import com.customization.oaones.appsyn.entity.AttSyncLink;
import com.customization.oaones.appsyn.entity.AttSyncLog;
import com.customization.oaones.appsyn.entity.AttSyncLogItem;
import com.customization.oaones.appsyn.entity.KqGroup;
import com.customization.oaones.appsyn.entity.wecom.QywxConfig;
import com.customization.oaones.appsyn.service.AttPullAdapter;
import com.customization.oaones.appsyn.service.AttPullContext;
import com.customization.oaones.appsyn.service.AttPushAdapter;
import com.customization.oaones.appsyn.service.AttPushContext;
import com.customization.oaones.appsyn.service.AttSyncService;
import com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 考勤同步服务实现（一级菜单「考勤同步」）。
 *
 * <p>一轮执行的完整口径（需求 3.3.7 / 3.6 / 4.x）：</p>
 * <ol>
 *   <li>前置：功能总开关 → 设置启用 → 租户级开关 → **考勤应用必须指定且可用**（不指定则不执行，
 *       也不静默回退到租户的通讯录 Secret）；任一不满足记批次失败并写明原因；</li>
 *   <li>范围：分部 / 部门 / 人员三个维度 OR 并集（都为空 = 该租户全部人员），
 *       选中分部 / 部门自动含下级；未绑定平台账号 / 已离职 / 不在范围的人在明细里写**固定文案**的跳过原因；</li>
 *   <li>时间窗：当天 / 前一天 / 最近 N 天 / 任意范围；按**打卡时间**判定是否在窗口内，
 *       跨天班次按查询日期区间取数（向前多取一天）；</li>
 *   <li>第三方 → OA：拉取打卡 → 过滤 → **先删后插**（只删本平台来源 + 本次时间窗内的导入记录）→
 *       写入 E9 标准打卡表（isimport = 1、来源 = 平台标识、补卡用 card: 来源）→ 写映射 →
 *       **只对本次真正写入 / 变更的人员与日期触发标准格式化**；</li>
 *   <li>OA → 第三方：只取 OA 侧真实产生的打卡（排除本功能导入回来的），按人员绑定换算平台账号后写入平台
 *       （direct / card / none 三级方案，未实测开通的一律显式跳过，不做假成功）；</li>
 *   <li>收尾：批次计数与结果落库、设置行健康度（连续失败次数）更新、达到阈值给显式告警文案。</li>
 * </ol>
 *
 * <p>幂等：同一时间窗反复执行不重复插入（映射表 + 先删后插）；同一“平台 + 租户 + 方向”同一时刻
 * 只允许一个执行实例（手工与定时撞车时提示“已在执行中”）。</p>
 */
public class AttSyncServiceImpl implements AttSyncService {

    /** 正在执行中的“平台 + 租户 + 方向”标记（需求 3.7.5：同一时刻只允许一个执行实例）。 */
    private static final Map<String, Long> RUNNING = new ConcurrentHashMap<String, Long>();
    /**
     * 租户级行的固定名称（同一“平台 + 租户”只保留一行）。
     *
     * <p>这一行（{@code row_type = 1}）同时承载两件事：**该租户的整级启停开关**（需求 3.10.3）
     * 与**该租户的考勤应用 + 校验结论**（考勤应用与租户绑定，新增同步设置时不再重复选，
     * 2026-10-15 现场反馈）。</p>
     */
    private static final String TENANT_SWITCH_NAME = "__tenant__";
    private final ConfigServiceImpl configService = new ConfigServiceImpl();

    /* ------------------------------------------------------------------
       租户级行（row_type = 1）：考勤应用 + 校验结论 + 整级开关
       ------------------------------------------------------------------ */

    /**
     * 造一条租户级行，**所有 NOT NULL 列都给显式值**。
     *
     * <p>为什么必须一个个填：{@code uf_oaones_appsyn_att_cfg} 的 {@code data_types / sign_periods /
     * win_mode / app_id / plan_mode / cron_day_type / enabled / check_state} 都是 {@code NOT NULL}，
     * 而 DDL 里的 DEFAULT 只对“insert 时压根不写这一列”生效——{@code insertCfg} 是把这些列
     * 全部列出来、值取实体字段的，实体字段为 null 就是显式插 NULL，直接报
     * {@code Cannot insert the value NULL into column 'data_types'}（2026-10-15 现场点「整体停用」
     * 就是这个原因：租户行是那一刻才第一次建的）。</p>
     */
    private AttSyncCfg newTenantRow(int type, int cfgId, int appId, int enabled, String operatorId) {
        String now = OaonesUtil.now();
        AttSyncCfg entity = new AttSyncCfg();
        entity.setType(Integer.valueOf(type));
        entity.setCfgid(Integer.valueOf(cfgId));
        entity.setRowType(Integer.valueOf(OaonesConst.ATT_ROW_TENANT));
        entity.setCfgName(TENANT_SWITCH_NAME);
        entity.setDir(Integer.valueOf(OaonesConst.ATT_DIR_BOTH));
        entity.setOaAccountRule(Integer.valueOf(OaonesConst.ATT_OA_ACCOUNT_PRIMARY));
        entity.setDataTypes(Integer.valueOf(OaonesConst.ATT_DATA_NORMAL));
        entity.setSignPeriods(Integer.valueOf(OaonesConst.ATT_PERIOD_BOTH));
        entity.setWinMode(Integer.valueOf(OaonesConst.ATT_WIN_TODAY));
        entity.setWinDays(Integer.valueOf(OaonesProps.attWinLastDaysDefault()));
        entity.setAppId(Integer.valueOf(appId));
        entity.setPlanMode(Integer.valueOf(OaonesConst.ATT_PLAN_MODE_ITEM));
        entity.setPlanWorkdayGroupId(Integer.valueOf(0));
        entity.setCronDayType(Integer.valueOf(OaonesConst.ATT_PLAN_DAY_ALL));
        entity.setEnabled(Integer.valueOf(enabled));
        entity.setCheckState(Integer.valueOf(OaonesConst.ATT_CHECK_NONE));
        entity.setFailStreak(Integer.valueOf(0));
        entity.setModedatacreater(Integer.valueOf(OaonesUtil.i(operatorId, 0)));
        entity.setModedatacreatedate(now.length() >= 10 ? now.substring(0, 10) : now);
        entity.setModedatacreatetime(now.length() >= 8 ? now.substring(11) : "");
        entity.setModedatamodifydatetime(now);
        return entity;
    }

    /** 取该租户的租户级行（没有返回 null）。 */
    private AttSyncCfg tenantRow(int type, int cfgId) {
        return cfgByName(type, cfgId, OaonesConst.ATT_ROW_TENANT, TENANT_SWITCH_NAME);
    }

    /** 取该租户的租户级行，没有就按缺省值建一条（返回带 id 的行）。 */
    private AttSyncCfg tenantRowOrCreate(int type, int cfgId, int appId, int enabled, String operatorId) {
        AttSyncCfg row = tenantRow(type, cfgId);
        if (row != null) {
            return row;
        }
        AttSyncCfg entity = newTenantRow(type, cfgId, appId, enabled, operatorId);
        Map<String, Object> p = OaonesParam.of();
        p.put("e", entity);
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.insertCfg(p));
        return entity;
    }

    /** 该租户的考勤应用ID（0 = 没指定；考勤应用与租户绑定，设置行只是快照）。 */
    private int tenantAppId(int type, int cfgId) {
        AttSyncCfg row = tenantRow(type, cfgId);
        return row == null || row.getAppId() == null ? 0 : row.getAppId().intValue();
    }

    /** 该租户的整级开关状态（没配过 = 开启，与页面同一口径）。 */
    private int tenantEnabled(int type, int cfgId) {
        AttSyncCfg row = tenantRow(type, cfgId);
        return row == null || row.getEnabled() == null ? 1 : row.getEnabled().intValue();
    }

    /* ==================================================================
       一、设置列表 / 保存 / 开关 / 考勤应用
       ================================================================== */

    @Override
    public JSONObject list(int type, String operatorId) {
        JSONObject out = new JSONObject();
        JSONArray tenants = new JSONArray();
        List<QywxConfig> rows = tenants(type);
        List<Integer> allowIds = new ArrayList<Integer>();
        for (QywxConfig tenant : rows) {
            if (tenant.getId() != null && canMaintain(String.valueOf(tenant.getId()), operatorId)) {
                allowIds.add(tenant.getId());
            }
        }
        Map<Integer, JSONArray> appMap = appOptions(type, allowIds);
        Map<Integer, AttSyncCfg> tenantSwitch = new LinkedHashMap<Integer, AttSyncCfg>();
        for (AttSyncCfg one : queryCfgs(type, allowIds, OaonesConst.ATT_ROW_TENANT)) {
            if (one.getCfgid() != null) {
                tenantSwitch.put(one.getCfgid(), one);
            }
        }
        for (QywxConfig tenant : rows) {
            if (tenant.getId() == null || !allowIds.contains(tenant.getId())) {
                continue;
            }
            AttSyncCfg sw = tenantSwitch.get(tenant.getId());
            JSONObject item = new JSONObject();
            item.put("cfg_id", String.valueOf(tenant.getId()));
            item.put("tenant_name", OaonesUtil.s(tenant.getCorpName()));
            item.put("corp_id", OaonesUtil.s(tenant.getCorpId()));
            item.put("qy", tenant.getQy() == null ? "1" : String.valueOf(tenant.getQy()));
            // 租户级开关：没配过 = 开启（与设置行自己的开关是两层，设置行默认关闭）
            item.put("tenant_enabled", sw == null || sw.getEnabled() == null ? "1" : String.valueOf(sw.getEnabled()));
            item.put("tenant_switch_id", sw == null || sw.getId() == null ? "0" : String.valueOf(sw.getId()));
            // 考勤应用与租户绑定（2026-10-15 现场反馈）：应用 + 校验结论都挂在租户行上，
            // 新增同步设置时不再重复选，页面在租户卡片上维护一次即可。
            item.put("app_id", sw == null || sw.getAppId() == null ? 0 : sw.getAppId().intValue());
            item.put("app_name", sw == null ? "" : OaonesUtil.s(sw.getAppName()));
            item.put("app_ready", sw != null && sw.isAppSecretReady() ? 1 : 0);
            item.put("check_state", sw == null || sw.getCheckState() == null
                    ? OaonesConst.ATT_CHECK_NONE : sw.getCheckState().intValue());
            item.put("check_msg", sw == null ? "" : OaonesUtil.s(sw.getCheckMsg()));
            item.put("check_time", sw == null ? "" : OaonesUtil.s(sw.getCheckTime()));
            JSONArray apps = appMap.get(tenant.getId());
            item.put("apps", apps == null ? new JSONArray() : apps);
            item.put("push_mode", OaonesProps.attPushMode(type));
            item.put("push_note", pushCapabilityNote(type));
            tenants.add(item);
        }
        out.put("tenants", tenants);
        out.put("rows", cfgRows(type, allowIds, tenantSwitch));
        out.put("groups", OaonesWorkday.groups());
        out.put("today_workday", OaonesWorkday.isWorkday(new Date(), 0));
        out.put("win_last_days", OaonesProps.attWinLastDaysDefault());
        out.put("alert_threshold", OaonesProps.attFailAlertThreshold());
        out.put("enabled", OaonesProps.attSyncEnabled());
        return out;
    }

    /** 设置行（含计划任务摘要、健康度、告警文案）。 */
    private JSONArray cfgRows(int type, List<Integer> allowIds, Map<Integer, AttSyncCfg> tenantRows) {
        JSONArray out = new JSONArray();
        List<AttSyncCfg> cfgs = queryCfgs(type, allowIds, OaonesConst.ATT_ROW_CFG);
        if (cfgs.isEmpty()) {
            return out;
        }
        Map<String, String> userNameMap = scopeUserNames(cfgs);
        List<Integer> ids = new ArrayList<Integer>();
        for (AttSyncCfg cfg : cfgs) {
            if (cfg.getId() != null) {
                ids.add(cfg.getId());
            }
        }
        Map<Integer, List<AttPlanItem>> itemsMap = new LinkedHashMap<Integer, List<AttPlanItem>>();
        if (!ids.isEmpty()) {
            List<AttPlanItem> items = OaonesMyBatis.call(AttPlanItemMapper.class,
                    m -> m.listByCfgIds(OaonesProps.attPlanItemTable(), ids));
            if (items != null) {
                for (AttPlanItem item : items) {
                    if (item.getCfgId() == null) {
                        continue;
                    }
                    List<AttPlanItem> list = itemsMap.get(item.getCfgId());
                    if (list == null) {
                        list = new ArrayList<AttPlanItem>();
                        itemsMap.put(item.getCfgId(), list);
                    }
                    list.add(item);
                }
            }
        }
        for (AttSyncCfg cfg : cfgs) {
            fillTenantApp(cfg, tenantRows);
            out.add(cfgJson(cfg, itemsMap.get(cfg.getId()), userNameMap));
        }
        return out;
    }

    /** 列表里所有设置已选人员ID → 姓名；一次查询覆盖全部设置，供编辑弹框直接回显姓名。 */
    private Map<String, String> scopeUserNames(List<AttSyncCfg> cfgs) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Set<Integer> ids = new LinkedHashSet<Integer>();
        for (AttSyncCfg cfg : cfgs) {
            ids.addAll(splitIds(cfg.getScopeUsers()));
        }
        if (ids.isEmpty()) {
            return out;
        }
        // Oracle 的 IN 列表上限为 1000，按 500 分批，避免多条设置累计后超限。
        List<Integer> all = new ArrayList<Integer>(ids);
        for (int from = 0; from < all.size(); from += 500) {
            int to = Math.min(from + 500, all.size());
            Map<String, Object> p = OaonesParam.of();
            p.put("userIds", new ArrayList<Integer>(all.subList(from, to)));
            List<AttMember> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listUserNames(p));
            if (rows != null) {
                for (AttMember row : rows) {
                    String name = OaonesUtil.s(row.getName());
                    if (row.getOaUserId() != null && name.length() > 0) {
                        out.put(String.valueOf(row.getOaUserId()), name);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 设置行自己没存考勤应用（老数据 / 迁移前保存的）时，按租户级行补齐。
     *
     * <p>考勤应用现在属于租户：设置行里的 {@code app_id} 只是保存时的快照（执行与日志都按它走），
     * 快照为空的按租户当前的应用展示与执行，避免页面上出现“租户配了应用、设置行却显示未指定”。</p>
     */
    private void fillTenantApp(AttSyncCfg cfg, Map<Integer, AttSyncCfg> tenantRows) {
        if (cfg.getAppId() != null && cfg.getAppId().intValue() > 0) {
            return;
        }
        AttSyncCfg row = tenantRows == null ? null : tenantRows.get(cfg.getCfgid());
        if (row == null || row.getAppId() == null || row.getAppId().intValue() <= 0) {
            return;
        }
        cfg.setAppId(row.getAppId());
        cfg.setAppName(row.getAppName());
        cfg.setAppSecretReady(row.isAppSecretReady());
    }

    /** 一条设置行的页面结构。 */
    private JSONObject cfgJson(AttSyncCfg cfg, List<AttPlanItem> items) {
        List<AttSyncCfg> one = new ArrayList<AttSyncCfg>();
        one.add(cfg);
        return cfgJson(cfg, items, scopeUserNames(one));
    }

    private JSONObject cfgJson(AttSyncCfg cfg, List<AttPlanItem> items, Map<String, String> userNameMap) {
        JSONObject item = new JSONObject();
        item.put("id", cfg.getId());
        item.put("type", cfg.getType());
        item.put("cfg_id", cfg.getCfgid());
        item.put("tenant_name", OaonesUtil.s(cfg.getTenantName()));
        item.put("corp_id", OaonesUtil.s(cfg.getCorpId()));
        item.put("cfg_name", OaonesUtil.s(cfg.getCfgName()));
        item.put("dir", cfg.getDir() == null ? 0 : cfg.getDir().intValue());
        item.put("dir_text", OaonesConst.attDirText(cfg.getDir() == null ? 0 : cfg.getDir().intValue()));
        item.put("scope_subs", OaonesUtil.s(cfg.getScopeSubs()));
        item.put("scope_depts", OaonesUtil.s(cfg.getScopeDepts()));
        item.put("scope_users", OaonesUtil.s(cfg.getScopeUsers()));
        JSONObject scopeUserNames = new JSONObject();
        for (Integer userId : splitIds(cfg.getScopeUsers())) {
            String name = userNameMap == null ? "" : OaonesUtil.s(userNameMap.get(String.valueOf(userId)));
            if (name.length() > 0) { scopeUserNames.put(String.valueOf(userId), name); }
        }
        item.put("scope_user_names", scopeUserNames);
        item.put("scope_text", OaonesUtil.s(cfg.getScopeText()));
        item.put("oa_account_rule", oaAccountRule(cfg));
        item.put("oa_account_rule_text", OaonesConst.attOaAccountRuleText(oaAccountRule(cfg)));
        item.put("data_types", cfg.getDataTypes() == null ? 0 : cfg.getDataTypes().intValue());
        item.put("data_text", OaonesConst.attDataText(cfg.getDataTypes() == null ? 0 : cfg.getDataTypes().intValue()));
        item.put("sign_periods", cfg.getSignPeriods() == null ? OaonesConst.ATT_PERIOD_BOTH
                : cfg.getSignPeriods().intValue());
        item.put("period_text", OaonesConst.attPeriodText(cfg.getSignPeriods() == null
                ? OaonesConst.ATT_PERIOD_BOTH : cfg.getSignPeriods().intValue()));
        item.put("win_mode", cfg.getWinMode() == null ? 0 : cfg.getWinMode().intValue());
        item.put("win_text", winText(cfg));
        item.put("win_from", OaonesUtil.s(cfg.getWinFrom()));
        item.put("win_to", OaonesUtil.s(cfg.getWinTo()));
        item.put("win_days", cfg.getWinDays() == null ? OaonesProps.attWinLastDaysDefault() : cfg.getWinDays());
        item.put("app_id", cfg.getAppId() == null ? 0 : cfg.getAppId().intValue());
        item.put("app_name", OaonesUtil.s(cfg.getAppName()));
        item.put("app_ready", cfg.isAppSecretReady() ? 1 : 0);
        item.put("plan_mode", cfg.getPlanMode() == null ? OaonesConst.ATT_PLAN_MODE_ITEM
                : cfg.getPlanMode().intValue());
        item.put("workday_group_id", cfg.getPlanWorkdayGroupId() == null ? 0
                : cfg.getPlanWorkdayGroupId().intValue());
        item.put("cron_day_type", cfg.getCronDayType() == null ? 0 : cfg.getCronDayType().intValue());
        item.put("day_cron", OaonesUtil.s(cfg.getDayCron()));
        item.put("night_cron", OaonesUtil.s(cfg.getNightCron()));
        item.put("enabled", cfg.getEnabled() == null ? 0 : cfg.getEnabled().intValue());
        item.put("check_state", cfg.getCheckState() == null ? 0 : cfg.getCheckState().intValue());
        item.put("check_msg", OaonesUtil.s(cfg.getCheckMsg()));
        item.put("check_time", OaonesUtil.s(cfg.getCheckTime()));
        item.put("last_run_time", OaonesUtil.s(cfg.getLastRunTime()));
        item.put("last_result", cfg.getLastResult() == null ? -1 : cfg.getLastResult().intValue());
        item.put("last_result_text", OaonesUtil.s(cfg.getLastResultText()));
        item.put("last_log_id", cfg.getLastLogId() == null ? 0 : cfg.getLastLogId().intValue());
        item.put("fail_streak", cfg.getFailStreak() == null ? 0 : cfg.getFailStreak().intValue());
        item.put("remark", OaonesUtil.s(cfg.getRemark()));
        item.put("items", itemsJson(items));
        item.put("rule_text", ruleText(cfg, items));
        Date lastRun = OaonesUtil.parseDateTimeSafe(cfg.getLastRunTime());
        Date next = nextRun(cfg, items, lastRun == null ? new Date() : lastRun);
        item.put("next_run", next == null ? "" : OaonesUtil.formatDateTime(next.getTime()));
        item.put("alert_text", alertText(cfg));
        item.put("push_mode", OaonesProps.attPushMode(cfg.getType() == null ? 0 : cfg.getType().intValue()));
        item.put("push_note", pushCapabilityNote(cfg.getType() == null ? 0 : cfg.getType().intValue()));
        return item;
    }

    /** 连续失败告警文案（需求 3.8.9）：达到阈值时给出显式提示，避免“只在日志里悄悄记一条”。 */
    private String alertText(AttSyncCfg cfg) {
        int streak = cfg.getFailStreak() == null ? 0 : cfg.getFailStreak().intValue();
        int threshold = OaonesProps.attFailAlertThreshold();
        if (streak < threshold) {
            return "";
        }
        return "已连续 " + streak + " 次同步失败，打卡数据可能未进入 OA，请立即排查"
                + "（看本行「同步日志」的批次与明细，失败原因带平台原文）";
    }

    /** 时间口径文案。 */
    private String winText(AttSyncCfg cfg) {
        int mode = cfg.getWinMode() == null ? OaonesConst.ATT_WIN_TODAY : cfg.getWinMode().intValue();
        if (mode == OaonesConst.ATT_WIN_RANGE) {
            return "任意时间范围：" + OaonesUtil.s(cfg.getWinFrom()) + " ~ " + OaonesUtil.s(cfg.getWinTo());
        }
        if (mode == OaonesConst.ATT_WIN_LAST_DAYS) {
            int days = cfg.getWinDays() == null ? OaonesProps.attWinLastDaysDefault() : cfg.getWinDays();
            return "最近 " + days + " 天";
        }
        return OaonesConst.attWinText(mode);
    }

    /** OA 多账户同步规则（历史行 / 未赋值按“只同步主账户”处理）。 */
    private int oaAccountRule(AttSyncCfg cfg) {
        return cfg == null || cfg.getOaAccountRule() == null
                ? OaonesConst.ATT_OA_ACCOUNT_PRIMARY : cfg.getOaAccountRule().intValue();
    }

    /** 该 OA 账户是否允许参与这条设置的同步。 */
    private boolean oaAccountAllowed(AttSyncCfg cfg, AttMember member) {
        return oaAccountRule(cfg) == OaonesConst.ATT_OA_ACCOUNT_ALL
                || (member != null && member.isPrimaryAccount());
    }

    /** 计划任务文案（含工作日口径）。 */
    private String ruleText(AttSyncCfg cfg, List<AttPlanItem> items) {
        int mode = cfg.getPlanMode() == null ? OaonesConst.ATT_PLAN_MODE_ITEM : cfg.getPlanMode().intValue();
        StringBuilder sb = new StringBuilder();
        if (mode == OaonesConst.ATT_PLAN_MODE_CRON) {
            String dayCron = OaonesUtil.s(cfg.getDayCron());
            String nightCron = OaonesUtil.s(cfg.getNightCron());
            if (dayCron.length() == 0 && nightCron.length() == 0) {
                sb.append("未设置计划任务（仅手工执行）");
            } else {
                sb.append("高级模式（cron）：白天 ").append(dayCron);
                sb.append("；夜间 ").append(nightCron);
                sb.append("；执行日 ").append(OaonesWorkday.dayTypeText(cfg.getCronDayType() == null
                        ? 0 : cfg.getCronDayType().intValue()));
            }
        } else {
            sb.append("按时间段：");
            if (items == null || items.isEmpty()) {
                sb.append("未设置计划任务（仅手工执行）");
            } else {
                for (int i = 0; i < items.size(); i++) {
                    AttPlanItem item = items.get(i);
                    if (i > 0) {
                        sb.append("、");
                    }
                    sb.append(OaonesWorkday.dayTypeText(item.getDayType() == null ? 0 : item.getDayType().intValue()))
                            .append(" ").append(OaonesUtil.s(item.getStartTime()))
                            .append("–").append(OaonesUtil.s(item.getEndTime()))
                            .append(" ").append(OaonesProps.humanMinutes(item.getIntervalMin() == null
                                    ? 0 : item.getIntervalMin().intValue()));
                }
            }
        }
        int groupId = cfg.getPlanWorkdayGroupId() == null ? 0 : cfg.getPlanWorkdayGroupId().intValue();
        sb.append("；").append(OaonesWorkday.ruleText(groupId));
        if (cfg.getEnabled() == null || cfg.getEnabled().intValue() == 0) {
            sb.append("（本条设置已停用，只允许手工执行）");
        }
        return sb.toString();
    }

    /** 时间段明细的页面结构。 */
    private JSONArray itemsJson(List<AttPlanItem> items) {
        JSONArray out = new JSONArray();
        if (items == null) {
            return out;
        }
        for (AttPlanItem item : items) {
            JSONObject row = new JSONObject();
            row.put("id", item.getId());
            row.put("day_type", item.getDayType() == null ? 0 : item.getDayType().intValue());
            row.put("start_time", OaonesUtil.s(item.getStartTime()));
            row.put("end_time", OaonesUtil.s(item.getEndTime()));
            row.put("interval_min", item.getIntervalMin() == null ? 0 : item.getIntervalMin().intValue());
            out.add(row);
        }
        return out;
    }

    /* ==================================================================
       二、计划任务：到点判断 / 预测后 N 次 / 保存
       ================================================================== */

    /** 某条设置的下一次执行时间（null = 停用 / 没配出可执行时间）。 */
    private Date nextRun(AttSyncCfg cfg, List<AttPlanItem> items, Date after) {
        if (cfg == null) {
            return null;
        }
        return nextRun(cfg.getPlanMode(), cfg.getCronDayType(), cfg.getPlanWorkdayGroupId(),
                OaonesUtil.s(cfg.getDayCron()), OaonesUtil.s(cfg.getNightCron()), items, after);
    }

    private Date nextRun(Integer planMode, Integer cronDayType, Integer workdayGroupId,
                         String dayCron, String nightCron, List<AttPlanItem> items, Date after) {
        int mode = planMode == null ? OaonesConst.ATT_PLAN_MODE_ITEM : planMode.intValue();
        int dayType = cronDayType == null ? OaonesConst.ATT_PLAN_DAY_ALL : cronDayType.intValue();
        int groupId = workdayGroupId == null ? 0 : Math.max(workdayGroupId.intValue(), 0);
        Date base = after == null ? new Date() : after;
        if (mode == OaonesConst.ATT_PLAN_MODE_CRON) {
            Date day = nextCronTime(dayCron, base, dayType, groupId);
            Date night = nextCronTime(nightCron, base, dayType, groupId);
            if (day == null) {
                return night;
            }
            if (night == null) {
                return day;
            }
            return day.before(night) ? day : night;
        }
        Date best = null;
        if (items == null) {
            return null;
        }
        for (AttPlanItem item : items) {
            Date at = nextItemTime(item, base, groupId);
            if (at != null && (best == null || at.before(best))) {
                best = at;
            }
        }
        return best;
    }

    /** cron 档位的下一次执行时间（遇到“执行日”不匹配的那一天就往后找，最多找 60 天）。 */
    private Date nextCronTime(String cron, Date base, int dayType, int groupId) {
        String expr = OaonesCronExpr.normalize(cron);
        if (expr.length() == 0 || !OaonesCronExpr.valid(expr)) {
            return null;
        }
        Date cursor = base;
        for (int i = 0; i < 60; i++) {
            String next = OaonesCronExpr.nextAfter(expr, cursor);
            if (next == null || next.length() == 0) {
                return null;
            }
            Date at = OaonesUtil.parseDateTimeSafe(next);
            if (at == null) {
                return null;
            }
            if (dayMatches(dayType, at, groupId)) {
                return at;
            }
            cursor = at;
        }
        return null;
    }

    /**
     * 一个时间段在 {@code after} 之后的第一个执行时间。
     *
     * <p>轮询从时段**开始时间**起算（8:30 起每 45 分钟 → 8:30 / 9:15 / 10:00 …，含起点）；
     * 结束时间不大于开始时间时按**跨天**处理（21:00–06:30 = 当天 21:00 到次日 06:30），
     * 跨天时段按**开始那一天**的适用日算；因此从 {@code after} 的前一天开始扫。</p>
     */
    private Date nextItemTime(AttPlanItem item, Date after, int groupId) {
        if (item == null) {
            return null;
        }
        int startMin = minuteOfTime(item.getStartTime());
        int endMin = minuteOfTime(item.getEndTime());
        if (startMin < 0 || endMin < 0) {
            return null;
        }
        int span = endMin <= startMin ? (endMin + 1440 - startMin) : (endMin - startMin);
        if (span <= 0) {
            return null;
        }
        int step = intervalOf(item);
        int dayType = item.getDayType() == null ? OaonesConst.ATT_PLAN_DAY_ALL : item.getDayType().intValue();
        Date base = after == null ? new Date() : after;
        Calendar day = Calendar.getInstance();
        day.setTime(base);
        day.add(Calendar.DAY_OF_MONTH, -1);   // 跨天时段的凌晨尾巴归属前一天
        for (int offset = 0; offset <= 9; offset++) {
            Calendar owner = (Calendar) day.clone();
            owner.add(Calendar.DAY_OF_MONTH, offset);
            if (dayMatches(dayType, owner.getTime(), groupId)) {
                for (int m = 0; m <= span; m += step) {
                    Calendar at = (Calendar) owner.clone();
                    at.set(Calendar.HOUR_OF_DAY, 0);
                    at.set(Calendar.MINUTE, 0);
                    at.set(Calendar.SECOND, 0);
                    at.set(Calendar.MILLISECOND, 0);
                    at.add(Calendar.MINUTE, startMin + m);
                    if (!at.getTime().before(base)) {
                        return at.getTime();
                    }
                }
            }
        }
        return null;
    }

    /** 适用日是否命中：0 每天 / 1 工作日 / 2 非工作日（工作日按泛微标准考勤口径）。 */
    private boolean dayMatches(int dayType, Date day, int groupId) {
        if (dayType == OaonesConst.ATT_PLAN_DAY_WORK) {
            return OaonesWorkday.isWorkday(day, groupId);
        }
        if (dayType == OaonesConst.ATT_PLAN_DAY_OFF) {
            return !OaonesWorkday.isWorkday(day, groupId);
        }
        return true;
    }

    private int intervalOf(AttPlanItem item) {
        int minutes = item == null || item.getIntervalMin() == null ? 0 : item.getIntervalMin().intValue();
        if (minutes < OaonesConst.ATT_PLAN_MIN_MINUTES) {
            return OaonesConst.ATT_PLAN_MIN_MINUTES;
        }
        return minutes > OaonesConst.ATT_PLAN_MAX_MINUTES ? OaonesConst.ATT_PLAN_MAX_MINUTES : minutes;
    }

    private int minuteOfTime(String text) {
        String value = OaonesUtil.s(text).trim();
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return -1;
        }
        int hour = OaonesUtil.i(value.substring(0, colon), -1);
        int minute = OaonesUtil.i(value.substring(colon + 1), -1);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    @Override
    public boolean planDue(AttSyncCfg cfg, List<AttPlanItem> items, Date lastRun, Date now) {
        if (cfg == null || cfg.getEnabled() == null || cfg.getEnabled().intValue() == 0) {
            return false;
        }
        Date next = nextRun(cfg, items, lastRun);
        if (next == null) {
            return false;
        }
        return !next.after(now == null ? new Date() : now);
    }

    @Override
    public JSONObject planPreview(JSONObject params) {
        Integer planMode = Integer.valueOf(params.getIntValue("plan_mode"));
        Integer cronDayType = Integer.valueOf(params.getIntValue("cron_day_type"));
        Integer groupId = Integer.valueOf(params.getIntValue("workday_group_id"));
        String dayCron = OaonesUtil.s(params.getString("day_cron"));
        String nightCron = OaonesUtil.s(params.getString("night_cron"));
        List<AttPlanItem> items = parseItems(params.getJSONArray("items"), params.getIntValue("id"));
        JSONObject out = new JSONObject();
        JSONArray runs = new JSONArray();
        JSONArray labels = new JSONArray();
        Date cursor = new Date();
        for (int i = 0; i < 10; i++) {
            Date next = nextRun(planMode, cronDayType, groupId, dayCron, nightCron, items, cursor);
            if (next == null) {
                break;
            }
            runs.add(OaonesUtil.formatDateTime(next.getTime()));
            labels.add(hitLabel(planMode, dayCron, nightCron, items, cursor, next));
            cursor = next;
        }
        out.put("runs", runs);
        out.put("labels", labels);
        boolean cronMode = planMode != null && planMode.intValue() == OaonesConst.ATT_PLAN_MODE_CRON;
        String mode = cronMode ? "高级模式（cron）" : "按时间段";
        boolean noPlan = cronMode
                ? dayCron.trim().length() == 0 && nightCron.trim().length() == 0
                : items.isEmpty();
        out.put("rule_text", noPlan
                ? "未设置计划任务：可保存为暂停状态，并通过“立即执行一次”手工运行"
                : "计划任务（" + mode + "）：" + (runs.isEmpty()
                    ? "当前配置推不出执行时间，请检查时间段 / cron 与工作日口径"
                    : "按当前配置实际会执行的时间见下表"));
        return out;
    }

    /** 预测里“命中的是哪一档”的说明文案（与实际执行共用同一份判断）。 */
    private String hitLabel(Integer planMode, String dayCron, String nightCron,
                            List<AttPlanItem> items, Date base, Date at) {
        int mode = planMode == null ? OaonesConst.ATT_PLAN_MODE_ITEM : planMode.intValue();
        if (mode == OaonesConst.ATT_PLAN_MODE_CRON) {
            String day = OaonesCronExpr.nextAfter(OaonesCronExpr.normalize(dayCron), base);
            if (day != null && day.equals(OaonesUtil.formatDateTime(at.getTime())) && dayCron.length() > 0) {
                return "白天（" + dayCron + "）";
            }
            return "夜间（" + nightCron + "）";
        }
        if (items != null) {
            for (AttPlanItem item : items) {
                Date first = nextItemTime(item, base, 0);
                if (first != null && first.getTime() == at.getTime()) {
                    return OaonesWorkday.dayTypeText(item.getDayType() == null
                            ? 0 : item.getDayType().intValue()) + " " + OaonesUtil.s(item.getStartTime())
                            + "–" + OaonesUtil.s(item.getEndTime())
                            + " " + OaonesProps.humanMinutes(intervalOf(item));
                }
            }
        }
        return "";
    }

    /** 页面提交的时间段 JSON → 实体（id 只是占位，落库时整表重写）。 */
    private List<AttPlanItem> parseItems(JSONArray rows, int cfgId) {
        List<AttPlanItem> out = new ArrayList<AttPlanItem>();
        if (rows == null) {
            return out;
        }
        int order = 1;
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row == null) {
                continue;
            }
            AttPlanItem item = new AttPlanItem();
            item.setCfgId(Integer.valueOf(cfgId));
            item.setDayType(Integer.valueOf(row.getIntValue("day_type")));
            item.setStartTime(normalizeTime(row.getString("start_time")));
            item.setEndTime(normalizeTime(row.getString("end_time")));
            item.setIntervalMin(Integer.valueOf(row.getIntValue("interval_min")));
            item.setShoworder(Integer.valueOf(order++));
            out.add(item);
        }
        return out;
    }

    private String normalizeTime(String text) {
        String value = OaonesUtil.s(text).trim();
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return value;
        }
        int hour = OaonesUtil.i(value.substring(0, colon), 0);
        String rest = value.substring(colon + 1).trim();
        int minute = OaonesUtil.i(rest.length() > 2 ? rest.substring(0, 2) : rest, 0);
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    /* ==================================================================
       三、公共小工具
       ================================================================== */

    private boolean canMaintain(String cfgId, String operatorId) {
        if (OaonesAdmin.isTotalAdmin(operatorId)) {
            return true;
        }
        return configService.canMaintain(cfgId, operatorId);
    }

    private List<QywxConfig> tenants(int type) {
        List<QywxConfig> rows = OaonesMyBatis.call(QywxConfigMapper.class,
                m -> m.listByType(OaonesProps.cfgTable(), type));
        return rows == null ? new ArrayList<QywxConfig>() : rows;
    }

    private QywxConfig tenant(int cfgId) {
        return OaonesMyBatis.call(QywxConfigMapper.class,
                m -> m.getById(OaonesProps.cfgTable(), cfgId));
    }

    private List<AttSyncCfg> queryCfgs(int type, List<Integer> allowIds, int rowType) {
        Map<String, Object> p = OaonesParam.of();
        p.put("type", Integer.valueOf(type));
        p.put("rowType", Integer.valueOf(rowType));
        p.put("cfgIds", allowIds);
        List<AttSyncCfg> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listCfg(p));
        return rows == null ? new ArrayList<AttSyncCfg>() : rows;
    }

    private AttSyncCfg cfgById(int id) {
        Map<String, Object> p = OaonesParam.of();
        p.put("id", Integer.valueOf(id));
        return OaonesMyBatis.call(AttSyncMapper.class, m -> m.getCfgById(p));
    }

    private List<AttPlanItem> itemsOf(int cfgId) {
        List<AttPlanItem> rows = OaonesMyBatis.call(AttPlanItemMapper.class,
                m -> m.listByCfg(OaonesProps.attPlanItemTable(), cfgId));
        return rows == null ? new ArrayList<AttPlanItem>() : rows;
    }

    private Map<Integer, JSONArray> appOptions(int type, List<Integer> allowIds) {
        Map<Integer, JSONArray> out = new LinkedHashMap<Integer, JSONArray>();
        if (allowIds == null || allowIds.isEmpty()) {
            return out;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("type", Integer.valueOf(type));
        p.put("cfgIds", allowIds);
        List<AttAppOption> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listAppOptions(p));
        if (rows == null) {
            return out;
        }
        for (AttAppOption row : rows) {
            JSONObject item = new JSONObject();
            item.put("id", row.getId());
            item.put("app_name", OaonesUtil.s(row.getAppName()));
            item.put("agent_id", OaonesUtil.s(row.getAgentId()));
            // 应用是否可用：**只给 enable 这个布尔值**，不把原始的 yx 抛给页面——
            // uf_oaones_appsyn_app_bind.yx 是“0 有效 / 1 无效”（OaonesConst.APP_BIND_STATE_VALID = 0，
            // 与部门绑定表的 1 有效 / 0 失效相反），页面自己判很容易写反
            // （2026-10-15 现场：启用中的应用被标成「已停用」）。与 appBind / 会议同步同一口径。
            item.put("enable", Boolean.valueOf(row.getYx() != null
                    && row.getYx().intValue() == OaonesConst.APP_BIND_STATE_VALID));
            item.put("secret_ready", row.getSecretConfigured());
            JSONArray list = out.get(row.getCfgId());
            if (list == null) {
                list = new JSONArray();
                out.put(row.getCfgId(), list);
            }
            list.add(item);
        }
        return out;
    }

    /**
     * 反向写入能力说明（需求 3.9 第 4 条）：页面与日志用同一句文案，
     * 避免“看起来在同步、其实没写成”。{@code none} 时说明原因（明细写“跳过”）；
     * 其它方式把真正的落地口径（走哪个接口 / 参数从哪来）交给对应适配器说明。
     *
     * <p>注意：适配器的 {@code capabilityNote} 按“上下文里的 mode”分岔，所以这里必须把
     * 解析出来的 mode 一起传进去，否则会按“未配置”回答（页面上会出现
     * “配置是 card、说明里却说未启用”的自相矛盾）。</p>
     */
    private String pushCapabilityNote(int type) {
        String mode = OaonesProps.attPushMode(type);
        if (OaonesConst.ATT_PUSH_MODE_NONE.equalsIgnoreCase(mode)) {
            String plat = type == OaonesConst.TYPE_DINGTALK ? "钉钉"
                    : (type == OaonesConst.TYPE_FEISHU ? "飞书" : "企业微信");
            return plat + "反向写入（OA → 第三方）暂标记为不支持：" + OaonesConst.ATT_SKIP_PUSH_UNSUPPORTED
                    + "（首次上线实测确认后，把 att.push.mode." + (type == OaonesConst.TYPE_DINGTALK ? "dingtalk"
                    : (type == OaonesConst.TYPE_FEISHU ? "feishu" : "wecom")) + " 改成 direct / card 即启用）";
        }
        AttPushContext ctx = new AttPushContext();
        ctx.setType(type);
        ctx.setMode(mode);
        // 页面那行是“反向同步（OA → 第三方）当前方式：<这里>”，所以只回“方式 + 一句落地说明”，不再重复前缀
        return mode + "（" + pushAdapter(type).capabilityNote(ctx) + "）";
    }

    private AttPullAdapter pullAdapter(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new DingTalkAttApi("", "");
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new FeishuAttApi("", "");
        }
        return new WeComAttApi("", "");
    }

    private AttPullAdapter pullAdapter(int type, String key, String secret) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new DingTalkAttApi(key, secret);
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new FeishuAttApi(key, secret);
        }
        return new WeComAttApi(key, secret);
    }

    private AttPushAdapter pushAdapter(int type) {
        return (AttPushAdapter) pullAdapter(type);
    }

    private AttPushAdapter pushAdapter(int type, String key, String secret) {
        return (AttPushAdapter) pullAdapter(type, key, secret);
    }

    /* ==================================================================
       四、设置的保存 / 删除 / 开关 / 考勤应用 / 校验
       ================================================================== */

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        int type = params.getIntValue("type");
        int cfgId = params.getIntValue("cfg_id");
        if (type < 0 || type > 2) {
            throw new RuntimeException("平台类型不正确");
        }
        if (cfgId <= 0) {
            throw new RuntimeException("请选择租户");
        }
        if (!canMaintain(String.valueOf(cfgId), operatorId)) {
            throw new RuntimeException("您不是该租户的维护人，无权查看或设置该租户");
        }
        QywxConfig tenant = tenant(cfgId);
        if (tenant == null || tenant.getType() == null || tenant.getType().intValue() != type) {
            throw new RuntimeException("该租户不属于当前平台");
        }
        int id = params.getIntValue("id");
        AttSyncCfg exist = id > 0 ? cfgById(id) : null;
        if (id > 0 && (exist == null || exist.getRowType() == null
                || exist.getRowType().intValue() != OaonesConst.ATT_ROW_CFG)) {
            throw new RuntimeException("要修改的同步设置不存在");
        }
        if (exist != null && !canMaintain(String.valueOf(exist.getCfgid()), operatorId)) {
            throw new RuntimeException("您不是该租户的维护人，无权修改这条同步设置");
        }

        String cfgName = OaonesUtil.s(params.getString("cfg_name")).trim();
        if (cfgName.length() == 0) {
            cfgName = "默认设置";
        }
        AttSyncCfg sameName = cfgByName(type, cfgId, OaonesConst.ATT_ROW_CFG, cfgName);
        if (sameName != null && (exist == null || !sameName.getId().equals(exist.getId()))) {
            throw new RuntimeException("同一租户下已经有名为「" + cfgName + "」的同步设置，请换个名称");
        }

        int dir = params.getIntValue("dir");
        if (dir < OaonesConst.ATT_DIR_BOTH || dir > OaonesConst.ATT_DIR_OA2THIRD) {
            throw new RuntimeException("同步方向不正确");
        }
        int oaAccountRule = params.getIntValue("oa_account_rule");
        if (oaAccountRule != OaonesConst.ATT_OA_ACCOUNT_PRIMARY
                && oaAccountRule != OaonesConst.ATT_OA_ACCOUNT_ALL) {
            throw new RuntimeException("OA多账户同步规则不正确");
        }
        int dataTypes = params.getIntValue("data_types");
        if ((dataTypes & (OaonesConst.ATT_DATA_NORMAL | OaonesConst.ATT_DATA_REMEDY)) == 0) {
            throw new RuntimeException("请至少勾选一种数据类型（正常打卡 / 补签卡）");
        }
        int periods = params.getIntValue("sign_periods");
        if (periods != OaonesConst.ATT_PERIOD_SIGN_IN && periods != OaonesConst.ATT_PERIOD_SIGN_OUT
                && periods != OaonesConst.ATT_PERIOD_BOTH) {
            periods = OaonesConst.ATT_PERIOD_BOTH;
        }

        // 范围三要素（多选；三者 OR 并集；都为空 = 该租户全部人员）
        List<Integer> subIds = splitIds(params.getString("scope_subs"));
        List<Integer> deptIds = splitIds(params.getString("scope_depts"));
        List<Integer> userIds = splitIds(params.getString("scope_users"));

        int winMode = params.getIntValue("win_mode");
        if (winMode < 0 || winMode > OaonesConst.ATT_WIN_LAST_DAYS) {
            winMode = OaonesConst.ATT_WIN_TODAY;
        }
        String winFrom = OaonesUtil.s(params.getString("win_from")).trim();
        String winTo = OaonesUtil.s(params.getString("win_to")).trim();
        if (winMode == OaonesConst.ATT_WIN_RANGE) {
            if (winFrom.length() == 0 || winTo.length() == 0) {
                throw new RuntimeException("时间口径选了“任意时间范围”，请填写起止时间");
            }
            long from = OaonesUtil.parseDateTime(winFrom);
            long to = OaonesUtil.parseDateTime(winTo);
            if (from <= 0L || to <= 0L) {
                throw new RuntimeException("时间范围的起止时间格式不正确（应是 yyyy-MM-dd HH:mm:ss）");
            }
            if (to < from) {
                throw new RuntimeException("时间范围的结束时间不能早于开始时间");
            }
        } else if (winMode == OaonesConst.ATT_WIN_LAST_DAYS) {
            int days = params.getIntValue("win_days");
            if (days < 1) {
                throw new RuntimeException("“最近 N 天”的 N 至少为 1");
            }
        }

        // 考勤应用与租户绑定（2026-10-15 现场反馈）：设置行不再自己选应用，
        // 一律取该租户当前的应用存成本行快照（租户还没指定就是 0 → 不执行同步）。
        AttSyncCfg appRow = tenantRow(type, cfgId);
        int appId = appRow == null || appRow.getAppId() == null ? 0 : appRow.getAppId().intValue();
        int appCheckState = appRow == null || appRow.getCheckState() == null
                ? OaonesConst.ATT_CHECK_NONE : appRow.getCheckState().intValue();
        String appCheckMsg = appRow == null ? "" : OaonesUtil.s(appRow.getCheckMsg());
        String appCheckTime = appRow == null ? "" : OaonesUtil.s(appRow.getCheckTime());

        int planMode = params.getIntValue("plan_mode");
        if (planMode != OaonesConst.ATT_PLAN_MODE_ITEM && planMode != OaonesConst.ATT_PLAN_MODE_CRON) {
            planMode = OaonesConst.ATT_PLAN_MODE_ITEM;
        }
        int groupId = Math.max(params.getIntValue("workday_group_id"), 0);
        int cronDayType = params.getIntValue("cron_day_type");
        if (cronDayType < 0 || cronDayType > 2) {
            cronDayType = OaonesConst.ATT_PLAN_DAY_ALL;
        }
        String dayCron = checkCron(params.getString("day_cron"), "白天");
        String nightCron = checkCron(params.getString("night_cron"), "夜间");
        List<AttPlanItem> items = parseItems(params.getJSONArray("items"), id);
        if (planMode == OaonesConst.ATT_PLAN_MODE_ITEM && !items.isEmpty()) {
            checkItems(items);
        }

        int enabled = params.getIntValue("enabled") == 1 ? 1 : 0;
        if (enabled == 1
                && (planMode == OaonesConst.ATT_PLAN_MODE_ITEM
                    ? items.isEmpty()
                    : dayCron.length() == 0 && nightCron.length() == 0)) {
            throw new RuntimeException("启用状态的同步设置必须配置计划任务；"
                    + "如果只需要手工执行，请先保存为暂停状态，执行时再点“立即执行一次”");
        }
        String freshCheckMsg = null;
        if (enabled == 1) {
            // 前置条件不放行就不能开启（需求 3.1.2）：租户必须指定考勤应用，且这个应用校验通过
            if (appId <= 0) {
                throw new RuntimeException("开启同步前请先在租户卡片上指定「考勤应用」"
                        + "（考勤数据必须用有考勤权限的应用自己的凭据，不指定则不执行同步，"
                        + "也不会静默回退到租户的通讯录 Secret）");
            }
            if (appCheckState != OaonesConst.ATT_CHECK_OK) {
                // 该租户的应用还没校验通过（刚换过 / 从没校验过）：保存前真调一次平台考勤接口，
                // 通过才允许开启；不通过把平台原始错误抛给页面（弹框不关，改完可以再存）。
                AttSyncCfg rowForCheck = appRow == null
                        ? tenantRowOrCreate(type, cfgId, appId, 1, operatorId) : appRow;
                JSONObject probe = check(rowForCheck, operatorId);
                if (!probe.getBooleanValue("ok")) {
                    throw new RuntimeException("开启同步前请先在租户卡片上点「重新校验」，"
                            + "用该考勤应用真调一次平台考勤接口；校验通过后才允许开启。本次校验结果："
                            + OaonesUtil.s(probe.getString("message")));
                }
                freshCheckMsg = OaonesUtil.s(probe.getString("message"));
                appCheckState = OaonesConst.ATT_CHECK_OK;
                appCheckMsg = freshCheckMsg;
                appCheckTime = OaonesUtil.now();
            }
        }

        String now = OaonesUtil.now();
        AttSyncCfg entity = new AttSyncCfg();
        entity.setId(id > 0 ? Integer.valueOf(id) : exist == null ? null : exist.getId());
        entity.setType(Integer.valueOf(type));
        entity.setCfgid(Integer.valueOf(cfgId));
        entity.setRowType(Integer.valueOf(OaonesConst.ATT_ROW_CFG));
        entity.setCfgName(cfgName);
        entity.setDir(Integer.valueOf(dir));
        entity.setScopeSubs(join(subIds));
        entity.setScopeDepts(join(deptIds));
        entity.setScopeUsers(join(userIds));
        entity.setScopeText(scopeText(cfgId, subIds, deptIds, userIds));
        entity.setOaAccountRule(Integer.valueOf(oaAccountRule));
        entity.setDataTypes(Integer.valueOf(dataTypes));
        entity.setSignPeriods(Integer.valueOf(periods));
        entity.setWinMode(Integer.valueOf(winMode));
        entity.setWinFrom(winFrom);
        entity.setWinTo(winTo);
        entity.setWinDays(Integer.valueOf(winMode == OaonesConst.ATT_WIN_LAST_DAYS
                ? Math.max(params.getIntValue("win_days"), 1) : OaonesProps.attWinLastDaysDefault()));
        entity.setAppId(Integer.valueOf(appId));
        entity.setPlanMode(Integer.valueOf(planMode));
        entity.setPlanWorkdayGroupId(Integer.valueOf(groupId));
        entity.setCronDayType(Integer.valueOf(cronDayType));
        entity.setDayCron(dayCron);
        entity.setNightCron(nightCron);
        entity.setEnabled(Integer.valueOf(enabled));
        entity.setRemark(OaonesUtil.s(params.getString("remark")));
        entity.setModedatamodifier(Integer.valueOf(OaonesUtil.i(operatorId, 0)));
        entity.setModedatamodifydatetime(now);
        // 校验状态是**租户级**的（考勤应用属于租户）：设置行跟着租户行记一份，页面直接看得到
        entity.setCheckState(Integer.valueOf(appCheckState));
        entity.setCheckMsg(appCheckMsg);
        entity.setCheckTime(appCheckTime);

        if (exist == null) {
            entity.setFailStreak(Integer.valueOf(0));
            entity.setModedatacreater(Integer.valueOf(OaonesUtil.i(operatorId, 0)));
            entity.setModedatacreatedate(now.length() >= 10 ? now.substring(0, 10) : now);
            entity.setModedatacreatetime(now.length() >= 8 ? now.substring(11) : "");
            Map<String, Object> p = OaonesParam.of();
            p.put("e", entity);
            OaonesMyBatis.run(AttSyncMapper.class, m -> m.insertCfg(p));
        } else {
            entity.setFailStreak(exist.getFailStreak());
            entity.setLastRunTime(exist.getLastRunTime());
            entity.setLastResult(exist.getLastResult());
            entity.setLastResultText(exist.getLastResultText());
            entity.setLastLogId(exist.getLastLogId());
            Map<String, Object> p = OaonesParam.of();
            p.put("e", entity);
            OaonesMyBatis.run(AttSyncMapper.class, m -> m.updateCfg(p));
        }
        saveItems(entity.getId().intValue(), items, operatorId);
        OaonesWorkday.clearCache();
        return cfgJson(cfgById(entity.getId().intValue()), itemsOf(entity.getId().intValue()));
    }

    /** 保存计划任务的时间段明细：整表重写（先删该设置的全部行，再按页面顺序插入）。 */
    private void saveItems(int cfgId, List<AttPlanItem> items, String operatorId) {
        String now = OaonesUtil.now();
        OaonesMyBatis.run(AttPlanItemMapper.class,
                m -> m.deleteByCfg(OaonesProps.attPlanItemTable(), cfgId));
        for (AttPlanItem item : items) {
            item.setCfgId(Integer.valueOf(cfgId));
            item.setModedatacreater(Integer.valueOf(OaonesUtil.i(operatorId, 0)));
            item.setModedatacreatedate(now.length() >= 10 ? now.substring(0, 10) : now);
            item.setModedatacreatetime(now.length() >= 8 ? now.substring(11) : "");
            OaonesMyBatis.run(AttPlanItemMapper.class,
                    m -> m.insert(OaonesProps.attPlanItemTable(), item));
        }
    }

    @Override
    public JSONObject remove(JSONObject params, String operatorId) {
        int id = params.getIntValue("id");
        AttSyncCfg cfg = cfgById(id);
        if (cfg == null) {
            throw new RuntimeException("要删除的同步设置不存在");
        }
        assertCfgRight(cfg.getCfgid(), operatorId);
        Map<String, Object> p = OaonesParam.of();
        p.put("id", Integer.valueOf(id));
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.deleteCfg(p));
        OaonesMyBatis.run(AttPlanItemMapper.class,
                m -> m.deleteByCfg(OaonesProps.attPlanItemTable(), id));
        JSONObject out = new JSONObject();
        out.put("id", id);
        return out;
    }

    @Override
    public JSONObject setEnabled(int id, int enabled, String operatorId) {
        AttSyncCfg cfg = cfgById(id);
        if (cfg == null) {
            throw new RuntimeException("同步设置不存在");
        }
        assertCfgRight(cfg.getCfgid(), operatorId);
        if (enabled == 1) {
            List<AttPlanItem> items = itemsOf(id);
            int planMode = cfg.getPlanMode() == null
                    ? OaonesConst.ATT_PLAN_MODE_ITEM : cfg.getPlanMode().intValue();
            boolean hasPlan = planMode == OaonesConst.ATT_PLAN_MODE_CRON
                    ? OaonesUtil.s(cfg.getDayCron()).length() > 0
                        || OaonesUtil.s(cfg.getNightCron()).length() > 0
                    : items != null && !items.isEmpty();
            if (!hasPlan) {
                throw new RuntimeException("该设置还没有配置计划任务，不能直接启用；"
                        + "请先编辑并设置按时间段或 cron，保存后再启用");
            }
            // 前置条件与保存时同一口径：考勤应用在租户卡片上维护，校验状态也取租户行的
            int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
            int cfgId = cfg.getCfgid() == null ? 0 : cfg.getCfgid().intValue();
            if (tenantAppId(type, cfgId) <= 0) {
                throw new RuntimeException("开启同步前请先在租户卡片上指定「考勤应用」"
                        + "（不指定则不执行同步，也不会回退到租户的通讯录 Secret）");
            }
            AttSyncCfg row = tenantRow(type, cfgId);
            if (row == null || row.getCheckState() == null
                    || row.getCheckState().intValue() != OaonesConst.ATT_CHECK_OK) {
                throw new RuntimeException("开启同步前请先在租户卡片上点「重新校验」，"
                        + "该租户的考勤应用校验通过后才允许开启");
            }
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("id", Integer.valueOf(id));
        p.put("enabled", Integer.valueOf(enabled));
        p.put("modifier", Integer.valueOf(OaonesUtil.i(operatorId, 0)));
        p.put("modifyTime", OaonesUtil.now());
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.updateEnabled(p));
        JSONObject out = new JSONObject();
        out.put("id", id);
        out.put("enabled", enabled);
        return out;
    }

    @Override
    public JSONObject setTenantEnabled(int type, int cfgId, int enabled, String operatorId) {
        assertCfgRight(Integer.valueOf(cfgId), operatorId);
        // 没配过就按“已开启”的缺省值建行（**NOT NULL 列必须全部给值**，见 newTenantRow 的注释）
        AttSyncCfg row = tenantRowOrCreate(type, cfgId, tenantAppId(type, cfgId), 1, operatorId);
        String now = OaonesUtil.now();
        if (row.getEnabled() == null || row.getEnabled().intValue() != enabled) {
            Map<String, Object> p = OaonesParam.of();
            p.put("id", row.getId());
            p.put("enabled", Integer.valueOf(enabled));
            p.put("modifier", Integer.valueOf(OaonesUtil.i(operatorId, 0)));
            p.put("modifyTime", now);
            OaonesMyBatis.run(AttSyncMapper.class, m -> m.updateEnabled(p));
            row.setEnabled(Integer.valueOf(enabled));
        }
        JSONObject out = new JSONObject();
        out.put("cfg_id", cfgId);
        out.put("enabled", enabled);
        out.put("id", row.getId());
        return out;
    }

    @Override
    public JSONObject setTenantApp(int type, int cfgId, int appId, String operatorId) {
        assertCfgRight(Integer.valueOf(cfgId), operatorId);
        QywxConfig tenant = tenant(cfgId);
        if (tenant == null || tenant.getType() == null || tenant.getType().intValue() != type) {
            throw new RuntimeException("该租户不属于当前平台");
        }
        if (appId > 0) {
            AttAppOption app = appById(type, appId);
            if (app == null || app.getCfgId() == null || app.getCfgId().intValue() != cfgId) {
                throw new RuntimeException("选中的考勤应用不存在，或不属于该租户");
            }
        }
        AttSyncCfg row = tenantRowOrCreate(type, cfgId, 0, 1, operatorId);
        Map<String, Object> p = OaonesParam.of();
        p.put("id", row.getId());
        p.put("appId", Integer.valueOf(appId));
        p.put("modifier", Integer.valueOf(OaonesUtil.i(operatorId, 0)));
        p.put("modifyTime", OaonesUtil.now());
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.updateApp(p));
        row.setAppId(Integer.valueOf(appId));
        row.setCheckState(Integer.valueOf(OaonesConst.ATT_CHECK_NONE));
        row.setCheckMsg("");
        JSONObject out = new JSONObject();
        out.put("cfg_id", cfgId);
        out.put("app_id", appId);
        if (appId <= 0) {
            // 清空应用：不执行任何同步（也不回退租户的通讯录 Secret），不需要再调平台接口
            out.put("ok", false);
            out.put("check_state", OaonesConst.ATT_CHECK_NONE);
            out.put("message", "已清空该租户的「考勤应用」：不指定则不执行同步"
                    + "（也不会静默回退到租户的通讯录 Secret）");
            return out;
        }
        // 「考勤应用设好就立刻保存并校验」：这一步真调一次平台考勤接口，结论落到租户行上
        JSONObject probe = check(row, operatorId);
        out.putAll(probe);
        out.put("cfg_id", cfgId);
        out.put("app_id", appId);
        return out;
    }

    @Override
    public JSONObject checkApp(int type, int cfgId, String operatorId) {
        assertCfgRight(Integer.valueOf(cfgId), operatorId);
        AttSyncCfg row = tenantRow(type, cfgId);
        if (row == null || row.getAppId() == null || row.getAppId().intValue() <= 0) {
            JSONObject out = new JSONObject();
            String msg = "该租户还没有指定「考勤应用」：先在租户卡片上选一个有考勤数据权限的应用"
                    + "（不指定则不执行同步，也不会回退到租户的通讯录 Secret）";
            out.put("ok", false);
            out.put("message", msg);
            return out;
        }
        return check(row, operatorId);
    }

    /** 「校验」的实际动作（保存时也会复用同一份逻辑）。 */
    private JSONObject check(AttSyncCfg cfg, String operatorId) {
        JSONObject out = new JSONObject();
        int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
        Cred cred = credential(cfg);
        if (cred == null) {
            String msg = "未指定「考勤应用」，无法校验：考勤同步必须单独指定有考勤数据权限的应用"
                    + "（不指定则不执行同步，也不会回退到租户的通讯录 Secret）";
            markCheck(cfg, OaonesConst.ATT_CHECK_FAIL, msg, Integer.valueOf(0));
            out.put("ok", false);
            out.put("message", msg);
            return out;
        }
        AttPullContext ctx = new AttPullContext();
        ctx.setType(type);
        ctx.setCfgId(cfg.getCfgid() == null ? 0 : cfg.getCfgid().intValue());
        ctx.setCorpId(cred.key);
        ctx.setCorpSecret(cred.secret);
        ctx.setAgentId(cred.agentId);
        ctx.setUserIds(boundWxUsers(cfg));
        String today = OaonesUtil.now().substring(0, 10);
        ctx.setFromDate(today);
        ctx.setToDate(today);
        ctx.setLimit(50);
        try {
            JSONObject probe = pullAdapter(type, cred.key, cred.secret).probe(ctx);
            boolean ok = probe.getBooleanValue("ok");
            String message = OaonesUtil.s(probe.getString("message"));
            if (cred.useTenantSecret) {
                message = message + "（该应用没有单独配 Secret，本次用的是租户配置里的 Secret；"
                        + "考勤权限一般在「应用」上，建议在应用绑定里补上应用自己的 Secret）";
            }
            markCheck(cfg, ok ? OaonesConst.ATT_CHECK_OK : OaonesConst.ATT_CHECK_FAIL, message,
                    Integer.valueOf(OaonesUtil.i(operatorId, 0)));
            out.put("ok", ok);
            out.put("message", message);
            out.putAll(probe);
            return out;
        } catch (Throwable t) {
            String message = "校验失败：" + (t.getMessage() == null ? t.getClass().getName() : t.getMessage());
            markCheck(cfg, OaonesConst.ATT_CHECK_FAIL, message, Integer.valueOf(OaonesUtil.i(operatorId, 0)));
            OaonesLog.error("[attSync] 考勤应用校验失败 cfgId=" + cfg.getId(), t);
            out.put("ok", false);
            out.put("message", message);
            return out;
        }
    }

    private void markCheck(AttSyncCfg cfg, int state, String message, Integer operatorId) {
        cfg.setCheckState(Integer.valueOf(state));
        cfg.setCheckMsg(message);
        if (cfg.getId() == null || cfg.getId().intValue() <= 0) {
            return;   // 弹框第 1 步（还没保存这条设置）：校验结论只在页面上用，不写库
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("id", cfg.getId());
        p.put("checkState", Integer.valueOf(state));
        p.put("checkMsg", message);
        p.put("checkTime", OaonesUtil.now());
        p.put("modifier", operatorId);
        p.put("modifyTime", OaonesUtil.now());
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.updateCheck(p));
    }

    @Override
    public JSONObject health(int id) {
        AttSyncCfg cfg = cfgById(id);
        JSONObject out = new JSONObject();
        if (cfg == null) {
            return out;
        }
        out.put("last_run_time", OaonesUtil.s(cfg.getLastRunTime()));
        out.put("last_result", cfg.getLastResult() == null ? -1 : cfg.getLastResult().intValue());
        out.put("last_result_text", OaonesUtil.s(cfg.getLastResultText()));
        out.put("fail_streak", cfg.getFailStreak() == null ? 0 : cfg.getFailStreak().intValue());
        out.put("alert_text", alertText(cfg));
        Date next = nextRun(cfg, itemsOf(id), OaonesUtil.parseDateTimeSafe(cfg.getLastRunTime()));
        out.put("next_run", next == null ? "" : OaonesUtil.formatDateTime(next.getTime()));
        return out;
    }

    @Override
    public JSONObject orgOptions(String operatorId) {
        JSONObject data = new JSONObject();
        OaServiceImpl oaService = new OaServiceImpl();
        data.put("oa_subcompanies", oaService.listSubCompaniesFromCache(operatorId));
        data.put("oa_departments", oaService.listDepartmentsFromCache(operatorId));
        return data;
    }

    @Override
    public JSONArray oaUsers(String keyword, String operatorId) {
        try {
            JSONArray rows = new OaServiceImpl().listUsers(
                    OaonesUtil.s(keyword), operatorId);
            return rows == null ? new JSONArray() : rows;
        } catch (Exception e) {
            throw new RuntimeException("读取 OA 人员失败：" + e.getMessage());
        }
    }

    /* ==================================================================
       五、通用校验 / 取值辅助
       ================================================================== */

    private void assertCfgRight(Integer cfgId, String operatorId) {
        int id = cfgId == null ? 0 : cfgId.intValue();
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        if (!canMaintain(String.valueOf(id), operatorId)) {
            throw new RuntimeException("您不是该租户的维护人，无权查看或设置该租户");
        }
    }

    private AttSyncCfg cfgByName(int type, int cfgId, int rowType, String name) {
        Map<String, Object> p = OaonesParam.of();
        p.put("type", Integer.valueOf(type));
        p.put("cfgId", Integer.valueOf(cfgId));
        p.put("rowType", Integer.valueOf(rowType));
        p.put("cfgName", name);
        return OaonesMyBatis.call(AttSyncMapper.class, m -> m.getCfgByName(p));
    }

    private AttAppOption appById(int type, int appId) {
        Map<String, Object> p = OaonesParam.of();
        p.put("type", Integer.valueOf(type));
        p.put("appId", Integer.valueOf(appId));
        return OaonesMyBatis.call(AttSyncMapper.class, m -> m.getAppById(p));
    }

    /** 逗号分隔的 ID 串 → ID 列表（去重、只留正整数）。 */
    private List<Integer> splitIds(String value) {
        List<Integer> list = new ArrayList<Integer>();
        String text = OaonesUtil.s(value).trim();
        if (text.length() == 0) {
            return list;
        }
        for (String item : text.split(",")) {
            int id = OaonesUtil.i(item.trim(), 0);
            if (id > 0 && !list.contains(Integer.valueOf(id))) {
                list.add(Integer.valueOf(id));
            }
        }
        return list;
    }

    private String join(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        if (ids == null) {
            return "";
        }
        for (Integer id : ids) {
            if (id == null || id.intValue() <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(id.intValue());
        }
        return sb.toString();
    }

    /**
     * 把「分部 / 部门」ID 展开成「自身 + 全部下级」。
     *
     * <p>页面口径：选中分部自动含其下级分部、选中部门自动含其下级部门（需求 3.2.1）。
     * 展开用**全量组织**——结果视图不按 OA 分权裁剪，只有“选择候选”按分权过滤
     * （见 {@code common.OaonesHrmScope}）。</p>
     */
    private List<Integer> subtreeIds(List<Integer> roots, boolean sub) {
        List<Integer> out = new ArrayList<Integer>();
        if (roots == null || roots.isEmpty()) {
            return out;
        }
        OaServiceImpl oaService = new OaServiceImpl();
        JSONArray rows = sub ? oaService.listSubCompaniesFromCache() : oaService.listDepartmentsFromCache();
        Set<String> ids = new HashSet<String>();
        for (Integer root : roots) {
            ids.add(String.valueOf(root));
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (ids.contains(OaonesUtil.s(row.getString("parent")))
                        && ids.add(OaonesUtil.s(row.getString("id")))) {
                    grew = true;
                }
            }
        }
        for (String id : ids) {
            int value = OaonesUtil.i(id, 0);
            if (value > 0 && !out.contains(Integer.valueOf(value))) {
                out.add(Integer.valueOf(value));
            }
        }
        return out;
    }

    /** 范围文案快照（保存时落一份，列表直接展示；组织改名后名称现算）。 */
    private String scopeText(int cfgId, List<Integer> subIds, List<Integer> deptIds, List<Integer> userIds) {
        if (subIds.isEmpty() && deptIds.isEmpty() && userIds.isEmpty()) {
            return "全部已绑定人员（范围为：分部 / 部门 / 人员都未选）";
        }
        StringBuilder sb = new StringBuilder();
        appendNames(sb, "分部", subIds, false);
        appendNames(sb, "部门", deptIds, true);
        if (!userIds.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append("人员：").append(userNames(cfgId, userIds));
        }
        sb.append("（选中分部 / 部门自动含其全部下级）");
        return sb.toString();
    }

    private void appendNames(StringBuilder sb, String label, List<Integer> ids, boolean dept) {
        if (ids.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("；");
        }
        sb.append(label).append("：").append(names(ids, dept));
    }

    private String names(List<Integer> ids, boolean dept) {
        OaServiceImpl oaService = new OaServiceImpl();
        JSONArray rows = dept ? oaService.listDepartmentsFromCache() : oaService.listSubCompaniesFromCache();
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            String name = "";
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (String.valueOf(id).equals(OaonesUtil.s(row.getString("id")))) {
                    name = OaonesUtil.s(row.getString("name"));
                    break;
                }
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(name.length() == 0 ? ("#" + id) : name);
        }
        return sb.toString();
    }

    private String userNames(int cfgId, List<Integer> userIds) {
        Map<String, Object> p = OaonesParam.of();
        p.put("cfgId", Integer.valueOf(cfgId));
        p.put("scoped", Boolean.TRUE);
        p.put("userIds", userIds);
        List<AttMember> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listMembers(p));
        StringBuilder sb = new StringBuilder();
        if (rows != null) {
            for (AttMember row : rows) {
                if (sb.length() > 0) {
                    sb.append("、");
                }
                sb.append(OaonesUtil.s(row.getName()).length() == 0
                        ? ("#" + row.getOaUserId()) : OaonesUtil.s(row.getName()));
            }
        }
        return sb.length() == 0 ? "（按人员ID：" + join(userIds) + "）" : sb.toString();
    }

    /** 校验一个 cron 档位（Quartz 口径 + 频率下限 5 分钟）。 */
    private String checkCron(String text, String label) {
        String cron = OaonesCronExpr.normalize(text);
        if (cron.length() == 0) {
            return "";
        }
        if (!OaonesCronExpr.valid(cron)) {
            throw new RuntimeException(label + " cron 表达式不正确，请按 Quartz 口径填写“秒 分 时 日 月 周”"
                    + "（例：0 0 0/1 * * ? = 每小时执行，0 0/30 * * * ? = 每 30 分钟执行）");
        }
        List<String> times = OaonesCronExpr.nextTimes(cron, new Date(), 2);
        if (times.size() >= 2) {
            long first = OaonesUtil.parseDateTime(times.get(0));
            long second = OaonesUtil.parseDateTime(times.get(1));
            long gap = (second - first) / 60000L;
            if (gap < OaonesConst.ATT_PLAN_MIN_MINUTES) {
                throw new RuntimeException(label + " cron 频率太高（相邻两次只隔 " + gap + " 分钟）："
                        + "同步频率下限是每 " + OaonesConst.ATT_PLAN_MIN_MINUTES + " 分钟一次");
            }
        }
        return cron;
    }

    /**
     * 校验时间段明细：至少要一行、间隔在下限与上限之间、同一适用日下不允许重叠、
     * 「每天」与「工作日 / 非工作日」不允许同时配（否则同一天会命中两套时段）。
     */
    private void checkItems(List<AttPlanItem> items) {
        if (items.isEmpty()) {
            throw new RuntimeException("计划任务选了“按时间段”，至少要配一个时间段"
                    + "（适用日 + 开始时间 + 结束时间 + 轮询间隔）");
        }
        if (items.size() > OaonesConst.ATT_PLAN_ITEM_LIMIT) {
            throw new RuntimeException("时间段最多配 " + OaonesConst.ATT_PLAN_ITEM_LIMIT + " 行");
        }
        Set<Integer> dayTypes = new LinkedHashSet<Integer>();
        for (AttPlanItem item : items) {
            int dayType = item.getDayType() == null ? 0 : item.getDayType().intValue();
            dayTypes.add(Integer.valueOf(dayType));
            int start = minuteOfTime(item.getStartTime());
            int end = minuteOfTime(item.getEndTime());
            if (start < 0 || end < 0) {
                throw new RuntimeException("时间段的开始 / 结束时间要填 HH:mm（例：09:00）");
            }
            int interval = item.getIntervalMin() == null ? 0 : item.getIntervalMin().intValue();
            if (interval < OaonesConst.ATT_PLAN_MIN_MINUTES) {
                throw new RuntimeException("轮询间隔下限是 " + OaonesConst.ATT_PLAN_MIN_MINUTES + " 分钟");
            }
            if (interval > OaonesConst.ATT_PLAN_MAX_MINUTES) {
                throw new RuntimeException("轮询间隔上限是 24 小时");
            }
        }
        if (dayTypes.contains(Integer.valueOf(OaonesConst.ATT_PLAN_DAY_ALL)) && dayTypes.size() > 1) {
            throw new RuntimeException("「每天」不能与「工作日 / 非工作日」同时配置（同一天会命中两套时间段）");
        }
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                if (overlap(items.get(i), items.get(j))) {
                    throw new RuntimeException("同一适用日下的时间段不能重叠："
                            + OaonesUtil.s(items.get(i).getStartTime()) + "–" + OaonesUtil.s(items.get(i).getEndTime())
                            + " 与 " + OaonesUtil.s(items.get(j).getStartTime()) + "–"
                            + OaonesUtil.s(items.get(j).getEndTime()));
                }
            }
        }
    }

    /** 两个时间段是否（在“前一天 / 当天 / 后一天”三种对齐下）重叠。 */
    private boolean overlap(AttPlanItem one, AttPlanItem two) {
        int dayOne = one.getDayType() == null ? 0 : one.getDayType().intValue();
        int dayTwo = two.getDayType() == null ? 0 : two.getDayType().intValue();
        if (dayOne != dayTwo) {
            return false;      // 适用日不同：跨天尾巴的撞车在真实执行里各自独立判断
        }
        int startOne = minuteOfTime(one.getStartTime());
        int endOne = minuteOfTime(one.getEndTime());
        int startTwo = minuteOfTime(two.getStartTime());
        int endTwo = minuteOfTime(two.getEndTime());
        if (startOne < 0 || endOne < 0 || startTwo < 0 || endTwo < 0) {
            return false;
        }
        int spanOne = endOne <= startOne ? (endOne + 1440 - startOne) : (endOne - startOne);
        int spanTwo = endTwo <= startTwo ? (endTwo + 1440 - startTwo) : (endTwo - startTwo);
        for (int shift = -1440; shift <= 1440; shift += 1440) {
            int a1 = startOne;
            int a2 = startOne + spanOne;
            int b1 = startTwo + shift;
            int b2 = startTwo + shift + spanTwo;
            if (a1 < b2 && b1 < a2) {
                return true;
            }
        }
        return false;
    }

    /** 考勤应用的凭据（应用自己的 Secret 优先，为空时由租户的 Secret 兜底，并标记一下）。 */
    private Cred credential(AttSyncCfg cfg) {
        int appId = cfg.getAppId() == null ? 0 : cfg.getAppId().intValue();
        if (appId <= 0) {
            // 设置行里的 app_id 只是保存时的快照：老数据 / 迁移前的行按租户当前的考勤应用走
            appId = tenantAppId(cfg.getType() == null ? 0 : cfg.getType().intValue(),
                    cfg.getCfgid() == null ? 0 : cfg.getCfgid().intValue());
        }
        if (appId <= 0) {
            return null;
        }
        int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
        AttAppOption app = appById(type, appId);
        if (app == null) {
            return null;
        }
        Cred cred = new Cred();
        cred.key = OaonesUtil.s(app.getCfgCorpId());
        cred.agentId = OaonesUtil.s(app.getAgentId());
        String appSecret = OaonesUtil.s(app.getAppSecret());
        if (appSecret.length() > 0) {
            cred.secret = appSecret;
        } else {
            cred.secret = OaonesUtil.s(app.getCfgCorpSecret());
            cred.useTenantSecret = true;
        }
        return cred;
    }

    /** 该设置范围内**已绑定**平台账号的人员（去重后的平台账号列表）。 */
    private List<String> boundWxUsers(AttSyncCfg cfg) {
        List<AttMember> members = members(cfg);
        List<String> out = new ArrayList<String>();
        for (AttMember member : members) {
            String wx = OaonesUtil.s(member.getWxUserId());
            if (wx.length() > 0 && member.getBindState() != null && member.getBindState().intValue() == 1
                    && !out.contains(wx)) {
                out.add(wx);
            }
        }
        return out;
    }

    /** 该设置范围圈出来的人员（含未绑定 / 已离职的人，便于明细里写固定文案的跳过原因）。 */
    private List<AttMember> members(AttSyncCfg cfg) {
        List<Integer> subs = splitIds(cfg.getScopeSubs());
        List<Integer> depts = splitIds(cfg.getScopeDepts());
        List<Integer> users = splitIds(cfg.getScopeUsers());
        boolean scoped = !subs.isEmpty() || !depts.isEmpty() || !users.isEmpty();
        Map<String, Object> p = OaonesParam.of();
        p.put("cfgId", cfg.getCfgid());
        p.put("scoped", Boolean.valueOf(scoped));
        p.put("subIds", subtreeIds(subs, true));
        p.put("deptIds", subtreeIds(depts, false));
        p.put("userIds", users);
        List<AttMember> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listMembers(p));
        return rows == null ? new ArrayList<AttMember>() : rows;
    }

    /* ==================================================================
       六、执行：立即执行一次 / 整批执行 / 内置调度器一轮 / 重推
       ================================================================== */

    @Override
    public JSONObject runNow(JSONObject params, String operatorId) {
        AttSyncCfg cfg = cfgById(params.getIntValue("id"));
        if (cfg == null) {
            throw new RuntimeException("同步设置不存在");
        }
        assertCfgRight(cfg.getCfgid(), operatorId);
        Win win = window(cfg, params);
        return execute(cfg, win, OaonesConst.ATT_TRIGGER_MANUAL, operatorName(operatorId));
    }

    @Override
    public JSONObject runBatch(int type, int cfgId, String operatorId) {
        assertCfgRight(Integer.valueOf(cfgId), operatorId);
        JSONObject out = new JSONObject();
        JSONArray runs = new JSONArray();
        for (AttSyncCfg cfg : queryCfgs(type, java.util.Collections.singletonList(Integer.valueOf(cfgId)),
                OaonesConst.ATT_ROW_CFG)) {
            if (cfg.getEnabled() == null || cfg.getEnabled().intValue() == 0) {
                continue;
            }
            try {
                runs.add(execute(cfg, window(cfg, null), OaonesConst.ATT_TRIGGER_MANUAL,
                        operatorName(operatorId)));
            } catch (Throwable t) {
                JSONObject one = new JSONObject();
                one.put("id", cfg.getId());
                one.put("cfg_name", OaonesUtil.s(cfg.getCfgName()));
                one.put("message", "执行失败：" + messageOf(t));
                runs.add(one);
                OaonesLog.error("[attSync] 整批执行单条设置失败 cfgId=" + cfg.getId(), t);
            }
        }
        out.put("runs", runs);
        out.put("count", runs.size());
        out.put("message", runs.isEmpty() ? "该租户下没有启用中的同步设置" : "已执行 " + runs.size() + " 条设置");
        return out;
    }

    @Override
    public JSONObject syncRound() {
        JSONObject out = new JSONObject();
        JSONArray runs = new JSONArray();
        List<AttSyncCfg> cfgs = enabledCfgs();
        Date now = new Date();
        for (AttSyncCfg cfg : cfgs) {
            try {
                List<AttPlanItem> items = itemsOf(cfg.getId().intValue());
                Date lastRun = OaonesUtil.parseDateTimeSafe(cfg.getLastRunTime());
                if (!planDue(cfg, items, lastRun, now)) {
                    continue;
                }
                runs.add(execute(cfg, window(cfg, null), OaonesConst.ATT_TRIGGER_PLAN, ""));
            } catch (Throwable t) {
                OaonesLog.error("[attSync] 调度器执行设置失败 cfgId=" + cfg.getId(), t);
            }
        }
        out.put("runs", runs);
        out.put("count", runs.size());
        out.put("message", runs.isEmpty() ? "本轮没有到点的同步设置" : "本轮执行了 " + runs.size() + " 条设置");
        return out;
    }

    /** 启用中的设置（调度器扫描用，一次最多 {@code att.sync.batch} 条）。 */
    private List<AttSyncCfg> enabledCfgs() {
        Map<String, Object> p = OaonesParam.of();
        p.put("rowType", Integer.valueOf(OaonesConst.ATT_ROW_CFG));
        p.put("limit", Integer.valueOf(OaonesProps.attSyncBatch()));
        List<AttSyncCfg> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listEnabledCfg(p));
        return rows == null ? new ArrayList<AttSyncCfg>() : rows;
    }

    /** 一次执行的时间窗。 */
    private static final class Win {
        private long from;
        private long to;
        private int mode = OaonesConst.ATT_WIN_TODAY;
    }

    /**
     * 计算本次执行的时间窗：默认按设置自己的口径，页面「立即执行一次」可临时指定一次
     * （不落库、只影响本次）。
     *
     * <ul>
     *   <li>当天数据：执行时刻所在自然日 00:00:00 ~ 23:59:59；</li>
     *   <li>前一天数据：执行时刻前一天；</li>
     *   <li>最近 N 天：今天往前 n-1 天的 00:00:00 ~ 今天 23:59:59；</li>
     *   <li>任意时间范围：页面手工指定的起止（精确到秒）。</li>
     * </ul>
     */
    private Win window(AttSyncCfg cfg, JSONObject params) {
        int mode = cfg.getWinMode() == null ? OaonesConst.ATT_WIN_TODAY : cfg.getWinMode().intValue();
        String from = OaonesUtil.s(cfg.getWinFrom());
        String to = OaonesUtil.s(cfg.getWinTo());
        int days = cfg.getWinDays() == null ? OaonesProps.attWinLastDaysDefault() : cfg.getWinDays().intValue();
        if (params != null && params.containsKey("win_mode")) {
            int override = params.getIntValue("win_mode");
            if (override >= 0 && override <= OaonesConst.ATT_WIN_LAST_DAYS) {
                mode = override;
            }
            if (OaonesUtil.s(params.getString("win_from")).length() > 0) {
                from = OaonesUtil.s(params.getString("win_from"));
            }
            if (OaonesUtil.s(params.getString("win_to")).length() > 0) {
                to = OaonesUtil.s(params.getString("win_to"));
            }
            if (params.getIntValue("win_days") > 0) {
                days = params.getIntValue("win_days");
            }
        }
        Win win = new Win();
        win.mode = mode;
        String today = OaonesUtil.now().substring(0, 10);
        if (mode == OaonesConst.ATT_WIN_RANGE) {
            win.from = OaonesUtil.parseDateTime(from);
            win.to = OaonesUtil.parseDateTime(to);
        } else if (mode == OaonesConst.ATT_WIN_PREV_DAY) {
            String day = OaonesUtil.shiftDate(today, -1);
            win.from = OaonesUtil.parseDateTime(day + " 00:00:00");
            win.to = OaonesUtil.parseDateTime(day + " 23:59:59");
        } else if (mode == OaonesConst.ATT_WIN_LAST_DAYS) {
            String day = OaonesUtil.shiftDate(today, -(Math.max(days, 1) - 1));
            win.from = OaonesUtil.parseDateTime(day + " 00:00:00");
            win.to = OaonesUtil.parseDateTime(today + " 23:59:59");
        } else {
            win.from = OaonesUtil.parseDateTime(today + " 00:00:00");
            win.to = OaonesUtil.parseDateTime(today + " 23:59:59");
        }
        if (win.from <= 0L) {
            throw new RuntimeException("时间窗口的开始时间取不到，请检查时间口径配置");
        }
        if (win.to <= win.from) {
            throw new RuntimeException("时间窗口的结束时间必须晚于开始时间");
        }
        return win;
    }

    /**
     * 执行一轮：前置校验 → 第三方→OA 导入 → OA→第三方 推送 → 收尾（批次结果、设置健康度）。
     */
    private JSONObject execute(AttSyncCfg cfg, Win win, int trigger, String operatorName) {
        String lockKey = cfg.getType() + ":" + cfg.getCfgid() + ":" + cfg.getDir();
        if (RUNNING.containsKey(lockKey)) {
            throw new RuntimeException("该租户该方向已在执行中，请稍后再试");
        }
        RUNNING.put(lockKey, Long.valueOf(System.currentTimeMillis()));
        long begin = System.currentTimeMillis();
        Batch batch = new Batch();
        batch.cfg = cfg;
        batch.win = win;
        batch.trigger = trigger;
        try {
            String preError = precondition(cfg, trigger);
            if (preError.length() > 0) {
                batch.logId = beginBatch(cfg, win, trigger, operatorName);
                batch.note(preError);
                batch.fail++;
                finishBatch(batch, begin, true);
                return batchJson(batch, preError);
            }
            Cred cred = credential(cfg);
            List<AttMember> members = members(cfg);
            batch.logId = beginBatch(cfg, win, trigger, operatorName);
            batch.note("OA多账户同步规则：" + OaonesConst.attOaAccountRuleText(oaAccountRule(cfg)));
            int dir = cfg.getDir() == null ? OaonesConst.ATT_DIR_BOTH : cfg.getDir().intValue();
            if (dir == OaonesConst.ATT_DIR_BOTH || dir == OaonesConst.ATT_DIR_THIRD2OA) {
                runPull(cfg, cred, members, batch);
            }
            if (dir == OaonesConst.ATT_DIR_BOTH || dir == OaonesConst.ATT_DIR_OA2THIRD) {
                runPush(cfg, cred, members, batch);
            }
            finishBatch(batch, begin, false);
            return batchJson(batch, "");
        } catch (Throwable t) {
            batch.note("执行异常：" + messageOf(t));
            batch.fail++;
            OaonesLog.error("[attSync] 执行考勤同步失败 cfgId=" + cfg.getId(), t);
            try {
                if (batch.logId == null) {
                    batch.logId = beginBatch(cfg, win, trigger, operatorName);
                }
                finishBatch(batch, begin, true);
            } catch (Throwable ignore) {
                // 收尾失败不再往外抛，避免把原始异常盖掉
            }
            JSONObject out = batchJson(batch, "执行失败：" + messageOf(t));
            out.put("error", messageOf(t));
            return out;
        } finally {
            RUNNING.remove(lockKey);
        }
    }

    /**
     * 前置条件（需求 3.1 / 3.10）：功能总开关 → 设置启用（手工执行允许停用）→ 租户级开关 →
     * 考勤应用必须指定且可用。返回空串表示放行，否则是批次失败原因。
     */
    private String precondition(AttSyncCfg cfg, int trigger) {
        if (!OaonesProps.attSyncEnabled()) {
            return "考勤同步功能总开关已关闭（oaones.appsyn.properties#att.sync.enable），不执行任何同步";
        }
        if (trigger != OaonesConst.ATT_TRIGGER_MANUAL
                && (cfg.getEnabled() == null || cfg.getEnabled().intValue() == 0)) {
            return "这条同步设置已停用，不自动执行（停用后只允许手工执行）";
        }
        int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
        int cfgId = cfg.getCfgid() == null ? 0 : cfg.getCfgid().intValue();
        AttSyncCfg sw = tenantRow(type, cfgId);
        if (sw != null && sw.getEnabled() != null && sw.getEnabled().intValue() == 0) {
            return "该租户的考勤同步已整体停用（租户级开关），本轮不执行";
        }
        int appId = cfg.getAppId() == null ? 0 : cfg.getAppId().intValue();
        if (appId <= 0) {
            // 设置行只是快照：按租户当前的考勤应用兜底，避免老数据被误判成“未指定”
            appId = sw == null || sw.getAppId() == null ? 0 : sw.getAppId().intValue();
        }
        if (appId <= 0) {
            return "该租户还没有指定「考勤应用」，不执行同步（考勤数据必须用有考勤权限的应用自己的凭据，"
                    + "不指定也不会回退到租户的通讯录 Secret）：请在租户卡片上指定并校验通过";
        }
        AttAppOption app = appById(type, appId);
        if (app == null) {
            markCheck(cfg, OaonesConst.ATT_CHECK_FAIL,
                    "考勤应用不可用：应用绑定记录已删除或不属于该租户，请重新指定并校验", Integer.valueOf(0));
            return "考勤应用不可用（应用绑定记录已删除或不属于该租户），批次失败";
        }
        return "";
    }

    /** 新建一个批次记录（返回批次ID）。 */
    private Integer beginBatch(AttSyncCfg cfg, Win win, int trigger, String operatorName) {
        String now = OaonesUtil.now();
        AttSyncLog log = new AttSyncLog();
        log.setType(cfg.getType());
        log.setCfgid(cfg.getCfgid());
        log.setTrigger(Integer.valueOf(trigger));
        log.setDir(cfg.getDir());
        log.setCfgId(cfg.getId());
        log.setCfgName(OaonesUtil.s(cfg.getCfgName()));
        log.setWinMode(Integer.valueOf(win.mode));
        log.setWinFrom(OaonesUtil.formatDateTime(win.from));
        log.setWinTo(OaonesUtil.formatDateTime(win.to));
        log.setScopeText(OaonesUtil.s(cfg.getScopeText()));
        log.setDataTypes(cfg.getDataTypes());
        log.setBeginTime(now);
        log.setCostMs(Integer.valueOf(0));
        log.setAddCount(Integer.valueOf(0));
        log.setUpdateCount(Integer.valueOf(0));
        log.setSkipCount(Integer.valueOf(0));
        log.setFailCount(Integer.valueOf(0));
        log.setApiCount(Integer.valueOf(0));
        log.setResult(Integer.valueOf(OaonesConst.SYNC_OP_FAIL));
        log.setMessage("执行中…");
        log.setOperatorName(operatorName);
        log.setModedatacreater(Integer.valueOf(0));
        log.setModedatacreatedate(now.length() >= 10 ? now.substring(0, 10) : now);
        log.setModedatacreatetime(now.length() >= 8 ? now.substring(11) : "");
        log.setModedatamodifydatetime(now);
        Map<String, Object> p = OaonesParam.of();
        p.put("e", log);
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.insertLog(p));
        return log.getId();
    }

    /** 收尾：写批次结果 + 更新设置行健康度（连续失败次数、最近结果）。 */
    private void finishBatch(Batch batch, long begin, boolean forceFail) {
        String now = OaonesUtil.now();
        AttSyncLog log = new AttSyncLog();
        log.setId(batch.logId);
        log.setEndTime(now);
        log.setCostMs(Integer.valueOf((int) (System.currentTimeMillis() - begin)));
        log.setAddCount(Integer.valueOf(batch.add));
        log.setUpdateCount(Integer.valueOf(batch.update));
        log.setSkipCount(Integer.valueOf(batch.skip));
        log.setFailCount(Integer.valueOf(batch.fail));
        log.setApiCount(Integer.valueOf(batch.api));
        boolean failed = forceFail || batch.fail > 0;
        log.setResult(Integer.valueOf(failed ? OaonesConst.SYNC_OP_FAIL : OaonesConst.SYNC_OP_CREATE));
        String message = batch.message();
        log.setMessage(message.length() == 0 ? (failed ? "执行失败" : "执行成功") : message);
        Map<String, Object> p = OaonesParam.of();
        p.put("e", log);
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.updateLog(p));

        AttSyncCfg cfg = batch.cfg;
        int streak = cfg.getFailStreak() == null ? 0 : cfg.getFailStreak().intValue();
        streak = failed ? streak + 1 : 0;
        Map<String, Object> q = OaonesParam.of();
        q.put("id", cfg.getId());
        q.put("lastRunTime", now);
        q.put("lastResult", failed ? Integer.valueOf(OaonesConst.SYNC_OP_FAIL)
                : Integer.valueOf(OaonesConst.SYNC_OP_CREATE));
        q.put("lastResultText", batch.healthText(failed));
        q.put("failStreak", Integer.valueOf(streak));
        q.put("lastLogId", batch.logId);
        q.put("modifier", Integer.valueOf(0));
        q.put("modifyTime", now);
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.updateRunState(q));
        cfg.setFailStreak(Integer.valueOf(streak));
        if (streak >= OaonesProps.attFailAlertThreshold()) {
            // 需求 3.8.9：连续失败到阈值要**显式告警**（页面设置行上也会显示同一句文案）
            new weaver.general.BaseBean().writeLog("[attSync] 考勤同步连续失败告警：" + alertText(cfg)
                    + "（设置ID " + cfg.getId() + "，租户 " + cfg.getCfgid() + "）");
        }
    }

    private JSONObject batchJson(Batch batch, String error) {
        JSONObject out = new JSONObject();
        out.put("log_id", batch.logId);
        out.put("add", batch.add);
        out.put("update", batch.update);
        out.put("skip", batch.skip);
        out.put("fail", batch.fail);
        out.put("api", batch.api);
        out.put("message", error.length() > 0 ? error : batch.message());
        return out;
    }

    /** 一轮执行的计数器与说明。 */
    private static final class Batch {
        private AttSyncCfg cfg;
        private Win win;
        private int trigger;
        private Integer logId;
        private int add;
        private int update;
        private int skip;
        private int fail;
        private int api;
        private final List<String> notes = new ArrayList<String>();

        private void note(String text) {
            if (text == null || text.length() == 0) {
                return;
            }
            if (notes.size() < 12) {
                notes.add(text);
            }
        }

        private String message() {
            StringBuilder sb = new StringBuilder();
            for (String note : notes) {
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append(note);
            }
            return sb.length() > 900 ? sb.substring(0, 900) + "…" : sb.toString();
        }

        private String healthText(boolean failed) {
            return (failed ? "失败：" : "成功：")
                    + "新增 " + add + " / 更新 " + update + " / 跳过 " + skip + " / 失败 " + fail
                    + "，平台请求 " + api + " 次";
        }
    }

    private String messageOf(Throwable t) {
        if (t == null) {
            return "";
        }
        String message = t.getMessage();
        return message == null || message.length() == 0 ? t.getClass().getName() : message;
    }

    private String operatorName(String operatorId) {
        int uid = OaonesUtil.i(operatorId, 0);
        if (uid <= 0) {
            return "";
        }
        try {
            JSONObject user = new OaServiceImpl().getUser(String.valueOf(uid));
            return user == null ? ("#" + uid) : OaonesUtil.s(user.getString("name"));
        } catch (Throwable t) {
            return "#" + uid;
        }
    }

    /* ==================================================================
       七、第三方 → OA：拉取打卡并写进 E9 标准考勤打卡表
       ================================================================== */

    private void runPull(AttSyncCfg cfg, Cred cred, List<AttMember> members, Batch batch) {
        int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
        int dataTypes = cfg.getDataTypes() == null ? 0 : cfg.getDataTypes().intValue();
        if (dataTypes == 0) {
            batch.note("没有勾选任何数据类型，本次不拉取平台打卡");
            return;
        }
        // 同一平台账号可被多个 OA 账户绑定：这里必须保留候选列表，不能再用 Map<账号, 单人>
        // 覆盖；否则“所有账户均同步”实际只会同步最后一个人。
        Map<String, List<AttMember>> byWx = new LinkedHashMap<String, List<AttMember>>();
        Map<Integer, AttMember> byOa = new LinkedHashMap<Integer, AttMember>();
        List<String> wxUsers = new ArrayList<String>();
        for (AttMember member : members) {
            if (member.getOaUserId() != null) {
                byOa.put(member.getOaUserId(), member);
            }
            String wx = OaonesUtil.s(member.getWxUserId());
            if (wx.length() > 0 && member.getBindState() != null && member.getBindState().intValue() == 1) {
                List<AttMember> bound = byWx.get(wx);
                if (bound == null) {
                    bound = new ArrayList<AttMember>();
                    byWx.put(wx, bound);
                }
                bound.add(member);
                if (!wxUsers.contains(wx)) {
                    wxUsers.add(wx);
                }
            }
        }
        if (wxUsers.isEmpty()) {
            batch.note("该范围内没有已绑定平台账号的人员，本次不拉取平台打卡");
            return;
        }
        // 跨天班次：平台按“考勤日期”查询，比打卡时间窗向前 / 向后各多取一天（需求 3.5.4）
        String fromDate = OaonesUtil.shiftDate(OaonesUtil.formatDateTime(batch.win.from).substring(0, 10), -1);
        String toDate = OaonesUtil.shiftDate(OaonesUtil.formatDateTime(batch.win.to).substring(0, 10), 1);
        AttPullContext ctx = new AttPullContext();
        ctx.setType(type);
        ctx.setCfgId(cfg.getCfgid() == null ? 0 : cfg.getCfgid().intValue());
        ctx.setCorpId(cred.key);
        ctx.setCorpSecret(cred.secret);
        ctx.setAgentId(cred.agentId);
        ctx.setUserIds(wxUsers);
        ctx.setFromDate(fromDate);
        ctx.setToDate(toDate);
        ctx.setLimit(OaonesProps.attSyncItemLimit());
        AttPullAdapter adapter = pullAdapter(type, cred.key, cred.secret);
        List<AttPunch> punches = new ArrayList<AttPunch>();
        boolean wantNormal = (dataTypes & OaonesConst.ATT_DATA_NORMAL) != 0;
        boolean wantRemedy = (dataTypes & OaonesConst.ATT_DATA_REMEDY) != 0;
        try {
            ctx.setRemedyOnly(false);
            if (wantNormal || (wantRemedy && type != OaonesConst.TYPE_FEISHU)) {
                batch.api++;
                punches.addAll(adapter.list(ctx));
            }
            if (wantRemedy && type == OaonesConst.TYPE_FEISHU) {
                // 飞书有补卡专用接口：补签卡单独取一次（其它平台同一个接口就带回补卡记录）
                ctx.setRemedyOnly(true);
                batch.api++;
                punches.addAll(adapter.list(ctx));
            }
        } catch (Throwable t) {
            batch.fail++;
            batch.note("拉取平台打卡失败：" + messageOf(t));
            return;
        }
        if (punches.size() > OaonesProps.attSyncItemLimit()) {
            punches = punches.subList(0, OaonesProps.attSyncItemLimit());
            batch.note("本轮达到单次处理上限（" + OaonesProps.attSyncItemLimit() + " 条），剩余下一轮继续");
        }
        String platformSource = OaonesProps.attSignSource(type);
        String remedySource = remedySource(type);
        Set<String> batchKeys = new HashSet<String>();
        Map<String, List<AttPunch>> groups = new LinkedHashMap<String, List<AttPunch>>();
        for (AttPunch punch : punches) {
            List<AttMember> bound = byWx.get(OaonesUtil.s(punch.getWxUserId()));
            long signMs = OaonesUtil.parseDateTime(punch.getSignTime());
            int dataType = punch.getDataType() == null ? OaonesConst.ATT_DATA_NORMAL : punch.getDataType().intValue();
            if (bound == null || bound.isEmpty()) {
                writeSkip(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, null, punch, OaonesConst.ATT_SKIP_NO_BIND,
                        "平台账号 " + OaonesUtil.s(punch.getWxUserId()) + " 在 OA 里没有有效的人员绑定");
                continue;
            }
            boolean remedy = dataType == OaonesConst.ATT_DATA_REMEDY;
            for (AttMember member : bound) {
                String key = dedupKey(member.getOaUserId(), punch, dataType);
                // 只同步主账户：子账户的绑定保留，但本次动作明确记“跳过”，不静默丢弃。
                if (!oaAccountAllowed(cfg, member)) {
                    if (batchKeys.add(key)) {
                        writeSkip(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch,
                                OaonesConst.ATT_SKIP_OA_SUB_ACCOUNT,
                                "平台账号 " + OaonesUtil.s(punch.getWxUserId())
                                        + " 绑定的该 OA 账户是子账户，当前规则只把平台打卡写入主账户");
                    }
                    continue;
                }
                if (member.getStatus() != null && member.getStatus().intValue() >= 4) {
                    writeSkip(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch,
                            OaonesConst.ATT_SKIP_LEFT, "OA 侧人员已离职 / 已失效（status=" + member.getStatus() + "）");
                    continue;
                }
                if (signMs <= 0L) {
                    writeFail(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch,
                            "打卡时间为空或解析失败，保留平台原值：" + OaonesUtil.s(punch.getSignTime()));
                    continue;
                }
                if (signMs < batch.win.from || signMs > batch.win.to) {
                    writeSkip(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch,
                            OaonesConst.ATT_SKIP_OUT_OF_WINDOW,
                            "打卡时间 " + OaonesUtil.s(punch.getSignTime()) + " 不在本次时间窗内");
                    continue;
                }
                if ((remedy && !wantRemedy) || (!remedy && !wantNormal)) {
                    writeSkip(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch,
                            OaonesConst.ATT_SKIP_TYPE_OFF,
                            "本条是" + (remedy ? "补签卡（补卡）" : "正常打卡") + "，该类型没有勾选");
                    continue;
                }
                if (!periodChecked(cfg, punch.getSignType())) {
                    writeSkip(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch,
                            OaonesConst.ATT_SKIP_TYPE_OFF,
                            "打卡时段（签到 / 签退）没有勾选：" + signTypeText(punch.getSignType()));
                    continue;
                }
                if (!batchKeys.add(key)) {
                    continue;   // 同一批里重复返回的同一条打卡
                }
                if (existsLink(cfg, OaonesConst.ATT_DIR_THIRD2OA, member.getOaUserId(), punch, dataType)) {
                    writeSkip(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch,
                            OaonesConst.ATT_SKIP_SYNCED, "这条打卡此前已经写入过 OA（按人员 + 打卡时间 + 类型去重）");
                    continue;
                }
                String groupKey = member.getOaUserId() + "|" + OaonesUtil.s(punch.getSignDate());
                List<AttPunch> group = groups.get(groupKey);
                if (group == null) {
                    group = new ArrayList<AttPunch>();
                    groups.put(groupKey, group);
                }
                group.add(punch);
            }
        }
        // 「先删后插」按（人员 + 打卡日期）成组做：平台这次返回了谁的哪一天，就把那一天
        // 本平台来源的记录整组重写（既幂等，又不会误删时间窗以外的数据），与 E9 标准考勤导入同口径
        Set<String> touched = new LinkedHashSet<String>();
        for (Map.Entry<String, List<AttPunch>> entry : groups.entrySet()) {
            List<AttPunch> group = entry.getValue();
            AttPunch first = group.get(0);
            int split = entry.getKey().indexOf('|');
            if (split <= 0) {
                continue;
            }
            AttMember member = byOa.get(Integer.valueOf(entry.getKey().substring(0, split)));
            if (member == null) {
                continue;
            }
            deleteSignGroup(cfg, member, first, platformSource, remedySource);
            for (AttPunch punch : group) {
                writeImport(cfg, batch, member, punch, platformSource, remedySource);
                touched.add(member.getOaUserId() + "|" + OaonesUtil.s(punch.getSignDate()));
            }
        }
        formatKq(touched, batch);
    }

    /** 一条打卡写进 E9 标准考勤打卡表 + 写映射 + 记明细。 */
    private void writeImport(AttSyncCfg cfg, Batch batch, AttMember member, AttPunch punch,
                             String platformSource, String remedySource) {
        int dataType = punch.getDataType() == null ? OaonesConst.ATT_DATA_NORMAL : punch.getDataType().intValue();
        boolean remedy = dataType == OaonesConst.ATT_DATA_REMEDY;
        String signFrom = remedy ? remedySource : platformSource;
        String signDatetime = OaonesUtil.s(punch.getSignTime());
        String signDate = signDatetime.length() >= 10 ? signDatetime.substring(0, 10)
                : OaonesUtil.s(punch.getSignDate());
        String signTime = signDatetime.length() >= 19 ? signDatetime.substring(11, 19) : signDatetime;
        int signType = punch.getSignType() == null ? OaonesConst.ATT_PERIOD_SIGN_IN
                : punch.getSignType().intValue();
        AttSyncLogItem item = newItem(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, punch, dataType);
        item.setSignDate(signDate);
        item.setSignTime(signTime);
        item.setSignDatetime(signDatetime);
        item.setSignType(Integer.valueOf(signType));
        item.setWinDate(signDate);
        try {
            Map<String, Object> p = OaonesParam.of();
            p.put("oaUserId", member.getOaUserId());
            p.put("signType", Integer.valueOf(signType));
            p.put("signDate", signDate);
            p.put("signTime", signTime);
            p.put("clientAddress", "");
            p.put("isImport", Integer.valueOf(1));
            p.put("signFrom", signFrom);
            p.put("longitude", "");
            p.put("latitude", "");
            p.put("addr", "");
            OaonesMyBatis.run(AttSignMapper.class, m -> m.insertSign(p));
            Object generated = p.get("oaSignId");
            String oaSignId = generated == null ? "" : String.valueOf(generated);
            item.setOaSignId(oaSignId);
            item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_CREATE));
            item.setMessage("写入 OA 标准考勤打卡记录（来源 " + signFrom + "，平台原始类型："
                    + (OaonesUtil.s(punch.getRawType()).length() == 0 ? "平台类型未知" : punch.getRawType()) + "）");
            saveLink(cfg, member, punch, dataType, OaonesConst.ATT_LINK_SOURCE_IMPORT, oaSignId);
            writeItem(item, batch);
            batch.add++;
            OaonesLog.step("[attSync] 导入打卡：人员 " + member.getOaUserId() + " " + signDate + " " + signTime
                    + " 类型 " + signType + " 来源 " + signFrom);
        } catch (Throwable t) {
            item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_FAIL));
            item.setMessage("写入 OA 打卡记录失败：" + messageOf(t));
            writeItem(item, batch);
            batch.fail++;
            OaonesLog.error("[attSync] 导入打卡失败：人员 " + member.getOaUserId() + " " + signDatetime, t);
        }
    }

    /**
     * 「先删后插」：删掉该人员该打卡日期上**本平台来源**的导入记录（正常来源 + 补卡来源），
     * 别的来源（E9 自带导入 / PC / 移动端 / 真补卡流程）一律不碰。
     */
    private void deleteSignGroup(AttSyncCfg cfg, AttMember member, AttPunch first,
                                 String platformSource, String remedySource) {
        String signDate = OaonesUtil.s(first.getSignDate());
        if (signDate.length() == 0) {
            return;
        }
        List<String> sources = new ArrayList<String>();
        sources.add(platformSource);
        sources.add(remedySource);
        try {
            Map<String, Object> p = OaonesParam.of();
            p.put("oaUserId", member.getOaUserId());
            p.put("signDate", signDate);
            p.put("signFroms", sources);
            applyDelete(p, platformSource, remedySource);
        } catch (Throwable t) {
            OaonesLog.error("[attSync] 先删后插清理失败：人员 " + member.getOaUserId() + " " + signDate, t);
        }
    }

    /** 逐个来源删（同一 SQL 里用 in 也行，这里按来源分开删，便于日志定位）。 */
    private void applyDelete(Map<String, Object> base, String platformSource, String remedySource) {
        for (String source : new String[]{platformSource, remedySource}) {
            Map<String, Object> p = new java.util.HashMap<String, Object>(base);
            p.put("signFrom", source);
            OaonesMyBatis.run(AttSignMapper.class, m -> m.deleteSignByUserDate(p));
        }
    }

    /** 触发 E9 标准考勤格式化（只对本次真正写入 / 变更过的人员与日期，含前一天）。 */
    private void formatKq(Set<String> touched, Batch batch) {
        if (touched.isEmpty()) {
            return;
        }
        List<List<Object>> params = new ArrayList<List<Object>>();
        Set<String> dedup = new LinkedHashSet<String>();
        for (String key : touched) {
            int split = key.indexOf('|');
            if (split <= 0) {
                continue;
            }
            String userId = key.substring(0, split);
            String date = key.substring(split + 1);
            String prev = OaonesUtil.shiftDate(date, -1);
            if (dedup.add(userId + "|" + prev)) {
                List<Object> row = new ArrayList<Object>();
                row.add(userId);
                row.add(prev);
                params.add(row);
            }
            if (dedup.add(userId + "|" + date)) {
                List<Object> row = new ArrayList<Object>();
                row.add(userId);
                row.add(date);
                params.add(row);
            }
        }
        try {
            new com.engine.kq.biz.KQFormatBiz().format(params);
            batch.note("已触发 E9 标准考勤格式化（刷新 kq_format_detail / kq_format_total）："
                    + params.size() + " 个「人员 + 日期」");
        } catch (Throwable t) {
            batch.fail++;
            batch.note("打卡已写入，但触发 E9 标准考勤格式化失败（考勤报表 / 异常统计可能未刷新）：" + messageOf(t));
            OaonesLog.error("[attSync] 触发标准考勤格式化失败", t);
        }
    }

    /* ==================================================================
       八、OA → 第三方：把 OA 的打卡写到平台
       ================================================================== */

    private void runPush(AttSyncCfg cfg, Cred cred, List<AttMember> members, Batch batch) {
        int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
        String mode = OaonesProps.attPushMode(type);
        if (OaonesConst.ATT_PUSH_MODE_NONE.equalsIgnoreCase(mode)) {
            // 兜底口径（需求 3.9 第 4 条）：显式标记不支持，不做“看起来在同步、其实没写成”的假成功
            String note = pushCapabilityNote(type);
            batch.skip++;
            AttSyncLogItem item = newItem(cfg, batch, OaonesConst.ATT_DIR_OA2THIRD, null, null, null);
            item.setOaUserId(Integer.valueOf(0));
            item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_SKIP));
            item.setMessage(OaonesConst.ATT_SKIP_PUSH_UNSUPPORTED + "：" + note);
            writeItem(item, batch);
            batch.note("OA→第三方 未执行：" + note);
            return;
        }
        int dataTypes = cfg.getDataTypes() == null ? 0 : cfg.getDataTypes().intValue();
        Map<Integer, AttMember> byOa = new LinkedHashMap<Integer, AttMember>();
        List<Integer> userIds = new ArrayList<Integer>();
        for (AttMember member : members) {
            if (member.getOaUserId() == null) {
                continue;
            }
            byOa.put(member.getOaUserId(), member);
            userIds.add(member.getOaUserId());
        }
        if (userIds.isEmpty()) {
            batch.note("该范围内没有人员，本次不推送 OA 打卡");
            return;
        }
        List<String> excludes = new ArrayList<String>();
        for (String one : OaonesProps.attOaSignExcludeSources().split(",")) {
            String value = one.trim();
            if (value.length() > 0) {
                excludes.add(value);
            }
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("fromDate", OaonesUtil.formatDateTime(batch.win.from).substring(0, 10));
        p.put("toDate", OaonesUtil.formatDateTime(batch.win.to).substring(0, 10));
        p.put("userIds", userIds);
        p.put("excludeSources", excludes);
        p.put("limit", Integer.valueOf(OaonesProps.attSyncItemLimit()));
        List<AttOaPunch> punches = OaonesMyBatis.call(AttSignMapper.class, m -> m.listOaPunch(p));
        if (punches == null || punches.isEmpty()) {
            batch.note("时间窗内没有 OA 侧自有的打卡记录，无需推送");
            return;
        }
        AttPushAdapter adapter = pushAdapter(type, cred.key, cred.secret);
        for (AttOaPunch punch : punches) {
            long signMs = OaonesUtil.parseDateTime(punch.getSignDatetime());
            AttMember member = byOa.get(punch.getOaUserId());
            if (member == null) {
                continue;
            }
            if (!oaAccountAllowed(cfg, member)) {
                writeSkipOa(cfg, batch, member, punch, OaonesConst.ATT_SKIP_OA_SUB_ACCOUNT,
                        "当前设置 “" + OaonesConst.attOaAccountRuleText(oaAccountRule(cfg))
                                + "”，该 OA 账户是子账户");
                continue;
            }
            if (member.getStatus() != null && member.getStatus().intValue() >= 4) {
                writeSkipOa(cfg, batch, member, punch, OaonesConst.ATT_SKIP_LEFT, "OA 侧人员已离职 / 已失效");
                continue;
            }
            if (signMs < batch.win.from || signMs > batch.win.to) {
                writeSkipOa(cfg, batch, member, punch, OaonesConst.ATT_SKIP_OUT_OF_WINDOW,
                        "打卡时间 " + OaonesUtil.s(punch.getSignDatetime()) + " 不在本次时间窗内");
                continue;
            }
            if (!periodChecked(cfg, punch.getSignType())) {
                writeSkipOa(cfg, batch, member, punch, OaonesConst.ATT_SKIP_TYPE_OFF,
                        "打卡时段没有勾选：" + signTypeText(punch.getSignType()));
                continue;
            }
            int dataType = punch.isRemedy() ? OaonesConst.ATT_DATA_REMEDY : OaonesConst.ATT_DATA_NORMAL;
            if ((dataTypes & dataType) == 0) {
                writeSkipOa(cfg, batch, member, punch, OaonesConst.ATT_SKIP_TYPE_OFF,
                        "本条是" + (punch.isRemedy() ? "OA 补卡" : "OA 正常打卡") + "，该类型没有勾选");
                continue;
            }
            String wxUserId = OaonesUtil.s(member.getWxUserId());
            if (wxUserId.length() == 0 || member.getBindState() == null
                    || member.getBindState().intValue() != 1) {
                writeSkipOa(cfg, batch, member, punch, OaonesConst.ATT_SKIP_NO_BIND,
                        "该人员没有绑定平台账号，无法写入平台");
                continue;
            }
            if (existsPushLink(cfg, member.getOaUserId(), punch, dataType)) {
                writeSkipOa(cfg, batch, member, punch, OaonesConst.ATT_SKIP_SYNCED,
                        "这条 OA 打卡此前已经成功推送到平台（同一条只推一次）");
                continue;
            }
            pushOne(cfg, batch, adapter, member, punch, dataType, wxUserId, batch.trigger, "");
        }
    }

    /* ==================================================================
       十、日志：批次列表 / 打卡明细列表 / 重推
       ================================================================== */

    /** 当前登录人在该平台可维护的租户集合（不带租户查日志时用）。 */
    private List<Integer> maintainableCfgIds(int type, String operatorId) {
        List<Integer> ids = new ArrayList<Integer>();
        for (QywxConfig tenant : tenants(type)) {
            if (tenant.getId() != null && canMaintain(String.valueOf(tenant.getId()), operatorId)) {
                ids.add(tenant.getId());
            }
        }
        return ids;
    }

    @Override
    public JSONObject logs(JSONObject params, String operatorId) {
        int type = params.getIntValue("type");
        JSONObject out = new JSONObject();
        Map<String, Object> p = logFilter(params, maintainableCfgIds(type, operatorId));
        p.put("type", Integer.valueOf(type));
        p.put("limit", Integer.valueOf(2000));
        List<AttSyncLog> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listLog(p));
        int total = OaonesMyBatis.call(AttSyncMapper.class, m -> m.countLog(p));
        JSONArray list = new JSONArray();
        if (rows != null) {
            for (AttSyncLog row : rows) {
                list.add(logJson(row));
            }
        }
        out.put("rows", list);
        out.put("total", total);
        return out;
    }

    private JSONObject logJson(AttSyncLog row) {
        JSONObject item = new JSONObject();
        item.put("id", row.getId());
        item.put("tenant_name", OaonesUtil.s(row.getTenantName()));
        item.put("cfg_id", row.getCfgid());
        item.put("cfg_name", OaonesUtil.s(row.getCfgName()));
        item.put("trigger", row.getTrigger() == null ? 0 : row.getTrigger().intValue());
        item.put("trigger_text", OaonesConst.attTriggerText(row.getTrigger() == null
                ? 0 : row.getTrigger().intValue()));
        item.put("dir", row.getDir() == null ? 0 : row.getDir().intValue());
        item.put("dir_text", OaonesConst.attDirText(row.getDir() == null ? 0 : row.getDir().intValue()));
        item.put("win_mode", row.getWinMode() == null ? 0 : row.getWinMode().intValue());
        item.put("win_text", OaonesConst.attWinText(row.getWinMode() == null ? 0 : row.getWinMode().intValue()));
        item.put("win_from", OaonesUtil.s(row.getWinFrom()));
        item.put("win_to", OaonesUtil.s(row.getWinTo()));
        item.put("scope_text", OaonesUtil.s(row.getScopeText()));
        item.put("data_text", OaonesConst.attDataText(row.getDataTypes() == null
                ? 0 : row.getDataTypes().intValue()));
        item.put("begin_time", OaonesUtil.s(row.getBeginTime()));
        item.put("end_time", OaonesUtil.s(row.getEndTime()));
        item.put("cost_ms", row.getCostMs() == null ? 0 : row.getCostMs().intValue());
        item.put("add", row.getAddCount() == null ? 0 : row.getAddCount().intValue());
        item.put("update", row.getUpdateCount() == null ? 0 : row.getUpdateCount().intValue());
        item.put("skip", row.getSkipCount() == null ? 0 : row.getSkipCount().intValue());
        item.put("fail", row.getFailCount() == null ? 0 : row.getFailCount().intValue());
        item.put("api", row.getApiCount() == null ? 0 : row.getApiCount().intValue());
        item.put("result", row.getResult() == null ? 3 : row.getResult().intValue());
        item.put("message", OaonesUtil.s(row.getMessage()));
        item.put("created", OaonesUtil.s(row.getModedatacreatedate()) + " "
                + OaonesUtil.s(row.getModedatacreatetime()));
        return item;
    }

    @Override
    public JSONObject items(JSONObject params, String operatorId) {
        int type = params.getIntValue("type");
        JSONObject out = new JSONObject();
        Map<String, Object> p = logFilter(params, maintainableCfgIds(type, operatorId));
        p.put("type", Integer.valueOf(type));
        p.put("limit", Integer.valueOf(2000));
        List<AttSyncLogItem> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listLogItem(p));
        int total = OaonesMyBatis.call(AttSyncMapper.class, m -> m.countLogItem(p));
        JSONArray list = new JSONArray();
        if (rows != null) {
            for (AttSyncLogItem row : rows) {
                list.add(itemJson(row));
            }
        }
        out.put("rows", list);
        out.put("total", total);
        return out;
    }

    private JSONObject itemJson(AttSyncLogItem row) {
        JSONObject item = new JSONObject();
        item.put("id", row.getId());
        item.put("log_id", row.getLogId());
        item.put("cfg_id", row.getCfgid());
        item.put("tenant_name", OaonesUtil.s(row.getTenantName()));
        item.put("cfg_ref", row.getCfgId());
        item.put("dir", row.getDir() == null ? 0 : row.getDir().intValue());
        item.put("dir_text", OaonesConst.attDirText(row.getDir() == null ? 0 : row.getDir().intValue()));
        item.put("trigger", row.getTrigger());
        item.put("trigger_text", OaonesConst.attTriggerText(row.getTrigger() == null
                ? 0 : row.getTrigger().intValue()));
        item.put("oa_user_id", row.getOaUserId() == null ? 0 : row.getOaUserId().intValue());
        item.put("oa_user_name", OaonesUtil.s(row.getOaUserName()));
        item.put("login_id", OaonesUtil.s(row.getLoginId()));
        item.put("wx_user_id", OaonesUtil.s(row.getWxUserId()));
        item.put("sign_datetime", OaonesUtil.s(row.getSignDatetime()));
        item.put("sign_date", OaonesUtil.s(row.getSignDate()));
        item.put("sign_type", row.getSignType());
        item.put("sign_type_text", signTypeText(row.getSignType()));
        item.put("data_type", row.getDataType());
        item.put("data_text", row.getDataType() != null
                && row.getDataType().intValue() == OaonesConst.ATT_DATA_REMEDY ? "补签卡（补卡）" : "正常打卡");
        item.put("raw_type", OaonesUtil.s(row.getRawType()));
        item.put("raw_summary", OaonesUtil.s(row.getRawSummary()));
        item.put("op", row.getOp() == null ? 3 : row.getOp().intValue());
        item.put("op_text", OaonesConst.syncOpText(row.getOp() == null ? 3 : row.getOp().intValue()));
        item.put("message", OaonesUtil.s(row.getMessage()));
        item.put("oa_sign_id", OaonesUtil.s(row.getOaSignId()));
        item.put("wx_record_id", OaonesUtil.s(row.getWxRecordId()));
        item.put("api_log_id", row.getApiLogId() == null ? 0 : row.getApiLogId().intValue());
        item.put("created", OaonesUtil.s(row.getModedatacreatedate()) + " "
                + OaonesUtil.s(row.getModedatacreatetime()));
        return item;
    }

    /** 批次 / 明细共用的筛选条件（不含 type，由调用方补）。 */
    private Map<String, Object> logFilter(JSONObject params, List<Integer> allowIds) {
        Map<String, Object> p = OaonesParam.of();
        p.put("cfgIds", allowIds);
        if (params.getIntValue("cfgid") > 0) {
            p.put("cfgid", Integer.valueOf(params.getIntValue("cfgid")));
        }
        if (params.getIntValue("cfg_ref") > 0) {
            p.put("cfgId", Integer.valueOf(params.getIntValue("cfg_ref")));
        }
        if (params.getIntValue("log_id") > 0) {
            p.put("logId", Integer.valueOf(params.getIntValue("log_id")));
        }
        if (params.containsKey("dir") && params.getIntValue("dir") >= 0) {
            p.put("dir", Integer.valueOf(params.getIntValue("dir")));
        }
        if (params.containsKey("result") && params.getIntValue("result") >= 0) {
            p.put("result", Integer.valueOf(params.getIntValue("result")));
        }
        if (params.containsKey("op") && params.getIntValue("op") >= 0) {
            p.put("op", Integer.valueOf(params.getIntValue("op")));
        }
        if (params.getIntValue("oa_user_id") > 0) {
            p.put("oaUserId", Integer.valueOf(params.getIntValue("oa_user_id")));
        }
        String[] from = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("from_time")), "00:00:00");
        String[] to = OaonesUtil.splitDateTime(OaonesUtil.s(params.getString("to_time")), "23:59:59");
        p.put("fromDate", from[0]);
        p.put("fromTime", from[1]);
        p.put("toDate", to[0]);
        p.put("toTime", to[1]);
        p.put("signFrom", OaonesUtil.s(params.getString("sign_from")).trim());
        p.put("signTo", OaonesUtil.s(params.getString("sign_to")).trim());
        String keyword = OaonesUtil.s(params.getString("keyword")).trim();
        if (keyword.length() > 0) {
            p.put("keyword", keyword);
            p.put("keywordLike", "%" + keyword + "%");
        }
        return p;
    }

    @Override
    public JSONObject retry(JSONObject params, String operatorId) {
        int itemId = params.getIntValue("item_id");
        Map<String, Object> q = OaonesParam.of();
        q.put("id", Integer.valueOf(itemId));
        AttSyncLogItem row = OaonesMyBatis.call(AttSyncMapper.class, m -> m.getLogItem(q));
        if (row == null) {
            throw new RuntimeException("要重推的打卡明细不存在");
        }
        AttSyncCfg cfg = cfgById(row.getCfgId() == null ? 0 : row.getCfgId().intValue());
        if (cfg == null) {
            throw new RuntimeException("这条明细对应的同步设置已被删除，无法重推");
        }
        assertCfgRight(cfg.getCfgid(), operatorId);
        String preError = precondition(cfg, OaonesConst.ATT_TRIGGER_MANUAL);
        Cred cred = credential(cfg);
        if (cred == null) {
            throw new RuntimeException("未指定可用的「考勤应用」，无法重推");
        }
        int dir = row.getDir() == null ? OaonesConst.ATT_DIR_THIRD2OA : row.getDir().intValue();
        Win win = new Win();
        win.from = OaonesUtil.parseDateTime(OaonesUtil.s(row.getSignDatetime()));
        win.to = win.from <= 0L ? System.currentTimeMillis() : win.from;
        Batch batch = new Batch();
        batch.cfg = cfg;
        batch.win = win;
        batch.trigger = OaonesConst.ATT_TRIGGER_RETRY;
        batch.logId = beginBatch(cfg, win, OaonesConst.ATT_TRIGGER_RETRY, operatorName(operatorId));
        batch.note("重推：按「平台 + 租户 + 人员 + 打卡时间 + 类型」定位，不受当前时间窗限制");
        batch.note("OA多账户同步规则：" + OaonesConst.attOaAccountRuleText(oaAccountRule(cfg)));
        if (preError.length() > 0) {
            batch.note(preError);
            batch.fail++;
            finishBatch(batch, System.currentTimeMillis(), true);
            return batchJson(batch, preError);
        }
        try {
            if (dir == OaonesConst.ATT_DIR_OA2THIRD) {
                retryPush(cfg, cred, batch, row);
            } else {
                retryPull(cfg, cred, batch, row);
            }
        } catch (Throwable t) {
            batch.note("重推失败：" + messageOf(t));
            batch.fail++;
            OaonesLog.error("[attSync] 重推失败 itemId=" + itemId, t);
        }
        finishBatch(batch, System.currentTimeMillis(), false);
        return batchJson(batch, "");
    }

    /** 重推：第三方 → OA（按该人员该日期重新从平台取这条打卡，再按单条幂等写入）。 */
    private void retryPull(AttSyncCfg cfg, Cred cred, Batch batch, AttSyncLogItem row) {
        int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
        AttMember member = memberOf(cfg, row.getOaUserId());
        if (member == null) {
            batch.note("重推失败：OA 侧找不到该人员（可能已删除）");
            batch.fail++;
            return;
        }
        if (!oaAccountAllowed(cfg, member)) {
            batch.note("重推失败：" + OaonesConst.ATT_SKIP_OA_SUB_ACCOUNT
                    + "（当前规则：" + OaonesConst.attOaAccountRuleText(oaAccountRule(cfg)) + "）");
            batch.fail++;
            return;
        }
        String wxUserId = OaonesUtil.s(member.getWxUserId());
        if (wxUserId.length() == 0 || member.getBindState() == null
                || member.getBindState().intValue() != 1) {
            batch.note("重推失败：" + OaonesConst.ATT_SKIP_NO_BIND);
            batch.fail++;
            return;
        }
        String signDate = OaonesUtil.s(row.getSignDate());
        boolean remedy = row.getDataType() != null
                && row.getDataType().intValue() == OaonesConst.ATT_DATA_REMEDY;
        AttPullContext ctx = new AttPullContext();
        ctx.setType(type);
        ctx.setCfgId(cfg.getCfgid() == null ? 0 : cfg.getCfgid().intValue());
        ctx.setCorpId(cred.key);
        ctx.setCorpSecret(cred.secret);
        ctx.setAgentId(cred.agentId);
        ctx.setUserIds(java.util.Collections.singletonList(wxUserId));
        ctx.setFromDate(OaonesUtil.shiftDate(signDate, -1));
        ctx.setToDate(OaonesUtil.shiftDate(signDate, 1));
        ctx.setLimit(200);
        ctx.setRemedyOnly(remedy && type == OaonesConst.TYPE_FEISHU);
        List<AttPunch> punches;
        try {
            batch.api++;
            punches = pullAdapter(type, cred.key, cred.secret).list(ctx);
        } catch (Throwable t) {
            batch.note("重推失败：拉取平台打卡出错：" + messageOf(t));
            batch.fail++;
            return;
        }
        AttPunch found = null;
        if (punches != null) {
            for (AttPunch punch : punches) {
                if (OaonesUtil.s(punch.getSignTime()).equals(OaonesUtil.s(row.getSignDatetime()))
                        && punch.getSignType() != null && punch.getSignType().equals(row.getSignType())) {
                    found = punch;
                    break;
                }
            }
        }
        if (found == null) {
            batch.note("重推失败：平台未再返回这条打卡（可能已被平台删除，或不在该人员该日期的查询范围内）");
            batch.fail++;
            return;
        }
        int dataType = found.getDataType() == null
                ? OaonesConst.ATT_DATA_NORMAL : found.getDataType().intValue();
        if (countSign(cfg, member, found) > 0) {
            AttSyncLogItem item = newItem(cfg, batch, OaonesConst.ATT_DIR_THIRD2OA, member, found,
                    Integer.valueOf(dataType));
            item.setSignDatetime(OaonesUtil.s(found.getSignTime()));
            item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_SKIP));
            item.setMessage("重推：" + OaonesConst.ATT_SKIP_SYNCED + "（OA 里已经有这条打卡）");
            writeItem(item, batch);
            batch.skip++;
            return;
        }
        writeImport(cfg, batch, member, found, OaonesProps.attSignSource(type), remedySource(type));
    }

    /** 重推：OA → 第三方（按 OA 打卡记录ID 取回那条打卡再写一次）。 */
    private void retryPush(AttSyncCfg cfg, Cred cred, Batch batch, AttSyncLogItem row) {
        AttMember member = memberOf(cfg, row.getOaUserId());
        if (member == null) {
            batch.note("重推失败：OA 侧找不到该人员（可能已删除）");
            batch.fail++;
            return;
        }
        if (!oaAccountAllowed(cfg, member)) {
            batch.note("重推失败：" + OaonesConst.ATT_SKIP_OA_SUB_ACCOUNT
                    + "（当前规则：" + OaonesConst.attOaAccountRuleText(oaAccountRule(cfg)) + "）");
            batch.fail++;
            return;
        }
        AttOaPunch punch = null;
        if (OaonesUtil.s(row.getOaSignId()).length() > 0) {
            Map<String, Object> p = OaonesParam.of();
            p.put("signId", OaonesUtil.s(row.getOaSignId()));
            punch = OaonesMyBatis.call(AttSignMapper.class, m -> m.getOaPunch(p));
        }
        if (punch == null) {
            punch = new AttOaPunch();
            punch.setSignId(OaonesUtil.s(row.getOaSignId()));
            punch.setOaUserId(row.getOaUserId());
            punch.setSignDate(OaonesUtil.s(row.getSignDate()));
            punch.setSignTime(OaonesUtil.s(row.getSignTime()));
            punch.setSignDatetime(OaonesUtil.s(row.getSignDatetime()));
            punch.setSignType(row.getSignType());
            punch.setSignFrom("");
        }
        punch.setRemedy(row.getDataType() != null
                && row.getDataType().intValue() == OaonesConst.ATT_DATA_REMEDY);
        String wxUserId = OaonesUtil.s(member.getWxUserId());
        if (wxUserId.length() == 0) {
            batch.note("重推失败：" + OaonesConst.ATT_SKIP_NO_BIND);
            batch.fail++;
            return;
        }
        int dataType = punch.isRemedy() ? OaonesConst.ATT_DATA_REMEDY : OaonesConst.ATT_DATA_NORMAL;
        if (existsPushLink(cfg, member.getOaUserId(), punch, dataType)) {
            AttSyncLogItem item = newItem(cfg, batch, OaonesConst.ATT_DIR_OA2THIRD, member, null,
                    Integer.valueOf(dataType));
            item.setSignDatetime(OaonesUtil.s(punch.getSignDatetime()));
            item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_SKIP));
            item.setMessage("重推：" + OaonesConst.ATT_SKIP_SYNCED + "（这条 OA 打卡此前已成功推送到平台）");
            writeItem(item, batch);
            batch.skip++;
            return;
        }
        pushOne(cfg, batch, pushAdapter(cfg.getType() == null ? 0 : cfg.getType().intValue(),
                cred.key, cred.secret), member, punch, dataType, wxUserId,
                OaonesConst.ATT_TRIGGER_RETRY, "重推：");
    }

    /** 按 OA 人员ID 取该设置范围内的成员（重推用；不做分权过滤，重推入口本身已校验租户）。 */
    private AttMember memberOf(AttSyncCfg cfg, Integer oaUserId) {
        if (oaUserId == null || oaUserId.intValue() <= 0) {
            return null;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("cfgId", cfg.getCfgid());
        p.put("scoped", Boolean.TRUE);
        p.put("userIds", java.util.Collections.singletonList(oaUserId));
        List<AttMember> rows = OaonesMyBatis.call(AttSyncMapper.class, m -> m.listMembers(p));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    /** 该人员该打卡在 OA 标准表里是否已有本平台来源的记录（重推幂等判断）。 */
    private int countSign(AttSyncCfg cfg, AttMember member, AttPunch punch) {
        int type = cfg.getType() == null ? 0 : cfg.getType().intValue();
        List<String> sources = new ArrayList<String>();
        sources.add(OaonesProps.attSignSource(type));
        sources.add(remedySource(type));
        Map<String, Object> p = OaonesParam.of();
        p.put("oaUserId", member.getOaUserId());
        p.put("signDate", OaonesUtil.s(punch.getSignTime()).length() >= 10
                ? punch.getSignTime().substring(0, 10) : OaonesUtil.s(punch.getSignDate()));
        p.put("signTime", OaonesUtil.s(punch.getSignTime()).length() >= 19
                ? punch.getSignTime().substring(11, 19) : "");
        p.put("signType", punch.getSignType());
        p.put("signFroms", sources);
        Integer count = OaonesMyBatis.call(AttSignMapper.class, m -> Integer.valueOf(m.countSignByKey(p)));
        return count == null ? 0 : count.intValue();
    }

    /** 推一条 OA 打卡（正常执行与「重推」共用）。 */
    private void pushOne(AttSyncCfg cfg, Batch batch, AttPushAdapter adapter, AttMember member,
                         AttOaPunch punch, int dataType, String wxUserId, int trigger, String prefix) {
        AttPushContext ctx = new AttPushContext();
        ctx.setType(cfg.getType() == null ? 0 : cfg.getType().intValue());
        ctx.setCfgId(cfg.getCfgid() == null ? 0 : cfg.getCfgid().intValue());
        Cred cred = credential(cfg);
        if (cred != null) {
            ctx.setCorpId(cred.key);
            ctx.setCorpSecret(cred.secret);
            ctx.setAgentId(cred.agentId);
        }
        ctx.setPunch(punch);
        ctx.setWxUserId(wxUserId);
        ctx.setMode(OaonesProps.attPushMode(ctx.getType()));
        ctx.setOaUserName(OaonesUtil.s(member == null ? "" : member.getName()));
        AttSyncLogItem item = newItem(cfg, batch, OaonesConst.ATT_DIR_OA2THIRD, member, null, dataType);
        item.setTrigger(Integer.valueOf(trigger));
        item.setSignDate(OaonesUtil.s(punch.getSignDate()));
        item.setSignTime(OaonesUtil.s(punch.getSignTime()));
        item.setSignDatetime(OaonesUtil.s(punch.getSignDatetime()));
        item.setSignType(punch.getSignType());
        item.setOaSignId(OaonesUtil.s(punch.getSignId()));
        item.setWxRecordId("");
        try {
            batch.api++;
            String credential = adapter.push(ctx);
            item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_CREATE));
            item.setWxRecordId(OaonesUtil.s(credential));
            item.setMessage(prefix + "已写入平台（方式：" + ctx.getMode() + "，平台凭据："
                    + (OaonesUtil.s(credential).length() == 0 ? "平台未返回" : credential) + "）");
            savePushLink(cfg, member, punch, dataType, credential);
            writeItem(item, batch);
            batch.add++;
        } catch (Throwable t) {
            item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_FAIL));
            item.setMessage(prefix + "写入平台失败：" + messageOf(t));
            writeItem(item, batch);
            batch.fail++;
        }
    }

    /* ==================================================================
       九、明细 / 映射的写入与查询
       ================================================================== */

    private AttSyncLogItem newItem(AttSyncCfg cfg, Batch batch, int dir, AttMember member,
                                   AttPunch punch, Integer dataType) {
        String now = OaonesUtil.now();
        AttSyncLogItem item = new AttSyncLogItem();
        item.setLogId(batch.logId);
        item.setType(cfg.getType());
        item.setCfgid(cfg.getCfgid());
        item.setCfgId(cfg.getId());
        item.setDir(Integer.valueOf(dir));
        item.setTrigger(Integer.valueOf(batch.trigger));
        if (member != null) {
            item.setOaUserId(member.getOaUserId());
            item.setOaUserName(OaonesUtil.s(member.getName()));
            item.setLoginId(OaonesUtil.s(member.getLoginId()));
            item.setWxUserId(OaonesUtil.s(member.getWxUserId()));
        }
        if (punch != null) {
            item.setWxUserId(OaonesUtil.s(punch.getWxUserId()));
            item.setRawType(OaonesUtil.s(punch.getRawType()).length() == 0 ? "平台类型未知" : punch.getRawType());
            item.setRawSummary(OaonesUtil.s(punch.getRawSummary()));
            item.setWxRecordId(OaonesUtil.s(punch.getWxRecordId()));
        }
        item.setDataType(dataType);
        item.setModedatacreater(Integer.valueOf(0));
        item.setModedatacreatedate(now.length() >= 10 ? now.substring(0, 10) : now);
        item.setModedatacreatetime(now.length() >= 8 ? now.substring(11) : "");
        item.setModedatamodifydatetime(now);
        return item;
    }

    private void writeItem(AttSyncLogItem item, Batch batch) {
        try {
            Map<String, Object> p = OaonesParam.of();
            p.put("e", item);
            OaonesMyBatis.run(AttSyncMapper.class, m -> m.insertLogItem(p));
        } catch (Throwable t) {
            OaonesLog.error("[attSync] 写打卡明细失败", t);
        }
    }

    private void writeSkip(AttSyncCfg cfg, Batch batch, int dir, AttMember member, AttPunch punch,
                           String reason, String detail) {
        AttSyncLogItem item = newItem(cfg, batch, dir, member, punch,
                punch == null ? null : punch.getDataType());
        if (punch != null) {
            item.setSignDatetime(OaonesUtil.s(punch.getSignTime()));
            item.setSignDate(OaonesUtil.s(punch.getSignTime()).length() >= 10
                    ? punch.getSignTime().substring(0, 10) : OaonesUtil.s(punch.getSignDate()));
            item.setSignTime(OaonesUtil.s(punch.getSignTime()).length() >= 19
                    ? punch.getSignTime().substring(11, 19) : "");
            item.setSignType(punch.getSignType());
            item.setWinDate(OaonesUtil.s(item.getSignDate()));
        }
        item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_SKIP));
        item.setMessage(reason + (detail.length() == 0 ? "" : "（" + detail + "）"));
        writeItem(item, batch);
        batch.skip++;
    }

    private void writeFail(AttSyncCfg cfg, Batch batch, int dir, AttMember member, AttPunch punch, String reason) {
        AttSyncLogItem item = newItem(cfg, batch, dir, member, punch,
                punch == null ? null : punch.getDataType());
        item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_FAIL));
        item.setMessage(reason);
        writeItem(item, batch);
        batch.fail++;
    }

    private void writeSkipOa(AttSyncCfg cfg, Batch batch, AttMember member, AttOaPunch punch,
                             String reason, String detail) {
        AttSyncLogItem item = newItem(cfg, batch, OaonesConst.ATT_DIR_OA2THIRD, member, null,
                Integer.valueOf(punch.isRemedy() ? OaonesConst.ATT_DATA_REMEDY : OaonesConst.ATT_DATA_NORMAL));
        item.setSignDate(OaonesUtil.s(punch.getSignDate()));
        item.setSignTime(OaonesUtil.s(punch.getSignTime()));
        item.setSignDatetime(OaonesUtil.s(punch.getSignDatetime()));
        item.setSignType(punch.getSignType());
        item.setOaSignId(OaonesUtil.s(punch.getSignId()));
        item.setRawType(punch.isRemedy() ? "OA 补卡（来源 " + OaonesUtil.s(punch.getSignFrom()) + "）"
                : "OA 正常打卡（来源 " + OaonesUtil.s(punch.getSignFrom()) + "）");
        item.setOp(Integer.valueOf(OaonesConst.SYNC_OP_SKIP));
        item.setMessage(reason + (detail.length() == 0 ? "" : "（" + detail + "）"));
        writeItem(item, batch);
        batch.skip++;
    }

    /** 导入方向是否已经有映射（去重；平台给出记录ID 时按「OA人员 + 记录ID」判）。 */
    private boolean existsLink(AttSyncCfg cfg, int dir, Integer oaUserId, AttPunch punch, int dataType) {
        Map<String, Object> p = OaonesParam.of();
        p.put("type", cfg.getType());
        p.put("cfgid", cfg.getCfgid());
        p.put("dir", Integer.valueOf(dir));
        p.put("oaUserId", oaUserId);
        String recordId = OaonesUtil.s(punch.getWxRecordId());
        if (recordId.length() > 0) {
            p.put("wxRecordId", recordId);
            AttSyncLink byRecord = OaonesMyBatis.call(AttSyncMapper.class, m -> m.findLinkByRecord(p));
            if (byRecord != null) {
                return true;
            }
        }
        p.put("signDate", OaonesUtil.s(punch.getSignTime()).length() >= 10
                ? punch.getSignTime().substring(0, 10) : OaonesUtil.s(punch.getSignDate()));
        p.put("signTime", OaonesUtil.s(punch.getSignTime()).length() >= 19
                ? punch.getSignTime().substring(11, 19) : "");
        p.put("signType", punch.getSignType());
        p.put("dataType", Integer.valueOf(dataType));
        return OaonesMyBatis.call(AttSyncMapper.class, m -> m.findLinkByKey(p)) != null;
    }

    /** 推送方向是否已经成功推过（按 OA 打卡记录ID 优先，其次按人员 + 打卡时间 + 类型）。 */
    private boolean existsPushLink(AttSyncCfg cfg, Integer oaUserId, AttOaPunch punch, int dataType) {
        Map<String, Object> p = OaonesParam.of();
        p.put("type", cfg.getType());
        p.put("cfgid", cfg.getCfgid());
        p.put("dir", Integer.valueOf(OaonesConst.ATT_DIR_OA2THIRD));
        p.put("oaUserId", oaUserId);
        p.put("signDate", OaonesUtil.s(punch.getSignDate()));
        p.put("signTime", OaonesUtil.s(punch.getSignTime()));
        p.put("signType", punch.getSignType());
        p.put("dataType", Integer.valueOf(dataType));
        return OaonesMyBatis.call(AttSyncMapper.class, m -> m.findLinkByKey(p)) != null;
    }

    private void saveLink(AttSyncCfg cfg, AttMember member, AttPunch punch, int dataType,
                          int source, String oaSignId) {
        AttSyncLink link = new AttSyncLink();
        link.setType(cfg.getType());
        link.setCfgid(cfg.getCfgid());
        link.setDir(Integer.valueOf(OaonesConst.ATT_DIR_THIRD2OA));
        link.setOaUserId(member.getOaUserId());
        link.setWxUserId(OaonesUtil.s(punch.getWxUserId()));
        link.setSignDate(OaonesUtil.s(punch.getSignTime()).length() >= 10
                ? punch.getSignTime().substring(0, 10) : OaonesUtil.s(punch.getSignDate()));
        link.setSignTime(OaonesUtil.s(punch.getSignTime()).length() >= 19
                ? punch.getSignTime().substring(11, 19) : "");
        link.setSignType(punch.getSignType());
        link.setDataType(Integer.valueOf(dataType));
        link.setWxRecordId(OaonesUtil.s(punch.getWxRecordId()));
        link.setOaSignId(OaonesUtil.s(oaSignId));
        link.setSource(Integer.valueOf(source));
        link.setLastTime(OaonesUtil.now());
        link.setRawType(OaonesUtil.s(punch.getRawType()));
        Map<String, Object> p = OaonesParam.of();
        p.put("e", link);
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.insertLink(p));
    }

    private void savePushLink(AttSyncCfg cfg, AttMember member, AttOaPunch punch, int dataType,
                              String platformCredential) {
        AttSyncLink link = new AttSyncLink();
        link.setType(cfg.getType());
        link.setCfgid(cfg.getCfgid());
        link.setDir(Integer.valueOf(OaonesConst.ATT_DIR_OA2THIRD));
        link.setOaUserId(member == null ? null : member.getOaUserId());
        link.setWxUserId(OaonesUtil.s(member == null ? "" : member.getWxUserId()));
        link.setSignDate(OaonesUtil.s(punch.getSignDate()));
        link.setSignTime(OaonesUtil.s(punch.getSignTime()));
        link.setSignType(punch.getSignType());
        link.setDataType(Integer.valueOf(dataType));
        link.setWxRecordId(OaonesUtil.s(platformCredential));
        link.setOaSignId(OaonesUtil.s(punch.getSignId()));
        link.setSource(Integer.valueOf(OaonesConst.ATT_LINK_SOURCE_PUSH));
        link.setLastTime(OaonesUtil.now());
        link.setRawType(punch.isRemedy() ? "OA 补卡" : "OA 正常打卡");
        Map<String, Object> p = OaonesParam.of();
        p.put("e", link);
        OaonesMyBatis.run(AttSyncMapper.class, m -> m.insertLink(p));
    }

    private String dedupKey(Integer oaUserId, AttPunch punch, int dataType) {
        return oaUserId + "|" + OaonesUtil.s(punch.getSignTime()) + "|" + punch.getSignType() + "|" + dataType;
    }

    /** 反补卡来源标识（E9 标准口径：以 card: 开头即视为补卡）。 */
    private String remedySource(int type) {
        return OaonesProps.attSignRemedyPrefix() + "|oaones|"
                + (type == OaonesConst.TYPE_DINGTALK ? "dingtalk"
                : (type == OaonesConst.TYPE_FEISHU ? "feishu" : "wecom"));
    }

    private boolean periodChecked(AttSyncCfg cfg, Integer signType) {
        int periods = cfg.getSignPeriods() == null ? OaonesConst.ATT_PERIOD_BOTH : cfg.getSignPeriods().intValue();
        int want = signType != null && signType.intValue() == OaonesConst.ATT_PERIOD_SIGN_OUT
                ? OaonesConst.ATT_PERIOD_SIGN_OUT : OaonesConst.ATT_PERIOD_SIGN_IN;
        return (periods & want) != 0;
    }

    private String signTypeText(Integer signType) {
        return signType != null && signType.intValue() == OaonesConst.ATT_PERIOD_SIGN_OUT ? "签退（下班卡）"
                : "签到（上班卡）";
    }

    /** 一条设置的凭据（考勤应用）。 */
    private static final class Cred {
        private String key = "";
        private String secret = "";
        private String agentId = "";
        /** 应用没有单独配 Secret、本次用的是租户的 Secret（校验时提示一下）。 */
        private boolean useTenantSecret;
    }
}
