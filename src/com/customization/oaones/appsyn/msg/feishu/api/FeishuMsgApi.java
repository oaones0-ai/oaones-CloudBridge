package com.customization.oaones.appsyn.msg.feishu.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesApiLog;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesProps;
import com.customization.oaones.appsyn.msg.feishu.common.FeishuMsgConst;
import com.customization.oaones.appsyn.msg.feishu.common.FeishuMsgProps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书「消息 + 待办」开放接口封装（本模块自研推送专用）。
 *
 * <ul>
 *   <li>tenant_access_token 按 App ID + App Secret 缓存在内存，有效期取 {@code expire}
 *       并提前 {@code msg.token.advance.sec} 秒失效，避免边界失效；</li>
 *   <li>token 失效（{@code 999916xx}）时强制刷新并自动重试一次，无需人工干预；</li>
 *   <li>普通消息走 {@code POST /open-apis/im/v1/messages}（msg_type = text / post / interactive）；
 *       <b>飞书一次只能发给一个接收人</b>，因此本类按接收人逐个调用并把结果合并成一个
 *       与企微/钉钉同口径的返回体（{@code errcode / errmsg}），上层推送引擎不必区分平台；</li>
 *   <li>待办（task_card）走飞书任务接口 {@code /open-apis/task/v2/tasks}：
 *       先建任务、再把接收人加成任务成员（可选挂到配置的任务清单），
 *       推送成功后把飞书返回的 {@code task.guid} 回写成卡片任务表的 task_id，
 *       办结 / 驳回 / 失效时按它更新或删除该待办。</li>
 * </ul>
 */
public class FeishuMsgApi {

    /** token 缓存：key = appId|appSecret。 */
    private static final Map<String, TokenHolder> TOKEN_CACHE = new ConcurrentHashMap<String, TokenHolder>();
    /** 接收人标识前缀：飞书的 open_id 形如 ou_xxx，其余按 user_id 处理。 */
    private static final String OPEN_ID_PREFIX = "ou_";
    /** 飞书发送消息时 content 的兜底长度上限（字节），超出按上限截断并记日志。 */
    private static final int CONTENT_MAX_BYTES = 30 * 1024;

    private final String appId;
    private final String secret;
    private final int timeoutMs;

    public FeishuMsgApi(String appId, String secret, int timeoutMs) {
        this.appId = appId == null ? "" : appId.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : FeishuMsgProps.timeout();
    }

    public String getCorpId() {
        return appId;
    }

    /** 获取 tenant_access_token（命中缓存直接返回）。 */
    public String accessToken() throws Exception {
        return accessToken(false);
    }

    /**
     * 获取 tenant_access_token。
     *
     * @param force 是否强制刷新（token 失效重试用）
     */
    public String accessToken(boolean force) throws Exception {
        String key = appId + "|" + secret;
        long now = System.currentTimeMillis();
        TokenHolder holder = TOKEN_CACHE.get(key);
        if (!force && holder != null && now < holder.expireAt) {
            return holder.token;
        }
        synchronized (TOKEN_CACHE) {
            holder = TOKEN_CACHE.get(key);
            if (!force && holder != null && now < holder.expireAt) {
                return holder.token;
            }
            JSONObject req = new JSONObject();
            req.put("app_id", appId);
            req.put("app_secret", secret);
            JSONObject resp = requestJson("POST",
                    OaonesProps.fsApiBase() + "/open-apis/auth/v3/tenant_access_token/internal", req, null);
            String token = resp.getString("tenant_access_token");
            if (token == null || token.length() == 0) {
                throw new RuntimeException("获取飞书 tenant_access_token 失败: " + resp.toJSONString());
            }
            int expire = resp.getIntValue("expire");
            if (expire <= 0) {
                expire = 7200;
            }
            int advance = FeishuMsgProps.tokenAdvanceSec();
            long ttl = (expire - advance) * 1000L;
            if (ttl < 60 * 1000L) {
                ttl = 60 * 1000L;
            }
            TokenHolder next = new TokenHolder();
            next.token = token;
            next.expireAt = now + ttl;
            next.refreshTime = now;
            TOKEN_CACHE.put(key, next);
            OaonesLog.step("[msg] 刷新飞书 tenant_access_token appId=" + appId
                    + " expire=" + expire + " 提前刷新秒数=" + advance);
            return token;
        }
    }

