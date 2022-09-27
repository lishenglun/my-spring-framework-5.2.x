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

package org.springframework.transaction.interceptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract implementation of {@link TransactionAttributeSource} that caches
 * attributes for methods and implements a fallback policy: 1. specific target
 * method; 2. target class; 3. declaring method; 4. declaring class/interface.
 *
 * <p>Defaults to using the target class's transaction attribute if none is
 * associated with the target method. Any transaction attribute associated with
 * the target method completely overrides a class transaction attribute.
 * If none found on the target class, the interface that the invoked method
 * has been called through (in case of a JDK proxy) will be checked.
 *
 * <p>This implementation caches attributes by method after they are first used.
 * If it is ever desirable to allow dynamic changing of transaction attributes
 * (which is very unlikely), caching could be made configurable. Caching is
 * desirable because of the cost of evaluating rollback rules.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */
public abstract class AbstractFallbackTransactionAttributeSource implements TransactionAttributeSource {

	/**
	 * Canonical value held in cache to indicate no transaction attribute was
	 * found for this method, and we don't need to look again.
	 */
	// 空事务属性
	@SuppressWarnings("serial")
	private static final TransactionAttribute NULL_TRANSACTION_ATTRIBUTE = new DefaultTransactionAttribute() {
		@Override
		public String toString() {
			return "null";
		}
	};


	/**
	 * Logger available to subclasses.
	 * <p>As this base class is not marked Serializable, the logger will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Cache of TransactionAttributes, keyed by method on a specific target class.
	 * <p>As this base class is not marked Serializable, the cache will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	// 事务属性缓存（缓存方法上的TransactionAttribute）
	// key：MethodClassKey
	// value：TransactionAttribute
	// 例如：方法上有一个@Transaction，则会解析方法上的@Transaction，构建一个TransactionAttribute，然后被缓存到该集合中。大多数也是这种情况。
	private final Map<Object, TransactionAttribute> attributeCache = new ConcurrentHashMap<>(1024);

	/**
	 * 获取方法上所声明的事务属性TransactionAttribute。
	 *
	 * （1）如果实现子类是：AnnotationTransactionAttributeSource，那么AnnotationTransactionAttributeSource#getTransactionAttribute()逻辑：
	 * 寻找和解析方法上的事务注解，构建一个TransactionAttribute返回。最典型的就是寻找和解析方法上的@Transaction，构建一个TransactionAttribute进行缓存和返回。
	 *
	 * Determine the transaction attribute for this method invocation.
	 * <p>Defaults to the class's transaction attribute if no method attribute is found.
	 *
	 * @param method the method for the current invocation (never {@code null})
	 * @param targetClass the target class for this invocation (may be {@code null})
	 * @return a TransactionAttribute for this method, or {@code null} if the method
	 * is not transactional
	 */
	@Override
	@Nullable
	public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		if (method.getDeclaringClass() == Object.class) {
			return null;
		}

		/* 1、先从缓存中获取当前方法的事务属性 */

		// First, see if we have a cached value. —— 首先，看看我们是否有缓存值。
		Object cacheKey = getCacheKey(method, targetClass);
		// 从事务属性缓存中获取TransactionAttribute
		TransactionAttribute cached = this.attributeCache.get(cacheKey);

