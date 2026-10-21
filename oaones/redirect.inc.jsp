<%--
  公共 302 跳转片段（静态 include：<%@ include file="/oaones/redirect.inc.jsp" %>）。

  为什么不直接写 response.sendRedirect("/oaones/xxx.jsp")：
  容器只在 Location 里拿到**路径**时，会按“容器自己看到的请求地址”补成绝对地址——
  nginx 终止 TLS 后转发给内网 Resin 的是**明文 http**，容器拼出来的就是
  http://oatest.ghedu.com/oaones/xxx.jsp；而 nginx 只监听 443（没有 80 端口），
  客户端按这个地址再去连 80 端口就直接 net::ERR_CONNECTION_REFUSED
  （钉钉 / 企业微信 / 飞书内置浏览器上就表现为
  “errorCode: -6 net::ERR_CONNECTION_REFUSED 请刷新页面重试”），
  而且服务端日志里看不到这次失败请求（请求根本没到），很容易被误判成“回调地址填错了”。

  这里把 Location 写成**相对地址**（"/oaones/login/scan.jsp?..." 这类）：
  客户端按“当前访问地址”解析，协议 / 域名 / 端口与用户访问时完全一致，
  换域名、换协议、走 https 反代都不会再拼出连不上的地址。
  已经是绝对地址的（http:// / https://，例如平台的网页授权地址）原样透传，不做改写。
--%>
<%
    // 同步中心“计划任务”由**模块内置调度器**按页面配置的 cron 执行（不依赖 E9 后台计划任务）：
    // 任何 OAones 页面被访问时都做一次幂等启动（内部只判断一个静态标志，开销可忽略）。
    com.customization.oaones.appsyn.job.OaonesSyncPlanScheduler.ensureStarted();
    // 会议同步 / 日程同步的内置调度器：同样在任意 OAones 页面被访问时幂等启动。
    com.customization.oaones.appsyn.job.OaonesMtgSyncScheduler.ensureStarted();
%>
<%!
    /**
     * 302 跳转：站内跳转写相对 Location，绝对地址原样透传。
     *
     * @param request  当前请求（与调用处写法统一，便于以后按代理头兜底）
     * @param response 当前响应
     * @param location 站内路径（以 / 开头，可带查询串）或绝对地址（http:// / https://）
     */
    public static void oaonesRedirect(javax.servlet.http.HttpServletRequest request,
                                      javax.servlet.http.HttpServletResponse response,
                                      String location) {
        String oaonesTarget = location == null ? "" : location.trim();
        boolean oaonesAbsolute = oaonesTarget.startsWith("http://") || oaonesTarget.startsWith("https://");
        // 既不是站内路径（"/x"，但 "//host/x" 是跨站地址）也不是绝对地址：退回站内根路径，
        // 绝不把来路不明的值拼进 Location（防跳转钓鱼）
        if (!oaonesAbsolute && !(oaonesTarget.startsWith("/") && !oaonesTarget.startsWith("//"))) {
            oaonesTarget = "/";
        }
        response.setStatus(302);
        response.setHeader("Location", oaonesTarget);
    }
%>
