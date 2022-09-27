package com.springstudy.book2.chapter7_aop;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/13 4:46 下午
 */
public class Aop7_Main {

	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("book2/chapter7_aop/spring-aop-config.xml");
		TestBean test = (TestBean) ac.getBean("test");
		test.test();
	}

}