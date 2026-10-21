package com.customization.oaones.appsyn.service.feishu;

import com.alibaba.fastjson.JSONArray;

/**
 * 统一登录页（`/oaones/login/login.jsp`）里的**飞书**租户视图（三个平台共用同一个登录页）。
 *
 * <p>本接口只负责“登录页能看到什么租户”：登录页没有登录人，无法按维护人过滤，
 * 因此列出**全部启用中**的租户，并对配置不全（缺 App ID / App Secret）的租户
 * 标记原因，页面据此决定显示“可以扫码”还是“请联系管理员补配置”；
 * <b>不下发 corp_secret</b>。</p>
 *
 * <p>扫码登录本身（自建二维码、票据、平台换取身份、一次性登录令牌）见
 * {@link ScanLoginService}；两张表分工：本接口读租户配置，ScanLoginService 读写扫码票据。</p>
 */
public interface LoginService {

    /**
     * 启用中的飞书租户清单（含 usable / reason 标记），供登录页与扫码探测共同使用。
     *
     * <p>返回顺序即租户配置顺序；扫码**探测顺序**由 {@code ScanLoginServiceImpl#tenantsOf} 决定
     * （properties 指定了 {@code login.qywx.cfg_id} 时该租户会被排到最前，单租户部署一次跳转即命中）。</p>
     */
    JSONArray tenants();
}
