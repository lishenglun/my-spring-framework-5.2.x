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

package org.springframework.aop.framework;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.lang.Nullable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring's implementation of the AOP Alliance
 * {@link org.aopalliance.intercept.MethodInvocation} interface,
 * implementing the extended
 * {@link org.springframework.aop.ProxyMethodInvocation} interface.
 *
 * <p>Invokes the target object using reflection. Subclasses can override the
 * {@link #invokeJoinpoint()} method to change this behavior, so this is also
 * a useful base class for more specialized MethodInvocation implementations.
 *
 * <p>It is possible to clone an invocation, to invoke {@link #proceed()}
 * repeatedly (once per clone), using the {@link #invocableClone()} method.
 * It is also possible to attach custom attributes to the invocation,
 * using the {@link #setUserAttribute} / {@link #getUserAttribute} methods.
 *
 * <p><b>NOTE:</b> This class is considered internal and should not be
 * directly accessed. The sole reason for it being public is compatibility
 * with existing framework integrations (e.g. Pitchfork). For any other
 * purposes, use the {@link ProxyMethodInvocation} interface instead.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @see #invokeJoinpoint
 * @see #proceed
 * @see #invocableClone
 * @see #setUserAttribute
 * @see #getUserAttribute
 */
public class ReflectiveMethodInvocation/* 反射的方法执行器 */ implements ProxyMethodInvocation, Cloneable {

	// 代理对象
	protected final Object proxy;
	// 目标对象
	@Nullable
	protected final Object target;
	// 目标方法
	protected final Method method;
	// 入参
	protected Object[] arguments;
	// 目标类
	@Nullable
	private final Class<?> targetClass;
	/**
	 * Lazily initialized map of user-specific attributes for this invocation. —— 此调用的用户特定属性的延迟初始化映射。
	 */
	@Nullable
	private Map<String, Object> userAttributes;
	/**
	 * List of MethodInterceptor and InterceptorAndDynamicMethodMatcher
	 * that need dynamic checks. —— 需要动态检查的MethodInterceptor和InterceptorAndDynamicMethodMatcher列表。
	 */
	// 方法的拦截器链
	// 题外：调用拦截器的顺序，是沿着interceptorOrInterceptionAdvice顺序进行调用的
	protected final List<?> interceptorsAndDynamicMethodMatchers;
	/**
	 * Index from 0 of the current interceptor we're invoking.
	 * -1 until we invoke: then the current interceptor. —— 我们调用的当前拦截器的索引从0开始。-1直到调用:然后是当前拦截器。
	 */
	// 当前拦截器索引
	private int currentInterceptorIndex = -1;


	/**
	 * Construct a new ReflectiveMethodInvocation with the given arguments.
	 *
	 * @param proxy                                the proxy object that the invocation was made on
	 * @param target                               the target object to invoke
	 * @param method                               the method to invoke
	 * @param arguments                            the arguments to invoke the method with
	 * @param targetClass                          the target class, for MethodMatcher invocations
	 * @param interceptorsAndDynamicMethodMatchers interceptors that should be applied,
	 *                                             along with any InterceptorAndDynamicMethodMatchers that need evaluation at runtime.
	 *                                             MethodMatchers included in this struct must already have been found to have matched
	 *                                             as far as was possibly statically. Passing an array might be about 10% faster,
	 *                                             but would complicate the code. And it would work only for static pointcuts.
	 */
	protected ReflectiveMethodInvocation(
			Object proxy, @Nullable Object target, Method method, @Nullable Object[] arguments,
			@Nullable Class<?> targetClass, List<Object> interceptorsAndDynamicMethodMatchers) {
		// 代理对象
		this.proxy = proxy;
		// 目标对象
		this.target = target;
		// 目标类
		this.targetClass = targetClass;
		// 目标方法
		this.method = BridgeMethodResolver.findBridgedMethod(method);
		// 入参
		this.arguments = AopProxyUtils.adaptArgumentsIfNecessary(method, arguments);
		// 方法的拦截器链
		this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
	}


	@Override
	public final Object getProxy() {
		return this.proxy;
	}

	@Override
	@Nullable
	public final Object getThis() {
		return this.target;
	}

	@Override
	public final AccessibleObject getStaticPart() {
		return this.method;
	}

	/**
	 * Return the method invoked on the proxied interface.
	 * May or may not correspond with a method invoked on an underlying
	 * implementation of that interface.
	 */
	@Override
	public final Method getMethod() {
		return this.method;
	}

	@Override
	public final Object[] getArguments() {
		return this.arguments;
	}

	@Override
	public void setArguments(Object... arguments) {
		this.arguments = arguments;
	}


	/**
	 * 递归获取通知，然后执行
	 * <p>
	 * 题外：从【当前拦截器的索引=-1】开始调用当前方法，在获取拦截器之前，提前递增1，得到要执行的拦截器。
	 */
	@Override
	@Nullable
	public Object proceed() throws Throwable {
		// We start with an index of -1 and increment early. —— 我们从-1的索引开始，并提前递增

		/*

		1、执行完所有的拦截器后，执行切点方法(目标方法)

		也就是说，如果当前拦截器是最后一个进来的拦截器，就执行目标方法

		*/
		/**
		 * 1、题外：当前拦截器的索引虽然定为-1，但其实是从索引0开始获取拦截器，因为在获取索引之前会提前递增，后续也是按照顺序依次递增。
		 *
		 * 2、题外：而为什么不从0开始，要从-1开始呢？其实从0开始也可以，但是从0开始，语义就不够准确了，无法准确的代表"当前拦截器的索引"，
		 *
		 * (1)先论证为何从0开始，语义就不够准确了
		 *
		 * 例如：第一次进入当前方法，当前拦截器索引是0，没有问题，但是当我获取完毕当前拦截器之后，我必须要进行0++，变为1，这样我才能获取下一个拦截器；
		 * 然后调用当前拦截器，当前拦截器进入了当前方法，它一看当前拦截器索引等于-1，这明显的语义不正确，当前拦截器其实是0号索引的拦截器的！
		 * 你可能会想，我在获取完毕当前拦截器之后，可不可以不进行0++呢；或者说，不进行0++，而是等到下次，当前拦截器进来的时候，再进行0++呢？
		 * 请注意，这是一套公用的代码逻辑，如果你不进行0++，意味着当前方法当中根本没有0++这个操作，那么当前拦截器进入到当前方法，也不会进行0++操作，永远不会获取到下一个拦截器！
		 * 如果是等到下次，当前拦截器进来的时候，再进行0++，也就意味着，当前方法逻辑中，是先0++再获取拦截器的，基于这么一个逻辑，由于这是一套公共的代码逻辑，那么第一次进入当前方法，索引是0，必然也会先0++，也就是索引变为了1，然后再去获取拦截器，这样就没有从第一个拦截器开始获取了！
		 * 所以基于语义正确性，当前拦截器的索引从-1开始，代表还未获取当前拦截器，也只有在获取拦截器的时候，才会++-1，变为0，代表当前拦截器索引位置，后续当前拦截器进来的时候，当前索引位置为0，代表的就是当前拦截器的索引位置
		 *
		 * (2)为何从-1开始，语义就准确了
		 *
		 * 第一次进入到当前方法，还没有获取拦截器呢，所以"当前拦截器的索引"=-1，语义是准确的。
		 * 然后在获取当前拦截器索引的时候，提前把"当前拦截器的索引"+1=0，保证了是从0开始获取索引，同时保证了当前要执行的拦截器是索引0，这语义是准确的；
		 * 然后调用当前拦截器，当前拦截器进入了当前方法，发现当前拦截器的索引"=0，代表的就是自己索引的位置，语义也是准确的！
		 * 只有当它获取了下一个拦截器，用于执行下一个拦截器了，才会把"当前拦截器的索引"提前+1，这既确保了正确的递增获取下一个拦截器，也保证了"当前拦截器的索引"所代表的语义是准确的！
		 *
		 * 3、切点方法也就是目标方法
		 */
		// 根据"当前拦截器的索引"判断，当前拦截器是不是最后一个拦截器，如果当前拦截器是最后一个拦截器进到此方法，则执行切点方法(目标方法)
		if (this.currentInterceptorIndex/* 当前拦截器的索引 */ == this.interceptorsAndDynamicMethodMatchers.size()/* ️当前方法需要被执行的所有拦截器 */ - 1/* 3 */) {
			// 执行切点方法(目标方法)
			// CglibAopProxy
			return invokeJoinpoint();
		}

		/*

		2、获取下一个要执行的拦截器

		注意：⚠️如果是第一次调用当前方法，那么就是获取当前拦截器；如果是拦截器来调用当前方法，那么就是获取下一个拦截器，这个概念是相对的！

		 */
		// 获取下一个要执行的拦截器
		// 题外：每次在获取下一个要执行的拦截器时，都先递增"当前拦截器的索引"，来获取到下一个要执行的拦截器；
		// >>> 同时，递增后，"当前拦截器的索引"也代表了即将要调用的拦截器；
		// >>> 同时由此得知，调用拦截器的时候，调用拦截器的顺序，是沿着interceptorOrInterceptionAdvice顺序进行调用的
		Object interceptorOrInterceptionAdvice/* 拦截器或拦截建议 */ =
				this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);

		/* 3、动态匹配 */
		// 如果是InterceptorAndDynamicMethodMatcher类型，则进行动态匹配，每次都去判断一下当前拦截器是否适用于目标方法
		// 题外：动态匹配就是说，每次都去判断一下，当前拦截器是否适用于目标方法
		if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher) {
			// Evaluate dynamic method matcher here: static part will already have
			// been evaluated and found to match.
			// 上面翻译：在这里评估动态方法匹配器：静态部分已经被评估并发现匹配。

			// InterceptorAndDynamicMethodMatcher
			// 题外：InterceptorAndDynamicMethodMatcher当中有一个"拦截器"和一个"方法匹配器"
			InterceptorAndDynamicMethodMatcher dm = (InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
			// 目标类
			Class<?> targetClass = (this.targetClass != null ? this.targetClass : this.method.getDeclaringClass());

			/* (1)每次都根据方法匹配器，去判断当前拦截器是否适用于目标方法。如果适用，就调用执行内部的拦截器 */
			// 题外：这里是对pointcut触发进行匹配的地方，如果和定义的pointcut匹配，那么这个advice将会得到执行
			if (dm.methodMatcher.matches(this.method, targetClass, this.arguments)) {
				// 调用执行内部的拦截器
				// 题外：将this作为参数传递，以保证当前实例中，调用链的执行
				return dm.interceptor.invoke(this);
			}
			/* (2)如果不适用，则跳过当前拦截器，不进行执行，而是递归调用链中的下一个拦截器 */
			else {
				// Dynamic matching failed. —— 动态匹配失败。
				// Skip this interceptor and invoke the next in the chain. —— 跳过这个拦截器并调用链中的下一个。

				return proceed();
			}
		}
		/* 4、普通拦截器，直接调用拦截器 */
		else {
			// It's an interceptor, so we just invoke it: The pointcut will have
			// been evaluated statically before this object was constructed.
			// 上面翻译：它是一个拦截器，所以我们只是调用它：在构造这个对象之前，切入点将被静态评估。
			/**
			 * 1、普通拦截器：
			 * ExposeInvocationInterceptor、
			 * DelegatePerTargetObjectIntroductionInterceptor、
			 * MethodBeforeAdviceInterceptor、
			 * AspectJAroundAdvice、
			 * AspectJAfterAdvice
			 */
			// 题外：将this作为参数传递，以保证当前实例中，调用链的执行
			// this = CglibMethodInvocation
			// this = ReflectiveMethodInvocation
			return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
		}
	}

	/**
	 * Invoke the joinpoint using reflection.
	 * Subclasses can override this to use custom invocation.
	 *
	 * @return the return value of the joinpoint
	 * @throws Throwable if invoking the joinpoint resulted in an exception
	 */
	@Nullable
	protected Object invokeJoinpoint() throws Throwable {
		return AopUtils.invokeJoinpointUsingReflection(this.target/* 目标对象 */, this.method/* 目标方法 */, this.arguments/* 目标方法的参数 */);
	}


	/**
	 * This implementation returns a shallow copy of this invocation object,
	 * including an independent copy of the original arguments array.
	 * <p>We want a shallow copy in this case: We want to use the same interceptor
	 * chain and other object references, but we want an independent value for the
	 * current interceptor index.
	 *
	 * @see java.lang.Object#clone()
	 */
	@Override
	public MethodInvocation invocableClone() {
		Object[] cloneArguments = this.arguments;
		if (this.arguments.length > 0) {
			// Build an independent copy of the arguments array.
			cloneArguments = this.arguments.clone();
		}
		return invocableClone(cloneArguments);
	}

	/**
	 * This implementation returns a shallow copy of this invocation object,
	 * using the given arguments array for the clone.
	 * <p>We want a shallow copy in this case: We want to use the same interceptor
	 * chain and other object references, but we want an independent value for the
	 * current interceptor index.
	 *
	 * @see java.lang.Object#clone()
	 */
	@Override
	public MethodInvocation invocableClone(Object... arguments) {
		// Force initialization of the user attributes Map,
		// for having a shared Map reference in the clone.
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}

		// Create the MethodInvocation clone.
		try {
			ReflectiveMethodInvocation clone = (ReflectiveMethodInvocation) clone();
			clone.arguments = arguments;
			return clone;
		} catch (CloneNotSupportedException ex) {
			throw new IllegalStateException(
					"Should be able to clone object of type [" + getClass() + "]: " + ex);
		}
	}


	@Override
	public void setUserAttribute(String key, @Nullable Object value) {
		if (value != null) {
			if (this.userAttributes == null) {
				this.userAttributes = new HashMap<>();
			}
			this.userAttributes.put(key, value);
		} else {
			if (this.userAttributes != null) {
				this.userAttributes.remove(key);
			}
		}
	}

	@Override
	@Nullable
	public Object getUserAttribute(String key) {
		return (this.userAttributes != null ? this.userAttributes.get(key) : null);
	}

	/**
	 * Return user attributes associated with this invocation.
	 * This method provides an invocation-bound alternative to a ThreadLocal.
	 * <p>This map is initialized lazily and is not used in the AOP framework itself.
	 *
	 * @return any user attributes associated with this invocation
	 * (never {@code null})
	 */
	public Map<String, Object> getUserAttributes() {
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}
		return this.userAttributes;
	}


	@Override
	public String toString() {
		// Don't do toString on target, it may be proxied.
		StringBuilder sb = new StringBuilder("ReflectiveMethodInvocation: ");
		sb.append(this.method).append("; ");
		if (this.target == null) {
			sb.append("target is null");
		} else {
			sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
		}
		return sb.toString();
	}

}
