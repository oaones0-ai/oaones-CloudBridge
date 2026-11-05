package com.customization.oaones.appsyn.entity;

/**
 * OAones 功能开关配置（uf_oaones_appsyn_feature_setting）。
 *
 * <p>一个功能一行；没有配置行时按启用处理，避免升级后误隐藏现有功能。</p>
 */
public class FeatureSetting {

    private Long id;
    private String featureCode;
    private Integer enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
