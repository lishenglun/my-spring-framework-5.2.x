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

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * JDK-based {@link AopProxy} implementation for the Spring AOP framework,
 * based on JDK {@link java.lang.reflect.Proxy dynamic proxies}.
 *
 * <p>Creates a dynamic proxy, implementing the interfaces exposed by
 * the AopProxy. Dynamic proxies <i>cannot</i> be used to proxy methods
 * defined in classes, rather than interfaces.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} class. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>Proxies created using this class will be thread-safe if the
 * underlying (target) class is thread-safe.
 *
 * <p>Proxies are serializable so long as all Advisors (including Advices
 * and Pointcuts) and the TargetSource are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {

	/** use serialVersionUID from Spring 1.2 for interoperability. */
	private static final long serialVersionUID = 5531744639992436476L;


	/*
	 * NOTE: We could avoid the code duplication between this class and the CGLIB
	 * proxies by refactoring "invoke" into a template method. However, this approach
	 * adds at least 10% performance overhead versus a copy-paste solution, so we sacrifice
	 * elegance for performance. (We have a good test suite to ensure that the different
	 * proxies behave the same :-)
	 * This way, we can also more easily take advantage of minor optimizations in each class.
	 */

	/** We use a static Log to avoid serialization issues. */
	private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

	/** Config used to configure this proxy. */

	/**
	 * 1、advised = ProxyFactory
	 * 2、参数值由来：
	 * 由{@link DefaultAopProxyFactory#createAopProxy(AdvisedSupport)}内部确定aop代理方式时，
	 * 通过调用{@link JdkDynamicAopProxy#JdkDynamicAopProxy(AdvisedSupport)}构造函数传入的值，
	 * 值为ProxyFactory
	 *
	 * Config used to configure this proxy. —— 用于配置"此代理"的配置。
	 */
	// ProxyFactory
	private final AdvisedSupport advised;

	/**
	 * Is the {@link #equals} method defined on the proxied interfaces?
	 */
	// 是否存在定义了equals()
	private boolean equalsDefined;

	/**
	 * Is the {@link #hashCode} method defined on the proxied interfaces?
	 */
	// 是否存在定义了hashCode()
	private boolean hashCodeDefined;


	/**
	 * Construct a new JdkDynamicAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 * exception in this case, rather than let a mysterious failure happen later.
	 */
	public JdkDynamicAopProxy(AdvisedSupport config/* ProxyFactory */) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		if (config.getAdvisors().length == 0 && config.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE) {
			throw new AopConfigException("No advisors and no TargetSource specified");
		}
		this.advised = config;
	}


	@Override
	public Object getProxy() {
		return getProxy(ClassUtils.getDefaultClassLoader());
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
		}
		// 获取完整的代理接口
		Class<?>[] proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces/* 完整的代理接口 */(this.advised, true);

		// 查找代理接口中定义的equals()和hashCode()
		findDefinedEqualsAndHashCodeMethods/* 查找定义的equals和hashCode方法 */(proxiedInterfaces);

		// ⚠️创建代理对象
		// 题外：this=JdkDynamicAopProxy。JdkDynamicAopProxy#invoke()完成织入的过程。
		return Proxy.newProxyInstance(classLoader, proxiedInterfaces, this/* JdkDynamicAopProxy */);
	}

	/**
	 * 查找代理接口中定义的equals()和hashCode()
	 *
	 * Finds any {@link #equals} or {@link #hashCode} method that may be defined
	 * on the supplied set of interfaces.
	 *
	 * 查找可以在提供的接口集上定义的任何 {@link equals} 或 {@link hashCode} 方法。
	 *
	 * @param proxiedInterfaces the interfaces to introspect
	 */
	private void findDefinedEqualsAndHashCodeMethods(Class<?>[] proxiedInterfaces/* 代理接口 */) {
		for (Class<?> proxiedInterface : proxiedInterfaces) {
			Method[] methods = proxiedInterface.getDeclaredMethods();
			for (Method method : methods) {
				// 这个方法是equals()方法
				if (AopUtils.isEqualsMethod(method)) {
					this.equalsDefined = true;
				}
				// 这个方法是hashCode()方法
				if (AopUtils.isHashCodeMethod(method)) {
					this.hashCodeDefined = true;
				}
				// equals()方法和hashCode()方法都找到了，就没有继续循环查找的必要了，所以直接结束循环，退出当前方法
				if (this.equalsDefined && this.hashCodeDefined) {
					return;
				}
			}
		}
	}


	/**
	 * AOP的核心逻辑，里面完成动态代理织入过程
	 *
	 * Implementation of {@code InvocationHandler.invoke}.
	 * <p>Callers will see exactly the exception thrown by the target,
	 * unless a hook method throws an exception.
	 *
	 * {@code InvocationHandler.invoke}的实现，调用者将准确地看到目标抛出的异常，除非钩子方法抛出异常。
	 */
	@Override
	@Nullable
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object oldProxy = null;
		boolean setProxyContext = false;

		TargetSource targetSource = this.advised.targetSource;
		Object target = null;

		try {
			// equals()方法的处理
			if (!this.equalsDefined && AopUtils.isEqualsMethod(method)) {
				// The target does not implement the equals(Object) method itself. —— 目标不实现equals(Object)方法本身。
				return equals(args[0]);
			}
			// hash()方法的处理
			else if (!this.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
				// The target does not implement the hashCode() method itself. —— 目标不实现hashCode()方法本身。
				return hashCode();
			}
			else if (method.getDeclaringClass() == DecoratingProxy.class) {
				// There is only getDecoratedClass() declared -> dispatch to proxy config. —— 只有声明了getDecoratedClass()->分派到代理配置。
				return AopProxyUtils.ultimateTargetClass(this.advised);
			}
			/**
			 * 1、isAssignableFrom(Class els)：
			 * 调用方法的类或接口，与参数表示的类或接口相同，则返回true；
			 * 或者方法的类或接口，是参数表示的类或接口相同，的父类，则也返回true
			 *
			 * 例子：自身类.class.isAssignableFrom(自身类或子类.class)才返回true
			 * 例子：ArrayList.class.isAssignableFrom(Object.class);为false
			 * 例子：Object.class.isAssignableFrom(ArrayList.class);为false
			 *
			 * 简单概括：参数与调用者相同，或者是它的儿子，则返回true；否则返回false.
			 */
			else if (!this.advised.opaque && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom/* 可分配自 */(Advised.class)) {
				// Service invocations on ProxyConfig with the proxy config... —— 使用代理配置对ProxyConfig进行服务调用...
				return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
			}

			Object retVal;

			// 题外：exposeProxy：有时候目标对象内部的自我调用将无法实施切面中的增强则需要通过此属性暴露代理
			if (this.advised.exposeProxy) {
				// Make invocation available if necessary. —— 如有必要，使调用可用。
				oldProxy = AopContext.setCurrentProxy(proxy);
				setProxyContext = true;
			}

			// Get as late as possible to minimize the time we "own" the target,
			// in case it comes from a pool.
			// 上面翻译：尽可能晚一点，以尽量减少我们“拥有”目标的时间，以防它来自池。
			// 目标对象，targetSource = SingletonTargetSource
			target = targetSource.getTarget();
			// 目标类
			Class<?> targetClass = (target != null ? target.getClass() : null);

			/* 获取能够拦截当前方法的拦截器链 */
			// Get the interception chain for this method. —— 获取此方法的拦截链。
			List<Object> chain = this.advised/* ProxyFactory */.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

			// Check whether we have any advice. If we don't, we can fallback on direct
			// reflective invocation of the target, and avoid creating a MethodInvocation.
			// 检查我们是否有任何建议。如果我们不这样做，我们可以回退到目标的直接反射调用，并避免创建 MethodInvocation。

			/* 如果拦截器链为空，则直接调用目标方法 */
			// 题外：切点方法，目标方法，都是一个意思
			if (chain.isEmpty()) {
				// We can skip creating a MethodInvocation: just invoke the target directly
				// Note that the final invoker must be an InvokerInterceptor so we know it does
				// nothing but a reflective operation on the target, and no hot swapping or fancy proxying.
				// 我们可以跳过创建MethodInvocation：直接调用目。标请注意，最终调用者必须是InvokerInterceptor，因此我们知道它只对目标执行反射操作，并且没有热交换或花哨的代理。

				Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
				retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
			}
			/* 有拦截器，则执行方法的拦截器链 */
			else {
				// We need to create a method invocation... —— 我们需要创建一个方法调用...
				/**
				 * 1、题外：jdk中执行方法的拦截器链，用的是ReflectiveMethodInvocation，而cglib用的是CglibMethodInvocation。
				 * CglibMethodInvocation extends ReflectiveMethodInvocation，所以用的proceed()是ReflectiveMethodInvocation中的proceed()
				 *
				 * CglibMethodInvocation/ReflectiveMethodInvocation：作为控制整个拦截器链调用的中枢，控制整个拦截器链的调用
				 */
				MethodInvocation invocation = new ReflectiveMethodInvocation/* 反射方法调用 */(proxy, target, method, args, targetClass, chain/* 拦截器链 */);
				// Proceed to the joinpoint through the interceptor chain. —— 通过拦截器链，前往连接点
				// ⚠️执行拦截器链（里面实现了拦截器的逐一调用）
				retVal = invocation.proceed();
			}
			/* 返回结果 */
			// Massage return value if necessary. —— 如有必要，按摩返回值。
			Class<?> returnType = method.getReturnType();
			if (retVal != null && retVal == target &&
					returnType != Object.class && returnType.isInstance(proxy) &&
					!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
				// Special case: it returned "this" and the return type of the method
				// is type-compatible. Note that we can't help if the target sets
				// a reference to itself in another returned object.
				// 特殊情况：它返回“this”并且方法的返回类型是类型兼容的。请注意，如果目标在另一个返回的对象中设置对自身的引用，我们将无能为力。

				retVal = proxy;
			}
			else if (retVal == null && returnType != Void.TYPE && returnType.isPrimitive()) {
				throw new AopInvocationException(
						// 来自通知的空返回值与以下的原始返回类型不匹配：
						"Null return value from advice does not match primitive return type for: " + method);
			}
			return retVal;
		}
		finally {
			if (target != null && !targetSource.isStatic()) {
				// Must have come from TargetSource. —— 必须来自TargetSource。
				targetSource.releaseTarget(target);
			}
			if (setProxyContext) {
				// Restore old proxy. —— 恢复旧代理。
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Equality means interfaces, advisors and TargetSource are equal.
	 * <p>The compared object may be a JdkDynamicAopProxy instance itself
	 * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		JdkDynamicAopProxy otherProxy;
		if (other instanceof JdkDynamicAopProxy) {
			otherProxy = (JdkDynamicAopProxy) other;
		}
		else if (Proxy.isProxyClass(other.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(other);
			if (!(ih instanceof JdkDynamicAopProxy)) {
				return false;
			}
			otherProxy = (JdkDynamicAopProxy) ih;
		}
		else {
			// Not a valid comparison...
			return false;
		}

		// If we get here, otherProxy is the other AopProxy.
		return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
	}

	/**
	 * Proxy uses the hash code of the TargetSource.
	 */
	@Override
	public int hashCode() {
		return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}

}
