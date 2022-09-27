package com.springstudy.importspring.ImportSelector.UserImportSelectTwo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 8:08 下午
 */
public class Main {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(UserImportSelectTwoConfig.class);
		context.refresh();
		UserTwo bean = context.getBean(UserTwo.class);
		System.out.println(bean);
	}

}