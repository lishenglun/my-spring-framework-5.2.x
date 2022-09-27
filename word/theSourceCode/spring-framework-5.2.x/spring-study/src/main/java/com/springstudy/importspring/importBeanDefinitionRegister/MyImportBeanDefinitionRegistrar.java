package com.springstudy.importspring.importBeanDefinitionRegister;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 *
 */
public class MyImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		/* 1、得到MyFactoryBean的通用bean定义  */
		// genericBeanDefinition是得到通用bean定义
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MyFactoryBean.class);
		AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();

		/**
		 * 2、为MyFactoryBean放入构造参数
		 * Spring获取「com.springstudy.dao.SystemDao」，然后转换为class对象，最后再传给这个构造方法，也就是「MyFactoryBean(Class<?> clazz)」
		 */
		beanDefinition.getConstructorArgumentValues().addGenericArgumentValue("com.springstudy.dao.SystemDao");

		/**
		 * 3、注册MyFactoryBean，名称为systemDao
		 * 这里有一个妙点：注册MyFactoryBean会执行getObject()，其中代理了「com.springstudy.dao.SystemDao」，
		 * 				 而systemDao所代表的也是「MyFactoryBean的getObject()获取到的对象」，
		 * 				 所以获取「context.getBean("systemDao", SystemDao.class)」得到的将是「MyFactoryBean的getObject()」的对象
		 * 				 	 也就是「com.springstudy.dao.SystemDao」的代理对象！
		 * 				 	 后续注入@SystemDao，也是「com.springstudy.dao.SystemDao」的代理对象！
		 */
		registry.registerBeanDefinition("systemDao", beanDefinition);
	}

}



