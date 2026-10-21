package com.customization.oaones.appsyn.appsso;

import com.customization.oaones.appsyn.common.OaonesLog;
import com.customization.oaones.appsyn.common.OaonesUtil;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 单点登录实现类注册表：按类名加载 / 缓存 {@link AppSsoHandler} 实现类实例。
 *
 * <p>应用列表里的 {@code sso_class} 只是**类名字符串**，真正的类由本类反射加载：</p>
 * <ul>
 *   <li>{@link #check(String)}：保存前的校验（类是否存在、是否实现 AppSsoHandler、能否实例化），
 *       返回空串表示可用，否则返回给维护人看的原因；</li>
 *   <li>{@link #resolve(String)}：取实现类实例（按类名缓存，失败返回 null 并记日志）。</li>
 * </ul>
 *
 * <p>注意：实现类由客户自行编译并放进 ecology 的 classbean / WEB-INF/lib，
 * 新增类之后需要重启 E9（与 Mapper 注册表同理）。</p>
 */
public final class AppSsoRegistry {

    /** 已加载的实现类实例（类名 → 实例）；加载失败的类名不缓存，避免“改好类再试”永远失败。 */
    private static final ConcurrentHashMap<String, AppSsoHandler> CACHE =
            new ConcurrentHashMap<String, AppSsoHandler>();

    private AppSsoRegistry() {
    }

    /**
     * 保存前校验：返回空串表示该类可用；否则返回原因。
     *
     * @param className 实现类全限定名（空串 = 不使用单点登录，视为合法）
     */
    public static String check(String className) {
        String name = OaonesUtil.s(className).trim();
        if (name.length() == 0) {
            return "";
        }
        try {
            Class<?> clazz = Class.forName(name);
            if (!AppSsoHandler.class.isAssignableFrom(clazz)) {
                return "该类不是单点登录实现类：请实现 " + AppSsoHandler.class.getName() + " 接口";
            }
            clazz.getConstructor();
            return "";
        } catch (ClassNotFoundException e) {
            return "找不到该类（请确认类已编译进 ecology/classbean 或 WEB-INF/lib，并重启 E9）：" + name;
        } catch (NoSuchMethodException e) {
            return "该类缺少无参构造函数，无法实例化：" + name;
        } catch (Throwable t) {
            return "该类不可用（" + t.getClass().getSimpleName() + "）：" + name;
        }
    }

    /**
     * 取实现类实例（按类名缓存）；类名为空或加载失败时返回 null。
     */
    public static AppSsoHandler resolve(String className) {
        String name = OaonesUtil.s(className).trim();
        if (name.length() == 0) {
            return null;
        }
        AppSsoHandler cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        String reason = check(name);
        if (reason.length() > 0) {
            OaonesLog.error("加载单点登录实现类失败 " + name + "：" + reason, null);
            return null;
        }
        try {
            AppSsoHandler handler = (AppSsoHandler) Class.forName(name).getConstructor().newInstance();
            CACHE.put(name, handler);
            OaonesLog.step("已加载应用单点登录实现类 " + name);
            return handler;
        } catch (Throwable t) {
            OaonesLog.error("实例化单点登录实现类失败 " + name, t);
            return null;
        }
    }
}
