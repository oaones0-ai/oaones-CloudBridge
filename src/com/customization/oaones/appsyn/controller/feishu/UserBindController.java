package com.customization.oaones.appsyn.controller.feishu;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.feishu.DeptBindService;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.feishu.UserBindService;
import com.customization.oaones.appsyn.service.feishu.UserRuleService;
import com.customization.oaones.appsyn.service.feishu.UserTmpService;
import com.customization.oaones.appsyn.service.impl.feishu.DeptBindServiceImpl;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.UserBindServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.UserRuleServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.UserTmpServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 人员绑定控制器（action 前缀 user.）。
 */
public class UserBindController extends BaseController {

    private UserBindService userBindService() {
        return new UserBindServiceImpl();
    }

    private UserTmpService userTmpService() {
        return new UserTmpServiceImpl();
    }

    private UserRuleService userRuleService() {
        return new UserRuleServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    private DeptBindService deptBindService() {
        return new DeptBindServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        // 租户分权：带 cfg_id 的请求必须由该租户维护人或系统管理员发起
        requireCfgRight(request, operatorId);
        if ("user.list".equals(action)) {
            return OaonesUtil.ok(userBindService().listBound(
                    req(request, "cfg_id"), req(request, "keyword")));
        }
        if ("user.oaUnbound".equals(action)) {
            return OaonesUtil.ok(userBindService().listOaUnbound(
                    req(request, "cfg_id"), req(request, "keyword")));
        }
        if ("user.wxUnbound".equals(action)) {
            return OaonesUtil.ok(userBindService().listWxUnbound(req(request, "cfg_id")));
        }
        if ("user.wxlist".equals(action)) {
            return OaonesUtil.ok(userTmpService().listNormal(req(request, "cfg_id")));
        }
        if ("user.tmpRefresh".equals(action)) {
            return OaonesUtil.ok(userTmpService().refreshByCfgId(req(request, "cfg_id")));
        }
        if ("user.noBind".equals(action)) {
            return OaonesUtil.ok(userTmpService().markNoBind(
                    req(request, "cfg_id"), req(request, "wx_user_id")));
        }
        if ("user.cancelNoBind".equals(action)) {
            return OaonesUtil.ok(userTmpService().cancelNoBind(
                    req(request, "cfg_id"), req(request, "wx_user_id")));
        }
        if ("user.batchNoBind".equals(action)) {
            return OaonesUtil.ok(userTmpService().batchNoBind(
                    req(request, "cfg_id"), req(request, "wx_user_ids")));
        }
        if ("user.oaNoBindList".equals(action)) {
            return OaonesUtil.ok(userBindService().listOaNoBind(req(request, "cfg_id")));
        }
        if ("user.oaNoBind".equals(action)) {
            return OaonesUtil.ok(userBindService().markOaNoBind(
                    req(request, "cfg_id"), req(request, "oa_user_id"), operatorId));
        }
        if ("user.oaCancelNoBind".equals(action)) {
            return OaonesUtil.ok(userBindService().cancelOaNoBind(
                    req(request, "cfg_id"), req(request, "oa_user_id")));
        }
        if ("user.oaBatchNoBind".equals(action)) {
            return OaonesUtil.ok(userBindService().batchOaNoBind(paramObject(request), operatorId));
        }
        if ("user.wxUsers".equals(action)) {
            return OaonesUtil.ok(userBindService().listWxUsers(
                    req(request, "cfg_id"), req(request, "keyword")));
        }
        if ("user.oaUsers".equals(action)) {
            // “选择 OA 人员”候选按登录人的 OA 分权范围过滤（OA 没开分权时=全量）
            return OaonesUtil.ok(oaService().listUsers(req(request, "keyword"), operatorId));
        }
        if ("user.orgOptions".equals(action)) {
            OaService oaService = oaService();
            JSONObject data = new JSONObject();
            // “选择 OA 部门 / 分部”候选同口径过滤
            data.put("oa_subcompanies", oaService.listSubCompaniesFromCache(operatorId));
            data.put("oa_departments", oaService.listDepartmentsFromCache(operatorId));
            return OaonesUtil.ok(data);
        }
        if ("user.save".equals(action)) {
            return OaonesUtil.ok(userBindService().save(paramObject(request), operatorId));
        }
        if ("user.delete".equals(action)) {
            userBindService().delete(req(request, "id"));
            return OaonesUtil.ok();
        }
        if ("user.rule.list".equals(action)) {
            return OaonesUtil.ok(userRuleService().list(operatorId));
        }
        if ("user.rule.get".equals(action)) {
            return OaonesUtil.ok(userRuleService().getEffective(req(request, "cfg_id")));
        }
        if ("user.rule.save".equals(action)) {
            return OaonesUtil.ok(userRuleService().save(paramObject(request), operatorId));
        }
        if ("user.rule.delete".equals(action)) {
            userRuleService().delete(req(request, "cfg_id"));
            return OaonesUtil.ok();
        }
        if ("user.autoMatch".equals(action)) {
            return OaonesUtil.ok(userBindService().autoMatch(paramObject(request)));
        }
        if ("user.batchSave".equals(action)) {
            return OaonesUtil.ok(userBindService().batchSave(paramObject(request), operatorId));
        }
        if ("user.deptBinds".equals(action)) {
            // 人员绑定页“部门绑定”列：只需启用中的部门绑定对（OA部门 ↔ 飞书部门）
            return OaonesUtil.ok(deptBindService().listEnabled(req(request, "cfg_id")));
        }
        if ("user.syncToWx".equals(action)) {
            return OaonesUtil.ok(userBindService().syncToWx(paramObject(request), operatorId));
        }
        if ("user.syncToWxBatch".equals(action)) {
            // A 页“同步人员”批量：逐条同步勾选的 OA 人员，返回成功/失败明细
            return OaonesUtil.ok(userBindService().syncToWxBatch(paramObject(request), operatorId));
        }
        if ("user.syncToOa".equals(action)) {
            return OaonesUtil.ok(userBindService().syncToOa(paramObject(request), operatorId));
        }
        if ("user.syncToOaBatch".equals(action)) {
            // B 页“同步人员”批量：逐条同步勾选的飞书成员，返回成功/失败明细
            return OaonesUtil.ok(userBindService().syncToOaBatch(paramObject(request), operatorId));
        }
        return null;
    }
}
