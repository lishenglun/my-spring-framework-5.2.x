package com.springstudy.msb.s_15.resolveBeforeInstantiation;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/5/5 6:10 下午
 */
public class Main {

	// 不做任何合并和修改工作，原生读取到的bd是GenericBeanDefinition
	// 还需要告诉它这个类到底是什么类型
	/**
	 * InstantiationAwareBeanPostProcessor接口：返回一个自定义的实例化bean来代替目标bean
	 * >>> 1、可以在这里面实现动态代理的代码，返回一个动态代理的对象，来代替要实例化的bean，就采用这个bean，后续不会再实例化bean对象了
	 * >>> 2、也可以返回一个普通的对象.如果返回普通的对象，那么方法调用的时候就不会像代理对象那样，被拦截，有前置处理逻辑了
	 *
	 * 源码实现处：AbstractAutowireCapableBeanFactory#createBean() ——> AbstractAutowireCapableBeanFactory#resolveBeforeInstantiation()
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-15-resolveBeforeInstantiation-config.xml");
		// 获取到的是代理对象！
		BeforeInstantiation bean = ac.getBean(BeforeInstantiation.class);
		bean.doSomeThing();
	}

}