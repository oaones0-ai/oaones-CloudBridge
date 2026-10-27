<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.alibaba.fastjson.JSONObject" %>
<%@ page import="com.customization.oaones.appsyn.controller.wecom.OaonesController" %>
<%@ page import="weaver.hrm.HrmUserVarify" %>
<%@ page import="weaver.hrm.User" %>
<%
    /*
     * 登录设置「登录页图片上传」入口（/oaones/appsyn/loginConfig/imageUpload.jsp?kind=logo|bg）。
     *
     * 为什么单独一个入口：/oaones/appsyn/WeCom/api.jsp 是 query string 口径的 AJAX 入口
     * （js/api.js 用 GET 调），收不了 multipart/form-data；图片是文件，必须 POST + FormData 提交，
     * 因此另开本页——与「应用中心」的图标上传页（/oaones/appsyn/appCenter/iconUpload.jsp）同一口径：
     * 先做登录校验，再把请求交给同一个控制器 action（logincfg.imageUpload），
     * 设置页（loginConfig/config.jsp）的「上传」按钮就是往本地址 POST。
     *
     * 用途放在**查询串**上（kind=logo 登录框上方的 logo / kind=bg 整页背景图）：
     * multipart 请求体里的字段由 commons-fileupload 解析，容器不会把它并进 request.getParameter，
     * 所以 kind 走 query string 最稳。
     *
     * 权限：登录设置的维护人（系统管理员 + oaones.appsyn.properties#login.admin.ids）才能上传——
     * 校验在 Service（LoginConfigServiceImpl#uploadImage 的 canMaintain）里做，
     * 非维护人即使直接调本地址也拿不到写文件的能力，只会收到“无权限”提示。
     *
     * 落盘：与图标同目录（站点根下的 oaones/appsyn/img/，E9 部署即 ecology/oaones/appsyn/img/），
     * 文件名由服务端生成（时间戳 + 随机串，前缀 loginlogo_ / loginbg_），不采用上传上来的文件名；
     * 类型按**文件头**判定（PNG / JPG / GIF / BMP / WebP，不收 SVG），并校验体积与长宽，
     * 具体口径见 common/OaonesImageSize 与 OaonesConst.LOGIN_LOGO_* / LOGIN_BG_*。
     *
     * 注意：上传**只是落盘**，回给页面的是“待保存”的地址——点了「保存设置」才写进配置行，
     * 登录页才显示（与“先传图标、再保存应用”一致）。
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
    JSONObject oaonesImageResult =
            new OaonesController().handle("logincfg.imageUpload", request, String.valueOf(user.getUID()));
    out.print(oaonesImageResult == null ? "{\"code\":-1,\"msg\":\"无返回\"}" : oaonesImageResult.toJSONString());
%>
