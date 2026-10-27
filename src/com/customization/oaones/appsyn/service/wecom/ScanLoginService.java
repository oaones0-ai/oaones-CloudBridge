package com.customization.oaones.appsyn.service.wecom;

import com.alibaba.fastjson.JSONObject;

/**
 * 扫码登录（**二维码由本系统自己生成**）的票据服务。
 *
 * <p>为什么要自建二维码：企业微信托管的 {@code qrConnect} 二维码页是按“单个企业的单个应用”
 * 生成的，一个二维码只能服务一家企业；而 OA 侧自己生成的二维码内容只是
 * {@code /oaones/appsyn/login/scan.jsp?t=<ticket>}（统一扫码落地页），扫码方（企业微信 / 钉钉 / 飞书）打开后
 * 由 OA 按扫码客户端判断平台、再依次用它已对接的租户配置换成员身份——
 * 于是一张二维码可以同时服务**三个平台与各自的多个租户**。</p>
 *
 * <p>状态机（{@code state}）：0 待扫码 / 1 已扫码待确认 / 2 已确认（可登录）/
 * 3 失败（msg 写原因）/ 4 已过期（含用户刷新作废）/ 5 已使用。</p>
 *
 * <p>会话只能由 **PC 的请求**写入（{@link LoginController#handle} 的 {@code login.qrLogin}），
 * 手机端只负责“确认”，这样 Set-Cookie 一定落在发起登录的 PC 浏览器上。</p>
 */
public interface ScanLoginService {

    int STATE_WAITING = 0;
    int STATE_SCANNED = 1;
    int STATE_CONFIRMED = 2;
    int STATE_FAILED = 3;
    int STATE_EXPIRED = 4;
    int STATE_USED = 5;

    /**
     * PC：新建登录票据并返回二维码地址。
     *
     * @param browserKey   PC 浏览器本地生成的随机串（完成登录时校验同一个值，防二维码被换浏览器使用）
     * @param baseUrl      站点地址前缀（协议 + 域名 + 端口），二维码内容按它拼本站地址
     * @param landingUrl   登录成功后的站内落地页（可空，取配置默认值）
     */
    JSONObject create(String browserKey, String baseUrl, String landingUrl,
                      String ip, String userAgent);

    /**
     * PC：轮询票据状态。
     *
     * <p>已确认（state=2）时返回一次性 {@code login_token}，PC 立刻用它调
     * {@code login.qrLogin} 写会话；轮询不会消耗令牌。</p>
     */
    JSONObject status(String ticket, String browserKey);

    /** PC：用一次性令牌换取登录会话（由 {@code LoginController} 在 PC 的请求里调用 {@link com.customization.oaones.appsyn.common.OaonesSso}）。 */
    JSONObject take(String ticket, String browserKey, String loginToken);

    /** PC：刷新二维码（旧票据作废）或页面关闭时取消。 */
    JSONObject cancel(String ticket, String browserKey);

    /**
     * 二维码内容（供 {@code qrImage.jsp} 把票据画成二维码图片）。
     *
     * <p>只按票据取**服务端生成时写入**的内容，不接受页面传入的任意文本
     * （避免这个端点被当成通用二维码生成器用）；票据不存在时返回空串。</p>
     */
    String qrText(String ticket);

    /**
     * 手机端：扫码落地/授权回跳的统一入口。
     *
     * <p>返回的 {@code mode} 供页面决定渲染哪种视图：</p>
     * <ul>
     *   <li>{@code oauth}：302 到该平台的网页授权地址（{@code auth_url}）；</li>
     *   <li>{@code choose}：配了多家企业时让扫码人在手机上选自己所在企业（{@code tenants} 列表）——
     *       不能拿一个授权 code 去逐家试探：跨企业授权在客户端会直接报错、code 也可能被消耗，
     *       所以多租户时明确选一次，单租户时自动跳过；</li>
     *   <li>{@code confirm}：已换出身份、等扫码人点“确认登录”；</li>
     *   <li>{@code done}：已确认（电脑端会自动登录）；</li>
     *   <li>{@code failed}：失败（{@code msg} 写原因）；</li>
     *   <li>{@code unsupported}：不是企业微信/钉钉扫的；</li>
     *   <li>{@code closed}：票据已过期或已完成。</li>
     * </ul>
     */
    JSONObject scanStep(String ticket, String platform, String cfgId, String code,
                        String baseUrl, String ip, String userAgent) throws Exception;

    /** 手机端：扫码人点“确认登录”（{@code act=confirm}）或“取消”（{@code act=cancel}）。 */
    JSONObject scanConfirm(String ticket, String act);
}
