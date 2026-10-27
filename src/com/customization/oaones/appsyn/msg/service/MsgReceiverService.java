package com.customization.oaones.appsyn.msg.service;

import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.msg.common.MsgProps;
import com.customization.oaones.appsyn.msg.dao.MsgReceiverMapper;
import com.customization.oaones.appsyn.msg.dto.MsgReceiver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 接收人解析服务。
 *
 * <ul>
 *   <li>传入 OA 账号（登录名 / 工号）或 OA 人员ID：按人员绑定表
 *       {@code uf_oaones_appsyn_user_bind}（bind_state = 1）转换为企业微信 userid；</li>
 *   <li>直接传入企微 userid：优先直接使用，并反查所属租户用于应用路由；</li>
 *   <li>未绑定、已免绑定、已删除的账号自动过滤，调用方按 {@code filtered} 记录异常日志。</li>
 * </ul>
 */
public class MsgReceiverService {

    /**
     * 按 OA 人员ID解析接收人。
     *
     * @param cfgId     指定租户（0 = 不限制，一个 OA 人员可能绑定多个企微租户）
     * @param oaUserIds OA 人员ID集合
     */
    public List<MsgReceiver> resolveOaUsers(int cfgId, Collection<Integer> oaUserIds) {
        List<MsgReceiver> result = new ArrayList<MsgReceiver>();
        Set<Integer> ids = new LinkedHashSet<Integer>();
        if (oaUserIds != null) {
            for (Integer id : oaUserIds) {
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        List<Integer> idList = new ArrayList<Integer>(ids);
        List<MsgReceiver> rows = OaonesMyBatis.call(MsgReceiverMapper.class,
                mapper -> mapper.listByOaUsers(MsgProps.userBindTable(), MsgProps.cfgTable(), cfgId > 0 ? cfgId : null, idList));
        if (rows != null) {
            result.addAll(rows);
        }
        return dedup(result);
    }

    /** 按“登录名 / 工号”解析接收人（页面测试推送与统一消息接口按 OA 账号传参时使用）。 */
    public List<MsgReceiver> resolveAccounts(int cfgId, Collection<String> accounts) {
        List<MsgReceiver> result = new ArrayList<MsgReceiver>();
        if (accounts == null || accounts.isEmpty()) {
            return result;
        }
        Set<String> values = new LinkedHashSet<String>();
        for (String account : accounts) {
            if (account != null && account.trim().length() > 0) {
                values.add(account.trim());
            }
        }
        if (values.isEmpty()) {
            return result;
        }
        List<Integer> oaIds = OaonesMyBatis.call(MsgReceiverMapper.class,
                mapper -> mapper.findOaUserIdsByAccounts(new ArrayList<String>(values)));
        if (oaIds == null || oaIds.isEmpty()) {
            OaonesLog.step("[msg] 接收人账号在 OA 中不存在：" + values);
            return result;
        }
        return resolveOaUsers(cfgId, oaIds);
    }

    /** 直接按企微 userid 解析（反查到的租户用于应用路由）。 */
    public List<MsgReceiver> resolveWxUserids(Collection<String> wxUserids) {
        List<MsgReceiver> result = new ArrayList<MsgReceiver>();
        Set<String> values = new LinkedHashSet<String>();
        if (wxUserids != null) {
            for (String value : wxUserids) {
                if (value != null && value.trim().length() > 0) {
                    values.add(value.trim());
                }
            }
        }
        if (values.isEmpty()) {
            return result;
        }
        List<MsgReceiver> rows = OaonesMyBatis.call(MsgReceiverMapper.class,
                mapper -> mapper.listByWxUserids(MsgProps.userBindTable(), MsgProps.cfgTable(), new ArrayList<String>(values)));
        Map<String, MsgReceiver> matched = new LinkedHashMap<String, MsgReceiver>();
        if (rows != null) {
            for (MsgReceiver row : rows) {
                matched.put(row.getWxUserid(), row);
            }
        }
        for (String value : values) {
            MsgReceiver row = matched.get(value);
            if (row != null) {
                result.add(row);
            } else {
                // 未建立绑定的企微账号仍按“直接传入”使用，租户未知时回落到缺省应用
                MsgReceiver direct = new MsgReceiver();
                direct.setWxUserid(value);
                result.add(direct);
            }
        }
        return result;
    }

    /**
     * 给接收人补 OA 姓名 / 登录名 / OA 语言（日志展示与多语言取值用；
     * 直接传企微账号的接收人无 OA 信息）。
     *
     * <p>语言取自 {@code hrmresource.systemlanguage}：消息里的 E9 多语言标记
     * （{@code ~`~`7 中文`~`8 English`~`~}）按接收人的 OA 语言取成纯文本，
     * 取不到时由渲染层回落到系统默认语言。</p>
     */
    public void fillOaInfo(List<MsgReceiver> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            return;
        }
        Set<Integer> ids = new LinkedHashSet<Integer>();
        for (MsgReceiver receiver : receivers) {
            if (receiver.getOaUserId() > 0) {
                ids.add(receiver.getOaUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        List<MsgReceiver> rows = OaonesMyBatis.call(MsgReceiverMapper.class,
                mapper -> mapper.listOaUsers(new ArrayList<Integer>(ids)));
        Map<Integer, MsgReceiver> map = new LinkedHashMap<Integer, MsgReceiver>();
        if (rows != null) {
            for (MsgReceiver row : rows) {
                map.put(row.getOaUserId(), row);
            }
        }
        for (MsgReceiver receiver : receivers) {
            MsgReceiver info = map.get(receiver.getOaUserId());
            if (info != null) {
                receiver.setOaName(info.getOaName());
                receiver.setOaCode(info.getOaCode());
                if (info.getLanguage() > 0) {
                    receiver.setLanguage(info.getLanguage());
                }
            }
        }
    }

    /** 去重：同一租户下同一企微 userid 只保留一条。 */
    private List<MsgReceiver> dedup(List<MsgReceiver> rows) {
        Map<String, MsgReceiver> map = new LinkedHashMap<String, MsgReceiver>();
        if (rows != null) {
            for (MsgReceiver row : rows) {
                if (row == null || row.getWxUserid() == null || row.getWxUserid().trim().length() == 0) {
                    continue;
                }
                map.put(row.getCfgId() + "|" + row.getWxUserid(), row);
            }
        }
        return new ArrayList<MsgReceiver>(map.values());
    }
}
