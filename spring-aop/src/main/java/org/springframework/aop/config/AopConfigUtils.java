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
	 * ?????????????????????????????????????????????
	 *
	 * ????????????{@link AopConfigUtils#registerOrEscalateApcAsRequired}??????????????????"?????????????????????"???"?????????????????????????????????????????????"??????????????????????????????????????????
	 * >>> ???????????????????????????????????????????????????????????????????????????????????????????????????
	 *
	 * Stores the auto proxy creator classes in escalation order. ?????? ????????????????????????????????????????????????
	 */
	private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<>(3);

	static {
		// Set up the escalation list... ?????? ?????????????????????...
		APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
		// ???????????????
		APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class);
	}


	/**
	 * ??????"?????????????????????bd" = InfrastructureAdvisorAutoProxyCreator
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 */
	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAutoProxyCreatorIfNecessary(registry, null);
	}

	/**
	 * ??????"?????????????????????bd" = InfrastructureAdvisorAutoProxyCreator
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
	 * ????????????????????????"?????????????????????bd"????????????AspectJAwareAdvisorAutoProxyCreator bd("?????????????????????bd")
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
		 * 1????????????AOP??????????????????????????????????????????????????????????????????????????????bean???????????????????????????????????????????????????????????????AspectJAwareAdvisorAutoProxyCreator
		 *
		 * 2???AspectJAwareAdvisorAutoProxyCreator????????????BeanPostProcessor
		 */
		// ????????????????????????"?????????????????????bd"????????????AspectJAwareAdvisorAutoProxyCreator bd("?????????????????????bd")
		// key = org.springframework.aop.config.internalAutoProxyCreator
		return registerOrEscalateApcAsRequired/* ???????????????????????????Apc */(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		// ????????????????????????"?????????????????????bd"?????????AnnotationAwareAspectJAutoProxyCreator bd???"?????????????????????bd"
		// key = org.springframework.aop.config.internalAutoProxyCreator
		return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
	}

	/**
	 * ???"?????????????????????bd"?????????proxyTargetClass?????????????????????true
	 *
	 * 1???proxy-target-class?????????????????????cglib??????????????????true?????????????????????cglib??????
	 *
	 * Spring AOP????????????JDK??????????????????CGLIB??????????????????????????????(??????????????????JDK???????????????)???
	 * ????????????????????????????????????????????????????????????????????????JDK?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????CGLIB?????????
	 * ???????????????????????????CGLIB??????(????????????????????????????????????????????????????????????????????????????????????)?????????????????????????????????????????????????????????
	 * ???1???????????????( advise ) Final ???????????????????????????????????????
	 * ???2???????????? CGLIB???????????????????????? classpath??????
	 */
	public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
		// ??????????????????org.springframework.aop.config.internalAutoProxyCreator?????????bd
		// ???????????????????????????"?????????????????????bd"
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */)) {
			// ??????org.springframework.aop.config.internalAutoProxyCreator?????????bd
			// ???????????????"?????????????????????bd"
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			// ???"?????????????????????bd"?????????proxyTargetClass?????????????????????true
			definition.getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
		}
	}

	/**
	 * ???"?????????????????????bd"?????????exposeProxy?????????????????????true
	 *
	 * 1???expose-proxy????????????????????????????????????????????????????????????????????????????????????????????????true??????????????????"??????????????????????????????????????????????????????"???
	 *
	 * ?????????????????????????????????????????????????????????????????????????????????????????????:
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
	 *      	// ??????????????????????????????????????????????????????????????????????????????
	 * 			this.b();
	 * 		}
	 * 		@Transactional(propagation=Propagation.REQUIRES_NEW)
	 * 		public void b () {
	 * 		}
	 * }
	 *
	 * ???a()???????????????this?????????????????????????????????this.b()???????????????b?????????????????????????????????????????????
	 * ??????b?????????????????????"@Transactional(propagation = Propagation.REQUIRES_NEW)"??????????????????
	 * ?????????????????????????????????????????????expose-proxy=true????????????this.b();?????????((AService) AopContext.currentProxy()).b();??????????????? a??? b?????????????????????
	 */
	public static void forceAutoProxyCreatorToExposeProxy/* ??????AutoProxyCreator???????????? */(BeanDefinitionRegistry registry) {
		// ??????????????????org.springframework.aop.config.internalAutoProxyCreator?????????bd
		// ???????????????????????????"?????????????????????bd"
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */)) {
			// ??????org.springframework.aop.config.internalAutoProxyCreator?????????bd
			// ???????????????"?????????????????????bd"
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			// ???"?????????????????????bd"?????????exposeProxy?????????????????????true
			definition.getPropertyValues().add("exposeProxy", Boolean.TRUE);
		}
	}

	/**
	 * ????????????????????????"?????????????????????bd"?????????"?????????????????????bd"
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 *
	 * @param cls					??????"?????????????????????"???Class
	 * @param registry
	 * @param source
	 * @return
	 */
	@Nullable
	private static BeanDefinition registerOrEscalateApcAsRequired(
			Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

		/*

		1?????????bean???????????????????????????"?????????????????????bd"

		?????????????????????"?????????????????????bd"???
		???1???????????????"?????????????????????bd"????????????"?????????????????????bd"????????????????????????????????????
		???2??????????????????"?????????????????????bd"????????????"?????????????????????bd"????????????????????????????????????????????????????????????????????????

		 */
		// ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */)) {
			// ???????????????"?????????????????????bd"
			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);

			/* (1)???????????????"?????????????????????bd"???????????????????????????????????????????????????????????????????????????????????? */
			// ???????????????"?????????????????????bd"????????????????????????
			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
				// ???"?????????????????????????????????????????????"???????????????????????????"?????????????????????"???????????????
				int currentPriority = findPriorityForClass/* ??????????????? */(apcDefinition.getBeanClassName());
				// ???"?????????????????????????????????????????????"??????????????????"?????????????????????"???????????????
				int requiredPriority = findPriorityForClass/* ??????????????? */(cls);

				// ????????????????????????"?????????????????????"??????????????????????????????"?????????????????????"????????????????????????????????????"?????????????????????"???
				// ?????????????????????????????????"?????????????????????"???"?????????????????????????????????????????????"???????????????????????????????????????
				if (currentPriority < requiredPriority) {
					// ??????"?????????????????????bd"?????????"?????????????????????"
					apcDefinition.setBeanClassName(cls.getName());
				}
			}

			/* (2)???????????????????????????????????????bd????????????????????????????????????bd????????????????????????????????????????????????null */
			return null;
		}

		/*

		2???beanFactory????????????"?????????????????????bd"????????????"?????????????????????bd"

		key = org.springframework.aop.config.internalAutoProxyCreator

		*/
		// ??????"?????????????????????bd"
		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
		beanDefinition.setSource(source);
		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		// ?????????????????????"?????????????????????bd"???bean?????????
		// key = org.springframework.aop.config.internalAutoProxyCreator
		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME/* org.springframework.aop.config.internalAutoProxyCreator */, beanDefinition);
		return beanDefinition;
	}

	/**
	 * ??????????????????????????????Class???????????????
	 *
	 * @param clazz		????????????????????????Class
	 */
	private static int findPriorityForClass(Class<?> clazz) {
		// ???"?????????????????????????????????????????????"???????????????"?????????????????????"????????????
		return APC_PRIORITY_LIST.indexOf(clazz);
	}

	/**
	 * ??????????????????????????????className???????????????
	 *
	 * @param className		????????????????????????className
	 */
	private static int findPriorityForClass(@Nullable String className) {
		// ???"?????????????????????????????????????????????"???????????????"?????????????????????"????????????
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
