package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

/**
 * 接口日志中心服务：查询 / 详情 / 过滤项 / 清理。
 */
public interface ApiLogService {

    /** 分页查询接口日志（带过滤条件与统计）。 */
    JSONObject query(JSONObject params, String operatorId);

    /** 单条日志详情（含请求 / 返回报文与异常堆栈）。 */
    JSONObject detail(String id, String operatorId);

    /** 过滤项：平台 / 租户 / 接口类型（按登录人权限给出可见租户）。 */
    JSONObject options(String operatorId);

    /** 确保每天执行一次的日志保留调度器已启动。 */
    void cleanIfDue();

    /** 手动清理：删除早于保留天数的日志。 */
    JSONObject clean(String operatorId);
}
