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
 * 顾名思义，ConverterFactory就是产生Converter的一个工厂，用来获取对应的转换器。
 *
 * 注意：⚠️但是通过ConverterFactory获取的Converter和我们直接定义的Converter不同：ConverterFactory获取的Converter，它的目标类型必须是ConverterFactory目标类型的子类或者相同
 * 题外：ConverterFactory接口只支持从一个原类型转换为一个目标类型对应的子类型。
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
	 * 生产Converter（获取转换器）
	 *
	 * 题外：S是源类型，T是目标类型，T是R的一个实例(T要么是R的子类，或者T就是R)
	 *
	 * Get the converter to convert from S to target type T, where T is also an instance of R.
	 * 让转换器从 S 转换为目标类型 T，其中 T 也是 R 的一个实例。
	 *
	 * @param <T> the target type
	 *
	 * @param targetType the target type to convert to
	 *                   目标类型
	 *
	 * @return a converter from S to T —— 从S到T的转换器
	 */
	// <T extends R>,其中的【extends R】是范型的上限，包含了具体的一些子类实现，就是说有N对N的转换关系
	<T extends R> Converter<S, T> getConverter(Class<T> targetType);

}
