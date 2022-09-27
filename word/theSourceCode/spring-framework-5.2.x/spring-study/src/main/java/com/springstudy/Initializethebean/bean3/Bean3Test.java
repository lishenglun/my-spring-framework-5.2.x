package com.springstudy.Initializethebean.bean3;

import com.springstudy.Initializethebean.obj.Bean3_One;
import com.springstudy.Initializethebean.obj.Bean3_Two;
import com.springstudy.Initializethebean.obj.ComponentInfo;
import com.springstudy.Initializethebean.obj.TemplateInfo;
import com.springstudy.config.Bean3Config_One;
import com.springstudy.config.Bean3Config_Two;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 实例化bean的三种方式测试：
 * 		构造方法
 * 		静态方法
 * 		普通方法
 */
public class Bean3Test {

	public static void main(String[] args) {
		/* xml */
		bean3_one();
		/* 注解 */
//		bean3_two();
	}

	/**
	 * 实例化bean的三种方式测试。方法二：注解的方式
	 */
	public static void bean3_two(){
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Bean3Config_Two.class);
		Bean3_Two beanThree = context.getBean("bean3_Two", Bean3_Two.class);
		System.out.println(beanThree);
		System.out.println("===============");
		ComponentInfo componentInfo = context.getBean("componentInfo", ComponentInfo.class);
		System.out.println(componentInfo);
		System.out.println("===============");
		TemplateInfo templateInfo = context.getBean("templateInfo", TemplateInfo.class);
		System.out.println(templateInfo);
	}


	/**
	 * 实例化bean的三种方式测试。方法一：xml的方式
	 */
	public static void bean3_one(){
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Bean3Config_One.class);
		Bean3_One beanThree = context.getBean("bean3_One", Bean3_One.class);
		System.out.println(beanThree);
		System.out.println("===============");
		ComponentInfo componentInfo = context.getBean("componentInfo", ComponentInfo.class);
		System.out.println(componentInfo);
		System.out.println("===============");
		TemplateInfo templateInfo = context.getBean("templateInfo", TemplateInfo.class);
		System.out.println(templateInfo);
	}

}