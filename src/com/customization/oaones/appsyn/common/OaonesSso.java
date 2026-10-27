package com.customization.oaones.appsyn.common;

import weaver.general.Util;
import weaver.hrm.User;
import weaver.login.VerifyLogin;
import weaver.systeminfo.SysMaintenanceLog;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.URI;

/**
 * OA 免登录（单点登录落地）公共工具。
 *
 * <p>“企微工作台应用”打开 OA 页面时没有 OA 登录会话，本类负责把
 * 已经过企业微信网页授权核实的 OA 人员写入 E9 登录会话：</p>
 *
 * <ol>
 *   <li>按人员ID取 E9 标准 {@link User}（{@code User.getUser(id, 1)}，与平台登录同一套数据源）；</li>
 *   <li>重建会话并写入平台登录态属性
 *       {@code weaver_user@bean} / {@code accounts} / {@code isLoginIsUser} / {@code rtxlogin}
 *       （与平台登录、其它免登录集成保持同一口径，{@code HrmUserVarify.getUser} 据此识别登录人）；</li>
 *   <li>写 {@code loginidweaver} / {@code languageidweaver} / {@code loginid} / {@code lastname}
 *       等登录 cookie（部分页面脚本与前端组件按它取当前登录人）；</li>
 *   <li>写 E9 系统维护日志（登录事件），便于审计“谁在什么时间通过企微工作台免登录进入 OA”。</li>
 * </ol>
 *
 * <p>本类只做“会话注入”，<b>不</b>做身份合法性的判断——调用方必须先完成
 * 企业微信授权（code 换 userid）、绑定关系与 OA 账号状态的校验。</p>
 */
public final class OaonesSso {

    /** 免登录会话注入时使用的登录类型（1 = 网页端登录，与平台一致）。 */
    private static final int LOGIN_TYPE_WEB = 1;

    private OaonesSso() {
    }

    /**
     * 把指定 OA 人员写入当前请求的登录会话（免登录）。
     *
     * @param oaUserId 已核实身份的 OA 人员ID
     * @param source   免登录来源说明（写日志用，例如“企微工作台应用「XX」(agentid 1000002)”）
     * @return 实际登录的 E9 用户对象
     */
    public static User login(HttpServletRequest request, HttpServletResponse response,
                             int oaUserId, String source) {
        if (oaUserId <= 0) {
            throw new RuntimeException("缺少免登录的 OA 人员ID");
        }
        User user = User.getUser(oaUserId, LOGIN_TYPE_WEB);
        if (user == null) {
            throw new RuntimeException("OA 账号不存在或不可用（人员ID " + oaUserId + "），无法免登录进入 OA");
        }
        user.setLogintype(String.valueOf(LOGIN_TYPE_WEB));
        user.setLoginip(Util.getIpAddr(request));
        user.setLastlogindate(OaonesUtil.nowDate());

        // 与平台登录一致：先清空旧会话（避免残留上一个人的身份），再写入新登录态
        HttpSession old = request.getSession(false);
        if (old != null) {
            try {
                old.invalidate();
            } catch (Throwable ignore) {
                // 会话已失效/并发处理：忽略，下一步会新建会话
            }
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("accounts", accountsOf(oaUserId));
        session.setAttribute("isLoginIsUser", 1);
        session.setAttribute("weaver_user@bean", user);
        session.setAttribute("rtxlogin", "1");

        try {
            Util.setCookie(response, "loginidweaver", String.valueOf(oaUserId), -1);
            Util.setCookie(response, "languageidweaver", String.valueOf(user.getLanguage()), -1);
            Util.setCookie(response, "loginid", OaonesUtil.s(user.getLoginid()), -1);
            Util.setCookie(response, "lastname", OaonesUtil.s(user.getLastname()), -1);
        } catch (Throwable t) {
            OaonesLog.error("免登录写入登录 cookie 失败（不影响会话登录）oaUserId=" + oaUserId, t);
        }
        writeLoginLog(request, user, source);
        OaonesLog.step("免登录通过 oaUserId=" + oaUserId
                + " loginid=" + OaonesUtil.s(user.getLoginid()) + " source=" + OaonesUtil.s(source));
        return user;
    }

    /**
     * 主次账号集合（支持登录后切换身份）。
     *
     * <p>取不到时返回 {@code null}（会话里不写该属性），不因为查询异常中断免登录。</p>
     */
    private static Object accountsOf(int oaUserId) {
        try {
            return new VerifyLogin().getAccountsById(oaUserId);
        } catch (Throwable t) {
            OaonesLog.error("免登录读取主次账号失败 oaUserId=" + oaUserId, t);
            return null;
        }
    }

    /** E9 系统维护日志：记录一次免登录（operateType=6 登录 / operateItem=60）。 */
    private static void writeLoginLog(HttpServletRequest request, User user, String source) {
        try {
            SysMaintenanceLog log = new SysMaintenanceLog();
            log.resetParameter();
            log.setRelatedId(user.getUID());
            log.setRelatedName((OaonesUtil.s(user.getFirstname()) + " "
                    + OaonesUtil.s(user.getLastname())).trim());
            log.setOperateType("6");
            log.setOperateDesc(OaonesUtil.s(source));
            log.setOperateItem("60");
            log.setOperateUserid(user.getUID());
            log.setClientAddress(Util.getIpAddr(request));
            log.setSysLogInfo();
        } catch (Throwable t) {
            OaonesLog.error("免登录写系统维护日志失败 oaUserId=" + (user == null ? "" : user.getUID()), t);
        }
    }

    /**
     * 站内地址校验：只接受以 {@code /} 开头的站内路径，避免被构造成
     * “免登录后跳到站外”的开放重定向。
     *
     * <p>拒绝 {@code //host}、{@code http://}、{@code \} 等写法；
     * 不合法时返回 {@code dft}（缺省落地页）。</p>
     */
    public static String safeInternalUrl(String url, String dft) {
        String value = internalPath(url);
        if (value.length() == 0) {
            return dft == null ? "" : dft;
        }
        String lower = value.toLowerCase();
        if (!value.startsWith("/") || value.startsWith("//")
                || lower.startsWith("/\\") || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            OaonesLog.step("免登录落地地址不是站内路径，已忽略: " + value);
            return dft == null ? "" : dft;
        }
        return value;
    }

    /**
     * gopage 等“登录后跳转目标”的安全校验：允许站内路径和完整的 http/https 网址。
     *
     * <p>与 {@link #safeInternalUrl} 分开，避免把免登录落地页、应用绑定回跳等
     * 只应留在站内的入口一并放开。http/https 地址必须能解析出协议和主机，
     * 且不允许携带账号密码；{@code javascript:}、{@code data:}、协议相对地址、
     * 反斜杠和控制字符都会被拒绝。</p>
     */
    public static String safeRedirectUrl(String url, String dft) {
        String value = OaonesUtil.s(url).trim();
        String fallback = dft == null ? "" : dft;
        if (value.length() == 0 || value.indexOf('\\') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return fallback;
        }
        String lower = value.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return safeInternalUrl(value, fallback);
        }
        try {
            URI uri = new URI(value);
            String scheme = OaonesUtil.s(uri.getScheme());
            boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (http && uri.getHost() != null && uri.getHost().length() > 0
                    && uri.getRawUserInfo() == null) {
                return uri.toASCIIString();
            }
        } catch (Exception ignore) {
            // 统一在下方记录被拒绝的地址
        }
        OaonesLog.step("跳转目标不是合法的站内路径或 http/https 地址，已忽略: " + value);
        return fallback;
    }

