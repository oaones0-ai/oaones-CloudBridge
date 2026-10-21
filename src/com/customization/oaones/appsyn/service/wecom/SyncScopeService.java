package com.customization.oaones.appsyn.service.wecom;

import com.customization.oaones.appsyn.dto.wecom.SyncScope;

/**
 * 同步范围解析（同步中心“先选范围、再同步”的第一步）。
 *
 * <p>把页面上选择的“OA 分部 / OA 部门”与“企微租户下部门”解析成
 * <b>含下级的组织集合</b>：分部自动含其下级分部与部门，部门自动含其下级部门；
 * 企微部门未指定时取租户配置的 root 节点（{@code uf_oaones_qywx_config.root_dept_id}）。</p>
 */
public interface SyncScopeService {

    /**
     * 解析同步范围。
     *
     * @param cfgId      企微租户配置ID
     * @param direction  同步方向：0 OA→企微 / 1 企微→OA
     * @param oaSubIds   页面上选择的 OA 分部ID，逗号分隔（可空）
     * @param oaDeptIds  页面上选择的 OA 部门ID，逗号分隔（可空）
     * @param wxDeptId   页面上选择的企微部门ID；留空表示租户 root 节点
     */
    SyncScope resolve(String cfgId, int direction, String oaSubIds, String oaDeptIds, String wxDeptId);
}
