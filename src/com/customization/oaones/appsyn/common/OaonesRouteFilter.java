package com.customization.oaones.appsyn.common;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * OAones URL 路由过滤器：把 {@link OaonesRoutes} 的路由表接到容器上。
 *
 * <p>本过滤器**只按配置文件处理**：路由表里没写的地址一律原样放行
 * （包括 E9 平台页面、静态资源、接口），所以映射可以放心地写成 {@code /*}；
 * 处理失败的异常也一律吞掉并继续走后面的过滤器链——路由是“锦上添花”，
 * 绝不能因为一份配置写错把整个应用请求挡下来。</p>
 *
 * <p>注册方式见 {@code ecology/WEB-INF/resin-web-post.xml}（Resin 会自动 import 该文件）；
 * 非 Resin 容器把同样两条 {@code <filter>} / {@code <filter-mapping>} 加到
 * {@code WEB-INF/web.xml} 即可，类名相同。</p>
 */
public class OaonesRouteFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // 无需初始化参数：路由表从 ecology/WEB-INF/prop/oaones.appsyn.properties 的 route.* 项读
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws java.io.IOException, javax.servlet.ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            try {
                if (OaonesRoutes.handle((HttpServletRequest) request, (HttpServletResponse) response)) {
                    return;
                }
            } catch (Throwable t) {
                OaonesLog.error("URL 路由处理失败（按未命中继续）", t);
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 无资源需要释放
    }
}
