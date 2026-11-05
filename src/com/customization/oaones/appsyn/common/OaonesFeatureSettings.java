package com.customization.oaones.appsyn.common;

import com.customization.oaones.appsyn.service.impl.FeatureSettingServiceImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * JSP 渲染阶段读取功能开关的轻量入口。
 *
 * <p>同一个请求只查一次配置表，nav 片段与页面按钮共用同一份结果。</p>
 */
public final class OaonesFeatureSettings {

    private static final String REQUEST_ATTRIBUTE = "oaones.feature.enabledMap";

    private OaonesFeatureSettings() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Boolean> current(HttpServletRequest request) {
        Object cached = request.getAttribute(REQUEST_ATTRIBUTE);
        if (cached instanceof Map) {
            return (Map<String, Boolean>) cached;
        }
        Map<String, Boolean> map = new FeatureSettingServiceImpl().enabledMap();
        request.setAttribute(REQUEST_ATTRIBUTE, map);
        return map;
    }

    public static boolean enabled(HttpServletRequest request, String code) {
        Boolean value = current(request).get(code);
        return value == null || value.booleanValue();
    }
}
