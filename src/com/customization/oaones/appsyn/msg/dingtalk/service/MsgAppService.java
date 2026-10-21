package com.customization.oaones.appsyn.msg.dingtalk.service;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.dingtalk.api.DingTalkMsgApi;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgConst;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgProps;
import com.customization.oaones.appsyn.msg.dingtalk.dao.MsgAppMapper;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgApp;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 推送用钉钉应用服务（**只读**“应用绑定”表 uf_oaones_app_bind）。
 *
 * <p>合并口径：钉钉自建应用只在“应用绑定”页维护一次（应用名 / AgentId / 应用 Secret /
 * 所属租户 / 启停），消息中心直接引用同一行；因此本服务不再提供新增、修改、删除，
 * 只提供列表、按接收人租户路由、缺省应用（记录在 uf_oaones_msg_config.default_app_id）
 * 与连通测试。</p>
 */
public class MsgAppService {

    /** 全部应用（含停用；页面用于展示与选择默认应用）。 */
    public List<MsgApp> list() {
        List<MsgApp> list = OaonesMyBatis.call(MsgAppMapper.class,
                mapper -> mapper.list(DingTalkMsgProps.appBindTable(), DingTalkMsgProps.cfgTable()));
        return list == null ? new ArrayList<MsgApp>() : list;
    }

    /** 启用中的应用。 */
    public List<MsgApp> listEnabled() {
        List<MsgApp> list = OaonesMyBatis.call(MsgAppMapper.class,
                mapper -> mapper.listEnabled(DingTalkMsgProps.appBindTable(), DingTalkMsgProps.cfgTable()));
        return list == null ? new ArrayList<MsgApp>() : list;
    }

    /** 按应用绑定记录ID取一条（uf_oaones_app_bind.id）。 */
    public MsgApp getById(int id) {
        if (id <= 0) {
            return null;
        }
        return OaonesMyBatis.call(MsgAppMapper.class,
                mapper -> mapper.getById(DingTalkMsgProps.appBindTable(), DingTalkMsgProps.cfgTable(), id));
    }

    /**
     * 缺省应用：优先取“推送配置”里维护的缺省应用（{@code uf_oaones_msg_config.default_app_id}），
     * 该配置为空或对应应用已停用/删除时，回退到第一个启用中的应用。
     */
    public MsgApp defaultApp() {
        MsgConfig config = new MsgConfigService().get();
        MsgApp app = getById(config.getDefaultAppId() == null ? 0 : config.getDefaultAppId());
        if (isUsable(app)) {
            return app;
        }
        List<MsgApp> enabled = listEnabled();
        for (MsgApp item : enabled) {
            if (isUsable(item)) {
                return item;
            }
        }
        return null;
    }

    /** 指定租户下启用中的应用（该租户有多个应用时取ID最小的一个）。 */
    public MsgApp getByCfgId(int cfgId) {
        if (cfgId <= 0) {
            return null;
        }
        List<MsgApp> list = OaonesMyBatis.call(MsgAppMapper.class,
                mapper -> mapper.listEnabledByCfgId(DingTalkMsgProps.appBindTable(), DingTalkMsgProps.cfgTable(), cfgId));
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 按“消息类型指定应用 -> 接收人所属租户 -> 缺省应用”的顺序解析应用。
     *
     * @param typeAppId 消息类型上指定的应用ID（0 表示不指定）
     * @param cfgId     接收人所属组织同步租户ID（0 表示未知）
     */
    public MsgApp resolveApp(int typeAppId, int cfgId) {
        MsgApp app = getById(typeAppId);
        if (isUsable(app)) {
            return app;
        }
        app = getByCfgId(cfgId);
        if (isUsable(app)) {
            return app;
        }
        app = defaultApp();
        return isUsable(app) ? app : null;
    }

    /** 该应用是否可用于推送：启用 + 租户企业ID + AgentId + 有效 Secret（应用 Secret 或租户兜底）。 */
    public boolean isUsable(MsgApp app) {
        return app != null && app.getId() != null
                && app.getYx() != null && app.getYx() == DingTalkMsgConst.STATE_ENABLE
                && notBlank(app.getCorpId()) && notBlank(app.getAgentId())
                && notBlank(app.getEffectiveSecret());
    }

    /**
     * 把某个应用设为“推送缺省应用”（只改 uf_oaones_msg_config.default_app_id，
     * 应用本身仍在“应用绑定”页维护）。
     */
    public void setDefaultApp(int appId, int operatorId) {
        MsgApp app = getById(appId);
        if (app == null) {
            throw new RuntimeException("应用不存在（可能已在应用绑定页删除）");
        }
        MsgConfigService configService = new MsgConfigService();
        MsgConfig config = configService.get();
        config.setDefaultAppId(appId);
        configService.save(config, operatorId);
        OaonesLog.step("[msg] 设置推送缺省应用 appId=" + appId + " operatorId=" + operatorId);
    }

    /**
     * 连通测试：用该应用的有效 Secret 拉取 access_token，验证企业ID / AgentId / 密钥是否正确。
     */
    public JSONObject test(MsgApp app) {
        JSONObject result = new JSONObject();
        if (app == null) {
            result.put("success", false);
            result.put("msg", "应用不存在（请确认应用绑定页里的应用是否已删除）");
            return result;
        }
        String secret = app.getEffectiveSecret();
        if (!notBlank(app.getCorpId()) || !notBlank(secret)) {
            result.put("success", false);
            result.put("msg", "该应用缺少企业ID或应用 Secret（可在应用绑定页补全，或由租户配置的 Secret 兜底）");
            return result;
        }
        // 页面上可能刚改过密钥：先清掉旧缓存再验证
        DingTalkMsgApi.clearToken(app.getCorpId(), secret);
        try {
            DingTalkMsgApi api = new DingTalkMsgApi(app.getCorpId(), secret, 0);
            String token = api.accessToken(true);
            result.put("success", true);
            result.put("msg", "钉钉鉴权成功");
            result.put("appId", app.getId());
            result.put("corpId", app.getCorpId());
            result.put("agentId", app.getAgentId());
            result.put("secretSource", app.isSecretFromCfg() ? "租户配置 Secret 兜底" : "应用自己的 Secret");
            result.put("tokenTail", token.length() <= 6 ? token : token.substring(token.length() - 6));
            result.put("tokenInfo", api.tokenInfo());
        } catch (Exception e) {
            OaonesLog.error("[msg] 钉钉应用连通测试失败 corpId=" + app.getCorpId()
                    + " agentId=" + app.getAgentId(), e);
            result.put("success", false);
            result.put("msg", e.getMessage() == null ? "连通测试失败" : e.getMessage());
        }
        return result;
    }

    /** 页面展示用：密钥尾号（不回显明文）。 */
    public String secretTail(MsgApp app) {
        if (app == null) {
            return "";
        }
        String secret = app.getEffectiveSecret();
        if (!notBlank(secret)) {
            return "";
        }
        String tail = secret.length() <= 4 ? "****" : "****" + secret.substring(secret.length() - 4);
        return app.isSecretFromCfg() ? tail + "（租户兜底）" : tail;
    }

    private boolean notBlank(String value) {
        return value != null && value.trim().length() > 0;
    }
}
