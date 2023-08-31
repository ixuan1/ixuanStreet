package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

//1.加注解
@Configuration
public class MvcConfig implements WebMvcConfigurer { // 2.实现WebMvcConfigurer接口
    //3.重写addInterceptors（）方法

    //传递给登陆拦截器使用
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //4.注册拦截器
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate)) //13727294072
                .excludePathPatterns(   //excludePathPatterns：排除不需要拦截的路径
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                );
    }
}
