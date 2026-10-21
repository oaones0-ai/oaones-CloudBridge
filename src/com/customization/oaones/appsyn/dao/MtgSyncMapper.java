package com.customization.oaones.appsyn.dao;

import com.customization.oaones.appsyn.dto.MtgOaItem;
import com.customization.oaones.appsyn.dto.MtgAppOption;
import com.customization.oaones.appsyn.entity.MtgSyncCfg;
import com.customization.oaones.appsyn.entity.MtgSyncLog;
import com.customization.oaones.appsyn.entity.MtgSyncUser;
import com.customization.oaones.appsyn.entity.MtgSyncLink;
import com.customization.oaones.appsyn.entity.MtgOaWrite;

import java.util.List;
import java.util.Map;

/**
 * 会议 / 日程同步的数据访问接口（Mapper XML：
 * {@code WEB-INF/config/mapper/oaones/MtgSyncMapper.xml}）。
 *
 * <p>会议（{@code Meeting}）与日程（{@code WorkPlan}）共用两张业务表：
 * 设置表 {@code uf_oaones_mtg_sync}（一行一个“业务 + 平台 + 租户”，用 biz 区分会议 / 日程）与
 * 记录表 {@code uf_oaones_mtg_sync_log}（一行一次同步动作）；表名由入参
 * {@code ${mtgSyncTable}} / {@code ${mtgSyncLogTable}} 传入（取值见 {@code OaonesProps}）。</p>
 *
 * <p>入参统一是 {@code OaonesParam.of(...)} 造出来的 Map：既带表名占位符，
 * 也带查询条件；实体写入用 {@code e} 键。</p>
 */
public interface MtgSyncMapper {

    /** 按业务 + 平台 + 租户列出设置（不含租户时只按业务 + 平台列，供页面列表合并展示）。 */
    List<MtgSyncCfg> listCfg(Map<String, Object> params);

    /** 取某个“业务 + 平台 + 租户”的设置（没有则返回 null）。 */
    MtgSyncCfg getCfg(Map<String, Object> params);

    /** 按主键取一条设置。 */
    MtgSyncCfg getCfgById(Map<String, Object> params);

    /** 内置调度器扫描：启用中且到点的设置。 */
    List<MtgSyncCfg> listEnabledCfg(Map<String, Object> params);

    void insertCfg(Map<String, Object> params);

    void updateCfg(Map<String, Object> params);

    /** “每个租户单独开关”：只改 enabled。 */
    void updateEnabled(Map<String, Object> params);

    /**
     * 「绑定应用」：只改某条设置的 {@code app_id}
     * （会议 / 日程同步调平台日程接口时用哪个“应用绑定”里的应用）。
     */
    void updateApp(Map<String, Object> params);

    /**
     * 页面「绑定应用」下拉的候选：该平台可维护租户下**全部**应用绑定记录
     * （含停用；页面按状态标注），不取 Secret，只取“Secret 是否已配置”两个标志位。
     */
    List<MtgAppOption> listAppOptions(Map<String, Object> params);

    /**
     * 按应用绑定记录ID 取一条（后端取凭据用：带应用自己的 Secret 与租户的
     * corpId / corpSecret，供同步引擎决定用哪一份 Secret）；不存在返回 null。
     */
    MtgAppOption getAppById(Map<String, Object> params);

    /** 写一条同步记录。 */
    void insertLog(Map<String, Object> params);

    /** 「同步记录」列表（按时间倒序，最多 limit 条）。 */
    List<MtgSyncLog> listLog(Map<String, Object> params);

    /** 「同步记录」总条数（与 listLog 同条件，分页用）。 */
    int countLog(Map<String, Object> params);

    /**
     * 「同步清单」：按 OA 对象（会议 / 日程）维度取“最近一条”同步结果（一行一个对象），
     * 供页面按会议维度看同步结果、对“跳过 / 失败”行重推。
     */
    List<MtgSyncLog> listItems(Map<String, Object> params);

