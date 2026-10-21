package com.customization.oaones.appsyn.service.impl.dingtalk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.dingtalk.LoginService;

/**
 * 登录页租户视图实现：只读钉钉租户配置表，不下发 corp_secret。
 *
 * <p>扫码登录（自建二维码 / 票据 / 换取身份 / 一次性登录令牌）在
 * {@link ScanLoginServiceImpl} 里，本类保持“只提供租户清单”这一件事，
 * 避免登录页与扫码登录两处各写一份“哪些租户可用”的判断。</p>
 */
public class LoginServiceImpl implements LoginService {

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    @Override
    public JSONArray tenants() {
        JSONArray result = new JSONArray();
        JSONArray rows = configService().list();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String cfgId = OaonesUtil.nullToEmpty(row.getString("id"));
            JSONObject item = new JSONObject();
            item.put("cfg_id", cfgId);
            item.put("cfg_name", OaonesUtil.s(row.getString("corp_name")));
            item.put("corp_id", OaonesUtil.s(row.getString("corp_id")));
            item.put("agent_id", OaonesUtil.s(row.getString("agent_id")));
            boolean enabled = OaonesUtil.i(row.getString("sync_enable"), 0) == 1;
            String reason = unusableReason(row, enabled);
            item.put("enabled", enabled);
            item.put("usable", reason.length() == 0);
            item.put("reason", reason);
            // 只列启用中的租户：停用租户不该出现在登录页/扫码探测里（否则员工会扫到不接收二维码的企业）
            if (enabled) {
                result.add(item);
            }
        }
        OaonesLog.step("登录页读取可扫码租户：启用租户 " + result.size() + " 个");
        return result;
    }

    /** 租户可用性判断：返回空串表示可用于扫码登录，否则返回“缺了什么”的说明。 */
    private String unusableReason(JSONObject row, boolean enabled) {
        if (!enabled) {
            return "已停用";
        }
        String corpId = OaonesUtil.s(row.getString("corp_id"));
        String corpSecret = OaonesUtil.s(row.getString("corp_secret"));
        if (corpId.length() == 0 || corpSecret.length() == 0) {
            return "未维护 AppKey / AppSecret（企业内部应用）";
        }
        // AgentId 只有“工作台应用免登录”才用得到，扫码登录本身不依赖它，因此不作可用性门槛
        return "";
    }
}
