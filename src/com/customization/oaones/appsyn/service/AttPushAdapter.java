package com.customization.oaones.appsyn.service;

import com.customization.oaones.appsyn.dto.AttPushRequest;

/**
 * 考勤同步「OA → 第三方」写入适配器（需求 3.9 第 4 条的三级方案）。
 *
 * <p>三个平台的落地方式必须先实测确认（Q13），所以这里按三级方案做成可配置的能力：
 * ① direct：平台开放“写入 / 导入打卡记录”接口时直接写入；
 * ② card：平台不开放打卡写入时，改走平台的补卡 / 申诉类审批接口（等价于帮员工提交一次补卡），
 * 需要现场配好审批模板编码，否则按“未配置 = 不支持”处理；
 * ③ none：确实没有写入能力时显式标记为不支持，页面上说明原因，
 * 明细写“跳过”而不是假装成功（OaonesConst.ATT_SKIP_PUSH_UNSUPPORTED）。</p>
 *
 * <p>落地方式按平台各配一条（{@code att.push.mode.<platform>}），按官方接口文档核对后的正式口径：
 * <b>企微 = card</b>（打卡的「为打卡人员补卡」接口）、<b>钉钉 / 飞书 = none</b>（平台不开放打卡写入，
 * 只能走补卡审批，配好审批模板 / 审批人后改成 card 即启用）。</p>
 *
 * <p>报文由 {@link #buildRequest} 单独组装、{@link #push} 直接拿它下发，
 * 保证“页面上看到的报文 = 实际发出去的报文”。</p>
 */
public interface AttPushAdapter {

    /** 组装一条写入请求报文（只组装：不取 token、不发请求）。 */
    AttPushRequest buildRequest(AttPushContext ctx);

    /**
     * 写入一条打卡（按 {@code ctx.getMode()} 走 direct 或 card）。
     *
     * @return 平台侧凭据（写入返回的记录ID / 补卡审批单号），没有时返回空串
     * @throws Exception 平台报错、缺少必要配置（如补卡审批模板未配）等；由引擎记一条明细失败
     */
    String push(AttPushContext ctx) throws Exception;

    /**
     * 该平台该方向当前的落地方式说明（页面展示 + 不支持时写明原因），
     * 例如“企业微信未开放打卡写入接口，该方向暂不支持（需实测确认后按配置开启）”。
     */
    String capabilityNote(AttPushContext ctx);
}