    /** token 缓存信息（页面“连通测试”展示用）。 */
    public JSONObject tokenInfo() {
        String key = appId + "|" + secret;
        TokenHolder holder = TOKEN_CACHE.get(key);
        JSONObject obj = new JSONObject();
        obj.put("cached", holder != null);
        if (holder != null) {
            obj.put("expireInSec", Math.max(0, (holder.expireAt - System.currentTimeMillis()) / 1000));
            obj.put("refreshTime", holder.refreshTime);
        }
        return obj;
    }

    /**
     * 发送消息（{@code POST /open-apis/im/v1/messages}）。
     *
     * <p>入参是渲染层统一口径的消息体（{@code touser / agentid / msgtype / 各形态正文段}），
     * 这里翻译成飞书入参：接收人取 {@code touser} 里的成员ID（逗号分隔），
     * 内容取 {@code text / post / interactive} 中与 {@code msgtype} 同名的那一段。
     * {@code task_card}（待办）走 {@link #addTask(JSONObject)}。</p>
     *
     * <p>返回体保持企微/钉钉口径（{@code {"errcode":0,"errmsg":"ok",...}}）：
     * 全部接收人都成功才算成功；有失败时返回第一个失败的错误码与提示，
     * 明细放在 {@code data.failed} 里，日志页可直接看到是哪几个接收人失败。</p>
     */
    public JSONObject send(JSONObject body) throws Exception {
        if (FeishuMsgConst.FS_TASK_CARD.equals(txt(body.getString("msgtype")))) {
            return addTask(body);
        }
        String msgtype = txt(body.getString("msgtype"));
        JSONArray receiveIds = receivers(body.getString("touser"));
        if (receiveIds.isEmpty()) {
            return error(230001, "没有可用的飞书接收人（接收人未绑定飞书成员）");
        }
        JSONObject content = buildContent(msgtype, body);
        String contentText = content.toJSONString();
        if (contentText.getBytes("UTF-8").length > CONTENT_MAX_BYTES) {
            OaonesLog.step("[msg] 飞书消息内容超过 " + CONTENT_MAX_BYTES + " 字节，按上限截断 msgtype=" + msgtype);
            contentText = contentText.substring(0, Math.min(contentText.length(), CONTENT_MAX_BYTES));
        }
        JSONArray messageIds = new JSONArray();
        JSONArray failed = new JSONArray();
        int firstErrorCode = 0;
        String firstErrorMsg = "";
        for (int i = 0; i < receiveIds.size(); i++) {
            String receiveId = receiveIds.getString(i);
            JSONObject payload = new JSONObject();
            payload.put("receive_id", receiveId);
            payload.put("msg_type", msgtype);
            payload.put("content", contentText);
            JSONObject resp = call("/open-apis/im/v1/messages?receive_id_type=" + receiveIdType(receiveId),
                    payload, "POST");
            if (resp.getIntValue("code") == 0) {
                JSONObject data = resp.getJSONObject("data");
                String messageId = data == null ? "" : txt(data.getString("message_id"));
                if (messageId.length() > 0) {
                    messageIds.add(messageId);
                }
            } else {
                if (firstErrorCode == 0) {
                    firstErrorCode = resp.getIntValue("code");
                    firstErrorMsg = txt(resp.getString("msg"));
                }
                JSONObject row = new JSONObject();
                row.put("receive_id", receiveId);
                row.put("code", resp.getIntValue("code"));
                row.put("msg", txt(resp.getString("msg")));
                failed.add(row);
                OaonesLog.step("[msg] 飞书消息发送失败 receiveId=" + receiveId
                        + " code=" + resp.getIntValue("code") + " msg=" + txt(resp.getString("msg")));
            }
        }
        JSONObject result = new JSONObject();
        if (failed.isEmpty()) {
            result.put("errcode", 0);
            result.put("errmsg", "ok");
        } else {
            result.put("errcode", firstErrorCode == 0 ? -1 : firstErrorCode);
            result.put("errmsg", firstErrorMsg.length() == 0 ? "部分接收人发送失败" : firstErrorMsg);
        }
        JSONObject data = new JSONObject();
        data.put("sent", messageIds.size());
        data.put("failed", failed);
        data.put("message_ids", messageIds);
        result.put("data", data);
        return result;
    }

