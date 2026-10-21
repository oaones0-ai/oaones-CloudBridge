package com.customization.oaones.appsyn.service.feishu;

import com.alibaba.fastjson.JSONObject;

/**
 * 统一登录设置（`/oaones/loginConfig/Feishu/config.jsp`）。
 *
 * <p>设置项：账号密码登录 / 手机号登录 / 飞书扫码登录 / 飞书扫码登录 /
 * 个人微信登录 / 扫码二维码有效期（秒）/ **登录页外观**（logo、整页背景图、左侧富文本）。
 * 其中**飞书与个人微信只保留配置项**，
 * 登录页不展示、也没有换取身份的实现（见 {@code QrLoginPlatform#supported}）。</p>
 *
 * <p>登录页按这里的开关显示登录方式；二维码有效期（秒）直接决定票据过期时间，
 * 超时后登录页提示“已超时，请手动刷新二维码”。</p>
 */
public interface LoginConfigService {

    /**
     * 当前设置（永远不返回 null：没有数据时返回默认值，即账号密码 + 飞书扫码 开、
     * 二维码有效期取 properties 的 `login.qr.ttl`）。
     */
    JSONObject get();

    /**
     * 登录页需要的公开部分（不下发备注、维护人、建模字段等管理信息）。
     *
     * <p>登录页与扫码落地页用它决定显示哪些登录方式；未接入的飞书/个人微信也会带出来，
     * 供页面在“只开了未接入的方式”时给出明确提示。</p>
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
     * <p>保存前校验：至少要有一种**已实现**的登录方式开启（账号密码 / 手机号 / 飞书扫码），
     * 否则会把所有人挡在登录页外；二维码有效期只接受 `login.qr.ttl.min/max` 之间的秒数；
     * {@code logo} 只接受空值或本页上传后产生的地址（{@code /oaones/img/loginlogo_xxx.png}）——
     * 请求里**不带 logo 参数 = 保持原值**（从别的入口只改开关时不会把 logo 抹掉），
     * 带空串 = 清除 logo（同时清理不再被引用的旧文件）；
     * {@code bg_image} 与 {@code logo} 同一口径；{@code brand_html}（登录页左侧富文本，HTML）
     * **设置什么存什么**，只做长度上限校验。</p>
     */
    JSONObject save(JSONObject params, String operatorId);

    /** 当前登录人是否有权查看/维护统一登录设置。 */
    boolean canMaintain(String operatorId);

    /** 二维码有效期（秒）：设置页配了就用它，否则回退 properties 的 `login.qr.ttl`。 */
    int qrTtlSeconds();
}
