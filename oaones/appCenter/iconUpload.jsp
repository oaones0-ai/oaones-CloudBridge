<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.wecom.OaonesController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 应用中心「图标上传」入口（/oaones/appCenter/iconUpload.jsp）。
     *
     * 为什么单独一个入口：/oaones/WeCom/api.jsp 是 query string 口径的 AJAX 入口
     * （js/api.js 用 GET 调），收不了 multipart/form-data；图标是文件，必须 POST + FormData
     * 提交，因此另开本页——与平台自带的上传页（docs/ofd/OfdUpload.jsp 等）同一口径：
     * 先做登录校验，再把请求交给同一个控制器 action（appc.uploadIcon），
     * 页面（appCenter/appListPanel.jsp）“选择图片上传”按钮就是往本地址 POST。
     *
     * 权限：「应用中心」维护人（系统管理员 user.id=1 + oaones.properties#appc.admin.ids）
     * 才能上传——校验在 Service（AppCenterServiceImpl#saveIcon 的 assertMaintain）里做，
     * 非维护人即使直接调本地址也拿不到写文件的能力，只会收到“无权限”提示。
     *
     * 落盘：oaones/img/ 下（站点根目录，E9 部署即 ecology/oaones/img/），
     * 文件名由服务端生成（时间戳 + 随机串），不采用上传上来的文件名；
     * 类型按**文件头**判定（PNG / JPG / GIF / BMP / WebP，不收 SVG），并校验体积与长宽，
     * 具体口径见 common/OaonesImageSize 与 OaonesConst.APP_ICON_*。
     */
    request.setCharacterEncoding("UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    User user = HrmUserVarify.getUser(request, response);
    if (user == null) {
        out.print("{\"code\":401,\"msg\":\"未登录或会话已失效\"}");
        return;
    }
    JSONObject oaonesIconResult =
            new OaonesController().handle("appc.uploadIcon", request, String.valueOf(user.getUID()));
    out.print(oaonesIconResult == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : oaonesIconResult.toJSONString());
%>
