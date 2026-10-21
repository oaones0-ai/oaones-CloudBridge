package com.customization.oaones.appsyn.msg.dingtalk.service;

import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesMyBatis;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgConst;
import com.customization.oaones.appsyn.msg.dingtalk.common.DingTalkMsgProps;
import com.customization.oaones.appsyn.msg.dingtalk.dao.MsgTaskMapper;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgApp;
import com.customization.oaones.appsyn.msg.dingtalk.entity.MsgTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片任务服务：待办卡片 task_id 的登记与状态更新。
 *
 * <p>task_id 由“业务类型 + <strong>本次推送的业务单号</strong> + 钉钉成员”生成：
 * 钉钉要求发送任务卡片时 task_id 唯一（重复会被 42014 拒收），所以同一业务每次推送
 * 都是一张新卡片；卡片任务表里的 {@code biz_id} 记<strong>稳定业务键</strong>，
 * 流程办结/退回/收回等标准消息按“业务键 + 接收人”取出待处理卡片，
 * 逐张调用钉钉 update_taskcard 置灰。</p>
 *
 * <p><strong>业务键口径</strong>：本类各方法的 {@code bizId} 入参传的是
 * <strong>稳定业务键</strong>（业务单号去掉末尾 12 位推送流水，流程即 requestid），
 * 不是推送日志里带流水的业务单号 —— 业务单号带流水（{@link DingTalkMsgConst#withBizSeq}）
 * 是为了让同一流程可以重复推送，若用它生成 task_id 会导致办结 / 退回时
 * 找不到原来那张卡片。</p>
 */
public class MsgTaskService {

    /** 钉钉 task_id 长度上限（字节）。 */
    private static final int TASK_ID_MAX_BYTES = 128;

    /**
     * 生成 task_id。
     *
     * <p>钉钉对卡片 {@code task_id} 有硬性约束：<b>只能由数字、字母和 “_-@” 组成，最长 128 字节</b>，
     * 不满足会直接以 <b>42014（卡片消息的task_id不合法）</b> 拒收整条消息。
     * OA 的业务单号里常见邮箱（{@code zhengli.wang@ghedu.com}）、点号、中文、{@code /}、{@code |} 等，
     * 直接拼进去就会推送失败，因此这里统一：</p>
     *
     * <ol>
     *   <li>把“业务类型 / 业务单号 / 钉钉成员”里的非法字符替换成 {@code _}；</li>
     *   <li>发生过替换时补 8 位内容指纹，避免 {@code a.b} 与 {@code a-b} 归一成同一个 task_id
     *       （同一业务对同一人仍然稳定、可复现）；</li>
     *   <li>超过 128 字节时保留“前缀 + 指纹 + 尾部”，保证唯一且落在长度限制内。</li>
     * </ol>
     */
    public String buildTaskId(String bizType, String bizId, String wxUserid) {
        String raw = "oaones_" + OaonesUtil.s(bizType) + "_" + OaonesUtil.s(bizId)
                + "_" + OaonesUtil.s(wxUserid);
        String taskId = "oaones_" + DingTalkMsgConst.safeWxToken(OaonesUtil.s(bizType))
                + "_" + DingTalkMsgConst.safeWxToken(OaonesUtil.s(bizId))
                + "_" + DingTalkMsgConst.safeWxToken(OaonesUtil.s(wxUserid));
        if (!taskId.equals(raw)) {
            taskId = taskId + "_" + DingTalkMsgConst.fingerprint(raw);
            OaonesLog.step("[msg] task_id 含钉钉不允许的字符，已替换为 _ 并补指纹：业务单号="
                    + OaonesUtil.s(bizId) + " 成员=" + OaonesUtil.s(wxUserid) + " task_id=" + taskId);
        }
        if (taskId.length() > TASK_ID_MAX_BYTES) {
            // 已全部是单字节字符（数字/字母/_-@），按字符截断与按字节截断等价
            String prefix = "oaones_" + DingTalkMsgConst.fingerprint(raw) + "_";
            int keep = TASK_ID_MAX_BYTES - prefix.length();
            taskId = prefix + taskId.substring(taskId.length() - keep);
            OaonesLog.step("[msg] task_id 超过 128 字节，已按“指纹+尾部”截断：业务单号="
                    + OaonesUtil.s(bizId) + " task_id=" + taskId);
        }
        return taskId;
    }

    public MsgTask getByTaskId(String taskId) {
        if (taskId == null || taskId.length() == 0) {
            return null;
        }
        return OaonesMyBatis.call(MsgTaskMapper.class,
                mapper -> mapper.getByTaskId(DingTalkMsgProps.taskTable(), taskId));
    }

    public MsgTask getByBizUser(String bizType, String bizId, String wxUserid) {
        if (bizId == null || bizId.length() == 0 || wxUserid == null || wxUserid.length() == 0) {
            return null;
        }
        return OaonesMyBatis.call(MsgTaskMapper.class,
                mapper -> mapper.getByBizUser(DingTalkMsgProps.taskTable(), bizType, bizId, wxUserid));
    }

    /**
     * 同一业务 + 同一接收人的卡片列表（{@code taskState} 传空表示不过滤状态）。
     *
     * <p>同一个业务允许重复推送，每次推送都是一张新卡片（钉钉要求 taskcard 的 task_id 唯一），
     * 所以办结 / 退回时要按业务键取出该接收人的全部待处理卡片逐张置灰。</p>
     *
     * <p>用已有的 {@code listByBiz}（按业务取该业务的全部卡片，量很小）在内存里按接收人 / 状态过滤，
     * 不再新增 Mapper 语句 —— E9 的 MyBatis 注册表只在 JVM 启动时建一次，
     * 新增 Mapper 语句必须重启 E9 才生效，热替换 class 不会重建注册表。</p>
     */
    public List<MsgTask> listByBizUser(String bizType, String bizId, String wxUserid, String taskState) {
        List<MsgTask> result = new ArrayList<MsgTask>();
        if (bizId == null || bizId.length() == 0 || wxUserid == null || wxUserid.length() == 0) {
            return result;
        }
        List<MsgTask> list = OaonesMyBatis.call(MsgTaskMapper.class, mapper ->
                mapper.listByBiz(DingTalkMsgProps.taskTable(), bizType, bizId));
        if (list == null) {
            return result;
        }
        for (MsgTask task : list) {
            if (task == null || !wxUserid.equals(OaonesUtil.s(task.getWxUserid()).trim())) {
                continue;
            }
            if (taskState != null && taskState.length() > 0
                    && !taskState.equals(OaonesUtil.s(task.getTaskState()).trim())) {
                continue;
            }
            result.add(task);
        }
        return result;
    }

    /**
     * 修正历史 task_id（升级自愈）。
     *
     * <p>老版本把业务单号/账号里的邮箱、点号等字符原样拼进 task_id，被钉钉以 42014 拒收，
     * 且这些历史记录仍然留在卡片任务表里；办结/退回时按 task_id 找不到卡片就会更新失败。
     * 这里按新口径重算并把历史行改名，返回修正后的 task_id（本来就合法时原样返回）。</p>
     */
    public String repairTaskId(String bizType, String bizId, String wxUserid, String oldTaskId) {
        if (oldTaskId == null || oldTaskId.length() == 0) {
            return oldTaskId;
        }
        if (DingTalkMsgConst.isWxTokenSafe(oldTaskId) && oldTaskId.length() <= TASK_ID_MAX_BYTES) {
            return oldTaskId;
        }
        String fixed = buildTaskId(bizType, bizId, wxUserid);
        if (fixed.equals(oldTaskId)) {
            return oldTaskId;
        }
        if (getByTaskId(oldTaskId) != null) {
            renameTaskId(oldTaskId, fixed);
        }
        return fixed;
    }

    /** 修正历史 task_id（入参为卡片任务行，办结/退回/失效更新路径用）。 */
    public String repairTaskId(MsgTask task) {
        if (task == null) {
            return null;
        }
        return repairTaskId(task.getBizType(), task.getBizId(), task.getWxUserid(), task.getTaskId());
    }

    /**
     * 把 task_id 改名。
     *
     * <p>两处使用：① 历史 task_id 里带钉钉不允许的字符时按新口径改名；
     * ② 待办推送成功后把本模块生成的推送流水ID改写成钉钉返回的 {@code record_id}，
     * 供办结/驳回/失效时按 record_id 更新待办状态。</p>
     */
    public void renameTaskId(String oldTaskId, String newTaskId) {
        int rows = OaonesMyBatis.call(MsgTaskMapper.class, mapper -> mapper.updateTaskId(
                DingTalkMsgProps.taskTable(), oldTaskId, newTaskId, OaonesUtil.now()));
        OaonesLog.step("[msg] 修正历史 task_id：" + oldTaskId + " -> " + newTaskId + " rows=" + rows);
    }

    /**
     * 换一个新的 task_id（重推“卡片创建失败”的日志时用）。
     *
     * <p>钉钉返回 <b>42014（taskid has existed / empty / exceed max len）</b> 时，
     * 说明这个 task_id 已经存在或不能再用，重推时原样再发必然还是同一个错；
     * 因此在合法前缀后面补一段新的推送流水，并对卡片任务表同步改名，
     * 让这次重推真正落一张新卡片（钉钉要求发送任务卡片时 task_id 唯一）。</p>
     */
    public String renewTaskId(String bizType, String bizId, String wxUserid, String oldTaskId) {
        String old = OaonesUtil.s(oldTaskId).trim();
        String base = old.length() > 0 && DingTalkMsgConst.isWxTokenSafe(old) && old.length() <= TASK_ID_MAX_BYTES
                ? old : buildTaskId(bizType, bizId, wxUserid);
        String renewed = base + "_" + DingTalkMsgConst.bizSeq();
        if (renewed.length() > TASK_ID_MAX_BYTES) {
            renewed = buildTaskId(bizType, bizId + "-" + DingTalkMsgConst.bizSeq(), wxUserid);
        }
        try {
            if (old.length() > 0 && !old.equals(renewed) && getByTaskId(old) != null) {
                renameTaskId(old, renewed);
            }
        } catch (Throwable t) {
            // 卡片任务表改名失败不影响这次重推：钉钉那边用的是新的 task_id
            OaonesLog.step("[msg] 重推换 task_id 时同步卡片任务表失败（不影响本次重推）：" + t.getMessage());
        }
        OaonesLog.step("[msg] 卡片 task_id 已换新（重推）：" + old + " -> " + renewed);
        return renewed;
    }

    /**
     * 登记卡片任务（同 task_id 已存在时复用，不重复插入）。
     *
     * @param bizId 稳定业务键（业务单号去掉推送流水，流程即 requestid）——卡片按它归属业务
     * @param taskId 本次推送的卡片 task_id（带推送流水，钉钉要求唯一）
     * @return 可用的 task_id（发生过历史改名时返回改名后的值）
     */
    public String register(String bizType, String bizId, String bizTitle, MsgApp app,
                           int oaUserId, String wxUserid, String taskId) {
        MsgTask exists = getByTaskId(taskId);
        if (exists != null) {
            // 重新推送同一待办：状态回到待处理，卡片会随 task_id 在钉钉端更新
            if (!DingTalkMsgConst.TASK_PENDING.equals(exists.getTaskState())) {
                updateState(taskId, DingTalkMsgConst.TASK_PENDING);
            }
            return taskId;
        }
        // 同一业务同一接收人的历史卡片（老 task_id 里带邮箱等非法字符、被钉钉拒收过）：
        // 直接改名复用，不再插一条新记录，后续办结/退回才更新得到同一张卡片
        MsgTask history = getByBizUser(bizType, bizId, wxUserid);
        if (history != null && !taskId.equals(history.getTaskId())
                && !DingTalkMsgConst.isWxTokenSafe(history.getTaskId())) {
            renameTaskId(history.getTaskId(), taskId);
            if (!DingTalkMsgConst.TASK_PENDING.equals(history.getTaskState())) {
                updateState(taskId, DingTalkMsgConst.TASK_PENDING);
            }
            return taskId;
        }
        MsgTask task = new MsgTask();
        task.setTaskId(taskId);
        task.setBizType(bizType);
        task.setBizId(bizId);
        task.setBizTitle(bizTitle);
        task.setAppId(app == null ? null : app.getId());
        task.setCorpId(app == null ? null : app.getCorpId());
        task.setAgentId(app == null ? null : app.getAgentId());
        task.setOaUserId(oaUserId);
        task.setWxUserid(wxUserid);
        task.setTaskState(DingTalkMsgConst.TASK_PENDING);
        // 时间统一用建模系统标准字段：登记日期/时间 + 最近更新时间
        task.setModedatacreatedate(OaonesUtil.nowDate());
        task.setModedatacreatetime(OaonesUtil.nowTime());
        task.setModedatamodifydatetime(OaonesUtil.now());
        OaonesMyBatis.run(MsgTaskMapper.class, mapper -> mapper.insert(DingTalkMsgProps.taskTable(), task));
        return taskId;
    }

    public void updateState(String taskId, String taskState) {
        OaonesMyBatis.run(MsgTaskMapper.class, mapper -> mapper.updateState(
                DingTalkMsgProps.taskTable(), taskId, taskState, OaonesUtil.now()));
    }

    /** 按保留天数清理卡片任务。 */
    public int cleanExpired(int keepDays) {
        if (keepDays <= 0) {
            return 0;
        }
        // 登记时间用建模标准字段（日期 + 时间两列），清理时按同样的两段口径比较
        String[] before = MsgLogService.splitDateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(System.currentTimeMillis() - keepDays * 24L * 3600 * 1000)), "00:00:00");
        int rows = OaonesMyBatis.call(MsgTaskMapper.class,
                mapper -> mapper.deleteBefore(DingTalkMsgProps.taskTable(), before[0], before[1]));
        OaonesLog.step("[msg] 清理卡片任务 before=" + before[0] + " " + before[1] + " rows=" + rows);
        return rows;
    }
}
