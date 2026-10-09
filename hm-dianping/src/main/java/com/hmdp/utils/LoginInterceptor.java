package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录校验拦截器（order = 1，在 RefreshTokenIntercept 之后执行）
 * 职责：判断当前请求是否已登录，登录了就放行，没登录返回 401。
 * 因为前面的 RefreshTokenIntercept 已经根据请求头 token 把用户放进了 UserHolder，
 * 所以这里只需要看 UserHolder 里有没有用户即可。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    // 保留该字段（历史上由它直接查 Redis 做校验）。当前校验只看 UserHolder，这里暂未使用
    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 请求处理完成后调用：清理 ThreadLocal 里的用户信息
     * 目的是防止 Tomcat 线程复用，导致上一个请求的用户残留到下一个请求
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }

    /**
     * 请求处理前调用
     * @return true 放行；false 拦截（同时设置响应状态码 401）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // UserHolder 里没有用户 => 未登录，返回 401 并拦截本次请求
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        // 已登录，放行
        return true;
    }
}
