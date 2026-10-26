package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

/**
 * 同步结果独立页面服务。
 */
public interface SyncResultService {

    /** 平台 / 可见租户等筛选项。 */
    JSONObject options(String operatorId);

    /** 按部门 / 分部 / 人员粒度查询同步结果。 */
    JSONObject query(JSONObject params, String operatorId);
}