    /**
     * 按消息形态组装飞书 {@code content}（发送时序列化成字符串）。
     *
     * <ul>
     *   <li>text：{@code {"text":"..."}}；</li>
     *   <li>post：{@code {"post":{"zh_cn":{"title":"...","content":[[{tag,text}]]}}}；</li>
     *   <li>interactive：消息卡片 JSON 本身（{@code header + elements}）。</li>
     * </ul>
     */
    private JSONObject buildContent(String msgtype, JSONObject body) {
        if (FeishuMsgConst.FS_POST.equals(msgtype)) {
            JSONObject post = body.getJSONObject("post");
            JSONObject lang = new JSONObject();
            lang.put("title", post == null ? "" : txt(post.getString("title")));
            JSONArray elements = post == null ? null : post.getJSONArray("elements");
            lang.put("content", elements == null ? new JSONArray() : elements);
            JSONObject inner = new JSONObject();
            inner.put("zh_cn", lang);
            JSONObject content = new JSONObject();
            content.put("post", inner);
            return content;
        }
        if (FeishuMsgConst.FS_INTERACTIVE.equals(msgtype)) {
            JSONObject card = body.getJSONObject("interactive");
            return card == null ? new JSONObject() : card;
        }
        JSONObject text = body.getJSONObject("text");
        JSONObject content = new JSONObject();
        content.put("text", text == null ? "" : txt(text.getString("content")));
        return content;
    }

    /**
     * 新增飞书待办（任务）。
     *
     * <p>调用链：</p>
     * <ol>
     *   <li>{@code POST /open-apis/task/v2/tasks} 建任务（summary + description，
     *       description 里带业务正文与跳转链接）；</li>
     *   <li>{@code POST /open-apis/task/v2/tasks/{task_guid}/add_members} 把接收人加成任务成员
     *       （{@code assignee}），接收人才会在自己的飞书“任务”里看到这条待办；</li>
     *   <li>配置了 {@code fs.task.tasklist.guid} 时再挂到该任务清单下（可选）。</li>
     * </ol>
     *
     * <p>返回体里带 {@code record_id} 与 {@code result.record_id}（= 飞书 task guid），
     * 与企微/钉钉的返回口径一致，上层推送引擎据此回写成卡片任务表的 task_id。</p>
     */
    public JSONObject addTask(JSONObject body) throws Exception {
        JSONObject card = body.getJSONObject("task_card");
        String userId = firstReceiver(body.getString("touser"));
        if (userId.length() == 0) {
            return error(230001, "待办缺少接收人（接收人未绑定飞书成员）");
        }
        String summary = card == null ? "" : txt(card.getString("summary"));
        if (summary.length() == 0) {
            summary = card == null ? "" : txt(card.getString("title"));
        }
        String description = card == null ? "" : txt(card.getString("description"));
        String sourceName = card == null || txt(card.getString("source_name")).length() == 0
                ? "OA" : txt(card.getString("source_name"));
        JSONObject origin = new JSONObject();
        JSONObject platformName = new JSONObject();
        platformName.put("zh_cn", sourceName);
        platformName.put("en_us", sourceName);
        origin.put("platform_i18n_name", platformName);
        JSONObject payload = new JSONObject();
        payload.put("summary", summary);
        payload.put("description", description);
        payload.put("origin", origin);
        JSONObject resp = call("/open-apis/task/v2/tasks?user_id_type=" + receiveIdType(userId),
                payload, "POST");
        if (resp.getIntValue("code") != 0) {
            return platformError(resp);
        }
        JSONObject data = resp.getJSONObject("data");
        JSONObject task = data == null ? null : data.getJSONObject("task");
        String guid = task == null ? "" : txt(task.getString("guid"));
        if (guid.length() == 0) {
            return error(-1, "飞书创建待办失败：返回体没有 task.guid, resp=" + resp.toJSONString());
        }
        // 接收人加成任务成员（失败不整条回滚，但要能在日志里看到）
        addTaskMember(guid, userId);
        attachTaskList(guid);
        JSONObject result = new JSONObject();
        result.put("errcode", 0);
        result.put("errmsg", "ok");
        result.put("record_id", guid);
        JSONObject inner = new JSONObject();
        inner.put("record_id", guid);
        inner.put("task_guid", guid);
        result.put("result", inner);
        JSONObject extra = new JSONObject();
        extra.put("task_guid", guid);
        extra.put("source_name", sourceName);
        if (description.length() > 0) {
            extra.put("task_desc", description);
        }
        result.put("data", extra);
        return result;
    }

