package com.customization.oaones.appsyn.service;

import com.customization.oaones.appsyn.dto.MtgPlatformItem;

import java.util.List;

/**
 * 「会议 / 日程同步」平台**拉取**适配器（第三方→OA）：从一个租户下若干成员的日历里
 * 读出时间窗内的日程 / 事件，归一化成 {@link MtgPlatformItem}。
 *
 * <p>按平台类型（0 企微 / 1 钉钉 / 2 飞书）各有一个实现，放在对应连接器的子包里
 * （{@code api.wecom.WeComScheduleApi} / {@code api.dingtalk.DingTalkScheduleApi} /
 * {@code api.feishu.FeishuScheduleApi}），与推送适配器 {@link MtgPushAdapter} 是同一个平台客户端。</p>
 *
 * <p>约定：返回时间窗内的平台对象（按 {@code wxId} 去重由拉取引擎负责）；
 * 单个日历查询失败不影响其它日历（实现内部吞掉并记日志，返回已取到的部分）。</p>
 */
public interface MtgPullAdapter {

    /**
     * 拉取时间窗内的平台日程 / 事件。
     *
     * @param ctx 租户凭据 + 要查询的日历 + 时间窗（见 {@link MtgPullContext}）
     * @return 平台侧对象列表（可能为空；失败时抛异常由引擎记一条“失败”）
     * @throws Exception 缺少必要凭据等致命问题时抛出
     */
    List<MtgPlatformItem> list(MtgPullContext ctx) throws Exception;

    /**
     * 按平台对象ID **现查**这件事（删除对账用）：返回仍存在的对象；
     * 平台返回“已取消 / 查不到”的对象不出现在结果里。
     *
     * <p>默认实现返回 {@code null}，表示“该平台没有按 ID 现查的接口”：调用方退回
     * “按本轮拉取结果推断”（对象在本轮日历列表里消失即视为已删除，且只在
     * {@code ctx.getListedCalendars()} 里确实取过的日历上推断）。
     * 企业微信有 {@code /cgi-bin/oa/schedule/get}（一次最多 1000 条），因此精确对账走这里。</p>
     *
     * @param ctx 平台凭据（与拉取同一份）
     * @param wxIds 平台对象ID（调用方已去重、分片）
     * @return 仍存在的对象；{@code null} = 本平台不支持按 ID 现查
     */
    default List<MtgPlatformItem> getByIds(MtgPullContext ctx, List<String> wxIds) throws Exception {
        return null;
    }
}
