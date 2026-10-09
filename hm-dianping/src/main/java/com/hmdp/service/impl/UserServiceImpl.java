package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    // Redis 客户端。用 StringRedisTemplate（key/value 都是字符串），比通用 RedisTemplate 更方便读写和调试
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     * 流程：校验手机号格式 -> 生成 6 位随机码 -> 存入 Redis（2 分钟过期）-> 返回成功
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号格式；不合法直接返回失败
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        // 2. 生成 6 位数字验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 存入 Redis：key = login:code:手机号，value = 验证码，有效期 2 分钟（LOGIN_CODE_TTL）
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 真实项目这里会调用短信服务发送；学习环境只把验证码打印到控制台，方便自己测试
        System.out.println(code);
        log.debug("发送手机验证码成功,验证码: {} ", code);
        // 5. 返回成功
        return Result.ok();
    }

    /**
     * 登录
     * 流程：校验手机号 -> 校验验证码 -> 按手机号查用户（没有就自动注册）-> 生成 token 存 Redis -> 把 token 返回给前端
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 取出手机号并校验格式
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误!");
        }

        // 2. 从 Redis 取出该手机号对应的验证码，和用户提交的验证码比对
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cachecode == null || !cachecode.toString().equals(code)) {
            // 验证码为 null（没发过或已过期）或与用户输入不一致，都判为错误
            return Result.fail("验证码错误");
        }

        // 3. 按手机号查用户；查不到说明是新用户，自动注册一个
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 4. 生成随机 UUID 作为登录凭证 token
        String token = UUID.randomUUID().toString();
        // 5. 把用户转成 UserDTO（只保留 id、昵称、头像，不带手机号/密码等敏感信息），再转成 Map 以便按 Hash 存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                // 忽略值为 null 的字段，避免往 Redis 里塞空值
                .setIgnoreNullValue(true)
                // 所有字段值统一转成字符串，符合 StringRedisTemplate 的要求
                .setFieldValueEditor((filedName, fieldValue) -> fieldValue.toString()));
        // 6. 以 Hash 结构存入 Redis：key = login:token:token，value = 用户字段；并设置有效期 LOGIN_USER_TTL 分钟
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 7. 把 token 返回给前端；前端保存它，之后每个请求都会放在请求头 authorization 里带回来
        return Result.ok(token);
    }

    /**
     * 根据手机号创建新用户
     * 新用户默认昵称 = 固定前缀 + 10 位随机数字
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        // 保存到数据库
        save(user);
        return user;
    }


}
