package com.customization.oaones.appsyn.msg.dingtalk.job;

import com.customization.oaones.appsyn.msg.dingtalk.service.MsgSendService;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 消息推送失败自动重试任务。
 *
 * <p>在 E9 后台“计划任务”中新建任务，Java 类填本类全限定名，
 * cron 建议每 1~5 分钟一次（重试次数/间隔在消息中心页面上配置）：
 * {@code com.customization.oaones.appsyn.msg.dingtalk.job.OaonesMsgRetryCronJob}</p>
 */
public class OaonesMsgRetryCronJob extends BaseCronJob {

    /** 单次任务最多重试的日志条数（避免一次拉太多拖慢数据库）。 */
    private static final int BATCH_LIMIT = 200;

    @Override
    public void execute() {
        try {
            new MsgSendService().retryPending(BATCH_LIMIT);
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones msg retry cron job error: " + t);
        }
    }
}
