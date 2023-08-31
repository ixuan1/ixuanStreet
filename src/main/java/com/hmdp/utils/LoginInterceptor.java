package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * 登陆状态拦截器
 * 不能加Competent，拦截器是一个非常轻量级的组件，只有在需要时才会被调用，
 * 并且不需要像控制器或服务一样在整个应用程序中可用。
 * 因此，将拦截器声明为一个Spring Bean可能会引导致性能下降。
 * 在这种手动创建的类中，需要自己写构造器,不能使用 @AutoWire 和@Resource等注解
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {//1.实现HandlerInterceptor

    //StringRedisTemplate 需要从配置拦截器配置那边注册好再使用构造器传递过来
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //开始前
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        log.info("拦截到请求"+ requestURI);
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        //2.使用token作为key从redis中获取用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> usermap = stringRedisTemplate.opsForHash().entries(key);
        //2.1将获得的map转换为UserDto
        UserDTO userDTO = BeanUtil.fillBeanWithMap(usermap, new UserDTO(), false);

        //3.判断取得中的用户map是否存在
        if (usermap.isEmpty()){
            //4.不存在，拦截 ，
            response.setStatus(401);
            log.info("未登录,已拦截请求" + requestURI );
            return false;
        }
        //5.存在保存用户在ThreadLocal中，供使用
        UserHolder.saveUser(userDTO);
        //6.放行
        //7.刷新有效期
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        log.info("已放行");
        return true;
    }

    //结束后
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
