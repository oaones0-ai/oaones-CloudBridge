package com.customization.oaones.appsyn.msg.feishu.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 消息中心（OA -> 飞书推送）常量。
 *
 * <p>开关口径与 uf_oaones_appsyn_app_bind.yx 保持一致：
 * {@code 0 = 启用 / 1 = 停用}；状态口径集中在 {@link FeishuMsgConst} 一处，页面与后端都按此判断。</p>
 *
 * <p><b>飞书消息形态</b>与企微、钉钉都不同，本连接器只保留四种：</p>
 * <ul>
 *   <li>{@code text} 文本（{@code im/v1/messages} msg_type=text）；</li>
 *   <li>{@code post} 富文本（msg_type=post：标题 + 段落，正文里带跳转链接）；</li>
 *   <li>{@code interactive} 消息卡片（msg_type=interactive：标题 + markdown 正文 + 按钮）；</li>
 *   <li>{@code task_card} 待办（走飞书任务接口 {@code task/v2}，可办结 / 失效）。</li>
 * </ul>
 */
public final class FeishuMsgConst {

    /** 启用（各配置表 yx 列）。 */
    public static final int STATE_ENABLE = 0;
    /** 停用（各配置表 yx 列）。 */
    public static final int STATE_DISABLE = 1;

    /** 记录状态标记（废弃/删除用）。 */
    public static final String YES = "1";
    public static final String NO = "0";

    /** 飞书消息形态：文本（im/v1/messages，msg_type=text）。 */
    public static final String FS_TEXT = "text";
    /** 飞书消息形态：富文本（im/v1/messages，msg_type=post：标题 + 段落，支持正文内链接）。 */
    public static final String FS_POST = "post";
    /** 飞书消息形态：消息卡片（im/v1/messages，msg_type=interactive：标题 + markdown 正文 + 按钮）。 */
    public static final String FS_INTERACTIVE = "interactive";
    /**
     * 飞书消息形态：待办（走飞书任务接口 {@code /open-apis/task/v2/tasks}）。
     *
     * <p>待办会进入接收人的飞书“任务/待办”列表，并支持二次更新
     * （完成 / 失效，见 {@code FeishuMsgApi#updateTaskCard}），
     * 因此“流程待办提醒”这类办结后需要收口的消息用本形态。</p>
     */
    public static final String FS_TASK_CARD = "task_card";

    /** 全部飞书消息形态（用于页面下拉、保存校验与日志展示）。 */
    public static final String[] FS_MSGTYPES = {
            FS_TEXT, FS_POST, FS_INTERACTIVE, FS_TASK_CARD
    };

    /** 消息形态文案（页面下拉与日志展示用）。 */
    public static String msgtypeLabel(String msgtype) {
        String value = msgtype == null ? "" : msgtype.trim();
        if (FS_POST.equals(value)) {
            return "富文本（post）";
        }
        if (FS_INTERACTIVE.equals(value)) {
            return "消息卡片（interactive）";
        }
        if (FS_TASK_CARD.equals(value)) {
            return "待办（task_card）";
        }
        return "文本（text）";
    }

    /** 是否为可二次更新状态的形态（只有卡片与待办能更新）。 */
    public static boolean isCardMsgtype(String msgtype) {
        String value = msgtype == null ? "" : msgtype.trim();
        return FS_TASK_CARD.equals(value) || FS_INTERACTIVE.equals(value);
    }

    /**
     * 飞书待办（任务）状态更新动作码：置为已完成。
     *
     * <p>飞书任务只有“未完成 / 已完成”两种状态，办结与驳回都按“已完成”收口
     * （驳回在日志里写清文案），失效按“删除待办”收口，见 {@code FeishuMsgApi#updateTaskCard}。</p>
     */
    public static final int TASK_UPDATE_DONE = 1;
    /** 飞书待办状态更新动作码：驳回（同样置为已完成，日志文案区分）。 */
    public static final int TASK_UPDATE_REJECTED = 2;
    /** 飞书待办状态更新动作码：失效（删除该待办）。 */
    public static final int TASK_UPDATE_INVALID = 3;

    /** 标准消息多语言取值用的简体中文语言ID（与平台 HtmlLabelInfo.languageid 一致）。 */
    public static final int LANG_ZH_CN = 7;

    /** 卡片动作：不处理卡片（普通消息）。 */
    public static final String CARD_NONE = "none";
    /** 卡片动作：新建待办。 */
    public static final String CARD_CREATE = "create";
    /** 卡片动作：把既有待办更新为“已处理”。 */
    public static final String CARD_DONE = "done";
    /** 卡片动作：把既有待办更新为“已驳回”。 */
    public static final String CARD_REJECTED = "rejected";
    /** 卡片动作：把既有待办更新为“已失效”（删除待办）。 */
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

