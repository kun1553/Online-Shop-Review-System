package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

/**
 * 当前登录用户的持有者（基于 ThreadLocal 实现）
 * ThreadLocal 为每个线程保存一份独立数据，因此可以在同一次请求的任意位置
 * （Service、Controller 等）直接取到当前登录用户，无需层层传参。
 * 注意：请求结束后必须由拦截器调用 removeUser() 清理，防止线程复用导致数据串号。
 */
public class UserHolder {

    // 当前线程（即当前请求）对应的登录用户
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    // 保存当前登录用户
    public static void saveUser(UserDTO user) {
        tl.set(user);
    }

    // 获取当前登录用户；未登录时返回 null
    public static UserDTO getUser() {
        return tl.get();
    }

    // 清理当前登录用户（请求处理完成后调用）
    public static void removeUser() {
        tl.remove();
    }
}
