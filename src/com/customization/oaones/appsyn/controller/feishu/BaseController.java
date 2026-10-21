package com.customization.oaones.appsyn.controller.feishu;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.service.impl.feishu.ConfigServiceImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

/**
 * 飞书连接器控制器基类：提供请求参数读取的公共方法。
 *
 * <p>与企微连接器的公共基类（{@code controller.BaseController}）分开维护：
 * 这里的分权校验走飞书租户配置（{@link ConfigServiceImpl}），
 * 保证飞书页面的每个 action 都按“飞书租户维护人”再校验一次。</p>
 */
public abstract class BaseController {

    /**
     * 处理某个 action；返回 null 表示当前控制器不处理该 action。
     */
    public abstract JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception;

    /** 单值参数（去首尾空格，null 归一为 ""）。 */
    protected String req(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? "" : value.trim();
    }

    /** 全部请求参数转 JSONObject。 */
    @SuppressWarnings("unchecked")
    protected JSONObject paramObject(HttpServletRequest request) {
        JSONObject params = new JSONObject();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }

    /**
     * 租户维度的分权校验：请求带有效租户ID（cfg_id）时，
     * 只有该租户维护人或系统管理员（user.id = 1）可以继续操作。
     *
     * <p>未指定维护人的租户对任何人都不可查看/设置；请求未带租户ID
     * （如全局默认绑定规则 cfg_id 为空/0）时不校验。</p>
     */
    protected void requireCfgRight(HttpServletRequest request, String operatorId) {
        int cfgId = OaonesUtil.i(req(request, "cfg_id"), 0);
        if (cfgId <= 0) {
            return;
        }
        assertCfgRight(String.valueOf(cfgId), operatorId);
    }

    /**
     * 指定租户的分权校验：请求参数里的租户ID之外，
     * 批量操作、按记录ID操作等场景由调用方显式传入租户ID再校验一次。
     *
     * <p>同一逻辑放在基类，避免各控制器各写一套判断。</p>
     */
    protected void assertCfgRight(String cfgId, String operatorId) {
        if (OaonesUtil.i(cfgId, 0) <= 0) {
            return;
        }
        if (!new ConfigServiceImpl().canMaintain(cfgId, operatorId)) {
            throw new RuntimeException("您不是该飞书租户的维护人，无权查看或设置该租户");
        }
    }
}
