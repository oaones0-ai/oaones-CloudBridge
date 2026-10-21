package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.api.dingtalk.DingTalkApi;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dto.dingtalk.SyncPlan;
import com.customization.oaones.appsyn.dto.dingtalk.SyncPlanItem;
import com.customization.oaones.appsyn.dto.dingtalk.SyncScope;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.impl.SyncPlanServiceImpl;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.dingtalk.DeptBindService;
import com.customization.oaones.appsyn.service.dingtalk.SyncLogService;
import com.customization.oaones.appsyn.service.dingtalk.SyncScopeService;
import com.customization.oaones.appsyn.service.dingtalk.SyncService;
import com.customization.oaones.appsyn.service.dingtalk.SubBindService;
import com.customization.oaones.appsyn.service.dingtalk.UserBindService;
import com.customization.oaones.appsyn.service.dingtalk.UserTmpService;
import com.customization.oaones.appsyn.service.OaService;
import weaver.general.BaseBean;
import weaver.general.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 双向同步实现。
 *
 * <p>两条入口并存：</p>
 * <ul>
 *   <li>{@link #run(String, int, String)}：原来的“全租户全量同步”，计划任务与其它调用方沿用；</li>
 *   <li>{@link #preview(JSONObject, String)} / {@link #runScoped(JSONObject, String)}：
 *       同步中心页“先选 OA 分部/部门 + 钉钉部门（含下级），预览确认后再同步”的按范围同步，
 *       逐条部门/人员写同步日志明细。</li>
 * </ul>
 */
public class SyncServiceImpl implements SyncService {

    /** 预览弹窗里每类清单最多回传的条数（页面只用于列名字，不用于写库）。 */
    private static final int PREVIEW_LIST_LIMIT = 200;
    /** 单次同步最多记录的明细条数（防止超大数据量时日志表被写爆）。 */
    private static final int LOG_ITEM_LIMIT = 5000;
    /** 提示文案里每类最多列出的名称个数。 */
    private static final int MESSAGE_NAME_LIMIT = 10;

    private final BaseBean log = new BaseBean();

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    private UserBindService userBindService() {
        return new UserBindServiceImpl();
    }

    private UserTmpService userTmpService() {
        return new UserTmpServiceImpl();
    }

    private DeptBindService deptBindService() {
        return new DeptBindServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    private SyncLogService syncLogService() {
        return new SyncLogServiceImpl();
    }

    private SyncScopeService syncScopeService() {
        return new SyncScopeServiceImpl();
    }

    private SubBindService subBindService() {
        return new SubBindServiceImpl();
    }

    /* ===============================================================
     * 一、原“全租户全量同步”（计划任务 / 兼容入口）——行为不变
     * =============================================================== */

    @Override
    public JSONObject run(String cfgId, int direction, String operatorId) {
        OaonesLog.step("开始同步 cfgId=" + cfgId + " direction=" + direction
                + " operatorId=" + operatorId);
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            return OaonesUtil.fail("钉钉租户配置不存在: id=" + cfgId);
        }
        if (Util.getIntValue(OaonesUtil.s(cfg.getString("sync_enable")), 0) == 0) {
            return OaonesUtil.fail("钉钉租户[" + OaonesUtil.s(cfg.getString("corp_name"))
                    + "]未启用同步");
        }
        try {
            JSONObject result = syncOne(cfg, direction, operatorId);
            result.put("code", 0);
            result.put("msg", "同步完成");
            OaonesLog.step("同步完成 cfgId=" + cfgId + " direction=" + direction
                    + " total=" + result.getIntValue("total")
                    + " success=" + result.getIntValue("success")
                    + " skip=" + result.getIntValue("skip")
                    + " fail=" + result.getIntValue("fail")
                    + " runStatus=" + result.getIntValue("run_status"));
            return result;
        } catch (Exception e) {
            OaonesLog.error("同步失败 cfgId=" + cfgId + " direction=" + direction, e);
            log.writeLog("oaones sync error cfgId=" + cfgId + " dir=" + direction, e);
            return OaonesUtil.fail("同步失败: " + e.getMessage());
        }
    }

    @Override
    public void runAllForCron() {
        JSONArray cfgs = configService().list();
        // oaones.properties: dd.cron.direction 0=双向, 1=OA->钉钉, 2=钉钉->OA
        //   （未配置时沿用 cron.direction；指定租户用 dd.cron.cfg_id，避免与企微的 cron.corpid 互相干扰）
        int cronDir = OaonesProps.ddCronDirection();
        int cronCfgId = OaonesProps.ddCronCfgId();
        for (int i = 0; i < cfgs.size(); i++) {
            JSONObject cfg = cfgs.getJSONObject(i);
            int id = Util.getIntValue(OaonesUtil.s(cfg.getString("id")), 0);
            if (cronCfgId > 0 && id != cronCfgId) {
                continue;
            }
            if (Util.getIntValue(OaonesUtil.s(cfg.getString("sync_enable")), 0) != 1) {
                continue;
            }
            if (cronDir == 0 || cronDir == 1) {
                run(String.valueOf(id), OaonesConst.DIR_OA_TO_WX, "0");
            }
            if (cronDir == 0 || cronDir == 2) {
                run(String.valueOf(id), OaonesConst.DIR_WX_TO_OA, "0");
            }
        }
    }

    private JSONObject syncOne(JSONObject cfg, int direction, String operatorId) throws Exception {
        String cfgId = OaonesUtil.s(cfg.getString("id"));
        String cfgName = OaonesUtil.s(cfg.getString("corp_name"));
        DingTalkApi api = configService().getClient(cfgId);
        // 同步中心“同步设置”里该租户该方向<b>实际生效</b>的口径（成员没有找到部门时怎么处理）：
        // 租户自己配了就用租户的，没配就用“全局默认配置”（cfgid = 0）；
        // 两者都没有时为 null，按默认约束执行（不自动创建部门、只回写基础信息）
        JSONObject plan = new SyncPlanServiceImpl().effective(OaonesConst.TYPE_DINGTALK, cfgId, direction);
        String start = OaonesUtil.now();
        JSONObject summary;
        if (direction == OaonesConst.DIR_OA_TO_WX) {
            summary = syncOaToWx(cfgId, api, plan);
        } else if (direction == OaonesConst.DIR_WX_TO_OA) {
            summary = syncWxToOa(cfgId, api, plan);
        } else {
            throw new IllegalArgumentException("不支持的同步方向: " + direction);
        }
        String end = OaonesUtil.now();
        int total = summary.getIntValue("total");
        int success = summary.getIntValue("success");
        int fail = summary.getIntValue("fail");
        int skip = summary.getIntValue("skip");
        // run_status：0 成功 / 1 部分成功 / 2 失败（序号从 0 开始）
        int runStatus = fail == 0 ? 0 : (success > 0 ? 1 : 2);

        JSONObject logRow = new JSONObject();
        logRow.put("cfg_id", cfgId);
        logRow.put("corp_name", cfgName);
        logRow.put("direction", String.valueOf(direction));
        logRow.put("total_count", String.valueOf(total));
        logRow.put("success_count", String.valueOf(success));
        logRow.put("fail_count", String.valueOf(fail));
        logRow.put("skip_count", String.valueOf(skip));
        logRow.put("start_time", start);
        logRow.put("end_time", end);
        logRow.put("run_status", String.valueOf(runStatus));
        logRow.put("detail", buildDetail(summary));
        // 本次同步统计（页面“同步结果 → 详情”按它展示“x 个部门 x 个员工，其中 x 个没有找到部门”）
        logRow.put("dept_count", String.valueOf(summary.getIntValue("deptCount")));
        logRow.put("user_count", String.valueOf(summary.getIntValue("userCount")));
        logRow.put("nodept_count", String.valueOf(summary.getIntValue("nodeptCount")));
        logRow.put("nodept_dept", OaonesUtil.s(summary.getString("nodeptDept")));
        logRow.put("create_by", operatorId);
        logRow.put("create_time", end);
        int logId = syncLogService().add(logRow);

        JSONObject result = new JSONObject();
        result.put("cfg_id", cfgId);
        result.put("cfg_name", cfgName);
        result.put("direction", direction);
        result.put("total", total);
        result.put("success", success);
        result.put("fail", fail);
        result.put("skip", skip);
        result.put("start_time", start);
        result.put("end_time", end);
        result.put("run_status", runStatus);
        result.put("log_id", logId);
        result.put("dept_count", summary.getIntValue("deptCount"));
        result.put("user_count", summary.getIntValue("userCount"));
        result.put("nodept_count", summary.getIntValue("nodeptCount"));
        result.put("nodept_dept", OaonesUtil.s(summary.getString("nodeptDept")));
        result.put("messages", summary.getJSONArray("messages"));
        return result;
    }

    /**
     * OA -> 钉钉：仅同步已建立人员绑定且部门已绑定的成员。
     * 未建立部门绑定 / 钉钉部门不存在时一律跳过，绝不自动创建部门。
     */
    private JSONObject syncOaToWx(String cfgId, DingTalkApi api, JSONObject plan) throws Exception {
        JSONObject summary = newSyncSummary();
        Map<String, JSONObject> wxDeptMap = wxDeptMap(api);
        Map<String, JSONObject> oaDeptBindMap = new HashMap<String, JSONObject>();
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            oaDeptBindMap.put(OaonesUtil.s(bind.getString("oa_dept_id")), bind);
        }
        // 部门名称同步：只更新“已绑定且钉钉侧仍存在”的部门，未绑定/已删除一律不自动创建
        OaService oaService = oaService();
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            String oaDeptId = OaonesUtil.s(bind.getString("oa_dept_id"));
            String wxDeptId = OaonesUtil.s(bind.getString("wx_dept_id"));
            JSONObject oaDept = oaService.getDepartment(oaDeptId);
            if (oaDept == null) {
                addInfoMessage(summary, "[dept] OA部门[id=" + oaDeptId + "]不存在/已删除，跳过，不自动创建钉钉部门");
                continue;
            }
            if (!wxDeptMap.containsKey(wxDeptId)) {
                addInfoMessage(summary, "[dept] OA部门[" + OaonesUtil.s(oaDept.getString("name"))
                        + "]对应钉钉部门[id=" + wxDeptId + "]已不存在，按约束不自动创建");
                continue;
            }
            String oaName = OaonesUtil.s(oaDept.getString("name"));
            String wxName = OaonesUtil.s(wxDeptMap.get(wxDeptId).getString("name"));
            if (oaName.length() > 0 && !oaName.equals(wxName)) {
                api.updateDepartment(Long.parseLong(wxDeptId), oaName, null, null);
                summary.put("deptCount", summary.getIntValue("deptCount") + 1);
                addInfoMessage(summary, "[dept] 更新钉钉部门名称 " + wxDeptId + " -> " + oaName);
            }
        }
        JSONArray rows = userBindService().listBoundRows(cfgId);
        // 多对一：同一钉钉成员可能被多个 OA 人员绑定，本方向按绑定记录逐条写入钉钉（以最后一条为准），
        // 这里把被多个 OA 人员绑定的钉钉成员提前写进同步明细，便于排查“成员信息被覆盖”的情况。
        Map<String, Integer> wxBindCount = new HashMap<String, Integer>();
        for (int i = 0; i < rows.size(); i++) {
            String countWxUserId = OaonesUtil.s(rows.getJSONObject(i).getString("wx_user_id"));
            if (countWxUserId.length() == 0) {
                continue;
            }
            Integer existedCount = wxBindCount.get(countWxUserId);
            wxBindCount.put(countWxUserId, existedCount == null ? 1 : existedCount + 1);
        }
        for (Map.Entry<String, Integer> wxCount : wxBindCount.entrySet()) {
            if (wxCount.getValue() != null && wxCount.getValue().intValue() > 1) {
                addInfoMessage(summary, "[user] 钉钉成员 " + wxCount.getKey() + " 被 "
                        + wxCount.getValue() + " 个 OA 人员绑定，OA→钉钉同步按绑定逐条写入（以最后一条为准）");
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            summary.put("total", summary.getIntValue("total") + 1);
            summary.put("userCount", summary.getIntValue("userCount") + 1);
            String oaUserId = OaonesUtil.s(row.getString("oa_user_id"));
            String wxUserId = OaonesUtil.s(row.getString("wx_user_id"));
            String oaDeptId = OaonesUtil.s(row.getString("oa_dept_id"));
            try {
                // oa_canceled 由 hrmresource.status 推导（E9 标准表没有 canceled 列）：>=4 视为离职/解聘
                if ("1".equals(OaonesUtil.s(row.getString("oa_canceled")))) {
                    addMessage(summary, "fail", "OA人员[id=" + oaUserId
                            + "]已离职/解聘（status≥4），跳过");
                    continue;
                }
                if (wxUserId.length() == 0) {
                    addMessage(summary, "skip", "OA人员[id=" + oaUserId + "]未绑定钉钉userid，跳过");
                    continue;
                }
                // 成员“没有找到部门”（所在部门未建立绑定 / 目标侧部门已不存在）：按同步设置处理
                // 0 不同步（默认，保持历史约束：不自动创建部门，该成员本次跳过）
                // 1 直接挂在租户下（落到本租户配置的 root 节点，见 syncPlanRootDeptId）
                String noDeptReason = "";
                JSONObject deptBind = oaDeptBindMap.get(oaDeptId);
                String wxDeptId = "";
                if (deptBind == null) {
                    noDeptReason = "OA部门[id=" + oaDeptId + "]未建立部门绑定，按约束不自动创建钉钉部门";
                } else {
                    wxDeptId = OaonesUtil.s(deptBind.getString("wx_dept_id"));
                    if (!wxDeptMap.containsKey(wxDeptId)) {
                        noDeptReason = "钉钉部门[id=" + wxDeptId + "]在钉钉侧不存在，按约束不自动创建";
                    }
                }
                if (noDeptReason.length() > 0) {
                    int noDeptPolicy = OaonesUtil.i(OaonesUtil.s(plan == null ? "" : plan.getString("nodept_policy")),
                            OaonesConst.SYNC_NODEPT_SKIP);
                    if (noDeptPolicy == OaonesConst.SYNC_NODEPT_ATTACH_TENANT) {
                        wxDeptId = syncPlanRootDeptId(cfgId);
                        String rootName = wxDeptNameOf(wxDeptMap, wxDeptId);
                        summary.put("nodeptCount", summary.getIntValue("nodeptCount") + 1);
                        summary.put("nodeptDept", rootName);
                        addInfoMessage(summary, "[user] " + noDeptReason + "，按同步设置[直接挂在租户下]："
                                + row.getString("oa_name") + " 落到根部门「" + rootName + "」");
                    } else {
                        addMessage(summary, "skip", noDeptReason + "，跳过 " + row.getString("oa_name")
                                + "（同步设置：成员没有找到部门时不同步）");
                        continue;
                    }
                }
                String name = OaonesUtil.s(row.getString("oa_name"));
                String mobile = OaonesUtil.s(row.getString("oa_mobile"));
                String email = OaonesUtil.s(row.getString("oa_email"));
                if (name.length() == 0) {
                    addMessage(summary, "fail", "OA人员[id=" + oaUserId + "]姓名为空，无法同步");
                    continue;
                }
                JSONObject user = newWxUser(wxUserId, name, mobile, email, wxDeptId);
                if (!api.userExists(wxUserId)) {
                    api.createUser(user);
                    addMessage(summary, "success", "新建钉钉成员 " + wxUserId + "(" + name + ")");
                } else {
                    api.updateUser(user);
                    addMessage(summary, "success", "更新钉钉成员 " + wxUserId + "(" + name + ")");
                }
                userBindService().markSynced(cfgId, oaUserId, OaonesConst.DIR_OA_TO_WX);
            } catch (Exception e) {
                addMessage(summary, "fail", "OA人员[id=" + oaUserId + "→" + wxUserId + "]同步异常: " + e.getMessage());
                log.writeLog("oaones sync user fail oaUserId=" + oaUserId + " wxUserId=" + wxUserId, e);
            }
        }
        return summary;
    }

    /**
     * 钉钉 -> OA：拉取钉钉成员，仅回写已绑定人员；
     * 成员所在钉钉部门未绑定 OA 部门时仅同步姓名/手机/邮箱，不调整 OA 部门。
     */
    private JSONObject syncWxToOa(String cfgId, DingTalkApi api, JSONObject plan) throws Exception {
        JSONObject summary = newSyncSummary();
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在");
        }
        int rootDept = Util.getIntValue(OaonesUtil.s(cfg.getString("root_dept_id")), 1);
        JSONArray wxUsers = api.listUsers(rootDept, true);
        // OA → 钉钉为“多对一”：同一个钉钉成员可能被多个 OA 人员绑定（多个 OA 账户对应同一个钉钉账户），
        // 因此按钉钉成员 userid 收集全部绑定，回写时逐个 OA 人员处理。
        Map<String, List<JSONObject>> bindsByWxUser = new HashMap<String, List<JSONObject>>();
        JSONArray rows = userBindService().listBoundRows(cfgId);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String bindWxUserId = OaonesUtil.s(row.getString("wx_user_id"));
            if (bindWxUserId.length() == 0) {
                continue;
            }
            List<JSONObject> oneWxBinds = bindsByWxUser.get(bindWxUserId);
            if (oneWxBinds == null) {
                oneWxBinds = new ArrayList<JSONObject>();
                bindsByWxUser.put(bindWxUserId, oneWxBinds);
            }
            oneWxBinds.add(row);
        }
        Map<String, JSONObject> wxDeptBindMap = new HashMap<String, JSONObject>();
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            wxDeptBindMap.put(OaonesUtil.s(bind.getString("wx_dept_id")), bind);
        }
        OaService oaService = oaService();
        for (int i = 0; i < wxUsers.size(); i++) {
            JSONObject wx = wxUsers.getJSONObject(i);
            String wxUserId = OaonesUtil.s(wx.getString("userid"));
            List<JSONObject> binds = bindsByWxUser.get(wxUserId);
            if (binds == null) {
                continue;
            }
            for (int b = 0; b < binds.size(); b++) {
                String oaUserId = OaonesUtil.s(binds.get(b).getString("oa_user_id"));
                summary.put("total", summary.getIntValue("total") + 1);
                writeBackToOa(summary, oaService, wxDeptBindMap, cfgId, wx, wxUserId, oaUserId, plan);
            }
        }
        return summary;
    }

    /**
     * 钉钉 → OA 单人回写：把钉钉成员的基础信息回写到其绑定的 OA 人员，
     * 钉钉部门已绑定 OA 部门时一并调整 OA 人员部门。
     *
     * <p>多对一时同一个钉钉成员会对应多个 OA 人员，逐个 OA 人员调用本方法。</p>
     */
    private void writeBackToOa(JSONObject summary, OaService oaService,
                               Map<String, JSONObject> wxDeptBindMap, String cfgId,
                               JSONObject wx, String wxUserId, String oaUserId, JSONObject plan) {
        try {
            summary.put("userCount", summary.getIntValue("userCount") + 1);
            JSONObject oaUser = oaService.getUser(oaUserId);
            if (oaUser == null) {
                addMessage(summary, "skip", "钉钉成员 " + wxUserId + " 的绑定 OA 人员[id=" + oaUserId
                        + "]不存在，不自动新建 OA 人员");
                return;
            }
            String wxName = OaonesUtil.s(wx.getString("name"));
            String mobile = OaonesUtil.s(wx.getString("mobile"));
            String email = OaonesUtil.s(wx.getString("email"));
            String updateName = wxName.length() == 0 ? null : wxName;
            String updateMobile = mobile.length() == 0 ? null : mobile;
            String updateEmail = email.length() == 0 ? null : email;
            String deptId = null;
            String subCompanyId = null;
            JSONArray wxDepts = wx.getJSONArray("department");
            if (wxDepts != null) {
                for (int d = 0; d < wxDepts.size(); d++) {
                    String wxDeptId = String.valueOf(wxDepts.getLongValue(d));
                    JSONObject deptBind = wxDeptBindMap.get(wxDeptId);
                    if (deptBind != null) {
                        deptId = OaonesUtil.s(deptBind.getString("oa_dept_id"));
                        JSONObject oaDept = oaService.getDepartment(deptId);
                        if (oaDept != null) {
                            subCompanyId = OaonesUtil.s(oaDept.getString("subcompanyid1"));
                        }
                        break;
                    }
                }
            }
            if (deptId == null && wxDepts != null && wxDepts.size() > 0) {
                // 成员所在平台部门没有绑定 OA 部门（部门找不到）：按同步设置处理
                // 配了“默认部门”就挂到该部门下；没配则保持原口径（只回写基础信息、不动 OA 部门）
                String defaultDeptId = OaonesUtil.s(plan == null ? "" : plan.getString("default_dept_id"));
                JSONObject defaultDept = defaultDeptId.length() == 0 ? null : oaService.getDepartment(defaultDeptId);
                summary.put("nodeptCount", summary.getIntValue("nodeptCount") + 1);
                if (defaultDept != null) {
                    deptId = defaultDeptId;
                    subCompanyId = OaonesUtil.s(defaultDept.getString("subcompanyid1"));
                    String defaultName = OaonesUtil.s(defaultDept.getString("name"));
                    summary.put("nodeptDept", defaultName);
                    addInfoMessage(summary, "[user] 钉钉成员 " + wxUserId + "（" + wxName
                            + "）所在钉钉部门未绑定 OA 部门，按同步设置挂到默认部门「" + defaultName + "」");
                } else {
                    addMessage(summary, "skip", "钉钉成员 " + wxUserId + " 所在钉钉部门未绑定 OA 部门，"
                            + "仅回写人员基础信息、不调整 OA 部门（同步设置未指定默认部门）");
                }
            }
            if (deptId != null) {
                summary.put("deptCount", summary.getIntValue("deptCount") + 1);
            }
            oaService.updateUserBasic(oaUserId, updateName, updateMobile, updateEmail, deptId, subCompanyId);
            userBindService().markSynced(cfgId, oaUserId, OaonesConst.DIR_WX_TO_OA);
            addMessage(summary, "success", "回写 OA 人员 " + oaUserId + "(" + wxName + ")");
        } catch (Exception e) {
            addMessage(summary, "fail", "钉钉成员 " + wxUserId + " 回写异常: " + e.getMessage());
            log.writeLog("oaones sync user fail wxUserId=" + wxUserId, e);
        }
    }

    /* ===============================================================
     * 二、同步中心“按范围同步”：预览 + 执行
     * =============================================================== */

    @Override
    public JSONObject preview(JSONObject params, String operatorId) {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        OaonesLog.step("同步预览 cfgId=" + cfgId
                + " direction=" + OaonesUtil.s(params.getString("direction"))
                + " oaSubIds=" + OaonesUtil.s(params.getString("oa_sub_ids"))
                + " oaDeptIds=" + OaonesUtil.s(params.getString("oa_dept_ids"))
                + " wxDeptId=" + OaonesUtil.s(params.getString("wx_dept_id")));
        try {
            SyncPlan plan = buildPlan(params);
            return OaonesUtil.ok(planJson(plan));
        } catch (Exception e) {
            OaonesLog.error("同步预览失败 cfgId=" + cfgId, e);
            log.writeLog("oaones sync preview error cfgId=" + cfgId, e);
            return OaonesUtil.fail(e.getMessage() == null ? "同步预览失败" : e.getMessage());
        }
    }

    @Override
    public JSONObject runScoped(JSONObject params, String operatorId) {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        OaonesLog.step("开始按范围同步 cfgId=" + cfgId
                + " direction=" + OaonesUtil.s(params.getString("direction"))
                + " operatorId=" + operatorId);
        SyncPlan plan;
        try {
            plan = buildPlan(params);
        } catch (Exception e) {
            OaonesLog.error("按范围同步失败（构建同步计划） cfgId=" + cfgId, e);
            log.writeLog("oaones sync scoped error cfgId=" + cfgId, e);
            return OaonesUtil.fail(e.getMessage() == null ? "同步失败" : e.getMessage());
        }
        SyncScope scope = plan.getScope();
        int direction = scope.getDirection();
        String start = OaonesUtil.now();
        JSONObject stats = direction == OaonesConst.DIR_OA_TO_WX
                ? executeOaToWx(plan, operatorId)
                : executeWxToOa(plan, operatorId);
        String end = OaonesUtil.now();

        int success = stats.getIntValue("success");
        int skip = stats.getIntValue("skip");
        int fail = stats.getIntValue("fail");
        int total = success + skip + fail;
        int runStatus = fail == 0 ? 0 : (success > 0 ? 1 : 2);

        // 汇总日志 + 逐条明细（一行一个部门 / 一行一个人员）
        JSONObject logRow = new JSONObject();
        logRow.put("cfg_id", cfgId);
        logRow.put("corp_name", OaonesUtil.s(scope.getCfg().getString("corp_name")));
        logRow.put("direction", String.valueOf(direction));
        logRow.put("total_count", String.valueOf(total));
        logRow.put("success_count", String.valueOf(success));
        logRow.put("fail_count", String.valueOf(fail));
        logRow.put("skip_count", String.valueOf(skip));
        logRow.put("start_time", start);
        logRow.put("end_time", end);
        logRow.put("run_status", String.valueOf(runStatus));
        logRow.put("detail", planDetailText(plan, stats, start, end));
        logRow.put("create_by", operatorId);
        logRow.put("create_time", end);
        int logId = syncLogService().add(logRow);
        int itemCount = 0;
        if (logId > 0) {
            itemCount = syncLogService().addItems(logId,
                    planItemsJson(plan.getItems(), cfgId, direction, LOG_ITEM_LIMIT), operatorId);
        }
        OaonesLog.step("按范围同步完成 cfgId=" + cfgId + " direction=" + direction
                + " 日志ID=" + logId + " 明细条数=" + itemCount
                + " 成功=" + success + " 跳过=" + skip + " 失败=" + fail);

        JSONObject data = planJson(plan);
        data.put("log_id", logId);
        data.put("total", total);
        data.put("success", success);
        data.put("skip", skip);
        data.put("fail", fail);
        data.put("run_status", runStatus);
        data.put("start_time", start);
        data.put("end_time", end);
        data.put("detail_count", itemCount);
        data.put("messages", messagesOf(plan, 100));
        return OaonesUtil.ok(data);
    }

    /** 构建同步计划（预览与执行共用，保证“弹窗里看到的”就是“实际执行的”）。 */
    private SyncPlan buildPlan(JSONObject params) throws Exception {
        String cfgId = OaonesUtil.s(params.getString("cfg_id"));
        if (cfgId.length() == 0) {
            throw new RuntimeException("请先选择钉钉租户");
        }
        int direction = Util.getIntValue(OaonesUtil.s(params.getString("direction")), 0);
        if (direction != OaonesConst.DIR_OA_TO_WX && direction != OaonesConst.DIR_WX_TO_OA) {
            throw new RuntimeException("同步方向参数不正确");
        }
        JSONObject cfg = configService().get(cfgId);
        if (cfg == null) {
            throw new RuntimeException("钉钉租户配置不存在: id=" + cfgId);
        }
        if (Util.getIntValue(OaonesUtil.s(cfg.getString("sync_enable")), 0) == 0) {
            throw new RuntimeException("钉钉租户[" + OaonesUtil.s(cfg.getString("corp_name"))
                    + "]未启用同步");
        }
        String oaSubIds = OaonesUtil.s(params.getString("oa_sub_ids"));
        String oaDeptIds = OaonesUtil.s(params.getString("oa_dept_ids"));
        if (direction == OaonesConst.DIR_OA_TO_WX
                && oaSubIds.trim().length() == 0 && oaDeptIds.trim().length() == 0) {
            throw new RuntimeException("请先选择要同步的 OA 分部或 OA 部门");
        }
        SyncScope scope = syncScopeService().resolve(cfgId, direction, oaSubIds, oaDeptIds,
                OaonesUtil.s(params.getString("wx_dept_id")));
        if (direction == OaonesConst.DIR_OA_TO_WX && scope.getOaDepts().isEmpty()) {
            throw new RuntimeException("所选 OA 分部/部门下没有可同步的 OA 部门，请重新选择同步范围");
        }
        // “A 同步到 B”的前提：两侧都没有既有的绑定关系（否则先人工解绑，或用“完全干净”的分部/部门）；
        // 例外：同步设置（计划任务）在保存时已就“范围内（含下级）已有绑定关系”向用户确认过，
        // 用户选择强制同步后这里带 force=1 直接把这条映射关系写入（不再拦截）。
        if (!isForce(params)) {
            assertCleanScope(scope);
        }
        DingTalkApi api = configService().getClient(cfgId);
        return direction == OaonesConst.DIR_OA_TO_WX
                ? buildOaToWxPlan(scope, api)
                : buildWxToOaPlan(scope, api);
    }

    /* ---------------------------------------------------------------
     * 2.1 OA → 钉钉：OA 分部/部门（含下级）→ 钉钉落点部门（含下级）
     * --------------------------------------------------------------- */

    /**
     * 是否“强制同步”：同步设置（计划任务 / 立即执行一次）保存时，用户已就
     * “范围内（含下级）已有部门绑定或成员绑定”确认过强制同步，执行时带上 force=1。
     */
    private boolean isForce(JSONObject params) {
        String force = OaonesUtil.s(params.getString("force")).trim();
        return "1".equals(force) || "true".equalsIgnoreCase(force);
    }

    /**
     * “A 同步到 B”的前置校验：<b>A（来源范围）与 B（落点）都必须没有任何绑定关系</b>。
     *
     * <ul>
     *   <li>OA → 钉钉：A = 所选 OA 分部 / 部门（含下级）——不得已有部门绑定，所选分部不得已有分部绑定；
     *       B = 钉钉落点部门（非空时）——不得已被任何 OA 部门绑定；
     *       若 A 含分部，还要保证该钉钉租户没有被其它分部占用（分部 ⇄ 租户是一对一）。</li>
     *   <li>钉钉 → OA：A = 所选钉钉部门（含下级）——不得已有部门绑定；
     *       B = OA 落点部门（选了落点部门时）——不得已被绑定；
     *       只选落点分部（没选落点分部下的落点部门）时不校验 B——
     *       落点只是“新建部门的挂靠根/同名匹配范围”，本次新增的部门都是新对象。</li>
     * </ul>
     *
     * <p>不满足时统一提示：无法同步，请选择完全干净的分部/部门进行同步（并列出冲突对象便于排查）。</p>
     */
    private void assertCleanScope(SyncScope scope) {
        String cfgId = scope.getCfgId();
        boolean oaToWx = scope.getDirection() == OaonesConst.DIR_OA_TO_WX;
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        List<String> conflicts = new ArrayList<String>();

        if (oaToWx) {
            // A 侧：范围内的 OA 部门不得已有部门绑定
            for (int i = 0; i < deptBinds.size(); i++) {
                JSONObject bind = deptBinds.getJSONObject(i);
                String oaDeptId = OaonesUtil.s(bind.getString("oa_dept_id"));
                if (scope.oaDeptInScope(oaDeptId)) {
                    conflicts.add("OA 部门「" + oaDeptName(oaDeptId) + "」(ID " + oaDeptId
                            + ") 已绑定钉钉部门 " + OaonesUtil.s(bind.getString("wx_dept_id")));
                }
            }
            // A 侧：所选分部不得已有分部绑定；A 含分部时该租户也不能被其它分部占用
            Set<String> selectedSubs = new HashSet<String>();
            JSONArray subBinds = subBindService().listEnabled(cfgId);
            for (int i = 0; i < scope.getOaSubs().size(); i++) {
                String subId = OaonesUtil.s(scope.getOaSubs().get(i).getString("id"));
                selectedSubs.add(subId);
                // 分部侧一对一：这里的绑定不分租户（可能绑在别的租户上），必须查“该分部的现有绑定”
                Integer boundCfg = subBindService().cfgIdOfSub(subId);
                if (boundCfg != null && boundCfg.intValue() > 0) {
                    conflicts.add("OA 分部「" + subLabel(scope, subId) + "」(ID " + subId
                            + ") 已绑定钉钉租户 " + boundCfg);
                }
            }
            if (!selectedSubs.isEmpty()) {
                for (int j = 0; j < subBinds.size(); j++) {
                    String boundSub = OaonesUtil.s(subBinds.getJSONObject(j).getString("oa_sub_id"));
                    if (!selectedSubs.contains(boundSub)) {
                        conflicts.add("钉钉租户已被 OA 分部「" + subLabel(scope, boundSub)
                                + "」(ID " + boundSub + ") 绑定（一个租户只能被一个分部绑定）");
                    }
                }
            }
            // B 侧：钉钉落点部门不得已被其它 OA 部门绑定（落点为空 = root 时不作为绑定对象）
            String landing = scope.getWxDeptId();
            if (landing.length() > 0) {
                for (int i = 0; i < deptBinds.size(); i++) {
                    JSONObject bind = deptBinds.getJSONObject(i);
                    if (landing.equals(OaonesUtil.s(bind.getString("wx_dept_id")))) {
                        conflicts.add("钉钉落点部门 " + landing + "（" + wxDeptNameById(scope, landing) + "）已被 OA 部门「"
                                + oaDeptName(OaonesUtil.s(bind.getString("oa_dept_id"))) + "」绑定");
                    }
                }
            }
        } else {
            // A 侧：钉钉范围（含下级）内的钉钉部门不得已有部门绑定
            for (int i = 0; i < deptBinds.size(); i++) {
                JSONObject bind = deptBinds.getJSONObject(i);
                String wxDeptId = OaonesUtil.s(bind.getString("wx_dept_id"));
                if (scope.wxDeptInScope(wxDeptId)) {
                    conflicts.add("钉钉部门 " + wxDeptId + "（" + wxDeptNameById(scope, wxDeptId)
                            + "）已被 OA 部门「" + oaDeptName(OaonesUtil.s(bind.getString("oa_dept_id")))
                            + "」绑定");
                }
            }
            // B 侧：OA 落点部门不得已被绑定（没选落点部门时不校验：落点只选分部时新建部门都是新对象）
            for (int i = 0; i < scope.getOaDeptsSelected().size(); i++) {
                String oaDeptId = OaonesUtil.s(scope.getOaDeptsSelected().get(i).getString("id"));
                for (int j = 0; j < deptBinds.size(); j++) {
                    JSONObject bind = deptBinds.getJSONObject(j);
                    if (oaDeptId.equals(OaonesUtil.s(bind.getString("oa_dept_id")))) {
                        conflicts.add("OA 落点部门「" + oaDeptName(oaDeptId) + "」(ID " + oaDeptId
                                + ") 已绑定钉钉部门 " + OaonesUtil.s(bind.getString("wx_dept_id")));
                        break;
                    }
                }
            }
        }
        if (!conflicts.isEmpty()) {
            StringBuilder sb = new StringBuilder("无法同步，请选择完全干净的分部/部门进行同步（已存在绑定：");
            for (int i = 0; i < conflicts.size() && i < 10; i++) {
                if (i > 0) {
                    sb.append("；");
                }
                sb.append(conflicts.get(i));
            }
            if (conflicts.size() > 10) {
                sb.append("；等 ").append(conflicts.size()).append(" 项");
            }
            sb.append("）");
            throw new RuntimeException(sb.toString());
        }
    }

    /** OA 分部名称（用于提示文案）。 */
    private String subLabel(SyncScope scope, String subId) {
        for (int i = 0; i < scope.getOaSubs().size(); i++) {
            JSONObject sub = scope.getOaSubs().get(i);
            if (subId.equals(OaonesUtil.s(sub.getString("id")))) {
                return OaonesUtil.s(sub.getString("name"));
            }
        }
        JSONObject sub = oaService().getSubCompany(subId);
        return sub == null ? "" : OaonesUtil.s(sub.getString("name"));
    }

    private SyncPlan buildOaToWxPlan(SyncScope scope, DingTalkApi api) throws Exception {
        String cfgId = scope.getCfgId();
        SyncPlan plan = new SyncPlan();
        plan.setScope(scope);

        Map<String, JSONObject> wxDeptMap = wxDeptMap(api);
        if (!wxDeptMap.containsKey(scope.getWxDeptId())) {
            // root 节点（钉钉根部门 1）常常不在“部门列表”里，补一个占位节点保证落点可用
            JSONObject root = new JSONObject();
            root.put("id", Util.getIntValue(scope.getWxDeptId(), 1));
            root.put("name", "root 节点");
            root.put("parentid", 0);
            wxDeptMap.put(scope.getWxDeptId(), root);
        }
        int rootDept = Util.getIntValue(OaonesUtil.s(scope.getCfg().getString("root_dept_id")), 1);
        Map<String, JSONObject> wxUserMap = wxUserMap(api, rootDept);

        // 部门绑定：OA 部门 → 钉钉部门
        Map<String, String> bindWxOfOaDept = new HashMap<String, String>();
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            bindWxOfOaDept.put(OaonesUtil.s(bind.getString("oa_dept_id")),
                    OaonesUtil.s(bind.getString("wx_dept_id")));
        }
        // 人员绑定与免绑定
        Map<String, String> bindWxOfOaUser = new HashMap<String, String>();
        JSONArray userBinds = userBindService().listBoundRows(cfgId);
        for (int i = 0; i < userBinds.size(); i++) {
            JSONObject bind = userBinds.getJSONObject(i);
            String wxUserId = OaonesUtil.s(bind.getString("wx_user_id"));
            if (wxUserId.length() > 0) {
                bindWxOfOaUser.put(OaonesUtil.s(bind.getString("oa_user_id")), wxUserId);
            }
        }
        Set<String> oaNoBind = oaNoBindIds(cfgId);

        // A 侧选中的 OA 分部：钉钉里只有部门概念，分部在钉钉侧落成一个部门（挂在落点下），
        // 该分部的下级部门挂在这个部门下面；同步完成后写分部绑定（含该分部对应的钉钉部门）。
        Map<String, String> plannedWxOfSub = new HashMap<String, String>();
        for (int i = 0; i < scope.getOaSubs().size(); i++) {
            JSONObject sub = scope.getOaSubs().get(i);
            String subId = OaonesUtil.s(sub.getString("id"));
            SyncPlanItem subItem = new SyncPlanItem();
            subItem.setItemType(OaonesConst.SYNC_ITEM_SUB);
            subItem.setOp(OaonesConst.SYNC_OP_CREATE);
            subItem.setOaId(subId);
            subItem.setOaName(OaonesUtil.s(sub.getString("name")));
            subItem.setParentId(scope.getWxDeptId());
            subItem.setParentName(wxDeptName(wxDeptMap, scope.getWxDeptId()));
            subItem.setMessage("钉钉里只有部门概念：在钉钉「" + subItem.getParentName() + "」下新建部门「"
                    + subItem.getOaName() + "」代表该 OA 分部，并写入分部绑定");
            plan.addItem(subItem);
            plannedWxOfSub.put(subId, "");
        }

        // 部门计划（父部门先处理；新建父部门的钉钉ID在执行阶段回填）
        Map<String, String> plannedWxOfOaDept = new HashMap<String, String>();
        List<JSONObject> oaDepts = scope.getOaDepts();
        for (int i = 0; i < oaDepts.size(); i++) {
            JSONObject dept = oaDepts.get(i);
            String oaDeptId = OaonesUtil.s(dept.getString("id"));
            String oaDeptName = OaonesUtil.s(dept.getString("name"));
            String oaParentId = OaonesUtil.s(dept.getString("parent"));
            String oaSubId = OaonesUtil.s(dept.getString("subcompanyid1"));
            String boundWxDeptId = bindWxOfOaDept.get(oaDeptId);
            boolean boundExists = boundWxDeptId != null && boundWxDeptId.length() > 0
                    && wxDeptMap.containsKey(boundWxDeptId);
            String parentWxId;
            String parentRef = "";
            boolean parentInThisRun = plannedWxOfOaDept.containsKey(oaParentId);
            if (parentInThisRun) {
                // 父部门本次一起同步：执行阶段按“本次已创建的钉钉部门”解析
                parentWxId = "";
                parentRef = "dept";
            } else if (plannedWxOfSub.containsKey(oaSubId)) {
                // 该部门所属分部也是本次同步对象：顶层部门挂在“分部对应的钉钉部门”下
                parentWxId = "";
                parentRef = "sub";
            } else {
                // 其余顶层部门（含直接选的部门）统一挂在同步落点下
                parentWxId = scope.getWxDeptId();
            }

            SyncPlanItem item = new SyncPlanItem();
            item.setItemType(OaonesConst.SYNC_ITEM_DEPT);
            item.setOaId(oaDeptId);
            item.setOaName(oaDeptName);
            item.setSrcParentId(oaParentId);
            item.setOaSubId(oaSubId);
            item.setParentRef(parentRef);
            item.setParentId(parentWxId);
            if (parentWxId.length() > 0) {
                item.setParentName(wxDeptName(wxDeptMap, parentWxId));
            } else if ("sub".equals(parentRef)) {
                item.setParentName("分部「" + subLabel(scope, oaSubId) + "」对应的钉钉部门（本次新建）");
            } else if (parentInThisRun) {
                // 父部门本次一起新建：先用 OA 父部门名称占位，执行阶段换成真实钉钉部门
                item.setParentName(oaDeptName(oaParentId) + "（本次新建）");
            } else {
                item.setParentName("保持原挂靠位置");
            }
            if (boundExists) {
                item.setOp(OaonesConst.SYNC_OP_UPDATE);
                item.setWxId(boundWxDeptId);
                item.setWxName(OaonesUtil.s(wxDeptMap.get(boundWxDeptId).getString("name")));
                item.setMessage("更新钉钉部门名称/挂靠");
                plannedWxOfOaDept.put(oaDeptId, boundWxDeptId);
            } else {
                item.setOp(OaonesConst.SYNC_OP_CREATE);
                item.setMessage("在钉钉「" + item.getParentName() + "」下新建部门");
                plannedWxOfOaDept.put(oaDeptId, "");
            }
            plan.addItem(item);
        }

        // 人员计划：范围内 OA 在职人员（含下级部门）
        JSONArray oaUsers = oaService().listUsers("");
        for (int i = 0; i < oaUsers.size(); i++) {
            JSONObject oaUser = oaUsers.getJSONObject(i);
            String oaUserId = OaonesUtil.s(oaUser.getString("id"));
            String oaUserName = OaonesUtil.s(oaUser.getString("lastname"));
            String oaDeptId = OaonesUtil.s(oaUser.getString("departmentid"));
            if (!plannedWxOfOaDept.containsKey(oaDeptId)) {
                continue;
            }
            SyncPlanItem item = new SyncPlanItem();
            item.setItemType(OaonesConst.SYNC_ITEM_USER);
            item.setOaId(oaUserId);
            item.setOaName(oaUserName);
            item.setSrcParentId(oaDeptId);
            String deptName = OaonesUtil.s(oaUser.getString("departmentname"));
            if (deptName.length() == 0) {
                deptName = oaDeptName(oaDeptId);
            }
            item.setParentName(deptName);
            if (oaNoBind.contains(oaUserId)) {
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("已标记免绑定，不参与同步");
                plan.addItem(item);
                continue;
            }
            if (oaUserName.length() == 0) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("OA 人员姓名为空，无法同步到钉钉");
                plan.addItem(item);
                continue;
            }
            String wxUserId;
            try {
                wxUserId = bindWxOfOaUser.get(oaUserId);
                if (wxUserId == null || wxUserId.length() == 0) {
                    wxUserId = userBindService().resolveWxUserId(cfgId, oaUserId);
                }
            } catch (Exception e) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage(e.getMessage());
                plan.addItem(item);
                continue;
            }
            item.setWxId(wxUserId);
            item.setWxName(oaUserName);
            if (wxUserMap.containsKey(wxUserId)) {
                item.setOp(OaonesConst.SYNC_OP_UPDATE);
                item.setMessage("更新钉钉成员资料与所属部门（钉钉部门由 OA 部门「" + deptName + "」对应）");
            } else {
                item.setOp(OaonesConst.SYNC_OP_CREATE);
                item.setMessage("新建钉钉成员并建立人员绑定（钉钉部门由 OA 部门「" + deptName + "」对应）");
            }
            plan.addItem(item);
        }
        return plan;
    }

    /* ---------------------------------------------------------------
     * 2.2 钉钉 → OA：钉钉部门（含下级）→ OA 分部/部门（含下级）
     * --------------------------------------------------------------- */

    private SyncPlan buildWxToOaPlan(SyncScope scope, DingTalkApi api) throws Exception {
        String cfgId = scope.getCfgId();
        SyncPlan plan = new SyncPlan();
        plan.setScope(scope);

        String landingOaDeptId = landingOaDept(scope);
        String landingOaSubId = landingOaSub(scope);
        List<JSONObject> wxDepts = scope.getWxDepts();
        String rangeRoot = scope.getWxDeptId();

        // 部门绑定：两侧互相反查（钉钉部门 → OA 部门 / OA 部门 → 钉钉部门）
        Map<String, String> bindOaOfWxDept = new HashMap<String, String>();
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            String oaDeptId = OaonesUtil.s(bind.getString("oa_dept_id"));
            String wxDeptId = OaonesUtil.s(bind.getString("wx_dept_id"));
            bindOaOfWxDept.put(wxDeptId, oaDeptId);
        }
        // 未绑定的钉钉部门按名称匹配已有 OA 部门：落了落点部门则只在落点部门子树下匹配；
        // 只落了落点分部则在该分部（含下级分部）的部门里匹配；
        // 两者都没有（历史数据）则在整个 OA 组织里按同名匹配，且不再新建部门
        Map<String, String> oaDeptIdByName = oaDeptIdByName(landingOaDeptId, landingOaSubId);

        // 范围根节点：已绑定 OA 部门则用它作为挂靠根，否则用本次 OA 落点
        String rootOaDeptId = bindOaOfWxDept.get(rangeRoot);
        if (rootOaDeptId == null || rootOaDeptId.length() == 0) {
            rootOaDeptId = landingOaDeptId;
        }
        Map<String, String> plannedOaOfWxDept = new HashMap<String, String>();
        plannedOaOfWxDept.put(rangeRoot, rootOaDeptId);

        // 绑定 A 与 B：A = 钉钉范围根部门、B = OA 落点部门（前提是 A 没有任何既有绑定，见 assertCleanScope）
        if (landingOaDeptId.length() > 0 && !rangeRoot.equals(landingOaDeptId)) {
            SyncPlanItem bindItem = new SyncPlanItem();
            bindItem.setItemType(OaonesConst.SYNC_ITEM_DEPT);
            bindItem.setOp(OaonesConst.SYNC_OP_UPDATE);
            bindItem.setOaId(landingOaDeptId);
            bindItem.setOaName(oaDeptName(landingOaDeptId));
            bindItem.setWxId(rangeRoot);
            bindItem.setWxName(wxDeptNameById(scope, rangeRoot));
            bindItem.setParentRef("bindRoot");
            bindItem.setMessage("把钉钉范围根部门「" + bindItem.getWxName() + "」与 OA 落点部门「"
                    + bindItem.getOaName() + "」建立部门绑定，其下级部门挂在落点部门下");
            plan.addItem(bindItem);
        }

        for (int i = 0; i < wxDepts.size(); i++) {
            JSONObject wxDept = wxDepts.get(i);
            String wxDeptId = OaonesUtil.s(wxDept.getString("id"));
            // 范围根节点自身不新建（它只是本次同步的起点，已有绑定关系保持不动）
            if (wxDeptId.equals(rangeRoot)) {
                continue;
            }
            String wxDeptName = OaonesUtil.s(wxDept.getString("name"));
            String wxParentId = OaonesUtil.s(wxDept.getString("parentid"));
            String boundOaDeptId = bindOaOfWxDept.get(wxDeptId);
            // 绑定里的 OA 部门已被删除时按“未绑定”处理（重新按名称匹配或新建）
            if (boundOaDeptId != null && boundOaDeptId.length() > 0
                    && oaService().getDepartment(boundOaDeptId) == null) {
                boundOaDeptId = "";
            }
            String matchedOaDeptId = oaDeptIdByName.get(wxDeptName.trim());
            // 目标父节点：
            //   父钉钉部门在本次范围内且不是范围根 → 它对应的 OA 部门本次才确定（parentRef=dept，
            //     执行阶段按“本次已处理映射”解析，见 resolveParentOa）；
            //   父钉钉部门是范围根（或不在本次范围内）→ 挂到本次落点：选了落点部门就挂在落点部门下；
            //     只选落点分部（没选落点部门）则在该分部下新建顶级部门（parentRef=sub）
            String parentOa = "";
            String parentRef = "";
            String parentName = "";
            if (plannedOaOfWxDept.containsKey(wxParentId) && !wxParentId.equals(rangeRoot)) {
                parentRef = "dept";
                parentName = "钉钉部门「" + wxDeptNameById(scope, wxParentId) + "」（本次同步）";
            } else if (landingOaDeptId.length() > 0) {
                parentOa = landingOaDeptId;
                parentName = oaDeptName(parentOa);
            } else if (landingOaSubId.length() > 0) {
                parentRef = "sub";
                parentName = subLabel(scope, landingOaSubId);
            }

            SyncPlanItem item = new SyncPlanItem();
            item.setItemType(OaonesConst.SYNC_ITEM_DEPT);
            item.setWxId(wxDeptId);
            item.setWxName(wxDeptName);
            item.setSrcParentId(wxParentId);
            item.setParentRef(parentRef);
            item.setParentId(parentOa);
            item.setParentName(parentName);
            item.setOaSubId(landingOaSubId);
            if (boundOaDeptId != null && boundOaDeptId.length() > 0) {
                item.setOp(OaonesConst.SYNC_OP_UPDATE);
                item.setOaId(boundOaDeptId);
                item.setOaName(oaDeptName(boundOaDeptId));
                item.setMessage("更新 OA 部门名称，并按本次落点调整挂靠位置（钉钉部门已建立部门绑定）");
                plannedOaOfWxDept.put(wxDeptId, boundOaDeptId);
            } else if (matchedOaDeptId != null && matchedOaDeptId.length() > 0) {
                item.setOp(OaonesConst.SYNC_OP_UPDATE);
                item.setOaId(matchedOaDeptId);
                item.setOaName(oaDeptName(matchedOaDeptId));
                item.setMessage("按部门名称匹配到落点范围内的 OA 部门，更新名称、按本次落点挂靠并建立部门绑定");
                plannedOaOfWxDept.put(wxDeptId, matchedOaDeptId);
            } else if ("sub".equals(parentRef)) {
                // 落点只选了分部：在该分部下新建顶级部门（supdepid=0、subcompanyid1=该分部）
                item.setOp(OaonesConst.SYNC_OP_CREATE);
                item.setMessage("在 OA 落点分部「" + parentName + "」下新建顶级部门并建立部门绑定");
                plannedOaOfWxDept.put(wxDeptId, "");
            } else if ("dept".equals(parentRef) || parentOa.length() > 0) {
                item.setOp(OaonesConst.SYNC_OP_CREATE);
                item.setMessage("在 OA「" + parentName + "」下新建部门并建立部门绑定");
                plannedOaOfWxDept.put(wxDeptId, "");
            } else {
                // 落点分部与落点部门都没选（历史数据）：不新建 OA 部门，只做“已绑定 / 同名匹配”
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("未指定 OA 落点分部/落点部门、OA 中也无同名部门：按“或为空”口径不新建"
                        + "（需要新建时请在第三行选择 OA 落点分部）");
                plannedOaOfWxDept.put(wxDeptId, "");
            }
            plan.addItem(item);
        }

        // 人员：钉钉侧范围内的成员（实时拉取；主部门在范围内）
        JSONArray wxUsers = api.listUsers(Util.getIntValue(rangeRoot, 1), true);
        Map<String, String> bindOaOfWxUser = new HashMap<String, String>();
        JSONArray userBinds = userBindService().listBoundRows(cfgId);
        for (int i = 0; i < userBinds.size(); i++) {
            JSONObject bind = userBinds.getJSONObject(i);
            String wxUserId = OaonesUtil.s(bind.getString("wx_user_id"));
            if (wxUserId.length() > 0) {
                bindOaOfWxUser.put(wxUserId, OaonesUtil.s(bind.getString("oa_user_id")));
            }
        }
        String corpId = OaonesUtil.s(scope.getCfg().getString("corp_id"));
        for (int i = 0; i < wxUsers.size(); i++) {
            JSONObject wx = wxUsers.getJSONObject(i);
            String wxUserId = OaonesUtil.s(wx.getString("userid"));
            String memberDeptId = mainWxDeptId(wx);
            if (memberDeptId.length() == 0 || !scope.wxDeptInScope(memberDeptId)) {
                continue;
            }
            String wxName = OaonesUtil.s(wx.getString("name"));
            SyncPlanItem item = new SyncPlanItem();
            item.setItemType(OaonesConst.SYNC_ITEM_USER);
            item.setWxId(wxUserId);
            item.setWxName(wxName);
            item.setSrcParentId(memberDeptId);
            item.setParentName(wxDeptNameById(scope, memberDeptId));
            if (userTmpService().isNoBind(corpId, wxUserId)) {
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("钉钉成员已标记免绑定，不参与同步");
                plan.addItem(item);
                continue;
            }
            if (wxName.length() == 0) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("钉钉成员姓名为空，无法同步到 OA");
                plan.addItem(item);
                continue;
            }
            String oaUserId = bindOaOfWxUser.get(wxUserId);
            if (oaUserId != null && oaUserId.length() > 0) {
                item.setOp(OaonesConst.SYNC_OP_UPDATE);
                item.setOaId(oaUserId);
                item.setOaName(oaUserName(oaUserId));
                item.setMessage("回写 OA 人员基础信息与所属部门（已建立人员绑定）");
                plan.addItem(item);
                continue;
            }
            String loginId = sanitizeAccount(wxUserId);
            if (loginId.length() == 0) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("钉钉成员ID 无法作为 OA 登录名，无法自动建号");
                plan.addItem(item);
                continue;
            }
            if (oaService().findUserIdByLoginId(loginId) != null) {
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("OA 已存在登录名为 " + loginId
                        + " 的账号，请先在“人员绑定”页手工建立绑定");
                plan.addItem(item);
                continue;
            }
            String mappedOaDept = plannedOaOfWxDept.get(memberDeptId);
            if (landingOaDeptId.length() == 0 && landingOaSubId.length() == 0
                    && (mappedOaDept == null || mappedOaDept.length() == 0)) {
                // 落点分部与落点部门都没选（历史数据）、该钉钉部门也没有匹配到 OA 部门：无法决定建号部门
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("未指定 OA 落点分部/落点部门，且所在钉钉部门没有对应的 OA 部门，不自动建号");
                plan.addItem(item);
                continue;
            }
            if (landingOaDeptId.length() == 0 && memberDeptId.equals(rangeRoot)) {
                // 落点只选了分部、人员直接挂在钉钉范围根上：没有对应的 OA 落点部门，无法决定建号部门
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("钉钉成员直接挂在钉钉范围根部门上、未指定 OA 落点部门，不自动建号"
                        + "（需要建号时请在第三行选择 OA 落点部门）");
                plan.addItem(item);
                continue;
            }
            item.setOp(OaonesConst.SYNC_OP_CREATE);
            item.setMessage("在 OA「" + item.getParentName() + "」下新建账号（登录名 "
                    + loginId + "）并建立人员绑定");
            plan.addItem(item);
        }
        return plan;
    }

    /**
     * 钉钉→OA 的 OA 落点部门（未建立绑定的钉钉部门挂靠到它下面）。
     *
     * <p>页面口径：OA 落点先选落点分部（{@link #landingOaSub(SyncScope)}，单选必选），
     * 再选落点部门（<b>只能选一个，也可以不选</b>）；
     * 只选分部时落点就是该分部（新建部门成为该分部的顶级部门，见
     * {@code OaService#createDepartment(String, String, String, String)}）。</p>
     */
    private String landingOaDept(SyncScope scope) {
        List<JSONObject> selected = scope.getOaDeptsSelected();
        if (!selected.isEmpty()) {
            return OaonesUtil.s(selected.get(0).getString("id"));
        }
        return "";
    }

    /**
     * 钉钉→OA 的 OA 落点分部（落点根）。
     *
     * <p>页面口径：<b>单选、必选</b>；取不到（历史数据）时为空串，
     * 此时落点只按落点部门处理（落点部门也没有＝不指定落点，只做绑定/同名匹配、不新建）。</p>
     */
    private String landingOaSub(SyncScope scope) {
        List<JSONObject> subs = scope.getOaSubs();
        return subs.isEmpty() ? "" : OaonesUtil.s(subs.get(0).getString("id"));
    }

    /**
     * 钉钉→OA 的落点文案：落点 = 落点分部（必选）+ 落点部门（非必选，只选分部时新建部门挂在该分部下）。
     *
     * <p>用于确认弹窗与同步日志汇总（预览清单与日志里的父节点由逐条明细给出）。</p>
     */
    private String oaLandingText(SyncScope scope) {
        List<JSONObject> subs = scope.getOaSubs();
        List<JSONObject> depts = scope.getOaDeptsSelected();
        if (subs.isEmpty() && depts.isEmpty()) {
            return "不指定 OA 落点（只在已有 OA 部门中按绑定/同名匹配，不新建部门与账号）";
        }
        StringBuilder sb = new StringBuilder();
        if (!subs.isEmpty()) {
            sb.append("分部「").append(OaonesUtil.s(subs.get(0).getString("name")))
                    .append("」（含下级分部与部门）");
        }
        if (!depts.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append("部门「").append(OaonesUtil.s(depts.get(0).getString("name")))
                    .append("」（含下级部门）");
        } else if (sb.length() > 0) {
            sb.append("，未指定落点部门（新建部门直接挂在该分部下，作为该分部的顶级部门）");
        }
        return sb.toString();
    }

    /**
     * 已有 OA 部门的“名称 → 部门ID”索引。
     *
     * @param landingOaDeptId OA 落点部门ID；非空时只在“落点部门 + 其下级部门”里匹配同名；
     * @param landingOaSubId  OA 落点分部ID；落点部门为空而它非空时，只在该分部（含下级分部）的部门里匹配同名；
     *                        两者都为空时在整个 OA 组织里匹配同名（不新建）
     */
    private Map<String, String> oaDeptIdByName(String landingOaDeptId, String landingOaSubId) {
        Map<String, String> map = new HashMap<String, String>();
        JSONArray rows = oaService().listDepartmentsFromCache();
        Set<String> scopeIds = null;
        if (landingOaDeptId != null && landingOaDeptId.length() > 0) {
            scopeIds = oaDeptSubtreeIds(landingOaDeptId, rows);
        } else if (landingOaSubId != null && landingOaSubId.length() > 0) {
            Set<String> subIds = oaSubtreeIds(landingOaSubId);
            scopeIds = new HashSet<String>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (subIds.contains(OaonesUtil.s(row.getString("subcompanyid1")))) {
                    scopeIds.add(OaonesUtil.s(row.getString("id")));
                }
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String id = OaonesUtil.s(row.getString("id"));
            if (scopeIds != null && !scopeIds.contains(id)) {
                continue;
            }
            String name = OaonesUtil.s(row.getString("name")).trim();
            if (name.length() > 0 && !map.containsKey(name)) {
                map.put(name, id);
            }
        }
        return map;
    }

    /** OA 分部子树（自身 + 全部下级分部）ID 集合。 */
    private Set<String> oaSubtreeIds(String rootSubId) {
        JSONArray rows = oaService().listSubCompaniesFromCache();
        Set<String> ids = new HashSet<String>();
        ids.add(rootSubId);
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
        return ids;
    }

    /** OA 部门子树（自身 + 全部下级部门）ID 集合。 */
    private Set<String> oaDeptSubtreeIds(String rootDeptId, JSONArray deptRows) {
        Set<String> ids = new HashSet<String>();
        ids.add(rootDeptId);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < deptRows.size(); i++) {
                JSONObject row = deptRows.getJSONObject(i);
                if (ids.contains(OaonesUtil.s(row.getString("parent")))
                        && ids.add(OaonesUtil.s(row.getString("id")))) {
                    grew = true;
                }
            }
        }
        return ids;
    }

    /* ---------------------------------------------------------------
     * 2.3 执行：OA → 钉钉
     * --------------------------------------------------------------- */

    private JSONObject executeOaToWx(SyncPlan plan, String operatorId) {
        SyncScope scope = plan.getScope();
        String cfgId = scope.getCfgId();
        DingTalkApi api = configService().getClient(cfgId);
        Map<String, JSONObject> wxDeptMap = safeWxDeptMap(api);
        // OA 部门 → 钉钉部门（本次同步后的实际映射：已绑定 + 本次新建）
        Map<String, String> liveWxOfOaDept = new HashMap<String, String>();
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            liveWxOfOaDept.put(OaonesUtil.s(bind.getString("oa_dept_id")),
                    OaonesUtil.s(bind.getString("wx_dept_id")));
        }
        List<SyncPlanItem> deptItems = plan.of(OaonesConst.SYNC_ITEM_DEPT);
        List<SyncPlanItem> userItems = plan.of(OaonesConst.SYNC_ITEM_USER);
        List<SyncPlanItem> subItems = plan.of(OaonesConst.SYNC_ITEM_SUB);
        // OA 分部 → 该分部在钉钉侧对应的部门（钉钉只有部门概念，分部落成一个部门）
        Map<String, String> liveWxOfSub = new HashMap<String, String>();

        // 0) 分部：先在钉钉落点下建出“代表该分部”的部门，并写入分部绑定（含该部门ID），
        //    分部的下级部门随后挂在这个部门下面
        for (int i = 0; i < subItems.size(); i++) {
            SyncPlanItem item = subItems.get(i);
            try {
                long newWxDeptId = api.createDepartment(item.getOaName(),
                        Util.getIntValue(scope.getWxDeptId(), 1), null);
                String newId = String.valueOf(newWxDeptId);
                item.setWxId(newId);
                item.setWxName(item.getOaName());
                liveWxOfSub.put(item.getOaId(), newId);
                saveSubBind(cfgId, item.getOaId(), newId, operatorId);
                item.setMessage("在钉钉「" + item.getParentName() + "」下新建部门「" + item.getOaName()
                        + "」（钉钉部门ID " + newId + "）代表该 OA 分部，并写入分部绑定");
            } catch (Exception e) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("分部同步失败: " + e.getMessage());
                log.writeLog("oaones scoped sync sub fail oaSubId=" + item.getOaId(), e);
            }
        }

        // 1) 部门：父部门先处理；新建后用真实钉钉部门ID补映射，供下级部门/人员挂靠
        for (int i = 0; i < deptItems.size(); i++) {
            SyncPlanItem item = deptItems.get(i);
            // 只负责建立“钉钉范围根部门 ⇄ OA 落点部门”绑定的行：不新建、不改名
            if ("bindRoot".equals(item.getParentRef())) {
                try {
                    saveDeptBind(cfgId, item.getOaId(), item.getWxId(),
                            wxParentOf(scope, item.getWxId()), operatorId);
                    item.setMessage("已建立部门绑定：OA 部门「" + item.getOaName() + "」 ⇄ 钉钉部门「"
                            + item.getWxName() + "」");
                } catch (Exception e) {
                    item.setOp(OaonesConst.SYNC_OP_FAIL);
                    item.setMessage("绑定失败: " + e.getMessage());
                    log.writeLog("oaones scoped sync bind root fail wxDeptId=" + item.getWxId(), e);
                }
                continue;
            }
            if (item.getOp() != OaonesConst.SYNC_OP_CREATE
                    && item.getOp() != OaonesConst.SYNC_OP_UPDATE) {
                continue;
            }
            String parentWxId = resolveParentWx(item, liveWxOfOaDept, liveWxOfSub, scope);
            item.setParentId(parentWxId);
            if (parentWxId.length() == 0) {
                if (item.getOp() == OaonesConst.SYNC_OP_UPDATE) {
                    // 已绑定部门：父部门本次解析不到 = 保持钉钉侧原挂靠位置
                    item.setParentName("保持原挂靠位置");
                } else {
                    // 新建部门必须知道父部门：父部门 / 所属分部对应的钉钉部门本次没同步成功 → 跳过
                    item.setOp(OaonesConst.SYNC_OP_SKIP);
                    item.setMessage("父部门（或所属分部对应的钉钉部门）本次未同步成功，跳过该部门");
                    continue;
                }
            } else {
                item.setParentName(wxDeptName(wxDeptMap, parentWxId));
            }
            try {
                if (item.getOp() == OaonesConst.SYNC_OP_CREATE) {
                    long newWxDeptId = api.createDepartment(item.getOaName(),
                            Util.getIntValue(parentWxId, 1), null);
                    String newId = String.valueOf(newWxDeptId);
                    item.setWxId(newId);
                    item.setWxName(item.getOaName());
                    saveDeptBind(cfgId, item.getOaId(), newId, parentWxId, operatorId);
                    liveWxOfOaDept.put(item.getOaId(), newId);
                    item.setMessage("在钉钉「" + item.getParentName() + "」下新建部门「"
                            + item.getOaName() + "」（钉钉部门ID " + newId + "），并写入部门绑定");
                } else {
                    // 父部门取不到（空串）时传 null：钉钉只更新名称、不动挂靠位置
                    Long parentKey = parentWxId.length() == 0 ? null
                            : Long.valueOf(Util.getIntValue(parentWxId, 0));
                    api.updateDepartment(Util.getIntValue(item.getWxId(), 0), item.getOaName(),
                            parentKey, null);
                    saveDeptBind(cfgId, item.getOaId(), item.getWxId(), parentWxId, operatorId);
                    liveWxOfOaDept.put(item.getOaId(), item.getWxId());
                    item.setMessage("更新钉钉部门 " + item.getWxId() + "「" + item.getOaName()
                            + "」，父部门「" + item.getParentName() + "」");
                }
            } catch (Exception e) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("部门同步失败: " + e.getMessage());
                log.writeLog("oaones scoped sync dept fail oaDeptId=" + item.getOaId(), e);
            }
        }

        // 2) 人员：先在钉钉建号/更新，再刷新人员快照，最后写人员绑定
        List<SyncPlanItem> writtenUsers = new ArrayList<SyncPlanItem>();
        for (int i = 0; i < userItems.size(); i++) {
            SyncPlanItem item = userItems.get(i);
            if (item.getOp() == OaonesConst.SYNC_OP_SKIP
                    || item.getOp() == OaonesConst.SYNC_OP_FAIL) {
                continue;
            }
            String wxDeptId = liveWxOfOaDept.get(item.getSrcParentId());
            if (wxDeptId == null || wxDeptId.length() == 0) {
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("OA 人员所属部门本次未建立钉钉部门，跳过人员同步");
                continue;
            }
            String deptName = wxDeptName(wxDeptMap, wxDeptId);
            item.setParentId(wxDeptId);
            item.setParentName(deptName);
            try {
                JSONObject oaUser = oaService().getUser(item.getOaId());
                String mobile = oaUser == null ? "" : OaonesUtil.s(oaUser.getString("mobile"));
                String email = oaUser == null ? "" : OaonesUtil.s(oaUser.getString("email"));
                String name = item.getOaName();
                JSONObject user = newWxUser(item.getWxId(), name, mobile, email, wxDeptId);
                if (item.getOp() == OaonesConst.SYNC_OP_CREATE) {
                    api.createUser(user);
                    item.setMessage("新建钉钉成员 " + item.getWxId() + "（" + name
                            + "），所属钉钉部门「" + deptName + "」");
                } else {
                    api.updateUser(user);
                    item.setMessage("更新钉钉成员 " + item.getWxId() + "（" + name
                            + "），所属钉钉部门「" + deptName + "」");
                }
                writtenUsers.add(item);
            } catch (Exception e) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("人员同步失败: " + e.getMessage());
                log.writeLog("oaones scoped sync user fail oaUserId=" + item.getOaId(), e);
            }
        }
        // 钉钉写成功后刷新人员快照（人员绑定落库时以快照为准），再逐条写绑定
        if (!writtenUsers.isEmpty()) {
            try {
                userTmpService().refreshByCfgId(cfgId);
            } catch (Exception e) {
                OaonesLog.error("刷新钉钉人员快照失败（钉钉侧已写入，人员绑定可能未落库）", e);
                log.writeLog("oaones scoped sync refresh user tmp fail cfgId=" + cfgId, e);
            }
            for (int i = 0; i < writtenUsers.size(); i++) {
                SyncPlanItem item = writtenUsers.get(i);
                try {
                    saveUserBind(cfgId, item.getOaId(), item.getWxId(), item.getParentId(), operatorId);
                    userBindService().markSynced(cfgId, item.getOaId(), OaonesConst.DIR_OA_TO_WX);
                    item.setMessage(item.getMessage() + "，并写入人员绑定");
                } catch (Exception e) {
                    item.setOp(OaonesConst.SYNC_OP_FAIL);
                    item.setMessage("钉钉成员已写入，但人员绑定失败: " + e.getMessage());
                    log.writeLog("oaones scoped sync user bind fail oaUserId=" + item.getOaId(), e);
                }
            }
        }
        return planStats(plan);
    }

    /* ---------------------------------------------------------------
     * 2.4 执行：钉钉 → OA
     * --------------------------------------------------------------- */

    private JSONObject executeWxToOa(SyncPlan plan, String operatorId) {
        SyncScope scope = plan.getScope();
        String cfgId = scope.getCfgId();
        // 钉钉部门 → OA 部门（本次同步后的实际映射）
        Map<String, String> liveOaOfWxDept = new HashMap<String, String>();
        JSONArray deptBinds = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < deptBinds.size(); i++) {
            JSONObject bind = deptBinds.getJSONObject(i);
            liveOaOfWxDept.put(OaonesUtil.s(bind.getString("wx_dept_id")),
                    OaonesUtil.s(bind.getString("oa_dept_id")));
        }
        String rangeRoot = scope.getWxDeptId();
        String rootOaDeptId = liveOaOfWxDept.get(rangeRoot);
        if (rootOaDeptId == null || rootOaDeptId.length() == 0) {
            rootOaDeptId = landingOaDept(scope);
        }
        liveOaOfWxDept.put(rangeRoot, rootOaDeptId);

        List<SyncPlanItem> deptItems = plan.of(OaonesConst.SYNC_ITEM_DEPT);
        List<SyncPlanItem> userItems = plan.of(OaonesConst.SYNC_ITEM_USER);
        String landingOaSubId = landingOaSub(scope);

        // 1) 部门：父部门先处理；新建 OA 部门后把真实ID补进映射
        for (int i = 0; i < deptItems.size(); i++) {
            SyncPlanItem item = deptItems.get(i);
            if (item.getOp() != OaonesConst.SYNC_OP_CREATE
                    && item.getOp() != OaonesConst.SYNC_OP_UPDATE) {
                continue;
            }
            String parentOaDeptId;
            // 落点 = OA 分部（没选落点部门）：新建部门直接成为该分部的顶级部门（supdepid=0）
            boolean atLandingSubTop = "sub".equals(item.getParentRef());
            if (atLandingSubTop) {
                parentOaDeptId = "";
            } else if ("dept".equals(item.getParentRef())) {
                // 父节点 = 上级钉钉部门本次对应的 OA 部门：本次没建/没匹配到时跳过，不悄悄挂到落点上
                String fromLive = liveOaOfWxDept.get(item.getSrcParentId());
                parentOaDeptId = fromLive == null ? "" : fromLive;
                if (item.getOp() == OaonesConst.SYNC_OP_CREATE && parentOaDeptId.length() == 0) {
                    item.setOp(OaonesConst.SYNC_OP_SKIP);
                    item.setMessage("上级钉钉部门本次未在 OA 同步成功，跳过该部门");
                    continue;
                }
            } else {
                parentOaDeptId = resolveParentOa(item, liveOaOfWxDept, rootOaDeptId);
            }
            item.setParentId(parentOaDeptId);
            item.setParentName(atLandingSubTop ? subLabel(scope, landingOaSubId) : oaDeptName(parentOaDeptId));
            try {
                if (item.getOp() == OaonesConst.SYNC_OP_CREATE) {
                    JSONObject created = atLandingSubTop
                            ? oaService().createDepartment(item.getWxName(), "", landingOaSubId, operatorId)
                            : oaService().createDepartment(item.getWxName(), parentOaDeptId, operatorId);
                    String newId = OaonesUtil.s(created.getString("id"));
                    item.setOaId(newId);
                    item.setOaName(OaonesUtil.s(created.getString("name")));
                    saveDeptBind(cfgId, newId, item.getWxId(), item.getParentId(), operatorId);
                    liveOaOfWxDept.put(item.getWxId(), newId);
                    item.setMessage(atLandingSubTop
                            ? "在 OA 落点分部「" + item.getParentName() + "」下新建顶级部门「"
                                    + item.getOaName() + "」（OA 部门ID " + newId + "），并写入部门绑定"
                            : "在 OA「" + item.getParentName() + "」下新建部门「"
                                    + item.getOaName() + "」（OA 部门ID " + newId + "），并写入部门绑定");
                } else {
                    // 命中已有 OA 部门（部门绑定 / 同名匹配）：先改名，再按本次落点把部门“挂”到位——
                    // 只 update 名称、位置不动会让“A 挂在 B 下面”落空（与 OA→钉钉 方向的重新挂靠口径一致）
                    oaService().updateDepartmentName(item.getOaId(), item.getWxName(), operatorId);
                    String tackText;
                    if ("dept".equals(item.getParentRef()) && parentOaDeptId.length() == 0) {
                        // 上级钉钉部门本次没同步成功：拿不到目标上级，保持原挂靠位置（不悄悄挂到落点上）
                        tackText = "，上级钉钉部门本次未同步成功，保持原挂靠位置";
                    } else if (parentOaDeptId.length() > 0 || landingOaSubId.length() > 0) {
                        boolean moved = oaService().moveDepartment(item.getOaId(), parentOaDeptId,
                                landingOaSubId, operatorId);
                        item.setParentId(parentOaDeptId);
                        item.setParentName(atLandingSubTop ? subLabel(scope, landingOaSubId)
                                : oaDeptName(parentOaDeptId));
                        tackText = moved ? "，并按本次落点挂靠到「" + item.getParentName() + "」下"
                                : "，已在本次落点的挂靠位置";
                    } else {
                        tackText = "，未指定 OA 落点，保持原挂靠位置";
                    }
                    saveDeptBind(cfgId, item.getOaId(), item.getWxId(), item.getParentId(), operatorId);
                    liveOaOfWxDept.put(item.getWxId(), item.getOaId());
                    item.setMessage("更新 OA 部门 " + item.getOaId() + "「" + item.getWxName()
                            + "」" + tackText + "，并写入部门绑定");
                }
            } catch (Exception e) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("部门同步失败: " + e.getMessage());
                log.writeLog("oaones scoped sync dept fail wxDeptId=" + item.getWxId(), e);
            }
        }

        // 2) 人员：已绑定 → 回写；未绑定 → 在对应 OA 部门下新建账号并建立绑定
        JSONArray wxUsers;
        try {
            wxUsers = configService().getClient(cfgId).listUsers(Util.getIntValue(rangeRoot, 1), true);
        } catch (Exception e) {
            throw new RuntimeException("读取钉钉成员失败: " + e.getMessage(), e);
        }
        // 人员绑定落库时以人员快照为准（userBindService.save 会校验“快照中状态正常”），
        // 这里按本次实时拉取结果刷新一次快照，避免成员刚加入企业、快照还没更新时绑定失败
        try {
            userTmpService().refreshByCfgId(cfgId);
        } catch (Exception e) {
            OaonesLog.error("刷新钉钉人员快照失败（人员绑定可能未落库）", e);
            log.writeLog("oaones scoped sync refresh user tmp fail cfgId=" + cfgId, e);
        }
        for (int i = 0; i < wxUsers.size(); i++) {
            JSONObject wx = wxUsers.getJSONObject(i);
            String wxUserId = OaonesUtil.s(wx.getString("userid"));
            SyncPlanItem item = findItem(userItems, wxUserId);
            if (item == null || item.getOp() == OaonesConst.SYNC_OP_SKIP
                    || item.getOp() == OaonesConst.SYNC_OP_FAIL) {
                continue;
            }
            String oaDeptId = liveOaOfWxDept.get(item.getSrcParentId());
            if (oaDeptId == null || oaDeptId.length() == 0) {
                oaDeptId = rootOaDeptId;
            }
            if (oaDeptId.length() == 0) {
                // 落点只选了分部（没选落点部门）且该钉钉部门本次没有对应的 OA 部门：无法决定人员所属部门
                item.setOp(OaonesConst.SYNC_OP_SKIP);
                item.setMessage("所在钉钉部门本次没有对应的 OA 部门（未指定 OA 落点部门或该部门本次未同步成功），"
                        + "跳过人员同步");
                continue;
            }
            item.setParentId(oaDeptId);
            item.setParentName(oaDeptName(oaDeptId));
            try {
                String name = OaonesUtil.s(wx.getString("name"));
                String mobile = OaonesUtil.s(wx.getString("mobile"));
                String email = OaonesUtil.s(wx.getString("email"));
                JSONObject oaDept = oaService().getDepartment(oaDeptId);
                String subCompanyId = oaDept == null ? "" : OaonesUtil.s(oaDept.getString("subcompanyid1"));
                if (item.getOp() == OaonesConst.SYNC_OP_UPDATE) {
                    oaService().updateUserBasic(item.getOaId(), name, mobile, email, oaDeptId, subCompanyId);
                    item.setMessage("回写 OA 人员 " + item.getOaId() + "（" + name
                            + "），所属 OA 部门「" + item.getParentName() + "」");
                } else {
                    String loginId = sanitizeAccount(wxUserId);
                    int newOaUserId = oaService().createUser(loginId, name, mobile, email,
                            oaDeptId, subCompanyId, null, operatorId);
                    item.setOaId(String.valueOf(newOaUserId));
                    item.setOaName(name);
                    item.setMessage("新建 OA 账号 " + loginId + "（" + name + "，OA 人员ID "
                            + newOaUserId + "），所属 OA 部门「" + item.getParentName() + "」");
                }
                saveUserBind(cfgId, item.getOaId(), wxUserId, item.getSrcParentId(), operatorId);
                userBindService().markSynced(cfgId, item.getOaId(), OaonesConst.DIR_WX_TO_OA);
                item.setMessage(item.getMessage() + "，并写入人员绑定");
            } catch (Exception e) {
                item.setOp(OaonesConst.SYNC_OP_FAIL);
                item.setMessage("人员同步失败: " + e.getMessage());
                log.writeLog("oaones scoped sync user fail wxUserId=" + wxUserId, e);
            }
        }
        return planStats(plan);
    }

    /* ---------------------------------------------------------------
     * 2.5 执行阶段的公共动作
     * --------------------------------------------------------------- */

    /**
     * 执行阶段解析钉钉侧父部门：
     * 计划里有值就用计划值；计划为空且是“新建部门”（父部门本次一起新建）时，
     * 从“本次已处理映射”里取父部门真实钉钉ID，仍取不到就挂到同步落点；
     * 已绑定部门的计划值为空表示“保持钉钉侧原挂靠位置”，这里返回空串由调用方传 null。
     */
    private String resolveParentWx(SyncPlanItem item, Map<String, String> liveWxOfOaDept,
                                   Map<String, String> liveWxOfSub, SyncScope scope) {
        if ("sub".equals(item.getParentRef())) {
            String subDept = liveWxOfSub.get(item.getOaSubId());
            // 分部对应的钉钉部门没建出来（分部那条失败）时返回空串：由调用方把下级部门记为跳过，
            // 而不是悄悄挂到落点上
            return subDept == null ? "" : subDept;
        }
        if ("dept".equals(item.getParentRef())) {
            String fromLive = liveWxOfOaDept.get(item.getSrcParentId());
            return fromLive == null ? "" : fromLive;
        }
        String planned = item.getParentId();
        if (planned != null && planned.length() > 0) {
            return planned;
        }
        if (item.getOp() == OaonesConst.SYNC_OP_UPDATE) {
            return "";
        }
        String fromLive = liveWxOfOaDept.get(item.getSrcParentId());
        if (fromLive != null && fromLive.length() > 0) {
            return fromLive;
        }
        return scope.getWxDeptId();
    }

    /** 目标侧父部门：优先计划值，其次“父钉钉部门本次对应的 OA 部门”，最后落点。 */
    private String resolveParentOa(SyncPlanItem item, Map<String, String> liveOaOfWxDept, String rootOaDeptId) {
        String planned = item.getParentId();
        if (planned != null && planned.length() > 0) {
            return planned;
        }
        String fromLive = liveOaOfWxDept.get(item.getSrcParentId());
        if (fromLive != null && fromLive.length() > 0) {
            return fromLive;
        }
        return rootOaDeptId;
    }

    /** 写入/刷新部门绑定（同一 OA 部门只保留一条，重新同步即更新）。 */
    private void saveDeptBind(String cfgId, String oaDeptId, String wxDeptId,
                              String parentWxDeptId, String operatorId) {
        JSONObject params = new JSONObject();
        params.put("cfg_id", cfgId);
        params.put("oa_dept_id", oaDeptId);
        params.put("wx_dept_id", wxDeptId);
        // 父部门本次取不到（已绑定部门保持原挂靠）时保留绑定里原来的钉钉父部门，避免被写成 0
        String parentId = parentWxDeptId == null ? "" : parentWxDeptId;
        if (parentId.length() == 0) {
            parentId = existingParentWxDept(cfgId, oaDeptId);
        }
        params.put("wx_parent_dept_id", parentId);
        params.put("remark", OaonesConst.SCOPE_SYNC_BIND_REMARK);
        deptBindService().save(params, operatorId);
    }

    /**
     * 写入分部绑定并登记“该分部在钉钉侧对应的部门”。
     *
     * <p>钉钉里只有部门概念：A = OA 分部时，本次在钉钉新建的“代表该分部的部门”会写进
     * {@code uf_oaones_sub_bind.wx_dept_id}，该分部下的部门挂在这个部门下面。</p>
     */
    private void saveSubBind(String cfgId, String oaSubId, String wxDeptId, String operatorId) {
        JSONObject params = new JSONObject();
        params.put("cfg_id", cfgId);
        params.put("oa_sub_id", oaSubId);
        params.put("remark", OaonesConst.SCOPE_SYNC_BIND_REMARK);
        subBindService().save(params, operatorId);
        subBindService().markWxDept(cfgId, oaSubId, wxDeptId, operatorId);
    }

    /** 部门绑定里已记录的钉钉父部门ID（取不到时返回 "0"）。 */
    private String existingParentWxDept(String cfgId, String oaDeptId) {
        JSONArray rows = deptBindService().listEnabled(cfgId);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (oaDeptId.equals(OaonesUtil.s(row.getString("oa_dept_id")))) {
                String parent = OaonesUtil.s(row.getString("wx_parent_dept_id"));
                return parent.length() == 0 ? "0" : parent;
            }
        }
        return "0";
    }

    /** 写入/刷新人员绑定（按范围同步固定为“系统绑定”，并写入同步原因）。 */
    private void saveUserBind(String cfgId, String oaUserId, String wxUserId,
                              String wxDeptId, String operatorId) throws Exception {
        JSONObject params = new JSONObject();
        params.put("cfg_id", cfgId);
        params.put("oa_user_id", oaUserId);
        params.put("wx_user_id", wxUserId);
        params.put("bind_source", String.valueOf(OaonesConst.BIND_SOURCE_SYSTEM));
        params.put("remark", OaonesConst.SCOPE_SYNC_BIND_REMARK);
        userBindService().save(params, operatorId);
    }

    /** 汇总：成功 = 已成功新增/更新；跳过、失败按明细统计。 */
    private JSONObject planStats(SyncPlan plan) {
        JSONObject stats = new JSONObject();
        int success = 0;
        int skip = 0;
        int fail = 0;
        List<SyncPlanItem> items = plan.getItems();
        for (int i = 0; i < items.size(); i++) {
            int op = items.get(i).getOp();
            if (op == OaonesConst.SYNC_OP_CREATE || op == OaonesConst.SYNC_OP_UPDATE) {
                success++;
            } else if (op == OaonesConst.SYNC_OP_SKIP) {
                skip++;
            } else {
                fail++;
            }
        }
        stats.put("success", success);
        stats.put("skip", skip);
        stats.put("fail", fail);
        return stats;
    }

    /* ---------------------------------------------------------------
     * 2.6 预览 / 结果 JSON 与日志文本
     * --------------------------------------------------------------- */

    /** 预览/结果 JSON：范围、统计、提示文案与八类清单（新增/更新/跳过/失败 × 部门/人员）。 */
    private JSONObject planJson(SyncPlan plan) {
        SyncScope scope = plan.getScope();
        JSONObject data = new JSONObject();
        data.put("direction", scope.getDirection());
        data.put("cfg_id", scope.getCfgId());
        data.put("cfg_name", OaonesUtil.s(scope.getCfg().getString("corp_name")));
        data.put("scope_text", scope.getScopeText());
        data.put("wx_scope_text", scope.getWxScopeText());
        data.put("oa_dept_count", scope.getOaDepts().size());
        data.put("wx_dept_count", scope.getWxDepts().size());
        data.put("message", scopeMessage(plan));

        JSONObject counts = new JSONObject();
        counts.put("dept_create", plan.deptCreateCount());
        counts.put("dept_update", plan.deptUpdateCount());
        counts.put("user_create", plan.userCreateCount());
        counts.put("user_update", plan.userUpdateCount());
        counts.put("dept_skip", plan.countDeptLike(OaonesConst.SYNC_OP_SKIP));
        counts.put("user_skip", plan.count(OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_SKIP));
        counts.put("dept_fail", plan.countDeptLike(OaonesConst.SYNC_OP_FAIL));
        counts.put("user_fail", plan.count(OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_FAIL));
        data.put("counts", counts);

        JSONObject lists = new JSONObject();
        lists.put("dept_create", itemsJsonDeptLike(plan, OaonesConst.SYNC_OP_CREATE));
        lists.put("dept_update", itemsJsonDeptLike(plan, OaonesConst.SYNC_OP_UPDATE));
        lists.put("user_create", itemsJson(plan, OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_CREATE));
        lists.put("user_update", itemsJson(plan, OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_UPDATE));
        lists.put("dept_skip", itemsJsonDeptLike(plan, OaonesConst.SYNC_OP_SKIP));
        lists.put("user_skip", itemsJson(plan, OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_SKIP));
        lists.put("dept_fail", itemsJsonDeptLike(plan, OaonesConst.SYNC_OP_FAIL));
        lists.put("user_fail", itemsJson(plan, OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_FAIL));
        data.put("lists", lists);
        return data;
    }

    /** “部门类”（部门 + 分部）明细清单：钉钉里只有部门概念，A = OA 分部时也会落成一个部门。 */
    private JSONArray itemsJsonDeptLike(SyncPlan plan, int op) {
        JSONArray arr = new JSONArray();
        List<SyncPlanItem> items = plan.getItems();
        for (int i = 0; i < items.size() && arr.size() < PREVIEW_LIST_LIMIT; i++) {
            SyncPlanItem item = items.get(i);
            if (!SyncPlan.isDeptLike(item.getItemType()) || item.getOp() != op) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("item_type", String.valueOf(item.getItemType()));
            row.put("oa_id", item.getOaId());
            row.put("oa_name", item.getOaName());
            row.put("wx_id", item.getWxId());
            row.put("wx_name", item.getWxName());
            row.put("parent_id", item.getParentId());
            row.put("parent_name", item.getParentName());
            row.put("message", item.getMessage());
            arr.add(row);
        }
        return arr;
    }

    private JSONArray itemsJson(SyncPlan plan, int itemType, int op) {
        JSONArray arr = new JSONArray();
        List<SyncPlanItem> items = plan.getItems();
        for (int i = 0; i < items.size() && arr.size() < PREVIEW_LIST_LIMIT; i++) {
            SyncPlanItem item = items.get(i);
            if (item.getItemType() != itemType || item.getOp() != op) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("oa_id", item.getOaId());
            row.put("oa_name", item.getOaName());
            row.put("wx_id", item.getWxId());
            row.put("wx_name", item.getWxName());
            row.put("parent_id", item.getParentId());
            row.put("parent_name", item.getParentName());
            row.put("message", item.getMessage());
            arr.add(row);
        }
        return arr;
    }

    /** 逐条说明（用于页面“同步结果”区域）。 */
    private JSONArray messagesOf(SyncPlan plan, int limit) {
        JSONArray arr = new JSONArray();
        List<SyncPlanItem> items = plan.getItems();
        for (int i = 0; i < items.size() && arr.size() < limit; i++) {
            SyncPlanItem item = items.get(i);
            String target = SyncPlan.isDeptLike(item.getItemType())
                    ? (item.getOaName() + (item.getWxName().length() > 0 ? " → 钉钉：" + item.getWxName() : ""))
                    : (item.getOaName() + "(" + item.getOaId() + ") / " + item.getWxName());
            arr.add("[" + OaonesConst.syncOpText(item.getOp()) + "]["
                    + OaonesConst.syncItemTypeText(item.getItemType()) + "] " + target
                    + "：" + item.getMessage());
        }
        return arr;
    }

    /** 同步日志明细表数据（一行一个部门 / 一行一个人员）。 */
    private JSONArray planItemsJson(List<SyncPlanItem> items, String cfgId, int direction, int limit) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < items.size() && arr.size() < limit; i++) {
            SyncPlanItem item = items.get(i);
            JSONObject row = new JSONObject();
            row.put("cfg_id", cfgId);
            row.put("direction", String.valueOf(direction));
            row.put("item_type", String.valueOf(item.getItemType()));
            row.put("op", String.valueOf(item.getOp()));
            row.put("oa_id", item.getOaId());
            row.put("oa_name", item.getOaName());
            row.put("wx_id", item.getWxId());
            row.put("wx_name", item.getWxName());
            row.put("parent_id", item.getParentId());
            row.put("parent_name", item.getParentName());
            row.put("seq", String.valueOf(item.getSeq()));
            row.put("message", item.getMessage());
            arr.add(row);
        }
        return arr;
    }

    /** 日志汇总的 detail 文本：范围 + 统计 + 前若干条失败原因（逐条明细在明细表里）。 */
    private String planDetailText(SyncPlan plan, JSONObject stats, String start, String end) {
        SyncScope scope = plan.getScope();
        StringBuilder sb = new StringBuilder();
        sb.append("同步方向：").append(scope.getDirection() == OaonesConst.DIR_OA_TO_WX
                ? "OA → 钉钉" : "钉钉 → OA").append('\n');
        if (scope.getDirection() == OaonesConst.DIR_WX_TO_OA) {
            // 钉钉→OA：来源是钉钉侧，OA 侧是落点（落点分部必选 + 落点部门非必选）
            sb.append("同步范围：").append(scope.getWxScopeText()).append('\n');
            sb.append("OA 落点：").append(oaLandingText(scope)).append('\n');
        } else {
            sb.append("同步范围：").append(scope.getScopeText()).append("（含下级数据）\n");
            sb.append("钉钉侧节点：").append(scope.getWxScopeText()).append('\n');
        }
        sb.append("开始时间：").append(start).append("  结束时间：").append(end).append('\n');
        sb.append("本次同步：更新部门 ").append(plan.deptUpdateCount())
                .append(" 个、更新人员 ").append(plan.userUpdateCount())
                .append(" 个、新增部门 ").append(plan.deptCreateCount())
                .append(" 个、新增人员 ").append(plan.userCreateCount())
                .append(" 个；跳过 ").append(stats.getIntValue("skip"))
                .append(" 条、失败 ").append(stats.getIntValue("fail")).append(" 条。\n");
        sb.append("逐条部门/人员明细见“同步明细”（uf_oaones_sync_log_item）。\n");
        List<SyncPlanItem> items = plan.getItems();
        int shown = 0;
        for (int i = 0; i < items.size() && shown < 30 && sb.length() < 3000; i++) {
            SyncPlanItem item = items.get(i);
            if (item.getOp() != OaonesConst.SYNC_OP_FAIL) {
                continue;
            }
            sb.append("[失败]").append(OaonesConst.syncItemTypeText(item.getItemType()))
                    .append(' ');
            if (SyncPlan.isDeptLike(item.getItemType())) {
                sb.append(item.getWxName().length() > 0 ? item.getWxName() : item.getOaName());
            } else {
                sb.append(item.getWxName().length() > 0 ? item.getWxName() : item.getWxId());
            }
            sb.append("：").append(item.getMessage()).append('\n');
            shown++;
        }
        return sb.toString();
    }

    /** 预览/确认文案（按“即将将…同步到…，本次同步将更新 x 个部门…请确认是否开始同步？”口径）。 */
    private String scopeMessage(SyncPlan plan) {
        SyncScope scope = plan.getScope();
        boolean oaToWx = scope.getDirection() == OaonesConst.DIR_OA_TO_WX;
        StringBuilder sb = new StringBuilder();
        // 钉钉→OA 时 OA 侧是落点：落点分部（必选）+ 落点部门（非必选）
        String oaLandingText = oaLandingText(scope);
        sb.append("即将将").append(oaToWx ? "OA数据（" : "钉钉数据（")
                .append(oaToWx ? scope.getScopeText() + "（含下级数据）" : scope.getWxScopeText())
                .append("）同步到").append(oaToWx ? "钉钉（落点：" : "OA（落点：")
                .append(oaToWx ? scope.getWxScopeText() : oaLandingText)
                .append("），本次同步将更新 ").append(plan.deptUpdateCount()).append(" 个部门");
        String deptUpdateNames = namesOf(plan, OaonesConst.SYNC_ITEM_DEPT, OaonesConst.SYNC_OP_UPDATE);
        if (deptUpdateNames.length() > 0) {
            sb.append("：").append(deptUpdateNames);
        }
        sb.append("，").append(plan.userUpdateCount()).append(" 个人员");
        String userUpdateNames = namesOf(plan, OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_UPDATE);
        if (userUpdateNames.length() > 0) {
            sb.append("：").append(userUpdateNames);
        }
        sb.append("；新增 ").append(plan.deptCreateCount()).append(" 个部门");
        String deptCreateNames = namesOf(plan, OaonesConst.SYNC_ITEM_DEPT, OaonesConst.SYNC_OP_CREATE);
        if (deptCreateNames.length() > 0) {
            sb.append("：").append(deptCreateNames);
        }
        sb.append("，").append(plan.userCreateCount()).append(" 个人");
        String userCreateNames = namesOf(plan, OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_CREATE);
        if (userCreateNames.length() > 0) {
            sb.append("：").append(userCreateNames);
        }
        int skipCount = plan.countDeptLike(OaonesConst.SYNC_OP_SKIP)
                + plan.count(OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_SKIP);
        int failCount = plan.countDeptLike(OaonesConst.SYNC_OP_FAIL)
                + plan.count(OaonesConst.SYNC_ITEM_USER, OaonesConst.SYNC_OP_FAIL);
        if (skipCount > 0) {
            sb.append("；另有 ").append(skipCount).append(" 条按规则跳过");
        }
        if (failCount > 0) {
            sb.append("；").append(failCount).append(" 条无法同步（见同步明细）");
        }
        sb.append("。请确认是否开始同步？");
        return sb.toString();
    }

    /** 某一类对象的名称串（最多 MESSAGE_NAME_LIMIT 个，超出标注条数）。 */
    private String namesOf(SyncPlan plan, int itemType, int op) {
        StringBuilder sb = new StringBuilder();
        List<SyncPlanItem> items = plan.getItems();
        // 传“部门”时把“分部”一起算上：钉钉里只有部门概念，A = OA 分部时也落成一个部门
        boolean deptLike = itemType == OaonesConst.SYNC_ITEM_DEPT;
        int match = deptLike ? plan.countDeptLike(op) : plan.count(itemType, op);
        int shown = 0;
        for (int i = 0; i < items.size() && shown < MESSAGE_NAME_LIMIT; i++) {
            SyncPlanItem item = items.get(i);
            boolean hit = deptLike ? SyncPlan.isDeptLike(item.getItemType())
                    : item.getItemType() == itemType;
            if (!hit || item.getOp() != op) {
                continue;
            }
            String name = deptLike
                    ? firstNonEmpty(item.getOaName(), item.getWxName())
                    : firstNonEmpty(item.getOaName(), item.getWxName(), item.getWxId());
            if (name.length() == 0) {
                continue;
            }
            if (shown > 0) {
                sb.append('、');
            }
            sb.append(name);
            shown++;
        }
        if (match > shown) {
            sb.append(" 等 ").append(match).append(" 个");
        }
        return sb.toString();
    }

    private String firstNonEmpty(String... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].length() > 0) {
                return values[i];
            }
        }
        return "";
    }

    /* ---------------------------------------------------------------
     * 2.7 读取工具
     * --------------------------------------------------------------- */

    /** 钉钉部门列表（失败时退化为空表，保证预览不因为一次接口异常整页失败）。 */
    private Map<String, JSONObject> safeWxDeptMap(DingTalkApi api) {
        try {
            return wxDeptMap(api);
        } catch (Exception e) {
            OaonesLog.error("读取钉钉部门列表失败（落点名称按ID展示）", e);
            return new HashMap<String, JSONObject>();
        }
    }

    private Map<String, JSONObject> wxDeptMap(DingTalkApi api) throws Exception {
        Map<String, JSONObject> map = new HashMap<String, JSONObject>();
        JSONArray depts = api.listDepartments();
        for (int i = 0; i < depts.size(); i++) {
            JSONObject d = depts.getJSONObject(i);
            map.put(String.valueOf(d.getLongValue("id")), d);
        }
        return map;
    }

    /** 钉钉成员 userid → 成员（一次拉取，避免逐个 userExists 造成大量接口调用）。 */
    private Map<String, JSONObject> wxUserMap(DingTalkApi api, long rootDept) throws Exception {
        Map<String, JSONObject> map = new HashMap<String, JSONObject>();
        JSONArray users = api.listUsers(rootDept, true);
        for (int i = 0; i < users.size(); i++) {
            JSONObject user = users.getJSONObject(i);
            map.put(OaonesUtil.s(user.getString("userid")), user);
        }
        return map;
    }

    private String wxDeptName(Map<String, JSONObject> wxDeptMap, String wxDeptId) {
        JSONObject dept = wxDeptMap.get(wxDeptId);
        if (dept == null) {
            return wxDeptId;
        }
        String name = OaonesUtil.s(dept.getString("name"));
        return name.length() == 0 ? wxDeptId : name;
    }

    private String wxDeptNameById(SyncScope scope, String wxDeptId) {
        List<JSONObject> rows = scope.getWxDepts();
        for (int i = 0; i < rows.size(); i++) {
            if (wxDeptId.equals(OaonesUtil.s(rows.get(i).getString("id")))) {
                String name = OaonesUtil.s(rows.get(i).getString("name"));
                return name.length() == 0 ? wxDeptId : name;
            }
        }
        return wxDeptId;
    }

    /** 钉钉部门在快照里的父部门ID（部门绑定需要带上，取不到按 0）。 */
    private String wxParentOf(SyncScope scope, String wxDeptId) {
        List<JSONObject> rows = scope.getWxDepts();
        for (int i = 0; i < rows.size(); i++) {
            if (wxDeptId.equals(OaonesUtil.s(rows.get(i).getString("id")))) {
                String parent = OaonesUtil.s(rows.get(i).getString("parentid"));
                return parent.length() == 0 ? "0" : parent;
            }
        }
        return "0";
    }

    private String oaDeptName(String oaDeptId) {
        if (oaDeptId == null || oaDeptId.length() == 0 || "0".equals(oaDeptId)) {
            return "";
        }
        String name = OaonesUtil.s(oaService().getDeptName(oaDeptId));
        return name.length() == 0 ? oaDeptId : name;
    }

    private String oaUserName(String oaUserId) {
        try {
            JSONObject user = oaService().getUser(oaUserId);
            return user == null ? "" : OaonesUtil.s(user.getString("lastname"));
        } catch (Exception e) {
            return "";
        }
    }

    /** 钉钉成员的主部门（接口返回 main_department，缺省取 department 的第一个）。 */
    private String mainWxDeptId(JSONObject wx) {
        if (wx.containsKey("main_department")) {
            return String.valueOf(wx.getLongValue("main_department"));
        }
        JSONArray depts = wx.getJSONArray("department");
        if (depts != null && depts.size() > 0) {
            return String.valueOf(depts.getLongValue(0));
        }
        return "";
    }

    /** 已标记“免绑定”的 OA 人员ID集合（bind_state = 0）。 */
    private Set<String> oaNoBindIds(String cfgId) {
        Set<String> ids = new HashSet<String>();
        JSONArray rows = userBindService().listOaNoBind(cfgId);
        for (int i = 0; i < rows.size(); i++) {
            ids.add(OaonesUtil.s(rows.getString(i)));
        }
        return ids;
    }

    private JSONObject newWxUser(String wxUserId, String name, String mobile, String email, String wxDeptId) {
        JSONArray deptArr = new JSONArray();
        deptArr.add(Util.getIntValue(wxDeptId, 1));
        JSONObject user = new JSONObject();
        user.put("userid", wxUserId);
        user.put("name", name);
        user.put("department", deptArr);
        if (mobile != null && mobile.length() > 0) {
            user.put("mobile", mobile);
        }
        if (email != null && email.length() > 0) {
            user.put("email", email);
        }
        return user;
    }

    private SyncPlanItem findItem(List<SyncPlanItem> items, String wxUserId) {
        for (int i = 0; i < items.size(); i++) {
            if (wxUserId.equals(items.get(i).getWxId())) {
                return items.get(i);
            }
        }
        return null;
    }

    /** 账号ID归一化：只保留钉钉/OA 账号允许的字符（字母数字与 _ - . @）。 */
    private String sanitizeAccount(String value) {
        String source = OaonesUtil.s(value).trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length() && sb.length() < 64; i++) {
            char c = source.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '@';
            if (keep) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /* ---------------------------------------------------------------
     * 2.8 原全量同步的汇总工具
     * --------------------------------------------------------------- */

    /** 同步设置[直接挂在租户下]：成员落到本租户配置的 root 节点（企微 / 钉钉取 root_dept_id）。 */
    private String syncPlanRootDeptId(String cfgId) {
        JSONObject cfg = configService().get(cfgId);
        String root = cfg == null ? "" : OaonesUtil.s(cfg.getString("root_dept_id"));
        return root.length() == 0 ? "1" : root;
    }

    /** 目标侧部门名称（从部门快照取；取不到时退化为“ID x”）。 */
    private String wxDeptNameOf(Map<String, JSONObject> wxDeptMap, String wxDeptId) {
        JSONObject dept = wxDeptMap.get(wxDeptId);
        String name = dept == null ? "" : OaonesUtil.s(dept.getString("name"));
        return name.length() == 0 ? ("ID " + wxDeptId) : name;
    }

    private JSONObject newSyncSummary() {
        JSONObject summary = new JSONObject();
        summary.put("total", 0);
        summary.put("success", 0);
        summary.put("fail", 0);
        summary.put("skip", 0);
        // 本次同步统计：部门（含分部）条数 / 人员条数 / 其中“没有找到部门”的人员条数 / 挂靠部门名
        summary.put("deptCount", 0);
        summary.put("userCount", 0);
        summary.put("nodeptCount", 0);
        summary.put("nodeptDept", "");
        summary.put("messages", new JSONArray());
        return summary;
    }

    private void addMessage(JSONObject summary, String type, String message) {
        OaonesLog.step("同步明细[" + type + "] " + message);
        summary.put(type, summary.getIntValue(type) + 1);
        JSONArray arr = summary.getJSONArray("messages");
        if (arr.size() < 100) {
            arr.add("[" + type + "] " + message);
        }
    }

    private void addInfoMessage(JSONObject summary, String message) {
        OaonesLog.step("同步信息 " + message);
        JSONArray arr = summary.getJSONArray("messages");
        if (arr.size() < 100) {
            arr.add(message);
        }
    }

    private String buildDetail(JSONObject summary) {
        JSONArray msgs = summary.getJSONArray("messages");
        if (msgs == null || msgs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < msgs.size(); i++) {
            if (sb.length() > 3900) {
                break;
            }
            sb.append(msgs.getString(i)).append('\n');
        }
        return sb.toString();
    }
}
