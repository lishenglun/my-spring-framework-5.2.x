/*
 * Copyright 2002-2020 the original author or authors.
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

import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Generic auto proxy creator that builds AOP proxies for specific beans
 * based on detected Advisors for each bean.
 *
 * <p>Subclasses may override the {@link #findCandidateAdvisors()} method to
 * return a custom list of Advisors applying to any object. Subclasses can
 * also override the inherited {@link #shouldSkip} method to exclude certain
 * objects from auto-proxying.
 *
 * <p>Advisors or advices requiring ordering should be annotated with
 * {@link org.springframework.core.annotation.Order @Order} or implement the
 * {@link org.springframework.core.Ordered} interface. This class sorts
 * advisors using the {@link AnnotationAwareOrderComparator}. Advisors that are
 * not annotated with {@code @Order} or don't implement the {@code Ordered}
 * interface will be considered as unordered; they will appear at the end of the
 * advisor chain in an undefined order.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #findCandidateAdvisors
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisorAutoProxyCreator extends AbstractAutoProxyCreator {
	// AbstractAdvisorAutoProxyCreator$BeanFactoryAdvisorRetrievalHelperAdapter
	@Nullable
	private BeanFactoryAdvisorRetrievalHelper advisorRetrievalHelper;


	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		super.setBeanFactory(beanFactory);
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"AdvisorAutoProxyCreator requires a ConfigurableListableBeanFactory: " + beanFactory);
		} // ⚠️调用处
		initBeanFactory((ConfigurableListableBeanFactory) beanFactory);
	}

	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		this.advisorRetrievalHelper = new BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory);
	}

	/**
	 * 获取能够增强当前bean的所有Advisor bean(通知)
	 *
	 * 题外：只要当前bean对象中的一个方法被某个Advisor中的切入点所匹配，这个bean对象就需要被代理，这个Advisor也就作为代理中的拦截器。
	 * 那肯定有疑惑，为什么一个方法被匹配了，就要对整个bean对象进行代理，其余的方法可能是不需要增强的？因为代理粒度是对象级别的，所以一个方法匹配了就对整个对象进行代理。
	 * 后续在代理对象内部再判断，当前方法是不是要被拦截的！
	 *
	 * 题外：扩展advisor的逻辑在里面：会添加一个DefaultPointcutAdvisor Advisor，里面包含了ExposeInvocationInterceptor advice；
	 *
	 * 题外：sortAdvisor()的逻辑在里面
	 *
	 * 题外：⚠️@Transaction的解析逻辑在里面
	 */
	@Override
	@Nullable
	protected Object[] getAdvicesAndAdvisorsForBean(
			Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {

		/* 1、获取能够增强当前bean的所有Advisor bean(通知) */
		// 例如：当前bean的add()，能够增强当前bean的add()的Advisor bean(通知类型)有：before、after，那么获取到的能够增强当前bean的所有Advisor bean就是before、after相关的advisor
		// 注意：⚠️@Transaction的解析逻辑在里面
		List<Advisor> advisors = /* ⚠️ */findEligibleAdvisors(beanClass, beanName);

		/* 2、若为空，表示没找到，返回一个null */
		if (advisors.isEmpty()) {
			return DO_NOT_PROXY/* null */;
		}

		return advisors.toArray();
	}

	/**
	 * 获取能够增强当前bean的所有Advisor bean（通知）
	 *
	 * Find all eligible Advisors for auto-proxying this class.
	 * @param beanClass the clazz to find advisors for
	 * @param beanName the name of the currently proxied bean
	 * @return the empty List, not {@code null},
	 * if there are no pointcuts or interceptors
	 * @see #findCandidateAdvisors
	 * @see #sortAdvisors
	 * @see #extendAdvisors
	 */
	protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
		/* 1、获取所有容器中的Advisor bean */
		/**
		 * 1、如果是<tx:annotation-driven"/>或者是@EnableTransactionManagement，注解支持事务的话，那么必然有一个BeanFactoryTransactionAttributeSourceAdvisor
		 */
		// 将当前系统中所有的切面类的切面逻辑进行封装，从而得到目标Advisor
		List<Advisor> candidateAdvisors = findCandidateAdvisors();

		/* 2、找到能够增强当前bean的所有Advisor bean */
		// 对获取到的所有Advisor进行判断，看其切面定义是否可以应用到当前bean，从而得到最终需要应用的Advisor
		// 看一下，当前找到的Advisor bean，能否对它进行一个应用，能否适配得上，我是否能否匹配当前的这么一个要求
		// 注意：⚠️@Transaction的解析逻辑在里面
		List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);

		/*

		3、扩展Advisor

		例如：会添加一个DefaultPointcutAdvisor Advisor，里面包含了ExposeInvocationInterceptor advice。
		ExposeInvocationInterceptor：它并不是一个实际意义上的消息通知类型，只是用来保存整个拦截器链里面所通用的MethodInvocation对象，
		以及为了在拦截器链里面返回我需要的MethodInvocation对象，不管什么时候我都能够获取得到MethodInvocation，仅此而已

		⚠️题外：在使用<tx:advice>，事务的情况下，里面存在实例化TransactionInterceptor这个advice！

		*/
		// 提供的hook方法，用于对目标Advisor进行拓展
		// AspectJAwareAdvisorAutoProxyCreator
		extendAdvisors/* 扩展 */(eligibleAdvisors);

		/* 4、对Advisor bean进行排序 */
		// 对Advisor bean进行排序。有的通知先执行，有的通知后执行；排完序之后，返回的是我们正常的要执行的顺序
		if (!eligibleAdvisors.isEmpty()) {
			// 对需要代理的Advisor按照一定的规则进行排序
			// 题外：拓扑排序
			eligibleAdvisors = sortAdvisors(eligibleAdvisors);
		}
		return eligibleAdvisors;
	}

	/**
	 * 获取所有容器中的Advisor bean（通知器）
	 *
	 * Find all candidate Advisors to use in auto-proxying. —— 查找要在自动代理中使用的所有候选顾问。
	 * @return the List of candidate Advisors
	 */
	protected List<Advisor> findCandidateAdvisors() {
		Assert.state(this.advisorRetrievalHelper != null, "No BeanFactoryAdvisorRetrievalHelper available");
		// 获取所有容器中的Advisor bean
		// 题外：Helper相当于是一个工具类
		return this.advisorRetrievalHelper/* 顾问检索助手 */.findAdvisorBeans();
	}

	/**
	 * 从候选的Advisor列表中找到能够增强当前bean的所有Advisor
	 *
	 * Search the given candidate Advisors to find all Advisors that
	 * can apply to the specified bean.
	 *
	 * 搜索给定的候选Advisor以查找可以应用于指定bean的所有Advisor
	 *
	 * @param candidateAdvisors the candidate Advisors
	 * @param beanClass the target's bean class
	 * @param beanName the target's bean name
	 * @return the List of applicable Advisors
	 * @see ProxyCreationContext#getCurrentProxiedBeanName()
	 */
	protected List<Advisor> findAdvisorsThatCanApply(
			List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {

		ProxyCreationContext.setCurrentProxiedBeanName(beanName);
		try {
			// 从候选的Advisor列表中找到能够增强当前bean的所有Advisor
			// 注意：⚠️@Transaction的解析逻辑在里面
			return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
		}
		finally {
			ProxyCreationContext.setCurrentProxiedBeanName(null);
		}
	}

	/**
	 * Return whether the Advisor bean with the given name is eligible
	 * for proxying in the first place.
	 * @param beanName the name of the Advisor bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleAdvisorBean(String beanName) {
		return true;
	}

	/**
	 * Sort advisors based on ordering. Subclasses may choose to override this
	 * method to customize the sorting strategy.
	 * @param advisors the source List of Advisors
	 * @return the sorted List of Advisors
	 * @see org.springframework.core.Ordered
	 * @see org.springframework.core.annotation.Order
	 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
	 */
	protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
		AnnotationAwareOrderComparator.sort(advisors);
		return advisors;
	}

	/**
	 * Extension hook that subclasses can override to register additional Advisors,
	 * given the sorted Advisors obtained to date.
	 * <p>The default implementation is empty.
	 * <p>Typically used to add Advisors that expose contextual information
	 * required by some of the later advisors.
	 * @param candidateAdvisors the Advisors that have already been identified as
	 * applying to a given bean
	 */
	protected void extendAdvisors(List<Advisor> candidateAdvisors) {
	}

	/**
	 * This auto-proxy creator always returns pre-filtered Advisors.
	 */
	@Override
	protected boolean advisorsPreFiltered() {
		return true;
	}


	/**
	 * Subclass of BeanFactoryAdvisorRetrievalHelper that delegates to
	 * surrounding AbstractAdvisorAutoProxyCreator facilities.
	 */
	private class BeanFactoryAdvisorRetrievalHelperAdapter extends BeanFactoryAdvisorRetrievalHelper {

		public BeanFactoryAdvisorRetrievalHelperAdapter(ConfigurableListableBeanFactory beanFactory) {
			super(beanFactory);
		}

		@Override
		protected boolean isEligibleBean(String beanName) {
			return AbstractAdvisorAutoProxyCreator.this.isEligibleAdvisorBean(beanName);
		}
	}

}
