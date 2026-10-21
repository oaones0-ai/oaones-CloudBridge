package com.customization.oaones.appsyn.controller.wecom;

import com.customization.oaones.appsyn.controller.BaseController;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.controller.MsgController;
import weaver.general.BaseBean;

import javax.servlet.http.HttpServletRequest;

/**
 * OAones 前端 AJAX 统一入口（控制层门面）。
 *
 * <p>所有 /oaones 页面通过 action 参数调用本类，按 action 前缀路由到各模块控制器：
 * cfg. → {@link ConfigController}、dept. → {@link DeptBindController}、
 * sub. → {@link SubBindController}、
 * user. → {@link UserBindController}、sync. → {@link SyncController}、
 * account. → {@link AccountController}（个人账号中心：本人自助）、
 * app. → {@link AppBindController}（企微工作台应用 - OA 页面绑定）；
 * login. → {@link LoginController}（本连接器的扫码换身份逻辑，**不走 api.jsp**：
 * 统一登录页与扫码回调都没有登录会话，由 `/oaones/login/loginAction.jsp` 与
 * `/oaones/login/scan.jsp` 直接调用公共的 `controller.LoginController`，
 * 本处路由只为兼容 api.jsp 上 `login.*` 的老口径）；
 * logincfg. → {@link LoginConfigController}（统一登录设置：登录方式开关与二维码有效期）；
 * msg. → {@link MsgController}（统一消息中心：企微推送配置/日志/看板）；
 * overview. → {@link com.customization.oaones.appsyn.controller.BindOverviewController}
 * （绑定总览：跨平台只读汇总，见 `/oaones/index/BindOverview.jsp`）；
 * pw. → {@link com.customization.oaones.appsyn.controller.PersonalWxController}
 * （个人微信：微信授权密钥维护、个人账号中心扫码绑定 / 换绑 / 解绑，见 `/oaones/personalWx/config.jsp`）；
 * 登录校验由 api.jsp 完成，本类统一包装异常与返回结构。</p>
 */
public class OaonesController {

    private final BaseBean log = new BaseBean();

    public JSONObject handle(String action, HttpServletRequest request, String operatorId) {
        try {
            if (action == null || action.trim().length() == 0) {
                return OaonesUtil.fail("缺少 action 参数");
            }
            OaonesLog.step("收到前端请求 action=" + action + " operatorId=" + operatorId);
            // 同步中心的“页面 cron”由模块内置调度器执行（不需要 E9 后台计划任务）：
            // 这里做一次幂等启动，保证只要有人用过页面/接口，调度器就在跑
            com.customization.oaones.appsyn.job.OaonesSyncPlanScheduler.ensureStarted();
            // 会议同步 / 日程同步的内置调度器：同样做一次幂等启动
            // （按页面上的「计划任务」跑一轮：OA→第三方 推送 + 第三方→OA 拉取）
            com.customization.oaones.appsyn.job.OaonesMtgSyncScheduler.ensureStarted();
            // 考勤同步（一级菜单「考勤同步」）的内置调度器：同样做一次幂等启动
            // （按每条设置自己的计划任务跑一轮：第三方→OA 导入 / OA→第三方 推送）
            com.customization.oaones.appsyn.job.OaonesAttSyncScheduler.ensureStarted();
            BaseController controller = route(action);
            if (controller == null) {
                return OaonesUtil.fail("未知 action: " + action);
            }
            JSONObject result = controller.handle(action, request, operatorId);
            if (result == null) {
                return OaonesUtil.fail("未知 action: " + action);
            }
            return result;
        } catch (Exception e) {
            OaonesLog.error("OAones action 处理失败 action=" + action, e);
            log.writeLog("oaones action error action=" + action, e);
            return OaonesUtil.fail(e.getMessage() == null ? "处理失败" : e.getMessage());
        }
    }

    /** 按 action 前缀选择模块控制器，未匹配返回 null。 */
    private BaseController route(String action) {
        int dot = action.indexOf('.');
        String prefix = dot > 0 ? action.substring(0, dot) : action;
        if ("cfg".equals(prefix)) {
            return new ConfigController();
        }
        if ("dept".equals(prefix)) {
            return new DeptBindController();
        }
        if ("sub".equals(prefix)) {
            return new SubBindController();
        }
        if ("user".equals(prefix)) {
            return new UserBindController();
        }
        if ("sync".equals(prefix)) {
            return new SyncController();
        }
        // 同步中心“同步设置”（一级菜单“同步中心”的租户级计划任务与“成员没有找到部门”口径）：
        // 三个连接器共用同一套公共实现（表 uf_oaones_sync_plan 用 type 区分平台）
        if ("syncplan".equals(prefix)) {
            return new com.customization.oaones.appsyn.controller.SyncPlanController();
        }
        if ("account".equals(prefix)) {
            return new AccountController();
        }
        if ("app".equals(prefix)) {
            return new AppBindController();
        }
        if ("msg".equals(prefix)) {
            return new MsgController();
        }
        // login.* 由登录页自己的无会话入口调用（见 LoginController 类注释），
        // 这里保留路由是为了「已登录时在模块内复用同一组 action」时口径一致。
        if ("login".equals(prefix)) {
            return new LoginController();
        }
        if ("logincfg".equals(prefix)) {
            return new LoginConfigController();
        }
        // 绑定总览（公共只读页 /oaones/index/BindOverview.jsp）：跨平台汇总，不区分连接器
        if ("overview".equals(prefix)) {
            return new com.customization.oaones.appsyn.controller.BindOverviewController();
        }
        // 个人微信（第四个平台，但没有“租户”概念）：「微信设置」页与个人账号中心共用
        if ("pw".equals(prefix)) {
            return new com.customization.oaones.appsyn.controller.PersonalWxController();
        }
        // 应用中心（无租户维度的公共菜单：应用列表 + macOS 风格桌面启动台），三个连接器共用一套公共实现
        if ("appc".equals(prefix)) {
            return new com.customization.oaones.appsyn.controller.AppCenterController();
        }
        // 会议同步 / 日程同步（两条独立的一级菜单，三个连接器共用一套公共实现：
        // 表 uf_oaones_mtg_sync / uf_oaones_mtg_sync_log 用 biz + type 区分业务与平台）
        if ("mtgsync".equals(prefix)) {
            return new com.customization.oaones.appsyn.controller.MtgSyncController();
        }
        // 考勤同步（一级菜单「考勤同步」，三个连接器共用一套公共实现：表 uf_oaones_att_cfg / _att_log / _att_log_item / _att_link 用 type 区分平台）
        if ("attsync".equals(prefix)) {
            return new com.customization.oaones.appsyn.controller.AttSyncController();
        }
        // 接口日志中心（一级菜单，跨平台只读汇总；三个连接器共用一套公共实现）
        if ("apilog".equals(prefix)) {
            return new com.customization.oaones.appsyn.controller.ApiLogController();
        }
        return null;
    }
}
