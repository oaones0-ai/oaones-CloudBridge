<%--
  静态资源版本号片段（必须用静态 include 引入，<%@ include file="/oaones/appsyn/assets.jsp" %>）。

  E9 对 .js/.css 会返回 Cache-Control: private,max-age=86400000（1 天），
  浏览器在有效期内不会回源校验；这里取公共组件与 appsyn 专用资源的最后修改时间，
  文件一变页面引用的 ?v= 就变，避免浏览器继续使用旧脚本或旧样式。
--%>
<%
    long oaonesAssetV = 0L;
    String[] oaonesAssetPaths = {
        // 公共组件与公共样式：只能位于 /oaones/scripts/ 下
        "/oaones/scripts/css/style.css",
        "/oaones/scripts/js/api.js",
        "/oaones/scripts/js/modal.js",
        "/oaones/scripts/js/tree.js",
        "/oaones/scripts/js/hrmPicker.js",
        "/oaones/scripts/js/query.js",
        "/oaones/scripts/js/multiSelect.js",
        "/oaones/scripts/js/picker.js",
        "/oaones/scripts/js/help.js",
        "/oaones/scripts/js/richtext.js",
        "/oaones/scripts/js/secret.js",
        "/oaones/scripts/js/loading.js",
        "/oaones/scripts/js/toast.js",
        "/oaones/scripts/js/hrmlink.js",
        "/oaones/scripts/js/table.js",
        "/oaones/scripts/js/aiPicker.js",
        "/oaones/scripts/js/excel.js",
        "/oaones/scripts/js/datetime.js",
        "/oaones/scripts/js/clipboard.js",
        "/oaones/scripts/js/jsonViewer.js",
        "/oaones/scripts/js/form.js",
        "/oaones/scripts/js/upload.js",
        "/oaones/scripts/js/tabs.js",
        "/oaones/scripts/js/featureGate.js",
        // appsyn 项目专用资源：页面、上下文配置与业务脚本
        "/oaones/appsyn/script/css/login.css",
        "/oaones/appsyn/script/css/desktop.css",
        "/oaones/appsyn/script/js/login.i18n.js",
        "/oaones/appsyn/script/js/appContext.js",
        "/oaones/appsyn/script/js/featureConfig.js",
        "/oaones/appsyn/script/js/appDesktop.js",
        "/oaones/appsyn/script/js/syncCenter.js",
        "/oaones/appsyn/script/js/syncPlan.js",
        "/oaones/appsyn/script/js/syncResult.js",
        "/oaones/appsyn/script/js/mtgSync.js",
        "/oaones/appsyn/script/js/attSync.js",
        "/oaones/appsyn/script/js/apiLog.js",
        "/oaones/appsyn/script/js/opMonitor.js",
        "/oaones/appsyn/script/js/authReport.js",
        "/oaones/appsyn/script/js/systemSettings.js",
        "/oaones/appsyn/script/js/orgMap.js"
    };
    try {
        for (String oaonesAssetPath : oaonesAssetPaths) {
            java.net.URL oaonesAssetUrl = application.getResource(oaonesAssetPath);
            if (oaonesAssetUrl != null) {
                oaonesAssetV = Math.max(oaonesAssetV, oaonesAssetUrl.openConnection().getLastModified());
            }
        }
    } catch (Exception oaonesAssetIgnore) {
        oaonesAssetV = 0L;
    }
    if (oaonesAssetV <= 0L) {
        oaonesAssetV = System.currentTimeMillis();
    }
%>
