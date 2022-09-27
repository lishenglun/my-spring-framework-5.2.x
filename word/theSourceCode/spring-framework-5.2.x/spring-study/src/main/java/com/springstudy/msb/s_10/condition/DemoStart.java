package com.springstudy.msb.s_10.condition;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/26 11:03 上午
 */
public class DemoStart {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("msb/spring-10-condition-config.xml");
	}

}