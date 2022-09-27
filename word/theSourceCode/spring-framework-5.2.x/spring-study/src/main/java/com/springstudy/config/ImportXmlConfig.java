package com.springstudy.config;

import com.springstudy.autowire.AutoMember;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ImportResource;

/**
 * 注解导入spring-xml配置的方式
 */
@ImportResource("spring-config.xml")
public class ImportXmlConfig {


	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ImportXmlConfig.class);
		AutoMember autoMember = context.getBean("autoMember",AutoMember.class);
		System.out.println(autoMember);
	}


}