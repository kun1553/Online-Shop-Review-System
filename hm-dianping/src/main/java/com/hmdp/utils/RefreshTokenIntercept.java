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
 * Token 刷新拦截器（order = 0，最先执行，拦截所有路径）
 * 职责：
 *   1. 从请求头 authorization 取出 token；
 *   2. 用 token 去 Redis 查询登录用户信息；
 *   3. 查到就把用户放进 ThreadLocal（UserHolder），并给 token 续期；
 *   4. 没带 token 或 Redis 查不到，也一律放行——"要不要拦截"交给后面的 LoginInterceptor 决定。
 * 好处：这个拦截器不阻断任何请求，只负责"能识别出用户就识别出来"，从而实现部分接口免登录、部分接口需登录。
 */
@Component
public class RefreshTokenIntercept implements HandlerInterceptor {

    // Redis 客户端，用来根据 token 查询用户信息并续期
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenIntercept(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 请求处理完成后调用：清理 ThreadLocal 里的用户信息，防止线程复用导致数据错乱
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }

    /**
     * 请求处理前调用
     * @return 始终返回 true（本拦截器只做解析和续期，不拦截请求）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头取出 token
        String token = request.getHeader("authorization");
        // 2. 没带 token：直接放行（可能是免登录接口，或未登录访问业务接口，后续交给 LoginInterceptor 处理）
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 3. 用 token 拼出 Redis key，取出该登录用户的字段（Hash）
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 4. Redis 里查不到（token 已过期或伪造）：也放行，同样交给 LoginInterceptor 决定是否拦截
        if (userMap.isEmpty()) {
            return true;
        }
        // 5. 查到用户：把 Hash 还原成 UserDTO，存入 ThreadLocal，供后续 Controller/Service 直接取用
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 6. 续期：每次访问都刷新 token 的有效期，实现"用户只要活跃就不会掉线"
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }
}
