package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 日志保留设置与过期清理服务。
 */
public interface LogRetentionService {

    /** 日志项、当前保留天数与自动清理状态。 */
    JSONObject list(String operatorId);

    /** 单独保存一个日志项的保留天数。 */
    JSONObject save(String operatorId, String logCode, int keepDays);

    /** 一次保存多个日志项。 */
    JSONObject saveAll(String operatorId, JSONArray items);

    /** 按当前配置立即清理一个日志项；{@code <= 0} 时不删除。 */
    JSONObject cleanNow(String logCode);

    /** 管理员立即按配置清理全部日志项。 */
    JSONObject cleanAll(String operatorId);

    /** 自动清理全部日志项（调度线程调用，不做登录权限校验）。 */
    JSONObject cleanExpiredAll();

    /** 读取某个日志项当前生效的保留天数；未配置或异常时按 0（永久保留）处理。 */
    int keepDays(String logCode);
}
