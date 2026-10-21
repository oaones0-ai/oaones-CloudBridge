package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.SyncPlanService;
import com.customization.oaones.appsyn.service.impl.SyncPlanServiceImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 同步中心“同步设置”控制器（action 前缀 syncplan.，三个连接器共用同一实现）。
 *
 * <p>入口：一级菜单“同步中心”（`/oaones/syncCenter/syncCenter.jsp`）与
 * 各连接器的同步中心页（`/oaones/syncCenter/{WeCom,DingTalk,Feishu}/sync.jsp`），
 * 页面按当前平台走对应的 api.jsp（WeCom / DingTalk / Feishu），三个入口都路由到本控制器。</p>
 *
 * <p>分权：带 cfg_id 的请求（保存 / 删除 / 立即执行 / 单租户列表）都按“租户维护人”校验；
 * 不带 cfg_id 的列表只返回当前登录人可维护租户的设置。</p>
 */
public class SyncPlanController extends BaseController {

    private SyncPlanService planService() {
        return new SyncPlanServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("syncplan.list".equals(action)) {
            String cfgId = req(request, "cfg_id");
            String type = req(request, "type");
            List<Integer> allowed = null;
            if (OaonesUtil.s(cfgId).trim().length() > 0 && OaonesUtil.i(cfgId, 0) > 0) {
                assertCfgRight(cfgId, operatorId);
            } else {
                // 不带租户：只列本人可维护租户的设置（与同步日志同一分权口径）
                allowed = planService().maintainableCfgIds(type, operatorId);
            }
            return OaonesUtil.ok(planService().list(type, cfgId, req(request, "direction"), allowed));
        }
        if ("syncplan.get".equals(action)) {
            String cfgId = req(request, "cfg_id");
            assertCfgRight(cfgId, operatorId);
            int direction = OaonesUtil.i(req(request, "direction"), 0);
            JSONObject row = planService().get(OaonesUtil.i(req(request, "type"), 0), cfgId, direction);
            return OaonesUtil.ok(row == null ? new JSONObject() : row);
        }
        if ("syncplan.save".equals(action)) {
            JSONObject params = paramObject(request);
            assertPlanRight(OaonesUtil.s(params.getString("cfg_id")), operatorId);
            return OaonesUtil.ok(planService().save(params, operatorId));
        }
        if ("syncplan.delete".equals(action)) {
            // 删除一条规则（按主键）：先取该条规则并按它的租户校验分权
            requireRule(req(request, "id"), operatorId);
            return OaonesUtil.ok(planService().delete(req(request, "type"), req(request, "id"), operatorId));
        }
        if ("syncplan.state".equals(action)) {
            // 暂停 / 启动该条规则的计划任务（同步设置列表行尾按钮）：只改启停，不动同步范围与 cron
            requireRule(req(request, "id"), operatorId);
            return OaonesUtil.ok(planService().setEnabled(req(request, "type"), req(request, "id"),
                    OaonesUtil.i(req(request, "enabled"), 0), operatorId));
        }
        if ("syncplan.cronPreview".equals(action)) {
            // 「预测后 10 次」：按页面选定的计划任务方式（轮询间隔（分钟）/ 高级 cron）试算
            // 实际会执行的时间；不涉及租户数据，任何登录人都可以试算（页面保存前也会再校验一次）
            return OaonesUtil.ok(planService().planPreview(req(request, "mode"), req(request, "cron"),
                    req(request, "interval_min"),
                    OaonesUtil.i(req(request, "count"), 10)));
        }
        if ("syncplan.scheduler".equals(action)) {
            // 内置调度器状态：页面据此显示“页面 cron 直接生效”，不依赖 E9 后台计划任务
            return OaonesUtil.ok(
                    com.customization.oaones.appsyn.job.OaonesSyncPlanScheduler.status());
        }
        if ("syncplan.runNow".equals(action)) {
            // 立即执行该条规则（按主键）：按该条规则自己保存的同步范围执行
            requireRule(req(request, "id"), operatorId);
            return OaonesUtil.ok(planService().runNow(req(request, "type"), req(request, "id"), operatorId));
        }
        return null;
    }

    /**
     * 按规则主键取该条规则，并按它所属的租户校验分权。
     *
     * <p>行内操作（暂停 / 启动、立即执行、删除）只带规则 id 进来，
     * 所以必须先取回这条规则、用它自己的 {@code cfg_id} 走 {@link #assertPlanRight}
     * （租户按维护人；“全局默认配置”cfg_id = 0 只允许系统管理员），避免只报一个 id 就能动别人的规则。</p>
     *
     * @return 该条规则的 JSON（含 id / cfg_id / direction / scope 等，调用方用于后续处理）
     */
    private JSONObject requireRule(String id, String operatorId) {
        JSONObject rule = planService().getById(id);
        if (rule == null) {
            throw new RuntimeException("规则不存在（id " + OaonesUtil.s(id) + "），请刷新页面后重试");
        }
        assertPlanRight(OaonesUtil.s(rule.getString("cfg_id")), operatorId);
        return rule;
    }

    /**
     * 同步设置的分权：带租户（cfg_id &gt; 0）按“租户维护人”校验；
     * “全局默认配置”（cfg_id = 0，作用于该平台所有未单独配置的租户）只允许系统管理员或超管角色维护。
     */
    private void assertPlanRight(String cfgId, String operatorId) {
        String text = OaonesUtil.s(cfgId).trim();
        if (text.length() == 0) {
            throw new RuntimeException("请先选择租户（要维护全局默认配置，请把“配置范围”切到“全局默认配置”）");
        }
        if (OaonesUtil.i(text, 0) > 0) {
            assertCfgRight(text, operatorId);
            return;
        }
        if (!OaonesAdmin.isTotalAdmin(operatorId)) {
            throw new RuntimeException("“全局默认配置”作用于本平台全部未单独配置的租户，只允许系统管理员维护");
        }
    }
}
