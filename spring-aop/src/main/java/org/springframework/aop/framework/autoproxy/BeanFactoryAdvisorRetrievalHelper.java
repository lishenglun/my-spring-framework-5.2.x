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

package org.springframework.aop.framework.autoproxy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for retrieving standard Spring Advisors from a BeanFactory,
 * for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AbstractAdvisorAutoProxyCreator
 */
public class BeanFactoryAdvisorRetrievalHelper {

	private static final Log logger = LogFactory.getLog(BeanFactoryAdvisorRetrievalHelper.class);

	private final ConfigurableListableBeanFactory beanFactory;

	@Nullable
	private volatile String[] cachedAdvisorBeanNames;


	/**
	 * Create a new BeanFactoryAdvisorRetrievalHelper for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAdvisorRetrievalHelper(ConfigurableListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	/**
	 * 获取所有容器中的Advisor bean
	 *
	 * 题外：在《spring源码深度解析(第2版)》中，Advisor bean叫做增强器！
	 *
	 * Find all eligible Advisor beans in the current bean factory,
	 * ignoring FactoryBeans and excluding beans that are currently in creation.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> findAdvisorBeans() {
		/*

		1、获取到当前容器，以及父容器中，所有实现了Advisor接口的beanName —— 也就是通知类型的bean名称，

		题外：Advisor bd中包含了Advice bd
		题外：Advice bd有：
		（1）aop5大Advice bd：AspectJAroundAdvice、AspectJMethodBeforeAdvice、AspectJAfterReturningAdvice、AspectJAfterThrowingAdvice、AspectJAfterAdvice、
		（2）事务Advice bd：TransactionInterceptor

		 */
		// Determine list of advisor bean names, if not cached already. —— 确定advisor bean名称列表（如果尚未缓存）。
		// 从缓存中获取，所有实现了Advisor接口的beanName
		String[] advisorNames = this.cachedAdvisorBeanNames;
		// 缓存中不存在Advisor接口的beanName
		if (advisorNames == null) {
			/**
			 * 1、AOP的5大通知，可以得到：
			 *
			 * org.springframework.aop.aspectj.AspectJPointcutAdvisor#0
			 * org.springframework.aop.aspectj.AspectJPointcutAdvisor#1
			 * org.springframework.aop.aspectj.AspectJPointcutAdvisor#2
			 * org.springframework.aop.aspectj.AspectJPointcutAdvisor#3
			 * org.springframework.aop.aspectj.AspectJPointcutAdvisor#4
			 *
			 * 2、事务通知，也就是<aop:advisor>标签，可以得到：
			 *
			 * org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor#0
			 */
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the auto-proxy creator apply to them!
			// 上面的翻译：不要在这里初始化 FactoryBeans：我们需要让所有常规 bean 保持未初始化状态，让自动代理创建者应用到它们！

			// 获取当前BeanFactory以及父BeanFactory中所有实现了Advisor接口的beanName
			advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors/* 包括祖先：如果我父容器里面如果存在了Advisor.class类型的bd，也会获取到 */(
					this.beanFactory, Advisor.class, true, false);
			// 缓存一下
			this.cachedAdvisorBeanNames = advisorNames;
		}

		if (advisorNames.length == 0) {
			return new ArrayList<>();
		}

		/*

		2、根据获取到的"Advisor beanName"列表，实例化所有的Advisor bean

		题外：<aop:advisor>标签，对应的Advisor bean是DefaultBeanFactoryPointcutAdvisor
		<aop:config>
			<aop:advisor advice-ref="txAdvice" pointcut-ref="pt1"/>
		</aop:config>

		 */
		// 对获取到的实现Advisor接口的bean的名称进行遍历
		List<Advisor> advisors = new ArrayList<>();
		// 循环所有的beanName，找出对应的增强器
		for (String name : advisorNames) {
			// 判断当前的advisor是不是符合条件的：一个钩子方法，默认都为true
			// isEligibleBean()是提供的一个hook方法，用于子类对Advisor进行过滤，这里默认返回值都是true
			if (isEligibleBean/* 是合格的bean */(name)) {
				// 如果当前bean还在创建过程中，则略过，其创建完成之后会为其判断是否需要织入切面逻辑
				if (this.beanFactory.isCurrentlyInCreation(name)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Skipping currently created advisor '" + name + "'");
					}
				}
				else {
					try {
						// ⚠️获取增强器
						// 将当前bean添加到结果中
						advisors.add(this.beanFactory.getBean(name/* org.springframework.aop.aspectj.AspectJPointcutAdvisor#0 */, Advisor.class));
					}
					catch (BeanCreationException ex) {
						// 对获取过程中产生的异常进行封装
						Throwable rootCause = ex.getMostSpecificCause();
						if (rootCause instanceof BeanCurrentlyInCreationException) {
							BeanCreationException bce = (BeanCreationException) rootCause;
							String bceBeanName = bce.getBeanName();
							if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
								if (logger.isTraceEnabled()) {
									logger.trace("Skipping advisor '" + name +
											"' with dependency on currently created bean: " + ex.getMessage());
								}
								// Ignore: indicates a reference back to the bean we're trying to advise.
								// We want to find advisors other than the currently created bean itself.
								continue;
							}
						}
						throw ex;
					}
				}
			}
		}
		return advisors;
	}

	/**
	 * Determine whether the aspect bean with the given name is eligible.
	 * <p>The default implementation always returns {@code true}.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
