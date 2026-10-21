package com.customization.oaones.appsyn.controller.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.feishu.ConfigService;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.feishu.SubBindService;
import com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.SubBindServiceImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 分部绑定控制器（action 前缀 sub.）。
 *
 * <p>分部绑定只登记“OA 分部归属哪个飞书租户”（`uf_oaones_sub_bind.cfg_id`），
 * 飞书部门取租户配置 `uf_oaones_qywx_config.root_dept_id`；
 * 分部到飞书租户为“一对一”，且分部绑定是部门绑定的前置条件。</p>
 *
 * <p>本页是全局视图（一行一个 OA 分部，右侧选择飞书租户），因此：
 * `sub.list` 只返回当前登录人可维护租户的分部绑定；
 * `sub.save/batchSave` 校验目标租户的维护人权限；`sub.delete` 按记录ID
 * 反查该绑定的租户再做一次校验，避免漏掉分权兜底。</p>
 */
public class SubBindController extends BaseController {

    private SubBindService subBindService() {
        return new SubBindServiceImpl();
    }

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        // 租户分权：带 cfg_id 的请求必须由该租户维护人或系统管理员发起
        requireCfgRight(request, operatorId);
        if ("sub.list".equals(action)) {
            JSONObject data = new JSONObject();
            // 页面不按单个租户过滤，只返回本人可维护租户的分部绑定（未指定维护人的租户仅系统管理员可见）
            data.put("binds", subBindService().listForCfgs(visibleCfgIds(operatorId), req(request, "keyword")));
            OaService oaService = oaService();
            data.put("oa_subcompanies", oaService.listSubCompaniesFromCache(operatorId));
            return OaonesUtil.ok(data);
        }
        if ("sub.batchSave".equals(action)) {
            JSONObject params = paramObject(request);
            assertCfgRights(params.getString("cfg_ids"), operatorId);
            return OaonesUtil.ok(subBindService().batchSave(params, operatorId));
        }
        if ("sub.save".equals(action)) {
            JSONObject params = paramObject(request);
            assertRebindRight(params.getString("oa_sub_id"), params.getString("cfg_id"), operatorId);
            return OaonesUtil.ok(subBindService().save(params, operatorId));
        }
        if ("sub.delete".equals(action)) {
            String id = req(request, "id");
            // 按记录ID反查绑定所属租户再校验，防止不带 cfg_id 绕过维护人校验
            assertCfgRight(OaonesUtil.nullToEmpty(subBindService().cfgIdOf(id)), operatorId);
            return OaonesUtil.ok(subBindService().delete(id));
        }
        return null;
    }

    /** 当前登录人可维护的租户ID集合（系统管理员为全部租户）。 */
    private List<Integer> visibleCfgIds(String operatorId) {
        List<Integer> ids = new ArrayList<Integer>();
        JSONArray cfgs = configService().listForUser(operatorId);
        for (int i = 0; i < cfgs.size(); i++) {
            Integer id = OaonesUtil.integer(OaonesUtil.s(cfgs.getJSONObject(i).getString("id")));
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 批量绑定：逐个校验本批涉及的飞书租户维护人权限。 */
    private void assertCfgRights(String cfgIdsCsv, String operatorId) {
        String value = OaonesUtil.s(cfgIdsCsv);
        if (value.length() == 0) {
            return;
        }
        for (String cfgId : value.split(",")) {
            if (cfgId == null || cfgId.trim().length() == 0) {
                continue;
            }
            assertCfgRight(cfgId.trim(), operatorId);
        }
    }

    /**
     * 更换“分部 → 飞书租户”绑定时的兜底校验。
     *
     * <p>分部侧是一对一（一个分部只属于一个飞书租户）。若该分部当前已绑定到**另一个租户**，
     * 而当前登录人不是那个租户的维护人（且该租户仍存在），说明这是别人维护的绑定，
     * 不允许在页面上看不到的情况下被改绑；原租户已不存在时（配置被删）允许改绑以便清理。</p>
     */
    private void assertRebindRight(String oaSubId, String targetCfgId, String operatorId) {
        Integer currentCfgId = subBindService().cfgIdOfSub(oaSubId);
        if (currentCfgId == null || currentCfgId <= 0) {
            return;
        }
        if (OaonesUtil.s(targetCfgId).equals(String.valueOf(currentCfgId))) {
            return;
        }
        if (configService().get(String.valueOf(currentCfgId)) == null) {
            // 原租户配置已删除：允许把该分部改绑到新的租户
            return;
        }
        assertCfgRight(String.valueOf(currentCfgId), operatorId);
    }
}
