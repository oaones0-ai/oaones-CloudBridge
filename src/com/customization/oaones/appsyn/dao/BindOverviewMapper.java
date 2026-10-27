package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.BindOverviewRow;

import java.util.List;
import java.util.Map;

/**
 * 绑定总览 Mapper（只读，三个平台共用一条 SQL 口径）。
 *
 * <p>页面 `/oaones/appsyn/index/BindOverview.jsp` 是**公共只读总览**：把企微 / 钉钉 /
 * 飞书三个连接器的**全部平台租户**放在一起，分别展示部门绑定、人员绑定。
 * 因此这里不按平台分包（不写成 {@code dao.wecom} 等），直接读共用业务表：</p>
 * <ul>
 *   <li>租户：{@code uf_oaones_appsyn_qywx_config}（`type` 区分 0 企微 / 1 钉钉 / 2 飞书）；</li>
 *   <li>部门绑定：{@code uf_oaones_appsyn_dept_bind}（`yx=1` 为有效；飞书的部门标识在 `wx_dept_key`）；</li>
 *   <li>人员绑定：{@code uf_oaones_appsyn_user_bind}（`bind_state=1` 为已绑定）；</li>
 *   <li>平台侧名称：部门快照 {@code uf_oaones_appsyn_dep_tmp}、人员快照 {@code uf_oaones_appsyn_user_tmp}
 *       （飞书侧按字符串列 {@code wx_dept_key} 关联，企微/钉钉按数值列 {@code wx_dept_id}）。</li>
 * </ul>
 *
 * <p>表名同其它 Mapper 一样由 {@link com.customization.oaones.appsyn.common.OaonesParam}
 * 放进入参 Map（{@code ${cfgTable}} 等），不写死物理表名。</p>
 */
public interface BindOverviewMapper {

    /**
     * 全部平台租户（企微 + 钉钉 + 飞书），用于筛选框与“平台租户”统计。
     *
     * <p>该语句除租户表外还要 {@code left join ${deptTmpTable}} 取租户根部门名称，
     * 因此入参必须是带上全部业务表名的 Map（见
     * {@link com.customization.oaones.appsyn.common.OaonesParam}），不能只传 {@code cfgTable}：
     * MyBatis 解析 {@code ${deptTmpTable}} 时找不到该键，会直接抛
     * {@code BindingException: Parameter 'deptTmpTable' not found}。</p>
     */
    List<BindOverviewRow> listCfgs(Map<String, Object> params);

    /** 部门绑定行（OA 部门 → 平台部门），入参含 platform/cfgId/keyword 与全部业务表名。 */
    List<BindOverviewRow> listDeptRows(Map<String, Object> params);

    /** 人员绑定行（OA 人员 → 平台成员），入参含 platform/cfgId/keyword 与全部业务表名。 */
    List<BindOverviewRow> listUserRows(Map<String, Object> params);
}
