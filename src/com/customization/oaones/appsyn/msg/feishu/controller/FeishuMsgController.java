package com.customization.oaones.appsyn.msg.feishu.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.common.OaonesAdmin;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.msg.feishu.common.FeishuMsgConst;
import com.customization.oaones.appsyn.msg.feishu.common.FeishuMsgProps;
import com.customization.oaones.appsyn.msg.feishu.entity.MsgApp;
import com.customization.oaones.appsyn.msg.feishu.entity.MsgConfig;
import com.customization.oaones.appsyn.msg.feishu.entity.MsgLog;
import com.customization.oaones.appsyn.msg.feishu.entity.MsgTypeDef;
import com.customization.oaones.appsyn.msg.feishu.service.MsgAppService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgConfigService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgLogService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgRenderService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgSendService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgTenantService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgTypeAppService;
import com.customization.oaones.appsyn.msg.feishu.service.MsgTypeService;
import com.customization.oaones.appsyn.msg.feishu.sub.MsgPushQueue;
import com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl;
import weaver.general.BaseBean;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一消息中心控制器（action 前缀 msg.）。
 *
 * <p>覆盖：推送配置、飞书应用、消息类型映射、推送日志、数据看板、测试推送、
 * 外部统一发送接口。写操作限系统管理员或 oaones.properties#msg.admin.ids 指定人员。</p>
 */
public class FeishuMsgController extends BaseController {

    private final MsgConfigService configService = new MsgConfigService();
    private final MsgAppService appService = new MsgAppService();
    private final MsgTypeService typeService = new MsgTypeService();
    private final MsgLogService logService = new MsgLogService();
    private final MsgSendService sendService = new MsgSendService();
    private final MsgRenderService renderService = new MsgRenderService();

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("msg.send".equals(action)) {
            // 供 /oaones/Feishu/msgapi.jsp 外部统一消息接口复用（密钥已在 JSP 内校验）
            return sendService.push(readPushRequest(request, OaonesUtil.i(operatorId, 0)));
        }
        assertMsgAdmin(operatorId);

        // “消息设置 → 租户开关”：本平台（飞书）租户的消息通知开关
        // （页面把企微与飞书两侧清单合并展示，各自只能改自己那一半）
        if ("msg.tenantList".equals(action)) {
            return OaonesUtil.ok(new MsgTenantService().list());
        }
        if ("msg.tenantToggle".equals(action)) {
            return OaonesUtil.ok(new MsgTenantService().toggle(
                    req(request, "cfg_id"), req(request, "msg_enable"), operatorId));
        }

