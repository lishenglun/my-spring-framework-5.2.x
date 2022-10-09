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

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Converts an array to another array. First adapts the source array to a List,
 * then delegates to {@link CollectionToArrayConverter} to perform the target
 * array conversion.
 *
 * @author Keith Donald
 * @author Phillip Webb
 * @since 3.0
 */
final class ArrayToArrayConverter implements ConditionalGenericConverter {

	private final CollectionToArrayConverter helperConverter;

	private final ConversionService conversionService;


	public ArrayToArrayConverter(ConversionService conversionService) {
		this.helperConverter = new CollectionToArrayConverter(conversionService);
		this.conversionService = conversionService;
	}


	@Override
	public Set<ConvertiblePair> getConvertibleTypes() {
		return Collections.singleton(new ConvertiblePair(Object[].class, Object[].class));
	}

	@Override
	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
		return this.helperConverter.matches(sourceType, targetType);
	}

	@Override
	@Nullable
	public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {

		/* 1、如果转换服务是GenericConversionService，那么判断是不是没有原始类型和目标类型对应的转换器，是的话，就直接返回原始数据 */

		// 转换服务是GenericConversionService
		if (this.conversionService instanceof GenericConversionService) {
			TypeDescriptor targetElement = targetType.getElementTypeDescriptor();
			// 判断转换器是不是"不需要转换时，使用的转换器"，是的话，就代表不需要进行转换，既然不需要进行转换，就直接返回原始数据
			// 题外：只有没有原始类型和目标类型对应的转换器，转换器才是"不需要转换时使用的转换器"
			if (targetElement != null &&
					((GenericConversionService) this.conversionService).canBypassConvert(
							sourceType.getElementTypeDescriptor(), targetElement)) {

				return source;
			}
		}

		/* 2、转换服务不是GenericConversionService，或者有对应的转换器，那么就用转换器进行转换 */

		List<Object> sourceList = Arrays.asList(ObjectUtils.toObjectArray(source));
		return this.helperConverter.convert(sourceList, sourceType, targetType);
	}

}
