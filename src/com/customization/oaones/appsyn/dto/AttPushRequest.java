package com.customization.oaones.appsyn.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 反向同步（OA → 第三方）的写入报文载体：HTTP 方法 + 路径 + 请求体 + 逐字段简要说明。
 *
 * <p>与会议 / 日程同步的 {@code MtgPushRequest} 同构：页面上的“这条会怎么写到平台”与实际下发
 * 共用同一份组装代码（{@code AttPushAdapter#buildRequest}），因此看到的报文就是要发出去的报文。</p>
 */
public class AttPushRequest {

    private String method = "POST";
    private String path = "";
    private String body = "";
    /** 组装阶段就能发现的问题（如缺少打卡时间）：挂在这里，真正推送时照旧抛出去。 */
    private String pushError = "";
    /** 字段说明：key = 字段路径，value = 口径说明（页面按顺序展示）。 */
    private final Map<String, String> notes = new LinkedHashMap<String, String>();

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getPushError() { return pushError; }
    public void setPushError(String pushError) { this.pushError = pushError; }

    public Map<String, String> getNotes() { return notes; }

    /** 记一条字段说明。 */
    public void note(String field, String text) {
        if (field != null && field.length() > 0) {
            notes.put(field, text == null ? "" : text);
        }
    }
}
