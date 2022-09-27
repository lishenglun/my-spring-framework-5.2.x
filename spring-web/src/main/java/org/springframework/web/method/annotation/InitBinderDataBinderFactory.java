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

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;

import java.util.Collections;
import java.util.List;

/**
 * Adds initialization to a WebDataBinder via {@code @InitBinder} methods.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class InitBinderDataBinderFactory extends DefaultDataBinderFactory {

	// 保存所有的初始化数据绑定器的方法 —— @InitBinder方法
	// 是在创建数据绑定工厂的时候解析出来的，包含了当前Controller内部的和@ControllerAdvice注解全局标识出的@InitBinder方法
	private final List<InvocableHandlerMethod> binderMethods;

	/**
	 * Create a new InitBinderDataBinderFactory instance.
	 * @param binderMethods {@code @InitBinder} methods
	 * @param initializer for global data binder initialization
	 */
	public InitBinderDataBinderFactory(@Nullable List<InvocableHandlerMethod> binderMethods,
			@Nullable WebBindingInitializer initializer) {

		super(initializer);
		this.binderMethods = (binderMethods != null ? binderMethods : Collections.emptyList());
	}


	/**
	 * Initialize a WebDataBinder with {@code @InitBinder} methods.
	 * <p>If the {@code @InitBinder} annotation specifies attributes names,
	 * it is invoked only if the names include the target object name.
	 * @throws Exception if one of the invoked @{@link InitBinder} methods fails
	 * @see #isBinderMethodApplicable
	 */
	@Override
	public void initBinder(WebDataBinder dataBinder, NativeWebRequest request) throws Exception {
		/*

		1、遍历当前Controller中的和全局的@InnitBinder方法，判断是否可以执行@InitBinder方法，来初始化当前的WebDataBinder。
		如果@InitBinder没有配置value属性值，或者@InitBinder中配置的value属性值包含当前"参数名称"，则代表可以执行。
		然后执行@InitBinder方法，始化当前的WebDataBinder。

		 */
		for (InvocableHandlerMethod binderMethod : this.binderMethods) {
			// （1）判断是否可以执行这个@InitBinder方法，来初始化当前的WebDataBinder
			// >>> 如果@InitBinder没有配置value属性值，或者@InitBinder中的value属性值包含当前"参数名称"，则代表可以执行
			if (isBinderMethodApplicable/* 是否适用Binder方法 */(binderMethod, dataBinder)) {
				// （2）调用@InitBidner方法，把当前参数的WebDataBinder作为方法入参，并获取返回值
				Object returnValue = binderMethod.invokeForRequest(request, null, dataBinder);
				// （3）如果执行@InitBinder方法有返回值，就报错；也就是说，@InitBinder方法，不能有返回值，需要是void。
				if (returnValue != null) {
					throw new IllegalStateException(
							// @InitBinder方法不得返回值（应为void）：
							"@InitBinder methods must not return a value (should be void): " + binderMethod);
				}
			}
		}
	}

	/**
	 * 判断"当前@InitBinder方法"是否适用于"当前方法参数"
	 *
	 * 判断指定的方式（当前方法参数）是否需要进行初始化操作
	 *
	 * Determine whether the given {@code @InitBinder} method should be used
	 * to initialize the given {@link WebDataBinder} instance. By default we
	 * check the specified attribute names in the annotation value, if any.
	 */
	protected boolean isBinderMethodApplicable/* 是否适用Binder方法 */(HandlerMethod initBinderMethod/* @InitBinder方法 */, WebDataBinder dataBinder) {
		/* 1、获取@InitBinder中的value属性值 */
		// 获取@InitBinder
		InitBinder ann = initBinderMethod.getMethodAnnotation(InitBinder.class);
		Assert.state(ann != null, "No InitBinder annotation");
		// 获取@InitBinder中的value属性值
		String[] names = ann.value();

		/*

		2、如果【@InitBinder中的value属性值为空，或者@InitBinder中的value属性值包含当前参数名称】，
		则代表当前@InitBinder方法可以应用于当前方法参数

		题外：从这里可以看出，一个未设置任何属性值的@InitBinder方法，适用于所有的方法参数。

		*/
		return (ObjectUtils.isEmpty(names) || ObjectUtils.containsElement(names, dataBinder.getObjectName()/* 参数名称 */));
	}

}
