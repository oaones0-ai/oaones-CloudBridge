package com.customization.oaones.appsyn.controller.dingtalk;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.dingtalk.DeptBindService;
import com.customization.oaones.appsyn.service.dingtalk.DeptTmpService;
import com.customization.oaones.appsyn.service.OaService;
import com.customization.oaones.appsyn.service.impl.dingtalk.DeptBindServiceImpl;
import com.customization.oaones.appsyn.service.impl.dingtalk.DeptTmpServiceImpl;
import com.customization.oaones.appsyn.service.impl.OaServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 部门绑定控制器（action 前缀 dept.）。
 */
public class DeptBindController extends BaseController {

    private DeptBindService deptBindService() {
        return new DeptBindServiceImpl();
    }

    private DeptTmpService deptTmpService() {
        return new DeptTmpServiceImpl();
    }

    private OaService oaService() {
        return new OaServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        // 租户分权：带 cfg_id 的请求必须由该租户维护人或系统管理员发起
        requireCfgRight(request, operatorId);
        if ("dept.list".equals(action)) {
            JSONObject data = new JSONObject();
            data.put("binds", deptBindService().list(req(request, "cfg_id"), req(request, "keyword")));
            OaService oaService = oaService();
            // 选择 OA 部门 / 分部的候选按登录人的 OA 分权范围过滤（OA 没开分权时=全量）
            data.put("oa_departments", oaService.listDepartments(operatorId));
            data.put("oa_subcompanies", oaService.listSubCompaniesFromCache(operatorId));
            data.put("oa_dept_options", oaService.listDepartmentsFromCache(operatorId));
            return OaonesUtil.ok(data);
        }
        if ("dept.wxlist".equals(action)) {
            return OaonesUtil.ok(deptTmpService().listNormal(req(request, "cfg_id")));
        }
        if ("dept.tmpRefresh".equals(action)) {
            return OaonesUtil.ok(deptTmpService().refreshByCfgId(req(request, "cfg_id")));
        }
        if ("dept.noBind".equals(action)) {
            return OaonesUtil.ok(deptTmpService().markNoBind(
                    req(request, "cfg_id"), req(request, "wx_dept_id")));
        }
        if ("dept.cancelNoBind".equals(action)) {
            return OaonesUtil.ok(deptTmpService().cancelNoBind(
                    req(request, "cfg_id"), req(request, "wx_dept_id")));
        }
        if ("dept.batchNoBind".equals(action)) {
            return OaonesUtil.ok(deptTmpService().batchNoBind(
                    req(request, "cfg_id"), req(request, "wx_dept_ids")));
        }
        if ("dept.batchSave".equals(action)) {
            return OaonesUtil.ok(deptBindService().batchSave(paramObject(request), operatorId));
        }
        if ("dept.autoBatchSave".equals(action)) {
            return OaonesUtil.ok(deptBindService().autoBatchSave(paramObject(request), operatorId));
        }
        if ("dept.save".equals(action)) {
            return OaonesUtil.ok(deptBindService().save(paramObject(request), operatorId));
        }
        if ("dept.delete".equals(action)) {
            deptBindService().delete(req(request, "id"));
            return OaonesUtil.ok();
        }
        return null;
    }
}
