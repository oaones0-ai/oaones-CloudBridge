package com.customization.oaones.appsyn.entity;

/**
 * 日志保留配置（uf_oaones_log_retention）。
 *
 * <p>每个日志项一行；{@code keepDays <= 0} 表示永久保留。</p>
 */
public class LogRetentionSetting {

    private Long id;
    private String logCode;
    private Integer keepDays;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogCode() {
        return logCode;
    }

    public void setLogCode(String logCode) {
        this.logCode = logCode;
    }

    public Integer getKeepDays() {
        return keepDays;
    }

    public void setKeepDays(Integer keepDays) {
        this.keepDays = keepDays;
    }
}
