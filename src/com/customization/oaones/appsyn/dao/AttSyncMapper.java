package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.AttAppOption;
import com.customization.oaones.appsyn.dto.AttMember;
import com.customization.oaones.appsyn.entity.AttSyncCfg;
import com.customization.oaones.appsyn.entity.AttSyncLink;
import com.customization.oaones.appsyn.entity.AttSyncLog;
import com.customization.oaones.appsyn.entity.AttSyncLogItem;

import java.util.List;
import java.util.Map;

/**
 * 考勤同步的数据访问接口（Mapper XML：WEB-INF/config/mapper/oaones/appsyn/AttSyncMapper.xml）。
 *
 * <p>涵盖：设置（uf_oaones_appsyn_att_cfg，含“租户级开关”行）、批次（uf_oaones_appsyn_att_log）、
 * 打卡明细（uf_oaones_appsyn_att_log_item）、映射（uf_oaones_appsyn_att_link），以及本模块自己写的那部分 SQL：
 * 范围人员（联 hrmresource / hrmdepartment / hrmsubcompany / 人员绑定表）、
 * 考勤应用候选（读“应用绑定”表，不下发 Secret）。</p>
 *
 * <p>E9 标准考勤打卡表（写入目标）的读写放在 {@link AttSignMapper}；
 * 入参统一是 {@code OaonesParam.of(...)} 造出来的 Map（既带表名占位符也带条件），
 * 实体写入用 {@code e} 键。</p>
 */
public interface AttSyncMapper {

    // ---------------- 同步设置（uf_oaones_appsyn_att_cfg） ----------------

    /**
     * 列设置：按平台（type）+ 可选租户（cfgId / cfgIds）+ 可选行类型（rowType）。
     *
     * <p>页面列表要“一行一条设置”，所以不聚合；rowType 传 0 只看同步设置、传 1 只看租户级开关行。</p>
     */
    List<AttSyncCfg> listCfg(Map<String, Object> params);

    /** 按主键取一条设置（不存在返回 null）。 */
    AttSyncCfg getCfgById(Map<String, Object> params);

    /** 取“平台 + 租户 + 行类型 + 名称”的那一条（保存前查重 / 取租户级开关行用）。 */
    AttSyncCfg getCfgByName(Map<String, Object> params);

    /** 内置调度器扫描：启用中的同步设置（row_type = 0 and enabled = 1）。 */
    List<AttSyncCfg> listEnabledCfg(Map<String, Object> params);

    /** 新增一条设置（插入后回填自增ID）。 */
    void insertCfg(Map<String, Object> params);

    /** 更新一条设置（范围、类型、时间口径、考勤应用、计划任务整体覆盖）。 */
    void updateCfg(Map<String, Object> params);

    /** 删除一条设置（时间段明细由 Service 一并清掉，历史日志保留）。 */
    void deleteCfg(Map<String, Object> params);

    /** 单条启停：只改 enabled。 */
    void updateEnabled(Map<String, Object> params);

    /** 「考勤应用」：只改 app_id（并把校验状态重置为“未校验”）。 */
    void updateApp(Map<String, Object> params);

    /** 写回「校验」结果（check_state / check_msg / check_time）。 */
    void updateCheck(Map<String, Object> params);

    /**
     * 写回设置行的健康度：最近执行时间 / 结果 / 说明 / 连续失败次数 / 最近批次ID
     * （页面据此显示“最近一次执行、连续失败次数”，以及达到阈值时的显式告警文案）。
     */
    void updateRunState(Map<String, Object> params);

    // ---------------- 考勤应用（复用“应用绑定”表） ----------------

    /**
     * 「考勤应用」下拉候选：该平台可维护租户下全部应用绑定记录（含停用，页面标注），
     * 只给展示字段与“Secret 是否已配置”两个标志位，不下发 Secret。
     */
    List<AttAppOption> listAppOptions(Map<String, Object> params);

    /** 后端取凭据：按应用绑定记录ID 取一条（带应用 Secret 与租户 corpId / corpSecret）。 */
    AttAppOption getAppById(Map<String, Object> params);

    // ---------------- 范围人员（OA 侧圈人 + 绑定情况） ----------------

    /**
     * 同步范围圈出的人员（分部 / 部门 / 人员三个维度 OR 并集；都为空 = 该租户全部人员），
     * 一并带出人员状态与该租户下的平台绑定情况。
     *
     * <p>subIds / deptIds / userIds 是已展开下级的 ID 列表；只按 OA 侧组织圈定，
     * 不带“平台部门”维度（需求 Q14）。</p>
     */
    List<AttMember> listMembers(Map<String, Object> params);

    /**
     * 设置里已选人员ID → 姓名（编辑回显用）。
     *
     * <p>只查 {@code hrmresource}，不联绑定表、不按人员状态过滤：已保存到设置里的历史人员
     * 即使后来离职，也应当继续显示姓名，而不是在编辑弹框里退化成 {@code #人员ID}。</p>
     */
    List<AttMember> listUserNames(Map<String, Object> params);

    /** 一批 OA 人员ID → 该租户已绑定的平台账号（未绑定的不返回）。 */
    List<AttMember> listBoundByUsers(Map<String, Object> params);

    /** 平台账号 → OA 人员ID（反向同步 / 明细回填用；未绑定返回 null）。 */
    Integer mapOaUserByWx(Map<String, Object> params);

    // ---------------- 批次（uf_oaones_appsyn_att_log） ----------------

    void insertLog(Map<String, Object> params);

    void updateLog(Map<String, Object> params);

    /** 「同步批次」列表（按执行时间倒序，最多 limit 条）。 */
    List<AttSyncLog> listLog(Map<String, Object> params);

    /** 「同步批次」总条数（与 listLog 同条件）。 */
    int countLog(Map<String, Object> params);

    /** 取一个批次（明细弹窗头部用）。 */
    AttSyncLog getLog(Map<String, Object> params);

    /** 日志保留期清理：删除早于给定日期的批次（不影响正在执行的批次）。 */
    int cleanLog(Map<String, Object> params);

    /** 日志保留期清理：删除早于给定日期的明细。 */
    int cleanLogItem(Map<String, Object> params);

    // ---------------- 打卡明细（uf_oaones_appsyn_att_log_item） ----------------

    void insertLogItem(Map<String, Object> params);

    /** 「打卡明细」列表（按批次 / 人员 / 结果 / 时间 / 关键字筛选）。 */
    List<AttSyncLogItem> listLogItem(Map<String, Object> params);

    int countLogItem(Map<String, Object> params);

    /** 取一条打卡明细（「重推」按明细行定位时用）。 */
    AttSyncLogItem getLogItem(Map<String, Object> params);

    // ---------------- 映射（uf_oaones_appsyn_att_link） ----------------

    /** 按“平台 + 租户 + 方向 + 人员 + 打卡时间 + 类型 + 来源”找映射（去重用）。 */
    AttSyncLink findLinkByKey(Map<String, Object> params);

    /** 按「OA人员 + 平台记录ID」找映射（同一平台记录可扇出到多个 OA 账户）。 */
    AttSyncLink findLinkByRecord(Map<String, Object> params);

    /** 该「平台 + 租户 + 方向」下的映射列表（可按来源 / 时间窗 / 人员过滤）。 */
    List<AttSyncLink> listLink(Map<String, Object> params);

    void insertLink(Map<String, Object> params);

    void updateLink(Map<String, Object> params);

    /** 删除一条映射（时间窗内先删后插时，删掉不再返回的旧记录对应的映射）。 */
    void deleteLink(Map<String, Object> params);
}
