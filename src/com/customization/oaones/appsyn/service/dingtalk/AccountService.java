package com.customization.oaones.appsyn.service.dingtalk;

import com.alibaba.fastjson.JSONObject;

/**
 * 个人账号中心（本人自助）：本人 OA 账号信息 + 本人钉钉绑定管理 + 扫码新增绑定。
 *
 * <p>与其它业务域的区别：本服务**只操作登录人本人**（`oa_user_id` 固定取登录会话），
 * 因此不做租户维护人分权校验；写操作的安全性由两点保证：
 * 一是所有读写都由服务端按登录人ID限定范围，二是扫码新增绑定以
 * “钉钉返回的扫码人身份 + 带签名且未过期的 state”为凭据
 * （见 {@link com.customization.oaones.appsyn.common.OaonesScanToken}）。</p>
 */
public interface AccountService {

    /** 登录人的 OA 账号信息（头像/账号/登录名/手机号/员工编号/部门/状态等）。 */
    JSONObject info(String oaUserId);

    /**
     * 登录人的钉钉绑定情况：启用中的钉钉租户清单 + 本人在各租户的绑定明细
     * （一个 OA 人员在每个租户最多一条绑定，因此一行 = 一个租户）。
     */
    JSONObject wxList(String oaUserId) throws Exception;

    /**
     * 为登录人生成某钉钉租户的扫码绑定二维码地址（内嵌官方二维码，含签名 state）。
     *
     * @param callbackBase 回调地址前缀（协议 + 域名 + 端口，不含路径）
     */
    JSONObject qrStart(String cfgId, String oaUserId, String callbackBase) throws Exception;

    /**
     * 扫码回调：用 state 还原发起人与租户、用 code 换扫码人钉钉身份，并把本人账号与该成员绑定。
     *
     * <p>回调没有登录会话可依赖（手机上扫码 / 换设备打开），身份取自签名 state。</p>
     */
    JSONObject scanConfirm(String code, String state) throws Exception;

    /** 解除登录人在某钉钉租户的绑定（只解除本人这一条，不影响其它 OA 人员）。 */
    JSONObject unbind(String cfgId, String oaUserId);
}
