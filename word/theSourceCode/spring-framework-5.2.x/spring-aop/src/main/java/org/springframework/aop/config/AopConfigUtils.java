/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.aop.config;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling registration of AOP auto-proxy creators.
 *
 * <p>Only a single auto-proxy creator should be registered yet multiple concrete
 * implementations are available. This class provides a simple escalation protocol,
 * allowing a caller to request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 * @see AopNamespaceUtils
 */
public abstract class AopConfigUtils {

	/**
	 * The bean name of the internally managed auto-proxy creator.
	 */
	public static final String AUTO_PROXY_CREATOR_BEAN_NAME =
			"org.springframework.aop.config.internalAutoProxyCreator";

	/**
	 * 自动代理创建器的优先级顺序列表
	 *
	 * 题外：从{@link AopConfigUtils#registerOrEscalateApcAsRequired}中可以得出，"自动代理创建器"在"自动代理创建器的优先级顺序列表"，索引顺序大的，优先级越高。
	 * >>> 也就是越往后面添加的，优先级顺序越大！越在前面添加的，优先级越小！
	 *
	 * Stores the auto proxy creator classes in escalation order. —— 按优先级顺序存储自动代理创建器类
	 */
	private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<>(3);

	static {
		// Set up the escalation list... —— 设置优先级列表...
		APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
		// 优先级最大
		APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class);
	}


	/**
	 * 注册"自动代理创建器bd" = InfrastructureAdvisorAutoProxyCreator
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 */
	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAutoProxyCreatorIfNecessary(registry, null);
	}

	/**
	 * 注册"自动代理创建器bd" = InfrastructureAdvisorAutoProxyCreator
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 */
	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);
	}

	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAutoProxyCreatorIfNecessary(registry, null);
	}

	/**
	 * 如果工厂中不存在"自动代理创建器bd"，则注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 *
	 * @param registry
	 * @param source
	 * @return
	 */
	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {
		/**
		 * 1、后面在AOP生成动态代理对象的时候，需要一个自动代理创建器，来为bean对象，创建对应的动态代理对象，当前采用的是AspectJAwareAdvisorAutoProxyCreator
		 *
		 * 2、AspectJAwareAdvisorAutoProxyCreator间接实现BeanPostProcessor
		 */
		// 如果工厂中不存在"自动代理创建器bd"，则注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")
		// key = org.springframework.aop.config.internalAutoProxyCreator
		return registerOrEscalateApcAsRequired/* 根据需要注册或升级Apc */(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		// 如果工厂中不存在"自动代理创建器bd"，注册AnnotationAwareAspectJAutoProxyCreator bd为"自动代理创建器bd"
		// key = org.springframework.aop.config.internalAutoProxyCreator
		return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
	}

	/**
	 * 往"自动代理创建器bd"中添加proxyTargetClass属性，属性值为true
	 *
	 * 1、proxy-target-class：是否强制使用cglib代理。设置为true，代表强制使用cglib代理
	 *
	 * Spring AOP部分使用JDK动态代理或者CGLIB来为目标对象创建代理(建议尽量使用JDK的动态代理)。
	 * 如果被代理的目标对象实现了至少一个接口，则会使用JDK动态代理。所有该目标类型实现的接口都将被代理。若该目标对象没有实现任何接口，则创建一个CGLIB代理。
	 * 如果你希望强制使用CGLIB代理(例如希望代理目标对象的所有方法，而不只是实现自接口的方法)，那也可以。但是需妥考虑以下两个问题：
	 * （1）无法通知( advise ) Final 方法，因为它们不能被覆写。
	 * （2）需要将 CGLIB二进制友行包放在 classpath下面
	 */
	public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
		// 判断是否包含org.springframework.aop.config.internalAutoProxyCreator名称的bd
		// 也就是判断是否包含"自动代理创建器bd"
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */)) {
			// 获取org.springframework.aop.config.internalAutoProxyCreator名称的bd
			// 也就是获取"自动代理创建器bd"
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			// 往"自动代理创建器bd"中添加proxyTargetClass属性，属性值为true
			definition.getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
		}
	}

	/**
	 * 往"自动代理创建器bd"中添加exposeProxy属性，属性值为true
	 *
	 * 1、expose-proxy：解决目标对象内部的自我调用，无法实施切面中的增强的问题。设置为true，即代表支持"目标对象内部的自我调用，进行切面增强"。
	 *
	 * 有时候目标对象内部的自我调用，将无法实施切面中的增强，如下示例:
	 *
	 * public interface AService {
	 * 		public void a();
	 * 		public void b();
	 * }
	 *
	 * @Service
	 * public class AServiceImpl implements AService{
	 *      @Transactional(propagation=Propagation.REQUIRED)
	 *      public void a () {
	 *      	// 由于是目标对象内部的自我调用，将无法实施切面中的增强
	 * 			this.b();
	 * 		}
	 * 		@Transactional(propagation=Propagation.REQUIRES_NEW)
	 * 		public void b () {
	 * 		}
	 * }
	 *
	 * 在a()中，此处的this指向目标对象，因此调用this.b()将不会执行b事务切面，即不会执行事务增强，
	 * 因此b方法的事务定义"@Transactional(propagation = Propagation.REQUIRES_NEW)"将不会实施，
	 * 为了解决这个问题，我们可以设置expose-proxy=true，然后将this.b();修改为((AService) AopContext.currentProxy()).b();即可完成对 a和 b方法的同时增强
	 */
	public static void forceAutoProxyCreatorToExposeProxy/* 强制AutoProxyCreator公开代理 */(BeanDefinitionRegistry registry) {
		// 判断是否包含org.springframework.aop.config.internalAutoProxyCreator名称的bd
		// 也就是判断是否包含"自动代理创建器bd"
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */)) {
			// 获取org.springframework.aop.config.internalAutoProxyCreator名称的bd
			// 也就是获取"自动代理创建器bd"
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			// 往"自动代理创建器bd"中添加exposeProxy属性，属性值为true
			definition.getPropertyValues().add("exposeProxy", Boolean.TRUE);
		}
	}

	/**
	 * 如果工厂中不存在"自动代理创建器bd"，注册"自动代理创建器bd"
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 *
	 * @param cls					当前"自动代理创建器"的Class
	 * @param registry
	 * @param source
	 * @return
	 */
	@Nullable
	private static BeanDefinition registerOrEscalateApcAsRequired(
			Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

		/*

		1、判断bean工厂中是否已经存在"自动代理创建器bd"

		如果已经存在了"自动代理创建器bd"：
		（1）且存在的"自动代理创建器bd"与现在的"自动代理创建器bd"一致，那么无须再次创建；
		（2）如果存在的"自动代理创建器bd"与现在的"自动代理创建器bd"不一致，那么需要根据优先级来判断到底需要使用哪个

		 */
		// 如果已经存在了自动代理创建器，且存在的自动代理创建器与现在不一致，那么需要根据优先级来判断到底需要使用哪个
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */)) {
			// 获取存在的"自动代理创建器bd"
			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);

			/* (1)如果存在的"自动代理创建器bd"与现在的不一致，那么需要根据优先级来判断到底需要使用哪个 */
			// 判断存在的"自动代理创建器bd"与现在的是否一致
			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
				// 从"自动代理创建器的优先级顺序列表"中获取【已经存在的"自动代理创建器"的优先级】
				int currentPriority = findPriorityForClass/* 查找优先级 */(apcDefinition.getBeanClassName());
				// 从"自动代理创建器的优先级顺序列表"中获取【当前"自动代理创建器"的优先级】
				int requiredPriority = findPriorityForClass/* 查找优先级 */(cls);

				// 如果【已经存在的"自动代理创建器"的优先级】小于【当前"自动代理创建器"的优先级】，则采用【当前"自动代理创建器"】
				// 题外：从这里可以得出，"自动代理创建器"在"自动代理创建器的优先级顺序列表"，索引顺序大的，优先级越高
				if (currentPriority < requiredPriority) {
					// 设置"自动代理创建器bd"为当前"自动代理创建器"
					apcDefinition.setBeanClassName(cls.getName());
				}
			}

			/* (2)如果已经存在自动代理创建器bd，与当前的自动代理创建器bd一致，那么无须再次创建，所以返回null */
			return null;
		}

		/*

		2、beanFactory中不存在"自动代理创建器bd"，则注册"自动代理创建器bd"

		key = org.springframework.aop.config.internalAutoProxyCreator

		*/
		// 创建"自动代理创建器bd"
		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
		beanDefinition.setSource(source);
		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		// 注册上面创建的"自动代理创建器bd"到bean工厂中
		// key = org.springframework.aop.config.internalAutoProxyCreator
		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */, beanDefinition);
		return beanDefinition;
	}

	/**
	 * 根据自动代理创建器的Class查询优先级
	 *
	 * @param clazz		自动代理创建器的Class
	 */
	private static int findPriorityForClass(Class<?> clazz) {
		// 从"自动代理创建器的优先级顺序列表"中获取当前"自动代理创建器"的优先级
		return APC_PRIORITY_LIST.indexOf(clazz);
	}

	/**
	 * 根据自动代理创建器的className查询优先级
	 *
	 * @param className		自动代理创建器的className
	 */
	private static int findPriorityForClass(@Nullable String className) {
		// 从"自动代理创建器的优先级顺序列表"中获取当前"自动代理创建器"的优先级
		for (int i = 0; i < APC_PRIORITY_LIST.size(); i++) {
			Class<?> clazz = APC_PRIORITY_LIST.get(i);
			if (clazz.getName().equals(className)) {
				return i;
			}
		}
		throw new IllegalArgumentException(
				"Class name [" + className + "] is not a known auto-proxy creator class");
	}

}
