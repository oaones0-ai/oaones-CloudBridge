package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.BindOverviewService;
import com.customization.oaones.appsyn.service.impl.BindOverviewServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 绑定总览控制器（action 前缀 overview.）。
 *
 * <p>公共只读页 `/oaones/index/BindOverview.jsp` 用：把企微 / 钉钉 / 飞书三个连接器的
 * **全部平台租户**放在一起，分别给出部门绑定、人员绑定的现状。</p>
 *
 * <p><b>只读</b>：本控制器只有查询 action，没有保存/删除/同步等写操作；
 * 因此也不做“维护人分权”过滤（未指定维护人的租户同样展示）——
 * 页面上看不到也改不了任何 Secret，只有组织与绑定关系。</p>
 */
public class BindOverviewController extends BaseController {

    private BindOverviewService bindOverviewService() {
        return new BindOverviewServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId) {
        if ("overview.list".equals(action)) {
            return OaonesUtil.ok(bindOverviewService().overview(
                    req(request, "platform"), req(request, "cfg_id"), req(request, "keyword")));
        }
        return null;
    }
}
