package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static net.sf.jsqlparser.schema.Sequence.ParameterType.CACHE;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Test
    void test() throws InterruptedException{
     Shop shop= shopService.getById(1L);
      cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1l,shop,10L, TimeUnit.SECONDS);
    }



}
