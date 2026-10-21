package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

/**
 * 统一认证报表服务：跨平台只读看板的数据装配。
 *
 * <p>一次 {@link #data(JSONObject)} 返回看板所需的全部数据（租户列表、分部候选、
 * 各租户总人数 / 已绑定 / 未绑定、今日新增绑定 / 今日统一认证登录、按日与按月的走势），
 * 页面切换“按日 / 按月”不再回后端。</p>
 *
 * <p>只读：本服务没有任何写操作。</p>
 */
public interface AuthReportService {

    /** 看板数据（入参：cfg_ids / sub_ids / from_date / to_date，逗号分隔的 ID 列表）。 */
    JSONObject data(JSONObject params);
}
