package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * Redis 缓存工具类，封装了缓存相关的通用操作：
 *   1. set                  —— 写入普通缓存（带 TTL，即"缓存过期"）
 *   2. setWithLogicalExpire —— 写入逻辑过期缓存
 *   3. queryWithPassThrough —— 查询（解决缓存穿透：未命中查库、库空则缓存空值）
 *   4. queryWithLogicalExpire —— 查询（解决缓存击穿，逻辑过期 + 异步重建）
 * 其中查询方法用泛型 + 函数式接口，把"怎么查数据库"交给调用方传入，从而复用一套逻辑。
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 缓存重建线程池（逻辑过期方案用：让抢到锁的线程异步去重建，不阻塞请求）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ===================== 写入 =====================

    /**
     * 写入普通缓存（带 TTL）：把对象序列化成 JSON 存进 Redis，并设置过期时间
     * @param key   缓存 key
     * @param value 缓存对象
     * @param time  过期时间数值
     * @param unit  时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 写入逻辑过期缓存：Redis 不设 TTL（永不物理过期），
     * 而是把"逻辑过期时间"塞进 value 里，查询时再判断是否过期
     * @param key   缓存 key
     * @param value 缓存对象
     * @param time  逻辑过期时间数值
     * @param unit  时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 用 RedisData 包装：data 存真实数据，expireTime 存逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入 Redis，注意这里不设置 TTL
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // ===================== 查询 =====================

    /**
     * 查询缓存（解决缓存穿透）
     * 流程：查缓存 -> 命中真实数据返回 -> 命中空值返回 null -> 未命中查库 -> 库空写空值 / 库有写缓存
     * @param keyPrefix 缓存 key 前缀（完整 key = 前缀 + id）
     * @param id        业务 id
     * @param type      返回值的 Class，用于 JSON 反序列化
     * @param dbFallback 查数据库的回调（由调用方传入具体查询逻辑）
     * @param time      缓存过期时间数值
     * @param unit      时间单位
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 命中真实数据，反序列化后返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 3. 命中空值标记（""），说明之前查过、数据库里没有，直接返回 null
        if (json != null) {
            return null;
        }
        // 4. 未命中，调用回调查数据库
        R r = dbFallback.apply(id);
        // 5. 数据库不存在：缓存空值（防穿透），返回 null
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. 数据库存在：写入缓存并返回
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 查询缓存（解决缓存击穿，逻辑过期方案）
     * 流程：查缓存 -> 未命中直接返回 null（需预热）-> 命中判断逻辑过期 ->
     *       未过期直接返回；已过期则返回旧数据的同时，异步开线程重建缓存
     * 需要配合 setWithLogicalExpire 预热数据使用
     * @param keyPrefix 缓存 key 前缀
     * @param id        业务 id
     * @param type      返回值 Class
     * @param dbFallback 查数据库回调
     * @param time      重建后的逻辑过期时间数值
     * @param unit      时间单位
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 未命中（数据没预热），直接返回 null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. 命中，先反序列化成 RedisData，再取出真实数据
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 未过期：直接返回缓存里的数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 5. 已过期：尝试获取互斥锁，抢到的线程异步重建缓存
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存（重新设置逻辑过期时间）
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6. 无论是否触发重建，都先把旧的缓存数据返回给用户（不阻塞）
        return r;
    }

    // ===================== 互斥锁 =====================

    /**
     * 尝试获取互斥锁：SETNX 抢占，并设置过期时间防止死锁
     * @return 是否拿到锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁：删除锁 key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