    /** 把接收人加成任务成员。 */
    private void addTaskMember(String taskGuid, String userId) {
        try {
            JSONArray members = new JSONArray();
            JSONObject member = new JSONObject();
            member.put("id", userId);
            member.put("type", "user");
            member.put("role", "assignee");
            members.add(member);
            JSONObject payload = new JSONObject();
            payload.put("members", members);
            JSONObject resp = call("/open-apis/task/v2/tasks/" + urlEncode(taskGuid)
                    + "/add_members?user_id_type=" + receiveIdType(userId), payload, "POST");
            if (resp.getIntValue("code") != 0) {
                OaonesLog.step("[msg] 飞书待办添加成员失败 taskGuid=" + taskGuid + " userId=" + userId
                        + " code=" + resp.getIntValue("code") + " msg=" + txt(resp.getString("msg")));
            }
        } catch (Exception e) {
            OaonesLog.step("[msg] 飞书待办添加成员异常 taskGuid=" + taskGuid + "：" + e.getMessage());
        }
    }

    /** 把任务挂到配置的任务清单下（可选；未配置清单时跳过）。 */
    private void attachTaskList(String taskGuid) {
        String taskListGuid = "";
        try {
            taskListGuid = txt(OaonesProps.fsTaskListGuid());
        } catch (Throwable t) {
            taskListGuid = "";
        }
        if (taskListGuid.length() == 0) {
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("task_guid", taskGuid);
            JSONObject resp = call("/open-apis/task/v2/tasklists/" + urlEncode(taskListGuid) + "/tasks",
                    payload, "POST");
            if (resp.getIntValue("code") != 0) {
                OaonesLog.step("[msg] 飞书待办挂任务清单失败 taskGuid=" + taskGuid
                        + " taskList=" + taskListGuid
                        + " code=" + resp.getIntValue("code") + " msg=" + txt(resp.getString("msg")));
            }
        } catch (Exception e) {
            OaonesLog.step("[msg] 飞书待办挂任务清单异常：" + e.getMessage());
        }
    }

    /**
     * 更新待办状态（办结 / 驳回 / 失效）。
     *
     * <p>入参沿用渲染层的卡片更新口径：{@code userids} / {@code task_id}（飞书 task guid）/
     * {@code status}（{@link FeishuMsgConst#TASK_UPDATE_DONE} 等）/ {@code replace_name}（仅日志）。</p>
     *
     * <ul>
     *   <li>办结 / 驳回：{@code PATCH /open-apis/task/v2/tasks/{guid}?update_fields=completed_at}
     *       （飞书任务只有“未完成 / 已完成”两态，驳回也按已完成收口，日志里写清文案）；</li>
     *   <li>失效：{@code DELETE /open-apis/task/v2/tasks/{guid}} 删除该待办（从接收人的任务列表里收起）。</li>
     * </ul>
     */
    public JSONObject updateTaskCard(JSONObject body) throws Exception {
        String taskId = txt(body.getString("task_id"));
        if (taskId.length() == 0) {
            taskId = txt(body.getString("record_id"));
        }
        if (taskId.length() == 0) {
            return error(1061004, "待办更新缺少 task_id（该记录没有推送成功的待办）");
        }
        int status = body.getIntValue("status");
        if (status <= 0) {
            status = FeishuMsgConst.TASK_UPDATE_DONE;
        }
        String path = "/open-apis/task/v2/tasks/" + urlEncode(taskId);
        if (status == FeishuMsgConst.TASK_UPDATE_INVALID) {
            JSONObject resp = call(path, null, "DELETE");
            return platformResult(resp, "已删除待办（失效）：" + taskId);
        }
        JSONObject payload = new JSONObject();
        payload.put("completed_at", String.valueOf(System.currentTimeMillis()));
        JSONObject resp = call(path + "?update_fields=completed_at", payload, "PATCH");
        String action = status == FeishuMsgConst.TASK_UPDATE_REJECTED ? "驳回" : "办结";
        String replaceName = txt(body.getString("replace_name"));
        return platformResult(resp, "已把待办更新为" + action + "：taskId=" + taskId
                + (replaceName.length() == 0 ? "" : "（" + replaceName + "）"));
    }

