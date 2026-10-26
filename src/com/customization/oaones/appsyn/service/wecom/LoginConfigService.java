package com.customization.oaones.appsyn.service.wecom;

import com.alibaba.fastjson.JSONObject;

/**
 * 统一登录设置（`/oaones/loginConfig/config.jsp`）。
 *
 * <p>设置项：账号密码登录 / 手机号登录 / 企业微信扫码登录 / 钉钉扫码登录 /
 * 个人微信登录 / 扫码二维码有效期（秒）/ **登录页外观**（logo、整页背景图、左侧富文本）/
 * **登录框位置与尺寸**（视口左上角像素 + 固定宽高）/ **登录框背景透明度**（0~100）。
 * 桌面启动台自动跳转秒数仍存在同一张配置表里，但维护入口已移到「应用中心 → 应用列表」
 * （{@link #gopageSettings()} / {@link #saveGopageSeconds(String, String)}）。
 * 三个企业应用平台共用本站生成的二维码；个人微信使用微信开放平台官方二维码，
 * 在登录页上有独立页签，但复用同一套登录票据与确认流程。</p>
 *
 * <p>登录页按这里的开关显示登录方式；二维码有效期（秒）直接决定票据过期时间，
 * 超时后登录页提示“已超时，请手动刷新二维码”。</p>
 */
public interface LoginConfigService {

    /**
     * 当前设置（永远不返回 null：没有数据时返回默认值，即账号密码 + 企业微信扫码 开、
     * 二维码有效期取 properties 的 `login.qr.ttl`）。
     */
    JSONObject get();

    /**
     * 登录页需要的公开部分（不下发备注、维护人、建模字段等管理信息）。
     *
     * <p>登录页与扫码落地页用它决定显示哪些登录方式；个人微信另回
     * {@code personal_wx_available}，只有开关开启且授权密钥已配置时才为 true。</p>
     */
    JSONObject publicSettings();

    /**
     * 上传登录页外观图片（只有维护人可上传；与「应用中心」的图标同一套落盘口径，都落到
     * {@code oaones/img/} 下，地址形如 {@code /oaones/img/<前缀><时间戳>_<随机>.<ext>}）。
     *
     * <p>上传与保存是两步：本方法**只落盘、不写库**，设置页把回的地址放进表单，
     * 由 {@link #save(JSONObject, String)} 与其它设置一起保存——与「先上传图标、再保存应用」同一口径。
     * 因此“上传了但没点保存”的图片属于无引用文件，不影响登录页（登录页只认库里那一行）。</p>
     *
     * @param kind         图片用途：{@code logo} = 登录框内部上方的 logo（前缀 {@code loginlogo_}，
     *                     1MB / 2048 像素）；{@code bg} = 整页背景图（前缀 {@code loginbg_}，
     *                     2MB / 8192 像素）；其它值抛业务异常
     * @param originalName 浏览器提交的原始文件名（只记日志，落盘文件名由服务端生成）
     * @param content      文件内容（空 / 不是支持的图片格式 / 超过体积或长宽上限时抛业务异常）
     * @param operatorId   登录人（非维护人抛业务异常）
     */
    JSONObject uploadImage(String kind, String originalName, byte[] content, String operatorId);

    /**
     * 保存设置（仅系统管理员与 `login.admin.ids` 中列出的人员可保存）。
     *
     * <p>保存前校验：至少要有一种登录方式开启（账号密码 / 手机号 / 邮箱 / 企业应用扫码 / 个人微信），
     * 否则会把所有人挡在登录页外；二维码有效期只接受 `login.qr.ttl.min/max` 之间的秒数；
     * {@code logo} 只接受空值或本页上传后产生的地址（{@code /oaones/img/loginlogo_xxx.png}）——
     * 请求里**不带 logo 参数 = 保持原值**（从别的入口只改开关时不会把 logo 抹掉），
     * 带空串 = 清除 logo（同时清理不再被引用的旧文件）；
     * {@code bg_image}（整页背景图，{@code /oaones/img/loginbg_xxx.png}）与 {@code logo} 同一口径；
     * {@code brand_html}（登录页左侧富文本，HTML）**设置什么存什么**，只做长度上限校验
     * （{@code OaonesConst.LOGIN_BRAND_HTML_MAX}），同样“不带 = 保持原值、带空串 = 恢复系统默认”。
     * {@code login_box_left / top / width / height} 要么四个都带、要么四个都为空串（空串 = 恢复默认布局）；
     * {@code login_box_opacity} 为 0~100 的透明度（0 = 不透明，100 = 完全透明），
     * 不带 = 保持原值、空串 = 回到默认 0；
     * {@code gopage_seconds} 为 0~120 的秒数，空串 = 用 properties 默认值，0 = 不自动跳转。</p>
     */
    JSONObject save(JSONObject params, String operatorId);

    /** 当前登录人是否有权查看/维护统一登录设置。 */
    boolean canMaintain(String operatorId);

    /** 二维码有效期（秒）：设置页配了就用它，否则回退 properties 的 `login.qr.ttl`。 */
    int qrTtlSeconds();

    /**
     * 桌面启动台带 gopage 时自动跳转的秒数：设置页配了就用它，否则回退 properties 的
     * `login.landing.gopage.seconds`；0 = 不自动跳转。
     */
    int gopageSeconds();

    /**
     * 启动台自动跳转的页面回显数据：配置值（空串 = 用系统默认）、实际生效值、默认值与上限。
     * 该设置已从「登录设置」移到「应用中心 → 应用列表」，接口权限由应用中心那侧校验。
     */
    JSONObject gopageSettings();

    /**
     * 只保存「启动台自动跳转秒数」，<b>不改动任何登录方式 / 登录页外观</b>。
     *
     * @param seconds    空串 = 回到 properties 默认值；0 = 不自动跳转；其它为 0~120 的整数秒
     * @param operatorId 当前登录人
     */
    JSONObject saveGopageSeconds(String seconds, String operatorId);
}
