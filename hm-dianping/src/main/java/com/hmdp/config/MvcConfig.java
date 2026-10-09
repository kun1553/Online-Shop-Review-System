package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenIntercept;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * SpringMVC 配置类：注册登录相关的两个拦截器
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // 拦截器需要用 StringRedisTemplate 去查 Redis，这里注入后在创建拦截器时传给它们的构造方法
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截器一：登录校验器，order = 1（后执行）
        // excludePathPatterns 里的接口无需登录即可访问：发验证码、登录、热门博客、店铺/店铺类型/优惠券相关
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate)).excludePathPatterns(
            "/user/code", "/user/login", "/blog/hot", "/shop/**", "/shop-type/**", "/voucher/**"
        ).order(1);
        // 拦截器二：token 刷新器，order = 0（先执行），addPathPatterns("/**") 表示拦截所有路径
        // 有 token 就解析出用户并续期，没有也放行；是否拦截由上面的登录校验器负责
        registry.addInterceptor(new RefreshTokenIntercept(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
