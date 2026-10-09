package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 * 缓存相关的通用逻辑（穿透、逻辑过期、互斥锁、写入等）都抽到了 {@link CacheClient}，
 * 这里只负责按业务调用工具类 + 组织结果。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    // 缓存工具类：封装了缓存穿透、逻辑过期、写入等通用操作
    @Resource
    private CacheClient cacheClient;

    // 更新店铺时需要删除缓存，保留 StringRedisTemplate
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据 id 查询店铺（对外入口）
     * 这里默认走【缓存穿透】方案；若要用【缓存击穿（逻辑过期）】方案，
     * 请改用下面注释掉的那行，并先用 saveShopRedis 预热数据
     */
    @Override
    public Result queryById(Long id) {
        // 方案一：缓存穿透（默认启用）
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 方案二：缓存击穿（逻辑过期）—— 需要预热数据，按需切换
        // Shop shop = cacheClient.queryWithLogicalExpire(
        //         CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    /**
     * 更新店铺：先更新数据库，再删除缓存
     */
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 预热：把店铺按【逻辑过期】格式写入缓存，供 queryWithLogicalExpire 使用
     * @param id 店铺id
     * @param expireSeconds 逻辑过期秒数
     */
    public void saveShopRedis(Long id, Long expireSeconds) {
        //查店铺
        Shop shop = getById(id);
        //按逻辑过期格式写入缓存
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + id, shop, expireSeconds, TimeUnit.SECONDS);
    }
}
