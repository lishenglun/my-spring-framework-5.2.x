package com.springstudy.msb.s_09;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description BeanFactoryPostProcessor
 * @date 2022/4/23 12:04 上午
 */
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// SingletonBeanRegistry singletonBeanRegistry = beanFactory;
		// 注册单例！
		// singletonBeanRegistry.registerSingleton();
	}

}