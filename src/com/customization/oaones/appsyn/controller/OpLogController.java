package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.OpLogService;
import com.customization.oaones.appsyn.service.impl.OpLogServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 操作监控平台控制器（action 前缀 oplog.）。
 *
 * <p>入口：一级菜单「操作监控平台」{@code /oaones/opMonitor/opMonitor.jsp}
 * （自带接口入口 {@code /oaones/opMonitor/api.jsp}）。</p>
 *
 * <p>只读 + 清理：没有设置类写入口。查询 / 清理都按“系统管理员或被授权人员”校验
 * （{@link OpLogServiceImpl#canView}），与页面准入同一口径。</p>
 */
public class OpLogController extends BaseController {

    private OpLogService logService() {
        return new OpLogServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("oplog.options".equals(action)) {
            // 打开页面时确保每天执行一次的日志保留调度器已启动
            if (logService().canView(operatorId)) {
                logService().cleanIfDue();
            }
            return OaonesUtil.ok(logService().options(operatorId));
        }
        if ("oplog.list".equals(action)) {
            JSONObject params = paramObject(request);
            params.put("page", req(request, "page"));
            params.put("size", req(request, "size"));
            return OaonesUtil.ok(logService().query(params, operatorId));
        }
        if ("oplog.clean".equals(action)) {
            return OaonesUtil.ok(logService().clean(operatorId));
        }
        return null;
    }
}
