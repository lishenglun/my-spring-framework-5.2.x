package com.springstudy.autowire;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/30 9:51 上午
 */
public class AutowireTest {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring-config.xml");
		AutoUser bean = context.getBean("autoUser",AutoUser.class);
		System.out.println(bean.autoOrder);
//		System.out.println(bean.autoMember);
	}

}