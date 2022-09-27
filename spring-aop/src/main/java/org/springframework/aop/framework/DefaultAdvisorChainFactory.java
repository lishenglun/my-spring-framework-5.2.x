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

package org.springframework.aop.framework;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.*;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	/**
	 * 从能够增强当前bean的Advisor中，获取出能够增强当前方法的Advisor；
	 * 然后从"能够增强当前方法的Advisor"当中，获取其中Advice所对应的所有MethodInterceptor，也就是能够拦截当前方法的拦截器链，返回
	 *
	 * 简单概括：从能够增强当前bean的Advisor中，获取能够增强当前方法的Advisor，然后从这些Advisor当作，获取其Advice对应的能够拦截当前方法的拦截器链（也就是Advisor当中Advice所对应的所有MethodInterceptor）
	 * 极简概括：获取能够拦截当前方法的拦截器链
	 *
	 * @param config			DefaultAdvisorChainFactory：Advised对象形式的AOP配置
	 * @param method 			目标方法
	 * @param targetClass 		目标类
	 */
	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice/* 获取拦截器和动态拦截建议 */(
			Advised config/* DefaultAdvisorChainFactory */, Method method/* 目标方法 */, @Nullable Class<?> targetClass/* 目标类 */) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		// 上面翻译：这有点棘手......我们必须先处理介绍，但我们需要保持最终列表中的顺序。

		/**
		 * 1、题外：这里用了一个单例模式获取DefaultAdvisorAdapterRegistry实例
		 *
		 * 在Spring中把每一个功能都分的很细，每个功能都会有相应的类去处理，符合单一职责原则的地方很多，这也是值得我们借鉴的一个地方
		 *
		 * 2、题外：Registry含义：某个类型对象的增删改查功能类
		 *
		 * 3、AdvisorAdapterRegistry作用：
		 *
		 * 将Advice适配为Advisor，将Advisor适配为对应的MethodInterceptor
		 */
		// 获取单例的DefaultAdvisorAdapterRegistry对象
		// 题外：拦截器链是通过AdvisorAdapterRegistry加入的，这个AdvisorAdapterRegistry对advice织入具备很大的作用
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry/* 全局Advisor适配器注册表 */.getInstance();

		// 返回在创建代理对象之初，查找到的，能够增强当前bean的Advisor
		Advisor[] advisors = config.getAdvisors();

		List<Object> interceptorList = new ArrayList<>(advisors.length);

		// 目标类
		Class<?> actualClass/* 实际Class */ = (targetClass != null ? targetClass : method.getDeclaringClass()/* 通过method获取目标类 */);

		// 判断是否存在引介增强（类粒度的增强），通常为false
		Boolean hasIntroductions = null;

		// 遍历能够增强当前bean的Advisor
		for (Advisor advisor : advisors) {
			/*

			1、方法粒度的匹配 —— 切入点增强

			如果是PointcutAdvisor切入点增强，就进行方法粒度的匹配，会先调用ClassFilter#matches()匹配类是不是符合规则，
			再调用IntroductionAwareMethodMatcher#matches()匹配方法是不是符合规则

			*/
			// 如果是一个切入点Advisor
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally. —— 有条件地添加它
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;

				// 如果提前进行过切点的匹配了 || 当前的Advisor适用于目标类
				if (config.isPreFiltered()/* 判断有没有前置过滤器 */ || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)/* 类匹配 */) {

					/* 方法匹配 */

					/**
					 * 1、如果advisor是ExposeInvocationInterceptor.ADVISOR = DefaultPointcutAdvisor的话，
					 * Pointcut = Pointcut.TRUE = TruePointcut；
					 * MethodMatcher = MethodMatcher.TRUE = TrueMethodMatcher
					 *
					 * 2、如果advisor是DefaultBeanFactoryPointcutAdvisor的话，
					 * Pointcut = AspectJExpressionPointcut，
					 * MethodMatcher = AspectJExpressionPointcut
					 */
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();

					// Advisor是否适用于当前目标方法的标识
					boolean match;

					/* 检测Advisor是否适用于此目标方法 */
					// 注意：IntroductionAwareMethodMatcher和MethodMatcher都是用于检测Advisor是否适用于此目标方法，只是实现不同
					if (mm instanceof IntroductionAwareMethodMatcher) {
						/* 判断"能够增强当前bean的Advisor"中是否存在一个能够匹配当前目标类的引介增强 */
						if (hasIntroductions == null) {
							hasIntroductions = hasMatchingIntroductions/* 有匹配的引介 */(advisors, actualClass);
						}
						// 判断目标方法是否匹配
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					}
					else {
						// 通过方法匹配器进行匹配
						match = mm.matches(method, actualClass);
					}

					/* 匹配成功，表示当前Advisor中可以用于增强当前方法 */
					// 当前Advisor适用于当前目标方法
					if (match) {
						// ⚠️获取Advisor中Advice对应的所有MethodInterceptor（Advisor适配器的逻辑在里面）
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);

						// 判断是否需要执行动态匹配
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							// 上面翻译：在getInterceptors()方法中，创建一个新的对象实例不是问题，因为我们通常会缓存创建的链。

							// 动态切入点则会创建一个InterceptorAndDynamicMethodMatcher对象，这个对象包含MethodInterceptor和MethodMatcher的实例
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						} else {
							// ⚠️添加到列表中
							interceptorList.addAll(Arrays.asList(interceptors));
						}

					}
				}
			}
			/*

			2、类粒度的匹配 —— 引介增强

			如果是IntroductionAdvisor引介增强，就进行类粒度的匹配，调用ClassFilter#matches()

			*/
			// 如果是引介增强
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				/* 匹配成功，表示当前Advisor中可以用于增强当前方法 */
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					// ⚠️获取Advisor中Advice对应的所有MethodInterceptor（Advisor适配器的逻辑在里面）
					// 题外：MethodInterceptor extends Interceptor
					// 题外：Interceptor extends Advice
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			/* 3、既不是PointcutAdvisor，也不是IntroductionAdvisor的Advisor，则直接从Advisor中获取Advice对应的所有MethodInterceptor */
			else {
				// ⚠️获取Advisor中Advice对应的所有MethodInterceptor（Advisor适配器的逻辑在里面）
				// 题外：MethodInterceptor extends Interceptor
				// 题外：Interceptor extends Advice
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * 判断"能够增强当前bean的Advisor"中是否存在一个能够匹配当前目标类的引介增强
	 *
	 * Determine whether the Advisors contain matching introductions. —— 确定Advisor是否包含匹配的introductions(介绍)
	 *
	 * @param advisors			在创建代理对象之初，查找到的，能够增强当前bean的Advisor
	 * @param actualClass		目标类
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		/* 遍历"能够增强当前bean的Advisor"，判断其中是否存在一个能够匹配当前目标类的引介增强 */
		// 遍历"能够增强当前bean的Advisor"
		for (Advisor advisor : advisors) {
			// 如果Advisor是IntroductionAdvisor引介增强，就进行类粒度的匹配
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				// 通过IntroductionAdvisor获取ClassFilter，
				// 然后调用ClassFilter#matches()判断当前类是否匹配
				if (ia.getClassFilter().matches(actualClass)) {
					// 只要有一个引介增强，可以匹配当前目标类就返回true
					return true;
				}
			}
		}

		return false;
	}

}
