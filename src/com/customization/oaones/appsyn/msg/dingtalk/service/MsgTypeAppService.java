package com.customization.oaones.appsyn.msg.dingtalk.service;

import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgProps;
import com.customization.oaones.appsyn.msg.dingtalk.dao.MsgTypeAppMapper;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgTypeApp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息类型「推送目标应用」明细服务：一行 = 一个消息类型 + 一个租户 + 一个应用。
 *
 * <p><b>口径</b>：页面「消息类型」的“推送目标应用”列不再是一个全局应用下拉，而是
 * <b>按租户逐行设置</b>——同一个消息类型在每个租户上最多一个应用（唯一键
 * {@code type + std_config_id + std_type + cfg_id}）。推送时按“接收人所属租户在该消息类型上
 * 绑定的应用”优先取用（见 {@link MsgSendService}），没绑定该租户时保持原来的回退顺序
 * （消息类型上指定的应用 → 接收人租户的应用 → 缺省应用）。</p>
 *
 * <p>{@code std_config_id} / {@code std_type} 的口径与 {@link MsgTypeService} 一致：
 * 标准消息类型用 {@code std_config_id}（字符串），未纳入标准消息类型的独立来源用 {@code std_type}
 * （来源编号 typecode），两者的 {@code std_config_id} 为空串。</p>
 */
public class MsgTypeAppService {

    /** 明细表还没建（老库未执行升级脚本）时，只提示一次完整建表口径，避免每条推送都刷一遍长日志。 */
    private static volatile boolean tableMissingWarned = false;

    /** 明细行分组键：{@code std_config_id + "|" + std_type}（与页面上的行一一对应）。 */
    public static String keyOf(String stdConfigId, int stdType) {
        return stdKey(stdConfigId) + "|" + Math.max(stdType, 0);
    }

    /** 标准消息类型ID 归一：{@code null} / 空串 / {@code "0"} 一律按“独立来源”处理（空串）。 */
    public static String stdKey(String stdConfigId) {
        String key = stdConfigId == null ? "" : stdConfigId.trim();
        return "0".equals(key) ? "" : key;
    }

    /**
     * 本平台全部绑定：{@code 分组键 -> (租户ID -> 应用ID)}。
     *
     * <p>页面「消息类型」列表一次取回后按类型拼“已设置目标应用的数量”与明细预填值，
     * 因此这里整表读一次，避免按类型逐行查询。</p>
     */
    public Map<String, Map<Integer, Integer>> allBinds() {
        Map<String, Map<Integer, Integer>> result = new LinkedHashMap<String, Map<Integer, Integer>>();
        List<MsgTypeApp> rows;
        try {
            rows = list();
        } catch (Throwable t) {
            emptyBinds(t);
            return result;
        }
        for (MsgTypeApp row : rows) {
            if (row == null || row.getCfgId() == null) {
                continue;
            }
            int appId = row.getAppId() == null ? 0 : row.getAppId();
            if (appId <= 0) {
                continue;
            }
            String key = keyOf(row.getStdConfigId(), row.getStdType() == null ? 0 : row.getStdType());
            Map<Integer, Integer> binds = result.get(key);
            if (binds == null) {
                binds = new LinkedHashMap<Integer, Integer>();
                result.put(key, binds);
            }
            binds.put(row.getCfgId(), appId);
        }
        return result;
    }

    /** 某个消息类型（标准类型或独立来源）的绑定：{@code 租户ID -> 应用ID}。 */
    public Map<Integer, Integer> bindsOf(String stdConfigId, int stdType) {
        Map<Integer, Integer> binds = new LinkedHashMap<Integer, Integer>();
        List<MsgTypeApp> rows;
        try {
            rows = listByKey(stdConfigId, stdType);
        } catch (Throwable t) {
            return emptyBinds(t);
        }
        for (MsgTypeApp row : rows) {
            if (row == null || row.getCfgId() == null) {
                continue;
            }
            int appId = row.getAppId() == null ? 0 : row.getAppId();
            if (appId > 0) {
                binds.put(row.getCfgId(), appId);
            }
        }
        return binds;
    }

    /**
     * 推送用：某个消息类型“按租户绑定的应用”（只有 {@code appId > 0} 的行），
     * 键是租户ID（{@code MsgReceiver#getCfgId()}）。
     */
    public Map<Integer, Integer> bindsForPush(String stdConfigId, int stdType) {
        String key = stdKey(stdConfigId);
        if (key.length() == 0 && stdType <= 0) {
            return new LinkedHashMap<Integer, Integer>();
        }
        return bindsOf(key, stdType);
    }

