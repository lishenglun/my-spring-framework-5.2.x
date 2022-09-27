package com.springstudy.importspring.ImportSelector.UserImportSelectOne;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 使用ImportSelect的方式一：导入一个bd
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 8:05 下午
 */
public class Main {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		// @Import(UserImportSelectOne.class)
		context.register(UserImportSelectOneConfig.class);
		context.refresh();
		UserOne bean = context.getBean(UserOne.class);
		System.out.println(bean);
	}

}