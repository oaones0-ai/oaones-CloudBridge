package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

/**
 * 操作监控平台服务：查询 / 过滤项 / 清理。
 *
 * <p>本页是“全站的操作留痕”，因此分权口径与租户维护人无关：只有系统管理员
 * （user.id = 1）与 {@code oaones.properties#opmonitor.admin.ids} 中列出的人员可以查看。</p>
 */
public interface OpLogService {

    /** 当前登录人是否可以查看操作监控（页面准入，接口层每次再判一次）。 */
    boolean canView(String operatorId);

    /** 分页查询操作日志（带过滤条件与按事件类型的统计）。 */
    JSONObject query(JSONObject params, String operatorId);

    /** 过滤项：事件类型 / 菜单列表 / 保留天数 / 开关状态。 */
    JSONObject options(String operatorId);

    /** 按保留天数清理历史日志（每进程每小时最多清一次，供页面打开时调用）。 */
    void cleanIfDue();

    /** 手动清理：删除早于保留天数的日志。 */
    JSONObject clean(String operatorId);
}
