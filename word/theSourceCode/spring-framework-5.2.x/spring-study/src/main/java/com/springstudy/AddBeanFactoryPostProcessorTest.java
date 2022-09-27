package com.springstudy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/22 12:16 下午
 */
@Component
public class AddBeanFactoryPostProcessorTest implements BeanFactoryPostProcessor {

	public void addTest(){
		System.out.println("add test ...");
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

	}
}