    /**
     * 该 OA 对象此前是否已成功同步过：返回最近一次记录的动作（0 新增 / 1 更新 / 2 跳过 / 3 失败），
     * 没记录返回 null。引擎据此决定本次动作是“新增”还是“更新”。
     */
    Integer lastLogOp(Map<String, Object> params);

    /**
     * 该 OA 对象此前成功同步时返回的平台侧对象ID（op 0 新增 / 1 更新里最近的一条），
     * 没有成功记录返回 null；引擎据此决定本次走平台“新增”还是“更新”。
     */
    String lastSuccessWxId(Map<String, Object> params);

    /**
     * 「只同步一次」：给定一批 OA 对象ID，返回其中**已经有同步记录**的对象ID——
     * 不限动作（新增 / 更新 / 跳过 / 失败都算“已经处理过”）。
     *
     * <p>内置调度器据此跳过已经处理过的对象：失败 / 跳过的对象**不会每轮自动重推**，
     * 需要重推时由页面「同步清单」手工点「重推」（action {@code mtgsync.retry}）。</p>
     */
    List<String> listAttemptedOaIds(Map<String, Object> params);

    /**
     * 某个 OA 对象在本「业务 + 平台 + 租户」下的**最近一条**同步记录（没有返回 null）。
     *
     * <p>删除对账用：源端把对象删掉后，映射行里没有标题 / 时间，可从这里取回来写进删除记录；
     * 同时用它判断“上次平台删除失败是不是刚试过”，避免每个轮询周期都重复试一次。</p>
     */
    MtgSyncLog lastLog(Map<String, Object> params);

    // ---------------- OA 侧来源（会议 Meeting / 日程 WorkPlan） ----------------

    /** 时间窗内、未取消的会议（按 beginDateTime 过滤）。 */
    List<MtgOaItem> listMeetings(Map<String, Object> params);

    /** 按 OA 会议ID 取单条（“重推”用；不带时间窗）。 */
    MtgOaItem getMeeting(Map<String, Object> params);

    /** 一条会议的参与人 OA 人员ID（Meeting_Member2，membertype = 0 人员）。 */
    List<String> listMeetingMembers(Map<String, Object> params);

    /** 时间窗内、未删除的日程（按 begindate 过滤）。 */
    List<MtgOaItem> listSchedules(Map<String, Object> params);

    /** 按 OA 日程ID 取单条（“重推”用）。 */
    MtgOaItem getSchedule(Map<String, Object> params);

    /** 一条日程的参与人 OA 人员ID（workplanMember.resourceid）。 */
    List<String> listScheduleMembers(Map<String, Object> params);

    /**
     * 把一批 OA 人员ID 换成该租户已绑定的平台成员ID（uf_oaones_user_bind，
     * bind_state = 1 且 x_user_id 非空）；未绑定的 OA 人员不出现在结果里。
     */
    List<String> resolvePlatformUserIds(Map<String, Object> params);

    // ---------------- 个人同步开关（uf_oaones_mtg_sync_user） ----------------

    /**
     * 按（业务 / 平台 / 租户 / 人员）个人同步开关：一行一个「业务 + 平台 + 租户 + 本人」，
     * 没有行 = 允许同步（默认所有人同步）。条件可选 biz / type / cfgId / oaUserId / cfgIds / enabled。
     */
    List<MtgSyncUser> listUserPref(Map<String, Object> params);

    /** 取某个「业务 + 平台 + 租户 + 人员」的个人同步开关（没有则返回 null = 默认允许）。 */
    MtgSyncUser getUserPref(Map<String, Object> params);

    /**
     * 「个人同步方向」只读列表：该「业务 + 平台（+ 可选租户 / 可维护租户集合）」下
     * 非“遵循系统设置”（{@code dir_mode = 1}）的个人及其具体方向（联表带租户名称与 OA 人员姓名）。
     */
    List<MtgSyncUser> listUserDirPref(Map<String, Object> params);

    void insertUserPref(Map<String, Object> params);

    void updateUserPref(Map<String, Object> params);

    // ---------------- 对象映射（uf_oaones_mtg_link，第三方向拉取用） ----------------

    /** 按平台对象ID 找映射（没有返回 null）。 */
    MtgSyncLink findLinkByWx(Map<String, Object> params);

