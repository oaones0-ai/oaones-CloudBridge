package com.customization.oaones.appsyn.msg.sub;

import com.alibaba.fastjson.JSONArray;
import com.cloudstore.dev.api.bean.MessageBean;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.msg.service.MsgSendService;
import com.weaver.base.msgcenter.channel.IMessageReceive;

import java.util.List;
import java.util.Map;

/**
 * 泛微标准消息中心「消息订阅」推送实现类：把标准消息转推到企业微信。
 *
 * <p>注册方式（后台 应用中心 -> 消息类型 / 消息订阅，可视化配置，无需改代码）：</p>
 * <ul>
 *   <li>通道类型：Direct（直接推送）；</li>
 *   <li>推送类：{@code com.customization.oaones.appsyn.msg.sub.QywxMsgReceiveSub}；</li>
 *   <li>新模式：是（NEWMODE=1）；</li>
 *   <li>消息类型：流程提醒 / 会议提醒 等需要推送到企业微信的标准消息类型。</li>
 * </ul>
 *
 * <p>标准消息中心回调 {@code onMessage(JSON 数组)}，本类只负责解析与投递，
 * 具体推送规则（消息类型映射、模板、开关、分片、去重、重试、日志）全部在
 * “消息中心”模块页面可视化配置。</p>
 */
public class QywxMsgReceiveSub implements IMessageReceive {

    @Override
    public void onMessage(String message) {
        if (message == null || message.trim().length() == 0) {
            return;
        }
        final String payload = message;
        boolean submitted = MsgPushQueue.submit(new Runnable() {
            @Override
            public void run() {
                handle(payload);
            }
        });
        if (!submitted) {
            handle(payload);
        }
    }

    /** 解析标准消息并逐条投递到推送服务。 */
    private void handle(String message) {
        try {
            List<MessageBean> list = JSONArray.parseArray(message, MessageBean.class);
            if (list == null || list.isEmpty()) {
                return;
            }
            MsgSendService sendService = new MsgSendService();
            for (MessageBean bean : list) {
                if (bean == null) {
                    continue;
                }
                try {
                    sendService.pushFromStdMessage(bean);
                } catch (Throwable t) {
                    OaonesLog.error("[msg] 标准消息推送失败（单条），已忽略", t);
                }
            }
        } catch (Throwable t) {
            OaonesLog.error("[msg] 标准消息解析失败：" + message, t);
        }
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onEnd() {
    }

    @Override
    public void setProp(Map<String, String> map) {
    }

    @Override
    public void setList(List<IMessageReceive> list) {
    }
}
