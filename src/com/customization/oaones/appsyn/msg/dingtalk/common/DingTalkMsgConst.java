package com.customization.oaones.appsyn.msg.dingtalk.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 消息中心（OA -> 钉钉推送）常量。
 *
 * <p>开关口径与 uf_oaones_appsyn_app_bind.yx 保持一致：
 * {@code 0 = 启用 / 1 = 停用}；状态口径集中在 {@link DingTalkMsgConst} 一处，页面与后端都按此判断。</p>
 */
public final class DingTalkMsgConst {

    /** 启用（各配置表 yx 列）。 */
    public static final int STATE_ENABLE = 0;
    /** 停用（各配置表 yx 列）。 */
    public static final int STATE_DISABLE = 1;

    /** 记录状态标记（废弃/删除用）。 */
    public static final String YES = "1";
    public static final String NO = "0";

    /** 钉钉消息形态：文本（工作通知 text）。 */
    public static final String DD_TEXT = "text";
    /** 钉钉消息形态：链接卡片（工作通知 link：标题 + 摘要 + 跳转地址 + 封面）。 */
    public static final String DD_LINK = "link";
    /** 钉钉消息形态：markdown（工作通知 markdown：支持粗体/链接等富文本）。 */
    public static final String DD_MARKDOWN = "markdown";
    /** 钉钉消息形态：卡片（工作通知 action_card：标题 + markdown 正文 + 按钮）。 */
    public static final String DD_ACTION_CARD = "action_card";
    /** 钉钉消息形态：审批式通知（工作通知 oa：头部色条 + 表单字段，适合单据类提醒）。 */
    public static final String DD_OA = "oa";
    /**
     * 钉钉消息形态：待办（OA 待办事项，走钉钉待办接口 /topapi/workrecord/add）。
     *
     * <p>与工作通知不同：待办会进入钉钉“待办”列表，并支持二次更新状态
     * （完成 / 拒绝 / 失效，见 {@code DingTalkMsgApi#updateTaskCard}），
     * 因此“流程待办提醒”这类办结后需要置灰的消息用本形态。</p>
     */
    public static final String DD_TASK_CARD = "task_card";

    /** 全部钉钉消息形态（用于页面下拉、保存校验与日志展示）。 */
    public static final String[] DD_MSGTYPES = {
            DD_TEXT, DD_LINK, DD_MARKDOWN, DD_ACTION_CARD, DD_OA, DD_TASK_CARD
    };

    /** 消息形态文案（页面下拉与日志展示用）。 */
    public static String msgtypeLabel(String msgtype) {
        String value = msgtype == null ? "" : msgtype.trim();
        if (DD_LINK.equals(value)) {
            return "链接卡片（link）";
        }
        if (DD_MARKDOWN.equals(value)) {
            return "富文本（markdown）";
        }
        if (DD_ACTION_CARD.equals(value)) {
            return "卡片（action_card）";
        }
        if (DD_OA.equals(value)) {
            return "审批通知（oa）";
        }
        if (DD_TASK_CARD.equals(value)) {
            return "待办（task_card）";
        }
        return "文本（text）";
    }

    /** 是否为卡片类形态（只有这些形态才能调用“卡片状态更新”）。 */
    public static boolean isCardMsgtype(String msgtype) {
        String value = msgtype == null ? "" : msgtype.trim();
        return DD_TASK_CARD.equals(value) || DD_ACTION_CARD.equals(value) || DD_OA.equals(value);
    }

    /** 钉钉待办 update 接口状态码：完成。 */
    public static final int WORKRECORD_STATUS_DONE = 1;
    /** 钉钉待办 update 接口状态码：拒绝。 */
    public static final int WORKRECORD_STATUS_REJECTED = 2;
    /** 钉钉待办 update 接口状态码：失效。 */
    public static final int WORKRECORD_STATUS_INVALID = 3;

