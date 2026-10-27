package com.customization.oaones.appsyn.service;

import com.customization.oaones.appsyn.dto.MtgPushRequest;

/**
 * 「会议 / 日程同步」平台推送适配器：把一个 OA 会议 / 日程 推到第三方平台的日程 / 日历。
 *
 * <p>按平台类型（0 企微 / 1 钉钉 / 2 飞书）各有一个实现，放在对应连接器的子包里
 * （{@code api.wecom.WeComScheduleApi} / {@code api.dingtalk.DingTalkScheduleApi} /
 * {@code api.feishu.FeishuScheduleApi}），同步引擎按 type 分派，业务侧不感知平台字段差异。</p>
 *
 * <p>约定：成功返回平台侧对象ID（写进同步记录 {@code uf_oaones_appsyn_mtg_sync_log.wx_id}）；
 * 失败直接抛异常（引擎捕获后记一条“失败”记录，不影响其余对象）。</p>
 *
 * <p>请求报文由 {@link #buildRequest} 单独组装，{@link #push} 直接拿它下发；
 * 页面「同步清单」行尾的「报文」预览（{@code mtgsync.payload}）也调它，
 * 同一份代码保证“弹框里看到的报文 = 实际下发的报文”。</p>
 */
public interface MtgPushAdapter {

    /**
     * 组装一条会议 / 日程的平台请求报文（**只组装：不取 token、不发请求**）。
     *
     * <p>页面「报文」弹框用它预览：给出 HTTP 方法、接口路径、请求体与逐字段简要说明。
     * 缺少必要数据（如开始时间）时同样抛异常，页面按提示展示。</p>
     *
     * @param ctx 租户凭据 + OA 对象 + 换算好的平台成员ID（见 {@link MtgPushContext}）
     * @return 请求报文（方法 / 路径 / 请求体 / 字段说明，见 {@link MtgPushRequest}）
     * @throws RuntimeException 缺少必要数据时抛出（带可读的原因，页面按提示展示）
     */
    MtgPushRequest buildRequest(MtgPushContext ctx);

    /**
     * 推送一条会议 / 日程。
     *
     * @param ctx 租户凭据 + OA 对象 + 换算好的平台成员ID（见 {@link MtgPushContext}）
     * @return 平台侧对象ID（日程 / 事件 ID）
     * @throws Exception 调用平台接口失败、或缺少必要凭据时抛出（带可读的失败原因）
     */
    String push(MtgPushContext ctx) throws Exception;

    /**
     * 删除平台上的一条日程 / 事件（**源端删除要同步到接收方**的那一步）。
     *
     * <p>会议 / 日程同步是双向的，但“谁创建谁维护”：OA 创建的对象（映射 {@code source = 0}）
     * 在 OA 被删除 / 取消后，由本方法调平台删除接口把平台上的那条日程也删掉；
     * 平台创建的对象反过来由拉取侧在 OA 标记删除（见 {@code MtgSyncServiceImpl#reconcileDeletes}）。</p>
     *
     * <p>{@code ctx.getExistWxId()} 必须带平台对象ID（映射表里的 {@code wx_id}）；
     * 删除失败（无权限 / 对象已不存在等）直接抛异常，引擎按
     * {@code OaonesConst.MTG_DELETE_RETRY_HOURS} 节流后重试。</p>
     */
    void delete(MtgPushContext ctx) throws Exception;
}
