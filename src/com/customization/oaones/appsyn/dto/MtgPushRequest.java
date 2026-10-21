package com.customization.oaones.appsyn.dto;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 「会议 / 日程同步」推送到第三方平台时的一份**请求报文**（只组装、不发送）。
 *
 * <p>推送适配器（{@code MtgPushAdapter#buildRequest}）先把请求组装成本对象，
 * <b>实际推送</b>（{@code MtgPushAdapter#push}）与页面上的<b>「报文」预览</b>
 * （{@code mtgsync.payload}）用的是同一份代码，保证“页面上看到的报文 = 实际下发的报文”。</p>
 *
 * <p>字段说明（{@link #note}）随报文一起组装：注释写在组装字段的同一行，
 * 页面弹框按顺序展示，避免“报文改了、说明没改”。</p>
 */
public class MtgPushRequest {

    /** HTTP 方法：企微 / 钉钉固定 POST，飞书新增 POST、更新 PATCH。 */
    private String method = "POST";
    /** 接口路径（不含域名与 access_token，token 在真正调用时实时换取）。 */
    private String path;
    /** 请求体（实际下发给平台的 JSON）。 */
    private final JSONObject body = new JSONObject();
    /** 字段简要说明：[{field, text}]，field 用报文里的字段路径（如 schedule.summary）。 */
    private final JSONArray fields = new JSONArray();
    /**
     * 组装时发现的“这条实际推不出去”的原因（如 OA 里没有开始时间）。
     *
     * <p>页面「报文」预览要能**把报文显示出来**（顺带说明为什么推不出去），
     * 所以组装阶段不抛异常，把原因挂在这里：{@code push()} 见到它就照旧抛出去
     * （同步记录里失败行“说明”写的就是这句话），预览则把它当提示展示。</p>
     */
    private String pushError;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public JSONObject getBody() { return body; }

    public JSONArray getFields() { return fields; }

    public String getPushError() { return pushError; }
    public void setPushError(String pushError) { this.pushError = pushError; }

    /**
     * 给报文里的某个字段加一条简要说明（页面弹框“字段说明”按加入顺序展示）。
     *
     * @param field 报文里的字段路径，如 {@code schedule.summary} / {@code attendees[].userid}
     * @param text  一句话说明这个字段取自哪里、什么口径
     */
    public void note(String field, String text) {
        fields.add(new JSONObject().fluentPut("field", field).fluentPut("text", text));
    }
}
