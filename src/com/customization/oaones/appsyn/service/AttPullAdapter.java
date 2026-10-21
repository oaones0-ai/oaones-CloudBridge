package com.customization.oaones.appsyn.service;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.dto.AttPunch;

import java.util.List;

/**
 * 考勤同步「第三方 → OA」的平台适配器：从平台读出时间窗内的打卡记录，
 * 归一化成 {@link AttPunch}（业务层不感知平台字段差异）。
 *
 * <p>按平台类型（0 企微 / 1 钉钉 / 2 飞书）各有一个实现，放在对应连接器的子包里
 * （api.wecom.WeComAttApi / api.dingtalk.DingTalkAttApi / api.feishu.FeishuAttApi）。</p>
 *
 * <p>接口名、权限点、时间跨度与人数限制在各平台版本间会变（需求第七节表格只作为实现起点），
 * 因此三个适配器的接口路径都做成配置项（att.*.path）；首次上线实测对不上时只改配置或对应适配器一处，
 * 不触碰页面、设置与日志。</p>
 */
public interface AttPullAdapter {

    /**
     * 拉取时间窗内的打卡记录。
     *
     * <p>单个成员查询失败不影响其它成员（实现内部记日志并继续）；致命问题（缺少凭据、
     * 应用无考勤权限、token 换不到）直接抛异常，由引擎把整批记失败并写明平台原文。</p>
     */
    List<AttPunch> list(AttPullContext ctx) throws Exception;

    /**
     * 「校验」动作：用该应用凭据真调一次平台考勤接口，逐项验证（需求 3.1.2）：
     * ① 换 access_token 成功（企业标识 / Secret / AgentId 正确）；
     * ② 能读到考勤组列表（或等效的考勤基础数据）；
     * ③ 能读到至少一条打卡 / 排班数据（确认打卡数据读取权限）。
     *
     * <p>返回结构：ok（是否通过）、step（卡在哪一步）、message（含平台原文）等；
     * 不通过时页面直接展示平台原始错误信息。</p>
     */
    JSONObject probe(AttPullContext ctx) throws Exception;
}
