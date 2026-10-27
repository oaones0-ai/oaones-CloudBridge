package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.MtgSyncService;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.impl.MtgSyncServiceImpl;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 会议 / 日程同步控制器（action 前缀 {@code mtgsync.}，三个连接器共用同一实现）。
 *
 * <p>入口是两条独立的一级菜单页：「会议同步」{@code /oaones/appsyn/meetingSync/meetingSync.jsp}
 * 与「日程同步」{@code /oaones/appsyn/scheduleSync/scheduleSync.jsp}；页面按当前平台走对应的
 * {@code api.jsp}（企微 / 钉钉 / 飞书），三个入口都路由到本控制器，
 * 用 {@code biz}（0 会议 / 1 日程）+ {@code type}（0 企微 / 1 钉钉 / 2 飞书）区分。</p>
 *
 * <p>分权：带 {@code cfg_id} 的写操作按“租户维护人”校验（{@link #assertCfgRight}），
 * 不带租户的列表只返回当前登录人可维护租户的数据。</p>
 *
 * <p>例外：{@code mtgsync.userStates} / {@code mtgsync.userState} 是<b>个人账号中心</b>
 * （{@code /oaones/appsyn/accountCenter/MyAccount.jsp}）的本人自助入口——本人看 / 设自己在各租户的
 * 会议 / 日程同步开关，只按登录人ID限定范围，<b>不做</b>租户维护人分权
 * （与 {@code account.*} 同一口径）。</p>
 */
public class MtgSyncController extends BaseController {

    private MtgSyncService syncService() {
        return new MtgSyncServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("mtgsync.list".equals(action)) {
            return OaonesUtil.ok(syncService().listSettings(
                    OaonesUtil.i(req(request, "biz"), 0), OaonesUtil.i(req(request, "type"), 0), operatorId));
        }
        if ("mtgsync.save".equals(action)) {
            JSONObject params = paramObject(request);
            assertCfgRight(OaonesUtil.s(params.getString("cfg_id")), operatorId);
            return OaonesUtil.ok(syncService().save(params, operatorId));
        }
        if ("mtgsync.state".equals(action)) {
            // “每个租户单独设置开启和关闭”：只改开关
            assertCfgRight(req(request, "cfg_id"), operatorId);
            return OaonesUtil.ok(syncService().setEnabled(
                    OaonesUtil.i(req(request, "biz"), 0), OaonesUtil.i(req(request, "type"), 0),
                    req(request, "cfg_id"), OaonesUtil.i(req(request, "enabled"), 1), operatorId));
        }
        if ("mtgsync.direction".equals(action)) {
            // 租户级“同步方向”（两个复选框，可双选，默认双选）：OA 同步到第三方 / 第三方同步到 OA
            assertCfgRight(req(request, "cfg_id"), operatorId);
            return OaonesUtil.ok(syncService().setDirection(
                    OaonesUtil.i(req(request, "biz"), 0), OaonesUtil.i(req(request, "type"), 0),
                    req(request, "cfg_id"), OaonesUtil.i(req(request, "dir_oa2third"), 1),
                    OaonesUtil.i(req(request, "dir_third2oa"), 1), operatorId));
        }
        if ("mtgsync.app".equals(action)) {
            // 租户级“绑定应用”：本租户同步时调平台日程接口用哪个“应用绑定”（应用要有日程权限）
            assertCfgRight(req(request, "cfg_id"), operatorId);
            return OaonesUtil.ok(syncService().setApp(
                    OaonesUtil.i(req(request, "biz"), 0), OaonesUtil.i(req(request, "type"), 0),
                    req(request, "cfg_id"), OaonesUtil.i(req(request, "app_id"), 0), operatorId));
        }
        if ("mtgsync.appCheck".equals(action)) {
            // 「校验」：用该应用的凭据真实调一次平台接口（token + 日程读取权限），不做推送
            assertCfgRight(req(request, "cfg_id"), operatorId);
            return OaonesUtil.ok(syncService().checkApp(
                    OaonesUtil.i(req(request, "biz"), 0), OaonesUtil.i(req(request, "type"), 0),
                    req(request, "cfg_id"), OaonesUtil.i(req(request, "app_id"), 0), operatorId));
        }
        if ("mtgsync.runNow".equals(action)) {
            assertCfgRight(req(request, "cfg_id"), operatorId);
            return OaonesUtil.ok(syncService().runNow(
                    OaonesUtil.i(req(request, "biz"), 0), OaonesUtil.i(req(request, "type"), 0),
                    req(request, "cfg_id"), operatorId));
        }
        if ("mtgsync.logs".equals(action)) {
            return OaonesUtil.ok(syncService().listLog(paramObject(request), operatorId));
        }
        if ("mtgsync.items".equals(action)) {
            // 「同步清单」：按 OA 会议 / 日程维度看同步结果（一行一个对象，取最近一条记录）
            return OaonesUtil.ok(syncService().listItems(paramObject(request), operatorId));
        }
        if ("mtgsync.dirUserList".equals(action)) {
            // 「个人同步方向」页签的只读列表：非“遵循系统设置”的个人及其具体方向
            // （管理员只能看，不能改）；支持按分部 / 部门 / 人员 / 同步方向 / 租户筛选
            return OaonesUtil.ok(syncService().listUserDirPref(paramObject(request), operatorId));
        }
        if ("mtgsync.retry".equals(action)) {
            // 「重推」：对某条 OA 会议 / 日程重新同步一次，会像第一次一样再写一条同步记录
            JSONObject params = paramObject(request);
            assertCfgRight(OaonesUtil.s(params.getString("cfg_id")), operatorId);
            return OaonesUtil.ok(syncService().retry(params, operatorId));
        }
        if ("mtgsync.payload".equals(action)) {
            // 「报文」：按 biz + type + cfg_id + oa_id 组装实际会下发的平台请求报文（只组装、不发送），
            // 供「同步清单」行尾的「报文」弹框查看（含逐字段简要说明）
            JSONObject params = paramObject(request);
            assertCfgRight(OaonesUtil.s(params.getString("cfg_id")), operatorId);
            return OaonesUtil.ok(syncService().payload(params, operatorId));
        }
        if ("mtgsync.orgOptions".equals(action)) {
            // 「个人同步方向」页签里“分部 / 部门”筛选弹框的候选
            // （按登录人的 OA 分权过滤，OA 没开分权时=全量；分权口径见 common.OaonesHrmScope）
            JSONObject data = new JSONObject();
            data.put("oa_subcompanies", oaService().listSubCompaniesFromCache(operatorId));
            data.put("oa_departments", oaService().listDepartmentsFromCache(operatorId));
            return OaonesUtil.ok(data);
        }
        if ("mtgsync.scheduler".equals(action)) {
            // 内置调度器状态 + 计划任务：页面据此显示“按计划任务（白天 / 夜间两套频率）自动同步”，
            // 不依赖 E9 后台计划任务
            return OaonesUtil.ok(
                    com.customization.oaones.appsyn.job.OaonesMtgSyncScheduler.status());
        }
        if ("mtgsync.plan".equals(action)) {
            // 页面「计划任务」：同步方式（时间段明细 / 高级 cron）+ 工作日口径 + 启用开关
            // + 规则文案 + 下次执行时间
            return OaonesUtil.ok(syncService().syncPlan());
        }
        if ("mtgsync.planSave".equals(action)) {
            // 保存「计划任务」（管理员 / 任一转租户维护人可调；
            // 时间段明细 JSON + 白天 / 夜间两套 Quartz cron + 工作日口径 + 启用开关）
            return OaonesUtil.ok(syncService().saveSyncPlan(paramObject(request), operatorId));
        }
        if ("mtgsync.planPreview".equals(action)) {
            // 「预测后 10 次」：按表单里的配置（时间段明细 / 白天·夜间两套 cron）试算实际执行时间
            // （不落库、不分权）
            return OaonesUtil.ok(syncService().planPreview(paramObject(request)));
        }
        if ("mtgsync.pullNow".equals(action)) {
            // 页面行内「立即拉取」：按租户维护人校验后，对该租户拉取一轮「第三方→OA」
            return OaonesUtil.ok(syncService().pullCfgNow(
                    OaonesUtil.i(req(request, "biz"), 0), OaonesUtil.i(req(request, "type"), 0),
                    req(request, "cfg_id"), operatorId));
        }
        if ("mtgsync.userStates".equals(action)) {
            // 个人账号中心：本人视角的“各租户 会议 / 日程 同步状态 + 本人开关”（本人自助，不分权）
            return OaonesUtil.ok(syncService().userStates(
                    OaonesUtil.i(req(request, "type"), 0), operatorId));
        }
        if ("mtgsync.userState".equals(action)) {
            // 个人账号中心：设置本人对某个“业务 + 平台 + 租户”的同步开关
            // （本人自助，只按登录人ID限定范围，不做租户维护人分权）
            return OaonesUtil.ok(syncService().userSetState(paramObject(request), operatorId));
        }
        if ("mtgsync.userDirection".equals(action)) {
            // 个人账号中心：设置本人对某个“业务 + 平台 + 租户”的同步方向
            // （遵循系统设置 / 指定方向；本人自助，只按登录人ID限定范围）
            return OaonesUtil.ok(syncService().userSetDirection(paramObject(request), operatorId));
        }
        return null;
    }
}
