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

package org.springframework.aop.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.PriorityOrdered;

import java.io.Serializable;

/**
 * ExposeInvocationInterceptor就是用来传递MethodInvocation的：
 * 在后续的任何下调用链环节，只要需要用到当前的MethodInvocation，就可以通过ExposeInvocationInterceptor.currentInvocation()获取得到，而不需要一直进行传递，
 * 所以它并不是实际意义上存在的消息通知，只是用来保存整个拦截器链里面所通用的MethodInvocation对象，和为了返回拦截器链里面我需要的MethodInvocation对象，不管什么时候我都能够获取得到MethodInvocation，仅此而已
 *
 * Interceptor that exposes the current {@link org.aopalliance.intercept.MethodInvocation}
 * as a thread-local object. We occasionally need to do this; for example, when a pointcut
 * (e.g. an AspectJ expression pointcut) needs to know the full invocation context.
 *
 * 将当前 {@link org.aopalliance.intercept.MethodInvocation} 公开为线程本地对象的拦截器。
 * 我们偶尔需要这样做；例如，当一个切入点（例如 AspectJ 表达式切入点）需要知道完整的调用上下文时。
 *
 * <p>Don't use this interceptor unless this is really necessary. Target objects should
 * not normally know about Spring AOP, as this creates a dependency on Spring API.
 * Target objects should be plain POJOs as far as possible.
 *
 * <p>If used, this interceptor will normally be the first in the interceptor chain.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public final class ExposeInvocationInterceptor implements MethodInterceptor, PriorityOrdered, Serializable {

	/** Singleton instance of this class. */
	public static final ExposeInvocationInterceptor INSTANCE = new ExposeInvocationInterceptor();

	/**
	 * Singleton advisor for this class. Use in preference to INSTANCE when using
	 * Spring AOP, as it prevents the need to create a new Advisor to wrap the instance.
	 */
	public static final Advisor ADVISOR = new DefaultPointcutAdvisor(INSTANCE/* ⚠️ */) {
		@Override
		public String toString() {
			return ExposeInvocationInterceptor.class.getName() +".ADVISOR";
		}
	};

	private static final ThreadLocal<MethodInvocation> invocation =
			new NamedThreadLocal<>("Current AOP method invocation");


	/**
	 * 此处是继续调用ReflectiveMethodInvocation的proceed()方法来进行递归调用
	 *
	 * Return the AOP Alliance MethodInvocation object associated with the current invocation.
	 * @return the invocation object associated with the current invocation
	 * @throws IllegalStateException if there is no AOP invocation in progress,
	 * or if the ExposeInvocationInterceptor was not added to this interceptor chain
	 */
	public static MethodInvocation currentInvocation() throws IllegalStateException {
		// 返回我们当前正在执行的MethodInvocation
		// 也就是说当我暴露好当前对象之后，我在进行调用的时候，可以直接通过该方法进行获取
		MethodInvocation mi = invocation.get();
		if (mi == null) {
			throw new IllegalStateException(
					"No MethodInvocation found: Check that an AOP invocation is in progress and that the " +
					"ExposeInvocationInterceptor is upfront in the interceptor chain. Specifically, note that " +
					"advices with order HIGHEST_PRECEDENCE will execute before ExposeInvocationInterceptor! " +
					"In addition, ExposeInvocationInterceptor and ExposeInvocationInterceptor.currentInvocation() " +
					"must be invoked from the same thread.");
		}
		return mi;
	}


	/**
	 * Ensures that only the canonical instance can be created. —— 确保只能创建规范实例。
	 */
	private ExposeInvocationInterceptor() {
	}

	@Override
	public Object invoke(MethodInvocation mi/* ReflectiveMethodInvocation */) throws Throwable {
		// 获取上一个MethodInvocation(最开始为null)，作为当前方法的局部变量保存，
		// 因为接下来要修改invocation中的MethodInvocation了，后面操作完，要把上一个MethodInvocation给它还回去，所以获取一下，作为当前方法的局部变量保存
		MethodInvocation oldInvocation = invocation.get();

		// 设置为当前的MethodInvocation，因为后续需要获取mi对象，因为mi对象包含了一堆请求参数，mi里面包含了很多值
		invocation.set(mi);

		try {
			return mi.proceed();
		}
		// 设置上一个MethodInvocation，给它还回去
		finally {
			invocation.set(oldInvocation);
		}
	}

	@Override
	public int getOrder() {
		return PriorityOrdered.HIGHEST_PRECEDENCE + 1;
	}

	/**
	 * Required to support serialization. Replaces with canonical instance
	 * on deserialization, protecting Singleton pattern.
	 * <p>Alternative to overriding the {@code equals} method.
	 */
	private Object readResolve() {
		return INSTANCE;
	}

}
