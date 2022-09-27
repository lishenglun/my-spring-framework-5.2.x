package com.springstudy.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/30 10:24 下午
 */
@Configuration
@ComponentScan("com.springstudy.aop")
@EnableAspectJAutoProxy //开启切面编程，需要开启切面编程，@Aspect才有用
public class AopConfig {

}