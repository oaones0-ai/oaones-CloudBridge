package com.customization.oaones.appsyn.dao.feishu;

import com.customization.oaones.appsyn.entity.feishu.ScanLogin;
import org.apache.ibatis.annotations.Param;

/**
 * 扫码登录票据表 Mapper（uf_oaones_appsyn_scan_login）。
 *
 * <p>按 ticket 定位（唯一索引），状态推进用 {@link #updateState} 之外的小粒度更新方法，
 * 避免“PC 轮询”与“手机确认”两个线程互相覆盖对方写的字段。</p>
 */
public interface ScanLoginMapper {

    ScanLogin findByTicket(@Param("table") String table, @Param("ticket") String ticket);

    int insert(@Param("table") String table, @Param("e") ScanLogin entity);

    /**
     * 扫码落地：记录平台、命中的租户、扫码人身份与命中结果。
     *
     * <p>只写“扫码侧”的字段，不动 browser_key；扫码成功时同时延长 expire_time，
     * 给扫码人留出确认时间，避免原二维码倒计时在确认前把票据判过期。</p>
     */
    int markScanned(@Param("table") String table, @Param("e") ScanLogin entity);

    /** 扫码失败：写失败原因与状态 3（失败），不改动命中身份字段。 */
    int markFailed(@Param("table") String table,
                   @Param("ticket") String ticket,
                   @Param("msg") String msg,
                   @Param("platform") String platform,
                   @Param("scanIp") String scanIp,
                   @Param("scanUa") String scanUa);

    /** 扫码人确认登录：写状态 2（已确认）与一次性登录令牌。 */
    int markConfirmed(@Param("table") String table,
                      @Param("ticket") String ticket,
                      @Param("loginToken") String loginToken,
                      @Param("confirmTime") long confirmTime);

    /**
     * PC 完成登录：状态置 5（已使用）并清掉登录令牌。
     *
     * <p>带 {@code state = 2 且 login_token = 令牌} 的条件更新，返回受影响行数：
     * 并发或重放时只有第一个请求能拿到 1，其余为 0（调用方据此拒绝）。</p>
     */
    int markUsed(@Param("table") String table,
                 @Param("ticket") String ticket,
                 @Param("loginToken") String loginToken,
                 @Param("usedTime") long usedTime);

    /** 置为已过期（PC 轮询发现超时时顺手落库，便于排查）。 */
    int markExpired(@Param("table") String table, @Param("ticket") String ticket);

    /**
     * 把票据退回“待扫码”（同一张二维码重试用）。
     *
     * <p>手机端扫码失败（例如选错了企业）后，只要二维码还没过期，就允许用同一张二维码重新扫：
     * 清掉上一次的身份命中结果与失败原因，重建流程，不需要让用户回电脑上刷新二维码。</p>
     */
    int resetToWaiting(@Param("table") String table, @Param("ticket") String ticket);

    /** 用户主动刷新二维码 / 取消：状态置 4（已过期），与“等太久自动过期”同一口径。 */
    int markCanceled(@Param("table") String table, @Param("ticket") String ticket);

    /** 清理过期票据（新建票据时顺手清理，不需要额外计划任务）。 */
    int deleteExpired(@Param("table") String table, @Param("before") long before);
}