        if ("msg.config.get".equals(action)) {
            JSONObject data = new JSONObject();
            MsgConfig config = configService.get();
            data.put("config", configJson(config));
            data.put("apps", appJson(appService.listEnabled(), config.getDefaultAppId()));
            data.put("queue", MsgPushQueue.status());
            data.put("msgtypes", msgtypeOptions());
            // OA 访问地址前缀：默认自动取后台系统设置里的 OA 网址（页面展示来源，便于核对）
            data.put("oaBase", renderService.oaBaseInfo());
            return OaonesUtil.ok(data);
        }
        if ("msg.config.save".equals(action)) {
            MsgConfig config = readConfig(request);
            configService.save(config, OaonesUtil.i(operatorId, 0));
            return OaonesUtil.ok();
        }
        if ("msg.app.list".equals(action)) {
            JSONObject data = new JSONObject();
            data.put("list", appJson(appService.list(), configService.get().getDefaultAppId()));
            // 飞书租户（组织同步）候选：列表展示与筛选用
            data.put("cfgs", new ConfigServiceImpl().listForUser(operatorId));
            return OaonesUtil.ok(data);
        }
        if ("msg.app.test".equals(action)) {
            // 应用在“应用绑定”页维护，这里只做鉴权连通测试
            return OaonesUtil.ok(appService.test(appService.getById(OaonesUtil.i(req(request, "id"), 0))));
        }
        if ("msg.app.default".equals(action)) {
            // 设为推送缺省应用：只写 uf_oaones_msg_config.default_app_id
            appService.setDefaultApp(OaonesUtil.i(req(request, "id"), 0), OaonesUtil.i(operatorId, 0));
            return OaonesUtil.ok();
        }
        if ("msg.type.list".equals(action)) {
            JSONObject data = new JSONObject();
            // 消息类型清单以泛微标准页（后台 应用中心 → 消息类型）为准：
            // 分组 / 消息来源 / 消息类型实时读标准表，本模块只在其上叠加“推不推 + 推到哪个应用”等设置。
            // 标准页新建类型后刷新本页即可看到，无需在本模块重复维护类型清单。
            // 名称按登录人语言取（标准表里的名称是多语言串）
            int lang = currentLang();
            data.putAll(typeService.standardView(lang));
            data.put("options", msgtypeOptions());
            data.put("stdOptions", standardTypeOptions());
            data.put("cardActions", cardActionOptions());
            data.put("apps", appJson(appService.listEnabled(), configService.get().getDefaultAppId()));
            // 租户清单（本平台全部租户）随列表一次预加载：
            // 页面“推送目标应用”列的明细弹框按租户逐行绑定应用，打开弹框不再发请求
            data.put("tenants", new MsgTenantService().list());
            return OaonesUtil.ok(data);
        }
        if ("msg.type.app.save".equals(action)) {
            // 消息类型「推送目标应用」明细：整体覆盖保存某消息类型的“租户 → 应用”绑定
            // （binds = "租户ID:应用ID,租户ID:应用ID"；没提交的租户＝不绑定，按回退口径推送）
            String stdConfigId = req(request, "std_config_id").trim();
            int stdType = OaonesUtil.i(req(request, "std_type"), 0);
            int rows = new MsgTypeAppService().save(stdConfigId, stdType,
                    parseAppBinds(req(request, "binds")), OaonesUtil.i(operatorId, 0));
            JSONObject data = new JSONObject();
            data.put("rows", rows);
            return OaonesUtil.ok(data);
        }
        if ("msg.type.save".equals(action)) {
            MsgTypeDef def = readType(request);
            typeService.save(def, OaonesUtil.i(operatorId, 0));
            JSONObject data = new JSONObject();
            data.put("id", def.getId());
            return OaonesUtil.ok(data);
        }
        if ("msg.type.delete".equals(action)) {
            // 既支持按本模块行主键删除，也支持按“标准消息类型 / 独立来源”删除（恢复到默认口径）
            int rowId = OaonesUtil.i(req(request, "id"), 0);
            // 标准消息类型ID 是字符串（内置类型 "20"、自定义类型 "1LbVgIrt"）
            String stdConfigId = req(request, "std_config_id").trim();
            int stdType = OaonesUtil.i(req(request, "std_type"), 0);
            MsgTypeDef row = rowId > 0 ? typeService.getById(rowId)
                    : (stdConfigId.length() > 0 && !"0".equals(stdConfigId)
                        ? typeService.getByStdConfigId(stdConfigId)
                        : typeService.getByStdType(stdType));
            if (row != null && row.getId() != null) {
                // 一并清掉该消息类型的“租户 → 应用”明细，避免留下孤儿绑定
                new MsgTypeAppService().deleteByType(row.getStdConfigId(),
                        row.getStdType() == null ? 0 : row.getStdType());
                typeService.delete(row.getId());
            }
            return OaonesUtil.ok();
        }
        if ("msg.type.batch".equals(action)) {
            // 列表页勾选后批量启用 / 停用：keys 为勾选行（"std:<标准消息类型ID>" / "orphan:<来源编号>"，
            // 与列表行的 data-kind / data-key 一致），只改状态（不改形态与模板）；
            // 还没单独配置过的行按默认口径建一行再改，因此与行内“启用 / 停用”同一口径。
            JSONObject data = new JSONObject();
            int yx = OaonesUtil.i(req(request, "yx"), FeishuMsgConst.STATE_ENABLE);
            int lang = currentLang();
            int rows = 0;
            int failed = 0;
            for (String key : splitString(req(request, "keys"))) {
                int sep = key.indexOf(':');
                String kind = (sep > 0 ? key.substring(0, sep) : "").trim().toLowerCase();
                String raw = (sep > 0 ? key.substring(sep + 1) : key).trim();
                boolean std = "std".equals(kind);
                if (!std && !"orphan".equals(kind)) {
                    continue;
                }
                // 标准消息类型ID 是字符串（自定义类型是 "1LbVgIrt" 这种），所以 std 行不转数字
                String stdKey = std ? raw : "";
                int stdType = std ? 0 : OaonesUtil.i(raw, 0);
                if (std ? stdKey.length() == 0 : stdType <= 0) {
                    continue;
                }
                try {
                    typeService.quickSave(stdKey, stdType, yx, null, OaonesUtil.i(operatorId, 0), lang);
                    rows++;
                } catch (Throwable t) {
                    failed++;
                    OaonesLog.error("[msg] 批量设置消息类型状态失败：" + key, t);
                }
            }
            data.put("rows", rows);
            data.put("failed", failed);
            return OaonesUtil.ok(data);
        }
        if ("msg.type.quick".equals(action)) {
            // 列表里内联改“状态 / 推送目标应用”（没配置过的类型按默认口径建一行）
            // 标准消息类型ID 是字符串（内置类型 "20"、自定义类型 "1LbVgIrt"）
            String stdConfigId = req(request, "std_config_id").trim();
            int stdType = OaonesUtil.i(req(request, "std_type"), 0);
            Integer yx = req(request, "yx").length() == 0 ? null : OaonesUtil.i(req(request, "yx"), 0);
            Integer appId = req(request, "app_id").length() == 0 ? null : OaonesUtil.i(req(request, "app_id"), 0);
            MsgTypeDef saved = typeService.quickSave(stdConfigId, stdType, yx, appId,
                    OaonesUtil.i(operatorId, 0), FeishuMsgConst.LANG_ZH_CN);
            JSONObject data = new JSONObject();
            data.put("id", saved == null ? 0 : saved.getId());
            data.put("yx", saved == null ? null : saved.getYx());
            data.put("appId", saved == null ? null : saved.getAppId());
            return OaonesUtil.ok(data);
        }
        if ("msg.type.test".equals(action)) {
            // 列表页“测试推送”：按该消息类型的模板与配置，向指定接收人推一条真实消息
            MsgSendService.MsgPushRequest pushRequest = readPushRequest(request, OaonesUtil.i(operatorId, 0));
            if (pushRequest.getDef() == null) {
                String stdConfigId = req(request, "std_config_id").trim();
                int stdType = OaonesUtil.i(req(request, "std_type"), 0);
                pushRequest.setDef(stdConfigId.length() > 0 && !"0".equals(stdConfigId)
                        ? typeService.effectiveForStdConfig(stdConfigId, FeishuMsgConst.LANG_ZH_CN)
                        : typeService.effectiveForStdType(stdType));
                pushRequest.setStdType(stdType);
            }
            if (pushRequest.getDef() == null) {
                return OaonesUtil.fail("消息类型不存在（可能已在标准页删除），请刷新后重试");
            }
            pushRequest.setIgnoreDedup(true);
            return OaonesUtil.ok(sendService.push(pushRequest));
        }
        if ("msg.log.list".equals(action)) {
            Map<String, String> filters = new HashMap<String, String>();
            filters.put("fromTime", req(request, "from_time"));
            filters.put("toTime", req(request, "to_time"));
            filters.put("bizId", req(request, "biz_id"));
            filters.put("stdType", req(request, "std_type"));
            filters.put("status", req(request, "status"));
            filters.put("keyword", req(request, "keyword"));
            JSONObject data = logService.page(filters,
                    OaonesUtil.i(req(request, "page_index"), 1),
                    OaonesUtil.i(req(request, "page_size"), 20));
            data.put("stdOptions", standardTypeOptions());
            return OaonesUtil.ok(data);
        }
        if ("msg.log.detail".equals(action)) {
            MsgLog log = logService.getById(OaonesUtil.i(req(request, "id"), 0));
            if (log == null) {
                return OaonesUtil.fail("日志不存在");
            }
            return OaonesUtil.ok(logJson(log));
        }
        if ("msg.log.retry".equals(action)) {
            return OaonesUtil.ok(sendService.retryLog(OaonesUtil.i(req(request, "id"), 0)));
        }
        if ("msg.board.get".equals(action)) {
            JSONObject data = logService.board();
            data.put("queue", MsgPushQueue.status());
            return OaonesUtil.ok(data);
        }
        if ("msg.push".equals(action)) {
            // 页面测试推送 / 手工补推（不忽略去重，避免重复骚扰）
            return OaonesUtil.ok(sendService.push(readPushRequest(request, OaonesUtil.i(operatorId, 0))));
        }
        return null;
    }

    // ------------------------------------------------------------------ 权限

    /** 当前登录人语言（标准表里的名称是多语言串，按登录人语言取纯文本）；取不到用简体中文。 */
    private int currentLang() {
        try {
            return OaonesUtil.i(weaver.general.ThreadVarLanguage.getLanguage(), FeishuMsgConst.LANG_ZH_CN);
        } catch (Throwable t) {
            return FeishuMsgConst.LANG_ZH_CN;
        }
    }

    /** 消息中心写操作权限：系统管理员（user.id=1）、超管角色（total.admin.roleid）或 oaones.properties#msg.admin.ids 指定人员。 */
    private void assertMsgAdmin(String operatorId) {
        int uid = OaonesUtil.i(operatorId, 0);
        if (OaonesAdmin.isTotalAdmin(operatorId)) {
            return;
        }
        String ids = "";
        try {
            ids = OaonesUtil.s(new BaseBean().getPropValue("oaones", "msg.admin.ids"));
        } catch (Throwable t) {
            ids = "";
        }
        if (ids.trim().length() > 0) {
            for (String item : ids.split(",")) {
                if (uid > 0 && item.trim().equals(String.valueOf(uid))) {
                    return;
                }
            }
        }
        throw new RuntimeException("只有系统管理员或被授权的消息中心管理员可以维护消息中心配置");
    }

    // ------------------------------------------------------------------ 入参 -> 实体

    private MsgConfig readConfig(HttpServletRequest request) {
        MsgConfig config = configService.get();
        config.setPushEnable(OaonesUtil.i(req(request, "push_enable"), 1));
        config.setDefaultAppId(OaonesUtil.i(req(request, "default_app_id"), 0));
        config.setRetryTimes(OaonesUtil.i(req(request, "retry_times"), 3));
        config.setRetryInterval(OaonesUtil.i(req(request, "retry_interval"), 60));
        config.setShardSize(OaonesUtil.i(req(request, "shard_size"), 1000));
        config.setTimeoutMs(OaonesUtil.i(req(request, "timeout_ms"), 30000));
        config.setTokenAdvanceSec(OaonesUtil.i(req(request, "token_advance_sec"), 300));
        config.setDedupWindowSec(OaonesUtil.i(req(request, "dedup_window_sec"), 600));
        config.setLogKeepDays(OaonesUtil.i(req(request, "log_keep_days"), 90));
        config.setTaskKeepDays(OaonesUtil.i(req(request, "task_keep_days"), 30));
        config.setRemark(req(request, "remark"));
        return config;
    }

    private MsgTypeDef readType(HttpServletRequest request) {
        MsgTypeDef def = new MsgTypeDef();
        int id = OaonesUtil.i(req(request, "id"), 0);
        def.setId(id > 0 ? id : null);
        // 标准消息类型ID 是字符串（内置类型 "20"、自定义类型 "1LbVgIrt"）；空 = 独立来源行，落 "0"
        String stdConfigId = req(request, "std_config_id").trim();
        def.setStdConfigId(stdConfigId.length() == 0 ? "0" : stdConfigId);
        // 独立来源（未纳入标准消息类型）才有来源编号；标准消息类型行的来源从标准侧实时读
        def.setStdType(OaonesUtil.i(req(request, "std_type"), 0));
        def.setTypeName(req(request, "type_name"));
        def.setWxMsgtype(req(request, "wx_msgtype"));
        def.setCardAction(req(request, "card_action"));
        def.setTitleTpl(req(request, "title_tpl"));
        def.setContextTpl(req(request, "context_tpl"));
        def.setUrlTpl(req(request, "url_tpl"));
        def.setMobileUrlTpl(req(request, "mobile_url_tpl"));
        def.setCoverUrl(req(request, "cover_url"));
        def.setBtnJson(req(request, "btn_json"));
        def.setAppId(OaonesUtil.i(req(request, "app_id"), 0));
        def.setYx(OaonesUtil.i(req(request, "yx"), FeishuMsgConst.STATE_ENABLE));
        def.setSortNo(OaonesUtil.i(req(request, "sort_no"), 0));
        def.setRemark(req(request, "remark"));
        return def;
    }


    /** 页面测试推送 / 统一消息接口入参。 */
    private MsgSendService.MsgPushRequest readPushRequest(HttpServletRequest request, int operatorId) {
        MsgSendService.MsgPushRequest pushRequest = new MsgSendService.MsgPushRequest();
        int typeId = OaonesUtil.i(req(request, "type_id"), 0);
        pushRequest.setDef(typeId > 0 ? typeService.getById(typeId) : null);
        pushRequest.setStdType(OaonesUtil.i(req(request, "std_type"), 0));
        // 也可以直接按标准「消息类型」ID 指定（消息类型现在按泛微标准页维护，见第一节第 16 条）：
        // 本模块没为该类型单独配置过时，取默认口径（默认形态 + 默认应用）。
        String stdConfigId = req(request, "std_config_id").trim();
        if (pushRequest.getDef() == null && stdConfigId.length() > 0 && !"0".equals(stdConfigId)) {
            pushRequest.setDef(typeService.effectiveForStdConfig(stdConfigId, FeishuMsgConst.LANG_ZH_CN));
        }
        pushRequest.setBizType(defaultIfEmpty(req(request, "biz_type"), FeishuMsgConst.BIZ_CUSTOM));
        pushRequest.setBizId(req(request, "biz_id"));
        pushRequest.setBizTitle(req(request, "biz_title"));
        pushRequest.setOperatorId(operatorId);
        pushRequest.setCfgId(OaonesUtil.i(req(request, "cfg_id"), 0));
        pushRequest.setOaUserIds(splitInt(req(request, "oa_user_ids")));
        pushRequest.setAccounts(splitString(req(request, "accounts")));
        pushRequest.setWxUserids(splitString(req(request, "wx_userids")));
        Map<String, String> vars = new HashMap<String, String>();
        if (req(request, "title").length() > 0) {
            vars.put("title", req(request, "title"));
        }
        if (req(request, "context").length() > 0) {
            vars.put("context", req(request, "context"));
        }
        if (req(request, "target_name").length() > 0) {
            vars.put("targetName", req(request, "target_name"));
            vars.put("bizTitle", req(request, "target_name"));
        }
        if (req(request, "url").length() > 0) {
            vars.put("url", req(request, "url"));
        }
        // v_ 前缀参数直接作为模板变量传入，便于外部系统按业务字段填充模板
        Enumeration<String> names = request.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (name != null && name.startsWith("v_")) {
                vars.put(name.substring(2), OaonesUtil.s(request.getParameter(name)));
            }
        }
        if (!vars.isEmpty()) {
            pushRequest.setVars(vars);
        }
        pushRequest.setSourceJson(JSONObject.toJSONString(paramObject(request)));
        return pushRequest;
    }

    // ------------------------------------------------------------------ 实体 -> JSON

    private JSONObject configJson(MsgConfig config) {
        JSONObject json = new JSONObject();
        json.put("id", config.getId());
        json.put("pushEnable", config.getPushEnable());
        json.put("defaultAppId", config.getDefaultAppId());
        json.put("retryTimes", config.getRetryTimes());
        json.put("retryInterval", config.getRetryInterval());
        json.put("shardSize", config.getShardSize());
        json.put("timeoutMs", config.getTimeoutMs());
        json.put("tokenAdvanceSec", config.getTokenAdvanceSec());
        json.put("dedupWindowSec", config.getDedupWindowSec());
        json.put("logKeepDays", config.getLogKeepDays());
        json.put("taskKeepDays", config.getTaskKeepDays());
        json.put("remark", config.getRemark());
        return json;
    }

    /**
     * 应用列表（来自“应用绑定”表，只读）。
     *
     * @param defaultAppId 推送缺省应用ID（uf_oaones_msg_config.default_app_id），用于标记“默认”
     */
    private JSONArray appJson(List<MsgApp> list, Integer defaultAppId) {
        JSONArray array = new JSONArray();
        if (list == null) {
            return array;
        }
        for (MsgApp app : list) {
            JSONObject json = new JSONObject();
            json.put("id", app.getId());
            json.put("cfgId", app.getCfgId());
            json.put("cfgName", app.getCfgName());
            json.put("appName", app.getAppName());
            json.put("corpId", app.getCorpId());
            json.put("agentId", app.getAgentId());
            json.put("secretTail", appService.secretTail(app));
            json.put("secretFromCfg", app.isSecretFromCfg());
            json.put("yx", app.getYx());
            json.put("remark", app.getRemark());
            json.put("cfgQy", app.getCfgQy());
            // 是否是推送缺省应用（缺省应用记录在消息中心系统参数里，不在应用表上）
            json.put("isDefault", defaultAppId != null && app.getId() != null
                    && defaultAppId.intValue() == app.getId().intValue() ? 1 : 0);
            // 同一行应用同时服务“应用绑定”（工作台免登录），这里标出是否已配入口页面
            json.put("entryConfigured", app.isEntryConfigured());
            json.put("oaPageUrl", app.getOaPageUrl());
            json.put("updateTime", OaonesUtil.s(app.getModedatamodifydatetime()));
            array.add(json);
        }
        return array;
    }

    private JSONObject logJson(MsgLog log) {
        JSONObject json = new JSONObject();
        json.put("id", log.getId());
        json.put("bizType", log.getBizType());
        json.put("bizId", log.getBizId());
        json.put("bizTitle", log.getBizTitle());
        json.put("stdType", log.getStdType());
        json.put("typeName", log.getTypeName());
        json.put("wxMsgtype", log.getWxMsgtype());
        json.put("cardAction", log.getCardAction());
        json.put("appId", log.getAppId());
        json.put("corpId", log.getCorpId());
        json.put("agentId", log.getAgentId());
        json.put("taskId", log.getTaskId());
        json.put("dedupKey", log.getDedupKey());
        json.put("receiverOa", log.getReceiverOa());
        json.put("receiverWx", log.getReceiverWx());
        json.put("receiverCount", log.getReceiverCount());
        json.put("shardIndex", log.getShardIndex());
        json.put("shardTotal", log.getShardTotal());
        json.put("status", log.getStatus());
        json.put("statusText", FeishuMsgConst.logStatusText(log.getStatus() == null ? 0 : log.getStatus()));
        json.put("retryCount", log.getRetryCount());
        json.put("nextRetryTime", log.getNextRetryTime());
        // 结果与报文来自接口日志中心（本表只存状态）；键名保持不变，页面无需改动
        String detailMsg = log.getApiErrMsg() == null ? "" : log.getApiErrMsg();
        String reason = log.getReason() == null ? "" : log.getReason();
        if (reason.length() > 0) {
            detailMsg = detailMsg.length() == 0 ? reason : (detailMsg + " [" + reason + "]");
        }
        json.put("errcode", log.getApiErrCode());
        json.put("errmsg", detailMsg);
        json.put("costMs", log.getApiCostMs());
        json.put("jumpUrl", MsgLogService.jumpUrl(log.getApiReqBody()));
        json.put("srcJson", log.getSrcJson());
        json.put("requestJson", log.getApiReqBody());
        json.put("responseJson", log.getApiRespBody());
        json.put("errorStack", log.getApiErrStack());
        // 时间字段统一来自建模系统标准字段（写入日期/时间 + 最近更新时间）
        json.put("createdDate", log.getModedatacreatedate());
        json.put("createdTime", log.getModedatacreatetime());
        json.put("modifyDatetime", log.getModedatamodifydatetime());
        return json;
    }

    // ------------------------------------------------------------------ 下拉选项

    /** 飞书消息形态选项。 */
    private JSONArray msgtypeOptions() {
        JSONArray array = new JSONArray();
        addOption(array, FeishuMsgConst.FS_TEXT, "文本消息（text：纯文本，链接拼在正文）");
        addOption(array, FeishuMsgConst.FS_POST, "富文本（post：标题 + 段落，正文内可点链接）");
        addOption(array, FeishuMsgConst.FS_INTERACTIVE, "消息卡片（interactive：标题 + 正文 + 按钮）");
        addOption(array, FeishuMsgConst.FS_TASK_CARD, "待办（task_card：进入飞书任务，可办结/失效）");
        return array;
    }

    /** 卡片动作选项。 */
    private JSONArray cardActionOptions() {
        JSONArray array = new JSONArray();
        addOption(array, FeishuMsgConst.CARD_NONE, "不处理卡片");
        addOption(array, FeishuMsgConst.CARD_CREATE, "新建待办卡片");
        addOption(array, FeishuMsgConst.CARD_DONE, "更新卡片为已处理");
        addOption(array, FeishuMsgConst.CARD_REJECTED, "更新卡片为已驳回");
        addOption(array, FeishuMsgConst.CARD_INVALID, "更新卡片为已失效");
        return array;
    }

    private void addOption(JSONArray array, String value, String label) {
        JSONObject item = new JSONObject();
        item.put("value", value);
        item.put("label", label);
        array.add(item);
    }

    /** 泛微标准消息类型选项（平台表读取，读不到时回落到内置清单）。 */
    private JSONArray standardTypeOptions() {
        JSONArray array = new JSONArray();
        for (Map<String, Object> row : typeService.standardTypes(FeishuMsgConst.LANG_ZH_CN)) {
            int code = OaonesUtil.i(String.valueOf(row.get("code")), 0);
            if (code <= 0) {
                continue;
            }
            String label = OaonesUtil.s(String.valueOf(row.get("name")));
            String group = OaonesUtil.s(String.valueOf(row.get("group")));
            if (label.length() == 0) {
                label = MsgTypeService.builtinTypeName(code);
            }
            if (group.length() > 0) {
                label = group + " / " + label;
            }
            JSONObject item = new JSONObject();
            item.put("value", String.valueOf(code));
            item.put("label", label);
            array.add(item);
        }
        return array;
    }

    private List<Integer> splitInt(String value) {
        List<Integer> list = new ArrayList<Integer>();
        for (String item : splitString(value)) {
            list.add(OaonesUtil.i(item, 0));
        }
        return list;
    }

    private List<String> splitString(String value) {
        List<String> list = new ArrayList<String>();
        if (value == null) {
            return list;
        }
        for (String item : Arrays.asList(value.split("[,，\\s]+"))) {
            String trimmed = item.trim();
            if (trimmed.length() > 0) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private String defaultIfEmpty(String value, String def) {
        return value == null || value.trim().length() == 0 ? def : value.trim();
    }

    /**
     * 解析“消息类型 → 租户 → 应用”明细提交值：{@code "租户ID:应用ID,租户ID:应用ID"}。
     *
     * <p>分隔符兼容中英文逗号/分号/竖线/空格，冒号兼容中英文冒号与等号；
     * 只保留 {@code 租户ID > 0} 的项（应用ID 为 0 / 空的项＝该租户不绑定，由服务层统一按“覆盖”处理）。</p>
     */
    private Map<Integer, Integer> parseAppBinds(String value) {
        Map<Integer, Integer> binds = new LinkedHashMap<Integer, Integer>();
        if (value == null || value.trim().length() == 0) {
            return binds;
        }
        for (String item : value.split("[,，;；|\\s]+")) {
            String one = item == null ? "" : item.trim();
            if (one.length() == 0) {
                continue;
            }
            int sep = -1;
            int[] candidates = {one.indexOf(':'), one.indexOf('：'), one.indexOf('=')};
            for (int idx : candidates) {
                if (idx > 0 && (sep < 0 || idx < sep)) {
                    sep = idx;
                }
            }
            String cfgId = sep > 0 ? one.substring(0, sep).trim() : one;
            String appId = sep > 0 ? one.substring(sep + 1).trim() : "";
            int cfg = OaonesUtil.i(cfgId, 0);
            if (cfg > 0) {
                binds.put(Integer.valueOf(cfg), Integer.valueOf(OaonesUtil.i(appId, 0)));
            }
        }
        return binds;
    }

}
