package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;

/**
 * 个人微信（与企微 / 钉钉 / 飞书并列的第四个平台，但没有“租户”概念）。
 *
 * <p>两条入口共用本服务：</p>
 * <ul>
 *   <li><b>个人账号中心 → 个人微信页签</b>（`/oaones/accountCenter/MyAccount.jsp?tab=personalwx`）：
 *       本人查看 / 扫码绑定 / 换绑 / 解绑，所有读写都限定在登录会话本人的那一行；</li>
 *   <li><b>微信设置</b>（`/oaones/personalWx/config.jsp`，独立一级菜单）：
 *       系统管理员（user.id=1）与 `oaones.properties#pw.admin.ids` 中的人员维护微信授权密钥
 *       （AppID / AppSecret），并在“个人微信绑定列表”里为员工绑定、解绑。</li>
 * </ul>
 *
 * <p>扫码绑定走微信扫码授权：页面内嵌授权二维码，用户用个人微信扫码后回调页把 code 交给
 * {@link #scanConfirm(String, String)}；发起人身份取自带签名且未过期的 state（见 OaonesScanToken），
 * 因此回调可以在没有登录会话的设备上完成绑定。</p>
 */
public interface PersonalWxService {

    /** 当前登录人是否有权维护微信授权密钥、解绑 / 代绑他人的个人微信。 */
    boolean canMaintain(String operatorId);

    /** 全局微信授权密钥是否已配置（AppID 与 AppSecret 都非空）；登录页只据此判断个人微信能否使用。 */
    boolean configured();

    /**
     * 微信授权密钥（AppID / AppSecret 掩码 / 备注）+ 是否已配置；仅维护人可读。
     *
     * <p>页面地址上带 {@code ?showSetting=true}（= 显示“微信设置”这一部分）时才下发这些参数；
     * 不带该条件的页面（只展示下方个人微信绑定列表）**只回 {@code configured} / {@code can_maintain}，
     * 不下发 AppID / AppSecret 掩码 / 备注 / 修改人等设置参数**。</p>
     */
    JSONObject getConfig(String operatorId, boolean showSetting);

    /** 保存微信授权密钥（AppID 与 AppSecret 必填）；仅维护人可保存；返回体同样按 showSetting 裁剪。 */
    JSONObject saveConfig(JSONObject params, String operatorId, boolean showSetting);

    /**
     * 个人微信绑定列表（一行一个 OA 有效账号，**含系统管理员账号、不含子账户**）。
     *
     * <p>子账户（E9 标准 {@code hrmresource.accounttype = 1}）既不显示在列表里，
     * 也不计入列表的“已绑定 / 未绑定 / OA 有效账号合计”统计，且不允许绑定个人微信
     * （绑定入口统一由 {@link #qrStart} / {@link #scanConfirm} 校验拦下）。</p>
     *
     * @param keyword OA 人员姓名 / 登录名 / 工号 / 手机号 / 人员ID 关键字（空串取全部）
     * @param state   绑定状态筛选：空串全部 / "1" 已绑定 / "0" 未绑定
     */
    JSONObject list(String keyword, String state, String operatorId);

    /**
     * 本人的个人微信绑定状态（个人账号中心用，只看登录人自己那一行）。
     *
     * <p>返回体里的 {@code sub_account}（= {@code account_type} 为 {@code "1"}）表示登录人
     * 是子账户：页面据此提示“子账户不支持绑定个人微信”并禁用扫码绑定入口。</p>
     */
    JSONObject mine(String oaUserId);

    /**
     * OA 分部 / 部门候选（绑定列表里“选择人员”弹框的分部、部门两个条件用）。
     *
     * <p>与人员绑定页的 {@code user.orgOptions} 同一份数据源（E9 组织缓存
     * {@code SubCompanyComInfo} / {@code DepartmentComInfo}）与同一套字段口径，
     * 供弹框里做“列表式 / 树状 + 选中含下级”的分部 / 部门选择。</p>
     */
    /**
     * 绑定列表“选择人员”弹框的分部 / 部门候选。
     *
     * <p>与人员绑定页 {@code user.orgOptions} 同一份数据源，并按登录人的 OA 分权范围过滤
     * （OA 没开分权时=全量），见 {@link com.customization.oaones.appsyn.common.OaonesHrmScope}。</p>
     */
    JSONObject orgOptions(String operatorId);

    /**
     * 生成扫码绑定的二维码地址与签名 state。
     *
     * @param operatorId      登录人（发起人）
     * @param targetOaUserId  为空 / 等于登录人 = 绑定本人；指定他人（管理员代绑）时必须具备维护权限
     * @param callbackBase    回调地址前缀（协议 + 域名 + 端口），供未配置 properties 时兜底
     */
    JSONObject qrStart(String operatorId, String targetOaUserId, String callbackBase);

    /**
     * 个人微信登录：为统一扫码登录票据生成微信网页授权地址。
     *
     * <p>这里返回的 {@code auth_url} 由登录页内嵌成微信官方二维码；扫码回调仍回到统一落地页
     * {@code /oaones/login/scan.jsp}，state 里带票据，不暴露 AppSecret。</p>
     */
    JSONObject loginQrStart(String ticket, String callbackBase);

    /**
     * 个人微信登录回调：用微信返回的 code 换 openid，按个人微信绑定关系定位 OA 账号，
     * 再把统一登录票据推进到“已扫码 / 已确认”。
     */
    JSONObject loginScanStep(String ticket, String code, String baseUrl, String ip, String userAgent);

    /** 扫码回调：按 code 换 openid 并写入 / 更新绑定（无登录会话，身份取自签名 state）。 */
    JSONObject scanConfirm(String code, String state);

    /**
     * 解绑。
     *
     * @param operatorId     登录人
     * @param targetOaUserId 为空 = 解除本人的绑定；指定他人时必须具备维护权限
     */
    JSONObject unbind(String operatorId, String targetOaUserId);
}