    /**
     * 泛微标准消息类型名称的语言ID：简体中文（HtmlLabelInfo.languageid）。
     *
     * <p>“消息类型映射”页面的下拉、日志与列表里的标准类型名称都按这条口径取名，
     * 取不到再回落 {@code ECOLOGY_MESSAGE_TYPE.typename} 与内置清单。</p>
     */
    public static final int LANG_ZH_CN = 7;

    /** 卡片动作：不处理卡片（普通消息）。 */
    public static final String CARD_NONE = "none";
    /** 卡片动作：新建任务卡片（待办）。 */
    public static final String CARD_CREATE = "create";
    /** 卡片动作：把既有卡片更新为“已处理”。 */
    public static final String CARD_DONE = "done";
    /** 卡片动作：把既有卡片更新为“已驳回”。 */
    public static final String CARD_REJECTED = "rejected";
    /** 卡片动作：把既有卡片更新为“已失效”（置灰、不可再点）。 */
    public static final String CARD_INVALID = "invalid";

    /** 卡片动作是否为“更新既有卡片”。 */
    public static boolean isCardUpdateAction(String action) {
        return CARD_DONE.equals(action) || CARD_REJECTED.equals(action) || CARD_INVALID.equals(action);
    }

    /** 卡片任务状态：待处理。 */
    public static final String TASK_PENDING = "pending";
    /** 卡片任务状态：已处理。 */
    public static final String TASK_DONE = "done";
    /** 卡片任务状态：已驳回。 */
    public static final String TASK_REJECTED = "rejected";
    /** 卡片任务状态：已失效。 */
    public static final String TASK_INVALID = "invalid";

    /** 日志状态：待推送（异步/待重试）。 */
    public static final int LOG_WAIT = 0;
    /** 日志状态：推送成功。 */
    public static final int LOG_SUCCESS = 1;
    /** 日志状态：推送失败。 */
    public static final int LOG_FAIL = 2;
    /** 日志状态：被幂等去重拦截。 */
    public static final int LOG_DEDUP = 3;
    /** 日志状态：跳过（未配置规则、无有效接收人、总开关关闭等）。 */
    public static final int LOG_SKIP = 4;

    /** 日志状态文案。 */
    public static String logStatusText(int status) {
        if (status == LOG_SUCCESS) {
            return "推送成功";
        }
        if (status == LOG_FAIL) {
            return "推送失败";
        }
        if (status == LOG_DEDUP) {
            return "重复拦截";
        }
        if (status == LOG_SKIP) {
            return "已跳过";
        }
        return "待推送";
    }

    /** 业务来源：流程。 */
    public static final String BIZ_WORKFLOW = "workflow";
    /** 业务来源：会议。 */
    public static final String BIZ_MEETING = "meeting";
    /** 业务来源：外部系统调用统一消息接口。 */
    public static final String BIZ_CUSTOM = "custom";

    // ------------------------------------------------------------------ 业务单号

    /**
     * 推送流水长度：12 位（去横线 uuid 前 12 位，十六进制小写）。
     *
     * <p>标准消息的业务单号 = {@code 业务ID + "-" + 12位uuid}（流程即 {@code requestid-12位uuid}）：
     * 同一业务会因为重提、加签、转办等原因需要<strong>重复推送</strong>，业务单号带流水后
     * 幂等去重窗口（键 = 消息类型 + 业务类型 + 业务单号）不会把后续推送当成重复消息拦掉；
     * 推送日志页仍可按业务ID 模糊检索（Mapper 用 {@code biz_id like '%单号%'}）。</p>
     */
    public static final int BIZ_SEQ_LEN = 12;

