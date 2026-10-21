package com.customization.oaones.appsyn.job.feishu;

import com.customization.oaones.appsyn.service.feishu.DeptTmpService;
import com.customization.oaones.appsyn.service.feishu.SyncService;
import com.customization.oaones.appsyn.service.feishu.UserTmpService;
import com.customization.oaones.appsyn.service.impl.feishu.DeptTmpServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.SyncServiceImpl;
import com.customization.oaones.appsyn.service.impl.feishu.UserTmpServiceImpl;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 定时同步计划任务。
 *
 * <p>在 E9 后台“计划任务”中配置本类全限定名：
 * com.customization.oaones.appsyn.job.feishu.OaonesCronJob。</p>
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
