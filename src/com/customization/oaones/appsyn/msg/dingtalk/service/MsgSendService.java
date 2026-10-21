package com.customization.oaones.appsyn.msg.dingtalk.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cloudstore.dev.api.bean.MessageBean;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.msg.dingtalk.api.DingTalkMsgApi;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgConst;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgProps;
import com.customization.oaones.appsyn.msg.dingtalk.dto.MsgReceiver;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgApp;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgConfig;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgLog;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgTask;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgTypeDef;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 消息推送核心服务：接收人映射 -> 应用/租户路由 -> 模板渲染 -> 分片 -> 去重 -> 发送 -> 日志 -> 重试。
 *
 * <p>入口有三类：</p>
 * <ol>
 *   <li>泛微标准消息中心的消息订阅（{@link #pushFromStdMessage(MessageBean)}）：
 *       流程通知、会议通知等标准消息；</li>
 *   <li>统一消息接口 / 页面测试推送（{@link #push(MsgPushRequest)}）；</li>
 *   <li>失败重推（{@link #retryLog(long)}、{@link #retryPending(int)}）。</li>
 * </ol>
 */
public class MsgSendService {

    private final MsgConfigService configService = new MsgConfigService();
    private final MsgTypeService typeService = new MsgTypeService();
    private final MsgAppService appService = new MsgAppService();
    private final MsgReceiverService receiverService = new MsgReceiverService();
    private final MsgRenderService renderService = new MsgRenderService();
    private final MsgLogService logService = new MsgLogService();
    private final MsgTaskService taskService = new MsgTaskService();

    // ------------------------------------------------------------------ 标准消息入口

    /**
     * 泛微标准消息中心入口：由消息订阅实现类调用。
     *
     * <p><b>从未配置过映射</b>的标准消息类型按默认口径自动登记一行（超管在泛微后台自建的类型、
     * 建模“自定义提醒”等第一次发消息即可推送，见 {@link MsgTypeService#findForPush(int, int)}；
     * 可用 {@code msg.type.auto.register=0} 关闭）；
     * <b>已配置但停用</b>的类型不推，推送失败也不抛异常，避免影响 OA 业务流程。</p>
     *
     * <p><strong>业务单号口径</strong>：标准消息（流程 / 会议 / 其它已配置映射的类型）
     * 统一为 {@code 业务ID + "-" + 12位uuid} —— 流程即 {@code requestid-12位uuid}
     * （同一业务重提 / 加签 / 转办需要重复推送时，不会被幂等去重窗口拦掉）；
     * 卡片任务（OA 待办）按去掉流水的<strong>稳定业务键</strong>（流程即 requestid）绑定，
     * 保证办结 / 退回 / 失效更新的是同一张卡片。</p>
     */
    public void pushFromStdMessage(MessageBean bean) {
        try {
            if (bean == null || bean.getMessageType() == null) {
                return;
            }
            int stdType = bean.getMessageType().getCode();
            // 未配置过的类型按需自动登记一行默认映射；停用的类型照旧不推
            MsgTypeDef def = typeService.findForPush(stdType, bean.getCreater());
            if (def == null) {
                return;
            }
            String bizType = bizTypeOf(stdType);
            // 稳定业务键：去掉推送流水的业务单号（会议等 targetId 形如 64|1234，先按 | 取业务ID）
            String bizKey = cleanBizId(bean.getTargetId());
            // 业务单号：标准消息统一追加 12 位推送流水，允许同一业务重复推送
            String bizId = DingTalkMsgConst.withBizSeq(bizKey);
            MsgPushRequest request = new MsgPushRequest();
            request.setDef(def);
            request.setStdType(stdType);
            request.setStdBean(bean);
            request.setBizType(bizType);
            request.setBizId(bizId);
            request.setBizKey(bizKey);
            // 业务标题只做展示与卡片归属，按系统默认语言取多语言值（姓名 / 流程名的多语言标记不下发到钉钉）
            request.setBizTitle(renderService.multiLang(OaonesUtil.s(bean.getTitle()), 0));
            request.setOperatorId(bean.getCreater());
            request.setOaUserIds(collectOaUserIds(bean));
            request.setAccounts(collectAccounts(bean));
            request.setDedupId(dedupIdOf(bean, request));
            request.setSourceJson(JSON.toJSONString(bean));
            request.setVars(renderService.variablesFromStdMessage(bean, null));
            push(request);
        } catch (Throwable t) {
            OaonesLog.error("[msg] 标准消息推送异常，已忽略以免影响 OA 流程", t);
        }
    }

    /**
     * 标准消息的幂等键：{@code stdmsg|消息ID|接收人指纹}。
     *
     * <p>消息中心对同一条标准消息可能回调多次（多条订阅 / 重复投递），业务单号带推送流水后
     * 每次都不同、挡不住这种重复；用消息ID 去重后同一条消息只推一次。
     * 带接收人指纹是为了兼容“每条消息只带一个接收人、分多次回调”的投递方式，
     * 避免把后面几个接收人的消息也当成重复拦掉。</p>
     *
     * @return 没有消息ID 时返回空串（退回按业务单号去重）
     */
    private String dedupIdOf(MessageBean bean, MsgPushRequest request) {
        String messageId = OaonesUtil.s(bean.getMessageId()).trim();
        if (messageId.length() == 0) {
            return "";
        }
        StringBuilder receivers = new StringBuilder();
        for (Integer id : request.getOaUserIds()) {
            receivers.append(id).append(',');
        }
        for (String account : request.getAccounts()) {
            receivers.append(account).append(',');
        }
        return "stdmsg|" + messageId + "|" + DingTalkMsgConst.fingerprint(receivers.toString());
    }

    /** 标准消息里的接收人（OA 人员ID：userList / userId）。 */
    private List<Integer> collectOaUserIds(MessageBean bean) {
        Set<Integer> ids = new LinkedHashSet<Integer>();
        if (bean.getUserList() != null) {
            for (Object value : bean.getUserList()) {
                int id = OaonesUtil.i(OaonesUtil.nullToEmpty(value), 0);
                if (id > 0) {
                    ids.add(id);
                }
            }
        }
        if (bean.getUserId() > 0) {
            ids.add(bean.getUserId());
        }
        return new ArrayList<Integer>(ids);
    }

    /** 标准消息里的接收人（登录名 / 工号：userLoginIdList / userCodeList）。 */
    private List<String> collectAccounts(MessageBean bean) {
        Set<String> values = new LinkedHashSet<String>();
        if (bean.getUserLoginIdList() != null) {
            for (Object value : bean.getUserLoginIdList()) {
                String item = OaonesUtil.nullToEmpty(value).trim();
                if (item.length() > 0) {
                    values.add(item);
                }
            }
        }
        if (bean.getUserCodeList() != null) {
            for (Object value : bean.getUserCodeList()) {
                String item = OaonesUtil.nullToEmpty(value).trim();
                if (item.length() > 0) {
                    values.add(item);
                }
            }
        }
        return new ArrayList<String>(values);
    }

    /** 标准消息类型 -> 业务来源（用于日志筛选与卡片任务归属）。 */
    private String bizTypeOf(int stdType) {
        if (stdType == 64 || stdType == 81 || stdType == 124 || stdType == 125
                || stdType == 126 || stdType == 127) {
            return DingTalkMsgConst.BIZ_MEETING;
        }
        if (stdType == 20 || stdType == 21 || stdType == 22 || stdType == 23 || stdType == 24
                || stdType == 25 || stdType == 27 || stdType == 33 || stdType == 34 || stdType == 35
                || stdType == 38 || stdType == 118 || stdType == 119 || stdType == 120
                || stdType == 136 || stdType == 151 || stdType == 217) {
            return DingTalkMsgConst.BIZ_WORKFLOW;
        }
        return DingTalkMsgConst.BIZ_CUSTOM;
    }

    /** 业务单号清洗：会议等模块的 targetId 形如 {@code 64|1234}，只取业务ID部分。 */
    private String cleanBizId(String bizId) {
        String value = OaonesUtil.s(bizId).trim();
        int index = value.indexOf('|');
        if (index >= 0 && index < value.length() - 1) {
            return value.substring(index + 1).trim();
        }
        return value;
    }

    // ------------------------------------------------------------------ 通用推送入口

    /** 通用推送：页面测试推送、统一消息接口、指定消息类型直推。 */
    public JSONObject push(MsgPushRequest request) {
        JSONObject result = new JSONObject();
        result.put("pushed", 0);
        result.put("skipped", 0);
        result.put("failed", 0);
        result.put("dedup", 0);
        result.put("logs", new JSONArray());
        try {
            MsgTypeDef def = request.getDef() != null
                    ? request.getDef() : typeService.findEnabledByStdType(request.getStdType());
            if (def == null) {
                result.put("code", -1);
                result.put("msg", "未配置该消息类型的钉钉推送映射（或已停用）");
                return result;
            }
            MsgConfig config = configService.get();
            if (config.getPushEnable() == null || config.getPushEnable() != 1) {
                MsgLog log = newLog(def, request, config, "推送总开关已关闭");
                saveSkip(log, DingTalkMsgConst.LOG_SKIP);
                result.put("skipped", 1);
                result.put("msg", "推送总开关已关闭，已记录拦截日志");
                return result;
            }
            String dedupKey = buildDedupKey(def, request);
            if (!request.isIgnoreDedup() && request.getBizId() != null && request.getBizId().length() > 0
                    && config.getDedupWindowSec() != null && config.getDedupWindowSec() > 0) {
                int windowSec = config.getDedupWindowSec();
                // ① 进程内占位：同一条标准消息被消息中心投递多次时，这里先挡住并发窗口
                //   （只在很短的窗口内有效：失败的推送仍允许被重新投递或定时重试）
                boolean duplicated = !claimOnce(dedupKey, Math.min(windowSec, 120));
                if (!duplicated) {
                    // ② 落库去重：跨进程 / 重启后仍然有效
                    String since = format(new Date(System.currentTimeMillis() - windowSec * 1000L));
                    duplicated = logService.existsDedupKey(dedupKey, since);
                }
                if (duplicated) {
                    MsgLog log = newLog(def, request, config, "相同业务在去重窗口内已推送，自动拦截");
                    log.setDedupKey(dedupKey);
                    saveSkip(log, DingTalkMsgConst.LOG_DEDUP);
                    result.put("dedup", 1);
                    result.put("msg", "重复消息已拦截（去重窗口 " + windowSec + " 秒）");
                    return result;
                }
            }
            List<MsgReceiver> receivers = resolveReceivers(request);
            if (receivers.isEmpty()) {
                MsgLog log = newLog(def, request, config, "没有可推送的有效接收人（未绑定钉钉或已免绑定）");
                saveSkip(log, DingTalkMsgConst.LOG_SKIP);
                result.put("skipped", 1);
                result.put("msg", "没有可推送的有效接收人");
                return result;
            }
            request.setVars(buildBaseVars(request));
            Map<Integer, List<MsgReceiver>> groups = groupByApp(def, receivers);
            for (Map.Entry<Integer, List<MsgReceiver>> entry : groups.entrySet()) {
                MsgApp app = appService.getById(entry.getKey());
                if (app == null) {
                    continue;
                }
                List<MsgReceiver> list = entry.getValue();
                if (DingTalkMsgConst.isCardUpdateAction(def.getCardAction())) {
                    pushCardUpdate(def, app, list, request, config, dedupKey, result);
                } else if (DingTalkMsgConst.DD_TASK_CARD.equals(def.getWxMsgtype())) {
                    pushTaskCards(def, app, list, request, config, dedupKey, result);
                } else {
                    pushShards(def, app, list, request, config, dedupKey, result);
                }
            }
            result.put("code", 0);
            result.put("msg", "推送完成：成功 " + result.getIntValue("pushed")
                    + " 条，失败 " + result.getIntValue("failed")
                    + " 条，跳过 " + result.getIntValue("skipped") + " 条");
        } catch (Throwable t) {
            OaonesLog.error("[msg] 消息推送异常", t);
            result.put("code", -1);
            result.put("msg", t.getMessage() == null ? "推送异常" : t.getMessage());
        }
        return result;
    }

    /** 缺省模板变量（未显式传入时按标准消息或业务参数构造）。 */
    private Map<String, String> buildBaseVars(MsgPushRequest request) {
        Map<String, String> vars;
        if (request.getVars() != null && !request.getVars().isEmpty()) {
            vars = new HashMap<String, String>(request.getVars());
        } else if (request.getStdBean() != null) {
            vars = renderService.variablesFromStdMessage(request.getStdBean(), null);
        } else {
            vars = renderService.variablesFromParams(request.getParams(), null);
        }
        // 兜底：标题缺省取业务标题，时间缺省取当前时间，避免模板渲染出空标题
        if (isBlank(vars.get("title")) && !isBlank(request.getBizTitle())) {
            vars.put("title", request.getBizTitle());
        }
        if (isBlank(vars.get("targetName")) && !isBlank(request.getBizTitle())) {
            vars.put("targetName", request.getBizTitle());
        }
        if (isBlank(vars.get("bizTitle")) && !isBlank(request.getBizTitle())) {
            vars.put("bizTitle", request.getBizTitle());
        }
        if (isBlank(vars.get("bizId")) && !isBlank(request.getBizId())) {
            vars.put("bizId", request.getBizId());
        }
        String now = OaonesUtil.now();
        if (isBlank(vars.get("date"))) {
            vars.put("date", now.substring(0, 10));
        }
        if (isBlank(vars.get("time"))) {
            vars.put("time", now.substring(11));
        }
        return vars;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    /** 接收人解析：OA 人员ID + OA 账号（登录名/工号）+ 钉钉 userid 三种入参。 */
    private List<MsgReceiver> resolveReceivers(MsgPushRequest request) {
        Map<String, MsgReceiver> merged = new LinkedHashMap<String, MsgReceiver>();
        List<MsgReceiver> byOa = receiverService.resolveOaUsers(request.getCfgId(), request.getOaUserIds());
        for (MsgReceiver receiver : byOa) {
            merged.put(receiver.getWxUserid(), receiver);
        }
        List<MsgReceiver> byAccount = receiverService.resolveAccounts(request.getCfgId(), request.getAccounts());
        for (MsgReceiver receiver : byAccount) {
            merged.put(receiver.getWxUserid(), receiver);
        }
        List<MsgReceiver> byWx = receiverService.resolveWxUserids(request.getWxUserids());
        for (MsgReceiver receiver : byWx) {
            merged.put(receiver.getWxUserid(), receiver);
        }
        List<MsgReceiver> receivers = new ArrayList<MsgReceiver>(merged.values());
        receiverService.fillOaInfo(receivers);
        return filterMsgEnabled(receivers);
    }

    /**
     * 过滤掉“消息通知已关闭”的钉钉租户下的接收人。
     *
     * <p>「消息设置 → 租户开关」按租户控制消息推送（复用租户表列 {@code msg_enable}）；
     * 关掉的租户在这里被剔除，推送结果里计入“跳过”，不会外发也不需要人工逐个解绑。</p>
     */
    private List<MsgReceiver> filterMsgEnabled(List<MsgReceiver> receivers) {
        List<MsgReceiver> result = new ArrayList<MsgReceiver>();
        if (receivers == null || receivers.isEmpty()) {
            return result;
        }
        MsgTenantService tenantService = new MsgTenantService();
        Map<String, Boolean> cache = new HashMap<String, Boolean>();
        for (MsgReceiver receiver : receivers) {
            String cfgId = OaonesUtil.nullToEmpty(receiver.getCfgId());
            if (cfgId.length() == 0) {
                result.add(receiver);
                continue;
            }
            Boolean enabled = cache.get(cfgId);
            if (enabled == null) {
                enabled = tenantService.isMsgEnabled(cfgId);
                cache.put(cfgId, enabled);
            }
            if (enabled) {
                result.add(receiver);
            } else {
                OaonesLog.step("[msg] 钉钉租户已关闭消息通知，跳过接收人 cfgId=" + cfgId
                        + " wxUserid=" + receiver.getWxUserid());
            }
        }
        return result;
    }

    /**
     * 按“消息类型的租户明细绑定 -> 消息类型指定应用 -> 接收人所属租户 -> 缺省应用”
     * 把接收人分组到应用。
     *
     * <p>「消息设置 → 消息类型」的“推送目标应用”是按租户逐行设置的明细：
     * 接收人所属租户在该消息类型上绑定过应用时优先用它（见 {@link MsgTypeAppService}），
     * 没绑定时保持原来的回退顺序（消息类型上的应用 → 接收人租户的应用 → 缺省应用）。</p>
     *
     * <p>一批消息内按租户缓存应用解析结果（缺省应用只取一次），
     * 避免大群发时按接收人逐条查库。</p>
     */
    private Map<Integer, List<MsgReceiver>> groupByApp(MsgTypeDef def, List<MsgReceiver> receivers) {
        Map<Integer, List<MsgReceiver>> groups = new LinkedHashMap<Integer, List<MsgReceiver>>();
        MsgApp typeApp = null;
        if (def.getAppId() != null && def.getAppId() > 0) {
            MsgApp candidate = appService.getById(def.getAppId());
            typeApp = appService.isUsable(candidate) ? candidate : null;
        }
        // 消息类型「推送目标应用」明细：按接收人所属租户在该消息类型上绑定的应用（最高优先）
        Map<Integer, Integer> tenantAppIds = new MsgTypeAppService().bindsForPush(
                def.getStdConfigId(), def.getStdType() == null ? 0 : def.getStdType());
        Map<Integer, MsgApp> tenantAppCache = new HashMap<Integer, MsgApp>();
        Map<Integer, MsgApp> cfgAppCache = new HashMap<Integer, MsgApp>();
        MsgApp defaultApp = null;
        boolean defaultLoaded = false;
        for (MsgReceiver receiver : receivers) {
            MsgApp app = null;
            Integer boundAppId = receiver.getCfgId() > 0 ? tenantAppIds.get(Integer.valueOf(receiver.getCfgId())) : null;
            if (boundAppId != null && boundAppId > 0) {
                if (tenantAppCache.containsKey(boundAppId)) {
                    app = tenantAppCache.get(boundAppId);
                } else {
                    MsgApp bound = appService.getById(boundAppId);
                    app = appService.isUsable(bound) ? bound : null;
                    tenantAppCache.put(boundAppId, app);
                }
            }
            if (app == null) {
                app = typeApp;
            }
            if (app == null && receiver.getCfgId() > 0) {
                Integer cfgKey = Integer.valueOf(receiver.getCfgId());
                if (cfgAppCache.containsKey(cfgKey)) {
                    app = cfgAppCache.get(cfgKey);
                } else {
                    app = appService.getByCfgId(receiver.getCfgId());
                    cfgAppCache.put(cfgKey, app);
                }
                if (!appService.isUsable(app)) {
                    app = null;
                }
            }
            if (app == null) {
                if (!defaultLoaded) {
                    defaultApp = appService.defaultApp();
                    defaultLoaded = true;
                }
                app = appService.isUsable(defaultApp) ? defaultApp : null;
            }
            if (app == null || app.getId() == null) {
                OaonesLog.step("[msg] 接收人无可用钉钉应用，已跳过 wxUserid=" + receiver.getWxUserid()
                        + " cfgId=" + receiver.getCfgId());
                continue;
            }
            receiver.setAppId(app.getId());
            List<MsgReceiver> list = groups.get(app.getId());
            if (list == null) {
                list = new ArrayList<MsgReceiver>();
                groups.put(app.getId(), list);
            }
            list.add(receiver);
        }
        return groups;
    }

    // ------------------------------------------------------------------ 三种推送形态

    /**
     * 普通消息（文本/图文/文本卡片/模板卡片）：按分片大小合并接收人，一次接口多人。
     *
     * <p>先按接收人的 <strong>OA 语言</strong>分组再分片：消息体里的 E9 多语言标记
     * （{@code ~`~`7 中文`~`8 English`~`~}）要按每个人自己的语言取值，
     * 同一语言的人仍然合并在一次钉钉接口调用里（语言未知的归到系统默认语言）。</p>
     */
    private void pushShards(MsgTypeDef def, MsgApp app, List<MsgReceiver> receivers, MsgPushRequest request,
                            MsgConfig config, String dedupKey, JSONObject result) {
        int shardSize = config.getShardSize() == null || config.getShardSize() <= 0 ? 1000 : config.getShardSize();
        Map<Integer, List<MsgReceiver>> byLang = groupByLanguage(receivers);
        for (Map.Entry<Integer, List<MsgReceiver>> langEntry : byLang.entrySet()) {
            int lang = langEntry.getKey().intValue();
            List<MsgReceiver> langReceivers = langEntry.getValue();
            int total = (langReceivers.size() + shardSize - 1) / shardSize;
            for (int index = 0; index < total; index++) {
                int from = index * shardSize;
                int to = Math.min(from + shardSize, langReceivers.size());
                List<MsgReceiver> shard = langReceivers.subList(from, to);
                List<String> wxUserids = wxUserids(shard);
                MsgReceiver first = shard.get(0);
                Map<String, String> vars = shard.size() == 1
                        ? receiverVars(request, first) : request.getVars();
                JSONObject body = renderService.buildMessage(def, vars, app, wxUserids, null, lang);
                MsgLog log = newLog(def, request, config, null);
                fillLog(log, app, def, request, shard, dedupKey, index + 1, total);
                sendAndLog(app, body, log, config, result);
            }
        }
    }

    /** 按接收人的 OA 语言分组（语言未知的归到系统默认语言），组内保持原有顺序。 */
    private Map<Integer, List<MsgReceiver>> groupByLanguage(List<MsgReceiver> receivers) {
        Map<Integer, List<MsgReceiver>> groups = new LinkedHashMap<Integer, List<MsgReceiver>>();
        int fallback = renderService.defaultLanguage();
        for (MsgReceiver receiver : receivers) {
            int lang = receiver.getLanguage() > 0 ? receiver.getLanguage() : fallback;
            Integer key = Integer.valueOf(lang);
            List<MsgReceiver> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<MsgReceiver>();
                groups.put(key, list);
            }
            list.add(receiver);
        }
        return groups;
    }

    /**
     * 任务卡片（OA 待办）：一次推送一张卡片，task_id 用<strong>本次推送的业务单号</strong>生成。
     *
     * <p>钉钉对任务卡片有硬约束：<b>发送时 task_id 必须唯一，已存在会被以
     * 42014（taskid has existed）拒收</b>，重复发送同一 task_id 不会更新卡片（更新要走
     * update_taskcard）。所以同一业务重复推送时每次都换一张新卡片：
     * task_id 取带推送流水的业务单号（{@link MsgPushRequest#getBizId()}），
     * 卡片任务表的 biz_id 仍记<strong>稳定业务键</strong>（{@link MsgPushRequest#getBizKey()}），
     * 办结 / 退回 / 失效时按业务键把该接收人的待处理卡片逐张置灰。</p>
     */
    private void pushTaskCards(MsgTypeDef def, MsgApp app, List<MsgReceiver> receivers, MsgPushRequest request,
                               MsgConfig config, String dedupKey, JSONObject result) {
        int index = 0;
        for (MsgReceiver receiver : receivers) {
            index++;
            // task_id 带推送流水：重复推送换新卡片，避免钉钉 42014（taskid has existed）
            String taskId = taskService.buildTaskId(request.getBizType(), request.getBizId(), receiver.getWxUserid());
            taskService.register(request.getBizType(), request.getBizKey(), request.getBizTitle(),
                    app, receiver.getOaUserId(), receiver.getWxUserid(), taskId);
            List<String> wxUserids = new ArrayList<String>();
            wxUserids.add(receiver.getWxUserid());
            JSONObject body = renderService.buildMessage(def, receiverVars(request, receiver),
                    app, wxUserids, taskId, receiver.getLanguage());
            MsgLog log = newLog(def, request, config, null);
            fillLog(log, app, def, request, toList(receiver), dedupKey, index, receivers.size());
            log.setTaskId(taskId);
            sendAndLog(app, body, log, config, result);
        }
    }

    /**
     * 卡片状态更新（办结/驳回/失效）：非卡片类消息直接拦截，不调用更新接口。
     *
     * <p>同一业务允许重复推送（每次一张新卡片），所以这里按业务键取出该接收人的
     * <strong>全部待处理卡片</strong>逐张调用 update_taskcard 置灰，一张卡片一条日志；
     * 卡片创建失败 / 从未推送过时记一条“已跳过”。</p>
     */
    private void pushCardUpdate(MsgTypeDef def, MsgApp app, List<MsgReceiver> receivers, MsgPushRequest request,
                                MsgConfig config, String dedupKey, JSONObject result) {
        // 钉钉侧只有“待办 / 卡片 / 审批通知”这三种形态能更新状态（label 见 DingTalkMsgConst#isCardMsgtype）
        boolean cardMsgtype = DingTalkMsgConst.isCardMsgtype(def.getWxMsgtype());
        if (!cardMsgtype) {
            MsgLog log = newLog(def, request, config, "非卡片类消息禁止调用卡片更新接口，已拦截");
            saveSkip(log, DingTalkMsgConst.LOG_SKIP);
            result.put("skipped", result.getIntValue("skipped") + 1);
            return;
        }
        int index = 0;
        for (MsgReceiver receiver : receivers) {
            index++;
            List<MsgTask> tasks = taskService.listByBizUser(request.getBizType(), request.getBizKey(),
                    receiver.getWxUserid(), DingTalkMsgConst.TASK_PENDING);
            if (tasks.isEmpty()) {
                MsgLog log = newLog(def, request, config, "没有找到该业务的待办卡片（可能未推送过），已跳过");
                fillLog(log, app, def, request, toList(receiver), dedupKey, index, receivers.size());
                saveSkip(log, DingTalkMsgConst.LOG_SKIP);
                result.put("skipped", result.getIntValue("skipped") + 1);
                continue;
            }
            for (MsgTask task : tasks) {
                // task_id：待办形态下是钉钉待办 record_id（推送成功后由本类回写）；
                // 历史口径里可能带钉钉不允许的字符，repairTaskId 会做兼容修正
                String taskId = taskService.repairTaskId(task);
                JSONObject body = new JSONObject();
                JSONArray userids = new JSONArray();
                userids.add(receiver.getWxUserid());
                body.put("userids", userids);
                body.put("agentid", renderService.agentIdValue(app));
                body.put("task_id", taskId);
                body.put("status", workRecordStatusOf(def.getCardAction()));
                body.put("replace_name", cardActionText(def.getCardAction()));
                MsgLog log = newLog(def, request, config, null);
                fillLog(log, app, def, request, toList(receiver), dedupKey, index, receivers.size());
                log.setTaskId(taskId);
                boolean success = sendCardUpdate(app, body, log, config, result);
                if (success) {
                    taskService.updateState(taskId, taskStateOf(def.getCardAction()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 调用与日志

    /** 发送消息并落日志。 */
    private void sendAndLog(MsgApp app, JSONObject body, MsgLog log, MsgConfig config, JSONObject result) {
        // 发送前置校验：文本卡片 / 模板卡片 / 图文消息的跳转地址是钉钉必填项，
        // 且必须是带 http/https 协议头的完整网址（相对地址会被 40127 整条拒收）。
        // 与其发出去必然失败，不如记一条“已跳过”，把原因写清楚
        String urlProblem = cardUrlProblem(body);
        if (urlProblem != null) {
            OaonesLog.step("[msg] " + urlProblem);
            log.setReason(urlProblem);
            saveSkip(log, DingTalkMsgConst.LOG_SKIP);
            result.put("skipped", result.getIntValue("skipped") + 1);
            return;
        }
        long begin = System.currentTimeMillis();
        JSONObject resp = null;
        String errorStack = null;
        // 先清掉上一次的“最近接口日志ID”：本次若没落接口日志（如接口日志表未建），关联列应为空而不是串到上一条
        OaonesApiLog.clearLastId();
        try {
            DingTalkMsgApi api = new DingTalkMsgApi(app.getCorpId(), app.getEffectiveSecret(),
                    config.getTimeoutMs() == null ? 0 : config.getTimeoutMs());
            resp = api.send(body);
            // 待办（task_card）推送成功后，钉钉会返回 record_id：把它回写成卡片任务表的 task_id，
            // 办结/驳回/失效时才能按 record_id 调用 /topapi/workrecord/update 更新那张待办
            syncWorkRecordId(log, resp);
        } catch (Throwable t) {
            errorStack = stack(t);
            OaonesLog.error("[msg] 调用钉钉消息接口异常 agentId=" + app.getAgentId(), t);
        }
        finishLog(log, resp, errorStack, (int) (System.currentTimeMillis() - begin), config, result);
    }

    /**
     * 卡片跳转地址检查。
     *
     * @return 需要拦截时返回给运维看的原因，可直接发送时返回 null
     */
    private String cardUrlProblem(JSONObject body) {
        String msgtype = body == null ? "" : OaonesUtil.s(body.getString("msgtype"));
        String url = "";
        if (DingTalkMsgConst.DD_LINK.equals(msgtype)) {
            JSONObject link = body.getJSONObject("link");
            url = link == null ? "" : OaonesUtil.s(link.getString("messageUrl"));
        } else if (DingTalkMsgConst.DD_ACTION_CARD.equals(msgtype)) {
            JSONObject card = body.getJSONObject("action_card");
            if (card != null) {
                url = OaonesUtil.s(card.getString("single_url"));
                if (url.length() == 0) {
                    JSONArray buttons = card.getJSONArray("btn_json_list");
                    if (buttons != null && !buttons.isEmpty()) {
                        url = OaonesUtil.s(buttons.getJSONObject(0).getString("action_url"));
                    }
                }
            }
        } else if (DingTalkMsgConst.DD_OA.equals(msgtype)) {
            JSONObject oa = body.getJSONObject("oa");
            url = oa == null ? "" : OaonesUtil.s(oa.getString("message_url"));
        } else if (DingTalkMsgConst.DD_TASK_CARD.equals(msgtype)) {
            // 待办：url 是钉钉待办跳转地址，同样要求完整网址，否则钉钉会以 40127 拒收
            JSONObject card = body.getJSONObject("task_card");
            url = card == null ? "" : OaonesUtil.s(card.getString("url"));
        } else {
            // 文本 / markdown：跳转地址写在正文里，缺链接不影响送达
            return null;
        }
        if (url.length() == 0) {
            return "跳转地址为空：钉钉要求 " + DingTalkMsgConst.msgtypeLabel(msgtype) + " 必须有完整网址，"
                    + "请检查消息类型里的跳转地址模板";
        }
        if (!MsgRenderService.isAbsoluteUrl(url)) {
            return "跳转地址不是完整网址（缺少 http/https 协议头）：" + url
                    + "；请在后台 系统设置 → 微信设置 → 集成设置 里维护「OA访问地址」";
        }
        return null;
    }

    /**
     * 待办推送成功后把钉钉返回的 {@code record_id} 回写成卡片任务表的 task_id。
     *
     * <p>推送前登记的 task_id 是本模块生成的“唯一推送流水ID”（用于日志与重复推送区分），
     * 但钉钉待办的更新接口（{@code /topapi/workrecord/update}）只认它自己返回的
     * {@code record_id}，因此成功推送后立刻改写成 record_id；
     * 失败/未返回 record_id 时保持原值，后续重推会重新登记。</p>
     */
    private void syncWorkRecordId(MsgLog log, JSONObject resp) {
        if (log == null || resp == null || resp.getIntValue("errcode") != 0) {
            return;
        }
        JSONObject result = resp.getJSONObject("result");
        String recordId = result == null ? "" : OaonesUtil.s(result.getString("record_id")).trim();
        String oldTaskId = OaonesUtil.s(log.getTaskId()).trim();
        if (recordId.length() == 0 || oldTaskId.length() == 0 || recordId.equals(oldTaskId)) {
            return;
        }
        taskService.renameTaskId(oldTaskId, recordId);
        log.setTaskId(recordId);
        OaonesLog.step("[msg] 待办推送成功，task_id 已回写为钉钉 record_id：" + oldTaskId + " -> " + recordId);
    }

    private boolean sendCardUpdate(MsgApp app, JSONObject body, MsgLog log, MsgConfig config, JSONObject result) {
        long begin = System.currentTimeMillis();
        JSONObject resp = null;
        String errorStack = null;
        OaonesApiLog.clearLastId();
        try {
            DingTalkMsgApi api = new DingTalkMsgApi(app.getCorpId(), app.getEffectiveSecret(),
                    config.getTimeoutMs() == null ? 0 : config.getTimeoutMs());
            resp = api.updateTaskCard(body);
        } catch (Throwable t) {
            errorStack = stack(t);
            OaonesLog.error("[msg] 更新任务卡片异常 agentId=" + app.getAgentId(), t);
        }
        return finishLog(log, resp, errorStack, (int) (System.currentTimeMillis() - begin), config, result);
    }

    /**
     * 统一处理返回码、状态与重试计划，并落“状态表”。
     *
     * <p>口径（接口日志中心方案 B）：本表**不再保存报文与结果**——
     * 请求 / 返回报文、返回码、错误信息、耗时、异常堆栈都在接口日志中心，
     * 这里只把 {@code api_log_id} 关联过去，并写推送状态与重试计划；
     * 常见的错误码仍在本表 {@code reason} 里补一句运维提示（页面上与平台错误信息一起显示）。</p>
     */
    private boolean finishLog(MsgLog log, JSONObject resp, String errorStack, int costMs,
                              MsgConfig config, JSONObject result) {
        boolean success = false;
        int errcode = -999;
        if (resp != null) {
            errcode = resp.getIntValue("errcode");
            success = errcode == 0;
        }
        // 关联本次接口调用在接口日志中心的记录（报文 / 结果 / 耗时都在那边）
        log.setApiLogId(OaonesApiLog.lastId());
        if (success) {
            log.setStatus(DingTalkMsgConst.LOG_SUCCESS);
            result.put("pushed", result.getIntValue("pushed") + 1);
        } else {
            log.setStatus(DingTalkMsgConst.LOG_FAIL);
            int retryTimes = config.getRetryTimes() == null ? 0 : config.getRetryTimes();
            int retryInterval = config.getRetryInterval() == null ? 60 : config.getRetryInterval();
            boolean retryable = errorStack != null || DingTalkMsgConst.isRetryable(errcode);
            if (retryable && retryTimes > 0) {
                log.setNextRetryTime(format(new Date(System.currentTimeMillis() + retryInterval * 1000L)));
            }
            // 常见错误码的运维提示：写在业务原因列（页面上与平台返回信息一起显示）
            String hint = DingTalkMsgConst.errorHint(errcode);
            if (hint.length() > 0) {
                log.setReason(limitReason(hint));
            }
            result.put("failed", result.getIntValue("failed") + 1);
        }
        // 写入时间（modedatacreatedate/createtime）保持不变，重试只更新修改时间
        log.setModedatamodifydatetime(OaonesUtil.now());
        logService.save(log);
        return success;
    }

    /** 业务原因列长度保护（reason varchar(1000)）。 */
    private String limitReason(String text) {
        String value = text == null ? "" : text;
        return value.length() > 900 ? value.substring(0, 900) : value;
    }

    /** errmsg 列长度 1000：拼运维提示时按长度截断，避免写库失败。 */
    private String hintText(String errmsg, String hint) {
        String text = (errmsg == null ? "" : errmsg) + " [" + hint + "]";
        return text.length() > 900 ? text.substring(0, 900) : text;
    }

    /**
     * 重推历史报文时补全相对跳转地址（钉钉要求带 http/https 协议头）。
     *
     * <p>拼不出完整网址时：任务卡片去掉 `url` 字段（钉钉可选，保证卡片能送达），
     * 文本卡片/模板卡片/图文保持原样（发送前置校验会记一条“已跳过”并写明配置项）。</p>
     *
     * @return 报文本是否被改动
     */
    private boolean fixRelativeUrl(JSONObject body) {
        if (body == null) {
            return false;
        }
        String msgtype = OaonesUtil.s(body.getString("msgtype"));
        JSONObject holder = null;
        String field = "url";
        boolean taskCard = "taskcard".equals(msgtype) || "interactive_taskcard".equals(msgtype);
        if (taskCard) {
            holder = body.getJSONObject("taskcard");
            if (holder == null && body.getJSONObject("interactive_taskcard") != null) {
                holder = body.getJSONObject("interactive_taskcard");
            }
        } else if ("textcard".equals(msgtype)) {
            holder = body.getJSONObject("textcard");
        } else if ("template_card".equals(msgtype)) {
            JSONObject card = body.getJSONObject("template_card");
            holder = card == null ? null : card.getJSONObject("card_action");
        } else if ("news".equals(msgtype)) {
            JSONObject news = body.getJSONObject("news");
            JSONArray articles = news == null ? null : news.getJSONArray("articles");
            if (articles != null && !articles.isEmpty()) {
                holder = articles.getJSONObject(0);
            }
        } else {
            return false;
        }
        if (holder == null) {
            return false;
        }
        String url = OaonesUtil.s(holder.getString(field));
        if (url.length() == 0 || MsgRenderService.isAbsoluteUrl(url)) {
            return false;
        }
        String base = renderService.currentOaBase();
        if (base.length() == 0) {
            if (taskCard) {
                holder.remove(field);
                OaonesLog.step("[msg] 重推：跳转地址不是完整网址且未取到 OA 访问地址，任务卡片已去掉链接：" + url);
                return true;
            }
            return false;
        }
        holder.put(field, base + (url.startsWith("/") ? url : "/" + url));
        OaonesLog.step("[msg] 重推：跳转地址已补成完整网址 " + holder.getString(field));
        return true;
    }

    /**
     * 失败重试（定时重试与页面 / 接口手工重推走同一个入口）。
     *
     * <p>推送日志的口径是“一次接口调用一条记录”，所以重推不覆盖原记录：原失败行保留自己的
     * 报文 / 返回码 / 运维提示作为历史（把它的 {@code next_retry_time} 清掉，下一次自动重试
     * 交给本次新增的记录），本次重推单独落一条记录——重推 1 次就是 2 条记录，重推几次就有几条；
     * 成功的那条状态仍是“推送成功”，页面按 {@code retry_count > 0} 把它显示成“重推后成功”。</p>
     */
    public JSONObject retryLog(long logId) {
        MsgLog src = logService.getById(logId);
        JSONObject result = new JSONObject();
        if (src == null) {
            result.put("code", -1);
            result.put("msg", "日志不存在");
            return result;
        }
        // 重推只对“推送失败”有意义：重推后成功 / 重复拦截 / 已跳过 / 待推送都不允许再重推，
        // 与页面上的重推按钮口径一致（按钮同样只对失败记录可用），避免同一条消息被重复发出去
        if (src.getStatus() == null || src.getStatus().intValue() != DingTalkMsgConst.LOG_FAIL) {
            int status = src.getStatus() == null ? 0 : src.getStatus().intValue();
            result.put("code", -1);
            result.put("msg", "这条日志当前状态是“" + DingTalkMsgConst.logStatusText(status)
                    + "”，只有「推送失败」的记录可以重推；重推后成功的记录不再重推");
            return result;
        }
        MsgConfig config = configService.get();
        MsgApp app = appService.getById(src.getAppId() == null ? 0 : src.getAppId());
        if (app == null) {
            result.put("code", -1);
            result.put("msg", "该日志对应的钉钉应用已删除或停用，无法重推");
            return result;
        }
        JSONObject body;
        // 报文不再存在状态表里：按 api_log_id 到接口日志中心取（见 MsgLogService#getById 联表）
        String reqBody = src.getApiReqBody();
        if (reqBody == null || reqBody.trim().length() == 0) {
            // 报文已过期/缺失：必须停止自动重试（清掉 next_retry_time），否则调度器每轮都会再挑出来
            src.setNextRetryTime(null);
            src.setRetryCount((src.getRetryCount() == null ? 0 : src.getRetryCount()) + 1);
            src.setModedatamodifydatetime(OaonesUtil.now());
            logService.updateAfterRetry(src);
            result.put("code", -1);
            result.put("msg", "这条日志没有可重推的接口报文（可能是“已跳过/去重拦截”，或接口日志已按保留天数清理）；已停止自动重试");
            return result;
        }
        try {
            body = JSON.parseObject(reqBody);
        } catch (Exception e) {
            result.put("code", -1);
            result.put("msg", "日志里的请求报文本已损坏，无法重推");
            return result;
        }
        if (body == null) {
            result.put("code", -1);
            result.put("msg", "日志里没有可重推的请求报文");
            return result;
        }
        // 历史报文自愈：failed 记录里的 taskcard.task_id 可能带邮箱等钉钉不允许的字符（老版本口径），
        // 直接重推还是会被 42014 拒收，这里按新口径修正报文本与卡片任务表后再发
        String fixedTaskId = null;
        JSONObject taskcard = body.getJSONObject("taskcard");
        if (taskcard != null) {
            String oldTaskId = taskcard.getString("task_id");
            int lastErrCode = OaonesUtil.i(OaonesUtil.s(src.getApiErrCode()), -999);
            if (lastErrCode == 42014
                    && oldTaskId != null && DingTalkMsgConst.isWxTokenSafe(oldTaskId)) {
                // 上次失败是“taskid has existed/不合法”：原样重推必然再失败，换一张新卡片
                fixedTaskId = taskService.renewTaskId(src.getBizType(), src.getBizId(),
                        body.getString("touser"), oldTaskId);
            } else {
                // 历史 task_id 里可能带邮箱等钉钉不允许的字符（老版本口径）：按新口径改名后再发
                fixedTaskId = taskService.repairTaskId(src.getBizType(), src.getBizId(),
                        body.getString("touser"), oldTaskId);
            }
            if (fixedTaskId != null && !fixedTaskId.equals(oldTaskId)) {
                taskcard.put("task_id", fixedTaskId);
                body.put("taskcard", taskcard);
                OaonesLog.step("[msg] 重推前修正 task_id：" + oldTaskId + " -> " + fixedTaskId
                        + " logId=" + src.getId());
            }
        }
        // 历史报文的跳转地址是相对路径（缺协议头）时按系统设置里「OA访问地址」补全，
        // 否则重推仍会被钉钉以 40127 拒收（维护好 OA访问地址后重推即可成功）
        fixRelativeUrl(body);
        boolean cardUpdate = DingTalkMsgConst.isCardUpdateAction(src.getCardAction());
        JSONObject resp = null;
        String errorStack = null;
        OaonesApiLog.clearLastId();
        try {
            DingTalkMsgApi api = new DingTalkMsgApi(app.getCorpId(), app.getEffectiveSecret(),
                    config.getTimeoutMs() == null ? 0 : config.getTimeoutMs());
            resp = cardUpdate ? api.updateTaskCard(body) : api.send(body);
        } catch (Throwable t) {
            errorStack = stack(t);
        }
        int errcode = resp == null ? -999 : resp.getIntValue("errcode");
        String failMsg = resp == null
                ? (errorStack == null ? "重推失败" : firstLine(errorStack))
                : OaonesUtil.s(resp.getString("errmsg"));
        int retryCount = (src.getRetryCount() == null ? 0 : src.getRetryCount()) + 1;
        // 本次重推单独落一条日志：报文 / 结果 / 耗时照旧只进接口日志中心，本表只存状态与重试计划
        MsgLog log = retryLogOf(src, retryCount, fixedTaskId);
        log.setApiLogId(OaonesApiLog.lastId());
        String hint = errcode == 0 ? "" : DingTalkMsgConst.errorHint(errcode);
        log.setReason(hint.length() == 0 ? null : limitReason(hint));
        if (errcode == 0) {
            log.setStatus(DingTalkMsgConst.LOG_SUCCESS);
            result.put("code", 0);
            result.put("msg", "重推成功，已在推送日志新增一条「重推后成功」记录");
        } else {
            log.setStatus(DingTalkMsgConst.LOG_FAIL);
            int retryTimes = config.getRetryTimes() == null ? 0 : config.getRetryTimes();
            boolean retryable = errorStack != null || DingTalkMsgConst.isRetryable(errcode);
            if (retryable && retryCount < retryTimes) {
                int interval = config.getRetryInterval() == null ? 60 : config.getRetryInterval();
                log.setNextRetryTime(format(new Date(System.currentTimeMillis() + interval * 1000L)));
            }
            result.put("code", -1);
            result.put("msg", "重推失败：" + failMsg);
        }
        logService.save(log);
        // 原失败行：自动重试计划交给新记录（清掉 next_retry_time），状态与运维提示保持历史原样
        src.setNextRetryTime(null);
        src.setModedatamodifydatetime(OaonesUtil.now());
        logService.updateAfterRetry(src);
        return result;
    }

    /**
     * 按原失败记录复制出一条本次重推的日志（未落库）。
     *
     * <p>只带业务信息：业务单号 / 消息类型 / 接收人 / 原始入参 / 去重键 / 卡片任务 / 关联应用；
     * 状态、运维提示、报文与返回码都由本次重推写入（另一条记录）。</p>
     */
    private MsgLog retryLogOf(MsgLog src, int retryCount, String taskId) {
        MsgLog log = new MsgLog();
        log.setBizType(src.getBizType());
        log.setBizId(src.getBizId());
        log.setBizTitle(src.getBizTitle());
        log.setStdType(src.getStdType());
        log.setTypeName(src.getTypeName());
        log.setWxMsgtype(src.getWxMsgtype());
        log.setCardAction(src.getCardAction());
        log.setAppId(src.getAppId());
        log.setCorpId(src.getCorpId());
        log.setAgentId(src.getAgentId());
        log.setTaskId(taskId != null ? taskId : src.getTaskId());
        log.setDedupKey(src.getDedupKey());
        log.setReceiverOa(src.getReceiverOa());
        log.setReceiverWx(src.getReceiverWx());
        log.setReceiverCount(src.getReceiverCount());
        log.setShardIndex(src.getShardIndex());
        log.setShardTotal(src.getShardTotal());
        log.setSrcJson(src.getSrcJson());
        log.setOperatorId(src.getOperatorId());
        log.setRetryCount(retryCount);
        // 记录建模模块ID（与新增日志同一口径），老记录没带时按表名反查
        log.setFormmodeid(src.getFormmodeid() != null ? src.getFormmodeid() : DingTalkMsgProps.logModeId());
        log.setModedatacreatedate(OaonesUtil.nowDate());
        log.setModedatacreatetime(OaonesUtil.nowTime());
        log.setModedatamodifydatetime(OaonesUtil.now());
        return log;
    }

    /** 定时任务批量重试到期失败日志，返回重试条数。 */
    public int retryPending(int limit) {
        MsgConfig config = configService.get();
        int retryTimes = config.getRetryTimes() == null ? 0 : config.getRetryTimes();
        if (retryTimes <= 0) {
            return 0;
        }
        List<MsgLog> logs = logService.listRetryable(OaonesUtil.now(), retryTimes, limit);
        int count = 0;
        for (MsgLog log : logs) {
            retryLog(log.getId());
            count++;
        }
        if (count > 0) {
            OaonesLog.step("[msg] 定时重试完成，本次重试 " + count + " 条");
        }
        return count;
    }

    // ------------------------------------------------------------------ 辅助

    private List<String> wxUserids(List<MsgReceiver> receivers) {
        List<String> list = new ArrayList<String>();
        for (MsgReceiver receiver : receivers) {
            list.add(receiver.getWxUserid());
        }
        return list;
    }

    private List<MsgReceiver> toList(MsgReceiver receiver) {
        List<MsgReceiver> list = new ArrayList<MsgReceiver>();
        list.add(receiver);
        return list;
    }

    private Map<String, String> receiverVars(MsgPushRequest request, MsgReceiver receiver) {
        Map<String, String> vars = new HashMap<String, String>(request.getVars() == null
                ? new HashMap<String, String>() : request.getVars());
        vars.put("receiver", OaonesUtil.s(receiver.getOaName()));
        vars.put("receiverWx", OaonesUtil.s(receiver.getWxUserid()));
        return vars;
    }

    private String buildDedupKey(MsgTypeDef def, MsgPushRequest request) {
        if (request.getBizId() == null || request.getBizId().trim().length() == 0) {
            return null;
        }
        // 标准消息：按“消息ID + 接收人”去重 —— 消息中心会把同一条消息投递多次，
        // 业务单号带推送流水（每次都不同）挡不住这种重复投递；同一流程的再次到达是新消息、新消息ID，照推
        String dedupId = OaonesUtil.s(request.getDedupId()).trim();
        if (dedupId.length() > 0) {
            return cut(dedupId, 250);
        }
        // 类型编号优先取本次请求的（标准消息 / 外部接口都带），配置行上为 0 时（归入标准消息类型的行不冗余来源编号）也不影响幂等键
        int stdCode = def.getStdType() == null || def.getStdType() <= 0
                ? request.getStdType() : def.getStdType();
        return stdCode
                + "|" + OaonesUtil.s(request.getBizType())
                + "|" + request.getBizId().trim();
    }

    /** 进程内近期用过的幂等键（键 -> 上次占位时间），防止同一条消息被并发投递时重复推送。 */
    private static final Map<String, Long> RECENT_DEDUP = new java.util.concurrent.ConcurrentHashMap<String, Long>();

    /**
     * 进程内幂等占位：同一条标准消息被投递多次时，第一条日志可能还没写完（推送要调钉钉接口），
     * 落库去重会漏掉并发的后几条，这里先在内存里占位。
     *
     * @return true = 本次是窗口内第一次（可以继续推送）；false = 重复投递（拦截）
     */
    private static boolean claimOnce(String key, int windowSec) {
        if (key == null || key.length() == 0 || windowSec <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long deadline = now - windowSec * 1000L;
        if (RECENT_DEDUP.size() > 5000) {
            for (Map.Entry<String, Long> item : RECENT_DEDUP.entrySet()) {
                Long at = item.getValue();
                if (at == null || at < deadline) {
                    RECENT_DEDUP.remove(item.getKey());
                }
            }
        }
        Long prev = RECENT_DEDUP.put(key, now);
        return prev == null || prev < deadline;
    }

    private MsgLog newLog(MsgTypeDef def, MsgPushRequest request, MsgConfig config, String skipReason) {
        MsgLog log = new MsgLog();
        log.setBizType(request.getBizType());
        log.setBizId(request.getBizId());
        log.setBizTitle(request.getBizTitle());
        log.setStdType(request.getStdType() == 0 && def.getStdType() != null
                ? def.getStdType() : request.getStdType());
        log.setTypeName(def.getTypeName());
        log.setWxMsgtype(def.getWxMsgtype());
        log.setCardAction(def.getCardAction());
        log.setStatus(DingTalkMsgConst.LOG_SKIP);
        log.setRetryCount(0);
        log.setShardIndex(1);
        log.setShardTotal(1);
        log.setOperatorId(request.getOperatorId());
        log.setSrcJson(request.getSourceJson());
        // 记录建模模块ID（日志表不做权限重构，只记录模块ID，便于在建模引擎里查询日志）
        log.setFormmodeid(DingTalkMsgProps.logModeId());
        // 时间统一用建模系统标准字段：写入日期/时间 + 最近更新时间
        log.setModedatacreatedate(OaonesUtil.nowDate());
        log.setModedatacreatetime(OaonesUtil.nowTime());
        log.setModedatamodifydatetime(OaonesUtil.now());
        if (skipReason != null) {
            log.setReason(skipReason);
        }
        if (config != null && config.getDefaultAppId() != null) {
            log.setAppId(request.getDef() != null && def.getAppId() != null && def.getAppId() > 0
                    ? def.getAppId() : config.getDefaultAppId());
        }
        return log;
    }

    private void fillLog(MsgLog log, MsgApp app, MsgTypeDef def, MsgPushRequest request,
                         List<MsgReceiver> receivers, String dedupKey, int shardIndex, int shardTotal) {
        log.setAppId(app.getId());
        log.setCorpId(app.getCorpId());
        log.setAgentId(app.getAgentId());
        log.setDedupKey(dedupKey);
        log.setShardIndex(shardIndex);
        log.setShardTotal(shardTotal);
        log.setReceiverCount(receivers.size());
        StringBuilder oa = new StringBuilder();
        StringBuilder wx = new StringBuilder();
        for (MsgReceiver receiver : receivers) {
            if (oa.length() > 0) {
                oa.append(',');
            }
            oa.append(receiver.getOaUserId() > 0 ? String.valueOf(receiver.getOaUserId()) : receiver.getWxUserid());
            if (wx.length() > 0) {
                wx.append(',');
            }
            wx.append(receiver.getWxUserid());
        }
        log.setReceiverOa(cut(oa.toString(), 500));
        log.setReceiverWx(cut(wx.toString(), 2000));
        log.setWxMsgtype(def.getWxMsgtype());
        log.setCardAction(def.getCardAction());
        log.setTypeName(def.getTypeName());
        log.setStdType(def.getStdType());
    }

    private void saveSkip(MsgLog log, int status) {
        log.setStatus(status);
        logService.save(log);
    }

    private String cardActionText(String action) {
        if (DingTalkMsgConst.CARD_DONE.equals(action)) {
            return "已处理";
        }
        if (DingTalkMsgConst.CARD_REJECTED.equals(action)) {
            return "已驳回";
        }
        if (DingTalkMsgConst.CARD_INVALID.equals(action)) {
            return "已失效";
        }
        return "已处理";
    }

    private String taskStateOf(String action) {
        if (DingTalkMsgConst.CARD_DONE.equals(action)) {
            return DingTalkMsgConst.TASK_DONE;
        }
        if (DingTalkMsgConst.CARD_REJECTED.equals(action)) {
            return DingTalkMsgConst.TASK_REJECTED;
        }
        return DingTalkMsgConst.TASK_INVALID;
    }

    /**
     * 卡片动作 → 钉钉待办状态码（{@code /topapi/workrecord/update} 的 {@code status}）。
     *
     * <p>口径：办结 1（完成）/ 驳回 2（拒绝）/ 失效 3；如需按企业习惯调整，
     * 改 {@link DingTalkMsgConst} 里的三个常量即可，页面与日志都按同一处取值。</p>
     */
    private int workRecordStatusOf(String action) {
        if (DingTalkMsgConst.CARD_REJECTED.equals(action)) {
            return DingTalkMsgConst.WORKRECORD_STATUS_REJECTED;
        }
        if (DingTalkMsgConst.CARD_INVALID.equals(action)) {
            return DingTalkMsgConst.WORKRECORD_STATUS_INVALID;
        }
        return DingTalkMsgConst.WORKRECORD_STATUS_DONE;
    }

    private String format(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String stack(Throwable t) {
        java.io.StringWriter writer = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int index = text.indexOf('\n');
        String line = index > 0 ? text.substring(0, index) : text;
        return cut(line, 900);
    }

    /**
     * 推送请求（标准消息 / 统一消息接口 / 页面测试推送 共用）。
     */
    public static class MsgPushRequest {
        private MsgTypeDef def;
        private int stdType;
        private MessageBean stdBean;
        private String bizType = DingTalkMsgConst.BIZ_CUSTOM;
        private String bizId;
        private String bizKey;
        private String dedupId;
        private String bizTitle;
        private List<Integer> oaUserIds = new ArrayList<Integer>();
        private List<String> accounts = new ArrayList<String>();
        private List<String> wxUserids = new ArrayList<String>();
        private int cfgId;
        private int operatorId;
        private String sourceJson;
        private Map<String, String> vars;
        private Map<String, Object> params;
        private boolean ignoreDedup;

        public MsgTypeDef getDef() { return def; }
        public void setDef(MsgTypeDef def) { this.def = def; }

        public int getStdType() { return stdType; }
        public void setStdType(int stdType) { this.stdType = stdType; }

        public MessageBean getStdBean() { return stdBean; }
        public void setStdBean(MessageBean stdBean) { this.stdBean = stdBean; }

        public String getBizType() { return bizType; }
        public void setBizType(String bizType) { this.bizType = bizType; }

        public String getBizId() { return bizId; }
        public void setBizId(String bizId) { this.bizId = bizId; }

        /**
         * 稳定业务键（卡片任务绑定用）：业务单号去掉末尾 12 位推送流水（流程即 requestid）。
         *
         * <p>不显式设置时回退用 {@link #getBizId()}，页面测试推送、统一消息接口等
         * 直接传业务单号的入口行为不变。</p>
         */
        public String getBizKey() {
            if (bizKey != null && bizKey.trim().length() > 0) {
                return bizKey.trim();
            }
            return DingTalkMsgConst.baseBizId(bizId);
        }
        public void setBizKey(String bizKey) { this.bizKey = bizKey; }

        /**
         * 幂等去重键（可选）：填了就用它去重，不再用“消息类型 + 业务类型 + 业务单号”。
         *
         * <p>标准消息入口填的是“消息ID + 接收人指纹”：业务单号带推送流水，挡不住
         * 同一条消息被消息中心投递多次；同一流程再次到达是新消息、新消息ID，照常推送。</p>
         */
        public String getDedupId() { return dedupId; }
        public void setDedupId(String dedupId) { this.dedupId = dedupId; }

        public String getBizTitle() { return bizTitle; }
        public void setBizTitle(String bizTitle) { this.bizTitle = bizTitle; }

        public List<Integer> getOaUserIds() { return oaUserIds; }
        public void setOaUserIds(List<Integer> oaUserIds) { this.oaUserIds = oaUserIds; }

        public List<String> getAccounts() { return accounts; }
        public void setAccounts(List<String> accounts) { this.accounts = accounts; }

        public List<String> getWxUserids() { return wxUserids; }
        public void setWxUserids(List<String> wxUserids) { this.wxUserids = wxUserids; }

        public int getCfgId() { return cfgId; }
        public void setCfgId(int cfgId) { this.cfgId = cfgId; }

        public int getOperatorId() { return operatorId; }
        public void setOperatorId(int operatorId) { this.operatorId = operatorId; }

        public String getSourceJson() { return sourceJson; }
        public void setSourceJson(String sourceJson) { this.sourceJson = sourceJson; }

        public Map<String, String> getVars() { return vars; }
        public void setVars(Map<String, String> vars) { this.vars = vars; }

        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }

        public boolean isIgnoreDedup() { return ignoreDedup; }
        public void setIgnoreDedup(boolean ignoreDedup) { this.ignoreDedup = ignoreDedup; }
    }
}
