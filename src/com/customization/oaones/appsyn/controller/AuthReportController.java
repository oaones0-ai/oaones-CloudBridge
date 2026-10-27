package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.AuthReportService;
import com.customization.oaones.appsyn.service.impl.AuthReportServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 统一认证报表控制器（action 前缀 {@code authreport.}）。
 *
 * <p>页面 {@code /oaones/appsyn/authReport/authReport.jsp} 是**独立的一级菜单**、**跨平台只读看板**：
 * 把企微 / 钉钉 / 飞书三个连接器的全部平台租户放在一起，展示
 * “各租户总人数 / 已绑定 / 未绑定”“今日新增绑定 / 今日统一认证登录”与按日 / 按月的走势。</p>
 *
 * <p><b>只读</b>：本控制器只有查询 action，没有保存 / 删除 / 同步等写操作；
 * 与「绑定总览」同一口径，**不按租户维护人分权**（看板只展示人数与趋势，不含任何 Secret
 * 或人员明细），入口的可见范围由 E9 后台菜单授权控制。</p>
 */
public class AuthReportController extends BaseController {

    private AuthReportService authReportService() {
        return new AuthReportServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId) {
        if ("authreport.data".equals(action)) {
            return OaonesUtil.ok(authReportService().data(paramObject(request)));
        }
        return null;
    }
}
