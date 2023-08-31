package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
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

    /**
     * 实现发送验证码功能
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号不符合！");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到session
        //session.setAttribute("code",code);

        //4.保存验证码到redis ，以手机号为key
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,2, TimeUnit.MINUTES);

        //5.发送验证码
        log.info("手机验证码：" + code);

        //6.成功返回
        return Result.ok("发送验证码成功！");
    }

    /**
     * 实现登陆功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //1.检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号不符合！");
        }

        //2.校验验证码
        //2.1 从Redis中获得验证码

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            //2.2获取提交的验证码
        String code = loginForm.getCode();
        //2.3与提交的验证码校对
        if (cacheCode == null || !cacheCode.equals(code))
        {
                //2.3.1如果不一致或者等于空，则报错
            return Result.fail("验证码错误");
        }

        //3.一致，根据手机号查询用户 select * from tb_user where phone = ?
            //可以使用ServiceImpl自带的query（）查询
        User user = query().eq("phone", phone).one();

        //3.判断用户是否存在
        if (user == null){
            //3.1如果不存在，则创建新用户，保存到数据库
           user = createUserWithPhone(phone); //创建后还需要返回user出来，以保存到session
        }

        //TODO: 3.2保存用户到Redis
        //3.3生成随机token，作为登陆令牌 使用UUID进行生成
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY + token;

        //3.4将User对象转为HashMap存储, 使用BeanUtil.BeanToMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString())
                );

        //3.5存储用户信息到redis，使用hash结构
        //HashMap<String,String> usermap = new HashMap<>();
        //usermap.put(nickName,)
        stringRedisTemplate.opsForHash().putAll(tokenKey,usermap);
        //3.6  token设置有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //4.返回token给客户端
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));

        //2.保存用户
        save(user); //也是使用mp的
        return user;

    }
}
