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

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;

/**
 * Utility class for handling registration of auto-proxy creators used internally
 * by the '{@code aop}' namespace tags.
 *
 * <p>Only a single auto-proxy creator should be registered and multiple configuration
 * elements may wish to register different concrete implementations. As such this class
 * delegates to {@link AopConfigUtils} which provides a simple escalation protocol.
 * Callers may request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @see AopConfigUtils
 * @since 2.0
 */
public abstract class AopNamespaceUtils {

	/**
	 * The {@code proxy-target-class} attribute as found on AOP-related XML tags. —— 在 AOP 相关的 XML 标记上找到的 {@code proxy-target-class} 属性。
	 */
	public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

	/**
	 * The {@code expose-proxy} attribute as found on AOP-related XML tags. —— 在 AOP 相关的 XML 标记上找到的 {@code expose-proxy} 属性。
	 */
	private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";

	/**
	 * 注册"自动代理创建器bd" = InfrastructureAdvisorAutoProxyCreator
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 *
	 * @param parserContext
	 * @param sourceElement
	 */
	public static void registerAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		// 注册"自动代理创建器bd" = InfrastructureAdvisorAutoProxyCreator
		// key = org.springframework.aop.config.internalAutoProxyCreator
		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));

		// 处理proxyTargetClass和exposeProxy属性
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);

		// 注册组件
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/**
	 * 如果工厂中不存在"自动代理创建器bd"，则注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")，以及配置"自动代理创建器bd"
	 *
	 * @param sourceElement <aop:config>
	 * @param parserContext 解析<aop:config>的ParserContext
	 */
	public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
		/*

		1、如果工厂中不存在"自动代理创建器bd"，则注册AspectJAwareAdvisorAutoProxyCreator bd("自动代理创建器bd")
		key = org.springframework.aop.config.internalAutoProxyCreator

		 */
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry()/* beanFactory */, parserContext.extractSource(sourceElement));
		/*

		2、配置"自动代理创建器bd" —— 处理proxyTargetClass和exposeProxy属性：
		(1)如果配置了proxyTargetClass属性为true，则往"自动代理创建器bd"中添加proxyTargetClass属性，属性值为true
		(2)如果配置了exposeProxy属性为true，则往"自动代理创建器bd"中添加exposeProxy属性，属性值为true

 		*/
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);

		/* 3、注册了一个组件：BeanComponentDefinition */
		// 注册组件并通知，便于监听器做进一步处理
		registerComponentIfNecessary(beanDefinition, parserContext);
	}


	/**
	 * 注册AnnotationAwareAspectJAutoProxyCreator bd("自动代理创建器bd")
	 *
	 * @param parserContext
	 * @param sourceElement
	 */
	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		/* 1、注册AnnotationAwareAspectJAutoProxyCreator bd("自动代理创建器bd") */

		// 如果工厂中不存在"自动代理创建器bd"，则注册AnnotationAwareAspectJAutoProxyCreator bd为"自动代理创建器bd"
		// key = org.springframework.aop.config.internalAutoProxyCreator
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));

		/* 2、配置AnnotationAwareAspectJAutoProxyCreator bd("自动代理创建器bd") —— 处理proxyTargetClass和exposeProxy属性 */
		// 处理proxyTargetClass和exposeProxy属性：
		// （1）如果配置了proxyTargetClass属性为true，则往"自动代理创建器bd"中添加proxyTargetClass属性，属性值为true
		// （2）如果配置了exposeProxy属性为true，则往"自动代理创建器bd"中添加exposeProxy属性，属性值为true
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);

		/* 3、注册组件并通知，便于监听器做进一步处理 */
		// 其中beanDefinition的className为AnnotationAwareAspectJAutoProxyCreator
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/**
	 * 处理proxyTargetClass和exposeProxy属性：
	 * 1、如果配置了proxyTargetClass属性为true，则往"自动代理创建器bd"中添加proxyTargetClass属性，属性值为true
	 * 2、如果配置了exposeProxy属性为true，则往"自动代理创建器bd"中添加exposeProxy属性，属性值为true
	 *
	 * 参考：
	 * <aop:config proxy-target-class="true" expose-proxy="true">
	 * </aop:config>
	 *
	 * @param registry      BeanDefinitionRegistry
	 * @param sourceElement <aop:config>
	 */
	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
		if (sourceElement != null) {
			/**
			 * <aop:config proxy-target-class="true" expose-proxy="true">
			 * </aop:config>
			 *
			 * 1、SpringAOP部分使用JDK动态代理或者cglib来为目标创建代理：
			 * （1）如果被代理的目标对象实现了至少一个接口，则会使用JDK动态代理，所有该目标类型实现的接口都将被代理；
			 * （2）若该目标对象没有实现任何接口，则创建一个cglib代理
			 */
			/* 1、如果配置了proxyTargetClass属性为true，则往"自动代理创建器bd"中添加proxyTargetClass属性，属性值为true */

			// 对于proxy-target-class属性的处理
			/**
			 * 1、proxy-target-class：是否强制使用cglib代理。设置为true，代表强制使用cglib代理
			 *
			 * Spring AOP部分使用JDK动态代理或者CGLIB来为目标对象创建代理(建议尽量使用JDK的动态代理)。
			 * 如果被代理的目标对象实现了至少一个接口，则会使用JDK动态代理。所有该目标类型实现的接口都将被代理。若该目标对象没有实现任何接口，则创建一个CGLIB代理。
			 * 如果你希望强制使用CGLIB代理(例如希望代理目标对象的所有方法，而不只是实现自接口的方法)，那也可以。但是需妥考虑以下两个问题：
			 * （1）无法通知( advise ) Final 方法，因为它们不能被覆写。
			 * （2）需要将 CGLIB二进制友行包放在 classpath下面
			 */
			// 获取<aop:config proxy-target-class="true">中的proxy-target-class属性
			boolean proxyTargetClass = Boolean.parseBoolean(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE/* proxy-target-class */));
			// 如果配置了exposeProxy属性为true，则往"自动代理创建器bd"中添加exposeProxy属性，属性值为true
			if (proxyTargetClass) {
				// 往"自动代理创建器bd"中添加proxyTargetClass属性，属性值为true
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying/* 强制AutoProxyCreator使用类代理 */(registry);
			}

			/* 2、如果配置了exposeProxy属性为true，则往"自动代理创建器bd"中添加exposeProxy属性，属性值为true */

			// 对expose-proxy属性的处理
			/**
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
			// 获取<aop:config expose-proxy ="true">中的expose-proxy 属性
			boolean exposeProxy = Boolean.parseBoolean(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE/* expose-proxy */));
			// 如果配置了exposeProxy属性为true，则往"自动代理创建器bd"中添加exposeProxy属性，属性值为true
			if (exposeProxy) {
				// 往"自动代理创建器bd"中添加exposeProxy属性，属性值为true
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy/* 强制AutoProxyCreator公开代理 */(registry);
			}
		}
	}

	/**
	 * 必要时注册组件
	 */
	private static void registerComponentIfNecessary(@Nullable BeanDefinition beanDefinition, ParserContext parserContext) {
		if (beanDefinition != null) {
			// 注册组件
			parserContext.registerComponent(
					new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */));
		}
	}

}
