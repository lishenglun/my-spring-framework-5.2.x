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

package org.springframework.web.method.annotation;

import org.springframework.core.ExceptionDepthComparator;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Discovers {@linkplain ExceptionHandler @ExceptionHandler} methods in a given class,
 * including all of its superclasses, and helps to resolve a given {@link Exception}
 * to the exception types supported by a given {@link Method}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ExceptionHandlerMethodResolver {

	/**
	 * A filter for selecting {@code @ExceptionHandler} methods.
	 */
	public static final MethodFilter EXCEPTION_HANDLER_METHODS = method ->
			AnnotatedElementUtils.hasAnnotation(method, ExceptionHandler.class);

	// 存储的是当前Controller中，用@ExceptionHandler修饰的方法
	// key = 异常类型 —— @ExceptionHandler中设置的当前方法处理的异常类型，或者是方法参数中的异常类型
	// value = 处理异常类型的方法 —— @ExceptionHandler修饰的Method对象
	private final Map<Class<? extends Throwable>, Method> mappedMethods = new HashMap<>(16);

	private final Map<Class<? extends Throwable>, Method> exceptionLookupCache = new ConcurrentReferenceHashMap<>(16);


	/**
	 * 查找当前类中@ExceptionHandler修饰的方法，和注册异常类型和处理方法的映射关系。
	 *
	 * A constructor that finds {@link ExceptionHandler} methods in the given type.
	 *
	 * 在给定类型中查找{@link ExceptionHandler}方法的构造函数。
	 *
	 * @param handlerType the type to introspect
	 */
	public ExceptionHandlerMethodResolver(Class<?> handlerType) {
		/* 1、查询当前类（是当前Controller，也可能是全局Controller）中被@ExceptionHandler修饰的方法 */
		for (Method method : MethodIntrospector.selectMethods(handlerType, EXCEPTION_HANDLER_METHODS)) {
			/*

			2、遍历@ExceptionHandler修饰的方法，提取方法处理的异常类型，并注册"异常类型"与"处理方法"的映射关系

			注意：1、先是获取当前方法上的@ExceptionHandler中配置的异常类型作为当前方法处理的异常类型；2、如果没有，再获取方法参数上的异常类型，作为当前方法处理的异常类型。
			注意：在注册"异常类型"与"处理方法"的映射关系时，如果"异常类型"之前已经存在"处理方法"，并且不是同一个方法；也就是说有2个方法处理同一异常类型，则抛出异常。

			*/
			// 获取当前方法所处理的异常类型，然后遍历当前方法所处理的异常类型
			for (Class<? extends Throwable> exceptionType/* 当前方法所处理的异常类型 */ : detectExceptionMappings(method)) {
				// 注册"异常类型"与"对应处理的方法"的映射关系，并获取旧的处理方法
				// 如果当前异常类型，存在旧的处理方法，则判断，是否与当前方法一致，不一致，则代表有2个方法处理同一异常类型，则报错
				addExceptionMapping(exceptionType, method);
			}
		}
	}


	/**
	 * 获取方法所处理的异常类型：
	 * （1）先获取当前方法上的@ExceptionHandler中配置的value属性值，作为当前方法所处理的异常类型！
	 * （2）如果当前方法上的@ExceptionHandler中没有配置处理的异常类型，获取方法参数上的异常类型，作为当前方法处理的异常类型
	 * <p>
	 * Extract exception mappings from the {@code @ExceptionHandler} annotation first,
	 * and then as a fallback from the method signature itself.
	 */
	@SuppressWarnings("unchecked")
	private List<Class<? extends Throwable>> detectExceptionMappings(Method method) {
		// 存储的是当前方法所处理的异常类
		List<Class<? extends Throwable>> result = new ArrayList<>();
		/* 1、先获取当前方法上的@ExceptionHandler中配置的value属性值，作为当前方法所处理的异常类型！ */
		detectAnnotationExceptionMappings(method, result);
		/* 2、如果当前方法上的@ExceptionHandler中没有配置处理的异常类型，获取方法参数上的异常类型，作为当前方法处理的异常类型 */
		// 如果当前方法上的@ExceptionHandler中没有配置处理的异常类型，则获取方法的参数类型，
		// 逐一判断参数类型是不是异常类型(Throwable)，是的话，就使用参数类型作为当前方法所处理的异常类型
		if (result.isEmpty()) {
			// 获取参数，遍历参数
			for (Class<?> paramType : method.getParameterTypes()) {
				// 判断参数是不是异常类型
				if (Throwable.class.isAssignableFrom(paramType)) {
					// 是的话，就使用参数类型作为当前方法所处理的异常类型
					result.add((Class<? extends Throwable>) paramType);
				}
			}
		}
		if (result.isEmpty()) {
			throw new IllegalStateException("No exception types mapped to " + method/* 当前方法没有映射到的异常类型 */);
		}
		return result;
	}

	/**
	 * 获取当前方法上的@ExceptionHandler的value属性，代表当前方法所处理的异常！
	 *
	 * @param method 当前方法
	 * @param result 当前方法所处理的异常集合
	 */
	private void detectAnnotationExceptionMappings(Method method, List<Class<? extends Throwable>> result) {
		// 获取当前方法上的@ExceptionHandler
		ExceptionHandler ann = AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class);
		Assert.state(ann != null, "No ExceptionHandler annotation");
		// 获取当前方法上的@ExceptionHandler上的value属性，代表当前方法所处理的异常！
		result.addAll(Arrays.asList(ann.value()));
	}

	/**
	 * 注册"异常类型"与"对应处理的方法"的映射关系
	 *
	 * @param exceptionType @ExceptionHandler中设置的当前方法处理的异常类型，或者是方法参数中的异常类型
	 * @param method        @ExceptionHandler修饰的Method对象
	 */
	private void addExceptionMapping(Class<? extends Throwable> exceptionType, Method method) {
		// 注册"异常类型"与"对应的处理方法"的映射关系，并获取旧的处理方法
		Method oldMethod = this.mappedMethods.put(exceptionType, method);
		// 如果当前异常类型，存在旧的处理方法，则判断，是否与当前方法一致
		// 如果不一致，则代表有2个方法处理同一异常类型，则报错
		if (oldMethod != null && !oldMethod.equals(method)) {
			// 为 [" + exceptionType + "] 映射的模棱两可的 @ExceptionHandler 方法：{" + oldMethod + ", " + method + "}
			throw new IllegalStateException("Ambiguous @ExceptionHandler method mapped for [" +
					exceptionType + "]: {" + oldMethod + ", " + method + "}");
		}
	}

	/**
	 * Whether the contained type has any exception mappings.
	 */
	public boolean hasExceptionMappings() {
		return !this.mappedMethods.isEmpty();
	}

	/**
	 * Find a {@link Method} to handle the given exception.
	 * Use {@link ExceptionDepthComparator} if more than one match is found.
	 *
	 * @param exception the exception
	 * @return a Method to handle the exception, or {@code null} if none found
	 */
	@Nullable
	public Method resolveMethod(Exception exception) {
		return resolveMethodByThrowable(exception);
	}

	/**
	 * Find a {@link Method} to handle the given Throwable.
	 * Use {@link ExceptionDepthComparator} if more than one match is found.
	 *
	 * @param exception the exception
	 * @return a Method to handle the exception, or {@code null} if none found
	 * @since 5.0
	 */
	@Nullable
	public Method resolveMethodByThrowable(Throwable exception) {
		// ⚠️根据异常类型，获取到用哪个方法来进行处理
		Method method = resolveMethodByExceptionType(exception.getClass()/* 获取异常的类型，例如：NullPointerException */);
		if (method == null) {
			Throwable cause = exception.getCause();
			if (cause != null) {
				method = resolveMethodByExceptionType(cause.getClass());
			}
		}
		return method;
	}

	/**
	 * Find a {@link Method} to handle the given exception type. This can be
	 * useful if an {@link Exception} instance is not available (e.g. for tools).
	 *
	 * @param exceptionType the exception type
	 * @return a Method to handle the exception, or {@code null} if none found
	 */
	@Nullable
	public Method resolveMethodByExceptionType(Class<? extends Throwable> exceptionType) {
		Method method = this.exceptionLookupCache.get(exceptionType);
		if (method == null) {
			// ⚠️获取能够匹配处理当前异常的Method
			method = getMappedMethod(exceptionType);
			this.exceptionLookupCache.put(exceptionType, method);
		}
		return method;
	}

	/**
	 * Return the {@link Method} mapped to the given exception type, or {@code null} if none.
	 */
	@Nullable
	private Method getMappedMethod(Class<? extends Throwable> exceptionType) {
		List<Class<? extends Throwable>> matches = new ArrayList<>();

		// 匹配可以处理当前异常类型的Method（可以匹配到多个）
		for (Class<? extends Throwable> mappedException : this.mappedMethods.keySet()) {
			// 判断是不是这个类型
			if (mappedException.isAssignableFrom(exceptionType)) {
				matches.add(mappedException);
			}
		}

		// 排序，取第一个
		if (!matches.isEmpty()) {
			matches.sort(new ExceptionDepthComparator(exceptionType));
			return this.mappedMethods.get(matches.get(0));
		} else {
			return null;
		}
	}

}