    /** 按 OA 对象ID 找映射（没有返回 null）。 */
    MtgSyncLink findLinkByOa(Map<String, Object> params);

    /**
     * 该「业务 + 平台 + 租户」（可选来源）下的映射列表，供引擎逐个对象做
     * **删除对账**（源端还在不在）与**变更比对**（快照列与当前内容是否一致）。
     */
    List<MtgSyncLink> listLink(Map<String, Object> params);

    /** 该「业务 + 平台 + 租户」下由「第三方→OA」拉取建立的 OA 对象ID 集合（推送时据此跳过）。 */
    List<String> listPlatformOriginOaIds(Map<String, Object> params);

    /**
     * 兼容升级：映射表还没有对应行时，从**推送日志**里按平台对象ID 反查 OA 对象ID
     * （此前由 OA 推送到平台的对象只有日志、没有映射；拉取时要能认出它们，避免在 OA 又建一条重复记录）。
     */
    String findLoggedOaIdByWx(Map<String, Object> params);

    void insertLink(Map<String, Object> params);

    void updateLink(Map<String, Object> params);

    /** 删除一条映射（对象已按“源端删除”同步完，或映射本身已失效）。 */
    void deleteLink(Map<String, Object> params);

    // ---------------- 第三方向拉取：平台成员 ⇄ OA 人员（第三方向拉取用） ----------------

    /** 该租户已绑定到 OA 人员的平台成员ID（拉取的日历候选，一次取全）。 */
    List<String> listBoundPlatformUserIds(Map<String, Object> params);

    /** 平台成员ID → OA 人员ID（未绑定返回 null）。 */
    String mapOaUserByWx(Map<String, Object> params);

    /** 一批平台成员ID → OA 人员ID（返回 wx_user_id / oa_user_id 两列，参与人批量换算）。 */
    List<Map<String, Object>> mapOaUsersByWx(Map<String, Object> params);

    /** 取某个 OA 会议 / 日程的创建人 OA 人员ID（拉取更新前做本人的方向 / 开关校验）。 */
    String getOaOwnerId(Map<String, Object> params);

    // ---------------- 第三方向拉取：写入 / 更新 OA 侧对象（平台 → OA） ----------------

    /** 新建 OA 会议（Meeting，id 自增，insert 后用 {@code selectKey} 回填 {@code e.id}）。 */
    void insertMeeting(Map<String, Object> params);

    /** 更新 OA 会议（只改会议内容字段，不碰创建人 / 状态 / 取消标记）。 */
    void updateMeeting(Map<String, Object> params);

    /**
     * 标记删除 OA 会议（{@code Meeting.cancel = '1'}）。
     *
     * <p>平台侧把日程删掉 / 取消后由订阅回调或删除对账调用——按约定<b>不做物理删除</b>，
     * 只打取消标记；打上标记后会议不再进入同步候选（{@code listMeetings} 已过滤）。</p>
     */
    void markMeetingDeleted(Map<String, Object> params);

    /** 删除某会议的人员参与行（Meeting_Member2，membertype = 0）。 */
    void deleteMeetingMembers(Map<String, Object> params);

    /** 写一条会议参与人（Meeting_Member2，membertype = 0）。 */
    void insertMeetingMember(Map<String, Object> params);

    /** 新建 OA 日程（WorkPlan，id 自增，insert 后用 {@code selectKey} 回填 {@code e.id}）。 */
    void insertWorkPlan(Map<String, Object> params);

    /** 更新 OA 日程（只改日程内容字段）。 */
    void updateWorkPlan(Map<String, Object> params);

    /** 标记删除 OA 日程（{@code WorkPlan.deleted = '1'}）；同 {@link #markMeetingDeleted}，不物理删除。 */
    void markWorkPlanDeleted(Map<String, Object> params);

    /** 删除某日程的人员参与行（workplanMember）。 */
    void deleteWorkplanMembers(Map<String, Object> params);

    /** 写一条日程参与人（workplanMember）。 */
    void insertWorkplanMember(Map<String, Object> params);
}
