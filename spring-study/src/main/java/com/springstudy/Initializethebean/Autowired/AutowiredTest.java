package com.springstudy.Initializethebean.Autowired;

import com.springstudy.config.AutowiredConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 9:21 下午
 */
public class AutowiredTest {

	public static void main(String[] args) {
		testAutowired2();
	}

	public static void testAutowired2(){
		ClassPathXmlApplicationContext context=new ClassPathXmlApplicationContext("spring-autowired-config.xml");
		A1 a1 = context.getBean("a1", A1.class);
		System.out.println(a1.getA2());
	}

	public static void testAutowired(){
		AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(AutowiredConfig.class);
		A1 a1 = context.getBean("a1", A1.class);
		System.out.println(a1.getA2());
	}
}