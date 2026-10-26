package com.customization.oaones.appsyn.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesCronExpr;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.SyncPlanMapper;
import com.customization.oaones.appsyn.dao.OrgMapMapper;
import com.customization.oaones.appsyn.entity.OrgMap;
import com.customization.oaones.appsyn.entity.SyncPlanCfg;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.SyncPlanService;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同步中心“同步设置”服务实现（三个连接器共用，DAO 层为 {@link SyncPlanMapper}）。
 *
 * <p>同步方向的执行仍落在各连接器自己的同步服务上（{@code service.impl.{wecom,dingtalk,feishu}}），
 * 本类只负责：设置行的读写与校验、计划任务预测（轮询间隔（分钟）/ 高级 cron 两种方式）、
 * 按到点触发（计划任务），
 * 以及把“成员没有找到部门”的口径交给同步引擎（引擎按 {@link #get(int, String, int)} 读取）。</p>
 */
public class SyncPlanServiceImpl implements SyncPlanService {

    /** 计划任务一次最多执行的设置条数。 */
    private static final int DUE_LIMIT = 20;
    /** 页面 cron 预测的默认条数（需求：预测后面 10 次执行时间）。 */
    private static final int PREVIEW_COUNT = 10;

    private String table() {
        return OaonesProps.syncPlanTable();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    @Override
    public JSONArray list(String type, String cfgId, String direction, List<Integer> allowedCfgIds) {
        OaonesLog.step("查询同步设置 type=" + type + " cfgId=" + cfgId + " direction=" + direction
                + " 可见租户=" + (allowedCfgIds == null ? "全部" : allowedCfgIds.size()));
        JSONArray result = new JSONArray();
        // 结果里始终带上“全局默认配置”（cfgid = 0）：租户视图因此同时看到“全局默认 + 当前租户”
        List<Integer> scopeIds = scopeCfgIds(cfgId, allowedCfgIds);
        List<SyncPlanCfg> rows = OaonesMyBatis.call(SyncPlanMapper.class,
                mapper -> mapper.list(table(), typeValue(type),
                        scopeIds == null ? OaonesUtil.integer(cfgId) : null,
                        directionFilter(direction), scopeIds));
        if (rows == null) {
            return result;
        }
        // 列表的「源组织 / 目标组织」两栏（一行一个组织，多组织按行显示）：名称按设置里保存的 ID 现算，
        // 所以组织改名后列表跟着变。OA 侧走 E9 组织缓存（不查库），平台侧走该租户的部门快照——
        // 后者按租户缓存，一次列表最多每租户查一次。
        Map<String, JSONObject> subById = indexById(oaService().listSubCompaniesFromCache());
        Map<String, JSONObject> deptById = indexById(oaService().listDepartmentsFromCache());
        Map<String, Map<String, String>> wxNameCache = new HashMap<String, Map<String, String>>();
        for (SyncPlanCfg row : rows) {
            JSONObject json = toJson(row);
            fillScopeSides(json, row, subById, deptById, wxNameCache);
            result.add(json);
        }
        return result;
    }

    @Override
    public JSONObject get(int type, String cfgId, int direction) {
        // 注意：cfgid = 0 也要能查到（“全局默认配置”是 effective() 的兜底口径），
        // 因此这里直接按 (type, cfgid, direction) 查 Mapper，不排除 cfgid = 0。
        String cfgText = OaonesUtil.s(cfgId).trim();
        int cfg = OaonesUtil.i(cfgText, 0);
        if (cfgText.length() == 0 || cfg < 0) {
            return null;
        }
        SyncPlanCfg row = OaonesMyBatis.call(SyncPlanMapper.class,
                mapper -> mapper.get(table(), type, cfg, direction));
        return row == null ? null : toJson(row);
    }

    @Override
    public JSONObject getById(String id) {
        int ruleId = OaonesUtil.i(id, 0);
        if (ruleId <= 0) {
            return null;
        }
        SyncPlanCfg row = OaonesMyBatis.call(SyncPlanMapper.class,
                mapper -> mapper.getById(table(), ruleId));
        return row == null ? null : toJson(row);
    }

    /**
     * 新增规则失败时的提示归一：老库还没删“一个租户 + 一个方向只能一条规则”的唯一键时，
     * 直接告诉用户先执行哪一条升级脚本（否则页面上只会看到数据库抛出的唯一键冲突原文）。
     */
    private String insertRuleHint(Throwable error) {
        String first = "";
        for (Throwable t = error; t != null; t = t.getCause()) {
            String msg = OaonesUtil.s(t.getMessage());
            if (msg.indexOf("UK_OAONES_SYNC_PLAN") >= 0) {
                return "数据库上还留着“一个租户 + 一个方向只能有一条规则”的唯一键 UK_OAONES_SYNC_PLAN："
                        + "请先执行升级脚本 sql/old/sql_legacy_upgrade/"
                        + "20260930_020_alter_oaones_sync_plan_multi_rule_sqlserver.sql"
                        + "（MySQL 用 …_019_…、Oracle 用 …_021_… 那个文件）把它改成普通索引，再回来保存。";
            }
            if (first.length() == 0) {
                first = msg;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return first.length() == 0 ? "新增同步规则失败，请查看 E9 日志" : first;
    }

    @Override
    public JSONObject effective(int type, String cfgId, int direction) {
        JSONObject own = get(type, cfgId, direction);
        if (own != null) {
            return own;
        }
        // 该租户没单独配置：退回“全局默认配置”（cfgid = 0）
        return get(type, GLOBAL_CFG_ID, direction);
    }

    /** 列表的租户范围：带租户时 = 全局默认 + 该租户；不带租户时 = 可见租户 + 全局默认。 */
    private List<Integer> scopeCfgIds(String cfgId, List<Integer> allowedCfgIds) {
        int cfg = OaonesUtil.i(cfgId, -1);
        if (cfg >= 0) {
            // 带了具体租户（或明确要“全局默认”）= 只看这两档，不再列其它租户
            String text = OaonesUtil.s(cfgId).trim();
            if (text.length() > 0 || cfg == 0) {
                List<Integer> ids = new ArrayList<Integer>();
                ids.add(Integer.valueOf(GLOBAL_CFG_ID));
                if (cfg > 0) {
                    ids.add(Integer.valueOf(cfg));
                }
                return ids;
            }
        }
        if (allowedCfgIds == null) {
            return null;
        }
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(Integer.valueOf(GLOBAL_CFG_ID));
        ids.addAll(allowedCfgIds);
        return ids;
    }

    @Override
    public JSONObject save(JSONObject params, String operatorId) {
        int type = platformType(OaonesUtil.s(params.getString("type")));
        int cfgId = OaonesUtil.i(OaonesUtil.s(params.getString("cfg_id")), 0);
        // cfg_id = 0 表示“全局默认配置”（作用于该平台所有未单独配置的租户），由入口（控制器）校验权限
        if (cfgId < 0) {
            throw new RuntimeException("请先选择需要设置的租户");
        }
        int direction = directionValue(OaonesUtil.s(params.getString("direction")));
        if (direction < 0) {
            throw new RuntimeException("同步方向参数不正确（0 = OA → 平台，1 = 平台 → OA）");
        }
        int enabled = OaonesUtil.i(OaonesUtil.s(params.getString("enabled")), 1) == 0 ? 0 : 1;
        String cron = OaonesCronExpr.normalize(OaonesUtil.s(params.getString("cron")));
        if (cron.length() > 0 && !OaonesCronExpr.valid(cron)) {
            throw new RuntimeException("cron 表达式不正确，请按 Quartz 口径填写“秒 分 时 日 月 周”"
                    + "（例：0 0 2 * * ? 表示每天 02:00:00 执行）");
        }
        // 计划任务有两种方式（见第一节第 24 条）：轮询间隔（分钟）与高级模式（cron 表达式）。
        // 两种方式的取值**一起保存**（页面来回切不丢值），执行时按 plan_mode 取用其中一套；
        // 启用时当前方式那一套必须配出可执行的时间。
        String planModeText = OaonesUtil.s(params.getString("plan_mode")).trim();
        int planMode = planModeOf(planModeText.length() == 0 ? null : OaonesUtil.integer(planModeText), cron);
        String intervalText = OaonesUtil.s(params.getString("interval_min")).trim();
        int intervalMin = intervalText.length() == 0 ? 0 : checkInterval(intervalText);
        if (planMode == OaonesConst.SYNC_PLAN_MODE_CRON) {
            if (enabled == 1 && cron.length() == 0) {
                throw new RuntimeException("计划任务选的是“高级模式（cron）”：启用时请填写 cron 表达式"
                        + "（要按分钟轮询请把“同步方式”改回“轮询间隔（分钟）”）");
            }
        } else if (enabled == 1 && intervalMin == 0) {
            throw new RuntimeException("启用计划任务必须填写轮询间隔（分钟）："
                    + OaonesConst.SYNC_PLAN_MIN_MINUTES + " ~ " + OaonesConst.SYNC_PLAN_MAX_MINUTES
                    + " 分钟（例：30 = 每 30 分钟同步一次）；要按 cron 执行请把“同步方式”切到“高级模式（cron）”");
        }
        if (intervalMin <= 0) {
            // 没填 / 停用时也把间隔存成默认值（切回“轮询间隔”方式即有一个可用的值）
            intervalMin = OaonesConst.SYNC_PLAN_DEF_MINUTES;
        }
        // 两个方向统一三种口径：0 不同步 / 1 按源部门层级新增 / 2 使用默认部门。
        int policy = OaonesUtil.i(OaonesUtil.s(params.getString("nodept_policy")),
                OaonesConst.SYNC_NODEPT_SKIP);
        if (policy != OaonesConst.SYNC_NODEPT_CREATE
                && policy != OaonesConst.SYNC_NODEPT_DEFAULT_DEPT) {
            policy = OaonesConst.SYNC_NODEPT_SKIP;
        }
        String defaultDeptId = OaonesUtil.s(params.getString("default_dept_id")).trim();
        String defaultDeptName = "";
        if (policy != OaonesConst.SYNC_NODEPT_DEFAULT_DEPT) {
            defaultDeptId = "";
        } else if (OaonesConst.SYNC_DEFAULT_TENANT.equals(defaultDeptId)) {
            defaultDeptName = "租户本身";
        } else if (direction == OaonesConst.DIR_WX_TO_OA) {
            if (defaultDeptId.length() == 0) {
                throw new RuntimeException("请选择默认 OA 部门，或选择直接落到租户本身");
            }
            JSONObject dept = oaService().getDepartment(defaultDeptId);
            if (dept == null) {
                throw new RuntimeException("指定的默认部门不存在（ID " + defaultDeptId + "），请重新选择");
            }
            defaultDeptName = OaonesUtil.s(dept.getString("name"));
        } else {
            if (defaultDeptId.length() == 0) {
                throw new RuntimeException("请选择默认平台部门，或选择直接落到租户本身");
            }
            Map<String, String> platformDepts = wxDeptNamesOf(type, String.valueOf(cfgId));
            String deptName = platformDepts.get(defaultDeptId);
            if (deptName == null) {
                throw new RuntimeException("指定的默认平台部门不存在（ID " + defaultDeptId
                        + "），请先更新部门快照或重新选择");
            }
            defaultDeptName = deptName;
        }
        /* ---------------- 同步范围（本轮新增） ----------------
           像页面上的“同步方向 / 同步范围”一样设置同步范围：计划任务与“立即执行一次”
           每次执行都按这条映射关系（OA 分部/部门 ⇄ 平台部门）同步；
           scope_set = 0 表示不限制范围（沿用历史的全量同步，兼容历史行与“全局默认配置”）。 */
        String scopeCfgId = String.valueOf(cfgId);
        int scopeSet = OaonesUtil.i(OaonesUtil.s(params.getString("scope_set")), 0) == 1 ? 1 : 0;
        String oaSubIds = csv(params.getString("oa_sub_ids"));
        String oaDeptIds = csv(params.getString("oa_dept_ids"));
        String wxDeptId = OaonesUtil.s(params.getString("wx_dept_id")).trim();
        boolean useScope = scopeSet == 1;
        if (useScope && direction == OaonesConst.DIR_WX_TO_OA && cfgId <= 0) {
            throw new RuntimeException("“全局默认配置”作用于多个租户，无法指定平台侧来源部门；"
                    + "请选择具体租户后再设置范围（全局默认配置按全量同步执行）");
        }
        // 同步范围只选“源侧”：A 只保存 OA 分部/部门；B 只保存平台部门。
        if (direction == OaonesConst.DIR_OA_TO_WX) {
            wxDeptId = "";
            oaDeptIds = csv(oaDeptIds);
        } else {
            oaSubIds = "";
            oaDeptIds = "";
            wxDeptId = csv(wxDeptId);
        }
        String scopeText = "";
        if (useScope) {
            // 范围校验（与页面上“按范围同步”同一口径，避免保存出一条执行时必然失败的设置）
            if (direction == OaonesConst.DIR_OA_TO_WX
                    && oaSubIds.length() == 0 && oaDeptIds.length() == 0) {
                throw new RuntimeException("请先选择同步范围：OA → 平台 方向至少要选一个 OA 分部或 OA 部门"
                        + "（选中即含其下级；人员按现有部门映射写入）");
            }
            if (direction == OaonesConst.DIR_WX_TO_OA) {
                if (cfgId > 0 && wxDeptId.length() == 0) {
                    throw new RuntimeException("请先选择平台侧的同步范围：平台 → OA 方向"
                            + "至少要选一个平台部门（选中即含其下级；人员按现有部门映射写入 OA）");
                }
            }
            if (cfgId > 0) {
                // 解析一次范围：分部/部门不存在或已封存时直接报错，同时生成页面展示用的范围文案
                scopeText = scopeIndexOf(type, scopeCfgId, direction, oaSubIds, oaDeptIds, wxDeptId).text;
            } else {
                // 全局默认配置作用于多个租户：平台侧是各租户自己的 root 节点，这里只解析 OA 侧
                scopeText = oaScopeText(oaSubIds, oaDeptIds)
                        + " ⇄ 各租户 " + platformText(type) + " root 节点（含下级部门）";
            }
        }
        /* 规则定位：带 id 时更新那一条规则；不带 id 时新增一条规则。
           （同一个“租户 + 方向”可以有多条规则，所以不再按 (type, cfgid, direction) 唯一定位。） */
        int ruleId = OaonesUtil.i(OaonesUtil.s(params.getString("id")), 0);
        SyncPlanCfg existed = ruleId > 0
                ? OaonesMyBatis.call(SyncPlanMapper.class, mapper -> mapper.getById(table(), ruleId)) : null;
        if (ruleId > 0) {
            if (existed == null) {
                throw new RuntimeException("要修改的规则不存在（id " + ruleId + "），请刷新页面后重试");
            }
            int existedType = existed.getType() == null ? type : existed.getType().intValue();
            int existedCfg = existed.getCfgid() == null ? 0 : existed.getCfgid().intValue();
            int existedDir = existed.getDirection() == null ? direction : existed.getDirection().intValue();
            if (existedType != type || existedCfg != cfgId || existedDir != direction) {
                throw new RuntimeException("规则所属的平台 / 租户 / 方向与当前编辑的不一致，请刷新页面后重试");
            }
        }
        // 现有部门映射是本次同步的输入，不应再被当作“冲突”要求强制确认。
        String now = OaonesUtil.now();
        int operator = OaonesUtil.i(operatorId, 0);

        SyncPlanCfg entity = new SyncPlanCfg();
        entity.setType(type);
        entity.setCfgid(cfgId);
        entity.setDirection(direction);
        entity.setEnabled(enabled);
        entity.setPlanMode(Integer.valueOf(planMode));
        entity.setIntervalMin(Integer.valueOf(intervalMin));
        entity.setCron(cron);
        if (direction == OaonesConst.DIR_WX_TO_OA
                && (policy == OaonesConst.SYNC_NODEPT_CREATE
                || (policy == OaonesConst.SYNC_NODEPT_DEFAULT_DEPT
                && OaonesConst.SYNC_DEFAULT_TENANT.equals(defaultDeptId)))) {
            assertTenantOaSubMapping(type, String.valueOf(cfgId));
        }
        entity.setNodeptPolicy(Integer.valueOf(policy));
        entity.setDefaultDeptId(defaultDeptId);
        entity.setDefaultDeptName(defaultDeptName);
        entity.setScopeSet(scopeSet);
        entity.setOaSubIds(oaSubIds);
        entity.setOaDeptIds(oaDeptIds);
        entity.setWxDeptId(wxDeptId);
        entity.setScopeText(scopeText);
        // 保存成功即“已检测/已确认”（含用户选择强制同步）：之后不再重复询问“下级已有绑定”
        entity.setBindForced(1);
        entity.setNextRunTime(enabled == 1 ? nextRunOf(planMode, cron, intervalMin, new Date()) : "");
        entity.setRemark(OaonesUtil.s(params.getString("remark")));
        entity.setModedatamodifier(operator);
        entity.setModedatamodifydatetime(now);
        if (existed == null) {
            entity.setLastRunTime("");
            entity.setLastLogId(0);
            entity.setLastResult("");
            entity.setModedatacreater(operator);
            entity.setModedatacreatedate(OaonesUtil.nowDate());
            entity.setModedatacreatetime(OaonesUtil.nowTime());
            try {
                OaonesMyBatis.run(SyncPlanMapper.class, mapper -> mapper.insert(table(), entity));
            } catch (RuntimeException insertError) {
                OaonesLog.error("新增同步规则失败（insert）", insertError);
                throw new RuntimeException(insertRuleHint(insertError));
            }
        } else {
            entity.setId(existed.getId());
            // 保存设置不重置“最近执行”信息（计划任务执行结果仍保留在页面上可查）
            entity.setLastRunTime(existed.getLastRunTime());
            entity.setLastLogId(existed.getLastLogId());
            entity.setLastResult(existed.getLastResult());
            OaonesMyBatis.run(SyncPlanMapper.class, mapper -> mapper.update(table(), entity));
        }
        OaonesLog.step("保存同步规则 type=" + type + " cfgId=" + cfgId + " direction=" + direction
                + " id=" + (existed == null ? "新增" : OaonesUtil.nullToEmpty(entity.getId()))
                + " enabled=" + enabled
                + " 计划任务方式=" + (planMode == OaonesConst.SYNC_PLAN_MODE_CRON ? "高级（cron）" : "轮询间隔")
                + (planMode == OaonesConst.SYNC_PLAN_MODE_CRON
                        ? (" cron=" + cron) : (" 间隔=" + intervalMin + " 分钟"))
                + " 下次执行=" + entity.getNextRunTime());
        if (existed != null) {
            return toJson(entity);
        }
        // 新增：回读该档最新插入的那条规则（带 id）返回给页面
        SyncPlanCfg last = OaonesMyBatis.call(SyncPlanMapper.class,
                mapper -> mapper.getLast(table(), type, cfgId, direction));
        return last == null ? toJson(entity) : toJson(last);
    }

    /* ==================================================================
     * 一、同步范围（页面上“同步方向 + 同步范围”的映射关系）
     * ================================================================== */

    /**
     * 范围内对象集合：三个平台各自的 {@code dto.{wecom,dingtalk,feishu}.SyncScope}
     * 都按同一口径解析（分部含下级分部与部门、部门含下级部门、平台部门留空取 root 节点），
     * 这里归一成一份“部门ID集合 + 文案”，供保存校验与“首次检测”使用。
     */
    private static final class ScopeIndex {
        /** OA 侧范围内的部门ID（含下级部门）。 */
        private final Set<String> oaDeptIds = new LinkedHashSet<String>();
        /** 平台侧范围内的部门ID（含下级部门）。 */
        private final Set<String> wxDeptIds = new LinkedHashSet<String>();
        /** 页面上选中的 OA 分部ID（不含下级展开）。 */
        private final Set<String> oaSubIds = new LinkedHashSet<String>();
        /** 页面上选中的 OA 部门ID（不含下级展开）。 */
        private final Set<String> oaDeptSelectedIds = new LinkedHashSet<String>();
        /** 范围文案（“OA 分部… ⇄ 企业微信部门…”）。 */
        private String text = "";
    }

    /** 解析同步范围（按平台派发到各连接器的 SyncScopeServiceImpl）。 */
    private ScopeIndex scopeIndexOf(int type, String cfgId, int direction, String oaSubIds,
                                    String oaDeptIds, String wxDeptId) {
        ScopeIndex index = new ScopeIndex();
        if (type == OaonesConst.TYPE_DINGTALK) {
            com.customization.oaones.appsyn.dto.dingtalk.SyncScope scope =
                    new com.customization.oaones.appsyn.service.impl.dingtalk.SyncScopeServiceImpl()
                            .resolve(cfgId, direction, oaSubIds, oaDeptIds, wxDeptId);
            fillScope(index, scope.getOaSubs(), scope.getOaDepts(), scope.getOaDeptsSelected(),
                    scope.getWxDepts(), scope.getScopeText(), scope.getWxScopeText(), direction);
        } else if (type == OaonesConst.TYPE_FEISHU) {
            com.customization.oaones.appsyn.dto.feishu.SyncScope scope =
                    new com.customization.oaones.appsyn.service.impl.feishu.SyncScopeServiceImpl()
                            .resolve(cfgId, direction, oaSubIds, oaDeptIds, wxDeptId);
            fillScope(index, scope.getOaSubs(), scope.getOaDepts(), scope.getOaDeptsSelected(),
                    scope.getWxDepts(), scope.getScopeText(), scope.getWxScopeText(), direction);
        } else {
            com.customization.oaones.appsyn.dto.wecom.SyncScope scope =
                    new com.customization.oaones.appsyn.service.impl.wecom.SyncScopeServiceImpl()
                            .resolve(cfgId, direction, oaSubIds, oaDeptIds, wxDeptId);
            fillScope(index, scope.getOaSubs(), scope.getOaDepts(), scope.getOaDeptsSelected(),
                    scope.getWxDepts(), scope.getScopeText(), scope.getWxScopeText(), direction);
        }
        return index;
    }

    /** 把各平台 SyncScope 的解析结果填进统一口径（只取 id 集合与文案）。 */
    private void fillScope(ScopeIndex index, List<JSONObject> subs, List<JSONObject> depts,
                           List<JSONObject> deptSelected, List<JSONObject> wxDepts,
                           String scopeText, String wxScopeText, int direction) {
        index.oaSubIds.addAll(idsOf(subs));
        index.oaDeptSelectedIds.addAll(idsOf(deptSelected));
        index.oaDeptIds.addAll(idsOf(depts));
        index.wxDeptIds.addAll(idsOf(wxDepts));
        String left = OaonesUtil.s(scopeText);
        String right = OaonesUtil.s(wxScopeText);
        index.text = (direction == OaonesConst.DIR_OA_TO_WX ? left : right)
                + " ⇄ 按租户部门映射写入人员";
    }

    private Set<String> idsOf(List<JSONObject> rows) {
        Set<String> ids = new LinkedHashSet<String>();
        if (rows == null) {
            return ids;
        }
        for (JSONObject row : rows) {
            String id = OaonesUtil.s(row.getString("id"));
            if (id.length() > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * “首次检测”：该同步范围内（<b>含下级</b>）是否已有部门绑定 / 成员绑定。
     *
     * <p>保存设置时调用：检测到（返回非空）就由页面弹框告知用户并询问是否<b>强制同步</b>；
     * 用户确认强制同步后再保存，并按这条映射关系立即同步一次。</p>
     */
    private JSONArray scopeConflicts(int type, String cfgId, int direction, String oaSubIds,
                                     String oaDeptIds, String wxDeptId, String scopeText) {
        JSONArray conflicts = new JSONArray();
        if (OaonesUtil.i(cfgId, 0) <= 0) {
            // 全局默认配置作用于本平台多个租户，不做单租户的绑定检测
            return conflicts;
        }
        ScopeIndex index = scopeIndexOf(type, cfgId, direction, oaSubIds, oaDeptIds, wxDeptId);
        boolean oaToWx = direction == OaonesConst.DIR_OA_TO_WX;
        Map<String, String> oaDeptNames = oaDeptNames();
        Map<String, String> wxDeptNames = wxDeptNamesOf(type, cfgId);

        // 1) 部门绑定：范围内（含下级）的部门已经建过绑定
        JSONArray deptBinds = deptBindsOf(type, cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            String oaDept = OaonesUtil.s(bind.getString("oa_dept_id"));
            String wxDept = OaonesUtil.s(bind.getString("wx_dept_id"));
            if (oaToWx ? index.oaDeptIds.contains(oaDept) : index.wxDeptIds.contains(wxDept)) {
                conflicts.add("部门绑定：OA 部门「" + nameOf(oaDeptNames, oaDept) + "」(ID " + oaDept
                        + ") 已绑定平台部门「" + nameOf(wxDeptNames, wxDept) + "」(ID " + wxDept + ")");
            }
        }
        // 2) 成员绑定：范围内（含下级）的部门下已有成员建立了人员绑定
        JSONArray userBinds = userBindsOf(type, cfgId);
        Map<String, Integer> memberHits = new LinkedHashMap<String, Integer>();
        Map<String, String> memberNames = new LinkedHashMap<String, String>();
        for (int i = 0; i < userBinds.size(); i++) {
            JSONObject bind = userBinds.getJSONObject(i);
            String oaDept = OaonesUtil.s(bind.getString("oa_dept_id"));
            String wxDept = OaonesUtil.s(bind.getString("wx_dept_id"));
            String key = null;
            if (oaToWx && index.oaDeptIds.contains(oaDept)) {
                key = oaDept;
                memberNames.put(key, nameOf(oaDeptNames, oaDept));
            } else if (!oaToWx && index.wxDeptIds.contains(wxDept)) {
                key = wxDept;
                memberNames.put(key, nameOf(wxDeptNames, wxDept));
            }
            if (key == null) {
                continue;
            }
            memberHits.put(key, memberHits.get(key) == null ? Integer.valueOf(1)
                    : Integer.valueOf(memberHits.get(key).intValue() + 1));
        }
        for (Map.Entry<String, Integer> entry : memberHits.entrySet()) {
            String key = entry.getKey();
            conflicts.add("成员绑定：" + (oaToWx ? "OA 部门「" : "平台部门「")
                    + nameOf(memberNames, key) + "」(ID " + key + ") 下有 "
                    + entry.getValue().intValue() + " 个成员已建立人员绑定");
        }
        if (!conflicts.isEmpty()) {
            OaonesLog.step("同步设置首次检测：范围内已有绑定 cfgId=" + cfgId + " direction=" + direction
                    + " 范围=" + OaonesUtil.s(scopeText) + " 检测到 " + conflicts.size() + " 处");
        }
        return conflicts;
    }

    /** OA 部门ID → 名称（读 E9 部门缓存，一次查完）。 */
    private Map<String, String> oaDeptNames() {
        Map<String, String> names = new HashMap<String, String>();
        JSONArray rows = oaService().listDepartmentsFromCache();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            names.put(OaonesUtil.s(row.getString("id")), OaonesUtil.s(row.getString("name")));
        }
        return names;
    }

    /** 平台部门ID → 名称（读该租户的部门快照）。 */
    private Map<String, String> wxDeptNamesOf(int type, String cfgId) {
        Map<String, String> names = new HashMap<String, String>();
        try {
            JSONArray rows;
            if (type == OaonesConst.TYPE_DINGTALK) {
                rows = new com.customization.oaones.appsyn.service.impl.dingtalk.DeptTmpServiceImpl()
                        .listNormal(cfgId);
            } else if (type == OaonesConst.TYPE_FEISHU) {
                rows = new com.customization.oaones.appsyn.service.impl.feishu.DeptTmpServiceImpl()
                        .listNormal(cfgId);
            } else {
                rows = new com.customization.oaones.appsyn.service.impl.wecom.DeptTmpServiceImpl()
                        .listNormal(cfgId);
            }
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                names.put(OaonesUtil.s(row.getString("id")), OaonesUtil.s(row.getString("name")));
            }
        } catch (Exception e) {
            // 部门快照读不到不影响检测：文案里退回显示部门ID
            OaonesLog.error("读取部门快照失败（同步范围检测）cfgId=" + cfgId, e);
        }
        return names;
    }

    private String nameOf(Map<String, String> names, String id) {
        String name = names.get(OaonesUtil.s(id));
        return name == null || name.length() == 0 ? OaonesUtil.s(id) : name;
    }

    /**
     * 只按 OA 侧拼范围文案（“全局默认配置”用：平台侧固定为各租户自己的 root 节点）。
     *
     * <p>同时校验所选分部/部门是否存在（不存在或已封存直接报错，避免保存出执行时必然失败的设置）。</p>
     */
    private String oaScopeText(String oaSubIds, String oaDeptIds) {
        Map<String, JSONObject> subById = indexById(oaService().listSubCompaniesFromCache());
        Map<String, JSONObject> deptById = indexById(oaService().listDepartmentsFromCache());
        List<String> parts = new ArrayList<String>();
        for (String id : splitIds(oaSubIds)) {
            JSONObject sub = subById.get(id);
            if (sub == null) {
                throw new RuntimeException("OA 分部[id=" + id + "]不存在或已封存，请重新选择同步范围");
            }
            parts.add("分部「" + nameOf(sub) + "」（含下级分部与部门）");
        }
        for (String id : splitIds(oaDeptIds)) {
            JSONObject dept = deptById.get(id);
            if (dept == null) {
                throw new RuntimeException("OA 部门[id=" + id + "]不存在或已封存，请重新选择同步范围");
            }
            parts.add("部门「" + nameOf(dept) + "」（含下级部门）");
        }
        if (parts.isEmpty()) {
            return "全部 OA 部门";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private Map<String, JSONObject> indexById(JSONArray rows) {
        Map<String, JSONObject> index = new HashMap<String, JSONObject>();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            index.put(OaonesUtil.s(row.getString("id")), row);
        }
        return index;
    }

    /** 分部/部门行的显示名（name → fullname → id）。 */
    private String nameOf(JSONObject row) {
        String name = OaonesUtil.s(row.getString("name"));
        if (name.length() == 0) {
            name = OaonesUtil.s(row.getString("fullname"));
        }
        return name.length() == 0 ? OaonesUtil.s(row.getString("id")) : name;
    }

    /** 逗号分隔的 ID 串 → 列表（去空格、去空项）。 */
    private List<String> splitIds(String value) {
        List<String> list = new ArrayList<String>();
        String text = OaonesUtil.s(value);
        if (text.trim().length() > 0) {
            for (String token : text.split(",")) {
                String id = token == null ? "" : token.trim();
                if (id.length() > 0 && !list.contains(id)) {
                    list.add(id);
                }
            }
        }
        return list;
    }

    /* ==================================================================
     * 二、按“同步范围”执行（计划任务 / 立即执行一次）
     * ================================================================== */

    /** 设置行是否配置了同步范围（scope_set = 1 才算；历史行与全局默认配置走全量同步）。 */
    private int scopeSetOf(JSONObject plan) {
        if (plan == null) {
            return 0;
        }
        return valueOf(OaonesUtil.integer(OaonesUtil.s(plan.getString("scope_set")))) == 1 ? 1 : 0;
    }

    /**
     * 按设置里保存的“映射关系”执行一次同步（scope_set = 1 的设置走这里）。
     *
     * <p>保存该设置时已就“范围内已有绑定关系”做过检测（含用户选择强制同步），
     * 因此这里带上 {@code force=1} 直接按范围写入；执行结果与同步日志明细
     * 由各连接器的同步服务按同一口径落库，页面“结果详情”可直接查看。</p>
     */
    private JSONObject runScopedSync(int type, String cfgId, int direction, JSONObject plan,
                                    String operatorId) {
        JSONObject params = new JSONObject();
        params.put("cfg_id", cfgId);
        params.put("direction", String.valueOf(direction));
        params.put("oa_sub_ids", OaonesUtil.s(plan.getString("oa_sub_ids")));
        params.put("oa_dept_ids", OaonesUtil.s(plan.getString("oa_dept_ids")));
        params.put("wx_dept_id", OaonesUtil.s(plan.getString("wx_dept_id")));
        if (scopeSetOf(plan) != 1) {
            params.put("scope_all", "1");
            if (direction == OaonesConst.DIR_OA_TO_WX) {
                StringBuilder allSubs = new StringBuilder();
                JSONArray subs = oaService().listSubCompaniesFromCache();
                for (int i = 0; i < subs.size(); i++) {
                    String id = OaonesUtil.s(subs.getJSONObject(i).getString("id"));
                    if (id.length() == 0) { continue; }
                    if (allSubs.length() > 0) { allSubs.append(','); }
                    allSubs.append(id);
                }
                params.put("oa_sub_ids", allSubs.toString());
            }
        }
        params.put("nodept_policy", String.valueOf(OaonesUtil.i(
                OaonesUtil.s(plan.getString("nodept_policy")), OaonesConst.SYNC_NODEPT_SKIP)));
        params.put("default_dept_id", OaonesUtil.s(plan.getString("default_dept_id")));
        params.put("force", "1");
        JSONObject res;
        if (type == OaonesConst.TYPE_DINGTALK) {
            res = new com.customization.oaones.appsyn.service.impl.dingtalk.SyncServiceImpl()
                    .runScoped(params, operatorId);
        } else if (type == OaonesConst.TYPE_FEISHU) {
            res = new com.customization.oaones.appsyn.service.impl.feishu.SyncServiceImpl()
                    .runScoped(params, operatorId);
        } else {
            res = new com.customization.oaones.appsyn.service.impl.wecom.SyncServiceImpl()
                    .runScoped(params, operatorId);
        }
        if (res == null || res.getIntValue("code") != 0) {
            return res;
        }
        JSONObject data = res.getJSONObject("data");
        if (data == null) {
            return res;
        }
        JSONObject counts = data.getJSONObject("counts");
        int deptCount = counts == null ? 0
                : counts.getIntValue("dept_create") + counts.getIntValue("dept_update");
        int userCount = counts == null ? 0
                : counts.getIntValue("user_create") + counts.getIntValue("user_update");
        int success = data.getIntValue("success");
        int skip = data.getIntValue("skip");
        int fail = data.getIntValue("fail");
        JSONObject out = new JSONObject();
        out.put("code", 0);
        out.put("scoped", true);
        out.put("cfg_id", cfgId);
        out.put("direction", direction);
        out.put("scope_text", OaonesUtil.s(data.getString("scope_text")));
        out.put("log_id", OaonesUtil.s(data.getString("log_id")));
        out.put("total", data.getIntValue("total"));
        out.put("success", success);
        out.put("skip", skip);
        out.put("fail", fail);
        out.put("dept_count", deptCount);
        out.put("user_count", userCount);
        out.put("nodept_count", data.getIntValue("nodept_count"));
        out.put("nodept_dept", OaonesUtil.s(data.getString("nodept_dept")));
        out.put("msg", "按源组织与部门映射同步完成：同步 " + deptCount + " 个部门、"
                + userCount + " 个成员（成功 "
                + success + "，跳过 " + skip + "，失败 " + fail + "）");
        return out;
    }

    /** 入参归一：逗号分隔的 ID（去空格、去空项、去重）。 */
    private String csv(String value) {
        Set<String> ids = new LinkedHashSet<String>();
        String text = OaonesUtil.s(value);
        if (text.trim().length() > 0) {
            for (String token : text.split(",")) {
                String id = token == null ? "" : token.trim();
                if (id.length() > 0) {
                    ids.add(id);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** Integer 空值安全：null 按 0。 */
    private int valueOf(Integer value) {
        return value == null ? 0 : value.intValue();
    }

    /* ---------------------------------------------------------------
     * 2.1 各平台的绑定关系读取（“首次检测”用）
     * --------------------------------------------------------------- */

    private JSONArray deptBindsOf(int type, String cfgId) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new com.customization.oaones.appsyn.service.impl.dingtalk.DeptBindServiceImpl()
                    .listEnabled(cfgId);
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new com.customization.oaones.appsyn.service.impl.feishu.DeptBindServiceImpl()
                    .listEnabled(cfgId);
        }
        return new com.customization.oaones.appsyn.service.impl.wecom.DeptBindServiceImpl()
                .listEnabled(cfgId);
    }

    private JSONArray userBindsOf(int type, String cfgId) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new com.customization.oaones.appsyn.service.impl.dingtalk.UserBindServiceImpl()
                    .listBoundRows(cfgId);
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new com.customization.oaones.appsyn.service.impl.feishu.UserBindServiceImpl()
                    .listBoundRows(cfgId);
        }
        return new com.customization.oaones.appsyn.service.impl.wecom.UserBindServiceImpl()
                .listBoundRows(cfgId);
    }

    /**
     * 平台 → OA 选择“新增部门”时，租户必须先通过“OA 分部 - 平台租户”映射关联到 OA 分部。
     * 没有这条映射就无法确定新 OA 部门应归属哪个 OA 租户/分部，禁止自动创建。
     */
    private void assertTenantOaSubMapping(int type, String cfgId) {
        int cfg = OaonesUtil.i(cfgId, 0);
        if (cfg <= 0) {
            throw new RuntimeException("选择“新增部门”前请先保存具体租户设置，并建立租户与 OA 分部的映射");
        }
        OrgMap mapping = OaonesMyBatis.call(OrgMapMapper.class,
                mapper -> mapper.findByCfg(OaonesProps.subTenantMapTable(), type, cfg));
        if (mapping == null || mapping.getOaSubId() == null || mapping.getOaSubId().intValue() <= 0) {
            throw new RuntimeException("平台 → OA 选择“新增部门”时，必须先把该租户映射到 OA 分部；"
                    + "请先到分部映射中设置租户与 OA 分部的绑定关系");
        }
    }

    @Override
    public JSONObject delete(String type, String id, String operatorId) {
        int t = platformType(type);
        int ruleId = OaonesUtil.i(id, 0);
        if (ruleId <= 0) {
            throw new RuntimeException("请指定要删除的规则（id 缺失）");
        }
        SyncPlanCfg row = OaonesMyBatis.call(SyncPlanMapper.class,
                mapper -> mapper.getById(table(), ruleId));
        if (row == null) {
            throw new RuntimeException("要删除的规则不存在（id " + ruleId + "），请刷新页面后重试");
        }
        OaonesMyBatis.run(SyncPlanMapper.class, mapper -> mapper.deleteById(table(), ruleId));
        OaonesLog.step("删除同步规则 type=" + t + " cfgId=" + OaonesUtil.nullToEmpty(row.getCfgid())
                + " direction=" + OaonesUtil.nullToEmpty(row.getDirection()) + " id=" + ruleId);
        JSONObject data = new JSONObject();
        data.put("id", ruleId);
        data.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgid()));
        data.put("direction", OaonesUtil.nullToEmpty(row.getDirection()));
        return data;
    }

    @Override
    public JSONObject setEnabled(String type, String id, int enabled, String operatorId) {
        int t = platformType(type);
        int ruleId = OaonesUtil.i(id, 0);
        if (ruleId <= 0) {
            throw new RuntimeException("请指定要启停的规则（id 缺失）");
        }
        SyncPlanCfg row = OaonesMyBatis.call(SyncPlanMapper.class,
                mapper -> mapper.getById(table(), ruleId));
        if (row == null) {
            throw new RuntimeException("要启停的规则不存在（id " + ruleId + "），请刷新页面后重试");
        }
        boolean on = enabled == 1;
        String nextRun = "";
        if (on) {
            // 启动前按这条规则自己的“计划任务方式”校验：轮询间隔要有间隔、高级模式要有合法 cron，
            // 缺了 / 写错了就拒绝启动，避免“启用了但永远不会跑”
            int planMode = planModeOf(row.getPlanMode(), row.getCron());
            if (planMode == OaonesConst.SYNC_PLAN_MODE_CRON) {
                String cron = OaonesCronExpr.normalize(OaonesUtil.s(row.getCron()));
                if (cron.length() == 0 || !OaonesCronExpr.valid(cron)) {
                    throw new RuntimeException("该设置的计划任务是“高级模式（cron）”，还没有合法的 cron 表达式，"
                            + "请先在“编辑同步设置 → 计划任务”里填好并保存，再启动计划任务");
                }
            }
            // 轮询间隔方式：间隔缺失 / 越界时按默认值兜底（见 intervalOf），总能算出下一次执行时间
            nextRun = nextRunOf(planMode, OaonesUtil.s(row.getCron()),
                    intervalOf(row.getIntervalMin()), new Date());
        }
        final int enabledValue = on ? 1 : 0;
        final String nextRunValue = nextRun;
        OaonesMyBatis.run(SyncPlanMapper.class, mapper -> mapper.updateEnabled(table(), ruleId,
                enabledValue, nextRunValue, OaonesUtil.i(operatorId, 0), OaonesUtil.now()));
        OaonesLog.step("同步规则启停 type=" + t + " id=" + ruleId + " cfgId=" + OaonesUtil.nullToEmpty(row.getCfgid())
                + " direction=" + OaonesUtil.nullToEmpty(row.getDirection())
                + " enabled=" + enabledValue + " 下次执行=" + nextRunValue);
        JSONObject data = new JSONObject();
        data.put("id", ruleId);
        data.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgid()));
        data.put("direction", OaonesUtil.nullToEmpty(row.getDirection()));
        data.put("enabled", enabledValue);
        data.put("enabled_text", on ? "已启用" : "已停用");
        data.put("next_run_time", nextRunValue);
        return data;
    }

    @Override
    public JSONArray planPreview(String mode, String cron, String intervalMin, int count) {
        int times = count > 0 ? count : PREVIEW_COUNT;
        String cronText = OaonesCronExpr.normalize(cron);
        String modeText = OaonesUtil.s(mode).trim();
        int planMode = planModeOf(modeText.length() == 0 ? null : OaonesUtil.integer(modeText), cronText);
        JSONArray result = new JSONArray();
        if (planMode == OaonesConst.SYNC_PLAN_MODE_CRON) {
            if (cronText.length() == 0) {
                throw new RuntimeException("请先填写 cron 表达式");
            }
            if (!OaonesCronExpr.valid(cronText)) {
                throw new RuntimeException("cron 表达式不正确，请按 Quartz 口径填写“秒 分 时 日 月 周”"
                        + "（例：0 0 2 * * ? 表示每天 02:00:00 执行，0 0/30 * * * ? 表示每 30 分钟执行）");
            }
            result.addAll(OaonesCronExpr.nextTimes(cronText, new Date(), times));
            return result;
        }
        // 轮询间隔：从当前时刻起每 N 分钟一次（与到点判断同一口径：下一次 = 上一次执行时刻 + N 分钟）
        int minutes = checkInterval(intervalMin);
        long base = new Date().getTime();
        for (int i = 1; i <= times; i++) {
            result.add(OaonesUtil.formatDateTime(base + (long) i * minutes * 60000L));
        }
        return result;
    }

    /**
     * 一条规则的**下一次执行时间**（两种方式共用，保存 / 启停 / 计划任务认领都走这里）。
     *
     * <p>轮询间隔：{@code from + intervalMin} 分钟（不累积历史漂移）；
     * 高级模式：按 cron 表达式从 {@code from} 往后推一次（表达式空 / 非法时返回空串）。</p>
     */
    private String nextRunOf(int planMode, String cron, int intervalMin, Date from) {
        Date base = from == null ? new Date() : from;
        if (planMode == OaonesConst.SYNC_PLAN_MODE_CRON) {
            return OaonesCronExpr.nextAfter(cron, base);
        }
        return OaonesUtil.formatDateTime(base.getTime() + (long) intervalOf(intervalMin) * 60000L);
    }

    /**
     * 计划任务方式归一（{@code OaonesConst.SYNC_PLAN_MODE_*}）。
     *
     * <p>老库兼容：{@code plan_mode} 为空（还没执行补列脚本、或老版本程序写入的行）时，
     * 有 cron 表达式就按<b>高级模式</b>、没有 cron 就按<b>轮询间隔</b>——
     * 因此升级后原有 cron 的节奏不变，与升级前的执行口径一致。</p>
     *
     * @param stored 表里 / 页面传来的方式值（可为 null）
     * @param cron   cron 表达式（用于老库兼容判断）
     */
    private int planModeOf(Integer stored, String cron) {
        if (stored != null) {
            return stored.intValue() == OaonesConst.SYNC_PLAN_MODE_CRON
                    ? OaonesConst.SYNC_PLAN_MODE_CRON : OaonesConst.SYNC_PLAN_MODE_INTERVAL;
        }
        return OaonesCronExpr.normalize(cron).length() == 0
                ? OaonesConst.SYNC_PLAN_MODE_INTERVAL : OaonesConst.SYNC_PLAN_MODE_CRON;
    }

    /** 轮询间隔（分钟）：空值 / 越界回落到默认值（老库缺列时也有可执行的时间）。 */
    private int intervalOf(Integer minutes) {
        int value = valueOf(minutes);
        if (value <= 0) {
            return OaonesConst.SYNC_PLAN_DEF_MINUTES;
        }
        return Math.min(Math.max(value, OaonesConst.SYNC_PLAN_MIN_MINUTES), OaonesConst.SYNC_PLAN_MAX_MINUTES);
    }

    /** 校验页面填的轮询间隔（分钟）：必须是 {@code SYNC_PLAN_MIN_MINUTES ~ SYNC_PLAN_MAX_MINUTES} 的整数。 */
    private int checkInterval(String text) {
        String value = OaonesUtil.s(text).trim();
        if (value.length() == 0) {
            throw new RuntimeException("请先填写轮询间隔（分钟）");
        }
        if (!value.matches("\\d+")) {
            throw new RuntimeException("轮询间隔要填整数分钟（例：30 = 每 30 分钟同步一次）");
        }
        int minutes = OaonesUtil.i(value, 0);
        if (minutes < OaonesConst.SYNC_PLAN_MIN_MINUTES || minutes > OaonesConst.SYNC_PLAN_MAX_MINUTES) {
            throw new RuntimeException("轮询间隔要在 " + OaonesConst.SYNC_PLAN_MIN_MINUTES + " ~ "
                    + OaonesConst.SYNC_PLAN_MAX_MINUTES + " 分钟之间（"
                    + OaonesConst.SYNC_PLAN_MIN_MINUTES + " 分钟是频率下限，"
                    + OaonesConst.SYNC_PLAN_MAX_MINUTES + " 分钟 = 24 小时）");
        }
        return minutes;
    }

    /** 轮询间隔文案（页面列表 / 提示用）：整小时说“每 N 小时”，否则“每 N 分钟”。 */
    private String intervalText(int minutes) {
        int value = intervalOf(Integer.valueOf(minutes));
        if (value % 60 == 0) {
            return "每 " + (value / 60) + " 小时轮询一次";
        }
        return "每 " + value + " 分钟轮询一次";
    }

    @Override
    public JSONArray listDue(String now, int limit) {
        List<SyncPlanCfg> rows = OaonesMyBatis.call(SyncPlanMapper.class,
                mapper -> mapper.listDue(table(), OaonesUtil.s(now), limit > 0 ? limit : DUE_LIMIT));
        JSONArray result = new JSONArray();
        if (rows != null) {
            for (SyncPlanCfg row : rows) {
                result.add(toJson(row));
            }
        }
        return result;
    }

    @Override
    public int runDuePlans(int limit) {
        JSONArray due = listDue(OaonesUtil.now(), limit > 0 ? limit : DUE_LIMIT);
        int executed = 0;
        for (int i = 0; i < due.size(); i++) {
            JSONObject plan = due.getJSONObject(i);
            String id = OaonesUtil.s(plan.getString("id"));
            String oldNext = OaonesUtil.s(plan.getString("next_run_time"));
            // 按这条规则自己的计划任务方式算下一次：轮询间隔 = 现在 + N 分钟；高级模式 = cron 往后推一次
            int planMode = planModeOf(OaonesUtil.integer(OaonesUtil.s(plan.getString("plan_mode"))),
                    OaonesUtil.s(plan.getString("cron")));
            String nextRun = nextRunOf(planMode, OaonesUtil.s(plan.getString("cron")),
                    intervalOf(OaonesUtil.integer(OaonesUtil.s(plan.getString("interval_min")))), new Date());
            if (!claim(OaonesUtil.i(id, 0), oldNext, nextRun)) {
                // 已被别的实例（或 E9 后台计划任务）认领，或页面刚改过设置：本次跳过
                OaonesLog.step("同步设置计划任务跳过（未认领成功）id=" + id);
                continue;
            }
            runDue(plan);
            executed++;
        }
        return executed;
    }

    /**
     * 认领一条到点设置：只有把 {@code next_run_time} 从旧值改成下一个执行时间成功的实例才执行。
     *
     * <p>多实例部署时天然去重；页面保存/删除会改这一行，认领失败即自动跳过。</p>
     */
    private boolean claim(int planId, String oldNext, String newNext) {
        if (planId <= 0 || oldNext.length() == 0) {
            return false;
        }
        Integer rows = OaonesMyBatis.call(SyncPlanMapper.class, mapper -> mapper.claimRun(table(), planId,
                oldNext, newNext, OaonesUtil.now(), "执行中…", OaonesUtil.now()));
        return rows != null && rows.intValue() > 0;
    }

    @Override
    public JSONObject runNow(String type, String id, String operatorId) {
        int t = platformType(type);
        int ruleId = OaonesUtil.i(id, 0);
        if (ruleId <= 0) {
            throw new RuntimeException("请指定要执行的规则（id 缺失）");
        }
        JSONObject plan = getById(String.valueOf(ruleId));
        if (plan == null) {
            throw new RuntimeException("要执行的规则不存在（id " + ruleId + "），请刷新页面后重试");
        }
        int cfgIdValue = OaonesUtil.i(OaonesUtil.s(plan.getString("cfg_id")), 0);
        int dir = directionValue(OaonesUtil.s(plan.getString("direction")));
        if (dir < 0) {
            dir = 0;
        }
        JSONObject sync = cfgIdValue > 0
                ? runPlatformSync(t, String.valueOf(cfgIdValue), dir, operatorId, plan)
                : runGlobalSync(t, dir, operatorId, plan);
        JSONObject data = new JSONObject();
        data.put("plan", plan);
        data.put("sync", sync);
        if (plan != null) {
            // 手工执行只回写“最近一次执行”，不动计划任务的“下次执行时间”
            Integer logId = sync == null ? null
                    : OaonesUtil.integer(OaonesUtil.s(sync.getString("log_id")));
            String msg = sync == null ? "" : OaonesUtil.s(sync.getString("msg"));
            markRun(OaonesUtil.s(plan.getString("id")), OaonesUtil.now(), logId,
                    "手工执行：" + (msg.length() == 0 ? "已触发" : msg),
                    OaonesUtil.s(plan.getString("next_run_time")), operatorId);
        }
        return data;
    }

    @Override
    public JSONObject runDue(JSONObject plan) {
        if (plan == null) {
            return null;
        }
        String id = OaonesUtil.s(plan.getString("id"));
        String type = OaonesUtil.s(plan.getString("type"));
        String cfgId = OaonesUtil.s(plan.getString("cfg_id"));
        String direction = OaonesUtil.s(plan.getString("direction"));
        // 下一次执行按这条规则自己保存的计划任务方式算（轮询间隔 / 高级 cron）
        int planMode = planModeOf(OaonesUtil.integer(OaonesUtil.s(plan.getString("plan_mode"))),
                OaonesUtil.s(plan.getString("cron")));
        String nextRun = nextRunOf(planMode, OaonesUtil.s(plan.getString("cron")),
                intervalOf(OaonesUtil.integer(OaonesUtil.s(plan.getString("interval_min")))), new Date());
        Integer logId = null;
        String message;
        JSONObject sync = null;
        try {
            int planType = platformType(type);
            int planDir = directionValue(direction);
            if (OaonesUtil.i(cfgId, 0) > 0) {
                sync = runPlatformSync(planType, cfgId, planDir, "0", plan);
                logId = sync == null ? null : OaonesUtil.integer(OaonesUtil.s(sync.getString("log_id")));
                String msg = sync == null ? "" : OaonesUtil.s(sync.getString("msg"));
                message = "计划任务执行：" + (msg.length() == 0 ? "已触发" : msg);
            } else {
                // 全局默认配置：覆盖该平台下“没有单独配置本方向”的已启用租户
                sync = runGlobalSync(planType, planDir, "0", plan);
                message = "计划任务（全局默认配置）执行：覆盖 "
                        + sync.getIntValue("tenant_count") + " 个租户，成功 "
                        + sync.getIntValue("success_count") + "，失败 " + sync.getIntValue("fail_count");
            }
        } catch (Throwable t) {
            message = "计划任务执行失败：" + (t.getMessage() == null ? t.getClass().getName() : t.getMessage());
            OaonesLog.error("同步设置计划任务执行失败 id=" + id, t);
        }
        markRun(id, OaonesUtil.now(), logId, message, nextRun, "0");
        return sync;
    }

    @Override
    public void markRun(String id, String runTime, Integer logId, String result,
                        String nextRun, String operatorId) {
        int planId = OaonesUtil.i(id, 0);
        if (planId <= 0) {
            return;
        }
        String rawResult = OaonesUtil.s(result);
        // lambda 里引用的是“实际上的最终变量”，这里显式定稿后传入，避免多个临时变量互相牵制
        final String resultText = rawResult.length() > 490 ? rawResult.substring(0, 490) : rawResult;
        final String next = OaonesUtil.s(nextRun);
        final Integer logIdValue = logId == null ? Integer.valueOf(0) : logId;
        final String runTimeValue = OaonesUtil.s(runTime);
        final int modifier = OaonesUtil.i(operatorId, 0);
        OaonesMyBatis.run(SyncPlanMapper.class, mapper -> mapper.updateRun(table(), planId,
                runTimeValue, logIdValue, resultText, next, modifier, OaonesUtil.now()));
    }

    @Override
    public List<Integer> maintainableCfgIds(String type, String operatorId) {
        int t = platformType(type);
        JSONArray rows;
        if (t == OaonesConst.TYPE_DINGTALK) {
            rows = new com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl()
                    .listMaintainable(operatorId);
        } else if (t == OaonesConst.TYPE_FEISHU) {
            rows = new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl()
                    .listMaintainable(operatorId);
        } else {
            rows = new com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl()
                    .listMaintainable(operatorId);
        }
        List<Integer> ids = new ArrayList<Integer>();
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                Integer id = OaonesUtil.integer(rows.getJSONObject(i).getString("id"));
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** 全局默认配置的行标识：cfgid = 0 表示“本平台所有租户的默认设置”。 */
    public static final String GLOBAL_CFG_ID = "0";

    /**
     * 执行“全局默认配置”：对该平台下“已启用同步且没有单独配置本方向”的租户逐个同步。
     *
     * <p>每个租户的同步结果仍写在各自的同步日志里（可在“同步结果 → 明细”按租户查看），
     * 这里返回的是跨租户的汇总，用于计划任务/立即执行的结果提示。</p>
     */
    private JSONObject runGlobalSync(int type, int direction, String operatorId, JSONObject plan) {
        List<Integer> tenants = globalTargetCfgIds(type, direction);
        int okCount = 0;
        int failCount = 0;
        int deptCount = 0;
        int userCount = 0;
        int noDeptCount = 0;
        String noDeptDept = "";
        JSONArray messages = new JSONArray();
        for (Integer tenantId : tenants) {
            try {
                JSONObject one = runPlatformSync(type, String.valueOf(tenantId), direction, operatorId, plan);
                int code = one == null ? -1 : one.getIntValue("code");
                if (code == 0) {
                    okCount++;
                    deptCount += one.getIntValue("dept_count");
                    userCount += one.getIntValue("user_count");
                    noDeptCount += one.getIntValue("nodept_count");
                    if (noDeptDept.length() == 0) {
                        noDeptDept = OaonesUtil.s(one.getString("nodept_dept"));
                    }
                } else {
                    failCount++;
                    messages.add("租户[" + tenantId + "]：" + (one == null ? "无返回" : OaonesUtil.s(one.getString("msg"))));
                }
            } catch (Throwable t) {
                failCount++;
                messages.add("租户[" + tenantId + "]同步异常：" + (t.getMessage() == null ? t.getClass().getName() : t.getMessage()));
                OaonesLog.error("全局默认配置同步失败 tenantId=" + tenantId, t);
            }
        }
        JSONObject data = new JSONObject();
        data.put("code", failCount == 0 ? 0 : 1);
        data.put("global", true);
        data.put("tenant_count", tenants.size());
        data.put("success_count", okCount);
        data.put("fail_count", failCount);
        data.put("dept_count", deptCount);
        data.put("user_count", userCount);
        data.put("nodept_count", noDeptCount);
        data.put("nodept_dept", noDeptDept);
        data.put("msg", "全局默认配置：覆盖 " + tenants.size() + " 个租户（成功 " + okCount + "，失败 " + failCount + "）");
        data.put("messages", messages);
        return data;
    }

    /**
     * 全局默认配置覆盖的租户：该平台下<b>已启用同步</b>、且该方向<b>没有单独配置</b>的租户。
     *
     * <p>租户自己配了（哪怕是停用状态）就按租户自己的走，不再套全局默认。</p>
     */
    private List<Integer> globalTargetCfgIds(int type, int direction) {
        List<Integer> ids = new ArrayList<Integer>();
        JSONArray rows = tenantsOf(type);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            int id = OaonesUtil.i(OaonesUtil.s(row.getString("id")), 0);
            if (id <= 0) {
                continue;
            }
            // 未启用同步的租户不参与（同步服务本身也会拒绝执行）
            if (!"1".equals(OaonesUtil.s(row.getString("sync_enable")))) {
                continue;
            }
            if (get(type, String.valueOf(id), direction) != null) {
                continue;
            }
            ids.add(Integer.valueOf(id));
        }
        return ids;
    }

    /** 某平台的全部租户（含租户名与同步开关）。 */
    private JSONArray tenantsOf(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return new com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl().list();
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return new com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl().list();
        }
        return new com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl().list();
    }

    /**
     * 按平台派发到该连接器的同步服务（设置只决定口径，同步动作仍走各连接器自己的实现）。
     *
     * <p>企微为默认分支：历史数据与老调用方不带 type 时仍按企微执行。</p>
     */
    private JSONObject runPlatformSync(int type, String cfgId, int direction, String operatorId,
                                       JSONObject plan) {
        // 同步中心的全部规则统一走“源组织 + 部门映射”引擎；未配置范围时按全部源组织执行。
        return runScopedSync(type, cfgId, direction, plan, operatorId);
    }

    /** 设置行转页面 JSON（同时给出平台/方向/口径的展示文案）。 */
    private JSONObject toJson(SyncPlanCfg row) {
        int type = row.getType() == null ? OaonesConst.TYPE_WECHAT : row.getType().intValue();
        int direction = row.getDirection() == null ? OaonesConst.DIR_OA_TO_WX : row.getDirection().intValue();
        int policy = row.getNodeptPolicy() == null ? OaonesConst.SYNC_NODEPT_SKIP : row.getNodeptPolicy().intValue();
        int enabled = row.getEnabled() == null ? 0 : row.getEnabled().intValue();
        String platform = platformText(type);
        JSONObject json = new JSONObject();
        json.put("id", OaonesUtil.nullToEmpty(row.getId()));
        json.put("type", type);
        json.put("cfg_id", OaonesUtil.nullToEmpty(row.getCfgid()));
        json.put("direction", direction);
        json.put("enabled", enabled);
        // 计划任务：两种方式（轮询间隔（分钟）/ 高级 cron），两种取值一起回给页面（切方式不丢值）
        int planMode = planModeOf(row.getPlanMode(), row.getCron());
        int intervalMin = intervalOf(row.getIntervalMin());
        json.put("plan_mode", planMode);
        json.put("interval_min", intervalMin);
        json.put("cron", OaonesUtil.nullToEmpty(row.getCron()));
        json.put("plan_text", planMode == OaonesConst.SYNC_PLAN_MODE_CRON
                ? (OaonesUtil.s(row.getCron()).length() == 0 ? "高级模式（未填 cron）" : OaonesUtil.s(row.getCron()))
                : intervalText(intervalMin));
        json.put("plan_mode_text", planMode == OaonesConst.SYNC_PLAN_MODE_CRON
                ? "高级模式（cron）" : "轮询间隔（分钟）");
        json.put("nodept_policy", policy);
        json.put("default_dept_id", OaonesUtil.nullToEmpty(row.getDefaultDeptId()));
        json.put("default_dept_name", OaonesUtil.nullToEmpty(row.getDefaultDeptName()));
        // 同步范围（页面上“同步方向 + 同步范围”的映射关系）：scope_set = 0 表示未设置（全量同步）
        int scopeSet = row.getScopeSet() == null ? 0 : row.getScopeSet().intValue();
        json.put("scope_set", scopeSet == 1 ? 1 : 0);
        json.put("oa_sub_ids", OaonesUtil.nullToEmpty(row.getOaSubIds()));
        json.put("oa_dept_ids", OaonesUtil.nullToEmpty(row.getOaDeptIds()));
        json.put("wx_dept_id", OaonesUtil.nullToEmpty(row.getWxDeptId()));
        json.put("scope_text", OaonesUtil.nullToEmpty(row.getScopeText()));
        json.put("bind_forced", row.getBindForced() == null ? 0 : row.getBindForced().intValue());
        json.put("scope_label", scopeSet == 1
                ? (OaonesUtil.s(row.getScopeText()).length() == 0 ? "按范围同步" : OaonesUtil.s(row.getScopeText()))
                : "全量同步（未设置范围）");
        // 完整的范围文案（“OA 侧 ⇄ 平台侧”整段）：页面拿它做「源组织 / 目标组织」两栏的悬浮提示
        json.put("scope_range_text", OaonesUtil.nullToEmpty(row.getScopeText()));
        json.put("next_run_time", OaonesUtil.nullToEmpty(row.getNextRunTime()));
        json.put("last_run_time", OaonesUtil.nullToEmpty(row.getLastRunTime()));
        json.put("last_log_id", OaonesUtil.nullToEmpty(row.getLastLogId()));
        json.put("last_result", OaonesUtil.nullToEmpty(row.getLastResult()));
        json.put("remark", OaonesUtil.nullToEmpty(row.getRemark()));
        boolean global = row.getCfgid() == null || row.getCfgid().intValue() <= 0;
        json.put("scope", global ? "global" : "tenant");
        json.put("scope_text", global ? "全局默认配置" : "当前租户配置");
        json.put("platform_text", platform);
        json.put("direction_text", direction == OaonesConst.DIR_OA_TO_WX ? "OA→平台" : "平台→OA");
        // 方向文案带具体平台名：页面再补上租户名（“OA → 企业微信（租户名）”）
        json.put("direction_label", direction == OaonesConst.DIR_OA_TO_WX
                ? "OA → " + platform : platform + " → OA");
        json.put("enabled_text", enabled == 1 ? "已启用" : "已停用");
        json.put("nodept_text", nodeptText(direction, policy,
                OaonesUtil.s(row.getDefaultDeptName()), OaonesUtil.s(row.getDefaultDeptId())));
        return json;
    }

    /**
     * 列表的「源组织 / 目标组织」两栏：**源组织一行一个组织**（多个组织用 {@code \n} 分隔，页面按行显示）、
     * **目标组织恒为一行**（同步落点只有一个，见下）。
     *
     * <p>哪一侧是“源”由同步方向决定：<b>A（OA→平台）</b> = 源是 OA 侧（分部 / 部门）、目标是平台侧
     * （一个平台部门，留空 = 该租户 root 节点）；<b>B（平台→OA）</b> = 源是平台侧（一个平台部门或
     * root 节点）、目标是 OA 落点——**一条落点路径**「落点：分部「X」› 部门「Y」」，
     * 只选了落点分部时只有前半段，都没选（全量同步 / 历史行）= “不指定落点（按部门绑定 / 同名匹配，不新建）”。</p>
     *
     * <p>未设置范围（{@code scope_set = 0}，历史行与“全局默认配置”）：A 方向源 = “全部 OA 部门”、
     * 目标 = 该租户 root 节点；B 方向源 = 该租户 root 节点（含下级）、目标 = 不指定落点
     * （与同步引擎的实际口径一致）。</p>
     *
     * <p>名称按设置里保存的 ID 现算：OA 侧走 E9 组织缓存（缓存表一次，列表内复用），
     * 平台侧走该租户的部门快照（{@code wxNameCache} 按租户缓存，一次列表最多每租户查一次；
     * 快照读不到时退回显示部门ID，不影响其它列）。</p>
     */
    private void fillScopeSides(JSONObject json, SyncPlanCfg row,
                                Map<String, JSONObject> subById, Map<String, JSONObject> deptById,
                                Map<String, Map<String, String>> wxNameCache) {
        int type = row.getType() == null ? OaonesConst.TYPE_WECHAT : row.getType().intValue();
        int direction = row.getDirection() == null ? OaonesConst.DIR_OA_TO_WX : row.getDirection().intValue();
        String platform = platformText(type);
        boolean oaFirst = direction == OaonesConst.DIR_OA_TO_WX;
        boolean scoped = valueOf(row.getScopeSet()) == 1;
        List<String> src = new ArrayList<String>();
        List<String> dst = new ArrayList<String>();
        if (oaFirst) {
            // A：只选 OA 源组织；人员目标按租户现有部门映射确定。
            if (scoped) {
                for (String id : splitIds(row.getOaSubIds())) {
                    src.add("分部「" + scopeName(subById, id) + "」");
                }
                for (String id : splitIds(row.getOaDeptIds())) {
                    src.add("部门「" + scopeName(deptById, id) + "」");
                }
            } else {
                src.add("全部 OA 部门");
            }
            dst.add("按租户部门映射写入" + platform + "人员（同一 OA 部门取第一条映射）");
        } else {
            // B：只选平台源部门；OA 目标按租户现有部门映射确定。
            src.add(platformLine(row, type, platform, wxNameCache));
            dst.add("按租户部门映射写入 OA 人员（同一平台部门取第一条映射）");
        }
        json.put("scope_src_text", joinLines(src));
        json.put("scope_dst_text", joinLines(dst));
        json.put("scope_src_side", oaFirst ? "OA 侧" : platform + "侧");
        json.put("scope_dst_side", oaFirst ? platform + "映射目标" : "OA 映射目标");
    }

    /**
     * 平台侧（只占一行）：指定了平台部门就写该部门，留空 = 该租户配置的 root 节点；
     * “全局默认配置”（cfgid = 0）作用于本平台多个租户，平台侧是各租户自己的 root 节点。
     */
    private String platformLine(SyncPlanCfg row, int type, String platform,
                                Map<String, Map<String, String>> wxNameCache) {
        if (row.getCfgid() == null || row.getCfgid().intValue() <= 0) {
            return "各租户 " + platform + " root 节点";
        }
        List<String> ids = splitIds(row.getWxDeptId());
        if (ids.isEmpty()) {
            return platform + " root 节点";
        }
        Map<String, String> names = wxDeptNamesCache(type, row.getCfgid(), wxNameCache);
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(platform).append("部门「").append(nameOf(names, id)).append("」");
        }
        return sb.toString();
    }

    /**
     * OA 落点（B 方向的目标，**只有一行**）：“落点：分部「X」› 部门「Y」”。
     *
     * <p>落点分部必选、落点部门可空：只选了落点分部时新建部门直接作为该分部的顶级部门；
     * 两个都没配（全量同步 / 历史行）时按同步引擎的实际口径写成“不指定落点（按部门绑定 / 同名匹配，不新建）”。</p>
     */
    private String landingLine(SyncPlanCfg row, Map<String, JSONObject> subById,
                               Map<String, JSONObject> deptById) {
        StringBuilder sb = new StringBuilder();
        for (String id : splitIds(row.getOaSubIds())) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append("分部「").append(scopeName(subById, id)).append("」");
        }
        for (String id : splitIds(row.getOaDeptIds())) {
            if (sb.length() > 0) {
                sb.append(" › ");
            }
            sb.append("部门「").append(scopeName(deptById, id)).append("」");
        }
        return sb.length() == 0
                ? "不指定落点（按部门绑定 / 同名匹配，不新建）" : ("落点：" + sb);
    }

    /** 平台部门名缓存：该租户的部门快照只读一次（键 = 平台 + 租户ID）。 */
    private Map<String, String> wxDeptNamesCache(int type, Integer cfgId,
                                                 Map<String, Map<String, String>> cache) {
        String key = type + "-" + (cfgId == null ? "0" : String.valueOf(cfgId));
        Map<String, String> names = cache.get(key);
        if (names == null) {
            names = wxDeptNamesOf(type, String.valueOf(cfgId == null ? 0 : cfgId.intValue()));
            cache.put(key, names);
        }
        return names;
    }

    /** 组织行的显示名（缓存表里取不到时退回 ID）。 */
    private String scopeName(Map<String, JSONObject> index, String id) {
        JSONObject row = index.get(OaonesUtil.s(id));
        return row == null ? OaonesUtil.s(id) : nameOf(row);
    }

    /** 多行文案：一行一个组织（页面按 {@code \n} 分行显示）。 */
    private String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (OaonesUtil.s(line).length() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /** 成员“没有找到部门”的口径文案。 */
    private String nodeptText(int direction, int policy, String deptName, String deptId) {
        if (policy == OaonesConst.SYNC_NODEPT_CREATE) {
            return "新增部门";
        }
        if (policy == OaonesConst.SYNC_NODEPT_DEFAULT_DEPT) {
            if (OaonesConst.SYNC_DEFAULT_TENANT.equals(deptId)) {
                return "默认部门：租户本身";
            }
            return "默认部门「" + (deptName.length() == 0 ? deptId : deptName) + "」";
        }
        return "不同步";
    }

    /** 平台文案：0 企业微信 / 1 钉钉 / 2 飞书。 */
    private String platformText(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return "钉钉";
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return "飞书";
        }
        return "企业微信";
    }

    /** 入参 type 归一（只认 0/1/2，其它值按企微处理）。 */
    private int platformType(String type) {
        int value = OaonesUtil.i(type, OaonesConst.TYPE_WECHAT);
        if (value != OaonesConst.TYPE_DINGTALK && value != OaonesConst.TYPE_FEISHU) {
            return OaonesConst.TYPE_WECHAT;
        }
        return value;
    }

    /** 入参 type 的空值形态：空串表示不过滤。 */
    private Integer typeValue(String type) {
        String text = OaonesUtil.s(type).trim();
        return text.length() == 0 ? null : Integer.valueOf(platformType(text));
    }

    /** 入参 direction 的过滤值：空 / all（不指定方向）时返回 null，表示不过滤。 */
    private Integer directionFilter(String direction) {
        int value = directionValue(direction);
        return value < 0 ? null : Integer.valueOf(value);
    }

    /** 入参 direction 归一：-1 表示不合法（保存 / 执行时据此报错）。 */
    private int directionValue(String direction) {
        String text = OaonesUtil.s(direction).trim();
        if (text.length() == 0 || "all".equalsIgnoreCase(text)) {
            return -1;
        }
        int value = OaonesUtil.i(text, -1);
        return (value == OaonesConst.DIR_OA_TO_WX || value == OaonesConst.DIR_WX_TO_OA) ? value : -1;
    }
}