    /**
     * 生成 12 位推送流水。
     *
     * <p>取去横线 uuid 的前 12 位（十六进制小写），字符集满足钉钉卡片
     * {@code task_id} / 按钮 key 的限制，可直接进 URL 与日志检索条件。</p>
     */
    public static String bizSeq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, BIZ_SEQ_LEN);
    }

    /**
     * 业务单号追加推送流水：{@code requestid} -> {@code requestid-<12位uuid>}。
     *
     * <p>标准消息（流程 / 会议 / 其它）统一走这里；业务键为空（标准消息没带 targetId）时
     * 原样返回空串，不生成 {@code -uuid} 这种无意义单号。</p>
     */
    public static String withBizSeq(String bizId) {
        String base = bizId == null ? "" : bizId.trim();
        if (base.length() == 0) {
            return "";
        }
        return base + "-" + bizSeq();
    }

    /**
     * 去掉业务单号末尾的推送流水，得到稳定业务键（流程即 requestid，会议即会议ID）。
     *
     * <p>卡片任务表按稳定业务键绑定：同一待办对同一接收人只有一张卡片，
     * 办结 / 退回 / 收回的标准消息才能按它找到并置灰那张卡片（会议等业务同理）；
     * 推送日志仍按带流水的业务单号记录（日志页展示与检索的都是带流水的业务单号）。</p>
     */
    public static String baseBizId(String bizId) {
        String value = bizId == null ? "" : bizId.trim();
        int index = value.length() - BIZ_SEQ_LEN - 1;
        if (index <= 0 || value.charAt(index) != '-') {
            return value;
        }
        for (int i = index + 1; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return value;
            }
        }
        return value.substring(0, index);
    }

    /**
     * 可重试的钉钉错误码：网络抖动、临时限流、token 失效等可恢复异常自动重试；
     * 用户不存在、无权限、参数非法等业务异常不重试。
     */
    private static final Set<Integer> RETRYABLE_ERRCODES = new HashSet<Integer>(Arrays.asList(
            -1,      // 系统繁忙
            -2,      // 系统繁忙（旧口径）
            88,      // 钉钉：请求过于频繁（限流）
            89,      // 钉钉：请求过于频繁（限流，旧口径）
            40001,   // 钉钉：AppSecret / access_token 不合法（token 失效，刷新后可重试）
            40014,   // 钉钉：不合法的 access_token
            41001,   // 钉钉：缺少 access_token 参数
            42001,   // 钉钉：access_token 超时
            90018,   // 钉钉：请求过于频繁，请降低调用频率
            50002,   // 钉钉内部系统错误
            60020    // 访问IP不在白名单（配置修正后可重试）
    ));

    /** 钉钉错误码是否可重试。 */
    public static boolean isRetryable(int errcode) {
        return RETRYABLE_ERRCODES.contains(errcode);
    }

    /** token 失效类错误码（需要强制刷新 token 并立即重试一次）。 */
    public static boolean isTokenError(int errcode) {
        return errcode == 40001 || errcode == 40014 || errcode == 41001 || errcode == 42001;
    }

    // ------------------------------------------------------------------ 钉钉字段字符集

    /**
     * 钉钉对卡片类字段（taskcard/interactive_taskcard 的 {@code task_id}、
     * 按钮 {@code key}）有字符集限制：<b>只能由数字、字母和 “_-@” 组成，最长 128 字节</b>。
     *
     * <p>OA 侧业务单号经常带邮箱（{@code zhengli.wang@ghedu.com}）、带点号、带中文或
     * {@code /}、{@code |} 等字符，直接拼进 task_id 会被钉钉以
     * <b>42014（卡片消息的task_id不合法）</b> 拒收 —— 表现为“待办提醒推送失败”。
     * 这里统一把不允许的字符替换成 {@code _}，保证送出去的卡片一定合法。</p>
     */
    public static String safeWxToken(String value) {
        String src = value == null ? "" : value;
        StringBuilder sb = new StringBuilder(src.length());
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            sb.append(isWxTokenChar(c) ? c : '_');
        }
        return sb.toString();
    }

    /** 字段是否本来就全是钉钉允许的字符（用于判断是否发生过替换）。 */
    public static boolean isWxTokenSafe(String value) {
        String src = value == null ? "" : value;
        for (int i = 0; i < src.length(); i++) {
            if (!isWxTokenChar(src.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWxTokenChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_' || c == '-' || c == '@';
    }

    /**
     * 内容指纹：8 位十六进制（FNV-1a，跨 JVM / 重启稳定）。
     *
     * <p>用于两处：① 字符被替换后补指纹，避免 {@code a.b} 与 {@code a-b} 归一成同一个 task_id；
     * ② 超长截断后补指纹，避免截断碰撞。</p>
     */
    public static String fingerprint(String raw) {
        int hash = 0x811c9dc5;
        String src = raw == null ? "" : raw;
        for (int i = 0; i < src.length(); i++) {
            hash ^= src.charAt(i);
            hash *= 0x01000193;
        }
        String hex = Integer.toHexString(hash & 0xffffffff);
        while (hex.length() < 8) {
            hex = "0" + hex;
        }
        return hex;
    }

    /** 钉钉返回码 -> 运维提示（写进推送日志的 errmsg，便于日志页直接看出怎么修）。 */
    public static String errorHint(int errcode) {
        switch (errcode) {
            case 42014:
                return "卡片 task_id 不合法或已存在（taskid has existed）：只允许数字、字母和 _ - @，"
                        + "业务单号里的邮箱点号、中文等已自动替换为 _、超 128 字节自动截断；"
                        + "钉钉要求发送任务卡片时 task_id 唯一，重复推送会自动换新 task_id，"
                        + "历史失败日志点“重推”也会换一个新 task_id 再发；如仍报错请对照日志里的 task_id 与业务单号";
            case 42015:
                return "更新卡片的应用与发送卡片的应用不是同一个：请用同一钉钉应用推送与更新";
            case 42016:
                return "要更新的 task_id 不存在：该卡片未推送成功或已被清理，请重推待办卡片";
            case 42017:
                return "按钮 key 不存在：请核对消息类型里配置的按钮 key";
            case 42018:
                return "按钮 key 不合法：只允许数字、字母和 _ - @";
            case 42019:
                return "缺少按钮 key：请在消息类型里补上按钮 key";
            case 40127:
                return "跳转地址不支持的 uri schema：钉钉要求卡片链接带 http/https 协议头，"
                        + "请在后台 系统设置 → 微信设置 → 集成设置 里维护「OA访问地址」"
                        + "（链接前缀只取这一项）";
            case 40008:
                return "msgtype 不被钉钉接受：请核对消息类型映射里的钉钉消息形态";
            case 40003:
                return "接收人 userid 不合法：请核对人员绑定里的钉钉 userid";
            case 40056:
                return "agentid 不合法：请核对钉钉应用绑定里的 AgentId";
            case 60011:
                return "指定的成员无权限：请把接收人加入该钉钉应用的可见范围";
            case 60020:
                return "调用 IP 不在应用可信 IP 白名单：请在钉钉后台把 OA 服务器出口 IP 加白";
            case 60021:
                return "userid 不在应用可见范围内：请调整该钉钉应用的可见范围";
            case 81013:
                return "UserID / 部门 / 标签全部非法或无权限：请核对该成员是否存在于本企业";
            case 40014:
                return "不合法的 access_token：请核对租户配置里的 AppKey / AppSecret（企业内部应用）是否正确、"
                        + "应用是否已被停用；access_token 失效时本模块会自动刷新并重试";
            case 41001:
                return "缺少 access_token 参数：请核对 AppKey / AppSecret 是否维护完整";
            case 42001:
                return "access_token 超时：本模块会自动刷新并重试，持续报错请核对服务器时间";
            case 90018:
                return "请求过于频繁（钉钉限流）：请在“推送配置”里把分片人数调小、"
                        + "或把接收人拆到多次推送，隔一会儿重试";
            case 88:
            case 89:
                return "请求过于频繁（钉钉限流）：请降低推送频率后重试";
            case 60003:
                return "找不到该成员（userid 不存在或已离职）：请在“人员绑定”页重新绑定钉钉成员";
            default:
                return "";
        }
    }

    private DingTalkMsgConst() {
    }
}