    /**
     * 保存某个消息类型的“租户 → 应用”明细（<b>整体覆盖</b>）：
     * 传进来的绑定落库，库里多余的绑定删掉，{@code appId <= 0} 的租户＝不绑定（按回退口径推送）。
     *
     * @param stdConfigId 标准消息类型ID（独立来源传空串 / {@code "0"}）
     * @param stdType     独立来源编号 typecode（标准类型传 0）
     * @param binds       页面提交的“租户ID → 应用ID”（可为空 Map，表示全部取消绑定）
     * @param operatorId  操作人（OA 人员ID）
     * @return 实际生效的绑定条数（{@code appId > 0}）
     */
    public int save(String stdConfigId, int stdType, Map<Integer, Integer> binds, int operatorId) {
        try {
            return doSave(stdConfigId, stdType, binds, operatorId);
        } catch (RuntimeException e) {
            if (isTableMissing(e)) {
                throw new RuntimeException("“推送目标应用”明细表还没建，" + tableMissingHint());
            }
            throw e;
        } catch (Throwable t) {
            if (isTableMissing(t)) {
                throw new RuntimeException("“推送目标应用”明细表还没建，" + tableMissingHint());
            }
            throw new RuntimeException(t);
        }
    }

    private int doSave(String stdConfigId, int stdType, Map<Integer, Integer> binds, int operatorId) {
        String key = stdKey(stdConfigId);
        int type = Math.max(stdType, 0);
        if (key.length() == 0 && type <= 0) {
            throw new RuntimeException("缺少消息类型编号（或消息来源编号）");
        }
        Map<Integer, Integer> target = new LinkedHashMap<Integer, Integer>();
        if (binds != null) {
            for (Map.Entry<Integer, Integer> entry : binds.entrySet()) {
                Integer cfgId = entry.getKey();
                Integer appId = entry.getValue();
                if (cfgId == null || cfgId <= 0 || appId == null || appId <= 0) {
                    continue;
                }
                target.put(cfgId, appId);
            }
        }
        List<MsgTypeApp> exists = listByKey(key, type);
        Map<Integer, MsgTypeApp> existsByCfg = new LinkedHashMap<Integer, MsgTypeApp>();
        for (MsgTypeApp row : exists) {
            if (row != null && row.getCfgId() != null) {
                existsByCfg.put(row.getCfgId(), row);
            }
        }
        int saved = 0;
        // 提交的行：新增或改应用
        for (Map.Entry<Integer, Integer> entry : target.entrySet()) {
            MsgTypeApp row = existsByCfg.get(entry.getKey());
            if (row == null) {
                MsgTypeApp entity = new MsgTypeApp();
                entity.setStdConfigId(key);
                entity.setStdType(type);
                entity.setCfgId(entry.getKey());
                entity.setAppId(entry.getValue());
                entity.setModedatacreater(operatorId);
                entity.setModedatacreatedate(OaonesUtil.nowDate());
                entity.setModedatacreatetime(OaonesUtil.nowTime());
                entity.setModedatamodifier(operatorId);
                entity.setModedatamodifydatetime(OaonesUtil.now());
                OaonesMyBatis.run(MsgTypeAppMapper.class,
                        mapper -> mapper.insert(DingTalkMsgProps.typeAppTable(), entity));
                saved++;
            } else {
                int oldAppId = row.getAppId() == null ? 0 : row.getAppId();
                if (oldAppId != entry.getValue()) {
                    row.setAppId(entry.getValue());
                    row.setModedatamodifier(operatorId);
                    row.setModedatamodifydatetime(OaonesUtil.now());
                    OaonesMyBatis.run(MsgTypeAppMapper.class,
                            mapper -> mapper.update(DingTalkMsgProps.typeAppTable(), row));
                }
                saved++;
            }
        }
        // 库里多余的行：页面已经取消了绑定
        for (MsgTypeApp row : exists) {
            if (row == null || row.getCfgId() == null || target.containsKey(row.getCfgId())) {
                continue;
            }
            OaonesMyBatis.run(MsgTypeAppMapper.class,
                    mapper -> mapper.deleteById(DingTalkMsgProps.typeAppTable(), row.getId() == null ? 0 : row.getId()));
        }
        OaonesLog.step("[msg] 保存消息类型推送目标应用明细 stdConfigId=" + key + " stdType=" + type
                + " tenantRows=" + target.size() + " operatorId=" + operatorId);
        return saved;
    }

