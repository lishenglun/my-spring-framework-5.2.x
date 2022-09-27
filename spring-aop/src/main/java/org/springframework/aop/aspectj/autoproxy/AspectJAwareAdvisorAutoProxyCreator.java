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

package org.springframework.aop.aspectj.autoproxy;

import org.aopalliance.aop.Advice;
import org.aspectj.util.PartialOrder;
import org.aspectj.util.PartialOrder.PartialComparable;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.aspectj.AspectJPointcutAdvisor;
import org.springframework.aop.aspectj.AspectJProxyUtils;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 自动代理创建器（xml方式）
 *
 * 题外：后面在生成动态代理对象的时候，需要一个动态代理创建器（也就是为bean对象，创建动态代理的对象）
 *
 * AbstractAdvisorAutoProxyCreator的子类，使用AspectJ语法创建Advisor和代理对象
 *
 * {@link org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator}
 * subclass that exposes AspectJ's invocation context and understands AspectJ's rules
 * for advice precedence when multiple pieces of advice come from the same aspect.
 *
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJAwareAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator {

	private static final Comparator<Advisor> DEFAULT_PRECEDENCE_COMPARATOR = new AspectJPrecedenceComparator();


	/**
	 * Sort the supplied {@link Advisor} instances according to AspectJ precedence.
	 * <p>If two pieces of advice come from the same aspect, they will have the same
	 * order. Advice from the same aspect is then further ordered according to the
	 * following rules:
	 * <ul>
	 * <li>If either of the pair is <em>after</em> advice, then the advice declared
	 * last gets highest precedence (i.e., runs last).</li>
	 * <li>Otherwise the advice declared first gets highest precedence (i.e., runs
	 * first).</li>
	 * </ul>
	 * <p><b>Important:</b> Advisors are sorted in precedence order, from highest
	 * precedence to lowest. "On the way in" to a join point, the highest precedence
	 * advisor should run first. "On the way out" of a join point, the highest
	 * precedence advisor should run last.
	 */
	@Override
	protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
		List<PartiallyComparableAdvisorHolder> partiallyComparableAdvisors = new ArrayList<>(advisors.size());
		for (Advisor advisor : advisors) {
			partiallyComparableAdvisors.add(
					new PartiallyComparableAdvisorHolder(advisor, DEFAULT_PRECEDENCE_COMPARATOR));
		}
		// 拓扑排序
		List<PartiallyComparableAdvisorHolder> sorted = PartialOrder.sort(partiallyComparableAdvisors);
		if (sorted != null) {
			List<Advisor> result = new ArrayList<>(advisors.size());
			for (PartiallyComparableAdvisorHolder pcAdvisor : sorted) {
				result.add(pcAdvisor.getAdvisor());
			}
			return result;
		}
		else {
			return super.sortAdvisors(advisors);
		}
	}

	/**
	 * Add an {@link ExposeInvocationInterceptor} to the beginning of the advice chain.
	 * <p>This additional advice is needed when using AspectJ pointcut expressions
	 * and when using AspectJ-style advice.
	 */
	@Override
	protected void extendAdvisors(List<Advisor> candidateAdvisors) {
		AspectJProxyUtils.makeAdvisorChainAspectJCapableIfNecessary/* 必要时使顾问链方面J具备能力 */(candidateAdvisors);
	}

	/**
	 * 获取所有容器中的Advisor bean，然后用于判断当前bd是不是切面。是的话，就跳过，不需要进行动态代理(因为切面类自身不需要被代理，所以直接跳过去)
	 *
	 * 题外：如果Advisor中引入了Advice，那么在实例化Advice bean的时候，也会实例化Advice bean
	 *
	 * @param beanClass the class of the bean
	 * @param beanName the name of the bean
	 * @return
	 */
	@Override
	protected boolean shouldSkip(Class<?> beanClass, String beanName) {
		/*

		1、获取所有容器中的Advisor bean（通知器）

		题外：在创建当前bd的时候，我要判断一下，当前bd是否要被代理。如果当前bd是切面类，则代表不需要进行动态代理，就跳过；否则就不跳过，进行下一步的判断，看是否要代理（这只是初步的排查，后面还有一个排查）
		所以要获取所有容器(父子)中的Advisor bean，用于判断当前bd是不是切面类。

		*/
		// TODO: Consider optimization by caching the list of the aspect names —— 考虑通过缓存方面名称列表进行优化
		// 获取所有容器中的Advisor bean
		// 题外：最主要的目的是为了判断一下当前bd是不是切面类，是的话就跳过动态代理的创建
		List<Advisor> candidateAdvisors = findCandidateAdvisors/* 寻找候选人顾问 */();

		/*

		2、遍历所有容器中的Advisor bean，判断当前bd是不是切面类，是的话就跳过，不进行动态代理(因为切面类自身不需要被代理，所以直接跳过去)

		题外：切面："切入点"和"通知"的结合

		 */
		for (Advisor advisor : candidateAdvisors) {
			// 【advisor属于AspectJPointcutAdvisor实例 && 当前beanName等于，切面名称等于当前beanBean】就证明当前bd是切面类，于是跳过创建其代理对象！
			if (advisor instanceof AspectJPointcutAdvisor &&
					((AspectJPointcutAdvisor) advisor).getAspectName().equals(beanName)) {
				return true;
			}
		}

		return super.shouldSkip(beanClass, beanName);
	}


	/**
	 * Implements AspectJ's {@link PartialComparable} interface for defining partial orderings.
	 */
	private static class PartiallyComparableAdvisorHolder implements PartialComparable {

		private final Advisor advisor;

		private final Comparator<Advisor> comparator;

		public PartiallyComparableAdvisorHolder(Advisor advisor, Comparator<Advisor> comparator) {
			this.advisor = advisor;
			this.comparator = comparator;
		}

		@Override
		public int compareTo(Object obj) {
			Advisor otherAdvisor = ((PartiallyComparableAdvisorHolder) obj).advisor;
			return this.comparator.compare(this.advisor, otherAdvisor);
		}

		@Override
		public int fallbackCompareTo(Object obj) {
			return 0;
		}

		public Advisor getAdvisor() {
			return this.advisor;
		}

		@Override
		public String toString() {
			Advice advice = this.advisor.getAdvice();
			StringBuilder sb = new StringBuilder(ClassUtils.getShortName(advice.getClass()));
			boolean appended = false;
			if (this.advisor instanceof Ordered) {
				sb.append(": order = ").append(((Ordered) this.advisor).getOrder());
				appended = true;
			}
			if (advice instanceof AbstractAspectJAdvice) {
				sb.append(!appended ? ": " : ", ");
				AbstractAspectJAdvice ajAdvice = (AbstractAspectJAdvice) advice;
				sb.append("aspect name = ");
				sb.append(ajAdvice.getAspectName());
				sb.append(", declaration order = ");
				sb.append(ajAdvice.getDeclarationOrder());
			}
			return sb.toString();
		}
	}

}
