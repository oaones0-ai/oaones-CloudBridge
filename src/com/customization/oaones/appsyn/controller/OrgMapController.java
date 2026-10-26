package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.OrgMapService;
import com.customization.oaones.appsyn.service.impl.OrgMapServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * OA 分部 - 平台租户映射控制器（action 前缀 orgmap.）。
 *
 * <p>页面通过独立的 /oaones/orgMap/api.jsp 调用，不参与现有三个连接器的业务路由。</p>
 */
public class OrgMapController extends BaseController {

    private final int type;
    private final OrgMapService service = new OrgMapServiceImpl();

    public OrgMapController(int type) {
        this.type = normalizeType(type);
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("orgmap.list".equals(action)) {
            return OaonesUtil.ok(service.list(type, operatorId, req(request, "keyword")));
        }
        if ("orgmap.save".equals(action)) {
            return OaonesUtil.ok(service.save(paramObject(request), type, operatorId));
        }
        if ("orgmap.delete".equals(action)) {
            service.delete(req(request, "id"), type, operatorId);
            return OaonesUtil.ok();
        }
        return null;
    }

    /** 页面只允许三个已知平台；非法参数按企微兜底。 */
    public static int normalizeType(int type) {
        return type == OaonesConst.TYPE_DINGTALK || type == OaonesConst.TYPE_FEISHU
                ? type : OaonesConst.TYPE_WECHAT;
    }
}
