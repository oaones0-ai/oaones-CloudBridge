package com.customization.oaones.appsyn.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.dao.ApiLogMapper;
import com.customization.oaones.appsyn.entity.ApiLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口日志记录器：把 OAones 与第三方平台之间的每一次接口调用落进 {@code uf_oaones_api_log}。
 *
 * <p>统一口径只有这一处：</p>
 * <ul>
 *   <li><b>出口（OA→平台）</b>：各连接器的底层 HTTP 方法（{@code api.*.*Api}、
 *       {@code msg.*.*Api}、{@code common.OaonesHttp}）在发请求 / 收响应后调用
 *       {@link #out}；平台已知（调用方传入），租户按 {@code corp_id} 反查（带缓存）；</li>
 *   <li><b>入口（平台→OA）</b>：外部接口入口页（统一消息接口 / 工作台免登 / 扫码回调 /
 *       个人微信回调）调用 {@link #inbound}，平台 / 租户 / 接口类型由页面显式给出；</li>
 *   <li><b>接口类型</b>：出口按真实请求路径归类（{@link #apiTypeOf}），入口按业务显式指定。</li>
 * </ul>
 *
 * <p>记录<b>只做留痕</b>：写库失败（表没建、库不可用等）只写一次日志后静默放弃，
 * 绝不影响任何业务接口的返回；接口地址与请求 / 返回报文<b>按原文完整落库</b>
 * （不脱敏、不截断），排查问题时看到的就是与第三方平台之间真实收发的报文。</p>
 */
public final class OaonesApiLog {

    /** 平台：个人微信（没有租户概念，cfg_id = 0）。 */
    public static final int PLATFORM_PERSONAL_WX = 3;

    /** 接口类型：组织同步（成员 / 部门 / 通讯录）。 */
    public static final String TYPE_ORG = "org";
    /** 接口类型：消息推送（消息 / 待办卡片）。 */
    public static final String TYPE_MSG = "msg";
    /** 接口类型：会议 / 日程同步（日程 / 日历）。 */
    public static final String TYPE_MTG = "mtg";
    /** 接口类型：考勤同步（独立一级菜单「考勤同步」的打卡数据读取 / 反向写入）。 */
    public static final String TYPE_ATT = "att";
    /** 接口类型：登录授权（扫码 / 免登 / 换令牌 / 凭证）。 */
    public static final String TYPE_LOGIN = "login";
    /** 接口类型：其他。 */
    public static final String TYPE_OTHER = "other";

    /** 异常堆栈 / 错误信息等非报文段最多保留的字符数（超出截断并标注）。 */
    private static final int MAX_BODY = 20000;

    /** 租户反查缓存：key = 平台 + "|" + corpId，value = [cfgId, 到期时间毫秒, 名称...]（name 另存）。 */
    private static final Map<String, Cfg> CFG_CACHE = new ConcurrentHashMap<String, Cfg>();
    private static final long CFG_TTL_HIT = 5 * 60 * 1000L;
    private static final long CFG_TTL_MISS = 60 * 1000L;
    /** 写库失败（表没建等）后静默窗口，避免每次调用都刷错误日志。 */
    private static volatile long disableUntil;
    /**
     * 出口调用的“当前平台 + 企业标识”上下文（仅服务于共用 HTTP 客户端 {@link OaonesHttp}：
     * 会议 / 日程同步的适配器在调用前后设置 / 清除，HTTP 客户端据此写接口日志）。
     */
    private static final ThreadLocal<String[]> HTTP_CTX = new ThreadLocal<String[]>();
    /**
     * 当前线程最近一次写入的接口日志ID。
     *
     * <p>业务侧的“状态表”（如消息推送日志）不再自己存报文，而是发完接口后取本值做外键关联，
     * 这样同一次接口调用只在接口日志中心存一份。用 {@link #lastId()} 取、用 {@link #clearLastId()} 清。</p>
     */
    private static final ThreadLocal<Long> LAST_ID = new ThreadLocal<Long>();

    private OaonesApiLog() {
    }

    /** 设置共用 HTTP 客户端的出口上下文（平台 + 企业标识），配合 {@link #clearHttpContext()} 使用。 */
    public static void httpContext(int platform, String corpId) {
        HTTP_CTX.set(new String[]{String.valueOf(platform), OaonesUtil.s(corpId)});
    }

    /** 清除出口上下文（务必放在 finally 里，避免线程复用串台）。 */
    public static void clearHttpContext() {
        HTTP_CTX.remove();
    }

    /** 当前线程是否设置了出口上下文。 */
    public static boolean hasHttpContext() {
        return HTTP_CTX.get() != null;
    }

    /** 当前线程出口上下文的平台（未设置返回 -1）。 */
    public static int httpContextPlatform() {
        String[] ctx = HTTP_CTX.get();
        return ctx == null ? -1 : OaonesUtil.i(ctx[0], -1);
    }

    /** 当前线程出口上下文的企业标识（未设置返回空串）。 */
    public static String httpContextCorpId() {
        String[] ctx = HTTP_CTX.get();
        return ctx == null ? "" : ctx[1];
    }

    /** 当前线程最近一次写入的接口日志ID（没有则为 null）。 */
    public static Long lastId() {
        return LAST_ID.get();
    }

    /** 清除当前线程的“最近一次接口日志ID”。 */
    public static void clearLastId() {
        LAST_ID.remove();
    }

    /** 缓存项：租户ID + 名称 + 到期时间。 */
    private static final class Cfg {
        private final int id;
        private final String name;
        private final long expireAt;

        Cfg(int id, String name, long expireAt) {
            this.id = id;
            this.name = name;
            this.expireAt = expireAt;
        }
    }

    /* ------------------------------------------------------------------
     * 出口：OA → 第三方平台
     * ------------------------------------------------------------------ */

    /**
     * 记一次发往第三方平台的接口调用。
     *
     * @param platform  平台：0 企微 / 1 钉钉 / 2 飞书 / 3 个人微信
     * @param corpId    企业标识（corpid / AppKey / App ID；用于反查租户，个人微信可传空）
     * @param method    HTTP 方法
     * @param url       请求地址（含查询串）
     * @param reqBody   请求报文（可为空）
     * @param respBody  返回报文正文（可为空）
     * @param httpStatus HTTP 状态码（0 表示未拿到）
     * @param costMs    耗时（毫秒）
     * @param errorStack 异常堆栈（无异常传 null）
     */
    public static void out(int platform, String corpId, String method, String url,
                           String reqBody, String respBody, int httpStatus, long costMs,
                           String errorStack) {
        try {
            if (!OaonesProps.apiLogEnabled() || System.currentTimeMillis() < disableUntil) {
                return;
            }
            Cfg cfg = platform == PLATFORM_PERSONAL_WX ? new Cfg(0, "", 0L) : resolveCfg(platform, corpId);
            ApiLog e = base(platform, cfg);
            e.setMethod(OaonesUtil.s(method));
            String fullUrl = OaonesUtil.s(url);
            // 列宽保护：URL / 路径 / 错误码按建表脚本里的列宽截断，避免整条日志写不进去
            e.setApiUrl(cap(fullUrl, 990));
            e.setApiPath(cap(pathOf(fullUrl), 490));
            e.setApiType(apiTypeOf(platform, method, fullUrl));
            e.setDirection(0);
            // 报文按原文完整落库（不脱敏、不截断）：排查问题要能看到与第三方真实收发的每一段内容，
            // 业务侧“重推”也直接拿这份报文原样重发。
            e.setReqBody(OaonesUtil.s(reqBody));
            e.setRespBody(OaonesUtil.s(respBody));
            e.setHttpStatus(httpStatus);
            e.setCostMs(Integer.valueOf((int) Math.min(costMs, Integer.MAX_VALUE)));
            Integer errCode = errCodeOf(respBody);
            boolean ok = httpStatus > 0 && httpStatus < 400 && (errCode == null || errCode.intValue() == 0);
            if (errorStack != null) {
                ok = false;
            }
            e.setSuccess(ok ? Integer.valueOf(1) : Integer.valueOf(0));
            e.setErrCode(errCode == null ? "" : cap(String.valueOf(errCode), 60));
            e.setErrMsg(trim(errMsgOf(respBody)));
            e.setErrorStack(trim(OaonesUtil.s(errorStack)));
            record(e);
        } catch (Throwable ignore) {
            // 留痕失败不能影响业务
        }
    }

    /* ------------------------------------------------------------------
     * 入口：第三方平台 → OA
     * ------------------------------------------------------------------ */

    /**
     * 记一次第三方平台调用 OAones 入口的接口。
     *
     * @param platform 平台：0 企微 / 1 钉钉 / 2 飞书 / 3 个人微信
     * @param cfgId    租户配置ID（无租户传 0）
     * @param corpName 租户名称（可空）
     * @param apiType  接口类型（{@link #TYPE_MSG} / {@link #TYPE_LOGIN} / {@link #TYPE_OTHER} 等）
     * @param path     入口路径（如 /oaones/WeCom/msgapi.jsp）
     * @param reqBody  入参摘要
     * @param respBody 返回摘要
     * @param success  是否成功
     * @param errCode  错误码（可空）
     * @param errMsg   错误信息（可空）
     * @param operatorId 触发人（外部调用 / 无会话为 0）
     */
    public static void inbound(int platform, int cfgId, String corpName, String apiType,
                               String path, String reqBody, String respBody, boolean success,
                               String errCode, String errMsg, String operatorId) {
        try {
            if (!OaonesProps.apiLogEnabled() || System.currentTimeMillis() < disableUntil) {
                return;
            }
            Cfg cfg = new Cfg(cfgId, OaonesUtil.s(corpName), 0L);
            ApiLog e = base(platform, cfg);
            e.setMethod("POST");
            e.setApiPath(cap(OaonesUtil.s(path), 490));
            e.setApiUrl(cap(OaonesUtil.s(path), 990));
            e.setApiType(OaonesUtil.s(apiType).length() == 0 ? TYPE_OTHER : apiType);
            e.setDirection(1);
            // 入口调用同样原文完整落库（不脱敏、不截断）
            e.setReqBody(OaonesUtil.s(reqBody));
            e.setRespBody(OaonesUtil.s(respBody));
            e.setHttpStatus(200);
            e.setCostMs(Integer.valueOf(0));
            e.setSuccess(success ? Integer.valueOf(1) : Integer.valueOf(0));
            e.setErrCode(cap(OaonesUtil.s(errCode), 60));
            e.setErrMsg(trim(OaonesUtil.s(errMsg)));
            e.setOperatorId(Integer.valueOf(OaonesUtil.i(OaonesUtil.s(operatorId), 0)));
            record(e);
        } catch (Throwable ignore) {
            // 留痕失败不能影响业务
        }
    }

    /* ------------------------------------------------------------------
     * 接口类型归类（出口按真实路径；入口由页面显式指定）
     * ------------------------------------------------------------------ */

    /**
     * 按真实请求路径归类接口类型。规则只认路径特征，与各平台文档路径一一对应：
     * 通讯录 → 组织同步；消息 / 待办 → 消息推送；日程 / 日历 → 会议日程同步；
     * 令牌 / 鉴权 / 用户信息 → 登录授权；其余 → 其他。
     */
    public static String apiTypeOf(int platform, String method, String url) {
        String path = pathOf(OaonesUtil.s(url)).toLowerCase();
        if (path.length() == 0) {
            return TYPE_OTHER;
        }
        if (contains(path, "/message/") || contains(path, "/topapi/message") || contains(path, "/topapi/workrecord")
                || contains(path, "/open-apis/im/") || contains(path, "/open-apis/task/")
                || contains(path, "send_v2")) {
            return TYPE_MSG;
        }
        if (contains(path, "/schedule") || contains(path, "/topapi/calendar") || contains(path, "/open-apis/calendar")) {
            return TYPE_MTG;
        }
        // 考勤（打卡 / 考勤组 / 补卡）：需求 3.8.3 要求接口日志中心能按接口类型「考勤同步」过滤出本轮的全部调用
        if (contains(path, "/checkin/") || contains(path, "/topapi/attendance") || contains(path, "/topapi/processinstance")
                || contains(path, "/open-apis/attendance") || contains(path, "/open-apis/approval")) {
            return TYPE_ATT;
        }
        if (contains(path, "/cgi-bin/user") || contains(path, "/cgi-bin/department")
                || contains(path, "/topapi/v2/user") || contains(path, "/topapi/v2/department")
                || contains(path, "/topapi/user/getbyunionid") || contains(path, "/open-apis/contact/")) {
            return TYPE_ORG;
        }
        if (contains(path, "gettoken") || contains(path, "getuserinfo") || contains(path, "/oauth2/")
                || contains(path, "/authen/") || contains(path, "/open-apis/auth/") || contains(path, "/sns/")) {
            return TYPE_LOGIN;
        }
        return TYPE_OTHER;
    }

    /** 接口类型文案（页面下拉与记录列表共用）。 */
    public static String apiTypeText(String code) {
        String c = OaonesUtil.s(code);
        if (TYPE_ORG.equals(c)) {
            return "组织同步";
        }
        if (TYPE_MSG.equals(c)) {
            return "消息推送";
        }
        if (TYPE_MTG.equals(c)) {
            return "会议/日程同步";
        }
        if (TYPE_ATT.equals(c)) {
            return "考勤同步";
        }
        if (TYPE_LOGIN.equals(c)) {
            return "登录授权";
        }
        return "其他";
    }

    /** 平台文案：0 企业微信 / 1 钉钉 / 2 飞书 / 3 个人微信。 */
    public static String platformText(int type) {
        if (type == OaonesConst.TYPE_DINGTALK) {
            return "钉钉";
        }
        if (type == OaonesConst.TYPE_FEISHU) {
            return "飞书";
        }
        if (type == PLATFORM_PERSONAL_WX) {
            return "个人微信";
        }
        return "企业微信";
    }

    /* ------------------------------------------------------------------
     * 内部：落库 / 租户反查 / 脱敏
     * ------------------------------------------------------------------ */

    private static ApiLog base(int platform, Cfg cfg) {
        ApiLog e = new ApiLog();
        e.setType(Integer.valueOf(platform));
        e.setCfgId(Integer.valueOf(cfg.id));
        e.setCorpName(cap(cfg.name, 190));
        e.setOperatorId(Integer.valueOf(0));
        e.setFormmodeid(OaonesProps.apiLogModeId());
        e.setModedatacreatedate(OaonesUtil.nowDate());
        e.setModedatacreatetime(OaonesUtil.nowTime());
        return e;
    }

    private static void record(ApiLog e) {
        try {
            final String table = OaonesProps.apiLogTable();
            OaonesMyBatis.run(ApiLogMapper.class, mapper -> mapper.insert(table, e));
            // 关联用：把刚写入的日志ID留给业务侧（如消息推送状态表）做外键，
            // 避免业务表再存一份报文。
            if (e.getId() != null) {
                LAST_ID.set(e.getId());
            }
        } catch (Throwable t) {
            // 表还没建 / 库不可用：只提示一次，之后 10 分钟内静默，避免刷日志
            disableUntil = System.currentTimeMillis() + 10 * 60 * 1000L;
            OaonesLog.error("写入接口日志失败（已临时静默 10 分钟，请确认已执行 uf_oaones_api_log 建表脚本）", t);
        }
    }

    /** 按“平台 + 企业标识”反查租户（带缓存，避免每次接口调用都查库）。 */
    private static Cfg resolveCfg(int platform, String corpId) {
        String key = platform + "|" + OaonesUtil.s(corpId).trim();
        long now = System.currentTimeMillis();
        Cfg cached = CFG_CACHE.get(key);
        if (cached != null && now < cached.expireAt) {
            return cached;
        }
        int id = 0;
        String name = "";
        try {
            final String cfgTable = OaonesProps.cfgTable();
            final String corp = OaonesUtil.s(corpId).trim();
            if (corp.length() > 0) {
                Map<String, Object> row = OaonesMyBatis.call(ApiLogMapper.class,
                        mapper -> mapper.findCfg(cfgTable, platform, corp));
                if (row != null) {
                    id = OaonesUtil.i(String.valueOf(row.get("id")), 0);
                    name = OaonesUtil.s(String.valueOf(row.get("corp_name")));
                }
            }
        } catch (Throwable ignore) {
            // 反查失败退化为 cfg_id=0（不影响业务，日志仍留痕）
        }
        Cfg cfg = new Cfg(id, name, now + (id > 0 ? CFG_TTL_HIT : CFG_TTL_MISS));
        CFG_CACHE.put(key, cfg);
        return cfg;
    }

    /** 取地址的路径部分（去掉协议、域名与查询串），用于接口类型归类与展示。 */
    private static String pathOf(String url) {
        String u = OaonesUtil.s(url);
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        int scheme = u.indexOf("://");
        if (scheme >= 0) {
            int slash = u.indexOf('/', scheme + 3);
            u = slash >= 0 ? u.substring(slash) : "/";
        }
        return u;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack.indexOf(needle) >= 0;
    }

    private static String trim(String value) {
        String s = OaonesUtil.s(value);
        if (s.length() <= MAX_BODY) {
            return s;
        }
        return s.substring(0, MAX_BODY) + "...(truncated)";
    }

    /** 按列宽截断（用于 URL / 路径 / 错误码等定长列，避免超长写库失败）。 */
    private static String cap(String value, int max) {
        String s = OaonesUtil.s(value);
        if (max <= 0 || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    /** 从平台返回报文里取错误码：errcode / code / sub_code，均为 0 表示成功；取不到返回 null。 */
    private static Integer errCodeOf(String respBody) {
        JSONObject json = parse(respBody);
        if (json == null) {
            return null;
        }
        if (json.containsKey("errcode")) {
            return Integer.valueOf(json.getIntValue("errcode"));
        }
        if (json.containsKey("code")) {
            return Integer.valueOf(json.getIntValue("code"));
        }
        if (json.containsKey("sub_code")) {
            return Integer.valueOf(json.getIntValue("sub_code"));
        }
        return null;
    }

    /** 从平台返回报文里取错误信息：errmsg / msg / error_description。 */
    private static String errMsgOf(String respBody) {
        JSONObject json = parse(respBody);
        if (json == null) {
            return "";
        }
        if (json.containsKey("errmsg")) {
            return OaonesUtil.s(json.getString("errmsg"));
        }
        if (json.containsKey("msg")) {
            return OaonesUtil.s(json.getString("msg"));
        }
        if (json.containsKey("error_description")) {
            return OaonesUtil.s(json.getString("error_description"));
        }
        return "";
    }

    private static JSONObject parse(String text) {
        String s = OaonesUtil.s(text).trim();
        if (s.length() == 0 || s.charAt(0) != '{') {
            return null;
        }
        try {
            return JSON.parseObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 异常堆栈转字符串（接口日志记录异常用；无异常返回空串）。 */
    public static String stackTrace(Throwable t) {
        if (t == null) {
            return "";
        }
        java.io.StringWriter writer = new java.io.StringWriter();
        java.io.PrintWriter printer = new java.io.PrintWriter(writer);
        try {
            t.printStackTrace(printer);
        } finally {
            printer.close();
        }
        return trim(writer.toString());
    }

    /**
     * 入口调用的入参（供 JSP 入口页记接口日志用）：把请求参数原样拼成一个 JSON 串
     * （不脱敏、不截断）；调用方可通过 {@code exclude} 指定要跳过的参数，不传则全部记录。
     */
    public static String requestSummary(javax.servlet.http.HttpServletRequest request, String... exclude) {
        if (request == null) {
            return "";
        }
        java.util.Set<String> skip = new java.util.HashSet<String>();
        if (exclude != null) {
            for (String e : exclude) {
                if (e != null) {
                    skip.add(e);
                }
            }
        }
        JSONObject json = new JSONObject();
        java.util.Enumeration<String> names = request.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (skip.contains(name)) {
                continue;
            }
            String value = request.getParameter(name);
            json.put(name, value == null ? "" : value);
        }
        return json.toJSONString();
    }
}
