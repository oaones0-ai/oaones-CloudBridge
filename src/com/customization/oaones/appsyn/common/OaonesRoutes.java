package com.customization.oaones.appsyn.common;

import weaver.general.GCONST;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * OAones URL 路由（对外地址 ⇄ 实际页面），由配置文件驱动。
 *
 * <p>为什么要有这一层：模块里有些页面是**给别人用的入口**（统一登录页
 * {@code /oaones/appsyn/login/login.jsp}、桌面启动台 {@code /oaones/appsyn/appCenter/Desktop.jsp}），
 * 真实地址一旦写进员工收藏、E9 后台菜单、门户链接，页面换个目录/改个文件名就要满环境改地址；
 * 而且真实 .jsp 地址本身也不适合对外。</p>
 *
 * <p>做法是把「对外地址」与「实际页面」分成两个概念，路由表就写在**本模块唯一的配置文件**
 * {@code ecology/WEB-INF/prop/oaones.appsyn.properties} 里（key 以 {@code route.} 开头）：</p>
 *
 * <pre>
 * route./oaones/appsyn/login=/oaones/appsyn/login/login.jsp|forward|hide
 * route./oaones/appsyn/desktop=/oaones/appsyn/appCenter/Desktop.jsp|forward|hide
 * </pre>
 *
 * <ul>
 *   <li>{@code forward}（默认）内部转发：地址栏保持对外地址；{@code redirect} 则 302 到实际页面；</li>
 *   <li>{@code hide} 表示**真实页面不允许被直接访问**：直接访问时 302 回对外地址；
 *       {@code hide404} 则直接返回 404（比 hide 更严格）；</li>
 *   <li>只有路由表里写明的地址会被处理，其它 URL（含平台页面）一律不碰；</li>
 *   <li>配置文件按修改时间**热加载**：改完即生效，不用重启 E9、也不用改 web.xml。</li>
 * </ul>
 *
 * <p>本类只做“查表 + 转发/跳转”，入口是 {@link #handle(HttpServletRequest, HttpServletResponse)}
 * （由 {@link OaonesRouteFilter} 调用）；页面里要输出对外地址时用 {@link #publicUrl(String)}
 * （配了路由就用对外地址，没配原样返回），不要在页面里再写一份地址表。</p>
 *
 * <p>302 一律写**相对地址**（只写路径，不拼协议/域名/端口）：与 {@code oaones/appsyn/redirect.inc.jsp}
 * 同一口径——nginx 终止 TLS 后转发给内网 Resin 的是明文 http，若由容器补全绝对地址，
 * 客户端会拿到连不上的 http:// 地址（钉钉/企微/飞书内置浏览器上表现为
 * “errorCode: -6 net::ERR_CONNECTION_REFUSED”）。</p>
 */
public final class OaonesRoutes {

    /** 配置项前缀：配置文件里的 key 写成 {@code route.<对外地址>}。 */
    private static final String KEY_PREFIX = "route.";
    /** 路由表所在文件：与模块其它配置同一个文件（ecology/WEB-INF/prop/oaones.appsyn.properties）。 */
    private static final String PROP_FILE = "oaones.appsyn.properties";
    /** 请求级防重入标记：配置写错（两个地址互指）时也不会绕成死循环。 */
    private static final String ATTR_DONE = "oaones.route.done";
    /**
     * 两次“看配置文件有没有改”之间的最小间隔（毫秒）。
     *
     * <p>过滤器映射到 {@code /*}，每个请求都 stat 一次 oaones.appsyn.properties 虽然不慢，
     * 但在高并发下没必要：这里 1 秒最多看一次文件，代价只是**改完配置最多 1 秒生效**
     * （“改完立即生效、不用重启”的体验不变）。</p>
     */
    private static final long RELOAD_CHECK_MS = 1000L;

    private static volatile Snapshot cache;

    private OaonesRoutes() {
    }

    /**
     * 处理一次请求：命中对外地址就转发/跳转，命中被隐藏的真实页面就挡回对外地址。
     *
     * @return true 表示本方法已经写完响应（调用方不要再走后面的过滤器链与页面）；
     *         false 表示没有命中路由，按原样继续
     */
    public static boolean handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request == null || response == null) {
            return false;
        }
        if (request.getAttribute(ATTR_DONE) != null) {
            return false;
        }
        String path = requestPath(request);
        if (path.length() == 0) {
            return false;
        }
        Snapshot snap = snapshot();

        // ① 对外地址 → 实际页面
        Rule rule = find(snap.byFrom, path);
        if (rule != null && !rule.to.equals(path)) {
            request.setAttribute(ATTR_DONE, Boolean.TRUE);
            OaonesLog.step("URL 路由：" + path + " -> " + rule.to
                    + (rule.redirect ? "（302）" : "（内部转发）"));
            if (rule.redirect) {
                redirect(response, withQuery(rule.to, request.getQueryString()));
            } else {
                // 内部转发：原请求的查询串由容器自动保留（页面里的 gopage、t 等参数照常可用）
                request.getRequestDispatcher(rule.to).forward(request, response);
            }
            return true;
        }

        // ② 实际页面 → 不允许直接访问
        Rule hidden = find(snap.byTo, path);
        if (hidden != null && hidden.hide404) {
            request.setAttribute(ATTR_DONE, Boolean.TRUE);
            OaonesLog.step("URL 路由：实际页面 " + path + " 不允许直接访问，返回 404");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }
        if (hidden != null && hidden.hide) {
            request.setAttribute(ATTR_DONE, Boolean.TRUE);
            OaonesLog.step("URL 路由：实际页面 " + path + " 不允许直接访问，302 回对外地址 " + hidden.from);
            redirect(response, withQuery(hidden.from, request.getQueryString()));
            return true;
        }
        return false;
    }

    /**
     * 取某个页面的**对外地址**：配了路由就用路由里的对外地址，没配或参数为空则原样返回。
     *
     * <p>页面里输出链接（预览登录页、登录成功落地页等）统一走本方法，
     * 不要在 JSP 里再写死一份“真实地址 ⇄ 对外地址”的对照表。
     * 入参可以带查询串与 {@code #} 锚点（如 {@code /wui/index.html#/main}），
     * 未配路由时整串原样返回。</p>
     */
    public static String publicUrl(String realUrl) {
        String url = realUrl == null ? "" : realUrl.trim();
        if (url.length() == 0) {
            return url;
        }
        int hashAt = url.indexOf('#');
        String hash = hashAt < 0 ? "" : url.substring(hashAt);
        String head = hashAt < 0 ? url : url.substring(0, hashAt);
        int queryAt = head.indexOf('?');
        String query = queryAt < 0 ? "" : head.substring(queryAt);
        String path = queryAt < 0 ? head : head.substring(0, queryAt);

        Rule rule = find(snapshot().byTo, path);
        if (rule == null) {
            return url;
        }
        return rule.from + query + hash;
    }

    /** 取某个“对外地址”对应的实际页面；没配返回空串。 */
    public static String target(String publicPath) {
        String path = publicPath == null ? "" : publicPath.trim();
        if (path.length() == 0) {
            return "";
        }
        Rule rule = find(snapshot().byFrom, path);
        return rule == null ? "" : rule.to;
    }

    /* ---------- 内部实现 ---------- */

    /** 一条路由规则。 */
    public static final class Rule {
        /** 对外地址（配置文件里 {@code route.<from>} 的 from）。 */
        public final String from;
        /** 实际页面（站内以 / 开头的路径，或 http(s) 绝对地址）。 */
        public final String to;
        /** true=302 跳到实际页面；false=服务端内部转发。 */
        public final boolean redirect;
        /** true=直接访问实际页面时 302 回对外地址。 */
        public final boolean hide;
        /** true=直接访问实际页面时返回 404（优先于 hide）。 */
        public final boolean hide404;

        Rule(String from, String to, boolean redirect, boolean hide, boolean hide404) {
            this.from = from;
            this.to = to;
            this.redirect = redirect;
            this.hide = hide;
            this.hide404 = hide404;
        }
    }

    /** 一份路由表的快照（byFrom 查对外地址，byTo 查实际页面）。 */
    private static final class Snapshot {
        private final Map<String, Rule> byFrom;
        private final Map<String, Rule> byTo;
        private final long stamp;
        /** 下次允许再看文件时间戳的时刻（毫秒）。 */
        private volatile long nextCheck;

        Snapshot(Map<String, Rule> byFrom, Map<String, Rule> byTo, long stamp) {
            this.byFrom = byFrom;
            this.byTo = byTo;
            this.stamp = stamp;
        }
    }

    /** 取当前路由表：文件修改时间变了就重新读（改配置文件不用重启）。 */
    private static Snapshot snapshot() {
        Snapshot snap = cache;
        if (snap != null && System.currentTimeMillis() < snap.nextCheck) {
            return snap;
        }
        synchronized (OaonesRoutes.class) {
            snap = cache;
            long now = System.currentTimeMillis();
            if (snap != null && now < snap.nextCheck) {
                return snap;
            }
            String dir = propDir();
            File prop = new File(dir + PROP_FILE);
            long stamp = prop.exists() ? prop.lastModified() : 0L;
            if (snap != null && snap.stamp == stamp) {
                snap.nextCheck = now + RELOAD_CHECK_MS;
                return snap;
            }
            Snapshot fresh = load(prop, stamp);
            fresh.nextCheck = now + RELOAD_CHECK_MS;
            cache = fresh;
            return fresh;
        }
    }

    /** 读路由表：只认 key 以 {@code route.} 开头的配置项，其它配置项一律忽略。 */
    private static Snapshot load(File prop, long stamp) {
        Map<String, Rule> byFrom = new LinkedHashMap<String, Rule>();
        int fromProp = readProperties(prop, byFrom);
        Map<String, Rule> byTo = new LinkedHashMap<String, Rule>();
        for (Rule rule : byFrom.values()) {
            String to = pathOf(rule.to);
            if (to.length() > 0 && !byTo.containsKey(to)) {
                byTo.put(to, rule);
            }
        }
        if (fromProp == 0) {
            OaonesLog.step("URL 路由：没有读到任何 route.* 配置（" + prop.getAbsolutePath() + "），本次按“不路由”处理");
        }
        return new Snapshot(byFrom, byTo, stamp);
    }

    /** 读 properties 形式：key 去掉 {@code route.} 前缀就是对外地址。 */
    private static int readProperties(File file, Map<String, Rule> byFrom) {
        if (file == null || !file.exists() || !file.isFile()) {
            return 0;
        }
        Reader reader = null;
        try {
            Properties props = new Properties();
            // 路由项（key 以 route. 开头）的值都是地址、只用 ASCII，按什么编码读都不影响路由本身；
            // 这里按 GBK 读，与 oaones.appsyn.properties 的存盘编码一致，注释里的中文也不会读成乱码
            // （平台 Properties.load(InputStream) 的 ISO-8859-1 口径与还原办法见 OaonesProps#decodePropValue）
            reader = new InputStreamReader(new FileInputStream(file), "GBK");
            props.load(reader);
            int count = 0;
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String key = String.valueOf(entry.getKey()).trim();
                if (!key.startsWith(KEY_PREFIX)) {
                    continue;
                }
                Rule rule = parse(key.substring(KEY_PREFIX.length()), String.valueOf(entry.getValue()));
                if (rule != null) {
                    byFrom.put(rule.from, rule);
                    count++;
                }
            }
            return count;
        } catch (Throwable t) {
            OaonesLog.error("读取 URL 路由配置失败（按“不路由”继续）：" + file.getAbsolutePath(), t);
            return 0;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {
                    // 关流失败无需处理
                }
            }
        }
    }

    /** 解析一行路由：{@code <实际页面>[|forward|redirect][|hide|hide404]}。 */
    private static Rule parse(String from, String value) {
        String src = normalizeFrom(from);
        String raw = value == null ? "" : value.trim();
        if (src.length() == 0 || raw.length() == 0) {
            OaonesLog.step("URL 路由：配置不完整已忽略（对外地址=" + from + "，实际页面=" + value + "）");
            return null;
        }
        String[] tokens = raw.split("\\|");
        String to = tokens[0].trim();
        if (!(to.startsWith("/") && !to.startsWith("//")) && !isAbsolute(to)) {
            OaonesLog.step("URL 路由：实际页面必须是站内路径（/ 开头）或 http(s) 绝对地址，已忽略：" + raw);
            return null;
        }
        boolean redirect = false;
        boolean hide = false;
        boolean hide404 = false;
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].trim().toLowerCase();
            if (token.length() == 0) {
                continue;
            }
            if ("redirect".equals(token) || "302".equals(token)) {
                redirect = true;
            } else if ("forward".equals(token)) {
                redirect = false;
            } else if ("hide".equals(token)) {
                hide = true;
            } else if ("hide404".equals(token)) {
                hide404 = true;
            } else {
                OaonesLog.step("URL 路由：无法识别的写法（已忽略该段）：" + token + "（配置：" + raw + "）");
            }
        }
        return new Rule(src, to, redirect, hide, hide404);
    }

    /** 对外地址归一：去空格、必须以 / 开头（且不是 // 跨站地址）、去掉多余的结尾斜线。 */
    private static String normalizeFrom(String from) {
        String src = from == null ? "" : from.trim();
        if (!src.startsWith("/") || src.startsWith("//")) {
            if (src.length() > 0) {
                OaonesLog.step("URL 路由：对外地址必须以 / 开头（且不能是 // 跨站地址），已忽略：" + src);
            }
            return "";
        }
        while (src.length() > 1 && src.endsWith("/")) {
            src = src.substring(0, src.length() - 1);
        }
        return src;
    }

    /** 查表：先按原样查，再按“去掉结尾斜线”的写法查（兼容 /oaones/appsyn/login/ 这种带斜线的访问）。 */
    private static Rule find(Map<String, Rule> table, String path) {
        Rule rule = table.get(path);
        if (rule != null) {
            return rule;
        }
        String decoded = decode(path);
        rule = table.get(decoded);
        if (rule != null) {
            return rule;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return table.get(path.substring(0, path.length() - 1));
        }
        return null;
    }

    /** 当前请求的站内路径（去掉上下文路径）。 */
    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String context = request.getContextPath();
        if (context != null && context.length() > 0 && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri;
    }

    /** 只取地址里的路径部分（去掉查询串与锚点）。 */
    private static String pathOf(String url) {
        String value = url == null ? "" : url.trim();
        int cut = value.length();
        int hashAt = value.indexOf('#');
        if (hashAt >= 0 && hashAt < cut) {
            cut = hashAt;
        }
        int queryAt = value.indexOf('?');
        if (queryAt >= 0 && queryAt < cut) {
            cut = queryAt;
        }
        return value.substring(0, cut);
    }

    private static boolean isAbsolute(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /** 302：站内地址写相对路径，绝对地址原样透传（口径见类注释）。 */
    private static void redirect(HttpServletResponse response, String location) {
        String target = location == null ? "" : location.trim();
        if (!isAbsolute(target) && !(target.startsWith("/") && !target.startsWith("//"))) {
            target = "/";
        }
        response.setStatus(302);
        response.setHeader("Location", target);
    }

    /** 跳转时把原请求的查询串带上（内部转发由容器自动保留，只有 302 需要自己拼）。 */
    private static String withQuery(String target, String query) {
        if (query == null || query.length() == 0) {
            return target;
        }
        return target.indexOf('?') < 0 ? target + "?" + query : target + "&" + query;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Throwable t) {
            return value;
        }
    }

    /** 路由配置文件所在目录（与平台 properties 同目录：ecology/WEB-INF/prop/）。 */
    private static String propDir() {
        try {
            String dir = GCONST.getPropertyPath();
            if (dir != null && dir.length() > 0) {
                return dir.endsWith(File.separator) ? dir : dir + File.separator;
            }
        } catch (Throwable ignore) {
            // 平台常量取不到时退回下面的根目录拼法
        }
        try {
            String root = GCONST.getRootPath();
            if (root != null && root.length() > 0) {
                return root + "WEB-INF" + File.separator + "prop" + File.separator;
            }
        } catch (Throwable ignore) {
            // 平台还没初始化（如单元测试）时返回空串，按当前工作目录取相对路径
        }
        return "";
    }
}
