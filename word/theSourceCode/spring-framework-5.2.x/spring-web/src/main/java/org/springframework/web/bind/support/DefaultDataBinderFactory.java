/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.bind.support;

import org.springframework.lang.Nullable;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Create a {@link WebRequestDataBinder} instance and initialize it with a
 * {@link WebBindingInitializer}.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class DefaultDataBinderFactory implements WebDataBinderFactory {

	// 全局的@IniterBinder初始化器
	@Nullable
	private final WebBindingInitializer initializer;


	/**
	 * Create a new {@code DefaultDataBinderFactory} instance.
	 * @param initializer for global data binder initialization
	 * (or {@code null} if none)
	 */
	public DefaultDataBinderFactory(@Nullable WebBindingInitializer initializer) {
		this.initializer = initializer;
	}


	/**
	 * 为当前参数创建一个WebDataBinder，然后从当前Controller中的和全局的@InnitBinder方法中，筛选出适用的@InnitBinder方法，进行执行，来初始化WebDataBinder。
	 * 筛选规则：如果@InitBinder没有配置value属性值，或者@InitBinder中配置的value属性值包含当前"参数名称"，则代表适用
	 *
	 * Create a new {@link WebDataBinder} for the given target object and
	 * initialize it through a {@link WebBindingInitializer}.
	 *
	 * 为给定的目标对象创建一个新的 {@link WebDataBinder} 并通过 {@link WebBindingInitializer} 对其进行初始化。
	 *
	 * @throws Exception in case of invalid state or arguments
	 */
	@Override
	@SuppressWarnings("deprecation")
	public final WebDataBinder createBinder(
			NativeWebRequest webRequest, @Nullable Object target/* null */, String objectName/* 参数名称 */) throws Exception {
		/*

		1、为当前方法参数创建一个WebDataBinder对象，里面包含了参数名称

		题外：@InitBinder修饰的方法，必须要有一个入参：WebDataBinder。

		 */
		WebDataBinder dataBinder = createBinderInstance(target, objectName, webRequest);

		/*

		2、（可忽略）使用全局的@IniterBinder初始化器，初始化WebDataBinder。全局的@IniterBinder初始化器一般为null，所以这里不做任何事情。

		 */
		if (this.initializer != null) {
			this.initializer.initBinder(dataBinder, webRequest);
		}

		/*

		3、遍历当前Controller中的和全局的@InnitBinder方法，然后执行适用的@IniterBinder方法，进行执行，来初始化WebDataBinder。
		适用规则：如果@InitBinder没有配置value属性值，或者@InitBinder中配置的value属性值包含当前"参数名称"，则代表适用。

		 */
		// 执行所有的数据绑定器的初始化方法，为当前参数的数据绑定器进行初始化
		// InitBinderDataBinderFactory
		initBinder(dataBinder, webRequest);

		return dataBinder;
	}

	/**
	 * Extension point to create the WebDataBinder instance.
	 * By default this is {@code WebRequestDataBinder}.
	 * @param target the binding target or {@code null} for type conversion only
	 * @param objectName the binding target object name
	 * @param webRequest the current request
	 * @throws Exception in case of invalid state or arguments
	 */
	protected WebDataBinder createBinderInstance(
			@Nullable Object target/* null */, String objectName, NativeWebRequest webRequest) throws Exception {

		return new WebRequestDataBinder(target, objectName);
	}

	/**
	 * Extension point to further initialize the created data binder instance
	 * (e.g. with {@code @InitBinder} methods) after "global" initialization
	 * via {@link WebBindingInitializer}.
	 * @param dataBinder the data binder instance to customize
	 * @param webRequest the current request
	 * @throws Exception if initialization fails
	 */
	protected void initBinder(WebDataBinder dataBinder, NativeWebRequest webRequest)
			throws Exception {

	}

}
