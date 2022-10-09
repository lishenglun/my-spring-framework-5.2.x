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

package org.springframework.core.convert.support;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * 将字符串转换为任一枚举类型对象（字符串转换为整个枚举类型层次结构中的枚举的转换逻辑）：从对应枚举类型当中，获取指定名称的枚举实例
 *
 * Converts from a String to a {@link java.lang.Enum} by calling {@link Enum#valueOf(Class, String)}.
 *
 * 通过调用 {@link Enum#valueOf(Class, String)} 将字符串转换为 {@link java.lang.Enum}。
 *
 * @author Keith Donald
 * @author Stephane Nicoll
 * @since 3.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class StringToEnumConverterFactory implements ConverterFactory<String, Enum> {

	/**
	 * 获取转换器
	 *
	 * @param targetType 目标类型
	 */
	@Override
	public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
		// 动态创建一个，可从固定原始类型，转换为整个R类型层次结构中的某个具体类型的转换器
		return new StringToEnum(ConversionUtils.getEnumType/* 获取枚举类型 */(targetType));

		// 自己改写而成的lambda表达式
		// return (source -> {
		// 	if (source.isEmpty()) {
		// 		return null;
		// 	}
		// 	Class<T> enumType = (Class<T>) ConversionUtils.getEnumType/* 获取枚举类型 */(targetType);
		// 	return (T) Enum.valueOf(enumType/* 枚举类型 */, source.trim()/* 去掉字符串中的空格，得到枚举实例名称 */);
		// });
	}

	/**
	 *  将字符串转换为任一枚举类型对象：从对应枚举类型当中，获取指定名称的枚举实例
	 */
	private static class StringToEnum<T extends Enum> implements Converter<String/* 枚举实例名称 */, T> {

		// 枚举类型
		private final Class<T> enumType;

		public StringToEnum(Class<T> enumType) {
			this.enumType = enumType;
		}

		/**
		 * 从对应枚举类型当中，获取指定名称的枚举实例(枚举对象)
		 *
		 * @param source 		原始数据：枚举实例名称
		 * @return		枚举实例
		 */
		@Override
		public T convert(String source) {
			if (source.isEmpty()) {
				// It's an empty enum identifier: reset the enum value to null. —— 这是一个空的枚举标识符：将枚举值重置为null
				return null;
			}
			// 从对应枚举类型当中，根据"枚举实例名称"，获取对应的枚举实例
			return (T) Enum.valueOf(this.enumType/* 枚举类型 */, source.trim()/* 去掉字符串中的空格，得到枚举实例名称 */);
		}

	}

}
