package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

/**
 * OA 分部 - 平台租户一对一映射。
 *
 * <p>本服务只维护映射关系，不参与部门绑定、人员绑定或同步执行。</p>
 */
public interface OrgMapService {

    /**
     * 页面数据：当前平台可见租户、OA 分部候选、已有映射和统计。
     *
     * @param type 平台类型：0 企微 / 1 钉钉 / 2 飞书
     */
    JSONObject list(int type, String operatorId, String keyword);

    /** 新建或更新一条“OA 分部 - 平台租户”一对一映射。 */
    JSONObject save(JSONObject params, int type, String operatorId);

    /** 删除一条映射。 */
    void delete(String id, int type, String operatorId);
}
