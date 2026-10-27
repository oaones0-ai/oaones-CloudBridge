package com.customization.oaones.appsyn.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.customization.oaones.appsyn.common.OaonesConst;
import com.customization.oaones.appsyn.common.OaonesUtil;
import com.customization.oaones.appsyn.common.OaonesUpload;
import com.customization.oaones.appsyn.service.AppCenterService;
import com.customization.oaones.appsyn.service.impl.AppCenterServiceImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 应用中心控制器（action 前缀 {@code appc.}）。
 *
 * <p>「应用中心」是没有租户维度的公共菜单（与个人微信同口径），两个页面：</p>
 * <ul>
 *   <li>应用列表（管理页）{@code /oaones/appsyn/appCenter/AppList.jsp}：
 *       {@code appc.list} / {@code appc.roles} / {@code appc.save} / {@code appc.state} / {@code appc.delete}
 *       / {@code appc.checkClass}，仅系统管理员与 {@code oaones.appsyn.properties#appc.admin.ids} 可维护；
 *       {@code appc.gopage} / {@code appc.gopageSave} 维护启动台自动跳转秒数
 *       （只给系统管理员 + {@code appc.admin.ids}，只写 {@code gopage_seconds} 一列，不改登录页设置）；
 *       {@code appc.uploadIcon}（图标上传）也属于这一组，但它收的是文件，
 *       由专用入口 {@code /oaones/appsyn/appCenter/iconUpload.jsp} 以 multipart 调进来，
 *       页面通过 {@code api.jsp}（GET 口径）调不到；</li>
 *   <li>桌面启动台（展示页）{@code /oaones/appsyn/appCenter/Desktop.jsp}：
 *       {@code appc.desktop}（取出启用中的应用）与 {@code appc.entry}（点击图标时按
 *       “单点登录实现类”生成最终访问地址），本模块任何登录人都可用。</li>
 * </ul>
 *
 * <p>“单点登录实现类”的约定见 {@code com.customization.oaones.appsyn.appsso.AppSsoHandler}：
 * 页面上只登记类名，真正的身份认证由该类里的方法完成。</p>
 */
public class AppCenterController extends BaseController {

    private AppCenterService appCenterService() {
        return new AppCenterServiceImpl();
    }

    @Override
    public JSONObject handle(String action, HttpServletRequest request, String operatorId)
            throws Exception {
        if ("appc.list".equals(action)) {
            return OaonesUtil.ok(appCenterService().list(req(request, "keyword"), operatorId));
        }
        if ("appc.roles".equals(action)) {
            // 可见范围 · 角色候选（只给能进应用中心的人；角色本身没有上下级，页面用列表式多选）
            JSONArray roles = new JSONArray();
            roles.addAll(appCenterService().roles(operatorId));
            return OaonesUtil.ok(roles);
        }
        if ("appc.gopage".equals(action)) {
            // 启动台自动跳转（从「登录设置」移过来）：只读写 gopage_seconds 一列，不碰登录方式与登录页外观
            return OaonesUtil.ok(appCenterService().gopageSettings(operatorId));
        }
        if ("appc.gopageSave".equals(action)) {
            return OaonesUtil.ok(appCenterService().saveGopageSeconds(
                    req(request, "gopage_seconds"), operatorId));
        }
        if ("appc.save".equals(action)) {
            return OaonesUtil.ok(appCenterService().save(paramObject(request), operatorId));
        }
        if ("appc.state".equals(action)) {
            return OaonesUtil.ok(appCenterService().setState(req(request, "id"),
                    req(request, "enable"), operatorId));
        }
        if ("appc.delete".equals(action)) {
            return OaonesUtil.ok(appCenterService().delete(req(request, "id"), operatorId));
        }
        if ("appc.checkClass".equals(action)) {
            return OaonesUtil.ok(appCenterService().checkClass(req(request, "sso_class")));
        }
        if ("appc.uploadIcon".equals(action)) {
            // 图标上传（页面上传，落到 oaones/appsyn/img/ 下）：multipart 请求由专用入口页转进来，
            // 这里只负责取出文件交给 Service（类型/大小/长宽的校验都在 Service 里，见 saveIcon）
            OaonesUpload.Item file = OaonesUpload.read(request, OaonesConst.APP_ICON_MAX_BYTES);
            return OaonesUtil.ok(appCenterService().saveIcon(
                    file == null ? "" : file.getName(),
                    file == null ? null : file.getBytes(),
                    operatorId));
        }
        if ("appc.desktop".equals(action)) {
            // 桌面启动台：任何登录人都能看（只下发启用的、且命中该登录人可见范围的应用，不含维护字段）
            List<Map<String, Object>> apps = appCenterService().desktopApps(operatorId);
            JSONObject data = new JSONObject();
            JSONArray rows = new JSONArray();
            rows.addAll(apps);
            data.put("apps", rows);
            data.put("canMaintain", appCenterService().canMaintain(operatorId));
            return OaonesUtil.ok(data);
        }
        if ("appc.entry".equals(action)) {
            return OaonesUtil.ok(appCenterService().entry(req(request, "id"), operatorId));
        }
        return null;
    }
}