    /** 消息类型在本模块被删除（恢复默认口径）时，一并清掉它的租户应用明细。 */
    public void deleteByType(String stdConfigId, int stdType) {
        String key = stdKey(stdConfigId);
        int type = Math.max(stdType, 0);
        if (key.length() == 0 && type <= 0) {
            return;
        }
        OaonesMyBatis.run(MsgTypeAppMapper.class,
                mapper -> mapper.deleteByKey(DingTalkMsgProps.typeAppTable(), key, type));
    }

    /** 单聊读取：某个消息类型 + 某个租户绑定的应用ID（没绑定返回 0）。 */
    public int appIdForTenant(String stdConfigId, int stdType, int cfgId) {
        if (cfgId <= 0) {
            return 0;
        }
        MsgTypeApp row;
        try {
            row = OaonesMyBatis.call(MsgTypeAppMapper.class,
                    mapper -> mapper.getByKeyAndCfg(DingTalkMsgProps.typeAppTable(), stdKey(stdConfigId),
                            Math.max(stdType, 0), cfgId));
        } catch (Throwable t) {
            emptyBinds(t);
            return 0;
        }
        if (row == null || row.getAppId() == null) {
            return 0;
        }
        return row.getAppId();
    }

    private List<MsgTypeApp> list() {
        List<MsgTypeApp> rows = OaonesMyBatis.call(MsgTypeAppMapper.class,
                mapper -> mapper.list(DingTalkMsgProps.typeAppTable()));
        return rows == null ? new java.util.ArrayList<MsgTypeApp>() : rows;
    }

    private List<MsgTypeApp> listByKey(String stdConfigId, int stdType) {
        List<MsgTypeApp> rows = OaonesMyBatis.call(MsgTypeAppMapper.class,
                mapper -> mapper.listByKey(DingTalkMsgProps.typeAppTable(), stdKey(stdConfigId),
                        Math.max(stdType, 0)));
        return rows == null ? new java.util.ArrayList<MsgTypeApp>() : rows;
    }

    /**
     * 明细表还没建（老库还没执行升级脚本）时把异常吞掉并按“没有任何绑定”处理。
     *
     * <p>读取路径一律走这里：<b>不让一个可选功能把整个消息模块拖挂</b>——
     * 表不存在时页面照常打开（“推送目标应用”列显示 0 个租户）、推送照原来的回退口径取应用；
     * 只有真正保存明细时才会报错，并用 {@link #tableMissingHint} 给出可执行的建表口径。</p>
     */
    private static boolean isTableMissing(Throwable t) {
        String msg = t == null ? "" : OaonesUtil.s(t.getMessage());
        String lower = msg.toLowerCase();
        return lower.indexOf("uf_oaones_msg_type_app") >= 0 || lower.indexOf("invalid object name") >= 0
                || lower.indexOf("doesn't exist") >= 0 || lower.indexOf("does not exist") >= 0
                || lower.indexOf("ora-00942") >= 0;
    }

    /** 读取路径兜底：表不存在时按“没有任何绑定”处理（只提示一次建表口径）。 */
    private static Map<Integer, Integer> emptyBinds(Throwable t) {
        if (isTableMissing(t)) {
            if (!tableMissingWarned) {
                tableMissingWarned = true;
                OaonesLog.error("[msg] 消息类型推送目标应用明细表还没建，" + tableMissingHint(), t);
            }
        } else {
            OaonesLog.error("[msg] 读取消息类型推送目标应用明细失败（按“没有绑定”处理）", t);
        }
        return new LinkedHashMap<Integer, Integer>();
    }

    /** 明细表缺失时的运维口径（与消息中心其它“可选列/表”的提示同一风格）。 */
    private static String tableMissingHint() {
        return "请先建表再使用“按租户设置推送目标应用”：新库执行 "
                + "sql/{Mysql,SQLServer,Oracle}/1.20260928oaones_tables.sql 第 17.2 段；"
                + "老库按方言执行 sql/old/sql_legacy_upgrade/20261013_009~011_create_oaones_msg_type_app_*.sql 之一";
    }
}
