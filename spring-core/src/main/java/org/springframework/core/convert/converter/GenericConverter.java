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

package org.springframework.core.convert.converter;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Set;

/**
 * 一个GenericConverter支持转换多个不同的"原始类型和目标类型的键值对组合"。属于多对多，比如：一个原始类型Integer可以转换为n种目标类型，同样n多个原始类型可以转换为同一个目标类型Integer
 *
 * 例如：
 * public Set<ConvertiblePair> getConvertibleTypes() {
 *
 * 		Set<ConvertiblePair> pairs = new HashSet<ConvertiblePair>();
 * 		// Integer.class 转换为 String.class
 * 		pairs.add(new ConvertiblePair(Integer.class, String.class));
 * 		// Long.class 转换为 User.class
 * 		pairs.add(new ConvertiblePair(Long.class, User.class));
 * 		// Person.class 转换为 Member.class
 * 		pairs.add(new ConvertiblePair(Person.class, Member.class));
 * 		// .. 可以添加n多个不同的"原始类型和目标类型的键值对组合"
 *
 * 		// 多对多示范：
 * 		pairs.add(new ConvertiblePair(Integer.class, Long.class));
 * 		pairs.add(new ConvertiblePair(Integer.class, User.class));
 * 		pairs.add(new ConvertiblePair(Long.class, Integer.class));
 * 		pairs.add(new ConvertiblePair(User.class, Integer.class));
 *
 * 		return pairs;
 * }
 *
 * 题外：虽然Converter接口、ConverterFactory接口和GenericConverter接口之间没有任何的关系(接口之间没有互相实现)，
 * 但是Spring内部在注册Converter实现类和ConverterFactory实现类时是先把它们转换为GenericConverter，之后再统一对GenericConverter进行注册的。
 * 也就是说Spring内部会把Converter和ConverterFactory全部转换为GenericConverter进行注册，在Spring注册的容器中只存在GenericConverter这一种类型转换器。
 * 我想之所以给用户开放Converter接口和ConverterFactory接口是为了让我们能够更方便的实现自己的类型转换器。
 * 基于此，Spring官方也提倡我们在进行一些简单类型转换器定义时更多的使用Converter接口和ConverterFactory接口，在非必要的情况下少使用GenericConverter接口
 *
 * Generic converter interface for converting between two or more types.
 *
 * <p>This is the most flexible of the Converter SPI interfaces, but also the most complex.
 * It is flexible in that a GenericConverter may support converting between multiple source/target
 * type pairs (see {@link #getConvertibleTypes()}. In addition, GenericConverter implementations
 * have access to source/target {@link TypeDescriptor field context} during the type conversion
 * process. This allows for resolving source and target field metadata such as annotations and
 * generics information, which can be used to influence the conversion logic.
 *
 * <p>This interface should generally not be used when the simpler {@link Converter} or
 * {@link ConverterFactory} interface is sufficient.
 *
 * <p>Implementations may additionally implement {@link ConditionalConverter}.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 * @see TypeDescriptor
 * @see Converter
 * @see ConverterFactory
 * @see ConditionalConverter
 */
public interface GenericConverter {

	/**
	 * 返回这个GenericConverter能够转换的原类型和目标类型的这么一个组合
	 *
	 * 返回转换器集合，可以转换源和目标类型
	 *
	 *
	 * 多个不同的ConvertiblePair使用同一个GenericConverter进行转换，也就是一个GenericConverter支持转换多个不同的"原始类型和目标类型的键值对组合"
	 *
	 *
	 * Return the source and target types that this converter can convert between.
	 * <p>Each entry is a convertible source-to-target type pair.
	 * <p>For {@link ConditionalConverter conditional converters} this method may return
	 * {@code null} to indicate all source-to-target pairs should be considered.
	 */
	@Nullable
	Set<ConvertiblePair> getConvertibleTypes();

	/**
	 * 进行类型转换
	 *
	 * 转换源对象到目标类型的描述
	 *
	 * Convert the source object to the targetType described by the {@code TypeDescriptor}.
	 * @param source the source object to convert (may be {@code null})
	 * @param sourceType the type descriptor of the field we are converting from
	 * @param targetType the type descriptor of the field we are converting to
	 * @return the converted object
	 */
	// 完成对应类型的转换，只是当前这个类型，不再是规定的T类型了，而是TypeDescriptor(类的描述信息)
	@Nullable
	Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType);

	/**
	 * 原始类型和目标类型组成的键值对组合
	 *
	 * Holder for a source-to-target class pair. —— 源到目标类对的持有者
	 */
	final class ConvertiblePair/* 可转换对 */ {

		// 原始类型
		private final Class<?> sourceType;

		// 目标类型
		private final Class<?> targetType;

		/**
		 * Create a new source-to-target pair.
		 * @param sourceType the source type
		 * @param targetType the target type
		 */
		public ConvertiblePair(Class<?> sourceType, Class<?> targetType) {
			Assert.notNull(sourceType, "Source type must not be null");
			Assert.notNull(targetType, "Target type must not be null");
			this.sourceType = sourceType;
			this.targetType = targetType;
		}

		public Class<?> getSourceType() {
			return this.sourceType;
		}

		public Class<?> getTargetType() {
			return this.targetType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (other == null || other.getClass() != ConvertiblePair.class) {
				return false;
			}
			ConvertiblePair otherPair = (ConvertiblePair) other;
			return (this.sourceType == otherPair.sourceType && this.targetType == otherPair.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() * 31 + this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return (this.sourceType.getName() + " -> " + this.targetType.getName());
		}
	}

}
