package com.customization.oaones.appsyn.service.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * OA人员 - 钉钉成员 绑定关系维护。
 *
 * <p>页面可查看全部已绑定数据，也可分别查看：
 * OA 有账号但未绑定钉钉 / 钉钉有成员但未绑定 OA 的单方未绑定数据。</p>
 */
public interface UserBindService {

    /** 已绑定数据（含 OA/钉钉两侧名称）。 */
    JSONArray listBound(String cfgId, String keyword);

    /** OA 有账号但未绑定钉钉。 */
    JSONArray listOaUnbound(String cfgId, String keyword) throws Exception;

    /** 钉钉有成员但未绑定 OA（读取人员快照）。 */
    JSONArray listWxUnbound(String cfgId) throws Exception;

    /** 可选钉钉成员（读取人员快照，含主要数据）。 */
    JSONArray listWxUsers(String cfgId, String keyword) throws Exception;

    /** 按默认绑定规则为未绑定 OA 人员匹配钉钉成员。 */
    JSONObject autoMatch(JSONObject params) throws Exception;

    /** 建立/更新人员绑定。 */
    JSONObject save(JSONObject params, String operatorId) throws Exception;

    /** 批量保存自动匹配出的人员绑定。 */
    JSONObject batchSave(JSONObject params, String operatorId) throws Exception;

    /** 删除人员绑定。 */
    void delete(String bindId);

    /**
     * 已标记“免绑定”的 OA 人员ID列表（A 页 OA 数据基准）。
     *
     * <p>本表已取消 no_bind 列：{@code bind_state=0} 即免绑定、{@code bind_state=1} 即已绑定；
     * “待绑定/未绑定”的人员不落库，由页面加载时在代码中判定。</p>
     */
    JSONArray listOaNoBind(String cfgId);

    /** 把 OA 人员标记为免绑定（该 OA 人员不需要绑定钉钉成员）。 */
    JSONObject markOaNoBind(String cfgId, String oaUserId, String operatorId) throws Exception;

    /** 取消 OA 人员免绑定（回到未绑定状态）。 */
    JSONObject cancelOaNoBind(String cfgId, String oaUserId) throws Exception;

    /**
     * A 页（OA 数据基准）批量“免绑定”：对勾选的多个 OA 人员逐条执行 {@link #markOaNoBind}。
     *
     * @param params cfg_id 钉钉租户ID、oa_ids 逗号分隔的 OA 人员ID
     */
    JSONObject batchOaNoBind(JSONObject params, String operatorId) throws Exception;

    /** 供同步引擎使用的绑定明细（含 OA 人员侧字段）。 */
    JSONArray listBoundRows(String cfgId);

    /** 标记人员最近一次同步方向与时间。 */
    void markSynced(String cfgId, String oaUserId, int direction);

    /**
     * OA 人员 → 钉钉成员ID（供同步引擎按范围同步时使用，与页面“同步人员”同一口径）：
     * 优先 OA 登录名，其次工号，最后兜底 {@code oa + OA人员ID}；
     * 生成的成员ID已被标记免绑定时抛出业务异常。
     */
    String resolveWxUserId(String cfgId, String oaUserId) throws Exception;

    /**
     * A 页（OA 数据基准）“同步人员”：部门已建立绑定但人员未绑定时，
     * 按部门绑定关系把 OA 人员新建/更新到钉钉并建立人员绑定。
     */
    JSONObject syncToWx(JSONObject params, String operatorId) throws Exception;

    /**
     * A 页（OA 数据基准）批量“同步人员”：对勾选的多个 OA 人员逐条执行 {@link #syncToWx}，
     * 逐条返回成功/失败结果，失败行带上可读的失败原因，供页面弹框列表展示。
     *
     * @param params cfg_id 钉钉租户ID、oa_ids 逗号分隔的 OA 人员ID
     */
    JSONObject syncToWxBatch(JSONObject params, String operatorId) throws Exception;

    /**
     * B 页（钉钉数据基准）“同步人员”：部门已建立绑定但人员未绑定时，
     * 按部门绑定关系把钉钉成员新建/更新到 OA 并建立人员绑定。
     */
    JSONObject syncToOa(JSONObject params, String operatorId) throws Exception;

    /**
     * B 页（钉钉数据基准）批量“同步人员”：对勾选的多个钉钉成员逐条执行 {@link #syncToOa}，
     * 逐条返回成功/失败结果，失败行带上可读的失败原因，供页面弹框列表展示。
     *
     * @param params cfg_id 钉钉租户ID、wx_ids 逗号分隔的钉钉成员ID
     */
    JSONObject syncToOaBatch(JSONObject params, String operatorId) throws Exception;
}
