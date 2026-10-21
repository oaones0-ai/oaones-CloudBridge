package com.customization.oaones.appsyn.msg.feishu.job;

import com.customization.oaones.appsyn.msg.feishu.service.MsgConfigService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgLogService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgTaskService;
import com.customization.oaones.appsyn.msg.feishu.entity.MsgConfig;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 推送日志 / 卡片任务清理任务（按页面配置的保留天数）。
 *
 * <p>在 E9 后台“计划任务”中新建任务，Java 类填本类全限定名，建议每天凌晨一次：
 * {@code com.customization.oaones.appsyn.msg.feishu.job.OaonesMsgCleanCronJob}</p>
 */
public class OaonesMsgCleanCronJob extends BaseCronJob {

    @Override
    public void execute() {
        try {
            MsgConfig config = new MsgConfigService().get();
            new MsgLogService().cleanExpired(config.getLogKeepDays() == null ? 0 : config.getLogKeepDays());
            new MsgTaskService().cleanExpired(config.getTaskKeepDays() == null ? 0 : config.getTaskKeepDays());
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones msg clean cron job error: " + t);
        }
    }
}
