/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.core.convert.converter;

/**
 * 注册或者移除相关的类型转换器
 *
 * >>> 要实现自己的类型转换逻辑我们可以实现Converter接口、ConverterFactory接口和GenericConverter接口。
 * >>> ConverterRegistry接口就分别为这三种类型提供了对应的注册方法。
 * >>> 之所以提供了不同的方法来注册Converter、ConverterFactory、GenericConverter，是因为这三个接口，在接口之间没有任何的关系，也就是互相之间并不实现
 *
 * 题外：虽然Converter接口、ConverterFactory接口和GenericConverter接口之间没有任何的关系，
 * 但是Spring内部在注册Converter实现类和ConverterFactory实现类时是先把它们转换为GenericConverter，之后再统一对GenericConverter进行注册的。
 * 也就是说Spring内部会把Converter和ConverterFactory全部转换为GenericConverter进行注册，在Spring注册的容器中只存在GenericConverter这一种类型转换器。
 * 我想之所以给用户开放Converter接口和ConverterFactory接口是为了让我们能够更方便的实现自己的类型转换器。
 * 基于此，Spring官方也提倡我们在进行一些简单类型转换器定义时更多的使用Converter接口和ConverterFactory接口，在非必要的情况下少使用GenericConverter接口
 *
 * For registering converters with a type conversion system.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 */
public interface ConverterRegistry {

	/**
	 * 注册Converter
	 *
	 * Add a plain converter to this registry.
	 * The convertible source/target type pair is derived from the Converter's parameterized types.
	 * @throws IllegalArgumentException if the parameterized types could not be resolved
	 */
	void addConverter(Converter<?, ?> converter);

	/**
	 * Add a plain converter to this registry.
	 * The convertible source/target type pair is specified explicitly.
	 * <p>Allows for a Converter to be reused for multiple distinct pairs without
	 * having to create a Converter class for each pair.
	 * @since 3.1
	 */
	<S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter);

	/**
	 * 注册GenericConverter
	 *
	 * Add a generic converter to this registry.
	 */
	void addConverter(GenericConverter converter);

	/**
	 * 注册ConverterFactory
	 *
	 * Add a ranged converter factory to this registry.
	 * The convertible source/target type pair is derived from the ConverterFactory's parameterized types.
	 * @throws IllegalArgumentException if the parameterized types could not be resolved
	 */
	void addConverterFactory(ConverterFactory<?, ?> factory);

	/**
	 * Remove any converters from {@code sourceType} to {@code targetType}.
	 * @param sourceType the source type
	 * @param targetType the target type
	 */
	void removeConvertible(Class<?> sourceType, Class<?> targetType);

}
