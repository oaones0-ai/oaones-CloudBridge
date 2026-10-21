package com.customization.oaones.appsyn.common;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * 操作监控采集过滤器：把「登录 / 退出 / 菜单访问」交给 {@link OaonesOpLog} 落库。
 *
 * <p>为什么用过滤器：E9 的登录是统一登录页 + 会话（没有一处“登录成功”的页面可以挂代码），
 * 菜单点击也分散在平台各个前端模块里；而“谁、什么时间、访问了哪个菜单”只有“每个页面请求”
 * 这个位置能同时看到登录态与请求地址。过滤器只做被动观察：</p>
 * <ul>
 *   <li>不写响应、不改请求、不做跳转，命中与否都由 {@link OaonesOpLog} 内部判断；</li>
 *   <li>只处理“页面请求”（GET / POST + Accept: text/html + 非 XHR + 非静态资源），
 *       并且只有地址命中 E9 菜单表时才写日志，所以不会给接口与静态资源带来写库开销；</li>
 *   <li>任何异常一律吞掉（{@link OaonesOpLog#observe} 自带兜底），
 *       最坏情况只是漏一条日志，绝不影响业务请求；</li>
 *   <li>整体开关 {@code oaones.properties#op.log.enable=0} 可随时关闭（60 秒内生效）。</li>
 * </ul>
 *
 * <p>注册方式见 {@code ecology/WEB-INF/resin-web-post.xml}（Resin 自动 import 该文件）；
 * 非 Resin 容器把同样两条 {@code <filter>} / {@code <filter-mapping>} 加到
 * {@code WEB-INF/web.xml} 即可，类名相同。</p>
 */
public class OaonesOpLogFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // 无需初始化参数：开关与窗口从 ecology/WEB-INF/prop/oaones.properties 的 op.log.* 项读
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws java.io.IOException, javax.servlet.ServletException {
        if (request instanceof HttpServletRequest) {
            try {
                OaonesOpLog.observe((HttpServletRequest) request);
            } catch (Throwable ignore) {
                // 采集是“锦上添花”，绝不能因为一份日志把整个应用请求挡下来
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 无资源需要释放
    }
}
