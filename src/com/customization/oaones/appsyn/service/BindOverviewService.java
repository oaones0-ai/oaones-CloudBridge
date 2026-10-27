package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

/**
 * 绑定总览（公共只读页 `/oaones/appsyn/index/BindOverview.jsp`，action 前缀 overview.）。
 *
 * <p>把企微 / 钉钉 / 飞书三个连接器的**全部平台租户**放在一起，
 * 分别给出部门绑定、人员绑定的现状；只读、不写任何数据。</p>
 */
public interface BindOverviewService {

    /**
     * 总览数据。
     *
     * @param platform 平台类型筛选：空串 / "all" 表示全部，0 企微 / 1 钉钉 / 2 飞书
     * @param cfgId    租户筛选：空串 / 0 表示全部租户
     * @param keyword  关键字（OA 部门/人员名称、登录名、工号、平台成员与部门、租户名称）
     * @return 含 cfgs（全部平台租户）、depts / users（两类绑定行）与 count 统计
     */
    JSONObject overview(String platform, String cfgId, String keyword);
}
