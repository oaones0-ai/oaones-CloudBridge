package com.customization.oaones.appsyn.service.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * OA分部 - 飞书租户 绑定关系维护。
 *
 * <p>分部绑定只登记“该 OA 分部归属哪个飞书租户”（cfg_id），飞书部门侧不重复存储：
 * 该分部对应的飞书部门取租户配置 `uf_oaones_qywx_config` 中维护的根部门。</p>
 *
 * <p>分部绑定是部门绑定的前置条件：OA 部门所在分部未建立绑定（或该绑定已解除）时，
 * 该部门不允许建立/更换部门绑定，见 {@code DeptBindServiceImpl#save}。
 * 分部到飞书租户为“一对一”（同一飞书租户只能被一个 OA 分部绑定），
 * 与部门绑定的“多对一”口径不同。</p>
 */
public interface SubBindService {

    /** 分部绑定列表（可按租户/关键字过滤）。 */
    JSONArray list(String cfgId, String keyword);

    /**
     * 按“可维护租户集合”过滤的分部绑定列表（页面用）。
     *
     * <p>分部绑定页是全局视图（一行一个 OA 分部，右侧选择飞书租户），
     * 因此列表只返回当前登录人可维护租户的绑定；集合为空时返回空列表。</p>
     */
    JSONArray listForCfgs(List<Integer> cfgIds, String keyword);

    /** 仅返回有效（yx=0）的绑定（供部门绑定校验、页面标记使用）。 */
    JSONArray listEnabled(String cfgId);

    /** 分部绑定记录对应的飞书租户ID（按记录ID反查，供按记录操作时的分权校验使用）。 */
    Integer cfgIdOf(String bindId);

    /**
     * 记录该分部在飞书侧对应的部门（同步中心按范围同步时写入）。
     *
     * <p>飞书里只有“部门”概念：OA 分部同步到飞书时落成一个部门，
     * 该方法把该部门ID回写到分部绑定上（老的分部绑定页不写该列，留空按租户根部门口径）。</p>
     */
    void markWxDept(String cfgId, String oaSubId, String wxDeptId, String operatorId);

    /** 该 OA 分部当前绑定的飞书租户ID（无有效绑定返回 null，供“更换绑定”时的分权校验使用）。 */
    Integer cfgIdOfSub(String oaSubId);

    /** 建立/更新分部绑定（一对一：飞书租户已被其他分部绑定时拒绝）。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 批量绑定分部（同一批中 OA 分部ID、飞书租户ID都不允许重复）。 */
    JSONObject batchSave(JSONObject params, String operatorId);

    /**
     * 删除分部绑定。
     *
     * <p>只解除分部与飞书租户的绑定：已建立的部门绑定（含手动绑定）保持不变，
     * 只影响该分部下部门的“按编号/名称自动绑定”（需重新绑定分部后再用）。</p>
     *
     * @return 含 {@code oa_sub_id} / {@code cfg_id}（被解除的绑定对象）的结果
     */
    JSONObject delete(String bindId);
}
