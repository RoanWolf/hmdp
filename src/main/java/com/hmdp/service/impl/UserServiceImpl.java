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
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号不对哦");
        }

        // 2. 生成验证码 并保存在session里面
        String code = RandomUtil.randomNumbers(6);
        session.setAttribute("code", code);

        // 3. 发送验证码到第三方平台
        log.debug("你的验证码为:{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 取出验证码和手机号
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();

        // 2. 验证会话的手机号和验证码
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号不对哦");
        }
        Object cacheCode = session.getAttribute("code");
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码不对哦");
        }

        // 3.根据手机号查用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 4. 插入这个用户
            user = createUserWithPhone(phone);

        }

        // 5. 保存到session中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);
        return Result.ok("登录成功");
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
