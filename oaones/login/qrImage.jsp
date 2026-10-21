<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"
         trimDirectiveWhitespaces="true" %>
<%@ page import="com.customization.oaones.appsyn.common.QrCodeImage" %>
<%@ page import="com.customization.oaones.appsyn.service.impl.wecom.ScanLoginServiceImpl" %>
<%@ page import="weaver.general.Util" %>
<%
    // 统一登录页的自建二维码图片：按票据取出**服务端生成时写入**的二维码内容，现场画成图片。
    //
    // 为什么自己画：企业微信/钉钉/飞书托管的二维码页都是按“单个企业 + 单个应用”生成的，
    // 一张只能对应一家企业、也只能对应一个平台；本页画出来的二维码内容只是本站的扫码落地页
    // （/oaones/login/scan.jsp?t=票据），扫到之后由 OA 按扫码客户端判断平台（企业微信/钉钉/飞书），
    // 再依次用该平台对接的租户换身份，因此**一张二维码**既能覆盖多租户、也能被三个平台扫。
    //
    // 输出格式：默认 SVG（纯字符串拼接，不依赖 JVM headless 设置，任意缩放都清晰）；
    //          ?fmt=png 时输出 PNG（需要 JVM 以 -Djava.awt.headless=true 运行）。
    // 本页不校验登录会话（登录页本身就是未登录页面），只按票据读内容，不接受任意文本；
    // 票据表 uf_oaones_scan_login 是三个连接器共用的一张表（读法与 /oaones/login/login.jsp 一致）。
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    String oaonesQrTicket = Util.null2String(request.getParameter("t")).trim();
    String oaonesQrText = oaonesQrTicket.length() == 0
            ? "" : new ScanLoginServiceImpl().qrText(oaonesQrTicket);
    if (oaonesQrText.length() == 0) {
        // 票据不存在（被清理）或已过期：直接 404，页面上的 <img> 会走 onerror 提示刷新
        response.sendError(javax.servlet.http.HttpServletResponse.SC_NOT_FOUND, "二维码票据不存在或已失效");
        return;
    }
    int oaonesQrModule = Util.getIntValue(request.getParameter("ms"), QrCodeImage.DEFAULT_MODULE_SIZE);
    if (oaonesQrModule < 2) {
        oaonesQrModule = QrCodeImage.DEFAULT_MODULE_SIZE;
    }
    if (oaonesQrModule > 20) {
        oaonesQrModule = 20;
    }
    if ("png".equalsIgnoreCase(Util.null2String(request.getParameter("fmt")))) {
        try {
            byte[] oaonesQrPng = QrCodeImage.png(oaonesQrText, oaonesQrModule,
                    QrCodeImage.DEFAULT_QUIET_ZONE);
            response.setContentType("image/png");
            response.setContentLength(oaonesQrPng.length);
            response.getOutputStream().write(oaonesQrPng);
        } catch (Throwable oaonesQrPngError) {
            // 非 headless 的 JVM 生成 PNG 可能失败：返回 500，页面上的 <img> 会走 onerror 提示
            response.sendError(javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "二维码图片生成失败（如需 PNG，请以 -Djava.awt.headless=true 启动 E9）");
        }
        return;
    }
    response.setContentType("image/svg+xml;charset=UTF-8");
    out.print(QrCodeImage.svg(oaonesQrText, oaonesQrModule, QrCodeImage.DEFAULT_QUIET_ZONE));
%>
