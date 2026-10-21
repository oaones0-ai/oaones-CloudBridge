package com.customization.oaones.appsyn.service.feishu;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.entity.feishu.AppBind;

import java.util.List;

/**
 * 飞书工作台应用 - OA 页面 绑定维护。
 *
 * <p>一条绑定 = 飞书某个自建应用（App ID）在飞书工作台里的一个入口：
 * 用户从飞书工作台打开该应用时，先按飞书网页授权（code → userid）拿到成员身份，
 * 再按人员绑定关系找到 OA 账号，免登录进入该绑定配置的 OA 页面。</p>
 *
 * <p>支持多应用绑定：同一飞书租户可绑定多个应用（不同 App ID），
 * 每个应用配置一个必填的 PC 端 OA 页面地址，可选再配置手机端 OA 页面地址
 * （留空表示手机端与 PC 端相同）。</p>
 */
public interface AppBindService {

    /** 应用绑定列表（按租户/关键字过滤）。 */
    JSONArray list(String cfgId, String keyword);

    /** 按“可维护租户集合”过滤的应用绑定列表（页面用）。 */
    JSONArray listForCfgs(List<Integer> cfgIds, String keyword);

    /** 单条绑定明细，不存在返回 null。 */
    JSONObject get(String id);

    /** 绑定记录所属的飞书租户ID（按记录操作时的分权校验用）。 */
    Integer cfgIdOf(String bindId);

    /** 新增/更新应用绑定（同一租户下 App ID 唯一；OA 地址必须是站内路径）。 */
    JSONObject save(JSONObject params, String operatorId);

    /** 启用（0）/停用（1）应用绑定。 */
    JSONObject setState(String bindId, String state, String operatorId);

    /** 删除应用绑定。 */
    void delete(String bindId);

    /**
     * 免登录入口：解析飞书工作台应用入口参数并核实身份。
     *
     * <p>依次完成：定位启用中的绑定 → 校验租户状态 → 用飞书网页授权 code 换成员身份
     * → 按人员绑定关系定位 OA 账号 → 校验 OA 账号可登录 → 计算落地页地址。
     * 返回体含 {@code oa_user_id} 与 {@code redirect_url}，由调用方（控制器）写入登录会话后跳转。</p>
     *
     * @param bindId  绑定记录ID（入口地址中的 b 参数，可选）
     * @param cfgId   飞书租户ID（入口地址中的 cfg_id 参数）
     * @param agentId 飞书App ID（入口地址中的 agent_id 参数）
     * @param code    飞书网页授权 code
     * @param state   飞书回带的 state（可携带站内目标页面，可选）
     * @param userAgent 客户端 User-Agent（用于识别手机端/桌面端，决定用手机端还是 PC 端链接）
     * @param device  入口地址上的显式设备参数（pc / mobile，优先级高于 User-Agent）
     */
    JSONObject workbenchLogin(String bindId, String cfgId, String agentId, String code, String state,
                              String userAgent, String device) throws Exception;

    /**
     * 免登录入口缺少 {@code code} 时的飞书网页授权地址（OAuth2 授权链接）。
     *
     * <p>飞书工作台打开应用时通常会直接带上 {@code code}；若没有带上
     * （例如飞书开放平台没有把本 OA 域名登记进该应用的「重定向URL」），
     * 入口页会先跳到本方法给出的授权地址换取授权码，再带 {@code code} 回到入口页，
     * 因此两种情况下都能完成免登录。调用方需在 {@code redirectUri} 里带上防循环标记。</p>
     *
     * @param redirectUri 授权后回跳的入口页完整地址（含 cfg_id/agent_id/state 等参数）
     * @param state       原样带回的 state（可为目标 OA 页面的站内路径）
     */
    String oauthAuthorizeUrl(String bindId, String cfgId, String agentId, String redirectUri, String state)
            throws Exception;

    /**
     * 免登录落地页：state → （按客户端来源）手机端/PC 端 OA 页面地址 → 另一端页面地址回退
     * → 默认首页（都只取站内路径）。
     */
    String landingUrl(AppBind bind, String state, String userAgent, String device);
}
