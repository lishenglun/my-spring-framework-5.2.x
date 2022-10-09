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

package org.springframework.core.convert.support;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Internal utilities for the conversion package.
 *
 * @author Keith Donald
 * @author Stephane Nicoll
 * @since 3.0
 */
abstract class ConversionUtils {

	/**
	 * 调用转换器，转换数据，得到转换后的目标类型数据返回
	 *
	 * @param converter			GenericConverter
	 * @param source 			原始数据
	 * @param sourceType 		原始类型
	 * @param targetType 		目标类型
	 */
	@Nullable
	public static Object invokeConverter(GenericConverter converter, @Nullable Object source/* 源数据 */,
			TypeDescriptor sourceType, TypeDescriptor targetType) {

		try {
			/**
			 * 1、{@link GenericConversionService.ConverterAdapter#convert(Object, TypeDescriptor, TypeDescriptor)}
			 * 2、{@link GenericConversionService.ConverterFactoryAdapter#convert(Object, TypeDescriptor, TypeDescriptor)}
			 * 3、{@link GenericConverter#convert(Object, TypeDescriptor, TypeDescriptor)} —— 直接调用实现类
			 * 4、如果没有转换器的话，就是{@link GenericConversionService.NoOpConverter#convert(Object, TypeDescriptor, TypeDescriptor)}，直接返回原始数据
			 */
			// ⚠️调用转换器
			return converter.convert(source, sourceType, targetType);
		}
		catch (ConversionFailedException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new ConversionFailedException(sourceType, targetType, source, ex);
		}
	}

	public static boolean canConvertElements(@Nullable TypeDescriptor sourceElementType,
			@Nullable TypeDescriptor targetElementType, ConversionService conversionService) {

		if (targetElementType == null) {
			// yes
			return true;
		}
		if (sourceElementType == null) {
			// maybe
			return true;
		}
		if (conversionService.canConvert(sourceElementType, targetElementType)) {
			// yes
			return true;
		}
		if (ClassUtils.isAssignable(sourceElementType.getType(), targetElementType.getType())) {
			// maybe
			return true;
		}
		// no
		return false;
	}

	public static Class<?> getEnumType(Class<?> targetType) {
		Class<?> enumType = targetType;
		while (enumType != null && !enumType.isEnum()) {
			enumType = enumType.getSuperclass();
		}
		Assert.notNull(enumType, () -> "The target type " + targetType.getName() + " does not refer to an enum");
		return enumType;
	}

}
