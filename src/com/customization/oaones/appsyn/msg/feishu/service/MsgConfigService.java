package com.customization.oaones.appsyn.msg.feishu.service;

import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.feishu.common.FeishuMsgProps;
import com.customization.oaones.appsyn.msg.feishu.dao.MsgConfigMapper;
import com.customization.oaones.appsyn.msg.feishu.entity.MsgConfig;

/**
 * 消息中心系统参数服务（可视化热更新，无需改配置文件、无需重启）。
 *
 * <p>数据库无配置行时用 oaones.appsyn.properties 的缺省值兜底，保证首次部署即可用。</p>
 */
public class MsgConfigService {

    /** 读取系统参数（缺行返回 properties 缺省值）。 */
    public MsgConfig get() {
        MsgConfig config = OaonesMyBatis.call(MsgConfigMapper.class,
                mapper -> mapper.getOne(FeishuMsgProps.configTable()));
        if (config == null) {
            config = defaultConfig();
        }
        normalize(config);
        return config;
    }

    /** 保存系统参数（单行配置：存在则更新，不存在则插入）。 */
    public void save(MsgConfig config, int operatorId) {
        normalize(config);
        config.setModedatamodifier(operatorId);
        config.setModedatamodifydatetime(OaonesUtil.now());
        MsgConfig exists = OaonesMyBatis.call(MsgConfigMapper.class,
                mapper -> mapper.getOne(FeishuMsgProps.configTable()));
        if (exists == null || exists.getId() == null) {
            OaonesMyBatis.run(MsgConfigMapper.class,
                    mapper -> mapper.insert(FeishuMsgProps.configTable(), config));
        } else {
            config.setId(exists.getId());
            OaonesMyBatis.run(MsgConfigMapper.class,
                    mapper -> mapper.update(FeishuMsgProps.configTable(), config));
        }
        OaonesLog.step("[msg] 保存消息中心系统参数 operatorId=" + operatorId
                + " pushEnable=" + config.getPushEnable());
    }

    /** 推送总开关是否打开。 */
    public boolean isPushEnabled() {
        Integer enable = get().getPushEnable();
        return enable != null && enable == 1;
    }

    private MsgConfig defaultConfig() {
        MsgConfig config = new MsgConfig();
        config.setPushEnable(FeishuMsgProps.pushEnable());
        config.setDefaultAppId(0);
        config.setRetryTimes(FeishuMsgProps.retryTimes());
        config.setRetryInterval(FeishuMsgProps.retryInterval());
        config.setShardSize(FeishuMsgProps.shardSize());
        config.setTimeoutMs(FeishuMsgProps.timeout());
        config.setTokenAdvanceSec(FeishuMsgProps.tokenAdvanceSec());
        config.setDedupWindowSec(FeishuMsgProps.dedupWindow());
        config.setLogKeepDays(FeishuMsgProps.logKeepDays());
        config.setTaskKeepDays(FeishuMsgProps.taskKeepDays());
        config.setRemark("缺省参数（oaones.appsyn.properties），保存后以数据库为准");
        return config;
    }

    /** 参数口径兜底：为空/非法的取值回落到缺省值，避免页面误填导致推送异常。 */
    private void normalize(MsgConfig config) {
        if (config.getPushEnable() == null) {
            config.setPushEnable(1);
        }
        if (config.getDefaultAppId() == null) {
            config.setDefaultAppId(0);
        }
        if (config.getRetryTimes() == null || config.getRetryTimes() < 0) {
            config.setRetryTimes(FeishuMsgProps.retryTimes());
        }
        if (config.getRetryInterval() == null || config.getRetryInterval() < 10) {
            config.setRetryInterval(FeishuMsgProps.retryInterval());
        }
        if (config.getShardSize() == null || config.getShardSize() <= 0 || config.getShardSize() > 1000) {
            config.setShardSize(1000);
        }
        if (config.getTimeoutMs() == null || config.getTimeoutMs() < 1000) {
            config.setTimeoutMs(FeishuMsgProps.timeout());
        }
        if (config.getTokenAdvanceSec() == null || config.getTokenAdvanceSec() < 0) {
            config.setTokenAdvanceSec(FeishuMsgProps.tokenAdvanceSec());
        }
        if (config.getDedupWindowSec() == null || config.getDedupWindowSec() < 0) {
            config.setDedupWindowSec(FeishuMsgProps.dedupWindow());
        }
        if (config.getLogKeepDays() == null || config.getLogKeepDays() < 0) {
            config.setLogKeepDays(FeishuMsgProps.logKeepDays());
        }
        if (config.getTaskKeepDays() == null || config.getTaskKeepDays() < 0) {
            config.setTaskKeepDays(FeishuMsgProps.taskKeepDays());
        }
    }
}
