package com.customization.oaones.appsyn.job.wecom;

import com.customization.oaones.appsyn.service.wecom.DeptTmpService;
import com.customization.oaones.appsyn.service.wecom.SyncService;
import com.customization.oaones.appsyn.service.wecom.UserTmpService;
import com.customization.oaones.appsyn.service.impl.wecom.DeptTmpServiceImpl;
import com.customization.oaones.appsyn.service.impl.wecom.SyncServiceImpl;
import com.customization.oaones.appsyn.service.impl.wecom.UserTmpServiceImpl;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 定时同步计划任务。
 *
 * <p>在 E9 后台“计划任务”中配置本类全限定名：
 * com.customization.oaones.appsyn.job.wecom.OaonesCronJob。</p>
 */
public class OaonesCronJob extends BaseCronJob {

    @Override
    public void execute() {
        try {
            try {
                DeptTmpService deptTmpService = new DeptTmpServiceImpl();
                deptTmpService.refreshAll();
            } catch (Throwable t) {
                new BaseBean().writeLog("oaones dept tmp refresh before sync error: " + t);
            }
            try {
                UserTmpService userTmpService = new UserTmpServiceImpl();
                userTmpService.refreshAll();
            } catch (Throwable t) {
                new BaseBean().writeLog("oaones user tmp refresh before sync error: " + t);
            }
            SyncService syncService = new SyncServiceImpl();
            syncService.runAllForCron();
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones cron job error: " + t);
        }
    }
}
