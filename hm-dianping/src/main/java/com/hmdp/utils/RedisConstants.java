package com.hmdp.utils;

/**
 * Redis 的 key 前缀与有效期常量集中管理类
 * 说明：这里只定义"值"，单位在使用处指定（当前验证码 / token 均以分钟为单位）
 */
public class RedisConstants {

    // 登录验证码的 key 前缀，完整 key = login:code:手机号
    public static final String LOGIN_CODE_KEY = "login:code:";
    // 验证码有效期（分钟）
    public static final Long LOGIN_CODE_TTL = 2L;
    // 登录用户 token 的 key 前缀，完整 key = login:token:token
    public static final String LOGIN_USER_KEY = "login:token:";
    // 登录 token 有效期（分钟）
    public static final Long LOGIN_USER_TTL = 36000L;

    // 缓存空对象的过期时间（分钟），用于防止缓存穿透
    public static final Long CACHE_NULL_TTL = 2L;

    // 店铺缓存：key 前缀 + 有效期（分钟）
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    // 店铺缓存重建用的分布式锁：key 前缀 + 锁有效期（分钟）
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // 秒杀优惠券库存的 key 前缀
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    // 博客点赞用户的 key 前缀
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    // 关注推送（Feed 流）收件箱的 key 前缀
    public static final String FEED_KEY = "feed:";
    // 店铺地理位置的 key 前缀
    public static final String SHOP_GEO_KEY = "shop:geo:";
    // 用户签到的 key 前缀
    public static final String USER_SIGN_KEY = "sign:";
}
