package com.customization.oaones.appsyn.controller.dingtalk;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.dingtalk.ConfigService;
import com.customization.oaones.appsyn.service.impl.dingtalk.ConfigServiceImpl;

import javax.servlet.http.HttpServletRequest;

/**
 * 钉钉租户配置控制器（action 前缀 cfg.）。
 *
 * <p>租户分权：只有被指定为该租户维护人的用户才能查看与设置该租户；
 * 系统管理员（user.id = 1）可查看与维护全部租户。</p>
 */
public class ConfigController extends BaseController {

    private ConfigService configService() {
        return new ConfigServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("cfg.list".equals(action)) {
            // 仅返回当前登录人可查看的租户，并标注其是否为该租户维护人
            return OaonesUtil.ok(configService().listForUser(operatorId));
        }
        if ("cfg.myList".equals(action)) {
            // 同步中心等业务页：仅返回当前登录人可维护的租户
            return OaonesUtil.ok(configService().listMaintainable(operatorId));
        }
        if ("cfg.save".equals(action)) {
            JSONObject params = paramObject(request);
            String id = OaonesUtil.s(params.getString("id"));
            assertCfgRight(id, operatorId, "无权修改");
            return OaonesUtil.ok(configService().save(params, operatorId));
        }
        if ("cfg.delete".equals(action)) {
            String id = req(request, "id");
            assertCfgRight(id, operatorId, "无权删除");
            configService().delete(id);
            return OaonesUtil.ok();
        }
        if ("cfg.test".equals(action)) {
            String id = req(request, "id");
            assertCfgRight(id, operatorId, "无权进行操作");
            return OaonesUtil.ok(configService().test(id));
        }
        return null;
    }

    /** 已有租户（id > 0）必须由维护人或系统管理员操作；新建（id 为空）不校验。 */
    private void assertCfgRight(String cfgId, String operatorId, String actionText) {
        if (OaonesUtil.i(cfgId, 0) > 0 && !configService().canMaintain(cfgId, operatorId)) {
            throw new RuntimeException("您不是该钉钉租户的维护人，" + actionText + "该租户");
        }
    }
}
