package com.customization.oaones.appsyn.controller.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.feishu.ConfigService;
import com.customization.oaones.appsyn.service.feishu.DeptTmpService;
import com.customization.oaones.appsyn.service.feishu.SyncLogService;
import com.customization.oaones.appsyn.service.feishu.SyncService;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.DeptTmpServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.SyncLogServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.SyncServiceImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步中心控制器（action 前缀 sync.）。
 *
 * <p>同步与日志均按“租户维护人”分权：维护人为空的租户所有人可用，
 * 已指定维护人的租户仅维护人可同步、可查看日志。</p>
 *
 * <p>同步入参口径（页面“先选范围、再同步”）：</p>
 * <ul>
 *   <li>{@code oa_sub_ids} / {@code oa_dept_ids}：OA 分部 / OA 部门（逗号分隔，含下级）。
 *       OA→飞书 是来源范围（至少选一个）；飞书→OA 是 OA 落点：
 *       {@code oa_sub_ids} = 落点分部（页面单选必选）、{@code oa_dept_ids} = 落点部门（页面单选、可不选，
 *       不选时新建的 OA 部门直接落在落点分部下）；</li>
 *   <li>{@code wx_dept_id}：飞书租户下的部门（含下级）；<b>留空表示租户 root 节点</b>；</li>
 *   <li>{@code direction}：0 OA→飞书 / 1 飞书→OA。</li>
 * </ul>
 */
public class SyncController extends BaseController {

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    private SyncService syncService() {
        return new SyncServiceImpl();
    }

    private SyncLogService syncLogService() {
        return new SyncLogServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    private DeptTmpService deptTmpService() {
        return new DeptTmpServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("sync.run".equals(action)) {
            String cfgId = req(request, "cfg_id");
            if (!configService().canMaintain(cfgId, operatorId)) {
                return OaonesUtil.fail("您不是该飞书租户的维护人，无权执行同步");
            }
            int direction = OaonesUtil.i(req(request, "direction"), 0);
            if (direction != OaonesConst.DIR_OA_TO_WX && direction != OaonesConst.DIR_WX_TO_OA) {
                return OaonesUtil.fail("同步方向参数不正确");
            }
            return syncService().run(cfgId, direction, operatorId);
        }
        if ("sync.preview".equals(action) || "sync.runScoped".equals(action)) {
            // 按范围同步：先预览（本次将新增/更新哪些部门、哪些人员），确认后再真正写入
            String cfgId = req(request, "cfg_id");
            if (!configService().canMaintain(cfgId, operatorId)) {
                return OaonesUtil.fail("您不是该飞书租户的维护人，无权执行同步");
            }
            return "sync.preview".equals(action)
                    ? syncService().preview(paramObject(request), operatorId)
                    : syncService().runScoped(paramObject(request), operatorId);
        }
        if ("sync.orgOptions".equals(action)) {
            // 同步范围选择框的候选：OA 分部 / OA 部门（E9 组织缓存）与飞书部门（部门快照）
            String cfgId = req(request, "cfg_id");
            if (cfgId.length() > 0 && !configService().canMaintain(cfgId, operatorId)) {
                return OaonesUtil.fail("您不是该飞书租户的维护人，无权选择该租户的同步范围");
            }
            JSONObject data = new JSONObject();
            data.put("subcompanies", oaService().listSubCompaniesFromCache(operatorId));
            data.put("departments", oaService().listDepartmentsFromCache(operatorId));
            data.put("wx_depts", cfgId.length() == 0 ? new JSONArray()
                    : deptTmpService().listNormal(cfgId));
            return OaonesUtil.ok(data);
        }
        if ("sync.logItems".equals(action)) {
            // 同步日志明细（一行一个部门 / 一行一个人员）：日志同样按维护人分权
            String cfgId = req(request, "cfg_id");
            if (cfgId.length() > 0 && !configService().canMaintain(cfgId, operatorId)) {
                return OaonesUtil.fail("您不是该飞书租户的维护人，无权查看同步日志");
            }
            return OaonesUtil.ok(syncLogService().listItems(
                    req(request, "log_id"), req(request, "item_type"),
                    OaonesUtil.i(req(request, "limit"), 5000)));
        }
        if ("sync.logs".equals(action)) {
            String cfgId = req(request, "cfg_id");
            if (cfgId.length() > 0 && !configService().canMaintain(cfgId, operatorId)) {
                return OaonesUtil.fail("您不是该飞书租户的维护人，无权查看同步日志");
            }
            int limit = OaonesUtil.i(req(request, "limit"), 200);
            // 只看当前登录人可维护租户的日志（维护人为空的公共租户也算可见）
            return OaonesUtil.ok(syncLogService().list(
                    cfgId, req(request, "direction"), limit, maintainableCfgIds(operatorId)));
        }
        return null;
    }

    /** 当前登录人可维护的租户ID列表（同步日志按此过滤）。 */
    private List<Integer> maintainableCfgIds(String operatorId) {
        List<Integer> ids = new ArrayList<Integer>();
        JSONArray rows = configService().listMaintainable(operatorId);
        for (int i = 0; i < rows.size(); i++) {
            Integer id = OaonesUtil.integer(rows.getJSONObject(i).getString("id"));
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }
}
