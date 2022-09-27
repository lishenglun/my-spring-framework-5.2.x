package com.springstudy.msb.s_02.bean;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2021/12/13 12:28 下午
 */
public class AFactoryBean implements FactoryBean<A> {

	@Override
	public A getObject() throws Exception {
		return new A();
	}

	@Override
	public Class<?> getObjectType() {
		return A.class;
	}

	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("tx.xml");
		A bean = (A) context.getBean("aFactoryBean");
		AFactoryBean aFactoryBean = (AFactoryBean) context.getBean("&aFactoryBean");
	}

}