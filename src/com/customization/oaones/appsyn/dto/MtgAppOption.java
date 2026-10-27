package com.customization.oaones.appsyn.dto;

/**
 * 会议 / 日程同步「绑定应用」候选项（数据来自“应用绑定”表 {@code uf_oaones_appsyn_app_bind}）。
 *
 * <p>用途有两处，取的是同一张表、同一批列：</p>
 * <ul>
 *   <li><b>页面下拉</b>（{@code mtgsync.list} 里每个租户的 {@code apps}）：只回填 id / 租户 /
 *       应用名 / AgentId / 启停 / 密钥是否就绪，<b>不含 Secret</b>；</li>
 *   <li><b>后端取凭据</b>（推送 / 拉取 / 校验）：回填 {@code appSecret} 与租户的
 *       {@code corpId / corpSecret}，供同步引擎决定“用应用的 Secret 还是回退租户配置”。</li>
 * </ul>
 */
public class MtgAppOption {

    /** 应用绑定记录ID（{@code uf_oaones_appsyn_app_bind.id}）。 */
    private Integer id;
    /** 所属租户配置ID（{@code uf_oaones_appsyn_qywx_config.id}）。 */
    private Integer cfgId;
    /** 应用名称（页面展示用）。 */
    private String appName;
    /** 平台应用标识：企微 AgentId / 钉钉 AgentId / 飞书 App ID。 */
    private String agentId;
    /** 应用自己的 Secret（只在后端取凭据时回填，不下发给页面）。 */
    private String appSecret;
    /** 绑定状态（物理列 yx）：0 有效 / 1 停用（{@code OaonesConst.APP_BIND_STATE_VALID}）。 */
    private Integer yx;
    /** 应用自己的 Secret 是否已配置：1 是 / 0 否（页面提示用；不取 Secret 本身）。 */
    private Integer secretConfigured;
    /** 租户配置里的 Secret 是否已配置：1 是 / 0 否（应用没单独配 Secret 时由它兜底）。 */
    private Integer cfgSecretConfigured;
    /** 租户企业标识（企微 corpid / 钉钉 AppKey / 飞书 App ID），后端取凭据时回填。 */
    private String cfgCorpId;
    /** 租户配置里的 Secret，后端取凭据时回填。 */
    private String cfgCorpSecret;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCfgId() { return cfgId; }
    public void setCfgId(Integer cfgId) { this.cfgId = cfgId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public Integer getYx() { return yx; }
    public void setYx(Integer yx) { this.yx = yx; }

    public Integer getSecretConfigured() { return secretConfigured; }
    public void setSecretConfigured(Integer secretConfigured) { this.secretConfigured = secretConfigured; }

    public Integer getCfgSecretConfigured() { return cfgSecretConfigured; }
    public void setCfgSecretConfigured(Integer cfgSecretConfigured) {
        this.cfgSecretConfigured = cfgSecretConfigured;
    }

    public String getCfgCorpId() { return cfgCorpId; }
    public void setCfgCorpId(String cfgCorpId) { this.cfgCorpId = cfgCorpId; }

    public String getCfgCorpSecret() { return cfgCorpSecret; }
    public void setCfgCorpSecret(String cfgCorpSecret) { this.cfgCorpSecret = cfgCorpSecret; }
}
