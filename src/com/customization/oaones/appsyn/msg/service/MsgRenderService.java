package com.customization.oaones.appsyn.msg.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cloudstore.dev.api.bean.MessageBean;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.common.MsgConst;
import com.customization.oaones.appsyn.msg.common.MsgProps;
import com.customization.oaones.appsyn.msg.dto.MsgReceiver;
import com.customization.oaones.appsyn.msg.entity.MsgApp;
import com.customization.oaones.appsyn.msg.entity.MsgTypeDef;

import weaver.system.SystemComInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息渲染服务：模板变量替换 + 企业微信 5 类消息体组装。
 *
 * <p>模板占位符统一为 {@code ${变量名}}，可用变量：
 * {@code title / context / targetName / targetId / date / time / creater / bizTitle / bizId /
 * receiver / appName}，以及标准消息 {@code params} 里的任意键（{@code ${params.xxx}}）。</p>
 *
 * <p>{@code ${bizId}} 是推送用的<strong>业务单号</strong>（流程消息为
 * {@code requestid + "-" + 12位uuid}，见 {@link MsgConst#withBizSeq}），
 * 需要原始流程号（如拼跳转地址）用标准消息自带的 {@code ${targetId}}。</p>
 *
 * <p><strong>多语言口径</strong>：OA 标准消息里的流程名 / 人员姓名等字段带 E9 多语言标记
 * （{@code ~`~`7 中文`~`8 English`~`9 繁體`~`~}），原样下发到企业微信会显示成标记串；
 * 渲染时统一按<strong>接收人的 OA 语言</strong>（{@code hrmresource.systemlanguage}）
 * 取值成纯文本，取不到时用系统默认语言（见 {@link #multiLang(String, int)}）。</p>
 */
public class MsgRenderService {

    /** E9 多语言值的起始标记（GCONST.LANG_CONTENT_PREFIX，取不到平台常量时用它兜底）。 */
    private static final String MULTI_LANG_PREFIX = "~`~`";
    /** 系统默认语言读不到时的兜底语言：7 简体中文。 */
    public static final int DEFAULT_LANG = 7;

    /** OA 访问地址缓存有效期：10 分钟（系统设置里的地址变动不频繁，避免每条消息都读库）。 */
    private static final long OA_BASE_TTL_MS = 10L * 60 * 1000;
    /** 读取系统设置里的 OA 访问地址的超时（毫秒）：推送线程不能卡在“读配置”上。 */
    private static final long SYSTEM_URL_TIMEOUT_MS = 5000L;
    /** 取不到 OA 访问地址时的缓存时长（毫秒）：只压一小会儿，避免一次性故障让链接空 10 分钟。 */
    private static final long OA_BASE_EMPTY_TTL_MS = 60L * 1000;
    /** 企业微信网页授权 state 的长度上限（官方口径 128 字节，留一点余量）。 */
    private static final int STATE_MAX_LEN = 120;

    private static volatile String cachedOaBase;
    private static volatile long cachedOaBaseAt;
    /** 地址来源（页面展示用）。 */
    private static volatile String cachedOaBaseFrom;
    /** 仅供本地冒烟测试注入的 OA 访问地址（生产为 null，一律从系统设置里读）。 */
    private static volatile String testedOaBase;
    /** 是否已由冒烟测试注入过地址（注入后不再去读系统设置，独立 JVM 里没有平台连接池）。 */
    private static volatile boolean testedOaBaseInjected;
    /** 相对链接告警的时间戳（同类告警限频）。 */
    private static volatile long warnedRelativeAt;

    /**
     * 供本地冒烟测试（{@code oaones/appsyn/tools/MsgRenderSmokeTest}）注入 OA 访问地址：
     * 生产环境不调用本方法，地址一律从系统设置里读。
     *
     * <p>传空串表示“按未配置地址”参与自检（相对地址不补全、任务卡片不带链接），
     * 注入后不再去读后台系统设置 —— 独立 JVM 里没有 Resin 连接池，读系统设置会失败。</p>
     */
    public static void setOaBaseForTest(String base) {
        testedOaBase = base == null ? "" : base.trim();
        testedOaBaseInjected = true;
        cachedOaBase = null;
        cachedOaBaseAt = 0L;
        cachedOaBaseFrom = "";
    }

    /** 恢复“按后台系统设置读 OA 访问地址”（冒烟测试结束或需要验证真实读法时调用）。 */
    public static void clearOaBaseForTest() {
        testedOaBase = null;
        testedOaBaseInjected = false;
        cachedOaBase = null;
        cachedOaBaseAt = 0L;
        cachedOaBaseFrom = "";
    }

    /** 由泛微标准消息构造模板变量。 */
    public Map<String, String> variablesFromStdMessage(MessageBean bean, MsgReceiver receiver) {
        Map<String, String> vars = new HashMap<String, String>();
        if (bean == null) {
            return vars;
        }
        put(vars, "title", bean.getTitle());
        put(vars, "context", bean.getContext());
        put(vars, "targetName", bean.getTargetName());
        put(vars, "targetId", bean.getTargetId());
        put(vars, "date", bean.getDate());
        put(vars, "time", bean.getTime());
        put(vars, "bizTitle", bean.getBizTitle());
        // 标准消息没有 bizId 字段：业务单号不再直接取 targetId，而由 MsgSendService 统一给出
        // （流程 = requestid + "-" + 12 位推送流水，见 MsgConst#withBizSeq），
        // 这里不占位，由 MsgSendService#buildBaseVars 用 request.getBizId() 补齐；
        // 需要原始流程号（如拼跳转地址）用 ${targetId}。
        // 标准消息自带的链接（流程即流程详情页）：跳转地址模板留空时用它，不再一律回落到 OA 首页
        put(vars, "linkUrl", bean.getLinkUrl());
        put(vars, "linkMobileUrl", bean.getLinkMobileUrl());
        put(vars, "bizType", bean.getBizType());
        put(vars, "desc", bean.getDesc());
        put(vars, "creater", createrName(bean.getCreater()));
        put(vars, "messageId", bean.getMessageId());
        if (bean.getParams() != null) {
            for (Map.Entry<String, Object> item : bean.getParams().entrySet()) {
                put(vars, "params." + item.getKey(), OaonesUtil.nullToEmpty(item.getValue()));
            }
        }
        fillReceiver(vars, receiver);
        return vars;
    }

    /** 由外部统一消息接口入参构造模板变量。 */
    public Map<String, String> variablesFromParams(Map<String, Object> params, MsgReceiver receiver) {
        Map<String, String> vars = new HashMap<String, String>();
        if (params != null) {
            for (Map.Entry<String, Object> item : params.entrySet()) {
                put(vars, item.getKey(), OaonesUtil.nullToEmpty(item.getValue()));
                put(vars, "params." + item.getKey(), OaonesUtil.nullToEmpty(item.getValue()));
            }
        }
        if (vars.get("title") == null && vars.get("context") != null) {
            vars.put("title", vars.get("context"));
        }
        fillReceiver(vars, receiver);
        return vars;
    }

    private void fillReceiver(Map<String, String> vars, MsgReceiver receiver) {
        if (receiver == null) {
            return;
        }
        put(vars, "receiver", receiver.getOaName());
        put(vars, "receiverWx", receiver.getWxUserid());
    }

    /** 模板渲染：{@code ${key}} 替换；变量缺失按空串处理（多语言按系统默认语言取值）。 */
    public String render(String template, Map<String, String> vars) {
        return render(template, vars, 0);
    }

    /**
     * 模板渲染：{@code ${key}} 替换 + 多语言取值。
     *
     * <p>渲染结果里如果带 E9 多语言标记（变量原值里带，或模板里手写了），
     * 统一按 {@code lang} 取成纯文本后再交给企业微信；{@code lang <= 0} 表示按系统默认语言。</p>
     *
     * @param lang 接收人的 OA 语言ID（{@code hrmresource.systemlanguage}）
     */
    public String render(String template, Map<String, String> vars, int lang) {
        if (template == null || template.length() == 0) {
            return "";
        }
        String result = template;
        if (vars != null) {
            for (Map.Entry<String, String> item : vars.entrySet()) {
                String key = item.getKey();
                if (key == null) {
                    continue;
                }
                result = result.replace("${" + key + "}", item.getValue() == null ? "" : item.getValue());
            }
        }
        // 未识别的占位符统一清空，避免企业微信端出现 ${xxx} 字样
        return multiLang(result.replaceAll("\\$\\{[^}]{0,60}\\}", ""), lang);
    }

    /**
     * E9 多语言取值：把多语言标记取成指定语言的纯文本。
     *
     * <p>OA 里“姓名 / 流程名 / 消息内容”等字段可能存成多语言值，形如
     * {@code ~`~`7 系统管理员`~`8 system administrator`~`9 系統管理員`~`~}
     * （标记可能嵌在一段文字的中间）。企业微信不认这套标记，原样下发就会出现
     * “流程「xx」由 ~`~`7 系统管理员`~`8 ...`~`~ 于 ... 提交”，所以在下发前按语言取值。</p>
     *
     * <p>取值用平台 {@code weaver.general.Util#formatMultiLang(String, String)}：
     * 优先取 {@code lang} 对应的语言；该语言没有翻译时取 7（简体中文）那一份；
     * 没有该语言的翻译且也没有 7 时返回能取到的内容。不含标记的字符串原样返回。</p>
     *
     * @param lang 语言ID（{@code hrmresource.systemlanguage}）；{@code <= 0} 按系统默认语言
     */
    public String multiLang(String value, int lang) {
        String text = OaonesUtil.s(value);
        if (text.length() == 0 || text.indexOf(MULTI_LANG_PREFIX) < 0) {
            return text;
        }
        int language = lang > 0 ? lang : defaultLanguage();
        try {
            String converted = weaver.general.Util.formatMultiLang(text, String.valueOf(language));
            return converted == null ? text : converted;
        } catch (Throwable t) {
            OaonesLog.error("[msg] 多语言取值失败（lang=" + language + "），按原文下发：" + text, t);
            return text;
        }
    }

    /** 系统默认语言ID（平台读不到时按 7 简体中文）。 */
    public int defaultLanguage() {
        try {
            int lang = weaver.general.ThreadVarLanguage.getDefaultLang();
            return lang > 0 ? lang : DEFAULT_LANG;
        } catch (Throwable t) {
            return DEFAULT_LANG;
        }
    }

    /**
     * 组装企业微信消息体。
     *
     * @param def       消息类型映射
     * @param vars      模板变量
     * @param app       企微应用
     * @param wxUserids 本批接收人（已分片）
     * @param taskId    任务卡片 task_id（非卡片消息传空）
     */
    public JSONObject buildMessage(MsgTypeDef def, Map<String, String> vars, MsgApp app,
                                   List<String> wxUserids, String taskId) {
        return buildMessage(def, vars, app, wxUserids, taskId, 0);
    }

    /**
     * 组装企业微信消息体（按接收人的 OA 语言取多语言值）。
     *
     * @param lang 本批接收人的 OA 语言ID；{@code <= 0} 按系统默认语言
     */
    public JSONObject buildMessage(MsgTypeDef def, Map<String, String> vars, MsgApp app,
                                   List<String> wxUserids, String taskId, int lang) {
        String wxMsgtype = def.getWxMsgtype() == null ? MsgConst.WX_TEXT : def.getWxMsgtype();
        String title = render(def.getTitleTpl(), vars, lang);
        String context = render(def.getContextTpl(), vars, lang);
        // 跳转地址：模板 → 标准消息自带链接（流程即流程详情页）→ 通用变量；没有业务链接才回落 OA 首页
        String url = resolveUrl(def.getUrlTpl(), vars, null, app);
        // 移动端跳转：模板 → 标准消息自带的移动端链接 → PC 端地址
        String mobileDefUrl = firstNonBlank(vars, "linkMobileUrl", "linkUrl", "url");
        String mobileUrl = resolveUrl(def.getMobileUrlTpl(), vars,
                mobileDefUrl.length() > 0 ? mobileDefUrl : url, app);

        JSONObject body = new JSONObject();
        body.put("touser", join(wxUserids));
        body.put("agentid", agentIdValue(app));
        body.put("msgtype", MsgConst.WX_TASK_CARD.equals(wxMsgtype) ? "taskcard" : wxMsgtype);
        // 卡片类消息允许重复推送同一 task_id（企业微信按 task_id 更新），其余消息不去重刷屏
        body.put("duplicate_check_interval", 1800);

        if (MsgConst.WX_TEXT.equals(wxMsgtype)) {
            JSONObject text = new JSONObject();
            // 文本消息没有链接字段：有业务链接时拼进正文，企业微信客户端会把网址渲染成可点链接
            String textUrl = businessUrl(def.getUrlTpl(), vars, null, app);
            if (textUrl.length() == 0) {
                textUrl = businessUrl(def.getMobileUrlTpl(), vars, null, app);
            }
            text.put("content", textContentWithUrl(title, context, textUrl));
            body.put("text", text);
        } else if (MsgConst.WX_NEWS.equals(wxMsgtype)) {
            JSONObject article = new JSONObject();
            article.put("title", title);
            article.put("description", context);
            article.put("url", url);
            String cover = render(def.getCoverUrl(), vars, lang);
            if (cover != null && cover.length() > 0) {
                article.put("picurl", cover);
            }
            JSONArray articles = new JSONArray();
            articles.add(article);
            JSONObject news = new JSONObject();
            news.put("articles", articles);
            body.put("news", news);
        } else if (MsgConst.WX_TEXTCARD.equals(wxMsgtype)) {
            JSONObject textcard = new JSONObject();
            textcard.put("title", title);
            textcard.put("description", context);
            textcard.put("url", mobileUrl != null && mobileUrl.length() > 0 ? mobileUrl : url);
            textcard.put("btntxt", "详情");
            body.put("textcard", textcard);
        } else if (MsgConst.WX_TEMPLATE_CARD.equals(wxMsgtype)) {
            body.put("template_card", buildTemplateCard(def, vars, app, title, context, url, lang));
        } else if (MsgConst.WX_TASK_CARD.equals(wxMsgtype)) {
            JSONObject taskcard = new JSONObject();
            taskcard.put("title", title);
            taskcard.put("description", context);
            String cardUrl = mobileUrl != null && mobileUrl.length() > 0 ? mobileUrl : url;
            if (isAbsoluteUrl(cardUrl)) {
                taskcard.put("url", cardUrl);
            } else {
                // 企业微信对卡片链接要求带协议头（相对地址会被 40127 “不支持的uri schema”整条拒收），
                // 任务卡片的 url 是可选字段：取不到完整网址时宁可不带链接，也要把待办送到
                OaonesLog.step("[msg] 任务卡片跳转地址不是完整网址（缺少 http/https 协议头），"
                        + "已按“无链接卡片”发送：" + cardUrl
                        + "；请在后台 系统设置 → 微信设置 → 集成设置 里维护「OA访问地址」");
            }
            taskcard.put("task_id", taskId);
            JSONArray btn = buildTaskCardButtons(def, lang);
            if (btn != null && !btn.isEmpty()) {
                taskcard.put("btn", btn);
            }
            body.put("taskcard", taskcard);
        } else {
            JSONObject text = new JSONObject();
            text.put("content", joinText(title, context));
            body.put("text", text);
            body.put("msgtype", "text");
        }
        return body;
    }

    /** 模板卡片（文本通知型）组装；按钮配置与任务卡片共用 btn_json。 */
    private JSONObject buildTemplateCard(MsgTypeDef def, Map<String, String> vars, MsgApp app,
                                         String title, String context, String url, int lang) {
        JSONObject card = new JSONObject();
        card.put("card_type", "text_notice");
        JSONObject source = new JSONObject();
        source.put("desc", app == null ? "OA" : OaonesUtil.s(app.getAppName()));
        card.put("source", source);
        JSONObject mainTitle = new JSONObject();
        mainTitle.put("title", title);
        mainTitle.put("desc", OaonesUtil.now().substring(11));
        card.put("main_title", mainTitle);
        card.put("sub_title_text", context);
        JSONObject cardAction = new JSONObject();
        cardAction.put("type", 1);
        cardAction.put("url", url);
        card.put("card_action", cardAction);
        JSONArray buttons = buildTemplateCardButtons(def, vars, lang);
        if (buttons != null && !buttons.isEmpty()) {
            card.put("button_list", buttons);
        }
        return card;
    }

    /** 任务卡片按钮：企业微信要求 key/name/replace_name。 */
    private JSONArray buildTaskCardButtons(MsgTypeDef def, int lang) {
        JSONArray result = new JSONArray();
        JSONArray config = parseButtons(def);
        for (int i = 0; i < config.size(); i++) {
            JSONObject item = config.getJSONObject(i);
            JSONObject btn = new JSONObject();
            btn.put("key", safeBtnKey(item.getString("key"), i));
            btn.put("name", multiLang(item.getString("name"), lang));
            btn.put("replace_name", multiLang(item.getString("replace_name") == null
                    ? item.getString("name") : item.getString("replace_name"), lang));
            String color = item.getString("color");
            btn.put("color", color == null || color.length() == 0 ? "blue" : color);
            Boolean bold = item.getBoolean("is_bold");
            btn.put("is_bold", bold != null && bold);
            result.add(btn);
        }
        if (result.isEmpty()) {
            JSONObject btn = new JSONObject();
            btn.put("key", "detail");
            btn.put("name", "查看详情");
            btn.put("replace_name", "已查看");
            btn.put("color", "blue");
            btn.put("is_bold", true);
            result.add(btn);
        }
        return result;
    }

    /** 模板卡片按钮：企业微信要求 text/key/style。 */
    private JSONArray buildTemplateCardButtons(MsgTypeDef def, Map<String, String> vars, int lang) {
        JSONArray result = new JSONArray();
        JSONArray config = parseButtons(def);
        for (int i = 0; i < config.size(); i++) {
            JSONObject item = config.getJSONObject(i);
            JSONObject btn = new JSONObject();
            btn.put("text", render(item.getString("name"), vars, lang));
            btn.put("key", safeBtnKey(item.getString("key"), i));
            String color = item.getString("color");
            if ("blue".equalsIgnoreCase(color)) {
                btn.put("style", 1);
            } else if ("red".equals(color)) {
                btn.put("style", 2);
            } else {
                btn.put("style", 3);
            }
            result.add(btn);
        }
        return result;
    }

    /**
     * 按钮 key 归一：企业微信要求 <b>只能由数字、字母和 “_-@” 组成</b>，为空或不合法会以
     * 42018（按钮key值不合法）/ 42019（缺少按钮key值）拒收；这里替换非法字符，为空时补默认 key。
     */
    private String safeBtnKey(String key, int index) {
        String raw = key == null ? "" : key.trim();
        String safe = MsgConst.safeWxToken(raw);
        if (safe.length() == 0) {
            return "key" + (index + 1);
        }
        if (!safe.equals(raw)) {
            OaonesLog.step("[msg] 按钮 key 含企业微信不允许的字符，已替换为 _：" + raw + " -> " + safe);
        }
        return safe;
    }

    /** agentid：企业微信要求整型，能转数字就按数字下发（字符串形式可能被 40056 拒收）。 */
    public Object agentIdValue(MsgApp app) {
        String value = app == null ? "" : OaonesUtil.s(app.getAgentId()).trim();
        if (value.length() == 0) {
            return "";
        }
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            return value;
        }
    }

    private JSONArray parseButtons(MsgTypeDef def) {
        JSONArray array = new JSONArray();
        String json = def.getBtnJson();
        if (json == null || json.trim().length() == 0) {
            return array;
        }
        try {
            JSONArray parsed = JSON.parseArray(json.trim());
            return parsed == null ? array : parsed;
        } catch (Exception e) {
            OaonesLog.error("[msg] 卡片按钮配置不是合法 JSON，已忽略：id=" + def.getId(), e);
            return array;
        }
    }

    /**
     * 业务跳转地址（**不带 OA 首页兜底**）：模板 → 指定地址 → 标准消息自带链接 → 通用变量
     * （{@code linkUrl} / {@code url} / {@code params.url}）。
     *
     * <p>相对地址按 OA 访问地址补全成企业微信能打开的完整网址；
     * 返回空串表示这条消息确实没有业务链接（文本消息不再拼地址，卡片才回落到 OA 首页）。</p>
     */
    public String businessUrl(String tpl, Map<String, String> vars, String defUrl, MsgApp app) {
        return absoluteUrl(pickUrl(tpl, vars, defUrl), app);
    }

    /**
     * 跳转地址：业务链接优先，没有业务链接时回落到 OA 首页
     * （卡片类消息的 url 是必填项，留空会被企业微信拒收）。
     */
    public String resolveUrl(String tpl, Map<String, String> vars, String defUrl) {
        return resolveUrl(tpl, vars, defUrl, null);
    }

    public String resolveUrl(String tpl, Map<String, String> vars, String defUrl, MsgApp app) {
        String url = businessUrl(tpl, vars, defUrl, app);
        if (url.length() > 0) {
            return url;
        }
        // 没有业务链接才回落到 OA 首页：保证卡片能发出去（相对地址会被企微以 40127 拒收）
        String base = oaBase();
        return base.length() == 0
                ? com.customization.oaones.appsyn.common.OaonesConst.DEFAULT_OA_HOME_URL
                : base + com.customization.oaones.appsyn.common.OaonesConst.DEFAULT_OA_HOME_URL;
    }

    /** 取业务跳转地址原文：模板 → 指定地址 → 标准消息自带链接 → 通用变量（都为空则返回空串）。 */
    private String pickUrl(String tpl, Map<String, String> vars, String defUrl) {
        String url = render(tpl, vars);
        if (url == null || url.trim().length() == 0) {
            url = defUrl == null ? "" : defUrl;
        }
        if (url == null || url.trim().length() == 0) {
            // 标准消息自带链接（getLinkUrl，流程即流程详情页）优先于模板里的通用 url 变量
            url = firstLink(vars, "linkUrl", "url", "params.url");
        }
        return url == null ? "" : url.trim();
    }

    /**
     * 依次返回第一个“像链接”的变量值：站内相对路径（{@code /xxx}）或完整网址
     * （{@code http(s)://}），跳过空值与其它值，避免把占位符/编号当成跳转地址。
     */
    private String firstLink(Map<String, String> vars, String... keys) {
        if (vars == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = vars.get(key);
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.startsWith("/") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }
        return "";
    }

    /** 相对地址补成完整网址：站内路径拼系统设置里的 OA 访问地址。 */
    private String absoluteUrl(String url, MsgApp app) {
        String value = url == null ? "" : url.trim();
        if (value.length() == 0) {
            return "";
        }
        String absolute = value;
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            String base = oaBase();
            if (base.length() == 0) {
                warnRelativeUrl(value);
                return value;
            }
            absolute = base + (value.startsWith("/") ? value : "/" + value);
        }
        return loginEntryUrl(absolute, app);
    }

    /**
     * 把站内业务地址换成【企微工作台免登录入口】地址，解决企业微信里点开卡片“登录超时”。
     *
     * <p>企业微信内置浏览器里没有 OA 登录会话，直接打开 {@code /workflow/request/...}
     * 这类站内地址只会看到登录页 / 登录超时；换成
     * {@code {base}/oaones/appsyn/appBind/WeCom/appEntry.jsp?cfg_id=..&agent_id=..&state=<base64url 目标路径>}
     * 后，入口页会用企业微信网页授权（snsapi_base）静默核实成员身份、写入 OA 会话，
     * 再按 {@code state} 跳到原来的业务页（{@code AppBindServiceImpl#landingUrl} 支持该 state 口径）。</p>
     *
     * <p>只包装本 OA 域名下的站内地址：外部链接原样返回，已经是免登录入口的地址不重复包装；
     * 取不到 OA 对外域名、应用没有 AgentId、或 {@code oaones.appsyn.properties#msg.oa.login.entry=0} 时原样返回。</p>
     */
    private String loginEntryUrl(String url, MsgApp app) {
        String target = url == null ? "" : url.trim();
        boolean hasBind = app != null && app.getId() != null && app.getId() > 0;
        boolean hasCfgAgent = app != null && app.getCfgId() != null && app.getCfgId() > 0
                && OaonesUtil.s(app.getAgentId()).trim().length() > 0;
        if (target.length() == 0 || app == null || MsgProps.loginEntry() == 0
                || (!hasBind && !hasCfgAgent)
                || target.indexOf(OaonesProps.appEntryPath()) >= 0) {
            return target;
        }
        String base = oaBase();
        if (base.length() == 0) {
            return target;
        }
        // 只处理本站地址：完整网址必须是本站前缀，或本身就是站内相对路径
        String path;
        String withoutSlash = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (target.startsWith(withoutSlash + "/")) {
            path = target.substring(withoutSlash.length());
        } else if (target.startsWith("/") && target.indexOf("://") < 0) {
            path = target;
        } else {
            return target;
        }
        // 免登录入口的 state 带走目标路径：入口页 statePath() 支持「站内路径 / URL 编码 / base64url」三种写法，
        // 统一用 base64url —— 字符集只有字母数字和 -_，URL 里不用再转义，企业微信回传也不会被改写
        String state;
        try {
            state = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(path.getBytes("UTF-8"));
        } catch (Exception e) {
            OaonesLog.error("[msg] 生成免登录入口 state 失败，跳转地址按原样下发：" + path, e);
            return target;
        }
        if (state.length() > STATE_MAX_LEN) {
            // 目标地址太长（超过企微 state 上限）时不包入口，避免授权链接本身被企微拒绝；
            // 这种情况建议在「消息类型映射」里直接把跳转地址模板写成最终页面（或用短参数）
            OaonesLog.step("[msg] 目标地址过长（state " + state.length() + " 字节），跳转地址保持原样："
                    + target);
            return target;
        }
        StringBuilder sb = new StringBuilder(withoutSlash).append(OaonesProps.appEntryPath());
        String sep = "?";
        if (hasBind) {
            // 入口页按 b（应用绑定ID）定位最稳，cfg_id/agent_id 一起带上便于排错
            sb.append(sep).append("b=").append(urlEncode(String.valueOf(app.getId())));
            sep = "&";
        }
        sb.append(sep).append("cfg_id=")
                .append(urlEncode(app.getCfgId() == null ? "" : String.valueOf(app.getCfgId())));
        sb.append("&agent_id=").append(urlEncode(OaonesUtil.s(app.getAgentId()).trim()));
        sb.append("&state=").append(urlEncode(state));
        String entry = sb.toString();
        OaonesLog.step("[msg] 跳转地址已换成企微免登录入口（避免登录超时）：" + target + " -> " + entry);
        return entry;
    }

    /** URL 参数编码（失败时返回原值，保证地址至少是完整的）。 */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    /** 依次返回第一个非空变量值（去空格）。 */
    private String firstNonBlank(Map<String, String> vars, String... keys) {
        if (vars == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = vars.get(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 文本消息正文：有<strong>完整网址</strong>时把地址拼在末尾
     * （企业微信客户端会把正文里的网址渲染成可点链接；站内相对路径在企微里点不开，不拼）。
     */
    private String textContentWithUrl(String title, String context, String url) {
        String content = joinText(title, context);
        String value = url == null ? "" : url.trim();
        if (!isAbsoluteUrl(value) || content.indexOf(value) >= 0) {
            return content;
        }
        return content + (content.length() > 0 ? "\n" : "") + "详情：" + value;
    }

    /**
     * OA 访问地址前缀（协议 + 域名 + 端口），用于把站内相对链接补成企业微信能打开的完整网址。
     *
     * <p>**只取后台 系统设置 → 微信设置 → 集成设置 里的「OA访问地址」**（`wechat_Set.oaUrl`，
     * 与 E9 自带企业微信推送用的是同一项）；没有其它兜底来源 ——
     * 没配就按“取不到”处理（任务卡片不带链接、文本卡片等记“已跳过”并写明该配哪一项）。</p>
     *
     * <p>结果带 10 分钟缓存：推送是高频路径，不能每条消息都去读系统设置（该读取走数据库）；
     * 读不到或读取异常时按空串处理（跳转地址退化为原样输出），保证推送不被拖住；
     * 空结果只缓存 1 分钟，避免一次库抖动让后续推送在 10 分钟内都丢链接。</p>
     */
    private String oaBase() {
        long now = System.currentTimeMillis();
        String cached = cachedOaBase;
        if (cached != null && now - cachedOaBaseAt < ttlOf(cached)) {
            return cached;
        }
        synchronized (MsgRenderService.class) {
            cached = cachedOaBase;
            if (cached != null && System.currentTimeMillis() - cachedOaBaseAt < ttlOf(cached)) {
                return cached;
            }
            if (testedOaBaseInjected) {
                // 冒烟测试注入过地址（可以是空串=按未配置）：直接用它，不去读系统设置
                String tested = testedOaBase == null ? "" : testedOaBase;
                cachedOaBase = tested;
                cachedOaBaseAt = System.currentTimeMillis();
                cachedOaBaseFrom = "冒烟测试注入";
                return tested;
            }
            // 链接前缀**只取系统设置里的 OA 访问地址**（后台 系统设置 → 微信设置 → 集成设置 → OA访问地址，
            // 与 E9 自带企业微信推送同一项；读法见 {@link #systemOaUrl()}：平台入口 → 直查 wechat_Set）
            String systemUrl = "";
            try {
                systemUrl = new SystemComInfo().getOaaddress();
            } catch (Throwable t) {
                // 平台连接池未就绪 / 读系统设置异常时按“未配置”处理：
                // 不能因为一个地址读不到就让整条推送失败（任务卡片会去掉链接照常送达）
                OaonesLog.step("[msg] 读取系统设置里的「OA访问地址」失败，按未配置处理：" + t);
            }
            String base = normalizeBase(systemUrl, "");
            String from = base.length() > 0 ? "系统设置里的 OA 访问地址" : "";
            if (base.length() > 0) {
                OaonesLog.step("[msg] OA 访问地址前缀取自 " + from + "：" + base);
            } else {
                OaonesLog.step("[msg] 系统设置里没取到「OA访问地址」，卡片跳转地址只能是站内相对路径"
                        + "（企微会以 40127 拒收，任务卡片会去掉链接）；"
                        + "请在后台 系统设置 → 微信设置 → 集成设置 里维护「OA访问地址」");
            }
            cachedOaBase = base;
            cachedOaBaseAt = System.currentTimeMillis();
            cachedOaBaseFrom = from;
            return base;
        }
    }

    /**
     * 页面上展示用：当前生效的 OA 访问地址前缀与来源，以及各候选来源的取值。
     */
    public JSONObject oaBaseInfo() {
        JSONObject info = new JSONObject();
        String base = oaBase();
        info.put("base", base);
        info.put("from", cachedOaBaseFrom == null ? "" : cachedOaBaseFrom);
        // 系统设置里的原始值（读不到地址时展示给运维看：是没配还是读不到）
        String systemUrl = "";
        try {
            systemUrl = new SystemComInfo().getOaaddress();
        } catch (Throwable t) {
            // 页面上只用于提示“读不到”，不能因为读系统设置异常让整个推送配置页报错
            OaonesLog.step("[msg] 读取系统设置里的「OA访问地址」失败：" + t);
        }
        info.put("systemOaUrl", OaonesUtil.s(systemUrl));
        return info;
    }

    /**
     * 缓存的存活时间：取到地址时缓存 10 分钟；**没取到地址（空串）时只缓存 1 分钟**，
     * 避免一次库抖动 / 超时让之后的推送在 10 分钟内全部丢链接。
     */
    private static long ttlOf(String base) {
        return base != null && base.length() > 0 ? OA_BASE_TTL_MS : OA_BASE_EMPTY_TTL_MS;
    }

    /** 地址前缀归一：补协议、去掉结尾的 /，并校验确实带了域名。 */
    private static String normalizeBase(String url, String from) {
        String value = OaonesUtil.s(url).trim();
        if (value.length() == 0) {
            return "";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            // 站内路径（/oaones/appsyn/... 等）取不出域名；只有“域名[:端口]”这种值才补协议
            if (value.startsWith("/") || value.startsWith("\\")) {
                return "";
            }
            value = "https://" + value;
        }
        int start = value.indexOf("://") + 3;
        int slash = value.indexOf('/', start);
        if (slash > 0) {
            // 只保留 协议+域名+端口，路径部分丢弃（业务链接自己带路径）
            value = value.substring(0, slash);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        String host = start >= value.length() ? "" : value.substring(start);
        // 必须是个像域名的地址（带点；IP 也带点）：否则宁可按“取不到”处理，
        // 免得拼出 https:/... 这种坏链接让企微整条拒收（40127）
        if (host.length() == 0 || host.indexOf('.') < 0) {
            return "";
        }
        OaonesLog.step("[msg] OA 访问地址前缀取自 " + from + "：" + value);
        return value;
    }

    /** 相对链接告警（同类告警每 10 分钟最多写一条，避免刷日志）。 */
    private void warnRelativeUrl(String url) {
        long now = System.currentTimeMillis();
        if (now - warnedRelativeAt < OA_BASE_TTL_MS) {
            return;
        }
        warnedRelativeAt = now;
        OaonesLog.step("[msg] 跳转地址不是完整网址（缺少 http/https 协议头）：" + url
                + "；企业微信里可能点不开，请在后台 系统设置 → 微信设置 → 集成设置 里维护「OA访问地址」");
    }

    /** 清空 OA 访问地址缓存（系统设置里改了 OA 地址后可调用，一般无需手工触发）。 */
    public static void clearOaBaseCache() {
        cachedOaBase = null;
        cachedOaBaseAt = 0L;
        warnedRelativeAt = 0L;
    }

    /** 当前生效的 OA 访问地址前缀（重推历史报文时用它补全相对链接）。 */
    public String currentOaBase() {
        return oaBase();
    }

    /**
     * 地址是否带协议头（企业微信要求卡片链接必须是 http/https 的完整网址，否则报 40127）。
     */
    public static boolean isAbsoluteUrl(String url) {
        String value = OaonesUtil.s(url).trim();
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String joinText(String title, String context) {
        StringBuilder sb = new StringBuilder();
        if (title != null && title.length() > 0) {
            sb.append(title);
        }
        if (context != null && context.length() > 0) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(context);
        }
        return sb.toString();
    }

    private String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.trim().length() == 0) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(value.trim());
            }
        }
        return sb.toString();
    }

    private void put(Map<String, String> vars, String key, String value) {
        if (key == null) {
            return;
        }
        vars.put(key, OaonesUtil.s(value));
    }

    private String createrName(int userId) {
        if (userId <= 0) {
            return "";
        }
        try {
            return OaonesUtil.s(new weaver.hrm.resource.ResourceComInfo().getLastname(String.valueOf(userId)));
        } catch (Throwable t) {
            return String.valueOf(userId);
        }
    }
}
