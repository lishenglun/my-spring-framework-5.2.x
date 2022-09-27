package com.springstudy.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/11/24 5:40 下午
 */
public class BaSaiNuoNaRegister implements BeanDefinitionRegistryPostProcessor {

//	@Override
//	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
//
//	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		System.out.println("=============postProcessBeanDefinitionRegistry===========");

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(BaSaiNuoNa.class);
		builder.setLazyInit(true);
		//设置scope,为单例类型
		builder.setScope(BeanDefinition.SCOPE_SINGLETON);
		//设置自动装配模式
		builder.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_NO);
		builder.addPropertyReference("puJinDaDi","puJinDaDi");
		AbstractBeanDefinition baSaiNuoNaBeanDefinition = builder.getBeanDefinition();
		registry.registerBeanDefinition("baSaiNuoNa", baSaiNuoNaBeanDefinition);


		BeanDefinitionBuilder puJinDaDi = BeanDefinitionBuilder.genericBeanDefinition(PuJinDaDi.class);
		puJinDaDi.setLazyInit(true);
		//设置scope,为单例类型
		puJinDaDi.setScope(BeanDefinition.SCOPE_SINGLETON);
		//设置自动装配模式
		puJinDaDi.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_NO);
		puJinDaDi.addPropertyReference("baSaiNuoNa","baSaiNuoNa");
		AbstractBeanDefinition puJinDaDiBeanDefinition = puJinDaDi.getBeanDefinition();
		registry.registerBeanDefinition("puJinDaDi", puJinDaDiBeanDefinition);


	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("=============postProcessBeanFactory===========");
	}
}
