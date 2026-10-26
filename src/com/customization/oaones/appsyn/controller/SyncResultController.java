package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.SyncResultService;
import com.customization.oaones.appsyn.service.impl.SyncResultServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 同步结果独立页面控制器（action 前缀 syncresult.）。
 *
 * <p>只读查询部门 / 分部 / 人员粒度的同步明细；流程建号（TenantAccountServiceImpl）
 * 与同步中心写入的同一张明细表在这里统一展示。</p>
 */
public class SyncResultController extends BaseController {

    private SyncResultService service() {
        return new SyncResultServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("syncresult.options".equals(action)) {
            return OaonesUtil.ok(service().options(operatorId));
        }
        if ("syncresult.list".equals(action)) {
            String requestId = req(request, "requestid");
            if (requestId.length() > 0 && !requestId.matches("\\d+")) {
                return OaonesUtil.fail("requestid 必须是 OA 流程请求ID（数字）");
            }
            String logId = req(request, "log_id");
            if (logId.length() > 0 && !logId.matches("\\d+")) {
                return OaonesUtil.fail("批次ID必须是数字");
            }
            return OaonesUtil.ok(service().query(paramObject(request), operatorId));
        }
        return null;
    }
}
