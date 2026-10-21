package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.ApiLogService;
import com.customization.oaones.appsyn.service.impl.ApiLogServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 接口日志中心控制器（action 前缀 apilog.，三个连接器共用同一实现）。
 *
 * <p>入口：一级菜单「接口日志中心」`/oaones/apiLog/apiLog.jsp`（自带接口入口
 * `/oaones/apiLog/api.jsp`，也按 action 前缀路由到本控制器，见三个连接器的
 * {@code controller.*.OaonesController#route}）。</p>
 *
 * <p>只读 + 清理：没有设置类写入口。查询 / 详情按登录人权限过滤
 * （系统管理员或被授权人员看全部，其余登录人只看本人可维护租户的日志）。</p>
 */
public class ApiLogController extends BaseController {

    private ApiLogService logService() {
        return new ApiLogServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("apilog.options".equals(action)) {
            // 打开页面时顺带按保留天数清理历史日志（内部每小时最多一次，管理员口径）
            logService().cleanIfDue();
            return OaonesUtil.ok(logService().options(operatorId));
        }
        if ("apilog.list".equals(action)) {
            JSONObject params = paramObject(request);
            params.put("page", req(request, "page"));
            params.put("size", req(request, "size"));
            return OaonesUtil.ok(logService().query(params, operatorId));
        }
        if ("apilog.detail".equals(action)) {
            return OaonesUtil.ok(logService().detail(req(request, "id"), operatorId));
        }
        if ("apilog.clean".equals(action)) {
            return OaonesUtil.ok(logService().clean(operatorId));
        }
        return null;
    }
}
