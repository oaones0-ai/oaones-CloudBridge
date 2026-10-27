package com.customization.oaones.appsyn.service.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 人员快照表维护（业务表名取 oaones.appsyn.properties#oaones_user_tmp_table）。
 *
 * <p>租户连通测试成功后、人员绑定页手动刷新或定时任务执行时，
 * 把平台成员列表及其主要数据写入快照；corpid + wx_user_id 唯一。</p>
 */
public interface UserTmpService {

    /** 刷新指定租户的企微人员快照。 */
    JSONObject refreshByCfgId(String cfgId) throws Exception;

    /** 刷新全部启用租户的人员快照。 */
    JSONObject refreshAll();

    /** 拉取成功后把当前成员 upsert 到快照表。 */
    JSONObject refresh(JSONObject cfg, JSONArray users) throws Exception;

    /**
     * 只刷新单个企微成员的快照（不关闭其它成员）。
     *
     * <p>个人账号中心扫码绑定用：扫码人可能是刚加入企业的新成员，
     * 整租户快照里还没有他，绑定前先按 userid 精确拉取并 upsert 一条，
     * 保证后续 {@link #isNormal(String, String)} 校验与页面展示都能取到该成员。
     * 与 {@link #refresh(JSONObject, JSONArray)} 不同，本方法<b>不</b>把其它成员置为已删除。</p>
     */
    JSONObject refreshMember(String cfgId, String wxUserId) throws Exception;

    /** 返回快照中状态正常的成员及其主要数据。 */
    JSONArray listNormal(String cfgId) throws Exception;

    /** 将企微成员标记为免绑定。 */
    JSONObject markNoBind(String cfgId, String wxUserId);

    /** 取消企微成员免绑定。 */
    JSONObject cancelNoBind(String cfgId, String wxUserId);

    /** 批量将企微成员标记为免绑定（多选后一次提交，已绑定 OA 人员的成员会报错）。 */
    JSONObject batchNoBind(String cfgId, String wxUserIds);

    /** 判断企微成员是否已被标记为免绑定。 */
    boolean isNoBind(String corpId, String wxUserId) throws Exception;

    /** 校验企微成员是否存在于人员快照且状态正常。 */
    boolean isNormal(String corpId, String wxUserId) throws Exception;

    /** 快照状态文本。 */
    String statusText(String status);
}
