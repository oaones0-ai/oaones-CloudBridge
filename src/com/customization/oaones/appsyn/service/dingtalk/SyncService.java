package com.customization.oaones.appsyn.service.dingtalk;

import com.alibaba.fastjson.JSONObject;

/**
 * OA <-> 钉钉 双向同步服务。
 *
 * <p>同步遵守以下约束：</p>
 * <ul>
 *   <li>只处理“已建立人员绑定”的人员，未绑定人员只在前台补绑，不自动创建对端账号；</li>
 *   <li>OA 部门与钉钉部门未建立绑定时，不自动创建钉钉部门；
 *       若钉钉侧部门已被删除，同样只跳过并记录，不重建。</li>
 * </ul>
 */
public interface SyncService {

    /** 手工触发指定租户、指定方向的同步。 */
    JSONObject run(String cfgId, int direction, String operatorId);

    /** 后台计划任务：所有启用租户 + 配置方向。 */
    void runAllForCron();

    /**
     * 同步中心“开始同步”前的预览：按范围算出本次将新增/更新哪些部门、哪些人员。
     *
     * <p>只读不写：读 OA 组织缓存、钉钉部门快照与钉钉通讯录接口（部门列表 / 成员列表）
     * 得到将要写入的清单与提示文案，供页面用弹窗向用户确认；
     * 真正的写入在 {@link #runScoped(JSONObject, String)} 里按同一套计划执行。</p>
     *
     * @param params cfg_id、direction、nodept_policy、default_dept_id。
     *               OA→钉钉只传 oa_sub_ids / oa_dept_ids；钉钉→OA 只传 wx_dept_id。
     *               目标部门统一按租户现有部门映射确定，不再传页面落点。
     *               （单选、可不选：不选时新建的 OA 部门直接落在该落点分部下）。
     */
    JSONObject preview(JSONObject params, String operatorId);

    /**
     * 按范围同步（页面上确认“开始同步”后调用）。
     *
     * <p>写入范围：OA 分部/部门（含下级）⇄ 钉钉部门（含下级，未指定时为租户 root 节点）；
     * 钉钉 → OA 的 OA 侧是落点（落点分部必选 + 落点部门非必选，只选分部时新建部门落在该分部下）；
     * 部门与人员逐条处理后，把“单条部门 / 单条人员”的同步过程写入同步日志明细表
     * （uf_oaones_sync_log_item）。</p>
     */
    JSONObject runScoped(JSONObject params, String operatorId);
}
