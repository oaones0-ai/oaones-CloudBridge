package com.customization.oaones.appsyn.job.wecom;

import com.customization.oaones.appsyn.service.wecom.DeptTmpService;
import com.customization.oaones.appsyn.service.impl.wecom.DeptTmpServiceImpl;
import weaver.general.BaseBean;
import weaver.interfaces.schedule.BaseCronJob;

/**
 * 企微部门快照定时刷新任务。
 *
 * <p>在 E9 后台“计划任务”中新建任务，Java 类填写本类全限定名，
 * 并按需配置 cron 表达式：com.customization.oaones.appsyn.job.wecom.OaonesDeptTmpCronJob。</p>
 */
public class OaonesDeptTmpCronJob extends BaseCronJob {

    @Override
    public void execute() {
        try {
            DeptTmpService deptTmpService = new DeptTmpServiceImpl();
            deptTmpService.refreshAll();
        } catch (Throwable t) {
            new BaseBean().writeLog("oaones dept tmp cron job error: " + t);
        }
    }
}
