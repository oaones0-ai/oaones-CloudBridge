package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.job.OaonesAttSyncScheduler;
import com.customization.oaones.appsyn.service.AttSyncService;
import com.customization.oaones.appsyn.service.impl.AttSyncServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 考勤同步控制器（action 前缀 {@code attsync.}，三个连接器共用同一实现）。
 *
 * <p>入口是独立的一级菜单「考勤同步」：{@code /oaones/attSync/attSync.jsp?platform=wecom|dingtalk|feishu}，
 * 页面按当前平台走对应的 {@code api.jsp}（企微 / 钉钉 / 飞书），三个入口都路由到本控制器，
 * 用 {@code type}（0 企微 / 1 钉钉 / 2 飞书）区分平台。</p>
 *
 * <p>分权：带 {@code cfg_id} 的写操作按“租户维护人”校验（{@link #assertCfgRight}），
 * 不带租户的列表只返回当前登录人可维护租户的数据；每条设置的具体校验在 Service 里再按记录所属租户做一次
 * （与 {@code sub.*} / {@code app.*} 同一口径，避免不带 {@code cfg_id} 绕过）。</p>
 */
public class AttSyncController extends BaseController {

    private AttSyncService service() {
        return new AttSyncServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("attsync.list".equals(action)) {
            // 「同步设置」页签：当前平台下本人可维护的租户 + 每个租户的设置行
            return OaonesUtil.ok(service().list(OaonesUtil.i(req(request, "type"), 0), operatorId));
        }
        if ("attsync.save".equals(action)) {
            return OaonesUtil.ok(service().save(readParams(request), operatorId));
        }
        if ("attsync.remove".equals(action)) {
            return OaonesUtil.ok(service().remove(readParams(request), operatorId));
        }
        if ("attsync.state".equals(action)) {
            // 单条设置启停（停用后只允许手工执行）
            return OaonesUtil.ok(service().setEnabled(
                    OaonesUtil.i(req(request, "id"), 0), OaonesUtil.i(req(request, "enabled"), 1), operatorId));
        }
        if ("attsync.tenantState".equals(action)) {
            // 租户级开关（平台 + 租户整级启停）
            return OaonesUtil.ok(service().setTenantEnabled(
                    OaonesUtil.i(req(request, "type"), 0), OaonesUtil.i(req(request, "cfg_id"), 0),
                    OaonesUtil.i(req(request, "enabled"), 1), operatorId));
        }
        if ("attsync.tenantApp".equals(action)) {
            // 该租户的「考勤应用」（与租户绑定，新增同步设置时不再重复选）：
            // 保存应用后**立刻真调一次平台考勤接口**，结论落到租户行上
            return OaonesUtil.ok(service().setTenantApp(
                    OaonesUtil.i(req(request, "type"), 0), OaonesUtil.i(req(request, "cfg_id"), 0),
                    OaonesUtil.i(req(request, "app_id"), 0), operatorId));
        }
        if ("attsync.check".equals(action)) {
            // 「重新校验」：用该租户当前考勤应用的凭据真调一次平台考勤接口
            // （token + 考勤组 + 至少一条打卡 / 排班），结论写回租户行
            return OaonesUtil.ok(service().checkApp(
                    OaonesUtil.i(req(request, "type"), 0),
                    OaonesUtil.i(req(request, "cfg_id"), 0),
                    operatorId));
        }
        if ("attsync.runNow".equals(action)) {
            // 「立即执行一次」：按该设置自己的口径；也可临时指定一次时间范围（不落库、不改下次执行时间）
            return OaonesUtil.ok(service().runNow(readParams(request), operatorId));
        }
        if ("attsync.runBatch".equals(action)) {
            // 「整批执行」：当前平台 + 租户下所有启用中的设置各跑一轮
            return OaonesUtil.ok(service().runBatch(
                    OaonesUtil.i(req(request, "type"), 0), OaonesUtil.i(req(request, "cfg_id"), 0), operatorId));
        }
        if ("attsync.retry".equals(action)) {
            // 「重推」：失败 / 跳过的明细按「平台 + 租户 + 人员 + 打卡时间 + 类型」重推一次
            return OaonesUtil.ok(service().retry(readParams(request), operatorId));
        }
        if ("attsync.logs".equals(action)) {
            return OaonesUtil.ok(service().logs(readParams(request), operatorId));
        }
        if ("attsync.items".equals(action)) {
            // 「打卡明细」：可按批次 / 人员 / 结果 / 打卡时间 / 关键字筛选
            return OaonesUtil.ok(service().items(readParams(request), operatorId));
        }
        if ("attsync.planPreview".equals(action)) {
            // 「预测后 10 次」：按表单里当前配置试算实际会执行的时间（不落库、不分权）
            return OaonesUtil.ok(service().planPreview(readParams(request)));
        }
        if ("attsync.orgOptions".equals(action)) {
            // 范围选择框的候选（OA 分部 + OA 部门，按登录人的泛微标准分权过滤）
            return OaonesUtil.ok(service().orgOptions(operatorId));
        }
        if ("attsync.oaUsers".equals(action)) {
            // 人员候选（姓名 / 登录名 / 工号关键字；按 OA 分权过滤）
            return OaonesUtil.ok(service().oaUsers(req(request, "keyword"), operatorId));
        }
        if ("attsync.health".equals(action)) {
            return OaonesUtil.ok(service().health(OaonesUtil.i(req(request, "id"), 0)));
        }
        if ("attsync.scheduler".equals(action)) {
            // 内置调度器状态（页面顶部提示行用）
            return OaonesUtil.ok(OaonesAttSyncScheduler.status());
        }
        return null;
    }

    /**
     * 读参数：{@code items} 是页面提交的 JSON 数组（时间段明细），
     * 其它字段（win_mode / win_from / …）按字符串读取，由 Service 做类型转换与校验。
     */
    private JSONObject readParams(HttpServletRequest request) throws Exception {
        JSONObject params = paramObject(request);
        String items = req(request, "items");
        if (items.length() > 0) {
            params.put("items", com.alibaba.fastjson.JSONArray.parseArray(items));
        }
        return params;
    }
}
