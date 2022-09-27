package com.springstudy.msb.other.annotation_bean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @Bean的解析过程
 * @date 2022/7/8 8:45 下午
 */
public class Main {

	public static void main(String[] args) {
		t2();
	}

	public static void t1(){
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
		ac.register(Config.class);
		ac.refresh();
		MyBean bean = ac.getBean(MyBean.class);
		//System.out.println(bean);
		ac.refresh();
		//GetBeanTestObject bean = ac.getBean(GetBeanTestObjecCllass);
		//System.out.println(bean);
	}

	public static void t2(){
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("msb/other.xml");
		GetBeanTestObject bean1 = ac.getBean("GetBeanTestObject",GetBeanTestObject.class);
		GetBeanTestObject bean2 = ac.getBean(GetBeanTestObject.class);
	}

}