package com.customization.oaones.appsyn.job.dingtalk;

import com.customization.oaones.appsyn.service.dingtalk.DeptTmpService;
import com.customization.oaones.appsyn.service.dingtalk.SyncService;
import com.customization.oaones.appsyn.service.dingtalk.UserTmpService;
import com.customization.oaones.appsyn.service.impl.dingtalk.DeptTmpServiceImpl;
import com.customization.oaones.appsyn.service.impl.dingtalk.SyncServiceImpl;
import com.customization.oaones.appsyn.service.impl.dingtalk.UserTmpServiceImpl;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 定时同步计划任务。
 *
 * <p>在 E9 后台“计划任务”中配置本类全限定名：
 * com.customization.oaones.appsyn.job.dingtalk.OaonesCronJob。</p>
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
