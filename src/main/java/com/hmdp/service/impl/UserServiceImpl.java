package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        // 1. 验证手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号不对哦");
        }

        // 2. 生成验证码 并保存在session里面  => redis 里面
        String code = RandomUtil.randomNumbers(6);
        // session.setAttribute("code", code);
        // k = 前缀加手机号保证唯一
        // v = 验证码
        // 时间 和 单位
        String loginCodeKey = LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(loginCodeKey, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 3. 发送验证码到第三方平台
        log.debug("你的验证码为:{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1. 取出验证码和手机号
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();

        // 2. 验证会话的手机号和验证码  => redis
//        if (RegexUtils.isPhoneInvalid(phone)) {
//            return Result.fail("手机号不对哦");
//        }
//        Object cacheCode = session.getAttribute("code");
//        if (cacheCode == null || !cacheCode.toString().equals(code)) {
//            return Result.fail("验证码不对哦");
//        }
        // 取出redis里面存的code验证码
        String loginCodeKey = LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(loginCodeKey);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码不对哦");
        }

        // 3.根据手机号查用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 4. 插入这个用户
            user = createUserWithPhone(phone);

        }

        // 5. 保存到session中 => redis
        String token = UUID.randomUUID().toString();
        String tokenKey = LOGIN_USER_KEY + token;
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        HashMap<String, String> userMap = new HashMap<>();
        userMap.put("icon", userDTO.getIcon());
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("nickName", userDTO.getNickName());

        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
