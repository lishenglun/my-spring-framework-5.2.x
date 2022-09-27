package com.springstudy.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/22 11:37 下午
 */
@ComponentScan({"com.springstudy.dao.impl","com.springstudy.service.impl"})
/**
 * @Configuration：
 * 		加了@Configuration，则此类会被标注为Full
 * 		没有加@Configuration，则此类会被标注为lite
 *
 * 		被标注为Full,则会通过Cglib动态代理AppConfig对象
 * 		如果是被标注为lite，则是普通AppConfig对象
 */
public class AppConfig {

}