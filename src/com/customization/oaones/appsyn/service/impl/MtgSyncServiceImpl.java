package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkScheduleApi;
import com.customization.oaones.appsyn.api.feishu.FeishuScheduleApi;
import com.customization.oaones.appsyn.api.wecom.WeComScheduleApi;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkApi;
import com.customization.oaones.appsyn.api.feishu.FeishuApi;
import com.customization.oaones.appsyn.api.wecom.WeComApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesCronExpr;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesHttp;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesParam;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.OaonesWorkday;
import com.customization.oaones.appsyn.dao.MtgSyncMapper;
import com.customization.oaones.appsyn.dao.MtgCronMapper;
import com.customization.oaones.appsyn.dao.MtgPlanItemMapper;
import com.customization.oaones.appsyn.dao.wecom.QywxConfigMapper;
import com.customization.oaones.appsyn.dto.MtgOaItem;
import com.customization.oaones.appsyn.dto.MtgAppOption;
import com.customization.oaones.appsyn.dto.MtgPlatformItem;
import com.customization.oaones.appsyn.dto.MtgPushRequest;
import com.customization.oaones.appsyn.dto.OaAccountRow;
import com.customization.oaones.appsyn.dao.HrmMapper;
import com.customization.oaones.appsyn.entity.MtgSyncCfg;
import com.customization.oaones.appsyn.entity.MtgCron;
import com.customization.oaones.appsyn.entity.MtgPlanItem;
import com.customization.oaones.appsyn.entity.KqGroup;
import com.customization.oaones.appsyn.entity.MtgSyncLink;
import com.customization.oaones.appsyn.entity.MtgSyncLog;
import com.customization.oaones.appsyn.entity.MtgSyncUser;
import com.customization.oaones.appsyn.entity.MtgOaWrite;
import com.customization.oaones.appsyn.entity.wecom.QywxConfig;
import com.customization.oaones.appsyn.service.MtgPullAdapter;
import com.customization.oaones.appsyn.service.MtgPullContext;
import com.customization.oaones.appsyn.service.MtgPushAdapter;
import com.customization.oaones.appsyn.service.MtgPushContext;
import com.customization.oaones.appsyn.service.MtgSyncService;
import com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会议 / 日程同步实现（一级菜单「会议同步」/「日程同步」）。
 *
 * <p>会议（{@code biz = 0}，来源 E9 Meeting）与日程（{@code biz = 1}，来源 E9 WorkPlan）
 * 共用同一套逻辑：按租户的设置取“分部范围”内的对象，逐个推送到该平台（企微 / 钉钉 / 飞书）的日程，
 * 逐条写 {@code uf_oaones_mtg_sync_log}；页面「同步记录」直接读这张表。</p>
 *
 * <p>平台差异收敛在 {@link MtgPushAdapter}（企微 / 钉钉 / 飞书各一个实现），本类只做分派。</p>
 */
public class MtgSyncServiceImpl implements MtgSyncService {

    /** 一次同步最多处理多少条对象（防止一个租户一个窗口内的对象过多把接口打满）。 */
    private static final int ATTENDEE_LIMIT = 200;

    /**
     * 一轮同步里“删除对账 / 变更比对”最多看多少条映射（含时间窗外的历史对象）。
     *
     * <p>映射表一行一个对象，正常情况下远小于这个数；设上限只是防止某个租户历史对象极多时
     * 把一轮拉得太长（超出的部分下一轮继续）。</p>
     */
    private static final int LINK_SCAN_LIMIT = 2000;

    /**
     * 计划任务：判断“下一次落在本时段内的执行时间”时的迭代上限。
     *
     * <p>例如白天频率“每小时一次”、当前 17:30：day cron 的 18:00 / 19:00 … 都不算白天，
     * 要一直找到次日 9:00。上限只是兜底（配置成“一天一次”这类不可能落在白天时，
     * 该时段就当作不自动执行，页面上“下次执行时间”会如实显示）。</p>
     */
    private static final int PLAN_ITER_LIMIT = 40;

    /**
     * 计划任务按“仅工作日 / 仅非工作日”执行时，找下一次执行时间的迭代上限。
     *
     * <p>比 {@link #PLAN_ITER_LIMIT} 大得多：周末 + 长假（春节、国庆连着 7~8 天）时，
     * “每小时一次”的表达式要往后迭代几百次才落到下一个工作日。</p>
     */
    private static final int PLAN_ITER_LIMIT_DAYTYPE = 500;

    /**
     * 时间段明细（按时间段同步）向后最多找多少天。
     *
     * <p>“仅工作日”配在明细里时，最坏情况要跨过 8 天长假，所以上限给到 60 天；
     * 一天一天找，代价可以忽略。</p>
     */
    private static final int PLAN_ITEM_LOOKAHEAD_DAYS = 60;

    /** 计划任务那一行的主键：{@code uf_oaones_mtg_cron} 与时间段明细 {@code plan_id} 都用它。 */
    private static final int PLAN_ID = 1;

    /** 计划（cron）预测用的时间格式：yyyy-MM-dd HH:mm:ss。 */
    private static final String PLAN_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** “计划任务表读不到”只提示一次（调度器每 60 秒读一次，表没建好时不刷日志）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean PLAN_ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** “计划任务时间段明细表读不到”同样只提示一次（老库还没执行建表脚本时）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean PLAN_ITEM_ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final ConfigServiceImpl configService = new ConfigServiceImpl();

    // ==================================================================
    // 「同步设置」列表
    // ==================================================================

