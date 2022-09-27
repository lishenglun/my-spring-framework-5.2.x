package com.springstudy.aop.classroomProblem;

import com.springstudy.config.AppConfig;
import com.springstudy.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/29 10:44 下午
 */
public class ClassroomProblem1 {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(AppConfig.class);
		UserService bean = context.getBean(UserService.class);
		bean.invokeMethod("B");
	}

}