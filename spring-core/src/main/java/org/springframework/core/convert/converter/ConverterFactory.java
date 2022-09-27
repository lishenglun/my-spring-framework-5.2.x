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

package org.springframework.core.convert.converter;

/**
 * converter转换器的工厂类，用来获取对应的转换器
 *
 * A factory for "ranged" converters that can convert objects from S to subtypes of R.
 *
 * “范围”转换器的工厂，可以将对象从 S 转换为 R 的子类型。
 *
 * 题外：一个类型包含它具体的子类类型，我都可以完成具体的转换操作
 *
 * <p>Implementations may additionally implement {@link ConditionalConverter}.
 *
 * @author Keith Donald
 * @since 3.0
 * @param <S> the source type converters created by this factory can convert from
 * @param <R> the target range (or base) type converters created by this factory can convert to;
 * for example {@link Number} for a set of number subtypes.
 * @see ConditionalConverter
 */
public interface ConverterFactory<S, R> {

	/**
	 * 获取转换器
	 *
	 *
	 *
	 * Get the converter to convert from S to target type T, where T is also an instance of R.
	 * @param <T> the target type
	 * @param targetType the target type to convert to
	 * @return a converter from S to T
	 */
	// <T extends R>,其中的【extends R】是范型的上限，包含了具体的一些子类实现，就是说有N对N的转换关系
	<T extends R> Converter<S, T> getConverter(Class<T> targetType);

}
