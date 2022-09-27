package com.springstudy.msb.s_13.selfConverter;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/30 5:28 下午
 */
public class Main {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("msb/spring-13-converter-config.xml");
	}

}