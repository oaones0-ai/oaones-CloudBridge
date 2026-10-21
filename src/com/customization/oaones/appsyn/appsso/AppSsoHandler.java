package com.customization.oaones.appsyn.appsso;

/**
 * 应用单点登录实现接口 —— 「应用中心」指向的“具体类”都必须实现本接口。
 *
 * <p>在应用列表（{@code /oaones/appCenter/AppList.jsp}）里给某个应用填写
 * {@code sso_class}（本接口实现类的全限定名）后，员工在桌面启动台点击该应用时，
 * 系统会把当前 OA 登录人交给该实现类，由它完成<b>对对方系统的身份认证</b>
 * （生成签名票据 / 一次性令牌 / 免登录地址等），并返回最终要跳转的地址。</p>
 *
 * <p>实现约定：</p>
 * <ol>
 *   <li>类的全限定名填写在应用记录里，类必须<b>有无参构造函数</b>（系统按需实例化，实例按类名缓存）；</li>
 *   <li>{@link #loginUrl(AppSsoContext)} 只负责“生成带身份凭据的地址”，不要在这里做页面跳转；</li>
 *   <li>不做单点登录的应用把 {@code sso_class} 留空即可，系统直接打开应用默认地址。</li>
 * </ol>
 *
 * <p>“怎么实现”由各应用自己决定（本模块只约定入口），例如：</p>
 * <pre>
 * public class JdySsoHandler implements AppSsoHandler {
 *     public String loginUrl(AppSsoContext ctx) throws Exception {
 *         String ticket = ...按对方接口用 ctx.getOaLoginId() 换 ticket...;
 *         return ctx.getAppUrl() + "?ticket=" + URLEncoder.encode(ticket, "UTF-8");
 *     }
 *     public String describe() { return "简道云：/api/sso/ticket 按登录名换一次性票据"; }
 * }
 * </pre>
 */
public interface AppSsoHandler {

    /**
     * 生成该应用带“对方身份认证凭据”的最终访问地址。
     *
     * @param ctx 当前 OA 登录人与应用配置（见 {@link AppSsoContext}）
     * @return 最终要跳转的访问地址（http(s) 绝对地址或站内相对地址，不能为空）
     * @throws Exception 认证失败 / 对方接口异常（页面会提示并回退到应用默认地址）
     */
    String loginUrl(AppSsoContext ctx) throws Exception;

    /**
     * 实现类自述（展示在应用列表的“单点登录”一列，便于维护人知道这个类做什么）。
     *
     * @return 一句话说明；返回空串表示不展示
     */
    String describe();
}