    @Override
    public JSONArray listSettings(int biz, int type, String operatorId) {
        JSONArray result = new JSONArray();
        List<QywxConfig> tenants = tenants(type);
        Map<Integer, MtgSyncCfg> cfgMap = new HashMap<Integer, MtgSyncCfg>();
        for (MtgSyncCfg row : queryCfgs(biz, type, null)) {
            if (row.getCfgid() != null) {
                cfgMap.put(row.getCfgid(), row);
            }
        }
        // 当前登录人可维护租户下的“绑定应用”候选：会议 / 日程同步要选一个**有日程权限的应用**
        // （用它的 Secret 换 access_token 调平台日程接口）；一个租户可绑多个应用，所以逐租户给出候选
        List<Integer> allowIds = new ArrayList<Integer>();
        for (QywxConfig tenant : tenants) {
            if (tenant.getId() != null && configService.canMaintain(String.valueOf(tenant.getId()), operatorId)) {
                allowIds.add(tenant.getId());
            }
        }
        Map<Integer, JSONArray> appMap = appOptionsByCfg(type, allowIds);
        for (QywxConfig tenant : tenants) {
            if (tenant.getId() == null || !configService.canMaintain(String.valueOf(tenant.getId()), operatorId)) {
                continue;
            }
            MtgSyncCfg cfg = cfgMap.get(tenant.getId());
            JSONObject item = new JSONObject();
            item.put("cfg_id", String.valueOf(tenant.getId()));
            item.put("cfg_name", OaonesUtil.s(tenant.getCorpName()));
            item.put("corp_id", OaonesUtil.s(tenant.getCorpId()));
            item.put("sync_enable", tenant.getQy() == null ? "1" : String.valueOf(tenant.getQy()));
            // 租户级同步开关：**必须显式开启**（没配过 = 关闭）——与同步引擎同一口径
            // （调度器只扫 enabled = 1 的设置），避免“页面显示已开启、实际没有同步”。
            // 个人“默认所有人同步”是另一层口径（见 entity.MtgSyncUser），与这里无关。
            item.put("enabled", cfg == null || cfg.getEnabled() == null ? "0" : String.valueOf(cfg.getEnabled()));
            // 同步方向（两个复选框，可双选，默认双选）：1 选中 / 0 不选
            item.put("dir_oa2third", cfg == null || cfg.getDirOa2Third() == null
                    ? "1" : String.valueOf(cfg.getDirOa2Third()));
            item.put("dir_third2oa", cfg == null || cfg.getDirThird2Oa() == null
                    ? "1" : String.valueOf(cfg.getDirThird2Oa()));
            // 绑定应用：本租户该业务同步时用哪个“应用绑定”（0 / 空 = 未指定，回退租户配置的
            // Secret + AgentId，与加该列之前的行为一致）；候选应用随行带回，页面下拉直接渲染。
            Integer appId = cfg == null || cfg.getAppId() == null ? 0 : cfg.getAppId();
            JSONArray apps = appMap.get(tenant.getId());
            if (apps == null) { apps = new JSONArray(); }
            JSONObject app = findAppOption(apps, appId);
            item.put("app_id", String.valueOf(appId));
            item.put("app_name", app == null ? "" : OaonesUtil.s(app.getString("app_name")));
            item.put("app_agent_id", app == null ? "" : OaonesUtil.s(app.getString("agent_id")));
            item.put("app_missing", appId > 0 && app == null ? "1" : "0");
            item.put("apps", apps);
            item.put("update_time", cfg == null
                    ? OaonesUtil.s(tenant.getModedatamodifydatetime())
                    : OaonesUtil.s(cfg.getModedatamodifydatetime()));
            result.add(item);
        }
        OaonesLog.step("会议/日程同步设置列表 biz=" + biz + " type=" + type + " 条数=" + result.size());
        return result;
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        int cfgId = params.getIntValue("cfg_id");
        if (cfgId <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        assertCfgRight(cfgId, operatorId);
        int enabled = params.containsKey("enabled") ? params.getIntValue("enabled") : 1;
        int dirOa2Third = normDir(params.containsKey("dir_oa2third")
                ? params.getIntValue("dir_oa2third") : 1);
        int dirThird2Oa = normDir(params.containsKey("dir_third2oa")
                ? params.getIntValue("dir_third2oa") : 1);
        // 绑定应用：带 app_id 才处理（不带时插入落 0、更新保持原值），并校验应用确属本租户
        boolean hasApp = params.containsKey("app_id");
        int appId = hasApp ? params.getIntValue("app_id") : 0;
        if (hasApp) {
            assertAppOfCfg(type, cfgId, appId);
        }

        MtgSyncCfg exist = getCfg(biz, type, cfgId);
        String now = OaonesUtil.now();
        String date = OaonesUtil.nowDate();
        String time = OaonesUtil.nowTime();
        String operator = OaonesUtil.s(operatorId);
        Map<String, Object> p = OaonesParam.of();
        if (exist == null) {
            MtgSyncCfg cfg = new MtgSyncCfg();
            cfg.setBiz(biz);
            cfg.setType(type);
            cfg.setCfgid(cfgId);
            cfg.setEnabled(enabled);
            cfg.setDirOa2Third(dirOa2Third);
            cfg.setDirThird2Oa(dirThird2Oa);
            cfg.setAppId(appId);
            cfg.setRemark("");
            cfg.setModedatacreater(OaonesUtil.i(operator, 0));
            cfg.setModedatacreatedate(date);
            cfg.setModedatacreatetime(time);
            cfg.setModedatamodifier(OaonesUtil.i(operator, 0));
            cfg.setModedatamodifydatetime(now);
            p.put("e", cfg);
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertCfg(p));
        } else {
            exist.setEnabled(enabled);
            if (params.containsKey("dir_oa2third")) {
                exist.setDirOa2Third(dirOa2Third);
            } else if (exist.getDirOa2Third() == null) {
                exist.setDirOa2Third(1);
            }
            if (params.containsKey("dir_third2oa")) {
                exist.setDirThird2Oa(dirThird2Oa);
            } else if (exist.getDirThird2Oa() == null) {
                exist.setDirThird2Oa(1);
            }
            if (hasApp) {
                exist.setAppId(appId);
            } else if (exist.getAppId() == null) {
                exist.setAppId(0);
            }
            exist.setModedatamodifier(OaonesUtil.i(operator, 0));
            exist.setModedatamodifydatetime(now);
            p.put("e", exist);
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateCfg(p));
        }
        return getSettingsRow(biz, type, cfgId, operatorId);
    }

    @Override
    public JSONObject setEnabled(int biz, int type, String cfgId, int enabled, String operatorId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        assertCfgRight(id, operatorId);
        MtgSyncCfg cfg = getCfg(biz, type, id);
        if (cfg == null) {
            // 还没配过：先落一条默认设置（开启 / 无范围），再改开关，页面无需先“保存范围”
            JSONObject params = new JSONObject();
            params.put("biz", biz);
            params.put("type", type);
            params.put("cfg_id", id);
            params.put("enabled", enabled);
            save(params, operatorId);
            return getSettingsRow(biz, type, id, operatorId);
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("id", cfg.getId());
        p.put("enabled", enabled);
        p.put("modifier", OaonesUtil.i(operatorId, 0));
        p.put("modifyTime", OaonesUtil.now());
        OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateEnabled(p));
        return getSettingsRow(biz, type, id, operatorId);
    }

    @Override
    public JSONObject setDirection(int biz, int type, String cfgId, int oa2Third, int third2Oa,
                                   String operatorId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        assertCfgRight(id, operatorId);
        int dirOa2Third = normDir(oa2Third);
        int dirThird2Oa = normDir(third2Oa);
        if (dirOa2Third == 0 && dirThird2Oa == 0) {
            throw new RuntimeException("同步方向至少要勾选一个（OA 同步到第三方 / 第三方同步到 OA）");
        }
        int operator = OaonesUtil.i(operatorId, 0);
        String now = OaonesUtil.now();
        MtgSyncCfg cfg = getCfg(biz, type, id);
        Map<String, Object> p = OaonesParam.of();
        if (cfg == null) {
            // 没配过：落一条设置只存方向（保持“未开启”，管理员再单独开同步开关）
            MtgSyncCfg c = new MtgSyncCfg();
            c.setBiz(biz);
            c.setType(type);
            c.setCfgid(id);
            c.setEnabled(0);
            c.setDirOa2Third(dirOa2Third);
            c.setDirThird2Oa(dirThird2Oa);
            c.setRemark("");
            c.setModedatacreater(operator);
            c.setModedatacreatedate(OaonesUtil.nowDate());
            c.setModedatacreatetime(OaonesUtil.nowTime());
            c.setModedatamodifier(operator);
            c.setModedatamodifydatetime(now);
            p.put("e", c);
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertCfg(p));
        } else {
            cfg.setDirOa2Third(dirOa2Third);
            cfg.setDirThird2Oa(dirThird2Oa);
            cfg.setModedatamodifier(operator);
            cfg.setModedatamodifydatetime(now);
            p.put("e", cfg);
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateCfg(p));
        }
        OaonesLog.step("会议/日程同步方向已保存 biz=" + biz + " type=" + type + " cfg=" + id
                + " oa2third=" + dirOa2Third + " third2oa=" + dirThird2Oa);
        return getSettingsRow(biz, type, id, operatorId);
    }

    @Override
    public JSONObject setApp(int biz, int type, String cfgId, int appId, String operatorId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        assertCfgRight(id, operatorId);
        // 应用必须存在、属于本租户、属于本平台（0 = 未指定，回退租户配置的 Secret + AgentId）
        assertAppOfCfg(type, id, appId);
        int operator = OaonesUtil.i(operatorId, 0);
        String now = OaonesUtil.now();
        MtgSyncCfg cfg = getCfg(biz, type, id);
        Map<String, Object> p = OaonesParam.of();
        if (cfg == null) {
            // 还没配过：先落一条“未开启”的设置再写 app_id（口径同“保存同步方向”，不顺手打开同步）
            MtgSyncCfg c = new MtgSyncCfg();
            c.setBiz(biz);
            c.setType(type);
            c.setCfgid(id);
            c.setEnabled(0);
            c.setDirOa2Third(1);
            c.setDirThird2Oa(1);
            c.setAppId(appId);
            c.setRemark("");
            c.setModedatacreater(operator);
            c.setModedatacreatedate(OaonesUtil.nowDate());
            c.setModedatacreatetime(OaonesUtil.nowTime());
            c.setModedatamodifier(operator);
            c.setModedatamodifydatetime(now);
            p.put("e", c);
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertCfg(p));
        } else {
            p.put("id", cfg.getId());
            p.put("appId", appId);
            p.put("modifier", operator);
            p.put("modifyTime", now);
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateApp(p));
        }
        OaonesLog.step("会议/日程同步「绑定应用」已保存 biz=" + biz + " type=" + type + " cfg=" + id
                + " appId=" + appId);
        return getSettingsRow(biz, type, id, operatorId);
    }

    @Override
    public JSONObject checkApp(int biz, int type, String cfgId, int appId, String operatorId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        assertCfgRight(id, operatorId);
        assertAppOfCfg(type, id, appId);
        QywxConfig tenant = tenant(type, id);
        if (tenant == null) {
            throw new RuntimeException("租户不存在或已删除（cfg_id=" + id + "）");
        }
        MtgAppOption app = app(type, appId);
        String corpId = OaonesUtil.s(tenant.getCorpId());
        String secret = secretOf(app, tenant);
        String agentId = agentIdOf(app, tenant);

        JSONObject data = new JSONObject();
        data.put("app_id", String.valueOf(appId));
        data.put("app_name", app == null ? "" : OaonesUtil.s(app.getAppName()));
        data.put("agent_id", agentId);
        data.put("secret_source", app != null && !isBlank(app.getAppSecret()) ? "app" : "cfg");
        if (isBlank(corpId) || isBlank(secret)) {
            data.put("success", false);
            data.put("token_ok", false);
            data.put("schedule_ok", false);
            data.put("message", "缺少企业标识或 Secret：请在「租户设置」补全企业标识，"
                    + "并在「应用绑定」里给该应用配上应用 Secret（或让租户配置的 Secret 兜底）");
            return data;
        }
        // ① 换 access_token：验证企业标识 / Secret 是否正确（错密钥这一步就会失败）
        data.put("corp_id", corpId);
        try {
            String token = accessToken(type, corpId, secret);
            data.put("token_ok", true);
            data.put("token_tail", token.length() <= 6 ? token : token.substring(token.length() - 6));
        } catch (Exception e) {
            data.put("success", false);
            data.put("token_ok", false);
            data.put("schedule_ok", false);
            data.put("message", "换取 access_token 失败（企业标识 / Secret 不对，或应用已停用）："
                    + msgOf(e));
            OaonesLog.error("[mtgSync] 绑定应用校验失败（token）type=" + type + " cfg=" + id
                    + " appId=" + appId, e);
            return data;
        }
        // ② 日程权限：用该租户已绑定的一个平台成员当日历试读一小段日程，
        //    接口回错（多为“该应用没有日程权限”/“成员不在应用可见范围”）就报出来——
        //    不能复用拉取适配器（它对单个日历的报错是“跳过”，会看不出权限问题）
        List<String> calIds = boundPlatformUserIds(id, 1);
        if (calIds.isEmpty()) {
            data.put("success", true);
            data.put("schedule_ok", false);
            data.put("message", "鉴权正常（AgentId " + (agentId.length() == 0 ? "-" : agentId)
                    + "，Secret 来源：" + ("app".equals(data.getString("secret_source"))
                    ? "应用自己的 Secret" : "租户配置的 Secret（该应用没单独配）")
                    + "）；但该租户还没有已绑定平台账号的人员，**无法验证日程权限**——"
                    + "请先在「人员绑定」里绑定几个人，或到「同步记录 / 报文」看实际推送结果。");
            return data;
        }
        try {
            JSONObject probe = probeSchedule(type, corpId, secret, calIds.get(0));
            data.put("schedule_ok", true);
            data.put("success", true);
            data.put("member", calIds.get(0));
            data.put("events", probe.getIntValue("count"));
            data.put("api", probe.getString("api"));
            data.put("message", "鉴权与日程读取权限都正常：用成员 " + calIds.get(0) + " 的日历试读，"
                    + "接口返回 " + probe.getIntValue("count") + " 条日程（没有日程也算正常）");
        } catch (Exception e) {
            data.put("success", false);
            data.put("schedule_ok", false);
            data.put("member", calIds.get(0));
            data.put("message", "日程接口调用失败：该应用多半**没有“日程 / 日历”权限**，"
                    + "或成员不在应用的可见范围内。请在平台后台给这个应用勾上日程权限后重试；"
                    + "接口返回：" + msgOf(e));
            OaonesLog.error("[mtgSync] 绑定应用校验失败（日程权限）type=" + type + " cfg=" + id
                    + " appId=" + appId, e);
        }
        OaonesLog.step("[mtgSync] 绑定应用校验 biz=" + biz + " type=" + type + " cfg=" + id
                + " appId=" + appId + " 结果=" + OaonesUtil.s(data.getString("message")));
        return data;
    }

    @Override
    public JSONObject listUserDirPref(JSONObject params, String operatorId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        if (OaonesUtil.i(cfgId, 0) > 0) {
            assertCfgRight(OaonesUtil.i(cfgId, 0), operatorId);
            p.put("cfgId", OaonesUtil.i(cfgId, 0));
        } else {
            p.put("cfgIds", maintainableCfgIds(type, operatorId));
        }
        String keyword = OaonesUtil.s(params.getString("keyword"));
        if (keyword.length() > 0) {
            p.put("keyword", keyword);
            p.put("keywordLike", "%" + keyword + "%");
        }
        // 组织维度筛选：分部 / 部门都**自动含其下级**（页面口径，与“分部绑定 / 部门绑定”的筛选一致）
        List<Integer> subIds = subtreeIds(splitIds(params.getString("sub_ids")), true);
        if (!subIds.isEmpty()) {
            p.put("subIds", subIds);
        }
        List<Integer> deptIds = subtreeIds(splitIds(params.getString("dept_ids")), false);
        if (!deptIds.isEmpty()) {
            p.put("deptIds", deptIds);
        }
        // 方向筛选：oa2 / t2oa / both / none（空 = 不筛）
        String direction = OaonesUtil.s(params.getString("direction")).trim().toLowerCase();
        if (direction.length() > 0) {
            p.put("direction", direction);
        }
        p.put("limit", String.valueOf(readLimit(params)));
        List<MtgSyncUser> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listUserDirPref(p));
        JSONArray list = new JSONArray();
        if (rows != null) {
            for (MtgSyncUser row : rows) {
                list.add(userPrefJson(row));
            }
        }
        JSONObject data = new JSONObject();
        data.put("rows", list);
        data.put("limit", readLimit(params));
        return data;
    }

    /** 个人开关 / 方向行转 JSON（个人账号中心与「个人同步方向」只读列表共用）。 */
    private JSONObject userPrefJson(MtgSyncUser row) {
        JSONObject json = new JSONObject();
        json.put("id", row.getId());
        json.put("biz", row.getBiz());
        json.put("type", row.getType());
        json.put("cfg_id", row.getCfgid() == null ? "" : String.valueOf(row.getCfgid()));
        json.put("cfg_name", OaonesUtil.s(row.getCfgName()));
        json.put("oa_user_id", row.getOaUserId() == null ? "" : String.valueOf(row.getOaUserId()));
        json.put("oa_user_name", OaonesUtil.s(row.getOaUserName()));
        json.put("oa_user_loginid", OaonesUtil.s(row.getOaUserLoginid()));
        json.put("oa_sub_id", row.getOaSubId() == null ? "" : String.valueOf(row.getOaSubId()));
        json.put("oa_sub_name", OaonesUtil.s(row.getOaSubName()));
        json.put("oa_dept_id", row.getOaDeptId() == null ? "" : String.valueOf(row.getOaDeptId()));
        json.put("oa_dept_name", OaonesUtil.s(row.getOaDeptName()));
        json.put("enabled", row.getEnabled() == null ? 1 : row.getEnabled());
        json.put("dir_mode", row.getDirMode() == null ? OaonesConst.MTG_DIR_MODE_FOLLOW : row.getDirMode());
        json.put("dir_oa2third", row.getDirOa2Third() == null ? 1 : row.getDirOa2Third());
        json.put("dir_third2oa", row.getDirThird2Oa() == null ? 1 : row.getDirThird2Oa());
        json.put("dir_text", OaonesConst.mtgDirText(dirOn(row.getDirOa2Third()), dirOn(row.getDirThird2Oa())));
        json.put("update_time", OaonesUtil.s(row.getModedatamodifydatetime()));
        return json;
    }

    /** 逗号分隔的 ID 串 → 去重正整数列表（页面上“分部 / 部门”是多选弹框，传过来即 {@code id,id}）。 */
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

    /**
     * 把「分部 / 部门」ID 展开成「自身 + 全部下级」。
     *
     * <p>页面口径：<b>选中分部自动含其下级分部、选中部门自动含其下级部门</b>
     * （与「分部绑定 / 部门绑定」两页的分部 / 部门筛选一致）。<b>展开用全量组织</b>——
     * 结果列表本身不按 OA 分权裁剪（只有“选择候选”按分权过滤，见 {@code common.OaonesHrmScope}），
     * 所以这里不能再去套登录人的分权范围，否则下级会漏掉。</p>
     */
    private List<Integer> subtreeIds(List<Integer> roots, boolean sub) {
        List<Integer> out = new ArrayList<Integer>();
        if (roots.isEmpty()) {
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

    @Override
    public JSONObject runNow(int biz, int type, String cfgId, String operatorId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        assertCfgRight(id, operatorId);
        MtgSyncCfg cfg = getCfg(biz, type, id);
        if (cfg == null) {
            // 允许未保存就点“立即同步”：按默认（开启）跑一次
            cfg = new MtgSyncCfg();
            cfg.setId(null);
            cfg.setBiz(biz);
            cfg.setType(type);
            cfg.setCfgid(id);
            cfg.setEnabled(1);
            cfg.setDirOa2Third(1);
            cfg.setDirThird2Oa(1);
        }
        return syncCfg(cfg, operatorId, false);
    }

    @Override
    public JSONObject listLog(JSONObject params, String operatorId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        if (OaonesUtil.i(cfgId, 0) > 0) {
            assertCfgRight(OaonesUtil.i(cfgId, 0), operatorId);
            p.put("cfgId", OaonesUtil.i(cfgId, 0));
        } else {
            List<Integer> cfgIds = maintainableCfgIds(type, operatorId);
            p.put("cfgIds", cfgIds.isEmpty() ? new ArrayList<Integer>() : cfgIds);
        }
        String op = OaonesUtil.s(params.getString("op"));
        if (op.length() > 0 && !"-1".equals(op)) {
            p.put("op", OaonesUtil.i(op, -1));
        }
        String keyword = OaonesUtil.s(params.getString("keyword"));
        if (keyword.length() > 0) {
            p.put("keyword", keyword);
            p.put("keywordLike", "%" + keyword + "%");
        }
        // 时间检索：页面的“时间”是日期时间框（精确到秒），一个框给 yyyy-MM-dd HH:mm:ss，
        // 而库里是“日期 + 时间”两列，这里拆开传参（与消息中心推送日志同一口径，见 OaonesUtil#splitDateTime）
        String[] from = logRange(params, "from", "00:00:00");
        if (from[0] != null) {
            p.put("fromDate", from[0]);
            p.put("fromTime", from[1]);
        }
        String[] to = logRange(params, "to", "23:59:59");
        if (to[0] != null) {
            p.put("toDate", to[0]);
            p.put("toTime", to[1]);
        }
        p.put("limit", String.valueOf(readLimit(params)));
        List<MtgSyncLog> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listLog(p));
        int total = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.countLog(p));
        JSONArray list = new JSONArray();
        if (rows != null) {
            for (MtgSyncLog row : rows) {
                list.add(toJson(row));
            }
        }
        JSONObject data = new JSONObject();
        data.put("rows", list);
        data.put("total", total);
        data.put("limit", readLimit(params));
        data.put("truncated", total > list.size());
        return data;
    }

    @Override
    public JSONObject listItems(JSONObject params, String operatorId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        if (OaonesUtil.i(cfgId, 0) > 0) {
            assertCfgRight(OaonesUtil.i(cfgId, 0), operatorId);
            p.put("cfgId", OaonesUtil.i(cfgId, 0));
        } else {
            p.put("cfgIds", maintainableCfgIds(type, operatorId));
        }
        String op = OaonesUtil.s(params.getString("op"));
        if (op.length() > 0 && !"-1".equals(op)) {
            p.put("op", OaonesUtil.i(op, -1));
        }
        String keyword = OaonesUtil.s(params.getString("keyword"));
        if (keyword.length() > 0) {
            p.put("keyword", keyword);
            p.put("keywordLike", "%" + keyword + "%");
        }
        p.put("limit", String.valueOf(readLimit(params)));
        List<MtgSyncLog> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listItems(p));
        JSONArray list = new JSONArray();
        if (rows != null) {
            for (MtgSyncLog row : rows) {
                list.add(toJson(row));
            }
        }
        JSONObject data = new JSONObject();
        data.put("rows", list);
        data.put("limit", readLimit(params));
        return data;
    }

    @Override
    public JSONObject retry(JSONObject params, String operatorId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        int cfgId = params.getIntValue("cfg_id");
        String oaId = OaonesUtil.s(params.getString("oa_id")).trim();
        if (cfgId <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        if (oaId.length() == 0) {
            throw new RuntimeException("缺少会议 / 日程ID");
        }
        assertCfgRight(cfgId, operatorId);
        QywxConfig tenant = tenant(type, cfgId);
        if (tenant == null) {
            throw new RuntimeException("租户不存在或已删除（cfg_id=" + cfgId + "）");
        }
        MtgOaItem item = getOaItem(biz, oaId);
        if (item == null) {
            throw new RuntimeException("会议 / 日程不存在（ID=" + oaId + "），可能已被删除");
        }
        item.setSubId(pickSubId(item));
        item.setSubName(OaonesUtil.s(subNameMap().get(item.getSubId())));

        String batchId = "mr" + biz + "-" + type + "-" + cfgId + "-" + System.currentTimeMillis();
        String existWxId = lastSuccessWxId(biz, type, cfgId, oaId);
        // 重推同样按“租户方向 + 个人方向”判断：租户没勾「OA 同步到第三方」、或创建人不允许时跳过
        MtgSyncCfg cfg = getCfg(biz, type, cfgId);
        int op = pushOne(adapter(type), biz, type, cfgId, tenant, batchId, item, existWxId, "重推：",
                operatorId, userPrefs(biz, type, cfgId), cfg == null || dirOn(cfg.getDirOa2Third()),
                cfg == null ? null : cfg.getAppId(), findLinkByOa(biz, type, cfgId, oaId));

        JSONObject summary = new JSONObject();
        summary.put("biz", biz);
        summary.put("type", type);
        summary.put("cfg_id", cfgId);
        summary.put("oa_id", oaId);
        summary.put("batch_id", batchId);
        summary.put("op", op);
        summary.put("op_text", OaonesConst.syncOpText(op));
        summary.put("message", op == OaonesConst.SYNC_OP_FAIL
                ? "重推失败，详情见同步记录"
                : (op == OaonesConst.SYNC_OP_SKIP ? "重推仍被跳过，原因见同步记录" : "重推成功"));
        return summary;
    }

    @Override
    public JSONObject payload(JSONObject params, String operatorId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        int cfgId = params.getIntValue("cfg_id");
        String oaId = OaonesUtil.s(params.getString("oa_id")).trim();
        if (cfgId <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        if (oaId.length() == 0) {
            throw new RuntimeException("缺少会议 / 日程ID");
        }
        assertCfgRight(cfgId, operatorId);
        QywxConfig tenant = tenant(type, cfgId);
        if (tenant == null) {
            throw new RuntimeException("租户不存在或已删除（cfg_id=" + cfgId + "）");
        }
        MtgOaItem item = getOaItem(biz, oaId);
        if (item == null) {
            throw new RuntimeException(OaonesConst.mtgBizText(biz) + "不存在（ID=" + oaId + "），可能已被删除");
        }
        item.setSubId(pickSubId(item));
        item.setSubName(OaonesUtil.s(subNameMap().get(item.getSubId())));

        // 与「重推 / 立即同步」同一口径取“此前是否同步过 + 个人开关 / 方向”，
        // 保证预览出来的报文与实际下发的一致（有平台日程ID 走更新接口，否则走新增）
        String existWxId = lastSuccessWxId(biz, type, cfgId, oaId);
        MtgSyncCfg cfg = getCfg(biz, type, cfgId);
        boolean tenantOa2Third = cfg == null || dirOn(cfg.getDirOa2Third());
        Map<String, MtgSyncUser> userPrefs = userPrefs(biz, type, cfgId);
        MtgPushContext ctx = buildContext(biz, type, cfgId, tenant, item, existWxId, userPrefs,
                tenantOa2Third, cfg == null ? null : cfg.getAppId());
        MtgPushRequest req = adapter(type).buildRequest(ctx);

        JSONObject data = new JSONObject();
        data.put("biz", biz);
        data.put("biz_text", OaonesConst.mtgBizText(biz));
        data.put("type", type);
        data.put("cfg_id", String.valueOf(cfgId));
        data.put("cfg_name", OaonesUtil.s(tenant.getCorpName()));
        data.put("corp_id", OaonesUtil.s(tenant.getCorpId()));
        data.put("oa_id", oaId);
        data.put("oa_title", OaonesUtil.s(item.getTitle()));
        data.put("sub_name", OaonesUtil.s(item.getSubName()));
        data.put("begin_time", OaonesUtil.s(item.getBeginTime()));
        data.put("end_time", OaonesUtil.s(item.getEndTime()));
        data.put("api_method", req.getMethod());
        data.put("api_path", req.getPath());
        data.put("api_url", apiBase(type) + req.getPath());
        data.put("update", existWxId != null);
        data.put("exist_wx_id", OaonesUtil.s(existWxId));
        data.put("owner_user_id", OaonesUtil.s(ctx.getOwnerUserId()));
        data.put("attendee_count", ctx.getAttendeeUserIds().size());
        data.put("payload", req.getBody().toJSONString());
        data.put("fields", req.getFields());
        // 本次同步用哪份凭据调平台日程接口（绑定应用 or 租户配置兜底），页面顶部提示会说明
        Integer payloadAppId = cfg == null ? null : cfg.getAppId();
        MtgAppOption payloadApp = syncApp(type, cfgId, payloadAppId);
        data.put("app_id", String.valueOf(payloadAppId == null ? 0 : payloadAppId.intValue()));
        data.put("app_name", payloadApp == null ? "" : OaonesUtil.s(payloadApp.getAppName()));
        data.put("app_agent_id", payloadApp == null ? "" : OaonesUtil.s(payloadApp.getAgentId()));
        JSONArray tips = payloadTips(biz, tenantOa2Third, item, ctx, userPrefs);
        if (payloadApp != null) {
            tips.add("本次同步用「应用绑定」里的应用「" + OaonesUtil.s(payloadApp.getAppName())
                    + "」(AgentId " + OaonesUtil.s(payloadApp.getAgentId()) + ") 的凭据调用平台日程接口"
                    + "（Secret 来源：" + (isBlank(payloadApp.getAppSecret())
                    ? "租户配置的 Secret 兜底" : "应用自己的 Secret") + "）。");
        } else {
            tips.add("该租户还没指定「绑定应用」：用租户配置里的 Secret 调用平台日程接口。"
                    + "平台日程接口通常要求用**有日程权限的应用自己的 Secret**，"
                    + "没指定时可能报权限错误——可到「绑定应用」列选择该租户下已配好日程权限的应用。");
        }
        if (!isBlank(req.getPushError())) {
            // 组装阶段就发现“这条推不出去”（如 OA 里没有开始时间）：把原因摆到最前面——
            // 与同步记录里失败行“说明”是同一句话，报文照常展示（时间字段为 0）
            JSONArray withWarn = new JSONArray();
            withWarn.add("实际同步会失败：" + req.getPushError()
                    + "（同步记录里失败行的“说明”就是这句话）；报文按同样口径组装，时间字段为 0，"
                    + "在 OA 里补齐时间后点“重推”即可。");
            withWarn.addAll(tips);
            tips = withWarn;
        }
        if (OaonesUtil.parseDateTime(item.getBeginTime()) <= 0L) {
            // 时间用不了时把 OA 里的原始值一并带出来，便于一眼判断是“OA 里没填”还是“写法没认出来”
            tips.add("OA 里取到的开始时间：" + rawTimeText(item.getBeginTime())
                    + "　结束时间：" + rawTimeText(item.getEndTime())
                    + "（可接受的写法：2026-10-10 09:00:00 / 2026-10-10 09:00 / 2026-10-10）");
        }
        data.put("push_error", OaonesUtil.s(req.getPushError()));
        data.put("tips", tips);
        OaonesLog.step("[mtgSync] 查看报文 biz=" + biz + " type=" + type + " cfg=" + cfgId
                + " oaId=" + oaId + " api=" + req.getMethod() + " " + req.getPath());
        return data;
    }

    /**
     * 「报文」弹框顶部的提示：说明这条对象**实际同步时会不会下发**、缺少哪些数据。
     * 都是“简要说明”，不重复报文体里的字段说明。
     */
    private JSONArray payloadTips(int biz, boolean tenantOa2Third, MtgOaItem item,
                                  MtgPushContext ctx, Map<String, MtgSyncUser> userPrefs) {
        JSONArray tips = new JSONArray();
        String ownerOaId = OaonesUtil.s(item.getOwnerId()).trim();
        MtgSyncUser ownerPref = ownerOaId.length() == 0 ? null : userPrefs.get(ownerOaId);
        if (!tenantOa2Third) {
            tips.add("该租户未勾选「OA 同步到第三方」方向：实际同步不会推送（这里只是按同样口径组装出来的报文）。");
        }
        if (ownerPref != null && ownerPref.getEnabled() != null && ownerPref.getEnabled().intValue() == 0) {
            tips.add("创建人已设置“不同步到第三方平台”（OA 人员ID=" + ownerOaId + "）：实际同步该条记为“跳过”。");
        } else if (!effectiveOa2Third(tenantOa2Third, ownerPref)) {
            tips.add("创建人的生效方向未包含「OA 同步到第三方」（OA 人员ID=" + ownerOaId + "）：实际同步该条记为“跳过”。");
        }
        if (isBlank(ctx.getOwnerUserId())) {
            tips.add("创建人未绑定平台账号（OA 人员ID=" + (ownerOaId.length() == 0 ? "-" : ownerOaId)
                    + "）：报文中不含组织者字段，实际同步该条记为“跳过”。"
                    + "绑定入口：人员绑定页 / 个人账号中心。");
        } else {
            tips.add("组织者按人员绑定换算：OA 人员ID " + ownerOaId + " → 平台成员 " + ctx.getOwnerUserId() + "。");
        }
        if (ctx.getAttendeeUserIds().isEmpty()) {
            tips.add("本条没有可下发的参与人（OA 参与人未绑定平台账号，或本人选择“不同步”）。");
        }
        if (ctx.isUpdate()) {
            tips.add("该对象此前已同步过（平台日程ID=" + ctx.getExistWxId() + "），报文按平台“更新日程”接口组装。");
        } else {
            tips.add("该对象此前未成功同步过，报文按平台“新增日程”接口组装。");
        }
        tips.add("access_token（企微 / 钉钉）与 Authorization（飞书）在真正调用时实时换取，不在报文里。");
        tips.add("业务：" + OaonesConst.mtgBizText(biz) + "；报文与「重推 / 自动同步」下发的是同一份内容。");
        return tips;
    }

    /** 平台接口域名（报文里只展示“域名 + 路径”，token 实时换取、不落在页面上）。 */
    private String apiBase(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return OaonesProps.ddApiBase();
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return OaonesProps.fsApiBase();
        }
        return OaonesProps.wxApiBase();
    }

    /** 「报文」提示里展示的 OA 原始时间文本：空值写“（空）”，非空加引号。 */
    private String rawTimeText(String value) {
        String v = OaonesUtil.s(value).trim();
        return v.length() == 0 ? "（空）" : "“" + v + "”";
    }

    /**
     * 内置调度器调用：**跑一轮同步**（计划任务到点时触发）——
     * 先推 OA→第三方（新增 / 修改 / 删除），再拉 第三方→OA（新增 / 修改 / 删除）。
     *
     * <p>两个方向都在这里，因为会议 / 日程是<b>双向同步</b>且共用同一套“计划任务”频率：
     * 页面上配的白天 / 夜间频率同时作用于“推”和“拉”，不再各跑各的调度器。</p>
     */
    @Override
    public JSONObject syncRound() {
        String operator = String.valueOf(OaonesConst.SYS_ADMIN_USER_ID);
        JSONObject push = pushAll(operator);
        JSONObject pull = OaonesProps.mtgPullEnabled() ? pullNow() : new JSONObject();

        JSONObject summary = new JSONObject();
        summary.put("push", push);
        summary.put("pull", pull);
        summary.put("created", push.getIntValue("created") + pull.getIntValue("created"));
        summary.put("updated", push.getIntValue("updated") + pull.getIntValue("updated"));
        summary.put("skipped", push.getIntValue("skipped") + pull.getIntValue("skipped"));
        summary.put("failed", push.getIntValue("failed") + pull.getIntValue("failed"));
        summary.put("deleted", push.getIntValue("deleted") + pull.getIntValue("deleted"));
        summary.put("message", "一轮同步：OA→第三方 " + push.getString("message")
                + "；第三方→OA " + (pull.isEmpty() ? "（拉取开关已关闭）" : pull.getString("message")));
        return summary;
    }

    /**
     * 推送阶段：扫描全部启用中的设置（{@code enabled = 1}），逐个租户推一轮 OA→第三方。
     *
     * <p>调度器口径（{@code auto = true}）：没有映射、也没有任何同步记录的新对象才推“新增”；
     * 已经有过同步记录（含跳过 / 失败）的对象不自动重推——要重推由页面「同步清单」手工点。</p>
     */
    private JSONObject pushAll(String operatorId) {
        int created = 0, updated = 0, skipped = 0, failed = 0, deleted = 0, cfgs = 0;
        Map<String, Object> p = OaonesParam.of();
        p.put("limit", String.valueOf(OaonesProps.mtgSyncBatch()));
        List<MtgSyncCfg> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listEnabledCfg(p));
        if (rows != null) {
            for (MtgSyncCfg cfg : rows) {
                if (!dirOn(cfg.getDirOa2Third())) {
                    continue;   // 该租户没勾「OA 同步到第三方」：推送这一档不跑（拉取那档由拉取链路处理）
                }
                cfgs++;
                try {
                    JSONObject one = syncCfg(cfg, operatorId, true);
                    created += one.getIntValue("created");
                    updated += one.getIntValue("updated");
                    skipped += one.getIntValue("skipped");
                    failed += one.getIntValue("failed");
                    deleted += one.getIntValue("deleted");
                } catch (Throwable t) {
                    failed++;
                    OaonesLog.error("[mtgSync] 调度器同步失败 biz=" + cfg.getBiz() + " type=" + cfg.getType()
                            + " cfg=" + cfg.getCfgid(), t);
                }
            }
        }
        JSONObject summary = new JSONObject();
        summary.put("cfgs", cfgs);
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("deleted", deleted);
        summary.put("message", "推送：租户 " + cfgs + "，新增 " + created + " / 更新 " + updated
                + " / 删除 " + deleted + " / 跳过 " + skipped + " / 失败 " + failed);
        return summary;
    }

    // ==================================================================
    // 计划任务（两种同步方式：按时间段明细 / 高级 cron；都支持工作日与非工作日）
    // ==================================================================

    @Override
    public JSONObject syncPlan() {
        return planJson(new Date());
    }

    /**
     * 保存页面「计划任务」。两种方式（{@code mode}）的配置**都存下来**，来回切不丢值：
     * <ul>
     *   <li>{@code mode = 0} 按时间段：{@code items}（JSON 数组，一行一个时间段）必须非空；
     *       同一适用日下的时间段不允许重叠；</li>
     *   <li>{@code mode = 1} 高级（cron）：{@code day_cron} / {@code night_cron} 至少一个非空。</li>
     * </ul>
     *
     * <p>工作日口径由 {@code workday_group_id} 指定用哪个考勤组算（0 = 不指定，按周一至周五），
     * 高级模式再用 {@code cron_day_type} 指定“每天 / 仅工作日 / 仅非工作日”。</p>
     */
    @Override
    public JSONObject saveSyncPlan(JSONObject params, String operatorId) {
        if (!canManagePlan(operatorId)) {
            throw new RuntimeException("计划任务是全局设置：只有系统管理员 / 超管，或该平台任一租户的维护人可以修改");
        }
        JSONObject src = params == null ? new JSONObject() : params;
        int enabled = src.containsKey("enabled") ? OaonesUtil.i(src.getString("enabled"), 1) : 1;
        int mode = checkPlanMode(src.getString("mode"));
        int groupId = Math.max(OaonesUtil.i(src.getString("workday_group_id"), 0), 0);
        // 高级模式的两档 cron 与执行日：始终校验格式并保存（切回时间段模式时值还在）
        String dayCron = checkPlanCron(src.getString("day_cron"), "白天");
        String nightCron = checkPlanCron(src.getString("night_cron"), "夜间");
        int cronDayType = checkDayType(src.getString("cron_day_type"));
        if (mode == OaonesConst.MTG_PLAN_MODE_CRON && dayCron.length() == 0 && nightCron.length() == 0) {
            throw new RuntimeException("高级模式：白天 / 夜间至少填一个 cron 表达式（留空 = 该时段不自动同步）");
        }
        // 时间段明细：同样始终校验并保存（整表重写）
        List<MtgPlanItem> items = checkPlanItems(src.getString("items"), operatorId);
        if (mode == OaonesConst.MTG_PLAN_MODE_ITEM && items.isEmpty()) {
            throw new RuntimeException("按时间段同步：至少要配一个时间段（点“+ 新增时间段”添加）");
        }
        MtgCron row = planRow();
        boolean insert = row == null || row.getId() == null;
        if (insert) {
            row = new MtgCron();
            row.setId(PLAN_ID);
            row.setRemark("");
            row.setModedatacreater(OaonesUtil.i(operatorId, 0));
            row.setModedatacreatedate(OaonesUtil.nowDate());
            row.setModedatacreatetime(OaonesUtil.nowTime());
        }
        row.setPlanMode(Integer.valueOf(mode));
        row.setWorkdayGroupId(Integer.valueOf(groupId));
        row.setCronDayType(Integer.valueOf(cronDayType));
        row.setDayCron(dayCron);
        row.setNightCron(nightCron);
        row.setEnabled(enabled == 0 ? 0 : 1);
        row.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        row.setModedatamodifydatetime(OaonesUtil.now());
        final MtgCron entity = row;
        if (insert) {
            OaonesMyBatis.run(MtgCronMapper.class, m -> m.insert(OaonesProps.mtgCronTable(), entity));
        } else {
            OaonesMyBatis.run(MtgCronMapper.class, m -> m.update(OaonesProps.mtgCronTable(), entity));
        }
        savePlanItems(items);
        // 考勤组 / 节假日可能刚改过：清缓存，让新口径立刻生效
        OaonesWorkday.clearCache();
        OaonesLog.step("[mtgSync] 计划任务已保存：启用=" + row.getEnabled()
                + " 方式=" + (mode == OaonesConst.MTG_PLAN_MODE_CRON ? "高级（cron）" : "按时间段")
                + " 考勤组=" + (groupId <= 0 ? "（不指定，周一至周五）" : String.valueOf(groupId))
                + " 时间段=" + items.size() + " 条"
                + " 白天=" + (dayCron.length() == 0 ? "（不跑）" : dayCron)
                + " 夜间=" + (nightCron.length() == 0 ? "（不跑）" : nightCron));
        return syncPlan();
    }

    @Override
    public JSONObject planPreview(JSONObject params) {
        JSONObject src = params == null ? new JSONObject() : params;
        int count = src.getIntValue("count");
        if (count <= 0 || count > 50) {
            count = 10;
        }
        int mode = checkPlanMode(src.getString("mode"));
        int groupId = Math.max(OaonesUtil.i(src.getString("workday_group_id"), 0), 0);
        JSONArray runs = new JSONArray();
        JSONArray labels = new JSONArray();
        String ruleText;
        Date base = new Date();
        if (mode == OaonesConst.MTG_PLAN_MODE_CRON) {
            String dayCron = checkPlanCron(src.getString("day_cron"), "白天");
            String nightCron = checkPlanCron(src.getString("night_cron"), "夜间");
            int cronDayType = checkDayType(src.getString("cron_day_type"));
            if (dayCron.length() == 0 && nightCron.length() == 0) {
                throw new RuntimeException("高级模式：白天 / 夜间至少填一个 cron 表达式（留空 = 该时段不自动同步）");
            }
            for (int i = 0; i < count; i++) {
                PlanRun run = cronNext(base, dayCron, nightCron, cronDayType, groupId);
                if (run == null) {
                    break;
                }
                runs.add(textOf(run.at));
                labels.add(run.label);
                base = run.at;
            }
            ruleText = ruleTextOfCron(dayCron, nightCron, cronDayType);
        } else {
            List<MtgPlanItem> items = checkPlanItems(src.getString("items"), "0");
            if (items.isEmpty()) {
                throw new RuntimeException("按时间段同步：至少要配一个时间段");
            }
            for (int i = 0; i < count; i++) {
                PlanRun run = itemsNext(items, base, groupId);
                if (run == null) {
                    break;
                }
                runs.add(textOf(run.at));
                labels.add(run.label);
                base = run.at;
            }
            ruleText = ruleTextOfItems(items);
        }
        JSONObject data = new JSONObject();
        data.put("runs", runs);
        data.put("labels", labels);
        data.put("mode", mode);
        data.put("rule_text", ruleText);
        data.put("workday_group_id", groupId);
        data.put("workday_rule", OaonesWorkday.ruleText(groupId));
        data.put("today_workday", OaonesWorkday.isWorkday(new Date(), groupId) ? 1 : 0);
        data.put("day_from", OaonesProps.mtgPullHourFrom());
        data.put("day_to", OaonesProps.mtgPullHourTo());
        return data;
    }

    /**
     * 校验一档 cron 表达式（与同步中心同一套口径）：Quartz 口径（秒 分 时 日 月 周）、
     * 能被解析、且相邻两次执行的间隔不小于 {@code OaonesConst.MTG_PLAN_MIN_MINUTES} 分钟。
     *
     * @param text  页面填的表达式；空白 = 该时段不自动跑（返回空串）
     * @param label 出错时提示用的时段名（白天 / 夜间）
     */
    private String checkPlanCron(String text, String label) {
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
            long gapMinutes = (second - first) / 60000L;
            if (gapMinutes < OaonesConst.MTG_PLAN_MIN_MINUTES) {
                throw new RuntimeException(label + " cron 频率太高（相邻两次只隔 " + gapMinutes + " 分钟）："
                        + "同步频率下限是每 " + OaonesConst.MTG_PLAN_MIN_MINUTES + " 分钟一次");
            }
        }
        return cron;
    }

    /**
     * 计划任务是**全局一份**（不分租户），所以保存入口按“系统管理员 / 超管，或三个平台里任一租户的维护人”放行，
     * 与页面口径一致（页面上的租户列表本来就是按维护人过滤的）。
     */
    private boolean canManagePlan(String operatorId) {
        if (OaonesAdmin.isTotalAdmin(operatorId)) {
            return true;
        }
        int[] types = new int[]{OaonesConst.TYPE_WECHAT, OaonesConst.TYPE_DINGTALK, OaonesConst.TYPE_FEISHU};
        for (int type : types) {
            for (QywxConfig one : tenants(type)) {
                if (one.getId() != null
                        && configService.canMaintain(String.valueOf(one.getId()), operatorId)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String planRuleText() {
        MtgCron row = planRow();
        return ruleTextOf(row, itemsOf());
    }

    /** 计划任务是否到点（调度器每轮调用）：{@code lastRun} 起的第一条计划时间已经到 / 已过就算到点。 */
    public boolean planDue(Date lastRun, Date now) {
        if (lastRun == null) {
            return true;   // 首次启动：立即跑一轮
        }
        Date next = planNextRun(lastRun);
        return next != null && !next.after(now);
    }

    /**
     * 计划任务的**下一次执行时间**（null = 已停用 / 没有可执行时间）。
     *
     * <p>两种同步方式（按时间段 / 高级 cron）都走 {@link #planNextRunDetail}，
     * 与页面上的「预测后 10 次」共用同一份判断，所以页面看到的执行时间就是实际会跑的时间。</p>
     */
    public Date planNextRun(Date after) {
        MtgCron row = planRow();
        if (row != null && row.getEnabled() != null && row.getEnabled().intValue() == 0) {
            return null;
        }
        PlanRun run = planNextRunDetail(after, row, itemsOf());
        return run == null ? null : run.at;
    }

    /** 下一次执行时间 + 命中的那一档（时段明细 / cron 的白天·夜间）说明。 */
    private PlanRun planNextRunDetail(Date after, MtgCron row, List<MtgPlanItem> items) {
        Date base = after == null ? new Date() : after;
        if (isCronMode(row, items)) {
            return cronNext(base, dayCronOf(row), nightCronOf(row), dayTypeOf(row), groupIdOf(row));
        }
        return itemsNext(items, base, groupIdOf(row));
    }

    /**
     * 计划任务用的是哪种同步方式。
     *
     * <p>兼容口径：表里还没有行（全新环境 / 还没保存过）时按原来的“白天 / 夜间两档 cron”跑，
     * 节奏与升级前一致；老库升级上来的行（{@code plan_mode} 还是空）有明细就按时间段、
     * 没有明细就仍按那两档 cron。</p>
     */
    private static boolean isCronMode(MtgCron row, List<MtgPlanItem> items) {
        if (row == null || row.getId() == null) {
            return true;
        }
        if (row.getPlanMode() != null) {
            return row.getPlanMode().intValue() == OaonesConst.MTG_PLAN_MODE_CRON;
        }
        return items == null || items.isEmpty();
    }

    /** 高级模式的「执行日」：0 每天 / 1 仅工作日 / 2 仅非工作日（没有行时 = 每天）。 */
    private static int dayTypeOf(MtgCron row) {
        if (row == null || row.getCronDayType() == null) {
            return OaonesConst.MTG_PLAN_DAY_ALL;
        }
        return checkDayType(String.valueOf(row.getCronDayType()));
    }

    /** 工作日口径用的考勤组ID；0 = 不指定（按周一至周五）。 */
    private static int groupIdOf(MtgCron row) {
        if (row == null || row.getWorkdayGroupId() == null) {
            return 0;
        }
        return Math.max(row.getWorkdayGroupId().intValue(), 0);
    }

    /** 高级（cron）模式：白天 / 夜间两套表达式各自只在自己时段里生效，取更早的那一条。 */
    private PlanRun cronNext(Date base, String dayCron, String nightCron, int dayType, int groupId) {
        Date day = nextInPeriod(dayCron, base, true, dayType, groupId);
        Date night = nextInPeriod(nightCron, base, false, dayType, groupId);
        if (day == null && night == null) {
            return null;
        }
        if (day == null) {
            return new PlanRun(night, "夜间（其余时段）");
        }
        if (night == null) {
            return new PlanRun(day, "白天");
        }
        return day.before(night) ? new PlanRun(day, "白天") : new PlanRun(night, "夜间（其余时段）");
    }

    /** 按时间段模式：取所有明细里更早的那一条。 */
    private PlanRun itemsNext(List<MtgPlanItem> items, Date after, int groupId) {
        if (items == null) {
            return null;
        }
        PlanRun best = null;
        for (MtgPlanItem item : items) {
            Date at = nextItemTime(item, after, groupId);
            if (at == null) {
                continue;
            }
            if (best == null || at.before(best.at)) {
                best = new PlanRun(at, itemLabel(item));
            }
        }
        return best;
    }

    /** 一行明细的说明文案，例：{@code 工作日 08:30–12:00 每 30 分钟一次}。 */
    private static String itemLabel(MtgPlanItem item) {
        return OaonesWorkday.dayTypeText(dayTypeOfItem(item))
                + " " + OaonesUtil.s(item.getStartTime()) + "–" + OaonesUtil.s(item.getEndTime())
                + " " + OaonesProps.humanMinutes(intervalOf(item));
    }

    /** 一行明细的适用日（0 每天 / 1 工作日 / 2 非工作日）；空 = 工作日。 */
    private static int dayTypeOfItem(MtgPlanItem item) {
        if (item == null || item.getDayType() == null) {
            return OaonesConst.MTG_PLAN_DAY_WORK;
        }
        return item.getDayType().intValue();
    }

    /** 一行明细的轮询间隔（分钟，已按频率下限收敛）。 */
    private static int intervalOf(MtgPlanItem item) {
        int minutes = item == null || item.getIntervalMin() == null ? 0 : item.getIntervalMin().intValue();
        return minutes < OaonesConst.MTG_PLAN_MIN_MINUTES ? OaonesConst.MTG_PLAN_MIN_MINUTES : minutes;
    }

    /**
     * 一个时间段在 {@code after} 之后的**第一个执行时间**。
     *
     * <p>轮询**从时段开始时间起算**（8:30 起每 45 分钟 → 8:30 / 9:15 / 10:00 …，含起点）；
     * 结束时间不大于开始时间时按**跨天**处理（21:00–06:30 = 当天 21:00 到次日 06:30），
     * 跨天时段按**开始那一天**的适用日算。找不到（例如“仅工作日”遇上长假）返回 null。</p>
     */
    private Date nextItemTime(MtgPlanItem item, Date after, int groupId) {
        int startMin = minuteOfTime(item == null ? "" : item.getStartTime());
        int endMin = minuteOfTime(item == null ? "" : item.getEndTime());
        if (startMin < 0 || endMin < 0) {
            return null;
        }
        int span = endMin <= startMin ? (endMin + 1440 - startMin) : (endMin - startMin);
        if (span <= 0) {
            return null;
        }
        int step = intervalOf(item);
        Date base = after == null ? new Date() : after;
        Calendar today = Calendar.getInstance();
        today.setTime(base);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        // d 从 -1 起：跨天时段（21:00–06:30）的后半段落在**次日凌晨**，
        // 所以要先看“昨天”那个时段今天还有没有没跑完的时刻，再看今天、明天……
        for (int d = -1; d <= PLAN_ITEM_LOOKAHEAD_DAYS; d++) {
            Calendar day = (Calendar) today.clone();
            day.add(Calendar.DAY_OF_MONTH, d);
            Date dayStart = day.getTime();
            if (!matchesDayType(dayStart, dayTypeOfItem(item), groupId)) {
                continue;
            }
            for (int k = 0; k * step <= span; k++) {
                Date at = new Date(dayStart.getTime() + (startMin + k * step) * 60000L);
                if (at.after(base)) {
                    return at;
                }
            }
        }
        return null;
    }

    /** 某一天是否满足“适用日”口径：每天 / 工作日 / 非工作日（工作日取泛微标准考勤，见 {@link OaonesWorkday}）。 */
    private static boolean matchesDayType(Date day, int dayType, int groupId) {
        if (dayType == OaonesConst.MTG_PLAN_DAY_ALL) {
            return true;
        }
        boolean work = OaonesWorkday.isWorkday(day, groupId);
        return dayType == OaonesConst.MTG_PLAN_DAY_WORK ? work : !work;
    }

    /** {@code HH:mm} / {@code HH:mm:ss} → 当天的第几分钟（0..1439）；解析不了返回 -1。 */
    private static int minuteOfTime(String text) {
        String value = OaonesUtil.s(text).trim();
        if (value.length() == 0) {
            return -1;
        }
        String[] parts = value.split(":");
        if (parts.length < 2) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 分钟 → {@code HH:mm}（明细存库前的归一，秒一律丢弃）。 */
    private static String timeOfMinute(int minute) {
        int value = ((minute % 1440) + 1440) % 1440;
        String hour = String.valueOf(value / 60);
        String min = String.valueOf(value % 60);
        return (hour.length() < 2 ? "0" + hour : hour) + ":" + (min.length() < 2 ? "0" + min : min);
    }

    /** 校验页面提交的同步方式：0 按时间段 / 1 高级（cron）。 */
    private static int checkPlanMode(String text) {
        int mode = OaonesUtil.i(text, OaonesConst.MTG_PLAN_MODE_ITEM);
        if (mode != OaonesConst.MTG_PLAN_MODE_ITEM && mode != OaonesConst.MTG_PLAN_MODE_CRON) {
            throw new RuntimeException("同步方式只能选“按时间段”或“高级（cron 表达式）”");
        }
        return mode;
    }

    /** 校验“适用日 / 执行日”：0 每天 / 1 仅工作日 / 2 仅非工作日。 */
    private static int checkDayType(String text) {
        int dayType = OaonesUtil.i(text, OaonesConst.MTG_PLAN_DAY_ALL);
        if (dayType != OaonesConst.MTG_PLAN_DAY_ALL
                && dayType != OaonesConst.MTG_PLAN_DAY_WORK
                && dayType != OaonesConst.MTG_PLAN_DAY_OFF) {
            throw new RuntimeException("适用日只能选“每天 / 工作日 / 非工作日”");
        }
        return dayType;
    }

    /**
     * 校验页面提交的时间段明细（{@code items} 是 JSON 数组，一行一个时间段），并归一成实体。
     *
     * <p>规则：起止时间要 {@code HH:mm} 且不相同；轮询间隔在
     * [{@link OaonesConst#MTG_PLAN_MIN_MINUTES}, {@link OaonesConst#MTG_PLAN_MAX_MINUTES}] 分钟之间；
     * **同一适用日下的时间段不能重叠**（跨天时段按 [开始, 结束+24h) 参与比较，会同时和“次日的时段”比，
     * 所以 21:00–06:30 与 00:00–06:00 这种跨天撞车也能拦住）；
     * 「每天」与「工作日 / 非工作日」不能同时配（同一天会命中两套时段）。</p>
     */
    private List<MtgPlanItem> checkPlanItems(String json, String operatorId) {
        List<MtgPlanItem> list = new ArrayList<MtgPlanItem>();
        if (OaonesUtil.s(json).trim().length() == 0) {
            return list;   // 没提交明细（例如在高级模式下保存）：当成“没有明细”
        }
        JSONArray array;
        try {
            array = JSONArray.parseArray(json);
        } catch (Throwable t) {
            throw new RuntimeException("时间段明细的格式不对，请刷新页面后重试");
        }
        if (array == null || array.isEmpty()) {
            return list;
        }
        if (array.size() > OaonesConst.MTG_PLAN_ITEM_LIMIT) {
            throw new RuntimeException("一个计划最多配 " + OaonesConst.MTG_PLAN_ITEM_LIMIT + " 个时间段");
        }
        Map<Integer, List<int[]>> windows = new LinkedHashMap<Integer, List<int[]>>();
        boolean hasAll = false;
        boolean hasWork = false;
        boolean hasOff = false;
        for (int i = 0; i < array.size(); i++) {
            JSONObject one = array.getJSONObject(i);
            if (one == null) {
                continue;
            }
            int dayType = checkDayType(one.getString("day_type"));
            int startMin = minuteOfTime(one.getString("start_time"));
            int endMin = minuteOfTime(one.getString("end_time"));
            if (startMin < 0 || endMin < 0) {
                throw new RuntimeException("第 " + (i + 1) + " 行的时间段填得不对：开始 / 结束时间要 HH:mm"
                        + "（可以点输入框右边的时钟图标选，例：08:30）");
            }
            if (startMin == endMin) {
                throw new RuntimeException("第 " + (i + 1) + " 行的开始时间与结束时间不能相同"
                        + "（要设成整天请拆成两个时段，或把结束时间写成次日时刻：结束时间小于开始时间即表示跨天）");
            }
            int interval = OaonesUtil.i(one.getString("interval_min"), 0);
            if (interval < OaonesConst.MTG_PLAN_MIN_MINUTES) {
                throw new RuntimeException("第 " + (i + 1) + " 行的轮询间隔不能小于 "
                        + OaonesConst.MTG_PLAN_MIN_MINUTES + " 分钟");
            }
            if (interval > OaonesConst.MTG_PLAN_MAX_MINUTES) {
                throw new RuntimeException("第 " + (i + 1) + " 行的轮询间隔不能超过 24 小时");
            }
            int from = startMin;
            int to = endMin <= startMin ? endMin + 1440 : endMin;
            checkItemOverlap(windows, dayType, from, to, i + 1);
            MtgPlanItem item = new MtgPlanItem();
            item.setPlanId(Integer.valueOf(PLAN_ID));
            item.setDayType(Integer.valueOf(dayType));
            item.setStartTime(timeOfMinute(startMin));
            item.setEndTime(timeOfMinute(endMin));
            item.setIntervalMin(Integer.valueOf(interval));
            item.setShoworder(Integer.valueOf(list.size()));
            item.setRemark("");
            item.setModedatacreater(OaonesUtil.i(operatorId, 0));
            item.setModedatacreatedate(OaonesUtil.nowDate());
            item.setModedatacreatetime(OaonesUtil.nowTime());
            item.setModedatamodifier(OaonesUtil.i(operatorId, 0));
            item.setModedatamodifydatetime(OaonesUtil.now());
            list.add(item);
            hasAll = hasAll || dayType == OaonesConst.MTG_PLAN_DAY_ALL;
            hasWork = hasWork || dayType == OaonesConst.MTG_PLAN_DAY_WORK;
            hasOff = hasOff || dayType == OaonesConst.MTG_PLAN_DAY_OFF;
        }
        if (hasAll && (hasWork || hasOff)) {
            throw new RuntimeException("「每天」与「工作日 / 非工作日」不能同时配置："
                    + "同一天会命中两套时间段。请统一改成「每天」，或只按「工作日 / 非工作日」分组配置");
        }
        return list;
    }

    /**
     * 同一适用日下的时间段不能重叠（页面上的“时间段不能重复”）。
     *
     * <p>这几套时段是**每天循环**的，所以每个时段除了它自己，还要带上“前一天 / 后一天”的
     * 影子区间一起比，这样跨天时段（21:00–06:30）与次日凌晨的时段（00:00–06:00）撞车也能拦住。
     * （区间都按 [开始, 结束) 展开成分钟，跨天的结束时间 = 小时*60+分钟+1440。）</p>
     */
    private static void checkItemOverlap(Map<Integer, List<int[]>> windows, int dayType,
                                         int from, int to, int rowNo) {
        List<int[]> exist = windows.get(Integer.valueOf(dayType));
        if (exist == null) {
            exist = new ArrayList<int[]>();
        } else {
            for (int[] old : exist) {
                // 当前时段 / 前一天 / 后一天，三种落位都跟已有的区间比一次
                int[][] probe = new int[][]{
                        {from, to}, {from - 1440, to - 1440}, {from + 1440, to + 1440}};
                boolean hit = false;
                for (int i = 0; i < probe.length && !hit; i++) {
                    hit = probe[i][0] < old[1] && old[0] < probe[i][1];
                }
                if (hit) {
                    throw new RuntimeException("时间段重复：第 " + rowNo + " 行（"
                            + OaonesWorkday.dayTypeText(dayType) + " " + timeOfMinute(from)
                            + "–" + timeOfMinute(to) + "）与已配的 "
                            + OaonesWorkday.dayTypeText(dayType) + " " + timeOfMinute(old[0] % 1440)
                            + "–" + timeOfMinute(old[1] % 1440) + " 重叠，同一适用日下的时间段不能重复");
                }
            }
        }
        exist.add(new int[]{from, to});
        // 影子区间：本时段在“前一天 / 后一天”的同一位置（用来发现跨天时段与次日时段的冲突）
        exist.add(new int[]{from - 1440, to - 1440});
        exist.add(new int[]{from + 1440, to + 1440});
        windows.put(Integer.valueOf(dayType), exist);
    }

    /** 时间段明细整表重写（先删本计划的全部行，再按页面顺序逐行插入）。 */
    private void savePlanItems(List<MtgPlanItem> items) {
        final String table = OaonesProps.mtgPlanItemTable();
        OaonesMyBatis.run(MtgPlanItemMapper.class, m -> m.deleteByPlan(table, PLAN_ID));
        for (MtgPlanItem item : items) {
            final MtgPlanItem entity = item;
            OaonesMyBatis.run(MtgPlanItemMapper.class, m -> m.insert(table, entity));
        }
    }

    /** 读计划任务的时间段明细；表还没升级 / 读不到时返回空表并只提示一次。 */
    private List<MtgPlanItem> itemsOf() {
        try {
            return OaonesMyBatis.call(MtgPlanItemMapper.class,
                    m -> m.listByPlan(OaonesProps.mtgPlanItemTable(), PLAN_ID));
        } catch (Throwable t) {
            if (PLAN_ITEM_ERROR_LOGGED.compareAndSet(false, true)) {
                OaonesLog.error("[mtgSync] 读取计划任务的时间段明细失败（请确认已执行 "
                        + "uf_oaones_mtg_plan_item 建表 / 升级脚本并重启 E9）", t);
            }
            return new ArrayList<MtgPlanItem>();
        }
    }

    /** 页面 / 调度器共用的计划对象（含规则文案与下次执行时间）。 */
    private JSONObject planJson(Date now) {
        MtgCron row = planRow();
        boolean configured = row != null && row.getId() != null;
        boolean enabled = !configured || row.getEnabled() == null || row.getEnabled().intValue() == 1;
        List<MtgPlanItem> items = configured ? itemsOf() : new ArrayList<MtgPlanItem>();
        int groupId = groupIdOf(row);
        int cronDayType = dayTypeOf(row);
        String dayCron = dayCronOf(row);
        String nightCron = nightCronOf(row);
        PlanRun next = enabled ? planNextRunDetail(now, row, items) : null;
        JSONObject json = new JSONObject();
        json.put("configured", configured);
        json.put("enabled", enabled ? 1 : 0);
        json.put("mode", isCronMode(row, items) ? OaonesConst.MTG_PLAN_MODE_CRON : OaonesConst.MTG_PLAN_MODE_ITEM);
        json.put("workday_group_id", groupId);
        json.put("workday_groups", planGroups());
        json.put("workday_rule", OaonesWorkday.ruleText(groupId));
        json.put("today_workday", OaonesWorkday.isWorkday(now, groupId) ? 1 : 0);
        json.put("items", itemsJson(items));
        json.put("default_items", itemsJson(defaultItems()));
        json.put("day_cron", dayCron);
        json.put("night_cron", nightCron);
        json.put("cron_day_type", cronDayType);
        // 「恢复默认」用：白天每小时一次、其余时段每 2 小时一次（oaones.properties 的两个默认值）
        json.put("default_day_cron", cronOfMinutes(OaonesProps.mtgPullHourlyMinutes()));
        json.put("default_night_cron", cronOfMinutes(OaonesProps.mtgPullOffhourMinutes()));
        json.put("day_from", OaonesProps.mtgPullHourFrom());
        json.put("day_to", OaonesProps.mtgPullHourTo());
        json.put("rule_text", ruleTextOf(row, items));
        json.put("next_run", next == null ? "" : textOf(next.at));
        json.put("next_label", next == null ? "" : next.label);
        json.put("now_period", inDayPeriod(hourOf(now)) ? "day" : "night");
        json.put("now_cron", inDayPeriod(hourOf(now)) ? dayCron : nightCron);
        json.put("day_next", textOf(nextInPeriod(dayCron, now, true, cronDayType, groupId)));
        json.put("night_next", textOf(nextInPeriod(nightCron, now, false, cronDayType, groupId)));
        return json;
    }

    /** 计划任务「工作日口径」的考勤组候选（泛微标准考勤 {@code kq_group}；页面下拉用）。 */
    private static JSONArray planGroups() {
        JSONArray array = new JSONArray();
        for (KqGroup group : OaonesWorkday.groups()) {
            if (group.getId() == null) {
                continue;
            }
            JSONObject one = new JSONObject();
            one.put("id", group.getId());
            one.put("name", OaonesUtil.s(group.getName()));
            array.add(one);
        }
        return array;
    }

    /** 时间段明细 → JSON（页面回填 / 预测用）。 */
    private static JSONArray itemsJson(List<MtgPlanItem> items) {
        JSONArray array = new JSONArray();
        if (items == null) {
            return array;
        }
        for (MtgPlanItem item : items) {
            JSONObject one = new JSONObject();
            one.put("day_type", dayTypeOfItem(item));
            one.put("start_time", OaonesUtil.s(item.getStartTime()));
            one.put("end_time", OaonesUtil.s(item.getEndTime()));
            one.put("interval_min", intervalOf(item));
            array.add(one);
        }
        return array;
    }

    /**
     * 页面上「恢复默认」的时间段明细：工作日 9:00–18:00 每
     * {@code mtg.pull.hourly.minutes}（默认 60）分钟、非工作日 9:00–18:00 每
     * {@code mtg.pull.offhour.minutes}（默认 120）分钟——与升级前“白天每小时、其余每 2 小时”的口径一致，
     * 只是把“白天 / 其余时段”换成了“工作日 / 非工作日”。
     */
    private static List<MtgPlanItem> defaultItems() {
        List<MtgPlanItem> list = new ArrayList<MtgPlanItem>();
        list.add(defaultItem(OaonesConst.MTG_PLAN_DAY_WORK, "09:00", "18:00",
                OaonesProps.mtgPullHourlyMinutes(), 0));
        list.add(defaultItem(OaonesConst.MTG_PLAN_DAY_OFF, "09:00", "18:00",
                OaonesProps.mtgPullOffhourMinutes(), 1));
        return list;
    }

    private static MtgPlanItem defaultItem(int dayType, String start, String end, int minutes, int order) {
        MtgPlanItem item = new MtgPlanItem();
        item.setPlanId(Integer.valueOf(PLAN_ID));
        item.setDayType(Integer.valueOf(dayType));
        item.setStartTime(start);
        item.setEndTime(end);
        item.setIntervalMin(Integer.valueOf(minutes));
        item.setShoworder(Integer.valueOf(order));
        return item;
    }

    /**
     * 读计划任务那一行（表 {@code uf_oaones_mtg_cron} 只保留一行 id = 1）：
     * 没有行 / 表还没升级时返回 null，调用方按 {@code oaones.properties} 的默认频率处理。
     */
    private MtgCron planRow() {
        try {
            return OaonesMyBatis.call(MtgCronMapper.class, m -> m.findFirst(OaonesProps.mtgCronTable()));
        } catch (Throwable t) {
            // 每 60 秒见一次，表没建好时不要把日志刷满：同一个 JVM 只提示一次
            if (PLAN_ERROR_LOGGED.compareAndSet(false, true)) {
                OaonesLog.error("[mtgSync] 读取计划任务失败（按 oaones.properties 的默认频率执行；"
                        + "请确认已执行 uf_oaones_mtg_cron 建表脚本并重启 E9）", t);
            }
            return null;
        }
    }

    /**
     * 计划任务里高级模式的白天 cron：**表里没有行**时用 {@code mtg.pull.hourly.minutes} 的默认频率折算
     * （全新环境 / 还没执行建表脚本也能按“白天每小时一次”跑）；
     * 有行但这一档留空 = 该时段不自动跑（与建表注释一致）。
     */
    private String dayCronOf(MtgCron row) {
        if (row == null) {
            return cronOfMinutes(OaonesProps.mtgPullHourlyMinutes());
        }
        return OaonesCronExpr.normalize(row.getDayCron());
    }

    /** 计划任务里高级模式的夜间 cron：口径同 {@link #dayCronOf(MtgCron)}（默认取 {@code mtg.pull.offhour.minutes}）。 */
    private String nightCronOf(MtgCron row) {
        if (row == null) {
            return cronOfMinutes(OaonesProps.mtgPullOffhourMinutes());
        }
        return OaonesCronExpr.normalize(row.getNightCron());
    }

    /** 计划任务频率文案：按当前同步方式生成（页面、个人账号中心、调度器状态共用同一句）。 */
    private String ruleTextOf(MtgCron row, List<MtgPlanItem> items) {
        if (isCronMode(row, items)) {
            return ruleTextOfCron(dayCronOf(row), nightCronOf(row), dayTypeOf(row));
        }
        return ruleTextOfItems(items);
    }

    /**
     * 高级（cron）模式的频率文案：白天 / 夜间两档（同一句话同时说明两个方向），
     * 例：{@code 同步频率（OA ⇄ 第三方平台）：仅工作日执行，白天 9:00–18:00 每小时一次，其余时段 每 2 小时一次。}
     */
    private String ruleTextOfCron(String dayCron, String nightCron, int dayType) {
        int from = OaonesProps.mtgPullHourFrom();
        int to = OaonesProps.mtgPullHourTo();
        String prefix = "";
        if (dayType == OaonesConst.MTG_PLAN_DAY_WORK) {
            prefix = "仅工作日执行，";
        } else if (dayType == OaonesConst.MTG_PLAN_DAY_OFF) {
            prefix = "仅非工作日执行，";
        }
        return "同步频率（OA ⇄ 第三方平台）：高级模式（cron）" + prefix
                + "白天 " + from + ":00–" + to + ":00 " + describeCron(dayCron)
                + "，其余时段 " + describeCron(nightCron) + "。";
    }

    /**
     * 按时间段模式的频率文案：按适用日分组列出每个时间段与轮询间隔，
     * 例：{@code 同步频率（OA ⇄ 第三方平台）：工作日 08:30–12:00 每 30 分钟一次、13:00–18:00 每小时一次；非工作日 09:00–18:00 每 2 小时一次。}
     */
    private String ruleTextOfItems(List<MtgPlanItem> items) {
        if (items == null || items.isEmpty()) {
            return "同步频率（OA ⇄ 第三方平台）：还没配时间段，不会自动同步。";
        }
        int[] order = new int[]{OaonesConst.MTG_PLAN_DAY_WORK, OaonesConst.MTG_PLAN_DAY_OFF,
                OaonesConst.MTG_PLAN_DAY_ALL};
        StringBuilder text = new StringBuilder("同步频率（OA ⇄ 第三方平台）：");
        boolean first = true;
        for (int dayType : order) {
            StringBuilder part = new StringBuilder();
            for (MtgPlanItem item : items) {
                if (dayTypeOfItem(item) != dayType) {
                    continue;
                }
                if (part.length() > 0) {
                    part.append("、");
                }
                part.append(OaonesUtil.s(item.getStartTime())).append("–")
                        .append(OaonesUtil.s(item.getEndTime())).append(" ")
                        .append(OaonesProps.humanMinutes(intervalOf(item)));
            }
            if (part.length() == 0) {
                continue;
            }
            if (!first) {
                text.append("；");
            }
            text.append(OaonesWorkday.dayTypeText(dayType)).append(" ").append(part);
            first = false;
        }
        return text.append("。").toString();
    }

    /** cron 表达式的“人话”描述（留空 = 不自动执行；解析不出间隔时说“按表达式执行”）。 */
    private String describeCron(String cron) {
        String text = OaonesCronExpr.normalize(cron);
        if (text.length() == 0) {
            return "不自动同步";
        }
        int gap = gapMinutes(text);
        if (gap <= 0) {
            return "按表达式执行";
        }
        if (gap % 1440 == 0) {
            int days = gap / 1440;
            return days == 1 ? "每天一次" : ("每 " + days + " 天一次");
        }
        return OaonesProps.humanMinutes(gap);
    }

    /** cron 相邻两次执行的间隔（分钟）：取最近两次算差值，算不出来返回 0。 */
    private int gapMinutes(String cron) {
        List<String> times = OaonesCronExpr.nextTimes(cron, new Date(), 2);
        if (times.size() < 2) {
            return 0;
        }
        long gap = (OaonesUtil.parseDateTime(times.get(1)) - OaonesUtil.parseDateTime(times.get(0))) / 60000L;
        return gap > 0 ? (int) gap : 0;
    }

    /**
     * 默认频率（分钟）→ Quartz cron：只用于「计划任务还没配过」时的默认两档与页面上的「恢复默认」。
     * 分钟粒度用 {@code 0 0/N * * * ?}，整小时用 {@code 0 0 0/H * * ?}。
     */
    private static String cronOfMinutes(int minutes) {
        if (minutes <= 0) {
            return "";
        }
        if (minutes < 60) {
            return "0 0/" + minutes + " * * * ?";
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours >= 24 ? "0 0 0 * * ?" : ("0 0 0/" + hours + " * * ?");
        }
        return "";
    }

    /** 某个小时算不算“白天”（默认 9:00–18:00，含 9 点、不含 18 点）。 */
    private static boolean inDayPeriod(int hour) {
        int from = OaonesProps.mtgPullHourFrom();
        int to = OaonesProps.mtgPullHourTo();
        return from <= to ? (hour >= from && hour < to) : (hour >= from || hour < to);
    }

    private static int hourOf(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at == null ? new Date() : at);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 高级模式某一套 cron（白天 / 夜间）在 {@code from} 之后的**第一条同时满足“落在自己时段里”
     * 与“适用日命中”的执行时间**。
     *
     * <p>例：白天“每小时一次”，当前 17:30 → 18:00 / 19:00 … 都不算白天，一直找到次日 9:00；
     * 再叠上“仅工作日”时，周末与节假日要一直往后找。迭代上限见
     * {@link #PLAN_ITER_LIMIT} / {@link #PLAN_ITER_LIMIT_DAYTYPE}，找不到就返回 null，
     * 表示这一档在当前配置下不会自动执行（页面的“下次执行时间”会如实显示）。</p>
     */
    private Date nextInPeriod(String cron, Date from, boolean day, int dayType, int groupId) {
        String text = OaonesCronExpr.normalize(cron);
        if (text.length() == 0 || !OaonesCronExpr.valid(text)) {
            return null;
        }
        int limit = dayType == OaonesConst.MTG_PLAN_DAY_ALL ? PLAN_ITER_LIMIT : PLAN_ITER_LIMIT_DAYTYPE;
        Date t = from == null ? new Date() : from;
        for (int i = 0; i < limit; i++) {
            Date next = nextAfterDate(text, t);
            if (next == null) {
                return null;
            }
            if (day == inDayPeriod(hourOf(next)) && matchesDayType(next, dayType, groupId)) {
                return next;
            }
            t = next;
        }
        return null;
    }

    /** 下一次执行时间 + 命中的那一档说明（内部结构，不对外暴露）。 */
    private static final class PlanRun {
        private final Date at;
        private final String label;

        private PlanRun(Date at, String label) {
            this.at = at;
            this.label = label;
        }
    }

    private Date nextAfterDate(String cron, Date after) {
        try {
            return new org.quartz.CronExpression(cron).getNextValidTimeAfter(after);
        } catch (Throwable t) {
            return null;
        }
    }

    private String textOf(Date at) {
        return at == null ? "" : new SimpleDateFormat(PLAN_TIME_PATTERN).format(at);
    }

    // ==================================================================
    // 个人同步开关（个人账号中心：本人自助，不做租户维护人分权）
    // ==================================================================

    @Override
    public JSONObject userStates(int type, String oaUserId) {
        int uid = OaonesUtil.i(oaUserId, 0);
        Map<Integer, MtgSyncCfg> mtgTenant = cfgMap(OaonesConst.MTG_BIZ_MEETING, type);
        Map<Integer, MtgSyncCfg> schTenant = cfgMap(OaonesConst.MTG_BIZ_SCHEDULE, type);
        Map<String, MtgSyncUser> personal = userPrefMap(type, uid);
        JSONObject states = new JSONObject();
        for (QywxConfig tenant : tenants(type)) {
            if (tenant.getId() == null) {
                continue;
            }
            int id = tenant.getId();
            JSONObject st = new JSONObject();
            // 租户开关：只有“已开启”才算允许（没配过 / 已关闭 = 0），与同步引擎同一口径
            st.put("mtg_tenant", tenantGate(mtgTenant, id));
            st.put("sch_tenant", tenantGate(schTenant, id));
            // 个人开关：默认允许（没落过行 = 1），只有显式点过“不同步”才是 0
            MtgSyncUser mtgPref = personal.get(OaonesConst.MTG_BIZ_MEETING + "|" + id);
            MtgSyncUser schPref = personal.get(OaonesConst.MTG_BIZ_SCHEDULE + "|" + id);
            st.put("mtg_personal", personalEnabled(mtgPref));
            st.put("sch_personal", personalEnabled(schPref));
            // 同步方向：租户方向（系统设置）+ 个人方向（遵循 / 指定）+ 生效方向
            fillDirState(st, "mtg", mtgTenant.get(id), mtgPref);
            fillDirState(st, "sch", schTenant.get(id), schPref);
            states.put(String.valueOf(id), st);
        }
        JSONObject data = new JSONObject();
        data.put("type", type);
        data.put("states", states);
        // 同步频率规则（页面上配的「计划任务」：白天 / 夜间两档，两个方向同一频率）：
        // 个人账号中心把这句话直接展示给本人看
        data.put("pull_rule_text", planRuleText());
        return data;
    }

    /**
     * 把某个业务（前缀 mtg = 会议 / sch = 日程）的同步方向写进个人账号中心的状态对象：
     * 租户方向、个人方向口径（遵循系统 / 指定）与两者取交集得到的<b>生效方向</b>。
     */
    private void fillDirState(JSONObject st, String prefix, MtgSyncCfg tenantCfg, MtgSyncUser pref) {
        boolean tenantOa2 = tenantCfg == null || dirOn(tenantCfg.getDirOa2Third());
        boolean tenantT2o = tenantCfg == null || dirOn(tenantCfg.getDirThird2Oa());
        int mode = pref == null || pref.getDirMode() == null
                ? OaonesConst.MTG_DIR_MODE_FOLLOW : pref.getDirMode();
        boolean personalOa2 = pref == null || dirOn(pref.getDirOa2Third());
        boolean personalT2o = pref == null || dirOn(pref.getDirThird2Oa());
        boolean custom = mode == OaonesConst.MTG_DIR_MODE_CUSTOM;
        boolean effOa2 = custom ? (tenantOa2 && personalOa2) : tenantOa2;
        boolean effT2o = custom ? (tenantT2o && personalT2o) : tenantT2o;
        st.put(prefix + "_tenant_dir_oa2third", tenantOa2 ? "1" : "0");
        st.put(prefix + "_tenant_dir_third2oa", tenantT2o ? "1" : "0");
        st.put(prefix + "_tenant_dir_text", OaonesConst.mtgDirText(tenantOa2, tenantT2o));
        st.put(prefix + "_dir_mode", String.valueOf(mode));
        st.put(prefix + "_dir_oa2third", personalOa2 ? "1" : "0");
        st.put(prefix + "_dir_third2oa", personalT2o ? "1" : "0");
        st.put(prefix + "_eff_dir_text", OaonesConst.mtgDirText(effOa2, effT2o));
    }

    @Override
    public JSONObject userSetState(JSONObject params, String oaUserId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        int cfgId = params.getIntValue("cfg_id");
        int enabled = params.containsKey("enabled") ? params.getIntValue("enabled") : 1;
        int uid = assertSelfTenant(biz, type, cfgId, oaUserId, "个人同步开关");
        String label = OaonesConst.mtgBizText(biz);
        upsertUserPref(biz, type, cfgId, uid, enabled, null, null, null);
        JSONObject st = new JSONObject();
        st.put("cfg_id", String.valueOf(cfgId));
        st.put("biz", biz);
        st.put("type", type);
        st.put("enabled", enabled);
        st.put("tenant_enabled", 1);
        st.put("personal_enabled", enabled);
        st.put("message", enabled == 1
                ? ("已恢复本人的" + label + "同步") : ("已关闭本人的" + label + "同步"));
        OaonesLog.step("个人账号中心设置" + label + "同步开关 oaUserId=" + uid + " cfg=" + cfgId
                + " enabled=" + enabled);
        return st;
    }

    @Override
    public JSONObject userSetDirection(JSONObject params, String oaUserId) {
        int biz = params.getIntValue("biz");
        int type = params.getIntValue("type");
        int cfgId = params.getIntValue("cfg_id");
        int uid = assertSelfTenant(biz, type, cfgId, oaUserId, "个人同步方向");
        String label = OaonesConst.mtgBizText(biz);
        int dirMode = params.getIntValue("dir_mode") == OaonesConst.MTG_DIR_MODE_CUSTOM
                ? OaonesConst.MTG_DIR_MODE_CUSTOM : OaonesConst.MTG_DIR_MODE_FOLLOW;
        int oa2Third = normDir(params.containsKey("dir_oa2third")
                ? params.getIntValue("dir_oa2third") : 1);
        int third2Oa = normDir(params.containsKey("dir_third2oa")
                ? params.getIntValue("dir_third2oa") : 1);
        if (dirMode == OaonesConst.MTG_DIR_MODE_CUSTOM && oa2Third == 0 && third2Oa == 0) {
            throw new RuntimeException("指定同步方向时至少要勾选一个方向（OA 同步到第三方 / 第三方同步到 OA）");
        }
        MtgSyncUser saved = upsertUserPref(biz, type, cfgId, uid, null, dirMode, oa2Third, third2Oa);
        JSONObject st = new JSONObject();
        st.put("cfg_id", String.valueOf(cfgId));
        st.put("biz", biz);
        st.put("type", type);
        st.put("dir_mode", dirMode);
        st.put("dir_oa2third", oa2Third);
        st.put("dir_third2oa", third2Oa);
        st.put("dir_text", dirMode == OaonesConst.MTG_DIR_MODE_FOLLOW
                ? "遵循系统设置" : OaonesConst.mtgDirText(oa2Third == 1, third2Oa == 1));
        st.put("message", dirMode == OaonesConst.MTG_DIR_MODE_FOLLOW
                ? ("本人的" + label + "同步方向已改为「遵循系统设置」")
                : ("本人的" + label + "同步方向已保存：" + OaonesConst.mtgDirText(oa2Third == 1, third2Oa == 1)));
        OaonesLog.step("个人账号中心设置" + label + "同步方向 oaUserId=" + uid + " cfg=" + cfgId
                + " mode=" + dirMode + " oa2third=" + oa2Third + " third2oa=" + third2Oa
                + " id=" + (saved == null ? "" : saved.getId()));
        return st;
    }

    /**
     * 本人设置个人开关 / 方向的公共校验：登录人有效、租户存在且属于当前平台、该租户该业务已开启同步。
     * 返回登录人 OA 人员ID。
     */
    private int assertSelfTenant(int biz, int type, int cfgId, String oaUserId, String what) {
        int uid = OaonesUtil.i(oaUserId, 0);
        if (uid <= 0) {
            throw new RuntimeException("登录会话无效，无法设置" + what);
        }
        if (cfgId <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        QywxConfig tenant = tenant(type, cfgId);
        if (tenant == null || tenant.getType() == null || tenant.getType().intValue() != type) {
            throw new RuntimeException("租户不存在或与当前平台不符（cfg_id=" + cfgId + "）");
        }
        Integer tenantEnabled = cfgEnabled(biz, type, cfgId);
        if (tenantEnabled == null || tenantEnabled.intValue() != 1) {
            throw new RuntimeException("该租户未开启" + OaonesConst.mtgBizText(biz) + "同步，暂不能设置" + what);
        }
        return uid;
    }

    /**
     * 落库个人开关 / 方向（一行 = 业务 + 平台 + 租户 + 本人）：
     * 传 null 的字段保持原值（新建时取默认：enabled 1 / dir_mode 0 遵循系统 / 方向 1、1）。
     */
    private MtgSyncUser upsertUserPref(int biz, int type, int cfgId, int uid,
                                       Integer enabled, Integer dirMode, Integer oa2, Integer t2o) {
        String now = OaonesUtil.now();
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        p.put("oaUserId", uid);
        MtgSyncUser exist = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.getUserPref(p));
        if (exist == null) {
            MtgSyncUser e = new MtgSyncUser();
            e.setBiz(biz);
            e.setType(type);
            e.setCfgid(cfgId);
            e.setOaUserId(uid);
            e.setEnabled(enabled == null ? 1 : enabled);
            e.setDirMode(dirMode == null ? OaonesConst.MTG_DIR_MODE_FOLLOW : dirMode);
            e.setDirOa2Third(oa2 == null ? 1 : oa2);
            e.setDirThird2Oa(t2o == null ? 1 : t2o);
            e.setRemark("");
            e.setModedatacreater(uid);
            e.setModedatacreatedate(OaonesUtil.nowDate());
            e.setModedatacreatetime(OaonesUtil.nowTime());
            e.setModedatamodifier(uid);
            e.setModedatamodifydatetime(now);
            Map<String, Object> ins = OaonesParam.of("e", e);
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertUserPref(ins));
            return e;
        }
        if (enabled != null) { exist.setEnabled(enabled); }
        if (dirMode != null) { exist.setDirMode(dirMode); }
        if (oa2 != null) { exist.setDirOa2Third(oa2); }
        if (t2o != null) { exist.setDirThird2Oa(t2o); }
        if (exist.getDirMode() == null) { exist.setDirMode(OaonesConst.MTG_DIR_MODE_FOLLOW); }
        if (exist.getDirOa2Third() == null) { exist.setDirOa2Third(1); }
        if (exist.getDirThird2Oa() == null) { exist.setDirThird2Oa(1); }
        exist.setModedatamodifier(uid);
        exist.setModedatamodifydatetime(now);
        Map<String, Object> upd = OaonesParam.of("e", exist);
        OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateUserPref(upd));
        return exist;
    }

    /** 某租户某业务的租户级开关（null = 没配过）。 */
    private Integer cfgEnabled(int biz, int type, int cfgId) {
        MtgSyncCfg cfg = getCfg(biz, type, cfgId);
        return cfg == null ? null : cfg.getEnabled();
    }

    /** 该平台某业务的全部设置行（cfgid -> 设置，只含已配过的行）。 */
    private Map<Integer, MtgSyncCfg> cfgMap(int biz, int type) {
        Map<Integer, MtgSyncCfg> map = new HashMap<Integer, MtgSyncCfg>();
        for (MtgSyncCfg row : queryCfgs(biz, type, null)) {
            if (row.getCfgid() != null) {
                map.put(row.getCfgid(), row);
            }
        }
        return map;
    }

    /** 租户级同步状态文案（"1" = 该租户该业务已开启同步）。 */
    private String tenantGate(Map<Integer, MtgSyncCfg> map, int cfgId) {
        MtgSyncCfg c = map.get(cfgId);
        return c != null && c.getEnabled() != null && c.getEnabled().intValue() == 1 ? "1" : "0";
    }

    /** 登录人在该平台的个人开关 / 方向行（"biz|cfgid" -> 行，只含显式落过的行）。 */
    private Map<String, MtgSyncUser> userPrefMap(int type, int oaUserId) {
        Map<String, MtgSyncUser> map = new HashMap<String, MtgSyncUser>();
        if (oaUserId <= 0) {
            return map;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("type", type);
        p.put("oaUserId", oaUserId);
        List<MtgSyncUser> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listUserPref(p));
        if (rows != null) {
            for (MtgSyncUser row : rows) {
                if (row.getBiz() != null && row.getCfgid() != null) {
                    map.put(row.getBiz() + "|" + row.getCfgid(), row);
                }
            }
        }
        return map;
    }

    /** 个人开关状态文案（默认允许；只有显式 enabled = 0 才是“不同步”）。 */
    private String personalEnabled(MtgSyncUser pref) {
        return pref != null && pref.getEnabled() != null && pref.getEnabled().intValue() == 0 ? "0" : "1";
    }

    /** 归一同步方向勾选：非 0 一律按 1（选中）。 */
    private int normDir(int value) {
        return value == 0 ? 0 : 1;
    }

    /** 方向勾选是否选中（null / 非 0 = 选中；默认双选）。 */
    private boolean dirOn(Integer value) {
        return value == null || value.intValue() != 0;
    }

    /**
     * 该人员在「OA 同步到第三方」上的**生效方向**：租户方向是上限，
     * 个人「指定」时取交集（租户选中 且 个人选中）；个人「遵循系统设置」或没落过行时直接取租户方向。
     */
    private boolean effectiveOa2Third(boolean tenantOa2Third, MtgSyncUser pref) {
        if (!tenantOa2Third) {
            return false;
        }
        if (pref != null && pref.getDirMode() != null
                && pref.getDirMode().intValue() == OaonesConst.MTG_DIR_MODE_CUSTOM) {
            return dirOn(pref.getDirOa2Third());
        }
        return true;
    }

    /**
     * 该「业务 + 平台 + 租户」下**显式落过行**的个人开关 / 方向（oa_user_id -> 行）：
     * 同步引擎据此判断创建人是否“不同步 / 未选 OA→第三方”，以及参与人是否要剔除。
     */
    private Map<String, MtgSyncUser> userPrefs(int biz, int type, int cfgId) {
        Map<String, MtgSyncUser> map = new HashMap<String, MtgSyncUser>();
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        List<MtgSyncUser> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listUserPref(p));
        if (rows != null) {
            for (MtgSyncUser row : rows) {
                if (row.getOaUserId() != null) {
                    map.put(String.valueOf(row.getOaUserId()), row);
                }
            }
        }
        return map;
    }

    // ==================================================================
    // 同步引擎
    // ==================================================================

    /**
     * 推一条 OA 会议 / 日程 到平台，并按结果写一条同步记录（第一次同步与每次“重推”都走这里）。
     *
     * @param existWxId 该对象此前成功同步返回的平台侧ID（null = 第一次推，走“新增”；非 null = “更新”）
     * @param prefix    记录说明前缀（“重推”用 {@code "重推："}，普通同步传 {@code ""}）
     * @param userPrefs 该「业务 + 平台 + 租户」下显式落过行的个人开关 / 方向（oa_user_id -> 行）
     * @param tenantOa2Third 租户级是否勾选了「OA 同步到第三方」（租户方向是个人方向的上限）
     * @param appId 该租户该业务选定的「绑定应用」ID（0 = 未指定，回退租户配置的 Secret + AgentId）
     * @param link  该对象在映射表里的行（可为 null = 第一次推）；推送成功后由本方法建立 / 刷新映射，
     *              并把当前 OA 内容记成快照（{@code oa_hash}），供下一轮“变了才推”比对
     * @return 本次动作：0 新增 / 1 更新 / 2 跳过 / 3 失败（{@code OaonesConst.SYNC_OP_*}）
     */
    private int pushOne(MtgPushAdapter adapter, int biz, int type, int cfgId, QywxConfig tenant,
                        String batchId, MtgOaItem item, String existWxId, String prefix, String operatorId,
                        Map<String, MtgSyncUser> userPrefs, boolean tenantOa2Third, Integer appId,
                        MtgSyncLink link) {
        // 个人同步开关 / 方向：创建人“不同步”整条跳过；创建人未选择「OA 同步到第三方」也跳过
        // （“只有租户允许且个人允许才同步”里的个人那一半；租户那一半由 enabled=1 + 租户方向保证）
        String ownerOaId = OaonesUtil.s(item.getOwnerId()).trim();
        MtgSyncUser ownerPref = ownerOaId.length() == 0 ? null : userPrefs.get(ownerOaId);
        if (ownerPref != null && ownerPref.getEnabled() != null && ownerPref.getEnabled().intValue() == 0) {
            writeLog(biz, type, cfgId, batchId, item, null, OaonesConst.SYNC_OP_SKIP,
                    prefix + "创建人已设置“不同步到第三方平台”（OA 人员ID=" + ownerOaId + "），本次跳过",
                    operatorId);
            return OaonesConst.SYNC_OP_SKIP;
        }
        if (!effectiveOa2Third(tenantOa2Third, ownerPref)) {
            writeLog(biz, type, cfgId, batchId, item, null, OaonesConst.SYNC_OP_SKIP,
                    prefix + "生效方向未包含「OA 同步到第三方」（租户方向或创建人 OA 人员ID="
                            + ownerOaId + " 的个人方向未选），本次跳过",
                    operatorId);
            return OaonesConst.SYNC_OP_SKIP;
        }
        MtgPushContext ctx = buildContext(biz, type, cfgId, tenant, item, existWxId, userPrefs,
                tenantOa2Third, appId);
        if (isBlank(ctx.getOwnerUserId())) {
            writeLog(biz, type, cfgId, batchId, item, null, OaonesConst.SYNC_OP_SKIP,
                    prefix + "创建人未绑定平台账号（OA 人员ID=" + OaonesUtil.s(item.getOwnerId()) + "），本次跳过",
                    operatorId);
            return OaonesConst.SYNC_OP_SKIP;
        }
        boolean update = ctx.isUpdate();
        try {
            String wxId = adapter.push(ctx);
            // 推送成功后建立 / 刷新映射：source = 0（OA 是创建方）、wx_id 换成平台返回的ID、
            // oa_hash 记当前 OA 内容（下一轮据此判断“OA 改了才推”）
            savePushLink(biz, type, cfgId, item, ctx, link, wxId, operatorId);
            int op = update ? OaonesConst.SYNC_OP_UPDATE : OaonesConst.SYNC_OP_CREATE;
            writeLog(biz, type, cfgId, batchId, item, wxId, op,
                    prefix + (update ? "已更新平台日程" : "已推送到平台日程"), operatorId);
            return op;
        } catch (Exception e) {
            String msg = OaonesUtil.s(e.getMessage());
            writeLog(biz, type, cfgId, batchId, item, null, OaonesConst.SYNC_OP_FAIL,
                    prefix + (msg.length() == 0 ? "推送失败" : msg), operatorId);
            OaonesLog.error("[mtgSync] 推送失败 biz=" + biz + " type=" + type + " cfg=" + cfgId
                    + " oaId=" + item.getOaId(), e);
            return OaonesConst.SYNC_OP_FAIL;
        }
    }

    /**
     * 组装推送到平台时给适配器的上下文（租户凭据 + OA 对象 + 换算好的平台成员ID）。
     *
     * <p>「推送」（{@code pushOne}）与页面「报文」预览（{@link #payload}）共用本方法，
     * 保证两处对“组织者 / 参与人 / 新增还是更新”的判断完全一致。</p>
     *
     * <p>与推送不同的一点：预览时创建人可能未绑定平台账号（推送在那之前就已经记为“跳过”），
     * 此时 {@code ownerUserId} 为空，报文里不会出现组织者字段，页面会给出提示。</p>
     */
    private MtgPushContext buildContext(int biz, int type, int cfgId, QywxConfig tenant, MtgOaItem item,
                                        String existWxId, Map<String, MtgSyncUser> userPrefs,
                                        boolean tenantOa2Third, Integer appId) {
        String owner = firstPlatformUserId(cfgId, item.getOwnerId());
        MtgPushContext ctx = new MtgPushContext();
        ctx.setType(type);
        ctx.setCfgId(cfgId);
        ctx.setCorpId(OaonesUtil.s(tenant.getCorpId()));
        // 凭据：优先用该租户选定的「绑定应用」（平台日程接口要用有日程权限的应用的 Secret），
        // 未指定 / 应用已删除时回退租户配置的 Secret + AgentId（与加“绑定应用”之前一致）
        MtgAppOption app = syncApp(type, cfgId, appId);
        ctx.setCorpSecret(secretOf(app, tenant));
        ctx.setAgentId(agentIdOf(app, tenant));
        ctx.setItem(item);
        ctx.setOwnerUserId(owner);
        ctx.setAttendeeUserIds(resolveAttendees(biz, cfgId, item, owner, userPrefs, tenantOa2Third));
        ctx.setUpdate(existWxId != null);
        ctx.setExistWxId(existWxId);
        return ctx;
    }

    /** 按 OA 会议 / 日程ID 取单条（“重推”用；不带时间窗，历史对象也能重推）。 */
    private MtgOaItem getOaItem(int biz, String oaId) {
        Map<String, Object> p = OaonesParam.of("oaId", oaId);
        return OaonesMyBatis.call(MtgSyncMapper.class,
                m -> biz == OaonesConst.MTG_BIZ_SCHEDULE ? m.getSchedule(p) : m.getMeeting(p));
    }

    // ==================================================================
    // 对象映射（uf_oaones_mtg_link）：变更快照 + 删除对账
    // ==================================================================

    /** 按 OA 对象ID 找映射（没有返回 null）。 */
    private MtgSyncLink findLinkByOa(int biz, int type, int cfgId, String oaId) {
        if (isBlank(oaId)) {
            return null;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        p.put("oaId", oaId.trim());
        return OaonesMyBatis.call(MtgSyncMapper.class, m -> m.findLinkByOa(p));
    }

    /** 按平台对象ID 找映射（没有返回 null）。 */
    private MtgSyncLink findLinkByWx(int biz, int type, int cfgId, String wxId) {
        if (isBlank(wxId)) {
            return null;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        p.put("wxId", wxId.trim());
        return OaonesMyBatis.call(MtgSyncMapper.class, m -> m.findLinkByWx(p));
    }

    /** 该「业务 + 平台 + 租户」下的映射列表（可选来源；一次最多 {@link #LINK_SCAN_LIMIT} 条）。 */
    private List<MtgSyncLink> listLinks(int biz, int type, int cfgId, Integer source) {
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        p.put("source", source);
        p.put("limit", String.valueOf(LINK_SCAN_LIMIT));
        List<MtgSyncLink> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listLink(p));
        return rows == null ? new ArrayList<MtgSyncLink>() : rows;
    }

    /** 某个 OA 对象在该租户该平台下的最近一条同步记录（没有返回 null）。 */
    private MtgSyncLog lastLog(int biz, int type, int cfgId, String oaId) {
        if (isBlank(oaId)) {
            return null;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        p.put("oaId", oaId.trim());
        return OaonesMyBatis.call(MtgSyncMapper.class, m -> m.lastLog(p));
    }

    /**
     * 内容指纹（MD5 十六进制）：把“对这条会议 / 日程有意义的内容”拼起来算一个快照，用于
     * “变了才同步”。只取两边都能取到的字段（标题 / 起止时间 / 地点 / 正文 / 组织者 / 参与人），
     * 不取库里的审计字段，避免“只是被谁保存过一次”就误判成内容变了。
     */
    private String fingerprint(MtgPushContext ctx) {
        if (ctx == null) {
            return "";
        }
        MtgOaItem item = ctx.getItem();
        StringBuilder sb = new StringBuilder();
        if (item != null) {
            sb.append(OaonesUtil.s(item.getTitle())).append('\u0001')
              .append(OaonesUtil.parseDateTime(item.getBeginTime())).append('\u0001')
              .append(OaonesUtil.parseDateTime(item.getEndTime())).append('\u0001')
              .append(OaonesUtil.s(item.getLocation())).append('\u0001')
              .append(OaonesUtil.s(item.getContent())).append('\u0001');
        }
        sb.append(OaonesUtil.s(ctx.getOwnerUserId())).append('\u0001');
        sb.append(joinSorted(ctx.getAttendeeUserIds()));
        return md5(sb.toString());
    }

    /**
     * 平台侧内容指纹（拉取到的平台事件）：字段与 {@link #fingerprint(MtgPushContext)} 一一对应，
     * 但取的是平台侧的值（正文 / 地点 / 组织者都是平台返回的那份）。
     */
    private String fingerprint(MtgPlatformItem pItem, List<String> attendeeWxIds) {
        if (pItem == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(OaonesUtil.s(pItem.getTitle())).append('\u0001')
          .append(OaonesUtil.parseDateTime(pItem.getBeginTime())).append('\u0001')
          .append(OaonesUtil.parseDateTime(pItem.getEndTime())).append('\u0001')
          .append(OaonesUtil.s(pItem.getLocation())).append('\u0001')
          .append(OaonesUtil.s(pItem.getContent())).append('\u0001')
          .append(OaonesUtil.s(pItem.getOwnerWxId())).append('\u0001')
          .append(pItem.getStatus() == null ? -1 : pItem.getStatus().intValue()).append('\u0001');
        sb.append(joinSorted(attendeeWxIds));
        return md5(sb.toString());
    }

    /** 参与人排序后拼接（顺序不影响指纹）。 */
    private String joinSorted(List<String> ids) {
        List<String> list = new ArrayList<String>();
        if (ids != null) {
            for (String one : ids) {
                if (!isBlank(one)) {
                    list.add(one.trim());
                }
            }
        }
        java.util.Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        for (String one : list) {
            sb.append(one).append(',');
        }
        return sb.toString();
    }

    /** MD5 十六进制（长度 32，与 {@code OaonesConst#MTG_HASH_LENGTH} 一致）。 */
    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(OaonesUtil.s(text).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() < 2) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Throwable t) {
            // 算不出来时退回长度受限的原文（同样能起到“内容变了值就变”的作用）
            String value = OaonesUtil.s(text);
            return value.length() <= OaonesConst.MTG_HASH_LENGTH
                    ? value : value.substring(0, OaonesConst.MTG_HASH_LENGTH);
        }
    }

    /** 推送成功后建立 / 刷新映射（source = 0），记下 OA 内容快照，并把平台侧旧基线作废。 */
    private void savePushLink(int biz, int type, int cfgId, MtgOaItem item, MtgPushContext ctx,
                              MtgSyncLink link, String wxId, String operatorId) {
        String hash = fingerprint(ctx);
        String oaId = OaonesUtil.s(item.getOaId());
        if (link == null) {
            link = findLinkByOa(biz, type, cfgId, oaId);
        }
        if (link == null && !isBlank(wxId)) {
            link = findLinkByWx(biz, type, cfgId, wxId);
        }
        if (link == null) {
            MtgSyncLink created = newLink(biz, type, cfgId, oaId, wxId,
                    OaonesConst.MTG_LINK_SOURCE_OA, operatorId);
            created.setOaHash(hash);
            final MtgSyncLink seed = created;
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertLink(OaonesParam.of("e", seed)));
            return;
        }
        // 平台侧内容已经是刚推的这份：旧基线作废（wx_hash 置空），下一轮拉取只重建基线，
        // 不会误报“平台侧改过了”
        updateLinkSnapshot(link, wxId, hash, "", operatorId);
    }

    /**
     * 刷新映射的快照列（传 null = 不动那一列，传空串 = 清空那一列）。
     *
     * <p>{@code oaHash} / {@code wxHash} 为空（null 或空串）都按“还没建基线”处理：
     * 该轮只记基线、不同步，因此重置基线是安全的，不会把历史数据重跑一遍。</p>
     */
    private void updateLinkSnapshot(MtgSyncLink link, String wxId, String oaHash, String wxHash,
                                    String operatorId) {
        if (link == null || link.getId() == null) {
            return;
        }
        link.setWxId(isBlank(wxId) ? null : wxId);
        link.setOaHash(oaHash);
        link.setWxHash(wxHash);
        link.setLastPullTime(OaonesUtil.now());
        link.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        link.setModedatamodifydatetime(OaonesUtil.now());
        OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateLink(OaonesParam.of("e", link)));
    }

    /** 删掉一条映射（源端删除已同步完，或映射已失效、没必要再跟踪）。 */
    private void dropLink(MtgSyncLink link) {
        if (link == null || link.getId() == null) {
            return;
        }
        OaonesMyBatis.run(MtgSyncMapper.class, m -> m.deleteLink(OaonesParam.of("id", link.getId())));
    }

    /**
     * 映射对账（推送阶段跑）：对「业务 + 平台 + 租户」下的每条映射，按<b>谁创建谁维护</b>处理创建端的改动。
     *
     * <ul>
     *   <li><b>OA 创建（source = 0）</b>：OA 对象已删 / 取消 → 调平台删除接口把平台日程也删掉，
     *       写一条“删除”记录后清掉映射；OA 对象还在但内容变了（时间窗外被改也算）→ 补推一条“更新”；</li>
     *   <li><b>平台创建（source = 1）</b>：OA 侧改过 → <b>不反向同步</b>，只在同步记录里留一条说明
     *       （同一处改动只留一条，之后同样内容不再重复记）；OA 侧把这行删了 → 同样不回删平台日程。</li>
     * </ul>
     *
     * @return 四元数组：{补推的更新数, 跳过数, 失败数, 删除数}
     */
    private int[] reconcileLinks(MtgPushAdapter adapter, MtgSyncCfg cfg, QywxConfig tenant, String batchId,
                                 Map<String, MtgSyncUser> userPrefs, boolean tenantOa2Third,
                                 String operatorId, Map<String, String> subNames) {
        int biz = cfg.getBiz() == null ? OaonesConst.MTG_BIZ_MEETING : cfg.getBiz();
        int type = cfg.getType() == null ? OaonesConst.TYPE_WECHAT : cfg.getType();
        int cfgId = cfg.getCfgid() == null ? 0 : cfg.getCfgid();
        int updated = 0, skipped = 0, failed = 0, deleted = 0;
        List<MtgSyncLink> links = listLinks(biz, type, cfgId, null);
        if (links.size() >= LINK_SCAN_LIMIT) {
            OaonesLog.step("[mtgSync] 映射数达到本轮上限 " + LINK_SCAN_LIMIT + "，剩余对象下一轮继续对账"
                    + " biz=" + biz + " type=" + type + " cfg=" + cfgId);
        }
        for (MtgSyncLink link : links) {
            String oaId = OaonesUtil.s(link.getOaId()).trim();
            if (oaId.length() == 0) {
                continue;
            }
            MtgOaItem item = getOaItem(biz, oaId);
            boolean gone = item == null
                    || (item.getDeleted() != null && item.getDeleted().intValue() == 1);
            if (isOaOrigin(link)) {
                if (gone) {
                    // 创建端（OA）删了 → 接收方（平台）也要删
                    int op = pushDelete(adapter, cfg, tenant, link, batchId, operatorId, subNames);
                    if (op == OaonesConst.SYNC_OP_DELETE) {
                        deleted++;
                    } else if (op == OaonesConst.SYNC_OP_FAIL) {
                        failed++;
                    }
                    continue;
                }
                item.setSubId(pickSubId(item));
                item.setSubName(OaonesUtil.s(subNames.get(item.getSubId())));
                String hash = fingerprint(buildContext(biz, type, cfgId, tenant, item, link.getWxId(),
                        userPrefs, tenantOa2Third, cfg.getAppId()));
                String old = OaonesUtil.s(link.getOaHash());
                if (old.length() == 0) {
                    updateLinkSnapshot(link, null, hash, null, operatorId);   // 只建基线
                    continue;
                }
                if (hash.equals(old)) {
                    continue;   // 内容没变：不调接口、也不写记录
                }
                int op = pushOne(adapter, biz, type, cfgId, tenant, batchId, item, link.getWxId(), "",
                        operatorId, userPrefs, tenantOa2Third, cfg.getAppId(), link);
                if (op == OaonesConst.SYNC_OP_UPDATE) {
                    updated++;
                } else if (op == OaonesConst.SYNC_OP_SKIP) {
                    skipped++;
                } else if (op == OaonesConst.SYNC_OP_FAIL) {
                    failed++;
                }
                continue;
            }
            // 平台创建的对象（source = 1）：OA 侧的改动不反向同步
            if (gone) {
                continue;   // OA 侧删了平台创建的记录：按口径不回删平台日程，也不再写回 OA
            }
            item.setSubId(pickSubId(item));
            item.setSubName(OaonesUtil.s(subNames.get(item.getSubId())));
            String hash = fingerprint(buildContext(biz, type, cfgId, tenant, item, link.getWxId(),
                    userPrefs, tenantOa2Third, cfg.getAppId()));
            String old = OaonesUtil.s(link.getOaHash());
            if (old.length() == 0) {
                updateLinkSnapshot(link, null, hash, null, operatorId);   // 只建基线
                continue;
            }
            if (hash.equals(old)) {
                continue;
            }
            writeLog(biz, type, cfgId, batchId, item, link.getWxId(), OaonesConst.SYNC_OP_SKIP,
                    "OA 侧修改了这条" + OaonesConst.mtgBizText(biz) + "，但该数据由第三方平台创建"
                            + OaonesConst.MTG_FOREIGN_HINT + "，不做反向同步（平台侧内容保持不变）",
                    operatorId);
            updateLinkSnapshot(link, null, hash, null, operatorId);
            skipped++;
        }
        return new int[]{updated, skipped, failed, deleted};
    }

    /**
     * 把“OA 侧已删除 / 已取消”同步到平台：调平台删除接口，成功后写一条“删除”记录并清掉映射。
     *
     * <p>删除失败按 {@code OaonesConst.MTG_DELETE_RETRY_HOURS} 节流重试：同一对象在这个时间内
     * 只再试一次，避免“平台侧其实已经删掉、我们一直删失败”这种场景每个轮询周期刷一条失败记录。</p>
     *
     * @return {@code OaonesConst.SYNC_OP_DELETE} 已删除 / {@code OaonesConst.SYNC_OP_FAIL} 失败 /
     *         {@code -1} 被节流跳过（本轮什么都不做）
     */
    private int pushDelete(MtgPushAdapter adapter, MtgSyncCfg cfg, QywxConfig tenant, MtgSyncLink link,
                           String batchId, String operatorId, Map<String, String> subNames) {
        int biz = cfg.getBiz() == null ? OaonesConst.MTG_BIZ_MEETING : cfg.getBiz();
        int type = cfg.getType() == null ? OaonesConst.TYPE_WECHAT : cfg.getType();
        int cfgId = cfg.getCfgid() == null ? 0 : cfg.getCfgid();
        String oaId = OaonesUtil.s(link.getOaId());
        MtgSyncLog last = lastLog(biz, type, cfgId, oaId);
        if (last != null && last.getOp() != null
                && last.getOp().intValue() == OaonesConst.SYNC_OP_FAIL
                && withinHours(OaonesUtil.s(last.getModedatacreatedate()),
                        OaonesUtil.s(last.getModedatacreatetime()),
                        OaonesConst.MTG_DELETE_RETRY_HOURS)) {
            return -1;   // 刚试过且失败：本轮不再试
        }
        // 源端对象已删除，记录里带不出标题 / 时间：从最近一条同步记录里取回来，页面上一眼看得出是哪条
        MtgOaItem logItem = new MtgOaItem();
        logItem.setOaId(oaId);
        if (last != null) {
            logItem.setTitle(last.getOaTitle());
            logItem.setBeginTime(last.getBeginTime());
            logItem.setEndTime(last.getEndTime());
            logItem.setSubName(last.getOaSubName());
            logItem.setOwnerId("");
        }
        MtgPushContext ctx = deleteContext(type, cfgId, tenant, cfg, link.getWxId());
        try {
            adapter.delete(ctx);
            writeLog(biz, type, cfgId, batchId, logItem, link.getWxId(), OaonesConst.SYNC_OP_DELETE,
                    "OA 侧的" + OaonesConst.mtgBizText(biz) + "已删除 / 取消，已同步删除平台日程"
                            + (isBlank(link.getWxId()) ? "" : "（平台日程ID " + link.getWxId() + "）"),
                    operatorId);
            dropLink(link);
            return OaonesConst.SYNC_OP_DELETE;
        } catch (Exception e) {
            writeLog(biz, type, cfgId, batchId, logItem, link.getWxId(), OaonesConst.SYNC_OP_FAIL,
                    "OA 侧已删除 / 取消，但删除平台日程失败：" + msgOf(e)
                            + "（" + OaonesConst.MTG_DELETE_RETRY_HOURS + " 小时内不再重试）",
                    operatorId);
            OaonesLog.error("[mtgSync] 删除平台日程失败 biz=" + biz + " type=" + type + " cfg=" + cfgId
                    + " oaId=" + oaId + " wxId=" + link.getWxId(), e);
            return OaonesConst.SYNC_OP_FAIL;
        }
    }

    /** 删除平台日程用的上下文（只需要凭据 + 平台对象ID，不读 OA 内容）。 */
    private MtgPushContext deleteContext(int type, int cfgId, QywxConfig tenant, MtgSyncCfg cfg, String wxId) {
        MtgPushContext ctx = new MtgPushContext();
        ctx.setType(type);
        ctx.setCfgId(cfgId);
        ctx.setCorpId(OaonesUtil.s(tenant.getCorpId()));
        MtgAppOption app = syncApp(type, cfgId, cfg == null ? null : cfg.getAppId());
        ctx.setCorpSecret(secretOf(app, tenant));
        ctx.setAgentId(agentIdOf(app, tenant));
        ctx.setExistWxId(OaonesUtil.s(wxId));
        ctx.setUpdate(true);
        ctx.setItem(new MtgOaItem());
        return ctx;
    }

    /** {@code date + time} 是否在最近 hours 小时内（时间取不到时按“不在窗口内”处理）。 */
    private boolean withinHours(String date, String time, int hours) {
        String text = OaonesUtil.s(date).trim();
        if (text.length() == 0) {
            return false;
        }
        String t = OaonesUtil.s(time).trim();
        long at = OaonesUtil.parseDateTime(text + " " + (t.length() == 0 ? "00:00:00" : t));
        if (at <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - at <= hours * 3600L * 1000L;
    }

    /**
     * 跑一次同步。
     *
     * @param cfg        该「业务 + 平台 + 租户」的设置
     * @param operatorId 操作人（审计字段）
     * @param createOnly true = 只推还没成功同步过的对象（调度器用）；false = 也更新已同步的对象（“立即同步”用）
     */
    private JSONObject syncCfg(MtgSyncCfg cfg, String operatorId, boolean createOnly) {
        int biz = cfg.getBiz() == null ? OaonesConst.MTG_BIZ_MEETING : cfg.getBiz();
        int type = cfg.getType() == null ? OaonesConst.TYPE_WECHAT : cfg.getType();
        int cfgId = cfg.getCfgid() == null ? 0 : cfg.getCfgid();
        String batchId = ("mb" + biz + "-" + type + "-" + cfgId + "-" + System.currentTimeMillis());

        JSONObject summary = new JSONObject();
        summary.put("biz", biz);
        summary.put("type", type);
        summary.put("cfg_id", cfgId);
        summary.put("batch_id", batchId);
        int created = 0, updated = 0, skipped = 0, failed = 0, deleted = 0, scanned = 0;

        QywxConfig tenant = tenant(type, cfgId);
        if (tenant == null) {
            throw new RuntimeException("租户不存在或已删除（cfg_id=" + cfgId + "）");
        }
        MtgPushAdapter adapter = adapter(type);
        Map<String, String> subNames = subNameMap();
        // 租户级同步方向：租户没勾「OA 同步到第三方」时本次不推送
        //（只选「第三方→OA」时由拉取链路处理，这里不逐条写跳过记录以免刷屏）
        boolean tenantOa2Third = dirOn(cfg.getDirOa2Third());
        if (!tenantOa2Third) {
            String msg = "该租户未勾选「OA 同步到第三方」方向，本次不推送（第三方→OA 由拉取链路处理）";
            summary.put("scanned", 0);
            summary.put("created", 0);
            summary.put("updated", 0);
            summary.put("skipped", 0);
            summary.put("failed", 0);
            summary.put("message", msg);
            OaonesLog.step("[mtgSync] " + OaonesConst.mtgBizText(biz) + " type=" + type + " cfg=" + cfgId
                    + " -> " + msg);
            return summary;
        }
        // 个人同步开关 / 方向：本次同步该「业务 + 平台 + 租户」下显式落过行的人员
        // （创建人命中整条判断、参与人命中从参与人里剔除），一次查询供本次所有对象复用
        Map<String, MtgSyncUser> userPrefs = userPrefs(biz, type, cfgId);
        // 「第三方→OA」拉取进来的 OA 记录：平台已有，不再推回平台（否则会造成重复日程）
        Set<String> platformOrigin = platformOriginOaIds(biz, type, cfgId);

        Map<String, Object> p = OaonesParam.of();
        p.put("fromDate", OaonesUtil.shiftDate(OaonesUtil.nowDate(), -OaonesProps.mtgSyncWindowBeforeDays()));
        p.put("toDate", OaonesUtil.shiftDate(OaonesUtil.nowDate(), OaonesProps.mtgSyncWindowAfterDays()));
        p.put("limit", String.valueOf(OaonesProps.mtgSyncItemLimit()));
        List<MtgOaItem> items = OaonesMyBatis.call(MtgSyncMapper.class,
                m -> biz == OaonesConst.MTG_BIZ_SCHEDULE ? m.listSchedules(p) : m.listMeetings(p));
        if (items == null) {
            items = new ArrayList<MtgOaItem>();
        }
        // 「只同步一次」：调度器（createOnly）只推**从来没处理过**的新对象——
        // 只要有过同步记录（不管新增 / 更新 / 跳过 / 失败）就不再自动推，
        // 需要重推时由页面「同步清单」手工点「重推」。
        // 已经推成功过、并建立了映射的对象不受这条限制：它们按快照比对“OA 改了才推更新”。
        Set<String> attempted = createOnly
                ? attemptedOaIds(biz, type, cfgId, items) : new HashSet<String>();
        // 本次扫到、但因为“已经同步过一次（含跳过 / 失败）”而被跳过的条数（只用于日志 / 汇总）
        int already = 0;

        for (MtgOaItem item : items) {
            String oaId = OaonesUtil.s(item.getOaId()).trim();
            if (platformOrigin.contains(oaId)) {
                // 平台创建的数据：主链路由拉取维护；OA 侧改过 / 删过都不反向同步（对账时留痕）
                continue;
            }
            String subId = pickSubId(item);
            scanned++;
            item.setSubId(subId);
            item.setSubName(OaonesUtil.s(subNames.get(subId)));

            MtgSyncLink link = findLinkByOa(biz, type, cfgId, oaId);
            String existWxId = lastSuccessWxId(biz, type, cfgId, item.getOaId());
            if (link == null && existWxId != null) {
                // 兼容升级：此前推成功过、映射表还没有行（老版本只给“拉取建立”的对象写映射）——
                // 这里补一条 source = 0 的映射并把当前内容记成基线，**不重推**，避免升级后把历史对象刷一遍
                MtgSyncLink byWx = findLinkByWx(biz, type, cfgId, existWxId);
                if (byWx == null) {
                    MtgSyncLink created0 = newLink(biz, type, cfgId, oaId, existWxId,
                            OaonesConst.MTG_LINK_SOURCE_OA, operatorId);
                    created0.setOaHash(fingerprint(buildContext(biz, type, cfgId, tenant, item, existWxId,
                            userPrefs, tenantOa2Third, cfg.getAppId())));
                    final MtgSyncLink seed = created0;
                    OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertLink(OaonesParam.of("e", seed)));
                } else {
                    // 该平台对象ID 已经被别的映射占用（历史数据异常）：不动它，避免唯一键冲突
                    OaonesLog.step("[mtgSync] 平台对象ID 已被其它映射占用，跳过补映射 oaId=" + oaId
                            + " wxId=" + existWxId);
                }
                continue;
            }
            if (link != null && !isOaOrigin(link)) {
                continue;   // 平台创建的对象：不推送（对账阶段按快照判断“OA 侧改过没有”）
            }
            if (link == null && createOnly && attempted.contains(oaId)) {
                already++;
                continue; // 已经同步过一次（含跳过 / 失败），不再自动重推
            }
            if (link != null) {
                String oaHash = fingerprint(buildContext(biz, type, cfgId, tenant, item, link.getWxId(),
                        userPrefs, tenantOa2Third, cfg.getAppId()));
                if (OaonesUtil.s(link.getOaHash()).length() == 0) {
                    // 还没有基线（老映射 / 刚补的行）：本轮只记基线，不重推
                    updateLinkSnapshot(link, null, oaHash, null, operatorId);
                    continue;
                }
                if (oaHash.equals(OaonesUtil.s(link.getOaHash()))) {
                    continue;   // OA 侧没改：不调接口、也不写同步记录
                }
            }
            // 有映射时按映射里的平台对象ID 走“更新”（比日志更权威），没映射时才用日志里的ID
            int op = pushOne(adapter, biz, type, cfgId, tenant, batchId, item,
                    link != null ? link.getWxId() : existWxId, "", operatorId,
                    userPrefs, tenantOa2Third, cfg.getAppId(), link);
            if (op == OaonesConst.SYNC_OP_CREATE) {
                created++;
            } else if (op == OaonesConst.SYNC_OP_UPDATE) {
                updated++;
            } else if (op == OaonesConst.SYNC_OP_SKIP) {
                skipped++;
            } else {
                failed++;
            }
        }

        // 映射对账（含时间窗外的历史对象）：① OA 源端对象被删 / 取消 → 平台侧也删；
        // ② OA 源端对象被改 → 补推更新；③ 平台源端对象被 OA 改过 → 只记一条“不反向同步”的说明。
        int[] reconciled = reconcileLinks(adapter, cfg, tenant, batchId, userPrefs, tenantOa2Third,
                operatorId, subNames);
        updated += reconciled[0];
        skipped += reconciled[1];
        failed += reconciled[2];
        deleted += reconciled[3];

        String resultText = "本次推送：扫描 " + scanned + " 条（其中 " + already + " 条已同步过、不再自动重推），新增 "
                + created + " / 更新 " + updated + " / 删除 " + deleted + " / 跳过 " + skipped + " / 失败 " + failed;
        summary.put("scanned", scanned);
        summary.put("already", already);
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("deleted", deleted);
        summary.put("message", resultText);
        OaonesLog.step("[mtgSync] " + OaonesConst.mtgBizText(biz) + " type=" + type + " cfg=" + cfgId
                + " -> " + resultText);
        return summary;
    }

    /**
     * 一条 OA 对象所属分部（只用于记录里展示“分部”一列）。
     *
     * <p>页面已取消“同步范围”，同步<b>不再按分部筛选</b>：这里只是尽力取一个分部ID用于留痕，
     * 取不到（对象没带分部信息）就返回空串，对象照常同步。</p>
     */
    private String pickSubId(MtgOaItem item) {
        List<String> candidates = new ArrayList<String>();
        if (!isBlank(item.getSubId())) {
            candidates.add(item.getSubId().trim());
        }
        if (!isBlank(item.getRawSubIds())) {
            for (String one : item.getRawSubIds().split("[,，;；|]")) {
                if (!isBlank(one)) {
                    candidates.add(one.trim());
                }
            }
        }
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    private List<String> resolveAttendees(int biz, int cfgId, MtgOaItem item, String owner,
                                          Map<String, MtgSyncUser> userPrefs, boolean tenantOa2Third) {
        Map<String, Object> p = OaonesParam.of("oaId", item.getOaId());
        List<String> oaUserIds = OaonesMyBatis.call(MtgSyncMapper.class,
                m -> biz == OaonesConst.MTG_BIZ_SCHEDULE
                        ? m.listScheduleMembers(p) : m.listMeetingMembers(p));
        Set<String> ids = new LinkedHashSet<String>();
        if (oaUserIds != null) {
            ids.addAll(oaUserIds);
        }
        if (!isBlank(item.getOwnerId())) {
            ids.add(item.getOwnerId().trim());
        }
        // 参与人里“不同步”或未选择「OA 同步到第三方」的，不推到第三方平台
        //（本人数据不外发；创建人已在上游整条判断）
        if (userPrefs != null && !userPrefs.isEmpty()) {
            java.util.Iterator<String> it = ids.iterator();
            while (it.hasNext()) {
                MtgSyncUser pref = userPrefs.get(it.next());
                if (pref == null) {
                    continue;
                }
                boolean on = !(pref.getEnabled() != null && pref.getEnabled().intValue() == 0)
                        && effectiveOa2Third(tenantOa2Third, pref);
                if (!on) {
                    it.remove();
                }
            }
        }
        List<String> platformIds = resolvePlatformUserIds(cfgId, ids);
        if (platformIds.size() > ATTENDEE_LIMIT) {
            platformIds = platformIds.subList(0, ATTENDEE_LIMIT);
        }
        return platformIds;
    }

    private String firstPlatformUserId(int cfgId, String oaUserId) {
        if (isBlank(oaUserId)) {
            return null;
        }
        List<String> ids = new ArrayList<String>();
        ids.add(oaUserId.trim());
        List<String> resolved = resolvePlatformUserIds(cfgId, ids);
        return resolved.isEmpty() ? null : resolved.get(0);
    }

    private List<String> resolvePlatformUserIds(int cfgId, java.util.Collection<String> oaUserIds) {
        List<String> result = new ArrayList<String>();
        if (oaUserIds == null || oaUserIds.isEmpty()) {
            return result;
        }
        List<String> ids = new ArrayList<String>();
        for (String one : oaUserIds) {
            if (!isBlank(one)) {
                ids.add(OaonesUtil.s(one).trim());
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("cfgId", cfgId);
        p.put("oaUserIds", ids);
        List<String> resolved = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.resolvePlatformUserIds(p));
        if (resolved != null) {
            result.addAll(resolved);
        }
        return result;
    }

    private void writeLog(int biz, int type, int cfgId, String batchId, MtgOaItem item, String wxId,
                          int op, String message, String operatorId) {
        MtgSyncLog log = new MtgSyncLog();
        log.setBiz(biz);
        log.setType(type);
        log.setCfgid(cfgId);
        log.setBatchId(batchId);
        log.setOaId(OaonesUtil.s(item.getOaId()));
        log.setOaTitle(cut(OaonesUtil.s(item.getTitle()), 500));
        log.setOaSubId(OaonesUtil.s(item.getSubId()));
        log.setOaSubName(cut(OaonesUtil.s(item.getSubName()), 255));
        log.setOaDeptId(OaonesUtil.s(item.getDeptId()));
        log.setOaDeptName(cut(OaonesUtil.s(item.getDeptName()), 255));
        log.setBeginTime(OaonesUtil.s(item.getBeginTime()));
        log.setEndTime(OaonesUtil.s(item.getEndTime()));
        log.setWxId(wxId);
        log.setOp(op);
        log.setMessage(cut(message, 1000));
        log.setModedatacreater(OaonesUtil.i(operatorId, 0));
        log.setModedatacreatedate(OaonesUtil.nowDate());
        log.setModedatacreatetime(OaonesUtil.nowTime());
        log.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        log.setModedatamodifydatetime(OaonesUtil.now());
        Map<String, Object> p = OaonesParam.of("e", log);
        try {
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertLog(p));
        } catch (Throwable t) {
            // 写记录失败绝不影响同步本身（与消息中心日志同一口径）
            OaonesLog.error("[mtgSync] 写同步记录失败 biz=" + biz + " type=" + type + " cfg=" + cfgId, t);
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    // ==================================================================
    // 第三方向拉取（第三方平台 → OA）
    // ==================================================================

    @Override
    public JSONObject pullNow() {
        int cfgScanned = 0, created = 0, updated = 0, skipped = 0, failed = 0;
        Map<String, Object> p = OaonesParam.of();
        p.put("limit", String.valueOf(OaonesProps.mtgSyncBatch()));
        List<MtgSyncCfg> cfgs = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listEnabledCfg(p));
        if (cfgs != null) {
            for (MtgSyncCfg cfg : cfgs) {
                // 只拉取了「第三方→OA」方向的租户
                if (!dirOn(cfg.getDirThird2Oa())) {
                    continue;
                }
                cfgScanned++;
                try {
                    JSONObject one = pullCfg(cfg);
                    created += one.getIntValue("created");
                    updated += one.getIntValue("updated");
                    skipped += one.getIntValue("skipped");
                    failed += one.getIntValue("failed");
                } catch (Throwable t) {
                    failed++;
                    OaonesLog.error("[mtgPull] 拉取失败 biz=" + cfg.getBiz() + " type=" + cfg.getType()
                            + " cfg=" + cfg.getCfgid(), t);
                }
            }
        }
        JSONObject summary = new JSONObject();
        summary.put("cfgs", cfgScanned);
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("message", "第三方→OA 拉取：租户 " + cfgScanned + "，新增 " + created
                + " / 更新 " + updated + " / 跳过 " + skipped + " / 失败 " + failed);
        OaonesLog.step("[mtgPull] " + summary.getString("message"));
        return summary;
    }

    /** 拉取一个「业务 + 平台 + 租户」的第三方事件（平台新建 → 在 OA 新建；平台改动 → 更新 OA）。 */
    private JSONObject pullCfg(MtgSyncCfg cfg) {
        int biz = cfg.getBiz() == null ? OaonesConst.MTG_BIZ_MEETING : cfg.getBiz();
        int type = cfg.getType() == null ? OaonesConst.TYPE_WECHAT : cfg.getType();
        int cfgId = cfg.getCfgid() == null ? 0 : cfg.getCfgid();
        JSONObject summary = new JSONObject();
        if (!dirOn(cfg.getDirThird2Oa())) {
            putCounts(summary, 0, 0, 0, 0, 0, 0);
            summary.put("message", "该租户未勾选「第三方→OA」同步方向，未拉取");
            return summary;
        }
        QywxConfig tenant = tenant(type, cfgId);
        if (tenant == null) {
            putCounts(summary, 0, 0, 0, 0, 1, 0);
            return summary;
        }
        boolean tenantT2o = dirOn(cfg.getDirThird2Oa());
        Map<String, MtgSyncUser> userPrefs = userPrefs(biz, type, cfgId);
        List<String> calIds = boundPlatformUserIds(cfgId, OaonesProps.mtgPullMemberLimit());

        MtgPullContext ctx = new MtgPullContext();
        ctx.setType(type);
        ctx.setCfgId(cfgId);
        ctx.setCorpId(OaonesUtil.s(tenant.getCorpId()));
        // 与推送同一口径：优先用该租户选定的「绑定应用」的凭据（应用要有日程权限），未指定则回退租户配置
        MtgAppOption app = syncApp(type, cfgId, cfg.getAppId());
        ctx.setCorpSecret(secretOf(app, tenant));
        ctx.setAgentId(agentIdOf(app, tenant));
        ctx.setCalendarIds(calIds);
        ctx.setFromTime(OaonesUtil.shiftDate(OaonesUtil.nowDate(),
                -OaonesProps.mtgPullWindowBeforeDays()) + " 00:00:00");
        ctx.setToTime(OaonesUtil.shiftDate(OaonesUtil.nowDate(),
                OaonesProps.mtgPullWindowAfterDays()) + " 23:59:59");
        ctx.setLimit(OaonesProps.mtgPullItemLimit());

        MtgPullAdapter adapter = pullAdapter(type);
        List<MtgPlatformItem> items;
        try {
            items = adapter.list(ctx);
        } catch (Exception e) {
            OaonesLog.error("[mtgPull] 平台拉取失败 type=" + type + " cfg=" + cfgId, e);
            putCounts(summary, 0, 0, 0, 0, 1, 0);
            return summary;
        }
        if (items == null) {
            items = new ArrayList<MtgPlatformItem>();
        }
        String batchId = "mp" + biz + "-" + type + "-" + cfgId + "-" + System.currentTimeMillis();
        int limit = OaonesProps.mtgPullItemLimit();
        int scanned = 0, created = 0, updated = 0, skipped = 0, failed = 0, deleted = 0;
        Set<String> seen = new HashSet<String>();
        boolean truncated = false;
        for (MtgPlatformItem item : items) {
            if (item == null || isBlank(item.getWxId())) {
                continue;
            }
            if (scanned >= limit) {
                truncated = true;
                break;   // 本轮按 item.limit 截断：剩下的下一轮继续
            }
            if (!seen.add(item.getWxId())) {
                continue; // 同一平台事件在多个日历里出现，按平台对象ID 去重
            }
            scanned++;
            try {
                int op = pullOne(biz, type, cfgId, batchId, item, userPrefs, tenantT2o);
                if (op == OaonesConst.SYNC_OP_CREATE) {
                    created++;
                } else if (op == OaonesConst.SYNC_OP_UPDATE) {
                    updated++;
                } else if (op == OaonesConst.SYNC_OP_DELETE) {
                    deleted++;
                } else {
                    skipped++;
                }
            } catch (Throwable t) {
                failed++;
                OaonesLog.error("[mtgPull] 拉取单个对象失败 type=" + type + " cfg=" + cfgId
                        + " wxId=" + item.getWxId(), t);
            }
        }
        // 删除对账（平台侧删了 → OA 侧标记删除）：只处理“平台创建”的对象（source = 1），
        // OA 推上去的对象平台侧删了不反向处理（口径见 reconcilePulledDeletes）。
        // 本轮被 item.limit 截断时**不对账**：没被扫到的对象不能当成“平台已经删了”。
        if (truncated) {
            OaonesLog.step("[mtgPull] 本轮平台对象数超过 mtg.pull.item.limit=" + limit
                    + "，跳过删除对账（下一轮继续）type=" + type + " cfg=" + cfgId);
        } else {
            int[] drop = reconcilePulledDeletes(adapter, cfg, tenant, ctx, batchId, seen, userPrefs);
            skipped += drop[0];
            failed += drop[1];
            deleted += drop[2];
        }
        putCounts(summary, scanned, created, updated, skipped, failed, deleted);
        return summary;
    }

    /**
     * 拉取侧的删除对账：**平台创建的**对象（映射 {@code source = 1}）在平台上被删 / 取消后，
     * 在 OA 侧标记删除（日程 {@code deleted = '1'} / 会议 {@code cancel = '1'}，不物理删行）。
     *
     * <p>判据尽量保守，宁可晚一轮也不误删 OA 数据：</p>
     * <ol>
     *   <li>只对本轮**确实取到结果**的日历做推断（{@code ctx.getListedCalendars()}：
     *       没取过的日历——缺少成员绑定、接口报错被跳过——一律不推断）；</li>
     *   <li>OA 镜像记录的开始时间落在本轮查询窗口内（窗口外的对象本来就不在拉取范围内）；</li>
     *   <li>该平台对象ID 不在本轮拉取结果里；</li>
     *   <li>企业微信另有“按 ID 现查”接口（{@code /cgi-bin/oa/schedule/get}），能查就按它判
     *       （查不到 / {@code status = 1 已取消}都算已删除）；钉钉 / 飞书没有这个接口，
     *       退回上面的“日历 + 窗口 + 不在结果里”推断。</li>
     * </ol>
     *
     * <p>OA 推上去的对象（{@code source = 0}）平台侧删了<b>不反向处理</b>：OA 是创建方，
     * 平台侧的删除属于“对方改了”，按口径不回写 OA、也不在 OA 标记删除。</p>
     *
     * @return 三元数组：{跳过数, 失败数, 删除数}
     */
    private int[] reconcilePulledDeletes(MtgPullAdapter adapter, MtgSyncCfg cfg, QywxConfig tenant,
                                         MtgPullContext ctx, String batchId, Set<String> seenWxIds,
                                         Map<String, MtgSyncUser> userPrefs) {
        int biz = cfg.getBiz() == null ? OaonesConst.MTG_BIZ_MEETING : cfg.getBiz();
        int type = cfg.getType() == null ? OaonesConst.TYPE_WECHAT : cfg.getType();
        int cfgId = cfg.getCfgid() == null ? 0 : cfg.getCfgid();
        String operator = String.valueOf(OaonesConst.SYS_ADMIN_USER_ID);
        int skipped = 0, failed = 0, deleted = 0;
        List<MtgSyncLink> links = listLinks(biz, type, cfgId, OaonesConst.MTG_LINK_SOURCE_PLATFORM);
        if (links.isEmpty()) {
            return new int[]{0, 0, 0};
        }
        long from = OaonesUtil.parseDateTime(ctx.getFromTime());
        long to = OaonesUtil.parseDateTime(ctx.getToTime());
        Map<String, MtgSyncLink> byWx = new LinkedHashMap<String, MtgSyncLink>();
        Map<String, MtgOaItem> items = new HashMap<String, MtgOaItem>();
        for (MtgSyncLink link : links) {
            String wxId = OaonesUtil.s(link.getWxId()).trim();
            if (wxId.length() == 0 || seenWxIds.contains(wxId)) {
                continue;   // 本轮平台结果里还在：不是删除
            }
            MtgOaItem item = getOaItem(biz, OaonesUtil.s(link.getOaId()));
            if (item == null || (item.getDeleted() != null && item.getDeleted().intValue() == 1)) {
                continue;   // OA 侧已经删了（或本来就没了）：无处理
            }
            long begin = OaonesUtil.parseDateTime(item.getBeginTime());
            if (from > 0L && to > 0L && (begin <= 0L || begin < from || begin > to)) {
                // 开始时间读不出来 / 在窗口外：本轮拉取本来就不会包含它，不能据此判断“平台删了”
                skipped++;
                continue;
            }
            byWx.put(wxId, link);
            items.put(wxId, item);
        }
        if (byWx.isEmpty()) {
            return new int[]{skipped, 0, 0};
        }
        // ① 能“按 ID 现查”的平台（企微）先精确问一遍
        Set<String> checked = new HashSet<String>();
        Set<String> gone = new HashSet<String>();
        if (adapter != null) {
            List<String> ids = new ArrayList<String>(byWx.keySet());
            for (int i = 0; i < ids.size(); i += 200) {
                List<String> slice = new ArrayList<String>(
                        ids.subList(i, Math.min(ids.size(), i + 200)));
                List<MtgPlatformItem> alive;
                try {
                    alive = adapter.getByIds(ctx, slice);
                } catch (Exception e) {
                    OaonesLog.error("[mtgPull] 按ID现查失败 type=" + type + " cfg=" + cfgId, e);
                    alive = null;
                }
                if (alive == null) {
                    break;   // 该平台不支持按 ID 现查：整批退回“按拉取结果推断”
                }
                Set<String> found = new HashSet<String>();
                for (MtgPlatformItem one : alive) {
                    if (one == null || isBlank(one.getWxId())) {
                        continue;
                    }
                    checked.add(one.getWxId().trim());
                    // status = 1（企微“已取消”）按删除处理
                    if (one.getStatus() != null && one.getStatus().intValue() == 1) {
                        gone.add(one.getWxId().trim());
                    } else {
                        found.add(one.getWxId().trim());
                    }
                }
                for (String id : slice) {
                    checked.add(id);
                    if (!found.contains(id)) {
                        gone.add(id);
                    }
                }
            }
        }
        for (Map.Entry<String, MtgSyncLink> entry : byWx.entrySet()) {
            String wxId = entry.getKey();
            MtgSyncLink link = entry.getValue();
            MtgOaItem item = items.get(wxId);
            boolean dropped;
            if (checked.contains(wxId)) {
                dropped = gone.contains(wxId);
            } else {
                // ② 退回“按拉取结果推断”：必须在“本轮确实取过”的日历上，且该日历属于这条记录的组织者
                String calId = firstPlatformUserId(cfgId, oaOwnerId(biz, OaonesUtil.s(link.getOaId())));
                dropped = !isBlank(calId) && ctx.getListedCalendars().contains(calId);
            }
            if (!dropped) {
                skipped++;
                continue;
            }
            try {
                Map<String, Object> p = OaonesParam.of("oaId", OaonesUtil.s(link.getOaId()));
                if (biz == OaonesConst.MTG_BIZ_SCHEDULE) {
                    OaonesMyBatis.run(MtgSyncMapper.class, m -> m.markWorkPlanDeleted(p));
                } else {
                    OaonesMyBatis.run(MtgSyncMapper.class, m -> m.markMeetingDeleted(p));
                }
                writeLog(biz, type, cfgId, batchId, item, wxId, OaonesConst.SYNC_OP_DELETE,
                        "平台侧的" + OaonesConst.mtgBizText(biz) + "已删除 / 取消，已同步在 OA 标记删除"
                                + "（不物理删行；平台日程ID " + wxId + "）",
                        operator);
                dropLink(link);
                deleted++;
            } catch (Throwable t) {
                failed++;
                OaonesLog.error("[mtgPull] 标记 OA 删除失败 type=" + type + " cfg=" + cfgId
                        + " wxId=" + wxId, t);
            }
        }
        return new int[]{skipped, failed, deleted};
    }

    @Override
    public JSONObject pullCfgNow(int biz, int type, String cfgId, String operatorId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        assertCfgRight(id, operatorId);
        MtgSyncCfg cfg = getCfg(biz, type, id);
        if (cfg == null) {
            // 没配过：按默认（开关关闭、方向双选）处理——页面显式点“立即拉取”时按默认方向拉一轮
            cfg = new MtgSyncCfg();
            cfg.setBiz(biz);
            cfg.setType(type);
            cfg.setCfgid(id);
            cfg.setEnabled(0);
            cfg.setDirOa2Third(1);
            cfg.setDirThird2Oa(1);
        }
        JSONObject one = pullCfg(cfg);
        one.put("biz", biz);
        one.put("type", type);
        one.put("cfg_id", id);
        return one;
    }

    /**
     * 拉取一条平台事件：按平台对象ID 找映射——
     * 有映射（此前从 OA 推过去 / 之前已拉取过）→ 更新对应 OA 记录；没有映射 → 在 OA 新建记录并写映射。
     */
    private int pullOne(int biz, int type, int cfgId, String batchId, MtgPlatformItem pItem,
                        Map<String, MtgSyncUser> userPrefs, boolean tenantT2o) {
        String operator = String.valueOf(OaonesConst.SYS_ADMIN_USER_ID);
        Map<String, Object> lp = OaonesParam.of();
        lp.put("biz", biz);
        lp.put("type", type);
        lp.put("cfgId", cfgId);
        lp.put("wxId", pItem.getWxId());
        MtgSyncLink link = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.findLinkByWx(lp));
        if (link == null) {
            // 兼容升级：此前由 OA 推送过去的对象只有推送日志、没有映射行；按日志补一条映射，
            // 免得把“本来就在平台上的日程”当成平台新建，在 OA 又建一条重复记录
            String loggedOa = OaonesUtil.s(OaonesMyBatis.call(MtgSyncMapper.class, m -> m.findLoggedOaIdByWx(lp)));
            if (loggedOa.length() > 0) {
                link = newLink(biz, type, cfgId, loggedOa, pItem.getWxId(), 0, operator);
                final MtgSyncLink seed = link;
                OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertLink(OaonesParam.of("e", seed)));
            }
        }
        if (link != null) {
            return applyPlatformItemToOa(biz, type, cfgId, batchId, pItem, link, userPrefs, tenantT2o, operator);
        }

        // 没有映射：平台新建的事件 —— 需要组织者能换算成 OA 人员，才能在 OA 落一条记录
        String ownerOa = mapOaUserByWx(cfgId, pItem.getOwnerWxId());
        MtgOaItem logItem = toLogItem(pItem, null, ownerOa);
        if (isBlank(ownerOa)) {
            writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_SKIP,
                    "第三方拉取：平台组织者未绑定 OA 账号（成员ID=" + OaonesUtil.s(pItem.getOwnerWxId())
                            + "），本次跳过", operator);
            return OaonesConst.SYNC_OP_SKIP;
        }
        if (!effectiveThird2Oa(tenantT2o, userPrefs.get(ownerOa))) {
            writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_SKIP,
                    "第三方拉取：组织者未选择「第三方→OA」或已设不同步，本次跳过", operator);
            return OaonesConst.SYNC_OP_SKIP;
        }
        MtgOaWrite w = buildWrite(pItem, ownerOa);
        w.setOwnerId(OaonesUtil.i(ownerOa, 0));
        w.setCreateDate(OaonesUtil.nowDate());
        w.setCreateTime(OaonesUtil.nowTime());
        List<Integer> memberIds = oaMemberIds(cfgId, pItem, ownerOa);
        w.setMemberCount(memberIds.size());
        w.setResourceId(joinIds(memberIds));
        if (biz == OaonesConst.MTG_BIZ_SCHEDULE) {
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertWorkPlan(OaonesParam.of("e", w)));
        } else {
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertMeeting(OaonesParam.of("e", w)));
        }
        if (w.getId() == null || w.getId() <= 0) {
            writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_FAIL,
                    "第三方拉取：写入 OA 记录失败（未取到自增ID）", operator);
            return OaonesConst.SYNC_OP_FAIL;
        }
        replaceMembers(biz, w, memberIds);
        MtgSyncLink nl = newLink(biz, type, cfgId, String.valueOf(w.getId()), pItem.getWxId(), 1, operator);
        // 记下平台侧内容快照：下一轮拉取“没变就不重复写 OA”，也在推送对账时用来判断“平台侧改过没有”
        nl.setWxHash(fingerprint(pItem, pItem.getAttendeeWxIds()));
        OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertLink(OaonesParam.of("e", nl)));
        logItem.setOaId(String.valueOf(w.getId()));
        writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_CREATE,
                "第三方拉取：平台新建的" + OaonesConst.mtgBizText(biz) + "已拉取到 OA", operator);
        return OaonesConst.SYNC_OP_CREATE;
    }

    /**
     * 把平台侧的一条日程写到它**已经对应**的那条 OA 记录上（对象映射已命中）。
     *
     * <p><b>只有平台创建的数据（{@code link.source = 1}）才允许更新 OA</b>：由 OA 推送到平台的数据
     * （{@code source = 0}）按“谁创建谁维护”的口径<b>不做反向更新</b>——平台侧的改动一律不写回 OA，
     * 只在内容**确实变了**的时候记一条说明（现场看同步记录就知道“为什么平台改了 OA 没变”），
     * 没变则不调接口、也不写记录。</p>
     *
     * <p>平台创建的数据这边：平台内容与快照 {@code wx_hash} 一致（没变）→ 直接跳过；
     * 变了（含被取消）→ 更新 OA 并把新内容记成快照；OA 侧把这行删了 → 不回写（也不回删平台日程）。</p>
     */
    private int applyPlatformItemToOa(int biz, int type, int cfgId, String batchId, MtgPlatformItem pItem,
                                      MtgSyncLink link, Map<String, MtgSyncUser> userPrefs,
                                      boolean tenantT2o, String operator) {
        String oaId = OaonesUtil.s(link.getOaId());
        String ownerOa = oaOwnerId(biz, oaId);
        MtgOaItem logItem = toLogItem(pItem, oaId, ownerOa);
        String wxHash = fingerprint(pItem, pItem.getAttendeeWxIds());
        boolean cancelled = pItem.getStatus() != null && pItem.getStatus().intValue() == 1;
        if (isOaOrigin(link)) {
            // OA 创建的数据：平台侧改动（含取消 / 删除）都不反向写回 OA
            String old = OaonesUtil.s(link.getWxHash());
            if (old.length() == 0) {
                updateLinkSnapshot(link, null, null, wxHash, operator);   // 只建基线
                return OaonesConst.SYNC_OP_SKIP;
            }
            if (wxHash.equals(old)) {
                return OaonesConst.SYNC_OP_SKIP;   // 平台侧没改：不写流水
            }
            writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_SKIP,
                    (cancelled ? "平台侧已取消这条" + OaonesConst.mtgBizText(biz)
                            : "平台侧修改了这条" + OaonesConst.mtgBizText(biz))
                            + "，但该数据由 OA 创建" + OaonesConst.MTG_FOREIGN_HINT
                            + (cancelled ? "，不做反向处理（OA 侧记录保持不变）" : "，不做反向更新"),
                    operator);
            updateLinkSnapshot(link, null, null, wxHash, operator);
            return OaonesConst.SYNC_OP_SKIP;
        }
        if (!effectiveThird2Oa(tenantT2o, userPrefs.get(ownerOa))) {
            writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_SKIP,
                    "该 OA 记录创建人未选择「第三方→OA」或已设不同步，本次跳过", operator);
            return OaonesConst.SYNC_OP_SKIP;
        }
        int id = OaonesUtil.i(oaId, 0);
        if (id <= 0) {
            return OaonesConst.SYNC_OP_SKIP;
        }
        // OA 侧已经把这行删了（人工删 / 之前对账标记删除）：不写回，也不回删平台日程
        MtgOaItem mirror = getOaItem(biz, oaId);
        if (mirror == null || (mirror.getDeleted() != null && mirror.getDeleted().intValue() == 1)) {
            return OaonesConst.SYNC_OP_SKIP;
        }
        String oldWx = OaonesUtil.s(link.getWxHash());
        if (oldWx.length() > 0 && wxHash.equals(oldWx)) {
            return OaonesConst.SYNC_OP_SKIP;   // 平台侧没改：不调写库接口、也不写流水
        }
        if (cancelled) {
            // 平台侧把这条日程取消了：按“平台删了”处理，在 OA 标记删除
            Map<String, Object> p = OaonesParam.of("oaId", oaId);
            if (biz == OaonesConst.MTG_BIZ_SCHEDULE) {
                OaonesMyBatis.run(MtgSyncMapper.class, m -> m.markWorkPlanDeleted(p));
            } else {
                OaonesMyBatis.run(MtgSyncMapper.class, m -> m.markMeetingDeleted(p));
            }
            writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_DELETE,
                    "平台侧已取消这条" + OaonesConst.mtgBizText(biz) + "，已同步在 OA 标记删除"
                            + "（不物理删行）", operator);
            dropLink(link);
            return OaonesConst.SYNC_OP_DELETE;
        }
        MtgOaWrite w = buildWrite(pItem, ownerOa);
        w.setId(id);
        List<Integer> memberIds = oaMemberIds(cfgId, pItem, ownerOa);
        w.setMemberCount(memberIds.size());
        // 平台没返回参与人时 resourceId 留空（Mapper 里用 <if> 跳过，保留 OA 侧原来的参与人）
        w.setResourceId(pItem.getAttendeeWxIds().isEmpty() ? null : joinIds(memberIds));
        if (biz == OaonesConst.MTG_BIZ_SCHEDULE) {
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateWorkPlan(OaonesParam.of("e", w)));
        } else {
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.updateMeeting(OaonesParam.of("e", w)));
        }
        // 只有平台确实返回了参与人时才重写参与人，避免“取不到参与人（接口失败 / 个人日程）”把 OA 侧参与人清空
        if (!pItem.getAttendeeWxIds().isEmpty()) {
            replaceMembers(biz, w, memberIds);
        }
        // 平台侧内容已写进 OA，记下平台快照；OA 侧快照作废（下一轮推送对账只建基线，
        // 不会把“平台刚写进来的内容”误判成“OA 侧改过”）
        updateLinkSnapshot(link, null, "", wxHash, operator);
        writeLog(biz, type, cfgId, batchId, logItem, pItem.getWxId(), OaonesConst.SYNC_OP_UPDATE,
                "平台侧的" + OaonesConst.mtgBizText(biz) + "有更新，已更新 OA 数据", operator);
        return OaonesConst.SYNC_OP_UPDATE;
    }

    /** 该对象映射是否由 OA 创建（推送建立）：{@code source = 0}。 */
    private boolean isOaOrigin(MtgSyncLink link) {
        return link == null || link.getSource() == null
                || link.getSource().intValue() == OaonesConst.MTG_LINK_SOURCE_OA;
    }

    /** 在描述末尾补一句「非本平台创建，请在原平台修改」；已有则不重复追加。 */
    private String withForeignHint(String text) {
        String value = OaonesUtil.s(text);
        if (value.indexOf(OaonesConst.MTG_FOREIGN_HINT) >= 0) {
            return value;
        }
        return value.length() == 0 ? OaonesConst.MTG_FOREIGN_HINT
                : value + "\n" + OaonesConst.MTG_FOREIGN_HINT;
    }

    /** 平台事件 → 写入载体（标题 / 时间 / 地点 / 创建人所在分部部门）。 */
    private MtgOaWrite buildWrite(MtgPlatformItem pItem, String ownerOa) {
        MtgOaWrite w = new MtgOaWrite();
        w.setTitle(isBlank(pItem.getTitle()) ? "未命名" : pItem.getTitle());
        // 平台创建的数据同步到 OA 时，描述末尾补一句“非本平台创建，请在原平台修改”
        w.setContent(withForeignHint(pItem.getContent()));
        w.setLocation(OaonesUtil.s(pItem.getLocation()));
        String bt = OaonesUtil.s(pItem.getBeginTime());
        String et = OaonesUtil.s(pItem.getEndTime());
        w.setBeginDateTime(bt);
        w.setEndDateTime(et);
        w.setBeginDate(bt.length() >= 10 ? bt.substring(0, 10) : bt);
        w.setBeginTime(bt.length() >= 19 ? bt.substring(11, 19) : "");
        w.setEndDate(et.length() >= 10 ? et.substring(0, 10) : et);
        w.setEndTime(et.length() >= 19 ? et.substring(11, 19) : "");
        int ownerId = OaonesUtil.i(ownerOa, 0);
        if (ownerId > 0) {
            w.setOwnerId(ownerId);
            try {
                OaAccountRow row = OaonesMyBatis.call(HrmMapper.class, m -> m.getResourceAccount(ownerId));
                if (row != null) {
                    w.setSubId(row.getSubcompanyId());
                    w.setDeptId(row.getDepartmentId());
                }
            } catch (Throwable ignore) {
                // 取不到分部 / 部门不影响写入
            }
        }
        return w;
    }

    /** 平台参与人 → OA 人员ID（创建人在前；未绑定的平台成员忽略）。 */
    private List<Integer> oaMemberIds(int cfgId, MtgPlatformItem pItem, String ownerOa) {
        List<Integer> ids = new ArrayList<Integer>();
        Set<String> seen = new HashSet<String>();
        if (!isBlank(ownerOa) && seen.add(ownerOa)) {
            ids.add(OaonesUtil.i(ownerOa, 0));
        }
        List<String> wxIds = new ArrayList<String>();
        for (String w : pItem.getAttendeeWxIds()) {
            if (!isBlank(w) && !seen.contains(w) && !wxIds.contains(w)) {
                wxIds.add(w);
            }
        }
        if (!wxIds.isEmpty()) {
            Map<String, Object> p = OaonesParam.of();
            p.put("cfgId", cfgId);
            p.put("wxIds", wxIds);
            List<Map<String, Object>> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.mapOaUsersByWx(p));
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    int id = OaonesUtil.i(mapValue(row, "oa_user_id"), 0);
                    if (id > 0 && seen.add(String.valueOf(id))) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    /** 重写参与人：先删旧行再逐个写（会议 Meeting_Member2 / 日程 workplanMember）。 */
    private void replaceMembers(int biz, MtgOaWrite w, List<Integer> memberIds) {
        if (w.getId() == null || w.getId() <= 0) {
            return;
        }
        Map<String, Object> del = OaonesParam.of();
        if (biz == OaonesConst.MTG_BIZ_SCHEDULE) {
            del.put("workplanId", w.getId());
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.deleteWorkplanMembers(del));
        } else {
            del.put("meetingId", w.getId());
            OaonesMyBatis.run(MtgSyncMapper.class, m -> m.deleteMeetingMembers(del));
        }
        for (Integer mid : memberIds) {
            if (mid == null || mid <= 0) {
                continue;
            }
            Map<String, Object> row = OaonesParam.of();
            row.put("memberId", mid);
            if (biz == OaonesConst.MTG_BIZ_SCHEDULE) {
                row.put("workplanId", w.getId());
                row.put("beginDate", w.getBeginDate());
                row.put("beginTime", w.getBeginTime());
                row.put("endDate", w.getEndDate());
                row.put("endTime", w.getEndTime());
                OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertWorkplanMember(row));
            } else {
                row.put("meetingId", w.getId());
                OaonesMyBatis.run(MtgSyncMapper.class, m -> m.insertMeetingMember(row));
            }
        }
    }

    /** 某个 OA 会议 / 日程的创建人（OA 人员ID）。 */
    private String oaOwnerId(int biz, String oaId) {
        if (isBlank(oaId)) {
            return "";
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("oaId", oaId);
        return OaonesUtil.s(OaonesMyBatis.call(MtgSyncMapper.class, m -> m.getOaOwnerId(p)));
    }

    /** 平台成员ID → OA 人员ID（未绑定返回空串）。 */
    private String mapOaUserByWx(int cfgId, String wxId) {
        if (isBlank(wxId)) {
            return "";
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("cfgId", cfgId);
        p.put("wxId", wxId);
        return OaonesUtil.s(OaonesMyBatis.call(MtgSyncMapper.class, m -> m.mapOaUserByWx(p)));
    }

    /** 该租户已绑定的平台成员ID（拉取的日历候选，最多 limit 个）。 */
    private List<String> boundPlatformUserIds(int cfgId, int limit) {
        Map<String, Object> p = OaonesParam.of();
        p.put("cfgId", cfgId);
        List<String> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listBoundPlatformUserIds(p));
        List<String> out = new ArrayList<String>();
        if (rows != null) {
            for (String id : rows) {
                if (!isBlank(id)) {
                    out.add(id.trim());
                    if (limit > 0 && out.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return out;
    }

    // ==================================================================
    // 「绑定应用」：逐租户指定同步用哪个“应用绑定”记录（应用要有日程权限）
    // ==================================================================

    /** 该平台（type）指定租户集合下的“绑定应用”候选，按租户分组（页面每个租户一行下拉）。 */
    private Map<Integer, JSONArray> appOptionsByCfg(int type, List<Integer> cfgIds) {
        Map<Integer, JSONArray> map = new HashMap<Integer, JSONArray>();
        if (cfgIds == null || cfgIds.isEmpty()) {
            return map;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("type", type);
        p.put("cfgIds", new ArrayList<Integer>(new LinkedHashSet<Integer>(cfgIds)));
        List<MtgAppOption> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listAppOptions(p));
        if (rows == null) {
            return map;
        }
        for (MtgAppOption row : rows) {
            if (row.getCfgId() == null) {
                continue;
            }
            JSONArray list = map.get(row.getCfgId());
            if (list == null) {
                list = new JSONArray();
                map.put(row.getCfgId(), list);
            }
            list.add(appOptionJson(row));
        }
        return map;
    }

    /** 候选应用 → 页面 JSON（**不含 Secret**；只给“Secret 是否就绪 / 是否靠租户配置兜底”）。 */
    private JSONObject appOptionJson(MtgAppOption row) {
        boolean appSecret = row.getSecretConfigured() != null && row.getSecretConfigured().intValue() == 1;
        boolean cfgSecret = row.getCfgSecretConfigured() != null
                && row.getCfgSecretConfigured().intValue() == 1;
        JSONObject json = new JSONObject();
        json.put("id", row.getId() == null ? "" : String.valueOf(row.getId()));
        json.put("app_name", OaonesUtil.s(row.getAppName()));
        json.put("agent_id", OaonesUtil.s(row.getAgentId()));
        json.put("bind_state", row.getYx() == null ? 0 : row.getYx());
        json.put("enable", row.getYx() != null && row.getYx() == OaonesConst.APP_BIND_STATE_VALID);
        json.put("secret_ready", appSecret || cfgSecret ? "1" : "0");
        // 应用没单独配 Secret 时由租户配置的 Secret 兜底（与消息中心“应用凭据优先、租户兜底”同一口径）
        json.put("secret_from_cfg", !appSecret && cfgSecret ? "1" : "0");
        return json;
    }

    /** 在候选里按应用绑定记录ID 找一行（找不到返回 null，页面据此提示“应用已删除，请重选”）。 */
    private JSONObject findAppOption(JSONArray apps, int appId) {
        if (apps == null || appId <= 0) {
            return null;
        }
        for (int i = 0; i < apps.size(); i++) {
            JSONObject one = apps.getJSONObject(i);
            if (one != null && String.valueOf(appId).equals(one.getString("id"))) {
                return one;
            }
        }
        return null;
    }

    /** 按应用绑定记录ID 取一条（带 Secret / 租户凭据，供后端取用）；不存在返回 null。 */
    private MtgAppOption app(int type, int appId) {
        if (appId <= 0) {
            return null;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("type", type);
        p.put("appId", appId);
        return OaonesMyBatis.call(MtgSyncMapper.class, m -> m.getAppById(p));
    }

    /**
     * 本次同步使用的应用：按设置的 {@code app_id} 取，且必须仍属于本租户；
     * 未指定（0）/ 已被删除 / 被改到别的租户时返回 null = 回退租户配置的 Secret + AgentId。
     */
    private MtgAppOption syncApp(int type, int cfgId, Integer appId) {
        MtgAppOption app = app(type, appId == null ? 0 : appId.intValue());
        if (app == null) {
            return null;
        }
        if (app.getCfgId() == null || app.getCfgId().intValue() != cfgId) {
            return null;
        }
        return app;
    }

    /** 有效 Secret：应用自己的 Secret 优先（日程接口要用有日程权限的应用），为空时回退租户配置。 */
    private String secretOf(MtgAppOption app, QywxConfig tenant) {
        if (app != null && !isBlank(app.getAppSecret())) {
            return OaonesUtil.s(app.getAppSecret());
        }
        return OaonesUtil.s(tenant.getCorpSecret());
    }

    /** 有效应用标识：应用绑定的 AgentId 优先，为空时回退租户配置。 */
    private String agentIdOf(MtgAppOption app, QywxConfig tenant) {
        if (app != null && !isBlank(app.getAgentId())) {
            return OaonesUtil.s(app.getAgentId());
        }
        return OaonesUtil.s(tenant.getAgentId());
    }

    /** 保存 / 校验前的“应用属于本租户”校验（appId = 0 表示未指定，直接放过）。 */
    private void assertAppOfCfg(int type, int cfgId, int appId) {
        if (appId <= 0) {
            return;
        }
        MtgAppOption app = app(type, appId);
        if (app == null) {
            throw new RuntimeException("应用不存在（可能已在「应用绑定」页删除，或不属于当前平台），请重新选择");
        }
        if (app.getCfgId() == null || app.getCfgId().intValue() != cfgId) {
            throw new RuntimeException("该应用不属于本租户，请重新选择");
        }
    }

    /** 换 access_token（企微 / 钉钉 / 飞书各一套接口，凭据不同）。 */
    private String accessToken(int type, String corpId, String secret) throws Exception {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new DingTalkApi(corpId, secret).getAccessToken();
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new FeishuApi(corpId, secret).getAccessToken();
        }
        return new WeComApi(corpId, secret).getAccessToken();
    }

    /**
     * 「校验应用」的第 2 步：直接用该应用的凭据调一次平台**读日程**接口（只读一条），
     * 用于判断该应用是否真的有日程权限。
     *
     * <p>这里刻意不复用拉取适配器：拉取对“单个日历读失败”是跳过（不抛异常），
     * 权限不对时也只会返回空列表，看不出问题；本方法把平台返回的 errcode / code 报出来。</p>
     *
     * @return {@code {api, count}}；接口返回非 0 时抛异常（消息即平台原因）
     */
    private JSONObject probeSchedule(int type, String corpId, String secret, String member)
            throws Exception {
        String token = accessToken(type, corpId, secret);
        JSONObject out = new JSONObject();
        OaonesApiLog.httpContext(type, corpId);
        try {
            long now = System.currentTimeMillis();
            if (type == OaonesConst.TYPE_DINGTALK) {
                String url = OaonesProps.ddApiBase() + "/topapi/calendar/v2/event/list?access_token="
                        + OaonesUtil.urlEncode(token);
                JSONObject body = new JSONObject();
                body.put("unionid", member);
                body.put("calendar_id", member);
                body.put("time_min", now);
                body.put("time_max", now + 86400000L);
                body.put("limit", 1);
                body.put("offset", 0);
                JSONObject resp = OaonesHttp.postJson(url, body);
                checkPlatformErrcode(resp, "钉钉");
                JSONObject result = resp.getJSONObject("result");
                JSONArray list = result == null ? null : result.getJSONArray("events");
                if (list == null && result != null) {
                    list = result.getJSONArray("items");
                }
                out.put("api", "POST /topapi/calendar/v2/event/list");
                out.put("count", list == null ? 0 : list.size());
                return out;
            }
            if (type == OaonesConst.TYPE_FEISHU) {
                long sec = now / 1000L;
                String url = OaonesProps.fsApiBase() + "/open-apis/calendar/v4/calendars/primary/events"
                        + "?start_time=" + sec + "&end_time=" + (sec + 86400L) + "&page_size=1";
                Map<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "Bearer " + token);
                JSONObject resp = OaonesHttp.getJson(url, headers);
                int code = resp.getIntValue("code");
                if (code != 0) {
                    throw new RuntimeException("飞书返回 code=" + code + "：" + OaonesUtil.s(resp.getString("msg")));
                }
                JSONObject data = resp.getJSONObject("data");
                JSONArray items = data == null ? null : data.getJSONArray("items");
                out.put("api", "GET /open-apis/calendar/v4/calendars/primary/events");
                out.put("count", items == null ? 0 : items.size());
                return out;
            }
            String url = OaonesProps.wxApiBase() + "/cgi-bin/oa/schedule/get_by_calendar?access_token="
                    + OaonesUtil.urlEncode(token);
            JSONObject body = new JSONObject();
            body.put("cal_id", member);
            body.put("offset", 0);
            body.put("limit", 1);
            JSONObject resp = OaonesHttp.postJson(url, body);
            checkPlatformErrcode(resp, "企业微信");
            JSONArray list = resp.getJSONArray("schedule_list");
            out.put("api", "POST /cgi-bin/oa/schedule/get_by_calendar");
            out.put("count", list == null ? 0 : list.size());
            return out;
        } finally {
            OaonesApiLog.clearHttpContext();
        }
    }

    /** 平台返回非 0 时抛异常（消息里带 errcode / errmsg，便于页面直接展示原因）。 */
    private void checkPlatformErrcode(JSONObject resp, String platform) {
        int code = resp.getIntValue("errcode");
        if (code != 0) {
            throw new RuntimeException(platform + "返回 errcode=" + code + "："
                    + OaonesUtil.s(resp.getString("errmsg")));
        }
    }

    /** 异常消息文本（空消息给个兜底文案）。 */
    private String msgOf(Exception e) {
        String msg = OaonesUtil.s(e == null ? null : e.getMessage());
        return msg.length() == 0 ? "接口未返回原因" : msg;
    }

    /** 该「业务 + 平台 + 租户」下由「第三方→OA」拉取建立的 OA 对象ID 集合（推送时跳过）。 */
    private Set<String> platformOriginOaIds(int biz, int type, int cfgId) {
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        List<String> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listPlatformOriginOaIds(p));
        Set<String> set = new HashSet<String>();
        if (rows != null) {
            for (String id : rows) {
                if (!isBlank(id)) {
                    set.add(id.trim());
                }
            }
        }
        return set;
    }

    /**
     * 「只同步一次」：本次扫到的这批 OA 对象里**已经有同步记录**的对象ID集合——
     * <b>不限动作</b>（新增 / 更新 / 跳过 / 失败都算“已经处理过”）。
     *
     * <p>只查本租户本平台、且 oa_id 落在本次候选集里的记录（走
     * {@code IDX_OAONES_MTG_SYNC_LOG_OA}），所以是一次有界的 IN 查询，
     * 不会随历史记录无限增长。</p>
     */
    private Set<String> attemptedOaIds(int biz, int type, int cfgId, List<MtgOaItem> items) {
        Set<String> set = new HashSet<String>();
        Set<String> ids = new LinkedHashSet<String>();
        for (MtgOaItem item : items) {
            String oaId = OaonesUtil.s(item.getOaId()).trim();
            if (oaId.length() > 0) {
                ids.add(oaId);
            }
        }
        if (ids.isEmpty()) {
            return set;
        }
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        p.put("oaIds", new ArrayList<String>(ids));
        List<String> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listAttemptedOaIds(p));
        if (rows != null) {
            for (String id : rows) {
                if (!isBlank(id)) {
                    set.add(id.trim());
                }
            }
        }
        return set;
    }

    private MtgPullAdapter pullAdapter(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new DingTalkScheduleApi();
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new FeishuScheduleApi();
        }
        return new WeComScheduleApi();
    }

    /**
     * 个人在「第三方→OA」上的生效方向：租户方向是上限；
     * 个人「不同步」（enabled = 0）双向都不同步；个人「指定」时取交集；「遵循系统设置」时取租户方向。
     */
    private boolean effectiveThird2Oa(boolean tenantThird2Oa, MtgSyncUser pref) {
        if (!tenantThird2Oa) {
            return false;
        }
        if (pref != null && pref.getEnabled() != null && pref.getEnabled().intValue() == 0) {
            return false;
        }
        if (pref != null && pref.getDirMode() != null
                && pref.getDirMode().intValue() == OaonesConst.MTG_DIR_MODE_CUSTOM) {
            return dirOn(pref.getDirThird2Oa());
        }
        return true;
    }

    /** 造一条映射行（source 0 推送建立 / 1 拉取建立）。 */
    private MtgSyncLink newLink(int biz, int type, int cfgId, String oaId, String wxId,
                                int source, String operatorId) {
        MtgSyncLink l = new MtgSyncLink();
        l.setBiz(biz);
        l.setType(type);
        l.setCfgid(cfgId);
        l.setOaId(OaonesUtil.s(oaId));
        l.setWxId(OaonesUtil.s(wxId));
        l.setSource(source);
        l.setLastPullTime(OaonesUtil.now());
        l.setRemark("");
        l.setModedatacreater(OaonesUtil.i(operatorId, 0));
        l.setModedatacreatedate(OaonesUtil.nowDate());
        l.setModedatacreatetime(OaonesUtil.nowTime());
        l.setModedatamodifier(OaonesUtil.i(operatorId, 0));
        l.setModedatamodifydatetime(OaonesUtil.now());
        return l;
    }

    private MtgOaItem toLogItem(MtgPlatformItem pItem, String oaId, String ownerOa) {
        MtgOaItem it = new MtgOaItem();
        it.setOaId(OaonesUtil.s(oaId));
        it.setTitle(pItem.getTitle());
        it.setBeginTime(pItem.getBeginTime());
        it.setEndTime(pItem.getEndTime());
        it.setOwnerId(OaonesUtil.s(ownerOa));
        it.setLocation(pItem.getLocation());
        return it;
    }

    private String joinIds(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** MyBatis 返回的 Map 键大小写不固定，按不区分大小写取值。 */
    private String mapValue(Map<String, Object> row, String key) {
        if (row == null) {
            return "";
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue() == null ? "" : String.valueOf(e.getValue());
            }
        }
        return "";
    }

    private void putCounts(JSONObject summary, int scanned, int created, int updated,
                           int skipped, int failed, int deleted) {
        summary.put("scanned", scanned);
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        // 删除：平台删除 / 取消后在 OA 侧标记删除的条数（订阅回调与删除对账都会计入）
        summary.put("deleted", deleted);
    }

    private MtgPushAdapter adapter(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new DingTalkScheduleApi();
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new FeishuScheduleApi();
        }
        return new WeComScheduleApi();
    }

    private MtgSyncCfg getCfg(int biz, int type, int cfgId) {
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        return OaonesMyBatis.call(MtgSyncMapper.class, m -> m.getCfg(p));
    }

    private List<MtgSyncCfg> queryCfgs(int biz, int type, List<Integer> cfgIds) {
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        if (cfgIds != null) {
            p.put("cfgIds", cfgIds);
        }
        List<MtgSyncCfg> rows = OaonesMyBatis.call(MtgSyncMapper.class, m -> m.listCfg(p));
        return rows == null ? new ArrayList<MtgSyncCfg>() : rows;
    }

    private JSONObject getSettingsRow(int biz, int type, int cfgId, String operatorId) {
        JSONArray rows = listSettings(biz, type, operatorId);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (String.valueOf(cfgId).equals(row.getString("cfg_id"))) {
                return row;
            }
        }
        return new JSONObject();
    }

    /** 租户维护人校验（与 BaseController#assertCfgRight 同一口径）。 */
    private void assertCfgRight(int cfgId, String operatorId) {
        if (cfgId <= 0) {
            return;
        }
        if (!configService.canMaintain(String.valueOf(cfgId), operatorId)) {
            throw new RuntimeException("您不是该企业微信租户的维护人，无权查看或设置该租户");
        }
    }

    private String lastSuccessWxId(int biz, int type, int cfgId, String oaId) {
        Map<String, Object> p = OaonesParam.of();
        p.put("biz", biz);
        p.put("type", type);
        p.put("cfgId", cfgId);
        p.put("oaId", oaId);
        return OaonesMyBatis.call(MtgSyncMapper.class, m -> m.lastSuccessWxId(p));
    }

    private List<QywxConfig> tenants(int type) {
        List<QywxConfig> rows = OaonesMyBatis.call(QywxConfigMapper.class,
                m -> m.listByType(OaonesProps.cfgTable(), type));
        return rows == null ? new ArrayList<QywxConfig>() : rows;
    }

    private QywxConfig tenant(int type, int cfgId) {
        return OaonesMyBatis.call(QywxConfigMapper.class,
                m -> m.getById(OaonesProps.cfgTable(), cfgId));
    }

    /** 当前登录人在该平台可维护的租户集合（用于不带租户查同步记录）。 */
    private List<Integer> maintainableCfgIds(int type, String operatorId) {
        List<Integer> ids = new ArrayList<Integer>();
        for (QywxConfig tenant : tenants(type)) {
            if (tenant.getId() != null && configService.canMaintain(String.valueOf(tenant.getId()), operatorId)) {
                ids.add(tenant.getId());
            }
        }
        return ids;
    }

    private Map<String, String> subNameMap() {
        Map<String, String> map = new HashMap<String, String>();
        try {
            JSONArray subs = new OaServiceImpl().listSubCompaniesFromCache();
            for (int i = 0; i < subs.size(); i++) {
                JSONObject sub = subs.getJSONObject(i);
                map.put(OaonesUtil.s(sub.getString("id")), OaonesUtil.s(sub.getString("name")));
            }
        } catch (Throwable t) {
            OaonesLog.error("[mtgSync] 读取 OA 分部缓存失败（记录里的分部名将留空）", t);
        }
        return map;
    }

    private JSONObject toJson(MtgSyncLog row) {
        JSONObject json = new JSONObject();
        json.put("id", row.getId());
        json.put("biz", row.getBiz());
        json.put("type", row.getType());
        json.put("cfg_id", row.getCfgid());
        json.put("cfg_name", OaonesUtil.s(row.getCfgName()));
        json.put("batch_id", OaonesUtil.s(row.getBatchId()));
        json.put("oa_id", OaonesUtil.s(row.getOaId()));
        json.put("oa_title", OaonesUtil.s(row.getOaTitle()));
        json.put("oa_sub_id", OaonesUtil.s(row.getOaSubId()));
        json.put("oa_sub_name", OaonesUtil.s(row.getOaSubName()));
        json.put("oa_dept_id", OaonesUtil.s(row.getOaDeptId()));
        json.put("oa_dept_name", OaonesUtil.s(row.getOaDeptName()));
        json.put("begin_time", OaonesUtil.s(row.getBeginTime()));
        json.put("end_time", OaonesUtil.s(row.getEndTime()));
        json.put("wx_id", OaonesUtil.s(row.getWxId()));
        json.put("op", row.getOp());
        json.put("op_text", OaonesConst.syncOpText(row.getOp() == null ? -1 : row.getOp()));
        json.put("try_count", row.getTryCount() == null ? 1 : row.getTryCount());
        json.put("message", OaonesUtil.s(row.getMessage()));
        json.put("sync_time", OaonesUtil.s(row.getModedatacreatedate()) + " " + OaonesUtil.s(row.getModedatacreatetime()));
        return json;
    }

    private int readLimit(JSONObject params) {
        int limit = params.getIntValue("limit");
        if (limit <= 0) {
            limit = 1000;
        }
        return Math.min(limit, 5000);
    }

    /**
     * 「同步记录」时间筛选取值 → [日期, 时间]（走 {@link OaonesUtil#splitDateTime}）。
     *
     * <p>页面（{@code js/mtgSync.js}）给的是**一个日期时间框**的完整值（{@code yyyy-MM-dd HH:mm:ss}，
     * 精确到秒），参数名沿用 {@code from_time} / {@code to_time}；
     * 同时兼容更早的“日期 + 可选时间”两个参数（{@code from_date} / {@code to_date}，
     * 供脚本沿用老口径调用）：带了 {@code *_date} 就按“日期 [+ 空格 + 时间]”拼；
     * 只给日期时，起点补 {@code 00:00:00}、终点补 {@code 23:59:59}，正好覆盖整天。</p>
     */
    private String[] logRange(JSONObject params, String side, String defaultTime) {
        String date = OaonesUtil.s(params.getString(side + "_date"));
        String time = OaonesUtil.s(params.getString(side + "_time"));
        if (date.length() > 0) {
            return OaonesUtil.splitDateTime(time.length() > 0 ? (date + " " + time) : date, defaultTime);
        }
        return OaonesUtil.splitDateTime(time, defaultTime);
    }

    private String cut(String value, int max) {
        String v = OaonesUtil.s(value);
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().length() == 0;
    }
}
