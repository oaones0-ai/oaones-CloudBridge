package com.customization.oaones.appsyn.dto;

/**
 * 考勤同步「考勤应用」候选项（页面下拉 + 后端取凭据两用）。
 *
 * <p>考勤数据必须用有考勤权限的应用自己的 Secret 换 access_token
 * （租户配置里的通讯录 Secret 一般没有考勤权限），因此每条设置要单独指定「考勤应用」；
 * 候选来自「应用绑定」表（应用名称 + AgentId/AppId + Secret）。</p>
 *
 * <p>两条取值口径：页面下拉（{@code listAppOptions}）只带展示字段与“Secret 是否已配置”两个标志位，
 * 不下发 Secret；后端取凭据（{@code getAppById}）才带 {@code appSecret} 与租户的
 * {@code corpId / corpSecret}，供同步引擎与「校验」决定用哪一份凭据（应用自己的 Secret 优先）。</p>
 */
public class AttAppOption {

    private Integer id;
    private Integer cfgId;
    private String appName;
    private String agentId;
    private String appSecret;
    private Integer yx;
    private Integer secretConfigured;
    private Integer cfgSecretConfigured;
    private String cfgCorpId;
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
    public void setCfgSecretConfigured(Integer cfgSecretConfigured) { this.cfgSecretConfigured = cfgSecretConfigured; }

    public String getCfgCorpId() { return cfgCorpId; }
    public void setCfgCorpId(String cfgCorpId) { this.cfgCorpId = cfgCorpId; }

    public String getCfgCorpSecret() { return cfgCorpSecret; }
    public void setCfgCorpSecret(String cfgCorpSecret) { this.cfgCorpSecret = cfgCorpSecret; }
}
