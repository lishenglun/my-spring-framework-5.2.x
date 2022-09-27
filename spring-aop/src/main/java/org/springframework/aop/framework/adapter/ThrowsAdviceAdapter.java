/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.aop.framework.adapter;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.ThrowsAdvice;

import java.io.Serializable;

/**
 * 后置异常通知的适配器。支持后置异常通知类。 有一个getInterceptor方法：将Advisor适配为MethodInterceptor.
 * Advisor持有Advice类型的实例.获取ThrowsAdvice，将ThrowsAdvice适配为ThrowsAdviceInterceptor.
 * AOP的拦截过程通过MethodInterceptor来完成
 *
 * 题外：ThrowsAdviceAdapter和AspectJAfterThrowingAdvice返回的拦截器不一样，正常执行的话，返回的是AspectJAfterThrowingAdvice拦截器，
 * 非正常的话，可以自定义操作，返回随意的拦截器
 *
 * Adapter to enable {@link org.springframework.aop.MethodBeforeAdvice}
 * to be used in the Spring AOP framework.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
class ThrowsAdviceAdapter implements AdvisorAdapter, Serializable {

	@Override
	public boolean supportsAdvice(Advice advice) {
		return (advice instanceof ThrowsAdvice);
	}

	@Override
	public MethodInterceptor getInterceptor(Advisor advisor) {
		return new ThrowsAdviceInterceptor(advisor.getAdvice());
	}

}
