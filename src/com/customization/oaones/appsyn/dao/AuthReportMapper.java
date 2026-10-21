package com.customization.oaones.appsyn.dao;

import java.util.List;
import java.util.Map;

/**
 * 统一认证报表 Mapper（跨平台只读看板，表名全部由 {@link com.customization.oaones.appsyn.common.OaonesParam}
 * 注入的入参 Map 里的 {@code ${...}} 占位提供，不写死物理表名）。
 *
 * <p>口径见 {@code WEB-INF/config/mapper/oaones/AuthReportMapper.xml} 顶部说明：
 * 总人数取平台成员快照、已绑定 / 新增绑定取人员绑定表、登录取操作监控的 login 留痕。</p>
 *
 * <p>本接口只有查询，没有任何写操作（报表页只读）。</p>
 */
public interface AuthReportMapper {

    /** 租户候选（可被 cfgIds 收窄；报表表格与筛选下拉共用）。 */
    List<Map<String, Object>> listCfgs(Map<String, Object> params);

    /** OA 分部候选（有效分部，供“分部”多选筛选）。 */
    List<Map<String, Object>> listSubs();

    /** 各租户平台成员总数（快照 status = 0）。 */
    List<Map<String, Object>> memberCounts(Map<String, Object> params);

    /** 各租户已绑定人数（bind_state = 1 去重 OA 人员，可按分部过滤）。 */
    List<Map<String, Object>> boundCounts(Map<String, Object> params);

    /** 新增绑定走势（按 租户 + 日 / 月 分组）。 */
    List<Map<String, Object>> bindTrend(Map<String, Object> params);

    /** 统一认证登录走势（按 租户 + 日 / 月 分组）。 */
    List<Map<String, Object>> loginTrend(Map<String, Object> params);
}
