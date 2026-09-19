package org.example.facerecognitionspringboot.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.facerecognitionspringboot.utils.JwtUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 1. 放行跨域预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 从请求头中取出 Token
        String token = request.getHeader("Authorization");

        // 3. 验证 Token 是否存在并合法
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            if (JwtUtils.verifyToken(token)) {

                // 3.1 管理接口需要 admin 角色
                String uri = request.getRequestURI();
                if (uri.startsWith("/api/admin")) {
                    String role = JwtUtils.getRoleFromToken(token);
                    if (!"admin".equals(role)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(
                                "{\"success\":false, \"message\":\"权限不足，需要管理员身份\"}");
                        return false;
                    }
                }
                return true;
            }
        }

        // 4. Token 无效或不存在
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false, \"message\":\"登录凭证无效或已过期，请重新登录\"}");
        return false;
    }
}
