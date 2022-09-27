package com.springstudy.msb.s_08;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2021/12/13 10:43 下午
 */
public class MyClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {

	public MyClassPathXmlApplicationContext(String... configurations) {
		super(configurations);
	}

	/**
	 * 扩展点作用：
	 * 1、比如可以添加对一些属性的验证
	 */
	@Override
	protected void initPropertySources() {
		System.out.println("扩展initPropertySouece");
		// 必须得存在"abc"这个系统环境变量，比如在启动系统的时候需要-Dabc=xxx，就是必须得存在abc这个环境变量值，所以可以通过这个限制一下！
		// 如果没有，会报异常 org.springframework.core.env.MissingRequiredPropertiesException: The following properties were declared as required but could not be resolved: [abc]
		//
		//getEnvironment().setRequiredProperties("abc");
	}

	@Override
	protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
		super.setAllowBeanDefinitionOverriding(false);
		super.setAllowCircularReferences(false);
		super.customizeBeanFactory(beanFactory);
		super.addBeanFactoryPostProcessor(new MyBeanFactoryPostProcessor());
	}

}