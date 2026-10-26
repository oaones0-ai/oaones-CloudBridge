<%--
  静态资源版本号片段（必须用静态 include 引入，<%@ include file="/oaones/assets.jsp" %>）。

  E9 对 .js/.css 会返回 Cache-Control: private,max-age=86400000（1 天），
  浏览器在有效期内不会回源校验，改造后旧版 api.js 可能仍被沿用
  （旧版用相对地址定位 api.jsp，页面拆到子目录后会 404）。
  这里取静态文件的最后修改时间作为 ?v= 参数，文件一变版本号就变，浏览器必然重新拉取。
--%>
<%
    long oaonesAssetV = 0L;
    try {
        java.net.URL oaonesCssUrl = application.getResource("/oaones/css/style.css");
        if (oaonesCssUrl != null) {
            oaonesAssetV = oaonesCssUrl.openConnection().getLastModified();
        }
        // 登录页专用样式（统一登录页 /oaones/login/login.jsp），与 style.css 分开维护，
        // 改了两者中任意一个，页面上引用的 ?v= 都会变（否则浏览器缓存一天，改动看不到）
        java.net.URL oaonesLoginCssUrl = application.getResource("/oaones/css/login.css");
        if (oaonesLoginCssUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesLoginCssUrl.openConnection().getLastModified());
        }
        // 应用中心“桌面启动台”专用样式（/oaones/appCenter/Desktop.jsp 引它）：
        // 与 style.css 分开维护，改任一个页面上引用的 ?v= 都会变
        java.net.URL oaonesDesktopCssUrl = application.getResource("/oaones/css/desktop.css");
        if (oaonesDesktopCssUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesDesktopCssUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesJsUrl = application.getResource("/oaones/js/api.js");
        if (oaonesJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesPickerJsUrl = application.getResource("/oaones/js/picker.js");
        if (oaonesPickerJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesPickerJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesHelpJsUrl = application.getResource("/oaones/js/help.js");
        if (oaonesHelpJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesHelpJsUrl.openConnection().getLastModified());
        }
        // 富文本编辑器（登录设置的「登录页左侧文字」用；本模块自己的轻量控件）
        java.net.URL oaonesRichTextJsUrl = application.getResource("/oaones/js/richtext.js");
        if (oaonesRichTextJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesRichTextJsUrl.openConnection().getLastModified());
        }
        // 密码 / 密钥输入框的掩码口径（/oaones/js/secret.js）：租户设置、应用绑定、微信设置共用
        java.net.URL oaonesSecretJsUrl = application.getResource("/oaones/js/secret.js");
        if (oaonesSecretJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesSecretJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesToastJsUrl = application.getResource("/oaones/js/toast.js");
        if (oaonesToastJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesToastJsUrl.openConnection().getLastModified());
        }
        // 统一登录页 / 找回密码弹层的语种资源
        java.net.URL oaonesLoginI18nJsUrl = application.getResource("/oaones/js/login.i18n.js");
        if (oaonesLoginI18nJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesLoginI18nJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesHrmLinkJsUrl = application.getResource("/oaones/js/hrmlink.js");
        if (oaonesHrmLinkJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesHrmLinkJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesTableJsUrl = application.getResource("/oaones/js/table.js");
        if (oaonesTableJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesTableJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesExcelJsUrl = application.getResource("/oaones/js/excel.js");
        if (oaonesExcelJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesExcelJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesDateJsUrl = application.getResource("/oaones/js/datetime.js");
        if (oaonesDateJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesDateJsUrl.openConnection().getLastModified());
        }
        // 应用中心“桌面启动台”脚本（/oaones/js/appDesktop.js）：图标渲染 + Dock 放大 + 点击启动
        java.net.URL oaonesAppDesktopJsUrl = application.getResource("/oaones/js/appDesktop.js");
        if (oaonesAppDesktopJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesAppDesktopJsUrl.openConnection().getLastModified());
        }
        // 同步中心通用页面脚本（/oaones/js/syncCenter.js）：三个平台共用一份
        java.net.URL oaonesSyncCenterJsUrl = application.getResource("/oaones/js/syncCenter.js");
        if (oaonesSyncCenterJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesSyncCenterJsUrl.openConnection().getLastModified());
        }
        // 同步中心的「编辑同步设置 + 同步设置列表」模块（/oaones/js/syncPlan.js）
        java.net.URL oaonesSyncPlanJsUrl = application.getResource("/oaones/js/syncPlan.js");
        if (oaonesSyncPlanJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesSyncPlanJsUrl.openConnection().getLastModified());
        }
        // 同步结果独立页面（/oaones/js/syncResult.js）
        java.net.URL oaonesSyncResultJsUrl = application.getResource("/oaones/js/syncResult.js");
        if (oaonesSyncResultJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesSyncResultJsUrl.openConnection().getLastModified());
        }
        // 会议同步 / 日程同步页面脚本（/oaones/js/mtgSync.js）：两个一级菜单共用一份
        java.net.URL oaonesMtgSyncJsUrl = application.getResource("/oaones/js/mtgSync.js");
        if (oaonesMtgSyncJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesMtgSyncJsUrl.openConnection().getLastModified());
        }
        // 接口日志中心脚本（/oaones/js/apiLog.js）：一级菜单「接口日志中心」用
        // 考勤同步页面脚本（/oaones/js/attSync.js）：一级菜单「考勤同步」用（三个平台共用一份）
        java.net.URL oaonesAttSyncJsUrl = application.getResource("/oaones/js/attSync.js");
        if (oaonesAttSyncJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesAttSyncJsUrl.openConnection().getLastModified());
        }
        java.net.URL oaonesApiLogJsUrl = application.getResource("/oaones/js/apiLog.js");
        if (oaonesApiLogJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesApiLogJsUrl.openConnection().getLastModified());
        }
        // 操作监控平台脚本（/oaones/js/opMonitor.js）：一级菜单「操作监控平台」用
        java.net.URL oaonesOpMonitorJsUrl = application.getResource("/oaones/js/opMonitor.js");
        if (oaonesOpMonitorJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesOpMonitorJsUrl.openConnection().getLastModified());
        }
        // 统一认证报表脚本（/oaones/js/authReport.js）：一级菜单「统一认证报表」用
        java.net.URL oaonesAuthReportJsUrl = application.getResource("/oaones/js/authReport.js");
        if (oaonesAuthReportJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesAuthReportJsUrl.openConnection().getLastModified());
        }
        // 系统设置页面脚本（/oaones/js/systemSettings.js）：日志保留统一 / 单独设置
        java.net.URL oaonesSystemSettingsJsUrl = application.getResource("/oaones/js/systemSettings.js");
        if (oaonesSystemSettingsJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesSystemSettingsJsUrl.openConnection().getLastModified());
        }
        // OA 分部 - 平台租户一对一映射页面脚本（/oaones/js/orgMap.js）
        java.net.URL oaonesOrgMapJsUrl = application.getResource("/oaones/js/orgMap.js");
        if (oaonesOrgMapJsUrl != null) {
            oaonesAssetV = Math.max(oaonesAssetV, oaonesOrgMapJsUrl.openConnection().getLastModified());
        }
    } catch (Exception oaonesAssetIgnore) {
        oaonesAssetV = 0L;
    }
    if (oaonesAssetV <= 0L) {
        oaonesAssetV = System.currentTimeMillis();
    }
%>
