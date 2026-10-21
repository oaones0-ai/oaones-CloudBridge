package com.customization.oaones.appsyn.msg.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.dao.wecom.QywxConfigMapper;
import com.customization.oaones.appsyn.service.impl.wecom.ConfigServiceImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * 「消息设置 → 租户开关」：本平台（企微）租户的消息通知开关。
 *
 * <p>“消息设置”是独立的一级菜单，其中按租户决定 OA 消息要不要推到该租户：
 * 开关值落在**租户表复用列** {@code uf_oaones_qywx_config.msg_enable}（1 开启 / 0 关闭），
 * 不新建表；关掉的租户在推送前置校验里被跳过并记“已跳过”日志。</p>
 *
 * <p>钉钉 / 飞书连接器各有同名实现（分别读 {@code type = 1} / {@code type = 2} 的租户），
 * 页面把两侧的清单合并展示，各自只能改自己那一半。</p>
 */
public class MsgTenantService {

    private String table() {
        return OaonesProps.cfgTable();
    }

    /** 本平台全部租户（含维护人不可见的？不——按配置表全量返回，权限由“消息设置”的管理员口径控制）。 */
    public JSONArray list() {
        JSONArray result = new JSONArray();
        JSONArray rows = new ConfigServiceImpl().list();
        // 通知开关单独取（列还没建时返回空 Map，下面按“全部开启”处理，页面据此给提示）
        Map<String, String> flags = msgEnableMap();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String cfgId = OaonesUtil.nullToEmpty(row.getString("id"));
            JSONObject item = new JSONObject();
            item.put("cfg_id", cfgId);
            item.put("cfg_name", OaonesUtil.s(row.getString("corp_name")));
            item.put("corp_id", OaonesUtil.s(row.getString("corp_id")));
            // 同步开关：租户停用时消息同样不会推送（推送前会判启用状态）
            item.put("sync_enable", OaonesUtil.nullToEmpty(row.getString("sync_enable")));
            item.put("msg_enable", "0".equals(OaonesUtil.s(flags.get(cfgId))) ? "0" : "1");
            // 开关列是否已建好：没建时页面提示先执行 alter 脚本（此时按“全部开启”兜底，不影响推送）
            item.put("switch_ready", flags.isEmpty() ? "0" : "1");
            item.put("update_time", OaonesUtil.s(row.getString("update_time")));
            result.add(item);
        }
        return result;
    }

    /**
     * 取“租户ID → 消息通知开关”映射。
     *
     * <p>开关列 {@code msg_enable} 是后加的（`sql/public/alter_oaones_tenant_msg_enable.sql`）：
     * 老库还没执行该脚本时这里会报“Invalid column name 'msg_enable'”，
     * 直接吞掉并按“全部开启”处理——**不让一个可选功能的列把整个消息模块拖挂**，
     * 页面用 {@code switch_ready=0} 提示运维补脚本。</p>
     */
    private Map<String, String> msgEnableMap() {
        Map<String, String> flags = new HashMap<String, String>();
        try {
            java.util.List<java.util.Map<String, Object>> rows = OaonesMyBatis.call(
                    QywxConfigMapper.class, mapper -> mapper.listMsgEnable(table()));
            if (rows == null) {
                return flags;
            }
            for (java.util.Map<String, Object> row : rows) {
                Object id = row.get("id");
                Object value = row.get("msg_enable");
                if (id != null) {
                    flags.put(String.valueOf(id), value == null ? "1" : String.valueOf(value));
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("[msg] 读取租户消息通知开关失败（按“全部开启”处理；"
                    + "若提示 Invalid column name 'msg_enable'，请执行 "
                    + "sql/public/alter_oaones_tenant_msg_enable.sql）", t);
        }
        return flags;
    }

    /** 打开/关闭某租户的消息通知。 */
    public JSONObject toggle(String cfgId, String enable, String operatorId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            throw new RuntimeException("缺少租户ID");
        }
        int value = "0".equals(OaonesUtil.s(enable)) ? 0 : 1;
        OaonesLog.step("[msg] 设置租户消息通知开关 cfgId=" + id + " msgEnable=" + value
                + " operatorId=" + operatorId);
        try {
            OaonesMyBatis.call(QywxConfigMapper.class, mapper -> {
                int rows = mapper.updateMsgEnable(table(), id, value,
                        OaonesUtil.i(operatorId, 0), OaonesUtil.now());
                if (rows <= 0) {
                    throw new RuntimeException("租户消息通知开关保存失败（租户不存在？）cfgId=" + id);
                }
                return rows;
            });
        } catch (RuntimeException e) {
            throw new RuntimeException(columnHint(e), e);
        }
        JSONObject data = new JSONObject();
        data.put("cfg_id", String.valueOf(id));
        data.put("msg_enable", String.valueOf(value));
        return data;
    }

    /**
     * 该租户是否允许推送消息（推送前置校验用）。
     *
     * <p>租户不存在（配置被删）时按“不允许”处理，避免把消息推给一个已经没人维护的租户。</p>
     */
    public boolean isMsgEnabled(String cfgId) {
        int id = OaonesUtil.i(cfgId, 0);
        if (id <= 0) {
            return false;
        }
        try {
            Integer value = OaonesMyBatis.call(QywxConfigMapper.class,
                    mapper -> mapper.getMsgEnable(table(), id));
            return value == null || value.intValue() != 0;
        } catch (Throwable t) {
            // 列还没建（老库未执行 alter 脚本）：按“开启”处理，消息照常推送，不因为一个可选开关丢消息
            OaonesLog.error("[msg] 读取租户消息通知开关失败，按“开启”处理 cfgId=" + cfgId, t);
            return true;
        }
    }

    /** 把“列不存在”这类数据库异常翻译成运维可执行的提示。 */
    private String columnHint(Throwable t) {
        String msg = t == null ? "" : OaonesUtil.s(t.getMessage());
        if (msg.toLowerCase().indexOf("msg_enable") >= 0) {
            return "消息通知开关列还没建：请先执行 sql/public/alter_oaones_tenant_msg_enable.sql"
                    + "（给 " + table() + " 加 msg_enable 列），再回到本页重试";
        }
        return msg.length() == 0 ? "保存失败" : msg;
    }
}
