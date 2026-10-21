package com.customization.oaones.appsyn.job.wecom;

import com.customization.oaones.appsyn.service.wecom.UserTmpService;
import com.customization.oaones.appsyn.service.impl.wecom.UserTmpServiceImpl;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 企微人员快照定时刷新任务。
 *
 * <p>在 E9 后台“计划任务”中新建任务，Java 类填写本类全限定名，
 * 并按需配置 cron 表达式：com.customization.oaones.appsyn.job.wecom.OaonesUserTmpCronJob。</p>
 */
public class OaonesUserTmpCronJob extends BaseCronJob {

    @Override
    public void execute() {
        try {
            UserTmpService userTmpService = new UserTmpServiceImpl();
            userTmpService.refreshAll();
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones user tmp cron job error: " + t);
        }
    }
}
