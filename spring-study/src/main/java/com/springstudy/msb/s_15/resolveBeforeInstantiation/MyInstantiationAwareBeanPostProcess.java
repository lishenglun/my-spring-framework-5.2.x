package com.springstudy.msb.s_15.resolveBeforeInstantiation;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.core.Ordered;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/5/5 6:00 下午
 */
public class MyInstantiationAwareBeanPostProcess implements InstantiationAwareBeanPostProcessor, Ordered {

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		// 1、可以在这里面实现动态代理的代码，返回一个动态代理的对象，来代替要实例化的bean，就采用这个bean，后续不会再实例化bean对象了
		// 2、也可以返回一个普通的对象.如果返回普通的对象，那么方法调用的时候就不会像代理对象那样，被拦截，有前置处理逻辑了
		System.out.println("beanName：" + beanName + "--------执行，实例化之前的，方法");
		if (beanClass == BeforeInstantiation.class) {
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(beanClass);
			enhancer.setCallback(new MyMethodInterceptor());
			BeforeInstantiation beforeInstantiation = (BeforeInstantiation) enhancer.create();
			System.out.println("创建代理对象：" + beforeInstantiation);
			return beforeInstantiation;
		}
		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		System.out.println("beanName：" + beanName + "--------执行，实例化之后的，方法");
		return false;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("beanName：" + beanName + "--------执行，初始化之前的，方法");
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("beanName：" + beanName + "--------执行，初始化之后的，方法");
		return bean;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
		System.out.println("beanName：" + beanName + "--------执行，Properties的，方法");
		return pvs;
	}

	@Override
	public int getOrder() {
		return 0;
	}

}