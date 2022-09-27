/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Conventions;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for identifying {@link Configuration} classes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
abstract class ConfigurationClassUtils {

	// 如果是@Configuration标注的类，那么将属性标注为full
	public static final String CONFIGURATION_CLASS_FULL = "full";
	// 如果是非@Configuration标注的类，那么将属性标注为lite
	public static final String CONFIGURATION_CLASS_LITE = "lite";

	// CONFIGURATION_CLASS_ATTRIBUTE = org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass
	// 作为属性配置类型，标记属性的key
	public static final String CONFIGURATION_CLASS_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");

	// ORDER_ATTRIBUTE = org.springframework.context.annotation.ConfigurationClassPostProcessor.order
	// 配置属性配置类排序的属性key
	private static final String ORDER_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "order");


	private static final Log logger = LogFactory.getLog(ConfigurationClassUtils.class);

	// 定义set集合，用于存储标注配置类的注解
	private static final Set<String> candidateIndicators = new HashSet<>(8);

	static {
		candidateIndicators.add(Component.class.getName());
		candidateIndicators.add(ComponentScan.class.getName());
		candidateIndicators.add(Import.class.getName());
		candidateIndicators.add(ImportResource.class.getName());
	}


	/**
	 * Check whether the given bean definition is a candidate for a configuration class
	 * (or a nested component class declared within a configuration/component class,
	 * to be auto-registered as well), and mark it accordingly.
	 *
	 * 检查给定的BeanDefinition是否是配置类的候选者（或在配置组件类中声明的嵌套组件类，也将自动注册），并相应地标记它。
	 *
	 * @param beanDef               the bean definition to check
	 * @param metadataReaderFactory the current factory in use by the caller
	 * @return whether the candidate qualifies as (any kind of) configuration class ）—— 候选人是否有资格作为（任何类型的）配置类
	 *
	 * 🚩true：代表包含@Configuration、@Component、@ComponentScan、@Import、@ImportSource、@Bean中的某一个注解
	 * 🚩false：代表不包含
	 *
	 */
	public static boolean checkConfigurationClassCandidate/* 检查配置类候选 */(
			BeanDefinition beanDef, MetadataReaderFactory metadataReaderFactory) {

		// 获取当前BeanDefinition对应的类名称
		String className = beanDef.getBeanClassName();
		if (className == null || beanDef.getFactoryMethodName() != null) {
			return false;
		}

		/* 一、得到BeanDefinition当中描述的"类的元数据对象"(AnnotationMetadata，包含了类上面注解的信息) */

		/**
		 * AnnotationMetadata：注解元数据。怎么理解"注解元数据"呢？
		 * >>> 定义bean的时候，有BeanDefinition来存储相关的信息；
		 * >>> 定义注解的时候，注解里面也可以写属性值，AnnotationMetadata就是用来存储注解里面相关的属性值的
		 */
		AnnotationMetadata metadata/* 元数据 */;

		// ⚠️⚠️题外：通过注解注入的BD都是AnnotatedGenericBeanDefinition，实现了AnnotatedBeanDefinition
		// ⚠️⚠️题外：spring内部的BD是RootBeanDefinition，实现了AbstractBeanDefinition

		// 判断是否归属于AnnotatedBeanDefinition，以及当前className是否等于元数据包含的className
		/* 1、⚠️主要是为了匹配是不是注解生成的，是否有一些注解 */
		if (beanDef instanceof AnnotatedBeanDefinition
				&& className.equals(((AnnotatedBeanDefinition) beanDef).getMetadata().getClassName())) {
			/**
			 * 获取程序员自身定义的BeanDefinition的元数据信息
			 *
			 * 我们自己register()的bean是：AnnotatedGenericBeanDefinition ，AnnotatedGenericBeanDefinition implements AnnotatedBeanDefinition，
			 * 所以只有自己定义的bean，在「beanDef instanceof AnnotatedBeanDefinition」才成立
			 */
			// Can reuse the pre-parsed metadata from the given BeanDefinition... —— 可以重用来自给定 BeanDefinition 的预解析元数据...
			// ⚠️从当前bean定义信息中，获取元数据信息
			metadata = ((AnnotatedBeanDefinition) beanDef).getMetadata();
		}
		// 判断是否是spring中默认的BeanDefinition
		/* 2、普通的 */
		else if (beanDef instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) beanDef).hasBeanClass()) {
			/**
			 * 获取spring的BeanDefinition的元数据信息
			 */
			// Check already loaded Class if present... —— 检查已加载的类（如果存在）...
			// since we possibly can't even load the class file for this Class. —— 因为我们甚至可能无法加载这个类的类文件。
			// 获取当前bean对象的Class对象
			Class<?> beanClass = ((AbstractBeanDefinition) beanDef).getBeanClass();
			// 判断该类是否是指定类的子类，如果是就排除掉（排除掉，当前类是以下类型的！）
			if (BeanFactoryPostProcessor.class.isAssignableFrom/* 可分配自 */(beanClass) ||
					BeanPostProcessor.class.isAssignableFrom(beanClass) ||
					AopInfrastructureBean.class.isAssignableFrom(beanClass) ||
					EventListenerFactory.class.isAssignableFrom(beanClass)) {
				return false;
			}
			// 根据beanClass生成对应的AnnotationMetadata对象
			metadata = AnnotationMetadata.introspect(beanClass);
		}
		// 如果上述两种情况都不符合
		/* 3、其他的 */
		else {
			try {
				// 获取元数据读取器
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(className);
				// 通过"元数据读取器"获取注解元数据
				metadata = metadataReader.getAnnotationMetadata();
			} catch (IOException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Could not find class file for introspecting configuration annotations: " +
							className, ex);
				}
				return false;
			}
		}

		/*

		二、通过注解元数据(AnnotationMetadata)，获取和判断，是否包含了@Configuration、@Component、@ComponentScan、@Import、@ImportSource、@Bean中的某一个注解，

		（1）如果包含@Configuration，同时包含proxyBeanMethods属性，那么设置BeanDefinition的configurationClass属性为full

		（2）如果包含@Component、@ComponentScan、@Import、@ImportSource、@Bean中的某一个，就往BD里面设置属性值了，将configurationClass属性设置为lite

		 */

		/* 1、判断当前BD，是否存在@Configuration */
		// 获取BeanDefinition的注解元数据中的@Configuration标注的属性字典值
		Map<String, Object> config = metadata.getAnnotationAttributes(Configuration.class.getName());
		// 🌹如果包含@Configuration，且proxyBeanMethods属性为false(使用代理模式)，则将bean定义标记为full（设置BeanDefinition的configurationClass属性为full）
		if (config != null && !Boolean.FALSE.equals(config.get("proxyBeanMethods"))) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE/* configurationClass */, CONFIGURATION_CLASS_FULL/* full(全部) */);
		}
		/* 2、判断当前BD，是否包含@Component、@ComponentScan、@Import、@ImportSource、@Bean中的某一个 */
		else if (config != null || isConfigurationCandidate/* 是否是配置的候选者 */(metadata)) {

			/**
			 * 什么是配置的候选者？
			 * 我们在配置的时候，优先级最高的是@Configuration；但是里面没有@Configuration的话，
			 * 里面可能包含了@Component、@ComponentScan、@Import、@ImportSource、@Bean等东西
			 */

			/**
			 * 如果不存在存在@Configuration注解，但加了@Component、@ComponentScan、@Import、@ImportResource注解，则为BeanDefinition设置configurationClass属性为lite
			 * ⚠️注意：这里之所以要判断是否加了如上四个注解，是因为：
			 * 		1、当一个类加了「@Configuration」时，BeanDefinition的configurationClass属性已经被标注为full，那么当解析这个配置类时，就会去自动解析如上四个注解
			 * 		2、可是会存在另一种情况，就是一个类没有加@Configuration，但是加了如上四个注解，如果不单独标记(BeanDefinition的configurationClass属性设置为lite)这个类加了如上四个注解，那么就不会去解析如上四个注解，
			 * 			所以就需要判断一个类上是否存在如上四个注解，如果存在就单独标记加了如上四个注解，那么就会去解析一个类上的这四个注解
			 */
			// 🌹如果包含@Component、@ComponentScan、@Import、@ImportSource、@Bean中的某一个，就往BD里面设置属性值了，将configurationClass属性设置为lite
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE/* lite(部分) */);
		}
		/* 3、@Configuration、@Component、@ComponentScan、@Import、@ImportSource、@Bean，这些注解都不包含，直接返回false */
		else {
			return false;
		}

		/* 三、通过这个BeanDefinition的注解元数据，获取当前这个BeanDefinition上的@Order的排序值，设置到BeanDefinition的order属性中；如果不存在@Order，那么返回的就是null */

		// It's a full or lite configuration candidate... Let's determine the order value, if any. —— 这是一个完整或精简的配置候选...让我们确定订单价值，如果有的话。
		// 获取具体的执行顺序
		Integer order = getOrder(metadata);
		// 如果值不为空的话，那么直接设置值到具体的BD中
		if (order != null) {
			// 存在如果排序值，那么就设置到BeanDefinition的order属性中
			beanDef.setAttribute(ORDER_ATTRIBUTE/* order */, order);
		}

		return true;
	}

	/**
	 * Check the given metadata for a configuration class candidate
	 * (or nested component class declared within a configuration/component class).
	 *
	 * @param metadata the metadata of the annotated class
	 * @return {@code true} if the given class is to be registered for
	 * configuration class processing; {@code false} otherwise
	 */
	public static boolean isConfigurationCandidate(AnnotationMetadata metadata) {
		/* 1、检查BD是不是一个接口，如果是，直接返回 */

		// Do not consider an interface or an annotation... —— 不要考虑接口或注释...
		if (metadata.isInterface()) {
			return false;
		}

		/* 2、检查bean中是否包含@Component、@ComponentScan、@Import、@ImportResource中的任意一个 */

		// Any of the typical annotations found?
		for (String indicator : candidateIndicators) {
			// 当前类是否被candidateIndicators里面的注解修饰
			// @Component
			// @ComponentScan
			// @Import
			// @ImportResource
			if (metadata.isAnnotated(indicator)) {
				return true;
			}
		}

		// 如果不存在上述注解

		/* 3、检查是否有@Bean标注的方法 */

		// Finally, let's look for @Bean methods... —— 最后，让我们寻找@Bean 方法......
		try {
			// 是否包含@Bean标注的方法
			return metadata.hasAnnotatedMethods(Bean.class.getName());
		} catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to introspect @Bean methods on class [" + metadata.getClassName() + "]: " + ex);
			}
			return false;
		}
	}

	/**
	 * Determine the order for the given configuration class metadata.
	 *
	 * @param metadata the metadata of the annotated class
	 * @return the {@code @Order} annotation value on the configuration class,
	 * or {@code Ordered.LOWEST_PRECEDENCE} if none declared
	 * @since 5.0
	 */
	@Nullable
	public static Integer getOrder(AnnotationMetadata metadata) {
		// 如果存在@Order，那么就获取对应的value属性值
		// 如果不存在@Order，那么就返回null
		Map<String, Object> orderAttributes = metadata.getAnnotationAttributes(Order.class.getName());
		return (orderAttributes != null ? ((Integer) orderAttributes.get(AnnotationUtils.VALUE)) : null);
	}

	/**
	 * Determine the order for the given configuration class bean definition,
	 * as set by {@link #checkConfigurationClassCandidate}.
	 *
	 * @param beanDef the bean definition to check
	 * @return the {@link Order @Order} annotation value on the configuration class,
	 * or {@link Ordered#LOWEST_PRECEDENCE} if none declared
	 * @since 4.2
	 */
	public static int getOrder(BeanDefinition beanDef) {
		Integer order = (Integer) beanDef.getAttribute(ORDER_ATTRIBUTE);
		return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
	}

}
