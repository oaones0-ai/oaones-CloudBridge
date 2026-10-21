package com.customization.oaones.appsyn.common;

import com.customization.oaones.appsyn.dao.OpLogMapper;
import com.customization.oaones.appsyn.entity.OpLog;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.systeminfo.SystemEnv;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作监控采集器：一级菜单「操作监控平台」的数据来源。
 *
 * <p>本类是唯一的采集口径（页面只查 {@code uf_oaones_op_log}，不自己采集）：</p>
 * <ul>
 *   <li>登录（{@code op_type=login}）：会话里第一次出现登录态的页面请求
 *       —— 不是“打开登录页”，而是登录成功后第一次真正进入 OA 页面；</li>
 *   <li>退出（{@code op_type=logout}）：请求落到 E9 的退出地址
 *       （{@code /login/Logout.jsp}、{@code /login/LogoutForMain.jsp} 等）；
 *       同一会话换账号后再登录，会按“登录人ID变了”重新记一条登录；</li>
 *   <li>菜单访问（{@code op_type=menu}）：请求地址命中 E9 菜单表 {@code LeftMenuInfo}
 *       里配置的菜单（{@code linkAddress / fullrouteurl / routeurl / mobxrouteurl}，
 *       带 {@code #} 路由的只比 {@code #} 之前的部分），记录命中的
 *       菜单ID / 菜单名称 / 菜单地址 + 本次实际请求地址。</li>
 * </ul>
 *
 * <p>口径与边界：</p>
 * <ul>
 *   <li>只记页面请求：GET / POST、{@code Accept} 带 {@code text/html}、不是 XHR、
 *       不是静态资源、不是本模块自己的 {@code api.jsp} / {@code msgapi.jsp}；</li>
 *   <li>只记命中菜单表的地址：没配成菜单的页面（平台内部片段页、弹框页等）不写日志，
 *       既避免海量流水，也正好对上“具体到访问的菜单即可”的口径；</li>
 *   <li>极低开销：菜单索引与开关都按 60 秒缓存，读登录态只读会话属性
 *       （{@code weaver_user@bean}，与 {@code HrmUserVarify} 同一口径），不做额外查询；</li>
 *   <li>绝不影响业务：写库 / 解析失败只写一条 mylog 日志后静默，
 *       永远不会把异常抛回请求；开关 {@code oaones.properties#op.log.enable=0} 可整体关闭。</li>
 * </ul>
 */
public final class OaonesOpLog {

    /** 登录态在 E9 会话里的键（与 {@code weaver.hrm.HrmUserVarify#getUser} 同一口径）。 */
    private static final String SESSION_USER_KEY = "weaver_user@bean";
    /** 会话标记：最近一次已经记过登录的人员ID（换账号后会重新记一条登录）。 */
    private static final String SESSION_LOGIN_UID = "oaones.op.loginUid";
    /** 会话标记：登录时落库的部门名快照（后续菜单访问直接复用，避免重复查库）。 */
    private static final String SESSION_DEPT_NAME = "oaones.op.deptName";
    /** 会话标记：菜单访问去重表（key = 菜单ID，value = 上次落库的毫秒时间）。 */
    private static final String SESSION_MENU_AT = "oaones.op.menuAt";
    /** 请求级防重入标记（同一请求被多个过滤器链重复经过时只处理一次）。 */
    private static final String ATTR_DONE = "oaones.op.done";

    /** 需要跳过的静态资源后缀（大小写不敏感）。 */
    private static final String[] SKIP_EXT = {
            ".js", ".css", ".map", ".json", ".xml", ".txt", ".ico", ".png", ".jpg", ".jpeg",
            ".gif", ".bmp", ".svg", ".webp", ".woff", ".woff2", ".ttf", ".eot", ".otf",
            ".mp3", ".mp4", ".wav", ".avi", ".pdf", ".zip", ".rar", ".7z", ".exe", ".apk",
            ".xls", ".xlsx", ".doc", ".docx", ".ppt", ".pptx", ".csv"
    };

    /** 需要跳过的站内目录前缀（大小写不敏感）。 */
    private static final String[] SKIP_PREFIX = {
            "/wui/js/", "/wui/css/", "/wui/image", "/wui/common/", "/js/", "/css/",
            "/images/", "/images_face/", "/appres/", "/tempfile/", "/weaver.file",
            "/api/", "/services/", "/dwr/", "/debug/"
    };

    /** 会话级菜单去重表的最大条数（防止长时间不退出时 Map 无限变大）。 */
    private static final int MENU_DEDUP_MAX = 200;
    /** 菜单索引 / 配置缓存的有效期（毫秒）：E9 菜单或开关改动最多 1 分钟生效。 */
    private static final long CACHE_MS = 60 * 1000L;
    /** 写库失败后的静默窗口（毫秒）：表还没建时不刷日志。 */
    private static final long DISABLE_MS = 10 * 60 * 1000L;

    /** 配置快照。 */
    private static volatile Snapshot config;
    private static volatile long configExpireAt;
    /** 菜单索引缓存。 */
    private static volatile MenuIndex menuIndex;
    /** 写库失败静默窗口截止时间。 */
    private static volatile long disableUntil;

    private OaonesOpLog() {
    }

    /* ------------------------------------------------------------------
     * 采集入口（由 OaonesOpLogFilter 调用）
     * ------------------------------------------------------------------ */

    /**
     * 观察一次请求：命中“登录 / 退出 / 菜单访问”就落一条日志。
     *
     * <p>本方法自己吞掉所有异常：采集失败绝不影响业务请求。</p>
     */
    public static void observe(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        try {
            if (request.getAttribute(ATTR_DONE) != null) {
                return;
            }
            request.setAttribute(ATTR_DONE, Boolean.TRUE);
            Snapshot cfg = config();
            if (!cfg.enabled) {
                return;
            }
            String path = path(request);
            if (!isPageRequest(request, path)) {
                return;
            }
            HttpSession session = request.getSession(false);
            User user = sessionUser(session);

            // 1) 退出：先判退出地址（此刻会话里通常还带登录态）
            if (isLogoutPath(path)) {
                recordLogout(request, session, user);
                return;
            }

            if (user == null) {
                return;
            }

            // 2) 登录：会话里第一次出现该登录人（换账号会重新记一条）
            String uid = String.valueOf(user.getUID());
            boolean firstOfSession = session != null && !uid.equals(attr(session, SESSION_LOGIN_UID));
            if (firstOfSession) {
                OpLog e = base(request, session, user, OpLog.TYPE_LOGIN);
                fillUserSnapshot(e, user, session);
                record(e);
                session.setAttribute(SESSION_LOGIN_UID, uid);
            }

            // 3) 菜单访问：命中 E9 菜单表才记
            MenuItem item = menuOf(path, request.getQueryString());
            if (item == null) {
                return;
            }
            if (session != null && deduped(session, item.id, cfg)) {
                return;
            }
            OpLog e = base(request, session, user, OpLog.TYPE_MENU);
            e.setDeptName(attr(session, SESSION_DEPT_NAME));
            e.setMenuId(item.id);
            e.setMenuName(item.name);
            e.setMenuPath(cap(item.address, 490));
            record(e);
        } catch (Throwable ignore) {
            // 留痕失败不能影响业务
        }
    }

    /** 记一条“退出”：登录态已经没了时，用会话上留的人员ID快照兜底。 */
    private static void recordLogout(HttpServletRequest request, HttpSession session, User user) {
        OpLog e = base(request, session, user, OpLog.TYPE_LOGOUT);
        if (e.getUserId() == null || e.getUserId().intValue() <= 0) {
            String lastUid = attr(session, SESSION_LOGIN_UID);
            if (lastUid.length() > 0) {
                e.setUserId(Integer.valueOf(OaonesUtil.i(lastUid, 0)));
                e.setDeptName(attr(session, SESSION_DEPT_NAME));
            }
        }
        if (e.getUserId() != null && e.getUserId().intValue() > 0) {
            fillUserSnapshot(e, user, session);
            record(e);
        }
        if (session != null) {
            session.removeAttribute(SESSION_LOGIN_UID);
        }
    }

    /* ------------------------------------------------------------------
     * 落库
     * ------------------------------------------------------------------ */

    /** 写一条操作监控日志（失败静默 10 分钟，避免表没建时刷日志）。 */
    private static void record(OpLog e) {
        if (e == null || System.currentTimeMillis() < disableUntil) {
            return;
        }
        try {
            final String table = OaonesProps.opLogTable();
            OaonesMyBatis.run(OpLogMapper.class, mapper -> mapper.insert(table, e));
        } catch (Throwable t) {
            disableUntil = System.currentTimeMillis() + DISABLE_MS;
            OaonesLog.error("写入操作监控日志失败（已临时静默，请确认已执行 uf_oaones_op_log 建表脚本）", t);
        }
    }

    /** 组装一条日志的公共字段（人员、时间、来源、设备）。 */
    private static OpLog base(HttpServletRequest request, HttpSession session, User user, String opType) {
        OpLog e = new OpLog();
        e.setUserId(Integer.valueOf(user == null ? 0 : user.getUID()));
        e.setLoginName(cap(user == null ? "" : user.getLoginid(), 100));
        e.setUserName(cap(user == null ? "" : realName(user), 190));
        e.setOpType(opType);
        e.setPageUrl(cap(pageUrl(request), 990));
        e.setClientIp(cap(clientIp(request), 100));
        e.setDevice(device(request));
        e.setSessionId(cap(session == null ? "" : session.getId(), 100));
        e.setFormmodeid(OaonesProps.opLogModeId());
        e.setModedatacreatedate(OaonesUtil.nowDate());
        e.setModedatacreatetime(OaonesUtil.nowTime());
        return e;
    }

    /** 登录 / 退出时补齐“登录名 + 姓名 + 主部门”快照（查一次 HrmResource）。 */
    private static void fillUserSnapshot(OpLog e, User user, HttpSession session) {
        if (user == null || user.getUID() <= 0) {
            return;
        }
        try {
            Map<String, Object> row = OaonesMyBatis.call(OpLogMapper.class,
                    mapper -> mapper.findUser(user.getUID()));
            if (row == null) {
                return;
            }
            String loginName = text(row.get("login_name"));
            String userName = text(row.get("user_name"));
            String deptName = text(row.get("dept_name"));
            if (loginName.length() > 0) {
                e.setLoginName(cap(loginName, 100));
            }
            if (userName.length() > 0) {
                e.setUserName(cap(userName, 190));
            }
            e.setDeptName(cap(deptName, 190));
            if (session != null && deptName.length() > 0) {
                session.setAttribute(SESSION_DEPT_NAME, cap(deptName, 190));
            }
        } catch (Throwable ignore) {
            // 快照补不齐不影响这条日志
        }
    }

    /* ------------------------------------------------------------------
     * 菜单索引（E9 菜单表 LeftMenuInfo）
     * ------------------------------------------------------------------ */

    /** 菜单项：命中的菜单ID / 名称 / 配置地址。 */
    public static final class MenuItem {
        private final String id;
        private final String name;
        private final String address;

        MenuItem(String id, String name, String address) {
            this.id = id;
            this.name = name;
            this.address = address;
        }

        public String getId() { return id; }

        public String getName() { return name; }

        public String getAddress() { return address; }
    }

    /** 按“路径 + 查询串”找菜单；找不到返回 null（不是菜单页，不记日志）。 */
    public static MenuItem menuOf(String path, String query) {
        MenuIndex index = menuIndex();
        if (index == null || path == null || path.length() == 0) {
            return null;
        }
        String p = normalize(path);
        String q = query == null ? "" : query.trim();
        if (q.length() > 0) {
            MenuItem exact = index.byFull.get(p + "?" + q);
            if (exact != null) {
                return exact;
            }
        }
        MenuItem same = index.byFull.get(p);
        if (same != null) {
            return same;
        }
        return index.byPath.get(p);
    }

    /** 全部菜单（页面“菜单”筛选用），按名称排序后返回。 */
    public static List<MenuItem> allMenus() {
        MenuIndex index = menuIndex();
        return index == null ? new ArrayList<MenuItem>() : index.ordered;
    }

    /** 菜单索引缓存（60 秒；E9 菜单改动最多 1 分钟生效）。 */
    private static MenuIndex menuIndex() {
        MenuIndex index = menuIndex;
        long now = System.currentTimeMillis();
        if (index != null && now < index.expireAt) {
            return index;
        }
        synchronized (OaonesOpLog.class) {
            index = menuIndex;
            now = System.currentTimeMillis();
            if (index != null && now < index.expireAt) {
                return index;
            }
            MenuIndex fresh = loadMenus(now);
            menuIndex = fresh;
            return fresh;
        }
    }

    /** 读 E9 菜单表建索引：地址 → 菜单（带查询串的精确匹配优先，其次按路径匹配）。 */
    private static MenuIndex loadMenus(long now) {
        MenuIndex index = new MenuIndex(now + CACHE_MS);
        try {
            List<Map<String, Object>> rows = OaonesMyBatis.call(OpLogMapper.class,
                    mapper -> mapper.listMenus());
            if (rows == null) {
                return index;
            }
            Map<String, MenuItem> named = new LinkedHashMap<String, MenuItem>();
            for (Map<String, Object> row : rows) {
                String id = text(row.get("id"));
                if (id.length() == 0) {
                    continue;
                }
                String name = menuName(row);
                String[] addresses = {
                        text(row.get("linkaddress")), text(row.get("fullrouteurl")),
                        text(row.get("routeurl")), text(row.get("mobxrouteurl"))
                };
                for (String address : addresses) {
                    String candidate = address.trim();
                    if (candidate.length() == 0) {
                        continue;
                    }
                    int hash = candidate.indexOf('#');
                    if (hash >= 0) {
                        candidate = candidate.substring(0, hash);
                    }
                    String full = normalize(candidate);
                    if (full.length() == 0) {
                        continue;
                    }
                    String bare = full;
                    int question = bare.indexOf('?');
                    if (question >= 0) {
                        bare = bare.substring(0, question);
                    }
                    MenuItem item = named.get(id);
                    if (item == null) {
                        item = new MenuItem(id, name, address.trim());
                        named.put(id, item);
                    }
                    if (!index.byFull.containsKey(full)) {
                        index.byFull.put(full, item);
                    }
                    if (bare.length() > 0 && !index.byPath.containsKey(bare)) {
                        index.byPath.put(bare, item);
                    }
                }
            }
            index.ordered.addAll(named.values());
        } catch (Throwable t) {
            OaonesLog.error("读取 E9 菜单表 LeftMenuInfo 失败（操作监控本次按“无菜单”继续）", t);
        }
        return index;
    }

    /**
     * 菜单名称：{@code useCustomName=1} 且 customName 非空时用 customName
     * （可能是 {@code ~`~`7 中文~`~`8 English~`~`} 这种多语言串，取中文段），
     * 否则用平台标签 {@code labelId} 取中文文案；都取不到时退化为“菜单#id”。
     */
    private static String menuName(Map<String, Object> row) {
        String id = text(row.get("id"));
        String customName = text(row.get("customname")).trim();
        String useCustom = text(row.get("usecustomname")).trim();
        if (customName.length() > 0 && ("1".equals(useCustom) || useCustom.length() == 0)) {
            String name = localized(customName);
            if (name.length() > 0) {
                return cap(name, 190);
            }
        }
        int labelId = OaonesUtil.i(text(row.get("labelid")), 0);
        if (labelId > 0) {
            try {
                String label = SystemEnv.getHtmlLabelName(labelId, 7);
                if (label != null && label.trim().length() > 0) {
                    return cap(localized(label.trim()), 190);
                }
            } catch (Throwable ignore) {
                // 取不到平台标签就退化为下面的兜底名
            }
        }
        return id.length() == 0 ? "" : ("菜单#" + id);
    }

    /**
     * 多语言串取值：平台与部分自定义菜单的名字形如
     * {@code ~`~`7 工程基建~`~`8 Engineering & Construction~`~`}，
     * 按分隔符切开后取“语言号 = 7（中文）”那一段；普通文本原样返回。
     */
    static String localized(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf("~`~`") < 0) {
            return text;
        }
        String[] parts = text.split("~`~`");
        String fallback = "";
        for (String part : parts) {
            String seg = part.trim();
            if (seg.length() == 0) {
                continue;
            }
            int space = seg.indexOf(' ');
            if (space <= 0) {
                continue;
            }
            String lang = seg.substring(0, space).trim();
            String label = seg.substring(space + 1).trim();
            if (label.length() == 0) {
                continue;
            }
            if ("7".equals(lang)) {
                return label;
            }
            if (fallback.length() == 0 && "8".equals(lang)) {
                fallback = label;
            }
        }
        return fallback.length() > 0 ? fallback : text;
    }

    /* ------------------------------------------------------------------
     * 请求判定与工具
     * ------------------------------------------------------------------ */

    /** 是否算页面请求：GET / POST + 接受 HTML + 非 XHR + 非静态资源 + 非本模块接口入口。 */
    private static boolean isPageRequest(HttpServletRequest request, String path) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            return false;
        }
        String xhr = request.getHeader("X-Requested-With");
        if (xhr != null && xhr.toLowerCase().indexOf("xmlhttprequest") >= 0) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept == null || accept.toLowerCase().indexOf("text/html") < 0) {
            return false;
        }
        String lower = path.toLowerCase();
        for (String ext : SKIP_EXT) {
            if (lower.endsWith(ext)) {
                return false;
            }
        }
        for (String prefix : SKIP_PREFIX) {
            if (lower.startsWith(prefix)) {
                return false;
            }
        }
        // 本模块自己的接口入口（api.jsp / msgapi.jsp）不是页面
        if (lower.endsWith("/api.jsp") || lower.endsWith("/msgapi.jsp")) {
            return false;
        }
        return true;
    }

    /** E9 的退出地址（命中即记一条“退出”）。 */
    private static boolean isLogoutPath(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        return "/login/logout.jsp".equals(lower)
                || "/login/logoutformain.jsp".equals(lower)
                || "/login/logout_ad.jsp".equals(lower)
                || "/login/logout_saml.jsp".equals(lower)
                || "/login/nouserlogout.jsp".equals(lower);
    }

    private static User sessionUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        try {
            Object value = session.getAttribute(SESSION_USER_KEY);
            return value instanceof User ? (User) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 菜单访问去重：同一会话在去重窗口内重复打开同一菜单只记一条
     * （页面里的 iframe / 重定向 / 刷新会短时间内多次请求同一个菜单地址）。
     */
    @SuppressWarnings("unchecked")
    private static boolean deduped(HttpSession session, String menuId, Snapshot cfg) {
        if (cfg.dedupeSeconds <= 0 || menuId == null || menuId.length() == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long window = cfg.dedupeSeconds * 1000L;
        Map<String, Long> seen;
        Object value = session.getAttribute(SESSION_MENU_AT);
        if (value instanceof Map) {
            seen = (Map<String, Long>) value;
        } else {
            seen = new HashMap<String, Long>();
            session.setAttribute(SESSION_MENU_AT, seen);
        }
        Long last = seen.get(menuId);
        if (last != null && now - last.longValue() < window) {
            return true;
        }
        if (seen.size() >= MENU_DEDUP_MAX) {
            // 简单淘汰：窗口已经过期的一律清掉，避免长时间不退出时会话里越攒越多
            Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().longValue() >= window) {
                    it.remove();
                }
            }
            if (seen.size() >= MENU_DEDUP_MAX) {
                seen.clear();
            }
        }
        seen.put(menuId, Long.valueOf(now));
        return false;
    }

    /** 当前请求的站内路径（去掉上下文路径）。 */
    private static String path(HttpServletRequest request) {
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

    /** 本次请求地址（站内路径 + 查询串）。 */
    private static String pageUrl(HttpServletRequest request) {
        String path = path(request);
        String query = request.getQueryString();
        return query == null || query.length() == 0 ? path : (path + "?" + query);
    }

    /** 统一归一：去空格、去协议与域名、去掉结尾多余的斜线（保留查询串）。 */
    static String normalize(String value) {
        String url = value == null ? "" : value.trim();
        int scheme = url.indexOf("://");
        if (scheme >= 0) {
            int slash = url.indexOf('/', scheme + 3);
            url = slash >= 0 ? url.substring(slash) : "/";
        }
        while (url.length() > 1 && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String clientIp(HttpServletRequest request) {
        try {
            String ip = Util.getIpAddr(request);
            return ip == null ? "" : ip.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** PC / MOBILE：与模块其它页面同一判断口径（看 User-Agent）。 */
    private static String device(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        String text = ua == null ? "" : ua.toLowerCase();
        if (text.indexOf("mobile") >= 0 || text.indexOf("android") >= 0
                || text.indexOf("iphone") >= 0 || text.indexOf("ipad") >= 0
                || text.indexOf("micromessenger") >= 0 || text.indexOf("dingtalk") >= 0) {
            return "MOBILE";
        }
        return "PC";
    }

    /** 姓名：lastname 为空时退化为登录名。 */
    private static String realName(User user) {
        String name = OaonesUtil.s(user.getLastname()).trim();
        if (name.length() == 0) {
            name = OaonesUtil.s(user.getLoginid()).trim();
        }
        return name;
    }

    private static String attr(HttpSession session, String key) {
        if (session == null) {
            return "";
        }
        try {
            Object value = session.getAttribute(key);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String cap(String value, int max) {
        String s = value == null ? "" : value;
        return (max > 0 && s.length() > max) ? s.substring(0, max) : s;
    }

    /* ------------------------------------------------------------------
     * 配置与索引容器
     * ------------------------------------------------------------------ */

    /** 采集开关 / 去重窗口快照（60 秒刷新一次，改配置最多 1 分钟生效）。 */
    private static final class Snapshot {
        private final boolean enabled;
        private final int dedupeSeconds;

        Snapshot(boolean enabled, int dedupeSeconds) {
            this.enabled = enabled;
            this.dedupeSeconds = dedupeSeconds;
        }
    }

    private static Snapshot config() {
        Snapshot snap = config;
        long now = System.currentTimeMillis();
        if (snap != null && now < configExpireAt) {
            return snap;
        }
        synchronized (OaonesOpLog.class) {
            if (config != null && System.currentTimeMillis() < configExpireAt) {
                return config;
            }
            Snapshot fresh = new Snapshot(OaonesProps.opLogEnabled(), OaonesProps.opLogDedupeSeconds());
            config = fresh;
            configExpireAt = System.currentTimeMillis() + CACHE_MS;
            return fresh;
        }
    }

    /** 菜单索引：精确地址表 + 路径表 + 有序菜单列表。 */
    private static final class MenuIndex {
        private final Map<String, MenuItem> byFull = new HashMap<String, MenuItem>();
        private final Map<String, MenuItem> byPath = new HashMap<String, MenuItem>();
        private final List<MenuItem> ordered = new ArrayList<MenuItem>();
        private final long expireAt;

        MenuIndex(long expireAt) {
            this.expireAt = expireAt;
        }
    }
}