    /**
     * 把“从浏览器地址栏复制的完整网址”归一成站内路径（保留 query 与 {@code #hash}）。
     *
     * <p>例：{@code https://oatest.ghedu.com/wui/index.html#/main/cube/search?customid=132}
     * → {@code /wui/index.html#/main/cube/search?customid=132}。</p>
     *
     * <p>注意：站内地址里 {@code #} 之后还可能带 {@code ?customid=…} 这类参数，
     * 归一与校验都必须<b>整段保留</b>，不能按“问号在前”去截断，
     * 否则会把 {@code customid} 这类页面参数丢掉（表现为落地页退回门户首页）。</p>
     */
    public static String internalPath(String url) {
        String value = OaonesUtil.s(url).trim();
        if (value.length() == 0) {
            return "";
        }
        String lower = value.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            int start = value.indexOf("://") + 3;
            int slash = value.indexOf('/', start);
            value = slash >= 0 ? value.substring(slash) : "/";
            OaonesLog.step("免登录落地地址填写的是完整网址，已归一成站内路径: " + value);
        }
        return value;
    }

    /**
     * 站点地址前缀（协议 + 域名 + 端口，不含上下文路径），用于拼“企微工作台应用主页地址”。
     *
     * <p>properties 里配置了 {@code app.entry.base} 时优先用它（反向代理/多域名部署），
     * 否则按浏览器当前访问地址自动拼接。</p>
     */
    public static String siteBase(HttpServletRequest request) {
        String configured = OaonesUtil.s(OaonesProps.appEntryBase()).trim();
        while (configured.endsWith("/")) {
            configured = configured.substring(0, configured.length() - 1);
        }
        if (configured.length() > 0) {
            return configured;
        }
        // 其次用“系统设置”里维护的 OA 访问地址（后台微信/企业微信设置 → OA访问地址，表 wechat_Set.oaUrl）：
        // 这是客户自己维护的对外访问地址，换域名/换协议时不用改代码、也不用配本模块的 properties
        String systemBase = systemOaBase();
        if (systemBase.length() > 0) {
            return systemBase;
        }
        if (request == null) {
            return "";
        }
        // 反向代理（Nginx/负载均衡终止 TLS）把请求转成 http 进来时 request.getScheme() 仍是 http，
        // 直接拼出来就是 http://…，而企业微信的“应用主页 / 网页授权”要求 https。
        // 这里优先取代理透传的协议与主机（X-Forwarded-Proto / X-Forwarded-Host），
        // 仍然对不上时请在 oaones.appsyn.properties 里显式配 app.entry.base。
        String protoHeader = firstHeader(request, "X-Forwarded-Proto");
        if (protoHeader.length() == 0) {
            protoHeader = firstHeader(request, "X-Forwarded-Scheme");
        }
        String hostHeader = firstHeader(request, "X-Forwarded-Host");
        String portHeader = firstHeader(request, "X-Forwarded-Port");
        String scheme = protoHeader.length() > 0 ? protoHeader : OaonesUtil.s(request.getScheme()).trim();
        String host = hostHeader.length() > 0 ? hostHeader : OaonesUtil.s(request.getServerName()).trim();
        if (scheme.length() == 0 || host.length() == 0) {
            return "";
        }
        if (host.indexOf(':') >= 0) {
            // X-Forwarded-Host 可能自带端口（host:port），不再另拼端口
            return scheme + "://" + host;
        }
        int port = OaonesUtil.i(portHeader, 0);
        if (port <= 0) {
            if (protoHeader.length() > 0 || hostHeader.length() > 0) {
                // 代理只透传了协议/主机、没给端口：按协议默认端口算，
                // 绝不能用 Tomcat 收到的内部端口（反向代理几乎都是 80 转发，会拼出 https://域名:80）
                port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            } else {
                port = request.getServerPort();
            }
        }
        // https 配 80 只可能是“内网转发端口”（代理终止 TLS 后回源到 80，或连接器配了 scheme=https），
        // 不是给用户看的端口，一律不拼进对外地址
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && (port == 443 || port == 80));
        String base = scheme + "://" + host + (defaultPort || port <= 0 ? "" : ":" + port);
        OaonesLog.step("免登录入口地址前缀按请求地址计算: " + base
                + "（protoHeader=" + protoHeader + " hostHeader=" + hostHeader
                + " portHeader=" + portHeader + " serverPort=" + request.getServerPort() + "）");
        if (base.startsWith("http://")) {
            OaonesLog.step("免登录入口地址前缀算出来是 " + base
                    + "：企业微信的应用主页与网页授权要求 https，"
                    + "请在 oaones.appsyn.properties 里配置 app.entry.base（协议+域名+端口）");
        }
        return base;
    }

    /**
     * 系统设置里的 OA 访问地址（协议 + 域名 + 端口，去掉路径）。
     *
     * <p>取 E9 微信/企业微信集成设置中的“OA访问地址”（物理存放于 {@code wechat_Set.oaUrl}，
     * 平台读取入口 {@code weaver.wechat.util.WechatPropConfig#getOAUrl()}），
     * 与 E9 自己给企业微信/微信推送消息时拼链接用的是同一个地址，口径一致。</p>
     *
     * <p>只配了域名（不带协议）时按 https 补全（企业微信工作台与网页授权要求 https）；
     * 读不到（未配置 / 表不存在）时返回空串，由调用方继续按代理头与浏览器地址拼。</p>
     */
    private static String systemOaBase() {
        try {
            String url = OaonesUtil.s(weaver.wechat.util.WechatPropConfig.getOAUrl()).trim();
            if (url.length() == 0) {
                return "";
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            int start = url.indexOf("://") + 3;
            int slash = url.indexOf('/', start);
            String base = slash > 0 ? url.substring(0, slash) : url;
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            OaonesLog.step("免登录入口地址前缀取系统设置的 OA 访问地址: " + base);
            return base;
        } catch (Throwable t) {
            OaonesLog.error("读取系统设置里的 OA 访问地址失败（改用代理头/浏览器地址）", t);
            return "";
        }
    }

    /**
     * 取代理透传头里的第一个值（多个代理串联时形如 {@code https,http} 或 {@code a.example.com,b.example.com}）。
     */
    private static String firstHeader(HttpServletRequest request, String name) {
        try {
            String value = OaonesUtil.s(request.getHeader(name)).trim();
            if (value.length() == 0) {
                return "";
            }
            int comma = value.indexOf(',');
            if (comma >= 0) {
                value = value.substring(0, comma).trim();
            }
            return value;
        } catch (Throwable t) {
            return "";
        }
    }
}
