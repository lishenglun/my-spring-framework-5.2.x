package com.springstudy.Initializethebean.autowireMode;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *  自动装配模型测试
 */
public class AutowireModeTest {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context=new ClassPathXmlApplicationContext("spring-autowire-mode-config.xml");
		AutowireModeA autowireModeA = context.getBean("autowireModeA", AutowireModeA.class);
		System.out.println(autowireModeA.getAutowireModeB());
	}


}