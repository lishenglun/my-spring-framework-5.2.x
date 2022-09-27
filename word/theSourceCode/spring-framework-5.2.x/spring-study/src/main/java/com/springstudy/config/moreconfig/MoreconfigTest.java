package com.springstudy.config.moreconfig;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 测试spring当中是否可以有多个配置类。
 * 结论：可以有多个配置类！
 */
public class MoreconfigTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringConfig1.class);
		SpringConfig1 springConfig1 = context.getBean(SpringConfig1.class);
		System.out.println(springConfig1);
		SpringConfig2 springConfig2 = context.getBean(SpringConfig2.class);
		System.out.println(springConfig2);
	}

}