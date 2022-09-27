package com.springstudy.msb.s_11;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/28 11:31 下午
 */
public class TestMessageSourceMain {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("msb/spring-11-initMessageSource-config.xml");

	}

}