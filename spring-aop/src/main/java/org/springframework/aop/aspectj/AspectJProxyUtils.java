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

package org.springframework.aop.aspectj;

import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;

import java.util.List;

/**
 * Utility methods for working with AspectJ proxies.
 *
 * @author Rod Johnson
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class AspectJProxyUtils {

	/**
	 * Add special advisors if necessary to work with a proxy chain that contains AspectJ advisors:
	 * concretely, {@link ExposeInvocationInterceptor} at the beginning of the list.
	 * <p>This will expose the current Spring AOP invocation (necessary for some AspectJ pointcut
	 * matching) and make available the current AspectJ JoinPoint. The call will have no effect
	 * if there are no AspectJ advisors in the advisor chain.
	 *
	 * 如有必要，添加特殊顾问以使用包含 AspectJ 顾问的代理链：具体而言，{@link ExposeInvocationInterceptor} 在列表的开头。
	 * <p>这将公开当前的 Spring AOP 调用（某些 AspectJ 切入点匹配所必需的）并使当前的 AspectJ JoinPoint 可用。
	 * 如果顾问链中没有 AspectJ 顾问，则调用将无效。
	 *
	 * @param advisors the advisors available
	 * @return {@code true} if an {@link ExposeInvocationInterceptor} was added to the list,
	 * otherwise {@code false}
	 */
	public static boolean makeAdvisorChainAspectJCapableIfNecessary(List<Advisor> advisors) {
		// Don't add advisors to an empty list; may indicate that proxying is just not required
		// 上面翻译：不要将顾问添加到空列表中；可能表明不需要代理

		// 存在Advisor
		if (!advisors.isEmpty()) {

			// Advisor中是否包含Advice的标识
			boolean foundAspectJAdvice = false;

			for (Advisor advisor : advisors) {
				// Be careful not to get the Advice without a guard, as this might eagerly
				// instantiate a non-singleton AspectJ aspect...
				// 上面翻译：小心不要在没有警卫的情况下获得Advice，因为这可能会急切地实例化非单例AspectJ切面......
				/**
				 * 1、题外：⚠️在使用<tx:advice>，也就是事务的情况下，isAspectJAdvice()里面，存在实例化TransactionInterceptor advice
				 */
				// 检验Advisor中是否包含Advice
				if (isAspectJAdvice(advisor)) {
					// 设置为true，表示找到了advice
					foundAspectJAdvice = true;
					break;
				}
			}

			// 【Advisor中存在Advice && advisors中不包含ExposeInvocationInterceptor.ADVISOR】则往advisors中添加一个advisor：ExposeInvocationInterceptor.ADVISOR
			if (foundAspectJAdvice && !advisors.contains(ExposeInvocationInterceptor.ADVISOR)) {
				/**
				 * 1、ExposeInvocationInterceptor.ADVISOR = DefaultPointcutAdvisor
				 *
				 * DefaultPointcutAdvisor是一个Advisor，里面包含了ExposeInvocationInterceptor，ExposeInvocationInterceptor是一个Advice
				 *
				 * 作用：添加的ExposeInvocationInterceptor.ADVISOR对象，是用来完成整个拦截器链里面所通用的MethodInvocation信息保存，不管什么时候我都能够获取得到MethodInvocation
				 *
				 * 2、ExposeInvocationInterceptor：用来传递MethodInvocation的
				 *
				 * 在后续的任何下调用链环节，只要需要用到当前的MethodInvocation，就可以通过ExposeInvocationInterceptor.currentInvocation()获取得到，而不需要一直进行传递，
				 * 所以它并不是实际意义上存在的消息通知，只是为了返回拦截器链里面我需要的MethodInvocation对象，仅此而已
				 */
				// ⚠️往advisors中添加一个advisor
				// 题外：新添加的advisor并不是实际意义上的Advisor，并不是新增的通知类型，添加的ExposeInvocationInterceptor.ADVISOR(DefaultPointcutAdvisor)对象，
				// >>> 是用来完成整个拦截器链里面所通用的MethodInvocation信息保存，不管什么时候我都能够获取得到MethodInvocation
				advisors.add(0, ExposeInvocationInterceptor.ADVISOR);
				return true;
			}
		}

		return false;
	}

	/**
	 * 检验Advisor中是否包含Advice
	 *
	 * Determine whether the given Advisor contains an AspectJ advice. —— 确定给定的Advisor是否包含AspectJ advice
	 * @param advisor the Advisor to check
	 */
	private static boolean isAspectJAdvice(Advisor advisor) {
		return (advisor instanceof InstantiationModelAwarePointcutAdvisor ||
				/**
				 * 1、题外：在使用<tx:advice>，也就是事务的情况下，advisor.getAdvice()方法逻辑里面，存在实例化TransactionInterceptor advice
				 */
				// AbstractBeanFactoryPointcutAdvisor
				advisor.getAdvice() instanceof AbstractAspectJAdvice ||
				(advisor instanceof PointcutAdvisor &&
						((PointcutAdvisor) advisor).getPointcut() instanceof AspectJExpressionPointcut));
	}

}
