package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.FeatureSettingService;
import com.customization.oaones.appsyn.service.impl.FeatureSettingServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 系统设置 → 功能设置（action 前缀 features.）。
 */
public class FeatureSettingController extends BaseController {

    private FeatureSettingService service() {
        return new FeatureSettingServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("features.list".equals(action)) {
            return OaonesUtil.ok(service().list(operatorId));
        }
        if ("features.save".equals(action)) {
            String raw = req(request, "items");
            JSONArray items = raw.length() == 0 ? new JSONArray() : JSONArray.parseArray(raw);
            return OaonesUtil.ok(service().save(operatorId, items));
        }
        if ("features.read".equals(action)) {
            return OaonesUtil.ok(service().publicSettings());
        }
        return null;
    }
}
