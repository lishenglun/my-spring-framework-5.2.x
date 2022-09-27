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

package org.springframework.web.method.support;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves method parameters by delegating to a list of registered
 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
 * Previously resolved method parameters are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class HandlerMethodArgumentResolverComposite implements HandlerMethodArgumentResolver {

	private final List<HandlerMethodArgumentResolver> argumentResolvers = new LinkedList<>();

	// 参数解析器缓存 - 把已经寻找好的"给定方法参数"对应的"参数解析器"放入缓存中，方便下次快捷获取
	// key：给定方法参数
	// value：参数解析器
	private final Map<MethodParameter, HandlerMethodArgumentResolver> argumentResolverCache =
			new ConcurrentHashMap<>(256);


	/**
	 * Add the given {@link HandlerMethodArgumentResolver}.
	 */
	public HandlerMethodArgumentResolverComposite addResolver(HandlerMethodArgumentResolver resolver) {
		this.argumentResolvers.add(resolver);
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 * @since 4.3
	 */
	public HandlerMethodArgumentResolverComposite addResolvers(
			@Nullable HandlerMethodArgumentResolver... resolvers) {

		if (resolvers != null) {
			Collections.addAll(this.argumentResolvers, resolvers);
		}
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 */
	public HandlerMethodArgumentResolverComposite addResolvers(
			@Nullable List<? extends HandlerMethodArgumentResolver> resolvers) {

		if (resolvers != null) {
			this.argumentResolvers.addAll(resolvers);
		}
		return this;
	}

	/**
	 * Return a read-only list with the contained resolvers, or an empty list.
	 */
	public List<HandlerMethodArgumentResolver> getResolvers() {
		return Collections.unmodifiableList(this.argumentResolvers);
	}

	/**
	 * Clear the list of configured resolvers.
	 * @since 4.3
	 */
	public void clear() {
		this.argumentResolvers.clear();
	}


	/**
	 * Whether the given {@linkplain MethodParameter method parameter} is
	 * supported by any registered {@link HandlerMethodArgumentResolver}.
	 *
	 * 任何注册的 {@link HandlerMethodArgumentResolver} 是否支持给定的 {@linkplain MethodParameter 方法参数}。
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 获取"给定方法参数"对应的"参数解析器"
		// 如果不为null，则代表有对应的参数解析器，则代表支持解析该"给定方法参数"
		return getArgumentResolver(parameter) != null;
	}

	/**
	 * Iterate over registered
	 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}
	 * and invoke the one that supports it.
	 * @throws IllegalArgumentException if no suitable argument resolver is found
	 */
	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		/* 1、获取方法参数对应的参数解析器 */
		HandlerMethodArgumentResolver resolver = getArgumentResolver(parameter);
		if (resolver == null) {
			throw new IllegalArgumentException("Unsupported parameter type [" +
					parameter.getParameterType().getName() + "]. supportsParameter should be called first.");
		}

		/* 2、用参数解析器解析参数 */
		// AbstractNamedValueMethodArgumentResolver
		return resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
	}

	/**
	 * 获取"给定方法参数"对应的"参数解析器" —— 遍历所有的参数解析器，判断是否有"参数解析器"支持解析"该方法参数"，有的话就获取该参数解析器
	 *
	 * Find a registered {@link HandlerMethodArgumentResolver} that supports
	 * the given method parameter.
	 *
	 * 查找支持"给定方法参数"的已注册{@link HandlerMethodArgumentResolver}。
	 */
	@Nullable
	private HandlerMethodArgumentResolver getArgumentResolver(MethodParameter/* 方法参数 */ parameter) {
		// 先从缓存中获取参数解析器
		HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
		// 如果缓存中没有对应的参数解析器
		if (result == null) {
			// 遍历所有的参数解析器
			for (HandlerMethodArgumentResolver resolver : this.argumentResolvers) {
				// 判断该参数解析器，是否支持解析该参数
				if (resolver.supportsParameter(parameter)) {
					// 支持解析该参数的参数解析器
					result = resolver;
					this.argumentResolverCache.put(parameter, result);
					/**
					 * 从这里可以看出，因为是只获取一个参数解析器，而参数解析器，一般是对某个注解的处理，
					 * >>> 所以从这里可以看出，参数上只能写某个注解，如果写了很多注解，这些注解都是不同的参数解析器进行处理，
					 * >>> 那么也只有一个参数上的注解会生效！
					 */
					// ⚠️获取到一个能够解析该参数的参数解析器就返回了，不会再往下面寻找了
					break;
				}
			}
		}
		return result;
	}

}