    /** 接收人列表：{@code touser} 逗号分隔，去重去空。 */
    private JSONArray receivers(String touser) {
        JSONArray out = new JSONArray();
        String text = txt(touser);
        if (text.length() == 0) {
            return out;
        }
        String[] parts = text.split(",");
        for (int i = 0; i < parts.length; i++) {
            String id = parts[i] == null ? "" : parts[i].trim();
            if (id.length() > 0 && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 取第一个接收人（飞书待办按单人创建）。 */
    private String firstReceiver(String value) {
        JSONArray list = receivers(value);
        return list.isEmpty() ? "" : txt(list.getString(0));
    }

    /** 接收人ID类型：open_id 还是 user_id（飞书的 IM 与任务接口都按它取人）。 */
    private String receiveIdType(String userId) {
        return txt(userId).startsWith(OPEN_ID_PREFIX) ? "open_id" : "user_id";
    }

    private String txt(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 代调用：自动带 tenant_access_token；token 失效自动刷新并重试一次。
     *
     * @param method GET / POST / PATCH / DELETE
     */
    public JSONObject call(String path, JSONObject body, String method) throws Exception {
        JSONObject resp = request(path, body, accessToken(false), method);
        int code = resp.getIntValue("code");
        if (FeishuMsgConst.isTokenError(code)) {
            OaonesLog.step("[msg] 飞书 tenant_access_token 失效 code=" + code + "，刷新后重试 path=" + path);
            resp = request(path, body, accessToken(true), method);
        }
        return resp;
    }

    private JSONObject request(String path, JSONObject body, String token, String method) throws Exception {
        String url = path.startsWith("http") ? path : OaonesProps.fsApiBase() + path;
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + token);
        return requestJson(method == null ? "POST" : method, url, body, headers);
    }

    /** 飞书原始返回体 -> 本模块统一口径（errcode/errmsg）。 */
    private JSONObject platformResult(JSONObject resp, String successMsg) {
        if (resp == null) {
            return error(-1, "飞书接口无响应");
        }
        JSONObject result = new JSONObject();
        int code = resp.getIntValue("code");
        result.put("errcode", code);
        result.put("errmsg", code == 0
                ? (successMsg == null ? "ok" : successMsg)
                : txt(resp.getString("msg")));
        result.put("data", resp.getJSONObject("data"));
        return result;
    }

    private JSONObject platformError(JSONObject resp) {
        if (resp == null) {
            return error(-1, "飞书接口无响应");
        }
        return error(resp.getIntValue("code"), txt(resp.getString("msg")));
    }

    private JSONObject error(int code, String msg) {
        JSONObject result = new JSONObject();
        result.put("errcode", code);
        result.put("errmsg", msg == null ? "" : msg);
        return result;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private JSONObject requestJson(String method, String url, JSONObject body,
                                   Map<String, String> headers) throws Exception {
        HttpURLConnection conn = null;
        long start = System.currentTimeMillis();
        String reqText = body == null ? "" : body.toJSONString();
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            conn.setDoInput(true);
            if (body != null) {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(body.toJSONString().getBytes("UTF-8"));
                    os.flush();
                } finally {
                    os.close();
                }
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = readAll(in);
            OaonesApiLog.out(OaonesConst.TYPE_FEISHU, appId, method, url, reqText, text, code,
                    System.currentTimeMillis() - start, null);
            JSONObject resp = JSON.parseObject(text);
            return resp == null ? new JSONObject() : resp;
        } catch (Exception e) {
            OaonesApiLog.out(OaonesConst.TYPE_FEISHU, appId, method, url, reqText, "", 0,
                    System.currentTimeMillis() - start, OaonesApiLog.stackTrace(e));
            throw e;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "{}";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    /** 清除指定应用/企业的 token 缓存（修改 App Secret 后调用，保证配置即时生效）。 */
    public static void clearToken(String corpId, String secret) {
        if (corpId == null || secret == null) {
            return;
        }
        TOKEN_CACHE.remove(corpId.trim() + "|" + secret.trim());
    }

    /** 清空全部 token 缓存（推送配置保存后调用）。 */
    public static void clearAll() {
        TOKEN_CACHE.clear();
    }

    private static class TokenHolder {
        private String token;
        private long expireAt;
        private long refreshTime;
    }
}
