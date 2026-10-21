package com.customization.oaones.appsyn.controller.feishu;

import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.OaonesUpload;
import com.customization.oaones.appsyn.controller.BaseController;
import com.customization.oaones.appsyn.service.impl.feishu.LoginConfigServiceImpl;
import com.customization.oaones.appsyn.service.feishu.LoginConfigService;

import javax.servlet.http.HttpServletRequest;

/**
 * 统一登录设置控制器（action 前缀 {@code logincfg.}）。
 *
 * <p>页面：`/oaones/loginConfig/config.jsp`（模块一级菜单“登录设置”，三个连接器共用一行配置）。
 * 与登录页的 {@code login.*} 不同，本控制器走 {@code /oaones/Feishu/api.jsp}，
 * 因此**必须有登录会话**，并且只允许系统管理员与 `oaones.properties#login.admin.ids`
 * 里列出的人员查看/修改（登录方式开关一改就是全体员工的登录入口，必须收紧权限）。</p>
 *
 * <p>{@code logincfg.imageUpload}（登录页 logo / 整页背景图上传）收的是文件，页面通过
 * {@code api.jsp}（GET 口径）调不到，由专用入口页 {@code /oaones/loginConfig/imageUpload.jsp}
 * 以 multipart 调进来（入口页调的是企微连接器的那一个，本类保留同一组 action 只为口径一致）；
 * 用途 {@code kind=logo|bg} 放在查询串上。</p>
 */
public class LoginConfigController extends BaseController {

    private LoginConfigService loginConfigService() {
        return new LoginConfigServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId) {
        if ("logincfg.get".equals(action)) {
            // 查看也按维护人限制：非维护人不需要看到开关明细
            if (!loginConfigService().canMaintain(operatorId)) {
                return OaonesUtil.fail("你不是统一登录设置的维护人，无权查看（仅系统管理员与 login.admin.ids 中的人员可维护）");
            }
            JSONObject data = loginConfigService().get();
            data.put("can_maintain", true);
            return OaonesUtil.ok(data);
        }
        if ("logincfg.save".equals(action)) {
            return OaonesUtil.ok(loginConfigService().save(paramObject(request), operatorId));
        }
        if ("logincfg.imageUpload".equals(action)) {
            // 登录页图片上传：上传**只落盘、不写库**（地址由页面放进表单，保存时随 logincfg.save 一起写）
            // 解析上限取两类图片里较大的那一档（背景图 2MB），按用途的严格上限（logo 1MB）在 Service 里判
            OaonesUpload.Item file = OaonesUpload.read(request, OaonesConst.LOGIN_IMAGE_MAX_BYTES);
            return OaonesUtil.ok(loginConfigService().uploadImage(
                    request.getParameter("kind"),
                    file == null ? "" : file.getName(),
                    file == null ? null : file.getBytes(),
                    operatorId));
        }
        return OaonesUtil.fail("未知 action: " + action);
    }
}
