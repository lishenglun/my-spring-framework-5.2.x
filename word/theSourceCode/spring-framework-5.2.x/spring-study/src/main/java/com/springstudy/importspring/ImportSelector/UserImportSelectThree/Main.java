package com.springstudy.importspring.ImportSelector.UserImportSelectThree;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;


/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 8:14 下午
 */
public class Main {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(UserImportSelectThreeConfig.class);
		context.refresh();
		UserThreeDao bean = context.getBean("getUserDaoImpl", UserThreeDao.class);
		if (bean instanceof Proxy) {
			System.out.println("是代理对象");
		}
		bean.proxyTest();
	}

}