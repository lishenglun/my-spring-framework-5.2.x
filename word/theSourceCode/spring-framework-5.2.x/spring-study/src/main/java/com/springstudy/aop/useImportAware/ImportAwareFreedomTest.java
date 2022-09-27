package com.springstudy.aop.useImportAware;

import com.springstudy.anno.ImportAwareAnnotation;
import com.springstudy.config.AppConfig;
import com.springstudy.config.ImportAwareTestConfiguration;
import com.springstudy.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/29 11:21 下午
 */
public class ImportAwareFreedomTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(ImportAwareTestConfiguration.class);
		ImportAwareFreedom importAwareFreedom = (ImportAwareFreedom) context.getBean("importAwareFreedom");
		System.out.println(importAwareFreedom.importAwareAnnotationValue);
	}

}