		/* 1.1、缓存中存在 */
		if (cached != null) {
			// Value will either be canonical value indicating there is no transaction attribute,
			// or an actual transaction attribute.
			// 上面翻译：值可以是表示没有交易属性的规范值，也可以是实际的交易属性。

			/* (1)是一个空事务属性标识，则返回null */
			if (cached == NULL_TRANSACTION_ATTRIBUTE/* 空事务属性 */) {
				return null;
			}
			/* (2)不是空事务属性标识，直接返回缓存中获取到的事务属性 */
			else {
				return cached;
			}
		}
		/* 1.2、缓存中没有 */
		else {
			/*

			(1)寻找方法上的TransactionAttribute
			典型的：寻找和解析方法上的⚠️@Transaction，然后构建一个TransactionAttribute返回。

			*/
			// We need to work it out. —— 我们需要解决它。
			// ⚠️寻找事务声明，用事务声明信息，构建成一个TransactionAttribute(事务属性)对象返回。典型的：寻找和解析@Transactional，构建一个TransactionAttribute返回。
			TransactionAttribute txAttr = computeTransactionAttribute(method, targetClass);

			// Put it in the cache. —— 把它放在缓存中。

			/*

			(2)如果方法上不存在事务属性，则往缓存中放一个空事务属性标识
			之所以要放一个空事务属性标识，是为了下次不再重新检索当前方法上的事务属性，因为已经检索过了，根本就没有事务属性

			 */
			if (txAttr == null) {
				this.attributeCache.put(cacheKey, NULL_TRANSACTION_ATTRIBUTE);
			}
			/* (3)方法上存在事务属性，则直接放入缓存 */
			else {
				String methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
				if (txAttr instanceof DefaultTransactionAttribute) {
					((DefaultTransactionAttribute) txAttr).setDescriptor(methodIdentification);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Adding transactional method '" + methodIdentification + "' with attribute: " + txAttr);
				}
				// ⚠️缓存方法上的事务属性
				this.attributeCache.put(cacheKey, txAttr);
			}
			return txAttr;
		}
	}

	/**
	 * Determine a cache key for the given method and target class.
	 * <p>Must not produce same key for overloaded methods.
	 * Must produce same key for different instances of the same method.
	 * @param method the method (never {@code null})
	 * @param targetClass the target class (may be {@code null})
	 * @return the cache key (never {@code null})
	 */
	protected Object getCacheKey(Method method, @Nullable Class<?> targetClass) {
		return new MethodClassKey(method, targetClass);
	}

	/**
	 * 寻找事务声明，用事务声明信息，构建成一个TransactionAttribute(事务属性)对象返回。典型的：寻找和解析@Transactional，构建一个TransactionAttribute返回。
	 *
	 * 题外：当前方法并没有真正的去做搜寻TransactionAttribute的逻辑，而是搭建了个执行框架，将搜寻事务属性的任务委托给了findTransactionAttribute()，
	 * 而其内部，最终又是委托给了TransactionAnnotationParser接口，典型的实现类是SpringTransactionAnnotationParser：寻找和解析@Transactional信息，构建一个TransactionAttribute返回
	 *
	 * Same signature as {@link #getTransactionAttribute}, but doesn't cache the result.
	 * {@link #getTransactionAttribute} is effectively a caching decorator for this method.
	 * <p>As of 4.1.8, this method can be overridden.
	 * @since 4.1.8
	 * @see #getTransactionAttribute
	 */
	@Nullable
	protected TransactionAttribute computeTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		// Don't allow no-public methods as required. —— 不允许根据需要使用非公共方法。
		/**
		 * 1、allowPublicMethodsOnly()：是否"只允许公共方法(public)可以声明事务"的标识。默认false。
		 */
		// 【只允许public方法可以声明事务 && 不是public方法】，则返回null
		// 也就是说，如果是只允许public方法可以声明事务，但当前方法却不是public方法，就返回null
		if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
			return null;
		}

		// The method may be on an interface, but we need attributes from the target class. —— 该方法可能在接口上，但我们需要来自目标类的属性。
		// If the target class is null, the method will be unchanged. —— 如果目标类为空，则方法将保持不变。

		// method代表接口中的方法，specificMethod代表实现类中的方法
		Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

		/* 1、先查看方法上是否存在事务声明。典型的：寻找和解析方法上存在的@Transactional信息，构建一个TransactionAttribute */
		// First try is the method in the target class. —— 首先尝试的是目标类中的方法。
		// 查看方法上是否存在事务声明
		TransactionAttribute txAttr = findTransactionAttribute(specificMethod);
		if (txAttr != null) {
			return txAttr;
		}

		/* 2、方法上没有事务声明，则查看类上是否有事务声明。典型的：寻找和解析类上存在的@Transactional信息，构建一个TransactionAttribute */
		// Second try is the transaction attribute on the target class. —— 第二次尝试是目标类的事务属性。
		// 查看方法所在类上是否存在事务声明
		txAttr = findTransactionAttribute(specificMethod.getDeclaringClass());
		if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
			return txAttr;
		}

		// specificMethod与method不相等，则代表存在接口。如果存在接口，则到接口中去寻找事务声明
		if (specificMethod != method) {

			/* 3、如果方法和类上都没有事务声明，则去接口方法上查找事务声明。典型的：寻找和解析类上存在的@Transactional信息，构建一个TransactionAttribute */

			// Fallback is to look at the original method. —— Fallback就是看原来的方法。
			// 查找接口方法
			txAttr = findTransactionAttribute(method/* 接口方法 */);
			if (txAttr != null) {
				return txAttr;
			}

			/* 4、如果接口方法上也没有事务声明，则去接口上查找事务声明。典型的：寻找和解析类上存在的@Transactional信息，构建一个TransactionAttribute */

			// Last fallback is the class of the original method. —— 最后一个备用是原始方法的类。
			// 到接口上去寻找
			txAttr = findTransactionAttribute(method.getDeclaringClass()/* 接口(由于是接口方法，所以获取到的是接口) */);
			if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
				return txAttr;
			}
		}

		return null;
	}


	/**
	 * 查找类上的TransactionAttribute(事务属性)。典型的：解析类上存在的@Transactional信息，构建一个TransactionAttribute返回
	 *
	 * Subclasses need to implement this to return the transaction attribute for the
	 * given class, if any.
	 *
	 * 子类需要实现这一点以返回给定类的事务属性（如果有）。
	 *
	 * @param clazz the class to retrieve the attribute for
	 * @return all transaction attribute associated with this class, or {@code null} if none
	 */
	@Nullable
	protected abstract TransactionAttribute findTransactionAttribute(Class<?> clazz);

	/**
	 * 查找方法上的TransactionAttribute(事务属性)。典型的：解析方法上存在的@Transactional信息，构建一个TransactionAttribute返回
	 *
	 * Subclasses need to implement this to return the transaction attribute for the
	 * given method, if any.
	 *
	 * 子类需要实现这一点以返回给定方法的事务属性（如果有）。
	 *
	 * @param method the method to retrieve the attribute for
	 * @return all transaction attribute associated with this method, or {@code null} if none
	 */
	@Nullable
	protected abstract TransactionAttribute findTransactionAttribute(Method method);

	/**
	 * 是否"只允许公共方法(public)可以声明事务"的标识。默认false
	 *
	 * Should only public methods be allowed to have transactional semantics?
	 * <p>The default implementation returns {@code false}.
	 *
	 * 是否应该只允许公共方法具有事务语义？<p>默认实现返回 {@code false}。
	 */
	protected boolean allowPublicMethodsOnly() {
		return false;
	}

}
