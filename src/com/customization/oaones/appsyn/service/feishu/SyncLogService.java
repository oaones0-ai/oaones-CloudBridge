package com.customization.oaones.appsyn.service.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * 同步日志服务（业务表名取 oaones.appsyn.properties#oaones_sync_log_table）。
 */
public interface SyncLogService {

    /** 日志列表；direction 为空或 all 表示不过滤方向。 */
    JSONArray list(String cfgId, String direction, int limit);

    /**
     * 日志列表（按可见租户过滤，用于维护人分权）。
     *
     * @param allowedCfgIds 可见的租户ID；{@code null} 表示不限制租户，
     *                      空集合表示无任何可见租户（返回空列表）
     */
    JSONArray list(String cfgId, String direction, int limit, List<Integer> allowedCfgIds);

    /**
     * 写一条同步日志（汇总层：总数/成功/跳过/失败/起止时间/结果），返回新增日志ID。
     *
     * <p>detail 超出 3900 字符时截断；逐条对象的同步过程写在
     * {@link #addItems(int, JSONArray)} 的明细表里。</p>
     */
    int add(JSONObject data);

    /**
     * 写一批同步明细（一行一个同步对象：单条部门 / 单条人员）。
     *
     * <p>每条入参字段：item_type（0 部门 / 1 人员）、op（0 新增 / 1 更新 / 2 跳过 / 3 失败）、
     * oa_id、oa_name、wx_id、wx_name、parent_id、parent_name、message、seq。</p>
     *
     * @param logId 所属同步日志ID（{@link #add(JSONObject)} 的返回值）
     * @return 实际写入的明细条数
     */
    int addItems(int logId, JSONArray items, String operatorId);

    /** 某条同步日志的明细（itemType 为空或 all 表示不过滤对象类型），按处理顺序返回。 */
    JSONArray listItems(String logId, String itemType, int limit);
}