    /** 生成 12 位推送流水（去横线 uuid 的前 12 位，可直接进 URL 与日志检索条件）。 */
    public static String bizSeq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, BIZ_SEQ_LEN);
    }

    /**
     * 业务单号追加推送流水：{@code requestid} -> {@code requestid-<12位uuid>}。
     *
     * <p>业务键为空（标准消息没带 targetId）时原样返回空串，不生成无意义单号。</p>
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
     * <p>待办任务表按稳定业务键绑定：同一待办对同一接收人只有一条待办，
     * 办结 / 退回 / 收回的标准消息才能按它找到并收口那条待办；
     * 推送日志仍按带流水的业务单号记录。</p>
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
     * 可重试的飞书错误码：网络抖动、临时限流、token 失效等可恢复异常自动重试；
     * 权限不足、参数非法、接收人不存在等业务异常不重试。
     */
    private static final Set<Integer> RETRYABLE_ERRCODES = new HashSet<Integer>(Arrays.asList(
            -1,        // 本模块：系统繁忙 / 网络异常
            -2,        // 本模块：系统繁忙（旧口径）
            9499,      // 飞书：系统内部错误
            99991400,  // 飞书：请求过于频繁（触发应用限流）
            99991663,  // 飞书：tenant_access_token 无效（刷新后重试）
            99991664,  // 飞书：app 校验失败 / app_secret 不合法（刷新后重试）
            230020,    // 飞书：调用频率超限，请稍后重试
            230038,    // 飞书：请求过于频繁
            1061045    // 飞书任务接口：系统繁忙
    ));

    /** 飞书错误码是否可重试。 */
    public static boolean isRetryable(int errcode) {
        return RETRYABLE_ERRCODES.contains(errcode);
    }

    /**
     * token 失效类错误码（需要强制刷新 tenant_access_token 并立即重试一次）。
     *
     * <p>飞书把 token 类错误统一放在 {@code 999916xx} 段：{@code 99991663} 是
     * tenant_access_token 校验失败，{@code 99991661/99991664/99991668} 是应用身份
     * （App ID / App Secret）不合法或未发布版本。</p>
     */
    public static boolean isTokenError(int errcode) {
        return errcode == 99991663
                || errcode == 99991664
                || errcode == 99991661
                || errcode == 99991668;
    }

    // ------------------------------------------------------------------ 飞书字段字符集

    /**
     * 飞书对任务（待办）、消息卡片等字段有字符集与长度限制：
     * <b>task 的 guid / summary 与卡片字段建议只用数字、字母和 “_-@ .”</b>，
     * OA 侧业务单号经常带邮箱、斜杠、竖线或中文，直接塞进结构化字段容易被拒。
     *
     * <p>这里把不允许的字符替换成 {@code _}，用于生成稳定的“本地待办流水ID”
     * （待办推送成功后会用飞书返回的 task guid 覆盖它）。</p>
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

    /** 字段是否本来就全是允许的字符（用于判断是否发生过替换）。 */
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
                || c == '_' || c == '-' || c == '@' || c == '.';
    }

    /**
     * 内容指纹：8 位十六进制（FNV-1a，跨 JVM / 重启稳定）。
     *
     * <p>用于两处：① 字符被替换后补指纹，避免 {@code a.b} 与 {@code a-b} 归一成同一个值；
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

    /** 飞书返回码 -> 运维提示（写进推送日志的 errmsg，便于日志页直接看出怎么修）。 */
    public static String errorHint(int errcode) {
        switch (errcode) {
            case 99991663:
                return "飞书 tenant_access_token 无效或已过期：本模块会自动刷新并重试一次；"
                        + "持续报错请核对租户配置里的 App ID / App Secret，"
                        + "以及该应用是否已被停用、是否已发布版本";
            case 99991664:
            case 99991661:
            case 99991668:
                return "飞书应用身份校验失败：请核对租户配置里的 App ID / App Secret（企业自建应用），"
                        + "并确认应用已发布（创建版本并通过审核）且在有效期内";
            case 99991672:
            case 99991671:
            case 99991670:
                return "飞书应用缺少调用该接口的权限：请在飞书开放平台给应用开通"
                        + "「通讯录」「发送消息」「任务」等对应权限，并重新发布版本";
            case 230001:
                return "飞书参数不合法：请核对消息类型映射里的模板与跳转地址";
            case 230002:
                return "接收人不在应用的可用范围（或机器人不可用）："
                        + "请把接收人加入该飞书应用的可见范围";
            case 230013:
                return "接收人不是该应用的可用成员：请调整该飞书应用的可见范围";
            case 230020:
            case 230038:
                return "请求过于频繁（飞书限流）：请在“推送配置”里把分片人数调小，"
                        + "或把接收人拆到多次推送，隔一会儿重试";
            case 99991400:
                return "触发飞书应用级限流：请降低推送频率后重试（本模块会自动重试）";
            case 9499:
                return "飞书系统内部错误：请稍后重试，持续报错请联系飞书开放平台";
            case 1061001:
                return "飞书任务参数不合法：请核对“消息类型映射”里待办的标题/正文模板";
            case 1061002:
                return "没有该任务的编辑权限：请确认应用已开通任务权限，"
                        + "且推送与更新用的是同一个飞书应用";
            case 1061004:
                return "飞书任务不存在或已被删除：该待办可能是上一次推送失败留下的记录，"
                        + "在日志页点“重推”会重新建一条待办";
            default:
                return "";
        }
    }

    private FeishuMsgConst() {
    }
}
