package com.springstudy.importspring.importBeanDefinitionRegister;

import com.springstudy.dao.SystemDao;
import com.springstudy.service.impl.SystemServiceImpl;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/7 8:32 下午
 */
public class Main {

	public static void main(String[] args) {
		testMyImportSelect();
	}

	public static void testMyImportSelect() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(MyImportBeanDefinitionRegistrarConfig.class);
		context.refresh();
		SystemDao bean = context.getBean("systemDao", SystemDao.class);
		//System.out.println(bean);
		System.out.println("===========");
		SystemServiceImpl bean1 = context.getBean(SystemServiceImpl.class);
		bean1.getUserInfo();
	}

}