package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Map;

/**
 * 系统设置 → 功能开关服务。
 */
public interface FeatureSettingService {

    JSONObject list(String operatorId);

    JSONObject save(String operatorId, JSONArray items);

    JSONObject publicSettings();

    Map<String, Boolean> enabledMap();
}
