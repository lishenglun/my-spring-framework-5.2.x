package com.springstudy.msb.s_15.supplier;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/7 9:31 上午
 */
public class Main {

	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-15-supplier-config.xml");
		User user = ac.getBean("user", User.class);
		System.out.println(user.getUsername());
	}

}