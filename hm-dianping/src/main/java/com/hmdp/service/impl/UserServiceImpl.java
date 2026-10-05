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
@Resource
private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
    if (RegexUtils.isPhoneInvalid(phone)) {
        return Result.fail("手机格式错误");
    }
    String code = RandomUtil.randomNumbers(6);
    stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
    System.out.println(code);
    log.debug("发送手机验证码成功,验证码: {} ",code);
    return Result.ok();
}

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

    String phone = loginForm.getPhone();
     if(RegexUtils.isPhoneInvalid(phone))
     {
     return Result.fail("手机格式错误!");
     }


     String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
     String code = loginForm.getCode();
     if(cachecode == null || !cachecode.toString().equals(code))
{
      return  Result.fail("验证码错误");
}

     User user=query().eq("phone",phone).one();
     if(user==null)
{
    user=createUserWithPhone(phone);
}
     String token=UUID.randomUUID().toString();
     UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
     Map<String, Object> userMap= BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().
             setIgnoreNullValue(true)
             .setFieldValueEditor((filedName,fieldValue)->fieldValue.toString()));
     String tokenKey = LOGIN_USER_KEY + token;
     stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
     stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

      return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
    User user=new User();
    user.setPhone(phone);
    user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
    save(user);
    return user;
    }


}
