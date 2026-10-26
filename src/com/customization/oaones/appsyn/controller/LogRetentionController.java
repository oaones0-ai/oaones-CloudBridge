package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.LogRetentionService;
import com.customization.oaones.appsyn.service.impl.LogRetentionServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 系统设置 → 日志保留（action 前缀 logretention.）。
 */
public class LogRetentionController extends BaseController {

    private LogRetentionService service() {
        return new LogRetentionServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("logretention.list".equals(action)) {
            return OaonesUtil.ok(service().list(operatorId));
        }
        if ("logretention.save".equals(action)) {
            return OaonesUtil.ok(service().save(operatorId,
                    req(request, "code"), OaonesUtil.i(req(request, "days"), 0)));
        }
        if ("logretention.saveAll".equals(action)) {
            String raw = req(request, "items");
            JSONArray items = raw.length() == 0 ? new JSONArray() : JSONArray.parseArray(raw);
            return OaonesUtil.ok(service().saveAll(operatorId, items));
        }
        if ("logretention.clean".equals(action)) {
            return OaonesUtil.ok(service().cleanAll(operatorId));
        }
        return null;
    }
}
