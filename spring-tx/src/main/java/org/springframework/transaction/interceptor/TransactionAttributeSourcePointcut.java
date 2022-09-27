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

package org.springframework.transaction.interceptor;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * 注解方式声明事务的切入点表达式。而实际上它并有切入点表达式，它只是会去寻找方法上的事务注解，从而判断当前方法是否是事务方法，当前方法是否需要被增强。
 * 例如：{@link #matches(Method,Class)}里面会寻找方法上的@Transaction，构建一个TransactionAttribute，从而根据是否有创建TransactionAttribute对象，判断当前方法是不是事务方法，是的话，就代表是
 *
 * Abstract class that implements a Pointcut that matches if the underlying
 * {@link TransactionAttributeSource} has an attribute for a given method.
 *
 * @author Juergen Hoeller
 * @since 2.5.5
 */
@SuppressWarnings("serial")
abstract class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {

	protected TransactionAttributeSourcePointcut() {
		setClassFilter(new TransactionAttributeSourceClassFilter());
	}

	/**
	 * 获取方法所声明的TransactionAttribute(事务属性)，然后通过判断方法是否有声明的TransactionAttribute，来得知当前方法是不是需要被增强。
	 * 如果方法有声明的TransactionAttribute，则证明当前方法是事务方法，需要被增强
	 *
	 * 典型的：寻找和解析方法所声明的@Transactional，构建一个成TransactionAttribute进行缓存和返回。
	 * 如果能够获取得到一个TransactionAttribute，则证明当前方法是被@Transactional修饰的方法，是一个事务方法，需要被增强
	 *
	 * @param method 		目标类中的方法
	 * @param targetClass 	目标类
	 */
	@Override
	public boolean matches(Method method, Class<?> targetClass) {
		/* 1、获取事务属性源 */
		/**
		 * 1、开启注解事务支持时，tas = {@link AnnotationTransactionAttributeSource}
		 *
		 * 题外：AnnotationTransactionAttributeSource由来：
		 * 在解析<tx:annotation-driven>标签，或者是解析@EnableTransactionManagement时，
		 * 会注册{@link AnnotationTransactionAttributeSource}和{@link BeanFactoryTransactionAttributeSourceAdvisor}，
		 * 并往BeanFactoryTransactionAttributeSourceAdvisor当中设置AnnotationTransactionAttributeSource，
		 * 以及在实例化BeanFactoryTransactionAttributeSourceAdvisor的时候，会实例化TransactionAttributeSourcePointcut，
		 * 并且把AnnotationTransactionAttributeSource作为TransactionAttributeSourcePointcut#getTransactionAttributeSource()的返回值，
		 * 所以就有AnnotationTransactionAttributeSource了
		 */
		TransactionAttributeSource tas = getTransactionAttributeSource();

		/*

		2、先获取方法所声明的TransactionAttribute(事务属性)，然后通过判断是否有声明的TransactionAttribute，来得知当前方法是不是需要被增强。
		如果有TransactionAttribute，则证明当前方法是事务方法，需要被增强。

		例如：寻找和解析方法所声明的@Transactional，构建一个成TransactionAttribute返回。
		>>> 如果能够获取得到一个TransactionAttribute，则证明当前方法是被@Transactional修饰的方法，是一个事务方法，需要被增强

		*/
		/**
		 * 1、tas.getTransactionAttribute(method, targetClass)
		 *
		 * (1) AnnotationTransactionAttributeSource#getTransactionAttribute()逻辑：寻找和解析方法上的事务注解，构建一个TransactionAttribute返回
		 *
		 * 最典型的就是寻找和解析方法上的@Transaction，构建一个TransactionAttribute进行缓存和返回
		 *
		 * 题外：由于AnnotationTransactionAttributeSource extends AbstractFallbackTransactionAttributeSource，所以走的是AbstractFallbackTransactionAttributeSource
		 */
		return (tas == null || tas.getTransactionAttribute(method, targetClass) != null);
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TransactionAttributeSourcePointcut)) {
			return false;
		}
		TransactionAttributeSourcePointcut otherPc = (TransactionAttributeSourcePointcut) other;
		return ObjectUtils.nullSafeEquals(getTransactionAttributeSource(), otherPc.getTransactionAttributeSource());
	}

	@Override
	public int hashCode() {
		return TransactionAttributeSourcePointcut.class.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + getTransactionAttributeSource();
	}


	/**
	 * Obtain the underlying TransactionAttributeSource (may be {@code null}).
	 * To be implemented by subclasses.
	 */
	@Nullable
	protected abstract TransactionAttributeSource getTransactionAttributeSource();


	/**
	 * {@link ClassFilter} that delegates to {@link TransactionAttributeSource#isCandidateClass}
	 * for filtering classes whose methods are not worth searching to begin with.
	 */
	private class TransactionAttributeSourceClassFilter implements ClassFilter {

		@Override
		public boolean matches(Class<?> clazz) {
			if (TransactionalProxy.class.isAssignableFrom(clazz) ||
					TransactionManager.class.isAssignableFrom(clazz) ||
					PersistenceExceptionTranslator.class.isAssignableFrom(clazz)) {
				return false;
			}
			TransactionAttributeSource tas = getTransactionAttributeSource();
			return (tas == null || tas.isCandidateClass(clazz));
		}
	}

}
